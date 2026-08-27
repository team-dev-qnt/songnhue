#!/usr/bin/env bash
# =============================================================================
# Migration MỚI phải có số hiệu LỚN HƠN mọi migration đã có trên nhánh nền.
#
# ── Chuyện đã xảy ra — 27/8, §10.66 ─────────────────────────────────────────
#
#   CD Staging đỏ ở bước "Triển khai". Migrator không khởi động được:
#
#     Validate failed: Migrations have failed validation
#     Detected resolved migration not applied to database: 202608272320.
#     Detected resolved migration not applied to database: 202608272321.
#
#   Quy ước đánh số của dự án là `V<YYYYMMDD><số thứ tự 4 chữ số>` — …281036,
#   …281037, …281038. Hai tệp thêm ở PR #53 dùng GIỜ-PHÚT (…272320 = 23:20 ngày
#   27) nên rơi xuống DƯỚI ba mã đã áp lên staging. Flyway gọi đó là out-of-order
#   và từ chối chạy.
#
#   Cùng lượt ấy còn một hỏng thứ hai, im lặng hơn: seed …2320 `UPDATE` một khoá
#   `settings` mà migration …2321 mới `INSERT` ra. Trên CSDL trắng, `UPDATE` chạm
#   ĐÚNG 0 hàng — không lỗi, không cảnh báo, khối ảnh trang chủ rỗng vĩnh viễn.
#   Số hiệu sai thứ tự làm hỏng CẢ tính áp được LẪN ý nghĩa nghiệp vụ.
#
# ── ⚠⚠ Vì sao bộ test không bao giờ bắt được ────────────────────────────────
#
#   Mọi bài kiểm chạy migration từ CSDL RỖNG. Trên CSDL rỗng thì KHÔNG CÓ khái
#   niệm out-of-order: Flyway áp tuần tự từ đầu và xanh trọn vẹn. 688 bài BE xanh
#   trong khi lượt deploy kế tiếp chết ngay ở `validate`. Cùng một lớp mù với
#   §10.65 (checksum) — nó chỉ hiện ra ở một CSDL ĐÃ SỐNG.
#
#   Vì thế bảo đảm không nằm ở một bài kiểm hành vi, mà ở phép so với NHÁNH NỀN:
#   thứ đã merge vào `dev` là thứ đã (hoặc sắp) áp lên staging.
#
# ── Giới hạn của bộ canh này — nói ra thay vì để người đọc tự suy (luật 28) ──
#
#   · Nó KHÔNG bắt việc xoá/đổi tên một migration đã phát hành. Đó là việc của
#     `MigrationImmutabilityTest` (vế `biXoa`) + `db-migration-checksums.txt`.
#   · Nó lấy `dev` làm nền, không phải staging/production. Nếu một môi trường
#     chạy trước `dev` thì phép so này hụt — hiện không có môi trường nào như vậy.
#   · Nó không biết migration mới có ĐÚNG nghiệp vụ hay không, chỉ biết thứ tự.
#
# Dùng:  make migration-order          (nền mặc định: origin/dev)
#        backend/tools/kiem-thu-tu-migration.sh <ref>
# =============================================================================
set -euo pipefail

GOC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$GOC"

BASE="${1:-${MIGRATION_BASE_REF:-origin/dev}}"
MAU='src/main/resources/db/.*/V[0-9][0-9]*__.*\.sql$'

version_tu_duong() { sed -n 's|.*/V\([0-9][0-9]*\)__.*\.sql$|\1|p'; }

# ── 1. Giải nhánh nền ────────────────────────────────────────────────────────
# Không giải được thì ĐỎ, không bỏ qua: một bộ canh im lặng bỏ qua đúng bằng
# không có bộ canh (luật 24).
if ! NEN="$(git rev-parse --verify --quiet "${BASE}^{commit}")"; then
    echo "✗ Không giải được nhánh nền '$BASE'." >&2
    echo "  Thử: git fetch origin dev     — hoặc truyền nền khác: $0 <ref>" >&2
    exit 1
fi

# Push thẳng lên chính nhánh nền (CI chạy sau squash merge vào `dev`): lúc ấy
# origin/dev CHÍNH LÀ HEAD, so với chính mình thì tập "migration mới" luôn rỗng
# và bộ canh xanh mà không soi gì (luật 7). Lùi một commit.
if [ "$NEN" = "$(git rev-parse HEAD)" ]; then
    BASE="HEAD^"
    NEN="$(git rev-parse --verify "${BASE}^{commit}")"
fi

# ── 2. Đo hai vế ─────────────────────────────────────────────────────────────
CU="$(git ls-tree -r --name-only "$NEN" | grep -E "$MAU" | version_tu_duong | sort -u)"
MOI="$(find backend -path '*/src/main/resources/db/*' -name 'V*.sql' | version_tu_duong | sort -u)"

SO_CU="$(printf '%s\n' "$CU"  | grep -c . || true)"
SO_MOI="$(printf '%s\n' "$MOI" | grep -c . || true)"

# Chặn xanh-trên-tập-rỗng ở CẢ HAI vế: đường dẫn đổi làm `grep`/`find` trượt thì
# phải ĐỎ, chứ không phải "không thấy vi phạm nào".
if [ "$SO_CU" -lt 10 ] || [ "$SO_MOI" -lt 10 ]; then
    echo "✗ Quét ra quá ít migration (nền $SO_CU, cây làm việc $SO_MOI) — đường dẫn đã đổi." >&2
    echo "  SỬA bộ canh này cho khớp, đừng để nó xanh trên tập rỗng." >&2
    exit 1
fi

DINH_CU="$(printf '%s\n' "$CU" | sort -n | tail -1)"
THEM="$(comm -13 <(printf '%s\n' "$CU") <(printf '%s\n' "$MOI"))"
SO_THEM="$(printf '%s\n' "$THEM" | grep -c . || true)"

echo "  nền '$BASE' ($(git rev-parse --short "$NEN")): $SO_CU migration, đỉnh $DINH_CU"
echo "  cây làm việc: $SO_MOI migration, $SO_THEM mới"

# ── 3. Phán ──────────────────────────────────────────────────────────────────
LOI=0
for v in $THEM; do
    if [ "$v" -le "$DINH_CU" ]; then
        echo "  ✗ V$v  ≤ đỉnh nền V$DINH_CU"
        LOI=1
    else
        echo "  ✓ V$v"
    fi
done

if [ "$LOI" -ne 0 ]; then
    cat >&2 <<'MSG'

⛔ Có migration mới đánh số THẤP HƠN migration đã có trên nhánh nền.

   Mọi CSDL đã áp mã cao hơn sẽ từ chối khởi động:

     Validate failed: Migrations have failed validation
     Detected resolved migration not applied to database: <mã>.

   Bộ test KHÔNG bắt được lớp lỗi này — nó chạy từ CSDL rỗng, mà trên CSDL rỗng
   thì không có out-of-order (§10.66).

   Cách sửa: đổi tên tệp cho số hiệu lớn hơn đỉnh nền. Quy ước của dự án là
   `V<YYYYMMDD><số thứ tự 4 chữ số>` — số thứ tự chạy tiếp, KHÔNG PHẢI giờ-phút.
   Đổi tên xong nhớ chạy `make migration-manifest`.

   ⚠ Chỉ đổi tên được khi tệp CHƯA từng áp ở đâu. Nếu nó đã lên staging thì viết
     một migration MỚI thay vì đổi số — xem `docs/coding-guide.md`.
MSG
    exit 1
fi

echo "✓ Thứ tự migration hợp lệ."
