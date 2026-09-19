package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Tự kiểm của {@code dat-bien-b6.sh} chạy trong MỌI lượt CI — T11.95 (WS-71).</b>
 *
 * <p>{@code tools/may-chu/tu-kiem-dat-bien-b6.sh} dựng hai máy giả (một {@code ssh} giả hút stdin như ssh thật) và
 * canh 17 bất biến của script đặt biến {@code .env} — gồm lỗi §10.60 (chỉ ghi được biến ĐẦU TIÊN) và, từ WS-71, chỗ
 * đặt bản sao {@code .env}: NGOÀI {@code /opt/songnhue} (rsync {@code --delete} của CD từng xoá bản nằm cạnh
 * {@code .env}) và ⛔ trong thư mục sao lưu (tài khoản kéo T61.9 chép thư mục ấy sang VPS-2).
 *
 * <p>Tới 19/09/2026 tự kiểm ấy chỉ chạy TAY. Một tự kiểm ⛔ ai chạy ⛔ phải một cổng kiểm — bài này nối nó vào CI.
 * ⚠ Phạm vi (luật 28): máy GIẢ; bằng chứng trên máy thật là lượt chạy {@code --thu} của QuanTran.
 */
class DatBienB6TuKiemTest {

    @Test
    @DisplayName("⛔ tu-kiem-dat-bien-b6.sh thoát 0 và in `tự kiểm xanh` — 17 bất biến, hai máy giả")
    void tuKiemXanh() throws Exception {
        Path goc = timTuGocKho("tools/may-chu/tu-kiem-dat-bien-b6.sh")
                .getParent()
                .getParent()
                .getParent();
        ProcessBuilder pb = new ProcessBuilder("bash", "tools/may-chu/tu-kiem-dat-bien-b6.sh").directory(goc.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(120, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("tự kiểm ⛔ kết thúc trong 120 giây");
        }
        assertThat(p.exitValue()).as("tự kiểm đỏ:%n%s", ra).isZero();
        assertThat(ra).contains("tự kiểm xanh");
        assertThat(ra.lines().filter(d -> d.contains("✓")).count())
                .as("chống tập rỗng (luật 7): phải chạy đủ các bất biến, ⛔ thoát sớm trước khi kiểm")
                .isGreaterThanOrEqualTo(17);
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
