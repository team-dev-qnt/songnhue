#!/usr/bin/env bash
# =============================================================================
# KHÔI PHỤC CSDL BẰNG CÔNG CỤ BÊN TRONG CONTAINER POSTGRES
#
# ⚠ THAO TÁC NÀY GHI ĐÈ TOÀN BỘ DỮ LIỆU. Không hoàn tác được.
#
# ⛔ VÌ SAO TỆP NÀY TỒN TẠI, THAY VÌ DÙNG `restore.sh`
#
#    `restore.sh` gọi `psql` và `pg_restore` TRÊN HOST. Đo ngày 07/09/2026 trên
#    CẢ HAI máy chủ thật (VPS-1 production 27.71.16.154, VPS-2 staging
#    27.71.27.75):
#
#      · psql / pg_dump / pg_restore : KHÔNG CÓ trên host, cả hai máy
#      · DB_HOST=postgres            : chỉ phân giải được BÊN TRONG mạng docker
#      · container postgres          : `"5432/tcp": null` — không publish cổng nào
#      · $DEPLOY_DIR/env/prod.env    : không tồn tại trên host (rsync không mang
#                                      thư mục env/ sang; cấu hình ở /opt/songnhue/.env)
#
#    Bốn điều đó cộng lại: **đường khôi phục thủ công DUY NHẤT của hệ — chính là
#    thứ T7.13-a đã vá sau sự cố §10.58 — không chạy được ở đúng nơi cần nó.**
#    Nó chỉ chạy trên máy dev, tức nơi không bao giờ xảy ra thảm hoạ.
#
#    Cùng một hình dạng đã gặp nhiều lần trong dự án: một cơ chế tồn tại trong mã
#    nhưng chưa có hiệu lực ở nơi nó phải chặn.
#
# ⭐ TỆP NÀY GIỮ NGUYÊN MỌI BẢO ĐẢM CỦA `restore.sh`, chỉ đổi ĐƯỜNG TỚI CÔNG CỤ:
#    `docker exec` vào container postgres — đúng cách `pre-deploy-dump.sh` đã làm
#    và đã chạy thật trong mọi lượt CD. Phiên bản client khớp tuyệt đối với máy chủ
#    vì đó chính là máy chủ.
#
#      ① đối chiếu checksum TRƯỚC khi đụng vào dữ liệu
#      ② bắt xác nhận bằng TÊN CSDL, không phải "y"
#      ③ tự chụp bản PRE_RESTORE — đường lùi
#      ④ ngắt các kết nối khác trước khi DROP
#      ⑤ lọc mục lục theo CHỦ SỞ HỮU (bỏ mục của `postgres` do extension dựng)
#      ⑥ KHÔNG `--no-privileges` — giữ ACL, nếu không app không đọc nổi (§10.58)
#      ⑦ `--exit-on-error --single-transaction` — được ăn cả, ngã về không
#      ⑧ nghiệm thu bằng VAI TRÒ CỦA ỨNG DỤNG, không bằng chủ sở hữu
#
#   Dùng:
#     ENV_FILE=/opt/songnhue/.env XAC_NHAN=songnhue \
#       /opt/songnhue/backup/khoi-phuc-qua-container.sh <đường-dẫn-dump> [--sau <tệp.sql>]
#
#   ⚠ XAC_NHAN thay cho câu hỏi tương tác của `restore.sh`. Không phải để cho tiện:
#     `read` trên một phiên ssh không cấp tty nhận EOF và script tự huỷ — tức
#     đường khôi phục thảm hoạ hỏng đúng lúc người ta chạy nó từ xa. Bắt gõ đúng
#     tên CSDL vẫn giữ nguyên tính chất "phải đọc dòng phía trên mới gõ được".
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"

[ -f "$ENV_FILE" ] || { echo "✗ Không thấy $ENV_FILE" >&2; exit 1; }
# shellcheck disable=SC1090
. "$SCRIPT_DIR/../lib/read-env.sh"
set -a; eval "$(doc_env "$ENV_FILE")"; set +a

: "${DB_NAME:?Thiếu DB_NAME}"
: "${DB_MIGRATION_PASSWORD:?Cần mật khẩu chủ sở hữu để khôi phục}"
OWNER="${DB_MIGRATION_USER:-songnhue_owner}"
APP_ROLE="${DB_USER:-songnhue_app}"
HOST_BACKUP_DIR="${BACKUP_DIR:-/var/lib/songnhue/backup}"

SOURCE=""
SAU_SQL=""
while [ $# -gt 0 ]; do
    case "$1" in
        --sau) SAU_SQL="${2:-}"; shift 2 ;;
        *)     SOURCE="$1"; shift ;;
    esac
done
[ -n "$SOURCE" ] || { echo "✗ Thiếu đường dẫn bản dump" >&2; exit 1; }
[ -r "$SOURCE" ] || { echo "✗ Không đọc được $SOURCE" >&2; exit 1; }
[ -z "$SAU_SQL" ] || [ -r "$SAU_SQL" ] || { echo "✗ Không đọc được tệp --sau: $SAU_SQL" >&2; exit 1; }

# shellcheck disable=SC1090
. "$SCRIPT_DIR/../lib/docker-svc.sh"
CT="$(container_cua postgres)"

# psql/pg_restore chạy TRONG container. Mật khẩu đi qua biến môi trường của
# `docker exec`, không qua dòng lệnh — dòng lệnh nhìn thấy được ở `ps` của host.
#
# ⛔⛔ `< /dev/null` KHÔNG phải cho đẹp. `docker exec -i` ĐỌC stdin, và stdin của
#     script này có thể là chính khối lệnh đang được nạp (chạy qua `ssh bash -s`,
#     qua heredoc, qua đường ống). Khi ấy lệnh đầu tiên nuốt trọn phần còn lại và
#     script im lặng dừng giữa chừng — đúng §10.60, lượt CD Staging *success trọn
#     vẹn mà không container nào được thay*. Đã mắc lại đúng lỗi này HAI lần trong
#     phiên soạn tệp này. Chỗ nào cần đẩy dữ liệu vào thì dùng `trong_stdin`.
trong()       { docker exec -i -e PGPASSWORD="$DB_MIGRATION_PASSWORD" "$CT" "$@" < /dev/null; }
trong_stdin() { docker exec -i -e PGPASSWORD="$DB_MIGRATION_PASSWORD" "$CT" "$@"; }

# -----------------------------------------------------------------------------
# ⓪ Bản dump PHẢI nằm trong thư mục được bind-mount vào container, ở ĐÚNG cùng
#    đường dẫn. Đo bằng cách hỏi container, không bằng cách tin vào compose.
# -----------------------------------------------------------------------------
DUMP_ABS="$(cd "$(dirname "$SOURCE")" && pwd)/$(basename "$SOURCE")"
case "$DUMP_ABS" in
    "$HOST_BACKUP_DIR"/*) : ;;
    *) echo "✗ Bản dump phải nằm trong $HOST_BACKUP_DIR để container thấy được." >&2
       echo "  Đang ở: $DUMP_ABS" >&2; exit 1 ;;
esac
BYTE_HOST="$(wc -c < "$DUMP_ABS" | tr -d ' ')"
BYTE_CT="$(docker exec -i "$CT" sh -c "wc -c < '$DUMP_ABS' 2>/dev/null || echo 0" < /dev/null | tr -d ' ')"
echo "→ Bản dump nhìn từ hai phía"
echo "   host      : $BYTE_HOST byte"
echo "   container : $BYTE_CT byte"
[ "$BYTE_HOST" -gt 0 ] && [ "$BYTE_HOST" = "$BYTE_CT" ] || {
    echo "✗ Container KHÔNG thấy đúng tệp ấy (bind mount sai hoặc tệp rỗng). DỪNG." >&2; exit 1; }

# -----------------------------------------------------------------------------
# ① Checksum TRƯỚC khi đụng vào dữ liệu. Phát hiện dump hỏng SAU khi đã xoá dữ
#    liệu hiện tại là mất cả hai.
# -----------------------------------------------------------------------------
if [ -f "$DUMP_ABS.sha256" ]; then
    ACTUAL="$(sha256sum "$DUMP_ABS" | awk '{print $1}')"
    EXPECTED="$(awk '{print $1}' < "$DUMP_ABS.sha256")"
    echo "→ Checksum: đo ${ACTUAL:0:16}… / ghi ${EXPECTED:0:16}…"
    [ ${#ACTUAL} -eq 64 ] || { echo "✗ Không tính được checksum. DỪNG." >&2; exit 1; }
    [ "$ACTUAL" = "$EXPECTED" ] || {
        echo "✗ CHECKSUM KHÔNG KHỚP — bản dump đã hỏng hoặc bị sửa. DỪNG." >&2; exit 1; }
    echo "   ✓ khớp"
else
    echo "   ⚠ Không có .sha256 đi kèm — không xác minh được bản dump còn nguyên vẹn."
fi

# -----------------------------------------------------------------------------
# ② Xác nhận
# -----------------------------------------------------------------------------
cat <<BANNER

  ⚠  KHÔI PHỤC CSDL — GHI ĐÈ TOÀN BỘ DỮ LIỆU HIỆN CÓ
      Container : $CT
      CSDL      : $DB_NAME
      Nguồn     : $DUMP_ABS
      Sau đó chạy: ${SAU_SQL:-<không có>}

BANNER
[ "${XAC_NHAN:-}" = "$DB_NAME" ] || {
    echo "✗ Chưa xác nhận. Đặt XAC_NHAN=$DB_NAME để tiếp tục." >&2; exit 1; }

# -----------------------------------------------------------------------------
# ③ Chụp bản PRE_RESTORE — đường lùi duy nhất khi khôi phục nhầm bản.
#    ⚠ `< /dev/null`: `pre-deploy-dump.sh` có `docker exec -i` bên trong, và nó
#      sẽ NUỐT phần còn lại của stdin nếu script này đang được nạp qua heredoc
#      (đúng lỗi §10.60 — một lệnh nuốt mất nửa cuối khối triển khai).
# -----------------------------------------------------------------------------
echo "→ Chụp bản trước khi ghi đè"
ENV_FILE="$ENV_FILE" "$SCRIPT_DIR/pre-deploy-dump.sh" < /dev/null || {
    echo "✗ Không chụp được bản trước khi khôi phục. DỪNG — khôi phục mà không có" >&2
    echo "  đường lùi là đánh cược toàn bộ dữ liệu đang có vào việc chọn đúng bản." >&2
    exit 1; }

# -----------------------------------------------------------------------------
# ④ Ngắt kết nối khác. `--clean` phải DROP từng đối tượng, mà DROP chờ vô hạn khi
#    còn phiên khác giữ khoá. Nối vào `postgres`, không vào CSDL đích.
# -----------------------------------------------------------------------------
NGAT="$(trong psql -U "$OWNER" -d postgres -At -c \
    "SELECT count(*) FROM (SELECT pg_terminate_backend(pid) FROM pg_stat_activity
      WHERE datname = '$DB_NAME' AND pid <> pg_backend_pid()) t;")"
echo "→ Đã ngắt $NGAT kết nối tới $DB_NAME"

# -----------------------------------------------------------------------------
# ⑤ Lọc mục lục theo CHỦ SỞ HỮU — bỏ mục do superuser tạo qua extension.
#    Ghi tệp mục lục vào thư mục bind-mount để container đọc được cùng đường dẫn.
# -----------------------------------------------------------------------------
TOC="$HOST_BACKUP_DIR/.toc-khoi-phuc.$$"
trap 'rm -f "$TOC"' EXIT
trong pg_restore --list "$DUMP_ABS" > "$TOC.day-du"
# ⛔ BA VẾ, không phải hai. Vế giữa được thêm ngày 08/09/2026 sau khi ĐO trên bản
#    dump thật: bộ lọc cũ để LỌT 3 mục EXTENSION (postgis, pg_trgm, unaccent).
#    Dòng mục lục của extension là `2; 3079 16389 EXTENSION - postgis ` — pg_dump
#    KHÔNG ghi chủ sở hữu cho extension, nên `$NF` là TÊN extension chứ không phải
#    `postgres`, và `awk '$NF != "postgres"'` giữ nó lại. Hệ quả: pg_restore phát
#    `DROP EXTENSION IF EXISTS postgis;` → `must be owner of extension postgis`.
#    ⚠ §10.58 quy lỗi ấy cho mục `COMMENT - EXTENSION` và vá nhầm chỗ: hai trạng
#      thái khác nhau in ra cùng một câu.
grep -v "COMMENT - EXTENSION" "$TOC.day-du" \
  | grep -vE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION ' \
  | awk '$NF != "postgres"' > "$TOC"
TONG="$(grep -c . "$TOC.day-du")"
CON="$(grep -c . "$TOC")"
echo "→ Mục lục: $TONG mục → bỏ $(( TONG - CON )) mục thuộc extension → còn $CON"
rm -f "$TOC.day-du"
[ "$CON" -gt 100 ] || {
    echo "✗ Mục lục sau khi lọc chỉ còn $CON mục — bộ lọc đã ăn quá tay. DỪNG." >&2; exit 1; }

# Phép chốt của chính bộ lọc — đo trên ARTIFACT sẽ được truyền cho pg_restore.
# `|| true` bắt buộc: `grep -c` thoát 1 khi đếm 0, và `set -e` sẽ giết script.
SO_EXT="$(grep -cE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION ' "$TOC" || true)"
[ "$SO_EXT" -eq 0 ] || {
    echo "✗ Mục lục còn $SO_EXT mục EXTENSION — pg_restore sẽ phát DROP EXTENSION và đỏ. DỪNG." >&2; exit 1; }
echo "   ✓ 0 mục EXTENSION còn lại"

# Bỏ mục EXTENSION nghĩa là TIN rằng đích đã có sẵn chúng. Bộ canh phải tự nói ra
# tiền đề của mình (luật 28) thay vì để nó là giả định im lặng.
SO_CO="$(trong psql -U "$OWNER" -d "$DB_NAME" -At -c \
    "SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','unaccent','pg_trgm')")"
[ "$SO_CO" = "3" ] || {
    echo "✗ Đích chỉ có $SO_CO/3 extension (postgis, unaccent, pg_trgm)." >&2
    echo "  Bản dump đã bị lọc mục EXTENSION nên nó KHÔNG tạo lại được, và" >&2
    echo "  songnhue_owner cũng không có quyền tạo postgis. Chạy 10-bootstrap.sh trước." >&2
    exit 1; }
echo "   ✓ đích có đủ 3 extension"

# -----------------------------------------------------------------------------
# ⑥⑦ pg_restore. KHÔNG `--no-privileges`: GRANT cấp bảng do migration Flyway cấp,
#    mà Flyway không chạy lại trên CSDL vừa khôi phục. Tước ACL = app chết ở
#    `permission denied for table users` (§10.58).
# -----------------------------------------------------------------------------
# ⛔⛔ KHÔNG gọi `pg_restore --clean` thẳng vào CSDL. Lý do ĐO ĐƯỢC 08/09/2026
#    trên một bản sao đúng của production:
#
#      ERROR: cannot drop index public.hydro_readings_p202708_station_id_measured_at_idx
#             because index public.ix_hydro_readings_station_time requires it
#      Command was: DROP INDEX IF EXISTS public.hydro_readings_p202708_...;
#
#    `--clean` phát DROP INDEX / DROP CONSTRAINT cho TỪNG phân mảnh, mà chỉ mục và
#    ràng buộc của phân mảnh KHÔNG xoá lẻ được khi bảng cha còn — Postgres bắt xoá
#    ở cha. Kho có BA bảng phân mảnh: audit_logs, hydro_raw_logs, hydro_readings.
#
#    ⚠ Lỗi này CHỈ xuất hiện khi đích ĐÃ CÓ dữ liệu. Mọi lượt diễn tập của dự án
#      (§10.58, T11.3-b) đều chạy trên cluster VỪA DỰNG LẠI, tức đích rỗng — nơi
#      những câu DROP ấy là no-op. Đường hay thử thì chạy; đường dùng thật thì hỏng.
#
# ⭐ Cách làm: sinh SQL ra TỆP, ghép một khối bỏ-bảng-phân-mảnh lên TRƯỚC, rồi nạp
#    CẢ HAI trong MỘT giao dịch. Nếu tách hai giao dịch thì một lượt pg_restore
#    hỏng sẽ để lại CSDL production KHÔNG CÒN bảng phân mảnh nào.
SQL_NAP="$HOST_BACKUP_DIR/.nap-$$.sql"
trap 'rm -f "$TOC" "$SQL_NAP"' EXIT

echo "→ Sinh SQL khôi phục ra tệp"
trong pg_restore \
    --clean --if-exists --no-owner \
    --use-list="$TOC" \
    -f "$SQL_NAP.than" \
    "$DUMP_ABS"
echo "   ✓ $(grep -c . "$SQL_NAP.than") dòng SQL"

{
    printf '%s\n' \
      "DO \$\$" \
      "DECLARE r record; n int := 0;" \
      "BEGIN" \
      "    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace" \
      "              WHERE ns.nspname = 'public' AND c.relkind = 'p'" \
      "    LOOP" \
      "        EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname);" \
      "        n := n + 1;" \
      "    END LOOP;" \
      "    RAISE NOTICE 'da bo % bang phan manh truoc khi nap', n;" \
      "END \$\$;"
    cat "$SQL_NAP.than"
} > "$SQL_NAP"
rm -f "$SQL_NAP.than"

echo "→ Nạp trong MỘT giao dịch (ON_ERROR_STOP, single-transaction)"
trong_stdin psql -U "$OWNER" -d "$DB_NAME" \
    --single-transaction -v ON_ERROR_STOP=1 -q -f - < "$SQL_NAP"
echo "   ✓ nạp thoát 0"

# -----------------------------------------------------------------------------
# ⑧ Khối SQL vá sau khôi phục — chạy TRƯỚC khi ai đó bật ứng dụng lên.
# -----------------------------------------------------------------------------
if [ -n "$SAU_SQL" ]; then
    echo "→ Chạy khối vá sau khôi phục: $SAU_SQL"
    trong_stdin psql -U "$OWNER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f - < "$SAU_SQL"
    echo "   ✓ khối vá thoát 0"
fi

# -----------------------------------------------------------------------------
# ⑨ NGHIỆM THU — bằng VAI TRÒ CỦA ỨNG DỤNG, không bằng chủ sở hữu.
#    Chủ sở hữu luôn đọc được, nên hỏi bằng chủ sở hữu KHÔNG phân biệt được hai
#    trạng thái. Đây chính là phép kiểm mà §10.58 đã thiếu.
# -----------------------------------------------------------------------------
: "${DB_PASSWORD:?Thiếu DB_PASSWORD — không nghiệm thu được bằng vai trò ứng dụng}"
echo "→ Đọc thử bằng vai trò ứng dụng ($APP_ROLE)"
DOC_DUOC="$(docker exec -i -e PGPASSWORD="$DB_PASSWORD" "$CT" \
    psql -U "$APP_ROLE" -d "$DB_NAME" -At \
    -c "SELECT count(*) FROM users" < /dev/null 2>&1 || true)"
case "$DOC_DUOC" in
    ''|*[!0-9]*) echo "✗ $APP_ROLE KHÔNG đọc nổi bảng users: $DOC_DUOC" >&2
                 echo "  Đây là đúng hình dạng §10.58 — CSDL đầy dữ liệu mà app chết lúc khởi động." >&2
                 exit 1 ;;
    *) echo "   ✓ $APP_ROLE đọc được users: $DOC_DUOC hàng" ;;
esac

echo ""
echo "✓ Khôi phục xong từ $(basename "$DUMP_ABS")"
echo "  Việc PHẢI làm tiếp:"
echo "   1. Bật lại ứng dụng (cache Caffeine của bảng settings còn giữ giá trị cũ)."
echo "   2. Đối chiếu số bản ghi các bảng trọng yếu với kỳ vọng."
echo "   3. Kiểm chuỗi hash nhật ký: SELECT * FROM core_verify_audit_chain();"
