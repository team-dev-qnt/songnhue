package com.songnhue.operations.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một lớp bản đồ do người vận hành nạp — CN-02.4 / M2.9.
 *
 * <h2>⛔ ⛔ KHÔNG phải {@code ScopedEntity}</h2>
 *
 * <p>Một lớp *"Ranh giới lưu vực sông Nhuệ"* ⛔ không thuộc về Xí nghiệp nào — nó là nền bản đồ
 * dùng chung. Gắn phạm vi vào đây là để mỗi Xí nghiệp thấy một tấm bản đồ khác nhau, và ⛔ không ai
 * nhìn được toàn hệ. Cổng quyền là {@code ops:gis-layer:view} / {@code :manage}, ⛔ không phải phạm
 * vi đơn vị.
 *
 * <h2>⛔ Nội dung tệp ở {@code attachments}</h2>
 *
 * <p>{@code attachmentPublicId} trỏ sang kho tệp chung ({@code owner_type = 'GIS_LAYER'}), nên lớp
 * này thừa hưởng quét virus, hạn mức, phiên bản và xoá mềm. Chép một {@code bytea} vào đây là dựng
 * kho tệp thứ hai với ⛔ không một cơ chế nào trong số đó.
 */
@Entity
@Table(name = "gis_layers")
@Audited(module = "ops", entityType = "Lớp bản đồ GIS")
public class GisLayer extends BaseEntity {

    /** Khớp {@code attachments.owner_type}. */
    public static final String OWNER_TYPE = "GIS_LAYER";

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "geometry_type", nullable = false, length = 20)
    private GisGeometryType geometryType;

    @Column(name = "color", nullable = false, length = 7)
    private String color = "#1677ff";

    /** ⛔ Phần trăm NGUYÊN 0–100 — xem chú thích cột ở {@code V202609141080}. */
    @Column(name = "opacity", nullable = false)
    private short opacity = 70;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "attachment_public_id")
    private UUID attachmentPublicId;

    @Column(name = "feature_count")
    private Integer featureCount;

    protected GisLayer() {}

    public GisLayer(String name, GisGeometryType geometryType) {
        this.name = name;
        this.geometryType = geometryType;
    }

    /**
     * Gắn tệp vừa nạp — <b>hai cột đi thành CẶP</b>.
     *
     * <p>⛔ Hai setter riêng thì sẽ có lượt gọi đặt cột này mà quên cột kia, và
     * {@code ck_gis_layers_tep_va_so_doi_tuong} từ chối ở tầng CSDL — đúng lúc người dùng vừa chờ
     * một lượt tải 20 MB xong. Một hàm nhận cả hai thì ⛔ không có khe hở đó.
     */
    public void ganTep(UUID attachmentPublicId, Integer featureCount, GisGeometryType geometryType) {
        this.attachmentPublicId = attachmentPublicId;
        this.featureCount = featureCount;
        this.geometryType = geometryType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public GisGeometryType getGeometryType() {
        return geometryType;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public short getOpacity() {
        return opacity;
    }

    public void setOpacity(short opacity) {
        this.opacity = opacity;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public UUID getAttachmentPublicId() {
        return attachmentPublicId;
    }

    public Integer getFeatureCount() {
        return featureCount;
    }
}
