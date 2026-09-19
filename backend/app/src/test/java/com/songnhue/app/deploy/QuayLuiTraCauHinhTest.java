package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.yaml.snakeyaml.Yaml;

/**
 * <b>Quay lui trả CẢ cấu hình lẫn ảnh — T11.9 · DOD0.21 (WS-71).</b>
 *
 * <h2>Chuyện đã xảy ra — 17/09/2026, run 35236229504</h2>
 *
 * Lượt CD staging hỏng SAU {@code up -d}: cấu hình nginx MỚI ném {@code [emerg]}. Bước quay lui khi ấy chỉ
 * {@code up -d} ba ẢNH cũ — cấu hình vừa rsync ở lại trên máy, và nginx (template gắn bind mount) ⛔ được tạo lại
 * vì compose ⛔ thấy tệp đổi. Site vẫn chết. Quay lui có từ WS-11, và đó là lượt đầu tiên nó có một bản ĐÃ BỊ THAY
 * để dựng lại — nó thất bại.
 *
 * <h2>Bài này canh</h2>
 *
 * <ol>
 *   <li><b>Hành vi</b> — trích NGUYÊN VĂN lệnh rsync chụp và lệnh rsync trả từ {@code deploy.yml}, chạy thật trên
 *       một cây giống {@code /opt/songnhue}: sau chụp → đồng bộ bản mới → trả, cấu hình phải về đúng bản cũ, còn
 *       {@code .env}, {@code keys/}, {@code env/}, bản sao {@code .env.bak-*} ⛔ bị đụng.
 *   <li><b>Luật 14</b> — danh sách {@code --exclude} TRÙNG ở bốn nơi (chụp · thử khô · đồng bộ · trả). Một nơi thiếu
 *       {@code .ban-truoc*} là rsync {@code --delete} xoá mất bản chụp ngay trước khi cần nó.
 *   <li><b>Thứ tự</b> — lấy {@code deploy/} của đúng commit ngay sau checkout; chụp TRƯỚC đồng bộ; diễn tập SAU
 *       triển khai và TRƯỚC smoke test.
 *   <li><b>Quay lui đủ bốn phép đo</b> của bước Triển khai (luật 35): {@code nginx -t} · {@code --force-recreate}
 *       · ID ảnh · nginx healthy — và chỉ trả bản chụp của ĐÚNG lượt này (mốc).
 *   <li><b>Diễn tập chỉ staging</b> — điều kiện nằm trong {@code if}, ⛔ trong lời dặn.
 * </ol>
 *
 * <p>⚠ Giới hạn (luật 28): bài chạy rsync trên máy dev, ⛔ qua SSH; và ⛔ chạy {@code docker compose}. Bằng chứng
 * trọn vẹn là lượt diễn tập DOD0.21 trên staging (biến {@code DIEN_TAP_QUAY_LUI}).
 */
class QuayLuiTraCauHinhTest {

    private static final String WORKFLOW = ".github/workflows/deploy.yml";

    private static final Set<String> LOAI_TRU_CHUAN =
            Set.of(".env*", "env/", "keys/", ".ban-truoc*", "compose.local.yml", "compose.infra.yml");

    @Test
    @DisplayName(
            "⛔⛔ Chụp → đồng bộ bản mới → trả: cấu hình về ĐÚNG bản cũ, .env/keys/env/.env.bak ⛔ bị đụng (chạy rsync thật)")
    void chupRoiTraChayThat(@TempDir Path tam) throws Exception {
        String yml = doc(WORKFLOW);
        String lenhChup = dongRsyncTrongKhoi(yml, "\"chup-cau-hinh\"");
        String lenhTra = dongRsyncTrongKhoi(yml, "\"quay-lui\"");

        Path opt = Files.createDirectories(tam.resolve("opt-songnhue"));
        ghi(opt, "compose.prod.yml", "phien-ban: cu");
        ghi(opt, "nginx/templates/default.conf.template", "server { cu }");
        ghi(opt, "backup/khoi-phuc.sh", "cu");
        ghi(opt, ".env", "BI_MAT=that");
        ghi(opt, ".env.bak-20260916193335", "BI_MAT=cu");
        ghi(opt, "keys/jwt.pem", "khoa");
        ghi(opt, "env/prod.env", "mau");

        // Chụp — đúng khối của bước "Chụp cấu hình đang chạy" (trích nguyên văn).
        chayBash(
                opt,
                "set -euo pipefail\n" + lenhChup.replace("if rsync", "rsync").replaceAll(";\\s*then\\s*$", "")
                        + "\nrm -rf .ban-truoc && mv .ban-truoc.moi .ban-truoc\n");
        assertThat(opt.resolve(".ban-truoc/compose.prod.yml")).exists();
        assertThat(opt.resolve(".ban-truoc/.env"))
                .as("bản chụp ⛔ được mang .env")
                .doesNotExist();
        assertThat(opt.resolve(".ban-truoc/keys"))
                .as("bản chụp ⛔ được mang keys/")
                .doesNotExist();

        // Mô phỏng bước Đồng bộ đưa bản MỚI lên: sửa một tệp, thêm một tệp, xoá một tệp.
        ghi(opt, "compose.prod.yml", "phien-ban: moi");
        ghi(opt, "nginx/templates/moi.conf", "server { moi }");
        Files.delete(opt.resolve("backup/khoi-phuc.sh"));

        // Trả — đúng lệnh rsync của bước "Quay lui bản cũ".
        chayBash(opt, "set -euo pipefail\n" + lenhTra + "\n");

        assertThat(Files.readString(opt.resolve("compose.prod.yml"))).isEqualTo("phien-ban: cu");
        assertThat(opt.resolve("nginx/templates/moi.conf"))
                .as("tệp chỉ bản MỚI có phải bị gỡ — nếu ⛔, nginx vẫn nạp cấu hình hỏng")
                .doesNotExist();
        assertThat(opt.resolve("backup/khoi-phuc.sh"))
                .as("tệp bản mới đã xoá phải quay lại")
                .exists();
        assertThat(Files.readString(opt.resolve(".env"))).as(".env ⛔ được đụng").isEqualTo("BI_MAT=that");
        assertThat(opt.resolve(".env.bak-20260916193335"))
                .as("bản sao .env ⛔ bị --delete xoá (T11.95)")
                .exists();
        assertThat(opt.resolve("keys/jwt.pem")).exists();
        assertThat(opt.resolve("env/prod.env")).exists();
        assertThat(opt.resolve(".ban-truoc/compose.prod.yml"))
                .as("bản chụp phải còn — trả KHÔNG được tự xoá nguồn của chính nó")
                .exists();
    }

    @Test
    @DisplayName("⛔ Luật 14 — danh sách --exclude TRÙNG ở bốn lệnh rsync (chụp · thử khô · đồng bộ · trả)")
    void loaiTruTrungBonNoi() {
        List<Set<String>> tapLoaiTru = new ArrayList<>();
        for (String lenh : lenhRsyncNoiDong(doc(WORKFLOW))) {
            Set<String> tap = new TreeSet<>();
            Matcher m = Pattern.compile("--exclude '([^']+)'").matcher(lenh);
            while (m.find()) {
                tap.add(m.group(1));
            }
            tapLoaiTru.add(tap);
        }
        assertThat(tapLoaiTru)
                .as("phải thấy ĐÚNG bốn lệnh rsync mang --exclude — ít hơn là một nơi đã thôi loại trừ")
                .hasSize(4)
                .allSatisfy(tap -> assertThat(tap)
                        .as("⛔ thiếu `.ban-truoc*` ⇒ rsync --delete xoá bản chụp; thiếu `.env*` ⇒ xoá bản sao .env")
                        .isEqualTo(new TreeSet<>(LOAI_TRU_CHUAN)));
    }

    @Test
    @DisplayName(
            "⛔ Thứ tự: deploy/ của đúng commit ngay sau checkout · chụp trước đồng bộ · diễn tập giữa triển khai và smoke test")
    @SuppressWarnings("unchecked")
    void thuTuBuoc() {
        List<Map<String, Object>> buoc =
                (List<Map<String, Object>>) ((Map<String, Object>) ((Map<String, Object>) new Yaml()
                                        .<Map<String, Object>>load(doc(WORKFLOW))
                                        .get("jobs"))
                                .get("deploy"))
                        .get("steps");
        List<String> ten = buoc.stream()
                .map(b -> String.valueOf(b.getOrDefault("name", b.get("uses"))))
                .toList();

        assertThat(ten.get(0)).startsWith("actions/checkout");
        assertThat(ten.get(1)).isEqualTo("Lấy deploy/ của đúng commit triển khai");
        assertThat(ten)
                .containsSubsequence(
                        "Chụp cấu hình đang chạy",
                        "Thử khô đồng bộ",
                        "Đồng bộ cấu hình lên máy chủ",
                        "Ghi lại bản đang chạy",
                        "Triển khai",
                        "Diễn tập quay lui (chỉ staging)",
                        "Smoke test",
                        "Quay lui bản cũ");
    }

    @Test
    @DisplayName("⛔⛔ Quay lui: trả bản chụp của ĐÚNG lượt · nginx -t · --force-recreate · ID ảnh · nginx healthy")
    void quayLuiDuBonPhepDo() {
        String khoi = khoiTuXa(doc(WORKFLOW), "\"quay-lui\"");
        assertThat(khoi)
                .contains("[ \"\\$(cat .ban-truoc.moc 2>/dev/null || true)\" = \"$LUOT\" ]")
                .contains("nginx nginx -t")
                .contains("up -d --force-recreate app admin-app public-web nginx")
                .contains("docker image inspect --format '{{.Id}}'")
                .contains("{{.State.Health.Status}}");
        assertThat(khoi.indexOf("nginx nginx -t"))
                .as("kiểm cấu hình TRƯỚC khi tạo lại container")
                .isLessThan(khoi.indexOf("up -d --force-recreate"));
    }

    @Test
    @DisplayName("⛔⛔ Diễn tập DOD0.21 CHỈ staging — điều kiện nằm TRONG `if`, ⛔ trong lời dặn")
    @SuppressWarnings("unchecked")
    void dienTapChiStaging() {
        List<Map<String, Object>> buoc =
                (List<Map<String, Object>>) ((Map<String, Object>) ((Map<String, Object>) new Yaml()
                                        .<Map<String, Object>>load(doc(WORKFLOW))
                                        .get("jobs"))
                                .get("deploy"))
                        .get("steps");
        Map<String, Object> dienTap = buoc.stream()
                .filter(b -> "Diễn tập quay lui (chỉ staging)".equals(b.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("⛔ thấy bước diễn tập"));
        assertThat(String.valueOf(dienTap.get("if")))
                .contains("inputs.moi_truong == 'staging'")
                .contains("vars.DIEN_TAP_QUAY_LUI == 'true'");
    }

    @Test
    @DisplayName("⛔ deploy/ lấy từ ĐÚNG commit_sha — lượt quay lui tay ⛔ dựng ảnh cũ trên cấu hình mới")
    void deployCuaDungCommit() {
        String yml = doc(WORKFLOW);
        int batDau = yml.indexOf("- name: Lấy deploy/ của đúng commit triển khai");
        int ketThuc = yml.indexOf("- name:", batDau + 10);
        String khoi = yml.substring(batDau, ketThuc);
        assertThat(khoi)
                .contains("SHA: ${{ inputs.commit_sha }}")
                .contains("git fetch --no-tags --depth=1 origin \"$SHA\"")
                .contains("rm -rf deploy")
                .contains("git checkout \"$SHA\" -- deploy/");
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    /** Thân heredoc REMOTE của một lời gọi {@code chay-tu-xa.sh <nhãn>}. */
    private static String khoiTuXa(String yml, String nhan) {
        int goi = yml.indexOf("chay-tu-xa.sh " + nhan);
        assertThat(goi).as("⛔ thấy lời gọi chay-tu-xa.sh %s", nhan).isNotNegative();
        int batDau = yml.indexOf('\n', goi) + 1;
        Matcher ketThuc = Pattern.compile("^\\s*REMOTE\\s*$", Pattern.MULTILINE).matcher(yml);
        assertThat(ketThuc.find(batDau))
                .as("⛔ thấy dòng đóng REMOTE sau %s", nhan)
                .isTrue();
        return yml.substring(batDau, ketThuc.start());
    }

    /** Dòng {@code rsync} duy nhất trong khối từ xa ấy, bỏ thụt lề. */
    private static String dongRsyncTrongKhoi(String yml, String nhan) {
        List<String> dong = khoiTuXa(yml, nhan)
                .lines()
                .map(String::strip)
                .filter(d -> d.contains("rsync -a --delete"))
                .toList();
        assertThat(dong).as("khối %s phải có ĐÚNG một lệnh rsync", nhan).hasSize(1);
        return dong.get(0);
    }

    /** Mọi lệnh rsync (đã ghép dòng nối bằng `\`) có mang --exclude. */
    private static List<String> lenhRsyncNoiDong(String yml) {
        List<String> ket = new ArrayList<>();
        List<String> dong = yml.lines().toList();
        for (int i = 0; i < dong.size(); i++) {
            String d = dong.get(i).strip();
            if (d.startsWith("#") || !d.contains("rsync ")) {
                continue;
            }
            StringBuilder gom = new StringBuilder(d);
            int j = i;
            while (dong.get(j).stripTrailing().endsWith("\\") && j + 1 < dong.size()) {
                gom.append(' ').append(dong.get(++j).strip());
            }
            if (gom.indexOf("--exclude") >= 0) {
                ket.add(gom.toString());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(ket));
    }

    private static void chayBash(Path thuMuc, String kichBan) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", kichBan).directory(thuMuc.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("rsync ⛔ kết thúc trong 60 giây");
        }
        assertThat(p.exitValue())
                .as("lệnh trích từ deploy.yml phải thoát 0:%n%s%n%s", kichBan, ra)
                .isZero();
    }

    private static void ghi(Path goc, String duongDan, String noiDung) throws IOException {
        Path p = goc.resolve(duongDan);
        Files.createDirectories(p.getParent());
        Files.writeString(p, noiDung, StandardCharsets.UTF_8);
    }

    private static String doc(String duongDan) {
        try {
            return Files.readString(timTuGocKho(duongDan), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
