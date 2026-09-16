package com.songnhue.core.common.filter;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.songnhue.core.common.ratelimit.RateLimitPolicy;
import com.songnhue.core.common.ratelimit.RateLimitStore;
import com.songnhue.core.common.security.AccessTokenClaims;
import com.songnhue.core.common.web.ClientIp;
import com.songnhue.core.spi.SettingPort;

/**
 * Filter [3b] — hạn mức <b>theo NGƯỜI DÙNG đã xác thực</b>: xô {@code API} và {@code EXPORT}. T61.17.
 *
 * <h2>Vì sao đứng SAU {@link AuthFilter}</h2>
 *
 * Khoá của xô phải là danh tính <b>đã kiểm chữ ký và đối chiếu phiên</b>. Đọc {@code sub} từ token
 * trước bước ấy là để kẻ gọi xoay token giả mà xoay xô — một hạn mức né được là một hạn mức ⛔ tồn
 * tại. {@link AuthFilter} chỉ đặt {@link AuthRequestAttributes#TOKEN_CLAIMS} khi token đúng chữ ký,
 * còn hạn, phiên còn sống; mọi trường hợp khác (vắng token, token giả, token hết hạn gửi kèm
 * {@code /auth/refresh}) rơi về khoá <b>theo IP</b> — y như trước bản vá.
 *
 * <p>Cái giá của vị trí này đã cân: lượt gọi mang token giả đi qua một lần kiểm chữ ký trước khi bị
 * đếm. Kiểm chữ ký rẻ (khác hẳn băm BCrypt — lý do {@code LOGIN} vẫn đứng trước), và nginx đã chặn
 * thô 30 lượt/giây mỗi IP ở lớp ngoài. Còn đứng TRƯỚC {@link ScopeContextFilter} để lượt bị chặn ⛔
 * tốn một lần nạp quyền từ CSDL.
 *
 * <h2>Vì sao khoá là NGƯỜI DÙNG + IP, ⛔ chỉ người dùng</h2>
 *
 * <ul>
 *   <li>Câu hỏi của T61.17 là <i>"hai cán bộ sau một IP ⛔ được ăn chung ngân sách"</i> — khoá
 *       {@code người@IP} trả lời trọn câu ấy.
 *   <li>Một người trên hai máy là hai máy khách, như mọi lớp chặn khối lượng khác. Kẻ cầm tài khoản
 *       bị lộ muốn nhân xô phải có thêm IP <b>thật</b>: {@code X-Real-IP} do nginx biên ghi đè từ
 *       socket, ⛔ tự khai được (T47.4 · T47.5).
 *   <li>Bộ kiểm HTTP mô phỏng "mỗi bài một máy khách" bằng cách đổi IP ({@code PhienHttp.doiIp},
 *       T60.9). Khoá thuần theo người dùng bắt bộ kiểm dựng một tài khoản mới mỗi bài — đi một đường
 *       khác production chỉ để né chính cơ chế đang kiểm.
 * </ul>
 */
@Component
@Order(FilterOrder.RATE_LIMIT_NGUOI_DUNG)
public class HanMucNguoiDungFilter extends HanMucFilterCoSo {

    private final SettingPort settings;

    public HanMucNguoiDungFilter(
            RateLimitStore store,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            SettingPort settings) {
        super(store, exceptionResolver);
        this.settings = settings;
    }

    /**
     * T61.27 — trần kết xuất đọc từ {@code settings} MỖI lượt (SettingService có đệm, xoá khi sửa) ⇒ sửa
     * trên giao diện có hiệu lực ngay, ⛔ chờ khởi động lại. Kẹp ở {@link RateLimitPolicy#kepKetXuat}.
     */
    @Override
    int hanMuc(RateLimitPolicy policy) {
        if (policy == RateLimitPolicy.EXPORT) {
            return RateLimitPolicy.kepKetXuat(settings.getInt(RateLimitPolicy.KHOA_KET_XUAT, policy.limit()));
        }
        return policy.limit();
    }

    @Override
    boolean phuTrach(RateLimitPolicy policy) {
        return policy == RateLimitPolicy.API || policy == RateLimitPolicy.EXPORT;
    }

    @Override
    String danhTinh(HttpServletRequest request) {
        return khoa(request.getAttribute(AuthRequestAttributes.TOKEN_CLAIMS), ClientIp.cua(request));
    }

    /**
     * Hàm thuần — tách để bài kiểm hỏi thẳng. Hai không gian khoá ⛔ giao nhau: {@code u:…} chỉ sinh từ
     * claims đã kiểm, còn IP ⛔ bao giờ bắt đầu bằng {@code u:}.
     */
    static String khoa(Object claims, String ip) {
        if (claims instanceof AccessTokenClaims daKiem) {
            return "u:" + daKiem.subject() + "@" + ip;
        }
        return ip;
    }
}
