package com.songnhue.hydro.infra;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.ChatLuongNgayRow;
import com.songnhue.hydro.domain.DongBoNgayRow;

/**
 * Truy vấn nuôi báo cáo thuỷ văn — WS-34.
 *
 * <h2>⛔ Mọi câu ở đây đọc {@code hydro_agg_daily}, ⛔ không scan {@code hydro_readings}</h2>
 *
 * <p>Quy tắc 8 của dự án. Con số đằng sau nó: một điểm đo × một chỉ số sinh <b>144 bản ghi mỗi
 * ngày</b>; báo cáo tháng của 19 điểm đo là ~82 nghìn dòng cho một bảng 30 hàng, và NFR-04 đòi dưới
 * 60 giây. Bảng tổng hợp trả lời cùng câu hỏi ấy bằng vài trăm hàng.
 *
 * <p>⚠ Ngoại lệ hợp lệ <b>duy nhất</b> là BC-12 (chi tiết theo yêu cầu) — nó tồn tại ĐỂ hiện từng
 * bản ghi, nên nó đọc bảng gốc trong một khoảng ngày có cận. Khi dựng nó, ⛔ đừng để nó thành cái
 * cớ mở đường cho các báo cáo khác quay lại quét raw.
 */
@Repository
public class HydroReportRepository {

    /**
     * ⭐⭐ BC-13 — chất lượng dữ liệu theo (điểm đo × chỉ số × ngày). T34.3.
     *
     * <h3>Vì sao phải {@code CROSS JOIN} với một dãy ngày sinh ra tại chỗ</h3>
     *
     * <p>Cột chịu lực của báo cáo này là <b>"số khung 10' bị bỏ sót"</b> — phép đo <b>duy nhất</b>
     * của NFR-03 (T37.1: 7 ngày liên tục, 1008 khung). Một ngày poller chết hoàn toàn ⛔ <b>không có
     * hàng nào</b> trong {@code hydro_agg_daily}; nếu chỉ {@code SELECT … FROM hydro_agg_daily} thì
     * ngày tệ nhất là ngày <b>biến mất khỏi báo cáo</b>, và bảng trông sạch sẽ đúng lúc nó phải kêu.
     * Đó là quy tắc 16 ở dạng nguy hiểm nhất: sự vắng mặt đọc như sự bình thường.
     *
     * <p>⇒ Khung hàng dựng từ {@code generate_series(ngày) × (điểm đo × chỉ số đang khai)}, rồi
     * {@code LEFT JOIN} số liệu vào. Ngày rỗng ra hàng có ba bộ đếm bằng 0, và Java biến nó thành
     * "bỏ sót trọn ngày".
     *
     * <p>⚠⚠ Ngoại lệ CÓ TÊN của quy tắc 14. Câu này đọc <b>cả ba</b> nhóm chất lượng và đó là lý do
     * nó tồn tại — nó đang <i>đếm</i> dữ liệu xấu. {@code FILTER (WHERE quality = …)} ở đây ⛔
     * <b>không</b> được đọc là "đã lọc": bộ canh {@code QualityFilterGuardTest} bóc mệnh đề
     * {@code FILTER} trước khi khớp, đúng vì lý do ấy.
     *
     * <p>⭐ {@code dau.ngay_dau} — ngày có số đo <b>đầu tiên</b> của mỗi cặp. Ngày trước mốc ấy ⛔
     * không phải "bỏ sót 144 khung": điểm đo khi ấy chưa được theo dõi. Trả 0 ở đó là bịa ra một sự
     * cố; Java biến nó thành ô rỗng <b>kèm lý do</b> (quy tắc 16).
     */
    private static final String SQL_CHAT_LUONG_NGAY =
            """
            WITH ngay AS (
                SELECT d::date AS ngay
                  FROM generate_series(?::date, ?::date, INTERVAL '1 day') AS d
            ),
            cap AS (
                SELECT s.id AS station_id, s.code AS station_code, s.name AS station_name,
                       s.active AS station_active,
                       m.id AS type_id, m.code AS type_code, m.name AS type_name
                  FROM station_measurement_types smt
                  JOIN stations s ON s.id = smt.station_id AND s.deleted_at IS NULL
                  JOIN measurement_types m ON m.id = smt.measurement_type_id AND m.deleted_at IS NULL
                 WHERE (?::bigint IS NULL OR s.id = ?::bigint)
            ),
            dau AS (
                SELECT station_id, measurement_type_id, min(agg_date) AS ngay_dau
                  FROM hydro_agg_daily
                 GROUP BY station_id, measurement_type_id
            )
            SELECT n.ngay,
                   c.station_code, c.station_name, c.station_active,
                   c.type_code, c.type_name,
                   d.ngay_dau,
                   coalesce(sum(a.reading_count) FILTER (WHERE a.quality = 'HOP_LE'), 0) AS so_hop_le,
                   coalesce(sum(a.reading_count) FILTER (WHERE a.quality = 'NGHI_NGO'), 0) AS so_nghi_ngo,
                   coalesce(sum(a.reading_count) FILTER (WHERE a.quality = 'XOA'), 0) AS so_da_xoa,
                   max(a.computed_at) AS tinh_luc
              FROM ngay n
              CROSS JOIN cap c
              LEFT JOIN dau d
                     ON d.station_id = c.station_id AND d.measurement_type_id = c.type_id
              LEFT JOIN hydro_agg_daily a
                     ON a.station_id = c.station_id
                    AND a.measurement_type_id = c.type_id
                    AND a.agg_date = n.ngay
             GROUP BY n.ngay, c.station_code, c.station_name, c.station_active,
                      c.type_code, c.type_name, d.ngay_dau
             ORDER BY n.ngay DESC, c.station_code, c.type_code
            """;

    /**
     * BC-13, phần dưới — tổng hợp {@code sync_logs} theo ngày × nguồn. T34.3.
     *
     * <p>⭐ Bốn kết cục đếm <b>riêng</b>, và {@code SKIPPED_UP_TO_DATE} ⛔ không được gộp vào
     * "thành công": nó là lượt gọi <b>đã bỏ</b> vì mọi điểm đo đã có bản ghi của khung hiện tại — số
     * ấy cao là <i>tốt</i> (rate-limit đang làm việc), trong khi số "thành công" cao mà số ghi mới
     * bằng 0 là dấu hiệu ngược lại. Gộp chúng là xoá đúng sự phân biệt ấy. §10.68-B: cùng một vân
     * tay cho ba nguyên nhân cần ba cách xử lý khác nhau.
     *
     * <p>⚠ Cắt ngày bằng {@code hyd_dau_ngay_vn} thay vì so {@code hyd_ngay_vn(started_at) = ?}:
     * dạng sau là vị từ trên biểu thức của cột, ⛔ không dùng được chỉ mục
     * {@code ix_sync_logs_source_time}.
     */
    private static final String SQL_DONG_BO_NGAY =
            """
            SELECT hyd_ngay_vn(sl.started_at) AS ngay,
                   src.code AS source_code, src.name AS source_name,
                   count(*) AS so_luot,
                   count(*) FILTER (WHERE sl.status = 'SUCCESS') AS so_thanh_cong,
                   count(*) FILTER (WHERE sl.status = 'PARTIAL') AS so_mot_phan,
                   count(*) FILTER (WHERE sl.status = 'FAILED') AS so_hong,
                   count(*) FILTER (WHERE sl.status = 'SKIPPED_UP_TO_DATE') AS so_bo_qua,
                   coalesce(sum(sl.received_count), 0) AS so_nhan,
                   coalesce(sum(sl.written_count), 0) AS so_ghi_moi,
                   coalesce(sum(sl.skipped_count), 0) AS so_trung,
                   coalesce(sum(sl.unmapped_count), 0) AS so_ma_la,
                   max(sl.started_at) FILTER (WHERE sl.status = 'FAILED') AS hong_gan_nhat
              FROM sync_logs sl
              JOIN api_sources src ON src.id = sl.api_source_id
             WHERE sl.started_at >= hyd_dau_ngay_vn(?)
               AND sl.started_at <  hyd_dau_ngay_vn((?::date) + 1)
             GROUP BY 1, src.code, src.name
             ORDER BY 1 DESC, src.code
            """;

    /**
     * ⚠⚠ {@code rs.getObject(col, Instant.class)} <b>ném</b> với cột {@code timestamptz} — trình
     * điều khiển PostgreSQL ⛔ không khai phép đổi ấy ("conversion to class java.time.Instant from
     * timestamptz not supported"), dù nó đổi được sang {@code OffsetDateTime}.
     *
     * <p>⭐ Phần đắt ⛔ không phải cái ném: {@code GlobalExceptionHandler} dịch nó thành
     * {@code SYS-0005} — <i>"Dữ liệu vừa được người khác thay đổi"</i>, <b>409</b>. Một lỗi ánh xạ
     * kiểu hiện ra thành một lỗi <i>tranh chấp ghi</i> trên một endpoint <b>chỉ đọc</b>, và người
     * đọc thông điệp ấy sẽ đi tìm ở đúng chỗ ⛔ không có gì. Đo được ngay lượt chạy HTTP đầu tiên
     * của BC-13 — ⛔ không một bài kiểm gọi thẳng service nào thấy được (luật 5).
     */
    private static Instant moc(java.sql.ResultSet rs, String cot) throws java.sql.SQLException {
        java.sql.Timestamp t = rs.getTimestamp(cot);
        return t == null ? null : t.toInstant();
    }

    private final JdbcTemplate jdbc;

    public HydroReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ChatLuongNgayRow> chatLuongTheoNgay(LocalDate tuNgay, LocalDate denNgay, Long stationId) {
        return jdbc.query(
                SQL_CHAT_LUONG_NGAY,
                (rs, i) -> new ChatLuongNgayRow(
                        rs.getObject("ngay", LocalDate.class),
                        rs.getString("station_code"),
                        rs.getString("station_name"),
                        rs.getBoolean("station_active"),
                        rs.getString("type_code"),
                        rs.getString("type_name"),
                        rs.getObject("ngay_dau", LocalDate.class),
                        rs.getInt("so_hop_le"),
                        rs.getInt("so_nghi_ngo"),
                        rs.getInt("so_da_xoa"),
                        moc(rs, "tinh_luc")),
                Date.valueOf(tuNgay),
                Date.valueOf(denNgay),
                stationId,
                stationId);
    }

    public List<DongBoNgayRow> dongBoTheoNgay(LocalDate tuNgay, LocalDate denNgay) {
        return jdbc.query(
                SQL_DONG_BO_NGAY,
                (rs, i) -> new DongBoNgayRow(
                        rs.getObject("ngay", LocalDate.class),
                        rs.getString("source_code"),
                        rs.getString("source_name"),
                        rs.getInt("so_luot"),
                        rs.getInt("so_thanh_cong"),
                        rs.getInt("so_mot_phan"),
                        rs.getInt("so_hong"),
                        rs.getInt("so_bo_qua"),
                        rs.getLong("so_nhan"),
                        rs.getLong("so_ghi_moi"),
                        rs.getLong("so_trung"),
                        rs.getLong("so_ma_la"),
                        moc(rs, "hong_gan_nhat")),
                Date.valueOf(tuNgay),
                Date.valueOf(denNgay));
    }
}
