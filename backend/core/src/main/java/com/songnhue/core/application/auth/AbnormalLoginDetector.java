package com.songnhue.core.application.auth;

import java.time.Instant;
import java.time.LocalTime;

import org.springframework.stereotype.Service;

import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.security.SecurityEventType;

/**
 * Cảnh báo đăng nhập bất thường — M5.16 (T5.14).
 *
 * <p>Tách khỏi {@link AuthService} vì đây là một mối quan tâm riêng và sẽ còn lớn thêm: hiện mới xét
 * khung giờ, về sau còn "nhiều tài khoản khác nhau bị dò từ cùng một IP", "đăng nhập từ dải mạng lạ".
 * Nhồi hết vào luồng đăng nhập thì mỗi lần thêm một luật là sửa vào đúng đoạn mã nhạy cảm nhất hệ
 * thống.
 *
 * <p>Ở đây chỉ <b>ghi sự kiện</b>. Việc bắn cảnh báo tới Admin gần thời gian thực do luồng
 * {@code security_events} → Prometheus/Grafana lo (WS-7 / T7.10) — nhờ vậy quy tắc cảnh báo sửa được
 * mà không phải build lại ứng dụng.
 */
@Service
public class AbnormalLoginDetector {

    private static final LocalTime DEFAULT_OFFICE_START = LocalTime.of(8, 0);
    private static final LocalTime DEFAULT_OFFICE_END = LocalTime.of(17, 0);

    private final SettingService settings;
    private final SecurityEventService securityEvents;

    public AbnormalLoginDetector(SettingService settings, SecurityEventService securityEvents) {
        this.settings = settings;
        this.securityEvents = securityEvents;
    }

    /**
     * Xét một lần đăng nhập thành công có gì bất thường không.
     *
     * <p>So theo <b>giờ Việt Nam</b> chứ không phải UTC: dữ liệu lưu UTC nhưng "giờ hành chính" là
     * khái niệm của người dùng (quy tắc 1 của dự án). Bỏ bước đổi múi giờ thì mọi lần đăng nhập buổi
     * chiều đều bị đánh dấu bất thường — cảnh báo kêu suốt ngày rồi không ai đọc nữa.
     */
    public void inspectSuccessfulLogin(User user, ClientInfo client, Instant now) {
        LocalTime start = settings.getTime(SettingKeys.OFFICE_HOURS_START, DEFAULT_OFFICE_START);
        LocalTime end = settings.getTime(SettingKeys.OFFICE_HOURS_END, DEFAULT_OFFICE_END);
        LocalTime localNow = now.atZone(DateTimeUtils.ZONE_VN).toLocalTime();

        if (localNow.isBefore(start) || localNow.isAfter(end)) {
            securityEvents.record(
                    SecurityEventType.LOGIN_OUTSIDE_OFFICE_HOURS,
                    user.getUsername(),
                    user.getId(),
                    client,
                    "{\"localTime\":\"" + localNow + "\",\"officeHours\":\"" + start + "-" + end + "\"}");
        }
    }
}
