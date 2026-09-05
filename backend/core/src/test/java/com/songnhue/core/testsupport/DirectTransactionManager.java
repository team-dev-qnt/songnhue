package com.songnhue.core.testsupport;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

/**
 * Quản lý transaction giả cho unit test: chạy thẳng, không mở transaction thật.
 *
 * <p>Các dịch vụ xác thực dùng {@code TransactionTemplate} với {@code REQUIRES_NEW} để việc ghi
 * nhật ký bảo mật và bộ đếm đăng nhập sai không bị cuốn theo rollback. Unit test không có CSDL, nên
 * ở đây chỉ cần một hiện thực trả về trạng thái rỗng để {@code TransactionTemplate} gọi được hàm
 * bên trong.
 *
 * <p>Hành vi transaction thật (rollback có làm mất bản ghi không) được kiểm ở tầng integration
 * test với Testcontainers — WS-10 / T10.1.
 */
public class DirectTransactionManager implements PlatformTransactionManager {

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
        // không có gì để ghi
    }

    @Override
    public void rollback(TransactionStatus status) {
        // không có gì để hoàn tác
    }
}
