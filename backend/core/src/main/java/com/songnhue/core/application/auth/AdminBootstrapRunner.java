package com.songnhue.core.application.auth;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * Kích hoạt tài khoản {@code superadmin} lần đầu — T5.7.
 *
 * <p><b>Vì sao phải có bước này.</b> Migration seed tài khoản với {@code password_hash = '!'}, tức
 * là không mật khẩu nào khớp. Cố ý như vậy: đặt một mật khẩu mặc định trong repo là lỗ hổng (ai
 * cũng đọc được, và luôn có môi trường quên đổi), còn sinh hash ngay trong migration thì mật khẩu
 * thô đi qua log SQL của máy chủ. Hệ quả là <b>không có lệnh này thì không ai đăng nhập được</b>.
 *
 * <p>Cách dùng: đặt {@code BOOTSTRAP_ADMIN_PASSWORD} vào env, khởi động ứng dụng một lần, rồi
 * <b>gỡ biến đó khỏi máy chủ</b>. Không gỡ thì mật khẩu quản trị nằm trong file cấu hình vô thời hạn.
 *
 * <p>Chạy đúng một lần theo nghĩa: chỉ tác động khi tài khoản còn ở trạng thái
 * {@code PENDING_ACTIVATION}. Khởi động lại với biến còn nguyên cũng không đặt lại mật khẩu — nếu
 * không thì ai sửa được file env là chiếm được tài khoản quản trị tối cao mà chẳng cần biết mật khẩu
 * cũ.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private static final String BOOTSTRAP_USERNAME = "superadmin";

    private final UserRepository users;
    private final PasswordPolicyService passwords;
    private final SecurityEventService securityEvents;
    private final String bootstrapPassword;

    public AdminBootstrapRunner(
            UserRepository users,
            PasswordPolicyService passwords,
            SecurityEventService securityEvents,
            @Value("${app.bootstrap.admin-password:}") String bootstrapPassword) {
        this.users = users;
        this.passwords = passwords;
        this.securityEvents = securityEvents;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(bootstrapPassword)) {
            return;
        }

        User admin = users.findActiveByUsername(BOOTSTRAP_USERNAME).orElse(null);
        if (admin == null) {
            log.warn("Có BOOTSTRAP_ADMIN_PASSWORD nhưng không tìm thấy tài khoản '{}'", BOOTSTRAP_USERNAME);
            return;
        }
        if (!UserStatus.PENDING_ACTIVATION.name().equals(admin.getStatus())) {
            log.info(
                    "Tài khoản '{}' đã kích hoạt từ trước — bỏ qua bootstrap. "
                            + "Gỡ biến BOOTSTRAP_ADMIN_PASSWORD khỏi cấu hình máy chủ.",
                    BOOTSTRAP_USERNAME);
            return;
        }

        // Áp đúng chính sách như mọi mật khẩu khác — mật khẩu quản trị tối cao không phải ngoại lệ
        // Không phải request HTTP nào cả — tên trường chỉ đi vào log của lượt fail-fast lúc khởi
        // động. Vẫn khai tường minh: một chuỗi rỗng ở đây sẽ làm chi tiết lỗi vô nghĩa.
        passwords.validate(bootstrapPassword, BOOTSTRAP_USERNAME, "SEED_ADMIN_PASSWORD");

        admin.setPasswordHash(passwords.hash(bootstrapPassword));
        admin.setPasswordChangedAt(Instant.now());
        admin.setMustChangePassword(true);
        admin.setStatus(UserStatus.ACTIVE);
        users.save(admin);

        // ⛔ Không bao giờ ghi mật khẩu ra log, kể cả một phần của nó
        log.warn(
                "Đã kích hoạt tài khoản '{}'. HÃY GỠ BOOTSTRAP_ADMIN_PASSWORD khỏi cấu hình ngay. "
                        + "Lần đăng nhập đầu tiên sẽ bắt đổi mật khẩu và bắt đăng ký 2FA.",
                BOOTSTRAP_USERNAME);
        securityEvents.record(
                SecurityEventType.ADMIN_BOOTSTRAP, BOOTSTRAP_USERNAME, admin.getId(), ClientInfo.unknown());
    }
}
