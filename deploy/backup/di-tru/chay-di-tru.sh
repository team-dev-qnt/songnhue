#!/usr/bin/env bash
# =============================================================================
# CHẠY LƯỢT DI TRÚ DỮ LIỆU staging → production — MỘT LỆNH DUY NHẤT
#
# ⚠⚠ BƯỚC NÀY GHI ĐÈ TOÀN BỘ CSDL PRODUCTION. Không hoàn tác được bằng Ctrl-C.
#
# Đã diễn tập trọn vẹn ngày 08/09/2026 trên CSDL nháp `songnhue_thu` — một bản
# sao ĐÚNG của production (cùng icu=vi-VN, cùng datacl, cùng nspacl, cùng 107
# bảng, cùng 15 phân mảnh). Lượt diễn tập ấy tìm ra ba khuyết tật CHẶN mà không
# lượt rà tài liệu nào thấy được; cả ba đã vá và đo lại.
#
#   Chạy trên VPS-1:
#     cd /opt/songnhue && ./backup/chay-di-tru.sh
#
#   Quay lui (nếu cần), cũng trên VPS-1:
#     XAC_NHAN=songnhue ENV_FILE=/opt/songnhue/.env ./backup/khoi-phuc-qua-container.sh /var/lib/songnhue/backup/predeploy-songnhue-20260907-232828.dump
# =============================================================================
set -euo pipefail

# ⛔ Tệp SQL sinh ra ở các bước dưới mang TOÀN BỘ CSDL dạng THUẦN — gồm
#    `users.password_hash` và `user_totp.secret_encrypted`. Với umask mặc định
#    chúng ra `644`/`664`, tức MỌI user trên máy đọc được. Đo ngày 08/09/2026 sau
#    lượt di trú: bốn tệp trung gian 4 MB nằm ở `/var/lib/songnhue/backup` với
#    quyền 644, chứa 4 lần `password_hash` mỗi tệp.
umask 077

B="${BACKUP_DIR:-/var/lib/songnhue/backup}"
DUMP="${1:-$B/di-tru-staging-20260907-2330.dump}"
DANH_TINH="${DANH_TINH:-$B/danh-tinh-prod-20260907.sql}"
KHOI_VA="${KHOI_VA:-$B/sau-khoi-phuc-production.sql}"
GHEP="$B/.khoi-va.sql"
trap 'rm -f "$GHEP"' EXIT   # tệp ghép chứa hash mật khẩu + bí mật TOTP; đừng để lại

for f in "$DUMP" "$DANH_TINH" "$KHOI_VA"; do
    [ -r "$f" ] || { echo "✗ Không đọc được $f" >&2; exit 1; }
done

T0=$(date +%s)
echo "════ $(date '+%H:%M:%S') BẮT ĐẦU DI TRÚ ════"

# ⚠ Mốc chèn phải NEO `^…$`: chú thích trong tệp cũng chứa chuỗi mốc, và mẫu
#   không neo sẽ chèn tệp danh tính HAI lần → trùng khoá uq_user_totp_user_id.
sed -e "/^-- @@CHEN_DANH_TINH@@\$/r $DANH_TINH" "$KHOI_VA" > "$GHEP"
SO_TOTP=$(grep -c '^INSERT INTO user_totp' "$GHEP" || true)
SO_MA=$(grep -c '^INSERT INTO user_recovery_codes' "$GHEP" || true)
echo "→ Khối vá: $(grep -c . "$GHEP") dòng · user_totp=$SO_TOTP (phải 1) · mã khôi phục=$SO_MA (phải 10)"
[ "$SO_TOTP" -eq 1 ] && [ "$SO_MA" -eq 10 ] || {
    echo "✗ Ghép khối vá sai số lượng. DỪNG trước khi đụng vào dữ liệu." >&2; exit 1; }

echo "→ Dừng ứng dụng (cổng sẽ trả lỗi trong ~2 phút)"
docker stop songnhue-app >/dev/null
echo "   ✓ songnhue-app đã dừng"

set +e
XAC_NHAN=songnhue ENV_FILE=/opt/songnhue/.env \
    "$(dirname "$0")/khoi-phuc-qua-container.sh" "$DUMP" --sau "$GHEP" < /dev/null
MA=$?
set -e

echo "→ Bật lại ứng dụng"
docker start songnhue-app >/dev/null
# public-web giữ đệm ISR của trang cũ; khởi động lại là cách rẻ nhất để xoá
docker restart songnhue-public-web >/dev/null
echo "   ✓ đã bật songnhue-app và khởi động lại songnhue-public-web"

T1=$(date +%s)
echo "════ $(date '+%H:%M:%S') KẾT THÚC · $(( T1 - T0 )) giây · mã thoát khôi phục = $MA ════"
[ "$MA" -eq 0 ] || {
    echo "⛔ KHÔI PHỤC HỎNG. Giao dịch đã cuộn ngược nên dữ liệu cũ CÒN NGUYÊN." >&2
    echo "   Đọc log ở trên, đừng bỏ --exit-on-error hay --single-transaction để 'chữa nhanh'." >&2
    exit "$MA"; }
echo "Việc tiếp theo: chạy phần NGHIỆM THU trong docs/runbook/di-tru-staging-len-production.md"
