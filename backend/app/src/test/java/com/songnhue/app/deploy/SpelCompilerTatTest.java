package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Trình biên dịch SpEL phải TẮT tường minh ở mọi đường JVM khởi động — T11.73.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * Lượt quét CVE 3/9/2026 đỏ với <b>7 mã ≥ 7</b>, trong đó <b>CVE-2026-59283 (9.1)</b> là lách được
 * lớp bảo vệ của {@code SimpleEvaluationContext} <b>khi trình biên dịch SpEL đang bật</b>. Bản vá
 * nằm ở Spring Framework {@code 7.0.9}; dòng {@code 6.2.x} ta đang dùng hết hỗ trợ OSS
 * <b>30/6/2026</b> và {@code 6.2.20} trả <b>HTTP 404</b> trên Central — nghĩa là mã này
 * <b>không vá được</b> cho tới khi lên Boot 4 (T11.69).
 *
 * <p>Trong khoảng ấy, thứ duy nhất đứng giữa hệ thống và một mã 9.1 là <b>giá trị mặc định
 * {@code OFF}</b> của chính framework. Đo 3/9/2026: chuỗi {@code spring.expression.compiler.mode}
 * <b>không xuất hiện ở bất kỳ đâu trong kho</b> — không {@code application.yml}, không compose,
 * không Dockerfile.
 *
 * <h2>Vì sao một mặc định đúng vẫn là một lỗi</h2>
 *
 * Luật 3 — <i>canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH</i>. Mặc định là thứ của người
 * khác: nó đổi theo bản nâng cấp, và không ai gửi thông báo cho ta khi nó đổi. Một giá trị khai
 * tường minh thì ngược lại — nó là một dòng có người duyệt, đo được từ ngoài, và khi ai đó muốn
 * đổi thì phải đổi ở một chỗ nhìn thấy được.
 *
 * <p>⚠ <b>Giữ nguyên cả sau khi lên Boot 4.</b> Bản vá 7.0.9 xử lý lỗ hổng <i>đã biết</i>; dòng
 * cấu hình này xử lý cả những lỗ hổng cùng họ <i>chưa ai đặt tên</i>. Gỡ nó sau di trú là đánh đổi
 * một bảo đảm vĩnh viễn lấy một bản vá điểm.
 *
 * <h2>Vì sao phải khai ở HAI nơi</h2>
 *
 * Có hai đường biến JVM đi vào, và chúng phục vụ hai lượt chạy khác nhau:
 *
 * <ul>
 *   <li>{@code deploy/docker/backend.Dockerfile} → {@code ENV JAVA_OPTS} — mặc định của image,
 *       là thứ có hiệu lực khi ai đó {@code docker run} ảnh trần (dựng lại sự cố, chạy thử).
 *   <li>{@code deploy/compose.prod.yml} → {@code JAVA_TOOL_OPTIONS} — đường triển khai thật.
 * </ul>
 *
 * Bỏ một trong hai thì có một đường chạy không được che, và đó đúng hình dạng luật 12: đặt bảo
 * đảm ở <i>chỗ dữ liệu đi qua</i>, và không đặt được ở một chỗ thì phải đếm đủ các đường vào.
 *
 * <h2>⛔ GIỚI HẠN — ghi vào chính bộ canh (luật 28)</h2>
 *
 * Bài này chứng minh giá trị đã được <b>KHAI</b> trong hai tệp triển khai. Nó <b>KHÔNG</b> chứng
 * minh JVM đang chạy đã <b>GIẢI</b> ra giá trị ấy — nửa sau chỉ đo được trên container thật:
 *
 * <pre>{@code
 * ssh $U@$H 'docker inspect -f "{{json .Config.Env}}" songnhue-app' | grep -o 'compiler.mode=[a-z]*'
 * }</pre>
 */
class SpelCompilerTatTest {

    /** Khoá thuộc tính điều khiển trình biên dịch SpEL — định nghĩa ĐÚNG MỘT LẦN cho cả bài. */
    private static final String KHOA = "spring.expression.compiler.mode";

    /** Giá trị bắt buộc. {@code immediate} và {@code mixed} đều BẬT trình biên dịch. */
    private static final String GIA_TRI_BAT_BUOC = "off";

    /**
     * Hai tệp mang biến JVM. ⚠ Viết dạng <b>hằng chuỗi</b> để {@code CiPathFilterTest} quét thấy —
     * dựng đường dẫn bằng biến là tàng hình trước phép quét ấy, và khi đó bộ lọc {@code ci.yml} có
     * bỏ sót {@code deploy/} cũng không ai biết (luật 24).
     */
    private static final List<String> TEP_TRIEN_KHAI =
            List.of("deploy/compose.prod.yml", "deploy/docker/backend.Dockerfile");

    /**
     * Chứng nhân phải-tìm-thấy: cờ này đã có trong cả hai tệp TRƯỚC đợt vá này. Nếu bộ dò hỏng thì
     * nó cũng không thấy cờ này — nên một danh sách vi phạm rỗng mới phân biệt được với một phép
     * quét không đọc được gì (luật 7).
     */
    private static final String CHUNG_NHAN = "user.timezone";

    @Test
    @DisplayName("⭐⭐ Cả hai đường JVM đều khai spring.expression.compiler.mode=off")
    void moiDuongJvmDeuTatTrinhBienDichSpel() throws IOException {
        Map<String, Map<String, String>> theoTep = new LinkedHashMap<>();
        for (String tep : TEP_TRIEN_KHAI) {
            theoTep.put(tep, coDCua(Files.readString(timTuGocKho(tep), StandardCharsets.UTF_8)));
        }

        // ⛔ Chặn tập rỗng, hai tầng: đủ số tệp, và mỗi tệp phải parse ra được cờ chứng nhân.
        assertThat(theoTep)
                .as("không đọc ra khai báo JVM nào — phép quét hỏng thì mọi khẳng định dưới đây vô nghĩa")
                .hasSize(TEP_TRIEN_KHAI.size());
        theoTep.forEach((tep, co) -> assertThat(co)
                .as(
                        "`%s` parse ra %d cờ -D và KHÔNG có `%s` — bộ dò đang không đọc được dòng khai "
                                + "báo, chứ chưa chắc tệp thiếu cấu hình",
                        tep, co.size(), CHUNG_NHAN)
                .containsKey(CHUNG_NHAN));

        theoTep.forEach((tep, co) -> assertThat(co.get(KHOA))
                .as(
                        """
                        `%s` KHÔNG tắt trình biên dịch SpEL (đọc ra: %s).

                        CVE-2026-59283 (9.1) chỉ khai thác được khi trình biên dịch BẬT, và mã này \
                        chưa vá được — bản vá ở Spring Framework 7.0.9, tức phải lên Boot 4 (T11.69).
                        Thêm `-D%s=%s` vào khai báo JVM của tệp này.""",
                        tep, co.get(KHOA), KHOA, GIA_TRI_BAT_BUOC)
                .isEqualTo(GIA_TRI_BAT_BUOC));
    }

    /**
     * ⭐ Tự kiểm: bộ dò phải phân biệt <b>dòng khai báo thật</b> với <b>dòng chú thích</b>, và phải
     * đọc ra <b>giá trị</b> chứ không chỉ thấy chuỗi có mặt.
     *
     * <p>Đây là chỗ bài này dễ sai nhất và sai theo hướng khó thấy nhất. Cả hai tệp thật nay đều có
     * một khối chú thích dài <b>nhắc tới</b> {@code spring.expression.compiler.mode}; một bộ dò
     * khớp chuỗi sẽ xanh kể cả khi dòng khai báo thật đã bị gỡ hẳn — đúng luật 2, và đúng cái bẫy
     * {@code includes('.sn-align-center')} đã trả giá.
     */
    @Test
    @DisplayName("⭐ Tự kiểm: bỏ qua chú thích, đọc GIÁ TRỊ chứ không khớp chuỗi")
    void tuKiemBoDoDocViTriVaGiaTri() {
        String chiCoTrongChuThich =
                """
                # Nhớ đặt -Dspring.expression.compiler.mode=off cho service này.
                ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC"
                """;
        assertThat(coDCua(chiCoTrongChuThich))
                .as("khoá chỉ xuất hiện trong CHÚ THÍCH mà bộ dò vẫn thấy ⇒ nó đang khớp chuỗi chứ "
                        + "không đọc dòng khai báo, và bài trên sẽ xanh sau khi cấu hình thật bị gỡ")
                .doesNotContainKey(KHOA)
                .containsEntry(CHUNG_NHAN, "UTC");

        String batNhamGiaTri = "ENV JAVA_OPTS=\"-Duser.timezone=UTC -D" + KHOA + "=immediate\"\n";
        assertThat(coDCua(batNhamGiaTri))
                .as("`immediate` BẬT trình biên dịch — bộ dò phải đọc ra giá trị thật, không được "
                        + "hài lòng vì thấy khoá có mặt")
                .containsEntry(KHOA, "immediate");

        String dungCach = "    JAVA_TOOL_OPTIONS: \"-Duser.timezone=UTC -D" + KHOA + "=off\"\n";
        assertThat(coDCua(dungCach))
                .as("dạng khai báo của compose (`KHOÁ: \"...\"`) phải đọc được y như dạng ENV")
                .containsEntry(KHOA, GIA_TRI_BAT_BUOC);
    }

    // -------------------------------------------------------------------------

    /**
     * Dòng khai báo biến JVM — bắt cả hai dạng {@code ENV JAVA_OPTS="..."} và
     * {@code JAVA_TOOL_OPTIONS: "..."}.
     *
     * <p>{@code [^#\n]*} ở đầu là phần mang nghĩa: nó đòi <b>không có dấu {@code #} nào đứng
     * trước</b> trên cùng dòng, tức bộ dò đọc <b>vị trí</b> chứ không khớp chuỗi.
     */
    private static final Pattern DONG_KHAI_BAO =
            Pattern.compile("(?m)^[^#\\n]*\\b(?:JAVA_TOOL_OPTIONS|JAVA_OPTS)\\s*[:=]\\s*\"([^\"]*)\"");

    /**
     * Bảng {@code -Dkhoá=giá-trị} đọc từ mọi dòng khai báo JVM của một nội dung.
     *
     * <p>Bộ dò DUY NHẤT của bài này: cả phép quét thật lẫn bài tự kiểm đều đi qua hàm này. Chép lại
     * vòng lặp ở chỗ khác là dựng hai bộ dò, và bài tự kiểm sẽ chứng minh cho bộ KHÔNG chạy
     * (luật 14 — và đúng lỗi đã mắc ở {@code VongDoiPhienBanTest}).
     */
    private static Map<String, String> coDCua(String noiDung) {
        Map<String, String> ket = new LinkedHashMap<>();
        Matcher m = DONG_KHAI_BAO.matcher(noiDung);
        while (m.find()) {
            for (String token : m.group(1).trim().split("\\s+")) {
                if (!token.startsWith("-D")) {
                    continue;
                }
                String[] doi = token.substring(2).split("=", 2);
                ket.put(doi[0], doi.length > 1 ? doi[1] : "");
            }
        }
        return ket;
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
