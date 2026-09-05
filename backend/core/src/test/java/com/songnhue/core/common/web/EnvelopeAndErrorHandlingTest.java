package com.songnhue.core.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.error.ErrorMessageResolver;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.filter.CorrelationFilter;

/**
 * Kiểm hai lời hứa của Common Platform bằng request thật:
 *
 * <ol>
 *   <li>Mọi response — kể cả lỗi — đều đúng envelope §2.1 và <b>luôn có traceId</b> (DoD #9).
 *   <li>Lỗi không lường trước <b>không để lộ</b> stacktrace, tên class hay message kỹ thuật (§2.2).
 * </ol>
 */
@SpringBootTest(classes = EnvelopeAndErrorHandlingTest.TestApp.class)
@TestPropertySource(
        properties = {
            "spring.messages.basename=error-messages",
            "spring.messages.encoding=UTF-8",
            "spring.messages.fallback-to-system-locale=false",
            // ⭐ BẮT BUỘC, và việc nó bắt buộc là CÓ CHỦ ĐÍCH — không phải phiền toái để né.
            //
            // `GlobalExceptionHandler` đọc trần multipart bằng `@Value` KHÔNG mặc định, nên thiếu
            // thuộc tính này là context không lên. Đổi lại, ta được một bảo đảm mạnh hơn mọi bài
            // kiểm: xoá khối `spring.servlet.multipart` khỏi `application.yml` thì **ứng dụng từ
            // chối khởi động**, thay vì âm thầm rơi về mặc định 1MB của Spring Boot và trả 500 cho
            // mọi tệp lớn (§10.69 — đúng sự cố staging 30/08).
            //
            // ⛔ Nếu bạn tới đây vì context đỏ: KHÔNG thêm giá trị mặc định vào `@Value`. Một mặc
            //    định ở đó làm câu thông báo lỗi nói một con số còn máy chủ hành xử theo con số
            //    khác — đúng cái bẫy mà cả §10.69 sinh ra để đóng lại.
            "spring.servlet.multipart.max-file-size=1MB"
        })
@Import(EnvelopeAndErrorHandlingTest.TestController.class)
class EnvelopeAndErrorHandlingTest {

    /**
     * Ứng dụng tối giản cho test: chỉ web + message source.
     *
     * <p>Loại DataSource/JPA/Flyway ra khỏi auto-config — test này kiểm envelope và xử lý lỗi,
     * không đụng tới cơ sở dữ liệu. Kéo cả tầng DB vào chỉ làm test chậm và phụ thuộc thứ không
     * liên quan.
     *
     * <p>{@code GlobalExceptionHandler} và {@code ResponseEnvelopeAdvice} nằm cùng package với lớp
     * này nên được component scan tự nhặt — đúng như khi chạy thật.
     */
    @SpringBootApplication(
            exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                FlywayAutoConfiguration.class
            })
    static class TestApp {

        @Bean
        ErrorMessageResolver errorMessageResolver(org.springframework.context.MessageSource messageSource) {
            return new ErrorMessageResolver(messageSource);
        }
    }

    /**
     * Byte "ảnh" của bài kiểm.
     *
     * <p>⚠ Cố ý mở đầu bằng 4 byte magic của PNG và chứa một byte {@code 0x00}: nếu có ai đó lỡ
     * đưa thân phản hồi qua một bộ mã hoá văn bản, byte 0 sẽ chết hoặc bị thay, và phép so sánh
     * mảng bên dưới bắt được — thay vì im lặng đi qua như một chuỗi UTF-8 hợp lệ.
     */
    private static final byte[] ANH = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, (byte) 0xFF, 0x42};

    /** Controller giả lập, chỉ tồn tại trong test. */
    @RestController
    @Validated
    static class TestController {

        @GetMapping("/api/v1/core/ping")
        Payload ping() {
            return new Payload("pong", 42);
        }

        @GetMapping("/api/v1/core/business-error")
        Payload businessError() {
            throw (BusinessRuleException) new BusinessRuleException(ErrorCode.OPS_2001)
                    .withDetail("completedDate", "AFTER_OR_EQUAL_START_DATE", "2026-08-01");
        }

        @GetMapping("/api/v1/core/not-found")
        Payload notFound() {
            throw new ResourceNotFoundException(ErrorCode.HYD_1001);
        }

        @GetMapping("/api/v1/core/boom")
        Payload boom() {
            // Lỗi lập trình điển hình — thứ TUYỆT ĐỐI không được lộ ra ngoài
            throw new IllegalStateException("Chi tiết nội bộ: bảng constructions, cột org_unit_id bị null");
        }

        @GetMapping("/api/v1/core/validated")
        Payload validated(@RequestParam @Min(1) int size) {
            return new Payload("ok", size);
        }

        /**
         * Ảnh — {@code ResponseEntity<byte[]>}, đúng chữ ký của
         * {@code PublicPortalController.file()}.
         *
         * <p>Spring chọn {@code ByteArrayHttpMessageConverter} theo kiểu trả về này, TRƯỚC khi
         * advice chạy. Đó là toàn bộ lý do bài kiểm tồn tại.
         */
        @GetMapping("/api/v1/core/anh")
        ResponseEntity<byte[]> anh() {
            return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(ANH);
        }

        /** Loại thứ hai đi qua converter không-JSON — chứng minh phép chừa không chỉ đúng cho byte[]. */
        @GetMapping("/api/v1/core/tai-ve")
        ResponseEntity<Resource> taiVe() {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(new ByteArrayResource(ANH));
        }

        record Payload(String message, int value) {}
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new CorrelationFilter())
                .build();
    }

    @Test
    @DisplayName("Thành công: bọc envelope, success=true, có traceId")
    void wrapsSuccessResponse() throws Exception {
        mockMvc()
                .perform(get("/api/v1/core/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("pong"))
                .andExpect(jsonPath("$.data.value").value(42))
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(header().exists(RequestContext.TRACE_ID_HEADER));
    }

    @Test
    @DisplayName("Nhận lại traceId do phía gọi gửi sang — nối được chuỗi log")
    void reusesIncomingTraceId() throws Exception {
        mockMvc()
                .perform(get("/api/v1/core/ping").header(RequestContext.TRACE_ID_HEADER, "abc123def456"))
                .andExpect(jsonPath("$.traceId").value("abc123def456"))
                .andExpect(header().string(RequestContext.TRACE_ID_HEADER, "abc123def456"));
    }

    @Test
    @DisplayName("traceId rác từ client bị bỏ, sinh cái mới")
    void rejectsMalformedTraceId() throws Exception {
        mockMvc()
                .perform(get("/api/v1/core/ping").header(RequestContext.TRACE_ID_HEADER, "x'; DROP TABLE users;--"))
                .andExpect(jsonPath("$.traceId").value(not(containsString("DROP"))));
    }

    @Test
    @DisplayName("Lỗi nghiệp vụ: đúng mã, đúng HTTP status, có chi tiết theo trường")
    void mapsBusinessError() throws Exception {
        mockMvc()
                .perform(get("/api/v1/core/business-error"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("OPS-2001"))
                .andExpect(jsonPath("$.error.message").value("Ngày hoàn thành phải lớn hơn hoặc bằng ngày bắt đầu"))
                .andExpect(jsonPath("$.error.details", hasSize(1)))
                .andExpect(jsonPath("$.error.details[0].field").value("completedDate"))
                .andExpect(jsonPath("$.error.details[0].rule").value("AFTER_OR_EQUAL_START_DATE"))
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    @DisplayName("ErrorCode quyết định HTTP status, controller không tự chọn")
    void statusComesFromErrorCode() throws Exception {
        mockMvc()
                .perform(get("/api/v1/core/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("HYD-1001"));
    }

    @Test
    @DisplayName("Lỗi không lường trước → SYS-0001, KHÔNG lộ chi tiết kỹ thuật")
    void hidesInternalDetails() throws Exception {
        String body = mockMvc()
                .perform(get("/api/v1/core/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SYS-0001"))
                .andExpect(jsonPath("$.traceId", notNullValue()))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("org_unit_id"))))
                .andExpect(content().string(not(containsString("constructions"))))
                .andExpect(content().string(not(containsString("com.songnhue"))))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Message chỉ đưa traceId để người dùng đọc cho bộ phận hỗ trợ
        org.assertj.core.api.Assertions.assertThat(body).contains("Mã tra cứu");
    }

    @Test
    @DisplayName("Bean Validation → SYS-0003 kèm tên trường sai")
    void mapsValidationError() throws Exception {
        mockMvc()
                .perform(get("/api/v1/core/validated").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("SYS-0003"))
                .andExpect(jsonPath("$.error.details[0].field").value("size"))
                .andExpect(jsonPath("$.error.details[0].rule").value("Min"));
    }

    @Test
    @DisplayName("Đường dẫn ngoài /api/v1 KHÔNG bị bọc envelope")
    void doesNotWrapNonApiPaths() throws Exception {
        mockMvc().perform(get("/khong-ton-tai")).andExpect(status().is4xxClientError());
    }

    // ---- Phản hồi nhị phân: envelope PHẢI đứng ngoài ---------------------------
    //
    // ⚠⚠ Ba bài dưới đây trả một món nợ đã thành sự cố: `GET /api/v1/public/files/<id>` — đường
    //    phục vụ ảnh bìa của mọi bài viết trên cổng — trả 500 với
    //    `ClassCastException: ApiResponse cannot be cast to [B`, sau khi đã đọc xong byte từ MinIO.
    //
    //    Endpoint ấy có bài kiểm, nhưng bài kiểm dùng UUID KHÔNG TỒN TẠI nên chỉ đi nhánh 404.
    //    Nhánh trả byte — nhánh duy nhất hỏng — chưa ai đi qua (luật 7). Ở đây đi qua nó.

    @Test
    @DisplayName("⭐⭐ Ảnh trả về NGUYÊN BYTE, không bị bọc envelope")
    void anhKhongBiBocEnvelope() throws Exception {
        byte[] than = mockMvc()
                .perform(get("/api/v1/core/anh"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(than)
                .as(
                        """
                    Thân phản hồi phải là ĐÚNG mảng byte controller trả, không phải JSON bọc quanh nó.

                    Trước bản vá, `ResponseEnvelopeAdvice` bọc cả `byte[]` vào `ApiResponse`, trong
                    khi Spring đã chọn `ByteArrayHttpMessageConverter` theo kiểu trả về — và
                    converter ấy ép kiểu thân về `[B` lúc tính Content-Length. Kết quả là 500.""")
                .isEqualTo(ANH);
    }

    @Test
    @DisplayName("⭐ Tệp tải về (Resource) cũng vậy — phép chừa canh CONVERTER, không canh kiểu byte[]")
    void resourceCungKhongBiBoc() throws Exception {
        byte[] than = mockMvc()
                .perform(get("/api/v1/core/tai-ve"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(than)
                .as("bắt theo từng kiểu dữ liệu thì luôn có kiểu thứ tư lọt qua — luật 24")
                .isEqualTo(ANH);
    }

    @Test
    @DisplayName("⛔ Kiểm chứng ngược: phép chừa KHÔNG được nới tay tới response JSON")
    void jsonVanBiBocNhuCu() throws Exception {
        // Không có bài này thì `return body;` vô điều kiện ở đầu `beforeBodyWrite` cũng làm hai bài
        // trên xanh — trong khi nó gỡ envelope khỏi TOÀN BỘ hệ thống.
        mockMvc()
                .perform(get("/api/v1/core/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("pong"))
                .andExpect(jsonPath("$.traceId", notNullValue()));
    }
}
