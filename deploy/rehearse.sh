#!/usr/bin/env bash
# =============================================================================
# Diễn tập đường dữ liệu của lượt triển khai staging — ngay tại máy
#
# ⭐ VÌ SAO CẦN. Bảy sự cố liên tiếp (§10.42 → §10.49) đều nằm trên đường triển
#    khai, và chỗ duy nhất chúng lộ ra là **một lượt deploy thật lên VPS**. Bộ
#    kiểm ở máy không đụng tới compose, không đụng tới `minio-init`, không đụng
#    tới thứ tự khởi động — nên "xanh ở máy" không nói gì về chúng.
#
#    Script này chạy ĐÚNG `compose.staging.yml` và ĐÚNG lệnh mà CD gõ
#    (`run --rm migrator`), rồi hỏi ba câu mà chỉ hệ thật trả lời được:
#
#      1. Bucket có được tạo không?                       → §10.49
#      2. Migration seed có ghi đủ hàng không?            → §10.50
#      3. Mỗi `storage_key` trong CSDL có BYTE thật không? → chỗ hỏng CÂM
#
#    Câu 3 là câu quan trọng nhất và là câu không bài kiểm JUnit nào trả lời
#    được: hàng trong CSDL và byte trong MinIO là hai hệ thống khác nhau.
#
# ⛔ LƯỢT DIỄN TẬP NÀY KHÔNG NÓI GÌ VỀ: nginx biên · TLS · tên miền · CSP ·
#    quyền thư mục trên máy chủ · hai image giao diện · hành vi dưới tải.
#    Danh sách đầy đủ kèm lý do: `deploy/compose.rehearse.yml`.
#
# Dùng:
#   make rehearse            # dựng, kiểm, dọn
#   GIU=1 make rehearse      # giữ container lại để soi
# =============================================================================
set -euo pipefail

THU_MUC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_DT="$THU_MUC/env/rehearse.env"
ENV_COMPOSE="$THU_MUC/.env"
DU_AN="songnhue-rehearse"
GIU="${GIU:-0}"

# ⚠⚠ BA BIẾN IMAGE PHẢI EXPORT Ở ĐÂY, TRƯỚC MỌI LỆNH COMPOSE.
#
#    `compose.prod.yml` khai chúng dạng `${X:?}`, và compose nội suy TOÀN BỘ tệp
#    trước khi làm bất cứ việc gì — kể cả `down` trong bẫy dọn dẹp. Đặt sau là
#    hỏng y hệt như không đặt (§10.48). `ScriptDockerLookupTest` canh đúng thứ tự
#    này, và nó đã bắt được bản đầu của chính tệp này.
#
#    Hai image giao diện không được dựng: lượt diễn tập chỉ đi đường dữ liệu, nên
#    compose chỉ cần một chuỗi khác rỗng để nội suy.
TU_DUNG_IMAGE=0
[ -z "${APP_IMAGE:-}" ] && TU_DUNG_IMAGE=1
export APP_IMAGE="${APP_IMAGE:-songnhue-app:rehearse}"
export ADMIN_IMAGE="${ADMIN_IMAGE:-khong-dung-trong-dien-tap}"
export PUBLIC_IMAGE="${PUBLIC_IMAGE:-khong-dung-trong-dien-tap}"

dc() { docker compose --env-file "$ENV_COMPOSE" -f "$THU_MUC/compose.rehearse.yml" "$@"; }

# ---------------------------------------------------------------------------
# 0. Dọn dẹp có kỷ luật
#
# `deploy/.env` là đường mà `env_file: [.env]` của compose đọc — trên máy chủ nó
# là `/opt/songnhue/.env`. Ở máy thì nó có thể đang là tệp của ai đó, nên cất đi
# rồi trả lại, đừng ghi đè.
# ---------------------------------------------------------------------------
CAT_DI=""
don_dep() {
  local ma=$?
  if [ "$GIU" != "1" ]; then
    dc down -v --remove-orphans >/dev/null 2>&1 || true
  else
    echo "  ⓘ GIU=1 — container còn sống. Dọn tay: docker compose -p $DU_AN down -v"
  fi
  # Trả lại tệp của người ta, hoặc dọn tệp mình vừa tạo — không để lại một
  # `deploy/.env` mồ côi mang mật khẩu giả, thứ mà lượt `docker compose` sau đó
  # sẽ đọc mà không ai ngờ.
  if [ -n "$CAT_DI" ]; then
    mv -f "$CAT_DI" "$ENV_COMPOSE"
  else
    rm -f "$ENV_COMPOSE"
  fi
  exit $ma
}
trap don_dep EXIT

# ---------------------------------------------------------------------------
# 1. Tệp env diễn tập
#
# Sinh từ CHÍNH `staging.env.example` — không viết một danh sách biến thứ hai.
# Danh sách thứ hai sẽ thiếu biến mới, và lượt diễn tập sẽ hỏng vì lý do không
# liên quan gì tới thứ đang được diễn tập.
# ---------------------------------------------------------------------------
echo "→ [1/5] Tệp env diễn tập"
python3 - "$THU_MUC/env/staging.env.example" "$ENV_DT" <<'PY'
import pathlib, sys

nguon, dich = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])

# Chỉ những biến mà PHẦN ĐANG DIỄN TẬP thật sự đọc. SMTP, khoá API thuỷ văn,
# Grafana… để trống — service dùng chúng không được dựng, và điền bừa vào là tự
# tạo cảm giác "đã cấu hình xong".
GIA = {
    "DB_PASSWORD": "rehearse-not-a-secret",
    "DB_MIGRATION_PASSWORD": "rehearse-not-a-secret",
    "DB_ARCHIVER_PASSWORD": "rehearse-not-a-secret",
    "DB_READONLY_PASSWORD": "rehearse-not-a-secret",
    "POSTGRES_PASSWORD": "rehearse-not-a-secret",
    "MINIO_ROOT_USER": "rehearseroot",
    "MINIO_ROOT_PASSWORD": "rehearse-not-a-secret",
    "MINIO_ACCESS_KEY": "rehearseapp",
    "MINIO_SECRET_KEY": "rehearse-not-a-secret",
}

ra = []
for dong in nguon.read_text(encoding="utf-8").splitlines():
    t = dong.strip()
    if "=" in t and not t.startswith("#"):
        ten = t.split("=", 1)[0].strip()
        if ten in GIA:
            ra.append(f"{ten}={GIA[ten]}")
            continue
    ra.append(dong)

dich.write_text("\n".join(ra) + "\n", encoding="utf-8")
PY
echo "     ✓ $ENV_DT"

if [ -f "$ENV_COMPOSE" ]; then
  CAT_DI="$ENV_COMPOSE.truoc-dien-tap"
  mv -f "$ENV_COMPOSE" "$CAT_DI"
  echo "     ⓘ Đã cất $ENV_COMPOSE sang $CAT_DI — sẽ trả lại lúc kết thúc"
fi
cp "$ENV_DT" "$ENV_COMPOSE"

# ---------------------------------------------------------------------------
# 2. Image backend
#
# ⚠ Dựng từ mã nguồn đang có trên cây làm việc, không kéo từ GHCR: câu đang hỏi
#   là *bản mã này có triển khai được không*, và migration nằm TRONG jar.
# ---------------------------------------------------------------------------
echo "→ [2/5] Image backend"
if [ "$TU_DUNG_IMAGE" = "0" ]; then
  echo "     ⓘ Dùng APP_IMAGE gọi từ ngoài: $APP_IMAGE"
else
  docker build -q -f "$THU_MUC/docker/backend.Dockerfile" -t "$APP_IMAGE" "$THU_MUC/../backend" >/dev/null
  echo "     ✓ $APP_IMAGE (dựng từ mã nguồn trên cây làm việc)"
fi

# ---------------------------------------------------------------------------
# 3. Chạy ĐÚNG lệnh của CD
#
# `run --rm migrator` — không phải `up -d`. Cả chuỗi phụ thuộc nằm ở compose:
# postgres (healthy) → minio (healthy) → minio-init (completed) → migrator.
# Nếu chuỗi ấy sai thì bước này hỏng, và đó chính là điều cần biết.
# ---------------------------------------------------------------------------
echo "→ [3/5] Dựng nền và chạy migrator (đúng lệnh CD gõ)"
dc down -v --remove-orphans >/dev/null 2>&1 || true
dc run --rm migrator

# ---------------------------------------------------------------------------
# 4. Ba câu hỏi mà chỉ hệ thật trả lời được
# ---------------------------------------------------------------------------
echo "→ [4/5] Đối chiếu"

. "$THU_MUC/lib/read-env.sh"
eval "$(doc_env "$ENV_DT" DB_NAME DB_MIGRATION_USER DB_MIGRATION_PASSWORD \
                MINIO_BUCKET_MEDIA MINIO_ROOT_USER MINIO_ROOT_PASSWORD)"

COMPOSE_PROJECT_NAME="$DU_AN"
export COMPOSE_PROJECT_NAME
. "$THU_MUC/lib/docker-svc.sh"
CT_PG="$(container_cua postgres)"
MANG="$(mang_cua minio)"

psql_ra() {
  docker exec -i -e PGPASSWORD="$DB_MIGRATION_PASSWORD" "$CT_PG" \
    psql -tAq -U "$DB_MIGRATION_USER" -d "$DB_NAME" -c "$1" | tr -d '\r'
}

mc_chay() {
  docker run --rm --network "$MANG" --entrypoint sh minio/mc:latest -c \
    "mc alias set s3 http://minio:9000 '$MINIO_ROOT_USER' '$MINIO_ROOT_PASSWORD' >/dev/null && $1"
}

loi=0

# ---- Câu 1: bucket có tồn tại không (§10.49) ----
if mc_chay "mc ls s3/$MINIO_BUCKET_MEDIA >/dev/null"; then
  echo "     ✓ [1/3] Bucket '$MINIO_BUCKET_MEDIA' tồn tại — minio-init đã chạy"
else
  echo "     ✗ [1/3] Bucket '$MINIO_BUCKET_MEDIA' KHÔNG tồn tại — minio-init không nằm trên đường chạy" >&2
  loi=1
fi

# ---- Câu 2: migration seed đã ghi đủ hàng chưa ----
so_bai="$(psql_ra "SELECT count(*) FROM articles WHERE source LIKE 'http%'")"
so_anh="$(psql_ra "SELECT count(*) FROM attachments WHERE purpose = 'SEED_PORTAL'")"
if [ "$so_bai" = "5" ] && [ "$so_anh" = "4" ]; then
  echo "     ✓ [2/3] Seed vào CSDL: 5 bài · 4 đính kèm"
else
  echo "     ✗ [2/3] Seed KHÔNG đủ: $so_bai bài (cần 5) · $so_anh đính kèm (cần 4)" >&2
  echo "            Kiểm SEED_LOCATION trong $ENV_DT và log migrator ở trên." >&2
  loi=1
fi

# ---- Câu 3: mỗi hàng có BYTE thật không — chỗ hỏng CÂM ----
# Đây là câu duy nhất đi qua CẢ HAI hệ thống. Không câu SQL nào và không bài
# kiểm JUnit nào trả lời được nó.
thieu=0
while read -r khoa; do
  [ -z "$khoa" ] && continue
  if ! mc_chay "mc stat s3/$MINIO_BUCKET_MEDIA/$khoa >/dev/null 2>&1"; then
    echo "     ✗ CSDL nói có, MinIO không có: $khoa" >&2
    thieu=$((thieu + 1))
  fi
done < <(psql_ra "SELECT storage_key FROM attachments WHERE purpose = 'SEED_PORTAL' ORDER BY storage_key")

if [ "$so_anh" = "0" ]; then
  # ⚠ Phân biệt cho rõ: KHÔNG có khoá nào để đối chiếu KHÁC hẳn với "mọi khoá đều
  #   ra được byte". Gộp hai trạng thái ấy vào một dòng ✓ là đúng kiểu khẳng định
  #   không phân biệt được gì (luật 9).
  echo "     ✗ [3/3] Không có khoá nào để đối chiếu — câu 2 đã hỏng, câu này chưa kiểm được gì" >&2
  loi=1
elif [ "$thieu" = "0" ] && [ "$so_anh" = "4" ]; then
  echo "     ✓ [3/3] Cả 4 khoá trong CSDL đều có byte thật trong MinIO"
else
  echo "     ✗ [3/3] $thieu/$so_anh khoá không ra được byte — hàng CSDL và MinIO đang lệch nhau" >&2
  loi=1
fi

# ---------------------------------------------------------------------------
# 5. Kết luận — phải phân biệt được ĐẠT với HỎNG
# ---------------------------------------------------------------------------
echo "→ [5/5] Kết luận"
if [ "$loi" != "0" ]; then
  echo ""
  echo "  ❌ DIỄN TẬP HỎNG — đừng merge vào staging trước khi chữa." >&2
  exit 1
fi

echo ""
echo "  ✅ Đường dữ liệu của staging chạy được trên bản mã này."
echo ""
echo "  ⛔ Nhưng lượt này KHÔNG nói gì về: nginx biên · TLS · tên miền · CSP ·"
echo "     quyền thư mục trên máy chủ · hai image giao diện · hành vi dưới tải."
echo "     Lý do từng mục: deploy/compose.rehearse.yml"
