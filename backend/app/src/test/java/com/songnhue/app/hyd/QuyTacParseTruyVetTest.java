package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Mười quy tắc parse của {@code function-spec.md} CN-03.2 ↔ bài kiểm — T37.13.
 *
 * <h2>Cái hở mà bài này lấp</h2>
 *
 * Ngày 07/09 khi đóng {@code DOD2.5} đã đếm tay được <b>10/10</b> quy tắc có bài kiểm. Vấn đề không
 * nằm ở con số mà ở chỗ <b>không có gì đỏ khi một quy tắc mất bài kiểm</b> — và ở chỗ quy tắc parse
 * số 1 lại mang tên <i>"quy tắc 18"</i> trong {@code @DisplayName}, theo số hiệu của {@code CLAUDE.md}
 * chứ không theo số hiệu của spec. Người sau muốn nghiệm thu phải <b>tự biết chúng là một</b>.
 *
 * <h2>⛔⛔ Ba hệ đánh số cùng dùng chữ "quy tắc N" — đo được, không suy đoán</h2>
 *
 * <ol>
 *   <li>{@code function-spec.md} CN-03.2 — quy tắc <b>parse</b> 1→10;
 *   <li>{@code CLAUDE.md} §"Quy tắc bất di bất dịch khi code" — 1→18;
 *   <li>{@code CLAUDE.md} §"Luật đã trả giá" — cũng có mục 18, nghĩa <b>khác hẳn</b>.
 * </ol>
 *
 * <p>Đo trong kho: {@code quy tắc 14} xuất hiện ở <b>6</b> {@code @DisplayName} và <i>không lần nào</i>
 * là quy tắc parse; {@code quy tắc 2}, {@code quy tắc 9}, {@code quy tắc 16} cũng vậy. Một mẫu
 * {@code quy tắc (\d+)} vì thế <b>không dùng được</b> — nó sẽ khớp hàng chục chỗ vô can và bỏ sót
 * đúng thứ cần tìm. Đây là lý do lượt này <b>đổi tên</b> 19 {@code @DisplayName} sang dạng duy nhất
 * {@code quy tắc parse N} thay vì cố viết một regex khôn hơn: <b>sửa nguồn nhập nhằng, đừng đoán nó</b>.
 *
 * <h2>⚠ Phạm vi — nói ra thay vì để người đọc suy (quy tắc 28)</h2>
 *
 * <ul>
 *   <li>Canh được: mỗi quy tắc có <b>ít nhất một</b> bài gọi đúng tên nó, và không bài nào gọi tên
 *       một số hiệu không tồn tại.
 *   <li><b>Không</b> canh được bài kiểm ấy có kiểm đúng quy tắc hay không — truy vết không thay được
 *       nội dung. Nó chỉ bảo đảm <i>chỗ trống thì đỏ</i>.
 *   <li>Chỉ quét {@code @DisplayName}. Một quy tắc được kiểm trong thân bài mà không nêu tên ở
 *       {@code @DisplayName} thì bài này coi như chưa có — cố ý: tên bài là thứ người nghiệm thu đọc.
 * </ul>
 */
class QuyTacParseTruyVetTest {

    /** Nguồn sự thật. ⚠ Đường dẫn này phải có trong bộ lọc `ci.yml`, xem {@code CiPathFilterTest}. */
    private static final String SPEC = ".claude/function-spec.md";

    /** Thư mục mã kiểm được quét — khai tường minh để phạm vi không âm thầm co lại. */
    private static final List<String> THU_MUC_TEST = List.of(
            "backend/app/src/test", "backend/hydro/src/test", "backend/content/src/test", "backend/core/src/test");

    /**
     * Neo của khối quy tắc.
     *
     * <p>⛔ Bắt buộc phải neo. Trong cùng mục CN-03.2 còn <b>hai</b> danh sách đánh số khác — bốn lưu
     * ý của CN-03.1 ngay phía trên, và khối rate-limit 1→4 ngay phía dưới — nên quét cả mục sẽ trộn
     * ba danh sách thành một.
     */
    private static final String NEO = "**Quy tắc parse bắt buộc**:";

    private static final Pattern DONG_QUY_TAC = Pattern.compile("^(\\d+)\\.\\s+(\\S.*)$");

    /** Số hiệu quy tắc parse trong một {@code @DisplayName}. */
    private static final Pattern TEN_BAI_GOI_QUY_TAC =
            Pattern.compile("quy tắc parse (\\d+)", Pattern.CASE_INSENSITIVE);

    /**
     * Trọn khối {@code @DisplayName( ... "chuỗi" )}, <b>kể cả khi Spotless đã ngắt xuống dòng</b>.
     *
     * <p>{@code (?s)} cho {@code .} khớp cả xuống dòng; phần chuỗi cho phép {@code \"} thoát bên trong.
     */
    private static final Pattern KHOI_DISPLAY_NAME =
            Pattern.compile("(?s)@DisplayName\\s*\\(\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    // =========================================================================

    @Test
    @DisplayName("⭐ Spec khai ĐÚNG 10 quy tắc parse — không 9, không 11")
    void specKhaiDungMuoiQuyTac() {
        Map<Integer, String> quyTac = docQuyTacTuSpec();

        // Khẳng định VỀ SỐ LƯỢNG, không về hình dạng — nó không dùng chung giả định nào với regex
        // ở trên, nên một mẫu hỏng không thể kéo theo bài này cùng xanh (quy tắc 29).
        assertThat(quyTac)
                .as("CN-03.2 khai 10 quy tắc parse; đổi số thì phải đổi cả bộ canh")
                .hasSize(10);
        assertThat(quyTac.keySet()).containsExactlyElementsOf(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
    }

    @Test
    @DisplayName("⭐ Bộ trích KHÔNG nuốt nhầm hai danh sách đánh số nằm sát bên")
    void botrichKhongLanSangDanhSachKhac() {
        Map<Integer, String> quyTac = docQuyTacTuSpec();

        // Đối chứng PHẢI-TÌM-THẤY: ba mốc chỉ có ở khối quy tắc parse.
        assertThat(quyTac.get(1)).contains("hydro_raw_logs");
        assertThat(quyTac.get(3)).contains("DOCTYPE");
        assertThat(quyTac.get(7)).contains("BigDecimal");

        // Đối chứng PHẢI-KHÔNG-TÌM-THẤY: mốc riêng của hai danh sách lân cận. Thiếu vế này thì một
        // bộ trích quét cả mục vẫn xanh ở bài trên, vì nó tình cờ vẫn có đủ 10 khoá.
        String tatCa = String.join("\n", quyTac.values());
        assertThat(tatCa)
                .as("4 lưu ý của CN-03.1 nằm ngay phía trên khối này")
                .doesNotContain("Yên Nghĩa")
                .as("khối rate-limit 1→4 nằm ngay phía dưới")
                .doesNotContain("floor(now / 10 phút)");
    }

    @Test
    @DisplayName("⭐⭐ Mỗi quy tắc parse có ít nhất một @DisplayName gọi đúng tên nó")
    void moiQuyTacDeuCoBaiKiem() {
        Map<Integer, String> quyTac = docQuyTacTuSpec();
        Map<Integer, List<String>> baiKiem = timBaiKiemTheoQuyTac();

        TreeSet<Integer> thieu = new TreeSet<>();
        for (Integer so : quyTac.keySet()) {
            if (baiKiem.getOrDefault(so, List.of()).isEmpty()) {
                thieu.add(so);
            }
        }

        assertThat(thieu)
                .as(
                        """
                        Quy tắc parse %s của CN-03.2 không có @DisplayName nào gọi tên.

                        Đặt tên bài theo dạng DUY NHẤT `quy tắc parse N` — `quy tắc N` trần khớp nhầm \
                        hai hệ đánh số khác của CLAUDE.md (đo được: `quy tắc 14` có 6 lượt, không lượt \
                        nào là quy tắc parse).

                        Quy tắc đang thiếu bài:
                        %s""",
                        thieu,
                        thieu.stream()
                                .map(so -> "  %d. %s".formatted(so, quyTac.get(so)))
                                .toList())
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Chiều ngược: không bài nào gọi một số hiệu quy tắc parse không tồn tại")
    void khongBaiNaoGoiSoHieuMa() {
        Map<Integer, String> quyTac = docQuyTacTuSpec();
        Map<Integer, List<String>> baiKiem = timBaiKiemTheoQuyTac();

        TreeSet<Integer> la = new TreeSet<>(baiKiem.keySet());
        la.removeAll(quyTac.keySet());

        assertThat(la)
                .as(
                        "Bài kiểm gọi tên quy tắc parse %s mà spec không có số hiệu ấy — thường là chép "
                                + "nhầm số hiệu CLAUDE.md (chính lỗi T37.13 sinh ra để chặn: quy tắc parse 1 "
                                + "từng mang tên 'quy tắc 18'). Nơi gọi: %s",
                        la, la.stream().map(baiKiem::get).toList())
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Bộ dò phải THẬT SỰ dò được — chống xanh trên tập rỗng và regex chết")
    void boDoTuKiemChung() {
        Map<Integer, List<String>> baiKiem = timBaiKiemTheoQuyTac();

        // Quy tắc 7 (quy tắc 32 của phiên 03/09): một bộ dò đã chết trông y hệt hệ thống hỏng toàn
        // phần. Con số đếm được là thứ duy nhất phân biệt hai trạng thái ấy.
        long tongBai = baiKiem.values().stream().mapToLong(List::size).sum();
        assertThat(tongBai)
                .as("đo 08/09/2026: 24 @DisplayName gọi tên quy tắc parse")
                .isGreaterThanOrEqualTo(20);

        // Đối chứng PHẢI-TÌM-THẤY và PHẢI-KHÔNG-TÌM-THẤY cho chính mẫu regex.
        assertThat(soHieuTrongTen("⭐ Quy tắc parse 7: 493 cm ⇒ 4.930 m")).containsExactly(7);
        assertThat(soHieuTrongTen("⭐ hopLe() là vế đọc của quy tắc 14"))
                .as("`quy tắc 14` là số hiệu CLAUDE.md — mẫu KHÔNG được nhận nhầm")
                .isEmpty();
        assertThat(soHieuTrongTen("quy tắc parse 3 + quy tắc parse 4: hai vế một bài"))
                .as("một bài phủ hai quy tắc phải đếm cho cả hai")
                .containsExactly(3, 4);

        // ⚠⚠ Đối chứng cho chính lỗi đã mắc: Spotless ngắt @DisplayName xuống dòng khi chuỗi dài.
        //    Bản đầu của bộ dò quét theo DÒNG và đánh rơi quy tắc parse 1 vì đúng chuyện này —
        //    một bộ canh mà BỘ ĐỊNH DẠNG MÃ làm cho sai sẽ đỏ giả vào một ngày không ai đoán trước.
        String ngatDong =
                """
                    @DisplayName(
                            "Nguồn từ chối ⇒ raw log VẪN ghi (quy tắc parse 1) rồi mới tới sync_logs")
                """;
        Matcher m = KHOI_DISPLAY_NAME.matcher(ngatDong);
        assertThat(m.find())
                .as("mẫu phải bắt được @DisplayName đã bị ngắt dòng")
                .isTrue();
        assertThat(soHieuTrongTen(m.group(1))).containsExactly(1);
    }

    // =========================================================================

    /** Mười quy tắc, theo số hiệu → nguyên văn. */
    private static Map<Integer, String> docQuyTacTuSpec() {
        List<String> dong = docDong(SPEC);
        int neo = -1;
        for (int i = 0; i < dong.size(); i++) {
            if (dong.get(i).strip().equals(NEO)) {
                neo = i;
                break;
            }
        }
        if (neo < 0) {
            return fail("Không thấy neo '%s' trong %s — spec đã đổi cách viết, bộ canh phải đổi theo", NEO, SPEC);
        }

        Map<Integer, String> quyTac = new LinkedHashMap<>();
        for (int i = neo + 1; i < dong.size(); i++) {
            String d = dong.get(i);
            if (d.isBlank()) {
                break; // hết khối — dòng trống là biên dưới
            }
            Matcher m = DONG_QUY_TAC.matcher(d);
            if (m.matches()) {
                quyTac.put(Integer.parseInt(m.group(1)), m.group(2));
            }
        }
        return quyTac;
    }

    /**
     * Số hiệu → {@code tệp:dòng} của mọi {@code @DisplayName} gọi tên quy tắc parse.
     *
     * <p>⚠⚠ <b>Quét theo KHỐI CHÚ GIẢI, không quét theo DÒNG</b> — và đây là bài học đo được ngay ở
     * lượt chạy đầu tiên của chính bộ canh này (08/09/2026). Bản đầu duyệt từng dòng và đòi
     * {@code @DisplayName} nằm <i>cùng dòng</i> với tên quy tắc. Lượt đổi tên làm chuỗi của
     * {@code TelemetryIngestServiceTest} dài thêm, <b>Spotless ngắt nó xuống dòng dưới</b>, và quy
     * tắc parse 1 lập tức "biến mất" — bộ canh đỏ với thông báo <i>"quy tắc 1 không có bài kiểm"</i>
     * trong khi bài vẫn nằm nguyên đó.
     *
     * <p>Một bộ canh mà <b>bộ định dạng mã</b> làm cho sai là một bộ canh sẽ đỏ giả vào một ngày
     * không ai đoán trước, và lượt sửa nó rất dễ thành "nới bộ canh cho hết đỏ". Nên mẫu ở đây bắt
     * trọn {@code @DisplayName( ... "chuỗi" )} bất kể xuống dòng ở đâu.
     */
    private static Map<Integer, List<String>> timBaiKiemTheoQuyTac() {
        Map<Integer, List<String>> ket = new LinkedHashMap<>();
        for (String thuMuc : THU_MUC_TEST) {
            Path goc = timTuGocKho(thuMuc);
            try (Stream<Path> tep = Files.walk(goc)) {
                for (Path p : tep.filter(x -> x.toString().endsWith(".java")).toList()) {
                    String nguon = Files.readString(p);
                    Matcher chuThich = KHOI_DISPLAY_NAME.matcher(nguon);
                    while (chuThich.find()) {
                        List<Integer> so = soHieuTrongTen(chuThich.group(1));
                        if (so.isEmpty()) {
                            continue;
                        }
                        int dong = (int) nguon.substring(0, chuThich.start())
                                        .chars()
                                        .filter(c -> c == '\n')
                                        .count()
                                + 1;
                        for (Integer n : so) {
                            ket.computeIfAbsent(n, k -> new ArrayList<>())
                                    .add("%s:%d".formatted(p.getFileName(), dong));
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return ket;
    }

    private static List<Integer> soHieuTrongTen(String tenBai) {
        List<Integer> so = new ArrayList<>();
        Matcher m = TEN_BAI_GOI_QUY_TAC.matcher(tenBai);
        while (m.find()) {
            so.add(Integer.parseInt(m.group(1)));
        }
        return so;
    }

    private static List<String> docDong(String duongDanTuongDoi) {
        try {
            return Files.readAllLines(timTuGocKho(duongDanTuongDoi));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Khuôn lấy từ {@code PortalSettingsReadTest.timTuGocKho()} — leo tối đa 6 cấp cha.
     *
     * <p>⚠ Surefire chạy với CWD = {@code backend/app}, nên {@code Paths.get("...")} tương đối sẽ
     * phân giải thành {@code backend/app/...} và cho ra <b>0 tệp</b> — bộ canh xanh vì không thấy gì.
     */
    private static Path timTuGocKho(String duongDanTuongDoi) {
        Path hienTai = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 6 && hienTai != null; i++) {
            Path thu = hienTai.resolve(duongDanTuongDoi);
            if (Files.exists(thu)) {
                return thu;
            }
            hienTai = hienTai.getParent();
        }
        return fail("Không tìm thấy %s tính từ %s", duongDanTuongDoi, System.getProperty("user.dir"));
    }
}
