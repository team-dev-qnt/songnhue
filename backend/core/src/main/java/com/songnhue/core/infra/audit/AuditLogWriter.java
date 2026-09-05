package com.songnhue.core.infra.audit;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.core.common.filter.AuditContext;
import com.songnhue.core.common.web.RequestContext;
import com.songnhue.core.domain.audit.AuditEntry;

/**
 * Ghi nhật ký kiểm toán.
 *
 * <p><b>Vì sao dùng JDBC thẳng chứ không entity JPA.</b> Ba cột {@code seq}, {@code prev_hash},
 * {@code hash} do trigger trong DB cấp và app không có quyền sửa. Một entity JPA sẽ mang ba trường
 * đó, Hibernate sẽ gửi giá trị của mình lên, và người đọc mã sẽ tưởng tầng Java nắm chuỗi hash —
 * trong khi thực tế mọi giá trị đó bị trigger ghi đè. Câu INSERT liệt kê đúng những cột app được
 * phép ghi làm cho ranh giới ấy hiện ra ngay trong mã.
 *
 * <p>Insert theo lô: một giao dịch nghiệp vụ sửa nhiều dòng thì cũng chỉ một lần đi DB.
 */
@Repository
public class AuditLogWriter {

    private static final String INSERT_SQL =
            """
            INSERT INTO audit_logs (
                actor_user_id, actor_username, module, entity_type, entity_id, entity_public_id,
                action, old_value, new_value, org_unit_id, ip_address, trace_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, CAST(? AS inet), ?)
            """;

    private final JdbcTemplate jdbc;

    public AuditLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void write(List<AuditEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        AuditContext.Data actor = AuditContext.get();
        String traceId = RequestContext.traceId();

        jdbc.batchUpdate(INSERT_SQL, entries, entries.size(), (ps, entry) -> {
            ps.setObject(1, actor.userId());
            ps.setString(2, actor.username());
            ps.setString(3, entry.module());
            ps.setString(4, entry.entityType());
            ps.setObject(5, entry.entityId());
            ps.setObject(6, entry.entityPublicId());
            ps.setString(7, entry.action().name());
            ps.setString(8, entry.oldValue());
            ps.setString(9, entry.newValue());
            ps.setObject(10, entry.orgUnitId());
            ps.setString(11, actor.ipAddress());
            ps.setString(12, traceId);
        });
    }
}
