#!/bin/sh
# =============================================================================
# Điền cấu hình giám sát lúc container khởi động — T61.5.
#
# ⛔⛔ Prometheus và Alertmanager KHÔNG thay biến môi trường trong tệp cấu hình.
#    Đo 14/09/2026: `prometheus.yml` khai target `${PROD_APP_TARGET}`, Prometheus
#    v3.1.0 dùng NGUYÊN VĂN chuỗi ấy ⇒ `invalid URL escape "%7B"` ⇒ 0 chỉ số ứng
#    dụng ⇒ `NguonDuLieuImLang` và `SaoLuuQuaHan` nằm `inactive` VĨNH VIỄN (không
#    có chuỗi nào để so), trong khi hai chuông khác kêu thường trực.
#
# Cách dùng (busybox sh — ảnh prom/* có sẵn sed, printf):
#   dien-cau-hinh.sh <mẫu> <đích> <BIẾN>...     thay mọi `__BIẾN__` bằng giá trị
#   bi-mat <BIẾN> <tệp>                          ghi giá trị ra tệp 0600 (…_file)
#
# Bí mật (webhook, token, mật khẩu) ⛔ đi qua sed: chúng vào tệp riêng, cấu hình
# trỏ bằng `*_file`. Giá trị rỗng/thiếu ⇒ DỪNG (luật 3: rỗng khác chưa đặt, và
# cả hai đều không được coi là "đã cấu hình").
# =============================================================================
set -eu

loi() { echo "✗ dien-cau-hinh: $*" >&2; exit 1; }

if [ "${1:-}" = "bi-mat" ]; then
    ten="$2"; tep="$3"
    eval "gt=\${$ten:-}"
    [ -n "$gt" ] || loi "thiếu biến bí mật $ten"
    case "$gt" in \#*) loi "biến $ten bắt đầu bằng '#' — chú thích lọt vào giá trị trong .env" ;; esac
    umask 077
    mkdir -p "$(dirname "$tep")"
    printf '%s' "$gt" > "$tep"
    exit 0
fi

mau="$1"; dich="$2"; shift 2
[ -f "$mau" ] || loi "không có tệp mẫu $mau"
mkdir -p "$(dirname "$dich")"
cp "$mau" "$dich.tmp"
for ten in "$@"; do
    eval "gt=\${$ten:-}"
    [ -n "$gt" ] || loi "thiếu biến $ten (mẫu $mau)"
    # `TÊN=   # chú thích` trong .env cho ra giá trị là CHÍNH câu chú thích (EnvFileCommentTest).
    case "$gt" in \#*) loi "biến $ten bắt đầu bằng '#' — chú thích lọt vào giá trị trong .env" ;; esac
    # Thoát ba ký tự đặc biệt của vế thay thế sed: \ | &
    thoat=$(printf '%s' "$gt" | sed -e 's/[\\|&]/\\&/g')
    sed -i "s|__${ten}__|${thoat}|g" "$dich.tmp"
done
# Còn chỗ trống nào chưa điền ⇒ DỪNG, đừng khởi động với cấu hình nửa vời.
if grep -n '__[A-Z][A-Z0-9_]*__' "$dich.tmp" >&2; then
    loi "còn chỗ trống chưa điền trong $mau (xem dòng trên)"
fi
mv "$dich.tmp" "$dich"
