#!/usr/bin/env bash
# =============================================================================
# Phạm vi quét CVE — mọi jar trong fat jar PHẢI có mặt trong báo cáo Dependency-Check (T11.83)
#
#   Dùng: phu-quet-cve.sh <fat.jar> <dependency-check-report.json> <ra.txt> [regex-jar-của-dự-án]
#
#   Mã thoát 0  ⇔ mọi jar bên thứ ba trong `BOOT-INF/lib/` đều được báo cáo phủ.
#   Mã thoát 1  ⇔ có jar chưa được quét (liệt kê ở `THIEU:`), hoặc thiếu công cụ / thiếu đầu vào.
#
# VÌ SAO TỒN TẠI
#
#   Ngày 5/9 sổ nợ ghi T11.83: *"cổng quét soi 110 jar trong khi runtime có 121 — bảy gói
#   chưa từng được quét, mọi con số CVE là cận dưới"*. Đo lại 6/9 trên chính báo cáo và fat
#   jar thật: khẳng định ấy SAI. Báo cáo có 110 mục top-level `dependencies[]` **cộng 39 mục
#   `relatedDependencies[]`** — Dependency-Check gộp các jar cùng nhóm/phiên bản dưới một mục
#   cha (`spring-context`/`beans`/`aop`/`tx`/`jdbc` nằm dưới `spring-core`). Tính đủ hai tầng thì
#   báo cáo phủ 146 tên, 115/121 jar runtime có mặt; 6 jar ngoài báo cáo là 5 module của chính
#   kho và `spring-boot-jarmode-tools` — jar plugin chèn lúc repackage, không nằm trong đồ thị
#   Maven nên `aggregate` không thể thấy (đã tắt bằng `includeTools=false`, `app/pom.xml`).
#
#   Người đo sai vì đếm một tầng. Script này tồn tại để **cổng tự nói ra phạm vi của mình mỗi
#   lượt chạy** (CLAUDE.md luật 28) bằng phép đo, thay vì để một người đọc báo cáo rồi ghi kết
#   luận vào sổ — kết luận ấy đã sai một lần và sống trong sổ một ngày.
#
# CÁCH ĐO
#
#   runtime = tên tệp trong `BOOT-INF/lib/*.jar` của fat jar THẬT vừa dựng ở cùng lượt.
#   bao     = `dependencies[].fileName` ∪ `dependencies[].relatedDependencies[].fileName`,
#             chuẩn hoá `X.jar (shaded: …)` / `X.jar: y.js` về `X.jar`.
#   ngoai   = jar khớp regex dự án (mặc định `^songnhue-`) — module của chính kho, không có mục
#             CVE nào để tra, KHÔNG tính là thiếu nhưng vẫn LIỆT KÊ ra (nói ra phạm vi).
#   thieu   = runtime \ ngoai \ bao  ⇒  J > 0 là ĐỎ, không phải cảnh báo (luật 24).
#
#   ⛔ KHÔNG có danh sách ngoại lệ nào khác. `spring-boot-jarmode-tools` được xử lý bằng cách
#   KHÔNG đóng nó vào jar (`<includeTools>false</includeTools>`), và `PhuQuetCveTest` canh
#   dòng pom ấy — cặp đọc–ghi: script này không tha thứ, nên pom phải giữ lời.
#
# ⛔ GIỚI HẠN — ghi vào chính bộ canh (luật 28)
#
#   Nó chứng minh MỖI jar runtime **có tên** trong báo cáo, không chứng minh Dependency-Check
#   nhận diện đúng CPE cho jar ấy — một jar có mặt mà không khớp CPE nào thì vẫn "được phủ" ở
#   đây và vẫn 0 CVE ở kia. Vế ấy không đo được bằng so tên.
#
# BASH 3.2 — máy dev chạy `/bin/bash` 3.2.57 (`CanhBaoQuetCveTest` và họ hàng gọi thẳng
# `/bin/bash`), runner là 5.2. Không mảng kết hợp, không `${var,,}`, không `mapfile`; đếm bằng
# `grep -c .` (không `wc -l`, macOS chèn khoảng trắng); `LC_ALL=C` để `sort`/`comm` một thứ tự.
# =============================================================================
set -euo pipefail
export LC_ALL=C

jar="${1:-}"
bao_cao="${2:-}"
ra="${3:-}"
mau="${4:-^songnhue-}"

if [ -z "$jar" ] || [ -z "$bao_cao" ] || [ -z "$ra" ]; then
    echo "::error::Dùng: $(basename "$0") <fat.jar> <dependency-check-report.json> <ra.txt> [regex-jar-của-dự-án]" >&2
    exit 1
fi

# ⛔ Thiếu công cụ thì ĐỎ, không im lặng đi tiếp (conventions.md §1.5).
for cong_cu in jq unzip; do
    if ! command -v "$cong_cu" >/dev/null 2>&1; then
        echo "::error::Không tìm thấy '$cong_cu' trên PATH — không đo được phạm vi quét CVE." >&2
        exit 1
    fi
done

if [ ! -s "$jar" ]; then
    echo "::error::Không có fat jar '$jar' — phép đo phạm vi không có gì để đo. Bước 'Dựng jar mọi module' đã chạy chưa?" >&2
    exit 1
fi
if [ ! -s "$bao_cao" ]; then
    echo "::error::Không có báo cáo '$bao_cao' — lượt quét chưa sinh JSON, không đo được phạm vi." >&2
    exit 1
fi
if ! jq -e '.dependencies | type == "array"' "$bao_cao" >/dev/null 2>&1; then
    echo "::error::'$bao_cao' không phải báo cáo Dependency-Check hợp lệ (thiếu mảng .dependencies)." >&2
    exit 1
fi

# Chỉ in các dòng KHÔNG rỗng — `comm` coi dòng trống là một phần tử và sẽ so khớp nó.
dong() { printf '%s\n' "$1" | grep . || true; }
dem() { printf '%s\n' "$1" | grep -c . || true; }

# `unzip -Z1` không khớp mẫu nào thì thoát 11 — nuốt bằng `|| true` rồi tự kiểm tập rỗng.
runtime=$(unzip -Z1 "$jar" 'BOOT-INF/lib/*.jar' 2>/dev/null | sed 's#.*/##' | sort -u || true)
if [ -z "$runtime" ]; then
    echo "::error::'$jar' không có jar nào trong BOOT-INF/lib — tập rỗng thì phép đo vô nghĩa (luật 7)." >&2
    exit 1
fi

# Hai tầng: top-level VÀ relatedDependencies. Đếm một tầng là đúng cái lỗi T11.83 đã mắc.
bao=$(jq -r '
    [ .dependencies[].fileName, (.dependencies[].relatedDependencies[]?.fileName) ]
    | map(select(. != null) | sub("\\.jar.*$"; ".jar") | sub(".*/"; ""))
    | unique | .[]' "$bao_cao" | sort -u)
# ⚠ Thứ tự hai phép `sub` là CỐ Ý: cắt hậu tố `.jar…` TRƯỚC rồi mới bỏ đường dẫn. Làm ngược thì
#   `a-1.jar: some/file.js` bị cắt ở dấu `/` cuối thành `file.js` — bài P4 của `PhuQuetCveTest`
#   đã đỏ đúng chỗ này ở lượt viết đầu.
if [ -z "$bao" ]; then
    echo "::error::Báo cáo không có tên phụ thuộc nào — lượt quét đã soi một tập rỗng (luật 7)." >&2
    exit 1
fi

ngoai=$(printf '%s\n' "$runtime" | grep -E -- "$mau" || true)
can=$(printf '%s\n' "$runtime" | grep -Ev -- "$mau" || true)
phu=$(comm -12 <(dong "$can") <(dong "$bao") || true)
thieu=$(comm -23 <(dong "$can") <(dong "$bao") || true)

so_runtime=$(dem "$runtime")
so_phu=$(dem "$phu")
so_ngoai=$(dem "$ngoai")
so_thieu=$(dem "$thieu")

{
    printf 'runtime=%s phu=%s ngoai=%s thieu=%s\n' "$so_runtime" "$so_phu" "$so_ngoai" "$so_thieu"
    dong "$ngoai" | sed 's/^/NGOAI: /'
    dong "$thieu" | sed 's/^/THIEU: /'
} > "$ra.tmp"
mv "$ra.tmp" "$ra"

echo "phạm vi quét CVE: runtime=$so_runtime · phủ=$so_phu · ngoài phạm vi (module dự án)=$so_ngoai · THIẾU=$so_thieu"
cat "$ra"

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
    {
        echo "### Phạm vi quét CVE — đo trên fat jar thật"
        echo ""
        echo "| runtime | phủ | ngoài phạm vi (module dự án) | THIẾU |"
        echo "|---|---|---|---|"
        echo "| $so_runtime | $so_phu | $so_ngoai | **$so_thieu** |"
        if [ "$so_thieu" -gt 0 ]; then
            echo ""
            echo "⛔ Jar có trong runtime mà KHÔNG có trong báo cáo:"
            dong "$thieu" | sed 's/^/- `/; s/$/`/'
        fi
        echo ""
    } >> "$GITHUB_STEP_SUMMARY"
fi

if [ "$so_thieu" -gt 0 ]; then
    echo "::error::$so_thieu jar trong BOOT-INF/lib KHÔNG được lượt quét CVE phủ: $(dong "$thieu" | paste -sd, -)" >&2
    echo "Mọi con số CVE của lượt này là CẬN DƯỚI. Hoặc jar ấy vào fat jar ngoài đồ thị Maven (plugin chèn" >&2
    echo "lúc repackage — xem 'includeTools' ở app/pom.xml), hoặc lượt quét đã bỏ sót một scope." >&2
    echo "⛔ Đừng chữa bằng cách thêm ngoại lệ vào script này — nó cố ý không có danh sách ấy." >&2
    exit 1
fi
