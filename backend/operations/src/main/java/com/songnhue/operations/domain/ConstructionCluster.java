package com.songnhue.operations.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Cụm công trình — G15 (điểm nghiệp vụ 12).
 *
 * <h2>Vì sao KHÔNG kế thừa {@code ScopedEntity}</h2>
 *
 * Cụm chỉ là <b>cách nhóm</b> để hiển thị và lọc, không mang ý nghĩa phân quyền. Người ở Xí nghiệp A
 * vẫn cần nhìn thấy tên cụm của Xí nghiệp B khi đọc một báo cáo toàn Công ty; thứ họ không được thấy
 * là <i>công trình</i> bên trong, và điều đó do {@link Construction} lo — nó mới là bản ghi mang dữ
 * liệu.
 *
 * <p>⛔ Hệ quả phải giữ: {@code cluster_id} không được xuất hiện trong bất kỳ truy vấn phân quyền
 * nào. Có hai nguồn phạm vi thì sớm muộn chúng lệch nhau, và bên lỏng hơn sẽ thắng mà không ai biết.
 */
@Entity
@Table(name = "construction_clusters")
@Audited(module = "ops", entityType = "Cụm công trình")
public class ConstructionCluster extends BaseEntity {

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** Đơn vị quản lý cụm — để hiển thị và lọc, <b>không</b> thay thế đơn vị phụ trách từng công trình. */
    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected ConstructionCluster() {}

    public ConstructionCluster(String code, String name, Long orgUnitId) {
        this.code = code;
        this.name = name;
        this.orgUnitId = orgUnitId;
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

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
