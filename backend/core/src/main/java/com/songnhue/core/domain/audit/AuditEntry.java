package com.songnhue.core.domain.audit;

import java.util.UUID;

/**
 * Một dòng nhật ký chuẩn bị ghi.
 *
 * <p>Cố ý <b>không</b> có {@code seq}, {@code prevHash}, {@code hash}: ba giá trị đó do trigger
 * trong DB cấp. App không có quyền {@code UPDATE} trên {@code audit_chain_head} nên dù có ghi lên
 * cũng bị ghi đè — chuỗi hash không thể bị giả từ phía ứng dụng (quyết định WS-2). Đặt chúng vào
 * record này chỉ tạo ảo giác rằng mã Java kiểm soát được chuỗi.
 */
public record AuditEntry(
        String module,
        String entityType,
        Long entityId,
        UUID entityPublicId,
        AuditAction action,
        String oldValue,
        String newValue,
        Long orgUnitId) {}
