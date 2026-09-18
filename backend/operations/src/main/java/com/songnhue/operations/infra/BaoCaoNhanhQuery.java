package com.songnhue.operations.infra;

import java.util.Collection;
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

    private static final String SQL_DIEM_MUA =
            """
            SELECT id, public_id, ten, sort_order
              FROM diem_mua_bao_cao_nhanh
             WHERE deleted_at IS NULL
             ORDER BY sort_order
            """;

    private static final String SQL_CONG_TRINH =
            """
            SELECT id, public_id, code, name, construction_type
              FROM constructions
             WHERE deleted_at IS NULL
            """;

    /** Một xã của Bảng 5. */
    public record Xa(Long id, UUID publicId, String ten, int thuTu) {}

    /** Một điểm mưa của Bảng 4 — {@code thuTu} = STT in ra bản Word. */
    public record DiemMua(Long id, UUID publicId, String ten, int thuTu) {}

    /** Công trình để gắn vào một vị trí của mẫu — đọc TOÀN Công ty, ⛔ lọc phạm vi. */
    public record CongTrinh(Long id, UUID publicId, String ma, String ten, String loai) {}

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

    /** 8 điểm mưa của Sông Nhuệ, đúng thứ tự Bảng 4. */
    public List<DiemMua> diemMua() {
        return jdbc.query(
                SQL_DIEM_MUA,
                (rs, i) -> new DiemMua(
                        rs.getLong("id"),
                        rs.getObject("public_id", UUID.class),
                        rs.getString("ten"),
                        rs.getInt("sort_order")));
    }

    /** Công trình theo loại hình, sắp theo tên — nguồn ô chọn của màn hình cấu hình. */
    public List<CongTrinh> congTrinhTheoLoai(String loai) {
        return jdbc.query(
                SQL_CONG_TRINH + " AND construction_type = ? ORDER BY name, id", BaoCaoNhanhQuery::congTrinh, loai);
    }

    public List<CongTrinh> congTrinhTheoPublicId(UUID publicId) {
        return jdbc.query(SQL_CONG_TRINH + " AND public_id = ?", BaoCaoNhanhQuery::congTrinh, publicId);
    }

    /** Tải hàng loạt theo khoá nội bộ — công trình đã xoá mềm ⛔ có mặt. */
    public List<CongTrinh> congTrinhTheoIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbc.query(
                con -> {
                    var ps = con.prepareStatement(SQL_CONG_TRINH + " AND id = ANY (?)");
                    ps.setArray(1, con.createArrayOf("bigint", ids.toArray()));
                    return ps;
                },
                BaoCaoNhanhQuery::congTrinh);
    }

    private static CongTrinh congTrinh(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new CongTrinh(
                rs.getLong("id"),
                rs.getObject("public_id", UUID.class),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("construction_type"));
    }
}
