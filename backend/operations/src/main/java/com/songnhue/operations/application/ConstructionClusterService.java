package com.songnhue.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.exception.ValidationException;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;
import com.songnhue.operations.domain.ConstructionCluster;
import com.songnhue.operations.infra.ConstructionClusterRepository;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Danh mục cụm công trình — T17.11 (G15, điểm nghiệp vụ 12).
 *
 * <p>⛔ Cụm <b>chỉ để nhóm hiển thị và lọc</b>. Không lớp nào ở đây tra cứu quyền theo cụm, và không
 * được thêm về sau: phạm vi dữ liệu đi bằng {@code org_unit_id} của từng công trình. Hai nguồn phạm
 * vi thì sớm muộn sẽ lệch, và bên lỏng hơn sẽ thắng mà không ai biết.
 */
@Service
public class ConstructionClusterService {

    private static final Logger log = LoggerFactory.getLogger(ConstructionClusterService.class);

    private final ConstructionClusterRepository clusters;
    private final ConstructionRepository constructions;
    private final OrgUnitPort orgUnits;

    public ConstructionClusterService(
            ConstructionClusterRepository clusters, ConstructionRepository constructions, OrgUnitPort orgUnits) {
        this.clusters = clusters;
        this.constructions = constructions;
        this.orgUnits = orgUnits;
    }

    @Transactional(readOnly = true)
    public List<ConstructionCluster> list() {
        return clusters.findByDeletedAtIsNullOrderBySortOrderAscNameAsc();
    }

    @Transactional
    public ConstructionCluster create(String code, String name, UUID orgUnitPublicId, String description, int order) {
        String ma = chuanHoaMa(code);
        if (clusters.existsByCodeAndDeletedAtIsNull(ma)) {
            throw new ConflictException(ErrorCode.OPS_2014, ma);
        }
        ConstructionCluster cum = new ConstructionCluster(
                ma, chuanHoaTen(name), donVi(orgUnitPublicId).id());
        cum.setDescription(description);
        cum.setSortOrder(order);
        log.info("Thêm cụm công trình {}", ma);
        return clusters.save(cum);
    }

    @Transactional
    public ConstructionCluster update(
            UUID publicId, String code, String name, UUID orgUnitPublicId, String description, int order) {
        ConstructionCluster cum = tim(publicId);
        String ma = chuanHoaMa(code);
        if (clusters.existsByCodeAndDeletedAtIsNullAndIdNot(ma, cum.getId())) {
            throw new ConflictException(ErrorCode.OPS_2014, ma);
        }
        cum.setCode(ma);
        cum.setName(chuanHoaTen(name));
        cum.setOrgUnitId(donVi(orgUnitPublicId).id());
        cum.setDescription(description);
        cum.setSortOrder(order);
        return clusters.save(cum);
    }

    /**
     * Xoá mềm một cụm.
     *
     * <p>Chặn khi còn công trình bên trong ({@code OPS-2012}) thay vì tự gỡ liên kết: gỡ tự động thì
     * một cú bấm nhầm làm hàng chục hồ sơ mất cách nhóm, và không có gì để hoàn tác vì bản thân việc
     * gỡ không để lại nơi nào ghi "trước đó thuộc cụm nào".
     */
    @Transactional
    public void delete(UUID publicId) {
        ConstructionCluster cum = tim(publicId);
        long dangDung = constructions.countByClusterIdAndDeletedAtIsNull(cum.getId());
        if (dangDung > 0) {
            throw new ConflictException(ErrorCode.OPS_2012, dangDung);
        }
        cum.markDeleted(Instant.now());
        clusters.save(cum);
        log.info("Xoá cụm công trình {}", cum.getCode());
    }

    /** Đơn vị quản lý của một cụm — để giao diện hiện tên thay vì khoá số. */
    @Transactional(readOnly = true)
    public OrgUnitRef orgUnitOf(ConstructionCluster cum) {
        return orgUnits.findRefById(cum.getOrgUnitId()).orElse(null);
    }

    private ConstructionCluster tim(UUID publicId) {
        return clusters.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private OrgUnitRef donVi(UUID publicId) {
        if (publicId == null) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return orgUnits.findRef(publicId).orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static String chuanHoaMa(String ma) {
        if (ma == null || ma.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ma.trim().toUpperCase(Locale.ROOT);
    }

    private static String chuanHoaTen(String ten) {
        if (ten == null || ten.isBlank()) {
            throw new ValidationException(ErrorCode.SYS_0003);
        }
        return ten.trim();
    }
}
