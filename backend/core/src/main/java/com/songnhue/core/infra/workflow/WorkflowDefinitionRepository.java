package com.songnhue.core.infra.workflow;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.workflow.WorkflowDefinition;

@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinition, Long> {

    /**
     * Quy trình đang hiệu lực cho một loại entity.
     *
     * <p>Chỉ mục duy nhất {@code uq_workflow_definitions_entity_type WHERE active} bảo đảm mỗi loại
     * chỉ có một quy trình bật — hai quy trình cùng lúc thì bản ghi nào theo bản nào là không xác
     * định được.
     */
    Optional<WorkflowDefinition> findByEntityTypeAndActiveTrue(String entityType);

    Optional<WorkflowDefinition> findByCode(String code);
}
