#!/usr/bin/env bash
# =============================================================================
# Cổng: nhánh ĐÍCH của một lượt đề bạt không được có commit riêng của nó
# =============================================================================
#
# ⛔⛔ CHUYỆN ĐÃ XẢY RA (01/09/2026)
#
# PR đề bạt #72 (`dev → staging`) được gộp bằng **Squash and merge**. GitHub tạo
# ra `b4a0ac0` — một commit MỘT CHA mang đúng nội dung của `dev@2add2bf` nhưng
# không nối vào lịch sử `dev`. Về nội dung thì không mất gì; về đồ thị thì gốc
# chung của hai nhánh **kẹt lại** ở `bbe0b50` (30/8).
#
# Lượt đề bạt kế tiếp vì thế phải áp lại nguyên delta của #70 lên một `staging`
# vốn ĐÃ có nó: **13 tệp xung đột giả**, PR #76 nằm ở `CONFLICTING`.
#
# ⚠ Và cái đắt hơn: **xung đột giả làm chính cổng này không chạy**. GitHub dựng
#   `refs/pull/N/merge` để chạy workflow `pull_request`; PR đụng độ thì ref ấy
#   không dựng được, nên `Promotion guard` KHÔNG BAO GIỜ được lên lịch và context
#   bắt buộc treo vĩnh viễn ở *"Expected — waiting for status to be reported"*.
#   Không có một dòng đỏ nào để đọc. Đúng hình dạng §10.63.
#
#   ⇒ Nên bộ canh này chặn ở lượt đề bạt KẾ TIẾP, không phải lượt gây ra lỗi.
#     Đó là giới hạn của chính nó, ghi ra đây để không ai đọc cái xanh của nó
#     thành lời bảo đảm rằng nút Squash đã bị khoá (luật 28). GitHub không có
#     tuỳ chọn tắt squash cho riêng một nhánh.
#
# ⭐ BẤT BIẾN ĐO ĐƯỢC
#
#   Luồng đã chốt là dev → staging → production, một chiều. Nội dung chỉ SINH RA
#   ở `dev`. Vậy nhánh đích không được sở hữu một commit **không-phải-merge** nào
#   mà nhánh nguồn không có. Commit merge thì được — đó chính là các lượt đề bạt
#   trước, và chúng vốn không tồn tại ở nhánh nguồn.
#
#   Đo 01/09 trước bản vá: 1 (b4a0ac0). Sau bản vá: 0.
#
# Dùng: kiem-goc-chung.sh <nhánh-nguồn> <nhánh-đích>
#   VD: kiem-goc-chung.sh origin/dev origin/staging
# =============================================================================
set -euo pipefail

nguon="${1:?thiếu tham số 1: nhánh nguồn (vd origin/dev)}"
dich="${2:?thiếu tham số 2: nhánh đích (vd origin/staging)}"

for ref in "$nguon" "$dich"; do
    git rev-parse --verify --quiet "$ref^{commit}" >/dev/null || {
        echo "::error::Không giải được ref '$ref'. Bước checkout có lấy đủ lịch sử không (fetch-depth: 0)?"
        exit 1
    }
done

# ⚠ In ra con số ĐO ĐƯỢC trước mọi kết luận (luật 10): một `rev-list` chạy trên
#   bản clone nông trả về rỗng, và rỗng thì trông y hệt "sạch".
tong=$(git rev-list --count "$nguon".."$dich")
rieng=$(git rev-list --no-merges "$nguon".."$dich")
so_rieng=$(printf '%s' "$rieng" | grep -c . || true)

echo "Gốc chung: $(git merge-base "$nguon" "$dich")"
echo "$dich hơn $nguon: $tong commit, trong đó $so_rieng commit không-phải-merge"

if [ "$so_rieng" -eq 0 ]; then
    echo "✓ '$dich' không có commit riêng — gốc chung còn nguyên, lượt đề bạt sẽ hợp nhất sạch"
    exit 0
fi

echo "::error::'$dich' có $so_rieng commit KHÔNG-PHẢI-MERGE mà '$nguon' không có."
while IFS= read -r sha; do
    [ -n "$sha" ] || continue
    echo "    $(git log -1 --format='%h %ad %s' --date=short "$sha")"
done <<< "$rieng"

cat <<'HUONGDAN'

Gần như chắc chắn một PR đề bạt đã được gộp bằng **Squash and merge**. Squash tạo
một commit mới không nối vào lịch sử nhánh nguồn, nên gốc chung của hai nhánh
đứng yên, và mọi lượt đề bạt sau đó phải áp lại các thay đổi mà nhánh đích ĐÃ có
— ra xung đột giả, và xung đột giả thì chặn luôn chính cổng này.

⛔ PR đề bạt (`dev → staging`, `staging → production`) phải gộp bằng
   **Create a merge commit**. Squash chỉ dành cho PR tính năng vào `dev`.

⚠⚠ ĐỌC KỸ TRƯỚC KHI CHỮA — `dev` bật `required_linear_history: true`, tức là
   GitHub **KHÔNG cho merge commit vào `dev`**. Cách chữa hiển nhiên nhất
   (`git merge -s ours origin/staging` rồi mở PR vào `dev`) **không gộp được**:
   squash và rebase đều xoá đúng cái quan hệ cha cần dựng. Đo 01/09 bằng
   `branches/dev/protection`. Vì vậy chỉ còn hai lối, cả hai đều cần quyền quản
   trị kho — chọn một, đừng tự chế lối thứ ba:

   [A] Tạm tắt `required_linear_history` trên `dev`, gộp PR chữa bằng
       **Create a merge commit**, rồi bật lại. Giữ nguyên cổng đề bạt.

         gh api -X PATCH repos/<kho>/branches/dev/protection/required_linear_history -X DELETE
         # … gộp PR bằng merge commit …
         gh api -X POST  repos/<kho>/branches/dev/protection/required_linear_history

   [B] Quản trị viên gộp thẳng trên `staging` (kho đặt `enforce_admins: false`
       nên admin đi qua được yêu cầu PR). Một lệnh, nhưng **bỏ qua cổng
       `Promotion guard`** — chỉ làm khi đã tự xác minh SHA của `dev` xanh CI:

         git checkout staging && git pull
         git merge --no-commit --no-ff origin/dev || true
         git checkout origin/dev -- .          # ép cây về ĐÚNG cây của dev
         git rm -r --cached . -q && git add -A # dọn tệp staging có mà dev không
         test "$(git write-tree)" = "$(git rev-parse 'origin/dev^{tree}')" \
              || { echo 'CÂY KHÔNG KHỚP — DỪNG'; exit 1; }
         git commit -m 'Merge dev vào staging — nối lại gốc chung'
         git push origin staging

   ⛔ Dù chọn lối nào, **đo lại bằng chính script này** trước khi coi là xong.
HUONGDAN
exit 1
