package com.songnhue.core.spi;

import java.time.Instant;
import java.util.List;

/**
 * Đọc nhật ký thay đổi của <b>một</b> bản ghi nghiệp vụ.
 *
 * <h2>⛔ Vì sao module nghiệp vụ KHÔNG dựng bảng lịch sử riêng</h2>
 *
 * CN-02.7 gọi nó là "nhật ký thay đổi hồ sơ công trình", và cám dỗ tự nhiên là tạo một bảng
 * {@code construction_history}. Đừng. {@code audit_logs} đã ghi đủ giá trị cũ/mới cho mọi entity mang
 * {@code @Audited}, ghi tự động ở tầng Hibernate nên không có nhánh nào quên ghi, lại còn có chuỗi
 * băm chống sửa. Một bảng lịch sử thứ hai chỉ tạo ra hai nguồn sự thật, và bảng viết tay sẽ là bảng
 * thiếu dòng — vì nó phụ thuộc vào việc lập trình viên nhớ gọi.
 *
 * <h2>⚠ Vì sao bắt buộc truyền khoảng thời gian</h2>
 *
 * {@code audit_logs} phân mảnh theo tháng. Truy vấn không có điều kiện {@code occurred_at} buộc
 * PostgreSQL quét mọi partition — đúng thứ mà partition sinh ra để tránh, và với retention 5 năm thì
 * đó là 60 bảng. Nơi gọi luôn biết một mốc đầu chặt hơn nhiều: <b>ngày tạo của chính bản ghi</b>.
 */
public interface AuditQueryPort {

    /** Số dòng tối đa một lượt — lịch sử của một hồ sơ đếm bằng chục, không phải bằng nghìn. */
    int MAX_ENTRIES = 200;

    /**
     * @param module mã module, VD {@code ops}
     * @param entityType đúng giá trị khai ở {@code @Audited(entityType = …)}
     * @param entityId khoá nội bộ của bản ghi
     * @param from thường là {@code createdAt} của chính bản ghi
     * @param limit tự kẹp về {@link #MAX_ENTRIES}
     * @return mới nhất trước
     */
    List<AuditEntryView> historyOf(
            String module, String entityType, Long entityId, Instant from, Instant to, int limit);
}
