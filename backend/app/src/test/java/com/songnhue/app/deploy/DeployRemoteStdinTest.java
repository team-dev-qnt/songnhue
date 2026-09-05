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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Khối lệnh chạy trên máy chủ không được đi qua stdin của bash-từ-xa.</b>
 *
 * <h2>Chuyện đã xảy ra — 27/8, §10.60</h2>
 *
 * CD Staging báo <b>success</b> trọn vẹn: pull xong, migration xong, 4/4 câu smoke test xanh, gắn tag
 * {@code :staging}, ghi tóm tắt. Đo trên máy chủ ngay sau đó:
 *
 * <pre>
 *   app        digest vừa "triển khai" 6dcf9e4b…   ĐANG chạy 9c9f18e9…  (tạo 25/8)
 *   admin-app                          af546d21…             f022dac1…  (tạo 24/8)
 *   public-web                         ed49011f…             19754e8b…  (tạo 24/8)
 * </pre>
 *
 * <b>Không container nào được thay.</b> Nguyên nhân: khối "Triển khai" được nuôi vào bash-từ-xa bằng
 * chính stdin ({@code ssh host bash <<REMOTE}). Bash đọc script ấy DẦN, và {@code docker compose run
 * --rm migrator} <b>gắn stdin</b> nên nó nuốt nốt phần script chưa đọc. Bash gặp EOF, thoát 0.
 * {@code up -d --force-recreate} và cả bước đo lại image ID <b>không bao giờ chạy</b>.
 *
 * <p>Đo trên VPS-2 (mỗi dòng là một khối heredoc có {@code echo} đứng sau lệnh):
 *
 * <pre>
 *   docker compose run --rm            → dòng sau MẤT
 *   docker compose run --rm -T         → dòng sau MẤT   ← `-T` chỉ tắt TTY, vẫn gắn stdin
 *   docker compose run --rm &lt;/dev/null → dòng sau CHẠY
 *   docker compose up -d               → dòng sau CHẠY
 *   docker compose pull                → dòng sau CHẠY
 * </pre>
 *
 * <h2>Vì sao 4/4 smoke test không cứu được</h2>
 *
 * Chúng hỏi <i>site</i>, mà site vẫn sống — bằng mã cũ. Không câu nào phân biệt được "đã thay
 * container" với "chưa thay". Đó đúng là việc của bước đo lại image ID, và bước ấy nằm trong phần bị
 * nuốt. Cùng họ với §10.53: <i>ba dòng xanh, và không dòng nào nói container đang chạy cái gì.</i>
 *
 * <h2>Bảo đảm nằm ở đâu</h2>
 *
 * Ở {@code .github/scripts/chay-tu-xa.sh} — <b>chỗ dữ liệu đi qua</b> (CLAUDE.md luật 12), không ở
 * từng lời gọi. Thêm {@code </dev/null} vào riêng dòng {@code migrator} chỉ chữa đúng dòng ấy;
 * {@code pre-deploy-dump.sh} đã có sẵn 3 lệnh {@code docker exec -i} và chưa gây hại chỉ vì tình cờ
 * được gọi ở DÒNG CUỐI của khối.
 */
class DeployRemoteStdinTest {

    private static final String HELPER = ".github/scripts/chay-tu-xa.sh";

    /** Khối thử: dòng 2 nuốt stdin, dòng 3 là thứ phân biệt hai trạng thái. */
    private static final String KHOI_THU =
            """
            echo DONG-1
            cat > /dev/null
            echo DONG-3
            """;

    // =========================================================================
    // Phần 1 — deploy.yml không còn nuôi bash-từ-xa bằng stdin
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Không khối nào nuôi `bash` từ xa bằng heredoc qua stdin")
    void khongNuoiBashTuXaBangStdin() {
        String w = doc(timTuGocKho(".github/workflows/deploy.yml"));

        Matcher m = Pattern.compile("(?m)^.*\\bssh\\b.*\\bbash\\b.*<<.*$").matcher(boChuThich(w));
        if (m.find()) {
            fail(
                    """
                    `deploy.yml` còn nuôi bash-từ-xa bằng stdin:

                        %s

                    Bash đọc script ấy DẦN từ stdin, nên lệnh nào bên trong gắn stdin sẽ nuốt nốt phần \
                    chưa đọc — bash gặp EOF, thoát 0, bước XANH sau khi bỏ qua nửa cuối công việc. Đã \
                    hỏng đúng vậy 27/8: không container nào được thay mà CD báo thành công (§10.60).

                    Dùng `%s` — nó chuyển khối sang TỆP rồi chạy với stdin là /dev/null."""
                            .formatted(m.group().strip(), HELPER));
        }
    }

    @Test
    @DisplayName("⭐ Mọi heredoc `REMOTE` trong deploy.yml đều mở bằng helper")
    void moiHeredocDeuQuaHelper() {
        String w = boChuThich(doc(timTuGocKho(".github/workflows/deploy.yml")));

        Matcher m = Pattern.compile("(?m)^.*<<'?REMOTE'?\\s*$").matcher(w);
        int dem = 0;
        while (m.find()) {
            dem++;
            assertThat(m.group())
                    .as("Khối heredoc không đi qua helper — xem `khongNuoiBashTuXaBangStdin`")
                    .contains("chay-tu-xa.sh");
        }
        // Chặn xanh-trên-tập-rỗng: đổi tên dấu kết thúc heredoc thì bài này phải ĐỎ, không phải im lặng đạt.
        assertThat(dem)
                .as("Không tìm thấy khối heredoc nào — regex đã lỗi thời, SỬA bài kiểm")
                .isEqualTo(4);
    }

    @Test
    @DisplayName("⭐⭐ Heredoc KHÔNG NHÁY: không dấu huyền / `$(` nào chưa thoát — kể cả trong chú thích")
    void heredocKhongNhayKhongThayTheLenh() {
        List<String> viPham = timThayTheLenh(doc(timTuGocKho(".github/workflows/deploy.yml")));
        if (!viPham.isEmpty()) {
            fail(
                    """
                    %d dòng trong heredoc KHÔNG NHÁY còn dấu huyền hoặc `$(` chưa thoát:

                      %s

                    ⛔ Heredoc mở bằng `<<REMOTE` (không nháy) được RUNNER khai triển trước khi \
                    gửi đi — dấu huyền là THAY THẾ LỆNH, kể cả nằm trong một dòng `#`. Ngày 27/8 \
                    một khối chú thích về `docker compose run` làm runner chạy thật `docker \
                    compose run`, `up -d --force-recreate` và `-T`, in ra ba dòng `command not \
                    found` giữa lượt deploy (§10.66).

                    Cần biến của runner (`$APP_IMAGE`, `$COMPOSE`) nên KHÔNG nháy được dấu kết \
                    thúc heredoc. Vậy thì thoát thủ công: `\\`` và `\\$(`."""
                            .formatted(viPham.size(), String.join("\n      ", viPham)));
        }
    }

    @Test
    @DisplayName("⭐⭐ Bộ dò ấy BẮT ĐƯỢC vi phạm — chứng minh cái xanh ở trên có nghĩa")
    void boDoThayTheLenhBatDuocViPham() {
        String gia =
                """
                      - name: Giả
                        run: |
                          .github/scripts/chay-tu-xa.sh "gia" <<REMOTE
                            # ⚠ `docker compose run` nuốt stdin
                            echo "\\$(date)"
                            ket=$(hostname)
                          REMOTE
                """;
        assertThat(timThayTheLenh(gia))
                .as("Bộ dò phải bắt CẢ dấu huyền trong chú thích LẪN `$(` chưa thoát")
                .hasSize(2);

        String sach = gia.replace("`docker compose run`", "\\`docker compose run\\`")
                .replace("ket=$(hostname)", "ket=\\$(hostname)");
        assertThat(timThayTheLenh(sach))
                .as("Bản đã thoát phải sạch — nếu không thì bộ dò đang bắt bừa")
                .isEmpty();
    }

    /**
     * Trả về mọi dòng nằm trong một heredoc KHÔNG NHÁY còn dấu huyền hoặc {@code $(} chưa thoát.
     *
     * <p>⚠ Cố ý KHÔNG dùng {@link #boChuThich}: dòng {@code #} bên trong heredoc chính là chỗ đã
     * phát nổ ngày 27/8. Với shell nó là chú thích, nhưng runner khai triển dấu huyền TRƯỚC khi
     * shell nào nhìn thấy dòng ấy.
     */
    private static List<String> timThayTheLenh(String yaml) {
        Pattern mo = Pattern.compile("<<([A-Z]+)\\s*$");
        Pattern xau = Pattern.compile("(?<!\\\\)`|(?<!\\\\)\\$\\(");

        List<String> viPham = new ArrayList<>();
        String dauKetThuc = null;
        int soKhoi = 0;
        String[] dong = yaml.split("\n", -1);
        for (int i = 0; i < dong.length; i++) {
            if (dauKetThuc == null) {
                Matcher m = mo.matcher(dong[i]);
                if (m.find()) {
                    dauKetThuc = m.group(1);
                    soKhoi++;
                }
                continue;
            }
            if (dong[i].strip().equals(dauKetThuc)) {
                dauKetThuc = null;
                continue;
            }
            if (xau.matcher(dong[i]).find()) {
                viPham.add("dòng %d: %s".formatted(i + 1, dong[i].strip()));
            }
        }
        // Chặn xanh-trên-tập-rỗng: regex mở heredoc lỗi thời thì không quét được gì.
        assertThat(soKhoi)
                .as("Không thấy heredoc KHÔNG NHÁY nào — regex đã lỗi thời, SỬA bài kiểm chứ đừng xoá")
                .isGreaterThanOrEqualTo(1);
        return viPham;
    }

    // =========================================================================
    // Phần 2 — helper giữ đúng hai bảo đảm của nó
    // =========================================================================

    @Test
    @DisplayName("⭐ Helper có mặt, chạy được, và mang đủ hai bảo đảm")
    void helperCoDuHaiBaoDam() {
        Path h = timTuGocKho(HELPER);
        assertThat(Files.isExecutable(h))
                .as("`%s` phải có cờ thực thi — workflow gọi thẳng", HELPER)
                .isTrue();

        String s = doc(h);
        assertThat(s).as("Thiếu `</dev/null` thì khối vẫn có thể bị nuốt stdin").contains("</dev/null");
        assertThat(s)
                .as("Thiếu bước đối chiếu số byte thì 'sang máy chủ một nửa' vẫn chạy êm rồi thoát 0")
                .contains("SO_BYTE_XA");
    }

    // =========================================================================
    // Phần 3 — KIỂM CHỨNG NGƯỢC: chạy thật, và chứng minh bài kiểm phân biệt được
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Kiểu CŨ mất dòng sau lệnh nuốt stdin — chứng minh lỗi có thật")
    void kieuCuMatDongSau(@TempDir Path hop) throws Exception {
        // Không có bước này thì bài dưới chỉ chứng minh "kiểu mới chạy được", chứ không chứng minh
        // "kiểu cũ hỏng" — mà đó mới là điều cả bài này nói.
        Path khoi = hop.resolve("khoi.sh");
        Files.writeString(khoi, KHOI_THU, StandardCharsets.UTF_8);

        // `bash` đọc script từ STDIN — đúng hình dạng `ssh host bash <<REMOTE`.
        KetQua kq = chay(hop, Map.of(), new String[] {"bash", "-euo", "pipefail"}, khoi);

        assertThat(kq.ra())
                .as("Dòng đầu phải chạy — nếu không thì phép thử sai chỗ khác")
                .contains("DONG-1");
        assertThat(kq.ra())
                .as(
                        """
                        Kiểu cũ VẪN in DONG-3 → phép thử này không tái hiện được lỗi, nên bài dưới \
                        không chứng minh gì. Có thể `cat` đã đổi hành vi, hoặc bash đã đọc trọn stdin \
                        trước khi chạy. SỬA phép thử, đừng xoá.""")
                .doesNotContain("DONG-3");
    }

    @Test
    @DisplayName("⭐⭐ Helper giữ được dòng sau lệnh nuốt stdin")
    void helperGiuDuocDongSau(@TempDir Path hop) throws Exception {
        Path khoi = hop.resolve("khoi.sh");
        Files.writeString(khoi, KHOI_THU, StandardCharsets.UTF_8);

        KetQua kq = chay(hop, Map.of(), new String[] {timTuGocKho(HELPER).toString(), "thu"}, khoi);

        assertThat(kq.ma()).as("Helper thoát khác 0.\n%s", kq).isZero();
        assertThat(kq.ra()).contains("DONG-1");
        assertThat(kq.ra())
                .as("Helper vẫn để khối bị nuốt stdin — bảo đảm chính đã hỏng.\n%s", kq)
                .contains("DONG-3");
    }

    @Test
    @DisplayName("⭐⭐ Helper DỪNG ĐỎ khi khối sang máy chủ thiếu byte")
    void helperDungKhiThieuByte(@TempDir Path hop) throws Exception {
        Path khoi = hop.resolve("khoi.sh");
        Files.writeString(khoi, KHOI_THU, StandardCharsets.UTF_8);

        // `CAT_CUT=1` bảo shim `ssh` giả cắt bớt lúc chuyển — mô phỏng đĩa đầy / kết nối đứt.
        KetQua kq = chay(
                hop, Map.of("CAT_CUT", "1"), new String[] {timTuGocKho(HELPER).toString(), "thu"}, khoi);

        assertThat(kq.ma())
                .as("Chuyển thiếu byte mà helper vẫn thoát 0 — đúng cái bẫy nó tồn tại để chặn.\n%s", kq)
                .isNotZero();
        assertThat(kq.ra() + kq.loi()).contains("KHÔNG trọn vẹn");
        assertThat(kq.ra()).as("Khối thiếu byte KHÔNG được chạy dở").doesNotContain("DONG-1");
    }

    // -------------------------------------------------------------------------

    private record KetQua(int ma, String ra, String loi) {
        @Override
        public String toString() {
            return "exit=%d%nstdout:%n%s%nstderr:%n%s".formatted(ma, ra, loi);
        }
    }

    /**
     * Chạy {@code lenh} với stdin là {@code khoi}, dưới một shim `ssh` giả chạy mọi thứ ở MÁY NÀY.
     *
     * <p>Shim nhận đúng hai đối số như helper gọi: {@code user@host} và chuỗi lệnh. Nó bỏ đối số đầu
     * rồi chạy chuỗi lệnh cục bộ — nên bài kiểm đi qua đúng đường mã thật đi, không mock lời gọi.
     */
    private static KetQua chay(Path hop, Map<String, String> them, String[] lenh, Path khoi) throws Exception {
        Path binDir = Files.createDirectories(hop.resolve("bin"));
        Path shim = binDir.resolve("ssh");
        Files.writeString(
                shim,
                """
                #!/usr/bin/env bash
                shift                      # bỏ user@host
                if [ -n "${CAT_CUT:-}" ] && [ "${1#cat >}" != "$1" ]; then
                    exec bash -c "head -c 5 | $1"
                fi
                exec bash -c "$1"
                """,
                StandardCharsets.UTF_8);
        shim.toFile().setExecutable(true);

        ProcessBuilder pb = new ProcessBuilder(lenh);
        pb.directory(hop.toFile());
        pb.redirectInput(khoi.toFile());
        pb.environment().put("PATH", binDir + ":" + System.getenv("PATH"));
        pb.environment().put("HOST", "may-gia");
        pb.environment().put("USER", "nguoi-gia");
        pb.environment().putAll(them);

        Process p = pb.start();
        String ra = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String loi = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Quá 60 giây — helper treo chờ stdin?");
        }
        return new KetQua(p.exitValue(), ra, loi);
    }

    private static String boChuThich(String noiDung) {
        StringBuilder ket = new StringBuilder();
        for (String dong : noiDung.split("\n", -1)) {
            if (!dong.stripLeading().startsWith("#")) {
                ket.append(dong).append('\n');
            }
        }
        return ket.toString();
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
