package com.songnhue.core.application.org;

import java.util.UUID;

/**
 * Phần thông tin <b>⛔ bắt buộc</b> của một đơn vị — liên hệ và người đứng đầu.
 *
 * <h2>Vì sao gom thành một record</h2>
 *
 * <p>{@code create}/{@code update} của {@link OrgUnitService} đã mang 8 và 7 tham số. Checkstyle
 * chặn ở <b>8</b> ({@code ParameterNumber}), nên thêm hai ô trưởng/phó là vượt trần — đúng chỗ WS-74
 * đã trả giá khi thêm một thành phần vào {@code NotifyRequest}. Gom lại còn giết một lớp lỗi khác:
 * năm tham số liên tiếp cùng kiểu {@code String}/{@code UUID} thì đổi chỗ hai cái là <b>biên dịch
 * sạch</b> và địa chỉ đi vào ô điện thoại.
 *
 * @param address địa chỉ hiển thị trên cổng công khai (CR-26)
 * @param phone số điện thoại hiển thị trên cổng
 * @param email hòm thư hiển thị trên cổng
 * @param truongPublicId tài khoản <b>trưởng</b> đơn vị — {@code null} = chưa có, và <i>chưa có</i>
 *     phải phân biệt được với <i>đã chọn</i> (quy tắc 16). ⛔ Đây ⛔ phải một ô trang trí: nó là
 *     nguồn người nhận cảnh báo ngưỡng của <b>G11</b>
 *     ({@code OrgUnitRepository.findActiveHeadAndDeputyUserIds})
 * @param phoPublicId tài khoản <b>phó</b> đơn vị — cùng vai trò nhận cảnh báo với trưởng
 */
public record ThongTinDonVi(String address, String phone, String email, UUID truongPublicId, UUID phoPublicId) {

    /** Dạng ⛔ có thông tin phụ nào — dùng cho nơi gọi chỉ quan tâm mã, tên và vị trí trong cây. */
    public static ThongTinDonVi trong() {
        return new ThongTinDonVi(null, null, null, null, null);
    }
}
