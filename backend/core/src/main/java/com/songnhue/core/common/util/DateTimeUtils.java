package com.songnhue.core.common.util;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Set;

/**
 * Xử lý thời gian — MỘT chỗ duy nhất cho quy tắc UTC ⇄ UTC+7.
 *
 * <p>Quy tắc 1 của dự án: <b>lưu {@code timestamptz} UTC, chỉ đổi sang UTC+7 ở tầng hiển thị.</b>
 * Mọi lỗi lệch 7 tiếng đều bắt nguồn từ việc ai đó tự đổi múi giờ ở giữa — nên chỗ đổi múi giờ chỉ
 * được nằm ở đây.
 *
 * <p>Cấm dùng {@code new Date()} và {@code LocalDateTime.now()} trong mã nghiệp vụ:
 * {@code LocalDateTime.now()} lấy múi giờ của máy chủ, chạy đúng trên máy dev ở Việt Nam nhưng sai
 * trên container chạy UTC. Dùng {@link #nowUtc()}.
 */
public final class DateTimeUtils {

    /** Múi giờ hiển thị của toàn hệ thống. */
    public static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Định dạng hiển thị chuẩn (conventions.md §3). */
    public static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private DateTimeUtils() {}

    /** Thời điểm hiện tại — luôn dùng hàm này thay cho {@code Instant.now()} rải rác. */
    public static Instant nowUtc() {
        return Instant.now();
    }

    /** Đổi mốc thời gian UTC sang giờ Việt Nam để hiển thị. */
    public static ZonedDateTime toVietnamTime(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE_VN);
    }

    /** Đổi giờ Việt Nam người dùng nhập vào thành mốc UTC để lưu. */
    public static Instant fromVietnamTime(LocalDateTime localDateTime) {
        return localDateTime == null ? null : localDateTime.atZone(ZONE_VN).toInstant();
    }

    /** Ngày theo lịch Việt Nam của một mốc UTC. Dùng khi gom nhóm báo cáo theo ngày. */
    public static LocalDate toVietnamDate(Instant instant) {
        return instant == null ? null : instant.atZone(ZONE_VN).toLocalDate();
    }

    public static String formatDateTime(Instant instant) {
        return instant == null ? "" : DISPLAY_DATE_TIME.format(toVietnamTime(instant));
    }

    public static String formatDate(Instant instant) {
        return instant == null ? "" : DISPLAY_DATE.format(toVietnamTime(instant));
    }

    /** Đầu ngày (00:00 giờ VN) của một ngày, quy về UTC — dùng cho truy vấn theo khoảng ngày. */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE_VN).toInstant();
    }

    /**
     * Đầu ngày HÔM SAU — dùng làm cận trên nửa mở {@code [từ, đến)}.
     *
     * <p>Cố ý không trả 23:59:59: cận trên đóng sẽ bỏ sót bản ghi trong khoảng 23:59:59,001 →
     * 23:59:59,999. Với dữ liệu thủy văn ghi theo phút thì mất bản ghi là mất vĩnh viễn.
     */
    public static Instant endOfDayExclusive(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE_VN).toInstant();
    }

    /**
     * Độ dài một ca làm việc, xử lý được ca đêm vắt qua nửa đêm.
     *
     * @param start giờ bắt đầu ca
     * @param end giờ kết thúc ca; nhỏ hơn hoặc bằng giờ bắt đầu nghĩa là sang ngày hôm sau
     */
    public static Duration shiftLength(java.time.LocalTime start, java.time.LocalTime end) {
        Duration length = Duration.between(start, end);
        return length.isNegative() || length.isZero() ? length.plusDays(1) : length;
    }

    /**
     * Đếm ngày làm việc trong khoảng (bao gồm cả hai đầu), trừ thứ Bảy, Chủ nhật và ngày lễ.
     *
     * <p>Dùng cho tính số ngày nghỉ phép (CN-04.9). Danh sách ngày lễ lấy từ bảng {@code holidays},
     * KHÔNG hard-code: lễ âm lịch đổi ngày mỗi năm.
     */
    public static long countWorkingDays(LocalDate from, LocalDate to, Collection<LocalDate> holidays) {
        if (from == null || to == null || to.isBefore(from)) {
            return 0;
        }
        Set<LocalDate> holidaySet = Set.copyOf(holidays);
        long count = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            if (isWorkingDay(day, holidaySet)) {
                count++;
            }
        }
        return count;
    }

    public static boolean isWorkingDay(LocalDate date, Set<LocalDate> holidays) {
        DayOfWeek dow = date.getDayOfWeek();
        boolean weekend = dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
        return !weekend && !holidays.contains(date);
    }

    /** Mốc UTC dùng cho seed/test cố định — tránh test phụ thuộc đồng hồ máy chạy. */
    public static Instant atUtc(int year, int month, int day, int hour, int minute) {
        return LocalDateTime.of(year, month, day, hour, minute).toInstant(ZoneOffset.UTC);
    }
}
