package com.songnhue.core.infra.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.audit.AuditLog;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Tra cứu nhật ký (M5.8). Mọi bộ lọc đều tuỳ chọn — {@code null} nghĩa là không lọc theo tiêu chí đó.
     *
     * <p>Bắt buộc truyền khoảng thời gian ở tầng service: bảng phân mảnh theo tháng, không có điều
     * kiện {@code occurred_at} thì PostgreSQL phải quét mọi partition — đúng thứ mà partition sinh ra
     * để tránh.
     */
    @Query(
            """
            SELECT a FROM AuditLog a
             WHERE a.occurredAt >= :from AND a.occurredAt < :to
               AND (:module IS NULL OR a.module = :module)
               AND (:entityType IS NULL OR a.entityType = :entityType)
               AND (:entityId IS NULL OR a.entityId = :entityId)
               AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
             ORDER BY a.seq DESC
            """)
    Page<AuditLog> search(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("module") String module,
            @Param("entityType") String entityType,
            @Param("entityId") Long entityId,
            @Param("actorUserId") Long actorUserId,
            Pageable pageable);
}
