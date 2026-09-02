package com.songnhue.operations.application;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.ConstructionLookupPort;
import com.songnhue.core.spi.ConstructionRef;
import com.songnhue.operations.domain.Construction;
import com.songnhue.operations.infra.ConstructionRepository;

/**
 * Cài đặt {@link ConstructionLookupPort} — T28.19.
 *
 * <p>Đặt ở {@code operations.application} theo đúng khuôn {@code content.application.PortalCache}:
 * hợp đồng ở {@code core.spi}, cài đặt ở <b>tầng application của module sở hữu dữ liệu</b>, Spring
 * nối hai đầu lúc dựng context. Nhờ vậy {@code hydro} gọi được mà ⛔ không cần một dòng phụ thuộc
 * Maven nào sang {@code operations} — và ranh giới ArchUnit vẫn sạch.
 *
 * <p>⚠ Bản đầu đặt ở {@code operations.spi} và {@code LayeringTest.transactionBoundaryBelongsToApplication}
 * làm CI đỏ ngay: <b>ranh giới giao dịch thuộc về tầng application</b>. Luật ấy đúng ở đây chứ không
 * chỉ đúng về hình thức — {@code spi} là nơi khai <i>hợp đồng</i>, và một hợp đồng mở giao dịch là
 * một hợp đồng đã quyết hộ người cài đặt.
 *
 * <h2>⚠ Hai phương thức đi HAI con đường khác nhau, có chủ ý</h2>
 *
 * <p>{@link #timTheoPublicId} đi qua {@link ConstructionService#get} nên hưởng trọn
 * {@code ScopeGuard} — ⛔ không tự viết lại điều kiện phạm vi ở đây, vì đó đúng là kiểu "người viết
 * phải nhớ" mà quy tắc 5 cấm, và {@code ScopeGuard} còn ghi một <b>sự kiện an ninh</b> mà một câu
 * {@code WHERE} chép tay ⛔ không ghi.
 *
 * <p>{@link #timTheoIds} đọc thẳng repository, ⛔ <b>không</b> lọc phạm vi — xem javadoc của cổng.
 */
@Component
public class ConstructionLookupAdapter implements ConstructionLookupPort {

    private final ConstructionService constructions;
    private final ConstructionRepository repository;

    public ConstructionLookupAdapter(ConstructionService constructions, ConstructionRepository repository) {
        this.constructions = constructions;
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConstructionRef> timTheoPublicId(UUID publicId) {
        if (publicId == null) {
            return Optional.empty();
        }
        // ⛔ Bắt ĐÚNG một loại. `PermissionDeniedException` (AUTH-3002) của ScopeGuard đi thẳng ra ngoài — "không tồn
        //    tại" và "tồn tại nhưng ngoài phạm vi của anh" là hai câu trả lời khác nhau, và gộp
        //    chúng lại đúng là cách một tín hiệu an ninh biến thành một dòng "không tìm thấy" trên
        //    biểu mẫu, kèm mất luôn sự kiện an ninh mà ScopeGuard vừa ghi.
        try {
            return Optional.of(chuyen(constructions.get(publicId)));
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, ConstructionRef> timTheoIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Construction> tim = repository.findAllById(ids);
        Map<Long, ConstructionRef> ket = new HashMap<>(tim.size());
        for (Construction c : tim) {
            // ⚠ `findAllById` KHÔNG biết tới xoá mềm — lọc ở đây, đúng như hợp đồng đã hứa. Thiếu
            //   dòng này thì một công trình đã thanh lý vẫn hiện tên trên màn hình liên kết, và
            //   người dùng tin rằng liên kết ấy còn sống.
            if (c.getDeletedAt() == null) {
                ket.put(c.getId(), chuyen(c));
            }
        }
        return ket;
    }

    private static ConstructionRef chuyen(Construction c) {
        return new ConstructionRef(
                c.getId(),
                c.getPublicId(),
                c.getCode(),
                c.getName(),
                c.getOrgUnitId(),
                c.getLifecycleState() == null ? null : c.getLifecycleState().name());
    }
}
