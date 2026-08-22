package com.songnhue.operations.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.operations.api.dto.OperationStatusBatchCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusBatchItemRequest;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.domain.ConstructionOperationStatus;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.OperationStatusCodeRepository;

@Service
public class ConstructionOperationStatusService {

    private final ConstructionOperationStatusRepository repository;
    private final OperationStatusCodeRepository codeRepository;
    private final ConstructionRepository constructionRepository;
    private final ConstructionStatusService statusService;

    public ConstructionOperationStatusService(
            ConstructionOperationStatusRepository repository,
            OperationStatusCodeRepository codeRepository,
            ConstructionRepository constructionRepository,
            ConstructionStatusService statusService) {
        this.repository = repository;
        this.codeRepository = codeRepository;
        this.constructionRepository = constructionRepository;
        this.statusService = statusService;
    }

    @Transactional
    public void batchCreate(OperationStatusBatchCreateRequest request) {
        for (OperationStatusBatchItemRequest item : request.getItems()) {
            createSingle(item);
        }
    }

    private void createSingle(OperationStatusBatchItemRequest item) {
        Construction construction = constructionRepository
                .findById(item.getConstructionId())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.SYS_0008));

        OperationStatusCode code = codeRepository
                .findByCodeAndDeletedAtIsNull(item.getOperationCode())
                .orElseThrow(() -> new BusinessRuleException(ErrorCode.SYS_0008));

        if (code.isHasParameter() && item.getParameterValue() == null) {
            throw new BusinessRuleException(ErrorCode.OPS_2006);
        }
        if (!code.isHasParameter() && item.getParameterValue() != null) {
            throw new BusinessRuleException(ErrorCode.OPS_2006);
        }

        ConstructionOperationStatus status = new ConstructionOperationStatus();
        status.setConstructionId(construction.getId());
        status.setOrgUnitId(construction.getOrgUnitId());
        status.setOperationCode(code);
        status.setParameterValue(item.getParameterValue());
        status.setNote(item.getNote());
        status.setEffectiveAt(item.getEffectiveAt());

        repository.save(status);

        // Tính lại trạng thái công trình ngay lập tức
        statusService.recompute(construction);
    }
}
