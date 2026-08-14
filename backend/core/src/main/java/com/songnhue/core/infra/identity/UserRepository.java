package com.songnhue.core.infra.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.identity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Tra tài khoản để đăng nhập.
     *
     * <p>So sánh {@code lower(username)} cho khớp với chỉ mục duy nhất {@code uq_users_username} —
     * nếu ở đây so phân biệt hoa thường thì "Admin" và "admin" là hai đường đăng nhập khác nhau
     * trong khi DB chỉ cho tồn tại một tài khoản.
     */
    @Query("SELECT u FROM User u WHERE lower(u.username) = lower(:username) AND u.deletedAt IS NULL")
    Optional<User> findActiveByUsername(@Param("username") String username);

    /**
     * Tra theo định danh công khai — <b>lối duy nhất</b> để lấy user từ dữ liệu do client gửi lên
     * (conventions.md §4.2 chống IDOR). Cấm dùng {@code findById} với id lấy từ request.
     */
    Optional<User> findByPublicIdAndDeletedAtIsNull(UUID publicId);
}
