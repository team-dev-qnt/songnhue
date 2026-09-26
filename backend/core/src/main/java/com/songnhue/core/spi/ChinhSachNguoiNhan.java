package com.songnhue.core.spi;

/**
 * <b>Chính sách chọn người nhận</b> của một lượt gửi thông báo — T85.4, 23/09/2026 (trả {@code T82.2}).
 *
 * <h2>Nó thay thế cái gì</h2>
 *
 * <p>Tới 22/09/2026 chính sách ấy được khai bằng <b>hai {@code boolean} đứng liền nhau</b> ở cuối
 * {@code NotifyRequest} và {@code NotificationRequest}: {@code permissionScopedToUnits} rồi
 * {@code nhomCanhBao}. Trình biên dịch ép <b>có mặt</b> nhưng ⛔ ép <b>đúng</b> — hai tham số cùng
 * kiểu thì hoán vị nhau vẫn biên dịch sạch. Đo 23/09: <b>12</b> nơi dựng thô ở {@code src/main} (6
 * factory + 6 nơi gọi) và <b>6</b> trong số đó khai cả hai cờ {@code false}, nên ở đó một lượt đảo
 * chỗ là phép ⛔ làm gì: ⛔ cổng kiểm nào của kho đỏ được.
 *
 * <p>Cái giá thật ⛔ ở sáu chỗ đã viết đúng — nó ở chỗ <b>tiếp theo</b>. Điền {@code true} vào ô sai
 * là lặng lẽ cộng nhóm <i>"Ban điều hành"</i> vào một lá thư riêng, đúng khuyết tật T74.7 từng mất
 * nhiều tuần mới thấy và <b>đang ngủ</b> chứ ⛔ đã chết: khoá
 * {@code notification.alert-group.executive-board} seed {@code '[]'} nên hôm nay phép hợp ⛔ thêm ai.
 *
 * <p>⇒ Một giá trị <b>có tên</b>, cùng khuôn {@code MocSoLieu} (T47.2): một hằng số phải đọc được
 * thành một câu, và ⛔ đảo chỗ được với thứ gì khác.
 *
 * <h2>⚠ Vì sao MỘT kiểu dùng chung, ⛔ phải hai bản soi gương</h2>
 *
 * <p>{@code NotifySeverity}/{@code NotificationSeverity} và {@code NotifyChannel}/
 * {@code NotificationChannel} <b>có</b> hai bản và {@code NotificationService} dịch qua lại. Tiền lệ
 * ấy ⛔ áp ở đây, và lý do đo được: hai kiểu kia là <b>dữ liệu miền</b> — chúng được GHI XUỐNG
 * {@code notifications} / {@code notification_recipients}. Chính sách người nhận thì ⛔: nó bị
 * {@code RecipientResolver} tiêu thụ rồi bỏ, ⛔ cột nào giữ nó. Nhân đôi một kiểu ⛔ ai lưu là dựng
 * đúng thứ luật 14 gọi tên — hai nơi cùng nhớ một luật — để đổi lấy ⛔ gì.
 *
 * @see NotifyRequest
 */
public enum ChinhSachNguoiNhan {

    /**
     * <b>Luật G11</b> — nhóm <i>"Ban điều hành"</i> (đọc từ {@code settings}, có CRUD theo quy tắc
     * 16) ∪ trưởng/phó của {@code relatedOrgUnitIds}.
     *
     * <p>Dùng cho <b>cảnh báo vận hành</b>, nơi hệ thống phải <i>đoán</i> ai nên biết: ⛔ ai "sở hữu"
     * một mực nước vượt ngưỡng. ⚠ Danh sách đơn vị có thể RỖNG — 4/19 điểm đo {@code MN_SONG} ⛔
     * thuộc công trình nào theo thiết kế (T33.8) — và khi ấy nhóm cố định là người nhận DUY NHẤT,
     * nên ⛔ suy chính sách này từ {@code relatedOrgUnitIds.isEmpty()} được.
     *
     * <p>⛔ Đi cùng {@code targetPermission}: xem {@link #kiemKhopVoiQuyen}.
     */
    NHOM_CANH_BAO,

    /**
     * Mọi tài khoản đang hoạt động có {@code targetPermission}, <b>trên toàn Công ty</b>; cộng
     * trưởng/phó của {@code relatedOrgUnitIds} khi nơi gọi có nêu đơn vị (ca thứ ba, T28.51).
     *
     * <p>⚠ Nhóm suy ra <b>thay thế</b> Ban điều hành chứ ⛔ cộng dồn: một lượt bấm <i>"Gửi duyệt"</i>
     * mà cả ban lãnh đạo nhận thư thì vài tuần sau ⛔ ai đọc thông báo nữa, và lúc ấy cảnh báo sự cố
     * thật chết theo.
     */
    THEO_QUYEN,

    /**
     * Người có {@code targetPermission} <b>mà PHẠM VI dữ liệu phủ</b> một trong
     * {@code relatedOrgUnitIds} — tức đúng người bộ lọc phạm vi tầng 3 cho THẤY bản ghi (ca thứ tư,
     * T57.15).
     *
     * <p>⚠ Khác {@link #THEO_QUYEN} ở chiều <b>bớt người</b>: seed cấp {@code hr:leave:approve} cho
     * 4/12 vai trò, nên nhắm theo quyền một mình là gửi cho quản lý của MỌI Xí nghiệp về đơn họ ⛔
     * duyệt được. ⛔ Cộng trưởng/phó riêng — có quyền và có phạm vi thì đã nằm trong tập rồi.
     */
    THEO_QUYEN_TRONG_PHAM_VI,

    /**
     * <b>ĐÚNG những người nơi gọi nêu tên</b>, ⛔ suy thêm ai — T80.7.
     *
     * <p>Dùng khi nơi gọi <i>tự tính được</i> tập người nhận bằng một luật mà {@code core} ⛔ biểu
     * diễn nổi. Ca đầu tiên: người duyệt được một đơn nghỉ = trưởng/phó chuỗi đơn vị ∪ người đang
     * được <b>uỷ quyền</b>, mà {@code UyQuyenDuyetPhep} sống ở {@code hr} nơi {@code core} ⛔ được
     * import (quy tắc 6). Ca thứ hai: thư an ninh của <b>một cá nhân</b> (<i>"tài khoản của bạn đã
     * bị khoá"</i>) và 17 hàng {@code notify_owner} của quy trình duyệt.
     *
     * <p>⚠ Danh sách rỗng ⇒ ⛔ ai nhận, và đó là hành vi ĐÚNG: nơi gọi khai <i>"đúng những người
     * này"</i> thì một tập rỗng là một câu trả lời, ⛔ phải một chỗ để hệ thống đoán bù vào.
     */
    DICH_DANH;

    /** {@code true} ⇒ chính sách này lấy người nhận TỪ {@code targetPermission}. */
    public boolean nhamTheoQuyen() {
        return this == THEO_QUYEN || this == THEO_QUYEN_TRONG_PHAM_VI;
    }

    /**
     * Bắt lỗi <b>ngay tại nơi dựng</b> khi chính sách và {@code targetPermission} mâu thuẫn.
     *
     * <h3>Vì sao NÉM chứ ⛔ bỏ qua trong im lặng</h3>
     *
     * <p>Đây là nửa còn lại của {@code T82.2}: một {@code enum} ép nơi gọi <b>khai ra</b> chính sách,
     * nhưng tự nó vẫn ⛔ ngăn được một lời khai <i>⛔ khớp dữ liệu đi kèm</i>. Hai trạng thái mâu
     * thuẫn ấy hỏng theo chiều <b>im lặng</b>:
     *
     * <ul>
     *   <li>{@link #THEO_QUYEN} mà ⛔ có quyền ⇒ tập suy ra RỖNG ⇒ thông báo tới <b>0 người</b>,
     *       để lại đúng một dòng {@code log.warn} mà ⛔ ai đọc (§10.76 — một con số trên màn hình
     *       ⛔ phải một cái chuông);
     *   <li>{@link #DICH_DANH} mà LẠI có quyền ⇒ nơi gọi tưởng mình đã nhắm đích trong khi tập người
     *       nhận nở ra theo quyền.
     * </ul>
     *
     * <p>Cả hai đều <b>⛔ thể xảy ra nếu ⛔ có người viết sai</b>, nên chỗ đúng để nói là chỗ viết,
     * ⛔ phải ba lớp sau đó. Đo 23/09: cả <b>12</b> nơi dựng ở {@code src/main} và <b>1</b> ở
     * {@code src/test} đều đã khớp, nên bản vá này ⛔ đổi hành vi của lượt gửi nào đang chạy.
     *
     * @param targetPermission mã quyền của cùng yêu cầu; {@code null}/rỗng = ⛔ nhắm theo quyền
     * @throws IllegalArgumentException khi hai vế mâu thuẫn
     */
    public void kiemKhopVoiQuyen(String targetPermission) {
        boolean coQuyen = targetPermission != null && !targetPermission.isBlank();
        if (nhamTheoQuyen() == coQuyen) {
            return;
        }
        throw new IllegalArgumentException(
                nhamTheoQuyen()
                        ? "Chính sách %s đòi targetPermission, nhưng nhận được %s ⇒ thông báo sẽ tới 0 người."
                                .formatted(this, targetPermission == null ? "null" : "chuỗi rỗng")
                        : "Chính sách %s ⛔ dùng targetPermission, nhưng nhận được '%s' ⇒ tập người nhận sẽ nở ra theo quyền."
                                .formatted(this, targetPermission));
    }
}
