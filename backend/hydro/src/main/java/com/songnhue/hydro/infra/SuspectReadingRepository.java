package com.songnhue.hydro.infra;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.SoDoNghiNgo;

/**
 * Đường <b>ĐỌC</b> của màn hình <i>Dữ liệu nghi ngờ</i> — T32.7.
 *
 * <h2>⚠⚠ Đây là NGOẠI LỆ có tên của quy tắc 14 — và nó phải là ngoại lệ</h2>
 *
 * <p>{@code QualityFilterGuardTest} bắt mọi truy vấn đọc {@code hydro_readings} phải lọc
 * {@code quality = 'HOP_LE'}. Truy vấn ở đây làm <b>ngược lại</b>, và đó chính là lý do nó tồn tại:
 * màn hình này là nơi duy nhất trong hệ thống được nhìn thấy những dòng mà quy tắc 14 loại ra. Lọc
 * {@code HOP_LE} ở đây là làm hàng chờ duyệt <b>luôn rỗng</b> — và một hàng chờ luôn rỗng trông y
 * hệt một hệ thống không có dữ liệu xấu.
 *
 * <p>⇒ Ngoại lệ ấy khai <b>có tên</b> trong {@code QualityFilterGuardTest.NGOAI_LE}, ⛔ không phải
 * bằng cách nới mẫu regex. Và nó bị chặn hẹp lại ngay tại đây: câu chỉ nhận
 * {@link ReadingQuality#NGHI_NGO} và {@link ReadingQuality#XOA}, ⛔ tuyệt đối không
 * {@code HOP_LE} — xem {@link #trangThaiHopLe}.
 *
 * <p>⛔ Không lớp nào ở đây mở giao dịch — ranh giới transaction thuộc tầng application.
 */
@Repository
public class SuspectReadingRepository {

    /**
     * ⚠ Hằng này mang tên {@code TU_BANG} vì {@code QualityFilterGuardTest} khai ngoại lệ theo
     * <b>tên hằng</b>. Đổi tên nó mà quên bộ canh thì bài <i>ngoại lệ mồ côi</i> đỏ — cố ý.
     */
    private static final String TU_BANG =
            """
             FROM hydro_readings r
             JOIN stations s ON s.id = r.station_id
             JOIN measurement_types m ON m.id = r.measurement_type_id
            """;

    private static final String CHON_COT =
            """
            SELECT r.id, r.measured_at, r.reading_value, r.quality, r.quality_reason,
                   r.review_note, r.source, r.ingested_at, r.raw_log_id,
                   s.public_id AS diem_do_public_id, s.code AS diem_do_code, s.name AS diem_do_name,
                   m.code AS loai_code, m.name AS loai_name, m.unit AS don_vi
            """;

    /**
     * ⭐ Mới nhất trước, và <b>có cặp phân định</b>.
     *
     * <p>28 số đo của cùng một khung mang <b>đúng cùng một</b> {@code measured_at}. Sắp xếp chỉ theo
     * cột ấy là để PostgreSQL tự chọn thứ tự trong nhóm, và hai lượt phân trang liên tiếp có thể trả
     * cùng một dòng hai lần — hoặc bỏ sót một dòng, cái nguy hiểm hơn nhiều ở một hàng chờ duyệt.
     */
    private static final String SAP_XEP = " ORDER BY r.measured_at DESC, r.id DESC";

    private final JdbcTemplate jdbc;

    public SuspectReadingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * ⛔ Chốt chặn: màn hình này ⛔ không bao giờ phục vụ {@code HOP_LE}.
     *
     * <p>Đặt ở repository chứ không ở controller là có chủ ý (luật 12): controller không phải đường
     * vào duy nhất — một job, một báo cáo, hay một lượt gọi nội bộ sau này đều đi qua đây.
     */
    private static void chanHopLe(ReadingQuality trangThai) {
        if (trangThai == null || trangThai == ReadingQuality.HOP_LE) {
            throw new IllegalArgumentException("Màn hình Dữ liệu nghi ngờ chỉ phục vụ NGHI_NGO và XOA. "
                    + "Dữ liệu hợp lệ có đường đọc riêng — ⛔ đừng mở cửa hậu ở đây.");
        }
    }

    /** Vế phân biệt cho bài tự-kiểm-chứng: {@code true} nghĩa là trạng thái ⛔ không được phục vụ. */
    public static boolean trangThaiHopLe(ReadingQuality trangThai) {
        return trangThai == ReadingQuality.HOP_LE;
    }

    public long dem(ReadingQuality trangThai, UUID diemDoPublicId, Instant tu, Instant den) {
        chanHopLe(trangThai);
        List<Object> thamSo = new ArrayList<>();
        String sql = "SELECT count(*)" + TU_BANG + dieuKien(trangThai, diemDoPublicId, tu, den, thamSo);
        Long n = jdbc.queryForObject(sql, Long.class, thamSo.toArray());
        return n == null ? 0L : n;
    }

    public List<SoDoNghiNgo> trang(
            ReadingQuality trangThai, UUID diemDoPublicId, Instant tu, Instant den, long bo, int lay) {
        chanHopLe(trangThai);
        List<Object> thamSo = new ArrayList<>();
        String sql = CHON_COT + TU_BANG + dieuKien(trangThai, diemDoPublicId, tu, den, thamSo) + SAP_XEP
                + " LIMIT ? OFFSET ?";
        thamSo.add(lay);
        thamSo.add(bo);
        return jdbc.query(sql, MOT_DONG, thamSo.toArray());
    }

    /** ⚠ Chỉ {@code ?}, ⛔ không nối chuỗi giá trị — kể cả với enum đã kiểm. */
    private static String dieuKien(
            ReadingQuality trangThai, UUID diemDoPublicId, Instant tu, Instant den, List<Object> thamSo) {
        StringBuilder w = new StringBuilder(" WHERE r.quality = ?");
        thamSo.add(trangThai.name());
        if (diemDoPublicId != null) {
            w.append(" AND s.public_id = ?");
            thamSo.add(diemDoPublicId);
        }
        if (tu != null) {
            w.append(" AND r.measured_at >= ?");
            thamSo.add(Timestamp.from(tu));
        }
        if (den != null) {
            // ⚠ `<` chứ không `<=` — khớp lệ đã dùng ở AuditLogRepository: giao diện gửi mốc đầu ngày
            //   kế tiếp, nên `<=` sẽ kéo thêm đúng một khung của ngày sau.
            w.append(" AND r.measured_at < ?");
            thamSo.add(Timestamp.from(den));
        }
        return w.toString();
    }

    private static final RowMapper<SoDoNghiNgo> MOT_DONG = (rs, i) -> {
        // ⚠ getObject, ⛔ không getLong + wasNull(): wasNull() nói về LƯỢT ĐỌC GẦN NHẤT, nên đặt sau
        //   một cột khác là nó trả lời hộ cột ấy và mọi NULL thành 0. `raw_log_id` rỗng nghĩa là
        //   dòng này do NGƯỜI nhập, ⛔ không phải "raw số 0".
        Number rawLogId = (Number) rs.getObject("raw_log_id");
        Timestamp mocGhi = rs.getTimestamp("ingested_at");
        return new SoDoNghiNgo(
                rs.getLong("id"),
                rs.getTimestamp("measured_at").toInstant(),
                (UUID) rs.getObject("diem_do_public_id"),
                rs.getString("diem_do_code"),
                rs.getString("diem_do_name"),
                rs.getString("loai_code"),
                rs.getString("loai_name"),
                rs.getString("don_vi"),
                rs.getBigDecimal("reading_value"),
                ReadingQuality.valueOf(rs.getString("quality")),
                rs.getString("quality_reason"),
                rs.getString("review_note"),
                ReadingSource.valueOf(rs.getString("source")),
                mocGhi == null ? null : mocGhi.toInstant(),
                rawLogId == null ? null : rawLogId.longValue());
    };
}
