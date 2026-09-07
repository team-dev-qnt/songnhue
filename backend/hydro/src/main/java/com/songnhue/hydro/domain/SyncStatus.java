package com.songnhue.hydro.domain;

/**
 * Kết cục một lượt polling — nuôi màn hình <i>Nhật ký đồng bộ</i> (M3.16).
 *
 * <p>⭐ Bốn giá trị tồn tại để trả lời đúng một câu hỏi vận hành: <b>lượt vừa rồi không ghi được
 * dòng nào — vì nguồn hỏng, vì đã đủ dữ liệu, hay vì mới ghi được một phần?</b> Ba câu trả lời ấy
 * cần ba cách xử lý ngược nhau, nên chúng phải là ba trạng thái khác nhau chứ không phải một dòng
 * log chung mà người đọc tự suy.
 *
 * <p>⚠ Đặc biệt {@link #SKIPPED_UP_TO_DATE}: nó là kết cục <b>bình thường và mong muốn</b> của phần
 * lớn các lượt chạy — poll 2 phút/lần trên một nguồn cập nhật 10 phút/lần thì 4/5 lượt không có gì
 * mới. Trộn nó vào {@link #FAILED} là dạy người vận hành bỏ qua màu đỏ.
 */
public enum SyncStatus {

    /** Gọi được, parse được, mọi bản ghi hợp lệ đã xử lý xong. */
    SUCCESS,

    /**
     * Gọi được nhưng số bản ghi hợp lệ <b>dưới 50%</b> số điểm đo đang hoạt động (quy tắc 9 của
     * CN-03.2, WS-31/T31.7).
     *
     * <p>Không phải lỗi của ta và cũng không phải chuyện bình thường — thường là nguồn đang đẩy dở
     * dữ liệu của khung. Có cảnh báo, nhưng không đánh dấu từng trạm là mất tín hiệu.
     */
    PARTIAL,

    /** Không lấy được dữ liệu. ⛔ Bắt buộc kèm {@link SyncFailureKind} — CSDL ép bằng CHECK. */
    FAILED,

    /**
     * ⭐ <b>Cố ý không gọi</b>: toàn bộ điểm đo đang hoạt động đã có bản ghi thuộc khung hiện tại.
     *
     * <p>⛔ Điều kiện dừng là <b>đủ TOÀN BỘ trạm</b>, không phải "đã có bản ghi đầu tiên" — nguồn
     * trả rải rác trong cửa sổ {@code x1:30 → x8:30}, nên dừng sớm là mất trạm lên muộn (quy tắc 17).
     */
    SKIPPED_UP_TO_DATE
}
