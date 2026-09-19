package com.songnhue.core.application.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.domain.identity.User;

/**
 * Băm mật khẩu và kiểm chính sách độ mạnh (T5.7, M5.15).
 *
 * <p><b>BCrypt cost 12</b> (§4.1): mỗi lần băm tốn khoảng 0,3 giây — chậm có chủ đích. Với người
 * dùng, 0,3 giây khi đăng nhập là không cảm nhận được; với kẻ dò mật khẩu từ bảng hash bị lộ, nó
 * biến hàng tỉ phép thử mỗi giây thành vài phép thử mỗi giây.
 *
 * <p>Tham số chính sách (độ dài tối thiểu, bắt buộc chữ + số) đọc từ bảng {@code settings} để Admin
 * sửa được trên UI — quy tắc 12 của dự án. Riêng <b>cost của BCrypt thì không</b>: đó là tham số an
 * toàn mật mã, hạ xuống bằng UI là mở toang cửa mà không ai nhận ra.
 */
@Service
public class PasswordPolicyService {

    /** ≥ 12 theo conventions.md §4.1. Nâng lên khi phần cứng nhanh hơn, không bao giờ hạ xuống. */
    private static final int BCRYPT_COST = 12;

    /**
     * Băm giả để so sánh khi tài khoản không tồn tại.
     *
     * <p>Không có nó thì thời gian trả lời tự tố cáo: tài khoản có thật mất ~300 ms để băm mật khẩu,
     * tài khoản không có thì trả về tức thì. Đo vài lần là dựng được danh sách tài khoản có thật —
     * đúng thứ mà message chung {@code AUTH-0001} sinh ra để che.
     */
    private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEe.iUKMFkjfRWZ2PYlpvHrKgqQGKuqQwmy";

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(BCRYPT_COST);
    private final SettingService settings;

    public PasswordPolicyService(SettingService settings) {
        this.settings = settings;
    }

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String storedHash) {
        return encoder.matches(rawPassword, storedHash);
    }

    /**
     * Gán một MẬT KHẨU TẠM do quản trị phát — T73.8 (ASVS 2.3.1).
     *
     * <p>Một nơi cho cả bốn trường: hash · mốc đổi · cờ buộc đổi · HẠN. Hai đường phát mật khẩu tạm (tạo tài khoản ·
     * đặt lại) cùng gọi đây, để đường thứ ba ra đời ⛔ quên hạn (luật 12). Trước bản vá mật khẩu tạm ⛔ có hạn: đo
     * 19/09/2026, một mật khẩu tạm phát từ 30 ngày trước vẫn đăng nhập được.
     *
     * <p>⛔ Bootstrap {@code superadmin} cố ý ⛔ đi qua đây ({@code AdminBootstrapRunner}): hết hạn ở đó thì ⛔ còn ai
     * đặt lại được. Người gọi phải {@link #validate} trước — hàm này ⛔ kiểm độ mạnh.
     */
    public void ganMatKhauTam(User user, String matKhauTam, Instant now) {
        user.setPasswordHash(hash(matKhauTam));
        user.setPasswordChangedAt(now);
        user.setMustChangePassword(true);
        user.setTempPasswordExpiresAt(now.plus(hanDungMatKhauTam()));
    }

    /** Số giờ ở {@code security.password.temp-ttl-hours}, kẹp {@code [1; 720]} như cột {@code validation} của khoá. */
    public Duration hanDungMatKhauTam() {
        int gio = settings.getInt(SettingKeys.PASSWORD_TEMP_TTL_HOURS, SettingKeys.DEFAULT_PASSWORD_TEMP_TTL_HOURS);
        return Duration.ofHours(Math.max(1, Math.min(TRAN_GIO_MAT_KHAU_TAM, gio)));
    }

    /** Trần của {@link #hanDungMatKhauTam} — phải bằng {@code max=} ở cột {@code validation} của khoá (V202609201091). */
    public static final int TRAN_GIO_MAT_KHAU_TAM = 720;

    /** Tiêu tốn đúng lượng thời gian như một lần kiểm mật khẩu thật, rồi trả về sai. */
    public void wasteTimeToHideMissingUser(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, DUMMY_HASH);
    }

    /**
     * Chính sách độ mạnh đang có hiệu lực — đọc từ {@code settings}, để FE nói ra được YÊU CẦU
     * THẬT thay vì một câu chung chung.
     *
     * <p>⛔ Không có bản ghi này thì màn hình chỉ nói được *"mật khẩu chưa đạt yêu cầu an toàn"*,
     * và người dùng phải đoán xem *yêu cầu* là gì. Ghi cứng "ít nhất 10 ký tự" vào giao diện thì
     * còn tệ hơn: hai con số ở hai nơi, và cái ở giao diện <b>nói dối</b> ngay khi Admin sửa
     * tham số — đúng lớp lỗi §10.69 (một tham số cấu hình nói dối khó thấy hơn một tham số không
     * ai đọc).
     */
    public record ChinhSachMatKhau(int minLength, boolean requireLetterAndDigit) {}

    public ChinhSachMatKhau chinhSach() {
        return new ChinhSachMatKhau(
                settings.getInt(SettingKeys.PASSWORD_MIN_LENGTH, SettingKeys.DEFAULT_PASSWORD_MIN_LENGTH),
                settings.getBoolean(SettingKeys.PASSWORD_REQUIRE_LETTER_AND_DIGIT, true));
    }

    /**
     * Kiểm mật khẩu mới có đạt chính sách không.
     *
     * <h2>⚠⚠ Vì sao {@code tenTruong} là THAM SỐ, không phải hằng số "newPassword"</h2>
     *
     * Bản trước ghi cứng {@code "newPassword"} vào mọi {@code withDetail}. Nó đúng với đúng một
     * trong ba nơi gọi. Ở màn hình *Thêm tài khoản*, DTO khai trường là
     * {@code temporaryPassword}, nên chi tiết lỗi trỏ vào một trường <b>không tồn tại trên
     * biểu mẫu</b>; {@code Form.setFields} của AntD bỏ qua tên lạ <b>trong im lặng</b>, và nhánh
     * xử lý ở FE {@code return} ngay sau đó nên không có cả toast.
     *
     * <p>Kết quả đo được: HTTP <b>422</b> với lý do đầy đủ trong thân phản hồi, và màn hình
     * <b>không hiện gì cả</b> — người dùng bấm "Tạo", không thấy gì xảy ra, bấm tiếp. Chính
     * javadoc của {@code ApiClientError.fieldErrors} đã cảnh báo: *"nếu hai bên lệch tên thì AntD
     * lặng lẽ bỏ qua dòng đó… lệch tên trường lộ ra ngay lần thử đầu tiên"*. Nó <b>không</b> lộ
     * ra — vì cả hai lớp cùng im lặng.
     *
     * <p>⛔ Nên tên trường phải do <b>nơi gọi</b> khai: chỉ nơi gọi biết DTO của mình. Đây là
     * quy tắc 12 ở dạng ngược — bảo đảm không đặt được ở chỗ dữ liệu đi qua, thì tham số hoá nó
     * để trình biên dịch bắt người thêm nơi gọi thứ tư phải trả lời câu hỏi ấy.
     *
     * @param tenTruong tên trường trong DTO của <b>nơi gọi</b> — sẽ đi thẳng ra
     *     {@code error.details[].field} và FE dùng nó để tô đỏ đúng ô
     * @throws BusinessRuleException {@code AUTH-0006} kèm chi tiết theo trường để FE chỉ đúng chỗ sai
     */
    public void validate(String rawPassword, String username, String tenTruong) {
        ChinhSachMatKhau chinhSach = chinhSach();

        BusinessRuleException error = new BusinessRuleException(ErrorCode.AUTH_0006);
        boolean failed = false;

        if (rawPassword == null || rawPassword.length() < chinhSach.minLength()) {
            // ⚠ rejectedValue để null — giá trị này đi thẳng ra response, không được là mật khẩu
            error.withDetail(tenTruong, "MIN_LENGTH_" + chinhSach.minLength(), null);
            failed = true;
        }
        if (chinhSach.requireLetterAndDigit() && !hasLetterAndDigit(rawPassword)) {
            error.withDetail(tenTruong, "REQUIRE_LETTER_AND_DIGIT", null);
            failed = true;
        }
        if (rawPassword != null
                && username != null
                && rawPassword
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains(username.toLowerCase(java.util.Locale.ROOT))) {
            // Không có trong spec nhưng là bước rẻ nhất chặn "admin/admin123" — kiểu mật khẩu bị dò
            // đầu tiên trong mọi đợt tấn công
            error.withDetail(tenTruong, "MUST_NOT_CONTAIN_USERNAME", null);
            failed = true;
        }

        if (rawPassword != null && rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > TRAN_BYTE) {
            error.withDetail(tenTruong, "MAX_BYTES_" + TRAN_BYTE, null);
            failed = true;
        }
        if (failed) {
            throw error;
        }
    }

    /**
     * Trần mật khẩu tính theo <b>BYTE</b>, ⛔ theo ký tự — <b>T61.40</b> (ASVS 2.1.2).
     *
     * <p>{@code BCrypt.hashpw} của spring-security-crypto <b>NÉM</b> {@code IllegalArgumentException}
     * ("password cannot be more than 72 bytes") — ⛔ cắt ngắn trong im lặng như các bản cũ. Ngoại lệ ấy
     * rơi xuống {@code GlobalExceptionHandler} thành <b>500</b> kèm *"Lỗi hệ thống, vui lòng thử lại"*.
     *
     * <p>⚠⚠ Và nó bắn ở ngưỡng rất DỄ CHẠM với tiếng Việt: chữ có dấu chiếm 2–3 byte UTF-8, nên một
     * cụm mật khẩu ~30 ký tự có dấu đã vượt 72 byte. Người dùng làm đúng lời khuyên *"đặt mật khẩu
     * dài"* là người gặp lỗi 500.
     *
     * <p>⇒ Kiểm ở đây, trả {@code AUTH-0006} kèm chi tiết {@code MAX_BYTES_72} để màn hình nói được
     * yêu cầu thật. 72 byte vẫn thoả ASVS 2.1.2 (≥ 64 ký tự) cho mật khẩu ASCII.
     */
    public static final int TRAN_BYTE = 72;

    private static boolean hasLetterAndDigit(String value) {
        if (value == null) {
            return false;
        }
        boolean letter = false;
        boolean digit = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetter(c)) {
                letter = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            }
            if (letter && digit) {
                return true;
            }
        }
        return false;
    }
}
