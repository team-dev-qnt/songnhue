package com.songnhue.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.EvaluationResult;

import com.songnhue.app.architecture.fixture.PhamViFixtures;

/**
 * <b>Ai canh người canh gác</b> — {@link TraCuuPhamViRuleTest} chạy trên mã cố ý sai (luật 1).
 *
 * <p>Luật kia chạy trên một kho đã sạch nên nó xanh; cái xanh ấy ⛔ phân biệt được <i>"bắt được và ⛔ có vi phạm"</i>
 * với <i>"hỏng nên ⛔ bắt được gì"</i> (luật 9). Ba bản làm ĐÚNG phải ⛔ bị báo — thiếu vế ấy thì một luật "từ
 * chối tất cả" cũng qua.
 */
class TraCuuPhamViRuleSelfCheckTest {

    private static final JavaClasses FIXTURES = new ClassFileImporter()
            .importClasses(
                    PhamViFixtures.DonViGia.class,
                    PhamViFixtures.DanhMucGia.class,
                    PhamViFixtures.DonViGiaRepo.class,
                    PhamViFixtures.DanhMucGiaRepo.class,
                    PhamViFixtures.Tra404ImLang.class,
                    PhamViFixtures.TraQuaScopeGuard.class,
                    PhamViFixtures.TraTrongLambda.class,
                    PhamViFixtures.TraDanhMuc.class);

    @Test
    @DisplayName("⛔ Bắt được lượt tra entity phạm vi 404 im lặng — ĐÚNG MỘT vi phạm, gọi đích danh")
    void batDuoc404ImLang() {
        EvaluationResult kq = TraCuuPhamViRuleTest.traCuuPhamViQuaScopeGuard().evaluate(FIXTURES);

        assertThat(kq.getFailureReport().getDetails())
                .as("chỉ Tra404ImLang vi phạm; TraQuaScopeGuard · TraTrongLambda · TraDanhMuc làm đúng")
                .hasSize(1)
                .allMatch(d -> d.contains(PhamViFixtures.Tra404ImLang.class.getName() + ".lay"));
    }
}
