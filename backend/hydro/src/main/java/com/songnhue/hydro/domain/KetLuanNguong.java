package com.songnhue.hydro.domain;

/**
 * Kết luận của một lượt đánh giá ngưỡng — WS-33.
 *
 * <p>⭐ <b>Ba</b> kết cục, ⛔ không phải hai. Đây là điểm chịu lực của cả bộ đánh giá:
 *
 * <ul>
 *   <li>{@link #khongViPham()} — đã so, và số đo nằm trong ngưỡng;
 *   <li>{@link #viPham(String)} — đã so, và nó vượt;
 *   <li>{@link #khongKetLuanDuoc(String)} — <b>chưa so được</b>, vì thiếu mốc so sánh.
 * </ul>
 *
 * <p>⛔ Gộp kết cục thứ ba vào kết cục thứ nhất là <b>quy tắc 16 ở dạng nguy hiểm nhất</b>: cả hai
 * cho ra "không có cảnh báo nào", nên màn hình trông y hệt nhau và không ai đi tìm. Nhưng một cái
 * nghĩa là <i>trạm ổn</i>, còn cái kia nghĩa là <i>hệ thống đang không canh gì cả</i> — và luật
 * {@link AlertConditionType#RATE_OF_CHANGE} rơi vào nhánh ấy suốt mọi lượt đầu tiên sau khi khai
 * điểm đo, sau mỗi quãng API chết, và sau mỗi lượt xoá bản ghi nghi ngờ.
 *
 * <p>Vì vậy {@link #lyDoKhongKetLuan()} phải <b>ra tới giao diện</b> (T33.6), đứng cạnh nhãn
 * <i>"chưa cấu hình ngưỡng"</i> chứ không lẫn vào ô xanh.
 *
 * @param trangThai kết cục
 * @param moTa câu giải thích cho người trực — vì sao vượt, hoặc vì sao chưa so được. ⛔ Rỗng khi và
 *     chỉ khi {@code trangThai == KHONG_VI_PHAM}: không có gì để nói thì ⛔ đừng bịa một câu
 */
public record KetLuanNguong(TrangThai trangThai, String moTa) {

    public enum TrangThai {
        KHONG_VI_PHAM,
        VI_PHAM,
        KHONG_KET_LUAN_DUOC
    }

    public KetLuanNguong {
        if (trangThai == null) {
            throw new IllegalArgumentException("Trạng thái là bắt buộc");
        }
        boolean coMoTa = moTa != null && !moTa.isBlank();
        if ((trangThai == TrangThai.KHONG_VI_PHAM) == coMoTa) {
            throw new IllegalArgumentException(
                    "Mô tả phải đi cùng VI_PHAM/KHONG_KET_LUAN_DUOC và vắng ở KHONG_VI_PHAM — nhận: " + trangThai);
        }
    }

    public static KetLuanNguong khongViPham() {
        return new KetLuanNguong(TrangThai.KHONG_VI_PHAM, null);
    }

    public static KetLuanNguong viPham(String moTa) {
        return new KetLuanNguong(TrangThai.VI_PHAM, moTa);
    }

    public static KetLuanNguong khongKetLuanDuoc(String lyDo) {
        return new KetLuanNguong(TrangThai.KHONG_KET_LUAN_DUOC, lyDo);
    }

    public boolean dangViPham() {
        return trangThai == TrangThai.VI_PHAM;
    }

    /** @return lý do chưa so được, hoặc {@code null} nếu lượt đánh giá này đã kết luận được */
    public String lyDoKhongKetLuan() {
        return trangThai == TrangThai.KHONG_KET_LUAN_DUOC ? moTa : null;
    }
}
