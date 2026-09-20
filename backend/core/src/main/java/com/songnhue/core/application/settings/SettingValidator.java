package com.songnhue.core.application.settings;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.util.DiaChiLienKet;
import com.songnhue.core.domain.settings.Setting;

import tools.jackson.databind.ObjectMapper;

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

    /** Mã màu nhận diện — xem {@link #requireMaMau}. */
    private static final Pattern MA_MAU = Pattern.compile("^#[0-9a-fA-F]{6}$");

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
                case "URL" -> requireLienKetAnToan(value);
                case "COLOR" -> requireMaMau(value);
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

    private static void requireLienKetAnToan(String value) {
        // ⛔⛔ T63.4. Những khoá mang kiểu này đi thẳng vào `href` ở cổng công khai, và React 18.3.1
        //    chỉ CẢNH BÁO chứ ⛔ chặn `javascript:` (chặn từ React 19) ⇒ bấm là chạy.
        //
        // ⚠ Thông điệp KHÔNG chép lại giá trị người dùng nhập. Mọi nhánh khác ở lớp này đều chép
        //   (`cron`, `boolean`) và điều đó hợp lý với chúng — còn ở đây giá trị bị từ chối CHÍNH LÀ
        //   một đoạn mã tấn công, nên ném nó qua lớp thông báo, lớp nhật ký và màn hình quản trị là
        //   chở nó đi xa hơn chứ ⛔ phải chặn nó lại. Người quản trị ⛔ cần đọc lại thứ họ vừa gõ;
        //   họ cần biết *dạng nào được chấp nhận*.
        if (!DiaChiLienKet.anToan(value)) {
            throw new ValidationException(
                    ErrorCode.ADM_2006, "địa chỉ liên kết", "http://, https://, mailto:, tel:, /đường-dẫn hoặc #neo");
        }
    }

    private static void requireCron(String value) {
        // Cron sai cú pháp thì job không bao giờ chạy, và không có lỗi nào ngoài sự vắng mặt —
        // đúng thứ khó phát hiện nhất trong nhóm tham số này.
        if (!CronExpression.isValidExpression(value)) {
            throw new ValidationException(ErrorCode.ADM_2006, "cron", value);
        }
    }

    /**
     * Mã màu nhận diện — đúng {@code #} + 6 chữ số hex, ⛔ nhận gì khác.
     *
     * <h3>Vì sao chặt tới mức ⛔ nhận cả {@code #fff}</h3>
     *
     * Giá trị này đi thẳng vào một khối {@code <style>} của **cổng công khai**
     * ({@code mauThuongHieu.ts}). Một chuỗi tuỳ ý ở đó là một đường tiêm CSS: dấu {@code ;} đóng
     * khai báo, {@code &#125;} đóng luật, và phần sau viết được luật mới — đủ để phủ một lớp lên
     * toàn trang. Nên vị từ ở đây ⛔ phải *"CSS có hiểu ⛔"* mà là *"có đúng hình dạng ta cho phép
     * ⛔"*; {@code rgb()}, {@code hsl()} và tên màu đều bị loại dù CSS hiểu cả ba.
     *
     * <p>Dạng 3 ký tự bị loại vì một lý do khác: ô nhập ở admin mô tả *"6 chữ số"*, và nhận thêm
     * một dạng nghĩa là mọi bài kiểm hai phía phải nhớ hai dạng (quy tắc 14).
     *
     * <p>⚠ Đây là chốt chặn THỨ NHẤT, ⛔ phải chốt duy nhất — {@code mauThuongHieu.ts} lọc lại một
     * lượt nữa lúc dựng trang. ⛔ phải thừa: một giá trị có thể vào bảng {@code settings} bằng
     * đường khác (khôi phục sao lưu, nhập cấu hình), và cổng công khai ⛔ được tin bảng ấy vô điều
     * kiện. Quy tắc 12 — đặt bảo đảm ở chỗ dữ liệu ĐI QUA.
     */
    private static void requireMaMau(String value) {
        if (!MA_MAU.matcher(value).matches()) {
            throw new ValidationException(ErrorCode.ADM_2006, "mã màu", "#rrggbb — ví dụ #1758bf");
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
