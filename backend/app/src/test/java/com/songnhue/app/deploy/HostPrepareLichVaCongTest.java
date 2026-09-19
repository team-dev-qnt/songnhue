package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>{@code host-prepare.sh} dựng lịch gia hạn TLS (T11.88) và kiểm kê cổng đang nghe (T11.54).</b>
 *
 * <h2>Hai lỗ, cả hai chỉ lộ ra bằng đo từ ngoài</h2>
 *
 * <ul>
 *   <li><b>T11.88</b> — Ubuntu 24.04 tối giản ⛔ có gói {@code cron}. VPS-2 chạy từ 07/09 mà {@code systemctl
 *       is-enabled cron} ⇒ {@code not-found}; chứng chỉ staging hết hạn 22/11/2026. Thứ đi cảnh báo chính là thứ
 *       chưa cài. Và dòng cron sổ từng dặn ({@code docker compose … --profile certbot}) thoát 1 ở mọi lượt
 *       (§10.81).
 *   <li><b>T11.54</b> — cổng 5201 (iperf3) mở ra Internet trên VPS-2, ⛔ dịch vụ nào của dự án dùng. Script cũ
 *       chỉ đọc {@code ufw status}, mà VPS-2 ⛔ cài ufw.
 * </ul>
 *
 * <p>Hàm kiểm kê cổng được TRÍCH từ script rồi chạy thật trên đầu ra {@code ss} mẫu — ⛔ chép logic sang Java
 * (chép lại là kiểm bản chép, khuôn {@code HostPrepareQuyenTest}).
 *
 * <p>⚠ Giới hạn (luật 28): bước cài crontab ⛔ chạy được trong bài kiểm — chạy {@code crontab} ở đây là sửa
 * crontab THẬT của người đang chạy bộ kiểm. Vế ấy canh bằng cấu trúc; bằng chứng hành vi là lượt
 * {@code host-prepare.sh --kiem} trên máy chủ (việc của QuanTran, {@code phase4-tracking-tmp.md} §B4).
 */
class HostPrepareLichVaCongTest {

    private static final String SCRIPT = "deploy/host-prepare.sh";

    /** Bốn nơi chép dòng cron — sửa một nơi mà quên nơi khác là người chạy máy cài một dòng khác. */
    private static final List<String> NOI_CHEP_DONG_CRON = List.of(
            "docs/deploy-production-guideline.md",
            "docs/deploy-guideline.md",
            "docs/runbook/ten-mien-va-chung-chi.md",
            "deploy/gia-han-tls.sh");

    /** Đầu ra {@code ss -Htln} đúng định dạng thật (luật 25): v4 + v6, loopback, systemd-resolved, docker-proxy. */
    private static final String SS_MAU =
            """
            LISTEN 0      4096   127.0.0.53%lo:53         0.0.0.0:*
            LISTEN 0      128          0.0.0.0:22         0.0.0.0:*
            LISTEN 0      4096         0.0.0.0:80         0.0.0.0:*
            LISTEN 0      4096         0.0.0.0:443        0.0.0.0:*
            LISTEN 0      4096       127.0.0.1:19090      0.0.0.0:*
            LISTEN 0      5            0.0.0.0:5201       0.0.0.0:*
            LISTEN 0      128             [::]:22            [::]:*
            LISTEN 0      4096            [::]:80            [::]:*
            LISTEN 0      4096           [::1]:9093          [::]:*
            LISTEN 0      5               [::]:5201          [::]:*
            """;

    @Test
    @DisplayName("⛔⛔ T11.54 — kiểm kê cổng BẮT đúng 5201, THA 22/80/443, BỎ QUA loopback, gộp v4+v6 làm MỘT mục")
    void kiemCongNgheChiBatCongLa(@TempDir Path tam) throws Exception {
        String ra = chayKiemCong(tam, null);

        assertThat(dong(ra, "KHONG"))
                .as("đúng MỘT vi phạm (5201) — v4 và v6 của cùng cổng là một mục, ⛔ hai")
                .hasSize(1)
                .allMatch(d -> d.contains("cổng 5201"));
        assertThat(dong(ra, "DAT")).as("22/80/443 được phép, mỗi cổng một dòng").hasSize(3);
        assertThat(ra)
                .as("ổ nghe loopback ⛔ ra được Internet — ⛔ được nhắc tới")
                .doesNotContain(":53 ")
                .doesNotContain("19090")
                .doesNotContain("9093");
    }

    @Test
    @DisplayName(
            "Vế phân biệt (luật 9): cho phép thêm 5201 qua CONG_DUOC_PHEP ⇒ 0 vi phạm — danh sách thật sự quyết định")
    void danhSachChoPhepQuyetDinh(@TempDir Path tam) throws Exception {
        assertThat(dong(chayKiemCong(tam, "22 80 443 5201"), "KHONG")).isEmpty();
    }

    @Test
    @DisplayName("⛔⛔ T11.88 — dòng cron gia hạn TLS khai MỘT lần trong script, bốn nơi chép lại NGUYÊN VĂN")
    void dongCronTrungBonNoi() {
        String dongCron = giaTriBien(doc(SCRIPT), "DONG_CRON_TLS");
        assertThat(dongCron)
                .as("dòng chuẩn phải gọi script gia hạn, ⛔ compose (§10.81)")
                .startsWith("17 3 * * 1 /opt/songnhue/gia-han-tls.sh ")
                .doesNotContain("docker compose");
        for (String noi : NOI_CHEP_DONG_CRON) {
            assertThat(doc(noi))
                    .as("`%s` phải chép ĐÚNG dòng cron của host-prepare.sh — người chạy máy cài theo tài liệu", noi)
                    .contains(dongCron);
        }
        // `/opt/songnhue/` là đích rsync của `deploy/` ⇒ tệp phải có thật trong kho và chạy được.
        Path script = timTuGocKho("deploy/gia-han-tls.sh");
        assertThat(Files.isExecutable(script))
                .as("deploy/gia-han-tls.sh phải executable")
                .isTrue();
    }

    @Test
    @DisplayName("⛔ T11.88 — `cron` là công cụ BẮT BUỘC, dịch vụ phải ĐANG CHẠY, dòng cron cũ dạng compose bị gọi tên")
    void cronLaCongCuBatBuoc() {
        String s = doc(SCRIPT);
        Matcher vong =
                Pattern.compile("^for goi in ([^;]+); do", Pattern.MULTILINE).matcher(s);
        assertThat(vong.find()).as("⛔ tìm thấy vòng lặp công cụ bắt buộc").isTrue();
        assertThat(List.of(vong.group(1).trim().split("\\s+"))).contains("rsync", "cron");
        assertThat(s).as("đo dịch vụ ĐANG CHẠY, ⛔ chỉ đo gói đã cài").contains("systemctl is-active --quiet cron");
        assertThat(s)
                .as("dòng cron cũ dạng compose phải bị gọi tên — nó thoát 1 ở mọi lượt (§10.81)")
                .contains("grep -q -- '--profile certbot'");
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    private static String chayKiemCong(Path tam, String choPhep) throws IOException, InterruptedException {
        String s = doc(SCRIPT);
        String ham = trichHam(s, "kiem_cong_nghe");
        String khai = Pattern.compile("^CONG_DUOC_PHEP=.*$", Pattern.MULTILINE)
                .matcher(s)
                .results()
                .findFirst()
                .orElseThrow(() -> new AssertionError("⛔ tìm thấy dòng khai CONG_DUOC_PHEP"))
                .group();
        assertThat(ham)
                .as("⛔ trích được hàm kiem_cong_nghe — script đã đổi hình dạng")
                .isNotBlank();

        Path ss = Files.writeString(tam.resolve("ss.txt"), SS_MAU, StandardCharsets.UTF_8);
        Path chay = tam.resolve("chay.sh");
        // ⚠ Nối chuỗi, ⛔ `.formatted()`: thân hàm có `%` của shell (khuôn HostPrepareQuyenTest).
        Files.writeString(
                chay,
                "set -u\n"
                        + khai
                        + "\n"
                        + "do_dac() { printf 'DAT %s %s\\n' \"$1\" \"$2\"; }\n"
                        + "hong()   { printf 'KHONG %s\\n' \"$*\"; }\n"
                        + ham
                        + "\nkiem_cong_nghe < \"$1\"\n",
                StandardCharsets.UTF_8);

        // `bash` tường minh: shell mặc định của máy dev là zsh, runner chạy bash (luật 20).
        ProcessBuilder pb = new ProcessBuilder("bash", chay.toString(), ss.toString());
        if (choPhep != null) {
            pb.environment().put("CONG_DUOC_PHEP", choPhep);
        } else {
            pb.environment().remove("CONG_DUOC_PHEP");
        }
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor()).as("script trích ra phải thoát 0:%n%s", ra).isZero();
        return ra;
    }

    private static List<String> dong(String ra, String tienTo) {
        return ra.lines().filter(d -> d.startsWith(tienTo + " ")).toList();
    }

    /** Giá trị nháy đơn của {@code TEN='…'} ở đầu dòng. */
    private static String giaTriBien(String s, String ten) {
        Matcher m =
                Pattern.compile("^" + ten + "='([^']*)'$", Pattern.MULTILINE).matcher(s);
        assertThat(m.find()).as("⛔ tìm thấy dòng khai %s='…'", ten).isTrue();
        return m.group(1);
    }

    /** Cắt từ {@code <ten>() {} cho tới dòng {@code }} ở cột 0. */
    private static String trichHam(String noiDung, String ten) {
        Matcher m = Pattern.compile(
                        "^" + Pattern.quote(ten) + "\\(\\)\\s*\\{.*?^\\}", Pattern.MULTILINE | Pattern.DOTALL)
                .matcher(noiDung);
        return m.find() ? m.group() : "";
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
