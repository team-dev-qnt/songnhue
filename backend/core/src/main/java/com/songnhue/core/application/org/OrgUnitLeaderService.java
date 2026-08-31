package com.songnhue.core.application.org;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.ResourceNotFoundException;
import com.songnhue.core.domain.org.OrgUnit;
import com.songnhue.core.domain.org.OrgUnitLeader;
import com.songnhue.core.infra.org.OrgUnitLeaderRepository;
import com.songnhue.core.infra.org.OrgUnitRepository;
import com.songnhue.core.spi.PortalCachePort;

/**
 * Quản trị danh bạ lãnh đạo của một đơn vị — đường <b>GHI</b> cho {@code org_unit_leaders}.
 *
 * <h2>⚠⚠ Vì sao lớp này tồn tại: một bảng chỉ có đường đọc</h2>
 *
 * Bảng {@code org_unit_leaders} dựng ngày 27/08/2026 ({@code V202608271034}) kèm repository và
 * {@link PublicOrgDirectoryService} đọc nó ra cổng công khai. Đo lại ngày 28/8: <b>không có
 * controller nào, không có màn hình quản trị nào</b> ghi vào bảng ấy.
 *
 * <p>Hệ quả cụ thể: trang {@code /gioi-thieu/lanh-dao} (CR-25) và cột "Giám đốc XN" của bảng 6 cột
 * (CR-26) đọc một bảng mà <b>không ai có cách nào điền</b>. Cả hai trang đã được dựng, đã có bài
 * kiểm, đã lên staging, và sẽ rỗng vĩnh viễn. Đúng quy tắc 15 ở chiều ghi — cùng hình dạng với
 * {@code categories.visible} (T24.25) và {@code OrgUnit.address/phone/email}, và đây là vụ thứ ba
 * trong cùng một đợt: <i>việc làm xong nửa đường trông y hệt việc làm xong</i> (luật 19).
 *
 * <h2>Vì sao dùng lại quyền {@code adm:org-unit:manage}, không thêm quyền mới</h2>
 *
 * Danh bạ lãnh đạo là một phần hồ sơ của đơn vị, không phải một miền nghiệp vụ riêng: ai sửa được
 * cơ cấu tổ chức thì sửa được danh bạ của nó, và không có vai trò nào cần vế này mà không cần vế
 * kia. Thêm một quyền thứ 89 chỉ để phân biệt hai thao tác luôn đi cùng nhau là làm bảng phân
 * quyền dài thêm mà không thêm khả năng diễn đạt nào.
 */
@Service
@Transactional(readOnly = true)
public class OrgUnitLeaderService {

    private final OrgUnitLeaderRepository repository;
    private final OrgUnitRepository orgUnits;

    private final PortalCachePort portalCache;

    public OrgUnitLeaderService(
            OrgUnitLeaderRepository repository, OrgUnitRepository orgUnits, PortalCachePort portalCache) {
        this.portalCache = portalCache;
        this.repository = repository;
        this.orgUnits = orgUnits;
    }

    /** Toàn bộ danh bạ của một đơn vị, <b>kể cả dòng đã tắt</b> — màn hình quản trị phải thấy đủ. */
    public List<OrgUnitLeader> danhSach(UUID orgUnitPublicId) {
        return repository.findByOrgUnitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(
                donVi(orgUnitPublicId).getId());
    }

    @Transactional
    public OrgUnitLeader them(
            UUID orgUnitPublicId, String fullName, String title, String phone, String email, Integer sortOrder) {
        OrgUnitLeader dong = new OrgUnitLeader();
        dong.setOrgUnitId(donVi(orgUnitPublicId).getId());
        ganTruong(dong, fullName, title, phone, email, sortOrder);
        OrgUnitLeader ketQua = repository.save(dong);
        portalCache.orgUnitsChanged();
        return ketQua;
    }

    @Transactional
    public OrgUnitLeader sua(
            UUID publicId, String fullName, String title, String phone, String email, Integer sortOrder) {
        OrgUnitLeader dong = require(publicId);
        ganTruong(dong, fullName, title, phone, email, sortOrder);
        OrgUnitLeader ketQua = repository.save(dong);
        portalCache.orgUnitsChanged();
        return ketQua;
    }

    /**
     * Bật / tắt một dòng.
     *
     * <p>Tắt ≠ xoá: người chuyển công tác thì dòng phải rời khỏi cổng ngay, nhưng còn nguyên trong
     * bảng để đối chiếu lịch sử và để bật lại nếu tắt nhầm. Cổng chỉ đọc dòng {@code active}
     * (bộ lọc nằm ở repository — quy tắc 12).
     */
    @Transactional
    public OrgUnitLeader doiTrangThai(UUID publicId, boolean active) {
        OrgUnitLeader dong = require(publicId);
        dong.setActive(active);
        OrgUnitLeader ketQua = repository.save(dong);
        portalCache.orgUnitsChanged();
        return ketQua;
    }

    @Transactional
    public void xoa(UUID publicId) {
        OrgUnitLeader dong = require(publicId);
        dong.markDeleted(Instant.now());
        repository.save(dong);
        portalCache.orgUnitsChanged();
    }

    // ---- Nội bộ --------------------------------------------------------------

    private void ganTruong(
            OrgUnitLeader dong, String fullName, String title, String phone, String email, Integer sortOrder) {
        dong.setFullName(fullName.trim());
        dong.setTitle(title.trim());
        // ⛔ Rỗng phải thành `null`, không giữ chuỗi rỗng: cổng dựng ô điện thoại khi giá trị "có",
        //    nên `""` sẽ cho ra một ô có mặt mà không nội dung — luật 16 đòi phân biệt được
        //    "chưa công bố số" với "đã công bố". Biểu mẫu AntD gửi ô trống lên dưới dạng `""`.
        dong.setPhone(rongThanhNull(phone));
        dong.setEmail(rongThanhNull(email));
        dong.setSortOrder(sortOrder == null ? 0 : sortOrder);
    }

    private static String rongThanhNull(String giaTri) {
        return giaTri == null || giaTri.isBlank() ? null : giaTri.trim();
    }

    private OrgUnit donVi(UUID publicId) {
        return orgUnits.findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }

    private OrgUnitLeader require(UUID publicId) {
        return repository
                .findByPublicIdAndDeletedAtIsNull(publicId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SYS_0004));
    }
}
