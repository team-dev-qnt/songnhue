package com.songnhue.hydro.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * ⭐⭐ <b>Số khung 10' bị bỏ sót</b> của một (điểm đo × chỉ số × ngày) — phép đo <b>duy nhất</b> của
 * NFR-03, và là cột chịu lực của BC-13 (T34.3, T37.1).
 *
 * <h2>⛔ Quy tắc 16 ép ở HÀM DỰNG, ⛔ không ở lời dặn</h2>
 *
 * <p><i>"Số 0 là một câu khẳng định"</i>. Một ô ghi <b>0 khung bỏ sót</b> nói rằng poller chạy hoàn
 * hảo hôm ấy; một ô ghi <b>144 khung bỏ sót</b> nói rằng nó chết cả ngày. Cả hai đều <b>sai</b> khi
 * điểm đo hôm ấy chưa được theo dõi, hoặc đang tắt — và cái sai ấy đi thẳng vào con số nghiệm thu
 * NFR-03, tức là đi thẳng vào một cam kết với Công ty.
 *
 * <p>⇒ Hàm dựng ép đúng một bất biến: <b>hoặc</b> có đủ ba con số <b>hoặc</b> có lý do, ⛔ không bao
 * giờ cả hai và ⛔ không bao giờ không có gì. Nó ném thay vì sửa lặng lẽ — một ô rỗng không lý do
 * trông y hệt một ô đang chờ tải xong.
 *
 * <h2>⚠ "Khung mong đợi" chỉ đếm khung ĐÃ TRỌN VẸN</h2>
 *
 * <p>Nguồn trả rải rác trong cửa sổ {@code x1:30 → x8:30} của mỗi khung, nên khung <i>đang diễn
 * ra</i> chưa có dữ liệu là chuyện bình thường tuyệt đối. Đếm nó vào "mong đợi" làm hàng của hôm nay
 * <b>luôn</b> thiếu ít nhất một khung, và một báo cáo luôn báo động thì sau một tuần ⛔ không ai đọc
 * nó nữa. Nên mong đợi = số khung đã <b>kết thúc</b> trước thời điểm xem.
 *
 * @param soKhungMongDoi {@code null} ⇔ ô rỗng, và khi ấy {@link #lyDoTrong} bắt buộc có
 * @param lyDoTrong ⛔ ⛔ Bắt buộc khi ba con số rỗng. Người đọc phải phân biệt được <i>"chưa theo
 *     dõi"</i> với <i>"theo dõi mà không có dữ liệu"</i> — hai câu trả lời trái ngược nhau.
 */
public record DoDayDuKhung(Integer soKhungMongDoi, Integer soKhungBoSot, BigDecimal tyLeDayDu, String lyDoTrong) {

    public DoDayDuKhung {
        boolean coSo = soKhungMongDoi != null;
        if (coSo != (soKhungBoSot != null) || coSo != (tyLeDayDu != null)) {
            throw new IllegalArgumentException(
                    "Ba con số của độ đầy đủ phải cùng có hoặc cùng rỗng — nửa vời là một ô nói dối");
        }
        if (coSo == (lyDoTrong != null)) {
            throw new IllegalArgumentException(
                    coSo
                            ? "Ô có số liệu ⛔ không được kèm lý do rỗng — hai câu trái nhau trên cùng một ô"
                            : "Ô rỗng BẮT BUỘC có lý do (quy tắc 16) — rỗng không lý do trông y hệt đang tải");
        }
        if (coSo && soKhungBoSot < 0) {
            throw new IllegalArgumentException("Số khung bỏ sót ⛔ không thể âm");
        }
    }

    /** Ô rỗng có lý do — đường ra <b>duy nhất</b> khi chưa đo được. */
    public static DoDayDuKhung chuaDoDuoc(String lyDo) {
        return new DoDayDuKhung(null, null, null, lyDo);
    }

    /**
     * Tính độ đầy đủ cho một hàng BC-13.
     *
     * <p>Bốn nhánh, mỗi nhánh một câu trả lời <b>phân biệt được</b> (luật 9):
     *
     * <ol>
     *   <li>điểm đo đang tắt → rỗng, "Điểm đo đang tắt"
     *   <li>chưa từng có số đo nào → rỗng, "Chưa có số đo nào"
     *   <li>ngày nằm trước ngày có số đo đầu tiên → rỗng, "Trước ngày có số đo đầu tiên"
     *   <li>còn lại → ba con số
     * </ol>
     *
     * <p>⚠ Ngày <b>tương lai</b> rơi vào nhánh 4 với mong đợi bằng 0 ⇒ bỏ sót 0, tỷ lệ 100%. Đó là
     * câu trả lời đúng: chưa tới ngày thì chưa thiếu gì.
     *
     * @param khungMoiPhut kích thước khung của nguồn, tính bằng phút (mặc định 10)
     * @param bayGio thời điểm xem, <b>giờ VN</b> — cùng múi giờ với {@code hyd_ngay_vn} của CSDL
     */
    public static DoDayDuKhung tinh(ChatLuongNgayRow hang, int khungMoiPhut, ZonedDateTime bayGio) {
        if (khungMoiPhut <= 0) {
            throw new IllegalArgumentException("Kích thước khung phải dương: " + khungMoiPhut);
        }
        if (!hang.stationActive()) {
            return chuaDoDuoc("Điểm đo đang tắt — ⛔ không tính là bỏ sót");
        }
        if (hang.ngayDau() == null) {
            return chuaDoDuoc("Chưa có số đo nào cho cặp điểm đo — chỉ số này");
        }
        if (hang.ngay().isBefore(hang.ngayDau())) {
            return chuaDoDuoc("Trước ngày có số đo đầu tiên (" + hang.ngayDau() + ")");
        }

        int mongDoi = soKhungDaTronVen(hang.ngay(), khungMoiPhut, bayGio);
        int daNhan = hang.soDaNhan();

        // ⚠ Kẹp về 0: nguồn có thể trả một mốc lệch khung (đã thấy ở dữ liệu thật), và một ô
        //   "bỏ sót −3 khung" là một ô không ai đọc được. Kẹp ở đây, ⛔ không kẹp ở tầng hiển thị.
        int boSot = Math.max(0, mongDoi - daNhan);

        BigDecimal tyLe = mongDoi == 0
                ? BigDecimal.valueOf(100).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(Math.min(daNhan, mongDoi))
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(mongDoi), 1, RoundingMode.HALF_UP);

        return new DoDayDuKhung(mongDoi, boSot, tyLe, null);
    }

    /** Số khung đã <b>kết thúc</b> trong ngày ấy tính tới {@code bayGio}. */
    private static int soKhungDaTronVen(LocalDate ngay, int khungMoiPhut, ZonedDateTime bayGio) {
        ZoneId mui = bayGio.getZone();
        long phutTronNgay = Duration.ofDays(1).toMinutes();
        long phutDaQua = Duration.between(ngay.atStartOfDay(mui), bayGio).toMinutes();
        long dung = Math.max(0, Math.min(phutDaQua, phutTronNgay));
        return (int) (dung / khungMoiPhut);
    }
}
