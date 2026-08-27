# Hướng dẫn triển khai — Staging & Production

> Đi kèm: `docs/cicd.md` (luồng và triết lý) · `hosting_recommendations.md` (mua gì, vì sao) ·
> `docs/runbook/` (xử lý khi có sự cố) · `deploy/env/prod.env.example` (danh sách biến đầy đủ)
>
> Tài liệu này để **làm theo từ đầu tới cuối**. Mỗi mục có phần kiểm chứng ở cuối; đừng sang mục
> tiếp theo khi phần kiểm chứng chưa xanh — sai ở mục 3 mà phát hiện ở mục 8 thì phải lần ngược
> qua năm mục.

---

## 0. Bức tranh tổng thể

```
                      ┌──────────────────── VPS-1 · PRODUCTION ────────────────────┐
  Internet ──TLS──►   │  nginx ──┬──► public-web:3000 ──/api/v1──►┐                │
                      │          ├──► admin-app:80   ──/api/──►   ├─► app:8080     │
                      │          └──► minio:9000  (presigned URL) │      │         │
                      │                                           │      ▼         │
                      │                              postgres:5432 ◄─────┘         │
                      └───────────────────────────────┬───────────────────────────-┘
                                                      │ SSH (chỉ ĐỌC)
                      ┌───────────────────────────────▼─── VPS-2 · STAGING ────────┐
                      │  cùng stack (nhỏ hơn)                                      │
                      │  + kho sao lưu kéo từ VPS-1  + Prometheus/Grafana          │
                      └───────────────────────────────┬────────────────────────────┘
                                                      │ age + rclone
                                            ┌─────────▼──────────┐
                                            │ Kho ngoài (B2/R2)  │  ← nhà cung cấp KHÁC
                                            └────────────────────┘
```

**Ba điều quyết định toàn bộ thiết kế này, đọc trước khi sửa bất cứ thứ gì:**

1. **Giao diện và API luôn cùng origin.** Mỗi image FE tự chuyển tiếp `/api` sang backend dưới
   chính tên miền của nó. Nhờ vậy backend **không có một dòng CORS nào** và cookie refresh giữ
   được `SameSite=Strict`. ⛔ Đừng cho nginx trỏ thẳng `/api` sang `app` — đó là cấu hình đã chặn
   toàn bộ giao diện quản trị suốt WS-8→WS-20.
2. **Máy chạy ứng dụng không giữ khoá ghi vào kho sao lưu.** VPS-2 **kéo** về, VPS-1 không đẩy đi.
   Chiếm được VPS-1 vẫn không xoá được bản sao lưu — đó là điều mã độc tống tiền làm đầu tiên.
3. **VPS-2 vừa là staging vừa là kho sao lưu và giám sát.** Kế hoạch cũ ghi 3 VM; VM-3 gộp vào đây.
   Giám sát nằm ngoài máy production nên nó **còn sống khi production chết** — đúng lúc cần nó.

---

## 1. Mua và chuẩn bị — làm xong hết trước khi gõ lệnh đầu tiên

| # | Việc | Ghi chú |
|---|---|---|
| 1.1 | **VPS-1**: 4 vCPU / 8 GB RAM / 160 GB SSD, Ubuntu 24.04 LTS, đặt tại Việt Nam | Lấy báo giá vài nơi — Viettel IDC, VNPT, FPT, BizFly, VNG. Chênh nhau nhiều, hay có giá trả trước theo năm |
| 1.2 | **VPS-2**: 2 vCPU / **8 GB** / 80 GB, cùng nhà cung cấp cũng được | ⚠ **Sửa 24/8: 4 GB → 8 GB.** VPS-2 chạy staging **và** giám sát **và** giữ kho sao lưu — cộng lại ≈ 5,4 GB. Phép cộng đầy đủ ở `hosting_recommendations.md` §8 |
| 1.3 | **Một** tên miền `.vn`, đăng ký **chủ thể là Công ty**, không phải cá nhân | Sáu địa chỉ của hệ thống = 1 tên miền mua + 5 tên miền phụ **miễn phí**. Chọn nhà đăng ký, hồ sơ chủ thể, và cái bẫy "nhà đăng ký đứng tên hộ": **`hosting_recommendations.md` §9**. ⚠ Đổi chủ thể `.vn` về sau là thủ tục hành chính thật, cần hồ sơ từ cả hai bên |
| 1.4 | **Email trung tính** (`it@songnhue.vn`…) để mở mọi tài khoản | ⛔ Không dùng Gmail cá nhân. Thẻ cá nhân hết hạn = production tắt và không ai ngoài anh xử lý được |
| 1.5 | **Tài khoản SMTP** gửi thư thật | Nhà cung cấp trong nước, hoặc Amazon SES / Postmark |
| 1.6 | **Kho lưu trữ ngoài** — Backblaze B2 hoặc Cloudflare R2 | Phải **khác nhà cung cấp** với hai VPS. Vài chục nghìn đồng/tháng |
| 1.7 | **Phụ lục xử lý dữ liệu cá nhân** ký với Công ty | Công ty = Bên Kiểm soát, anh = Bên Xử lý. Việc rẻ nhất, giá trị cao nhất |

> ⚠ Mục 1.7 không phải thủ tục cho có. Hiện anh đang giữ dữ liệu nhân sự của người khác mà không có
> văn bản nào định danh tư cách, và nghĩa vụ lập hồ sơ đánh giá tác động (NĐ 13/2023 Điều 25) áp
> dụng **kể cả khi đặt máy trong nước**. Có phụ lục thì nghĩa vụ đó về đúng chỗ là Công ty.

---

## 2. Dựng máy — làm **y hệt** cho cả VPS-1 và VPS-2

Khác nhau duy nhất là nội dung `.env`. Làm khác nhau ở tầng máy là làm cho staging mất giá trị.

### 2.1. Người dùng và SSH

```bash
# --- Trên máy của anh: sinh khoá riêng cho triển khai ---
ssh-keygen -t ed25519 -C "songnhue-deploy" -f ~/.ssh/songnhue_deploy

# --- Trên VPS, đăng nhập lần đầu bằng root ---
adduser --disabled-password --gecos "" songnhue
usermod -aG sudo songnhue
mkdir -p /home/songnhue/.ssh && chmod 700 /home/songnhue/.ssh
# Dán nội dung ~/.ssh/songnhue_deploy.pub vào:
nano /home/songnhue/.ssh/authorized_keys
chmod 600 /home/songnhue/.ssh/authorized_keys
chown -R songnhue:songnhue /home/songnhue/.ssh
```

Khoá đăng nhập bằng mật khẩu — `/etc/ssh/sshd_config`:

```
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
```

```bash
sshd -t && systemctl restart ssh
```

> ⚠ **Mở một phiên SSH thứ hai và đăng nhập được rồi mới đóng phiên đang dùng.** Gõ sai một dòng
> trong `sshd_config` và thoát ra là mất máy, và cách chữa là mở bảng điều khiển VNC của nhà cung
> cấp — nếu họ có.

### 2.2. Tường lửa

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

⛔ **Không mở 5432, 9000, 9001.** Postgres và MinIO không có một cổng nào chạm host — chúng chỉ tồn
tại trong mạng nội bộ của compose. Cần `psql` thì đi qua `docker compose exec`.

⛔ **Và trên VPS-2 cũng KHÔNG mở thêm cổng nào cho Grafana.** Bản trước của mục này bảo
`ufw allow from <IP> to any port 3000` — sai hai lần: `compose.observability.yml` publish Grafana ra
`127.0.0.1:13001`, nên (a) cổng không phải 3000, và (b) đã bind vào loopback thì mở tường lửa không
có tác dụng gì. Vào bằng SSH tunnel, không mở cổng:

```bash
ssh -N -L 13001:127.0.0.1:13001 songnhue@<VPS-2>    # rồi mở http://localhost:13001
```

Đây là cách đúng hơn hẳn: Grafana và Prometheus **không có lớp xác thực nào trước mặt** ở cấu hình
này, nên mở chúng ra mạng là mở toàn bộ chỉ số vận hành.

### 2.2-b. Siết SSH — ⛔ **cổng 22 đang bị quét liên tục, và nó làm ĐỎ lượt deploy**

Đo trên VPS-2 ngày 27/8, không phải suy đoán:

```
── kết nối đang mở ở cổng 22, theo IP ──
     32  79.108.163.24      ← một IP lạ, giữ 32 kết nối cùng lúc
      1  <máy dev>
```

Sáu lượt đếm cách nhau 5 giây: `28 → 1 → 1 → 33 → 33 → 33` kết nối · **67 tiến trình sshd**. Nó tới
theo đợt.

**Vì sao nó làm hỏng deploy.** `MaxStartups` mặc định là `10:30:100`: vượt **10** kết nối chưa xác
thực thì sshd **thả ngẫu nhiên 30%** kết nối mới. Tỉ lệ đo được:

| | |
|---|---|
| SSH cổng 22, giãn 4 giây | 7/10 đạt — **hỏng 30%** |
| HTTPS cổng 443, cùng máy cùng lúc | 5/5 |

**30% đúng bằng chữ số giữa của `10:30:100`.** CD Staging 27/8 đỏ ở bước *"Ghi lại bản đang chạy"* —
bước mở ba kết nối SSH liên tiếp — với `kex_exchange_identification: Connection reset by peer`.

⚠ Không ai vào được (`PasswordAuthentication no`, `PermitRootLogin no`, `who` = 0 phiên). Đây là vấn
đề **sẵn sàng phục vụ**, không phải xâm nhập. Nhưng nó vẫn là một cửa mở cho người lạ tiêu tài nguyên
của máy.

#### Làm gì

```bash
# 1 ─ fail2ban. ⚠ Đo 27/8: CHƯA CÀI trên VPS-2, không phải "đã cài mà tắt".
sudo apt-get update && sudo apt-get install -y fail2ban
sudo tee /etc/fail2ban/jail.local >/dev/null <<'EOF'
[sshd]
enabled  = true
mode     = aggressive
maxretry = 3
findtime = 10m
bantime  = 1h
EOF
sudo systemctl enable --now fail2ban
sudo fail2ban-client status sshd

# 2 ─ Không cho MỘT nguồn chiếm hết suất kết nối (OpenSSH ≥ 8.5; máy đang chạy 9.6)
sudo tee /etc/ssh/sshd_config.d/60-startups.conf >/dev/null <<'EOF'
MaxStartups 30:30:200
PerSourceMaxStartups 6
PerSourceNetBlockSize 32:128
ClientAliveInterval 120
ClientAliveCountMax 3
EOF
sudo sshd -t && sudo systemctl reload ssh
```

⛔ **Giữ nguyên dấu `&&`.** Reload một cấu hình sai là tự khoá mình khỏi máy chủ, và phiên SSH đang
mở là thứ duy nhất còn lại để cứu. `sshd -t` phải xanh trước.

⚠ `ClientAliveInterval` mặc định là `0` — phiên chết **không bao giờ** được dọn. Đo được lúc sự cố:
33 kết nối "đã xác thực" còn treo trong khi `who` trả về **0** phiên đăng nhập.

#### Phía đường ống đã làm gì

`deploy.yml` nay ghép kênh SSH (`ControlMaster`/`ControlPath`/`ControlPersist`): cả lượt deploy dùng
**một** kết nối thay vì ~10, và lượt bắt tay đầu tiên có thử lại 6 lần giãn cách tăng dần.

⛔ **Đó là giảm mặt tiếp xúc, không phải bản vá gốc.** Chưa làm hai bước ở trên thì máy chủ vẫn đang
bị quét, và một lượt deploy vẫn có thể đỏ vì lý do chẳng liên quan gì tới mã.

#### Kiểm chứng sau khi làm

```bash
# tỉ lệ huồng phải về 0
ok=0; for i in $(seq 1 10); do ssh -o BatchMode=yes -o ConnectTimeout=8 <user>@<host> true 2>/dev/null && ok=$((ok+1)); sleep 2; done; echo "$ok/10"
sudo fail2ban-client status sshd     # Currently banned phải > 0 sau ít giờ
```

### 2.3. Docker

```bash
curl -fsSL https://get.docker.com | sh
usermod -aG docker songnhue
systemctl enable --now docker
```

### 2.4. Tự vá bảo mật — làm một lần, chạy mãi

```bash
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades
```

Đây là phần lớn cái mà PaaS bán cho anh, lấy về với giá hai dòng lệnh.

### 2.5. Cây thư mục — ⛔ **chủ sở hữu KHÔNG phải user SSH**

⚠⚠ Bản trước của mục này ghi `chown -R songnhue:songnhue …` cho cả ba đường dẫn. **Sai, và sai
theo kiểu làm mọi lượt deploy đỏ.** Đã trả giá trên staging ngày 25/8; ghi lại ở đây vì đây là tệp
người dựng VPS-1 sẽ làm theo.

Ba danh tính khác nhau cùng dùng cây thư mục này, và **không cái nào là user SSH**:

| Danh tính | Là ai | Đụng vào gì |
|---|---|---|
| `1000` | user trong image `app` (ghim ở `backend.Dockerfile`) | đọc khoá, ghi log |
| `999` | user `postgres` **bên trong container** | `pre-deploy-dump.sh` chạy `pg_dump` ở đó, ghi thẳng vào thư mục sao lưu |
| user SSH | người vận hành trên host | sửa `.env`, dọn bản sao lưu cũ |

⛔ **`chown` trong Dockerfile không có tác dụng với bind mount** — host che hoàn toàn thứ image dựng
sẵn. Nên quyền phải đặt **trên máy chủ**, không đặt trong image.

```bash
mkdir -p /opt/songnhue/keys /var/lib/songnhue/backup /var/log/songnhue /var/log/nginx

# ⚠ ĐO uid/gid của image, đừng ghi cứng 1000. Trước khi ghim, id thật là 100:101 —
#   không phải 1000 như ai cũng tưởng khi đọc lướt `adduser -S`.
docker run --rm --entrypoint id ghcr.io/<owner>/songnhue/app:dev

APP_UID=1000; APP_GID=1000; PG_UID=999
getent group "$APP_GID" || groupadd -g "$APP_GID" songnhue-app
usermod -aG "$APP_GID" "$(id -un)"          # user SSH vào chung nhóm

chown -R "$APP_UID:$APP_GID" /opt/songnhue/keys /var/log/songnhue
chmod 700 /opt/songnhue/keys && chmod 600 /opt/songnhue/keys/* 2>/dev/null
chmod 755 /var/log/songnhue

# ⚠⚠ Thư mục sao lưu: chủ là POSTGRES (999), nhóm là app (1000), + setgid.
#    `chown -R 1000:1000` ở đây làm `pg_dump` hỏng — mà bước chụp trước triển khai
#    nay chạy ở MỌI lượt deploy, nên hậu quả là mọi lượt deploy đỏ ngay bước đầu.
chown -R "$PG_UID:$APP_GID" /var/lib/songnhue/backup
chmod 2775 /var/lib/songnhue/backup
```

> 📌 Đây vẫn là việc gõ tay, tức một thứ phải nhớ. Gói thành `deploy/host-prepare.sh` — **T11.35**,
> chưa làm. Và nhóm hiện đang mượn gid 1000 của user `ubuntu` có sẵn; script phải tạo nhóm riêng
> rồi **đọc uid/gid từ image** thay vì ghi cứng.

### 2.6. `docker login ghcr.io` — làm trước, không thì `compose up` dừng ngay

```bash
docker login ghcr.io -u <github-username> -p <PAT có quyền read:packages>
```

Chưa đăng nhập thì lệnh kéo image đầu tiên trả `unauthorized`, kể cả khi repo là public. Đây đang là
thao tác tay — **T11.36** đề xuất bỏ nó đi.

### 2.7. Collation của cluster — ⛔ **chốt được đúng MỘT lần, lúc `initdb` chạy**

`POSTGRES_INITDB_ARGS` trong `compose.prod.yml` chỉ có tác dụng ở lượt dựng volume **đầu tiên**. Sau
đó image bỏ qua nó vĩnh viễn. Nên tệp cấu hình và cluster thật có thể nói hai điều khác nhau mãi mãi
mà không lệnh nào báo sai.

Đo ngày 26/8 trên chính `postgis/postgis:16-3.4` — đúng phải là `Anh < Dung < Đăng < Em`:

| cluster dựng với | `ORDER BY` cho ra |
|---|---|
| ICU `vi-VN` | `Anh < Dung < Đăng < Em` ✅ |
| mặc định của image (glibc `en_US.utf8`) | `Anh < Đăng < Dung < Em` |
| locale `C` (so theo byte) | `Anh < Dung < Em < Đăng` |

**Hỏi cluster đang chạy, đừng đọc tệp compose:**

```bash
cd /opt/songnhue && ./postgres/kiem-collation.sh
```

Lệnh này chạy tự động ở smoke test câu **[1/4]** của mọi lượt triển khai. Đạt thì in
`i | collate=C.UTF-8 | icu=vi-VN`.

#### Sai rồi thì sửa thế nào

⛔ **Không sửa được bằng cách thêm dòng vào compose rồi deploy lại.** Đo được: vá tệp rồi
`up -d --force-recreate` vẫn cho ra đúng collation cũ. `ALTER DATABASE` cũng không đổi được collation
của một cluster đã có dữ liệu.

Đường duy nhất — **có gián đoạn**, làm ngoài giờ:

```bash
cd /opt/songnhue
dc="docker compose --env-file .env -f compose.prod.yml"

$dc stop app admin-app public-web            # ngừng ghi trước khi chụp
$dc exec -T postgres pg_dumpall -U postgres > /var/lib/songnhue/backup/truoc-doi-collation.sql
ls -l /var/lib/songnhue/backup/truoc-doi-collation.sql   # ⚠ 0 byte là DỪNG, đừng đi tiếp

$dc down                                     # KHÔNG `-v`: xoá có chọn lọc ở dòng dưới
docker volume rm songnhue_postgres-data      # ⛔ điểm không quay lui được

$dc up -d postgres                           # dựng lại — lần này initdb đọc POSTGRES_INITDB_ARGS
sleep 30
$dc exec -T postgres psql -U postgres -d postgres -f /dev/stdin \
    < /var/lib/songnhue/backup/truoc-doi-collation.sql

./postgres/kiem-collation.sh   # phải in ✓ trước khi bật app trở lại
$dc up -d
```

Quy trình này đã được **chạy thật** ngày 26/8 trên một cluster sai: dữ liệu giữ nguyên, thứ tự
`Anh < Đăng < Dung < Em` đổi thành `Anh < Dung < Đăng < Em`.

⚠ **CSDL staging hiện tại đang sai** — nó được dựng ngày 25/8, trước khi `compose.prod.yml` có dòng
ấy. Nó phải đi qua đúng quy trình trên, và đó cũng là lượt diễn tập cho khoản **DOD0.21** (quay lui)
lẫn **T7.13** (khôi phục thật).

### ✅ Kiểm chứng mục 2

```bash
ssh -i ~/.ssh/songnhue_deploy songnhue@<HOST> 'docker run --rm hello-world && echo OK'
ssh -i ~/.ssh/songnhue_deploy root@<HOST> 2>&1 | grep -q "Permission denied" && echo "✓ root đã khoá"
```

---

## 3. Khoá và tệp `.env`

### 3.1. Sinh khoá — **trên máy chủ**, không sinh ở máy cá nhân rồi chép qua

```bash
cd /opt/songnhue/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
chmod 600 jwt-private.pem
openssl rand -base64 32          # ← chép giá trị này vào AES_KEY_V1
```

> ⛔ **Khoá của staging và production PHẢI khác nhau.** Dùng chung nghĩa là token cấp ở staging mở
> được production, và một bản dump staging (thường được xử lý lỏng tay hơn) trở thành đường vào dữ
> liệu thật.
>
> ⛔ **Sinh lại `jwt-private.pem` là vô hiệu mọi phiên đang sống.** Chỉ làm khi xoay khoá có kế
> hoạch — `docs/runbook/xoay-khoa.md`.

### 3.2. Điền `.env`

Chép tệp mẫu **của đúng môi trường** thành `/opt/songnhue/.env`, điền hết:

| Môi trường | Chép từ |
|---|---|
| Staging (VPS-2) | `deploy/env/staging.env.example` |
| Production (VPS-1) | `deploy/env/prod.env.example` |

> ✅ **Sửa 24/8**: sáu biến `PUBLIC_DOMAIN` · `ADMIN_DOMAIN` · `FILES_DOMAIN` · `ROBOTS_TAG` ·
> `MINIO_ROOT_USER` · `MINIO_ROOT_PASSWORD` · `REVALIDATE_SECRET` trước đây **không có trong tệp
> mẫu nào** — mục này liệt kê tay 6/7 và bỏ sót `REVALIDATE_SECRET`. Nay cả bảy đã nằm trong cả hai
> tệp mẫu, và `ComposeEnvCompletenessTest` canh cho việc "compose thêm biến mà mẫu quên".
>
> ⚠ Vì sao chuyện đó im lặng được: compose viết chúng ở dạng `${TÊN}` **không có `:?`**, nên thiếu
> không phải là lỗi khởi động — nó thay bằng **chuỗi rỗng** rồi chạy tiếp. `server_name` rỗng, MinIO
> chạy tài khoản mặc định, `/api/revalidate` trả 503. Không dòng log nào nói ra.

⛔⛔ **Ba biến dễ điền sai nhất, mỗi cái hỏng theo một kiểu im lặng:**

| Biến | Sai thế nào | Triệu chứng |
|---|---|---|
| `ROBOTS_TAG` ở staging | quên → compose lấy mặc định **`all`** | **Google đánh chỉ mục staging.** Và vì hai môi trường chạy cùng image, sitemap staging còn mang địa chỉ production. Staging phải là `noindex, nofollow` |
| `REVALIDATE_SECRET` | lệch giữa `app` và `public-web` | `/api/revalidate` trả 401, cổng **đứng yên ở nội dung cũ** sau mỗi lần duyệt bài. Compose lấy cùng một giá trị từ `.env` nên chỉ cần điền một chỗ |
| `MINIO_ROOT_*` | để trống | MinIO khởi động bằng tài khoản mặc định — kho tệp nhân sự mở bằng mật khẩu ai cũng biết |

```bash
chmod 600 /opt/songnhue/.env
```

> ⚠⚠ **`MINIO_ENDPOINT` KHÔNG được điền `http://minio:9000`**, dù trực giác bảo thế cho nhanh.
> `AttachmentService` trả *presigned URL* để trình duyệt tải thẳng từ MinIO, và chữ ký AWS SigV4 ký
> **cả tên máy**. Ký bằng `minio:9000` thì trình duyệt không phân giải nổi tên đó. Triệu chứng:
> tải lên chạy tốt, **mọi nút Tải về đều hỏng**. `compose.prod.yml` đã đặt sẵn giá trị đúng
> (`https://${FILES_DOMAIN}`) — đừng ghi đè nó trong `.env`.

Ba biến trong mẫu cần chú ý riêng:

| Biến | Lưu ý |
|---|---|
| `BOOTSTRAP_ADMIN_PASSWORD` | Chỉ điền ở lần dựng đầu tiên. **Đăng nhập xong thì XOÁ khỏi `.env` và khởi động lại `app`** |
| `DB_RESTORE_PASSWORD` | Để **trống** ở production. Điền vào là tiến trình ứng dụng giữ mật khẩu chủ sở hữu lược đồ; khôi phục đi bằng `docs/runbook/khoi-phuc-du-lieu.md` |
| `FLYWAY_ENABLED` | Phải là `false`. Migration là việc của service `migrator` riêng |

### ✅ Kiểm chứng mục 3 — **fail-fast có thật không**

```bash
cd /opt/songnhue
cp .env .env.bak && sed -i 's/^AES_KEY_V1=.*/AES_KEY_V1=/' .env
docker compose --env-file .env -f compose.prod.yml run --rm app 2>&1 | grep -i "AES_KEY_V1"
mv .env.bak .env
```

Phải thấy dòng báo thiếu `AES_KEY_V1` và tiến trình **không khởi động**. Nếu nó lên bình thường
thì fail-fast không hoạt động, và một biến điền sai sẽ chỉ lộ ra vào lúc có người dùng thật.

---

## 4. DNS và TLS

### 4.1. Bản ghi DNS

| Tên | Kiểu | Trỏ tới |
|---|---|---|
| `songnhue.vn` · `www` · `admin` · `files` | A | IP của **VPS-1** |
| `staging` · `admin-staging` · `files-staging` | A | IP của **VPS-2** |

Nếu dùng Cloudflare: **cổng công khai bật proxy** (cam) để có cache và chống ngập; **`admin` và
`files` để DNS-only** (xám). Không cho phiên quản trị và tệp nhân sự đi vòng qua hạ tầng nước
ngoài — vốn là điều đang cố tránh khi chọn đặt máy trong nước.

```bash
dig +short admin.songnhue.vn      # phải ra đúng IP VPS-1 trước khi sang 4.2
```

### 4.2. Cấp chứng chỉ lần đầu

Certbot cần cổng 80 trả lời được, mà nginx thì cần chứng chỉ mới khởi động — vòng tròn. Cắt bằng
cách chạy certbot **độc lập** một lần:

```bash
cd /opt/songnhue
docker run --rm -p 80:80 \
  -v /etc/letsencrypt:/etc/letsencrypt \
  certbot/certbot certonly --standalone --non-interactive --agree-tos \
  -m it@songnhue.vn \
  -d songnhue.vn -d www.songnhue.vn
docker run --rm -p 80:80 -v /etc/letsencrypt:/etc/letsencrypt \
  certbot/certbot certonly --standalone --non-interactive --agree-tos \
  -m it@songnhue.vn -d admin.songnhue.vn
docker run --rm -p 80:80 -v /etc/letsencrypt:/etc/letsencrypt \
  certbot/certbot certonly --standalone --non-interactive --agree-tos \
  -m it@songnhue.vn -d files.songnhue.vn
```

> ⚠ **Ba chứng chỉ riêng, không gộp một.** `default.conf.template` trỏ vào
> `/etc/letsencrypt/live/<từng-tên-miền>/`. Gộp `-d` vào một lệnh thì Let's Encrypt chỉ tạo **một**
> thư mục mang tên miền đầu, và hai server block còn lại chết với `cannot load certificate`.
> (`www` gộp chung với tên gốc là đúng — chúng ở cùng một server block.)

### 4.3. Gia hạn tự động

Sau khi stack đã chạy, dùng webroot để không phải dừng nginx:

```bash
crontab -e
```
```cron
17 3 * * 1 cd /opt/songnhue && docker compose --env-file .env -f compose.prod.yml --profile certbot run --rm certbot renew --webroot -w /var/www/certbot --quiet && docker compose --env-file .env -f compose.prod.yml exec nginx nginx -s reload
```

> ⚠ Khối `location ^~ /.well-known/acme-challenge/` phải đứng **trước** lệnh chuyển hướng sang
> HTTPS trong `default.conf.template` — nó đã đứng đúng chỗ. Đảo thứ tự là certbot bị đẩy sang
> HTTPS và **không gia hạn được**, hỏng âm thầm cho tới đúng ngày hết hạn.

**Kiểm chứng ngay, đừng đợi 60 ngày nữa mới biết:**

```bash
docker compose --env-file .env -f compose.prod.yml --profile certbot \
  run --rm certbot renew --webroot -w /var/www/certbot --dry-run
```

### 4.4. Kiểm cấu hình nginx trước khi bật

```bash
cd /opt/songnhue
docker compose --env-file .env -f compose.prod.yml run --rm nginx nginx -t
```

> ⚠ Phải để lệnh bắt đầu bằng `nginx`. Chạy `sh -c 'nginx -t'` thì entrypoint của image **không
> chạy**, `envsubst` không thay biến, và `nginx -t` sẽ kiểm tệp mặc định của image chứ không phải
> tệp của ta — báo xanh trên một thứ không liên quan. (Đã mắc đúng lỗi này khi soạn tài liệu.)

---

## 5. Dựng Staging lần đầu — bằng tay, đúng một lần

Làm tay lần đầu để thấy từng bước; từ lượt sau CI làm hộ.

```bash
# Trên MÁY CỦA ANH: đẩy cấu hình lên (đúng thứ CI sẽ làm)
rsync -az --exclude '.env' --exclude 'env/' --exclude 'keys/' \
      --exclude 'compose.local.yml' --exclude 'compose.infra.yml' \
      deploy/ songnhue@<VPS2>:/opt/songnhue/

# Trên VPS-2
cd /opt/songnhue
chmod +x backup/*.sh

export APP_IMAGE=ghcr.io/<owner>/songnhue/app:dev
export ADMIN_IMAGE=ghcr.io/<owner>/songnhue/admin-app:dev
export PUBLIC_IMAGE=ghcr.io/<owner>/songnhue/public-web:dev
echo <GITHUB_PAT> | docker login ghcr.io -u <user> --password-stdin

docker compose --env-file .env -f compose.staging.yml pull
docker compose --env-file .env -f compose.staging.yml up -d postgres minio
docker compose --env-file .env -f compose.staging.yml run --rm minio-init
docker compose --env-file .env -f compose.staging.yml run --rm migrator   # ← phải thoát mã 0
docker compose --env-file .env -f compose.staging.yml up -d app admin-app public-web nginx
```

> ⚠ Chỉ dùng tag `:dev` ở lần dựng tay này. **Từ lượt sau luôn là tag SHA** — tag di động nghĩa là
> không biết chắc mình vừa đưa cái gì lên.

### ✅ Kiểm chứng mục 5 — sáu phép, làm đủ

```bash
# 1. Đi hết chặng nginx → public-web → app → postgres
#    ⚠ Sửa 24/8: bản cũ hỏi `/actuator/health/readiness` qua tên miền công khai —
#    đường đó KHÔNG đi tới đâu. nginx biên chỉ định tuyến `/api/` và `/` sang hai
#    image giao diện, không khối location nào chuyển `/actuator` sang `app`.
#    Đo thật: public-web trả 404, admin-app trả 200 kèm trang HTML của SPA.
curl -fsS https://staging.songnhue.vn/api/v1/public/site-config | head -c 120
#    → phải thấy '"success":true'

# 2. ⚠ PHẢI có Origin. curl trần không preflight nên nó đi lọt qua đúng bức tường
#    chặn người dùng thật — CORS đã chặn cả giao diện quản trị suốt WS-8→WS-20.
curl -si -X OPTIONS https://admin-staging.songnhue.vn/api/v1/auth/login \
     -H "Origin: https://admin-staging.songnhue.vn" \
     -H "Access-Control-Request-Method: POST" | head -1

# 3. Header bảo mật có mặt
curl -sI https://admin-staging.songnhue.vn/ | grep -iE "strict-transport|content-security|x-frame|x-robots"

# 4. Staging KHÔNG được đánh chỉ mục
curl -sI https://staging.songnhue.vn/ | grep -i x-robots-tag    # → noindex, nofollow

# 5. Gõ thẳng IP phải bị từ chối ở tầng TLS
curl -sk https://<IP-VPS2>/ -o /dev/null -w '%{http_code}\n'    # → 000 (đóng kết nối)

# 6. Đăng nhập được bằng superadmin, và TẢI VỀ được một tệp đính kèm
#    (phép 6 là phép duy nhất chứng minh MINIO_ENDPOINT đúng)
```

> Phép 6 không bỏ được. Năm phép đầu vẫn xanh trọn vẹn khi `MINIO_ENDPOINT` sai, vì lỗi chỉ xuất
> hiện ở đường tải tệp về.

---

## 6. Nối GitHub

**Settings → Secrets and variables → Actions**

| Loại | Tên | Đặt ở | Giá trị |
|---|---|---|---|
| Secret | `STAGING_HOST` · `STAGING_USER` · `STAGING_SSH_KEY` · `STAGING_BASE_URL` | environment `staging` | IP VPS-2 · `songnhue` · nội dung `~/.ssh/songnhue_deploy` · `https://staging.songnhue.vn` |
| Secret | `PROD_HOST` · `PROD_USER` · `PROD_SSH_KEY` · `PROD_BASE_URL` | environment `production` | như trên, cho VPS-1 |
| Secret | `NVD_API_KEY` | **repo** | ✅ đã đặt 18/8 |
| **Variable** | `PUBLIC_SITE_URL` | **repo** | `https://songnhue.vn` |

> ⚠ **Đặt đúng cấp, không chỉ đúng tên.** Secret của environment chỉ đến được job có khai
> `environment:`. Lần đầu `NVD_API_KEY` bị đặt vào environment `staging` và phép quét im lặng bỏ
> qua — `secrets.NVD_API_KEY` giải ra chuỗi rỗng, **không có lỗi nào**.

Environment `production` phải có **required reviewer** — đó là chỗ lượt deploy dừng chờ người bấm.

---

## 7. Từ đây trở đi

| Việc | Cách làm |
|---|---|
| Đưa mã lên staging | Mở PR `dev → staging`, **merge commit** (không squash), CD tự chạy |
| Đưa lên production | Actions → **CD Production** → nhập SHA + lý do → bấm duyệt |
| Đổi cấu hình nginx / compose | Sửa trong repo, đi theo đúng luồng trên. **Đừng sửa tay trên máy chủ** — lượt deploy sau `rsync --delete` sẽ xoá mất |
| Đổi tham số nghiệp vụ | Màn hình **Cấu hình hệ thống**, không sửa `.env` |
| Đổi bí mật | Sửa `/opt/songnhue/.env` rồi `up -d --force-recreate app` |

`.env` và `keys/` là hai thứ **duy nhất** chỉ tồn tại trên máy chủ. Cả hai đều được loại khỏi
`rsync`, đã kiểm chứng là `--delete` không chạm tới chúng.

---

## 8. Production

Giống hệt mục 5, đổi `compose.staging.yml` → `compose.prod.yml` và `ROBOTS_TAG=all`. Ba việc thêm:

1. **Đăng nhập `superadmin`, đổi mật khẩu, bật 2FA. Rồi xoá `BOOTSTRAP_ADMIN_PASSWORD` khỏi `.env`**
   và `up -d --force-recreate app`.
2. **Bật lịch sao lưu** ở màn hình Cấu hình hệ thống (`backup.schedule-enabled`), rồi mục 9.
3. **Diễn tập khôi phục một lần** — mục 12.

---

## 9. Sao lưu — ba bản, mỗi bản trả lời một câu hỏi khác nhau

| Bản | Ở đâu | Trả lời câu hỏi |
|---|---|---|
| Đêm 02:00 | VPS-1, `/var/lib/songnhue/backup` | "Xoá nhầm bảng lúc chiều" |
| Kéo về 03:00 | VPS-2, `/srv/songnhue-backups` | "VPS-1 chết / đĩa hỏng" |
| Đẩy ra 03:30 | B2/R2, đã mã hoá | "Mất tài khoản nhà cung cấp" |

Cộng thêm bản `predeploy-*` sinh tự động trước mỗi lượt deploy production — **điểm quay lui duy
nhất** khi migration làm hỏng dữ liệu, vì hệ này không có PITR.

### 9.1. Kéo về VPS-2

Tạo trên **VPS-1** một tài khoản chỉ đọc kho sao lưu, rồi trên **VPS-2**:

```cron
0 3 * * * PROD_HOST=<IP-VPS1> /opt/songnhue/backup/pull-from-prod.sh >> /var/log/songnhue-pull.log 2>&1
```

### 9.2. Đẩy ra ngoài nhà cung cấp

```bash
# Trên máy CÁ NHÂN (offline), sinh cặp khoá age:
age-keygen -o songnhue-backup.key       # ⛔ khoá RIÊNG cất offline, KHÔNG lên VPS nào

# Trên VPS-2:
apt install -y age rclone && rclone config       # thêm remote `b2`
cat > /etc/songnhue-offsite.env <<'EOF'
OFFSITE_RCLONE_REMOTE=b2:songnhue-dr
OFFSITE_AGE_RECIPIENT=age1...            # ← khoá CÔNG khai
OFFSITE_MINIO_REMOTE=prod-minio          # remote rclone trỏ tới MinIO của VPS-1, quyền CHỈ ĐỌC
EOF
```
```cron
30 3 * * * set -a; . /etc/songnhue-offsite.env; set +a; /opt/songnhue/backup/push-offsite.sh >> /var/log/songnhue-offsite.log 2>&1
```

> ⚠⚠ **Khoá riêng age tuyệt đối không nằm trên VPS-2.** Để cả hai trên cùng máy thì mã hoá chỉ còn
> là một bước tốn thời gian. Và **cất bản sao khoá riêng ở nơi thứ hai** — mất khoá là mất luôn
> toàn bộ kho sao lưu ngoài, không có đường nào lấy lại.
>
> ⚠ `OFFSITE_MINIO_REMOTE` là mắt xích hay bị bỏ quên nhất: sao lưu CSDL **không** bao gồm tệp.
> Thiếu nó thì khôi phục xong sẽ được một hệ thống đầy đủ bản ghi mà mọi đường tải về đều 404.

### 9.3. ⚠ Ba khoản nợ kỹ thuật đã biết — ghi ra để không ai tưởng đã xong

| # | Nợ | Hiện đang chữa thế nào | Cách chữa gốc |
|---|---|---|---|
| 1 | `app.storage` chỉ có **một** `endpoint`, dùng chung cho cả lượt gọi nội bộ lẫn lượt ký presigned URL | Trỏ về tên miền công khai, và cho service `nginx` một bí danh mạng để app không phải đi vòng ra Internet | Tách `app.storage.public-endpoint`, dùng hai `MinioClient` |
| 2 | `NEXT_PUBLIC_SITE_URL` nướng vào bundle lúc build → staging mang URL production | `X-Robots-Tag: noindex` ở nginx biên của staging | Cho `SITE_URL` đọc lúc chạy — `lib/site.ts` chỉ được tệp phía máy chủ dùng |
| 3 | `system_backups.trigger_type` chưa có giá trị `PRE_DEPLOY` | Bản chụp trước deploy ghi `MANUAL`, phân biệt bằng tiền tố tên tệp | Migration bốn dòng, gộp vào lần sửa lược đồ kế tiếp |

---

## 10. Giám sát — trên VPS-2, để nó sống sót khi VPS-1 chết

```bash
cd /opt/songnhue
docker compose -f compose.observability.yml up -d
```

Cộng thêm hai thứ ngoài hệ thống, cả hai đều miễn phí:

* **Ping từ bên ngoài** (UptimeRobot / Better Stack) tới `https://songnhue.vn/healthz` — trả lời
  câu "cả máy có chết không", mà Prometheus đặt trên VPS-2 không trả lời được nếu mạng đứt.
* **Cảnh báo `backup_last_success > 26h`** gửi email. `deploy/observability/alerts.yml` đã có.

> Ba thứ **phải** có cảnh báo, không thêm gì nữa cho tới khi thấy thiếu: ứng dụng chết · sao lưu
> chết · poller thuỷ văn chết (Phase 2 — nguồn không có API lịch sử, mất là mất vĩnh viễn).

---

## 11. Quay lui

| Tình huống | Cách |
|---|---|
| Mã mới hỏng, **lược đồ không đổi** | Chạy lại **CD Production** với SHA của lần trước. ~3 phút |
| Migration làm hỏng dữ liệu | Khôi phục từ bản `predeploy-*` sinh ở đầu lượt deploy — `docs/runbook/khoi-phuc-du-lieu.md` |
| Máy chủ chết hẳn | Dựng VPS mới theo mục 2–4, khôi phục từ bản trên VPS-2. Đây là con số RTO ≤ 4h |

> ⚠ Mỗi migration đổi lược đồ **phải kèm ghi chú quay lui trong PR**. Không có PITR nghĩa là câu
> "quay lui thế nào" phải được trả lời **trước** khi merge, không phải lúc đang hỏng.

---

## 12. Checklist nghiệm thu trước go-live

| # | Việc | Xong |
|---|---|:-:|
| 1 | Thiếu một biến bắt buộc → app **không khởi động**, log chỉ đúng tên biến | ☐ |
| 2 | Migration chạy ở service `migrator` riêng, thoát mã 0 trước khi `app` lên | ☐ |
| 3 | Đăng nhập → bắt đổi mật khẩu → bật 2FA chạy hết luồng | ☐ |
| 4 | Preflight `OPTIONS` **có `Origin`** trả 200, không phải 403 | ☐ |
| 5 | Header bảo mật đủ trên **cả hai** tên miền (không chỉ một) | ☐ |
| 6 | **Tải về một tệp đính kèm thành công** (chứng minh `MINIO_ENDPOINT` đúng) | ☐ |
| 7 | Gõ thẳng IP → TLS từ chối bắt tay | ☐ |
| 8 | Staging trả `X-Robots-Tag: noindex`, production trả `all` | ☐ |
| 9 | `certbot renew --dry-run` xanh | ☐ |
| 10 | **`make backup` sinh ra một tệp THẬT**, checksum khớp | ☐ |
| 11 | Bản dump **không chứa** khoá AES/JWT (`verify-no-keys.sh`) | ☐ |
| 12 | Bản sao đã có mặt trên VPS-2 **và** trên kho ngoài | ☐ |
| 13 | **Diễn tập khôi phục thật** lên VPS-2, đối chiếu số bản ghi, **ghi con số RTO thật vào runbook** | ☐ |
| 14 | Dừng job sao lưu > 26h → cảnh báo bắn tới email thật | ☐ |
| 15 | `BOOTSTRAP_ADMIN_PASSWORD` đã **xoá** khỏi `.env` | ☐ |
| 16 | Ping ngoài đã dựng và đã thử bằng cách tắt nginx | ☐ |
| 17 | Đã quay lui thử một lần ở staging | ☐ |
| 18 | **Quyền ba thư mục đúng như §2.5** — `stat -c '%u:%g %a' /var/lib/songnhue/backup` phải ra `999:1000 2775`. Sai ô này thì mọi lượt deploy đỏ ở bước chụp trước triển khai | ☐ |
| 19 | `docker login ghcr.io` đã chạy trên máy chủ (§2.6) | ☐ |
| 20 | **`./postgres/kiem-collation.sh` in ✓** (§2.7). ⛔ Ô này chỉ ký được TRƯỚC khi có dữ liệu thật — sau đó sửa là dump + dựng lại cluster | ☐ |

> **Mục 10 và 13 là hai mục không được ký khống.** Dự án này đã có 4 ngày sao lưu chạy mà **không
> sinh ra một tệp nào**, trong khi `BackupServiceTest` vẫn xanh trọn vẹn — vì nó mock đúng chỗ mã
> chạm ra ngoài. Chưa khôi phục thật thì chưa có sao lưu, chỉ có tệp.
