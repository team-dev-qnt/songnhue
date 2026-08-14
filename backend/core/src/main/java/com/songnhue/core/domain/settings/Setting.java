package com.songnhue.core.domain.settings;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Tham số nghiệp vụ sửa được trên UI (bảng {@code settings}) — quy tắc 12 của dự án.
 *
 * <p>WS-5 chỉ <b>đọc</b> (chính sách mật khẩu, ngưỡng khoá tài khoản, giờ hành chính). Phần ghi,
 * validate theo {@code validation}, cache và xuất/nhập cấu hình thuộc WS-6 / T6.11.
 *
 * <p>Tên cột là {@code setting_key}/{@code setting_value} chứ không phải {@code key}/{@code value}:
 * cả hai đều là từ khoá của JPQL, đặt trùng thì mọi truy vấn phải trích dẫn ngoặc kép.
 */
@Entity
@Table(name = "settings")
public class Setting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "setting_key", nullable = false, length = 120)
    private String settingKey;

    @Column(name = "setting_value")
    private String settingValue;

    @Column(name = "value_type", nullable = false, length = 20)
    private String valueType;

    @Column(name = "default_value")
    private String defaultValue;

    @Column(name = "group_code", nullable = false, length = 50)
    private String groupCode;

    @Column(name = "label", nullable = false, length = 255)
    private String label;

    @Column(name = "description")
    private String description;

    @Column(name = "validation")
    private String validation;

    @Column(name = "editable", nullable = false)
    private boolean editable = true;

    /** FALSE = loại khỏi bản xuất cấu hình M5.17 (conventions.md §4.7 — credential không được xuất). */
    @Column(name = "exportable", nullable = false)
    private boolean exportable = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    public Long getId() {
        return id;
    }

    public String getSettingKey() {
        return settingKey;
    }

    public String getSettingValue() {
        return settingValue;
    }

    public String getValueType() {
        return valueType;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getGroupCode() {
        return groupCode;
    }

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public String getValidation() {
        return validation;
    }

    public boolean isEditable() {
        return editable;
    }

    public boolean isExportable() {
        return exportable;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Integer getVersion() {
        return version;
    }

    /** Giá trị đang hiệu lực: giá trị đã đặt, thiếu thì lấy mặc định của danh mục. */
    public String effectiveValue() {
        return settingValue != null && !settingValue.isBlank() ? settingValue : defaultValue;
    }
}
