package com.songnhue.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.spi.PortalCachePort;
import com.songnhue.operations.api.dto.OperationStatusCodeCreateRequest;
import com.songnhue.operations.api.dto.OperationStatusCodeFields;
import com.songnhue.operations.api.dto.OperationStatusCodeUpdateRequest;
import com.songnhue.operations.domain.OperationStatusCode;
import com.songnhue.operations.infra.ConstructionOperationStatusRepository;
import com.songnhue.operations.infra.OperationStatusCodeRepository;

/**
 * Danh mục mã tình hình vận hành — CN-02.11, quy tắc 16 của dự án.
 *
 * <p>Danh mục do <b>Công ty vận hành</b>: thêm một mã mới không được đòi một lượt deploy. Vì vậy nó
 * là bảng có CRUD chứ không phải enum trong mã nguồn.
 *
 * <h2>Đổi ánh xạ mã là đổi trạng thái của những công trình đang mang mã đó</h2>
 *
 * <p>{@code mapped_status} là mắt xích 4 của chuỗi suy ra trạng thái. Sửa nó mà không tính lại thì
 * cột {@code operational_status} giữ giá trị suy ra từ luật <i>cũ</i> cho tới lần ghi nhận kế tiếp —
 * có thể là nhiều ngày. Nên {@link #update} gọi lại {@code recomputeFor} cho đúng nhúm công trình
 * đang lấy mã này làm bản ghi mới nhất.
 *
 * <h2>⭐ Ba cột của danh mục này được cổng công khai công bố NGUYÊN VĂN — V1</h2>
 *
 * <p>{@code PublicOperationStatusService.OperationStatusRow} mang thẳng {@code name},
 * {@code colorHex} và {@code parameterUnit} của mã ra cổng, cố ý — <i>"trả mã màu ra để cổng khỏi
 * giữ một bảng ánh xạ thứ hai"</i>. Cái giá của quyết định đó là: <b>sửa danh mục cũng là sửa nội
 * dung cổng</b>, và cho tới 02/09/2026 lớp này ⛔ chưa từng gọi {@link PortalCachePort} — đổi tên
 * hay màu một mã thì cổng hiện giá trị cũ tới hết chu kỳ ISR 5 phút. Đúng triệu chứng §10.62 mà
 * T27.7 đã đi trả nợ ở <i>đường ghi trạng thái</i>, tái phát ở <b>đường ghi danh mục</b> (luật 27).
 *
 * <h2>⛔ Vì sao chỉ {@link #update} gọi, còn {@link #create} và {@link #delete} thì không</h2>
 *
 * <p>Không phải vì tiết kiệm, mà vì hai đường kia <b>về nguyên tắc không đổi được nội dung cổng</b>,
 * và ghi lý do ra đây để người sau khỏi "sửa" cho đều tay:
 *
 * <ul>
 *   <li>{@code create} — cổng chỉ công bố mã <i>đang được một công trình mang</i>; một mã vừa sinh ra
 *       chưa có bản ghi nào tham chiếu, nên không ô nào trên cổng đổi. ⛔ Không có endpoint công khai
 *       nào trả về danh mục mã (đã kiểm: {@code PublicConstructionController} không đụng tới).
 *   <li>{@code delete} — {@code OPS-2007} chặn xoá đúng khi mã <i>đang</i> được dùng, nên mã xoá được
 *       là mã không ai mang. Cùng lập luận.
 * </ul>
 *
 * <p>⚠ Bất biến ngầm ở đây: <b>"cổng chỉ thấy mã đang được dùng"</b>. Ngày nào cổng công bố cả danh
 * mục (một chú giải màu, một bộ lọc), hai nhánh trên hết đúng và phải gọi theo.
 */
@Service
@Transactional(readOnly = true)
public class OperationStatusCodeService {

    private final OperationStatusCodeRepository repository;
    private final ConstructionOperationStatusRepository statusRepository;
    private final ConstructionStatusService constructionStatusService;
    private final PortalCachePort portalCache;

    public OperationStatusCodeService(
            OperationStatusCodeRepository repository,
            ConstructionOperationStatusRepository statusRepository,
            ConstructionStatusService constructionStatusService,
            PortalCachePort portalCache) {
        this.repository = repository;
        this.statusRepository = statusRepository;
        this.constructionStatusService = constructionStatusService;
        this.portalCache = portalCache;
    }

    /**
     * Danh sách cho màn hình quản trị danh mục.
     *
     * <p>⚠ Bản trước gọi {@code repository.findAll()} kèm chú thích *"Assuming we just want all, or
     * need to filter deletedAt IS NULL"* — và câu đó trả về cả mã đã xoá mềm. Người quản trị xoá một
     * mã, danh sách vẫn hiện nó, xoá lần nữa thì {@code OPS-2007} chặn vì bản ghi cũ vẫn tham chiếu.
     */
    public List<OperationStatusCode> findAll() {
        return repository.findByDeletedAtIsNullOrderBySortOrderAscCodeAsc();
    }

    @Transactional
    public OperationStatusCode create(OperationStatusCodeCreateRequest request) {
        if (repository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
            throw new BusinessRuleException(ErrorCode.OPS_2005);
        }

        OperationStatusCode entity = new OperationStatusCode();
        apDung(entity, request.getCode(), request);
        return repository.save(entity);
    }

    @Transactional
    public OperationStatusCode update(UUID publicId, OperationStatusCodeUpdateRequest request) {
        OperationStatusCode entity = trongDanhMuc(publicId);

        // Đổi mã sang một mã đã có người khác dùng → chặn. Bỏ qua chính nó, nếu không nó tự trùng.
        if (!entity.getCode().equals(request.getCode())
                && repository.existsByCodeAndDeletedAtIsNull(request.getCode())) {
            throw new BusinessRuleException(ErrorCode.OPS_2005);
        }

        // So sánh TRƯỚC khi ghi đè — đọc sau khi set thì hai vế luôn bằng nhau và nhánh tính lại
        // trạng thái không bao giờ chạy.
        boolean anhXaDoi = entity.getMappedStatus() != request.getMappedStatus();
        boolean anAnDoi = entity.isActive() != request.isActive();

        apDung(entity, request.getCode(), request);
        repository.save(entity);

        if (anhXaDoi || anAnDoi) {
            statusRepository
                    .findConstructionIdsWithLatestCode(entity.getId())
                    .forEach(constructionStatusService::recomputeFor);
        }

        // ⚠ VÔ ĐIỀU KIỆN, ⛔ không gói trong `anhXaDoi || anAnDoi`: hai cờ ấy chỉ hỏi *"trạng thái
        //   dẫn xuất có phải tính lại không"*. Ba cột cổng công bố nguyên văn — tên, màu, đơn vị
        //   tham số — đổi được mà cả hai cờ vẫn `false`, và đó chính là lượt sửa THƯỜNG GẶP NHẤT
        //   (gõ lại một cái tên cho gọn). Gói vào là vá đúng một nửa số lượt sửa — xem javadoc lớp.
        portalCache.constructionsChanged();

        return entity;
    }

    @Transactional
    public void delete(UUID publicId) {
        OperationStatusCode entity = trongDanhMuc(publicId);

        if (statusRepository.existsByOperationCodeId(entity.getId())) {
            throw new BusinessRuleException(ErrorCode.OPS_2007);
        }

        entity.markDeleted(Instant.now());
        repository.save(entity);
    }

    /**
     * Tra một mã theo định danh công khai.
     *
     * <p>Không bọc {@code ScopeGuard}: {@link OperationStatusCode} là danh mục dùng chung toàn Công
     * ty, không có phạm vi đơn vị để mà vi phạm. Cái phải giữ ở đây là {@code deleted_at IS NULL} —
     * thiếu nó thì một mã đã xoá vẫn sửa lại được và sống dậy trong danh sách.
     */
    private OperationStatusCode trongDanhMuc(UUID publicId) {
        return repository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private static void apDung(OperationStatusCode entity, String code, OperationStatusCodeFields fields) {
        entity.setCode(code);
        entity.setName(fields.getName());
        entity.setHasParameter(fields.isHasParameter());
        entity.setParameterUnit(fields.getParameterUnit());
        entity.setColorHex(fields.getColorHex());
        entity.setMappedStatus(fields.getMappedStatus());
        entity.setSortOrder(fields.getSortOrder());
        entity.setActive(fields.isActive());
    }
}
