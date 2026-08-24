package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import com.songnhue.content.boundaryfixture.BoundaryFixtures;

/**
 * <b>Ai canh người canh gác</b> — cho luật ranh giới module (WS-12/T12.8).
 *
 * <p>Suốt Phase 0, {@link ModuleBoundaryTest} xanh mọi lượt chạy. Nhưng bốn module nghiệp vụ còn là
 * khung rỗng, nên luật chạy qua <b>tập rỗng</b>: nó chỉ chứng minh chưa có ai thử, không chứng minh
 * nó đúng (architecture-review.md §9.14 — <i>một ranh giới chưa ai đi qua thì chưa biết nó đúng hay
 * sai</i>).
 *
 * <p>WS-12 mở {@code core/spi}, tức là lần đầu có một đường đi hợp lệ xuyên module. Bài kiểm này
 * chạy đúng luật thật lên {@link BoundaryFixtures} và đòi hỏi ba điều — thiếu điều nào cũng đủ làm
 * SPI trở nên vô nghĩa:
 *
 * <ol>
 *   <li>Đường qua {@code core.spi} <b>được cho qua</b>. Nếu không thì SPI vừa mở ra đã không dùng
 *       được, mà triệu chứng sẽ chỉ lộ ra ở dòng mã nghiệp vụ đầu tiên của WS-13.
 *   <li>Gọi thẳng {@code core.application} <b>bị chặn</b> — đường tắt hiển nhiên.
 *   <li>Chạm {@code core.domain} <b>cũng bị chặn</b>. Đây mới là dạng vi phạm khó thấy: một interface
 *       đặt đúng chỗ nhưng <i>trả về</i> entity domain thì nơi gọi vẫn phải import nó, và SPI chỉ dời
 *       chỗ vi phạm chứ không xoá.
 * </ol>
 */
class ModuleBoundarySelfCheckTest {

    private static final String FIXTURE_PACKAGE = BoundaryFixtures.class.getPackageName();

    /** Nhập riêng gói fixture — luật thật loại {@code src/test} bằng {@code DoNotIncludeTests}. */
    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);

    @Test
    @DisplayName("✅ Module dùng core.spi.* + core.common.* thì luật CHO QUA")
    void allowsTheSpiPath() {
        EvaluationResult result = ModuleBoundaryTest.CHI_IMPORT_SPI_CUA_MODULE_KHAC.evaluate(FIXTURES);

        assertThat(result.getFailureReport().getDetails())
                .as(
                        """
                        Lớp chỉ chạm core.spi và core.common phải đi qua được. Nếu bài này đỏ thì SPI \
                        mở ra để làm gì cũng không dùng được, và WS-13 sẽ đâm vào đúng bức tường này.""")
                .noneMatch(detail -> detail.contains(BoundaryFixtures.LegitSpiConsumer.class.getSimpleName()))
                .noneMatch(detail -> detail.contains(BoundaryFixtures.FixtureArticle.class.getSimpleName()));
    }

    @Test
    @DisplayName("⛔ Luật bắt được lời gọi thẳng vào core.application")
    void catchesReachIntoCoreApplication() {
        assertThatThrownBy(() -> ModuleBoundaryTest.CHI_IMPORT_SPI_CUA_MODULE_KHAC.check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(BoundaryFixtures.ReachesIntoCoreApplication.class.getSimpleName())
                .hasMessageContaining("WorkflowEngine");
    }

    @Test
    @DisplayName("⛔ Luật bắt được cả việc chỉ NHẬN VỀ một entity của core.domain")
    void catchesTouchingCoreDomain() {
        assertThatThrownBy(() -> ModuleBoundaryTest.CHI_IMPORT_SPI_CUA_MODULE_KHAC.check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(BoundaryFixtures.TouchesCoreDomain.class.getSimpleName())
                .hasMessageContaining("Attachment");
    }

    @Test
    @DisplayName("Fixture nằm ngoài tập lớp production — không làm bẩn luật thật")
    void fixturesAreExcludedFromProductionScope() {
        assertThat(ProductionClasses.ALL.stream()
                        .map(javaClass -> javaClass.getPackageName())
                        .anyMatch(FIXTURE_PACKAGE::equals))
                .as("gói fixture nằm trong src/test nên DoNotIncludeTests phải loại nó ra")
                .isFalse();
    }
}
