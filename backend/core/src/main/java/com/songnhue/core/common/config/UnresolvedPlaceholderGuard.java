package com.songnhue.core.common.config;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.ReflectionUtils;

/**
 * Chặn khởi động khi một biến môi trường bắt buộc chưa được đặt (conventions.md §1.6 — fail-fast).
 *
 * <p><b>Vì sao phải có lớp này, dù mọi lớp cấu hình đã có {@code @Validated} + {@code @NotBlank}.</b>
 * Bộ nạp của {@code @ConfigurationProperties} dùng {@code PropertySourcesPlaceholdersResolver} với
 * {@code ignoreUnresolvablePlaceholders = true} — khác hẳn {@code @Value}. Khi thiếu biến môi trường,
 * placeholder <b>không</b> ném lỗi mà được gán nguyên văn: trường {@code endpoint} nhận đúng chuỗi
 * {@code "${MINIO_ENDPOINT}"}. Chuỗi đó không rỗng, nên {@code @NotBlank} <b>đi qua</b> và app khởi
 * động bình thường.
 *
 * <p>Kiểm chứng bằng chạy thật (14/8, rà soát sau WS-5): bỏ hẳn {@code MINIO_ENDPOINT} rồi khởi động
 * → {@code Started SongnhueApplication}, {@code /actuator/health} trả {@code UP}. Thông báo
 * {@code "Thiếu MINIO_ENDPOINT"} viết sẵn trong {@link StorageProperties} không bao giờ có cơ hội in
 * ra. Đây đúng là kiểu hỏng mà fail-fast sinh ra để chặn: deploy lên môi trường thiếu một biến, hệ
 * thống báo khoẻ mạnh, và chỉ vỡ ở lần upload tệp đầu tiên bằng một lỗi kết nối tới máy chủ tên
 * {@code ${MINIO_ENDPOINT}} — cách chỗ sai rất xa.
 *
 * <p><b>Cài ở tầng chung, không đặt vào từng lớp cấu hình.</b> Mỗi lớp tự kiểm thì lớp thêm về sau
 * chỉ cần quên một lần là thủng lại, mà không có gì báo. Lớp này quét mọi bean mang
 * {@code @ConfigurationProperties}, kể cả của module nghiệp vụ Phase 1+ chưa viết.
 */
public class UnresolvedPlaceholderGuard implements BeanPostProcessor, Ordered {

    /**
     * Chỉ khớp khi <b>toàn bộ</b> giá trị là một placeholder chưa thay thế.
     *
     * <p>Cố ý không dùng {@code contains("${")}: mật khẩu hay chuỗi kết nối hoàn toàn có thể chứa ký
     * tự {@code $} một cách hợp lệ, chặn nhầm thì người dùng không hiểu vì sao app không lên. Còn
     * placeholder <i>có</i> giá trị mặc định ({@code ${FOO:8080}}) thì đã được thay thế từ trước nên
     * không lọt tới đây — thứ còn sót lại luôn là biến bắt buộc mà chưa ai đặt.
     */
    private static final Pattern UNRESOLVED = Pattern.compile("^\\$\\{([A-Za-z0-9_.\\-]+)}$");

    @Override
    public int getOrder() {
        // Sau ConfigurationPropertiesBindingPostProcessor (HIGHEST_PRECEDENCE + 1) để có giá trị đã
        // nạp, nhưng trước @PostConstruct — nếu không, CryptoProperties sẽ ném trước với thông báo
        // "khoá AES không phải base64 hợp lệ", che mất nguyên nhân thật là thiếu biến môi trường.
        return Ordered.HIGHEST_PRECEDENCE + 2;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        ConfigurationProperties annotation =
                AnnotationUtils.findAnnotation(bean.getClass(), ConfigurationProperties.class);
        if (annotation != null) {
            String prefix = annotation.prefix().isEmpty() ? annotation.value() : annotation.prefix();
            ReflectionUtils.doWithFields(bean.getClass(), field -> check(bean, field, prefix));
        }
        return bean;
    }

    private void check(Object bean, Field field, String prefix) {
        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
            return;
        }
        ReflectionUtils.makeAccessible(field);
        Object value = ReflectionUtils.getField(field, bean);
        String property = prefix.isEmpty() ? kebab(field.getName()) : prefix + "." + kebab(field.getName());

        if (value instanceof String text) {
            reject(text, property);
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((key, entry) -> {
                if (entry instanceof String text) {
                    reject(text, property + "." + key);
                }
            });
        } else if (value instanceof Collection<?> items) {
            int index = 0;
            for (Object item : items) {
                if (item instanceof String text) {
                    reject(text, property + "[" + index + "]");
                }
                index++;
            }
        }
    }

    private void reject(String value, String property) {
        Matcher matcher = UNRESOLVED.matcher(value.trim());
        if (matcher.matches()) {
            String variable = matcher.group(1);
            throw new IllegalStateException("Thiếu biến môi trường " + variable + " — tham số '" + property
                    + "' vẫn còn nguyên placeholder, chưa được thay bằng giá trị thật. "
                    + "Đặt " + variable + " trong file env của môi trường đang chạy "
                    + "(local: deploy/env/local.env, xem deploy/env/local.env.example và "
                    + "docs/setup-guideline.md).");
        }
    }

    /** {@code bucketMedia} → {@code bucket-media}, khớp cách springdoc và Boot hiển thị tham số. */
    private static String kebab(String name) {
        StringBuilder out = new StringBuilder(name.length() + 4);
        for (char c : name.toCharArray()) {
            if (Character.isUpperCase(c)) {
                out.append('-').append(Character.toLowerCase(c));
            } else {
                out.append(c);
            }
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
