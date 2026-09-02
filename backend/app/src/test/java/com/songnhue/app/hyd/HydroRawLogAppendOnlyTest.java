package com.songnhue.app.hyd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.songnhue.app.testsupport.IntegrationTestBase;
import com.songnhue.hydro.domain.RawFetch;
import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.infra.HydroRawLogWriter;

/**
 * ⭐⭐ {@code hydro_raw_logs} thật sự <b>append-only</b> — T29.10.
 *
 * <h2>Vì sao khai {@code REVOKE} trong tệp SQL chưa phải bằng chứng</h2>
 *
 * <p>{@code V202608131006} đặt {@code ALTER DEFAULT PRIVILEGES} cấp sẵn {@code UPDATE, DELETE} cho
 * {@code songnhue_app} trên <b>mọi bảng tạo sau</b> — và chính tệp đó ghi lời cảnh báo đích danh
 * {@code hydro_raw_logs}. Nghĩa là mặc định của hệ thống đang <i>chống lại</i> tính append-only, và
 * migration phải chủ động giành lại. Quên khối {@code REVOKE} thì <b>không một lệnh nào báo sai</b>:
 * bảng vẫn tạo được, ứng dụng vẫn chạy, mọi bài kiểm khác vẫn xanh. Nó chỉ lộ ra vào ngày ai đó cần
 * chứng minh dữ liệu chưa bị sửa — tức là đúng lúc không còn chứng minh được nữa.
 *
 * <p>Nên bài này ⛔ không đọc lại tệp SQL. Nó chạy <b>bằng chính vai trò {@code songnhue_app}</b>
 * ({@code IntegrationTestBase} đặt đúng vai trò ấy cho kết nối runtime) và đo xem CSDL có thật sự từ
 * chối không.
 *
 * <h2>⚠ Và nó phân biệt được hai trạng thái (luật 9)</h2>
 *
 * <p>Một bài kiểm "UPDATE ném ngoại lệ" sẽ xanh trọn vẹn cả khi kết nối hỏng, bảng không tồn tại,
 * hay câu SQL sai chính tả. Nên ở đây có ba vế đi cùng nhau: {@code INSERT} <b>phải thành công</b>
 * (và bản ghi phải đọc lại được), ba lệnh ghi đè <b>phải bị từ chối</b>, và cùng ba lệnh ấy trên
 * {@code hydro_readings} — bảng cố ý <i>không</i> append-only — <b>phải chạy được</b>. Vế thứ ba là
 * thứ chứng minh phép đo đang đo một khác biệt về quyền, chứ không đo một kết nối hỏng.
 */
class HydroRawLogAppendOnlyTest extends IntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private HydroRawLogWriter rawLogs;

    private long sourceId() {
        return jdbc.queryForObject("SELECT id FROM api_sources WHERE code = 'BHH40'", Long.class);
    }

    /**
     * Ghi một bản ghi raw thật rồi trả về id.
     *
     * <p>⚠ {@code fetchedAt} là <b>bây giờ</b> có chủ đích: bản ghi phải rơi vào partition tháng
     * hiện tại, ⛔ không rơi vào {@code DEFAULT} — nếu không, bài kiểm này sẽ lặng lẽ làm đỏ bài
     * {@code HydroTimeSeriesSchemaTest.nhanhDefaultDiQuaDuoc}, mà nguyên nhân thì nằm ở một tệp khác.
     * ⛔ Và không dọn được: vai trò {@code songnhue_app} không có DELETE trên bảng này — đó chính là
     * thứ đang được kiểm.
     */
    private long ghiMotBanGhi() {
        return rawLogs.write(new RawFetch(
                sourceId(),
                Instant.now(),
                Instant.now().truncatedTo(ChronoUnit.HOURS),
                200,
                318,
                "F01771;01/09/2026;10:20;value=493;<br>",
                null,
                null));
    }

    @Test
    @DisplayName("⭐ Vế thuận: songnhue_app GHI được và ĐỌC LẠI được — không có vế này thì ba vế dưới vô nghĩa")
    void ghiVaDocLaiDuoc() {
        long id = ghiMotBanGhi();

        assertThat(id).isPositive();
        assertThat(jdbc.queryForObject("SELECT body FROM hydro_raw_logs WHERE id = ?", String.class, id))
                .as("nguyên văn phải được giữ NGUYÊN — nguồn không có API lịch sử, đây là bản sao duy nhất")
                .contains("F01771")
                .contains("value=493");
        assertThat(jdbc.queryForObject("SELECT body_bytes FROM hydro_raw_logs WHERE id = ?", Integer.class, id))
                .isPositive();
    }

    @Test
    @DisplayName("⛔⛔ UPDATE / DELETE / TRUNCATE trên hydro_raw_logs bị CSDL từ chối")
    void baLenhGhiDeBiTuChoi() {
        long id = ghiMotBanGhi();

        // ⚠ Khẳng định ở NGUYÊN NHÂN GỐC, không ở thông báo ngoài cùng. Spring dịch mã SQLSTATE
        //   42501 (`permission denied`) thành `BadSqlGrammarException`, và `getMessage()` của lớp
        //   bọc ấy chỉ in lại **câu SQL** — không một chữ nào về quyền. Một bài kiểm đọc thông báo
        //   ngoài cùng sẽ xanh với BẤT KỲ lỗi cú pháp nào, kể cả gõ sai tên bảng: nó không phân biệt
        //   được "bị từ chối" với "câu lệnh sai" (luật 9).
        assertThatThrownBy(() -> jdbc.update("UPDATE hydro_raw_logs SET body = 'sửa lại' WHERE id = ?", id))
                .as("sửa nguyên văn response là bịa lại lịch sử của nguồn")
                .rootCause()
                .hasMessageContaining("permission denied for table hydro_raw_logs");

        assertThatThrownBy(() -> jdbc.update("DELETE FROM hydro_raw_logs WHERE id = ?", id))
                .rootCause()
                .hasMessageContaining("permission denied for table hydro_raw_logs");

        assertThatThrownBy(() -> jdbc.execute("TRUNCATE hydro_raw_logs"))
                .as("TRUNCATE là đường vòng quanh DELETE — thu hồi cả ba mới là thu hồi")
                .rootCause()
                .hasMessageContaining("permission denied for table hydro_raw_logs");

        assertThat(jdbc.queryForObject("SELECT count(*) FROM hydro_raw_logs WHERE id = ?", Integer.class, id))
                .as("và bản ghi vẫn còn nguyên sau cả ba lượt thử")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("⛔⛔ Quyền trên PARTITION không kế thừa — đi thẳng vào partition cũng phải bị chặn")
    void diThangVaoPartitionCungBiChan() {
        // ⚠ Đây là cái bẫy mà `core_create_audit_partition` đã dựng biển báo: PostgreSQL KHÔNG cho
        //   partition kế thừa quyền của bảng cha khi truy vấn thẳng vào partition. Siết ở bảng cha
        //   mà quên partition thì `DELETE FROM hydro_raw_logs_p202609` chạy trót lọt, và
        //   append-only chỉ còn trên giấy.
        ghiMotBanGhi();
        List<String> partitions = jdbc.queryForList(
                """
                SELECT c.relname FROM pg_class c
                  JOIN pg_namespace n ON n.oid = c.relnamespace
                 WHERE n.nspname = 'public' AND c.relname ~ '^hydro_raw_logs_p[0-9]{6}$'
                 ORDER BY c.relname
                """,
                String.class);

        assertThat(partitions)
                .as("⛔ vế chống xanh-trên-tập-rỗng: không có partition nào thì vòng lặp dưới không kiểm gì")
                .hasSize(12);

        for (String partition : partitions) {
            assertThatThrownBy(() -> jdbc.update("UPDATE " + partition + " SET body = 'x'"))
                    .as("partition %s vẫn cho UPDATE — quyền không được siết lúc tạo", partition)
                    .rootCause()
                    .hasMessageContaining("permission denied for table " + partition);
            assertThatThrownBy(() -> jdbc.update("DELETE FROM " + partition))
                    .as("partition %s vẫn cho DELETE", partition)
                    .rootCause()
                    .hasMessageContaining("permission denied for table " + partition);
        }

        assertThatThrownBy(() -> jdbc.update("UPDATE hydro_raw_logs_default SET body = 'x'"))
                .as("partition DEFAULT cũng là một partition — nó hay bị quên nhất vì migration tạo nó riêng")
                .rootCause()
                .hasMessageContaining("permission denied for table hydro_raw_logs_default");
    }

    @Test
    @DisplayName("⭐ Vế đối chứng: cùng ba lệnh ấy trên hydro_readings PHẢI chạy được")
    void bangKhongAppendOnlyThiVanGhiDeDuoc() {
        // Luật 9: một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì. Nếu
        // kết nối hỏng hay vai trò sai, MỌI câu lệnh đều ném — và ba bài trên vẫn xanh trọn vẹn.
        // Vế này chứng minh phép đo đang đo một khác biệt VỀ QUYỀN.
        //
        // ⚠ Và nó nói ra một quyết định thiết kế: `hydro_readings` CỐ Ý không append-only, vì bản
        //   ghi NGHI_NGO được duyệt lên HOP_LE ở WS-32 (qua Workflow engine, ⛔ không phải bằng một
        //   câu UPDATE tuỳ tiện). Không siết ở đó là chủ đích, không phải chỗ quên.
        long stationId = jdbc.queryForObject("SELECT id FROM stations WHERE api_code = 'F01771'", Long.class);
        long typeId = jdbc.queryForObject("SELECT id FROM measurement_types WHERE code = 'MUC_NUOC'", Long.class);
        Instant moc = Instant.parse("2049-01-02T03:00:00Z");

        assertThatCode(() -> {
                    jdbc.update(
                            """
                            INSERT INTO hydro_readings (measured_at, station_id, measurement_type_id,
                                                        reading_value, quality, quality_reason, source)
                            VALUES (?, ?, ?, 1.234, 'NGHI_NGO', 'kiểm quyền ghi đè', 'API')
                            """,
                            java.sql.Timestamp.from(moc),
                            stationId,
                            typeId);
                    // ⚠ `quality_reason` bắt buộc với dòng NGHI_NGO từ `V202609021054`
                    //   (`ck_hydro_readings_nghi_ngo_co_ly_do`): một cờ đỏ không nói được vì sao là
                    //   một cờ đỏ không hành động được. Bài này ⛔ không đo ràng buộc ấy — nó đo
                    //   QUYỀN ghi đè — nên chỉ cần điền cho hợp lệ.
                    jdbc.update(
                            "UPDATE hydro_readings SET quality = 'HOP_LE' WHERE station_id = ? AND measured_at = ?",
                            stationId,
                            java.sql.Timestamp.from(moc));
                    jdbc.update(
                            "DELETE FROM hydro_readings WHERE station_id = ? AND measured_at = ?",
                            stationId,
                            java.sql.Timestamp.from(moc));
                })
                .as("vai trò songnhue_app PHẢI ghi đè được hydro_readings — nếu vế này cũng đỏ thì ba bài "
                        + "trên đang đo một kết nối hỏng chứ không đo quyền")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("⭐ Lượt gọi HỎNG cũng phải để lại dấu vết — đó mới là lượt cần nhất")
    void luotGoiHongVanGhiDuoc() {
        // §10.68-B: bốn nguyên nhân cần bốn cách xử lý ngược nhau, nên chúng phải phân biệt được
        // ngay trên dòng dữ liệu, không phải trong đầu người đọc log.
        long id = rawLogs.write(new RawFetch(
                sourceId(),
                Instant.now(),
                null,
                200,
                412,
                "not.working",
                SyncFailureKind.NOT_WORKING,
                "Nguồn trả not.working — sai mã số hoặc THIẾU DẤU ; ở cuối mã số"));

        var dong = jdbc.queryForMap("SELECT failure_kind, failure_detail, body FROM hydro_raw_logs WHERE id = ?", id);
        assertThat(dong.get("failure_kind")).isEqualTo("NOT_WORKING");
        assertThat((String) dong.get("failure_detail")).contains("THIẾU DẤU ;");
        assertThat(dong.get("body"))
                .as("⛔ thân phản hồi hỏng vẫn phải giữ nguyên văn — nó là thứ duy nhất trả lời 'vì sao hỏng'")
                .isEqualTo("not.working");
    }
}
