package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Cổng secret của lượt triển khai — chạy THẬT script, không đọc chữ trong YAML.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Bước "Kiểm tra đã có cấu hình máy chủ chưa" cũ chỉ hỏi một biến, và thiếu thì in {@code ::warning::}
 * rồi đặt {@code ready=false}. Mọi bước sau mang {@code if: ready == 'true'} nên tự bỏ qua, và lượt
 * chạy kết thúc <b>xanh trọn vẹn</b>.
 *
 * <p>Đo bằng {@code gh api} ngày 26/8: environment {@code production} <b>không có secret nào</b>. Tức
 * là bấm "CD Production" hôm ấy sẽ cho ra một lượt chạy xanh, một dòng tóm tắt, và không một byte nào
 * chạm máy chủ — trong khi người bấm nút tin rằng đã deploy xong.
 *
 * <h2>Vì sao chạy script thay vì đọc YAML</h2>
 *
 * Một bài kiểm khẳng định "`deploy.yml` có chứa chuỗi `exit 1`" sẽ xanh với cả một script đúng lẫn một
 * script gọi {@code exit 1} ở nhánh không bao giờ tới. Nó không phân biệt được hai trạng thái, nên nó
 * không khẳng định gì (CLAUDE.md luật 9). Ở đây script được <b>gọi</b>, và thứ được khẳng định là mã
 * thoát cùng nội dung {@code $GITHUB_OUTPUT} — đúng hai thứ GitHub Actions thật sự đọc.
 */
class SecretGateTest {

    private static final String DU_BON = "co-gia-tri";

    /** Bộ NĂM secret hợp lệ — một chỗ duy nhất, để thêm secret thứ sáu chỉ phải sửa ở đây. */
    private static Map<String, String> duBo() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("HOST", DU_BON);
        m.put("USER", DU_BON);
        m.put("SSH_KEY", DU_BON);
        m.put("BASE_URL", "https://vi.du");
        m.put("SSH_KNOWN_HOSTS", "27.71.27.75 ssh-ed25519 AAAAC3Nza-gia-lap");
        return m;
    }

    @Test
    @DisplayName("⭐⭐ production + environment RỖNG → DỪNG ĐỎ, không phải bỏ qua trong im lặng")
    void productionRongThiDungDo() throws Exception {
        KetQua kq = chay("production", Map.of());

        assertThat(kq.maThoat())
                .as(
                        """
                        Cổng cho lượt CD Production đi tiếp dù không có secret nào.

                        Với `ready=false`, mọi bước sau tự bỏ qua và lượt chạy XANH — người bấm nút \
                        sẽ tin là đã deploy. Ra: %s""",
                        kq.dauRa())
                .isEqualTo(1);
        assertThat(kq.output())
                .as("Không được đặt `ready` khi đã dừng đỏ — hai tín hiệu trái nhau là một tín hiệu")
                .doesNotContain("ready=true");
        assertThat(kq.dauRa()).contains("::error::");
    }

    @Test
    @DisplayName("staging + environment RỖNG → bỏ qua kèm cảnh báo, KHÔNG đỏ")
    void stagingRongThiBoQua() throws Exception {
        // CD Staging chạy tự động sau mỗi lượt merge. Một môi trường chưa dựng mà nhuộm đỏ cả dòng CI
        // của mọi người là đổi một lỗi thật lấy một lỗi phiền — và rồi người ta tắt bớt cổng kiểm.
        KetQua kq = chay("staging", Map.of());

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.output()).contains("ready=false").doesNotContain("ready=true");
        assertThat(kq.dauRa()).contains("::warning::");
    }

    @Test
    @DisplayName("⭐ Đủ năm secret → đi tiếp, ở CẢ HAI môi trường")
    void duBonThiDiTiep() throws Exception {
        for (String moi : new String[] {"staging", "production"}) {
            KetQua kq = chay(moi, duBo());

            assertThat(kq.maThoat())
                    .as("`%s` có đủ năm secret mà cổng vẫn chặn. Ra: %s", moi, kq.dauRa())
                    .isZero();
            assertThat(kq.output()).contains("ready=true");
        }
    }

    @Test
    @DisplayName("⭐⭐ Thiếu MỘT SỐ → đỏ ở CẢ HAI môi trường — cấu hình dở dang không phải 'chưa dựng'")
    void thieuMotSoThiDoOCaHai() throws Exception {
        // Đây là trạng thái nguy hiểm nhất và cũng dễ tạo ra nhất: đặt xong 3 secret rồi bị gọi đi.
        // Bản cũ chỉ hỏi HOST, nên tổ hợp này đi lọt và hỏng ở `ssh` với "Permission denied" —
        // một thông báo không nhắc gì tới secret.
        Map<String, String> thieuSshKey = duBo();
        thieuSshKey.remove("SSH_KEY");

        for (String moi : new String[] {"staging", "production"}) {
            KetQua kq = chay(moi, thieuSshKey);

            assertThat(kq.maThoat())
                    .as("`%s` thiếu SSH_KEY mà cổng vẫn cho đi tiếp. Ra: %s", moi, kq.dauRa())
                    .isEqualTo(1);
            assertThat(kq.dauRa()).contains("SSH_KEY");
            assertThat(kq.output()).doesNotContain("ready=true");
        }
    }

    @Test
    @DisplayName(
            "⭐ Secret ĐẶT BẰNG CHUỖI RỖNG bị coi là thiếu — 'rỗng' khác 'chưa đặt' với con người, không khác với script")
    void chuoiRongCungLaThieu() throws Exception {
        // CLAUDE.md luật 3. Một secret gõ nhầm thành chuỗi rỗng tới đây giống hệt một secret không tồn
        // tại, và cả hai đều không dùng được. Nếu cổng chỉ hỏi "biến có được khai không" thì tổ hợp
        // này đi lọt.
        Map<String, String> rong = duBo();
        rong.put("SSH_KEY", "");

        KetQua kq = chay("production", rong);

        assertThat(kq.maThoat()).isEqualTo(1);
        assertThat(kq.dauRa()).contains("SSH_KEY");
    }

    @Test
    @DisplayName("⭐⭐ Thiếu RIÊNG `SSH_KNOWN_HOSTS` → đỏ, và phải GỌI TÊN nó")
    void thieuKhoaGhimThiDo() throws Exception {
        // Secret này vào bộ ngày 29/8 (§10.68-C). Trước đó bước "Mở đường SSH" tự dò khoá bằng
        // `ssh-keyscan`, và chính lượt dò ấy — 5 kết nối đóng trước xác thực — làm fail2ban của máy
        // chủ cấm IP runner ngay ở lệnh đầu tiên. Nay khoá phải ghim sẵn, nên thiếu nó thì KHÔNG nối
        // được; cổng phải chặn ở đây thay vì để hỏng ở `ssh` với một câu không nhắc gì tới secret.
        Map<String, String> thieu = duBo();
        thieu.remove("SSH_KNOWN_HOSTS");

        for (String moi : new String[] {"staging", "production"}) {
            KetQua kq = chay(moi, thieu);

            assertThat(kq.maThoat())
                    .as("`%s` thiếu SSH_KNOWN_HOSTS mà cổng vẫn cho đi tiếp. Ra: %s", moi, kq.dauRa())
                    .isEqualTo(1);
            assertThat(kq.dauRa())
                    .as("Đỏ mà không nói thiếu cái gì thì người gặp phải dò lại từ đầu")
                    .contains("SSH_KNOWN_HOSTS");
            assertThat(kq.output()).doesNotContain("ready=true");
        }
    }

    @Test
    @DisplayName("Thiếu MOI_TRUONG thì script tự dừng — không có mặc định im lặng")
    void thieuMoiTruongThiDung() throws Exception {
        KetQua kq = chay(null, duBo());

        assertThat(kq.maThoat()).isNotZero();
    }

    @Test
    @DisplayName("⭐ `deploy.yml` thật sự GỌI script này — cổng nằm ngoài đường chạy là cổng không có hiệu lực")
    void deployYmlPhaiGoiScript() {
        String workflow = doc(timTuGocKho(".github/workflows/deploy.yml"));

        assertThat(workflow)
                .as(
                        """
                        `deploy.yml` không gọi `kiem-secret-may-chu.sh`.

                        Sáu bài trên chứng minh script đúng; dòng này là thứ duy nhất chứng minh nó \
                        được CHẠY. Dự án đã có 5 cơ chế xanh mà không nằm trên đường chạy nào.""")
                .contains("kiem-secret-may-chu.sh");

        // Bốn biến phải được truyền vào — script không tự đọc được secret của GitHub.
        for (String ten : new String[] {"MOI_TRUONG:", "HOST:", "USER:", "SSH_KEY:", "BASE_URL:"}) {
            assertThat(workflow).as("Bước gọi cổng phải truyền `%s`", ten).contains(ten);
        }
    }

    // -------------------------------------------------------------------------

    private record KetQua(int maThoat, String dauRa, String output) {}

    private static KetQua chay(String moiTruong, Map<String, String> bien) throws Exception {
        Path script = timTuGocKho(".github/scripts/kiem-secret-may-chu.sh");
        Path output = Files.createTempFile("github-output", ".txt");

        // ⚠ `bash` tường minh, không dựa vào shebang: runner của GitHub chạy bash, còn shell mặc
        //   định của máy dev là zsh — và zsh không tách từ như bash (CLAUDE.md luật 20).
        ProcessBuilder pb = new ProcessBuilder("bash", script.toString());
        pb.redirectErrorStream(true);
        // Môi trường SẠCH: kế thừa môi trường của JVM là để một biến HOST/USER tình cờ có sẵn trên
        // máy dev quyết định kết quả bài kiểm. `USER` gần như luôn được đặt sẵn trên Unix.
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH"));
        pb.environment().put("GITHUB_OUTPUT", output.toString());
        if (moiTruong != null) {
            pb.environment().put("MOI_TRUONG", moiTruong);
        }
        pb.environment().putAll(bien);

        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script không kết thúc trong 30 giây");
        }
        return new KetQua(p.exitValue(), dauRa, Files.readString(output, StandardCharsets.UTF_8));
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
