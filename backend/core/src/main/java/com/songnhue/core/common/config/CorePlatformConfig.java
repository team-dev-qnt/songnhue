package com.songnhue.core.common.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Bật các thành phần của Common Platform.
 *
 * <p>{@code @EnableJpaAuditing} làm cho {@code created_at/by} và {@code updated_at/by} của
 * {@code BaseEntity} tự điền — không lập trình viên nào phải nhớ set tay, và cũng không ai set sai.
 *
 * <p>{@code @EnableScheduling} phục vụ job dọn token hết hạn (WS-5). WS-6 chuyển các tác vụ định kỳ
 * sang hàng đợi job trong DB + ShedLock; annotation này vẫn giữ vì vài việc dọn dẹp hạ tầng không
 * cần tới bộ máy đó.
 */
@Configuration
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
@EnableConfigurationProperties({
    AppProperties.class,
    CryptoProperties.class,
    StorageProperties.class,
    JwtProperties.class
})
@EnableTransactionManagement(order = CorePlatformConfig.TRANSACTION_ADVISOR_ORDER)
public class CorePlatformConfig {

    /**
     * ⚠ <b>Thứ tự của bộ chặn transaction — bắt buộc phải NGOÀI {@code ScopeFilterAspect}.</b>
     *
     * <p>Mặc định của Spring là {@link Ordered#LOWEST_PRECEDENCE} ({@code Integer.MAX_VALUE}), mà
     * trong Spring AOP <b>số nhỏ hơn nghĩa là chạy ở vòng ngoài</b>. {@code ScopeFilterAspect} từng
     * đặt {@code LOWEST_PRECEDENCE - 1} với ý định "nằm bên trong bộ chặn transaction" — nhưng
     * {@code MAX_VALUE - 1} nhỏ hơn {@code MAX_VALUE}, nên nó lại nằm ở <b>vòng ngoài</b>. Hệ quả:
     * {@code enableFilter} chạy khi chưa có transaction nào, Spring cấp cho nó một {@code Session}
     * tạm rồi vứt đi, còn truy vấn thật chạy trong một {@code Session} khác <b>không có bộ lọc</b>.
     *
     * <p>Triệu chứng: đúng bằng không. Không lỗi, không log, chỉ là <b>mọi Xí nghiệp đọc được dữ liệu
     * của nhau</b>. Lỗi này sống sót qua cả WS-5 và chỉ lộ ra ở T10.3, khi có entity thật thuộc phạm
     * vi đơn vị để mà thử.
     *
     * <p>Không thể sửa bằng cách đẩy aspect xuống thấp hơn nữa — {@code LOWEST_PRECEDENCE} đã là số
     * lớn nhất biểu diễn được. Nên phải kéo bộ chặn transaction <i>lên trên</i>, và đó là dòng này.
     * Khoảng cách 100 để sau này còn chỗ chen thêm aspect vào giữa nếu cần.
     */
    public static final int TRANSACTION_ADVISOR_ORDER = Ordered.LOWEST_PRECEDENCE - 100;

    /**
     * Phải là {@code static}: một {@code BeanPostProcessor} khai báo bằng phương thức thường sẽ kéo
     * cả lớp cấu hình này khởi tạo quá sớm, trước khi các post-processor khác kịp đăng ký.
     */
    @Bean
    static UnresolvedPlaceholderGuard unresolvedPlaceholderGuard() {
        return new UnresolvedPlaceholderGuard();
    }
}
