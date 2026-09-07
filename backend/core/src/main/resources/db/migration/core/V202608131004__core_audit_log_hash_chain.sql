-- =============================================================================
-- WS-2 / T2.5 — audit_logs: partition theo tháng + hash chain chống sửa lén
--
-- conventions.md §4.3:
--   • append-only — app user chỉ có INSERT/SELECT (GRANT ở V…1006)
--   • hash = SHA-256(nội dung bản ghi ‖ prev_hash) → sửa/xóa lén là phát hiện được
--   • kết xuất >5 năm (G7) phải giữ điểm neo để chain nối tiếp liền mạch
--
-- ⭐ QUYẾT ĐỊNH: hash tính bằng TRIGGER trong DB, không tính ở tầng Java.
--    Lý do: app user chỉ có INSERT, trigger là SECURITY DEFINER nên client
--    KHÔNG thể tự đặt seq/prev_hash/hash — có ghi lên cũng bị ghi đè. Nếu tính
--    ở Java thì bug ở app đủ để phá chain mà không ai biết.
--    Hệ quả: audit insert bị tuần tự hóa qua 1 dòng khóa (audit_chain_head).
--    Chấp nhận được với tải vài nghìn bản ghi/ngày của hệ này.
--
-- ⚠ Hàm băm nằm ở ĐÚNG MỘT CHỖ (core_audit_canonical_payload). API verify chain
--   (T6.12) phải gọi core_verify_audit_chain(), CẤM cài lại công thức bên Java —
--   hai công thức lệch nhau là chain gãy giả.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Bảng cha, phân mảnh RANGE theo occurred_at (tháng)
-- Khóa chính bắt buộc chứa cột phân mảnh → PK (id, occurred_at).
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    occurred_at      timestamptz  NOT NULL DEFAULT now(),
    -- Số thứ tự toàn cục của chain, do trigger cấp — liên tục qua mọi partition
    seq              BIGINT       NOT NULL,
    actor_user_id    BIGINT,
    actor_username   VARCHAR(100),
    module           VARCHAR(20)  NOT NULL,
    entity_type      VARCHAR(100) NOT NULL,
    entity_id        BIGINT,
    entity_public_id UUID,
    action           VARCHAR(30)  NOT NULL,
    old_value        JSONB,
    new_value        JSONB,
    org_unit_id      BIGINT,
    ip_address       INET,
    trace_id         VARCHAR(64),
    prev_hash        CHAR(64),
    hash             CHAR(64)     NOT NULL,
    PRIMARY KEY (id, occurred_at),
    CONSTRAINT ck_audit_logs_module CHECK (
        module IN ('core', 'cms', 'ops', 'hyd', 'hr', 'adm')
    ),
    CONSTRAINT ck_audit_logs_action CHECK (
        action IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'LOGIN', 'LOGOUT',
                   'LOGIN_FAILED', 'PERMISSION_CHANGE', 'EXPORT', 'IMPORT',
                   'APPROVE', 'REJECT', 'PUBLISH', 'BACKUP', 'DB_RESTORE', 'ARCHIVE')
    )
) PARTITION BY RANGE (occurred_at);

CREATE INDEX ix_audit_logs_seq ON audit_logs (seq);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_user_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_module ON audit_logs (module, occurred_at DESC);
CREATE INDEX ix_audit_logs_trace_id ON audit_logs (trace_id) WHERE trace_id IS NOT NULL;

COMMENT ON TABLE audit_logs IS
    'Append-only + hash chain. KHÔNG vai trò nào được sửa/xóa, kể cả Admin (CN-05.4)';
COMMENT ON COLUMN audit_logs.seq IS
    'Thứ tự chain, do trigger cấp. Verify bằng core_verify_audit_chain()';

-- Partition hứng mọi bản ghi không rơi vào partition tháng nào.
-- Là LƯỚI AN TOÀN: thà ghi chậm còn hơn INSERT lỗi làm hỏng giao dịch nghiệp vụ.
-- Bình thường phải luôn RỖNG — có dòng ở đây nghĩa là job bảo trì partition đã chết.
CREATE TABLE audit_logs_default PARTITION OF audit_logs DEFAULT;

-- -----------------------------------------------------------------------------
-- Đầu chain — đúng 1 dòng, giữ seq và hash cuối cùng
-- -----------------------------------------------------------------------------
CREATE TABLE audit_chain_head (
    id         SMALLINT PRIMARY KEY DEFAULT 1,
    last_seq   BIGINT      NOT NULL DEFAULT 0,
    last_hash  CHAR(64),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_chain_head_singleton CHECK (id = 1)
);

INSERT INTO audit_chain_head (id) VALUES (1);

-- -----------------------------------------------------------------------------
-- Dạng chuẩn hóa của bản ghi để băm — ĐỊNH NGHĨA DUY NHẤT
-- JSONB::text được PostgreSQL chuẩn hóa (sắp khóa, bỏ khoảng trắng thừa) nên
-- cùng một nội dung luôn cho cùng một chuỗi.
-- -----------------------------------------------------------------------------
CREATE FUNCTION core_audit_canonical_payload(
    p_seq              BIGINT,
    p_occurred_at      timestamptz,
    p_actor_user_id    BIGINT,
    p_module           VARCHAR,
    p_entity_type      VARCHAR,
    p_entity_id        BIGINT,
    p_action           VARCHAR,
    p_old_value        JSONB,
    p_new_value        JSONB
) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
    SELECT concat_ws('|',
        p_seq::text,
        to_char(p_occurred_at AT TIME ZONE 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS.US'),
        coalesce(p_actor_user_id::text, ''),
        p_module,
        p_entity_type,
        coalesce(p_entity_id::text, ''),
        p_action,
        coalesce(p_old_value::text, ''),
        coalesce(p_new_value::text, '')
    );
$$;

CREATE FUNCTION core_audit_hash(p_payload TEXT, p_prev_hash CHAR(64))
RETURNS CHAR(64)
LANGUAGE sql IMMUTABLE AS $$
    SELECT encode(sha256(convert_to(p_payload || coalesce(p_prev_hash, ''), 'UTF8')), 'hex');
$$;

-- -----------------------------------------------------------------------------
-- Trigger cấp seq/prev_hash/hash. SECURITY DEFINER vì app user không có UPDATE
-- trên audit_chain_head — đây chính là điều làm client không giả được chain.
-- -----------------------------------------------------------------------------
CREATE FUNCTION core_audit_logs_before_insert() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
    v_seq       BIGINT;
    v_prev_hash CHAR(64);
    v_payload   TEXT;
BEGIN
    -- UPDATE … RETURNING khóa dòng đầu chain → các insert đồng thời xếp hàng.
    -- last_hash trả về là giá trị TRƯỚC khi ta ghi hash mới (chưa đụng cột này).
    UPDATE audit_chain_head
       SET last_seq = last_seq + 1,
           updated_at = now()
     WHERE id = 1
    RETURNING last_seq, last_hash INTO v_seq, v_prev_hash;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'audit_chain_head trống — database khởi tạo sai';
    END IF;

    NEW.seq := v_seq;
    NEW.prev_hash := v_prev_hash;

    v_payload := core_audit_canonical_payload(
        NEW.seq, NEW.occurred_at, NEW.actor_user_id, NEW.module,
        NEW.entity_type, NEW.entity_id, NEW.action, NEW.old_value, NEW.new_value);
    NEW.hash := core_audit_hash(v_payload, v_prev_hash);

    UPDATE audit_chain_head SET last_hash = NEW.hash WHERE id = 1;

    RETURN NEW;
END $$;

CREATE TRIGGER trg_audit_logs_chain
    BEFORE INSERT ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION core_audit_logs_before_insert();

-- Chặn UPDATE ở tầng DB cho MỌI role, kể cả owner. Xóa thì vẫn cho phép nhưng
-- chỉ songnhue_archiver có quyền (job kết xuất G7) — xem V…1006.
CREATE FUNCTION core_audit_logs_deny_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_logs là append-only — không sửa bản ghi nhật ký (CN-05.4)'
        USING ERRCODE = 'restrict_violation';
END $$;

CREATE TRIGGER trg_audit_logs_deny_update
    BEFORE UPDATE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION core_audit_logs_deny_update();

-- -----------------------------------------------------------------------------
-- Verify chain — trả về các mắt xích gãy. Rỗng = chain toàn vẹn.
-- Kiểm 2 thứ: (a) hash tự thân đúng, (b) prev_hash khớp hash của bản ghi liền trước.
-- p_from_seq cho phép verify từ điểm neo kết xuất trở đi (không phải quét lại 5 năm).
-- -----------------------------------------------------------------------------
CREATE FUNCTION core_verify_audit_chain(
    p_from_seq BIGINT DEFAULT NULL,
    p_to_seq   BIGINT DEFAULT NULL
) RETURNS TABLE (
    broken_seq    BIGINT,
    broken_id     BIGINT,
    occurred_at   timestamptz,
    reason        TEXT
)
LANGUAGE sql STABLE AS $$
    WITH scoped AS (
        SELECT a.id, a.seq, a.occurred_at, a.actor_user_id, a.module, a.entity_type,
               a.entity_id, a.action, a.old_value, a.new_value, a.prev_hash, a.hash,
               lag(a.hash) OVER (ORDER BY a.seq) AS expected_prev_hash
          FROM audit_logs a
         WHERE (p_from_seq IS NULL OR a.seq >= p_from_seq)
           AND (p_to_seq   IS NULL OR a.seq <= p_to_seq)
    )
    SELECT s.seq, s.id, s.occurred_at,
           CASE
               WHEN s.hash <> core_audit_hash(
                        core_audit_canonical_payload(s.seq, s.occurred_at, s.actor_user_id,
                            s.module, s.entity_type, s.entity_id, s.action,
                            s.old_value, s.new_value),
                        s.prev_hash)
                   THEN 'Nội dung bản ghi không khớp hash — đã bị sửa'
               ELSE 'prev_hash không khớp bản ghi liền trước — có bản ghi bị xóa hoặc chèn'
           END
      FROM scoped s
     WHERE s.hash <> core_audit_hash(
               core_audit_canonical_payload(s.seq, s.occurred_at, s.actor_user_id,
                   s.module, s.entity_type, s.entity_id, s.action, s.old_value, s.new_value),
               s.prev_hash)
        OR (s.expected_prev_hash IS NOT NULL AND s.prev_hash IS DISTINCT FROM s.expected_prev_hash)
     ORDER BY s.seq;
$$;

COMMENT ON FUNCTION core_verify_audit_chain IS
    'Trả về các mắt xích gãy; rỗng = toàn vẹn. API verify (T6.12) gọi hàm này, cấm cài lại ở Java';

-- -----------------------------------------------------------------------------
-- audit_archive_anchors — điểm neo khi kết xuất audit quá hạn (G7)
-- Quy tắc §4.3: CHỈ được xóa khỏi bảng nóng SAU KHI file đã lên MinIO và
-- checksum verify khớp. Thất bại → không xóa dòng nào + ADM-2001.
-- -----------------------------------------------------------------------------
CREATE TABLE audit_archive_anchors (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id        UUID         NOT NULL DEFAULT gen_random_uuid(),
    from_seq         BIGINT       NOT NULL,
    to_seq           BIGINT       NOT NULL,
    from_occurred_at timestamptz  NOT NULL,
    to_occurred_at   timestamptz  NOT NULL,
    row_count        BIGINT       NOT NULL,
    -- Hash của bản ghi CUỐI lô — chain sau khi xóa vẫn nối lại được từ đây
    last_hash        CHAR(64)     NOT NULL,
    storage_bucket   VARCHAR(100) NOT NULL,
    storage_key      VARCHAR(500) NOT NULL,
    file_size_bytes  BIGINT       NOT NULL,
    checksum_sha256  CHAR(64)     NOT NULL,
    archived_at      timestamptz  NOT NULL DEFAULT now(),
    archived_by      BIGINT,
    -- Chỉ có giá trị sau khi checksum đã verify khớp; NULL = chưa được phép xóa
    verified_at      timestamptz,
    purged_at        timestamptz,
    CONSTRAINT ck_audit_archive_anchors_range CHECK (to_seq >= from_seq),
    CONSTRAINT ck_audit_archive_anchors_purge_order CHECK (
        purged_at IS NULL OR verified_at IS NOT NULL
    )
);

CREATE UNIQUE INDEX uq_audit_archive_anchors_public_id ON audit_archive_anchors (public_id);
CREATE UNIQUE INDEX uq_audit_archive_anchors_storage ON audit_archive_anchors
    (storage_bucket, storage_key);
CREATE INDEX ix_audit_archive_anchors_to_seq ON audit_archive_anchors (to_seq DESC);

COMMENT ON CONSTRAINT ck_audit_archive_anchors_purge_order ON audit_archive_anchors IS
    'Chặn ở tầng DB: chưa verify checksum thì không được đánh dấu đã xóa (§4.3, G7)';
