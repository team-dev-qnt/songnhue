package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.hydro.domain.ReadingQuality;
import com.songnhue.hydro.domain.ReadingRow;
import com.songnhue.hydro.domain.ReadingSource;
import com.songnhue.hydro.domain.UnmappedRow;
import com.songnhue.hydro.infra.HydroMaintenanceRepository;
import com.songnhue.hydro.infra.HydroTimeSeriesWriter;

/**
 * Lược đồ time-series WS-29 — <b>bốn thứ chỉ một CSDL thật mới kiểm chứng được</b>.
 *
 * <p>Bảng phân mảnh, ràng buộc chống trùng, {@code ON CONFLICT DO NOTHING} và câu UPSERT bốn cột đều
 * là hành vi của PostgreSQL, ⛔ không phải hành vi của mã Java. Một bài kiểm mock ở đây chỉ chứng
 * minh rằng mock đã được gọi (luật 4 — {@code BackupServiceTest} mock {@code PostgresToolRunner} và
 * {@code pg_dump} chưa từng chạy suốt bốn ngày).
 *
 * <h2>Bốn câu hỏi bài này trả lời</h2>
 *
 * <ol>
 *   <li>Runway partition có thật không, và nhánh {@code DEFAULT} có <b>đi qua được</b> không — một
 *       lưới an toàn chưa ai rơi vào thì chưa biết nó đỡ được hay không (luật 7).
 *   <li>Poll 2' trên nguồn 10' trả trùng — {@code ON CONFLICT} có <b>đếm đúng</b> số dòng ghi mới
 *       không. Đây là con số phân biệt "poller khoẻ, dữ liệu trùng" với "poller chết".
 *   <li>{@code hydro_latest} có <b>không lùi</b> khi nhận bản ghi cũ hơn không.
 *   <li>⭐ Bản ghi {@code NGHI_NGO} có <b>đẩy được</b> giá trị nghi ngờ vào cột hiển thị không —
 *       đây là chỗ quy tắc 14 được ép ở tầng dữ liệu, và là lý do bảng có bốn cột chứ không hai.
 * </ol>
 */
class HydroTimeSeriesSchemaTest extends IntegrationTestBase {

    /** Cống Liên Mạc — Thượng lưu. Mã thật, seed từ bảng ánh xạ G8b. */
    private static final String MA_API = "F01771";

    /** ⚠ Xa mọi runway partition (migration tạo 12 tháng) ⇒ chắc chắn rơi vào {@code DEFAULT}. */
    private static final Instant NGOAI_RUNWAY = Instant.parse("2050-06-15T10:20:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HydroTimeSeriesWriter writer;

    @Autowired
    private HydroMaintenanceRepository maintenance;

    private long stationId;
    private long typeId;
    private long sourceId;

    private long stationId() {
        if (stationId == 0) {
            stationId = jdbc.queryForObject("SELECT id FROM stations WHERE api_code = ?", Long.class, MA_API);
        }
        return stationId;
    }

    private long typeId() {
        if (typeId == 0) {
            typeId = jdbc.queryForObject("SELECT id FROM measurement_types WHERE code = 'MUC_NUOC'", Long.class);
        }
        return typeId;
    }

    private long sourceId() {
        if (sourceId == 0) {
            sourceId = jdbc.queryForObject("SELECT id FROM api_sources WHERE code = 'BHH40'", Long.class);
        }
        return sourceId;
    }

    /** Mười hai tên partition mong đợi — hỏi chính CSDL, xem ghi chú ở {@link #runwayVaPartitionDefault}. */
    private List<String> runwayMongDoi(String bang) {
        return jdbc.queryForList(
                """
                SELECT ? || '_p' || to_char(current_date + (g || ' month')::interval, 'YYYYMM')
                  FROM generate_series(0, 11) AS g ORDER BY 1
                """,
                String.class,
                bang);
    }

    @AfterEach
    void don() {
        // ⚠ Dọn có phạm vi hẹp: context Spring dùng chung cho mọi bài kiểm tích hợp, nên một câu
        //   DELETE không điều kiện ở đây sẽ xoá dữ liệu của bài khác mà không ai thấy.
        jdbc.update("DELETE FROM hydro_readings WHERE station_id = ?", stationId());
        jdbc.update("DELETE FROM hydro_latest WHERE station_id = ?", stationId());
        jdbc.update("DELETE FROM hydro_unmapped_readings WHERE api_code LIKE 'Z%'");
    }

    @Test
    @DisplayName("⭐ Migration tạo runway 12 tháng cho CẢ HAI bảng, kèm partition DEFAULT")
    void runwayVaPartitionDefault() {
        // ⚠ Khẳng định TÊN THÁNG chứ không chỉ số lượng: `hasSize(12)` vẫn xanh khi vòng lặp runway
        //   lệch một tháng — mà lệch một tháng nghĩa là tháng hiện tại không có partition, tức mọi
        //   bản ghi hôm nay rơi vào DEFAULT. Đếm đúng số lượng và sai tháng là dạng xanh giả rẻ nhất.
        //   Danh sách mong đợi hỏi CHÍNH CSDL, vì hàm tạo partition dùng `current_date` của phiên
        //   CSDL — tính lại phía Java là dựng một công thức thứ hai cho cùng một sự thật.
        assertThat(maintenance.partitionNames("hydro_raw_logs"))
                .as("migration gọi hyd_ensure_time_series_partitions(11) ⇒ tháng hiện tại + 11 tháng tới")
                .containsExactlyElementsOf(runwayMongDoi("hydro_raw_logs"));
        assertThat(maintenance.partitionNames("hydro_readings"))
                .containsExactlyElementsOf(runwayMongDoi("hydro_readings"));

        // Partition DEFAULT là lưới an toàn: thà ghi chậm còn hơn INSERT lỗi làm hỏng giao dịch
        // ingest — mà giao dịch ingest hỏng ở MOD-03 là mất dữ liệu vĩnh viễn.
        assertThat(jdbc.queryForObject(
                        """
                        SELECT count(*) FROM pg_class c
                          JOIN pg_inherits i ON i.inhrelid = c.oid
                         WHERE c.relname IN ('hydro_raw_logs_default', 'hydro_readings_default')
                           AND pg_get_expr(c.relpartbound, c.oid) = 'DEFAULT'
                        """,
                        Integer.class))
                .as("cả hai bảng phải có partition DEFAULT và nó phải THẬT SỰ là DEFAULT")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⭐ Job bảo trì partition chạy được bằng vai trò songnhue_app và idempotent")
    void baoTriPartitionIdempotent() {
        // ⛔ Đây là phép kiểm cho `SECURITY DEFINER`: `songnhue_app` KHÔNG có quyền CREATE trên
        //   schema public. Thiếu từ khoá ấy thì hàm chạy được lúc migrate (Flyway dùng
        //   songnhue_owner) và đỏ ở production sau 12 tháng — đúng lúc không ai còn nhớ vì sao.
        assertThat(maintenance.ensurePartitions(6))
                .as("runway 12 tháng đã có sẵn ⇒ không tạo thêm gì; 0 là ĐÃ ĐỦ, không phải lỗi")
                .isZero();
    }

    @Test
    @DisplayName("⭐ Nhánh partition DEFAULT ĐI QUA ĐƯỢC — lưới an toàn chưa ai rơi vào là lưới chưa biết đỡ được không")
    void nhanhDefaultDiQuaDuoc() {
        assertThat(maintenance.countInDefaultPartition("hydro_readings"))
                .as("trạng thái nền phải sạch, nếu không hai khẳng định dưới không nói lên điều gì")
                .isZero();

        int ghi = writer.writeReadings(List.of(new ReadingRow(
                stationId(),
                typeId(),
                NGOAI_RUNWAY,
                new BigDecimal("4.930"),
                ReadingQuality.HOP_LE,
                ReadingSource.API,
                null)));

        assertThat(ghi).as("ghi được — ⛔ không được ném lỗi chỉ vì hết runway").isEqualTo(1);
        assertThat(maintenance.countInDefaultPartition("hydro_readings"))
                .as("và bản ghi phải nằm ở DEFAULT — đó chính là tín hiệu job bảo trì đã chết")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⭐ Poll 2' trên nguồn 10' trả trùng — ON CONFLICT đếm ĐÚNG số dòng ghi mới")
    void trungKhoaThiBoQuaVaDemDung() {
        Instant khung = Instant.now().truncatedTo(ChronoUnit.HOURS);
        List<ReadingRow> lo = List.of(
                new ReadingRow(
                        stationId(),
                        typeId(),
                        khung,
                        new BigDecimal("4.930"),
                        ReadingQuality.HOP_LE,
                        ReadingSource.API,
                        null),
                new ReadingRow(
                        stationId(),
                        typeId(),
                        khung.plus(10, ChronoUnit.MINUTES),
                        new BigDecimal("4.940"),
                        ReadingQuality.HOP_LE,
                        ReadingSource.API,
                        null));

        assertThat(writer.writeReadings(lo)).as("lượt đầu ghi mới cả hai dòng").isEqualTo(2);
        assertThat(writer.writeReadings(lo))
                .as(
                        """
                        Lượt thứ hai phải ghi 0 dòng và ⛔ KHÔNG ném lỗi. Đây là hành vi của 4/5 lượt \
                        polling: nguồn cập nhật 10 phút/lần, ta gọi 2 phút/lần. Con số này là thứ phân \
                        biệt "poller khoẻ, dữ liệu trùng" với "poller chết" — gộp nó vào một cột chung \
                        là xoá mất phân biệt ấy.""")
                .isZero();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hydro_readings WHERE station_id = ?", Integer.class, stationId()))
                .as("và CSDL vẫn đúng hai dòng — không nhân bản, không mất")
                .isEqualTo(2);
    }

    @Test
    @DisplayName("⭐⭐ hydro_latest: giá trị NGHI_NGO KHÔNG chạm được vào cột hiển thị (quy tắc 14)")
    void giaTriNghiNgoKhongVaoCotHienThi() {
        Instant t0 = Instant.now().truncatedTo(ChronoUnit.MINUTES).minus(20, ChronoUnit.MINUTES);
        Instant t1 = t0.plus(10, ChronoUnit.MINUTES);

        ReadingRow hopLe = new ReadingRow(
                stationId(), typeId(), t0, new BigDecimal("4.930"), ReadingQuality.HOP_LE, ReadingSource.API, null);
        ReadingRow nghiNgo = new ReadingRow(
                stationId(), typeId(), t1, new BigDecimal("99.900"), ReadingQuality.NGHI_NGO, ReadingSource.API, null);

        writer.upsertLatest(List.of(hopLe));
        writer.upsertLatest(List.of(nghiNgo));

        var dong = jdbc.queryForMap(
                """
                SELECT last_seen_at, last_quality, valid_measured_at, valid_value
                  FROM hydro_latest WHERE station_id = ? AND measurement_type_id = ?
                """,
                stationId(),
                typeId());

        assertThat(((java.sql.Timestamp) dong.get("last_seen_at")).toInstant())
                .as("trạm VẪN đang phát tín hiệu — một trạm chỉ trả số nghi ngờ không phải trạm mất tín hiệu")
                .isEqualTo(t1);
        assertThat(dong.get("last_quality"))
                .as("và màn hình phải nói được rằng số mới nhất đang bị nghi ngờ")
                .isEqualTo("NGHI_NGO");

        assertThat(((java.sql.Timestamp) dong.get("valid_measured_at")).toInstant())
                .as("cột hiển thị vẫn đứng ở bản ghi HỢP LỆ gần nhất")
                .isEqualTo(t0);
        assertThat((BigDecimal) dong.get("valid_value"))
                .as(
                        """
                        ⛔ 99.900 là giá trị NGHI_NGO — nó KHÔNG được xuất hiện ở cột mà widget cổng và \
                        lớp GIS đọc. Đây là quy tắc 14 ép ở tầng dữ liệu: bảo đảm đặt ở chỗ dữ liệu đi \
                        qua chứ không ở nơi gọi, nên một widget đọc `valid_value` không có CÁCH NÀO hiện \
                        nhầm, kể cả khi người viết nó chưa từng đọc quy tắc 14.""")
                .isEqualByComparingTo("4.930");
    }

    @Test
    @DisplayName("⭐ hydro_latest KHÔNG LÙI khi nhận bản ghi cũ hơn")
    void latestKhongLui() {
        Instant moi = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        Instant cu = moi.minus(30, ChronoUnit.MINUTES);

        writer.upsertLatest(List.of(new ReadingRow(
                stationId(), typeId(), moi, new BigDecimal("4.930"), ReadingQuality.HOP_LE, ReadingSource.API, null)));
        // Lượt ingest muộn (thử lại sau lỗi mạng, hoặc nhập tay bù quá khứ) mang về bản ghi CŨ HƠN.
        writer.upsertLatest(List.of(new ReadingRow(
                stationId(), typeId(), cu, new BigDecimal("1.110"), ReadingQuality.HOP_LE, ReadingSource.API, null)));

        var dong = jdbc.queryForMap(
                "SELECT last_seen_at, valid_value FROM hydro_latest WHERE station_id = ?", stationId());

        assertThat(((java.sql.Timestamp) dong.get("last_seen_at")).toInstant())
                .as("mốc hiện tại ⛔ không được lùi")
                .isEqualTo(moi);
        assertThat((BigDecimal) dong.get("valid_value"))
                .as("và giá trị hiển thị cũng không")
                .isEqualByComparingTo("4.930");
    }

    @Test
    @DisplayName("⛔ CSDL từ chối dòng hydro_latest có nửa cặp (mốc hợp lệ mà không có giá trị)")
    void csdlTuChoiNuaCap() {
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO hydro_latest (station_id, measurement_type_id, last_seen_at,
                                                  last_quality, last_source, valid_measured_at, valid_value)
                        VALUES (?, ?, now(), 'HOP_LE', 'API', now(), NULL)
                        """,
                        stationId(),
                        typeId()))
                .as("có mốc mà không có số là một trạng thái vô nghĩa — bảng này do UPSERT ghi, và "
                        + "UPSERT viết sai thì không có lượt review nào nhìn thấy")
                .hasMessageContaining("ck_hydro_latest_cap_hop_le");
    }

    @Test
    @DisplayName("⭐ Mã nguồn chưa khai: giữ lại nguyên trạng, chống trùng theo (mã, khung)")
    void maChuaKhaiDuocGiuLai() {
        Instant khung = Instant.now().truncatedTo(ChronoUnit.HOURS);
        // ⚠ Dùng mã 'Z…' chứ không dùng một trong 9 mã lạ thật: bài kiểm không được phụ thuộc vào
        //   việc Công ty CHƯA trả lời G8. Ngày họ khai F01613 thành điểm đo, bài này vẫn phải xanh.
        List<UnmappedRow> lo = List.of(
                new UnmappedRow("Z99001", sourceId(), khung, new BigDecimal("213.000"), "cm", null),
                new UnmappedRow("Z99002", sourceId(), khung, new BigDecimal("216.000"), "cm", null));

        assertThat(writer.writeUnmapped(lo)).isEqualTo(2);
        assertThat(writer.writeUnmapped(lo)).as("gọi lại không nhân bản").isZero();

        assertThat(jdbc.queryForObject(
                        "SELECT raw_unit FROM hydro_unmapped_readings WHERE api_code = 'Z99001'", String.class))
                .as("đơn vị NGUỒN phải đi kèm — chưa biết loại chỉ số thì 213 không có nghĩa gì nếu thiếu 'cm'")
                .isEqualTo("cm");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hydro_readings WHERE station_id IS NOT NULL AND measured_at = ?",
                        Integer.class,
                        java.sql.Timestamp.from(khung)))
                .as("⛔ và TUYỆT ĐỐI không được tự tạo điểm đo/số đo từ mã lạ — bản suy đoán trước đó sai 1/4 mã")
                .isZero();
    }

    @Test
    @DisplayName("⛔⛔ ck_hydro_readings_nguoi_nhap ép CẢ HAI vế nó tự khai — cả hai đều phải đỏ")
    void nguoiChiuTrachNhiemEpCaHaiVe() {
        // ⚠ Bản đầu viết `(source = 'MANUAL') OR (created_by IS NULL AND note IS NULL)` và chỉ ép
        //   được NỬA SAU: với source='MANUAL' thì vế trái đã TRUE nên created_by NULL đi lọt. Một
        //   ràng buộc khai HAI bảo đảm ngay trên đầu nó mà chỉ thi hành MỘT thì nguy hiểm hơn một
        //   ràng buộc vắng mặt — dòng chú thích làm người đọc tin là đã có.
        //
        //   Nên bài này khẳng định CẢ HAI chiều. Chỉ kiểm một chiều thì bản cũ vẫn xanh.
        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id,
                                                    reading_value, quality, source, created_by)
                        VALUES ('2049-02-01T00:00:00Z', ?, ?, 1.234, 'HOP_LE', 'MANUAL', NULL)
                        """,
                        stationId(),
                        typeId()))
                .as("vế 1 — nhập tay PHẢI có người chịu trách nhiệm (đây là vế bản cũ để lọt)")
                .rootCause()
                .hasMessageContaining("ck_hydro_readings_nguoi_nhap");

        assertThatThrownBy(() -> jdbc.update(
                        """
                        INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id,
                                                    reading_value, quality, source, created_by)
                        VALUES ('2049-02-02T00:00:00Z', ?, ?, 1.234, 'HOP_LE', 'API', 7)
                        """,
                        stationId(),
                        typeId()))
                .as("vế 2 — máy ghi thì ⛔ không được mượn tên ai (quy tắc 18: bịa một chữ ký)")
                .rootCause()
                .hasMessageContaining("ck_hydro_readings_nguoi_nhap");

        // Và vế THUẬN: dòng nhập tay hợp lệ phải ghi được — nếu không, ràng buộc mới đã chặn luôn
        // cả đường đúng, và hai khẳng định trên sẽ xanh vì lý do sai (luật 9).
        jdbc.update(
                """
                INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id,
                                            reading_value, quality, source, created_by, note)
                VALUES ('2049-02-03T00:00:00Z', ?, ?, 1.234, 'HOP_LE', 'MANUAL', 7, 'bù dữ liệu mất tín hiệu')
                """,
                stationId(),
                typeId());
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM hydro_readings WHERE station_id = ? AND source = 'MANUAL'",
                        Integer.class,
                        stationId()))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔ ReadingRow từ chối source=MANUAL — kiểu này không có chỗ mang người chịu trách nhiệm")
    void readingRowTuChoiNhapTay() {
        // Ràng buộc CSDL đòi mọi dòng MANUAL có `created_by`, mà ReadingRow không có trường ấy ⇒ một
        // ReadingRow(… MANUAL …) đi tới CSDL là chắc chắn vỡ, vỡ ở giữa một lượt ingest và cách chỗ
        // viết sai rất xa. Chặn ở hàm dựng để lỗi rơi đúng dòng mã sai.
        assertThatThrownBy(() -> new ReadingRow(
                        stationId(),
                        typeId(),
                        Instant.now(),
                        new BigDecimal("1.000"),
                        ReadingQuality.HOP_LE,
                        ReadingSource.MANUAL,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WS-32");
    }

    @Test
    @DisplayName("⛔ Đơn vị rỗng bị chặn ở HÀM DỰNG, không đợi tới CSDL")
    void donViRongBiChanSom() {
        assertThatThrownBy(() -> new UnmappedRow("Z99003", sourceId(), Instant.now(), BigDecimal.ONE, " ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rawUnit");
    }
}
