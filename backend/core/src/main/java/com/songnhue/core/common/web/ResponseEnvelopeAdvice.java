package com.songnhue.core.common.web;

import org.springframework.core.MethodParameter;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tự bọc mọi response của {@code /api/v1/**} vào {@link ApiResponse} (conventions.md §2.1).
 *
 * <p>Nhờ vậy controller chỉ cần {@code return dto;} — không ai phải nhớ bọc envelope bằng tay, và
 * cũng không ai bọc sai kiểu. {@link Page} được tách sẵn thành {@code data} + {@code meta}.
 *
 * <p>Không đụng tới: response đã là {@code ApiResponse} (do {@link GlobalExceptionHandler} tạo) và
 * mọi đường dẫn ngoài {@code /api/v1} — {@code /actuator/**}, {@code /v3/api-docs},
 * {@code /swagger-ui/**} phải giữ nguyên cấu trúc chuẩn của chúng.
 */
@RestControllerAdvice
public class ResponseEnvelopeAdvice implements ResponseBodyAdvice<Object> {

    private static final String API_PREFIX = "/api/v1";

    private final ObjectMapper objectMapper;

    public ResponseEnvelopeAdvice(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Không lọc theo kiểu trả về ở đây: handler lỗi khai báo ResponseEntity<ApiResponse<…>>,
        // nên getParameterType() ra ResponseEntity và không nhận ra envelope bên trong.
        // Việc nhận diện làm ở beforeBodyWrite theo BODY THẬT.
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {

        // Đã là envelope rồi thì trả nguyên — GlobalExceptionHandler tự dựng sẵn ApiResponse,
        // bọc thêm lần nữa sẽ biến response lỗi thành success=true với lỗi nằm trong `data`.
        if (body instanceof ApiResponse<?>) {
            return body;
        }
        if (!request.getURI().getPath().startsWith(API_PREFIX)) {
            return body;
        }

        String traceId = RequestContext.traceId();

        // Controller trả String thì Spring chọn StringHttpMessageConverter — converter này không
        // serialize được object bọc ngoài. Tự serialize rồi trả chuỗi JSON, để lời hứa "100%
        // endpoint đều có envelope" không có ngoại lệ nào.
        if (StringHttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            try {
                return objectMapper.writeValueAsString(ApiResponse.ok(body, traceId));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Không serialize được envelope cho response kiểu String", e);
            }
        }

        if (body instanceof Page<?> page) {
            return ApiResponse.ofPage(page, traceId);
        }
        return ApiResponse.ok(body, traceId);
    }
}
