-- =============================================================================
-- WS-7 / T7.1, T7.4, T7.5 — Sổ đăng ký bản sao lưu
--
-- Vì sao cần một bảng thay vì chỉ đọc thư mục chứa file dump:
--   • Màn hình M5.10 phải hiển thị "sao lưu gần nhất lúc nào, có thành công
--     không". Bản dump HỎNG thì KHÔNG để lại file — mà đó chính là trường hợp
--     phải hiện ra rõ nhất. Đọc thư mục thì lần hỏng đó vô hình.
--   • Cảnh báo T7.3 cần mốc "lần thành công gần nhất", không phải "file mới
--     nhất trong thư mục". Hai thứ này khác nhau đúng vào lúc backup đang chết.
--   • Khôi phục (M5.11) phải chọn từ danh sách có checksum đối chiếu được.
--
-- ⚠ Bảng này KHÔNG chứa dữ liệu sao lưu, chỉ chứa siêu dữ liệu. File dump nằm
--   ngoài CSDL — nếu không thì bản sao lưu nằm trong chính thứ nó phải cứu.
-- =============================================================================

CREATE TABLE system_backups (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id       UUID        NOT NULL DEFAULT gen_random_uuid(),

    file_name       VARCHAR(255) NOT NULL,
    -- Đường dẫn tuyệt đối trên máy chủ. Giữ nguyên văn để runbook khôi phục thủ
    -- công dùng lại được đúng chuỗi này, không phải suy ra từ quy ước đặt tên.
    file_path       TEXT,
    size_bytes      BIGINT,
    -- SHA-256 dạng hex, luôn đúng 64 ký tự.
    -- ⚠ VARCHAR chứ KHÔNG phải CHAR: CHAR(64) đệm khoảng trắng cho đủ độ dài, nên
    -- phép so sánh checksum lúc khôi phục có thể lệch vì lý do chẳng liên quan gì
    -- tới dữ liệu. (Hibernate `ddl-auto: validate` cũng chặn: CHAR ánh xạ thành
    -- bpchar, không khớp String.)
    checksum_sha256 VARCHAR(64),

    status          VARCHAR(20) NOT NULL,
    trigger_type    VARCHAR(20) NOT NULL,

    started_at      timestamptz NOT NULL DEFAULT now(),
    finished_at     timestamptz,
    duration_ms     BIGINT,

    -- Nguyên văn dòng lỗi cuối của pg_dump. Cắt ngắn ở tầng ứng dụng.
    error_message   TEXT,
    -- Phiên bản máy chủ lúc dump — pg_restore không đọc được bản dump sinh bởi
    -- máy chủ mới hơn, và biết điều đó TRƯỚC khi cần khôi phục thì đỡ hơn nhiều.
    server_version  VARCHAR(50),

    requested_by    BIGINT REFERENCES users (id),
    trace_id        VARCHAR(64),

    CONSTRAINT uq_system_backups_public_id UNIQUE (public_id),
    CONSTRAINT ck_system_backups_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_system_backups_trigger CHECK (
        trigger_type IN ('SCHEDULED', 'MANUAL', 'PRE_RESTORE')
    )
);

COMMENT ON TABLE system_backups IS
    'Sổ đăng ký bản sao lưu pg_dump (WS-7). Ghi cả lượt THẤT BẠI — đó là dòng '
    'quan trọng nhất của bảng này. Không có PITR (architecture-review.md §6.5) '
    'nên bản dump đêm là đường phục hồi duy nhất.';

COMMENT ON COLUMN system_backups.trigger_type IS
    'SCHEDULED = job 02:00 · MANUAL = nút trên UI (M5.10) · '
    'PRE_RESTORE = bản chụp bắt buộc ngay trước khi ghi đè, để còn đường lùi';

-- Truy vấn nóng nhất: "lần thành công gần nhất" — cho cả màn hình M5.10 lẫn
-- metric backup_last_success_timestamp (T7.3). Chỉ mục một phần vì lượt hỏng
-- không bao giờ là câu trả lời của truy vấn này.
CREATE INDEX ix_system_backups_last_success
    ON system_backups (finished_at DESC)
    WHERE status = 'SUCCEEDED';

CREATE INDEX ix_system_backups_started_at ON system_backups (started_at DESC);

-- -----------------------------------------------------------------------------
-- Tham số vận hành — bảng `settings` để sửa được trên UI (CLAUDE.md quy tắc 12)
-- -----------------------------------------------------------------------------
INSERT INTO settings (
    setting_key, setting_value, value_type, default_value,
    group_code, label, description, validation, editable, exportable, sort_order
)
SELECT v.k, v.val, v.vtype, v.val, 'BACKUP', v.label, v.descr, v.validation, TRUE, TRUE, v.ord
FROM (VALUES
    ('backup.schedule-enabled', 'true', 'BOOLEAN',
     'Bật sao lưu tự động hằng đêm',
     'Tắt là hệ thống chạy KHÔNG có lưới an toàn — chỉ tắt khi có cơ chế sao lưu khác ở ngoài',
     NULL, 10),
    ('backup.retention-days', '30', 'INTEGER',
     'Số ngày giữ bản sao lưu',
     'Bản cũ hơn bị xoá sau khi lượt sao lưu mới THÀNH CÔNG (§6.5)',
     'min=7;max=365', 20),
    ('backup.stale-hours', '26', 'INTEGER',
     'Ngưỡng coi bản sao lưu gần nhất là quá cũ (giờ)',
     'Nguồn của cảnh báo backup. 26 chứ không phải 24: chừa biên cho lượt chạy trễ '
     'mà không sinh cảnh báo giả mỗi đêm (T7.3)',
     'min=12;max=168', 30)
) AS v(k, val, vtype, label, descr, validation, ord);
