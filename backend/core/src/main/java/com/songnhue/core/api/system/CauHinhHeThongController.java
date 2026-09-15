package com.songnhue.core.api.system;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.auth.ClientInfo;
import com.songnhue.core.application.auth.XacThucLaiService;
import com.songnhue.core.application.secret.BiMatTichHopService;
import com.songnhue.core.application.system.TinhTrangCauHinhService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.PermissionDeniedException;
import com.songnhue.core.common.security.AuthContext;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.spi.LoaiBiMat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Cấu hình hệ thống — tình trạng + bí mật tích hợp. T61.41 / T61.44 (architecture-review.md §12.1).
 *
 * <h2>⛔⛔ Ba lớp trước một lượt ghi bí mật</h2>
 *
 * <ol>
 *   <li>Quyền {@code adm:system-config:secret} — migration chỉ cấp SUPER_ADMIN.
 *   <li><b>Vai trò SUPER_ADMIN kiểm TƯỜNG MINH</b> — quyền gán được qua màn hình phân quyền; vai trò hệ thống thì ⛔
 *       (cùng khuôn khôi phục CSDL).
 *   <li><b>Mã 2FA nhập lại ngay lúc này</b> — {@link XacThucLaiService}, đếm lượt sai vào khoá tài khoản.
 * </ol>
 *
 * <p>⛔ Không endpoint nào trả GIÁ TRỊ cấu hình hay bí mật, kể cả che một phần.
 */
@RestController
@RequestMapping("/api/v1/system/cau-hinh")
@Tag(name = "05-adm · Cấu hình hệ thống", description = "Tình trạng cấu hình + bí mật tích hợp")
public class CauHinhHeThongController {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final TinhTrangCauHinhService tinhTrang;
    private final BiMatTichHopService biMat;
    private final XacThucLaiService xacThucLai;

    public CauHinhHeThongController(
            TinhTrangCauHinhService tinhTrang, BiMatTichHopService biMat, XacThucLaiService xacThucLai) {
        this.tinhTrang = tinhTrang;
        this.biMat = biMat;
        this.xacThucLai = xacThucLai;
    }

    @GetMapping
    @Operation(summary = "Tình trạng từng mục cấu hình — ⛔ trả giá trị")
    @RequirePermission("adm:system-config:view")
    public TinhTrangCauHinhService.TongQuan tongQuan() {
        return tinhTrang.tongQuan();
    }

    @GetMapping("/tom-tat")
    @Operation(summary = "Số mục chặn / cảnh báo — nguồn của banner trên layout quản trị")
    @RequirePermission("adm:system-config:view")
    public TinhTrangCauHinhService.TomTat tomTat() {
        return tinhTrang.tomTat();
    }

    @GetMapping("/bi-mat")
    @Operation(summary = "Danh mục bí mật tích hợp — chỉ tình trạng, ⛔ giá trị")
    @RequirePermission("adm:system-config:view")
    public List<BiMatTichHopService.TinhTrang> danhSachBiMat() {
        return biMat.danhSach();
    }

    @PutMapping("/bi-mat/{loai}")
    @Operation(summary = "Đặt/thay một bí mật — SUPER_ADMIN + mã 2FA nhập lại")
    @RequirePermission("adm:system-config:secret")
    public BiMatTichHopService.TinhTrang datBiMat(
            @PathVariable LoaiBiMat loai, @Valid @RequestBody DatBiMatRequest request, HttpServletRequest httpRequest) {
        chiSuperAdmin();
        xacThucLai.xacThuc(request.maXacThuc(), ClientInfo.from(httpRequest));
        return biMat.dat(loai, request.giaTri());
    }

    @PostMapping("/bi-mat/{loai}/xoa")
    @Operation(summary = "Xoá một bí mật — SUPER_ADMIN + mã 2FA nhập lại")
    @RequirePermission("adm:system-config:secret")
    public BiMatTichHopService.TinhTrang xoaBiMat(
            @PathVariable LoaiBiMat loai, @Valid @RequestBody XacThucRequest request, HttpServletRequest httpRequest) {
        chiSuperAdmin();
        xacThucLai.xacThuc(request.maXacThuc(), ClientInfo.from(httpRequest));
        return biMat.xoa(loai);
    }

    private static void chiSuperAdmin() {
        if (!AuthContext.current().map(u -> u.hasRole(SUPER_ADMIN)).orElse(false)) {
            throw new PermissionDeniedException(ErrorCode.AUTH_3001);
        }
    }

    /** ⛔ {@code toString} mặc định của record in cả giá trị — ghi đè để một dòng log lỡ tay ⛔ mang bí mật. */
    public record DatBiMatRequest(@NotBlank @Size(max = 512) String giaTri, String maXacThuc) {
        @Override
        public String toString() {
            return "DatBiMatRequest[giaTri=***]";
        }
    }

    public record XacThucRequest(String maXacThuc) {}
}
