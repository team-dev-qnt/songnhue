package com.songnhue.core.infra.audit;

import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Gọi các hàm bảo trì nhật ký kiểm toán đã cài trong DB.
 *
 * <p>⚠ Công thức băm và luật nối chuỗi nằm <b>duy nhất</b> trong DB (migration V…1004 ghi rõ). Lớp
 * này chỉ gọi hàm, tuyệt đối không cài lại phép tính bên Java — hai công thức lệch nhau dù chỉ ở một
 * ký tự phân cách sẽ báo chuỗi gãy trong khi dữ liệu hoàn toàn nguyên vẹn, và không ai dám tin kết
 * quả verify nữa.
 */
@Repository
public class AuditMaintenanceRepository {

    private final JdbcTemplate jdbc;

    public AuditMaintenanceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Tạo trước partition cho {@code monthsAhead} tháng tới.
     *
     * <p>Idempotent: gọi lại không tạo thêm. Hết runway thì bản ghi rơi vào partition
     * {@code DEFAULT} — vẫn ghi được, nhưng đó là dấu hiệu job này đã chết.
     *
     * @return số partition vừa tạo thêm
     */
    public int ensurePartitions(int monthsAhead) {
        Integer created = jdbc.queryForObject("SELECT core_ensure_audit_partitions(?)", Integer.class, monthsAhead);
        return created == null ? 0 : created;
    }

    /** Số bản ghi lọt vào partition {@code DEFAULT} — bình thường phải luôn bằng 0. */
    public long countInDefaultPartition() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM audit_logs_default", Long.class);
        return count == null ? 0 : count;
    }

    /**
     * Kiểm tra tính toàn vẹn chuỗi hash (M5.8, quyền {@code adm:audit:verify}).
     *
     * <p>Gọi thẳng {@code core_verify_audit_chain} — hàm này dùng lại đúng công thức băm mà trigger
     * đã dùng lúc ghi. Đó là lý do <b>cấm</b> cài lại phép tính bên Java: lệch nhau dù chỉ ở ký tự
     * phân cách sẽ báo "chuỗi gãy" trên dữ liệu hoàn toàn nguyên vẹn, và một khi công cụ kiểm tra đã
     * báo sai thì không ai còn tin nó nữa — nhật ký mất hết giá trị làm bằng chứng.
     *
     * @return danh sách bản ghi có vấn đề; rỗng nghĩa là chuỗi nguyên vẹn
     */
    public List<ChainBreak> verifyChain(Long fromSeq, Long toSeq) {
        return jdbc.query(
                "SELECT broken_seq, broken_id, occurred_at, reason FROM core_verify_audit_chain(?, ?)",
                (rs, rowNum) -> new ChainBreak(
                        rs.getLong("broken_seq"),
                        rs.getLong("broken_id"),
                        rs.getTimestamp("occurred_at").toInstant(),
                        rs.getString("reason")),
                fromSeq,
                toSeq);
    }

    /** Khoảng {@code seq} hiện có — để màn hình kiểm tra biết phạm vi và chia nhỏ lượt chạy. */
    public SeqRange seqRange() {
        return jdbc.queryForObject(
                "SELECT coalesce(min(seq), 0) AS lo, coalesce(max(seq), 0) AS hi, count(*) AS total FROM audit_logs",
                (rs, rowNum) -> new SeqRange(rs.getLong("lo"), rs.getLong("hi"), rs.getLong("total")));
    }

    /** @param reason mô tả bằng tiếng Việt do hàm trong DB sinh ra — hiển thị thẳng cho người dùng */
    public record ChainBreak(long seq, long id, Instant occurredAt, String reason) {}

    public record SeqRange(long minSeq, long maxSeq, long total) {}
}
