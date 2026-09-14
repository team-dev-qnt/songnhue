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
| A7 | `T61.4` | Dịch vụ ClamAV trong `compose.prod.yml` + nối `APP_CLAMAV_HOST` (staging chờ quyết RAM) | P1 · bảo mật | [ ] |
| A8 | `T61.5` | Alertmanager trong `compose.observability.yml` — cấu hình, **kênh chờ QT chọn** | P1 · NFR-01 | [ ] |
| A9 | `T61.11` | Job `CRYPTO_REENCRYPT` + chống trùng dưới mọi khoá (T51.9, phần mã) — ⬜ diễn tập thật trên staging ở §B | P1 · dữ liệu | [x] |
| A10 | `T61.13` | Bộ canh ĐẾM nơi ném đối số vào mã lỗi ⛔ `{n}`, rồi vá — 44 nơi, 7 chiều THIẾU (người dùng thấy `{1}`) + `JobWorker.last_error` | P2 | [x] |
| A11 | `T61.15` | javadoc T57.7→T57.15 · xoá `hr.spi` rỗng · sửa `nghiem-thu-cong-ttdt-v1.md` | P2 | [x] |
| A12 | `T58.18` · `T25.23` | Bộ canh N+1 · hạ trần màu ghi cứng | P2 | [ ] |
| A13 | `T61.17` | ⛔⛔ Hạn mức khoá theo IP ⇒ 50 cán bộ sau một NAT chung 100 lượt/phút — QT chốt *vá ngay*: backend ✅ (API + kết xuất theo người@IP) · nginx `api_auth` ⬜ chờ đo NAT | P0 | [~] |
| A14 | `T47.17` | Bài vòng khứ hồi biểu mẫu thay-toàn-phần — 3/17 (công trình · bài viết · sửa chữa) | P2 | [~] |
| A15 | `T61.18` | Lối SỬA bản ghi sửa chữa (PUT có 0 nơi gọi) ✅ + mở rộng bộ canh endpoint mồ côi ⬜ | P2 | [~] |

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

### B5. Sau khi A6/A7/A8 gộp và lên staging

`T37.2` load test · `T37.3` LCP từ máy ở Việt Nam (cả lượt ISR nguội) · `DOD2.9` bắn chuông thật ·
`T61.10` khôi phục vào máy trắng + RTO · `DOD0.21` một lượt hỏng **SAU** `up -d`.

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
