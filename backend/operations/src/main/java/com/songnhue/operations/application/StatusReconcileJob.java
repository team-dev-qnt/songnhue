package com.songnhue.operations.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.JobContext;
import com.songnhue.core.spi.JobHandler;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.OperationalStatus;
import com.songnhue.operations.infra.ConstructionRepository;

@Component
public class StatusReconcileJob implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(StatusReconcileJob.class);

    private final ConstructionRepository constructionRepository;
    private final ConstructionStatusService statusService;

    public StatusReconcileJob(ConstructionRepository constructionRepository, ConstructionStatusService statusService) {
        this.constructionRepository = constructionRepository;
        this.statusService = statusService;
    }

    @Override
    public String jobType() {
        return "STATUS_RECONCILE";
    }

    @Override
    @Transactional
    public void handle(JobContext context) throws Exception {
        List<Construction> constructions = constructionRepository.findAll();

        int total = constructions.size();
        if (total == 0) {
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
}
