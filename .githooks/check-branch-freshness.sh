#!/usr/bin/env bash
# =============================================================================
# Bắt nhánh ĐÃ LỖI THỜI SAU SQUASH MERGE — trước khi nó thành xung đột giả
#
# Vì sao có phép kiểm này (sự việc 18/8/2026, tái diễn 2 lần trong một ngày):
#
#   `dev` bật `required_linear_history`, nên PR feature → `dev` phải merge bằng
#   **Squash**. Squash tạo một commit MỚI mang nội dung của nhánh nguồn nhưng
#   KHÔNG mang lịch sử của nó. Git vì thế không biết `dev` đã chứa công việc đó.
#
#   Dùng lại nhánh nguồn sau khi squash là chỗ hỏng. Hai lần đã xảy ra:
#     • Lần 1 — PR hiện **437 tệp** trong khi nhánh chỉ thật sự khác 8 tệp.
#     • Lần 2 — commit chồng lên nền chưa reset → **xung đột thật** ở 3 tệp,
#       dù nội dung hai bên giống hệt nhau.
#
#   Cả hai lần đều không có dấu hiệu nào cho tới lúc mở PR. Đó là lý do phép
#   kiểm nằm ở `pre-push`: sớm nhất mà vẫn thấy được cả hai nhánh.
#
# Cách phát hiện — chính xác, không dùng ngưỡng phỏng đoán:
#
#   GitHub hiển thị PR bằng diff BA CHẤM: `merge-base(base, head)...head`.
#   Nên nếu một tệp xuất hiện trong diff ba chấm mà nội dung của nó ĐÃ GIỐNG HỆT
#   trên base, thì đó là tệp PR sẽ bày ra một cách vô nghĩa — dấu hiệu chắc chắn
#   của nhánh lỗi thời (hoặc của công việc trùng lặp). Đếm đúng tập đó.
#
# Chạy tay: make branch-check      Tự kiểm: make branch-check-selftest
# Bỏ qua một lần: SKIP_BRANCH_CHECK=1 git push …
# =============================================================================
set -euo pipefail

BASE_REF="${BRANCH_CHECK_BASE:-origin/dev}"

# Nhánh dài hạn không bao giờ được reset — bỏ qua.
NHANH_DAI_HAN='^(dev|staging|production|master|main)$'

kiem_tra() {
    local base="$1" head="$2" nhan="${3:-}"

    local mb
    mb=$(git merge-base "$base" "$head" 2>/dev/null) || return 0

    # Nhánh chưa có commit riêng nào thì không có gì để nói.
    [ "$mb" = "$(git rev-parse "$head")" ] && return 0

    local pr_hien that_su thua
    pr_hien=$(git diff --name-only "$mb" "$head")
    that_su=$(git diff --name-only "$base" "$head")

    # Tệp PR sẽ bày ra NHƯNG nội dung đã giống base.
    thua=$(comm -23 <(printf '%s\n' "$pr_hien" | sort -u) \
                    <(printf '%s\n' "$that_su" | sort -u) | grep -c . || true)

    if [ "$thua" -eq 0 ]; then
        [ -n "$nhan" ] && echo "  ✓ $nhan"
        return 0
    fi

    local so_pr so_that
    so_pr=$(printf '%s\n' "$pr_hien" | grep -c . || true)
    so_that=$(printf '%s\n' "$that_su" | grep -c . || true)

    cat >&2 <<EOF

  ⛔ NHÁNH ĐÃ LỖI THỜI SAU SQUASH MERGE — dừng lại trước khi đẩy

     PR sẽ hiển thị        : $so_pr tệp
     Thật sự khác '$base' : $so_that tệp
     → $thua tệp bày ra vô nghĩa (nội dung đã giống hệt trên base)

     Nguyên nhân: '$base' đã nhận công việc của nhánh này qua một commit SQUASH,
     tức một commit khác danh tính. Git không biết hai bên là một, nên sẽ dựng
     lại toàn bộ khác biệt — và nếu bạn commit tiếp thì thành xung đột thật.

     Cách gỡ (giữ lại đúng phần mới):

       git log --oneline $base..HEAD      # xem commit nào THẬT SỰ mới
       git reset --hard $base
       git cherry-pick <những commit mới>
       git push --force-with-lease

     Tốt hơn nữa: bỏ hẳn nhánh này, cắt nhánh mới từ '$base' cho hạng mục sau.
     Squash cố ý vứt lịch sử nhánh đi — nên nhánh coi như đã chết khi merge xong.

     Bỏ qua một lần (biết mình đang làm gì): SKIP_BRANCH_CHECK=1 git push …

EOF
    return 1
}

# ---------------------------------------------------------------------------
# Tự kiểm — conventions.md §1.5: cơ chế canh gác phải chứng minh được nó BẮT
# ĐƯỢC vi phạm, không phải chỉ chạy mà không báo gì.
# ---------------------------------------------------------------------------
tu_kiem() {
    local tmp ket_qua=0
    tmp=$(mktemp -d)
    trap 'rm -rf "$tmp"' RETURN

    # ⚠ MỌI thứ phải chạy BÊN TRONG repo tạm. Bản đầu dựng repo trong một
    #   subshell rồi gọi `kiem_tra` ở ngoài — nó soi nhầm repo songnhue, không
    #   có nhánh `main` nên `merge-base` hỏng và hàm trả về "không vi phạm".
    #   Chính phép tự kiểm này bắt được, đúng thứ nó sinh ra để làm.
    cd "$tmp"
    git init -q -b main .
    git config user.email t@t; git config user.name t
    echo goc > a.txt; git add .; git commit -qm goc

    git checkout -qb nhanh
    echo moi > b.txt; git add .; git commit -qm "viec cua nhanh"

    # Giả lập squash merge: main nhận CÙNG nội dung, khác danh tính commit.
    git checkout -q main
    echo moi > b.txt; git add .; git commit -qm "squash cua nhanh"

    # 1) Phải BẮT ĐƯỢC nhánh lỗi thời.
    if kiem_tra main nhanh >/dev/null 2>&1; then
        echo "  ✗ KHÔNG bắt được nhánh lỗi thời sau squash — phép kiểm vô dụng" >&2
        ket_qua=1
    else
        echo "  ✓ bắt được nhánh lỗi thời sau squash"
    fi

    # 2) Phải IM LẶNG với nhánh sạch cắt thẳng từ base.
    git checkout -q -b sach main
    echo them > c.txt; git add .; git commit -qm "viec that su moi"
    if kiem_tra main sach >/dev/null 2>&1; then
        echo "  ✓ im lặng với nhánh cắt thẳng từ base"
    else
        echo "  ✗ BÁO ĐỘNG GIẢ trên nhánh sạch — sẽ bị tắt đi sau vài lần" >&2
        ket_qua=1
    fi

    return $ket_qua
}

if [ "${1:-}" = "--self-test" ]; then
    echo "Tự kiểm phép canh nhánh:"
    # Chạy trong repo tạm nên phải tách khỏi repo hiện tại.
    ( unset GIT_DIR GIT_WORK_TREE; tu_kiem )
    exit $?
fi

nhanh=$(git rev-parse --abbrev-ref HEAD)
if [[ "$nhanh" =~ $NHANH_DAI_HAN ]]; then
    exit 0
fi
if ! git rev-parse --verify -q "$BASE_REF" >/dev/null; then
    echo "  ⚠ Không có '$BASE_REF' — bỏ qua phép kiểm nhánh (chạy 'git fetch' trước)" >&2
    exit 0
fi

kiem_tra "$BASE_REF" HEAD "nhánh '$nhanh' còn tươi so với $BASE_REF"
