package com.songnhue.core.domain.audit;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một dòng nhật ký kiểm toán — <b>chỉ đọc</b>.
 *
 * <p>{@link Immutable} không phải để tối ưu mà là để tuyên bố ranh giới: bảng này append-only, app
 * không có quyền {@code UPDATE}/{@code DELETE} ở tầng DB (WS-2 siết bằng GRANT và trigger). Nếu để
 * entity ghi được thì mã nghiệp vụ sẽ biên dịch trót lọt rồi đổ vỡ lúc chạy với một lỗi quyền khó
 * hiểu, thay vì không viết được ngay từ đầu.
 *
 * <p>Việc ghi đi qua {@code AuditLogWriter} bằng JDBC — xem lớp đó để biết vì sao.
 *
 * <p>Khoá chính thật trong DB là {@code (id, occurred_at)} vì bảng phân mảnh theo tháng. Ở đây khai
 * mỗi {@code id} là đủ: entity chỉ dùng cho truy vấn danh sách, không bao giờ tải theo khoá.
 */
@Entity
@Immutable
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    /** Số thứ tự chain do trigger cấp — liên tục qua mọi partition. */
    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_username", length = 100)
    private String actorUsername;

    @Column(name = "module", nullable = false, length = 20)
    private String module;

    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "entity_public_id")
    private UUID entityPublicId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    private AuditAction action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value")
    private String newValue;

    @Column(name = "org_unit_id")
    private Long orgUnitId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    protected AuditLog() {}

    public Long getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Long getSeq() {
        return seq;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public String getModule() {
        return module;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public UUID getEntityPublicId() {
        return entityPublicId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Long getOrgUnitId() {
        return orgUnitId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getTraceId() {
        return traceId;
    }
}
