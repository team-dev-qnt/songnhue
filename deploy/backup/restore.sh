#!/usr/bin/env bash
# =============================================================================
# Khôi phục CSDL từ bản dump — ĐƯỜNG THỦ CÔNG (WS-7 / T7.5)
#
# ⚠ THAO TÁC NÀY GHI ĐÈ TOÀN BỘ DỮ LIỆU. Không hoàn tác được.
#
# Đường bình thường là nút trên màn hình M5.11 (Super Admin + mã 2FA + xác
# nhận). Script này dành cho hai tình huống mà đường kia không dùng được:
#   • Ứng dụng không khởi động nổi — và đó thường chính là lý do phải khôi phục.
#   • Khôi phục sang máy KHÁC (diễn tập trên VM-2 — T7.7).
#
#   Dùng:  make restore                             (chọn bản mới nhất)
#          ENV=staging deploy/backup/restore.sh <đường-dẫn-dump>
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ENV="${ENV:-local}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/env/$ENV.env}"

[ -f "$ENV_FILE" ] || { echo "✗ Không thấy $ENV_FILE" >&2; exit 1; }
# shellcheck disable=SC1090
# ⛔ KHÔNG `source` tệp .env — nó không phải script shell. Compose cho phép giá trị
#    nhiều từ không nháy (`ROBOTS_TAG=noindex, nofollow`); shell thì gán nửa đầu rồi
#    CHẠY nửa sau như một lệnh. Đã hỏng đúng vậy, exit 127 (§10.46).
. "$SCRIPT_DIR/../lib/read-env.sh"
set -a; eval "$(doc_env "$ENV_FILE")"; set +a

: "${DB_HOST:?}"; : "${DB_PORT:?}"; : "${DB_NAME:?}"; : "${BACKUP_DIR:?}"
: "${DB_MIGRATION_PASSWORD:?Cần mật khẩu chủ sở hữu để khôi phục}"
OWNER="${DB_MIGRATION_USER:-songnhue_owner}"

SOURCE="${1:-}"
if [ -z "$SOURCE" ]; then
    SOURCE="$(find "$BACKUP_DIR" -maxdepth 1 -name 'songnhue-*.dump' -type f \
        | sort | tail -1)"
    [ -n "$SOURCE" ] || { echo "✗ Không tìm thấy bản dump nào trong $BACKUP_DIR" >&2; exit 1; }
fi
[ -r "$SOURCE" ] || { echo "✗ Không đọc được $SOURCE" >&2; exit 1; }

# -----------------------------------------------------------------------------
# Đối chiếu checksum TRƯỚC khi đụng vào dữ liệu.
# Phát hiện bản dump hỏng SAU khi đã xoá dữ liệu hiện tại là mất cả hai.
# -----------------------------------------------------------------------------
if [ -f "$SOURCE.sha256" ]; then
    echo "→ Đối chiếu checksum"
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL="$(sha256sum "$SOURCE" | awk '{print $1}')"
    else
        ACTUAL="$(shasum -a 256 "$SOURCE" | awk '{print $1}')"
    fi
    EXPECTED="$(awk '{print $1}' < "$SOURCE.sha256")"
    if [ "$ACTUAL" != "$EXPECTED" ]; then
        echo "✗ CHECKSUM KHÔNG KHỚP — bản dump đã hỏng hoặc bị sửa. DỪNG." >&2
        exit 1
    fi
    echo "  ✓ khớp"
else
    echo "  ⚠ Không có tệp .sha256 đi kèm — không xác minh được bản dump này còn nguyên vẹn."
fi

# -----------------------------------------------------------------------------
# Xác nhận. Hỏi lại bằng cách bắt gõ TÊN CSDL chứ không phải "y" — người ta gõ
# "y" theo phản xạ, gõ đúng tên CSDL thì phải đọc dòng phía trên.
# -----------------------------------------------------------------------------
cat <<BANNER

  ⚠  KHÔI PHỤC CSDL — GHI ĐÈ TOÀN BỘ DỮ LIỆU HIỆN CÓ
      Máy chủ : $DB_HOST:$DB_PORT
      CSDL    : $DB_NAME
      Nguồn   : $SOURCE
      Môi trường: $ENV

BANNER
printf "  Gõ đúng tên CSDL (%s) để tiếp tục: " "$DB_NAME"
read -r ANSWER
[ "$ANSWER" = "$DB_NAME" ] || { echo "  Đã huỷ."; exit 1; }

# -----------------------------------------------------------------------------
# Chụp bản PRE_RESTORE — đường lùi duy nhất khi khôi phục nhầm bản.
# -----------------------------------------------------------------------------
echo "→ Chụp bản trước khi ghi đè"
ENV="$ENV" "$SCRIPT_DIR/backup.sh" || {
    echo "✗ Không chụp được bản trước khi khôi phục. DỪNG — khôi phục mà không có" >&2
    echo "  đường lùi là đánh cược toàn bộ dữ liệu đang có vào việc chọn đúng bản." >&2
    exit 1
}

# -----------------------------------------------------------------------------
# Ngắt kết nối khác. pg_restore --clean phải DROP từng đối tượng, mà DROP chờ
# vô hạn khi còn phiên khác giữ khoá. Nối vào `postgres`, không nối vào CSDL
# đích — nếu không thì chính phiên này nằm trong danh sách bị ngắt.
# -----------------------------------------------------------------------------
echo "→ Ngắt các kết nối khác tới $DB_NAME"
PGPASSWORD="$DB_MIGRATION_PASSWORD" psql \
    --host="$DB_HOST" --port="$DB_PORT" --username="$OWNER" --dbname=postgres \
    --no-password --quiet --command \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
      WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" >/dev/null

# -----------------------------------------------------------------------------
# Lọc mục lục — BẮT BUỘC khi khôi phục vào một cluster MỚI (T7.7: diễn tập sang
# máy khác, và mọi lượt khôi phục thảm hoạ thật).
#
# ⚠⚠ Ba nhóm mục sau thuộc về EXTENSION, không thuộc về ta, và `pg_restore` chạy
#    bằng `songnhue_owner` sẽ ĐỎ ở chúng — đo trên staging 26/8 (§10.58):
#
#      COMMENT - EXTENSION pg_trgm     → ERROR: must be owner of extension pg_trgm
#      TABLE DATA … spatial_ref_sys    → ERROR: permission denied for table spatial_ref_sys
#      (và ACL của các đối tượng ấy)
#
#    Cả ba đều do `CREATE EXTENSION` dựng lại rồi — `spatial_ref_sys` ở cluster mới
#    đã có đủ 8500 dòng trước khi ta nạp gì. Bỏ chúng KHÔNG mất dữ liệu.
#
# Luật lọc: bỏ mục nào có **chủ sở hữu là `postgres`** (tức do superuser tạo qua
# extension) và mọi `COMMENT - EXTENSION`. Lọc theo chủ sở hữu thay vì liệt kê tên
# bảng: liệt kê tên là một danh sách sẽ mục ngay khi thêm extension thứ tư.
# -----------------------------------------------------------------------------
TOC="$(mktemp)"
trap 'rm -f "$TOC"' EXIT
PGPASSWORD="$DB_MIGRATION_PASSWORD" pg_restore --list "$SOURCE" \
    | grep -v "COMMENT - EXTENSION" \
    | awk '$NF != "postgres"' > "$TOC"

BO="$(( $(PGPASSWORD="$DB_MIGRATION_PASSWORD" pg_restore --list "$SOURCE" | grep -c .) - $(grep -c . "$TOC") ))"
echo "→ Mục lục: bỏ $BO mục thuộc extension (do CREATE EXTENSION dựng lại)"

# ⚠ `grep -c .` chứ không `wc -l`: mục lục có dòng chú thích và dòng trống.
[ "$(grep -c . "$TOC")" -gt 100 ] || {
    echo "✗ Mục lục sau khi lọc chỉ còn $(grep -c . "$TOC") mục — bộ lọc đã ăn quá tay. DỪNG." >&2
    exit 1
}

echo "→ pg_restore"
# ⚠ KHÔNG `--no-privileges`: GRANT cấp bảng do migration Flyway cấp, mà Flyway
#   không chạy lại trên một CSDL vừa khôi phục (`flyway_schema_history` nói đã áp
#   đủ). Tước ACL khỏi bản dump là khôi phục ra một CSDL `songnhue_app` không đọc
#   nổi — app chết ở `permission denied for table users` (§10.58).
PGPASSWORD="$DB_MIGRATION_PASSWORD" pg_restore \
    --host="$DB_HOST" --port="$DB_PORT" --username="$OWNER" --dbname="$DB_NAME" \
    --no-password --clean --if-exists --no-owner \
    --use-list="$TOC" \
    --exit-on-error --single-transaction \
    "$SOURCE"

echo "✓ Khôi phục xong từ $(basename "$SOURCE")"
echo ""
echo "  Việc PHẢI làm tiếp — xem docs/runbook/khoi-phuc-du-lieu.md:"
echo "   1. Tắt chế độ bảo trì nếu đang bật, rồi KHỞI ĐỘNG LẠI ứng dụng"
echo "      (cache Caffeine của bảng settings còn giữ giá trị trước khi ghi đè)."
echo "   2. Đối chiếu số bản ghi các bảng trọng yếu với kỳ vọng."
echo "   3. Kiểm chuỗi hash nhật ký:  make db-verify-audit"
echo "   4. ⚠ ĐỌC THỬ BẰNG VAI TRÒ CỦA ỨNG DỤNG, không chỉ bằng chủ sở hữu:"
echo "        psql -U songnhue_app -d $DB_NAME -c 'SELECT count(*) FROM users'"
echo "      Khôi phục vào cluster MỚI mà thiếu GRANT thì mọi bảng đều có dữ liệu"
echo "      và app vẫn chết ngay lúc khởi động (§10.58). Chủ sở hữu luôn đọc được,"
echo "      nên hỏi bằng chủ sở hữu KHÔNG phân biệt được hai trạng thái."
