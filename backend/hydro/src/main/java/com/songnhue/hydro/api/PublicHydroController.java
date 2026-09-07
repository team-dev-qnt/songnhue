package com.songnhue.hydro.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.PublicEndpoint;
import com.songnhue.hydro.application.PublicHydroService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Số liệu thuỷ văn cho <b>cổng công khai</b> — T35.5.
 *
 * <h2>⭐ Không phải sửa một dòng cấu hình bảo mật nào</h2>
 *
 * <p>Dự án ⛔ không có lớp {@code SecurityConfig}; tiền tố {@code /api/v1/public} được nhận theo
 * <b>tiền tố</b> ở ba chỗ độc lập:
 *
 * <ul>
 *   <li>{@code CsrfFilter} — miễn CSRF (trình duyệt vô danh ⛔ không có token nào để gửi);
 *   <li>{@code RateLimitFilter} — vẫn <b>có</b> hạn mức, vì đây là bề mặt không đăng nhập;
 *   <li>{@code PermissionInterceptor} — thấy {@link PublicEndpoint} thì cho qua.
 * </ul>
 *
 * <p>⇒ Controller này được miễn CSRF và có rate-limit <b>miễn phí</b>. ⚠ Nhưng ⛔ đừng đọc điều đó
 * thành "không phải kiểm": bài kiểm phải giữ <b>cả hai vế</b> (§10.19) và phải mang header
 * {@code Origin} (§10.29) — {@code curl} không có origin nên nó đi lọt qua đúng bức tường chặn
 * người dùng thật.
 *
 * <h2>⛔ Module `hydro` tự sở hữu endpoint công khai của mình</h2>
 *
 * <p>Đúng khuôn đang chạy: {@code content} sở hữu {@code /public/articles…}, {@code operations} sở
 * hữu {@code /public/constructions…}, {@code core} sở hữu {@code /public/org-units…}. ⛔ Không gom
 * về một controller chung — gom lại là dựng một chỗ mà mọi module phải sửa chung.
 */
@RestController
@RequestMapping("/api/v1/public/hydro")
@Tag(name = "06-public · Thuỷ văn", description = "Số liệu mực nước công bố trên cổng")
public class PublicHydroController {

    private final PublicHydroService service;

    public PublicHydroController(PublicHydroService service) {
        this.service = service;
    }

    /**
     * Bảng "Mực nước, lượng mưa" — CR-13 · CR-33 · CN-03.4.
     *
     * <p>⚠ Trả <b>danh sách rỗng</b> khi chưa điểm đo nào đang hoạt động — ⛔ không phải 404, và ⛔
     * không phải một bộ dữ liệu mẫu. Cổng tự hiện trạng thái chờ dữ liệu kèm lý do.
     */
    @GetMapping("/muc-nuoc")
    @Operation(summary = "Mực nước theo điểm đo — bảng 8 cột của trang chủ và trang Mực nước")
    @PublicEndpoint(
            reason =
                    "Khối 'Mực nước, lượng mưa' trên trang chủ và trang Quản lý vận hành — CR-13, §6 nhóm Tất cả người dùng")
    public List<PublicHydroService.MucNuocRow> mucNuoc() {
        return service.mucNuoc();
    }
}
