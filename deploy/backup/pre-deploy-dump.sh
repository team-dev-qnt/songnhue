#!/usr/bin/env bash
# =============================================================================
# CHỤP CSDL NGAY TRƯỚC KHI TRIỂN KHAI — chạy TRÊN máy production (WS-11 / T11.5)
#
# ⭐ Đây là ĐIỂM QUAY LUI DUY NHẤT về dữ liệu của cả hệ thống.
#
#   Hệ này không có PITR (architecture-review.md §6.5 rủi ro #3): thứ gần nhất
#   là bản dump đêm trước. Một migration làm hỏng dữ liệu lúc 10h sáng nghĩa là
#   quay về 02:00 và mất trọn buổi làm việc của cả cơ quan. Bản chụp này bù đúng
#   khoảng đó, ở đúng lúc rủi ro cao nhất, với giá vài chục giây.
#
#   Vì vậy: script này hỏng thì LƯỢT DEPLOY PHẢI DỪNG. Nó cố ý không có nhánh
#   "cảnh báo rồi đi tiếp" nào. Deploy mà không có điểm quay lui là đánh cược
#   toàn bộ dữ liệu để đổi lấy vài phút.
#
# Gọi từ `.github/workflows/deploy-prod.yml`:
#     cd /opt/songnhue && ./backup/pre-deploy-dump.sh
#
# ⚠ KHÁC `backup.sh` ở ba điểm, và cả ba đều cố ý:
#   1. Chạy pg_dump BÊN TRONG container `postgres`, không cần cài postgresql-client
#      lên host, và phiên bản khớp tuyệt đối với máy chủ.
#   2. Đặt tên `predeploy-*` chứ không `songnhue-*`, nên vòng dọn theo hạn 30
#      ngày của `backup.sh` KHÔNG chạm tới nó. Bản chụp trước deploy phải sống
#      lâu hơn bản đêm thường: lỗi do migration có khi vài tuần sau mới lộ.
#   3. Không phụ thuộc vào ứng dụng đang khoẻ.
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
COMPOSE_FILE="${COMPOSE_FILE:-$DEPLOY_DIR/compose.prod.yml}"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"
KEEP_COUNT="${PREDEPLOY_KEEP:-10}"

[ -f "$ENV_FILE" ] || { echo "✗ Không thấy $ENV_FILE" >&2; exit 1; }
# shellcheck disable=SC1090
# ⛔ KHÔNG `source` tệp .env — nó không phải script shell. Compose cho phép giá trị
#    nhiều từ không nháy (`ROBOTS_TAG=noindex, nofollow`); shell thì gán nửa đầu rồi
#    CHẠY nửa sau như một lệnh. Đã hỏng đúng vậy, exit 127 (§10.46).
. "$SCRIPT_DIR/../lib/read-env.sh"
set -a; eval "$(doc_env "$ENV_FILE")"; set +a

: "${DB_NAME:?Thiếu DB_NAME}"
: "${DB_READONLY_PASSWORD:?Thiếu DB_READONLY_PASSWORD}"
DB_READONLY_USER="${DB_READONLY_USER:-songnhue_readonly}"
HOST_BACKUP_DIR="${BACKUP_DIR:-/var/lib/songnhue/backup}"

dc() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }

# -----------------------------------------------------------------------------
# ⚠ Kiểm CSDL còn sống TRƯỚC khi làm gì khác.
#   Không có bước này thì lượt dump hỏng vì "postgres đang khởi động lại" trông
#   giống hệt lượt hỏng vì "CSDL sắp chết" — mà hai tình huống đó cần hai phản
#   ứng hoàn toàn khác nhau.
# -----------------------------------------------------------------------------
if ! dc exec -T postgres pg_isready -U postgres -d "$DB_NAME" >/dev/null 2>&1; then
    echo "✗ Postgres không trả lời — DỪNG. Không deploy khi chưa chụp được CSDL." >&2
    exit 1
fi

STAMP="$(date +%Y%m%d-%H%M%S)"
FILE_NAME="predeploy-${DB_NAME}-${STAMP}.dump"
IN_CONTAINER="/var/lib/songnhue/backup/$FILE_NAME"
ON_HOST="$HOST_BACKUP_DIR/$FILE_NAME"

echo "→ Chụp CSDL trước triển khai → $ON_HOST"

# -Fc đã nén sẵn bằng zlib. KHÔNG gzip chồng lên — thêm một tầng nén là thêm
# một chỗ hỏng mà gần như không giảm thêm dung lượng.
if ! dc exec -T \
        -e PGPASSWORD="$DB_READONLY_PASSWORD" \
        postgres pg_dump \
            --username="$DB_READONLY_USER" --dbname="$DB_NAME" \
            --format=custom --compress=6 --no-password \
            --no-owner --no-privileges \
            --file="$IN_CONTAINER"; then
    echo "✗ pg_dump THẤT BẠI — DỪNG lượt triển khai." >&2
    # Bản dump dở dang nguy hiểm hơn không có bản nào: nó trông như bản hợp lệ
    # trong danh sách tệp và chỉ lộ ra là rác đúng lúc cần khôi phục.
    rm -f "$ON_HOST"
    exit 1
fi

[ -s "$ON_HOST" ] || {
    echo "✗ pg_dump báo thành công nhưng tệp rỗng hoặc không thấy ở host." >&2
    echo "  Kiểm tra service postgres đã gắn $HOST_BACKUP_DIR chưa (compose.prod.yml)." >&2
    exit 1
}

# Checksum đọc LẠI từ đĩa — băm cái thật sự nằm trên đĩa, không băm cái ta định
# ghi. Đĩa đầy và ghi thiếu bị bắt đúng ở đây.
CHECKSUM="$(sha256sum "$ON_HOST" | awk '{print $1}')"
echo "$CHECKSUM  $FILE_NAME" > "$ON_HOST.sha256"
SIZE="$(wc -c < "$ON_HOST" | tr -d ' ')"

# -----------------------------------------------------------------------------
# Ghi sổ đăng ký để bản chụp này HIỆN trên màn hình quản trị (M5.10) và được
# tính vào cảnh báo "quá 26 giờ". Bản dump vô hình với hệ thống thì tương đương
# không có, vì không ai biết mà dùng.
#
# 📌 Nợ nhỏ: cột `trigger_type` hiện chỉ nhận ('SCHEDULED','MANUAL','PRE_RESTORE')
#    — xem V202608161010. Ghi 'MANUAL' cho đúng ràng buộc; tên tệp `predeploy-*`
#    và `file_path` vẫn phân biệt được. Thêm 'PRE_DEPLOY' là một migration bốn
#    dòng, gộp vào lần sửa lược đồ kế tiếp cho đỡ tốn một vòng CI.
# -----------------------------------------------------------------------------
if [ -n "${DB_MIGRATION_PASSWORD:-}" ]; then
    dc exec -T -e PGPASSWORD="$DB_MIGRATION_PASSWORD" postgres psql \
        --username="${DB_MIGRATION_USER:-songnhue_owner}" --dbname="$DB_NAME" \
        --quiet --no-password --command \
        "INSERT INTO system_backups
            (file_name, file_path, status, trigger_type, finished_at, size_bytes, checksum_sha256)
         VALUES
            ('$FILE_NAME', '$ON_HOST', 'SUCCEEDED', 'MANUAL', now(), $SIZE, '$CHECKSUM');" \
        >/dev/null 2>&1 \
        || echo "  ⚠ Không ghi được sổ đăng ký — bản dump vẫn hợp lệ, nhưng màn hình quản trị sẽ không thấy nó."
fi

# Khoá AES/JWT nằm ở /opt/songnhue/keys, KHÔNG được lọt vào bản dump. Lộ backup
# cộng lộ khoá = lộ toàn bộ employee_sensitive (DoD 13d).
"$SCRIPT_DIR/verify-no-keys.sh" "$ON_HOST"

# Giữ N bản gần nhất. Dọn SAU KHI lượt này đã thành công — dọn trước là có một
# khoảng hệ thống không còn điểm quay lui nào.
mapfile -t OLD < <(find "$HOST_BACKUP_DIR" -maxdepth 1 -name 'predeploy-*.dump' -type f -printf '%T@ %p\n' \
    | sort -rn | tail -n "+$((KEEP_COUNT + 1))" | cut -d' ' -f2-)
for f in "${OLD[@]:-}"; do
    [ -n "$f" ] || continue
    rm -f "$f" "$f.sha256"
done
[ "${#OLD[@]}" -gt 0 ] && [ -n "${OLD[0]:-}" ] && echo "  Đã dọn ${#OLD[@]} bản chụp cũ (giữ $KEEP_COUNT bản gần nhất)"

echo "✓ $ON_HOST"
echo "  $(numfmt --to=iec "$SIZE" 2>/dev/null || echo "$SIZE byte")  ·  sha256 ${CHECKSUM:0:16}…"
echo "  Quay lui bằng: docs/runbook/khoi-phuc-du-lieu.md (dùng đúng tệp này)"
