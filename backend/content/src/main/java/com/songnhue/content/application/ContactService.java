package com.songnhue.content.application;

import java.time.Instant;
import java.util.List;
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
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.core.spi.SettingPort;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4.
 *
 * <h2>Phạm vi ĐANG dựng, và phần cố ý chưa dựng</h2>
 *
 * Lượt 29/08 dựng đúng vòng khép kín: người dân gửi → bản ghi được lưu → cán bộ đọc được ở màn
 * hình quản trị. Đó là ngưỡng tối thiểu để một biểu mẫu là trung thực; dưới ngưỡng ấy thì
 * *"người dân tin là đã gửi được"* mà thật ra không ai nhận.
 *
 * <p>WS-36 (06/09) dựng nốt: <b>quy trình sáu trạng thái</b> qua Workflow engine, phân loại,
 * chuyển phòng ban, ghi chú nội bộ, và chặn xoá khi {@code DANG_XU_LY}.
 *
 * <p>⛔ Vẫn chưa dựng, ghi ra đây thay vì để im: reCAPTCHA (chặn bởi <b>G13</b>), xuất Excel.
 *
 * <h2>⭐ Hai chiều thư của một lượt gửi biểu mẫu — HAI đường khác hẳn nhau</h2>
 *
 * <ul>
 *   <li><b>Báo cho cán bộ</b> ⇒ {@link NotificationPort}: có hộp thư trên giao diện, có "đã đọc",
 *       tôn trọng công tắc bật-tắt kênh, và người nhận phân giải theo <i>quyền</i> nên thêm một cán
 *       bộ mới là xong — ⛔ không phải sửa một danh sách địa chỉ ở đâu đó.
 *   <li><b>Xác nhận cho người gửi</b> ⇒ hàng đợi + {@code MailPort}: họ ⛔ không có
 *       {@code user_id}, nên toàn bộ bộ máy thông báo ⛔ không dùng được. Xem
 *       {@link ContactAckMailHandler}.
 * </ul>
 *
 * <p>⚠ Cả hai đều nằm <b>trong cùng giao dịch</b> với lượt lưu: bản ghi hỏng thì ⛔ không thư nào
 * đi. ⛔ Và cả hai đều ⛔ <b>không</b> gọi SMTP tại chỗ — người dân ⛔ không phải chờ máy chủ thư để
 * biết biểu mẫu đã gửi được.
 *
 * <h2>⭐⭐ Trạng thái đổi DUY NHẤT qua {@link WorkflowPort}</h2>
 *
 * <p>Kể cả {@code MOI → DA_DOC}. Trước WS-36, bước ấy là một phép gán field trong entity — ⛔ không
 * luật ArchUnit nào bắt được, vì luật soi lời gọi {@code applyState()}. Xem javadoc của
 * {@link Contact}.
 *
 * <h2>⛔ Nội dung là VĂN BẢN THUẦN — không bao giờ dựng thành HTML</h2>
 *
 * Đây là chuỗi do người lạ trên Internet nhập. Nó được lưu nguyên văn (cắt ký tự điều khiển và
 * giới hạn độ dài) và nơi hiển thị <b>phải</b> để React escape như văn bản thường. Một
 * {@code dangerouslySetInnerHTML} đặt lên trường này là XSS lưu trữ nhắm thẳng vào người có
 * quyền quản trị — đúng hình dạng luật 12, chỉ khác là nạn nhân có quyền cao hơn.
 *
 * <h2>Chống lạm dụng dựa vào bộ lọc tần suất sẵn có</h2>
 *
 * Tiền tố {@code /api/v1/public} đã đi qua {@code RateLimitFilter} với
 * {@code RateLimitPolicy.PUBLIC}. Không dựng cơ chế đếm thứ hai ở tầng này: hai bộ đếm cho cùng
 * một mục đích là hai nơi phải nhớ, và cái ở dưới sẽ không ai để ý khi cái ở trên đổi.
 */
@Service
public class ContactService {

    /**
     * Giới hạn độ dài nội dung.
     *
     * <p>Cột là {@code TEXT} nên CSDL không chặn gì — không có ngưỡng ở đây thì một lượt gửi có
     * thể nhét vài megabyte vào một hàng, và màn hình quản trị là nơi lãnh hậu quả.
     */
    private static final int DAI_TOI_DA_NOI_DUNG = 5_000;

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
     * Quyền gác toàn bộ hộp thư — và cũng là <b>tập người nhận</b> của thư báo có liên hệ mới.
     *
     * <p>⭐ Phân giải người nhận theo <i>quyền</i> chứ ⛔ không theo một danh sách địa chỉ trong
     * {@code settings}: thêm một cán bộ vào hộp thư là gán quyền cho họ, và họ nhận thư ngay. Một
     * danh sách địa chỉ riêng là nơi thứ hai phải nhớ, và nó sẽ lệch (luật 14).
     */
    public static final String QUYEN_XU_LY = "cms:contact:manage";

    /** ⛔ Đọc ở {@link ContactAckMailHandler} nữa — một công tắc, hai nơi đọc, cùng một hằng. */
    public static final String KHOA_THU_XAC_NHAN = "cms.contact.ack-email-enabled";

    /** Hạn xử lý (giờ); {@code 0} = tắt nhắc. Đọc ở {@link ContactSlaHandler}. */
    public static final String KHOA_HAN_SLA_GIO = "cms.contact.sla-hours";

    private final ContactRepository contacts;
    private final ContactCategoryRepository danhMuc;
    private final ContactNoteRepository ghiChu;
    private final WorkflowPort workflow;
    private final OrgUnitPort orgUnits;
    private final NotificationPort notifications;
    private final JobPort jobs;
    private final SettingPort settings;

    public ContactService(
            ContactRepository contacts,
            ContactCategoryRepository danhMuc,
            ContactNoteRepository ghiChu,
            WorkflowPort workflow,
            OrgUnitPort orgUnits,
            NotificationPort notifications,
            JobPort jobs,
            SettingPort settings) {
        this.contacts = contacts;
        this.danhMuc = danhMuc;
        this.ghiChu = ghiChu;
        this.workflow = workflow;
        this.orgUnits = orgUnits;
        this.notifications = notifications;
        this.jobs = jobs;
        this.settings = settings;
    }

    /**
     * Ghi nhận một liên hệ gửi từ cổng công khai.
     *
     * @throws ValidationException khi thiếu trường bắt buộc hoặc không có đường liên lạc ngược
     */
    @Transactional
    public Contact tiepNhan(String hoTen, String email, String dienThoai, String chuDe, String noiDung) {
        String ten = chuanHoa(hoTen);
        String mail = chuanHoa(email);
        String dt = chuanHoa(dienThoai);
        String cd = chuanHoa(chuDe);
        String nd = chuanHoa(noiDung);

        batBuoc(ten, "fullName");
        batBuoc(cd, "subject");
        batBuoc(nd, "content");

        // Cùng luật với `ck_contacts_lien_lac`. Hai tầng chặn hai loại lỗi khác nhau: tầng này
        // trả lời được người dùng bằng tên trường cụ thể, ràng buộc CSDL bịt đường ghi thẳng.
        if (mail == null && dt == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("email", "LIEN_LAC_BAT_BUOC", "");
        }

        if (nd.length() > DAI_TOI_DA_NOI_DUNG) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("content", "QUA_DAI", String.valueOf(DAI_TOI_DA_NOI_DUNG));
        }

        Contact daLuu = contacts.save(new Contact(ten, mail, dt, cd, nd));
        baoCoLienHeMoi(daLuu);
        datThuXacNhan(daLuu);
        return daLuu;
    }

    /**
     * Báo cho cán bộ — T36.3.
     *
     * <p>⛔ {@code targeted} chứ ⛔ không {@code alert}: {@code alert} cộng thêm nhóm "Ban điều
     * hành" vì ở đó hệ thống <i>đoán</i> ai nên biết. Ở đây ta <b>biết chính xác</b> — người có
     * {@code cms:contact:manage}. Cộng cả ban lãnh đạo vào mỗi lượt người dân điền biểu mẫu là cách
     * chắc chắn nhất để vài tuần sau ⛔ không ai đọc thông báo nữa, và lúc đó cảnh báo sự cố thật
     * cũng chết theo.
     *
     * <p>⚠ Tiêu đề ⛔ <b>không</b> mang tên người gửi: hộp thư thông báo hiện trên màn hình của
     * nhiều người, và nội dung liên hệ có thể là một khiếu nại đích danh. Ai cần biết thì mở hộp
     * thư — nơi đã có kiểm quyền.
     */
    private void baoCoLienHeMoi(Contact c) {
        notifications.notify(NotifyRequest.targeted(
                "CONTACT_RECEIVED",
                "Có liên hệ mới từ cổng thông tin",
                "Chủ đề: " + c.getSubject(),
                NotifySeverity.INFO,
                QUYEN_XU_LY,
                List.of()));
    }

    /**
     * Đặt việc gửi thư xác nhận cho người gửi — T36.3.
     *
     * <p>⚠ Kiểm công tắc ở <b>cả hai đầu</b>: ở đây để ⛔ không đặt việc thừa, và lại một lần nữa
     * trong {@link ContactAckMailHandler} vì một việc có thể nằm chờ qua đêm và người tắt nó sáng
     * hôm sau mong nó có hiệu lực ngay.
     *
     * <p>⛔ Payload mang <b>mã công khai</b>, ⛔ không mang địa chỉ email — xem javadoc của handler.
     */
    private void datThuXacNhan(Contact c) {
        if (c.getEmail() == null || !settings.getBoolean(KHOA_THU_XAC_NHAN, true)) {
            return;
        }
        jobs.enqueue(new JobRequest(
                CmsJobTypes.CONTACT_ACK_MAIL,
                "{\"contactPublicId\":\"%s\"}".formatted(c.getPublicId()),
                // ⚠ Khoá chống trùng theo chính bản ghi: một lượt gửi biểu mẫu ⇒ tối đa một thư.
                CmsJobTypes.CONTACT_ACK_MAIL + ":" + c.getPublicId(),
                (short) 3));
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
        String nd = chuanHoa(noiDung);
        batBuoc(nd, "content");
        if (nd.length() > DAI_TOI_DA_GHI_CHU) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("content", "QUA_DAI", String.valueOf(DAI_TOI_DA_GHI_CHU));
        }
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
    private static String chuanHoa(String s) {
        if (s == null) return null;
        String sach = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
        return sach.isEmpty() ? null : sach;
    }

    private static void batBuoc(String giaTri, String truong) {
        if (giaTri == null) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail(truong, "BAT_BUOC", "");
        }
    }
}
