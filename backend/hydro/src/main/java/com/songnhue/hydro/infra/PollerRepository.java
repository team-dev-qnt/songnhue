package com.songnhue.hydro.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.DiemDoDich;
import com.songnhue.hydro.domain.TinHieuDiemDo;

/**
 * Bốn câu hỏi mà một lượt polling phải trả lời trước và sau khi mở HTTP — T31.4 · T31.7 · T31.8 ·
 * T31.9.
 *
 * <h2>Vì sao JDBC chứ không repository JPA</h2>
 *
 * <p>Ba lý do, và lý do thứ ba là lý do chịu lực:
 *
 * <ol>
 *   <li>Ba trong bốn câu là <b>phép đếm/tổng hợp</b>. Nạp 19 entity để đếm chúng là 19 dòng dữ liệu
 *       đi qua mạng cho một con số.
 *   <li>Câu ánh xạ chạy <b>mỗi 2 phút, suốt đời hệ thống</b>. Một map dựng bằng một lượt quét bảng
 *       19 dòng là thứ rẻ nhất có thể; {@code findByApiCode...} gọi 28 lần là 28 lượt đi–về.
 *   <li>⭐ {@code StationRepository} chịu bộ lọc phạm vi đơn vị của {@code ScopeFilterAspect}. Hôm
 *       nay aspect ấy <b>không bật khi không có người đăng nhập</b> (đúng thiết kế, javadoc của nó
 *       nói đích danh "poller thuỷ văn"), nên đường JPA cũng chạy đúng — nhưng đó là một bảo đảm
 *       <i>gián tiếp</i>, và ngày nào có người thêm một nhánh "job cũng có danh tính" thì poller âm
 *       thầm bỏ sót điểm đo của các Xí nghiệp mà không một lỗi nào. Truy vấn ở đây <b>không thể</b>
 *       bị lọc, và đó là điều phải đúng: một số đo của trạm thuộc Xí nghiệp nào cũng phải được ghi.
 * </ol>
 *
 * <p>⛔ Không lớp nào ở đây mở giao dịch — ranh giới transaction thuộc tầng application (ArchUnit
 * canh).
 */
@Repository
public class PollerRepository {

    /**
     * ⭐ Ánh xạ mã nguồn → điểm đo — <b>quét TOÀN BỘ điểm đo còn sống, ⛔ không lọc theo nguồn.</b>
     *
     * <p>Lý do là một ràng buộc đã có trong lược đồ: {@code ux_stations_api_code} là
     * {@code UNIQUE (api_code) WHERE deleted_at IS NULL}, tức <b>toàn hệ thống</b>. Nên câu hỏi "mã
     * này là trạm nào" chỉ có một câu trả lời, và lọc thêm {@code api_source_id} chỉ tạo ra một câu
     * trả lời <i>thứ hai</i> cho cùng câu hỏi — đúng hình dạng luật 14. Cột {@code api_source_id}
     * vẫn đi theo trong {@link DiemDoDich} để nơi gọi ghi WARN khi nguồn trả mã của một hồ sơ khai
     * cho nguồn khác; ⛔ nó không phải điều kiện lọc.
     *
     * @param measurementTypeId loại chỉ số mà adapter đang giao — quyết định cờ
     *     {@link DiemDoDich#daKhaiLoaiChiSo()}
     */
    private static final String SQL_ANH_XA =
            """
            SELECT s.api_code, s.id, s.code, s.api_source_id, s.active,
                   EXISTS (
                       SELECT 1 FROM station_measurement_types smt
                        WHERE smt.station_id = s.id AND smt.measurement_type_id = ?
                   ) AS da_khai
              FROM stations s
             WHERE s.deleted_at IS NULL
            """;

    /**
     * ⭐⭐ Điều kiện dừng của rate-limit — <b>quy tắc 17</b>, và là chỗ dễ viết sai nhất của WS-31.
     *
     * <p>Đếm số điểm đo <b>đang hoạt động</b> của nguồn này đã có bản ghi thuộc khung hiện tại. Nơi
     * gọi so con số ấy với tổng số điểm đo đang hoạt động và chỉ bỏ lượt gọi khi <b>bằng nhau</b>.
     *
     * <p>⛔ ⛔ Không phải "đã có bản ghi đầu tiên thì thôi": nguồn đẩy dữ liệu rải rác trong cửa sổ
     * {@code x1:30 → x8:30}, nên một trạm lên muộn sẽ bị bỏ lỡ <b>vĩnh viễn</b> — và trên biểu đồ nó
     * trông y hệt một trạm hỏng.
     *
     * <p>⚠ Đọc {@code hydro_latest} chứ không {@code hydro_readings}: bảng latest có đúng một dòng
     * cho mỗi (điểm đo × loại chỉ số) và một chỉ mục trên {@code last_seen_at}, còn readings là bảng
     * phân mảnh lớn nhất hệ thống. Cùng một câu trả lời, khác nhau hai bậc chi phí.
     */
    private static final String SQL_DA_CO_TRONG_KHUNG =
            """
            SELECT count(*) FROM stations s
             WHERE s.deleted_at IS NULL AND s.active AND s.api_source_id = ?
               AND EXISTS (
                   SELECT 1 FROM hydro_latest l
                    WHERE l.station_id = s.id AND l.last_seen_at >= ?
               )
            """;

    private static final String SQL_DEM_HOAT_DONG =
            "SELECT count(*) FROM stations s WHERE s.deleted_at IS NULL AND s.active AND s.api_source_id = ?";

    /**
     * Ảnh chụp tín hiệu toàn bộ điểm đo — đầu vào của job phát hiện mất tín hiệu.
     *
     * <p>⚠ {@code LEFT JOIN}, ⛔ không {@code JOIN}: một điểm đo <b>chưa từng</b> có bản ghi nào phải
     * đi ra khỏi câu này với {@code last_seen_at = NULL}. Đổi sang {@code JOIN} là làm cả nhóm
     * {@code CHUA_CO_DU_LIEU} biến mất khỏi màn hình — và đó chính là nhóm cần nhìn nhất trong tuần
     * đầu vận hành.
     *
     * <p>⚠ {@code max(...)}: một điểm đo có nhiều loại chỉ số thì có nhiều dòng latest. Trạm còn
     * phát tín hiệu nếu <b>bất kỳ</b> chỉ số nào của nó còn về.
     */
    private static final String SQL_TIN_HIEU =
            """
            SELECT s.id, s.code, s.name, s.active, max(l.last_seen_at) AS gan_nhat
              FROM stations s
              LEFT JOIN hydro_latest l ON l.station_id = s.id
             WHERE s.deleted_at IS NULL
             GROUP BY s.id, s.code, s.name, s.active
             ORDER BY s.code
            """;

    /**
     * ⭐ Mốc lượt ingest <b>thành công</b> gần nhất — chỉ số đo <b>SỰ VẮNG MẶT</b> (T31.9).
     *
     * <p>⛔ Khác hẳn {@code HydroFreshnessRegistrar}: chỉ số độ tươi đo <i>dữ liệu</i> có mới không,
     * và nó <b>im lặng</b> chừng nào chưa có dòng nào — cố ý, để một cảnh báo critical không kêu
     * suốt quãng WS-29 → WS-31. Câu này đo <i>lượt chạy</i> có xảy ra không, nên nó nói được điều
     * kia không nói được: <b>một hệ thống chưa từng ingest thành công lần nào.</b>
     *
     * <p>⚠ Nhận cả {@code PARTIAL}: một lượt lấy được 8/19 trạm vẫn là bằng chứng poller sống và
     * nguồn trả lời. "Thiếu trạm" là chuyện của job mất tín hiệu, không phải của chỉ số này.
     */
    private static final String SQL_INGEST_GAN_NHAT =
            "SELECT max(started_at) FROM sync_logs WHERE status IN ('SUCCESS', 'PARTIAL')";

    private static final String SQL_ID_LOAI_CHI_SO =
            "SELECT id FROM measurement_types WHERE code = ? AND deleted_at IS NULL";

    private final JdbcTemplate jdbc;

    public PollerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Loại chỉ số mà adapter giao, tra theo mã nghiệp vụ.
     *
     * @return rỗng khi ai đó đã xoá mềm loại chỉ số — nơi gọi phải <b>dừng lượt ingest</b> và nói
     *     ra, ⛔ không được đoán một id khác: ghi mực nước vào cột "lượng mưa" là sai số liệu câm
     */
    public Optional<Long> idLoaiChiSo(String code) {
        return jdbc.queryForList(SQL_ID_LOAI_CHI_SO, Long.class, code).stream().findFirst();
    }

    /** @return mã API → điểm đo; mã không có trong map là <b>mã chưa khai</b> (quy tắc parse 5) */
    public Map<String, DiemDoDich> dichTheoMaApi(long measurementTypeId) {
        Map<String, DiemDoDich> theoMa = new HashMap<>();
        jdbc.query(
                SQL_ANH_XA,
                rs -> {
                    theoMa.put(
                            rs.getString("api_code"),
                            new DiemDoDich(
                                    rs.getLong("id"),
                                    rs.getString("code"),
                                    rs.getLong("api_source_id"),
                                    rs.getBoolean("active"),
                                    rs.getBoolean("da_khai")));
                },
                measurementTypeId);
        return Map.copyOf(theoMa);
    }

    public int demDiemDoDaCoTrongKhung(long apiSourceId, Instant frameStart) {
        Integer n = jdbc.queryForObject(SQL_DA_CO_TRONG_KHUNG, Integer.class, apiSourceId, Timestamp.from(frameStart));
        return n == null ? 0 : n;
    }

    public int demDiemDoDangHoatDong(long apiSourceId) {
        Integer n = jdbc.queryForObject(SQL_DEM_HOAT_DONG, Integer.class, apiSourceId);
        return n == null ? 0 : n;
    }

    public List<TinHieuDiemDo> tinHieuDiemDo() {
        return jdbc.query(SQL_TIN_HIEU, (rs, i) -> {
            Timestamp ganNhat = rs.getTimestamp("gan_nhat");
            return new TinHieuDiemDo(
                    rs.getLong("id"),
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getBoolean("active"),
                    ganNhat == null ? null : ganNhat.toInstant());
        });
    }

    /** @return rỗng khi <b>chưa từng</b> có lượt ingest thành công nào — xem {@link #SQL_INGEST_GAN_NHAT} */
    public Optional<Instant> mocIngestThanhCongGanNhat() {
        return jdbc.queryForList(SQL_INGEST_GAN_NHAT, Timestamp.class).stream()
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(Timestamp::toInstant);
    }
}
