#!/usr/bin/env bash
# =============================================================================
# Sao lưu CSDL — ĐƯỜNG THỦ CÔNG / KHẨN CẤP (WS-7 / T7.1)
#
# ⚠ Đường sao lưu BÌNH THƯỜNG là job trong ứng dụng (02:00 hằng đêm, hoặc nút
#   trên màn hình M5.10). Script này tồn tại cho đúng một tình huống, và đó là
#   tình huống quan trọng nhất: **ứng dụng không chạy được**. Cần một bản chụp
#   trước khi mổ xẻ CSDL hỏng, mà đường trong ứng dụng thì đã chết theo.
#
#   Vì vậy nó KHÔNG phụ thuộc vào ứng dụng — chỉ cần psql + pg_dump + file env.
#
# Hai đường ghi vào CÙNG một sổ đăng ký `system_backups`. Không có phần ghi sổ
# đó thì bản dump này vô hình với màn hình quản trị và với cảnh báo "quá 26 giờ"
# — tức là chạy script xong mà hệ thống vẫn báo động, và người ta sẽ học cách
# bỏ qua cảnh báo.
#
#   Dùng:  make backup                (ENV=local mặc định)
#          ENV=prod deploy/backup/backup.sh
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ENV="${ENV:-local}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/env/$ENV.env}"

if [ ! -f "$ENV_FILE" ]; then
    echo "✗ Không thấy $ENV_FILE — tạo bằng: make env ENV=$ENV" >&2
    exit 1
fi

# shellcheck disable=SC1090
# ⛔ KHÔNG `source` tệp .env — nó không phải script shell. Compose cho phép giá trị
#    nhiều từ không nháy (`ROBOTS_TAG=noindex, nofollow`); shell thì gán nửa đầu rồi
#    CHẠY nửa sau như một lệnh. Đã hỏng đúng vậy, exit 127 (§10.46).
. "$SCRIPT_DIR/../lib/read-env.sh"
set -a; eval "$(doc_env "$ENV_FILE")"; set +a

: "${DB_HOST:?Thiếu DB_HOST}"
: "${DB_PORT:?Thiếu DB_PORT}"
: "${DB_NAME:?Thiếu DB_NAME}"
: "${BACKUP_DIR:?Thiếu BACKUP_DIR}"
: "${DB_READONLY_PASSWORD:?Thiếu DB_READONLY_PASSWORD}"

DB_READONLY_USER="${DB_READONLY_USER:-songnhue_readonly}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

mkdir -p "$BACKUP_DIR"

STAMP="$(date +%Y%m%d-%H%M%S)"
FILE_NAME="songnhue-${DB_NAME}-${STAMP}.dump"
TARGET="$BACKUP_DIR/$FILE_NAME"

# -----------------------------------------------------------------------------
# Ghi sổ: mở dòng RUNNING TRƯỚC khi dump.
# Máy mất điện giữa chừng thì dòng đó còn lại làm dấu vết. Chỉ ghi khi xong thì
# lượt hỏng không để lại gì — mà đó mới là lượt cần nhìn thấy.
# -----------------------------------------------------------------------------
psql_owner() {
    PGPASSWORD="$DB_MIGRATION_PASSWORD" psql \
        --host="$DB_HOST" --port="$DB_PORT" \
        --username="${DB_MIGRATION_USER:-songnhue_owner}" \
        --dbname="$DB_NAME" --no-password --quiet --tuples-only --no-align "$@"
}

BACKUP_ID=""
if [ -n "${DB_MIGRATION_PASSWORD:-}" ]; then
    BACKUP_ID="$(psql_owner --command \
        "INSERT INTO system_backups (file_name, status, trigger_type)
         VALUES ('$FILE_NAME', 'RUNNING', 'MANUAL') RETURNING id;" 2>/dev/null || true)"
fi
[ -z "$BACKUP_ID" ] && echo "  ⚠ Không ghi được sổ đăng ký — bản dump vẫn tạo, nhưng màn hình quản trị sẽ không thấy nó."

close_record() {
    local status="$1" size="$2" checksum="$3" error="$4"
    [ -z "$BACKUP_ID" ] && return 0
    psql_owner --command \
        "UPDATE system_backups
            SET status = '$status',
                finished_at = now(),
                duration_ms = EXTRACT(EPOCH FROM (now() - started_at)) * 1000,
                file_path = NULLIF('$TARGET', ''),
                size_bytes = NULLIF('$size', '')::BIGINT,
                checksum_sha256 = NULLIF('$checksum', ''),
                error_message = NULLIF(\$err\$$error\$err\$, '')
          WHERE id = $BACKUP_ID;" >/dev/null 2>&1 || true
}

# -----------------------------------------------------------------------------
# Dump. -Fc đã nén sẵn bằng zlib — KHÔNG gzip chồng lên: thêm một tầng nén là
# thêm một chỗ hỏng mà gần như không giảm thêm dung lượng.
# -----------------------------------------------------------------------------
echo "→ pg_dump $DB_NAME → $TARGET"
# ⚠⚠ CÓ `--no-owner` nhưng KHÔNG `--no-privileges` — bỏ `--no-privileges` ngày 26/8
#    sau lượt diễn tập khôi phục THẬT trên staging (§10.58).
#
#    GRANT cấp bảng của dự án này do MIGRATION Flyway cấp, không do `10-bootstrap.sh`.
#    Khi khôi phục vào một cluster MỚI:
#      · `flyway_schema_history` được nạp lại → Flyway nói "up to date, no migration
#        necessary" → migration cấp quyền KHÔNG chạy;
#      · `--no-privileges` đã tước ACL khỏi bản dump.
#    Kết quả: CSDL đầy đủ dữ liệu mà `songnhue_app` **không đọc nổi một bảng nào** —
#    app chết ở `AdminBootstrapRunner` với `ERROR: permission denied for table users`.
#    Đo trên staging 26/8.
#
#    ⛔ Đây là đường quay lui DỮ LIỆU duy nhất của hệ (không có PITR), và nó đã hỏng ở
#       đúng tình huống nó tồn tại để phục vụ.
#
#    `--no-owner` thì GIỮ: chủ sở hữu là vai trò đang chạy pg_restore
#    (`songnhue_owner`), đúng thứ ta muốn ở mọi máy.
#
# ⚠ Chú thích này để NGOÀI lệnh. Chèn nó vào giữa một lệnh nối dòng bằng `\` là cắt
#   đứt lệnh — và `bash -n` vẫn XANH vì phần còn lại vẫn hợp lệ cú pháp, chỉ là một
#   lệnh khác. Đã mắc đúng vậy một lượt, lộ ra ở `--format=custom: command not found`.
if ! PGPASSWORD="$DB_READONLY_PASSWORD" pg_dump \
        --host="$DB_HOST" --port="$DB_PORT" \
        --username="$DB_READONLY_USER" --dbname="$DB_NAME" \
        --format=custom --compress=6 --no-password \
        --no-owner \
        --file="$TARGET" 2> >(tee /tmp/songnhue-dump-err.$$ >&2); then
    ERR="$(tail -c 2000 /tmp/songnhue-dump-err.$$ 2>/dev/null || echo 'pg_dump thất bại')"
    rm -f "/tmp/songnhue-dump-err.$$"
    # Bản dump dở dang nguy hiểm hơn không có bản nào: nó trông như bản hợp lệ
    # trong danh sách file và chỉ lộ ra là rác đúng lúc cần khôi phục.
    rm -f "$TARGET"
    close_record FAILED "" "" "${ERR//\'/}"
    echo "✗ Sao lưu THẤT BẠI" >&2
    exit 1
fi
rm -f "/tmp/songnhue-dump-err.$$"

if [ ! -s "$TARGET" ]; then
    close_record FAILED "" "" "pg_dump báo thành công nhưng tệp rỗng"
    echo "✗ Tệp dump rỗng" >&2
    exit 1
fi

# -----------------------------------------------------------------------------
# Checksum: đọc LẠI từ đĩa. Băm dòng dữ liệu lúc ghi chỉ chứng minh "cái tôi
# định ghi"; đọc lại chứng minh "cái thật sự nằm trên đĩa" — mà đó mới là thứ
# sẽ được dùng để khôi phục. Đĩa đầy và ghi thiếu bị bắt đúng ở đây.
# -----------------------------------------------------------------------------
if command -v sha256sum >/dev/null 2>&1; then
    CHECKSUM="$(sha256sum "$TARGET" | awk '{print $1}')"
else
    CHECKSUM="$(shasum -a 256 "$TARGET" | awk '{print $1}')"   # macOS
fi
echo "$CHECKSUM  $FILE_NAME" > "$TARGET.sha256"

SIZE="$(wc -c < "$TARGET" | tr -d ' ')"
close_record SUCCEEDED "$SIZE" "$CHECKSUM" ""

echo "✓ $TARGET"
echo "  $(numfmt --to=iec "$SIZE" 2>/dev/null || echo "$SIZE byte")  ·  sha256 ${CHECKSUM:0:16}…"

# -----------------------------------------------------------------------------
# Dọn bản cũ — CHỈ SAU KHI lượt này đã thành công.
# Dọn trước là có một khoảng hệ thống không còn bản nào đủ mới, và nếu lượt mới
# hỏng thì khoảng đó kéo dài tới tận đêm sau.
# -----------------------------------------------------------------------------
DELETED="$(find "$BACKUP_DIR" -maxdepth 1 -name 'songnhue-*.dump*' -type f \
    -mtime "+$RETENTION_DAYS" -print -delete | wc -l | tr -d ' ')"
[ "$DELETED" -gt 0 ] && echo "  Đã xoá $DELETED tệp quá $RETENTION_DAYS ngày"

"$SCRIPT_DIR/verify-no-keys.sh" "$TARGET"
