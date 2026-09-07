# Di trú dữ liệu staging → production, và đổi tên miền

Ghi lại lượt chạy **thật** ngày 07–08/09/2026. Mọi con số dưới đây là số **đo được**,
không phải ước lượng. Dùng lại tài liệu này cho mọi lượt nhân bản môi trường về sau.

---

## Phần A — Vì sao không thể "khôi phục thẳng bản dump staging"

Ba khuyết tật **CHẶN**, cả ba chỉ lộ ra khi chạy thật trên một bản sao của production.
Không lượt rà tài liệu nào thấy được, và hai trong ba **không thể** xuất hiện trên
cluster vừa dựng lại — tức chính cảnh mà mọi lượt diễn tập trước đây của dự án đã dùng.

### A.1 `pg_restore --clean` vấp bảng phân mảnh khi đích ĐÃ CÓ dữ liệu

```
ERROR: cannot drop index public.hydro_readings_p202708_station_id_measured_at_idx
       because index public.ix_hydro_readings_station_time requires it
Command was: DROP INDEX IF EXISTS public.hydro_readings_p202708_station_id_measured_at_idx;
```

`--clean` phát `DROP INDEX` / `DROP CONSTRAINT` cho **từng phân mảnh**, mà chỉ mục và
ràng buộc của phân mảnh không xoá lẻ được khi bảng cha còn — Postgres bắt xoá ở cha.
Kho có **ba** bảng phân mảnh: `audit_logs`, `hydro_raw_logs`, `hydro_readings`.

⚠ Trên đích **rỗng** những câu ấy là no-op. §10.58 và T11.3-b đều diễn tập trên cluster
vừa dựng lại ⇒ về nguyên tắc không thể thấy. *Đường hay thử thì chạy, đường dùng thật thì hỏng.*

**Vá**: sinh SQL ra tệp, ghép một khối bỏ-bảng-phân-mảnh lên trước, nạp **cả hai trong
một giao dịch**. Tách hai giao dịch thì một lượt nạp hỏng để lại CSDL production không
còn bảng phân mảnh nào.

### A.2 Bộ lọc mục lục để lọt mục `EXTENSION`

Dòng mục lục của extension là `2; 3079 16389 EXTENSION - postgis ` — `pg_dump` **không
ghi chủ sở hữu** cho extension, nên `$NF` là *tên extension*, và `awk '$NF != "postgres"'`
giữ nó lại. Hệ quả: `DROP EXTENSION IF EXISTS postgis;` → `must be owner of extension`.

⚠ §10.58 quy lỗi ấy cho mục `COMMENT - EXTENSION` và vá nhầm chỗ — hai trạng thái khác
nhau in ra cùng một câu.

**Vá**: thêm vế `grep -vE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION '`, kèm phép chốt đếm = 0
và phép chốt "đích phải có sẵn đủ 3 extension" (bộ canh tự nói ra tiền đề của nó).

### A.3 ⛔⛔ Dữ liệu staging mang quyền YẾU HƠN và ghi đè bảo đảm append-only

Đo trên bản sao: `songnhue_app` trên staging có **`arwd`** (sửa + xoá) ở ~35 bảng mà
production cố ý chỉ cho `ar`/`r`:

| Bảng | production | staging |
|---|---|---|
| `audit_logs` + 15 phân mảnh | `ar` | `arwd` |
| `audit_chain_head` | *(app không có quyền nào)* | `arwd` |
| `audit_archive_anchors` | `r` | `arwd` |
| `hydro_raw_logs` + 13 phân mảnh | `ar` | `arwd` |
| `security_events` | `ar` | `arwd` |
| `flyway_schema_history` | `r` | `arwd` |

`pg_dump` mang ACL theo dữ liệu ⇒ khôi phục nguyên trạng là **âm thầm hạ cấp** production:
vai trò runtime sửa/xoá được nhật ký kiểm toán (phá luật 18 — hash chain đang ký tên vào
lịch sử), sửa được `hydro_raw_logs` (luật 8 — bản sao **duy nhất** của nguồn không có API
lịch sử), và ghi được cả sổ migration.

Nguyên nhân: lượt khôi phục staging 26/8 chạy bản `restore.sh` còn `--no-privileges` —
đúng thứ §10.58 ghi là *"`ALTER DEFAULT PRIVILEGES` cứu"*. Nó cứu app khỏi chết và cùng
lúc xoá mọi câu `REVOKE`. **Staging đã chạy như thế từ 26/8.**

**Vá**: mục ⑥ của `sau-khoi-phuc-production.sql` **tái khẳng định nguồn sự thật** — chép
nguyên văn phần "2. Siết các bảng append-only" của `V202608131006__core_db_role_grants.sql`
cộng phần tương ứng của `V202609041059__hyd_time_series.sql`. Tái khẳng định *migration*,
không chép lại một *ảnh chụp* — ảnh chụp cũng có thể đã sai.

### A.4 Ba migration seed staging có mà production không giải được

`flyway_schema_history`: staging 63 hàng, production 60, **cùng đỉnh** `202609071069`.
Chênh đúng ba tệp `seed_portal_*` ở `classpath:db/seed/portal`, chỉ mở khi `SEED_LOCATION`
trỏ vào đó. Với `validate-on-migrate: true`, ba hàng mồ côi làm app **không khởi động được**.

⛔ Cấm chữa bằng cách đặt `SEED_LOCATION` ở production: migration seed mở đầu bằng
`DELETE FROM articles` — để lại một khẩu súng lên đạn chĩa vào đúng đường khôi phục thảm hoạ.

### A.5 `restore.sh` không chạy được trên máy chủ nào

Đo trên **cả hai** VPS: không máy nào có `psql`/`pg_dump`/`pg_restore` trên host,
`DB_HOST=postgres` chỉ phân giải trong mạng docker, container postgres không publish cổng,
`deploy/env/prod.env` không tồn tại ở đó. Đường khôi phục thủ công **duy nhất** của hệ —
chính là thứ T7.13-a đã vá — chỉ chạy được ở máy dev.

**Vá**: `deploy/backup/khoi-phuc-qua-container.sh`, giữ nguyên cả 8 bảo đảm của
`restore.sh` nhưng đi qua `docker exec`, đúng cách `pre-deploy-dump.sh` đã làm.

---

## Phần B — Trình tự đã chạy

| # | Việc | Số đo |
|---|---|---|
| 1 | Chụp production sau khi verify admin | `predeploy-…232828.dump` **656.077 B**, sha256 khớp, kéo về máy thứ hai |
| 2 | Chụp danh tính superadmin của production | 13 câu lệnh, bám `username`, **0** dòng bám `id=1` |
| 3 | Chụp CSDL staging | `di-tru-staging-20260907-2330.dump` **1.250.768 B**, checksum khớp qua 3 máy |
| 4 | Diễn tập trên CSDL nháp `songnhue_thu` | bản sao đúng: `icu=vi-VN`, `datacl`+`nspacl` giống từng ký tự, 107 bảng, 15 phân mảnh |
| 5 | Chép media (**byte trước, hàng sau**) | `mc mirror` files-staging → production: **1 → 123 đối tượng**, 93.966.947 B, 88,66 MiB/4 giây |
| 6 | Khôi phục + khối vá | thoát 0, 10 phép chốt đạt |
| 7 | Nghiệm thu | `articles=28 attachments=120 banners=8 org_units=13 users=4 flyway=60` · chuỗi băm audit **rỗng = nguyên vẹn** · `songnhue_app` đọc được · `Anh < Dung < Đăng < Em` |

**Bằng chứng hai trạng thái** cho mục ⑥ (luật 1): trước khi vá, 5/5 phép hỏi
`has_table_privilege` trả `t`; sau khi vá, 5/5 trả `f`, mà `INSERT` trên `audit_logs`
vẫn `t` — siết đúng chỗ, không siết quá tay.

**Thứ tự media/CSDL**: chép byte **trước**. Nếu ngược lại thì có một khoảng thời gian CSDL
trỏ vào tệp chưa tồn tại. Chép thừa một object thì vô hại.

---

## Phần C — Đổi tên miền sang `thuyloisongnhue.vn`

Công ty yêu cầu dùng `thuyloisongnhue.vn` vì văn bản duyệt cho tên miền này.

### C.1 Hiện trạng lúc bắt đầu (đo 08/09)

```
thuyloisongnhue.vn        → 27.71.16.154  → 308 sang https → TLS THẤT BẠI (không có chứng chỉ)
www.thuyloisongnhue.vn    → 27.71.16.154  → y hệt
admin.thuyloisongnhue.vn  → 27.71.27.75   ⚠ STAGING
files.thuyloisongnhue.vn  → 27.71.27.75   ⚠ STAGING
```

Tức tên miền chính thức của Công ty **hỏng hoàn toàn** với người dùng thật, và hai tên
con đang trỏ vào máy staging.

### C.2 Kiểm kê — kho KHÔNG ghi cứng tên miền

`grep -rn "songnhue\.com"` trên `*.ts|*.tsx|*.java|*.yml|*.conf|*.template|*.sql`
(bỏ các dòng nói về staging): **0 dòng**. Tất cả đi qua env. Cần đổi:

| Nơi | Khoá | Ghi chú |
|---|---|---|
| `.env` production | `PUBLIC_DOMAIN` | nginx `server_name` + đường dẫn chứng chỉ |
| `.env` production | `APP_BASE_URL` | ⚠ **biến mồ côi** — không dòng mã nào đọc (`deploy-production-guideline.md:602`) |
| `.env` production | `NEXT_PUBLIC_SITE_URL` | ⚠ **cũng không ai đọc lúc chạy** — khối `environment:` của `public-web` chỉ có `PORT`, `HOSTNAME`, `REVALIDATE_SECRET`, `API_INTERNAL_BASE_URL`. Giá trị thật đến từ `ENV` nướng trong image |
| biến kho GitHub | `PUBLIC_SITE_URL` | → `ci.yml:520` build-arg → `ENV` của image → sitemap, canonical, OG |
| secret môi trường | `PROD_BASE_URL` | `deploy-prod.yml:171`, dùng cho smoke test |
| Let's Encrypt | chứng chỉ mới | nginx **không khởi động được** nếu đường dẫn chứng chỉ không tồn tại |

**Không có CORS** trong toàn kho — kiến trúc same-origin, khối `admin.` tự proxy `/api/`.
Nên đổi `PUBLIC_DOMAIN` **không thể** làm hỏng giao diện quản trị.

### C.3 Thứ tự bắt buộc

1. **Chứng chỉ trước.** Khối `server_name _;` đã phục vụ `/.well-known/acme-challenge/`
   cho *mọi* tên miền, nên xin bằng webroot được **mà không dừng nginx**. Kiểm trước bằng
   một tệp thử gọi từ Internet qua chính tên miền mới (đo được: 200 + đúng nội dung).
2. Sửa `.env` (3 dòng), **tạo lại** nginx — `restart` không đủ: envsubst chạy lúc
   entrypoint với môi trường đã đóng băng từ lúc container được tạo.
   ⚠ `docker compose up -d --no-deps --force-recreate nginx` cần `APP_IMAGE`/`ADMIN_IMAGE`/
   `PUBLIC_IMAGE` — lấy từ chính container đang chạy, đừng đoán.
3. Đổi biến kho + secret GitHub.
4. Một commit chạm `deploy/` hoặc `frontend/` → CI dựng lại `public-web` với URL mới →
   đề bạt `dev → staging → production`. Chỉ sau bước này thì sitemap/canonical/OG mới đúng.

### C.4 Còn treo — chờ VNPT sửa DNS

`admin.` và `files.` vẫn trỏ `27.71.27.75`. Chừng nào chưa sửa thì **giữ nguyên**
`ADMIN_DOMAIN`/`FILES_DOMAIN` ở `songnhue.com`.

⛔ `FILES_DOMAIN` phải đổi **cuối cùng**: presigned URL ký cả tên máy, đổi khi chưa có
DNS + chứng chỉ là mọi nút Tải về hỏng ngay.

Sau khi VNPT sửa:
```bash
# 1. chứng chỉ
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v songnhue_certbot-webroot:/var/www/certbot \
  certbot/certbot:v5.8.0 certonly --webroot -w /var/www/certbot \
  -d admin.thuyloisongnhue.vn --key-type ecdsa --non-interactive --agree-tos
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt -v songnhue_certbot-webroot:/var/www/certbot \
  certbot/certbot:v5.8.0 certonly --webroot -w /var/www/certbot \
  -d files.thuyloisongnhue.vn --key-type ecdsa --non-interactive --agree-tos
# 2. .env  →  ADMIN_DOMAIN=admin.thuyloisongnhue.vn  ·  FILES_DOMAIN=files.thuyloisongnhue.vn
# 3. tạo lại nginx (xem C.3 bước 2)
```

`SMTP_FROM=no-reply@songnhue.com` giữ nguyên cho tới khi xác nhận máy chủ SMTP cho phép
gửi thay mặt miền mới — đổi trước là thư đi vào hộp rác hoặc bị từ chối thẳng.

---

## Phần D — Cron gia hạn TLS: chưa bao giờ chạy được

Chạy đúng dòng cron đang cài, chế độ thử khô:

```
$ docker compose --env-file .env -f compose.prod.yml --profile certbot run --rm certbot \
      renew --webroot -w /var/www/certbot --dry-run
error while interpolating services.app.image: required variable APP_IMAGE is missing a value
MÃ THOÁT = 1
```

Compose **nội suy toàn bộ tệp** trước khi trả lời bất cứ câu hỏi nào, kể cả lệnh chỉ đụng
service `certbot`. Ba biến `*_IMAGE` cố ý không nằm trong `.env`. Vế sau của cron
(`docker compose exec nginx -s reload`) hỏng y hệt.

Đây là lần thứ **ba** cùng một lỗi (`seed.sh`, `pre-deploy-dump.sh`, §10.48) và là lần đầu
nó nằm trên đường giữ HTTPS còn sống. Chứng chỉ hết hạn **06/12/2026**; cron không gửi thư
đi đâu nên nó chỉ lộ ra vào đúng ngày ấy.

**Vá**: `deploy/gia-han-tls.sh` — `docker run` + `docker exec`, không compose. Kèm hai lỗi
phụ đã vá trong lúc làm:

- `/var/log/songnhue` là `ubuntu:ubuntu 755`, mà cron chạy bằng `songnhue` **uid 1001**
  còn container ghi bằng **uid 1000** ⇒ cron không ghi nổi log ⇒ lại hỏng câm. Nay `2775`
  (setgid), và `host-prepare.sh` đã sửa để máy dựng mới không dính lại.
- Bản đầu của script in `còn -20703 ngày` cho mọi chứng chỉ: `date -d "<chuỗi GMT>"` không
  chạy dưới busybox của image Alpine. Một con số sai đọc như một con số đúng — nay dùng
  `openssl x509 -checkend`, phép đo nhị phân, phân biệt được hai trạng thái (luật 9).

Nghiệm thu: chạy **đúng dòng cron**, thoát 0, log ghi được, nginx nạp lại.

---

## Phần E — Việc còn lại

- ⬜ VNPT sửa `admin.` + `files.` → `27.71.16.154`, rồi làm C.4.
- ⬜ `songnhue.com` nay **không còn khối server nào phục vụ** (TLS thất bại). Nếu muốn giữ
  liên kết cũ sống thì cần thêm khối chuyển hướng 301 vào `default.conf.template` — chưa làm.
- ⬜ Xoá hai thư mục media rác (`Staging test` 1 ảnh, `CongBoThongTin-TEST` 0 ảnh) **ở màn
  hình quản trị**, để đi qua soft-delete + audit đúng luật 9. Cố ý không xoá bằng SQL:
  `attachments` không có cột thư mục, liên kết đi qua `owner_type`/`owner_id` nên xoá thẳng
  để lại ảnh mồ côi mà không khoá ngoại nào chặn.
- ⬜ Hai tài khoản đội phát triển (`nguyetmoon`, `huynq1`) đã **khoá** — cả hai vốn mang
  vai trò `SUPER_ADMIN` trên staging với mật khẩu bcrypt dùng được ở mọi môi trường. Mở lại
  bằng một cú bấm ở màn hình quản trị khi cần.
- ⬜ `audit_logs` giữ nguyên 2223 hàng của staging: chúng đi cùng `audit_chain_head` nên
  chuỗi băm tự kiểm được, xoá là phá bất biến (luật 18). Ghi một dòng vào hồ sơ bàn giao
  nói rõ nhật ký trước 08/09/2026 mô tả thao tác trong giai đoạn dựng hệ.
- ⬜ Tệp `quy-hoach-HN251109-1.webp` người dùng tải lên lúc verify: hàng CSDL bị ghi đè mất,
  **byte vẫn nằm trong MinIO** (~94 KB) và không ai trỏ tới. Kho không có job dọn rác object.
