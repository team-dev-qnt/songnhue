package com.songnhue.content.infra;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Hỏi Google xem một mã reCAPTCHA v3 có thật ⛔ không — CN-01.4 / T36.6.
 *
 * <h2>⛔⛔ HỎNG THÌ MỞ, ⛔ KHÔNG ĐÓNG — và đây là quyết định đắt nhất của lớp này</h2>
 *
 * <p>Google ⛔ không trả lời (mạng đứt, DNS hỏng, Google chặn dải IP của VPS) thì lớp này trả
 * {@code true}. Lập luận:
 *
 * <ul>
 *   <li>Biểu mẫu này là <b>kênh phản ánh của người dân về công trình thuỷ lợi</b>. Một sự cố mạng
 *       phía ta ⛔ không được biến thành "người dân ⛔ không báo được sạt kênh".
 *   <li>Hạn mức tần suất ({@code RateLimitPolicy.PUBLIC}) <b>vẫn chạy</b> trong lúc đó — ta ⛔ không
 *       mất hết lớp bảo vệ, chỉ mất lớp tinh nhất.
 *   <li>Một lượt mất kết nối vài phút ⇒ vài lượt spam lọt. Một lượt chặn nhầm ⇒ một phản ánh ⛔
 *       không bao giờ tới.
 * </ul>
 *
 * <p>⚠ Nhưng <b>mã sai / điểm thấp thì ĐÓNG</b> — đó là câu trả lời của Google, ⛔ không phải sự
 * vắng mặt của nó. Hai thứ ấy phải phân biệt được (luật 9), và chúng phân biệt bằng <b>ngoại lệ</b>
 * so với <b>giá trị trả về</b>.
 *
 * <h2>⛔ Khoá bí mật đi trong THÂN, ⛔ không đi trong URL</h2>
 *
 * <p>URL nằm trong log truy cập, trong lịch sử proxy, trong báo cáo lỗi. Thân {@code POST} thì ⛔
 * không. Google hỗ trợ cả hai — chọn cái ⛔ không rò.
 *
 * <h2>⚠ Lớp này ⛔ CHƯA ĐƯỢC CHẠY VỚI KHOÁ THẬT</h2>
 *
 * <p>G13 chưa về, nên chưa lượt gọi nào ra tới Google. Thứ đã kiểm là <b>đường dây</b>: bật/tắt,
 * suy ra bắt buộc, và nhánh "bật mà thiếu khoá". Ghi ra thay vì để cái xanh của bộ kiểm đọc như một
 * lời bảo đảm (luật 28).
 */
@Component
public class RecaptchaClient {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaClient.class);

    private static final URI SITEVERIFY = URI.create("https://www.google.com/recaptcha/api/siteverify");

    /** Ngắn: người dân đang chờ nút Gửi phản hồi, ⛔ không phải một job nền. */
    private static final Duration HAN = Duration.ofSeconds(5);

    private final RecaptchaProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient client;

    public RecaptchaClient(RecaptchaProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = HttpClient.newBuilder()
                .connectTimeout(HAN)
                // ⛔ Đích là Google qua HTTPS; đi theo chuyển hướng là gửi khoá bí mật tới một máy
                //    chủ mình ⛔ không chọn.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * @param token mã do trình duyệt sinh; rỗng ⇒ {@code false} ngay, ⛔ không gọi mạng
     * @param diemToiThieuPhanTram ngưỡng theo phần trăm nguyên — 50 nghĩa là 0,5. ⛔ ⛔ Không dùng
     *     {@code double}: quy tắc 2 cấm số thực nhị phân ngoài gói quan sát, và
     *     {@code CodingRuleTest.noBinaryFloatingPoint} đã bắt bản đầu của lớp này
     * @return {@code true} khi hợp lệ <b>hoặc</b> khi ⛔ không hỏi được Google (xem javadoc lớp)
     */
    public boolean hopLe(String token, int diemToiThieuPhanTram) {
        if (token == null || token.isBlank()) {
            // ⛔ Đây là câu trả lời DỨT KHOÁT, ⛔ không phải sự cố: trình duyệt ⛔ không gửi mã.
            return false;
        }
        try {
            String than = "secret=" + URLEncoder.encode(properties.getSecret(), StandardCharsets.UTF_8) + "&response="
                    + URLEncoder.encode(token, StandardCharsets.UTF_8);

            HttpResponse<String> ra = client.send(
                    HttpRequest.newBuilder(SITEVERIFY)
                            .timeout(HAN)
                            .header("Content-Type", "application/x-www-form-urlencoded")
                            .POST(HttpRequest.BodyPublishers.ofString(than, StandardCharsets.UTF_8))
                            .build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (ra.statusCode() != 200) {
                log.warn("reCAPTCHA trả HTTP {} — cho qua (hỏng thì mở)", ra.statusCode());
                return true;
            }

            JsonNode json = objectMapper.readTree(ra.body());
            boolean thanhCong = json.path("success").asBoolean(false);

            // ⚠ Đọc điểm qua CHUỖI rồi dựng `BigDecimal` — `asDouble()` là đúng thứ quy tắc 2 cấm.
            //   Google trả một chữ số thập phân (0.0–1.0); nhân 100 để so ở thang phần trăm nguyên.
            BigDecimal diem = new BigDecimal(json.path("score").asText("0"))
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP);

            if (!thanhCong) {
                // ⛔ Không log nguyên văn `error-codes` ở mức WARN cho mọi lượt: một trang bị bot
                //    quét sẽ đẩy hàng nghìn dòng. Mức DEBUG là đủ để điều tra khi cần.
                log.debug("reCAPTCHA từ chối mã: {}", json.path("error-codes"));
                return false;
            }
            if (diem.intValueExact() < diemToiThieuPhanTram) {
                log.debug("reCAPTCHA điểm {}% < ngưỡng {}%", diem, diemToiThieuPhanTram);
                return false;
            }
            return true;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Lượt hỏi reCAPTCHA bị ngắt — cho qua (hỏng thì mở)");
            return true;
        } catch (Exception e) {
            // ⛔⛔ Cho qua. Xem javadoc lớp: một sự cố mạng phía ta ⛔ không được biến thành "người
            //    dân ⛔ không báo được sạt kênh". Hạn mức tần suất vẫn đang chạy.
            log.warn("⛔ Không hỏi được reCAPTCHA ({}) — cho qua, chỉ còn hạn mức tần suất bảo vệ", e.toString());
            return true;
        }
    }
}
