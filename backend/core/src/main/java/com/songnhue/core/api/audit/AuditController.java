package com.songnhue.core.api.audit;

import java.util.List;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.songnhue.core.application.audit.AuditService;
import com.songnhue.core.common.security.RequirePermission;
import com.songnhue.core.common.util.PageUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Tra cứu nhật ký hoạt động và kiểm tra toàn vẹn — {@code /api/v1/audit-logs/**} (M5.8, CN-05.4).
 *
 * <p>Chỉ có đọc. Nhật ký append-only ở tầng DB, nên ở đây <b>không tồn tại</b> endpoint sửa hay xoá
 * — kể cả cho Super Admin. Đó không phải thiếu sót mà là toàn bộ giá trị của bảng này.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
@Tag(name = "05-adm · Nhật ký kiểm toán", description = "Tra cứu và kiểm tra chuỗi hash")
public class AuditController {

    /** Cột được phép sắp xếp — whitelist, chống chèn tên cột tuỳ ý qua tham số (§4.4). */
    private static final List<String> SORTABLE = List.of("seq", "occurredAt");

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "Tra cứu nhật ký theo khoảng thời gian và bộ lọc")
    @RequirePermission("adm:audit:view")
    public Page<AuditDtos.AuditLogView> search(
            @ParameterObject AuditDtos.SearchRequest filter,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {

        Pageable pageable = PageUtils.toPageable(page, size, sort, SORTABLE);
        return auditService
                .search(
                        filter.effectiveFrom(),
                        filter.effectiveTo(),
                        filter.module(),
                        filter.entityType(),
                        filter.entityId(),
                        filter.actorUserId(),
                        pageable)
                .map(AuditDtos.AuditLogView::of);
    }

    @PostMapping("/verify")
    @Operation(summary = "Kiểm tra tính toàn vẹn chuỗi hash — kết quả rỗng nghĩa là nguyên vẹn")
    @RequirePermission("adm:audit:verify")
    public AuditDtos.ChainVerificationView verify(
            @RequestParam(required = false) Long fromSeq, @RequestParam(required = false) Long toSeq) {
        return AuditDtos.ChainVerificationView.of(auditService.verifyChain(fromSeq, toSeq));
    }
}
