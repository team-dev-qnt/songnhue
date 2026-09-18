package com.songnhue.operations.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.OrgUnitUsagePort;
import com.songnhue.operations.infra.ConstructionClusterRepository;
import com.songnhue.operations.infra.ConstructionRepository;
import com.songnhue.operations.infra.MaintenanceLogRepository;

/**
 * Phần khai của {@code operations} cho {@link OrgUnitUsagePort} — CN-04.1.
 *
 * <p><b>Công trình</b> là thứ thứ hai mà {@code function-spec.md:616} nêu đích danh. <b>Nhật ký
 * bảo trì</b> ⛔ không được nêu nhưng cùng vấn đề — {@code maintenance_logs.org_unit_id} là
 * {@code NOT NULL}, và một nhật ký trỏ vào đơn vị đã giải thể sẽ rơi khỏi mọi báo cáo lọc theo đơn
 * vị. ⚠ Quy tắc 18 nói <i>mất dữ liệu là vĩnh viễn</i>, và nhật ký bảo trì <b>chính là</b> chức
 * năng ghi nhận chính của MOD-02.
 *
 * <p>⛔ {@code construction_operation_status} ⛔ không kiểm riêng: mỗi bản ghi tình hình vận hành
 * <b>bắt buộc</b> thuộc một công trình, nên chặn được công trình là chặn được nó. Kiểm hai lần chỉ
 * làm câu lỗi dài ra mà ⛔ không chặn thêm trạng thái nào.
 */
@Component
public class CongTrinhThuocDonVi implements OrgUnitUsagePort {

    private final ConstructionRepository constructions;
    private final MaintenanceLogRepository maintenanceLogs;
    private final ConstructionClusterRepository clusters;

    public CongTrinhThuocDonVi(
            ConstructionRepository constructions,
            MaintenanceLogRepository maintenanceLogs,
            ConstructionClusterRepository clusters) {
        this.constructions = constructions;
        this.maintenanceLogs = maintenanceLogs;
        this.clusters = clusters;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangThuocDonVi(Long orgUnitId) {
        List<String> ly = new ArrayList<>(3);
        long soCongTrinh = constructions.countByOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        if (soCongTrinh > 0) {
            ly.add("%d công trình".formatted(soCongTrinh));
        }
        long soNhatKy = maintenanceLogs.countByOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        if (soNhatKy > 0) {
            ly.add("%d nhật ký bảo trì / khắc phục sự cố".formatted(soNhatKy));
        }
        // ⚠ Cụm công trình hôm nay có **0 hàng** (ba endpoint ghi cụm có 0 nơi gọi — T42.23), nên
        //   vế này chạy trên tập rỗng. Nó vẫn ở đây vì `SoDonViThamChieuTest` đo phạm vi trên
        //   **lược đồ**, ⛔ không trên dữ liệu: cột khoá ngoại có thật thì phải có câu trả lời thật.
        long soCum = clusters.countByOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        if (soCum > 0) {
            ly.add("%d cụm công trình".formatted(soCum));
        }
        return ly;
    }
}
