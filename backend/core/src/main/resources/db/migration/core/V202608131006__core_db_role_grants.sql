-- =============================================================================
-- WS-2 / T2.7 — Phân quyền DB theo role (architecture-review.md §9.3)
--
-- Nguyên tắc §4.3: "enforce ở tầng DB, không chỉ ở code". Lỗi lập trình hay
-- SQL injection cũng KHÔNG xóa được nhật ký, vì app user đơn giản là không có
-- quyền đó ở tầng PostgreSQL.
--
--   songnhue_owner    sở hữu schema — CHỈ service `migrator` dùng
--   songnhue_app      runtime — không UPDATE/DELETE trên audit_logs,
--                     security_events; không đụng audit_chain_head
--   songnhue_archiver job kết xuất audit quá hạn (G7) — có DELETE audit
--   songnhue_readonly chỉ đọc
--
-- Role do deploy/postgres/init/10-bootstrap.sh tạo (cần superuser).
-- =============================================================================

-- Thiếu role thì DỪNG HẲN: chạy tiếp nghĩa là production chạy không có lớp bảo
-- vệ ở tầng DB mà không ai biết.
DO $$
DECLARE
    v_missing TEXT;
BEGIN
    SELECT string_agg(r.name, ', ' ORDER BY r.name)
      INTO v_missing
      FROM (VALUES ('songnhue_owner'), ('songnhue_app'),
                   ('songnhue_archiver'), ('songnhue_readonly')) AS r(name)
     WHERE NOT EXISTS (SELECT 1 FROM pg_roles p WHERE p.rolname = r.name);

    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION 'Thiếu DB role: %', v_missing
            USING HINT = 'Chạy deploy/postgres/init/10-bootstrap.sh bằng superuser trước khi migrate.';
    END IF;

    IF current_user <> 'songnhue_owner' THEN
        RAISE WARNING
            'Migration đang chạy bằng "%" chứ không phải songnhue_owner. '
            'Bảng sẽ thuộc sở hữu của role này và default privileges gắn theo nó.',
            current_user;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 1. Quyền trên các bảng HIỆN CÓ — chỉ duyệt bảng do migration tạo ra, không
--    đụng tới bảng của PostGIS (spatial_ref_sys…) mà ta không sở hữu.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.relname
          FROM pg_class c
          JOIN pg_namespace n ON n.oid = c.relnamespace
         WHERE n.nspname = 'public'
           AND c.relkind IN ('r', 'p')
           AND pg_get_userbyid(c.relowner) = current_user
    LOOP
        EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON public.%I TO songnhue_app', r.relname);
        EXECUTE format('GRANT SELECT ON public.%I TO songnhue_readonly', r.relname);
    END LOOP;
END $$;

GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO songnhue_app;
GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO songnhue_readonly;

-- -----------------------------------------------------------------------------
-- 2. Siết các bảng append-only
-- -----------------------------------------------------------------------------

-- audit_logs: bảng cha + TOÀN BỘ partition. Quyền trên partition không kế thừa
-- từ bảng cha khi truy vấn thẳng vào partition — phải revoke từng cái.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.relname
          FROM pg_class c
         WHERE c.oid = 'public.audit_logs'::regclass
        UNION ALL
        SELECT c.relname
          FROM pg_inherits i
          JOIN pg_class c ON c.oid = i.inhrelid
         WHERE i.inhparent = 'public.audit_logs'::regclass
    LOOP
        EXECUTE format('REVOKE UPDATE, DELETE, TRUNCATE ON public.%I FROM songnhue_app', r.relname);
        EXECUTE format('GRANT SELECT, DELETE ON public.%I TO songnhue_archiver', r.relname);
    END LOOP;
END $$;

-- security_events: bằng chứng điều tra sự cố — cùng chế độ append-only
REVOKE UPDATE, DELETE, TRUNCATE ON security_events FROM songnhue_app;

-- audit_chain_head: app KHÔNG được đụng vào. Trigger chèn audit là SECURITY
-- DEFINER nên vẫn cập nhật được — đây chính là chỗ khiến client không giả được chain.
REVOKE ALL ON audit_chain_head FROM songnhue_app, songnhue_readonly;
GRANT SELECT ON audit_chain_head TO songnhue_archiver, songnhue_readonly;

-- Điểm neo kết xuất: chỉ job archiver ghi
REVOKE INSERT, UPDATE, DELETE ON audit_archive_anchors FROM songnhue_app;
GRANT SELECT, INSERT, UPDATE ON audit_archive_anchors TO songnhue_archiver;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO songnhue_archiver;

-- Lịch sử migration: app chỉ đọc (health-check báo version), không ghi
REVOKE INSERT, UPDATE, DELETE, TRUNCATE ON flyway_schema_history FROM songnhue_app;

-- -----------------------------------------------------------------------------
-- 3. Quyền mặc định cho bảng TẠO SAU (module nghiệp vụ Phase 1+)
--    Gắn theo current_user để đúng cả khi migrate bằng role khác.
--
--    ⚠ Bảng append-only mới (VD hydro_raw_logs — MOD-03) sẽ tự động nhận
--      UPDATE/DELETE từ đây → migration của module ĐÓ bắt buộc phải REVOKE lại.
--      Nhắc ở backend/hydro/src/main/resources/db/migration/hyd/README.md
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
        'GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO songnhue_app', current_user);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
        'GRANT USAGE, SELECT ON SEQUENCES TO songnhue_app', current_user);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
        'GRANT SELECT ON TABLES TO songnhue_readonly', current_user);
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public '
        'GRANT EXECUTE ON FUNCTIONS TO songnhue_app', current_user);
END $$;

-- -----------------------------------------------------------------------------
-- 4. Hàm dùng chung
-- -----------------------------------------------------------------------------
GRANT EXECUTE ON FUNCTION core_verify_audit_chain(BIGINT, BIGINT) TO songnhue_app, songnhue_readonly;
GRANT EXECUTE ON FUNCTION core_ensure_audit_partitions(INTEGER) TO songnhue_app;
GRANT EXECUTE ON FUNCTION core_create_audit_partition(DATE) TO songnhue_app;

-- Tạo partition = tạo bảng → chỉ owner làm được. App gọi hàm này qua job hằng
-- tháng nên hàm phải chạy bằng quyền owner.
ALTER FUNCTION core_create_audit_partition(DATE) SECURITY DEFINER;
ALTER FUNCTION core_ensure_audit_partitions(INTEGER) SECURITY DEFINER;
ALTER FUNCTION core_create_audit_partition(DATE) SET search_path = public;
ALTER FUNCTION core_ensure_audit_partitions(INTEGER) SET search_path = public;
