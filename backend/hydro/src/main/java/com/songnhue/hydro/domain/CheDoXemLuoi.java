package com.songnhue.hydro.domain;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Chế độ xem của thanh mốc thời gian — spec-description.md §6.1.1.
 *
 * <h2>⛔ Chỉ có HAI giá trị, và đó là một quyết định chứ ⛔ không phải một thiếu sót</h2>
 *
 * <p>Spec §6.1.1 liệt <b>bảy</b> chế độ: phút · giờ · 7h · 19h · 7/19h · 1/7/13/19h · 1/3/5…/23h ·
 * ngày. Năm chế độ còn lại đều là <b>nhiều ngày</b> ({@code 1 cột/ngày}, {@code 2 cột/ngày}…), và
 * số liệu nhiều ngày ⛔ không đọc {@code hydro_readings} — nó đọc {@code hydro_agg_daily}
 * (quy tắc 8). Đó là một câu truy vấn khác, một trần chi phí khác, và một hạng mục khác (WS-44).
 *
 * <p>⛔ Vì sao ⛔ không khai sẵn cả bảy rồi ném ở năm giá trị chưa nối: một enum có nhánh
 * "chưa dựng" là một enum mà tầng trên phải nhớ đừng gọi — và người sau đọc danh sách bảy giá trị
 * sẽ tin cả bảy đều chạy. Thà thiếu một giá trị còn hơn có một giá trị nói dối (quy tắc 15).
 */
public enum CheDoXemLuoi {

    /**
     * Mốc <b>10 phút</b> — chế độ mặc định của §6.1.1.
     *
     * <p>⚠ Một ngày có <b>144 mốc</b>, và spec ghi thẳng <i>"⛔ không được đổ hết 144 cột ra
     * bảng"</i>. Số cột do tầng gọi truyền vào và bị chặn hai đầu.
     */
    PHUT(Duration.ofMinutes(10)),

    /** Giá trị tại <b>đầu mỗi giờ</b> — 24 cột cho một ngày. */
    GIO(Duration.ofHours(1));

    private final Duration buoc;

    CheDoXemLuoi(Duration buoc) {
        this.buoc = buoc;
    }

    public Duration buoc() {
        return buoc;
    }

    /**
     * Dựng danh sách mốc, <b>mới nhất trước</b> — đúng thứ tự cột của §6.1.2
     * (<i>"cột thời gian xếp mới nhất bên trái"</i>).
     *
     * <p>⛔⛔ Đây là chỗ chữa khuyết tật <b>T43.13</b>. Trước đây trục thời gian được dựng
     * <b>từ chính mảng điểm trả về</b>, nên một mốc mất dữ liệu ⛔ không tạo ra cột nào — nó bị
     * <b>NUỐT</b>, và hai mốc cách nhau một giờ trông như hai mốc liền kề. Lưới phải dựng
     * <b>trước</b> và <b>độc lập</b> với dữ liệu; ô nào ⛔ không có số thì để trống, và chính chỗ
     * trống ấy là thứ làm biểu đồ ngắt đường thay vì nối liền qua khoảng mất tín hiệu (§7.1).
     *
     * @param den mốc cuối (thường là bây giờ); được <b>cắt xuống</b> bội của bước
     * @param soCot số cột muốn lấy; bị kẹp vào {@code [1, 288]} — 288 = 48 giờ mốc 10 phút, đủ rộng
     *     cho mọi cách dùng của §6.1.1 mà vẫn chặn một lượt gọi hỏi cả tháng
     */
    public List<Instant> dungLuoi(Instant den, int soCot) {
        int n = Math.max(1, Math.min(soCot, 288));
        Instant chot = catXuong(den);
        List<Instant> moc = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            moc.add(chot.minus(buoc.multipliedBy(i)));
        }
        return List.copyOf(moc);
    }

    /**
     * Cắt một mốc bất kỳ xuống bội gần nhất của bước — {@code 10:57} → {@code 10:50} với
     * {@link #PHUT}.
     *
     * <p>⚠ Cắt bằng số học trên epoch-second chứ ⛔ không bằng {@code truncatedTo}: bước 10 phút
     * ⛔ không phải một {@link ChronoUnit} nên {@code truncatedTo} ⛔ không nhận nó, và bước một giờ
     * thì nhận — dùng hai đường khác nhau cho hai giá trị của cùng một enum là chỗ hai chế độ lệch
     * nhau nửa mốc mà ⛔ không ai nhìn ra.
     */
    public Instant catXuong(Instant moc) {
        long buocGiay = buoc.getSeconds();
        long giay = moc.getEpochSecond();
        return Instant.ofEpochSecond(Math.floorDiv(giay, buocGiay) * buocGiay);
    }
}
