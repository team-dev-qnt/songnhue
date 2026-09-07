package com.songnhue.core.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.identity.RoleSummary;
import com.songnhue.core.application.identity.UserAdminService;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Quản trị tài khoản và vai trò — {@code /api/v1/admin/users/**} (CN-05.1, M5.1–M5.4).
 *
 * <p><b>Lát cắt dọc nghiệm thu Phase 0</b> (T6.15): mỗi endpoint mang một mã quyền riêng, mọi thay
 * đổi tự vào nhật ký kiểm toán, khoá/mở tài khoản bắn thông báo, và đổi vai trò làm mới cache phân
 * quyền ngay lập tức.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "05-adm · Tài khoản & vai trò", description = "Quản trị người dùng, phân vai trò")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    @Operation(summary = "Danh sách tài khoản")
    @RequirePermission("adm:user:view")
    public List<UserDtos.UserView> list() {
        return userAdminService.list().stream().map(UserDtos.UserView::of).toList();
    }

    @GetMapping("/{publicId}")
    @Operation(summary = "Chi tiết một tài khoản")
    @RequirePermission("adm:user:view")
    public UserDtos.UserView get(@PathVariable UUID publicId) {
        return UserDtos.UserView.of(userAdminService.get(publicId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản — bắt buộc đổi mật khẩu ở lần đăng nhập đầu")
    @RequirePermission("adm:user:create")
    public UserDtos.UserView create(@Valid @RequestBody UserDtos.CreateRequest request) {
        return UserDtos.UserView.of(userAdminService.create(
                request.username(),
                request.fullName(),
                request.email(),
                request.orgUnitPublicId(),
                request.temporaryPassword()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa thông tin tài khoản")
    @RequirePermission("adm:user:update")
    public UserDtos.UserView update(@PathVariable UUID publicId, @Valid @RequestBody UserDtos.UpdateRequest request) {
        return UserDtos.UserView.of(
                userAdminService.update(publicId, request.fullName(), request.email(), request.phone()));
    }

    @PostMapping("/{publicId}/status")
    @Operation(summary = "Khoá / mở khoá tài khoản — có hiệu lực ngay, không chờ token hết hạn")
    @RequirePermission("adm:user:lock")
    public UserDtos.UserView setStatus(
            @PathVariable UUID publicId, @Valid @RequestBody UserDtos.StatusRequest request) {
        return UserDtos.UserView.of(userAdminService.setStatus(publicId, request.status()));
    }

    @GetMapping("/{publicId}/roles")
    @Operation(summary = "Vai trò hiện tại của một tài khoản")
    @RequirePermission("adm:role:view")
    public List<String> rolesOf(@PathVariable UUID publicId) {
        return userAdminService.rolesOf(publicId);
    }

    @PutMapping("/{publicId}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Gán lại toàn bộ vai trò — quyền mới có hiệu lực ngay")
    @RequirePermission("adm:user:assign-role")
    public void assignRoles(@PathVariable UUID publicId, @Valid @RequestBody UserDtos.RolesRequest request) {
        userAdminService.assignRoles(publicId, request.roleCodes());
    }

    @GetMapping("/roles/catalog")
    @Operation(summary = "Danh mục vai trò và số quyền của mỗi vai trò")
    @RequirePermission("adm:role:view")
    public List<RoleSummary> roleCatalog() {
        return userAdminService.roleCatalog();
    }

    @GetMapping("/roles/{roleCode}/permissions")
    @Operation(summary = "Mã quyền của một vai trò — nguồn dựng ma trận phân quyền")
    @RequirePermission("adm:role:view")
    public List<String> permissionsOfRole(@PathVariable String roleCode) {
        return userAdminService.permissionsOfRole(roleCode);
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá mềm tài khoản")
    @RequirePermission("adm:user:update")
    public void delete(@PathVariable UUID publicId) {
        userAdminService.delete(publicId);
    }

    /** DTO của API quản trị tài khoản. Không record nào mang hash mật khẩu ra ngoài. */
    public static final class UserDtos {

        private UserDtos() {}

        public record CreateRequest(
                @NotBlank @Size(max = 100) String username,
                @NotBlank @Size(max = 255) String fullName,
                @Size(max = 255) String email,
                @NotNull UUID orgUnitPublicId,
                @NotBlank String temporaryPassword) {}

        public record UpdateRequest(
                @NotBlank @Size(max = 255) String fullName,
                @Size(max = 255) String email,
                @Size(max = 30) String phone) {}

        public record StatusRequest(@NotNull UserStatus status) {}

        public record RolesRequest(@NotNull List<String> roleCodes) {}

        public record UserView(
                UUID publicId,
                String username,
                String fullName,
                String email,
                String phone,
                String status,
                boolean mustChangePassword,
                boolean twoFactorRequired,
                Instant lastLoginAt) {

            public static UserView of(User user) {
                return new UserView(
                        user.getPublicId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getStatus(),
                        user.isMustChangePassword(),
                        user.isTwoFactorRequired(),
                        user.getLastLoginAt());
            }
        }
    }
}
