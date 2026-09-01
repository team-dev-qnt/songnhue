package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
