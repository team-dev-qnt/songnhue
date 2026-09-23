package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Mỗi khoá seed trong {@code db/migration/core} phải có nơi đọc — hoặc được XẾP LOẠI</b> ({@code T68.29}).
 *
 * <h2>Vì sao thư mục này, và vì sao mãi tới hôm nay</h2>
 *
 * <p>Ba bộ canh anh em đã có — {@code PortalSettingsReadTest} ({@code site.*}/{@code company.*}),
 * {@code HydroSettingsReadTest}, {@code HrSettingsReadTest} — và mỗi cái tự khai phạm vi theo <b>tiền tố khoá</b>.
 * Hệ quả đo được 23/09/2026: {@code backend/core/…/db/migration/core} là thư mục seed <b>duy nhất ⛔ ai canh</b>,
 * vì khoá ở đó mang đủ mọi tiền tố ({@code limits.*} · {@code integration.*} · {@code security.*} …) và ⛔ tiền tố
 * nào trong ba cái trên nhận chúng. Bộ canh này vì thế chia theo <b>THƯ MỤC</b>, ⛔ theo tiền tố — chỗ hở nằm ở
 * ranh giới giữa ba phạm vi, nên một phạm vi thứ tư cùng kiểu sẽ để lại đúng một chỗ hở mới (luật 28).
 *
 * <h2>⛔⛔ Ba cách một bộ canh như thế này tự nói dối — cả ba đã có nạn nhân</h2>
 *
 * <ol>
 *   <li><b>Khoá dựng ĐỘNG.</b> {@code NotificationService:48} khai {@code "notification.channel.%s.enabled"} rồi
 *       ghép {@code sms}/{@code web-push} lúc chạy. Một phép so chuỗi thẳng sẽ báo hai khoá ấy <i>⛔ ai đọc</i> —
 *       và dòng sổ {@code T68.29} <b>đã kể nhầm đúng hai khoá này</b>. ⇒ {@code %s}/{@code %d} là ký tự đại diện.
 *   <li><b>Khoá đã {@code DELETE} ở migration sau.</b> Đếm chúng là dựng một danh sách nợ ⛔ tồn tại.
 *   <li><b>Chú thích.</b> Một khoá nhắc trong {@code //} hay javadoc ⛔ phải một nơi đọc (T46.7 · T54.8).
 * </ol>
 *
 * <h2>⚠ Giới hạn, nói thẳng ra</h2>
 *
 * <p>Bộ canh hỏi <i>"có dòng mã nào NHẮC tới khoá ⛔"</i>, ⛔ hỏi <i>"giá trị đọc ra có điều khiển gì ⛔"</i>. Hai
 * khoá {@code notification.channel.sms/web-push.enabled} đi qua được nó mà vẫn <b>vô hiệu</b>, vì 16 nơi đặt thông
 * báo đều ghi cứng {@code List.of(IN_APP, EMAIL)}. Đó là nợ KHÁC LOẠI ({@code T6.6}) và cách vá khác hẳn — ⛔ đọc
 * cái xanh của lớp này thành <i>"mọi khoá đều có tác dụng"</i>.
 */
class CoreSettingsReadTest {

    private static final String THU_MUC_SEED = "backend/core/src/main/resources/db/migration/core";

    /** Nơi một khoá có thể được đọc. ⛔ Cố ý ⛔ gồm thư mục test. */
    private static final List<String> NOI_DOC = List.of(
            "backend/core/src/main/java",
            "backend/content/src/main/java",
            "backend/operations/src/main/java",
            "backend/hydro/src/main/java",
            "backend/hr/src/main/java",
            "backend/app/src/main/java",
            "frontend/admin-app/src",
            "frontend/public-web/src");

    /**
     * Khoá <b>cố ý</b> chưa có nơi đọc — mỗi dòng một lý do <b>đo được</b> và <b>nói rõ AI quyết</b>.
     *
     * <p>⛔ <i>"Chưa dùng"</i> ⛔ phải một lý do: nó đúng với mọi khoảng trống. Bảy dòng dưới đây là toàn bộ nợ đo
     * được ngày 23/09/2026, và ⛔ dòng nào trong số đó là việc của phía phát triển — chúng chờ một quyết định
     * nghiệp vụ. Nối một nơi đọc cho {@code limits.max-users} mà ⛔ ai duyệt con số là tự đặt ra một luật.
     */
    private static final Map<String, String> CHUA_CO_NGUOI_DOC = new LinkedHashMap<>(Map.of(
            "company.name",
            "Tên Công ty đang GHI CỨNG ở `AdminLayout.tsx:201` và `LoginPage.tsx:79` (đo 23/09). Nối khoá này là "
                    + "cho phép sửa tên đơn vị trên mọi màn hình — QuanTran quyết còn để sửa được hay chốt cứng.",
            "company.short-name",
            "Cùng cặp với `company.name`, cùng hai nơi ghi cứng. Vá một mình nó là để hai nguồn tên Công ty lệch "
                    + "nhau — thứ chỉ lộ ra trên bản in báo cáo. QuanTran quyết cả cặp, hoặc gỡ cả cặp.",
            "limits.max-stations",
            "Trần số điểm đo. ⛔ nơi nào chặn, nên ô này sửa được mà ⛔ tác dụng. Con số trần là một luật vận hành "
                    + "(Công ty có bao nhiêu điểm đo là đủ) — QuanTran quyết trước khi có đường chặn.",
            "limits.max-constructions",
            "Trần số công trình, cùng nhóm `limits.max-*`. Đo 23/09: 0 nơi đọc. Đặt trần rồi chặn là đổi hành vi "
                    + "nhập liệu của Công ty — ⛔ phải việc mã tự quyết. Chờ QuanTran cùng lượt với hai khoá kia.",
            "limits.max-users",
            "Trần số tài khoản. Nặng hơn hai khoá trên vì chạm cấp phát tài khoản cho CBNV (chốt C3 đòi cấp cho "
                    + "TOÀN BỘ CBNV) — một trần đặt nhầm là khoá đường tạo tài khoản. QuanTran quyết.",
            "integration.external-doc.enabled",
            "Công tắc liên kết hệ thống văn bản điều hành (CN-01.7). Chặn bởi **G5** — Công ty chưa cấp mã số và "
                    + "chưa trả lời vụ SSO, nên ⛔ có gì để bật. Mở lại đúng lúc G5 đóng, ⛔ sớm hơn.",
            "security.password.max-age-days",
            "⭐ Khoá chết thứ bảy — ⛔ dòng sổ nào từng nhắc tới nó trước 20/09. Hằng `SettingKeys:18` khai tên khoá "
                    + "rồi 0 nơi dùng. Buộc đổi mật khẩu định kỳ là một chính sách bảo mật có tranh cãi (NIST "
                    + "SP 800-63B khuyên ⛔ ép xoay vòng) ⇒ QuanTran quyết bật hay gỡ, ⛔ phải mã tự chọn."));

    static final Pattern KHOA_SEED = Pattern.compile("\\(\\s*'([a-z0-9.\\-]+)',\\s*'[^']*',\\s*'[A-Z]+',");

    /**
     * ⛔⛔ Nhận <b>CẢ HAI</b> dạng câu xoá. Kho có 4 câu {@code IN (…)} và <b>2</b> câu {@code = '…'} (đo 23/09), và
     * ba bộ canh anh em chỉ nhận dạng đầu ⇒ một khoá gỡ bằng dạng thứ hai vẫn nằm trong tập <i>còn sống</i> của
     * chúng. Đó ⛔ phải chuyện giả định: {@code hydro.threshold.default-set} gỡ ở {@code V202609041062} bằng đúng
     * dạng {@code = '…'}, và {@code HydroSettingsReadTest} vẫn xanh — nhờ một dòng <b>javadoc</b> nhắc tên khoá.
     * Hai khuyết tật độc lập triệt tiêu nhau (T46.7 · luật 9).
     */
    static final Pattern CAU_XOA = Pattern.compile(
            "DELETE\\s+FROM\\s+settings\\s+WHERE\\s+setting_key\\s*(?:IN\\s*\\(([^)]*)\\)|=\\s*('[^']*'))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MOT_KHOA = Pattern.compile("'([a-z0-9.\\-]+)'");

    /**
     * Chuỗi ký tự trông như một khoá {@code settings} — nhận chỗ cắm {@code %s}/{@code %d}, và nhận <b>cả nháy
     * đơn</b>.
     *
     * <p>⛔⛔ Nháy đơn ⛔ phải chi tiết thẩm mỹ: {@code public-web} đọc khoá bằng {@code config?.['company.address']}
     * — toàn bộ frontend viết chuỗi bằng nháy đơn theo Prettier của kho. Bản đầu của lớp này chỉ nhận nháy kép và
     * báo <b>6 khoá {@code company.*} là chết</b> trong khi {@code SiteFooter.tsx} và {@code lien-he/page.tsx} đang
     * đọc chúng thật. Một bộ canh mù trước quy ước nháy của chính kho là một máy sinh dương tính giả.
     *
     * <p>⚠ Nháy đơn trong Java là <i>ký tự</i>, ⛔ phải chuỗi — một khoá dài hơn 1 ký tự nên ⛔ bao giờ khớp nhầm.
     */
    private static final Pattern CHUOI_KHOA = Pattern.compile("[\"']([a-z][a-z0-9.\\-]*(?:%[sd][a-z0-9.\\-]*)*)[\"']");

    /** Dòng khai một hằng mang tên khoá: {@code String TEN_HANG = "khoa.abc";} */
    private static final Pattern KHAI_HANG =
            Pattern.compile("\\b([A-Z][A-Z0-9_]{2,})\\s*=\\s*[\"']([a-z][a-z0-9.\\-]*)[\"']");

    private static final Pattern SO_HIEU = Pattern.compile("^V(\\d+)__");

    @Test
    @DisplayName("⛔ MỌI khoá seed ở db/migration/core đều có nơi đọc — hoặc nằm trong danh sách XẾP LOẠI")
    void moiKhoaDeuCoNguoiDocHoacDuocXepLoai() {
        Set<String> noiDoc = chuoiTrongMa();
        List<String> khongAiDoc = khoaSong().stream()
                .filter(k -> !CHUA_CO_NGUOI_DOC.containsKey(k))
                .filter(k -> !duocDoc(k, noiDoc))
                .toList();

        assertThat(khongAiDoc)
                .as(
                        """
                        Seed vào `settings` ở `db/migration/core` mà ⛔ dòng mã nào đọc: %s

                        Quy tắc 15 — người vận hành sửa ô ấy trên màn hình Cấu hình, hệ báo *lưu thành công*, và \
                        ⛔ gì đổi. Chọn MỘT: viết nơi đọc · `DELETE` khoá ở migration mới · hoặc khai vào \
                        `CHUA_CO_NGUOI_DOC` kèm lý do ĐO ĐƯỢC và tên người quyết.""",
                        khongAiDoc)
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Danh sách xếp loại phải còn ĐÚNG — khoá đã nối nơi đọc hoặc đã xoá thì phải rời danh sách")
    void danhSachXepLoaiKhongDuocMuc() {
        List<String> song = khoaSong();
        Set<String> noiDoc = chuoiTrongMa();

        List<String> daHetLyDo = CHUA_CO_NGUOI_DOC.keySet().stream()
                .filter(k -> !song.contains(k) || duocDoc(k, noiDoc))
                .toList();

        assertThat(daHetLyDo)
                .as(
                        """
                        Những khoá này khai là *chưa có người đọc* mà đo lại thì ⛔ còn đúng — hoặc đã có nơi đọc, \
                        hoặc đã bị DELETE: %s

                        ⛔ Một danh sách miễn trừ ⛔ ai dọn là một bộ canh đang MỤC: nó vẫn xanh trong khi tập nó \
                        miễn đã khác hẳn (đúng thứ vừa xảy ra với dòng miễn trừ `core` của `CotPhase2CoDocGhiTest`).""",
                        daHetLyDo)
                .isEmpty();

        List<String> lyDoHoiHot = CHUA_CO_NGUOI_DOC.entrySet().stream()
                .filter(e -> e.getValue().length() < 40)
                .map(Map.Entry::getKey)
                .toList();
        assertThat(lyDoHoiHot)
                .as("lý do phải ĐO ĐƯỢC — *'chưa cần'* đúng với mọi khoảng trống nên nó ⛔ nói gì")
                .isEmpty();
    }

    @Test
    @DisplayName("⚠ Chống tập rỗng (luật 7 · 29) — đo ra đủ khoá seed VÀ đủ chuỗi trong mã")
    void doDuocCaHaiNguon() {
        assertThat(khoaSong())
                .as("khoá còn sống seed ở db/migration/core — 0 nghĩa là mẫu regex hỏng, ⛔ phải kho sạch nợ")
                .hasSizeGreaterThanOrEqualTo(40);
        assertThat(chuoiTrongMa()).as("chuỗi trông như khoá trong mã sản phẩm").hasSizeGreaterThanOrEqualTo(100);
        assertThat(CHUA_CO_NGUOI_DOC).as("bảy khoá đo được 23/09/2026").hasSize(7);
    }

    @Test
    @DisplayName("⛔ Tự kiểm: %s là ký tự đại diện · tiền tố có dấu chấm · DELETE bị trừ · chú thích ⛔ tính")
    void tuKiem() {
        assertThat(duocDoc("notification.channel.sms.enabled", Set.of("notification.channel.%s.enabled")))
                .as("⛔⛔ Vế này là lý do dòng sổ T68.29 kể nhầm hai khoá: khoá dựng ĐỘNG vẫn là khoá có người đọc")
                .isTrue();
        assertThat(duocDoc("notification.channel.sms.enabled.extra", Set.of("notification.channel.%s.enabled")))
                .as("⚠ ký tự đại diện ⛔ được nuốt luôn phần đuôi — ⛔ thì mọi khoá cùng tiền tố đều 'có người đọc'")
                .isFalse();
        assertThat(duocDoc("hr.document.max-mb.HOP_DONG", Set.of("hr.document.max-mb.")))
                .isTrue();
        assertThat(duocDoc("limits.max-users", Set.of("limits.max-user")))
                .as("⛔ khớp tiền tố khi ⛔ có dấu chấm — 'limits.max-user' ⛔ phải nơi đọc của 'limits.max-users'")
                .isFalse();

        String mau =
                """
                VALUES ('a.b', '1', 'INTEGER',
                VALUES (
                    'c.d', '2027', 'INTEGER', '2027',
                DELETE FROM settings WHERE setting_key IN (
                    'a.b'
                );
                """;
        assertThat(KHOA_SEED.matcher(mau).results().map(r -> r.group(1)).toList())
                .containsExactly("a.b", "c.d");
        Matcher xoa = CAU_XOA.matcher(mau);
        assertThat(xoa.find()).isTrue();
        assertThat(MOT_KHOA.matcher(xoa.group(1)).results().map(r -> r.group(1)).toList())
                .containsExactly("a.b");

        assertThat(khoaBiXoa("DELETE FROM settings WHERE setting_key = 'x.y';"))
                .as("⛔⛔ Dạng `= '…'` — kho có 2 câu như vậy, và ba bộ canh anh em đều MÙ trước nó. "
                        + "`hydro.threshold.default-set` gỡ đúng bằng dạng này.")
                .containsExactly("x.y");
        assertThat(khoaBiXoa("DELETE FROM settings WHERE setting_key IN ('a.b', 'c.d');"))
                .containsExactly("a.b", "c.d");

        Path giaKhai = Paths.get("SettingKeys.java");
        Path giaDung = Paths.get("Dung.java");
        assertThat(khoaCuaHangChet(Map.of(giaKhai, "String KHOA_CHET = \"a.chet\";")))
                .as("⛔ hằng khai rồi 0 nơi dùng ⛔ phải một nơi đọc — đúng ca `security.password.max-age-days`")
                .containsExactly("a.chet");
        assertThat(khoaCuaHangChet(Map.of(
                        giaKhai, "String KHOA_SONG = \"a.song\";", giaDung, "settings.get(SettingKeys.KHOA_SONG)")))
                .as("⚠ VẾ PHÂN BIỆT: hằng CÓ người dùng thì khoá của nó vẫn là nơi đọc — ⛔ thì mọi khoá đi qua "
                        + "`SettingKeys` đều hoá mồ côi và bộ canh thành máy sinh dương tính giả")
                .isEmpty();

        assertThat(CHUOI_KHOA.matcher("config?.['company.address']").results().map(r -> r.group(1)))
                .as("⛔⛔ Nháy ĐƠN — `public-web` đọc khoá kiểu này, và bản đầu của lớp này báo 6 khoá company.* "
                        + "là chết trong khi `SiteFooter.tsx` đang đọc chúng thật")
                .containsExactly("company.address");

        assertThat(boChuThich("// đọc \"limits.max-users\" ở đây\nString x = \"limits.max-stations\";"))
                .as("⛔⛔ T46.7 · T54.8 — một khoá nhắc trong CHÚ THÍCH ⛔ phải một nơi đọc, và bộ canh phạt đúng "
                        + "người viết tài liệu tử tế nếu nó đếm cả chú thích")
                .doesNotContain("limits.max-users")
                .contains("limits.max-stations");
    }

    // ---- Trích dữ liệu -------------------------------------------------------

    /**
     * Một khoá được coi là có người đọc khi mã nhắc tới nó <b>nguyên văn</b>, qua một <b>tiền tố kết thúc bằng dấu
     * chấm</b>, hoặc qua một <b>khuôn có {@code %s}/{@code %d}</b>.
     */
    static boolean duocDoc(String khoa, Set<String> noiDoc) {
        return noiDoc.stream().anyMatch(t -> {
            if (t.equals(khoa)) {
                return true;
            }
            if (t.endsWith(".")) {
                return khoa.startsWith(t);
            }
            if (!t.contains("%")) {
                return false;
            }
            String bieuThuc =
                    Pattern.quote(t).replace("%s", "\\E[a-z0-9\\-]+\\Q").replace("%d", "\\E[0-9]+\\Q");
            return khoa.matches(bieuThuc);
        });
    }

    /** Bỏ chú thích mà <b>giữ chuỗi ký tự</b> — cùng khuôn {@code boChuThich} của T54.8. */
    static String boChuThich(String ma) {
        return ma.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)(^|[^:])//.*$", "$1");
    }

    private static List<String> khoaSong() {
        Path goc = timTuGocKho(THU_MUC_SEED);
        List<Path> tep;
        try (Stream<Path> luot = Files.list(goc)) {
            tep = luot.filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .filter(p -> SO_HIEU.matcher(p.getFileName().toString()).find())
                    .sorted(Comparator.comparing(CoreSettingsReadTest::soHieu))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertThat(tep).as("⛔ đọc được migration nào dưới %s", goc).hasSizeGreaterThan(5);

        Set<String> song = new LinkedHashSet<>();
        for (Path p : tep) {
            String sql = docTep(p).replaceAll("(?m)--.*$", "");
            KHOA_SEED.matcher(sql).results().map(r -> r.group(1)).forEach(song::add);
            khoaBiXoa(sql).forEach(song::remove);
        }
        // ⚠ Câu DELETE có thể nằm ở migration của module KHÁC (khoá `hr.*` seed ở `core` bị gỡ ở `hr`).
        // ⛔ trừ chúng là dựng một danh sách nợ ⛔ tồn tại.
        for (Path p : moiMigrationToanKho()) {
            khoaBiXoa(docTep(p).replaceAll("(?m)--.*$", "")).forEach(song::remove);
        }
        return song.stream().sorted().toList();
    }

    /** Khoá bị gỡ, nhận cả {@code IN (…)} (nhóm 1) lẫn {@code = '…'} (nhóm 2). */
    static List<String> khoaBiXoa(String sql) {
        return CAU_XOA.matcher(sql)
                .results()
                .map(cau -> cau.group(1) != null ? cau.group(1) : cau.group(2))
                .flatMap(danh -> MOT_KHOA.matcher(danh).results())
                .map(r -> r.group(1))
                .toList();
    }

    private static List<Path> moiMigrationToanKho() {
        try (Stream<Path> luot = Files.walk(timTuGocKho("backend"))) {
            return luot.filter(p -> p.toString().contains("/src/main/resources/db/"))
                    .filter(p -> p.getFileName().toString().endsWith(".sql"))
                    .sorted(Comparator.comparing(CoreSettingsReadTest::soHieu))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static java.math.BigInteger soHieu(Path p) {
        Matcher m = SO_HIEU.matcher(p.getFileName().toString());
        return m.find() ? new java.math.BigInteger(m.group(1)) : java.math.BigInteger.ZERO;
    }

    /**
     * Mọi chuỗi trông như khoá trong mã sản phẩm — đã bỏ chú thích và đã <b>trừ hằng chết</b>.
     *
     * <p>⛔⛔ <b>Vì sao phải trừ hằng chết.</b> {@code SettingKeys:18} khai
     * {@code PASSWORD_MAX_AGE_DAYS = "security.password.max-age-days"} rồi <b>0 nơi dùng</b> cái hằng ấy. Đếm dòng
     * khai là một nơi đọc thì khoá ấy <i>trông như</i> đang được dùng — đúng loại xanh-vì-lý-do-sai mà lớp này sinh
     * ra để chặn, và nó đã lừa được lượt đo ĐẦU TIÊN của chính tôi.
     *
     * <p>⚠ Và ⛔ được đi đường tắt <i>"bỏ hẳn {@code SettingKeys.java} khỏi phạm vi quét"</i>: phần lớn khoá được
     * đọc qua {@code SettingKeys.TEN_HANG}, nên chuỗi nguyên văn CHỈ tồn tại trong tệp ấy. Bỏ tệp ⇒ mọi khoá đi qua
     * hằng đều hoá mồ côi. ⇒ Phép phân biệt đúng là <b>tên hằng có được dùng ở chỗ khác ⛔</b>.
     */
    private static Set<String> chuoiTrongMa() {
        Map<Path, String> ma = maSanPham();
        Set<String> ra = new LinkedHashSet<>();
        ma.values().forEach(noiDung -> CHUOI_KHOA
                .matcher(noiDung)
                .results()
                .map(r -> r.group(1))
                .forEach(ra::add));
        ra.removeAll(khoaCuaHangChet(ma));
        return ra;
    }

    /** Khoá mà lần xuất hiện DUY NHẤT của nó là dòng khai một hằng ⛔ ai dùng. */
    static Set<String> khoaCuaHangChet(Map<Path, String> ma) {
        Set<String> chet = new LinkedHashSet<>();
        for (String noiDung : ma.values()) {
            Matcher khai = KHAI_HANG.matcher(noiDung);
            while (khai.find()) {
                String tenHang = khai.group(1);
                String khoa = khai.group(2);
                if (demToanBo(ma, tenHang) <= 1 && demToanBo(ma, khoa) <= 1) {
                    chet.add(khoa);
                }
            }
        }
        return chet;
    }

    private static long demToanBo(Map<Path, String> ma, String can) {
        return ma.values().stream()
                .mapToLong(noiDung -> demXuatHien(noiDung, can))
                .sum();
    }

    private static long demXuatHien(String noiDung, String can) {
        long so = 0;
        int i = noiDung.indexOf(can);
        while (i >= 0) {
            so++;
            i = noiDung.indexOf(can, i + can.length());
        }
        return so;
    }

    private static Map<Path, String> maSanPham() {
        Map<Path, String> ra = new LinkedHashMap<>();
        for (String thuMuc : NOI_DOC) {
            try (Stream<Path> luot = Files.walk(timTuGocKho(thuMuc))) {
                luot.filter(Files::isRegularFile)
                        .filter(p -> !p.toString().contains("node_modules"))
                        .filter(p -> {
                            String ten = p.getFileName().toString();
                            return ten.endsWith(".java") || ten.endsWith(".ts") || ten.endsWith(".tsx");
                        })
                        .forEach(p -> ra.put(p, boChuThich(docTep(p))));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ra;
    }

    private static String docTep(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path timTuGocKho(String duongDan) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDan);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ thấy " + duongDan + " từ " + System.getProperty("user.dir"));
    }
}
