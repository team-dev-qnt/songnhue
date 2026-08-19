package com.songnhue.core.spi;

import java.util.Optional;
import java.util.UUID;

/**
 * Tra cứu tài khoản người dùng cho module nghiệp vụ.
 *
 * <p>Sinh ra vì một nhu cầu lặp lại ở mọi module: bảng nghiệp vụ giữ khoá ngoại tới {@code users}
 * (tác giả bài viết, người thực hiện bảo trì, người phụ trách), mà API thì chỉ nhận và trả
 * {@code publicId}. Ai đó phải dịch giữa hai thứ, và chỗ đó không thể là repository của Core —
 * module khác không được import.
 *
 * <p>⚠ <b>Cố ý rất hẹp.</b> Đây không phải cổng "quản lý người dùng": không tạo, không sửa, không
 * đọc thông tin cá nhân. Mở rộng nó là mời module nghiệp vụ đi vòng qua MOD-05 để đụng vào hồ sơ
 * người dùng.
 */
public interface UserDirectoryPort {

    /**
     * Đổi {@code publicId} thành khoá nội bộ để lưu khoá ngoại.
     *
     * @return rỗng khi không có tài khoản nào như vậy, hoặc tài khoản đã xoá mềm
     */
    Optional<Long> internalIdOf(UUID publicId);

    /** Chiều ngược lại — dựng phản hồi API từ khoá ngoại đang lưu. */
    Optional<UUID> publicIdOf(Long internalId);
}
