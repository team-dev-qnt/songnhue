package com.songnhue.hydro.application;

import java.time.Duration;

/**
 * Tham số nhịp <b>đã giải</b> của một nguồn — kết quả của {@code ApiSourceService.thamSoHieuLuc}.
 *
 * <h2>Vì sao trả cả giá trị lẫn cờ "đang dùng tham số chung"</h2>
 *
 * <p>{@code api_sources} có bốn cột nhịp nullable; {@code null} nghĩa là dùng tham số chung ở bảng
 * {@code settings}. Nếu màn hình chỉ hiện ô nhập (tức là hiện {@code null} thành ô trống) thì người
 * vận hành <b>không biết mình đang chịu con số nào</b> — họ thấy ô trống và kết luận "chưa cấu
 * hình", trong khi poller vẫn chạy theo cron chung.
 *
 * <p>{@code architecture-review.md} §10.29-a gọi tên đúng lỗi này: <i>canh giá trị ĐÃ GIẢI, đừng
 * canh giá trị MẶC ĐỊNH</i>. Bản ghi này là vế "đã giải", và cờ đi kèm cho biết giá trị đến từ đâu.
 *
 * @param cron biểu thức cron đang có hiệu lực
 * @param cronDungChung {@code true} nếu cron đến từ {@code settings}, không phải cột riêng của nguồn
 * @param khungNguon độ dài một khung cập nhật của nguồn — cơ sở của phép rate-limit
 * @param khungDungChung khung lấy từ {@code settings}
 * @param timeout thời gian chờ tối đa một lượt gọi
 * @param timeoutDungChung timeout lấy từ {@code settings}
 * @param soLanThuLai số lần thử lại khi gọi hỏng
 * @param thuLaiDungChung số lần thử lại lấy từ {@code settings}
 */
public record ThamSoNguon(
        String cron,
        boolean cronDungChung,
        Duration khungNguon,
        boolean khungDungChung,
        Duration timeout,
        boolean timeoutDungChung,
        int soLanThuLai,
        boolean thuLaiDungChung) {

    /** Nguồn này có ít nhất một tham số đặt riêng — để giao diện đánh dấu "đã tuỳ chỉnh". */
    public boolean coTuyChinh() {
        return !(cronDungChung && khungDungChung && timeoutDungChung && thuLaiDungChung);
    }
}
