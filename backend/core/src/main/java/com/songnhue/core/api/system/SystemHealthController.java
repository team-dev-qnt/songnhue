package com.songnhue.core.api.system;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.actuate.health.CompositeHealth;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tình trạng dịch vụ cho màn hình quản trị — {@code /api/v1/system/health} (M5.12 · CN-05.6).
 *
 * <h2>Vì sao có endpoint này khi đã có {@code /actuator/health}</h2>
 *
 * <p>Hai endpoint phục vụ hai đối tượng khác nhau, và đó là lý do chúng không dùng chung đường:
 *
 * <ul>
 *   <li>{@code /actuator/health} — cho hạ tầng: Docker, nginx, Prometheus. Không đăng nhập, nên
 *       {@code show-details: never}. Chi tiết ở đó gồm cả nhà cung cấp CSDL, đường dẫn thư mục,
 *       tên bucket — thứ không cần lộ cho ai gọi được cổng.
 *   <li>Đường này — cho người quản trị đã đăng nhập và có quyền {@code adm:health:view}. Chi tiết
 *       đầy đủ, vì đó chính là nội dung của màn hình M5.12.
 * </ul>
 *
 * <p>Cách khác là mở {@code show-details} của actuator rồi bọc bằng xác thực — nhưng dự án cố ý
 * không dùng filter chain của Spring Security (architecture-review.md §9.5), nên actuator nằm ngoài
 * cơ chế phân quyền 3 tầng. Đi qua controller là cách duy nhất để chi tiết này chịu đúng bộ luật
 * phân quyền như mọi API khác.
 */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "05-adm · Tình trạng hệ thống", description = "M5.12 — CSDL, kho tệp, thư, sao lưu, telemetry")
public class SystemHealthController {

    private final HealthEndpoint healthEndpoint;

    public SystemHealthController(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @GetMapping("/health")
    @Operation(summary = "Tình trạng từng thành phần, kèm chi tiết")
    @RequirePermission("adm:health:view")
    public HealthView health() {
        HealthComponent root = healthEndpoint.health();

        Map<String, ComponentView> components = new LinkedHashMap<>();
        if (root instanceof CompositeHealth composite) {
            composite.getComponents().forEach((name, component) -> components.put(name, ComponentView.of(component)));
        }
        return new HealthView(root.getStatus().getCode(), components);
    }

    /** @param components khoá là tên indicator: {@code db}, {@code storage}, {@code mail}, {@code backup}, {@code telemetry} */
    public record HealthView(String status, Map<String, ComponentView> components) {}

    public record ComponentView(String status, Map<String, Object> details) {

        static ComponentView of(HealthComponent component) {
            Map<String, Object> details = component instanceof Health health ? health.getDetails() : Map.of();
            return new ComponentView(component.getStatus().getCode(), details);
        }
    }
}
