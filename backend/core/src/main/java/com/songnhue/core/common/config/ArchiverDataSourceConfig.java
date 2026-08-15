package com.songnhue.core.common.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import com.songnhue.core.infra.audit.ArchiverJdbc;

/**
 * Kết nối riêng bằng vai trò {@code songnhue_archiver} — chỉ dành cho job kết xuất nhật ký (T6.13).
 *
 * <p><b>Vì sao phải có một kết nối thứ hai.</b> WS-2 tách quyền ở tầng DB: {@code songnhue_app}
 * <b>không có</b> {@code DELETE} trên {@code audit_logs}, kể cả khi mã ứng dụng bị chiếm quyền điều
 * khiển. Chỉ {@code songnhue_archiver} xoá được, và nó chỉ được dùng ở đúng một chỗ là job này.
 * Cấp quyền xoá cho tài khoản chạy hằng ngày sẽ vô hiệu hoá toàn bộ ý nghĩa của việc tách vai trò.
 *
 * <p>⚠⚠ <b>KHÔNG được khai {@code DataSource} thành bean.</b> Đây là bẫy đã sập một lần khi làm
 * WS-6: {@code DataSourceAutoConfiguration} của Spring Boot mang
 * {@code @ConditionalOnMissingBean(DataSource.class)}, nên <i>bất kỳ</i> bean {@code DataSource} nào
 * do mình khai đều làm Boot <b>ngừng tạo DataSource chính</b>. Kết quả: kết nối duy nhất của cả ứng
 * dụng trở thành kết nối của vai trò archiver, và mọi truy vấn bình thường đổ vỡ với
 * {@code permission denied for table jobs} — một thông báo không hề gợi ý nguyên nhân thật.
 *
 * <p>Cùng cái bẫy đó lặp lại ở tầng thứ hai: {@code JdbcTemplateAutoConfiguration} mang
 * {@code @ConditionalOnMissingBean(JdbcOperations.class)}, nên khai một bean {@code JdbcTemplate}
 * cũng làm Boot ngừng tạo bản chính — và mọi nơi dùng JDBC (ghi nhật ký kiểm toán, bảng nối vai trò)
 * lặng lẽ chuyển sang chạy bằng vai trò archiver.
 *
 * <p>Vì vậy {@link HikariDataSource} được tạo <i>bên trong</i> phương thức bean, và kết quả lộ ra
 * dưới một <b>kiểu riêng</b> {@link ArchiverJdbc} mà Boot không biết tới. Lớp cấu hình tự đóng pool
 * khi context tắt.
 *
 * <p>Pool cố ý <b>rất nhỏ</b> và không giữ kết nối rỗi: job chạy mỗi tháng một lần, không có lý do
 * gì để chiếm slot kết nối của PostgreSQL suốt thời gian còn lại.
 */
@Configuration
@ConditionalOnProperty(name = "app.audit.archiver.password", matchIfMissing = false)
public class ArchiverDataSourceConfig implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ArchiverDataSourceConfig.class);

    private HikariDataSource dataSource;

    @Bean
    public ArchiverJdbc archiverJdbc(
            @Value("${spring.datasource.url}") String url,
            @Value("${app.audit.archiver.username:songnhue_archiver}") String username,
            @Value("${app.audit.archiver.password}") String password) {

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setPoolName("songnhue-archiver");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        config.setIdleTimeout(30_000);

        this.dataSource = new HikariDataSource(config);
        log.info("Kết nối kết xuất nhật ký sẵn sàng (vai trò {})", username);
        return new ArchiverJdbc(new JdbcTemplate(dataSource));
    }

    @Override
    public void destroy() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
