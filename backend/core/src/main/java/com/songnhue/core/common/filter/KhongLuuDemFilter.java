package com.songnhue.core.common.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter [1a] — phản hồi API nội bộ ⛔ được nằm lại trong bộ đệm nào — T61.35 (ASVS 8.2.1).
 *
 * <p>Dự án ⛔ dùng Spring Security nên ⛔ có {@code Cache-Control: no-store} mặc định, và {@code location /api/}
 * của nginx cũng ⛔ đặt. Đo 15/09/2026: 0 dòng mã nào đặt header ấy cho {@code /api/v1/**} riêng tư. Hệ quả:
 * hồ sơ CBNV, danh sách tài khoản, bản kết xuất có thể nằm lại trong bộ đệm đĩa của trình duyệt trên máy
 * dùng chung ở phòng làm việc, hoặc ở một proxy doanh nghiệp giữa đường.
 *
 * <p>Đứng NGOÀI hạn mức và xác thực (sau {@link CorrelationFilter}) để cả phản hồi 401/403/429 cũng mang
 * header. Đặt TRƯỚC {@code chain.doFilter} ⇒ controller muốn khác vẫn ghi đè được bằng
 * {@code ResponseEntity.cacheControl} — nhưng chỉ cổng công khai có lý do làm thế, và nó nằm ngoài phạm vi.
 *
 * <p>⛔ {@code /api/v1/public/**}: ảnh nhúng và tài liệu cổng tự đặt {@code max-age} ({@code PublicPortalController}).
 */
@Component
@Order(FilterOrder.KHONG_LUU_DEM)
public class KhongLuuDemFilter extends OncePerRequestFilter {

    static final String TIEN_TO_API = "/api/v1/";
    static final String TIEN_TO_CONG_KHAI = "/api/v1/public/";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !uri.startsWith(TIEN_TO_API) || uri.startsWith(TIEN_TO_CONG_KHAI);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        // ⛔⛔ T61.40 (ASVS 14.4.1) — `nosniff` cho phản hồi API. Hai image FE đã đặt header này cho
        //   nội dung của chúng, nhưng ⛔ image nào phục vụ `/api/**`: phản hồi JSON của backend đi
        //   thẳng qua nginx (tệp `edge-headers.conf` cố ý ⛔ đặt hộ — mỗi header một nơi chịu trách
        //   nhiệm). ⇒ Chủ của header này là chính backend, và chỗ đặt là bộ lọc đã phủ đúng phạm vi.
        response.setHeader("X-Content-Type-Options", "nosniff");
        chain.doFilter(request, response);
    }
}
