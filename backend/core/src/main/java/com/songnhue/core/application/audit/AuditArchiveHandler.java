package com.songnhue.core.application.audit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.songnhue.core.application.job.JobContext;
import com.songnhue.core.application.job.JobHandler;
import com.songnhue.core.application.job.JobTypes;
import com.songnhue.core.application.settings.SettingService;
import com.songnhue.core.common.config.StorageProperties;
import com.songnhue.core.common.error.ErrorCode;
import com.songnhue.core.common.exception.BusinessRuleException;
import com.songnhue.core.common.util.HashUtils;
import com.songnhue.core.infra.audit.ArchiverJdbc;
import com.songnhue.core.infra.storage.ObjectStorage;

/**
 * Kết xuất nhật ký kiểm toán quá hạn lưu trữ rồi mới xoá — T6.13 (chốt G7).
 *
 * <p>Khách chốt giữ nhật ký <b>5 năm</b> rồi kết xuất ra lưu trữ. Việc này xoá dữ liệu không phục
 * hồi được, nên thứ tự các bước là toàn bộ giá trị của lớp này:
 *
 * <ol>
 *   <li>Đọc các dòng quá hạn, ghi ra CSV nén.
 *   <li>Tải lên bucket lưu trữ riêng (có bật versioning từ WS-3).
 *   <li><b>Tải ngược về và đối chiếu checksum SHA-256.</b> Không phải nghi thức: mạng đứt giữa
 *       chừng, đĩa đầy, hay ghi đè nhầm object đều cho ra một tệp <i>tồn tại</i> nhưng không dùng
 *       được — mà lúc đó dữ liệu gốc đã bị xoá.
 *   <li>Ghi điểm neo, đánh dấu {@code verified_at}.
 *   <li><b>Chỉ khi đó</b> mới xoá, và xoá bằng vai trò {@code songnhue_archiver}.
 * </ol>
 *
 * <p>Bất kỳ bước nào hỏng → <b>không xoá dòng nào</b> và báo {@code ADM-2001}. Ràng buộc
 * {@code ck_audit_archive_anchors_purge_order} trong DB chốt lại điều đó một lần nữa ở tầng dữ liệu:
 * không có {@code verified_at} thì không được đặt {@code purged_at}.
 *
 * <p>Điểm neo giữ {@code last_hash} của lô — nhờ đó chuỗi hash <b>vẫn kiểm tra được</b> sau khi các
 * dòng cũ biến mất. Không có nó thì lần kết xuất đầu tiên sẽ làm mọi lượt verify về sau báo gãy.
 */
@Component
public class AuditArchiveHandler implements JobHandler {

    private static final Logger log = LoggerFactory.getLogger(AuditArchiveHandler.class);

    private static final String KEY_RETENTION_YEARS = "audit.retention-years";
    private static final String KEY_ARCHIVE_ENABLED = "audit.archive-enabled";
    private static final int DEFAULT_RETENTION_YEARS = 5;

    /** Số dòng tối đa mỗi lô. Lô quá lớn thì tệp nén không lọt vào bộ nhớ của tiến trình. */
    private static final int BATCH_LIMIT = 100_000;

    private final ObjectProvider<ArchiverJdbc> archiverJdbc;
    private final ObjectStorage storage;
    private final StorageProperties storageProperties;
    private final SettingService settings;

    public AuditArchiveHandler(
            ObjectProvider<ArchiverJdbc> archiverJdbc,
            ObjectStorage storage,
            StorageProperties storageProperties,
            SettingService settings) {
        this.archiverJdbc = archiverJdbc;
        this.storage = storage;
        this.storageProperties = storageProperties;
        this.settings = settings;
    }

    @Override
    public String jobType() {
        return JobTypes.AUDIT_ARCHIVE;
    }

    @Override
    public short maxAttempts() {
        // Thử lại nhiều lần cho một việc xoá dữ liệu là mời gọi rắc rối. Hỏng thì để người vận hành
        // xem log rồi quyết định — đây không phải loại việc nên tự chạy lại trong đêm.
        return 1;
    }

    @Override
    public void handle(JobContext context) throws IOException {
        if (!settings.getBoolean(KEY_ARCHIVE_ENABLED, true)) {
            log.info("Kết xuất nhật ký đang tắt theo cấu hình — bỏ qua");
            return;
        }
        ArchiverJdbc archiver = archiverJdbc.getIfAvailable();
        if (archiver == null) {
            throw new BusinessRuleException(
                    ErrorCode.ADM_2001, "chưa cấu hình DB_ARCHIVER_PASSWORD — không có quyền xoá nhật ký");
        }

        org.springframework.jdbc.core.JdbcTemplate jdbc = archiver.jdbc();

        int years = settings.getInt(KEY_RETENTION_YEARS, DEFAULT_RETENTION_YEARS);
        Instant cutoff = Instant.now().minus(years * 365L, ChronoUnit.DAYS);

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM audit_logs WHERE occurred_at < ? ORDER BY seq LIMIT ?", cutoff, BATCH_LIMIT);
        if (rows.isEmpty()) {
            log.info("Không có bản ghi nhật ký nào quá {} năm — không phải kết xuất", years);
            return;
        }

        long fromSeq = ((Number) rows.get(0).get("seq")).longValue();
        Map<String, Object> lastRow = rows.get(rows.size() - 1);
        long toSeq = ((Number) lastRow.get("seq")).longValue();
        String lastHash = (String) lastRow.get("hash");

        byte[] archive = toGzippedCsv(rows);
        String checksum = HashUtils.sha256Hex(archive);
        String bucket = storageProperties.getBucketAudit();
        String objectKey = "audit/%d-%d.csv.gz".formatted(fromSeq, toSeq);

        storage.put(bucket, objectKey, archive, "application/gzip");

        // Đọc ngược về từ kho, không tin vào việc "ghi không báo lỗi tức là ghi thành công".
        String storedChecksum = HashUtils.sha256Hex(storage.get(bucket, objectKey));
        if (!checksum.equals(storedChecksum)) {
            throw new BusinessRuleException(
                    ErrorCode.ADM_2001, "checksum bản kết xuất không khớp — KHÔNG xoá bản ghi nào");
        }

        jdbc.update(
                """
                INSERT INTO audit_archive_anchors (
                    from_seq, to_seq, from_occurred_at, to_occurred_at, row_count, last_hash,
                    storage_bucket, storage_key, file_size_bytes, checksum_sha256, verified_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                fromSeq,
                toSeq,
                rows.get(0).get("occurred_at"),
                lastRow.get("occurred_at"),
                rows.size(),
                lastHash,
                bucket,
                objectKey,
                archive.length,
                checksum);

        int deleted = jdbc.update("DELETE FROM audit_logs WHERE seq BETWEEN ? AND ?", fromSeq, toSeq);
        jdbc.update(
                "UPDATE audit_archive_anchors SET purged_at = now() WHERE from_seq = ? AND to_seq = ?", fromSeq, toSeq);

        log.info(
                "Kết xuất nhật ký seq {}–{}: {} dòng → {}/{} ({} byte, checksum khớp), đã xoá {} dòng",
                fromSeq,
                toSeq,
                rows.size(),
                bucket,
                objectKey,
                archive.length,
                deleted);
    }

    /**
     * CSV nén gzip.
     *
     * <p>Chọn CSV thay vì Parquet (kế hoạch nêu cả hai): bản lưu trữ này để <b>người</b> mở khi cần
     * tra một sự việc nhiều năm trước, không phải để chạy phân tích. CSV mở được bằng bất cứ thứ gì,
     * kể cả sau khi dự án đã đổi tay nhiều lần — đó mới là thứ quan trọng với dữ liệu lưu 5 năm.
     */
    private static byte[] toGzippedCsv(List<Map<String, Object>> rows) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(buffer);
                Writer writer = new OutputStreamWriter(gzip, StandardCharsets.UTF_8)) {

            List<String> columns = List.copyOf(rows.get(0).keySet());
            writer.write(String.join(",", columns));
            writer.write("\n");

            for (Map<String, Object> row : rows) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) {
                        line.append(',');
                    }
                    line.append(csvEscape(row.get(columns.get(i))));
                }
                writer.write(line.append('\n').toString());
            }
        }
        return buffer.toByteArray();
    }

    /** Cột {@code old_value}/{@code new_value} là JSON, chắc chắn chứa dấu phẩy và dấu nháy kép. */
    private static String csvEscape(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.indexOf(',') < 0 && text.indexOf('"') < 0 && text.indexOf('\n') < 0) {
            return text;
        }
        return '"' + text.replace("\"", "\"\"") + '"';
    }
}
