package com.songnhue.app.architecture.fixture;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * Mã cố ý sai cho {@code GuiThuQuaEmailSenderRuleSelfCheckTest} — T61.23 (WS-73).
 *
 * <p>⛔ Cố ý ⛔ {@code @Component}: gói này nằm trong tầm quét của Spring.
 */
public final class GuiThuFixtures {

    private GuiThuFixtures() {}

    /** ⛔ Vi phạm: gửi thẳng qua {@code JavaMailSender} — vượt mặt chuyển hướng thư của staging (T61.23). */
    public static final class GuiVuotMat {

        private final JavaMailSender mail;

        public GuiVuotMat(JavaMailSender mail) {
            this.mail = mail;
        }

        public void gui(String toi) {
            SimpleMailMessage thu = new SimpleMailMessage();
            thu.setTo(toi);
            mail.send(thu);
        }
    }

    /** ✅ Bắt lỗi gửi thư là hợp lệ — {@code MailException} ⛔ gửi được thư nào. */
    public static final class BatLoiGuiThu {

        public boolean laLoiThu(RuntimeException e) {
            return e instanceof org.springframework.mail.MailException;
        }
    }
}
