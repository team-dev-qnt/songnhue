package com.songnhue.core.spi;

import java.util.UUID;

/**
 * Cổng giao tiếp với phân hệ Thuỷ văn (MOD-03).
 *
 * <p>SPI cố ý mỏng (coding-guide.md §1), chỉ khai đúng phương thức đang có người gọi.
 *
 * <p>Cài đặt: {@code com.songnhue.hydro.application.HydroAlertAdapter} (WS-33). ⛔ Chỉ được có
 * <b>một</b> bean cài đặt cổng này — {@code DummyHydroAlertService} của Phase 1 đã bị <b>xoá</b>,
 * ⛔ không bị {@code @Primary} đè.
 */
public interface HydroAlertPort {

    /**
     * Kiểm tra xem công trình có cảnh báo ngưỡng thuỷ văn nào <b>đang xảy ra và đã được xác nhận</b>
     * không.
     *
     * <p>⚠ "Đã xác nhận" là vế chịu lực: một điều kiện vừa vượt nhưng chưa giữ đủ
     * {@code delay_minutes} là một điều kiện <i>đang được theo dõi</i> — chưa ai nhận thông báo nào
     * về nó, và nó ⛔ không được lật trạng thái một công trình sang {@code CANH_BAO}.
     *
     * <p>⛔ Cài đặt ⛔ <b>không</b> lọc phạm vi đơn vị: đây là <b>mắt xích 3</b> của
     * {@code ConstructionStatusService.tinh()}, kết quả được ghi xuống cột
     * {@code constructions.operational_status}, và bốn mắt xích còn lại đều chạy câu native không
     * lọc. Trộn hai chiều lọc là để kết quả phụ thuộc <i>ai bấm F5 sau cùng</i> (luật 13, §10.35).
     *
     * @param constructionId ID công trình
     * @return true nếu có cảnh báo đang mở đã xác nhận
     */
    boolean hasActiveAlert(Long constructionId);

    /**
     * Một sự kiện cảnh báo có thật không — dùng để kiểm
     * {@code maintenance_logs.alert_event_public_id} trước khi ghi (T33.4).
     *
     * <p>⛔ Cột ấy cố ý là một {@code UUID} trần, ⛔ không phải khoá ngoại: {@code operations} và
     * {@code hydro} là hai module không thấy nhau, và một {@code REFERENCES} xuyên ranh giới là
     * ràng buộc mà không lượt tái tổ chức nào gỡ ra được (§10.4). Cái giá phải trả là tính toàn vẹn
     * phải do tầng dịch vụ giữ — tức đúng phương thức này.
     *
     * @return {@code false} khi UUID không trỏ tới sự kiện nào, kể cả khi nó đúng định dạng
     */
    boolean alertEventExists(UUID alertEventPublicId);
}
