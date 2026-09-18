package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *   <li>Chi phí <b>đã chặn</b>: một điểm đo × một loại chỉ số × 24 giờ. Ràng buộc ấy nằm ở SQL
 *       ({@code LIMIT}) chứ ⛔ không ở lời dặn tầng trên — tầng trên đổi chủ, câu SQL thì không.
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
 *
 * <h2>⛔⛔ Một chú thích ở đây đã KHẲNG ĐỊNH SAI suốt từ WS-35 (T43.13)</h2>
 *
 * <p>Bản trước viết: <i>"⛔ Trả đúng những gì có — khoảng trống là thông tin, và {@code optionDuong}
 * đặt {@code connectNulls: false} chính để khoảng ấy nhìn thấy được."</i> Vế đầu đúng, vế sau
 * <b>chưa bao giờ đúng</b>: tầng vẽ dựng trục X <i>từ chính mảng trả về</i>, nên ⛔ không có
 * {@code null} nào để {@code connectNulls} tác dụng lên — hai mốc cách nhau ba giờ vẽ ra liền kề
 * nhau. Nay lưới mốc do {@code CheDoXemLuoi} dựng ở tầng service và hàm này vẫn chỉ trả
 * <b>những gì có</b>; việc ghép vào lưới là của {@code HydroChartService}.
 *
 * <p>⇒ Bài học ghi lại vì nó lặp: <b>một chú thích mô tả hành vi của một tệp KHÁC là một khẳng định
 * ⛔ không ai kiểm.</b> Cùng hình dạng §10.65, §11.14, T11.6-a.
 */
@Repository
public class HydroChartRepository {

    private static final Logger log = LoggerFactory.getLogger(HydroChartRepository.class);

    /**
     * Trần số hàng — <b>gấp bốn</b> số mốc của một cửa sổ 24 giờ (144).
     *
     * <p>⚠ Headroom ấy ⛔ không phải sự thận trọng mơ hồ: nguồn ghi mỗi mốc một hàng, nhưng một
     * bản ghi <b>nhập tay</b> hoặc một lượt sửa chất lượng có thể thêm hàng vào cùng mốc. Gấp bốn
     * là đủ rộng cho mọi trường hợp thật mà vẫn chặn một lượt quét vô hạn.
     */
    static final int TRAN_HANG = 576;

    /**
     * ⚠ Tên hằng này xuất hiện trong {@code ReportReadsAggregateTest.NGOAI_LE} — đổi tên ở đây thì
     * phải đổi ở đó, nếu không bộ canh sẽ báo một vi phạm không có thật.
     *
     * <p>⛔⛔ {@code ORDER BY … DESC} là <b>có chủ đích</b>, ⛔ đừng "sửa" thành {@code ASC} cho
     * thuận mắt. Nếu trần {@link #TRAN_HANG} có ngày bị chạm thì thứ bị cắt phải là phần <b>cũ
     * nhất</b> — người trực ban mở biểu đồ để xem nước đang lên hay xuống <i>bây giờ</i>. Với
     * {@code ASC}, cắt cụt lấy mất đúng phần mới nhất, và triệu chứng là <b>một đường cong dừng
     * giữa chừng</b> — thứ trông y hệt một trạm vừa mất tín hiệu. Đây đúng là hình dạng §11.18
     * (<i>trần 5.000 dòng cắt cụt im lặng rồi đếm SAU khi cắt</i>).
     *
     * <p>⚠ Cận trên {@code <} (⛔ không phải {@code <=}) vì tầng gọi truyền <b>mốc lưới cuối + một
     * bước</b>: một số đo nhập tay lúc 10:53 thuộc ô 10:50, và với {@code <=} mốc-cuối nó rơi ra
     * ngoài cửa sổ tra rồi biến mất khỏi biểu đồ.
     */
    private static final String SQL_CHUOI_24H =
            """
            SELECT r.measured_at, r.reading_value
              FROM hydro_readings r
             WHERE r.station_id = ?
               AND r.measurement_type_id = ?
               AND r.measured_at >= ?
               AND r.measured_at <  ?
               AND r.quality = 'HOP_LE'
             ORDER BY r.measured_at DESC
             LIMIT %d
            """
                    .formatted(TRAN_HANG);

    private final JdbcTemplate jdbc;

    public HydroChartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param moc mốc <b>nguồn đo</b> ({@code measured_at}), ⛔ không phải mốc ingest
     */
    public record DiemChuoi(Instant moc, BigDecimal giaTri) {}

    /**
     * Chuỗi số đo hợp lệ trong một cửa sổ thời gian, <b>mới nhất trước</b>.
     *
     * <p>⛔ Trả <b>đúng những gì có</b> — ⛔ không nội suy, ⛔ không điền 0 vào khoảng trống. Khoảng
     * trống là <b>thông tin</b>: nó nghĩa là trạm ⛔ không gửi số về. Việc biến khoảng trống ấy
     * thành một ô {@code null} trên trục là của {@code HydroChartService}, ⛔ không phải của tầng
     * này — xem javadoc lớp về chú thích cũ đã khẳng định sai chỗ đó.
     *
     * @param denLoaiTru cận trên <b>không bao gồm</b> — tầng gọi truyền mốc lưới cuối cộng một bước
     */
    public List<DiemChuoi> chuoi24h(long stationId, long measurementTypeId, Instant tu, Instant denLoaiTru) {
        List<DiemChuoi> hang = jdbc.query(
                SQL_CHUOI_24H,
                (rs, i) -> new DiemChuoi(
                        moc(rs.getObject("measured_at", OffsetDateTime.class)), rs.getBigDecimal("reading_value")),
                stationId,
                measurementTypeId,
                java.sql.Timestamp.from(tu),
                java.sql.Timestamp.from(denLoaiTru));

        // ⛔ Chạm trần thì phải NÓI RA. Một lượt cắt cụt im lặng là khuyết tật §11.18 đúng nguyên
        //    văn: hệ thống trả về một câu trả lời trông hoàn chỉnh cho một câu hỏi nó chỉ đọc được
        //    một phần. Ở đây hậu quả nhẹ hơn (mất phần CŨ của một biểu đồ 24h) nhưng con số vẫn
        //    phải nhìn thấy được, vì nó là dấu hiệu điểm đo đang có hàng trùng mốc bất thường.
        if (hang.size() >= TRAN_HANG) {
            log.warn(
                    "Biểu đồ 24h của điểm đo id={} chỉ số id={} chạm trần {} hàng — phần CŨ nhất của cửa sổ đã bị "
                            + "cắt. Bình thường một cửa sổ 24h có ≤144 hàng; con số này nghĩa là có nhiều hàng cùng "
                            + "một mốc. Kiểm tra hydro_readings xem có lượt ingest lặp không.",
                    stationId,
                    measurementTypeId,
                    TRAN_HANG);
        }
        return hang;
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
