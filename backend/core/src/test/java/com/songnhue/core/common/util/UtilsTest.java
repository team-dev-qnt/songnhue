package com.songnhue.core.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.songnhue.core.common.exception.ValidationException;

/** Kiểm các bẫy thật của 6 util thuần (2 util còn lại cần Spring context, test riêng). */
class UtilsTest {

    @Nested
    @DisplayName("VietnameseUtils")
    class Vietnamese {

        @Test
        @DisplayName("Đ/đ phải thành D/d — Normalizer không tự tách được chữ này")
        void handlesDStroke() {
            assertThat(VietnameseUtils.removeDiacritics("Đông Anh")).isEqualTo("Dong Anh");
            assertThat(VietnameseUtils.removeDiacritics("đập tràn")).isEqualTo("dap tran");
            // Nếu quên xử lý Đ thì slug thành "ng-anh" — lỗi kinh điển
            assertThat(VietnameseUtils.toSlug("Đông Anh")).isEqualTo("dong-anh");
        }

        @Test
        void removesAllVietnameseDiacritics() {
            assertThat(VietnameseUtils.removeDiacritics("Nguyễn Đình Chiểu")).isEqualTo("Nguyen Dinh Chieu");
            assertThat(VietnameseUtils.removeDiacritics("Thủy lợi Sông Nhuệ")).isEqualTo("Thuy loi Song Nhue");
        }

        @Test
        void buildsCleanSlug() {
            assertThat(VietnameseUtils.toSlug("Thông báo Điều tiết nước vụ Đông Xuân 2026"))
                    .isEqualTo("thong-bao-dieu-tiet-nuoc-vu-dong-xuan-2026");
            // Không để lại dấu gạch thừa ở hai đầu, không để gạch đôi ở giữa
            assertThat(VietnameseUtils.toSlug("  --- Cống  Liên   Mạc !!! ")).isEqualTo("cong-lien-mac");
        }

        @Test
        @DisplayName("Tìm không dấu ra được bản ghi có dấu")
        void normalizesForSearch() {
            assertThat(VietnameseUtils.normalizeForSearch("Nguyễn  Văn   Ấn")).isEqualTo("nguyen van an");
        }
    }

    @Nested
    @DisplayName("NumericUtils")
    class Numeric {

        @Test
        @DisplayName("So sánh bỏ qua scale — bẫy equals() của BigDecimal")
        void comparesIgnoringScale() {
            BigDecimal a = new BigDecimal("1.50");
            BigDecimal b = new BigDecimal("1.5");

            assertThat(a.equals(b)).isFalse(); // đây chính là cái bẫy
            assertThat(NumericUtils.eq(a, b)).isTrue(); // và đây là cách đúng
        }

        @Test
        @DisplayName("Trung bình không có dữ liệu trả null, KHÔNG trả 0")
        void averageOfNothingIsNull() {
            assertThat(NumericUtils.average(List.of(), 3)).isNull();
            assertThat(NumericUtils.average(Arrays.asList(null, null), 3)).isNull();
            // Trả 0 sẽ vẽ ra mực nước bằng 0 trên biểu đồ — sai nghiêm trọng
        }

        @Test
        void averageSkipsNullValues() {
            var values = Arrays.asList(new BigDecimal("2.000"), null, new BigDecimal("4.000"));
            assertThat(NumericUtils.average(values, 3)).isEqualByComparingTo("3.000");
        }

        @Test
        @DisplayName("Nguồn thủy văn trả cm, hệ thống lưu m scale 3")
        void convertsCentimetersToMeters() {
            assertThat(NumericUtils.centimetersToMeters(new BigDecimal("447"))).isEqualByComparingTo("4.470");
            assertThat(NumericUtils.centimetersToMeters(new BigDecimal("294"))).isEqualByComparingTo("2.940");
        }

        @Test
        void sumIsNullSafe() {
            assertThat(NumericUtils.sum(Arrays.asList(new BigDecimal("1.5"), null, new BigDecimal("2.5"))))
                    .isEqualByComparingTo("4.0");
            assertThat(NumericUtils.sum(null)).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Làm tròn HALF_UP, không phải HALF_EVEN mặc định của Java")
        void roundsHalfUp() {
            assertThat(NumericUtils.scale(new BigDecimal("2.5"), 0)).isEqualByComparingTo("3");
            assertThat(NumericUtils.scale(new BigDecimal("1.2345"), 3)).isEqualByComparingTo("1.235");
        }
    }

    @Nested
    @DisplayName("MaskUtils")
    class Mask {

        @Test
        void masksSensitiveFields() {
            assertThat(MaskUtils.maskIdNumber("001234567890")).isEqualTo("0012*****890");
            assertThat(MaskUtils.maskPhone("0912345678")).isEqualTo("091*****78");
            assertThat(MaskUtils.maskEmail("nguyenvana@congty.vn")).isEqualTo("ngu*******@congty.vn");
        }

        @Test
        @DisplayName("Chuỗi quá ngắn thì che TOÀN BỘ, không lộ gần hết")
        void masksEverythingWhenTooShort() {
            assertThat(MaskUtils.maskIdNumber("123")).isEqualTo("***");
            assertThat(MaskUtils.maskMiddle("ab", 4, 3)).isEqualTo("**");
        }

        @Test
        @DisplayName("Credential chỉ giữ 2 ký tự cuối")
        void masksCredentialAggressively() {
            assertThat(MaskUtils.maskCredential("SUPERSECRETKEY;"))
                    .endsWith("Y;")
                    .doesNotContain("SUPERSECRET");
        }
    }

    @Nested
    @DisplayName("PageUtils")
    class Page {

        private static final Set<String> ALLOWED = Set.of("createdAt", "name");

        @Test
        @DisplayName("Trường sort ngoài whitelist bị từ chối")
        void rejectsSortFieldOutsideWhitelist() {
            assertThatThrownBy(() -> PageUtils.parseSort("password,asc", ALLOWED))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Lỗi KHÔNG liệt kê danh sách trường hợp lệ — đó là bản đồ cấu trúc bảng")
        void errorDoesNotLeakAllowedFields() {
            var ex = org.junit.jupiter.api.Assertions.assertThrows(
                    ValidationException.class, () -> PageUtils.parseSort("secretColumn,asc", ALLOWED));

            assertThat(ex.details()).hasSize(1);
            assertThat(ex.details().get(0).rule()).isEqualTo("SORT_FIELD_NOT_ALLOWED");
            assertThat(ex.details().toString()).doesNotContain("createdAt").doesNotContain("name");
        }

        @Test
        void acceptsWhitelistedSort() {
            Sort sort = PageUtils.parseSort("createdAt,desc", ALLOWED);
            assertThat(sort.getOrderFor("createdAt")).isNotNull();
            assertThat(sort.getOrderFor("createdAt").isDescending()).isTrue();
        }

        @Test
        @DisplayName("size bị kẹp trần, page đếm từ 1 đổi sang đếm từ 0")
        void clampsPageAndSize() {
            Pageable pageable = PageUtils.toPageable(1, 100_000, null, ALLOWED);
            assertThat(pageable.getPageSize()).isEqualTo(PageUtils.MAX_SIZE);
            assertThat(pageable.getPageNumber()).isZero();

            assertThat(PageUtils.toPageable(3, 20, null, ALLOWED).getPageNumber())
                    .isEqualTo(2);
            assertThat(PageUtils.toPageable(0, null, null, ALLOWED).getPageNumber())
                    .isZero();
        }
    }

    @Nested
    @DisplayName("FileValidator")
    class File {

        private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        private static final byte[] PDF = {'%', 'P', 'D', 'F', '-', '1', '.', '7', 0, 0, 0, 0};

        @Test
        void detectsByMagicBytes() {
            assertThat(FileValidator.detect(PNG)).isEqualTo("image/png");
            assertThat(FileValidator.detect(PDF)).isEqualTo("application/pdf");
        }

        @Test
        @DisplayName("Đổi đuôi tệp KHÔNG qua được — chỉ tin nội dung")
        void extensionSpoofingIsRejected() {
            // Tệp PDF nhưng đặt tên .png, và chỉ cho phép ảnh
            assertThatThrownBy(() -> FileValidator.detectAndValidate(PDF, "anh.png", List.of("image/png")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Nội dung không khớp chữ ký nào thì từ chối, không đoán bừa")
        void rejectsUnknownContent() {
            byte[] garbage = "khong-phai-tep-hop-le".getBytes();
            assertThat(FileValidator.detect(garbage)).isNull();
            assertThatThrownBy(() -> FileValidator.detectAndValidate(garbage, "x.png", List.of("image/png")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        void rejectsEmptyFile() {
            assertThatThrownBy(() -> FileValidator.detectAndValidate(new byte[0], "x.png", List.of("image/png")))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("Tên lưu trữ random hoá, đuôi lấy theo MIME đã xác thực")
        void randomisesStorageName() {
            String name = FileValidator.randomStorageName("png");
            assertThat(name).endsWith(".png").hasSizeGreaterThan(20);
            assertThat(FileValidator.randomStorageName("png")).isNotEqualTo(name);
            // Không cho ký tự lạ lọt vào tên tệp
            // Ký tự đường dẫn bị lọc sạch khỏi "đuôi tệp"; dấu chấm còn lại đúng một cái,
            // là dấu ngăn giữa UUID và đuôi.
            String sanitised = FileValidator.randomStorageName("../../etc/passwd");
            assertThat(sanitised).doesNotContain("/").doesNotContain("..");
            assertThat(sanitised.chars().filter(c -> c == '.').count()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("DateTimeUtils")
    class DateTime {

        @Test
        @DisplayName("UTC ⇄ UTC+7 lệch đúng 7 giờ")
        void convertsBetweenUtcAndVietnam() {
            Instant utc = DateTimeUtils.atUtc(2026, 8, 14, 3, 0);
            assertThat(DateTimeUtils.toVietnamTime(utc).getHour()).isEqualTo(10);
            assertThat(DateTimeUtils.formatDateTime(utc)).isEqualTo("14/08/2026 10:00");
        }

        @Test
        @DisplayName("Cận trên là đầu ngày HÔM SAU, không phải 23:59:59")
        void endOfDayIsExclusive() {
            LocalDate day = LocalDate.of(2026, 8, 14);
            Instant end = DateTimeUtils.endOfDayExclusive(day);
            Instant nextStart = DateTimeUtils.startOfDay(day.plusDays(1));
            // Trùng khít nghĩa là không có khe hở làm mất bản ghi lúc 23:59:59.5
            assertThat(end).isEqualTo(nextStart);
        }

        @Test
        @DisplayName("Ngày làm việc trừ cuối tuần và ngày lễ")
        void countsWorkingDays() {
            // 2026-08-10 (T2) → 2026-08-16 (CN): 5 ngày làm việc
            LocalDate from = LocalDate.of(2026, 8, 10);
            LocalDate to = LocalDate.of(2026, 8, 16);
            assertThat(DateTimeUtils.countWorkingDays(from, to, Set.of())).isEqualTo(5);

            // Nghỉ lễ thứ Tư → còn 4
            assertThat(DateTimeUtils.countWorkingDays(from, to, Set.of(LocalDate.of(2026, 8, 12))))
                    .isEqualTo(4);
        }

        @Test
        @DisplayName("Ca đêm vắt qua nửa đêm vẫn tính đúng độ dài")
        void handlesNightShift() {
            var length = DateTimeUtils.shiftLength(java.time.LocalTime.of(22, 0), java.time.LocalTime.of(6, 0));
            assertThat(length.toHours()).isEqualTo(8);
        }
    }
}
