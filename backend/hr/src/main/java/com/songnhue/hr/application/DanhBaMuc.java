package com.songnhue.hr.application;

import java.util.UUID;

/**
 * Một người trong danh bạ nội bộ — CN-04.6, <b>chỉ thông tin liên hệ công vụ</b>.
 *
 * <h2>⛔⛔ Bất biến của record này: mọi trường ở đây MỌI nhân viên đều đọc được</h2>
 *
 * <p>Danh bạ gác bằng {@code hr:directory:view} — quyền mà seed cấp cho <b>11/12</b> vai trò, gồm
 * cả VIEWER và CLERK. Nó ⛔ <b>không</b> đi qua bộ lọc phạm vi đơn vị (một cuốn danh bạ chỉ thấy
 * đơn vị mình là một cuốn danh bạ vô dụng). ⇒ Thêm một trường vào đây là <b>công bố nó cho toàn
 * Công ty</b>, ⛔ không phải "hiện thêm một cột".
 *
 * <p>⛔ Cấm tuyệt đối: ngày sinh · địa chỉ nhà · email cá nhân · liên hệ khẩn cấp · hôn nhân ·
 * lương · CCCD · số tài khoản. Bảy thứ ấy có mặt trên {@code Employee} và ⛔ không thứ nào là
 * <i>"liên hệ công vụ"</i>. {@code DanhBaKhongLoDuLieuCaNhanTest} đọc
 * {@code getRecordComponents()} và đỏ nếu một tên trường như vậy lọt vào.
 *
 * @param phone số liên hệ <b>công vụ</b>. ⚠ Cột {@code employees.phone} ⛔ không phân biệt số cơ
 *     quan với số cá nhân — đặc tả CN-04.6 xếp nó vào thẻ danh bạ, nên ô ấy <b>là</b> ô công vụ và
 *     màn hình nhập hồ sơ phải nói rõ điều đó với người nhập
 * @param orgUnitId đọc ở giao diện để bấm vào đơn vị là lọc theo đơn vị ấy — ⛔ không phải trường
 *     trang trí
 */
// ⛔⛔ ⛔ KHÔNG có `anhDaiDienId`. Đặc tả CN-04.6 vẽ thẻ có **ảnh**, và bản nháp đầu của record này
//    có ô ấy — nhưng đo ra: `employees` ⛔ không có cột ảnh nào, và thư mục `HoSoThuMuc.ANH` là
//    "ảnh trong hồ sơ nhân sự" (có thể là bản chụp giấy tờ), ⛔ không phải "ảnh đại diện danh bạ".
//    Lấy đại một tệp trong đó đem công bố cho toàn Công ty là một quyết định ⛔ không ai duyệt.
//    ⇒ Một trường LUÔN `null` là đúng thứ luật 15 cấm: nó bày ra giao diện một lời hứa ⛔ không có
//    nguồn. Ghi nợ T55.4, thêm lại khi có nguồn thật.
public record DanhBaMuc(
        UUID publicId,
        String code,
        String fullName,
        String gender,
        String phone,
        String workEmail,
        String jobTitle,
        String positionName,
        UUID orgUnitId,
        String orgUnitName) {}
