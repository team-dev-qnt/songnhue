package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Canh hạn chứng chỉ TLS từ BÊN NGOÀI — T11.88 · DOD4.8.</b>
 *
 * <p>Lịch gia hạn từng hỏng theo hai cách, cả hai IM LẶNG trên máy chủ: VPS-2 ⛔ cài gói {@code cron}, và dòng
 * cron dạng compose thoát 1 ở mọi lượt (§10.81). Chuông này đo chứng chỉ mà máy khách THẬT nhận, từ runner.
 *
 * <p>Script được GỌI với {@code openssl} và {@code gh} giả; thứ được khẳng định là <b>lệnh nào thật sự chạy</b>
 * (khuôn {@code CanhCongQuetTest}) — ⛔ chữ trong YAML.
 *
 * <p>⛔ Giới hạn (luật 28): bài chứng minh các nhánh quyết định đúng và dây đã nối. Nó ⛔ chứng minh GitHub chạy
 * lịch, cũng ⛔ chứng minh danh sách {@code TEN_MIEN} đủ — danh sách ấy đo tay 19/09 (7 tên, ghi trong workflow).
 */
class CanhChungChiTest {

    private static final String SCRIPT = ".github/scripts/canh-chung-chi.sh";
    private static final String WORKFLOW = ".github/workflows/canh-chung-chi.yml";

    /** 19/09/2026 00:00 UTC — giờ ghim; mọi hạn chứng chỉ trong bài tính TƯƠNG ĐỐI với nó. */
    private static final Instant BAY_GIO = Instant.parse("2026-09-19T00:00:00Z");

    /** Đúng định dạng `openssl x509 -enddate`: ngày một chữ số được ĐỆM khoảng trắng (`Dec  6`). */
    private static final DateTimeFormatter DINH_DANG_OPENSSL = DateTimeFormatter.ofPattern(
                    "MMM ppd HH:mm:ss yyyy 'GMT'", Locale.ENGLISH)
            .withZone(ZoneOffset.UTC);

    private static final String HONG = "HONG";

    private static String sau(long ngay) {
        return DINH_DANG_OPENSSL.format(BAY_GIO.plus(Duration.ofDays(ngay)).plusSeconds(3600));
    }

    @Test
    @DisplayName("Tự kiểm: định dạng giả ĐÚNG hình dạng openssl, gồm cả ngày một chữ số có đệm")
    void dinhDangGiaDungHinhDang() {
        assertThat(DINH_DANG_OPENSSL.format(Instant.parse("2026-12-06T16:35:35Z")))
                .as("đo 19/09 trên thuyloisongnhue.vn: `Dec  6 16:35:35 2026 GMT`")
                .isEqualTo("Dec  6 16:35:35 2026 GMT");
    }

    @Test
    @DisplayName("A · mọi tên miền còn ≥ ngưỡng ⇒ im lặng, thoát 0, ⛔ issue nào")
    void conHanThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, Map.of("a.vn", sau(60), "b.vn", sau(40)), null, null);

        assertThat(kq.dauRa()).contains("TRANG_THAI=binh-thuong");
        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue create") || l.startsWith("issue comment"));
    }

    @Test
    @DisplayName("⭐ B · một tên miền còn 13 ngày ⇒ tạo NHÃN rồi mở issue, và ĐỎ")
    void sapHetHanThiKeu(@TempDir Path thuMuc) throws Exception {
        // 13 ngày ⇒ `Oct  2 …` — ngày MỘT chữ số có đệm: phép đọc ngày của BSD (máy dev) lẫn GNU (runner)
        // đều phải qua, ⛔ rơi vào `khong-do-duoc` vì lý do định dạng.
        assertThat(sau(13)).startsWith("Oct  2 ");
        KetQua kq = chay(thuMuc, Map.of("a.vn", sau(60), "b.vn", sau(13)), null, null);

        assertThat(kq.dauRa()).contains("b.vn => sap-het-han (còn 13 ngày");
        assertThat(kq.maThoat())
                .as("lượt chạy phải ĐỎ trên tab Actions (§10.42)")
                .isNotZero();
        int nhan = viTri(kq, "label create canh-chung-chi");
        int tao = viTri(kq, "issue create");
        assertThat(nhan)
                .as(
                        "⛔⛔ nhãn phải được TẠO trước — `issue create --label <nhãn chưa có>` HỎNG (nhãn canh-cong-quet ⛔ tồn tại, đo 19/09)")
                .isNotNegative()
                .isLessThan(tao);
        assertThat(kq.lenhGh().get(tao))
                .contains("b.vn")
                .contains("sap-het-han")
                .contains("<!-- van-tay:b.vn:sap-het-han:14; -->");
    }

    @Test
    @DisplayName("⭐ C · bắt tay HỎNG ⇒ `khong-do-duoc` và KÊU — ⛔ đọc là còn hạn (luật 9)")
    void khongDoDuocThiKeu(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, Map.of("a.vn", sau(60), "chet.vn", HONG), null, null);

        assertThat(kq.dauRa()).contains("chet.vn => khong-do-duoc");
        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue create") && l.contains("khong-do-duoc"));
    }

    @Test
    @DisplayName("D · đã quá notAfter ⇒ `het-han`")
    void hetHan(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, Map.of("a.vn", sau(-2)), null, null);

        assertThat(kq.dauRa()).contains("a.vn => het-han");
        assertThat(kq.maThoat()).isNotZero();
    }

    @Test
    @DisplayName("E · hết sự cố mà còn issue mở ⇒ ĐÓNG issue, thoát 0")
    void hetSuCoThiDong(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, Map.of("a.vn", sau(80)), "42", null);

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue close 42"));
    }

    @Test
    @DisplayName("⭐⭐ F · vân tay ⛔ đổi so với lần kêu trước ⇒ ⛔ bình luận thêm (T11.84), nhưng vẫn ĐỎ")
    void vanTayKhongDoiThiKhongBinhLuan(@TempDir Path thuMuc) throws Exception {
        String issueCu = "{\"body\":\"cũ <!-- van-tay:b.vn:sap-het-han:14; -->\",\"comments\":[]}";
        KetQua kq = chay(thuMuc, Map.of("b.vn", sau(9)), "42", issueCu);

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue comment") || l.startsWith("issue create"));
    }

    @Test
    @DisplayName("⭐ G · bước sang bậc gấp hơn (14 → 7 ngày) ⇒ vân tay ĐỔI ⇒ bình luận lại")
    void sangBacGapHonThiKeuLai(@TempDir Path thuMuc) throws Exception {
        String issueCu = "{\"body\":\"x\",\"comments\":[{\"body\":\"<!-- van-tay:b.vn:sap-het-han:14; -->\"}]}";
        KetQua kq = chay(thuMuc, Map.of("b.vn", sau(5)), "42", issueCu);

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh())
                .anyMatch(l -> l.startsWith("issue comment 42") && l.contains("<!-- van-tay:b.vn:sap-het-han:7; -->"));
    }

    @Test
    @DisplayName("⛔ H · TEN_MIEN rỗng ⇒ ĐỎ (exit 3), ⛔ 'mọi chứng chỉ còn hạn' trên tập rỗng (luật 7)")
    void danhSachRongThiDo(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, Map.of(), null, null);

        assertThat(kq.maThoat()).isEqualTo(3);
        assertThat(kq.lenhGh()).isEmpty();
    }

    @Test
    @DisplayName("⛔ I · thiếu `openssl` trên PATH ⇒ ĐỎ (exit 3), ⛔ im lặng đi tiếp")
    void thieuOpensslThiDo(@TempDir Path thuMuc) throws Exception {
        Path bin = dungCongCuGia(thuMuc, Map.of("a.vn", sau(60)), null, null);
        Files.delete(bin.resolve("openssl"));
        // PATH chỉ có thư mục giả: ⛔ `/usr/bin` (runner và macOS đều có `openssl` thật ở đó).
        KetQua kq = chayVoiPath(thuMuc, bin.toString(), "a.vn");

        assertThat(kq.maThoat()).isEqualTo(3);
        assertThat(kq.dauRa()).contains("Không có `openssl`");
    }

    @Test
    @DisplayName("Workflow gọi đúng script, có quyền issues:write, và danh sách tên miền ⛔ rỗng")
    void workflowNoiDungScript() {
        String wf = doc(timTuGocKho(WORKFLOW));
        assertThat(wf).contains("run: bash " + SCRIPT).contains("issues: write").contains("TEN_MIEN: >-");
        assertThat(wf)
                .as("lượt schedule ⛔ có inputs ⇒ phải rơi về 21; lượt tay có đường DIỄN TẬP nhánh mở issue (luật 7)")
                .contains("NGUONG_NGAY: ${{ inputs.nguong_ngay || '21' }}")
                .contains("nguong_ngay:");
        assertThat(wf)
                .as("7 tên đo 19/09: production 4 + staging 3")
                .contains("thuyloisongnhue.vn www.thuyloisongnhue.vn admin.thuyloisongnhue.vn files.thuyloisongnhue.vn")
                .contains("staging.songnhue.com admin-staging.songnhue.com files-staging.songnhue.com");
    }

    // ---- Bộ khung -------------------------------------------------------------

    private record KetQua(int maThoat, String dauRa, List<String> lenhGh) {}

    private static int viTri(KetQua kq, String tienTo) {
        for (int i = 0; i < kq.lenhGh().size(); i++) {
            if (kq.lenhGh().get(i).startsWith(tienTo)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param hetHan tên miền → notAfter đúng định dạng openssl, hoặc {@link #HONG} cho bắt tay hỏng
     * @param soHieuIssue issue mở sẵn, hoặc {@code null}
     * @param issueJson thân {@code gh issue view --json body,comments}, hoặc {@code null}
     */
    private static KetQua chay(Path thuMuc, Map<String, String> hetHan, String soHieuIssue, String issueJson)
            throws Exception {
        Path bin = dungCongCuGia(thuMuc, hetHan, soHieuIssue, issueJson);
        return chayVoiPath(
                thuMuc,
                bin + java.io.File.pathSeparator + System.getenv("PATH"),
                String.join(" ", new LinkedHashMap<>(hetHan).keySet()));
    }

    private static Path dungCongCuGia(Path thuMuc, Map<String, String> hetHan, String soHieuIssue, String issueJson)
            throws IOException {
        Path bin = Files.createDirectories(thuMuc.resolve("bin"));
        Path noiHan = Files.createDirectories(thuMuc.resolve("han"));
        for (Map.Entry<String, String> e : hetHan.entrySet()) {
            Files.writeString(noiHan.resolve(e.getKey()), e.getValue(), StandardCharsets.UTF_8);
        }
        Path nhatKy = thuMuc.resolve("argv.txt");
        Files.writeString(nhatKy, "", StandardCharsets.UTF_8);
        Files.writeString(thuMuc.resolve("issues.txt"), soHieuIssue == null ? "" : soHieuIssue, StandardCharsets.UTF_8);
        Files.writeString(thuMuc.resolve("view.json"), issueJson == null ? "{}" : issueJson, StandardCharsets.UTF_8);

        // `openssl s_client` in "CERT:<tên>" ra ống; `openssl x509 -enddate` đọc ống ấy rồi tra hạn giả.
        ghiThucThi(
                bin.resolve("openssl"),
                """
                #!/bin/bash
                if [ "$1" = s_client ]; then
                  while [ $# -gt 0 ]; do [ "$1" = -servername ] && { echo "CERT:$2"; exit 0; }; shift; done
                  exit 1
                fi
                if [ "$1" = x509 ]; then
                  read -r dong || exit 1
                  ten="${dong#CERT:}"
                  f='%s'/"$ten"
                  [ -f "$f" ] || exit 1
                  han="$(cat "$f")"
                  [ "$han" = HONG ] && exit 1
                  echo "notAfter=$han"
                  exit 0
                fi
                exit 1
                """
                        .formatted(noiHan));
        ghiThucThi(
                bin.resolve("gh"),
                """
                #!/bin/bash
                printf '%%s\\036' "$*" >> '%s'
                if [ "$1" = issue ] && [ "$2" = list ]; then cat '%s'; fi
                if [ "$1" = issue ] && [ "$2" = view ]; then cat '%s'; fi
                exit 0
                """
                        .formatted(nhatKy, thuMuc.resolve("issues.txt"), thuMuc.resolve("view.json")));
        return bin;
    }

    private static void ghiThucThi(Path tep, String noiDung) throws IOException {
        Files.writeString(tep, noiDung, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(tep, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private static KetQua chayVoiPath(Path thuMuc, String path, String tenMien) throws Exception {
        // `/bin/bash` TUYỆT ĐỐI: bài I dựng PATH chỉ có thư mục giả (khuôn CanhCongQuetTest).
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", timTuGocKho(SCRIPT).toString());
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);
        pb.environment().put("KHO", "songnhue/songnhue");
        pb.environment().put("TEN_MIEN", tenMien);
        pb.environment().put("NGUONG_NGAY", "21");
        pb.environment().put("BAY_GIO", String.valueOf(BAY_GIO.getEpochSecond()));

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script ⛔ kết thúc trong 30 giây");
        }
        Path nhatKy = thuMuc.resolve("argv.txt");
        List<String> lenhGh = Files.exists(nhatKy)
                ? Arrays.stream(Files.readString(nhatKy, StandardCharsets.UTF_8).split("\\u001e"))
                        .filter(d -> !d.isBlank())
                        .toList()
                : List.of();
        return new KetQua(p.exitValue(), dauRa, lenhGh);
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path thu = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(thu)) {
                return thu;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("Không tìm thấy " + duongDanTuongDoi);
    }
}
