package com.songnhue.core.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Endpoint ai cũng gọi được, <b>không</b> cần đăng nhập.
 *
 * <p>Bắt buộc ghi {@link #reason()} — mỗi endpoint công khai là một cánh cửa mở, và người rà soát an
 * ninh phải đọc được ngay lý do nó mở mà không cần lần theo mã nguồn. Danh sách endpoint công khai
 * cũng là thứ được soát lại mỗi lần kiểm thử bảo mật (§4.6).
 *
 * <p>Dùng cho: đăng nhập, làm mới token, nội dung công khai của cổng thông tin (MOD-01), health
 * check.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {

    /** Vì sao endpoint này không cần đăng nhập. */
    String reason();
}
