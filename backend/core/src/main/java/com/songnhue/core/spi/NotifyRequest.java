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
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL));
    }
}
