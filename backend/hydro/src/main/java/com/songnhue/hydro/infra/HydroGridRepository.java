package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn của <b>bảng lưới mực nước</b> — WS-43 / T43.4, câu đọc {@code hydro_readings} cho
 * <b>nhiều điểm đo</b> đầu tiên của hệ (spec-description.md §5.2, §6.1.2).
 *
 * <h2>⛔⛔ Vì sao phải có câu này — 10/10 câu đang có đều ràng một điểm đo</h2>
 *
 * <p>Đo 09/09/2026: mọi lời gọi đọc {@code hydro_readings} trong kho đều mang
 * {@code WHERE station_id = ?}. Bảng §6.1.2 hỏi một câu khác hẳn — <i>"19 điểm đo này, tại 12 mốc
 * gần nhất, mỗi ô là bao nhiêu"</i> — và gọi câu cũ 19 lần là 19 lượt quét chỉ mục thay vì một.
 *
 * <h2>⛔ Hai câu, ⛔ không phải một câu LEFT JOIN</h2>
 *
 * <p>{@link #danhMucCongTrinh()} đọc <b>chỉ</b> {@code stations}; {@link #soDoTrongKhung} đọc
 * {@code hydro_readings}. Gộp lại bằng {@code LEFT JOIN} thì siêu dữ liệu công trình bị nhân bản
 * theo số mốc đo (19 × 144 = 2.736 lần lặp cùng một chuỗi tên), và — nặng hơn — <b>cả câu</b> sẽ
 * rơi vào phạm vi hai bộ canh chất lượng, khiến một câu thuần danh mục phải mang một ngoại lệ nó
 * ⛔ không cần.
 *
 * <h2>⛔⛔ {@link #SQL_SO_DO_TRONG_KHUNG} là NGOẠI LỆ CÓ TÊN của quy tắc 8 VÀ quy tắc 14</h2>
 *
 * <p><b>Quy tắc 8</b> (báo cáo đọc bảng tổng hợp, ⛔ không scan bảng thô) — ngoại lệ chính đáng vì
 * cùng lý do {@link HydroChartRepository} đã được cấp: {@code hydro_agg_daily} có <b>một hàng cho
 * cả ngày</b>, còn §6.1.2 hỏi giá trị <b>tại từng mốc 10 phút</b>. Chi phí chặn ở SQL bằng
 * {@code LIMIT} truyền vào, ⛔ không bằng lời dặn ở tầng trên.
 *
 * <p><b>Quy tắc 14</b> (mọi truy vấn báo cáo phải lọc {@code quality = 'HOP_LE'}) — câu này
 * <b>cố ý ⛔ KHÔNG lọc</b>, và đó là <i>thi hành</i> đặc tả chứ ⛔ không phải lách nó:
 * spec §6.1.2 ghi <i>"Ô có {@code quality = SUSPECT} in màu vàng kèm dấu ⚠ và tooltip lý do"</i>.
 * Lọc đi thì ô nghi ngờ biến thành ô <b>trống</b>, và trống ⛔ không phân biệt được với
 * <i>"trạm ⛔ không gửi số"</i> — đúng thứ quy tắc 16 cấm.
 *
 * <p>⚠ Tiền lệ đã chạy thật: {@code HydroReportRepository#SQL_CHI_TIET} (BC-12) đổi đúng đánh đổi
 * này — bỏ lọc, đổi lấy một <b>cột Chất lượng</b> đi kèm từng ô. Câu dưới đây trả
 * {@code quality} ra ngoài <b>cùng</b> giá trị, nên tầng trên ⛔ không thể vô tình quên nó.
 *
 * <p>⛔ Điều này ⛔ KHÔNG mở đường cho câu thứ hai bỏ lọc. Ranh giới: <b>bảng trình bày từng ô
 * kèm nhãn chất lượng</b> thì được; <b>mọi phép cộng/trung bình/so ngưỡng</b> thì ⛔ không —
 * một số trung bình ⛔ không mang theo được nhãn "trong này có số nghi ngờ".
 */
@Repository
public class HydroGridRepository {

    /**
     * ⛔ Trần cứng cho một lượt đọc, áp <b>sau</b> trần của tầng gọi.
     *
     * <p>19 điểm đo × 144 mốc/ngày = 2.736 hàng cho một ngày trọn vẹn. Trần này để chỗ cho danh mục
     * lớn lên (§2.7 của {@code phase3-plan.md}: còn ~9 mã lạ đang chờ vào danh mục) mà vẫn chặn một
     * lượt gọi hỏi nhiều ngày. ⛔ Đừng nới nó để phục vụ biểu nhiều ngày — biểu nhiều ngày đọc
     * {@code hydro_agg_daily}, đó là một câu hỏi khác và một bảng khác.
     */
    public static final int TRAN_HANG = 20_000;

    /**
     * Danh mục công trình đã gộp — ⛔ đọc <b>chỉ</b> {@code stations}, ⛔ không chạm bảng số đo.
     *
     * <p><b>Thứ tự</b> là phần nghiệp vụ, ⛔ không phải chi tiết kỹ thuật, nên nó nằm trong SQL:
     *
     * <ol>
     *   <li>{@code river_name} — nhóm tuyến sông của §5.2. {@code NULLS LAST} để nhóm
     *       "Chưa phân tuyến" (G8 chưa về) rơi xuống cuối thay vì lên đầu bảng;
     *   <li>{@code display_order} — thứ tự Công ty cấp. ⛔ Hôm nay <b>0 ở mọi điểm đo</b>, nên vế
     *       này ⛔ chưa quyết định gì và vế sau mới là vế thật;
     *   <li>{@code chainage_m} — lý trình đã quy ra mét (cột STORED). Đây là <b>số đo thật</b>,
     *       ⛔ không phải thứ tự bịa, nên nó là thứ giữ cho bảng đúng trật tự thượng→hạ nguồn khi
     *       {@code display_order} còn trống. ⭐ Đo 09/09: cho ra đúng trật tự của "Biểu tổng hợp"
     *       Công ty đang dùng (K0+390 → K18+100 → K43+750 → K63+405 → K72+506);
     *   <li>{@code structure_code}, rồi {@code position_role} — chốt chặn cho thứ tự ổn định giữa
     *       hai lượt gọi. ⛔ Thiếu vế này thì hai lượt F5 có thể cho hai thứ tự khác nhau và
     *       ⛔ không ai tìm ra vì sao.
     * </ol>
     *
     * <p>⚠ {@code position_role} sắp bằng một biểu thức CASE chứ ⛔ không theo bảng chữ cái:
     * {@code HA_LUU} đứng trước {@code THUONG_LUU} nếu sắp theo chữ, mà §6.1.2 đòi
     * <b>Thượng lưu → Hạ lưu → Chênh lệch</b>.
     */
    private static final String SQL_DANH_MUC =
            """
            SELECT s.id,
                   s.code,
                   s.name,
                   s.structure_code,
                   s.structure_name,
                   s.position_role,
                   s.river_name,
                   s.chainage,
                   s.is_main_axis,
                   s.active,
                   mt.id   AS measurement_type_id,
                   mt.unit AS unit
              FROM stations s
              JOIN station_measurement_types smt ON smt.station_id = s.id
              JOIN measurement_types mt ON mt.id = smt.measurement_type_id
             WHERE s.deleted_at IS NULL
               AND mt.deleted_at IS NULL
               AND mt.code = ?
             ORDER BY s.river_name NULLS LAST,
                      s.display_order,
                      s.chainage_m NULLS LAST,
                      s.structure_code,
                      CASE s.position_role
                           WHEN 'THUONG_LUU' THEN 1
                           WHEN 'HA_LUU'     THEN 2
                           WHEN 'BE_HUT'     THEN 3
                           WHEN 'MN_SONG'    THEN 4
                           WHEN 'MUA'        THEN 5
                           ELSE 9
                      END,
                      s.code
            """;

    /**
     * ⚠ Tên hằng này xuất hiện trong {@code ReportReadsAggregateTest.NGOAI_LE} <b>và</b>
     * {@code QualityFilterGuardTest.NGOAI_LE} — đổi tên ở đây thì phải đổi ở cả hai, nếu không bộ
     * canh sẽ báo một ngoại lệ mồ côi (cả hai bộ canh đều có bài bắt đúng chuyện đó).
     *
     * <p>⛔ {@code quality} nằm trong danh sách chọn <b>một cách bắt buộc</b>: nó là thứ đổi lấy
     * quyền ⛔ không lọc. Bỏ nó khỏi câu SELECT là biến một ngoại lệ có kiểm soát thành đúng cái
     * lỗi quy tắc 14 sinh ra để chặn.
     */
    private static final String SQL_SO_DO_TRONG_KHUNG =
            """
            SELECT r.station_id,
                   r.measured_at,
                   r.reading_value,
                   r.quality,
                   r.quality_reason
              FROM hydro_readings r
             WHERE r.station_id = ANY (?)
               AND r.measurement_type_id = ?
               AND r.measured_at >= ?
               AND r.measured_at <= ?
             ORDER BY r.station_id, r.measured_at
             LIMIT ?
            """;

    private final JdbcTemplate jdbc;

    public HydroGridRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Một điểm đo kèm khoá gộp công trình của nó.
     *
     * @param structureCode {@code null} khi chưa biết điểm đo thuộc công trình nào — tầng trên cho
     *     nó đứng riêng một nhóm, ⛔ không loại khỏi bảng
     */
    public record DiemDoLuoi(
            long id,
            String code,
            String name,
            String structureCode,
            String structureName,
            String positionRole,
            String riverName,
            String chainage,
            boolean mainAxis,
            boolean active,
            long measurementTypeId,
            String unit) {}

    /**
     * Một số đo thô kèm <b>nhãn chất lượng của chính nó</b>.
     *
     * @param quality {@code HOP_LE} | {@code NGHI_NGO} — ⛔ ⛔ không bao giờ {@code null}, và tầng
     *     trên ⛔ không được bỏ qua nó (xem javadoc lớp)
     */
    public record SoDoLuoi(long stationId, Instant moc, BigDecimal giaTri, String quality, String lyDoNghiNgo) {}

    /** Danh mục điểm đo của một loại chỉ số, đã sắp đúng thứ tự trình bày. */
    public List<DiemDoLuoi> danhMucCongTrinh(String maLoaiChiSo) {
        return jdbc.query(
                SQL_DANH_MUC,
                (rs, i) -> new DiemDoLuoi(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("structure_code"),
                        rs.getString("structure_name"),
                        rs.getString("position_role"),
                        rs.getString("river_name"),
                        rs.getString("chainage"),
                        rs.getBoolean("is_main_axis"),
                        rs.getBoolean("active"),
                        rs.getLong("measurement_type_id"),
                        rs.getString("unit")),
                maLoaiChiSo);
    }

    /**
     * Số đo của <b>nhiều</b> điểm đo trong một cửa sổ thời gian.
     *
     * <p>⛔ Trả <b>đúng những gì có</b> — ⛔ không nội suy, ⛔ không điền 0. Mốc thiếu là
     * <b>thông tin</b>, và tầng trên trải lưới rồi để ô ấy {@code null} để bảng hiện ô trống còn
     * biểu đồ <b>ngắt</b> đường (spec §7.1). Đây là chỗ chữa khuyết tật T43.13: trước đây trục thời
     * gian dựng từ chính mảng điểm trả về nên mốc thiếu bị <b>nuốt</b> thay vì ngắt.
     *
     * @param stationIds ⛔ rỗng thì trả danh sách rỗng — ⛔ không gọi CSDL với mảng rỗng
     * @param tranHang trần hàng của lượt gọi; luôn bị {@link #TRAN_HANG} chặn thêm một lần nữa
     */
    public List<SoDoLuoi> soDoTrongKhung(
            List<Long> stationIds, long measurementTypeId, Instant tu, Instant den, int tranHang) {
        if (stationIds == null || stationIds.isEmpty()) {
            return List.of();
        }
        int tran = Math.max(1, Math.min(tranHang, TRAN_HANG));
        return jdbc.query(
                SQL_SO_DO_TRONG_KHUNG,
                ps -> {
                    ps.setArray(1, ps.getConnection().createArrayOf("bigint", stationIds.toArray()));
                    ps.setLong(2, measurementTypeId);
                    ps.setTimestamp(3, java.sql.Timestamp.from(tu));
                    ps.setTimestamp(4, java.sql.Timestamp.from(den));
                    ps.setInt(5, tran);
                },
                (rs, i) -> new SoDoLuoi(
                        rs.getLong("station_id"),
                        moc(rs.getObject("measured_at", OffsetDateTime.class)),
                        rs.getBigDecimal("reading_value"),
                        rs.getString("quality"),
                        rs.getString("quality_reason")));
    }

    /**
     * Ngưỡng báo động của từng điểm đo — spec §5.3, để tô nền ô theo dải giá trị.
     *
     * <p>⛔ Đọc {@code alert_rules} + {@code alert_levels}, ⛔ <b>không</b> đọc
     * {@code alert_events}. Hai thứ trả lời hai câu khác nhau và {@code StationMapRepository} dùng
     * cái thứ hai:
     *
     * <ul>
     *   <li>{@code alert_events} = <i>"trạm này ĐANG có cảnh báo mở nào ⛔ không"</i> — một trạng
     *       thái của <b>trạm</b>, đúng cho chấm màu trên bản đồ;
     *   <li>{@code alert_rules} = <i>"giá trị NÀY rơi vào dải nào"</i> — một thuộc tính của
     *       <b>từng ô</b>, và §5.3 tô màu theo ô chứ ⛔ không theo trạm. Một bảng 12 cột có thể có
     *       ba ô vàng và chín ô trắng trên cùng một dòng.
     * </ul>
     *
     * <p>⛔ Chỉ nhận {@code condition_type = 'GT'}: §5.3 là một thang <b>vượt lên trên</b>
     * (BĐ1 ≤ giá trị < BĐ2 …). {@code LT} / {@code OUT_OF_RANGE} / {@code RATE_OF_CHANGE} là những
     * luật cảnh báo khác và ⛔ không xếp thành thang được — gộp chúng vào đây là tô màu một ô theo
     * một luật ⛔ không nói gì về độ cao mực nước.
     *
     * <p>⚠ Trả về <b>rỗng</b> hôm nay: {@code alert_levels} cố ý 0 hàng cho tới khi Công ty đưa bộ
     * mức thật (G9-a). Đó là một trạng thái <b>hợp lệ</b> — §5.3 ghi rõ <i>"điểm đo chưa khai báo
     * ngưỡng: ⛔ không tô màu, ⛔ không dùng ngưỡng của điểm khác"</i>.
     */
    private static final String SQL_NGUONG =
            """
            SELECT r.station_id,
                   r.threshold_value,
                   al.color_token,
                   al.name AS ten_muc,
                   al.severity_rank
              FROM alert_rules r
              JOIN alert_levels al ON al.id = r.alert_level_id
             WHERE r.deleted_at IS NULL
               AND al.deleted_at IS NULL
               AND r.active = TRUE
               AND al.active = TRUE
               AND r.condition_type = 'GT'
               AND r.measurement_type_id = ?
             ORDER BY r.station_id, al.severity_rank
            """;

    /**
     * Một bậc của thang ngưỡng.
     *
     * @param khoaMau {@code color_token} — ⛔ một KHOÁ trong design-tokens, ⛔ không phải mã hex.
     *     {@code ck_alert_levels_color_token} chặn hex ở tầng CSDL (nợ T25.23)
     */
    public record BacNguong(long stationId, BigDecimal nguong, String khoaMau, String tenMuc, int mucDo) {}

    /** Thang ngưỡng của mọi điểm đo, đã sắp theo {@code severity_rank} tăng dần trong từng trạm. */
    public List<BacNguong> nguongTheoDiemDo(long measurementTypeId) {
        return jdbc.query(
                SQL_NGUONG,
                (rs, i) -> new BacNguong(
                        rs.getLong("station_id"),
                        rs.getBigDecimal("threshold_value"),
                        rs.getString("color_token"),
                        rs.getString("ten_muc"),
                        rs.getInt("severity_rank")),
                measurementTypeId);
    }

    /**
     * Hai mốc mà khối {@code meta} của spec §10 cần — đọc {@code hydro_latest}, ⛔ không phải
     * {@code hydro_readings}, nên câu này ⛔ không nằm trong phạm vi hai bộ canh chất lượng.
     *
     * <h2>⛔⛔ Vì sao HAI mốc chứ ⛔ không phải một</h2>
     *
     * <p>Chúng trả lời hai câu <b>khác nhau</b>, và gộp lại là đúng chỗ khuyết tật <b>T43.9</b>:
     *
     * <ul>
     *   <li>{@code lanLayCuoi} = {@code MAX(updated_at)} — <i>"lần cuối hệ thống lấy được dữ
     *       liệu"</i>. Đây là thứ dòng <i>"Cập nhật lúc"</i> của §5.1 phải in, thay cho
     *       {@code Instant.now()} mà cổng đang dùng (nguồn chết ba ngày, cổng vẫn nhảy số mới mỗi
     *       lượt F5);
     *   <li>{@code mocDoGanNhat} = {@code MAX(last_seen_at)} — <i>"số liệu mới nhất được ĐO lúc
     *       nào"</i>. Đây mới là mốc quyết định {@code source_status}: nguồn có thể trả về đều đặn
     *       <b>cùng một số đo cũ</b>, khi ấy {@code updated_at} vẫn nhích còn {@code last_seen_at}
     *       đứng im — và chỉ mốc thứ hai nhìn ra điều đó.
     * </ul>
     *
     * <p>⚠ Cả hai {@code null} khi bảng rỗng — đó là trạng thái <b>DOWN</b> hợp lệ (chưa lượt
     * polling nào thành công), ⛔ không phải lỗi.
     */
    public record MocDongBo(Instant lanLayCuoi, Instant mocDoGanNhat) {}

    public MocDongBo mocDongBo() {
        return jdbc.queryForObject(
                "SELECT max(updated_at) AS lan_lay_cuoi, max(last_seen_at) AS moc_do FROM hydro_latest",
                (rs, i) -> new MocDongBo(
                        moc(rs.getObject("lan_lay_cuoi", OffsetDateTime.class)),
                        moc(rs.getObject("moc_do", OffsetDateTime.class))));
    }

    /**
     * ⚠⚠ {@code rs.getObject(col, Instant.class)} <b>ném</b> với cột {@code timestamptz} — trình
     * điều khiển PostgreSQL ⛔ không khai phép đổi ấy, dù nó đổi được sang {@link OffsetDateTime}.
     * Cùng bẫy đã trả giá ở {@code HydroReportRepository} và {@code HydroChartRepository}; ngoại lệ
     * bị dịch thành {@code SYS-0005}/409 nên triệu chứng ⛔ không hề trỏ vào dòng mã này.
     */
    private static Instant moc(OffsetDateTime odt) {
        return odt == null ? null : odt.toInstant();
    }
}
