package com.songnhue.hydro.infra;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryReading;

/**
 * Bóc thân phản hồi của {@code getmn.aspx} — <b>quy tắc parse 2, 3, 4, 6, 8</b> của CN-03.2.
 *
 * <h2>Định dạng, đo thật ngày 01/09/2026 (không chép từ tài liệu)</h2>
 *
 * <pre>
 * F01830;01/09/2026;10:20;value=240;&lt;br&gt;F01613;01/09/2026;10:20;value=198;&lt;br&gt;…&lt;br&gt;
 * &lt;!DOCTYPE html …&gt;   ← trang ASP.NET RỖNG, luôn nối vào đuôi
 * </pre>
 *
 * <ul>
 *   <li>{@code Content-Type: text/html}, ⛔ không phải JSON — 28 bản ghi nối bằng thẻ {@code <br>},
 *       ⛔ không phải ký tự xuống dòng;
 *   <li>cả 28 dòng cùng một mốc {@code 01/09/2026 10:20} — mốc <b>khung 10 phút</b> của nguồn, ⛔
 *       không phải giờ ta gọi (10:24);
 *   <li>đơn vị là số nguyên <b>cm</b>.
 * </ul>
 *
 * <h2>⛔ Ba chỗ cố ý KHÔNG thắt chặt</h2>
 *
 * <ol>
 *   <li><b>Regex giữ {@code ^([A-Z]\d+);…} của spec</b>, ⛔ không thắt thành {@code F\d{5}}. Hai mươi
 *       tám mã hôm nay đều {@code F} + 5 chữ số, nhưng đó là <i>một lượt đo</i>, không phải một cam
 *       kết của nguồn. Thắt lại thì ngày nguồn thêm một trạm mang tiền tố khác, toàn bộ trạm ấy vào
 *       {@link TelemetryBatch#soDongRac()} — và số đo mất là <b>mất vĩnh viễn</b> vì không có API
 *       lịch sử. ⚠ Ràng buộc {@code ^F[0-9]{5}$} vẫn đứng ở cột {@code stations.api_code}: chỗ khai
 *       báo được phép nghiêm khắc, chỗ <i>nhận</i> dữ liệu thì không.
 *   <li><b>Nhận {@code <br>}, {@code <BR>}, {@code <br/>}, {@code <br />}</b>. Nguồn là ASP.NET
 *       WebForms và HTML không phân biệt hoa thường; một lượt nâng cấp phía họ đổi cách sinh thẻ là
 *       chuyện thường, còn hậu quả phía ta là mất trọn một response.
 *   <li><b>Nhận dấu thập phân {@code .} lẫn {@code ,}</b> — spec khai vậy, và ⚠ chỗ này đắt: đọc
 *       {@code 4,93} theo locale Việt cho ra {@code 4.93}, đọc theo locale Mỹ cho ra {@code 493}.
 *       Quy đổi bằng cách thay ký tự rồi giao cho {@code BigDecimal}, ⛔ không dùng
 *       {@code NumberFormat} (phụ thuộc locale của máy chủ, thứ không ai đặt trong tệp compose).
 * </ol>
 *
 * <h2>⚠ Một dòng hỏng KHÔNG được làm hỏng cả mẻ</h2>
 *
 * <p>Quy tắc 4 nói thẳng điều đó, và lý do là số học: bỏ cả mẻ vì một ký tự lạ là vứt 27 số đo tốt
 * để phản ứng với 1 số đo xấu. Mỗi dòng hỏng cộng vào {@link TelemetryBatch#soDongRac()} và ghi một
 * dòng log — con số ấy lên màn hình Nhật ký đồng bộ, nên nguồn đổi định dạng là <i>nhìn thấy được</i>
 * chứ không phải một khoảng lặng.
 */
public final class Bhh40Parser {

    private static final Logger log = LoggerFactory.getLogger(Bhh40Parser.class);

    /** Chuỗi nguồn trả khi mã số sai <b>hoặc thiếu dấu {@code ;} cuối</b> — quy tắc 2. */
    static final String CHUOI_NGUON_HONG = "not.working";

    /** ⛔ Nguyên văn regex của {@code function-spec.md} CN-03.2 quy tắc 4. Đọc javadoc trước khi sửa. */
    private static final Pattern DONG =
            Pattern.compile("^([A-Z]\\d+);(\\d{2}/\\d{2}/\\d{4});(\\d{2}:\\d{2});value=(-?\\d+(?:[.,]\\d+)?);$");

    private static final Pattern NGAT_DONG = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);

    /**
     * ⚠ {@code uuuu} chứ không {@code yyyy}, và {@link ResolverStyle#STRICT}.
     *
     * <p>Mặc định ({@code SMART}) <b>im lặng nắn</b> {@code 31/02/2026} thành 28/02 — một mốc thời
     * gian bịa đi thẳng vào khoá chống trùng của {@code hydro_readings}, và nó trông hợp lệ mãi mãi.
     * {@code STRICT} đòi trường era, mà {@code yyyy} (year-of-era) không tự suy ra được; {@code uuuu}
     * (năm theo lịch tuyệt đối) thì có. Đây là cặp bẫy quen của {@code java.time}.
     */
    private static final DateTimeFormatter MOC =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private Bhh40Parser() {}

    /**
     * Nguồn có đang tự báo hỏng không — <b>một chỗ nhận biết duy nhất</b>.
     *
     * <p>Cả {@code Bhh40Adapter.goi()} (để đặt {@code failure_kind}) lẫn {@link #boc(String)} (để trả
     * mẻ rỗng) đều cần câu trả lời này. Viết hai lần là đúng hình dạng luật 14 — <i>chỗ nào con
     * người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ</i> — nên thay vào đó có đúng một
     * hàm và hai người gọi.
     */
    static boolean nguonBaoHong(String body) {
        return body != null && body.toLowerCase(Locale.ROOT).contains(CHUOI_NGUON_HONG);
    }

    /** Bóc — xem hợp đồng ở {@code TelemetryAdapter.boc}. */
    static TelemetryBatch boc(String body) {
        if (body == null || body.isBlank()) {
            return new TelemetryBatch(List.of(), 0, 0, false);
        }
        if (nguonBaoHong(body)) {
            return TelemetryBatch.meBaoHong();
        }

        List<TelemetryReading> soDo = new ArrayList<>();
        Set<String> daThay = new HashSet<>();
        int rac = 0;
        int trung = 0;

        for (String dongTho : NGAT_DONG.split(catPhanHtml(body), -1)) {
            String dong = dongTho.trim();
            if (dong.isEmpty()) {
                // ⚠ KHÔNG tính là rác: thẻ `<br>` cuối cùng luôn sinh ra một phần tử rỗng, và một
                //   bộ đếm rác nhảy lên 1 ở MỌI lượt gọi thành công là một bộ đếm không ai đọc nữa.
                continue;
            }
            TelemetryReading soDoDong = bocMotDong(dong);
            if (soDoDong == null) {
                rac++;
                continue;
            }
            if (!daThay.add(soDoDong.khoaTrung())) {
                trung++;
                continue;
            }
            soDo.add(soDoDong);
        }
        return new TelemetryBatch(soDo, rac, trung, false);
    }

    /**
     * Quy tắc 3 — cắt bỏ trang HTML rỗng ở đuôi.
     *
     * <p>⚠ Không phân biệt hoa thường: {@code <!doctype} là cách viết hợp lệ và phổ biến không kém.
     * Nếu không cắt, dòng đầu của trang HTML sẽ thành một dòng rác ở mọi lượt gọi.
     */
    private static String catPhanHtml(String body) {
        int cat = body.toLowerCase(Locale.ROOT).indexOf("<!doctype");
        return cat < 0 ? body : body.substring(0, cat);
    }

    /** @return {@code null} khi dòng không dùng được — người gọi đếm nó vào {@code soDongRac} */
    private static TelemetryReading bocMotDong(String dong) {
        Matcher m = DONG.matcher(dong);
        if (!m.matches()) {
            log.warn("Bỏ qua dòng không khớp định dạng nguồn: {}", rutGon(dong));
            return null;
        }
        try {
            LocalDateTime gioVn = LocalDateTime.parse(m.group(2) + " " + m.group(3), MOC);
            Instant mocDo = gioVn.atZone(DateTimeUtils.ZONE_VN).toInstant();
            BigDecimal giaTriTho = new BigDecimal(m.group(4).replace(',', '.'));
            return new TelemetryReading(m.group(1), mocDo, giaTriTho, TelemetryReading.DON_VI_CM);
        } catch (DateTimeParseException e) {
            // Dòng KHỚP regex nhưng mang mốc không tồn tại (32/13, 25:70). Regex chỉ đếm chữ số, nó
            // không biết lịch — nếu để ngoại lệ này bay lên thì một dòng rác làm hỏng cả mẻ.
            log.warn("Bỏ qua dòng có mốc thời gian không tồn tại: {}", rutGon(dong));
            return null;
        } catch (IllegalArgumentException e) {
            log.warn("Bỏ qua dòng không dựng được số đo ({}): {}", e.getMessage(), rutGon(dong));
            return null;
        }
    }

    /** ⛔ Cắt trước khi log: một thân phản hồi lạ có thể dài hàng megabyte và log xoay vòng 30 ngày. */
    private static String rutGon(String dong) {
        return dong.length() <= 120 ? dong : dong.substring(0, 120) + "…";
    }
}
