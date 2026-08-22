package com.songnhue.operations.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.operations.api.dto.OperationStatusCodeCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusCodeUpdateRequest;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.OperationStatusCodeRepository;

@Service
@Transactional(readOnly = true)
public class OperationStatusCodeService {

    private final OperationStatusCodeRepository repository;
    private final ConstructionOperationStatusRepository statusRepository;
    private final ConstructionStatusService constructionStatusService;

    public OperationStatusCodeService(
            OperationStatusCodeRepository repository,
            ConstructionOperationStatusRepository statusRepository,
            ConstructionStatusService constructionStatusService) {
        this.repository = repository;
        this.statusRepository = statusRepository;
        this.constructionStatusService = constructionStatusService;
    }

    public List<OperationStatusCode> findAll() {
        return repository.findAll(); // Assuming we just want all, or need to filter deletedAt IS NULL
    }

    @Transactional
    public OperationStatusCode create(OperationStatusCodeCreateRequest request) {
        if (repository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
            throw new BusinessRuleException(ErrorCode.OPS_2005);
        }

        OperationStatusCode entity = new OperationStatusCode();
        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setHasParameter(request.isHasParameter());
        entity.setParameterUnit(request.getParameterUnit());
        entity.setColorHex(request.getColorHex());
        entity.setMappedStatus(request.getMappedStatus());
        entity.setSortOrder(request.getSortOrder());
        entity.setActive(request.isActive());

        return repository.save(entity);
    }

    @Transactional
    public OperationStatusCode update(Long id, OperationStatusCodeUpdateRequest request) {
        OperationStatusCode entity =
                repository.findById(id).orElseThrow(() -> new BusinessRuleException(ErrorCode.SYS_0008));

        // Check if code changes and already exists
        if (!entity.getCode().equals(request.getCode())
                && repository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
            throw new BusinessRuleException(ErrorCode.OPS_2005);
        }

        boolean mappedStatusChanged = (entity.getMappedStatus() != request.getMappedStatus())
                || (entity.getMappedStatus() != null
                        && !entity.getMappedStatus().equals(request.getMappedStatus()));
        boolean activeChanged = entity.isActive() != request.isActive();

        entity.setCode(request.getCode());
        entity.setName(request.getName());
        entity.setHasParameter(request.isHasParameter());
        entity.setParameterUnit(request.getParameterUnit());
        entity.setColorHex(request.getColorHex());
        entity.setMappedStatus(request.getMappedStatus());
        entity.setSortOrder(request.getSortOrder());
        entity.setActive(request.isActive());

        repository.save(entity);

        if (mappedStatusChanged || activeChanged) {
            statusRepository
                    .findConstructionIdsWithLatestCode(entity.getId())
                    .forEach(constructionStatusService::recomputeFor);
        }

        return entity;
    }

    @Transactional
    public void delete(Long id) {
        OperationStatusCode entity =
                repository.findById(id).orElseThrow(() -> new BusinessRuleException(ErrorCode.SYS_0008));

        if (statusRepository.existsByOperationCodeId(id)) {
            throw new BusinessRuleException(ErrorCode.OPS_2007);
        }

        entity.markDeleted(java.time.Instant.now());
        repository.save(entity);
    }
}
