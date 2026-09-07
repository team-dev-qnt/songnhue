package com.songnhue.hydro.infra;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.BoLocNhatKy;
import com.songnhue.hydro.domain.LuotDongBo;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncStatus;
import com.songnhue.hydro.domain.TongHopDongBo;

/**
 * Đường <b>ĐỌC</b> của {@code sync_logs} — nửa còn lại của cặp mà {@link SyncLogWriter} mở ra
 * (T31.13).
 *
 * <h2>⚠ Vì sao lớp này phải tồn tại, nói thẳng</h2>
 *
 * <p>Từ 01/09 tới 02/09, {@code sync_logs} có <b>một đường ghi hoàn chỉnh và không đường đọc nào</b>
 * — đúng hình dạng luật 27 (<i>một nửa vòng chạy hoàn hảo vẫn cho ra số không</i>). Bảng đầy dữ liệu
 * chẩn đoán, và cách duy nhất chạm tới nó là mở {@code psql} theo một câu truy vấn chép từ tài liệu.
 * Runbook có câu ấy ⛔ không phải là một màn hình.
 *
 * <h2>Vì sao JDBC chứ không repository JPA</h2>
 *
 * <p>Cùng lý do chịu lực với {@link PollerRepository}, thêm một lý do riêng: dựng một entity JPA cho
 * {@code sync_logs} là mở <b>đường ghi thứ hai</b> vào một bảng mà mọi ràng buộc {@code CHECK} liên
 * trường ({@code ck_sync_logs_failed_co_ly_do}, {@code ck_sync_logs_success_khong_loi}) đang được
 * đúng một nơi tôn trọng. Hai đường ghi vào một bảng có luật liên trường là đúng chỗ luật 14 gọi
 * tên. Ở đây <b>không có một câu {@code INSERT} nào</b>, và đó là bảo đảm ở tầng cấu trúc.
 *
 * <p>⛔ Không lớp nào ở đây mở giao dịch — ranh giới transaction thuộc tầng application (ArchUnit
 * canh).
 */
@Repository
public class SyncLogQueryRepository {

    /**
     * ⭐ <b>Một mệnh đề FROM, hai câu dùng chung.</b>
     *
     * <p>Câu đếm và câu lấy trang phải soi <b>đúng cùng một tập dòng</b>, nếu không thì tổng số ở
     * thanh phân trang nói một đằng và bảng hiện một nẻo — và cái sai ấy chỉ lộ ra ở trang cuối.
     * Ghép cùng một chuỗi {@link #dieuKien} vào cùng một chuỗi này là cách rẻ nhất để chúng không
     * thể lệch nhau (luật 14).
     */
    private static final String TU_BANG =
            """
             FROM sync_logs l
             JOIN api_sources s ON s.id = l.api_source_id
            """;

    private static final String CHON_COT =
            """
            SELECT l.id, l.started_at, l.finished_at, l.duration_ms, l.frame_start,
                   l.status, l.failure_kind, l.failure_detail,
                   l.received_count, l.written_count, l.skipped_count, l.unmapped_count, l.raw_log_id,
                   s.public_id AS nguon_public_id, s.code AS nguon_code, s.name AS nguon_name
            """;

    /**
     * ⚠ {@code ORDER BY} có <b>khoá phá hoà</b> {@code l.id DESC}.
     *
     * <p>Phân trang bằng {@code OFFSET} trên một thứ tự <i>không toàn phần</i> là một lỗi câm: hai
     * dòng cùng {@code started_at} có thể đổi chỗ giữa hai lượt truy vấn, và người dùng lật trang
     * thấy một dòng <b>hai lần</b> trong khi một dòng khác biến mất. Poller ghi một dòng mỗi lượt
     * nên trùng mốc là hiếm — nhưng "hiếm" và "không thể" là hai chuyện, còn khoá phá hoà thì miễn
     * phí.
     */
    private static final String SAP_XEP = " ORDER BY l.started_at DESC, l.id DESC";

    /**
     * Gộp theo <b>cặp</b> {@code (status, failure_kind)}, ⛔ không gộp hai lần riêng.
     *
     * <p>Hai câu {@code GROUP BY} riêng là hai lượt quét cho hai con số phải khớp nhau; một câu cho
     * cả hai bản đồ thì chúng không thể lệch. {@code max(started_at)} đi kèm luôn để trả lời câu hỏi
     * nặng nhất — <i>poller có còn chạy không</i> — mà không tốn thêm lượt đi–về.
     */
    private static final String SQL_TONG_HOP =
            """
            SELECT l.status, l.failure_kind, count(*) AS so, max(l.started_at) AS moc
              FROM sync_logs l
             WHERE l.started_at >= ?
             GROUP BY l.status, l.failure_kind
            """;

    private final JdbcTemplate jdbc;

    public SyncLogQueryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<LuotDongBo> trang(BoLocNhatKy loc, long boQua, int soDong) {
        List<Object> thamSo = new ArrayList<>();
        String sql = CHON_COT + TU_BANG + dieuKien(loc, thamSo) + SAP_XEP + " LIMIT ? OFFSET ?";
        thamSo.add(soDong);
        thamSo.add(boQua);
        return jdbc.query(sql, MOT_LUOT, thamSo.toArray());
    }

    public long dem(BoLocNhatKy loc) {
        List<Object> thamSo = new ArrayList<>();
        String sql = "SELECT count(*)" + TU_BANG + dieuKien(loc, thamSo);
        Long n = jdbc.queryForObject(sql, Long.class, thamSo.toArray());
        return n == null ? 0L : n;
    }

    /**
     * @param tuMoc đầu cửa sổ đo
     * @return bản đồ <b>đủ mọi khoá</b> — {@link TongHopDongBo} ép điều đó ở hàm dựng, xem lý do ở
     *     javadoc của nó
     */
    public TongHopDongBo tongHop(Instant tuMoc) {
        Map<SyncStatus, Long> theoTrangThai = new EnumMap<>(SyncStatus.class);
        Map<SyncFailureKind, Long> theoLoi = new EnumMap<>(SyncFailureKind.class);
        long[] tong = {0L};
        Instant[] ganNhat = {null};

        jdbc.query(
                SQL_TONG_HOP,
                rs -> {
                    long so = rs.getLong("so");
                    tong[0] += so;
                    theoTrangThai.merge(SyncStatus.valueOf(rs.getString("status")), so, Long::sum);
                    String loi = rs.getString("failure_kind");
                    if (loi != null) {
                        theoLoi.merge(SyncFailureKind.valueOf(loi), so, Long::sum);
                    }
                    Timestamp moc = rs.getTimestamp("moc");
                    if (moc != null && (ganNhat[0] == null || moc.toInstant().isAfter(ganNhat[0]))) {
                        ganNhat[0] = moc.toInstant();
                    }
                },
                Timestamp.from(tuMoc));

        return new TongHopDongBo(tuMoc, tong[0], theoTrangThai, theoLoi, ganNhat[0]);
    }

    /**
     * Dựng mệnh đề {@code WHERE} và <b>nạp tham số theo đúng thứ tự</b>.
     *
     * <p>⛔ Không một giá trị nào của người dùng được nối vào chuỗi SQL — tất cả đi qua {@code ?}.
     * Chuỗi ghép ở đây chỉ gồm hằng số viết trong chính tệp này.
     */
    private static String dieuKien(BoLocNhatKy loc, List<Object> thamSo) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (loc.nguonPublicId() != null) {
            sql.append(" AND s.public_id = ?");
            thamSo.add(loc.nguonPublicId());
        }
        if (loc.trangThai() != null) {
            sql.append(" AND l.status = ?");
            thamSo.add(loc.trangThai().name());
        }
        if (loc.loi() != null) {
            sql.append(" AND l.failure_kind = ?");
            thamSo.add(loc.loi().name());
        }
        if (loc.tu() != null) {
            sql.append(" AND l.started_at >= ?");
            thamSo.add(Timestamp.from(loc.tu()));
        }
        if (loc.den() != null) {
            // ⚠ `<` chứ không `<=` — nửa khoảng mở, xem javadoc BoLocNhatKy.den().
            sql.append(" AND l.started_at < ?");
            thamSo.add(Timestamp.from(loc.den()));
        }
        if (loc.chiHong()) {
            // Khớp đúng vị từ của ix_sync_logs_hong để Postgres dùng được chỉ mục riêng ấy.
            sql.append(" AND l.status IN ('FAILED', 'PARTIAL')");
        }
        return sql.toString();
    }

    private static final RowMapper<LuotDongBo> MOT_LUOT = (rs, i) -> {
        String loi = rs.getString("failure_kind");
        // ⚠ getObject, ⛔ không getLong + wasNull(): wasNull() nói về LƯỢT ĐỌC GẦN NHẤT, nên nó sẽ
        //   trả lời hộ cột `unmapped_count` đọc ở dưới và biến mọi raw_log_id NULL thành 0 — mà
        //   "chưa hề mở kết nối" và "đã ghi raw #0" là hai chuyện khác nhau (xem LuotDongBo).
        Number rawLogId = (Number) rs.getObject("raw_log_id");
        return new LuotDongBo(
                rs.getLong("id"),
                (UUID) rs.getObject("nguon_public_id"),
                rs.getString("nguon_code"),
                rs.getString("nguon_name"),
                moc(rs, "started_at"),
                moc(rs, "finished_at"),
                (Integer) rs.getObject("duration_ms"),
                moc(rs, "frame_start"),
                SyncStatus.valueOf(rs.getString("status")),
                loi == null ? null : SyncFailureKind.valueOf(loi),
                rs.getString("failure_detail"),
                rs.getInt("received_count"),
                rs.getInt("written_count"),
                rs.getInt("skipped_count"),
                rs.getInt("unmapped_count"),
                rawLogId == null ? null : rawLogId.longValue());
    };

    private static Instant moc(ResultSet rs, String cot) throws SQLException {
        Timestamp t = rs.getTimestamp(cot);
        return t == null ? null : t.toInstant();
    }
}
