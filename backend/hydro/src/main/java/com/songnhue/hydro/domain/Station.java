package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.Filter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * Điểm đo quan trắc — CN-03.1.
 *
 * <h2>⭐ {@link #getApiCode()} là khoá nối duy nhất với thế giới bên ngoài</h2>
 *
 * <p>Response của nguồn chỉ mang mã {@code F#####}. Toàn bộ việc "số này thuộc trạm nào" đi qua đúng
 * một cột. ⛔ <b>Bất biến sau khi seed</b>: đổi mã này là âm thầm gán số liệu của trạm này sang trạm
 * khác — không một ràng buộc CSDL nào bắt được, và biểu đồ vẫn vẽ đẹp.
 *
 * <p>⛔ Và vì thế: <b>cấm dùng tên để đối chiếu</b>. Có hai công trình khác nhau cùng tên "Yên Nghĩa"
 * ({@code TB Yên Nghĩa} và {@code Cống tiêu tự chảy Yên Nghĩa}), cụm Liên Mạc có cả
 * {@code Cống Liên Mạc} lẫn {@code Liên Mạc 2}. Bản suy đoán trước đó dò theo giá trị đo đã sai 1/4
 * mã ({@code F01705} đoán là Cống Phủ Lý, thực tế là <b>Vân Đình hạ lưu</b>).
 *
 * <h2>⚠ {@code orgUnitId} NULLable — khác mọi {@code ScopedEntity} khác của dự án</h2>
 *
 * <p>OI-05 chưa chốt 7 hay 8 Xí nghiệp nên không ai gán được đơn vị phụ trách cho 19 điểm đo. Hai
 * hệ quả bắt buộc xử lý, không được im lặng:
 *
 * <ol>
 *   <li>Bộ lọc phạm vi phải có vế {@code IS NULL} — xem {@link #LOC_PHAM_VI}.
 *   <li>Resolver người nhận cảnh báo (G11 tập 2) <b>rỗng</b> cho tới khi gán xong ⇒ màn hình
 *       <i>"Điểm đo chưa gán đơn vị"</i> là việc bắt buộc, không phải tính năng phụ.
 * </ol>
 *
 * <h2>⛔ Không có cột trạng thái 4 giá trị</h2>
 *
 * <p>Chỉ lưu {@link #isActive()} — vế con người. Vế tín hiệu suy ở lúc đọc bằng
 * {@link StationDisplayStatus#suyRa}; lý do đầy đủ nằm ở javadoc của lớp đó và ở khối ghi chú của
 * {@code V202608311049}.
 */
@Entity
@Table(name = "stations")
@AttributeOverride(name = "orgUnitId", column = @Column(name = "org_unit_id"))
@Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = Station.LOC_PHAM_VI)
@Audited(module = "hyd", entityType = "Điểm đo")
public class Station extends ScopedEntity {

    /**
     * Điều kiện lọc phạm vi của riêng điểm đo — <b>có thêm</b> vế {@code org_unit_id IS NULL}.
     *
     * <p>⚠ Không có vế đó thì <b>19/19 điểm đo vô hình với tất cả mọi người</b>, kể cả SUPER_ADMIN ở
     * path gốc {@code /1/}: trong SQL, {@code NULL IN (…)} cho ra {@code NULL} chứ không phải
     * {@code TRUE}, nên mọi dòng chưa gán đơn vị bị loại. Màn hình rỗng, không một dòng lỗi.
     *
     * <p>Điểm đo chưa gán đơn vị không phải "dữ liệu của Xí nghiệp khác" — nó là <b>dữ liệu chưa
     * thuộc về ai</b>, và giấu nó đi là giấu luôn việc cần làm. Ngay khi OI-05 chốt và mọi điểm đo
     * có đơn vị, vế này tự nhiên vô hại: không còn dòng nào {@code NULL} để nó nới ra.
     */
    public static final String LOC_PHAM_VI = "(org_unit_id IS NULL OR " + ORG_UNIT_FILTER_CONDITION + ")";

    /** Mã nội bộ của Công ty ({@code DO-LMAC-TL}) — mã người dùng đọc. */
    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** ⭐ Mã ánh xạ API bên thứ 3 ({@code F} + 5 chữ số). ⛔ Bất biến sau seed. */
    @Column(name = "api_code", nullable = false, length = 20)
    private String apiCode;

    @Column(name = "api_source_id", nullable = false)
    private Long apiSourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "position_role", nullable = false, length = 20)
    private PositionRole positionRole;

    /** ⛔ NULL cho tới khi có G8. Cấm bịa, cấm suy từ tên điểm đo. */
    @Column(name = "river_name", length = 100)
    private String riverName;

    /** ⛔ NULL cho tới khi có G8. Định dạng {@code K<km>+<m>}. */
    @Column(name = "chainage", length = 20)
    private String chainage;

    /**
     * Lý trình quy ra mét — cột sinh ở CSDL, để sắp xếp dọc tuyến sông.
     *
     * <p>{@code @Generated} chứ không chỉ {@code insertable = false}: thiếu nó thì Hibernate không
     * đọc lại giá trị sau khi ghi, và bản ghi vừa tạo trả về cho giao diện mang {@code null} trong
     * khi CSDL đã có số.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "chainage_m", insertable = false, updatable = false)
    private Integer chainageM;

    /** ⛔ NULL cho tới khi có G8 — toạ độ đi theo cặp, một nửa toạ độ là một điểm sai trên bản đồ. */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /**
     * Nguồn đánh dấu điểm đo này là "giá trị nội suy" — không đo trực tiếp.
     *
     * <p>Giữ riêng, không trộn vào số đo: báo cáo và biểu tổng hợp phải phân biệt được số đo thật với
     * số nội suy, nếu không thì một con số suy ra được dùng làm căn cứ vận hành.
     */
    @Column(name = "is_interpolated", nullable = false)
    private boolean interpolated = false;

    /** Vế CON NGƯỜI của trạng thái — cột trạng thái duy nhất được lưu. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "description", length = 500)
    private String description;

    /**
     * Loại chỉ số mà điểm đo này đo được.
     *
     * <p>{@code @ManyToMany} vì bảng nối không có cột nghiệp vụ nào — một entity riêng cho hai cột
     * khoá ngoại chỉ thêm việc phải nhớ.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "station_measurement_types",
            joinColumns = @JoinColumn(name = "station_id"),
            inverseJoinColumns = @JoinColumn(name = "measurement_type_id"))
    private Set<MeasurementType> measurementTypes = new LinkedHashSet<>();

    protected Station() {}

    public Station(String code, String name, String apiCode, Long apiSourceId, PositionRole positionRole) {
        this.code = code;
        this.name = name;
        this.apiCode = apiCode;
        this.apiSourceId = apiSourceId;
        this.positionRole = positionRole;
    }

    /**
     * Điểm đo này được phép không liên kết công trình nào hay không.
     *
     * <p>⛔ Chỉ {@link PositionRole#MN_SONG} — trạm thuỷ văn tham chiếu (4/19 điểm). Với mọi vai trò
     * khác, không có liên kết là dữ liệu thiếu và phải hiện lên như việc cần làm.
     */
    public boolean duocPhepKhongGanCongTrinh() {
        return positionRole == PositionRole.MN_SONG;
    }

    /** Chưa có đơn vị phụ trách — hệ quả của OI-05, hiện ở màn hình "Điểm đo chưa gán đơn vị". */
    public boolean chuaGanDonVi() {
        return getOrgUnitId() == null;
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

    public String getApiCode() {
        return apiCode;
    }

    /** ⚠ Chỉ dùng lúc TẠO mới. Đổi mã của một điểm đo đã có số liệu là gán nhầm dữ liệu lịch sử. */
    public void setApiCode(String apiCode) {
        this.apiCode = apiCode;
    }

    public Long getApiSourceId() {
        return apiSourceId;
    }

    public void setApiSourceId(Long apiSourceId) {
        this.apiSourceId = apiSourceId;
    }

    public PositionRole getPositionRole() {
        return positionRole;
    }

    public void setPositionRole(PositionRole positionRole) {
        this.positionRole = positionRole;
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

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public boolean isInterpolated() {
        return interpolated;
    }

    public void setInterpolated(boolean interpolated) {
        this.interpolated = interpolated;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<MeasurementType> getMeasurementTypes() {
        return measurementTypes;
    }

    public void setMeasurementTypes(Set<MeasurementType> measurementTypes) {
        this.measurementTypes = measurementTypes == null ? new LinkedHashSet<>() : measurementTypes;
    }
}
