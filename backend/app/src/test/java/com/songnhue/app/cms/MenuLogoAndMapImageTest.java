package com.songnhue.app.cms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.CmsFixtures;
import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.content.api.PublicPortalController;
import com.songnhue.content.application.MenuService;
import com.songnhue.content.application.PublicPortalService;
import com.songnhue.content.application.SiteConfigService;
import com.songnhue.content.domain.MenuLinkType;
import com.songnhue.content.domain.MenuPosition;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;
import com.songnhue.core.spi.SettingItem;

/**
 * Hai ô trên trang chủ mà Công ty <b>không có cách nào nhập</b> cho tới 29/08/2026 — CR-21, CN-02.4.
 *
 * <h2>Vì sao hai việc rất khác nhau lại nằm chung một bài kiểm</h2>
 *
 * Vì chúng cùng một <b>hình dạng lỗi</b>, và đó là hình dạng đắt nhất của dự án này: một khối hiển
 * thị đã hoàn chỉnh nằm chờ một đường nhập liệu <i>không tồn tại</i>. Dải "Liên kết website" vẽ
 * được logo nhưng bảng không có cột ảnh; khối "Bản đồ hệ thống công trình" vẽ được ảnh nhưng
 * {@code settings} không có khoá ảnh. Cả hai đều <b>không có gì đỏ</b> — chúng chỉ rỗng, và ai nhìn
 * cũng tưởng là "chờ dữ liệu Công ty".
 *
 * <p>Nên bài này canh đúng thứ phân biệt được hai trạng thái ấy: <b>vòng khép kín nhập → lưu →
 * hiện</b> (quy tắc 27). Không đủ vòng thì màn hình quản trị báo <i>lưu thành công</i> và cổng
 * không đổi gì.
 */
class MenuLogoAndMapImageTest extends IntegrationTestBase {

    @Autowired
    private MenuService menus;

    @Autowired
    private SiteConfigService siteConfig;

    @Autowired
    private PublicPortalService portal;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void dangNhap() {
        CmsFixtures.donDep(jdbc);
        CmsFixtures.datLaiCauHinhSite(jdbc);
        AuthContext.set(new AuthenticatedUser(
                1L,
                UUID.randomUUID(),
                "logo-probe",
                "Người kiểm thử",
                1L,
                "/1/",
                Set.of("PROBE"),
                Set.of("cms:layout:manage"),
                false,
                UUID.randomUUID(),
                UUID.randomUUID()));
    }

    @AfterEach
    void donDep() {
        AuthContext.clear();
        CmsFixtures.donDep(jdbc);
        CmsFixtures.datLaiCauHinhSite(jdbc);
    }

    // =========================================================================
    // 1. Logo của dải "Liên kết website"
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Vòng khép kín: tải logo ở LIEN_KET → id ra tới DTO CÔNG KHAI")
    void logoDiHetVongNhapLuuHien() {
        UUID muc = menus.create(
                        MenuPosition.LIEN_KET,
                        null,
                        "Bộ Nông nghiệp & PTNT",
                        new MenuService.Target(MenuLinkType.URL, null, null, "https://mard.gov.vn"))
                .publicId();

        // Trước khi tải: cổng phải nói RÕ là chưa có, chứ không phải trả một id rác.
        assertThat(logoTrenCong(muc)).isNull();

        MenuService.MenuNode sauKhiTai = menus.uploadLogo(muc, "logo.png", anhPng(60, 40));

        assertThat(sauKhiTai.logoAttachmentId())
                .as("Lượt tải phải trả về ngay id mới — màn hình quản trị vẽ lại thẻ từ đúng đáp án này")
                .isNotNull();

        // ⭐ Đây mới là phép khẳng định đắt giá: đi qua ĐÚNG đường cổng công khai đi, không đọc
        //    lại đối tượng service vừa trả. Vế `MenuLink.logoId` là chỗ dễ quên nhất của cả lượt
        //    này — quên nó thì CSDL có logo, màn hình quản trị hiện logo, và cổng vẫn là thẻ chữ.
        assertThat(logoTrenCong(muc))
                .as("`logoId` phải đi hết đường tuần tự hoá ra tới `GET /public/menus/LIEN_KET`")
                .isEqualTo(sauKhiTai.logoAttachmentId());
    }

    @Test
    @DisplayName("⛔ HEADER và FOOTER KHÔNG đặt được logo — cột không được có nửa cặp đọc–ghi")
    void chiLienKetMoiDatDuocLogo() {
        UUID header = menus.create(MenuPosition.HEADER, null, "Giới thiệu", MenuService.Target.none())
                .publicId();
        UUID footer = menus.create(MenuPosition.FOOTER, null, "Sơ đồ cổng", MenuService.Target.none())
                .publicId();

        // Menu đầu trang và chân trang là menu CHỮ: cổng không dựng ô ảnh nào cho chúng. Cho tải
        // vào đấy là tạo một cột có người ghi mà không ai đọc (quy tắc 15), và triệu chứng im
        // lặng: quản trị báo lưu thành công, cổng không đổi gì.
        for (UUID muc : List.of(header, footer)) {
            assertThatThrownBy(() -> menus.uploadLogo(muc, "logo.png", anhPng(10, 10)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("CMS-2015");
        }
    }

    @Test
    @DisplayName("⭐ Gỡ logo trả mục về thẻ chữ — không để lại id trỏ vào hư không")
    void goLogoTraVeNull() {
        UUID muc = menus.create(
                        MenuPosition.LIEN_KET,
                        null,
                        "Cục Thuỷ lợi",
                        new MenuService.Target(MenuLinkType.URL, null, null, "http://cucthuyloi.gov.vn"))
                .publicId();
        menus.uploadLogo(muc, "logo.png", anhPng(40, 40));
        assertThat(logoTrenCong(muc)).isNotNull();

        menus.removeLogo(muc);

        assertThat(logoTrenCong(muc))
                .as("Gỡ xong thì cổng phải quay về thẻ chữ, không giữ một id đã thôi dùng")
                .isNull();
    }

    @Test
    @DisplayName("⭐⭐ Tệp logo TẢI VỀ ĐƯỢC qua đường công khai — `MENU_ITEM` nằm trong danh sách trắng")
    void byteLogoRaDuocDuongCongKhai() throws java.io.IOException {
        UUID muc = menus.create(
                        MenuPosition.LIEN_KET,
                        null,
                        "UBND Thành phố Hà Nội",
                        new MenuService.Target(MenuLinkType.URL, null, null, "https://hanoi.gov.vn"))
                .publicId();
        byte[] goc = anhPng(48, 32);
        UUID anh = menus.uploadLogo(muc, "logo.png", goc).logoAttachmentId();
        sanSangPhucVu(anh);

        // ⛔ Không có bước này thì cả lượt là công cốc theo cách khó truy nhất: CSDL nói tệp tồn
        //    tại, DTO trả id, và `GET /api/v1/public/files/<id>` trả 404 vì loại chủ sở hữu
        //    `MENU_ITEM` không nằm trong `LOAI_TEP_CONG_KHAI`. Hỏng câm, đúng §10.52.
        // ⚠ T28.35 — `content()` nay là `InputStream` (ba endpoint công khai PHÁT TRỰC TIẾP thay vì
        //   nạp trọn tệp vào heap). ⇒ khẳng định phải ĐỌC luồng, và đó là một phép đo MẠNH HƠN bản
        //   cũ: bản cũ hỏi độ dài một mảng đã nằm sẵn trong bộ nhớ, bản này chứng minh byte thật sự
        //   CHẢY được từ kho ra — đúng thứ §10.52 trả giá vì thiếu.
        assertThat(portal.file(anh))
                .as("Byte logo phải ra được đường công khai — thẻ trên trang chủ tải bằng chính đường ấy")
                .isPresent();
        try (java.io.InputStream luong = portal.file(anh).orElseThrow().content()) {
            assertThat(luong.readAllBytes())
                    .as("⛔ Đúng nguyên văn byte đã tải lên — ⛔ không phải 'có một luồng nào đó'")
                    .isEqualTo(goc);
        }
    }

    // =========================================================================
    // 2. Ảnh sơ đồ hệ thống công trình
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Vòng khép kín: tải ảnh sơ đồ → giá trị ra tới `/public/site-config`")
    void anhSoDoDiHetVong() {
        assertThat(portal.siteConfig().get(SiteConfigService.KEY_HOME_MAP))
                .as("Khoá phải TỒN TẠI ngay khi chưa ai tải ảnh — rỗng là một câu trả lời, "
                        + "còn thiếu khoá thì cổng không có gì để đọc")
                .isEmpty();

        UUID anh = siteConfig
                .uploadBrandImage(SiteConfigService.KEY_HOME_MAP, "so-do.png", anhPng(800, 450))
                .publicId();
        sanSangPhucVu(anh);

        assertThat(portal.siteConfig().get(SiteConfigService.KEY_HOME_MAP))
                .as("Trang chủ đọc khoá này từ `/public/site-config`; không tới đây thì ô vẫn rỗng")
                .isEqualTo(anh.toString());
        assertThat(portal.file(anh)).isPresent();
    }

    @Test
    @DisplayName("⛔⛔ MỌI khoá ảnh phải mang hậu tố `.attachment-id` — hậu tố ấy LÁI giao diện")
    void moiKhoaAnhDeuCoHauToDungGiaoDienDoc() {
        // `SiteConfigTab.tsx` chia danh sách tham số bằng đúng một dòng:
        //     items.filter((item) => item.key.endsWith('.attachment-id'))
        // Khoá nào có hậu tố ấy thì được dựng ô TẢI ẢNH; khoá khác chỉ có ô nhập chữ. Đặt tên
        // thiếu hậu tố nghĩa là Công ty nhận một ô để gõ UUID vào — tức không ai làm được.
        assertThat(SiteConfigService.KHOA_ANH)
                .as("Tập khoá ảnh không được rỗng — bài kiểm chạy qua tập rỗng thì xanh mà không canh gì (luật 7)")
                .hasSizeGreaterThanOrEqualTo(3)
                .allSatisfy(khoa -> assertThat(khoa).endsWith(".attachment-id"));

        // Và mỗi khoá ấy phải THẬT SỰ có một dòng trong `settings`, nếu không màn hình cấu hình
        // không liệt kê nó ra và ô tải ảnh không bao giờ được dựng.
        List<String> coTrongSettings =
                siteConfig.list().stream().map(SettingItem::key).toList();
        assertThat(coTrongSettings).containsAll(SiteConfigService.KHOA_ANH);
    }

    // =========================================================================
    // 3. Ranh giới tệp công khai — bài kiểm này lẽ ra phải có từ T16.6
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Danh sách loại tệp công khai: bốn loại ĐƯỢC, hồ sơ nhân sự và tài liệu công trình KHÔNG")
    void ranhGioiTepCongKhaiDungCaHaiChieu() {
        // ⚠ Danh sách này là toàn bộ ranh giới bảo vệ kho tài liệu, và cho tới 29/08 nó **không có
        //   phép kiểm nào** — thêm một dòng vào đó là mở đường đọc mọi tệp thuộc loại ấy cho bất
        //   kỳ ai biết `publicId`. Lượt này thêm `MENU_ITEM`, nên dựng luôn bộ canh (quy tắc 1).
        assertThat(PublicPortalService.LOAI_TEP_CONG_KHAI)
                .containsExactlyInAnyOrder("MEDIA_FOLDER", "BANNER", "SITE_CONFIG", "MENU_ITEM");

        // Vế NGƯỢC là vế đắt: một khẳng định chỉ liệt kê thứ được phép sẽ vẫn xanh sau khi ai đó
        // thêm `EMPLOYEE` — `containsExactlyInAnyOrder` bắt được, nhưng nói ra đích danh hai loại
        // nhạy cảm thì người sửa đọc được lý do ngay trong thông báo lỗi.
        assertThat(PublicPortalService.LOAI_TEP_CONG_KHAI)
                .as("Hồ sơ nhân sự và tài liệu công trình có màn hình riêng, SAU đăng nhập — "
                        + "đưa chúng vào đây là công bố toàn bộ kho ra Internet")
                .doesNotContain("EMPLOYEE", "CONSTRUCTION");
    }

    // -------------------------------------------------------------------------

    private UUID logoTrenCong(UUID mucPublicId) {
        MenuService.MenuNode node = portal.menu(MenuPosition.LIEN_KET).stream()
                .filter(x -> x.publicId().equals(mucPublicId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Không thấy mục vừa tạo trên đường công khai"));
        PublicPortalController.MenuLink link = new PublicPortalController.MenuLink(
                node.label(),
                node.linkType().name(),
                node.categorySlug(),
                node.articleSlug(),
                node.url(),
                node.openNewTab(),
                node.depth(),
                null,
                node.logoAttachmentId());
        return link.logoId();
    }

    /**
     * Đưa tệp vừa tải sang trạng thái phục vụ được.
     *
     * <p>⚠ Không phải mẹo cho bài kiểm chạy: {@code readForPublic} đòi {@code status = READY}, và
     * ở môi trường có ClamAV thì bước quét mới đặt trạng thái ấy. Bài này đo <b>ranh giới phục vụ
     * công khai</b> (loại chủ sở hữu), không đo hàng đợi quét — nên mô phỏng đúng kết quả của bước
     * quét, y hệt {@code PublicPortalTest} đã làm. {@code SKIPPED} chứ không phải {@code CLEAN}:
     * ClamAV chưa quét thật, và ghi {@code CLEAN} là nói dối sổ sách về một cơ chế bảo mật.
     */
    private void sanSangPhucVu(UUID tep) {
        jdbc.update("UPDATE attachments SET scan_status = 'SKIPPED', status = 'READY' WHERE public_id = ?", tep);
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
}
