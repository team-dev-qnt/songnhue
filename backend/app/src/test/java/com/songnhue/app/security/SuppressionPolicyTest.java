package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.songnhue.core.common.util.DateTimeUtils;

/**
 * <b>Tệp suppression tự khai bốn luật ở đầu nó — và tới 1/9/2026 không gì thi hành cả.</b>
 *
 * <p>Luật số 1 của `dependency-check-suppressions.xml` là <i>"Mọi mục PHẢI có {@code until}"</i>, kèm
 * lý do: <i>"Suppression không hạn là cách êm ái nhất để một lỗ hổng thật biến mất khỏi tầm mắt."</i>
 * Đúng, và đó chính là loại luật cần một bài kiểm — vì người thêm mục thứ ba sẽ là người không đọc
 * phần đầu tệp.
 *
 * <p>⛔ Suppression là chỗ <b>duy nhất</b> trong dự án mà một dòng chữ có thể làm một CVE 9.8 biến mất
 * khỏi mọi bảng điều khiển. Cổng quét vẫn xanh, báo cáo vẫn đẹp, và không ai biết. Ràng buộc phải nằm
 * ở nơi <b>dữ liệu đi qua</b> — tức chính tệp ấy — chứ không nằm ở lời dặn trong phần chú thích
 * (CLAUDE.md luật 12, luật 15).
 *
 * <h2>⛔⛔ T85.1 (22/09/2026) — bài này kiểm {@code until} <b>CÓ MẶT</b>, và tới hôm nay ⛔ kiểm nó
 * <b>CÒN HẠN</b></h2>
 *
 * <p>Đó là luật 7 ở đúng chỗ nguy hiểm nhất của nó: một suppression <b>đã hết hạn đọc y hệt một
 * suppression đang sống</b>. Thứ duy nhất nhận ra là lượt quét đêm — và §10.68 đã ghi cái giá của
 * việc đó: cổng CVE đỏ <i>hơn một ngày</i> mà ⛔ ai đọc. ⇒ {@link #moiMucPhaiConHanDuLau} đòi
 * {@code until} còn hơn {@value #NGAY_BAO_TRUOC} ngày, tức chuyển chỗ đỏ từ một lượt chạy theo lịch
 * sang một cổng bắt buộc chặn PR, kèm đủ thời gian để thẩm định lại tử tế.
 *
 * <p>⚠ <b>Giới hạn phải khai ra</b> (luật 28): bài này bắt mục <i>sắp quá hạn</i> theo <b>thời
 * gian</b>; nó ⛔ bắt được mục <i>đã chết</i> theo <b>phạm vi</b> — một mẫu ghim phiên bản mà ⛔ còn
 * khớp gói nào. Phát hiện ấy đòi cây phụ thuộc đã giải, quá đắt cho một bài JUnit tĩnh. Thứ bắt được
 * nó là chính lượt thẩm định định kỳ mà bài này ép xảy ra — đúng cách mục {@code CVE-2026-59313} bị
 * lôi ra và gỡ ngày 22/09/2026.
 */
class SuppressionPolicyTest {

    private static final Path TEP = timTuGocKho("backend/dependency-check-suppressions.xml");

    /** Một khối {@code <suppress …> … </suppress>} nguyên văn. */
    private static List<String> boMuc(String xml) {
        List<String> ket = new ArrayList<>();
        Matcher m = Pattern.compile("<suppress\\b[\\s\\S]*?</suppress>").matcher(xml);
        while (m.find()) {
            ket.add(m.group());
        }
        return ket;
    }

    @Test
    @DisplayName("⭐⭐ Mọi mục suppression PHẢI có `until` — không hạn là cách êm ái nhất để quên một lỗ hổng")
    void moiMucPhaiCoHan() {
        List<String> muc = boMuc(doc(TEP));

        for (String m : muc) {
            assertThat(m)
                    .as(
                            """
                            Có mục suppression KHÔNG đặt `until`:

                            %s
                            Hết hạn thì phép quét tự đỏ lại và buộc người ta nhìn lại — đó là tính \
                            năng, không phải phiền toái. Một mục không hạn thì CVE ấy biến mất khỏi \
                            mọi bảng điều khiển vĩnh viễn, và cổng quét vẫn xanh.""",
                            tomTat(m))
                    .containsPattern("<suppress[^>]*\\buntil\\s*=");
        }
    }

    /**
     * Số ngày báo trước tối thiểu — <b>T85.1</b>.
     *
     * <p>⛔⛔ Con số này ⛔ phải một sở thích: nó là khoảng cách giữa <i>lúc biết</i> và <i>lúc phải
     * xong</i>. Một lượt thẩm định lại CVE gồm: tra bản vá đã ra chưa ({@code maven-metadata.xml}, ⛔
     * API tìm kiếm — luật 21) → nếu có thì nâng và đo lại → nếu chưa thì viết lý do mới. Với dự án
     * này đó là việc của một đợt, ⛔ phải một buổi. <b>30 ngày</b> đủ cho một đợt mà ⛔ dài tới mức
     * người ta quên mất mình đang hoãn cái gì.
     *
     * <p>⚠ Hệ quả PHẢI KHAI RA: bài này <b>đỏ theo lịch</b>, ⛔ theo commit — nó sẽ đỏ vào một ngày
     * ⛔ ai đụng vào mã. Đó <b>là tính năng</b>, và chính tệp suppression đã tự khai như vậy ở đầu nó
     * (<i>"Hết hạn thì phép quét tự đỏ lại và buộc người ta nhìn lại"</i>). Thứ bài này đổi là
     * <b>CHỖ</b> đỏ: từ một lượt quét đêm ⛔ ai đọc (§10.68 — cổng CVE đỏ hơn một ngày ⛔ ai thấy)
     * sang một cổng bắt buộc chặn PR.
     */
    private static final int NGAY_BAO_TRUOC = 30;

    @Test
    @DisplayName("⭐⭐ T85.1 — `until` phải còn HƠN 30 ngày: một mục đã hết hạn đọc y hệt một mục đang sống")
    void moiMucPhaiConHanDuLau() {
        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);

        for (String m : boMuc(doc(TEP))) {
            LocalDate han = docHan(m);
            long conLai = ChronoUnit.DAYS.between(homNay, han);

            assertThat(conLai)
                    .as(
                            """
                            Suppression hết hạn %s — còn %d ngày, dưới mức báo trước %d ngày:

                            %s
                            ⛔ Đây ⛔ phải lỗi của mã; nó là một lượt HOÃN sắp đáo hạn. Hai đường đi đúng:

                              (a) Mục ⛔ còn áp cho gói nào nữa (đã nâng phiên bản, đã đổi phụ thuộc) ⇒ \
                            XOÁ HẲN mục. Một mục chết đọc y hệt một mục đang che một lỗ hổng thật.
                              (b) Vẫn còn áp ⇒ THẨM ĐỊNH LẠI: bản vá đã ra chưa (tra \
                            `maven-metadata.xml`, ⛔ API tìm kiếm — luật 21)? Có thì nâng rồi xoá mục. \
                            Chưa thì viết lý do MỚI kèm ngày thẩm định và neo `until` mới.

                            ⛔ Tuyệt đối ⛔ chỉ dời ngày cho hết đỏ — đó là biến một lượt hoãn CÓ HẠN \
                            thành một lượt hoãn vĩnh viễn, đúng thứ luật số 1 của tệp này cấm.""",
                            han, conLai, NGAY_BAO_TRUOC, tomTat(m))
                    .isGreaterThan(NGAY_BAO_TRUOC);
        }
    }

    @Test
    @DisplayName("⭐ Mỗi mục phải NÊU LÝ DO và phải có PHẠM VI — cấm suppression trần")
    void moiMucPhaiCoLyDoVaPhamVi() {
        for (String m : boMuc(doc(TEP))) {
            assertThat(m).as("Mục thiếu `<notes>`: %s", tomTat(m)).contains("<notes>");

            String noiDung = m.replaceAll("[\\s\\S]*<notes><!\\[CDATA\\[", "").replaceAll("]]></notes>[\\s\\S]*", "");
            assertThat(noiDung.strip().length())
                    .as("`<notes>` quá ngắn để là một lượt thẩm định thật: %s", tomTat(m))
                    .isGreaterThan(80);

            assertThat(m)
                    .as(
                            """
                            Mục suppression KHÔNG giới hạn phạm vi:

                            %s
                            Thiếu `packageUrl`/`gav`/`filePath`/`cpe` thì nó áp cho MỌI artifact — \
                            một mã bị bỏ qua ở chỗ đã thẩm định sẽ đồng thời bị bỏ qua ở mọi chỗ \
                            chưa ai nhìn.""",
                            tomTat(m))
                    .containsPattern("<(packageUrl|gav|filePath|cpe)\\b");

            assertThat(m)
                    .as("Mục phải chỉ đích danh CVE, không suppress cả gói: %s", tomTat(m))
                    .containsPattern("<(cve|vulnerabilityName)\\b");
        }
    }

    @Test
    @DisplayName("⛔ Bộ đọc phải ĐỌC ĐƯỢC THẬT — mọi khẳng định trên đều xanh trọn vẹn khi không thấy mục nào")
    void boDocKhongDuocRong() {
        // Luật 7. Ba bài trên duyệt một danh sách; danh sách rỗng thì cả ba xanh mà không kiểm gì.
        List<String> muc = boMuc(doc(TEP));

        assertThat(muc)
                .as("Không đọc được mục suppression nào — mẫu regex hỏng, hoặc tệp đã đổi cấu trúc")
                .isNotEmpty();
        assertThat(doc(TEP))
                .as("Tệp phải là tệp suppression thật, không phải một tệp rỗng trùng tên")
                .contains("<suppressions");
    }

    @Test
    @DisplayName("⛔ Và bộ dò phải BẮT ĐƯỢC vi phạm — nếu không thì nó chỉ đang khen tệp hiện tại")
    void boDoBatDuocViPham() {
        // Ba dạng vi phạm, mỗi dạng phải bị đúng một khẳng định bắt. Không có bài này thì một mẫu
        // regex viết sai vẫn xanh trên tệp đang đúng (luật 1, luật 29).
        String khongHan = "<suppress>\n  <notes><![CDATA[ %s ]]></notes>\n  <cve>CVE-1</cve>\n</suppress>"
                .formatted("x".repeat(100));
        String khongPhamVi =
                "<suppress until=\"2026-12-31Z\">\n  <notes><![CDATA[ %s ]]></notes>\n  <cve>CVE-1</cve>\n</suppress>"
                        .formatted("x".repeat(100));

        assertThat(boMuc(khongHan)).hasSize(1);
        assertThat(boMuc(khongHan).get(0)).doesNotContainPattern("<suppress[^>]*\\buntil\\s*=");
        assertThat(boMuc(khongPhamVi).get(0)).doesNotContainPattern("<(packageUrl|gav|filePath|cpe)\\b");

        // T85.1 — vế PHÂN BIỆT của bộ đọc ngày. Không có nó thì một `docHan` luôn trả một ngày rất xa
        // vẫn làm `moiMucPhaiConHanDuLau` xanh trọn vẹn trên MỌI tệp (luật 9 + luật 29).
        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);
        String daHetHan = mucVoiHan(homNay.minusDays(1));
        String conLau = mucVoiHan(homNay.plusDays(NGAY_BAO_TRUOC + 1));

        assertThat(ChronoUnit.DAYS.between(homNay, docHan(daHetHan)))
                .as("mục hết hạn hôm qua phải ra số ÂM — nếu nó ra số dương thì bài hạn đang đo một thứ khác")
                .isEqualTo(-1);
        assertThat(ChronoUnit.DAYS.between(homNay, docHan(conLau)))
                .as("và mục còn xa phải VƯỢT ngưỡng — hai trạng thái phải đọc khác nhau")
                .isGreaterThan(NGAY_BAO_TRUOC);
    }

    /** Một mục suppression hợp lệ mọi mặt TRỪ ngày — dùng cho vế tự-kiểm ở trên. */
    private static String mucVoiHan(LocalDate han) {
        return """
                <suppress until="%sZ">
                  <notes><![CDATA[ %s ]]></notes>
                  <packageUrl regex="true">^pkg:maven/x/y.*$</packageUrl>
                  <cve>CVE-0000-0000</cve>
                </suppress>"""
                .formatted(han, "x".repeat(100));
    }

    @Test
    @DisplayName("⭐ Bộ lọc đường dẫn của lượt quét phải bao chính tệp này — luật 24")
    void congQuetPhaiChayLaiKhiTepNayDoi() {
        // Luật 24: bộ lọc phải bao những tệp mà bài kiểm ĐỌC, không chỉ tệp nó nằm cùng thư mục.
        // Đây là tệp ảnh hưởng TRỰC TIẾP nhất tới việc lượt quét báo gì — thêm một mục vào đây có thể
        // làm một CVE 9.8 biến mất khỏi báo cáo. Thiếu nó trong `paths:` thì thay đổi ấy chỉ được kiểm
        // ở lượt chạy theo lịch hôm sau.
        String w = doc(timTuGocKho(".github/workflows/security-scan.yml"));

        assertThat(w)
                .as(
                        """
                        `security-scan.yml` không chạy lại khi `dependency-check-suppressions.xml` đổi.

                        Sửa tệp suppression là cách nhanh nhất làm đổi kết quả quét, mà lượt push lại \
                        không kiểm nó — phải đợi lịch đêm. Thêm đường dẫn ấy vào khối `paths:`.""")
                .contains("backend/dependency-check-suppressions.xml");
    }

    // -------------------------------------------------------------------------

    /**
     * Ngày ở thuộc tính {@code until} của một mục — <b>T85.1</b>.
     *
     * <p>dependency-check nhận {@code yyyy-MM-dd} kèm hậu tố múi giờ tuỳ chọn ({@code Z}). Bộ đọc này
     * <b>ném</b> khi ⛔ đọc ra ngày: {@link #moiMucPhaiCoHan} bảo đảm thuộc tính có mặt, nhưng
     * <i>"có mặt"</i> và <i>"đọc được"</i> là hai chuyện — một giá trị gõ sai sẽ làm bài trên xanh
     * còn bài hạn thì im lặng bỏ qua mục ấy, tức xanh vì lý do sai (luật 9).
     */
    private static LocalDate docHan(String muc) {
        Matcher m = Pattern.compile("<suppress[^>]*\\buntil\\s*=\\s*\"(\\d{4}-\\d{2}-\\d{2})Z?\"")
                .matcher(muc);
        if (!m.find()) {
            return fail("⛔ đọc được ngày `until` (định dạng phải là `yyyy-MM-dd[Z]`) ở mục: %s".formatted(tomTat(muc)));
        }
        return LocalDate.parse(m.group(1));
    }

    private static String tomTat(String muc) {
        String mot = muc.replaceAll("\\s+", " ");
        return mot.length() > 220 ? mot.substring(0, 220) + " …" : mot;
    }

    private static String doc(Path duongDan) {
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
