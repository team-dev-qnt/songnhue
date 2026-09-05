package com.songnhue.hydro.infra;

import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.ChatLuongNgayRow;
import com.songnhue.hydro.domain.ChiTietSoDoRow;
import com.songnhue.hydro.domain.DongBoNgayRow;
import com.songnhue.hydro.domain.TongHopKyRow;
import com.songnhue.hydro.domain.TuyenSongRow;

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
     * ⭐⭐ BC-05 — tổng hợp kỳ theo (điểm đo × chỉ số). T34.5.
     *
     * <h3>⚠⚠ {@code quality = 'HOP_LE'} nằm ở mệnh đề {@code ON}, ⛔ KHÔNG ở {@code WHERE}</h3>
     *
     * <p>Đây là chỗ dễ sai nhất của cả tệp, và cái sai thì <b>im lặng</b>: đẩy vị từ ấy xuống
     * {@code WHERE} biến {@code LEFT JOIN} thành {@code INNER JOIN}, nên mọi điểm đo <b>không có
     * số liệu hợp lệ trong kỳ</b> — tức đúng những điểm đo đang có vấn đề — <b>biến mất khỏi báo
     * cáo</b>. Bảng khi ấy sạch sẽ, đủ hàng, và ⛔ không nói ra rằng nó vừa giấu đi thứ cần xem
     * nhất. Cùng hình dạng đã ghi ở {@link #SQL_CHAT_LUONG_NGAY}: sự vắng mặt đọc như sự bình
     * thường (quy tắc 16).
     *
     * <h3>⭐ Trung bình kỳ = {@code SUM(sum_value) / SUM(reading_count)}</h3>
     *
     * <p>⛔ <b>Không</b> {@code avg(avg_value)}. Ngày có 144 bản ghi và ngày poller chết chỉ có 12
     * bản ghi sẽ được tính <b>cùng trọng số</b>, và một ngày dữ liệu thưa kéo cả kỳ theo nó. Con số
     * sai ấy vẫn nằm trong khoảng min/max của kỳ nên ⛔ <b>không có cách nào nhìn ra bằng mắt</b> —
     * đó là toàn bộ lý do cột {@code sum_value} tồn tại.
     *
     * <p>⭐ {@code array_agg(… ORDER BY …)[1]} lấy <b>mốc</b> đạt max/min của kỳ từ chính hàng ngày
     * đã đạt nó — BC-05 đòi "kèm thời điểm đạt max/min", và ⛔ không phải quay lại quét số đo thô
     * (quy tắc 8). Khoá phụ theo mốc làm phép chọn tất định khi nhiều ngày cùng giá trị.
     */
    private static final String SQL_TONG_HOP_KY =
            """
            WITH cap AS (
                SELECT s.id AS station_id, s.code AS station_code, s.name AS station_name,
                       s.river_name, s.position_role,
                       m.id AS type_id, m.code AS type_code, m.name AS type_name, m.unit
                  FROM station_measurement_types smt
                  JOIN stations s ON s.id = smt.station_id AND s.deleted_at IS NULL
                  JOIN measurement_types m ON m.id = smt.measurement_type_id AND m.deleted_at IS NULL
                 WHERE (?::bigint IS NULL OR s.id = ?::bigint)
            )
            SELECT c.station_code, c.station_name, c.river_name, c.position_role,
                   c.type_code, c.type_name, c.unit,
                   coalesce(sum(a.reading_count), 0) AS so_ban_ghi,
                   count(a.id) AS so_ngay_co_du_lieu,
                   min(a.min_value) AS gia_tri_min,
                   (array_agg(a.min_at ORDER BY a.min_value ASC, a.min_at ASC))[1] AS moc_min,
                   max(a.max_value) AS gia_tri_max,
                   (array_agg(a.max_at ORDER BY a.max_value DESC, a.max_at ASC))[1] AS moc_max,
                   CASE WHEN coalesce(sum(a.reading_count), 0) > 0
                        THEN round(sum(a.sum_value) / sum(a.reading_count), 3)
                   END AS gia_tri_tb
              FROM cap c
              LEFT JOIN hydro_agg_daily a
                     ON a.station_id = c.station_id
                    AND a.measurement_type_id = c.type_id
                    AND a.agg_date >= ?
                    AND a.agg_date <= ?
                    AND a.quality = 'HOP_LE'
             GROUP BY c.station_code, c.station_name, c.river_name, c.position_role,
                      c.type_code, c.type_name, c.unit
             ORDER BY c.station_code, c.type_code
            """;

    /**
     * ⭐⭐ BC-11 — biểu tổng hợp theo tuyến sông. T34.4.
     *
     * <h3>⚠⚠ {@code quality = 'HOP_LE'} ở mệnh đề {@code ON}, cùng lý do với BC-05</h3>
     *
     * <p>Đẩy xuống {@code WHERE} biến {@code LEFT JOIN} thành {@code INNER JOIN}, và khi ấy đúng
     * những điểm đo <b>đang mất tín hiệu</b> — thứ mà một biểu tổng hợp vận hành sinh ra để chỉ ra —
     * biến mất khỏi bảng.
     *
     * <h3>⭐ Đọc {@code hydro_latest}, ⛔ không đọc bản ghi mới nhất của {@code hydro_readings}</h3>
     *
     * <p>{@code hydro_latest} tách sẵn <b>hai</b> mốc khác nhau và đó là toàn bộ giá trị của nó:
     * {@code valid_*} là <i>giá trị hợp lệ gần nhất</i> (thứ hiện lên bảng), còn {@code last_seen_at}
     * là <i>bản ghi gần nhất bất kể chất lượng</i> (thứ trả lời "trạm còn phát tín hiệu không").
     * Gộp hai câu hỏi ấy vào một cột là dựng ra một trạm mất tín hiệu giả cho mỗi trạm chỉ đang trả
     * số đáng ngờ.
     *
     * <p>⚠ Sắp theo {@code chainage_m} — lý trình, tức <b>thứ tự dọc tuyến sông</b>. Đó là thứ tự
     * người vận hành đọc: thượng lưu trước, hạ lưu sau. ⛔ Sắp theo tên là sắp theo bảng chữ cái,
     * và một tuyến sông ⛔ không chảy theo bảng chữ cái. {@code NULLS LAST} vì lý trình chờ G8.
     */
    private static final String SQL_TUYEN_SONG =
            """
            SELECT s.id AS station_id, s.code AS station_code, s.name AS station_name,
                   s.river_name, s.chainage, s.chainage_m, s.position_role, s.active,
                   m.code AS type_code, m.name AS type_name, m.unit,
                   l.valid_value, l.valid_measured_at, l.last_seen_at,
                   a.min_value AS min_ngay, a.max_value AS max_ngay,
                   a.reading_count AS so_ban_ghi_ngay
              FROM station_measurement_types smt
              JOIN stations s ON s.id = smt.station_id AND s.deleted_at IS NULL
              JOIN measurement_types m ON m.id = smt.measurement_type_id AND m.deleted_at IS NULL
              LEFT JOIN hydro_latest l
                     ON l.station_id = s.id AND l.measurement_type_id = m.id
              LEFT JOIN hydro_agg_daily a
                     ON a.station_id = s.id
                    AND a.measurement_type_id = m.id
                    AND a.agg_date = ?
                    AND a.quality = 'HOP_LE'
             ORDER BY s.river_name NULLS LAST, s.chainage_m NULLS LAST, s.code, m.code
            """;

    /**
     * Công trình mà mỗi điểm đo trỏ tới — để BC-11 hiện được cột <i>tình hình vận hành</i>.
     *
     * <p>⛔ Câu này dừng ở {@code construction_id}: nó ⛔ <b>không</b> đọc một cột nào của bảng
     * {@code constructions} (quy tắc 6 — module ⛔ không chạm bảng của module khác). Tình hình vận
     * hành lấy qua {@code core.spi.ConstructionLookupPort}.
     *
     * <p>⚠ {@code is_primary} quyết định công trình nào được hiện khi một điểm đo nối nhiều công
     * trình — hạ lưu của cống này đồng thời là thượng lưu của cống kế tiếp là chuyện bình thường
     * trên một tuyến kênh.
     */
    private static final String SQL_CONG_TRINH_CUA_DIEM_DO =
            """
            SELECT station_id, construction_id
              FROM station_constructions
             WHERE deleted_at IS NULL AND is_primary
            """;

    /**
     * ⭐ BC-12 — chi tiết theo yêu cầu. T34.6.
     *
     * <h3>⚠⚠ Ngoại lệ hợp lệ DUY NHẤT của quy tắc 8 — và của quy tắc 14</h3>
     *
     * <p>Mọi báo cáo khác đọc bảng tổng hợp. Báo cáo này <b>phải</b> đọc {@code hydro_readings}: nó
     * tồn tại để hiện <i>từng bản ghi</i>, và một bảng tổng hợp theo ngày ⛔ không trả lời được
     * <i>"lúc 14 giờ 20 hôm ấy máy đọc được bao nhiêu"</i> — câu hỏi mà người ta mở báo cáo chi
     * tiết ra để hỏi.
     *
     * <p>Và nó ⛔ <b>không</b> lọc {@code quality}: BC-12 là <b>nơi duy nhất</b> được phép hiện bản
     * ghi {@code NGHI_NGO} và {@code XOA} cạnh bản ghi hợp lệ, vì nó có <b>cột Chất lượng</b> và
     * <b>cột Nguồn</b> để người đọc biết mình đang nhìn cái gì. Bộ lọc bị thay bằng một thứ khác:
     * hai cột nói ra sự thật.
     *
     * <p>⛔ Cả hai ngoại lệ ấy <b>chỉ dùng được ở đây</b>, và cái giá phải trả để giữ chúng an toàn
     * là một khoảng ngày <b>có cận cứng</b> ({@code HYD-2012}) cộng phân trang. ⚠ Đừng để câu này
     * thành cái cớ mở đường cho báo cáo khác quay lại quét bảng gốc.
     *
     * <p>⛔ Cố ý ⛔ <b>không</b> join {@code users} để hiện tên người nhập: đó là bảng của Core
     * (quy tắc 6), và một danh tính trong bản xuất là dữ liệu cá nhân đi ra ngoài mà ⛔ không ai
     * quyết định điều đó. {@code source} đã trả lời đủ câu hỏi <i>"số này từ đâu"</i>.
     */
    private static final String SQL_CHI_TIET =
            """
            SELECT r.measured_at, r.reading_value, r.quality, r.quality_reason,
                   r.source, r.note, r.review_note
              FROM hydro_readings r
             WHERE r.station_id = ?
               AND r.measurement_type_id = ?
               AND r.measured_at >= hyd_dau_ngay_vn(?)
               AND r.measured_at <  hyd_dau_ngay_vn((?::date) + 1)
             ORDER BY r.measured_at DESC
             LIMIT ? OFFSET ?
            """;

    /** ⚠ Ngoại lệ CÓ TÊN cùng lý do với {@link #SQL_CHI_TIET} — phép đếm phải khớp tập được liệt. */
    private static final String SQL_DEM_CHI_TIET =
            """
            SELECT count(*)
              FROM hydro_readings r
             WHERE r.station_id = ?
               AND r.measurement_type_id = ?
               AND r.measured_at >= hyd_dau_ngay_vn(?)
               AND r.measured_at <  hyd_dau_ngay_vn((?::date) + 1)
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

    public List<TuyenSongRow> tuyenSong(LocalDate ngay) {
        return jdbc.query(
                SQL_TUYEN_SONG,
                (rs, i) -> new TuyenSongRow(
                        rs.getLong("station_id"),
                        rs.getString("station_code"),
                        rs.getString("station_name"),
                        rs.getString("river_name"),
                        rs.getString("chainage"),
                        (Integer) rs.getObject("chainage_m"),
                        rs.getString("position_role"),
                        rs.getBoolean("active"),
                        rs.getString("type_code"),
                        rs.getString("type_name"),
                        rs.getString("unit"),
                        rs.getBigDecimal("valid_value"),
                        moc(rs, "valid_measured_at"),
                        moc(rs, "last_seen_at"),
                        rs.getBigDecimal("min_ngay"),
                        rs.getBigDecimal("max_ngay"),
                        rs.getInt("so_ban_ghi_ngay")),
                Date.valueOf(ngay));
    }

    /** @return điểm đo → công trình CHÍNH của nó; điểm đo chưa liên kết ⛔ không có mặt */
    public java.util.Map<Long, Long> congTrinhChinhCuaDiemDo() {
        java.util.Map<Long, Long> ket = new java.util.LinkedHashMap<>();
        jdbc.query(SQL_CONG_TRINH_CUA_DIEM_DO, rs -> {
            ket.put(rs.getLong("station_id"), rs.getLong("construction_id"));
        });
        return ket;
    }

    public List<TongHopKyRow> tongHopKy(LocalDate tuNgay, LocalDate denNgay, Long stationId) {
        return jdbc.query(
                SQL_TONG_HOP_KY,
                (rs, i) -> new TongHopKyRow(
                        rs.getString("station_code"),
                        rs.getString("station_name"),
                        rs.getString("river_name"),
                        rs.getString("position_role"),
                        rs.getString("type_code"),
                        rs.getString("type_name"),
                        rs.getString("unit"),
                        rs.getLong("so_ban_ghi"),
                        rs.getInt("so_ngay_co_du_lieu"),
                        rs.getBigDecimal("gia_tri_min"),
                        moc(rs, "moc_min"),
                        rs.getBigDecimal("gia_tri_max"),
                        moc(rs, "moc_max"),
                        rs.getBigDecimal("gia_tri_tb")),
                stationId,
                stationId,
                Date.valueOf(tuNgay),
                Date.valueOf(denNgay));
    }

    public long demChiTiet(long stationId, long measurementTypeId, LocalDate tuNgay, LocalDate denNgay) {
        Long so = jdbc.queryForObject(
                SQL_DEM_CHI_TIET,
                Long.class,
                stationId,
                measurementTypeId,
                Date.valueOf(tuNgay),
                Date.valueOf(denNgay));
        return so == null ? 0L : so;
    }

    public List<ChiTietSoDoRow> chiTiet(
            long stationId, long measurementTypeId, LocalDate tuNgay, LocalDate denNgay, int gioiHan, long boQua) {
        return jdbc.query(
                SQL_CHI_TIET,
                (rs, i) -> new ChiTietSoDoRow(
                        moc(rs, "measured_at"),
                        rs.getBigDecimal("reading_value"),
                        rs.getString("quality"),
                        rs.getString("quality_reason"),
                        rs.getString("source"),
                        rs.getString("note"),
                        rs.getString("review_note")),
                stationId,
                measurementTypeId,
                Date.valueOf(tuNgay),
                Date.valueOf(denNgay),
                gioiHan,
                boQua);
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
