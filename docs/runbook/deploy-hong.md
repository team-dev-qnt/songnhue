# Deploy xong mà site không trả lời

> **Tình huống**: `CD Staging` / `CD Production` đỏ ở *Smoke test*, hoặc site trả
> `Couldn't connect` / 502 / trắng trang sau một lượt triển khai.

## ⛔ Điều đầu tiên: ĐỪNG khôi phục CSDL

Khôi phục CSDL là thao tác **phá huỷ nhất** của hệ này — nó ghi đè dữ liệu đã nhập
sau bản chụp. Trong sự cố **17/09/2026** thông điệp của bước quay lui khẳng định
*"nhiều khả năng migration đã đổi lược đồ"* rồi trỏ thẳng sang
[`khoi-phuc-du-lieu.md`](khoi-phuc-du-lieu.md) — và câu đó **sai**: log của chính
lượt ấy ghi `Container songnhue-app Healthy` trên ảnh **cũ**, tức bản cũ chạy được
trên lược đồ đã migrate. Thứ chết là nginx.

Khôi phục CSDL khi ứng dụng vẫn khoẻ thì **không chữa gì** và **xoá mất dữ liệu mới**.

## 0. Bước quay lui TỰ ĐỘNG đã làm gì — đọc log của nó trước (từ WS-71, 19/09/2026)

Trước WS-71 bước *Quay lui bản cũ* chỉ `up -d` ba **ảnh** cũ; cấu hình `deploy/` vừa rsync
(compose, template nginx) ở lại trên máy — nên ngày 17/09 nó dựng ảnh cũ trên cấu hình nginx
hỏng và site vẫn chết. Nay mỗi lượt CD:

1. **Chụp** `/opt/songnhue` (trừ `.env*`, `env/`, `keys/`) vào `/opt/songnhue/.ban-truoc/`
   TRƯỚC khi đồng bộ, kèm mốc `.ban-truoc.moc` = số lượt chạy.
2. Hỏng ⇒ bước quay lui **trả cấu hình** về bản chụp của ĐÚNG lượt ấy → `nginx -t` →
   tạo lại cả bốn container (`--force-recreate`) → so ID ảnh → chờ nginx `healthy`.

| Dòng trong log bước quay lui | Nghĩa |
|---|---|
| `✓ đã trả CẤU HÌNH về bản chụp đầu lượt …` | cấu hình đã về bản cũ |
| `⛔ có bản chụp cấu hình của lượt …` (warning) | chỉ trả được ẢNH — cấu hình của lượt hỏng VẪN trên máy ⇒ đi mục 2 |
| `✓ app/admin-app/public-web đã quay về đúng ảnh cũ` · `✓ nginx đang phục vụ sau quay lui` | quay lui dựng lại được — đo từ ngoài ở mục 5 rồi xong |
| `Cấu hình nginx sau khi trả về vẫn ⛔ hợp lệ` | bản chụp cũng hỏng, hoặc lỗi nằm ở `.env` (⛔ được chụp) ⇒ mục 2 |

⚠ Quay lui bằng tay (`CD Production` → *Run workflow* với `commit_sha` cũ) nay dùng `deploy/`
**của chính SHA ấy** — ảnh và cấu hình luôn đi cùng một bản.

**Diễn tập (DOD0.21, CHỈ staging):** đặt biến `DIEN_TAP_QUAY_LUI=true` ở **environment `staging`**
(⛔ ở cấp kho) → gộp một PR đề bạt vào `staging` → bước *Diễn tập quay lui* đỏ có chủ đích SAU
`up -d` → bước quay lui phải thoát 0 với đủ các dòng `✓` ở bảng trên → site trả lời → **xoá biến**.

## 1. Đo tầng nào đang chết — 30 giây

Ba câu hỏi, ba việc khác hẳn nhau. Hỏi đủ ba trước khi làm bất cứ điều gì.

```bash
cd /opt/songnhue

# (a) Tầng nào?
docker compose --env-file .env -f <compose.staging.yml|compose.prod.yml> ps

# (b) Có gì lắng nghe ở cổng web không?
ss -ltn | grep -E ':80 |:443 '

# (c) Nếu nginx không khoẻ — nó tự nói lý do
docker logs --tail 40 songnhue-nginx
```

⚠ `docker compose ps` cần `APP_IMAGE` / `ADMIN_IMAGE` / `PUBLIC_IMAGE`; chúng chỉ do
workflow export. Gõ tay thì lấy từ container đang chạy:

```bash
export APP_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-app)
export ADMIN_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-admin-app)
export PUBLIC_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-public-web)
```

### Bảng đọc kết quả

| Đo được | Nghĩa là | Đi tiếp |
|---|---|---|
| `app` **không** healthy | Bản đang chạy không sống được trên lược đồ hiện tại | Mục **3**, rồi mới tới [`khoi-phuc-du-lieu.md`](khoi-phuc-du-lieu.md) |
| `app` healthy, **`nginx` Restarting/unhealthy** | CSDL và ứng dụng **đều ổn** | Mục **2**. ⛔ Không đụng CSDL |
| cả hai healthy, ngoài vẫn không vào được | Tầng ngoài container | Mục **4** |
| `postgres` không healthy | CSDL không lên | [`khoi-phuc-du-lieu.md`](khoi-phuc-du-lieu.md) |

⚠⚠ **`Started` trong log deploy KHÔNG có nghĩa là đang phục vụ.** Nó chỉ nói tiến
trình container đã khởi động. Với `restart: unless-stopped`, một container chết ngay
khi khởi động sẽ **quay vòng mãi** và compose vẫn in đúng một dòng `Started`. Trạng
thái đáng tin là `docker ps` (`Restarting (N)`) và `.State.Health.Status`.

## 2. nginx không lên

`docker logs songnhue-nginx` in lý do ở dòng `[emerg]` **đầu tiên** — đọc dòng đầu,
không đọc dòng cuối (log quay vòng nên dòng cuối chỉ là lượt thử gần nhất).

### Đã gặp

| Dòng `[emerg]` | Nguyên nhân | Chữa |
|---|---|---|
| `could not build map_hash, you should increase map_hash_bucket_size: 64` | Một khoá `map` dài quá 64 byte. **17/09/2026**: khoá `"Bearer <token 64 ký tự>"` = 71 byte hạ toàn bộ staging | Bỏ chỗ cắm khỏi **khoá** `map` (so trực tiếp trong `location`). Bộ canh: `khoaMapKhongDuocMangChoCam` |
| `invalid parameter "..."` ở `allow`/`deny` | Giá trị trong `.env` không phải IP/CIDR — hay gặp nhất là **chú thích viết cùng dòng giá trị** | `grep -nE '^(METRICS_\|PROD_METRICS_)' .env`, bỏ phần chú thích |
| `cannot load certificate ... No such file` | Chứng chỉ TLS chưa cấp hoặc đã hết hạn và bị dọn | [`ten-mien-va-chung-chi.md`](ten-mien-va-chung-chi.md) |
| `host not found in upstream` | Một service `depends_on` chưa lên | Đo `docker compose ps`, dựng service đó trước |

### Sửa rồi kiểm TRƯỚC khi chạm nginx thật

```bash
cp nginx/templates/default.conf.template nginx/templates/default.conf.template.bak-$(date +%Y%m%d%H%M)
# ... sửa ...
docker compose --env-file .env -f <compose> run --rm --no-deps nginx nginx -t
```

⚠⚠ `--no-deps` **bắt buộc**. Thiếu nó thì `docker compose run` dựng cả chuỗi
`depends_on` — một lệnh *"chỉ kiểm cấu hình"* lại dựng cluster ngoài quy trình
(§10.78 đã trả giá đúng chuyện này).

⚠ Giữ **entrypoint mặc định**, chỉ đổi *command*. `envsubst` chạy trong
`/docker-entrypoint.d/`, nên `--entrypoint nginx` sẽ kiểm bản template **chưa thay
biến** — xanh trên một tệp không bao giờ được nạp.

`nginx -t` nói *successful* rồi mới:

```bash
docker compose --env-file .env -f <compose> up -d --force-recreate nginx
for i in $(seq 1 24); do
  tt=$(docker inspect -f '{{.State.Health.Status}}' songnhue-nginx 2>/dev/null || echo khong-ro)
  [ "$tt" = healthy ] && { echo "✓ healthy sau $((i*5))s"; break; }
  sleep 5
done
```

⛔⛔ **Bản vá gõ tay trên máy chủ sống được tới lượt deploy kế tiếp.** `deploy.yml`
rsync `deploy/nginx/templates` mỗi lượt, nên nó sẽ **ghi đè** bằng bản trong kho.
Sửa trên máy chủ là để *dựng lại dịch vụ ngay*; bản vá thật phải vào kho.

## 3. app không lên

```bash
docker logs --tail 60 songnhue-app | grep -iE 'error|caused by|failed'
```

- **Dừng khởi động có chủ đích** (`IllegalStateException` lúc dựng bean) — đọc nguyên
  văn câu lỗi, nó nói đúng biến nào thiếu. Ví dụ đã gặp: `MAIL_REDIRECT_TO` thiếu
  trên staging khi `SMTP_HOST` đã đặt.
- **Flyway đỏ** — khi đó mới là trường hợp lược đồ, đi tiếp
  [`khoi-phuc-du-lieu.md`](khoi-phuc-du-lieu.md).
- **Không có log nào** — container chưa từng khởi động được: kiểm image đã kéo về chưa
  (`docker image inspect <ref>`).

## 4. Hai tầng đều khoẻ mà ngoài không vào được

Theo thứ tự từ trong ra:

```bash
curl -sI http://127.0.0.1/healthz          # nginx tự trả, không đi tới app
dig +short <tên miền>                       # DNS còn trỏ đúng IP máy này?
ufw status | head                           # tường lửa host
```

⚠ Nhà cung cấp (Viettel IDC) báo **máy đang bật** chỉ có nghĩa là máy ảo đang chạy —
nó không nói gì về dịch vụ. Dấu hiệu phân biệt: **cổng 22 vào được mà 80/443 thì
không** ⇒ máy sống, phần mềm chết; đi lại từ mục **1**.

## 5. Xác nhận đã xong — đo từ NGOÀI

Đo trong máy không đủ: nó bỏ qua DNS, TLS và tường lửa.

```bash
B=https://<tên miền>
curl -sS -o /dev/null -w 'trang chủ  HTTP %{http_code} · %{size_download} byte · %{time_total}s\n' "$B/"
curl -fsS "$B/api/v1/public/site-config" | head -c 80; echo
curl -fsS "$B/api/v1/public/articles?page=0&size=100" | grep -o '"slug":' | wc -l
```

Ba câu này chính là smoke test của CD: nginx sống · đi hết chặng
`nginx → public-web → app → postgres` · cổng có nội dung thật.

## 6. Sau khi dịch vụ sống lại

1. Bản vá đã vào kho chưa (mục **2**, khối ⛔⛔)?
2. Có bộ canh nào bắt được nó ở lượt sau chưa? Một sự cố hạ cả site mà chỉ để lại một
   dòng runbook thì lần sau vẫn mất đúng chừng ấy thời gian.
3. Ghi vào `.claude/master-tracking.md` kèm **số đo**, và nguyên nhân gốc vào
   `architecture-review.md`.
