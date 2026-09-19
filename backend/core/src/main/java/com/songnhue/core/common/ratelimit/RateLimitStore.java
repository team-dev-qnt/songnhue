package com.songnhue.core.common.ratelimit;

import java.time.Duration;

/**
 * Bộ đếm tần suất — tách thành interface để đổi được cách lưu mà không sửa filter.
 *
 * <p>v1 dùng {@link CaffeineRateLimitStore} đếm trong bộ nhớ tiến trình: hệ chạy <b>1 node</b>
 * (architecture-review.md §6.2) và không có Redis, nên đếm in-process là đủ và rẻ.
 *
 * <p>⚠ <b>Lên từ 2 node trở đi bắt buộc đổi impl sang bảng DB</b>: mỗi node đếm riêng thì giới hạn
 * thực tế bị nhân lên theo số node — 30 lượt đăng nhập/15 phút trở thành 60 lượt với 2 node, tức
 * là chốt chặn dò mật khẩu yếu đi đúng một nửa. Đây là điều kiện đổi đã ghi ở §6.4, không phải việc
 * "tối ưu sau".
 */
public interface RateLimitStore {

    /**
     * Tăng bộ đếm của {@code key} và cho biết đã vượt hạn mức chưa.
     *
     * @param key khoá đã gồm cả loại bucket và định danh, VD {@code login:203.0.113.7}
     * @param limit số lượt tối đa trong một cửa sổ
     * @param window độ dài cửa sổ
     * @return kết quả kèm số lượt còn lại và thời điểm cửa sổ được đặt lại
     */
    Decision hit(String key, int limit, Duration window);

    /**
     * Trả lại ĐÚNG MỘT lượt đã tính cho {@code key} trong cửa sổ hiện tại. Bộ đếm ⛔ xuống dưới 0; khoá chưa có
     * cửa sổ, hoặc cửa sổ đã hết hạn, thì ⛔ làm gì.
     *
     * <p>T61.17 (WS-72): đăng nhập ĐÚNG mật khẩu trả lại lượt mà bộ lọc đã tính cho chính nó. Nhờ vậy xô
     * {@code LOGIN} theo IP chỉ còn giữ những lượt ⛔ đúng mật khẩu.
     *
     * <p>⛔⛔ Thay cho {@code reset(key)}. Phương thức ấy xoá SẠCH bộ đếm, có javadoc <i>"dùng khi đăng nhập
     * thành công"</i> mà có 0 nơi gọi. Nếu nối nó vào đúng chỗ ấy, ai có MỘT tài khoản thật chỉ cần đăng nhập
     * xen giữa các lượt đoán là xô về 0 và dò mật khẩu người khác ⛔ giới hạn. Trả lại một lượt thì ⛔ xoá được
     * lượt sai của ai.
     */
    void hoanLai(String key);

    /**
     * @param allowed cho đi tiếp hay chặn
     * @param remaining số lượt còn lại trong cửa sổ hiện tại
     * @param retryAfter còn bao lâu nữa mới được thử lại (chỉ có ý nghĩa khi bị chặn)
     */
    record Decision(boolean allowed, int remaining, Duration retryAfter) {}
}
