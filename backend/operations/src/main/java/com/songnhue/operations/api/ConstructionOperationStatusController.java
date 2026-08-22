package com.songnhue.operations.api;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.operations.api.dto.OperationStatusBatchCreateRequest;
import com.songnhue.operations.application.ConstructionOperationStatusService;

@RestController
@RequestMapping("/api/v1/ops/operation-statuses")
public class ConstructionOperationStatusController {

    private final ConstructionOperationStatusService service;

    public ConstructionOperationStatusController(ConstructionOperationStatusService service) {
        this.service = service;
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("ops:operation-status:update")
    public void batchCreate(@Valid @RequestBody OperationStatusBatchCreateRequest request) {
        service.batchCreate(request);
    }
}
