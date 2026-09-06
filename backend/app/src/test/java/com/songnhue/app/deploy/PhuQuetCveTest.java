package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Phạm vi quét CVE — chạy THẬT {@code phu-quet-cve.sh} trên một fat jar giả và một báo cáo giả.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Ngày 5/9 sổ nợ ghi T11.83: <i>"cổng quét soi 110 jar trong khi runtime có 121 — bảy gói chưa từng
 * được quét, mọi con số CVE là cận dưới"</i>. Đo lại 6/9 trên chính báo cáo và fat jar thật thì khẳng
 * định ấy <b>sai</b>: báo cáo có 110 mục top-level <b>cộng 39 mục {@code relatedDependencies}</b>
 * (Dependency-Check gộp jar cùng nhóm/phiên bản dưới một mục cha), phủ 146 tên, 115/121 jar runtime có
 * mặt. Sáu jar ngoài báo cáo là 5 module của chính kho và {@code spring-boot-jarmode-tools} — jar
 * plugin chèn lúc repackage, không nằm trong đồ thị Maven.
 *
 * <p>Người đo sai vì đếm một tầng. Script này làm cho cổng <b>tự nói ra phạm vi của mình mỗi lượt</b>
 * (CLAUDE.md luật 28) bằng phép đo — và bài này chứng minh phép đo ấy phân biệt được <i>phủ đủ</i>,
 * <i>thiếu</i>, và <i>chỉ nằm ở tầng hai</i> (luật 1, luật 9).
 *
 * <h2>Cặp đọc–ghi với {@code app/pom.xml}</h2>
 *
 * Script cố ý <b>không có danh sách ngoại lệ</b>. Jar {@code jarmode-tools} được xử lý bằng cách không
 * đóng nó vào fat jar ({@code <includeTools>false</includeTools>}); bài P9 canh dòng pom ấy, vì bật lại
 * là lượt quét thật đỏ đích danh ở một bước không ai ngờ tới.
 *
 * <h2>⛔ Giới hạn (luật 28)</h2>
 *
 * Bài này chứng minh script <b>so tên đúng</b>. Nó không chứng minh Dependency-Check nhận diện đúng CPE
 * cho từng jar — vế ấy không đo được bằng so tên, và cũng không đo được ở đây.
 */
class PhuQuetCveTest {

    private static final String SCRIPT = ".github/scripts/phu-quet-cve.sh";
    private static final String WORKFLOW = ".github/workflows/security-scan.yml";
    private static final String POM_APP = "backend/app/pom.xml";
    private static final String MODULE_DU_AN = "songnhue-core-0.1.0-SNAPSHOT.jar";

    /** Khoá job cấp một trong {@code security-scan.yml}: đúng hai dấu cách thụt đầu. */
    private static final Pattern KHOA_JOB = Pattern.compile("(?m)^  ([a-z][a-z0-9-]*):$");

    // ── Nhóm 1: hành vi thật của script ────────────────────────────────────────────────────────

    @Test
    @DisplayName("P1 · mọi jar bên thứ ba có trong báo cáo → exit 0, thieu=0, module dự án là `ngoai`")
    void phuDu(@TempDir Path thuMuc) throws Exception {
        Path jar = dungJar(thuMuc, "a-1.jar", "b-2.jar", MODULE_DU_AN);
        Path baoCao = ghi(thuMuc, "bc.json", baoCao(muc("a-1.jar"), muc("b-2.jar")));

        KetQua kq = chay(thuMuc, System.getenv("PATH"), jar, baoCao);

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(dongDau(thuMuc)).isEqualTo("runtime=3 phu=2 ngoai=1 thieu=0");
        assertThat(tepRa(thuMuc))
                .as("Module của chính kho phải được LIỆT KÊ là ngoài phạm vi — nói ra phạm vi, không giấu")
                .contains("NGOAI: " + MODULE_DU_AN)
                .doesNotContain("THIEU:");
    }

    @Test
    @DisplayName("⛔ P2 · một jar bên thứ ba KHÔNG có trong báo cáo → exit 1 và gọi đúng tên nó")
    void thieuMotJarThiDo(@TempDir Path thuMuc) throws Exception {
        Path jar = dungJar(thuMuc, "a-1.jar", "b-2.jar", MODULE_DU_AN);
        Path baoCao = ghi(thuMuc, "bc.json", baoCao(muc("a-1.jar")));

        KetQua kq = chay(thuMuc, System.getenv("PATH"), jar, baoCao);

        assertThat(kq.maThoat())
                .as(
                        """
                        Thiếu jar mà thoát 0 thì "phủ đủ" và "thiếu" cho ra cùng một màu — một khẳng \
                        định không phân biệt được hai trạng thái thì không khẳng định gì (luật 9). \
                        Và `warn` không phải cổng (luật 24).""")
                .isNotZero();
        assertThat(dongDau(thuMuc)).isEqualTo("runtime=3 phu=1 ngoai=1 thieu=1");
        assertThat(tepRa(thuMuc)).contains("THIEU: b-2.jar");
        assertThat(kq.dauRa()).contains("b-2.jar");
    }

    @Test
    @DisplayName("⭐ P3 · jar chỉ nằm ở `relatedDependencies` vẫn là ĐÃ PHỦ — chính bài học T11.83")
    void relatedDependenciesTinhLaPhu(@TempDir Path thuMuc) throws Exception {
        Path jar = dungJar(thuMuc, "a-1.jar", "b-2.jar");
        // b-2.jar KHÔNG có mục top-level nào; nó chỉ là con của zzz-9.jar.
        Path baoCao = ghi(thuMuc, "bc.json", baoCao(muc("a-1.jar"), muc("zzz-9.jar", "b-2.jar")));

        KetQua kq = chay(thuMuc, System.getenv("PATH"), jar, baoCao);

        assertThat(kq.maThoat())
                .as(
                        """
                        Đếm một tầng là đúng cái lỗi đã đưa "110/121, mọi con số là cận dưới" vào sổ nợ \
                        và CLAUDE.md trong một ngày. Dependency-Check gộp jar cùng nhóm/phiên bản dưới \
                        một mục cha; chúng ĐÃ được quét.

                        Đầu ra:
                        %s""",
                        kq.dauRa())
                .isZero();
        assertThat(dongDau(thuMuc)).isEqualTo("runtime=2 phu=2 ngoai=0 thieu=0");
    }

    @Test
    @DisplayName("P4 · tên báo cáo dạng `b-2.jar (shaded: …)` hay `b-2.jar: x.js` vẫn khớp `b-2.jar`")
    void chuanHoaTenJar(@TempDir Path thuMuc) throws Exception {
        Path jar = dungJar(thuMuc, "a-1.jar", "b-2.jar");
        Path baoCao = ghi(
                thuMuc, "bc.json", baoCao(muc("a-1.jar: some/file.js"), muc("b-2.jar (shaded: org.example:x:1.0)")));

        KetQua kq = chay(thuMuc, System.getenv("PATH"), jar, baoCao);

        assertThat(kq.maThoat()).as("Đầu ra:\n%s", kq.dauRa()).isZero();
        assertThat(dongDau(thuMuc)).isEqualTo("runtime=2 phu=2 ngoai=0 thieu=0");
    }

    @Test
    @DisplayName("⭐ P5 · tham số 4 (regex dự án) THẬT SỰ được đọc — đổi nó thì `ngoai` đổi theo")
    void thamSoRegexDuocDoc(@TempDir Path thuMuc) throws Exception {
        Path jar = dungJar(thuMuc, "a-1.jar", "b-2.jar");
        Path baoCao = ghi(thuMuc, "bc.json", baoCao(muc("b-2.jar")));

        // Không truyền tham số 4: a-1.jar là bên thứ ba và thiếu ⇒ đỏ.
        KetQua khongThamSo = chay(thuMuc, System.getenv("PATH"), jar, baoCao);
        assertThat(khongThamSo.maThoat()).isNotZero();
        assertThat(dongDau(thuMuc)).isEqualTo("runtime=2 phu=1 ngoai=0 thieu=1");

        // Truyền `^a-`: a-1.jar thành module dự án ⇒ xanh. Hai lượt khác nhau đúng ở một tham số.
        KetQua coThamSo = chay(thuMuc, System.getenv("PATH"), jar, baoCao, "^a-");
        assertThat(coThamSo.maThoat())
                .as("Tham số 4 không được đọc thì đổi tên module của kho là mọi lượt quét đỏ mà không ai hiểu")
                .isZero();
        assertThat(dongDau(thuMuc)).isEqualTo("runtime=2 phu=1 ngoai=1 thieu=0");
    }

    @Test
    @DisplayName("⛔ P6 · thiếu báo cáo · thiếu jar · jar không có BOOT-INF/lib → đều ĐỎ, không xanh trên tập rỗng")
    void tapRongThiDo(@TempDir Path thuMuc) throws Exception {
        Path jarDu = dungJar(thuMuc, "a-1.jar");
        Path baoCaoDu = ghi(thuMuc, "bc.json", baoCao(muc("a-1.jar")));

        KetQua thieuBaoCao = chay(thuMuc, System.getenv("PATH"), jarDu, thuMuc.resolve("khong-co.json"));
        assertThat(thieuBaoCao.maThoat())
                .as("Thiếu báo cáo:\n%s", thieuBaoCao.dauRa())
                .isNotZero();

        KetQua thieuJar = chay(thuMuc, System.getenv("PATH"), thuMuc.resolve("khong-co.jar"), baoCaoDu);
        assertThat(thieuJar.maThoat()).as("Thiếu jar:\n%s", thieuJar.dauRa()).isNotZero();

        Path jarRong = dungJarTai(thuMuc.resolve("rong.jar"));
        KetQua khongLib = chay(thuMuc, System.getenv("PATH"), jarRong, baoCaoDu);
        assertThat(khongLib.maThoat())
                .as(
                        """
                        Jar không có BOOT-INF/lib mà thoát 0 là "phủ 0/0 = đủ" — phép kiểm chạy qua tập \
                        rỗng vẫn xanh trọn vẹn (luật 7). Đầu ra:
                        %s""",
                        khongLib.dauRa())
                .isNotZero();

        Path khongPhaiJson = ghi(thuMuc, "rac.json", "không phải json");
        KetQua rac = chay(thuMuc, System.getenv("PATH"), jarDu, khongPhaiJson);
        assertThat(rac.maThoat()).as("Báo cáo rác:\n%s", rac.dauRa()).isNotZero();
    }

    @Test
    @DisplayName("⛔ P7 · không có `jq`/`unzip` trên PATH → ĐỎ và nói tên công cụ thiếu")
    void thieuCongCuThiDo(@TempDir Path thuMuc) throws Exception {
        Path jar = dungJar(thuMuc, "a-1.jar");
        Path baoCao = ghi(thuMuc, "bc.json", baoCao(muc("a-1.jar")));
        Path binRong = Files.createDirectories(thuMuc.resolve("bin-rong"));

        KetQua kq = chay(thuMuc, binRong.toString(), jar, baoCao);

        assertThat(kq.maThoat())
                .as("Thiếu công cụ mà thoát 0 là đúng bẫy `verify-no-keys.sh` (conventions.md §1.5)")
                .isNotZero();
        assertThat(kq.dauRa()).containsAnyOf("jq", "unzip");
    }

    // ── Nhóm 2: dây nối trong workflow và pom ──────────────────────────────────────────────────

    @Test
    @DisplayName("⭐ P8 · workflow gọi script với `always()`, giữ tệp ra trong artifact, và `paths:` bao script")
    void dayNoiWorkflow() {
        String than = thanJob("owasp");
        String buoc = buocChua(than, "phu-quet-cve.sh");
        assertThat(dieuKienIf(buoc))
                .as(
                        """
                        Bước đo phạm vi phải `if: always()`: bước quét ngay trước nó ĐỎ là trạng thái \
                        thường trực của job, không có `always()` thì phép đo bị bỏ qua đúng lúc có CVE \
                        và `skipped` đọc như đạt (luật 24). Dòng if đọc được: "%s\"""",
                        dieuKienIf(buoc))
                .contains("always()");
        assertThat(buoc).contains("backend/app/target/songnhue-app.jar");

        String upload = buocChua(than, "upload-artifact");
        assertThat(upload)
                .as("Tệp phạm vi không vào artifact thì chuông không đọc được lý do đỏ — nửa còn lại của cặp đọc–ghi")
                .contains("backend/target/phu-quet-cve.txt");

        String workflow = doc(timTuGocKho(WORKFLOW));
        assertThat(workflow)
                .as("Sửa script đo phạm vi phải làm cổng chạy lại (luật 24, §10.69)")
                .contains("'" + SCRIPT + "'");
    }

    @Test
    @DisplayName(
            "⭐⭐ P9 · cặp đọc–ghi: `app/pom.xml` khai `<includeTools>false</includeTools>` cho spring-boot-maven-plugin")
    void pomTatIncludeTools() {
        String pom = doc(timTuGocKho(POM_APP));
        String khoi = khoiPlugin(pom, "spring-boot-maven-plugin");

        assertThat(khoi)
                .as("Không tìm thấy khối <plugin> của spring-boot-maven-plugin trong %s", POM_APP)
                .isNotEmpty();
        assertThat(khoi)
                .as(
                        """
                        `phu-quet-cve.sh` cố ý KHÔNG có danh sách ngoại lệ, nên `spring-boot-jarmode-tools` \
                        phải bị giữ ngoài fat jar bằng chính pom. Bật lại `includeTools` là lượt quét \
                        thật đỏ ở bước đo phạm vi — bài này làm nó đỏ ở máy trước.""")
                .contains("<includeTools>false</includeTools>");

        // Và script thật sự không có ngoại lệ nào cho jar ấy — ngoài dòng chú thích giải thích.
        String script = doc(timTuGocKho(SCRIPT));
        List<String> dongMa =
                script.lines().filter(d -> !d.strip().startsWith("#")).toList();
        assertThat(dongMa)
                .as("Một ngoại lệ ghi cứng trong script là chỗ mọi jar thiếu tương lai sẽ được tha")
                .noneMatch(d -> d.contains("jarmode"));
    }

    @Test
    @DisplayName("⭐ P10 · TỰ KIỂM: `includeTools` ở plugin KHÁC hay trong chú thích KHÔNG được tính")
    void tuKiemBoCatKhoiPom() {
        String pluginKhac =
                """
                <plugins>
                  <plugin>
                    <artifactId>maven-jar-plugin</artifactId>
                    <configuration><includeTools>false</includeTools></configuration>
                  </plugin>
                  <plugin>
                    <artifactId>spring-boot-maven-plugin</artifactId>
                    <configuration><mainClass>x</mainClass></configuration>
                  </plugin>
                </plugins>
                """;
        assertThat(khoiPlugin(pluginKhac, "spring-boot-maven-plugin"))
                .as("Bộ cắt khối lấy nhầm plugin khác ⇒ bài P9 xanh trong khi jarmode-tools vẫn vào jar")
                .isNotEmpty()
                .doesNotContain("includeTools");

        String chiTrongChuThich =
                """
                <plugin>
                  <artifactId>spring-boot-maven-plugin</artifactId>
                  <configuration>
                    <!-- nhớ bật <includeTools>false</includeTools> -->
                    <mainClass>x</mainClass>
                  </configuration>
                </plugin>
                """;
        assertThat(khoiPlugin(chiTrongChuThich, "spring-boot-maven-plugin"))
                .as("Một dòng CHÚ THÍCH nhắc tới thẻ không phải là thẻ (CLAUDE.md luật 2)")
                .isNotEmpty()
                .doesNotContain("includeTools");

        // Đối chứng: khối thật có thẻ thì phải đọc ra thẻ (luật 9).
        String coThat = chiTrongChuThich.replace(
                "<!-- nhớ bật <includeTools>false</includeTools> -->", "<includeTools>false</includeTools>");
        assertThat(khoiPlugin(coThat, "spring-boot-maven-plugin")).contains("<includeTools>false</includeTools>");
    }

    @Test
    @DisplayName("⭐ P11 · TỰ KIỂM: `always()` chỉ nằm trong CHÚ THÍCH của khối bước thì KHÔNG được tính")
    void tuKiemBoDocBuoc() {
        String thanGia = "    steps:\n"
                + "      - name: Phạm vi quét\n"
                + "        if: steps.nvd.outputs.co_khoa == 'true'\n"
                + "        run: .github/scripts/phu-quet-cve.sh a b c\n"
                + "\n"
                + "      # `always()` vì bước quét ở trên ĐỎ là trạng thái thường trực\n"
                + "      - name: Bước kế\n"
                + "        if: always()\n"
                + "        run: echo\n";

        String buoc = buocChua(thanGia, "phu-quet-cve.sh");
        assertThat(buoc)
                .as("Bộ dò còn đọc chú thích ⇒ bỏ `always()` khỏi bước thật mà P8 vẫn xanh")
                .doesNotContain("always()");
        assertThat(dieuKienIf(buoc)).isEqualTo("steps.nvd.outputs.co_khoa == 'true'");
        assertThat(dieuKienIf(buocChua(thanGia, "Bước kế"))).contains("always()");
    }

    // ── Hạ tầng ────────────────────────────────────────────────────────────────────────────────

    private record KetQua(int maThoat, String dauRa) {}

    private static KetQua chay(Path thuMuc, String path, Path jar, Path baoCao, String... themArgs) throws Exception {
        Path script = timTuGocKho(SCRIPT);
        List<String> lenh = new ArrayList<>(List.of(
                "/bin/bash",
                script.toString(),
                jar.toString(),
                baoCao.toString(),
                tepRaPath(thuMuc).toString()));
        lenh.addAll(List.of(themArgs));

        // `/bin/bash` TUYỆT ĐỐI: bài P7 dựng một PATH rỗng để giấu `jq`, và shell mặc định của máy dev
        // là zsh (CLAUDE.md luật 20).
        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
        }
        return new KetQua(p.exitValue(), dauRa);
    }

    private static Path tepRaPath(Path thuMuc) {
        return thuMuc.resolve("phu-quet-cve.txt");
    }

    private static String tepRa(Path thuMuc) {
        return doc(tepRaPath(thuMuc));
    }

    private static String dongDau(Path thuMuc) {
        return tepRa(thuMuc).lines().findFirst().orElse("");
    }

    private static Path ghi(Path thuMuc, String ten, String noiDung) throws IOException {
        return Files.writeString(thuMuc.resolve(ten), noiDung, StandardCharsets.UTF_8);
    }

    /** Fat jar giả đúng hình dạng Spring Boot: MANIFEST + classes + {@code BOOT-INF/lib/*.jar}. */
    private static Path dungJar(Path thuMuc, String... lib) throws IOException {
        return dungJarTai(thuMuc.resolve("app.jar"), lib);
    }

    private static Path dungJarTai(Path jar, String... lib) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            them(zip, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
            them(zip, "BOOT-INF/classes/com/songnhue/app/X.class", "khong-phai-bytecode-that");
            for (String tep : lib) {
                them(zip, "BOOT-INF/lib/" + tep, "jar-gia");
            }
        }
        return jar;
    }

    private static void them(ZipOutputStream zip, String ten, String noiDung) throws IOException {
        zip.putNextEntry(new ZipEntry(ten));
        zip.write(noiDung.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    /** Một mục {@code dependencies[]} của báo cáo ODC, kèm các {@code relatedDependencies} (nếu có). */
    private static String muc(String fileName, String... related) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"fileName\":\"")
                .append(fileName)
                .append("\",\"filePath\":\"/x/")
                .append(fileName)
                .append('"');
        if (related.length > 0) {
            sb.append(",\"relatedDependencies\":[");
            for (int i = 0; i < related.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("{\"fileName\":\"")
                        .append(related[i])
                        .append("\",\"filePath\":\"/x/")
                        .append(related[i])
                        .append("\"}");
            }
            sb.append(']');
        }
        return sb.append('}').toString();
    }

    private static String baoCao(String... muc) {
        return "{\"dependencies\":[" + String.join(",", muc) + "]}";
    }

    /** Khối {@code <plugin>…</plugin>} có {@code artifactId} cho trước, đã bỏ mọi chú thích XML. */
    private static String khoiPlugin(String pom, String artifactId) {
        String khongChuThich = pom.replaceAll("(?s)<!--.*?-->", "");
        Matcher m = Pattern.compile("(?s)<plugin>.*?</plugin>").matcher(khongChuThich);
        while (m.find()) {
            if (m.group().contains("<artifactId>" + artifactId + "</artifactId>")) {
                return m.group();
            }
        }
        return "";
    }

    /**
     * Khối một bước (bắt đầu bằng {@code - } ở thụt 6) trong thân job có chứa chuỗi cho trước — đã <b>bỏ
     * mọi dòng chú thích</b>: chú thích của bước KẾ TIẾP nằm trong cùng khối và có thể nhắc đúng chuỗi
     * đang canh (CLAUDE.md luật 2; đo được 6/9 ở {@code VanTayCveTest}).
     */
    private static String buocChua(String thanJob, String chuoi) {
        for (String buoc : thanJob.split("(?m)^      - ")) {
            String khongChuThich = buoc.lines()
                    .filter(d -> !d.strip().startsWith("#"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            if (khongChuThich.contains(chuoi)) {
                return khongChuThich;
            }
        }
        return fail("Không có bước nào trong job chứa `%s`".formatted(chuoi));
    }

    /** Giá trị của dòng {@code if:} (thụt 8) trong một khối bước; rỗng nếu bước không có {@code if:}. */
    private static String dieuKienIf(String buoc) {
        Matcher m = Pattern.compile("(?m)^\\s{8}if:\\s*(.+?)\\s*$").matcher(buoc);
        return m.find() ? m.group(1) : "";
    }

    /** Thân của một job: từ dòng khoá job tới dòng khoá job cấp một kế tiếp. */
    private static String thanJob(String tenJob) {
        String noiDung = doc(timTuGocKho(WORKFLOW));
        Matcher m = Pattern.compile("(?m)^  " + Pattern.quote(tenJob) + ":$").matcher(noiDung);
        if (!m.find()) {
            return fail("Không tìm thấy job `%s` trong security-scan.yml".formatted(tenJob));
        }
        int batDau = m.end();
        Matcher ke = KHOA_JOB.matcher(noiDung);
        int ketThuc = noiDung.length();
        if (ke.find(batDau)) {
            ketThuc = ke.start();
        }
        return noiDung.substring(batDau, ketThuc);
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
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
