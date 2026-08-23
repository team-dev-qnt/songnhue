package com.songnhue.operations.infra;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.operations.domain.OperationStatusCode;

/**
 * Danh mục mã tình hình vận hành — CN-02.11.
 *
 * <p>⛔ {@link OperationStatusCode} <b>không</b> là {@code ScopedEntity} (danh mục dùng chung toàn
 * Công ty), nên ở đây không có bộ lọc phạm vi nào chạy ngầm. Đổi lại, mọi câu <b>phải tự mang
 * {@code deleted_at IS NULL}</b> — {@code findAll()} trần trả về cả mã đã ẩn lẫn mã đã xoá mềm.
 */
@Repository
public interface OperationStatusCodeRepository extends JpaRepository<OperationStatusCode, Long> {

    boolean existsByCodeAndDeletedAtIsNull(String code);

    Optional<OperationStatusCode> findByCodeAndDeletedAtIsNull(String code);

    /** Tra theo định danh công khai — đường duy nhất cho request của người dùng ({@code §4.2}). */
    Optional<OperationStatusCode> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /** Danh sách quản trị: gồm cả mã đã ẩn ({@code active = false}), trừ mã đã xoá mềm. */
    List<OperationStatusCode> findByDeletedAtIsNullOrderBySortOrderAscCodeAsc();

    /** Danh sách cho màn hình nhập liệu: chỉ mã còn dùng được. */
    List<OperationStatusCode> findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscCodeAsc();
}
