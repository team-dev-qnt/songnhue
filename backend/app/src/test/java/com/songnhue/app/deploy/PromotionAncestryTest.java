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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Nhánh đích của một lượt đề bạt không được có commit riêng của nó.</b>
 *
 * <h2>Chuyện đã xảy ra (01/09/2026)</h2>
 *
 * PR đề bạt #72 ({@code dev → staging}) được gộp bằng <b>Squash and merge</b>. GitHub tạo {@code b4a0ac0}
 * — một commit MỘT CHA mang đúng nội dung của {@code dev@2add2bf} (đo được: 0 tệp khác) nhưng không nối
 * vào lịch sử {@code dev}. Nội dung không mất gì; đồ thị thì gốc chung <b>kẹt lại</b> ở {@code bbe0b50}.
 *
 * <p>Lượt đề bạt kế tiếp (PR #76) vì thế phải áp lại nguyên delta của #70 lên một {@code staging} vốn ĐÃ
 * có nó: <b>13 tệp xung đột giả</b>, PR nằm ở {@code CONFLICTING}.
 *
 * <p>⛔⛔ Và cái đắt hơn hẳn: <b>xung đột giả làm chính {@code Promotion guard} không chạy</b>. GitHub
 * dựng {@code refs/pull/N/merge} để chạy workflow {@code pull_request}; PR đụng độ thì ref ấy không dựng
 * được, nên cổng kiểm bắt buộc DUY NHẤT của {@code staging} không bao giờ được lên lịch và treo vĩnh viễn
 * ở <i>"Expected — waiting for status to be reported"</i>. Không một dòng đỏ nào để đọc — cùng hình dạng
 * §10.63, và cùng họ với luật 24 ({@code skipped} được tính là ĐẠT).
 *
 * <h2>Vì sao bài kiểm này chạy script THẬT trên một kho git THẬT</h2>
 *
 * Soi văn bản của {@code kiem-goc-chung.sh} chỉ chứng minh script có mặt. Luật 1 đòi bằng chứng rằng nó
 * <b>bắt được vi phạm</b>, và luật 9 đòi nó <b>phân biệt được hai trạng thái</b> — một bộ canh đỏ với mọi
 * đầu vào cũng "bắt được" vi phạm. Nên mỗi bài dưới đây dựng một kho git trong {@code @TempDir}, tái hiện
 * đúng hình dạng squash / merge, rồi đọc mã thoát thật.
 *
 * <p>⚠ Giới hạn của bộ canh, ghi ra để không ai đọc cái xanh của nó rộng hơn thực tế (luật 28): nó chặn ở
 * lượt đề bạt <b>KẾ TIẾP</b>, không chặn được nút Squash. GitHub không có tuỳ chọn tắt squash cho riêng
 * một nhánh.
 */
class PromotionAncestryTest {

    private static final Path WORKFLOW = timTuGocKho(".github/workflows/promotion-guard.yml");
    private static final Path SCRIPT = timTuGocKho(".github/scripts/kiem-goc-chung.sh");

    // =========================================================================
    //  Đường dây: workflow có thật sự gọi bộ canh, và gọi trên đủ lịch sử không
    // =========================================================================

    @Test
    @DisplayName("⭐ `promotion-guard.yml` phải gọi bộ canh gốc chung")
    void workflowPhaiGoiBoCanh() {
        assertThat(doc(WORKFLOW))
                .as(
                        """
                        `promotion-guard.yml` không gọi `kiem-goc-chung.sh`.

                        Script tồn tại mà không workflow nào chạy là một bộ canh xanh vì chưa từng chạy \
                        — đúng năm cơ chế đã mắc lỗi ấy trước đây (conventions.md §1.5).""")
                .contains("kiem-goc-chung.sh");
    }

    @Test
    @DisplayName("⛔ Bước checkout phải lấy ĐỦ lịch sử — clone nông biến bộ canh thành lời khen suông")
    void phaiCheckoutDuLichSu() {
        // `actions/checkout` mặc định fetch-depth: 1. Trên bản clone nông `git rev-list A..B` trả về
        // RỖNG, và rỗng thì trông y hệt "sạch" (luật 7). Bộ canh sẽ xanh vĩnh viễn, kể cả đúng lúc
        // gốc chung đã gãy.
        assertThat(doc(WORKFLOW))
                .as("promotion-guard.yml checkout mà không có `fetch-depth: 0` — bộ canh sẽ đo trên tập rỗng")
                .contains("fetch-depth: 0");
    }

    // =========================================================================
    //  Bộ canh có bắt được vi phạm thật không
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Một lượt đề bạt bị SQUASH phải làm bộ canh ĐỎ và nêu đích danh commit")
    void squashLamGayGocChungThiPhaiDo(@TempDir Path kho) throws Exception {
        dungKhoCoSquash(kho);

        KetQua kq = chay(kho, "dev", "staging");

        assertThat(kq.maThoat())
                .as("Bộ canh XANH trên một kho đã bị squash làm gãy gốc chung:%n%s", kq.dauRa())
                .isNotZero();
        assertThat(kq.dauRa())
                .as("Bộ canh đỏ nhưng không nói vì sao — người đọc log không có gì để hành động")
                .contains("KHÔNG-PHẢI-MERGE")
                .contains("Create a merge commit");
    }

    @Test
    @DisplayName("⭐⭐ Một lượt đề bạt gộp bằng MERGE COMMIT phải XANH — bộ canh phải phân biệt hai trạng thái")
    void mergeCommitKhongBiBaoNham(@TempDir Path kho) throws Exception {
        // Luật 9: một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì. Không có
        // bài này thì một script `exit 1` vô điều kiện vẫn qua được bài trên.
        dungKhoDeBatDungCach(kho);

        KetQua kq = chay(kho, "dev", "staging");

        assertThat(kq.maThoat())
                .as("Bộ canh ĐỎ trên một lượt đề bạt gộp đúng cách — nó sẽ chặn mọi lượt hợp lệ:%n%s", kq.dauRa())
                .isZero();
        assertThat(kq.dauRa()).contains("không có commit riêng");
    }

    @Test
    @DisplayName("⭐⭐ Cách chữa mà bộ canh KHUYÊN phải chữa được thật — và không đổi một byte nào")
    void cachChuaPhaiChuaDuoc(@TempDir Path kho) throws Exception {
        // Một hướng dẫn sai trong thông báo lỗi tệ hơn không có hướng dẫn: nó gửi người đọc đi làm một
        // việc vô ích rồi kết luận là bộ canh hỏng.
        dungKhoCoSquash(kho);
        String cayTruoc = git(kho, "rev-parse", "dev^{tree}").trim();

        git(kho, "checkout", "-q", "-b", "sua", "dev");
        git(kho, "merge", "-s", "ours", "staging", "-m", "noi lai goc chung");

        assertThat(git(kho, "rev-parse", "HEAD^{tree}").trim())
                .as("`merge -s ours` đã đổi nội dung cây — cách chữa này KHÔNG an toàn như lời khuyên nói")
                .isEqualTo(cayTruoc);
        assertThat(git(kho, "diff", "--name-only", "dev", "HEAD").trim())
                .as("có tệp đổi so với nhánh nguồn")
                .isEmpty();

        KetQua kq = chay(kho, "sua", "staging");
        assertThat(kq.maThoat())
                .as("Vẫn đỏ sau khi áp đúng cách chữa mà bộ canh khuyên:%n%s", kq.dauRa())
                .isZero();
    }

    @Test
    @DisplayName("⛔ Bộ canh phải in con số ĐO ĐƯỢC trước khi kết luận")
    void phaiInSoDoDuoc(@TempDir Path kho) throws Exception {
        // Luật 10: một bộ canh không in số đo thì không phân biệt được "đã đếm và bằng 0" với "chưa
        // đếm gì cả" — đúng cái bẫy clone nông ở trên.
        dungKhoDeBatDungCach(kho);

        assertThat(chay(kho, "dev", "staging").dauRa())
                .as("Bộ canh kết luận mà không in số commit nó đếm được")
                .containsPattern("hơn .*: \\d+ commit, trong đó \\d+ commit không-phải-merge")
                .contains("Gốc chung:");
    }

    // =========================================================================

    /**
     * Kho tái hiện đúng sự cố: {@code staging} nhận nội dung của {@code dev} qua một commit MỘT CHA.
     */
    private static void dungKhoCoSquash(Path kho) throws Exception {
        khoiTao(kho);
        git(kho, "branch", "staging");
        Files.writeString(kho.resolve("f.txt"), "a\nb\n");
        git(kho, "commit", "-qam", "tinh nang #70");

        git(kho, "checkout", "-q", "staging");
        git(kho, "checkout", "-q", "dev", "--", "f.txt");
        git(kho, "commit", "-qm", "tinh nang #70 (#72)"); // ← squash: một cha, sha mới
        git(kho, "checkout", "-q", "dev");

        assertThat(soCha(kho, "staging"))
                .as("Dữ liệu dựng sai — commit đỉnh của staging phải có ĐÚNG một cha để là một squash")
                .isEqualTo(1);
    }

    /** Kho đề bạt đúng luồng: {@code staging} nhận {@code dev} qua một commit merge hai cha. */
    private static void dungKhoDeBatDungCach(Path kho) throws Exception {
        khoiTao(kho);
        git(kho, "branch", "staging");
        Files.writeString(kho.resolve("f.txt"), "a\nb\n");
        git(kho, "commit", "-qam", "tinh nang #70");

        git(kho, "checkout", "-q", "staging");
        git(kho, "merge", "-q", "--no-ff", "dev", "-m", "Merge pull request #72 from team-dev-qnt/dev");
        git(kho, "checkout", "-q", "dev");

        assertThat(soCha(kho, "staging")).isEqualTo(2);
    }

    private static void khoiTao(Path kho) throws Exception {
        git(kho, "init", "-q", "-b", "dev");
        git(kho, "config", "user.email", "kiem@songnhue.test");
        git(kho, "config", "user.name", "kiem");
        git(kho, "config", "commit.gpgsign", "false");
        Files.writeString(kho.resolve("f.txt"), "a\n");
        git(kho, "add", ".");
        git(kho, "commit", "-qm", "nen");
    }

    private static int soCha(Path kho, String ref) throws Exception {
        return git(kho, "rev-list", "--parents", "-n1", ref).trim().split("\\s+").length - 1;
    }

    private record KetQua(int maThoat, String dauRa) {}

    private static KetQua chay(Path kho, String nguon, String dich) throws Exception {
        // ⚠ `bash` tường minh, không dựa vào shebang: runner của GitHub chạy bash còn shell mặc định
        //   của máy dev là zsh, và zsh không tách từ như bash (CLAUDE.md luật 20).
        return chayLenh(kho, List.of("bash", SCRIPT.toString(), nguon, dich), false);
    }

    private static String git(Path kho, String... doiSo) throws Exception {
        List<String> lenh = new ArrayList<>();
        lenh.add("git");
        lenh.addAll(List.of(doiSo));
        return chayLenh(kho, lenh, true).dauRa();
    }

    private static KetQua chayLenh(Path kho, List<String> lenh, boolean batLoi) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.directory(kho.toFile());
        pb.redirectErrorStream(true);
        // Môi trường sạch: một `GIT_DIR` hay `GIT_INDEX_FILE` tình cờ có sẵn sẽ lái mọi lệnh git của
        // bài kiểm ra khỏi kho tạm và làm nó đọc chính kho của dự án.
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH"));
        pb.environment().put("HOME", kho.toString());
        pb.environment().put("LC_ALL", "C.UTF-8");

        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Lệnh %s không kết thúc trong 60 giây".formatted(lenh));
        }
        if (batLoi && p.exitValue() != 0) {
            return fail("Lệnh %s hỏng (mã %d):%n%s".formatted(lenh, p.exitValue(), ra));
        }
        return new KetQua(p.exitValue(), ra);
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
