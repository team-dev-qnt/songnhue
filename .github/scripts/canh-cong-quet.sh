#!/usr/bin/env bash
#
# T11.67 — canh chuyện **lượt quét bảo mật KHÔNG CHẠY**.
#
# =============================================================================
# ⛔⛔ VÌ SAO CẦN MỘT CÁI CHUÔNG THỨ HAI
# =============================================================================
#
# `bao-dong-quet-cve.sh` (T11.66/T11.84) là chuông cho lượt quét **ĐỎ**. Nó treo
# vào `needs: [owasp, npm]` với `if: always()`, nên nó chỉ kêu được khi **có một
# lượt để mà đỏ**. Tắt Actions, xoá nhầm workflow, hỏng cú pháp YAML, GitHub bỏ
# lịch cron — mọi trường hợp ấy đều cho ra **im lặng tuyệt đối**, và im lặng đọc
# y hệt "mọi thứ đều ổn".
#
# Đúng CLAUDE.md luật 31: *thứ nguy hiểm là **sự vắng mặt**, không phải màu đỏ.*
# Kho này đã trả giá đúng hình dạng ấy hai lần — `skipped` được tính ĐẠT (luật
# 24), và `Promotion guard` treo ở "Expected" mà không một dòng đỏ nào để đọc
# (§10.72).
#
# =============================================================================
# ⛔ RANH GIỚI VỚI CHUÔNG KIA — cố ý KHÔNG chồng lấn
# =============================================================================
#
# Script này **im lặng** khi lượt mới nhất `failure`. Đó là việc của T11.66, và
# nó kêu kèm **bằng chứng** (vân tay tập CVE, nhãn LEO THANG/GIẢM/ĐỔI). Kêu lần
# hai ở đây là dựng lại đúng lỗi T11.84 đã vá: **9 bình luận giống hệt nhau**,
# một cái chuông một bit.
#
# Nó CHỈ kêu ở ba trạng thái mà chuông kia **về nguyên tắc** không thể thấy:
#
#   1. `khong-co-luot`  — 0 lượt chạy nào. Workflow bị xoá/đổi tên/tắt.
#   2. `qua-han`        — lượt mới nhất cũ hơn NGUONG_GIO. Lịch cron đã chết.
#   3. `ket-cuc-la`     — kết cục ∉ {success, failure}: `cancelled`,
#                         `startup_failure` (YAML vỡ), `timed_out`, `null` treo.
#                         Ở những trạng thái này job `bao-dong` thường **không
#                         chạy tới**, nên nó không phát ra gì.
#
# =============================================================================
# ⚠ NGUONG_GIO CHỌN THEO SỐ ĐO, KHÔNG THEO DÒNG CRON
# =============================================================================
#
# Đo 6 lượt `schedule` gần nhất (T11.67): độ trễ so với `15 2 * * *` là
# **291–726 phút** (4,9–12,1 giờ) ⇒ kết quả rơi vào **07:06–14:21 UTC**, tức
# 14:06–21:21 giờ VN. Chú thích *"09:15, có kết quả trước giờ làm việc"* ở
# `security-scan.yml` đã **hết đúng từ 27/8**.
#
# ⇒ Canh chạy **16:00 UTC** (sau mốc muộn nhất từng đo ~1,6 giờ). Ngưỡng mặc
#   định **30 giờ**: một hệ khoẻ luôn có lượt < 26 giờ tuổi (14:21 UTC hôm
#   trước → 16:00 UTC hôm sau = 25,6 giờ), 30 cho 4 giờ dư. Bỏ trọn một ngày
#   ⇒ ≥ 40 giờ ⇒ **bị bắt**.
#
# ⛔ Đừng siết xuống 24: nó sẽ đỏ giả vào đúng ngày GitHub trễ như đã đo, và
#    lượt sửa lúc ấy rất dễ thành *nới cho hết đỏ* — tức tự tay tháo chuông.
#
set -euo pipefail

KHO="${KHO:-}"
WORKFLOW="${WORKFLOW:-security-scan.yml}"
NHANH="${NHANH:-dev}"
NGUONG_GIO="${NGUONG_GIO:-30}"
BAY_GIO="${BAY_GIO:-}" # epoch giây; để trống thì lấy giờ hệ thống (bài kiểm ghim giờ)

# Nhãn nhận diện issue — gán ĐÚNG MỘT LẦN. Hai hằng số rời nhau là dựng lại luật
# 14: nhánh MỞ tìm một chuỗi, nhánh ĐÓNG tìm chuỗi khác, và issue không bao giờ
# được đóng.
NHAN="canh-cong-quet"

# ⛔ Thiếu công cụ thì ĐỎ, không `exit 0`. `verify-no-keys.sh` đã mắc đúng bẫy
#    này: bốn ngày in "BỎ QUA việc kiểm khoá" mà không ai đọc, và phép kiểm bảo
#    mật duy nhất canh bản dump thoát 0 ở mọi lượt vì VPS không có
#    `postgresql-client` (T11.41). Một cổng kiểm thiếu công cụ là một cổng kiểm
#    KHÔNG CHẠY, và nó ⛔ không được đọc như một cổng kiểm ĐẠT.
if ! command -v gh >/dev/null 2>&1; then
    echo "::error::Không có \`gh\` trên PATH — bộ canh không chạy được, và một bộ canh không chạy KHÔNG phải một bộ canh đạt." >&2
    exit 3
fi

if [ -z "$KHO" ]; then
    echo "::error::Thiếu biến KHO (owner/repo)." >&2
    exit 3
fi

bay_gio() {
    if [ -n "$BAY_GIO" ]; then
        echo "$BAY_GIO"
    else
        date -u +%s
    fi
}

# `run list` trả mảng JSON. Lấy đúng lượt mới nhất của nhánh mốc.
DANH_SACH="$(gh run list \
    --repo "$KHO" \
    --workflow "$WORKFLOW" \
    --branch "$NHANH" \
    --limit 1 \
    --json databaseId,conclusion,status,createdAt,updatedAt,url 2>/dev/null || echo '[]')"

SO_LUOT="$(printf '%s' "$DANH_SACH" | jq 'length')"

TRANG_THAI=""
CHI_TIET=""

if [ "$SO_LUOT" -eq 0 ]; then
    TRANG_THAI="khong-co-luot"
    CHI_TIET="0 lượt chạy nào của \`$WORKFLOW\` trên nhánh \`$NHANH\`. Workflow đã bị xoá, đổi tên, hoặc Actions bị tắt cho kho này."
else
    KET_CUC="$(printf '%s' "$DANH_SACH" | jq -r '.[0].conclusion // "null"')"
    TRANG_THAI_LUOT="$(printf '%s' "$DANH_SACH" | jq -r '.[0].status // "null"')"
    URL_LUOT="$(printf '%s' "$DANH_SACH" | jq -r '.[0].url // ""')"
    LUC="$(printf '%s' "$DANH_SACH" | jq -r '.[0].updatedAt // .[0].createdAt // ""')"

    # ⚠ `date -d` là GNU; macOS dùng `-j -f`. Runner là ubuntu nên `-d` đúng, còn
    #   bài kiểm chạy ở máy dev nên phải đỡ được cả hai — thử GNU trước, rơi về BSD.
    LUC_EPOCH="$(date -u -d "$LUC" +%s 2>/dev/null || date -u -j -f '%Y-%m-%dT%H:%M:%SZ' "$LUC" +%s 2>/dev/null || echo 0)"
    TUOI_GIO=$(( ( $(bay_gio) - LUC_EPOCH ) / 3600 ))

    if [ "$LUC_EPOCH" -eq 0 ]; then
        # ⛔ Không đọc nổi mốc thời gian là một trạng thái RIÊNG, không phải "còn
        #    tươi". Đọc nó thành 0 giờ tuổi là đúng cách một bộ canh chết mà vẫn
        #    in màu xanh (luật 9).
        TRANG_THAI="ket-cuc-la"
        CHI_TIET="Không đọc được mốc thời gian của lượt mới nhất (\`$LUC\`) — bộ canh không tự khẳng định được điều gì, nên nó kêu thay vì đoán. $URL_LUOT"
    elif [ "$TUOI_GIO" -ge "$NGUONG_GIO" ]; then
        TRANG_THAI="qua-han"
        CHI_TIET="Lượt quét mới nhất đã **$TUOI_GIO giờ** tuổi (ngưỡng $NGUONG_GIO giờ) — lịch \`schedule\` nhiều khả năng đã ngừng chạy. $URL_LUOT"
    elif [ "$KET_CUC" = "success" ] || [ "$KET_CUC" = "failure" ]; then
        # `failure` là việc của `bao-dong-quet-cve.sh` — xem khối RANH GIỚI ở đầu tệp.
        TRANG_THAI="binh-thuong"
    else
        TRANG_THAI="ket-cuc-la"
        CHI_TIET="Lượt mới nhất kết thúc với \`$KET_CUC\` (status \`$TRANG_THAI_LUOT\`) — ⛔ không phải success cũng ⛔ không phải failure, nên job báo động của chính lượt quét thường ⛔ không chạy tới và ⛔ không phát ra gì. $URL_LUOT"
    fi
fi

echo "TRANG_THAI=$TRANG_THAI"

SO_HIEU="$(gh issue list --repo "$KHO" --label "$NHAN" --state open --limit 1 --json number --jq '.[0].number // empty' 2>/dev/null || true)"

if [ "$TRANG_THAI" = "binh-thuong" ]; then
    if [ -n "$SO_HIEU" ]; then
        gh issue close "$SO_HIEU" --repo "$KHO" \
            --comment "Lượt quét đã chạy lại và còn tươi (dưới $NGUONG_GIO giờ). Đóng tự động."
    fi
    exit 0
fi

# ⚠ Nháy ngược để TRẦN, ⛔ không `\`` — trong chuỗi nháy đơn thì `\`` ⛔ không phải một escape của
#   printf, nó in ra **cả dấu gạch chéo**: thân issue hiện `\`qua-han\`` thay vì mã nguồn. Một cái
#   chuông đọc như mã vỡ là một cái chuông ít người đọc hơn. Đã đo bằng `od -c` trước khi sửa;
#   `thanIssueKhongCoDauGachCheoThua` canh nó từ đây.
THAN="$(printf '**Trạng thái:** `%s`\n\n%s\n\n---\nBộ canh: `.github/scripts/canh-cong-quet.sh` · ngưỡng %s giờ · nhánh mốc `%s`.\nĐây là chuông cho **sự vắng mặt** của lượt quét. Lượt quét ĐỎ do `bao-dong-quet-cve.sh` phụ trách, kèm bằng chứng.' \
    "$TRANG_THAI" "$CHI_TIET" "$NGUONG_GIO" "$NHANH")"

if [ -n "$SO_HIEU" ]; then
    # ⛔ Đã có issue mở thì **bình luận**, ⛔ không mở cái thứ hai: mỗi ngày một
    #    issue mới là cách một cái chuông tự biến mình thành tiếng ồn, rồi không
    #    ai đọc nữa (T11.84).
    gh issue comment "$SO_HIEU" --repo "$KHO" --body "$THAN"
else
    gh issue create --repo "$KHO" \
        --title "⛔ Cổng quét CVE không chạy — $TRANG_THAI" \
        --label "$NHAN" \
        --body "$THAN"
fi

# ⛔ Thoát KHÁC 0: lượt chạy phải ĐỎ trên tab Actions, ⛔ không chỉ để lại một
#    issue. §10.42 đã đổi một lần dừng hẳn lấy một dòng `::warning::` trên lượt
#    xanh, và chuông ấy kêu đúng nguyên nhân lúc 31/8 23:54:54 rồi **trôi qua**.
exit 1
