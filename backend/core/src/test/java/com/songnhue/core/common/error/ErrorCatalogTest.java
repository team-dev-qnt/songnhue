package com.songnhue.core.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Giữ cho danh mục mã lỗi và file message luôn khớp nhau (T4.3).
 *
 * <p>Test này là chốt chặn ở CI: thiếu message thì người dùng nhận về khoá thô kiểu
 * {@code OPS-2001}, còn khoá thừa nghĩa là ai đó xoá mã lỗi mà quên dọn file — cả hai chỉ lộ ra ở
 * production nếu không chặn từ đây.
 */
class ErrorCatalogTest {

    private static final String MESSAGE_FILE = "/error-messages.properties";

    private static Properties loadMessages() throws Exception {
        Properties props = new Properties();
        try (InputStream in = ErrorCatalogTest.class.getResourceAsStream(MESSAGE_FILE)) {
            assertThat(in).as("Không tìm thấy %s trong classpath", MESSAGE_FILE).isNotNull();
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }

    @Test
    @DisplayName("Mọi ErrorCode đều có message tiếng Việt")
    void everyCodeHasMessage() throws Exception {
        Properties messages = loadMessages();

        Set<String> missing = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::messageKey)
                .filter(key -> !messages.containsKey(key))
                .collect(Collectors.toSet());

        assertThat(missing)
                .as("Thiếu message cho các mã sau — thêm vào error-messages_vi.properties")
                .isEmpty();
    }

    @Test
    @DisplayName("File message không có khoá thừa")
    void noOrphanMessageKeys() throws Exception {
        Set<String> declared =
                Arrays.stream(ErrorCode.values()).map(ErrorCode::messageKey).collect(Collectors.toSet());

        Set<String> orphans = messagesKeySet(loadMessages()).stream()
                .filter(key -> !declared.contains(key))
                .collect(Collectors.toSet());

        assertThat(orphans)
                .as("Khoá không còn ErrorCode tương ứng — xoá khỏi file message")
                .isEmpty();
    }

    @Test
    @DisplayName("Message không được rỗng và không được lộ chi tiết kỹ thuật")
    void messagesAreUserFacing() throws Exception {
        Properties messages = loadMessages();

        for (ErrorCode code : ErrorCode.values()) {
            String message = messages.getProperty(code.messageKey());
            assertThat(message).as("Message của %s", code.code()).isNotBlank();

            // Người dùng cuối không bao giờ cần đọc những thứ này (conventions.md §2.2)
            assertThat(message.toLowerCase())
                    .as("Message của %s lộ chi tiết kỹ thuật", code.code())
                    .doesNotContain("exception")
                    .doesNotContain("nullpointer")
                    .doesNotContain("sql")
                    .doesNotContain("stacktrace")
                    .doesNotContain("com.songnhue");
        }
    }

    @Test
    @DisplayName("Mã lỗi đúng định dạng <PREFIX>-<4 số> và không trùng nhau")
    void codeFormatIsConsistent() {
        Set<String> seen = new java.util.HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.code()).matches("^(SYS|AUTH|CMS|OPS|HYD|HR|ADM)-\\d{4}$");
            assertThat(seen.add(code.code()))
                    .as("Mã %s bị khai báo trùng", code.code())
                    .isTrue();
            assertThat(code.status())
                    .as("Mã %s chưa gán HTTP status", code.code())
                    .isNotNull();
        }
    }

    private static Set<String> messagesKeySet(Properties props) {
        return props.stringPropertyNames();
    }
}
