package com.songnhue.core.common.security;

import java.util.Set;
import java.util.UUID;

/**
 * Người dùng của request hiện tại — dữ liệu đã được kiểm chứng, dùng cho cả 3 tầng phân quyền.
 *
 * <p>Là {@code record} bất biến có chủ đích: không đoạn mã nghiệp vụ nào được phép thêm quyền cho
 * chính mình giữa chừng. Muốn đổi quyền thì phải đổi ở DB rồi đăng nhập/làm mới token lại.
 *
 * @param userId khoá nội bộ — chỉ dùng bên trong, không bao giờ ra API
 * @param publicId định danh ra API (§4.2 chống IDOR)
 * @param username giữ để ghi nhật ký, không dùng để phân quyền
 * @param fullName tên hiển thị, cho màn hình và nhật ký
 * @param orgUnitId đơn vị của người dùng
 * @param orgUnitPath materialized path, VD {@code /1/4/} — tham số bộ lọc phạm vi tầng 3
 * @param roles mã vai trò, chỉ dùng để quyết định có bắt buộc 2FA hay không
 * @param permissions mã quyền dạng {@code module:resource:action} — nguồn DUY NHẤT cho tầng 2
 * @param mustChangePassword đang bị chặn cho tới khi đổi mật khẩu
 * @param sessionFamilyId family của phiên, để đăng xuất và thu hồi
 * @param tokenId {@code jti} của access token, để đưa vào denylist khi thu hồi
 */
public record AuthenticatedUser(
        Long userId,
        UUID publicId,
        String username,
        String fullName,
        Long orgUnitId,
        String orgUnitPath,
        Set<String> roles,
        Set<String> permissions,
        boolean mustChangePassword,
        UUID sessionFamilyId,
        UUID tokenId) {

    /**
     * Vai trò buộc phải bật 2FA (chốt G12).
     *
     * <p>Đặt ở tầng mã nguồn chứ không phải bảng {@code settings}: đây là điều kiện nghiệm thu bảo
     * mật, không phải tham số vận hành. Để Admin tự tắt được trên UI thì tài khoản Admin bị chiếm sẽ
     * tắt 2FA trước tiên.
     */
    public static final Set<String> TWO_FACTOR_REQUIRED_ROLES = Set.of("SUPER_ADMIN", "ADMIN", "ADMIN_HR");

    public AuthenticatedUser {
        roles = Set.copyOf(roles);
        permissions = Set.copyOf(permissions);
    }

    public boolean hasPermission(String permissionCode) {
        return permissions.contains(permissionCode);
    }

    public boolean hasAnyPermission(String... codes) {
        for (String code : codes) {
            if (permissions.contains(code)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasAllPermissions(String... codes) {
        for (String code : codes) {
            if (!permissions.contains(code)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasRole(String roleCode) {
        return roles.contains(roleCode);
    }
}
