package com.songnhue.hydro.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.TelemetryCall;
import com.songnhue.hydro.domain.TelemetryFetch;

/**
 * ⭐⭐ Adapter đi qua <b>một máy chủ HTTP thật</b> — T30.9.
 *
 * <h2>Vì sao mock {@code TelemetryAdapter} ở đây là chưa kiểm gì cả</h2>
 *
 * <p><b>Luật 4</b>, và dự án này đã trả giá: {@code BackupServiceTest} mock {@code PostgresToolRunner}
 * — tức mock đúng chỗ mã chạm ra ngoài — và {@code pg_dump} <b>chưa từng chạy suốt 4 ngày</b>, trong
 * khi sao lưu là lưới an toàn duy nhất của hệ. Bốn thứ dưới đây <b>không</b> quan sát được nếu không
 * có byte thật đi qua dây, và cả bốn đều đã hỏng thật ở đâu đó trong dự án:
 *
 * <ol>
 *   <li>hai header nâng cấp HTTP/2 (§10.18 — máy chủ Node đóng kết nối, lỗi báo "header parser
 *       received no bytes", một câu không nhắc gì tới HTTP/2);
 *   <li>dấu {@code ;} cuối mã số có sống sót qua tầng URL-encoding không — thiếu nó nguồn trả
 *       {@code not.working}, trông y hệt mã số sai;
 *   <li>mã số có bị che khỏi thân phản hồi trước khi thân đi đâu không;
 *   <li>trần kích thước thân có chặn thật không.
 * </ol>
 *
 * <p>{@code com.sun.net.httpserver.HttpServer} có sẵn trong JDK — không thêm phụ thuộc nào, và nó
 * ghi lại được đúng những gì đi qua dây. Khuôn chép từ {@code PortalRevalidateClientTest} (WS-16).
 *
 * <h2>⚠ Phạm vi — luật 28</h2>
 *
 * <p>Bài kiểm này <b>không</b> chứng minh {@code songnhue.bhh40.net} trả lời đúng như bản mẫu: nó
 * chứng minh phía ta <i>gửi</i> đúng thứ và <i>đọc</i> đúng thứ, và phân biệt được năm tình trạng.
 * Vế còn lại chỉ đo được bằng một lượt gọi thật, và ⛔ lượt gọi thật là thứ phải tiết kiệm — nguồn có
 * thể chặn IP (§10.68-C: một lượt deploy đã tự cấm chính nó bằng {@code ssh-keyscan}).
 */
class Bhh40AdapterHttpTest {

    /**
     * ⛔ Mã số kiểm thử — <b>không phải</b> mã số thật, và bản mẫu trong {@code resources} đã được
     * thay đúng chuỗi này. Dấu {@code ;} cuối là <b>một phần của giá trị</b>, đúng như nguồn đòi.
     */
    private static final String MA_SO = "maso-kiem-thu-0123456789;";

    private HttpServer server;
    private final List<GhiNhan> daNhan = new ArrayList<>();
    private final AtomicInteger maTraVe = new AtomicInteger(200);
    private final AtomicReference<byte[]> thanTraVe = new AtomicReference<>();
    private final AtomicInteger treGiay = new AtomicInteger(0);

    /** Một lượt gọi đã đi qua dây. */
    private record GhiNhan(String method, String path, String rawQuery, String moiHeader) {}

    @BeforeEach
    void dungMayChu() throws IOException {
        thanTraVe.set(banMau());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/getmn.aspx", this::ghiNhanRoiTraLoi);
        server.start();
    }

    @AfterEach
    void dungLai() {
        server.stop(0);
    }

    private void ghiNhanRoiTraLoi(HttpExchange exchange) throws IOException {
        daNhan.add(new GhiNhan(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getRawQuery(),
                exchange.getRequestHeaders().entrySet().toString().toLowerCase(Locale.ROOT)));
        if (treGiay.get() > 0) {
            try {
                Thread.sleep(Duration.ofSeconds(treGiay.get()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        byte[] than = thanTraVe.get();
        exchange.sendResponseHeaders(maTraVe.get(), than.length);
        exchange.getResponseBody().write(than);
        exchange.close();
    }

    private Bhh40Adapter adapter(boolean chapNhanNoiBo) {
        HydroApiProperties props = new HydroApiProperties();
        props.setAllowInternalHost(chapNhanNoiBo);
        return new Bhh40Adapter(props);
    }

    private String diaChi() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    private TelemetryFetch goi() {
        return adapter(true).goi(new TelemetryCall(diaChi(), MA_SO, Duration.ofSeconds(5)));
    }

    // ==== Đường đi thành công =================================================

    @Test
    @DisplayName("⭐⭐ Gọi thật qua dây: GET đúng đường dẫn, bóc ra ĐÚNG 28 số đo của bản mẫu đo thật")
    void goiThatVaBocDuocBanMau() {
        TelemetryFetch fetch = goi();

        assertThat(daNhan).hasSize(1);
        assertThat(daNhan.get(0).method()).isEqualTo("GET");
        assertThat(daNhan.get(0).path()).isEqualTo("/api/getmn.aspx");
        assertThat(fetch.thanhCong()).isTrue();
        assertThat(fetch.httpStatus()).isEqualTo(200);
        assertThat(adapter(true).boc(fetch.body()).soDo()).hasSize(28);
    }

    @Test
    @DisplayName("⭐⭐ Dấu ';' cuối mã số ĐI NGUYÊN VĂN lên dây — thiếu nó nguồn trả not.working")
    void dauChamPhayCuoiMaSoDiNguyenVan() {
        goi();

        assertThat(daNhan.get(0).rawQuery())
                .as("⚠ URLEncoder của Java mã hoá ';' thành %3B. Thứ ĐÃ ĐO LÀ CHẠY ĐƯỢC là dấu ';' "
                        + "nguyên văn, và ⛔ không có lượt gọi thật nào để kiểm lại giả thiết 'nguồn "
                        + "cũng chấp nhận %3B'. Thiếu dấu ấy nguồn trả not.working — TRÔNG Y HỆT mã số sai.")
                .isEqualTo("key=" + MA_SO)
                .endsWith(";")
                .doesNotContain("%3B")
                .doesNotContain("%3b");
    }

    @Test
    @DisplayName("⭐ Mã số có ký tự nguy hiểm ('&', '#', khoảng trắng) bị mã hoá — ⛔ không tự chèn tham số")
    void maSoCoKyTuNguyHiemThiBiMaHoa() {
        String hiem = "abc&admin=1#x y;";

        adapter(true).goi(new TelemetryCall(diaChi(), hiem, Duration.ofSeconds(5)));

        String query = daNhan.get(0).rawQuery();
        assertThat(query)
                .as("một mã số là dữ liệu do NGƯỜI nhập, dù người ấy là quản trị viên")
                .contains("%26")
                .contains("%23")
                .doesNotContain("admin=1")
                .endsWith(";");
    }

    // ==== HTTP/1.1 — ĐO, không khẳng định =====================================

    @Test
    @DisplayName("⭐⭐ KHÔNG xin nâng cấp HTTP/2 — đo bằng header Upgrade/HTTP2-Settings, ⛔ không hỏi getProtocol()")
    void khongXinNangCapHttp2() {
        goi();

        // ⚠⚠ §10.36: bản đầu của một bài kiểm cùng loại khẳng định `exchange.getProtocol()` là
        //    "HTTP/1.1" — và nó XANH cả khi đã gỡ `.version(HTTP_1_1)`, vì com.sun.net.httpserver chỉ
        //    nói HTTP/1.1 nên client tự hạ cấp. Giao thức QUAN SÁT ĐƯỢC giống hệt nhau ở cả hai cấu
        //    hình; một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì (luật 9).
        //
        //    Thứ THẬT SỰ khác:  HTTP_2 → upgrade=true, http2-settings=true
        //                       HTTP_1_1 → cả hai vắng mặt
        //    Và đúng hai header đó là thứ IIS 8.5 / ASP.NET WebForms có thể không xử lý.
        assertThat(daNhan.get(0).moiHeader())
                .as("⛔ Gỡ .version(HTTP_1_1) khỏi Bhh40Adapter là bài kiểm này phải ĐỎ")
                .doesNotContain("upgrade")
                .doesNotContain("http2-settings");
    }

    // ==== Che mã số ===========================================================

    @Test
    @DisplayName("⭐⭐ Mã số bị CHE khỏi thân — nguồn trả chính credential về trong thẻ <form action>")
    void maSoBiCheKhoiThan() {
        TelemetryFetch fetch = goi();

        assertThat(new String(banMau(), StandardCharsets.UTF_8))
                .as("⚠ Vế PHÂN BIỆT: bản mẫu ĐO THẬT phải chứa mã số ở cả hai dạng, nếu không thì "
                        + "khẳng định 'thân đã sạch' bên dưới xanh vì không có gì để che (luật 7)")
                .contains("maso-kiem-thu-0123456789%3b");

        assertThat(fetch.body())
                .as("⛔ hydro_raw_logs nằm trong MỌI bản sao lưu và songnhue_readonly đọc được — "
                        + "conventions.md §4.7 cấm credential ở cả ba chỗ ấy")
                .doesNotContain("maso-kiem-thu-0123456789")
                .contains(TelemetryFetch.DAU_CHE_MA_SO);
    }

    @Test
    @DisplayName("⭐⭐ Che đủ BỐN dạng mã hoá — dạng hex CHỮ THƯỜNG của ASP.NET là dạng thật sự gặp")
    void cheDuBonDangMaHoa() {
        String maSo = "Ma-So-Hoa+Thuong;";

        // ⚠ Bốn chuỗi dưới đây viết TAY, ⛔ không gọi lại hàm của Bhh40Adapter để dựng ra. Luật 29:
        //   một bài kiểm chứng ngược có thể sai theo đúng cách mà thứ nó kiểm đang sai, vì người viết
        //   cả hai mang cùng một giả định. Dạng thứ ba là bằng chứng ĐO ĐƯỢC: response thật mang
        //   `key=…%3b` — .NET (HttpUtility.UrlEncode) sinh hex CHỮ THƯỜNG, Java sinh hex CHỮ HOA.
        String than = String.join(
                " | ",
                "Ma-So-Hoa+Thuong;", // nguyên văn
                "Ma-So-Hoa%2BThuong%3B", // URLEncoder của Java — hex HOA
                "Ma-So-Hoa%2bThuong%3b", // ASP.NET — hex thường, dạng đo được
                "Ma-So-Hoa%2BThuong;"); // thứ ta gửi đi: mã hoá nhưng giữ ';'

        String sach = Bhh40Adapter.cheMaSo(than, maSo);

        assertThat(sach)
                .as("⛔ Không được hạ chữ thường CẢ chuỗi đã mã hoá: mã số có chữ hoa thì hạ hết đi là "
                        + "chuỗi so sánh không còn khớp gì, và bộ che trượt trong im lặng. Chỉ hạ hai "
                        + "chữ số hex của mỗi escape %XX.")
                .doesNotContain("Ma-So-Hoa");
        assertThat(sach.split(java.util.regex.Pattern.quote(TelemetryFetch.DAU_CHE_MA_SO), -1))
                .as("⚠ Khẳng định về SỐ LƯỢNG (luật 29): bốn dạng ⇒ bốn lần thay ⇒ năm mảnh. Nó không "
                        + "chia sẻ giả định nào với danh sách chuỗi ở trên — che trượt một dạng thì "
                        + "doesNotContain vẫn có thể xanh nếu dạng ấy tình cờ khác chữ.")
                .hasSize(5);
    }

    @Test
    @DisplayName("⚠ Phạm vi bộ che (luật 28): hex TRỘN hoa-thường KHÔNG được che — và không encoder nào sinh ra nó")
    void hexTronHoaThuongNamNgoaiPhamVi() {
        String maSo = "A+B;";

        String sach = Bhh40Adapter.cheMaSo("A%2BB%3b", maSo);

        // ⚠ Ghi giới hạn vào chính bộ canh, ⛔ không để cái xanh của nó đọc như một lời bảo đảm rộng
        //   hơn nó. Java sinh %2B%3B (hoa cả hai), .NET sinh %2b%3b (thường cả hai); chuỗi trộn dưới
        //   đây không đến từ đâu cả. Nếu một ngày nó xuất hiện thật thì bài kiểm này là chỗ đổi.
        assertThat(sach)
                .as("bộ che phủ hai dạng thuần (hoa / thường), ⛔ không phủ dạng trộn")
                .isEqualTo("A%2BB%3b");
    }

    // ==== Năm tình trạng phân biệt được =======================================

    @Test
    @DisplayName("⭐⭐ not.working ⇒ NOT_WORKING, và lý do NÓI TỚI dấu ';' — hai nguyên nhân không phân biệt được")
    void nguonTraNotWorking() {
        thanTraVe.set("not.working".getBytes(StandardCharsets.UTF_8));

        TelemetryFetch fetch = goi();

        assertThat(fetch.failureKind()).isEqualTo(SyncFailureKind.NOT_WORKING);
        assertThat(fetch.failureDetail())
                .as("mã số sai và mã số thiếu ';' cho ra CÙNG một chuỗi — người trực lúc 2 giờ sáng "
                        + "phải đọc thấy câu hỏi đúng ngay dòng đầu")
                .contains("';'");
        assertThat(fetch.body())
                .as("⚠ VẪN giữ thân: nguồn không có API lịch sử để hỏi lại vì sao nó từ chối")
                .isEqualTo("not.working");
    }

    @Test
    @DisplayName("HTTP 500 ⇒ HTTP_ERROR, vẫn giữ thân — trang lỗi của IIS là thứ duy nhất nói vì sao")
    void nguonTraLoiMayChu() {
        maTraVe.set(500);
        thanTraVe.set("<html>Server Error in '/' Application.</html>".getBytes(StandardCharsets.UTF_8));

        TelemetryFetch fetch = goi();

        assertThat(fetch.failureKind()).isEqualTo(SyncFailureKind.HTTP_ERROR);
        assertThat(fetch.httpStatus()).isEqualTo(500);
        assertThat(fetch.body()).contains("Server Error");
    }

    @Test
    @DisplayName("⭐ HTTP 200 + thân RỖNG ⇒ EMPTY_BODY — ⛔ không đọc thành 'thành công, 0 số đo'")
    void thanRongLaMotTinhTrangRieng() {
        thanTraVe.set("   \n  ".getBytes(StandardCharsets.UTF_8));

        TelemetryFetch fetch = goi();

        assertThat(fetch.failureKind())
                .as("⚠ 'nguồn đang bảo trì' và 'khung này chưa trạm nào lên' cho ra cùng con số 0 số "
                        + "đo, nhưng đòi hai cách xử lý ngược nhau (§10.68-B)")
                .isEqualTo(SyncFailureKind.EMPTY_BODY);
    }

    @Test
    @DisplayName("⭐ Timeout ⇒ TIMEOUT (⛔ không phải HTTP_ERROR) và httpStatus null — nguồn treo ≠ mất mạng")
    void timeoutLaMotNhanhRieng() {
        treGiay.set(3);

        TelemetryFetch fetch = adapter(true).goi(new TelemetryCall(diaChi(), MA_SO, Duration.ofMillis(300)));

        assertThat(fetch.failureKind()).isEqualTo(SyncFailureKind.TIMEOUT);
        assertThat(fetch.httpStatus())
                .as("chưa nhận được phản hồi nào thì mã trạng thái phải là null, ⛔ không phải 0 — "
                        + "0 là một con số và nó sẽ được vẽ lên biểu đồ")
                .isNull();
        assertThat(fetch.durationMs()).isPositive();
    }

    @Test
    @DisplayName("⭐ Thân vượt trần 4 MB ⇒ HTTP_ERROR, thân bị BỎ — một node, ngân sách RAM tính chặt")
    void thanVuotTranBiChan() {
        byte[] khonglo = new byte[Bhh40Adapter.TRAN_BYTE_THAN + 10];
        java.util.Arrays.fill(khonglo, (byte) 'x');
        thanTraVe.set(khonglo);

        TelemetryFetch fetch = goi();

        assertThat(fetch.failureKind()).isEqualTo(SyncFailureKind.HTTP_ERROR);
        assertThat(fetch.failureDetail()).contains("vượt trần");
        assertThat(fetch.body())
                .as("⛔ Giữ 4 MB rác trong hydro_raw_logs mỗi 2 phút là 2,8 GB/ngày")
                .isNull();
    }

    // ==== SSRF ================================================================

    @Test
    @DisplayName("⭐⭐ Công tắc TẮT (mặc định): chính máy chủ 127.0.0.1 này bị TỪ CHỐI — bộ chặn SSRF chạy thật")
    void mayNoiBoBiChanKhiCongTacTat() {
        String dich = diaChi();

        assertThatThrownBy(() -> adapter(false).goi(new TelemetryCall(dich, MA_SO, Duration.ofSeconds(5))))
                .as("⚠ Đây là vế chứng minh công tắc app.hydro.api.allow-internal-host KHÔNG phải một "
                        + "cửa hậu bỏ ngỏ: cùng một địa chỉ, cùng một adapter, chỉ khác cờ — và nhánh "
                        + "mặc định chặn. Không có bài này thì mọi bài trên đây chạy ở chế độ nới và "
                        + "không ai biết chế độ chặt có hoạt động không (luật 10).")
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nội bộ");

        assertThat(daNhan)
                .as("⛔ Chặn phải xảy ra TRƯỚC khi mở socket — chặn sau khi đã gọi là không chặn gì")
                .isEmpty();
    }

    private static byte[] banMau() {
        try (InputStream in = Bhh40AdapterHttpTest.class.getResourceAsStream("/bhh40/response-mau.txt")) {
            if (in == null) {
                throw new AssertionError("thiếu bản mẫu đo thật /bhh40/response-mau.txt");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
