package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    /**
     * ⚠⚠ <b>Phạm vi đã mở rộng ngày 28/08/2026 — và việc mở rộng ấy lộ ra 4 khoá chết.</b>
     *
     * <p>Bản đầu (T24.19) chỉ soi <b>một</b> tệp: migration của đợt 27/8. Nó bắt đúng luật nhưng
     * canh sai chỗ — mọi khoá seed <i>trước</i> đợt ấy đều đi lọt. Đo lại khi mở phạm vi ra cả thư
     * mục: {@code site.analytics.ga-tracking-id}, {@code site.analytics.gtm-container-id},
     * {@code site.color.primary}, {@code site.color.secondary} — <b>0 nơi đọc</b>, bày trên màn hình
     * Cấu hình hệ thống từ 19/8 (đã gỡ ở {@code V202608281037}).
     *
     * <p>Cùng hình dạng với {@code NginxSecurityHeadersTest} chỉ soi {@code admin-app.Dockerfile}
     * trong khi cổng công khai chạy không có CSP nào (§10.61): <i>một cơ chế canh gác tồn tại
     * trong mã nhưng phạm vi của nó hẹp hơn nơi nó phải chặn</i>. Bộ canh xanh, và cái xanh ấy
     * đọc như một lời bảo đảm.
     */
    private static final String THU_MUC_MIGRATION_CMS = "backend/content/src/main/resources/db/migration/cms";

    private static final String MA_CONG = "frontend/public-web/src";

    /**
     * Khoá seed — dòng {@code ('khoa', 'giá trị', 'KIỂU',} của khối {@code VALUES}.
     *
     * <p>⚠ Chỉ bắt dòng {@code ('khoa', 'giá trị', 'KIỂU',} của khối {@code VALUES} — <b>không</b>
     * bắt khoá nằm trong câu {@code DELETE}. Hai khoá bị gỡ ({@code site.home.blocks},
     * {@code site.slider.effect}) cũng xuất hiện trong tệp này, và đòi chúng có người đọc là đúng
     * ngược lại ý định.
     */
    private static final Pattern KHOA_SEED = Pattern.compile("\\('([a-z0-9.\\-]+)',\\s*'[^']*',\\s*'[A-Z]+',");

    /**
     * Câu {@code DELETE FROM settings WHERE setting_key IN (...)} — bắt <b>cả câu</b>, rồi mới trích
     * khoá bên trong bằng {@link #MOT_KHOA}.
     *
     * <p>Hai bước thay vì một biểu thức lồng: một mẫu vừa xác định ngữ cảnh vừa trích giá trị thì
     * phải dùng lookbehind, và một lookbehind gõ sai vẫn biên dịch được rồi khớp 0 lần — tức bộ canh
     * xanh mà không trừ khoá nào. {@code kiemChungNguocDelete} kiểm cả hai bước.
     */
    private static final Pattern CAU_XOA = Pattern.compile(
            "DELETE\\s+FROM\\s+settings\\s+WHERE\\s+setting_key\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern MOT_KHOA = Pattern.compile("'([a-z0-9.\\-]+)'");

    @Test
    @DisplayName("⛔ MỌI khoá site.*/company.* còn sống đều có nơi đọc trong mã cổng")
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
        assertThat(khoaSeed())
                .as("không trích được khoá site.*/company.* nào từ %s", THU_MUC_MIGRATION_CMS)
                .hasSizeGreaterThanOrEqualTo(20);
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

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: khoá bị DELETE ở migration sau bị TRỪ khỏi danh sách")
    void kiemChungNguocDelete() {
        // Luật 1. Không có bài này thì `CAU_XOA` gõ sai vẫn khớp 0 lần, không khoá nào bị trừ, và
        // bài chính sẽ đòi `site.home.blocks` (đã gỡ 27/8) phải có người đọc — đỏ vì lý do sai.
        Matcher cau = CAU_XOA.matcher("DELETE FROM settings WHERE setting_key IN ('a.b', 'c.d');");
        assertThat(cau.find()).as("không bắt được câu DELETE").isTrue();
        assertThat(MOT_KHOA.matcher(cau.group(1)).results().map(r -> r.group(1)).toList())
                .containsExactly("a.b", "c.d");

        // ⛔ Và một câu DELETE đã bị `--` vô hiệu hoá thì KHÔNG được tính là đã chạy. Đây là lỗ đã
        //    lộ ra khi kiểm chứng ngược lượt đầu; xem `boChuThichSql`.
        assertThat(CAU_XOA.matcher(boChuThichSql("-- DELETE FROM settings WHERE setting_key IN ('a.b');"))
                        .find())
                .as("câu DELETE trong chú thích vẫn bị tính là đã chạy")
                .isFalse();

        // Và vế thật: bốn khoá gỡ ở V202608281037 phải KHÔNG còn trong danh sách phải-có-người-đọc.
        assertThat(khoaSeed())
                .as("khoá đã DELETE mà vẫn bị đòi có người đọc")
                .doesNotContain(
                        "site.analytics.ga-tracking-id",
                        "site.analytics.gtm-container-id",
                        "site.color.primary",
                        "site.color.secondary",
                        "site.home.blocks",
                        "site.slider.effect");
    }

    // ---- Trích dữ liệu -------------------------------------------------------

    /**
     * Mọi khoá nhóm {@code site.*} / {@code company.*} còn sống sau khi áp hết migration CMS.
     *
     * <p>Trừ đi khoá bị {@code DELETE} ở một migration <i>sau</i> lượt seed nó: hai câu ấy nằm ở hai
     * tệp khác nhau, nên đọc từng tệp riêng lẻ sẽ đòi một khoá đã gỡ phải có người đọc — đúng ngược
     * ý định. Thứ tự áp = thứ tự tên tệp, giống Flyway.
     */
    private static List<String> khoaSeed() {
        List<Path> tep;
        try (Stream<Path> luot = Files.list(timTuGocKho(THU_MUC_MIGRATION_CMS))) {
            tep = luot.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new IllegalStateException("Không liệt kê được " + THU_MUC_MIGRATION_CMS, e);
        }

        Set<String> song = new LinkedHashSet<>();
        for (Path p : tep) {
            String sql = boChuThichSql(doc(THU_MUC_MIGRATION_CMS + "/" + p.getFileName()));
            KHOA_SEED
                    .matcher(sql)
                    .results()
                    .map(r -> r.group(1))
                    .filter(PortalSettingsReadTest::laKhoaCong)
                    .forEach(song::add);
            CAU_XOA.matcher(sql)
                    .results()
                    .flatMap(cau -> MOT_KHOA.matcher(cau.group(1)).results())
                    .map(r -> r.group(1))
                    .forEach(song::remove);
        }
        return song.stream().sorted().toList();
    }

    /**
     * Bỏ chú thích {@code --} trước khi soi.
     *
     * <p>⚠ Phát hiện ngày 28/08/2026 <b>trong lúc kiểm chứng ngược chính bài kiểm này</b>: lượt phá
     * hoại có chủ đích đặt {@code --} trước câu {@code DELETE} rồi chờ bộ canh đỏ lên. Nó
     * <b>không đỏ</b> — {@link #CAU_XOA} là biểu thức chính quy, nó không biết SQL có chú thích, nên
     * một câu {@code DELETE} đã bị vô hiệu hoá vẫn được tính là đã chạy và bốn khoá vẫn bị trừ.
     *
     * <p>Đúng bài học 10 ở dạng thuần khiết nhất: <i>làm hỏng có chủ đích để kiểm chứng thì phải xác
     * nhận bản hỏng ĐÃ được nạp</i> — ở đây bản hỏng được nạp, mà bộ canh vẫn mù trước nó. Nếu lượt
     * kiểm chứng ấy không chạy, lỗ này nằm lại vĩnh viễn và không có triệu chứng nào.
     */
    private static String boChuThichSql(String sql) {
        return sql.replaceAll("(?m)--.*$", "");
    }

    /**
     * Chỉ soi khoá mà <b>cổng công khai</b> chịu trách nhiệm hiển thị.
     *
     * <p>{@code hr.*}, {@code hydro.*}, {@code security.*}, {@code limits.*}, {@code ops.*} là tham
     * số nghiệp vụ do backend đọc — chúng cũng phải có người đọc, nhưng người đọc nằm ở Java chứ
     * không ở mã cổng, nên hỏi chúng ở đây sẽ ra một câu trả lời sai.
     */
    private static boolean laKhoaCong(String khoa) {
        return khoa.startsWith("site.") || khoa.startsWith("company.");
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
