#!/usr/bin/env bash
# =============================================================================
# Chuông báo lượt quét bảo mật — T11.58 · T11.81 · T11.84
#
#   Dùng: bao-dong-quet-cve.sh <do|xanh> <url-lượt-chạy> [van-tay-cve.txt] [phu-quet-cve.txt]
#   Env:  NHANH     nhánh đang chạy (`github.ref_name`) — chỉ `dev` được đóng issue / dời mốc
#         KQ_OWASP  kết quả job quét OWASP (success|failure|cancelled|skipped) — để nói rõ khi
#         KQ_NPM    kết quả job npm audit                                        không có báo cáo
#
# VÌ SAO TỒN TẠI
#
#   Lượt quét theo lịch 2/9/2026 07:06 UTC đỏ với 4 mã CVSS ≥ 7. Tới 3/9 vẫn không ai biết. Đó là
#   lần thứ BA trong năm ngày, và nhịp đỏ 6 lượt `schedule` gần nhất trên `dev` là 4/6. Đo
#   `security-scan.yml` xem nó phát ra gì khi đỏ — KHÔNG GÌ CẢ: `if: failure()` 0 · step summary
#   0 byte · issue/webhook/smtp 0 · quyền `contents: read`. Và thư báo mặc định của GitHub gửi cho
#   *người tạo workflow*, không phải người commit cuối (đo 5 lượt: `quannt18` cả 5, tác giả HEAD
#   là `Toclac18` ở 4/5).
#
# VÌ SAO LÀ ISSUE, KHÔNG PHẢI THƯ HAY WEBHOOK
#
#   Issue **có trạng thái** và **quan sát được bằng API** — một lượt chạy đỏ trôi qua, một issue
#   mở thì nằm đó tới khi có người đóng; kiểm chứng được, không cần secret ngoài, không thêm một
#   dịch vụ có thể 404 trong im lặng.
#
# VÌ SAO TÁCH KHỎI `run:` CỦA WORKFLOW
#
#   Để nhánh quyết định kiểm được bằng **dữ liệu giả** (`CanhBaoQuetCveTest` chạy script này với
#   `gh` giả). Thứ nằm trong `run:` thì muốn tái hiện phải thắng một cuộc đua thật.
#
# ⛔⛔ T11.84 — CHUÔNG MỘT BIT, VÀ NĂM TRẠNG THÁI THAY NÓ
#
#   Từ 3/9 tới 5/9 chuông để lại 9 bình luận trên issue #84, mỗi cái đúng 732 byte, cùng vân tay
#   sau khi bỏ URL — trong khi tập CVE đi 12 → 13 → 12 → 15 → 11 mã và 7 → 8 → 6 mã ≥ 7. Thân
#   bình luận là heredoc cố định; script không đọc báo cáo một dòng nào. *Đỏ như cũ* và *đỏ và tệ
#   hơn* in ra cùng một câu (luật 9), và tiếng ồn giống hệt nhau chính là thứ làm người ta thôi đọc.
#
#   Bất biến từ nay: thân(A) == thân(B) sau khi bỏ URL  ⟺  tập CVE(A) == tập CVE(B).
#
#   | # | Trạng thái               | Điều kiện                                   | `dev`                                   | nhánh khác                      |
#   |---|--------------------------|---------------------------------------------|-----------------------------------------|---------------------------------|
#   | 1 | xanh-có-bằng-chứng       | `xanh` + tệp vân tay đọc được + `ge7=0`     | bình luận rồi `close`                   | KHÔNG đóng (T11.81)             |
#   | 2 | xanh-không-có-báo-cáo    | `xanh` + thiếu tệp vân tay (hoặc ge7 ≠ 0)   | bình luận "không có bằng chứng", exit 1 | như `dev`                       |
#   | 3 | đỏ-không-có-báo-cáo      | `do` + thiếu tệp vân tay                    | bình luận nêu kết quả job; không mốc    | như `dev`                       |
#   | 4 | đỏ-như-cũ                | vân tay == mốc                              | IM LẶNG (log + step summary)            | im lặng                         |
#   | 5 | đỏ-và-đổi                | vân tay ≠ mốc                               | bình luận diff + đổi tiêu đề/body (mốc) | chỉ nói khi có mã ≥ 7 MỚI; KHÔNG dời mốc |
#
#   Trạng thái 2 là lỗ cùng họ T11.81 bịt cùng lượt: thiếu `NVD_API_KEY` ⇒ mọi bước OWASP `skipped`
#   ⇒ job `success` ⇒ chuông cũ đọc là `xanh` và ĐÓNG issue trong khi chẳng quét gì. Từ nay "xanh"
#   phải mang bằng chứng.
#
#   MỐC nằm trong BODY của issue (không phải bình luận): một đối tượng, không lớn theo tuổi issue,
#   và màn đầu của issue luôn là trạng thái hiện tại; bình luận là nhật ký thay đổi. Dạng:
#
#       <!-- van-tay-cve ge7=6 tong=11 suppress=2 npm=success phu=ok ma7=CVE-…,… ma=CVE-…,… -->
#
#   Chỉ lượt trên `dev` được ghi mốc. Nhánh phụ đỏ vẫn được nói khi nó tìm ra mã ≥ 7 chưa có ở mốc
#   (mở thì rộng tay), nhưng KHÔNG dời mốc — nếu không, một `workflow_dispatch` trên nhánh vá đưa
#   6 → 4 mã sẽ làm lượt theo lịch hôm sau trên `dev` báo "LEO THANG 4 → 6" giả.
#
#   Đầu vào là VĂN BẢN THUẦN do `van-tay-cve.sh` / `phu-quet-cve.sh` sinh trong job quét (nơi có
#   báo cáo JSON). Script này cố ý không cần `jq`: chuông chỉ so văn bản, và bài kiểm của nó chỉ
#   cần một tệp giả vài dòng. `van_tay()` là điểm DUY NHẤT mọi phép so đi qua — bài tự-kiểm-của-
#   tự-kiểm thay nó bằng hằng số và đòi bài "đổi ⇒ bình luận" phải mù.
#
# ⛔ GIỚI HẠN — ghi vào chính bộ canh (luật 28)
#
#   Chứng minh **dây đã nối**, không chứng minh **GitHub đã giao** — nửa sau chỉ đo được bằng một
#   lượt chạy thật. Không phủ trường hợp **lượt quét không chạy** (luật 31 — thứ nguy hiểm là sự
#   vắng mặt): vế ấy cần một workflow canh riêng, đang là dòng nợ riêng. Hai lượt chạy SONG SONG
#   trên `dev` có thể cùng thấy mốc cũ và cùng bình luận — hiếm, chấp nhận.
#
# BASH 3.2 — máy dev chạy `/bin/bash` 3.2.57 (bài kiểm gọi thẳng `/bin/bash`); không mảng kết hợp,
# không so số thực (cột 3 của tệp vân tay đã ghi `>=7`/`<7`); `LC_ALL=C` để `sort`/`comm` một thứ tự.
# =============================================================================
set -euo pipefail
export LC_ALL=C

# Nhánh DUY NHẤT được phép đóng issue mốc và dời mốc so sánh. Định nghĩa một lần (luật 14).
NHANH_MOC='dev'

# ⭐ Nhãn nhận diện issue — ĐỊNH NGHĨA ĐÚNG MỘT LẦN, nằm trong TIÊU ĐỀ chứ không dùng label:
#   label phải tạo tay ở GitHub trước, tức thêm đúng cái loại việc mà cả bản vá này muốn bỏ đi
#   ("lệnh nằm sẵn trong tài liệu mà không ai chạy"). Nhánh MỞ, nhánh ĐÓNG và nhánh ĐỔI TIÊU ĐỀ
#   phải dùng cùng một chuỗi — `CanhBaoQuetCveTest` canh điều đó.
NHAN='[quét-cve]'

trang_thai="${1:-}"
url_lan_chay="${2:-}"
tep_van_tay="${3:-}"
tep_phu_quet="${4:-}"
nhanh="${NHANH:-}"
kq_owasp="${KQ_OWASP:-?}"
kq_npm="${KQ_NPM:-?}"
url="${url_lan_chay:-(không rõ)}"

if [ "$trang_thai" != "do" ] && [ "$trang_thai" != "xanh" ]; then
    echo "::error::Tham số 1 phải là 'do' hoặc 'xanh', nhận được: '${trang_thai}'" >&2
    echo "Dùng: $(basename "$0") <do|xanh> <url-lượt-chạy> [van-tay-cve.txt] [phu-quet-cve.txt]" >&2
    exit 1
fi

# ⛔ Không có `gh` thì ĐỎ, không im lặng đi tiếp — đúng bẫy `verify-no-keys.sh` đã mắc (thiếu công
#   cụ thì `exit 0`, và suốt bốn ngày mọi lượt triển khai in "BỎ QUA" mà không ai đọc). Đứng TRƯỚC
#   mọi lệnh ngoài khác: bài E của `CanhBaoQuetCveTest` chạy với PATH rỗng.
if ! command -v gh >/dev/null 2>&1; then
    echo "::error::Không tìm thấy 'gh' trên PATH — chuông báo CVE KHÔNG chạy được." >&2
    echo "Đây là lỗi, không phải trường hợp bỏ qua được: lượt quét có thể đang đỏ mà không ai được báo." >&2
    exit 1
fi

tom_tat_buoc() { if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then printf '%s\n' "$@" >> "$GITHUB_STEP_SUMMARY"; fi; }

# ── Bằng chứng của lượt này: tệp vân tay (CVE) và tệp phạm vi (jar) ─────────────────────────────
co_bang_chung=false
ge7=0
tong=0
suppress=0
ma7=""
ma=""
if [ -n "$tep_van_tay" ] && [ -s "$tep_van_tay" ] && grep -q '^ge7=' "$tep_van_tay"; then
    co_bang_chung=true
    ge7=$(grep '^ge7=' "$tep_van_tay" | head -n 1 | cut -d= -f2 || true)
    tong=$(grep '^tong=' "$tep_van_tay" | head -n 1 | cut -d= -f2 || true)
    suppress=$(grep '^suppress=' "$tep_van_tay" | head -n 1 | cut -d= -f2 || true)
    ge7="${ge7:-0}"
    tong="${tong:-0}"
    suppress="${suppress:-0}"
    # Cột 3 đã ghi `>=7`/`<7` — không so số thực trong bash.
    ma7=$(awk -F'\t' 'NF >= 4 && $3 == ">=7" { print $1 }' "$tep_van_tay" | sort -u | paste -sd, - || true)
    ma=$(awk -F'\t' 'NF >= 4 { print $1 }' "$tep_van_tay" | sort -u | paste -sd, - || true)
fi

phu="?"
phu_chi_tiet=""
thieu_ds=""
if [ -n "$tep_phu_quet" ] && [ -s "$tep_phu_quet" ] && grep -q '^runtime=' "$tep_phu_quet"; then
    phu_chi_tiet=$(head -n 1 "$tep_phu_quet")
    so_thieu=$(printf '%s' "$phu_chi_tiet" | grep -oE 'thieu=[0-9]+' | cut -d= -f2 || true)
    so_thieu="${so_thieu:-0}"
    if [ "$so_thieu" = 0 ]; then phu=ok; else phu="thieu-$so_thieu"; fi
    thieu_ds=$(grep '^THIEU: ' "$tep_phu_quet" | sed 's/^THIEU: //' | paste -sd, - || true)
fi

# ── Issue mốc đang mở, và mốc so sánh ghi trong body của nó ────────────────────────────────────
# `--json` rồi tự bóc bằng grep thay vì `--jq`: ít phụ thuộc hơn, và `gh` giả trong bài kiểm chỉ
# cần in JSON. Mốc đọc bằng mẫu KHÔNG phụ thuộc `<!--`/`-->` để không kẹt vào việc gh có escape
# HTML trong JSON hay không (đo: không escape, nhưng bài G18 vẫn giả `<`).
danh_sach=$(gh issue list --search "in:title $NHAN" --state open --json number --limit 1 2>/dev/null || true)
so_issue=$(printf '%s' "$danh_sach" | grep -oE '"number"[[:space:]]*:[[:space:]]*[0-9]+' | grep -oE '[0-9]+' | head -1 || true)

moc=""
if [ -n "$so_issue" ]; then
    json_body=$(gh issue view "$so_issue" --json body 2>/dev/null || true)
    moc=$(printf '%s' "$json_body" | grep -oE 'van-tay-cve( [a-z0-9]+=[A-Za-z0-9,.?_-]*)+' | tail -n 1 || true)
fi
truong() { printf '%s' "$moc" | tr ' ' '\n' | grep -E "^$1=" | head -n 1 | cut -d= -f2- || true; }
moc_ge7=$(truong ge7)
moc_tong=$(truong tong)
moc_suppress=$(truong suppress)
moc_npm=$(truong npm)
moc_phu=$(truong phu)
moc_ma7=$(truong ma7)
moc_ma=$(truong ma)

echo "trạng thái=$trang_thai · nhánh=${nhanh:-(không rõ)} · issue mốc=${so_issue:-(không có)} · bằng chứng=$co_bang_chung · ge7=$ge7 tong=$tong suppress=$suppress phu=$phu npm=$kq_npm"
echo "mốc trong body: ${moc:-(không có)}"

# ── Vân tay: điểm DUY NHẤT mọi phép so đi qua. Một dòng — bài tự-kiểm-của-tự-kiểm thay nó bằng hằng số. ──
chuan_hoa() { printf '%s' "$1" | tr ',' '\n' | grep . | sort -u | paste -sd, - || true; }
tung_dong() { printf '%s' "$1" | tr ',' '\n' | grep . | sort -u || true; }
van_tay() { printf 'ma7=%s|ma=%s|phu=%s|npm=%s|suppress=%s' "$(chuan_hoa "$1")" "$(chuan_hoa "$2")" "$3" "$4" "$5"; }

dau_moc() {
    printf '<!-- van-tay-cve ge7=%s tong=%s suppress=%s npm=%s phu=%s ma7=%s ma=%s -->' \
        "$ge7" "$tong" "$suppress" "$kq_npm" "$phu" "$(chuan_hoa "$ma7")" "$(chuan_hoa "$ma")"
}

dat_tieu_de() { TIEU_DE="$NHAN Lượt quét phụ thuộc — $1"; }

# Thân dựng bằng printf với định dạng trong NHÁY ĐƠN và heredoc CÓ NHÁY: bản đầu (3/9) viết thân
# trong nháy kép, `dependency-check-report` nằm giữa hai dấu huyền bị bash chạy như một lệnh, script
# thoát 127 ở cả hai nhánh đỏ. Không một dấu huyền nào dưới đây nằm trong nháy kép.
bang_cve() {
    if [ "$co_bang_chung" = true ] && [ -n "$ma" ]; then
        printf '| CVE | Điểm (max v2/v3/v4) | ≥ 7 | Jar |\n|---|---|---|---|\n'
        awk -F'\t' 'NF >= 4 { printf "| %s | %s | %s | %s |\n", $1, $2, ($3 == ">=7" ? "**≥ 7**" : "<7"), $4 }' "$tep_van_tay"
    else
        printf '_(không có mã nào trong báo cáo)_\n'
    fi
}

dong_pham_vi() {
    case "$phu" in
        ok)      printf 'Phạm vi quét: **đủ** — `%s`\n' "$phu_chi_tiet" ;;
        thieu-*) printf '⛔ Phạm vi quét THIẾU — `%s`; jar chưa được quét: `%s`\n' "$phu_chi_tiet" "$thieu_ds" ;;
        *)       printf 'Phạm vi quét: **không đo được** (không có `phu-quet-cve.txt` trong artifact)\n' ;;
    esac
}

quy_tac() {
    cat <<'HET'
Việc phải làm, theo `conventions.md` §4.5:

1. **Nâng cấp trước, suppress sau.** Tra bản vá bằng `maven-metadata.xml`,
   KHÔNG bằng API tìm kiếm (luật 21).
2. Nâng không được thì thẩm định *"không áp dụng"* — và lý do phải nói ĐƯỢC hay
   KHÔNG áp dụng, không phải *"chưa có bản vá"*.
3. ⛔ Lý do *"dòng này hết hỗ trợ"* **không hợp lệ** — nó biến một ngày hết hạn
   đã tới thành một ngày hết hạn tự đặt. Việc phải làm khi ấy là **đổi dòng**.

Issue này **tự đóng** khi lượt quét trên `dev` xanh trở lại **có bằng chứng** (tệp vân tay `ge7=0`).
Báo cáo đầy đủ nằm ở artifact `dependency-check-report` của lượt chạy (hết hạn sau 14 ngày).
HET
}

# ═══════════════════════════════════════════════════════════════════════════════════════════════
# XANH
# ═══════════════════════════════════════════════════════════════════════════════════════════════
if [ "$trang_thai" = "xanh" ]; then
    if [ "$co_bang_chung" != true ] || [ "$ge7" != 0 ]; then
        # ── Trạng thái 2: xanh-không-có-báo-cáo ──
        dat_tieu_de "XANH nhưng KHÔNG có bằng chứng quét"
        than=$(
            printf 'Lượt quét phụ thuộc báo **XANH** nhưng **KHÔNG có bằng chứng quét** '
            if [ "$co_bang_chung" != true ]; then
                printf '(không có tệp vân tay `van-tay-cve.txt` trong artifact).\n\n'
                printf 'Hình dạng quen: `NVD_API_KEY` vắng ⇒ mọi bước OWASP `skipped` ⇒ job vẫn `success`. '
            else
                printf '(tệp vân tay ghi ge7=%s ≠ 0 trong khi job báo xanh — mâu thuẫn).\n\n' "$ge7"
            fi
            printf 'Một lượt "xanh" như vậy không chứng minh gì (luật 31).\n\n'
            printf '⛔ **KHÔNG đóng issue mốc.** owasp=`%s` · npm=`%s` · nhánh=`%s`\n\n' "$kq_owasp" "$kq_npm" "${nhanh:-(không rõ)}"
            printf 'Lượt chạy: %s\n' "$url"
        )
        if [ -n "$so_issue" ]; then
            gh issue comment "$so_issue" --body "$than"
            echo "→ xanh KHÔNG có bằng chứng: đã bình luận vào issue #${so_issue}, KHÔNG đóng"
        else
            gh issue create --title "$TIEU_DE" --body "$than"
            echo "→ xanh KHÔNG có bằng chứng và chưa có issue mốc: đã mở issue"
        fi
        tom_tat_buoc "### ⛔ XANH nhưng không có bằng chứng quét" "" "owasp=$kq_owasp · npm=$kq_npm · nhánh=${nhanh:-?} · tệp vân tay: ${tep_van_tay:-(không có)}"
        echo "::error::Lượt quét báo xanh mà không có tệp vân tay — không có gì chứng minh phép quét đã chạy." >&2
        exit 1
    fi

    # ⛔⛔ T11.81 — CHỈ `dev` mới được đóng issue mốc. Nhánh phụ sạch không chứng minh `dev` sạch:
    #    khi làm T11.69 người ta bấm `workflow_dispatch` trên nhánh vá để xem đã sạch chưa; lượt ấy xanh
    #    mà đóng #84 thì lượt theo lịch hôm sau mở issue MỚI — số issue thôi khớp số lượt đỏ.
    if [ "$nhanh" != "$NHANH_MOC" ]; then
        echo "→ lượt XANH này chạy trên nhánh '${nhanh:-(không rõ)}', không phải '$NHANH_MOC'."
        echo "  ⛔ KHÔNG đóng issue mốc: một nhánh phụ sạch không chứng minh '$NHANH_MOC' đã sạch."
        tom_tat_buoc "### Xanh trên nhánh phụ — không đóng issue mốc của \`$NHANH_MOC\`"
        exit 0
    fi
    # ── Trạng thái 1: xanh-có-bằng-chứng trên dev ──
    if [ -n "$so_issue" ]; then
        than=$(
            printf 'Lượt quét phụ thuộc đã **XANH** trở lại trên `%s`, có bằng chứng: `ge7=%s tong=%s suppress=%s`.\n\n' \
                "$NHANH_MOC" "$ge7" "$tong" "$suppress"
            dong_pham_vi
            printf '\nLượt chạy: %s\n\nĐóng issue mốc. Lượt đỏ kế tiếp sẽ mở lại một issue mới.\n' "$url"
        )
        # Bình luận TRƯỚC rồi mới đóng — đóng trước thì dòng giải thích rơi vào một issue đã đóng.
        gh issue comment "$so_issue" --body "$than"
        gh issue close "$so_issue"
        echo "→ đã đóng issue #${so_issue}"
    else
        echo "→ không có issue mốc nào đang mở, không phải làm gì"
    fi
    tom_tat_buoc "### ✅ Xanh có bằng chứng: ge7=$ge7 · tong=$tong · suppress=$suppress"
    exit 0
fi

# ═══════════════════════════════════════════════════════════════════════════════════════════════
# ĐỎ
# ═══════════════════════════════════════════════════════════════════════════════════════════════
if [ "$co_bang_chung" != true ]; then
    # ── Trạng thái 3: đỏ-không-có-báo-cáo — lỗi CÔNG CỤ, khác hẳn "có CVE". Không mốc, không đổi tiêu đề. ──
    dat_tieu_de "ĐỎ · KHÔNG có báo cáo (lỗi công cụ)"
    than=$(
        printf 'Lượt quét phụ thuộc **ĐỎ** nhưng **KHÔNG có báo cáo** để đọc — đây là lỗi CÔNG CỤ, không phải kết luận về CVE.\n\n'
        printf 'owasp=`%s` · npm=`%s` · nhánh=`%s`\n\n' "$kq_owasp" "$kq_npm" "${nhanh:-(không rõ)}"
        printf 'Nguyên nhân quen: job OWASP bị huỷ vì timeout · NVD không trả lời · bước cập nhật CSDL hỏng · artifact không tải được · chỉ `npm audit` đỏ.\n\n'
        dong_pham_vi
        printf '\nLượt chạy: %s\n\nMốc so sánh CVE của `%s` KHÔNG thay đổi.\n' "$url" "$NHANH_MOC"
    )
    if [ -n "$so_issue" ]; then
        gh issue comment "$so_issue" --body "$than"
        echo "→ đỏ KHÔNG có báo cáo: đã bình luận vào issue #${so_issue}"
    else
        gh issue create --title "$TIEU_DE" --body "$than"
        echo "→ đỏ KHÔNG có báo cáo và chưa có issue mốc: đã mở issue"
    fi
    tom_tat_buoc "### ⛔ Đỏ nhưng KHÔNG có báo cáo — lỗi công cụ" "" "owasp=$kq_owasp · npm=$kq_npm"
    exit 0
fi

vt_moi=$(van_tay "$ma7" "$ma" "$phu" "$kq_npm" "$suppress")
vt_moc=$(van_tay "$moc_ma7" "$moc_ma" "$moc_phu" "$moc_npm" "$moc_suppress")
moi7=$(comm -23 <(tung_dong "$ma7") <(tung_dong "$moc_ma7") | paste -sd, - || true)
het7=$(comm -13 <(tung_dong "$ma7") <(tung_dong "$moc_ma7") | paste -sd, - || true)
moi=$(comm -23 <(tung_dong "$ma") <(tung_dong "$moc_ma") | paste -sd, - || true)
het=$(comm -13 <(tung_dong "$ma") <(tung_dong "$moc_ma") | paste -sd, - || true)

if [ -n "$moc" ] && [ "$vt_moi" = "$vt_moc" ]; then
    # ── Trạng thái 4: đỏ-như-cũ — IM LẶNG. Tiếng ồn giống hệt nhau là thứ đã che mất lượt tăng. ──
    echo "→ đỏ-như-cũ: vân tay khớp mốc ($ge7 mã ≥ 7 · $tong mã · phu=$phu · npm=$kq_npm) — KHÔNG bình luận"
    tom_tat_buoc "### 🔴 Đỏ như cũ — không đổi so với mốc trong issue #${so_issue}" "" \
        "ge7=$ge7 · tong=$tong · suppress=$suppress · phu=$phu · npm=$kq_npm" "" "$(bang_cve)" "" "$(dong_pham_vi)"
    exit 0
fi

if [ -z "$moc" ]; then
    nhan_doi="MỐC ĐẦU"
elif [ -n "$moi7" ]; then
    nhan_doi="LEO THANG"
elif [ -z "$moi" ] && { [ -n "$het" ] || [ -n "$het7" ]; }; then
    nhan_doi="GIẢM"
else
    nhan_doi="ĐỔI"
fi
echo "→ đỏ-và-đổi: $nhan_doi · mới≥7=[${moi7:-}] hết≥7=[${het7:-}] mới=[${moi:-}] hết=[${het:-}]"

# Nhánh phụ: nói khi tìm ra mã ≥ 7 chưa có ở mốc — và chỉ khi ấy; KHÔNG dời mốc, KHÔNG đổi tiêu đề.
if [ "$nhanh" != "$NHANH_MOC" ]; then
    if [ -n "$moi7" ]; then
        than=$(
            printf 'Lượt quét trên nhánh phụ `%s` tìm ra **mã CVSS ≥ 7 chưa có ở mốc của `%s`**: `%s`.\n\n' "$nhanh" "$NHANH_MOC" "$moi7"
            printf 'Lượt này có %s mã ≥ 7 · %s mã · suppress=%s · npm=%s.\n\n' "$ge7" "$tong" "$suppress" "$kq_npm"
            bang_cve
            printf '\n%s\nLượt chạy: %s\n\n⚠ Mốc so sánh của `%s` **không thay đổi** — chỉ lượt trên `%s` mới dời mốc.\n' \
                "$(dong_pham_vi)" "$url" "$NHANH_MOC" "$NHANH_MOC"
        )
        if [ -n "$so_issue" ]; then
            gh issue comment "$so_issue" --body "$than"
            echo "→ nhánh phụ có mã ≥ 7 mới: đã bình luận vào issue #${so_issue} (không dời mốc)"
        else
            dat_tieu_de "ĐỎ trên nhánh phụ · $ge7 mã CVSS ≥ 7 (chưa có mốc dev)"
            gh issue create --title "$TIEU_DE" --body "$than"
            echo "→ nhánh phụ có mã ≥ 7 mới và chưa có issue mốc: đã mở issue (không mốc)"
        fi
    else
        echo "→ nhánh phụ '$nhanh' đỏ nhưng không có mã ≥ 7 mới so với mốc '$NHANH_MOC' — im lặng"
    fi
    tom_tat_buoc "### Đỏ trên nhánh phụ \`$nhanh\` — $nhan_doi so với mốc \`$NHANH_MOC\`" "" \
        "mới ≥ 7: ${moi7:-(không)} · hết ≥ 7: ${het7:-(không)}" "" "$(bang_cve)"
    exit 0
fi

# ── Trạng thái 5 trên dev: bình luận diff (nhật ký) + ghi mốc mới vào body + tiêu đề mang số ──
dat_tieu_de "ĐỎ · $ge7 mã CVSS ≥ 7 · $tong mã"

than_binh_luan=$(
    printf 'Lượt quét phụ thuộc **ĐỎ** — **%s** so với mốc trước.\n\n' "$nhan_doi"
    printf '| | Mốc trước | Lượt này |\n|---|---|---|\n'
    printf '| mã CVSS ≥ 7 | %s | **%s** |\n' "${moc_ge7:-—}" "$ge7"
    printf '| tổng mã | %s | %s |\n' "${moc_tong:-—}" "$tong"
    printf '| mã bị suppression che | %s | %s |\n' "${moc_suppress:-—}" "$suppress"
    printf '| npm audit | %s | %s |\n' "${moc_npm:-—}" "$kq_npm"
    printf '| phạm vi quét | %s | %s |\n\n' "${moc_phu:-—}" "$phu"
    printf 'MỚI (≥ 7): %s\nHẾT (≥ 7): %s\nMỚI (mọi mức): %s\nHẾT (mọi mức): %s\n\n' \
        "${moi7:-(không)}" "${het7:-(không)}" "${moi:-(không)}" "${het:-(không)}"
    bang_cve
    printf '\n%s\nLượt chạy: %s\n\n' "$(dong_pham_vi)" "$url"
    quy_tac
)

than_moc=$(
    printf 'Lượt quét phụ thuộc trên `%s` **ĐỎ** — **%s mã CVSS ≥ 7** · %s mã · %s mã bị suppression che · npm audit: %s.\n\n' \
        "$NHANH_MOC" "$ge7" "$tong" "$suppress" "$kq_npm"
    bang_cve
    printf '\n%s\nLượt chạy gần nhất làm đổi trạng thái: %s\n\n' "$(dong_pham_vi)" "$url"
    quy_tac
    printf '\n%s\n' "$(dau_moc)"
)

if [ -n "$so_issue" ]; then
    gh issue comment "$so_issue" --body "$than_binh_luan"
    gh issue edit "$so_issue" --title "$TIEU_DE" --body "$than_moc"
    echo "→ $nhan_doi: đã bình luận vào issue #${so_issue} và ghi mốc mới vào body + tiêu đề"
else
    gh issue create --title "$TIEU_DE" --body "$than_moc"
    echo "→ chưa có issue mốc: đã mở issue mới với mốc trong body"
fi
tom_tat_buoc "### 🔴 Đỏ và ĐỔI — $nhan_doi" "" "ge7 ${moc_ge7:-—} → **$ge7** · tong ${moc_tong:-—} → $tong · phu=$phu · npm=$kq_npm" "" \
    "mới ≥ 7: ${moi7:-(không)} · hết ≥ 7: ${het7:-(không)}" "" "$(bang_cve)"
exit 0
