package com.songnhue.core.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Sơ đồ tổ chức — pattern P2.
 *
 * <p>Cố ý <b>chỉ đọc</b>: sửa cây tổ chức là việc của MOD-05, và việc đó kéo theo cập nhật đường dẫn
 * của cả nhánh con cùng với bộ nhớ đệm phân quyền. Module nghiệp vụ gán đơn vị phụ trách cho bản ghi
 * của mình thì chỉ cần tra, không cần sửa.
 *
 * <p>⚠ Cổng này mỏng là cố ý — xem {@code package-info}. Cần thêm phương thức thì thêm lúc có chỗ
 * gọi thật, kèm bài kiểm.
 */
public interface OrgUnitPort {

    Optional<OrgUnitRef> findRef(UUID publicId);

    Optional<OrgUnitRef> findRefById(Long id);

    /**
     * Tra theo mã đơn vị — dành cho đường nhập dữ liệu hàng loạt, nơi tệp nguồn ghi mã chứ không ghi
     * định danh của hệ thống.
     */
    Optional<OrgUnitRef> findRefByCode(String code);

    /**
     * Tải hàng loạt đơn vị theo ID — chống N+1 query trên màn hình danh sách.
     */
    java.util.Map<Long, OrgUnitRef> findRefsByIds(java.util.Collection<Long> ids);

    /**
     * Toàn bộ cây đơn vị còn hiệu lực, <b>dạng phẳng</b>, kèm người đứng đầu của từng nút — CN-04.1.
     *
     * <h2>Vì sao cổng "mỏng là cố ý" nay có thêm một phương thức</h2>
     *
     * <p>Javadoc đầu tệp nói: <i>"cần thêm phương thức thì thêm lúc có <b>chỗ gọi thật</b>, kèm bài
     * kiểm"</i>. Chỗ gọi thật là {@code hr.application.SoDoToChucService} — sơ đồ tổ chức gác bằng
     * {@code hr:org-chart:view}, một mã quyền <b>của MOD-04</b>, nên endpoint thuộc về {@code hr}.
     * Đặt nó ở {@code core} sẽ bắt {@code core} biết chính sách phân quyền của HRM.
     *
     * <p>⚠ Dữ liệu của sơ đồ chia làm hai nửa và chúng ở hai module: <b>cây + người đứng đầu</b>
     * thuộc {@code core}, <b>quân số</b> thuộc {@code hr}. Phương thức này giao nửa của {@code core};
     * nửa kia {@code hr} tự đếm trên bảng của mình.
     *
     * @return đã sắp theo thứ tự hiển thị (materialized path ⇒ {@code sort_order}); bên gọi chỉ việc
     *     đưa vào {@code TreeBuilder}
     */
    java.util.List<OrgUnitTreeRef> cayPhang();

    /**
     * Tài khoản là <b>trưởng hoặc phó</b> của đơn vị này, hoặc của một đơn vị <b>cha</b> nó — T80.1.
     *
     * <h2>Vì sao cổng "mỏng là cố ý" nay có thêm hai phương thức</h2>
     *
     * <p>Chỗ gọi thật là {@code hr.application.ThamQuyenDuyetPhep}: đặc tả CN-04.9 nói <i>"<b>Quản
     * lý đơn vị</b> duyệt"</i> — một <b>quan hệ</b> — mà {@code workflow_transitions} chỉ diễn đạt
     * được một <b>mã quyền</b>. Quan hệ ấy sống ở {@code org_units} (MOD-05) trong khi cổng duyệt
     * sống ở {@code hr} (MOD-04), và quy tắc 6 cấm {@code hr} đọc repository của {@code core}.
     *
     * <p>⚠ Cố ý trả khoá <b>nội bộ</b>, ⛔ phải {@code publicId}: hai bên đều so với
     * {@code AuthenticatedUser.userId()} và {@code leave_requests.org_unit_id}. Đổi sang
     * {@code UUID} chỉ để "sạch API" sẽ bắt cả hai đầu dịch ngược lại — một vòng dịch thừa là một
     * chỗ nữa để lệch.
     *
     * @return rỗng khi ⛔ đơn vị nào trong chuỗi có trưởng/phó <b>đang hoạt động</b> — một câu trả
     *     lời có nghĩa, ⛔ phải một lỗi (quy tắc 16)
     */
    java.util.Set<Long> lanhDaoCuaChuoiDonVi(Long orgUnitId);

    /**
     * Một đơn vị <b>và mọi đơn vị cha</b> của nó — chuỗi đi LÊN (T80.5).
     *
     * <p>Dùng để tra uỷ quyền duyệt: trưởng Xí nghiệp A giao quyền cho ai đó thì người ấy phải
     * duyệt được cả đơn của các <b>Tổ đội trực thuộc</b> A — tức một hàng uỷ quyền gắn ở A phải
     * khớp một đơn gắn ở Tổ đội. Soi từ phía đơn thì câu hỏi là <i>"những đơn vị nào phủ đơn
     * này"</i>, và đáp án là chính chuỗi này.
     *
     * <p>⚠ Cùng một phép so với {@link #lanhDaoCuaChuoiDonVi}, cố ý: hai câu trả lời hai câu hỏi
     * khác nhau về <b>cùng một quan hệ</b>, và chúng ⛔ bao giờ được lệch nhau (luật 14).
     */
    java.util.Set<Long> chuoiDonViLen(Long orgUnitId);
}
