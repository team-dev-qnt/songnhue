package com.songnhue.hydro.spi;

/**
 * Cổng đọc <b>tình trạng tín hiệu</b> của danh mục điểm đo — T35.6.
 *
 * <h2>⭐ Đây là lượt đi qua ranh giới {@code hydro} ĐẦU TIÊN của hệ</h2>
 *
 * <p>Cho tới 04/09/2026, {@code content} và {@code operations} import
 * {@code com.songnhue.hydro.*} đúng <b>0 lần</b>. Ranh giới {@code hydro} sạch không phải vì có một
 * cổng giữ nó, mà vì <b>chưa ai đọc dữ liệu thuỷ văn</b> — tức luật 7 ở dạng khó thấy nhất: luật
 * ArchUnit {@code CHI_IMPORT_SPI_CUA_MODULE_KHAC} chạy qua một <b>tập rỗng</b> và xanh trọn vẹn.
 * Cổng này là thứ đầu tiên làm tập ấy khác rỗng, nên nó cũng là thứ đầu tiên <i>chứng minh</i> luật
 * kia bắt được cái nó hứa sẽ bắt.
 *
 * <h2>⛔ Vì sao trả MỘT record thay vì ba phương thức đếm</h2>
 *
 * <p>Ba lượt đếm là ba ảnh chụp ở ba thời điểm. Ô KPI hiện "3 / 19" mà tử số và mẫu số đến từ hai
 * lượt quét khác nhau thì có lúc nó hiện "20 / 19" — và không ai dựng lại được tình huống ấy để
 * sửa. Cùng họ luật 13: một giá trị dẫn xuất trộn hai nguồn khác thời điểm thì kết quả phụ thuộc
 * <i>ai bấm F5 sau cùng</i>.
 *
 * <h2>⛔ Cổng này ⛔ KHÔNG lọc phạm vi đơn vị</h2>
 *
 * <p>Giống hệt lý do ở {@code core.spi.HydroAlertPort#hasActiveAlert}: <i>"còn bao nhiêu điểm đo
 * đang im lặng"</i> là một <b>sự thật về hệ thống</b>, không phụ thuộc ai đang nhìn. Ô KPI này nằm
 * cạnh {@code construction.without-location} vốn cũng không lọc — trộn hai chiều lọc trong cùng một
 * bảng KPI là để hai ô cạnh nhau kể hai câu chuyện khác nhau.
 */
public interface HydroLatestQueryPort {

    /**
     * Ảnh chụp tình trạng tín hiệu của toàn bộ danh mục điểm đo, đọc trong <b>một</b> lượt.
     *
     * @param dangDung số điểm đo {@code active = true} — mẫu số của ô KPI
     * @param matTinHieu đang dùng nhưng đã quá ngưỡng khung mà không có bản ghi mới
     * @param chuaCoDuLieu đang dùng và <b>chưa từng</b> có bản ghi nào — ⛔ khác hẳn
     *     {@code matTinHieu}: một điểm đo vừa seed chưa tới lượt polling đầu tiên ⛔ không phải một
     *     trạm hỏng, và gộp hai thứ này là biến ngày triển khai đầu tiên thành 19 cảnh báo giả
     */
    record TinhTrangTinHieu(long dangDung, long matTinHieu, long chuaCoDuLieu) {

        public TinhTrangTinHieu {
            // ⛔ Ép ở hàm dựng, không ở nơi gọi: một ô KPI hiện tử số lớn hơn mẫu số là thứ người
            //    đọc sẽ tin là lỗi hiển thị và bỏ qua, trong khi nó là lỗi dữ liệu.
            if (dangDung < 0 || matTinHieu < 0 || chuaCoDuLieu < 0) {
                throw new IllegalArgumentException("Số điểm đo không thể âm");
            }
            if (matTinHieu + chuaCoDuLieu > dangDung) {
                throw new IllegalArgumentException(
                        "Mất tín hiệu (%d) + chưa có dữ liệu (%d) không thể vượt số điểm đo đang dùng (%d)"
                                .formatted(matTinHieu, chuaCoDuLieu, dangDung));
            }
        }
    }

    /** @return ảnh chụp tại thời điểm gọi — ⛔ không đệm, ô KPI đọc lại mỗi lượt mở dashboard */
    TinhTrangTinHieu tinhTrangTinHieu();
}
