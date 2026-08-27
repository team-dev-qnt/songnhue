package com.songnhue.operations.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.operations.application.ConstructionForm;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionPurpose;
import com.songnhue.operations.domain.ConstructionType;
import com.songnhue.operations.domain.LifecycleState;
import com.songnhue.operations.domain.ManagementLevel;
import com.songnhue.operations.domain.OperationalStatus;

/** Kiểu dữ liệu vào/ra của API công trình — CN-02.1. */
public final class ConstructionDtos {

    private ConstructionDtos() {}

    /**
     * Yêu cầu tạo/sửa hồ sơ.
     *
     * <p>⚠ {@link #operationalStatus} có mặt ở đây <b>chỉ để từ chối</b>. Trạng thái vận hành là giá
     * trị dẫn xuất (quy tắc 4), nhưng bỏ hẳn trường ra khỏi DTO thì Jackson lặng lẽ bỏ qua khoá thừa
     * và người tích hợp tưởng mình vừa đặt được trạng thái. Nhận rồi trả {@code OPS-3001} nói thẳng
     * rằng thứ họ gửi không có tác dụng — im lặng mới là cái bẫy.
     *
     * <p>{@code lifecycleState} cũng không có: đổi vòng đời đi bằng endpoint riêng, có lý do kèm theo.
     */
    public record SaveRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @NotNull ConstructionType constructionType,
            ConstructionPurpose purpose,
            @NotNull UUID orgUnitId,
            ManagementLevel managementLevel,
            UUID clusterId,
            @Size(max = 500) String address,
            BigDecimal latitude,
            BigDecimal longitude,
            @Size(max = 100) String riverName,
            @Size(max = 20) String chainage,
            @Size(max = 500) String basinNote,
            Short builtYear,
            Short commissionedYear,
            @Size(max = 255) String designer,
            @Size(max = 255) String contractor,
            BigDecimal totalInvestment,
            /** Xem ghi chú ở {@code ConstructionForm} — hai cột từng đọc-được-mà-không-ghi-được. */
            UUID operatingProcedureAttachmentId,
            UUID protectionPlanAttachmentId,
            String description,
            PumpSpecRequest pump,
            SluiceSpecRequest sluice,
            LinearSpecRequest linear,
            OperationalStatus operationalStatus) {

        public ConstructionForm toForm() {
            return new ConstructionForm(
                    code,
                    name,
                    constructionType,
                    purpose,
                    orgUnitId,
                    managementLevel,
                    clusterId,
                    address,
                    latitude,
                    longitude,
                    riverName,
                    chainage,
                    basinNote,
                    builtYear,
                    commissionedYear,
                    designer,
                    contractor,
                    totalInvestment,
                    operatingProcedureAttachmentId,
                    protectionPlanAttachmentId,
                    description,
                    pump == null ? null : pump.toSpec(),
                    sluice == null ? null : sluice.toSpec(),
                    linear == null ? null : linear.toSpec());
        }
    }

    /** ⚠ Không có {@code totalFlowM3s}: CSDL tính từ số máy × lưu lượng/máy (CN-02.1 ghi "auto"). */
    public record PumpSpecRequest(
            BigDecimal totalPowerKw,
            Short pumpCount,
            Short standbyPumpCount,
            BigDecimal flowPerPumpM3s,
            BigDecimal headM,
            @Size(max = 100) String powerSource,
            BigDecimal voltageKv,
            BigDecimal operatingLevelMinM,
            BigDecimal operatingLevelMaxM) {

        ConstructionForm.PumpSpec toSpec() {
            return new ConstructionForm.PumpSpec(
                    totalPowerKw,
                    pumpCount,
                    standbyPumpCount,
                    flowPerPumpM3s,
                    headM,
                    powerSource,
                    voltageKv,
                    operatingLevelMinM,
                    operatingLevelMaxM);
        }
    }

    public record SluiceSpecRequest(
            @Size(max = 20) String sluiceType,
            Short bayCount,
            BigDecimal bayWidthM,
            BigDecimal sillElevationM,
            BigDecimal crestElevationM,
            BigDecimal designFlowM3s,
            @Size(max = 20) String gateOperation,
            BigDecimal upstreamWarningLevelM,
            BigDecimal upstreamDangerLevelM) {

        ConstructionForm.SluiceSpec toSpec() {
            return new ConstructionForm.SluiceSpec(
                    sluiceType,
                    bayCount,
                    bayWidthM,
                    sillElevationM,
                    crestElevationM,
                    designFlowM3s,
                    gateOperation,
                    upstreamWarningLevelM,
                    upstreamDangerLevelM);
        }
    }

    public record LinearSpecRequest(
            BigDecimal lengthKm,
            @Size(max = 20) String startChainage,
            @Size(max = 20) String endChainage,
            BigDecimal designFlowM3s,
            BigDecimal crestElevationM,
            @Size(max = 20) String technicalGrade,
            @Size(max = 255) String crossSection,
            String specNote) {

        ConstructionForm.LinearSpec toSpec() {
            return new ConstructionForm.LinearSpec(
                    lengthKm,
                    startChainage,
                    endChainage,
                    designFlowM3s,
                    crestElevationM,
                    technicalGrade,
                    crossSection,
                    specNote);
        }
    }

    /** Một dòng trên danh sách — cố ý gọn, không kéo theo thông số kỹ thuật. */
    public record ConstructionRow(
            UUID publicId,
            String code,
            String name,
            ConstructionType constructionType,
            ManagementLevel managementLevel,
            String orgUnitName,
            String clusterName,
            String riverName,
            String chainage,
            BigDecimal latitude,
            BigDecimal longitude,
            boolean located,
            LifecycleState lifecycleState,
            OperationalStatus operationalStatus,
            Instant updatedAt) {

        public static ConstructionRow of(Construction c, String orgUnitName, String clusterName) {
            return new ConstructionRow(
                    c.getPublicId(),
                    c.getCode(),
                    c.getName(),
                    c.getConstructionType(),
                    c.getManagementLevel(),
                    orgUnitName,
                    clusterName,
                    c.getRiverName(),
                    c.getChainage(),
                    c.getLatitude(),
                    c.getLongitude(),
                    c.daSoHoaViTri(),
                    c.getLifecycleState(),
                    c.getOperationalStatus(),
                    c.getUpdatedAt());
        }
    }

    /** Hồ sơ đầy đủ — kèm đúng khối thông số của loại công trình đó, ba khối kia là {@code null}. */
    public record ConstructionDetail(
            ConstructionRow summary,
            UUID orgUnitId,
            UUID clusterId,
            ConstructionPurpose purpose,
            String address,
            Integer chainageM,
            String basinNote,
            Short builtYear,
            Short commissionedYear,
            String designer,
            String contractor,
            BigDecimal totalInvestment,
            UUID operatingProcedureAttachmentId,
            UUID protectionPlanAttachmentId,
            String description,
            PumpSpecView pump,
            SluiceSpecView sluice,
            LinearSpecView linear) {}

    /** ⭐ {@code totalFlowM3s} là cột sinh ở CSDL — FE chỉ hiển thị, không nhân lại (quy tắc 3). */
    public record PumpSpecView(
            BigDecimal totalPowerKw,
            Short pumpCount,
            Short standbyPumpCount,
            BigDecimal flowPerPumpM3s,
            BigDecimal totalFlowM3s,
            BigDecimal headM,
            String powerSource,
            BigDecimal voltageKv,
            BigDecimal operatingLevelMinM,
            BigDecimal operatingLevelMaxM) {}

    public record SluiceSpecView(
            String sluiceType,
            Short bayCount,
            BigDecimal bayWidthM,
            BigDecimal sillElevationM,
            BigDecimal crestElevationM,
            BigDecimal designFlowM3s,
            String gateOperation,
            BigDecimal upstreamWarningLevelM,
            BigDecimal upstreamDangerLevelM) {}

    public record LinearSpecView(
            BigDecimal lengthKm,
            String startChainage,
            String endChainage,
            BigDecimal designFlowM3s,
            BigDecimal crestElevationM,
            String technicalGrade,
            String crossSection,
            String specNote) {}

    /** @param reason bắt buộc — thanh lý một công trình mà không nói vì sao thì nhật ký vô dụng */
    public record LifecycleRequest(@NotNull LifecycleState state, @NotBlank @Size(max = 500) String reason) {}

    /** Điểm trên bản đồ GIS — chỉ những gì popup cần, không kéo cả hồ sơ (M2.10). */
    /**
     * Một marker trên bản đồ — nội dung popup theo M2.10.
     *
     * <p>M2.10 đòi popup có <b>tên, mã, loại, Xí nghiệp, trạng thái</b>. {@code orgUnitName} nằm ở
     * đây chứ không để giao diện tự tra: trên bản đồ tổng quan có hàng trăm marker, và mỗi lượt mở
     * popup mà phải gọi thêm một lượt API là độ trễ đúng vào lúc người dùng đang chờ xem.
     *
     * <p>⛔ Cố ý <b>chưa</b> có số liệu thuỷ văn mới nhất (cũng thuộc M2.10): MOD-03 là Phase 2. Để
     * sẵn một trường luôn rỗng ở đây thì giao diện sẽ hiện một dòng trống mà không ai giải thích
     * được — chỗ giữ đúng đắn là dòng chữ "Chưa đấu nối dữ liệu thuỷ văn" trên popup, không phải
     * một trường {@code null} trong DTO.
     */
    public record MapPoint(
            UUID publicId,
            String code,
            String name,
            ConstructionType constructionType,
            OperationalStatus operationalStatus,
            String orgUnitName,
            BigDecimal latitude,
            BigDecimal longitude) {

        public static MapPoint of(Construction c, String orgUnitName) {
            return new MapPoint(
                    c.getPublicId(),
                    c.getCode(),
                    c.getName(),
                    c.getConstructionType(),
                    c.getOperationalStatus(),
                    orgUnitName,
                    c.getLatitude(),
                    c.getLongitude());
        }
    }

    public record ClusterRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 255) String name,
            @NotNull UUID orgUnitId,
            @Size(max = 500) String description,
            Integer sortOrder) {}

    public record ClusterView(
            UUID publicId,
            String code,
            String name,
            UUID orgUnitId,
            String orgUnitName,
            String description,
            Integer sortOrder,
            boolean active) {}

    /** @param usedBytes dung lượng đang dùng của công trình — để giao diện hiện "đã dùng x/500 MB" */
    public record DocumentList(long usedBytes, java.util.List<DocumentView> items) {}

    public record DocumentView(
            UUID publicId,
            String originalName,
            String docType,
            String contentType,
            long sizeBytes,
            int fileVersion,
            boolean downloadable,
            Instant uploadedAt,
            java.time.LocalDate issuedDate,
            java.time.LocalDate expiryDate) {}
}
