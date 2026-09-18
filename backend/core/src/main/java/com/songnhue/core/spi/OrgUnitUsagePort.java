package com.songnhue.core.spi;

import java.util.List;

/**
 * <b>Ai đang thuộc đơn vị này</b> — chốt chặn cho lượt giải thể / xoá đơn vị (CN-04.1).
 *
 * <h2>⛔⛔ Đặc tả đòi một bảo đảm mà mã chỉ dựng được MỘT PHẦN BA</h2>
 *
 * <p>{@code function-spec.md:616} viết: <i>"giải thể/xóa đơn vị chỉ khi <b>⛔ không còn nhân
 * viên/công trình liên kết</b>"</i>. Đo 10/09/2026: {@code OrgUnitService.delete} kiểm <b>đơn vị
 * cấp dưới</b> và <b>tài khoản người dùng</b> — hai thứ đặc tả ⛔ <b>không</b> nêu — và ⛔
 * <b>không kiểm</b> hai thứ đặc tả nêu đích danh. Guard ấy viết ở Phase 0, khi {@code employees}
 * và {@code constructions} còn chưa tồn tại; nó ⛔ không sai lúc viết, nó <b>hết đúng</b> khi kho
 * lớn lên.
 *
 * <p>⚠ Đo tiếp: có <b>13 cột khoá ngoại</b> trỏ vào {@code org_units} trên <b>5 module</b>. Một
 * guard liệt kê tay sẽ luôn thiếu cái thứ 14 — đúng hình dạng luật 28 (*phạm vi phải do bộ canh
 * ĐO, ⛔ không do người viết gõ*).
 *
 * <h2>Vì sao là một cổng NHIỀU BÊN CÀI, đúng khuôn {@link AttachmentUsagePort}</h2>
 *
 * <p>{@code core} ⛔ không được biết {@code employees} hay {@code constructions} tồn tại. Đảo
 * chiều: mỗi module <b>tự khai</b> phần của mình, Spring gom mọi bean thành một {@code List}, và
 * một module mới chỉ cần thêm <b>một</b> bean — ⛔ không phải sửa {@code core}. Cùng khuôn
 * {@code JobHandler} của {@code JobWorker}.
 *
 * <h2>⚠ Vì sao xoá MỀM vẫn phải chặn</h2>
 *
 * <p>{@code delete} chỉ đặt {@code deleted_at}, nên khoá ngoại ⛔ <b>không</b> nổ và ⛔ không hàng
 * nào mồ côi theo nghĩa của CSDL. Nhưng đơn vị <b>biến khỏi cây</b> trong khi hồ sơ nhân viên và
 * công trình vẫn trỏ vào nó ⇒ ô <i>"Đơn vị"</i> của họ thành trống, và báo cáo <i>"nhân sự theo
 * phòng ban"</i> <b>đếm thiếu</b> đúng những người ấy. Một con số sai mà ⛔ không dòng lỗi nào —
 * đúng thứ nguy hiểm hơn một lỗi.
 */
public interface OrgUnitUsagePort {

    /**
     * Mô tả ngắn những gì đang thuộc đơn vị — rỗng nghĩa là ⛔ ⛔ không ai thuộc.
     *
     * <p>Trả <b>mô tả cho người đọc</b> (<i>"3 hồ sơ CBNV"</i>) chứ ⛔ không trả mã: câu lỗi đi
     * thẳng ra màn hình, và người vận hành cần biết <b>phải đi chuyển cái gì</b> trước khi giải
     * thể, ⛔ không cần một danh sách UUID.
     *
     * <p>⚠ Bên cài nên trả một dòng <b>tổng hợp</b> kèm số lượng thay vì liệt kê từng bản ghi: một
     * Xí nghiệp có 40 người thì câu lỗi ⛔ không cần kể hết bốn mươi cái tên.
     *
     * @param orgUnitId khoá <b>nội bộ</b> — nơi gọi là {@code core}, ⛔ không phải một endpoint
     */
    List<String> dangThuocDonVi(Long orgUnitId);
}
