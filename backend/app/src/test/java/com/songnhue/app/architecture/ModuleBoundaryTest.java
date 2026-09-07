package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

    /**
     * Package-private có chủ đích: {@link ModuleBoundarySelfCheckTest} chạy đúng luật này lên mã cố ý
     * sai để chứng minh nó bắt được vi phạm. Nhân bản luật sang bài tự kiểm là kiểm một bản sao.
     */
    static final ArchRule CHI_IMPORT_SPI_CUA_MODULE_KHAC = classes()
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

    /**
     * ⭐⭐ <b>DOD2.1</b> — luật {@code spi/} chạy trên một tập <b>KHÁC RỖNG</b>.
     *
     * <h2>Vì sao bài này tồn tại</h2>
     *
     * <p>{@link #CHI_IMPORT_SPI_CUA_MODULE_KHAC} chỉ biết báo <i>vi phạm</i>. Nó ⛔ không hề khẳng
     * định rằng có <b>lượt đi qua hợp lệ</b> nào — nên một hệ mà <i>không module nào gọi module
     * nào</i> làm nó xanh trọn vẹn. Đó chính xác là tình trạng của {@code hydro} cho tới 04/09/2026:
     * {@code content} và {@code operations} import {@code com.songnhue.hydro.*} đúng <b>0 lần</b>,
     * và ranh giới trông như đang được canh trong khi thật ra <b>chưa ai đi qua nó</b> (luật 7 —
     * <i>một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai</i>).
     *
     * <p>⚠ Đây cũng là hình dạng đã trả giá nhiều lần ở dự án này: ArchUnit suốt Phase 0 tìm ra 0
     * bài kiểm, tầng 3 phân quyền chạy qua tập rỗng, ISR revalidate chưa ai gọi. Một luật xanh trên
     * tập rỗng <b>đọc y hệt</b> một luật xanh vì mã đúng.
     *
     * <p>⛔ Khẳng định là <b>về SỐ LƯỢNG và về CẶP module cụ thể</b>, không phải một mẫu chuỗi —
     * một phép đếm không chia sẻ giả định nào với điều kiện {@code startsWith} ở luật trên (luật 29).
     */
    @Test
    @DisplayName("⭐ DOD2.1 — luật spi/ chạy trên tập KHÁC RỖNG: `operations → hydro.spi` có lượt đi qua thật")
    void theSpiRuleRunsOnANonEmptySet() {
        Map<String, Set<String>> quaSpi = new TreeMap<>();
        for (JavaClass lop : ProductionClasses.ALL) {
            String from = moduleOf(lop.getPackageName());
            if (from == null) {
                continue;
            }
            for (Dependency phuThuoc : lop.getDirectDependenciesFromSelf()) {
                String targetPackage = phuThuoc.getTargetClass().getPackageName();
                String to = moduleOf(targetPackage);
                if (to == null || to.equals(from)) {
                    continue;
                }
                if (targetPackage.startsWith("com.songnhue." + to + ".spi")) {
                    quaSpi.computeIfAbsent(from + " → " + to, k -> new TreeSet<>())
                            .add(lop.getName());
                }
            }
        }

        assertThat(quaSpi)
                .as("⛔ Không cặp module nào đi qua `spi/` ⇒ luật ranh giới đang chạy trên TẬP RỖNG "
                        + "và cái xanh của nó không nói lên điều gì")
                .isNotEmpty();

        assertThat(quaSpi)
                .as("⭐ T35.6 — ô KPI thuỷ văn của dashboard đọc `hydro` qua `hydro.spi`. Mất cặp này "
                        + "nghĩa là ai đó đã gỡ cạnh Maven `operations → hydro`, hoặc đã lách qua "
                        + "`core.spi` — cả hai đều phải là quyết định có ý thức, ⛔ không phải hệ quả "
                        + "phụ của một lượt dọn dẹp")
                .containsKey("operations → hydro");
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
