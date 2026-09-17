# Phase 4 — bảng theo dõi TẠM

> ⛔ **Tệp TẠM, ⛔ không phải sổ.** Nó là *góc nhìn làm việc* của Phase 4: ai làm gì, theo thứ tự nào,
> bằng lệnh nào. Trạng thái chính thức, ID và bằng chứng nằm ở **`master-tracking.md`** (nguồn DUY
> NHẤT, `conventions.md` §6). ⛔ Cấm đẻ ID ở đây; mọi hàng trỏ về một ID đã có trong sổ.
> Tick ở đây **sau** khi đã tick trong sổ. **Xoá tệp này khi Phase 4 đóng.**
>
> Dựng 14/09/2026 từ lượt đối chiếu `T61.0`. Kế hoạch và thứ tự: `phase4-plan.md`.
> Ba cột chủ: **Dev** = phía phát triển (mã/tài liệu trong kho, PR vào `dev`) · **QT** = QuanTran
> (gộp, đề bạt, SSH, GitHub settings) · **CT** = Công ty.

---

## A. Việc của phía phát triển — làm được NGAY, ⛔ không cần máy chủ

| # | ID | Việc | Ưu tiên | Trạng thái |
|---|---|---|---|---|
| A1 | `T61.8` | `umask 077` cho `pre-deploy-dump.sh` + `backup.sh` + bộ canh tĩnh | P0 · bảo mật | [x] |
| A2 | `T61.11` | Runbook xoay khoá: dặn đúng về vân tay CCCD (phần lời dặn) | P0 · dữ liệu | [x] |
| A3 | `T61.3` · `T61.16` | Sửa CLAUDE.md: tên miền production `.vn`, VM-3 → VPS-2, 15 commit | P0 · tài liệu | [x] |
| A4 | `T61.12` | Bài render vòng khứ hồi `ConstructionFormPage` | P1 · dữ liệu | [x] |
| A5 | `T61.14` | Bài HTTP khẳng định GIÁ TRỊ `ip_address` ở 3 bảng | P1 · bảo mật | [x] |
| A6 | `T61.6` | Kịch bản load test (k6) — 200 CCU cổng · 50 users dashboard · khai tỉ lệ 429 | P1 · NFR-02 | [~] viết xong, chưa chạy staging |
| A7 | `T61.4` | Dịch vụ ClamAV trong `compose.prod.yml` + nối `APP_CLAMAV_HOST` — ✅ mã (cả hai máy, `ConcurrentDatabaseReload no`, trần luồng 130M, lỗi quét ⛔ còn đọc thành nhiễm) · ⬜ đo trên staging | P1 · bảo mật | [~] |
| A8 | `T61.5` | Alertmanager (Gmail + Slack + Telegram) + vá hệ giám sát 0 chỉ số từ WS-7 (target `${…}` ⛔ thay) + cửa nginx `/actuator/prometheus` — ✅ mã, chạy thật ở máy · ⬜ QT đặt biến + đo (§B6) | P1 · NFR-01 | [~] |
| A9 | `T61.11` | Job `CRYPTO_REENCRYPT` + chống trùng dưới mọi khoá (T51.9, phần mã) — ⬜ diễn tập thật trên staging ở §B | P1 · dữ liệu | [x] |
| A10 | `T61.13` | Bộ canh ĐẾM nơi ném đối số vào mã lỗi ⛔ `{n}`, rồi vá — 44 nơi, 7 chiều THIẾU (người dùng thấy `{1}`) + `JobWorker.last_error` | P2 | [x] |
| A11 | `T61.15` | javadoc T57.7→T57.15 · xoá `hr.spi` rỗng · sửa `nghiem-thu-cong-ttdt-v1.md` | P2 | [x] |
| A12 | `T58.18` · `T25.23` | Bộ canh N+1 · hạ trần màu ghi cứng | P2 | [ ] |
| A13 | `T61.17` | ⛔⛔ Hạn mức khoá theo IP ⇒ 50 cán bộ sau một NAT chung 100 lượt/phút — QT chốt *vá ngay*: backend ✅ (API + kết xuất theo người@IP) · nginx `api_auth` ⬜ chờ đo NAT | P0 | [~] |
| A14 | `T47.17` | Bài vòng khứ hồi biểu mẫu thay-toàn-phần — 3/17 (công trình · bài viết · sửa chữa) | P2 | [~] |
| A15 | `T61.18` | Lối SỬA bản ghi sửa chữa (PUT có 0 nơi gọi) ✅ + bộ canh endpoint ↔ lời gọi khớp ĐỘNG TỪ toàn `admin-app` ✅ (15 mồ côi → T61.19–22) | P2 | [x] |
| A16 | `T61.19` | Giao diện tệp đính kèm + nút xoá của bản ghi sửa chữa (CN-02.2 ảnh trước/sau) | P1 · nghiệm thu | [x] |
| A17 | `T61.20` | Màn hình cảnh báo hết hạn HĐLĐ/chứng chỉ M4.9 | P1 · nghiệm thu | [x] |
| A18 | `T61.21` · `T61.22` | Nút xoá tài khoản + nguồn dữ liệu (điểm đo cố ý ⛔) · gỡ 6 endpoint thừa kể cả `AttachmentController` — QT chốt 14/09 | P2 | [x] |
| A19 | `T61.23` | Chuyển hướng thư staging `MAIL_REDIRECT_TO` — dừng khởi động ở cả hai chiều sai | P1 · dữ liệu | [~] QT đặt biến |
| A20 | `T61.24` | Tự quét lại tệp `SKIPPED`/`ERROR` khi khởi động + `ScanStatus.ERROR` có nơi ghi | P1 · bảo mật | [x] |
| A21 | `T61.25` · `T61.26` | Chuông canh healthchecks.io · chuông sao lưu chỉ production | P1 · NFR-01 | [~] QT tạo check |
| A22 | `T61.27` | Hạn mức kết xuất vào `settings` (30/giờ, kẹp ≤ 100) | P2 | [x] |
| A23 | `T61.28` | NFR-05 tự đánh giá ASVS L1 + ZAP baseline | P0 · NFR-05 | [~] QT chạy ZAP |
| A24 | `T61.29` | NFR-09 Playwright 3 engine × 4 bề rộng — staging công khai 140 xanh | P1 · NFR-09 | [~] QT cấp tài khoản đo |
| A25 | `T61.30` · `T61.33` | ⛔⛔ Vượt 2FA bằng đăng ký lại · dò TOTP ⛔ khoá — vá + nút đặt lại 2FA | P0 · bảo mật | [x] |
| A26 | `T61.32` · `T61.34` · `T61.35` | SVG chạy script · `javascript:` trong href · `no-store` | P0/P1 · bảo mật | [x] (T61.34 vế ghi ⬜) |
| A27 | `T61.31` · `T61.36`→`T61.40` | đặt lại mật khẩu · khoảng trống ASVS còn lại | P1–P3 | [ ] |
| A28 | `T61.41`→`T61.46` | Cấu hình hệ thống từ giao diện: tình trạng + banner · xác thực lại 2FA · T54.4 · bí mật tích hợp · dọn env · script §B6 | P1 · NFR-05 | [x] |

---

## B. Việc của QuanTran — lệnh cụ thể, theo thứ tự

> ⛔ Mỗi bước in một **con số đếm được** (luật 32). ⚠ `gh run list` in **UTC**, `docker ps` in **+07**.
> CSDL chỉ đi qua `docker exec` — máy chủ ⛔ không có `psql` trên host. Mọi lệnh dưới dùng hàm `q` của
> `docs/runbook/khoi-phuc-du-lieu.md:194` (`-U postgres`, stdin `/dev/null` — §10.60):
>
> ```bash
> q() { docker exec -i songnhue-postgres psql -U postgres -d songnhue -At -c "$1" < /dev/null; }
> ```

### B1. TRƯỚC khi gộp PR #133 — `T60.10` · `T61.2`

```bash
# VPS-1 và VPS-2 — ảnh MinIO quay.io chỉ có manifest linux/amd64
uname -m                                   # phải: x86_64 (cả hai máy)

# VPS-2 (staging) — bản vá poller chạy từ 11/09, có ghi được byte thật chưa?
q "SELECT count(*)||' bản ghi, mới nhất '||coalesce(max(measured_at)::text,'—') FROM hydro_readings"
q "SELECT code, credential IS NOT NULL, consecutive_failures, last_success_at, left(last_failure_reason,120)
     FROM api_sources WHERE deleted_at IS NULL"
```
- Số bản ghi `> 0` và `last_success_at` gần giờ đo ⇒ bản vá chạy trên nguồn thật → đề bạt production ngay.
- `0 bản ghi` ⇒ **dừng**, gửi nguyên văn `last_failure_reason` — đề bạt production ⛔ cứu được gì.

### B2. Chặng 1 — gộp PR #133 `dev → staging` bằng **merge commit** (⛔ squash, §10.72) — `T60.13`

```bash
# sau khi CD Staging xong — đo trên VPS-2, ⛔ đọc lại lời workflow
docker ps --format '{{.Names}}\t{{.Status}}\t{{.CreatedAt}}' | sort   # 6/6 healthy, Created trong cửa sổ deploy
docker inspect -f '{{.Config.Image}} {{.Created}}' songnhue-minio      # phải: quay.io/...hotfix.7aa24e772
q "SELECT count(*) FILTER (WHERE success)||' đạt · '||count(*) FILTER (WHERE NOT success)||' hỏng · đỉnh '||max(version) FROM flyway_schema_history"
                                                                      # phải: 74 đạt · 0 hỏng · đỉnh 202609141080
```

### B3. Chặng 2 — `staging → production`, rồi đo — `T60.13` · `T60.3` · `T61.1`

```bash
# VPS-1, sau CD Production
docker ps --format '{{.Names}}\t{{.Status}}\t{{.CreatedAt}}' | sort
cd /opt/songnhue && docker compose --env-file .env -f compose.prod.yml pull minio minio-init; echo "PULL=$?"   # T60.3: phải 0
docker inspect -f '{{.Config.Image}} {{.Created}}' songnhue-minio
curl -s -o /dev/null -w '%{http_code}\n' https://thuyloisongnhue.vn/   # 200
```
Rồi ghi giờ bắt đầu **T37.1** (7 ngày lịch) ngay khi `hydro_readings` trên production bắt đầu tăng (DOD4.2).

### B4. Song song — không chờ đề bạt

| ID | Việc | Lệnh / nơi làm |
|---|---|---|
| `T11.88` · `T61.9` | Cài `cron` trên VPS-2 (⛔ hạn TLS staging **22/11**) | `sudo apt-get install -y cron && sudo systemctl enable --now cron && systemctl is-enabled cron` |
| `T61.3` | Chứng chỉ production `.vn` hạn **06/12** — có cron gia hạn chưa | VPS-1: `crontab -l \| grep gia-han-tls; echo "SO_DONG=$?"` |
| `T61.8` | Sửa quyền bản dump CŨ (bản vá mã chỉ lo tệp MỚI) | VPS-1: `sudo find /var/lib/songnhue/backup -name '*.dump' -perm -o=r \| wc -l` → `sudo chmod 640` từng tệp (⛔ `600`: user deploy đọc qua NHÓM — `600` làm bước quay lui ⛔ đọc được bản dump) → đếm lại = 0 |
| `T61.9` | Bật lịch sao lưu production | `/quan-tri/cau-hinh` → `backup.schedule-enabled = true`; sáng hôm sau `ls -l /var/lib/songnhue/backup` |
| `T11.89` | Tách khoá SSH triển khai hai môi trường | GitHub → secret `PROD_SSH_KEY` ≠ `STAGING_SSH_KEY`; đo vân tay `ssh-keygen -lf` |
| `T11.54` | Cổng 5201 mở trên VPS-2 | VPS-2: `sudo ss -tlnp \| grep 5201` → tắt dịch vụ / `ufw deny 5201` |
| `T61.5` | ✅ chốt 14/09: **Gmail + Slack + Telegram** — Dev dựng A8; QT tạo bot/webhook, đặt secret vào `.env` | xem A8 |
| `T61.4` | ✅ chốt 14/09: **cả hai máy**, staging `ConcurrentDatabaseReload no` | xem A7 |
| `T50.13` | ✅ chốt 14/09: staging **dùng chung cấu hình SMTP của production** | chép khối `SMTP_*` từ `.env` VPS-1 sang VPS-2 |
| `T61.17` | **Xác nhận**: Công ty ra Internet qua MỘT IP công cộng? | từ một máy trong mạng Công ty: `curl -s https://api.ipify.org` trên 2–3 máy khác phòng — cùng một số là một NAT |

### B6. ⛔ TRƯỚC lượt đề bạt mang T61.4/T61.5 — thiếu biến là nginx ⛔ lên

⭐ **Đã chạy 16/09/2026** (`tools/may-chu/dat-bien-b6.sh`, đo lại độc lập sau đó):
VPS-1 **2/2** (`METRICS_ALLOW_IP=27.71.27.75` · `METRICS_BEARER_TOKEN` 64 hex) ·
VPS-2 **4/10** (thêm `PROD_METRICS_HOST=admin.thuyloisongnhue.vn` · `PROD_METRICS_BEARER_TOKEN` = token VPS-1).
`.env` hai máy nay `600 songnhue:songnhue`, có bản sao lưu `.env.bak-20260916193335/6`.
⬜ Còn **6 giá trị của QuanTran** (ALERT_EMAIL_TO · SLACK_WEBHOOK_URL · TELEGRAM_BOT_TOKEN ·
TELEGRAM_CHAT_ID · MAIL_REDIRECT_TO · HEALTHCHECKS_PING_URL) ⇒ chạy lại script, nó hỏi bằng ô nhập ẩn.
⬜ `SMTP_*` sang VPS-2: script **cố ý bỏ qua** tới khi staging chạy bản mang T61.23.

⭐ **15/09 — có script làm hộ phần lớn khối dưới** (`T61.46`), chạy TỪ MÁY ANH, ⛔ trên máy chủ:

```bash
tools/may-chu/dat-bien-b6.sh --thu   # chỉ đo + in việc sẽ làm
tools/may-chu/dat-bien-b6.sh         # đặt thật: sao lưu .env, sinh token, hỏi 6 giá trị bằng ô ẩn
# mã thoát 3 = còn thiếu (in danh sách) · chạy LẠI sau khi staging lên bản PR #135 để chép SMTP_*
```

Sau khi lên bản mới, mở **Quản trị › Tình trạng cấu hình** (SUPER_ADMIN): mục *Prometheus đọc được chỉ số*
phải "Đã đặt" trong ~5 phút; banner đỏ trên layout biến mất khi hết mục CHẶN.
Dọn thêm (`T61.45`): xoá các dòng `APP_BASE_URL` · `GOOGLE_MAPS_API_KEY` · `EXTERNAL_DOC_SYSTEM_*` ·
`HYDRO_API_BASE_URL` khỏi `.env` hai máy (0 dòng mã đọc). `BOOTSTRAP_ADMIN_PASSWORD` còn trong `.env` ⇒ màn
hình báo — gỡ đi.

⭐⭐ **17/09 — độ dài token thôi ⛔ còn quan trọng, và đây là lý do phải ghi lại.** Bản T61.5 nhúng
token vào **KHOÁ** của một `map` nginx, nên `openssl rand -hex 32` (**64 ký tự**) cho khoá
`"Bearer " + 64` = **71 byte**, vượt `map_hash_bucket_size` mặc định **64** ⇒ `[emerg]` ⇒ nginx ⛔
khởi động nổi ⇒ **toàn bộ staging chết**. Tức **làm ĐÚNG hướng dẫn ngay bên dưới là tạo ra sự cố**
(§11.27 · luật 36). Đã vá bằng cách bỏ `map`, so trực tiếp trong `location` ⇒ độ dài thôi ⛔ còn là
một tham số. ⛔ Nếu về sau ai đưa một giá trị `.env` vào khoá `map` lần nữa thì bộ canh
`khoaMapKhongDuocMangChoCam` sẽ đỏ — **đừng nâng `map_hash_bucket_size` cho hết đỏ**, nâng trần chỉ
dời quả mìn đi xa hơn.

```bash
# VPS-1 (.env production) — IP công cộng của VPS-2 + token mới
echo "METRICS_ALLOW_IP=<IP-VPS-2>" >> /opt/songnhue/.env
echo "METRICS_BEARER_TOKEN=$(openssl rand -hex 32)" >> /opt/songnhue/.env
grep -c '^METRICS_' /opt/songnhue/.env                       # phải: 2

# VPS-2 (.env staging + giám sát) — ⛔ viết chú thích cùng dòng giá trị
#   METRICS_ALLOW_IP=127.0.0.1 · METRICS_BEARER_TOKEN=<openssl rand -hex 32, KHÁC production>
#   PROD_METRICS_HOST=<ADMIN_DOMAIN production> · PROD_METRICS_BEARER_TOKEN=<= token VPS-1>
#   ALERT_EMAIL_TO · SLACK_WEBHOOK_URL · TELEGRAM_BOT_TOKEN · TELEGRAM_CHAT_ID
#   SMTP_* chép từ .env VPS-1 (T50.13 — dùng chung)
#   MAIL_REDIRECT_TO=<hộp thư nhóm phát triển>  (T61.23 — staging có SMTP mà thiếu ⇒ app ⛔ lên; ⛔ đặt ở VPS-1)
#   HEALTHCHECKS_PING_URL=<Ping URL healthchecks.io, check Period 5' Grace 5'>  (T61.25)
grep -cE '^(METRICS_|PROD_METRICS_|ALERT_EMAIL_TO|SLACK_WEBHOOK_URL|TELEGRAM_|MAIL_REDIRECT_TO|HEALTHCHECKS_PING_URL)' /opt/songnhue/.env   # phải: 10
grep -c '^MAIL_REDIRECT_TO' /opt/songnhue/.env   # VPS-1 (production): phải 0

# Sau khi staging lên bản mới:
docker compose --env-file .env -f compose.observability.yml up -d
curl -s 127.0.0.1:19090/api/v1/targets | grep -o '"job":"songnhue-app-[a-z]*"[^}]*"health":"[a-z]*"'   # cả hai: up
# (từ VPS-2) cửa nginx VPS-1: có token ⇒ 200, thiếu ⇒ 403
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $PROD_METRICS_BEARER_TOKEN" https://<ADMIN_DOMAIN>/actuator/prometheus
curl -s -o /dev/null -w '%{http_code}\n' https://<ADMIN_DOMAIN>/actuator/prometheus
# Bắn thử — phải tới email + Slack + Telegram:
docker exec songnhue-alertmanager amtool alert add ThuCanhBao environment=production severity=critical --annotation=summary="Thử kênh cảnh báo T61.5" --alertmanager.url=http://localhost:9093
# Chuông canh (T61.25): trang healthchecks.io của check phải "up" sau ~5 phút; thử dừng alertmanager ⇒ ~10 phút sau có tin báo
# Thư staging (T61.23): tạo một thông báo có email trên staging ⇒ thư về MAIL_REDIRECT_TO, tiêu đề "[CHUYỂN HƯỚNG → <gốc>]"
# Hạn mức kết xuất (T61.27): /quan-tri/cau-hinh nhóm LIMIT → "Số lượt kết xuất báo cáo mỗi giờ" = 30
# ClamAV (T61.4) trên staging:
docker ps --filter name=songnhue-clamav --format '{{.Status}}'          # healthy
q "SELECT scan_status, count(*) FROM attachments WHERE created_at > now() - interval '1 hour' GROUP BY 1"   # sau khi tải 1 tệp thử: CLEAN
```

### B5. Sau khi A6/A7/A8 gộp và lên staging

`T37.2` load test · `T37.3` LCP từ máy ở Việt Nam (cả lượt ISR nguội) · `DOD2.9` bắn chuông thật ·
`T61.10` khôi phục vào máy trắng + RTO · `DOD0.21` một lượt hỏng **SAU** `up -d`.

**T61.28 ZAP** (sau khi staging lên bản mới): `TARGET_URL=https://staging.songnhue.com ADMIN_URL=https://admin-staging.songnhue.com tools/zap/zap-baseline.sh` — mã thoát 4 ⛔ đọc là sạch (có 429).
**T61.29 tương thích**: tạo một tài khoản đo trên staging ⛔ 2FA, quyền đọc các màn hình chính, rồi `TUONG_THICH_PUBLIC_URL=… TUONG_THICH_ADMIN_URL=… TUONG_THICH_ADMIN_USER=… TUONG_THICH_ADMIN_PASS=… make tuong-thich`.

`T61.11` **diễn tập xoay khoá AES trên staging** (sau khi bản có job `CRYPTO_REENCRYPT` lên staging) —
chỉ khi đã có bản sao lưu vừa chạy: `openssl rand -base64 32` → thêm `AES_KEY_V2`, **giữ** `AES_KEY_V1`,
đổi `AES_KEY_ID=v2` → `docker compose -f compose.prod.yml up -d app` → đo
`q "SELECT status, progress, last_error, result FROM jobs WHERE job_type='CRYPTO_REENCRYPT' ORDER BY id DESC LIMIT 1"`
(kỳ vọng `SUCCEEDED|100||…"conLai": 0…`) + ba câu `split_part` ở runbook `xoay-khoa.md` ra đúng một dòng `v2`.
⛔ Gỡ `AES_KEY_V1` trong lượt diễn tập.

---

## C. Chờ Công ty — ⛔ không chặn Phase 4, chặn nghiệm thu

G8 toạ độ (nhập trên màn hình) · G6/G10 tệp mẫu (→ T42.14) · G13 khoá reCAPTCHA (T28.50) · G9-a mức
ngưỡng · B3 uỷ quyền duyệt (T57.18) · T57.16 số phép tồn · T57.17 ca trực cuối tuần · T37.4 ba cặp
mã trùng giá trị.
