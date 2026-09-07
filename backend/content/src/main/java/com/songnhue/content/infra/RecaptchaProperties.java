package com.songnhue.content.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Khoá <b>bí mật</b> reCAPTCHA v3 — {@code RECAPTCHA_SECRET_KEY}. CN-01.4 / T36.6.
 *
 * <h2>⛔⛔ Vì sao khoá này ⛔ KHÔNG nằm ở bảng {@code settings}</h2>
 *
 * <p>Bảng {@code settings} nhóm {@code SITE} đi <b>thẳng</b> ra
 * {@code GET /api/v1/public/site-config} — một endpoint ⛔ <b>không cần đăng nhập</b>
 * ({@code SiteConfigService.effectiveValues} trả cả nhóm). Đặt một credential vào đó là phát nó cho
 * bất kỳ ai mở cổng. Quy tắc 13: credential bên thứ 3 ⛔ không log, ⛔ không trả ra API, ⛔ không
 * nằm trong bản export cấu hình.
 *
 * <p>⭐ Khoá <b>công khai</b> (site key) thì ngược lại — Google in nó ra trong HTML của mọi trang
 * dùng captcha, nên nó ở {@code settings} là đúng chỗ và Công ty đổi được ⛔ không cần deploy.
 *
 * <h2>⛔ ⛔ KHÔNG fail-fast lúc khởi động — đã trả giá đúng chỗ này ở WS-27</h2>
 *
 * <p>{@link HydroApiProperties} kể lại đầy đủ: một {@code @NotBlank} trên một credential mà Công ty
 * <i>chưa cấp</i> biến "chưa có khoá của một tính năng" thành "toàn bộ hệ thống ⛔ không khởi động
 * được", và làm đỏ mọi bài kiểm tích hợp của mọi module. Khoá này còn đang bị <b>G13</b> chặn, nên
 * rỗng là trạng thái <b>bình thường và lâu dài</b>.
 *
 * <p>Fail-fast ⛔ không mất, nó chuyển chỗ: {@code ContactFormPolicy} coi <i>"bật captcha mà thiếu
 * khoá bí mật"</i> là <b>chưa bật</b>, và ghi {@code ERROR} mỗi lượt — trạng thái sai cấu hình phải
 * nhìn thấy được, ⛔ không im lặng.
 *
 * <h2>⛔ Ghi đè {@code toString()}</h2>
 *
 * <p>Bản mặc định của Spring in hết trường, và một dòng log lúc khởi động là đủ để khoá bí mật nằm
 * vĩnh viễn trong tệp log.
 */
@ConfigurationProperties(prefix = "app.recaptcha")
public class RecaptchaProperties {

    /** ⚠ Rỗng là hợp lệ và có nghĩa: "Công ty chưa cấp khoá" (G13). */
    private String secret = "";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public boolean coKhoa() {
        return secret != null && !secret.isBlank();
    }

    @Override
    public String toString() {
        return "RecaptchaProperties{secret=" + (coKhoa() ? "***" : "(chưa đặt)") + "}";
    }
}
