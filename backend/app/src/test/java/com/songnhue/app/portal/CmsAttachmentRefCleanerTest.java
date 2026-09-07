package com.songnhue.app.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.content.application.CmsAttachmentRefCleaner;

/**
 * <b>Bộ gỡ tham chiếu ảnh của CMS phải phủ HẾT cột trỏ vào {@code attachments}</b> — T28.34.
 *
 * <h2>⛔⛔ Bài kiểm này ra đời vì javadoc đã nêu tên nó TRƯỚC KHI nó tồn tại</h2>
 *
 * <p>{@code CmsAttachmentRefCleaner} viết ngày 04/09/2026 có một câu trong javadoc:
 * <i>"{@code CmsAttachmentRefCleanerTest} đếm số phần tử để lượt thêm cột mà quên nơi gỡ ⛔ không đi
 * lọt trong im lặng"</i> — và tệp ấy <b>⛔ không tồn tại</b>. Một chú thích nêu tên một bài kiểm đọc
 * <b>y hệt một lời bảo đảm</b>, nên ⛔ không ai đi kiểm xem bài kiểm ấy có thật không.
 *
 * <p>⚠ Đây là <b>lần thứ hai</b> trong đúng một ngày. Sáng 04/09, {@code PortalCache#layoutChanged}
 * bị phát hiện đang trỏ vào {@code CongTacTrangChuTest} — cũng một tệp ⛔ không tồn tại — và câu vá
 * lúc ấy ghi thẳng vào javadoc: <i>"Tìm bằng {@code grep} trước khi viết tên vào đây"</i>. Vài giờ
 * sau, cùng một người lặp lại cùng một lỗi ở một tệp khác. ⇒ Lời dặn ⛔ không phải một cơ chế
 * (§10.42); thứ đóng được nó là <b>tệp này</b>.
 *
 * <h2>Bất biến được canh</h2>
 *
 * <p>Mọi cột trong migration {@code cms} khai
 * {@code REFERENCES attachments (public_id) ON DELETE SET NULL} phải có mặt trong
 * {@code CmsAttachmentRefCleaner.BANG_CO_THAM_CHIEU}. Ràng buộc CSDL ấy <b>chưa từng bắn</b> (xoá
 * đính kèm là xoá <i>mềm</i>), nên danh sách Java kia là thứ <b>duy nhất</b> thật sự gỡ tham chiếu —
 * và một cột thứ tư ra đời mà quên thêm vào đó sẽ hỏng <b>im lặng</b>, đúng như cột thứ nhất đã hỏng
 * im lặng suốt từ WS-14.
 */
class CmsAttachmentRefCleanerTest {

    private static final String THU_MUC_MIGRATION_CMS = "backend/content/src/main/resources/db/migration/cms";

    /**
     * {@code <cột> UUID … REFERENCES attachments (public_id) … ON DELETE SET NULL}.
     *
     * <p>⚠ Chỉ bắt cột khai {@code ON DELETE SET NULL}. Cột {@code NOT NULL} <b>không có</b> mệnh đề
     * ấy ({@code banners.image_attachment_public_id}) cố ý nằm ngoài: gỡ nó về {@code NULL} là vi
     * phạm ràng buộc, và câu hỏi đúng — <i>"xoá ảnh của một banner đang chạy thì banner ra sao"</i> —
     * là một quyết định nghiệp vụ chưa ai đặt ra.
     *
     * <h3>⛔⛔ Bản đầu của mẫu này ĐỎ GIẢ trên mã ĐÚNG — bóc chú thích TRƯỚC khi khớp</h3>
     *
     * <p>Nó dùng {@code DOTALL} nên {@code [^,;]*?} vắt được qua xuống dòng, và trong
     * {@code cms/V202608191016} có đúng một khối chú thích nằm giữa: <i>"module content không được
     * import entity của core, nên nó <b>cầm</b> UUID chứ không cầm khoá chạy số"</i>. Mẫu khớp từ
     * chữ <b>{@code cầm}</b> xuyên qua chú thích tới cột thật ⇒ bộ canh báo thiếu một "cột" tên
     * {@code cầm}.
     *
     * <p>⚠ Đây là <b>lần thứ tư trong một ngày</b> một bộ canh quét văn bản khớp sai — hai lần quá
     * hẹp, một lần quá rộng, và lần này <b>đỏ giả</b>. Cùng một gốc: khớp trên <i>văn bản thô</i>
     * thay vì trên thứ mình thật sự muốn hỏi. ⇒ {@link #boChuThichSql} chạy trước mọi phép khớp.
     */
    private static final Pattern COT_SET_NULL = Pattern.compile(
            "(\\w+)\\s+UUID[^,;]*?REFERENCES\\s+attachments\\s*\\(\\s*public_id\\s*\\)[^,;]*?ON\\s+DELETE\\s+SET\\s+NULL",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** {@code CREATE TABLE x} / {@code ALTER TABLE x} — để so theo CẶP (bảng, cột). */
    private static final Pattern TEN_BANG = Pattern.compile(
            "(?:CREATE|ALTER)\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?(\\w+)", Pattern.CASE_INSENSITIVE);

    /**
     * ⛔ Bóc chú thích {@code --} <b>trước</b> khi khớp.
     *
     * <p>⚠ Cắt theo <b>dòng</b>, ⛔ không theo ký tự: một chuỗi SQL chứa {@code --} sẽ bị cắt oan.
     * Giới hạn ấy khai ra ở đây thay vì để nó im — migration của kho ⛔ không có chuỗi nào như vậy
     * (đã kiểm), và ngày có thì bộ canh đỏ chứ ⛔ không mù.
     */
    private static String boChuThichSql(String sql) {
        StringBuilder ket = new StringBuilder();
        for (String dong : sql.split("\n", -1)) {
            int i = dong.indexOf("--");
            ket.append(i >= 0 ? dong.substring(0, i) : dong).append('\n');
        }
        return ket.toString();
    }

    @Test
    @DisplayName("⭐⭐ Mọi cột CMS khai ON DELETE SET NULL đều có mặt trong bộ gỡ tham chiếu")
    void moiCotSetNullDeuCoTrongBoGo() {
        Set<String> cotTrongLuocDo = cotKhaiSetNull();
        Set<String> cotDuocGo = new LinkedHashSet<>();
        for (String[] noi : CmsAttachmentRefCleaner.BANG_CO_THAM_CHIEU) {
            cotDuocGo.add(noi[0] + "." + noi[1]);
        }

        // ⚠ Vế chống xanh-trên-tập-rỗng (luật 7 + 29): mẫu hỏng ⇒ tập rỗng ⇒ `isSubsetOf` xanh trọn
        //   vẹn. Khẳng định về SỐ LƯỢNG ⛔ không chia sẻ giả định nào với mẫu regex.
        assertThat(cotTrongLuocDo)
                .as(
                        "⛔ Bộ tách ⛔ không thấy cột ON DELETE SET NULL nào trong %s — mẫu hỏng hoặc thư mục "
                                + "đổi tên. Cả hai làm bài này mù chứ ⛔ không đỏ.",
                        THU_MUC_MIGRATION_CMS)
                .hasSizeGreaterThanOrEqualTo(2);
        assertThat(CmsAttachmentRefCleaner.BANG_CO_THAM_CHIEU)
                .as("⛔ Danh sách bảng trong bộ gỡ rỗng hoặc hụt")
                .hasSizeGreaterThanOrEqualTo(3);

        assertThat(cotTrongLuocDo)
                .as(
                        """
                        ⛔ Có cột khai `ON DELETE SET NULL` trỏ vào `attachments` mà bộ gỡ ⛔ KHÔNG chạm tới. \
                        Ràng buộc ấy CHƯA TỪNG BẮN — xoá đính kèm là xoá MỀM, nên với CSDL ⛔ không có gì bị \
                        xoá và ⛔ không có gì để SET NULL. Danh sách Java là thứ DUY NHẤT thật sự gỡ tham \
                        chiếu; thiếu một cột ở đó thì cột ấy giữ UUID của một tệp đã chết và cổng dựng một \
                        liên kết/ảnh hỏng, ⛔ im lặng. Bộ gỡ đang phủ: %s""",
                        cotDuocGo)
                .isSubsetOf(cotDuocGo);
    }

    /**
     * ⚠ Vế tự kiểm (luật 29) — bộ tách phải <b>phân biệt được</b> cột có {@code ON DELETE SET NULL}
     * với cột chỉ {@code REFERENCES} trần. ⛔ Không có vế này thì một mẫu bắt mọi
     * {@code REFERENCES attachments} vẫn xanh, và nó sẽ đòi gỡ cả cột {@code NOT NULL} của
     * {@code banners} — tức đỏ trên một thiết kế <b>đúng</b>.
     */
    /** ⚠ Vế tự kiểm thứ hai — chú thích tiếng Việt ⛔ không được biến thành một "cột". */
    @Test
    @DisplayName("⛔ Tự kiểm: dòng CHÚ THÍCH nằm giữa ⛔ KHÔNG tạo ra một cột ma")
    void chuThichKhongTaoRaCotMa() {
        String thatSu =
                """
                CREATE TABLE categories (
                    parent_id BIGINT REFERENCES categories (id),
                    -- Ảnh đại diện trỏ tới `attachments` bằng public_id: module `content` không được
                    -- import entity của core, nên nó cầm UUID chứ không cầm khoá chạy số.
                    cover_attachment_public_id UUID REFERENCES attachments (public_id) ON DELETE SET NULL,
                );""";

        assertThat(trichCot(thatSu))
                .as("⛔ Bản đầu của mẫu này bắt trúng chữ `cầm` trong câu tiếng Việt và báo THIẾU một "
                        + "cột tên `cầm` — đỏ giả trên mã ĐÚNG. Bóc chú thích trước là bản vá.")
                .containsExactly("categories.cover_attachment_public_id");
    }

    @Test
    @DisplayName("⚠ Tự kiểm: cột REFERENCES trần (banners) ⛔ KHÔNG bị tính là cột phải gỡ")
    void boTachPhanBietDuocHaiKieuRangBuoc() {
        String coSetNull = "    cover_attachment_public_id UUID REFERENCES attachments (public_id) ON DELETE SET NULL,";
        String khongSetNull =
                "    image_attachment_public_id UUID         NOT NULL REFERENCES attachments (public_id),";

        assertThat(trichCot("CREATE TABLE categories (\n" + coSetNull + "\n);"))
                .containsExactly("categories.cover_attachment_public_id");
        assertThat(trichCot("CREATE TABLE banners (\n" + khongSetNull + "\n);"))
                .as("⛔ `banners.image_attachment_public_id` là NOT NULL và ⛔ không khai ON DELETE SET "
                        + "NULL — gỡ nó về NULL là vi phạm ràng buộc, ⛔ không phải một bản vá")
                .isEmpty();
    }

    // -------------------------------------------------------------------------

    private static Set<String> cotKhaiSetNull() {
        Path thuMuc = timTuGocKho(THU_MUC_MIGRATION_CMS);
        try (Stream<Path> cay = Files.walk(thuMuc)) {
            StringBuilder gop = new StringBuilder();
            for (Path p :
                    cay.filter(p -> p.toString().endsWith(".sql")).sorted().toList()) {
                gop.append(doc(p)).append('\n');
            }
            return trichCot(gop.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @return cặp {@code "bảng.cột"} — ⛔ <b>không</b> chỉ tên cột.
     *
     * <p>{@code cover_attachment_public_id} có ở <b>hai</b> bảng ({@code categories} và
     * {@code articles}); so bằng tên trần thì bộ gỡ chỉ cần phủ <i>một</i> trong hai là xanh, và bảng
     * còn lại giữ tham chiếu chết mà ⛔ không ai biết.
     */
    private static Set<String> trichCot(String sqlTho) {
        String sql = boChuThichSql(sqlTho);
        Set<String> ket = new LinkedHashSet<>();
        Matcher m = COT_SET_NULL.matcher(sql);
        while (m.find()) {
            ket.add(bangGanNhat(sql, m.start()) + "." + m.group(1));
        }
        return ket;
    }

    /** Tên bảng của câu lệnh {@code CREATE/ALTER TABLE} gần nhất đứng TRƯỚC vị trí này. */
    private static String bangGanNhat(String sql, int viTri) {
        Matcher m = TEN_BANG.matcher(sql);
        String ten = "?";
        while (m.find() && m.start() < viTri) {
            ten = m.group(1);
        }
        return ten;
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
