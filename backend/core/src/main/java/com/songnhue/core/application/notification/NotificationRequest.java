package com.songnhue.core.application.notification;

import java.util.List;

import com.songnhue.core.domain.notification.NotificationChannel;
import com.songnhue.core.domain.notification.NotificationSeverity;
import com.songnhue.core.spi.ChinhSachNguoiNhan;

/**
 * Một yêu cầu gửi thông báo — đầu vào duy nhất của {@link NotificationService}.
 *
 * <p>Gói thành một record thay vì truyền 8 tham số rời: nơi gọi là mã nghiệp vụ ở khắp các module,
 * và thêm một trường về sau không được phép làm vỡ mọi chỗ gọi.
 *
 * @param eventType mã sự kiện nghiệp vụ, VD {@code HYDRO_THRESHOLD_EXCEEDED}
 * @param relatedOrgUnitIds đơn vị liên quan — nguồn tìm người phụ trách theo G11
 * @param extraUserIds người nhận chỉ định thêm (VD người được giao việc)
 * @param targetPermission gửi cho mọi tài khoản đang hoạt động có quyền này; khai giá trị thì nhóm
 *     "Ban điều hành" <b>không</b> được cộng thêm (xem {@code RecipientResolver})
 * @param channels kênh muốn dùng; kênh đang tắt theo cấu hình sẽ bị bỏ qua, không phải lỗi
 * @param chinhSach cách chọn người nhận — phải được <b>khai ra</b>, ⛔ suy từ hình dạng dữ liệu (T74.7:
 *     phép suy ấy làm thư <i>"tài khoản của bạn đã bị khoá"</i> cộng cả ban lãnh đạo). ⚠ T85.4 đổi hai
 *     {@code boolean} cạnh nhau lấy một giá trị có tên; xem {@link ChinhSachNguoiNhan} — và ⚠ nó là
 *     kiểu <b>dùng chung</b> với bản SPI, ⛔ có bản soi gương như {@code NotificationSeverity}/
 *     {@code NotificationChannel}, vì chính sách ⛔ được ghi xuống bảng nào
 */
public record NotificationRequest(
        String eventType,
        String title,
        String body,
        NotificationSeverity severity,
        String linkUrl,
        String refType,
        Long refId,
        List<Long> relatedOrgUnitIds,
        List<Long> extraUserIds,
        String targetPermission,
        List<NotificationChannel> channels,
        ChinhSachNguoiNhan chinhSach) {

    public NotificationRequest {
        if (chinhSach == null) {
            throw new IllegalArgumentException("chinhSach ⛔ được null — người nhận phải là một lời khai (T85.4)");
        }
        chinhSach.kiemKhopVoiQuyen(targetPermission);
    }

    /** Mặc định hay dùng nhất: cảnh báo nghiệp vụ, gửi cả trên giao diện lẫn email. */
    public static NotificationRequest alert(
            String eventType, String title, String body, NotificationSeverity severity, List<Long> orgUnitIds) {
        return new NotificationRequest(
                eventType,
                title,
                body,
                severity,
                null,
                null,
                null,
                orgUnitIds,
                List.of(),
                null,
                List.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL),
                ChinhSachNguoiNhan.NHOM_CANH_BAO);
    }
}
