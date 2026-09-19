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

# ⛔ Tệp SQL sinh ra bên dưới mang TOÀN BỘ CSDL dạng thuần — gồm `password_hash` và
#    `user_totp.secret_encrypted`. Umask mặc định cho ra 644, tức mọi user trên máy
#    đọc được (đo 08/09 ở `khoi-phuc-qua-container.sh`).
umask 077

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
# Ngắt kết nối khác. `--clean` phải DROP từng đối tượng, mà DROP chờ vô hạn khi
# còn phiên khác giữ khoá. Nối vào `postgres`, không nối vào CSDL đích — nếu
# không thì chính phiên này nằm trong danh sách bị ngắt.
# -----------------------------------------------------------------------------
echo "→ Ngắt các kết nối khác tới $DB_NAME"
PGPASSWORD="$DB_MIGRATION_PASSWORD" psql \
    --host="$DB_HOST" --port="$DB_PORT" --username="$OWNER" --dbname=postgres \
    --no-password --quiet --command \
    "SELECT pg_terminate_backend(pid) FROM pg_stat_activity
      WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid();" >/dev/null

# -----------------------------------------------------------------------------
# Lọc mục lục — BA vế, cùng thứ tự với `khoi-phuc-qua-container.sh` và
# `KeHoachKhoiPhuc.locMucLuc` (nút M5.11):
#
#   ① bỏ `COMMENT - EXTENSION …`  → ERROR: must be owner of extension pg_trgm
#   ② bỏ chính dòng `EXTENSION`   → ERROR: must be owner of extension postgis
#   ③ bỏ mục có chủ sở hữu `postgres` (spatial_ref_sys và ACL của nó)
#
# ⚠ Vế ② thêm ngày 19/09/2026 (T68.3). Bản cũ chỉ có ① và ③ — đúng bộ lọc mà
#   script container đã phải vá ngày 08/09 sau khi ĐO trên bản dump thật: pg_dump
#   KHÔNG ghi chủ sở hữu cho extension, nên `$NF` của dòng ấy là TÊN extension và
#   `awk '$NF != "postgres"'` giữ nó lại. §10.58 quy lỗi ấy cho mục COMMENT và
#   vá nhầm chỗ: hai trạng thái khác nhau in ra cùng một câu.
#
# Cả ba nhóm đều do `CREATE EXTENSION` dựng lại rồi — bỏ chúng KHÔNG mất dữ liệu.
# Lọc theo chủ sở hữu thay vì liệt kê tên bảng: liệt kê tên là một danh sách sẽ
# mục ngay khi thêm extension thứ tư.
# -----------------------------------------------------------------------------
TOC="$(mktemp)"
SQL_THAN="$(mktemp)"
SQL_NAP="$(mktemp)"
# Tệp SQL mang TOÀN BỘ CSDL dạng thuần (gồm `password_hash`) ⇒ umask 077 ở đầu
# tệp + xoá khi thoát, kể cả thoát vì lỗi.
trap 'rm -f "$TOC" "$TOC.day-du" "$SQL_THAN" "$SQL_NAP"' EXIT

pg_restore --list "$SOURCE" > "$TOC.day-du"
grep -v "COMMENT - EXTENSION" "$TOC.day-du" \
  | grep -vE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION ' \
  | awk '$NF != "postgres"' > "$TOC"

# ⚠ `grep -c .` chứ không `wc -l`: mục lục có dòng chú thích và dòng trống.
TONG="$(grep -c . "$TOC.day-du")"
CON="$(grep -c . "$TOC")"
echo "→ Mục lục: $TONG mục → bỏ $(( TONG - CON )) mục thuộc extension → còn $CON"
[ "$CON" -gt 100 ] || {
    echo "✗ Mục lục sau khi lọc chỉ còn $CON mục — bộ lọc đã ăn quá tay. DỪNG." >&2
    exit 1
}

# Phép chốt của chính bộ lọc — đo trên THỨ sẽ được truyền cho pg_restore.
# `|| true` bắt buộc: `grep -c` thoát 1 khi đếm 0, và `set -e` sẽ giết script.
SO_EXT="$(grep -cE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION ' "$TOC" || true)"
[ "$SO_EXT" -eq 0 ] || {
    echo "✗ Mục lục còn $SO_EXT mục EXTENSION — pg_restore sẽ phát DROP EXTENSION và đỏ. DỪNG." >&2
    exit 1
}

# Bỏ mục EXTENSION nghĩa là TIN rằng đích đã có sẵn chúng — tiền đề phải ĐO.
SO_CO="$(PGPASSWORD="$DB_MIGRATION_PASSWORD" psql \
    --host="$DB_HOST" --port="$DB_PORT" --username="$OWNER" --dbname="$DB_NAME" \
    --no-password -At -c \
    "SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','unaccent','pg_trgm')")"
[ "$SO_CO" = "3" ] || {
    echo "✗ Đích chỉ có $SO_CO/3 extension (postgis, unaccent, pg_trgm) — bản dump đã lọc" >&2
    echo "  mục EXTENSION nên KHÔNG tạo lại được chúng. Chạy 10-bootstrap.sh trước." >&2
    exit 1
}

# -----------------------------------------------------------------------------
# ⛔⛔ KHÔNG `pg_restore --clean` thẳng vào CSDL — sinh SQL ra TỆP, ghép khối
#    `truoc-khi-nap.sql` lên TRƯỚC, rồi nạp CẢ HAI bằng psql trong MỘT giao dịch.
#
#    Khối ấy làm hai việc, cả hai chỉ lộ ra khi đích ĐÃ CÓ dữ liệu — tức đúng lúc
#    khôi phục thật, không phải lúc diễn tập trên cluster vừa dựng lại:
#      · bỏ bảng phân mảnh — `--clean` phát DROP INDEX cho từng phân mảnh, mà chỉ
#        mục phân mảnh không xoá lẻ được khi bảng cha còn (§10.80);
#      · gỡ quyền MẶC ĐỊNH của vai trò đang nạp — bảng dựng lại nhận
#        `ALTER DEFAULT PRIVILEGES` của đích (arwd cho songnhue_app), mà ACL của bản
#        dump chỉ GRANT, không REVOKE ⇒ đo 19/09: 72 quyền thừa trên đúng các bảng
#        append-only (audit_logs, hydro_raw_logs, security_events …).
#
# ⚠ KHÔNG `--no-privileges`: GRANT cấp bảng do migration Flyway cấp, mà Flyway
#   không chạy lại trên một CSDL vừa khôi phục (`flyway_schema_history` nói đã áp
#   đủ). Tước ACL khỏi bản dump là khôi phục ra một CSDL `songnhue_app` không đọc
#   nổi — app chết ở `permission denied for table users` (§10.58).
# -----------------------------------------------------------------------------
TRUOC_NAP="$SCRIPT_DIR/truoc-khi-nap.sql"
[ -r "$TRUOC_NAP" ] || { echo "✗ Thiếu $TRUOC_NAP — DỪNG trước khi đụng vào dữ liệu." >&2; exit 1; }

echo "→ Sinh SQL khôi phục ra tệp"
pg_restore --clean --if-exists --no-owner --use-list="$TOC" --file="$SQL_THAN" "$SOURCE"
cat "$TRUOC_NAP" "$SQL_THAN" > "$SQL_NAP"
echo "   ✓ $(grep -c . "$SQL_NAP") dòng SQL"

echo "→ Nạp trong MỘT giao dịch (ON_ERROR_STOP, single-transaction)"
PGPASSWORD="$DB_MIGRATION_PASSWORD" psql \
    --host="$DB_HOST" --port="$DB_PORT" --username="$OWNER" --dbname="$DB_NAME" \
    --no-password --single-transaction -v ON_ERROR_STOP=1 -q -f "$SQL_NAP"

# -----------------------------------------------------------------------------
# NGHIỆM THU — bằng VAI TRÒ CỦA ỨNG DỤNG, không bằng chủ sở hữu. Chủ sở hữu luôn
# đọc được, nên hỏi bằng chủ sở hữu KHÔNG phân biệt được hai trạng thái (§10.58).
# Và hỏi luôn nó có SỬA được nhật ký kiểm toán không — lỗi quyền mặc định ở trên
# không in ra một dòng lỗi nào.
# -----------------------------------------------------------------------------
APP_ROLE="${DB_USER:-songnhue_app}"
: "${DB_PASSWORD:?Thiếu DB_PASSWORD — không nghiệm thu được bằng vai trò ứng dụng}"
echo "→ Đọc thử bằng vai trò ứng dụng ($APP_ROLE)"
KQ="$(PGPASSWORD="$DB_PASSWORD" psql \
    --host="$DB_HOST" --port="$DB_PORT" --username="$APP_ROLE" --dbname="$DB_NAME" \
    --no-password -At -c \
    "SELECT count(*) || ' ' || CASE WHEN has_table_privilege(current_user, 'public.audit_logs', 'UPDATE')
              THEN 'sua-duoc' ELSE 'chi-ghi-them' END FROM users" 2>&1 || true)"
case "$KQ" in
    # ⚠ Nhãn tường minh, ⛔ `t`/`f`: `boolean || text` ra `true`/`false` chứ ⛔ phải
    #   dạng hiển thị của psql — mẫu `* f)` sẽ ⛔ bao giờ khớp và mọi lượt đều đỏ giả.
    *[0-9]\ chi-ghi-them) echo "   ✓ $APP_ROLE đọc được users (${KQ% chi-ghi-them} hàng) và KHÔNG sửa được audit_logs" ;;
    *[0-9]\ sua-duoc) echo "✗ $APP_ROLE SỬA được audit_logs — quyền append-only đã bị hạ. Dữ liệu ĐÃ nạp." >&2
                      echo "  Tái khẳng định REVOKE theo V202608131006 trước khi mở lại hệ." >&2
                      exit 1 ;;
    *) echo "✗ $APP_ROLE KHÔNG đọc nổi bảng users: $KQ" >&2
       echo "  Đây là đúng hình dạng §10.58 — CSDL đầy dữ liệu mà app chết lúc khởi động." >&2
       exit 1 ;;
esac

echo "✓ Khôi phục xong từ $(basename "$SOURCE")"
echo ""
echo "  Việc PHẢI làm tiếp — xem docs/runbook/khoi-phuc-du-lieu.md:"
echo "   1. Tắt chế độ bảo trì nếu đang bật, rồi KHỞI ĐỘNG LẠI ứng dụng"
echo "      (cache Caffeine của bảng settings còn giữ giá trị trước khi ghi đè)."
echo "   2. Đối chiếu số bản ghi các bảng trọng yếu với kỳ vọng."
echo "   3. Kiểm chuỗi hash nhật ký:  make db-verify-audit"
