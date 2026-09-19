package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Xô đăng nhập theo IP chỉ đếm lượt SAI — T61.17 (WS-72).</b>
 *
 * <h2>Khuyết tật</h2>
 *
 * Xô {@code LOGIN} (30 lượt / 15′ theo IP) đếm MỌI lượt gọi {@code /auth/login}, đúng lẫn sai. Chính mã khai
 * <i>"cả Công ty ra Internet qua một IP NAT"</i> ⇒ 8h sáng, người thứ 31 đăng nhập ĐÚNG trong cùng 15 phút nhận
 * 429 tới hết cửa sổ — cả cơ quan khoá ngoài hệ thống bởi chính lưới chống dò mật khẩu. Xô ấy sinh ra để chặn
 * DÒ (sai hàng loạt), ⛔ để chặn người đăng nhập đúng.
 *
 * <h2>Hai vế — vế thứ hai là thứ cho phép tin vế thứ nhất (luật 9)</h2>
 *
 * <ol>
 *   <li>{@code trần + 1} lượt đăng nhập ĐÚNG sau cùng một IP ⇒ tất cả 200. Viết TRƯỚC bản vá, đỏ ở lượt 31
 *       (T51.13).
 *   <li>{@code trần} lượt SAI từ một IP ⇒ lượt kế tiếp là 429, KỂ CẢ khi mật khẩu đúng — lưới chống dò vẫn đứng. IP
 *       khác ⛔ bị liên luỵ. ⛔ Có vế này thì bản vá *"bỏ hẳn xô LOGIN"* cũng làm vế 1 xanh.
 * </ol>
 *
 * <p>Lượt sai dùng tên tài khoản ⛔ tồn tại: khoá theo TÀI KHOẢN (5 lần sai ⇒ AUTH-0003) là cơ chế khác, bài này
 * ⛔ được lẫn hai cơ chế.
 */
class DangNhapSauNatHttpTest extends IntegrationTestBase {

    private static final String DANG_NHAP = "/api/v1/auth/login";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("⛔⛔ T61.17 — trần+1 lượt đăng nhập ĐÚNG sau CÙNG một IP (NAT) đều qua: xô LOGIN ⛔ đếm lượt đúng")
    void motNatNhieuLuotDungDeuQua() {
        PhienHttp nat = new PhienHttp(http);
        nat.doiIp();
        String canBo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t72_dung");
        int soLuot = RateLimitPolicy.LOGIN.limit() + 1;

        for (int i = 1; i <= soLuot; i++) {
            ResponseEntity<String> r =
                    nat.dangJson(DANG_NHAP, Map.of("username", canBo, "password", PhienHttp.MAT_KHAU));
            assertThat(r.getStatusCode().value())
                    .as(
                            "lượt ĐÚNG thứ %d/%d sau cùng một IP — 429 ở đây là cả cơ quan sau NAT bị khoá ngoài hệ thống"
                                    + " bởi lưới chống dò mật khẩu",
                            i, soLuot)
                    .isEqualTo(200);
        }
    }

    @Test
    @DisplayName("⛔⛔ trần lượt SAI từ một IP ⇒ lượt kế tiếp 429 kể cả mật khẩu ĐÚNG; IP khác ⛔ bị liên luỵ")
    void luotSaiVanBiChan() {
        PhienHttp doMatKhau = new PhienHttp(http);
        doMatKhau.doiIp();
        String canBo = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t72_sai");
        int tran = RateLimitPolicy.LOGIN.limit();

        for (int i = 1; i <= tran; i++) {
            ResponseEntity<String> r =
                    doMatKhau.dangJson(DANG_NHAP, Map.of("username", "t72_khong_ton_tai_" + i, "password", "sai-" + i));
            assertThat(r.getStatusCode().value())
                    .as("lượt SAI thứ %d/%d phải tới được bộ xác thực (401)", i, tran)
                    .isEqualTo(401);
        }
        ResponseEntity<String> sauTran =
                doMatKhau.dangJson(DANG_NHAP, Map.of("username", canBo, "password", PhienHttp.MAT_KHAU));
        assertThat(sauTran.getStatusCode().value())
                .as("sau %d lượt sai, IP ấy phải bị chặn ở bộ lọc — kể cả lượt có mật khẩu ĐÚNG", tran)
                .isEqualTo(429);

        PhienHttp ipKhac = new PhienHttp(http);
        ipKhac.doiIp();
        assertThat(ipKhac.dangJson(DANG_NHAP, Map.of("username", canBo, "password", PhienHttp.MAT_KHAU))
                        .getStatusCode()
                        .value())
                .as("IP khác ⛔ bị liên luỵ")
                .isEqualTo(200);
    }
}
