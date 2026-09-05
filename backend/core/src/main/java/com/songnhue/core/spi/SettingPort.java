package com.songnhue.core.spi;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Tham số nghiệp vụ cấu hình được — quy tắc 12 của dự án.
 *
 * <p>⛔ <b>Giờ hành chính, hạn lưu trữ, chu kỳ polling, ngưỡng, giới hạn số lượng… không nằm trong
 * {@code application.yml} và không hard-code.</b> Chúng ở bảng {@code settings} và có màn hình sửa —
 * vì Công ty đổi tham số thì không được đòi deploy lại.
 *
 * <p>Đây cũng là cơ chế hấp thụ các mục nghiệp vụ còn mở: tham số chưa chốt đổ vào đây, câu trả lời
 * về thì đổi giá trị, <b>không cần migration</b>.
 *
 * <p>Mọi hàm đọc đều có {@code fallback} và không ném lỗi khi thiếu khoá: một tham số chưa seed
 * không được phép làm chết chức năng nghiệp vụ.
 */
public interface SettingPort {

    Optional<String> getString(String key);

    int getInt(String key, int fallback);

    boolean getBoolean(String key, boolean fallback);

    Duration getMinutes(String key, int fallbackMinutes);

    LocalTime getTime(String key, LocalTime fallback);
}
