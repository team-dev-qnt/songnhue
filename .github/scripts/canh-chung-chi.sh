#!/usr/bin/env bash
#
# T11.88 · DOD4.8 — canh HẠN CHỨNG CHỈ TLS, đo từ BÊN NGOÀI máy chủ.
#
# =============================================================================
# ⛔⛔ VÌ SAO ĐO TỪ NGOÀI, ⛔ ĐỌC CRON TRÊN MÁY
# =============================================================================
#
# Cron gia hạn là thứ ĐI GIA HẠN, ⛔ phải thứ BÁO rằng gia hạn đã hỏng. Cả hai cách
# nó từng hỏng đều IM LẶNG trên máy:
#   · VPS-2 chạy từ 07/09 mà ⛔ cài gói `cron` (T11.88) — ⛔ có tiến trình nào để kêu;
#   · dòng cron dạng `docker compose … --profile certbot` mà sổ từng dặn thoát 1 ở
#     MỌI lượt (§10.81) — cron ⛔ gửi thư đi đâu.
# Thứ duy nhất ⛔ nói dối được là chứng chỉ mà một máy khách THẬT nhận khi bắt tay TLS
# (có SNI) — nên đo đúng chỗ ấy, từ runner GitHub, ⛔ từ chính máy đang hỏng.
#
# =============================================================================
# TRẠNG THÁI mỗi tên miền
# =============================================================================
#
#   con-han        còn ≥ NGUONG_NGAY ngày
#   sap-het-han    0 < còn < NGUONG_NGAY — certbot gia hạn khi còn < 30 ngày và cron
#                  chạy mỗi tuần, nên một máy khoẻ ⛔ bao giờ xuống dưới ~23 ngày.
#                  Xuống dưới 21 = cron đã hỏng ít nhất một tuần, còn 3 tuần để sửa.
#   het-han        đã quá notAfter
#   khong-do-duoc  bắt tay hỏng / ⛔ đọc được notAfter. ⛔ Đọc là "còn hạn" (luật 9).
#                  Tên miền bị khai tử thì GỠ khỏi danh sách, ⛔ để nó kêu mãi.
#
# ⚠ Phạm vi (luật 28): đúng danh sách TEN_MIEN của workflow — ⛔ tự dò tên miền nào.
#
# =============================================================================
# ⛔ CHỐNG CHUÔNG MỘT BIT VÀ CHUÔNG ỒN (T11.84)
# =============================================================================
#
# Mỗi lượt đỏ đều ĐỎ trên tab Actions, nhưng chỉ BÌNH LUẬN khi vân tay đổi: tập
# (tên miền · trạng thái · bậc ngày còn lại 21/14/7/3/1). Cùng một sự cố ⛔ sinh 21
# bình luận giống nhau; bước sang bậc gấp hơn thì kêu lại.
#
set -euo pipefail

KHO="${KHO:-}"
TEN_MIEN="${TEN_MIEN:-}"
NGUONG_NGAY="${NGUONG_NGAY:-21}"
BAY_GIO="${BAY_GIO:-}" # epoch giây; để trống thì lấy giờ hệ thống (bài kiểm ghim giờ)

# Nhãn nhận diện issue — gán ĐÚNG MỘT LẦN (luật 14: nhánh MỞ và nhánh ĐÓNG phải tìm cùng một chuỗi).
NHAN="canh-chung-chi"

# ⛔ Thiếu công cụ thì ĐỎ, ⛔ `exit 0` (T11.41 — một cổng kiểm thiếu công cụ là một cổng kiểm KHÔNG CHẠY).
for cong_cu in gh openssl jq; do
    if ! command -v "$cong_cu" >/dev/null 2>&1; then
        echo "::error::Không có \`$cong_cu\` trên PATH — bộ canh không chạy được, và một bộ canh không chạy KHÔNG phải một bộ canh đạt." >&2
        exit 3
    fi
done
if [ -z "$KHO" ]; then
    echo "::error::Thiếu biến KHO (owner/repo)." >&2
    exit 3
fi
# ⛔ Danh sách rỗng thì "mọi chứng chỉ còn hạn" là một câu đúng trên tập rỗng (luật 7).
if [ -z "${TEN_MIEN// /}" ]; then
    echo "::error::TEN_MIEN rỗng — bộ canh không có gì để đo." >&2
    exit 3
fi

bay_gio() {
    if [ -n "$BAY_GIO" ]; then
        echo "$BAY_GIO"
    else
        date -u +%s
    fi
}

# notAfter của chứng chỉ mà máy khách THẬT nhận (có SNI) — rỗng nếu bắt tay hỏng.
het_han_cua() {
    openssl s_client -connect "$1:443" -servername "$1" </dev/null 2>/dev/null |
        openssl x509 -noout -enddate 2>/dev/null |
        sed -n 's/^notAfter=//p' || true
}

# "Nov 22 17:10:01 2026 GMT" → epoch; rỗng nếu ⛔ đọc được. GNU trước (runner), BSD sau (máy dev).
sang_epoch() {
    local s="${1% GMT}"
    [ -n "$s" ] || return 0
    date -u -d "$s" +%s 2>/dev/null || date -u -j -f '%b %d %T %Y' "$s" +%s 2>/dev/null || true
}

bac_ngay() { # bậc gấp của số ngày còn lại — đổi bậc thì kêu lại
    local n="$1"
    for b in 1 3 7 14; do
        if [ "$n" -lt "$b" ]; then
            echo "$b"
            return
        fi
    done
    echo "$NGUONG_NGAY"
}

HIEN_TAI="$(bay_gio)"
BANG=""
VAN_TAY=""
so_hong=0
for h in $TEN_MIEN; do
    het="$(het_han_cua "$h")"
    ep="$(sang_epoch "$het")"
    if [ -z "$het" ] || [ -z "$ep" ]; then
        tt="khong-do-duoc"
        con="?"
        bac="-"
    else
        con_giay=$((ep - HIEN_TAI))
        con=$((con_giay / 86400))
        if [ "$con_giay" -le 0 ]; then
            tt="het-han"
            bac="0"
        elif [ "$con" -lt "$NGUONG_NGAY" ]; then
            tt="sap-het-han"
            bac="$(bac_ngay "$con")"
        else
            tt="con-han"
            bac="-"
        fi
    fi
    echo "$h => $tt (còn $con ngày · notAfter=${het:-—})"
    BANG="$BANG| \`$h\` | \`$tt\` | $con | ${het:-—} |"$'\n'
    if [ "$tt" != "con-han" ]; then
        so_hong=$((so_hong + 1))
        VAN_TAY="$VAN_TAY$h:$tt:$bac;"
    fi
done

if [ "$so_hong" -eq 0 ]; then
    echo "TRANG_THAI=binh-thuong"
else
    echo "TRANG_THAI=co-van-de"
fi

SO_HIEU="$(gh issue list --repo "$KHO" --label "$NHAN" --state open --limit 1 --json number --jq '.[0].number // empty' 2>/dev/null || true)"

if [ "$so_hong" -eq 0 ]; then
    if [ -n "$SO_HIEU" ]; then
        gh issue close "$SO_HIEU" --repo "$KHO" \
            --comment "Mọi chứng chỉ trong danh sách còn ≥ $NGUONG_NGAY ngày. Đóng tự động."
    fi
    exit 0
fi

THAN="$(printf '**%s/%s tên miền có vấn đề.** Ngưỡng %s ngày — certbot gia hạn khi còn < 30 ngày và cron chạy mỗi tuần, nên xuống dưới ngưỡng nghĩa là cron gia hạn đã hỏng ít nhất một tuần.\n\n| Tên miền | Trạng thái | Còn (ngày) | notAfter |\n|---|---|---:|---|\n%s\nViệc làm: `docs/runbook/ten-mien-va-chung-chi.md` (gia hạn tay) · đo cron trên máy: `sudo bash deploy/host-prepare.sh --kiem` (bước 6).\n\n<!-- van-tay:%s -->' \
    "$so_hong" "$(echo "$TEN_MIEN" | wc -w | tr -d ' ')" "$NGUONG_NGAY" "$BANG" "$VAN_TAY")"

if [ -n "$SO_HIEU" ]; then
    # Vân tay của lần kêu gần nhất: thân issue hoặc bình luận cuối mang dấu `van-tay`.
    CU="$(gh issue view "$SO_HIEU" --repo "$KHO" --json body,comments 2>/dev/null |
        jq -r '[.body, (.comments // [])[].body] | [.[] | select(type == "string") | capture("<!-- van-tay:(?<v>[^ ]*) -->") | .v] | last // ""' 2>/dev/null || true)"
    if [ "$CU" = "$VAN_TAY" ]; then
        echo "Vân tay ⛔ đổi so với lần kêu trước — ⛔ bình luận thêm (T11.84), lượt chạy vẫn ĐỎ."
    else
        gh issue comment "$SO_HIEU" --repo "$KHO" --body "$THAN"
    fi
else
    # ⛔⛔ Tạo nhãn TRƯỚC: `gh issue create --label <nhãn chưa có>` HỎNG. Nhãn `canh-cong-quet` của chuông
    #    T11.67 ⛔ tồn tại trong kho (đo 19/09) — nhánh mở issue của nó chưa từng chạy nên ⛔ ai biết.
    gh label create "$NHAN" --repo "$KHO" --color B60205 \
        --description "Chuông canh hạn chứng chỉ TLS (canh-chung-chi.yml)" --force >/dev/null 2>&1 || true
    gh issue create --repo "$KHO" \
        --title "⛔ Chứng chỉ TLS — $so_hong tên miền sắp hết hạn hoặc ⛔ đo được" \
        --label "$NHAN" \
        --body "$THAN"
fi

# ⛔ Thoát KHÁC 0: lượt chạy phải ĐỎ trên tab Actions, ⛔ chỉ để lại một issue (§10.42).
exit 1
