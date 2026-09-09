package com.songnhue.app.testsupport;

import java.net.http.HttpClient;

import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

/**
 * Client HTTP cho bài kiểm tích hợp — <b>bản thay thế {@code TestRestTemplate}</b> (T11.69).
 *
 * <h2>Vì sao lớp này tồn tại</h2>
 *
 * <p>Spring Boot 4 <b>xoá hẳn</b> {@code org.springframework.boot.test.web.client.TestRestTemplate}
 * (đo trên jar thật: 0 lần xuất hiện trong toàn bộ {@code spring-boot-test-4.1.1.jar}). Đường đi
 * chính thống là {@code RestTestClient} với API fluent — nhưng nó sẽ bắt phải viết lại <b>55 lời
 * gọi</b> ở 37 tệp, tức viết lại chính phần <i>khẳng định</i> của bộ kiểm thử. Đổi cách đo cùng lúc
 * với đổi thứ được đo là cách chắc chắn nhất để một lượt di trú đi qua mà không ai biết nó đã đổi gì.
 *
 * <p>Lớp này giữ nguyên <b>đúng ba phương thức</b> bộ kiểm thử đang dùng, nên lượt di trú chỉ đổi
 * <i>tên kiểu</i> — ⛔ không đổi một dòng khẳng định nào.
 *
 * <h2>⛔ Hai hành vi BẮT BUỘC phải giống {@code TestRestTemplate}, ⛔ không phải {@code RestTemplate}</h2>
 *
 * <ol>
 *   <li><b>⛔ Không ném ở 4xx/5xx.</b> {@code RestTemplate} trần ném {@code HttpClientErrorException}.
 *       37 tệp của ta khẳng định <i>mã lỗi</i> trên {@code ResponseEntity} — nếu client ném thì mọi
 *       bài kiểm đường lỗi đổi từ *khẳng định sai* sang *ngoại lệ*, và một số bài sẽ đỏ vì lý do
 *       khác hẳn thứ chúng canh.
 *   <li><b>⛔ Không đi theo chuyển hướng.</b> Mặc định của {@code HttpURLConnection} là ĐI THEO. Nếu
 *       đi theo, một endpoint trả {@code 302} sẽ được đọc thành {@code 200} — bài kiểm xanh trong
 *       khi thứ nó khẳng định đã biến mất. Đây đúng hình dạng nguy hiểm nhất của dự án, và T40.27
 *       sắp thêm ba endpoint trả 302 nên nó ⛔ không phải rủi ro giả định.
 * </ol>
 *
 * <p>Cả hai được canh bởi {@code TestHttpTest} — một bộ trợ giúp tự viết mà ⛔ không có phép kiểm thì
 * chính nó thành chỗ trú của khuyết tật (luật 1).
 */
public class TestHttp {

    private final Environment environment;
    private final RestTemplate template;

    public TestHttp(Environment environment) {
        this.environment = environment;
        this.template = new RestTemplate(new JdkClientHttpRequestFactory(KHACH_HTTP));
        this.template.setErrorHandler(KHONG_NEM);
    }

    /**
     * ⛔ Giải cổng ở MỖI lượt gọi, ⛔ không ở hàm dựng: với {@code WebEnvironment.RANDOM_PORT}, bean
     * có thể được dựng trước khi máy chủ nhận cổng, và lúc ấy {@code local.server.port} còn rỗng.
     */
    private RestTemplate voiGoc() {
        String cong = environment.getProperty("local.server.port");
        if (cong == null || cong.isBlank()) {
            throw new IllegalStateException(
                    "Chưa có `local.server.port` — lớp kiểm thử phải chạy với WebEnvironment.RANDOM_PORT");
        }
        template.setUriTemplateHandler(new DefaultUriBuilderFactory("http://localhost:" + cong));
        return template;
    }

    public <T> ResponseEntity<T> getForEntity(String duongDan, Class<T> kieu, Object... thamSo) {
        return voiGoc().getForEntity(duongDan, kieu, thamSo);
    }

    public <T> ResponseEntity<T> postForEntity(String duongDan, Object than, Class<T> kieu, Object... thamSo) {
        return voiGoc().postForEntity(duongDan, than, kieu, thamSo);
    }

    public <T> ResponseEntity<T> exchange(
            String duongDan, HttpMethod phuongThuc, HttpEntity<?> than, Class<T> kieu, Object... thamSo) {
        return voiGoc().exchange(duongDan, phuongThuc, than, kieu, thamSo);
    }

    /**
     * ⛔⛔ PHẢI là {@link JdkClientHttpRequestFactory}, ⛔ KHÔNG phải
     * {@code SimpleClientHttpRequestFactory}.
     *
     * <p>Bản đầu của lớp này dùng {@code SimpleClientHttpRequestFactory} và <b>4 bài kiểm đỏ ngay
     * lượt chạy đầu</b> với {@code java.net.ProtocolException: Invalid HTTP method: PATCH} —
     * {@code HttpURLConnection} của JDK ⛔ không biết {@code PATCH}, mà dự án có hai endpoint dùng nó
     * ({@code /contacts/{id}/assignment}, {@code /contacts/{id}/read}). {@code TestRestTemplate} cũ
     * che chuyện này đi vì nó tự chọn bộ máy khác khi có mặt trên classpath.
     *
     * <p>{@code Redirect.NEVER} là mặc định của {@code HttpClient}, nhưng khai <b>tường minh</b>:
     * một mặc định ⛔ không phải một quyết định, và đây là mặc định mà cả một lớp bài kiểm 302 dựa vào
     * (luật 3).
     */
    private static final HttpClient KHACH_HTTP =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Trả lời nào cũng là một trả lời — bài kiểm tự khẳng định mã trạng thái. */
    private static final ResponseErrorHandler KHONG_NEM = new ResponseErrorHandler() {
        @Override
        public boolean hasError(ClientHttpResponse traLoi) {
            return false;
        }

        // ⛔ Framework 7 đổi chữ ký: handleError(URI, HttpMethod, ClientHttpResponse). Vẫn để rỗng,
        // nhưng `hasError` trả false nên nhánh này ⛔ không bao giờ được gọi — giữ để rõ chủ ý.
        @Override
        public void handleError(java.net.URI duongDan, HttpMethod phuongThuc, ClientHttpResponse traLoi) {
            // cố ý rỗng — xem javadoc lớp, mục (1)
        }
    };
}
