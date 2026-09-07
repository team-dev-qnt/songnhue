package com.songnhue.core.infra.workflow;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.workflow.WorkflowTransition;

@Repository
public interface WorkflowTransitionRepository extends JpaRepository<WorkflowTransition, Long> {

    Optional<WorkflowTransition> findByDefinitionIdAndFromStateAndAction(
            Long definitionId, String fromState, String action);

    /** Các bước đi được từ một trạng thái — nguồn dựng nút trên giao diện. */
    List<WorkflowTransition> findByDefinitionIdAndFromStateOrderBySortOrderAsc(Long definitionId, String fromState);
}
