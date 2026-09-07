package com.songnhue.core.spi;

/**
 * Gửi <b>một</b> thư tới một địa chỉ email bất kỳ — kể cả địa chỉ <b>ngoài</b> hệ thống.
 *
 * <h2>⚠⚠ Vì sao cổng này tồn tại bên cạnh {@link NotificationPort}</h2>
 *
 * <p>{@code NotificationPort} gửi cho <b>người dùng của hệ thống</b>: nó phân giải người nhận theo
 * quyền hoặc theo đơn vị, ghi một dòng {@code notification_recipients} khoá theo {@code user_id},
 * rồi job nền tra địa chỉ email từ hồ sơ tài khoản. Toàn bộ chuỗi ấy <b>⛔ không dùng được</b> cho
 * thư xác nhận gửi tới người dân đã điền biểu mẫu liên hệ: họ ⛔ không có {@code user_id}, và tạo
 * một tài khoản ma cho mỗi lượt gửi biểu mẫu là một ý tưởng tệ hơn nhiều.
 *
 * <h2>⛔ Cổng này ⛔ KHÔNG phải đường tắt cho thông báo nội bộ</h2>
 *
 * <p>Gửi cho người dùng của hệ thống thì <b>phải</b> đi {@link NotificationPort}: chỉ đường ấy mới
 * có hộp thư trên giao diện, mới ghi được "đã gửi / đã đọc", mới tôn trọng công tắc bật-tắt kênh
 * trong {@code settings}, và mới thử lại được. Cổng này cố ý <b>không</b> có gì trong số đó — nó là
 * một lượt gọi SMTP trần, và nơi gọi phải tự lo hàng đợi cùng số lần thử.
 *
 * <h2>Vì sao trả {@code boolean} chứ ⛔ không phải {@code void}</h2>
 *
 * <p>Hai kết cục khác nhau và nơi gọi phải phân biệt được: <i>chưa cấu hình SMTP</i> ({@code false},
 * ⛔ không phải lỗi — môi trường dev chạy như vậy suốt) và <i>máy chủ thư từ chối</i> (ném, đáng để
 * hàng đợi thử lại). Gộp cả hai thành {@code void} là đúng hình dạng luật 9: một khẳng định ⛔ không
 * phân biệt được hai trạng thái thì ⛔ không khẳng định gì.
 */
public interface MailPort {

    /**
     * @param linkUrl đường dẫn đính kèm cuối thư; có thể {@code null}
     * @return {@code false} khi <b>chưa cấu hình máy chủ thư</b> — ⛔ không gửi, và ⛔ không phải lỗi
     * @throws org.springframework.mail.MailException khi máy chủ thư từ chối
     */
    boolean send(String toAddress, String subject, String body, String linkUrl);
}
