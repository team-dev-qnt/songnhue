package com.songnhue.core.api.identity;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
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

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.XacThucLaiService;
import com.songnhue.core.application.identity.DatLaiHaiBuocService;
import com.songnhue.core.application.identity.DatLaiMatKhauService;
import com.songnhue.core.application.identity.PermissionSummary;
import com.songnhue.core.application.identity.RoleSummary;
import com.songnhue.core.application.identity.UserAdminService;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.domain.identity.User;
import com.songnhue.core.domain.identity.UserStatus;
import com.songnhue.core.spi.EmployeeRef;

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
    private final DatLaiHaiBuocService datLaiHaiBuoc;

    private final DatLaiMatKhauService datLaiMatKhau;

    private final XacThucLaiService xacThucLai;

    public UserAdminController(
            UserAdminService userAdminService,
            DatLaiHaiBuocService datLaiHaiBuoc,
            DatLaiMatKhauService datLaiMatKhau,
            XacThucLaiService xacThucLai) {
        this.datLaiMatKhau = datLaiMatKhau;
        this.xacThucLai = xacThucLai;
        this.userAdminService = userAdminService;
        this.datLaiHaiBuoc = datLaiHaiBuoc;
    }

    @GetMapping
    @Operation(summary = "Danh sách tài khoản")
    @RequirePermission("adm:user:view")
    public List<UserDtos.UserView> list() {
        List<User> danhSach = userAdminService.list();
        // ⚠ MỘT lượt tra cho cả bảng — ⛔ không `map(u -> hoSoNhanSuCua(u))`, thứ cho ra một câu
        //   truy vấn mỗi hàng, và số hàng là số CBNV của Công ty sau chốt C3.
        Map<Long, EmployeeRef> hoSo = userAdminService.hoSoNhanSuCua(danhSach);
        return danhSach.stream()
                .map(u -> UserDtos.UserView.of(u, hoSo.get(u.getId())))
                .toList();
    }

    // ⛔ BIA MỘ — `GET /{publicId}` gỡ 14/09/2026 (T61.22): 0 nơi gọi, hộp thoại dựng từ hàng danh sách.

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Tạo tài khoản — bắt buộc đổi mật khẩu ở lần đăng nhập đầu")
    @RequirePermission("adm:user:create")
    public UserDtos.UserView create(@Valid @RequestBody UserDtos.CreateRequest request) {
        // `null`: tài khoản vừa ra đời ⇒ về nguyên tắc chưa liên kết hồ sơ nào. Đây là một khẳng
        // định, ⛔ không phải một lượt tra bị bỏ.
        return UserDtos.UserView.of(
                userAdminService.create(
                        request.username(),
                        request.fullName(),
                        request.email(),
                        request.orgUnitPublicId(),
                        request.temporaryPassword()),
                null);
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa thông tin tài khoản")
    @RequirePermission("adm:user:update")
    public UserDtos.UserView update(@PathVariable UUID publicId, @Valid @RequestBody UserDtos.UpdateRequest request) {
        User user = userAdminService.update(publicId, request.fullName(), request.email(), request.phone());
        return UserDtos.UserView.of(user, userAdminService.hoSoNhanSuCua(user).orElse(null));
    }

    @PostMapping("/{publicId}/status")
    @Operation(summary = "Khoá / mở khoá tài khoản — có hiệu lực ngay, không chờ token hết hạn")
    @RequirePermission("adm:user:lock")
    public UserDtos.UserView setStatus(
            @PathVariable UUID publicId, @Valid @RequestBody UserDtos.StatusRequest request) {
        User user = userAdminService.setStatus(publicId, request.status());
        return UserDtos.UserView.of(user, userAdminService.hoSoNhanSuCua(user).orElse(null));
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

    @GetMapping("/permissions/catalog")
    @Operation(summary = "Toàn bộ danh mục quyền — nguồn dựng các ô đánh dấu của ma trận")
    @RequirePermission("adm:role:view")
    public List<PermissionSummary> permissionCatalog() {
        return userAdminService.permissionCatalog();
    }

    /**
     * Đặt lại toàn bộ quyền của một vai trò — <b>T27.31, CN-05.2</b>.
     *
     * <p>⛔ {@code PUT} <b>thay cả tập</b>, ⛔ không phải {@code PATCH} thêm/bớt từng quyền. Cùng lý do
     * đã ghi ở {@link #assignRoles}: màn hình đọc trạng thái hiện tại, người dùng tick/bỏ tick, rồi
     * gửi nguyên trạng thái ấy lên ⇒ hai người sửa cùng lúc ⛔ không ra được một kết quả lai mà ⛔
     * không ai chọn.
     *
     * <p>⚠ Quyền gác ở đây là {@code adm:role:manage} — <b>khác</b> {@code adm:role:view} của ba
     * endpoint đọc bên trên. Tách hai quyền là có chủ đích: <i>nhìn thấy ai có quyền gì</i> và
     * <i>đổi được nó</i> là hai việc khác nhau, và ma trận seed cấp {@code :view} rộng hơn hẳn.
     */
    @PutMapping("/roles/{roleCode}/permissions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đặt lại toàn bộ quyền của một vai trò — có hiệu lực ngay")
    @RequirePermission("adm:role:manage")
    public void replacePermissionsOfRole(
            @PathVariable String roleCode, @Valid @RequestBody UserDtos.PermissionsRequest request) {
        userAdminService.replacePermissionsOfRole(roleCode, request.permissionCodes());
    }

    /**
     * Liên kết / gỡ liên kết tài khoản với hồ sơ CBNV — <b>T51.8, CN-05.1</b>.
     *
     * <p>⚠ Quyền gác là {@code adm:user:update}, cùng cửa với lượt sửa thông tin tài khoản: đặc tả
     * xếp việc này vào CN-05.1 <i>"Quản lý Tài khoản"</i>, ⛔ không vào MOD-04. Nhưng ⛔ <b>không</b>
     * chỉ có cổng quyền canh nó — {@code UserAdminService.lienKetHoSo} còn cấm tự liên kết chính
     * mình ({@code ADM-2018}), vì cột này quyết định ai đọc được trường 🔒 của ai.
     *
     * <p>{@code PUT} với thân {@code {"employeePublicId": null}} là <b>gỡ</b> liên kết. ⛔ Không
     * dùng {@code DELETE} riêng: gán và gỡ là hai giá trị của <b>một</b> ô trên biểu mẫu, và tách
     * đôi thì màn hình phải nhớ gọi động từ nào — đúng chỗ luật 14 nói tới.
     *
     * <p>⚠ {@link ClientInfo#from} truyền xuống service vì lượt này ghi một sự kiện <b>bảo mật</b>
     * mức DANGER, và một dòng nhật ký bảo mật ⛔ không có IP thì mất nửa giá trị.
     */
    @PutMapping("/{publicId}/ho-so-nhan-su")
    @Operation(summary = "Liên kết tài khoản với hồ sơ CBNV — thân rỗng là gỡ liên kết")
    @RequirePermission("adm:user:update")
    public UserDtos.UserView lienKetHoSo(
            @PathVariable UUID publicId,
            @Valid @RequestBody UserDtos.HoSoRequest request,
            HttpServletRequest httpRequest) {

        User user = userAdminService.lienKetHoSo(publicId, request.employeePublicId(), ClientInfo.from(httpRequest));
        return UserDtos.UserView.of(user, userAdminService.hoSoNhanSuCua(user).orElse(null));
    }

    /** T61.30 — người dùng mất cả ứng dụng xác thực lẫn mã khôi phục. */
    @PostMapping("/{publicId}/dat-lai-2fa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đặt lại xác thực hai bước (xoá đăng ký, thu hồi mọi phiên)")
    @RequirePermission("adm:user:update")
    public void datLaiHaiBuoc(@PathVariable UUID publicId, HttpServletRequest httpRequest) {
        datLaiHaiBuoc.datLai(publicId, ClientInfo.from(httpRequest));
    }

    /**
     * T61.31 · CN-05.2 — đặt lại mật khẩu một tài khoản nội bộ.
     *
     * <p>⚠ Quyền {@code adm:user:reset-password} có trong danh mục từ 13/08/2026 và tới 16/09 vẫn
     * <b>0 đầu nhận</b>; đây là lời gọi đầu tiên của nó. ⛔ Dùng {@code adm:user:update} như các
     * thao tác khác: đặt lại mật khẩu là chiếm quyền đăng nhập vào một tài khoản, ⛔ phải sửa hồ sơ.
     *
     * <p>⚠⚠ Đòi nhập lại mã 2FA (T61.42) — cùng lớp bảo vệ với khôi phục CSDL và bí mật tích hợp.
     */
    @PostMapping("/{publicId}/dat-lai-mat-khau")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Đặt lại mật khẩu tài khoản (mật khẩu tạm + buộc đổi ở lần đăng nhập tới)")
    @RequirePermission("adm:user:reset-password")
    public void datLaiMatKhau(
            @PathVariable UUID publicId,
            @Valid @RequestBody UserDtos.DatLaiMatKhauRequest request,
            HttpServletRequest httpRequest) {
        ClientInfo client = ClientInfo.from(httpRequest);
        xacThucLai.xacThuc(request.maXacThuc(), client);
        datLaiMatKhau.datLai(publicId, request.matKhauTam(), client);
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
                @NotBlank @Size(max = 200) String temporaryPassword) {}

        public record UpdateRequest(
                @NotBlank @Size(max = 255) String fullName,
                @Size(max = 255) String email,
                @Size(max = 30) String phone) {}

        public record StatusRequest(@NotNull UserStatus status) {}

        /** ⛔ {@code toString} mặc định in cả mật khẩu tạm — ghi đè để một dòng log lỡ tay ⛔ mang nó. */
        public record DatLaiMatKhauRequest(@NotBlank @Size(max = 200) String matKhauTam, String maXacThuc) {
            @Override
            public String toString() {
                return "DatLaiMatKhauRequest[đã ẩn]";
            }
        }

        public record RolesRequest(@NotNull List<String> roleCodes) {}

        /**
         * Tập quyền mới của một vai trò — T27.31.
         *
         * <p>⚠ {@code @NotNull} chứ ⛔ <b>không</b> {@code @NotEmpty}: một danh sách <b>rỗng</b> là
         * một thao tác hợp lệ — gỡ sạch quyền của một vai trò đang chờ định nghĩa lại. Thứ ⛔ không
         * hợp lệ là {@code null}, tức trường bị thiếu hẳn khỏi thân yêu cầu, vì lúc ấy ⛔ không phân
         * biệt được "muốn xoá hết" với "quên gửi" (luật 9).
         */
        public record PermissionsRequest(@NotNull List<String> permissionCodes) {}

        /**
         * Hồ sơ CBNV đang liên kết — T51.8.
         *
         * <p>⛔ Đúng ba trường, và ⛔ không một trường 🔒 nào: xem {@link EmployeeRef}. Đây là phía
         * <b>ra API</b> của một liên kết quyết định ai đọc được CCCD/lương của ai, nên nó phải mỏng
         * bằng đúng thứ màn hình cần vẽ.
         */
        public record HoSoNhanSuView(UUID publicId, String code, String fullName) {

            static HoSoNhanSuView of(EmployeeRef ref) {
                return ref == null ? null : new HoSoNhanSuView(ref.publicId(), ref.code(), ref.fullName());
            }
        }

        /** Hồ sơ cần liên kết; {@code null} là <b>gỡ</b> liên kết — xem {@code lienKetHoSo}. */
        public record HoSoRequest(UUID employeePublicId) {}

        public record UserView(
                UUID publicId,
                String username,
                String fullName,
                String email,
                String phone,
                String status,
                boolean mustChangePassword,
                boolean twoFactorRequired,
                Instant lastLoginAt,
                HoSoNhanSuView hoSoNhanSu) {

            /**
             * ⛔ <b>Không</b> có biến thể một tham số.
             *
             * <p>Một {@code of(User)} "cho tiện" sẽ trả {@code hoSoNhanSu = null} trong im lặng ở
             * bất kỳ endpoint nào quên tra — và {@code null} ở đây đọc y hệt <i>"chưa liên kết"</i>.
             * Người quản trị nhìn thấy ô trống rồi liên kết hồ sơ ấy sang một tài khoản khác. Bắt
             * mọi nơi gọi khai tường minh {@code null} thì cái {@code null} ấy là một <b>quyết
             * định</b>, ⛔ không phải một thứ bị quên.
             */
            public static UserView of(User user, EmployeeRef hoSo) {
                return new UserView(
                        user.getPublicId(),
                        user.getUsername(),
                        user.getFullName(),
                        user.getEmail(),
                        user.getPhone(),
                        user.getStatus(),
                        user.isMustChangePassword(),
                        user.isTwoFactorRequired(),
                        user.getLastLoginAt(),
                        HoSoNhanSuView.of(hoSo));
            }
        }
    }
}
