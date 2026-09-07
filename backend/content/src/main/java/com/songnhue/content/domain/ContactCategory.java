package com.songnhue.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một phân loại liên hệ — CN-01.4.
 *
 * <h2>Vì sao là một BẢNG chứ không phải một enum</h2>
 *
 * <p>Quy tắc 16 của dự án: danh mục do khách vận hành là <b>dữ liệu có CRUD</b>, không phải enum
 * trong mã — thêm một mã mới ⛔ không được đòi một lượt deploy. "Góp ý", "Khiếu nại", "Hỏi đáp" là
 * cách <i>Công ty</i> phân loại công việc của họ, và cách ấy đổi theo năm.
 *
 * <h2>⛔ Bảng ra đời RỖNG</h2>
 *
 * <p>Chưa có văn bản nào của Công ty cấp danh sách này. Seed vài giá trị "cho màn hình đỡ trống" là
 * biến một ô <i>chưa ai quyết</i> thành một ô <i>trông như đã cấu hình xong</i> — đúng hình dạng mà
 * CLAUDE.md cấm. Màn hình nói thẳng là rỗng và mời người dùng thêm.
 */
@Entity
@Table(name = "contact_categories")
@Audited(module = "cms", entityType = "ContactCategory")
public class ContactCategory extends BaseEntity {

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    /**
     * Còn hiện ở ô chọn hay không.
     *
     * <p>⚠ Cờ này ⛔ <b>không</b> ảnh hưởng tới các liên hệ đã gán: tắt một phân loại mà làm bản ghi
     * cũ mất phân loại thì báo cáo theo phân loại ⛔ không đọc lại được lịch sử. Tắt nghĩa là "đừng
     * gán thêm nữa", ⛔ không phải "coi như chưa từng có".
     */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ContactCategory() {}

    public ContactCategory(String code, String name, int sortOrder) {
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void apDung(String name, boolean active, int sortOrder) {
        this.name = name;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
