package com.songnhue.core.application.settings;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.domain.settings.Setting;

/**
 * Kiểm tra giá trị tham số trước khi ghi — T6.11.
 *
 * <p><b>Vì sao phải chặt tay ở đây.</b> Bảng {@code settings} điều khiển những thứ rất thật: chu kỳ
 * gọi API thuỷ văn, ngưỡng khoá tài khoản, số ngày giữ nhật ký. Một giá trị rác lọt vào không làm
 * hỏng gì ngay lúc lưu — nó hỏng lúc <i>đọc</i>, mà nơi đọc thì đã chọn cách "sai định dạng thì dùng
 * giá trị dự phòng và ghi log" để đường đăng nhập không bao giờ sập. Nghĩa là Admin sửa xong, giao
 * diện báo thành công, và tham số vẫn giữ giá trị cũ trong im lặng. Chặn ngay lúc ghi là chỗ duy
 * nhất báo được cho đúng người, đúng lúc.
 *
 * <p>Hai tầng: kiểu ({@code value_type}) rồi tới luật ({@code validation}). Luật hiện dùng ba dạng,
 * ghép bằng dấu {@code ;} — {@code min=8}, {@code max=64}, {@code in=A,B,C}.
 */
@Component
public class SettingValidator {

    private final ObjectMapper objectMapper;

    public SettingValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @throws ValidationException khi giá trị sai kiểu hoặc vi phạm luật */
    public void validate(Setting setting, String rawValue) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.isEmpty()) {
            // Rỗng = quay về giá trị mặc định của danh mục, luôn hợp lệ.
            return;
        }
        checkType(setting, value);
        checkRules(setting, value);
    }

    private void checkType(Setting setting, String value) {
        try {
            switch (setting.getValueType()) {
                case "INTEGER" -> Long.parseLong(value);
                case "DECIMAL" -> new BigDecimal(value);
                case "BOOLEAN" -> requireBoolean(value);
                case "TIME" -> LocalTime.parse(value);
                case "DATE" -> LocalDate.parse(value);
                case "DURATION" -> Duration.parse(value);
                case "JSON" -> objectMapper.readTree(value);
                case "CRON" -> requireCron(value);
                default -> {
                    // STRING, TEXT, HTML, HTML_EMBED — không có ràng buộc kiểu ở đây.
                    // ⚠ Hai kiểu HTML KHÔNG được kiểm ở tầng này một cách cố ý: chúng không "sai
                    // định dạng", chúng chỉ chứa thứ không được phép chạy. Việc đó là của
                    // SettingService.khuTrung() — lọc rồi lưu phần sạch, chứ không từ chối cả lượt
                    // sửa. Từ chối thì người soạn dán một khối HTML lấy từ nơi khác về là gặp lỗi
                    // mà không biết bỏ thẻ nào, và họ sẽ đi tìm đường khác để lưu.
                }
            }
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException(ErrorCode.ADM_2006, setting.getSettingKey(), setting.getValueType());
        }
    }

    private static void requireBoolean(String value) {
        // Boolean.parseBoolean nhận mọi thứ và trả false — "yes" hay "1" sẽ lặng lẽ thành false,
        // đúng kiểu sai mà không ai biết cho tới khi tính năng tương ứng không chạy.
        if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
            throw new ValidationException(ErrorCode.ADM_2006, "boolean", "true/false");
        }
    }

    private static void requireCron(String value) {
        // Cron sai cú pháp thì job không bao giờ chạy, và không có lỗi nào ngoài sự vắng mặt —
        // đúng thứ khó phát hiện nhất trong nhóm tham số này.
        if (!CronExpression.isValidExpression(value)) {
            throw new ValidationException(ErrorCode.ADM_2006, "cron", value);
        }
    }

    private void checkRules(Setting setting, String value) {
        String rules = setting.getValidation();
        if (rules == null || rules.isBlank()) {
            return;
        }
        for (String rule : rules.split(";")) {
            String[] parts = rule.split("=", 2);
            if (parts.length != 2) {
                continue;
            }
            String name = parts[0].trim();
            String arg = parts[1].trim();
            switch (name) {
                case "min" -> requireAtLeast(setting, value, arg);
                case "max" -> requireAtMost(setting, value, arg);
                case "in" -> requireOneOf(setting, value, arg);
                default -> {
                    // Luật lạ: bỏ qua thay vì chặn. Thêm luật mới ở migration mà quên cài ở đây thì
                    // Admin vẫn sửa được tham số, chỉ là chưa có kiểm tra — tốt hơn là khoá cứng
                    // toàn bộ màn hình cấu hình.
                }
            }
        }
    }

    private static void requireAtLeast(Setting setting, String value, String arg) {
        if (new BigDecimal(value).compareTo(new BigDecimal(arg)) < 0) {
            throw new ValidationException(ErrorCode.ADM_2006, setting.getSettingKey(), "≥ " + arg);
        }
    }

    private static void requireAtMost(Setting setting, String value, String arg) {
        if (new BigDecimal(value).compareTo(new BigDecimal(arg)) > 0) {
            throw new ValidationException(ErrorCode.ADM_2006, setting.getSettingKey(), "≤ " + arg);
        }
    }

    private static void requireOneOf(Setting setting, String value, String arg) {
        List<String> allowed = Arrays.stream(arg.split(",")).map(String::trim).toList();
        if (!allowed.contains(value)) {
            throw new ValidationException(ErrorCode.ADM_2006, setting.getSettingKey(), String.join(" / ", allowed));
        }
    }
}
