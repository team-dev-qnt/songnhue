package com.songnhue.operations.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.songnhue.operations.application.MaintenanceLogForm;
import com.songnhue.operations.domain.AcceptanceResult;
import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.MaintenanceLog;
import com.songnhue.operations.domain.MaintenanceType;

/**
 * DTO của lịch sử sửa chữa — CN-02.2.
 *
 * <p>Entity không ra khỏi tầng {@code application} ({@code conventions.md} §1.1), nên mọi thứ
 * controller trả ra đều là {@code record} ở đây.
 */
public final class MaintenanceDtos {

    private MaintenanceDtos() {}

    /**
     * Dữ liệu nhập.
     *
     * <p>⛔ Không có trường {@code status}, và đó là điều quan trọng nhất của record này: trạng thái
     * xử lý chỉ đổi qua Workflow engine (quy tắc 4). {@link #initialState} là chuyện khác — nó khai
     * bản ghi <i>vào đời</i> ở đâu, và engine vẫn kiểm quyền của đường vào đó.
     *
     * <p>⛔ Không có trường {@code orgUnitId}: đơn vị của bản ghi sao chép từ công trình (T18.2).
     * Nhận từ client là mở đường ghi dữ liệu vào phạm vi của Xí nghiệp khác.
     */
    public record SaveRequest(
            @NotNull UUID constructionId,
            @NotNull MaintenanceType workType,
            IncidentSeverity severity,
            /* Trạng thái ban đầu; null = mặc định của quy trình (MOI). */
            String initialState,
            @NotNull LocalDate startedOn,
            LocalDate completedOn,
            @NotNull @Size(max = 20000) String content,
            @Size(max = 255) String itemOrEquipment,
            /* Loại trừ với performerName — đúng một trong hai (điểm nghiệp vụ 17, OPS-2017). */
            UUID performerOrgUnitId,
            @Size(max = 255) String performerName,
            /* VND. */
            BigDecimal cost,
            @Size(max = 255) String fundingSource,
            AcceptanceResult acceptanceResult,
            @Size(max = 20000) String acceptanceNote,
            UUID assigneeUserId,
            UUID alertEventId) {

        public MaintenanceLogForm toForm() {
            return new MaintenanceLogForm(
                    constructionId,
                    workType,
                    severity,
                    initialState,
                    startedOn,
                    completedOn,
                    content,
                    itemOrEquipment,
                    performerOrgUnitId,
                    performerName,
                    cost,
                    fundingSource,
                    acceptanceResult,
                    acceptanceNote,
                    assigneeUserId,
                    alertEventId);
        }

        /**
         * Bản sao đã ép loại "Khắc phục sự cố" — dùng cho đường ghi nhận sự cố.
         *
         * <p>Ép ở đây chứ không tin trường {@code workType} của payload: đường đó chỉ đòi
         * {@code ops:maintenance:report-incident}, quyền mà Cán bộ vận hành cũng có. Nhận
         * {@code workType} tự do nghĩa là quyền hẹp đó mở được cả đường ghi bảo trì —
         * {@code ops:maintenance:create} trở thành thứ trang trí.
         */
        public MaintenanceLogForm toIncidentForm() {
            return new MaintenanceLogForm(
                    constructionId,
                    MaintenanceType.KHAC_PHUC_SU_CO,
                    severity,
                    initialState,
                    startedOn,
                    completedOn,
                    content,
                    itemOrEquipment,
                    performerOrgUnitId,
                    performerName,
                    cost,
                    fundingSource,
                    acceptanceResult,
                    acceptanceNote,
                    assigneeUserId,
                    alertEventId);
        }
    }

    /** Payload của một bước chuyển trạng thái. */
    public record ActionRequest(@NotNull String action, LocalDate completedOn, @Size(max = 20000) String note) {}

    /**
     * Một dòng timeline.
     *
     * @param performer tên đơn vị nội bộ hoặc tên nhà thầu — <b>đã gộp</b> để giao diện không phải
     *     biết hai cột. Cột nào có giá trị là chuyện của tầng dữ liệu; người đọc chỉ cần biết ai làm
     * @param performerIsInternal phân biệt nội bộ / thuê ngoài, phục vụ bộ lọc và BC-09
     */
    // CHECKSTYLE.OFF: ParameterNumber - đây là một dòng dữ liệu, không phải một lời gọi hàm.
    public record MaintenanceRow(
            UUID id,
            String code,
            UUID constructionId,
            String constructionCode,
            String constructionName,
            MaintenanceType workType,
            IncidentSeverity severity,
            String status,
            LocalDate startedOn,
            LocalDate completedOn,
            String content,
            String itemOrEquipment,
            String performer,
            boolean performerIsInternal,
            BigDecimal cost,
            String fundingSource,
            AcceptanceResult acceptanceResult,
            String acceptanceNote,
            UUID assigneeUserId,
            UUID alertEventId,
            Instant createdAt) {

        public static MaintenanceRow of(
                MaintenanceLog m,
                String constructionCode,
                String constructionName,
                UUID constructionPublicId,
                String performerName,
                UUID assigneePublicId) {
            return new MaintenanceRow(
                    m.getPublicId(),
                    m.getCode(),
                    constructionPublicId,
                    constructionCode,
                    constructionName,
                    m.getWorkType(),
                    m.getSeverity(),
                    m.getStatus(),
                    m.getStartedOn(),
                    m.getCompletedOn(),
                    m.getContent(),
                    m.getItemOrEquipment(),
                    performerName,
                    m.getPerformerOrgUnitId() != null,
                    m.getCost(),
                    m.getFundingSource(),
                    m.getAcceptanceResult(),
                    m.getAcceptanceNote(),
                    assigneePublicId,
                    m.getAlertEventPublicId(),
                    m.getCreatedAt());
        }
    }
    // CHECKSTYLE.ON: ParameterNumber

    /** Chi tiết = một dòng + danh sách nút được phép bấm (FE không tự suy — conventions.md §3). */
    public record MaintenanceDetail(
            MaintenanceRow record, java.util.List<com.songnhue.core.spi.AllowedAction> actions) {}

    /** Tệp đính kèm — biên bản nghiệm thu, ảnh trước / sau (T18.6). */
    public record AttachmentView(
            UUID id,
            String originalName,
            String purpose,
            String contentType,
            long sizeBytes,
            int fileVersion,
            boolean downloadable,
            Instant createdAt) {}
}
