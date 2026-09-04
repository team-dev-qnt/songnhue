package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.app.testsupport.PhienHttp;
import com.songnhue.core.application.auth.PasswordPolicyService;
import com.songnhue.core.common.util.DateTimeUtils;
import com.songnhue.core.infra.identity.UserRepository;
import com.songnhue.hydro.application.HydroAggService;

/**
 * ⭐⭐ <b>NFR-04 — báo cáo tháng dưới 60 giây</b>. T34.9.
 *
 * <h2>Đây là một PHÉP ĐO, ⛔ không phải một lời khẳng định</h2>
 *
 * <p>Bài này dựng <b>một tháng dữ liệu thật ở quy mô thật</b> — mọi điểm đo đang khai × mọi ngày của
 * tháng × <b>144 khung mỗi ngày</b> — rồi bấm giờ đúng hai endpoint mà người dùng bấm. Số đo được
 * <b>ghi ra log</b> ở mỗi lượt chạy, vì một cam kết NFR chỉ có nghĩa khi con số của nó còn đọc được
 * sau khi bài kiểm đã xanh.
 *
 * <p>⛔ Cách sai mà bài này cố tránh: chạy báo cáo trên CSDL rỗng rồi kết luận "nhanh". Ở quy mô vài
 * nghìn bản ghi thì <b>cả câu SQL đúng lẫn câu SQL sai</b> đều trả lời trong mili-giây — phép đo ấy
 * ⛔ không phân biệt được hai trạng thái, tức ⛔ không đo gì (luật 9).
 *
 * <h2>⚠ Dữ liệu ở đây là dữ liệu KIỂM THỬ, và nó bị dọn sạch</h2>
 *
 * <p>{@code CLAUDE.md} cấm <i>seed dữ liệu thuỷ văn "cho đẹp demo"</i>. Điều cấm ấy nói về dữ liệu
 * <b>đi vào sản phẩm</b>; ở đây số liệu do bài kiểm sinh ra, sống trong một giao dịch của bộ kiểm và
 * bị {@code @AfterAll} xoá — cùng khuôn với mọi lớp kiểm tích hợp khác. ⛔ Đừng dùng lại khối
 * {@code generate_series} này ở migration.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HydroReportPerformanceTest extends IntegrationTestBase {

    private static final Logger log = LoggerFactory.getLogger(HydroReportPerformanceTest.class);

    /** NFR-04 — {@code function-spec.md}. ⛔ Đây là trần của cam kết, ⛔ không phải mục tiêu. */
    private static final long TRAN_NFR04_MS = 60_000;

    /** 24 giờ × 6 khung = số bản ghi một điểm đo sinh ra mỗi ngày. */
    private static final int KHUNG_MOI_NGAY = 144;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordPolicyService passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HydroAggService tongHop;

    private PhienHttp phienHttp;
    private PhienHttp.Phien kyThuat;

    private LocalDate dauThang;
    private LocalDate cuoiThang;
    private long idLoaiChiSo;
    private int soDiemDo;

    @BeforeAll
    void dungDuLieu() {
        phienHttp = new PhienHttp(http);
        kyThuat = phienHttp.dangNhap(PhienHttp.taoNguoiDung(users, passwords, jdbc, "t349_nfr", "TECHNICIAN"));

        // ⚠ THÁNG TRƯỚC, ⛔ không phải tháng này: các lớp kiểm khác ghi số đo vào những ngày quanh
        //   hôm nay, và một phép ĐO thời gian mà tập dữ liệu đổi theo thứ tự chạy thì ⛔ không đo gì.
        LocalDate homNay = LocalDate.now(DateTimeUtils.ZONE_VN);
        dauThang = homNay.minusMonths(1).withDayOfMonth(1);
        cuoiThang = dauThang.plusMonths(1).minusDays(1);

        idLoaiChiSo = jdbc.queryForObject(
                "SELECT id FROM measurement_types WHERE code = 'MUC_NUOC' AND deleted_at IS NULL", Long.class);
        soDiemDo = jdbc.queryForObject(
                """
                SELECT count(*) FROM station_measurement_types smt
                  JOIN stations s ON s.id = smt.station_id AND s.deleted_at IS NULL AND s.active
                 WHERE smt.measurement_type_id = ?
                """,
                Integer.class,
                idLoaiChiSo);

        assertThat(soDiemDo)
                .as("⚠ Vế chống tập rỗng: ⛔ không có điểm đo nào thì phép đo dưới đây là phép đo trên "
                        + "một CSDL rỗng — và một CSDL rỗng thì câu SQL nào cũng nhanh")
                .isGreaterThanOrEqualTo(10);
    }

    @AfterAll
    void donSach() {
        jdbc.update(
                "DELETE FROM hydro_agg_daily WHERE agg_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(dauThang),
                java.sql.Date.valueOf(cuoiThang));
        jdbc.update(
                "DELETE FROM hydro_agg_dirty WHERE agg_date BETWEEN ? AND ?",
                java.sql.Date.valueOf(dauThang),
                java.sql.Date.valueOf(cuoiThang));
        jdbc.update(
                "DELETE FROM hydro_readings WHERE measured_at >= hyd_dau_ngay_vn(?) "
                        + "AND measured_at < hyd_dau_ngay_vn((?::date) + 1)",
                java.sql.Date.valueOf(dauThang),
                java.sql.Date.valueOf(cuoiThang));
    }

    @Test
    @Order(1)
    @DisplayName("⭐ Dựng một tháng dữ liệu ở quy mô THẬT — và trigger cắm cờ đúng MỘT lần cho mỗi kỳ")
    void aFullMonthOfRealVolume() {
        long batDau = System.nanoTime();
        int daGhi = jdbc.update(
                """
                INSERT INTO hydro_readings (
                    measured_at, station_id, measurement_type_id, reading_value, quality, source)
                SELECT moc, s.id, ?, 2.000 + (extract(epoch FROM moc)::bigint % 300) / 100.0, 'HOP_LE', 'API'
                  FROM station_measurement_types smt
                  JOIN stations s ON s.id = smt.station_id AND s.deleted_at IS NULL AND s.active
                  CROSS JOIN generate_series(
                        hyd_dau_ngay_vn(?),
                        hyd_dau_ngay_vn((?::date) + 1) - INTERVAL '10 minutes',
                        INTERVAL '10 minutes') AS moc
                 WHERE smt.measurement_type_id = ?
                ON CONFLICT DO NOTHING
                """,
                idLoaiChiSo, java.sql.Date.valueOf(dauThang), java.sql.Date.valueOf(cuoiThang), idLoaiChiSo);

        long msGhi = (System.nanoTime() - batDau) / 1_000_000;
        int soNgay = cuoiThang.getDayOfMonth();
        log.info(
                "⏱ NFR-04 — dựng {} bản ghi ({} điểm đo × {} ngày × {} khung) mất {} ms; "
                        + "trigger cắm cờ chạy đúng {} lượt",
                daGhi,
                soDiemDo,
                soNgay,
                KHUNG_MOI_NGAY,
                msGhi,
                daGhi);

        assertThat(daGhi)
                .as("⚠ Vế chống tập rỗng cho chính phép đo: thiếu dữ liệu thì mọi con số dưới đây vô nghĩa")
                .isEqualTo(soDiemDo * soNgay * KHUNG_MOI_NGAY);

        // ⭐⭐ Trigger chạy 84 nghìn lượt nhưng hàng đợi chỉ được đúng (điểm đo × ngày) hàng —
        //    `ON CONFLICT DO NOTHING` trên khoá ba thành phần làm việc đúng dưới tải hàng loạt.
        //    ⚠ Nếu con số này bằng số bản ghi thì hàng đợi cờ bẩn đã thành một bảng log, và lượt
        //      drain kế tiếp sẽ tính lại cùng một kỳ hàng trăm lần.
        Long soKyBan = jdbc.queryForObject(
                "SELECT count(*) FROM hydro_agg_dirty WHERE agg_date BETWEEN ? AND ?",
                Long.class,
                java.sql.Date.valueOf(dauThang),
                java.sql.Date.valueOf(cuoiThang));
        assertThat(soKyBan)
                .as("cờ bẩn phải gom về ĐÚNG một hàng cho mỗi (điểm đo × chỉ số × ngày)")
                .isEqualTo((long) soDiemDo * soNgay);
    }

    @Test
    @Order(2)
    @DisplayName("⭐ Tổng hợp cả tháng — và một lượt drain CÓ TRẦN, nên nạp bù cần nhiều lượt")
    void aggregatingAWholeMonth() {
        int soKyCanTinh = soDiemDo * cuoiThang.getDayOfMonth();

        long batDau = System.nanoTime();
        int luotDau = tongHop.chayMotLuot();
        long msLuotDau = (System.nanoTime() - batDau) / 1_000_000;

        // ⭐⭐ Một lượt drain xử lý TỐI ĐA `TRAN_MOI_LUOT` kỳ — đây là hành vi ĐÚNG và có chủ đích,
        //    ⛔ không phải một giới hạn cần vá. Nó giữ cho một lượt việc nền ⛔ không chạy hàng chục
        //    phút và ⛔ không giữ connection quá lâu; phần còn lại rơi sang lượt kế tiếp 5 phút sau,
        //    và hàng đợi thì ⛔ không mất gì.
        //
        // ⚠ Bản đầu của bài này khẳng định một lượt tính hết 589 kỳ — SAI, và cái sai ấy đo được
        //   ngay: lượt đầu trả đúng 500. Ghi lại ở đây để lượt sau ⛔ đừng "sửa" trần ấy đi.
        assertThat(luotDau)
                .as("một lượt drain có trần — %d kỳ cần tính, lượt đầu xử lý %d", soKyCanTinh, luotDau)
                .isLessThanOrEqualTo(500);

        int soLuot = 1;
        int tong = luotDau;
        // ⛔ Vòng lặp CÓ CẬN: một `while (soKyDangCho() > 0)` không cận là một lần treo CI ⛔ không
        //   có thông điệp nào.
        while (tongHop.soKyDangCho() > 0 && soLuot < 20) {
            tong += tongHop.chayMotLuot();
            soLuot++;
        }
        long msTong = (System.nanoTime() - batDau) / 1_000_000;

        log.info(
                "⏱ NFR-04 — tổng hợp {} kỳ qua {} lượt drain mất {} ms (lượt đầu {} kỳ / {} ms)",
                tong,
                soLuot,
                msTong,
                luotDau,
                msLuotDau);

        assertThat(tongHop.soKyDangCho())
                .as("⛔ Hàng đợi cờ bẩn phải vơi hết sau vài lượt — còn hàng nghĩa là có kỳ ⛔ không "
                        + "tính lại được, và một bảng tổng hợp thiếu kỳ trông y hệt một bảng đủ")
                .isZero();

        Long soHang = jdbc.queryForObject(
                "SELECT count(*) FROM hydro_agg_daily WHERE agg_date BETWEEN ? AND ? AND quality = 'HOP_LE'",
                Long.class,
                java.sql.Date.valueOf(dauThang),
                java.sql.Date.valueOf(cuoiThang));
        assertThat(soHang).isEqualTo((long) soKyCanTinh);
    }

    @Test
    @Order(3)
    @DisplayName("⭐⭐ NFR-04 — BC-05 báo cáo tháng qua HTTP, dưới 60 giây")
    void theMonthlySummaryReportMeetsNfr04() {
        long ms = bamGio("/api/v1/hyd/bao-cao/tong-hop?tuNgay=%s&denNgay=%s".formatted(dauThang, cuoiThang));

        log.info("⏱ NFR-04 — BC-05 tháng {}: {} ms (trần {} ms)", dauThang.getMonth(), ms, TRAN_NFR04_MS);
        assertThat(ms)
                .as(
                        """
                        ⛔ BC-05 vượt trần NFR-04. Đường sửa gần như luôn là một chỗ: có câu truy vấn đang \
                        quét `hydro_readings` thay vì `hydro_agg_daily` (quy tắc 8) — `ReportReadsAggregateTest` \
                        canh đúng chiều ấy.""")
                .isLessThan(TRAN_NFR04_MS);
    }

    @Test
    @Order(4)
    @DisplayName("⭐⭐ NFR-04 — BC-13 chất lượng dữ liệu cả tháng, dưới 60 giây")
    void theSyncQualityReportMeetsNfr04() {
        long ms = bamGio("/api/v1/hyd/bao-cao/dong-bo?tuNgay=%s&denNgay=%s".formatted(dauThang, cuoiThang));

        // ⚠ BC-13 nặng hơn BC-05: nó CROSS JOIN dãy ngày với mọi cặp (điểm đo × chỉ số), nên số hàng
        //   trả về là (ngày × cặp) chứ ⛔ không phải (cặp). Đó là chủ ý — ngày poller chết hoàn toàn
        //   phải có hàng để nói ra, xem javadoc SQL_CHAT_LUONG_NGAY.
        log.info("⏱ NFR-04 — BC-13 tháng {}: {} ms (trần {} ms)", dauThang.getMonth(), ms, TRAN_NFR04_MS);
        assertThat(ms).isLessThan(TRAN_NFR04_MS);
    }

    @Test
    @Order(5)
    @DisplayName("⭐ BC-11 biểu tuyến sông — màn hình tường tự làm mới, nên nó phải NHANH")
    void theRiverBoardIsFastEnoughToPoll() {
        long ms = bamGio("/api/v1/hyd/bao-cao/tuyen-song");

        log.info("⏱ BC-11 biểu tuyến sông: {} ms", ms);
        // ⚠ Trần chặt hơn NFR-04 rất nhiều, và có lý do: biểu này tự gọi lại 2 phút một lần trên
        //   màn hình tường. Một endpoint 10 giây ở đó nghĩa là màn hình đứng hình 10 giây mỗi 2 phút.
        assertThat(ms).isLessThan(5_000);
    }

    private long bamGio(String duong) {
        long batDau = System.nanoTime();
        ResponseEntity<String> ra = phienHttp.get(kyThuat, duong);
        long ms = (System.nanoTime() - batDau) / 1_000_000;

        assertThat(ra.getStatusCode())
                .as(
                        "⚠ Một endpoint trả lỗi thì trả lời rất NHANH — phép đo thời gian phải kiểm cả "
                                + "mã trạng thái, nếu không nó đo tốc độ của đường lỗi. Thân: %s",
                        ra.getBody())
                .isEqualTo(HttpStatus.OK);
        return ms;
    }
}
