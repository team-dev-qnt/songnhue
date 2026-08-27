package com.songnhue.core.domain.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.songnhue.core.common.audit.Audited;
import com.songnhue.core.common.persistence.BaseEntity;

/**
 * Một dòng danh bạ lãnh đạo mà Công ty <b>chủ động công bố</b> trên cổng — CR-25, CR-26.
 *
 * <h2>Không phải hồ sơ nhân sự, và sự tách biệt ấy là có chủ đích</h2>
 *
 * <p>Nội dung ở đây cùng loại với số điện thoại in trên bảng hiệu: tên, chức danh, số máy công vụ.
 * Nó <b>không</b> nối vào {@code employees} của MOD-04, nên endpoint công khai đọc nó không có
 * đường nào chạm tới trường nhạy cảm (quy tắc 10, NĐ 13/2023). Nối hai thứ lại thì mỗi lần sửa
 * lược đồ nhân sự đều phải chứng minh lại rằng không có gì rò ra cổng.
 *
 * <h2>Phân biệt với {@code OrgUnit.headUserId}</h2>
 *
 * <p>{@code headUserId} trỏ tới một <b>tài khoản</b> và phục vụ luồng duyệt / thông báo. Người
 * đứng đầu một Xí nghiệp có thể không có tài khoản nào trong hệ thống, và một tài khoản không phải
 * là một dòng danh bạ. Hai khái niệm khác nhau, cố ý không gộp.
 *
 * <p><b>Không kế thừa {@code ScopedEntity}</b> — cùng lý lẽ với {@link OrgUnit}: đây là dữ liệu
 * công bố ra ngoài, ai cũng xem được, nên nó không nằm trong phạm vi đơn vị nào.
 */
@Entity
@Table(name = "org_unit_leaders")
@Audited(module = "adm", entityType = "Lãnh đạo đơn vị")
public class OrgUnitLeader extends BaseEntity {

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    @Column(name = "full_name", nullable = false, length = 255)
    private String fullName;

    /** Chức danh hiển thị nguyên văn ở cột 2 của bảng Lãnh đạo Công ty. */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * Điện thoại liên hệ công vụ.
     *
     * <p>Cho phép {@code null}: Công ty có thể công bố tên và chức danh mà chưa công bố số. Ô trống
     * trung thực hơn một số đã cũ (quy tắc 16) — nơi hiển thị phải trả về dấu gạch, không phải một
     * giá trị mặc định nào đó.
     */
    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    /** Tắt khi người đó chuyển công tác — giữ dòng lại để đối chiếu lịch sử. */
    @Column(name = "active", nullable = false)
    private boolean active = true;

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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
