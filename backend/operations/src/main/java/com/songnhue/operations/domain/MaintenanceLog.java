package com.songnhue.operations.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.common.persistence.WorkflowAware;

/**
 * Một bản ghi sửa chữa / bảo trì / khắc phục sự cố — CN-02.2.
 *
 * <h2>⛔ Sự cố không phải entity riêng — quy tắc 15</h2>
 *
 * Chốt G1 (PA A): một sự cố là chính lớp này với {@code workType = KHAC_PHUC_SU_CO}. Không bảng
 * {@code incidents}, không mã {@code SC-}, không vòng đời bảy trạng thái. Đây là <b>chức năng ghi
 * nhận hoạt động duy nhất</b> của MOD-02 sau khi Nhật ký vận hành và Phiếu sự cố bị loại khỏi phạm
 * vi.
 *
 * <h2>⭐ Một entity, hai quy trình</h2>
 *
 * {@link #workflowEntityType()} trả tên quy trình <b>theo loại công việc</b>. Đây không phải mẹo
 * vặt: ma trận phân quyền §6 tách hai dòng khác nhau, và chúng khác nhau ở đúng cột "Kỹ thuật" —
 * ghi bảo trì thì được, tuyên bố sự cố đã xong thì không. Mà
 * {@code workflow_transitions.required_permission} gắn theo {@code (from_state, action)} chứ không
 * theo loại công việc, nên một quy trình duy nhất buộc phải diễn đạt luật đó bằng một câu {@code if}
 * trong service — đúng thứ mà cả cơ chế workflow sinh ra để tránh.
 *
 * <h2>Phạm vi đơn vị là bản sao, không phải khoá ngoại</h2>
 *
 * {@code orgUnitId} sao chép từ công trình <b>lúc tạo</b> (T18.2). Công trình đổi đơn vị phụ trách
 * thì các bản ghi cũ giữ nguyên đơn vị lúc phát sinh. Đó là điều đúng cho một hồ sơ lịch sử — chi
 * phí sửa chữa năm ngoái thuộc về Xí nghiệp đã bỏ tiền ra — nhưng phải nói ra, vì nhìn thoáng qua
 * nó giống một chỗ quên đồng bộ.
 */
@Entity
@Table(name = "maintenance_logs")
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
@Audited(module = "ops", entityType = "Lịch sử sửa chữa")
public class MaintenanceLog extends ScopedEntity implements WorkflowAware {

    /** Quy trình cho công việc sửa chữa / bảo trì — khớp {@code workflow_definitions.entity_type}. */
    public static final String WORKFLOW_WORK = "MAINTENANCE_LOG";

    /** Quy trình cho sự cố — khác quy trình trên ở quyền đóng bản ghi. */
    public static final String WORKFLOW_INCIDENT = "MAINTENANCE_INCIDENT";

    @Column(name = "code", nullable = false, length = 30, updatable = false)
    private String code;

    @Column(name = "construction_id", nullable = false)
    private Long constructionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_type", nullable = false, length = 30)
    private MaintenanceType workType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20)
    private IncidentSeverity severity;

    /**
     * Trạng thái xử lý.
     *
     * <p>⛔ Không có setter. {@link #applyState(String)} là đường duy nhất, và chỉ
     * {@code WorkflowEngine} được gọi nó (quy tắc 4 — luật ArchUnit
     * {@code SilentFailureRuleTest} canh).
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = MaintenanceState.MOI;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "completed_on")
    private LocalDate completedOn;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "item_or_equipment", length = 255)
    private String itemOrEquipment;

    // === Đơn vị thực hiện — điểm nghiệp vụ 17, đúng MỘT trong hai có giá trị ===

    @Column(name = "performer_org_unit_id")
    private Long performerOrgUnitId;

    @Column(name = "performer_name", length = 255)
    private String performerName;

    /** Chi phí, đơn vị <b>VND</b> — {@code NUMERIC}, cấm float (quy tắc 2). */
    @Column(name = "cost", precision = 18, scale = 2)
    private BigDecimal cost;

    @Column(name = "funding_source", length = 255)
    private String fundingSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "acceptance_result", length = 20)
    private AcceptanceResult acceptanceResult;

    @Column(name = "acceptance_note")
    private String acceptanceNote;

    @Column(name = "assignee_user_id", nullable = false)
    private Long assigneeUserId;

    /**
     * Cảnh báo ngưỡng thuỷ văn đã dẫn tới bản ghi này — điểm nghiệp vụ 16.
     *
     * <p>⛔ Cố ý <b>không</b> khoá ngoại: {@code alert_events} thuộc module {@code hydro}. Ở Phase 1
     * cột này chưa có đường điền từ giao diện (nút "Tạo bản ghi khắc phục" là việc của Phase 2), chỉ
     * có đường điền qua API để Phase 2 cắm vào mà không phải đổi lược đồ.
     */
    @Column(name = "alert_event_public_id")
    private UUID alertEventPublicId;

    protected MaintenanceLog() {}

    /**
     * @param initialState trạng thái khởi tạo <b>đã được {@code WorkflowPort.resolveInitialState}
     *     kiểm</b>. Nhận vào hàm dựng chứ không đặt sau, vì một bản ghi tồn tại ở trạng thái chưa
     *     kiểm — dù chỉ trong vài dòng mã — là một bản ghi có thể được lưu nhầm ở trạng thái đó.
     */
    // CHECKSTYLE.OFF: ParameterNumber - đây là tập trường BẮT BUỘC của CN-02.2; để trống bất kỳ cái
    // nào rồi gán sau nghĩa là có một khoảnh khắc bản ghi tồn tại mà không hợp lệ.
    public MaintenanceLog(
            String code,
            Long constructionId,
            Long orgUnitId,
            MaintenanceType workType,
            String initialState,
            LocalDate startedOn,
            String content,
            Long assigneeUserId) {
        this.code = code;
        this.constructionId = constructionId;
        this.workType = workType;
        this.status = initialState;
        this.startedOn = startedOn;
        this.content = content;
        this.assigneeUserId = assigneeUserId;
        setOrgUnitId(orgUnitId);
    }
    // CHECKSTYLE.ON: ParameterNumber

    // ---- WorkflowAware -------------------------------------------------------

    /**
     * Quy trình nào có hiệu lực — <b>quyết định bởi loại công việc</b>.
     *
     * <p>Xem khối giải thích ở đầu lớp. Hệ quả cần biết: đổi {@code workType} giữa sự cố và không sự
     * cố là đổi luôn quy trình đang áp cho bản ghi. Ba trạng thái của hai quy trình trùng nhau nên
     * bản ghi không bao giờ rơi vào trạng thái không hợp lệ, nhưng danh sách nút ở giao diện sẽ đổi
     * — và đó là điều đúng.
     */
    @Override
    public String workflowEntityType() {
        return workType.laSuCo() ? WORKFLOW_INCIDENT : WORKFLOW_WORK;
    }

    @Override
    public String currentState() {
        return status;
    }

    @Override
    public void applyState(String newState) {
        this.status = newState;
    }

    @Override
    public Long entityId() {
        return getId();
    }

    /** Người phụ trách — người nhận thông báo khi bản ghi chuyển trạng thái, không phải người tạo. */
    @Override
    public Long ownerUserId() {
        return assigneeUserId;
    }

    // ---- Truy vấn nghiệp vụ --------------------------------------------------

    /** Còn mở = còn làm công trình mang cờ trạng thái (đỏ nếu sự cố, vàng nếu bảo trì). */
    public boolean dangMo() {
        return MaintenanceState.dangMo(status);
    }

    public boolean laSuCo() {
        return workType.laSuCo();
    }

    // ---- Getter / setter -----------------------------------------------------

    public String getCode() {
        return code;
    }

    public Long getConstructionId() {
        return constructionId;
    }

    public MaintenanceType getWorkType() {
        return workType;
    }

    public void setWorkType(MaintenanceType workType) {
        this.workType = workType;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getStartedOn() {
        return startedOn;
    }

    public void setStartedOn(LocalDate startedOn) {
        this.startedOn = startedOn;
    }

    public LocalDate getCompletedOn() {
        return completedOn;
    }

    public void setCompletedOn(LocalDate completedOn) {
        this.completedOn = completedOn;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getItemOrEquipment() {
        return itemOrEquipment;
    }

    public void setItemOrEquipment(String itemOrEquipment) {
        this.itemOrEquipment = itemOrEquipment;
    }

    public Long getPerformerOrgUnitId() {
        return performerOrgUnitId;
    }

    public String getPerformerName() {
        return performerName;
    }

    /**
     * Đơn vị thực hiện — đúng một trong hai, ép ở một chỗ.
     *
     * <p>Hai setter riêng thì sẽ có lượt gọi đặt cột này mà quên xoá cột kia, và bản ghi mang cả hai
     * giá trị cho tới khi CSDL từ chối. Một hàm nhận cả hai thì không có khe hở đó.
     */
    public void datDonViThucHien(Long orgUnitId, String name) {
        this.performerOrgUnitId = orgUnitId;
        this.performerName = name;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public void setCost(BigDecimal cost) {
        this.cost = cost;
    }

    public String getFundingSource() {
        return fundingSource;
    }

    public void setFundingSource(String fundingSource) {
        this.fundingSource = fundingSource;
    }

    public AcceptanceResult getAcceptanceResult() {
        return acceptanceResult;
    }

    public void setAcceptanceResult(AcceptanceResult acceptanceResult) {
        this.acceptanceResult = acceptanceResult;
    }

    public String getAcceptanceNote() {
        return acceptanceNote;
    }

    public void setAcceptanceNote(String acceptanceNote) {
        this.acceptanceNote = acceptanceNote;
    }

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public UUID getAlertEventPublicId() {
        return alertEventPublicId;
    }

    public void setAlertEventPublicId(UUID alertEventPublicId) {
        this.alertEventPublicId = alertEventPublicId;
    }
}
