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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ⭐⭐ <b>Bẫy sai số liệu dễ mắc nhất của dự án</b> — quy tắc 14, bộ canh T32.4.
 *
 * <h2>Vì sao một dòng chữ trong tài liệu không đủ</h2>
 *
 * <p>Bản ghi {@code quality = 'NGHI_NGO'} và {@code 'XOA'} <b>nằm chung bảng chính</b>
 * {@code hydro_readings} — quyết định đúng (dữ liệu nghi ngờ vẫn là dữ liệu, và nguồn không có API
 * lịch sử nên vứt đi là vứt vĩnh viễn), nhưng nó đẩy một nghĩa vụ sang <i>mọi người viết truy vấn
 * sau này</i>: tự nhớ lọc. Quên một chỗ thì con số sai vẫn ra đúng định dạng, vẫn vẽ được biểu đồ
 * đẹp, và <b>không có lỗi nào</b> — không ngoại lệ, không dòng log, không cột đỏ.
 *
 * <p>Chỗ này đã có ba lớp bảo vệ và cả ba đều <i>không đủ một mình</i>: câu chữ trong CLAUDE.md
 * (người đọc quên), chú thích trong migration (người viết truy vấn không mở migration ra đọc), và
 * {@code hydro_latest} tách sẵn {@code valid_value} (chỉ đỡ cho widget/GIS, ⛔ không đỡ cho báo cáo
 * và biểu đồ nhiều ngày). Bài này là lớp thứ tư: nó đọc <b>mã thật</b>.
 *
 * <h2>⚠⚠ Ba cái bẫy mà chính bài này phải tránh — đã trả giá cho cả ba</h2>
 *
 * <ol>
 *   <li><b>Chú thích SQL.</b> §10.62: một lượt kiểm chứng ngược đặt {@code --} trước câu
 *       {@code DELETE} rồi chờ bộ canh đỏ — nó <b>không đỏ</b>, vì regex không biết SQL có chú
 *       thích. Ở đây câu SQL được <b>bóc chú thích trước khi khớp</b> ({@link #boChuThichSql}), và
 *       {@link BoCanhTuKiem} chứng minh việc ấy có tác dụng bằng chính hai chiều.
 *   <li><b>Xanh trên tập rỗng</b> (luật 7). Hôm nay {@code hydro_readings} có rất ít truy vấn đọc;
 *       một bộ canh chạy qua 0 câu vẫn xanh trọn vẹn và trông y hệt một bộ canh đang làm việc. ⇒
 *       {@link #soCauSoiDuocKhongDuocTut} khẳng định <b>số lượng</b> — một khẳng định không chia sẻ
 *       giả định nào với mẫu regex (luật 29, thứ đã cứu lượt 28/8).
 *   <li><b>Prose trông như SQL.</b> Chuỗi {@code hydro_readings} xuất hiện <b>hơn 20 lần</b> trong
 *       javadoc của module {@code hydro}. Một bộ canh khớp thô sẽ đỏ vì một câu văn — rồi bị nới ra
 *       cho hết đỏ, và sau lượt nới ấy nó không còn canh gì. ⇒ chú thích Java bị bóc trước, và chỉ
 *       <b>chuỗi ký tự</b> mới thành ứng viên.
 * </ol>
 *
 * <h2>⚠ Phạm vi tự khai (luật 28) — đọc trước khi tin cái xanh của bài này</h2>
 *
 * <ul>
 *   <li>Soi <b>mã main</b> của backend: {@code *.java} và {@code *.sql} migration. ⛔ Không soi mã
 *       kiểm thử — bài kiểm cố ý truy vấn không lọc để khẳng định dòng nghi ngờ <i>có</i> nằm trong
 *       bảng.
 *   <li>Soi câu có {@code FROM/JOIN/UPDATE hydro_readings}. ⛔ <b>Không thấy SQL dựng động</b> mà
 *       tên bảng đến từ tham số — {@code HydroMaintenanceRepository} thao tác partition theo tên
 *       bảng truyền vào, và bài này mù trước nó. Đó là DDL partition (DROP/ATTACH), ⛔ không phải
 *       truy vấn đọc, nên khoảng mù ấy <b>hôm nay</b> không che giấu rủi ro nào — nhưng ngày nào có
 *       ai dựng một câu <i>đọc</i> theo cùng kiểu thì bài này im lặng.
 *   <li>Soi <b>hai</b> bảng: {@code hydro_readings} và {@code hydro_agg_daily} (thêm ở WS-34/T34.1,
 *       <b>cùng commit</b> với migration tạo bảng). ⛔ Bảng tổng hợp cố ý giữ {@code quality} trong
 *       khoá để vị từ ở đây dùng được nguyên xi. ⚠ {@code hydro_latest} ⛔ <b>không</b> nằm trong
 *       danh sách và ⛔ không cần: nó tách sẵn {@code valid_value} khỏi {@code last_seen_at}, tức
 *       bảo đảm đã nằm ở lược đồ chứ không ở truy vấn.
 *   <li>⚠⚠ <b>Một câu ghép từ nhiều hằng chỉ được nhìn thấy từng mảnh.</b>
 *       {@code SuspectReadingRepository} tách {@code CHON_COT} · {@code TU_BANG} · {@code dieuKien()}
 *       — bài này thấy {@code TU_BANG} (có {@code FROM}, không có vị từ) và ⛔ không bao giờ thấy câu
 *       hoàn chỉnh. Đó là lý do mục ấy nằm trong {@link #NGOAI_LE}, ⛔ không phải vì nó thật sự
 *       thiếu bộ lọc. ⇒ Cách gói SQL này <b>làm mù bộ canh</b>: ai viết truy vấn mới trên
 *       {@code hydro_readings} thì để nguyên câu trong <b>một</b> hằng.
 * </ul>
 */
class QualityFilterGuardTest {

    /**
     * Bảng mà mọi truy vấn đọc phải khai chất lượng.
     *
     * <p>✅ {@code hydro_agg_daily} (WS-34/T34.1) đã vào đây <b>cùng commit</b> với migration tạo
     * nó — ⛔ không để sau, vì lúc ấy đã có sẵn vài truy vấn không lọc và người ta sẽ khai chúng
     * thành ngoại lệ. Bảng ấy giữ {@code quality} <b>trong khoá</b> chính là để vị từ mà bài này
     * canh dùng được nguyên xi, ⛔ không phải nới một chữ nào.
     */
    private static final Set<String> BANG_CANH = Set.of("hydro_readings", "hydro_agg_daily");

    /**
     * Số thứ tự câu {@code INSERT INTO hydro_agg_dirty … SELECT … FROM hydro_readings} trong
     * {@code V202609031056__hyd_agg_daily.sql}, theo cách đánh số của {@link #cauTrongSql}.
     *
     * <p>⚠ Con số này <b>mong manh theo cách viết migration</b> — thêm một câu lệnh phía trên là nó
     * lệch. Điều đó chấp nhận được vì migration là tệp <b>bất biến</b> (Flyway băm cả tệp,
     * {@code MigrationImmutabilityTest} canh), nên nó ⛔ không thể lệch sau khi đã vào kho. Và nếu
     * có ai sửa thì {@link #ngoaiLeKhongDuocMoCoi} đỏ ngay — ⛔ không im lặng.
     */
    private static final int CAU_NAP_CO_BAN = 26;

    /**
     * ⭐ Ngoại lệ <b>phải nêu tên và nêu lý do</b> — ⛔ không có mục "còn lại".
     *
     * <p>Khoá là tên hằng SQL trong Java hoặc {@code <tệp>#<số thứ tự câu>} trong migration. Một
     * khoảng chênh <b>có tên</b> là một quyết định; một khoảng chênh im lặng là một chỗ quên.
     *
     * <p>⚠ Mỗi mục ở đây tự nó là một khoản nợ nhỏ: nó nói rằng có một câu SQL đọc
     * {@code hydro_readings} mà không lọc chất lượng. Thêm một mục là một quyết định phải đi qua
     * review — đó chính là điều bài này muốn.
     */
    private static final Map<String, String> NGOAI_LE = Map.of(
            "HydroLatestRecomputer#SQL_MOC_GAN_NHAT",
            "⭐ Câu trả lời 'trạm còn phát tín hiệu không' — cố ý nhận CẢ bản ghi NGHI_NGO "
                    + "(`quality <> 'XOA'`). Một trạm chỉ trả số nghi ngờ VẪN đang phát; lọc HOP_LE ở "
                    + "đây là tự dựng ra một trạm mất tín hiệu giả, rồi job mất tín hiệu báo động về "
                    + "một sự cố không có thật.",
            "SuspectReadingRepository#TU_BANG",
            "⭐ Màn hình 'Dữ liệu nghi ngờ' — nó tồn tại ĐỂ đọc những dòng mà quy tắc 14 loại ra. "
                    + "Lọc HOP_LE ở đây là làm hàng chờ duyệt LUÔN RỖNG, và một hàng chờ luôn rỗng "
                    + "trông y hệt một hệ thống không có dữ liệu xấu. Vế `quality = ?` nằm ở "
                    + "`dieuKien()` và bị chặn hẹp lại bởi `chanHopLe()`.",
            "HydroTimeSeriesWriter#SQL_O_DA_CO_GI",
            "⭐ Không trả về số liệu — nó hỏi 'ô (điểm đo × chỉ số × mốc) này có ai ngồi chưa, và "
                    + "người ấy đang ở trạng thái nào'. PHẢI thấy cả NGHI_NGO lẫn XOA: lọc HOP_LE ở "
                    + "đây báo 'trống' cho một ô đang bị chiếm, rồi lượt INSERT nhập tay nổ bằng một "
                    + "lỗi ràng buộc thô thay vì HYD-2002 chỉ đường sang màn hình Dữ liệu nghi ngờ.",
            "HydroAggRepository#SQL_DUNG_LAI",
            "⭐⭐ Chính bộ dựng bảng tổng hợp — T34.1. Bảng đích giữ `quality` TRONG KHOÁ, nên câu "
                    + "này phải thấy cả ba nhóm để sinh ra ba hàng. Lọc HOP_LE ở đây làm BC-13 mù trước "
                    + "đúng những ngày tồi tệ nhất, tức mù đúng lúc nó cần nhìn. Bộ lọc nghiệp vụ nằm ở "
                    + "NƠI ĐỌC bảng agg, ⛔ không ở nơi dựng nó.",
            "HydroAggRepository#SQL_CAM_LAI_CO_GAN_DAY",
            "⭐ Lưới an toàn hằng ngày: cắm lại cờ bẩn cho hai ngày gần nhất. Nó ⛔ không đọc một "
                    + "giá trị đo nào — chỉ liệt kê những kỳ CÓ số đo. Một ngày chỉ toàn bản ghi nghi "
                    + "ngờ vẫn là một kỳ phải tổng hợp; lọc HOP_LE ở đây bỏ quên đúng nhóm ấy.",
            "HydroAggRepository#SQL_XOA_KY",
            "⭐ Xoá TRỌN kỳ (cả ba mức chất lượng) trước khi dựng lại — T34.1. Lọc `quality` ở đây "
                    + "để lại đúng những hàng cần biến mất: một bản ghi được duyệt NGHI_NGO → HOP_LE "
                    + "sẽ để lại hàng agg NGHI_NGO cũ, và BC-13 báo có dữ liệu nghi ngờ VĨNH VIỄN dù "
                    + "không còn cái nào. Đó là luật 27 ở tầng bảng tổng hợp.",
            "HydroReportRepository#SQL_CHAT_LUONG_NGAY",
            "⭐⭐ BC-13 — báo cáo tồn tại ĐỂ ĐẾM dữ liệu xấu, nên nó đọc cả ba nhóm chất lượng. "
                    + "⚠ Câu này CÓ `FILTER (WHERE quality = 'HOP_LE')`, và bản TRƯỚC của bộ canh đã "
                    + "cho nó đi lọt vì chuỗi ấy có mặt — lỗ hổng được bịt ở chính đợt WS-34 "
                    + "(`boFilterGop`), và câu đầu tiên nó bắt được là câu này. FILTER lọc cho MỘT "
                    + "hàm gộp, ⛔ không lọc cho câu.",
            "V202609031056__hyd_agg_daily.sql#" + CAU_NAP_CO_BAN,
            "⭐ §9 của migration — nạp cờ bẩn cho số đo ĐÃ CÓ để lượt tổng hợp đầu tiên tính được "
                    + "cả lịch sử. ⛔ Không tạo ra một con số nào; cùng lý do với SQL_CAM_LAI_CO_GAN_DAY.");

    /** Câu SQL có ít nhất một trong các cụm này thì mới là truy vấn <b>đọc</b> bảng đang canh. */
    private static final Pattern DOC_BANG = Pattern.compile("(?i)\\b(?:from|join|update)\\s+(\\w+)\\b");

    /**
     * ⭐⭐ Vị từ <b>đúng</b>: {@code quality = 'HOP_LE'} — ⛔ không phải "có nhắc tới quality".
     *
     * <p>Bản đầu của bài này nhận mọi vị từ ({@code =}, {@code <>}, {@code IN}). Nó <b>quá lỏng</b>:
     * {@code quality <> 'XOA'} khi ấy đi lọt, mà đó chính là câu <i>gần đúng</i> nguy hiểm nhất —
     * nó loại bản ghi đã xoá và <b>giữ nguyên bản ghi nghi ngờ</b>, tức đúng thứ quy tắc 14 sinh ra
     * để chặn, trong một câu trông đã cẩn thận.
     *
     * <p>⇒ Mặc định là {@code = 'HOP_LE'}; mọi biến thể khác phải khai <b>có tên</b> ở
     * {@link #NGOAI_LE}. Hai câu hợp lệ hôm nay đều đã khai, và mỗi mục ấy phải đi qua review.
     *
     * <p>⚠ {@code \\b} sau {@code quality} là chỗ chịu lực: cột {@code quality_reason} <b>không</b>
     * khớp (dấu {@code _} là ký tự từ), nên một câu chỉ {@code SELECT quality_reason} ⛔ không được
     * tính là đã lọc.
     *
     * <p>⚠ Nhóm {@code (?i: … )} chỉ bọc phần <b>từ khoá</b>: tên cột viết hoa hay thường đều được,
     * còn chuỗi {@code 'HOP_LE'} thì <b>phân biệt hoa thường</b> — vì PostgreSQL cũng vậy, và
     * {@code quality = 'hop_le'} ⛔ không khớp dòng nào. Một bộ canh nhận nó là một bộ canh đóng dấu
     * cho một truy vấn luôn trả rỗng.
     */
    private static final Pattern VI_TU_CHAT_LUONG = Pattern.compile("(?i:\\bquality\\b\\s*=\\s*)'HOP_LE'");

    /** Từ khoá đủ để coi một chuỗi là câu SQL chứ không phải một danh sách tên bảng. */
    private static final Pattern CO_VE_LA_SQL = Pattern.compile("(?i)\\b(?:select|insert|update|delete|from|join)\\b");

    /**
     * Số câu SQL đọc bảng canh mà bài này soi được — ⛔ <b>chỉ được tăng</b>.
     *
     * <p>Con số này là vế chống <i>xanh trên tập rỗng</i>: bộ tách chuỗi hỏng, thư mục đổi tên, hay
     * ai đó gói SQL theo kiểu bài này không đọc được — cả ba đều làm mọi khẳng định phía dưới chạy
     * qua một tập rỗng và xanh trọn vẹn. ⚠ Nó đếm <b>cả ngoại lệ</b>: thứ cần chứng minh là bộ tách
     * còn nhìn thấy mã, không phải là còn bao nhiêu câu tuân thủ.
     */
    private static final int SO_CAU_TOI_THIEU = 10;

    // =========================================================================
    // Bộ tách — bóc chú thích TRƯỚC khi khớp (§10.62)
    // =========================================================================

    /** Một câu SQL tìm được, kèm chỗ nó nằm. */
    private record CauSql(String nguon, String sql) {}

    /**
     * Bóc chú thích Java: {@code /* … *}{@code /} và {@code // …}.
     *
     * <p>⚠ Phải tôn trọng chuỗi ký tự, nếu không một câu SQL chứa {@code '--'} hay {@code "//"} sẽ
     * bị cắt cụt và câu bị cắt ấy <b>vẫn khớp</b> nửa đầu — xanh, và sai.
     */
    static String boChuThichJava(String ma) {
        StringBuilder ra = new StringBuilder(ma.length());
        int i = 0;
        while (i < ma.length()) {
            char c = ma.charAt(i);
            if (c == '/' && i + 1 < ma.length() && ma.charAt(i + 1) == '*') {
                int het = ma.indexOf("*/", i + 2);
                i = het < 0 ? ma.length() : het + 2;
            } else if (c == '/' && i + 1 < ma.length() && ma.charAt(i + 1) == '/') {
                int het = ma.indexOf('\n', i);
                i = het < 0 ? ma.length() : het;
            } else if (ma.startsWith("\"\"\"", i)) {
                int het = ma.indexOf("\"\"\"", i + 3);
                int cuoi = het < 0 ? ma.length() : het + 3;
                ra.append(ma, i, cuoi);
                i = cuoi;
            } else if (c == '"') {
                int j = i + 1;
                while (j < ma.length() && ma.charAt(j) != '"') {
                    j += ma.charAt(j) == '\\' ? 2 : 1;
                }
                int cuoi = Math.min(j + 1, ma.length());
                ra.append(ma, i, cuoi);
                i = cuoi;
            } else {
                ra.append(c);
                i++;
            }
        }
        return ra.toString();
    }

    /**
     * ⭐⭐ Bóc chú thích SQL — <b>đây là chỗ §10.62 đã cắn</b>.
     *
     * <p>Một câu {@code -- SELECT … FROM hydro_readings} là một câu <i>đã chết</i>: nó không chạy,
     * nên đòi nó lọc chất lượng là vô nghĩa, và tệ hơn — nếu bộ canh <i>không</i> bóc chú thích thì
     * người ta chữa một cảnh báo bằng cách chú thích câu SQL đi, và bộ canh xanh trở lại trong khi
     * câu thật nằm ở chỗ khác.
     *
     * <p>⚠ Tôn trọng chuỗi nháy đơn: {@code '--'} bên trong một literal ⛔ không phải chú thích.
     */
    static String boChuThichSql(String sql) {
        StringBuilder ra = new StringBuilder(sql.length());
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (c == '\'') {
                int j = i + 1;
                while (j < sql.length() && sql.charAt(j) != '\'') {
                    j++;
                }
                int cuoi = Math.min(j + 1, sql.length());
                ra.append(sql, i, cuoi);
                i = cuoi;
            } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                int het = sql.indexOf('\n', i);
                i = het < 0 ? sql.length() : het;
            } else if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                int het = sql.indexOf("*/", i + 2);
                i = het < 0 ? sql.length() : het + 2;
            } else {
                ra.append(c);
                i++;
            }
        }
        return ra.toString();
    }

    /**
     * Câu SQL đọc bảng canh, moi từ <b>chuỗi ký tự</b> của một tệp Java.
     *
     * <p>⚠ Gộp các literal nối nhau bằng {@code +}: một câu dựng bằng {@code "… FROM " + BANG} thì
     * bài này ⛔ không thấy — khoảng mù đã khai ở javadoc lớp.
     */
    static List<CauSql> cauTrongJava(String tenLop, String ma) {
        String sach = boChuThichJava(ma);
        List<CauSql> ket = new ArrayList<>();
        List<Literal> chuoi = literalTrong(sach);

        int i = 0;
        while (i < chuoi.size()) {
            // Gộp chuỗi nối bằng `+` thành MỘT câu: `"SELECT ... " + "FROM x WHERE ..."` chỉ là
            // một câu SQL bị xuống dòng, và từng mảnh rời thì `CO_VE_LA_SQL` không nhận ra.
            int j = i;
            StringBuilder gom = new StringBuilder(chuoi.get(i).noiDung());
            while (j + 1 < chuoi.size()
                    && chiCoMotDauCong(
                            sach, chuoi.get(j).ketThuc(), chuoi.get(j + 1).batDau())) {
                gom.append(' ').append(chuoi.get(j + 1).noiDung());
                j++;
            }
            String noiDung = gom.toString();
            if (CO_VE_LA_SQL.matcher(noiDung).find()) {
                String ten = tenHangNgayTruoc(sach, chuoi.get(i).batDau());
                ket.add(new CauSql(tenLop + "#" + (ten == null ? "inline" : ten), noiDung));
            }
            i = j + 1;
        }
        return ket;
    }

    /** Một literal chuỗi trong mã Java, kèm vị trí để biết cái nào đứng cạnh cái nào. */
    private record Literal(int batDau, int ketThuc, String noiDung) {}

    /**
     * ⚠⚠ <b>Bóc literal bằng BỘ QUÉT KÝ TỰ, không bằng regex — §10.73.</b>
     *
     * <p>Bản trước dùng {@code "(?:[^"\\]|\\.)*"}. Trong Java, một {@code *} bọc quanh <b>nhóm có
     * lựa chọn</b> được thực thi bằng <b>đệ quy</b>: mỗi ký tự của literal là một khung stack. Kho
     * này có một literal dài <b>1521 ký tự</b> ({@code HydroTimeSeriesWriter}) ⇒ hơn một nghìn năm
     * trăm khung chỉ cho một lần khớp, cộng khung của JUnit/Surefire bên dưới.
     *
     * <p>⛔ Hệ quả là bộ canh <b>mong manh theo kích thước kho</b> chứ không theo tính đúng đắn: nó
     * xanh ở máy (stack lớn) và ném {@code StackOverflowError} trên runner CI, và ngưỡng ấy trôi
     * mỗi khi có người viết thêm một câu SQL dài. Đo được ngày 3/9/2026: cùng một cây mã, mặc định
     * ở macOS thì xanh, {@code -Xss512k} thì đỏ. ⚠ Nó <b>không hỏng vì sai</b> — nó hỏng vì kho lớn
     * lên, tức là một cổng kiểm sẽ tự tắt vào một ngày không ai đoán trước.
     *
     * <p>Bộ quét dưới đây chạy <b>vòng lặp phẳng, O(n), 0 đệ quy</b> — cùng idiom với
     * {@link #boChuThichJava(String)} ngay trong tệp này, thứ vốn đã phải viết tay vì cùng lý do.
     */
    private static List<Literal> literalTrong(String ma) {
        List<Literal> ket = new ArrayList<>();
        int i = 0;
        while (i < ma.length()) {
            char c = ma.charAt(i);
            if (ma.startsWith("\"\"\"", i)) {
                int het = ma.indexOf("\"\"\"", i + 3);
                int cuoi = het < 0 ? ma.length() : het + 3;
                ket.add(new Literal(i, cuoi, ma.substring(i + 3, Math.max(i + 3, cuoi - 3))));
                i = cuoi;
            } else if (c == '"') {
                StringBuilder than = new StringBuilder();
                int j = i + 1;
                while (j < ma.length() && ma.charAt(j) != '"') {
                    if (ma.charAt(j) == '\\' && j + 1 < ma.length()) {
                        than.append(ma.charAt(j + 1));
                        j += 2;
                    } else {
                        than.append(ma.charAt(j));
                        j++;
                    }
                }
                int cuoi = Math.min(j + 1, ma.length());
                ket.add(new Literal(i, cuoi, than.toString()));
                i = cuoi;
            } else {
                i++;
            }
        }
        return ket;
    }

    /** Giữa hai literal chỉ có khoảng trắng và ĐÚNG MỘT dấu {@code +} ⇒ chúng là một câu bị nối. */
    private static boolean chiCoMotDauCong(String ma, int tu, int den) {
        boolean thayCong = false;
        for (int k = tu; k < den; k++) {
            char c = ma.charAt(k);
            if (c == '+') {
                if (thayCong) {
                    return false;
                }
                thayCong = true;
            } else if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return thayCong;
    }

    /** Tên hằng khai ngay trước literal, để tên nguồn đọc được là {@code Lớp#TEN_HANG}. */
    private static final Pattern TEN_HANG_TRUOC = Pattern.compile("static\\s+final\\s+String\\s+(\\w+)\\s*=\\s*$");

    private static String tenHangNgayTruoc(String ma, int viTri) {
        Matcher m = TEN_HANG_TRUOC.matcher(ma.substring(Math.max(0, viTri - 160), viTri));
        return m.find() ? m.group(1) : null;
    }

    /**
     * ⭐⭐ Bóc mệnh đề {@code FILTER (…)} của hàm gộp — <b>lỗ hổng tìm ra khi dựng WS-34</b>.
     *
     * <p>{@code FILTER (WHERE quality = 'HOP_LE')} lọc cho <b>một hàm gộp</b>, ⛔ không lọc cho câu.
     * Một câu như
     *
     * <pre>SELECT avg(reading_value), count(*) FILTER (WHERE quality = 'HOP_LE') FROM …</pre>
     *
     * có {@code avg} chạy trên <b>toàn bộ</b> ba nhóm chất lượng, mà bộ canh bản trước vẫn xanh vì
     * chuỗi {@code quality = 'HOP_LE'} <i>có mặt ở đâu đó trong câu</i>. Đúng họ với
     * {@code quality <> 'XOA'}: một câu <b>gần đúng</b> nguy hiểm hơn một câu thiếu hẳn, vì nó trông
     * đã cẩn thận.
     *
     * <p>⚠ Lỗ này ⛔ không phải giả thuyết — nó lộ ra vì báo cáo BC-13 (T34.3) là truy vấn đầu tiên
     * của dự án dùng {@code FILTER}, và nếu bịt sau thì câu ấy đã kịp đóng dấu "đạt".
     *
     * <p>Bộ quét đếm ngoặc cân bằng, ⛔ không regex — cùng lý do §10.73.
     */
    static String boFilterGop(String sql) {
        StringBuilder ra = new StringBuilder(sql.length());
        Matcher m = Pattern.compile("(?i)\\bfilter\\s*\\(").matcher(sql);
        int tu = 0;
        while (m.find(tu)) {
            ra.append(sql, tu, m.start());
            int sau = m.end();
            int sau2 = 1;
            while (sau < sql.length() && sau2 > 0) {
                char c = sql.charAt(sau);
                if (c == '(') {
                    sau2++;
                } else if (c == ')') {
                    sau2--;
                }
                sau++;
            }
            tu = sau;
        }
        return ra.append(sql.substring(tu)).toString();
    }

    /** Tách một tệp migration thành các câu, ⚠ ⛔ không cắt bên trong khối {@code $$ … $$}. */
    static List<CauSql> cauTrongSql(String tenTep, String sql) {
        List<CauSql> ket = new ArrayList<>();
        String[] khoi = sql.split("\\$\\$", -1);
        StringBuilder ngoai = new StringBuilder();
        for (int i = 0; i < khoi.length; i++) {
            // Phần chẵn nằm NGOÀI khối `$$`; phần lẻ là thân hàm/DO — giữ nguyên, không tách theo `;`.
            if (i % 2 == 0) {
                ngoai.append(khoi[i]).append("\n;\n");
            } else {
                ket.add(new CauSql(tenTep + "#$$" + i, khoi[i]));
            }
        }
        String[] cau = ngoai.toString().split(";");
        for (int i = 0; i < cau.length; i++) {
            ket.add(new CauSql(tenTep + "#" + i, cau[i]));
        }
        return ket;
    }

    /** Mọi câu SQL của mã main chạm vào một bảng đang canh. */
    private static List<CauSql> cauChamBangCanh() {
        List<CauSql> ket = new ArrayList<>();
        for (Path tep : tepMain()) {
            String ten = tep.getFileName().toString();
            String noiDung = docTep(tep);
            List<CauSql> ungVien =
                    ten.endsWith(".java") ? cauTrongJava(ten.replace(".java", ""), noiDung) : cauTrongSql(ten, noiDung);
            for (CauSql c : ungVien) {
                String sach = boChuThichSql(c.sql());
                if (chamBangCanh(sach)) {
                    ket.add(new CauSql(c.nguon(), sach));
                }
            }
        }
        return ket;
    }

    /** {@code FROM/JOIN/UPDATE <bảng canh>} — ⛔ {@code INSERT INTO … VALUES} không phải truy vấn đọc. */
    private static boolean chamBangCanh(String sqlDaBocChuThich) {
        Matcher m = DOC_BANG.matcher(sqlDaBocChuThich);
        while (m.find()) {
            if (BANG_CANH.contains(m.group(1).toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    // =========================================================================
    // Khẳng định
    // =========================================================================

    @Test
    @DisplayName("⭐⭐ Mọi truy vấn ĐỌC hydro_readings đều lọc quality — ngoại lệ phải khai có tên")
    void moiTruyVanDocDeuLocChatLuong() {
        List<String> viPham = cauChamBangCanh().stream()
                // ⭐ Bóc `FILTER (…)` TRƯỚC khi khớp: nó lọc cho một hàm gộp, ⛔ không lọc cho câu.
                .filter(c -> !VI_TU_CHAT_LUONG.matcher(boFilterGop(c.sql())).find())
                .filter(c -> !NGOAI_LE.containsKey(c.nguon()))
                .map(c -> c.nguon() + " → " + gonLai(c.sql()))
                .toList();

        assertThat(viPham)
                .as(
                        """
                        ⛔ Truy vấn đọc `hydro_readings` mà KHÔNG lọc `quality` — quy tắc 14, bẫy sai số \
                        liệu dễ mắc nhất của dự án. Bản ghi NGHI_NGO và XOA nằm CHUNG bảng chính, nên \
                        một truy vấn không lọc trả về số sai mà vẫn đúng định dạng, vẫn vẽ được biểu đồ \
                        đẹp, và KHÔNG có lỗi nào.

                        Sửa: thêm `AND quality = 'HOP_LE'`. Nếu câu này CỐ Ý đọc cả dòng nghi ngờ thì \
                        khai vào `NGOAI_LE` KÈM LÝ DO — ⛔ đừng nới regex.""")
                .isEmpty();
    }

    @Test
    @DisplayName("⛔ Chống xanh trên tập rỗng — số câu soi được chỉ được tăng (luật 7 · luật 29)")
    void soCauSoiDuocKhongDuocTut() {
        List<CauSql> soi = cauChamBangCanh();

        // ⭐ Khẳng định VỀ SỐ LƯỢNG: nó không chia sẻ giả định nào với `VI_TU_CHAT_LUONG`. Bộ tách
        //   chuỗi hỏng, thư mục đổi tên, hay ai đó gói SQL theo kiểu bài này không đọc được — cả ba
        //   làm khẳng định phía trên chạy qua tập rỗng và xanh trọn vẹn.
        assertThat(soi)
                .as(
                        "bộ tách phải còn nhìn thấy mã: %d câu, nguồn %s",
                        soi.size(), soi.stream().map(CauSql::nguon).toList())
                .hasSizeGreaterThanOrEqualTo(SO_CAU_TOI_THIEU);
    }

    @Test
    @DisplayName("⛔ Mọi mục NGOAI_LE phải còn tồn tại — một ngoại lệ mồ côi là một luật đã lỏng mà không ai biết")
    void ngoaiLeKhongDuocMoCoi() {
        Set<String> conSong =
                cauChamBangCanh().stream().map(CauSql::nguon).collect(java.util.stream.Collectors.toSet());

        assertThat(conSong)
                .as(
                        """
                        ⛔ `NGOAI_LE` khai một câu SQL không còn tồn tại. Ngoại lệ mồ côi nguy hiểm hơn \
                        một ngoại lệ thừa: nó sẽ khớp trở lại với một câu SQL TƯƠNG LAI mang cùng tên \
                        hằng, và câu ấy được miễn kiểm mà không ai quyết định điều đó.""")
                .containsAll(NGOAI_LE.keySet());
    }

    /**
     * ⭐⭐ Vế kiểm chứng ngược — {@code conventions.md} §1.5.
     *
     * <p>⚠ Luật 29: <i>một bài kiểm chứng ngược có thể sai theo đúng cách mà thứ nó kiểm đang sai</i>
     * — người viết cả hai là cùng một người, mang cùng một giả định. Nên các bài dưới đây ⛔ không
     * chỉ hỏi "có đỏ không": chúng đo từng mảnh <b>riêng</b> của bộ máy (bóc chú thích, nhận diện
     * bảng, khớp vị từ) và ép mỗi mảnh phân biệt được <b>hai trạng thái khác nhau</b> (luật 9).
     */
    @Nested
    @DisplayName("Bộ canh tự kiểm chứng")
    class BoCanhTuKiem {

        private static final String CAU_HONG = "SELECT reading_value FROM hydro_readings WHERE station_id = ?";
        private static final String CAU_DUNG = CAU_HONG + " AND quality = 'HOP_LE'";

        @Test
        @DisplayName("⭐ Câu thiếu bộ lọc BỊ BẮT, câu có bộ lọc thì KHÔNG — hai trạng thái phân biệt được")
        void batDuocCauThieuLoc() {
            assertThat(chamBangCanh(CAU_HONG)).isTrue();
            assertThat(VI_TU_CHAT_LUONG.matcher(CAU_HONG).find()).isFalse();
            assertThat(VI_TU_CHAT_LUONG.matcher(CAU_DUNG).find()).isTrue();
        }

        @Test
        @DisplayName("⭐⭐ §10.62 — câu bị CHÚ THÍCH không còn là câu: `--` phải bị bóc TRƯỚC khi khớp")
        void chuThichSqlBiBocTruocKhiKhop() {
            String biChuThich = "-- " + CAU_HONG + "\nSELECT 1";
            assertThat(chamBangCanh(boChuThichSql(biChuThich)))
                    .as("một lượt kiểm chứng ngược ngày 28/8 đặt `--` trước câu DELETE và bộ canh KHÔNG đỏ")
                    .isFalse();

            assertThat(chamBangCanh(boChuThichSql("/* " + CAU_HONG + " */ SELECT 1")))
                    .isFalse();

            // ⚠ Vế phân biệt: bóc chú thích ⛔ KHÔNG được nuốt câu thật. Thiếu vế này thì một bộ bóc
            //   trả về chuỗi rỗng cũng làm bài trên xanh — và làm cả bộ canh mù (luật 9).
            assertThat(chamBangCanh(boChuThichSql("-- ghi chú\n" + CAU_HONG)))
                    .as("chú thích ở dòng trên ⛔ không được làm mất câu SQL ở dòng dưới")
                    .isTrue();
        }

        @Test
        @DisplayName("⭐⭐ `quality <> 'XOA'` KHÔNG đủ — câu gần đúng nguy hiểm hơn câu thiếu hẳn")
        void locKhacHopLeKhongDuocTinhLaDat() {
            String ganDung = "SELECT reading_value FROM hydro_readings WHERE quality <> 'XOA'";

            assertThat(chamBangCanh(ganDung)).isTrue();
            assertThat(VI_TU_CHAT_LUONG.matcher(ganDung).find())
                    .as("`<> 'XOA'` loại bản ghi đã xoá và GIỮ NGUYÊN bản ghi nghi ngờ — đúng thứ quy tắc "
                            + "14 sinh ra để chặn, trong một câu trông đã cẩn thận. Muốn dùng thì khai NGOAI_LE")
                    .isFalse();

            // Vế phân biệt: các dạng viết hợp lệ của đúng vị từ ấy vẫn phải ĐẠT.
            assertThat(VI_TU_CHAT_LUONG
                            .matcher("... AND r.quality='HOP_LE' AND x = 1")
                            .find())
                    .isTrue();
            assertThat(VI_TU_CHAT_LUONG.matcher("... and quality = 'hop_le'").find())
                    .as("SQL không phân biệt hoa thường ở từ khoá, nhưng chuỗi 'hop_le' thì CÓ — "
                            + "PostgreSQL sẽ không khớp dòng nào, nên bộ canh cũng không được nhận")
                    .isFalse();
        }

        @Test
        @DisplayName("⭐⭐ `FILTER (WHERE quality = 'HOP_LE')` KHÔNG phải bộ lọc của câu — lỗ tìm ra ở WS-34")
        void filterCuaHamGopKhongDuocTinhLaBoLoc() {
            String lua = "SELECT avg(reading_value), count(*) FILTER (WHERE quality = 'HOP_LE') "
                    + "FROM hydro_agg_daily WHERE station_id = ?";

            assertThat(chamBangCanh(lua)).isTrue();
            assertThat(VI_TU_CHAT_LUONG.matcher(lua).find())
                    .as("bản TRƯỚC của bộ canh xanh ở đây — chuỗi có mặt, nhưng `avg` chạy trên cả ba nhóm")
                    .isTrue();
            assertThat(VI_TU_CHAT_LUONG.matcher(boFilterGop(lua)).find())
                    .as("sau khi bóc FILTER thì câu này ⛔ không còn vị từ nào — phải bị bắt")
                    .isFalse();

            // ⚠ Vế phân biệt: bộ bóc ⛔ KHÔNG được nuốt vị từ THẬT ở WHERE. Thiếu vế này thì một bộ
            //   bóc trả về chuỗi rỗng cũng làm khẳng định trên xanh, và cả bộ canh mù (luật 9).
            String that = "SELECT count(*) FILTER (WHERE quality = 'NGHI_NGO') FROM hydro_agg_daily "
                    + "WHERE station_id = ? AND quality = 'HOP_LE'";
            assertThat(VI_TU_CHAT_LUONG.matcher(boFilterGop(that)).find())
                    .as("vị từ ở WHERE vẫn phải ĐẠT sau khi bóc FILTER")
                    .isTrue();

            // Ngoặc lồng bên trong FILTER ⛔ không được làm bộ quét cắt hụt.
            String long2 = "SELECT count(*) FILTER (WHERE quality IN ('HOP_LE', 'XOA')) FROM hydro_agg_daily";
            assertThat(boFilterGop(long2))
                    .as("bóc trọn mệnh đề FILTER kể cả khi bên trong còn ngoặc")
                    .doesNotContain("HOP_LE")
                    .contains("FROM hydro_agg_daily");
        }

        @Test
        @DisplayName("⛔ `quality_reason` KHÔNG được tính là đã lọc — dấu `_` là ký tự từ")
        void cotQualityReasonKhongPhaiBoLoc() {
            String lua = "SELECT quality_reason FROM hydro_readings WHERE station_id = ?";
            assertThat(chamBangCanh(lua)).isTrue();
            assertThat(VI_TU_CHAT_LUONG.matcher(lua).find())
                    .as("`quality_reason` chỉ là một cột được CHỌN — nó ⛔ không lọc gì")
                    .isFalse();
        }

        @Test
        @DisplayName("⛔ INSERT … VALUES ⛔ không phải truy vấn đọc — bộ canh không được đỏ vì đường ghi")
        void duongGhiKhongBiTinhLaTruyVanDoc() {
            assertThat(chamBangCanh("INSERT INTO hydro_readings (measured_at, quality) VALUES (?, ?)"))
                    .isFalse();
            // ⭐ Nhưng `INSERT … SELECT FROM` thì CÓ đọc, và phải bị bắt.
            assertThat(chamBangCanh("INSERT INTO hydro_agg_daily SELECT * FROM hydro_readings"))
                    .isTrue();
        }

        @Test
        @DisplayName("⛔ DDL không bị tính — ALTER/CREATE INDEX trên bảng ấy ⛔ không phải truy vấn")
        void ddlKhongBiTinh() {
            assertThat(chamBangCanh("ALTER TABLE hydro_readings ADD COLUMN quality_reason VARCHAR(200)"))
                    .isFalse();
            assertThat(chamBangCanh("CREATE INDEX ix ON hydro_readings (measured_at DESC)"))
                    .isFalse();
        }

        @Test
        @DisplayName("⛔ Bảng KHÁC có tên gần giống ⛔ không bị bắt nhầm")
        void bangKhacKhongBiBatNham() {
            assertThat(chamBangCanh("SELECT * FROM hydro_unmapped_readings"))
                    .as("`hydro_unmapped_readings` là bảng khác và ⛔ không có cột quality")
                    .isFalse();
            assertThat(chamBangCanh("SELECT * FROM hydro_latest")).isFalse();
        }

        @Test
        @DisplayName("⛔ Chú thích Java bị bóc — hơn 20 lần `hydro_readings` trong javadoc ⛔ không được thành ứng viên")
        void javadocKhongThanhUngVien() {
            String lop =
                    """
                    /** Bảng {@code hydro_readings} — SELECT * FROM hydro_readings không lọc gì. */
                    class X {
                        // SELECT * FROM hydro_readings cũng vậy
                        static final String THAT = "SELECT v FROM hydro_readings WHERE quality = 'HOP_LE'";
                    }
                    """;
            List<CauSql> ra = cauTrongJava("X", lop).stream()
                    .filter(c -> chamBangCanh(c.sql()))
                    .toList();

            assertThat(ra).hasSize(1);
            assertThat(ra.get(0).nguon()).isEqualTo("X#THAT");
        }

        @Test
        @DisplayName("⛔ Chuỗi chỉ chứa TÊN BẢNG ⛔ không phải câu SQL — `Set.of(\"hydro_readings\")` không bị bắt")
        void danhSachTenBangKhongPhaiSql() {
            String lop = "class Y { static final Set<String> BANG = Set.of(\"hydro_raw_logs\", \"hydro_readings\"); }";
            assertThat(cauTrongJava("Y", lop)).isEmpty();
        }
    }

    // =========================================================================

    private static String gonLai(String sql) {
        String mot = sql.replaceAll("\\s+", " ").trim();
        return mot.length() <= 160 ? mot : mot.substring(0, 160) + "…";
    }

    private static String docTep(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Mọi tệp {@code .java} và {@code .sql} của <b>mã main</b> backend. */
    private static List<Path> tepMain() {
        Path backend = thuMucBackend();
        try (Stream<Path> cay = Files.walk(backend)) {
            return cay.filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/")
                                && !s.contains("/target/")
                                && (s.endsWith(".java") || s.endsWith(".sql"));
                    })
                    .sorted()
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
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
