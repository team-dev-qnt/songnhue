#!/usr/bin/env bash
# =============================================================================
# Tự kiểm `dat-bien-b6.sh` trên HAI MÁY GIẢ — ⛔ chạm máy chủ thật.
#
# Bất biến canh ở đây (luật 1 — mỗi cơ chế phải có bài chứng minh nó bắt được vi phạm):
#   1. Cả SÁU biến ngoài đều được hỏi và ghi. ⛔⛔ Bản script ĐẦU chỉ ghi được biến
#      ĐẦU TIÊN: `ssh` đọc stdin nên lượt `doc` bên trong vòng lặp nuốt mất phần còn
#      lại của danh sách (§10.60). Lỗi ấy ⛔ sinh một dòng đỏ nào — script thoát 3 kèm
#      danh sách thiếu, đọc y hệt "người dùng bỏ qua".
#   2. Biến ĐÃ CÓ giữ nguyên giá trị (chạy lại an toàn).
#   3. ⛔ chép SMTP khi bản staging chưa biết chuyển hướng thư (T61.23).
#   4. Giá trị secret ⛔ xuất hiện trong output.
#
# `ssh` giả: đọc `bash -c <lệnh> _ <đường dẫn> …` rồi chạy tại chỗ trên tệp `.env` giả.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/../.."
SAN="$(mktemp -d)"; trap 'rm -rf "$SAN"' EXIT
# Bài kiểm chứng ngược chạy chính bài này trên một BẢN KHÁC của script.
KICH_BAN="${KICH_BAN:-tools/may-chu/dat-bien-b6.sh}"

cat > "$SAN/prod.env" <<'ENV'
APP_ENVIRONMENT=production
ADMIN_DOMAIN=admin.thuyloisongnhue.vn
SMTP_HOST=smtp.gmail.com
SMTP_PASSWORD=mat-khau-ung-dung-that
ENV
cat > "$SAN/staging.env" <<'ENV'
APP_ENVIRONMENT=staging
ALERT_EMAIL_TO=da-co@songnhue.vn
ENV

cat > "$SAN/ssh-gia" <<'GIA'
#!/usr/bin/env bash
# Bỏ các tuỳ chọn của ssh, lấy <đích> và phần lệnh.
while [[ "${1:-}" == -* ]]; do [[ "$1" == -i || "$1" == -o ]] && shift; shift; done
dich="$1"; shift
tep="$SAN/prod.env"; [[ "$dich" == *27.71.27.75* ]] && tep="$SAN/staging.env"
lenh="$*"
# `hostname` và `curl` (đo IP VPS-2) trả lời tại chỗ.
[[ "$lenh" == *hostname* ]] && { echo "may-gia"; exit 0; }
[[ "$lenh" == *api.ipify.org* ]] && { echo "203.0.113.9"; exit 0; }
# Bản staging giả CHƯA có chuyển hướng thư ⇒ vế SMTP phải bị bỏ qua.
[[ "$lenh" == *app.jar* ]] && { echo "1 0"; exit 0; }
# Lệnh thật mang sẵn đối số đường dẫn `.env`; đổi nó sang tệp giả rồi chạy nguyên văn.
lenh="${lenh//\/opt\/songnhue\/.env/$tep}"
eval "$lenh"
# ⛔⛔ `ssh` THẬT hút sạch stdin của nơi gọi (nó chuyển tiếp sang máy ở xa). Máy giả PHẢI
#    làm đúng thế, nếu ⛔ thì bài này xanh trên cả bản script mang lỗi §10.60 — đo được:
#    lượt kiểm chứng ngược đầu tiên của tôi xanh ở CẢ HAI bản.
cat > /dev/null 2>/dev/null || true
GIA
chmod +x "$SAN/ssh-gia"

# Sáu dòng nhập: ALERT_EMAIL_TO đã có ⇒ ⛔ hỏi lại; năm dòng còn lại theo đúng thứ tự.
cat > "$SAN/nhap" <<'NHAP'
https://hooks.slack.com/services/T000/B000/xxxxxxxxxxxxxxxxxxxxxxxx
123456789:AAaaBBbbCCccDDddEEeeFFffGGgghhhhiii
-1001234567890
doi-phat-trien@goapps.team
https://hc-ping.com/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee
NHAP

ma=0
SAN="$SAN" SSH_BIN="$SAN/ssh-gia" SSH_KEY=/dev/null NHAP_TU="$SAN/nhap" \
  ENV_PATH="/opt/songnhue/.env" bash "$KICH_BAN" > "$SAN/ra" 2>&1 < /dev/null || ma=$?

hong=0
kiem() { # kiem <mô tả> <biểu thức đúng>
  if eval "$2"; then printf '   ✓ %s\n' "$1"; else printf '   ✗ %s\n' "$1"; hong=1; fi
}

echo "== Tự kiểm dat-bien-b6.sh (mã thoát: $ma)"
for k in SLACK_WEBHOOK_URL TELEGRAM_BOT_TOKEN TELEGRAM_CHAT_ID MAIL_REDIRECT_TO HEALTHCHECKS_PING_URL; do
  kiem "$k được ghi vào .env staging" "grep -q '^$k=.\\+' '$SAN/staging.env'"
done
kiem "ALERT_EMAIL_TO đã có ⇒ giữ nguyên" "grep -q '^ALERT_EMAIL_TO=da-co@songnhue.vn$' '$SAN/staging.env'"
kiem "METRICS_BEARER_TOKEN sinh cho cả hai máy" \
  "grep -q '^METRICS_BEARER_TOKEN=[0-9a-f]\\{64\\}$' '$SAN/prod.env' && grep -q '^METRICS_BEARER_TOKEN=[0-9a-f]\\{64\\}$' '$SAN/staging.env'"
kiem "hai token chỉ số KHÁC nhau" \
  "[ \"\$(sed -n 's/^METRICS_BEARER_TOKEN=//p' '$SAN/prod.env')\" != \"\$(sed -n 's/^METRICS_BEARER_TOKEN=//p' '$SAN/staging.env')\" ]"
kiem "PROD_METRICS_BEARER_TOKEN = token VPS-1" \
  "[ \"\$(sed -n 's/^PROD_METRICS_BEARER_TOKEN=//p' '$SAN/staging.env')\" = \"\$(sed -n 's/^METRICS_BEARER_TOKEN=//p' '$SAN/prod.env')\" ]"
kiem "PROD_METRICS_HOST lấy ADMIN_DOMAIN của VPS-1" \
  "grep -q '^PROD_METRICS_HOST=admin.thuyloisongnhue.vn$' '$SAN/staging.env'"
kiem "METRICS_ALLOW_IP VPS-1 = IP đo trên VPS-2" "grep -q '^METRICS_ALLOW_IP=203.0.113.9$' '$SAN/prod.env'"
kiem "⛔ chép SMTP khi bản staging chưa biết chuyển hướng thư" "! grep -q '^SMTP_PASSWORD=' '$SAN/staging.env'"
kiem "⛔ in secret ra màn hình" "! grep -qE 'mat-khau-ung-dung-that|hooks.slack.com/services|hc-ping.com/' '$SAN/ra'"
kiem "mã thoát 3 (còn thiếu SMTP_*)" "[ $ma -eq 3 ]"

(( hong )) && { echo; sed 's/^/   | /' "$SAN/ra"; echo "⛔ TỰ KIỂM ĐỎ"; exit 1; }
echo "   ✅ tự kiểm xanh"
