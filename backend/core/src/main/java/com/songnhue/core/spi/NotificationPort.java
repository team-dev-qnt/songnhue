package com.songnhue.core.spi;

import java.util.List;

/**
 * Thông báo đa kênh — pattern P4.
 *
 * <p>Ghi DB rồi trả về ngay; việc gửi email do job nền làm. Nhờ vậy giao dịch nghiệp vụ không phải
 * chờ SMTP, mà thông báo vẫn <b>cùng sống chết</b> với thay đổi đã gây ra nó: giao dịch rollback thì
 * thông báo cũng biến mất.
 */
public interface NotificationPort {

    void notify(NotifyRequest request);

    /** Gửi tới một danh sách người nhận cụ thể, bỏ qua bước phân giải theo đơn vị. */
    void broadcast(NotifyRequest request, List<Long> userIds);
}
