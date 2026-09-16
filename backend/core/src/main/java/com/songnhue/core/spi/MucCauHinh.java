package com.songnhue.core.spi;

/**
 * Một dòng của màn hình "Tình trạng cấu hình" — T61.41.
 *
 * <p>⛔⛔ Record ⛔ có trường nào mang GIÁ TRỊ cấu hình. Màn hình trả lời "đã đặt chưa / ai đọc / đặt ở đâu / thiếu thì
 * hỏng gì" — ⛔ bao giờ trả lời "giá trị là gì". Một ô che {@code ****ab12} vẫn là rò rỉ: bốn ký tự cuối của một token
 * đủ để đối chiếu với bản sao lưu bị lộ.
 *
 * @param ma mã ổn định (VD {@code SMTP}) — giao diện dùng làm khoá dòng
 * @param nhom nhóm hiển thị
 * @param nguoiDoc tiến trình đọc cấu hình ấy (ứng dụng · nginx · Prometheus · Alertmanager …)
 * @param datO chỗ đặt: {@code .env} máy nào, biến nào — hoặc đường dẫn màn hình quản trị
 * @param ghiChu hậu quả khi thiếu, hoặc lệnh để tự kiểm với mục ngoài tầm nhìn
 */
public record MucCauHinh(
        String ma,
        String nhom,
        String ten,
        TrangThai trangThai,
        MucDo mucDo,
        String nguoiDoc,
        String datO,
        String ghiChu) {

    public enum TrangThai {
        /** Đã đặt và (khi đo được) đang chạy. */
        DAT,
        /** Chưa đặt. */
        THIEU,
        /** Có đặt mà sai: công tắc nới bảo mật đang bật, máy quét ⛔ trả lời, bản mã ⛔ giải được… */
        SAI,
        /**
         * Ứng dụng ⛔ nhìn thấy được — cấu hình của tiến trình khác trên máy khác. ⛔ Hiện xanh giả: ghi lệnh tự kiểm vào
         * {@code ghiChu}.
         */
        NGOAI_TAM_NHIN,
        /** Mục ⛔ áp dụng cho môi trường này (VD chuyển hướng thư ở production). */
        KHONG_AP_DUNG
    }

    /** Mức nghiêm trọng KHI trạng thái là {@code THIEU} hoặc {@code SAI}. */
    public enum MucDo {
        /** Chặn go-live / đang mất dữ liệu hoặc mở lỗ bảo mật — banner đỏ. */
        CHAN,
        /** Một chức năng ngừng hoặc chạy thiếu lớp bảo vệ — banner vàng. */
        CANH_BAO,
        THONG_TIN
    }

    public boolean canChuY() {
        return (trangThai == TrangThai.THIEU || trangThai == TrangThai.SAI) && mucDo != MucDo.THONG_TIN;
    }
}
