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
# ⚠⚠ Và nó âm thầm sai LẦN THỨ HAI — phát hiện 26/8, vá 3/9 (T11.41). Nhánh
#   "không có pg_restore trên host" `exit 0`, mà VPS staging không cài
#   postgresql-client, nên từ 26/8 mọi lượt triển khai đều in *"BỎ QUA việc kiểm
#   khoá"* rồi đi tiếp — phép kiểm chạy suốt mà chưa soi một byte nào.
#   Nay: hỏi container `postgres` (luôn có pg_restore, đúng phiên bản), và không
#   hỏi được thì **thoát 1**. Xem khối chọn nguồn ở dưới.
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

# -----------------------------------------------------------------------------
# ⚠⚠ CHỌN CHỖ CHẠY pg_restore — T11.41
#
# Bản trước: không có `pg_restore` trên host thì in "BỎ QUA việc kiểm khoá" rồi
# `exit 0`. Trên VPS staging KHÔNG CÓ postgresql-client, nên nhánh ấy chạy ở
# **mọi lượt triển khai** kể từ 26/8: phép kiểm bảo mật duy nhất canh bản dump
# chưa từng soi một byte nào, và nó thoát 0 nên `pre-deploy-dump.sh` in ✓ đi tiếp.
#
# ⛔ `exit 0` là chỗ sai, không phải chỗ thiếu công cụ. Cùng hình dạng với nhánh
#    `ImportError → sys.exit(0)` của bộ đọc tracking (T11.49): một cơ chế canh gác
#    KHÔNG CHẠY ĐƯỢC phải nói ra bằng mã thoát khác 0. "Không kiểm được" và "kiểm
#    rồi, sạch" là hai kết luận khác nhau và phải trông khác nhau (luật 9).
#
# Container `postgres` LUÔN có `pg_restore`, đúng phiên bản máy chủ, và thư mục
# sao lưu được gắn vào nó ở **cùng một đường dẫn** với host
# (`/var/lib/songnhue/backup:/var/lib/songnhue/backup`, compose.prod.yml) — nên
# đường dẫn truyền vào dùng nguyên si được, không phải dịch.
# -----------------------------------------------------------------------------
NGUON_PG=""
if command -v pg_restore >/dev/null 2>&1; then
    NGUON_PG="host"
    pg_restore_run() { pg_restore "$@"; }
elif command -v docker >/dev/null 2>&1; then
    # shellcheck source=../lib/docker-svc.sh
    . "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/../lib/docker-svc.sh"
    CT_PG="$(container_cua postgres 2>/dev/null)" || CT_PG=""
    if [ -n "$CT_PG" ]; then
        # Bản dump phải NHÌN THẤY ĐƯỢC từ trong container. Không kiểm bước này thì
        # `pg_restore` trong đó báo "no such file" và ta lại rơi vào đúng cái bẫy
        # "không tìm thấy gì vì không đọc được gì".
        if docker exec "$CT_PG" test -f "$DUMP" 2>/dev/null; then
            NGUON_PG="container ${CT_PG:0:12}"
            pg_restore_run() { docker exec "$CT_PG" pg_restore "$@"; }
        else
            echo "  ✗ Container postgres không thấy $DUMP — thư mục sao lưu chưa được gắn vào nó," >&2
            echo "    hoặc bản dump nằm ngoài BACKUP_DIR. Không kết luận được gì về khoá." >&2
            exit 1
        fi
    fi
fi

if [ -z "$NGUON_PG" ]; then
    echo "  ✗ KHÔNG CHẠY ĐƯỢC phép kiểm khoá: không có pg_restore trên host, và cũng không" >&2
    echo "    hỏi được container postgres (thiếu docker, hoặc container không chạy)." >&2
    echo "    Đây KHÔNG phải 'không sao' — bản dump sắp rời máy này mà chưa ai soi nó." >&2
    echo "    Chữa: dựng container postgres rồi chạy lại, hoặc cài postgresql-client." >&2
    exit 1
fi

echo "  · Soi bằng pg_restore của: $NGUON_PG"

# Bản dump không đọc được mà vẫn in ✓ thì lại đúng cái bẫy cũ: không tìm thấy gì
# vì không đọc được gì. Chặn ở đây, trước khi soi.
if ! pg_restore_run --list "$DUMP" >/dev/null 2>&1; then
    echo "  ✗ Không đọc được bản dump $DUMP — không kết luận được gì về khoá." >&2
    exit 1
fi

# Giải nén ra dạng văn bản rồi soi. Không ghi ra tệp tạm: nội dung CSDL đầy đủ
# nằm trên đĩa dưới dạng đọc được là đúng thứ ta đang cố tránh.
FOUND=0
pg_restore_run --file=- "$DUMP" 2>/dev/null | scan_stream "$PATTERN_PEM" 'khoá riêng dạng PEM' || FOUND=1
pg_restore_run --file=- "$DUMP" 2>/dev/null | scan_stream "$PATTERN_ENV" 'tên biến môi trường chứa bí mật' || FOUND=1

if [ "$FOUND" -ne 0 ]; then
    echo "" >&2
    echo "  ⛔ Bản sao lưu chứa vật liệu khoá — vi phạm architecture-review.md §6.5." >&2
    echo "     Xử lý: xoá bản dump này, tìm bảng đang chứa khoá, chuyển sang tệp" >&2
    echo "     ngoài CSDL, rồi XOAY KHOÁ (docs/runbook/xoay-khoa.md) vì mọi bản" >&2
    echo "     sao lưu trước đó cũng đã chứa nó." >&2
    exit 1
fi

echo "  ✓ Bản sao lưu không chứa khoá AES/JWT (đã qua tự kiểm)"
