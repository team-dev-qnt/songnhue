#!/usr/bin/env bash
# =============================================================================
# ĐO trạng thái lược đồ — một chuỗi MỘT DÒNG phân biệt "migration đã chạy" với
# "migration chưa chạy" (T11.99, CLAUDE.md luật 37).
#
# ⭐ VÌ SAO TỆP NÀY TỒN TẠI
#
#   Nhánh quay lui của `deploy.yml` trước nay in KHẲNG ĐỊNH vô điều kiện:
#       "⛔ NHƯNG lược đồ CSDL vẫn đang ở trạng thái sau migration của lượt hỏng."
#   rồi mới thêm một câu điều kiện. Lượt `36238573202` (26/09/2026) chứng minh
#   câu ấy SAI: `docker compose pull` đỏ TRƯỚC `compose run --rm migrator`, nên
#   `set -e` cắt script và CSDL ⛔ bị chạm một byte nào.
#
#   Và nó sai về phía ĐẮT NHẤT: câu ấy đẩy người trực sang khôi phục CSDL —
#   thao tác PHÁ HUỶ nhất của cả hệ, ở đây ⛔ chữa được gì mà XOÁ MẤT dữ liệu
#   sinh ra sau bản chụp. Cùng hình dạng §11.27, và khối chú thích T63.19 nằm
#   NGAY DƯỚI bước quay lui đã dặn đúng chữ "⛔⛔ ĐỪNG ĐOÁN NGUYÊN NHÂN Ở ĐÂY".
#
#   ⇒ Luật 37: in CÁC KHẢ NĂNG kèm PHÉP ĐO tách chúng. Tệp này là phép đo ấy.
#
# ⭐ HÌNH DẠNG GIÁ TRỊ — `<số hàng>:<đỉnh version>:<số hàng hỏng>`, ví dụ
#   `92:202609221098:0`. Trường ĐẦU là thứ phân xử: `migrator` chạy được một
#   migration nào thì `flyway_schema_history` có thêm hàng, kể cả hàng hỏng
#   (`success = false`) — mà hàng hỏng chính là ca lược đồ có thể đã đổi MỘT
#   PHẦN, tức ca nguy hiểm nhất, nên nó phải ĐẾM CHỨ ⛔ LỌC BỎ.
#
#   Bảng chưa tồn tại (máy trắng, lượt deploy đầu) là một trạng thái THỨ BA
#   biểu diễn được: `chua-co-bang`. ⛔ để nó rơi vào nhánh lỗi — hai câu
#   "CSDL trống trơn" và "⛔ hỏi được CSDL" dẫn tới hai việc khác hẳn nhau.
#
# ⚠ Tệp này là phép ĐO CHẨN ĐOÁN, ⛔ phải một cổng: nơi gọi ⛔ được để nó làm
#   đỏ lượt deploy. Đo hỏng ⇒ bước quay lui in câu THỨ BA ("⛔ đo được") chứ
#   ⛔ im lặng và cũng ⛔ đoán (luật 9 — ba trạng thái, ba câu đọc khác nhau).
#
# ⚠ Chạy TRÊN máy chủ, qua `docker exec` vào container postgres — host ⛔ có
#   `psql` (CLAUDE.md, bảng runbook). Cách chạm CSDL chép ĐÚNG
#   `pre-deploy-dump.sh`: `read-env.sh` + `docker-svc.sh`, ⛔ `docker compose`
#   (§10.48: lệnh compose ngoài lượt triển khai thiếu biến `*_IMAGE` ⇒ hỏng, và
#   `pre-deploy-dump.sh` đã in "✗ Postgres không trả lời" trên một CSDL khoẻ).
#
# Dùng:
#     cd /opt/songnhue && ./backup/moc-luoc-do.sh
#     → MOC_LUOC_DO=92:202609221098:0
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(dirname "$SCRIPT_DIR")"
ENV_FILE="${ENV_FILE:-$DEPLOY_DIR/.env}"

[ -f "$ENV_FILE" ] || { echo "✗ Không thấy $ENV_FILE" >&2; exit 1; }

# shellcheck disable=SC1090
# ⛔ KHÔNG `source` thẳng tệp .env — nó ⛔ phải script shell (§10.46).
. "$SCRIPT_DIR/../lib/read-env.sh"
set -a; eval "$(doc_env "$ENV_FILE")"; set +a

: "${DB_NAME:?Thiếu DB_NAME}"

# shellcheck disable=SC1090
. "$SCRIPT_DIR/../lib/docker-svc.sh"
CT_POSTGRES="$(container_cua postgres)"

if ! docker exec -i "$CT_POSTGRES" pg_isready -U postgres -d "$DB_NAME" >/dev/null 2>&1; then
    echo "✗ Postgres ⛔ trả lời — ⛔ đo được lược đồ." >&2
    exit 1
fi

# ⚠ MỖI câu MỘT dòng, và `QuayLuiDoLuocDoTest` TRÍCH NGUYÊN VĂN hai dòng này rồi
#   chạy chúng trên Postgres thật. Câu hỏi chỉ được có MỘT bản (luật 14): một bài
#   kiểm chép lại câu SQL là một bài kiểm canh chính nó (T51.15).
#
# ⛔⛔ VÌ SAO HAI CÂU CHỨ ⛔ MỘT `CASE`. Bản đầu gộp thành
#     SELECT CASE WHEN to_regclass('…') IS NULL THEN 'chua-co-bang' ELSE (SELECT … FROM …) END
#   và nó ⛔ CHẠY: PostgreSQL phân giải tên bảng lúc **PHÂN TÍCH**, ⛔ lúc thực thi,
#   nên nhánh `ELSE` vẫn làm cả câu ném `relation … does not exist` đúng trong ca
#   nó sinh ra để đỡ. Một vế bảo vệ đọc y như nó chạy mà ⛔ chạy — `QuayLuiDoLuocDoTest`
#   bắt được ở lượt chạy ĐẦU. Tách hai câu thì vế thứ hai chỉ được gửi đi khi vế
#   thứ nhất đã trả lời `t`.
CAU_CO_BANG="SELECT to_regclass('public.flyway_schema_history') IS NOT NULL"
CAU_DO="SELECT count(*) || ':' || coalesce(max(version), '-') || ':' || count(*) FILTER (WHERE NOT success) FROM public.flyway_schema_history"

hoi() { docker exec -i "$CT_POSTGRES" psql -U postgres -d "$DB_NAME" -At -c "$1" | tr -d ' \r'; }

if [ "$(hoi "$CAU_CO_BANG")" = "t" ]; then
    MOC="$(hoi "$CAU_DO")"
else
    # Máy trắng / lượt deploy đầu. ⛔ để nó rơi vào nhánh lỗi: "CSDL chưa có bảng
    # nào" và "⛔ hỏi được CSDL" dẫn tới hai việc khác hẳn nhau (luật 9).
    MOC="chua-co-bang"
fi

# Chuỗi rỗng ⇒ truy vấn ⛔ trả gì. Im lặng ở đây là để nơi gọi so hai chuỗi
# rỗng với nhau rồi kết luận "lược đồ y nguyên" — một câu xanh giả về đúng thứ
# nguy hiểm nhất (luật 9).
[ -n "$MOC" ] || { echo "✗ Truy vấn flyway_schema_history trả RỖNG." >&2; exit 1; }

echo "MOC_LUOC_DO=$MOC"
