package com.songnhue.core.application.org;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.application.settings.SettingKeys;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.exception.ConflictException;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.common.tree.MaterializedPath;
import com.songnhue.core.common.tree.TreeBuilder;
import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitType;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;
import com.songnhue.core.spi.OrgUnitPort;
import com.songnhue.core.spi.OrgUnitRef;

/**
 * Cây tổ chức — pattern P2 (implement.md §2), một bảng dùng chung cho Xí nghiệp và phòng ban
 * (quy tắc 7 của dự án).
 *
 * <p><b>Vì sao lớp này quan trọng hơn vẻ ngoài của nó.</b> {@code org_units.path} là thứ mà bộ lọc
 * phạm vi tầng 3 dựa vào ({@code ScopedEntity}). Một path sai không làm hỏng màn hình sơ đồ tổ chức
 * — nó làm sai <b>quyền xem dữ liệu của toàn bộ nhánh bên dưới</b>, mà triệu chứng duy nhất là ai đó
 * xem được dữ liệu của Xí nghiệp khác. Vì vậy mọi thao tác đổi cấu trúc cây đều nằm trong một
 * transaction và đều tính lại path bằng {@link MaterializedPath}, không có đường nào sửa path tay.
 */
@Service
public class OrgUnitService implements OrgUnitPort {

    private static final Logger log = LoggerFactory.getLogger(OrgUnitService.class);

    /** Path tạm dùng đúng một nhịp giữa hai lần ghi trong {@link #create} — xem chú thích ở đó. */
    private static final String PLACEHOLDER_PATH = "/0/";

    private final OrgUnitRepository repository;
    private final UserRepository userRepository;
    private final SettingService settings;

    public OrgUnitService(OrgUnitRepository repository, UserRepository userRepository, SettingService settings) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.settings = settings;
    }

    // ---- Đọc ------------------------------------------------------------------

    /** Toàn bộ cây dạng lồng nhau, đã sắp theo {@code sort_order} trong từng cấp. */
    @Transactional(readOnly = true)
    public List<OrgUnitNode> tree() {
        return toTree(repository.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc());
    }

    /** Cây con tính từ một đơn vị — dùng cho người chỉ được xem đơn vị mình và cấp dưới. */
    @Transactional(readOnly = true)
    public List<OrgUnitNode> subtree(UUID publicId) {
        return toTree(repository.findSubtree(require(publicId).getPath()));
    }

    // ---- Hợp đồng cho module nghiệp vụ (core.spi) -------------------------------
    //
    // Chỉ đọc, và chỉ hai phương thức: module nghiệp vụ gán đơn vị phụ trách cho bản ghi của mình
    // thì cần tra, không cần sửa cây. `findRefById` có vì bản ghi nghiệp vụ lưu `org_unit_id` chứ
    // không lưu UUID — bộ lọc phạm vi ở tầng 3 làm việc trên khoá số.

    @Override
    @Transactional(readOnly = true)
    public Optional<OrgUnitRef> findRef(UUID publicId) {
        return repository.findByPublicIdAndDeletedAtIsNull(publicId).map(OrgUnitService::toRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrgUnitRef> findRefById(Long id) {
        return repository
                .findById(id)
                .filter(unit -> unit.getDeletedAt() == null)
                .map(OrgUnitService::toRef);
    }

    /**
     * Tra đơn vị theo <b>mã</b> — dành cho đường nhập dữ liệu hàng loạt (T17.9).
     *
     * <p>Tệp Excel do Công ty gửi ghi mã đơn vị ("XN1", "XNTL-HD"), không ghi UUID. Bắt người nhập
     * dịch sang UUID trước khi nhập là đòi họ làm việc mà hệ thống làm được — và mỗi lần dịch tay là
     * một cơ hội gán nhầm hồ sơ sang Xí nghiệp khác, tức là gán nhầm cả phạm vi xem dữ liệu.
     */
    @Override
    @Transactional(readOnly = true)
    public Optional<OrgUnitRef> findRefByCode(String code) {
        return code == null
                ? Optional.empty()
                : repository.findByCodeAndDeletedAtIsNull(code.trim()).map(OrgUnitService::toRef);
    }

    private static OrgUnitRef toRef(OrgUnit unit) {
        return new OrgUnitRef(
                unit.getId(),
                unit.getPublicId(),
                unit.getCode(),
                unit.getName(),
                unit.getShortName(),
                unit.getUnitType().name(),
                unit.getPath(),
                unit.getDepth());
    }

    @Transactional(readOnly = true)
    public OrgUnit get(UUID publicId) {
        return require(publicId);
    }

    /** Danh sách phẳng — cho ô chọn đơn vị, đã đủ path để FE tự thụt lề. */
    @Transactional(readOnly = true)
    public List<OrgUnit> listAll() {
        return repository.findAllByDeletedAtIsNullOrderByPathAscSortOrderAsc();
    }

    // ---- Ghi ------------------------------------------------------------------

    /**
     * Thêm đơn vị mới.
     *
     * <p>Path chỉ tính được <b>sau khi</b> có id (path chứa chính id của mình), nên phải lưu hai
     * bước trong cùng transaction: {@code save} lấy id → {@code placeAt} → flush. Đây là cái giá của
     * materialized path, đổi lại truy vấn cây con chỉ là một {@code LIKE} chạy trên chỉ mục.
     */
    @Transactional
    public OrgUnit create(String code, String name, OrgUnitType type, UUID parentPublicId) {
        if (repository.existsByCodeAndDeletedAtIsNull(code)) {
            throw new ConflictException(ErrorCode.ADM_2002);
        }

        OrgUnit parent = parentPublicId == null ? null : require(parentPublicId);
        if (parent == null
                && repository.findFirstByParentIdIsNullAndDeletedAtIsNull().isPresent()) {
            // Cây tổ chức có đúng một gốc. Hai gốc thì path của nhánh thứ hai không bắt đầu bằng
            // '/1/', và người ở gốc thứ nhất lặng lẽ không thấy dữ liệu của nhánh kia.
            throw new BusinessRuleException(ErrorCode.ADM_2003);
        }
        if (parent != null) {
            checkDepthLimit(parent.getDepth() + 1);
        }

        OrgUnit unit = new OrgUnit(code, name, type);
        // `path` và `depth` là NOT NULL, mà path thật lại chứa chính id — thứ chỉ có sau khi ghi.
        // Nên phải ghi bằng một path tạm rồi sửa ngay trong cùng transaction. Không ai quan sát được
        // giá trị tạm này: nó bị ghi đè trước khi transaction commit, và hỏng giữa chừng thì rollback
        // xoá luôn cả dòng.
        unit.placeAt(PLACEHOLDER_PATH);
        OrgUnit saved = repository.saveAndFlush(unit);

        saved.placeAt(
                parent == null
                        ? MaterializedPath.rootPath(saved.getId())
                        : MaterializedPath.childPath(parent.getPath(), saved.getId()));
        return repository.saveAndFlush(saved);
    }

    @Transactional
    public OrgUnit update(UUID publicId, String name, String shortName, OrgUnitType type) {
        OrgUnit unit = require(publicId);
        unit.setName(name);
        unit.setShortName(shortName);
        unit.setUnitType(type);
        return repository.save(unit);
    }

    /**
     * Chuyển một đơn vị (kèm toàn bộ cấp dưới) sang đơn vị cha khác.
     *
     * <p>Ba thứ phải chặn trước khi động vào dữ liệu:
     *
     * <ol>
     *   <li><b>Vòng</b> — chuyển vào chính cây con của mình. Nhánh bị cắt rời: không còn truy vấn
     *       theo path nào tìm thấy nó, nhưng các dòng vẫn nằm nguyên trong bảng, nên không có lỗi
     *       nào báo ra. Đây là ca hỏng nặng nhất của thao tác này.
     *   <li><b>Chuyển nút gốc</b> — cây mất gốc.
     *   <li><b>Vượt số cấp tối đa</b> — tính theo nhánh sâu nhất của cây con đang chuyển, không phải
     *       theo mỗi nút được chuyển.
     * </ol>
     */
    @Transactional
    public OrgUnit move(UUID publicId, UUID newParentPublicId) {
        OrgUnit unit = require(publicId);
        OrgUnit newParent = require(newParentPublicId);

        if (unit.isRoot()) {
            throw new BusinessRuleException(ErrorCode.ADM_2003);
        }
        if (MaterializedPath.wouldCreateCycle(unit.getPath(), newParent.getPath())) {
            throw new BusinessRuleException(ErrorCode.ADM_2003);
        }

        String oldPrefix = unit.getPath();
        String newPrefix = MaterializedPath.childPath(newParent.getPath(), unit.getId());
        if (oldPrefix.equals(newPrefix)) {
            return unit;
        }

        int deepestBelow = repository.findSubtree(oldPrefix).stream()
                .mapToInt(OrgUnit::getDepth)
                .max()
                .orElse(unit.getDepth());
        int shift = MaterializedPath.depthOf(newPrefix) - unit.getDepth();
        checkDepthLimit(deepestBelow + shift);

        int moved = repository.reparentSubtree(oldPrefix, newPrefix);
        log.info("Chuyển đơn vị {} từ {} sang {} — {} bản ghi đổi path", unit.getCode(), oldPrefix, newPrefix, moved);

        // reparentSubtree là câu lệnh gốc + clearAutomatically → entity trong bộ nhớ đã cũ, phải đọc lại
        return require(publicId);
    }

    /** Đổi thứ tự hiển thị giữa các đơn vị cùng cấp. Không đụng tới path. */
    @Transactional
    public void reorder(List<UUID> orderedPublicIds) {
        int order = 0;
        for (UUID publicId : orderedPublicIds) {
            OrgUnit unit = require(publicId);
            unit.setSortOrder(order++);
            repository.save(unit);
        }
    }

    /**
     * Xoá mềm.
     *
     * <p>Từ chối khi còn đơn vị cấp dưới hoặc còn người dùng trực thuộc — xoá cha mà con còn sống
     * thì các bản ghi con vẫn giữ path cũ chứa id đã chết, cây trở nên không dựng lại được. Bắt xoá
     * từ dưới lên là cách duy nhất giữ cây luôn nhất quán.
     */
    @Transactional
    public void delete(UUID publicId) {
        OrgUnit unit = require(publicId);
        if (unit.isRoot()) {
            throw new BusinessRuleException(ErrorCode.ADM_2003);
        }
        if (repository.existsByParentIdAndDeletedAtIsNull(unit.getId())
                || userRepository.existsByOrgUnitIdAndDeletedAtIsNull(unit.getId())) {
            throw new ConflictException(ErrorCode.ADM_2004);
        }
        unit.markDeleted(Instant.now());
        repository.save(unit);
    }

    // ---- Nội bộ ---------------------------------------------------------------

    private OrgUnit require(UUID publicId) {
        return repository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private void checkDepthLimit(int depth) {
        int max = settings.getInt(SettingKeys.ORG_TREE_MAX_DEPTH, SettingKeys.DEFAULT_ORG_TREE_MAX_DEPTH);
        if (depth >= max) {
            throw new BusinessRuleException(ErrorCode.ADM_2005);
        }
    }

    private static List<OrgUnitNode> toTree(List<OrgUnit> rows) {
        return TreeBuilder.build(
                rows,
                OrgUnit::getId,
                OrgUnit::getParentId,
                Comparator.comparing(OrgUnit::getSortOrder).thenComparing(OrgUnit::getName),
                OrgUnitNode::of);
    }
}
