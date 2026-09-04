package com.songnhue.hydro.infra;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.KyTongHop;

/**
 * Đọc hàng đợi kỳ bẩn và <b>tính lại</b> {@code hydro_agg_daily} — T34.1.
 *
 * <h2>⭐⭐ Vì sao XOÁ rồi GHI, chứ không phải UPSERT thuần</h2>
 *
 * <p>Một UPSERT theo khoá kỳ ({@code station × chỉ số × ngày × quality}) chỉ sửa được những nhóm
 * chất lượng <b>còn sinh ra hàng</b>. Nó ⛔ không xoá được nhóm đã biến mất — và nhóm biến mất là
 * trường hợp <i>thường xuyên nhất</i> của bảng này:
 *
 * <pre>
 *   Ngày 12/9, trạm X có đúng 1 bản ghi, bị đánh NGHI_NGO.
 *     → agg có 1 hàng: (…, 12/9, NGHI_NGO, count=1)
 *   Người trực duyệt nó lên HOP_LE.
 *     → tính lại sinh ra 1 hàng (…, 12/9, HOP_LE, count=1)
 *     → UPSERT thuần GIỮ NGUYÊN hàng NGHI_NGO cũ
 *     → BC-13 báo trạm ấy có 1 bản ghi nghi ngờ **vĩnh viễn**, dù không còn cái nào
 * </pre>
 *
 * <p>Đó đúng hình dạng luật 27 — <i>một nửa cặp trông y hệt cả cặp</i>: đường ghi chạy hoàn hảo, số
 * liệu vẫn ra đủ định dạng, và con số sai không có ngoại lệ nào đi kèm. Nên
 * {@link #tinhLai(KyTongHop)} <b>xoá trọn kỳ rồi dựng lại từ nguồn</b>, trong một giao dịch. Chạy
 * hai lần liên tiếp cho ra đúng một kết quả — đó là toàn bộ định nghĩa của <i>idempotent</i> mà
 * T34.1 đòi, và nó được chứng minh bằng một phép kiểm chạy job hai lượt.
 *
 * <h2>⚠⚠ Thứ tự bắt buộc: NHẬN kỳ TRƯỚC, tính SAU</h2>
 *
 * <p>Nếu tính xong rồi mới xoá cờ bẩn thì có một cửa sổ mất dữ liệu <b>im lặng</b>: một số đo về
 * giữa chừng làm trigger cắm cờ, nhưng cờ ấy <i>đã có sẵn</i> nên {@code ON CONFLICT DO NOTHING} bỏ
 * qua — rồi ta xoá chính cái cờ đó đi. Số đo ấy ⛔ không bao giờ vào bảng tổng hợp, và ⛔ không có
 * gì để phát hiện.
 *
 * <p>Xoá cờ <b>trước</b> (trong cùng giao dịch với lượt tính) thì trigger của lượt ghi song song
 * phải chờ hàng đang bị xoá, rồi cắm lại cờ sau khi ta commit — lượt drain kế tiếp nhặt nó lên. Đây
 * là lý do {@link #nhanKy} và {@link #tinhLai} phải nằm trong <b>cùng một</b> giao dịch, và lý do
 * {@code nhanKy} trả về {@code boolean}: {@code false} nghĩa là ai đó đã nhận kỳ này rồi.
 */
@Repository
public class HydroAggRepository {

    /**
     * Kỳ đang chờ, cũ trước.
     *
     * <p>⛔ Không {@code FOR UPDATE SKIP LOCKED}: khoá chống trùng của việc nền
     * ({@code HYDRO_AGG_REBUILD}) đã bảo đảm mỗi lúc chỉ một lượt drain, và chốt chặn cuối là
     * {@link #nhanKy} — nó dùng chính {@code DELETE … RETURNING} làm phép giành quyền.
     */
    private static final String SQL_KY_BAN =
            """
            SELECT station_id, measurement_type_id, agg_date
              FROM hydro_agg_dirty
             ORDER BY marked_at, station_id, measurement_type_id
             LIMIT ?
            """;

    private static final String SQL_DEM_KY_BAN = "SELECT count(*) FROM hydro_agg_dirty";

    /**
     * Giành quyền xử lý một kỳ.
     *
     * <p>{@code DELETE … RETURNING} là một thao tác nguyên tử: đúng một lượt gọi lấy được hàng, mọi
     * lượt gọi khác nhận rỗng. ⛔ Đừng đổi thành {@code SELECT} rồi {@code DELETE} — hai câu là hai
     * lần nhìn, và giữa chúng có chỗ cho một lượt drain thứ hai chen vào.
     */
    private static final String SQL_NHAN_KY =
            """
            DELETE FROM hydro_agg_dirty
             WHERE station_id = ? AND measurement_type_id = ? AND agg_date = ?
            RETURNING station_id
            """;

    /**
     * Xoá trọn kỳ — <b>cả ba mức chất lượng</b>.
     *
     * <p>⚠ Ngoại lệ CÓ TÊN của quy tắc 14 (khai ở {@code QualityFilterGuardTest.NGOAI_LE}): câu này
     * cố ý ⛔ không lọc {@code quality}. Lọc ở đây là để lại đúng những hàng cần biến mất — xem phần
     * đầu javadoc lớp.
     */
    private static final String SQL_XOA_KY =
            """
            DELETE FROM hydro_agg_daily
             WHERE station_id = ? AND measurement_type_id = ? AND agg_date = ?
            """;

    /**
     * ⭐ Dựng lại kỳ từ {@code hydro_readings} — một câu, một lượt quét, ba nhóm chất lượng.
     *
     * <p>⚠⚠ Ngoại lệ CÓ TÊN của quy tắc 14: câu này ⛔ <b>không</b> lọc {@code quality = 'HOP_LE'},
     * và <b>phải</b> không lọc. Bảng đích có {@code quality} trong khoá, nên nó cần cả ba nhóm; lọc
     * ở đây làm BC-13 mù trước đúng những ngày tồi tệ nhất — tức mù đúng lúc nó cần nhìn. Bộ lọc
     * nghiệp vụ nằm ở <i>nơi đọc</i> bảng agg, ⛔ không ở nơi dựng nó.
     *
     * <p>⭐ {@code array_agg(… ORDER BY …)[1]} thay cho một câu con tương quan: nó lấy mốc đạt
     * min/max trong <b>cùng một lượt gộp</b>. Khoá phụ {@code measured_at ASC} làm phép chọn <b>tất
     * định</b> khi nhiều mốc cùng giá trị — mực nước đứng yên nhiều giờ là chuyện thường, và nếu
     * không chốt thứ tự thì hai lượt tính lại của cùng một dữ liệu cho ra hai mốc khác nhau. Một
     * bảng "idempotent" mà chạy hai lần ra hai kết quả là một bảng không idempotent.
     *
     * <p>⚠ Biên ngày lấy qua {@code hyd_dau_ngay_vn} chứ ⛔ không so {@code hyd_ngay_vn(measured_at)
     * = ?}: dạng sau là một vị từ trên biểu thức của cột phân mảnh, PostgreSQL ⛔ không cắt tỉa
     * partition được và lượt quét trải rộng toàn bộ 5 năm.
     */
    private static final String SQL_DUNG_LAI =
            """
            INSERT INTO hydro_agg_daily (
                station_id, measurement_type_id, agg_date, quality,
                reading_count, min_value, min_at, max_value, max_at,
                avg_value, sum_value, first_at, last_at, computed_at)
            SELECT ?::bigint, ?::bigint, ?::date,
                   r.quality,
                   count(*),
                   min(r.reading_value),
                   (array_agg(r.measured_at ORDER BY r.reading_value ASC, r.measured_at ASC))[1],
                   max(r.reading_value),
                   (array_agg(r.measured_at ORDER BY r.reading_value DESC, r.measured_at ASC))[1],
                   round(avg(r.reading_value), 5),
                   sum(r.reading_value),
                   min(r.measured_at),
                   max(r.measured_at),
                   now()
              FROM hydro_readings r
             WHERE r.station_id = ?
               AND r.measurement_type_id = ?
               AND r.measured_at >= hyd_dau_ngay_vn(?)
               AND r.measured_at <  hyd_dau_ngay_vn((?::date) + 1)
             GROUP BY r.quality
            """;

    /**
     * Lưới an toàn hằng ngày — T34.1.
     *
     * <p>Trigger là đường chính và nó ⛔ không bỏ sót. Câu này bắt loại hỏng mà trigger ⛔ không
     * thấy: một lượt drain chết giữa chừng sau khi đã {@code DELETE} cờ bẩn nhưng trước khi commit
     * phần tính — giao dịch quay lui nên cờ trở lại, đúng; nhưng một lỗi cấu hình khiến job ⛔ không
     * chạy suốt nhiều ngày thì hàng đợi phình ra mà ⛔ không ai nhìn.
     *
     * <p>⇒ Mỗi ngày cắm lại cờ cho <b>hai ngày gần nhất</b> của mọi kỳ đã có số đo. Hai — ⛔ không
     * phải một: nguồn trả rải rác trong cửa sổ {@code x1:30 → x8:30} và một bản ghi 23:5x giờ VN có
     * thể về sau nửa đêm. ⛔ Cố ý ⛔ không làm thành tham số {@code settings}: một con số cấu hình
     * được là một con số phải có UI, phải có người đọc, và phải có người giải thích — trong khi ở
     * đây nó không có câu hỏi nghiệp vụ nào phía sau (quy tắc 12 nói về tham số NGHIỆP VỤ).
     *
     * <p>⚠ Ngoại lệ CÓ TÊN của quy tắc 14, cùng lý do với {@link #SQL_DUNG_LAI}.
     */
    private static final String SQL_CAM_LAI_CO_GAN_DAY =
            """
            INSERT INTO hydro_agg_dirty (station_id, measurement_type_id, agg_date)
            SELECT DISTINCT r.station_id, r.measurement_type_id, hyd_ngay_vn(r.measured_at)
              FROM hydro_readings r
             WHERE r.measured_at >= hyd_dau_ngay_vn((now() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date - 1)
            ON CONFLICT DO NOTHING
            """;

    private final JdbcTemplate jdbc;

    public HydroAggRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<KyTongHop> kyBan(int gioiHan) {
        return jdbc.query(
                SQL_KY_BAN,
                (rs, i) -> new KyTongHop(
                        rs.getLong("station_id"),
                        rs.getLong("measurement_type_id"),
                        rs.getObject("agg_date", LocalDate.class)),
                gioiHan);
    }

    public int demKyBan() {
        Integer so = jdbc.queryForObject(SQL_DEM_KY_BAN, Integer.class);
        return so == null ? 0 : so;
    }

    /** @return {@code true} nếu lượt gọi này là người giành được kỳ; {@code false} nếu đã có người khác */
    public boolean nhanKy(KyTongHop ky) {
        return !jdbc.queryForList(
                        SQL_NHAN_KY, Long.class, ky.stationId(), ky.measurementTypeId(), Date.valueOf(ky.ngay()))
                .isEmpty();
    }

    /** @return số hàng tổng hợp sinh ra (0 ⇒ kỳ ấy ⛔ không còn số đo nào, và hàng cũ đã bị xoá) */
    public int tinhLai(KyTongHop ky) {
        Date ngay = Date.valueOf(ky.ngay());
        jdbc.update(SQL_XOA_KY, ky.stationId(), ky.measurementTypeId(), ngay);
        return jdbc.update(
                SQL_DUNG_LAI,
                ky.stationId(),
                ky.measurementTypeId(),
                ngay,
                ky.stationId(),
                ky.measurementTypeId(),
                ngay,
                ngay);
    }

    /** @return số kỳ được cắm lại cờ */
    public int camLaiCoGanDay() {
        return jdbc.update(SQL_CAM_LAI_CO_GAN_DAY);
    }
}
