package com.songnhue.hydro.infra;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.hydro.domain.AdapterType;
import com.songnhue.hydro.domain.DiaChiNguon;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.TelemetryAdapter;
import com.songnhue.hydro.domain.TelemetryBatch;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;

/**
 * Adapter cho {@code songnhue.bhh40.net} — {@code GET /api/getmn.aspx?key=<mã số>;} (T30.2).
 *
 * <h2>Vì sao {@code HttpClient} của JDK, và vì sao nó là một TRƯỜNG chứ không phải một bean</h2>
 *
 * <p>Một lời gọi GET không đáng kéo thêm một thư viện HTTP vào cây phụ thuộc — mỗi thư viện là một
 * dòng nữa phải theo dõi CVE hằng đêm. Và ⛔ <b>không khai bean {@code HttpClient}/{@code RestClient}
 * trần</b> ({@code HydroConfig}, §9.7): một bean cùng kiểu với thứ Boot tự cấu hình làm Boot
 * <i>ngừng tạo bean chính</i>, và triệu chứng nằm cách nguyên nhân rất xa.
 *
 * <h2>⚠⚠ Ép HTTP/1.1 — và phép kiểm phải ĐO, không được KHẲNG ĐỊNH</h2>
 *
 * <p>Nguồn là IIS 8.5 / ASP.NET WebForms. {@code HttpClient} của JDK mặc định HTTP/2 và với
 * {@code http://} nó gửi kèm {@code Connection: Upgrade} + {@code HTTP2-Settings} để thử nâng cấp —
 * đúng cặp header đã làm máy chủ Node của Next đóng kết nối ở WS-16 (§10.18).
 *
 * <p>⛔ Bài kiểm ⛔ <b>không</b> được khẳng định {@code exchange.getProtocol()}: máy chủ trong JDK chỉ
 * nói HTTP/1.1 nên client tự hạ cấp, và khẳng định ấy xanh ở <i>cả hai</i> cấu hình — §10.36 đã có
 * đúng một bài xanh giả kiểu này. Thứ thật sự phân biệt là <b>hai header trên có mặt hay không</b>.
 *
 * <h2>⚠⚠ Thân phản hồi mang chính mã số — phải che trước khi nó đi đâu</h2>
 *
 * <p>Đo 01/09/2026: trang ASP.NET rỗng ở đuôi chứa
 * {@code <form action="./getmn.aspx?key=<mã số>%3b">}. Nghĩa là "ghi nguyên văn response" (quy tắc
 * 18) sẽ chép credential vào {@code hydro_raw_logs} — bảng nằm trong mọi bản sao lưu và
 * {@code songnhue_readonly} đọc được. Lý lẽ đầy đủ ở {@link TelemetryFetch}; ở đây chỉ giữ hai điều
 * thao tác:
 *
 * <ol>
 *   <li>Che <b>cả hai dạng</b>: nguyên văn và {@code %3b}/{@code %3B} do URL-encoding sinh ra. Chỉ
 *       che một dạng là che một nửa, và nửa còn lại chính là nửa mà nguồn thật sự trả về.
 *   <li>Che <b>cả {@code failureDetail}</b>, không chỉ thân: thông báo của một số ngoại lệ mạng có
 *       kèm nguyên URI đã gọi — tức kèm nguyên mã số.
 * </ol>
 */
@Component
public class Bhh40Adapter implements TelemetryAdapter {

    private static final Logger log = LoggerFactory.getLogger(Bhh40Adapter.class);

    /** ⚠ Đường dẫn tương đối — {@code URI.resolve} lo phần thiếu/thừa dấu {@code /} của base URL. */
    static final String DUONG_DAN = "api/getmn.aspx";

    /**
     * Trần kích thước thân phản hồi.
     *
     * <p>Đo thật: 1 659 byte cho 28 trạm. Trần 4 MB là hơn hai nghìn lần mức ấy — nó không chặn dữ
     * liệu thật, nó chặn <b>một nguồn hỏng trả về vô hạn</b>. Không có trần thì
     * {@code BodyHandlers.ofString()} đọc hết vào bộ nhớ, và hệ này chạy <b>một node</b> với ngân
     * sách RAM đã tính chặt ({@code hosting_recommendations.md} §8): một response 2 GB là một lượt
     * OOM kéo theo cả cổng công khai lẫn trang quản trị.
     */
    static final int TRAN_BYTE_THAN = 4 * 1024 * 1024;

    private static final Duration CHO_KET_NOI = Duration.ofSeconds(10);

    /** Escape URL {@code %XX} — dùng để hạ chữ thường phần hex mà ⛔ không đụng phần còn lại. */
    private static final Pattern ESCAPE = Pattern.compile("%([0-9A-Fa-f]{2})");

    private final HttpClient client;
    private final boolean chapNhanMayNoiBo;

    public Bhh40Adapter(HydroApiProperties properties) {
        this.chapNhanMayNoiBo = properties.isAllowInternalHost();
        this.client = HttpClient.newBuilder()
                .connectTimeout(CHO_KET_NOI)
                .version(HttpClient.Version.HTTP_1_1)
                // ⛔ Không đi theo chuyển hướng: đi theo 302 là gửi mã số tới một máy chủ mình không
                //   chọn, và cả bộ kiểm SSRF phía trên trở thành vô nghĩa vì nó chỉ soi chặng đầu.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public AdapterType kieu() {
        return AdapterType.BHH40;
    }

    @Override
    public TelemetryFetch goi(TelemetryCall yeuCau) {
        URI dich = DiaChiNguon.kiemVaDung(
                yeuCau.baseUrl(), DUONG_DAN + "?key=" + maHoaMaSo(yeuCau.maSo()), chapNhanMayNoiBo);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(dich)
                .timeout(yeuCau.timeout())
                .header("Accept", "text/html, text/plain")
                .GET()
                .build();

        long batDau = System.nanoTime();
        try {
            HttpResponse<InputStream> phanHoi = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            return doc(phanHoi, yeuCau.maSo(), mili(batDau));
        } catch (HttpTimeoutException e) {
            // ⚠ Nhánh riêng, ⛔ không gộp vào IOException: "nguồn treo" và "không có đường mạng" đòi
            //   hai cách xử lý ngược nhau (chờ tiếp / gọi nhà mạng). §10.68-B — cùng một vân tay cho
            //   ba nguyên nhân là cách chắc chắn để không ai xử lý được nguyên nhân nào.
            return hong(SyncFailureKind.TIMEOUT, "Nguồn không trả lời trong " + yeuCau.timeout(), yeuCau, batDau);
        } catch (IOException e) {
            return hong(SyncFailureKind.HTTP_ERROR, "Không kết nối được nguồn: " + e.getMessage(), yeuCau, batDau);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return hong(SyncFailureKind.HTTP_ERROR, "Bị ngắt khi đang gọi nguồn", yeuCau, batDau);
        }
    }

    @Override
    public TelemetryBatch boc(String body) {
        return Bhh40Parser.boc(body);
    }

    private TelemetryFetch doc(HttpResponse<InputStream> phanHoi, String maSo, int ms) throws IOException {
        byte[] thoBytes;
        try (InputStream in = phanHoi.body()) {
            // ⚠ readNBytes(TRẦN + 1): đọc dư đúng MỘT byte là cách duy nhất phân biệt "vừa đúng
            //   trần" với "vượt trần" mà không phải đọc hết (luật 9 — một khẳng định không phân biệt
            //   được hai trạng thái thì không khẳng định gì).
            thoBytes = in.readNBytes(TRAN_BYTE_THAN + 1);
        }
        int ma = phanHoi.statusCode();
        if (thoBytes.length > TRAN_BYTE_THAN) {
            return new TelemetryFetch(
                    ma, ms, null, SyncFailureKind.HTTP_ERROR, "Thân phản hồi vượt trần " + TRAN_BYTE_THAN + " byte");
        }
        // ⭐ Che NGAY tại đây — trước mọi lượt log, mọi lượt trả về, mọi lượt ghi.
        String than = cheMaSo(new String(thoBytes, StandardCharsets.UTF_8), maSo);

        if (ma / 100 != 2) {
            // ⚠ Vẫn giữ thân: một trang lỗi của IIS là thứ duy nhất nói vì sao nguồn từ chối, và
            //   nguồn không có API lịch sử để hỏi lại.
            return new TelemetryFetch(ma, ms, than, SyncFailureKind.HTTP_ERROR, "Nguồn trả HTTP " + ma);
        }
        if (than.isBlank()) {
            return new TelemetryFetch(ma, ms, than, SyncFailureKind.EMPTY_BODY, "Nguồn trả HTTP 200 với thân rỗng");
        }
        if (Bhh40Parser.nguonBaoHong(than)) {
            return new TelemetryFetch(
                    ma,
                    ms,
                    than,
                    SyncFailureKind.NOT_WORKING,
                    "Nguồn trả '" + Bhh40Parser.CHUOI_NGUON_HONG + "' — kiểm lại mã số, ⚠ kể cả dấu ';' ở cuối");
        }
        log.debug("Nguồn bhh40 trả {} byte trong {} ms", than.length(), ms);
        return new TelemetryFetch(ma, ms, than, null, null);
    }

    private TelemetryFetch hong(SyncFailureKind kieu, String lyDo, TelemetryCall yeuCau, long batDau) {
        // ⛔ Che cả lý do: thông báo của một số ngoại lệ mạng kèm nguyên URI đã gọi — tức kèm mã số.
        String sach = cheMaSo(lyDo, yeuCau.maSo());
        log.warn("Gọi nguồn hỏng ({}): {}", kieu, sach);
        return new TelemetryFetch(null, mili(batDau), null, kieu, sach);
    }

    private static int mili(long batDauNano) {
        return (int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - batDauNano) / 1_000_000L);
    }

    /**
     * Đưa mã số lên dây.
     *
     * <p>⚠⚠ Mã hoá mọi ký tự <b>trừ dấu {@code ;} cuối</b>. Hai vế đều bắt buộc:
     *
     * <ul>
     *   <li>Không mã hoá thì một mã số chứa {@code &} hoặc {@code #} tự chèn thêm tham số vào URL —
     *       một mã số là dữ liệu do người nhập, dù người ấy là quản trị viên.
     *   <li>Mã hoá luôn {@code ;} thì ta gửi {@code %3B}, mà thứ <b>đã đo là chạy được</b> là dấu
     *       {@code ;} nguyên văn. Thiếu nó nguồn trả {@code not.working} — <b>trông y hệt mã số
     *       sai</b>, và ta sẽ đi tìm nhầm chỗ. ⛔ Không có lượt gọi thử nào để kiểm lại giả thiết
     *       "nguồn cũng chấp nhận %3B": nguồn có thể chặn IP, và §10.68-C là chuyện một lượt deploy
     *       đã tự cấm chính nó.
     * </ul>
     */
    static String maHoaMaSo(String maSo) {
        return URLEncoder.encode(maSo, StandardCharsets.UTF_8).replace("%3B", ";");
    }

    /**
     * Thay mọi lần xuất hiện của mã số bằng {@link TelemetryFetch#DAU_CHE_MA_SO}.
     *
     * <h2>⚠ Bốn dạng, không phải một — và cái ta thật sự gặp là dạng thứ tư</h2>
     *
     * <p>Response đo được mang {@code key=<mã số>%3b}: ASP.NET mã hoá lại giá trị khi in vào thuộc
     * tính {@code action}, và nó dùng <b>hex chữ thường</b> trong khi {@code URLEncoder} của Java
     * sinh <b>hex chữ HOA</b>. Che theo đúng chuỗi {@code URLEncoder} trả về là che trượt đúng cái
     * duy nhất đang nằm trong bảng.
     *
     * <p>⛔ Và ⛔ không được hạ chữ thường cả chuỗi đã mã hoá: mã số có thể có chữ hoa, hạ hết đi thì
     * chuỗi so sánh không còn khớp gì. Chỉ hạ <b>phần escape</b> {@code %XX}.
     *
     * <p>⚠ Thứ tự thay quan trọng: dạng dài nhất trước. Thay dạng ngắn trước thì phần thân đã bị
     * thay một nửa và cái đuôi {@code ;} hay {@code %3b} còn đứng lại — nửa còn lại ấy vô hại một
     * mình, nhưng nó làm phép kiểm "thân không chứa mã số" xanh trong khi thân vẫn lộ ra độ dài và
     * hình dạng của giá trị.
     */
    static String cheMaSo(String van, String maSo) {
        if (van == null || maSo == null || maSo.isEmpty()) {
            return van;
        }
        String daMaHoa = URLEncoder.encode(maSo, StandardCharsets.UTF_8);
        String[] cacDang = {
            maSo, // nguyên văn, kể cả dấu ';'
            daMaHoa, // URLEncoder của Java — hex HOA
            hexThuong(daMaHoa), // ASP.NET — hex thường: chính dạng đo được
            maHoaMaSo(maSo), // thứ ta gửi đi: mã hoá nhưng giữ ';'
            hexThuong(maHoaMaSo(maSo)),
        };
        String ketQua = van;
        for (String dang : cacDang) {
            if (!dang.isEmpty()) {
                ketQua = ketQua.replace(dang, TelemetryFetch.DAU_CHE_MA_SO);
            }
        }
        return ketQua;
    }

    /** Hạ chữ thường <b>chỉ hai chữ số hex</b> của mỗi escape {@code %XX} — ⛔ không đụng phần còn lại. */
    private static String hexThuong(String daMaHoa) {
        return ESCAPE.matcher(daMaHoa).replaceAll(m -> "%" + m.group(1).toLowerCase(Locale.ROOT));
    }
}
