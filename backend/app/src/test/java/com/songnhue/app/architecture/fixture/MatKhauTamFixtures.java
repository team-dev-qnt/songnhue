package com.songnhue.app.architecture.fixture;

import com.songnhue.core.domain.identity.User;

/** Mã cố ý sai cho {@code MatKhauTamMotCuaRuleTest} — T73.8. ⛔ {@code @Component}. */
public final class MatKhauTamFixtures {

    private MatKhauTamFixtures() {}

    /** ⛔ Đường phát mật khẩu tạm thứ ba tự bật cờ — mật khẩu tạm ⛔ hạn. */
    public static final class DuongThuBa {

        public void phat(User user, String hash) {
            user.setPasswordHash(hash);
            user.setMustChangePassword(true);
        }
    }
}
