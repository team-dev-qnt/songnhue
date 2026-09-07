package com.songnhue.content.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.songnhue.content.domain.ContactCategory;

/** Truy vấn danh mục phân loại liên hệ — CN-01.4. */
public interface ContactCategoryRepository extends JpaRepository<ContactCategory, Long> {

    Optional<ContactCategory> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * ⚠ Trả về <b>cả</b> phân loại đã tắt.
     *
     * <p>Màn hình quản trị phải thấy chúng để bật lại; ô chọn ở màn hình xử lý tự lọc theo
     * {@code active}. Lọc sẵn ở đây là khiến một phân loại tắt trở thành ⛔ không sửa được.
     */
    List<ContactCategory> findAllByDeletedAtIsNullOrderBySortOrderAscNameAsc();

    boolean existsByCodeAndDeletedAtIsNull(String code);
}
