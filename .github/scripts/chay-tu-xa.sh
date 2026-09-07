#!/usr/bin/env bash
# =============================================================================
# Chạy một khối lệnh TRÊN máy chủ. Khối ấy đọc từ stdin của script này.
#
# ⭐⭐ VÌ SAO KHÔNG `ssh host bash <<REMOTE` NỮA
#
#   Kiểu cũ nuôi bash-từ-xa bằng chính stdin. Bash đọc script ấy DẦN, nên bất kỳ
#   lệnh nào bên trong mà gắn stdin sẽ NUỐT NỐT phần script chưa đọc — bash gặp
#   EOF, thoát 0, và bước workflow XANH sau khi đã bỏ qua nửa cuối công việc.
#
#   Đã hỏng đúng vậy ngày 27/8 (§10.60): `docker compose run --rm migrator` nuốt
#   phần còn lại của khối "Triển khai". Hệ quả đo được:
#     · `up -d --force-recreate` KHÔNG chạy   → không container nào được thay
#     · bước đo lại image ID KHÔNG chạy       → không ai phát hiện
#     · 4/4 câu smoke test XANH               → vì chúng hỏi site, mà site vẫn sống
#       bằng mã cũ; smoke test không phân biệt được "đã thay" với "chưa thay"
#   CD Staging báo thành công, gắn tag `:staging`, ghi tóm tắt — trong khi máy chủ
#   chạy image của 24–25/8.
#
#   Đo trên chính VPS-2 (mỗi dòng là một khối heredoc có `echo` đứng sau lệnh):
#     docker compose run --rm            → dòng sau MẤT
#     docker compose run --rm -T         → dòng sau MẤT  ← `-T` chỉ tắt TTY
#     docker compose run --rm </dev/null → dòng sau CHẠY
#     docker compose up -d               → dòng sau CHẠY
#     docker compose pull                → dòng sau CHẠY
#
# ⭐ Bảo đảm đặt ở ĐÂY, không đặt ở từng lời gọi (CLAUDE.md luật 12): thêm
#   `</dev/null` vào dòng `migrator` chỉ chữa đúng dòng ấy, còn lệnh nuốt stdin
#   tiếp theo mà ai đó thêm vào sẽ lại làm hỏng trong im lặng. Ở đây thì script
#   nằm trong TỆP và stdin của nó là /dev/null — không lệnh nào ăn được nó nữa.
#   `deploy/backup/pre-deploy-dump.sh` đã có sẵn 3 lệnh `docker exec -i`; nó chưa
#   gây hại chỉ vì tình cờ được gọi ở DÒNG CUỐI của khối.
#
# ⚠ Bước đối chiếu SỐ BYTE là phần không được bỏ: nó là thứ duy nhất phân biệt
#   "script đã sang máy chủ trọn vẹn" với "sang một nửa". Không có nó thì kiểu
#   mới cũng chỉ là một cách hỏng khác trong im lặng.
#
# Dùng:
#     HOST=… USER=… .github/scripts/chay-tu-xa.sh "trien-khai" <<'REMOTE'
#       …
#     REMOTE
# =============================================================================
set -euo pipefail

: "${HOST:?Thiếu HOST}"
: "${USER:?Thiếu USER}"

NHAN="${1:-khoi}"
XA="/tmp/songnhue-${NHAN}-$$.sh"

CUC_BO="$(mktemp)"
trap 'rm -f "$CUC_BO"' EXIT
cat > "$CUC_BO"

SO_BYTE="$(wc -c < "$CUC_BO" | tr -d ' ')"
[ "$SO_BYTE" -gt 0 ] || { echo "::error::Khối lệnh cho '$NHAN' RỖNG — không có gì để chạy." >&2; exit 1; }

ssh "$USER@$HOST" "cat > '$XA' && chmod 600 '$XA'" < "$CUC_BO"

# ⚠ Đo lại ở PHÍA BÊN KIA. `scp`/`cat` báo thành công vẫn có thể để lại tệp thiếu
#   (đĩa đầy, kết nối đứt giữa chừng) — và một script thiếu nửa cuối chạy hoàn
#   toàn bình thường rồi thoát 0, đúng cái bẫy mà tệp này tồn tại để chặn.
SO_BYTE_XA="$(ssh "$USER@$HOST" "wc -c < '$XA'" | tr -d ' \r\n')"
if [ "$SO_BYTE" != "$SO_BYTE_XA" ]; then
    ssh "$USER@$HOST" "rm -f '$XA'" || true
    echo "::error::Khối '$NHAN' sang máy chủ KHÔNG trọn vẹn: gửi $SO_BYTE byte, nhận $SO_BYTE_XA byte." >&2
    exit 1
fi
echo "→ [$NHAN] $SO_BYTE byte, khớp hai đầu"

# `</dev/null`: khối chạy từ TỆP, và stdin của nó là chỗ trống. Lệnh nào bên
# trong có gắn stdin thì gặp EOF ngay, không ăn mất phần script còn lại.
# `rc` + `exit \$rc` để mã thoát của khối đi ngược về runner NGUYÊN VẸN — `rm`
# thành công không được phép biến một khối hỏng thành một bước xanh.
ssh "$USER@$HOST" "bash -euo pipefail '$XA' </dev/null; rc=\$?; rm -f '$XA'; exit \$rc"
