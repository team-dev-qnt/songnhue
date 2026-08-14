package com.songnhue.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import com.songnhue.core.common.security.AuthenticatedEndpoint;
import com.songnhue.core.common.security.PublicEndpoint;
import com.songnhue.core.common.security.RequirePermission;

/**
 * <b>Deny by default</b> — chốt chặn ở CI (T5.10, conventions.md §4.2).
 *
 * <p>Quét mọi phương thức controller trong {@code com.songnhue} và bắt buộc mỗi phương thức khai báo
 * đúng <b>một</b> trong ba mức bảo vệ:
 *
 * <ul>
 *   <li>{@link RequirePermission} — cần quyền cụ thể
 *   <li>{@link AuthenticatedEndpoint} — chỉ cần đăng nhập (thao tác với chính mình)
 *   <li>{@link PublicEndpoint} — công khai, kèm lý do
 * </ul>
 *
 * <p><b>Vì sao chặn ở CI chứ không chỉ chặn lúc chạy.</b> Quên gắn quyền là lỗi <i>im lặng</i>:
 * endpoint hoạt động hoàn hảo, test nghiệp vụ xanh, chỉ có điều ai cũng gọi được. Không có bài kiểm
 * này thì lỗ hổng chỉ lộ ra khi kiểm thử bảo mật — hoặc khi đã muộn.
 * {@code PermissionInterceptor} cũng từ chối endpoint chưa khai báo, nhưng chặn từ lúc build thì
 * người viết mã biết ngay trong phút đó, không phải chờ chạy thử đúng đường dẫn ấy.
 *
 * <p>Bài kiểm này đặt ở module {@code app} vì chỉ ở đây cả 5 module nghiệp vụ mới cùng nằm trên
 * classpath.
 */
class DenyByDefaultTest {

    private static final String BASE_PACKAGE = "com.songnhue";

    private static final Set<Class<? extends java.lang.annotation.Annotation>> GUARDS =
            Set.of(RequirePermission.class, AuthenticatedEndpoint.class, PublicEndpoint.class);

    @Test
    @DisplayName("Mọi endpoint đều khai báo mức bảo vệ — thiếu là CI đỏ")
    void everyEndpointDeclaresItsGuard() {
        List<String> unguarded = new ArrayList<>();

        for (Class<?> controller : findControllers()) {
            boolean guardedAtClassLevel = GUARDS.stream()
                    .anyMatch(guard -> AnnotatedElementUtils.findMergedAnnotation(controller, guard) != null);

            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                boolean guarded = guardedAtClassLevel
                        || GUARDS.stream()
                                .anyMatch(guard -> AnnotatedElementUtils.findMergedAnnotation(method, guard) != null);
                if (!guarded) {
                    unguarded.add(controller.getName() + "#" + method.getName());
                }
            }
        }

        assertThat(unguarded)
                .as(
                        """
                        Endpoint dưới đây chưa khai báo mức bảo vệ. Bổ sung MỘT trong ba annotation:
                          @RequirePermission("module:resource:action")  — cần quyền cụ thể
                          @AuthenticatedEndpoint(reason = "…")          — chỉ cần đăng nhập
                          @PublicEndpoint(reason = "…")                 — công khai, ghi rõ vì sao
                        Xem conventions.md §4.2.""")
                .isEmpty();
    }

    @Test
    @DisplayName("Endpoint công khai phải ghi lý do — danh sách này bị soát lại mỗi lần kiểm thử bảo mật")
    void publicEndpointsExplainThemselves() {
        List<String> withoutReason = new ArrayList<>();

        for (Class<?> controller : findControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                PublicEndpoint annotation = AnnotatedElementUtils.findMergedAnnotation(method, PublicEndpoint.class);
                if (annotation != null && annotation.reason().isBlank()) {
                    withoutReason.add(controller.getName() + "#" + method.getName());
                }
            }
        }

        assertThat(withoutReason).isEmpty();
    }

    @Test
    @DisplayName("Mã quyền đúng định dạng module:resource:action")
    void permissionCodesFollowTheConvention() {
        List<String> malformed = new ArrayList<>();
        Set<String> modules = Set.of("cms", "ops", "hyd", "hr", "adm");

        for (Class<?> controller : findControllers()) {
            for (Method method : controller.getDeclaredMethods()) {
                RequirePermission required =
                        AnnotatedElementUtils.findMergedAnnotation(method, RequirePermission.class);
                if (required == null) {
                    continue;
                }
                for (String code : required.value()) {
                    String[] parts = code.split(":");
                    // Sai định dạng thì mã quyền không bao giờ khớp bảng `permissions` → endpoint
                    // chặn tất cả mọi người, kể cả Super Admin, mà không có dấu hiệu gì
                    if (parts.length != 3 || !modules.contains(parts[0])) {
                        malformed.add(controller.getSimpleName() + "#" + method.getName() + " → " + code);
                    }
                }
            }
        }

        assertThat(malformed)
                .as("Mã quyền phải là <module>:<resource>:<action> với module thuộc %s", modules)
                .isEmpty();
    }

    // -------------------------------------------------------------------------

    private static List<Class<?>> findControllers() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));

        List<Class<?>> controllers = new ArrayList<>();
        for (BeanDefinition definition : scanner.findCandidateComponents(BASE_PACKAGE)) {
            try {
                controllers.add(Class.forName(definition.getBeanClassName()));
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Không nạp được lớp " + definition.getBeanClassName(), e);
            }
        }
        return controllers;
    }

    /** Phương thức có ánh xạ HTTP — bỏ qua hàm phụ trợ private/package-private trong controller. */
    private static boolean isEndpoint(Method method) {
        return java.lang.reflect.Modifier.isPublic(method.getModifiers())
                && AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) != null;
    }
}
