package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * <b>Canh chuyện lượt quét bảo mật KHÔNG CHẠY — T11.67.</b>
 *
 * <h2>Khoảng trống bài này lấp</h2>
 *
 * {@code CanhBaoQuetCveTest} tự khai giới hạn của nó ở cuối javadoc: <i>"nó không phủ trường hợp
 * lượt quét không chạy (luật 31 — thứ nguy hiểm là sự vắng mặt)"</i>. Đó là một dòng nợ được ghi
 * thẳng vào bộ canh thay vì để trôi — và đây là lượt trả.
 *
 * <p>Chuông cũ treo vào {@code needs: [owasp, npm]} với {@code if: always()}, nên nó chỉ kêu được
 * khi <b>có một lượt để mà đỏ</b>. Tắt Actions, xoá nhầm workflow, YAML vỡ, GitHub bỏ lịch cron —
 * tất cả cho ra <b>im lặng tuyệt đối</b>, và im lặng đọc y hệt "mọi thứ đều ổn". Kho này đã trả giá
 * đúng hình dạng ấy hai lần: {@code skipped} được tính ĐẠT (luật 24), và {@code Promotion guard}
 * treo ở <i>"Expected"</i> mà không một dòng đỏ nào để đọc (§10.72).
 *
 * <h2>⭐⭐ Khẳng định chịu lực: chuông mới IM LẶNG khi lượt quét ĐỎ</h2>
 *
 * Đây là vế dễ làm sai nhất và cũng là vế đáng giá nhất. Kêu thêm một lần nữa cho một trạng thái
 * đã có chuông kèm bằng chứng là dựng lại đúng lỗi T11.84 vừa vá: <b>9 bình luận giống hệt nhau</b>,
 * một cái chuông một bit. Hai chuông phải phủ hai tập <b>rời nhau</b>, và bài
 * {@code doThiImLangVieCuaChuongKia} là thứ đóng đinh ranh giới ấy.
 *
 * <h2>Vì sao chạy script thật thay vì đọc chữ trong YAML</h2>
 *
 * Một bài khẳng định <i>"workflow có chứa {@code gh issue create}"</i> sẽ xanh với cả một chuông
 * đúng lẫn một chuông gọi lệnh ấy ở nhánh không bao giờ tới (luật 7). Ở đây script được <b>gọi</b>
 * với một {@code gh} giả, và thứ được khẳng định là <b>lệnh nào thật sự chạy</b>.
 *
 * <h2>⛔ Giới hạn của chính bài kiểm này (luật 28)</h2>
 *
 * Nó chứng minh <b>dây đã nối</b> và <b>các nhánh quyết định đúng</b>. Nó KHÔNG chứng minh GitHub
 * thật sự chạy workflow theo lịch — nửa ấy chỉ đo được bằng một lượt chạy thật, và trớ trêu là đúng
 * thứ bộ canh này sinh ra để phát hiện. Lượt bắn thật đầu tiên là bằng chứng còn thiếu.
 */
class CanhCongQuetTest {

    private static final String SCRIPT = ".github/scripts/canh-cong-quet.sh";
    private static final String WORKFLOW = ".github/workflows/canh-cong-quet.yml";
    private static final String WORKFLOW_QUET = ".github/workflows/security-scan.yml";

    /**
     * 08/09/2026 16:00 UTC — giờ ghim, để "tuổi lượt chạy" là số đo chứ ⛔ không phải đồng hồ máy.
     *
     * <p>⚠⚠ <b>Lượt viết đầu đặt nhầm hằng số này thành 1788105600 = 30/08/2026</b>, sớm hơn mọi mốc
     * fixture <b>9 ngày</b> ⇒ mọi phép trừ ra số ÂM ⇒ mọi lượt đều "còn tươi". Bài D đỏ và chỉ mình
     * nó đỏ — nhưng ba bài <i>còn tươi</i> (A, B, H) khi ấy cũng đang xanh <b>vì lý do sai</b>:
     * chúng chứng minh nhánh bình-thường chạy được với một tuổi ÂM, ⛔ không nói gì về ngưỡng.
     *
     * <p>⛔ Bài học giữ lại: <b>một hằng số thời gian sai chỉ làm ĐỎ nhánh xa nhất, các nhánh gần thì
     * XANH GIẢ</b> — và lượt sửa rẻ nhất lúc ấy là nới ngưỡng cho bài D hết đỏ, tức tự tay tháo bộ
     * canh. Vì thế mốc này được đối chiếu bằng một phép tính ĐỘC LẬP trong
     * {@link #tuoiPhaiDuongVaDungSoGio()} — ⛔ không phải bằng con mắt (luật 29).
     */
    private static final long BAY_GIO = 1_788_883_200L;

    private static final String KHONG_ISSUE = "[]";
    private static final String CO_ISSUE = "42";

    private static String luot(String ketCuc, String trangThai, String mocIso) {
        return """
               [{"databaseId":1,"conclusion":%s,"status":"%s","createdAt":"%s","updatedAt":"%s",\
               "url":"https://vi.du/runs/1"}]"""
                .formatted(ketCuc == null ? "null" : "\"" + ketCuc + "\"", trangThai, mocIso, mocIso);
    }

    /** 08/09 14:00 UTC — 2 giờ trước mốc ghim, thừa tươi. */
    private static final String TUOI_2H = "2026-09-08T14:00:00Z";

    /** 07/09 00:00 UTC — 40 giờ trước mốc ghim, quá ngưỡng 30. */
    private static final String TUOI_40H = "2026-09-07T00:00:00Z";

    // ---- Bốn trạng thái quyết định -------------------------------------------

    @Test
    @DisplayName("A · lượt quét xanh và còn tươi ⇒ im lặng, thoát 0")
    void xanhVaTuoiThiImLang(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, luot("success", "completed", TUOI_2H), KHONG_ISSUE);

        assertThat(kq.dauRa()).contains("TRANG_THAI=binh-thuong");
        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).noneMatch(l -> l.startsWith("issue create"));
    }

    @Test
    @DisplayName("⭐⭐ B · lượt quét ĐỎ ⇒ IM LẶNG — đó là việc của chuông kia, kêu hai lần là tiếng ồn")
    void doThiImLangVieCuaChuongKia(@TempDir Path thuMuc) throws Exception {
        // Khẳng định chịu lực. `bao-dong-quet-cve.sh` đã kêu cho trạng thái này, KÈM BẰNG CHỨNG
        // (vân tay tập CVE, nhãn LEO THANG/GIẢM/ĐỔI). Thêm một tiếng nữa ở đây là dựng lại đúng lỗi
        // T11.84 vừa vá — 9 bình luận giống hệt nhau, rồi không ai đọc nữa.
        KetQua kq = chay(thuMuc, luot("failure", "completed", TUOI_2H), KHONG_ISSUE);

        assertThat(kq.dauRa())
                .as("`failure` phải rơi vào nhánh bình-thường của bộ canh NÀY — nó canh sự VẮNG MẶT")
                .contains("TRANG_THAI=binh-thuong");
        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh())
                .as(
                        """
                        Hai chuông phải phủ hai tập RỜI NHAU. Chuông này kêu cho một lượt đỏ nghĩa là \
                        mỗi lượt quét đỏ sinh ra hai thông báo cho cùng một chuyện — đúng cách một \
                        cái chuông tự biến mình thành tiếng ồn (T11.84).""")
                .noneMatch(l -> l.startsWith("issue create") || l.startsWith("issue comment"));
    }

    @Test
    @DisplayName("⭐ C · 0 lượt chạy nào ⇒ mở issue và ĐỎ — workflow bị xoá/tắt")
    void khongCoLuotNaoThiKeu(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[]", KHONG_ISSUE);

        assertThat(kq.dauRa()).contains("TRANG_THAI=khong-co-luot");
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue create"));
        assertThat(kq.maThoat())
                .as(
                        """
                        Phải thoát KHÁC 0. §10.42 đã đổi một lần dừng hẳn lấy một dòng `::warning::` \
                        trên lượt xanh, và chuông ấy kêu đúng nguyên nhân lúc 31/8 23:54:54 rồi TRÔI \
                        QUA. Một issue không ai gán cũng trôi y như thế nếu tab Actions vẫn xanh.""")
                .isNotZero();
    }

    @Test
    @DisplayName("⭐ D · lượt mới nhất 40 giờ tuổi ⇒ quá hạn, kêu — lịch cron đã chết")
    void quaHanThiKeu(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, luot("success", "completed", TUOI_40H), KHONG_ISSUE);

        assertThat(kq.dauRa()).contains("TRANG_THAI=qua-han");
        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.contains("issue create") && l.contains("qua-han"));
    }

    @Test
    @DisplayName("⭐ E · kết cục `cancelled` ⇒ kêu — job báo động của lượt quét không chạy tới")
    void ketCucLaThiKeu(@TempDir Path thuMuc) throws Exception {
        // `cancelled`, `startup_failure` (YAML vỡ), `timed_out` — ở những trạng thái này job
        // `bao-dong` thường không chạy tới, nên nó KHÔNG phát ra gì. Đây là vùng chỉ bộ canh này thấy.
        KetQua kq = chay(thuMuc, luot("cancelled", "completed", TUOI_2H), KHONG_ISSUE);

        assertThat(kq.dauRa()).contains("TRANG_THAI=ket-cuc-la");
        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue create"));
    }

    @Test
    @DisplayName("⭐ F · mốc thời gian ĐỌC KHÔNG ĐƯỢC là một trạng thái riêng, ⛔ không phải 'còn tươi'")
    void mocHongThiKeuChuKhongDoanLaTuoi(@TempDir Path thuMuc) throws Exception {
        // Luật 9 ở dạng cụ thể nhất: `date` thất bại trả 0, và `bây giờ - 0` là một con số khổng lồ
        // hoặc — nếu ai đó "sửa" bằng cách mặc định về `bây giờ` — là 0 giờ tuổi, tức XANH GIẢ.
        // Bộ canh phải nói ra là nó không đọc được, chứ không đoán.
        KetQua kq = chay(thuMuc, luot("success", "completed", "khong-phai-ngay-thang"), KHONG_ISSUE);

        assertThat(kq.dauRa()).contains("TRANG_THAI=ket-cuc-la");
        assertThat(kq.maThoat()).isNotZero();
    }

    // ---- Hành vi với issue đang mở --------------------------------------------

    @Test
    @DisplayName("G · đã có issue mở ⇒ BÌNH LUẬN, ⛔ không mở cái thứ hai")
    void daCoIssueThiBinhLuan(@TempDir Path thuMuc) throws Exception {
        KetQua kq = chay(thuMuc, "[]", CO_ISSUE);

        assertThat(kq.lenhGh())
                .as("Mỗi ngày một issue mới là cách một cái chuông tự biến thành tiếng ồn")
                .anyMatch(l -> l.startsWith("issue comment 42"))
                .noneMatch(l -> l.startsWith("issue create"));
    }

    @Test
    @DisplayName("⭐ H · quét chạy lại bình thường ⇒ ĐÓNG issue đang mở")
    void binhThuongThiDongIssue(@TempDir Path thuMuc) throws Exception {
        // Vế phân biệt của bài G: thiếu nó thì một issue mở ra sẽ nằm đó vĩnh viễn, và một cái
        // chuông không bao giờ tắt cũng là một cái chuông không ai đọc.
        KetQua kq = chay(thuMuc, luot("success", "completed", TUOI_2H), CO_ISSUE);

        assertThat(kq.maThoat()).isZero();
        assertThat(kq.lenhGh()).anyMatch(l -> l.startsWith("issue close 42"));
    }

    // ---- Tự kiểm chứng (conventions.md §1.5) -----------------------------------

    @Test
    @DisplayName("⛔ I · thiếu `gh` trên PATH ⇒ ĐỎ, ⛔ không im lặng đi tiếp")
    void thieuGhThiDo(@TempDir Path thuMuc) throws Exception {
        // PATH chỉ có một thư mục tạm RỖNG — không `gh`, và cũng không `/usr/bin` (runner của
        // GitHub có `gh` thật ở đó, để nguyên PATH là bài này gọi `gh` THẬT).
        //
        // Bẫy đã trả giá hai lần: `verify-no-keys.sh` thiếu công cụ thì `exit 0`, bốn ngày in
        // "BỎ QUA việc kiểm khoá" mà không ai đọc; và phép kiểm bản dump thoát 0 ở MỌI lượt triển
        // khai từ 26/8 vì VPS không cài `postgresql-client` (T11.41).
        Path binRong = Files.createDirectories(thuMuc.resolve("bin-rong"));
        KetQua kq = chayVoiPath(thuMuc, binRong.toString(), Map.of());

        assertThat(kq.maThoat())
                .as("Một cổng kiểm thiếu công cụ là một cổng kiểm KHÔNG CHẠY, ⛔ không phải ĐẠT.\n\n%s", kq.dauRa())
                .isNotZero();
    }

    @Test
    @DisplayName("⛔ J · thiếu biến KHO ⇒ ĐỎ, ⛔ không hỏi nhầm kho")
    void thieuKhoThiDo(@TempDir Path thuMuc) throws Exception {
        Path bin = dungGhGia(thuMuc, "[]", KHONG_ISSUE);
        KetQua kq = chayVoiPath(thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), Map.of("KHO", ""));

        assertThat(kq.maThoat()).isNotZero();
        assertThat(kq.lenhGh()).isEmpty();
    }

    @Test
    @DisplayName("⭐ K · nhãn issue định nghĩa ĐÚNG MỘT LẦN — nhánh mở và nhánh đóng tìm cùng chuỗi")
    void nhanChiDinhNghiaMotLan() {
        String script = doc(timTuGocKho(SCRIPT));

        long soLanGan =
                script.lines().filter(d -> d.strip().startsWith("NHAN=")).count();
        assertThat(soLanGan)
                .as(
                        """
                        Hai hằng số rời nhau là dựng lại luật 14: nhánh MỞ tìm một chuỗi, nhánh ĐÓNG \
                        tìm chuỗi khác, và issue ⛔ không bao giờ được đóng.""")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐⭐ L · bộ canh phải là workflow RIÊNG — sống cùng nhà với thứ nó canh là chết cùng lúc")
    void phaiLaWorkflowRieng() {
        Path canh = timTuGocKho(WORKFLOW);
        assertThat(Files.exists(canh))
                .as("Thiếu `%s` thì script ở `%s` ⛔ không ai gọi — một bộ canh ⛔ không chạy", WORKFLOW, SCRIPT)
                .isTrue();

        String noiDung = doc(canh);
        assertThat(noiDung)
                .as("Workflow phải THẬT SỰ gọi script, ⛔ không chỉ nhắc tên nó trong chú thích")
                .contains("bash .github/scripts/canh-cong-quet.sh");

        // ⛔ Bất biến chịu lực: nó ⛔ KHÔNG được nằm trong `security-scan.yml`. Nếu tệp ấy vỡ cú
        //    pháp hoặc bị tắt, mọi job BÊN TRONG nó cũng ngừng — kể cả job đi báo rằng nó ngừng.
        assertThat(doc(timTuGocKho(WORKFLOW_QUET)))
                .as(
                        """
                        `security-scan.yml` ⛔ không được chứa bộ canh này. Một bộ canh sống cùng nhà \
                        với thứ nó canh sẽ chết cùng lúc, và cái chết ấy đọc y hệt "mọi thứ đều ổn" \
                        (luật 31).""")
                .doesNotContain("canh-cong-quet.sh");

        assertThat(noiDung)
                .as("Cần `issues: write` — thiếu nó `gh issue create` trả 403 TRONG IM LẶNG (§10.57)")
                .contains("issues: write");
        assertThat(noiDung)
                .as("Cần `actions: read` — ⛔ không có thì `gh run list` ⛔ không đọc được workflow khác")
                .contains("actions: read");
    }

    @Test
    @DisplayName("⭐ M · ngưỡng giờ do workflow TRUYỀN VÀO, và ≥ 13 giờ theo số đo độ trễ thật")
    void nguongTruyenVaoVaDuLon() {
        String wf = doc(timTuGocKho(WORKFLOW));
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("NGUONG_GIO:\\s*'?(\\d+)'?").matcher(wf);
        assertThat(m.find())
                .as("Workflow phải truyền `NGUONG_GIO` — ⛔ đừng để mặc định quyết định")
                .isTrue();

        int nguong = Integer.parseInt(m.group(1));
        assertThat(nguong)
                .as(
                        """
                        Đo 6 lượt `schedule` gần nhất: trễ 291–726 phút so với `15 2 * * *` ⇒ kết quả \
                        rơi vào 07:06–14:21 UTC. Ngưỡng dưới 13 giờ sẽ ĐỎ GIẢ vào đúng ngày GitHub \
                        trễ như đã đo, và lượt sửa lúc ấy rất dễ thành *nới cho hết đỏ* — tức tự tay \
                        tháo chuông.""")
                .isGreaterThanOrEqualTo(13);
    }

    @Test
    @DisplayName("⭐ O · thân issue ⛔ không mang dấu gạch chéo thừa — một chuông đọc như mã vỡ thì ít ai đọc")
    void thanIssueKhongCoDauGachCheoThua(@TempDir Path thuMuc) throws Exception {
        // ⛔ Lỗi thật, đo bằng `od -c`: trong chuỗi nháy đơn của `printf`, `\`` ⛔ KHÔNG phải một
        //    escape — nó in ra **cả dấu gạch chéo**, nên thân issue hiện `\`qua-han\`` thay vì mã
        //    nguồn. Mấy bài trên đều xanh với cả hai bản, vì ⛔ không bài nào nhìn vào THÂN.
        //    Đúng luật 28: bộ canh hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời
        //    bảo đảm.
        KetQua kq = chay(thuMuc, "[]", KHONG_ISSUE);

        String than = kq.lenhGh().stream()
                .filter(l -> l.startsWith("issue create"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("⛔ không có lượt `issue create` nào để soi thân"));

        assertThat(than)
                .as("Thân issue phải mang nháy ngược THẬT — `\\`` in ra cả dấu gạch chéo")
                .doesNotContain("\\`")
                .contains("`khong-co-luot`");
    }

    @Test
    @DisplayName("⭐⭐ N · tuổi fixture phải DƯƠNG và đúng số giờ — bắt đúng lỗi lượt viết đầu mắc")
    void tuoiPhaiDuongVaDungSoGio() {
        // ⛔ Bài này ⛔ KHÔNG gọi script. Nó tính lại bằng `java.time` — một nguồn KHÁC hẳn `date`
        //    của shell — nên nó ⛔ không chia giả định nào với thứ nó kiểm (luật 29). Đó chính là
        //    loại khẳng định đã cứu §10.62, nơi cả hai lượt kiểm chứng ngược đều sai theo cùng cách.
        long tuoi2h = (BAY_GIO - java.time.Instant.parse(TUOI_2H).getEpochSecond()) / 3600;
        long tuoi40h = (BAY_GIO - java.time.Instant.parse(TUOI_40H).getEpochSecond()) / 3600;

        assertThat(tuoi2h)
                .as(
                        """
                        Tuổi ÂM là cách mọi bài "còn tươi" xanh mà ⛔ không chứng minh gì: nhánh \
                        bình-thường vẫn chạy, chỉ là nó chạy với một con số vô nghĩa. Lượt viết đầu \
                        của bài này mắc đúng thế — mốc ghim sớm hơn fixture 9 ngày.""")
                .isEqualTo(2L);
        assertThat(tuoi40h).isEqualTo(40L);

        // Và hai mốc phải nằm HAI PHÍA của ngưỡng — nếu không thì bài A và bài D đang đo cùng một thứ.
        assertThat(tuoi2h).isLessThan(30L);
        assertThat(tuoi40h).isGreaterThanOrEqualTo(30L);
    }

    // ---- Bộ khung -------------------------------------------------------------

    private record KetQua(int maThoat, String dauRa, List<String> lenhGh) {}

    private static KetQua chay(Path thuMuc, String jsonRun, String soHieuIssue) throws Exception {
        Path bin = dungGhGia(thuMuc, jsonRun, soHieuIssue);
        return chayVoiPath(thuMuc, bin + java.io.File.pathSeparator + System.getenv("PATH"), Map.of());
    }

    /**
     * Dựng một {@code gh} giả: ghi nguyên văn argv ra tệp (phân cách bằng RS — thân bình luận nhiều
     * dòng nên ⛔ không tách được bằng xuống dòng); {@code run list} in danh sách đóng hộp,
     * {@code issue list} in số hiệu đóng hộp. Nhờ vậy mọi nhánh quyết định kiểm được bằng dữ liệu giả.
     */
    private static Path dungGhGia(Path thuMuc, String jsonRun, String soHieuIssue) throws IOException {
        Path bin = Files.createDirectories(thuMuc.resolve("bin"));
        Path nhatKy = thuMuc.resolve("argv.txt");
        Path runs = thuMuc.resolve("runs.json");
        Path issues = thuMuc.resolve("issues.txt");
        Files.writeString(runs, jsonRun, StandardCharsets.UTF_8);
        Files.writeString(issues, KHONG_ISSUE.equals(soHieuIssue) ? "" : soHieuIssue, StandardCharsets.UTF_8);
        Files.writeString(nhatKy, "", StandardCharsets.UTF_8);

        Path gh = bin.resolve("gh");
        Files.writeString(
                gh,
                """
                #!/usr/bin/env bash
                printf '%%s\\036' "$*" >> '%s'
                if [ "$1" = run ] && [ "$2" = list ]; then cat '%s'; fi
                if [ "$1" = issue ] && [ "$2" = list ]; then cat '%s'; fi
                exit 0
                """
                        .formatted(nhatKy, runs, issues),
                StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(gh, PosixFilePermissions.fromString("rwxr-xr-x"));
        return bin;
    }

    private static KetQua chayVoiPath(Path thuMuc, String path, Map<String, String> env) throws Exception {
        // ⚠ `/bin/bash` TUYỆT ĐỐI, ⛔ không dựa vào PATH: bài I cố tình dựng PATH rỗng để giấu `gh`,
        //   và nếu bash cũng phải tra qua PATH thì bài ấy hỏng vì lý do khác hẳn thứ nó đo. Cũng
        //   ⛔ không dùng shebang — shell mặc định của máy dev là zsh (luật 20).
        ProcessBuilder pb = new ProcessBuilder("/bin/bash", timTuGocKho(SCRIPT).toString());
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().put("PATH", path);
        pb.environment().put("KHO", "songnhue/songnhue");
        pb.environment().put("WORKFLOW", "security-scan.yml");
        pb.environment().put("NHANH", "dev");
        pb.environment().put("NGUONG_GIO", "30");
        // ⛔ Giờ GHIM. Không có nó thì "40 giờ tuổi" trôi theo đồng hồ máy, và bài D sẽ đổi kết quả
        //    theo ngày chạy — một bài kiểm chập chờn dạy người ta chạy lại cho xanh (T37.14).
        pb.environment().put("BAY_GIO", String.valueOf(BAY_GIO));
        pb.environment().putAll(env);

        Path nhatKy = thuMuc.resolve("argv.txt");
        Process p = pb.start();
        String dauRa = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(30, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return fail("Script ⛔ không kết thúc trong 30 giây");
        }

        List<String> lenhGh = Files.exists(nhatKy)
                ? Arrays.stream(Files.readString(nhatKy, StandardCharsets.UTF_8).split("\\u001e"))
                        .filter(d -> !d.isBlank())
                        .toList()
                : List.of();
        return new KetQua(p.exitValue(), dauRa, lenhGh);
    }

    private static String doc(Path duongDan) {
        try {
            return Files.readString(duongDan, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Không đọc được " + duongDan, e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path thu = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(thu)) {
                return thu;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("Không tìm thấy " + duongDanTuongDoi);
    }
}
