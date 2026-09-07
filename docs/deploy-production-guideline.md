# Hướng dẫn triển khai PRODUCTION — từ lúc chưa có máy tới lúc CI/CD chạy đều

> **Tài liệu này để làm theo từ đầu tới cuối, một lần, cho VPS-1.**
> Đi kèm: `docs/deploy-guideline.md` (bản chung cho cả hai môi trường) · `docs/cicd.md` (luồng và
> triết lý) · `hosting_recommendations.md` (mua gì, vì sao) · `docs/branch-protection.md` (bảo vệ
> nhánh) · `docs/runbook/` (khi có sự cố) · `deploy/env/prod.env.example` (danh sách biến gốc).
>
> Mỗi mục có phần **kiểm chứng** ở cuối. **Đừng sang mục sau khi phần kiểm chứng chưa xanh** — sai ở
> mục 5 mà phát hiện ở mục 11 thì phải lần ngược qua sáu mục, và một trong số đó (collation ở §6)
> **không sửa được** sau khi có dữ liệu thật.

---

## Trạng thái đo được — 4/9/2026

⛔ **Toàn bộ tài liệu này chưa từng được chạy hết một lượt.** VPS-1 chưa tồn tại. Dưới đây là những
gì **đo được**, không phải những gì sổ ghi:

| Đo bằng | Kết quả |
|---|---|
| `gh api repos/team-dev-qnt/songnhue/environments` | environment `production` **có** · ⭐ **đo lại 6/9/2026**: `protection_rules` chỉ còn `branch_policy` — **không còn required reviewer**; `deployment_branch_policy` = custom, đúng một nhánh `production` (T11.86) |
| `gh api .../environments/production/secrets` | **`total_count: 0`** — chưa có `PROD_HOST`, `PROD_USER`, `PROD_SSH_KEY`, `PROD_BASE_URL`, `PROD_SSH_KNOWN_HOSTS` |
| `gh api .../environments/staging/secrets` | **6** — `STAGING_*` đủ 5, cộng một `PUBLIC_SITE_URL` **đặt sai loại** (phải là repository *variable*), không workflow nào đọc |
| `gh api .../actions/secrets` | **1** — `NVD_API_KEY` (cấp repo, đúng chỗ) |
| `gh api .../actions/variables` | **rỗng** → `PUBLIC_SITE_URL` chưa đặt ⇒ image `public-web` đang mang `NEXT_PUBLIC_SITE_URL=''`, sitemap/canonical trỏ `http://localhost:3000` (T11.7-a) |
| `gh api .../branches/production/protection` | 1 check bắt buộc **`Promotion guard`** · `strict: false` · **1** approval · `dismiss_stale_reviews: true` · `require_last_push_approval: true` · `required_linear_history: false` (đúng — cho phép merge commit) · cấm force-push và xoá nhánh · `enforce_admins: false` · `required_conversation_resolution: true` |
| `ls deploy/host-prepare.sh` | **không tồn tại** — T11.35, quyền thư mục vẫn là việc gõ tay |
| `grep -rn "gh secret set" .` | **0 kết quả** — kho không có sẵn lệnh đặt secret. §10 dưới đây là bản soạn mới |

⚠ **Một chỗ lệch giữa tài liệu và thực tế, đã đo:** `docs/branch-protection.md` §4.2 in JSON đặt
`required_approving_review_count: 0`, còn nhánh `production` **đang chạy với 1**. Đừng chạy lại khối
`PUT` toàn bộ ấy — nó sẽ **hạ** số người duyệt xuống 0 mà không ai nhận ra. §10.4 nói cách vá an toàn.

---

## 0. Bức tranh — production khác staging ở đâu

```
                      ┌──────────────────── VPS-1 · PRODUCTION ────────────────────┐
  Internet ──TLS──►   │  nginx ──┬──► public-web:3000 ──/api/v1──►┐                │
   (chỉ 80/443)       │          ├──► admin-app:80   ──/api/──►   ├─► app:8080     │
                      │          └──► minio:9000  (presigned URL) │      │         │
                      │                                           │      ▼         │
                      │                              postgres:5432 ◄─────┘         │
                      │   /opt/songnhue/{.env, keys/}  ·  /var/lib/songnhue/backup │
                      └───────────────────────────────┬───────────────────────────-┘
                                                      │ SSH (tài khoản CHỈ ĐỌC)
                      ┌───────────────────────────────▼─── VPS-2 · STAGING ────────┐
                      │  cùng stack + kho sao lưu kéo về + Prometheus/Grafana      │
                      └───────────────────────────────┬────────────────────────────┘
                                                      │ age + rclone
                                            ┌─────────▼──────────┐
                                            │ Kho ngoài (B2/R2)  │  ← nhà cung cấp KHÁC
                                            └────────────────────┘
```

**Ba điều quyết định toàn bộ thiết kế, đọc trước khi sửa bất cứ thứ gì:**

1. **Giao diện và API luôn cùng origin.** Mỗi image FE tự chuyển tiếp `/api` sang backend dưới chính
   tên miền của nó. Nhờ vậy backend **không có một dòng CORS nào** và cookie refresh giữ được
   `SameSite=Strict`. ⛔ Đừng cho nginx trỏ thẳng `/api` sang `app` — đó là cấu hình đã chặn toàn bộ
   giao diện quản trị suốt WS-8→WS-20.
2. **Máy chạy ứng dụng không giữ khoá ghi vào kho sao lưu.** VPS-2 **kéo** về, VPS-1 không đẩy đi.
   Chiếm được VPS-1 vẫn không xoá được bản sao lưu.
3. **Giám sát nằm trên VPS-2** nên nó còn sống khi VPS-1 chết — đúng lúc cần nó.

### 0.1. Bảng khác biệt production ↔ staging

Hai môi trường dùng **chung một image**, chung `deploy.yml`, chung cây thư mục. Khác nhau ở đúng
những chỗ này — mỗi chỗ là một dòng người ta hay chép nhầm:

| | staging (VPS-2) | production (VPS-1) |
|---|---|---|
| Tệp compose | `compose.staging.yml` (chỉ `include:` bản prod rồi hạ trần bộ nhớ) | `compose.prod.yml` |
| `ROBOTS_TAG` | `noindex, nofollow` | **`all`** |
| `SEED_LOCATION` | `classpath:db/seed/portal` | ⛔ **RỖNG** — xem §5.4, đây là ô nguy hiểm nhất cả tài liệu |
| `so_bai_toi_thieu` của smoke test | 9 | **1** (production không seed, chỉ khẳng định "cổng không rỗng") |
| Kích hoạt CD | tự động, `push` vào nhánh `staging` | **tự động, `push` vào nhánh `production`** (6/9/2026 — T11.86). `workflow_dispatch` giữ lại làm đường **quay lui**, chạy từ nhánh `production` |
| Thiếu secret máy chủ | cảnh báo rồi bỏ qua | **DỪNG ĐỎ** |
| `DB_RESTORE_PASSWORD` | nên đặt (để diễn tập khôi phục qua UI) | ⛔ **để trống** |
| Prometheus/Grafana | có (`compose.observability.yml`) | không — giám sát đặt ngoài máy production |
| Khoá `jwt-private.pem`, `AES_KEY_V1` | riêng | ⛔ **PHẢI khác staging** |
| `LOG_TOTAL_SIZE_CAP` | không khai | `3GB` |

---

## 1. Mua sắm — làm xong hết **trước khi gõ lệnh đầu tiên**

Không mục nào ở đây là kỹ thuật, và cả bảy đều chặn. Thứ tự trong bảng là thứ tự thời gian chờ:
mục 1.3 (tên miền, cần hồ sơ Công ty) mất nhiều tuần nhất, nên bắt đầu từ nó.

| # | Việc | Chốt cụ thể | Ai làm |
|---|---|---|---|
| 1.1 | **Tên miền `.vn`** — chủ thể đăng ký **là Công ty** | §1.2 dưới | Công ty (pháp nhân) + QuanTran |
| 1.2 | **VPS-1** | **4 vCPU · 8 GB RAM · 160 GB SSD · Ubuntu 24.04 LTS**, đặt tại Việt Nam | QuanTran |
| 1.3 | **Email trung tính** của Công ty (`it@…`) để mở mọi tài khoản | ⛔ không dùng Gmail cá nhân | Công ty |
| 1.4 | **Tài khoản SMTP** gửi thư thật | nhà cung cấp trong nước, hoặc Amazon SES / Postmark | QuanTran |
| 1.5 | **Kho lưu trữ ngoài** — Backblaze B2 hoặc Cloudflare R2 | phải **khác nhà cung cấp** với hai VPS | QuanTran |
| 1.6 | **Giám sát ngoài** — UptimeRobot / Better Stack (bản miễn phí) | ping `/healthz` từ ngoài | QuanTran |
| 1.7 | **Phụ lục xử lý dữ liệu cá nhân** ký với Công ty | Công ty = Bên Kiểm soát, phía phát triển = Bên Xử lý | Pháp chế Công ty |

> ⚠ Mục 1.7 không phải thủ tục cho có. Hệ đang giữ dữ liệu nhân sự của người khác mà chưa có văn bản
> nào định danh tư cách, và nghĩa vụ lập hồ sơ đánh giá tác động (NĐ 13/2023 **Điều 25**) áp dụng
> **kể cả khi đặt máy trong nước**. Có phụ lục thì nghĩa vụ về đúng chỗ là Công ty.
> Đặt máy trong nước gỡ được **Điều 26** (chuyển dữ liệu ra nước ngoài), **không** gỡ được Điều 25.

### 1.1. VPS-1 — vì sao đúng con số đó

**4 vCPU · 8 GB · 160 GB SSD · Ubuntu 24.04 LTS.** Trần bộ nhớ khai trong `compose.prod.yml`:

| Thành phần | `mem_limit` |
|---|---:|
| `app` | 3 GB |
| `postgres` | 2 GB (+ `shm_size: 256mb`) |
| `minio` | 1 GB |
| `public-web` | 512 MB |
| `admin-app` | 256 MB |
| `nginx` | 256 MB |
| **Cộng** | **≈ 7,3 GB** |

Cộng hệ điều hành + Docker daemon (~500 MB) là đã chạm 8 GB. Biên còn lại đủ cho `pg_dump` chạy
song song với dịch vụ và cho lượt `docker compose pull` giữ đồng thời image cũ lẫn mới.

**Nhà cung cấp**: lấy báo giá **Viettel IDC · VNPT · FPT · BizFly · VNG**. Chênh nhau nhiều và hay
có giá trả trước theo năm — **hỏi thẳng, đừng lấy giá niêm yết**. Ba tiêu chí, theo thứ tự:

1. **Xuất hoá đơn VAT cho Công ty** — bắt buộc, không phải "nếu có thì tốt". Không có hoá đơn thì
   Công ty không thanh toán được, và cả lập luận "tài sản của Công ty" gãy ở khâu chứng từ.
2. **Cho tự quản lý firewall và snapshot**, có console cứu hộ (khi tự khoá mình khỏi SSH).
3. **Giá năm thứ hai**, không phải giá khuyến mãi năm đầu.

⛔ **Ba chỗ cắt là hỏng, không phải tiết kiệm:** tên miền + TLS riêng cho staging (chỗ này **không
tốn thêm đồng nào** — xem §1.2) · khoá riêng của staging · giám sát.

### 1.2. Tên miền — phần dễ làm sai nhất, và làm sai thì mất nhiều tuần để sửa

**Chỉ phải mua MỘT tên miền** cho cả hai môi trường. Sáu địa chỉ của hệ thống = 1 tên miền gốc +
5 tên miền phụ **miễn phí**:

| Địa chỉ | Trỏ tới | Phải mua? |
|---|---|---|
| `<ten-mien>` + `www` | VPS-1 | ✅ thứ duy nhất mất tiền |
| `admin.<ten-mien>` | VPS-1 | miễn phí |
| `files.<ten-mien>` | VPS-1 | miễn phí |
| `staging.<ten-mien>` · `admin-staging.<ten-mien>` · `files-staging.<ten-mien>` | VPS-2 | miễn phí |

**Đuôi**: mua **`.vn`**. `.com.vn` rẻ hơn và vẫn chính danh. ⛔ **`.gov.vn` không đủ điều kiện** —
dành riêng cho *cơ quan nhà nước*; Công ty TNHH MTV là *doanh nghiệp* nhà nước, hai tư cách khác nhau.

**Mua ở đâu**: VNNIC quản lý `.vn` nhưng **không bán trực tiếp**. Phải qua một **Nhà đăng ký được
VNNIC công nhận** — Viettel · VNPT · FPT Telecom · PA Việt Nam · Mắt Bão · iNET · Nhân Hoà ·
Tenten · BKNS. Danh sách chính thức ở `vnnic.vn`, mục *Nhà đăng ký tên miền ".vn"* — tra ở đó,
đừng tin danh sách trong tài liệu này là đã cập nhật.

**Kiểm tên còn trống TRƯỚC khi làm gì khác** — `whois.vnnic.vn`. Tên miền đi vào `.env`, vào chứng
chỉ TLS, vào **chữ ký presigned** và vào cấu hình nginx; đổi tên sau khi đã dựng là dựng lại kha khá thứ.

**Hồ sơ phải xin từ Công ty trước khi ngồi điền** (xin sớm, đây là phần chờ lâu):

* Tên đầy đủ **đúng như trên giấy đăng ký doanh nghiệp**
* Mã số doanh nghiệp / mã số thuế
* Địa chỉ trụ sở đăng ký
* Người đại diện theo pháp luật + thông tin liên hệ
* Email liên hệ — **email trung tính của Công ty**
* Bản khai đăng ký có **chữ ký người đại diện và dấu** (nhiều nhà đăng ký nay nhận ký số)

> ⛔ **Cái bẫy thật sự: có nhà đăng ký đăng ký tên miền dưới tên CHÍNH HỌ rồi "quản lý hộ".** Khi đó
> Công ty **không sở hữu** tên miền — muốn chuyển đi phải xin phép. Phải **kiểm**, không phải hỏi miệng:
> tra `whois.vnnic.vn`, trường chủ thể phải hiện **tên Công ty**. Hiện tên nhà đăng ký hoặc tên cá
> nhân nào đó là **sai — bắt làm lại ngay**, đừng để sang bước sau.
>
> ⚠⚠ **Đổi chủ thể tên miền `.vn` về sau là thủ tục hành chính thật**, cần hồ sơ từ **cả hai bên**.
> Đăng ký nhầm dưới tên cá nhân rồi chuyển về Công ty sau là tự tạo một việc mất nhiều tuần — và nó
> rơi đúng vào lúc bàn giao, tức lúc bận nhất.

**Gia hạn — chỗ hỏng lặng lẽ nhất trong cả hạ tầng.** Tên miền hết hạn thì cổng thông tin, giao diện
quản trị, đường tải tệp **và lượt gia hạn chứng chỉ TLS** dừng cùng lúc. Không cảnh báo nào trong hệ
bắt được: Prometheus canh máy chủ, mà máy chủ vẫn chạy tốt. Làm ngay lúc mua:

1. Bật tự động gia hạn **và** giữ phương thức thanh toán còn hiệu lực.
2. Đặt lịch nhắc **trước 60 ngày** trong lịch của **Công ty**, không phải lịch cá nhân.
3. Email liên hệ của tên miền phải là email Công ty **còn người đọc**.

### ✅ Kiểm chứng mục 1

- [ ] `whois` trả về **tên Công ty** ở trường chủ thể
- [ ] Hoá đơn VAT của VPS-1 mang tên Công ty
- [ ] Có địa chỉ IP công khai của VPS-1 và mật khẩu `root` lần đầu
- [ ] Đã có tài khoản SMTP thật, gửi thử một thư ra ngoài thành công
- [ ] Đã tạo bucket kho ngoài (B2/R2) ở **nhà cung cấp khác**
- [ ] Phụ lục xử lý DLCN đã ký

---

## 2. Lượt đăng nhập đầu tiên vào VPS-1

⚠ Mục 2, 3, 4 làm **y hệt** như đã làm cho VPS-2. Khác nhau duy nhất là nội dung `.env`. Làm khác
nhau ở tầng máy là làm cho staging mất giá trị.

### 2.1. Sinh khoá triển khai — **trên máy cá nhân**

Dùng lại khoá đã sinh cho VPS-2 hay sinh khoá mới đều được; tài liệu này giả định **dùng chung một
khoá triển khai** cho cả hai máy (nó là khoá của *người triển khai*, không phải của *máy*).

```bash
# --- Trên máy của anh ---
ssh-keygen -t ed25519 -C "songnhue-deploy" -f ~/.ssh/songnhue_deploy
cat ~/.ssh/songnhue_deploy.pub          # ← chuỗi này sẽ dán sang VPS-1 ở bước 2.2
```

### 2.2. Đăng nhập lần đầu bằng `root`, tạo user vận hành

Nhà cung cấp giao máy kèm mật khẩu `root` (hoặc đã cắm sẵn khoá). Vào bằng đường họ đưa:

```bash
ssh root@<IP-VPS1>
```

Rồi **ngay trong phiên đó**:

```bash
# --- Trên VPS-1, đang là root ---
adduser --disabled-password --gecos "" songnhue
usermod -aG sudo songnhue
mkdir -p /home/songnhue/.ssh && chmod 700 /home/songnhue/.ssh
nano /home/songnhue/.ssh/authorized_keys     # dán nội dung songnhue_deploy.pub
chmod 600 /home/songnhue/.ssh/authorized_keys
chown -R songnhue:songnhue /home/songnhue/.ssh
```

### 2.3. Khoá đăng nhập bằng mật khẩu — ⛔ ghi vào drop-in `10-`, **không** ghi vào `sshd_config`

```bash
sudo tee /etc/ssh/sshd_config.d/10-hardening.conf >/dev/null <<'EOF'
PermitRootLogin no
PasswordAuthentication no
KbdInteractiveAuthentication no
EOF
sudo sshd -t && sudo systemctl reload ssh
```

⛔⛔ **VÌ SAO `10-` CHỨ KHÔNG PHẢI `sshd_config`, VÀ KHÔNG PHẢI `60-`**

Trong `sshd_config`, với mỗi từ khoá thì **giá trị ĐỌC ĐƯỢC ĐẦU TIÊN thắng** — không phải giá trị
cuối, ngược với gần như mọi tệp cấu hình khác. Ubuntu đặt `Include /etc/ssh/sshd_config.d/*.conf` ở
**dòng đầu** `sshd_config`, và thư mục ấy đọc theo **thứ tự chữ cái**.

Nghĩa là ảnh Ubuntu của nhà cung cấp thả một `50-cloud-init.conf` chứa `PasswordAuthentication yes`
thì nó **thắng cả `sshd_config` lẫn mọi drop-in đánh số lớn hơn 50**. Đo trên VPS-2 ngày 29/8, đúng
từng dòng:

```
/etc/ssh/sshd_config:132:                    PasswordAuthentication no    ← thua
/etc/ssh/sshd_config.d/50-cloud-init.conf:1: PasswordAuthentication yes   ← thắng
```

Hướng dẫn cũ ghi vào `sshd_config`, nên VPS-2 **mở cửa mật khẩu suốt** trong khi tài liệu nói đã khoá.

> ⛔ **Giữ nguyên dấu `&&`.** `sshd -t` không đạt mà vẫn `reload` là tự khoá mình khỏi máy chủ, và
> lúc ấy đường vào duy nhất là console cứu hộ của nhà cung cấp.
>
> ⛔ **Mở một phiên SSH thứ hai và đăng nhập được rồi mới đóng phiên đang dùng.**

### ✅ Kiểm chứng mục 2 — hỏi cấu hình ĐANG CÓ HIỆU LỰC, đừng đọc tệp

```bash
# Trên VPS-1
sudo sshd -T | grep -iE '^(passwordauthentication|permitrootlogin|kbdinteractiveauthentication)'
sudo grep -rn -i '^[[:space:]]*passwordauthentication' /etc/ssh/sshd_config /etc/ssh/sshd_config.d/
```

Cả ba phải là `no`. Lệnh thứ hai cho thấy **ai đang tranh với ai** — nếu có một `50-cloud-init.conf`
nói `yes` thì `10-hardening.conf` đang thắng nó, đúng như thiết kế.

```bash
# Trên máy cá nhân
ssh -i ~/.ssh/songnhue_deploy songnhue@<IP-VPS1> 'echo OK'
ssh -i ~/.ssh/songnhue_deploy root@<IP-VPS1> 2>&1 | grep -q "Permission denied" && echo "✓ root đã khoá"
```

---

## 3. Làm cứng máy chủ — ⛔ **cổng 22 bị quét liên tục, và nó làm ĐỎ lượt deploy**

Đây không phải mục "cho chắc". Ngày 27/8 một lượt CD đỏ vì đúng chuyện này: một IP lạ giữ **32 kết
nối SSH đồng thời** (67 tiến trình `sshd`), `MaxStartups` mặc định là `10:30:100` nên sshd **thả
ngẫu nhiên 30%** kết nối mới. Đo thật từ máy dev lúc ấy: SSH **7/10**, HTTPS cùng máy cùng lúc 5/5.

### 3.1. Tường lửa

```bash
ufw default deny incoming
ufw default allow outgoing
ufw allow 22/tcp
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable
```

⛔ **Không mở 5432 (Postgres) và 9000/9001 (MinIO).** `compose.prod.yml` cố ý không khai `ports` cho
hai service ấy — chúng chỉ tồn tại trong mạng nội bộ của compose. Cần `psql` thì đi qua
`docker compose exec postgres` hoặc đường hầm SSH; cả hai đều đòi đã vào được máy.

⚠ **Kiểm cổng lạ ngay lúc nhận máy.** Trên VPS-2 đo được **cổng 5201 mở ra Internet** (mặc định của
`iperf3`) mà chưa xác định được tiến trình chủ — nợ T11.54. Ảnh của nhà cung cấp hay kèm thứ không ai gọi:

```bash
sudo ss -tlnp                     # mọi cổng đang lắng nghe + tiến trình chủ
sudo ufw status verbose
```

### 3.2. fail2ban + giới hạn kết nối

```bash
# 1 ─ fail2ban
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

# 2 ─ Không cho MỘT nguồn chiếm hết suất kết nối (OpenSSH ≥ 8.5)
sudo tee /etc/ssh/sshd_config.d/60-startups.conf >/dev/null <<'EOF'
MaxStartups 30:30:200
PerSourceMaxStartups 6
PerSourceNetBlockSize 32:128
ClientAliveInterval 120
ClientAliveCountMax 3
EOF
sudo sshd -t && sudo systemctl reload ssh
```

`ClientAliveInterval 120` không phải cho gọn: mặc định là `0`, nghĩa là **phiên chết không bao giờ
được dọn**. Lúc sự cố 27/8 có 33 kết nối "đã xác thực" treo trong khi `who` trả về **0 người**.

> 📌 `60-` chỉ có tác dụng vì `MaxStartups` không tệp nào khác đặt. Mọi directive **có tranh chấp**
> phải nằm ở `10-`. Một tham số ăn, một tham số không, cùng một thư mục — đó là lý do §2.3 tách ra.

### 3.3. ⛔ Ghim khoá máy chủ **trước** lượt CD đầu tiên — lượt deploy từng **tự cấm chính nó**

Đo 29/8, sau khi fail2ban chạy được một ngày. Nhật ký `sshd` trong cửa sổ lượt CD đỏ:

```
19:27:03 Unable to negotiate with 52.230.251.196 port 39970: no matching host key type
         found. Their offer: sk-ssh-ed25519@openssh.com [preauth]
19:27:04 Connection closed by 52.230.251.196 port 39971 [preauth]
19:27:05 Connection closed by 52.230.251.196 port 39968 [preauth]
19:27:05 Connection closed by 52.230.251.196 port 39972 [preauth]
19:27:06 Unable to negotiate with 52.230.251.196 port 39969 … sk-ecdsa-sha2-nistp256
```

`52.230.251.196` thuộc dải Azure — runner GitHub. Bước *Mở đường SSH* khởi động lúc **12:27:03 UTC =
19:27:03 giờ VN**: khớp tới từng giây. Năm kết nối song song, hai cái chào bằng kiểu khoá `sk-*` —
vân tay của **`ssh-keyscan`**. `mode = aggressive` tính cả dòng `Connection closed … [preauth]`, nên
**năm kết nối dò vượt ngưỡng `maxretry = 3` ngay lập tức**. IP runner vào danh sách cấm, rồi sáu lượt
`ssh` kế tiếp gõ vào bức tường mà chính nó vừa dựng. **Lượt CD xanh hôm trước chỉ thắng cuộc đua với
vòng quét nhật ký của fail2ban** — đường ống chạy bằng may rủi, không bằng thiết kế.

`deploy.yml` nay **không dùng `ssh-keyscan`** nữa: nó ghim khoá từ secret. Việc của mục này là lấy
giá trị ấy ra:

```bash
# TRÊN VPS-1
cat /etc/ssh/ssh_host_ed25519_key.pub
```

Lấy **hai trường đầu** (`ssh-ed25519 AAAA…`), bỏ đuôi `root@…`, rồi ghép với **đúng chuỗi** sẽ đặt ở
secret `PROD_HOST`:

```
203.0.113.10 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA…
```

⚠ Chuỗi đứng đầu phải **trùng từng ký tự** với `PROD_HOST`. Ghi tên miền trong khi `PROD_HOST` là địa
chỉ IP thì `known_hosts` không khớp và lượt deploy đỏ ở bước xác minh — đúng thiết kế, nhưng mất công dò.

> **Đây còn là bản vá bảo mật, không chỉ vá độ ổn định.** `ssh-keyscan` **tin bất kỳ khoá nào máy chủ
> đưa ra**. Dò lại mỗi lượt deploy nghĩa là **không lượt nào thật sự xác minh** mình đang nói chuyện
> với đúng máy: ai chen được vào giữa sẽ nhận trọn khoá triển khai và toàn bộ nội dung deploy.

### 3.4. Tự vá bảo mật — làm một lần, chạy mãi

```bash
apt install -y unattended-upgrades
dpkg-reconfigure -plow unattended-upgrades
```

### ✅ Kiểm chứng mục 3

```bash
# Trên máy cá nhân — tỉ lệ hỏng phải về 0/10
ok=0; for i in $(seq 1 10); do
  ssh -o BatchMode=yes -o ConnectTimeout=8 songnhue@<IP-VPS1> true 2>/dev/null && ok=$((ok+1))
  sleep 2
done; echo "$ok/10"

# Trên VPS-1
sudo systemctl is-active fail2ban          # → active
sudo fail2ban-client status sshd           # sau vài giờ: "Currently banned" > 0
sudo sshd -T | grep -iE '^(maxstartups|persourcemaxstartups|clientaliveinterval)'
sudo ss -tlnp                              # chỉ 22/80/443 ra ngoài
```

---

## 4. Docker · cây thư mục · quyền · đăng nhập GHCR

### 4.1. Docker

```bash
curl -fsSL https://get.docker.com | sh
usermod -aG docker songnhue
systemctl enable --now docker
```

⚠ Đăng xuất rồi vào lại để nhóm `docker` có hiệu lực. Workflow triển khai chạy `docker` **không qua
`sudo`** — user SSH không vào được nhóm `docker` thì mọi lượt deploy đỏ.

### 4.2. Cây thư mục — ⛔ **chủ sở hữu KHÔNG phải user SSH… trừ đúng một thư mục**

Bản cũ của tài liệu chung ghi `chown -R songnhue:songnhue …` cho cả ba đường dẫn. **Sai, và sai theo
kiểu làm mọi lượt deploy đỏ.** Đã trả giá trên staging ngày 25/8.

Ba danh tính khác nhau cùng dùng cây thư mục này, và **không cái nào là user SSH**:

| Danh tính | Là ai | Đụng vào gì |
|---|---|---|
| `1000` | user trong image `app` (ghim ở `backend.Dockerfile`) | đọc khoá, ghi log |
| `999` | user `postgres` **bên trong container** | `pre-deploy-dump.sh` chạy `pg_dump` ở đó, ghi thẳng vào thư mục sao lưu |
| user SSH (`songnhue`) | người vận hành trên host | sửa `.env`, dọn bản sao lưu cũ |

⛔⛔ **Ngoại lệ, và là chỗ đã trả giá ngày 7/9:** thư mục **gốc** `/opt/songnhue` phải thuộc **user
SSH**. `mkdir -p /opt/songnhue/keys` tạo thư mục cha bằng `root`, và lượt `rsync` của CD ghi bằng
user SSH nên thoát **23** với hàng chục dòng `Permission denied`. Trên VPS-1 `host-prepare.sh` đã
chạy **hai lượt, thoát 0 cả hai** trong khi thư mục vẫn `root:root` — vì cả ba phép đo lúc ấy dùng
`stat`, mà `stat` nói thư mục *mang nhãn gì*, không nói *ai ghi vào được*. Đối chiếu VPS-2 staging,
nơi CD chạy được suốt: `/opt/songnhue` là `songnhue:songnhue 755`.

⛔ **`chown` trong Dockerfile không có tác dụng với bind mount** — host che hoàn toàn thứ image dựng
sẵn. Quyền phải đặt **trên máy chủ**.

```bash
mkdir -p /opt/songnhue/keys /var/lib/songnhue/backup /var/log/songnhue /var/log/nginx

# ⚠ ĐO uid/gid của image, đừng ghi cứng 1000. Trước khi ghim, id thật là 100:101 —
#   không phải 1000 như ai cũng tưởng khi đọc lướt `adduser -S`.
docker run --rm --entrypoint id ghcr.io/team-dev-qnt/songnhue/app:staging

APP_UID=1000; APP_GID=1000; PG_UID=999
getent group "$APP_GID" || groupadd -g "$APP_GID" songnhue-app
usermod -aG "$APP_GID" "$(id -un)"          # user SSH vào chung nhóm

# ⛔ Thư mục GỐC thuộc user SSH — đây là chỗ DUY NHẤT chown theo TÊN là đúng.
chown "$(id -un):$(id -un)" /opt/songnhue && chmod 755 /opt/songnhue

chown -R "$APP_UID:$APP_GID" /opt/songnhue/keys /var/log/songnhue
chmod 700 /opt/songnhue/keys && chmod 600 /opt/songnhue/keys/* 2>/dev/null
chmod 755 /var/log/songnhue

# ⚠⚠ Thư mục sao lưu: chủ là POSTGRES (999), nhóm là app (1000), + setgid.
#    `chown -R 1000:1000` ở đây làm `pg_dump` hỏng — mà bước chụp trước triển khai
#    chạy ở MỌI lượt deploy, nên hậu quả là mọi lượt deploy đỏ ngay bước đầu.
chown -R "$PG_UID:$APP_GID" /var/lib/songnhue/backup
chmod 2775 /var/lib/songnhue/backup
```

> 📌 Không phải gõ tay nữa — `deploy/host-prepare.sh` làm hết (T11.35, chạy thật trên VPS-1 6/9 và
> 7/9). Khối trên giữ lại để đọc hiểu *vì sao* từng ô như vậy.
>
> ⭐ **Và script ấy đo bằng cách GHI THẬT, không bằng `stat`** — `kiem_ghi_duoc` chạy một lượt
> `touch` dưới danh nghĩa user triển khai. `HostPrepareQuyenTest` canh hai bất biến: mọi thư mục
> được tạo đều phải có phép đo đứng sau, và thư mục gốc phải đo bằng ghi thật. Bài kiểm ấy tìm ra
> `/var/log/nginx` chưa từng được đo, ngay lượt chạy đầu tiên.

### 4.3. `docker login ghcr.io` — làm trước, không thì `compose up` dừng ngay

```bash
echo <PAT> | docker login ghcr.io -u <github-username> --password-stdin
```

PAT cần đúng một quyền: **`read:packages`**. Chưa đăng nhập thì lệnh kéo image đầu tiên trả
`unauthorized`, **kể cả khi repo là public** — gói GHCR mặc định là riêng tư.

⚠ **Workflow triển khai KHÔNG đăng nhập hộ máy chủ.** Đã kiểm: không có một lệnh `docker login` nào
trong `deploy.yml` hay `.github/scripts/`. Đây là nợ **T11.36**.

### ✅ Kiểm chứng mục 4

```bash
ssh -i ~/.ssh/songnhue_deploy songnhue@<IP-VPS1> 'docker run --rm hello-world >/dev/null && echo OK'
ssh songnhue@<IP-VPS1> 'docker pull ghcr.io/team-dev-qnt/songnhue/app:staging >/dev/null && echo "✓ GHCR ok"'
stat -c '%u:%g %a' /opt/songnhue/keys          # → 1000:1000 700
stat -c '%u:%g %a' /var/log/songnhue           # → 1000:1000 755
stat -c '%u:%g %a' /var/lib/songnhue/backup    # → 999:1000 2775   ⛔ ô này sai là mọi deploy đỏ
```

---

## 5. Khoá và tệp `.env` production

`.env` và `keys/` là **hai thứ duy nhất chỉ tồn tại trên máy chủ**. Cả hai được loại khỏi `rsync` của
lượt deploy (`--exclude '.env' --exclude 'env/' --exclude 'keys/'`), nên chúng **không bao giờ đi qua
runner của GitHub** — và cũng nghĩa là **không ai điền hộ**.

### 5.1. Sinh khoá — **trên máy chủ**, không sinh ở máy cá nhân rồi chép qua

```bash
cd /opt/songnhue/keys
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -pubout -in jwt-private.pem -out jwt-public.pem
chmod 600 jwt-private.pem jwt-public.pem
chown 1000:1000 jwt-private.pem jwt-public.pem
openssl rand -base64 32          # ← chép giá trị này vào AES_KEY_V1
```

> ⛔ **Khoá của staging và production PHẢI khác nhau.** Dùng chung nghĩa là token cấp ở staging **mở
> được production**, và một bản dump staging — thứ luôn được xử lý lỏng tay hơn — trở thành đường vào
> dữ liệu thật.
>
> ⛔ **Sinh lại `jwt-private.pem` là vô hiệu mọi phiên đang sống.** Chỉ làm khi xoay khoá có kế hoạch —
> `docs/runbook/xoay-khoa.md`.
>
> ⛔ Khoá **không nằm trong bản backup CSDL**. Lộ backup **cộng** lộ khoá = lộ toàn bộ
> `employee_sensitive` và credential bên thứ 3. Đó là lý do `verify-no-keys.sh` chạy ở mỗi lượt dump.

### 5.2. Tạo `.env`

```bash
# Chép mẫu ĐÚNG môi trường lên máy chủ (từ máy cá nhân)
scp deploy/env/prod.env.example songnhue@<IP-VPS1>:/opt/songnhue/.env
ssh songnhue@<IP-VPS1> 'chown $(id -un):$(id -un) /opt/songnhue/.env && chmod 600 /opt/songnhue/.env'
```

⛔ **Chủ sở hữu phải là user SSH, mode `600`.** Hai cách sai đã gặp thật, mỗi máy một kiểu:
> * `root:root 600` (VPS-1, 7/9) — lượt CD chạy `docker compose --env-file .env` dưới user SSH và
>   chết ngay ở `open /opt/songnhue/.env: permission denied`.
> * `songnhue:songnhue 664` (VPS-2 staging) — **mọi user trên máy đọc được tệp bí mật**, kể cả
>   `nobody`. CD chạy được, nên không có gì kêu.

Mức đúng phân biệt được hai câu ấy: `test -r` dưới user triển khai phải **được**, dưới `nobody` phải
**không**. Đo cả hai chiều, đừng đo một chiều.

⛔ **Không dùng `staging.env.example` rồi sửa.** Hai tệp khác nhau ở những chỗ im lặng nhất
(`SEED_LOCATION`, `ROBOTS_TAG`, `LOG_TOTAL_SIZE_CAP`, `DB_RESTORE_PASSWORD`), và tất cả đều hỏng
**không kèm dòng log nào**.

### 5.3. ⚠ Bốn cơ chế fail-fast khác nhau — phải phân biệt, vì ba trong bốn KHÔNG bảo vệ gì

| Cơ chế | Ở đâu | Thiếu thì sao |
|---|---|---|
| `UnresolvedPlaceholderGuard` | bean `@ConfigurationProperties`, **chỉ trường String** và **chỉ khi toàn bộ giá trị** là `${TÊN}` | ném `IllegalStateException("Thiếu biến môi trường …")`, **app không lên** |
| `@Validated` + `@NotBlank` | các lớp `*Properties` | báo "Thiếu X" — ⚠ **đi lọt** nếu placeholder còn nguyên (chuỗi không rỗng) |
| `${VAR:?…}` của Compose | **chỉ** `APP_IMAGE` · `ADMIN_IMAGE` · `PUBLIC_IMAGE` | `docker compose` thoát ngay |
| `require_env` của `10-bootstrap.sh` | script initdb, **chỉ chạy lượt dựng volume ĐẦU TIÊN** | container postgres exit 1, quay vòng khởi động lại |

⛔⛔ **Mọi `${TÊN}` khác trong `compose.prod.yml` KHÔNG có `:?`** — thiếu là **giải ra chuỗi rỗng,
chạy tiếp, không một dòng log nào**. `server_name` rỗng, MinIO chạy tài khoản mặc định,
`/api/revalidate` trả 503. Đây là lý do bảng §5.5 tồn tại.

### 5.4. ⛔⛔ `SEED_LOCATION` — ô nguy hiểm nhất cả tài liệu

```
SEED_LOCATION=
```

**Rỗng, và đây không phải "chưa điền" — đây là giá trị ĐÚNG.**

Điền `SEED_LOCATION` ở production nghĩa là Flyway giải migration seed, mà migration ấy **mở đầu bằng
`DELETE FROM articles`** (mọi bài không có mục menu trỏ tới). Tức là **xoá nội dung thật của Công ty**
rồi đăng 5 bài chép lại của báo ngoài. **Không có lượt bấm xác nhận nào chặn được — migration chạy
một chiều.**

Rỗng ⇒ compose giải về `classpath:db/seed/none`, một thư mục **có thật** và **cố ý không có migration
nào**.

⚠ `SeedGateTest` canh **tệp mẫu trong repo**, **không** canh `/opt/songnhue/.env` đang chạy. Bộ canh
ấy không cứu được người gõ nhầm trên máy chủ. Phép kiểm thật là §9 mục 8.

⚠ Nợ **T11.38**: còn một đường seed thứ hai (`make seed-portal` + `tools/seeder/seed-portal-data.ts`
gọi REST) **không đi qua cổng `SEED_LOCATION`**. Đừng chạy `make seed-*` với `ENV=prod`, bao giờ.

### 5.5. Bảng biến — điền cái gì, sai thì hỏng thế nào

Cột **fail-fast** = có dừng ứng dụng không. `⛔ im lặng` là loại nguy hiểm nhất: nó chạy, và sai.

#### Ứng dụng

| Biến | Giá trị production | Fail-fast | Sai/rỗng thì sao |
|---|---|:-:|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | im lặng | chỉ là nhãn — **chưa có `application-prod.yml`**, mọi khác biệt nằm ở biến môi trường |
| `APP_PORT` | `8080` | — | compose ghi đè cứng 8080; biến trong `.env` vô hiệu với service `app` |
| `LOG_LEVEL_APP` | `INFO` | im lặng | ⛔ **không để `DEBUG`** — log phình, đĩa đầy, và `pg_dump` hỏng theo |
| `APP_ENVIRONMENT` | `production` | im lặng | thiếu ⇒ metric staging và production lẫn nhau ở Prometheus |
| `SHEDLOCK_ENABLED` | `false` | im lặng | v1 chạy 1 node; bật khi lên ≥2 node |
| `WORKER_ENABLED` | `true` | im lặng | tắt = hàng đợi công việc không ai chạy |
| `APP_BASE_URL` | *(bỏ qua)* | — | ⚠ **không dòng mã nào đọc** — biến mồ côi, để nguyên cũng được |

#### PostgreSQL

| Biến | Giá trị production | Fail-fast | Sai/rỗng thì sao |
|---|---|:-:|---|
| `DB_HOST` · `DB_PORT` · `DB_NAME` | `postgres` · `5432` · `songnhue` | ✅ | compose ghi đè cho `app`; giá trị ở `.env` là thứ **script sao lưu** đọc |
| `DB_USER` | `songnhue_app` | ✅ | role runtime — **không có DELETE** trên `audit_logs`, `security_events`, `hydro_raw_logs` |
| `DB_PASSWORD` | *(bí mật)* | ✅✅ | ⚠⚠ **tên phải đúng là `DB_PASSWORD`.** Bản trước ghi `DB_APP_PASSWORD` — biến ấy không ai đọc, biến `10-bootstrap.sh` cần thì không được truyền: postgres quay vòng khởi động lại, rồi `songnhue_app` báo `password authentication failed` |
| `DB_MIGRATION_USER` / `_PASSWORD` | `songnhue_owner` / *(bí mật)* | ✅ | role sở hữu lược đồ; `migrator` không chạy được |
| `DB_ARCHIVER_PASSWORD` | *(bí mật)* | ✅ | role **duy nhất** có DELETE `audit_logs` |
| `DB_READONLY_PASSWORD` | *(bí mật)* | ✅ | `pg_dump` hỏng — **lưới an toàn duy nhất của hệ** |
| `POSTGRES_PASSWORD` | *(bí mật)* | ⛔ im lặng | superuser; chỉ dùng lúc khởi tạo volume |
| `FLYWAY_ENABLED` | **`false`** | im lặng | ⚠ `true` ở service `app` ⇒ migration hỏng làm app lên **nửa vời**. Migration là việc của service `migrator` riêng |
| `DB_POOL_MAX` / `_MIN` | `20` / `5` | im lặng | — |
| `BOOTSTRAP_ADMIN_PASSWORD` | điền **lần đầu**, rồi **XOÁ** | im lặng | xem §8.5 |

#### MinIO

| Biến | Giá trị production | Fail-fast | Sai/rỗng thì sao |
|---|---|:-:|---|
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | *(bí mật, tài khoản **chủ**)* | ⛔ im lặng | rỗng ⇒ MinIO chạy **tài khoản mặc định** — kho tệp nhân sự mở bằng mật khẩu ai cũng biết |
| `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` | *(bí mật, tài khoản **dịch vụ**)* | ✅ | khác hẳn cặp `ROOT` ở trên. `minio-init` tự đo bằng chính cặp này: ghi → đọc → xoá |
| `MINIO_BUCKET_MEDIA` / `_REPORT` / `_AUDIT` | `songnhue-media` / `-report` / `-audit` | ✅ | bucket audit được bật versioning |
| `MINIO_ENDPOINT` | ⛔ **ĐỪNG ghi đè** | ✅ | compose đã đặt `https://${FILES_DOMAIN}`. Xem hộp dưới |

> ⚠⚠ **`MINIO_ENDPOINT` KHÔNG được điền `http://minio:9000`**, dù trực giác bảo thế cho nhanh.
> `AttachmentService` trả **presigned URL** để trình duyệt tải thẳng từ MinIO, và chữ ký AWS SigV4
> ký **cả tên máy**. Ký bằng `minio:9000` thì trình duyệt không phân giải nổi tên đó.
> **Triệu chứng: tải LÊN chạy tốt, MỌI nút Tải về đều hỏng** — và không phép kiểm nào ở §9 trừ mục 9
> phân biệt được hai trạng thái ấy.

#### SMTP

| Biến | Giá trị production | Fail-fast | Sai/rỗng thì sao |
|---|---|:-:|---|
| `SMTP_HOST` | *(nhà cung cấp thật)* | ⛔ im lặng | rỗng ⇒ `MailConfig` **không tạo bean**, mọi lượt gửi ghi `SKIPPED`, hệ vẫn chạy — thư đơn giản là không bao giờ tới |
| `SMTP_PORT` | `587` | im lặng | mặc định `1025` là Mailpit của môi trường local |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | *(bí mật)* | im lặng | — |
| `SMTP_STARTTLS` | `true` | im lặng | ⚠ mặc định của ứng dụng là `false` — mẫu **phải** ghi đè |
| `SMTP_FROM` | `no-reply@<ten-mien>` | ✅ | phải là miền SMTP cho phép gửi thay mặt |
| `SMTP_AUTH` | *(thêm tay nếu nhà cung cấp đòi)* | im lặng | ⚠ **không có trong tệp mẫu nào**, mặc định `false`, trong khi mẫu vẫn bắt điền username/password |

#### Khoá

| Biến | Giá trị production | Fail-fast |
|---|---|:-:|
| `AES_KEY_ID` | `v1` | ✅ |
| `AES_KEY_V1` | kết quả `openssl rand -base64 32` | ✅ — kiểm đúng **32 byte** |
| `JWT_KEY_ID` | `v1` | ✅ |
| `JWT_PRIVATE_KEY_PATH` | `/opt/songnhue/keys/jwt-private.pem` | ✅ |
| `JWT_PUBLIC_KEY_PATH` | `/opt/songnhue/keys/jwt-public.pem` | ✅ — hai tệp không cùng cặp ⇒ app không khởi động |

#### Tên miền / nginx — sáu biến compose đòi mà **không có `:?`**

| Biến | Giá trị production | Sai/rỗng thì sao |
|---|---|---|
| `PUBLIC_DOMAIN` | `<ten-mien>` — **không scheme, không `/` cuối** | rỗng ⇒ `server_name` rỗng ⇒ mọi tên miền rơi vào block mặc định và bị `ssl_reject_handshake`. Triệu chứng: "trang không vào được", **không log ứng dụng nào** |
| `ADMIN_DOMAIN` | `admin.<ten-mien>` | như trên |
| `FILES_DOMAIN` | `files.<ten-mien>` | vừa là tên miền công khai của MinIO, vừa là **bí danh mạng** của nginx trong compose. Rỗng ⇒ `MINIO_ENDPOINT` thành `https://`. Đổi tên mà không dựng lại ⇒ mọi nút Tải về hỏng |
| `ROBOTS_TAG` | **`all`** | mặc định của compose là `all` — nên **staging** mới là bên phải nhớ ghi `noindex, nofollow` |
| `MINIO_ROOT_USER` / `_PASSWORD` | (xem trên) | |
| `REVALIDATE_SECRET` | *(bí mật, một giá trị cho cả hai service)* | lệch giữa `app` và `public-web` ⇒ `/api/revalidate` trả **401** và cổng **đứng yên ở nội dung cũ** sau mỗi lần duyệt bài; rỗng ⇒ **503**. Compose lấy cùng một giá trị từ `.env` nên chỉ điền một chỗ |

#### Frontend (build-time — nướng vào image, **không** đọc lúc chạy)

| Biến | Giá trị production | Ghi chú |
|---|---|---|
| `VITE_API_BASE_URL` | ⛔ **để TRỐNG** | trống ⇒ đường dẫn tương đối `/api/v1`, cùng origin, không cần CORS |
| `NEXT_PUBLIC_API_BASE_URL` | ⛔ **để TRỐNG** | như trên |
| `NEXT_PUBLIC_SITE_URL` | `https://<ten-mien>` — **tuyệt đối** | gốc của sitemap, canonical, ảnh Open Graph. ⚠ Giá trị **thật sự có hiệu lực** đến từ **biến kho GitHub** `PUBLIC_SITE_URL` lúc CI dựng image, không từ `.env` — xem §10.3 |

#### Backup · log · tích hợp

| Biến | Giá trị production | Ghi chú |
|---|---|---|
| `BACKUP_DIR` | `/var/lib/songnhue/backup` | PHẢI là volume gắn ra host — VPS-2 **kéo** từ đúng thư mục này |
| `BACKUP_TIMEOUT` | `2h` | |
| `DB_RESTORE_USER` | `songnhue_owner` | |
| `DB_RESTORE_PASSWORD` | ⛔ **để TRỐNG** | điền vào là tiến trình ứng dụng giữ mật khẩu chủ sở hữu lược đồ. Nút khôi phục trên UI sẽ báo "chưa cấu hình" (`ADM-2010`) — **đó là trạng thái đúng ở production**; khôi phục đi bằng `docs/runbook/khoi-phuc-du-lieu.md` |
| `LOG_FILE` | `/var/log/songnhue/app.log` | |
| `LOG_STRUCTURED_FORMAT` | `ecs` | JSON cho bộ thu thập tập trung |
| `LOG_MAX_HISTORY` / `LOG_TOTAL_SIZE_CAP` | `30` / `3GB` | trần cứng — **đĩa đầy thì `pg_dump` hỏng theo** |
| `HYDRO_API_KEY` | *(Công ty cấp — có thể để trống)* | chỉ là giá trị **mồi** cho lượt triển khai đầu. Nhà thật của mã số là cột `api_sources.credential` (AES-256-GCM), sửa trên màn hình *Nguồn dữ liệu*. ⚠ **dấu `;` cuối là một phần của giá trị** |
| `EXTERNAL_DOC_SYSTEM_URL` · `EXTERNAL_DOC_SYSTEM_ENABLED` · `GOOGLE_MAPS_API_KEY` | *(bỏ qua)* | ⚠ **không dòng mã nào đọc** — ba biến mồ côi, chờ chốt BOQ G5/G13 |

⛔ **Hai công tắc nới bảo mật — KHÔNG khai ở production, kể cả với giá trị `false`:**
`HYDRO_API_ALLOW_INTERNAL_HOST` (bật = nới SSRF cho `127.0.0.1`/`10.*`) và `HYDRO_API_MOCK` (bật =
nguồn GIẢ, và dữ liệu giả vào **cùng bảng** với dữ liệu thật). `HydroEnvSwitchTest` canh cả bốn tệp mẫu.

```bash
chmod 600 /opt/songnhue/.env
```

### ✅ Kiểm chứng mục 5 — **fail-fast có thật không**

```bash
cd /opt/songnhue
cp .env .env.bak && sed -i 's/^AES_KEY_V1=.*/AES_KEY_V1=/' .env
docker compose --env-file .env -f compose.prod.yml run --rm app 2>&1 | grep -i "AES_KEY_V1"
mv .env.bak .env
```

Phải thấy dòng báo thiếu `AES_KEY_V1` và tiến trình **không khởi động**. Nếu nó lên bình thường thì
`UnresolvedPlaceholderGuard` không chạy và cả bảng §5.5 vừa mất một nửa hiệu lực.

⚠ Lệnh trên cần `APP_IMAGE` đã export — chưa có thì compose sẽ dừng ở `Thiếu APP_IMAGE` trước khi kịp
kiểm. Chạy nó **sau** §8.2.

---

## 6. Cluster PostgreSQL — ⛔ **chốt được đúng MỘT lần, lúc `initdb` chạy**

### 6.1. Collation

`POSTGRES_INITDB_ARGS` nằm **ghi cứng** trong `compose.prod.yml` (không phải biến trong `.env`):

```
--encoding=UTF8 --locale-provider=icu --icu-locale=vi-VN --locale=C.UTF-8
```

Nó **chỉ có tác dụng ở lượt dựng volume đầu tiên**. Sau đó image bỏ qua vĩnh viễn — nên tệp cấu hình
và cluster thật có thể nói hai điều khác nhau **mãi mãi mà không lệnh nào báo sai**.

Đo ngày 26/8 trên chính `postgis/postgis:16-3.4`. Đúng phải là `Anh < Dung < Đăng < Em`:

| cluster dựng với | `ORDER BY` cho ra |
|---|---|
| ICU `vi-VN` | `Anh < Dung < Đăng < Em` ✅ |
| mặc định của image (glibc `en_US.utf8`) | `Anh < Đăng < Dung < Em` |
| locale `C` (so theo byte) | `Anh < Dung < Em < Đăng` |

Hỏng ở đây là **danh bạ nhân sự và mọi danh mục xếp sai thứ tự tiếng Việt**, ở mọi màn hình, vĩnh viễn.

**Hỏi cluster đang chạy, đừng đọc tệp compose:**

```bash
cd /opt/songnhue && ./postgres/kiem-collation.sh
```

Đạt thì in `i | collate=C.UTF-8 | icu=vi-VN`. Lệnh này chạy tự động ở smoke test câu **[1/4]** của
mọi lượt triển khai, và nó đứng **đầu** vì mọi câu sau chỉ có nghĩa trên một cluster đúng.

### 6.2. Sai rồi thì sửa thế nào — **có gián đoạn**

⛔ **Không sửa được bằng cách thêm dòng vào compose rồi deploy lại.** Đã đo: vá tệp rồi
`up -d --force-recreate` vẫn cho ra đúng collation cũ. `ALTER DATABASE` cũng không đổi được collation
của một cluster đã có dữ liệu.

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

Quy trình này **đã chạy thật** ngày 26/8 trên một cluster sai: dữ liệu giữ nguyên, thứ tự đổi đúng.

> ⛔ **Ô này chỉ ký được TRƯỚC khi có dữ liệu thật.** Sau khi Công ty đã nhập, sửa nó là một lượt
> ngừng dịch vụ có kế hoạch, làm ngoài giờ.

### 6.3. Bốn vai trò tách quyền

`deploy/postgres/init/10-bootstrap.sh` chạy **một lần** lúc `initdb`, tạo:

| Role | Dùng ở đâu | Đặc điểm |
|---|---|---|
| `songnhue_app` | ứng dụng lúc chạy | **không có DELETE** trên `audit_logs`, `security_events`, `hydro_raw_logs` — chỉ-ghi-thêm ở **tầng CSDL**, không chỉ ở mã |
| `songnhue_owner` | Flyway (`migrator`), khôi phục | sở hữu lược đồ |
| `songnhue_archiver` | job kết xuất audit | role **duy nhất** có DELETE `audit_logs` |
| `songnhue_readonly` | `pg_dump`, báo cáo | chỉ đọc |

Script gọi `require_env` cho `DB_PASSWORD`, `DB_MIGRATION_PASSWORD`, `DB_ARCHIVER_PASSWORD`,
`DB_READONLY_PASSWORD`. Thiếu một cái ⇒ container postgres exit 1 và **quay vòng khởi động lại** —
triệu chứng dễ đọc, đó là điều tốt.

### ✅ Kiểm chứng mục 6

```bash
cd /opt/songnhue
./postgres/kiem-collation.sh                    # → i | collate=C.UTF-8 | icu=vi-VN
docker compose --env-file .env -f compose.prod.yml exec -T postgres \
  psql -U postgres -d songnhue -c '\du'         # → thấy đủ 4 role
```

---

## 7. DNS · TLS · nginx biên

### 7.1. Bản ghi DNS

| Tên | Kiểu | Trỏ tới |
|---|---|---|
| `<ten-mien>` · `www` · `admin` · `files` | A | IP của **VPS-1** |
| `staging` · `admin-staging` · `files-staging` | A | IP của **VPS-2** |

Nếu dùng Cloudflare:

| Bản ghi | Chế độ |
|---|---|
| tên miền gốc, `www` | 🟠 **Proxy bật** — cache và chống ngập cho phần dân truy cập |
| `admin`, `files`, mọi `*-staging` | ⚪ **DNS-only** |

⛔ Lý do `admin` và `files` phải DNS-only: không cho **phiên quản trị** và **tệp nhân sự** đi vòng
qua hạ tầng đặt ở nước ngoài — vốn chính là điều đang tránh khi chọn đặt máy trong nước. Và về kỹ
thuật, proxy đứng trước `files` **phá chữ ký presigned** của MinIO, vì chữ ký ký cả tên máy.

📌 Giai đoạn dựng: dùng thẳng **DNS của nhà đăng ký** (ít mắt xích nhất, thử thách ACME không qua
proxy). Đổi sang Cloudflare **trước khi mở production**.

**Chờ DNS lan xong rồi mới chạy certbot** — đây là một trong những lỗi mất thời gian nhất, vì thông
báo lỗi của ACME không nói ra nguyên nhân:

```bash
for h in "" www. admin. files.; do
  printf '%-10s %s\n' "$h" "$(dig +short ${h}<ten-mien>)"
done
```

Cả bốn phải ra **đúng IP VPS-1**. Còn trống dòng nào thì **đợi**, đừng đi tiếp.

### 7.2. Cấp chứng chỉ lần đầu

Certbot cần cổng 80 trả lời được, mà nginx thì cần chứng chỉ mới khởi động — vòng tròn. Cắt bằng cách
chạy certbot **độc lập** một lần, khi chưa có gì chiếm cổng 80:

```bash
cd /opt/songnhue
CB="docker run --rm -p 80:80 -v /etc/letsencrypt:/etc/letsencrypt certbot/certbot certonly
    --standalone --non-interactive --agree-tos -m it@<ten-mien>"

$CB -d <ten-mien> -d www.<ten-mien>
$CB -d admin.<ten-mien>
$CB -d files.<ten-mien>
```

> ⚠ **Ba chứng chỉ riêng, không gộp một.** `default.conf.template` trỏ vào
> `/etc/letsencrypt/live/<từng-tên-miền>/`. Gộp hết `-d` vào một lệnh thì Let's Encrypt chỉ tạo
> **một** thư mục mang tên miền đầu, và hai server block còn lại chết với `cannot load certificate`.
> (`www` gộp chung với tên gốc là đúng — chúng ở **cùng một** server block.)

### 7.3. Kiểm cấu hình nginx **trước** khi bật

```bash
cd /opt/songnhue
docker compose --env-file .env -f compose.prod.yml run --rm --no-deps nginx nginx -t
```

> ⛔⛔ **`--no-deps` không phải để chạy nhanh hơn.** Thiếu nó thì `docker compose run` kéo theo cả
> chuỗi `depends_on` — ngày 7/9 lệnh "chỉ kiểm cấu hình" này đã dựng `postgres`, `minio`,
> `minio-init` và `app`, tức **thực hiện luôn lượt `initdb` duy nhất** ở ngoài §8.3 và không đi qua
> `kiem-collation.sh`. Lượt ấy may vì `compose.prod.yml` đã mang tham số collation đúng — nhưng cái
> cứu là tệp compose, không phải quy trình. Nếu đã lỡ chạy thiếu `--no-deps`: **đo collation ngay**,
> và nếu sai thì xoá volume `songnhue_postgres-data` dựng lại, lúc đó chưa có dữ liệu nên không mất gì.

> ⚠ Phải để lệnh bắt đầu bằng `nginx`. Chạy `sh -c 'nginx -t'` thì entrypoint của image **không
> chạy**, `envsubst` không thay biến, và `nginx -t` sẽ kiểm tệp mặc định của image chứ không phải tệp
> của ta — **báo xanh trên một thứ không liên quan**.

### 7.4. Gia hạn tự động

Sau khi stack đã chạy, dùng webroot để không phải dừng nginx.

⚠ **`crontab` không có sẵn** trên bản Ubuntu 24.04 của VPS — đo 7/9: `command not found` trên **cả
hai** máy chủ. Cài trước, rồi mới đặt lịch:

```bash
sudo apt-get install -y cron && sudo systemctl enable --now cron
systemctl is-active cron && systemctl is-enabled cron    # phải: active · enabled
crontab -e
```
```cron
17 3 * * 1 cd /opt/songnhue && docker compose --env-file .env -f compose.prod.yml --profile certbot run --rm certbot renew --webroot -w /var/www/certbot --quiet && docker compose --env-file .env -f compose.prod.yml exec nginx nginx -s reload
```

**Kiểm chứng ngay, đừng đợi 60 ngày nữa mới biết:**

```bash
cd /opt/songnhue
docker compose --env-file .env -f compose.prod.yml --profile certbot \
  run --rm certbot renew --webroot -w /var/www/certbot --dry-run
```

⚠ Khối `location ^~ /.well-known/acme-challenge/` phải đứng **trước** lệnh chuyển hướng sang HTTPS —
nó đã đứng đúng chỗ trong template. Đảo thứ tự là certbot bị đẩy sang HTTPS và **không gia hạn được**,
hỏng âm thầm cho tới đúng ngày hết hạn.

### 7.5. Những gì nginx đã lo sẵn — để không ai "tối ưu" mất

| Việc | Ở đâu |
|---|---|
| Gõ thẳng IP → **từ chối ở tầng TLS** | server block 443 mặc định, `ssl_reject_handshake on` |
| Chặn `/swagger-ui/` và `/v3/api-docs/` | `snippets/chan-tai-lieu-api.conf`, include vào **cả** khối public lẫn admin |
| HSTS, CSP, X-Frame-Options, X-Robots-Tag | `snippets/edge-headers.conf` (`preload` của HSTS **cố ý chưa bật**) |
| Hạn mức chống ngập | `api_general 30r/s` · `api_auth 20r/m` (chỉ áp cho `/auth/login|refresh|2fa`) · `limit_conn per_ip 50` |
| Tải tệp không bị chặn ở biên | `client_max_body_size 0` trong `/api/` và trên khối `files` — hạn mức thật nằm ở bảng `settings` có UI sửa |
| `resolver` + biến trong `proxy_pass` | **bắt buộc**: viết thẳng `proxy_pass http://app:8080` thì nginx phân giải lúc nạp cấu hình, backend chưa lên là nginx **từ chối khởi động** và cả trang trắng |
| TLS 1.2 giữ lại | **có chủ đích** — bỏ là khoá cửa với Windows 10 bản cũ, Android < 10 và một phần máy trạm cơ quan nhà nước |

⛔ **Chưa có nén (gzip/brotli) ở bất kỳ đâu** — đã kiểm toàn bộ `deploy/nginx/` và hai image FE: 0 dòng
cấu hình nén. Ghi ra đây để không ai tưởng đã có khi đo DOD1.17 (trang chủ < 3s).

📌 **Có sẵn nhưng đang tắt**: giới hạn giao diện quản trị theo dải IP nằm sẵn trong template, đang bị
chú thích, chờ Công ty cấp dải IP cố định (BOQ G13). ⛔ Đừng bật khi chưa có dải IP thật — tự khoá
mình ra ngoài giao diện quản trị của production là sự cố có thật, và cách chữa là SSH vào máy.

### ✅ Kiểm chứng mục 7

```bash
D=<ten-mien>
curl -sI https://$D/ | head -1                                  # → 200
curl -sI https://admin.$D/ | grep -iE "strict-transport|content-security|x-frame|x-robots"
curl -sI https://$D/ | grep -i x-robots-tag                     # → all   (staging mới là noindex)
curl -s -o /dev/null -w '%{http_code}\n' https://$D/swagger-ui/ # → 404
curl -s -o /dev/null -w '%{http_code}\n' https://$D/v3/api-docs # → 404
curl -sk https://<IP-VPS1>/ -o /dev/null -w '%{http_code}\n'    # → 000 (đóng ở tầng TLS)
```

---

## 8. Lượt dựng đầu tiên — bằng tay, đúng một lần

Làm tay lần đầu để **thấy từng bước**; từ lượt sau CI làm hộ. Và quan trọng hơn: lượt CD đầu tiên
không phải chỗ để phát hiện thiếu một thư mục.

### 8.1. Đẩy cấu hình lên máy chủ — đúng thứ CI sẽ làm

```bash
# Trên MÁY CỦA ANH, tại gốc kho
rsync -az --delete \
  --exclude '.env' --exclude 'env/' --exclude 'keys/' \
  --exclude 'compose.local.yml' --exclude 'compose.infra.yml' \
  deploy/ songnhue@<IP-VPS1>:/opt/songnhue/

ssh songnhue@<IP-VPS1> \
  'find /opt/songnhue -maxdepth 2 \( -name keys -prune \) -o -name "*.sh" -exec chmod +x {} +'
```

⚠ `-prune` cho `keys/` là **bắt buộc**, không phải cho gọn: thư mục ấy là 700 của uid 1000, còn lượt
`ssh` này chạy dưới user triển khai — `find` không đọc được nó, in `Permission denied` và **thoát 1**.

⛔ **Đừng bao giờ sửa tay các tệp trong `/opt/songnhue/` (trừ `.env` và `keys/`).** Lượt deploy sau
`rsync --delete` sẽ xoá mất. Sửa trong kho rồi đi theo đúng luồng.

### 8.2. Chọn image

⛔ **Chỉ dùng tag di động ở lượt dựng tay này.** Từ lượt sau **luôn là digest** — tag là một cái tên,
và tên thì gán lại được.

```bash
# Trên VPS-1
cd /opt/songnhue
export APP_IMAGE=ghcr.io/team-dev-qnt/songnhue/app:staging
export ADMIN_IMAGE=ghcr.io/team-dev-qnt/songnhue/admin-app:staging
export PUBLIC_IMAGE=ghcr.io/team-dev-qnt/songnhue/public-web:staging
```

📌 Dùng tag `:staging` (bản đã chạy thật ở staging) chứ không phải `:dev`. `deploy.yml` gắn tag môi
trường **sau khi smoke test xanh**, nên `:staging` là "bản gần nhất đã qua nghiệm thu".

⚠ Ba biến này **cố ý không nằm trong `.env`** — chúng khai `${…:?}` trong compose, nên **mọi lệnh
`docker compose` gõ ngoài lượt deploy đều hỏng nếu quên export**, kể cả lệnh chỉ đọc. Đó là thiết kế:
nó ngăn việc đóng đinh một phiên bản lên đĩa máy chủ.

### 8.3. Dựng theo đúng thứ tự

```bash
cd /opt/songnhue
dc="docker compose --env-file .env -f compose.prod.yml"

$dc pull
$dc up -d postgres minio            # dựng volume — đây là lượt initdb DUY NHẤT
sleep 30
./postgres/kiem-collation.sh        # ⛔ DỪNG nếu không in ✓ — sau bước sau là quá muộn

$dc run --rm minio-init             # tạo bucket + tài khoản dịch vụ, tự đo ghi→đọc→xoá
$dc run --rm migrator               # ← PHẢI thoát mã 0
$dc up -d app admin-app public-web nginx
$dc ps
```

⚠ `migrator` chạy `run --rm`, tức **đồng bộ và trả mã thoát**. Migration hỏng là lượt dựng dừng
**ngay**, chưa có gì bị thay. Đó cũng là lý do đường quay lui tự động của CD chưa bao giờ được chứng
minh đầy đủ (§13.3).

### 8.4. `.env` chưa từng được kiểm — kiểm bây giờ

Chạy lệnh kiểm chứng fail-fast ở **§5 phần kiểm chứng** (giờ đã có `APP_IMAGE` nên nó chạy được).

### 8.5. `superadmin` — và **xoá mật khẩu mồi ngay sau đó**

1. Mở `https://admin.<ten-mien>`, đăng nhập bằng `superadmin` + `BOOTSTRAP_ADMIN_PASSWORD`.
2. **Đổi mật khẩu** và **bật 2FA**.
3. Xoá dòng ấy khỏi `.env` rồi dựng lại `app`:

```bash
cd /opt/songnhue
sed -i 's/^BOOTSTRAP_ADMIN_PASSWORD=.*/BOOTSTRAP_ADMIN_PASSWORD=/' .env
docker compose --env-file .env -f compose.prod.yml up -d --force-recreate app
```

> ⛔⛔ **Đừng viết chú thích ở cuối dòng ấy.** Compose **không cắt chú thích** khi giá trị rỗng, nên
> `BOOTSTRAP_ADMIN_PASSWORD=   # đã xoá` làm mật khẩu `superadmin` trở thành **chính đoạn chú thích
> đã commit lên repo**. `EnvFileCommentTest` canh việc này trong các tệp mẫu, nhưng nó **không nhìn
> thấy** `/opt/songnhue/.env` trên máy chủ.
>
> Giá trị rỗng ⇒ `AdminBootstrapRunner` thoát ngay, không tạo lại tài khoản. Đó là trạng thái đúng.

### 8.6. Bật lịch sao lưu

Vào **Cấu hình hệ thống** (`/quan-tri/cau-hinh`), bật `backup.schedule-enabled`. Job chạy **02:00 giờ
VN** hằng đêm — giờ này là hằng số trong mã, không sửa được trên UI. Hai tham số sửa được:
`backup.retention-days` (mặc định 30) và `backup.stale-hours` (mặc định 26).

⚠ Khi tắt, mỗi đêm ứng dụng ghi `Sao lưu tự động đang TẮT … đêm nay không có bản sao lưu nào`. Màn
hình *Sao lưu & khôi phục* (`/quan-tri/sao-luu`) nói thẳng: *"Hệ thống đang chạy không có lưới an toàn."*

---

## 9. Nghiệm thu lượt dựng đầu — **mười phép, làm đủ**

Năm phép đầu vẫn xanh trọn vẹn khi `MINIO_ENDPOINT` sai. Phép 9 là phép **duy nhất** phân biệt được.

```bash
D=<ten-mien>

# 1. Collation — làm trước, vì mọi phép sau chỉ có nghĩa trên một cluster đúng
ssh songnhue@<IP-VPS1> 'cd /opt/songnhue && ./postgres/kiem-collation.sh'

# 2. Đi hết chặng nginx → public-web → app → postgres
curl -fsS https://$D/api/v1/public/site-config | head -c 120
#    → phải thấy '"success":true'

# 3. ⚠ PHẢI có Origin. curl trần không preflight nên nó đi lọt qua đúng bức tường
#    chặn người dùng thật — CORS đã chặn cả giao diện quản trị suốt WS-8→WS-20.
curl -si -X OPTIONS https://admin.$D/api/v1/auth/login \
     -H "Origin: https://admin.$D" \
     -H "Access-Control-Request-Method: POST" | head -1
#    → 403. ĐÚNG, không phải hỏng: `admin-app` có nginx nội bộ chuyển tiếp `/api` sang `app` cùng
#      origin, nên trình duyệt KHÔNG BAO GIỜ preflight và OPTIONS lạ bị từ chối. Đo 7/9: staging và
#      production cho CÙNG 403. Phép phân biệt thật là POST thẳng — cả hai trả 401:
curl -s -o /dev/null -w '%{http_code}\n' -X POST https://admin.$D/api/v1/auth/login \
     -H "Origin: https://admin.$D" -H "Content-Type: application/json" \
     -d '{"username":"khong-ton-tai","password":"x"}'      # → 401

# 4. Header bảo mật có mặt trên CẢ HAI tên miền, không chỉ một
for h in "$D" "admin.$D"; do
  echo "— $h"; curl -sI "https://$h/" | grep -iE "strict-transport|content-security|x-frame|x-robots"
done

# 5. Production PHẢI được đánh chỉ mục
curl -sI https://$D/ | grep -i x-robots-tag          # → all

# 6. Gõ thẳng IP phải bị từ chối ở tầng TLS
curl -sk https://<IP-VPS1>/ -o /dev/null -w '%{http_code}\n'   # → 000

# 7. Tài liệu API không ra ngoài
for p in /swagger-ui/ /v3/api-docs; do
  printf '%-14s %s\n' "$p" "$(curl -s -o /dev/null -w '%{http_code}' https://$D$p)"
done                                                  # → 404 cả hai

# 8. ⛔ Cổng KHÔNG được có bài của bộ seed
curl -fsS "https://$D/api/v1/public/articles?page=0&size=100" \
  | grep -o '"source":"http[^"]*"' | head
#    → RỖNG. Có dòng nào là SEED_LOCATION đã bị điền — xem §5.4, dừng lại và đọc

# 9. ⭐ TẢI VỀ được một tệp đính kèm — phép DUY NHẤT chứng minh MINIO_ENDPOINT đúng
#    Đăng nhập giao diện quản trị, tải LÊN một ảnh, rồi bấm Tải về.
#    Hoặc, nếu đã có bài có ảnh bìa:
id=$(curl -fsS "https://$D/api/v1/public/articles?page=0&size=100" \
     | sed -n 's/.*"coverAttachmentPublicId": *"\([0-9a-f-]\{36\}\)".*/\1/p' | head -1)
[ -n "$id" ] && curl -fsS -o /dev/null -w '%{content_type}\n' "https://$D/api/v1/public/files/$id"
#    → image/…    (rỗng $id nghĩa là CHƯA kiểm được, không phải đã đạt)

# 10. Gia hạn chứng chỉ
ssh songnhue@<IP-VPS1> 'cd /opt/songnhue && docker compose --env-file .env -f compose.prod.yml \
   --profile certbot run --rm certbot renew --webroot -w /var/www/certbot --dry-run'
```

> ⛔ **Phép 9 không bỏ được**, và **phép 8 cũng vậy**. Phép 8 là thứ duy nhất phân biệt "cổng trống vì
> Công ty chưa đăng bài" với "cổng trống vì migration seed vừa `DELETE FROM articles`".

---

## 10. Nối GitHub — environment, secret, variable, bảo vệ nhánh

⚠ Kho **không có sẵn** lệnh nào để đặt secret (`grep "gh secret set"` → 0 kết quả). Mục này là bản
soạn mới; chạy xong thì **đo lại**, đừng tin lệnh đã chạy là đã đúng.

### 10.1. Năm secret của environment `production`

| Secret | Giá trị | Lấy ở đâu |
|---|---|---|
| `PROD_HOST` | IP công khai của VPS-1 | nhà cung cấp |
| `PROD_USER` | `songnhue` | user tạo ở §2.2 |
| `PROD_SSH_KEY` | **nội dung** khoá riêng `~/.ssh/songnhue_deploy` | §2.1 |
| `PROD_BASE_URL` | `https://<ten-mien>` | tên miền đã mua |
| `PROD_SSH_KNOWN_HOSTS` | **một dòng** `<PROD_HOST> ssh-ed25519 AAAA…` | §3.3 |

```bash
R=team-dev-qnt/songnhue

gh secret set PROD_HOST      --env production --repo "$R" --body '203.0.113.10'
gh secret set PROD_USER      --env production --repo "$R" --body 'songnhue'
gh secret set PROD_SSH_KEY   --env production --repo "$R" < ~/.ssh/songnhue_deploy
gh secret set PROD_BASE_URL  --env production --repo "$R" --body 'https://<ten-mien>'
gh secret set PROD_SSH_KNOWN_HOSTS --env production --repo "$R" \
  --body '203.0.113.10 ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA…'

# Đo lại — đây mới là bằng chứng
gh api "repos/$R/environments/production/secrets" \
  --jq '"\(.total_count) secret: " + ([.secrets[].name] | join(", "))'
```

Phải in ra **5 secret**.

> ⛔ **Đặt đúng cấp, không chỉ đúng tên.** Secret của environment **chỉ đến được job có khai
> `environment:`**. Lần đầu `NVD_API_KEY` bị đặt nhầm vào environment `staging` và phép quét CVE bị
> bỏ qua trong im lặng — `secrets.NVD_API_KEY` giải ra chuỗi rỗng, **không có lỗi nào**.
> Quy tắc: **khoá của công cụ CI đặt ở cấp repo; chỉ khoá gắn với một môi trường triển khai mới đặt
> ở environment.**
>
> ⚠ `PROD_SSH_KNOWN_HOSTS` **không bí mật về mặt mật mã** (ai kết nối cũng thấy khoá công khai của máy
> chủ), nhưng để ở secret vì nó chứa địa chỉ máy chủ — thứ `PROD_HOST` đã cố ý không phơi ra log.

### 10.2. ⛔ Ba trạng thái của cổng secret — production **DỪNG ĐỎ**

Cùng một cổng `.github/scripts/kiem-secret-may-chu.sh`, duyệt **cả năm** biến và kiểm **giá trị đã
giải** (nên "chưa đặt" và "chuỗi rỗng" cùng bị tính là thiếu):

| tình trạng | staging | production |
|---|---|---|
| đủ **năm** | đi tiếp | đi tiếp |
| thiếu **một số** (1–4) | ⛔ đỏ | ⛔ đỏ |
| thiếu **cả năm** | cảnh báo + bỏ qua | ⛔ **đỏ** |

Vì sao lệch nhau: CD Staging chạy **tự động** sau mỗi lượt merge, nên một môi trường chưa dựng mà
nhuộm đỏ cả dòng CI của mọi người là đổi một lỗi thật lấy một lỗi phiền. CD Production chỉ chạy khi
**có người bấm** — và người ấy đang chờ biết đã deploy được hay chưa; **im lặng bỏ qua là câu trả lời
sai nhất**.

⛔ Bản trước chỉ hỏi **một** biến (`HOST`) và luôn bỏ qua trong im lặng. Với environment `production`
rỗng — đúng trạng thái đo được — một lượt CD Production sẽ **xanh trọn vẹn mà không byte nào chạm máy
chủ** (§10.57). Nay `SecretGateTest` chạy thật script với từng tổ hợp.

> ⚠ **`docs/cicd.md` §7.1 vẫn ghi "đủ bốn / thiếu cả bốn"** — con số ấy lạc hậu từ 29/8 khi
> `SSH_KNOWN_HOSTS` vào bộ. Ai đặt 4 secret theo bảng đó sẽ gặp cổng **đỏ**, không phải "đi tiếp".

### 10.3. Biến kho `PUBLIC_SITE_URL` — **variable, không phải secret**

```bash
gh variable set PUBLIC_SITE_URL --repo team-dev-qnt/songnhue --body 'https://<ten-mien>'
gh api repos/team-dev-qnt/songnhue/actions/variables --jq '.variables[] | "\(.name)=\(.value)"'
```

⚠ Đây là **biến**, không phải bí mật: nó đi vào bundle mà cả thế giới tải về được. Để nhầm vào
Secrets thì vẫn chạy, nhưng bị che trong log — và che một giá trị công khai chỉ làm việc gỡ lỗi khó
hơn mà không thêm an toàn nào. (Environment `staging` **đang** có một `PUBLIC_SITE_URL` đặt sai loại
như vậy; nó là rác, không workflow nào đọc.)

⛔⛔ **Đặt biến này TRƯỚC lượt build image sẽ lên production.** `NEXT_PUBLIC_SITE_URL` **nướng vào
bundle lúc build**, và luồng đề bạt cố ý dùng **chung một image** cho cả hai môi trường. Đo 26/8:
`sitemap.xml` của staging trả `<loc>http://localhost:3000</loc>`, canonical trang chủ cũng vậy — và
**đúng image đó là image sẽ lên production**. Đặt biến xong thì phải có **một lượt build mới** trên
`dev` rồi đề bạt lại; sửa biến không làm đổi image đã dựng.

### 10.4. Environment `production`

Đã tạo 15/8/2026 với required reviewer `Toclac18`. Kiểm lại:

```bash
gh api repos/team-dev-qnt/songnhue/environments/production \
  --jq '{reviewers: [.protection_rules[]?.reviewers[]?.reviewer.login],
         self_review: [.protection_rules[]?.prevent_self_review],
         branch_policy: .deployment_branch_policy,
         admin_bypass: .can_admins_bypass}'
```

| Mục | Trạng thái đo được 4/9 | Ghi chú |
|---|---|---|
| required reviewers | ~~`Toclac18`~~ → **đã gỡ 6/9/2026** | Lượt deploy không dừng chờ ai nữa. Chốt của con người dời về bước **duyệt PR** `staging → production` (nhánh vẫn đòi 1 người duyệt) |
| `deployment_branch_policy` | custom, đúng nhánh `production` | ⛔ **Thêm CÙNG LƯỢT với việc gỡ reviewer.** Gỡ một mình là mở `PROD_*` cho mọi nhánh: `workflow_dispatch` chạy tệp của nhánh người dùng chọn, và job mang `environment: production` nhận đủ 5 secret |
| `prevent_self_review` | `false` | cố ý khi đội một người — **bật khi có người thứ hai thật** |
| `can_admins_bypass` | `true` | cùng lý do |
| `deployment_branch_policy` | `null` = **mọi nhánh** | lớp chặn tương đương nằm chỗ khác: `deploy-prod.yml` bắt commit phải là tổ tiên `origin/staging` |
| wait timer | không đặt | — |

### 10.5. Bảo vệ nhánh `production`

Trạng thái **đo được 4/9**: 1 check bắt buộc `Promotion guard` · `strict: false` · **1** approval ·
`dismiss_stale_reviews: true` · `require_last_push_approval: true` · `required_linear_history: false`
· cấm force-push và xoá nhánh · `required_conversation_resolution: true`.

⛔⛔ **ĐỪNG chạy lại khối `PUT` toàn bộ ở `branch-protection.md` §4.2.** Nó đặt
`required_approving_review_count: 0`, tức sẽ **hạ số người duyệt từ 1 xuống 0** mà không ai nhận ra.
`PUT` trên endpoint này ghi đè **toàn bộ object**, nên ghi hụt một trường là **xoá âm thầm** phần
bảo vệ khác. Muốn sửa một trường thì `PATCH` đúng trường ấy:

```bash
# ví dụ: chỉ sửa danh sách context, không đụng gì khác
gh api -X PATCH repos/team-dev-qnt/songnhue/branches/production/protection/required_status_checks \
  -F strict=false -f 'contexts[]=Promotion guard'
```

⚠ `-F` chứ không `-f` cho boolean: `-f` gửi chuỗi `"true"` → 422 `not a boolean`.

**Vì sao `strict: false`**: `strict: true` tạo vòng khoá không lối ra — lượt đề bạt đầu sinh một merge
commit không có trong `dev`, lần sau GitHub báo *out of date*, và nút **Update branch** ở cả hai chế
độ đều bị chính bảo vệ của `dev` chặn.

**Đo lại toàn bộ sau khi đụng vào bất cứ thứ gì:**

```bash
for b in dev staging production; do
  echo "— $b"
  gh api "repos/team-dev-qnt/songnhue/branches/$b/protection" --jq \
    '{strict: .required_status_checks.strict,
      contexts: .required_status_checks.contexts,
      reviews: .required_pull_request_reviews.required_approving_review_count,
      linear: .required_linear_history.enabled,
      force_push: .allow_force_pushes.enabled}'
done
gh api repos/team-dev-qnt/songnhue/rulesets      # phải [] — không có luật kiểu mới chồng lên
```

---

## 11. Luồng CI/CD production

### 11.1. Hình dạng

```
nhánh feature ──Squash──► dev ──merge commit──► staging ──merge commit──► production
                           │                      │                          │
                      Cổng kiểm CI          CD Staging tự chạy     CD Production tự chạy
                      (dựng 3 image                                (giải SHA theo cây tệp,
                       theo SHA)                                    dump, deploy, smoke test)
```

> ⭐ **Đổi 6/9/2026 (T11.86).** Trước đó mũi tên cuối là *"Actions → nhập SHA → người duyệt bấm"*.
> Lý do đổi: `CD Production` có **0 lượt chạy** trong toàn bộ lịch sử kho, trong khi `staging` đã đi
> qua 30 lượt. Một chốt an toàn không ai bấm không bảo vệ được gì.

**Hai nguyên tắc, và chúng là một**: mọi phép kiểm nặng chạy ở chặng `dev`; hai chặng sau **chỉ triển
khai, không kiểm lại và không build lại**. Image được đóng gói **một lần** ở CI của `dev`, gắn thẻ
theo commit SHA, rồi đề bạt **nguyên vẹn** qua staging tới production.

### 11.2. Kiểu merge — sai ở đây thì hỏng **im lặng**

| PR | Kiểu merge bắt buộc | Vì sao |
|---|---|---|
| nhánh feature → `dev` | **Squash** hoặc **Rebase** | `dev` bật `required_linear_history` — merge commit bị chặn |
| `dev` → `staging` → `production` | **Create a merge commit** | squash sinh SHA mới, **cắt đứt liên kết với image đã kiểm** và làm **gãy gốc chung** của nhánh |

⛔⛔ **Bẫy squash đã sập thật, và câu "làm sai thì hỏng to tiếng" đã hết đúng.** PR đề bạt #72 bị
squash lúc 31/8 23:53; CD Staging ngay sau đó **xanh** — bản vá §10.42 đã đổi một lần *dừng hẳn* lấy
một dòng `::warning::` trên lượt chạy xanh. Chuông kêu đúng nguyên nhân lúc 23:54:54 và **trôi qua**.
Hai ngày sau nó thành **13 tệp xung đột giả** trên PR #76 — và xung đột giả khiến GitHub không dựng
được `refs/pull/N/merge`, nên **`Promotion guard`, cổng bắt buộc duy nhất của `staging`, không bao giờ
được lên lịch**: treo ở *"Expected"*, không một dòng đỏ nào để đọc.

📌 **Một cổng kiểm KHÔNG CHẠY không đọc như một cổng kiểm ĐỎ, và không có gì đứng ra báo sự vắng mặt.**

Bất biến nay đo được, chạy sau mỗi lượt đề bạt:

```bash
bash .github/scripts/kiem-goc-chung.sh origin/staging origin/production
# → "production hơn staging: N commit, trong đó 0 commit không-phải-merge"
```

Ngưỡng là **0 commit không-phải-merge**. Merge commit thì được — chúng chính là các lượt đề bạt trước.

### 11.3. `Promotion guard` — check bắt buộc **duy nhất**

Chạy ở `pull_request` vào `staging` và `production`, ~5 giây, kiểm **ba** bất biến:

1. **Nhánh nguồn đúng chặng trước** — `dev` → staging, `staging` → production. GitHub **không có** tuỳ
   chọn "chỉ nhận PR từ nhánh X"; thiếu job này thì ai cũng mở được PR từ một nhánh feature thẳng vào
   production, và không check nặng nào chặn lại vì ta đã cố ý không yêu cầu chúng ở đó.
2. **Đúng commit đang đề bạt đã xanh CI** — tra qua API check-runs của **chính SHA đó**, không phải
   "nhánh `dev` nói chung đang xanh". Chờ tối đa **10 phút**; `conclusion: null` (đang chạy) là một
   nhánh **riêng**, không đọc thành "hỏng"; và **HỎNG thắng CHƯA XONG**.
3. **Nhánh đích không có commit riêng** — bất biến ở §11.2.

### 11.4. Đưa một commit lên production — chín bước

1. **Nhánh feature → `dev`**: chờ context `Cổng kiểm CI` xanh, gộp bằng **Squash**.
2. **Mở PR `dev → staging`**. Chờ `Promotion guard` xanh. Vừa gộp xong một PR khác vào `dev` thì cổng
   có thể hiện "đang chạy" — **chờ, đừng chạy lại vội**.
3. **Gộp bằng "Create a merge commit"** — nút giữa, không phải Squash.
4. `CD Staging` chạy **tự động**. Đợi xanh **và nghiệm thu trên site thật**.
5. **Mở PR `staging → production`**, chờ `Promotion guard` xanh, **gộp bằng "Create a merge commit"**.
   ⛔ **Bước này CHÍNH LÀ lượt deploy** (đổi 6/9/2026). Trước đó nó không làm gì cả và còn ba bước
   bấm tay nữa; nay merge xong là `CD Production` chạy ngay.
6. Theo dõi lượt chạy. §11.5 nói từng bước làm gì.

⛔ **Đưa lên production vẫn là một quyết định vận hành có thời điểm của nó** — ngoài giờ hành chính,
sau khi đã báo Công ty. Nhưng thời điểm ấy nay là **lúc bấm nút Merge**, không phải một bước bấm
riêng sau đó. Chốt của con người nằm ở chỗ duyệt PR (`production` vẫn đòi **1 người duyệt**), không
nằm ở environment nữa.

### 11.4-b. Quay lui — đây là việc còn lại của `workflow_dispatch`

1. **Actions → CD Production → Run workflow**.
2. ⚠ *Use workflow from* = nhánh **`production`**. Chọn `dev` sẽ bị `deployment_branch_policy` từ
   chối — đó là ràng buộc thay cho required reviewer đã gỡ (§10.4).
3. `commit_sha` = một SHA trên `dev` **cũ hơn**, vẫn phải là tổ tiên của `origin/staging`. Để trống
   thì triển khai lại đúng thứ đang ở đỉnh `production`.
4. `reason` bắt buộc — nó là thứ tạo ra nhật ký cho lượt bấm tay.

```bash
# tìm SHA đang chạy và các bản trước nó
git fetch origin staging && git log --oneline -10 origin/staging
```

### 11.5. Lượt CD Production làm gì — mười ba bước

Job đầu, `Mốc đề bạt đã qua staging chưa`, chạy đúng một phép:

```bash
git merge-base --is-ancestor "$sha" origin/staging
```

Nó chặn đúng cái sai nguy hiểm nhất của deploy thủ công: **gõ nhầm một SHA chưa bao giờ chạy ở
staging**. Không có bước này thì "manual deploy" nghĩa là "ai gõ gì cũng lên được".

Rồi thân chung `deploy.yml` (dùng chung với staging) chạy:

| # | Bước | Làm gì · điều đáng biết |
|---|---|---|
| 1 | **Đăng nhập GHCR** | phải trước mọi lượt tra image — gói GHCR mặc định **riêng tư** kể cả khi repo công khai |
| 2 | **Xác định image** | tra `manifest inspect` cho cả ba image theo tag SHA, lấy **digest** `sha256:…`. Triển khai theo digest, **không** theo tag |
| 3 | **Kiểm cấu hình máy chủ** | cổng secret §10.2. Mọi bước sau gác bằng `if: ready == 'true'` |
| 4 | **Mở đường SSH** | ghim `known_hosts` từ secret, **0 kết nối dò**; một kết nối `ControlMaster` dùng cho **cả lượt**; thử 6 lần có giãn cách; sai khoá thì **dừng ngay** thay vì thử lại vô ích |
| 5 | **Đồng bộ cấu hình** | `rsync -az --delete deploy/ → /opt/songnhue/`, **trừ** `.env`, `env/`, `keys/`. Đây là thứ ngăn một kiểu trôi hoàn toàn câm: sửa CSP, thêm service — tất cả nằm trong repo còn máy chủ vẫn chạy bản chép tay từ ngày dựng |
| 6 | **`pg_dump` trước khi triển khai** | `predeploy-<db>-<stamp>.dump` + `.sha256`, **điểm quay lui duy nhất về dữ liệu**. Không có nhánh "cảnh báo rồi đi tiếp": chụp hỏng ⇒ **không deploy** |
| 7 | **Ghi lại bản đang chạy** | `docker inspect` ba container; chỉ tin khi đủ **3 dòng**. Thiếu ⇒ `co_du=false` ⇒ **lượt này không có quay lui tự động** (đúng trường hợp lượt deploy đầu tiên) |
| 8 | **Triển khai** | `pull` → `run --rm migrator` → `up -d --force-recreate` → **đo lại image ID của container đang chạy** và so với digest đã khai |
| 9 | **Smoke test** | bốn câu, §11.6 |
| 10 | **Quay lui bản cũ** | chỉ khi `failure()` **và** có bản cũ. §13 |
| 11 | **Gắn tag `production`** | chỉ **sau khi** smoke test xanh |
| 12 | **Ghi tóm tắt** | `always()` — trạng thái, commit, ba digest, đích quay lui, và lời nhắc migration một chiều |

⚠ **`--force-recreate` là mệnh lệnh, không phải gợi ý.** Lượt 25/8 pull đúng digest mới về đĩa, rồi
compose in `Container songnhue-app Running` và **giữ nguyên container cũ** — mã cũ chạy tiếp trong khi
workflow tổng kết "đã triển khai digest 9c9f18e9". Bản vá nằm trong image, image nằm trên đĩa, và
không có gì nối hai thứ đó lại. Bước đo lại image ID ở #8 sinh ra để đóng lỗ ấy.

⚠ **Mọi khối lệnh từ xa đi qua `.github/scripts/chay-tu-xa.sh`**, chạy từ **tệp** với stdin là
`/dev/null`. Ngày 27/8 `docker compose run --rm migrator` **nuốt trọn** phần script còn lại (khi ấy
được nuôi vào bash-từ-xa qua stdin), nên `up -d --force-recreate` và cả bước đo lại image ID **không
hề chạy** — mà CD vẫn báo `success`. `-T` **không** chữa được: nó chỉ tắt TTY, stdin vẫn gắn.

### 11.6. Smoke test — bốn câu

| # | Câu hỏi | Đo bằng |
|---|---|---|
| 1 | Cluster có **đúng collation ICU vi-VN**? | `./postgres/kiem-collation.sh` trên máy chủ. Đứng đầu vì sai thì **không sửa được bằng deploy lại** |
| 2 | Đi hết chặng nginx → public-web → app → postgres? | `curl $BASE_URL/api/v1/public/site-config` chờ `"success":true`, lặp 30 lần × 10s = **5 phút** |
| 3 | Cổng **có nội dung**? | đếm `"slug":` trong `/api/v1/public/articles`, so với `so_bai_toi_thieu` = **1** ở production |
| 4 | MinIO **có byte**? | lấy `coverAttachmentPublicId` **từ chính phản hồi câu 3** rồi hỏi content-type. Không có ảnh nào ⇒ in **BỎ QUA có ghi rõ**, không in ✓ |

⚠ Câu 3 ở production chỉ khẳng định được điều tối thiểu — *cổng không rỗng* — vì production **không
chạy bộ seed**. Khi Công ty đã đăng đủ nội dung khởi tạo thì **nâng `so_bai_toi_thieu` trong
`deploy-prod.yml` lên**, và nó mới thành một phép kiểm thật.

⚠ Câu 4 không phải phép kiểm đầy đủ của MinIO: nó chỉ chạy khi có bài **có ảnh bìa**. Phép 9 ở §9 vẫn
là phép duy nhất chứng minh `MINIO_ENDPOINT` đúng.

### 11.7. ⛔ Cách phát hiện một lượt **xanh giả**

Dự án đã gặp cả ba dạng dưới đây trên môi trường thật. Sau lượt CD Production **đầu tiên**, đo độc
lập — **đừng đọc lại lời của workflow**:

```bash
# 1. Container có THẬT SỰ bị thay không, và có đang chạy đúng ảnh vừa triển khai không
ssh songnhue@<IP-VPS1> '
  for c in songnhue-app songnhue-admin-app songnhue-public-web; do
    printf "%-24s created=%s  image=%s  health=%s\n" "$c" \
      "$(docker inspect -f "{{.Created}}" $c)" \
      "$(docker inspect -f "{{.Image}}" $c | cut -c1-19)" \
      "$(docker inspect -f "{{.State.Health.Status}}" $c 2>/dev/null)"
  done'
```
`created` phải **nằm trong cửa sổ lượt deploy**. Nằm ngoài = container không được thay (§10.53/§10.60).

```bash
# 2. Migration có áp đúng thứ tự, và có cái nào thất bại không
ssh songnhue@<IP-VPS1> 'cd /opt/songnhue && docker compose --env-file .env -f compose.prod.yml \
  exec -T postgres psql -U songnhue_readonly -d songnhue -c \
  "SELECT version, success, installed_on FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5"'
```

```bash
# 3. Bản chụp trước triển khai có sinh ra tệp THẬT không
ssh songnhue@<IP-VPS1> 'ls -lh /var/lib/songnhue/backup/predeploy-*.dump | tail -3'
```

⛔ **Nếu lượt CD Production xanh mà log không có một dòng SSH nào — nghi ngay xanh giả** và đối chiếu
bằng ba lệnh trên. Đó đúng là hình dạng của §10.57.

---

## 12. Sao lưu — bốn bản, mỗi bản trả lời một câu hỏi khác nhau

| Bản | Ở đâu | Trả lời câu hỏi | Kích hoạt |
|---|---|---|---|
| Đêm 02:00 | VPS-1, `/var/lib/songnhue/backup` | "xoá nhầm bảng lúc chiều" | job **trong ứng dụng**, cron `0 0 2 * * *` giờ VN — không phải crontab |
| Kéo về 03:00 | VPS-2, `/srv/songnhue-backups` | "VPS-1 chết / đĩa hỏng" | crontab trên **VPS-2** |
| Đẩy ra 03:30, **đã mã hoá** | B2/R2 | "mất tài khoản nhà cung cấp" | crontab trên **VPS-2** |
| `predeploy-*` | VPS-1, cùng thư mục | "migration làm hỏng dữ liệu" — **điểm quay lui duy nhất**, hệ không có PITR | mỗi lượt CD |

⚠ Bản `predeploy-*` mang tiền tố khác `songnhue-` **có chủ đích**: vòng dọn 30 ngày của `backup.sh`
không chạm tới nó. Giữ **10** bản gần nhất (`PREDEPLOY_KEEP`).

### 12.1. Kéo về VPS-2

Tạo trên **VPS-1** một tài khoản **chỉ đọc** kho sao lưu (mặc định script dùng tên `songnhue-backup`),
cắm khoá công khai của VPS-2 vào `authorized_keys` của nó, rồi trên **VPS-2**:

```cron
0 3 * * * PROD_HOST=<IP-VPS1> /opt/songnhue/backup/pull-from-prod.sh >> /var/log/songnhue-pull.log 2>&1
```

⛔ **Mô hình KÉO, không ĐẨY.** Máy chạy ứng dụng **không được giữ khoá ghi** vào kho sao lưu — nếu
không thì ai chiếm được nó cũng xoá được mọi bản sao lưu. Đó là việc mã độc tống tiền làm đầu tiên.

📌 Kho chỉ nói *"tạo một tài khoản chỉ đọc"* mà **không có script nào** tạo nó — đây là một bước gõ
tay chưa được gói lại.

### 12.2. Đẩy ra ngoài nhà cung cấp

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

> ⚠⚠ **Khoá riêng `age` tuyệt đối không nằm trên VPS-2.** Để cả hai trên cùng máy thì mã hoá chỉ còn
> là một bước tốn thời gian. Và **cất bản sao khoá riêng ở nơi thứ hai** — mất khoá là mất luôn toàn
> bộ kho sao lưu ngoài, không có đường nào lấy lại.
>
> ⚠ `OFFSITE_MINIO_REMOTE` là **mắt xích hay bị bỏ quên nhất**: sao lưu CSDL **không** bao gồm tệp.
> Thiếu nó thì khôi phục xong sẽ được một hệ thống đầy đủ bản ghi mà **mọi đường tải về đều 404**.
>
> 📌 Tệp đính kèm **cố ý không mã hoá bằng `age`** (phá cơ chế đồng bộ tăng dần); bù bằng kho đích có
> mã hoá phía máy chủ và khoá ứng dụng **chỉ đọc** trên nguồn. Đánh đổi có ý thức, ghi ra để người sau
> không tưởng là sơ suất.

### 12.3. Bản dump không được chứa khoá

`verify-no-keys.sh` chạy tự động ở cuối **mỗi** lượt `backup.sh` và **mỗi** lượt `pre-deploy-dump.sh`
— tức mỗi lượt deploy. Nó soi hai mẫu: khối `-----BEGIN … PRIVATE KEY-----`, và các tên
`AES_KEY_V*` / `JWT_PRIVATE_KEY` / `MINIO_SECRET_KEY` / `DB_MIGRATION_PASSWORD` đứng trước `=` hoặc `:`.

⛔ **Nhánh `exit 0` khi thiếu công cụ đã bị bỏ hẳn (T11.41, vá 3/9).** Trước đó VPS staging không cài
`postgresql-client`, nên **mọi lượt triển khai từ 26/8** in *"BỎ QUA việc kiểm khoá"* rồi đi tiếp —
một phép kiểm bảo mật thoát 0 ở mọi lượt mà không kiểm gì. Nay nó tìm `pg_restore` trên host, rồi
trong container `postgres`; **không đường nào thì đỏ**.

Phép đo thật là dòng `· Soi bằng pg_restore của: <host|container …>` trong log CD. Chạy tay:

```bash
make backup-verify ENV=prod
```

### 12.4. ⛔ Diễn tập khôi phục — **bắt buộc trước go-live, và chưa từng chạy lần nào**

Nhật ký ở `docs/runbook/dien-tap-khoi-phuc.md` còn **bảy ô trống**, gồm `RTO thật: ______ phút`
(nợ **T7.13** / **DOD0.14**). Cam kết RTO ≤ 4h hiện **chưa có con số đo nào chống lưng**.

Làm trên **VPS-2**, không bao giờ trên production. Chọn **bản đã kéo về VPS-2**, không phải bản trên
VPS-1 — kéo về là mắt xích chưa được thử.

```bash
# Trên VPS-2
ENV=staging deploy/backup/restore.sh /srv/songnhue-backups/songnhue-songnhue-<stamp>.dump
```

`restore.sh` tự làm theo thứ tự: đối chiếu `.sha256` **trước khi đụng dữ liệu** → bắt gõ **đúng tên
CSDL** (không phải "y") → chụp một bản lùi, hỏng thì **dừng** → ngắt kết nối → lọc mục lục →
`pg_restore --clean --if-exists --no-owner --single-transaction --exit-on-error`.

⚠⚠ **KHÔNG có `--no-privileges`** — bỏ ngày 26/8 sau một lượt diễn tập thật. Giữ nó thì khôi phục ra
một CSDL đủ 61 bảng mà `songnhue_app` chết ở `permission denied for table users`: GRANT do migration
Flyway cấp, khôi phục vào cluster mới thì `flyway_schema_history` nạp lại nên Flyway thấy "up to date"
và **không cấp lại quyền** (§10.58). Đây là đường quay lui dữ liệu **duy nhất** của hệ, và nó từng
khôi phục ra một CSDL mà ứng dụng không đọc nổi.

**Kiểm sau khôi phục — sáu mục, không bỏ mục nào:**

```bash
curl -fsS http://localhost:8080/actuator/health/readiness
make db-verify-audit ENV=staging          # rỗng = chuỗi hash audit còn nguyên
make migrate-info ENV=staging             # ⛔ mục hay bị bỏ nhất, hậu quả nặng nhất
psql -U songnhue_readonly -d songnhue -c \
  "SELECT (SELECT count(*) FROM users) u, (SELECT count(*) FROM org_units) o,
          (SELECT count(*) FROM settings) s, (SELECT count(*) FROM audit_logs) a"
psql -U songnhue_app -d songnhue -c 'SELECT count(*) FROM users'   # ⭐ đọc bằng VAI TRÒ ỨNG DỤNG
psql -U songnhue_readonly -d songnhue -c \
  "SELECT setting_value FROM settings WHERE setting_key='system.maintenance-mode'"
```

⭐ Mục áp chót là mục quan trọng nhất: **hỏi bằng chủ sở hữu không phân biệt được hai trạng thái** —
đó đúng là chỗ §10.58 lọt qua. Cộng thêm hai mục của runbook: **đăng nhập lại được** (bảng `sessions`
bị ghi đè) và **giải mã được một trường 🔒** (chứng minh khoá AES trên máy đích khớp với dump).

**Ghi con số RTO thật vào runbook.** Ô ấy không được ký khống.

⚠ `make rehearse` / `deploy/rehearse.sh` là **diễn tập đường dữ liệu của lượt TRIỂN KHAI** (bucket,
migrator, byte seed) — **không phải** diễn tập khôi phục. Nó cố ý thay bind mount tuyệt đối bằng
volume có tên, nên nó **không kiểm được quyền thư mục**.

---

## 13. Quay lui

### 13.1. Ba tình huống

| Tình huống | Cách | Thời gian |
|---|---|---|
| Mã mới hỏng, **lược đồ không đổi** | chạy lại **CD Production** với SHA của lần trước | ~3 phút |
| Migration làm hỏng dữ liệu | khôi phục từ bản `predeploy-*` sinh ở đầu lượt deploy — `docs/runbook/khoi-phuc-du-lieu.md` | tính bằng giờ |
| Máy chủ chết hẳn | dựng VPS mới theo §2–§7, khôi phục từ bản trên VPS-2 | đây là con số **RTO ≤ 4h** |

> ⚠ **Mỗi migration đổi lược đồ phải kèm ghi chú quay lui trong PR.** Không có PITR nghĩa là câu
> "quay lui thế nào" phải được trả lời **trước** khi merge, không phải lúc đang hỏng.

### 13.2. ⛔ Quay lui tự động chỉ khôi phục **MÃ NGUỒN**

Bước *Quay lui bản cũ* chạy khi `failure()` **và** bước *Ghi lại bản đang chạy* đọc được đủ ba
container. Nó dựng lại ba image cũ rồi hỏi lại đúng câu 2 của smoke test, 18 vòng × 10 giây.

`migrator` đã chạy **xong trước đó**, và migration là **một chiều**: nếu nó đã đổi lược đồ thì mã cũ
có thể không chạy được trên lược đồ mới, và bước này **không cứu được gì**. Kể cả khi thành công,
workflow vẫn in `⛔ NHƯNG lược đồ CSDL vẫn đang ở trạng thái sau migration của lượt hỏng.`

Đường quay lui **về dữ liệu** là bản `predeploy-*`, và nó là việc **làm tay**.

⚠ Lượt deploy **đầu tiên** không có gì để quay lui (`co_du=false`) — workflow chỉ in một `::warning::`.

### 13.3. DOD0.21 vẫn chưa đóng, và lý do đáng đọc

Đường quay lui đã **chạy thật hai lần** (27/8, run `33086135148` và `33095696654`), cả hai in
`→ [quay-lui] 511 byte, khớp hai đầu` và bước `success`. **Vẫn không tick**: cả hai đều là lượt
**không có gì để quay lui** — `migrator` chạy `run --rm` **trước** `up -d --force-recreate`, nên lượt
deploy dừng trước khi chạm container nào; ba container vẫn nguyên, `Created` không đổi.

📌 Đây không phải trùng hợp mà là **thiết kế**: `migrator` chạy trước `up -d`, nên **mọi lỗi migration
sẽ mãi dừng ở đó**. Bằng chứng cho DOD0.21 chỉ đến từ một lỗi ở **tầng ứng dụng** (smoke test đỏ) sau
bước `up -d` — nếu chờ nó xảy ra tự nhiên thì có thể chờ mãi.

⇒ **Nên dựng một lượt hỏng có chủ đích trên staging trước go-live**, rồi đo `Created` của container
quay về mốc cũ. Đó là cách duy nhất đóng ô này bằng số đo.

---

## 14. Giám sát — đặt **ngoài** máy production

```bash
# Trên VPS-2
cd /opt/songnhue
docker compose --env-file .env -f compose.observability.yml up -d
```

Grafana và Prometheus publish ra `127.0.0.1`; vào bằng đường hầm SSH, không mở cổng:

```bash
ssh -N -L 13001:127.0.0.1:13001 songnhue@<IP-VPS2>
```

Cộng thêm hai thứ ngoài hệ thống, cả hai miễn phí:

* **Ping từ bên ngoài** (UptimeRobot / Better Stack) tới `https://<ten-mien>/healthz` — trả lời câu
  "cả máy có chết không", mà Prometheus đặt trên VPS-2 không trả lời được nếu mạng đứt.
  `/healthz` là `return 200` **tĩnh** ở cổng 80, trả lời được cả khi mọi upstream đã chết.
* **Cảnh báo sao lưu**: `SaoLuuQuaHan` (`songnhue_backup_age_seconds > 93600`, hoặc `== -1`) và
  `SaoLuuChuaRaKhoiMayChu` (bản offsite quá 26h) — đã có sẵn trong `deploy/observability/alerts.yml`.

> Ba thứ **phải** có cảnh báo, không thêm gì nữa cho tới khi thấy thiếu: **ứng dụng chết** · **sao lưu
> chết** · **poller thuỷ văn chết** (nguồn không có API lịch sử — mất là mất vĩnh viễn, không backfill được).

⛔ **Trước go-live phải xác nhận ai thật sự nhận cảnh báo.** Cổng quét CVE từng đỏ **hơn một ngày mà
không ai đọc**: chuông reo trong phòng trống — thư mặc định của GitHub gửi **người tạo workflow**,
không phải người commit (§10.68-A). Và nợ **T11.67**: chuông `if: failure()` **không phủ** trường hợp
lượt quét *không chạy*; độ trễ thật đo được **291–726 phút** so với lịch.

---

## 15. Từ đây trở đi — vận hành thường ngày

| Việc | Cách làm |
|---|---|
| Đưa mã lên staging | PR `dev → staging`, **merge commit**, CD tự chạy |
| Đưa lên production | PR `staging → production`, **merge commit** → CD tự chạy |
| **Quay lui production** | Actions → **CD Production** → *Use workflow from* = **`production`** → `commit_sha` cũ hơn + lý do |
| Đổi cấu hình nginx / compose / script | **Sửa trong repo**, đi theo đúng luồng trên. ⛔ Đừng sửa tay trên máy chủ — lượt deploy sau `rsync --delete` sẽ xoá mất |
| Đổi tham số nghiệp vụ (giờ hành chính, ngưỡng, hạn mức, retention…) | Màn hình **Cấu hình hệ thống**, **không** sửa `.env` |
| Đổi bí mật | Sửa `/opt/songnhue/.env` rồi `docker compose … up -d --force-recreate app` |
| Đổi mã số API thuỷ văn | Màn hình **Nguồn dữ liệu** — nhà thật của nó là `api_sources.credential`, không phải `.env` |
| Xoay khoá AES | thêm `AES_KEY_V2`, đổi `AES_KEY_ID=v2`, **GIỮ NGUYÊN `AES_KEY_V1`** cho tới khi bản sao lưu cuối cùng dùng nó đã quá hạn. `docs/runbook/xoay-khoa.md` |
| Xoay khoá JWT | sinh cặp mới **tên khác**, đổi `JWT_KEY_ID` — bắt buộc. Vô hiệu mọi phiên đang sống |
| Sau **mọi** lần xoay khoá | `ENV=prod deploy/backup/backup.sh` + `make backup-verify ENV=prod` |
| Gia hạn TLS | tự động (cron §7.4). Kiểm bằng `--dry-run`, đừng đợi ngày hết hạn |
| Gia hạn tên miền | §1.2 — **không cảnh báo nào trong hệ bắt được việc này** |

⚠ **Sau mỗi lượt đề bạt**, chạy bất biến gốc chung (§11.2). Nó chặn ở lượt **kế tiếp**, không chặn
lượt gây ra lỗi — nên chạy sớm là cách duy nhất biết mình vừa squash nhầm.

---

## 16. Checklist go-live

Điền ngày và người ký. **Hai ô có cảnh báo riêng: mục 14 và mục 20 không được ký khống** — dự án đã có
**4 ngày sao lưu chạy mà không sinh ra một tệp nào**, trong khi `BackupServiceTest` xanh trọn vẹn, vì
nó mock đúng chỗ mã chạm ra ngoài.

| # | Việc | Lệnh / phép đo | Xong |
|---|---|---|:-:|
| 1 | Chủ thể tên miền là **Công ty** | `whois` trả về tên Công ty | ☐ |
| 2 | Hoá đơn VPS-1 mang tên Công ty | — | ☐ |
| 3 | Phụ lục xử lý DLCN đã ký | — | ☐ |
| 4 | `sshd -T` trả `passwordauthentication no` **và** `permitrootlogin no` | §2 kiểm chứng | ☐ |
| 5 | `fail2ban` **active**, SSH 10/10 | §3 kiểm chứng | ☐ |
| 6 | Chỉ 22/80/443 lắng nghe ra ngoài | `sudo ss -tlnp` | ☐ |
| 7 | Quyền ba thư mục đúng | `stat -c '%u:%g %a' /var/lib/songnhue/backup` → **`999:1000 2775`** | ☐ |
| 8 | `docker login ghcr.io` đã chạy trên máy chủ | `docker pull` một image thật | ☐ |
| 9 | **Collation ✓** | `./postgres/kiem-collation.sh` → `icu=vi-VN`. ⛔ **chỉ ký được TRƯỚC khi có dữ liệu thật** | ☐ |
| 10 | Thiếu một biến bắt buộc → app **không khởi động**, log chỉ đúng tên biến | §5 kiểm chứng | ☐ |
| 11 | `migrator` chạy ở service riêng, thoát 0 trước khi `app` lên | log lượt dựng | ☐ |
| 12 | Mười phép nghiệm thu §9 **đều xanh** — gồm phép 8 (không có bài seed) và phép 9 (tải về được tệp) | §9 | ☐ |
| 13 | `certbot renew --dry-run` xanh | §7.4 | ☐ |
| 14 | **`make backup` sinh ra một tệp THẬT**, checksum khớp | `ls -lh` thấy tệp > 0 byte | ☐ |
| 15 | Bản dump **không chứa** khoá AES/JWT | `make backup-verify ENV=prod`, thấy dòng `Soi bằng pg_restore của: …` | ☐ |
| 16 | Bản sao đã có mặt trên **VPS-2** và trên **kho ngoài** | `ls /srv/songnhue-backups` + `rclone lsf` | ☐ |
| 17 | Cảnh báo sao lưu quá hạn bắn tới **email thật** (thử bằng cách dừng job > 26h) | — | ☐ |
| 18 | Ping ngoài đã dựng và **đã thử bằng cách tắt nginx** | — | ☐ |
| 19 | `BOOTSTRAP_ADMIN_PASSWORD` đã **xoá** khỏi `.env`, và **không có chú thích cùng dòng** | `grep '^BOOTSTRAP' /opt/songnhue/.env` | ☐ |
| 20 | **Diễn tập khôi phục thật**, đọc được bằng vai `songnhue_app`, **ghi con số RTO thật vào runbook** | §12.4 | ☐ |
| 21 | Đã quay lui thử một lần ở staging, đo `Created` của container quay về mốc cũ | §13.3 | ☐ |
| 22 | 5 secret `PROD_*` đã đặt và **đo lại bằng API** ra đúng 5 | §10.1 | ☐ |
| 23 | Biến kho `PUBLIC_SITE_URL` đã đặt **và đã có lượt build mới** sau đó | §10.3 | ☐ |
| 24 | Bảo vệ nhánh `production` đo lại vẫn còn **1 approval** + `Promotion guard`; environment **không còn reviewer** nhưng **có** `deployment_branch_policy` đúng một nhánh `production` | §10.5 | ☐ |
| 25 | `kiem-goc-chung.sh origin/staging origin/production` → **0 commit không-phải-merge** | §11.2 | ☐ |
| 26 | Lượt CD Production đầu tiên: đối chiếu **độc lập** container / Flyway / bản chụp | §11.7 | ☐ |
| 27 | Tải thử một tệp **> 1MB thật** qua giao diện | §17 bẫy §10.69 | ☐ |
| 28 | Đọc lại `dependency-check-suppressions.xml`: mọi mục còn `until` chưa hết hạn | §17 | ☐ |
| 29 | Đã xác nhận **ai thật sự nhận** cảnh báo CVE và cảnh báo sao lưu | §14 | ☐ |
| 30 | `docs/runbook/` đã đưa cho **người thứ hai**, và bản sao khoá riêng `age` cất ở **nơi thứ hai** | rủi ro #4 | ☐ |

---

## 17. Nợ đang chặn và bẫy phải tránh

### 17.1. Chặn go-live

| Mã | Nội dung | Ai làm | Chặn ở đâu |
|---|---|---|---|
| ~~**T11.2**~~ | ✅ **đóng 6/9/2026** — VPS-1 `27.71.16.154`: Ubuntu 24.04.3 · 8 vCPU · 15 GiB RAM · 118G đĩa · Docker 29.8.0 + Compose v5.5.1 · ufw 22/80/443 · fail2ban active | — | — |
| **T11.2-b** | Chưa mua tên miền `.vn`, chủ thể phải là Công ty | Công ty | §1.2, §7. ⭐ **Không còn chặn go-live**: production dùng `songnhue.com` trước (chốt 6/9), cắt sang `.vn` sau. ⛔ Lúc cắt phải **đặt lại biến kho + DỰNG LẠI image**, sửa DNS một mình là chưa đủ |
| ~~**T11.7**~~ | ✅ **đóng 6/9/2026** — đo lại bằng API: `total_count: 5`. Khoá host lấy từ `/etc/ssh/ssh_host_ed25519_key.pub` **trên máy chủ**, đối chiếu khớp với `known_hosts` cục bộ | — | — |
| ~~**T11.7-a**~~ | ✅ **đóng 6/9/2026** — biến kho `PUBLIC_SITE_URL = https://songnhue.com`. ⚠ **Chưa đủ**: image `public-web` đang chạy vẫn nướng chuỗi rỗng, phải có **một lượt build mới trên `dev`** rồi mới đề bạt (checklist #23) | — | — |
| ~~**T11.35**~~ | ✅ **đóng 6/9/2026** — `deploy/host-prepare.sh` có, idempotent, đã chạy thật trên VPS-1 (2 lượt, cùng kết quả, thoát 0). Nó cài `rsync` (**thiếu trên máy mới** — bước rsync của CD sẽ chết), dựng 4 thư mục và đặt quyền **bằng số**: keys `1000:1000 700` · log `1000:1000 755` · backup **`999:1000 2775`** | — | — |
| **T11.36** | `docker login ghcr.io` là thao tác tay bằng PAT | người dựng VPS-1 | §4.3 — chưa làm thì `compose up` dừng ở `unauthorized` |
| **T7.13 / DOD0.14** | **Chưa diễn tập khôi phục lần nào**; `RTO thật: ______` | QuanTran + vận hành | checklist #20 |
| **DOD0.21** | Quay lui chưa từng dựng lại một bản **đã bị thay** | — | checklist #21 |
| **T11.69** | Di trú Boot 3.5.16 → **4.1.1**, hạn **15/10/2026** | Dev | quyết định go-live — xem §17.3 |
| **T11.54** | Cổng lạ mở ra Internet (đã gặp trên VPS-2: 5201) | QuanTran | §3.1 — **kiểm cùng loại trên VPS-1 khi nhận máy** |

Nợ **không chặn** nhưng phải biết: **T11.33** (`app.storage` chỉ có một `endpoint`, đang chữa tạm bằng
bí danh mạng) · **T11.34** (`system_backups.trigger_type` chưa có `PRE_DEPLOY`, bản chụp trước deploy
ghi `MANUAL`, phân biệt bằng tiền tố tên tệp) · **T11.38** (hai đường seed cùng tồn tại, đường cũ
**không có cổng chặn `SEED_LOCATION`**).

### 17.2. Bẫy đã trả giá — mỗi dòng là một việc phải làm khi dựng VPS-1

| § | Hiện tượng đã xảy ra | Việc phải làm |
|---|---|---|
| §10.56 | Vá `compose.prod.yml` rồi `up -d --force-recreate` mà collation **vẫn sai** | chạy `kiem-collation.sh` và đọc ✓ **trước khi nạp một byte dữ liệu thật nào** |
| §10.58 | `pg_restore` xong, app chết `permission denied for table users` dù đủ 61 bảng | diễn tập khôi phục **vào một cluster MỚI**, rồi đọc thử bằng vai `songnhue_app` |
| §10.53 | Deploy "theo digest" xanh, container vẫn chạy image cũ | sau lượt deploy đầu, tự `docker inspect` **ID ảnh** và đối chiếu digest |
| §10.60 | CD báo `success` trọn vẹn, **không container nào được thay** | mọi khối lệnh từ xa phải đi qua `chay-tu-xa.sh`; nghi ngay nếu bước *Triển khai* kết thúc < 1s |
| §10.57 | CD Production xanh trọn vẹn, **0 byte chạm máy chủ** | lượt CD đầu **phải** thấy dòng đỏ nếu thiếu secret; xanh mà không có log SSH ⇒ nghi xanh giả |
| §10.68-C | Lượt deploy **tự cấm chính nó** | ghim `PROD_SSH_KNOWN_HOSTS` **trước** lượt CD đầu; `jail.local` không đặt `ignoreip` |
| §10.59 | CD đỏ vì cổng 22 bị quét, SSH thả 30% | áp §3.2 **ngay lúc nhận máy** |
| §10.68-D | Secret **có** trên GitHub mà CD vẫn báo thiếu | `workflow_call` không tự chảy secret xuống — sau khi đặt, chạy **một lượt thử** |
| §10.65 | Sửa **chú thích** trong tệp migration ⇒ app không khởi động (`checksum mismatch`) | không sửa bất kỳ tệp `V*.sql` nào **đã áp** ở staging |
| §10.66 | Migration đánh số bằng giờ-phút rơi xuống **dưới** bản đã áp | trước lượt đề bạt, chạy `backend/tools/kiem-thu-tu-migration.sh` |
| §10.69 | Ảnh Công ty tải lên → **500** | sau go-live, tải thử một tệp **> 1MB thật** qua giao diện |
| §10.55 | `minio-init` in `✓ sẵn sàng` với secret sai độ dài | phép nghiệm thu MinIO phải là **ghi → đọc → xoá**, không phải "lệnh chạy xong" |
| §10.72 | Squash làm gãy gốc chung ⇒ `Promotion guard` **không chạy**, treo ở *Expected* | đề bạt **bắt buộc** "Create a merge commit"; sau mỗi lượt chạy `kiem-goc-chung.sh` |
| §10.71 | Nâng thư viện xong quên xoá suppression đã thành rác | trước go-live đọc lại `dependency-check-suppressions.xml` |

⛔ **Bộ test xanh KHÔNG phải bằng chứng cho bất cứ điều gì về CSDL production đã sống.** Bộ test chạy
migration từ CSDL **rỗng**: ở đó **không có checksum cũ để so** và **không tồn tại khái niệm
out-of-order**. 688 bài kiểm không sai — chúng **về nguyên tắc** không thể thấy hai lớp lỗi ấy, và cả
hai chỉ hiện ra lúc deploy.

### 17.3. CVE — ảnh hưởng tới quyết định go-live

Đo **3/9/2026** trên `dev`: **7 mã CVSS ≥ 7** — 3 mã **9.8**, 2 mã 9.1, 2 mã 7.5 — tất cả ở
`spring-core` / `spring-web` 6.2.19 và `spring-security-crypto` 6.5.11.

**4 → 7 mã trong 24 giờ, mã dự án không đổi một dòng.** Không mã nào vá được trong dòng đang dùng:
`6.2.20` và `6.5.12` đều **HTTP 404 trên Maven Central**, vĩnh viễn — Boot 3.5 / Framework 6.2 hết hỗ
trợ OSS từ 30/6/2026. Đường xoá **duy nhất** là **Spring Boot 4.1.1**, hạn ép bằng máy **15/10/2026**.

Riêng `CVE-2026-59283` (9.1) đã hạ rủi ro **bằng cấu hình chứ không bằng vá**:
`-Dspring.expression.compiler.mode=off` khai ở **cả hai** đường JVM (`compose.prod.yml` và
`backend.Dockerfile`). ⬜ Vế "JVM **đang chạy** đã giải biến ấy" thì chưa đo — sau lượt deploy:

```bash
ssh songnhue@<IP-VPS1> \
  "docker inspect -f '{{json .Config.Env}}' songnhue-app | tr ',' '\n' | grep -i compiler"
```

> ⛔ **Lên production trước 15/10 nghĩa là chạy một hệ có ít nhất 3 mã 9.8 không có bản vá công khai,
> và con số ấy tăng theo ngày mà không ai chạm mã.** Đây là một quyết định phải nói ra với Công ty,
> không phải một dòng trong sổ kỹ thuật. `conventions.md` §4.5 **cấm** đóng bằng suppression với lý do
> "dòng này hết hỗ trợ".
>
> ⚠ Con số **7 chưa được xác nhận lại** trên đỉnh `dev` hiện tại: lượt gộp PR #78 (4/9) không chạm
> `**/pom.xml` nên bộ lọc đường dẫn không kích hoạt quét lại. **Đo lại trước khi quyết**.

### 17.4. Chỗ tài liệu cũ đang nói sai — đọc trước khi làm theo

| Ở đâu | Tài liệu nói | Đo được |
|---|---|---|
| `docs/cicd.md` §7.1 | cổng secret có **bốn** biến | **năm** — `SSH_KNOWN_HOSTS` vào bộ 29/8. Đặt 4 secret theo bảng ấy ⇒ cổng **đỏ**, không "đi tiếp" |
| `docs/cicd.md` §7 | *"lệnh đặt sẵn ở `master-tracking.md` T11.7"* | **không có lệnh nào** ở đó; `grep "gh secret set"` toàn kho → 0. §10.1 tài liệu này là bản soạn mới |
| `docs/cicd.md` §9 | *"`deploy-staging.yml` tìm image qua `HEAD^2`"* | đã bỏ hẳn `HEAD^2` — nay so **cây tệp**; chính §4.1 cùng tệp mô tả đúng |
| `docs/branch-protection.md` §4.2 | JSON đặt `required_approving_review_count: 0` | nhánh đang chạy với **1**. Chạy lại khối `PUT` ấy là **hạ** mức bảo vệ |
| `docs/deploy-guideline.md` §8 | *"Production giống hệt mục 5"* | không giống: `SEED_LOCATION`, `ROBOTS_TAG`, `DB_RESTORE_PASSWORD`, `so_bai_toi_thieu`, và ba việc thêm — xem §0.1 tài liệu này |
| `docs/deploy-guideline.md` §9 | bản `predeploy-*` sinh *"trước mỗi lượt deploy production"* | chạy ở **cả staging** từ 25/8 |

---

## Phụ lục — lệnh tra nhanh trên VPS-1

```bash
cd /opt/songnhue
dc="docker compose --env-file .env -f compose.prod.yml"

# ⚠ Mọi lệnh compose đều đòi ba biến image. Chưa export thì nó dừng ở "Thiếu APP_IMAGE",
#   kể cả lệnh chỉ đọc. Lấy đúng thứ đang chạy:
export APP_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-app)
export ADMIN_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-admin-app)
export PUBLIC_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-public-web)

$dc ps                                    # container nào sống, healthy chưa
$dc logs -n 200 app                       # log ứng dụng
$dc exec -T postgres psql -U songnhue_readonly -d songnhue -c '\dt'
./postgres/kiem-collation.sh              # collation
ls -lh /var/lib/songnhue/backup | tail     # bản sao lưu gần nhất
curl -sI http://localhost/healthz          # nginx còn sống không
```

| Cần gì | Đọc ở đâu |
|---|---|
| Sao lưu hỏng | `docs/runbook/sao-luu-hong.md` |
| Khôi phục dữ liệu | `docs/runbook/khoi-phuc-du-lieu.md` |
| Diễn tập khôi phục | `docs/runbook/dien-tap-khoi-phuc.md` |
| Job nền thất bại | `docs/runbook/job-that-bai.md` |
| Xoay khoá | `docs/runbook/xoay-khoa.md` |
| Sự kiện bảo mật | `docs/runbook/su-kien-bao-mat.md` |
| Poller thuỷ văn chết | `docs/runbook/poller-chet.md` |
| Phân vùng audit | `docs/runbook/audit-partition.md` |
| Nợ và task đang mở | `.claude/master-tracking.md` — **nguồn duy nhất** |
| Nguyên nhân gốc một sự cố cũ | `.claude/architecture-review.md` §9–§10 |
