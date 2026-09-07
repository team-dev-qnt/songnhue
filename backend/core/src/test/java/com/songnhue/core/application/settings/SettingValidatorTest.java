package com.songnhue.core.application.settings;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Field;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.domain.settings.Setting;

/**
 * Giá trị rác lọt qua đây sẽ không gây lỗi lúc lưu — nó gây lỗi lúc đọc, mà nơi đọc đã chọn cách
 * lặng lẽ dùng giá trị dự phòng. Nên đây là chốt chặn duy nhất báo được cho đúng người.
 */
class SettingValidatorTest {

    private final SettingValidator validator = new SettingValidator(new ObjectMapper());

    /** Dựng {@link Setting} bằng reflection — entity cố ý không có setter công khai. */
    private static Setting setting(String key, String type, String validation) {
        try {
            Setting s = Setting.class.getDeclaredConstructor().newInstance();
            set(s, "settingKey", key);
            set(s, "valueType", type);
            set(s, "validation", validation);
            return s;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void set(Setting target, String field, Object value) throws ReflectiveOperationException {
        Field f = Setting.class.getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, value);
    }

    @Test
    @DisplayName("Giá trị rỗng luôn hợp lệ — nghĩa là quay về mặc định")
    void blankMeansReset() {
        assertThatCode(() -> validator.validate(setting("a.b", "INTEGER", "min=5"), ""))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(setting("a.b", "INTEGER", "min=5"), null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Sai kiểu bị chặn ngay")
    void rejectsWrongType() {
        assertThatThrownBy(() -> validator.validate(setting("a.b", "INTEGER", null), "mười"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validate(setting("a.b", "TIME", null), "25:00"))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validate(setting("a.b", "JSON", null), "{khong-phai-json"))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "1", "on", "có"})
    @DisplayName("⚠ Boolean chỉ nhận true/false — 'yes' và '1' phải bị chặn")
    void rejectsLooseBooleans(String value) {
        // Boolean.parseBoolean nhận mọi thứ và trả false. Để lọt "yes" vào DB thì tính năng tương
        // ứng lặng lẽ TẮT, trong khi Admin tin rằng mình vừa bật nó.
        assertThatThrownBy(() -> validator.validate(setting("a.b", "BOOLEAN", null), value))
                .isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "TRUE", "True ", " false", "False"})
    @DisplayName("Hoa thường và khoảng trắng thừa vẫn nhận — chỉ cách viết mới bị siết, không phải kiểu gõ")
    void acceptsBooleanSpellings(String value) {
        assertThatCode(() -> validator.validate(setting("a.b", "BOOLEAN", null), value))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Cron sai cú pháp bị chặn — job sai cron không bao giờ chạy mà cũng không báo lỗi")
    void rejectsBadCron() {
        assertThatThrownBy(() -> validator.validate(setting("hydro.cron", "CRON", null), "*/2 * * *"))
                .isInstanceOf(ValidationException.class);
        assertThatCode(() -> validator.validate(setting("hydro.cron", "CRON", null), "45 1/2 * * * *"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Luật min/max theo đúng dữ liệu seed")
    void enforcesMinMax() {
        Setting minLength = setting("security.password.min-length", "INTEGER", "min=8;max=64");

        assertThatCode(() -> validator.validate(minLength, "10")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(minLength, "4")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> validator.validate(minLength, "100")).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Luật in= chỉ nhận giá trị trong danh sách")
    void enforcesAllowedValues() {
        Setting mode = setting("hr.seniority.base", "STRING", "in=HIRE_DATE,CONTRACT_DATE");

        assertThatCode(() -> validator.validate(mode, "HIRE_DATE")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(mode, "OTHER")).isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("Luật lạ được bỏ qua, không khoá cứng màn hình cấu hình")
    void ignoresUnknownRule() {
        assertThatCode(() -> validator.validate(setting("a.b", "STRING", "luat-moi=abc"), "gì đó"))
                .doesNotThrowAnyException();
    }
}
