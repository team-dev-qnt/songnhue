#!/usr/bin/env bash
# =============================================================================
# Kiểm chứng: KHOÁ KHÔNG NẰM TRONG BẢN SAO LƯU (WS-7 / T7.2 · DoD mục 13d)
#
# Cam kết ở architecture-review.md §6.5: khoá AES-256-GCM và khoá ký JWT nằm ở
# /opt/songnhue/keys/, NGOÀI bản sao lưu CSDL. Lý do: bản dump được sao chép
# sang máy khác, giữ 30 ngày, và ai đọc được nó thì đọc được toàn bộ dữ liệu.
# Nếu khoá nằm chung trong đó thì lớp mã hoá trường nhạy cảm HR (NĐ 13/2023)
# mất sạch tác dụng — kẻ có bản backup có cả khoá lẫn dữ liệu.
#
# ⚠ Đây là loại cam kết dễ đúng lúc viết và âm thầm sai về sau: chỉ cần ai đó
#   thêm một dòng vào bảng `settings` để "tiện" là khoá lọt vào mọi bản dump kể
#   từ đêm đó, không lỗi nào báo ra. Nên nó phải được KIỂM MỖI LẦN sao lưu, chứ
#   không phải kiểm một lần rồi tin (conventions.md §1.5).
#
#   Dùng:  deploy/backup/verify-no-keys.sh <đường-dẫn-bản-dump>
# =============================================================================
set -euo pipefail

DUMP="${1:?Dùng: verify-no-keys.sh <đường-dẫn-bản-dump>}"

if ! command -v pg_restore >/dev/null 2>&1; then
    echo "  ⚠ Không có pg_restore — BỎ QUA việc kiểm khoá trong bản dump." >&2
    echo "    Đây là bỏ qua một kiểm chứng bảo mật, không phải 'không sao'." >&2
    exit 0
fi

# Giải nén ra dạng văn bản rồi soi. Không ghi ra tệp tạm: nội dung CSDL đầy đủ
# nằm trên đĩa dưới dạng đọc được là đúng thứ ta đang cố tránh.
FOUND=0

check() {
    local pattern="$1" what="$2"
    if pg_restore --file=- "$DUMP" 2>/dev/null | grep -qiE "$pattern"; then
        echo "  ✗ PHÁT HIỆN $what trong bản sao lưu" >&2
        FOUND=1
    fi
}

# Khoá riêng dạng PEM — JWT_PRIVATE_KEY_PATH trỏ tới tệp, nội dung KHÔNG được
# lọt vào CSDL dưới bất kỳ hình thức nào.
check '-----BEGIN [A-Z ]*PRIVATE KEY-----' 'khoá riêng dạng PEM'

# Tên biến môi trường chứa khoá — xuất hiện trong dump nghĩa là ai đó đã lưu
# giá trị của chúng vào một bảng.
check '(AES_KEY_V[0-9]|JWT_PRIVATE_KEY|MINIO_SECRET_KEY|DB_MIGRATION_PASSWORD)[[:space:]]*[=:]' \
      'tên biến môi trường chứa bí mật'

if [ "$FOUND" -ne 0 ]; then
    echo "" >&2
    echo "  ⛔ Bản sao lưu chứa vật liệu khoá — vi phạm architecture-review.md §6.5." >&2
    echo "     Xử lý: xoá bản dump này, tìm bảng đang chứa khoá, chuyển sang tệp" >&2
    echo "     ngoài CSDL, rồi XOAY KHOÁ (docs/runbook/xoay-khoa.md) vì mọi bản" >&2
    echo "     sao lưu trước đó cũng đã chứa nó." >&2
    exit 1
fi

echo "  ✓ Bản sao lưu không chứa khoá AES/JWT"
