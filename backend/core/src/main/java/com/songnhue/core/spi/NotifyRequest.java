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
 * @param permissionScopedToUnits {@code true} ⇒ chỉ người có {@code targetPermission} mà PHẠM VI DỮ LIỆU phủ một
 *     trong {@code relatedOrgUnitIds} — xem {@link #targetedInUnitScope}
 * @param nhomCanhBao {@code true} ⇒ áp luật G11 (nhóm "Ban điều hành" ∪ trưởng/phó đơn vị liên quan).
 *     ⛔⛔ <b>Chỉ {@link #alert} khai {@code true}</b> — T74.7, 20/09/2026. Trước lượt vá ấy
 *     {@code RecipientResolver} <b>suy</b> cờ này từ {@code targetPermission == null}, nên MỌI lượt gửi
 *     ⛔ nhắm đích — kể cả thư <i>"tài khoản của bạn đã bị khoá"</i> và 17 hàng {@code notify_owner} của
 *     quy trình duyệt — đều cộng thêm cả ban lãnh đạo. Chính sách người nhận phải được <b>KHAI RA</b>
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
        List<NotifyChannel> channels,
        boolean permissionScopedToUnits,
        boolean nhomCanhBao) {

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
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL),
                false,
                // ⭐ G11 — factory DUY NHẤT khai true. Xem @param nhomCanhBao ở đầu record (T74.7).
                true);
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
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL),
                false,
                false);
    }

    /**
     * Nhắm đích theo quyền, <b>cộng người đứng đầu những đơn vị đang chịu trách nhiệm</b> — T28.51.
     *
     * <h3>Vì sao {@link #targeted} một mình là chưa đủ</h3>
     *
     * {@code targeted} ghi cứng {@code List.of()} cho {@code relatedOrgUnitIds}, nên một lượt nhắc
     * SLA gửi cho <b>mọi</b> tài khoản có quyền xử lý — kể cả người ⛔ không liên quan tới việc đã
     * được chuyển cho Xí nghiệp khác. Đo được: 40 thư/ngày tới người ⛔ không có việc gì phải làm,
     * và đó đúng là cách một hộp thư học được thói quen bỏ qua cảnh báo.
     *
     * <p>⚠ Đây là phép <b>THU HẸP</b> chứ ⛔ không phải mở rộng: nó thêm đúng những người có trách
     * nhiệm cụ thể, ⛔ không cộng nhóm "Ban điều hành" như {@link #alert}. Xem
     * {@code RecipientResolver#resolve} để biết vì sao ba ca này ⛔ không gộp làm một được.
     *
     * @param orgUnitIds đơn vị đang chịu trách nhiệm; rỗng ⇒ hành vi giống hệt {@link #targeted}
     */
    public static NotifyRequest targetedWithUnits(
            String eventType,
            String title,
            String body,
            NotifySeverity severity,
            String permission,
            List<Long> orgUnitIds,
            List<Long> extraUserIds) {
        return new NotifyRequest(
                eventType,
                title,
                body,
                severity,
                null,
                null,
                null,
                orgUnitIds == null ? List.of() : orgUnitIds,
                extraUserIds == null ? List.of() : extraUserIds,
                permission,
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL),
                false,
                false);
    }

    /**
     * Nhắm đích theo quyền <b>trong phạm vi đơn vị</b> — T57.15: người nhận là người có {@code permission}
     * <b>và</b> phạm vi dữ liệu (đơn vị của tài khoản) PHỦ một trong {@code orgUnitIds}, tức đúng người bộ lọc
     * phạm vi tầng 3 cho THẤY bản ghi.
     *
     * <h3>Vì sao ⛔ dùng {@link #targetedWithUnits}</h3>
     *
     * {@code targetedWithUnits} lấy <b>mọi</b> người có quyền trên toàn Công ty rồi <b>cộng</b> trưởng/phó đơn
     * vị — với đơn nghỉ phép, quản lý của MỌI Xí nghiệp nhận thư về đơn họ ⛔ duyệt được (seed cấp
     * {@code hr:leave:approve} cho 4/12 vai trò). Nhận thư về việc mình ⛔ làm được là cách một hộp thư học
     * được thói quen bỏ qua cảnh báo (§10.76).
     *
     * <p>⚠ Trưởng/phó ⛔ được cộng riêng: họ có quyền và phạm vi thì đã nằm trong tập; ⛔ có thì nhận thư về
     * một việc họ ⛔ làm được.
     *
     * @param orgUnitIds đơn vị của bản ghi; rỗng ⇒ ⛔ ai trong nhóm suy ra (chỉ còn người nêu đích danh)
     */
    public static NotifyRequest targetedInUnitScope(
            String eventType,
            String title,
            String body,
            NotifySeverity severity,
            String permission,
            List<Long> orgUnitIds,
            List<Long> extraUserIds) {
        return new NotifyRequest(
                eventType,
                title,
                body,
                severity,
                null,
                null,
                null,
                orgUnitIds == null ? List.of() : orgUnitIds,
                extraUserIds == null ? List.of() : extraUserIds,
                permission,
                List.of(NotifyChannel.IN_APP, NotifyChannel.EMAIL),
                true,
                false);
    }
}
