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
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.application.ConstructionClusterService;
import com.songnhue.operations.domain.ConstructionCluster;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Danh mục cụm công trình — {@code /api/v1/ops/construction-clusters/**} (T17.11, G15).
 *
 * <h2>Vì sao dùng lại quyền {@code ops:construction:*} thay vì thêm quyền mới</h2>
 *
 * Ma trận phân quyền ở {@code function-spec.md} §6 đã được Công ty duyệt và có 334 dòng đang được
 * {@code RbacMatrixTest} đối chiếu trên CSDL thật. Cụm chỉ là <b>cách nhóm</b> danh mục công trình,
 * không phải một đối tượng nghiệp vụ riêng — ai quản lý danh mục công trình thì quản lý luôn cách
 * nhóm nó. Thêm một quyền không có trong ma trận đã duyệt sẽ tạo ra một ô mà không vai trò nào được
 * gán, tức là một chức năng không ai dùng được.
 */
@RestController
@RequestMapping("/api/v1/ops/construction-clusters")
@Tag(name = "02-ops · Cụm công trình", description = "Danh mục cụm — chỉ để nhóm hiển thị và lọc")
public class ConstructionClusterController {

    private final ConstructionClusterService clusters;

    public ConstructionClusterController(ConstructionClusterService clusters) {
        this.clusters = clusters;
    }

    @GetMapping
    @Operation(summary = "Danh sách cụm công trình")
    @RequirePermission("ops:construction:view")
    public List<ConstructionDtos.ClusterView> list() {
        return clusters.list().stream().map(this::toView).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Thêm cụm công trình")
    @RequirePermission("ops:construction:create")
    public ConstructionDtos.ClusterView create(@Valid @RequestBody ConstructionDtos.ClusterRequest request) {
        return toView(clusters.create(
                request.code(),
                request.name(),
                request.orgUnitId(),
                request.description(),
                request.sortOrder() == null ? 0 : request.sortOrder()));
    }

    @PutMapping("/{publicId}")
    @Operation(summary = "Sửa cụm công trình")
    @RequirePermission("ops:construction:update")
    public ConstructionDtos.ClusterView update(
            @PathVariable UUID publicId, @Valid @RequestBody ConstructionDtos.ClusterRequest request) {
        return toView(clusters.update(
                publicId,
                request.code(),
                request.name(),
                request.orgUnitId(),
                request.description(),
                request.sortOrder() == null ? 0 : request.sortOrder()));
    }

    @DeleteMapping("/{publicId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Xoá cụm — chặn khi còn công trình bên trong (OPS-2012)")
    @RequirePermission("ops:construction:delete")
    public void delete(@PathVariable UUID publicId) {
        clusters.delete(publicId);
    }

    private ConstructionDtos.ClusterView toView(ConstructionCluster cum) {
        OrgUnitRef donVi = clusters.orgUnitOf(cum);
        return new ConstructionDtos.ClusterView(
                cum.getPublicId(),
                cum.getCode(),
                cum.getName(),
                donVi == null ? null : donVi.publicId(),
                donVi == null ? null : donVi.name(),
                cum.getDescription(),
                cum.getSortOrder(),
                cum.isActive());
    }
}
