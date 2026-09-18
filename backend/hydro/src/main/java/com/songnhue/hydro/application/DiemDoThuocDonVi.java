package com.songnhue.hydro.application;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.songnhue.core.spi.OrgUnitUsagePort;
import com.songnhue.hydro.infra.StationRepository;

/**
 * Phần khai của {@code hydro} cho {@link OrgUnitUsagePort} — CN-04.1.
 *
 * <p>⚠ {@code stations.org_unit_id} là cột <b>cho phép NULL</b> — một điểm đo chưa gán đơn vị là
 * trạng thái hợp lệ ({@code ModuleBoundaryTest} có ba bài riêng chứng minh ngoại lệ ấy ⛔ không bị
 * lạm dụng). Nhưng một điểm đo trỏ vào đơn vị <b>đã giải thể</b> thì khác hẳn: nó ⛔ không rơi về
 * "chưa gán" mà rơi vào một đơn vị <b>⛔ không còn hiện trên màn hình nào</b>, nên ⛔ không ai gán
 * lại được cho nó.
 *
 * <p>⛔ Quan trọng hơn: quy tắc 18 nói nguồn thuỷ văn <b>⛔ không có API lịch sử</b>, nên mọi thứ
 * làm gãy đường ghi số đo đều là mất dữ liệu vĩnh viễn.
 */
@Component
public class DiemDoThuocDonVi implements OrgUnitUsagePort {

    private final StationRepository stations;

    public DiemDoThuocDonVi(StationRepository stations) {
        this.stations = stations;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> dangThuocDonVi(Long orgUnitId) {
        long so = stations.countByOrgUnitIdAndDeletedAtIsNull(orgUnitId);
        return so == 0 ? List.of() : List.of("%d điểm đo thuỷ văn".formatted(so));
    }
}
