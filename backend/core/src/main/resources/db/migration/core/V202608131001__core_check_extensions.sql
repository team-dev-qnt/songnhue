-- =============================================================================
-- WS-2 / T2.1 — Kiểm tra extension bắt buộc
--
-- Extension do `deploy/postgres/init/10-bootstrap.sh` tạo bằng quyền superuser
-- (postgis KHÔNG phải trusted extension nên songnhue_owner không tự tạo được).
-- Migration này chỉ VERIFY để lỗi hiện ra ngay ở bước migrate, kèm hướng dẫn —
-- thay vì để lộ ra rất muộn dưới dạng "type geometry does not exist".
-- =============================================================================
DO $$
DECLARE
    v_missing text;
BEGIN
    SELECT string_agg(e.name, ', ' ORDER BY e.name)
      INTO v_missing
      FROM (VALUES ('postgis'), ('unaccent'), ('pg_trgm')) AS e(name)
     WHERE NOT EXISTS (SELECT 1 FROM pg_extension x WHERE x.extname = e.name);

    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION
            'Thiếu extension bắt buộc: %. Database chưa chạy script khởi tạo.',
            v_missing
            USING HINT =
                'Chạy deploy/postgres/init/10-bootstrap.sh bằng superuser, '
                'hoặc xóa volume Postgres để entrypoint chạy lại script init.';
    END IF;
END $$;

-- Ghi lại phiên bản PostGIS vào log migration để đối chiếu khi debug GIS về sau.
DO $$
BEGIN
    RAISE NOTICE 'PostGIS: %', postgis_lib_version();
END $$;
