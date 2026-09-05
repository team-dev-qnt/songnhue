package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Lệnh chạy được trong {@code docs/} không được trỏ vào host không phân giải — T11.53.</b>
 *
 * <h2>Cái sai bài này sinh ra để chặn</h2>
 *
 * {@code deploy-guideline.md} §5 có sáu lệnh nghiệm thu staging, và cả sáu gõ
 * {@code https://staging.songnhue.vn}. Đo 28/8 và lại 3/9/2026: host ấy trả <b>HTTP 000, không phân
 * giải được</b> — tên miền {@code .vn} chưa mua, chủ thể đăng ký phải là Công ty (nợ T11.2-b). Site
 * thật chạy ở {@code staging.songnhue.com}, HTTP 200.
 *
 * <p>⛔ Hại thật chứ không chỉ khó coi: người theo tài liệu để nghiệm thu gõ lệnh, nhận HTTP 000, và
 * <b>kết luận hệ thống chết</b> trong khi nó đang chạy. Đó đúng là loại kết luận sai mà {@code docs/}
 * tồn tại để chặn.
 *
 * <h2>⚠ Bất biến được canh là "LỆNH CHẠY ĐƯỢC", không phải "có nhắc tới"</h2>
 *
 * Bài này phân biệt <b>khối mã</b> (trong hàng rào ```) với <b>văn xuôi</b>. Văn xuôi được phép — và
 * phải được phép — nói tới {@code staging.songnhue.vn}: chính khối cảnh báo giải thích vì sao nó
 * chưa dùng được cũng phải gọi tên nó. Thứ cấm là <b>một dòng người ta sẽ sao chép rồi dán vào
 * terminal</b>. Cấm theo chuỗi thay vì theo vị trí sẽ biến khối cảnh báo thành vi phạm, và cách
 * người ta chữa một bộ canh báo oan là gỡ nó (luật 2).
 *
 * <h2>⚠ Bộ canh này có hạn dùng (luật 28)</h2>
 *
 * Danh sách {@link #HOST_CHUA_PHAN_GIAI} đúng <b>cho tới khi mua được tên miền {@code .vn}</b>
 * (T11.2-b). Lúc ấy tập này về rỗng và bài kiểm phải được sửa — không phải gỡ: đảo lại, nó sẽ canh
 * chiều ngược lại. Ghi rõ ở đây để lượt sau không phải đoán.
 */
class TenMienTaiLieuTest {

    /**
     * Host xuất hiện trong tài liệu mà <b>không phân giải được</b> (đo bằng {@code curl}, 3/9/2026 →
     * HTTP 000). Ba tên miền phụ staging của {@code .vn} — tên miền chưa mua.
     */
    private static final List<String> HOST_CHUA_PHAN_GIAI =
            List.of("staging.songnhue.vn", "admin-staging.songnhue.vn", "files-staging.songnhue.vn");

    /** Host staging ĐANG CHẠY — đối chứng phải-tìm-thấy cho phép quét. */
    private static final String HOST_DANG_CHAY = "staging.songnhue.com";

    /**
     * ⚠ Viết dạng <b>hằng chuỗi</b>, không dựng bằng biến — {@code CiPathFilterTest} quét mã nguồn
     * test tìm hằng chuỗi trỏ ra ngoài {@code backend/} rồi đối chiếu với bộ lọc đường dẫn của
     * {@code ci.yml}. Dựng đường dẫn bằng biến là tàng hình trước phép quét ấy, và khi đó bộ lọc có
     * bỏ sót {@code docs/} cũng không ai biết (luật 24).
     */
    private static final String THU_MUC_DOCS = "docs/";

    @Test
    @DisplayName("⭐⭐ Không lệnh nào trong docs/ gõ vào một host chưa phân giải")
    void khongLenhNaoTroVaoHostChet() throws IOException {
        List<String> viPham = new ArrayList<>();
        int soDongLenhCoHostThat = 0;

        for (Path tep : tepMarkdown()) {
            String tuongDoi = gocKho().relativize(tep).toString();
            // ⚠ Gọi CHÍNH `dongTrongKhoiMa` — cùng đường mà bài tự kiểm chứng minh. Chép lại vòng
            //   lặp ở đây là dựng hai bộ dò, và bài tự kiểm sẽ chứng minh cho bộ KHÔNG chạy.
            List<String> khoiMa = dongTrongKhoiMa(Files.readString(tep, StandardCharsets.UTF_8));

            soDongLenhCoHostThat += (int)
                    khoiMa.stream().filter(d -> d.contains(HOST_DANG_CHAY)).count();
            khoiMa.stream()
                    .filter(d -> HOST_CHUA_PHAN_GIAI.stream().anyMatch(d::contains))
                    .forEach(d -> viPham.add(tuongDoi + "  " + d));
        }

        // ⛔ Đối chứng phải-tìm-thấy (luật 7). Không có nó thì một lỗi ở bộ đọc — sai đường dẫn, sai
        //    cách nhận hàng rào ``` — cho ra danh sách vi phạm RỖNG và bài xanh mà chưa quét gì.
        assertThat(soDongLenhCoHostThat)
                .as(
                        "không thấy dòng lệnh nào chứa `%s` trong docs/ — phép quét đang không đọc "
                                + "được khối mã, nên danh sách vi phạm rỗng KHÔNG có nghĩa là sạch",
                        HOST_DANG_CHAY)
                .isGreaterThanOrEqualTo(3);

        assertThat(viPham)
                .as(
                        """
                        Những dòng này nằm trong khối mã — tức là người ta sẽ sao chép và chạy — mà \
                        trỏ vào host KHÔNG PHÂN GIẢI ĐƯỢC (đo 3/9/2026: HTTP 000). Người nghiệm thu \
                        sẽ kết luận hệ thống chết trong khi nó đang chạy ở %s.
                        Văn xuôi nhắc tới các host ấy thì không sao — chỗ giải thích vì sao chúng \
                        chưa dùng được buộc phải gọi tên chúng.""",
                        HOST_DANG_CHAY)
                .isEmpty();
    }

    /**
     * ⭐ Tự kiểm: chứng minh bộ dò phân biệt được <b>trong khối mã</b> với <b>ngoài khối mã</b>.
     *
     * <p>Đây là chỗ bài này dễ sai nhất, và sai theo hướng khó thấy: một bộ dò không phân biệt được
     * hai vị trí sẽ báo oan chính khối cảnh báo T11.53, người ta gỡ bộ canh, và ba tháng sau lỗi cũ
     * quay lại không ai chặn.
     */
    @Test
    @DisplayName("⭐ Tự kiểm: bắt trong khối mã, KHÔNG bắt trong văn xuôi")
    void tuKiemPhanBietKhoiMaVoiVanXuoi() {
        String gia =
                """
                # Tài liệu giả

                > ⚠ `https://staging.songnhue.vn` chưa phân giải — đây là VĂN XUÔI, phải được bỏ qua.

                ```bash
                curl -fsS https://staging.songnhue.vn/api/v1/public/site-config
                curl -fsS https://staging.songnhue.com/api/v1/public/site-config
                ```
                """;

        List<String> trongMa = timTrongKhoiMa(gia, HOST_CHUA_PHAN_GIAI);
        assertThat(trongMa)
                .as("bộ dò phải bắt đúng MỘT dòng — dòng nằm trong khối ```bash")
                .hasSize(1);
        assertThat(trongMa.get(0)).contains("curl").contains("staging.songnhue.vn");

        // Cùng nội dung nhưng bỏ hết hàng rào ``` ⇒ mọi dòng thành văn xuôi ⇒ phải KHÔNG bắt gì.
        String khongHangRao = gia.replace("```bash", "").replace("```", "");
        assertThat(timTrongKhoiMa(khongHangRao, HOST_CHUA_PHAN_GIAI))
                .as("gỡ hàng rào mà vẫn bắt ⇒ bộ dò đang khớp CHUỖI chứ không đọc VỊ TRÍ, và nó "
                        + "sẽ báo oan chính khối cảnh báo giải thích nợ này")
                .isEmpty();
    }

    // -------------------------------------------------------------------------

    /**
     * Các dòng nằm TRONG khối mã của một nội dung markdown — bộ dò DUY NHẤT của bài này.
     *
     * <p>Cả phép quét thật lẫn bài tự kiểm đều đi qua hàm này. Đó là điểm mấu chốt: một bài tự kiểm
     * chạy trên bộ dò riêng của nó chứng minh cho một cơ chế không ai dùng (luật 14).
     */
    private static List<String> dongTrongKhoiMa(String noiDung) {
        List<String> ket = new ArrayList<>();
        boolean trongKhoiMa = false;
        for (String d : noiDung.split("\n", -1)) {
            if (d.stripLeading().startsWith("```")) {
                trongKhoiMa = !trongKhoiMa;
                continue;
            }
            if (trongKhoiMa) {
                ket.add(d.strip());
            }
        }
        return ket;
    }

    /** Lọc ra các dòng khối mã có chứa một trong các host. */
    private static List<String> timTrongKhoiMa(String noiDung, List<String> host) {
        return dongTrongKhoiMa(noiDung).stream()
                .filter(d -> host.stream().anyMatch(d::contains))
                .toList();
    }

    private static List<Path> tepMarkdown() throws IOException {
        try (Stream<Path> cay = Files.walk(gocKho().resolve(THU_MUC_DOCS))) {
            return cay.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }
    }

    private static Path gocKho() {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            if (Files.isDirectory(hienTai.resolve(THU_MUC_DOCS)) && Files.isDirectory(hienTai.resolve("deploy/"))) {
                return hienTai;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy gốc kho tính từ %s".formatted(System.getProperty("user.dir")));
    }
}
