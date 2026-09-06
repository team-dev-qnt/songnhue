#!/usr/bin/env bash
# =============================================================================
# Vân tay CVE của một lượt quét — đọc báo cáo Dependency-Check, ghi ra văn bản thuần (T11.84)
#
#   Dùng: van-tay-cve.sh <dependency-check-report.json> <ra.txt>
#
#   Tệp ra (đã sắp, quyết định, không ngày giờ):
#       ge7=<số mã có điểm ≥ 7>
#       tong=<số mã khác nhau>
#       suppress=<số mã đang bị suppression che>
#       <mã><TAB><điểm max><TAB>>=7|<7<TAB><jar1,jar2,…>       # một dòng mỗi mã, sắp theo mã
#
#   Mã thoát 0 ⇔ đọc được báo cáo và ghi xong. Thiếu công cụ / thiếu báo cáo / JSON hỏng ⇔ 1,
#   và KHÔNG tạo tệp ra — chuông đọc "không có tệp" thành trạng thái *không có báo cáo*.
#
# VÌ SAO TỒN TẠI
#
#   Chuông `bao-dong-quet-cve.sh` từ 3/9 tới 5/9 để lại 9 bình luận trên issue #84, mỗi cái đúng
#   732 byte, cùng vân tay sau khi bỏ URL — trong khi tập CVE đi 12 → 13 → 12 → 15 → 11 mã và
#   7 → 8 → 6 mã ≥ 7. Nó không đọc báo cáo một dòng nào. Một chuông không phân biệt được
#   *đỏ như cũ* với *đỏ và tệ hơn* thì không nói gì (CLAUDE.md luật 9), và tiếng ồn giống hệt
#   nhau chính là thứ làm người ta thôi đọc nó.
#
#   Script này là nửa "đọc": nó chạy trong job `owasp` (nơi có báo cáo JSON), rút một bản tóm tắt
#   văn bản thuần rồi đưa vào artifact. Chuông chỉ so sánh văn bản — không cần `jq`, và bài kiểm
#   của chuông chỉ cần một tệp giả vài dòng.
#
# ⚠⚠ ĐIỂM — đọc đúng trường, lấy MAX qua mọi thang (conventions.md §4.5 mục 4)
#
#   Báo cáo ODC 12.1.3 THẬT (đo 6/9 trên lượt `33951186299`) đặt điểm ở `cvssv2.score`,
#   `cvssv3.baseScore` và `cvssv4.baseScore` — KHÔNG phải `cvssvX.cvssData.baseScore` như JSON
#   của API NVD. Bản nháp đầu của bộ lọc này viết theo hình dạng API NVD và cho ge7=0 trên một báo
#   cáo có 6 mã ≥ 7: xanh giả hoàn hảo. Vì thế bộ lọc đọc CẢ HAI hình dạng, và `VanTayCveTest`
#   neo một trích đoạn báo cáo thật để bộ lọc kiểu "API NVD" phải đỏ ở đó (luật 25).
#
#   Trường `severity` cấp trên cùng KHÔNG dùng được để lọc — nó lấy theo v4, mà cổng chặn theo
#   điểm cao nhất mọi thang (một mã v4 = 6.9 / v3 = 7.5 là ≥ 7).
#
# BASH 3.2 — máy dev chạy `/bin/bash` 3.2.57; không so số thực trong bash, nên cột 3 ghi sẵn
# `>=7`/`<7` để chuông chỉ cần `grep`. `LC_ALL=C` để thứ tự sắp xếp là một.
# =============================================================================
set -euo pipefail
export LC_ALL=C

bao_cao="${1:-}"
ra="${2:-}"

if [ -z "$bao_cao" ] || [ -z "$ra" ]; then
    echo "::error::Dùng: $(basename "$0") <dependency-check-report.json> <ra.txt>" >&2
    exit 1
fi

# ⛔ Thiếu công cụ thì ĐỎ, không im lặng đi tiếp (conventions.md §1.5).
if ! command -v jq >/dev/null 2>&1; then
    echo "::error::Không tìm thấy 'jq' trên PATH — không rút được vân tay CVE." >&2
    exit 1
fi

if [ ! -s "$bao_cao" ]; then
    echo "::error::Không có báo cáo '$bao_cao' — lượt quét chưa sinh JSON. Không có vân tay để so." >&2
    exit 1
fi
if ! jq -e '.dependencies | type == "array"' "$bao_cao" >/dev/null 2>&1; then
    echo "::error::'$bao_cao' không phải báo cáo Dependency-Check hợp lệ (thiếu mảng .dependencies)." >&2
    exit 1
fi

# `def diem`: max của mọi điểm tìm được, đọc cả hai hình dạng trường (xem đầu tệp). Không điểm nào ⇒ 0.
# `sub("\\.jar.*$"; ".jar")`: gom `X.jar (shaded: …)` / `X.jar: y.js` về `X.jar`.
tam="$ra.tmp"
jq -r '
    def diem: [ .cvssv2.score?, .cvssv2.cvssData.baseScore?,
                .cvssv3.baseScore?, .cvssv3.cvssData.baseScore?,
                .cvssv4.baseScore?, .cvssv4.cvssData.baseScore? ]
              | map(select(. != null) | (tonumber? // empty)) | max // 0;
    . as $bc
    | [ .dependencies[] as $d
        | ($d.vulnerabilities // [])[]
        | { ma: .name, diem: diem, jar: ($d.fileName | sub("\\.jar.*$"; ".jar") | sub(".*/"; "")) } ]
    | group_by(.ma)
    | map({ ma: .[0].ma, diem: (map(.diem) | max), jar: (map(.jar) | unique | join(",")) })
    | sort_by(.ma)
    | "ge7=\(map(select(.diem >= 7)) | length)",
      "tong=\(length)",
      "suppress=\([ $bc.dependencies[] | .suppressedVulnerabilities[]? | .name ] | unique | length)",
      (.[] | "\(.ma)\t\(.diem)\t\(if .diem >= 7 then ">=7" else "<7" end)\t\(.jar)")
' "$bao_cao" > "$tam"
mv "$tam" "$ra"

ge7=$(grep '^ge7=' "$ra" | cut -d= -f2)
tong=$(grep '^tong=' "$ra" | cut -d= -f2)
suppress=$(grep '^suppress=' "$ra" | cut -d= -f2)
echo "vân tay CVE: ge7=$ge7 · tong=$tong · suppress=$suppress → $ra"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
        echo "### CVE lượt này — $ge7 mã CVSS ≥ 7 · $tong mã · $suppress mã bị suppression che"
        echo ""
        echo "| CVE | Điểm (max v2/v3/v4) | ≥ 7 | Jar |"
        echo "|---|---|---|---|"
        # `awk -F'\t'` thay cho sed: BSD sed không hiểu `\t`.
        awk -F'\t' 'NF >= 4 { printf "| %s | %s | %s | %s |\n", $1, $2, ($3 == ">=7" ? "**≥ 7**" : "<7"), $4 }' "$ra"
        echo ""
    } >> "$GITHUB_STEP_SUMMARY"
fi
