package com.songnhue.hydro.domain;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một hàng thô của BC-13 — chất lượng dữ liệu của (điểm đo × loại chỉ số × ngày). T34.3.
 *
 * <p>⛔ Đây là <b>số liệu đọc lên</b>, chưa phải hàng báo cáo: phần "số khung bỏ sót" phụ thuộc kích
 * thước khung (tham số của nguồn) và thời điểm hiện tại, nên nó được tính ở {@link DoDayDuKhung} chứ
 * ⛔ không ở SQL. Tách như vậy để một thay đổi về nhịp lấy dữ liệu ⛔ không phải sửa truy vấn.
 *
 * @param ngayDau ngày có số đo <b>đầu tiên</b> của cặp này, hoặc {@code null} nếu chưa từng có bản
 *     ghi nào. ⭐ Ngày trước mốc ấy ⛔ <b>không</b> là "bỏ sót" — điểm đo khi ấy chưa được theo dõi,
 *     và đếm nó thành thiếu là bịa ra một sự cố.
 * @param tinhLuc lượt tính lại gần nhất của kỳ; {@code null} khi ngày ấy chưa có hàng tổng hợp nào
 */
public record ChatLuongNgayRow(
        LocalDate ngay,
        String stationCode,
        String stationName,
        boolean stationActive,
        String measurementTypeCode,
        String measurementTypeName,
        LocalDate ngayDau,
        int soHopLe,
        int soNghiNgo,
        int soDaXoa,
        Instant tinhLuc) {

    /** Tổng bản ghi đã nhận trong ngày — cũng chính là số khung đã có dữ liệu. */
    public int soDaNhan() {
        return soHopLe + soNghiNgo + soDaXoa;
    }
}
