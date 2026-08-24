-- =============================================================================
-- WS-2 / T2.4 — Bảng nền tảng dùng chung cho mọi module
-- Hiện thực hạ tầng cho 6 pattern P1–P6 (implement.md §2):
--   attachments (P3) · workflow_* (P1) · notifications (P4) · jobs (P5)
--   settings (P6) · code_sequences · holidays · shedlock · security_events
-- =============================================================================

-- -----------------------------------------------------------------------------
-- attachments — bảng polymorphic dùng chung (P3), file thực nằm ở MinIO
-- conventions.md §4.4: check magic bytes, tên file random hóa, scan ClamAV async
-- -----------------------------------------------------------------------------
CREATE TABLE attachments (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID         NOT NULL DEFAULT gen_random_uuid(),
    -- Chủ sở hữu đa hình: 'ARTICLE', 'CONSTRUCTION', 'MAINTENANCE_LOG', 'EMPLOYEE'…
    -- Không đặt FK vì trỏ sang nhiều bảng thuộc nhiều module.
    owner_type       VARCHAR(50)  NOT NULL,
    owner_id         BIGINT,
    purpose          VARCHAR(50),
    original_name    VARCHAR(500) NOT NULL,
    -- Object key trong MinIO — tên đã random hóa, KHÔNG dùng tên gốc do người dùng đặt
    storage_bucket   VARCHAR(100) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    content_type     VARCHAR(150) NOT NULL,
    size_bytes       BIGINT       NOT NULL,
    checksum_sha256  CHAR(64),
    -- Phiên bản tài liệu (P3): cùng owner + purpose, số tăng dần
    file_version     INTEGER      NOT NULL DEFAULT 1,
    -- Hiệu lực tài liệu (VD giấy phép, chứng chỉ) — nguồn cho cảnh báo hết hạn
    valid_from       DATE,
    valid_until      DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'UPLOADING',
    scan_status      VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    scan_at          timestamptz,
    scan_result      VARCHAR(255),
    org_unit_id      BIGINT REFERENCES org_units (id),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_at       timestamptz,
    updated_by       BIGINT,
    deleted_at       timestamptz,
    version          INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_attachments_status CHECK (
        status IN ('UPLOADING', 'READY', 'QUARANTINED', 'FAILED')
    ),
    CONSTRAINT ck_attachments_scan_status CHECK (
        scan_status IN ('PENDING', 'CLEAN', 'INFECTED', 'SKIPPED', 'ERROR')
    ),
    CONSTRAINT ck_attachments_size CHECK (size_bytes >= 0),
    CONSTRAINT ck_attachments_valid_range CHECK (
        valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from
    )
);

CREATE UNIQUE INDEX uq_attachments_public_id ON attachments (public_id);
CREATE UNIQUE INDEX uq_attachments_storage_key ON attachments (storage_bucket, storage_key);
CREATE INDEX ix_attachments_owner ON attachments (owner_type, owner_id)
    WHERE deleted_at IS NULL;
-- Job quét file quá hạn / file chờ scan
CREATE INDEX ix_attachments_valid_until ON attachments (valid_until)
    WHERE valid_until IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_attachments_scan_pending ON attachments (scan_status)
    WHERE scan_status = 'PENDING';

COMMENT ON COLUMN attachments.status IS
    'File chỉ chuyển READY sau khi scan_status = CLEAN (conventions.md §4.4)';

-- -----------------------------------------------------------------------------
-- settings — tham số nghiệp vụ có UI sửa (CLAUDE.md quy tắc 12, CN-05.3)
-- Tên cột `setting_key`/`setting_value`: `key` và `value` là từ khóa JPQL
-- (KEY()/VALUE() cho Map) — tránh phải escape ở mọi truy vấn.
-- -----------------------------------------------------------------------------
CREATE TABLE settings (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    setting_key    VARCHAR(120) NOT NULL,
    setting_value  TEXT,
    value_type     VARCHAR(20)  NOT NULL,
    default_value  TEXT,
    group_code     VARCHAR(50)  NOT NULL,
    label          VARCHAR(255) NOT NULL,
    description    TEXT,
    -- Ràng buộc validate phía BE (regex / khoảng giá trị / danh sách hợp lệ)
    validation     TEXT,
    -- Admin sửa được trên UI? (FALSE = chỉ đổi bằng migration)
    editable       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- FALSE = loại khỏi bản xuất cấu hình M5.17 (conventions.md §4.7)
    exportable     BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     BIGINT,
    updated_at     timestamptz,
    updated_by     BIGINT,
    version        INTEGER      NOT NULL DEFAULT 0,
    CONSTRAINT ck_settings_value_type CHECK (
        value_type IN ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN',
                       'JSON', 'CRON', 'TIME', 'DATE', 'DURATION')
    )
);

CREATE UNIQUE INDEX uq_settings_key ON settings (setting_key);
CREATE INDEX ix_settings_group_code ON settings (group_code);

COMMENT ON TABLE settings IS
    'Tham số nghiệp vụ — CẤM để trong application.yml hoặc hard-code (CLAUDE.md quy tắc 12)';
COMMENT ON COLUMN settings.exportable IS
    'FALSE → không nằm trong bản export cấu hình (M5.17). Credential không bao giờ nằm ở bảng này';

-- -----------------------------------------------------------------------------
-- jobs — hàng đợi job trong DB (P5). Worker in-process ở v1, lấy việc bằng
-- SELECT … FOR UPDATE SKIP LOCKED (architecture-review.md §6.3).
-- -----------------------------------------------------------------------------
CREATE TABLE jobs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id      UUID         NOT NULL DEFAULT gen_random_uuid(),
    job_type       VARCHAR(60)  NOT NULL,
    payload        JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    priority       SMALLINT     NOT NULL DEFAULT 5,
    -- Thời điểm sớm nhất được nhặt — dùng cho retry backoff và job hẹn giờ
    available_at   timestamptz  NOT NULL DEFAULT now(),
    started_at     timestamptz,
    finished_at    timestamptz,
    attempts       SMALLINT     NOT NULL DEFAULT 0,
    max_attempts   SMALLINT     NOT NULL DEFAULT 3,
    progress       SMALLINT     NOT NULL DEFAULT 0,
    result         JSONB,
    last_error     TEXT,
    -- Định danh worker đang giữ job — phát hiện job treo khi node chết
    locked_by      VARCHAR(120),
    locked_at      timestamptz,
    -- Chống chạy trùng: 2 job cùng dedup_key mà chưa kết thúc thì chỉ giữ 1
    dedup_key      VARCHAR(200),
    requested_by   BIGINT,
    org_unit_id    BIGINT REFERENCES org_units (id),
    trace_id       VARCHAR(64),
    created_at     timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT ck_jobs_status CHECK (
        status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_jobs_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_jobs_attempts CHECK (attempts >= 0 AND max_attempts > 0)
);

CREATE UNIQUE INDEX uq_jobs_public_id ON jobs (public_id);
-- Index chính của vòng lặp worker: chỉ quét job đang chờ
CREATE INDEX ix_jobs_pickup ON jobs (available_at, priority, id)
    WHERE status = 'PENDING';
CREATE INDEX ix_jobs_status_created ON jobs (status, created_at DESC);
-- Chống overlapping run (§6.3): 1 dedup_key chỉ có 1 job chưa kết thúc
CREATE UNIQUE INDEX uq_jobs_dedup_active ON jobs (dedup_key)
    WHERE dedup_key IS NOT NULL AND status IN ('PENDING', 'RUNNING');

-- -----------------------------------------------------------------------------
-- notifications (P4) — v1 bật kênh in-app + email; SMS tắt (chốt B7)
-- -----------------------------------------------------------------------------
CREATE TABLE notifications (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id   UUID         NOT NULL DEFAULT gen_random_uuid(),
    event_type  VARCHAR(60)  NOT NULL,
    severity    VARCHAR(20)  NOT NULL DEFAULT 'INFO',
    title       VARCHAR(255) NOT NULL,
    body        TEXT,
    link_url    VARCHAR(500),
    ref_type    VARCHAR(50),
    ref_id      BIGINT,
    -- TRUE = thông báo hệ thống do Admin gửi (M5.13), phân biệt với alert nghiệp vụ
    is_broadcast BOOLEAN     NOT NULL DEFAULT FALSE,
    expires_at  timestamptz,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    CONSTRAINT ck_notifications_severity CHECK (
        severity IN ('INFO', 'WARNING', 'DANGER', 'CRITICAL')
    )
);

CREATE UNIQUE INDEX uq_notifications_public_id ON notifications (public_id);
CREATE INDEX ix_notifications_event_type ON notifications (event_type, created_at DESC);

CREATE TABLE notification_recipients (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_id BIGINT      NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    user_id         BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    channel         VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts        SMALLINT    NOT NULL DEFAULT 0,
    sent_at         timestamptz,
    read_at         timestamptz,
    error_message   VARCHAR(500),
    CONSTRAINT ck_notification_recipients_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'SMS', 'WEB_PUSH')
    ),
    CONSTRAINT ck_notification_recipients_status CHECK (
        status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')
    )
);

-- Một người chỉ nhận 1 lần trên mỗi kênh (resolver G11 hợp nhiều nguồn → khử trùng lặp)
CREATE UNIQUE INDEX uq_notification_recipients ON notification_recipients
    (notification_id, user_id, channel);
-- Badge "chưa đọc" trên UI
CREATE INDEX ix_notification_recipients_inbox ON notification_recipients (user_id, read_at)
    WHERE channel = 'IN_APP';
CREATE INDEX ix_notification_recipients_pending ON notification_recipients (status)
    WHERE status = 'PENDING';

-- -----------------------------------------------------------------------------
-- Workflow engine (P1) — NƠI DUY NHẤT được đổi trạng thái entity
-- CLAUDE.md quy tắc 4 · conventions.md §4.3
-- -----------------------------------------------------------------------------
CREATE TABLE workflow_definitions (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL,
    entity_type   VARCHAR(100) NOT NULL,
    name          VARCHAR(255) NOT NULL,
    initial_state VARCHAR(50)  NOT NULL,
    description   TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    BIGINT,
    updated_at    timestamptz,
    updated_by    BIGINT,
    version       INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_workflow_definitions_code ON workflow_definitions (code);
CREATE UNIQUE INDEX uq_workflow_definitions_entity_type ON workflow_definitions (entity_type)
    WHERE active;

CREATE TABLE workflow_transitions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    definition_id       BIGINT      NOT NULL REFERENCES workflow_definitions (id) ON DELETE CASCADE,
    from_state          VARCHAR(50) NOT NULL,
    action              VARCHAR(50) NOT NULL,
    to_state            VARCHAR(50) NOT NULL,
    -- Quyền cần có để thực hiện transition — engine kiểm trong cùng transaction
    required_permission VARCHAR(100),
    -- Sự kiện bắn cho Notification service sau khi transition thành công
    notify_event        VARCHAR(60),
    label               VARCHAR(100) NOT NULL,
    sort_order          INTEGER     NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_workflow_transitions ON workflow_transitions
    (definition_id, from_state, action);
CREATE INDEX ix_workflow_transitions_definition ON workflow_transitions (definition_id);

COMMENT ON TABLE workflow_transitions IS
    'Bộ (from_state, action) → to_state. API trả allowedActions cho FE render nút — '
    'FE không tự suy quyền (conventions.md §3)';

-- -----------------------------------------------------------------------------
-- holidays — ngày nghỉ lễ, dùng cho DateTimeUtils.workingDays() và tính phép năm
-- CN-04.9: ngày lễ trừ tự động khi tính số ngày nghỉ
-- -----------------------------------------------------------------------------
CREATE TABLE holidays (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    holiday_date DATE         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    -- Lễ âm lịch đổi ngày mỗi năm → phải nhập từng năm, không suy ra được
    is_recurring BOOLEAN      NOT NULL DEFAULT FALSE,
    note         VARCHAR(500),
    created_at   timestamptz  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_at   timestamptz,
    updated_by   BIGINT,
    version      INTEGER      NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_holidays_date ON holidays (holiday_date);

-- -----------------------------------------------------------------------------
-- code_sequences — sinh mã nghiệp vụ (BT-2026-0001, NV-2026-001…)
-- CodeGenerator dùng UPDATE … RETURNING (khóa dòng) — an toàn concurrent.
-- CẤM dùng MAX(mã)+1 vì sinh trùng khi 2 request cùng lúc.
-- -----------------------------------------------------------------------------
CREATE TABLE code_sequences (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    seq_type      VARCHAR(30) NOT NULL,
    seq_year      SMALLINT    NOT NULL,
    current_value BIGINT      NOT NULL DEFAULT 0,
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_code_sequences_value CHECK (current_value >= 0)
);

CREATE UNIQUE INDEX uq_code_sequences ON code_sequences (seq_type, seq_year);

-- -----------------------------------------------------------------------------
-- shedlock — khóa phân tán cho job theo lịch. Cài sẵn nhưng TẮT ở v1 (1 node);
-- bật bằng env SHEDLOCK_ENABLED khi lên ≥2 node (architecture-review.md §6.2).
-- Schema theo chuẩn shedlock-provider-jdbc-template, không tự đặt tên cột.
-- -----------------------------------------------------------------------------
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  VARCHAR(255) NOT NULL
);

-- -----------------------------------------------------------------------------
-- security_events — luồng sự kiện bảo mật riêng, tách khỏi audit nghiệp vụ
-- conventions.md §4.5: login fail, refresh reuse, 403 scope, đổi quyền,
-- truy cập credential → Grafana + alert.
-- Append-only như audit_logs: app KHÔNG có UPDATE/DELETE (xem V…1006).
-- -----------------------------------------------------------------------------
CREATE TABLE security_events (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    event_type  VARCHAR(60) NOT NULL,
    severity    VARCHAR(20) NOT NULL DEFAULT 'WARNING',
    user_id     BIGINT,
    -- Giữ lại username dạng text: tài khoản có thể bị xóa mềm, sự kiện thì phải còn
    username    VARCHAR(100),
    ip_address  INET,
    user_agent  VARCHAR(500),
    detail      JSONB,
    trace_id    VARCHAR(64),
    CONSTRAINT ck_security_events_severity CHECK (
        severity IN ('INFO', 'WARNING', 'DANGER', 'CRITICAL')
    )
);

CREATE INDEX ix_security_events_occurred_at ON security_events (occurred_at DESC);
CREATE INDEX ix_security_events_type ON security_events (event_type, occurred_at DESC);
CREATE INDEX ix_security_events_user_id ON security_events (user_id, occurred_at DESC);

COMMENT ON TABLE security_events IS
    'Append-only. Dùng cho alert near-real-time (M5.16) và điều tra sự cố bảo mật';
