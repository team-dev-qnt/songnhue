package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.ApiSourceStatus;
import com.songnhue.hydro.domain.PositionRole;

/** Kiểu dữ liệu vào/ra của API danh mục thuỷ văn — CN-03.1 / CN-03.2. */
public final class HydroCatalogDtos {

    private HydroCatalogDtos() {}

    // ==== Loại chỉ số ========================================================

    public record MeasurementTypeRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20) String unit,
            Short valueScale,
            Integer sortOrder,
            Boolean active,
            @Size(max = 500) String description) {}

    public record MeasurementTypeView(
            UUID id,
            String code,
            String name,
            String unit,
            Short valueScale,
            Integer sortOrder,
            boolean active,
            String description) {}

    // ==== Nguồn dữ liệu ======================================================

    /**
     * ⛔ <b>Không có trường mã số ở đây.</b> Đặt mã số đi bằng endpoint riêng
     * ({@code PUT /{id}/credential}) để nó không lẫn vào một lần lưu hồ sơ thông thường — mỗi lần
     * chạm vào credential đều phải để lại một sự kiện bảo mật, và một trường lẫn trong form sửa tên
     * sẽ sinh sự kiện cả khi người dùng không định đổi mã số.
     */
    public record ApiSourceRequest(
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 500) String baseUrl,
            Integer frameMinutes,
            Integer timeoutSeconds,
            Integer maxRetry,
            @Size(max = 100) String cron,
            @NotNull ApiSourceStatus status,
            @Size(max = 500) String description) {}

    public record ApiSourceCreateRequest(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 255) String name,
            @NotNull AdapterType adapterType,
            @NotBlank @Size(max = 500) String baseUrl,
            @Size(max = 500) String description) {}

    /**
     * ⚠ Mã số nguyên văn — chỉ đi <b>vào</b>, không bao giờ đi ra.
     *
     * <p>⛔ Không {@code trim()} ở bất kỳ tầng nào: với {@code bhh40.net}, dấu {@code ;} cuối là một
     * phần của giá trị; thiếu nó thì nguồn trả {@code not.working}, trông y hệt lỗi sai mã số.
     */
    public record CredentialRequest(@NotBlank String maSo) {}

    /**
     * ⛔ Không có trường nào mang mã số — chỉ {@code credentialDaCauHinh}.
     *
     * <p>Bốn trường {@code *HieuLuc} là giá trị <b>đã giải</b> (cột riêng hay tham số chung), kèm cờ
     * {@code *DungChung} cho biết nó đến từ đâu. Hiện ô nhập rỗng mà không nói nguồn đang chạy theo
     * cron chung là để người vận hành kết luận "chưa cấu hình" trong khi poller vẫn chạy.
     */
    public record ApiSourceView(
            UUID id,
            String code,
            String name,
            AdapterType adapterType,
            String baseUrl,
            boolean credentialDaCauHinh,
            ApiSourceStatus status,
            String cron,
            Integer frameMinutes,
            Integer timeoutSeconds,
            Integer maxRetry,
            String cronHieuLuc,
            boolean cronDungChung,
            long khungNguonPhutHieuLuc,
            boolean khungDungChung,
            long timeoutGiayHieuLuc,
            boolean timeoutDungChung,
            int soLanThuLaiHieuLuc,
            boolean thuLaiDungChung,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            String lastFailureReason,
            Integer consecutiveFailures,
            int soDiemDo,
            String description) {}

    // ==== Điểm đo ============================================================

    /**
     * ⚠ {@code apiCode} bắt buộc cả khi sửa, và phải gửi <b>đúng giá trị đang có</b> — gửi khác thì
     * nhận {@code HYD-2006}. Bỏ trường ra khỏi DTO sửa thì Jackson lặng lẽ bỏ qua và người tích hợp
     * tưởng mình vừa đổi được mã ánh xạ; im lặng mới là cái bẫy.
     *
     * <p>⚠ {@code riverName} / {@code chainage} / toạ độ để trống là <b>bình thường</b> ở v1: G8
     * chưa có dữ liệu cho 19 điểm đo. ⛔ Không suy từ tên, không điền cho đẹp.
     */
    public record StationRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Pattern(regexp = "^[Ff][0-9]{5}$") String apiCode,
            @NotNull UUID apiSourceId,
            @NotNull PositionRole positionRole,
            UUID orgUnitId,
            @Size(max = 100) String riverName,
            @Size(max = 20) String chainage,
            BigDecimal latitude,
            BigDecimal longitude,
            Boolean interpolated,
            Boolean active,
            @Size(max = 500) String description,
            Set<UUID> measurementTypeIds) {}

    public record StationView(
            UUID id,
            String code,
            String name,
            String apiCode,
            UUID apiSourceId,
            String apiSourceCode,
            PositionRole positionRole,
            UUID orgUnitId,
            String orgUnitName,
            String riverName,
            String chainage,
            Integer chainageM,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean interpolated,
            boolean active,
            String description,
            List<MeasurementTypeView> measurementTypes,
            List<StationConstructionView> constructions,
            /** ⚠ Điểm đo {@code MN_SONG} không liên kết công trình nào là HỢP LỆ, không phải thiếu. */
            boolean thieuLienKetCongTrinh,
            boolean chuaGanDonVi) {}

    public record StationConstructionView(UUID id, UUID constructionId, PositionRole role, boolean primary) {}
}
