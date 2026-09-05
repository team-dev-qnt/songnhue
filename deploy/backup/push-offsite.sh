#!/usr/bin/env bash
# =============================================================================
# BẢN SAO THỨ BA — đẩy ra ngoài nhà cung cấp đang thuê (WS-11)
#
# Chạy TRÊN VPS-2, ngay sau `pull-from-prod.sh`. Đặt vào cron 03:30.
#     30 3 * * * /opt/songnhue/backup/push-offsite.sh >> /var/log/songnhue-offsite.log 2>&1
#
# ⭐ VÌ SAO CẦN BẢN THỨ BA, khi đã có bản trên VPS-1 và bản kéo về VPS-2:
#
#   Hai bản kia nằm ở CÙNG MỘT nhà cung cấp, dưới CÙNG MỘT tài khoản do một
#   người đứng tên. Cái hỏng chung của chúng không phải là đĩa — mà là tài
#   khoản: tranh chấp thanh toán, thẻ hết hạn, khoá tài khoản, hoặc chiếm được
#   quyền đăng nhập. Khi đó "sao lưu khác máy" trở thành vô nghĩa vì cả hai máy
#   biến mất cùng lúc.
#
#   Bản thứ ba phải khác nhà cung cấp thì mới trả lời được câu hỏi đó.
#
# ⚠⚠ MÃ HOÁ TRƯỚC KHI RỜI MÁY — không thương lượng.
#   Bản dump chứa `employee_sensitive` và credential bên thứ 3. Đẩy nguyên bản
#   sang kho của một công ty khác là tự tạo ra một bản sao dữ liệu cá nhân nằm
#   ngoài tầm kiểm soát — vừa là rủi ro thật, vừa là chuyện phải giải trình theo
#   NĐ 13/2023. Mã hoá bằng `age` xong thì thứ rời khỏi máy chỉ là byte ngẫu
#   nhiên, và kho lưu ở đâu cũng không còn là câu hỏi về dữ liệu cá nhân.
#
#   ⛔ KHOÁ RIÊNG TUYỆT ĐỐI KHÔNG ĐƯỢC NẰM TRÊN VPS-2. Chỉ khoá CÔNG khai ở đây
#      (age mã hoá bằng khoá công khai). Để cả hai trên cùng máy thì mã hoá chỉ
#      còn là một bước tốn thời gian. Khoá riêng cất offline — xem
#      docs/runbook/xoay-khoa.md.
#
# Cần trên máy: age · rclone (hoặc mc nếu kho đích là S3).
# =============================================================================
set -euo pipefail

LOCAL_DIR="${LOCAL_BACKUP_DIR:-/srv/songnhue-backups}"
STAGE_DIR="${OFFSITE_STAGE_DIR:-/srv/songnhue-backups/.offsite}"
REMOTE="${OFFSITE_RCLONE_REMOTE:-}"          # ví dụ: b2:songnhue-dr
AGE_RECIPIENT="${OFFSITE_AGE_RECIPIENT:-}"   # khoá CÔNG khai age (age1...)
KEEP_DAYS="${OFFSITE_KEEP_DAYS:-90}"

# --- Chưa cấu hình thì nói to rồi thoát ĐẠT, không giả vờ đỏ ------------------
# Cùng khuôn với các workflow deploy: một bước chưa tới lượt mà báo đỏ thì người
# ta học cách bỏ qua màu đỏ, và tới lúc đỏ thật thì không ai nhìn nữa.
if [ -z "$REMOTE" ] || [ -z "$AGE_RECIPIENT" ]; then
    echo "⚠ Chưa cấu hình bản sao ngoài nhà cung cấp — BỎ QUA."
    echo "  Cần đặt trong /etc/songnhue-offsite.env:"
    echo "    OFFSITE_RCLONE_REMOTE=b2:ten-bucket"
    echo "    OFFSITE_AGE_RECIPIENT=age1..."
    echo "  Hướng dẫn: docs/deploy-guideline.md §9.2"
    exit 0
fi

command -v age    >/dev/null || { echo "✗ Thiếu age"    >&2; exit 1; }
command -v rclone >/dev/null || { echo "✗ Thiếu rclone" >&2; exit 1; }

mkdir -p "$STAGE_DIR"

# -----------------------------------------------------------------------------
# 1. Bản dump CSDL
# -----------------------------------------------------------------------------
PUSHED=0
while IFS= read -r dump; do
    base="$(basename "$dump")"
    enc="$STAGE_DIR/$base.age"

    # Đã đẩy rồi thì thôi — cron chạy hằng ngày trên cùng một thư mục giữ 30 bản.
    if rclone lsf "$REMOTE/db/$base.age" >/dev/null 2>&1; then
        continue
    fi

    # ⚠ Kiểm checksum TRƯỚC khi mã hoá. Đẩy một bản đã hỏng ra kho ngoài thì ta
    #   có ba bản sao của cùng một tệp rác, và chỉ biết vào đúng ngày cần dùng.
    if [ -f "$dump.sha256" ] && ! (cd "$(dirname "$dump")" && sha256sum -c --status "$dump.sha256"); then
        echo "✗ $base sai checksum — KHÔNG đẩy đi. Kiểm tra ngay đường sao lưu." >&2
        continue
    fi

    age --encrypt --recipient "$AGE_RECIPIENT" --output "$enc" "$dump"
    rclone copyto "$enc" "$REMOTE/db/$base.age" --checksum
    rm -f "$enc"
    PUSHED=$((PUSHED + 1))
    echo "  ✓ db/$base.age"
done < <(find "$LOCAL_DIR" -maxdepth 1 -name '*.dump' -type f)

# -----------------------------------------------------------------------------
# 2. Tệp đính kèm trong MinIO
#
# ⚠ Sao lưu CSDL KHÔNG bao gồm tệp. Hồ sơ công trình, ảnh, tài liệu đều nằm
#   trong MinIO, và trước bước này chúng chưa có một bản sao nào ở đâu cả —
#   khôi phục CSDL xong sẽ được một hệ thống đầy đủ bản ghi mà mọi đường tải về
#   đều 404. Đây là lỗ hổng dễ bỏ quên nhất của mọi kế hoạch sao lưu.
#
# ⛔ Tệp đính kèm KHÔNG mã hoá bằng age ở đây (rclone đồng bộ tăng dần, mã hoá
#    từng tệp sẽ phá cơ chế đó). Thay vào đó dùng kho đích có mã hoá phía máy
#    chủ + khoá ứng dụng CHỈ ĐỌC trên nguồn. Đánh đổi có ý thức, ghi ra để
#    người sau không tưởng là sơ suất.
# -----------------------------------------------------------------------------
if [ -n "${OFFSITE_MINIO_REMOTE:-}" ]; then
    for bucket in "${MINIO_BUCKET_MEDIA:-songnhue-media}" "${MINIO_BUCKET_REPORT:-songnhue-report}"; do
        rclone sync "$OFFSITE_MINIO_REMOTE/$bucket" "$REMOTE/files/$bucket" \
            --checksum --fast-list --transfers 4
        echo "  ✓ files/$bucket"
    done
else
    echo "  ⚠ Chưa cấu hình OFFSITE_MINIO_REMOTE — TỆP ĐÍNH KÈM CHƯA CÓ BẢN SAO NÀO."
fi

# -----------------------------------------------------------------------------
# 3. Dọn bản quá hạn ở kho ngoài
# -----------------------------------------------------------------------------
rclone delete "$REMOTE/db" --min-age "${KEEP_DAYS}d" || true

# -----------------------------------------------------------------------------
# 4. Metric cho Prometheus (textfile collector) — Prometheus chạy ngay trên
#    VPS-2 nên chuỗi giám sát này sống sót khi VPS-1 chết, đúng lúc cần nó.
# -----------------------------------------------------------------------------
METRICS_FILE="${METRICS_FILE:-/var/lib/node_exporter/textfile/songnhue_backup_offsite_push.prom}"
if [ -d "$(dirname "$METRICS_FILE")" ]; then
    {
        echo "# HELP songnhue_backup_offsite_push_last_success_timestamp Lượt đẩy bản sao ra ngoài nhà cung cấp thành công gần nhất"
        echo "# TYPE songnhue_backup_offsite_push_last_success_timestamp gauge"
        echo "songnhue_backup_offsite_push_last_success_timestamp $(date +%s)"
    } > "$METRICS_FILE.tmp"
    mv "$METRICS_FILE.tmp" "$METRICS_FILE"
fi

echo "✓ Đã đẩy $PUSHED bản dump mới ra $REMOTE"
