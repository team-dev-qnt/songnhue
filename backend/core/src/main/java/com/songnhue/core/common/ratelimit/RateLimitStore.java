package com.songnhue.core.common.ratelimit;

import java.time.Duration;

/**
 * Bộ đếm tần suất — tách thành interface để đổi được cách lưu mà không sửa filter.
 *
 * <p>v1 dùng {@link CaffeineRateLimitStore} đếm trong bộ nhớ tiến trình: hệ chạy <b>1 node</b>
 * (architecture-review.md §6.2) và không có Redis, nên đếm in-process là đủ và rẻ.
 *
 * <p>⚠ <b>Lên từ 2 node trở đi bắt buộc đổi impl sang bảng DB</b>: mỗi node đếm riêng thì giới hạn
 * thực tế bị nhân lên theo số node — 5 lần đăng nhập sai/15 phút trở thành 10 lần với 2 node, tức
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

    /** Xoá bộ đếm — dùng khi đăng nhập thành công, để lần sai trước đó không tính vào lần sau. */
    void reset(String key);

    /**
     * @param allowed cho đi tiếp hay chặn
     * @param remaining số lượt còn lại trong cửa sổ hiện tại
     * @param retryAfter còn bao lâu nữa mới được thử lại (chỉ có ý nghĩa khi bị chặn)
     */
    record Decision(boolean allowed, int remaining, Duration retryAfter) {}
}
