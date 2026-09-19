package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
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

/**
 * <b>Không tệp nào git theo dõi mang một chuỗi có HÌNH DẠNG bí mật của nhà cung cấp — kể cả bí mật GIẢ.</b>
 *
 * <h2>Chuyện đã xảy ra — T68.37</h2>
 *
 * Cảnh báo secret scanning <b>#1</b> của GitHub mở từ 16/09/2026 ({@code publicly_leaked}): một token Telegram
 * <b>giả</b> {@code <9 chữ số>:<35 ký tự>} viết liền trong đồ gá {@code tools/may-chu/tu-kiem-dat-bien-b6.sh}. ⛔ Bí
 * mật thật nào lộ — nhưng cái chuông ⛔ tới ai suốt ba ngày, và một chuông giả dạy người ta bỏ qua chuông thật.
 * Khi kho chuyển private mà ⛔ mua <i>Secret Protection</i>, cái chuông ấy <b>tắt hẳn</b> — bài này là lưới còn
 * lại, chạy trong mọi lượt CI.
 *
 * <p>Đồ gá nay dựng giá trị lúc chạy và cố ý lệch mẫu nhà cung cấp (7 chữ số · 30 ký tự) mà vẫn qua mẫu hợp lệ
 * của {@code dat-bien-b6.sh}. Bài này bắt đồ gá TIẾP THEO trước khi nó lên kho.
 *
 * <h2>⚠ Phạm vi (luật 28)</h2>
 *
 * Mọi tệp {@code git ls-files} liệt kê, trừ tệp nhị phân và tệp &gt; 5 MB. Chỉ các mẫu CÓ TIỀN TỐ/CẤU TRÚC riêng
 * của nhà cung cấp — ⛔ mẫu entropy chung (sẽ đỏ giả trên hash, UUID, số hiệu). ⛔ Thay được một bộ quét bí mật
 * thật (gitleaks, Secret Protection): nó ⛔ quét LỊCH SỬ commit, chỉ quét cây hiện tại.
 */
class KhongChuoiHinhDangBiMatTest {

    /**
     * Mẫu theo nhà cung cấp. ⚠ Ghép từ mảnh ở vài chỗ để chính tệp này ⛔ tự khớp — cùng mẹo
     * {@code BackupRestoreFlagsTest.CO_CAM}.
     */
    static final Map<String, Pattern> MAU = Map.of(
            "Telegram bot token", Pattern.compile("\\b[0-9]{8,10}:[A-Za-z0-9_-]{35}\\b"),
            "Slack incoming webhook",
                    Pattern.compile(
                            "hooks\\.slack\\.com/services/T[A-Za-z0-9_]{8,}/B[A-Za-z0-9_]{8,}/[A-Za-z0-9_]{24}"),
            "Slack token", Pattern.compile("\\bxox[baprs]-[0-9A-Za-z-]{10,}"),
            "GitHub token", Pattern.compile("\\bgh[pousr]_[A-Za-z0-9]{36}\\b"),
            "GitHub fine-grained PAT", Pattern.compile("\\bgithub_pat_[A-Za-z0-9_]{82}\\b"),
            "AWS access key id", Pattern.compile("\\b(?:AKIA|ASIA)[0-9A-Z]{16}\\b"),
            "Google API key", Pattern.compile("\\bAIza[0-9A-Za-z_-]{35}\\b"));

    private static final long TOI_DA_BYTE = 5L * 1024 * 1024;

    @Test
    @DisplayName("⛔⛔ T68.37 — ⛔ tệp git theo dõi nào mang chuỗi hình dạng bí mật nhà cung cấp (kể cả GIẢ)")
    void khongTepNaoMangHinhDangBiMat() {
        Path goc = gocKho();
        List<String> tep = tepGitTheoDoi(goc);
        assertThat(tep)
                .as("chống tập rỗng (luật 7): `git ls-files` phải liệt kê cả nghìn tệp — ít hơn là phép liệt kê hỏng")
                .hasSizeGreaterThan(500)
                .contains("tools/may-chu/tu-kiem-dat-bien-b6.sh");

        List<String> viPham = new ArrayList<>();
        int daQuet = 0;
        for (String duongDan : tep) {
            Path p = goc.resolve(duongDan);
            String noiDung = docVanBan(p);
            if (noiDung == null) {
                continue;
            }
            daQuet++;
            for (Map.Entry<String, Pattern> mau : MAU.entrySet()) {
                Matcher m = mau.getValue().matcher(noiDung);
                if (m.find()) {
                    viPham.add("%s — %s (…%s…)".formatted(duongDan, mau.getKey(), cheBot(m.group())));
                }
            }
        }
        assertThat(daQuet)
                .as("phải quét được phần lớn số tệp — nếu ⛔ thì bộ lọc nhị phân ăn quá tay")
                .isGreaterThan(500);
        assertThat(viPham)
                .as(
                        """
                        Tệp git theo dõi mang chuỗi có HÌNH DẠNG bí mật của nhà cung cấp:
                        %s

                        Kể cả khi đó là giá trị GIẢ của đồ gá: bộ quét bí mật khớp theo hình dạng, nên mỗi lượt quét là \
                        một chuông giả (cảnh báo #1, T68.37). Dựng giá trị giả LÚC CHẠY và cho nó lệch mẫu nhà cung \
                        cấp (xem `tools/may-chu/tu-kiem-dat-bien-b6.sh`). Nếu là bí mật THẬT: thu hồi ngay ở nhà cung \
                        cấp — gỡ khỏi cây ⛔ gỡ khỏi lịch sử.""",
                        String.join("\n", viPham))
                .isEmpty();
    }

    @Test
    @DisplayName("Tự kiểm (luật 1): mỗi mẫu BẮT được một chuỗi đúng hình dạng, và THA giá trị giả của đồ gá mới")
    void tuKiemMau() {
        String so = "123456789";
        Map<String, String> duong = Map.of(
                "Telegram bot token", so + ":" + "Ab".repeat(17) + "c",
                "Slack incoming webhook",
                        "hooks.slack.com/services/" + "T" + "ABCDEFGH1" + "/B" + "ABCDEFGH2" + "/" + "x".repeat(24),
                "Slack token", "xo" + "xb-" + "1234567890-abcdef",
                "GitHub token", "gh" + "p_" + "a".repeat(36),
                "GitHub fine-grained PAT", "github" + "_pat_" + "a".repeat(82),
                "AWS access key id", "AK" + "IA" + "ABCDEFGHIJKLMNOP",
                "Google API key", "AI" + "za" + "a".repeat(35));
        assertThat(duong.keySet()).as("mỗi mẫu phải có một ca dương").isEqualTo(MAU.keySet());
        duong.forEach((ten, chuoi) -> assertThat(MAU.get(ten).matcher(chuoi).find())
                .as("mẫu `%s` phải bắt được `%s`", ten, cheBot(chuoi))
                .isTrue());

        // Giá trị giả của đồ gá mới — phải qua mẫu hợp lệ của dat-bien-b6.sh mà KHÔNG khớp mẫu nhà cung cấp.
        String telegramGia = "7".repeat(7) + ":" + "a".repeat(30);
        String slackGia = "hooks.slack.com/services/T000/B000/" + "x".repeat(24);
        assertThat(telegramGia).matches("^[0-9]+:[A-Za-z0-9_-]{30,}$");
        MAU.forEach((ten, mau) -> {
            assertThat(mau.matcher(telegramGia).find())
                    .as("`%s` ⛔ được khớp token giả của đồ gá", ten)
                    .isFalse();
            assertThat(mau.matcher(slackGia).find())
                    .as("`%s` ⛔ được khớp webhook giả của đồ gá", ten)
                    .isFalse();
        });
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    /** Che phần giữa — thông điệp lỗi ⛔ được tự mình in lại một bí mật THẬT ra log CI. */
    private static String cheBot(String s) {
        return s.length() <= 8 ? "***" : s.substring(0, 4) + "***" + s.substring(s.length() - 2);
    }

    /** Nội dung văn bản, hoặc {@code null} nếu là nhị phân / quá lớn / đã bị xoá khỏi cây làm việc. */
    private static String docVanBan(Path p) {
        try {
            if (!Files.isRegularFile(p) || Files.size(p) > TOI_DA_BYTE) {
                return null;
            }
            byte[] b = Files.readAllBytes(p);
            for (int i = 0; i < Math.min(b.length, 8192); i++) {
                if (b[i] == 0) {
                    return null;
                }
            }
            return new String(b, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> tepGitTheoDoi(Path goc) {
        String ra = chayGit(goc, "ls-files", "-z");
        List<String> tep = new ArrayList<>();
        for (String t : ra.split("\0")) {
            if (!t.isBlank()) {
                tep.add(t);
            }
        }
        return tep;
    }

    private static Path gocKho() {
        return Paths.get(chayGit(Paths.get(System.getProperty("user.dir")), "rev-parse", "--show-toplevel")
                .strip());
    }

    private static String chayGit(Path thuMuc, String... doiSo) {
        List<String> lenh = new ArrayList<>(List.of("git", "-C", thuMuc.toString()));
        lenh.addAll(List.of(doiSo));
        try {
            Process p = new ProcessBuilder(lenh).redirectErrorStream(true).start();
            String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IllegalStateException("`" + String.join(" ", lenh) + "` hỏng:\n" + ra);
            }
            return ra;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
