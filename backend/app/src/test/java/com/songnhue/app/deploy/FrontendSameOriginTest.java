package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

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

    @Test
    @DisplayName("⛔ Biến build của FE không được trỏ sang origin khác")
    void bienBuildKhongTroSangOriginKhac() {
        String compose = doc("deploy/compose.local.yml");

        // Bắt đúng dạng đã hỏng: `VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://...}`.
        // Mặc định phải rỗng để mã FE rơi về đường dẫn tương đối.
        for (String bien : new String[] {"VITE_API_BASE_URL", "NEXT_PUBLIC_API_BASE_URL"}) {
            assertThat(compose)
                    .as(
                            """
                            `%s` đang có giá trị mặc định là một địa chỉ TUYỆT ĐỐI. Bundle sẽ gọi khác \
                            origin, backend không cấu hình CORS, preflight trả 403 và giao diện chết. \
                            Để mặc định RỖNG để mã FE dùng đường dẫn tương đối `/api/v1`.""",
                            bien)
                    .doesNotContainPattern(Pattern.compile(bien + ":\\s*\\$\\{" + bien + ":-\\s*https?://"));
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
