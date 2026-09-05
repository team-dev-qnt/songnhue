package com.songnhue.hydro.domain;

import java.util.Objects;

/**
 * Điểm đo mà một mã {@code F#####} trỏ tới — <b>bước ánh xạ của quy tắc parse 5</b>.
 *
 * <p>Bản đọc gọn của {@code stations}, chỉ mang đúng những gì lượt ingest cần quyết định. ⛔ Cố ý
 * <b>không</b> dùng entity {@link Station}: một lượt ingest chạm 28 mã và cần trả lời ba câu hỏi
 * (đây là trạm nào · trạm còn dùng không · trạm có khai loại chỉ số này không) — nạp 28 entity kèm
 * quan hệ {@code @ManyToMany} lười để hỏi ba câu ấy là một chuỗi N+1 chạy 720 lần mỗi ngày.
 *
 * <h2>⚠ {@link #daKhaiLoaiChiSo} là một CẢNH BÁO, ⛔ không phải một cái cổng</h2>
 *
 * <p>Bảng {@code station_measurement_types} nói <i>điểm đo này công bố những loại chỉ số nào</i> —
 * nó nuôi biểu mẫu, báo cáo và biểu tổng hợp. Nó ⛔ <b>không</b> là điều kiện để ghi một số đo:
 * nguồn vừa gửi một mực nước có thật của một trạm có thật, và bỏ nó đi vì bảng nối thiếu một dòng là
 * <b>mất dữ liệu vĩnh viễn</b> (quy tắc 18 — nguồn không có API lịch sử) để bảo vệ một bảng danh
 * mục mà con người sửa được trong mười giây.
 *
 * <p>⇒ Cờ này để lượt ingest <b>đếm và ghi WARN kèm mã điểm đo</b>, còn số đo vẫn xuống
 * {@code hydro_readings}. Người đọc log biết chính xác phải tích ô nào ở màn hình nào.
 *
 * @param stationId khoá của {@code hydro_readings.station_id}
 * @param code mã nội bộ ({@code DO-LMAC-TL}) — ⚠ dùng cho log và thông báo, vì {@code F01771} không
 *     nói được gì với người đọc lúc 2 giờ sáng
 * @param apiSourceId nguồn mà hồ sơ điểm đo khai là chủ của nó
 * @param active vế <b>con người</b> của trạng thái ({@code stations.active})
 * @param daKhaiLoaiChiSo điểm đo có dòng trong {@code station_measurement_types} cho loại chỉ số mà
 *     adapter đang giao không — xem khối cảnh báo ở trên
 */
public record DiemDoDich(Long stationId, String code, Long apiSourceId, boolean active, boolean daKhaiLoaiChiSo) {

    public DiemDoDich {
        Objects.requireNonNull(stationId, "stationId");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(apiSourceId, "apiSourceId");
    }
}
