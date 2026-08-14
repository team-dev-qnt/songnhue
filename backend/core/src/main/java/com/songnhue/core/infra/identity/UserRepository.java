package com.songnhue.core.infra.identity;

import java.util.List;
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

    /** Chặn xoá đơn vị còn người trực thuộc (T6.1) — người dùng mồ côi đơn vị thì mất luôn phạm vi. */
    boolean existsByOrgUnitIdAndDeletedAtIsNull(Long orgUnitId);

    /**
     * Lọc ra những tài khoản còn hoạt động trong một tập id (T6.7).
     *
     * <p>Gửi cảnh báo cho tài khoản đã khoá là cảnh báo rơi vào khoảng không, mà bảng
     * {@code notification_recipients} vẫn ghi "đã gửi" — nhìn vào tưởng đã tới nơi.
     */
    @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL AND u.status = 'ACTIVE'")
    List<Long> findActiveIdsIn(@Param("ids") List<Long> ids);

    /** Địa chỉ email của người nhận, bỏ qua tài khoản không có email. */
    @Query("SELECT u.id, u.email, u.fullName FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    List<Object[]> findContactInfo(@Param("ids") List<Long> ids);

    /** Toàn bộ tài khoản đang hoạt động — dùng cho thông báo hệ thống gửi tất cả (M5.13). */
    @Query("SELECT u.id FROM User u WHERE u.deletedAt IS NULL AND u.status = 'ACTIVE'")
    List<Long> findAllActiveIds();
}
