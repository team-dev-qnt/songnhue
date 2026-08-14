package com.songnhue.core.common.persistence;

import java.util.Optional;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.filter.AuditContext;

/**
 * Cung cấp "ai đang thao tác" cho JPA auditing, để {@code created_by} / {@code updated_by} tự điền.
 *
 * <p>Lấy từ {@link AuditContext} chứ không từ SecurityContext: job chạy nền không có người dùng
 * đăng nhập, và lúc đó trả {@link Optional#empty()} là đúng — cột để trống nghĩa là "hệ thống làm",
 * không phải gán bừa cho một tài khoản nào đó.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<Long> {

    @Override
    public Optional<Long> getCurrentAuditor() {
        return Optional.ofNullable(AuditContext.get().userId());
    }
}
