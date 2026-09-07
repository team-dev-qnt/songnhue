package com.songnhue.core.spi;

/**
 * Mức độ của một thông báo, theo cách nhìn của module nghiệp vụ.
 *
 * <p><b>Vì sao không dùng thẳng enum của tầng domain.</b> Enum ở {@code core.domain.notification}
 * ánh xạ xuống cột trong CSDL; nếu module nghiệp vụ import nó thì hợp đồng SPI bị trói vào mô hình
 * lưu trữ của {@code core} — đổi cách lưu là vỡ mọi module. Đây là bản sao có chủ đích.
 *
 * <p>Cái giá của bản sao là nguy cơ trôi lệch, nên có {@code NotificationEnumParityTest} canh: hai
 * enum phải trùng khít tên hằng. Thêm mức ở một bên mà quên bên kia là <b>CI đỏ</b>, không phải một
 * lỗi ánh xạ phát hiện lúc chạy.
 */
public enum NotifySeverity {
    INFO,
    WARNING,
    DANGER,
    CRITICAL
}
