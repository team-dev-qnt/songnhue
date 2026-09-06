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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>{@code giai-dinh-dev.sh} — giải "commit nào trên {@code dev} đã dựng ra mã đang ở đây" theo CÂY TỆP.</b>
 *
 * <h2>Vì sao phép giải này tồn tại</h2>
 *
 * Image GHCR chỉ được đóng gói ở {@code dev} và gắn tag theo SHA của {@code dev}. Mọi chặng sau
 * ({@code staging}, {@code production}) vì thế phải quy về một SHA của {@code dev} trước khi tra image
 * hoặc hỏi kết quả CI. Quan hệ cha–con KHÔNG trả lời được câu ấy sau một lượt squash (§10.42) — cây tệp
 * thì có.
 *
 * <h2>Vì sao nó được tách ra một tệp riêng (06/09/2026, T11.86)</h2>
 *
 * Từ hôm nay có <b>ba</b> nơi cần đúng phép giải này: {@code deploy-staging.yml} và {@code deploy-prod.yml}
 * (tra image), và {@code promotion-guard.yml} — nơi PR {@code staging → production} phải hỏi check-run của
 * commit {@code dev} tương ứng, vì đỉnh {@code staging} là một merge commit <b>chưa bao giờ chạy CI</b>.
 * Ba bản chép tay là ba nơi phải nhớ (luật 14).
 *
 * <h2>Vì sao bài kiểm dựng kho git THẬT</h2>
 *
 * Soi văn bản chỉ chứng minh script có mặt. Luật 1 đòi bằng chứng nó <b>bắt được vi phạm</b>; luật 9 đòi nó
 * <b>phân biệt được các trạng thái</b> — một script trả về cùng một thứ với mọi đầu vào cũng "chạy được".
 * Nên mỗi bài dưới đây dựng một kho trong {@code @TempDir}, tái hiện đúng hình dạng merge / squash / lệch
 * cây, rồi đọc <b>mã thoát thật</b> và <b>hai luồng tách rời</b>.
 *
 * <p>⚠ Tách stdout khỏi stderr là bắt buộc ở đây, khác {@code PromotionAncestryTest} (gộp làm một): cả hợp
 * đồng của script nằm ở chỗ <b>stdout chỉ có SHA</b>, để nơi gọi đổ thẳng vào {@code $GITHUB_OUTPUT}. Gộp
 * hai luồng là xoá mất chính thứ cần khẳng định.
 */
class GiaiDinhDevTest {

    private static final Path SCRIPT = timTuGocKho(".github/scripts/giai-dinh-dev.sh");
    private static final Path WF_STAGING = timTuGocKho(".github/workflows/deploy-staging.yml");
    private static final Path WF_PROD = timTuGocKho(".github/workflows/deploy-prod.yml");
    private static final Path WF_GUARD = timTuGocKho(".github/workflows/promotion-guard.yml");

    // =========================================================================
    //  Đường đi đúng
    // =========================================================================

    @Test
    @DisplayName("⭐ Đề bạt bằng MERGE COMMIT — giải đúng đỉnh dev qua đường nhanh merge-base")
    void giaiDuocQuaMergeCommit(@TempDir Path kho) throws Exception {
        dungKhoDeBatDungCach(kho);
        String dinhDev = git(kho, "rev-parse", "dev").trim();

        KetQua kq = chay(kho, "staging");

        assertThat(kq.maThoat())
                .as("Không giải được một lượt đề bạt hợp lệ:%n%s", kq.loi())
                .isZero();
        // ⚠ So với giá trị ĐO TỪ GIT, không với một hằng chuỗi chép tay — hằng chuỗi sẽ mục theo dữ liệu.
        assertThat(kq.ra().strip()).isEqualTo(dinhDev);
        assertThat(kq.loi()).contains("merge-base");
    }

    @Test
    @DisplayName("⭐⭐ stdout CHỈ có SHA — đây là hợp đồng với `$GITHUB_OUTPUT`")
    void stdoutChiCoSha(@TempDir Path kho) throws Exception {
        // Nơi gọi làm `dev_tip="$(bash giai-dinh-dev.sh …)"` rồi ghi thẳng vào `$GITHUB_OUTPUT`.
        // Lọt một dòng chẩn đoán nào vào stdout là ghi một giá trị rác vào output của job, và thân
        // chung sẽ đi tra một tag image không tồn tại — 404 đổ lỗi sai chỗ (§10.43).
        dungKhoDeBatDungCach(kho);

        KetQua kq = chay(kho, "staging");

        assertThat(kq.ra())
                .as("stdout không phải ĐÚNG một dòng 40 ký tự hex — nơi gọi sẽ ghi rác vào $GITHUB_OUTPUT")
                .matches("^[0-9a-f]{40}\\n$");
        assertThat(kq.ra()).doesNotContain("::").doesNotContain("Đỉnh dev");
    }

    @Test
    @DisplayName("⭐ Đề bạt bị SQUASH — vẫn giải được bằng cây tệp, VÀ phải kêu chuông")
    void squashVanGiaiDuocVaKeuChuong(@TempDir Path kho) throws Exception {
        dungKhoCoSquash(kho);
        String dinhDev = git(kho, "rev-parse", "dev").trim();

        KetQua kq = chay(kho, "staging");

        assertThat(kq.maThoat())
                .as("Squash làm script chết — đúng lỗi §10.42 tái phát:%n%s", kq.loi())
                .isZero();
        assertThat(kq.ra().strip()).isEqualTo(dinhDev);
        // Sống sót qua sự cố mà không dời chuông sang chỗ khác là GỠ MẤT chuông (§10.72).
        assertThat(kq.loi()).contains("::warning::").contains("squash");
    }

    @Test
    @DisplayName("⭐ Một commit NẰM TRÊN dev thì giải ra chính nó")
    void refTrenDevTraVeChinhNo(@TempDir Path kho) throws Exception {
        dungKhoDeBatDungCach(kho);
        String dinhDev = git(kho, "rev-parse", "dev").trim();

        KetQua kq = chay(kho, dinhDev);

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.ra().strip()).isEqualTo(dinhDev);
    }

    // =========================================================================
    //  Phản chứng — script phải BIẾT NÓI KHÔNG, và nói khác nhau tuỳ lý do
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Không cây nào khớp → mã 1, stdout RỖNG (phản chứng bắt buộc)")
    void khongCayNaoKhopThiDo(@TempDir Path kho) throws Exception {
        dungKhoLechCay(kho);

        // Tiền đề tự đo: nếu dev THẬT SỰ có cây ấy thì bài này vô nghĩa (nó sẽ xanh vì lý do sai).
        String cayStaging = git(kho, "rev-parse", "staging^{tree}").trim();
        assertThat(git(kho, "log", "--format=%T", "dev")
                        .lines()
                        .map(String::strip)
                        .toList())
                .as("Dữ liệu dựng sai — dev đang CÓ cây của staging, nên 'không khớp' là kết luận sai")
                .doesNotContain(cayStaging);

        KetQua kq = chay(kho, "staging");

        assertThat(kq.maThoat())
                .as("Script XANH trên một nhánh mang mã chưa từng dựng image — sẽ deploy nhầm image:%n%s", kq.loi())
                .isEqualTo(1);
        assertThat(kq.ra())
                .as("Thất bại mà vẫn in ra stdout — nơi gọi sẽ tưởng có kết quả")
                .isEmpty();
        assertThat(kq.loi()).contains("::error::");
    }

    @Test
    @DisplayName("⭐⭐ Chưa fetch dev → mã 2, KHÁC hẳn mã 1 (luật 9)")
    void chuaFetchDevThiKeuKhongTraDuoc(@TempDir Path kho) throws Exception {
        // "Không tra được" và "đã tra, không có" là hai trạng thái khác nhau, và chúng đòi hai hành động
        // khác nhau: một bên thêm bước `git fetch`, một bên mở lại PR đề bạt. Khối inline cũ để git tự
        // chết bằng `fatal: ambiguous argument` và người đọc đi tìm lỗi cây tệp.
        dungKhoDeBatDungCach(kho);

        // Kho tạm không có remote nào ⇒ `origin/dev` không giải được. Đây là mô phỏng chính xác việc
        // quên bước `git fetch` trong workflow.
        KetQua kq = chayVoiMoiTruong(kho, List.of("staging", "500", "origin/dev"), Map.of());

        assertThat(kq.maThoat())
                .as("Thiếu nhánh dev mà không trả mã 2 — người đọc sẽ đi chữa nhầm chỗ:%n%s", kq.loi())
                .isEqualTo(2);
        assertThat(kq.ra()).isEmpty();
        assertThat(kq.loi()).contains("git fetch");
    }

    @Test
    @DisplayName("⭐⭐ Cửa sổ quét là tham số THẬT SỰ — hẹp thì đỏ, đủ rộng thì xanh")
    void cuaSoQuetLaThamSoThatSu(@TempDir Path kho) throws Exception {
        // Không có bài này thì `so_commit_quet` có thể là một số trang trí mà script bỏ qua, và ta sẽ
        // tin rằng đã nâng cửa sổ cho `production` (tụt xa hơn `staging`) trong khi chưa nâng gì.
        int sau = dungKhoCayNamSau(kho, 12, 10);

        assertThat(chay(kho, "staging", "5").maThoat())
                .as("Cửa sổ 5 mà vẫn tìm ra cây nằm ở độ sâu %d — tham số đang bị bỏ qua", sau)
                .isEqualTo(1);

        KetQua rong = chay(kho, "staging", "12");
        assertThat(rong.maThoat())
                .as("Cửa sổ 12 vẫn không thấy cây ở độ sâu %d:%n%s", sau, rong.loi())
                .isZero();
        assertThat(rong.ra().strip())
                .isEqualTo(git(kho, "rev-parse", "dev~" + sau).trim());
    }

    @Test
    @DisplayName("⛔ Cửa sổ 0 → mã 2, không phải mã 1 — chặn phép quét trên tập rỗng")
    void cuaSoRongThiKeuSaiThamSo(@TempDir Path kho) throws Exception {
        // Luật 7: một phép kiểm chạy qua tập rỗng vẫn xanh trọn vẹn. Ở đây nó sẽ "đỏ trọn vẹn" với lý do
        // sai — và mã 1 đọc như "đã quét, không có".
        dungKhoDeBatDungCach(kho);

        assertThat(chay(kho, "staging", "0").maThoat()).isEqualTo(2);
        assertThat(chay(kho, "staging", "khong-phai-so").maThoat()).isEqualTo(2);
    }

    @Test
    @DisplayName("⭐ Hai commit cùng cây thì lấy cái MỚI NHẤT")
    void haiCommitCungCayThiLayCaiMoiNhat(@TempDir Path kho) throws Exception {
        // Ghim ngữ nghĩa "khớp đầu tiên thắng" của `git log` (mới → cũ). Bản `awk` không được `exit`
        // sớm (SIGPIPE giết script dưới `pipefail`), nên nó dùng cờ — cờ ấy phải giữ đúng ngữ nghĩa này.
        int sau = dungKhoCoCayLapLai(kho);
        String moiNhat = git(kho, "rev-parse", "dev~" + sau).trim();

        KetQua kq = chay(kho, "staging", "50");

        assertThat(kq.maThoat()).as("%s", kq.loi()).isZero();
        assertThat(kq.ra().strip())
                .as("Lấy nhầm bản CŨ trong hai commit cùng cây — image sẽ là bản cũ hơn thứ đang chạy")
                .isEqualTo(moiNhat);
    }

    // =========================================================================
    //  Tác dụng phụ: chỉ ghi Job Summary khi runner có yêu cầu
    // =========================================================================

    @Test
    @DisplayName("⛔ `$GITHUB_STEP_SUMMARY` — không đặt thì KHÔNG sinh tệp nào; có đặt thì phải ghi đủ")
    void tomTatChiGhiKhiCoBien(@TempDir Path kho) throws Exception {
        dungKhoCoSquash(kho);
        Path tomTat = kho.resolve("tom-tat.md");

        // (a) không đặt biến → chạy được ở máy, không rải tệp
        assertThat(chay(kho, "staging").maThoat()).isZero();
        assertThat(Files.exists(tomTat)).isFalse();

        // (b) có đặt + đường squash → phải có CẢ khối cảnh báo LẪN dòng kết luận
        KetQua kq = chayVoiMoiTruong(
                kho, List.of("staging", "500", "dev"), Map.of("GITHUB_STEP_SUMMARY", tomTat.toString()));
        assertThat(kq.maThoat()).as("%s", kq.loi()).isZero();
        assertThat(Files.readString(tomTat, StandardCharsets.UTF_8))
                .contains("Lượt đề bạt này đã bị SQUASH")
                .contains("Đỉnh dev đang đề bạt");

        // (c) đường nhanh (không squash) → có kết luận, KHÔNG có khối cảnh báo
        Path kho2 = kho.resolveSibling("kho-merge-" + System.nanoTime());
        Files.createDirectories(kho2);
        dungKhoDeBatDungCach(kho2);
        Path tomTat2 = kho2.resolve("tom-tat.md");
        chayVoiMoiTruong(kho2, List.of("staging", "500", "dev"), Map.of("GITHUB_STEP_SUMMARY", tomTat2.toString()));
        assertThat(Files.readString(tomTat2, StandardCharsets.UTF_8))
                .contains("Đỉnh dev đang đề bạt")
                .doesNotContain("đã bị SQUASH");
    }

    // =========================================================================
    //  Tự kiểm: đúng hình dạng đã chặn PR #94, khẳng định bằng GIT chứ không bằng lời script
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ TỰ KIỂM · hình dạng PR #94 — đỉnh staging KHÔNG nằm trên dev, thứ script trả về thì CÓ")
    void hinhDangPR94(@TempDir Path kho) throws Exception {
        // Đây là bài không chia sẻ giả định nào với script: nó không đọc một chữ nào trong thông báo của
        // script, chỉ hỏi git hai câu. Luật 29 — người viết bộ canh và người viết phản chứng là cùng một
        // người, nên phản chứng phải neo vào một nguồn sự thật khác.
        dungKhoDeBatDungCach(kho);
        git(kho, "branch", "production", "HEAD~1"); // production tụt lại, là tổ tiên của staging
        String dinhStaging = git(kho, "rev-parse", "staging").trim();

        // ① Vì sao cổng cũ KHÔNG THỂ xanh: SHA mà nó đem đi hỏi check-run chưa từng nằm trên `dev`,
        //    mà CI chỉ chạy ở `dev` ⇒ commit ấy không có, và không thể có, một check-run nào.
        assertThat(maThoatGit(kho, "merge-base", "--is-ancestor", dinhStaging, "dev"))
                .as("Đỉnh staging lại là tổ tiên của dev — kho dựng sai, không tái hiện được #94")
                .isNotZero();

        // ② Vì sao bản vá đúng: thứ script trả về LÀ một commit trên `dev`, nên nó có check-run.
        KetQua kq = chay(kho, dinhStaging);
        assertThat(kq.maThoat()).as("%s", kq.loi()).isZero();
        assertThat(maThoatGit(kho, "merge-base", "--is-ancestor", kq.ra().strip(), "dev"))
                .as("Script trả về một commit KHÔNG nằm trên dev — nó cũng sẽ không có check-run nào")
                .isZero();
    }

    // =========================================================================
    //  Đường dây: script có thật sự được gọi, và không còn bản chép nào trôi đi
    // =========================================================================

    @Test
    @DisplayName("⛔ Script phải có quyền chạy")
    void scriptPhaiCoQuyenChay() {
        // Tệp mới tạo mặc định 100644. Ba workflow gọi bằng `bash <script>` nên vẫn chạy được, nhưng để
        // mất bit exec là để lại một cái bẫy cho lần đầu ai đó gọi trực tiếp.
        assertThat(Files.isExecutable(SCRIPT))
                .as("%s không có quyền chạy", SCRIPT)
                .isTrue();
    }

    @Test
    @DisplayName("⭐ Cả BA workflow phải gọi script, và `deploy-staging.yml` không còn giữ bản chép")
    void baWorkflowPhaiGoiVaKhongConBanChep() {
        for (Path wf : List.of(WF_STAGING, WF_PROD, WF_GUARD)) {
            assertThat(doc(wf))
                    .as(
                            """
                            %s không gọi `giai-dinh-dev.sh`.

                            Ba nơi cần đúng phép giải theo cây tệp: hai workflow triển khai (tra image) và \
                            cổng đề bạt (hỏi check-run của commit `dev` tương ứng). Một nơi tự chép lại là \
                            một nơi sẽ trôi khỏi hai nơi kia (luật 14).""",
                            wf.getFileName())
                    .contains("giai-dinh-dev.sh");
        }

        assertThat(doc(WF_STAGING))
                .as("`deploy-staging.yml` vẫn giữ vòng quét cũ — hai bản mã cho cùng một luật")
                .doesNotContain("rev-list -n 200");
    }

    // =========================================================================
    //  Dựng kho
    // =========================================================================

    /** {@code staging} nhận {@code dev} qua một merge commit hai cha — luồng đúng. */
    private static void dungKhoDeBatDungCach(Path kho) throws Exception {
        khoiTao(kho);
        git(kho, "branch", "staging");
        Files.writeString(kho.resolve("f.txt"), "a\nb\n");
        git(kho, "commit", "-qam", "tinh nang");

        git(kho, "checkout", "-q", "staging");
        git(kho, "merge", "-q", "--no-ff", "dev", "-m", "Merge pull request from team-dev-qnt/dev");
        git(kho, "checkout", "-q", "dev");

        assertThat(soCha(kho, "staging"))
                .as("Kho dựng sai — đỉnh staging phải có hai cha")
                .isEqualTo(2);
    }

    /** {@code staging} nhận nội dung của {@code dev} qua một commit MỘT CHA — hình dạng squash. */
    private static void dungKhoCoSquash(Path kho) throws Exception {
        khoiTao(kho);
        git(kho, "branch", "staging");
        Files.writeString(kho.resolve("f.txt"), "a\nb\n");
        git(kho, "commit", "-qam", "tinh nang");

        git(kho, "checkout", "-q", "staging");
        git(kho, "checkout", "-q", "dev", "--", "f.txt");
        git(kho, "commit", "-qm", "tinh nang (#72)");
        git(kho, "checkout", "-q", "dev");

        assertThat(soCha(kho, "staging"))
                .as("Kho dựng sai — squash phải cho commit MỘT cha")
                .isEqualTo(1);
    }

    /** {@code staging} mang một cây mà {@code dev} chưa bao giờ có. */
    private static void dungKhoLechCay(Path kho) throws Exception {
        dungKhoCoSquash(kho);
        git(kho, "checkout", "-q", "staging");
        Files.writeString(kho.resolve("f.txt"), "co nguoi day thang vao staging\n");
        git(kho, "commit", "-qam", "sua tay tren staging");
        git(kho, "checkout", "-q", "dev");
    }

    /**
     * {@code dev} có {@code soCommit} commit; {@code staging} là một nhánh MỒ CÔI mang đúng cây của
     * {@code dev~sau}. Mồ côi ⇒ không có gốc chung ⇒ đường nhanh trượt ⇒ buộc phải đi đường quét.
     *
     * @return độ sâu của cây cần tìm, tính từ đỉnh {@code dev}
     */
    private static int dungKhoCayNamSau(Path kho, int soCommit, int sau) throws Exception {
        khoiTao(kho);
        for (int i = 1; i < soCommit; i++) {
            Files.writeString(kho.resolve("f.txt"), "noi dung " + i + "\n");
            git(kho, "commit", "-qam", "commit " + i);
        }
        String moc = git(kho, "rev-parse", "dev~" + sau).trim();

        git(kho, "checkout", "-q", "--orphan", "staging");
        git(kho, "reset", "-q", "--hard", moc);
        git(kho, "commit", "-q", "--allow-empty", "--amend", "-m", "nhanh mo coi cung cay voi dev~" + sau);
        git(kho, "checkout", "-q", "dev");

        assertThat(git(kho, "rev-parse", "staging^{tree}").trim())
                .as("Kho dựng sai — staging phải trùng cây với dev~%d", sau)
                .isEqualTo(git(kho, "rev-parse", moc + "^{tree}").trim());
        return sau;
    }

    /** Hai commit trên {@code dev} cùng một cây (một lượt revert), {@code staging} mồ côi mang cây ấy. */
    private static int dungKhoCoCayLapLai(Path kho) throws Exception {
        khoiTao(kho);
        Files.writeString(kho.resolve("f.txt"), "ban A\n");
        git(kho, "commit", "-qam", "doi sang A"); // dev~1 mang cây A
        Files.writeString(kho.resolve("f.txt"), "ban B\n");
        git(kho, "commit", "-qam", "doi sang B");
        Files.writeString(kho.resolve("f.txt"), "ban A\n");
        git(kho, "commit", "-qam", "revert ve A"); // dev~0 mang LẠI cây A

        String cayA = git(kho, "rev-parse", "dev^{tree}").trim();
        assertThat(git(kho, "rev-parse", "dev~2^{tree}").trim())
                .as("Kho dựng sai — cần đúng hai commit cùng cây")
                .isEqualTo(cayA);

        git(kho, "checkout", "-q", "--orphan", "staging");
        git(kho, "reset", "-q", "--hard", "dev");
        git(kho, "commit", "-q", "--allow-empty", "--amend", "-m", "mo coi mang cay A");
        git(kho, "checkout", "-q", "dev");
        return 0; // cái MỚI NHẤT mang cây A là chính đỉnh dev
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

    // =========================================================================

    /** Hai luồng TÁCH RỜI — gộp lại là xoá mất chính thứ cần khẳng định. */
    private record KetQua(int maThoat, String ra, String loi) {}

    /**
     * Gọi script trên kho tạm. Kho tạm có nhánh {@code dev} <b>cục bộ</b>, không có remote nào — nên phải
     * truyền {@code ref_dev} tường minh; để script rơi về mặc định {@code origin/dev} là nhận mã 2 ở mọi
     * bài, kể cả những bài lẽ ra phải xanh.
     */
    private static KetQua chay(Path kho, String ref) throws Exception {
        return chayVoiMoiTruong(kho, List.of(ref, "500", "dev"), Map.of());
    }

    private static KetQua chay(Path kho, String ref, String cuaSo) throws Exception {
        return chayVoiMoiTruong(kho, List.of(ref, cuaSo, "dev"), Map.of());
    }

    private static KetQua chayVoiMoiTruong(Path kho, List<String> doiSo, Map<String, String> them) throws Exception {
        List<String> lenh = new ArrayList<>();
        // ⚠ `bash` tường minh, không dựa vào shebang: runner chạy bash còn shell mặc định của máy dev là
        //   zsh, và zsh không tách từ như bash (luật 20).
        lenh.add("bash");
        lenh.add(SCRIPT.toString());
        lenh.addAll(doiSo);

        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.directory(kho.toFile());
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH"));
        pb.environment().put("HOME", kho.toString());
        pb.environment().put("LC_ALL", "C.UTF-8");
        pb.environment().putAll(them);

        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String loi = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Lệnh %s không kết thúc trong 60 giây".formatted(lenh));
        }
        return new KetQua(p.exitValue(), ra, loi);
    }

    private static String git(Path kho, String... doiSo) throws Exception {
        List<String> lenh = new ArrayList<>();
        lenh.add("git");
        lenh.addAll(List.of(doiSo));
        Process p = khoiChayGit(kho, lenh);
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(60, TimeUnit.SECONDS);
        if (p.exitValue() != 0) {
            return fail("Lệnh %s hỏng (mã %d):%n%s".formatted(lenh, p.exitValue(), ra));
        }
        return ra;
    }

    /** Chạy git chỉ để lấy MÃ THOÁT — dùng cho những câu hỏi mà "hỏng" chính là câu trả lời. */
    private static int maThoatGit(Path kho, String... doiSo) throws Exception {
        List<String> lenh = new ArrayList<>();
        lenh.add("git");
        lenh.addAll(List.of(doiSo));
        Process p = khoiChayGit(kho, lenh);
        p.getInputStream().readAllBytes();
        p.waitFor(60, TimeUnit.SECONDS);
        return p.exitValue();
    }

    private static Process khoiChayGit(Path kho, List<String> lenh) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.directory(kho.toFile());
        pb.redirectErrorStream(true);
        // Môi trường sạch: một `GIT_DIR` tình cờ có sẵn sẽ lái mọi lệnh git ra khỏi kho tạm và làm nó
        // đọc chính kho của dự án.
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH"));
        pb.environment().put("HOME", kho.toString());
        pb.environment().put("LC_ALL", "C.UTF-8");
        return pb.start();
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
