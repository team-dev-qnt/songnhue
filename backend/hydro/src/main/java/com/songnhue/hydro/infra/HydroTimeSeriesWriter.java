package com.songnhue.hydro.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.KhoaSoDo;
import com.songnhue.hydro.domain.NhapTayRow;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingRow;
import com.songnhue.hydro.domain.UnmappedRow;

/**
 * Ghi số đo xuống {@code hydro_readings} · {@code hydro_latest} · {@code hydro_unmapped_readings}.
 *
 * <h2>⭐ Vì sao đếm được số dòng GHI MỚI là yêu cầu, không phải tiện ích</h2>
 *
 * <p>Poller chạy 2 phút/lần trên một nguồn cập nhật 10 phút/lần ⇒ <b>4/5 lượt gọi trả về dữ liệu
 * trùng</b>. Đó là hành vi bình thường và mong muốn, không phải lỗi. Nhưng nếu chỉ đếm "đã gọi thành
 * công" thì một poller ghi 0 dòng suốt ba ngày trông y hệt một poller khoẻ mạnh — và với một nguồn
 * <b>không có API lịch sử</b>, ba ngày ấy là ba ngày mất vĩnh viễn.
 *
 * <p>Nên {@link #writeReadings} trả về <b>số dòng thật sự ghi mới</b>, và nơi gọi lấy hiệu để ra số
 * dòng bỏ qua. Hai con số ấy đi thẳng vào {@code sync_logs} thành hai cột riêng.
 *
 * <h2>⚠ Một câu INSERT nhiều dòng, không dùng {@code batchUpdate}</h2>
 *
 * <p>{@code JdbcTemplate.batchUpdate} trả về mảng số dòng theo từng câu, nhưng trình điều khiển
 * PostgreSQL được phép trả {@code Statement.SUCCESS_NO_INFO} (−2) thay cho số thật tuỳ cấu hình
 * ({@code reWriteBatchedInserts}). Cộng một mảng có thể chứa −2 là một phép đếm <b>trông như</b>
 * đang đếm. Một câu {@code INSERT … VALUES (…), (…) ON CONFLICT DO NOTHING} thì
 * {@code jdbc.update()} trả đúng tổng số dòng đã ghi, không phụ thuộc cấu hình trình điều khiển —
 * và một lượt ingest chỉ có ~28 dòng nên không có lý do hiệu năng nào để chọn cách kia.
 */
@Repository
public class HydroTimeSeriesWriter {

    private static final String INSERT_READING_DAU =
            """
            INSERT INTO hydro_readings (
                measured_at, station_id, measurement_type_id, reading_value,
                quality, quality_reason, source, raw_log_id
            ) VALUES
            """;

    /**
     * ⚠ Khoá suy ra phải khớp {@code ux_hydro_readings_diem_do_khung}, kể cả thứ tự cột.
     *
     * <p>⭐ {@code RETURNING} — T32.3. {@code jdbc.update()} đếm được số dòng ghi mới, nhưng
     * <b>không nói được dòng nào</b>, và thông báo "có số đo nghi ngờ" thì cần đúng vế thứ hai: phát
     * theo <i>những gì nhận được</i> sẽ đánh thức người trực 5 lần mỗi khung cho cùng một bản ghi
     * (xem {@link com.songnhue.hydro.domain.KhoaSoDo}). Câu này vẫn là <b>một</b> câu SQL, nên phép
     * đếm không mất gì: {@code danh sách trả về .size()} chính là con số cũ.
     */
    private static final String INSERT_READING_DUOI =
            " ON CONFLICT (station_id, measurement_type_id, measured_at) DO NOTHING"
                    + " RETURNING station_id, measured_at";

    /**
     * Đường ghi <b>nhập tay</b> — T32.7. ⛔ Một dòng một lượt, ⛔ không {@code ON CONFLICT}.
     *
     * <p>⚠⚠ Cố ý <b>không</b> có {@code ON CONFLICT DO NOTHING}, khác hẳn đường ingest. Ở đó, trùng
     * khoá là chuyện <i>bình thường</i> (poll 2' trên nguồn 10'); ở đây, trùng khoá nghĩa là người
     * dùng vừa định ghi đè một số đo đã tồn tại — và bỏ qua trong im lặng sẽ hiện ra thành <i>"lưu
     * thành công"</i> kèm một màn hình ⛔ không đổi gì. Đó đúng hình dạng luật 27.
     *
     * <p>⇒ Trùng khoá phải nổ, và nơi gọi biến nó thành {@code HYD-2007} / {@code HYD-2002} tuỳ
     * trạng thái của dòng đang chiếm chỗ.
     */
    private static final String INSERT_MANUAL =
            """
            INSERT INTO hydro_readings (
                measured_at, station_id, measurement_type_id, reading_value,
                quality, source, created_by, note
            ) VALUES (?, ?, ?, ?, 'HOP_LE', 'MANUAL', ?, ?)
            RETURNING id
            """;

    /**
     * Ô này đã có gì chưa — đọc <b>trước</b> khi ghi tay.
     *
     * <p>⚠⚠ Ngoại lệ <b>có tên</b> của quy tắc 14 (khai ở {@code QualityFilterGuardTest}): câu này
     * <b>phải</b> thấy cả dòng {@code NGHI_NGO} và {@code XOA}. Nó không trả về số liệu — nó trả lời
     * <i>"chỗ này có ai ngồi chưa, và người ấy đang ở trạng thái nào"</i>, và lọc {@code HOP_LE} ở
     * đây làm câu trả lời sai theo hướng nguy hiểm nhất: báo "trống" cho một ô đang bị chiếm, rồi
     * lượt {@code INSERT} nổ bằng một lỗi ràng buộc thô.
     *
     * <p>⭐ Nhờ nó mà {@code HYD-2002} có đường chạy thật: người trực nhập tay đúng khung mà máy vừa
     * ghi một số đáng ngờ sẽ được chỉ sang màn hình <i>Dữ liệu nghi ngờ</i>, thay vì nhận một thông
     * báo "trùng dữ liệu" chung chung rồi không biết làm gì tiếp.
     */
    private static final String SQL_O_DA_CO_GI =
            """
            SELECT quality FROM hydro_readings
             WHERE station_id = ? AND measurement_type_id = ? AND measured_at = ?
            """;

    private static final String INSERT_UNMAPPED_DAU =
            """
            INSERT INTO hydro_unmapped_readings (
                api_code, api_source_id, measured_at, raw_value, raw_unit, raw_log_id
            ) VALUES
            """;

    private static final String INSERT_UNMAPPED_DUOI = " ON CONFLICT (api_code, measured_at) DO NOTHING";

    /**
     * UPSERT {@code hydro_latest} — bốn cột, hai luật <b>khác nhau</b> về "mới hơn".
     *
     * <p>⚠ Hai vế cập nhật độc lập, và đó là toàn bộ giá trị của bảng này:
     *
     * <ul>
     *   <li>{@code last_seen_at} tiến theo <b>mọi</b> bản ghi — nó trả lời "trạm còn phát tín hiệu
     *       không". Một trạm chỉ trả số nghi ngờ <b>vẫn đang phát</b>.
     *   <li>{@code valid_*} chỉ tiến theo bản ghi <b>HỢP LỆ</b> — nó trả lời "hiện mực nước bao
     *       nhiêu". Đây là chỗ quy tắc 14 được ép ở tầng dữ liệu chứ không ở lời dặn.
     * </ul>
     *
     * <p>⛔ Và cả hai vế đều <b>không lùi</b>: một lượt ingest muộn mang về bản ghi cũ hơn (thử lại
     * sau lỗi mạng, hoặc nhập tay bù dữ liệu quá khứ) ⛔ không được kéo mốc hiện tại lùi lại. So
     * bằng {@code >} chứ không ghi đè vô điều kiện.
     */
    private static final String UPSERT_LATEST =
            """
            INSERT INTO hydro_latest (
                station_id, measurement_type_id, last_seen_at, last_quality, last_source,
                valid_measured_at, valid_value, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, now())
            ON CONFLICT (station_id, measurement_type_id) DO UPDATE SET
                last_seen_at = GREATEST(hydro_latest.last_seen_at, EXCLUDED.last_seen_at),
                last_quality = CASE WHEN EXCLUDED.last_seen_at > hydro_latest.last_seen_at
                                    THEN EXCLUDED.last_quality ELSE hydro_latest.last_quality END,
                last_source  = CASE WHEN EXCLUDED.last_seen_at > hydro_latest.last_seen_at
                                    THEN EXCLUDED.last_source ELSE hydro_latest.last_source END,
                valid_measured_at = CASE
                    WHEN EXCLUDED.valid_measured_at IS NOT NULL
                     AND (hydro_latest.valid_measured_at IS NULL
                          OR EXCLUDED.valid_measured_at > hydro_latest.valid_measured_at)
                    THEN EXCLUDED.valid_measured_at ELSE hydro_latest.valid_measured_at END,
                valid_value = CASE
                    WHEN EXCLUDED.valid_measured_at IS NOT NULL
                     AND (hydro_latest.valid_measured_at IS NULL
                          OR EXCLUDED.valid_measured_at > hydro_latest.valid_measured_at)
                    THEN EXCLUDED.valid_value ELSE hydro_latest.valid_value END,
                updated_at = now()
            """;

    private final JdbcTemplate jdbc;

    public HydroTimeSeriesWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return khoá của những dòng <b>thật sự ghi mới</b>. Hiệu giữa {@code rows.size()} và kích
     *     thước danh sách là số dòng trùng khoá — ⚠ trùng khoá là chuyện bình thường, xem javadoc
     *     của lớp
     */
    public List<KhoaSoDo> writeReadings(List<ReadingRow> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(INSERT_READING_DAU);
        List<Object> args = new ArrayList<>(rows.size() * 8);
        for (int i = 0; i < rows.size(); i++) {
            sql.append(i == 0 ? " " : ", ").append("(?, ?, ?, ?, ?, ?, ?, ?)");
            ReadingRow r = rows.get(i);
            args.add(Timestamp.from(r.measuredAt()));
            args.add(r.stationId());
            args.add(r.measurementTypeId());
            args.add(r.value());
            args.add(r.quality().name());
            args.add(r.qualityReason());
            args.add(r.source().name());
            args.add(r.rawLogId());
        }
        sql.append(INSERT_READING_DUOI);
        return jdbc.query(
                sql.toString(),
                (rs, i) -> new KhoaSoDo(
                        rs.getLong("station_id"), rs.getTimestamp("measured_at").toInstant()),
                args.toArray());
    }

    /**
     * Trạng thái của dòng đang chiếm ô {@code (điểm đo × loại chỉ số × mốc đo)} — xem
     * {@link #SQL_O_DA_CO_GI}.
     *
     * @return rỗng nghĩa là ô còn trống
     */
    public Optional<ReadingQuality> chatLuongTaiO(long stationId, long measurementTypeId, Instant measuredAt) {
        return jdbc
                .queryForList(SQL_O_DA_CO_GI, String.class, stationId, measurementTypeId, Timestamp.from(measuredAt))
                .stream()
                .findFirst()
                .map(ReadingQuality::valueOf);
    }

    /**
     * Ghi một số đo nhập tay — T32.7.
     *
     * @return khoá của dòng vừa ghi
     * @throws org.springframework.dao.DuplicateKeyException khi ô đã bị chiếm; nơi gọi phải kiểm
     *     trước bằng {@link #chatLuongTaiO} để nói được <b>vì sao</b>, ⚠ nhưng vẫn phải bắt ngoại lệ
     *     này: giữa lượt kiểm và lượt ghi có một khe hở, và poller chạy 2 phút/lần
     */
    public long writeManual(NhapTayRow row) {
        Long id = jdbc.queryForObject(
                INSERT_MANUAL,
                Long.class,
                Timestamp.from(row.measuredAt()),
                row.stationId(),
                row.measurementTypeId(),
                row.value(),
                row.createdBy(),
                row.note());
        return id == null ? 0L : id;
    }

    /** @return số dòng ghi mới; trùng {@code (api_code, measured_at)} thì bỏ qua trong im lặng */
    public int writeUnmapped(List<UnmappedRow> rows) {
        if (rows.isEmpty()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder(INSERT_UNMAPPED_DAU);
        List<Object> args = new ArrayList<>(rows.size() * 6);
        for (int i = 0; i < rows.size(); i++) {
            sql.append(i == 0 ? " " : ", ").append("(?, ?, ?, ?, ?, ?)");
            UnmappedRow r = rows.get(i);
            args.add(r.apiCode());
            args.add(r.apiSourceId());
            args.add(Timestamp.from(r.measuredAt()));
            args.add(r.rawValue());
            args.add(r.rawUnit());
            args.add(r.rawLogId());
        }
        sql.append(INSERT_UNMAPPED_DUOI);
        return jdbc.update(sql.toString(), args.toArray());
    }

    /**
     * Cập nhật bảng "mực nước hiện tại".
     *
     * <p>⚠ Gọi cho <b>mọi</b> dòng nhận được, kể cả dòng vừa bị {@code ON CONFLICT DO NOTHING} bỏ
     * qua ở {@link #writeReadings}: hai bảng trả lời hai câu hỏi khác nhau. Một dòng trùng vẫn là
     * bằng chứng "trạm còn phát tín hiệu lúc này", và nếu chỉ upsert cho dòng ghi mới thì một trạm
     * gửi lại đúng giá trị cũ sẽ bị báo mất tín hiệu.
     */
    public void upsertLatest(List<ReadingRow> rows) {
        for (ReadingRow r : rows) {
            boolean hopLe = r.quality() == ReadingQuality.HOP_LE;
            jdbc.update(
                    UPSERT_LATEST,
                    r.stationId(),
                    r.measurementTypeId(),
                    Timestamp.from(r.measuredAt()),
                    r.quality().name(),
                    r.source().name(),
                    hopLe ? Timestamp.from(r.measuredAt()) : null,
                    hopLe ? r.value() : null);
        }
    }
}
