package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.app.testsupport.TestHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.infra.identity.UserRepository;

/**
 * ⭐⭐ <b>T61.49 — ba mục ASVS mà bảng tự đánh giá ghi <i>"Chưa đo"</i> vì ⛔ có bài kiểm nào.</b>
 *
 * <h2>Vì sao ba mục này đi chung một lớp</h2>
 *
 * <p>Cả ba hỏi <b>cùng một câu</b>: <i>hệ có hiểu một đầu vào ĐÃ MÃ HOÁ theo một cách xác định ⛔</i> —
 * tham số lặp lại (5.1.1), ký tự ngoài BMP (5.3.2), đường dẫn mang {@code %XX} (13.1.1). Chúng khác
 * nhau ở chỗ mã hoá nằm đâu, ⛔ khác nhau ở rủi ro: mỗi lần hai tầng hiểu khác nhau là một lần bộ lọc
 * của tầng trước ⛔ còn nói đúng về thứ tầng sau sẽ làm.
 *
 * <h2>⛔⛔ Cả ba HÔM NAY ĐỀU ĐẠT — và đó chính là lý do phải canh</h2>
 *
 * <p>Đo ngày 24/09/2026 trên hệ thật: ⛔ mục nào hỏng. Nhưng cả ba đang đúng nhờ <b>MẶC ĐỊNH</b> của
 * Tomcat / Spring / Jackson, ⛔ nhờ một dòng khai nào trong kho — bốn phép {@code grep}
 * ({@code StrictHttpFirewall}, {@code encodedSolidusHandling}, {@code getParameterValues},
 * {@code characterEncoding}) đều trả <b>0</b>. Luật 3 của kho nói thẳng: <i>canh giá trị ĐÃ GIẢI,
 * đừng canh giá trị MẶC ĐỊNH</i> — và {@code T11.69} đã trả giá đúng chuyện này khi Jackson 3 <b>đảo</b>
 * mặc định {@code WRITE_DATES_AS_TIMESTAMPS} trong một lượt nâng biên dịch hoàn toàn sạch.
 *
 * <p>⇒ Ba bài dưới đây ⛔ vá gì. Việc của chúng là biến ba mặc định <b>vô hình</b> thành ba khẳng định
 * <b>đỏ được</b>, để lượt nâng khung kế tiếp ⛔ lặng lẽ đảo chúng.
 *
 * <h2>Mỗi bài mang vế phân biệt riêng (luật 9)</h2>
 *
 * <ul>
 *   <li><b>5.1.1</b> — hỏi <i>hai</i> thứ tự đảo nhau. Một chiều thôi thì {@code size=1} có thể trùng
 *       khớp với một mặc định và bài xanh vì lý do sai.
 *   <li><b>5.3.2</b> — tiền đề đòi chuỗi thử THỰC SỰ có cặp thay thế; đổi nó thành chuỗi BMP thuần là
 *       bài đỏ ngay, ⛔ phải xanh trong im lặng.
 *   <li><b>13.1.1</b> — đòi hai mã HTTP <b>KHÁC NHAU</b> cho {@code a%2Fb} và {@code a/b}. Ngày chúng
 *       bằng nhau là ngày {@code %2F} đã được giải thành {@code /} trước khi tới Spring.
 * </ul>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DauVaoMaHoaHieuXacDinhHttpTest extends IntegrationTestBase {

    /** Mã đơn vị đồ gá — so BẰNG, ⛔ {@code LIKE}: một mẫu dọn cũng là một phép so (T74.1). */
    private static final String MA_DON_VI = "ASVS532XN";

    /**
     * {@code 🚧} (U+1F6A7) và {@code 𠮷} (U+20BB7) — cả hai nằm NGOÀI BMP nên trong UTF-16 mỗi ký tự
     * chiếm <b>hai</b> {@code char}. Đó là chỗ một phép cắt theo {@code char} xé đôi cặp thay thế.
     */
    private static final String NGOAI_BMP = "Cống 🚧 𠮷";

    @Autowired
    private TestHttp http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Environment env;

    private PhienHttp phienHttp;
    private PhienHttp.Phien phien;

    /** ⛔ Vai trò {@code SUPER_ADMIN}: nhóm ấy bắt buộc 2FA ⇒ lượt đăng nhập dừng ở bước ghi danh và
     * bài đỏ vì <b>lý do sai</b> (bài học đã trả giá ở {@code DinhDangNgayTrenDayTest}). */
    @BeforeAll
    void dangNhap() {
        phienHttp = new PhienHttp(http);
        String username = PhienHttp.taoNguoiDung(users, passwords, jdbc, "asvs_t6149");
        jdbc.update(
                "INSERT INTO roles (code, name) VALUES ('VT_ASVS_T6149', 'Kiểm thử T61.49') ON CONFLICT DO NOTHING");
        for (String quyen : new String[] {"adm:audit:view", "adm:org-unit:manage", "adm:org-unit:view"}) {
            jdbc.update(
                    """
                    INSERT INTO role_permissions (role_id, permission_id)
                    SELECT r.id, p.id FROM roles r, permissions p
                    WHERE r.code = 'VT_ASVS_T6149' AND p.code = ? ON CONFLICT DO NOTHING
                    """,
                    quyen);
        }
        jdbc.update(
                """
                INSERT INTO user_roles (user_id, role_id)
                SELECT u.id, r.id FROM users u, roles r
                WHERE u.username = ? AND r.code = 'VT_ASVS_T6149' ON CONFLICT DO NOTHING
                """,
                username);
        phien = phienHttp.dangNhap(username);
    }

    @Test
    @DisplayName("⭐⭐ ASVS 5.1.1 — tham số LẶP LẠI được hiểu xác định: luôn lấy giá trị ĐẦU, ⛔ phụ thuộc thứ tự")
    void thamSoTrungLapLayGiaTriDau() {
        // Tiền đề: endpoint phải THỰC SỰ đọc `size`. Thiếu vế này thì một endpoint bỏ qua `size`
        // hoàn toàn cũng làm mọi khẳng định dưới xanh (luật 7 — phép kiểm chạy trên tập rỗng).
        assertThat(kichThuocTrang("?size=1"))
                .as("endpoint ⛔ đọc `size` ⇒ cả bài này ⛔ khẳng định gì")
                .isEqualTo(1);
        assertThat(kichThuocTrang("?size=3")).isEqualTo(3);

        // ⭐ Vế phân biệt: hỏi CẢ HAI thứ tự. Một chiều thôi thì `size=1` có thể trùng với một mặc
        //   định nào đó; hai chiều cho hai kết quả ĐẢO theo mới chứng minh nó đọc phần tử ĐẦU.
        assertThat(kichThuocTrang("?size=1&size=3"))
                .as("⛔⛔ `?size=1&size=3` phải lấy 1 — lấy 3 nghĩa là tầng nào đó ưu tiên giá trị CUỐI")
                .isEqualTo(1);
        assertThat(kichThuocTrang("?size=3&size=1"))
                .as("⛔⛔ và đảo thứ tự phải đảo kết quả — hai lượt ra CÙNG một số nghĩa là `size` ⛔ còn"
                        + " do tham số quyết định")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("⭐⭐ ASVS 5.3.2 — ký tự NGOÀI BMP đi trọn vòng HTTP → Postgres → HTTP mà ⛔ méo")
    void kyTuNgoaiBmpDiTronVong() {
        // Tiền đề: chuỗi thử phải THỰC SỰ có cặp thay thế. Đổi nó thành chuỗi BMP thuần ⇒ bài đỏ
        // ngay tại đây, thay vì xanh trong im lặng và thôi canh gì (luật 1).
        assertThat(NGOAI_BMP.codePointCount(0, NGOAI_BMP.length()))
                .as("chuỗi thử phải có ít nhất một ký tự NGOÀI BMP — ⛔ thì bài này chỉ canh chữ thường")
                .isLessThan(NGOAI_BMP.length());

        jdbc.update("DELETE FROM org_units WHERE code = ?", MA_DON_VI);
        UUID goc = jdbc.queryForObject("SELECT public_id FROM org_units WHERE parent_id IS NULL", UUID.class);

        ResponseEntity<String> tao = phienHttp.goi(
                phien,
                HttpMethod.POST,
                "/api/v1/org-units",
                """
                {"code":"%s","name":"%s","shortName":"XN ASVS","unitType":"XI_NGHIEP","parentPublicId":"%s"}"""
                        .formatted(MA_DON_VI, NGOAI_BMP, goc));
        assertThat(tao.getStatusCode()).as("tạo đơn vị: %s", tao.getBody()).isEqualTo(HttpStatus.CREATED);

        // Vế 1 — CSDL. ⛔ chỉ hỏi thân trả về: một DTO có thể vọng lại đúng chuỗi vừa nhận mà chưa
        // hề ghi xuống đâu (đúng bài học `shortName` ở `OrgUnitLeaderHttpTest`).
        assertThat(jdbc.queryForObject("SELECT name FROM org_units WHERE code = ?", String.class, MA_DON_VI))
                .as("⛔⛔ Postgres/JDBC làm méo cặp thay thế — cột `name` ⛔ còn giữ nguyên chuỗi đã gửi")
                .isEqualTo(NGOAI_BMP);

        // Vế 2 — đường ĐỌC qua HTTP. Hai vế ⛔ thay thế nhau: CSDL đúng mà JSON méo thì người dùng
        // vẫn thấy sai, và ngược lại.
        UUID id = jdbc.queryForObject("SELECT public_id FROM org_units WHERE code = ?", UUID.class, MA_DON_VI);
        ResponseEntity<String> doc = phienHttp.get(phien, "/api/v1/org-units/" + id);
        assertThat(doc.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(doc.getBody())
                .as("⛔⛔ JSON trả về ⛔ còn mang nguyên chuỗi — kiểm cả escape `\\ud83d` lẫn ký tự thật")
                .contains(NGOAI_BMP);

        jdbc.update("DELETE FROM org_units WHERE code = ?", MA_DON_VI);
    }

    @Test
    @DisplayName("⭐⭐ ASVS 13.1.1 — `%2F` ⛔ được giải thành `/`: hai mã HTTP phải KHÁC NHAU")
    void duongDanMaHoaHieuGiongNhauOMoiTang() throws Exception {
        int maHoa = maCuaDuongDanTho("/api/v1/org-units/a%2Fb");
        int thuong = maCuaDuongDanTho("/api/v1/org-units/a/b");

        // ⭐⭐ Vế phân biệt của cả bài: nếu `%2F` được giải thành `/` trước khi tới định tuyến thì
        //    hai đường dẫn này là MỘT, và hai mã sẽ bằng nhau. Khẳng định "khác nhau" bắt đúng
        //    khoảnh khắc bức tường sập — mạnh hơn hẳn việc chốt cứng con số 400.
        assertThat(maHoa)
                .as("⛔⛔ `a%%2Fb` và `a/b` trả CÙNG một mã ⇒ `%%2F` đã thành dấu gạch chéo thật ⇒ mọi"
                        + " phép kiểm đường dẫn ở tầng trước (nginx, proxy Next) ⛔ còn nói đúng về"
                        + " thứ Spring sẽ định tuyến")
                .isNotEqualTo(thuong);
        assertThat(maHoa)
                .as("đường dẫn mang `%%2F` phải bị TỪ CHỐI, ⛔ phải đi tiếp tới tầng nghiệp vụ")
                .isEqualTo(400);
        assertThat(thuong)
                .as("đối chứng: cùng hình dạng nhưng gạch chéo THẬT thì đi tới định tuyến và ⛔ thấy dữ liệu")
                .isEqualTo(404);

        // Đối chứng thứ hai: ⛔ phải mọi `%XX` đều bị chặn — `%2D` (dấu `-`) vẫn giải bình thường.
        // Thiếu vế này thì một bản vá thô bạo (chặn sạch mọi `%`) cũng làm bài trên xanh.
        assertThat(maCuaDuongDanTho("/api/v1/org%2Dunits"))
                .as("`%%2D` là dấu gạch nối thường ⇒ phải giải bình thường; chặn sạch mọi `%%XX` là vá quá tay")
                .isNotEqualTo(400);
    }

    // ---- bộ đo ------------------------------------------------------------------

    /** {@code meta.size} của phản hồi phân trang — con số Spring THỰC SỰ dùng, ⛔ phải con số ta gửi. */
    private int kichThuocTrang(String truyVan) {
        ResponseEntity<String> r = phienHttp.get(phien, "/api/v1/audit-logs" + truyVan);
        assertThat(r.getStatusCode()).as("đọc nhật ký: %s", r.getBody()).isEqualTo(HttpStatus.OK);
        String than = r.getBody() == null ? "" : r.getBody();
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("\"size\"\\s*:\\s*(\\d+)").matcher(than);
        assertThat(m.find()).as("⛔ thấy `meta.size` trong thân: %s", than).isTrue();
        return Integer.parseInt(m.group(1));
    }

    /**
     * Gửi đường dẫn <b>NGUYÊN VĂN</b>.
     *
     * <p>⛔⛔ Cố ý ⛔ dùng {@link TestHttp}: nó đi qua {@code DefaultUriBuilderFactory}, thứ sẽ
     * <b>mã hoá lại</b> dấu {@code %} thành {@code %25} ⇒ lượt gửi ⛔ bao giờ mang được một
     * {@code %2F} thật tới máy chủ, và bài kiểm sẽ khẳng định về một chuỗi khác hẳn thứ nó định hỏi.
     */
    private int maCuaDuongDanTho(String duongDanTho) throws Exception {
        String goc = "http://localhost:" + env.getProperty("local.server.port");
        HttpRequest yeuCau = HttpRequest.newBuilder(URI.create(goc + duongDanTho))
                .header("Authorization", "Bearer " + phien.accessToken())
                .header("Cookie", phien.cookie())
                .GET()
                .build();
        HttpResponse<String> traLoi = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .build()
                .send(yeuCau, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return traLoi.statusCode();
    }
}
