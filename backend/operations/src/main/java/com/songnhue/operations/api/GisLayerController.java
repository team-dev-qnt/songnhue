package com.songnhue.operations.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.AttachmentContent;
import com.songnhue.operations.application.GisLayerForm;
import com.songnhue.operations.application.GisLayerService;
import com.songnhue.operations.domain.GisGeometryType;
import com.songnhue.operations.domain.GisLayer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Lớp bản đồ GIS — {@code /api/v1/ops/gis-layers} (CN-02.4 / M2.9).
 *
 * <h2>⭐ Đầu nhận ĐẦU TIÊN của {@code ops:gis-layer:view} và {@code ops:gis-layer:manage}</h2>
 *
 * <p>Hai mã quyền cuối cùng còn <b>0 endpoint</b> kể từ 13/08/2026. Cùng với
 * {@code ops:report:*} (BC-06/09/10), đây là bốn dòng miễn kiểm *"Phase 3"* cuối của
 * {@code RbacMatrixTest}.
 *
 * <h2>⛔ Lớp bản đồ ⛔ KHÔNG cắt theo phạm vi đơn vị</h2>
 *
 * <p>Một lớp *"Ranh giới lưu vực sông Nhuệ"* ⛔ không thuộc về Xí nghiệp nào — nó là nền bản đồ
 * dùng chung. Xem javadoc {@link GisLayer}.
 *
 * <h2>⛔ Nội dung tệp đi QUA máy chủ, ⛔ không qua presigned URL</h2>
 *
 * <p>Một presigned URL sống 10 phút và ⛔ không mang phiên đăng nhập, tức ai có chuỗi ấy đều tải
 * được cả lớp bản đồ. Ở đây nội dung đi thẳng qua máy chủ, sau cổng quyền.
 */
@RestController
@RequestMapping("/api/v1/ops/gis-layers")
@Tag(name = "02-ops · Lớp bản đồ GIS", description = "Nạp GeoJSON, bật/tắt, độ mờ, thứ tự chồng lớp — M2.9")
public class GisLayerController {

    private final GisLayerService layers;

    public GisLayerController(GisLayerService layers) {
        this.layers = layers;
    }

    /**
     * @param opacity phần trăm <b>NGUYÊN</b> 0–100, ⛔ không phải 0.0–1.0 — xem cột
     *     {@code gis_layers.opacity}
     * @param color {@code null} = giữ nguyên, ⛔ không phải "đặt về mặc định"
     */
    public record LayerRequest(
            @NotBlank @Size(max = 255) String name,
            @Size(max = 500) String description,
            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Màu phải dạng #RRGGBB") String color,
            @Min(0) @Max(100) Short opacity,
            Integer sortOrder,
            Boolean active) {}

    public record ThuTuRequest(@Size(max = 200) List<UUID> theoThuTu) {}

    /**
     * @param soDoiTuong {@code null} = lớp <b>chưa nạp tệp</b>. ⛔ Khác {@code 0} — số 0 ⛔ không
     *     biểu diễn được ở đây vì một tệp rỗng hình học bị từ chối ngay lúc nạp ({@code OPS-2026})
     */
    public record LayerView(
            UUID publicId,
            String name,
            String description,
            GisGeometryType geometryType,
            String color,
            short opacity,
            int sortOrder,
            boolean active,
            boolean coTep,
            Integer soDoiTuong) {

        static LayerView of(GisLayer l) {
            return new LayerView(
                    l.getPublicId(),
                    l.getName(),
                    l.getDescription(),
                    l.getGeometryType(),
                    l.getColor(),
                    l.getOpacity(),
                    l.getSortOrder(),
                    l.isActive(),
                    l.getAttachmentPublicId() != null,
                    l.getFeatureCount());
        }
    }

    /**
     * @param chiDangBat {@code true} = chỉ lớp đang bật (bảng lớp trên bản đồ); {@code false} =
     *     kể cả lớp đã tắt (màn hình quản trị)
     */
    @GetMapping
    @Operation(summary = "Danh sách lớp bản đồ, đã sắp theo thứ tự chồng lớp")
    @RequirePermission("ops:gis-layer:view")
    public List<LayerView> danhSach(@RequestParam(defaultValue = "true") boolean chiDangBat) {
        return layers.danhSach(chiDangBat).stream().map(LayerView::of).toList();
    }

    /**
     * Nội dung GeoJSON của một lớp.
     *
     * <p>⚠ {@code Content-Length} lấy từ CSDL nên trình duyệt hiện được thanh tiến trình — cùng lý
     * lẽ {@code AttachmentPort.readForPublic}.
     */
    @GetMapping("/{publicId}/noi-dung")
    @Operation(summary = "GeoJSON của lớp — đi qua máy chủ, sau cổng quyền")
    @RequirePermission("ops:gis-layer:view")
    public ResponseEntity<Resource> noiDung(@PathVariable UUID publicId) {
        AttachmentContent tep = layers.noiDung(publicId);
        return ResponseEntity.ok()
                .contentLength(tep.sizeBytes())
                .contentType(MediaType.valueOf("application/geo+json"))
                .body(new InputStreamResource(tep.content()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khai một lớp — nạp tệp ở bước sau")
    @RequirePermission("ops:gis-layer:manage")
    public LayerView tao(@Valid @RequestBody LayerRequest request) {
        return LayerView.of(layers.tao(toForm(request)));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa thuộc tính trình bày của lớp")
    @RequirePermission("ops:gis-layer:manage")
    public LayerView sua(@PathVariable UUID publicId, @Valid @RequestBody LayerRequest request) {
        return LayerView.of(layers.sua(publicId, toForm(request)));
    }

    /**
     * Nạp tệp GeoJSON.
     *
     * <p>⛔ KML/KMZ trả {@code OPS-2025} — kho chưa có bộ đọc, và nhận rồi lưu là để người dùng
     * thấy *"nạp thành công"* rồi nhìn một bản đồ trống.
     */
    @PostMapping(path = "/{publicId}/tep", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nạp GeoJSON ≤ 20MB — KML/KMZ trả OPS-2025")
    @RequirePermission("ops:gis-layer:manage")
    public LayerView napTep(@PathVariable UUID publicId, @RequestPart("file") MultipartFile file) throws IOException {
        return LayerView.of(layers.napTep(publicId, file.getOriginalFilename(), file.getBytes()));
    }

    /**
     * Sắp lại thứ tự chồng lớp.
     *
     * <p>⛔ Nhận <b>toàn bộ danh sách đã sắp</b>, ⛔ không nhận "lên một bậc": hai nơi cùng phải
     * biết thứ tự hiện tại là hai nơi sẽ lệch.
     */
    @PatchMapping("/thu-tu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Sắp lại thứ tự chồng lớp — gửi TOÀN BỘ danh sách đã sắp")
    @RequirePermission("ops:gis-layer:manage")
    public void sapThuTu(@Valid @RequestBody ThuTuRequest request) {
        layers.sapThuTu(request.theoThuTu());
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm một lớp")
    @RequirePermission("ops:gis-layer:manage")
    public void xoa(@PathVariable UUID publicId) {
        layers.xoa(publicId);
    }

    private static GisLayerForm toForm(LayerRequest r) {
        return new GisLayerForm(r.name(), r.description(), r.color(), r.opacity(), r.sortOrder(), r.active());
    }
}
