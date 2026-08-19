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
}
