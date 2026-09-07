package com.songnhue.core.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.songnhue.core.application.auth.AuthorityLoader;
import com.songnhue.core.common.security.AccessTokenClaims;
import com.songnhue.core.common.security.AuthContext;

/**
 * Filter [5] — nạp vai trò, quyền và phạm vi đơn vị vào {@link AuthContext} (T5.9, T5.11).
 *
 * <p>Tách khỏi {@code AuthFilter} vì hai việc khác nhau về bản chất: bên kia trả lời "token này có
 * thật không", còn ở đây trả lời "người này hiện được làm gì". Câu thứ hai đọc từ DB và có cache
 * riêng, câu thứ nhất thì không cache. Gộp lại thì hoặc phải cache cả hai (thu hồi phiên bị trễ),
 * hoặc không cache gì (mỗi request thêm 3 truy vấn).
 *
 * <p>Nếu token hợp lệ mà không nạp được người dùng — tài khoản đã bị xoá mềm, bị khoá, hoặc đơn vị
 * không còn tồn tại — thì context để trống. Interceptor sẽ trả 401 như trường hợp chưa đăng nhập,
 * đúng ý: token còn hạn nhưng người đứng sau nó đã không còn quyền vào hệ thống.
 */
@Component
@Order(FilterOrder.SCOPE_CONTEXT)
public class ScopeContextFilter extends OncePerRequestFilter {

    private final AuthorityLoader authorityLoader;

    public ScopeContextFilter(AuthorityLoader authorityLoader) {
        this.authorityLoader = authorityLoader;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        AccessTokenClaims claims = (AccessTokenClaims) request.getAttribute(AuthRequestAttributes.TOKEN_CLAIMS);

        if (claims != null) {
            authorityLoader
                    .load(claims.subject(), claims.sessionFamilyId(), claims.tokenId())
                    .ifPresentOrElse(
                            AuthContext::set,
                            () -> request.setAttribute(AuthRequestAttributes.TOKEN_REJECTED, Boolean.TRUE));
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // BẮT BUỘC: thread quay lại pool và phục vụ người khác. Quên dòng này là người sau thao
            // tác dưới danh nghĩa người trước — hệ thống vẫn chạy trơn tru, chỉ là sai người.
            AuthContext.clear();
        }
    }
}
