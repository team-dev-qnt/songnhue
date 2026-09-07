package com.songnhue.core.api.audit;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.songnhue.core.application.audit.AuditService;
import com.songnhue.core.domain.audit.AuditAction;
import com.songnhue.core.domain.audit.AuditLog;

/** DTO của API nhật ký kiểm toán. */
public final class AuditDtos {

    /** Khoảng tra mặc định khi người dùng không chọn. */
    private static final int DEFAULT_RANGE_DAYS = 30;

    private AuditDtos() {}

    /**
     * Bộ lọc tra cứu, gom thành một đối tượng thay vì 9 tham số rời.
     *
     * <p>Mọi trường đều tuỳ chọn. Riêng khoảng thời gian <b>luôn</b> được điền: bảng phân mảnh theo
     * tháng, thiếu điều kiện {@code occurred_at} thì truy vấn quét mọi partition — đúng thứ mà
     * partition sinh ra để tránh. Nên mặc định là 30 ngày gần nhất chứ không phải "tất cả".
     */
    public record SearchRequest(
            Instant from, Instant to, String module, String entityType, Long entityId, Long actorUserId) {

        public Instant effectiveTo() {
            return to != null ? to : Instant.now();
        }

        public Instant effectiveFrom() {
            return from != null ? from : effectiveTo().minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);
        }
    }

    public record AuditLogView(
            long seq,
            Instant occurredAt,
            Long actorUserId,
            String actorUsername,
            String module,
            String entityType,
            Long entityId,
            UUID entityPublicId,
            AuditAction action,
            String oldValue,
            String newValue,
            String ipAddress,
            String traceId) {

        public static AuditLogView of(AuditLog row) {
            return new AuditLogView(
                    row.getSeq(),
                    row.getOccurredAt(),
                    row.getActorUserId(),
                    row.getActorUsername(),
                    row.getModule(),
                    row.getEntityType(),
                    row.getEntityId(),
                    row.getEntityPublicId(),
                    row.getAction(),
                    row.getOldValue(),
                    row.getNewValue(),
                    row.getIpAddress(),
                    row.getTraceId());
        }
    }

    /** @param intact true = chuỗi nguyên vẹn; {@code breaks} rỗng khi đó */
    public record ChainVerificationView(
            boolean intact, long minSeq, long maxSeq, long totalRecords, List<ChainBreakView> breaks) {

        public static ChainVerificationView of(AuditService.ChainVerification result) {
            return new ChainVerificationView(
                    result.intact(),
                    result.minSeq(),
                    result.maxSeq(),
                    result.totalRecords(),
                    result.breaks().stream()
                            .map(b -> new ChainBreakView(b.seq(), b.occurredAt(), b.reason()))
                            .toList());
        }
    }

    /** @param reason mô tả tiếng Việt do hàm trong DB sinh — hiển thị thẳng cho người dùng */
    public record ChainBreakView(long seq, Instant occurredAt, String reason) {}
}
