package com.songnhue.operations.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.SecondaryTables;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.SecondaryRow;
import org.hibernate.generator.EventType;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Hồ sơ công trình thuỷ lợi — CN-02.1.
 *
 * <h2>⭐ Entity nghiệp vụ đầu tiên thuộc phạm vi đơn vị</h2>
 *
 * Kế thừa {@link ScopedEntity} nên mọi truy vấn tự động bị lọc theo Xí nghiệp của người đăng nhập
 * (tầng 3, {@code conventions.md} §4.2). Suốt Phase 0 cơ chế đó chỉ chạy trên một entity dựng riêng
 * cho bài kiểm; lớp này là nơi nó lần đầu chạy trên dữ liệu thật.
 *
 * <p>⚠ {@code @Filter} <b>bắt buộc</b> phải có. {@code @FilterDef} ở lớp cha mới chỉ <i>định nghĩa</i>
 * bộ lọc; thiếu {@code @Filter} thì bộ lọc tồn tại nhưng không áp cho entity này — mọi Xí nghiệp đọc
 * được dữ liệu của nhau, màn hình đầy đủ, không một dòng lỗi nào. Luật ArchUnit ở
 * {@code SilentFailureRuleTest} canh đúng chỗ đó.
 *
 * <h2>Vì sao thông số kỹ thuật là bảng phụ chứ không phải entity riêng</h2>
 *
 * Trạm bơm có 9 thông số, cống có 8, kênh/đê có 7 — ba tập không giao nhau. Nhồi chung vào
 * {@code constructions} thì mỗi hồ sơ mang theo hơn hai chục cột rỗng và không gì ngăn được một cái
 * cống có "số máy bơm".
 *
 * <p>Nhưng tách thành <b>entity</b> riêng thì mỗi lần sửa thông số kỹ thuật lại là một dòng nhật ký
 * mang loại đối tượng khác, và "nhật ký thay đổi hồ sơ công trình" (CN-02.7) sẽ bỏ sót đúng phần kỹ
 * thuật — thứ đáng theo dõi nhất. Bảng phụ giữ được cả hai: dữ liệu tách bảng, nhưng với Hibernate
 * và với bộ ghi nhật ký thì đây vẫn là <i>một</i> hồ sơ.
 *
 * <p>{@code @SecondaryRow(optional = true)} để Hibernate không chèn dòng rỗng vào bảng thông số của
 * loại không dùng tới.
 */
@Entity
@Table(name = "constructions")
@SecondaryTables({
    @SecondaryTable(name = "pump_station_specs", pkJoinColumns = @PrimaryKeyJoinColumn(name = "construction_id")),
    @SecondaryTable(name = "sluice_specs", pkJoinColumns = @PrimaryKeyJoinColumn(name = "construction_id")),
    @SecondaryTable(name = "linear_specs", pkJoinColumns = @PrimaryKeyJoinColumn(name = "construction_id"))
})
@SecondaryRow(table = "pump_station_specs", optional = true)
@SecondaryRow(table = "sluice_specs", optional = true)
@SecondaryRow(table = "linear_specs", optional = true)
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
@Audited(module = "ops", entityType = "Công trình")
public class Construction extends ScopedEntity {

    // === Định danh ===========================================================

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "construction_type", nullable = false, length = 20)
    private ConstructionType constructionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20)
    private ConstructionPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "management_level", nullable = false, length = 20)
    private ManagementLevel managementLevel = ManagementLevel.XI_NGHIEP;

    /**
     * Cụm công trình — chỉ để nhóm hiển thị và lọc (G15).
     *
     * <p>⛔ Không bao giờ được dùng trong truy vấn phân quyền. Phạm vi đi bằng {@code orgUnitId} kế
     * thừa từ {@link ScopedEntity}.
     */
    @Column(name = "cluster_id")
    private Long clusterId;

    // === Vị trí ==============================================================

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "river_name", length = 100)
    private String riverName;

    /** Lý trình dạng {@code K<km>+<m>}, VD {@code K0+390}. Định dạng do CHECK ở CSDL giữ. */
    @Column(name = "chainage", length = 20)
    private String chainage;

    /**
     * Lý trình quy ra mét — cột sinh ở CSDL, dùng để sắp xếp dọc tuyến sông.
     *
     * <p>{@code @Generated} chứ không chỉ {@code insertable = false}: thiếu nó thì Hibernate không
     * đọc lại giá trị sau khi ghi, và bản ghi vừa tạo trả về cho giao diện mang {@code null} trong
     * khi CSDL đã có số. Người dùng thấy ô trống, bấm F5 thì có — loại lỗi mất thời gian truy nhất.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "chainage_m", insertable = false, updatable = false)
    private Integer chainageM;

    /** Lưu vực / khu tưới tiêu — <b>trường văn bản</b>, chốt F3. Không danh mục, không polygon. */
    @Column(name = "basin_note", length = 500)
    private String basinNote;

    // === Hồ sơ chung =========================================================

    @Column(name = "built_year")
    private Short builtYear;

    @Column(name = "commissioned_year")
    private Short commissionedYear;

    @Column(name = "designer", length = 255)
    private String designer;

    @Column(name = "contractor", length = 255)
    private String contractor;

    /** Tổng vốn đầu tư, đơn vị <b>VND</b> (điểm nghiệp vụ 18) — không phải triệu VND. */
    @Column(name = "total_investment", precision = 18, scale = 2)
    private BigDecimal totalInvestment;

    @Column(name = "description")
    private String description;

    // === Trạng thái ==========================================================

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", nullable = false, length = 20)
    private LifecycleState lifecycleState = LifecycleState.DANG_HOAT_DONG;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_status", nullable = false, length = 20)
    private OperationalStatus operationalStatus = OperationalStatus.BINH_THUONG;

    // === Thông số trạm bơm ===================================================

    @Column(table = "pump_station_specs", name = "total_power_kw", precision = 12, scale = 2)
    private BigDecimal totalPowerKw;

    @Column(table = "pump_station_specs", name = "pump_count")
    private Short pumpCount;

    @Column(table = "pump_station_specs", name = "standby_pump_count")
    private Short standbyPumpCount;

    @Column(table = "pump_station_specs", name = "flow_per_pump_m3s", precision = 10, scale = 3)
    private BigDecimal flowPerPumpM3s;

    /** Tổng lưu lượng = số máy × lưu lượng/máy — CSDL tính, không có đường ghi từ mã. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(
            table = "pump_station_specs",
            name = "total_flow_m3s",
            precision = 14,
            scale = 3,
            insertable = false,
            updatable = false)
    private BigDecimal totalFlowM3s;

    @Column(table = "pump_station_specs", name = "head_m", precision = 8, scale = 2)
    private BigDecimal headM;

    @Column(table = "pump_station_specs", name = "power_source", length = 100)
    private String powerSource;

    @Column(table = "pump_station_specs", name = "voltage_kv", precision = 8, scale = 3)
    private BigDecimal voltageKv;

    @Column(table = "pump_station_specs", name = "operating_level_min_m", precision = 8, scale = 3)
    private BigDecimal operatingLevelMinM;

    @Column(table = "pump_station_specs", name = "operating_level_max_m", precision = 8, scale = 3)
    private BigDecimal operatingLevelMaxM;

    // === Thông số cống =======================================================

    @Column(table = "sluice_specs", name = "sluice_type", length = 20)
    private String sluiceType;

    @Column(table = "sluice_specs", name = "bay_count")
    private Short bayCount;

    @Column(table = "sluice_specs", name = "bay_width_m", precision = 8, scale = 2)
    private BigDecimal bayWidthM;

    @Column(table = "sluice_specs", name = "sill_elevation_m", precision = 8, scale = 3)
    private BigDecimal sillElevationM;

    @Column(table = "sluice_specs", name = "crest_elevation_m", precision = 8, scale = 3)
    private BigDecimal sluiceCrestElevationM;

    @Column(table = "sluice_specs", name = "design_flow_m3s", precision = 10, scale = 3)
    private BigDecimal sluiceDesignFlowM3s;

    @Column(table = "sluice_specs", name = "gate_operation", length = 20)
    private String gateOperation;

    @Column(table = "sluice_specs", name = "upstream_warning_level_m", precision = 8, scale = 3)
    private BigDecimal upstreamWarningLevelM;

    @Column(table = "sluice_specs", name = "upstream_danger_level_m", precision = 8, scale = 3)
    private BigDecimal upstreamDangerLevelM;

    // === Thông số công trình tuyến (kênh / đê) ===============================

    @Column(table = "linear_specs", name = "length_km", precision = 10, scale = 3)
    private BigDecimal lengthKm;

    @Column(table = "linear_specs", name = "start_chainage", length = 20)
    private String startChainage;

    @Column(table = "linear_specs", name = "end_chainage", length = 20)
    private String endChainage;

    @Column(table = "linear_specs", name = "design_flow_m3s", precision = 10, scale = 3)
    private BigDecimal linearDesignFlowM3s;

    @Column(table = "linear_specs", name = "crest_elevation_m", precision = 8, scale = 3)
    private BigDecimal linearCrestElevationM;

    @Column(table = "linear_specs", name = "technical_grade", length = 20)
    private String technicalGrade;

    @Column(table = "linear_specs", name = "cross_section", length = 255)
    private String crossSection;

    @Column(table = "linear_specs", name = "spec_note")
    private String specNote;

    public Construction() {}

    public Construction(String code, String name, ConstructionType constructionType, Long orgUnitId) {
        this.code = code;
        this.name = name;
        this.constructionType = constructionType;
        setOrgUnitId(orgUnitId);
    }

    /** Đã số hoá vị trí chưa — công trình chưa có toạ độ thì không lên bản đồ GIS (CN-02.4 / M2.8). */
    public boolean daSoHoaViTri() {
        return latitude != null && longitude != null;
    }

    /**
     * Đặt trạng thái vận hành.
     *
     * <p>⛔ Chỉ {@code ConstructionStatusService} được gọi. Không đặt setter công khai theo lối thông
     * thường vì đây là <b>giá trị dẫn xuất</b> (quy tắc 4): mở một đường ghi thứ hai là mở đường cho
     * cột này rời khỏi dữ liệu sinh ra nó, và sai lệch đó không có triệu chứng nào ngoài màu marker
     * trên bản đồ.
     */
    public void apDungTrangThai(OperationalStatus status) {
        this.operationalStatus = status;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ConstructionType getConstructionType() {
        return constructionType;
    }

    public void setConstructionType(ConstructionType constructionType) {
        this.constructionType = constructionType;
    }

    public ConstructionPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(ConstructionPurpose purpose) {
        this.purpose = purpose;
    }

    public ManagementLevel getManagementLevel() {
        return managementLevel;
    }

    public void setManagementLevel(ManagementLevel managementLevel) {
        this.managementLevel = managementLevel;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public void setClusterId(Long clusterId) {
        this.clusterId = clusterId;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    /** Toạ độ đi theo cặp — một nửa toạ độ là một điểm sai trên bản đồ, tệ hơn hẳn chưa số hoá. */
    public void datToaDo(BigDecimal latitude, BigDecimal longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getRiverName() {
        return riverName;
    }

    public void setRiverName(String riverName) {
        this.riverName = riverName;
    }

    public String getChainage() {
        return chainage;
    }

    public void setChainage(String chainage) {
        this.chainage = chainage;
    }

    public Integer getChainageM() {
        return chainageM;
    }

    public String getBasinNote() {
        return basinNote;
    }

    public void setBasinNote(String basinNote) {
        this.basinNote = basinNote;
    }

    public Short getBuiltYear() {
        return builtYear;
    }

    public void setBuiltYear(Short builtYear) {
        this.builtYear = builtYear;
    }

    public Short getCommissionedYear() {
        return commissionedYear;
    }

    public void setCommissionedYear(Short commissionedYear) {
        this.commissionedYear = commissionedYear;
    }

    public String getDesigner() {
        return designer;
    }

    public void setDesigner(String designer) {
        this.designer = designer;
    }

    public String getContractor() {
        return contractor;
    }

    public void setContractor(String contractor) {
        this.contractor = contractor;
    }

    public BigDecimal getTotalInvestment() {
        return totalInvestment;
    }

    public void setTotalInvestment(BigDecimal totalInvestment) {
        this.totalInvestment = totalInvestment;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LifecycleState getLifecycleState() {
        return lifecycleState;
    }

    public void setLifecycleState(LifecycleState lifecycleState) {
        this.lifecycleState = lifecycleState;
    }

    public OperationalStatus getOperationalStatus() {
        return operationalStatus;
    }

    public BigDecimal getTotalPowerKw() {
        return totalPowerKw;
    }

    public void setTotalPowerKw(BigDecimal totalPowerKw) {
        this.totalPowerKw = totalPowerKw;
    }

    public Short getPumpCount() {
        return pumpCount;
    }

    public void setPumpCount(Short pumpCount) {
        this.pumpCount = pumpCount;
    }

    public Short getStandbyPumpCount() {
        return standbyPumpCount;
    }

    public void setStandbyPumpCount(Short standbyPumpCount) {
        this.standbyPumpCount = standbyPumpCount;
    }

    public BigDecimal getFlowPerPumpM3s() {
        return flowPerPumpM3s;
    }

    public void setFlowPerPumpM3s(BigDecimal flowPerPumpM3s) {
        this.flowPerPumpM3s = flowPerPumpM3s;
    }

    public BigDecimal getTotalFlowM3s() {
        return totalFlowM3s;
    }

    public BigDecimal getHeadM() {
        return headM;
    }

    public void setHeadM(BigDecimal headM) {
        this.headM = headM;
    }

    public String getPowerSource() {
        return powerSource;
    }

    public void setPowerSource(String powerSource) {
        this.powerSource = powerSource;
    }

    public BigDecimal getVoltageKv() {
        return voltageKv;
    }

    public void setVoltageKv(BigDecimal voltageKv) {
        this.voltageKv = voltageKv;
    }

    public BigDecimal getOperatingLevelMinM() {
        return operatingLevelMinM;
    }

    public void setOperatingLevelMinM(BigDecimal operatingLevelMinM) {
        this.operatingLevelMinM = operatingLevelMinM;
    }

    public BigDecimal getOperatingLevelMaxM() {
        return operatingLevelMaxM;
    }

    public void setOperatingLevelMaxM(BigDecimal operatingLevelMaxM) {
        this.operatingLevelMaxM = operatingLevelMaxM;
    }

    public String getSluiceType() {
        return sluiceType;
    }

    public void setSluiceType(String sluiceType) {
        this.sluiceType = sluiceType;
    }

    public Short getBayCount() {
        return bayCount;
    }

    public void setBayCount(Short bayCount) {
        this.bayCount = bayCount;
    }

    public BigDecimal getBayWidthM() {
        return bayWidthM;
    }

    public void setBayWidthM(BigDecimal bayWidthM) {
        this.bayWidthM = bayWidthM;
    }

    public BigDecimal getSillElevationM() {
        return sillElevationM;
    }

    public void setSillElevationM(BigDecimal sillElevationM) {
        this.sillElevationM = sillElevationM;
    }

    public BigDecimal getSluiceCrestElevationM() {
        return sluiceCrestElevationM;
    }

    public void setSluiceCrestElevationM(BigDecimal sluiceCrestElevationM) {
        this.sluiceCrestElevationM = sluiceCrestElevationM;
    }

    public BigDecimal getSluiceDesignFlowM3s() {
        return sluiceDesignFlowM3s;
    }

    public void setSluiceDesignFlowM3s(BigDecimal sluiceDesignFlowM3s) {
        this.sluiceDesignFlowM3s = sluiceDesignFlowM3s;
    }

    public String getGateOperation() {
        return gateOperation;
    }

    public void setGateOperation(String gateOperation) {
        this.gateOperation = gateOperation;
    }

    public BigDecimal getUpstreamWarningLevelM() {
        return upstreamWarningLevelM;
    }

    public void setUpstreamWarningLevelM(BigDecimal upstreamWarningLevelM) {
        this.upstreamWarningLevelM = upstreamWarningLevelM;
    }

    public BigDecimal getUpstreamDangerLevelM() {
        return upstreamDangerLevelM;
    }

    public void setUpstreamDangerLevelM(BigDecimal upstreamDangerLevelM) {
        this.upstreamDangerLevelM = upstreamDangerLevelM;
    }

    public BigDecimal getLengthKm() {
        return lengthKm;
    }

    public void setLengthKm(BigDecimal lengthKm) {
        this.lengthKm = lengthKm;
    }

    public String getStartChainage() {
        return startChainage;
    }

    public void setStartChainage(String startChainage) {
        this.startChainage = startChainage;
    }

    public String getEndChainage() {
        return endChainage;
    }

    public void setEndChainage(String endChainage) {
        this.endChainage = endChainage;
    }

    public BigDecimal getLinearDesignFlowM3s() {
        return linearDesignFlowM3s;
    }

    public void setLinearDesignFlowM3s(BigDecimal linearDesignFlowM3s) {
        this.linearDesignFlowM3s = linearDesignFlowM3s;
    }

    public BigDecimal getLinearCrestElevationM() {
        return linearCrestElevationM;
    }

    public void setLinearCrestElevationM(BigDecimal linearCrestElevationM) {
        this.linearCrestElevationM = linearCrestElevationM;
    }

    public String getTechnicalGrade() {
        return technicalGrade;
    }

    public void setTechnicalGrade(String technicalGrade) {
        this.technicalGrade = technicalGrade;
    }

    public String getCrossSection() {
        return crossSection;
    }

    public void setCrossSection(String crossSection) {
        this.crossSection = crossSection;
    }

    public String getSpecNote() {
        return specNote;
    }

    public void setSpecNote(String specNote) {
        this.specNote = specNote;
    }
}
