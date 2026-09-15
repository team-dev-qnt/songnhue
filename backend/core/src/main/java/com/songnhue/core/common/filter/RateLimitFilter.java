package com.songnhue.core.common.filter;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.common.ratelimit.RateLimitStore;
import com.songnhue.core.common.web.ClientIp;

/**
 * Filter [2] — hạn mức <b>theo IP, TRƯỚC xác thực</b>: chỉ xô {@code LOGIN} và {@code PUBLIC}.
 *
 * <p>Đặt trước AuthFilter là có chủ đích: một đợt dò mật khẩu phải bị chặn ngay ở cửa, không được
 * tiêu tốn tài nguyên băm BCrypt (cost ≥ 12 — cố ý chậm) của máy chủ. Cổng công khai ⛔ có người
 * dùng nào để đếm, nên IP là khoá duy nhất có nghĩa.
 *
 * <p>⛔⛔ <b>T61.17 — xô {@code API} và {@code EXPORT} ⛔ còn ở đây.</b> Bản cũ đếm cả bốn xô theo IP,
 * trong khi {@code conventions.md} §4.5 hứa <i>"app filter theo user/token"</i> và chú thích trong
 * chính lớp này khai <i>"cả Công ty ra Internet qua một IP NAT"</i>. Nếu câu ấy đúng thì 50 cán bộ
 * chung <b>100 lượt/phút</b> — tải nền của một tab quản trị lúc ⛔ ai bấm gì đã là ~2–3 lượt/phút —
 * và <b>10 lượt kết xuất/giờ cho cả Công ty</b>. Hai xô ấy nay ở {@link HanMucNguoiDungFilter}.
 *
 * <p>Đây là lớp thứ hai; nginx đã chặn thô theo IP ở lớp ngoài (§4.5).
 */
@Component
@Order(FilterOrder.RATE_LIMIT)
public class RateLimitFilter extends HanMucFilterCoSo {

    public RateLimitFilter(
            RateLimitStore store, @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        super(store, exceptionResolver);
    }

    @Override
    boolean phuTrach(RateLimitPolicy policy) {
        return policy == RateLimitPolicy.LOGIN || policy == RateLimitPolicy.PUBLIC;
    }

    /**
     * Khoá của xô hạn mức — <b>T43.8-b</b>.
     *
     * <p>⛔⛔ Bản cũ lấy phần tử ĐẦU của {@code X-Forwarded-For}; nginx <b>nối thêm</b> header ấy nên kẻ
     * gọi đổi header là đổi xô. Nay neo vào {@code X-Real-IP} qua {@link ClientIp} — xem javadoc lớp ấy.
     */
    @Override
    String danhTinh(HttpServletRequest request) {
        return ClientIp.cua(request);
    }
}
