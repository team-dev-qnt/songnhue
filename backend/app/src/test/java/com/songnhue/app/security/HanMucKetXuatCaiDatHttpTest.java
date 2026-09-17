package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * <b>Trần kết xuất đọc từ {@code settings} — sửa trên giao diện là có hiệu lực ngay — T61.27.</b>
 *
 * <p>QuanTran chốt 15/09/2026: 10 lượt/giờ ghi cứng ⛔ đủ cho người lập 8 báo cáo BCNS + báo cáo vận
 * hành trong một buổi ⇒ tham số nghiệp vụ (quy tắc 12), mặc định 30, <b>kẹp trong mã</b> ở 100.
 *
 * <p>⚠ Giá trị thử là <b>3</b> và <b>1000</b> — ⛔ 30 (trùng dự phòng trong enum ⇒ hệ ghi cứng cũng cho
 * ra y hệt, T48.7). Đo {@code X-RateLimit-Limit}, rồi đi tới lượt {@code 429} thật ở giá trị 3.
 *
 * <p>⚠ Filter hạn mức [3b] đứng TRƯỚC nạp quyền nên người dùng ⛔ có quyền kết xuất vẫn bị đếm — bài
 * ⛔ cần dựng quyền, và {@code 403} sau đó ⛔ ảnh hưởng gì tới phép đo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HanMucKetXuatCaiDatHttpTest extends IntegrationTestBase {

    private static final String DUONG_KET_XUAT = "/api/v1/cms/contacts/export";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SettingService settings;

    private PhienHttp may;
    private PhienHttp.Phien canBo;

    @BeforeAll
    void dangNhap() {
        may = new PhienHttp(http);
        canBo = may.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t6127_ketxuat"));
    }

    /** Khôi phục trong {@code @AfterEach} — ⛔ ở cuối phương thức: một khẳng định đỏ là kẹt trạng thái (T48.8). */
    @AfterEach
    void khoiPhuc() {
        dat("30");
    }

    @Test
    @DisplayName("⛔⛔ settings = 3 ⇒ trần 3, lượt thứ tư nhận 429 — con số trên giao diện QUYẾT ĐỊNH hành vi")
    void caiDatQuyetDinhTran() {
        assertThat(jdbc.queryForObject(
                        "SELECT setting_value FROM settings WHERE setting_key = ?",
                        String.class,
                        RateLimitPolicy.KHOA_KET_XUAT))
                .as("chống tập rỗng: migration phải seed khoá với mặc định 30")
                .isEqualTo("30");

        dat("3");
        may.doiIp();
        ResponseEntity<String> dau = may.get(canBo, DUONG_KET_XUAT);
        assertThat(dau.getHeaders().getFirst("X-RateLimit-Limit"))
                .as("⛔ trần vẫn là hằng số trong mã ⇒ tham số trên giao diện ⛔ quyết định gì (luật 15)")
                .isEqualTo("3");
        may.get(canBo, DUONG_KET_XUAT);
        may.get(canBo, DUONG_KET_XUAT);
        assertThat(may.get(canBo, DUONG_KET_XUAT).getStatusCode().value())
                .as("lượt thứ tư phải bị chặn")
                .isEqualTo(429);
    }

    @Test
    @DisplayName("⛔⛔ Ghi thẳng 1000 vào CSDL (vượt validation) ⇒ mã vẫn kẹp ở trần cứng 100")
    void maKepTranCung() {
        dat("1000");
        may.doiIp();
        assertThat(may.get(canBo, DUONG_KET_XUAT).getHeaders().getFirst("X-RateLimit-Limit"))
                .as("Admin bị chiếm sửa được cột validation cũng ⛔ mở được đường rút dữ liệu hàng loạt")
                .isEqualTo(String.valueOf(RateLimitPolicy.TRAN_KET_XUAT));
    }

    @Test
    @DisplayName("⛔ Trần trong mã = `max=` của cột validation — hai nơi khai một con số (luật 14)")
    void tranMaKhopValidation() {
        String validation = jdbc.queryForObject(
                "SELECT validation FROM settings WHERE setting_key = ?", String.class, RateLimitPolicy.KHOA_KET_XUAT);
        Matcher m = Pattern.compile("max=(\\d+)").matcher(validation);
        assertThat(m.find()).as("validation phải có max=").isTrue();
        assertThat(Integer.parseInt(m.group(1))).isEqualTo(RateLimitPolicy.TRAN_KET_XUAT);
        assertThat(RateLimitPolicy.kepKetXuat(0))
                .as("0 ⛔ được khoá chết kết xuất")
                .isEqualTo(1);
    }

    private void dat(String giaTri) {
        jdbc.update(
                "UPDATE settings SET setting_value = ? WHERE setting_key = ?", giaTri, RateLimitPolicy.KHOA_KET_XUAT);
        settings.invalidate(RateLimitPolicy.KHOA_KET_XUAT);
    }
}
