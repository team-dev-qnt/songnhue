package com.songnhue.core.application.identity;

import java.util.List;

import org.springframework.stereotype.Service;

import com.songnhue.core.application.notification.NotificationRequest;
import com.songnhue.core.application.notification.NotificationService;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;

/**
 * Báo cho <b>chính chủ tài khoản</b> khi mật khẩu hoặc 2FA của họ vừa bị đổi — <b>T61.36</b>
 * (ASVS 2.2.3 · 2.5.5, tìm ra ở T61.28).
 *
 * <h2>⛔⛔ Vì sao một dòng {@code security_events} ⛔ đủ</h2>
 *
 * <p>Trước bản vá, cả bốn thao tác (tự đổi mật khẩu · quản trị đặt lại mật khẩu · quản trị đặt lại
 * 2FA · đăng ký 2FA mới) chỉ <b>ghi một dòng sự kiện bảo mật</b>. Bảng ấy do quản trị viên đọc, ⛔
 * phải chủ tài khoản — nên một tài khoản bị chiếm <b>⛔ lộ ra cho tới khi</b> chủ của nó tình cờ
 * bị đẩy khỏi phiên rồi đăng nhập lại ⛔ được. Người duy nhất biết *"tôi ⛔ làm việc này"* là người
 * duy nhất ⛔ được báo.
 *
 * <h2>Hai kênh, và vì sao ⛔ chỉ IN_APP</h2>
 *
 * <p>Kẻ vừa chiếm tài khoản <b>đọc được</b> thông báo trong ứng dụng rồi xoá dấu vết. Thư đi tới một
 * hộp thư <b>ngoài tầm</b> của hệ này — đó mới là kênh mang giá trị cảnh báo. ⚠ Kênh EMAIL có thể
 * đang tắt hoặc chưa cấu hình SMTP: khi đó {@code NotificationService} đánh dấu {@code SKIPPED} và
 * ⛔ ném, tức bản vá ⛔ bao giờ làm hỏng chính thao tác nó đi kèm.
 *
 * <p>⚠⚠ Tách thành lớp RIÊNG (⛔ nhét vào {@code UserAdminService}) vì cả bốn nơi gọi nằm ở bốn lớp
 * khác nhau, và vì {@code UserAdminService} đã chạm trần {@code ParameterNumber} của Checkstyle —
 * bài học T61.30, khi {@code DatLaiHaiBuocService} phải tách ra vì đúng lý do ấy.
 */
@Service
public class CanhBaoTaiKhoanService {

    private final NotificationService notifications;

    public CanhBaoTaiKhoanService(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** Mật khẩu vừa đổi — {@code boiQuanTri} phân biệt "tôi vừa đổi" với "ai đó đổi hộ tôi". */
    public void matKhauDaDoi(User user, boolean boiQuanTri) {
        bao(
                boiQuanTri ? "PASSWORD_RESET_BY_ADMIN" : "PASSWORD_CHANGED",
                "Mật khẩu tài khoản của bạn vừa thay đổi",
                boiQuanTri
                        ? "Quản trị viên vừa đặt lại mật khẩu cho tài khoản %s. Nếu bạn ⛔ đề nghị việc này, "
                                + "hãy báo ngay cho quản trị hệ thống."
                        : "Mật khẩu tài khoản %s vừa được đổi và mọi phiên đăng nhập cũ đã bị thu hồi. "
                                + "Nếu ⛔ phải bạn làm, hãy báo ngay cho quản trị hệ thống.",
                user);
    }

    /** 2FA vừa bị quản trị đặt lại — người dùng sẽ phải đăng ký lại thiết bị ở lần đăng nhập tới. */
    public void haiBuocDaDatLai(User user) {
        bao(
                "TWO_FACTOR_RESET_BY_ADMIN",
                "Xác thực hai bước của bạn vừa bị đặt lại",
                "Quản trị viên vừa gỡ đăng ký xác thực hai bước của tài khoản %s. Lần đăng nhập tới bạn sẽ "
                        + "phải đăng ký lại thiết bị. Nếu bạn ⛔ đề nghị việc này, hãy báo ngay cho quản trị hệ thống.",
                user);
    }

    /** Một thiết bị 2FA mới vừa được đăng ký cho tài khoản. */
    public void haiBuocDaDangKy(User user) {
        bao(
                "TWO_FACTOR_ENROLLED",
                "Tài khoản của bạn vừa đăng ký xác thực hai bước",
                "Một thiết bị xác thực hai bước vừa được đăng ký cho tài khoản %s. Nếu ⛔ phải bạn làm, "
                        + "hãy báo ngay cho quản trị hệ thống.",
                user);
    }

    private void bao(String loaiSuKien, String tieuDe, String mauThan, User user) {
        notifications.notify(new NotificationRequest(
                loaiSuKien,
                tieuDe,
                mauThan.formatted(user.getUsername()),
                NotificationSeverity.WARNING,
                null,
                "User",
                user.getId(),
                List.of(),
                List.of(user.getId()),
                null,
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                false));
    }
}
