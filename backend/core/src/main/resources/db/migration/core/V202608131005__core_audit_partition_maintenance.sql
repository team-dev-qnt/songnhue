-- =============================================================================
-- WS-2 / T2.6 — Tạo partition tháng cho audit_logs (idempotent)
--
-- Gọi 2 chỗ:
--   1. Cuối file này — tạo sẵn 12 tháng runway ngay lúc migrate
--   2. Job theo lịch hằng tháng (WS-6/T6.8) — giữ runway luôn ≥ 6 tháng
--
-- Hỏng job này KHÔNG làm hỏng nghiệp vụ: bản ghi rơi vào audit_logs_default,
-- vẫn đọc/verify chain bình thường, chỉ chậm hơn. Tình huống đó phải cảnh báo
-- (WS-7) chứ không im lặng.
-- =============================================================================

CREATE FUNCTION core_create_audit_partition(p_month DATE)
RETURNS BOOLEAN
LANGUAGE plpgsql AS $$
DECLARE
    v_start DATE := date_trunc('month', p_month)::date;
    v_end   DATE := (date_trunc('month', p_month) + INTERVAL '1 month')::date;
    v_name  TEXT := 'audit_logs_p' || to_char(v_start, 'YYYYMM');
    v_stuck BIGINT;
BEGIN
    IF to_regclass('public.' || quote_ident(v_name)) IS NOT NULL THEN
        RETURN FALSE;
    END IF;

    -- Nếu default đang giữ bản ghi của tháng này thì PostgreSQL sẽ từ chối tạo
    -- partition. Báo rõ và bỏ qua, KHÔNG để migration/job đổ vỡ vì chuyện này.
    SELECT count(*) INTO v_stuck
      FROM audit_logs_default
     WHERE occurred_at >= v_start AND occurred_at < v_end;

    IF v_stuck > 0 THEN
        RAISE WARNING
            'Bỏ qua % — audit_logs_default đang giữ % bản ghi thuộc tháng này. '
            'Xem docs/runbook/audit-partition.md để gỡ.', v_name, v_stuck;
        RETURN FALSE;
    END IF;

    EXECUTE format(
        'CREATE TABLE %I PARTITION OF audit_logs FOR VALUES FROM (%L) TO (%L)',
        v_name, v_start, v_end);

    -- Quyền trên partition KHÔNG kế thừa từ bảng cha khi truy vấn thẳng vào
    -- partition → phải siết ngay tại đây, nếu không app user xóa được bằng
    -- `DELETE FROM audit_logs_p202608`.
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_app') THEN
        EXECUTE format('GRANT SELECT, INSERT ON %I TO songnhue_app', v_name);
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON %I FROM songnhue_app', v_name);
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_archiver') THEN
        EXECUTE format('GRANT SELECT, DELETE ON %I TO songnhue_archiver', v_name);
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'songnhue_readonly') THEN
        EXECUTE format('GRANT SELECT ON %I TO songnhue_readonly', v_name);
    END IF;

    RAISE NOTICE 'Đã tạo partition % (% → %)', v_name, v_start, v_end;
    RETURN TRUE;
END $$;

COMMENT ON FUNCTION core_create_audit_partition IS
    'Tạo partition tháng cho audit_logs + siết quyền. Idempotent — gọi lại không lỗi';

-- -----------------------------------------------------------------------------
-- Đảm bảo có đủ runway: tháng hiện tại + p_months_ahead tháng tới.
-- Trả về số partition thực sự được tạo (0 = đã đủ, không phải lỗi).
-- -----------------------------------------------------------------------------
CREATE FUNCTION core_ensure_audit_partitions(p_months_ahead INTEGER DEFAULT 6)
RETURNS INTEGER
LANGUAGE plpgsql AS $$
DECLARE
    v_created INTEGER := 0;
    i         INTEGER;
BEGIN
    IF p_months_ahead < 0 THEN
        RAISE EXCEPTION 'p_months_ahead phải ≥ 0, nhận được %', p_months_ahead;
    END IF;

    FOR i IN 0..p_months_ahead LOOP
        IF core_create_audit_partition((current_date + (i || ' month')::interval)::date) THEN
            v_created := v_created + 1;
        END IF;
    END LOOP;

    RETURN v_created;
END $$;

COMMENT ON FUNCTION core_ensure_audit_partitions IS
    'Giữ runway partition. Job hằng tháng (T6.8) gọi với mặc định 6 tháng';

-- Tháng trước để chỗ cho bản ghi backdate lúc nhập liệu ban đầu / restore.
SELECT core_create_audit_partition((current_date - INTERVAL '1 month')::date);
-- 12 tháng runway: job bảo trì phải chết trọn 1 năm thì mới rơi vào default.
SELECT core_ensure_audit_partitions(12);
