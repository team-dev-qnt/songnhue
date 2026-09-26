package com.songnhue.operations.api;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.importer.KetQuaNhap;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.application.DanhMucMayBomService;
import com.songnhue.operations.application.importer.TramBomImportService;
import com.songnhue.operations.domain.BangCoMayBom;
import com.songnhue.operations.domain.CoMayBom;
import com.songnhue.operations.domain.DongNhomMay;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục máy bơm của Báo cáo nhanh — {@code /api/v1/ops/may-bom}.
 *
 * <p>Quyền dùng lại mã của danh mục công trình: nhóm máy LÀ một phần hồ sơ trạm bơm, và đường nhập
 * tệp gác cùng cổng {@code ops:construction:create} với đường nhập công trình.
 */
@RestController
@RequestMapping("/api/v1/ops/may-bom")
@Tag(name = "02-ops · Danh mục máy bơm", description = "9 cỡ máy + nhóm máy từng trạm — nguồn Bảng 1/2 Báo cáo nhanh")
public class DanhMucMayBomController {

    private final DanhMucMayBomService danhMuc;
    private final TramBomImportService importer;
    private final OrgUnitPort orgUnits;

    public DanhMucMayBomController(DanhMucMayBomService danhMuc, TramBomImportService importer, OrgUnitPort orgUnits) {
        this.danhMuc = danhMuc;
        this.importer = importer;
        this.orgUnits = orgUnits;
    }

    /** @param qTu {@code null} = −∞ · @param qDen {@code null} = +∞ — nửa mở {@code [qTu, qDen)} */
    public record CoMayView(UUID publicId, String nhan, BigDecimal qTu, BigDecimal qDen, int thuTu) {
        static CoMayView of(CoMayBom c) {
            return new CoMayView(c.getPublicId(), c.getNhan(), c.getQTuM3h(), c.getQDenM3h(), c.getSortOrder());
        }
    }

    public record Bien(@NotNull UUID publicId, @DecimalMin("0") BigDecimal qTu, @DecimalMin("0") BigDecimal qDen) {}

    /** ⛔ ĐỦ 9 cỡ một lượt — xem {@link DanhMucMayBomService#suaBien}. */
    public record BienRequest(@NotNull @Size(min = 1, max = 20) List<@Valid Bien> bien) {}

    /**
     * @param coMay nhãn cỡ máy (cột Bảng 1) mà Q rơi vào — tính ở BE (quy tắc 3)
     * @param tenDonVi tên Xí nghiệp quản lý trạm — nhóm dòng của Bảng 2
     */
    public record NhomMayView(
            UUID publicId,
            UUID constructionPublicId,
            String maCongTrinh,
            String tenCongTrinh,
            String tenDonVi,
            String nguonTuoiHuongTieu,
            int soMay,
            BigDecimal qMotMayM3h,
            String coMay) {}

    @GetMapping("/co-may")
    @Operation(summary = "9 cỡ máy của Bảng 1 — biên nửa mở [qTu, qDen) m³/h")
    @RequirePermission("ops:construction:view")
    public List<CoMayView> danhSachCo() {
        return danhMuc.danhSachCo().stream().map(CoMayView::of).toList();
    }

    @PutMapping("/co-may")
    @Operation(summary = "Sửa biên cỡ máy — gửi ĐỦ 9 cỡ; biên phải liền nhau (OPS-2031)")
    @RequirePermission("ops:construction:update")
    public List<CoMayView> suaBien(@Valid @RequestBody BienRequest request) {
        return danhMuc
                .suaBien(request.bien().stream()
                        .map(b -> new DanhMucMayBomService.BienMoi(b.publicId(), b.qTu(), b.qDen()))
                        .toList())
                .stream()
                .map(CoMayView::of)
                .toList();
    }

    @GetMapping("/nhom-may")
    @Operation(summary = "Toàn bộ nhóm máy (toàn Công ty) theo thứ tự tệp nhập")
    @RequirePermission("ops:construction:view")
    public List<NhomMayView> danhSachNhom() {
        List<DongNhomMay> dong = danhMuc.danhSachNhom();
        BangCoMayBom bang = danhMuc.bangCo();
        Set<Long> donVi = dong.stream().map(DongNhomMay::orgUnitId).collect(Collectors.toSet());
        Map<Long, OrgUnitRef> tenDonVi = orgUnits.findRefsByIds(donVi);
        return dong.stream()
                .map(d -> new NhomMayView(
                        d.nhomPublicId(),
                        d.constructionPublicId(),
                        d.maCongTrinh(),
                        d.tenCongTrinh(),
                        tenDonVi.containsKey(d.orgUnitId())
                                ? tenDonVi.get(d.orgUnitId()).name()
                                : null,
                        d.nguonTuoiHuongTieu(),
                        d.soMay(),
                        d.qM3h(),
                        bang.co().get(bang.xep(d.qM3h())).nhan()))
                .toList();
    }

    @DeleteMapping("/nhom-may/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm một nhóm máy — kỳ báo cáo đã chốt giữ nguyên số đã chụp")
    @RequirePermission("ops:construction:update")
    public void xoaNhom(@PathVariable UUID publicId) {
        danhMuc.xoaNhom(publicId);
    }

    @PostMapping(path = "/nhom-may/nhap/xem-truoc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Chạy khô tệp nhập trạm bơm — đếm trạm/nhóm máy thêm-sửa, liệt kê lỗi từng dòng")
    @RequirePermission("ops:construction:create")
    public KetQuaNhap xemTruoc(@RequestPart("file") MultipartFile file) {
        return importer.preview(doc(file));
    }

    @PostMapping(path = "/nhom-may/nhap", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập thật — còn dòng lỗi thì không dòng nào được ghi (SYS-0015)")
    @RequirePermission("ops:construction:create")
    public KetQuaNhap nhap(@RequestPart("file") MultipartFile file) {
        return importer.apply(doc(file));
    }

    @GetMapping(path = "/nhom-may/mau-nhap", produces = "text/csv; charset=utf-8")
    @Operation(summary = "Tải tệp mẫu CSV — một dòng một nhóm máy; sinh từ chính danh mục cột bộ đọc dùng")
    @RequirePermission("ops:construction:create")
    public ResponseEntity<byte[]> mauNhap() {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("mau-nhap-tram-bom.csv", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(TramBomImportService.bieuMau());
    }

    private static byte[] doc(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ValidationException(ErrorCode.SYS_0003, e);
        }
    }
}
