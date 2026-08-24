package com.songnhue.core.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.AppException;
import com.songnhue.core.common.exception.BusinessRuleException;

/** Băm mật khẩu và chính sách độ mạnh (T5.7, M5.15). */
@ExtendWith(MockitoExtension.class)
class PasswordPolicyServiceTest {

    @Mock
    private SettingService settings;

    private PasswordPolicyService service;

    @BeforeEach
    void setUp() {
        lenient().when(settings.getInt(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        lenient().when(settings.getBoolean(anyString(), anyBoolean())).thenAnswer(i -> i.getArgument(1));
        service = new PasswordPolicyService(settings);
    }

    @Test
    @DisplayName("BCrypt cost 12 — hash bắt đầu bằng $2a$12$")
    void usesBcryptCost12() {
        String hash = service.hash("MatKhauTot123");

        // Hạ cost xuống là hạ chi phí dò mật khẩu đúng theo cấp số nhân — mỗi bậc gấp đôi
        assertThat(hash).startsWith("$2a$12$");
        assertThat(service.matches("MatKhauTot123", hash)).isTrue();
        assertThat(service.matches("MatKhauSai123", hash)).isFalse();
    }

    @Test
    @DisplayName("Cùng mật khẩu, hai lần băm ra hai chuỗi khác nhau (có muối)")
    void hashesAreSalted() {
        // Không có muối thì hai người đặt cùng mật khẩu sẽ có cùng hash — lộ ngay khi bảng bị lấy đi
        assertThat(service.hash("MatKhauTot123")).isNotEqualTo(service.hash("MatKhauTot123"));
    }

    @Test
    @DisplayName("Giá trị '!' của tài khoản chưa kích hoạt không khớp mật khẩu nào")
    void placeholderHashNeverMatches() {
        assertThat(service.matches("", "!")).isFalse();
        assertThat(service.matches("!", "!")).isFalse();
        assertThat(service.matches("MatKhauTot123", "!")).isFalse();
    }

    @Test
    @DisplayName("Mật khẩu đạt chuẩn thì qua")
    void acceptsCompliantPassword() {
        assertThatCode(() -> service.validate("ThuyLoi2026x", "nva")).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "từ chối: {0}")
    @ValueSource(strings = {"ngan1", "chicochu", "12345678901", "Abc123"})
    @DisplayName("Từ chối mật khẩu quá ngắn hoặc thiếu chữ/số")
    void rejectsWeakPasswords(String weak) {
        assertThatThrownBy(() -> service.validate(weak, "nva"))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(e -> ((AppException) e).errorCode())
                .isEqualTo(ErrorCode.AUTH_0006);
    }

    @Test
    @DisplayName("Từ chối mật khẩu chứa tên đăng nhập")
    void rejectsPasswordContainingUsername() {
        // "admin/admin123" là cặp bị dò đầu tiên trong mọi đợt tấn công tự động
        assertThatThrownBy(() -> service.validate("Admin123456", "admin")).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("Lỗi trả về chỉ ra trường sai — KHÔNG kèm mật khẩu bị từ chối")
    void errorDetailNeverLeaksThePassword() {
        String secret = "abc";
        try {
            service.validate(secret, "nva");
        } catch (BusinessRuleException e) {
            assertThat(e.details()).isNotEmpty();
            assertThat(e.details()).allSatisfy(detail -> {
                assertThat(detail.field()).isEqualTo("newPassword");
                // rejectedValue đi thẳng ra response — mật khẩu lọt vào đây là lộ ra ngoài
                assertThat(detail.rejectedValue()).isNull();
            });
        }
    }

    @Test
    @DisplayName("Độ dài tối thiểu đọc từ bảng settings, không hard-code (quy tắc 12)")
    void minLengthComesFromSettings() {
        when(settings.getInt(SettingKeys.PASSWORD_MIN_LENGTH, SettingKeys.DEFAULT_PASSWORD_MIN_LENGTH))
                .thenReturn(16);

        assertThatThrownBy(() -> service.validate("ThuyLoi2026x", "nva")).isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> service.validate("ThuyLoiSongNhue2026", "nva")).doesNotThrowAnyException();
    }
}
