package com.songnhue.hydro.infra;

import java.sql.Timestamp;
import java.time.Duration;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.songnhue.hydro.domain.SyncFailureKind;
import com.songnhue.hydro.domain.SyncOutcome;

/**
 * Ghi một dòng {@code sync_logs} cho mỗi lượt polling — nguồn của màn hình <i>Nhật ký đồng bộ</i>
 * (M3.16).
 *
 * <p>⭐ <b>Ghi kể cả khi lượt gọi bị nguồn từ chối</b> (T31.12). Đây là điểm dễ bỏ sót nhất: khi mọi
 * thứ hỏng thì phản xạ tự nhiên là ném ngoại lệ lên và để worker ghi nhận — nhưng worker chỉ ghi
 * "job này thất bại", nó không biết vì sao và không hiện ở màn hình của người vận hành thuỷ văn. Một
 * chuỗi lượt {@code FAILED} kèm {@link SyncFailureKind} là <b>chính là</b> dữ liệu chẩn đoán, và
 * §10.68-C đã cho thấy điều gì xảy ra khi cơ chế bảo vệ và cơ chế tự động hoá đứng cạnh nhau mà
 * không ai đối chiếu: lượt deploy tự cấm chính nó, và không có dòng nào giải thích.
 */
@Repository
public class SyncLogWriter {

    private static final String SQL =
            """
            INSERT INTO sync_logs (
                api_source_id, started_at, finished_at, duration_ms, frame_start,
                status, failure_kind, failure_detail,
                received_count, written_count, skipped_count, unmapped_count, raw_log_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id
            """;

    private final JdbcTemplate jdbc;

    public SyncLogWriter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** @return {@code sync_logs.id} vừa ghi */
    public long write(SyncOutcome outcome) {
        SyncFailureKind kind = outcome.failureKind();
        Integer durationMs = outcome.finishedAt() == null
                ? null
                : (int) Duration.between(outcome.startedAt(), outcome.finishedAt())
                        .toMillis();

        Long id = jdbc.queryForObject(
                SQL,
                Long.class,
                outcome.apiSourceId(),
                Timestamp.from(outcome.startedAt()),
                outcome.finishedAt() == null ? null : Timestamp.from(outcome.finishedAt()),
                durationMs,
                outcome.frameStart() == null ? null : Timestamp.from(outcome.frameStart()),
                outcome.status().name(),
                kind == null ? null : kind.name(),
                outcome.failureDetail(),
                outcome.receivedCount(),
                outcome.writtenCount(),
                outcome.skippedCount(),
                outcome.unmappedCount(),
                outcome.rawLogId());
        if (id == null) {
            throw new IllegalStateException("INSERT sync_logs không trả về id");
        }
        return id;
    }
}
