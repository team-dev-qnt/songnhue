package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;

/**
 * Dọn dữ liệu quá hạn — T29.7. ⛔ Đây là <b>hàm XOÁ KHÔNG PHỤC HỒI ĐƯỢC chạy bằng quyền chủ sở
 * hữu</b>, nên bài này dành phần lớn công sức cho các <i>lối vào bị chặn</i>, không cho lối đi đúng.
 *
 * <h2>Vì sao ba lớp chặn, và vì sao mỗi lớp cần một phép kiểm riêng</h2>
 *
 * <ol>
 *   <li><b>Danh sách trắng tên bảng</b> — tham số không đi thẳng vào {@code EXECUTE}. Kiểm ở cả phía
 *       Java lẫn phía CSDL: phía CSDL chặn mọi đường gọi kể cả đường chưa ai nghĩ tới, phía Java cho
 *       lỗi ở đúng dòng mã sai.
 *   <li><b>Sàn an toàn 7 ngày</b> — chặn đúng một loại lỗi rất dễ mắc: <b>nhầm đơn vị</b> khi đọc
 *       tham số (ngày ↔ tháng ↔ năm). Với sàn ấy, lỗi như vậy chỉ làm job đỏ chứ không xoá mất dữ
 *       liệu của tuần này. Không có sàn thì một {@code minusDays} viết nhầm thành {@code minusHours}
 *       xoá sạch mọi thứ, một lần, lúc 04:30 sáng.
 *   <li><b>{@code search_path} cố định + tên bảng viết đủ {@code public.}</b> — với hàm
 *       {@code SECURITY DEFINER} thì {@code search_path} thừa hưởng từ người gọi là một đường chiếm
 *       quyền.
 * </ol>
 *
 * <p>⚠ Bài này cố ý thao tác trên <b>partition tháng rất cũ</b> mà nó tự tạo ra, ⛔ không đụng vào 12
 * partition của runway thật — xoá nhầm một trong số đó thì mọi bài kiểm sau đó chạy trên một lược đồ
 * khác, và nguyên nhân sẽ nằm cách chỗ báo lỗi rất xa.
 */
class HydroRetentionTest extends IntegrationTestBase {

    /** Rất cũ, và ⛔ không chạm runway (migration tạo từ tháng hiện tại trở đi). */
    private static final LocalDate THANG_CU = LocalDate.of(2020, 1, 15);

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HydroMaintenanceRepository maintenance;

    /**
     * Mười hai tên partition mà migration phải đã tạo — <b>hỏi chính CSDL</b>, ⛔ không tính bằng
     * {@code LocalDate} phía Java.
     *
     * <p>{@code hyd_ensure_time_series_partitions} dùng {@code current_date} của phiên CSDL. Tính
     * lại phía Java là dựng một công thức thứ hai cho cùng một sự thật, và hai công thức ấy lệch
     * nhau đúng một ngày mỗi tháng — vào đêm giao thừa của tháng, ở đúng cái múi giờ không ai để ý.
     */
    private List<String> runwayMongDoi(String bang) {
        return jdbc.queryForList(
                """
                SELECT ? || '_p' || to_char(current_date + (g || ' month')::interval, 'YYYYMM')
                  FROM generate_series(0, 11) AS g ORDER BY 1
                """,
                String.class,
                bang);
    }

    private boolean coPartition(String ten) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT to_regclass('public.' || ?) IS NOT NULL", Boolean.class, ten));
    }

    /**
     * ⛔ Dọn <b>phải</b> đi qua chính hàm đang được kiểm, ⛔ không bằng {@code DROP TABLE} tay.
     *
     * <p>Bản đầu của bài này dùng {@code DROP TABLE IF EXISTS} trong khối {@code finally} và nó
     * <b>ném {@code must be owner of table}</b>: vai trò {@code songnhue_app} không sở hữu bảng —
     * đó chính là điều buộc hàm kia phải {@code SECURITY DEFINER}. Hệ quả đo được: partition
     * {@code p202001} rò rỉ sang bài kiểm sau, và <b>hai bài ở hai lớp khác nhau đỏ vì một nguyên
     * nhân nằm ở đây</b>. Một bài kiểm dọn dẹp sai làm hỏng bài khác là dạng đỏ tốn thời gian nhất.
     *
     * <p>Mốc cắt 01/02/2020 chỉ chạm tháng 1/2020 — ⛔ không đụng 12 partition runway thật (2026+).
     */
    @AfterEach
    void donPartitionCu() {
        maintenance.dropPartitionsBefore("hydro_readings", LocalDate.of(2020, 2, 1));
    }

    @Test
    @DisplayName("⭐ Tạo rồi xoá một partition tháng cũ — vòng đầy đủ, chạy bằng vai trò songnhue_app")
    void taoRoiXoaPartitionCu() {
        String ten = "hydro_readings_p202001";

        Boolean daTao = jdbc.queryForObject(
                "SELECT hyd_create_month_partition('hydro_readings', ?)", Boolean.class, Date.valueOf(THANG_CU));
        assertThat(daTao)
                .as("⛔ vế chống xanh-trên-tập-rỗng: không tạo được thì phép xoá dưới đây xoá một thứ không tồn tại")
                .isTrue();
        assertThat(coPartition(ten)).isTrue();

        // Mốc cắt = đầu tháng KẾ TIẾP: partition tháng 1 chỉ được xoá khi cả tháng đã nằm trước mốc.
        int daXoa = maintenance.dropPartitionsBefore("hydro_readings", LocalDate.of(2020, 2, 1));

        assertThat(daXoa).isEqualTo(1);
        assertThat(coPartition(ten)).isFalse();
        assertThat(maintenance.partitionNames("hydro_readings"))
                .as("⛔ và 12 partition của runway thật KHÔNG được đụng tới")
                .containsExactlyElementsOf(runwayMongDoi("hydro_readings"));
    }

    @Test
    @DisplayName("⭐ Xoá theo THÁNG TRỌN VẸN — mốc cắt giữa tháng thì partition ấy còn nguyên")
    void chiXoaThangDaNamTronTruocMoc() {
        assertThat(jdbc.queryForObject(
                        "SELECT hyd_create_month_partition('hydro_readings', ?)",
                        Boolean.class,
                        Date.valueOf(THANG_CU)))
                .as("⛔ vế chống xanh-trên-tập-rỗng: không tạo được thì phép giữ-lại dưới đây vô nghĩa")
                .isTrue();

        // Mốc 20/01/2020 nằm GIỮA tháng 1 ⇒ tháng ấy chưa nằm trọn trước mốc ⇒ giữ lại.
        // Đây là lý do hạn lưu thực tế luôn dài hơn con số cấu hình, tối đa thêm một tháng.
        assertThat(maintenance.dropPartitionsBefore("hydro_readings", LocalDate.of(2020, 1, 20)))
                .isZero();
        assertThat(coPartition("hydro_readings_p202001")).isTrue();
    }

    @Test
    @DisplayName("⛔⛔ Sàn an toàn: mốc cắt quá gần hiện tại bị TỪ CHỐI, không xoá gì")
    void sanAnToanChanMocQuaGan() {
        assertThatThrownBy(() -> maintenance.dropPartitionsBefore("hydro_readings", LocalDate.now()))
                .as("một `minusDays` viết nhầm thành `minusHours` phải làm job ĐỎ, ⛔ không được xoá dữ liệu tuần này")
                .hasMessageContaining("sàn an toàn");

        assertThatThrownBy(() -> maintenance.dropPartitionsBefore(
                        "hydro_raw_logs", LocalDate.now().minusDays(6)))
                .as("6 ngày vẫn nằm trong sàn — biên phải chặt, không phải xấp xỉ")
                .hasMessageContaining("sàn an toàn");

        assertThat(maintenance.partitionNames("hydro_readings"))
                .as("và sau hai lượt bị từ chối, runway vẫn nguyên vẹn")
                .containsExactlyElementsOf(runwayMongDoi("hydro_readings"));
    }

    @Test
    @DisplayName("⛔ Danh sách trắng chặn ở CẢ HAI phía — Java và CSDL")
    void danhSachTrangChanCaHaiPhia() {
        // ⚠ Khẳng định ở THÔNG BÁO, không ở kiểu ngoại lệ: lớp mang `@Repository` nên
        //   `PersistenceExceptionTranslationInterceptor` bọc lại mọi RuntimeException thành
        //   `InvalidDataAccessApiUsageException`. `IllegalArgumentException` ta ném ra không còn là
        //   kiểu mà nơi gọi nhìn thấy — một chi tiết chỉ lộ ra khi chạy thật trong Spring context,
        //   và bài kiểm đơn vị thuần sẽ khẳng định sai về nó.
        assertThatThrownBy(() -> maintenance.dropPartitionsBefore("audit_logs", LocalDate.of(2020, 2, 1)))
                .as("phía Java: lỗi ở đúng dòng mã sai, và thông báo phải đi qua được lớp bọc của Spring")
                .hasMessageContaining("không phải bảng phân mảnh");

        // ⭐ Và phía CSDL — gọi thẳng hàm, vòng qua lớp Java. Đây là vế quan trọng hơn: kiểm ở tầng
        //   ứng dụng thì một lời gọi từ psql, từ một job tương lai, hay từ một lớp khác đều đi lọt.
        assertThatThrownBy(() -> jdbc.queryForObject(
                        "SELECT hyd_drop_month_partitions_before('audit_logs', ?)",
                        Integer.class,
                        Date.valueOf(LocalDate.of(2020, 2, 1))))
                .hasMessageContaining("không nằm trong danh sách cho phép");

        assertThatThrownBy(() -> jdbc.queryForObject(
                        "SELECT hyd_create_month_partition('users', ?)",
                        Boolean.class,
                        Date.valueOf(LocalDate.of(2020, 1, 1))))
                .hasMessageContaining("không nằm trong danh sách cho phép");
    }

    @Test
    @DisplayName("⭐ Hai hàm phân mảnh phải là SECURITY DEFINER — nếu không, job đỏ ở production sau 12 tháng")
    void haiHamPhaiLaSecurityDefiner() {
        // ⛔ Đây là loại khuyết tật mà bộ test KHÔNG thấy được nếu chỉ chạy đường thành công: Flyway
        //   chạy bằng `songnhue_owner`, nên lượt tạo runway trong migration luôn xanh. Vai trò
        //   `songnhue_app` mới là vai trò job chạy bằng, và nó KHÔNG có CREATE trên schema public.
        //   Runway 12 tháng che khuyết tật ấy suốt một năm — đúng kiểu nợ dễ quên nhất.
        // ⚠ `array_to_string` ở tầng SQL, ⛔ không `String.valueOf` một `java.sql.Array` phía Java:
        //   cách sau phụ thuộc `toString()` của trình điều khiển, và một khẳng định phụ thuộc
        //   `toString()` là một khẳng định có thể xanh vì lý do sai.
        List<Map<String, Object>> ket = jdbc.queryForList(
                """
                SELECT p.proname, p.prosecdef, coalesce(array_to_string(p.proconfig, ','), '') AS cauhinh
                  FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
                 WHERE n.nspname = 'public'
                   AND p.proname IN ('hyd_create_month_partition', 'hyd_ensure_time_series_partitions',
                                     'hyd_drop_month_partitions_before')
                 ORDER BY p.proname
                """);

        assertThat(ket).as("⛔ vế chống xanh-trên-tập-rỗng: ba hàm phải tồn tại").hasSize(3);
        for (Map<String, Object> dong : ket) {
            assertThat(dong.get("prosecdef"))
                    .as("hàm %s không phải SECURITY DEFINER", dong.get("proname"))
                    .isEqualTo(Boolean.TRUE);
            assertThat((String) dong.get("cauhinh"))
                    .as(
                            "hàm %s không ghim search_path — với SECURITY DEFINER đó là đường chiếm quyền",
                            dong.get("proname"))
                    .contains("search_path=");
        }
    }
}
