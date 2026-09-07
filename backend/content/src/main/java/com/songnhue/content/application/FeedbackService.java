package com.songnhue.content.application;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Feedback;
import com.songnhue.content.infra.FeedbackRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.SettingPort;

/**
 * Tiếp nhận và <b>công bố</b> phản hồi từ cổng công khai — CN-01.6, chốt <b>D1</b>.
 *
 * <p>Lớp này giữ <b>hai nửa của đường công khai</b> và chỉ hai nửa ấy: người dân <i>gửi</i>, và
 * người đọc cổng <i>xem những mục đã duyệt</i>. Đường cán bộ (kiểm duyệt, thống kê, xoá) ở
 * {@link FeedbackModerationService} — cùng lý do đã tách {@code ContactService} khỏi
 * {@code ContactInboxService}: hai nhóm người dùng, hai bộ phụ thuộc rời nhau.
 *
 * <h2>⛔⛔ Kiểm duyệt 100% — và bảo đảm ấy nằm ở LƯỢC ĐỒ, ⛔ không ở đây</h2>
 *
 * <p>{@code feedbacks.status} có {@code DEFAULT 'CHO_DUYET'} và {@link Feedback} khởi tạo field
 * bằng đúng giá trị ấy; ⛔ không có hàm dựng nào nhận trạng thái. Một dòng {@code if} ở service là
 * chỗ để ngày nào đó có người thêm một nhánh "tự duyệt nếu…", còn một mặc định ở lược đồ + một
 * đường ghi trạng thái <b>duy nhất</b> qua Workflow engine thì ⛔ không có nhánh nào để thêm.
 *
 * <h2>⭐ Công bố đọc qua {@code daDuyetMoiNhat()} — trạng thái ⛔ KHÔNG phải tham số</h2>
 *
 * <p>{@link #daDuyet()} ⛔ không nhận trạng thái nào từ bên ngoài, và truy vấn nó gọi khai
 * {@code DA_DUYET} <b>trong chính câu JPQL</b>. Một {@code timTheoTrangThai(status)} dùng chung cho
 * cả đường quản trị lẫn đường công khai là để ngỏ đúng một lỗi: truyền nhầm {@code CHO_DUYET} và
 * công bố những mục <b>chưa ai đọc</b> — thứ chốt D1 dựng ra để chặn.
 *
 * <h2>⛔ Nội dung là văn bản do người lạ gõ, và ở đây nó đi XA HƠN {@code contacts}</h2>
 *
 * <p>Sau khi duyệt, nó hiện trên <b>cổng công khai</b>. Một {@code dangerouslySetInnerHTML} đặt lên
 * trường này là XSS lưu trữ nhắm vào <i>mọi người đọc cổng</i>, ⛔ không chỉ vào người quản trị.
 *
 * <h2>Chống lạm dụng</h2>
 *
 * <p>Bốn bảo đảm chung nằm ở {@link InboundSubmissionGate} — <b>chỗ dữ liệu đi qua</b>, ⛔ không
 * chép lại ở đây (luật 12). Hạn mức tần suất do {@code RateLimitFilter} lo trên tiền tố
 * {@code /api/v1/public}.
 */
@Service
public class FeedbackService {

    /**
     * Trần độ dài một góp ý.
     *
     * <p>Ngắn hơn {@code contacts} (5.000) có chủ đích: đây là một ô góp ý về cổng, ⛔ không phải
     * nơi trình bày một vụ việc. Trần dài hơn ⛔ không giúp ai — nó chỉ làm khối hiển thị trên cổng
     * vỡ bố cục và làm màn hình kiểm duyệt khó đọc.
     */
    private static final int DAI_TOI_DA_NOI_DUNG = 2_000;

    private static final int DAI_TOI_DA_TEN = 255;

    /** Số mục tối đa công bố trên cổng — xem {@link #daDuyet()}. */
    private static final int SO_MUC_CONG_BO = 20;

    /**
     * Quyền gác toàn bộ việc kiểm duyệt, và cũng là <b>tập người nhận</b> thông báo có mục mới.
     *
     * <p>⭐ Mã quyền này đã nằm trong danh mục RBAC từ {@code V202608131007} và được gán cho
     * {@code CONTENT_MANAGER} — nhưng cho tới lượt này nó ⛔ <b>chưa có endpoint nào</b> đứng sau,
     * nên {@code RbacMatrixTest} liệt nó vào {@code QUYEN_PHASE_SAU}. Lượt này gỡ nó khỏi danh sách
     * ấy; ⛔ đừng thêm lại cho hết đỏ — {@code ngoaiLeQuyenPhaseSauVanConDung()} canh đúng chiều này.
     */
    public static final String QUYEN_KIEM_DUYET = "cms:feedback:manage";

    /** ⚠ Khớp từng chữ với {@code V202609071068}; đọc thêm ở {@code app/gop-y/page.tsx} (luật 14). */
    public static final String KHOA_NHAN_GOP_Y = "site.feedback.enabled";

    public static final String KHOA_CONG_BO = "site.feedback.public-list.enabled";

    private final FeedbackRepository feedbacks;
    private final NotificationPort notifications;
    private final SettingPort settings;
    private final InboundSubmissionGate cong;
    private final PortalCache portalCache;

    public FeedbackService(
            FeedbackRepository feedbacks,
            NotificationPort notifications,
            SettingPort settings,
            InboundSubmissionGate cong,
            PortalCache portalCache) {
        this.feedbacks = feedbacks;
        this.notifications = notifications;
        this.settings = settings;
        this.cong = cong;
        this.portalCache = portalCache;
    }

    /** Công ty có đang nhận góp ý ⛔ không — cổng đọc khoá này để quyết định hiện biểu mẫu. */
    @Transactional(readOnly = true)
    public boolean dangNhanGopY() {
        return settings.getBoolean(KHOA_NHAN_GOP_Y, true);
    }

    /**
     * Ghi nhận một góp ý gửi từ cổng.
     *
     * <p>⚠ Công tắc {@link #KHOA_NHAN_GOP_Y} kiểm ở <b>cả hai đầu</b>: ở giao diện để ⛔ không hiện
     * một biểu mẫu vô dụng, và <b>lại</b> ở đây vì giao diện ⛔ không phải đường vào duy nhất — một
     * lượt {@code curl} thẳng vào endpoint bỏ qua toàn bộ phía trình duyệt (luật 12).
     *
     * @param diem 1..5, hoặc {@code null} khi người gửi chỉ viết góp ý
     * @throws ValidationException {@code SYS-0003} khi thiếu nội dung, quá dài, hoặc điểm ngoài dải
     */
    @Transactional
    public Feedback tiepNhan(String hoTen, String email, Short diem, String noiDung, String maCaptcha) {
        if (!dangNhanGopY()) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail("content", "DA_TAT", "");
        }

        String ten = cong.chuanHoa(hoTen);
        String mail = cong.chuanHoa(email);
        String nd = cong.chuanHoa(noiDung);

        cong.batBuoc(nd, "content");
        cong.gioiHanDai(nd, DAI_TOI_DA_NOI_DUNG, "content");
        cong.gioiHanDai(ten, DAI_TOI_DA_TEN, "fullName");
        cong.gioiHanDai(mail, DAI_TOI_DA_TEN, "email");

        // ⛔ Dải 1..5 cũng nằm ở `ck_feedbacks_rating`. Hai tầng chặn hai loại lỗi khác nhau: tầng
        //   này trả lời được người dùng bằng tên trường, ràng buộc CSDL bịt đường ghi thẳng.
        if (diem != null && (diem < 1 || diem > 5)) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("rating", "NGOAI_DAI", "1..5");
        }

        // ⭐ Captcha đứng CUỐI có chủ đích: một biểu mẫu điền thiếu ⛔ không đáng một lượt gọi mạng
        //   ra Google, và người dân điền thiếu là chuyện thường xuyên hơn nhiều so với bot.
        cong.kiemNguoiThat(maCaptcha);

        Feedback daLuu = feedbacks.save(new Feedback(ten, mail, diem, nd));
        baoCoGopYMoi();
        return daLuu;
    }

    /**
     * Các mục <b>đã duyệt</b> để hiện trên cổng — rỗng khi Công ty tắt công bố.
     *
     * <p>⚠ Trần {@link #SO_MUC_CONG_BO} là một <b>giới hạn nói ra</b>, ⛔ không phải một lượt cắt
     * im lặng: khối trên cổng là một danh sách đọc thoáng qua, ⛔ không phải kho lưu trữ, và nó ⛔
     * không có phân trang. Ngày Công ty cần xem đủ thì đường đúng là màn hình kiểm duyệt (có phân
     * trang, có lọc), ⛔ không phải nâng con số này.
     */
    @Transactional(readOnly = true)
    public List<Feedback> daDuyet() {
        if (!settings.getBoolean(KHOA_CONG_BO, true)) {
            return List.of();
        }
        return feedbacks.daDuyetMoiNhat(PageRequest.of(0, SO_MUC_CONG_BO));
    }

    /**
     * Báo cho người kiểm duyệt.
     *
     * <p>⛔ {@code targeted} chứ ⛔ không {@code alert}: cộng Ban điều hành vào mỗi lượt một người
     * dân chấm sao là cách chắc nhất để vài tuần sau ⛔ không ai đọc thông báo nữa, và lúc đó cảnh
     * báo sự cố thật cũng chết theo.
     *
     * <p>⚠ Thân thông báo ⛔ <b>không</b> mang nội dung góp ý, kể cả một đoạn đầu. Hộp thư thông
     * báo hiện trên màn hình của nhiều người và đây là chữ <b>chưa qua kiểm duyệt</b> — nếu nó có
     * thể đi thẳng lên màn hình cán bộ thì bước duyệt ⛔ không còn đứng trước gì cả.
     */
    private void baoCoGopYMoi() {
        notifications.notify(NotifyRequest.targeted(
                "FEEDBACK_RECEIVED",
                "Có góp ý mới chờ duyệt",
                "Mở màn hình Góp ý & đánh giá để xem và kiểm duyệt.",
                NotifySeverity.INFO,
                QUYEN_KIEM_DUYET,
                List.of()));
    }

    /**
     * ⚠ Gọi bởi {@link FeedbackModerationService} sau mỗi bước chuyển — <b>⛔ không</b> gọi ở
     * {@link #tiepNhan}.
     *
     * <p>Một mục vừa gửi lên đang {@code CHO_DUYET}, tức là ⛔ không có gì đổi trên cổng. Xoá đệm ở
     * đó là đặt một việc dựng lại trang cho mỗi lượt người dân bấm Gửi — cùng loại lãng phí mà
     * {@code PortalCachePort.hydroStationsChanged} đã phải viết hẳn một khối javadoc để chặn.
     */
    void congDaDoi() {
        portalCache.feedbacksChanged();
    }
}
