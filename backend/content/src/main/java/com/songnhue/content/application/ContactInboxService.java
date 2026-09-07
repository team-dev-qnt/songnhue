package com.songnhue.content.application;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.domain.ContactCategory;
import com.songnhue.content.domain.ContactNote;
import com.songnhue.content.domain.ContactStatus;
import com.songnhue.content.infra.ContactCategoryRepository;
import com.songnhue.content.infra.ContactNoteRepository;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.export.BangCsv;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Hộp thư liên hệ ở phía <b>quản trị</b> — CN-01.4, phần cán bộ xử lý.
 *
 * <h2>⭐⭐ Vì sao tách khỏi {@link ContactService} — và vì sao Checkstyle nói đúng</h2>
 *
 * <p>Lượt WS-36 nối thêm hai chiều thư và một chỗ cắm captcha vào {@code ContactService}, và hàm
 * dựng của nó chạm <b>10 tham số</b>. Cổng kiểm {@code ParameterNumber} (trần 8) đỏ, và nó ⛔ không
 * phải một luật hình thức: mười phụ thuộc trong một lớp là dấu hiệu lớp ấy đang phục vụ <b>hai</b>
 * nhóm người dùng khác hẳn nhau.
 *
 * <p>Hai đường thật sự tách rời — chúng ⛔ không dùng chung một phụ thuộc nào ngoài kho dữ liệu:
 *
 * <table border="1">
 *   <caption>Hai nhóm phụ thuộc rời nhau</caption>
 *   <tr><th>Đường</th><th>Ai đi</th><th>Cần gì</th></tr>
 *   <tr><td>{@link ContactService}</td><td>người dân, ẩn danh, qua {@code /api/v1/public}</td>
 *       <td>luật biểu mẫu, cổng gửi vào ({@link InboundSubmissionGate}), thông báo, hàng đợi</td></tr>
 *   <tr><td>lớp này</td><td>cán bộ, đã đăng nhập, qua {@code /api/v1/cms}</td>
 *       <td>quy trình duyệt, danh mục, ghi chú, sơ đồ tổ chức</td></tr>
 * </table>
 *
 * <p>⇒ Tách là <b>sửa đúng thứ cổng kiểm chỉ vào</b>, ⛔ không phải gom phụ thuộc vào một object
 * holder để đi lọt phép đếm — cách ấy giữ nguyên vấn đề và mất luôn cái cảnh báo.
 *
 * <h2>⛔ Trạng thái đổi DUY NHẤT qua {@link WorkflowPort}</h2>
 *
 * <p>Kể cả {@code MOI → DA_DOC}. Xem javadoc của {@link Contact}.
 */
@Service
public class ContactInboxService {

    private static final int TRANG_TOI_DA = 100;

    /**
     * Giới hạn độ dài một ghi chú nội bộ.
     *
     * <p>Ngắn hơn nội dung liên hệ vì đây là chú thích thao tác, ⛔ không phải nơi chép lại hồ sơ.
     */
    private static final int DAI_TOI_DA_GHI_CHU = 2_000;

    /** Bước chuyển "đã đọc" — khớp {@code workflow_transitions.action} của quy trình CONTACT. */
    private static final String HANH_DONG_DOC = "READ";

    /**
     * ⛔⛔ Trần số dòng của một lượt xuất — T36.5.
     *
     * <p>Vượt trần thì <b>từ chối</b> ({@code CMS-2022}), ⛔ <b>không</b> cắt bớt. Một tệp Excel
     * thiếu 4.000 dòng trông y hệt một tệp đủ, và người nhận nó ⛔ không có cách nào biết.
     *
     * <p>⚠ 10.000 dòng CSV ≈ vài trăm KB — nằm gọn trong bộ nhớ của một VPS 2 nhân. Ngày khối lượng
     * thật sự tới ngưỡng này thì đường đúng ⛔ không phải nâng trần, mà là chuyển sang kết xuất chạy
     * nền theo khuôn {@code useXuatBaoCao} (conventions.md §3).
     */
    private static final int TRAN_DONG_XUAT = 10_000;

    private static final DateTimeFormatter GIO_VN = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ContactRepository contacts;
    private final ContactCategoryRepository danhMuc;
    private final ContactNoteRepository ghiChu;
    private final WorkflowPort workflow;
    private final OrgUnitPort orgUnits;
    private final InboundSubmissionGate cong;

    public ContactInboxService(
            ContactRepository contacts,
            ContactCategoryRepository danhMuc,
            ContactNoteRepository ghiChu,
            WorkflowPort workflow,
            OrgUnitPort orgUnits,
            InboundSubmissionGate cong) {
        this.contacts = contacts;
        this.danhMuc = danhMuc;
        this.ghiChu = ghiChu;
        this.workflow = workflow;
        this.orgUnits = orgUnits;
        this.cong = cong;
    }

    @Transactional(readOnly = true)
    public Page<Contact> danhSach(ContactStatus loc, int trang, int cor) {
        PageRequest yeuCau = PageRequest.of(Math.max(trang, 0), Math.min(Math.max(cor, 1), TRANG_TOI_DA));
        return loc == null
                ? contacts.findAllByDeletedAtIsNullOrderByCreatedAtDesc(yeuCau)
                : contacts.findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(loc, yeuCau);
    }

    @Transactional(readOnly = true)
    public long demChuaDoc() {
        return contacts.countByStatusAndDeletedAtIsNull(ContactStatus.MOI);
    }

    /**
     * Đánh dấu đã đọc — <b>qua Workflow engine</b>, và <b>chỉ khi đang ở {@code MOI}</b>.
     *
     * <p>⚠ Vế {@code == MOI} ⛔ không phải tối ưu: gọi {@code execute} ở trạng thái khác ném
     * {@code SYS-0008}, và màn hình chi tiết gọi hàm này ở <b>mỗi</b> lượt mở. Không có vế ấy thì
     * mở lại một liên hệ đã đọc là một lỗi đỏ trên màn hình người dùng.
     *
     * <p>⚠ Hàm này <b>idempotent</b> và trả về bản ghi hiện tại khi ⛔ không có gì để làm — nơi gọi
     * ⛔ không phải tự hỏi trạng thái trước.
     */
    @Transactional
    public Contact danhDauDaDoc(UUID publicId) {
        Contact c = tim(publicId);
        if (c.getStatus() != ContactStatus.MOI) {
            return c;
        }
        c.ghiDauVetDoc(nguoiDangDangNhap(), Instant.now());
        return contacts.save(workflow.execute(c, HANH_DONG_DOC, null));
    }

    /**
     * Thực hiện một bước chuyển bất kỳ của quy trình CONTACT.
     *
     * <p>⛔ ⛔ Không có {@code switch} nào ở đây, và đó là điểm chính: hành động hợp lệ, quyền cần
     * có, và "bước này có đòi lý do không" đều nằm trong {@code workflow_transitions} — <b>dữ
     * liệu</b>, ⛔ không phải mã. Thêm một bước chuyển là một dòng migration, ⛔ không phải một lượt
     * deploy mã.
     */
    @Transactional
    public Contact chuyenTrangThai(UUID publicId, String hanhDong, String lyDo) {
        Contact c = tim(publicId);
        return contacts.save(workflow.execute(c, hanhDong, null, lyDo));
    }

    /** Các nút giao diện được phép hiện — đã lọc theo quyền người đang đăng nhập. */
    @Transactional(readOnly = true)
    public List<AllowedAction> hanhDongChoPhep(UUID publicId) {
        return workflow.allowedActions(tim(publicId));
    }

    // === Phân loại · chuyển đơn vị · ghi chú nội bộ (T36.2) ===================

    /**
     * Gán hoặc gỡ phân loại. {@code null} = gỡ.
     *
     * <p>⚠ Cho gán cả phân loại <b>đã tắt</b> ⛔ không phải sơ suất — nó ⛔ không xảy ra: ô chọn ở
     * giao diện chỉ liệt kê phân loại đang bật. Chặn thêm ở đây nghĩa là một lượt sửa dữ liệu hàng
     * loạt về sau ⛔ không gán lại được đúng phân loại cũ mà bản ghi vốn mang.
     */
    @Transactional
    public Contact phanLoai(UUID publicId, UUID maPhanLoai) {
        Contact c = tim(publicId);
        if (maPhanLoai == null) {
            c.phanLoai(null);
        } else {
            ContactCategory pl = danhMuc.findByPublicIdAndDeletedAtIsNull(maPhanLoai)
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
            c.phanLoai(pl.getId());
        }
        return contacts.save(c);
    }

    /**
     * Chuyển liên hệ cho một phòng ban / Xí nghiệp. {@code null} = thu hồi.
     *
     * <p>⭐ Đây cũng là nguồn {@code orgUnitId()} của entity, tức là nguồn <b>người nhận thông
     * báo</b> của các bước chuyển sau. Chuyển đơn vị ⛔ không chỉ là một nhãn trên màn hình.
     */
    @Transactional
    public Contact chuyenDonVi(UUID publicId, UUID maDonVi) {
        Contact c = tim(publicId);
        if (maDonVi == null) {
            c.chuyenDonVi(null);
        } else {
            OrgUnitRef dv =
                    orgUnits.findRef(maDonVi).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
            c.chuyenDonVi(dv.id());
        }
        return contacts.save(c);
    }

    @Transactional(readOnly = true)
    public List<ContactNote> danhSachGhiChu(UUID publicId) {
        return ghiChu.findAllByContactIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                tim(publicId).getId());
    }

    @Transactional
    public ContactNote themGhiChu(UUID publicId, String noiDung) {
        Contact c = tim(publicId);
        // ⚠ Cán bộ đã đăng nhập, ⛔ không phải người lạ — nên ⛔ KHÔNG có captcha ở đường này. Ba
        //   bảo đảm còn lại của `InboundSubmissionGate` thì vẫn cần: ký tự điều khiển làm hỏng bản
        //   xuất CSV và chèn được dòng giả vào nhật ký, bất kể ai gõ ra chúng.
        String nd = cong.chuanHoa(noiDung);
        cong.batBuoc(nd, "content");
        cong.gioiHanDai(nd, DAI_TOI_DA_GHI_CHU, "content");
        return ghiChu.save(new ContactNote(c.getId(), nd));
    }

    @Transactional
    public void xoaGhiChu(UUID maGhiChu) {
        ContactNote n = ghiChu.findByPublicIdAndDeletedAtIsNull(maGhiChu)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        n.markDeleted(Instant.now());
        ghiChu.save(n);
    }

    // === Xoá — CN-01.4 cấm đích danh ở một trạng thái ========================

    /**
     * Xoá mềm một liên hệ. ⛔ <b>Cấm</b> khi đang {@code DANG_XU_LY} (CN-01.4).
     *
     * <p>⚠ Ràng buộc này đặt ở <b>service</b> chứ ⛔ không ở controller, vì controller ⛔ không phải
     * đường vào duy nhất còn lại mãi mãi (luật 12). Ở đây nó phủ mọi lời gọi, kể cả một job dọn dẹp
     * viết vào năm sau.
     */
    @Transactional
    public void xoa(UUID publicId) {
        Contact c = tim(publicId);
        if (c.getStatus() == ContactStatus.DANG_XU_LY) {
            throw new BusinessRuleException(ErrorCode.CMS_2018);
        }
        c.markDeleted(Instant.now());
        contacts.save(c);
    }

    /** Tra tên phân loại và tên đơn vị theo lô — chống N+1 trên màn hình danh sách. */
    @Transactional(readOnly = true)
    public Optional<ContactCategory> phanLoaiCua(Contact c) {
        return c.getCategoryId() == null ? Optional.empty() : danhMuc.findById(c.getCategoryId());
    }

    @Transactional(readOnly = true)
    public Optional<OrgUnitRef> donViCua(Contact c) {
        return c.getAssignedOrgUnitId() == null ? Optional.empty() : orgUnits.findRefById(c.getAssignedOrgUnitId());
    }

    private Contact tim(UUID publicId) {
        return contacts.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    /**
     * Kết xuất danh sách liên hệ ra CSV cho Excel — CN-01.4 / T36.5.
     *
     * <h2>⛔ CSV, ⛔ không phải {@code .xlsx} — và đây là quyết định đã chốt ở T34.8</h2>
     *
     * <p>Apache POI kéo theo ~12 MB phụ thuộc và mở thêm một bề mặt CVE (POI/XMLBeans/
     * commons-compress đều có lịch sử) — trên một kho đang có 8 mã CVE ≥ 7 phải theo dõi hằng đêm.
     * Excel mở CSV được, và {@link BangCsv} đã mang đủ ba quy ước để Excel bản <b>vi-VN</b> đọc
     * đúng (BOM · dấu tách {@code ;} · dấu thập phân {@code ,}).
     *
     * <h2>⛔⛔ Cột <i>Nội dung</i> mang chữ do NGƯỜI LẠ TRÊN INTERNET gõ</h2>
     *
     * <p>Một ô bắt đầu bằng {@code =}, {@code +}, {@code -}, {@code @} được Excel thi hành như
     * <b>công thức</b> khi mở tệp. {@code BangCsv.boc()} chặn bằng dấu nháy đơn — và đó chính là lý
     * do lớp ấy được <b>chuyển lên</b> {@code core.common.export} thay vì chép một bản sang đây: một
     * bản sao của lớp chống tấn công là hai nơi phải nhớ vá (luật 14).
     *
     * <p>⛔ Bản xuất ⛔ <b>không</b> mang ghi chú nội bộ: đó là chỗ cán bộ viết <i>về</i> người dân,
     * và một tệp Excel là thứ được gửi qua email, dán vào báo cáo, để trên máy dùng chung.
     *
     * @throws BusinessRuleException {@code CMS-2022} khi vượt {@link #TRAN_DONG_XUAT}
     */
    @Transactional(readOnly = true)
    public BanXuat xuatCsv(ContactStatus loc) {
        long tong = loc == null ? contacts.countByDeletedAtIsNull() : contacts.countByStatusAndDeletedAtIsNull(loc);
        if (tong > TRAN_DONG_XUAT) {
            throw new BusinessRuleException(ErrorCode.CMS_2022, tong, TRAN_DONG_XUAT);
        }

        List<Contact> hang = danhSach(loc, 0, TRAN_DONG_XUAT).getContent();

        // ⚠ Tra tên phân loại và tên đơn vị THEO LÔ — ⛔ không gọi trong vòng lặp (N+1 trên một
        //   lượt xuất 10.000 dòng là 20.000 lượt truy vấn).
        Map<Long, String> tenPhanLoai = new HashMap<>();
        danhMuc.findAllByDeletedAtIsNullOrderBySortOrderAscNameAsc()
                .forEach(c -> tenPhanLoai.put(c.getId(), c.getName()));
        Map<Long, OrgUnitRef> donVi = orgUnits.findRefsByIds(hang.stream()
                .map(Contact::getAssignedOrgUnitId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList());

        BangCsv bang = new BangCsv();
        bang.dong(
                "Thời điểm nhận",
                "Họ và tên",
                "Email",
                "Số điện thoại",
                "Tiêu đề",
                "Nội dung",
                "Trạng thái",
                "Phân loại",
                "Đơn vị xử lý");
        for (Contact c : hang) {
            bang.dong(
                    ZonedDateTime.ofInstant(c.getCreatedAt(), DateTimeUtils.ZONE_VN)
                            .format(GIO_VN),
                    c.getFullName(),
                    c.getEmail() == null ? "" : c.getEmail(),
                    c.getPhone() == null ? "" : c.getPhone(),
                    c.getSubject(),
                    c.getContent(),
                    c.getStatus().name(),
                    // ⛔ Rỗng nói ra là rỗng — ⛔ không dựng một chuỗi "Chưa phân loại" vào DỮ LIỆU.
                    //   Nhãn ấy là việc của màn hình; trong một tệp để lọc và cộng thì nó là một
                    //   giá trị giả trông như thật.
                    c.getCategoryId() == null ? "" : tenPhanLoai.getOrDefault(c.getCategoryId(), ""),
                    c.getAssignedOrgUnitId() == null
                            ? ""
                            : java.util.Optional.ofNullable(donVi.get(c.getAssignedOrgUnitId()))
                                    .map(OrgUnitRef::name)
                                    .orElse(""));
        }

        String tenTep = "lien-he_%s%s.csv"
                .formatted(java.time.LocalDate.now(DateTimeUtils.ZONE_VN), loc == null ? "" : "_" + loc.name());
        return new BanXuat(tenTep, bang.byteUtf8Bom(), bang.soDong());
    }

    /**
     * @param soDong <b>kể cả</b> dòng tiêu đề — nơi gọi khẳng định chống tập rỗng bằng con số này
     */
    public record BanXuat(String tenTep, byte[] noiDung, int soDong) {}

    /** `null` khi không có phiên — không thể xảy ra sau `@RequirePermission`, nhưng không giả định. */
    private static Long nguoiDangDangNhap() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
    }

    /**
     * Cắt khoảng trắng hai đầu và <b>loại ký tự điều khiển</b>; rỗng ⇒ {@code null}.
     *
     * <p>Ký tự điều khiển không phải chuyện thẩm mỹ: chúng làm hỏng bản xuất CSV về sau và có
     * thể chèn dòng giả vào nhật ký. Giữ lại {@code \n} và {@code \t} vì nội dung là văn bản
     * nhiều dòng thật.
     */
}
