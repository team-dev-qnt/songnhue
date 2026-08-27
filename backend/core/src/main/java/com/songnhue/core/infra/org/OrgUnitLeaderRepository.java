package com.songnhue.core.infra.org;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.songnhue.core.domain.org.OrgUnitLeader;

@Repository
public interface OrgUnitLeaderRepository extends JpaRepository<OrgUnitLeader, Long> {

    /** Tra cứu từ API luôn đi qua {@code public_id} — cấm lộ id chạy số (§4.2 chống IDOR). */
    Optional<OrgUnitLeader> findByPublicIdAndDeletedAtIsNull(UUID publicId);

    /**
     * Danh bạ của một đơn vị, theo thứ tự hiển thị.
     *
     * <p>Lọc {@code active} ngay ở đây chứ không ở nơi gọi: mọi chỗ đọc danh bạ đều muốn người
     * <i>đang</i> tại vị, và một nơi gọi quên lọc thì cổng công bố một người đã chuyển công tác —
     * loại sai không có thông báo nào (quy tắc 12: đặt bảo đảm ở chỗ dữ liệu đi qua).
     */
    List<OrgUnitLeader> findByOrgUnitIdAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long orgUnitId);

    /** Danh bạ của nhiều đơn vị trong một lượt — tránh N+1 khi dựng bảng Xí nghiệp trực thuộc. */
    List<OrgUnitLeader> findByOrgUnitIdInAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAscIdAsc(List<Long> orgUnitIds);

    /** Toàn bộ danh bạ của một đơn vị, kể cả dòng đã tắt — màn hình quản trị cần thấy đủ. */
    List<OrgUnitLeader> findByOrgUnitIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long orgUnitId);
}
