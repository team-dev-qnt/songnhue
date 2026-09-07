#!/usr/bin/env bash
# =============================================================================
# Sinh lại `backend/db-migration-checksums.txt` — vân tay SHA-256 của mọi
# migration/seed trong NGUỒN.
#
# ⚠ Chỉ quét `src/main/resources/db/`:
#   · `target/classes/db/` là bản sao lúc build — đưa vào là mỗi tệp có hai dòng,
#     và một lượt `mvn clean` làm bộ canh đỏ mà không ai sửa gì.
#   · `src/test/resources/db/migration/test/` chỉ áp lên CSDL dùng-một-lần của
#     Testcontainers, nên tính bất biến không có nghĩa ở đó.
#
# Dùng: make migration-manifest
# =============================================================================
set -euo pipefail

GOC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DICH="$GOC/backend/db-migration-checksums.txt"
NEO="# Thêm migration mới thì cập nhật tệp này bằng:  make migration-manifest"

bam() { if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1"; else shasum -a 256 "$1"; fi; }

grep -q "^$NEO$" "$DICH" 2>/dev/null || {
    echo "✗ $DICH thiếu dòng neo phần đầu — không dám ghi đè." >&2
    exit 1
}

sed -n "1,/^$NEO$/p" "$DICH" > "$DICH.moi"

cd "$GOC"
find backend -path '*/src/main/resources/db/*' -name 'V*.sql' | LC_ALL=C sort | while read -r f; do
    printf '%s  %s\n' "$(bam "$f" | cut -d' ' -f1)" "$f"
done >> "$DICH.moi"

mv "$DICH.moi" "$DICH"
echo "✓ $DICH — $(grep -c '^[0-9a-f]' "$DICH") vân tay"
