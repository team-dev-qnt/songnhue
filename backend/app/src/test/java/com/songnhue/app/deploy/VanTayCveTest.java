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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Vân tay CVE — chạy THẬT {@code van-tay-cve.sh} trên báo cáo Dependency-Check giả.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Chuông báo CVE từ 3/9 tới 5/9 để lại 9 bình luận giống hệt nhau (732 byte, cùng vân tay sau khi bỏ
 * URL) trên issue #84 trong khi tập CVE đi 12 → 13 → 12 → 15 → 11 mã — nó không đọc báo cáo một dòng
 * nào. Script này là nửa "đọc": rút báo cáo JSON thành văn bản thuần, <b>quyết định</b> (cùng đầu vào ⇒
 * cùng byte), để chuông chỉ cần so văn bản.
 *
 * <h2>⚠⚠ Bẫy bắt được ngay lượt viết đầu — và bài V9 giữ nó khỏi tái phát</h2>
 *
 * Bản nháp đầu của bộ lọc đọc {@code cvssvX.cvssData.baseScore} — hình dạng JSON của <i>API NVD</i>.
 * Báo cáo ODC 12.1.3 <b>thật</b> đặt điểm ở {@code cvssv2.score} và {@code cvssv3.baseScore}; bộ lọc
 * ấy cho {@code ge7=0} trên một báo cáo có <b>6 mã ≥ 7</b> — xanh giả hoàn hảo. Bài V9 chép hình dạng
 * của báo cáo thật vào fixture (CLAUDE.md luật 25): một bộ lọc viết theo API NVD phải đỏ ở đó.
 *
 * <p>Và điểm dùng để phân loại là <b>max</b> của v2/v3/v4 (conventions.md §4.5 mục 4): bài V3 dựng đúng
 * ca v4 = 6.9 / v3 = 7.5 / {@code severity: MEDIUM} và đòi nó nằm ở hàng {@code >=7}.
 *
 * <h2>⛔ Giới hạn (luật 28)</h2>
 *
 * Bài này chứng minh script <b>đọc đúng hình dạng đã đo ngày 6/9</b>. Một phiên bản Dependency-Check
 * đổi hình dạng JSON sẽ không được bắt ở đây — nó sẽ lộ ở chuông (vân tay {@code ge7=0} trong khi job
 * đỏ), và lộ ở {@code PhuQuetCveTest} nếu tên trường jar đổi.
 */
class VanTayCveTest {

    private static final String SCRIPT = ".github/scripts/van-tay-cve.sh";
    private static final String WORKFLOW = ".github/workflows/security-scan.yml";

    /** Khoá job cấp một trong {@code security-scan.yml}: đúng hai dấu cách thụt đầu. */
    private static final Pattern KHOA_JOB = Pattern.compile("(?m)^  ([a-z][a-z0-9-]*):$");

    // ── Nhóm 1: hành vi thật của script ────────────────────────────────────────────────────────

    @Test
    @DisplayName("V1 · bảy mã ≥ 7 và tám mã ≥ 7 → hai vân tay KHÁC nhau, `ge7=` đúng từng cái")
    void bayVaTamKhacNhau(@TempDir Path thuMuc) throws Exception {
        KetQua bay = chay(thuMuc, "bay", baoCao(dep("a.jar", nhieuMa(7, 9.8)), dep("b.jar", vuln("CVE-2000-1", 3.1))));
        KetQua tam = chay(thuMuc, "tam", baoCao(dep("a.jar", nhieuMa(8, 9.8)), dep("b.jar", vuln("CVE-2000-1", 3.1))));

        assertThat(bay.maThoat()).as(bay.dauRa()).isZero();
        assertThat(tam.maThoat()).as(tam.dauRa()).isZero();
        assertThat(bay.ra()).startsWith("ge7=7\ntong=8\n");
        assertThat(tam.ra()).startsWith("ge7=8\ntong=9\n");
        assertThat(bay.ra())
                .as("Hai báo cáo khác tập CVE mà cùng vân tay thì chuông lại thành một bit")
                .isNotEqualTo(tam.ra());
    }

    @Test
    @DisplayName("⭐ V2 · cùng đầu vào — kể cả đảo thứ tự `dependencies[]` — ra cùng byte")
    void quyetDinh(@TempDir Path thuMuc) throws Exception {
        String a = dep("a.jar", vuln("CVE-2000-2", 7.5), vuln("CVE-2000-1", 2.0));
        String b = dep("b.jar", vuln("CVE-2000-3", 9.8));

        KetQua lan1 = chay(thuMuc, "l1", baoCao(a, b));
        KetQua lan2 = chay(thuMuc, "l2", baoCao(a, b));
        KetQua dao = chay(thuMuc, "l3", baoCao(b, a));

        assertThat(lan1.ra()).isEqualTo(lan2.ra());
        assertThat(dao.ra())
                .as(
                        "Thứ tự trong JSON không phải thông tin — vân tay phải sắp trước khi so, nếu không mỗi lượt là một 'đổi'")
                .isEqualTo(lan1.ra());
        // jq giữ nguyên chữ số như trong JSON (`2.0` không thành `2`) — cùng đầu vào thì cùng chữ, đủ cho vân tay.
        assertThat(lan1.ra()).contains("CVE-2000-1\t2.0\t<7\ta.jar\n").contains("CVE-2000-2\t7.5\t>=7\ta.jar\n");
    }

    @Test
    @DisplayName("⭐⭐ V3 · bẫy §4.5 mục 4: v4 = 6.9, v3 = 7.5, severity MEDIUM → PHẢI là `>=7`")
    void maxQuaMoiThang(@TempDir Path thuMuc) throws Exception {
        String vuln = "{\"name\":\"CVE-2026-34479\",\"severity\":\"MEDIUM\","
                + "\"cvssv3\":{\"baseScore\":7.5,\"baseSeverity\":\"HIGH\"},"
                + "\"cvssv4\":{\"baseScore\":6.9,\"baseSeverity\":\"MEDIUM\"}}";
        KetQua kq = chay(thuMuc, "v3v4", baoCao(dep("log4j-api-2.24.3.jar", vuln)));

        assertThat(kq.maThoat()).as(kq.dauRa()).isZero();
        assertThat(kq.ra())
                .as(
                        """
                        Dependency-Check in điểm v4 (6.9) nhưng chặn theo điểm cao nhất mọi thang (v3 = 7.5). \
                        Lấy điểm cuối cùng gặp được, hay lấy `severity` cấp trên cùng, đều đếm THIẾU — đúng \
                        lỗi ngày 18/8 đọc "66 → 9 → 3" thành "56 → 6 → 0".""")
                .startsWith("ge7=1\ntong=1\n")
                .contains("CVE-2026-34479\t7.5\t>=7\tlog4j-api-2.24.3.jar");
    }

    @Test
    @DisplayName("V4 · cùng một CVE trên hai jar → MỘT dòng, hai jar đã sắp, `tong=1`")
    void gomTheoMa(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(
                thuMuc,
                "gom",
                baoCao(
                        dep("spring-web-6.2.19.jar", vuln("CVE-2026-47890", 9.8)),
                        dep("spring-core-6.2.19.jar", vuln("CVE-2026-47890", 9.8))));

        assertThat(kq.maThoat()).as(kq.dauRa()).isZero();
        assertThat(kq.ra())
                .isEqualTo(
                        "ge7=1\ntong=1\nsuppress=0\nCVE-2026-47890\t9.8\t>=7\tspring-core-6.2.19.jar,spring-web-6.2.19.jar\n");
    }

    @Test
    @DisplayName("V5 · không lỗ hổng nào → `ge7=0 tong=0`, exit 0, tệp ra tồn tại (xanh có bằng chứng)")
    void khongLoHong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "sach", baoCao(dep("a.jar"), dep("b.jar")));

        assertThat(kq.maThoat()).as(kq.dauRa()).isZero();
        assertThat(kq.ra())
                .as("Xanh phải để lại bằng chứng: tệp vân tay với ge7=0, không phải sự vắng mặt của tệp")
                .isEqualTo("ge7=0\ntong=0\nsuppress=0\n");
    }

    @Test
    @DisplayName("⛔ V6 · báo cáo vắng · báo cáo không phải JSON → ĐỎ và KHÔNG tạo tệp ra")
    void baoCaoHongThiDo(@TempDir Path thuMuc) throws Exception {
        Path ra1 = thuMuc.resolve("ra-vang.txt");
        KetQua vang = chayTho(thuMuc, System.getenv("PATH"), thuMuc.resolve("khong-co.json"), ra1, Map.of());
        assertThat(vang.maThoat()).as("Báo cáo vắng:\n%s", vang.dauRa()).isNotZero();
        assertThat(ra1)
                .as("Tệp ra mà tồn tại thì chuông đọc thành 'có bằng chứng' — sai trạng thái")
                .doesNotExist();

        Path rac = Files.writeString(thuMuc.resolve("rac.json"), "không phải json", StandardCharsets.UTF_8);
        Path ra2 = thuMuc.resolve("ra-rac.txt");
        KetQua hong = chayTho(thuMuc, System.getenv("PATH"), rac, ra2, Map.of());
        assertThat(hong.maThoat()).as("Báo cáo rác:\n%s", hong.dauRa()).isNotZero();
        assertThat(ra2).doesNotExist();
    }

    @Test
    @DisplayName("⛔ V7 · không có `jq` trên PATH → ĐỎ và nói tên công cụ")
    void thieuJqThiDo(@TempDir Path thuMuc) throws Exception {
        Path baoCao = Files.writeString(thuMuc.resolve("bc.json"), baoCao(dep("a.jar")), StandardCharsets.UTF_8);
        Path binRong = Files.createDirectories(thuMuc.resolve("bin-rong"));

        KetQua kq = chayTho(thuMuc, binRong.toString(), baoCao, thuMuc.resolve("ra.txt"), Map.of());

        assertThat(kq.maThoat())
                .as("Thiếu công cụ mà thoát 0 là đúng bẫy `verify-no-keys.sh`")
                .isNotZero();
        assertThat(kq.dauRa()).contains("jq");
    }

    @Test
    @DisplayName("V8 · có `GITHUB_STEP_SUMMARY` → bảng Markdown, mỗi mã một hàng; không có → vẫn chạy")
    void tomTatBuoc(@TempDir Path thuMuc) throws Exception {
        Path baoCao = Files.writeString(
                thuMuc.resolve("bc.json"),
                baoCao(dep("a.jar", vuln("CVE-2000-1", 9.8), vuln("CVE-2000-2", 4.0))),
                StandardCharsets.UTF_8);
        Path tomTat = thuMuc.resolve("summary.md");

        KetQua kq = chayTho(
                thuMuc,
                System.getenv("PATH"),
                baoCao,
                thuMuc.resolve("ra.txt"),
                Map.of("GITHUB_STEP_SUMMARY", tomTat.toString()));

        assertThat(kq.maThoat()).as(kq.dauRa()).isZero();
        String md = Files.readString(tomTat, StandardCharsets.UTF_8);
        assertThat(md).contains("| CVE-2000-1 | 9.8 | **≥ 7** | a.jar |").contains("| CVE-2000-2 | 4.0 | <7 | a.jar |");
        assertThat(md.lines().filter(d -> d.startsWith("| CVE-")).count())
                .as("Lượt đỏ vì CVE từng ghi 0 byte vào step summary (T11.66) — nay mỗi mã một hàng")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⭐⭐ V9 · NEO DỮ LIỆU THẬT: hình dạng báo cáo ODC 12.1.3 (`cvssv2.score`, `cvssv3.baseScore`)")
    void hinhDangBaoCaoThat(@TempDir Path thuMuc) throws Exception {
        // Chép từ báo cáo lượt 33951186299 (5/9): KHÔNG có `cvssData`, v2 dùng `score`, v3 dùng
        // `baseScore`, và jar con nằm ở `relatedDependencies`. Một bộ lọc viết theo API NVD
        // (`cvssvX.cvssData.baseScore`) cho ge7=0 ở đây — đúng bản nháp đầu của script.
        String baoCaoThat =
                """
                {"reportSchema":"1.1","scanInfo":{"engineVersion":"12.1.3"},
                 "dependencies":[
                  {"isVirtual":false,"fileName":"spring-core-6.2.19.jar","filePath":"/x/spring-core-6.2.19.jar",
                   "relatedDependencies":[
                     {"isVirtual":false,"fileName":"spring-context-6.2.19.jar","filePath":"/x/spring-context-6.2.19.jar"},
                     {"isVirtual":false,"fileName":"spring-beans-6.2.19.jar","filePath":"/x/spring-beans-6.2.19.jar"}],
                   "vulnerabilities":[
                     {"source":"NVD","name":"CVE-2026-47890","severity":"CRITICAL",
                      "cvssv2":{"score":10.0,"accessVector":"NETWORK","accessComplexity":"LOW","authenticationr":"NONE",
                                "confidentialityImpact":"COMPLETE","integrityImpact":"COMPLETE","availabilityImpact":"COMPLETE",
                                "severity":"HIGH","version":"2.0","exploitabilityScore":"10.0","impactScore":"10.0"},
                      "cvssv3":{"baseScore":9.8,"attackVector":"NETWORK","attackComplexity":"LOW","privilegesRequired":"NONE",
                                "userInteraction":"NONE","scope":"UNCHANGED","confidentialityImpact":"HIGH",
                                "integrityImpact":"HIGH","availabilityImpact":"HIGH","baseSeverity":"CRITICAL",
                                "exploitabilityScore":"3.9","impactScore":"5.9","version":"3.1"},
                      "cwes":["CWE-93"],"description":"Spring MVC and WebFlux applications are vulnerable…"},
                     {"source":"NVD","name":"CVE-2026-59314","severity":"LOW",
                      "cvssv3":{"baseScore":3.7,"baseSeverity":"LOW","version":"3.1"}}]},
                  {"isVirtual":false,"fileName":"kotlin-stdlib-1.9.25.jar","filePath":"/x/kotlin-stdlib-1.9.25.jar",
                   "vulnerabilities":[{"source":"NVD","name":"CVE-2020-29582","severity":"MEDIUM",
                                       "cvssv2":{"score":2.1,"severity":"LOW","version":"2.0"},
                                       "cvssv3":{"baseScore":5.3,"baseSeverity":"MEDIUM","version":"3.1"}}],
                   "suppressedVulnerabilities":[{"source":"NVD","name":"CVE-2026-53914","severity":"CRITICAL",
                                                 "cvssv3":{"baseScore":9.8}}]}
                 ]}
                """;
        KetQua kq = chay(thuMuc, "that", baoCaoThat);

        assertThat(kq.maThoat()).as(kq.dauRa()).isZero();
        assertThat(kq.ra())
                .as(
                        """
                        Trên hình dạng báo cáo THẬT, mã 9.8 phải nằm ở hàng `>=7`. Bộ lọc đọc \
                        `cvssData.baseScore` (hình dạng API NVD) cho ge7=0 ở đây — xanh giả hoàn hảo, \
                        và đúng là bản nháp đầu của script (§10.76). CLAUDE.md luật 25: bộ canh theo hình \
                        dạng phải thử với dữ liệu THẬT đang dùng.""")
                .isEqualTo(
                        """
                        ge7=1
                        tong=3
                        suppress=1
                        CVE-2020-29582\t5.3\t<7\tkotlin-stdlib-1.9.25.jar
                        CVE-2026-47890\t10.0\t>=7\tspring-core-6.2.19.jar
                        CVE-2026-59314\t3.7\t<7\tspring-core-6.2.19.jar
                        """);
    }

    @Test
    @DisplayName("V10 · mã bị suppression che → đếm vào `suppress=`, KHÔNG lọt vào danh sách mã")
    void suppressionDuocDem(@TempDir Path thuMuc) throws Exception {
        String depCoSuppress = "{\"fileName\":\"a.jar\",\"filePath\":\"/x/a.jar\",\"vulnerabilities\":["
                + vuln("CVE-2000-1", 9.8)
                + "],\"suppressedVulnerabilities\":["
                + vuln("CVE-2000-8", 9.8)
                + ","
                + vuln("CVE-2000-9", 7.0)
                + "]}";
        KetQua kq = chay(thuMuc, "sup", baoCao(depCoSuppress));

        assertThat(kq.maThoat()).as(kq.dauRa()).isZero();
        assertThat(kq.ra())
                .as("§10.69: một suppression làm mã 9.8 biến mất khỏi báo cáo — số ấy phải nhìn thấy được")
                .startsWith("ge7=1\ntong=1\nsuppress=2\n")
                .doesNotContain("CVE-2000-8")
                .doesNotContain("CVE-2000-9");
    }

    // ── Nhóm 2: dây nối trong workflow ─────────────────────────────────────────────────────────

    @Test
    @DisplayName(
            "⭐ V11 · workflow gọi script với `always()` trong job quét, giữ tệp ra trong artifact, `paths:` bao script")
    void dayNoiWorkflow() {
        String than = thanJob("owasp");
        String buoc = buocChua(than, "van-tay-cve.sh");
        assertThat(dieuKienIf(buoc))
                .as(
                        """
                        Bước vân tay phải `if: always()`: bước quét ngay trước nó ĐỎ là trạng thái thường \
                        trực của job, không có `always()` thì lượt đỏ vì CVE — lượt cần vân tay nhất — \
                        không có vân tay, và chuông đọc thành "không có báo cáo". Dòng if đọc được: "%s\"""",
                        dieuKienIf(buoc))
                .contains("always()");
        assertThat(buoc).contains("backend/target/dependency-check-report.json");

        String upload = buocChua(than, "upload-artifact");
        assertThat(upload)
                .as(
                        "Tệp vân tay không vào artifact thì job chuông không bao giờ thấy nó — nửa còn lại của cặp đọc–ghi (quy tắc 27)")
                .contains("backend/target/van-tay-cve.txt");

        String workflow = doc(timTuGocKho(WORKFLOW));
        assertThat(workflow)
                .as("Sửa script vân tay phải làm cổng chạy lại (luật 24, §10.69)")
                .contains("'" + SCRIPT + "'");
    }

    @Test
    @DisplayName("⭐ V12 · TỰ KIỂM: `always()` chỉ nằm trong CHÚ THÍCH của khối bước thì KHÔNG được tính")
    void tuKiemBoDocBuoc() {
        // Đúng hình dạng đã làm V11 xanh giả ở lượt phá-để-kiểm đầu: chú thích của bước KẾ TIẾP
        // nằm trong khối của bước trước và nhắc `always()`.
        String thanGia = "    steps:\n"
                + "      - name: Vân tay CVE từ báo cáo\n"
                + "        if: steps.nvd.outputs.co_khoa == 'true'\n"
                + "        run: .github/scripts/van-tay-cve.sh a b\n"
                + "\n"
                + "      # `always()` vì bước quét ở trên ĐỎ là trạng thái thường trực\n"
                + "      - name: Bước kế\n"
                + "        if: always()\n"
                + "        run: echo\n";

        String buoc = buocChua(thanGia, "van-tay-cve.sh");
        assertThat(buoc)
                .as("Bộ dò còn đọc chú thích ⇒ bỏ `always()` khỏi bước thật mà V11 vẫn xanh (đo được 6/9)")
                .doesNotContain("always()");
        assertThat(dieuKienIf(buoc)).isEqualTo("steps.nvd.outputs.co_khoa == 'true'");
        // Đối chứng (luật 9): bước có `always()` THẬT thì phải đọc ra.
        assertThat(dieuKienIf(buocChua(thanGia, "Bước kế"))).contains("always()");
    }

    // ── Hạ tầng ────────────────────────────────────────────────────────────────────────────────

    private record KetQua(int maThoat, String dauRa, String ra) {}

    /** Chạy script với PATH thật trên một báo cáo giả; trả cả nội dung tệp ra (rỗng nếu không tạo). */
    private static KetQua chay(Path thuMuc, String ten, String jsonBaoCao) throws Exception {
        Path baoCao = Files.writeString(thuMuc.resolve(ten + ".json"), jsonBaoCao, StandardCharsets.UTF_8);
        return chayTho(thuMuc, System.getenv("PATH"), baoCao, thuMuc.resolve(ten + ".txt"), Map.of());
    }

    private static KetQua chayTho(Path thuMuc, String path, Path baoCao, Path ra, Map<String, String> env)
            throws Exception {
        Path script = timTuGocKho(SCRIPT);
        List<String> lenh = new ArrayList<>(List.of("/bin/bash", script.toString(), baoCao.toString(), ra.toString()));

        // `/bin/bash` TUYỆT ĐỐI: bài V7 dựng một PATH rỗng để giấu `jq`, và shell mặc định của máy dev
        // là zsh (CLAUDE.md luật 20).
        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);
        pb.environment().putAll(env);

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
        }
        String noiDungRa = Files.exists(ra) ? Files.readString(ra, StandardCharsets.UTF_8) : "";
        return new KetQua(p.exitValue(), dauRa, noiDungRa);
    }

    /** Một mục {@code dependencies[]} của báo cáo ODC với các lỗ hổng cho trước. */
    private static String dep(String fileName, String... vulns) {
        return "{\"fileName\":\"" + fileName + "\",\"filePath\":\"/x/" + fileName + "\",\"vulnerabilities\":["
                + String.join(",", vulns) + "]}";
    }

    /** Một lỗ hổng theo hình dạng ODC 12.1.3: {@code cvssv3.baseScore} (không có {@code cvssData}). */
    private static String vuln(String name, double v3) {
        return "{\"name\":\"" + name + "\",\"severity\":\"X\",\"cvssv3\":{\"baseScore\":" + v3
                + ",\"version\":\"3.1\"}}";
    }

    private static String[] nhieuMa(int soLuong, double v3) {
        String[] ket = new String[soLuong];
        for (int i = 0; i < soLuong; i++) {
            ket[i] = vuln("CVE-2099-" + (100 + i), v3);
        }
        return ket;
    }

    private static String baoCao(String... deps) {
        return "{\"dependencies\":[" + String.join(",", deps) + "]}";
    }

    /**
     * Khối một bước (bắt đầu bằng {@code - } ở thụt 6) trong thân job có chứa chuỗi cho trước — đã <b>bỏ
     * mọi dòng chú thích</b>.
     *
     * <p>Lượt phá-để-kiểm đầu (6/9) bỏ {@code always()} khỏi bước vân tay mà bài dây nối VẪN XANH: khối
     * cắt tới {@code - name:} kế tiếp nên ôm cả chú thích của bước SAU, và chú thích ấy nhắc
     * {@code always()}. CLAUDE.md luật 2 — canh cấu trúc, đừng canh văn bản — lần thứ N.
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
