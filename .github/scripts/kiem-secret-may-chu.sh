#!/usr/bin/env bash
# =============================================================================
# CỔNG SECRET — quyết định lượt triển khai được đi tiếp, bỏ qua, hay dừng đỏ.
#
# Dùng (chạy trên runner, không phải trên máy chủ):
#   MOI_TRUONG=production HOST=… USER=… SSH_KEY=… BASE_URL=… SSH_KNOWN_HOSTS=… \
#     kiem-secret-may-chu.sh
#
# ⭐ VÌ SAO CÓ TỆP RIÊNG THAY VÌ VÀI DÒNG TRONG `deploy.yml`
#
#   Bản trước nằm inline và chỉ hỏi MỘT biến (`HOST`), rồi:
#       thiếu → `::warning::` + `ready=false` → mọi bước sau `if: ready == true`
#               tự bỏ qua → **lượt chạy XANH TRỌN VẸN**.
#   Đo 26/8: environment `production` KHÔNG có secret nào. Nghĩa là bấm "CD
#   Production" hôm nay sẽ cho ra một lượt chạy xanh, một dòng tóm tắt, và
#   KHÔNG MỘT BYTE nào chạm máy chủ — đúng hình dạng "cổng kiểm tồn tại trong
#   mã nhưng chưa có hiệu lực ở nơi nó phải chặn".
#
#   Tách ra tệp để `SecretGateTest` CHẠY THẬT nó với từng tổ hợp biến. Một khối
#   `run:` inline chỉ kiểm được bằng cách đọc chữ, mà đọc chữ thì không phân biệt
#   được hai trạng thái (luật 9).
#
# BA TRẠNG THÁI, và chúng phải phân biệt được nhau:
#
#   đủ cả NĂM      → ready=true,  thoát 0
#   thiếu MỘT SỐ   → thoát 1      ở MỌI môi trường. Cấu hình dở dang không phải
#                                 "chưa dựng" — nó là cấu hình hỏng, và nó sẽ
#                                 hỏng muộn hơn ở một bước không nói được vì sao.
#   thiếu CẢ NĂM   → production: thoát 1. staging: ready=false + cảnh báo.
#
# ⚠ `SSH_KNOWN_HOSTS` vào danh sách ngày 29/8 (§10.68-C). Trước đó bước "Mở đường
#   SSH" tự dò khoá bằng `ssh-keyscan`, và chính lượt dò ấy làm fail2ban của máy
#   chủ cấm IP runner. Nay khoá phải được GHIM sẵn, nên thiếu nó thì lượt deploy
#   không thể nối — đúng loại thiếu mà cổng này sinh ra để chặn SỚM.
#
#   Vì sao staging được nhẹ tay: `deploy-staging.yml` chạy tự động sau mỗi lượt
#   merge, nên một môi trường chưa dựng sẽ nhuộm đỏ cả dòng CI của mọi người.
#   Production thì chỉ chạy khi CÓ NGƯỜI BẤM — và người ấy đang chờ biết là đã
#   deploy được hay chưa. Với họ, im lặng bỏ qua là câu trả lời sai nhất.
# =============================================================================
set -euo pipefail

MOI_TRUONG="${MOI_TRUONG:?Thiếu MOI_TRUONG}"
GITHUB_OUTPUT="${GITHUB_OUTPUT:-/dev/null}"

thieu=()
for ten in HOST USER SSH_KEY BASE_URL SSH_KNOWN_HOSTS; do
    # ⚠ Kiểm giá trị ĐÃ GIẢI: một secret không tồn tại và một secret đặt bằng
    #   chuỗi rỗng đến đây giống hệt nhau, và cả hai đều không dùng được (luật 3).
    gia_tri="$(eval "printf '%s' \"\${$ten:-}\"")"
    [ -n "$gia_tri" ] || thieu+=("$ten")
done

if [ ${#thieu[@]} -eq 0 ]; then
    echo "ready=true" >> "$GITHUB_OUTPUT"
    echo "✓ Đủ năm secret máy chủ cho $MOI_TRUONG"
    exit 0
fi

if [ ${#thieu[@]} -lt 5 ]; then
    echo "::error::Cấu hình máy chủ $MOI_TRUONG DỞ DANG — thiếu: ${thieu[*]}"
    echo "Có secret nhưng không đủ bộ thì lượt triển khai sẽ hỏng ở một bước xa hơn"
    echo "với thông báo không nhắc gì tới secret. Đặt đủ năm, hoặc xoá hết."
    echo ""
    echo "SSH_KNOWN_HOSTS là khoá CÔNG KHAI của máy chủ, một dòng:"
    echo "    <host> ssh-ed25519 AAAAC3Nza…"
    echo "Lấy bằng: cat /etc/ssh/ssh_host_ed25519_key.pub  (TRÊN máy chủ)"
    exit 1
fi

if [ "$MOI_TRUONG" = "production" ]; then
    echo "::error::Environment \`production\` chưa có secret nào (HOST/USER/SSH_KEY/BASE_URL/SSH_KNOWN_HOSTS)."
    echo ""
    echo "Lượt chạy này DỪNG ĐỎ, cố ý. Bỏ qua trong im lặng sẽ cho ra một lượt CD"
    echo "Production xanh trọn vẹn mà không một byte nào chạm máy chủ — và người bấm"
    echo "nút sẽ tin là đã deploy xong."
    echo ""
    echo "Đặt ở: Settings → Environments → production → Environment secrets"
    echo "  PROD_HOST · PROD_USER · PROD_SSH_KEY · PROD_BASE_URL · PROD_SSH_KNOWN_HOSTS"
    exit 1
fi

echo "::warning::Chưa cấu hình secret máy chủ cho $MOI_TRUONG — bỏ qua bước triển khai."
echo "ready=false" >> "$GITHUB_OUTPUT"
