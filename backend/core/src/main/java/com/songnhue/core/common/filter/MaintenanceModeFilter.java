package com.songnhue.core.common.filter;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.application.maintenance.MaintenanceModeService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.security.AuthContext;

/**
 * Filter [5b] — chặn thao tác ghi khi hệ thống đang bảo trì (T7.6, M5.11 · architecture-review §7.3).
 *
 * <p>Trả {@code 503} kèm {@code SYS-0007}. Chỉ chặn <b>ghi</b>: trong lúc khôi phục dữ liệu, người
 * dùng vẫn xem được thứ đang có và vẫn thấy thông báo bảo trì trên giao diện. Chặn cả đọc thì màn
 * hình trắng trơn, và không ai phân biệt được "đang bảo trì" với "hệ thống chết".
 *
 * <p><b>Ba lối thoát, và cả ba đều bắt buộc</b> — thiếu bất kỳ cái nào là tự khoá mình ở ngoài:
 *
 * <ul>
 *   <li><b>Đường đăng nhập.</b> {@code POST /auth/login} cũng là thao tác ghi. Chặn nó thì Super
 *       Admin không đăng nhập được để tắt bảo trì — mà bảo trì lại hay được bật đúng lúc chưa ai
 *       đăng nhập.
 *   <li><b>Super Admin.</b> Người đang khôi phục dữ liệu phải thao tác được, kể cả thao tác ghi.
 *   <li><b>Ngoài {@code /api/v1}.</b> Actuator phải trả lời được để hạ tầng giám sát không báo cả
 *       cụm là chết trong lúc bảo trì có kế hoạch.
 * </ul>
 *
 * <p>⚠ Vai trò lấy từ {@link AuthContext}, nên filter này <b>phải</b> đứng sau
 * {@link ScopeContextFilter} — xem {@link FilterOrder#MAINTENANCE}.
 */
@Component
@Order(FilterOrder.MAINTENANCE)
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceModeFilter.class);

    private static final String API_PREFIX = "/api/v1";
    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** Phương thức đọc — đi qua kể cả khi đang bảo trì. */
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * Đường dẫn xác thực, luôn cho qua.
     *
     * <p>Khớp theo tiền tố {@code /api/v1/auth/} thay vì liệt kê từng đường: đăng nhập là chuỗi nhiều
     * bước (mật khẩu → 2FA → làm mới token), thiếu một bước là Super Admin vẫn đứng ngoài cửa.
     */
    private static final String AUTH_PREFIX = API_PREFIX + "/auth/";

    private final MaintenanceModeService maintenanceMode;
    private final HandlerExceptionResolver exceptionResolver;

    public MaintenanceModeFilter(
            MaintenanceModeService maintenanceMode,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.maintenanceMode = maintenanceMode;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(API_PREFIX) || uri.startsWith(AUTH_PREFIX) || READ_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        if (isSuperAdmin() || !maintenanceMode.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        log.info("Chặn {} {} — hệ thống đang bảo trì", request.getMethod(), request.getRequestURI());
        exceptionResolver.resolveException(request, response, null, new BusinessRuleException(ErrorCode.SYS_0007));
    }

    private boolean isSuperAdmin() {
        return AuthContext.current().map(user -> user.hasRole(SUPER_ADMIN)).orElse(false);
    }
}
