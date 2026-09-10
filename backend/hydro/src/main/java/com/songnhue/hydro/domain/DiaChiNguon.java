package com.songnhue.hydro.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Địa chỉ nguồn có được phép mở kết nối tới không — <b>SSRF, T30.12</b>
 * ({@code conventions.md} §4.6 A10).
 *
 * <h2>⚠ Vì sao phép kiểm này nằm ở tầng domain chứ không chỉ ở màn hình</h2>
 *
 * <p>{@code ApiSourceService.diaChi()} đã kiểm tiền tố {@code http://}/{@code https://} <b>lúc
 * ghi</b>. Nhưng lúc ghi không phải chỗ dữ liệu <i>đi qua</i> (luật 12): giá trị trong
 * {@code api_sources.base_url} còn có ba đường vào khác mà màn hình không đứng chắn —
 * <b>seed migration</b>, <b>bản khôi phục từ sao lưu</b> và <b>một câu {@code UPDATE} tay lúc xử lý
 * sự cố</b>. Cả ba đều hợp pháp và cả ba đều bỏ qua validator của service.
 *
 * <p>⇒ Bảo đảm đặt ở chỗ cuối cùng trước khi mở socket, tức trong adapter. Kiểm hai lần là cố ý:
 * phía màn hình cho người dùng một lỗi đọc được, phía adapter chặn cả những đường chưa ai nghĩ tới.
 *
 * <h2>Cái gì bị chặn và vì sao</h2>
 *
 * <ul>
 *   <li><b>Scheme ngoài http/https</b> — {@code file:}, {@code gopher:}, {@code jar:} là ba đường
 *       kinh điển để một "URL cấu hình được" thành một lượt đọc tệp trên máy chủ.
 *   <li><b>Có {@code userinfo}</b> ({@code http://ai-do@host/}) — dạng này để đánh lừa người đọc về
 *       host thật, và nó không có công dụng hợp lệ nào ở đây.
 *   <li><b>Host là địa chỉ vòng lặp / mạng nội bộ / link-local / metadata đám mây</b> — một nguồn
 *       trỏ {@code 169.254.169.254} biến poller thành công cụ đọc thông tin đăng nhập của máy ảo.
 *       ⚠ Chặn theo <b>chữ viết</b>, ⛔ không phân giải DNS: phân giải rồi kiểm là mở ra
 *       <i>DNS rebinding</i> — tên trả IP công cộng lúc kiểm và IP nội bộ lúc gọi.
 * </ul>
 *
 * <p>⛔ Đây <b>không</b> phải một bộ lọc SSRF đầy đủ, và bộ canh phải nói ra phạm vi của chính nó
 * (luật 28): một tên miền công cộng <i>trỏ vào</i> mạng nội bộ vẫn đi lọt. Vế còn lại thuộc về tầng
 * mạng (máy chủ ứng dụng không có đường ra tới dải nội bộ nào ngoài CSDL và MinIO —
 * {@code deploy-guideline.md}); ghi ở đây để lượt rà sau không đọc cái xanh này thành một lời bảo đảm
 * rộng hơn nó.
 *
 * <h2>⚠⚠ {@code chapNhanMayNoiBo} — công tắc duy nhất nới được, và nó mặc định TẮT</h2>
 *
 * <p>Bài kiểm dựng máy chủ HTTP thật (T30.9) bind vào {@code 127.0.0.1}, và một nguồn giả chạy trên
 * máy lập trình viên cũng vậy. Không có đường nới thì hoặc bài kiểm phải mock đúng chỗ mã chạm ra
 * ngoài (luật 4 — thứ đã làm {@code pg_dump} chưa từng chạy suốt 4 ngày), hoặc phép kiểm SSRF phải
 * bị gỡ. Cả hai đều tệ hơn một công tắc tường minh.
 *
 * <p>⇒ Công tắc là một <b>tham số của hàm</b>, ⛔ không phải một trường tĩnh: mỗi lời gọi phải nói
 * ra nó đang ở chế độ nào, nên {@code grep} tìm được đủ các chỗ nới. Giá trị chạy thật đến từ
 * {@code app.hydro.api.allow-internal-host} — mặc định {@code false}, và có bài kiểm khẳng định
 * không tệp {@code deploy/env/*} nào của staging/prod đặt nó.
 */
public final class DiaChiNguon {

    private static final Logger log = LoggerFactory.getLogger(DiaChiNguon.class);

    private static final Set<String> SCHEME_CHO_PHEP = Set.of("http", "https");

    /**
     * Tên máy bị chặn theo chữ viết.
     *
     * <p>{@code 169.254.169.254} là endpoint metadata của gần như mọi nhà cung cấp đám mây;
     * {@code metadata.google.internal} là bí danh DNS của cùng thứ đó.
     */
    private static final Set<String> HOST_CAM = Set.of("localhost", "metadata.google.internal");

    private DiaChiNguon() {}

    /**
     * @throws IllegalArgumentException khi địa chỉ không dùng được — thông báo nói rõ vi phạm nào,
     *     vì người đọc nó là quản trị viên đang tự hỏi vì sao nguồn không gọi được
     */
    public static URI kiemVaDung(String baseUrl, String duongDan, boolean chapNhanMayNoiBo) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ nguồn rỗng");
        }
        URI goc;
        try {
            goc = new URI(baseUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Địa chỉ nguồn không phải URI hợp lệ: " + baseUrl, e);
        }
        String scheme = goc.getScheme() == null ? "" : goc.getScheme().toLowerCase(Locale.ROOT);
        if (!SCHEME_CHO_PHEP.contains(scheme)) {
            throw new IllegalArgumentException("Chỉ chấp nhận http/https, nhận scheme '" + scheme + "' — " + baseUrl);
        }
        if (goc.getUserInfo() != null) {
            throw new IllegalArgumentException("Địa chỉ nguồn không được mang userinfo (phần trước @)");
        }
        String host = goc.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Địa chỉ nguồn không có tên máy: " + baseUrl);
        }
        if (!chapNhanMayNoiBo) {
            kiemHost(host);
        }
        return goc.resolve(chuanHoaGoc(goc, duongDan)).resolve(duongDan);
    }

    /**
     * Đưa đường dẫn của {@code goc} về dạng <b>thư mục</b>, sau khi cắt phần <i>đã trùng</i> với đầu
     * {@code duongDan} — <b>T52.1</b>.
     *
     * <h2>⛔⛔ Sự cố thật: 3576 lượt hỏng liên tiếp, {@code last_success_at} CHƯA BAO GIỜ</h2>
     *
     * <p>Đo trên staging 10/09/2026:
     *
     * <pre>
     *   base_url             = http://songnhue.bhh40.net/api/getmn.aspx
     *   credential           = ĐÃ ĐẶT, đúng chỗ, mã hoá đúng (v1:…)
     *   consecutive_failures = 3576
     *   last_success_at      = NULL          ← ⛔ chưa một lượt nào thành công, BAO GIỜ
     *   last_failure_reason  = "Nguồn trả HTTP 404"
     *   hydro_readings       = 0 · hydro_latest = 0
     * </pre>
     *
     * <p>Người vận hành đã làm <b>đúng</b> phần khó: gỡ {@code ?key=…} khỏi ô <i>Địa chỉ gốc</i> và
     * đặt mã số qua hộp thoại <i>Mã số truy cập</i> (bản vá T50.1). Thứ ở lại là <b>đường dẫn</b>
     * {@code /api/getmn.aspx} — và {@code URI.resolve} <b>thay đoạn cuối</b> của đường dẫn gốc, nên
     * {@code /api/getmn.aspx} + {@code api/getmn.aspx} cho ra {@code /api/api/getmn.aspx}. Đo trên
     * nguồn thật cùng ngày: đường ấy trả <b>404</b>, còn đường đúng trả 200.
     *
     * <h2>⚠⚠ Vế IM LẶNG còn nặng hơn vế 404</h2>
     *
     * <p>{@code URI.resolve} bỏ đoạn cuối của base khi base ⛔ không kết thúc bằng {@code /}:
     *
     * <pre>
     *   http://host/songnhue   + api/getmn.aspx  ->  http://host/api/getmn.aspx      ⛔ MẤT "/songnhue"
     *   http://host/songnhue/  + api/getmn.aspx  ->  http://host/songnhue/api/…      ✅
     * </pre>
     *
     * <p>Vế trên ⛔ không sinh 404 — nó gọi <b>một đường dẫn có thật trên một máy chủ có thật</b> và
     * trả 200. Ngày Công ty dời nguồn xuống một thư mục con, poller sẽ đọc nhầm nguồn mà ⛔ không
     * một dòng lỗi nào. Chú thích cũ ở ngay dòng {@code resolve} bênh vực {@code resolve} <i>vì</i>
     * vấn đề dấu {@code /} — mà {@code resolve} có đúng vấn đề dấu {@code /} của riêng nó.
     *
     * <h2>Luật: cắt phần TRÙNG theo ĐOẠN, ⛔ không so chuỗi</h2>
     *
     * <p>Gọi {@code base} = các đoạn của đường dẫn gốc, {@code rel} = các đoạn của {@code duongDan}.
     * Tìm {@code k} lớn nhất sao cho <b>k đoạn cuối của {@code base}</b> trùng <b>k đoạn đầu của
     * {@code rel}</b>, rồi bỏ {@code k} đoạn ấy khỏi {@code base}. Phần còn lại luôn kết thúc bằng
     * {@code /} nên {@code resolve} chỉ còn việc nối.
     *
     * <pre>
     *   /api/getmn.aspx   k=2  ->  /            ->  /api/getmn.aspx            (staging, ĐÃ VÁ)
     *   /api              k=1  ->  /            ->  /api/getmn.aspx
     *   /songnhue         k=0  ->  /songnhue/   ->  /songnhue/api/getmn.aspx   (⛔ không còn mất)
     *   /songnhue/api     k=1  ->  /songnhue/   ->  /songnhue/api/getmn.aspx
     *   (rỗng)            k=0  ->  /            ->  /api/getmn.aspx            (giá trị seed)
     * </pre>
     *
     * <p>⛔ So theo <b>đoạn</b> chứ ⛔ không {@code endsWith} trên chuỗi: {@code /xxxapi} kết thúc
     * bằng {@code api} theo chuỗi nhưng ⛔ không phải theo đoạn, và cắt nó đi là hỏng một địa chỉ
     * đang chạy được (luật 2 — canh cấu trúc, ⛔ không canh văn bản).
     *
     * <p>⚠ Hàm này <b>⛔ không</b> ném khi phải cắt. Quy tắc 18 của dự án: nguồn ⛔ không có API
     * lịch sử ⇒ <b>mỗi 2 phút poller ⛔ không chạy là số đo mất vĩnh viễn</b>. Từ chối một địa chỉ
     * mà ý định của nó ⛔ không hề mơ hồ là đổi một lỗi cấu hình sửa được lấy một khoảng trống dữ
     * liệu ⛔ không lấy lại được. Thay vào đó nó ghi một dòng {@code WARN} nêu cả hai giá trị.
     */
    static String chuanHoaGoc(URI goc, String duongDan) {
        String rawPath = goc.getRawPath() == null ? "" : goc.getRawPath();
        int hoi = duongDan.indexOf('?');
        String phanDuongDan = hoi < 0 ? duongDan : duongDan.substring(0, hoi);

        List<String> base = doanCua(rawPath);
        List<String> rel = doanCua(phanDuongDan);

        int k = Math.min(base.size(), rel.size());
        while (k > 0 && !base.subList(base.size() - k, base.size()).equals(rel.subList(0, k))) {
            k--;
        }
        if (k > 0 && !String.valueOf(goc).equals(DA_CANH_BAO.getAndSet(String.valueOf(goc)))) {
            log.warn(
                    "Địa chỉ nguồn '{}' đã mang sẵn {} đoạn đầu của đường dẫn endpoint '{}' — đã cắt phần "
                            + "trùng nên lượt gọi vẫn ĐÚNG. Nên sửa ô 'Địa chỉ gốc' về phần gốc của máy "
                            + "chủ để giá trị hiển thị thôi gây hiểu nhầm.",
                    goc,
                    k,
                    phanDuongDan);
        }
        String giu = base.subList(0, base.size() - k).stream().collect(Collectors.joining("/"));
        return giu.isEmpty() ? "/" : "/" + giu + "/";
    }

    /**
     * Địa chỉ gần nhất đã cảnh báo — <b>chống một cái chuông kêu 720 lần mỗi ngày</b>.
     *
     * <p>Poller gọi <b>2 phút/lần</b>. Một dòng {@code WARN} mỗi lượt là <b>720 dòng/ngày</b> cho
     * một tình trạng mà bản vá đã làm cho <b>vô hại</b> — và §10.42 ghi lại đúng cái giá của việc
     * ấy: một chuông kêu liên tục là một chuông <b>sẽ bị tắt</b>, rồi lần sau nó kêu thật thì ⛔
     * không ai nghe. Ở đây mỗi giá trị chỉ kêu <b>một lần cho mỗi lượt chạy</b> của tiến trình.
     *
     * <p>⚠ Cố ý giữ <b>giá trị gần nhất</b> chứ ⛔ không phải một tập tích luỹ: một tập là một chỗ
     * rò bộ nhớ chờ ngày ai đó cho phép cấu hình nhiều nguồn. Kho hôm nay có <b>một</b> nguồn
     * {@code BHH40}, nên "gần nhất" và "đã từng" là cùng một thứ; ngày có nguồn thứ hai thì hậu quả
     * xấu nhất là kêu lại — ⛔ không phải im.
     */
    private static final AtomicReference<String> DA_CANH_BAO = new AtomicReference<>();

    /** Các đoạn ⛔ rỗng của một đường dẫn. {@code ""} và {@code "/"} đều cho danh sách rỗng. */
    private static List<String> doanCua(String path) {
        return Arrays.stream(path.split("/")).filter(d -> !d.isEmpty()).toList();
    }

    private static void kiemHost(String host) {
        String h = host.toLowerCase(Locale.ROOT).replaceAll("^\\[|]$", "");
        if (HOST_CAM.contains(h)) {
            throw new IllegalArgumentException("Tên máy nội bộ bị chặn: " + host);
        }
        if (h.endsWith(".localhost") || h.endsWith(".internal") || h.endsWith(".local")) {
            throw new IllegalArgumentException("Tên máy nội bộ bị chặn: " + host);
        }
        if ("::1".equals(h) || h.startsWith("fe80:") || h.startsWith("fc") || h.startsWith("fd")) {
            throw new IllegalArgumentException("Địa chỉ IPv6 nội bộ bị chặn: " + host);
        }
        if (!h.matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
            return;
        }
        String[] o = h.split("\\.");
        int a = Integer.parseInt(o[0]);
        int b = Integer.parseInt(o[1]);
        boolean noiBo = a == 127 // vòng lặp
                || a == 10 // RFC 1918
                || a == 0 // "máy này"
                || (a == 192 && b == 168) // RFC 1918
                || (a == 172 && b >= 16 && b <= 31) // RFC 1918
                || (a == 169 && b == 254) // link-local + metadata đám mây
                || (a == 100 && b >= 64 && b <= 127); // CGNAT, RFC 6598
        if (noiBo) {
            throw new IllegalArgumentException("Địa chỉ IP nội bộ bị chặn: " + host);
        }
    }
}
