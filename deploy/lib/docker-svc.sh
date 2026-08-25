# =============================================================================
# Tìm container của một service compose mà KHÔNG đi qua `docker compose`
#
# ⛔ VÌ SAO KHÔNG DÙNG `docker compose ps` / `docker compose exec`:
#
#    Compose phải NỘI SUY TOÀN BỘ tệp trước khi trả lời bất cứ câu hỏi nào —
#    kể cả một câu chỉ đọc như `ps -q minio`. Mà `compose.prod.yml` khai ba tag
#    image ở dạng bắt buộc:
#
#        image: ${APP_IMAGE:?Thiếu APP_IMAGE - workflow deploy phải export biến này}
#        image: ${ADMIN_IMAGE:?Thiếu ADMIN_IMAGE}
#        image: ${PUBLIC_IMAGE:?Thiếu PUBLIC_IMAGE}
#
#    Ba biến ấy CỐ Ý không nằm trong `.env` — workflow triển khai `export` chúng
#    ngay trước khi gọi compose, vì ghim một phiên bản image vào đĩa máy chủ là
#    đúng thứ luồng đề bạt tránh (xem MIEN_TRU trong ComposeEnvCompletenessTest).
#
#    Hệ quả: mọi script chạy NGOÀI lượt triển khai đều không có chúng, và mọi
#    lệnh compose trong script đó hỏng — kể cả lệnh chỉ đọc.
#
# ⚠⚠ Hai chỗ đã trả giá, và cả hai đều báo SAI nguyên nhân (§10.48):
#
#    `seed.sh`            `dc ps -q minio` hỏng → chuỗi rỗng → `--network ""` →
#                         `docker: no name set for network`, exit 125.
#
#    `pre-deploy-dump.sh` `dc exec -T postgres pg_isready` hỏng → script in
#                         **"✗ Postgres không trả lời — DỪNG"** rồi thoát 1.
#                         CSDL hoàn toàn khoẻ. Và đây là bản chụp trước triển
#                         khai — điểm quay lui DUY NHẤT khi migration làm hỏng
#                         dữ liệu, vì dự án cố ý không có PITR (§6.5).
#
# Nhãn `com.docker.compose.*` do chính compose gắn lên container lúc tạo, nên
# hỏi bằng nhãn là hỏi đúng thứ đang chạy — không phải hỏi tệp mô tả nó.
# =============================================================================

# Tên dự án compose: `name: songnhue` ở đầu compose.prod.yml / compose.staging.yml
DU_AN="${COMPOSE_PROJECT_NAME:-songnhue}"

# container_cua <tên-service> — in ra id container đang chạy, hoặc dừng hẳn.
#
# ⚠ Gán vào biến TRƯỚC khi dùng (`CT="$(container_cua postgres)"`), đừng nhét
#   thẳng vào `if ! …`: trong ngữ cảnh điều kiện, `set -e` bị tắt, nên lượt hỏng
#   biến thành chuỗi rỗng và đi tiếp — đúng cách `seed.sh` sinh ra
#   `--network ""`.
container_cua() {
  local dich_vu="$1" id
  id="$(docker ps -q \
          --filter "label=com.docker.compose.project=$DU_AN" \
          --filter "label=com.docker.compose.service=$dich_vu" | head -1)"
  if [ -z "$id" ]; then
    echo "✗ Không thấy container ĐANG CHẠY của service '$dich_vu' (dự án '$DU_AN')." >&2
    echo "  Kiểm bằng: docker ps --filter label=com.docker.compose.project=$DU_AN" >&2
    echo "  Nếu dự án mang tên khác, đặt COMPOSE_PROJECT_NAME." >&2
    return 1
  fi
  printf '%s' "$id"
}

# mang_cua <tên-service> — in ra tên mạng đầu tiên của container ấy.
mang_cua() {
  local id ten
  id="$(container_cua "$1")" || return 1
  ten="$(docker inspect -f '{{range $k,$v := .NetworkSettings.Networks}}{{$k}}{{"\n"}}{{end}}' "$id" | head -1)"
  if [ -z "$ten" ]; then
    echo "✗ Container của '$1' không nối vào mạng nào." >&2
    return 1
  fi
  printf '%s' "$ten"
}
