package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn của <b>biểu đồ</b> — T35.4, chuỗi thời gian đầu tiên của hệ.
 *
 * <h2>⛔⛔ Đọc {@code hydro_readings} ở đây là NGOẠI LỆ CÓ TÊN của quy tắc 8</h2>
 *
 * <p>{@code ReportReadsAggregateTest} canh <i>"báo cáo đọc bảng tổng hợp, ⛔ không scan bảng thô"</i>
 * và javadoc của nó ghi sẵn: <i>"⬜ Dashboard (WS-35) sẽ có kho riêng — thêm nó vào
 * {@code TEP_BAO_CAO} <b>cùng lúc</b> với tệp ấy, ⛔ không để sau."</i> Tệp này là tệp ấy, và nó
 * được thêm vào bộ canh trong <b>cùng commit</b>.
 *
 * <p>Vì sao ngoại lệ này chính đáng — và vì sao nó ⛔ không mở đường cho câu thứ hai:
 *
 * <ul>
 *   <li>Một đường cong 24 giờ ⛔ <b>không vẽ được</b> từ bảng tổng hợp <i>theo ngày</i>: bảng ấy có
 *       đúng một hàng cho cả ngày hôm nay. Câu hỏi <i>"nước lên từ lúc mấy giờ"</i> — thứ người ta
 *       mở biểu đồ ra để hỏi — chỉ có bảng gốc trả lời được.
 *   <li>Chi phí <b>đã chặn</b>: một điểm đo × một loại chỉ số × 24 giờ = <b>≤ 144 dòng</b>. Ràng
 *       buộc ấy nằm ở SQL ({@code LIMIT}) chứ ⛔ không ở lời dặn tầng trên — tầng trên đổi chủ, câu
 *       SQL thì không.
 *   <li>⛔ Biểu nhiều ngày <b>chưa có ở đây</b>, và ngày nó có thì nó đọc {@code hydro_agg_daily}.
 *       ⛔ Đừng nới {@link #SQL_CHUOI_24H} bằng cách cho khoảng ngày rộng ra — đó đúng là cách một
 *       ngoại lệ hợp lệ trở thành một lượt quét 82 nghìn dòng.
 * </ul>
 *
 * <h2>⚠ Lọc {@code quality = 'HOP_LE'} là bắt buộc (quy tắc 14)</h2>
 *
 * <p>Bản ghi {@code NGHI_NGO} nằm <b>chung bảng</b>. Vẽ chúng lên đường cong là công bố một số đo
 * mà chính hệ thống ⛔ không tin — và trên biểu đồ nó ⛔ không phân biệt được với số đo tốt.
 * {@code QualityFilterGuardTest} soi câu dưới đây.
 */
@Repository
public class HydroChartRepository {

    /**
     * ⚠ Tên hằng này xuất hiện trong {@code ReportReadsAggregateTest.NGOAI_LE} — đổi tên ở đây thì
     * phải đổi ở đó, nếu không bộ canh sẽ báo một vi phạm không có thật.
     */
    private static final String SQL_CHUOI_24H =
            """
            SELECT r.measured_at, r.reading_value
              FROM hydro_readings r
             WHERE r.station_id = ?
               AND r.measurement_type_id = ?
               AND r.measured_at >= ?
               AND r.measured_at <= ?
               AND r.quality = 'HOP_LE'
             ORDER BY r.measured_at ASC
             LIMIT 200
            """;

    private final JdbcTemplate jdbc;

    public HydroChartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param moc mốc <b>nguồn đo</b> ({@code measured_at}), ⛔ không phải mốc ingest
     */
    public record DiemChuoi(Instant moc, BigDecimal giaTri) {}

    /**
     * Chuỗi số đo hợp lệ trong một cửa sổ thời gian.
     *
     * <p>⛔ Trả <b>đúng những gì có</b> — ⛔ không nội suy, ⛔ không điền 0 vào khoảng trống. Khoảng
     * trống là <b>thông tin</b>: nó nghĩa là trạm ⛔ không gửi số về, và {@code optionDuong} đặt
     * {@code connectNulls: false} chính để khoảng ấy nhìn thấy được. Nối liền qua chỗ mất tín hiệu
     * là vẽ ra một đoạn số liệu chưa từng được đo (quy tắc 16).
     */
    public List<DiemChuoi> chuoi24h(long stationId, long measurementTypeId, Instant tu, Instant den) {
        return jdbc.query(
                SQL_CHUOI_24H,
                (rs, i) -> new DiemChuoi(
                        moc(rs.getObject("measured_at", OffsetDateTime.class)), rs.getBigDecimal("reading_value")),
                stationId,
                measurementTypeId,
                java.sql.Timestamp.from(tu),
                java.sql.Timestamp.from(den));
    }

    /**
     * ⚠⚠ {@code rs.getObject(col, Instant.class)} <b>ném</b> với cột {@code timestamptz} — trình
     * điều khiển PostgreSQL ⛔ không khai phép đổi ấy, dù nó đổi được sang {@link OffsetDateTime}.
     * Cùng bẫy đã trả giá ở {@code HydroReportRepository}, và ngoại lệ ấy bị dịch thành
     * {@code SYS-0005}/409 nên triệu chứng ⛔ không hề trỏ vào dòng mã này.
     */
    private static Instant moc(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }
}
