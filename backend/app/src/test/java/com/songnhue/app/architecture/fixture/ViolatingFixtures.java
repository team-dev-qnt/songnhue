package com.songnhue.app.architecture.fixture;

import org.hibernate.annotations.Filter;

import com.songnhue.core.common.persistence.ScopedEntity;
import com.songnhue.core.domain.workflow.WorkflowAware;

/**
 * Mã <b>cố ý sai</b> — nguyên liệu cho {@link com.songnhue.app.architecture.SilentFailureRuleSelfCheckTest}.
 *
 * <p>Hai luật "hỏng âm thầm" của T10.2 hiện chưa có lớp thật nào để soi (Phase 0 chưa có entity
 * thuộc phạm vi đơn vị, cũng chưa có entity theo quy trình duyệt). Một luật không bao giờ chạy qua
 * lớp nào thì <b>xanh vĩnh viễn</b>, kể cả khi nó viết sai và không bắt được gì — đúng loại yên tâm
 * giả mà cả hai luật này sinh ra để chống.
 *
 * <p>Vì vậy các lớp ở đây đóng vai vi phạm để bài tự kiểm chứng chạy luật lên chúng và đòi hỏi luật
 * phải đỏ. Chúng nằm trong {@code src/test} và bị {@code ImportOption.DoNotIncludeTests} loại khỏi
 * mọi luật thật, nên không làm bẩn kết quả.
 *
 * <p>⚠ Cố ý <b>không</b> gắn {@code @Entity}: {@code @EntityScan} của ứng dụng quét cả
 * {@code com.songnhue}, gắn vào là Hibernate đòi một bảng không tồn tại và mọi test dựng context đều
 * đổ vỡ.
 */
public final class ViolatingFixtures {

    private ViolatingFixtures() {}

    /** Kế thừa {@link ScopedEntity} mà quên {@code @Filter} — đúng lỗi mục 5 của sổ nợ. */
    public static class MissingFilterEntity extends ScopedEntity {}

    /** Có {@code @Filter} nhưng điều kiện tự viết: cấp trên không thấy dữ liệu của cấp dưới. */
    @Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = "org_unit_id = :orgUnitPathPrefix")
    public static class HandWrittenConditionEntity extends ScopedEntity {}

    /** Khai đúng chuẩn — dùng để chứng minh luật không báo nhầm lớp hợp lệ. */
    @Filter(name = ScopedEntity.ORG_UNIT_FILTER, condition = ScopedEntity.ORG_UNIT_FILTER_CONDITION)
    public static class CompliantScopedEntity extends ScopedEntity {}

    /** Entity theo quy trình duyệt, chỉ để lớp dưới có thứ mà gọi sai. */
    public static class ApprovableDocument implements WorkflowAware {

        private String state = "DU_THAO";

        @Override
        public String workflowEntityType() {
            return "fixture_document";
        }

        @Override
        public String currentState() {
            return state;
        }

        @Override
        public void applyState(String newState) {
            this.state = newState;
        }

        @Override
        public Long entityId() {
            return 1L;
        }
    }

    /** Đổi trạng thái không qua engine — đúng lỗi mục 19 của sổ nợ. */
    public static class BypassesWorkflowEngine {

        public void approve(ApprovableDocument document) {
            document.applyState("DA_DUYET");
        }
    }
}
