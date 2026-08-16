package com.songnhue.core.application.maintenance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.SecurityEventService;
import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.domain.security.SecurityEventType;

/**
 * Chế độ bảo trì — chặn mọi thao tác ghi trong lúc khôi phục dữ liệu (T7.6, M5.11).
 *
 * <p><b>Vì sao cần.</b> Khôi phục là ghi đè toàn bộ CSDL. Một request ghi lọt vào giữa chừng sẽ hoặc
 * biến mất khi bản dump ghi đè lên, hoặc tệ hơn là còn lại một nửa — người dùng thấy "lưu thành
 * công" cho dữ liệu không tồn tại sau đó. Chặn ghi là cách duy nhất để câu "khôi phục về đúng bản
 * dump" còn đúng.
 *
 * <p><b>Vì sao cờ nằm trong bảng {@code settings} chứ không phải một biến trong bộ nhớ.</b> Ba lý
 * do, lý do thứ ba là lý do thật: cờ trong bộ nhớ mất khi khởi động lại — mà khôi phục hỏng giữa
 * chừng thì việc đầu tiên người ta làm là khởi động lại app, và hệ thống mở cửa cho ghi trở lại
 * đúng lúc dữ liệu đang dở dang.
 *
 * <p>⚠ <b>Trạng thái này đọc qua {@link SettingService} nên có cache 60 giây.</b> Bật/tắt bằng hai
 * phương thức ở đây thì cache được xoá ngay. Sửa thẳng bằng SQL trên DB thì phải chờ hết TTL — ghi ở
 * {@code docs/runbook/khoi-phuc-du-lieu.md}.
 */
@Service
public class MaintenanceModeService {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeService.class);

    private final SettingService settings;
    private final SecurityEventService securityEvents;

    public MaintenanceModeService(SettingService settings, SecurityEventService securityEvents) {
        this.settings = settings;
        this.securityEvents = securityEvents;
    }

    public boolean isEnabled() {
        return settings.getBoolean(SettingKeys.MAINTENANCE_MODE, false);
    }

    /**
     * Bật chế độ bảo trì.
     *
     * @param reason đi vào nhật ký bảo mật — "hệ thống đang bảo trì" mà không ai nhớ vì sao là tình
     *     huống hay gặp nhất khi cờ này bị bỏ quên ở trạng thái bật
     */
    public void enable(String reason) {
        settings.update(SettingKeys.MAINTENANCE_MODE, "true");
        log.warn("BẬT chế độ bảo trì — mọi thao tác ghi bị chặn. Lý do: {}", reason);
        recordEvent("MAINTENANCE_ON", reason);
    }

    public void disable(String reason) {
        settings.update(SettingKeys.MAINTENANCE_MODE, "false");
        log.warn("TẮT chế độ bảo trì — hệ thống nhận ghi trở lại. Lý do: {}", reason);
        recordEvent("MAINTENANCE_OFF", reason);
    }

    private void recordEvent(String action, String reason) {
        AuthenticatedUser user = AuthContext.current().orElse(null);
        securityEvents.record(
                SecurityEventType.MAINTENANCE_MODE_CHANGED,
                user == null ? "system" : user.username(),
                user == null ? null : user.userId(),
                ClientInfo.unknown(),
                "{\"action\":\"" + action + "\",\"reason\":" + jsonString(reason) + "}");
    }

    /** Lý do do người dùng gõ vào — phải thoát chuỗi, nếu không một dấu ngoặc kép làm hỏng cột jsonb. */
    private static String jsonString(String raw) {
        if (raw == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : raw.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
