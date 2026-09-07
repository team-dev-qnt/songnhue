package com.songnhue.core.common.audit;

import java.util.List;

import org.springframework.stereotype.Component;

import com.songnhue.core.domain.audit.AuditEntry;
import com.songnhue.core.infra.audit.AuditLogWriter;

/**
 * Ghi một dòng nhật ký <b>ngay tại chỗ</b>, trong đúng giao dịch đang chạy.
 *
 * <p><b>Vì sao ghi ngay chứ không gom lại rồi ghi một lượt trước khi commit.</b> Bản đầu của lớp này
 * gom vào bộ đệm và đăng ký {@code TransactionSynchronization} để ghi ở {@code beforeCommit}. Cách
 * đó <b>không chạy</b>, và im lặng: Spring gọi {@code triggerBeforeCommit(...)} <i>trước</i>
 * {@code doCommit(...)}, mà Hibernate chỉ flush (và bắn sự kiện thay đổi entity) bên trong
 * {@code doCommit}. Nghĩa là lúc bộ lắng nghe đăng ký synchronization thì các callback
 * {@code beforeCommit} đã chạy xong từ trước — bộ đệm không bao giờ được xả.
 *
 * <p>Triệu chứng lúc chạy thử: đăng nhập sai làm {@code users.failed_login_count} tăng và
 * {@code updated_at} đổi, nhưng {@code audit_logs} không có dòng nào và <b>không lỗi nào</b> trong
 * log. Đây là lý do WS-6 kiểm chứng bằng chạy thật chứ không dừng ở test đơn vị.
 *
 * <p>Ghi ngay vẫn giữ nguyên bất biến quan trọng nhất: câu INSERT chạy trên <b>cùng connection và
 * cùng giao dịch</b> với thay đổi nghiệp vụ, nên giao dịch rollback thì dòng nhật ký cũng biến mất.
 * Không bao giờ có chuyện nhật ký nói về một thay đổi chưa từng xảy ra, hay một thay đổi không để
 * lại dấu vết.
 *
 * <p>Cái mất đi là gộp nhiều dòng vào một lệnh. Với tải của hệ này (vài nghìn bản ghi/ngày, mỗi
 * request sửa vài entity) thì không đáng kể — và đổi lại là một cơ chế không có chỗ nào để hỏng
 * lặng lẽ.
 */
@Component
public class AuditCollector {

    private final AuditLogWriter writer;

    public AuditCollector(AuditLogWriter writer) {
        this.writer = writer;
    }

    public void collect(AuditEntry entry) {
        writer.write(List.of(entry));
    }
}
