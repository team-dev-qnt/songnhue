package com.songnhue.hydro.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

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
        // ⚠ `resolve` chứ không phải nối chuỗi: nối chuỗi thì `baseUrl` thiếu/thừa dấu `/` cho ra hai
        //   URL khác nhau, và một trong hai trả 404 mà không ai đoán được vì sao.
        return goc.resolve(duongDan);
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
