package com.songnhue.app.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Một chỉ số được khai trong kịch bản tải mà ⛔ có ngưỡng là một phép đo ⛔ ai đọc.</b>
 *
 * <h2>Vì sao cần cổng này (T63.21)</h2>
 *
 * k6 chỉ <b>thoát 99</b> khi một <i>ngưỡng</i> bị vượt. Một chỉ số ⛔ ngưỡng vẫn được tính, vẫn in ra
 * bảng tổng kết, và ⛔ bao giờ làm lượt đo đỏ — nó trông y hệt một cam kết đang được canh, trong khi
 * nó chỉ là một dòng số để người đọc tự nhìn. Đó đúng là luật 15 (<i>một công tắc chưa ai đọc là một
 * lỗi, ⛔ phải việc để dành</i>) đặt vào bộ đo tải.
 *
 * <p>Ngày 18/09/2026, lỗ ấy đang sống: {@code cong-cong-khai.js} ⛔ có cách nào phân biệt
 * <i>"vế tìm kiếm ĐẠT"</i> với <i>"vế tìm kiếm ⛔ được đo"</i> — {@code tim_kiem_rong} chỉ nhận mẫu
 * khi {@code setup()} tìm được ít nhất một từ khoá có kết quả, nên trên một môi trường ⛔ nội dung nó
 * đi qua một <b>tập rỗng</b> và xanh trọn vẹn (luật 7). Đo được: bản trước lượt vá thoát <b>0</b> ở ca
 * {@code moc-rong} của {@code tools/tai-thu/tu-kiem/tu-kiem-tim-kiem-rong.sh}.
 *
 * <h2>Phạm vi — nói ra thay vì để người đọc tự suy (luật 28)</h2>
 *
 * <ul>
 *   <li>Phạm vi do bộ canh <b>ĐO</b>: nó liệt kê mọi tệp {@code .js} ngay dưới {@code tools/tai-thu},
 *       ⛔ giữ một danh sách gõ tay. Thêm một kịch bản mới là bài này soi nó ngay.
 *   <li>Thư mục con {@code tu-kiem} ⛔ nằm trong phạm vi: máy chủ giả ở đó ⛔ phải kịch bản k6.
 *   <li>⛔ canh chiều ngược (<i>ngưỡng trỏ vào một chỉ số ⛔ tồn tại</i>) vì <b>k6 đã bắt</b> — đo
 *       18/09/2026 với một tên gõ sai: k6 thoát <b>104</b> kèm
 *       {@code invalid threshold defined on …; reason: no metric name … found}. Dựng thêm một bộ canh
 *       cho chiều ấy là một câu trả lời thứ hai cho câu hỏi đã có câu trả lời.
 *   <li>Nó ⛔ nói gì về việc ngưỡng ĐẶT ĐÚNG CHỖ ⛔ — thứ chứng minh điều đó là lượt tự kiểm bốn ca
 *       trên máy chủ giả, nơi mỗi ca đòi <b>đúng</b> ngưỡng tương ứng phải đỏ.
 * </ul>
 */
class KichBanTaiThuTest {

    private static final String THU_MUC = "tools/tai-thu";

    /**
     * Chỉ số <b>chẩn đoán</b> — cố ý ⛔ ngưỡng. Mỗi dòng phải mang lý do ≥ 40 ký tự, theo đúng khuôn
     * {@code VongKhuHoiDuPhamViTest}: khoảng trống nào cũng phải được <b>xếp loại</b>, ⛔ được nằm im.
     */
    private static final Map<String, String> MIEN_TRU = Map.of(
            "trang_chu_byte",
            "Kích thước thân trang chủ là con số để ĐỌC khi một lượt đo bất thường, ⛔ phải một cam kết"
                    + " NFR; vế khẳng định của nó đã nằm ở check *trang chủ có nội dung (> 20 KB)*.",
            "loi_5xx",
            "Đếm 5xx TÁCH THEO TRANG để biết trang nào hỏng; cam kết về tỷ lệ lỗi đã là"
                    + " `http_req_failed: rate<0.01`, dựng thêm một ngưỡng ở đây là hai câu trả lời"
                    + " khác nhau cho cùng một câu hỏi.");

    private static final Pattern KHAI_CHI_SO =
            Pattern.compile("new\\s+(?:Rate|Counter|Trend|Gauge)\\s*\\(\\s*'([^']+)'");

    @Test
    @DisplayName("⛔⛔ Mỗi chỉ số khai trong kịch bản tải phải có NGƯỠNG, hoặc được miễn trừ kèm lý do")
    void moiChiSoPhaiCoNguongHoacLyDo() {
        Set<String> xau = new TreeSet<>();

        for (Path tep : kichBan()) {
            String ma = doc(tep);
            Set<String> nguong = tenChiSoCoNguong(khoaNguong(ma));
            for (String chiSo : chiSoKhaiBao(ma)) {
                if (!nguong.contains(chiSo) && !MIEN_TRU.containsKey(chiSo)) {
                    xau.add("%s → chỉ số `%s` ⛔ có ngưỡng và ⛔ được miễn trừ".formatted(tep.getFileName(), chiSo));
                }
            }
        }

        assertThat(xau)
                .as(
                        """
                        Một chỉ số ⛔ ngưỡng ⛔ bao giờ làm k6 thoát 99 — nó chỉ là một dòng số \
                        trong bảng tổng kết, mà đọc lên thì y hệt một cam kết đang được canh.
                        Chọn MỘT trong hai: thêm ngưỡng vào khối `thresholds`, hoặc khai vào \
                        MIEN_TRU của lớp này kèm lý do ⛔ dưới 40 ký tự.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Mỗi dòng miễn trừ phải trỏ vào một chỉ số CÓ THẬT, và mang lý do đủ dài")
    void mienTruPhaiConSong() {
        Set<String> daKhai = new TreeSet<>();
        for (Path tep : kichBan()) {
            daKhai.addAll(chiSoKhaiBao(doc(tep)));
        }

        Set<String> mocMeo = new TreeSet<>(MIEN_TRU.keySet());
        mocMeo.removeAll(daKhai);
        assertThat(mocMeo)
                .as("Một dòng miễn trừ trỏ vào chỉ số ⛔ còn tồn tại là một dòng mã đã MỤC — nó ⛔ che gì"
                        + " nữa mà vẫn đọc như đang che.")
                .isEmpty();

        Set<String> lyDoNgan = new TreeSet<>();
        MIEN_TRU.forEach((chiSo, lyDo) -> {
            if (lyDo.trim().length() < 40) {
                lyDoNgan.add("%s (%d ký tự)".formatted(chiSo, lyDo.trim().length()));
            }
        });
        assertThat(lyDoNgan)
                .as("Lý do dưới 40 ký tự là *chưa dựng* viết dài ra — nó ⛔ nói được vì sao chỗ này"
                        + " an toàn khi ⛔ ngưỡng.")
                .isEmpty();
    }

    @Test
    @DisplayName("Vế chống tập rỗng — bộ canh phải ĐỌC RA đủ kịch bản, chỉ số và ngưỡng")
    void phaiDocRaDuKichBan() {
        List<Path> tep = kichBan();
        assertThat(tep)
                .as("Đổi tên thư mục hay phần mở rộng là bộ canh này quét một tập RỖNG và xanh trọn"
                        + " vẹn — xanh vì ⛔ nhìn thấy gì (luật 7).")
                .hasSizeGreaterThanOrEqualTo(2);

        Set<String> chiSo = new TreeSet<>();
        for (Path t : tep) {
            String ma = doc(t);
            assertThat(khoaNguong(ma))
                    .as("%s ⛔ đọc ra được khối `thresholds` nào", t.getFileName())
                    .isNotEmpty();
            chiSo.addAll(chiSoKhaiBao(ma));
        }
        assertThat(chiSo).hasSizeGreaterThanOrEqualTo(5);
    }

    @Test
    @DisplayName("Tự kiểm bộ đọc — phân biệt được khoá ngưỡng thật với tên chỉ số nằm trong CHÚ THÍCH")
    void tuKiemBoDoc() {
        String gia =
                """
                const a = new Rate('co_nguong');
                const b = new Counter('khong_nguong');
                const c = new Trend('co_nguong_kem_tag', true);
                export const options = {
                  thresholds: {
                    // khong_nguong: một chú thích ⛔ phải một ngưỡng
                    'http_req_duration{trang:trang-chu}': ['p(95)<3000'],
                    co_nguong: ['rate==0'],
                    'co_nguong_kem_tag{x:y}': ['p(95)<1000'],
                  },
                  summaryTrendStats: ['avg'],
                };
                """;

        assertThat(chiSoKhaiBao(gia)).containsExactlyInAnyOrder("co_nguong", "khong_nguong", "co_nguong_kem_tag");

        assertThat(khoaNguong(gia))
                .as("Khoá có ngoặc nhọn BÊN TRONG dấu nháy (`http_req_duration{trang:trang-chu}`) là chỗ"
                        + " một bộ đọc cân ngoặc ngây thơ sẽ lệch — nó phải bỏ qua ngoặc nằm trong chuỗi.")
                .containsExactlyInAnyOrder("http_req_duration{trang:trang-chu}", "co_nguong", "co_nguong_kem_tag{x:y}");

        assertThat(khoaNguong(gia))
                .as("⛔ ĐƯỢC tính một tên nằm trong chú thích là một ngưỡng — đúng hình dạng T46.7 và"
                        + " T54.8, nơi một bộ canh phạt (hoặc tha) người viết tài liệu tử tế.")
                .noneMatch(k -> k.startsWith("khong_nguong"));

        Set<String> coNguong = tenChiSoCoNguong(khoaNguong(gia));
        assertThat(coNguong).contains("co_nguong", "co_nguong_kem_tag").doesNotContain("khong_nguong");
    }

    // ------------------------------------------------------------------ bộ đọc

    /** Tên chỉ số của một khoá ngưỡng — bỏ phần tag `{…}` để `x{a:b}` vẫn tính là canh `x`. */
    private static Set<String> tenChiSoCoNguong(Set<String> khoa) {
        Set<String> ten = new TreeSet<>();
        for (String k : khoa) {
            int i = k.indexOf('{');
            ten.add(i < 0 ? k : k.substring(0, i));
        }
        return ten;
    }

    private static Set<String> chiSoKhaiBao(String ma) {
        Set<String> ten = new TreeSet<>();
        Matcher m = KHAI_CHI_SO.matcher(boChuThich(ma));
        while (m.find()) {
            ten.add(m.group(1));
        }
        return ten;
    }

    /**
     * Khoá cấp cao nhất của khối {@code thresholds}.
     *
     * <p>⛔ dùng regex theo DÒNG: {@code EnumBaNoiTest} đã trả giá đúng chuyện ấy (§11.13 — một danh
     * sách xuống dòng làm bộ đọc cắt nhầm rồi báo một chẩn đoán SAI), và chính khối này có một khoá
     * mang ngoặc nhọn bên trong dấu nháy. Ở đây là một lượt quét <b>cân ngoặc</b>, bỏ qua chuỗi và
     * chú thích.
     */
    private static Set<String> khoaNguong(String ma) {
        int viTri = ma.indexOf("thresholds:");
        if (viTri < 0) {
            return Set.of();
        }
        int mo = ma.indexOf('{', viTri);
        if (mo < 0) {
            return Set.of();
        }

        Set<String> khoa = new TreeSet<>();
        StringBuilder dem = new StringBuilder();
        int sau = 0;
        boolean daLayKhoa = false;

        for (int i = mo; i < ma.length(); i++) {
            char c = ma.charAt(i);

            if (c == '/' && i + 1 < ma.length() && ma.charAt(i + 1) == '/') {
                while (i < ma.length() && ma.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < ma.length() && ma.charAt(j) != c) {
                    j += ma.charAt(j) == '\\' ? 2 : 1;
                }
                dem.append(ma, i + 1, Math.min(j, ma.length()));
                i = j;
                continue;
            }
            if (c == '{' || c == '[' || c == '(') {
                sau++;
                if (sau > 1) {
                    dem.append(c);
                }
                continue;
            }
            if (c == '}' || c == ']' || c == ')') {
                sau--;
                if (sau == 0) {
                    break;
                }
                dem.append(c);
                continue;
            }
            if (sau == 1 && c == ':' && !daLayKhoa) {
                String k = dem.toString().trim();
                if (!k.isEmpty()) {
                    khoa.add(k);
                }
                daLayKhoa = true;
                dem.setLength(0);
                continue;
            }
            if (sau == 1 && c == ',') {
                daLayKhoa = false;
                dem.setLength(0);
                continue;
            }
            dem.append(c);
        }
        return khoa;
    }

    /** Bỏ chú thích dòng nhưng GIỮ chuỗi ký tự — tiền lệ {@code boChuThich} của T49.6. */
    private static String boChuThich(String ma) {
        StringBuilder ra = new StringBuilder(ma.length());
        for (int i = 0; i < ma.length(); i++) {
            char c = ma.charAt(i);
            if (c == '/' && i + 1 < ma.length() && ma.charAt(i + 1) == '/') {
                while (i < ma.length() && ma.charAt(i) != '\n') {
                    i++;
                }
                ra.append('\n');
                continue;
            }
            if (c == '\'' || c == '"' || c == '`') {
                int j = i + 1;
                while (j < ma.length() && ma.charAt(j) != c) {
                    j += ma.charAt(j) == '\\' ? 2 : 1;
                }
                ra.append(ma, i, Math.min(j + 1, ma.length()));
                i = j;
                continue;
            }
            ra.append(c);
        }
        return ra.toString();
    }

    private static List<Path> kichBan() {
        Path thuMuc = timTuGocKho(THU_MUC);
        try (Stream<Path> s = Files.list(thuMuc)) {
            List<Path> ra =
                    new ArrayList<>(s.filter(p -> p.getFileName().toString().endsWith(".js"))
                            .sorted()
                            .toList());
            return ra;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String doc(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path ungVien = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(ungVien)) {
                return ungVien;
            }
            hienTai = hienTai.getParent();
        }
        throw new IllegalStateException("⛔ tìm thấy %s tính từ %s"
                .formatted(duongDanTuongDoi, Paths.get("").toAbsolutePath()));
    }
}
