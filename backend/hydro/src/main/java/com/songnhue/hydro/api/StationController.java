package com.songnhue.hydro.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

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
import com.songnhue.core.spi.ConstructionRef;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.hydro.application.StationConstructionService;
import com.songnhue.hydro.application.StationForm;
import com.songnhue.hydro.application.StationMapService;
import com.songnhue.hydro.application.StationService;
import com.songnhue.hydro.application.importer.StationLocationImportService;
import com.songnhue.hydro.domain.ApiSource;
import com.songnhue.hydro.domain.Station;
import com.songnhue.hydro.domain.StationConstruction;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục điểm đo — {@code /api/v1/hyd/stations/**} (T28.3, T28.8, T28.9).
 *
 * <h2>⚠ Một endpoint, hai câu hỏi — và danh sách việc còn thiếu đi cùng mỗi dòng</h2>
 *
 * <p>{@code GET /} vừa là danh mục vừa là <b>danh sách việc còn thiếu</b>: cờ {@code chuaGanDonVi}
 * đi kèm <i>mỗi</i> dòng, nên {@code StationsPage} dựng được cả hai bảng đếm trong <b>một</b> lượt
 * gọi. Đó là hệ quả trực tiếp của OI-05 — cho tới khi phần còn thiếu rỗng, resolver người nhận cảnh
 * báo (G11 tập 2) không tìm được ai để gửi; một cảnh báo không có người nhận là một cảnh báo không
 * tồn tại, và nó ⛔ không báo lỗi ở đâu cả. Nên phần còn thiếu phải hiện thành một <b>con số trên
 * màn hình</b>, ⛔ không phải một dòng trong tài liệu.
 *
 * <p>⚠⚠ Đoạn trên trước 07/09/2026 mô tả {@code GET /chua-gan-don-vi} như một endpoint <b>đang
 * sống</b>, trong khi nó đã bị gỡ ngày 04/09 (nợ T28.30) — bia mộ nằm ngay dưới, cách đó 50 dòng,
 * và <b>mâu thuẫn với chính lời mở đầu này</b>. §10.69: một dòng chú thích <i>nói dối</i> khó thấy
 * hơn hẳn một dòng không ai đọc, vì người sau tin nó và đi tìm một thứ không tồn tại.
 */
@RestController
@RequestMapping("/api/v1/hyd/stations")
@Tag(name = "03-hyd · Điểm đo", description = "Danh mục điểm đo và ánh xạ mã API bên thứ 3")
public class StationController {

    private final StationService stations;
    private final StationConstructionService lienKets;
    private final OrgUnitPort orgUnits;
    private final StationMapService banDo;
    private final StationLocationImportService viTri;

    public StationController(
            StationService stations,
            StationConstructionService lienKets,
            OrgUnitPort orgUnits,
            StationMapService banDo,
            StationLocationImportService viTri) {
        this.stations = stations;
        this.lienKets = lienKets;
        this.orgUnits = orgUnits;
        this.banDo = banDo;
        this.viTri = viTri;
    }

    /**
     * Lớp GIS "Điểm đo thuỷ văn" — <b>T35.1</b>, kèm danh sách chưa số hoá vị trí (<b>T35.2</b>).
     *
     * <h2>⚠ Phụ thuộc chéo phải ghi ở CHỖ GỌI, không ở tài liệu</h2>
     *
     * <p>Bản đồ mà lớp này vẽ lên nằm ở màn hình điều hành của {@code operations}, mở bằng
     * {@code ops:dashboard:view}; còn endpoint này đòi {@code hyd:station:view}. Đo ngày 04/09/2026:
     * <b>cả 5</b> vai trò có {@code ops:dashboard:view} (CLERK · TECHNICIAN · XN_MANAGER ·
     * XN_OPERATOR · DUTY_OFFICER) đều có {@code hyd:station:view}, nên hôm nay ⛔ không ai mở được
     * bản đồ mà thiếu lớp.
     *
     * <p>⚠ Ngày nào có một vai trò mới được {@code ops:dashboard:view} mà ⛔ không có
     * {@code hyd:station:view}, lớp này sẽ <b>im lặng rỗng</b> — bản đồ vẫn vẽ, chỉ thiếu chấm, và
     * ⛔ không có thông báo lỗi nào. Đúng hình dạng T27.20/T28.25 đã tái phát hai lần.
     */
    @GetMapping("/map-points")
    @Operation(summary = "Lớp GIS điểm đo + danh sách chưa số hoá vị trí")
    @RequirePermission("hyd:station:view")
    public HydroMapDtos.LopDiemDoView mapPoints() {
        return banDo.lopDiemDo();
    }

    /**
     * ⛔ <b>Gỡ 04/09/2026: {@code GET /chua-gan-don-vi}</b> — nợ <b>T28.30</b>, đóng bằng cách XOÁ.
     *
     * <p>Nó trả lời <i>"điểm đo nào chưa gán đơn vị"</i>, và câu hỏi ấy <b>đã có câu trả lời</b> đi
     * cùng mỗi dòng của chính endpoint này: cờ {@code chuaGanDonVi} trên {@code StationView}, tính
     * từ {@code Station.chuaGanDonVi()}. {@code StationsPage} lọc theo cờ ấy và còn làm được nhiều
     * hơn — <b>hai</b> danh sách việc cần làm kèm số đếm, trong <b>một</b> lượt gọi.
     *
     * <p>⇒ Endpoint kia có <b>0 nơi gọi</b> từ giao diện suốt từ WS-28. Nó ⛔ không phải một cơ chế
     * đang chờ người dùng — nó là <b>nửa còn thừa</b> của một cặp đã hoàn chỉnh ở chỗ khác, và
     * §10.33 đã chốt cách xử lý: <i>"Phase sau đến mà vẫn không ai gọi thì XOÁ, không phải giữ"</i>.
     *
     * <p>⚠ Có một bài kiểm HTTP đi qua nó ({@code HydroCatalogueHttpTest}) — nhưng <b>bị kiểm ⛔
     * không phải là được dùng</b>. Bài ấy ra đời để bắt lỗi {@code LazyInitializationException} của
     * {@code toView}, và {@code list()} đã canh đúng lỗi đó rồi.
     */
    @GetMapping
    @Operation(summary = "Danh sách điểm đo")
    @RequirePermission("hyd:station:view")
    public List<HydroCatalogDtos.StationView> list() {
        return stations.list().stream().map(this::toView).toList();
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết điểm đo")
    @RequirePermission("hyd:station:view")
    public HydroCatalogDtos.StationView get(@PathVariable UUID publicId) {
        return toView(stations.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm điểm đo")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.StationView create(@Valid @RequestBody HydroCatalogDtos.StationRequest request) {
        return toView(stations.create(hoSo(request)));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa điểm đo — ⛔ đổi apiCode bị từ chối (HYD-2006)")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.StationView update(
            @PathVariable UUID publicId, @Valid @RequestBody HydroCatalogDtos.StationRequest request) {
        return toView(stations.update(publicId, hoSo(request)));
    }

    /**
     * ⭐ Một hàm đổi DTO → form, dùng cho <b>cả hai</b> đường ghi — T28.33.
     *
     * <p>Trước 02/09 chỉ {@code update} dựng {@link StationForm}, còn {@code create} tự chuyển tay
     * <b>7 trong 14</b> trường. Hai cách viết cạnh nhau cho cùng một DTO là chỗ chênh lệch sinh ra
     * và không ai thấy: {@code @Valid} chạy trên đủ 14 trường ở cả hai đường, nên lượt {@code POST}
     * kèm toạ độ vẫn <b>201 Created</b> trong khi toạ độ không tới được tầng dưới.
     *
     * <p>⚠ Hai phép giải mặc định nằm ở đây chứ không ở tầng application, có chủ ý: {@code Boolean}
     * của DTO mang <b>ba</b> trạng thái (true / false / không gửi) còn nghiệp vụ chỉ có hai. Giải
     * ngay tại biên là chỗ duy nhất còn phân biệt được "không gửi" — sâu hơn thì thông tin ấy mất.
     */
    private static StationForm hoSo(HydroCatalogDtos.StationRequest request) {
        return new StationForm(
                request.code(),
                request.name(),
                request.apiCode(),
                request.apiSourceId(),
                request.positionRole(),
                request.orgUnitId(),
                request.riverName(),
                request.chainage(),
                request.latitude(),
                request.longitude(),
                request.interpolated() != null && request.interpolated(),
                request.active() == null || request.active(),
                request.description(),
                request.measurementTypeIds());
    }

    @PostMapping("/{publicId}/lien-ket")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Khai liên kết điểm đo ↔ công trình (T28.19)")
    @RequirePermission("hyd:station:manage")
    public HydroCatalogDtos.StationConstructionView lienKet(
            @PathVariable UUID publicId, @Valid @RequestBody HydroCatalogDtos.StationLinkRequest request) {
        StationConstruction lienKet = lienKets.lienKet(
                publicId, request.constructionId(), request.role(), Boolean.TRUE.equals(request.primary()));
        return toLinkView(lienKet, lienKets.congTrinhCua(List.of(lienKet)));
    }

    @DeleteMapping("/lien-ket/{lienKetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bỏ liên kết điểm đo ↔ công trình")
    @RequirePermission("hyd:station:manage")
    public void boLienKet(@PathVariable UUID lienKetId) {
        lienKets.boLienKet(lienKetId);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm điểm đo")
    @RequirePermission("hyd:station:manage")
    public void delete(@PathVariable UUID publicId) {
        stations.delete(publicId);
    }

    private HydroCatalogDtos.StationView toView(Station diemDo) {
        List<StationConstruction> lienKet = stations.lienKetCua(diemDo);
        OrgUnitRef donVi = diemDo.getOrgUnitId() == null
                ? null
                : orgUnits.findRefById(diemDo.getOrgUnitId()).orElse(null);
        ApiSource nguon = stations.nguonCua(diemDo);
        return new HydroCatalogDtos.StationView(
                diemDo.getPublicId(),
                diemDo.getCode(),
                diemDo.getName(),
                diemDo.getApiCode(),
                nguon == null ? null : nguon.getPublicId(),
                nguon == null ? null : nguon.getCode(),
                diemDo.getPositionRole(),
                donVi == null ? null : donVi.publicId(),
                donVi == null ? null : donVi.name(),
                diemDo.getRiverName(),
                diemDo.getChainage(),
                diemDo.getChainageM(),
                diemDo.getLatitude(),
                diemDo.getLongitude(),
                diemDo.isInterpolated(),
                diemDo.isActive(),
                diemDo.getDescription(),
                diemDo.getMeasurementTypes().stream()
                        .map(MeasurementTypeController::toView)
                        .toList(),
                toLinkViews(lienKet),
                lienKet.isEmpty() && !diemDo.duocPhepKhongGanCongTrinh(),
                diemDo.chuaGanDonVi());
    }

    /**
     * ⚠ MỘT lượt tra cho cả danh sách — {@code timTheoIds} là hàm gộp, ⛔ không gọi trong vòng lặp.
     * Màn hình điểm đo hiện 19 dòng, mỗi dòng có thể có vài liên kết; gọi lẻ là N+1 ngay ở màn hình
     * đầu tiên người dùng mở.
     */
    private List<HydroCatalogDtos.StationConstructionView> toLinkViews(List<StationConstruction> lienKet) {
        Map<Long, ConstructionRef> congTrinh = lienKets.congTrinhCua(lienKet);
        return lienKet.stream().map(l -> toLinkView(l, congTrinh)).toList();
    }

    private static HydroCatalogDtos.StationConstructionView toLinkView(
            StationConstruction l, Map<Long, ConstructionRef> congTrinh) {
        ConstructionRef ct = congTrinh.get(l.getConstructionId());
        return new HydroCatalogDtos.StationConstructionView(
                l.getPublicId(),
                l.getConstructionPublicId(),
                ct == null ? null : ct.code(),
                ct == null ? null : ct.name(),
                l.getRole(),
                l.isPrimary());
    }

    // === Nhập vị trí hàng loạt — G8 phần còn lại =============================

    /**
     * ⭐⭐ Chạy khô tệp vị trí — ⛔ <b>không ghi một dòng nào</b>.
     *
     * <p>Tính tới 09/09/2026 toạ độ của <b>19/19</b> điểm đo vẫn NULL ⇒ lớp GIS RỖNG. Đường sửa duy
     * nhất trước đây là mở màn hình điểm đo và sửa từng bản ghi, 19 lượt. Ngày Công ty gửi bảng toạ
     * độ, người quản trị chỉ cần <b>tải mẫu → điền → upload</b>, ⛔ không cần thêm một đợt lập trình.
     *
     * <p>⚠ Quyền {@code hyd:station:manage} — cùng quyền với sửa một điểm đo, vì đây đúng là thao
     * tác ấy làm hàng loạt.
     */
    @PostMapping(path = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Chạy khô tệp vị trí điểm đo — liệt kê lỗi từng dòng, ⛔ không ghi gì")
    @RequirePermission("hyd:station:manage")
    public KetQuaNhap previewImport(@RequestPart("file") MultipartFile file) {
        return viTri.preview(doc(file));
    }

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Nhập thật — còn dòng lỗi thì ⛔ không dòng nào được ghi (OPS-2016)")
    @RequirePermission("hyd:station:manage")
    public KetQuaNhap applyImport(@RequestPart("file") MultipartFile file) {
        return viTri.apply(doc(file));
    }

    /** Tệp mẫu, sinh từ chính danh mục cột mà bộ đọc dùng — ⛔ không phải một tệp tĩnh (luật 14). */
    @GetMapping(path = "/import/template", produces = "text/csv; charset=utf-8")
    @Operation(summary = "Tải tệp mẫu CSV — tiêu đề + một dòng mô tả quy cách từng cột")
    @RequirePermission("hyd:station:manage")
    public ResponseEntity<byte[]> importTemplate() {
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("mau-nhap-vi-tri-diem-do.csv", StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.parseMediaType("text/csv; charset=utf-8"))
                .body(StationLocationImportService.bieuMau());
    }

    /**
     * Đọc nội dung tệp tải lên.
     *
     * <p>Lỗi đọc quy về {@code SYS-0003} chứ ⛔ không để {@link java.io.IOException} chui ra: một tệp
     * hỏng giữa chừng là lỗi của dữ liệu vào, ⛔ không phải sự cố hệ thống, và trả 500 cho nó sẽ làm
     * cảnh báo vận hành kêu vì chuyện người dùng gửi nhầm tệp.
     */
    private static byte[] doc(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw new ValidationException(ErrorCode.SYS_0003, e);
        }
    }
}
