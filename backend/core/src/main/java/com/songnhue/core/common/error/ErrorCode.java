package com.songnhue.core.common.error;

import org.springframework.http.HttpStatus;

/**
 * Danh mục mã lỗi toàn hệ thống — bản mã hoá của {@code conventions.md} §2.3.
 *
 * <p>Định dạng {@code <PREFIX>-<4 số>}. Prefix theo module: {@code SYS} (hệ thống/chung),
 * {@code AUTH}, {@code CMS}, {@code OPS}, {@code HYD}, {@code HR}, {@code ADM}. Dải số: {@code 0xxx}
 * hệ thống/chung · {@code 1xxx} not-found/conflict · {@code 2xxx} rule nghiệp vụ · {@code 3xxx}
 * quyền/phạm vi · {@code 5xxx} lỗi hệ thống ngoài.
 *
 * <p><b>Thêm mã mới = thêm hằng ở đây + thêm dòng vào {@code error-messages.properties} + cập
 * nhật {@code conventions.md} §2.3 và {@code shared/error-map.ts} bên FE.</b> Không có message thì
 * {@code ErrorCatalogTest} làm build đỏ — cố ý, để mã lỗi không bao giờ lọt ra ngoài dưới dạng khoá
 * thô.
 *
 * <p>Message nằm ở file properties chứ không nằm trong enum: sửa câu chữ tiếng Việt là việc của
 * người viết tài liệu, không nên bắt biên dịch lại mã nguồn.
 */
public enum ErrorCode {

    // ---- Hệ thống / dùng chung -------------------------------------------------
    /** Lỗi không lường trước. Chỉ lộ traceId ra ngoài, chi tiết nằm ở log. */
    SYS_0001("SYS-0001", HttpStatus.INTERNAL_SERVER_ERROR),
    SYS_0002("SYS-0002", HttpStatus.TOO_MANY_REQUESTS),
    /** Input sai định dạng / thiếu trường — mặc định của {@code ValidationException}. */
    SYS_0003("SYS-0003", HttpStatus.BAD_REQUEST),
    /** Mặc định của {@code ResourceNotFoundException} khi module chưa có mã riêng. */
    SYS_0004("SYS-0004", HttpStatus.NOT_FOUND),
    /** Optimistic lock hoặc trùng unique — mặc định của {@code ConflictException}. */
    SYS_0005("SYS-0005", HttpStatus.CONFLICT),
    /** Hệ thống bên ngoài lỗi — mặc định của {@code UpstreamException}. */
    SYS_0006("SYS-0006", HttpStatus.BAD_GATEWAY),
    /** Chế độ bảo trì đang bật (chặn ghi lúc khôi phục dữ liệu — M5.11). */
    SYS_0007("SYS-0007", HttpStatus.SERVICE_UNAVAILABLE),
    /** Vi phạm rule nghiệp vụ chưa có mã riêng — mặc định của {@code BusinessRuleException}. */
    SYS_0008("SYS-0008", HttpStatus.UNPROCESSABLE_ENTITY),

    // ---- Xác thực & phân quyền -------------------------------------------------
    /** Message cố ý mơ hồ: không tiết lộ tài khoản có tồn tại hay không (§4.1). */
    AUTH_0001("AUTH-0001", HttpStatus.UNAUTHORIZED),
    AUTH_0002("AUTH-0002", HttpStatus.UNAUTHORIZED),
    AUTH_0003("AUTH-0003", HttpStatus.LOCKED),
    /** Mã 2FA sai, hết hiệu lực, hoặc đã dùng rồi (chống replay). */
    AUTH_0004("AUTH-0004", HttpStatus.UNAUTHORIZED),
    /** Thiếu hoặc sai {@code X-CSRF-Token} — double-submit không khớp (§4.1). */
    AUTH_0005("AUTH-0005", HttpStatus.FORBIDDEN),
    /** Mật khẩu mới không đạt chính sách đọc từ bảng {@code settings} (M5.15). */
    AUTH_0006("AUTH-0006", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Đang bắt buộc đổi mật khẩu — chặn mọi thao tác khác cho tới khi đổi xong. */
    AUTH_0007("AUTH-0007", HttpStatus.FORBIDDEN),
    /** Phiên bị thu hồi vì phát hiện dùng lại refresh token cũ — buộc đăng nhập lại (§4.1). */
    AUTH_0008("AUTH-0008", HttpStatus.UNAUTHORIZED),
    AUTH_3001("AUTH-3001", HttpStatus.FORBIDDEN),
    /** Dữ liệu ngoài phạm vi đơn vị — scope filter tầng 3 chặn (§4.2). */
    AUTH_3002("AUTH-3002", HttpStatus.FORBIDDEN),

    // ---- MOD-01 Cổng thông tin điện tử -----------------------------------------
    CMS_2001("CMS-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    CMS_2002("CMS-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    CMS_5001("CMS-5001", HttpStatus.BAD_GATEWAY),

    // ---- MOD-02 Vận hành công trình --------------------------------------------
    OPS_2001("OPS-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2002("OPS-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2003("OPS-2003", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2004("OPS-2004", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2005("OPS-2005", HttpStatus.CONFLICT),
    OPS_2006("OPS-2006", HttpStatus.UNPROCESSABLE_ENTITY),
    OPS_2007("OPS-2007", HttpStatus.UNPROCESSABLE_ENTITY),
    /** Trạng thái công trình là giá trị dẫn xuất — client sửa trực tiếp là từ chối. */
    OPS_3001("OPS-3001", HttpStatus.FORBIDDEN),

    // ---- MOD-03 Thủy văn --------------------------------------------------------
    HYD_1001("HYD-1001", HttpStatus.NOT_FOUND),
    HYD_2001("HYD-2001", HttpStatus.UNPROCESSABLE_ENTITY),
    HYD_2002("HYD-2002", HttpStatus.UNPROCESSABLE_ENTITY),
    HYD_2003("HYD-2003", HttpStatus.UNPROCESSABLE_ENTITY),
    HYD_2004("HYD-2004", HttpStatus.UNPROCESSABLE_ENTITY),

    // ---- MOD-04 Nhân sự ---------------------------------------------------------
    HR_2001("HR-2001", HttpStatus.UNPROCESSABLE_ENTITY),

    // ---- MOD-05 Quản trị --------------------------------------------------------
    ADM_2001("ADM-2001", HttpStatus.UNPROCESSABLE_ENTITY);

    private final String code;
    private final HttpStatus status;

    ErrorCode(String code, HttpStatus status) {
        this.code = code;
        this.status = status;
    }

    /** Mã hiển thị ra API và ghi vào log, VD {@code OPS-2001}. */
    public String code() {
        return code;
    }

    /** HTTP status trả về cho mã này — cố định, controller không tự chọn. */
    public HttpStatus status() {
        return status;
    }

    /** Khoá tra message trong {@code error-messages.properties} — trùng luôn với mã. */
    public String messageKey() {
        return code;
    }
}
