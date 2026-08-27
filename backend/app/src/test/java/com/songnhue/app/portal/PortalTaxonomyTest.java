package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cây nội dung của cổng nằm ở <b>hai nơi</b>, và hai nơi đó không được lệch nhau.
 *
 * <ol>
 *   <li>{@code V202608271031__cms_site_taxonomy_v2.sql} — cột {@code menu_items.url} trong CSDL;
 *   <li>{@code frontend/public-web/src/lib/routes.ts} — tuyến đường mà Next thật sự phục vụ.
 * </ol>
 *
 * <h2>Vì sao cần một bài kiểm cho việc này</h2>
 *
 * Một mục menu trỏ vào tuyến đường không tồn tại <b>không gây lỗi ở đâu cả</b>: migration chạy
 * xanh, {@code next build} xanh, mọi bộ test xanh. Nó chỉ hiện ra khi một người dùng thật bấm vào
 * mục ấy và nhận 404 — đúng hình dạng §10.54, nơi cổng quảng cáo những khu vực bấm vào là không
 * có. Bảy tuyến đường mới của CR-02 và CR-05 đều thuộc loại này.
 *
 * <p>Quy tắc 14 của dự án: <i>chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ
 * hộ</i>. Cùng họ với {@code error-map.test.ts} (mã lỗi BE ↔ FE) và {@code EditorVocabularyTest}.
 *
 * <h2>⚠ Canh cả HAI chiều</h2>
 *
 * Chiều "menu → có trang" bắt lỗi 404. Chiều ngược lại — "trang → có ai dẫn tới" — bắt một loại
 * lãng phí im lặng hơn: một trang được dựng, được kiểm, được triển khai, mà không lối vào nào.
 */
class PortalTaxonomyTest {

    private static final String MIGRATION =
            "backend/content/src/main/resources/db/migration/cms/V202608271031__cms_site_taxonomy_v2.sql";

    private static final String SETTINGS_MIGRATION =
            "backend/content/src/main/resources/db/migration/cms/V202608271032__cms_portal_settings_v2.sql";

    private static final String ROUTES_TS = "frontend/public-web/src/lib/routes.ts";

    // ---- Vị trí menu: ba nơi phải nhớ cùng một danh sách -----------------------

    private static final String ENUM_VI_TRI =
            "backend/content/src/main/java/com/songnhue/content/domain/MenuPosition.java";

    private static final String KIEU_VI_TRI_FE = "frontend/admin-app/src/features/cms/types.ts";

    private static final String MIGRATION_VI_TRI =
            "backend/content/src/main/resources/db/migration/cms/V202608281036__cms_menu_position_lien_ket.sql";

    /** Chỉ những mục `link_type = 'URL'` trỏ vào chính cổng — bỏ qua liên kết ra ngoài. */
    private static final Pattern URL_NOI_BO = Pattern.compile("'(/[a-z0-9\\-/]*)'");

    @Test
    @DisplayName("⛔ Mọi đường dẫn nội bộ trong menu đều có mặt ở ROUTES — không mục nào trỏ vào 404")
    void menuKhongTroVaoTuyenDuongKhongTonTai() {
        String routes = doc(ROUTES_TS);
        List<String> thieu = duongDanTrongMenu().stream()
                .filter(duong -> !routes.contains("'" + duong + "'"))
                .toList();

        assertThat(thieu)
                .as(
                        """
                        Những đường dẫn này nằm trong `menu_items.url` mà KHÔNG có trong `ROUTES` của \
                        public-web: %s

                        Một mục menu trỏ vào tuyến đường không tồn tại không làm đỏ bất cứ thứ gì — \
                        migration xanh, next build xanh — và chỉ lộ ra khi người dùng thật bấm vào và \
                        nhận 404 (§10.54).""",
                        thieu)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Mọi tuyến đường mới của ROUTES đều có một mục menu dẫn tới")
    void moiTuyenDuongDeuCoLoiVao() {
        Set<String> trongMenu = Set.copyOf(duongDanTrongMenu());
        List<String> khongAiDan = duongDanTrongRoutes().stream()
                .filter(duong -> !trongMenu.contains(duong))
                .toList();

        assertThat(khongAiDan)
                .as(
                        """
                        Những tuyến đường này được dựng ở public-web nhưng KHÔNG mục menu nào dẫn tới: %s

                        Đó là một trang được viết, được kiểm, được triển khai mà không có lối vào — \
                        loại lãng phí không ai phát hiện, vì mọi cổng kiểm đều xanh.""",
                        khongAiDan)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Địa chỉ hệ thống văn bản điều hành khớp giữa `settings` và `menu_items` (CR-07)")
    void diaChiHeThongVanBanKhongLech() {
        String trongMenu = timDuy(
                doc(MIGRATION), Pattern.compile("'EXTERNAL_DOC',\\s*\\n?\\s*'(https?://[^']+)'"), "menu_items.url");
        String trongSettings = timDuy(
                doc(SETTINGS_MIGRATION),
                Pattern.compile("'site\\.external\\.doc-system-url',\\s*'(https?://[^']+)'"),
                "settings['site.external.doc-system-url']");

        assertThat(trongMenu)
                .as(
                        """
                        Cùng một địa chỉ nằm ở HAI bảng: `menu_items.url` của mục "Văn bản điều hành" \
                        và khoá `site.external.doc-system-url`. Chân trang và sidebar đọc khoá settings, \
                        còn menu đọc cột url — nên lệch nhau là hai nút cạnh nhau trên cùng một trang \
                        mở sang hai hệ thống khác nhau, và không có lỗi nào báo (quy tắc 14).""")
                .isEqualTo(trongSettings);
    }

    @Test
    @DisplayName("⚠ Bài kiểm thật sự đọc được cả hai nguồn — chạy qua tập rỗng thì xanh mà không canh gì")
    void doDuocCaHaiNguon() {
        // Luật 7. Nếu migration đổi tên hay `ROUTES` đổi cách khai, ba bài trên sẽ so hai tập RỖNG
        // với nhau và xanh trọn vẹn — đúng thứ đã xảy ra với ArchUnit suốt Phase 0.
        assertThat(duongDanTrongMenu())
                .as("không trích được đường dẫn nào từ %s", MIGRATION)
                .hasSizeGreaterThanOrEqualTo(7);
        assertThat(duongDanTrongRoutes())
                .as("không trích được tuyến đường nào từ %s", ROUTES_TS)
                .hasSizeGreaterThanOrEqualTo(7);
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: bộ canh bắt được một mục menu trỏ vào tuyến đường không có")
    void kiemChungNguoc() {
        String routesGia = "export const ROUTES = { lienHe: '/lien-he' } as const;";
        List<String> thieu = List.of("/gioi-thieu/lanh-dao", "/lien-he").stream()
                .filter(duong -> !routesGia.contains("'" + duong + "'"))
                .toList();
        assertThat(thieu).containsExactly("/gioi-thieu/lanh-dao");
    }

    // ---- Trích dữ liệu -------------------------------------------------------

    /**
     * Đường dẫn nội bộ mà menu trỏ tới.
     *
     * <p>⚠ Bỏ {@code '/'} (Trang chủ) — nó là gốc, không phải một tuyến đường khai trong
     * {@code ROUTES.gioiThieu}/{@code ROUTES.quanLyVanHanh}.
     */
    /**
     * <b>Ba nơi nhớ cùng một danh sách vị trí menu — quy tắc 14.</b>
     *
     * <p>Thêm {@code LIEN_KET} ngày 28/08/2026 đòi sửa đúng ba chỗ: enum Java, kiểu TypeScript của
     * màn hình quản trị, và ràng buộc {@code ck_menu_items_position} trong CSDL. Quên chỗ nào thì
     * triệu chứng khác nhau và không chỗ nào chỉ về nguyên nhân:
     *
     * <ul>
     *   <li>quên <b>enum</b> → Jackson ném lỗi lúc đọc dòng từ CSDL, ở một endpoint không liên quan;
     *   <li>quên <b>kiểu FE</b> → ô chọn thiếu một lựa chọn, không lỗi nào, không ai biết đã mất gì;
     *   <li>quên <b>CHECK</b> → lưu thất bại với một lỗi ràng buộc CSDL thô, sau khi người dùng đã
     *       nhập xong biểu mẫu.
     * </ul>
     *
     * <p>⚠ Đọc ràng buộc từ <b>migration mới nhất chạm tới nó</b>, không từ {@code V202608191019}:
     * ràng buộc đã bị {@code DROP} rồi {@code ADD} lại, nên tệp cũ vẫn ghi danh sách hai phần tử và
     * đối chiếu với nó sẽ đỏ vì một lý do sai.
     */
    @Test
    @DisplayName("⛔ Ba nơi khai vị trí menu không được lệch nhau — enum · kiểu FE · ràng buộc CSDL")
    void viTriMenuKhongLech() {
        Set<String> tuEnum = trichKhop(doc(ENUM_VI_TRI), Pattern.compile("(?m)^ {4}([A-Z_]{3,})\\s*(?:[,;]|$)"));
        Set<String> tuFe = trichKhop(
                timDuy(doc(KIEU_VI_TRI_FE), Pattern.compile("export type MenuPosition = ([^;]+);"), "kiểu FE"),
                Pattern.compile("'([A-Z_]+)'"));
        Set<String> tuCsdl = trichKhop(
                timDuy(
                        doc(MIGRATION_VI_TRI),
                        Pattern.compile("ck_menu_items_position CHECK \\(position IN \\(([^)]*)\\)"),
                        "ràng buộc CSDL"),
                Pattern.compile("'([A-Z_]+)'"));

        // Luật 7 — ba mẫu trên khớp 0 lần thì ba tập đều rỗng và ba tập rỗng thì "bằng nhau".
        assertThat(tuEnum).as("không trích được giá trị nào từ %s", ENUM_VI_TRI).hasSizeGreaterThanOrEqualTo(3);

        assertThat(tuFe).as("kiểu FE lệch enum backend").isEqualTo(tuEnum);
        assertThat(tuCsdl).as("ràng buộc CSDL lệch enum backend").isEqualTo(tuEnum);
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: ba mẫu trích vị trí menu bắt đúng thứ chúng phải bắt")
    void kiemChungNguocViTriMenu() {
        // ⚠ Hằng CUỐI của một enum Java không có dấu phẩy. Mẫu ở bản đầu đòi `[,;]` nên nó bỏ sót
        //    đúng hằng vừa thêm, và bài kiểm chứng ngược bản đầu khẳng định `("HEADER", "FOOTER")` —
        //    tức là nó **chép lại hành vi sai thay vì bắt nó**, và sẽ xanh mãi mãi. Bài chính
        //    `viTriMenuKhongLech` mới là chỗ lộ ra (2 < 3), nhờ khẳng định số lượng tối thiểu.
        assertThat(trichKhop(
                        "public enum MenuPosition {\n    HEADER,\n    FOOTER,\n    LIEN_KET\n}",
                        Pattern.compile("(?m)^ {4}([A-Z_]{3,})\\s*(?:[,;]|$)")))
                .as("hằng cuối không có dấu phẩy vẫn phải bắt được")
                .containsExactlyInAnyOrder("HEADER", "FOOTER", "LIEN_KET");
        assertThat(trichKhop("'HEADER' | 'FOOTER' | 'LIEN_KET'", Pattern.compile("'([A-Z_]+)'")))
                .containsExactlyInAnyOrder("HEADER", "FOOTER", "LIEN_KET");
    }

    private static Set<String> trichKhop(String nguon, Pattern mau) {
        return mau.matcher(nguon).results().map(r -> r.group(1)).collect(Collectors.toSet());
    }

    private static List<String> duongDanTrongMenu() {
        Matcher m = Pattern.compile("'(?:URL)',\\s*'(/[a-z0-9\\-/]+)'").matcher(doc(MIGRATION));
        List<String> ket = new java.util.ArrayList<>();
        while (m.find()) {
            ket.add(m.group(1));
        }
        // Dạng thứ hai: bảng VALUES của mục con — ('Nhãn', '/duong-dan', thu_tu)
        Matcher bang =
                Pattern.compile("\\('[^']+',\\s*'(/[a-z0-9\\-/]+)',\\s*\\d+\\)").matcher(doc(MIGRATION));
        while (bang.find()) {
            ket.add(bang.group(1));
        }
        return ket.stream().filter(d -> !"/".equals(d)).distinct().sorted().toList();
    }

    /** Tuyến đường khai trong hai nhóm lồng của `ROUTES` cộng với `lienHe`. */
    private static List<String> duongDanTrongRoutes() {
        String nguon = doc(ROUTES_TS);
        int batDau = nguon.indexOf("gioiThieu: {");
        int ketThuc = nguon.indexOf("} as const;", batDau);
        if (batDau < 0 || ketThuc < 0) {
            return fail("không tìm thấy khối tuyến đường mới trong " + ROUTES_TS);
        }
        Matcher m = URL_NOI_BO.matcher(nguon.substring(batDau, ketThuc));
        return m.results().map(r -> r.group(1)).distinct().sorted().collect(Collectors.toList());
    }

    private static String timDuy(String nguon, Pattern mau, String ten) {
        Matcher m = mau.matcher(nguon);
        if (!m.find()) {
            return fail("không tìm thấy %s — bài kiểm sẽ so hai giá trị rỗng".formatted(ten));
        }
        return m.group(1);
    }

    // ---- Đọc tệp từ gốc kho --------------------------------------------------

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
