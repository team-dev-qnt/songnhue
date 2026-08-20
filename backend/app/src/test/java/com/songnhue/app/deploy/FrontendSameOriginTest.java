package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Trình duyệt phải gọi API ở CÙNG origin với trang.</b>
 *
 * <h2>Lỗi này làm cả giao diện quản trị không dùng được, và không ai thấy suốt WS-8 → WS-20</h2>
 *
 * Backend <b>không cấu hình CORS</b> — cố ý, vì ở production nginx đứng trước cả hệ (T11.5) nên
 * admin-app và API vốn cùng origin. Nhưng {@code compose.local.yml} lại build bundle với
 * {@code VITE_API_BASE_URL=http://localhost:18080/api/v1}, trong khi giao diện phục vụ ở cổng
 * 15173. Khác cổng là <b>khác origin</b>, nên trình duyệt gửi lượt kiểm trước (preflight
 * {@code OPTIONS}) và nhận về:
 *
 * <pre>
 *   HTTP/1.1 403
 *   Invalid CORS request
 * </pre>
 *
 * <p>Hệ quả: <b>không một lượt gọi nào chạy được</b>, bắt đầu từ ô đăng nhập. Cùng lỗi ở cổng công
 * khai làm <b>bộ đếm lượt xem chưa từng chạy được từ trình duyệt thật</b>.
 *
 * <h2>⚠⚠ Vì sao mọi lượt kiểm trước đây đều xanh</h2>
 *
 * Chúng đều gọi bằng {@code curl} thẳng vào cổng backend:
 *
 * <ul>
 *   <li>WS-20: <i>"4 route CMS trả 200, API CMS chưa đăng nhập trả 401"</i> — đúng, nhưng đó là
 *       {@code curl} tới {@code :18080}. Nó chứng minh <b>backend</b> chạy, không chứng minh
 *       <b>trình duyệt</b> gọi được.
 *   <li>WS-16: <i>"bộ đếm lượt xem lên đúng 7/7"</i> — cũng {@code curl}.
 * </ul>
 *
 * <p><b>{@code curl} không làm preflight.</b> Nó không phải trình duyệt nên không có origin, không
 * có chính sách cùng nguồn, và đi lọt qua đúng bức tường chặn người dùng thật. Đây là biến thể mới
 * của bài học cũ: <i>kiểm bằng một đường khác đường mà production đi thì chưa kiểm gì cả</i> —
 * cùng họ với {@code BackupServiceTest} mock {@code PostgresToolRunner} (§9.12) và
 * {@code ViewCountService} gọi thẳng {@code day()} thay vì qua proxy (§10.20).
 *
 * <h2>Bài kiểm này chứng minh được gì, và KHÔNG chứng minh được gì</h2>
 *
 * Nó đọc <b>cấu hình</b>: có khối chuyển tiếp không, biến build có bị đặt thành địa chỉ tuyệt đối
 * không. Nó <b>không</b> thay được một lượt đăng nhập thật trên trình duyệt — muốn vậy phải có
 * trình duyệt trong CI, mà đó là một hạng mục riêng.
 *
 * <p>Thứ nó chặn là <b>quay lại đúng cấu hình đã hỏng</b>. Ghi rõ giới hạn ở đây để về sau không ai
 * đọc màu xanh của nó thành "đã kiểm giao diện".
 */
class FrontendSameOriginTest {

    @Test
    @DisplayName("⭐⭐ nginx của admin-app phải chuyển tiếp /api sang backend")
    void nginxCoChuyenTiepApi() {
        String dockerfile = doc("deploy/docker/admin-app.Dockerfile");

        assertThat(dockerfile)
                .as(
                        """
                        Thiếu khối `location /api/` thì bundle buộc phải trỏ sang một origin khác, và \
                        backend không cấu hình CORS nên preflight trả 403 — giao diện chết từ ô đăng nhập.""")
                .contains("location /api/");

        // ⚠ Không dùng `[^}]*` để giới hạn trong thân khối: giá trị `${API_UPSTREAM}` bản thân nó
        // có dấu `}`, nên phép khớp dừng sớm và bài kiểm đỏ oan. Giới hạn bằng số ký tự thay vì
        // bằng dấu đóng khối.
        assertThat(dockerfile)
                .as("có `location /api/` mà không `proxy_pass` thì nginx trả 404 cho mọi lượt gọi API")
                .containsPattern(Pattern.compile("location /api/\\s*\\{[\\s\\S]{0,400}?proxy_pass"));

        assertThat(dockerfile)
                .as("thiếu X-Forwarded-For thì mọi lượt đăng nhập trông như đến từ nginx: một người "
                        + "gõ sai mật khẩu sẽ khoá hạn mức theo IP của cả cơ quan")
                .contains("X-Forwarded-For");

        // ⚠⚠ Đo thật ở lượt dựng đầu: `proxy_pass http://app:8080` trực tiếp làm nginx phân giải
        // DNS LÚC NẠP CẤU HÌNH, backend chưa lên là `[emerg] host not found in upstream "app"` và
        // container quay vòng khởi động lại — một sự cố của backend kéo theo CẢ TRANG TRẮNG.
        assertThat(dockerfile)
                .as("phải phân giải tên máy lúc chạy: `resolver` + biến trong `proxy_pass`")
                .contains("resolver ")
                .containsPattern(Pattern.compile("proxy_pass\\s+\\$[a-z_]+;"));
    }

    @Test
    @DisplayName("⭐⭐ Cổng công khai phải chuyển tiếp /api/v1, và bằng Route Handler chứ không phải rewrites()")
    void nextCoChuyenTiepApiLucChay() {
        String route = doc("frontend/public-web/src/app/api/v1/[...path]/route.ts");

        assertThat(route)
                .as("thiếu bộ chuyển tiếp thì lượt ping đếm view gọi khác origin và bị CORS chặn")
                .contains("API_INTERNAL_BASE_URL")
                .contains("export async function POST");

        // ⚠⚠ `rewrites()` KHÔNG dùng được ở đây và đã thử rồi mới biết: với `output: 'standalone'`
        // Next gọi nó LÚC BUILD rồi ghi kết quả đã giải sẵn vào `required-server-files.json`, nên
        // `API_INTERNAL_BASE_URL` bị nướng cứng theo giá trị lúc build. Triệu chứng: container có
        // đúng biến môi trường mà log vẫn `ECONNREFUSED 127.0.0.1:8080`.
        assertThat(doc("frontend/public-web/next.config.ts"))
                .as("quay lại `rewrites()` là nướng cứng địa chỉ backend vào image — xem tài liệu "
                        + "trong `route.ts` và `next.config.ts`")
                .doesNotContain("async rewrites()");
    }

    @Test
    @DisplayName("Chế độ chạy native: Vite phải proxy /api, nếu không lượt gọi rơi vào máy chủ dev")
    void viteCoProxyApi() {
        String config = doc("frontend/admin-app/vite.config.ts");

        assertThat(config)
                .as("không có proxy thì `/api/v1/...` trả về index.html và axios báo lỗi phân tích JSON "
                        + "ở một chỗ chẳng liên quan gì tới nguyên nhân")
                .containsPattern(Pattern.compile("proxy:\\s*\\{[^}]*'/api'", Pattern.DOTALL));
    }

    /** Hai biến build quyết định trình duyệt gọi API ở origin nào. Cả hai phải RỖNG ở mọi nơi. */
    private static final String[] BIEN_BUILD_FE = {"VITE_API_BASE_URL", "NEXT_PUBLIC_API_BASE_URL"};

    @Test
    @DisplayName("⛔⛔ Tệp env — nơi QUYẾT ĐỊNH — không được gán địa chỉ tuyệt đối cho biến build FE")
    void tepEnvKhongGanDiaChiTuyetDoi() throws IOException {
        // ⚠⚠ Đây là bài kiểm sinh ra vì bản trước của chính lớp này XANH trong lúc lỗi còn sống.
        //
        // Bản trước chỉ soi `compose.local.yml` và thấy `${VITE_API_BASE_URL:-}` — mặc định rỗng,
        // đúng như mong muốn. Nhưng `:-` chỉ có tác dụng khi biến VẮNG MẶT, mà Makefile chạy
        // compose với `--env-file env/local.env`, và `--env-file` nuôi luôn phép thế biến. Tệp
        // env vẫn ghi `VITE_API_BASE_URL=http://localhost:18080/api/v1` → giá trị đó thắng →
        // bundle vẫn gọi khác origin → preflight vẫn 403 → giao diện vẫn chết.
        //
        // Đo lại bằng chính docker:
        //   docker compose --env-file env/local.env -f compose.local.yml --profile full config
        //   → VITE_API_BASE_URL: http://localhost:18080/api/v1
        //
        // Bài học: **canh giá trị đã giải, đừng canh giá trị mặc định.** Mặc định chỉ là thứ
        // dùng đến khi không ai ghi đè, mà ở đây luôn có người ghi đè.
        for (Path tep : timTatCaTepEnv()) {
            String noiDung = Files.readString(tep, StandardCharsets.UTF_8);
            for (String bien : BIEN_BUILD_FE) {
                var khop = Pattern.compile("^\\s*" + bien + "=(.*)$", Pattern.MULTILINE)
                        .matcher(noiDung);
                while (khop.find()) {
                    assertThat(khop.group(1).trim())
                            .as(
                                    """
                                    %s: `%s` phải để RỖNG.

                                    Có giá trị ở đây nghĩa là bundle FE gọi API ở một origin khác trang. \
                                    Backend cố ý không cấu hình CORS (production có nginx gộp chung \
                                    origin — T11.5), nên preflight trả `403 Invalid CORS request` và \
                                    KHÔNG một lượt gọi nào chạy được, bắt đầu từ ô đăng nhập.

                                    Để trống thì mã FE rơi về đường dẫn tương đối `/api/v1`, và tầng \
                                    phục vụ lo chuyển tiếp: nginx của image admin-app (`API_UPSTREAM`), \
                                    Route Handler của public-web (`API_INTERNAL_BASE_URL`), \
                                    `server.proxy` của Vite khi chạy native.

                                    ⚠ Giá trị bắt được: <%s>. Nếu nó trông như một chú thích thì đó là \
                                    bẫy §10.27 — Compose không cắt chú thích khi giá trị rỗng, phải \
                                    đưa chú thích xuống dòng riêng.""",
                                    tep.getFileName(), bien, khop.group(1))
                            .isEmpty();
                }
            }
        }
    }

    @Test
    @DisplayName("⛔ Mặc định của compose và Makefile cũng không được trỏ sang origin khác")
    void bienBuildKhongTroSangOriginKhac() {
        String compose = doc("deploy/compose.local.yml");

        // Bắt đúng dạng đã hỏng: `VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://...}`.
        for (String bien : BIEN_BUILD_FE) {
            assertThat(compose)
                    .as(
                            """
                            `%s` đang có giá trị mặc định là một địa chỉ TUYỆT ĐỐI. Bundle sẽ gọi khác \
                            origin, backend không cấu hình CORS, preflight trả 403 và giao diện chết. \
                            Để mặc định RỖNG để mã FE dùng đường dẫn tương đối `/api/v1`.""",
                            bien)
                    .doesNotContainPattern(Pattern.compile(bien + ":\\s*\\$\\{" + bien + ":-\\s*https?://"));
        }

        // `make dev-fe` (FE trong Docker, backend native) từng tiêm thẳng địa chỉ cổng 8080 vào
        // lúc build — cùng một lỗi, ở một chỗ thứ ba. Chế độ đó chỉ được đổi ĐÍCH CHUYỂN TIẾP.
        //
        // ⚠ Phải bỏ dòng chú thích trước khi khớp. Bản đầu của chính phép kiểm này ĐỎ OAN: trong
        // Makefile có một chú thích *mô tả lại lỗi cũ*, chứa nguyên văn
        // `VITE_API_BASE_URL=http://localhost:8080/api/v1`. Một phép canh trượt trên tài liệu
        // giải thích chính nó là phép canh soi VĂN BẢN thay vì soi CẤU TRÚC — đúng thứ đã bị cấm
        // sau vụ `articleContentCss.test.ts`.
        String makefile = boChuThichMakefile(doc("Makefile"));
        for (String bien : BIEN_BUILD_FE) {
            assertThat(makefile)
                    .as(
                            "`make dev-fe` không được gán địa chỉ tuyệt đối cho `%s`; hãy đổi "
                                    + "`API_UPSTREAM` / `API_INTERNAL_BASE_URL` thay vì biến build",
                            bien)
                    .doesNotContainPattern(Pattern.compile(bien + "=\"?\\$?\\{?\\$?https?://"))
                    .doesNotContainPattern(Pattern.compile(bien + "=\"\\$\\$api\""));
        }
    }

    @Test
    @DisplayName("⛔ Mặc định của mã FE phải là đường dẫn tương đối, và phải chịu được chuỗi rỗng")
    void macDinhCuaMaFeLaTuongDoi() {
        String apiClient = doc("frontend/admin-app/src/shared/apiClient.ts");
        String site = doc("frontend/public-web/src/lib/site.ts");

        // ⚠ `??` KHÔNG đủ: compose truyền biến để trống thì Vite/Next nhúng vào bundle một CHUỖI
        // RỖNG, mà chuỗi rỗng không phải nullish nên `??` giữ nguyên nó → baseURL = '' → lượt gọi
        // mất hẳn tiền tố `/api/v1`. Phải là `||`.
        assertThat(apiClient)
                .as("dùng `??` thì chuỗi rỗng đi lọt và baseURL thành '' — xem tài liệu trong tệp đó")
                .contains("import.meta.env.VITE_API_BASE_URL || '/api/v1'");

        assertThat(site)
                .as("cùng bẫy chuỗi rỗng ở phía cổng công khai")
                .contains("process.env.NEXT_PUBLIC_API_BASE_URL || '/api/v1'");

        // Địa chỉ phía MÁY CHỦ thì ngược lại — bắt buộc tuyệt đối, vì `fetch('/api/v1/...')` ở
        // Node không có gốc để nối.
        assertThat(site)
                .as("địa chỉ nội bộ mà rơi về đường dẫn tương đối thì Next dựng ra trang rỗng")
                .containsPattern(Pattern.compile("API_INTERNAL_BASE_URL\\s*\\|\\|\\s*'https?://"));
    }

    // -------------------------------------------------------------------------

    /** Bỏ mọi dòng chú thích của Makefile, kể cả dạng {@code @#} nằm trong thân recipe. */
    private static String boChuThichMakefile(String noiDung) {
        return noiDung.lines()
                .filter(dong -> !dong.stripLeading().replaceFirst("^@", "").startsWith("#"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * Mọi tệp env trong {@code deploy/env/} — cả bản {@code .example} có trong repo lẫn bản
     * {@code local.env} thật của từng máy (không commit).
     *
     * <p>Cố ý quét cả bản không commit: ở CI nó không tồn tại nên bài kiểm chỉ soi các bản mẫu,
     * còn ở máy lập trình viên nó bắt được đúng tệp đang thật sự nuôi {@code docker compose}.
     * Chính tệp đó là nơi lỗi CORS sống sót qua lần sửa trước.
     */
    private static List<Path> timTatCaTepEnv() throws IOException {
        Path thuMuc = timTuGocKho("deploy/env");
        try (Stream<Path> luot = Files.list(thuMuc)) {
            List<Path> ketQua = luot.filter(p -> p.getFileName().toString().contains(".env"))
                    .sorted()
                    .toList();
            assertThat(ketQua)
                    .as("không thấy tệp env nào trong %s — bài kiểm sẽ soi tập rỗng", thuMuc)
                    .isNotEmpty();
            return ketQua;
        }
    }

    private static String doc(String duongDanTuongDoi) {
        Path duongDan = timTuGocKho(duongDanTuongDoi);
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
