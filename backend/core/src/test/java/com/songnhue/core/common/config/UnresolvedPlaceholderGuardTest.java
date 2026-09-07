package com.songnhue.core.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Chốt fail-fast khi thiếu biến môi trường (DoD Phase 0 mục 3).
 *
 * <p>Trước khi có {@link UnresolvedPlaceholderGuard}, bỏ hẳn {@code MINIO_ENDPOINT} vẫn khởi động
 * được và {@code /actuator/health} báo {@code UP} — {@code @NotBlank} không bắt được vì giá trị nhận
 * vào là chuỗi {@code "${MINIO_ENDPOINT}"}, không rỗng.
 */
class UnresolvedPlaceholderGuardTest {

    private final UnresolvedPlaceholderGuard guard = new UnresolvedPlaceholderGuard();

    @ConfigurationProperties(prefix = "app.storage")
    static class SampleProperties {
        String endpoint;
        String accessKey;
        Map<String, String> keys = new LinkedHashMap<>();
        List<String> buckets = List.of();
        int poolSize = 20;
    }

    private SampleProperties valid() {
        SampleProperties bean = new SampleProperties();
        bean.endpoint = "http://localhost:19000";
        bean.accessKey = "songnhue";
        bean.keys.put("v1", "c29uZ25odWUtdGVzdC1rZXktMzItYnl0ZS12YWx1ZSE=");
        bean.buckets = List.of("media", "report");
        return bean;
    }

    @Test
    @DisplayName("Thiếu biến môi trường → app KHÔNG khởi động, thông báo gọi đúng tên biến")
    void rejectsUnresolvedPlaceholder() {
        SampleProperties bean = valid();
        bean.endpoint = "${MINIO_ENDPOINT}";

        assertThatThrownBy(() -> guard.postProcessBeforeInitialization(bean, "storageProperties"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MINIO_ENDPOINT")
                .hasMessageContaining("app.storage.endpoint")
                .hasMessageContaining("deploy/env/local.env");
    }

    @Test
    @DisplayName("Cấu hình đủ biến → đi qua, không cản trở khởi động")
    void acceptsResolvedValues() {
        assertThatCode(() -> guard.postProcessBeforeInitialization(valid(), "storageProperties"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Bắt cả placeholder nằm trong Map — khoá AES nạp theo dạng map key_id → giá trị")
    void rejectsInsideMap() {
        SampleProperties bean = valid();
        bean.keys.put("v1", "${AES_KEY_V1}");

        assertThatThrownBy(() -> guard.postProcessBeforeInitialization(bean, "cryptoProperties"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AES_KEY_V1")
                .hasMessageContaining("app.storage.keys.v1");
    }

    @Test
    @DisplayName("Bắt cả placeholder nằm trong List")
    void rejectsInsideList() {
        SampleProperties bean = valid();
        bean.buckets = List.of("media", "${MINIO_BUCKET_REPORT}");

        assertThatThrownBy(() -> guard.postProcessBeforeInitialization(bean, "storageProperties"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MINIO_BUCKET_REPORT")
                .hasMessageContaining("app.storage.buckets[1]");
    }

    @Test
    @DisplayName("⚠ Không chặn nhầm giá trị hợp lệ có chứa ký tự `$`")
    void doesNotRejectLegitimateDollarSign() {
        // Mật khẩu sinh ngẫu nhiên rất hay chứa `$`. Chặn nhầm ở đây thì app không lên mà thông báo
        // lại nói "thiếu biến môi trường" — người vận hành sẽ đi tìm sai hướng hoàn toàn.
        SampleProperties bean = valid();
        bean.accessKey = "p@ssw0rd${notAPlaceholder";

        assertThatCode(() -> guard.postProcessBeforeInitialization(bean, "storageProperties"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Placeholder có giá trị mặc định đã được thay từ trước — không phải việc của lớp này")
    void ignoresValueThatMerelyLooksLikeDefault() {
        SampleProperties bean = valid();
        bean.endpoint = "8080";

        assertThatCode(() -> guard.postProcessBeforeInitialization(bean, "storageProperties"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Bean thường (không mang @ConfigurationProperties) được bỏ qua")
    void ignoresNonConfigurationPropertiesBean() {
        Object plain = new Object();
        assertThat(guard.postProcessBeforeInitialization(plain, "someService")).isSameAs(plain);
    }

    @Test
    @DisplayName("Chạy sau khi nạp cấu hình nhưng TRƯỚC @PostConstruct")
    void runsBeforePostConstruct() {
        // Nếu chạy sau @PostConstruct thì CryptoProperties ném trước với thông báo "khoá AES không
        // phải base64 hợp lệ" — che mất nguyên nhân thật là thiếu AES_KEY_V1.
        assertThat(guard.getOrder())
                .as("phải sau ConfigurationPropertiesBindingPostProcessor (HIGHEST_PRECEDENCE + 1)")
                .isGreaterThan(Integer.MIN_VALUE + 1)
                .isLessThan(0);
    }
}
