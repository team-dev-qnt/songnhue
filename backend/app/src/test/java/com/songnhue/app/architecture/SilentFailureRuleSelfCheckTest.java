package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;

import com.songnhue.app.architecture.fixture.ViolatingFixtures;
import com.songnhue.core.common.persistence.ScopedEntity;

/**
 * <b>Ai canh người canh gác.</b>
 *
 * <p>Hai luật "hỏng âm thầm" của {@link SilentFailureRuleTest} hiện chạy qua <i>không lớp nào</i> —
 * Phase 0 chưa có entity thuộc phạm vi đơn vị, cũng chưa có entity theo quy trình duyệt. Một luật
 * không có gì để soi thì xanh vĩnh viễn, kể cả khi biểu thức bên trong nó viết sai. Đó chính là loại
 * "yên tâm giả" mà hai luật kia sinh ra để chống, nên để chúng ở trạng thái ấy mà không kiểm chứng
 * là tự mâu thuẫn.
 *
 * <p>Bài kiểm này chạy đúng hai luật đó lên {@link ViolatingFixtures} — mã cố ý sai — và đòi hỏi
 * chúng phải <b>đỏ</b>. Ngày Phase 1 thêm entity đầu tiên, luật đã được chứng minh là bắt được vi
 * phạm chứ không phải một lời hứa suông.
 */
class SilentFailureRuleSelfCheckTest {

    private static final String FIXTURE_PACKAGE = ViolatingFixtures.class.getPackageName();

    /** Nhập riêng gói fixture — luật thật loại {@code src/test} bằng {@code DoNotIncludeTests}. */
    private static final JavaClasses FIXTURES = new ClassFileImporter().importPackages(FIXTURE_PACKAGE);

    @Test
    @DisplayName("Luật ScopedEntity bắt được lớp con quên @Filter")
    void catchesMissingFilter() {
        assertThatThrownBy(() -> scopedEntityRule().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.MissingFilterEntity.class.getSimpleName())
                .hasMessageContaining("không khai @Filter");
    }

    @Test
    @DisplayName("Luật ScopedEntity bắt được điều kiện lọc tự viết")
    void catchesHandWrittenCondition() {
        assertThatThrownBy(() -> scopedEntityRule().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.HandWrittenConditionEntity.class.getSimpleName())
                .hasMessageContaining("điều kiện tự viết");
    }

    @Test
    @DisplayName("Luật ScopedEntity không báo nhầm lớp khai đúng chuẩn")
    void acceptsCompliantEntity() {
        EvaluationResult result = scopedEntityRule().evaluate(FIXTURES);

        assertThat(result.getFailureReport().getDetails())
                .as("lớp khai đúng @Filter + hằng điều kiện dùng chung không được bị báo vi phạm")
                .noneMatch(detail -> detail.contains(ViolatingFixtures.CompliantScopedEntity.class.getSimpleName()));
    }

    // ---- Ngoại lệ "cột phạm vi NULLable" (thêm ở WS-28, khi Station là lớp đầu tiên cần nó) ----

    /**
     * ⚠ <b>Cả hai fixture nới đều nằm trong danh sách</b> — và đó là chủ ý.
     *
     * <p>Nếu chỉ cho {@code NullableScopeCompliant} vào danh sách thì
     * {@link #catchesUnsanctionedWidening()} sẽ xanh vì <i>lý do sai</i>: nó đỏ do lớp kia không có
     * tên trong danh sách, chứ không phải do mẫu điều kiện bị từ chối. Cho cả hai vào thì thứ duy
     * nhất phân biệt được chúng là <b>hình dạng chuỗi điều kiện</b> — đúng thứ luật phải kiểm.
     *
     * <p>Đây là bài học §10.62: một bài kiểm chứng ngược có thể xanh theo đúng cách nó đang sai.
     */
    private static final Set<String> FIXTURE_CHO_PHEP_NOI = Set.of(
            ViolatingFixtures.NullableScopeCompliant.class.getSimpleName(),
            ViolatingFixtures.NullableScopeUnsanctioned.class.getSimpleName());

    @Test
    @DisplayName("Luật ScopedEntity cho qua vế nới `IS NULL` của entity có cột phạm vi NULLable")
    void acceptsSanctionedNullableWidening() {
        EvaluationResult result = scopedEntityRule().evaluate(FIXTURES);

        assertThat(result.getFailureReport().getDetails())
                .as(
                        """
                        Station cần vế `org_unit_id IS NULL` vì 19 điểm đo được seed trước khi OI-05 chốt \
                        7 hay 8 Xí nghiệp. Bài này đỏ nghĩa là luật đang ép một điều kiện làm 19/19 điểm \
                        đo vô hình với TẤT CẢ, kể cả SUPER_ADMIN.""")
                .noneMatch(detail -> detail.contains(ViolatingFixtures.NullableScopeCompliant.class.getSimpleName()));
    }

    @Test
    @DisplayName("⚠⚠ Luật ScopedEntity bắt được vế nới `1=1 OR` dù nó CÓ chứa hằng dùng chung")
    void catchesUnsanctionedWidening() {
        assertThatThrownBy(() -> scopedEntityRule().check(FIXTURES))
                .as(
                        """
                        `(1=1 OR <điều kiện chuẩn>)` chứa nguyên văn hằng dùng chung, nên một phép kiểm \
                        `contains()` sẽ cho qua — trong khi bộ lọc phạm vi đã tắt hoàn toàn. Bài này là \
                        lý do luật phải dùng mẫu chặt.""")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.NullableScopeUnsanctioned.class.getSimpleName())
                .hasMessageContaining("không đúng dạng nới được phép");
    }

    @Test
    @DisplayName("⚠ Vế nới `IS NULL` KHÔNG được cho qua nếu lớp không có tên trong danh sách")
    void wideningRequiresBeingOnTheList() {
        assertThatThrownBy(() -> scopedEntityRule(Set.of()).check(FIXTURES))
                .as(
                        """
                        Danh sách PHAM_VI_NULL_DUOC_PHEP phải thật sự có hiệu lực. Nếu bài này xanh thì \
                        mọi entity đều tự nới được bộ lọc phạm vi chỉ bằng cách viết đúng cú pháp — \
                        danh sách trở thành trang trí.""")
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.NullableScopeCompliant.class.getSimpleName())
                .hasMessageContaining("điều kiện tự viết");
    }

    @Test
    @DisplayName("Luật workflow bắt được lời gọi applyState() ngoài WorkflowEngine")
    void catchesApplyStateCalledOutsideEngine() {
        assertThatThrownBy(() -> workflowRule().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.BypassesWorkflowEngine.class.getSimpleName())
                .hasMessageContaining("applyState");
    }

    @Test
    @DisplayName("⚠⚠ Luật giao dịch bắt được hàm không-@Transactional tự gọi hàm @Transactional")
    void catchesSelfInvokedTransactionalMethod() {
        assertThatThrownBy(() -> transactionRule().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.SelfInvokesTransactional.class.getSimpleName())
                .hasMessageContaining("giao dịch KHÔNG mở");
    }

    @Test
    @DisplayName("Luật giao dịch bắt được cả lời gọi tự thân sang hàm đòi giao dịch riêng")
    void catchesSelfInvokedRequiresNew() {
        assertThatThrownBy(() -> transactionRule().check(FIXTURES))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(ViolatingFixtures.SelfInvokesRequiresNew.class.getSimpleName())
                .hasMessageContaining("giao dịch riêng");
    }

    @Test
    @DisplayName("Luật giao dịch không báo nhầm hai hàm cùng @Transactional mặc định")
    void acceptsCompliantTransactionalPair() {
        EvaluationResult result = transactionRule().evaluate(FIXTURES);

        assertThat(result.getFailureReport().getDetails())
                .as("giao dịch đã mở từ lượt gọi ngoài vào — đây là cách viết hợp lệ, luật báo là luật vô dụng")
                .noneMatch(
                        detail -> detail.contains(ViolatingFixtures.CompliantTransactionalPair.class.getSimpleName()));
    }

    // -------------------------------------------------------------------------
    // Dựng lại cùng biểu thức luật với SilentFailureRuleTest. Cố ý không tái sử dụng trực tiếp hai
    // hằng @ArchTest ở đó: hằng của lớp @AnalyzeClasses đã bị bộ máy ArchUnit gắn vào tập lớp
    // production, gọi check() lần nữa trên tập khác cho kết quả khó lường.

    /** Luật thật, chạy trên fixture — danh sách nới là {@link #FIXTURE_CHO_PHEP_NOI}. */
    private static ArchRule scopedEntityRule() {
        return scopedEntityRule(FIXTURE_CHO_PHEP_NOI);
    }

    private static ArchRule scopedEntityRule(Set<String> chophepNoi) {
        return classes()
                .that()
                .areAssignableTo(ScopedEntity.class)
                .and()
                .doNotHaveFullyQualifiedName(ScopedEntity.class.getName())
                .should(new SilentFailureRuleTest.CarriesOrgUnitFilter(chophepNoi))
                .allowEmptyShould(true);
    }

    private static ArchRule workflowRule() {
        return noClasses()
                .that()
                .resideOutsideOfPackage(SilentFailureRuleTest.ENGINE_PACKAGE)
                .should(new SilentFailureRuleTest.CallsApplyState());
    }

    private static ArchRule transactionRule() {
        return noClasses().should(new SilentFailureRuleTest.SelfInvokesTransactionalMethod());
    }
}
