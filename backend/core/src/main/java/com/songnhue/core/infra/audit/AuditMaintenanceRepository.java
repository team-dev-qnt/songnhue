package com.songnhue.core.infra.audit;

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
}
