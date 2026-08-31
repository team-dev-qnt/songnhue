package com.songnhue.hydro.api;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.hydro.application.ApiSourceForm;
import com.songnhue.hydro.application.ApiSourceService;
import com.songnhue.hydro.application.StationService;
import com.songnhue.hydro.application.ThamSoNguon;
import com.songnhue.hydro.domain.ApiSource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Nguồn dữ liệu bên thứ ba — {@code /api/v1/hyd/api-sources/**} (T28.2).
 *
 * <h2>⛔ Không endpoint nào ở đây trả mã số, kể cả cho SUPER_ADMIN</h2>
 *
 * <p>{@code conventions.md} §4.7. Giao diện chỉ nhận biết "đã cấu hình / chưa cấu hình"; muốn đổi thì
 * gõ lại mã mới. Trả về dạng che một phần cũng không được — vài ký tự cuối của một mã số ngắn thu
 * hẹp không gian tìm kiếm rất nhiều, mà lợi ích chỉ là đỡ phải hỏi lại người giữ mã.
 *
 * <p>Quyền {@code hyd:api-source:manage} hiện chỉ SUPER_ADMIN có ({@code V202608131007} cấp toàn bộ
 * quyền cho vai trò này qua {@code CROSS JOIN}; không vai trò nào khác được liệt kê). Đó là chủ ý:
 * cấu hình nguồn dữ liệu ngoài là việc của quản trị hệ thống.
 */
@RestController
@RequestMapping("/api/v1/hyd/api-sources")
@Tag(name = "03-hyd · Nguồn dữ liệu", description = "Cấu hình nguồn quan trắc bên thứ 3 — mã số không bao giờ trả ra")
public class ApiSourceController {

    private final ApiSourceService sources;
    private final StationService stations;

    public ApiSourceController(ApiSourceService sources, StationService stations) {
        this.sources = sources;
        this.stations = stations;
    }

    /**
     * ⚠⚠ Sửa 01/09/2026 — hai quyền, chế độ HOẶC. Trước đó chỉ {@code hyd:api-source:manage}.
     *
     * <p>Ô "Nguồn dữ liệu" của màn hình Điểm đo là trường <b>bắt buộc</b>, và nó nạp danh sách bằng
     * đúng endpoint này. Đo trên ma trận seed: {@code TECHNICIAN} là vai trò duy nhất ngoài
     * SUPER_ADMIN/ADMIN có {@code hyd:station:manage}, và nó <b>không</b> có
     * {@code hyd:api-source:manage} ⇒ danh sách vĩnh viễn rỗng ⇒ <b>không tạo nổi một điểm đo nào</b>.
     *
     * <p>Đúng hình dạng T27.20 (§10 — việc bị chôn sau một quyền khác), tái phát ở WS-28: quyền cấp
     * đúng, màn hình có thật, endpoint có thật — chỉ là đường nạp dữ liệu cho một ô bắt buộc lại
     * nằm sau một quyền mà vai trò ấy không có. Đơn vị đếm sai: đếm <i>màn hình đã dựng</i> thì
     * xanh, đếm <i>vai trò × việc họ phải làm được</i> thì ra số không.
     *
     * <p>⛔ Chỉ nới đường ĐỌC danh sách. Thêm/sửa/xoá/đặt mã số vẫn đòi riêng
     * {@code hyd:api-source:manage} — và {@code ApiSourceView} không mang credential (§4.7), nên mở
     * danh sách không lộ gì.
     */
    @GetMapping
    @Operation(summary = "Danh sách nguồn dữ liệu")
    @RequirePermission({"hyd:api-source:manage", "hyd:station:manage"})
    public List<HydroCatalogDtos.ApiSourceView> list() {
        return sources.list().stream().map(this::toView).toList();
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết nguồn — kèm tham số nhịp ĐÃ GIẢI")
    @RequirePermission("hyd:api-source:manage")
    public HydroCatalogDtos.ApiSourceView get(@PathVariable UUID publicId) {
        return toView(sources.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm nguồn dữ liệu")
    @RequirePermission("hyd:api-source:manage")
    public HydroCatalogDtos.ApiSourceView create(@Valid @RequestBody HydroCatalogDtos.ApiSourceCreateRequest request) {
        return toView(sources.create(
                request.code(), request.name(), request.adapterType(), request.baseUrl(), request.description()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa nguồn — để trống một tham số nhịp nghĩa là dùng tham số chung")
    @RequirePermission("hyd:api-source:manage")
    public HydroCatalogDtos.ApiSourceView update(
            @PathVariable UUID publicId, @Valid @RequestBody HydroCatalogDtos.ApiSourceRequest request) {
        return toView(sources.update(
                publicId,
                new ApiSourceForm(
                        request.name(),
                        request.baseUrl(),
                        request.frameMinutes(),
                        request.timeoutSeconds(),
                        request.maxRetry(),
                        request.cron(),
                        request.status(),
                        request.description())));
    }

    /**
     * Đặt hoặc thay mã số.
     *
     * <p>Endpoint riêng, không lẫn vào form sửa hồ sơ: mỗi lần chạm vào credential đều ghi một sự
     * kiện bảo mật, và một trường lẫn trong form sửa tên sẽ sinh sự kiện cả khi người dùng không định
     * đổi mã số — nhật ký đầy sự kiện giả thì sự kiện thật không còn ai để ý.
     */
    @PutMapping("/{publicId}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đặt / thay mã số truy cập — ⚠ giữ nguyên dấu ';' cuối nếu nguồn đòi")
    @RequirePermission("hyd:api-source:manage")
    public void datMaSo(@PathVariable UUID publicId, @Valid @RequestBody HydroCatalogDtos.CredentialRequest request) {
        sources.datMaSo(publicId, request.maSo());
    }

    @DeleteMapping("/{publicId}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gỡ mã số — nguồn về trạng thái 'chưa cấu hình', poller từ chối chạy")
    @RequirePermission("hyd:api-source:manage")
    public void xoaMaSo(@PathVariable UUID publicId) {
        sources.xoaMaSo(publicId);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá nguồn — chặn khi còn điểm đo trỏ vào (HYD-1002)")
    @RequirePermission("hyd:api-source:manage")
    public void delete(@PathVariable UUID publicId) {
        sources.delete(publicId);
    }

    private HydroCatalogDtos.ApiSourceView toView(ApiSource nguon) {
        ThamSoNguon thamSo = sources.thamSoHieuLuc(nguon);
        return new HydroCatalogDtos.ApiSourceView(
                nguon.getPublicId(),
                nguon.getCode(),
                nguon.getName(),
                nguon.getAdapterType(),
                nguon.getBaseUrl(),
                nguon.isCredentialDaCauHinh(),
                nguon.getStatus(),
                nguon.getCron(),
                nguon.getFrameMinutes(),
                nguon.getTimeoutSeconds(),
                nguon.getMaxRetry(),
                thamSo.cron(),
                thamSo.cronDungChung(),
                thamSo.khungNguon().toMinutes(),
                thamSo.khungDungChung(),
                thamSo.timeout().toSeconds(),
                thamSo.timeoutDungChung(),
                thamSo.soLanThuLai(),
                thamSo.thuLaiDungChung(),
                nguon.getLastSuccessAt(),
                nguon.getLastFailureAt(),
                nguon.getLastFailureReason(),
                nguon.getConsecutiveFailures(),
                stations.soDiemDoCuaNguon(nguon.getId()),
                nguon.getDescription());
    }
}
