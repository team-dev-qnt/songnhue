#!/usr/bin/env bash
# =============================================================================
# Phân loại kết quả check-run của chặng trước: XONG-VÀ-ĐẠT / CHƯA XONG / HỎNG
# =============================================================================
#
# ⛔⛔ CHUYỆN ĐÃ XẢY RA (01/09/2026)
#
# PR #77 được gộp vào `dev` lúc 11:06:0x. GitHub cập nhật `dev`, PR đề bạt #76
# đổi head SHA, và `Promotion guard` chạy **lúc 11:06:19** — trong khi CI của
# `dev@4e564d9` vừa mới bắt đầu. API trả `conclusion: null` cho hai check bắt
# buộc, và bản cũ của bước này rơi vào nhánh `*)` với câu:
#
#     ##[error]Backend — build, lint, test của commit 4e564d9 kết thúc với 'null'.
#
# Câu ấy **nói sai chuyện đang xảy ra**. Không có gì "kết thúc" cả — phép kiểm
# đang chạy. Cổng đề bạt đỏ, PR #76 bị chặn, và người đọc log được dẫn đi tìm
# một lượt CI hỏng vốn không tồn tại.
#
# ⭐ ĐÂY LÀ LUẬT 9 ĐÚNG NGUYÊN VĂN: *một khẳng định không phân biệt được hai
#   trạng thái thì không khẳng định gì.* `null` và `failure` là hai chuyện khác
#   hẳn nhau — một cái bảo *đợi thêm*, một cái bảo *dừng lại và đi sửa* — mà bản
#   cũ trộn chung vào một nhánh `*)`.
#
# ⚠ Và nó là một CUỘC ĐUA, nên nó sẽ không xảy ra mọi lần: mở PR đề bạt lâu sau
#   lượt gộp thì CI đã xong và cổng xanh. Loại lỗi chỉ hiện ra khi hai việc xảy
#   ra sát nhau là loại dễ đóng hồ sơ nhầm nhất — "chạy lại thấy xanh rồi".
#
# ⭐ VÌ SAO TÁCH RA THÀNH SCRIPT
#
# Để kiểm chứng được bằng dữ liệu tổng hợp. Logic nằm trong một bước `run:` của
# workflow thì chỉ chạy được trên GitHub, và muốn thử nhánh `null` phải **thắng
# một cuộc đua** mới tái hiện được. Tách ra thì đưa thẳng ba dòng đầu vào là đo
# được cả ba nhánh (conventions.md §1.5, luật 1).
#
# Dùng: đưa các dòng `<tên check>=<conclusion>` qua stdin.
# Mã thoát:  0 = tất cả đã xong và đạt · 2 = còn check CHƯA XONG · 1 = có check HỎNG
# =============================================================================
set -uo pipefail

sha="${1:-<không rõ>}"

vao="$(cat)"
if [ -z "$(printf '%s' "$vao" | tr -d '[:space:]')" ]; then
    echo "::error::Không tìm thấy kết quả CI nào cho commit $sha."
    echo "Nhiều khả năng commit này chưa từng chạy qua pipeline ở dev."
    exit 1
fi

hong=0
cho=0
# ⚠ PHẢI đọc theo DÒNG, không `for muc in $vao`. Tên job có dấu cách
#   ("Backend — build, lint, test") mà bash tách từ theo IFS gồm cả khoảng trắng
#   — vòng lặp sẽ vỡ tên job thành 5 mảnh và cổng này LUÔN đỏ. (Thử ở máy dùng
#   zsh sẽ KHÔNG thấy lỗi — zsh không tách từ mặc định. Runner chạy bash.)
while IFS= read -r muc; do
    [ -n "$muc" ] || continue
    ten="${muc%=*}"
    ket="${muc##*=}"
    case "$ket" in
        # ⚠ `skipped` là ĐẠT, không phải hỏng. `ci.yml` chỉ chạy job nặng khi
        #   vùng tương ứng đổi, và job bị bỏ qua VẪN tạo check-run với
        #   `conclusion: "skipped"`. Cổng này hỏi "có phép kiểm nào HỎNG không",
        #   không hỏi "có chạy đủ mọi phép kiểm không".
        success | skipped | neutral)
            echo "  ✓ $ten = $ket"
            ;;
        # ⭐ `null` (JSON null, `--jq` in ra chuỗi "null") và chuỗi rỗng đều có
        #   nghĩa CHƯA XONG, không phải hỏng.
        null | "")
            echo "  ⏳ $ten còn đang chạy"
            cho=1
            ;;
        *)
            echo "::error::$ten của commit $sha kết thúc với '$ket'."
            hong=1
            ;;
    esac
done <<< "$vao"

# ⛔ HỎNG thắng CHƯA XONG. Một check đã đỏ thì đợi thêm không đổi được gì, và
#   báo "đang chạy" ở đó là mời người ta đợi một thứ sẽ không bao giờ xanh.
[ "$hong" -eq 0 ] || exit 1
[ "$cho" -eq 0 ] || exit 2

echo "✓ Commit $sha không có phép kiểm nào hỏng ở chặng trước"
exit 0
