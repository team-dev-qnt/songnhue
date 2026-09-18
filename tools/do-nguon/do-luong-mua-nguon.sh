#!/usr/bin/env bash
# Đo tab "MN+Mưa" của nguồn bhh40 để trả lời MỘT câu duy nhất:
#   ô `-` ở 15 trạm mưa nghĩa là "hôm nay không mưa" hay "chưa bao giờ có số"?
#
# ⛔ Vì sao cần lượt đo này thay vì đọc tài liệu: luật 9 — một khẳng định không
#    phân biệt được hai trạng thái thì không khẳng định gì. BOQ G3-a đang ghi
#    "API lượng mưa chưa có", câu ấy đúng cho `/api/` và SAI cho cả nguồn
#    (`phase3-plan.md` §2.2). Trước khi mở lại G3-a phải biết nguồn có ĐANG THU
#    số hay không.
#
# Mã `@tonghopdh` nằm nguyên văn trong HTML công khai của nguồn — đây là lượt
# đọc một trang công khai, KHÔNG dùng mã số riêng của Công ty.
#
# Dùng: bash tools/do-nguon/do-luong-mua-nguon.sh [thư-mục-ghi]
# Đặt lịch (ví dụ 2 lượt/ngày, 08:05 và 20:05):
#   5 8,20 * * * cd <kho> && bash tools/do-nguon/do-luong-mua-nguon.sh >> /tmp/do-mua.log 2>&1
set -uo pipefail

URL='http://103.9.86.202/bhh40.net.songnhue/tonghop-dh/bieusov01.aspx?user=@tonghopdh&pro=homepage&menuh=indexselect02&tivi=yes'
THU_MUC="${1:-docs/do-nguon/luong-mua}"
mkdir -p "$THU_MUC"

MOC="$(date -u +%Y%m%dT%H%M%SZ)"
TEP="$THU_MUC/$MOC.html"

MA_HTTP=$(curl -sS -m 30 -o "$TEP" -w '%{http_code}' "$URL") || {
    echo "$MOC | LOI_MANG | curl thoát $?"
    exit 1
}

BYTE=$(wc -c < "$TEP" | tr -d ' ')

# ⛔ Đếm ô có SỐ trong khối lượng mưa, không đếm ô `-`. Nếu con số này > 0 dù
#    một lượt, thì `-` nghĩa là "không mưa" và G3-a mở được. Chừng nào nó còn 0
#    ở mọi lượt thì hai trạng thái vẫn chưa phân biệt được.
SO_O_CO_SO=$(python3 - "$TEP" <<'PY'
import re, sys, html
t = open(sys.argv[1], encoding='utf-8', errors='replace').read()
txt = re.sub(r'\s+', ' ', html.unescape(re.sub('<[^>]+>', ' ', t)))
i = txt.find('Lượng mưa tại các điểm đo')
print(0 if i < 0 else len(re.findall(r'(?<![\w.,])\d+(?:[.,]\d+)?(?![\w.,])', txt[i:])))
PY
)

echo "$MOC | HTTP $MA_HTTP | $BYTE byte | o-co-so=$SO_O_CO_SO | $TEP"
