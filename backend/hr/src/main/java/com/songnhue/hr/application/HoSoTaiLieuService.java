package com.songnhue.hr.application;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.util.TenTep;
import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;
import com.songnhue.hr.domain.Employee;
import com.songnhue.hr.domain.HoSoThuMuc;
import com.songnhue.hr.infra.EmployeeRepository;

/**
 * Hợp đồng, tài liệu và % hoàn thiện hồ sơ CBNV — CN-04.5.
 *
 * <h2>⛔ ⛔ KHÔNG có bảng tài liệu riêng cho HR</h2>
 *
 * <p>Tệp nằm ở {@code attachments} với {@code owner_type = "EMPLOYEE"}, {@code purpose} = tên một
 * {@link HoSoThuMuc}. Dựng bảng thứ hai là hai chỗ kiểm magic bytes, hai chỗ quét virus, hai chỗ
 * tính hạn mức — và chúng sẽ lệch nhau ({@code architecture-review.md} §10.6). Versioning *"⛔ không
 * ghi đè"* mà đặc tả đòi là <b>hành vi sẵn có</b> của {@code AttachmentService.nextVersion(owner,
 * purpose)}: tải lại cùng thư mục thì thành phiên bản kế tiếp, bản cũ giữ nguyên.
 *
 * <h2>⛔ ⛔ Hạn mức RIÊNG cho từng thư mục — và vì sao nó ở HR chứ ⛔ không ở core</h2>
 *
 * <p>Cơ chế hạn mức của {@code core} chia theo <b>nhóm định dạng</b>
 * ({@code limits.upload.max-mb.image|document|gis}), còn CN-04.5 đòi bảy mức theo <b>thư mục</b>
 * (10/10/20/10/5/20/10 MB). Hai cách chia khác nhau, và cách của HR hẹp hơn — nên nó là <b>luật
 * bổ sung</b> áp trước khi giao tệp cho {@code core}, ⛔ không phải một cơ chế thay thế. Tệp vẫn đi
 * qua đủ magic bytes, quét virus và hạn mức dung lượng cả hồ sơ của {@code core}.
 *
 * <p>⚠ Kiểm <b>trước</b> khi gọi {@code upload}: gọi rồi mới từ chối là đã ghi một tệp vào MinIO
 * rồi bỏ nó lại đó — một tệp mồ côi ⛔ không ai đếm và ⛔ không ai xoá.
 *
 * <h2>⚠ Ảnh chân dung ⛔ KHÔNG phải một thư mục riêng thứ tám</h2>
 *
 * <p>Đặc tả liệt kê {@code ANH} là một trong bảy thư mục, hạn 5MB. ⛔ Đừng thêm một cột
 * {@code employees.photo_attachment_id} "cho tiện": đó là nửa thứ hai của một cặp đọc–ghi mà ⛔
 * không ai đồng bộ, và dự án đã trả giá sáu lần cho đúng hình dạng ấy (luật 27).
 */
@Service
public class HoSoTaiLieuService {

    private static final Logger log = LoggerFactory.getLogger(HoSoTaiLieuService.class);

    /** Khớp {@code attachments.owner_type} và khoá {@code limits.attachment.quota-mb.EMPLOYEE}. */
    public static final String OWNER_TYPE = "EMPLOYEE";

    /**
     * Định dạng nhận cho hồ sơ nhân sự — CN-04.5.
     *
     * <p>⛔ Không có {@code image/svg+xml}: SVG là một tài liệu <b>thực thi được</b> (script trong
     * XML) và nó chỉ được nhận ở màn hình nhận diện cổng, nơi có {@code SvgSanitizer} đứng chắn.
     * Một CCCD scan hay một hợp đồng ⛔ không bao giờ là SVG.
     */
    private static final List<String> DINH_DANG_NHAN = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg",
            "image/png",
            "image/webp");

    private final EmployeeRepository employees;
    private final AttachmentPort attachments;
    private final HrSettings hrSettings;
    private final ScopeGuard scopeGuard;
    private final com.songnhue.core.spi.SecurityEventPort suKienBaoMat;

    public HoSoTaiLieuService(
            EmployeeRepository employees,
            AttachmentPort attachments,
            HrSettings hrSettings,
            ScopeGuard scopeGuard,
            com.songnhue.core.spi.SecurityEventPort suKienBaoMat) {
        this.employees = employees;
        this.attachments = attachments;
        this.hrSettings = hrSettings;
        this.scopeGuard = scopeGuard;
        this.suKienBaoMat = suKienBaoMat;
    }

    @Transactional(readOnly = true)
    public List<AttachmentRef> danhSach(UUID hoSoPublicId) {
        return attachments.refsOf(OWNER_TYPE, trongPhamVi(hoSoPublicId).getId());
    }

    @Transactional(readOnly = true)
    public long dungLuongDaDung(UUID hoSoPublicId) {
        return attachments.usedBytes(OWNER_TYPE, trongPhamVi(hoSoPublicId).getId());
    }

    @Transactional
    public AttachmentRef tai(
            UUID hoSoPublicId,
            HoSoThuMuc thuMuc,
            String tenGoc,
            byte[] noiDung,
            LocalDate hieuLucTu,
            LocalDate hetHan) {

        Employee hoSo = trongPhamVi(hoSoPublicId);
        kiemHanMucThuMuc(thuMuc, noiDung);

        AttachmentRef ref = attachments.upload(
                new AttachmentUploadCommand(OWNER_TYPE, hoSo.getId(), thuMuc.name(), tenGoc, noiDung, DINH_DANG_NHAN));
        AttachmentRef ketQua = (hieuLucTu == null && hetHan == null)
                ? ref
                : attachments.setValidity(ref.publicId(), hieuLucTu, hetHan);

        // ⛔ Ghi MÃ nhân viên, ⛔ không ghi họ tên: nhật ký ứng dụng đi ra ngoài phạm vi kiểm soát
        //    của NĐ 13/2023 dễ hơn hẳn một bảng CSDL.
        log.info("Hồ sơ {} nhận tài liệu '{}' vào thư mục {}", hoSo.getCode(), tenGoc, thuMuc);
        return ketQua;
    }

    @Transactional(readOnly = true)
    public String duongDanTai(UUID hoSoPublicId, UUID tepPublicId) {
        thuocHoSo(hoSoPublicId, tepPublicId);
        return attachments.downloadUrl(tepPublicId);
    }

    @Transactional
    public void xoa(UUID hoSoPublicId, UUID tepPublicId) {
        thuocHoSo(hoSoPublicId, tepPublicId);
        attachments.delete(tepPublicId);
    }

    /**
     * % hoàn thiện hồ sơ và danh sách thư mục còn thiếu — CN-04.5 / M4.9.
     *
     * <p>⬜ Danh sách thư mục bắt buộc là <b>tham số chưa chốt</b>
     * ({@code hr.document.required-folders}) — xem {@link HrSettings#thuMucBatBuoc()}.
     *
     * <p>⛔⛔ Danh sách rỗng ⇒ {@code phanTram = null}, ⛔ <b>không</b> phải {@code 0}. Quy tắc 16:
     * số 0 là một câu khẳng định, và ở đây nó sẽ khẳng định *"mọi hồ sơ đều thiếu tài liệu"* đúng
     * vào lúc ⛔ chưa ai định nghĩa thế nào là đủ. Giao diện đọc {@code null} thành *"chưa cấu hình"*.
     */
    @Transactional(readOnly = true)
    public TinhTrangHoSo tinhTrang(UUID hoSoPublicId) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        Set<HoSoThuMuc> batBuoc = hrSettings.thuMucBatBuoc();

        Map<HoSoThuMuc, Long> demTheoThuMuc = new LinkedHashMap<>();
        for (HoSoThuMuc tm : HoSoThuMuc.values()) {
            demTheoThuMuc.put(tm, 0L);
        }
        for (AttachmentRef ref : attachments.refsOf(OWNER_TYPE, hoSo.getId())) {
            thuMucCua(ref).ifPresent(tm -> demTheoThuMuc.merge(tm, 1L, Long::sum));
        }

        List<HoSoThuMuc> thieu = new ArrayList<>();
        for (HoSoThuMuc tm : batBuoc) {
            if (demTheoThuMuc.getOrDefault(tm, 0L) == 0L) {
                thieu.add(tm);
            }
        }
        Integer phanTram =
                batBuoc.isEmpty() ? null : (int) Math.round(100.0 * (batBuoc.size() - thieu.size()) / batBuoc.size());

        return new TinhTrangHoSo(phanTram, List.copyOf(batBuoc), thieu, demTheoThuMuc);
    }

    // -------------------------------------------------------------------------

    /**
     * ⛔ Thư mục của một tệp đọc từ {@code purpose}, và một giá trị ⛔ không giải được thì <b>bỏ
     * qua</b> chứ ⛔ không ném.
     *
     * <p>{@code attachments} là bảng <b>dùng chung</b>: một hàng {@code owner_type='EMPLOYEE'} với
     * {@code purpose} lạ có thể đến từ một bản khôi phục cũ hoặc một lượt đổi tên hằng. Ném ở đây là
     * làm chết màn hình hồ sơ vì một hàng dữ liệu cũ — mà thứ người dùng cần là thấy phần còn lại.
     */
    /**
     * Một lượt tải cả hồ sơ <b>đã được phân quyền và đã phân loại tệp</b> — xem {@link #chuanBiZip}.
     *
     * @param chuaSanSang tệp <b>chưa</b> tải được (đang chờ quét virus hoặc đã bị cách ly). ⛔ Danh
     *     sách này ⛔ không được rơi đi trong im lặng — xem {@link #ghiZip}
     */
    public record ChuanBiZip(
            Long hoSoId, String maHoSo, List<AttachmentRef> sanSang, List<AttachmentRef> chuaSanSang) {}

    /**
     * Phân quyền + phân loại <b>TRƯỚC</b> khi byte đầu tiên chảy — CN-04.5 (T53.12).
     *
     * <h2>⛔⛔ Vì sao tách khỏi {@link #ghiZip}, và đây là một bài học đo được</h2>
     *
     * <p>Bản đầu làm tất cả bên trong {@code StreamingResponseBody}. Lượt chạy đầu tiên in ra:
     * {@code HttpMessageNotWritableException: No converter for ApiResponse with preset Content-Type
     * 'application/octet-stream'}, rồi {@code Cannot render error page — the response has already
     * been committed}.
     *
     * <p>⇒ <b>Một endpoint phát luồng ⛔ KHÔNG CÓ CÁCH NÀO báo lỗi sau byte đầu tiên.</b> Header đã
     * gửi, kiểu nội dung đã chốt là {@code application/octet-stream}, và bộ xử lý ngoại lệ chung ⛔
     * không ghi nổi envelope JSON vào đó. Người dùng nhận một tệp ZIP <b>hỏng</b> thay vì một thông
     * báo — và một lượt từ chối vì <i>ngoài phạm vi đơn vị</i> khi ấy trông y hệt một lỗi mạng.
     *
     * <p>⇒ Mọi thứ có thể ném — {@code ScopeGuard}, hồ sơ ⛔ không tồn tại — phải xảy ra ở đây, khi
     * phản hồi còn chưa được gửi đi.
     */
    @Transactional(readOnly = true)
    public ChuanBiZip chuanBiZip(UUID hoSoPublicId) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        List<AttachmentRef> tep = attachments.refsOf(OWNER_TYPE, hoSo.getId());
        return new ChuanBiZip(
                hoSo.getId(),
                hoSo.getCode(),
                tep.stream().filter(AttachmentRef::downloadable).toList(),
                tep.stream().filter(ref -> !ref.downloadable()).toList());
    }

    /**
     * Ghi bản nén ra luồng phản hồi — CN-04.5 (T53.12).
     *
     * <h2>⛔ Ghi thẳng ra luồng, ⛔ KHÔNG dựng {@code byte[]}</h2>
     *
     * <p>Một hồ sơ đủ bảy thư mục có thể vài chục MB. Dựng cả bản nén trong heap rồi mới gửi là
     * đúng thứ VPS 2 nhân phải tiết kiệm (T28.35), và mười lượt bấm đồng thời là mười bản cùng lúc.
     *
     * <h2>⛔ Tên mục là {@code <Thư mục>/<tên tệp>}</h2>
     *
     * <p>Bản nén phẳng với hai mươi tệp tên kiểu <i>"Quyết định.pdf"</i> là thứ người nhận phải mở
     * từng cái để biết cái nào là cái gì. ⚠ Tệp trùng tên trong cùng thư mục <b>có thật</b>
     * (versioning của {@code AttachmentService.nextVersion} giữ cả bản cũ) ⇒ chèn số thứ tự, vì
     * nhiều trình giải nén <b>lặng lẽ ghi đè</b> mục sau lên mục trước.
     *
     * <h2>⛔⛔ Tệp CHƯA tải được ⇒ bản nén TỰ KHAI, ⛔ không lặng lẽ thiếu</h2>
     *
     * <p>Một tệp vừa tải lên còn đang chờ quét virus có {@code downloadable = false}. Bỏ nó đi kèm
     * một dòng WARN trong log là để người nhận cầm một bản nén <b>thiếu</b> mà ⛔ không có cách nào
     * biết — và họ sẽ dùng nó như một bản đầy đủ.
     *
     * <p>⇒ Bản nén mang thêm mục {@code _THIEU.txt} liệt kê đúng những tệp ấy. ⛔ Còn <b>chặn cả
     * lượt tải</b> thì cũng sai theo chiều kia: một tệp đang quét làm hỏng thao tác của người ⛔
     * không liên quan gì. Cùng lý lẽ quy tắc 16 — <i>một con số (ở đây là một tập) ⛔ không đi một
     * mình</i>.
     *
     * <h2>⚠ Lượt tải này ghi NHẬT KÝ BẢO MẬT</h2>
     *
     * <p>{@code audit_logs} chỉ sinh dòng khi có <b>thay đổi</b>, nên mọi lượt đọc là vô hình
     * (T51.6). Mang cả hồ sơ một con người ra khỏi hệ thống mà ⛔ không để lại dấu vết ở bảng nào là
     * đúng thứ NĐ 13/2023 hỏi tới.
     */
    /**
     * Dựng bản nén ra <b>một tệp tạm</b> rồi trả đường dẫn — CN-04.5 (T53.12).
     *
     * <h2>⛔⛔ Vì sao ⛔ KHÔNG dùng {@code StreamingResponseBody}, dù đó là cách "đúng sách"</h2>
     *
     * <p>Bản đầu làm đúng thế, và lượt chạy đầu tiên đỏ với {@code AuthenticationException:
     * AUTH-0002} phát ra từ <b>bên trong</b> thân phát luồng.
     *
     * <p>Nguyên nhân: {@code StreamingResponseBody} chạy trên <b>một luồng khác</b> (async
     * dispatch). Mà gần như mọi cơ chế nền của hệ này đứng trên {@code ThreadLocal} —
     * {@code AuthContext}, {@code ScopeFilterAspect}, {@code AuditContext}. Ở luồng ấy chúng
     * <b>RỖNG</b>. Hệ quả ⛔ không dừng ở một ngoại lệ: một truy vấn chạy ở đó sẽ đi qua bộ lọc
     * phạm vi <b>⛔ không có phạm vi nào</b> — tức có thể đọc <b>rộng hơn</b> người gọi được phép.
     *
     * <p>⛔⛔ Và nó hỏng theo chiều tệ nhất: header đã gửi, kiểu nội dung đã chốt là
     * {@code application/octet-stream}, nên {@code GlobalExceptionHandler} ⛔ không ghi nổi envelope
     * JSON — người dùng nhận <b>một tệp ZIP hỏng</b> thay vì một thông báo.
     *
     * <h2>⛔ Vì sao tệp tạm chứ ⛔ không phải {@code byte[]}</h2>
     *
     * <p>Hạn mức một hồ sơ là {@code limits.attachment.quota-mb.EMPLOYEE} = <b>200 MB</b> (seed
     * 10/09). Dựng ngần ấy trong heap trên một VPS <b>2 nhân</b> là đúng thứ T28.35 đã phải tránh.
     * Tệp tạm cũng trả lại được {@code Content-Length} ⇒ trình duyệt hiện thanh tiến trình.
     *
     * <p>⚠ Nơi gọi <b>phải</b> xoá tệp — xem {@code HoSoTaiLieuController.zip}.
     */
    public java.nio.file.Path taoZipTamThoi(ChuanBiZip chuanBi) throws java.io.IOException {
        java.nio.file.Path tam = java.nio.file.Files.createTempFile("ho-so-", ".zip");
        try (java.io.OutputStream ra = java.nio.file.Files.newOutputStream(tam)) {
            ghiZip(chuanBi, ra);
        } catch (java.io.IOException | RuntimeException hong) {
            java.nio.file.Files.deleteIfExists(tam);
            throw hong;
        }
        return tam;
    }

    void ghiZip(ChuanBiZip chuanBi, java.io.OutputStream ra) throws java.io.IOException {
        java.util.Map<String, Integer> daDung = new java.util.HashMap<>();
        int soChep = 0;
        try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(ra, StandardCharsets.UTF_8)) {
            for (AttachmentRef ref : chuanBi.sanSang()) {
                java.util.Optional<AttachmentContent> noiDung =
                        attachments.readForOwner(OWNER_TYPE, chuanBi.hoSoId(), ref.publicId());
                if (noiDung.isEmpty()) {
                    // Kho và CSDL đã lệch nhau — hiếm, nhưng có thật. WARN chứ ⛔ không ném: một tệp
                    // mất trong kho ⛔ không được làm hỏng cả bản nén.
                    log.warn(
                            "Tệp {} của hồ sơ {} có trong CSDL mà ⛔ không đọc được từ kho",
                            ref.publicId(),
                            chuanBi.maHoSo());
                    continue;
                }
                String thuMuc = thuMucCua(ref).map(Enum::name).orElse("KHAC");
                zip.putNextEntry(new java.util.zip.ZipEntry(
                        thuMuc + "/" + tenDuyNhat(daDung, thuMuc, TenTep.chiPhanTen(ref.originalName()))));
                try (java.io.InputStream vao = noiDung.get().content()) {
                    vao.transferTo(zip);
                }
                zip.closeEntry();
                soChep++;
            }

            if (!chuanBi.chuaSanSang().isEmpty()) {
                zip.putNextEntry(new java.util.zip.ZipEntry("_THIEU.txt"));
                zip.write(banKeThieu(chuanBi.chuaSanSang()).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        // ⚠ Ghi SAU khi nén xong: `soChep` là số tệp THẬT SỰ ra khỏi hệ thống, ⛔ không phải số dòng
        //   trong CSDL. Hai con số ấy khác nhau đúng khi kho có vấn đề — và khi đó dòng nhật ký phải
        //   nói số nhỏ hơn.
        suKienBaoMat.hrDossierDownloaded(chuanBi.maHoSo(), soChep);
    }

    private static String banKeThieu(List<AttachmentRef> chuaSanSang) {
        StringBuilder sb = new StringBuilder();
        sb.append("BẢN NÉN NÀY THIẾU ").append(chuaSanSang.size()).append(" TỆP\n\n");
        sb.append("Những tệp dưới đây có trong hồ sơ nhưng chưa tải xuống được — thường là do đang\n")
                .append("chờ quét virus (vài giây sau khi tải lên), hoặc đã bị cách ly.\n")
                .append("Hãy tải lại bản nén sau ít phút.\n\n");
        for (AttachmentRef ref : chuaSanSang) {
            sb.append("  - ").append(ref.originalName()).append("\n");
        }
        return sb.toString();
    }

    /** Tên tệp ⛔ không trùng trong cùng một thư mục của bản nén — xem javadoc {@link #ghiZip}. */
    private static String tenDuyNhat(java.util.Map<String, Integer> daDung, String thuMuc, String ten) {
        String khoa = thuMuc + "/" + ten;
        int lan = daDung.merge(khoa, 1, Integer::sum);
        if (lan == 1) {
            return ten;
        }
        int cham = ten.lastIndexOf('.');
        return cham <= 0 ? ten + " (" + lan + ")" : ten.substring(0, cham) + " (" + lan + ")" + ten.substring(cham);
    }

    private static java.util.Optional<HoSoThuMuc> thuMucCua(AttachmentRef ref) {
        return java.util.Arrays.stream(HoSoThuMuc.values())
                .filter(tm -> tm.name().equals(ref.purpose()))
                .findFirst();
    }

    private void kiemHanMucThuMuc(HoSoThuMuc thuMuc, byte[] noiDung) {
        int hanMb = hrSettings.hanMucMb(thuMuc);
        long hanByte = hanMb * 1024L * 1024L;
        if (noiDung.length > hanByte) {
            throw new BusinessRuleException(
                    ErrorCode.HR_2003, thuMuc.name(), hanMb, Math.max(1, noiDung.length / (1024 * 1024)));
        }
    }

    private Employee trongPhamVi(UUID publicId) {
        return scopeGuard.require(employees.findByPublicIdAndDeletedAtIsNull(publicId), Employee.class, publicId);
    }

    /**
     * ⛔ Tệp phải thuộc ĐÚNG hồ sơ vừa được kiểm quyền.
     *
     * <p>Thiếu bước này thì một người có quyền xem <i>một</i> hồ sơ bất kỳ tải được <i>mọi</i> tệp
     * trong hệ thống chỉ bằng cách đoán {@code publicId} — kể cả hợp đồng và hồ sơ y tế của người
     * khác, vì {@code attachments} là bảng dùng chung. Kiểm quyền ở tham số thứ nhất mà ⛔ không
     * ràng buộc tham số thứ hai là một lỗ IDOR trông y hệt mã đúng.
     */
    private void thuocHoSo(UUID hoSoPublicId, UUID tepPublicId) {
        Employee hoSo = trongPhamVi(hoSoPublicId);
        boolean cua = attachments.refsOf(OWNER_TYPE, hoSo.getId()).stream()
                .anyMatch(ref -> ref.publicId().equals(tepPublicId));
        if (!cua) {
            throw new ResourceNotFoundException(ErrorCode.SYS_0004);
        }
    }

    /**
     * @param phanTram {@code null} = ⛔ chưa cấu hình thư mục bắt buộc, ⛔ không phải 0%
     */
    public record TinhTrangHoSo(
            Integer phanTram,
            List<HoSoThuMuc> batBuoc,
            List<HoSoThuMuc> conThieu,
            Map<HoSoThuMuc, Long> soTepTheoThuMuc) {}
}
