package com.songnhue.operations.api;

import java.util.List;
import java.util.UUID;

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
import com.songnhue.operations.application.ConstructionOperationStatusService;
import com.songnhue.operations.application.OperationStatusCodeService;

/**
 * Danh mục mã tình hình vận hành — CN-02.11.
 *
 * <p>⛔ Đường dẫn nhận {@code publicId} (UUID), không nhận khoá nội bộ. Bản trước dùng
 * {@code @PathVariable Long id} — controller duy nhất trong toàn bộ MOD-01/MOD-02 còn làm vậy.
 */
@RestController
@RequestMapping("/api/v1/ops/operation-status-codes")
public class OperationStatusCodeController {

    private final OperationStatusCodeService service;
    private final ConstructionOperationStatusService operationStatuses;

    public OperationStatusCodeController(
            OperationStatusCodeService service, ConstructionOperationStatusService operationStatuses) {
        this.service = service;
        this.operationStatuses = operationStatuses;
    }

    @GetMapping
    @RequirePermission("ops:operation-status-code:manage")
    public List<OperationStatusCodeResponse> findAll() {
        return service.findAll().stream().map(OperationStatusCodeResponse::from).toList();
    }

    /**
     * Mã còn dùng được — cho màn hình nhập tình hình vận hành.
     *
     * <p>Tách khỏi {@link #findAll} vì <b>quyền khác nhau</b>: người trực ban có
     * {@code ops:operation-status:view} để nhập liệu nhưng không có quyền quản trị danh mục. Không có
     * endpoint này thì màn hình nhập buộc phải gọi đường quản trị, và cách "chữa" duy nhất còn lại là
     * cấp quyền quản trị danh mục cho toàn bộ người trực — tức là nới quyền để giao diện chạy được.
     */
    @GetMapping("/active")
    @RequirePermission("ops:operation-status:view")
    public List<OperationStatusCodeResponse> findActive() {
        return operationStatuses.activeCodes().stream()
                .map(OperationStatusCodeResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("ops:operation-status-code:manage")
    public OperationStatusCodeResponse create(@Valid @RequestBody OperationStatusCodeCreateRequest request) {
        return OperationStatusCodeResponse.from(service.create(request));
    }

    @PutMapping("/{publicId}")
    @RequirePermission("ops:operation-status-code:manage")
    public OperationStatusCodeResponse update(
            @PathVariable UUID publicId, @Valid @RequestBody OperationStatusCodeUpdateRequest request) {
        return OperationStatusCodeResponse.from(service.update(publicId, request));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("ops:operation-status-code:manage")
    public void delete(@PathVariable UUID publicId) {
        service.delete(publicId);
    }
}
