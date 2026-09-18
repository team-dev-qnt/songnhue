package com.songnhue.app.architecture.fixture;

import java.time.Clock;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.TimeZone;

/**
 * Mã <b>cố ý đọc đồng hồ theo múi giờ máy chủ</b> — nguyên liệu cho bài tự-kiểm-chứng của
 * {@code NoAmbientClock} ({@code CodingRuleTest.boCanhDongHoBatDuMoiDuongDocMuiGioMayChu}).
 *
 * <h2>⛔⛔ Vì sao tệp này tồn tại — T51.11</h2>
 *
 * <p>{@code NoAmbientClock} chạy trên {@code ProductionClasses.ALL}. Hôm nay <b>⛔ không lớp sản
 * xuất nào</b> vi phạm nó, nên nó xanh — và cái xanh ấy ⛔ không phân biệt được <i>"⛔ không ai vi
 * phạm"</i> với <i>"bộ canh ⛔ không nhìn thấy kiểu vi phạm ấy"</i>. Đo 10/09/2026: nó đúng là
 * <b>⛔ không</b> nhìn thấy 5 kiểu, trong đó {@code YearMonth.now()} là lời gọi tự nhiên nhất để
 * tính kỳ phép năm của CN-04.9 — việc sắp làm. Luật 7 ở dạng đắt nhất.
 *
 * <p>⇒ Mỗi phương thức dưới đây là <b>một đường</b> đọc múi giờ máy chủ, và bài tự-kiểm đòi bộ canh
 * gọi tên <b>từng cái một</b>. Thêm một kiểu vào {@code AMBIENT_NOW_OWNERS} mà quên fixture thì bài
 * kiểm ⛔ không chứng minh gì thêm; thêm fixture mà quên bộ canh thì bài kiểm ĐỎ.
 *
 * <p>⚠ Các lớp ở đây nằm trong {@code src/test} và bị {@code ImportOption.DoNotIncludeTests} loại
 * khỏi mọi luật thật, nên chúng ⛔ không làm đỏ luật chính. Bài tự-kiểm nhập gói này <b>riêng</b>
 * bằng {@code ClassFileImporter} — cùng cách {@link ViolatingFixtures} đang dùng.
 *
 * <p>⭐ {@link DungChuan} là <b>vế phân biệt</b>, và thiếu nó thì bài tự-kiểm ⛔ không nói được cách
 * cài đặt nào là đúng: một bộ canh chặn <i>mọi</i> lời gọi tới {@code java.time} cũng sẽ xanh trên
 * bảy khẳng định "phải bắt được", trong khi nó cấm cả {@code Instant.now()} — đúng thứ câu
 * {@code because} của luật đang khuyên dùng (luật 9).
 */
public final class DongHoFixtures {

    private DongHoFixtures() {}

    /**
     * ⭐ <b>MỎ NEO</b> — kiểu mà <i>mọi</i> bản của bộ canh đều bắt được, kể cả bản trước T51.11.
     *
     * <p>Thiếu nó thì lượt kiểm chứng ngược (thu bộ canh về 4 kiểu cũ) làm fixture sinh ra
     * <b>0 vi phạm</b>, và bài đỏ ở vế <i>chống tập rỗng</i> thay vì ở vế {@code YearMonth} — đúng
     * hình dạng §11.19: bài đỏ, nhưng câu chẩn đoán trỏ nhầm chỗ, và người đọc nó sẽ đi tìm một gói
     * fixture đổi tên thay vì một bộ canh bị thu hẹp.
     */
    public static LocalDate homNay() {
        return LocalDate.now();
    }

    /** ⛔ Lời gọi của CN-04.9 — kỳ phép năm. Bản canh trước T51.11 ⛔ không thấy dòng này. */
    public static YearMonth kyPhepHienTai() {
        return YearMonth.now();
    }

    /** ⛔ Ngày–tháng ⛔ không năm: dùng cho sinh nhật, ngày lễ cố định. */
    public static MonthDay ngayThangHomNay() {
        return MonthDay.now();
    }

    /** ⛔ Tên kiểu có chữ "Zoned" nên rất dễ tưởng đã an toàn — {@code now()} vẫn lấy múi giờ máy chủ. */
    public static ZonedDateTime bayGioCoMuiGio() {
        return ZonedDateTime.now();
    }

    /** ⛔ Cùng cái bẫy tên kiểu như trên. */
    public static OffsetDateTime bayGioCoDoLech() {
        return OffsetDateTime.now();
    }

    /** ⛔ Đường VÒNG: có đối số nên qua được vế {@code now()}, mà kết quả sai y hệt. */
    public static LocalDate homNayVongQuaZoneId() {
        return LocalDate.now(ZoneId.systemDefault());
    }

    /** ⛔ Đường vòng thứ hai — một {@code Clock} đọc múi giờ máy chủ. */
    public static Clock dongHoMayChu() {
        return Clock.systemDefaultZone();
    }

    /** ⛔ Đường vòng thứ ba, kiểu cũ. */
    public static TimeZone muiGioMayChu() {
        return TimeZone.getDefault();
    }

    /**
     * Cách viết <b>ĐÚNG</b> — bộ canh ⛔ không được báo lớp này.
     *
     * <p>Tách thành lớp riêng chứ ⛔ không để chung phương thức: bộ canh báo vi phạm theo <b>lớp</b>,
     * nên một phương thức hợp lệ nằm cùng lớp với bảy phương thức sai sẽ ⛔ không phân biệt được —
     * khẳng định "⛔ không báo nhầm" khi ấy về nguyên tắc ⛔ không thể đỏ.
     */
    public static final class DungChuan {

        private DungChuan() {}

        /** ⛔ Không múi giờ nào để mà sai — đây là lời khuyên của chính luật. */
        public static java.time.Instant bayGio() {
            return java.time.Instant.now();
        }

        /** Có đối số múi giờ TƯỜNG MINH — hợp lệ, và ⛔ không phải {@code systemDefault()}. */
        public static LocalDate homNayTheoGioVN() {
            return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        }

        /** {@code systemUTC} cố định UTC — ⛔ không đọc máy chủ, nên ⛔ không bị chặn. */
        public static Clock dongHoUtc() {
            return Clock.systemUTC();
        }
    }
}
