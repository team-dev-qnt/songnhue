package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mỗi khoá {@code settings} của cổng phải có một dòng mã đọc nó.</b>
 *
 * <h2>Luật đã trả giá — quy tắc 15</h2>
 *
 * <i>Công tắc / cột / tham số chưa ai đọc là một lỗi, không phải việc để dành.</i> Ba khoá từng
 * bày ra trên màn hình cấu hình mà không dòng mã nào đọc: {@code limits.upload.max-mb.*},
 * {@code company.*}, {@code attachments.valid_from}. Hậu quả không phải chuyện thẩm mỹ — quản trị
 * viên đặt giá trị, hệ thống báo <i>lưu thành công</i>, và không có gì thay đổi. Người dùng không
 * có cách nào biết mình vừa bị lừa.
 *
 * <p>{@code SiteLayoutTest.khongCoCongTacWidgetThuyVan} canh cùng luật này ở phía backend, nhưng
 * nó chỉ khẳng định được <i>vắng mặt</i> (không có khoá nào chứa "hydro"). Vế khó hơn — <i>khoá
 * có mặt thì phải có người đọc</i> — bắt buộc phải nhìn sang mã của cổng, và đó là việc của lớp
 * này.
 *
 * <h2>⚠ Vì sao ở backend chứ không ở public-web</h2>
 *
 * Dự án đã có bảy lớp kiểm phía backend đọc {@code frontend/} và {@code deploy/}
 * ({@code FrontendSameOriginTest}, {@code NginxSecurityHeadersTest}…), và bộ lọc đường dẫn của CI
 * đã được sửa để chạy job này khi những thư mục ấy đổi (quy tắc 24). Đặt chiều đọc ngược lại —
 * test của FE đi đọc SQL của BE — là mở một chiều phụ thuộc thứ hai mà bộ lọc CI chưa biết tới.
 */
class PortalSettingsReadTest {

    private static final String MIGRATION =
            "backend/content/src/main/resources/db/migration/cms/V202608271032__cms_portal_settings_v2.sql";

    private static final String MA_CONG = "frontend/public-web/src";

    /**
     * Khoá seed ở migration đợt 27/08/2026.
     *
     * <p>⚠ Chỉ bắt dòng {@code ('khoa', 'giá trị', 'KIỂU',} của khối {@code VALUES} — <b>không</b>
     * bắt khoá nằm trong câu {@code DELETE}. Hai khoá bị gỡ ({@code site.home.blocks},
     * {@code site.slider.effect}) cũng xuất hiện trong tệp này, và đòi chúng có người đọc là đúng
     * ngược lại ý định.
     */
    private static final Pattern KHOA_SEED = Pattern.compile("\\('([a-z0-9.\\-]+)',\\s*'[^']*',\\s*'[A-Z]+',");

    @Test
    @DisplayName("⛔ Mọi khoá settings seed ở đợt 27/08/2026 đều có nơi đọc trong mã cổng")
    void moiKhoaDeuCoNguoiDoc() throws IOException {
        String maCong = docCaThuMuc();
        List<String> khongAiDoc = khoaSeed().stream()
                .filter(khoa -> !maCong.contains("'" + khoa + "'"))
                .toList();

        assertThat(khongAiDoc)
                .as(
                        """
                        Những khoá này được seed vào `settings` nhưng KHÔNG dòng mã nào của cổng đọc: %s

                        Quy tắc 15: công tắc chưa ai đọc là một lỗi. Quản trị viên sẽ đặt giá trị, hệ \
                        thống báo lưu thành công, và không có gì thay đổi — đúng lỗi đã sửa ở WS-12 và \
                        đã canh ở SiteLayoutTest.khongCoCongTacWidgetThuyVan.

                        Chọn một: viết dòng mã đọc nó, hoặc đừng seed nó.""",
                        khongAiDoc)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Hai khoá đã gỡ không được đọc ở đâu nữa")
    void khoaDaGoKhongConNoiDoc() throws IOException {
        String maCong = docCaThuMuc();
        // Vế ngược của bài trên: gỡ khoá khỏi CSDL mà quên gỡ nơi đọc thì cổng lặng lẽ rơi về giá
        // trị mặc định viết trong mã — tức là tham số ấy thôi cấu hình được, mà không ai biết.
        for (String khoa : List.of("site.home.blocks", "site.slider.effect")) {
            assertThat(maCong.contains("'" + khoa + "'"))
                    .as("`%s` đã bị DELETE ở V202608271032 nhưng mã cổng vẫn đọc nó", khoa)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("⚠ Bài kiểm đọc được cả hai nguồn — chạy qua tập rỗng thì xanh mà không canh gì")
    void doDuocCaHaiNguon() throws IOException {
        // Luật 7. Đổi cách viết khối VALUES là `KHOA_SEED` khớp 0 dòng, và bài trên sẽ duyệt qua
        // một danh sách rỗng rồi xanh trọn vẹn.
        assertThat(khoaSeed()).as("không trích được khoá nào từ %s", MIGRATION).hasSizeGreaterThanOrEqualTo(6);
        assertThat(docCaThuMuc()).as("không đọc được mã cổng ở %s", MA_CONG).hasSizeGreaterThan(10_000);
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: mẫu KHOA_SEED bắt đúng dòng VALUES và bỏ qua dòng DELETE")
    void kiemChungNguoc() {
        String mau =
                """
                    ('site.abc.def', 'x', 'STRING',
                     'SITE', 'Nhãn', NULL, NULL, 90),
                DELETE FROM settings WHERE setting_key IN ('site.da.go');
                """;
        Matcher m = KHOA_SEED.matcher(mau);
        assertThat(m.find()).isTrue();
        assertThat(m.group(1)).isEqualTo("site.abc.def");
        assertThat(m.find()).as("không được bắt khoá trong câu DELETE").isFalse();
    }

    // ---- Trích dữ liệu -------------------------------------------------------

    private static List<String> khoaSeed() {
        Matcher m = KHOA_SEED.matcher(doc(MIGRATION));
        return m.results().map(r -> r.group(1)).distinct().sorted().toList();
    }

    /** Nối toàn bộ mã nguồn của cổng — đủ cho phép hỏi "có ai nhắc tới khoá này không". */
    private static String docCaThuMuc() throws IOException {
        Path goc = timTuGocKho(MA_CONG);
        try (Stream<Path> luot = Files.walk(goc)) {
            List<Path> tep = luot.filter(Files::isRegularFile)
                    .filter(p -> {
                        String ten = p.getFileName().toString();
                        return (ten.endsWith(".ts") || ten.endsWith(".tsx")) && !ten.contains(".test.");
                    })
                    .toList();
            assertThat(tep).as("không thấy tệp nguồn nào trong %s", goc).isNotEmpty();

            StringBuilder gop = new StringBuilder();
            for (Path p : tep) {
                gop.append(Files.readString(p, StandardCharsets.UTF_8)).append('\n');
            }
            return gop.toString();
        }
    }

    private static String doc(String duongDanTuongDoi) {
        try {
            return Files.readString(timTuGocKho(duongDanTuongDoi), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDanTuongDoi, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s".formatted(duongDanTuongDoi, System.getProperty("user.dir")));
    }
}
