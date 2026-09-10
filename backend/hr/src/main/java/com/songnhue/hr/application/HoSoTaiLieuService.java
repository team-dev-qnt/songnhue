package com.songnhue.hr.application;

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

    public HoSoTaiLieuService(
            EmployeeRepository employees, AttachmentPort attachments, HrSettings hrSettings, ScopeGuard scopeGuard) {
        this.employees = employees;
        this.attachments = attachments;
        this.hrSettings = hrSettings;
        this.scopeGuard = scopeGuard;
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
            throw new ResourceNotFoundException(ErrorCode.SYS_0004, tepPublicId);
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
