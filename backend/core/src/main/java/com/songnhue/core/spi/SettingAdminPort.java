package com.songnhue.core.spi;

import java.util.List;

/**
 * Sửa tham số nghiệp vụ <b>trong phạm vi đúng một nhóm</b> — WS-15.
 *
 * <h2>Vì sao có port thứ hai bên cạnh {@link SettingPort}</h2>
 *
 * {@link SettingPort} chỉ đọc, và đọc theo từng khoá. Màn hình cấu hình giao diện cổng cần cả
 * <i>liệt kê</i> lẫn <i>ghi</i>, mà API cấu hình hệ thống của Core lại gác bằng
 * {@code adm:setting:update} — quyền của Quản trị viên hệ thống. Quản trị nội dung không có mã đó và
 * cũng <b>không nên có</b>: tên site, logo, footer là việc của người làm nội dung, còn ngưỡng khoá
 * tài khoản hay chu kỳ gọi API thuỷ văn thì không.
 *
 * <h2>Vì sao mọi hàm đều mang {@code groupCode}</h2>
 *
 * ⛔ Đây là phần quan trọng nhất của hợp đồng này. Một port ghi tự do dạng
 * {@code update(key, value)} thì module {@code content} — chỉ cần cầm được bean — có thể ghi
 * {@code security.login.max-failed-attempts}. Chốt chặn duy nhất khi đó là annotation
 * {@code @RequirePermission} trên controller, tức là <i>một dòng người ta có thể quên</i>.
 *
 * <p>Buộc khai nhóm và từ chối khoá không thuộc nhóm đó biến giới hạn thành thứ máy kiểm tra được:
 * {@code content} khai {@code "site"} nên nó <b>không có đường nào</b> chạm tới nhóm
 * {@code SECURITY}, kể cả khi ai đó bỏ sót phân quyền.
 */
public interface SettingAdminPort {

    /**
     * Toàn bộ tham số của một nhóm, đã sắp theo thứ tự hiển thị.
     *
     * @return danh sách rỗng nếu nhóm không tồn tại — không ném lỗi, vì một nhóm chưa seed không phải
     *     là lỗi của người đang mở màn hình
     */
    List<SettingItem> listGroup(String groupCode);

    /**
     * Sửa một tham số, <b>chỉ khi</b> nó thuộc đúng nhóm đã khai.
     *
     * @throws com.songnhue.core.common.exception.ResourceNotFoundException khoá không tồn tại
     *     <i>hoặc</i> thuộc nhóm khác — cố ý không phân biệt hai trường hợp, vì phân biệt được là nói
     *     cho người gọi biết những khoá nào tồn tại ngoài phạm vi của họ
     */
    SettingItem updateInGroup(String groupCode, String key, String value);
}
