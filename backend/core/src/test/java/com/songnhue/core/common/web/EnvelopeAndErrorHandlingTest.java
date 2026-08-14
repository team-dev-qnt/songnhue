package com.songnhue.core.common.web;

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
            "spring.messages.fallback-to-system-locale=false"
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
}
