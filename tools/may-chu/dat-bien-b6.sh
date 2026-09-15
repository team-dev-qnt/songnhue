#!/usr/bin/env bash
# =============================================================================
# Đặt các biến `.env` chặn lượt đề bạt Phase 4 (phase4-tracking-tmp.md §B6)
# trên CẢ HAI máy — chạy TỪ MÁY CỦA QUẢN TRỊ, ⛔ không chạy trên máy chủ.
#
# Vì sao là script chứ ⛔ phải màn hình quản trị: mọi biến ở đây do nginx,
# Prometheus hoặc Alertmanager đọc — ⛔ phải ứng dụng. Lý do từng nhóm:
# architecture-review.md §12.1.
#
# Dùng:
#   tools/may-chu/dat-bien-b6.sh          # đặt thật
#   tools/may-chu/dat-bien-b6.sh --thu    # chỉ đo + in việc SẼ làm, ⛔ ghi gì
#
# Script làm:
#   1. Sao lưu `.env` cả hai máy (`.env.bak-<giờ>`, quyền 600).
#   2. VPS-1: `METRICS_ALLOW_IP` = IP ra Internet ĐO trên VPS-2 · `METRICS_BEARER_TOKEN`
#      sinh ngẫu nhiên nếu chưa có.
#   3. VPS-2: `METRICS_ALLOW_IP=127.0.0.1` · `METRICS_BEARER_TOKEN` riêng ·
#      `PROD_METRICS_HOST` = `ADMIN_DOMAIN` của VPS-1 · `PROD_METRICS_BEARER_TOKEN`
#      = token VPS-1 · hỏi 6 giá trị ngoài (ô nhập ẨN cho secret).
#   4. Chép `SMTP_*` VPS-1 → VPS-2 (T50.13) CHỈ KHI ứng dụng staging đang chạy bản
#      có chuyển hướng thư (T61.23) VÀ `MAIL_REDIRECT_TO` đã đặt. ⛔ Có SMTP mà bản
#      đang chạy ⛔ biết chuyển hướng ⇒ lượt khởi động lại kế tiếp gửi thư thử tới
#      hộp thư THẬT của cán bộ (CSDL staging nhân bản từ production).
#   5. `chmod 600 .env` cả hai máy.
#
# ⛔ Script ⛔ bao giờ in giá trị secret. Biến đã có giá trị thì GIỮ NGUYÊN (chạy lại
#    an toàn), trừ `PROD_METRICS_BEARER_TOKEN` — nó PHẢI bằng token VPS-1.
# ⛔ Đặt biến ⛔ khởi động lại gì: container chỉ đọc `.env` khi được tạo lại.
#
# Biến ghi đè:
#   PROD_SSH   (songnhue@27.71.16.154)   STAGING_SSH (songnhue@27.71.27.75)
#   SSH_KEY    (~/.ssh/songnhue_deploy)  ENV_PATH    (/opt/songnhue/.env)
#   SSH_BIN    (ssh) — bài tự-kiểm thay bằng bản giả chạy tại chỗ
#
# Mã thoát:
#   0  đủ mọi biến §B6 ở cả hai máy
#   3  còn thiếu biến (danh sách in ở cuối) — chạy lại sau khi có giá trị
#   1  lỗi (SSH, `.env` hỏng, giá trị mâu thuẫn)
# =============================================================================
set -euo pipefail

PROD_SSH="${PROD_SSH:-songnhue@27.71.16.154}"
STAGING_SSH="${STAGING_SSH:-songnhue@27.71.27.75}"
SSH_KEY="${SSH_KEY:-$HOME/.ssh/songnhue_deploy}"
ENV_PATH="${ENV_PATH:-/opt/songnhue/.env}"
SSH_BIN="${SSH_BIN:-ssh}"
NHAP_TU="${NHAP_TU:-/dev/tty}"   # bài tự-kiểm thay bằng một tệp

THU=0
[[ "${1:-}" == "--thu" ]] && THU=1

readonly SMTP_KHOA=(SMTP_HOST SMTP_PORT SMTP_USERNAME SMTP_PASSWORD SMTP_FROM SMTP_STARTTLS SMTP_AUTH)
# Mười biến §B6 của VPS-2 — cùng danh sách với lệnh đếm trong phase4-tracking-tmp.md.
readonly B6_STAGING=(METRICS_ALLOW_IP METRICS_BEARER_TOKEN PROD_METRICS_HOST PROD_METRICS_BEARER_TOKEN
  ALERT_EMAIL_TO SLACK_WEBHOOK_URL TELEGRAM_BOT_TOKEN TELEGRAM_CHAT_ID MAIL_REDIRECT_TO HEALTHCHECKS_PING_URL)
readonly B6_PROD=(METRICS_ALLOW_IP METRICS_BEARER_TOKEN)

bao() { printf '%s\n' "$*" >&2; }
loi() { bao "⛔ $*"; exit 1; }

chay() { # chay <máy> <lệnh bash> [đối số…] — lệnh đi qua `bash -c`, stdin chuyển tiếp
  local may="$1" lenh="$2"; shift 2
  local thamso="" a
  for a in "$@"; do thamso+=" $(printf '%q' "$a")"; done
  "$SSH_BIN" -i "$SSH_KEY" -o IdentitiesOnly=yes -o BatchMode=yes -o ConnectTimeout=15 "$may" \
    "bash -c $(printf '%q' "$lenh") _ $(printf '%q' "$ENV_PATH")$thamso"
}

# Đọc một biến (dòng cuối nếu trùng — cùng luật với env_file của compose).
doc() { chay "$1" 'sed -n "s/^$2=//p" "$1" | tail -n 1' "$2"; }

# Ghi một biến; giá trị đi qua STDIN (⛔ đối số dòng lệnh — `ps` thấy được).
# Trùng khoá ⇒ dừng: gộp hộ là đổi ngầm giá trị đang có hiệu lực.
ghi() {
  local may="$1" khoa="$2" gia_tri="$3"
  if (( THU )); then bao "   [thử] sẽ đặt $khoa trên $may"; return; fi
  printf '%s\n' "$gia_tri" | chay "$may" '
    set -eu
    f="$1"; k="$2"; IFS= read -r v
    n=$(grep -c "^$k=" "$f" || true)
    [ "$n" -le 1 ] || { echo "khoá $k xuất hiện $n lần trong $f" >&2; exit 1; }
    tmp=$(mktemp "$f.XXXXXX")
    K="$k" V="$v" awk '"'"'BEGIN { k = ENVIRON["K"]; v = ENVIRON["V"]; xong = 0 }
      index($0, k "=") == 1 { print k "=" v; xong = 1; next }
      { print }
      END { if (!xong) print k "=" v }'"'"' "$f" > "$tmp"
    cat "$tmp" > "$f"   # giữ inode, chủ sở hữu, quyền
    rm -f "$tmp"
    [ "$(sed -n "s/^$k=//p" "$f" | tail -n 1)" = "$v" ] || { echo "đọc lại $k lệch" >&2; exit 1; }
  ' "$khoa"
}

hoi() { # hoi <khoá> <mô tả> <regex> <an:0|1> — in giá trị ra stdout, rỗng = bỏ qua
  local khoa="$1" mo_ta="$2" mau="$3" an="$4" v
  while true; do
    if (( an )); then
      IFS= read -r -s -p "   $khoa — $mo_ta (Enter để bỏ qua): " v <&3 || v=""; printf '\n' >&2
    else
      IFS= read -r -p "   $khoa — $mo_ta (Enter để bỏ qua): " v <&3 || v=""
    fi
    v="${v#"${v%%[![:space:]]*}"}"; v="${v%"${v##*[![:space:]]}"}"
    [[ -z "$v" ]] && { printf ''; return; }
    [[ "$v" =~ $mau ]] && { printf '%s' "$v"; return; }
    bao "   ⚠ sai định dạng, nhập lại"
  done
}

sinh_token() { openssl rand -hex 32; }

# --- 0. Kết nối ---------------------------------------------------------------
bao "== Kiểm kết nối"
for may in "$PROD_SSH" "$STAGING_SSH"; do
  chay "$may" 'test -f "$1" && test -w "$1"' || loi "$may: ⛔ ghi được $ENV_PATH"
  bao "   $may: $(chay "$may" 'hostname')"
done
[[ "$(doc "$PROD_SSH" APP_ENVIRONMENT | tr -d '[:space:]')" == "production" ]] \
  || loi "$PROD_SSH ⛔ khai APP_ENVIRONMENT=production — nhầm máy?"
[[ "$(doc "$STAGING_SSH" APP_ENVIRONMENT | tr -d '[:space:]')" == "staging" ]] \
  || loi "$STAGING_SSH ⛔ khai APP_ENVIRONMENT=staging — nhầm máy?"

# --- 1. Sao lưu ---------------------------------------------------------------
if (( ! THU )); then
  bao "== Sao lưu .env"
  for may in "$PROD_SSH" "$STAGING_SSH"; do
    chay "$may" 'b="$1.bak-$(date +%Y%m%d%H%M%S)"; cp -p "$1" "$b"; chmod 600 "$b"; echo "$b"' >&2
  done
fi

# --- 2. VPS-1 -----------------------------------------------------------------
bao "== VPS-1 (production)"
[[ -z "$(doc "$PROD_SSH" MAIL_REDIRECT_TO)" ]] \
  || loi "VPS-1 có MAIL_REDIRECT_TO — production khai biến này thì ứng dụng DỪNG khởi động (T61.23). Gỡ tay rồi chạy lại."

ip_vps2="$(chay "$STAGING_SSH" 'curl -s --max-time 10 https://api.ipify.org' || true)"
[[ "$ip_vps2" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]] || loi "⛔ đo được IP ra Internet của VPS-2 (nhận: '${ip_vps2}')"
hien_co="$(doc "$PROD_SSH" METRICS_ALLOW_IP)"
if [[ -z "$hien_co" ]]; then
  ghi "$PROD_SSH" METRICS_ALLOW_IP "$ip_vps2"; bao "   METRICS_ALLOW_IP ← $ip_vps2 (đo trên VPS-2)"
elif [[ "$hien_co" != "$ip_vps2" ]]; then
  bao "   ⚠ METRICS_ALLOW_IP=$hien_co khác IP đo được $ip_vps2 — GIỮ NGUYÊN, Prometheus sẽ nhận 403 nếu sai"
else
  bao "   METRICS_ALLOW_IP đã đúng"
fi

token_prod="$(doc "$PROD_SSH" METRICS_BEARER_TOKEN)"
if [[ -z "$token_prod" ]]; then
  token_prod="$(sinh_token)"; ghi "$PROD_SSH" METRICS_BEARER_TOKEN "$token_prod"; bao "   METRICS_BEARER_TOKEN ← sinh mới"
else
  bao "   METRICS_BEARER_TOKEN đã có"
fi

# --- 3. VPS-2 -----------------------------------------------------------------
bao "== VPS-2 (staging + giám sát)"
[[ -n "$(doc "$STAGING_SSH" METRICS_ALLOW_IP)" ]] || { ghi "$STAGING_SSH" METRICS_ALLOW_IP 127.0.0.1; bao "   METRICS_ALLOW_IP ← 127.0.0.1"; }

token_stg="$(doc "$STAGING_SSH" METRICS_BEARER_TOKEN)"
if [[ -z "$token_stg" ]]; then
  token_stg="$(sinh_token)"; ghi "$STAGING_SSH" METRICS_BEARER_TOKEN "$token_stg"; bao "   METRICS_BEARER_TOKEN ← sinh mới"
fi
[[ "$token_stg" != "$token_prod" ]] || loi "token chỉ số staging TRÙNG production — đổi một bên"

admin_prod="$(doc "$PROD_SSH" ADMIN_DOMAIN | tr -d '[:space:]')"
[[ -n "$admin_prod" ]] || loi "VPS-1 thiếu ADMIN_DOMAIN"
hien_co="$(doc "$STAGING_SSH" PROD_METRICS_HOST)"
if [[ -z "$hien_co" ]]; then
  ghi "$STAGING_SSH" PROD_METRICS_HOST "$admin_prod"; bao "   PROD_METRICS_HOST ← $admin_prod"
elif [[ "$hien_co" != "$admin_prod" ]]; then
  bao "   ⚠ PROD_METRICS_HOST=$hien_co khác ADMIN_DOMAIN VPS-1 ($admin_prod) — GIỮ NGUYÊN"
fi

if [[ "$(doc "$STAGING_SSH" PROD_METRICS_BEARER_TOKEN)" != "$token_prod" ]]; then
  ghi "$STAGING_SSH" PROD_METRICS_BEARER_TOKEN "$token_prod"; bao "   PROD_METRICS_BEARER_TOKEN ← đồng bộ với VPS-1"
fi

# khoá | mô tả | regex | ẩn
while IFS='|' read -r khoa mo_ta mau an; do
  [[ -z "$khoa" ]] && continue
  if [[ -n "$(doc "$STAGING_SSH" "$khoa")" ]]; then bao "   $khoa đã có"; continue; fi
  (( THU )) && { bao "   [thử] sẽ hỏi $khoa"; continue; }
  # Mở nguồn nhập MỘT lần: mở lại mỗi lượt hỏi là đọc mãi dòng đầu; EOF (Ctrl-D) = bỏ qua, ⛔ lặp.
  [[ -e /dev/fd/3 ]] || exec 3<"$NHAP_TU"
  v="$(hoi "$khoa" "$mo_ta" "$mau" "$an")"
  [[ -n "$v" ]] && ghi "$STAGING_SSH" "$khoa" "$v" && bao "   $khoa ← đã đặt"
done <<'DANH_SACH'
ALERT_EMAIL_TO|hộp thư nhận cảnh báo, nhiều địa chỉ cách dấu phẩy|^[^@[:space:],]+@[^@[:space:],]+(,[^@[:space:],]+@[^@[:space:],]+)*$|0
SLACK_WEBHOOK_URL|Slack Incoming Webhook|^https://hooks\.slack\.com/services/[A-Za-z0-9/_-]+$|1
TELEGRAM_BOT_TOKEN|token @BotFather|^[0-9]+:[A-Za-z0-9_-]{30,}$|1
TELEGRAM_CHAT_ID|chat id (nhóm là số âm)|^-?[0-9]+$|0
MAIL_REDIRECT_TO|MỘT hộp thư nhóm phát triển nhận mọi thư của staging|^[^@[:space:],]+@[^@[:space:],]+$|0
HEALTHCHECKS_PING_URL|Ping URL healthchecks.io (Period 5', Grace 5')|^https://hc-ping\.com/[A-Za-z0-9/_-]+$|1
DANH_SACH

# --- 4. SMTP VPS-1 → VPS-2 ----------------------------------------------------
bao "== SMTP (T50.13)"
# Đối chứng dương `spring:` phân biệt "bản cũ" với "⛔ đọc được jar" (luật 9).
do_ban="$(chay "$STAGING_SSH" 'y=$(docker exec songnhue-app unzip -p /app/app.jar BOOT-INF/classes/application.yml 2>/dev/null) || exit 0
  printf "%s %s" "$(printf "%s\n" "$y" | grep -c "^spring:")" "$(printf "%s\n" "$y" | grep -c "redirect-to:")"' || true)"
read -r doc_duoc co_chuyen_huong <<<"${do_ban:-0 0}"
if [[ "${doc_duoc:-0}" != "1" ]]; then
  bao "   ⏸ ⛔ đọc được cấu hình của ứng dụng staging — BỎ QUA SMTP, chạy lại khi container chạy"
elif [[ "${co_chuyen_huong:-0}" == "0" ]]; then
  bao "   ⏸ ứng dụng staging chưa có chuyển hướng thư (T61.23) — BỎ QUA SMTP."
  bao "     Chạy lại script SAU KHI staging lên bản mang PR #135."
elif [[ -z "$(doc "$STAGING_SSH" MAIL_REDIRECT_TO)" && $THU -eq 0 ]]; then
  bao "   ⏸ MAIL_REDIRECT_TO chưa đặt — BỎ QUA SMTP (có SMTP mà ⛔ chuyển hướng ⇒ ứng dụng DỪNG khởi động)"
else
  for khoa in "${SMTP_KHOA[@]}"; do
    nguon="$(doc "$PROD_SSH" "$khoa")"; dich="$(doc "$STAGING_SSH" "$khoa")"
    if [[ -z "$nguon" ]]; then continue
    elif [[ -z "$dich" ]]; then ghi "$STAGING_SSH" "$khoa" "$nguon"; bao "   $khoa ← chép từ VPS-1"
    elif [[ "$dich" != "$nguon" ]]; then bao "   ⚠ $khoa trên VPS-2 khác VPS-1 — GIỮ NGUYÊN"
    fi
  done
fi

# --- 5. Quyền + tổng kết ------------------------------------------------------
if (( ! THU )); then
  for may in "$PROD_SSH" "$STAGING_SSH"; do chay "$may" 'chmod 600 "$1"'; done
fi

bao "== Tổng kết"
thieu=()
for khoa in "${B6_PROD[@]}"; do [[ -n "$(doc "$PROD_SSH" "$khoa")" ]] || thieu+=("VPS-1:$khoa"); done
for khoa in "${B6_STAGING[@]}"; do [[ -n "$(doc "$STAGING_SSH" "$khoa")" ]] || thieu+=("VPS-2:$khoa"); done
[[ -n "$(doc "$STAGING_SSH" SMTP_HOST)" ]] || thieu+=("VPS-2:SMTP_* (chờ bản T61.23 lên staging)")

if (( ${#thieu[@]} == 0 )); then
  bao "   ✅ đủ biến §B6 ở cả hai máy"; exit 0
fi
bao "   Còn thiếu (${#thieu[@]}):"; printf '     - %s\n' "${thieu[@]}" >&2
exit 3
