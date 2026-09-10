package com.songnhue.hr.api;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.hr.application.CanhBaoHetHanService;
import com.songnhue.hr.application.HoSoTaiLieuService;
import com.songnhue.hr.domain.HoSoThuMuc;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Hợp đồng, tài liệu và cảnh báo hết hạn — CN-04.5 / SRS M4.7–M4.9.
 *
 * <h2>Hai nhóm ở chung một controller, và đó là chủ ý</h2>
 *
 * <p>Tài liệu ({@code /employees/{id}/tai-lieu}) và cảnh báo hết hạn ({@code /canh-bao-het-han})
 * cùng thuộc CN-04.5 và cùng phục vụ một câu hỏi nghiệp vụ: *"hồ sơ nào chưa đủ, cái gì sắp hết
 * hạn"*. ⚠ Nhưng cảnh báo là tài nguyên <b>toàn hệ thống</b> (đã lọc theo phạm vi đơn vị của người
 * gọi), ⛔ không lồng dưới một hồ sơ — nên nó có đường dẫn riêng.
 *
 * <h2>⛔ Trần dung lượng: HAI tầng, hai mã lỗi khác nhau</h2>
 *
 * <ul>
 *   <li>{@code HR-2003} — trần <b>mỗi tệp theo thư mục</b> (Ảnh 5MB, Hợp đồng 20MB…), luật của HR;
 *   <li>{@code SYS-0010} — hạn mức <b>tổng dung lượng một hồ sơ</b>
 *       ({@code limits.attachment.quota-mb.EMPLOYEE}), cơ chế của {@code core}.
 * </ul>
 *
 * <p>Gộp hai mã là để người vận hành đọc một câu lỗi rồi đi sửa nhầm tham số.
 */
@RestController
@RequestMapping("/api/v1/hr")
@Tag(name = "04-hr · Tài liệu & cảnh báo hết hạn", description = "7 thư mục cố định, versioning, M4.9")
public class HoSoTaiLieuController {

    private final HoSoTaiLieuService taiLieu;
    private final CanhBaoHetHanService canhBao;

    public HoSoTaiLieuController(HoSoTaiLieuService taiLieu, CanhBaoHetHanService canhBao) {
        this.taiLieu = taiLieu;
        this.canhBao = canhBao;
    }

    @GetMapping("/employees/{hoSoId}/tai-lieu")
    @Operation(summary = "Tài liệu của một hồ sơ — mới nhất trước, kèm số phiên bản")
    @RequirePermission("hr:employee:view")
    public List<HoSoConDtos.TaiLieuView> danhSach(@PathVariable UUID hoSoId) {
        return taiLieu.danhSach(hoSoId).stream()
                .map(HoSoTaiLieuController::toView)
                .toList();
    }

    @GetMapping("/employees/{hoSoId}/tai-lieu/tinh-trang")
    @Operation(summary = "% hoàn thiện hồ sơ + thư mục còn thiếu + dung lượng đã dùng")
    @RequirePermission("hr:employee:view")
    public HoSoConDtos.TinhTrangHoSoView tinhTrang(@PathVariable UUID hoSoId) {
        return HoSoConDtos.TinhTrangHoSoView.of(taiLieu.tinhTrang(hoSoId), taiLieu.dungLuongDaDung(hoSoId));
    }

    /**
     * ⚠ {@code thuMuc} là <b>enum</b> ở chữ ký, ⛔ không phải {@code String}: một giá trị lạ bị Spring
     * từ chối ở tầng ràng buộc tham số với 400, trước khi chạm vào service. Nhận {@code String} rồi
     * tự giải là mở đường cho một {@code purpose} tuỳ ý vào bảng {@code attachments} dùng chung —
     * và một tệp mang {@code purpose} lạ sẽ ⛔ không hiện ở thư mục nào mà vẫn tính vào hạn mức.
     */
    @PostMapping(path = "/employees/{hoSoId}/tai-lieu", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tải tài liệu vào một trong 7 thư mục — tải lại cùng thư mục = phiên bản mới")
    @RequirePermission("hr:employee:update")
    public HoSoConDtos.TaiLieuView tai(
            @PathVariable UUID hoSoId,
            @RequestPart("file") MultipartFile file,
            @RequestParam HoSoThuMuc thuMuc,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hieuLucTu,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hetHan) {

        return toView(taiLieu.tai(hoSoId, thuMuc, file.getOriginalFilename(), doc(file), hieuLucTu, hetHan));
    }

    @GetMapping("/employees/{hoSoId}/tai-lieu/{tepId}/download-url")
    @Operation(summary = "Đường dẫn tải có hạn — chỉ cấp sau khi đã kiểm quyền xem hồ sơ")
    @RequirePermission("hr:employee:view")
    public DownloadUrl duongDanTai(@PathVariable UUID hoSoId, @PathVariable UUID tepId) {
        return new DownloadUrl(taiLieu.duongDanTai(hoSoId, tepId));
    }

    public record DownloadUrl(String url) {}

    @DeleteMapping("/employees/{hoSoId}/tai-lieu/{tepId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm một tài liệu")
    @RequirePermission("hr:employee:update")
    public void xoa(@PathVariable UUID hoSoId, @PathVariable UUID tepId) {
        taiLieu.xoa(hoSoId, tepId);
    }

    /**
     * Cảnh báo hợp đồng và chứng chỉ sắp/đã hết hạn — M4.9.
     *
     * <p>⚠ Danh sách <b>đã lọc theo phạm vi đơn vị</b> của người gọi: quản lý Xí nghiệp chỉ thấy
     * đơn vị mình (CN-04.7 / M4.13). Nó ⛔ không phải một báo cáo toàn Công ty cho mọi người.
     */
    @GetMapping("/canh-bao-het-han")
    @Operation(summary = "Hợp đồng + chứng chỉ sắp/ĐÃ hết hạn — ngưỡng đọc từ settings")
    @RequirePermission("hr:employee:view")
    public HoSoConDtos.CanhBaoHetHanView canhBaoHetHan() {
        return HoSoConDtos.CanhBaoHetHanView.of(canhBao.canhBao());
    }

    // -------------------------------------------------------------------------

    private static HoSoConDtos.TaiLieuView toView(AttachmentRef ref) {
        HoSoThuMuc thuMuc = java.util.Arrays.stream(HoSoThuMuc.values())
                .filter(tm -> tm.name().equals(ref.purpose()))
                .findFirst()
                .orElse(null);
        return HoSoConDtos.TaiLieuView.of(ref, thuMuc);
    }

    /**
     * ⚠ {@code getBytes()} ném {@link IOException} — bọc thành lỗi nghiệp vụ đọc được thay vì để nó
     * đi lên thành 500. Một tệp đứt giữa chừng là chuyện thường của mạng, ⛔ không phải lỗi hệ thống.
     */
    private static byte[] doc(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("file", "UNREADABLE", null);
        }
    }
}
