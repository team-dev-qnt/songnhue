package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.SongnhuePostgres;

/**
 * <b>Nhánh quay lui phải ĐO lược đồ, ⛔ đoán — T11.99 (CLAUDE.md luật 37).</b>
 *
 * <h2>Chuyện đã xảy ra — 26/09/2026, run 36238573202</h2>
 *
 * CD Staging đỏ ở bước 13 vì {@code docker compose pull} trả {@code error from registry: denied}. Pull đứng NGAY
 * TRƯỚC {@code compose run --rm migrator}, nên {@code set -e} cắt script và <b>CSDL ⛔ bị chạm một byte nào</b>.
 * Nhánh quay lui vẫn in nguyên văn:
 *
 * <pre>
 *   ⛔ NHƯNG lược đồ CSDL vẫn đang ở trạng thái sau migration của lượt hỏng.
 *      Nếu migration ấy có đổi lược đồ thì phải khôi phục từ bản chụp predeploy-*.
 * </pre>
 *
 * Một câu KHẲNG ĐỊNH vô điều kiện rồi một câu điều kiện chữa sau. Câu đầu sai, và sai về phía <b>phá huỷ nhất</b>:
 * nó đẩy người trực sang khôi phục CSDL — ở trạng thái ấy khôi phục ⛔ chữa được gì mà <b>xoá mất</b> dữ liệu sinh
 * ra sau bản chụp.
 *
 * <p>⛔⛔ Và khối chú thích {@code T63.19} nằm <b>ngay dưới</b> chính bước ấy đã dặn đúng chữ <i>"ĐỪNG ĐOÁN NGUYÊN
 * NHÂN Ở ĐÂY"</i>. Một lời dặn ⛔ phải một cổng kiểm (§11.19) — nên bài này là cổng.
 *
 * <h2>Bài này canh</h2>
 *
 * <ol>
 *   <li><b>Câu đoán đã biến mất</b> — gỡ bản vá là bài đỏ ngay ở khẳng định đầu tiên.
 *   <li><b>Ba trạng thái, ba câu đọc khác nhau</b> (luật 9): ⛔ đo được · y nguyên · đã đổi. Rỗng ⛔ được rơi vào
 *       nhánh "y nguyên" — hai chuỗi rỗng bằng nhau sẽ cho một câu XANH GIẢ về đúng thứ nguy hiểm nhất.
 *   <li><b>Thứ tự</b> — bước đo "trước" phải đứng TRƯỚC bước Triển khai (nơi {@code migrator} chạy). Đặt sau thì
 *       hai lượt đo cùng một trạng thái và phép so LUÔN nói "y nguyên".
 *   <li><b>Một bản câu hỏi</b> (luật 14) — cả hai lượt đo gọi CÙNG {@code backup/moc-luoc-do.sh}, và {@code
 *       deploy.yml} ⛔ mang bản chép nào của câu SQL.
 *   <li><b>Phép đo THẬT phân biệt được hai trạng thái</b> — trích nguyên văn câu SQL từ script rồi chạy nó trên
 *       Postgres thật: thêm một hàng {@code flyway_schema_history} ⇒ giá trị PHẢI đổi; quay lui ⇒ PHẢI về đúng
 *       giá trị cũ (luật 10, cả hai vế).
 * </ol>
 *
 * <p>⚠ Giới hạn khai ra (luật 28): bài chạy câu SQL qua JDBC, ⛔ qua {@code docker exec} và ⛔ qua SSH. Nó chứng
 * minh <i>câu hỏi phân biệt được hai trạng thái</i>, ⛔ chứng minh <i>đường ống tới máy chủ chạy được</i>. Bằng
 * chứng trọn vẹn là một lượt CD hỏng thật — và lượt ấy sẽ in ra một trong ba câu ở mục 2.
 */
class QuayLuiDoLuocDoTest extends IntegrationTestBase {

    private static final String WORKFLOW = ".github/workflows/deploy.yml";
    private static final String SCRIPT = "deploy/backup/moc-luoc-do.sh";

    /** Nguyên văn câu bị gỡ. Nó phải ⛔ còn ở bất kỳ đâu trong workflow. */
    private static final String CAU_DOAN = "lược đồ CSDL vẫn đang ở trạng thái sau migration";

    private static final String MOC_KHONG_DO_DUOC = "⚠ CSDL: ⛔ ĐO ĐƯỢC";
    private static final String MOC_Y_NGUYEN = "✓ CSDL: lược đồ Y NGUYÊN";
    private static final String MOC_DA_DOI = "⛔ CSDL: lược đồ ĐÃ ĐỔI";

    // ── 1 · Câu đoán đã biến mất ──────────────────────────────────────────────

    @Test
    @DisplayName("⛔⛔ T11.99 — workflow ⛔ còn KHẲNG ĐỊNH vô điều kiện rằng migration đã chạm CSDL")
    void khongConCauDoan() {
        assertThat(doc(WORKFLOW))
                .as(
                        """
                        `%s` còn nằm trong deploy.yml. Đó là một câu đoán, và nó đoán về phía PHÁ HUỶ \
                        nhất — lượt 36238573202 (26/09) có `compose pull` đỏ TRƯỚC `migrator`, tức CSDL \
                        ⛔ bị chạm, mà câu ấy vẫn đẩy người trực sang khôi phục CSDL (luật 37).""",
                        CAU_DOAN)
                .doesNotContain(CAU_DOAN);
    }

    // ── 2 · Ba trạng thái, ba câu ────────────────────────────────────────────

    @Test
    @DisplayName("⛔⛔ Luật 9 — ba trạng thái phải cho BA câu, và RỖNG ⛔ được đọc thành `y nguyên`")
    void baTrangThaiBaCau() {
        String buoc = thanBuoc("Quay lui bản cũ");

        int iRong = buoc.indexOf(MOC_KHONG_DO_DUOC);
        int iBang = buoc.indexOf(MOC_Y_NGUYEN);
        int iKhac = buoc.indexOf(MOC_DA_DOI);
        assertThat(List.of(iRong, iBang, iKhac))
                .as("thiếu một trong ba câu — hai trạng thái in ra cùng một câu là ⛔ khẳng định gì (luật 9)")
                .allMatch(i -> i >= 0);

        int iVeRong = buoc.indexOf("[ -z \"$TRUOC\" ] || [ -z \"$SAU\" ]");
        int iVeBang = buoc.indexOf("[ \"$SAU\" = \"$TRUOC\" ]");
        assertThat(iVeRong)
                .as("vế ⛔-đo-được phải được hỏi TRƯỚC vế bằng nhau — nếu ⛔ thì hai chuỗi RỖNG bằng nhau sẽ in"
                        + " `y nguyên`, một câu xanh giả về đúng ca nguy hiểm nhất")
                .isNotNegative()
                .isLessThan(iVeBang);
        assertThat(iVeRong).isLessThan(iRong);
        assertThat(iRong).isLessThan(iVeBang);
        assertThat(iVeBang).isLessThan(iBang);
        assertThat(iBang).isLessThan(iKhac);
    }

    @Test
    @DisplayName("⛔ Chỉ nhánh `y nguyên` được nói ⛔ khôi phục; nhánh `đã đổi` ⛔ được ra lệnh khôi phục")
    void khuyenNghiDungNhanh() {
        String buoc = thanBuoc("Quay lui bản cũ");
        // ⚠ Hỏi sự CÓ MẶT trước khi cắt chuỗi. Bản đầu cắt thẳng và lượt kiểm chứng ngược đỏ bằng
        //   `StringIndexOutOfBounds Range [-1, -1)` — một bộ canh đỏ mà ⛔ nói được vì sao thì gần
        //   như ⛔ có (§11.20). Lộ ra vì bản phá được chạy thật, ⛔ vì đọc lại mã.
        assertThat(buoc)
                .as("thiếu nhãn nhánh — xem `baTrangThaiBaCau`; ba trạng thái phải cho ba câu (luật 9)")
                .contains(MOC_Y_NGUYEN)
                .contains(MOC_DA_DOI);
        String nhanhBang = buoc.substring(buoc.indexOf(MOC_Y_NGUYEN), buoc.indexOf(MOC_DA_DOI));
        String nhanhKhac = buoc.substring(buoc.indexOf(MOC_DA_DOI));

        assertThat(nhanhBang)
                .as("lược đồ y nguyên ⇒ khôi phục CSDL ⛔ chữa gì mà xoá mất dữ liệu mới — phải nói thẳng")
                .contains("⛔ khôi phục CSDL");
        assertThat(nhanhKhac)
                .as("lược đồ đã đổi vẫn ⛔ phải một lệnh khôi phục: bản cũ đang TRẢ LỜI trên lược đồ mới, nên"
                        + " việc kế tiếp là ĐO dữ liệu (luật 37 — in khả năng kèm phép đo, ⛔ kết luận)")
                .contains("đo dữ liệu trước")
                .contains("docs/runbook/khoi-phuc-du-lieu.md");
    }

    // ── 3 · Thứ tự ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("⛔⛔ Bước đo `trước` phải đứng TRƯỚC Triển khai — đặt sau thì phép so LUÔN nói `y nguyên`")
    void doTruocKhiMigratorChay() {
        assertThat(tenCacBuoc())
                .containsSubsequence(
                        "pg_dump trước khi triển khai", "Đo lược đồ trước triển khai", "Triển khai", "Quay lui bản cũ");
    }

    // ── 4 · Một bản câu hỏi ──────────────────────────────────────────────────

    @Test
    @DisplayName("⛔ Luật 14 — hai lượt đo gọi CÙNG một script, và deploy.yml ⛔ mang bản chép nào của câu SQL")
    void motBanCauHoi() {
        String w = doc(WORKFLOW);
        assertThat(w.split(Pattern.quote("./backup/moc-luoc-do.sh"), -1).length - 1)
                .as("phải ĐÚNG hai lời gọi: một trước triển khai, một trong nhánh quay lui")
                .isEqualTo(2);
        assertThat(w)
                .as("câu hỏi chỉ được có MỘT nhà (%s) — một bản chép trong workflow là hai bản sẽ trôi xa nhau", SCRIPT)
                .doesNotContain("flyway_schema_history");

        Path script = timTuGocKho(SCRIPT);
        assertThat(Files.isExecutable(script))
                .as("`./backup/moc-luoc-do.sh` gọi trực tiếp — thiếu bit thực thi là `Permission denied` trên máy chủ")
                .isTrue();
    }

    // ── 5 · Phép đo THẬT phân biệt được hai trạng thái ────────────────────────

    @Test
    @DisplayName("⭐⭐ Câu SQL trích từ script: thêm một hàng flyway ⇒ mốc PHẢI đổi; quay lui ⇒ PHẢI về cũ")
    void cauDoPhanBietDuocHaiTrangThai() throws SQLException {
        String cauDo = cauTrongScript("CAU_DO");

        try (Connection c = moKetNoi()) {
            c.setAutoCommit(false);

            String truoc = hoi(c, cauDo);
            assertThat(truoc)
                    .as("phải đọc được bảng THẬT — ⛔ khớp hình dạng nghĩa là Flyway ⛔ chạy, tức bài đang canh"
                            + " một tập rỗng")
                    .matches("\\d+:\\d+:\\d+");
            assertThat(Integer.parseInt(truoc.split(":")[0]))
                    .as("số hàng flyway_schema_history phải > 0")
                    .isPositive();

            try (Statement s = c.createStatement()) {
                s.executeUpdate(
                        """
                        INSERT INTO public.flyway_schema_history
                          (installed_rank, version, description, type, script, checksum,
                           installed_by, installed_on, execution_time, success)
                        SELECT max(installed_rank) + 1, '999999999999', 'T11.99 do thu', 'SQL',
                               'V999999999999__do_thu.sql', 0, 'test', now(), 1, true
                        FROM public.flyway_schema_history
                        """);
            }
            assertThat(hoi(c, cauDo))
                    .as("một lượt migrator chạy được mà mốc ⛔ đổi thì phép so ở nhánh quay lui ⛔ phân biệt được"
                            + " gì — nó sẽ in `y nguyên` ngay cả khi lược đồ ĐÃ đổi (luật 9)")
                    .isNotEqualTo(truoc);

            c.rollback();
        }

        // Luật 10, VẾ THỨ HAI: xác nhận bản khôi phục — ⛔ chỉ xác nhận bản phá.
        try (Connection c = moKetNoi()) {
            assertThat(hoi(c, cauDo))
                    .as("lượt rollback phải trả bảng về nguyên trạng — một bài kiểm để lại hàng rác trong"
                            + " flyway_schema_history sẽ làm đỏ mọi lớp chạy sau nó")
                    .matches("\\d+:\\d+:\\d+");
        }
    }

    @Test
    @DisplayName("⛔⛔ Vế dò bảng phải CHẠY ĐƯỢC khi bảng vắng mặt — `CASE` bọc truy vấn con thì ⛔")
    void veDoBangChayDuocKhiBangVangMat() throws SQLException {
        String cauCoBang = cauTrongScript("CAU_CO_BANG");

        try (Connection c = moKetNoi()) {
            c.setAutoCommit(false);
            try (Statement s = c.createStatement()) {
                // Bảng này ⛔ ai đọc lúc chạy, nên khoá ở đây ⛔ chặn lớp kiểm nào; `lock_timeout`
                // để một ngày nào đó có người đọc thì bài ĐỎ có lý do chứ ⛔ treo.
                s.execute("SET LOCAL lock_timeout = '10s'");
            }

            assertThat(hoi(c, cauCoBang)).as("bảng ĐANG có").isEqualTo("t");

            try (Statement s = c.createStatement()) {
                s.execute("ALTER TABLE public.flyway_schema_history RENAME TO flyway_schema_history_tam_t1199");
            }
            // ⛔⛔ Bản ĐẦU của script gộp hai vế thành một `CASE WHEN to_regclass(…) IS NULL THEN
            //   'chua-co-bang' ELSE (SELECT … FROM …) END` và NÉM ở đúng dòng này: PostgreSQL phân
            //   giải tên bảng lúc PHÂN TÍCH, ⛔ lúc thực thi. Một vế bảo vệ đọc y như nó chạy mà
            //   ⛔ chạy — và nó chỉ hỏng trong ĐÚNG ca nó sinh ra để đỡ (máy trắng, lượt đầu).
            assertThat(hoi(c, cauCoBang))
                    .as("bảng vắng mặt phải trả `f` chứ ⛔ ném — `CSDL chưa có bảng nào` và `⛔ hỏi được CSDL` dẫn"
                            + " tới hai việc khác hẳn nhau (luật 9)")
                    .isEqualTo("f");

            c.rollback();
        }

        try (Connection c = moKetNoi()) {
            assertThat(hoi(c, cauCoBang))
                    .as("luật 10 vế hai — lượt đổi tên phải được trả lại")
                    .isEqualTo("t");
        }
    }

    // ── hạ tầng ──────────────────────────────────────────────────────────────

    /** Câu SQL NGUYÊN VĂN trong {@code moc-luoc-do.sh} — ⛔ chép lại ở đây (T51.15). */
    private static String cauTrongScript(String bien) {
        Matcher m = Pattern.compile("(?m)^" + bien + "=\"(.+)\"$").matcher(doc(SCRIPT));
        assertThat(m.find())
                .as("⛔ thấy dòng `%s=\"…\"` trong %s — script đổi hình dạng thì SỬA bài kiểm, đừng xoá", bien, SCRIPT)
                .isTrue();
        return m.group(1);
    }

    private static Connection moKetNoi() throws SQLException {
        // `postgres` — đúng vai trò script dùng bên trong container (`psql -U postgres`).
        return DriverManager.getConnection(
                SongnhuePostgres.instance().getJdbcUrl(), "postgres", SongnhuePostgres.password());
    }

    private static String hoi(Connection c, String cauDo) throws SQLException {
        try (Statement s = c.createStatement();
                ResultSet rs = s.executeQuery(cauDo)) {
            assertThat(rs.next()).as("câu đo phải trả đúng một hàng").isTrue();
            return rs.getString(1);
        }
    }

    /** Thân {@code run:} của một bước, tra theo tên. */
    private static String thanBuoc(String ten) {
        return cacBuoc().stream()
                .filter(b -> ten.equals(b.get("name")))
                .findFirst()
                .map(b -> String.valueOf(b.get("run")))
                .orElseGet(() -> fail("⛔ thấy bước '%s' trong %s".formatted(ten, WORKFLOW)));
    }

    private static List<String> tenCacBuoc() {
        return cacBuoc().stream()
                .map(b -> String.valueOf(b.getOrDefault("name", b.get("uses"))))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> cacBuoc() {
        return (List<Map<String, Object>>) ((Map<String, Object>) ((Map<String, Object>) new Yaml()
                                .<Map<String, Object>>load(doc(WORKFLOW))
                                .get("jobs"))
                        .get("deploy"))
                .get("steps");
    }

    private static String doc(String duongDan) {
        try {
            return Files.readString(timTuGocKho(duongDan), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
