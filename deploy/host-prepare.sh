#!/usr/bin/env bash
# =============================================================================
# host-prepare.sh — dựng phần HOST của một máy chủ songnhue (T11.35)
#
# Đóng gói ba ô mà `docs/deploy-production-guideline.md` §3.2 và §4.2 vẫn đang bắt gõ tay. Ba ô ấy có
# một điểm chung: sai thì **mọi lượt deploy đỏ**, và thông báo lỗi không trỏ về đây.
#
# ⭐ VÌ SAO PHẢI LÀ SCRIPT, KHÔNG PHẢI MỘT KHỐI LỆNH TRONG TÀI LIỆU
#   Một khối lệnh trong tài liệu được chép đi chép lại bằng tay, mỗi máy một dị bản, và không có gì
#   nói cho ta biết máy nào đang lệch. Script này **in một con số đo được ở mỗi bước** rồi tự kiểm
#   lại ở cuối — nên chạy nó hai lần cho cùng một kết quả, và đọc output là biết máy đang ở đâu.
#
# ⛔ BA CÁI BẪY ĐÃ TRẢ GIÁ, ghi ở đây vì chúng không tự hiển nhiên:
#
#   1. `rsync` KHÔNG có sẵn trên Ubuntu 24.04 tối giản. Bước đồng bộ của `deploy.yml` đòi rsync ở
#      **cả hai đầu**; thiếu ở máy chủ thì lượt CD đầu tiên chết ở đúng đó. Đo trên VPS-1 ngày 6/9:
#      chưa cài.
#
#   2. Chủ sở hữu cây thư mục **KHÔNG phải user SSH**. Ba danh tính khác nhau dùng chung nó:
#        `1000`   — user trong image `app` (ghim ở `backend.Dockerfile`): đọc khoá, ghi log
#        `999`    — user `postgres` BÊN TRONG container: `pre-deploy-dump.sh` ghi bản dump ở đó
#        user SSH — người vận hành: sửa `.env`, dọn bản cũ
#      `chown -R songnhue:songnhue` là bản cũ của tài liệu chung, và nó SAI theo kiểu làm mọi lượt
#      deploy đỏ (đã trả giá trên staging 25/8). Nặng nhất là thư mục sao lưu: `chown -R 1000:1000`
#      ở đó làm `pg_dump` hỏng, mà bước chụp trước triển khai chạy ở MỌI lượt deploy.
#      ⚠ Trên VPS-1 user SSH `songnhue` có uid **1001**, không phải 1000 — nên `chown` theo TÊN cho
#        ra một chủ sở hữu khác hẳn thứ container cần. Ở đây chỉ dùng SỐ.
#
#   3. `chown` trong Dockerfile không có tác dụng với bind mount — host che hoàn toàn thứ image
#      dựng sẵn. Quyền phải đặt trên máy chủ, và chỉ ở đây.
#
# DÙNG:  sudo bash deploy/host-prepare.sh            # dựng + tự kiểm
#        sudo bash deploy/host-prepare.sh --kiem     # CHỈ đo, không sửa gì
#
# Đổi được bằng biến môi trường khi image đổi uid: APP_UID · APP_GID · PG_UID.
# =============================================================================
set -euo pipefail

APP_UID="${APP_UID:-1000}"
APP_GID="${APP_GID:-1000}"
PG_UID="${PG_UID:-999}"

CHI_KIEM=0
[ "${1:-}" = "--kiem" ] && CHI_KIEM=1

if [ "$(id -u)" -ne 0 ] && [ "$CHI_KIEM" -eq 0 ]; then
    echo "✗ Cần quyền root: sudo bash deploy/host-prepare.sh" >&2
    exit 1
fi

loi=0
buoc() { printf '\n══ %s\n' "$*"; }
do_dac()  { printf '   %-46s %s\n' "$1" "$2"; }
canh()  { printf '   ⚠ %s\n' "$*"; }
hong()  { printf '   ✗ %s\n' "$*"; loi=$((loi + 1)); }

# ─────────────────────────────────────────────────────────────────────────────
buoc "1 · Công cụ bắt buộc trên host"
# `rsync` là thứ `deploy.yml` gọi thẳng; `curl`/`ca-certificates` để tra được GHCR và Let's Encrypt.
for goi in rsync curl ca-certificates; do
    if command -v "$goi" >/dev/null 2>&1 || dpkg -s "$goi" >/dev/null 2>&1; then
        do_dac "$goi" "đã có"
    elif [ "$CHI_KIEM" -eq 1 ]; then
        hong "$goi CHƯA CÀI — bước rsync của CD sẽ chết ở đây"
    else
        do_dac "$goi" "đang cài…"
        apt-get update -qq && apt-get install -y -qq "$goi" >/dev/null
        command -v "$goi" >/dev/null 2>&1 && do_dac "$goi" "đã cài xong" || hong "$goi cài không được"
    fi
done
# ⚠ `awk NR==1`, KHÔNG `head -1`: dưới `pipefail`, `head` đóng ống sớm làm `rsync` ăn SIGPIPE, lệnh
#   thay thế trả mã khác 0, và nhánh `|| echo` chạy THÊM — in ra cả phiên bản lẫn "KHÔNG CÓ". Đã tự
#   mắc đúng bẫy ấy ở lượt chạy thật đầu tiên trên VPS-1. `awk` đọc hết đầu vào nên không có SIGPIPE.
do_dac "rsync --version" "$(rsync --version 2>/dev/null | awk 'NR==1' || echo 'KHÔNG CÓ')"

# ─────────────────────────────────────────────────────────────────────────────
buoc "2 · Cây thư mục"
THU_MUC="/opt/songnhue /opt/songnhue/keys /var/lib/songnhue/backup /var/log/songnhue /var/log/nginx"
if [ "$CHI_KIEM" -eq 0 ]; then
    # shellcheck disable=SC2086
    mkdir -p $THU_MUC
fi
for d in $THU_MUC; do
    [ -d "$d" ] && do_dac "$d" "có" || hong "$d CHƯA CÓ"
done

# ─────────────────────────────────────────────────────────────────────────────
buoc "3 · Chủ sở hữu và quyền — bằng SỐ, không bằng tên"
# Cần ở CẢ hai chế độ: `--kiem` cũng phải biết ai là người triển khai để thử ghi.
NGUOI_SSH="${SUDO_USER:-$(id -un)}"
if [ "$CHI_KIEM" -eq 0 ]; then
    getent group "$APP_GID" >/dev/null 2>&1 || groupadd -g "$APP_GID" songnhue-app
    # User SSH vào chung nhóm với app để còn sửa `.env` và đọc log.
    if [ "$NGUOI_SSH" != "root" ]; then
        usermod -aG "$APP_GID" "$NGUOI_SSH" 2>/dev/null || canh "không thêm được $NGUOI_SSH vào nhóm $APP_GID"
    fi

    # ⛔⛔ Thư mục GỐC thuộc NGƯỜI TRIỂN KHAI, không phải root. `mkdir -p /opt/songnhue/keys`
    #    tạo thư mục cha bằng root, và cả ba phép `kiem_quyen` bên dưới vẫn XANH — nhưng `rsync`
    #    của CD ghi bằng user SSH nên thoát **23** với hàng chục dòng "Permission denied".
    #    Đo được 7/9 trên VPS-1: script này chạy 2 lượt, thoát 0 cả hai, mà thư mục vẫn root:root.
    #    Đây là chỗ DUY NHẤT trong tệp mà chown theo TÊN là đúng — đích danh user SSH, vì uid của
    #    nó khác nhau giữa hai máy chủ (1001 ở cả hai hiện nay, nhưng không có gì bảo đảm điều đó).
    if [ "$NGUOI_SSH" != "root" ]; then
        chown "$NGUOI_SSH:$NGUOI_SSH" /opt/songnhue
    fi
    chmod 755 /opt/songnhue

    chown -R "$APP_UID:$APP_GID" /opt/songnhue/keys /var/log/songnhue
    chmod 700 /opt/songnhue/keys
    find /opt/songnhue/keys -type f -exec chmod 600 {} + 2>/dev/null || true
    chmod 755 /var/log/songnhue

    # ⛔⛔ Thư mục sao lưu: chủ là POSTGRES (999), nhóm là app (1000), + setgid để tệp mới thừa kế
    #    nhóm. `chown -R 1000:1000` ở đây làm `pg_dump` hỏng ⇒ MỌI lượt deploy đỏ ngay bước đầu.
    chown -R "$PG_UID:$APP_GID" /var/lib/songnhue/backup
    chmod 2775 /var/lib/songnhue/backup
fi

kiem_quyen() { # đường dẫn · uid:gid mong đợi · mode mong đợi
    [ -e "$1" ] || { hong "$1 không tồn tại"; return; }
    that="$(stat -c '%u:%g %a' "$1")"
    if [ "$that" = "$2 $3" ]; then
        do_dac "$1" "$that"
    else
        hong "$1 → $that   (phải là: $2 $3)"
    fi
}
# ⛔ Đo bằng cách GHI THẬT, không bằng `stat`. `stat` chỉ nói thư mục ĐANG mang nhãn gì; nó không
#   trả lời được câu hỏi duy nhất có ý nghĩa ở đây — "người triển khai có ghi vào được không".
#   Ba dòng `kiem_quyen` bên dưới xanh trọn vẹn trong khi `rsync` thoát 23 (luật 1 · luật 9).
kiem_ghi_duoc() { # đường dẫn — thử ghi DƯỚI DANH NGHĨA người triển khai
    thu="$1/.hp-thu-ghi-$$"
    if [ "$(id -u)" -eq 0 ] && [ "$NGUOI_SSH" != "root" ]; then
        runuser -u "$NGUOI_SSH" -- touch "$thu" 2>/dev/null && ket=0 || ket=1
    else
        touch "$thu" 2>/dev/null && ket=0 || ket=1
    fi
    if [ "$ket" -eq 0 ]; then
        rm -f "$thu"
        do_dac "$1" "$NGUOI_SSH ghi được (đã thử ghi thật)"
    else
        hong "$1 → $NGUOI_SSH KHÔNG ghi được — rsync của CD sẽ thoát 23"
    fi
}
kiem_ghi_duoc /opt/songnhue
kiem_quyen /opt/songnhue/keys "$APP_UID:$APP_GID" 700
kiem_quyen /var/log/songnhue "$APP_UID:$APP_GID" 755
kiem_quyen /var/lib/songnhue/backup "$PG_UID:$APP_GID" 2775
# nginx chạy master bằng root nên không cần đổi chủ — nhưng thư mục vẫn phải CÓ phép đo:
# "được tạo mà không ai đo" chính là hình dạng đã làm đỏ rsync ngày 7/9 (HostPrepareQuyenTest).
kiem_quyen /var/log/nginx "0:0" 755

# ─────────────────────────────────────────────────────────────────────────────
buoc "4 · Docker"
if command -v docker >/dev/null 2>&1; then
    do_dac "docker" "$(docker --version)"
    # ⛔ CỐ Ý không gọi subcommand compose để hỏi phiên bản. `ScriptDockerLookupTest` cấm mọi script
    #   trong `deploy/` gọi nó, và luật ấy đúng: compose nội suy TOÀN BỘ tệp trước khi trả lời — kể
    #   cả với một lệnh chỉ đọc — mà `compose.prod.yml` đòi `${APP_IMAGE:?}` vốn chỉ tồn tại trong
    #   lượt triển khai (§10.48). Bộ canh khớp theo văn bản và **không có ngoại lệ nào**, cũng cố ý:
    #   một allow-list là chỗ mọi vi phạm tương lai được tha. Nên hỏi qua `docker info` — nó liệt kê
    #   plugin của client mà không đọc tệp compose nào.
    # ⚠ awk KHÔNG `exit` sớm: thoát giữa chừng làm lệnh bên trái ăn SIGPIPE, và dưới `pipefail` thì
    #   cả phép gán đỏ (đúng bẫy đã mắc ở dòng `rsync --version` phía trên).
    do_dac "compose plugin" "$(docker info 2>/dev/null |
        awk '/compose: Docker Compose/ {f = 1} f && /Version:/ && !v {v = $2} END {print (v ? v : "KHÔNG THẤY")}')"
else
    hong "docker chưa cài — xem §4.1 của docs/deploy-production-guideline.md"
fi
# ⚠ Workflow triển khai chạy `docker` KHÔNG qua sudo. User SSH không ở nhóm `docker` ⇒ mọi lượt đỏ.
NGUOI_SSH="${SUDO_USER:-$(id -un)}"
if id -nG "$NGUOI_SSH" 2>/dev/null | tr ' ' '\n' | grep -qx docker; then
    do_dac "$NGUOI_SSH ∈ nhóm docker" "có"
else
    hong "$NGUOI_SSH KHÔNG ở nhóm docker — deploy chạy docker không qua sudo nên sẽ đỏ"
fi
# ⛔ T11.36: `deploy.yml` KHÔNG đăng nhập hộ máy chủ. Gói GHCR riêng tư kể cả khi repo công khai.
if [ -s "${HOME_GOC:-/home/$NGUOI_SSH}/.docker/config.json" ] &&
    grep -q 'ghcr.io' "${HOME_GOC:-/home/$NGUOI_SSH}/.docker/config.json" 2>/dev/null; then
    do_dac "docker login ghcr.io" "đã đăng nhập"
else
    canh "CHƯA docker login ghcr.io (T11.36) — lượt 'compose pull' đầu tiên sẽ trả 'unauthorized'."
    canh "  echo <PAT có read:packages> | docker login ghcr.io -u <github-user> --password-stdin"
fi

# ─────────────────────────────────────────────────────────────────────────────
buoc "5 · SSH — chống tự cấm lượt deploy (§10.68-C)"
# Máy chủ bị quét liên tục. fail2ban `maxretry=3` cấm IP runner ngay lệnh đầu nếu có kết nối dò —
# đó là lý do CD ghim `known_hosts` thay vì `ssh-keyscan`. `PerSourceMaxStartups` giữ cho một nguồn
# không chiếm hết suất kết nối, để lượt deploy còn chỗ mà vào.
DROPIN=/etc/ssh/sshd_config.d/60-startups.conf
if [ "$CHI_KIEM" -eq 0 ] && [ ! -f "$DROPIN" ]; then
    mkdir -p /etc/ssh/sshd_config.d
    cat > "$DROPIN" <<'EOF'
MaxStartups 30:30:200
PerSourceMaxStartups 6
PerSourceNetBlockSize 32:128
ClientAliveInterval 120
ClientAliveCountMax 3
EOF
    # ⚠ `sshd -t` TRƯỚC khi reload: một tệp sai cú pháp + reload = mất đường vào máy.
    if sshd -t 2>/dev/null; then
        systemctl reload ssh 2>/dev/null || systemctl reload sshd 2>/dev/null || canh "không reload được sshd"
    else
        hong "sshd -t báo cấu hình sai — ĐÃ GỠ drop-in, không reload"
        rm -f "$DROPIN"
    fi
fi
[ -f "$DROPIN" ] && do_dac "$DROPIN" "có" || hong "$DROPIN chưa có"
# Đo thứ ĐÃ ĐƯỢC NẠP, không đọc lại tệp — tệp trên đĩa và tiến trình đang chạy là hai chuyện.
if command -v sshd >/dev/null 2>&1; then
    do_dac "PerSourceMaxStartups (đang chạy)" "$(sshd -T 2>/dev/null | awk '/persourcemaxstartups/{print $2}' || echo '?')"
    do_dac "clientaliveinterval (đang chạy)" "$(sshd -T 2>/dev/null | awk '/clientaliveinterval/{print $2}' || echo '?')"
fi
if systemctl is-active --quiet fail2ban 2>/dev/null; then
    do_dac "fail2ban" "active · $(fail2ban-client status sshd 2>/dev/null | awk -F'\t' '/Currently banned/{print $2}' || echo '?') IP đang bị cấm"
else
    hong "fail2ban KHÔNG chạy — xem §3.2"
fi

# ─────────────────────────────────────────────────────────────────────────────
buoc "6 · Tường lửa"
if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q 'Status: active'; then
    do_dac "ufw" "active"
    for cong in 22 80 443; do
        ufw status 2>/dev/null | grep -q "^$cong/tcp" && do_dac "  cổng $cong" "mở" || hong "cổng $cong CHƯA mở"
    done
else
    canh "ufw không chạy — kiểm tường lửa của nhà cung cấp thay vào đó"
fi

# ─────────────────────────────────────────────────────────────────────────────
buoc "7 · Những thứ script này CỐ Ý không làm"
canh "\`.env\` và \`keys/*.pem\` — §5. Chúng bị loại khỏi rsync nên KHÔNG ai điền hộ được."
canh "Cluster postgres — §6. \`POSTGRES_INITDB_ARGS\` chỉ có hiệu lực MỘT LẦN lúc initdb (§10.56)."
canh "DNS + TLS — §7. Bốn bản ghi A, ba chứng chỉ."

printf '\n'
if [ "$loi" -eq 0 ]; then
    echo "✓ Host sẵn sàng nhận lượt deploy — 0 mục sai."
else
    echo "✗ Còn $loi mục sai. Sửa xong chạy lại: sudo bash deploy/host-prepare.sh --kiem"
    exit 1
fi
