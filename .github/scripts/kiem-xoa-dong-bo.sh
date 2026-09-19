#!/usr/bin/env bash
#
# T11.95 — chặn `rsync --delete` của CD xoá một tệp mà MÁY CHỦ đang gọi.
#
# =============================================================================
# ⛔⛔ HÌNH DẠNG ĐÃ TRẢ GIÁ
# =============================================================================
#
# Host cần một tệp mà nhánh triển khai ⛔ ship, và `--delete` là bộ máy xoá nó
# trong im lặng. Đo 08/09 bằng thử khô ĐÚNG lệnh của CD: 4 tệp sẽ bị xoá ở lượt
# production kế tiếp, trong đó `gia-han-tls.sh` là script mà cron gia hạn TLS gọi
# ⇒ cron gọi tệp ⛔ tồn tại, hỏng câm, chứng chỉ chết 06/12. Cửa sổ ấy khép lại
# nhờ may (PR #105 mang tệp về) — ⛔ nhờ một bộ canh.
#
# =============================================================================
# CÁCH ĐO
# =============================================================================
#
#   $1  đầu ra `rsync --dry-run --itemize-changes` của ĐÚNG lệnh đồng bộ
#       (dòng xoá có dạng `*deleting   <đường dẫn tương đối>`)
#   $2  thứ máy chủ ĐANG GỌI: crontab của người triển khai + /etc/cron.d +
#       unit systemd — mọi chuỗi `/opt/songnhue/…` trong đó là một đường dẫn
#
# Giao nhau ≠ ∅ ⇒ thoát 1 và gọi tên từng cặp. Một đường dẫn nằm DƯỚI một thư mục
# sắp bị xoá cũng tính (xoá `backup/` là xoá mọi tệp bên trong).
#
# ⚠ Phạm vi (luật 28): crontab của ĐÚNG người triển khai — crontab của root cần
#   sudo, mà lượt CD ⛔ có sudo. Dòng cron đặt ở root thì bộ canh này ⛔ thấy.
#
set -euo pipefail

THU_KHO="${1:?Thiếu tệp đầu ra rsync --dry-run --itemize-changes}"
DANG_GOI="${2:?Thiếu tệp nội dung crontab/cron.d/systemd của máy chủ}"
[ -r "$THU_KHO" ] || { echo "::error::Không đọc được $THU_KHO" >&2; exit 3; }
[ -r "$DANG_GOI" ] || { echo "::error::Không đọc được $DANG_GOI" >&2; exit 3; }

# Đường dẫn rsync sắp xoá (bỏ tiền tố `*deleting` và khoảng trắng đệm).
SE_XOA="$(sed -n 's/^\*deleting[[:space:]]\{1,\}//p' "$THU_KHO")"
# Đường dẫn máy chủ đang gọi, quy về tương đối so với /opt/songnhue.
DANG_DUNG="$(grep -oE "/opt/songnhue/[^[:space:];|&<>\"']+" "$DANG_GOI" | sed 's#^/opt/songnhue/##' | sort -u || true)"

so_xoa="$(printf '%s\n' "$SE_XOA" | grep -c . || true)"
so_dung="$(printf '%s\n' "$DANG_DUNG" | grep -c . || true)"
echo "Thử khô: rsync sẽ xoá $so_xoa mục · máy chủ đang gọi $so_dung đường dẫn trong /opt/songnhue"

vi_pham=""
while IFS= read -r dung; do
    [ -n "$dung" ] || continue
    while IFS= read -r xoa; do
        [ -n "$xoa" ] || continue
        # Khớp ĐÚNG tệp, hoặc tệp nằm dưới một THƯ MỤC sắp bị xoá (rsync in thư mục kèm `/` ở cuối).
        case "$dung" in
            "$xoa" | "${xoa%/}"/*)
                vi_pham="$vi_pham  /opt/songnhue/$dung  ← rsync sẽ xoá \`$xoa\`"$'\n'
                break # mỗi đường dẫn báo MỘT lần, dù cả tệp lẫn thư mục cha đều sắp bị xoá
                ;;
        esac
    done <<<"$SE_XOA"
done <<<"$DANG_DUNG"

if [ -z "$vi_pham" ]; then
    echo "✓ Không tệp nào máy chủ đang gọi nằm trong danh sách sắp xoá."
    exit 0
fi

echo "::error::rsync --delete sẽ xoá tệp mà máy chủ ĐANG GỌI — DỪNG trước khi đồng bộ (T11.95)."
printf '%s' "$vi_pham"
echo ""
echo "Chưa có gì bị đổi trên máy chủ. Chọn MỘT trong hai rồi chạy lại:"
echo "  · đưa tệp ấy vào deploy/ của nhánh triển khai (nó là cấu hình máy chủ, phải nằm trong kho), hoặc"
echo "  · gỡ dòng cron / unit systemd đang gọi nó (nếu thật sự ⛔ còn cần)."
exit 1
