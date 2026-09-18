package com.songnhue.core.spi;

/**
 * Ghi sự kiện bảo mật từ một module nghiệp vụ — <b>nửa còn thiếu của {@code conventions.md} §4.7</b>.
 *
 * <h2>Vì sao cổng này phải tồn tại</h2>
 *
 * <p>§4.7 bắt buộc: mọi lần tạo/sửa/xoá một credential bên thứ ba phải để lại một dòng trong
 * {@code security_events}. Nhưng bộ ghi ({@code SecurityEventService}) nằm ở
 * {@code core.application.auth}, mà quy tắc 6 chỉ cho module khác import {@code spi/} của
 * {@code core} — nên trước cổng này, module {@code hydro} <b>không có đường nào</b> để tuân thủ
 * chính điều luật áp lên nó. Đó đúng dấu hiệu "SPI khai thiếu" mà {@code conventions.md} §1.1 nói
 * tới; ⛔ cách xử lý <b>sai</b> là nới ArchUnit cho import {@code core.application.*}.
 *
 * <h2>SPI mỏng, đặt tên theo đúng việc — không có {@code record(type, detail)} tự do</h2>
 *
 * <p>Một cổng nhận {@code SecurityEventType} bất kỳ sẽ biến bảng liệt kê sự kiện của {@code core}
 * thành thứ mọi module phải nhớ đúng, và nó cũng mở đường cho một module ghi nhầm loại sự kiện của
 * module khác — cùng lý lẽ với {@link PortalCachePort} và với {@link SettingAdminPort} bắt buộc khai
 * {@code groupCode}.
 *
 * <p>⛔ <b>Không tham số nào ở đây được mang giá trị bí mật.</b> Chỉ mã nguồn và tên hành động; nội
 * dung credential không bao giờ rời khỏi chỗ giải mã.
 */
public interface SecurityEventPort {

    /**
     * Mã số truy cập một nguồn dữ liệu bên ngoài vừa đổi.
     *
     * @param sourceCode mã nguồn, ví dụ {@code BHH40}
     * @param action việc vừa làm, dạng ngắn: {@code DAT_LAN_DAU} / {@code THAY} / {@code XOA}
     */
    void externalCredentialChanged(String sourceCode, String action);

    /**
     * Không giải mã được credential của một nguồn — bản mã và khoá AES hiện tại không khớp.
     *
     * @param sourceCode mã nguồn
     * @param keyId id khoá ghi kèm bản mã, để đối chiếu với khoá đang hoạt động
     */
    void externalCredentialDecryptFailed(String sourceCode, String keyId);

    /**
     * Ai đó vừa đọc trường 🔒 của một hồ sơ nhân sự (NĐ 13/2023, CN-04.7).
     *
     * <p>⛔ Chỉ truyền <b>mã nhân viên</b>. Giá trị vừa đọc ⛔ không bao giờ rời khỏi chỗ giải mã —
     * cùng luật với {@link #externalCredentialChanged}.
     *
     * @param employeeCode mã CBNV, ví dụ {@code NV-2019-001}
     */
    void hrSensitiveFieldsRead(String employeeCode);

    /**
     * Ai đó vừa <b>tải trọn hồ sơ tài liệu</b> của một CBNV dạng ZIP (NĐ 13/2023, CN-04.5).
     *
     * <h2>⛔⛔ Vì sao một lượt TẢI TỆP lại là sự kiện BẢO MẬT</h2>
     *
     * <p>{@code audit_logs} chỉ sinh dòng khi có <b>thay đổi</b>, nên mọi lượt <i>đọc</i> là vô
     * hình — T51.6 đã trả giá cho đúng chuyện này ở trường 🔒. Ở đây còn nặng hơn một bậc: một
     * lượt bấm mang **cả** hợp đồng, quyết định, bằng cấp và giấy tờ tuỳ thân của một con người ra
     * khỏi hệ thống, trong một tệp. Sau khi tệp rời máy chủ thì hệ ⛔ không kiểm soát được gì nữa.
     *
     * <p>⚠ Đối chiếu có ý thức với {@code EXTERNAL_CREDENTIAL_CHANGED}, nơi lượt *"đã dùng"* bị bỏ
     * vì poller gọi 720 lần/ngày: ở đây <b>số lượng</b> ngược hẳn — một hồ sơ được tải trọn vài lần
     * một năm. Chính số lượng, ⛔ không phải nguyên tắc, quyết định khác nhau giữa hai chỗ.
     *
     * @param employeeCode mã CBNV
     * @param soTep số tệp trong bản nén — ⛔ {@code 0} là một trạng thái CÓ THẬT (hồ sơ chưa có tài
     *     liệu nào) và vẫn phải ghi: nó phân biệt *"tải về rỗng"* với *"⛔ không ai tải"*
     */
    void hrDossierDownloaded(String employeeCode, int soTep);
}
