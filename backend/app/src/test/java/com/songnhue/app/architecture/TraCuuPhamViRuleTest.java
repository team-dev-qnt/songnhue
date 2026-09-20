package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaParameterizedType;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import com.songnhue.core.common.persistence.ScopeGuard;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * <b>Tra một entity phạm vi theo {@code publicId} thì phải đi qua {@link ScopeGuard} — T73.1.</b>
 *
 * <h2>Khuyết tật mà luật này sinh ra để chặn</h2>
 *
 * Bộ lọc phạm vi ({@code ScopeFilterAspect}) giấu bản ghi của đơn vị khác, nên một lượt tra theo {@code publicId}
 * trả {@code Optional.empty()}. Nếu nơi gọi tự ném {@code SYS-0004} thì người dùng nhận <b>404</b> và ⛔ dòng
 * {@code security_events} nào — người dò {@code publicId} của đơn vị khác trông y hệt người gõ nhầm (M5.16).
 * {@code ScopeGuard.require} phân biệt hai trạng thái ấy và để lại dấu vết. Đo 19/09/2026: 14 lượt tra như vậy,
 * <b>6</b> ⛔ qua {@code ScopeGuard} (5 ở {@code hydro}, 1 ở {@code hr}) — sổ chỉ ghi 5; cái thứ sáu
 * ({@code DonNghiPhepService.get}) lộ ra khi đo toàn kho.
 *
 * <h2>Luật</h2>
 *
 * Mọi phương thức gọi một {@code findByPublicId*} mà kiểu trả về là {@code Optional<X>} hoặc {@code X} với X
 * là một {@link ScopedEntity} phải gọi cả {@code ScopeGuard.require}. Phạm vi do bộ luật <b>ĐO</b> từ bytecode
 * (luật 28): entity phạm vi mới, repository mới, service mới đều tự rơi vào tầm soi, ⛔ có danh sách gõ tay.
 *
 * <h2>⚠ Phạm vi — nói ra thay vì để người đọc suy (luật 28)</h2>
 *
 * <ul>
 *   <li>Canh <b>đồng hiện</b> trong cùng phương thức, ⛔ canh luồng dữ liệu: một phương thức gọi cả hai mà ⛔ đưa
 *       kết quả tra vào {@code require} sẽ lọt. Đổi lại, luật ⛔ phụ thuộc cách viết.
 *   <li>⛔ soi tra cứu trả DANH SÁCH ({@code findByPublicIdIn…}) — {@code require} chỉ nhận một bản ghi.
 *   <li>⛔ soi tra theo khoá nội bộ ({@code findById}): khoá ấy ⛔ đến từ người dùng.
 * </ul>
 */
class TraCuuPhamViRuleTest {

    private static final String TIEN_TO_TRA_CUU = "findByPublicId";

    // =========================================================================
    // Luật — khai riêng để bài tự-kiểm chạy lại đúng nó, ⛔ chép lại
    // =========================================================================

    static ArchRule traCuuPhamViQuaScopeGuard() {
        return classes().should(new ArchCondition<>("tra entity phạm vi theo publicId qua ScopeGuard.require") {
            @Override
            public void check(JavaClass lop, ConditionEvents events) {
                for (JavaMethodCall goi : lop.getMethodCallsFromSelf()) {
                    if (!laTraCuuPhamVi(goi)) {
                        continue;
                    }
                    boolean coGuard = goi.getOrigin().getMethodCallsFromSelf().stream()
                            .anyMatch(TraCuuPhamViRuleTest::laScopeGuardRequire);
                    if (!coGuard) {
                        events.add(SimpleConditionEvent.violated(
                                lop,
                                ("%s gọi %s mà ⛔ qua ScopeGuard.require — bản ghi của đơn vị khác sẽ ra 404 im lặng, "
                                                + "⛔ dòng security_events nào (M5.16, T73.1)")
                                        .formatted(
                                                goi.getOrigin().getFullName(),
                                                goi.getTarget().getFullName())));
                    }
                }
            }
        });
    }

    /** Một lời gọi {@code findByPublicId*} mà kiểu trả về là (Optional của) một {@link ScopedEntity}. */
    static boolean laTraCuuPhamVi(JavaMethodCall goi) {
        if (!goi.getName().startsWith(TIEN_TO_TRA_CUU)) {
            return false;
        }
        return goi.getTarget()
                .resolveMember()
                .map(JavaMethod::getReturnType)
                .map(TraCuuPhamViRuleTest::entityPhamViCua)
                .orElse(false);
    }

    private static boolean entityPhamViCua(JavaType kieu) {
        if (kieu instanceof JavaParameterizedType p
                && p.toErasure().isEquivalentTo(java.util.Optional.class)
                && p.getActualTypeArguments().size() == 1) {
            return p.getActualTypeArguments().get(0).toErasure().isAssignableTo(ScopedEntity.class);
        }
        return kieu.toErasure().isAssignableTo(ScopedEntity.class);
    }

    private static boolean laScopeGuardRequire(JavaMethodCall goi) {
        return goi.getTargetOwner().isEquivalentTo(ScopeGuard.class) && "require".equals(goi.getName());
    }

    // =========================================================================
    // Chạy trên mã thật
    // =========================================================================

    @Test
    @DisplayName("⛔⛔ Tra entity phạm vi theo publicId phải qua ScopeGuard.require — ⛔ 404 im lặng (T73.1)")
    void traCuuPhamViPhaiQuaScopeGuard() {
        traCuuPhamViQuaScopeGuard().check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Vế chống tập rỗng — bộ luật phải NHÌN THẤY đủ lượt tra và đủ loại entity phạm vi")
    void phaiNhinThayDuLuotTra() {
        Set<String> noiGoi = new TreeSet<>();
        Set<String> entity = new TreeSet<>();
        JavaClasses tat = ProductionClasses.ALL;
        for (JavaClass lop : tat) {
            for (JavaMethodCall goi : lop.getMethodCallsFromSelf()) {
                if (laTraCuuPhamVi(goi)) {
                    noiGoi.add(goi.getOrigin().getFullName());
                    goi.getTarget()
                            .resolveMember()
                            .map(JavaMethod::getReturnType)
                            .ifPresent(k -> entity.add(
                                    k instanceof JavaParameterizedType p
                                            ? p.getActualTypeArguments().get(0).getName()
                                            : k.getName()));
                }
            }
        }
        // Đo 19/09/2026 sau bản vá: 10 lượt tra trên 5 loại entity (Employee · Station · Construction ·
        // MaintenanceLog · LeaveRequest). Một bộ đọc kiểu tổng quát hỏng cho ra 0 và luật chính XANH (luật 7).
        assertThat(noiGoi).as("lượt tra entity phạm vi theo publicId").hasSizeGreaterThanOrEqualTo(8);
        assertThat(entity).as("loại entity phạm vi được tra").hasSizeGreaterThanOrEqualTo(5);
    }
}
