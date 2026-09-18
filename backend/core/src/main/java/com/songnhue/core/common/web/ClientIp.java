package com.songnhue.core.common.web;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestWrapper;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.util.StringUtils;

/**
 * Địa chỉ IP của máy khách — <b>một nơi duy nhất</b> trả lời câu hỏi ấy cho cả hệ.
 *
 * <h2>⛔⛔ Vì sao lớp này ra đời — T43.8-b, 10/09/2026</h2>
 *
 * <p>Trước bản này có <b>ba</b> bản sao y hệt của cùng một hàm — {@code RateLimitFilter},
 * {@code AuditContextFilter}, {@code ClientInfo} — và cả ba lấy <b>phần tử ĐẦU</b> của
 * {@code X-Forwarded-For}. Hai trong ba mang javadoc khẳng định điều kiện khiến việc ấy an toàn:
 *
 * <blockquote>"nginx phải <b>ghi đè</b> header này chứ không nối thêm; nếu không thì kẻ tấn công đổi
 * header là thoát rate limit"</blockquote>
 *
 * <p>Điều kiện ấy <b>chưa bao giờ đúng</b>. Đo 10/09/2026 trên cấu hình thật:
 * {@code deploy/nginx/snippets/proxy-common.conf:16} và {@code deploy/docker/admin-app.Dockerfile}
 * đều đặt {@code X-Forwarded-For $proxy_add_x_forwarded_for} — <b>NỐI THÊM</b>. Và
 * {@code grep -rn "real_ip_header\\|set_real_ip_from" deploy/} = <b>0</b>: ⛔ không có module
 * {@code real_ip} nào chữa lại.
 *
 * <p>⇒ Client gửi {@code X-Forwarded-For: 1.2.3.4} thì app nhận
 * {@code 1.2.3.4, <ip thật>, <ip biên>} và <b>lấy 1.2.3.4</b>. Hệ quả đo được:
 *
 * <ul>
 *   <li><b>Cả bốn</b> hạn mức vô hiệu bằng cách đổi một header — LOGIN 30/15', API 100/1',
 *       PUBLIC 300/1', EXPORT 10/1h. Đổi giá trị mỗi lượt gọi là đổi khoá mỗi lượt gọi.
 *   <li>Nặng hơn: {@code ClientInfo} ghi giá trị này vào <b>nhật ký bảo mật</b> và danh sách phiên
 *       ("đăng xuất từ xa") ⇒ kẻ gọi <b>ghi được một IP bịa vào nhật ký</b>. Một dấu vết kiểm toán
 *       nhận dữ liệu do đối tượng bị kiểm toán cung cấp thì ⛔ không còn là dấu vết.
 * </ul>
 *
 * <p>⚠ Và <b>ba</b> chú thích cùng khẳng định nó đã an toàn — bản thứ ba ở
 * {@code PhienHttp:68} ("<i>ở production nginx ghi đè header này, nên ⛔ không có đường nào để
 * client thật tự cấp cho mình một IP</i>"), trong khi {@code proxy-common.conf:13-15} <b>mô tả
 * đúng</b> hành vi nối-thêm và coi việc lấy phần tử đầu là hợp lệ. Hai tệp trong kho khẳng định
 * ngược nhau về cùng một bất biến, và ⛔ không cổng kiểm nào so hai vế (luật 14).
 *
 * <h2>⭐ Neo mới: {@code X-Real-IP}, và vì sao nó tin được</h2>
 *
 * <p>{@code X-Real-IP} do nginx <b>biên</b> đặt bằng {@code $remote_addr}
 * ({@code proxy-common.conf:6}) — một phép <b>GHI ĐÈ</b>, nên giá trị client tự gửi bị vứt ngay ở
 * chặng đầu. Đó chính là điều kiện mà javadoc cũ tưởng {@code X-Forwarded-For} đang thoả.
 *
 * <p>Đo cả hai đường tới app:
 *
 * <ul>
 *   <li><b>Quản trị</b>: biên → nginx trong image {@code admin-app} → app. Chặng trong nay
 *       <b>chuyển tiếp</b> {@code $http_x_real_ip} thay vì ghi đè bằng {@code $remote_addr} (ip của
 *       chính chặng biên) — xem {@code admin-app.Dockerfile}.
 *   <li><b>Cổng công khai</b>: biên → {@code public-web} → Route Handler
 *       {@code app/api/v1/[...path]/route.ts} → app. Handler chép <b>mọi</b> header trừ danh sách
 *       hop-by-hop, và {@code x-real-ip} ⛔ không nằm trong danh sách ấy ⇒ đi xuyên nguyên vẹn.
 * </ul>
 *
 * <h2>Phạm vi — luật 28</h2>
 *
 * <p>Bảo đảm này đứng <b>khi và chỉ khi</b> mọi lượt gọi đi qua nginx biên. Một tiến trình đã ở
 * <b>bên trong</b> mạng docker vẫn tự đặt được {@code X-Real-IP}; đó là ngưỡng cao hơn hẳn "một
 * header trên Internet", và nó đúng y như vậy với mọi phương án khác. ⛔ Lớp này ⛔ không thay được
 * việc phân đoạn mạng.
 *
 * <p>⛔ {@code X-Forwarded-For} <b>KHÔNG</b> còn được đọc ở bất cứ đâu để định danh máy khách. Nó
 * vẫn được nginx gửi (và vẫn hữu ích khi đọc log để thấy cả chuỗi chặng), nhưng nó ⛔ không quyết
 * định điều gì.
 */
public final class ClientIp {

    /** Header do nginx biên GHI ĐÈ — xem javadoc lớp. */
    public static final String HEADER = "X-Real-IP";

    private ClientIp() {}

    /**
     * IP máy khách dùng cho hạn mức và cho nhật ký bảo mật.
     *
     * @return giá trị {@code X-Real-IP} nếu có; ngược lại địa chỉ của <b>chặng nối trực tiếp</b>
     *     ({@code getRemoteAddr()}). ⛔ Bản dự phòng ⛔ không bao giờ là một header do kẻ gọi đặt —
     *     thà gộp mọi máy khách sau một proxy chưa cấu hình vào một xô còn hơn để mỗi lượt gọi tự
     *     chọn xô của nó (một hạn mức né được là một hạn mức ⛔ không tồn tại).
     */
    public static String cua(HttpServletRequest request) {
        String thuc = request.getHeader(HEADER);
        if (StringUtils.hasText(thuc)) {
            return thuc.trim();
        }
        return diaChiChangNoi(request);
    }

    /**
     * Địa chỉ của <b>chặng nối trực tiếp</b> — bóc hết lớp bọc trước khi hỏi.
     *
     * <h2>⛔⛔ Vì sao ⛔ KHÔNG gọi thẳng {@code request.getRemoteAddr()}</h2>
     *
     * <p>{@code application.yml:196} đặt {@code server.forward-headers-strategy: framework}, nên
     * {@code ForwardedHeaderFilter} của Spring chạy <b>trước</b> mọi filter của hệ: nó <b>nuốt</b>
     * header {@code X-Forwarded-For} và <b>ghi đè</b> {@code getRemoteAddr()} bằng phần tử ĐẦU của
     * chính chuỗi ấy.
     *
     * <p>⇒ {@code getRemoteAddr()} trên request đã bọc <b>cũng do kẻ gọi điều khiển</b>. Đo
     * 10/09/2026 qua HTTP thật: hai lượt gọi chỉ khác nhau ở {@code X-Forwarded-For}, ⛔ không gửi
     * {@code X-Real-IP} ⇒ {@code X-RateLimit-Remaining} = <b>299 và 299</b>, tức <b>hai xô riêng</b>.
     * Một bản dự phòng "an toàn" mà kẻ gọi chọn được thì ⛔ không phải bản dự phòng.
     *
     * <p>⭐ Chiến lược {@code framework} chỉ <b>bọc</b> request (khác {@code native}, thứ sửa thẳng
     * ở Tomcat qua {@code RemoteIpValve}). Bóc hết {@link ServletRequestWrapper} là chạm tới request
     * gốc, nơi {@code getRemoteAddr()} vẫn là địa chỉ socket thật — <b>⛔ không giả mạo được</b>.
     *
     * <p>⚠ Nếu ai đó đổi {@code forward-headers-strategy} sang {@code native}, phép bóc này ⛔
     * không còn đủ: {@code RemoteIpValve} sửa ở tầng Tomcat, dưới cả request gốc. Bộ canh cho điều
     * ấy nằm ở {@code FrontendSameOriginTest} (luật 14 — hai nơi con người phải nhớ).
     */
    private static String diaChiChangNoi(HttpServletRequest request) {
        ServletRequest goc = request;
        while (goc instanceof ServletRequestWrapper boc) {
            goc = boc.getRequest();
        }
        return goc.getRemoteAddr();
    }
}
