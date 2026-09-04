package com.songnhue.hydro.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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
     *
     * <h2>⚠⚠ {@code measurementTypeIds} phải {@code @NotEmpty} — bỏ trường ra là XOÁ SẠCH</h2>
     *
     * Trường này trước 01/09 không có ràng buộc nào, và {@code StationService.loaiChiSo(null)} trả
     * <b>tập rỗng</b> rồi {@code setMeasurementTypes} ghi đè bằng chính tập rỗng ấy. Nghĩa là một
     * lượt {@code PUT} thiếu trường — đúng thứ mọi công cụ tích hợp và mọi bài kiểm dựng thân JSON
     * bằng tay đều làm — <b>gỡ hết liên kết loại chỉ số của điểm đo</b>, trả về <b>200 OK</b>, và
     * không để lại dấu vết nào.
     *
     * <p>Đây là cùng lớp lỗi T28.24 (đổi Nguồn dữ liệu bị vứt) ở chiều ngược lại: ở đó một trường
     * gửi lên bị bỏ qua, ở đây một trường <i>không</i> gửi lên bị hiểu là "xoá hết". Cả hai cùng
     * im lặng vì 200 OK là câu trả lời đúng cho mọi thứ khác trong cùng lượt gửi.
     *
     * <p>⛔ Nó bị bắt bởi một lượt CI ĐỎ chứ không phải bởi lượt rà nào: {@code HydroCatalogueHttpTest}
     * gửi thân 5 trường, làm điểm đo mất liên kết {@code MUC_NUOC}, và
     * {@code HydroCatalogueSeedTest.moiDiemDoDeuDoMucNuoc} đếm ra <b>18/19</b>. Ở máy thì xanh —
     * thứ tự chạy của surefire phụ thuộc hệ tệp, macOS xếp Seed trước Http còn runner Linux xếp
     * ngược lại. Đúng nguyên văn *"xanh ở máy cũng không phải bằng chứng"*.
     *
     * <p>Một điểm đo không đo chỉ số nào là một bản ghi vô nghĩa — nó không sinh được số liệu nào.
     * Nên ràng buộc đúng là {@code @NotEmpty}: gửi thiếu thì <b>422 nói thẳng</b>, thay vì 200 rồi
     * mất dữ liệu.
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
            @NotEmpty Set<UUID> measurementTypeIds) {}

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

    /**
     * Một liên kết điểm đo ↔ công trình — T28.19.
     *
     * <p>⚠ {@code constructionCode} và {@code constructionName} thêm 03/09/2026. Trước đó bản ghi
     * chỉ mang {@code constructionId} (một UUID), nên màn hình <b>chỉ hiện được một chuỗi 36 ký
     * tự</b> — dữ liệu có, người đọc không dùng được. Cùng hình dạng T27.24 (<i>"⛔ không bắt gõ
     * UUID"</i>), chỉ ở chiều đọc.
     *
     * @param constructionCode {@code null} khi công trình đã bị xoá mềm sau lúc liên kết được khai —
     *     giao diện phải nói ra điều đó, ⛔ không giấu cả dòng đi: một liên kết trỏ vào công trình
     *     đã xoá là thứ người vận hành cần thấy để dọn
     */
    public record StationConstructionView(
            UUID id,
            UUID constructionId,
            String constructionCode,
            String constructionName,
            PositionRole role,
            boolean primary) {}

    /**
     * Khai một liên kết — T28.19.
     *
     * <p>⛔ Không có trường {@code stationId}: điểm đo là <b>chủ sở hữu</b> của liên kết và nằm trên
     * đường dẫn. Nhận nó ở cả hai chỗ là mời một lượt gọi mà hai giá trị nói khác nhau.
     */
    public record StationLinkRequest(
            @NotNull UUID constructionId,
            @NotNull PositionRole role,
            /**
             * ⚠ {@code Boolean} chứ ⛔ không {@code boolean}: thiếu trường phải giải về {@code false}
             * ở <b>biên</b>, và chỉ kiểu bọc mới phân biệt được "gửi false" với "không gửi" đủ lâu
             * để controller ghi lại quyết định ấy thành một dòng đọc được.
             */
            Boolean primary) {}
}
