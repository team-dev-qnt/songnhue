package com.songnhue.operations.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.operations.api.dto.OperationStatusCodeCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusCodeResponse;
import com.songnhue.operations.api.dto.OperationStatusCodeUpdateRequest;
import com.songnhue.operations.application.OperationStatusCodeService;

@RestController
@RequestMapping("/api/v1/ops/operation-status-codes")
public class OperationStatusCodeController {

    private final OperationStatusCodeService service;

    public OperationStatusCodeController(OperationStatusCodeService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission("ops:operation-status-code:manage")
    public List<OperationStatusCodeResponse> findAll() {
        return service.findAll().stream().map(OperationStatusCodeResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("ops:operation-status-code:manage")
    public OperationStatusCodeResponse create(@Valid @RequestBody OperationStatusCodeCreateRequest request) {
        return OperationStatusCodeResponse.from(service.create(request));
    }

    @PutMapping("/{id}")
    @RequirePermission("ops:operation-status-code:manage")
    public OperationStatusCodeResponse update(
            @PathVariable Long id, @Valid @RequestBody OperationStatusCodeUpdateRequest request) {
        return OperationStatusCodeResponse.from(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("ops:operation-status-code:manage")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
