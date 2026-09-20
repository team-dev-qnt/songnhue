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
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Chặn {@code rsync --delete} của CD xoá tệp mà máy chủ ĐANG GỌI — T11.95 (WS-71).</b>
 *
 * <p>Đo 08/09: thử khô đúng lệnh CD cho thấy lượt production kế tiếp sẽ xoá {@code gia-han-tls.sh} — script mà
 * cron gia hạn TLS gọi ⇒ hỏng câm, chứng chỉ chết 06/12. Cửa sổ ấy khép nhờ may. Script
 * {@code .github/scripts/kiem-xoa-dong-bo.sh} đối chiếu đầu ra {@code --itemize-changes} với crontab/cron.d/systemd.
 *
 * <p>Dữ liệu mẫu là ĐÚNG định dạng rsync in ra (luật 25) — đo 19/09 bằng {@code rsync --dry-run --itemize-changes}
 * thật: dòng xoá {@code *deleting <đường dẫn>}, thư mục kèm {@code /} ở cuối.
 */
class KiemXoaDongBoTest {

    private static final String SCRIPT = ".github/scripts/kiem-xoa-dong-bo.sh";

    private static final String THU_KHO =
            """
            *deleting   backup/cu.sh
            *deleting   thumuc-cu/q.sh
            *deleting   thumuc-cu/
            *deleting   gia-han-tls.sh
            >f+++++++++ backup/x.sh
            """;

    @Test
    @DisplayName("⛔⛔ Cron gọi đúng tệp sắp bị xoá ⇒ thoát 1 và GỌI TÊN tệp ấy (ca 08/09: gia-han-tls.sh)")
    void cronGoiTepSapXoa(@TempDir Path tam) throws Exception {
        KetQua kq = chay(
                tam, THU_KHO, "17 3 * * 1 /opt/songnhue/gia-han-tls.sh >> /var/log/songnhue/gia-han-tls.log 2>&1\n");

        assertThat(kq.maThoat()).isEqualTo(1);
        assertThat(kq.ra()).contains("/opt/songnhue/gia-han-tls.sh").contains("DỪNG trước khi đồng bộ");
    }

    @Test
    @DisplayName("⛔ Tệp nằm DƯỚI một thư mục sắp bị xoá cũng tính — và mỗi đường dẫn báo MỘT lần")
    void tepDuoiThuMucSapXoa(@TempDir Path tam) throws Exception {
        KetQua kq = chay(tam, THU_KHO, "ExecStart=/opt/songnhue/thumuc-cu/q.sh\n");

        assertThat(kq.maThoat()).isEqualTo(1);
        assertThat(kq.ra()
                        .lines()
                        .filter(d -> d.contains("/opt/songnhue/thumuc-cu/q.sh"))
                        .count())
                .as("cả tệp lẫn thư mục cha đều sắp bị xoá — vẫn chỉ MỘT dòng báo")
                .isEqualTo(1);
    }

    /**
     * ⚠ Bài trên ⛔ chứng minh được nhánh khớp THƯ MỤC: rsync thật liệt kê CẢ tệp lẫn thư mục, nên nhánh khớp
     * đúng tên tự bắt được — lượt kiểm chứng ngược 19/09 bỏ hẳn nhánh thư mục mà bài ấy vẫn XANH (luật 9). Bài
     * này chỉ đưa dòng thư mục, để nhánh ấy là đường DUY NHẤT tới kết quả đúng.
     */
    @Test
    @DisplayName(
            "⛔ Chỉ có dòng THƯ MỤC (`*deleting thumuc-cu/`) ⇒ tệp bên trong vẫn bị bắt — vế phân biệt cho nhánh thư mục")
    void chiCoDongThuMuc(@TempDir Path tam) throws Exception {
        KetQua kq = chay(tam, "*deleting   thumuc-cu/\n", "ExecStart=/opt/songnhue/thumuc-cu/q.sh\n");

        assertThat(kq.maThoat()).isEqualTo(1);
        assertThat(kq.ra()).contains("/opt/songnhue/thumuc-cu/q.sh");
    }

    @Test
    @DisplayName("Vế phân biệt (luật 9): máy chủ gọi tệp KHÔNG bị xoá ⇒ thoát 0")
    void khongGiaoNhauThiQua(@TempDir Path tam) throws Exception {
        KetQua kq = chay(tam, THU_KHO, "ExecStart=/opt/songnhue/backup/x.sh\n0 2 * * * /usr/bin/true\n");

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.ra()).contains("rsync sẽ xoá 4 mục").contains("đang gọi 1 đường dẫn");
    }

    @Test
    @DisplayName("⛔ Tiền tố gần giống ⛔ được tính là khớp — `gia-han-tls.sh.cu` ⛔ phải `gia-han-tls.sh`")
    void khongKhopTienToGanGiong(@TempDir Path tam) throws Exception {
        KetQua kq = chay(tam, "*deleting   gia-han-tls.sh.cu\n", "17 3 * * 1 /opt/songnhue/gia-han-tls.sh\n");

        assertThat(kq.maThoat()).isZero();
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    private record KetQua(int maThoat, String ra) {}

    private static KetQua chay(Path tam, String thuKho, String dangGoi) throws Exception {
        Path a = Files.writeString(tam.resolve("thu-kho.txt"), thuKho, StandardCharsets.UTF_8);
        Path b = Files.writeString(tam.resolve("dang-goi.txt"), dangGoi, StandardCharsets.UTF_8);
        // `bash` tường minh: shell mặc định của máy dev là zsh, runner chạy bash (luật 20).
        ProcessBuilder pb = new ProcessBuilder("bash", timTuGocKho(SCRIPT).toString(), a.toString(), b.toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("script ⛔ kết thúc trong 30 giây");
        }
        return new KetQua(p.exitValue(), ra);
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
