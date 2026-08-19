package com.songnhue.core.spi;

import java.util.List;

/**
 * Một yêu cầu gửi thông báo — pattern P4.
 *
 * @param eventType mã sự kiện nghiệp vụ, VD {@code ARTICLE_SUBMITTED}, {@code INCIDENT_OPENED}
 * @param relatedOrgUnitIds đơn vị liên quan — nguồn tìm người phụ trách theo G11. Người nhận cuối
 *     cùng = nhóm "Ban điều hành" cấu hình được ∪ người đứng đầu các đơn vị này, đã khử trùng lặp và
 *     loại tài khoản khoá
 * @param extraUserIds người nhận chỉ định thêm (VD người được giao việc)
 * @param targetPermission gửi cho mọi tài khoản đang hoạt động có quyền này. Khai giá trị ở đây thì
 *     nhóm "Ban điều hành" <b>không</b> được cộng thêm — xem {@link #targeted}
 * @param channels kênh muốn dùng; kênh đang tắt theo cấu hình sẽ bị bỏ qua
 */
public record NotifyRequest(
        String eventType,
        String title,
        String body,
        NotifySeverity severity,
        String linkUrl,
        String refType,
        Long refId,
        List<Long> relatedOrgUnitIds,
        List<Long> extraUserIds,
        String targetPermission,
        List<NotifyChannel> channels) {

    /** Dạng hay dùng nhất: cảnh báo nghiệp vụ, gửi cả trên giao diện lẫn email. */
    public static NotifyRequest alert(
            String eventType, String title, String body, NotifySeverity severity, List<Long> orgUnitIds) {
        return new NotifyRequest(
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
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL));
    }

    /**
     * Thông báo <b>nhắm đích</b>: gửi cho người có quyền {@code permission}, cộng những người nêu
     * đích danh.
     *
     * <p>Dùng cho quy trình duyệt, nơi ta biết chính xác ai cần biết. Khác hẳn {@link #alert}: ở đó
     * hệ thống đoán ai nên biết nên mới cộng cả nhóm Ban điều hành.
     */
    public static NotifyRequest targeted(
            String eventType,
            String title,
            String body,
            NotifySeverity severity,
            String permission,
            List<Long> extraUserIds) {
        return new NotifyRequest(
                eventType,
                title,
                body,
                severity,
                null,
                null,
                null,
                List.of(),
                extraUserIds == null ? List.of() : extraUserIds,
                permission,
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL));
    }
}
