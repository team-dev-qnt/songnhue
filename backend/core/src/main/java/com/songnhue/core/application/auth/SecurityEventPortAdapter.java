package com.songnhue.core.application.auth;

import org.springframework.stereotype.Component;

import com.songnhue.core.domain.security.SecurityEventType;
import com.songnhue.core.spi.SecurityEventPort;

/**
 * Cài đặt {@link SecurityEventPort} — cầu nối để module nghiệp vụ ghi được sự kiện bảo mật.
 *
 * <p>Hợp đồng ở {@code core.spi}, cài đặt ở đây, đúng khuôn {@code HydroAlertPort} /
 * {@code PortalCachePort}: module gọi chỉ thấy interface, không thấy {@link SecurityEventService}.
 *
 * <p>⚠ {@code username}/{@code userId} để {@code null} là <b>đúng</b> cho nhóm sự kiện này: đổi
 * credential có thể do người dùng bấm trên UI, nhưng cũng có thể do tiến trình mồi lúc khởi động
 * (không có ai đăng nhập). Ai làm thì đã nằm ở nhật ký kiểm toán của bảng {@code api_sources}; nhét
 * một cái tên giả kiểu {@code "system"} vào đây chỉ làm cột {@code username} thôi đáng tin.
 */
@Component
public class SecurityEventPortAdapter implements SecurityEventPort {

    private final SecurityEventService events;

    public SecurityEventPortAdapter(SecurityEventService events) {
        this.events = events;
    }

    @Override
    public void externalCredentialChanged(String sourceCode, String action) {
        events.record(
                SecurityEventType.EXTERNAL_CREDENTIAL_CHANGED,
                null,
                null,
                ClientInfo.unknown(),
                chiTiet(sourceCode, "action", action));
    }

    @Override
    public void externalCredentialDecryptFailed(String sourceCode, String keyId) {
        events.record(
                SecurityEventType.EXTERNAL_CREDENTIAL_DECRYPT_FAILED,
                null,
                null,
                ClientInfo.unknown(),
                chiTiet(sourceCode, "keyId", keyId));
    }

    @Override
    public void hrSensitiveFieldsRead(String employeeCode) {
        // ⚠ Ở đây username/userId KHÔNG được để null như hai sự kiện credential: câu hỏi của dòng
        // nhật ký này chính là "AI đọc". Bối cảnh lấy từ phiên đang chạy — luôn có, vì endpoint gác
        // bằng `hr:employee:view-sensitive` nên ⛔ không có đường vô danh nào tới được.
        events.record(
                SecurityEventType.HR_SENSITIVE_FIELDS_READ,
                nguoiDangDangNhap(),
                idNguoiDangDangNhap(),
                ClientInfo.unknown(),
                "{\"employeeCode\":\"" + thoat(employeeCode) + "\"}");
    }

    private static String nguoiDangDangNhap() {
        return com.songnhue.core.common.security.AuthContext.current()
                .map(com.songnhue.core.common.security.AuthenticatedUser::username)
                .orElse(null);
    }

    private static Long idNguoiDangDangNhap() {
        return com.songnhue.core.common.security.AuthContext.current()
                .map(com.songnhue.core.common.security.AuthenticatedUser::userId)
                .orElse(null);
    }

    /**
     * Dựng {@code detail} dạng JSON.
     *
     * <p>⛔ Chỉ nhận hai giá trị ngắn do lớp này kiểm soát, cố ý: {@code detail} là chỗ dễ nhất để
     * một giá trị bí mật lọt vào nhật ký, và nhật ký bảo mật lưu 5 năm.
     */
    private String chiTiet(String sourceCode, String ten, String giaTri) {
        return "{\"source\":\"" + thoat(sourceCode) + "\",\"" + ten + "\":\"" + thoat(giaTri) + "\"}";
    }

    private String thoat(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
