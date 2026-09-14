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
}
