package com.songnhue.app.hr;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * <b>Mỗi khoá {@code hr.*} còn sống trong {@code settings} phải có một dòng mã đọc nó — T74.5.</b>
 *
 * <p>Quy tắc 15: tham số chưa ai đọc là một lỗi. {@code PortalSettingsReadTest} canh luật ấy cho {@code site.*}/
 * {@code company.*} và <b>tự khai</b> rằng {@code hr.*} nằm ngoài tầm của nó — nên suốt từ 13/08 ⛔ bộ canh nào
 * giữ nhóm này: T42.15 đo ra <b>15 khoá {@code hr.*} với 0 nơi đọc</b> treo bốn tuần trên màn hình Cấu hình. Lượt
 * T68.10 gỡ ba khoá 3 bậc và thêm bốn khoá mới — đúng loại thay đổi dễ để lại nửa cặp đọc–ghi nhất.
 *
 * <h2>Phạm vi — ĐO, ⛔ gõ tay (luật 28)</h2>
 *
 * <ul>
 *   <li>Khoá sống: mọi {@code V*.sql} dưới {@code backend/<module>/src/main/resources/db}, áp theo SỐ HIỆU (như
 *       Flyway), trừ khoá bị {@code DELETE} ở migration sau. Seed nhóm này nằm ở CẢ {@code core} lẫn {@code hr}.
 *   <li>Nơi đọc: chuỗi ký tự {@code "hr.…"} trong {@code backend/hr/src/main/java}.
 *   <li>⚠ Giới hạn, nói ra: một chuỗi kết thúc bằng dấu chấm ({@code "hr.document.max-mb."}) là nơi đọc cho MỌI
 *       khoá mang tiền tố ấy — bộ canh ⛔ phân biệt được một hậu tố seed thừa (thư mục ⛔ tồn tại).
 * </ul>
 */
class HrSettingsReadTest {

    private static final String GOC_BACKEND = "backend";
    private static final String MA_HR = "backend/hr/src/main/java";

    /**
     * Dòng seed {@code ('khoa', 'giá trị', 'KIỂU',} — ⚠ nhận cả {@code VALUES (} XUỐNG DÒNG rồi mới tới khoá:
     * {@code V202609201092} viết kiểu ấy, và mẫu đòi {@code ('} liền nhau (như bộ canh cổng) sẽ MÙ trước nó.
     */
    static final Pattern KHOA_SEED = Pattern.compile("\\(\\s*'([a-z0-9.\\-]+)',\\s*'[^']*',\\s*'[A-Z]+',");

    static final Pattern CAU_XOA = Pattern.compile(
            "DELETE\\s+FROM\\s+settings\\s+WHERE\\s+setting_key\\s+IN\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private static final Pattern MOT_KHOA = Pattern.compile("'([a-z0-9.\\-]+)'");

    private static final Pattern CHUOI_HR = Pattern.compile("\"(hr\\.[a-z0-9.\\-]+)\"");

    private static final Pattern SO_HIEU = Pattern.compile("^V(\\d+)__");

    @Test
    @DisplayName("⛔ MỌI khoá hr.* còn sống đều có nơi đọc trong backend/hr")
    void moiKhoaDeuCoNguoiDoc() {
        Set<String> noiDoc = chuoiHr();
        List<String> khongAiDoc =
                khoaSong().stream().filter(k -> !duocDoc(k, noiDoc)).toList();
        assertThat(khongAiDoc)
                .as(
                        """
                        Seed vào `settings` mà ⛔ dòng mã nào của backend/hr đọc: %s

                        Quy tắc 15 — người vận hành sửa ô ấy, hệ báo *lưu thành công*, và ⛔ gì đổi. Chọn một: \
                        viết nơi đọc, hoặc DELETE khoá ở một migration mới.""",
                        khongAiDoc)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ T68.10 — ba khoá 3 bậc đã gỡ ⛔ còn sống, ⛔ còn ai đọc; bốn khoá mới CÓ mặt")
    void khoaDieu114() {
        List<String> song = khoaSong();
        Set<String> noiDoc = chuoiHr();
        for (String cu : List.of(
                "hr.leave.annual-days.under-5-years",
                "hr.leave.annual-days.5-to-10-years",
                "hr.leave.annual-days.over-10-years")) {
            assertThat(song).as("khoá đã gỡ ở V202609201091").doesNotContain(cu);
            assertThat(noiDoc)
                    .as("gỡ khoá mà quên gỡ nơi đọc ⇒ tham số lặng lẽ rơi về dự phòng")
                    .doesNotContain(cu);
        }
        assertThat(song)
                .contains(
                        "hr.leave.annual-days.base",
                        "hr.leave.annual-days.seniority-step-years",
                        "hr.leave.annual-days.seniority-step-days",
                        "hr.leave.first-fully-recorded-year");
    }

    @Test
    @DisplayName("⚠ Chống tập rỗng — đo ra đủ khoá hr.* và đủ mã nguồn, ⛔ xanh trên danh sách rỗng (luật 7)")
    void doDuocCaHaiNguon() {
        assertThat(khoaSong()).as("khoá hr.* sống").hasSizeGreaterThanOrEqualTo(15);
        assertThat(chuoiHr()).as("chuỗi hr.* trong mã").hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    @DisplayName("⛔ Tự kiểm: mẫu bắt dòng seed liền và XUỐNG DÒNG, bỏ qua DELETE; tiền tố có dấu chấm là nơi đọc")
    void tuKiem() {
        String mau =
                """
                VALUES ('hr.a.b', '1', 'INTEGER',
                VALUES (
                    'hr.c.d', '2027', 'INTEGER', '2027',
                DELETE FROM settings WHERE setting_key IN (
                    'hr.a.b'
                );
                """;
        assertThat(KHOA_SEED.matcher(mau).results().map(r -> r.group(1)).toList())
                .as("⛔ bỏ sót dạng xuống dòng là để khoá mới đi lọt")
                .containsExactly("hr.a.b", "hr.c.d");
        Matcher xoa = CAU_XOA.matcher(mau);
        assertThat(xoa.find()).isTrue();
        assertThat(MOT_KHOA.matcher(xoa.group(1)).results().map(r -> r.group(1)).toList())
                .containsExactly("hr.a.b");

        assertThat(duocDoc("hr.document.max-mb.HOP_DONG", Set.of("hr.document.max-mb.")))
                .isTrue();
        assertThat(duocDoc("hr.document.max-mb", Set.of("hr.document.max-mb.")))
                .as("tiền tố ⛔ tính cho chính nó bỏ dấu chấm")
                .isFalse();
        assertThat(duocDoc("hr.x.y", Set.of("hr.x.yz"))).isFalse();
    }

    // ---- Trích dữ liệu -------------------------------------------------------

    static boolean duocDoc(String khoa, Set<String> noiDoc) {
        return noiDoc.contains(khoa) || noiDoc.stream().anyMatch(t -> t.endsWith(".") && khoa.startsWith(t));
    }

    private static List<String> khoaSong() {
        Path goc = timTuGocKho(GOC_BACKEND);
        List<Path> tep;
        try (Stream<Path> luot = Files.walk(goc)) {
            tep = luot.filter(p -> p.toString().contains("/src/main/resources/db/"))
                    .filter(p -> SO_HIEU.matcher(p.getFileName().toString()).find())
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(HrSettingsReadTest::soHieu))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(tep).as("⛔ đọc được migration nào dưới %s", goc).hasSizeGreaterThan(50);

        Set<String> song = new LinkedHashSet<>();
        for (Path p : tep) {
            String sql = docTep(p).replaceAll("(?m)--.*$", "");
            KHOA_SEED
                    .matcher(sql)
                    .results()
                    .map(r -> r.group(1))
                    .filter(k -> k.startsWith("hr."))
                    .forEach(song::add);
            CAU_XOA.matcher(sql)
                    .results()
                    .flatMap(cau -> MOT_KHOA.matcher(cau.group(1)).results())
                    .map(r -> r.group(1))
                    .forEach(song::remove);
        }
        return song.stream().sorted().toList();
    }

    private static java.math.BigInteger soHieu(Path p) {
        Matcher m = SO_HIEU.matcher(p.getFileName().toString());
        return m.find() ? new java.math.BigInteger(m.group(1)) : java.math.BigInteger.ZERO;
    }

    private static Set<String> chuoiHr() {
        Set<String> ra = new LinkedHashSet<>();
        try (Stream<Path> luot = Files.walk(timTuGocKho(MA_HR))) {
            luot.filter(p -> p.toString().endsWith(".java")).forEach(p -> CHUOI_HR.matcher(docTep(p))
                    .results()
                    .map(r -> r.group(1))
                    .forEach(ra::add));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ra;
    }

    private static String docTep(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path timTuGocKho(String duongDan) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDan);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ thấy " + duongDan + " từ " + System.getProperty("user.dir"));
    }
}
