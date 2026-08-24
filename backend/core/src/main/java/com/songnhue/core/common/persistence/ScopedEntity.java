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
 * <p>Bộ lọc được {@code ScopeFilterAspect} bật tự động theo đơn vị của người đăng nhập (WS-5 /
 * T5.11). Entity cụ thể chỉ cần khai báo:
 *
 * <pre>
 * &#64;Entity
 * &#64;Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
 * public class Construction extends ScopedEntity { … }
 * </pre>
 */
@MappedSuperclass
@FilterDef(
        name = ScopedEntity.ORG_UNIT_FILTER,
        parameters = @ParamDef(name = ScopedEntity.ORG_UNIT_PATHS_PARAM, type = String.class))
public abstract class ScopedEntity extends BaseEntity {

    /** Tên bộ lọc Hibernate — bật theo từng session, xem {@code ScopeFilterAspect}. */
    public static final String ORG_UNIT_FILTER = "orgUnitScopeFilter";

    /** Tham số: materialized path của đơn vị người dùng, để lấy cả cây con. */
    public static final String ORG_UNIT_PATHS_PARAM = "orgUnitPathPrefix";

    /**
     * Điều kiện SQL của bộ lọc — <b>viết đúng một lần ở đây</b>, mọi entity dùng lại hằng này.
     *
     * <p>Lọc theo <i>materialized path</i> chứ không phải theo {@code org_unit_id = ?}: người quản lý
     * Xí nghiệp phải thấy được cả các Tổ đội trực thuộc, mà so bằng id thì chỉ ra đúng một cấp.
     *
     * <p>Người ở nút gốc có path {@code /1/}, và path của MỌI đơn vị đều bắt đầu bằng {@code /1/} —
     * nên họ tự nhiên thấy toàn bộ dữ liệu. Không cần thêm cờ "bỏ qua phạm vi" nào, mà cờ như vậy
     * chính là thứ hay bị bật nhầm rồi không ai để ý.
     *
     * <p>Truy vấn con chạy trên chỉ mục {@code ix_org_units_path} ({@code varchar_pattern_ops}) —
     * cây đơn vị chỉ vài chục dòng nên chi phí không đáng kể.
     */
    public static final String ORG_UNIT_FILTER_CONDITION =
            "org_unit_id IN (SELECT ou.id FROM org_units ou WHERE ou.path LIKE :orgUnitPathPrefix || '%')";

    @Column(name = "org_unit_id", nullable = false)
    private Long orgUnitId;

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public void setOrgUnitId(Long orgUnitId) {
        this.orgUnitId = orgUnitId;
    }
}
