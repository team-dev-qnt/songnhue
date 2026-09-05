package com.songnhue.hydro.infra;

import java.sql.Timestamp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.RawFetch;
import com.songnhue.hydro.domain.SyncFailureKind;

/**
 * Ghi <b>nguyên văn</b> response của nguồn vào {@code hydro_raw_logs} — bước đầu tiên của mọi lượt
 * ingest, ⛔ <b>trước khi</b> parse.
 *
 * <h2>Vì sao lớp này dùng JDBC chứ không JPA</h2>
 *
 * <p>{@code hydro_raw_logs} là bảng <b>append-only</b>: vai trò {@code songnhue_app} không có
 * {@code UPDATE}/{@code DELETE}/{@code TRUNCATE} (T29.2). Một entity JPA ghi được sẽ biên dịch trót
 * lọt rồi đổ vỡ lúc chạy với một lỗi quyền khó hiểu, thay vì không viết được ngay từ đầu — đúng lý
 * do {@code AuditLogWriter} đã chọn JDBC ba tháng trước.
 *
 * <h2>⛔ Ba điều lớp này KHÔNG làm, và đều là chủ ý</h2>
 *
 * <ol>
 *   <li>⛔ <b>Không mở giao dịch.</b> Ranh giới transaction thuộc tầng application (ArchUnit canh).
 *       Nơi gọi phải bọc lượt ghi này trong một {@code TransactionTemplate} <b>{@code REQUIRES_NEW}</b>
 *       để nó sống sót khi phần parse phía sau rollback — nếu không thì đúng những response
 *       <i>hỏng</i>, tức những response cần nhất, là những response không bao giờ được lưu.
 *   <li>⛔ <b>Không log thân phản hồi.</b> Mã số nguồn nằm trong URL và có thể nằm trong thông báo
 *       lỗi của thư viện HTTP; conventions.md §4.7 cấm nó xuất hiện ở log, ở API, ở bản export.
 *   <li>⛔ <b>Không cắt {@code body}.</b> Nguồn trả 28 dòng số đo rồi một trang HTML rỗng ở đuôi;
 *       phần đuôi ấy trông như rác cho tới ngày nguồn đổi định dạng, và khi ấy nó là bằng chứng duy
 *       nhất. Cột là {@code TEXT}, không có trần.
 * </ol>
 */
@Repository
public class HydroRawLogWriter {

    private static final String SQL =
            """
            INSERT INTO hydro_raw_logs (
                fetched_at, api_source_id, frame_start, http_status, duration_ms,
                body, body_bytes, failure_kind, failure_detail
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public HydroRawLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @return {@code hydro_raw_logs.id} vừa sinh — số đo parse ra từ response này mang nó ở cột
     *     {@code raw_log_id} để truy ngược được về nguyên văn
     */
    public long write(RawFetch fetch) {
        SyncFailureKind kind = fetch.failureKind();
        Long id = jdbc.queryForObject(
                SQL,
                Long.class,
                Timestamp.from(fetch.fetchedAt()),
                fetch.apiSourceId(),
                fetch.frameStart() == null ? null : Timestamp.from(fetch.frameStart()),
                fetch.httpStatus(),
                fetch.durationMs(),
                fetch.body(),
                fetch.soByte(),
                kind == null ? null : kind.name(),
                fetch.failureDetail());
        if (id == null) {
            // Không thể xảy ra với RETURNING id, nhưng im lặng trả 0 thì mọi bản ghi số đo sau đó
            // mang một `raw_log_id` trỏ vào hư không — và nó chỉ lộ ra lúc có người đi truy ngược.
            throw new IllegalStateException("INSERT hydro_raw_logs không trả về id");
        }
        return id;
    }
}
