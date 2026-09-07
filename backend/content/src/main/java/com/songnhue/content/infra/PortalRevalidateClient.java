package com.songnhue.content.infra;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.songnhue.core.common.config.PortalProperties;

/**
 * Gọi {@code POST /api/revalidate} của cổng công khai — WS-16/T16.5.
 *
 * <p>Đây là lần đầu cơ chế dựng ở WS-9 có người đi qua. Trước đó endpoint tồn tại, có kiểm bí mật
 * bằng so sánh thời-gian-không-đổi, và <b>chưa ai gọi nó lần nào</b>.
 *
 * <h2>Vì sao dùng {@code HttpClient} của JDK</h2>
 *
 * Một lời gọi POST với hai header thì không đáng kéo thêm một thư viện HTTP vào cây phụ thuộc —
 * mỗi thư viện là một dòng nữa phải theo dõi CVE hằng đêm.
 *
 * <h2>Vì sao lớp này KHÔNG bắt lỗi</h2>
 *
 * Nó được gọi từ một {@code JobHandler}, mà hợp đồng của handler là <b>ném ngoại lệ = thất bại</b>.
 * Nuốt lỗi ở đây thì hàng đợi ghi SUCCEEDED cho một lượt gọi chưa tới nơi, và trang công khai đứng
 * yên với nội dung cũ mà không ai biết — đúng loại hỏng im lặng đắt nhất của dự án này.
 */
@Component
public class PortalRevalidateClient {

    private static final Logger log = LoggerFactory.getLogger(PortalRevalidateClient.class);

    private static final String PATH = "/api/revalidate";
    private static final String SECRET_HEADER = "x-revalidate-secret";

    private final PortalProperties portal;
    private final HttpClient client;

    public PortalRevalidateClient(PortalProperties portal) {
        this.portal = portal;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(portal.getTimeoutSeconds()))
                // ⚠⚠ ÉP HTTP/1.1. `HttpClient` của JDK mặc định là HTTP/2, và với `http://` nó gửi
                // kèm `Connection: Upgrade, HTTP2-Settings` để thử nâng cấp. Máy chủ Node của Next
                // đóng kết nối thay vì trả lời, và lỗi hiện ra là *"header parser received no
                // bytes"* — một câu không hề nhắc tới HTTP/2, nên rất dễ đọc thành lỗi mạng.
                //
                // Đo thật ở WS-16: cùng một yêu cầu, `curl` (HTTP/1.1) đi qua bình thường trong khi
                // job thử lại hỏng liên tiếp. Đây là lý do phải chạy thử thật chứ không chỉ chạy
                // bài kiểm — không bài kiểm đơn vị nào bắt được chỗ này.
                .version(HttpClient.Version.HTTP_1_1)
                // Không đi theo chuyển hướng: đích là một dịch vụ nội bộ đã biết địa chỉ. Đi theo
                // 302 là gửi bí mật tới một máy chủ mình không chọn.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean isEnabled() {
        return portal.isEnabled();
    }

    /**
     * Yêu cầu dựng lại một đường dẫn.
     *
     * @throws IllegalStateException khi cổng trả mã lỗi — để hàng đợi thử lại
     */
    public void revalidatePath(String path) {
        goi("{\"path\":%s}".formatted(chuoiJson(path)), path);
    }

    /** Yêu cầu dựng lại mọi trang mang một nhãn cache — dùng khi đổi menu, banner, cấu hình. */
    public void revalidateTag(String tag) {
        goi("{\"tag\":%s}".formatted(chuoiJson(tag)), "#" + tag);
    }

    private void goi(String body, String moTa) {
        if (!portal.isEnabled()) {
            // Không ném: chưa cấu hình cổng là một lựa chọn hợp lệ (môi trường dev, giai đoạn chưa
            // bật public-web). Ghi ở mức DEBUG để không làm ồn nhật ký mỗi lần xuất bản.
            log.debug("Bỏ qua yêu cầu dựng lại '{}' — chưa cấu hình app.portal", moTa);
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(portal.getBaseUrl() + PATH))
                .timeout(Duration.ofSeconds(portal.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header(SECRET_HEADER, portal.getRevalidateSecret())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                // ⚠ KHÔNG đưa nội dung phản hồi vào thông báo lỗi: nó đi vào bảng `jobs` và vào bản
                // sao lưu. Mã trạng thái đủ để chẩn đoán (401 = lệch bí mật, 503 = cổng chưa cấu hình).
                throw new IllegalStateException("Cổng công khai từ chối yêu cầu dựng lại '%s' — HTTP %d"
                        .formatted(moTa, response.statusCode()));
            }
            log.info("Đã yêu cầu cổng dựng lại '{}'", moTa);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị ngắt khi gọi cổng công khai", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không gọi được cổng công khai: " + e.getMessage(), e);
        }
    }

    /** Bọc chuỗi thành literal JSON. Slug đã qua {@code SlugUtils} nhưng nhãn thì không — thoát cho chắc. */
    private static String chuoiJson(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }
}
