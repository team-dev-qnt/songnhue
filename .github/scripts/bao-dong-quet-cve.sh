#!/usr/bin/env bash
# =============================================================================
# Chuông báo lượt quét bảo mật đỏ — T11.58
#
#   Dùng: bao-dong-quet-cve.sh <do|xanh> <url-lượt-chạy>
#
# VÌ SAO TỒN TẠI
#
#   Lượt quét theo lịch 2/9/2026 07:06 UTC đỏ với 4 mã CVSS ≥ 7. Tới 3/9 vẫn
#   không ai biết. Đó là lần thứ BA trong năm ngày (29/8 tomcat · 1/9
#   spring-59313 · 2/9 bốn mã spring), và nhịp đỏ 6 lượt `schedule` gần nhất
#   trên `dev` là 4/6.
#
#   Đo `security-scan.yml` xem nó phát ra gì khi đỏ — kết quả: KHÔNG GÌ CẢ.
#
#       if: failure()                                        0
#       GITHUB_STEP_SUMMARY khi đỏ vì CVE                    0 byte
#       gh issue create / github-script / webhook / smtp     0
#       permissions:                                contents: read
#
#   Dòng ghi `$GITHUB_STEP_SUMMARY` duy nhất nằm trong nhánh *thiếu
#   `NVD_API_KEY`* — nhánh không bao giờ chạy khi khoá có mặt. Thứ duy nhất
#   còn lại là artifact báo cáo, nằm trong tab Actions, hết hạn sau 14 ngày.
#
#   Và đường báo mặc định của GitHub TRỎ SAI NGƯỜI: thư của scheduled workflow
#   gửi cho *người tạo workflow*, không phải người commit cuối. Đo 5 lượt:
#   `actor.login` = `quannt18` đứng yên cả 5, trong khi tác giả HEAD là
#   `Toclac18` ở 4/5. Người viết cổng quét không phải người nhận thư báo đỏ.
#
# VÌ SAO LÀ ISSUE, KHÔNG PHẢI THƯ HAY WEBHOOK
#
#   Issue **có trạng thái**. Một lượt chạy đỏ trôi qua; một issue mở thì nằm
#   đó tới khi có người đóng. Nó cũng **quan sát được bằng API**, tức kiểm
#   chứng được — khác hẳn hộp thư cá nhân, thứ không REST API nào nhìn thấy.
#   Và nó không cần secret ngoài, không thêm một dịch vụ có thể 404 trong im
#   lặng mà không ai canh cái canh.
#
# VÌ SAO TÁCH KHỎI `run:` CỦA WORKFLOW
#
#   Để nhánh quyết định kiểm được bằng **dữ liệu giả**. Bài học của
#   `phan-loai-check-chang-truoc.sh`: thứ nằm trong `run:` thì muốn tái hiện
#   phải thắng một cuộc đua thật.
#
# ⛔ GIỚI HẠN — ghi vào chính bộ canh (luật 28)
#
#   Script này (và `CanhBaoQuetCveTest`) chứng minh **dây đã nối**. Nó KHÔNG
#   chứng minh **GitHub đã giao**. Nửa sau chỉ đo được bằng một lượt chạy thật.
#
#   Và nó KHÔNG phủ trường hợp nguy hiểm nhất: **lượt quét không chạy**.
#   `if: failure()` chỉ phát khi có lượt để mà đỏ. Luật 31 — thứ nguy hiểm là
#   sự VẮNG MẶT, không phải màu đỏ. Vế ấy cần một workflow canh riêng, hỏi API
#   lấy lượt mới nhất và đỏ nếu nó cũ hơn N giờ; đang là một dòng nợ riêng.
# =============================================================================
set -euo pipefail

# ⭐ Nhãn nhận diện issue — ĐỊNH NGHĨA ĐÚNG MỘT LẦN.
#
#   Dùng dấu hiệu trong TIÊU ĐỀ chứ không dùng label: label phải tạo tay ở
#   GitHub trước, tức thêm đúng cái loại việc mà cả bản vá này đang muốn bỏ đi
#   ("lệnh nằm sẵn trong tài liệu mà không ai chạy").
#
#   Nhánh MỞ và nhánh ĐÓNG phải tìm cùng một chuỗi. Để hai hằng số rời nhau là
#   dựng lại luật 14 — hai nơi phải nhớ mà không ai nhớ hộ. `CanhBaoQuetCveTest`
#   canh điều đó.
# Nhánh DUY NHẤT được phép đóng issue mốc. Định nghĩa một lần (luật 14).
NHANH_MOC='dev'

NHAN='[quét-cve]'
TIEU_DE="$NHAN Lượt quét phụ thuộc ĐỎ — có CVE cần xử lý"

trang_thai="${1:-}"
url_lan_chay="${2:-}"

if [ "$trang_thai" != "do" ] && [ "$trang_thai" != "xanh" ]; then
    echo "::error::Tham số 1 phải là 'do' hoặc 'xanh', nhận được: '${trang_thai}'" >&2
    echo "Dùng: $(basename "$0") <do|xanh> <url-lượt-chạy>" >&2
    exit 1
fi

# ⛔ Không có `gh` thì ĐỎ, không im lặng đi tiếp.
#
#   Một cái chuông hỏng phải kêu lên rằng nó hỏng. Đây đúng cái bẫy
#   `verify-no-keys.sh` đã mắc: thiếu công cụ thì `exit 0`, và suốt bốn ngày
#   mọi lượt triển khai in "BỎ QUA việc kiểm khoá" mà không ai đọc.
if ! command -v gh >/dev/null 2>&1; then
    echo "::error::Không tìm thấy 'gh' trên PATH — chuông báo CVE KHÔNG chạy được." >&2
    echo "Đây là lỗi, không phải trường hợp bỏ qua được: lượt quét có thể đang đỏ" >&2
    echo "mà không ai được báo." >&2
    exit 1
fi

# Tìm issue mốc đang mở. `--json number` rồi tự bóc bằng grep thay vì `--jq`:
# ít phụ thuộc hơn, và quan trọng hơn là `gh` giả trong bài kiểm chỉ cần in
# JSON, không phải cài lại một bộ máy jq.
danh_sach=$(gh issue list --search "in:title $NHAN" --state open --json number --limit 1 2>/dev/null || true)
so_issue=$(printf '%s' "$danh_sach" | grep -oE '"number"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+' | head -1 || true)

echo "trạng thái = ${trang_thai} · issue mốc đang mở = ${so_issue:-(không có)}"


# ⚠ Thân issue dựng bằng heredoc CÓ NHÁY (<<'HET'), không bằng chuỗi trong nháy
#   kép. Lý do đo được: bản đầu viết nó trong nháy kép và `dependency-check-report`
#   nằm giữa hai dấu huyền → bash chạy nó như một lệnh, script thoát **127** ở cả
#   hai nhánh "đỏ". Cùng lớp lỗi mà `DeployRemoteStdinTest` canh cho heredoc của
#   khối triển khai. Heredoc có nháy tắt MỌI phép khai triển, nên URL phải đi vào
#   bằng một thẻ thay chỗ thay vì `${...}`.
if [ "$trang_thai" = "do" ]; then
    than=$(cat <<'HET'
Lượt quét phụ thuộc theo lịch **ĐỎ**.

Lượt chạy: @@URL@@

Báo cáo CVE nằm ở artifact `dependency-check-report` của lượt chạy ấy
(**hết hạn sau 14 ngày** — tải về trước khi mất).

Việc phải làm, theo `conventions.md` §4.5:

1. **Nâng cấp trước, suppress sau.** Tra bản vá bằng `maven-metadata.xml`,
   KHÔNG bằng API tìm kiếm (luật 21).
2. Nâng không được thì thẩm định *"không áp dụng"* — và lý do phải nói ĐƯỢC hay
   KHÔNG áp dụng, không phải *"chưa có bản vá"*.
3. ⛔ Lý do *"dòng này hết hỗ trợ"* **không hợp lệ** — nó biến một ngày hết hạn
   đã tới thành một ngày hết hạn tự đặt. Việc phải làm khi ấy là **đổi dòng**.

Issue này **tự đóng** khi lượt quét xanh trở lại.
HET
    )
    than=${than//@@URL@@/${url_lan_chay:-(không rõ)}}

    if [ -n "${so_issue:-}" ]; then
        gh issue comment "$so_issue" --body "$than"
        echo "→ đã bình luận vào issue #${so_issue} (mỗi lượt đỏ một dòng — tiếng ồn tăng dần là CỐ Ý)"
    else
        gh issue create --title "$TIEU_DE" --body "$than"
        echo "→ đã mở issue mốc mới"
    fi
else
    # ⛔⛔ T11.81 — CHỈ `dev` mới được đóng issue mốc.
    #
    #    Đo 05/09: script không đọc một biến nhánh nào (`grep -cE 'GITHUB_REF|ref_name|branch'`
    #    = 0), nên nhánh XANH đóng issue bất kể lượt quét chạy ở đâu. Và lượt nhánh phụ CÓ
    #    với tới chuông thật — bình luận thứ 5 của issue #84 đến từ lượt `33887563690` chạy
    #    trên `fix/t11-76-jackson-bom` bằng `workflow_dispatch`.
    #
    #    Hệ quả đúng vào lúc nguy hiểm nhất: khi làm T11.69 (Boot 4.1.1) người ta sẽ bấm
    #    `workflow_dispatch` trên nhánh vá để xem đã sạch chưa. Lượt ấy XANH ⇒ script đóng
    #    issue #84 trong khi `dev` **vẫn đỏ nguyên**. Chuông tự tắt mình, và lượt quét theo
    #    lịch hôm sau mở một issue MỚI — số issue thôi khớp số lượt đỏ, tức phá đúng vòng
    #    khứ hồi mà T11.58 dựng ra để tự nghiệm thu (luật 9: hai trạng thái khác nhau —
    #    *`dev` đã sạch* và *một nhánh phụ đã sạch* — cho ra cùng một hành động).
    #
    #    Nhánh ĐỎ cố ý KHÔNG kiểm nhánh: một nhánh phụ phát hiện thêm mã thì vẫn đáng nói.
    #    Bất đối xứng này là có chủ đích — mở thì rộng tay, đóng thì chặt tay.
    if [ "${NHANH:-}" != "$NHANH_MOC" ]; then
        echo "→ lượt XANH này chạy trên nhánh '${NHANH:-(không rõ)}', không phải '$NHANH_MOC'."
        echo "  ⛔ KHÔNG đóng issue mốc: một nhánh phụ sạch không chứng minh '$NHANH_MOC' đã sạch."
        exit 0
    fi
    if [ -n "${so_issue:-}" ]; then
        gh issue comment "$so_issue" --body "Lượt quét phụ thuộc đã **XANH** trở lại trên \`$NHANH_MOC\`: ${url_lan_chay:-(không rõ)}

Đóng issue mốc. Lượt đỏ kế tiếp sẽ mở lại một issue mới."
        # Bình luận TRƯỚC rồi mới đóng — đóng trước thì dòng giải thích rơi vào
        # một issue đã đóng và người đọc thấy nó biến mất khỏi danh sách trước
        # khi kịp biết vì sao.
        gh issue close "$so_issue"
        echo "→ đã đóng issue #${so_issue}"
    else
        echo "→ không có issue mốc nào đang mở, không phải làm gì"
    fi
fi
