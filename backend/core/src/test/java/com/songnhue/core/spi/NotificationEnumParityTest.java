package com.songnhue.core.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;

/**
 * Canh hai cặp enum bị nhân bản có chủ đích giữa {@code core.spi} và {@code core.domain.notification}.
 *
 * <p><b>Vì sao có bản sao.</b> Enum ở {@code domain} ánh xạ xuống cột trong CSDL. Nếu module nghiệp
 * vụ import thẳng nó thì hợp đồng SPI bị trói vào mô hình lưu trữ của {@code core} — mà mục đích của
 * SPI là để {@code core} đổi bên trong không làm vỡ module khác. Nên bản sao ở đây là <i>lựa chọn</i>,
 * không phải sơ suất.
 *
 * <p><b>Vì sao phải canh.</b> Cái giá của mọi bản sao là nguy cơ trôi lệch, và ở đây nó trôi theo cách
 * khó chịu nhất: ánh xạ dùng {@code valueOf(name())}, nên thêm một mức ở bên này mà quên bên kia sẽ
 * biên dịch trót lọt, test đơn vị vẫn xanh, rồi ném {@code IllegalArgumentException} <b>lúc chạy</b> —
 * đúng lúc có người bấm gửi thông báo. Bài kiểm này kéo thời điểm phát hiện về CI.
 *
 * <p>Cùng nguyên tắc với việc canh đồng bộ mã lỗi giữa backend và frontend: chỗ nào con người phải
 * nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ (conventions.md §1.5).
 */
class NotificationEnumParityTest {

    @Test
    @DisplayName("NotifySeverity trùng khít NotificationSeverity")
    void severityStaysInSync() {
        assertThat(names(NotifySeverity.values()))
                .as("thêm/bớt một mức ở %s thì phải làm y hệt ở %s", NotifySeverity.class, NotificationSeverity.class)
                .containsExactlyElementsOf(names(NotificationSeverity.values()));
    }

    @Test
    @DisplayName("NotifyChannel trùng khít NotificationChannel")
    void channelStaysInSync() {
        assertThat(names(NotifyChannel.values()))
                .as("thêm/bớt một kênh ở %s thì phải làm y hệt ở %s", NotifyChannel.class, NotificationChannel.class)
                .containsExactlyElementsOf(names(NotificationChannel.values()));
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
