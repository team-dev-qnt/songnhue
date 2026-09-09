package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.core.common.exception.UpstreamException;
import com.songnhue.core.spi.JobContext;
import com.songnhue.hydro.application.ApiSourceService;
import com.songnhue.hydro.application.HydroJobTypes;
import com.songnhue.hydro.application.HydroPollJobHandler;

/**
 * ⭐⭐ <b>Chặng cuối của poller</b>: byte thật của nguồn → điểm đo THẬT → {@code hydro_readings} và
 * {@code hydro_latest} trên Postgres thật.
 *
 * <h2>⛔ Khoảng trống mà lớp này lấp — đo 09/09/2026</h2>
 *
 * <p>Đường thuỷ văn có <b>12 lớp kiểm</b>, và <b>10 trong số đó là JUnit trần</b>. Hai lớp chạy trên
 * CSDL thật thì:
 *
 * <ul>
 *   <li>{@code HydroPortalCacheSplitTest} — đệm cổng, ⛔ không đụng đường ingest;
 *   <li>{@code TelemetryProbeHttpTest} — <b>có</b> một lượt polling khép trọn vòng qua hàng đợi thật,
 *       nhưng {@code MockAdapter} trả mã {@code Z9000x} mà chính javadoc của nó khai là <i>"về nguyên
 *       tắc ⛔ không thể tra ra điểm đo nào"</i>. Bài ấy vì thế khẳng định
 *       <b>{@code written_count = 0}</b>.
 * </ul>
 *
 * <p>⇒ Tính tới hôm nay, <b>⛔ chưa lượt kiểm nào chứng minh một số đo rơi đúng lên một trong 19
 * điểm đo</b>. Bốn thứ ở chặng cuối vì thế chưa ai đi qua (luật 7):
 *
 * <ol>
 *   <li>tra {@code api_code} → {@code station_id} <b>trên dữ liệu seed thật</b>;
 *   <li>⭐ quy đổi <b>cm → m</b> (nguồn trả {@code value=154} nghĩa là 1,54 m);
 *   <li>ghi {@code hydro_latest} — bảng mà mọi màn hình realtime đọc;
 *   <li>mã lạ ⛔ <b>không</b> bị vứt: nguồn ⛔ không có API lịch sử (quy tắc 18).
 * </ol>
 *
 * <h2>⚠ Vì sao phải dựng một máy chủ HTTP thật thay vì mock adapter</h2>
 *
 * <p>{@code MockAdapter} cố ý ⛔ không trả mã {@code F#####} — đó là một bảo đảm an toàn, ⛔ không
 * phải một thiếu sót, nên ⛔ <b>không được sửa nó cho hợp bài kiểm</b>. Đường duy nhất còn lại là
 * {@code Bhh40Adapter} thật, gọi vào một {@code com.sun.net.httpserver.HttpServer} của JDK — cùng
 * khuôn {@code Bhh40AdapterHttpTest} đã dùng, ⛔ không thêm phụ thuộc nào.
 *
 * <p>⚠⚠ Và vì thế lớp này phải bật {@code app.hydro.api.allow-internal-host=true}: bộ chặn SSRF
 * ({@code DiaChiNguon.kiemHost}) từ chối {@code 127.0.0.1} <b>theo mặc định</b>, và nó đúng —
 * {@code Bhh40AdapterHttpTest} có hẳn một bài chứng minh công tắc ấy ⛔ không phải hình thức. Bật ở
 * đây là một quyết định <b>có phạm vi một lớp kiểm</b>, ⛔ không phải nới bộ chặn.
 *
 * <h2>⛔ Thân phản hồi dùng MÃ SỐ GIẢ</h2>
 *
 * <p>Thân thật của nguồn kết thúc bằng {@code <form action="./getmn.aspx?key=<mã số>%3b">} — tức
 * <b>chính credential</b> (quy tắc 13, {@code conventions.md} §4.7). Fixture ở đây dựng lại đúng
 * hình dạng ấy nhưng với mã giả, y như {@code bhh40/response-mau.txt} đang làm.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = "app.hydro.api.allow-internal-host=true")
class PollerChangCuoiHttpTest extends IntegrationTestBase {

    /** ⛔ Mã GIẢ. Mã thật ⛔ không bao giờ vào kho — xem khối chú thích ở đầu lớp. */
    private static final String MA_SO_GIA = "maso-kiem-thu-chang-cuoi;";

    private static final String PAYLOAD_POLL = "{\"maNguon\":\"%s\"}";

    /**
     * ⭐ Ba mã <b>có thật</b> trong seed 19 điểm đo + hai mã <b>lạ</b> nguồn cũng trả về.
     *
     * <p>Đo trên nguồn thật 09/09/2026: {@code getmn.aspx} trả <b>28 mã</b> trong khi danh mục của hệ
     * có <b>19</b> ⇒ <b>9 mã lạ ở mọi lượt poll</b>. Trộn cả hai loại vào một mẻ là đúng hình dạng
     * production, và nó bắt được lỗi mà một mẻ toàn mã đúng ⛔ không thấy: một cài đặt <i>"gặp mã lạ
     * thì bỏ cả mẻ"</i> vẫn xanh nếu bài kiểm chỉ gửi mã đúng.
     */
    private static final String F_LIEN_MAC_TL = "F01771";

    private static final String F_HA_DONG_TL = "F01794";
    private static final String F_BA_THA_MN = "F01532";
    private static final String MA_LA_1 = "F01696";
    private static final String MA_LA_2 = "F01830";

    private HttpServer server;
    private final AtomicInteger soLuotGoi = new AtomicInteger();
    private final AtomicReference<String> than = new AtomicReference<>("");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ApiSourceService sources;

    @Autowired
    private HydroPollJobHandler pollHandler;

    private UUID nguon;

    @BeforeAll
    void dungNguonGia() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/getmn.aspx", this::traLoi);
        server.start();

        nguon = jdbc.queryForObject(
                """
                INSERT INTO api_sources (code, name, adapter_type, base_url, status, created_at)
                VALUES ('PCC-BHH40', 'Nguồn kiểm thử chặng cuối', 'BHH40', ?, 'HOAT_DONG', now())
                RETURNING public_id
                """,
                UUID.class,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/");
        sources.datMaSo(nguon, MA_SO_GIA);
    }

    /**
     * ⚠⚠ Dọn <b>số đo</b>, ⛔ KHÔNG dọn nguồn và ⛔ KHÔNG dọn raw log — và cả hai đều có lý do.
     *
     * <ul>
     *   <li>{@code hydro_raw_logs} là <b>append-only</b>, ép ở tầng CSDL ({@code V202609041059:509}
     *       ghi thẳng: <i>"append-only chỉ còn trên giấy"</i> nếu xoá được). Một lượt {@code DELETE}
     *       ở đây ⛔ không phải dọn dẹp, nó là phá một bảo đảm.
     *   <li>⇒ ⛔ Không xoá được {@code api_sources} nữa: raw log giữ khoá ngoại. Thay bằng
     *       {@code TAM_DUNG} để bộ lập lịch bỏ qua nó ở mọi lớp kiểm chạy sau.
     *   <li>{@code hydro_readings} thì <b>xoá được</b> ({@code :162} khai rõ nó ⛔ không append-only)
     *       — và phải xoá: lớp này ghi lên <b>điểm đo seed thật</b>, đúng thứ §11.19 vừa trả giá.
     * </ul>
     */
    @AfterAll
    void dongNguonGia() {
        if (server != null) {
            server.stop(0);
        }
        jdbc.update(
                """
                DELETE FROM hydro_latest WHERE station_id IN
                  (SELECT id FROM stations WHERE api_code IN (?, ?, ?))
                """,
                F_LIEN_MAC_TL,
                F_HA_DONG_TL,
                F_BA_THA_MN);
        jdbc.update(
                """
                DELETE FROM hydro_readings WHERE station_id IN
                  (SELECT id FROM stations WHERE api_code IN (?, ?, ?))
                """,
                F_LIEN_MAC_TL,
                F_HA_DONG_TL,
                F_BA_THA_MN);
        jdbc.update("UPDATE api_sources SET status = 'TAM_DUNG' WHERE code = 'PCC-BHH40'");
    }

    private void traLoi(HttpExchange ex) throws IOException {
        soLuotGoi.incrementAndGet();
        byte[] b = than.get().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(b);
        }
    }

    /**
     * Dựng thân y hệt nguồn thật: các dòng {@code <br>} rồi <b>trang ASP.NET rỗng nối ở đuôi</b>.
     *
     * <p>⚠ Đuôi HTML ⛔ không phải trang trí — {@code Bhh40Parser.catPhanHtml} phải cắt nó, và nếu
     * ⛔ không cắt thì dòng đầu của trang thành một dòng rác ở <b>mọi</b> lượt gọi thành công.
     */
    private static String thanNguon(String ngay, String gio, Map<String, Integer> giaTri) {
        StringBuilder sb = new StringBuilder();
        giaTri.forEach((ma, v) -> sb.append(ma)
                .append(';')
                .append(ngay)
                .append(';')
                .append(gio)
                .append(";value=")
                .append(v)
                .append(";<br>"));
        sb.append("\r\n\r\n<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\">\n")
                .append("<html><body><form method=\"post\" action=\"./getmn.aspx?key=")
                .append("maso-kiem-thu-chang-cuoi%3b")
                .append("\" id=\"form1\"></form></body></html>");
        return sb.toString();
    }

    private void chayMotLuotPoll() {
        pollHandler.handle(new JobContext(
                UUID.randomUUID(), HydroJobTypes.POLL, PAYLOAD_POLL.formatted("PCC-BHH40"), null, p -> {}, c -> {}));
    }

    private Map<String, Object> syncLogMoiNhat() {
        return jdbc.queryForMap(
                """
                SELECT s.status, s.received_count, s.written_count, s.unmapped_count
                  FROM sync_logs s JOIN api_sources a ON a.id = s.api_source_id
                 WHERE a.code = 'PCC-BHH40' ORDER BY s.id DESC LIMIT 1
                """);
    }

    /**
     * ⭐⭐ Bài chịu lực: một lượt poll ghi số đo lên <b>đúng điểm đo</b>, và giá trị <b>đã quy đổi</b>.
     *
     * <p>Nguồn trả centimet ({@code value=154}); CSDL lưu mét ({@code 1.540}). Phép chia 100 nằm ở
     * adapter và tới hôm nay ⛔ <b>chưa lượt kiểm nào trên CSDL thật</b> đi qua nó — một lỗi hệ số
     * 100 ở đây cho ra mực nước 154 m, và bảng vẫn vẽ được.
     */
    @Test
    @DisplayName("⭐⭐ Poll → số đo rơi ĐÚNG điểm đo, quy đổi cm→m, và hydro_latest được cập nhật")
    void motLuotPollGhiSoDoLenDiemDoThat() {
        String ngay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        than.set(thanNguon(
                ngay,
                "10:20",
                Map.of(F_LIEN_MAC_TL, 154, F_HA_DONG_TL, 271, F_BA_THA_MN, 95, MA_LA_1, 240, MA_LA_2, 198)));

        chayMotLuotPoll();

        Map<String, Object> log = syncLogMoiNhat();
        assertThat(log.get("status")).as("nhật ký đồng bộ: %s", log).isEqualTo("SUCCESS");
        assertThat(log.get("received_count")).isEqualTo(5);
        assertThat(log.get("written_count"))
                .as("⭐⭐ 3 mã tra RA điểm đo thật — đây chính là con số mà mọi lớp kiểm trước đó "
                        + "khẳng định bằng 0, vì fixture của chúng dùng mã về nguyên tắc không tra ra gì")
                .isEqualTo(3);
        assertThat(log.get("unmapped_count"))
                .as("⛔ 2 mã lạ phải được ĐẾM, ⛔ không bị vứt im lặng — nguồn ⛔ không có API lịch sử "
                        + "nên một dòng bỏ đi là bỏ vĩnh viễn (quy tắc 18)")
                .isEqualTo(2);

        BigDecimal giaTri = jdbc.queryForObject(
                """
                SELECT l.valid_value FROM hydro_latest l
                  JOIN stations s ON s.id = l.station_id
                 WHERE s.api_code = ?
                """,
                BigDecimal.class,
                F_LIEN_MAC_TL);
        assertThat(giaTri)
                .as("⭐ Nguồn trả 154 (cm) ⇒ CSDL phải lưu 1.540 (m). Một lỗi hệ số 100 cho ra mực "
                        + "nước 154 m và bảng vẫn vẽ được — ⛔ không gì báo")
                .isEqualByComparingTo("1.540");

        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM hydro_readings r JOIN stations s ON s.id = r.station_id
                         WHERE s.api_code IN (?, ?, ?)
                        """,
                        Integer.class,
                        F_LIEN_MAC_TL,
                        F_HA_DONG_TL,
                        F_BA_THA_MN))
                .as("bảng gốc phải có đủ ba dòng — `hydro_latest` là bảng dẫn xuất, ⛔ không thay nó được")
                .isEqualTo(3);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM stations WHERE api_code IN (?, ?)", Integer.class, MA_LA_1, MA_LA_2))
                .as("⛔⛔ Quy tắc parse 5: ⛔ KHÔNG tự tạo điểm đo từ mã lạ, kể cả trên đường tự động")
                .isZero();
    }

    /**
     * ⛔ Lượt poll thứ hai với <b>cùng mốc</b> ⛔ không được ghi thêm dòng nào.
     *
     * <p>Nguồn đẩy dữ liệu rải rác trong cửa sổ bảy phút, nên hai lượt gọi liền nhau thấy <i>cùng</i>
     * một khung là chuyện thường ngày, ⛔ không phải ngoại lệ. Thiếu vế này thì bảng gốc phình theo
     * nhịp poll chứ ⛔ không theo nhịp dữ liệu, và mọi phép tổng hợp đếm trùng.
     */
    @Test
    @DisplayName("⛔ Poll lại CÙNG mốc ⇒ 0 dòng mới — chống trùng ở tầng CSDL, ⛔ không ở tầng nhớ")
    void pollLaiCungMocKhongGhiThem() {
        String ngay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        than.set(thanNguon(ngay, "11:40", Map.of(F_LIEN_MAC_TL, 161)));

        chayMotLuotPoll();
        Integer sauLuotDau = jdbc.queryForObject(
                "SELECT count(*) FROM hydro_readings r JOIN stations s ON s.id = r.station_id WHERE s.api_code = ?",
                Integer.class,
                F_LIEN_MAC_TL);
        assertThat(sauLuotDau)
                .as("⚠ Chống tập rỗng: lượt đầu phải ghi được gì đó, nếu không thì vế 'không ghi "
                        + "thêm' ở dưới xanh mà ⛔ không chứng minh gì (luật 7)")
                .isGreaterThan(0);

        chayMotLuotPoll();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hydro_readings r JOIN stations s ON s.id = r.station_id "
                                + "WHERE s.api_code = ?",
                        Integer.class,
                        F_LIEN_MAC_TL))
                .as("⛔ Cùng (điểm đo × loại chỉ số × mốc đo) ⇒ chỉ một dòng")
                .isEqualTo(sauLuotDau);

        assertThat(syncLogMoiNhat().get("written_count"))
                .as("⭐ Và nhật ký phải NÓI RA là 0 dòng mới — một lượt poll ghi 0 dòng vì trùng khác "
                        + "hẳn một lượt poll ghi 0 dòng vì nguồn im lặng (luật 9)")
                .isEqualTo(0);
    }

    /**
     * ⛔⛔ Mã số sai ⇒ nguồn trả {@code not.working} kèm <b>HTTP 200</b>, ⛔ không phải 401.
     *
     * <p>Đo trên nguồn thật 09/09/2026. Đây là hình dạng nguy hiểm nhất của cả đường thuỷ văn: một hệ
     * ⛔ không phân biệt được <i>"khoá hết hạn"</i> với <i>"hôm nay nguồn ⛔ không có số"</i> sẽ im
     * lặng mất dữ liệu — và quy tắc 18 nói mất là <b>vĩnh viễn</b>.
     */
    @Test
    @DisplayName("⛔⛔ Nguồn trả `not.working` kèm HTTP 200 ⇒ NÉM SYS-0006, 0 dòng ghi, nhật ký nói HỎNG")
    void nguonBaoHongKhongGhiDongNao() {
        Integer truoc = jdbc.queryForObject("SELECT count(*) FROM hydro_readings", Integer.class);
        than.set("not.working<!DOCTYPE html><html><body></body></html>");

        // ⭐ Handler NÉM `UpstreamException` (SYS-0006) sau khi đã ghi nhật ký — cố ý khác nhánh
        //   "chưa có mã số" (nhánh ấy ⛔ không ném, vì 720 job FAILED mỗi ngày là một màn hình ⛔
        //   không ai đọc). Ở đây nguồn hỏng THẬT nên một job đỏ là đúng: nó đáng được thử lại.
        assertThatThrownBy(this::chayMotLuotPoll)
                .as("⛔ Nuốt lỗi ở đây là biến 'khoá hết hạn' thành một lượt poll xanh ⛔ không dữ liệu")
                .isInstanceOf(UpstreamException.class);

        Map<String, Object> log = syncLogMoiNhat();
        assertThat(log.get("status"))
                .as("⛔ `SUCCESS` ở đây nghĩa là hệ đọc 'khoá hỏng' thành 'hôm nay không có số': %s", log)
                .isNotEqualTo("SUCCESS");
        assertThat(log.get("written_count")).isEqualTo(0);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM hydro_readings", Integer.class))
                .as("⛔ Quy tắc parse 2: nguồn báo hỏng thì ⛔ KHÔNG một bản ghi nào được ghi")
                .isEqualTo(truoc);
    }

    /**
     * ⚠ Vế chống tập rỗng của cả lớp: máy chủ giả phải THẬT SỰ được gọi.
     *
     * <p>Nếu bộ chặn SSRF từ chối {@code 127.0.0.1} (công tắc ⛔ không bật) thì mọi bài trên vẫn có
     * thể xanh vì lý do sai — {@code sync_logs} ghi một nhánh thoát khác, và ⛔ không byte nào rời
     * máy. Đếm số lượt gọi là cách duy nhất phân biệt hai trạng thái ấy.
     */
    @Test
    @DisplayName("⚠ Máy chủ giả THẬT SỰ nhận được yêu cầu — nếu không, cả lớp xanh vì lý do sai")
    void mayChuGiaThatSuDuocGoi() {
        int truoc = soLuotGoi.get();
        than.set(thanNguon(
                LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), "12:00", Map.of(F_HA_DONG_TL, 100)));

        chayMotLuotPoll();

        assertThat(soLuotGoi.get())
                .as("⛔ 0 lượt gọi nghĩa là adapter chưa từng mở kết nối — mọi khẳng định khác của "
                        + "lớp này khi ấy nói về một đường chạy KHÁC")
                .isEqualTo(truoc + 1);
    }
}
