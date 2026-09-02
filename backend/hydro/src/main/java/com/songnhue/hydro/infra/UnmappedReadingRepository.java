package com.songnhue.hydro.infra;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.MaLaTongHop;

/**
 * Đường <b>ĐỌC</b> của {@code hydro_unmapped_readings} — T31.13.
 *
 * <p>Nửa còn lại của cặp mà {@code HydroTimeSeriesWriter.writeUnmapped} mở ra từ WS-29. Từ 01/09 tới
 * 02/09 bảng này chỉ có đường ghi: số đo của 9 mã chưa khai được giữ đúng như quy tắc 18 hứa, và
 * <b>không ai nhìn thấy chúng</b> — kể cả người sẽ phải khai chúng.
 *
 * <p>⛔ Không lớp nào ở đây mở giao dịch — ranh giới transaction thuộc tầng application.
 */
@Repository
public class UnmappedReadingRepository {

    /**
     * Gộp theo mã nguồn — <b>một dòng cho một mã</b>, kèm câu trả lời "mã này đã khai chưa".
     *
     * <h2>⚠ {@code LEFT JOIN stations} ⛔ không nhân dòng — và đó là một bảo đảm của LƯỢC ĐỒ</h2>
     *
     * <p>{@code ux_stations_api_code} là {@code UNIQUE (api_code) WHERE deleted_at IS NULL}, nên với
     * mỗi {@code api_code} có <b>tối đa một</b> điểm đo còn sống khớp. Nếu chỉ mục ấy mất đi, câu
     * này nhân đôi số bản ghi trong im lặng — nên điều kiện {@code st.deleted_at IS NULL} phải nằm
     * <b>trong mệnh đề JOIN</b>, ⛔ không ở {@code WHERE}: đặt ở {@code WHERE} là biến LEFT JOIN
     * thành INNER JOIN và <b>mọi mã chưa khai biến mất khỏi màn hình chuyên để xem mã chưa khai</b>.
     *
     * <h2>Vì sao không phân trang</h2>
     *
     * <p>Số dòng ra khỏi câu này bằng <b>số mã khác nhau nguồn phát</b> — đo được là 28, ta khai 19,
     * còn 9. Nó bị chặn trên bởi danh mục của nguồn chứ không bởi số bản ghi đã tích, và nó
     * <b>teo dần</b> theo tiến độ khai báo. ⚠ Nếu ngày nào nguồn phát hàng nghìn mã thì câu này phải
     * phân trang — mốc ấy nằm ở {@code TRAN_CANH_BAO}, và vượt mốc thì nơi gọi nói ra, ⛔ không cắt
     * bớt trong im lặng.
     */
    private static final String SQL_TONG_HOP =
            """
            SELECT u.api_code,
                   s.public_id AS nguon_public_id,
                   s.code      AS nguon_code,
                   count(*)    AS so_ban_ghi,
                   min(u.measured_at) AS lan_dau,
                   max(u.measured_at) AS lan_gan_nhat,
                   (array_agg(u.raw_value ORDER BY u.measured_at DESC))[1] AS gia_tri_gan_nhat,
                   (array_agg(u.raw_unit  ORDER BY u.measured_at DESC))[1] AS don_vi,
                   st.code AS ma_diem_do
              FROM hydro_unmapped_readings u
              JOIN api_sources s ON s.id = u.api_source_id
              LEFT JOIN stations st ON st.api_code = u.api_code AND st.deleted_at IS NULL
             GROUP BY u.api_code, s.public_id, s.code, st.code
             ORDER BY count(*) DESC, u.api_code
            """;

    /**
     * Trên mốc này thì danh sách không phân trang không còn đọc được nữa — xem {@link #SQL_TONG_HOP}.
     *
     * <p>⚠ Đây là ngưỡng <b>nói ra</b>, ⛔ không phải ngưỡng cắt: cắt bớt trong im lặng là đúng hình
     * dạng A3 (modal xin {@code size=1000}, {@code PageUtils} kẹp về 100 không một dòng chữ nào).
     */
    public static final int TRAN_CANH_BAO = 200;

    private final JdbcTemplate jdbc;

    public UnmappedReadingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MaLaTongHop> tongHopTheoMa() {
        return jdbc.query(SQL_TONG_HOP, MOT_MA);
    }

    private static final RowMapper<MaLaTongHop> MOT_MA = (rs, i) -> {
        String maDiemDo = rs.getString("ma_diem_do");
        Timestamp lanDau = rs.getTimestamp("lan_dau");
        Timestamp lanGanNhat = rs.getTimestamp("lan_gan_nhat");
        return new MaLaTongHop(
                rs.getString("api_code"),
                (UUID) rs.getObject("nguon_public_id"),
                rs.getString("nguon_code"),
                rs.getLong("so_ban_ghi"),
                lanDau == null ? null : lanDau.toInstant(),
                lanGanNhat == null ? null : lanGanNhat.toInstant(),
                rs.getBigDecimal("gia_tri_gan_nhat"),
                rs.getString("don_vi"),
                maDiemDo != null,
                maDiemDo);
    };
}
