#!/usr/bin/env bash
# =============================================================================
# Giải "đỉnh dev tương ứng" theo CÂY TỆP
#
# Trả lời đúng một câu: *commit nào trên `dev` đã dựng ra đúng mã đang nằm ở
# `<ref>`?* — vì image GHCR chỉ được đóng gói ở `dev` và gắn tag theo SHA của
# `dev`, nên mọi chặng sau (`staging`, `production`) đều phải quy về một SHA của
# `dev` trước khi tra image hoặc hỏi kết quả CI.
#
# ⭐ VÌ SAO TÁCH RA MỘT TỆP: từ 6/9 có BA nơi cần đúng phép giải này —
#   `deploy-staging.yml` (tra image), `deploy-prod.yml` (tra image), và
#   `promotion-guard.yml` (hỏi check-run). Hai bản chép tay là hai nơi phải nhớ,
#   và luật 14 nói chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép
#   kiểm nhớ hộ. Ở đây rẻ hơn: một bản mã, ba nơi gọi.
#
# ═══ VÌ SAO TRẢ LỜI BẰNG CÂY TỆP, KHÔNG BẰNG QUAN HỆ CHA–CON ═══
#   Bản 1 tin `HEAD^2` vô điều kiện. PR bị **squash** → commit một cha → rơi về
#   SHA của chính staging, một SHA chưa bao giờ có trên `dev`, rồi báo "không tìm
#   thấy image" kèm ba bước chẩn đoán trỏ vào ba chỗ đều đang tốt (§10.42).
#
#   Bản 2 thử "lấy cha nào nằm trên dev". Sai êm hơn: cha thứ nhất của một commit
#   squash là tổ tiên chung cổ lỗ, mà nó *đúng là* nằm trên `dev`.
#
#   Cây tệp trả lời thẳng câu đang hỏi và không phụ thuộc vào việc ai bấm nút
#   merge nào.
#
# ═══ HỢP ĐỒNG ═══
#   Dùng:   giai-dinh-dev.sh <ref> [so_commit_quet=500] [ref_dev=origin/dev]
#   stdout: ĐÚNG MỘT dòng 40 ký tự hex — không gì khác. Nơi gọi đổ thẳng vào
#           `$GITHUB_OUTPUT` được.
#   stderr: mọi chẩn đoán, kể cả `::warning::` / `::error::` (runner đọc
#           workflow-command trên CẢ HAI luồng).
#   mã 0:   giải được.
#   mã 1:   đã quét <so_commit_quet> commit và KHÔNG cây nào khớp — "đã đo, không có".
#   mã 2:   KHÔNG TRA ĐƯỢC — thiếu tham số, hoặc ref không giải được (gần như luôn
#           là quên `git fetch` nhánh dev).
#
#   ⛔ 1 và 2 phải là hai mã KHÁC NHAU — luật 9. Khối inline cũ để git tự chết
#      bằng `fatal: ambiguous argument`, và người đọc đi tìm lỗi cây tệp trong khi
#      lỗi thật là một bước `fetch` thiếu.
#
#   `$GITHUB_STEP_SUMMARY` chỉ được ghi khi biến ĐƯỢC ĐẶT và KHÁC RỖNG — nhờ vậy
#   script chạy được ở máy và trong bài kiểm. `$GITHUB_OUTPUT` thì KHÔNG bao giờ
#   động tới: đó là chính sách của nơi gọi.
# =============================================================================
set -euo pipefail

ref="${1:-}"
so_quet="${2:-500}"
ref_dev="${3:-origin/dev}"

# `cat >>` đọc stdin thay vì ghi thẳng: `set -u` không nổ khi biến vắng, và nơi
# gọi không phải bọc thêm điều kiện ở mỗi chỗ.
ghi_tom_tat() {
    [ -n "${GITHUB_STEP_SUMMARY:-}" ] || { cat >/dev/null; return 0; }
    cat >> "$GITHUB_STEP_SUMMARY"
}

if [ -z "$ref" ]; then
    echo "::error::Thiếu tham số 1: ref cần giải (ví dụ HEAD, hoặc SHA đỉnh staging)." >&2
    echo "Dùng: giai-dinh-dev.sh <ref> [so_commit_quet=500] [ref_dev=origin/dev]" >&2
    exit 2
fi

case "$so_quet" in
    ''|*[!0-9]*)
        echo "::error::so_commit_quet phải là số nguyên dương, nhận '$so_quet'." >&2
        exit 2 ;;
esac
if [ "$so_quet" -le 0 ]; then
    # Cửa sổ rỗng thì phép quét luôn "không khớp" — một phép kiểm chạy qua tập
    # rỗng vẫn xanh trọn vẹn (luật 7). Chặn ở đây, đừng để nó thành mã 1.
    echo "::error::so_commit_quet phải > 0, nhận '$so_quet'." >&2
    exit 2
fi

for r in "$ref" "$ref_dev"; do
    if ! git rev-parse --verify --quiet "$r^{commit}" >/dev/null 2>&1; then
        echo "::error::Không giải được ref '$r' — CHƯA TRA ĐƯỢC GÌ, không phải 'không khớp'." >&2
        echo "  · 'actions/checkout' với fetch-depth: 0 chỉ lấy đủ lịch sử của NHÁNH ĐANG" >&2
        echo "    CHECKOUT, không lấy nhánh khác. Cần một bước riêng:" >&2
        echo "      git fetch --no-tags origin +refs/heads/dev:refs/remotes/origin/dev" >&2
        exit 2
    fi
done

cay="$(git rev-parse "$ref^{tree}")"
dinh=""

# ── Đường nhanh ──────────────────────────────────────────────────────────────
# Chỉ là PHỎNG ĐOÁN — nó vẫn phải qua đúng phép kiểm cây như mọi ứng viên khác.
goc="$(git merge-base "$ref" "$ref_dev" 2>/dev/null || true)"
if [ -n "$goc" ] && [ "$(git rev-parse "$goc^{tree}")" = "$cay" ]; then
    dinh="$goc"
    echo "Đỉnh dev: $dinh  (merge-base, cây tệp trùng khít)" >&2
fi

# ── Đường chung ──────────────────────────────────────────────────────────────
# `git log --format='%T %H'` in sẵn cây ở cột 1, nên cả cửa sổ chỉ tốn MỘT tiến
# trình thay vì một `git rev-parse` mỗi commit.
#
# ⛔ awk KHÔNG được `exit` sớm. Thoát giữa chừng làm `git log` ăn SIGPIPE (141),
#    `pipefail` nhận mã ấy, và `set -e` giết script — ngay trên đường đi ĐÚNG.
#    Dùng cờ `!thay` để giữ nguyên ngữ nghĩa "khớp đầu tiên thắng" mà vẫn đọc hết.
if [ -z "$dinh" ]; then
    dinh="$(git --no-pager log --format='%T %H' --max-count="$so_quet" "$ref_dev" \
            | awk -v t="$cay" '$1 == t && !thay { print $2; thay = 1 }')"

    if [ -n "$dinh" ]; then
        echo "::warning::Không nối được $ref với $ref_dev qua merge-base — PR nhiều khả năng đã bị squash/rebase." >&2
        echo "Đối chiếu theo cây tệp: nội dung trùng khít dev@$dinh nên dùng image của commit đó." >&2
        echo "⚠ Lần sau chọn 'Create a merge commit' khi merge vào staging (docs/branch-protection.md §2.3-b)." >&2

        # ⛔⛔ Cảnh báo này ĐÃ kêu đúng lúc — 31/8 23:54:54, dòng 194 của log lượt
        #   33452639951, gọi đúng tên nguyên nhân và nói đúng việc phải làm. Không ai
        #   đọc, vì job XANH. Hai ngày sau nó thành 13 tệp xung đột giả trên PR đề bạt
        #   #76, và xung đột giả ấy khoá luôn `Promotion guard` (§10.72).
        #
        #   Đây là cái giá ẩn của chính bản vá §10.42: đối chiếu theo cây tệp làm lượt
        #   deploy SỐNG SÓT qua một lượt squash — đúng và cần — nhưng nó đã biến một
        #   lần DỪNG HẲN thành một dòng ::warning:: trên một lượt chạy màu xanh. Làm
        #   một sự cố sống sót được mà không dời chuông sang chỗ khác là **gỡ mất
        #   chuông**. Nên ghi thẳng lên trang tóm tắt — chỗ người ta thật sự nhìn.
        ghi_tom_tat <<TOM_TAT
## ⚠ Lượt đề bạt này đã bị SQUASH

\`$ref\` không nối được với \`$ref_dev\` qua merge-base. Image vẫn giải đúng
theo cây tệp (\`dev@$dinh\`) nên lượt này an toàn — **nhưng gốc chung
của hai nhánh đã gãy**, và lượt đề bạt KẾ TIẾP sẽ đầy xung đột giả.

Chữa ngay, không đợi (§10.72):

\`\`\`bash
git checkout -b fix/noi-lai-goc-chung origin/dev
git merge -s ours origin/staging   # giữ nguyên cây của dev
git diff --name-only origin/dev HEAD   # PHẢI rỗng
\`\`\`

rồi mở PR vào \`dev\` và gộp bằng **Create a merge commit**.
TOM_TAT
    fi
fi

if [ -z "$dinh" ]; then
    echo "::error::Cây tệp của $ref không khớp commit nào trong $so_quet commit gần nhất của $ref_dev." >&2
    echo "" >&2
    echo "Nghĩa là $ref đang mang mã KHÁC với mọi thứ đã dựng image trên dev — có thể vì" >&2
    echo "PR bị squash/rebase rồi nhánh còn bị sửa thêm, hoặc có người đẩy thẳng vào nhánh." >&2
    echo "" >&2
    echo "⛔ Không đoán tiếp. Deploy một image không tương ứng với mã đang chạy là cách chắc" >&2
    echo "   chắn nhất để có một môi trường không ai giải thích được." >&2
    echo "Cách chữa: mở lại PR đề bạt và chọn **Create a merge commit**." >&2
    exit 1
fi

printf 'Đỉnh dev đang đề bạt: `%s`\n' "$dinh" | ghi_tom_tat
printf '%s\n' "$dinh"
