package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ⭐⭐ Quy tắc 8 — <b>báo cáo đọc bảng TỔNG HỢP, ⛔ không scan bảng số đo thô</b>. Bộ canh T34.2.
 *
 * <h2>Vì sao một dòng javadoc ⛔ không đủ</h2>
 *
 * <p>{@code HydroReportRepository} mở đầu bằng đúng câu ấy. Nhưng một câu chữ chỉ ngăn được người
 * <i>đọc</i> nó, và người viết truy vấn báo cáo thứ tám sẽ ⛔ không mở javadoc lớp ra đọc — họ chép
 * câu SQL gần nhất rồi sửa. Con số đằng sau: một điểm đo × một chỉ số sinh <b>144 bản ghi mỗi
 * ngày</b>; báo cáo tháng của 19 điểm đo là ~82 nghìn dòng cho một bảng 30 hàng, và NFR-04 đòi dưới
 * 60 giây.
 *
 * <p>⚠ Và cái hỏng thì <b>không có triệu chứng</b> ở quy mô hôm nay: hệ thống mới có vài nghìn bản
 * ghi, nên một truy vấn quét bảng gốc vẫn trả lời trong mili-giây. Nó chỉ chậm dần suốt năm năm hạn
 * lưu, và tới lúc đủ chậm để ai đó chú ý thì ⛔ không còn ai nhớ câu SQL nào là câu sai. Đây đúng là
 * loại bảo đảm phải được canh <b>trước khi</b> nó bị vi phạm.
 *
 * <h2>⚠ Phạm vi tự khai (luật 28)</h2>
 *
 * <ul>
 *   <li>Soi <b>một</b> tệp: {@code HydroReportRepository}. Đó là nơi truy vấn báo cáo sống, và là
 *       nơi câu tiếp theo sẽ được viết. ⬜ Dashboard (WS-35) sẽ có kho riêng — <b>thêm nó vào
 *       {@link #TEP_BAO_CAO} cùng lúc</b> với tệp ấy, ⛔ không để sau.
 *   <li>Chỉ thấy SQL nằm trong <b>một hằng chuỗi</b>. Một câu ghép từ nhiều mảnh thì bài này mù —
 *       cùng khoảng mù đã khai ở {@code QualityFilterGuardTest}, và cùng kết luận: ⛔ đừng gói SQL
 *       theo kiểu ấy trên các bảng đang được canh.
 * </ul>
 */
class ReportReadsAggregateTest {

    /** ⬜ Kho truy vấn của dashboard (WS-35) thêm vào đây <b>cùng lúc</b> với tệp ấy ra đời. */
    private static final List<String> TEP_BAO_CAO = List.of("hydro/HydroReportRepository.java");

    /** Bảng số đo thô — báo cáo ⛔ không được đọc, trừ ngoại lệ có tên. */
    private static final String BANG_THO = "hydro_readings";

    /**
     * ⭐ Ngoại lệ <b>phải nêu tên và nêu lý do</b> — ⛔ không có mục "còn lại".
     *
     * <p>Hôm nay đúng <b>hai</b> câu, và cả hai thuộc về BC-12 — báo cáo tồn tại ĐỂ hiện từng bản
     * ghi. Thêm một mục ở đây là một quyết định phải đi qua review, và đó chính là điều bài này muốn.
     */
    private static final Map<String, String> NGOAI_LE = Map.of(
            "SQL_CHI_TIET",
                    "⭐⭐ BC-12 — báo cáo chi tiết theo yêu cầu. Nó PHẢI đọc bảng gốc: một bảng tổng hợp "
                            + "theo ngày ⛔ không trả lời được 'lúc 14 giờ 20 hôm ấy máy đọc được bao nhiêu' — "
                            + "câu hỏi mà người ta mở báo cáo chi tiết ra để hỏi. Giữ an toàn bằng khoảng ngày "
                            + "tối đa 31 ngày (HYD-2012) + phân trang.",
            "SQL_DEM_CHI_TIET",
                    "⭐ Phép đếm của BC-12 — phải soi CHÍNH XÁC tập mà SQL_CHI_TIET liệt ra, nên nó đi cùng "
                            + "một bảng. Đọc bảng khác là tổng số trang nói một đằng, nội dung trang nói một nẻo.");

    /**
     * Số hằng SQL soi được — ⛔ <b>chỉ được tăng</b>.
     *
     * <p>Vế chống <i>xanh trên tập rỗng</i> (luật 7): bộ tách hỏng, tệp đổi tên, hay ai đó gói SQL
     * theo kiểu bài này ⛔ không đọc được — cả ba đều làm khẳng định phía dưới chạy qua một tập rỗng
     * và xanh trọn vẹn. ⚠ Đếm <b>cả ngoại lệ</b>: thứ cần chứng minh là bộ tách còn nhìn thấy mã.
     */
    private static final int SO_HANG_TOI_THIEU = 7;

    private static final Pattern HANG_SQL =
            Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"\"\"(.*?)\"\"\";", Pattern.DOTALL);

    /** {@code FROM|JOIN <bảng>} — ⛔ {@code INSERT INTO … VALUES} ⛔ không phải truy vấn đọc. */
    private static final Pattern DOC_BANG = Pattern.compile("(?i)\\b(?:from|join)\\s+(\\w+)\\b");

    private record HangSql(String ten, String sql) {}

    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Truy vấn báo cáo đọc bảng TỔNG HỢP — đọc hydro_readings phải khai ngoại lệ có tên")
    void reportQueriesReadTheAggregateTable() {
        List<String> viPham = hangSql().stream()
                .filter(h -> chamBangTho(h.sql()))
                .filter(h -> !NGOAI_LE.containsKey(h.ten()))
                .map(HangSql::ten)
                .toList();

        assertThat(viPham)
                .as(
                        """
                        ⛔ Truy vấn báo cáo đọc thẳng `hydro_readings` — quy tắc 8. Một điểm đo × một chỉ \
                        số sinh 144 bản ghi MỖI NGÀY; báo cáo tháng của 19 điểm đo là ~82 nghìn dòng cho \
                        một bảng 30 hàng, và NFR-04 đòi dưới 60 giây.

                        ⚠ Cái hỏng KHÔNG có triệu chứng hôm nay — hệ mới có vài nghìn bản ghi nên câu SQL \
                        ấy vẫn trả lời trong mili-giây. Nó chậm dần suốt 5 năm hạn lưu, và tới lúc đủ chậm \
                        để ai đó chú ý thì không còn ai nhớ câu nào là câu sai.

                        Sửa: đọc `hydro_agg_daily`. Nếu câu này THẬT SỰ cần từng bản ghi (chỉ BC-12) thì \
                        khai vào `NGOAI_LE` KÈM LÝ DO và kèm cận khoảng ngày.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Chống xanh trên tập rỗng — số hằng SQL soi được chỉ được tăng (luật 7 · luật 29)")
    void theScannerStillSeesTheCode() {
        List<HangSql> soi = hangSql();

        assertThat(soi)
                .as(
                        "bộ tách phải còn nhìn thấy mã: %d hằng, tên %s",
                        soi.size(), soi.stream().map(HangSql::ten).toList())
                .hasSizeGreaterThanOrEqualTo(SO_HANG_TOI_THIEU);
    }

    @Test
    @DisplayName("⛔ Ngoại lệ mồ côi — một luật đã lỏng mà ⛔ không ai biết")
    void noOrphanExceptions() {
        Set<String> conSong = hangSql().stream()
                .map(HangSql::ten)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(conSong)
                .as("⛔ `NGOAI_LE` khai một hằng ⛔ không còn tồn tại. Ngoại lệ mồ côi nguy hiểm hơn ngoại "
                        + "lệ thừa: nó sẽ khớp trở lại với một câu SQL TƯƠNG LAI mang cùng tên hằng, và câu "
                        + "ấy được miễn kiểm mà ⛔ không ai quyết định điều đó.")
                .containsAll(NGOAI_LE.keySet());
    }

    /**
     * ⭐⭐ Vế kiểm chứng ngược — {@code conventions.md} §1.5.
     *
     * <p>⚠ Luật 29: bài kiểm chứng ngược có thể sai theo đúng cách mà thứ nó kiểm đang sai. Nên các
     * bài dưới đây ép bộ nhận diện phân biệt được <b>hai trạng thái khác nhau</b> (luật 9), ⛔ không
     * chỉ hỏi "có đỏ không".
     */
    @Nested
    @DisplayName("Bộ canh tự kiểm chứng")
    class BoCanhTuKiem {

        @Test
        @DisplayName("⭐ Câu đọc bảng thô BỊ BẮT, câu đọc bảng tổng hợp thì KHÔNG")
        void itTellsTheTwoTablesApart() {
            assertThat(chamBangTho("SELECT reading_value FROM hydro_readings WHERE station_id = ?"))
                    .isTrue();
            assertThat(chamBangTho("SELECT min_value FROM hydro_agg_daily WHERE station_id = ?"))
                    .as("⛔ Bắt nhầm bảng tổng hợp thì bộ canh sẽ bị nới ra cho hết đỏ, và sau lượt nới "
                            + "ấy nó ⛔ không còn canh gì")
                    .isFalse();
        }

        @Test
        @DisplayName("⛔ Bảng có tên GẦN GIỐNG ⛔ không bị bắt nhầm")
        void similarTableNamesAreNotConfused() {
            assertThat(chamBangTho("SELECT * FROM hydro_unmapped_readings")).isFalse();
            assertThat(chamBangTho("SELECT * FROM hydro_readings_default"))
                    .as("partition con là một bảng KHÁC — ⛔ không tự nhận là bảng cha")
                    .isFalse();
        }

        @Test
        @DisplayName("⛔ JOIN cũng là đọc — ⛔ không chỉ FROM")
        void joinCountsAsReading() {
            assertThat(chamBangTho("SELECT a.* FROM hydro_agg_daily a JOIN hydro_readings r ON r.id = a.id"))
                    .as("⛔ Một câu chính đọc bảng agg mà JOIN sang bảng thô vẫn là một lượt quét bảng thô")
                    .isTrue();
        }

        @Test
        @DisplayName("⛔ INSERT … VALUES ⛔ không phải truy vấn đọc")
        void writesAreNotReads() {
            assertThat(chamBangTho("INSERT INTO hydro_readings (measured_at) VALUES (?)"))
                    .isFalse();
        }

        @Test
        @DisplayName("⭐ Bộ tách đọc được hằng text block — và ⛔ bỏ qua chuỗi thường")
        void theScannerReadsTextBlocks() {
            String lop =
                    """
                    class X {
                        private static final String CO = \"\"\"
                            SELECT 1 FROM hydro_readings
                            \"\"\";
                        private static final String KHONG = "chỉ là một chuỗi";
                    }
                    """;
            List<HangSql> ra = hangTrong(lop);

            assertThat(ra).hasSize(1);
            assertThat(ra.get(0).ten()).isEqualTo("CO");
            assertThat(chamBangTho(ra.get(0).sql())).isTrue();
        }
    }

    // =========================================================================

    private static boolean chamBangTho(String sql) {
        Matcher m = DOC_BANG.matcher(sql);
        while (m.find()) {
            if (BANG_THO.equalsIgnoreCase(m.group(1))) {
                return true;
            }
        }
        return false;
    }

    private static List<HangSql> hangSql() {
        List<HangSql> ket = new ArrayList<>();
        for (String tuongDoi : TEP_BAO_CAO) {
            Path tep = thuMucBackend()
                    .resolve(tuongDoi.replace("hydro/", "hydro/src/main/java/com/songnhue/hydro/infra/"));
            ket.addAll(hangTrong(docTep(tep)));
        }
        return ket;
    }

    static List<HangSql> hangTrong(String ma) {
        List<HangSql> ket = new ArrayList<>();
        Matcher m = HANG_SQL.matcher(ma);
        while (m.find()) {
            ket.add(new HangSql(m.group(1), m.group(2)));
        }
        return ket;
    }

    private static String docTep(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Bài chạy với cwd = {@code backend/app}; đi ngược cho tới khi thấy {@code backend/pom.xml}. */
    private static Path thuMucBackend() {
        Path p = Paths.get("").toAbsolutePath();
        Set<Path> daXet = new LinkedHashSet<>();
        while (p != null && daXet.add(p)) {
            Path ungVien =
                    p.getFileName() != null && p.getFileName().toString().equals("backend") ? p : p.resolve("backend");
            if (Files.exists(ungVien.resolve("pom.xml")) && Files.isDirectory(ungVien.resolve("hydro"))) {
                return ungVien;
            }
            p = p.getParent();
        }
        throw new IllegalStateException(
                "Không tìm thấy thư mục backend từ " + Paths.get("").toAbsolutePath());
    }
}
