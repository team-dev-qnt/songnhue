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

    /** Khoá `settings` CHUNG — giá trị dùng khi nguồn ⛔ không đặt riêng `api_sources.max_retry`. */
    private static final String KHOA_THU_LAI = "hydro.polling.max-retry";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.songnhue.core.application.settings.SettingService settings;

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

    /**
     * Số lượt gọi <b>đầu tiên</b> còn phải trả HTTP 503 trước khi máy chủ giả chịu trả 200 — T43.12.
     *
     * <p>⚠ 503 chứ ⛔ không phải 404: {@code TelemetryFetch.dangThuLaiDuoc()} cố ý ⛔ <b>không</b>
     * thử lại 4xx (một mã số sai ⛔ không tự đúng lên). Dùng 404 ở đây thì bài kiểm sẽ đo một nhánh
     * khác nhánh nó nói.
     */
    private final AtomicInteger conHong503 = new AtomicInteger();

    private void traLoi(HttpExchange ex) throws IOException {
        soLuotGoi.incrementAndGet();
        if (conHong503.get() > 0) {
            conHong503.decrementAndGet();
            ex.sendResponseHeaders(503, -1);
            ex.close();
            return;
        }
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
    /**
     * ⛔⛔⛔ <b>T43.10</b> — mã BÁO LỖI của thiết bị ⛔ không được đi vào bảng chính như một mực nước.
     *
     * <h2>Vì sao khuyết tật này sống được lâu</h2>
     *
     * <p>Vỏ bọc chất lượng khai khoảng vật lý {@code [-10; 30]} m, và một chú thích migration
     * ({@code V202609041061:233}) khẳng định <i>"sentinel âm của thiết bị đo (-999 / -9999) rơi
     * dưới -10"</i>. Đo lại: {@code -9999 cm} → {@code -99,99 m} <b>rơi ra ngoài</b> ✓, nhưng
     * {@code -999 cm} → <b>{@code -9,99 m}</b>, <b>nằm TRONG khoảng</b> ⇒ ra {@code HOP_LE}. Và bài
     * kiểm duy nhất canh mục ấy chỉ thử {@code -9999} — nó xanh trong <i>đúng</i> tình huống nó sinh
     * ra để bắt (luật 7 · luật 9).
     *
     * <h2>⭐ Ba khẳng định, và cái thứ ba mới là cái khó</h2>
     *
     * <p>Cách "sửa" rẻ nhất — <b>từ chối mọi giá trị âm</b> — làm hai khẳng định đầu xanh và <b>sai
     * nghiệp vụ</b>: mực nước âm là chuyện <i>bình thường</i> ở hệ thống này (cao độ tham chiếu),
     * và {@code PhanLoaiChatLuong} đã ghi sẵn hai cặp TL &lt; HL hợp lệ đo được từ số liệu thật.
     * Nên bài này gửi kèm một giá trị âm <b>thật</b> ({@code -50 cm}) và đòi nó ở lại {@code HOP_LE}.
     * Đó là vế phân biệt <i>"chặn sentinel"</i> với <i>"chặn số âm"</i> — hai cài đặt mà hai khẳng
     * định đầu ⛔ không tách được.
     *
     * <p>⚠ Và bản ghi sentinel vẫn <b>được GHI</b> (quy tắc 18: ⛔ không có API lịch sử, mất là mất
     * vĩnh viễn) — nó chỉ mang cờ {@code NGHI_NGO}, thứ quy tắc 14 tự động giữ ra khỏi mọi báo cáo,
     * dashboard và phép so ngưỡng. Vứt ở tầng parse thì <b>trạm hỏng trông y hệt trạm im lặng</b>.
     */
    @Test
    @DisplayName("⭐⭐ T43.10 — `-999` vào bảng dưới cờ NGHI_NGO, còn `-50` là mực nước THẬT và ở lại HOP_LE")
    void maBaoLoiCuaThietBiKhongThanhMotMucNuoc() {
        String ngay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        than.set(thanNguon(ngay, "09:10", Map.of(F_LIEN_MAC_TL, -999, F_HA_DONG_TL, -50, F_BA_THA_MN, 154)));

        chayMotLuotPoll();

        assertThat(syncLogMoiNhat().get("written_count"))
                .as("⛔ Quy tắc 18 — cả ba dòng phải được GHI. Vứt dòng sentinel là xoá dấu vết một "
                        + "cảm biến đang hỏng, và ⛔ không có API lịch sử để lấy lại")
                .isEqualTo(3);

        Map<String, Object> sentinel = doc(F_LIEN_MAC_TL, "09:10");
        assertThat(sentinel.get("quality"))
                .as("⛔⛔ `-999 cm` = `-9,99 m` NẰM TRONG [-10; 30] ⇒ vỏ bọc khoảng vật lý ⛔ không "
                        + "bắt được. Đây là dòng khẳng định phân biệt bản vá với bản cũ")
                .isEqualTo("NGHI_NGO");
        assertThat((String) sentinel.get("quality_reason"))
                .as("⛔ Người duyệt phải đọc được NGUYÊN VĂN nguồn trả về, ⛔ không phải giá trị đã "
                        + "quy đổi — `-9,99` trông như một mực nước, `-999 cm` thì ⛔ không")
                .contains("-999");
        assertThat((BigDecimal) sentinel.get("reading_value"))
                .as("⛔ Giá trị giữ NGUYÊN, ⛔ không nắn về 0 và ⛔ không NULL: bản ghi là bằng chứng "
                        + "cảm biến hỏng, sửa nó đi là xoá bằng chứng")
                .isEqualByComparingTo("-9.990");

        Map<String, Object> amThat = doc(F_HA_DONG_TL, "09:10");
        assertThat(amThat.get("quality"))
                .as(
                        """
                        ⛔⛔⛔ VẾ CHỊU LỰC. Cách sửa rẻ nhất — "từ chối mọi giá trị âm" — làm mọi khẳng \
                        định phía trên xanh và SAI NGHIỆP VỤ: mực nước âm là bình thường ở hệ này. Một \
                        bản vá chặn cả `-50` sẽ đánh nghi ngờ đúng những bản ghi mà người trực cần nhất, \
                        rồi quy tắc 14 loại chúng khỏi báo cáo — im lặng.""")
                .isEqualTo("HOP_LE");
        assertThat((BigDecimal) amThat.get("reading_value")).isEqualByComparingTo("-0.500");

        assertThat(jdbc.queryForObject(
                        """
                        SELECT l.valid_value FROM hydro_latest l JOIN stations s ON s.id = l.station_id
                         WHERE s.api_code = ?
                        """,
                        BigDecimal.class,
                        F_LIEN_MAC_TL))
                .as("⛔ `hydro_latest.valid_value` là thứ TRANG CHỦ đọc — một sentinel lọt vào đây là "
                        + "cổng công khai đăng `-9,99 m` như mực nước hiện tại của cống Liên Mạc")
                .isNotEqualByComparingTo(new BigDecimal("-9.990"));
    }

    /**
     * Một dòng {@code hydro_readings} của một mã API tại một mốc <b>giờ Việt Nam</b> hôm nay.
     *
     * <p>⚠ So bằng {@code to_char(… AT TIME ZONE 'Asia/Ho_Chi_Minh')} chứ ⛔ không dựng lại mốc
     * tuyệt đối trong bài kiểm: {@code Bhh40Parser} đọc {@code dd/MM/uuuu HH:mm} của nguồn theo
     * {@code DateTimeUtils.ZONE_VN} rồi đổi sang {@code Instant}. Chép lại phép đổi ấy ở đây là
     * dựng một bản sao thứ hai của cùng một luật — và ngày nó lệch, bài kiểm sẽ <b>đồng ý</b> với
     * bản sao của chính nó chứ ⛔ không với mã thật (luật 14).
     */
    private Map<String, Object> doc(String maApi, String gioVn) {
        return jdbc.queryForMap(
                """
                SELECT r.reading_value, r.quality, r.quality_reason
                  FROM hydro_readings r JOIN stations s ON s.id = r.station_id
                 WHERE s.api_code = ?
                   AND to_char(r.measured_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI') = ?
                   AND r.measured_at > now() - interval '1 day'
                """,
                maApi,
                gioVn);
    }

    /**
     * ⛔⛔⛔ <b>T43.12</b> — {@code max_retry} là một núm <b>ĐIỀU KHIỂN ĐƯỢC</b>, ⛔ không phải một ô
     * nhập trang trí.
     *
     * <h2>Nó đã ⛔ không điều khiển gì suốt từ WS-31 (luật 15)</h2>
     *
     * <p>{@code hydro.polling.max-retry} có seed (mặc định 3), có cột riêng từng nguồn với CHECK
     * {@code 0..10}, có ô nhập trên màn hình <i>Nguồn dữ liệu</i>, có cả <b>giá trị đã giải</b> hiện
     * kèm cờ "dùng chung" — và <b>0 nơi đọc</b>. Người vận hành chỉnh nó và ⛔ không gì đổi.
     *
     * <h2>⭐ Hai trạng thái, ⛔ không phải một — nếu không thì bài kiểm ⛔ không khẳng định gì</h2>
     *
     * <p>Một bài chỉ đo <i>"có thử lại"</i> sẽ xanh y hệt với một cài đặt <b>ghi cứng 3 lần</b>,
     * tức đúng lỗi mà nó sinh ra để bắt (luật 9). Nên bài này chạy <b>hai lượt cùng một kịch bản
     * hỏng</b> và chỉ đổi <b>một</b> thứ — giá trị trong CSDL:
     *
     * <table border="1">
     *   <caption>Cùng 1 lượt 503, khác nhau đúng ở núm</caption>
     *   <tr><th>{@code max_retry}</th><th>số lượt gọi HTTP</th><th>kết quả</th></tr>
     *   <tr><td>{@code 2}</td><td><b>2</b> (1 hỏng + 1 lại)</td><td>SUCCESS, số đo vào bảng</td></tr>
     *   <tr><td>{@code 0}</td><td><b>1</b></td><td>hỏng, ⛔ không dòng nào</td></tr>
     * </table>
     *
     * <p>⇒ Cột <i>số lượt gọi</i> chỉ có thể khác nhau nếu <b>giá trị đã giải</b> thật sự đi vào
     * vòng lặp (luật 3).
     */
    @Test
    @DisplayName("⭐⭐ T43.12 — `max_retry`=2 cứu được một cú chớp mạng; `max_retry`=0 thì ⛔ không")
    void soLanThuLaiThatSuDieuKhienVongGoi() {
        String ngay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        // ⛔⛔ T47.12 — TRƯỚC bản này, hai dòng khôi phục nằm ở mã THƯỜNG sau SÁU khẳng định.
        //    Một khẳng định đỏ ⇒ `max_retry` kẹt ở 2 hoặc 0 và `conHong503` kẹt khác 0, rò sang
        //    MỌI lớp chạy sau — và surefire xếp lớp theo hệ tệp (macOS ngược Linux), nên hậu quả
        //    là một lượt CI đỏ ở một bài vô can mà ở máy ⛔ không tái lập được (§11.19).
        try {
            than.set(thanNguon(ngay, "03:30", Map.of(F_BA_THA_MN, 210)));

            // ── Lượt A: cho phép thử lại 2 lần, nguồn hỏng đúng MỘT lượt đầu ────────────────────────
            jdbc.update("UPDATE api_sources SET max_retry = 2 WHERE code = 'PCC-BHH40'");
            conHong503.set(1);
            int truocA = soLuotGoi.get();

            chayMotLuotPoll();

            assertThat(soLuotGoi.get() - truocA)
                    .as("⛔ 1 lượt gọi nghĩa là ⛔ không hề thử lại — đúng trạng thái trước bản vá")
                    .isEqualTo(2);
            assertThat(syncLogMoiNhat().get("status"))
                    .as("⭐ Quy tắc 18: nguồn ⛔ không có API lịch sử, nên một cú chớp mạng ⛔ không cứu "
                            + "được là mất VĨNH VIỄN một khung 10 phút của cả 19 trạm")
                    .isEqualTo("SUCCESS");
            assertThat(doc(F_BA_THA_MN, "03:30").get("reading_value")).isNotNull();

            // ── Lượt B: CÙNG kịch bản hỏng, chỉ đổi ĐÚNG một thứ — núm về 0 ─────────────────────────
            jdbc.update("UPDATE api_sources SET max_retry = 0 WHERE code = 'PCC-BHH40'");
            than.set(thanNguon(ngay, "03:40", Map.of(F_BA_THA_MN, 211)));
            conHong503.set(1);
            int truocB = soLuotGoi.get();

            // ⭐ Ném SYS-0006 là ĐÚNG hợp đồng của `HydroPollJobHandler`: lượt gọi ĐÃ xảy ra rồi hỏng ⇒
            //   job phải đỏ. Và chính nó là vế đối xứng của lượt A — cùng một kịch bản 503, một bên
            //   SUCCESS, một bên ném. Nuốt ngoại lệ ở đây là bỏ mất nửa phép so.
            assertThatThrownBy(this::chayMotLuotPoll)
                    .as("⛔ ⛔ không thử lại ⇒ lượt việc phải ĐỎ, ⛔ không được im lặng trôi qua")
                    .isInstanceOf(UpstreamException.class);

            assertThat(soLuotGoi.get() - truocB)
                    .as("⛔⛔ VẾ PHÂN BIỆT: với một cài đặt GHI CỨNG số lần thử lại, con số này vẫn là 2 "
                            + "và khẳng định phía trên vẫn xanh. Chỉ dòng này nói được rằng núm ĐANG điều khiển")
                    .isEqualTo(1);
            assertThat(jdbc.queryForObject(
                            """
                            SELECT count(*) FROM hydro_readings r JOIN stations s ON s.id = r.station_id
                             WHERE s.api_code = ?
                               AND to_char(r.measured_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI') = '03:40'
                            """,
                            Integer.class,
                            F_BA_THA_MN))
                    .as("núm về 0 ⇒ lượt hỏng dừng lại ở đó, ⛔ không dòng nào vào bảng")
                    .isZero();

        } finally {
            jdbc.update("UPDATE api_sources SET max_retry = NULL WHERE code = 'PCC-BHH40'");
            conHong503.set(0);
        }
    }

    @Test
    @DisplayName("⭐⭐ T47.12 — khoá `settings` CHUNG điều khiển được nguồn ⛔ KHÔNG đặt riêng `max_retry`")
    void khoaSettingsChungDieuKhienNguonKhongDatRieng() {
        // ⛔⛔ VẾ CÒN THIẾU CỦA LUẬT 3. T43.12 chứng minh CỘT RIÊNG (`api_sources.max_retry`) điều
        //    khiển được. Nhưng `ApiSourceService.thamSoHieuLuc()` giải: cột NULL ⇒ lấy khoá
        //    `settings` chung. Nhánh ấy ⛔ CHƯA có bài kiểm nào — 3 bài đi qua nó đều **mock**
        //    `HydroSettings`, nên ⛔ không lượt nào chạm bảng `settings` thật.
        //
        // ⛔⛔ VÌ SAO CHỌN 1 VÀ 5, ⛔ KHÔNG CHỌN 2 HAY 3 — và vì sao `conHong503 = 4`:
        //    Giá trị dự phòng ghi trong Java (`HydroSettings:156`, `getInt(KHOA, 3)`) TRÙNG KHÍT
        //    giá trị seed (`V202608131009:65` = '3'). Mô phỏng vòng lặp
        //    (`soLuot = 1 + max(0, soLanThuLai)`, dừng khi thành công):
        //
        //      conHong503 = 4 →  settings=1 : 2 lượt gọi, NÉM
        //                        settings=5 : 5 lượt gọi, SUCCESS
        //                        GHI CỨNG 3 : 4 lượt gọi, NÉM      ← khác CẢ HAI ⇒ phân biệt được
        //
        //      conHong503 = 1 →  settings=1 : 2 lượt, SUCCESS
        //                        GHI CỨNG 3 : 2 lượt, SUCCESS      ← TRÙNG NHAU ⇒ ⛔ không khẳng định gì
        //
        //    Cặp thứ hai là cặp "tự nhiên" nhất khi viết vội, và nó cho một bài kiểm xanh vĩnh viễn
        //    kể cả khi đường dây đứt hẳn (luật 9).
        String cu = jdbc.queryForObject(
                "SELECT coalesce(setting_value, default_value) FROM settings WHERE setting_key = ?",
                String.class,
                KHOA_THU_LAI);

        // ⚠ VẾ CHỐNG TẬP RỖNG (luật 7 · §11.19): cả bài này nói về nhánh `max_retry IS NULL`. Nếu
        //   một lớp chạy trước để lại một giá trị ở cột ấy thì bài đo nhánh CỘT RIÊNG — tức đo lại
        //   đúng thứ T43.12 đã đo, và xanh vì lý do sai.
        assertThat(jdbc.queryForObject("SELECT max_retry FROM api_sources WHERE code = 'PCC-BHH40'", Integer.class))
                .as("⛔ Cột riêng PHẢI đang NULL — nếu ⛔ không, bài này đo nhầm nhánh")
                .isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM api_sources WHERE code = 'PCC-BHH40'", Integer.class))
                .as("⛔ 0 hàng nghĩa là `@BeforeAll` đã hỏng, ⛔ KHÔNG phải `max_retry` đúng")
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM settings WHERE setting_key = ? AND editable",
                        Integer.class,
                        KHOA_THU_LAI))
                .as("⛔ 0 nghĩa là `update()` sẽ ném SYS-0004/ADM-2007, và thông điệp ấy ⛔ không nói "
                        + "gì về T47.12 — người đọc CI sẽ đi tìm nhầm chỗ")
                .isEqualTo(1);

        // ⛔⛔ GHIM GIÁ TRỊ DỰ PHÒNG BẰNG SỐ ĐO, ⛔ không bằng hằng chép tay. Cả bài đứng trên tiền
        //    đề "1 và 5 đều KHÁC giá trị dự phòng"; ngày ai đó đổi seed cho trùng một vế, bài phải
        //    đỏ NGAY chứ ⛔ không lặng lẽ mất khả năng phân biệt (luật 3).
        String duPhong = jdbc.queryForObject(
                "SELECT default_value FROM settings WHERE setting_key = ?", String.class, KHOA_THU_LAI);
        assertThat(duPhong)
                .as("`HydroSettings` ghi literal 3; dòng này ghim seed khớp nó")
                .isEqualTo("3");
        assertThat(java.util.List.of("1", "5"))
                .as("⛔ ⛔ Không vế nào được TRÙNG giá trị dự phòng — trùng là bài mất vế phân biệt")
                .doesNotContain(duPhong);

        String ngay = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        try {
            // ── Lượt A: khoá chung = 1, nguồn hỏng 4 lượt ⇒ hết lượt thử trước khi nguồn tỉnh ───
            settings.update(KHOA_THU_LAI, "1");
            than.set(thanNguon(ngay, "05:10", Map.of(F_BA_THA_MN, 220)));
            conHong503.set(4);
            int truocA = soLuotGoi.get();

            assertThatThrownBy(this::chayMotLuotPoll).isInstanceOf(UpstreamException.class);

            assertThat(soLuotGoi.get() - truocA)
                    .as("⛔⛔ 4 lượt nghĩa là hệ đang chạy theo giá trị GHI CỨNG 3, ⛔ không đọc `settings`")
                    .isEqualTo(2);
            assertThat(jdbc.queryForObject(
                            """
                            SELECT count(*) FROM hydro_readings r JOIN stations s ON s.id = r.station_id
                             WHERE s.api_code = ?
                               AND to_char(r.measured_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'HH24:MI') = '05:10'
                            """,
                            Integer.class,
                            F_BA_THA_MN))
                    .as("⛔ Hết lượt thử ⇒ ⛔ không dòng nào vào bảng. Đối xứng với vế B — thiếu nó thì "
                            + "bài chỉ đếm lượt gọi mà ⛔ không nói gì về KẾT CỤC dữ liệu")
                    .isZero();

            // ── Lượt B: CÙNG kịch bản hỏng, đổi ĐÚNG một thứ — khoá chung lên 5 ─────────────────
            conHong503.set(0);
            settings.update(KHOA_THU_LAI, "5");
            than.set(thanNguon(ngay, "05:20", Map.of(F_BA_THA_MN, 221)));
            conHong503.set(4);
            int truocB = soLuotGoi.get();

            chayMotLuotPoll();

            assertThat(soLuotGoi.get() - truocB)
                    .as("⛔⛔ VẾ PHÂN BIỆT: với cài đặt ghi cứng 3 thì con số này là 4 và lượt poll NÉM. "
                            + "Chỉ dòng này nói được rằng khoá `settings` CHUNG đang điều khiển thật")
                    .isEqualTo(5);
            assertThat(syncLogMoiNhat().get("status")).isEqualTo("SUCCESS");
            assertThat(doc(F_BA_THA_MN, "05:20").get("reading_value")).isNotNull();
        } finally {
            // ⛔ BẮT BUỘC. `SettingService` giữ đệm Caffeine TOÀN TIẾN TRÌNH, nên một giá trị bỏ
            //    quên ở đây rò sang MỌI lớp chạy sau — và surefire xếp lớp theo hệ tệp (macOS ngược
            //    Linux), nên hậu quả sẽ là một lượt CI đỏ mà ở máy ⛔ không tái lập được (§11.19).
            conHong503.set(0);
            settings.update(KHOA_THU_LAI, cu);
        }
    }

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
