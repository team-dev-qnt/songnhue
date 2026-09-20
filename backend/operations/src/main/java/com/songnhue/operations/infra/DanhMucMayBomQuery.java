package com.songnhue.operations.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.operations.domain.DongNhomMay;

/**
 * Danh mục nhóm máy kèm thông tin trạm — <b>toàn Công ty</b>.
 *
 * <h2>⛔⛔ SQL thuần là CỐ Ý: nó đi NGOÀI bộ lọc phạm vi đơn vị</h2>
 *
 * <p>{@code Construction} là {@code ScopedEntity}; đọc qua JPA thì người của Xí nghiệp A dựng ra một
 * Bảng 1 chỉ có trạm của A, và cùng một kỳ báo cáo có bao nhiêu người mở thì bấy nhiêu con số "Tổng
 * số máy". Báo cáo nhanh là văn bản <b>cấp Công ty</b> gửi UBND — ⛔ một con số nào được phụ thuộc
 * người đang xem. Cổng ở đây là mã quyền {@code ops:report:view} (bảng lọc {@code @Filter} ⛔ áp cho
 * SQL thuần — coding-guide §4). Cùng quyết định với danh bạ CN-04.6.
 */
@Repository
public class DanhMucMayBomQuery {

    private static final String SQL =
            """
            SELECT n.id, n.public_id, n.so_may, n.q_mot_may_m3h, n.sort_order,
                   c.id AS cid, c.public_id AS cpid, c.code, c.name, c.basin_note, c.org_unit_id
              FROM nhom_may_bom n
              JOIN constructions c ON c.id = n.construction_id AND c.deleted_at IS NULL
             WHERE n.deleted_at IS NULL
             ORDER BY n.sort_order, n.id
            """;

    private final JdbcTemplate jdbc;

    public DanhMucMayBomQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Mọi nhóm máy còn sống, theo thứ tự tệp nhập. */
    public List<DongNhomMay> tatCa() {
        return jdbc.query(
                SQL,
                (rs, i) -> new DongNhomMay(
                        rs.getLong("id"),
                        rs.getObject("public_id", UUID.class),
                        rs.getLong("cid"),
                        rs.getObject("cpid", UUID.class),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("basin_note"),
                        rs.getLong("org_unit_id"),
                        rs.getInt("so_may"),
                        rs.getBigDecimal("q_mot_may_m3h"),
                        rs.getInt("sort_order")));
    }
}
