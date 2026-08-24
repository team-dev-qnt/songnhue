package com.songnhue.operations.api;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;
import com.songnhue.operations.api.dto.OperationStatusBatchCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusResponse;
import com.songnhue.operations.application.ConstructionOperationStatusService;

import io.swagger.v3.oas.annotations.Operation;

/**
 * Tình hình vận hành cống — CN-02.11.
 *
 * <p>⚠ Đường dẫn là {@code /operation-statuses} (số nhiều), thống nhất với {@code /constructions} và
 * {@code /operation-status-codes}. Giao diện từng gọi {@code /operation-status/batch} (số ít) nên
 * <b>mọi lượt bấm Lưu đều nhận 404</b> — không lượt nào chạm tới tầng service, và không bài kiểm nào
 * thấy vì phía BE được kiểm bằng lời gọi service trực tiếp.
 */
@RestController
@RequestMapping("/api/v1/ops/operation-statuses")
public class ConstructionOperationStatusController {

    private final ConstructionOperationStatusService service;

    public ConstructionOperationStatusController(ConstructionOperationStatusService service) {
        this.service = service;
    }

    /**
     * Lịch sử tình hình vận hành của một công trình.
     *
     * <p>Endpoint này lấp một quyền chết: {@code ops:operation-status:view} đã được cấp cho 6 vai trò
     * từ WS-5 mà <b>không endpoint nào đòi nó</b> — dữ liệu chỉ có đường ghi vào, không có đường đọc
     * ra. Một quyền chưa ai đi qua thì chưa biết nó đúng hay sai (luật 7).
     */
    @GetMapping
    @Operation(summary = "Lịch sử tình hình vận hành của một công trình, mới nhất trước")
    @RequirePermission("ops:operation-status:view")
    public Page<OperationStatusResponse> lichSu(
            @RequestParam UUID constructionPublicId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        Pageable pageable =
                PageUtils.toPageable(page, size, sort, ConstructionOperationStatusService.allowedSortFields());
        return service.lichSu(constructionPublicId, pageable).map(OperationStatusResponse::from);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nhập nhanh tình hình vận hành nhiều công trình — cả lô hoặc không dòng nào")
    @RequirePermission("ops:operation-status:update")
    public void batchCreate(@Valid @RequestBody OperationStatusBatchCreateRequest request) {
        service.batchCreate(request);
    }
}
