#!/usr/bin/env bash
# =============================================================================
# ĐƯA BẢN SAO LƯU RA KHỎI MÁY CHỦ CSDL — chạy TRÊN VM-3 (WS-7 / T7.2)
#
# ⭐ KÉO VỀ, KHÔNG ĐẨY ĐI. Đây là quyết định bảo mật, không phải sở thích:
#
#   • Đẩy (VM-1 → VM-3) thì VM-1 phải có khoá SSH ghi được vào kho sao lưu. Ai
#     chiếm được VM-1 — chính là máy chạy ứng dụng phơi ra Internet — cũng chiếm
#     luôn khả năng XOÁ mọi bản sao lưu. Mã hoá tống tiền làm đúng việc đó đầu
#     tiên, và khi ấy toàn bộ chiến lược phục hồi bằng 0.
#   • Kéo (VM-3 → VM-1) thì VM-1 không giữ khoá nào cả. VM-3 chỉ cần quyền ĐỌC
#     trên VM-1. Chiếm được VM-1 vẫn không chạm được vào kho sao lưu.
#
# Không có PITR/replica (architecture-review.md §6.5) nên kho này là bản sao duy
# nhất nằm ngoài máy chủ CSDL. Nó chết thì RPO 24h trên giấy thành RPO vô hạn.
#
#   Đặt vào cron của VM-3, 03:00 hằng ngày (sau job sao lưu 02:00):
#     0 3 * * * /opt/songnhue/backup/pull-from-prod.sh >> /var/log/songnhue-pull.log 2>&1
# =============================================================================
set -euo pipefail

: "${PROD_HOST:?Thiếu PROD_HOST — máy chủ chứa bản sao lưu gốc}"
PROD_USER="${PROD_USER:-songnhue-backup}"
REMOTE_DIR="${REMOTE_BACKUP_DIR:-/var/lib/songnhue/backup}"
LOCAL_DIR="${LOCAL_BACKUP_DIR:-/srv/songnhue-backups}"
RETENTION_DAYS="${OFFSITE_RETENTION_DAYS:-30}"

# Tệp metric cho node_exporter đọc (textfile collector). Prometheus chạy ngay
# trên VM-3 nên chuỗi giám sát này SỐNG SÓT khi VM-1 chết — đúng lúc cần nó.
METRICS_FILE="${METRICS_FILE:-/var/lib/node_exporter/textfile/songnhue_backup_offsite.prom}"

mkdir -p "$LOCAL_DIR"

write_metric() {
    local ok="$1"
    [ -d "$(dirname "$METRICS_FILE")" ] || return 0
    {
        echo "# HELP songnhue_backup_offsite_last_success_timestamp Mốc kéo bản sao lưu về VM-3 thành công gần nhất"
        echo "# TYPE songnhue_backup_offsite_last_success_timestamp gauge"
        if [ "$ok" = "1" ]; then
            echo "songnhue_backup_offsite_last_success_timestamp $(date +%s)"
        fi
        echo "# HELP songnhue_backup_offsite_files Số bản sao lưu đang giữ ngoài máy chủ CSDL"
        echo "# TYPE songnhue_backup_offsite_files gauge"
        echo "songnhue_backup_offsite_files $(find "$LOCAL_DIR" -maxdepth 1 -name '*.dump' -type f | wc -l | tr -d ' ')"
    } > "$METRICS_FILE.tmp"
    mv "$METRICS_FILE.tmp" "$METRICS_FILE"
}

echo "[$(date -Iseconds)] Kéo bản sao lưu từ $PROD_USER@$PROD_HOST:$REMOTE_DIR"

# --ignore-existing: bản dump không bao giờ bị sửa sau khi ghi, nên tệp đã có là
# tệp đã xong — không tải lại. Băng thông nội bộ vẫn là băng thông.
if ! rsync -av --ignore-existing --timeout=1800 \
        --include='songnhue-*.dump' --include='songnhue-*.dump.sha256' --exclude='*' \
        "$PROD_USER@$PROD_HOST:$REMOTE_DIR/" "$LOCAL_DIR/"; then
    echo "✗ rsync thất bại" >&2
    write_metric 0
    exit 1
fi

# -----------------------------------------------------------------------------
# Đối chiếu checksum của TỆP VỪA KÉO VỀ.
# Bản sao lưu đúng ở máy nguồn mà hỏng trên đường truyền thì vẫn là bản sao lưu
# hỏng — và đó lại là bản sẽ được dùng, vì nó là bản duy nhất còn lại khi VM-1
# đã chết.
# -----------------------------------------------------------------------------
FAILED=0
while IFS= read -r sumfile; do
    dump="${sumfile%.sha256}"
    [ -f "$dump" ] || continue
    if ! (cd "$LOCAL_DIR" && sha256sum --check --status "$(basename "$sumfile")"); then
        echo "  ✗ Checksum KHÔNG khớp: $(basename "$dump") — xoá để lượt sau tải lại" >&2
        rm -f "$dump" "$sumfile"
        FAILED=1
    fi
done < <(find "$LOCAL_DIR" -maxdepth 1 -name '*.dump.sha256' -type f)

DELETED="$(find "$LOCAL_DIR" -maxdepth 1 -name 'songnhue-*.dump*' -type f \
    -mtime "+$RETENTION_DAYS" -print -delete | wc -l | tr -d ' ')"
[ "$DELETED" -gt 0 ] && echo "  Đã xoá $DELETED tệp quá $RETENTION_DAYS ngày"

if [ "$FAILED" -ne 0 ]; then
    write_metric 0
    exit 1
fi

write_metric 1
echo "✓ Kho ngoài máy chủ CSDL: $(find "$LOCAL_DIR" -maxdepth 1 -name '*.dump' -type f | wc -l | tr -d ' ') bản tại $LOCAL_DIR"
