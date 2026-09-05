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
     *
     * <h2>⛔ Vế lọc là {@code status}, ⛔ KHÔNG phải {@code locked_until} — và đó là chủ ý</h2>
     *
     * <p>Hai thứ khác nhau, dễ gộp nhầm: {@code status = 'LOCKED'} là quyết định của quản trị viên
     * (khoá cho tới khi có người mở); {@code locked_until} là khoá <b>tạm</b> vài phút do gõ sai mật
     * khẩu. Thêm {@code locked_until} vào đây thì một người trực vừa gõ nhầm mật khẩu ⛔ <b>không
     * bao giờ</b> nhận email cảnh báo lũ của 15 phút ấy — cảnh báo mất <b>vĩnh viễn</b>, còn cái
     * khoá thì hết sau vài phút. Rà 03/09 từng ghi nhầm chỗ này là một khoản nợ; đo lại thì hành vi
     * hiện tại đúng, và dòng ghi chú này ở đây để lượt sau đừng "sửa" nó.
     */
    @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL AND u.status = 'ACTIVE'")
    List<Long> findActiveIdsIn(@Param("ids") List<Long> ids);

    /**
     * Đổi {@code public_id} sang khoá nội bộ — <b>WS-33</b>, cho nhóm "Ban điều hành".
     *
     * <p>⚠ Tồn tại vì một lệch kiểu <b>đo được</b>: seed của
     * {@code notification.alert-group.executive-board} mô tả giá trị là <i>"Danh sách publicId tài
     * khoản"</i> (UUID), trong khi {@code RecipientResolver.executiveBoard()} suốt từ 13/08 lại
     * {@code readValue(raw, Long[].class)}. Admin nhập <b>đúng như nhãn dặn</b> ⇒
     * {@code JsonProcessingException} ⇒ nuốt thành {@code log.error} ⇒ nhóm rỗng ⇒ màn hình vẫn báo
     * lưu thành công. Cả hai vế "xanh", cảnh báo tới <b>0 người</b>.
     *
     * <p>⛔ Chữa ở phía <b>mã</b>, ⛔ không sửa nhãn thành "id nội bộ": id nội bộ ⛔ không được lộ ra
     * giao diện (conventions §4), và màn hình chọn người dùng vốn gửi {@code publicId}.
     */
    @Query("SELECT u.id FROM User u WHERE u.publicId IN :publicIds AND u.deletedAt IS NULL")
    List<Long> findIdsByPublicIds(@Param("publicIds") List<java.util.UUID> publicIds);

    /**
     * Lọc ra tài khoản <b>chưa bị xoá</b>, không quan tâm đang hoạt động hay đã khoá.
     *
     * <p>Dùng cho người nhận được chỉ định <b>đích danh</b>. Khác với {@link #findActiveIdsIn}: ở đó
     * người nhận do hệ thống suy ra từ nhóm, nên bỏ tài khoản đã khoá là đúng. Còn khi nơi gọi đã nêu
     * tên cụ thể thì đó là quyết định của nghiệp vụ — điển hình là thư "tài khoản của bạn vừa bị
     * khoá", vốn chỉ có nghĩa khi gửi cho đúng người vừa bị khoá.
     */
    @Query("SELECT u.id FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    List<Long> findNotDeletedIdsIn(@Param("ids") List<Long> ids);

    /** Địa chỉ email của người nhận, bỏ qua tài khoản không có email. */
    @Query("SELECT u.id, u.email, u.fullName FROM User u WHERE u.id IN :ids AND u.deletedAt IS NULL")
    List<Object[]> findContactInfo(@Param("ids") List<Long> ids);

    /** Danh sách tài khoản cho màn hình quản trị (T6.15). */
    List<User> findAllByDeletedAtIsNullOrderByUsernameAsc();

    /** Toàn bộ tài khoản đang hoạt động — dùng cho thông báo hệ thống gửi tất cả (M5.13). */
    @Query("SELECT u.id FROM User u WHERE u.deletedAt IS NULL AND u.status = 'ACTIVE'")
    List<Long> findAllActiveIds();

    /**
     * Tài khoản đang hoạt động <b>có một quyền cụ thể</b> — người nhận của bước duyệt.
     *
     * <p>Truy vấn thẳng SQL vì {@code roles}/{@code permissions} chưa có entity JPA: RBAC nạp bằng
     * truy vấn riêng lúc đăng nhập, không map thành đồ thị đối tượng. Dựng entity chỉ để phục vụ
     * câu này là thêm một mô hình thứ hai cho cùng một thứ.
     *
     * <p>Lọc {@code active} ở cả người dùng lẫn vai trò: vai trò bị vô hiệu hoá mà vẫn gửi thông báo
     * cho người mang nó là báo cho người không còn quyền xử lý.
     */
    @Query(
            value =
                    """
                    SELECT DISTINCT u.id
                    FROM users u
                             JOIN user_roles ur ON ur.user_id = u.id
                             JOIN roles r ON r.id = ur.role_id AND r.active AND r.deleted_at IS NULL
                             JOIN role_permissions rp ON rp.role_id = r.id
                             JOIN permissions p ON p.id = rp.permission_id
                    WHERE u.deleted_at IS NULL
                      AND u.status = 'ACTIVE'
                      AND p.code = :permissionCode
                    """,
            nativeQuery = true)
    List<Long> findActiveIdsByPermission(@Param("permissionCode") String permissionCode);
}
