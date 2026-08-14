package com.songnhue.core.domain.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.tree.MaterializedPath;

/**
 * Một đơn vị trong sơ đồ tổ chức — Công ty, phòng ban, Xí nghiệp, tổ đội.
 *
 * <p><b>Không kế thừa {@code ScopedEntity}</b>: bảng này <i>định nghĩa</i> phạm vi chứ không nằm
 * trong phạm vi nào. Lọc cây đơn vị theo chính bộ lọc phạm vi sẽ khiến người dùng không nhìn thấy
 * đơn vị cha của mình, và menu chọn đơn vị thành rỗng.
 *
 * <p>{@code path} và {@code depth} là <b>giá trị dẫn xuất</b> từ vị trí trong cây — chỉ
 * {@link com.songnhue.core.application.org.OrgUnitService} được đặt, không có setter công khai để
 * mã nghiệp vụ khác sửa tay. Sai một path là sai bộ lọc phạm vi của toàn bộ dữ liệu bên dưới.
 */
@Entity
@Table(name = "org_units")
public class OrgUnit extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false, length = 30)
    private OrgUnitType unitType;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "path", nullable = false, length = 500)
    private String path;

    @Column(name = "depth", nullable = false)
    private Short depth;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Nguồn tìm người nhận cảnh báo theo G11 — người đứng đầu đơn vị phụ trách công trình. */
    @Column(name = "head_user_id")
    private Long headUserId;

    @Column(name = "deputy_user_id")
    private Long deputyUserId;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "address", length = 500)
    private String address;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected OrgUnit() {}

    public OrgUnit(String code, String name, OrgUnitType unitType) {
        this.code = code;
        this.name = name;
        this.unitType = unitType;
    }

    /**
     * Đặt vị trí trong cây. Gọi sau khi đã có id (path chứa chính id của mình).
     *
     * <p>Gói {@code path} và {@code depth} vào một lời gọi có chủ đích: hai giá trị này phải luôn
     * khớp nhau, tách ra là mở đường cho việc cập nhật một nửa.
     */
    public void placeAt(String path) {
        this.path = path;
        this.depth = (short) MaterializedPath.depthOf(path);
        this.parentId = MaterializedPath.parentId(path);
    }

    public boolean isRoot() {
        return parentId == null;
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

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public OrgUnitType getUnitType() {
        return unitType;
    }

    public void setUnitType(OrgUnitType unitType) {
        this.unitType = unitType;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getPath() {
        return path;
    }

    public Short getDepth() {
        return depth;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Long getHeadUserId() {
        return headUserId;
    }

    public void setHeadUserId(Long headUserId) {
        this.headUserId = headUserId;
    }

    public Long getDeputyUserId() {
        return deputyUserId;
    }

    public void setDeputyUserId(Long deputyUserId) {
        this.deputyUserId = deputyUserId;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
