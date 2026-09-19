#!/usr/bin/env bash
# =============================================================================
# Tự kiểm `cong-cong-khai.js` trên MÁY CHỦ GIẢ — ⛔ chạm staging, ⛔ chạm production.
#
# Vì sao (T63.21): `README.md` §3 tự khai *"vế `tim_kiem_rong` CHƯA thử chiều ĐỎ"* từ
# 14/09/2026. Một ngưỡng chưa ai thấy đỏ là một ngưỡng ⛔ ai biết nó bắt được gì ⛔
# (luật 1), và nếu nó đi qua một **tập rỗng** thì nó còn xanh trong đúng tình huống
# nó sinh ra để bắt (luật 7).
#
# Năm ca — mỗi ca dựng MỘT trạng thái và đòi ĐÚNG ngưỡng tương ứng phải đỏ. Ca đầu là
# **vế đối chứng**: ⛔ có nó thì một kịch bản đỏ-với-mọi-thứ cũng "đạt" cả bốn ca sau
# (luật 9 — một khẳng định ⛔ phân biệt được hai trạng thái thì ⛔ khẳng định gì).
#
#   binh-thuong             ⇒ thoát 0
#   rong-duoi-tai           ⇒ thoát 99 · `tim_kiem_rong`          (chiều ĐỎ, món nợ của dòng này)
#   khong-tra-loi-duoi-tai  ⇒ thoát 99 · `tim_kiem_khong_tra_loi` (429 phía SSR — T61.17, WS-72)
#   moc-rong                ⇒ thoát 99 · `thieu_moc_tim_kiem`     (vế chống tập rỗng)
#   chan-429                ⇒ thoát 99 · `bi_chan_429`
#
# Chạy:  tools/tai-thu/tu-kiem/tu-kiem-tim-kiem-rong.sh
#
# ⭐ Kiểm chứng NGƯỢC cho bản vá: chạy lại chính script này trên một BẢN KHÁC của kịch bản
#    (tiền lệ `tools/may-chu/tu-kiem-dat-bien-b6.sh`) —
#      KICH_BAN=/duong/dan/ban-truoc.js tools/tai-thu/tu-kiem/tu-kiem-tim-kiem-rong.sh
#    Bản TRƯỚC 18/09/2026 ⛔ có `thieu_moc_tim_kiem` ⇒ ca `moc-rong` thoát **0**, tức nó
#    xanh trong đúng tình huống *"vế tìm kiếm ⛔ được đo"*.
# =============================================================================
set -euo pipefail
cd "$(dirname "$0")/../../.."

KICH_BAN="${KICH_BAN:-tools/tai-thu/cong-cong-khai.js}"
ANH_K6="${ANH_K6:-grafana/k6:0.57.0}"
CONG="${CONG:-18099}"
NHAT_KY="$(mktemp -d)"
PID_MAY=""
don_dep() {
  [ -n "$PID_MAY" ] && kill "$PID_MAY" 2>/dev/null || true
  rm -rf "$NHAT_KY"
}
trap don_dep EXIT

command -v docker >/dev/null || { echo "⛔ Cần docker để chạy $ANH_K6."; exit 2; }
[ -f "$KICH_BAN" ] || { echo "⛔ ⛔ thấy kịch bản: $KICH_BAN"; exit 2; }
echo "Kịch bản đo: $KICH_BAN"

# Hình dạng phiên RÚT GỌN — chỉ để lượt tự kiểm xong trong vài chục giây. Kịch bản tự in
# cảnh báo rằng một lượt như vậy ⛔ phải số đo nghiệm thu.
export SO_NGUOI=4 LEN=1s GIU=8s HA=1s TI_LE_TIM_KIEM=1 NGHI_MIN=0 NGHI_KHOANG=0.2

thu_mot_ca() {
  local che_do="$1" ma_mong_doi="$2" nguong_phai_do="$3"
  local log="$NHAT_KY/$che_do.log"

  CHE_DO="$che_do" CONG="$CONG" node tools/tai-thu/tu-kiem/may-chu-gia.js >"$NHAT_KY/$che_do.may" 2>&1 &
  PID_MAY=$!
  for _ in $(seq 1 50); do
    curl -sf "http://127.0.0.1:$CONG/" -o /dev/null && break
    sleep 0.2
  done

  set +e
  docker run --rm -i --add-host=host.docker.internal:host-gateway \
    -e BASE_URL="http://host.docker.internal:$CONG" \
    -e SO_NGUOI -e LEN -e GIU -e HA -e TI_LE_TIM_KIEM -e NGHI_MIN -e NGHI_KHOANG \
    "$ANH_K6" run - <"$KICH_BAN" >"$log" 2>&1
  local ma=$?
  set -e
  kill "$PID_MAY" 2>/dev/null || true
  wait "$PID_MAY" 2>/dev/null || true
  PID_MAY=""

  # ⛔ Đọc mã thoát MỘT MÌNH (luật 9): 99 chỉ nói "một ngưỡng nào đó đỏ". Ngưỡng NÀO đỏ mới là
  #   thứ phân biệt được năm ca này với nhau — k6 in tên nó ở dòng `thresholds on metrics '…'`.
  # ⚠ `|| true` là BẮT BUỘC, ⛔ phải cho chắc: script bật `pipefail`, nên ở ca XANH (⛔ ngưỡng nào
  #   đỏ) `grep` ⛔ khớp gì và thoát 1 ⇒ cả phép gán thoát 1 ⇒ `set -e` giết script **trước** dòng
  #   in kết quả. Đo được ở lượt chạy đầu: nó dừng sau ca 1 mà ⛔ in một chữ nào (luật 32).
  local da_do
  da_do="$(grep -o "thresholds on metrics '[^']*'" "$log" | sed "s/.*'\(.*\)'/\1/" | tr '\n' ' ' || true)"
  echo "  · [$che_do] mã thoát=$ma (mong đợi $ma_mong_doi) · ngưỡng đỏ: ${da_do:-⛔ có}"

  if [ "$ma" != "$ma_mong_doi" ]; then
    echo "⛔ ĐỎ: ca '$che_do' cho mã thoát $ma, mong đợi $ma_mong_doi. Nhật ký:"; tail -30 "$log"; exit 1
  fi
  if [ -n "$nguong_phai_do" ] && ! grep -q "$nguong_phai_do" <<<"$da_do"; then
    echo "⛔ ĐỎ: ca '$che_do' đỏ vì LÝ DO KHÁC — mong '$nguong_phai_do', đo được '${da_do:-⛔ có}'."
    echo "   (một bài đỏ đúng lúc mà sai chỗ vẫn dẫn người đọc đi lạc — §11.19)"; tail -30 "$log"; exit 1
  fi
}

echo "── Năm ca ─────────────────────────────────────────────"
thu_mot_ca binh-thuong             0  ''
thu_mot_ca rong-duoi-tai           99 'tim_kiem_rong'
thu_mot_ca khong-tra-loi-duoi-tai  99 'tim_kiem_khong_tra_loi'
thu_mot_ca moc-rong                99 'thieu_moc_tim_kiem'
thu_mot_ca chan-429                99 'bi_chan_429'
echo "✓ 5/5 ca đúng — kịch bản phân biệt được năm trạng thái, ⛔ chỉ 'có đỏ hay ⛔'."
