#!/usr/bin/env bash
# =============================================================================
# ZAP baseline — quét THỤ ĐỘNG cổng công khai + trang quản trị (T61.28 · NFR-05)
#
# ⛔ Chỉ chạy `zap-baseline.py`: spider + passive scan. KHÔNG active scan, KHÔNG
#    gửi payload tấn công. Dù vậy spider vẫn gửi hàng trăm lượt GET — đủ để chạm
#    hạn mức và đủ để gây phiền nếu nhắm nhầm production.
#
# Dùng:
#   TARGET_URL=https://staging.songnhue.com \
#   ADMIN_URL=https://admin-staging.songnhue.com \
#   tools/zap/zap-baseline.sh
#
# Biến:
#   TARGET_URL            BẮT BUỘC — cổng công khai. ⛔ Không có mặc định.
#   ADMIN_URL             tuỳ chọn — trang quản trị (quét riêng, báo cáo riêng).
#   XAC_NHAN_PRODUCTION   phải đúng chữ `co` thì mới được nhắm tên miền production.
#   ANH_ZAP               ghi đè ảnh ZAP (mặc định: bản ghim tag + digest bên dưới).
#   KET_QUA_GOC           thư mục gốc chứa kết quả (mặc định: tools/zap/ket-qua).
#   PHUT_SPIDER           số phút spider cho mỗi mục tiêu (mặc định 2).
#   PHUT_TOI_DA           trần thời gian toàn lượt quét mỗi mục tiêu, phút (mặc định 15).
#   LUONG_SPIDER          số luồng spider (mặc định 2 — thấp có chủ đích, xem README §3).
#   AJAX_SPIDER           `co` ⇒ bật thêm AJAX spider (hữu ích cho trang quản trị SPA).
#
# Mã thoát (xem README §4):
#   0  không FAIL, không WARN, và đo được 0 lượt 429
#   1  có ít nhất một quy tắc FAIL
#   2  không FAIL, có WARN, và đo được 0 lượt 429
#   3  lỗi khi chạy (docker, mạng, ZAP hỏng, thiếu biến …)
#   4  ZAP không báo FAIL, NHƯNG có lượt 429 hoặc KHÔNG đo được số 429
#      ⇒ độ phủ không trọn, ⛔ không được đọc thành "sạch"
# =============================================================================
set -euo pipefail

# Ảnh chính thức. Ghim tag + digest đo ngày 15/09/2026 (tag `stable` lúc ấy trỏ
# cùng digest với `2.17.0`). Cách tra lại: README §5.
readonly ANH_MAC_DINH="ghcr.io/zaproxy/zaproxy:2.17.0@sha256:781a2bdaea47324e7bab583e2263f21d257b0aee61ed51521a5be45f5f5081ef"
ANH_ZAP="${ANH_ZAP:-$ANH_MAC_DINH}"

THU_MUC_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly THU_MUC_SCRIPT

PHUT_SPIDER="${PHUT_SPIDER:-2}"
PHUT_TOI_DA="${PHUT_TOI_DA:-15}"
LUONG_SPIDER="${LUONG_SPIDER:-2}"
AJAX_SPIDER="${AJAX_SPIDER:-khong}"

loi() {
  printf '⛔ %s\n' "$*" >&2
}

dung() {
  loi "$@"
  exit 3
}

# -----------------------------------------------------------------------------
# 1. Kiểm đầu vào — dừng TRƯỚC khi gửi bất kỳ lượt nào
# -----------------------------------------------------------------------------

if [[ -z "${TARGET_URL:-}" ]]; then
  dung "Thiếu TARGET_URL. ⛔ Không có mặc định: bắn nhầm môi trường là sự cố. Ví dụ: TARGET_URL=https://staging.songnhue.com $0"
fi
ADMIN_URL="${ADMIN_URL:-}"

for so in PHUT_SPIDER PHUT_TOI_DA LUONG_SPIDER; do
  if [[ ! "${!so}" =~ ^[1-9][0-9]*$ ]]; then
    dung "$so phải là số nguyên dương, đang là '${!so}'"
  fi
done

# In ra host (chữ thường) của một URL; rỗng nếu URL sai dạng.
host_cua() {
  local url="$1"
  if [[ "$url" =~ ^[Hh][Tt][Tt][Pp][Ss]?://([^/:?#@]+)(:[0-9]+)?([/?#].*)?$ ]]; then
    # Bỏ dấu chấm cuối: `thuyloisongnhue.vn.` là CÙNG một tên miền (FQDN) — để nguyên thì
    # nó đi lọt phép so tên miền production bên dưới.
    local host="${BASH_REMATCH[1]%.}"
    printf '%s' "$host" | tr '[:upper:]' '[:lower:]'
  fi
}

# Tên miền / địa chỉ đang phục vụ production (CLAUDE.md: từ 08/09/2026 là
# thuyloisongnhue.vn; songnhue.com cũ vẫn trỏ về cùng VPS-1).
la_production() {
  local host="$1"
  case "$host" in
    thuyloisongnhue.vn | *.thuyloisongnhue.vn) return 0 ;;
    songnhue.com | www.songnhue.com | admin.songnhue.com | files.songnhue.com) return 0 ;;
    27.71.16.154) return 0 ;;
  esac
  return 1
}

kiem_muc_tieu() {
  local ten_bien="$1" url="$2" host
  host="$(host_cua "$url")"
  if [[ -z "$host" ]]; then
    dung "$ten_bien='$url' không phải URL http(s) hợp lệ"
  fi
  if [[ "$url" =~ ^[Hh][Tt][Tt][Pp]:// ]]; then
    loi "$ten_bien dùng http:// — kết quả sẽ lẫn chuyển hướng 308 và thiếu các phát hiện về HSTS/cookie Secure. Nên dùng https://"
  fi
  case "$host" in
    localhost | 127.* | ::1)
      dung "$ten_bien trỏ '$host': bên trong container ZAP, địa chỉ ấy là CHÍNH container — không phải máy của bạn"
      ;;
  esac
  if la_production "$host"; then
    if [[ "${XAC_NHAN_PRODUCTION:-}" != "co" ]]; then
      dung "$ten_bien trỏ vào PRODUCTION ($host). Muốn thật sự quét production thì đặt XAC_NHAN_PRODUCTION=co (và báo QuanTran trước)."
    fi
    loi "⚠ Đang nhắm PRODUCTION ($host) với XAC_NHAN_PRODUCTION=co"
  fi
}

kiem_muc_tieu TARGET_URL "$TARGET_URL"
if [[ -n "$ADMIN_URL" ]]; then
  kiem_muc_tieu ADMIN_URL "$ADMIN_URL"
fi

command -v docker >/dev/null 2>&1 || dung "Không tìm thấy lệnh docker"
docker info >/dev/null 2>&1 || dung "docker có mặt nhưng không nói chuyện được với daemon (Docker Desktop đã bật chưa?)"

# -----------------------------------------------------------------------------
# 2. Thư mục kết quả có dấu thời gian
# -----------------------------------------------------------------------------

KET_QUA_GOC="${KET_QUA_GOC:-$THU_MUC_SCRIPT/ket-qua}"
DAU_THOI_GIAN="$(TZ=Asia/Ho_Chi_Minh date +%Y%m%d-%H%M%S)"
THU_MUC_LUOT="$KET_QUA_GOC/$DAU_THOI_GIAN"
mkdir -p "$THU_MUC_LUOT"
# Container chạy dưới người dùng `zap` (uid 1000) ⇒ phải ghi được vào thư mục gắn vào.
chmod 0777 "$THU_MUC_LUOT"

cp "$THU_MUC_SCRIPT/zap-rules.tsv" "$THU_MUC_LUOT/zap-rules.tsv"
cp "$THU_MUC_SCRIPT/dem-ma-trang-thai.py" "$THU_MUC_LUOT/dem-ma-trang-thai.py"
chmod 0644 "$THU_MUC_LUOT/zap-rules.tsv" "$THU_MUC_LUOT/dem-ma-trang-thai.py"

TOM_TAT="$THU_MUC_LUOT/tom-tat.txt"
{
  echo "ZAP baseline — $DAU_THOI_GIAN (+07)"
  echo "Ảnh: $ANH_ZAP"
  echo "Commit kho: $(git -C "$THU_MUC_SCRIPT" rev-parse --short HEAD 2>/dev/null || echo 'không đọc được')"
  echo "TARGET_URL: $TARGET_URL"
  echo "ADMIN_URL:  ${ADMIN_URL:-<không quét>}"
  echo "Spider: ${PHUT_SPIDER} phút · ${LUONG_SPIDER} luồng · AJAX=${AJAX_SPIDER} · trần ${PHUT_TOI_DA} phút"
  echo
} >"$TOM_TAT"

echo "Kết quả sẽ nằm ở: $THU_MUC_LUOT"

# -----------------------------------------------------------------------------
# 3. Quét một mục tiêu
# -----------------------------------------------------------------------------

# Mã thoát của từng mục tiêu, theo thứ tự chạy.
declare -a MA_THOAT_ZAP=()
# `co` nếu mục tiêu có 429 > 0 hoặc KHÔNG đo được số 429.
declare -a PHU_KHONG_TRON=()

quet() {
  local nhan="$1" url="$2"
  local nhat_ky="$THU_MUC_LUOT/$nhan-zap.log"
  local tep_ma="$nhan-ma-trang-thai.txt"
  local -a tham_so_zap

  echo
  echo "=== [$nhan] $url"

  # Thử một lượt trước: không tới được thì dừng sớm với lý do rõ, thay vì để
  # ZAP chạy hết trần thời gian rồi in một báo cáo rỗng trông như "sạch".
  if command -v curl >/dev/null 2>&1; then
    local ma_http
    ma_http="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$url" 2>/dev/null || true)"
    echo "Thử kết nối: HTTP ${ma_http:-000}"
    if [[ -z "$ma_http" || "$ma_http" == "000" ]]; then
      loi "[$nhan] không kết nối được $url — bỏ qua mục tiêu này"
      MA_THOAT_ZAP+=(3)
      PHU_KHONG_TRON+=(co)
      printf '[%s] %s\n  ZAP: KHÔNG CHẠY — không kết nối được (HTTP 000)\n\n' "$nhan" "$url" >>"$TOM_TAT"
      return 0
    fi
    if [[ "$ma_http" == "429" ]]; then
      loi "[$nhan] lượt thử đầu tiên đã nhận 429 — IP này đang bị chặn; kết quả quét sẽ gần như rỗng"
    fi
  fi

  tham_so_zap=(
    zap-baseline.py
    -t "$url"
    -c zap-rules.tsv
    -m "$PHUT_SPIDER"
    -T "$PHUT_TOI_DA"
    -r "$nhan-bao-cao.html"
    -J "$nhan-bao-cao.json"
    -w "$nhan-bao-cao.md"
    --hook=/zap/wrk/dem-ma-trang-thai.py
    -z "-config spider.thread=$LUONG_SPIDER"
  )
  if [[ "$AJAX_SPIDER" == "co" ]]; then
    tham_so_zap+=(-j)
  fi

  local -a cac_ma
  set +e
  docker run --rm \
    -v "$THU_MUC_LUOT:/zap/wrk/:rw" \
    -e "TEP_MA_TRANG_THAI=/zap/wrk/$tep_ma" \
    "$ANH_ZAP" \
    "${tham_so_zap[@]}" 2>&1 | tee "$nhat_ky"
  # ⛔ Đọc NGAY dòng sau ống: `$?` ở đây sẽ là mã của `tee`, không phải của ZAP.
  cac_ma=("${PIPESTATUS[@]}")
  set -e

  local ma_zap="${cac_ma[0]}"
  local ma_tee="${cac_ma[1]:-0}"
  if [[ "$ma_tee" != "0" ]]; then
    loi "[$nhan] tee thoát $ma_tee — nhật ký có thể không đầy đủ"
  fi
  # zap-baseline.py chỉ định nghĩa 0/1/2/3; mọi mã khác (vd 125 docker, 137 OOM) là lỗi chạy.
  case "$ma_zap" in
    0 | 1 | 2 | 3) ;;
    *)
      loi "[$nhan] docker/ZAP thoát $ma_zap (không phải mã của zap-baseline) — coi là lỗi chạy"
      ma_zap=3
      ;;
  esac
  MA_THOAT_ZAP+=("$ma_zap")

  # Đếm mã trạng thái do hook ghi ra
  local tong="" so429="" ty_le_429="KHÔNG ĐO ĐƯỢC"
  if [[ -s "$THU_MUC_LUOT/$tep_ma" ]]; then
    tong="$(awk '$1 == "tong" {print $2}' "$THU_MUC_LUOT/$tep_ma")"
    so429="$(awk '$1 == "429" {print $2}' "$THU_MUC_LUOT/$tep_ma")"
    so429="${so429:-0}"
  fi
  if [[ "$tong" =~ ^[0-9]+$ && "$tong" -gt 0 ]]; then
    ty_le_429="$so429/$tong ($(awk -v a="$so429" -v b="$tong" 'BEGIN {printf "%.1f", 100*a/b}')%)"
    if [[ "$so429" -gt 0 ]]; then
      PHU_KHONG_TRON+=(co)
    else
      PHU_KHONG_TRON+=(khong)
    fi
  else
    # Tệp vắng / tổng = 0 ⇒ "không đo được", ⛔ không phải "0 lượt 429"
    PHU_KHONG_TRON+=(co)
  fi

  local dong_dem
  dong_dem="$(grep -E '^FAIL-NEW:' "$nhat_ky" | tail -1 || true)"
  {
    echo "[$nhan] $url"
    echo "  ZAP thoát: $ma_zap"
    echo "  Đếm: ${dong_dem:-<không thấy dòng FAIL-NEW — ZAP có thể đã hỏng giữa chừng>}"
    echo "  Tỉ lệ 429 (phát hiện riêng): $ty_le_429"
    echo "  Báo cáo: $nhan-bao-cao.html · $nhan-bao-cao.json · $nhan-bao-cao.md"
    echo
  } >>"$TOM_TAT"
}

quet cong-khai "$TARGET_URL"
if [[ -n "$ADMIN_URL" ]]; then
  quet quan-tri "$ADMIN_URL"
fi

# -----------------------------------------------------------------------------
# 4. Gộp mã thoát — hỏng thắng tất cả, FAIL thắng WARN
# -----------------------------------------------------------------------------

co_ma() {
  local muon="$1" m
  for m in "${MA_THOAT_ZAP[@]}"; do
    [[ "$m" == "$muon" ]] && return 0
  done
  return 1
}

if co_ma 3; then
  MA_CUOI=3
elif co_ma 1; then
  MA_CUOI=1
else
  MA_CUOI=0
  if co_ma 2; then
    MA_CUOI=2
  fi
  for p in "${PHU_KHONG_TRON[@]}"; do
    if [[ "$p" == "co" ]]; then
      MA_CUOI=4
    fi
  done
fi

{
  echo "Mã thoát chung: $MA_CUOI"
  case "$MA_CUOI" in
    0) echo "  ⇒ không FAIL/WARN, 0 lượt 429." ;;
    1) echo "  ⇒ có quy tắc FAIL — đọc báo cáo HTML." ;;
    2) echo "  ⇒ không FAIL, có WARN — đọc báo cáo HTML." ;;
    3) echo "  ⇒ lỗi khi chạy — kết quả KHÔNG dùng được để kết luận." ;;
    4) echo "  ⇒ ZAP không báo FAIL nhưng có 429 hoặc không đo được 429 — độ phủ KHÔNG trọn, ⛔ không đọc là sạch." ;;
  esac
} >>"$TOM_TAT"

echo
cat "$TOM_TAT"
exit "$MA_CUOI"
