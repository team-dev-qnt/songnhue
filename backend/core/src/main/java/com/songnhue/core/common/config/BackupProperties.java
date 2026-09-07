package com.songnhue.core.common.config;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

/**
 * Cấu hình sao lưu và khôi phục (WS-7 / T7.1, T7.5).
 *
 * <h2>Vì sao dùng hai vai trò CSDL khác nhau</h2>
 *
 * <p><b>Sao lưu chạy bằng {@code songnhue_readonly}.</b> {@code pg_dump} chỉ cần đọc, và job này chạy
 * mỗi đêm — cấp cho nó quyền ghi là mở rộng vô cớ phạm vi thiệt hại khi tiến trình ứng dụng bị chiếm
 * quyền điều khiển. Đây là cùng một nguyên tắc đã áp cho {@code songnhue_archiver}
 * (architecture-review.md §9.3): mỗi việc chạy bằng đúng quyền nó cần, không hơn.
 *
 * <p><b>Khôi phục cần quyền chủ sở hữu, nên nó là tính năng BẬT RIÊNG.</b> Không đặt
 * {@code DB_RESTORE_PASSWORD} thì nút khôi phục trên UI báo "chưa cấu hình" và từ chối ngay, thay vì
 * chạy tới nửa đường rồi hỏng ở lệnh {@code DROP}. Nơi nào không muốn giữ mật khẩu chủ sở hữu trong
 * tiến trình ứng dụng thì cứ để trống và khôi phục bằng {@code docs/runbook/khoi-phuc-du-lieu.md} —
 * đó là lựa chọn hợp lệ, không phải thiếu sót cấu hình.
 *
 * <h2>Vì sao đường dẫn nhị phân cũng là tham số</h2>
 *
 * <p>{@code pg_restore} <b>không đọc được</b> bản dump sinh bởi máy chủ mới hơn nó. Máy chủ nâng lên
 * PostgreSQL 17 mà image ứng dụng vẫn còn client 16 thì bản sao lưu vẫn tạo được nhưng
 * <i>không khôi phục được</i> — và chuyện đó chỉ lộ ra đúng lúc cần khôi phục. Để đường dẫn cấu hình
 * được nghĩa là chỉ đường sang bộ client mới là xong, không phải dựng lại image.
 */
@Validated
@ConfigurationProperties(prefix = "app.backup")
public class BackupProperties {

    /** Thư mục chứa bản dump. Phải nằm trên volume được gắn ra ngoài container. */
    @NotBlank(message = "Thiếu BACKUP_DIR")
    private String directory;

    @NotBlank(message = "Thiếu DB_HOST")
    private String host;

    private int port = 5432;

    @NotBlank(message = "Thiếu DB_NAME")
    private String database;

    private String dumpUsername = "songnhue_readonly";

    private String dumpPassword = "";

    private String restoreUsername = "songnhue_owner";

    /** Để trống = tắt hẳn chức năng khôi phục qua UI. Xem javadoc lớp. */
    private String restorePassword = "";

    private String pgDumpPath = "pg_dump";

    private String pgRestorePath = "pg_restore";

    private String psqlPath = "psql";

    /**
     * Hạn chạy một lượt dump/restore.
     *
     * <p>Không có hạn thì một tiến trình con treo sẽ giữ luồng worker vĩnh viễn, và cái hỏng đầu tiên
     * người ta nhìn thấy là "hàng đợi ngừng chạy" — cách nguyên nhân thật vài lớp.
     */
    private Duration timeout = Duration.ofHours(2);

    /** Có đủ điều kiện khôi phục qua UI không (M5.11). */
    public boolean isRestoreConfigured() {
        return StringUtils.hasText(restorePassword);
    }

    public boolean isDumpConfigured() {
        return StringUtils.hasText(dumpPassword);
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getDumpUsername() {
        return dumpUsername;
    }

    public void setDumpUsername(String dumpUsername) {
        this.dumpUsername = dumpUsername;
    }

    public String getDumpPassword() {
        return dumpPassword;
    }

    public void setDumpPassword(String dumpPassword) {
        this.dumpPassword = dumpPassword;
    }

    public String getRestoreUsername() {
        return restoreUsername;
    }

    public void setRestoreUsername(String restoreUsername) {
        this.restoreUsername = restoreUsername;
    }

    public String getRestorePassword() {
        return restorePassword;
    }

    public void setRestorePassword(String restorePassword) {
        this.restorePassword = restorePassword;
    }

    public String getPgDumpPath() {
        return pgDumpPath;
    }

    public void setPgDumpPath(String pgDumpPath) {
        this.pgDumpPath = pgDumpPath;
    }

    public String getPgRestorePath() {
        return pgRestorePath;
    }

    public void setPgRestorePath(String pgRestorePath) {
        this.pgRestorePath = pgRestorePath;
    }

    public String getPsqlPath() {
        return psqlPath;
    }

    public void setPsqlPath(String psqlPath) {
        this.psqlPath = psqlPath;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }
}
