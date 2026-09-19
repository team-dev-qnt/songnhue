package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    @DisplayName("⛔⛔ Không có check nào ⇒ mã 2 (CHỜ) — ⛔ bao giờ mã 0, và ⛔ phải mã 1 (T63.16)")
    void tapRongPhaiCho() throws Exception {
        KetQua kq = chay("");

        // ⛔⛔ Vế BẤT BIẾN, ⛔ được nới đi: tập rỗng ⛔ BAO GIỜ là ĐẠT (luật 7). Một commit chưa
        //    ai kiểm mà đi lọt cổng đề bạt là thứ tệ nhất tệp này có thể gây ra.
        assertThat(kq.maThoat())
                .as("⛔ Đầu vào rỗng cho ra mã 0 ⇒ commit chưa từng chạy CI vẫn được đề bạt")
                .isNotZero();

        // ⭐ Vế ĐỔI ngày 17/09: rỗng nghĩa là CHƯA BIẾT, ⛔ phải BIẾT-LÀ-HỎNG.
        //
        //   Đo được: 12:50:35 PR #152 gộp vào `dev` → 12:50:57 `Promotion guard` của PR đề bạt
        //   #133 chạy (22 GIÂY sau) → `dev@0c01b26` chưa kịp có check-run nào → cổng ĐỎ và PR đề
        //   bạt BLOCKED; đo lại ngay sau đó thì CI của chính commit ấy đang `in_progress`.
        //
        //   ⇒ Mã 2 đẩy quyết định sang vòng chờ của `promotion-guard.yml` (20 lần × 30s), và
        //   HẾT vòng ấy mà vẫn rỗng thì workflow đỏ — xem {@link #hetVongChoThiDo()}. Luật 7 giữ
        //   nguyên nhờ vế `isNotZero()` ở trên.
        assertThat(kq.maThoat()).as("%s", kq.dauRa()).isEqualTo(2);
        assertThat(kq.dauRa())
                .as("Thông điệp phải nói CHƯA BIẾT chứ ⛔ khẳng định commit chưa từng chạy CI — "
                        + "hai trạng thái ấy dẫn tới hai việc khác hẳn nhau (đợi vs đi tìm nguyên nhân)")
                .contains("chưa có check-run nào")
                .doesNotContain("chưa từng chạy qua pipeline");
    }

    @Test
    @DisplayName("⭐⭐ HẾT vòng chờ mà vẫn chưa biết ⇒ workflow ĐỎ, và nói ra CẢ HAI khả năng")
    void hetVongChoThiDo() throws Exception {
        String wf = Files.readString(timTuGocKho(".github/workflows/promotion-guard.yml"), StandardCharsets.UTF_8);

        // ⛔ Vế này là thứ giữ cho bản vá T63.16 ⛔ biến cổng thành một cái cổng ⛔ bao giờ đỏ:
        //   mã 2 chỉ hoãn quyết định, và chỗ DUY NHẤT biến hoãn thành đỏ là dòng `exit 1` sau
        //   vòng lặp. Mất nó thì tập rỗng chờ 10 phút rồi... đi tiếp.
        String sauVongLap = wf.substring(wf.indexOf("Sau 10 phút"));
        assertThat(sauVongLap)
                .as("⛔ Hết vòng chờ mà workflow ⛔ `exit 1` ⇒ cổng đề bạt ⛔ chặn được gì nữa")
                .contains("exit 1");

        // ⚠ Và nó phải nói ra cả hai khả năng: từ T63.16, rỗng cũng rơi vào vòng chờ này, nên một
        //   thông điệp chỉ nhắc "CI còn đang chạy" sẽ dẫn người đọc đi chạy lại job mãi mãi trong
        //   khi nguyên nhân thật là `ci.yml` ⛔ kích hoạt trên `dev`.
        assertThat(sauVongLap)
                .as("Thông điệp hết-vòng-chờ chỉ nói MỘT khả năng ⇒ ca nguy hiểm hơn ⛔ ai đọc ra")
                .contains("còn đang chạy")
                .contains("CHƯA TỪNG chạy qua pipeline");
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
        // ⚠⚠ Vế "hết vòng chờ thì ĐỎ" TRƯỚC ĐÂY khẳng định một CÂU CHỮ —
        //    `contains("Sau 10 phút vẫn còn phép kiểm")`. Nó đỏ ở lượt vá T63.16 dù bất biến nó
        //    canh ⛔ hề đổi: tôi chỉ sửa thông điệp cho nói ra cả hai khả năng. Đúng hình dạng
        //    §11.16 — *một bộ canh hỏng vì mã được DỌN DẸP là một bộ canh đang canh văn bản*
        //    (luật 2). ⇒ Vế ấy chuyển sang {@link #hetVongChoThiDo()}, nơi nó khẳng định
        //    **cấu trúc**: đoạn SAU vòng lặp phải có `exit 1`. Chặt hơn, và ⛔ vỡ vì một lượt
        //    sửa câu chữ.
    }

    // =========================================================================
    //  18/09/2026 · Nguồn = `dev` cho CẢ HAI đích, và hỏi CI của `head.sha`
    //
    //  Luồng đơn giản hoá: ⛔ còn thứ tự `staging` trước `production`. Cả hai đích nhận PR từ `dev`,
    //  nên head của PR LUÔN là đỉnh `dev` — commit mang check-run của lượt push `dev`. Bản T11.86
    //  (06/09) phải giải cây tệp vì PR `staging → production` có head là merge commit trên `staging`,
    //  thứ chưa từng chạy CI (PR #94 — cổng "về nguyên tắc không thể xanh"). Nguồn ấy nay bị chặn ngay
    //  bước đầu, nên phép giải thành thừa — và giữ thứ thừa là giữ một chỗ có thể hỏng.
    //
    //  ⚠ Cặp bất biến phải đi CÙNG nhau: nếu bước kiểm nguồn lại nhận `staging` cho `production` mà
    //  bước hỏi CI vẫn dùng `head.sha`, thì T11.86 quay về nguyên vẹn. Hai bài dưới canh hai nửa.
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Nguồn của PR vào `staging` VÀ `production` đều phải là `dev`")
    void nguonPhaiLaDevChoCaHaiDich() {
        String buoc = buocKiemNguon(doc(WORKFLOW));

        assertThat(buoc)
                .as(
                        """
                        Bước kiểm nguồn không ép `dev` cho cả hai đích.

                        Bước hỏi CI dùng thẳng `head.sha` — chỉ đúng khi head là đỉnh `dev`. Nhận PR \
                        `staging → production` là dựng lại cổng "không thể xanh" của PR #94 (T11.86).""")
                .containsPattern("staging\\s*\\|\\s*production\\)\\s*expected=\"dev\"")
                .doesNotContain("expected=\"staging\"");
    }

    @Test
    @DisplayName("⭐⭐ Hỏi CI của `pull_request.head.sha` — ⛔ còn bước giải cây tệp")
    void hoiCiCuaHeadSha() {
        String yml = doc(WORKFLOW);

        assertThat(envBuocHoiCi(yml))
                .as("Bước hỏi check-run không lấy `pull_request.head.sha` — nó đang hỏi SHA nào?")
                .contains("SHA: ${{ github.event.pull_request.head.sha }}");
        assertThat(yml)
                .as(
                        """
                        `promotion-guard.yml` vẫn gọi `giai-dinh-dev.sh`. Từ 18/09 nguồn luôn là `dev` \
                        nên head đã là SHA mang CI; giải thêm một lần là thêm một đường trả mã 2 \
                        (chưa fetch `dev`) khoá cứng nhánh đích.""")
                .doesNotContain("giai-dinh-dev.sh");
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM: phép trích `env:` phải phân biệt được bản thật với một bản YAML giả")
    void tuKiemTrichEnvBuocHoiCi() {
        // Luật 29: nếu phép trích bắt hụt khối `env:` thì bài trên xanh/đỏ trên một chuỗi rỗng (luật 7).
        String gia =
                """
                      - name: Commit này đã xanh CI ở chặng trước chưa
                        env:
                          GH_TOKEN: ${{ github.token }}
                          SHA: ${{ steps.nguon.outputs.sha_ci }}
                          REPO: ${{ github.repository }}
                        run: |
                          echo x
                """;

        assertThat(envBuocHoiCi(gia))
                .as("Phép trích không thấy SHA giải sẵn trong một bản giả CÓ nó — nó đang soi nhầm chỗ")
                .contains("steps.nguon.outputs.sha_ci")
                .doesNotContain("pull_request.head.sha");
        assertThat(envBuocHoiCi(doc(WORKFLOW)))
                .as("Phép trích trả về rỗng trên bản thật — bài `hoiCiCuaHeadSha` sẽ đo trên tập rỗng")
                .isNotBlank();
        assertThat(buocKiemNguon(doc(WORKFLOW)))
                .as("Phép trích bước kiểm nguồn trả rỗng — bài `nguonPhaiLaDevChoCaHaiDich` đo trên tập rỗng")
                .contains("case \"$target\" in");
    }

    /** Trích khối {@code env:} của riêng bước "Commit này đã xanh CI…" (tới {@code run: |}). */
    private static String envBuocHoiCi(String yml) {
        int dau = yml.indexOf("Commit này đã xanh CI");
        if (dau < 0) {
            return "";
        }
        int cuoi = yml.indexOf("run: |", dau);
        return cuoi < 0 ? yml.substring(dau) : yml.substring(dau, cuoi);
    }

    /** Trích thân bước "Nhánh nguồn phải đúng chặng trước" (tới bước kế tiếp). */
    private static String buocKiemNguon(String yml) {
        int dau = yml.indexOf("Nhánh nguồn phải đúng chặng trước");
        if (dau < 0) {
            return "";
        }
        int cuoi = yml.indexOf("- uses:", dau);
        return cuoi < 0 ? yml.substring(dau) : yml.substring(dau, cuoi);
    }

    @Test
    @DisplayName("⭐⭐ T11.79 · Bộ lọc phải soi `Cổng kiểm CI` — không thì nó chỉ nhìn 2/9 check")
    void boLocPhaiSoiCongKiemCi() {
        String yml = doc(WORKFLOW);
        String tenCong = tenJobTrongCi("cong-kiem");

        assertThat(locCheckRun(yml))
                .as(
                        """
                        Đo 04/09: bộ lọc chỉ khớp `Backend*` và `Frontend*` — **2 trên 9** check-run \
                        của một lượt push vào `dev`. Lượt đề bạt thật gần nhất (run 33834838646) in \
                        ra đúng hai dòng, nên đây là số đo chứ không phải suy từ regex.

                        Bảy cái vô hình gồm `Thứ tự migration` (lớp lỗi đã giết hai lượt CD ngày \
                        27/8, và bộ test VỀ NGUYÊN TẮC không thấy — luật 30) và `Gắn tag SHA cho \
                        image không đổi` (đỏ ⇒ tag `:<sha>` có thể không tồn tại, mà deploy kéo \
                        image THEO ĐÚNG SHA đó).

                        Cổng đề bạt tự mô tả là "commit này đã xanh CI ở chặng trước chưa" — một \
                        bộ canh hẹp hơn nơi nó phải chặn, mà cái xanh của nó đọc như lời bảo đảm \
                        (luật 28).""")
                .contains(tenCong);
    }

    @Test
    @DisplayName("⛔ Không nới thành \"mọi check-run\" — lượt quét CVE cố ý đứng ngoài luồng chặn")
    void khongDuocNuocThanhMoiCheck() {
        String loc = locCheckRun(doc(WORKFLOW));

        assertThat(loc)
                .as(
                        """
                        `security-scan.yml` viết ngay ở đầu tệp rằng nhịp của việc quét CVE KHÔNG \
                        phải nhịp của PR, và nó chạy trên push vào `dev` mỗi khi `pom.xml` đổi. \
                        Bỏ `select` đi là biến một quyết định đã cân nhắc thành tác dụng phụ: \
                        7 mã ≥ 7 của T11.69 sẽ chặn cứng mọi lượt đề bạt cho tới 15/10.""")
                .contains("select(");
        assertThat(loc).doesNotContain("OWASP").doesNotContain("npm audit");
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM: bỏ tên cổng khỏi bộ lọc thì phép so PHẢI bắt")
    void tuKiemBoLoc() {
        String tenCong = tenJobTrongCi("cong-kiem");
        String locGia = "'.check_runs[] | select(.name | startswith(\"Backend\")) | \"x\"'";

        // Chính phép so của bài trên, chạy trên một bộ lọc GIẢ đã bỏ tên cổng.
        assertThat(locGia)
                .as("Bộ lọc giả này thiếu tên cổng, nên phép so phải phân biệt được nó với bản thật")
                .doesNotContain(tenCong);
        assertThat(locCheckRun(doc(WORKFLOW)))
                .as("Đối chứng phải-tìm-thấy: bản THẬT có tên cổng (luật 7 — phân biệt hai trạng thái)")
                .contains(tenCong);
    }

    /**
     * Tên hiển thị của một job trong {@code ci.yml}, đọc từ chính tệp ấy.
     *
     * <p>⭐ Không viết cứng chuỗi {@code "Cổng kiểm CI"} vào bài kiểm: đổi tên job ở
     * {@code ci.yml} mà quên sửa {@code promotion-guard.yml} là đúng hình dạng luật 14 — hai chỗ
     * một con người phải nhớ. Đọc một đầu và so với đầu kia thì bài kiểm nhớ hộ.
     */
    private static String tenJobTrongCi(String khoaJob) {
        String ci = doc(timTuGocKho(".github/workflows/ci.yml"));
        Matcher m = Pattern.compile("(?m)^  " + Pattern.quote(khoaJob) + ":\\n    name: (.+)$")
                .matcher(ci);
        return m.find() ? m.group(1).strip() : fail("Không đọc được tên job `%s` từ ci.yml".formatted(khoaJob));
    }

    /** Dòng {@code --jq} lọc check-run trong {@code promotion-guard.yml}. */
    private static String locCheckRun(String yml) {
        Matcher m = Pattern.compile("(?m)^\\s*'\\.check_runs\\[\\].*$").matcher(yml);
        return m.find() ? m.group() : fail("Không tìm thấy dòng lọc `.check_runs[]` trong promotion-guard.yml");
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
