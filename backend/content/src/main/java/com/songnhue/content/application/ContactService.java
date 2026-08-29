package com.songnhue.content.application;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.content.domain.Contact;
import com.songnhue.content.domain.ContactStatus;
import com.songnhue.content.infra.ContactRepository;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.AuthenticatedUser;

/**
 * Tiếp nhận liên hệ / phản ánh từ cổng công khai — CN-01.4.
 *
 * <h2>Phạm vi ĐANG dựng, và phần cố ý chưa dựng</h2>
 *
 * Lượt 29/08 dựng đúng vòng khép kín: người dân gửi → bản ghi được lưu → cán bộ đọc được ở màn
 * hình quản trị. Đó là ngưỡng tối thiểu để một biểu mẫu là trung thực; dưới ngưỡng ấy thì
 * *"người dân tin là đã gửi được"* mà thật ra không ai nhận.
 *
 * <p>⛔ Chưa dựng, và ghi ra đây thay vì để im: reCAPTCHA (chặn bởi <b>G13</b>), email báo có
 * liên hệ mới, email xác nhận cho người gửi, bốn trạng thái sau {@code DA_DOC}, phân loại,
 * chuyển phòng ban, ghi chú nội bộ, xuất Excel, nhắc SLA.
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

    private static final int TRANG_TOI_DA = 100;

    private final ContactRepository contacts;

    public ContactService(ContactRepository contacts) {
        this.contacts = contacts;
    }

    /**
     * Ghi nhận một liên hệ gửi từ cổng công khai.
     *
     * @throws ValidationException khi thiếu trường bắt buộc hoặc không có đường liên lạc ngược
     */
    @Transactional
    public Contact tiepNhan(String hoTen, String email, String dienThoai, String chuDe, String noiDung) {
        String ten = chuanHoa(hoTen);
        String mail = chuanHoa(email);
        String dt = chuanHoa(dienThoai);
        String cd = chuanHoa(chuDe);
        String nd = chuanHoa(noiDung);

        batBuoc(ten, "fullName");
        batBuoc(cd, "subject");
        batBuoc(nd, "content");

        // Cùng luật với `ck_contacts_lien_lac`. Hai tầng chặn hai loại lỗi khác nhau: tầng này
        // trả lời được người dùng bằng tên trường cụ thể, ràng buộc CSDL bịt đường ghi thẳng.
        if (mail == null && dt == null) {
            throw (ValidationException)
                    new ValidationException(ErrorCode.SYS_0003).withDetail("email", "LIEN_LAC_BAT_BUOC", "");
        }

        if (nd.length() > DAI_TOI_DA_NOI_DUNG) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003)
                    .withDetail("content", "QUA_DAI", String.valueOf(DAI_TOI_DA_NOI_DUNG));
        }

        return contacts.save(new Contact(ten, mail, dt, cd, nd));
    }

    @Transactional(readOnly = true)
    public Page<Contact> danhSach(ContactStatus loc, int trang, int cor) {
        PageRequest yeuCau = PageRequest.of(Math.max(trang, 0), Math.min(Math.max(cor, 1), TRANG_TOI_DA));
        return loc == null
                ? contacts.findAllByDeletedAtIsNullOrderByCreatedAtDesc(yeuCau)
                : contacts.findAllByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(loc, yeuCau);
    }

    @Transactional(readOnly = true)
    public long demChuaDoc() {
        return contacts.countByStatusAndDeletedAtIsNull(ContactStatus.MOI);
    }

    @Transactional
    public Contact danhDauDaDoc(UUID publicId) {
        Contact c = contacts.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
        c.danhDauDaDoc(nguoiDangDangNhap(), Instant.now());
        return contacts.save(c);
    }

    /** `null` khi không có phiên — không thể xảy ra sau `@RequirePermission`, nhưng không giả định. */
    private static Long nguoiDangDangNhap() {
        return AuthContext.current().map(AuthenticatedUser::userId).orElse(null);
    }

    /**
     * Cắt khoảng trắng hai đầu và <b>loại ký tự điều khiển</b>; rỗng ⇒ {@code null}.
     *
     * <p>Ký tự điều khiển không phải chuyện thẩm mỹ: chúng làm hỏng bản xuất CSV về sau và có
     * thể chèn dòng giả vào nhật ký. Giữ lại {@code \n} và {@code \t} vì nội dung là văn bản
     * nhiều dòng thật.
     */
    private static String chuanHoa(String s) {
        if (s == null) return null;
        String sach = s.replaceAll("[\\p{Cntrl}&&[^\n\t]]", "").trim();
        return sach.isEmpty() ? null : sach;
    }

    private static void batBuoc(String giaTri, String truong) {
        if (giaTri == null) {
            throw (ValidationException) new ValidationException(ErrorCode.SYS_0003).withDetail(truong, "BAT_BUOC", "");
        }
    }
}
