package com.songnhue.operations.application;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.core.spi.NotificationPort;
import com.songnhue.core.spi.NotifyRequest;
import com.songnhue.core.spi.NotifySeverity;
import com.songnhue.core.spi.SettingPort;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Đối soát trạng thái công trình mỗi đêm — mắt xích cuối của CN-02.1.
 *
 * <h2>Vì sao cần đối soát khi mọi đường ghi đã tự tính lại</h2>
 *
 * <p>Trạng thái được tính lại tại chỗ ở mỗi lượt ghi sửa chữa / tình hình vận hành. Nhưng có ba
 * nguồn lệch mà lượt ghi không bắt được: một bản ghi hết hiệu lực <b>do thời gian trôi</b> chứ không
 * do ai bấm gì; một lượt sửa danh mục mã bị lỗi giữa chừng; và những đường ghi tương lai của Phase 2
 * chưa tồn tại hôm nay. Job này là nơi phát hiện chúng — nó <b>báo</b> mọi chênh lệch ra log thay vì
 * lặng lẽ sửa, vì một cột dẫn xuất lệch thường là triệu chứng của một đường ghi bỏ sót lời gọi tính
 * lại, và sửa lặng lẽ thì đường ghi đó không bao giờ bị phát hiện.
 */
@Component
public class StatusReconcileJob implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(StatusReconcileJob.class);

    /** Xem {@code SettingKeys.OPS_OPERATION_STATUS_STALE_DAYS} — seed 7 ngày, sửa được ở UI. */
    private static final String KEY_STALE_DAYS = "ops.operation-status.stale-days";

    private static final int STALE_DAYS_MAC_DINH = 7;

    private final ConstructionRepository constructionRepository;
    private final ConstructionOperationStatusRepository operationStatuses;
    private final ConstructionStatusService statusService;
    private final SettingPort settings;
    private final NotificationPort notifications;

    public StatusReconcileJob(
            ConstructionRepository constructionRepository,
            ConstructionOperationStatusRepository operationStatuses,
            ConstructionStatusService statusService,
            SettingPort settings,
            NotificationPort notifications) {
        this.constructionRepository = constructionRepository;
        this.operationStatuses = operationStatuses;
        this.statusService = statusService;
        this.settings = settings;
        this.notifications = notifications;
    }

    @Override
    public String jobType() {
        return "STATUS_RECONCILE";
    }

    @Override
    @Transactional
    public void handle(JobContext context) throws Exception {
        // ⚠ KHÔNG dùng findAll(): nó gồm cả hồ sơ đã xoá mềm. Tính lại trạng thái cho một hồ sơ đã
        // xoá là ghi vào bản ghi mà không màn hình nào đọc, và nó làm số liệu "phát hiện N lệch"
        // phồng lên bằng những dòng không ai xử lý được.
        doiSoatTrangThai(context);
        canhBaoQuaHanCapNhat();
    }

    private void doiSoatTrangThai(JobContext context) {
        List<Construction> constructions = constructionRepository.findByDeletedAtIsNull();

        int total = constructions.size();
        if (total == 0) {
            log.info("Không có công trình nào để đối soát");
            return;
        }

        int diffCount = 0;
        int i = 0;
        for (Construction construction : constructions) {
            OperationalStatus oldStatus = construction.getOperationalStatus();
            OperationalStatus newStatus = statusService.recompute(construction);

            if (oldStatus != newStatus) {
                log.warn(
                        "Lệch trạng thái công trình {}: CSDL đang là {}, tính lại ra {}",
                        construction.getCode(),
                        oldStatus,
                        newStatus);
                diffCount++;
            }

            i++;
            if (i % 50 == 0) {
                context.progress((i * 100) / total);
            }
        }

        log.info("Đối soát xong {} công trình, phát hiện {} lệch trạng thái", total, diffCount);
    }

    /**
     * Cảnh báo mềm "lâu rồi chưa cập nhật tình hình vận hành" — CN-02.11, chốt G4.
     *
     * <p>Tham số {@code ops.operation-status.stale-days} đã được seed từ WS-2 và bày ra ở màn hình
     * cấu hình, nhưng suốt Phase 1 <b>không dòng mã nào đọc nó</b>. Người quản trị chỉnh từ 7 xuống
     * 3 và không có gì xảy ra — đúng loại lỗi mà luật 12 gọi tên: công tắc chưa ai đọc là một lỗi,
     * không phải việc để dành.
     */
    private void canhBaoQuaHanCapNhat() {
        int soNgay = settings.getInt(KEY_STALE_DAYS, STALE_DAYS_MAC_DINH);
        OffsetDateTime moc = OffsetDateTime.now(DateTimeUtils.ZONE_VN).minus(Duration.ofDays(soNgay));

        List<Long> quaHan = operationStatuses.congTrinhQuaHanCapNhat(moc);
        if (quaHan.isEmpty()) {
            log.info("Không có công trình nào quá {} ngày chưa cập nhật tình hình vận hành", soNgay);
            return;
        }

        log.warn("{} công trình quá {} ngày chưa cập nhật tình hình vận hành", quaHan.size(), soNgay);
        notifications.notify(NotifyRequest.targeted(
                "OPERATION_STATUS_STALE",
                "Tình hình vận hành quá hạn cập nhật",
                quaHan.size() + " công trình đã quá " + soNgay + " ngày chưa ghi nhận tình hình vận hành.",
                NotifySeverity.WARNING,
                "ops:operation-status:update",
                List.of()));
    }
}
