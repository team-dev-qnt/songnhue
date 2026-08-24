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
# ⚠⚠ Và chính nó đã âm thầm sai — phát hiện 17/8. Mẫu tìm khoá PEM bắt đầu bằng
#   `-----BEGIN`, nên `grep -qiE "$pattern"` đọc nó thành THAM SỐ DÒNG LỆNH:
#
#       grep: unrecognized option `-----BEGIN [A-Z ]*PRIVATE KEY-----'
#
#   grep chết, lời gọi nằm trong `if` nên lỗi bị nuốt, FOUND giữ nguyên 0, và
#   script in "✓ Bản sao lưu không chứa khoá AES/JWT". Nghĩa là suốt từ WS-7,
#   phép kiểm bảo mật này báo ĐẠT mà chưa một lần soi tìm khoá PEM. Sửa bằng
#   `-e` (mẫu là mẫu, không phải tuỳ chọn) và bằng phép TỰ KIỂM ở dưới.
#
#   Dùng:  deploy/backup/verify-no-keys.sh <đường-dẫn-bản-dump>
# =============================================================================
set -euo pipefail

DUMP="${1:?Dùng: verify-no-keys.sh <đường-dẫn-bản-dump>}"

# Khoá riêng dạng PEM — JWT_PRIVATE_KEY_PATH trỏ tới tệp, nội dung KHÔNG được
# lọt vào CSDL dưới bất kỳ hình thức nào.
PATTERN_PEM='-----BEGIN [A-Z ]*PRIVATE KEY-----'
# Tên biến môi trường chứa khoá — xuất hiện trong dump nghĩa là ai đó đã lưu
# giá trị của chúng vào một bảng.
PATTERN_ENV='(AES_KEY_V[0-9]|JWT_PRIVATE_KEY|MINIO_SECRET_KEY|DB_MIGRATION_PASSWORD)[[:space:]]*[=:]'

# Soi dòng dữ liệu ở stdin. Trả 1 khi TÌM THẤY (tức là có vi phạm).
scan_stream() {
    local pattern="$1" what="$2"
    if grep -qiE -e "$pattern"; then
        echo "  ✗ PHÁT HIỆN $what trong bản sao lưu" >&2
        return 1
    fi
    return 0
}

# -----------------------------------------------------------------------------
# Tự kiểm — chạy MỌI lần, tốn vài mili-giây.
#
# Bài học của chính tệp này: một phép canh gác hỏng thì im lặng báo đạt. Nên
# trước khi tin kết quả trên bản dump thật, bắt nó chứng minh nó còn bắt được
# vi phạm trên dữ liệu giả, và còn im lặng trên dữ liệu sạch.
# -----------------------------------------------------------------------------
self_test() {
    local sample_key='-----BEGIN RSA PRIVATE KEY-----'
    local sample_env='AES_KEY_V1=dGhpcy1sYS1raG9hLWdpYQ=='
    local benign='INSERT INTO settings VALUES (1, ''backup.retention-days'', ''30'');'

    if printf '%s\n' "$sample_key" | scan_stream "$PATTERN_PEM" 'tự kiểm' 2>/dev/null; then
        echo "  ⛔ TỰ KIỂM HỎNG: không nhận ra khoá PEM giả — mọi kết quả bên dưới vô nghĩa." >&2
        exit 2
    fi
    if printf '%s\n' "$sample_env" | scan_stream "$PATTERN_ENV" 'tự kiểm' 2>/dev/null; then
        echo "  ⛔ TỰ KIỂM HỎNG: không nhận ra tên biến bí mật giả." >&2
        exit 2
    fi
    if ! printf '%s\n' "$benign" | scan_stream "$PATTERN_PEM" 'tự kiểm' 2>/dev/null; then
        echo "  ⛔ TỰ KIỂM HỎNG: báo động nhầm trên dữ liệu sạch." >&2
        exit 2
    fi
}

self_test

if ! command -v pg_restore >/dev/null 2>&1; then
    echo "  ⚠ Không có pg_restore — BỎ QUA việc kiểm khoá trong bản dump." >&2
    echo "    Đây là bỏ qua một kiểm chứng bảo mật, không phải 'không sao'." >&2
    exit 0
fi

# Bản dump không đọc được mà vẫn in ✓ thì lại đúng cái bẫy cũ: không tìm thấy gì
# vì không đọc được gì. Chặn ở đây, trước khi soi.
if ! pg_restore --list "$DUMP" >/dev/null 2>&1; then
    echo "  ✗ Không đọc được bản dump $DUMP — không kết luận được gì về khoá." >&2
    exit 1
fi

# Giải nén ra dạng văn bản rồi soi. Không ghi ra tệp tạm: nội dung CSDL đầy đủ
# nằm trên đĩa dưới dạng đọc được là đúng thứ ta đang cố tránh.
FOUND=0
pg_restore --file=- "$DUMP" 2>/dev/null | scan_stream "$PATTERN_PEM" 'khoá riêng dạng PEM' || FOUND=1
pg_restore --file=- "$DUMP" 2>/dev/null | scan_stream "$PATTERN_ENV" 'tên biến môi trường chứa bí mật' || FOUND=1

if [ "$FOUND" -ne 0 ]; then
    echo "" >&2
    echo "  ⛔ Bản sao lưu chứa vật liệu khoá — vi phạm architecture-review.md §6.5." >&2
    echo "     Xử lý: xoá bản dump này, tìm bảng đang chứa khoá, chuyển sang tệp" >&2
    echo "     ngoài CSDL, rồi XOAY KHOÁ (docs/runbook/xoay-khoa.md) vì mọi bản" >&2
    echo "     sao lưu trước đó cũng đã chứa nó." >&2
    exit 1
fi

echo "  ✓ Bản sao lưu không chứa khoá AES/JWT (đã qua tự kiểm)"
