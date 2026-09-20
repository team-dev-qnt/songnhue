package com.songnhue.operations.api;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.operations.api.BaoCaoNhanhDtos.ChiTietView;
import com.songnhue.operations.api.BaoCaoNhanhDtos.KhungRequest;
import com.songnhue.operations.api.BaoCaoNhanhDtos.KyView;
import com.songnhue.operations.api.BaoCaoNhanhDtos.LuongMuaRequest;
import com.songnhue.operations.api.BaoCaoNhanhDtos.MoLaiRequest;
import com.songnhue.operations.api.BaoCaoNhanhDtos.NgapUngRequest;
import com.songnhue.operations.api.BaoCaoNhanhDtos.VanHanhRequest;
import com.songnhue.operations.application.BaoCaoNhanhDocx;
import com.songnhue.operations.application.BaoCaoNhanhService;
import com.songnhue.operations.application.SoLieuNhapTayService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Báo cáo nhanh — {@code /api/v1/ops/bao-cao-nhanh}.
 *
 * <p>Xem/tải dùng lại {@code ops:report:view}/{@code :export}. Nhập + chốt gác bằng
 * {@code ops:quick-report:manage}; mở lại bằng {@code ops:quick-report:reopen} — hai cổng cũng khai ở
 * {@code workflow_transitions.required_permission}, và engine kiểm lại lần nữa.
 */
@RestController
@RequestMapping("/api/v1/ops/bao-cao-nhanh")
@Tag(name = "02-ops · Báo cáo nhanh", description = "Kỳ báo cáo ngập úng — nhập Bảng 2/5, chốt, xuất Word")
public class BaoCaoNhanhController {

    private static final List<String> SAP_XEP = List.of("denThoiDiem", "createdAt");

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    private final BaoCaoNhanhService service;

    public BaoCaoNhanhController(BaoCaoNhanhService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách kỳ báo cáo — mới nhất trước")
    @RequirePermission("ops:report:view")
    public Page<KyView> danhSach(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(defaultValue = "denThoiDiem,desc") String sort) {
        return service.danhSach(PageUtils.toPageable(page, size, sort, SAP_XEP)).map(KyView::of);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo kỳ báo cáo — 'đến' phải sau 'từ' (OPS-2030)")
    @RequirePermission("ops:quick-report:manage")
    public KyView tao(@Valid @RequestBody KhungRequest request) {
        return KyView.of(service.tao(request.tuThoiDiem(), request.denThoiDiem()));
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Toàn bộ nội dung một kỳ — Bảng 1–5, Mục 1/3, ghi chú Yên Nghĩa (số do BE tính)")
    @RequirePermission("ops:report:view")
    public ChiTietView chiTiet(@PathVariable UUID publicId) {
        return ChiTietView.of(service.chiTiet(publicId));
    }

    @PutMapping("/{publicId}/khung")
    @Operation(summary = "Sửa khung giờ — kỳ đã chốt trả OPS-2029")
    @RequirePermission("ops:quick-report:manage")
    public ChiTietView suaKhung(@PathVariable UUID publicId, @Valid @RequestBody KhungRequest request) {
        service.suaKhung(publicId, request.tuThoiDiem(), request.denThoiDiem());
        return ChiTietView.of(service.chiTiet(publicId));
    }

    @PutMapping("/{publicId}/van-hanh")
    @Operation(summary = "Nhập Bảng 2 — số máy đang chạy; vượt thiết kế OPS-2028, kỳ đã chốt OPS-2029")
    @RequirePermission("ops:quick-report:manage")
    public ChiTietView luuVanHanh(@PathVariable UUID publicId, @Valid @RequestBody VanHanhRequest request) {
        service.luuVanHanh(
                publicId,
                request.o().stream()
                        .map(o -> new BaoCaoNhanhService.NhapVanHanh(o.nhomMayPublicId(), o.soMayVanHanh()))
                        .toList());
        return ChiTietView.of(service.chiTiet(publicId));
    }

    @PutMapping("/{publicId}/ngap-ung")
    @Operation(summary = "Nhập Bảng 5 — diện tích ngập úng (ha) theo xã của Sông Nhuệ")
    @RequirePermission("ops:quick-report:manage")
    public ChiTietView luuNgapUng(@PathVariable UUID publicId, @Valid @RequestBody NgapUngRequest request) {
        service.luuNgapUng(
                publicId,
                request.dong().stream()
                        .map(d -> new SoLieuNhapTayService.NhapNgapUng(
                                d.xaPublicId(), d.ngapTrangLua(), d.ngapTrangRau(), d.sauNuocLua(), d.sauNuocRau()))
                        .toList());
        return ChiTietView.of(service.chiTiet(publicId));
    }

    @PutMapping("/{publicId}/luong-mua")
    @Operation(summary = "Nhập Bảng 4 — lượng mưa (mm) 8 điểm của Sông Nhuệ; nguồn tự động (G3-a) chưa có")
    @RequirePermission("ops:quick-report:manage")
    public ChiTietView luuLuongMua(@PathVariable UUID publicId, @Valid @RequestBody LuongMuaRequest request) {
        service.luuLuongMua(
                publicId,
                request.o().stream()
                        .map(o -> new SoLieuNhapTayService.NhapLuongMua(o.diemMuaPublicId(), o.luongMuaMm()))
                        .toList());
        return ChiTietView.of(service.chiTiet(publicId));
    }

    @PostMapping("/{publicId}/chot")
    @Operation(summary = "Chốt kỳ — chụp danh mục vào kỳ, khoá mọi ô nhập")
    @RequirePermission("ops:quick-report:manage")
    public ChiTietView chot(@PathVariable UUID publicId) {
        service.chot(publicId);
        return ChiTietView.of(service.chiTiet(publicId));
    }

    /**
     * Tải bản Word — điền thẳng vào mẫu của Công ty ({@link BaoCaoNhanhDocx}).
     *
     * <p>⚠ Đường dẫn mang {@code /xuat} ⇒ xô hạn mức EXPORT ({@code RateLimitPolicy}), khai ở
     * {@code HanMucKetXuatTest.BAN_KHAI}. Tải được cả kỳ ĐANG NHẬP (bản nháp để rà) lẫn kỳ đã chốt.
     */
    @GetMapping("/{publicId}/xuat")
    @Operation(summary = "Tải Báo cáo nhanh dạng Word (.docx) theo mẫu của Công ty")
    @RequirePermission("ops:report:export")
    public ResponseEntity<byte[]> xuat(@PathVariable UUID publicId) {
        BaoCaoNhanhService.ChiTiet c = service.chiTiet(publicId);
        ZonedDateTime den = DateTimeUtils.toVietnamTime(c.baoCao().getDenThoiDiem());
        String ten = "bao-cao-nhanh-%04d%02d%02d-%02d%02d.docx"
                .formatted(den.getYear(), den.getMonthValue(), den.getDayOfMonth(), den.getHour(), den.getMinute());
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(ten, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType(DOCX))
                .body(BaoCaoNhanhDocx.dung(c));
    }

    @PostMapping("/{publicId}/mo-lai")
    @Operation(summary = "Mở lại kỳ đã chốt — BẮT BUỘC lý do (vào nhật ký kiểm toán)")
    @RequirePermission("ops:quick-report:reopen")
    public ChiTietView moLai(@PathVariable UUID publicId, @Valid @RequestBody MoLaiRequest request) {
        service.moLai(publicId, request.lyDo());
        return ChiTietView.of(service.chiTiet(publicId));
    }
}
