package com.songnhue.hydro.infra;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Dựng lại một dòng {@code hydro_latest} từ {@code hydro_readings} — T32.5.
 *
 * <h2>Vì sao phải dựng LẠI, ⛔ không vá tại chỗ</h2>
 *
 * <p>Lượt UPSERT của poller chỉ biết tiến: nó so mốc mới với mốc cũ và giữ cái mới hơn. Một lượt
 * <b>duyệt</b> thì đi ngược chiều ấy — nó làm một bản ghi <i>cũ</i> đột nhiên đủ điều kiện, hoặc
 * làm một bản ghi <i>đang là mới nhất</i> biến mất. Bốn tình huống, và cả bốn đều xảy ra được:
 *
 * <ul>
 *   <li>{@code NGHI_NGO → HOP_LE} cho bản ghi mới nhất ⇒ {@code valid_*} phải <b>nhảy tới</b> nó;
 *   <li>{@code NGHI_NGO → HOP_LE} cho một bản ghi <b>cũ hơn</b> {@code valid_measured_at} hiện tại
 *       ⇒ ⛔ không được đổi gì;
 *   <li>{@code NGHI_NGO → XOA} cho bản ghi mới nhất ⇒ {@code last_*} phải <b>lùi về</b> bản ghi
 *       trước đó — đây là chiều mà UPSERT ⛔ không làm được;
 *   <li>xoá bản ghi <b>duy nhất</b> của một cặp ⇒ dòng {@code hydro_latest} phải <b>biến mất</b>,
 *       vì {@code last_seen_at} là {@code NOT NULL} và "không có gì" ⛔ không viết được thành một mốc.
 * </ul>
 *
 * <p>Viết bốn nhánh ấy bằng tay là bốn cơ hội sai, và cái sai sẽ im lặng: {@code hydro_latest} là
 * thứ widget cổng, GIS và dashboard đọc — sai ở đây hiện ra thành <i>một con số cũ trông rất bình
 * thường</i>. ⇒ Dựng lại từ nguồn sự thật, một đường, không nhánh nào phải nhớ.
 *
 * <p>⚠ Chi phí: hai câu {@code ORDER BY … LIMIT 1} trên chỉ mục
 * {@code ix_hydro_readings_station_time}, chạy khi một con người bấm nút — ⛔ không nằm trên đường
 * ingest 2 phút/lần.
 *
 * <p>⛔ Không lớp nào ở đây mở giao dịch — ranh giới transaction thuộc tầng application.
 */
@Repository
public class HydroLatestRecomputer {

    /**
     * ⭐ "Trạm còn phát tín hiệu không" — cố ý nhận <b>cả</b> bản ghi {@code NGHI_NGO}.
     *
     * <p>⚠⚠ Đây là ngoại lệ <b>có tên</b> của quy tắc 14, khai ở {@code QualityFilterGuardTest}. Lý
     * do: một trạm chỉ gửi về số nghi ngờ <b>vẫn đang phát</b>. Lọc {@code HOP_LE} ở đây là tự dựng
     * ra một trạm mất tín hiệu giả, rồi job {@code HydroSignalLossHandler} sẽ báo động về một sự cố
     * không có thật — và người trực đi kiểm một cảm biến đang chạy tốt.
     *
     * <p>⛔ Nhưng nó <b>vẫn loại {@code XOA}</b>: một bản ghi đã bị người duyệt loại bỏ ⛔ không được
     * làm bằng chứng cho bất cứ điều gì.
     */
    private static final String SQL_MOC_GAN_NHAT =
            """
            SELECT measured_at, quality, source
              FROM hydro_readings
             WHERE station_id = ? AND measurement_type_id = ? AND quality <> 'XOA'
             ORDER BY measured_at DESC, id DESC
             LIMIT 1
            """;

    /** Giá trị được phép hiển thị và đem đi so ngưỡng — quy tắc 14 ở dạng thuần nhất. */
    private static final String SQL_HOP_LE_GAN_NHAT =
            """
            SELECT measured_at, reading_value
              FROM hydro_readings
             WHERE station_id = ? AND measurement_type_id = ? AND quality = 'HOP_LE'
             ORDER BY measured_at DESC, id DESC
             LIMIT 1
            """;

    private static final String SQL_GHI =
            """
            INSERT INTO hydro_latest (
                station_id, measurement_type_id, last_seen_at, last_quality, last_source,
                valid_measured_at, valid_value, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (station_id, measurement_type_id) DO UPDATE SET
                last_seen_at      = EXCLUDED.last_seen_at,
                last_quality      = EXCLUDED.last_quality,
                last_source       = EXCLUDED.last_source,
                valid_measured_at = EXCLUDED.valid_measured_at,
                valid_value       = EXCLUDED.valid_value,
                updated_at        = now()
            """;

    private static final String SQL_XOA_DONG =
            "DELETE FROM hydro_latest WHERE station_id = ? AND measurement_type_id = ?";

    private final JdbcTemplate jdbc;

    public HydroLatestRecomputer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Dựng lại dòng {@code hydro_latest} của một cặp (điểm đo × loại chỉ số).
     *
     * <p>⚠⚠ {@code SQL_GHI} ghi đè <b>vô điều kiện</b>, khác hẳn {@code UPSERT_LATEST} của đường
     * ingest — cái kia cố ý <i>không lùi</i>, cái này <b>phải</b> lùi được. Hai câu nói hai điều
     * khác nhau về cùng một bảng, và đó là chủ ý: đường ingest thấy <i>một</i> bản ghi mới, còn
     * đường này vừa đọc <i>toàn bộ</i> lịch sử của cặp ấy nên nó là bên biết nhiều hơn.
     *
     * @return {@code true} nếu còn dòng nào ngoài {@code XOA}; {@code false} khi dòng
     *     {@code hydro_latest} vừa bị xoá vì không còn bản ghi nào dùng được
     */
    public boolean dungLai(long stationId, long measurementTypeId) {
        List<Object[]> ganNhat = jdbc.query(
                SQL_MOC_GAN_NHAT,
                (rs, i) ->
                        new Object[] {rs.getTimestamp("measured_at"), rs.getString("quality"), rs.getString("source")},
                stationId,
                measurementTypeId);

        if (ganNhat.isEmpty()) {
            // ⛔ `last_seen_at` là NOT NULL: "không còn bản ghi nào" ⛔ không viết được thành một mốc.
            //    Xoá dòng là câu trả lời đúng — trạm quay về trạng thái "chưa có dữ liệu", đúng như
            //    trước lượt ingest đầu tiên. `PollerRepository.SQL_TIN_HIEU` dùng LEFT JOIN nên nó
            //    vẫn hiện ra ở nhóm CHUA_CO_DU_LIEU, ⛔ không biến mất khỏi màn hình.
            jdbc.update(SQL_XOA_DONG, stationId, measurementTypeId);
            return false;
        }

        List<Object[]> hopLe = jdbc.query(
                SQL_HOP_LE_GAN_NHAT,
                (rs, i) -> new Object[] {rs.getTimestamp("measured_at"), rs.getBigDecimal("reading_value")},
                stationId,
                measurementTypeId);
        Object[] v = hopLe.isEmpty() ? new Object[] {null, null} : hopLe.get(0);

        jdbc.update(
                SQL_GHI,
                stationId,
                measurementTypeId,
                ganNhat.get(0)[0],
                ganNhat.get(0)[1],
                ganNhat.get(0)[2],
                v[0],
                v[1]);
        return true;
    }
}
