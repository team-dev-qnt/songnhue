#!/usr/bin/env bash
# =============================================================================
# Nạp nội dung khởi tạo cho cổng thông tin — ảnh vào MinIO, dữ liệu vào CSDL.
#
# ⛔ ĐÂY KHÔNG PHẢI MIGRATION, và đó là chủ ý.
#
#    Flyway chạy ở MỌI môi trường, một chiều, không hỏi ai. Còn bộ này là 5 bài
#    SAO CHÉP NGUYÊN VĂN TỪ BÁO NGOÀI, chỉ để staging có nội dung thật mà đo.
#    Đưa chúng vào chuỗi migration nghĩa là production tự đăng lại bài có bản
#    quyền của người khác, im lặng, và không ai bấm nút nào cả.
#
#    CLAUDE.md: "⛔ Cấm seed dữ liệu 'cho đẹp demo'". Một script phải gõ tay,
#    có tham số, in ra mình sắp ghi gì — thoả điều đó; một migration thì không.
#
# Idempotent: chạy lại bao nhiêu lần cũng ra cùng một trạng thái.
#
# Dùng:
#   deploy/seed/seed.sh --dry-run       # in ra sẽ làm gì, không ghi gì
#   deploy/seed/seed.sh                 # nạp thật
# =============================================================================
set -euo pipefail

THU_MUC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="${ENV_FILE:-/opt/songnhue/.env}"
COMPOSE="${COMPOSE:-compose.staging.yml}"
THU=false

for tham_so in "$@"; do
  case "$tham_so" in
    --dry-run) THU=true ;;
    *) echo "Tham số lạ: $tham_so" >&2; exit 2 ;;
  esac
done

[ -f "$ENV_FILE" ] || { echo "✗ Không thấy $ENV_FILE — đặt ENV_FILE nếu nó nằm chỗ khác" >&2; exit 1; }

# ---------------------------------------------------------------------------
# ⛔ KHÔNG `source` tệp .env — nó KHÔNG phải script shell.
#
#    Bản đầu làm `set -a; . "$ENV_FILE"; set +a` và hỏng ngay lượt chạy thử đầu
#    tiên trên staging:
#
#        /opt/songnhue/.env: line 87: nofollow: command not found   (exit 127)
#
#    Dòng thủ phạm hoàn toàn HỢP LỆ với Docker Compose:
#
#        ROBOTS_TAG=noindex, nofollow
#
#    Compose đọc nó thành chuỗi `noindex, nofollow`. Shell thì gán
#    `ROBOTS_TAG=noindex,` rồi CHẠY `nofollow` như một lệnh. Hai bộ phân tích
#    khác nhau trên cùng một tệp — và tệp ấy viết cho bộ kia, không phải cho ta.
#
#    Nên đọc bằng đúng luật của Compose: bỏ dòng trống và dòng chú thích, cho
#    phép tiền tố `export`, tôn trọng nháy đơn/nháy kép, và chỉ cắt chú thích
#    cuối dòng khi có KHOẢNG TRẮNG trước `#` — để mật khẩu chứa `#` không bị
#    cắt mất đuôi (`MAT_KHAU=abc#def` giữ nguyên).
#
#    `shlex.quote` ở đầu ra làm giá trị an toàn tuyệt đối khi đi qua `eval`:
#    mật khẩu chứa dấu cách, `$`, nháy hay `;` đều về đúng nguyên trạng.
# ---------------------------------------------------------------------------
. "$THU_MUC/../lib/read-env.sh"

eval "$(doc_env "$ENV_FILE" MINIO_BUCKET_MEDIA MINIO_ROOT_USER MINIO_ROOT_PASSWORD \
                DB_NAME DB_MIGRATION_USER DB_MIGRATION_PASSWORD)"

: "${MINIO_BUCKET_MEDIA:?Thiếu MINIO_BUCKET_MEDIA}"
: "${MINIO_ROOT_USER:?Thiếu MINIO_ROOT_USER}"
: "${MINIO_ROOT_PASSWORD:?Thiếu MINIO_ROOT_PASSWORD}"
: "${DB_NAME:?Thiếu DB_NAME}"
: "${DB_MIGRATION_USER:?Thiếu DB_MIGRATION_USER}"
: "${DB_MIGRATION_PASSWORD:?Thiếu DB_MIGRATION_PASSWORD}"

dc() { docker compose --env-file "$ENV_FILE" -f "$COMPOSE" "$@"; }

echo "  Môi trường : $ENV_FILE  ($COMPOSE)"
echo "  Bucket     : $MINIO_BUCKET_MEDIA"
echo "  Nội dung  : 4 ảnh + 5 bài sao chép từ báo ngoài (chỉ staging)"
$THU && echo "  ⓘ Chạy thử — không ghi gì" && echo

# ---------------------------------------------------------------------------
# 1. Ảnh → MinIO
#
# ⚠ Đẩy qua `mc` trong một container dùng chung mạng compose, KHÔNG qua tên miền
#   công khai: đường công khai đi qua nginx và chữ ký SigV4 ký cả tên máy, nên
#   một lượt đẩy "cho tiện" từ ngoài vào sẽ hỏng ở đúng chỗ khó đoán nhất.
# ---------------------------------------------------------------------------
echo "→ [1/3] Đẩy ảnh lên MinIO"
python3 - "$THU_MUC/images.json" <<'PY' | while IFS='|' read -r tep khoa; do
import json, sys
for r in json.load(open(sys.argv[1])):
    print(f"{r['file']}|{r['storage_key']}")
PY
  if $THU; then
    echo "     [thử] $tep → s3/$MINIO_BUCKET_MEDIA/$khoa"
    continue
  fi
  docker run --rm --network "$(dc ps -q minio | head -1 | xargs -r docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{end}}')" \
    -v "$THU_MUC/images:/seed:ro" --entrypoint sh minio/mc:latest -c "
      mc alias set s3 http://minio:9000 '$MINIO_ROOT_USER' '$MINIO_ROOT_PASSWORD' >/dev/null &&
      mc cp --quiet /seed/'$tep' s3/'$MINIO_BUCKET_MEDIA'/'$khoa'" >/dev/null
  echo "     ✓ $tep → $khoa"
done

# ---------------------------------------------------------------------------
# 2. Dữ liệu → CSDL
#
# Chạy bằng vai trò CHỦ SỞ HỮU lược đồ, không phải vai trò ứng dụng: `songnhue_app`
# cố ý không có quyền ghi vài bảng, và seed thì cần.
# ---------------------------------------------------------------------------
echo "→ [2/3] Nạp dữ liệu vào CSDL"
TEP=(01-attachments.sql 02-articles.sql)

for f in "${TEP[@]}"; do
  if $THU; then echo "     [thử] $f"; continue; fi
  dc exec -T -e PGPASSWORD="$DB_MIGRATION_PASSWORD" postgres \
     psql -v ON_ERROR_STOP=1 -q -U "$DB_MIGRATION_USER" -d "$DB_NAME" < "$THU_MUC/$f"
  echo "     ✓ $f"
done

# ---------------------------------------------------------------------------
# 3. Đối chiếu — seed nào không tự kiểm thì chỉ là một đống lệnh INSERT
# ---------------------------------------------------------------------------
echo "→ [3/3] Đối chiếu"
$THU && { echo "     [thử] bỏ qua"; exit 0; }

dc exec -T -e PGPASSWORD="$DB_MIGRATION_PASSWORD" postgres \
   psql -q -U "$DB_MIGRATION_USER" -d "$DB_NAME" <<'SQL'
\pset border 2
SELECT
  (SELECT count(*) FROM attachments WHERE purpose = 'SEED_PORTAL')          AS anh,
  (SELECT count(*) FROM articles   WHERE source LIKE 'http%')               AS bai_seed,
  (SELECT count(*) FROM articles   WHERE source LIKE 'http%'
     AND published_version_id IS NOT NULL)                                  AS co_ban_dang,
  (SELECT count(*) FROM article_versions v JOIN articles a ON a.id = v.article_id
     WHERE a.source LIKE 'http%' AND v.cover_attachment_public_id IS NOT NULL) AS co_anh_bia,
  (SELECT count(*) FROM article_versions WHERE content LIKE '%/images/%')    AS con_duong_dan_cung;
SQL

echo
echo "  ⚠ Ba phép kiểm CUỐI CÙNG phải làm bằng trình duyệt, không bằng SQL:"
echo "     1. Ảnh ra được byte:  curl -sI \"\$BASE_URL/api/v1/public/files/<public_id>\" → 200 + image/jpeg"
echo "     2. Danh sách bài có thumbnail — cột đọc là article_versions.cover_attachment_public_id"
echo "     3. Mở một bài có ảnh giữa thân, xem ảnh có hiện không"
echo
echo "  Số ở bảng trên chỉ chứng minh CSDL có hàng. Nó không chứng minh MinIO có byte —"
echo "  hai thứ đó lệch nhau là hỏng câm, và đó chính là điều phép kiểm 1 bắt được."
