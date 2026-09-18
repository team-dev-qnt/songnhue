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

    /**
     * ⛔⛔ Menu ⛔ KHÔNG chỉ nằm ở một tệp — và bộ canh này từng tin là có (luật 28).
     *
     * <p>{@link #MIGRATION} là tệp dựng cây menu gốc (G14, 27/08). Mọi mục thêm SAU đó nằm ở tệp
     * khác, và {@code duongDanTrongMenu()} bản trước chỉ đọc <b>một</b> tệp ⇒ một mục menu mới ⛔
     * không được bộ canh nhìn thấy, còn trang nó dẫn tới vẫn bị báo là <i>"⛔ không ai dẫn tới"</i>.
     *
     * <p>⚠ Cùng hình dạng {@code PortalSettingsReadTest} đã mắc ở §10.62 — <i>soi mỗi một tệp
     * migration nên mọi khoá seed trước đó đi lọt</i>. Bộ canh đúng luật, hẹp hơn nơi nó phải chặn.
     *
     * <p>⛔ Và ⛔ KHÔNG đổi {@link #MIGRATION} thành một danh sách: {@code diaChiHeThongVanBanKhongLech()}
     * truyền nó thẳng vào {@code doc(...)} để trích đúng <b>một</b> địa chỉ {@code EXTERNAL_DOC}.
     * Đổi kiểu là gãy BIÊN DỊCH cả module {@code app} — mà thông báo lỗi sẽ trỏ vào một bài kiểm
     * ⛔ không liên quan gì tới menu.
     */
    private static final List<String> MIGRATION_MENU_BO_SUNG =
            List.of("backend/content/src/main/resources/db/migration/cms/V202609081070__cms_menu_gop_y.sql");

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

    /**
     * ⚠⚠ <b>Lối vào KHÔNG phải mục menu — và mỗi mục ở đây phải ĐO ĐƯỢC.</b>
     *
     * <p>Bộ canh này ra đời với giả định <i>"menu là lối vào duy nhất"</i>, và giả định ấy đúng
     * cho bảy tuyến của CR-02/CR-05. Nó thôi đúng ở T36.8: {@code /gop-y} (CN-01.6) cố ý ⛔
     * <b>không</b> vào menu — cây danh mục + menu nhận qua §3 văn bản nghiệm thu (G14) ⛔ không có
     * mục "Góp ý", và menu là <b>dữ liệu có CRUD của khách</b> (quy tắc 16). Chèn một mục bằng
     * migration là ta tự quyết hộ Công ty bố cục cổng của họ.
     *
     * <p>⛔⛔ Nhưng một danh sách miễn trừ TRẦN thì <b>xoá mất</b> chính điều bộ canh bảo vệ: nó
     * biến "tôi đã nghĩ tới" thành "tôi được phép quên". Nên mỗi mục ở đây khai <b>tệp thật sự
     * chứa lối vào</b>, và {@link #moiTuyenDuongDeuCoLoiVao()} <b>mở tệp ấy ra đọc</b>. Gỡ liên
     * kết ở {@code lien-he} thì bài này ĐỎ — sự miễn trừ ⛔ không sống lâu hơn cái nó miễn trừ.
     *
     * <p>⇒ Luật 28: bộ canh phải nói ra phạm vi của chính nó. Bản trước khẳng định một điều rộng
     * hơn thứ nó đo được.
     */
    /**
     * Tuyến đường có lối vào <b>⛔ không phải menu</b> — khai kèm TỆP chứa liên kết ấy.
     *
     * <p>⭐ <b>Rỗng từ 08/09/2026 (T28.53), và rỗng là trạng thái TỐT NHẤT.</b> Mục duy nhất từng
     * nằm đây là {@code /gop-y}, miễn trừ với lý do <i>"có một liên kết trong thân trang
     * /lien-he"</i>. Nay nó là menu con thật của "Liên hệ", nên dòng miễn trừ ⛔ không còn đúng —
     * và một sự miễn trừ sống lâu hơn cái nó miễn trừ chính là thứ javadoc của bài này cảnh báo.
     *
     * <p>⚠ Bản trước khẳng định {@code isNotEmpty()} để chống vòng lặp chạy 0 lần (luật 7). Khẳng
     * định ấy nay <b>sai hướng</b>: nó biến trạng thái tốt nhất thành một bài kiểm đỏ, và áp lực
     * dễ nhất để chữa là <i>giữ lại một dòng miễn trừ đã chết cho hết đỏ</i>. Thay bằng
     * {@link #coCheMienTruVanBatDuocViPham()} — một bài <b>tự-kiểm-chứng</b> chạy cơ chế trên dữ
     * liệu dựng sẵn, nên nó chứng minh được cơ chế mà ⛔ không đòi phải tồn tại một ngoại lệ thật.
     */
    private static final java.util.Map<String, String> LOI_VAO_NGOAI_MENU = java.util.Map.of();

    @Test
    @DisplayName("⛔ Mọi tuyến đường mới của ROUTES đều có một lối vào — menu, hoặc một liên kết ĐO ĐƯỢC")
    void moiTuyenDuongDeuCoLoiVao() {
        Set<String> trongMenu = Set.copyOf(duongDanTrongMenu());
        List<String> khongAiDan = duongDanTrongRoutes().stream()
                .filter(duong -> !trongMenu.contains(duong))
                .filter(duong -> !LOI_VAO_NGOAI_MENU.containsKey(duong))
                .toList();

        assertThat(khongAiDan)
                .as(
                        """
                        Những tuyến đường này được dựng ở public-web nhưng KHÔNG mục menu nào dẫn tới: %s

                        Đó là một trang được viết, được kiểm, được triển khai mà không có lối vào — \
                        loại lãng phí không ai phát hiện, vì mọi cổng kiểm đều xanh.

                        Lối vào KHÔNG phải menu thì khai ở `LOI_VAO_NGOAI_MENU` kèm TỆP chứa liên \
                        kết — bài này sẽ mở tệp ấy ra đọc.""",
                        khongAiDan)
                .isEmpty();

        // ⛔⛔ Vế chịu lực của phần miễn trừ: lối vào đã khai phải CÓ THẬT trong tệp đã khai.
        //    ⚠ Danh sách nay RỖNG (T28.53) nên vòng lặp chạy 0 lần — cơ chế được chứng minh ở
        //      `coCheMienTruVanBatDuocViPham()`, ⛔ không bằng cách giữ lại một ngoại lệ đã chết.
        LOI_VAO_NGOAI_MENU.forEach((duong, tep) -> {
            String khoa = "ROUTES." + tenKhoaRoutes(duong);
            assertThat(doc(tep))
                    .as(
                            """
                            `%s` được miễn trừ khỏi luật "phải có mục menu" với lý do là một liên kết \
                            ở `%s` — mà tệp ấy KHÔNG còn chứa `%s`.

                            Sự miễn trừ vừa sống lâu hơn cái nó miễn trừ: trang `%s` nay không có lối \
                            vào nào, và mọi cổng kiểm vẫn xanh.""",
                            duong, tep, khoa, duong)
                    .contains(khoa);
        });
    }

    /**
     * ⛔⛔ Tự-kiểm-chứng: cơ chế miễn trừ <b>bắt được</b> một dòng đã chết (conventions.md §1.5).
     *
     * <p>Thay cho khẳng định {@code isNotEmpty()} cũ. Vấn đề của nó: khi danh sách miễn trừ rỗng —
     * tức trạng thái <b>tốt nhất</b>, mọi trang đều có lối vào menu thật — bài kiểm ĐỎ, và cách
     * chữa dễ nhất là giữ lại một ngoại lệ đã hết đúng. Một bộ canh tạo áp lực đi sai hướng thì
     * tệ hơn ⛔ không có.
     *
     * <p>Bài này chạy đúng phép so ấy trên <b>dữ liệu dựng sẵn</b>: một tệp có chứa khoá và một
     * tệp ⛔ không. Nó chứng minh cơ chế còn sống mà ⛔ không đòi phải tồn tại một ngoại lệ thật.
     */
    @Test
    @DisplayName("⚠ Tự-kiểm-chứng: một dòng miễn trừ đã chết PHẢI bị bắt")
    void coCheMienTruVanBatDuocViPham() {
        assertThat(tenKhoaRoutes("/gop-y")).isEqualTo("gopY");
        assertThat(tenKhoaRoutes("/lien-he")).isEqualTo("lienHe");

        String tepCoLienKet = "<a href={ROUTES.gopY}>Góp ý</a>";
        String tepMatLienKet = "<p>Trang này ⛔ không còn dẫn đi đâu</p>";

        assertThat(tepCoLienKet).contains("ROUTES." + tenKhoaRoutes("/gop-y"));
        assertThat(tepMatLienKet).doesNotContain("ROUTES." + tenKhoaRoutes("/gop-y"));
    }

    /** `/gop-y` → `gopY`. ⚠ Bài này soi lời gọi `ROUTES.<khoá>`, ⛔ không soi chuỗi đường dẫn. */
    private static String tenKhoaRoutes(String duong) {
        String[] phan = duong.substring(1).split("-");
        StringBuilder sb = new StringBuilder(phan[0]);
        for (int i = 1; i < phan.length; i++) {
            sb.append(Character.toUpperCase(phan[i].charAt(0))).append(phan[i].substring(1));
        }
        return sb.toString();
    }

    /**
     * ⚠⚠ <b>Đây là bộ canh thật. {@code V202608271031:187} gọi tên một bộ canh KHÔNG TỒN TẠI.</b>
     *
     * <p>Chú thích trong migration ấy viết <i>"{@code PortalDocSystemUrlTest} canh cho khỏi lệch
     * (luật 14)"</i>. Đo 08/09/2026: <b>0 tệp</b> mang tên đó trong toàn kho — nó chưa bao giờ tồn
     * tại. Suốt 12 ngày, thứ giữ hai bảng khỏi lệch nhau là phương thức này, còn người đọc migration
     * thì được chỉ sang một cái tên không có thật.
     *
     * <p>⛔ <b>Và tệp migration ấy KHÔNG được sửa</b>, kể cả chỉ sửa chú thích: Flyway băm <i>cả
     * tệp</i>, nên đổi một ký tự là mọi môi trường đã áp bản cũ sẽ chết ở {@code validate} — §10.65,
     * dự án đã trả giá đúng lỗi này ngày 27/8. Vì vậy lời đính chính nằm ở đây, tại bộ canh thật.
     *
     * <p>Bài học rộng hơn (§10.81): <b>một chú thích không phải một cổng kiểm</b> — và một chú thích
     * <i>khẳng định</i> có cổng kiểm còn tệ hơn im lặng, vì nó làm người đọc thôi đi tìm.
     */
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
        List<String> ket = new java.util.ArrayList<>();
        // ⭐ Đọc CẢ tệp gốc LẪN mọi tệp bổ sung — xem javadoc `MIGRATION_MENU_BO_SUNG`.
        List<String> nguon = new java.util.ArrayList<>();
        nguon.add(MIGRATION);
        nguon.addAll(MIGRATION_MENU_BO_SUNG);

        for (String tep : nguon) {
            String noiDung = doc(tep);
            Matcher m = Pattern.compile("'(?:URL)',\\s*'(/[a-z0-9\\-/]+)'").matcher(noiDung);
            while (m.find()) {
                ket.add(m.group(1));
            }
            // Dạng thứ hai: bảng VALUES của mục con — ('Nhãn', '/duong-dan', thu_tu)
            Matcher bang = Pattern.compile("\\('[^']+',\\s*'(/[a-z0-9\\-/]+)',\\s*\\d+\\)")
                    .matcher(noiDung);
            while (bang.find()) {
                ket.add(bang.group(1));
            }
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
