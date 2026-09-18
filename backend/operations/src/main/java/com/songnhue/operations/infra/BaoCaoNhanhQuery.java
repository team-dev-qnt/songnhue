package com.songnhue.operations.infra;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.operations.domain.DongNhomMay;
import com.songnhue.operations.domain.TinhBaoCaoNhanh.DongVanHanh;

/**
 * Đọc của Báo cáo nhanh — toàn Công ty, SQL thuần NGOÀI bộ lọc phạm vi (lý do ở
 * {@link DanhMucMayBomQuery}).
 */
@Repository
public class BaoCaoNhanhQuery {

    /**
     * Ảnh chụp của một kỳ — số thiết kế + Q lấy từ BẢNG CỦA KỲ, ⛔ từ danh mục.
     *
     * <p>⛔ ⛔ lọc {@code deleted_at} ở {@code nhom_may_bom}/{@code constructions}: nhóm máy hay trạm bị
     * xoá SAU khi kỳ đã chốt vẫn phải hiện trong văn bản đã gửi.
     */
    private static final String SQL_ANH_CHUP =
            """
            SELECT v.so_may_van_hanh, v.so_may_thiet_ke, v.q_mot_may_m3h,
                   n.id, n.public_id, n.sort_order,
                   c.id AS cid, c.public_id AS cpid, c.code, c.name, c.basin_note, c.org_unit_id
              FROM bao_cao_nhanh_van_hanh v
              JOIN nhom_may_bom n ON n.id = v.nhom_may_id
              JOIN constructions c ON c.id = n.construction_id
             WHERE v.bao_cao_id = ? AND v.deleted_at IS NULL
             ORDER BY n.sort_order, n.id
            """;

    private static final String SQL_XA =
            """
            SELECT id, public_id, ten, sort_order
              FROM don_vi_hanh_chinh
             WHERE deleted_at IS NULL AND cong_ty_thuy_loi = ?
             ORDER BY sort_order
            """;

    /** Một xã của Bảng 5. */
    public record Xa(Long id, UUID publicId, String ten, int thuTu) {}

    private final JdbcTemplate jdbc;

    public BaoCaoNhanhQuery(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<DongVanHanh> anhChup(Long baoCaoId) {
        return jdbc.query(
                SQL_ANH_CHUP,
                (rs, i) -> new DongVanHanh(
                        new DongNhomMay(
                                rs.getLong("id"),
                                rs.getObject("public_id", UUID.class),
                                rs.getLong("cid"),
                                rs.getObject("cpid", UUID.class),
                                rs.getString("code"),
                                rs.getString("name"),
                                rs.getString("basin_note"),
                                rs.getLong("org_unit_id"),
                                rs.getInt("so_may_thiet_ke"),
                                rs.getBigDecimal("q_mot_may_m3h"),
                                rs.getInt("sort_order")),
                        (Integer) rs.getObject("so_may_van_hanh", Integer.class)),
                baoCaoId);
    }

    /** Các xã của một công ty thuỷ lợi, đúng thứ tự Bảng 5. */
    public List<Xa> xa(String congTy) {
        return jdbc.query(
                SQL_XA,
                (rs, i) -> new Xa(
                        rs.getLong("id"),
                        rs.getObject("public_id", UUID.class),
                        rs.getString("ten"),
                        rs.getInt("sort_order")),
                congTy);
    }
}
