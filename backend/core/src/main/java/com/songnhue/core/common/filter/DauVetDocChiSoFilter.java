package com.songnhue.core.common.filter;

import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mốc lần cuối Prometheus đọc được chỉ số của ứng dụng — T61.41.
 *
 * <p>⭐ Phép đo DUY NHẤT từ phía ứng dụng phủ trọn chuỗi {@code METRICS_ALLOW_IP} → {@code METRICS_BEARER_TOKEN} →
 * nginx → Prometheus: cả bốn đúng thì mới có lượt đọc 200 tới đây. Prometheus chưa từng đọc được chỉ số ứng dụng nào
 * từ WS-7 tới T61.5 (target {@code ${…}} dùng nguyên văn) — và ⛔ màn hình nào nói ra điều đó.
 *
 * <p>⛔ Chỉ ghi lượt trả 200: một lượt 403 của nginx ⛔ tới được đây, còn lượt 5xx ⛔ phải "đọc được".
 */
@Component
@Order(FilterOrder.DAU_VET_CHI_SO)
public class DauVetDocChiSoFilter extends OncePerRequestFilter {

    static final String DUONG_DAN = "/actuator/prometheus";

    private final AtomicReference<Instant> lanCuoi = new AtomicReference<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !DUONG_DAN.equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_OK) {
            lanCuoi.set(Instant.now());
        }
    }

    /** Rỗng khi tiến trình này chưa từng được đọc (kể từ lần khởi động). */
    public Optional<Instant> lanDocCuoi() {
        return Optional.ofNullable(lanCuoi.get());
    }
}
