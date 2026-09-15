package com.songnhue.core.common.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.common.security.AccessTokenClaims;

/** Hai tầng hạn mức chia xô ĐÚNG MỘT LẦN, đứng đúng chỗ trong chuỗi filter — T61.17. */
class HaiTangHanMucTest {

    private final RateLimitFilter theoIp = new RateLimitFilter(null, null);
    private final HanMucNguoiDungFilter theoNguoiDung = new HanMucNguoiDungFilter(null, null, null);

    @Test
    @DisplayName("⛔⛔ Mỗi xô do ĐÚNG MỘT tầng đếm — xô mới ⛔ được rơi lọt cả hai (⛔ ai đếm)")
    void moiXoDungMotTang() {
        assertThat(RateLimitPolicy.values()).as("chống tập rỗng").hasSizeGreaterThanOrEqualTo(4);
        assertThat(Arrays.stream(RateLimitPolicy.values())
                        .filter(p -> theoIp.phuTrach(p) == theoNguoiDung.phuTrach(p))
                        .toList())
                .as("xô ⛔ tầng nào đếm (hoặc bị đếm HAI lần)")
                .isEmpty();
        assertThat(theoIp.phuTrach(RateLimitPolicy.LOGIN))
                .as("đăng nhập phải chặn TRƯỚC xác thực — ⛔ để dò mật khẩu tiêu BCrypt")
                .isTrue();
        assertThat(theoNguoiDung.phuTrach(RateLimitPolicy.API)).isTrue();
        assertThat(theoNguoiDung.phuTrach(RateLimitPolicy.EXPORT)).isTrue();
    }

    @Test
    @DisplayName("⭐ Thứ tự: IP < CSRF < AUTH < theo người dùng < nạp quyền")
    void thuTuTrongChuoi() {
        assertThat(FilterOrder.RATE_LIMIT).isLessThan(FilterOrder.AUTH);
        assertThat(FilterOrder.RATE_LIMIT_NGUOI_DUNG)
                .as("đứng TRƯỚC AUTH là đọc `sub` chưa kiểm chữ ký")
                .isGreaterThan(FilterOrder.AUTH)
                .as("đứng SAU nạp quyền là lượt bị chặn vẫn tốn một lần đọc CSDL")
                .isLessThan(FilterOrder.SCOPE_CONTEXT);
    }

    @Test
    @DisplayName("⭐ Khoá: claims đã kiểm ⇒ người@IP · mọi thứ khác ⇒ IP")
    void khoa() {
        UUID sub = UUID.randomUUID();
        AccessTokenClaims claims = new AccessTokenClaims(sub, "a", UUID.randomUUID(), UUID.randomUUID(), Instant.now());

        assertThat(HanMucNguoiDungFilter.khoa(claims, "10.0.0.1")).isEqualTo("u:" + sub + "@10.0.0.1");
        assertThat(HanMucNguoiDungFilter.khoa(null, "10.0.0.1")).isEqualTo("10.0.0.1");
        assertThat(HanMucNguoiDungFilter.khoa("u:gia", "10.0.0.1"))
                .as("một thuộc tính request ⛔ phải claims đã kiểm ⇒ ⛔ được thành khoá người dùng")
                .isEqualTo("10.0.0.1");
    }
}
