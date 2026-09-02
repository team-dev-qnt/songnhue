package com.songnhue.hydro.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Bộ quy tắc nghi ngờ của <b>toàn hệ</b>, tra theo mã loại chỉ số — T32.1.
 *
 * <p>Đây là vế ĐỌC của khoá {@code hydro.quality.suspect-rule}, seed từ <b>13/08/2026</b> và tới
 * 02/09/2026 <b>chưa một dòng mã nào đọc</b> — luật 15 treo 20 ngày. Bộ đọc thật nằm ở
 * {@code HydroSettings.quyTacNghiNgo()}; lớp này chỉ giữ kết quả đã giải.
 *
 * <h2>⚠ Vì sao {@code deltaToiDaMoiGio} mặc định là KHÔNG KIỂM</h2>
 *
 * <p>Khoảng vật lý suy được từ phép đo: giá trị quan sát 01/09/2026 nằm trong 1,57–4,93 m, nên một
 * vỏ bọc rộng gấp nhiều lần khoảng ấy chỉ chạm tới khi cảm biến hỏng. <b>Tốc độ đổi thì không suy
 * được từ một lượt đo</b> — mở cống, xả lũ, bơm tiêu đều làm mực nước nhảy nhanh một cách hợp lệ, và
 * ta chưa có chuỗi thời gian nào để biết "nhanh bất thường" là bao nhiêu.
 *
 * <p>⇒ Seed <b>khoảng vật lý</b> (suy được), ⛔ <b>không seed</b> delta/giờ (chưa suy được). Đặt một
 * con số đoán vào đó là biến mọi lượt vận hành bình thường thành "dữ liệu nghi ngờ", và sau vài ngày
 * người trực sẽ thôi đọc nhãn ấy — lúc đó nhãn hỏng thật cũng không ai thấy. Ô nhập vẫn có trên màn
 * hình Cấu hình để bật khi đã có đủ chuỗi số đo, đúng quy tắc 12.
 *
 * @param theoLoaiChiSo khoá là {@code measurement_types.code}; loại chỉ số không có mục nào ⇒
 *     {@link QuyTacNghiNgo#KHONG_KIEM}
 */
public record BoQuyTacNghiNgo(Map<String, QuyTacNghiNgo> theoLoaiChiSo) {

    /**
     * Chưa cấu hình quy tắc nào.
     *
     * <p>⚠ ⛔ Đây <b>không</b> đồng nghĩa "mọi bản ghi hợp lệ" — nó nghĩa là <b>chưa ai kiểm</b>. Hai
     * điều ấy phân biệt được ở {@link #coKiemGiKhong()}, và màn hình "Dữ liệu nghi ngờ" phải nói ra
     * sự khác biệt: một bảng rỗng vì chưa cấu hình quy tắc mà hiện như "không có bản ghi nào đáng
     * ngờ" là đúng thứ quy tắc 16 cấm (<i>số 0 là một câu khẳng định</i>).
     */
    public static final BoQuyTacNghiNgo RONG = new BoQuyTacNghiNgo(Map.of());

    public BoQuyTacNghiNgo {
        Objects.requireNonNull(theoLoaiChiSo, "theoLoaiChiSo");
        theoLoaiChiSo = Map.copyOf(theoLoaiChiSo);
    }

    /** @return quy tắc của loại chỉ số, ⛔ không bao giờ {@code null} — chưa khai thì là KHONG_KIEM */
    public QuyTacNghiNgo cho(String maLoaiChiSo) {
        return theoLoaiChiSo.getOrDefault(maLoaiChiSo, QuyTacNghiNgo.KHONG_KIEM);
    }

    /** Có ít nhất một loại chỉ số thật sự được kiểm — xem {@link #RONG}. */
    public boolean coKiemGiKhong() {
        return theoLoaiChiSo.values().stream().anyMatch(QuyTacNghiNgo::coKiem);
    }
}
