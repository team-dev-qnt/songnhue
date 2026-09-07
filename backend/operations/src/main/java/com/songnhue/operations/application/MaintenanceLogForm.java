package com.songnhue.operations.application;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.songnhue.operations.domain.AcceptanceResult;
import com.songnhue.operations.domain.IncidentSeverity;
import com.songnhue.operations.domain.MaintenanceType;

/**
 * Dữ liệu nhập của một bản ghi sửa chữa / sự cố — CN-02.2.
 *
 * <h2>Ba thứ cố ý KHÔNG có ở đây</h2>
 *
 * <ul>
 *   <li><b>Mã bản ghi</b> — sinh tự động {@code BT-<năm>-xxxx} qua {@code code_sequences}. Khác hẳn
 *       mã công trình (nhập tay, có gợi ý): mã công trình đã in trên hồ sơ giấy của Công ty, còn mã
 *       bản ghi sửa chữa là thứ hệ thống này sinh ra lần đầu tiên.
 *   <li><b>Trạng thái xử lý</b> — chỉ đổi qua Workflow engine. Trạng thái <i>khởi tạo</i> thì có
 *       ({@link #initialState}), vì CN-02.2 cho phép hai đường vào đời và cả hai đều là sự thật về
 *       việc đã xảy ra, không phải một bước chuyển.
 *   <li><b>Đơn vị của bản ghi</b> — sao chép từ công trình (T18.2), không nhận từ client. Cho client
 *       chọn nghĩa là mở đường ghi dữ liệu vào phạm vi của đơn vị khác.
 * </ul>
 *
 * @param initialState {@code null} = trạng thái mặc định của quy trình ({@code MOI}). Giá trị khác
 *     phải là một đường vào đời hợp lệ và người gọi phải có quyền của nó — {@code WorkflowPort}
 *     kiểm, không phải service này.
 * @param performerOrgUnitPublicId đơn vị nội bộ thực hiện — <b>loại trừ</b> với
 *     {@link #performerName}; đúng một trong hai có giá trị (điểm nghiệp vụ 17)
 * @param cost đơn vị <b>VND</b>, {@code BigDecimal} (điểm nghiệp vụ 18 + quy tắc 2)
 * @param alertEventPublicId cảnh báo ngưỡng đã dẫn tới bản ghi này — Phase 2 điền, Phase 1 luôn rỗng
 */
public record MaintenanceLogForm(
        UUID constructionPublicId,
        MaintenanceType workType,
        IncidentSeverity severity,
        String initialState,
        LocalDate startedOn,
        LocalDate completedOn,
        String content,
        String itemOrEquipment,
        UUID performerOrgUnitPublicId,
        String performerName,
        BigDecimal cost,
        String fundingSource,
        AcceptanceResult acceptanceResult,
        String acceptanceNote,
        UUID assigneeUserPublicId,
        UUID alertEventPublicId) {}
