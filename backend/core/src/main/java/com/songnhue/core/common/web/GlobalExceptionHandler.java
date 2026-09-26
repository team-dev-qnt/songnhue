package com.songnhue.core.common.web;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.unit.DataSize;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.error.ErrorMessageResolver;
import com.songnhue.core.common.exception.AppException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.InvalidFormatException;

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

    /**
     * Trần multipart <b>đã giải</b>, đọc lại từ chính thuộc tính Spring dùng để cấu hình bộ phân
     * tích — không phải một hằng số chép tay ở đây.
     *
     * <p>⚠ Đây là điểm mấu chốt của quy tắc 3. Chép số 120 vào mã nghĩa là câu thông báo và hành vi
     * thật có thể trôi khỏi nhau ngay lượt đầu ai đó đặt {@code UPLOAD_MAX_FILE_MB} — và trôi trong
     * im lặng, vì cả hai đều "có vẻ đúng". Đọc cùng một nguồn thì không có hai giá trị để lệch.
     */
    private final DataSize tranMotTep;

    public GlobalExceptionHandler(
            ErrorMessageResolver messages, @Value("${spring.servlet.multipart.max-file-size}") DataSize tranMotTep) {
        this.messages = messages;
        this.tranMotTep = tranMotTep;
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

    /**
     * Sai ĐỘNG TỪ thì trả <b>405</b>, ⛔ gộp vào 400 — <b>T61.40</b> (ASVS 14.5.1).
     *
     * <p>Bản cũ ném {@code HttpRequestMethodNotSupportedException} chung vào nhánh *"dữ liệu ⛔ hợp
     * lệ"* ⇒ người tích hợp gửi {@code PUT} vào một endpoint {@code POST} nhận câu *"dữ liệu ⛔ hợp
     * lệ"* rồi đi soi payload, trong khi payload ⛔ hề sai.
     *
     * <p>⚠⚠ Nó còn làm một lớp khẳng định BẢO MẬT thành khẳng định RỖNG: bài canh *"endpoint này ⛔
     * có động từ ghi"* so mã 400, mà một {@code @PutMapping} CÓ THẬT từ chối thân yêu cầu cũng trả
     * 400 ⇒ bài xanh ở cả hai trạng thái (T54.7 đã phải chuyển sang đọc annotation vì lý do này).
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        log.debug("[{}] Sai động từ: {}", RequestContext.traceId(), ex.getMessage());
        return build(ErrorCode.SYS_0013);
    }

    @ExceptionHandler({
        MissingServletRequestParameterException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception ex) {
        // Message gốc hay chứa tên class và cấu trúc JSON nội bộ → CHỈ ghi log
        log.debug("[{}] Request sai định dạng: {}", RequestContext.traceId(), ex.getMessage());
        return validationResponse(oSai(ex));
    }

    /**
     * Tên TRƯỜNG bị sai kiểu — {@code T68.28}.
     *
     * <p>⛔⛔ Nhánh trên cố ý trả danh sách RỖNG, và lý do ấy đúng: {@code ex.getMessage()} của Jackson
     * mang tên lớp Java và cấu trúc JSON nội bộ. Nhưng <i>"⛔ lộ message gốc"</i> và <i>"⛔ nói ô nào
     * sai"</i> là hai chuyện khác nhau, và bản cũ đánh đồng chúng ⇒ người nhập một biểu mẫu <b>26 ô
     * thông số</b> nhận đúng câu <i>"Dữ liệu gửi lên ⛔ hợp lệ"</i> rồi đi dò từng ô.
     *
     * <p>⇒ Chỉ bóc <b>đường dẫn trường</b> từ {@code InvalidFormatException}. Đó là tên trường của
     * chính API — thứ đã công khai trong tài liệu OpenAPI — ⛔ phải nội dung người dùng gửi lên.
     *
     * <p>⚠ {@code rejectedValue} để {@code null} <b>có chủ đích</b>: nhánh {@code @Valid} ở trên có
     * dội giá trị về, nhưng nhánh NÀY bắt cả những ngoại lệ mà "giá trị" có thể là một mảnh thân yêu
     * cầu bất kỳ. Tên ô là phần hành động được; giá trị thì ⛔ đáng đổi lấy một đường dội dữ liệu.
     */
    private static List<ApiError.ErrorDetail> oSai(Exception ex) {
        if (!(ex.getCause() instanceof InvalidFormatException loi)) {
            return List.of();
        }
        String duongDan = loi.getPath().stream()
                .map(JacksonException.Reference::getPropertyName)
                .filter(ten -> ten != null && !ten.isBlank())
                .collect(java.util.stream.Collectors.joining("."));
        return duongDan.isBlank() ? List.of() : List.of(new ApiError.ErrorDetail(duongDan, "TypeMismatch", null));
    }

    /**
     * Tệp vượt trần multipart của máy chủ — <b>413</b>, không phải 500.
     *
     * <h2>Vì sao ngoại lệ này cần một nhánh riêng</h2>
     *
     * Nó được ném ở {@code DispatcherServlet.checkMultipart}, tức <b>trước khi vào controller</b>:
     * không {@code @RequirePermission} nào chạy, không dòng mã nghiệp vụ nào chạy. Nên nó không thể
     * là {@link AppException}, và trước 30/08/2026 nó rơi thẳng vào {@link #handleUnexpected} →
     * người dùng nhận <i>"Lỗi hệ thống, vui lòng thử lại"</i> kèm mã tra cứu, cho một tệp chỉ cần
     * nén nhỏ lại. Đo được trên staging: 3 lượt, tất cả là màn hình tải ảnh sơ đồ hệ thống.
     *
     * <p>⚠ {@code log.warn} chứ không {@code log.error}: đây là lỗi người dùng gửi sai, và nhánh
     * ERROR duy nhất của lớp này phải giữ nguyên nghĩa "phía mình hỏng". Một lỗi nhập liệu thường
     * ngày nằm trong ERROR sẽ làm chính cái ERROR mất giá trị cảnh báo.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        String traceId = RequestContext.traceId();
        long tranMb = tranMotTep.toMegabytes();
        log.warn("[{}] Tệp vượt trần multipart {} MB: {}", traceId, tranMb, ex.getMessage());

        ApiError error = ApiError.of(ErrorCode.SYS_0011.code(), messages.resolve(ErrorCode.SYS_0011, tranMb));
        return ResponseEntity.status(ErrorCode.SYS_0011.status()).body(ApiResponse.fail(error, traceId));
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
        // ⛔⛔ T61.40 (ASVS 7.1.2) — bản cũ ghi NGUYÊN VĂN message của Postgres, thứ mang cả giá trị:
        //   `Key (username)=(nguyenvana) already exists`. Nhật ký ứng dụng có nhiều người đọc hơn bảng
        //   gốc và giữ 30 ngày, nên nó vừa biến một lỗi 409 thành một lượt rò dữ liệu cá nhân.
        //   ⇒ Chỉ giữ TÊN ràng buộc — đủ để dev tra ra chỗ hỏng, ⛔ mang giá trị nào.
        log.warn("[{}] Vi phạm ràng buộc dữ liệu: {}", RequestContext.traceId(), tenRangBuoc(ex));
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
    /**
     * Tên ràng buộc bị vi phạm, ⛔ kèm giá trị — T61.40.
     *
     * <p>Postgres đặt tên ràng buộc trong dấu nháy kép: {@code duplicate key value violates unique
     * constraint "uq_users_username"}. Lấy đúng phần ấy; ⛔ khớp được thì trả tên lớp ngoại lệ —
     * <b>⛔ bao giờ</b> trả message gốc (luật 9: hai nhánh phải phân biệt được, và nhánh dự phòng ⛔
     * được là chính thứ ta đang tránh).
     */
    private static String tenRangBuoc(DataIntegrityViolationException ex) {
        Throwable goc = ex.getMostSpecificCause();
        String msg = goc.getMessage() == null ? "" : goc.getMessage();
        java.util.regex.Matcher m = MAU_RANG_BUOC.matcher(msg);
        return m.find() ? m.group(1) : goc.getClass().getSimpleName();
    }

    private static final java.util.regex.Pattern MAU_RANG_BUOC =
            java.util.regex.Pattern.compile("constraint \"([a-zA-Z0-9_]+)\"");
}
