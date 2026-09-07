#!/usr/bin/env bash
# =============================================================================
# Khởi tạo PostgreSQL lần đầu — CHỈ chạy khi volume dữ liệu còn rỗng
# (docker-entrypoint-initdb.d). Chạy bằng quyền superuser.
#
# Làm 2 việc mà migration KHÔNG làm được vì cần superuser:
#   1. CREATE ROLE — 4 role tách quyền (architecture-review.md §9.3, WS-2/T2.7)
#   2. CREATE EXTENSION — postgis không phải "trusted extension" (WS-2/T2.1)
#
# Schema, bảng, GRANT → do Flyway lo (backend/core/.../db/migration/core).
#
# ⚠ Tên role là CỐ ĐỊNH (songnhue_owner/app/archiver/readonly) vì migration
#   tham chiếu trực tiếp. Chỉ MẬT KHẨU đọc từ env.
# =============================================================================
set -euo pipefail

require_env() {
    local name="$1"
    if [[ -z "${!name:-}" ]]; then
        echo "✗ Thiếu biến môi trường bắt buộc: $name" >&2
        echo "  Xem deploy/env/local.env.example" >&2
        exit 1
    fi
}

require_env DB_MIGRATION_PASSWORD
require_env DB_PASSWORD
require_env DB_ARCHIVER_PASSWORD
require_env DB_READONLY_PASSWORD

echo "→ Tạo role và extension cho database '${POSTGRES_DB}'…"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
    -v owner_pw="$DB_MIGRATION_PASSWORD" \
    -v app_pw="$DB_PASSWORD" \
    -v archiver_pw="$DB_ARCHIVER_PASSWORD" \
    -v readonly_pw="$DB_READONLY_PASSWORD" <<-'EOSQL'

    -- ---------------------------------------------------------------------
    -- 1. Role tách quyền
    --    songnhue_owner    : sở hữu schema, CHỈ service `migrator` dùng
    --    songnhue_app      : runtime — KHÔNG có DELETE trên audit_logs,
    --                        security_events, hydro_raw_logs
    --    songnhue_archiver : job kết xuất audit quá hạn (G7) — có DELETE audit
    --    songnhue_readonly : chỉ đọc (báo cáo ad-hoc, điều tra sự cố)
    -- ---------------------------------------------------------------------
    CREATE ROLE songnhue_owner    LOGIN PASSWORD :'owner_pw';
    CREATE ROLE songnhue_app      LOGIN PASSWORD :'app_pw';
    CREATE ROLE songnhue_archiver LOGIN PASSWORD :'archiver_pw';
    CREATE ROLE songnhue_readonly LOGIN PASSWORD :'readonly_pw';

    -- Không role nào được tạo DB / tạo role / bypass RLS.
    ALTER ROLE songnhue_owner    NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ALTER ROLE songnhue_app      NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ALTER ROLE songnhue_archiver NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    ALTER ROLE songnhue_readonly NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;

    -- ---------------------------------------------------------------------
    -- 2. Quyền kết nối — đóng mặc định cho PUBLIC, chỉ mở cho 4 role trên
    -- ---------------------------------------------------------------------
    REVOKE ALL ON DATABASE :"DBNAME" FROM PUBLIC;
    GRANT CONNECT ON DATABASE :"DBNAME"
        TO songnhue_owner, songnhue_app, songnhue_archiver, songnhue_readonly;
    GRANT CREATE ON DATABASE :"DBNAME" TO songnhue_owner;

    -- Chỉ owner được tạo object trong schema public
    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
    GRANT ALL ON SCHEMA public TO songnhue_owner;
    GRANT USAGE ON SCHEMA public
        TO songnhue_app, songnhue_archiver, songnhue_readonly;

    -- ---------------------------------------------------------------------
    -- 3. Extension (cần superuser — postgis KHÔNG phải trusted extension)
    --    Migration V…1001 kiểm tra lại và fail rõ ràng nếu thiếu.
    -- ---------------------------------------------------------------------
    CREATE EXTENSION IF NOT EXISTS postgis;    -- GIS: MOD-02 bản đồ công trình
    CREATE EXTENSION IF NOT EXISTS unaccent;   -- tìm kiếm tiếng Việt không dấu
    CREATE EXTENSION IF NOT EXISTS pg_trgm;    -- tìm kiếm gần đúng (fuzzy)
EOSQL

echo "✓ Đã tạo 4 role + 3 extension."
