package com.songnhue.content.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.SettingPort;

/**
 * Tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4.
 *
 * <h2>Phạm vi ĐANG dựng, và phần cố ý chưa dựng</h2>
 *
 * Lượt 29/08 dựng đúng vòng khép kín: người dân gửi → bản ghi được lưu → cán bộ đọc được ở màn
 * hình quản trị. Đó là ngưỡng tối thiểu để một biểu mẫu là trung thực; dưới ngưỡng ấy thì
 * *"người dân tin là đã gửi được"* mà thật ra không ai nhận.
 *
 * <p>WS-36 (06/09) dựng nốt: <b>quy trình sáu trạng thái</b> qua Workflow engine, phân loại,
 * chuyển phòng ban, ghi chú nội bộ, và chặn xoá khi {@code DANG_XU_LY}.
 *
 * <p>⛔ Vẫn chưa dựng, ghi ra đây thay vì để im: reCAPTCHA (chặn bởi <b>G13</b>), xuất Excel.
 *
 * <h2>⭐ Hai chiều thư của một lượt gửi biểu mẫu — HAI đường khác hẳn nhau</h2>
 *
 * <ul>
 *   <li><b>Báo cho cán bộ</b> ⇒ {@link NotificationPort}: có hộp thư trên giao diện, có "đã đọc",
 *       tôn trọng công tắc bật-tắt kênh, và người nhận phân giải theo <i>quyền</i> nên thêm một cán
 *       bộ mới là xong — ⛔ không phải sửa một danh sách địa chỉ ở đâu đó.
 *   <li><b>Xác nhận cho người gửi</b> ⇒ hàng đợi + {@code MailPort}: họ ⛔ không có
 *       {@code user_id}, nên toàn bộ bộ máy thông báo ⛔ không dùng được. Xem
 *       {@link ContactAckMailHandler}.
 * </ul>
 *
 * <p>⚠ Cả hai đều nằm <b>trong cùng giao dịch</b> với lượt lưu: bản ghi hỏng thì ⛔ không thư nào
 * đi. ⛔ Và cả hai đều ⛔ <b>không</b> gọi SMTP tại chỗ — người dân ⛔ không phải chờ máy chủ thư để
 * biết biểu mẫu đã gửi được.
 *
 * <h2>⭐⭐ Trạng thái đổi DUY NHẤT qua {@link WorkflowPort}</h2>
 *
 * <p>Kể cả {@code MOI → DA_DOC}. Trước WS-36, bước ấy là một phép gán field trong entity — ⛔ không
 * luật ArchUnit nào bắt được, vì luật soi lời gọi {@code applyState()}. Xem javadoc của
 * {@link Contact}.
 *
 * <h2>⛔ Nội dung là VĂN BẢN THUẦN — không bao giờ dựng thành HTML</h2>
 *
 * Đây là chuỗi do người lạ trên Internet nhập. Nó được lưu nguyên văn (cắt ký tự điều khiển và
 * giới hạn độ dài) và nơi hiển thị <b>phải</b> để React escape như văn bản thường. Một
 * {@code dangerouslySetInnerHTML} đặt lên trường này là XSS lưu trữ nhắm thẳng vào người có
 * quyền quản trị — đúng hình dạng luật 12, chỉ khác là nạn nhân có quyền cao hơn.
 *
 * <h2>Chống lạm dụng dựa vào bộ lọc tần suất sẵn có</h2>
 *
 * Tiền tố {@code /api/v1/public} đã đi qua {@code RateLimitFilter} với
 * {@code RateLimitPolicy.PUBLIC}. Không dựng cơ chế đếm thứ hai ở tầng này: hai bộ đếm cho cùng
 * một mục đích là hai nơi phải nhớ, và cái ở dưới sẽ không ai để ý khi cái ở trên đổi.
 */
@Service
public class ContactService {

    /**
     * Giới hạn độ dài nội dung.
     *
     * <p>Cột là {@code TEXT} nên CSDL không chặn gì — không có ngưỡng ở đây thì một lượt gửi có
     * thể nhét vài megabyte vào một hàng, và màn hình quản trị là nơi lãnh hậu quả.
     */
    private static final int DAI_TOI_DA_NOI_DUNG = 5_000;

    /**
     * Quyền gác toàn bộ hộp thư — và cũng là <b>tập người nhận</b> của thư báo có liên hệ mới.
     *
     * <p>⭐ Phân giải người nhận theo <i>quyền</i> chứ ⛔ không theo một danh sách địa chỉ trong
     * {@code settings}: thêm một cán bộ vào hộp thư là gán quyền cho họ, và họ nhận thư ngay. Một
     * danh sách địa chỉ riêng là nơi thứ hai phải nhớ, và nó sẽ lệch (luật 14).
     */
    public static final String QUYEN_XU_LY = "cms:contact:manage";

    /** ⛔ Đọc ở {@link ContactAckMailHandler} nữa — một công tắc, hai nơi đọc, cùng một hằng. */
    public static final String KHOA_THU_XAC_NHAN = "cms.contact.ack-email-enabled";

    /** Hạn xử lý (giờ); {@code 0} = tắt nhắc. Đọc ở {@link ContactSlaHandler}. */
    public static final String KHOA_HAN_SLA_GIO = "cms.contact.sla-hours";

    private final ContactRepository contacts;
    private final NotificationPort notifications;
    private final JobPort jobs;
    private final SettingPort settings;
    private final ContactFormPolicy luatBieuMau;
    private final InboundSubmissionGate cong;

    public ContactService(
            ContactRepository contacts,
            NotificationPort notifications,
            JobPort jobs,
            SettingPort settings,
            ContactFormPolicy luatBieuMau,
            InboundSubmissionGate cong) {
        this.contacts = contacts;
        this.notifications = notifications;
        this.jobs = jobs;
        this.settings = settings;
        this.luatBieuMau = luatBieuMau;
        this.cong = cong;
    }

    /**
     * Ghi nhận một liên hệ gửi từ cổng công khai.
     *
     * @throws ValidationException khi thiếu trường bắt buộc hoặc không có đường liên lạc ngược
     */
    @Transactional
    public Contact tiepNhan(
            String hoTen, String email, String dienThoai, String chuDe, String noiDung, String maCaptcha) {
        String ten = cong.chuanHoa(hoTen);
        String mail = cong.chuanHoa(email);
        String dt = cong.chuanHoa(dienThoai);
        String cd = cong.chuanHoa(chuDe);
        String nd = cong.chuanHoa(noiDung);

        // --- T28.49: ô đã TẮT thì ⛔ không thể bắt buộc ------------------------
        //
        // ⛔ Bắt buộc một trường mà biểu mẫu ⛔ không còn ô để điền là dựng một cánh cửa khoá:
        //   người dân điền xong tất cả những gì nhìn thấy rồi nhận một lỗi ⛔ không sửa được.
        //   Cùng hình dạng `dienThoaiBatBuoc()` đã chặn ở T36.7 — chỉ đổi trường.
        //
        // ⚠ `content` cố ý ⛔ KHÔNG tắt được: một phản ánh ⛔ không có nội dung thì ⛔ không có gì
        //   để xử lý. Đó ⛔ không phải "nặc danh", đó là một hàng rác.
        if (luatBieuMau.hienHoTen()) {
            cong.batBuoc(ten, "fullName");
        }
        if (luatBieuMau.hienTieuDe()) {
            cong.batBuoc(cd, "subject");
        }
        cong.batBuoc(nd, "content");

        // --- T36.7: trường bắt buộc theo CẤU HÌNH -----------------------------
        //
        // ⚠ Kiểm hai khoá này TRƯỚC vế "ít nhất một" ở dưới, để thông điệp trả về nêu đúng ô mà
        //   Công ty đã đặt là bắt buộc. Đảo thứ tự thì người dùng bỏ trống email (đang bắt buộc)
        //   nhưng có điền điện thoại sẽ đi lọt, và lỗi chỉ hiện ra ở lượt rà dữ liệu nhiều tháng sau.
        if (luatBieuMau.emailBatBuoc() && mail == null) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail("email", "BAT_BUOC", "");
        }
        if (luatBieuMau.dienThoaiBatBuoc() && dt == null) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail("phone", "BAT_BUOC", "");
        }

        // Cùng luật với `ck_contacts_lien_lac`. Hai tầng chặn hai loại lỗi khác nhau: tầng này
        // trả lời được người dùng bằng tên trường cụ thể, ràng buộc CSDL bịt đường ghi thẳng.
        if (mail == null && dt == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("email", "LIEN_LAC_BAT_BUOC", "");
        }

        cong.gioiHanDai(nd, DAI_TOI_DA_NOI_DUNG, "content");

        // --- T36.6: reCAPTCHA, và nó đứng CUỐI có chủ đích ---------------------
        //
        // ⭐ Một biểu mẫu điền thiếu ⛔ không đáng một lượt gọi mạng ra Google — và người dân điền
        //   thiếu là chuyện thường xuyên hơn nhiều so với bot. Đặt ở đây thì lượt gọi ấy chỉ xảy ra
        //   với những gì đã hợp lệ về mặt dữ liệu.
        //
        // ⛔ `InboundSubmissionGate.captchaBatBuoc()` trả `false` ở CẢ HAI trạng thái "chưa bật" và
        //   "bật mà thiếu khoá bí mật" — nhưng chỉ trạng thái thứ hai ghi ERROR.
        cong.kiemNguoiThat(maCaptcha);

        Contact daLuu = contacts.save(new Contact(ten, mail, dt, cd, nd));
        baoCoLienHeMoi(daLuu);
        datThuXacNhan(daLuu);
        return daLuu;
    }

    /**
     * Báo cho cán bộ — T36.3.
     *
     * <p>⛔ {@code targeted} chứ ⛔ không {@code alert}: {@code alert} cộng thêm nhóm "Ban điều
     * hành" vì ở đó hệ thống <i>đoán</i> ai nên biết. Ở đây ta <b>biết chính xác</b> — người có
     * {@code cms:contact:manage}. Cộng cả ban lãnh đạo vào mỗi lượt người dân điền biểu mẫu là cách
     * chắc chắn nhất để vài tuần sau ⛔ không ai đọc thông báo nữa, và lúc đó cảnh báo sự cố thật
     * cũng chết theo.
     *
     * <p>⚠ Tiêu đề ⛔ <b>không</b> mang tên người gửi: hộp thư thông báo hiện trên màn hình của
     * nhiều người, và nội dung liên hệ có thể là một khiếu nại đích danh. Ai cần biết thì mở hộp
     * thư — nơi đã có kiểm quyền.
     */
    private void baoCoLienHeMoi(Contact c) {
        notifications.notify(NotifyRequest.targeted(
                "CONTACT_RECEIVED",
                "Có liên hệ mới từ cổng thông tin",
                "Chủ đề: " + c.getSubject(),
                NotifySeverity.INFO,
                QUYEN_XU_LY,
                List.of()));
    }

    /**
     * Đặt việc gửi thư xác nhận cho người gửi — T36.3.
     *
     * <p>⚠ Kiểm công tắc ở <b>cả hai đầu</b>: ở đây để ⛔ không đặt việc thừa, và lại một lần nữa
     * trong {@link ContactAckMailHandler} vì một việc có thể nằm chờ qua đêm và người tắt nó sáng
     * hôm sau mong nó có hiệu lực ngay.
     *
     * <p>⛔ Payload mang <b>mã công khai</b>, ⛔ không mang địa chỉ email — xem javadoc của handler.
     */
    private void datThuXacNhan(Contact c) {
        if (c.getEmail() == null || !settings.getBoolean(KHOA_THU_XAC_NHAN, true)) {
            return;
        }
        jobs.enqueue(new JobRequest(
                CmsJobTypes.CONTACT_ACK_MAIL,
                "{\"contactPublicId\":\"%s\"}".formatted(c.getPublicId()),
                // ⚠ Khoá chống trùng theo chính bản ghi: một lượt gửi biểu mẫu ⇒ tối đa một thư.
                CmsJobTypes.CONTACT_ACK_MAIL + ":" + c.getPublicId(),
                (short) 3));
    }
}
