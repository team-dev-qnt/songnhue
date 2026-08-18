package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

/**
 * Ranh giới giữa 5 module nghiệp vụ — quy tắc 6 của dự án, {@code conventions.md} §1.1.
 *
 * <p><b>Vì sao phải có bài kiểm này chứ không dựa vào Maven scope.</b> Modular Monolith gom cả 5
 * module vào <i>một</i> process, nên lúc chạy chúng nằm chung classpath và trình biên dịch không có
 * cách nào ngăn {@code operations} gọi thẳng repository của {@code hr}. Ranh giới module ở đây là
 * một <b>thoả thuận</b>, và thoả thuận không có ai canh thì tan trong vài sprint. Đây là chốt chặn
 * duy nhất.
 *
 * <p>Cái giá của việc để nó tan không phải là "code xấu": khi tách service về sau (mốc
 * {@code architecture-review.md} §6.4), mỗi lời gọi chéo trái phép là một chỗ phải viết lại. Chi phí
 * đó không nhìn thấy được ở thời điểm gây ra nó.
 *
 * <p>Bài kiểm đặt ở module {@code app} vì đây là nơi <b>duy nhất</b> cả 5 module cùng nằm trên
 * classpath — ở {@code core} thì không có gì để so.
 */
class ModuleBoundaryTest {

    /**
     * 5 module nghiệp vụ. {@code app} cố ý không nằm trong danh sách: nó là tầng lắp ráp, việc của nó
     * đúng là biết tới mọi module.
     */
    private static final Set<String> MODULES = Set.of("core", "content", "operations", "hydro", "hr");

    /** Ngoại lệ được phép import chéo — Common Platform, xem {@code conventions.md} §1.1. */
    private static final String COMMON_PLATFORM = "com.songnhue.core.common";

    private static final ArchRule CHI_IMPORT_SPI_CUA_MODULE_KHAC = classes()
            .that()
            .resideInAnyPackage(modulePackages())
            .should(new OnlyCrossModuleThroughSpi())
            .because(
                    """
                    conventions.md §1.1: module khác chỉ được import `spi/`, cộng ngoại lệ duy nhất là \
                    `core.common.*` (Common Platform — hạ tầng dùng chung, không phải dịch vụ nghiệp vụ). \
                    Cần dữ liệu của module khác thì thêm phương thức vào interface `spi/` của module đó, \
                    đừng gọi thẳng repository.""");

    /**
     * Module nghiệp vụ không được biết tới tầng lắp ráp.
     *
     * <p>Phụ thuộc ngược chiều này làm module không còn tách ra được, và nó thường lẻn vào qua một
     * hằng số hay một lớp cấu hình "cho tiện".
     */
    private static final ArchRule KHONG_PHU_THUOC_NGUOC_LEN_APP = classes()
            .that()
            .resideInAnyPackage(modulePackages())
            .should()
            .onlyDependOnClassesThat()
            .resideOutsideOfPackage("com.songnhue.app..")
            .because("`app` là tầng lắp ráp — phụ thuộc ngược lên nó làm module không tách ra được nữa");

    @Test
    @DisplayName("Module chỉ được import spi/ của module khác (ngoại lệ: core.common.*)")
    void crossModuleAccessGoesThroughSpi() {
        CHI_IMPORT_SPI_CUA_MODULE_KHAC.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Module nghiệp vụ không phụ thuộc ngược lên tầng lắp ráp `app`")
    void modulesDoNotDependOnTheAssembler() {
        KHONG_PHU_THUOC_NGUOC_LEN_APP.check(ProductionClasses.ALL);
    }

    // -------------------------------------------------------------------------

    private static String[] modulePackages() {
        return MODULES.stream().map(m -> "com.songnhue." + m + "..").toArray(String[]::new);
    }

    /** {@code null} nếu không thuộc module nghiệp vụ nào (JDK, thư viện ngoài, hay chính {@code app}). */
    private static String moduleOf(String packageName) {
        if (!packageName.startsWith("com.songnhue.")) {
            return null;
        }
        String rest = packageName.substring("com.songnhue.".length());
        int dot = rest.indexOf('.');
        String candidate = dot < 0 ? rest : rest.substring(0, dot);
        return MODULES.contains(candidate) ? candidate : null;
    }

    private static final class OnlyCrossModuleThroughSpi extends ArchCondition<JavaClass> {

        private OnlyCrossModuleThroughSpi() {
            super("chỉ phụ thuộc vào `spi/` của module khác, hoặc vào `core.common.*`");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            String from = moduleOf(item.getPackageName());
            if (from == null) {
                return;
            }
            for (Dependency dependency : item.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                String to = moduleOf(targetPackage);
                if (to == null || to.equals(from)) {
                    continue;
                }
                boolean allowed = targetPackage.startsWith("com.songnhue." + to + ".spi")
                        || targetPackage.startsWith(COMMON_PLATFORM);
                if (!allowed) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "%s → %s: module `%s` chạm vào phần bên trong của module `%s` (%s)"
                                    .formatted(
                                            item.getName(),
                                            dependency.getTargetClass().getName(),
                                            from,
                                            to,
                                            dependency.getDescription())));
                }
            }
        }
    }
}
