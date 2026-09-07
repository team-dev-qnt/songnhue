package com.songnhue.content.boundaryfixture;

import java.util.List;
import java.util.UUID;

import com.songnhue.core.application.workflow.WorkflowEngine;
import com.songnhue.core.common.persistence.BaseEntity;
import com.songnhue.core.common.persistence.WorkflowAware;
import com.songnhue.core.domain.attachment.Attachment;
import com.songnhue.core.spi.AllowedAction;
import com.songnhue.core.spi.AttachmentPort;
import com.songnhue.core.spi.AttachmentRef;
import com.songnhue.core.spi.AttachmentUploadCommand;
import com.songnhue.core.spi.JobPort;
import com.songnhue.core.spi.JobRef;
import com.songnhue.core.spi.JobRequest;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.core.spi.SettingPort;
import com.songnhue.core.spi.WorkflowPort;

/**
 * Nguyên liệu cho {@link com.songnhue.app.architecture.ModuleBoundarySelfCheckTest} — <b>vừa mã
 * đúng vừa mã cố ý sai</b>, cùng nằm dưới một gói thuộc module {@code content}.
 *
 * <p><b>Vì sao phải có.</b> Luật ranh giới module chạy xanh suốt Phase 0, nhưng bốn module nghiệp vụ
 * còn rỗng nên nó chạy qua <b>tập rỗng</b> — xanh mà chưa chứng minh được gì
 * (architecture-review.md §9.14). WS-12 mở {@code core/spi}, tức là lần đầu có một đường đi hợp lệ
 * xuyên module; bài tự kiểm dùng các lớp dưới đây để đòi hỏi luật <i>cho qua</i> đường hợp lệ và
 * <i>chặn</i> hai đường sai.
 *
 * <p>Gói cố ý đặt tên {@code com.songnhue.content..} chứ không phải {@code com.songnhue.app..}: luật
 * phân loại module <b>theo tên gói</b>, đặt ở {@code app} thì nó bỏ qua sạch. Tệp nằm trong
 * {@code src/test} nên {@code ImportOption.DoNotIncludeTests} loại nó khỏi mọi lượt chạy luật thật.
 *
 * <p>⚠ Cố ý không gắn {@code @Entity} — cùng lý do với {@code ViolatingFixtures}: {@code @EntityScan}
 * quét cả {@code com.songnhue} và Hibernate sẽ đòi một bảng không tồn tại.
 */
public final class BoundaryFixtures {

    private BoundaryFixtures() {}

    /**
     * ✅ <b>Đường hợp lệ</b> — đúng cách một service của module nghiệp vụ gọi Core từ Phase 1 trở đi:
     * chỉ chạm {@code core.spi.*} và {@code core.common.*}.
     *
     * <p>Nếu lớp này bị luật báo vi phạm thì SPI vừa mở ra là vô dụng, và bài tự kiểm phải kêu ngay
     * chứ không đợi tới lúc WS-13 viết dòng mã thật đầu tiên.
     */
    public static class LegitSpiConsumer {

        private final WorkflowPort workflow;
        private final AttachmentPort attachments;
        private final NotificationPort notifications;
        private final JobPort jobs;
        private final SettingPort settings;
        private final OrgUnitPort orgUnits;

        public LegitSpiConsumer(
                WorkflowPort workflow,
                AttachmentPort attachments,
                NotificationPort notifications,
                JobPort jobs,
                SettingPort settings,
                OrgUnitPort orgUnits) {
            this.workflow = workflow;
            this.attachments = attachments;
            this.notifications = notifications;
            this.jobs = jobs;
            this.settings = settings;
            this.orgUnits = orgUnits;
        }

        public List<AllowedAction> buttonsFor(WorkflowAware entity) {
            return workflow.allowedActions(entity);
        }

        public AttachmentRef attach(byte[] content) {
            return attachments.upload(new AttachmentUploadCommand(
                    "FIXTURE", 1L, "anh-minh-hoa", "anh.png", content, List.of("image/png")));
        }

        public JobRef export() {
            return jobs.enqueue(JobRequest.of("FIXTURE_EXPORT", "{}"));
        }

        public void announce() {
            notifications.notify(
                    NotifyRequest.alert("FIXTURE_EVENT", "Tiêu đề", "Nội dung", NotifySeverity.INFO, List.of(1L)));
        }

        public int pageSize() {
            return settings.getInt("fixture.page-size", 20);
        }

        public OrgUnitRef owner(UUID publicId) {
            return orgUnits.findRef(publicId).orElseThrow();
        }
    }

    /** ⛔ Gọi thẳng lớp cụ thể ở {@code core.application} — đúng đường tắt mà SPI sinh ra để chặn. */
    public static class ReachesIntoCoreApplication {

        private final WorkflowEngine engine;

        public ReachesIntoCoreApplication(WorkflowEngine engine) {
            this.engine = engine;
        }

        public void approve(WorkflowAware entity) {
            engine.execute(entity, "APPROVE", "Duyệt");
        }
    }

    /**
     * ⛔ <b>Vi phạm tinh vi hơn</b>: không gọi service nào của {@code core.application}, chỉ <i>nhận
     * về</i> một entity domain.
     *
     * <p>Đây chính là cái bẫy làm cho việc mở SPI lớn hơn "thêm sáu interface": một interface đặt
     * đúng chỗ nhưng trả entity {@code Attachment} thì mọi nơi gọi nó vẫn phải import
     * {@code core.domain.attachment}. Luật phải bắt được cả dạng này, nếu không thì SPI chỉ dời chỗ
     * vi phạm chứ không xoá nó.
     */
    public static class TouchesCoreDomain {

        public String nameOf(Attachment attachment) {
            return attachment.getOriginalName();
        }
    }

    /** Entity nghiệp vụ mẫu — chứng minh {@code BaseEntity}/{@code WorkflowAware} nằm đúng chỗ. */
    public static class FixtureArticle extends BaseEntity implements WorkflowAware {

        private String state = "NHAP";

        @Override
        public String workflowEntityType() {
            return "FIXTURE_ARTICLE";
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
            return getId();
        }
    }
}
