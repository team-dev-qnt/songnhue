package com.songnhue.core.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Chỉ cần đăng nhập, không cần quyền cụ thể nào.
 *
 * <p>Dành cho những thao tác người dùng làm <b>với chính mình</b>: xem hồ sơ bản thân, đổi mật khẩu,
 * xem và đăng xuất phiên của mình, đăng xuất. Gán một mã quyền cho những việc này là sai về mặt mô
 * hình — không có tình huống nào một người dùng hợp lệ lại bị cấm đổi mật khẩu của chính họ.
 *
 * <p>Có annotation riêng thay vì để trống là để phân biệt được với <b>quên khai báo</b>: nhìn vào mã
 * nguồn phải thấy ngay đây là quyết định có ý thức. Đó chính là điều mà quét deny-by-default (T5.10)
 * kiểm.
 *
 * <p>⚠ Phạm vi dữ liệu vẫn phải tự kiểm trong service: "phiên của mình" nghĩa là lọc theo
 * {@code userId} của người đang đăng nhập, không phải nhận id từ request rồi tin.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthenticatedEndpoint {

    /** Vì sao thao tác này không gắn với quyền nào. */
    String reason();
}
