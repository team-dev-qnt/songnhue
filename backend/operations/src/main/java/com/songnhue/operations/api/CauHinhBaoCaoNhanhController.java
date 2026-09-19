package com.songnhue.operations.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.operations.api.BaoCaoNhanhDtos.CongTrinhView;
import com.songnhue.operations.api.BaoCaoNhanhDtos.GanCongTrinhRequest;
import com.songnhue.operations.api.BaoCaoNhanhDtos.ViTriView;
import com.songnhue.operations.application.CauHinhBaoCaoNhanhService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cấu hình Báo cáo nhanh — {@code /api/v1/ops/bao-cao-nhanh/cau-hinh}: công trình gắn vào 7 cống của
 * Bảng 3 và trạm của ghi chú Yên Nghĩa. Sửa bằng {@code ops:quick-report:manage} — cùng người lập báo
 * cáo, vì đây là thứ họ thấy trống trên văn bản và cần tự lấp, ⛔ chờ quản trị viên.
 */
@RestController
@RequestMapping("/api/v1/ops/bao-cao-nhanh/cau-hinh")
@Tag(name = "02-ops · Báo cáo nhanh", description = "Kỳ báo cáo ngập úng — nhập Bảng 2/5, chốt, xuất Word")
public class CauHinhBaoCaoNhanhController {

    private static final Set<String> LOAI = Set.of("CONG", "TRAM_BOM");

    private final CauHinhBaoCaoNhanhService service;

    public CauHinhBaoCaoNhanhController(CauHinhBaoCaoNhanhService service) {
        this.service = service;
    }

    @GetMapping("/vi-tri")
    @Operation(summary = "8 vị trí của mẫu cần gắn công trình — kèm điểm đo từng vế suy ra được")
    @RequirePermission("ops:report:view")
    public List<ViTriView> danhSach() {
        return service.danhSach().stream().map(ViTriView::of).toList();
    }

    @PutMapping("/vi-tri/{publicId}")
    @Operation(summary = "Gắn / gỡ công trình của một vị trí — sai loại trả OPS-2032")
    @RequirePermission("ops:quick-report:manage")
    public ViTriView gan(@PathVariable UUID publicId, @Valid @RequestBody GanCongTrinhRequest request) {
        return ViTriView.of(service.gan(publicId, request.constructionPublicId()));
    }

    @GetMapping("/cong-trinh")
    @Operation(summary = "Công trình theo loại hình (CONG | TRAM_BOM) — nguồn ô chọn, toàn Công ty")
    @RequirePermission("ops:report:view")
    public List<CongTrinhView> congTrinh(@RequestParam String loai) {
        if (!LOAI.contains(loai)) {
            throw new ValidationException(ErrorCode.SYS_0003).withDetail("loai", "INVALID", loai);
        }
        return service.congTrinhTheoLoai(loai).stream().map(CongTrinhView::of).toList();
    }
}
