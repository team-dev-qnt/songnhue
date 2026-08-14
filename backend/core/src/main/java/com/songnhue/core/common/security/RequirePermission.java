package com.songnhue.core.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Tầng 2 của phân quyền — quyền cần có để gọi endpoint này (conventions.md §4.2).
 *
 * <pre>
 * &#64;RequirePermission("ops:maintenance:create")
 * &#64;PostMapping
 * ResponseEntity&lt;…&gt; create(…)
 * </pre>
 *
 * <p><b>Deny by default</b>: mọi phương thức controller bắt buộc mang một trong ba annotation —
 * {@code @RequirePermission}, {@link AuthenticatedEndpoint} hoặc {@link PublicEndpoint}. Thiếu cả ba
 * thì {@code DenyByDefaultTest} làm CI đỏ (T5.10). Cố tình bắt khai báo tường minh thay vì mặc định
 * cho qua: quên gắn quyền là lỗi <i>im lặng</i> — endpoint chạy tốt, chỉ là ai cũng gọi được.
 *
 * <p>Đặt trên lớp thì áp cho mọi phương thức; đặt lại trên phương thức thì phương thức thắng.
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {

    /** Một hoặc nhiều mã dạng {@code module:resource:action}. */
    String[] value();

    /** {@code ANY} (mặc định): có một quyền là đủ. {@code ALL}: phải có đủ tất cả. */
    Mode mode() default Mode.ANY;

    enum Mode {
        ANY,
        ALL
    }
}
