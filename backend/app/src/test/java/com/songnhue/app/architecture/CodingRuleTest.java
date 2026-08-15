package com.songnhue.app.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.GeneralCodingRules;

/**
 * Ba điều cấm của {@code conventions.md} §1.1 mà formatter không bắt được, cộng luật về thời gian
 * bắt nguồn từ quy tắc 1 của dự án.
 *
 * <p>Checkstyle đã chặn {@code System.out} và chuỗi {@code new Date()} ở mức văn bản. Ở đây chặn lại
 * ở mức <b>bytecode</b>, nên né bằng cách đổi cách viết cũng không lọt: {@code java.util.Date d = new
 * java.util.Date();} viết đầy đủ tên gói vẫn bị bắt như thường.
 */
class CodingRuleTest {

    /**
     * ⚠ <b>Cấm {@code float}/{@code double}</b> — quy tắc 2 của dự án.
     *
     * <p>Mực nước, lưu lượng, chi phí bảo trì, số dư ngày phép: tất cả đều là số phải cộng dồn và
     * phải khớp với sổ giấy. {@code double} không biểu diễn được {@code 0.1}, nên một phép cộng qua
     * vài nghìn bản ghi cho ra con số <i>gần đúng</i> — mà "gần đúng" trong báo cáo gửi cấp trên là
     * sai. Cái bẫy: sai số nhỏ tới mức không ai phát hiện lúc kiểm thử, chỉ lộ khi đối chiếu cuối kỳ.
     *
     * <p>Ưu tiên số 1 của dự án là <b>độ chính xác</b>. Luật này là chỗ nó được thi hành.
     */
    private static final ArchRule CAM_FLOAT_DOUBLE = classes()
            .should(new NoBinaryFloatingPoint())
            .because(
                    """
                    conventions.md §1.1 + quy tắc 2 của dự án: mọi số đo và tiền dùng BigDecimal/NUMERIC. \
                    double không biểu diễn chính xác số thập phân, sai số tích luỹ qua tổng hợp kỳ và chỉ lộ \
                    ra khi đối chiếu sổ sách.""");

    /**
     * ⚠ <b>Cấm đọc đồng hồ theo múi giờ máy chủ</b> — quy tắc 1 của dự án.
     *
     * <p>{@code new Date()}, {@code LocalDateTime.now()}, {@code Calendar.getInstance()} đều lấy múi
     * giờ của máy đang chạy. Trên máy dev ở Việt Nam chúng cho kết quả đúng; trên container production
     * chạy UTC thì lệch 7 tiếng — và triệu chứng là báo cáo theo ngày cắt sai ranh giới, dữ liệu nhập
     * lúc 17h hôm nay rơi sang ngày hôm sau. Không có lỗi nào báo ra.
     *
     * <p>{@code Date.from(instant)} <b>không</b> bị cấm: đó là phép đổi kiểu ở biên thư viện (Nimbus
     * JOSE bắt buộc dùng {@code Date} cho claim {@code exp}/{@code iat}), không phải đọc đồng hồ. Luật
     * này chặn đúng chỗ múi giờ bị đọc ngầm: hàm dựng rỗng và {@code now()} không tham số.
     */
    private static final ArchRule CAM_DOC_DONG_HO_MUI_GIO_MAY_CHU = classes()
            .should(new NoAmbientClock())
            .because(
                    """
                    Quy tắc 1 của dự án: lưu timestamptz UTC, hiển thị UTC+7. Đọc đồng hồ không kèm múi giờ \
                    cho kết quả đúng trên máy dev ở Việt Nam và sai 7 tiếng trên container UTC — báo cáo theo \
                    ngày cắt sai ranh giới mà không có lỗi nào. Dùng Instant.now(), hoặc LocalDate.now(ZONE_VN) \
                    khi thật sự cần ngày theo lịch Việt Nam.""");

    private static final ArchRule CAM_SIMPLE_DATE_FORMAT = noClasses()
            .should()
            .dependOnClassesThat()
            .haveFullyQualifiedName("java.text.SimpleDateFormat")
            .because("SimpleDateFormat không an toàn đa luồng và mặc định lấy múi giờ máy chủ — dùng "
                    + "DateTimeFormatter trong DateTimeUtils");

    private static final ArchRule CAM_STANDARD_STREAMS =
            GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS.because(
                    "conventions.md §1.1 — log phải đi qua SLF4J để có traceId, mức log và định dạng JSON; "
                            + "System.out không vào file log nào và biến mất khi chạy trong container");

    private static final ArchRule CAM_JAVA_UTIL_LOGGING =
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.because(
                    "cả dự án dùng SLF4J + Logback; java.util.logging đi đường riêng, không có traceId trong MDC");

    @Test
    @DisplayName("Không có float/double ở bất kỳ đâu — số đo và tiền dùng BigDecimal")
    void noBinaryFloatingPoint() {
        CAM_FLOAT_DOUBLE.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Không đọc đồng hồ theo múi giờ máy chủ")
    void noAmbientClockReads() {
        CAM_DOC_DONG_HO_MUI_GIO_MAY_CHU.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Không dùng SimpleDateFormat")
    void noSimpleDateFormat() {
        CAM_SIMPLE_DATE_FORMAT.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Không System.out / System.err")
    void noStandardStreams() {
        CAM_STANDARD_STREAMS.check(ProductionClasses.ALL);
    }

    @Test
    @DisplayName("Không java.util.logging")
    void noJavaUtilLogging() {
        CAM_JAVA_UTIL_LOGGING.check(ProductionClasses.ALL);
    }

    // -------------------------------------------------------------------------

    private static final Set<String> FORBIDDEN_TYPES = Set.of("double", "float", "java.lang.Double", "java.lang.Float");

    private static final class NoBinaryFloatingPoint extends ArchCondition<JavaClass> {

        private NoBinaryFloatingPoint() {
            super("không dùng float/double (dùng BigDecimal)");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (JavaField field : item.getFields()) {
                if (FORBIDDEN_TYPES.contains(field.getRawType().getName())) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "trường %s.%s kiểu %s"
                                    .formatted(
                                            item.getSimpleName(),
                                            field.getName(),
                                            field.getRawType().getSimpleName())));
                }
            }
            for (JavaCodeUnit codeUnit : item.getCodeUnits()) {
                List<JavaClass> parameters = codeUnit.getRawParameterTypes();
                for (JavaClass parameter : parameters) {
                    if (FORBIDDEN_TYPES.contains(parameter.getName())) {
                        events.add(SimpleConditionEvent.violated(
                                item,
                                "tham số kiểu %s ở %s.%s()"
                                        .formatted(
                                                parameter.getSimpleName(), item.getSimpleName(), codeUnit.getName())));
                    }
                }
                if (FORBIDDEN_TYPES.contains(codeUnit.getRawReturnType().getName())) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "%s.%s() trả về %s"
                                    .formatted(
                                            item.getSimpleName(),
                                            codeUnit.getName(),
                                            codeUnit.getRawReturnType().getSimpleName())));
                }
            }
        }
    }

    /** Bắt ở mức bytecode: hàm dựng rỗng của kiểu ngày cũ, và {@code now()} không kèm múi giờ. */
    private static final class NoAmbientClock extends ArchCondition<JavaClass> {

        private static final Set<String> LEGACY_DATE_TYPES = Set.of("java.util.Date", "java.util.GregorianCalendar");

        private static final Set<String> AMBIENT_NOW_OWNERS =
                Set.of("java.time.LocalDateTime", "java.time.LocalDate", "java.time.LocalTime", "java.time.Year");

        private NoAmbientClock() {
            super("không đọc đồng hồ theo múi giờ máy chủ");
        }

        @Override
        public void check(JavaClass item, ConditionEvents events) {
            for (JavaConstructorCall call : item.getConstructorCallsFromSelf()) {
                if (LEGACY_DATE_TYPES.contains(call.getTargetOwner().getName())
                        && call.getTarget().getRawParameterTypes().isEmpty()) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "gọi new %s() tại %s"
                                    .formatted(call.getTargetOwner().getSimpleName(), call.getSourceCodeLocation())));
                }
            }
            for (JavaMethodCall call : item.getMethodCallsFromSelf()) {
                String owner = call.getTargetOwner().getName();
                boolean ambientNow = AMBIENT_NOW_OWNERS.contains(owner)
                        && "now".equals(call.getName())
                        && call.getTarget().getRawParameterTypes().isEmpty();
                boolean legacyCalendar = "java.util.Calendar".equals(owner) && "getInstance".equals(call.getName());
                if (ambientNow || legacyCalendar) {
                    events.add(SimpleConditionEvent.violated(
                            item,
                            "gọi %s.%s() không kèm múi giờ tại %s"
                                    .formatted(
                                            call.getTargetOwner().getSimpleName(),
                                            call.getName(),
                                            call.getSourceCodeLocation())));
                }
            }
        }
    }
}
