package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * <b>Mỗi khoá {@code settings} nhóm HYDRO phải có một hàm đọc</b> — nợ <b>T27.5</b>.
 *
 * <h2>⛔⛔ Vì sao nợ này tồn tại: bộ canh cũ HẸP HƠN nơi nó phải chặn</h2>
 *
 * <p>{@code PortalSettingsReadTest} canh đúng luật này — <i>"công tắc / cột / tham số chưa ai đọc là
 * một lỗi"</i> (quy tắc 15) — nhưng chỉ quét {@code content/…/migration/cms} và
 * {@code public-web/src}. Nó ⛔ <b>không chạm</b> {@code migration/core}, ⛔ không chạm
 * {@code migration/hyd}, ⛔ không chạm {@code backend/hydro}. Suốt thời gian ấy, <b>tám khoá HYDRO
 * seed ngày 13/08 nằm 18 ngày ⛔ không một dòng mã nào đọc</b> — người vận hành thấy tám ô nhập trên
 * màn hình Cấu hình hệ thống, sửa chúng, và ⛔ không có gì đổi.
 *
 * <p>⇒ Đúng luật 28: <i>một cơ chế canh gác đúng luật, hẹp hơn nơi nó phải chặn, và cái xanh của nó
 * đọc như một lời bảo đảm</i>. Cùng hình dạng với {@code NginxSecurityHeadersTest} chỉ soi
 * {@code admin-app} trong khi cổng công khai chạy không CSP (§10.61).
 *
 * <h2>⭐ Hai chiều, và chiều thứ hai mới là chiều khó</h2>
 *
 * <ul>
 *   <li><b>seed ⇒ có hàm đọc</b>: ô nhập bày ra trên màn hình thì phải có tác dụng;
 *   <li><b>có hàm đọc ⇒ đã seed</b>: một hàm đọc trỏ vào khoá ⛔ không tồn tại thì <b>luôn</b> rơi
 *       về giá trị mặc định trong mã, và người vận hành ⛔ không có ô nào để đổi nó. Đây đúng lỗi
 *       {@code limit.upload.max-file-mb} của WS-6: mã đọc một khoá <b>chưa từng được seed</b>, nên
 *       mọi lượt tải rơi về 20MB cứng, trong khi màn hình bày ra ba tham số khác.
 * </ul>
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <p>Soi <b>mọi</b> migration của backend (⛔ không chỉ một thư mục — đó chính là lỗi đang sửa) và
 * <b>một</b> tệp đọc: {@code HydroSettings.java}, nơi dự án đã chốt là chỗ duy nhất đọc khoá
 * {@code hydro.*}. Một khoá {@code hydro.*} được đọc thẳng bằng {@code SettingPort} ở lớp khác ⛔ sẽ
 * không được bài này thấy — và đó là lý do {@link KhongDocTatKhoaONoiKhac} tồn tại.
 */
class HydroSettingsReadTest {

    private static final String LOP_DOC =
            "backend/hydro/src/main/java/com/songnhue/hydro/application/HydroSettings.java";

    /**
     * ⚠ Khoá seed — dòng {@code ('khoa', 'giá trị', 'KIỂU',} của khối {@code VALUES}.
     *
     * <p>⛔ Cố ý ⛔ <b>không</b> bắt mọi lần xuất hiện của chuỗi {@code 'hydro.…'}: một khoá bị nhắc
     * trong <b>chú thích</b> của migration (rất nhiều) hoặc trong câu {@code DELETE} ⛔ không phải
     * một khoá đang sống. Lượt đo tay 04/09 đã mắc đúng lỗi ấy ở một chỗ khác — {@code grep} khớp
     * trúng dòng chú thích nói <i>"biến này đã bị gỡ"</i> rồi kết luận là nó còn.
     *
     * <h3>⚠ {@code \\s*} sau dấu {@code (} — và bản đầu thiếu nó</h3>
     *
     * <p>Migration cũ viết {@code VALUES ('hydro.x', …} trên một dòng; migration mới xuống dòng ngay
     * sau {@code (}. Mẫu đầu đòi khoá <b>dính liền</b> dấu ngoặc nên bắt được <b>8/11</b> khoá —
     * và ba khoá đi lọt đều là khoá <i>mới nhất</i>, tức đúng nhóm mà bộ canh cần theo dõi nhất.
     *
     * <p>⭐ Thứ bắt được lỗi này ⛔ không phải phép so hai tập (nó vẫn xanh: 8 khoá ấy đều có hàm
     * đọc), mà là khẳng định <b>về SỐ LƯỢNG</b> — vế duy nhất ⛔ không chia sẻ giả định nào với mẫu
     * regex (luật 29).
     */
    private static final Pattern KHOA_SEED =
            Pattern.compile("\\(\\s*'(hydro\\.[a-z0-9.\\-]+)',\\s*'[^']*',\\s*'[A-Z]+',", Pattern.DOTALL);

    /** Câu {@code DELETE FROM settings … IN (…)} — khoá đã gỡ ⛔ không được đòi có hàm đọc. */
    private static final Pattern CAU_XOA =
            Pattern.compile("DELETE\\s+FROM\\s+settings\\s+WHERE\\s+setting_key[^;]+;", Pattern.CASE_INSENSITIVE);

    private static final Pattern MOT_KHOA = Pattern.compile("'(hydro\\.[a-z0-9.\\-]+)'");

    /** Hằng {@code static final String … = "hydro.…"} trong {@code HydroSettings}. */
    private static final Pattern KHOA_DUOC_KHAI = Pattern.compile("=\\s*\"(hydro\\.[a-z0-9.\\-]+)\"");

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ T27.5 — khoá HYDRO đã seed ↔ hằng khai trong HydroSettings, ĐỐI CHIẾU HAI CHIỀU")
    void moiKhoaHydroDeuCoHaiVe() {
        Set<String> daSeed = khoaConSong();
        Set<String> coHamDoc = khoaDuocKhai();

        // ⚠ Vế chống xanh-trên-tập-rỗng (luật 7) — và nó phải là một CON SỐ, ⛔ không phải `isNotEmpty`:
        //   một bộ tách hỏng một nửa vẫn "không rỗng".
        assertThat(daSeed)
                .as("⛔ Bộ tách ⛔ không thấy đủ khoá HYDRO trong migration — mẫu hỏng hoặc thư mục đổi. "
                        + "Cả hai làm bài này mù chứ ⛔ không đỏ.")
                .hasSizeGreaterThanOrEqualTo(9);
        assertThat(coHamDoc)
                .as("⛔ Bộ tách ⛔ không thấy đủ hằng khoá trong %s", LOP_DOC)
                .hasSizeGreaterThanOrEqualTo(9);

        assertThat(daSeed)
                .as(
                        """
                        ⛔ Khoá đã SEED mà ⛔ KHÔNG có hàm đọc — quy tắc 15. Người vận hành thấy ô nhập trên \
                        màn hình Cấu hình hệ thống, sửa nó, và ⛔ không có gì đổi. Tám khoá HYDRO của 13/08 \
                        đã nằm như vậy 18 ngày. Hằng đang khai: %s""",
                        coHamDoc)
                .isSubsetOf(coHamDoc);

        assertThat(coHamDoc)
                .as(
                        """
                        ⛔ Có hàm ĐỌC mà khoá ⛔ CHƯA seed — lượt đọc sẽ LUÔN rơi về mặc định trong mã, và \
                        người vận hành ⛔ không có ô nào để đổi nó. Đúng lỗi `limit.upload.max-file-mb` của \
                        WS-6: mã đọc một khoá chưa từng seed nên mọi lượt tải rơi về 20MB cứng, trong khi \
                        màn hình bày ra ba tham số khác. Đã seed: %s""",
                        daSeed)
                .isSubsetOf(daSeed);
    }

    /**
     * ⛔⛔ {@code HydroSettings} phải là <b>nơi duy nhất</b> đọc khoá {@code hydro.*}.
     *
     * <p>Không có khẳng định này thì bài trên có một khoảng mù đúng bằng phần còn lại của module: ai
     * đó gọi thẳng {@code settings.getString("hydro.gì.đó")} ở một service khác, và khoá ấy ⛔ không
     * bao giờ xuất hiện trong hai tập được đối chiếu.
     */
    @Nested
    @DisplayName("Khoá hydro.* chỉ được đọc ở một chỗ")
    class KhongDocTatKhoaONoiKhac {

        @Test
        @DisplayName("⛔ ⛔ Không lớp nào ngoài HydroSettings nhắc tới một chuỗi `hydro.…`")
        void chiHydroSettingsBietTenKhoa() {
            Path goc = timTuGocKho("backend/hydro/src/main/java");
            try (Stream<Path> cay = Files.walk(goc)) {
                var viPham = cay.filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().endsWith("HydroSettings.java"))
                        // ⚠ Chỉ bắt chuỗi Java THẬT (`"hydro.…"`), ⛔ không bắt `{@code hydro.…}` trong
                        //   javadoc — mọi lớp dùng tham số đều nhắc tên khoá trong tài liệu của nó, và
                        //   một bộ canh bắt cả văn xuôi sẽ đỏ ở khắp nơi rồi bị nới ra.
                        .filter(p -> KHOA_DUOC_KHAI.matcher(doc(p)).find()
                                || Pattern.compile("getString\\(\\s*\"hydro\\.")
                                        .matcher(doc(p))
                                        .find())
                        .map(p -> goc.relativize(p).toString())
                        .sorted()
                        .toList();

                assertThat(viPham)
                        .as("⛔ Tên khoá `hydro.*` nằm rải ở nhiều lớp thì bộ đối chiếu hai chiều phía "
                                + "trên có một khoảng mù đúng bằng phần còn lại của module.")
                        .isEmpty();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** ⚠ Vế tự kiểm (luật 29): bộ tách phải phân biệt được ba loại chuỗi nhìn giống nhau. */
    @Test
    @DisplayName("⚠ Tự kiểm: chú thích và câu DELETE ⛔ KHÔNG được tính là một khoá đang sống")
    void boTachPhanBietDuocBaLoaiChuoi() {
        String seedThat = "    ('hydro.polling.cron', '45 1/2 * * * *', 'CRON',";
        String chuThich = "-- ⛔ `hydro.threshold.default-set` đã gỡ khỏi seed ở V202609041062.";
        String cauXoa = "DELETE FROM settings WHERE setting_key IN ('hydro.threshold.default-set');";

        assertThat(trichKhoa(KHOA_SEED, seedThat)).containsExactly("hydro.polling.cron");
        assertThat(trichKhoa(KHOA_SEED, chuThich))
                .as("⛔ Một dòng chú thích nói rằng khoá ĐÃ GỠ mà bị đọc thành 'đã seed' thì bộ canh đòi "
                        + "phải có hàm đọc một khoá ⛔ không còn tồn tại — đỏ trên mã ĐÚNG.")
                .isEmpty();
        assertThat(trichKhoa(KHOA_SEED, cauXoa)).isEmpty();
        assertThat(CAU_XOA.matcher(cauXoa).find())
                .as("⚠ và câu DELETE phải được NHẬN RA, để khoá trong nó bị trừ khỏi tập đang sống")
                .isTrue();
    }

    // -------------------------------------------------------------------------

    /** Khoá đã seed <b>trừ</b> khoá đã gỡ — chỉ những khoá còn sống mới bị đòi có hàm đọc. */
    private static Set<String> khoaConSong() {
        String moiMigration = gopMigration();
        Set<String> song = trichKhoa(KHOA_SEED, moiMigration);
        Matcher xoa = CAU_XOA.matcher(moiMigration);
        while (xoa.find()) {
            song.removeAll(trichKhoa(MOT_KHOA, xoa.group()));
        }
        return song;
    }

    private static Set<String> khoaDuocKhai() {
        return trichKhoa(KHOA_DUOC_KHAI, doc(timTuGocKho(LOP_DOC)));
    }

    private static Set<String> trichKhoa(Pattern mau, String noiDung) {
        return mau.matcher(noiDung).results().map(r -> r.group(1)).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * ⚠ Gộp <b>mọi</b> migration của backend — ⛔ không chỉ một thư mục.
     *
     * <p>Khoá {@code hydro.*} nằm rải ở <b>hai</b> module: bộ tám gốc ở {@code migration/core}
     * (13/08) và các bộ sau ở {@code migration/hyd}. Soi một thư mục là tái lập đúng khuyết tật mà
     * bài này sinh ra để đóng.
     */
    private static String gopMigration() {
        Path backend = timTuGocKho("backend");
        try (Stream<Path> cay = Files.walk(backend)) {
            var tep = cay.filter(p -> p.toString().endsWith(".sql"))
                    // ⛔ `target/` chứa BẢN SAO của migration; đếm cả hai là đếm đúp và che một
                    //    lượt xoá tệp nguồn (tệp cũ còn nằm trong target cho tới `mvn clean`).
                    .filter(p -> !p.toString().contains("/target/"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            assertThat(tep)
                    .as("⚠ vế chống tập rỗng: ⛔ không tìm thấy tệp .sql nào dưới backend/")
                    .isNotEmpty();
            StringBuilder gop = new StringBuilder();
            for (Path p : tep) {
                gop.append(doc(p)).append('\n');
            }
            return gop.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String doc(Path tep) {
        try {
            return Files.readString(tep, StandardCharsets.UTF_8);
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
