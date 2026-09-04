package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>"Chưa xong" và "hỏng" là hai trạng thái khác nhau — cổng đề bạt phải phân biệt được.</b>
 *
 * <h2>Chuyện đã xảy ra (1/9/2026)</h2>
 *
 * PR #77 được gộp vào {@code dev} lúc 11:06:0x. GitHub cập nhật {@code dev}, PR đề bạt #76 đổi head
 * SHA, và {@code Promotion guard} chạy <b>lúc 11:06:19</b> — trong khi CI của {@code dev@4e564d9} vừa
 * mới bắt đầu. API trả {@code conclusion: null} cho hai check bắt buộc, và bản cũ rơi vào nhánh
 * {@code *)}:
 *
 * <pre>
 *   ##[error]Backend — build, lint, test của commit 4e564d9 kết thúc với 'null'.
 * </pre>
 *
 * Câu ấy <b>nói sai chuyện đang xảy ra</b>: không có gì "kết thúc" cả. Cổng đề bạt đỏ, PR #76 bị
 * chặn, và người đọc log bị dẫn đi tìm một lượt CI hỏng vốn không tồn tại.
 *
 * <p>⭐ Luật 9 đúng nguyên văn: <i>một khẳng định không phân biệt được hai trạng thái thì không khẳng
 * định gì.</i> {@code null} bảo <i>đợi thêm</i>, {@code failure} bảo <i>dừng lại và đi sửa</i> — bản
 * cũ trộn chung.
 *
 * <p>⚠ Và nó là một <b>cuộc đua</b>: mở PR đề bạt lâu sau lượt gộp thì CI đã xong và cổng xanh. Loại
 * lỗi chỉ hiện ra khi hai việc xảy ra sát nhau là loại dễ đóng hồ sơ nhầm nhất — <i>"chạy lại thấy
 * xanh rồi"</i>. Vì thế logic được tách khỏi bước {@code run:} của workflow: nằm trong workflow thì
 * muốn thử nhánh {@code null} phải <b>thắng một cuộc đua</b> mới tái hiện được, còn tách ra thì đưa
 * thẳng ba dòng đầu vào là đo được cả ba nhánh (conventions.md §1.5).
 */
class PromotionCheckStateTest {

    private static final Path SCRIPT = timTuGocKho(".github/scripts/phan-loai-check-chang-truoc.sh");
    private static final Path WORKFLOW = timTuGocKho(".github/workflows/promotion-guard.yml");

    private static final String BE = "Backend — build, lint, test";
    private static final String FE = "Frontend — lint";

    @Test
    @DisplayName("⭐ Mọi check đã xong và đạt ⇒ mã thoát 0")
    void tatCaDatThiXanh() throws Exception {
        KetQua kq = chay(BE + "=success\n" + FE + "=skipped\n");

        assertThat(kq.maThoat()).as("%s", kq.dauRa()).isZero();
        assertThat(kq.dauRa()).contains("không có phép kiểm nào hỏng");
    }

    @Test
    @DisplayName("⭐⭐ Check CHƯA XONG (`null`) ⇒ mã 2 — KHÔNG phải mã hỏng, và không nói là đã kết thúc")
    void chuaXongThiKhongPhaiHong() throws Exception {
        KetQua kq = chay(BE + "=null\n" + FE + "=success\n");

        assertThat(kq.maThoat())
                .as("`null` phải cho mã 2 (chờ), không phải 1 (hỏng):%n%s", kq.dauRa())
                .isEqualTo(2);
        assertThat(kq.dauRa())
                .as("Không được nói một phép kiểm đang chạy là đã 'kết thúc' — đúng câu đã sai 1/9")
                .doesNotContain("kết thúc với")
                .contains("còn đang chạy");
    }

    @Test
    @DisplayName("⭐⭐ Check HỎNG ⇒ mã 1, và hỏng THẮNG chưa-xong")
    void hongThangChuaXong() throws Exception {
        // Luật 9 chiều ngược lại: nếu chỉ có bài "null ⇒ mã 2" thì một script trả mã 2 vô điều kiện
        // cũng qua — và cổng sẽ chờ mãi một thứ đã đỏ.
        KetQua kq = chay(BE + "=failure\n" + FE + "=null\n");

        assertThat(kq.maThoat())
                .as("Một check đã đỏ thì đợi thêm không đổi được gì:%n%s", kq.dauRa())
                .isEqualTo(1);
        assertThat(kq.dauRa()).contains("kết thúc với 'failure'");
    }

    @Test
    @DisplayName("⛔ Không có check nào ⇒ ĐỎ, không phải xanh (luật 7: tập rỗng)")
    void tapRongPhaiDo() throws Exception {
        KetQua kq = chay("");

        assertThat(kq.maThoat())
                .as("Đầu vào rỗng cho ra mã 0 nghĩa là commit chưa từng chạy CI vẫn được đề bạt")
                .isEqualTo(1);
        assertThat(kq.dauRa()).contains("Không tìm thấy kết quả CI nào");
    }

    @Test
    @DisplayName("⛔ Tên job có DẤU CÁCH vẫn phải đọc đúng — `for` sẽ vỡ tên thành 5 mảnh")
    void tenJobCoDauCach() throws Exception {
        // Đã tái hiện bằng dữ liệu thật: `for muc in $runs` cho ra [Frontend] [—] [lint=success]
        // [Backend] [—] [build,] [lint,] [test=success] và cổng LUÔN đỏ. Thử ở máy dùng zsh KHÔNG
        // thấy lỗi — zsh không tách từ mặc định (CLAUDE.md luật 20).
        KetQua kq = chay(BE + "=success\n" + FE + "=success\n");

        assertThat(kq.maThoat()).as("%s", kq.dauRa()).isZero();
        assertThat(kq.dauRa())
                .as("Tên job bị cắt — bộ đọc đang tách từ theo khoảng trắng")
                .contains("✓ " + BE + " = success")
                .contains("✓ " + FE + " = success");
    }

    @Test
    @DisplayName("⭐ Workflow phải GỌI bộ phân loại và phải CHỜ khi nó trả mã 2")
    void workflowPhaiNoiDayVaBietCho() {
        String yml = doc(WORKFLOW);

        assertThat(yml)
                .as("promotion-guard.yml không gọi bộ phân loại — script tồn tại mà không ai chạy")
                .contains("phan-loai-check-chang-truoc.sh");
        assertThat(yml)
                .as(
                        """
                        Workflow không có vòng chờ. Bắt được `null` mà vẫn đỏ ngay thì chỉ đổi \
                        câu thông báo chứ không chữa được cuộc đua đã chặn PR #76.""")
                .contains("sleep 30");
        assertThat(yml)
                .as(
                        """
                        Vòng chờ phải có CHỐT và hết chốt thì ĐỎ. Một cổng đề bạt bỏ qua vì \
                        "chờ mãi không thấy" đúng bằng không có cổng.""")
                .contains("Sau 10 phút vẫn còn phép kiểm");
    }

    // -------------------------------------------------------------------------

    private record KetQua(int maThoat, String dauRa) {}

    private static KetQua chay(String dauVao) throws Exception {
        // ⚠ `bash` tường minh, không dựa vào shebang: runner GitHub chạy bash, shell mặc định của máy
        //   dev là zsh, và zsh không tách từ như bash (luật 20).
        ProcessBuilder pb = new ProcessBuilder("bash", SCRIPT.toString(), "abc1234");
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH"));
        pb.environment().put("LC_ALL", "C.UTF-8");

        Process p = pb.start();
        p.getOutputStream().write(dauVao.getBytes(StandardCharsets.UTF_8));
        p.getOutputStream().close();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
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
