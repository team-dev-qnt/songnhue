package com.songnhue.core.common.web;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.error.ErrorMessageResolver;
import com.songnhue.core.common.exception.AppException;

/**
 * Nơi DUY NHẤT biến exception thành response (conventions.md §2.2).
 *
 * <p>Nguyên tắc bất di bất dịch: <b>không bao giờ để lộ stacktrace, câu SQL, tên bảng, tên class hay
 * message của thư viện ra ngoài</b>. Chi tiết kỹ thuật chỉ đi vào log kèm {@code traceId}; người
 * dùng nhận một câu tiếng Việt và mã tra cứu.
 *
 * <p>Exception ngoài danh mục rơi vào {@link #handleUnexpected} → {@code SYS-0001}. Đó là lưới an
 * toàn, không phải chỗ để dựa dẫm: module nghiệp vụ phải ném {@link AppException} có mã rõ ràng.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ErrorMessageResolver messages;

    public GlobalExceptionHandler(ErrorMessageResolver messages) {
        this.messages = messages;
    }

    // -------------------------------------------------------------------------
    // Lỗi nghiệp vụ — đã mang sẵn mã
    // -------------------------------------------------------------------------
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleApp(AppException ex) {
        ErrorCode code = ex.errorCode();
        String traceId = RequestContext.traceId();

        // 5xx là lỗi phía mình → log ERROR kèm stacktrace. 4xx là người dùng gửi sai
        // → log DEBUG, nếu không log sẽ đầy vì lỗi nhập liệu là chuyện thường ngày.
        if (code.status().is5xxServerError()) {
            log.error("[{}] {} — {}", traceId, code.code(), ex.getMessage(), ex);
        } else {
            log.debug("[{}] {} — {}", traceId, code.code(), ex.getMessage());
        }

        List<ApiError.ErrorDetail> details = ex.details().stream()
                .map(v -> new ApiError.ErrorDetail(v.field(), v.rule(), v.rejectedValue()))
                .toList();

        ApiError error = new ApiError(code.code(), messages.resolve(code, ex.messageArgs()), details);
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(error, traceId));
    }

    // -------------------------------------------------------------------------
    // Validation của Bean Validation — gom về SYS-0003 kèm chi tiết từng trường
    // -------------------------------------------------------------------------
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException ex) {
        List<ApiError.ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> new ApiError.ErrorDetail(f.getField(), f.getCode(), f.getRejectedValue()))
                .toList();
        return validationResponse(details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParamValidation(ConstraintViolationException ex) {
        List<ApiError.ErrorDetail> details = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.ErrorDetail(lastNode(v), ruleOf(v), v.getInvalidValue()))
                .toList();
        return validationResponse(details);
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        HttpRequestMethodNotSupportedException.class,
        HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        // Message gốc hay chứa tên class và cấu trúc JSON nội bộ → CHỈ ghi log
        log.debug("[{}] Request sai định dạng: {}", RequestContext.traceId(), ex.getMessage());
        return validationResponse(List.of());
    }

    // -------------------------------------------------------------------------
    // Lỗi tầng dữ liệu
    // -------------------------------------------------------------------------
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.debug("[{}] Optimistic lock: {}", RequestContext.traceId(), ex.getMessage());
        return build(ErrorCode.SYS_0005);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        // ⚠ Message của Postgres chứa tên bảng, tên constraint và có thể cả giá trị dữ liệu.
        //   Log lại để dev tra được, nhưng người dùng chỉ nhận câu chung.
        log.warn(
                "[{}] Vi phạm ràng buộc dữ liệu: {}",
                RequestContext.traceId(),
                ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.SYS_0005);
    }

    // -------------------------------------------------------------------------
    // Route không tồn tại
    // -------------------------------------------------------------------------
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoRoute(NoResourceFoundException ex) {
        return build(ErrorCode.SYS_0004);
    }

    // -------------------------------------------------------------------------
    // Lưới an toàn cuối cùng
    // -------------------------------------------------------------------------
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        String traceId = RequestContext.traceId();
        // Đây là chỗ DUY NHẤT stacktrace được ghi ra — và chỉ vào log, không ra response
        log.error("[{}] Lỗi không lường trước", traceId, ex);

        ApiError error = ApiError.of(ErrorCode.SYS_0001.code(), messages.resolve(ErrorCode.SYS_0001, traceId));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail(error, traceId));
    }

    // -------------------------------------------------------------------------
    private ResponseEntity<ApiResponse<Void>> validationResponse(List<ApiError.ErrorDetail> details) {
        String traceId = RequestContext.traceId();
        ErrorCode code = ErrorCode.SYS_0003;
        ApiError error = new ApiError(code.code(), messages.resolve(code), details);
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(error, traceId));
    }

    private ResponseEntity<ApiResponse<Void>> build(ErrorCode code) {
        String traceId = RequestContext.traceId();
        ApiError error = ApiError.of(code.code(), messages.resolve(code));
        return ResponseEntity.status(code.status()).body(ApiResponse.fail(error, traceId));
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int dot = path.lastIndexOf('.');
        return dot >= 0 ? path.substring(dot + 1) : path;
    }

    private static String ruleOf(ConstraintViolation<?> violation) {
        return violation
                .getConstraintDescriptor()
                .getAnnotation()
                .annotationType()
                .getSimpleName();
    }
}
