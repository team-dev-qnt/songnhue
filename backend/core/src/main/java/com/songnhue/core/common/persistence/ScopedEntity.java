package com.songnhue.core.common.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;

/**
 * Entity thuộc phạm vi một đơn vị (Xí nghiệp / phòng ban) — tầng 3 của phân quyền
 * (conventions.md §4.2).
 *
 * <p>Mọi entity có dữ liệu "chỉ đơn vị mình được xem" <b>bắt buộc</b> kế thừa lớp này: hồ sơ công
 * trình, lịch sử sửa chữa, hồ sơ nhân viên, đơn nghỉ phép…
 *
 * <p>Lý do có lớp riêng thay vì để mỗi repository tự thêm {@code WHERE org_unit_id = ?}: quy tắc 5
 * của dự án — <b>lọc phạm vi phải nằm ở tầng repository, không dựa vào việc lập trình viên nhớ
 * thêm điều kiện</b>. Quên một chỗ là rò rỉ dữ liệu giữa các Xí nghiệp, mà loại lỗi này không tự
 * biểu hiện: hệ thống vẫn chạy, chỉ là ai cũng xem được dữ liệu của đơn vị khác.
 *
 * <p>⬜ Bộ lọc Hibernate được bật tự động theo đơn vị của người đăng nhập ở <b>WS-5 / T5.11</b>.
 * Khai báo {@code @FilterDef} đặt sẵn ở đây để entity của Phase 1 khai báo {@code @Filter} được
 * ngay, không phải chờ.
 */
@MappedSuperclass
@FilterDef(
        name = ScopedEntity.ORG_UNIT_FILTER,
        parameters = @ParamDef(name = ScopedEntity.ORG_UNIT_PATHS_PARAM, type = String.class))
public abstract class ScopedEntity extends BaseEntity {

    /** Tên bộ lọc Hibernate — WS-5 bật theo từng session. */
    public static final String ORG_UNIT_FILTER = "orgUnitScopeFilter";

    /** Tham số: materialized path của đơn vị người dùng, để lấy cả cây con. */
    public static final String ORG_UNIT_PATHS_PARAM = "orgUnitPathPrefix";

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }
}
