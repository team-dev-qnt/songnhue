#!/usr/bin/env bash
# =============================================================================
# GIA HẠN CHỨNG CHỈ TLS — chạy từ cron hằng tuần trên máy chủ
#
# ⛔ VÌ SAO KHÔNG DÙNG `docker compose`, dù compose có sẵn service `certbot`
#
#    Đo ngày 08/09/2026 trên VPS-1 production, chạy ĐÚNG dòng cron đang cài:
#
#      $ docker compose --env-file .env -f compose.prod.yml --profile certbot \
#            run --rm certbot renew --webroot -w /var/www/certbot --dry-run
#      error while interpolating services.app.image: required variable APP_IMAGE
#            is missing a value: Thiếu APP_IMAGE - workflow deploy phải export biến này
#      MÃ THOÁT = 1
#
#    Compose NỘI SUY TOÀN BỘ tệp trước khi trả lời bất cứ câu hỏi nào — kể cả một
#    lệnh chỉ đụng tới service `certbot`. Ba biến `*_IMAGE` cố ý KHÔNG nằm trong
#    `.env` (xem `deploy/lib/docker-svc.sh`), nên mọi lệnh compose chạy NGOÀI lượt
#    triển khai đều hỏng. Cron này vì thế **chưa bao giờ gia hạn được gì**, và
#    câu `docker compose exec nginx -s reload` ở vế sau cũng hỏng y hệt.
#
#    Đây là lần thứ BA cùng một lỗi trong dự án (`seed.sh`, `pre-deploy-dump.sh`,
#    §10.48) — và là lần đầu nó nằm trên đường giữ cho HTTPS còn sống. Hỏng câm:
#    cron không gửi thư đi đâu, nên nó sẽ chỉ lộ ra vào đúng ngày chứng chỉ hết hạn.
#
# ⭐ `docker run` + `docker exec` không nội suy tệp compose nào, nên không có gì
#    để thiếu. Volume gọi thẳng theo tên `<dự án>_certbot-webroot`.
#
#   Cài:  17 3 * * 1 /opt/songnhue/gia-han-tls.sh >> /var/log/songnhue/gia-han-tls.log 2>&1
# =============================================================================
set -uo pipefail

CERTBOT_IMAGE="${CERTBOT_IMAGE:-certbot/certbot:v5.8.0}"
WEBROOT_VOLUME="${WEBROOT_VOLUME:-songnhue_certbot-webroot}"
NGINX_CT="${NGINX_CT:-songnhue-nginx}"
LE_DIR="${LE_DIR:-/etc/letsencrypt}"

echo "════ $(date '+%F %T %z') gia hạn TLS ════"

# Số ngày còn lại TRƯỚC khi gia hạn — để dòng log nói được điều gì đó ngay cả khi
# certbot quyết định "chưa tới hạn". Một lượt chạy im lặng và một lượt chạy hỏng
# phải phân biệt được (luật 9).
# ⚠ KHÔNG tính "còn bao nhiêu ngày" bằng `date -d "<chuỗi GMT>"`: image certbot
#   dựa trên Alpine, `date` ở đó là busybox và KHÔNG đọc được định dạng của
#   OpenSSL. Bản đầu của tệp này in ra `còn -20703 ngày` cho MỌI chứng chỉ — một
#   con số sai đọc như một con số đúng, và nó là thứ duy nhất người ta liếc qua
#   trong log. Dùng `-checkend`, phép đo nhị phân của chính OpenSSL: nó phân biệt
#   được hai trạng thái mà không cần số học ngày tháng (luật 9).
truoc() {
    docker run --rm -v "$LE_DIR:/etc/letsencrypt:ro" --entrypoint sh "$CERTBOT_IMAGE" -c '
      for d in /etc/letsencrypt/live/*/; do
        [ -f "$d/cert.pem" ] || continue
        n=$(basename "$d")
        h=$(openssl x509 -in "$d/cert.pem" -noout -enddate | cut -d= -f2)
        if   ! openssl x509 -in "$d/cert.pem" -noout -checkend 0        >/dev/null 2>&1; then t="⛔ ĐÃ HẾT HẠN"
        elif ! openssl x509 -in "$d/cert.pem" -noout -checkend 604800   >/dev/null 2>&1; then t="⛔ dưới 7 ngày"
        elif ! openssl x509 -in "$d/cert.pem" -noout -checkend 2592000  >/dev/null 2>&1; then t="⚠ dưới 30 ngày"
        else t="còn trên 30 ngày"; fi
        printf "  %-28s %-18s (hết hạn %s)\n" "$n" "$t" "$h"
      done' < /dev/null 2>/dev/null
}

echo "── trước khi gia hạn"; truoc

docker run --rm \
    -v "$LE_DIR:/etc/letsencrypt" \
    -v "$WEBROOT_VOLUME:/var/www/certbot" \
    "$CERTBOT_IMAGE" renew --webroot -w /var/www/certbot --quiet < /dev/null
MA=$?
echo "── certbot renew thoát $MA"

if [ "$MA" -ne 0 ]; then
    echo "✗ GIA HẠN HỎNG — HTTPS sẽ chết vào ngày hết hạn ở trên. Xử lý ngay." >&2
    exit "$MA"
fi

# Nạp lại nginx bằng `docker exec`, KHÔNG `docker compose exec` (xem đầu tệp).
if docker exec "$NGINX_CT" nginx -s reload < /dev/null 2>/dev/null; then
    echo "── đã nạp lại nginx"
else
    echo "✗ Không nạp lại được nginx — chứng chỉ mới có trên đĩa nhưng nginx vẫn phục vụ bản cũ." >&2
    exit 1
fi

echo "── sau khi gia hạn"; truoc
echo "✓ xong"
