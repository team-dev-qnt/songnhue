#!/usr/bin/env bash
# =============================================================================
# Gắn tag SHA lên image KHÔNG ĐỔI — T11.78
#
#   Dùng: gan-tag-sha.sh <kho-prefix> <sha> <tên-image>...
#   Ví dụ: gan-tag-sha.sh ghcr.io/team-dev-qnt/songnhue f165f06 app admin-app
#
# VÌ SAO TỒN TẠI, VÀ VÌ SAO TÁCH KHỎI `run:`
#
#   Ngày 4/9/2026 lượt CI `33881305079` trên `dev` đỏ ở job này và kéo theo
#   `Cổng kiểm CI` — cổng bắt buộc DUY NHẤT của `dev`. Thông báo in ra là:
#
#       app chưa có tag `dev` — không suy được bản không đổi là bản nào.
#       Xảy ra ở commit ĐẦU TIÊN của một gói GHCR mới.
#
#   Khẳng định ấy SAI, và log của CHÍNH lượt chạy ấy chứng minh: job đóng gói
#   xanh, đẩy `app:dev@sha256:075d43d9…` lúc 14:07:42; bước gắn tag mới bắt đầu
#   lúc 14:09:58 — tag đã tồn tại trước đó 2 phút 16 giây. Nguyên nhân thật nằm
#   ở dòng ngay TRƯỚC dòng lỗi (CLAUDE.md luật 23):
#
#       failed to configure transport: error pinging v2 registry:
#       Get "https://ghcr.io/v2/": net/http: request canceled while waiting
#       for connection (Client.Timeout exceeded while awaiting headers)
#
#   Chạy lại KHÔNG đổi một dòng mã thì xanh, và bước ấy chạy 10 giây thay vì
#   3 phút 20 hết giờ. Tức là một cú chớp mạng của ghcr.io hạ đỏ cổng bắt buộc.
#
# ⛔⛔ HÌNH DẠNG LỖI — nó đã bị bắt MỘT LẦN RỒI, ở ngay chỗ này
#
#   Chú thích trên chính job này (`ci.yml`) viết rằng vòng quét ngược 50 commit
#   trước kia đẻ ra §10.43 vì **"nó nuốt stderr nên 403 (chưa xác thực) và 404
#   (chưa dựng) cho ra cùng một thông báo"**. Bản thay thế — job này — tái lập
#   ĐÚNG khuyết tật ấy: lượt dò đầu dùng `>/dev/null 2>&1`, và nhánh lỗi khẳng
#   định đúng MỘT nguyên nhân cho một điều kiện có ít nhất BA
#   (tag vắng · chưa xác thực · registry không với tới được).
#
#   CLAUDE.md luật 9: *một khẳng định không phân biệt được hai trạng thái thì
#   không khẳng định gì*. Ở đây nó còn tệ hơn — nó khẳng định NHẦM, và luật 33
#   nhắc rằng lời khuyên chữa lỗi in ra từ một bộ canh cũng là mã: câu
#   *"Chạy lại CI sau khi job đóng gói đã đẩy được ít nhất một lần"* dẫn người
#   đọc đi sai hướng, vì job đóng gói đã chạy và đã xanh trong CHÍNH lượt ấy.
#
# NGUYÊN TẮC CỦA BẢN VÁ
#
#   ⭐ Chỉ được kết luận "tag KHÔNG có" khi registry ĐÃ TRẢ LỜI và nói vậy.
#     Mọi thứ khác — hết giờ, không nối được, 5xx, TLS, 401/403 — là "CHƯA
#     BIẾT", và chưa biết thì thử lại, rồi đỏ với thông báo NÓI ĐÚNG lý do.
#     Không bao giờ suy "không đọc được" thành "không tồn tại".
#
#   ⭐ Tách stderr ra và IN NÓ RA. Nuốt stderr chính là thứ đã tạo ra §10.43 và
#     tái phát ở đây.
#
# ⛔ GIỚI HẠN — ghi vào chính bộ canh (luật 28)
#
#   Script này phân loại lỗi bằng **văn bản stderr của `docker`**, tức nó phụ
#   thuộc vào cách diễn đạt của một công cụ bên ngoài. Nếu Docker đổi câu chữ,
#   nhánh "vắng thật" sẽ rơi xuống nhánh "chưa biết" — nghĩa là script **thử
#   lại rồi đỏ với nguyên văn stderr**, chứ không im lặng gắn nhầm tag. Hướng
#   hỏng đó là hướng an toàn, và đó là lý do chọn cách phân loại này thay vì
#   mặc định ngược lại. `GanTagShaTest` canh cả hai nhánh bằng `docker` giả.
# =============================================================================
set -euo pipefail

SO_LAN_THU="${GAN_TAG_SO_LAN_THU:-4}"
CHO_GIAY="${GAN_TAG_CHO_GIAY:-5}"

if [ "$#" -lt 3 ]; then
    echo "::error::Dùng: gan-tag-sha.sh <kho-prefix> <sha> <tên-image>..." >&2
    exit 2
fi

KHO_PREFIX="$1"
SHA="$2"
shift 2

if ! command -v docker >/dev/null 2>&1; then
    # ⛔ Thiếu công cụ thì ĐỎ, không `exit 0`. Đúng bẫy `verify-no-keys.sh` đã mắc:
    #    nhánh thiếu `pg_restore` thoát 0 và suốt bốn ngày mọi lượt triển khai in
    #    "BỎ QUA việc kiểm khoá" mà không ai đọc (T11.41).
    echo "::error::Không có \`docker\` trên PATH — không thể gắn tag, và không được coi là đã xong." >&2
    exit 1
fi

# Những câu stderr có nghĩa là "registry ĐÃ trả lời, và tag thật sự không có".
# Chỉ khi khớp một trong số này ta mới được kết luận là VẮNG.
# Cố ý KHÔNG bao gồm `unauthorized`/`denied`: 401/403 nghĩa là *chưa xác thực*,
# không phải *không tồn tại* — gộp hai thứ ấy chính là §10.43.
CO_NGHIA_LA_VANG='manifest unknown|MANIFEST_UNKNOWN|no such manifest|not found|manifest tagged by .* is not found|reference does not exist'

# Trả 0 = có · 1 = VẮNG (registry đã trả lời) · 2 = CHƯA BIẾT (không hỏi được)
# Nguyên văn stderr đặt vào biến toàn cục `LY_DO` để nhánh gọi in ra được.
LY_DO=""
hoi_tag() {
    local tham_chieu="$1"
    local err
    if err="$(docker manifest inspect "$tham_chieu" 2>&1 >/dev/null)"; then
        LY_DO=""
        return 0
    fi
    LY_DO="$err"
    if printf '%s' "$err" | grep -qEi "$CO_NGHIA_LA_VANG"; then
        return 1
    fi
    return 2
}

# Hỏi có thử lại: chỉ thử lại khi CHƯA BIẾT. "Vắng" là một câu trả lời dứt khoát,
# thử lại một câu trả lời dứt khoát chỉ làm chậm lượt chạy.
hoi_tag_co_thu_lai() {
    local tham_chieu="$1"
    local lan=1
    local kq
    while :; do
        # ⛔ KHÔNG dùng cặp `set +e` / `set -e` quanh lời gọi: `set -e` là **toàn cục**,
        #    không theo phạm vi hàm. Một `set -e` bên trong hàm bật lại errexit và xoá
        #    mất `set +e` của nơi gọi, nên `return 1` giết cả script trong im lặng —
        #    bản đầu của chính tệp này mắc đúng lỗi ấy và thoát 1 mà không in một dòng
        #    nào. Dạng `cmd || kq=$?` an toàn dưới `set -e` vì nó là một danh sách `||`.
        kq=0
        hoi_tag "$tham_chieu" || kq=$?
        if [ "$kq" -ne 2 ] || [ "$lan" -ge "$SO_LAN_THU" ]; then
            return "$kq"
        fi
        echo "  ⏳ $tham_chieu — chưa hỏi được registry (lần $lan/$SO_LAN_THU), chờ ${CHO_GIAY}s rồi thử lại."
        echo "     stderr: $LY_DO"
        sleep "$CHO_GIAY"
        lan=$((lan + 1))
    done
}

for name in "$@"; do
    kho="$KHO_PREFIX/$name"

    kq_sha=0
    hoi_tag_co_thu_lai "$kho:$SHA" || kq_sha=$?

    if [ "$kq_sha" -eq 0 ]; then
        echo "✓ $name — lượt này đã đóng gói, không cần gắn thêm"
        continue
    fi
    if [ "$kq_sha" -eq 2 ]; then
        echo "::error::$name — KHÔNG HỎI ĐƯỢC registry về \`$kho:$SHA\` sau $SO_LAN_THU lần thử." >&2
        echo "::error::⛔ Đây KHÔNG phải bằng chứng tag vắng — chỉ là ta không hỏi được. Nguyên văn:" >&2
        echo "$LY_DO" >&2
        exit 1
    fi

    # Tới đây: registry đã trả lời và nói `:$SHA` chưa có. Đi tìm `:dev`.
    #
    # `:dev` là tag DI ĐỘNG, trỏ vào bản dựng gần nhất của image này. Ở đây dùng
    # nó làm NGUỒN để giải ra digest, rồi ghim digest ấy dưới một tên BẤT BIẾN.
    # Vẫn giữ nguyên luật "không deploy theo tag di động".
    kq_dev=0
    hoi_tag_co_thu_lai "$kho:dev" || kq_dev=$?

    if [ "$kq_dev" -eq 2 ]; then
        # ⭐ NHÁNH SINH RA BẢN VÁ NÀY. Trước 4/9 nhánh này không tồn tại và mọi
        #   lỗi đều rơi vào nhánh "gói mới" bên dưới.
        echo "::error::$name — KHÔNG HỎI ĐƯỢC registry về \`$kho:dev\` sau $SO_LAN_THU lần thử." >&2
        echo "::error::⛔ KHÔNG kết luận là tag vắng. Nhiều khả năng ghcr.io đang chớp tắt;" >&2
        echo "::error::   chạy lại lượt CI này (\`gh run rerun <id> --failed\`) là đủ. Nguyên văn:" >&2
        echo "$LY_DO" >&2
        exit 1
    fi

    if [ "$kq_dev" -eq 1 ]; then
        echo "::error::$name chưa có tag \`dev\` — không suy được bản không đổi là bản nào." >&2
        echo "::error::Registry ĐÃ trả lời và nói không có, nên đây thật sự là commit ĐẦU TIÊN" >&2
        echo "::error::của một gói GHCR mới. Chạy lại CI sau khi job đóng gói tương ứng đã đẩy" >&2
        echo "::error::được ít nhất một lần. Nguyên văn:" >&2
        echo "$LY_DO" >&2
        exit 1
    fi

    docker buildx imagetools create --tag "$kho:$SHA" "$kho:dev"
    echo "ℹ $name không đổi — gắn thêm tag $SHA lên đúng digest cũ"
done

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    echo "Cả ba image đều có tag \`$SHA\` — luồng đề bạt chỉ cần một SHA." >>"$GITHUB_STEP_SUMMARY"
fi
