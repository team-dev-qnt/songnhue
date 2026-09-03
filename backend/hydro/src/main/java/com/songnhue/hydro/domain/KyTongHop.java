package com.songnhue.hydro.domain;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Một <b>kỳ tổng hợp</b> — bộ ba (điểm đo × loại chỉ số × ngày) mà bảng {@code hydro_agg_daily} tính
 * cho một lượt.
 *
 * <p>⚠⚠ {@link #ngay()} là <b>ngày giờ Việt Nam</b>, ⛔ không phải {@code measured_at::date}. Cắt
 * ngày theo UTC đẩy mọi số đo từ 00:00 tới 06:59 giờ VN — <b>42 trong 144 khung</b>, 29% — sang ngày
 * hôm trước, và triệu chứng thì gần như vô hình: báo cáo vẫn ra đủ hàng, vẫn có max/min hợp lý, chỉ
 * là <i>"mực nước cao nhất ngày 12"</i> thật ra xảy ra rạng sáng ngày 13. Phép đổi sống ở đúng một
 * cặp hàm CSDL ({@code hyd_ngay_vn} / {@code hyd_dau_ngay_vn}) và có khối tự kiểm chứng chạy ngay
 * lúc migrate.
 *
 * <p>⛔ Đừng thêm {@code quality} vào bộ ba này. Một lượt tính lại xử lý <b>trọn cả ba mức chất
 * lượng</b> của kỳ trong một giao dịch — tính riêng từng mức là mở đường cho trạng thái nửa vời, mà
 * một bảng tổng hợp nửa vời trông y hệt một bảng tổng hợp đúng.
 */
public record KyTongHop(long stationId, long measurementTypeId, LocalDate ngay) {

    public KyTongHop {
        if (stationId <= 0 || measurementTypeId <= 0) {
            throw new IllegalArgumentException("Kỳ tổng hợp phải có điểm đo và loại chỉ số");
        }
        Objects.requireNonNull(ngay, "ngay");
    }

    /** Dùng làm khoá gộp/log — ⛔ không phải khoá chống trùng của hàng đợi việc nền. */
    public String khoa() {
        return stationId + ":" + measurementTypeId + ":" + ngay;
    }
}
