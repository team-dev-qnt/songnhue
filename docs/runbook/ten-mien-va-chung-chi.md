# Đổi tên miền và cấp / gia hạn chứng chỉ TLS

> Viết sau lượt đổi `songnhue.com → thuyloisongnhue.vn` ngày 08/09/2026 (Công ty yêu cầu vì văn bản
> duyệt cấp cho tên miền này). Mọi con số là số **đo được**.
>
> Dùng tài liệu này khi: thêm một tên miền · đổi tên miền đang phục vụ · chứng chỉ sắp hết hạn ·
> `certbot` báo lỗi.

---

## 0. Ba điều phải biết trước

1. **nginx KHÔNG khởi động được nếu đường dẫn chứng chỉ không tồn tại.** Mọi khối `server` trỏ
   `/etc/letsencrypt/live/${DOMAIN}/fullchain.pem`. ⇒ **chứng chỉ trước, `.env` sau.** Làm ngược là
   tự đánh sập cổng.

2. **`restart` container nginx là KHÔNG ĐỦ.** `envsubst` chạy ở entrypoint với môi trường **đóng
   băng lúc container được tạo**. Đổi `.env` xong phải `--force-recreate`.

3. ⛔ **`FILES_DOMAIN` đổi CUỐI CÙNG.** Presigned URL của MinIO ký cả tên máy; đổi khi chưa có DNS +
   chứng chỉ là **mọi nút Tải về hỏng ngay lập tức**.

---

## 1. Kiểm kê — những nơi mang tên miền

Kho **không ghi cứng tên miền một dòng nào**. Đo 08/09:
```bash
grep -rn "songnhue\.com" --include='*.ts' --include='*.tsx' --include='*.java' \
     --include='*.yml' --include='*.conf' --include='*.template' --include='*.sql' . \
  | grep -v node_modules | grep -v '/target/' | grep -vi staging | wc -l      # → 0
```

Tất cả đi qua env:

| Nơi | Khoá | Có hiệu lực lúc nào | Ghi chú |
|---|---|---|---|
| `.env` máy chủ | `PUBLIC_DOMAIN` | khi **tạo lại** nginx | `server_name` + đường dẫn chứng chỉ |
| `.env` máy chủ | `ADMIN_DOMAIN` | như trên | |
| `.env` máy chủ | `FILES_DOMAIN` | như trên | ⛔ đổi cuối cùng |
| `.env` máy chủ | `APP_BASE_URL` | — | ⚠ **biến mồ côi, không dòng mã nào đọc** |
| `.env` máy chủ | `NEXT_PUBLIC_SITE_URL` | — | ⚠ **cũng không ai đọc lúc chạy** (xem dưới) |
| `.env` máy chủ | `SMTP_FROM` | khi tạo lại `app` | đổi sau khi xác nhận SMTP cho phép gửi thay mặt miền mới |
| Biến kho GitHub | `PUBLIC_SITE_URL` | **lúc BUILD image** | sitemap · canonical · Open Graph |
| Secret môi trường | `PROD_BASE_URL` | lúc CD chạy smoke test | |
| Let's Encrypt | chứng chỉ | ngay | |

### ⚠ `NEXT_PUBLIC_SITE_URL` trong `.env` không có tác dụng

Khối `environment:` của service `public-web` trong `compose.prod.yml` chỉ có **bốn** biến: `PORT`,
`HOSTNAME`, `REVALIDATE_SECRET`, `API_INTERNAL_BASE_URL`. Giá trị thật đến từ `ENV` **nướng vào
image** lúc build, qua `ci.yml` → `build-args: NEXT_PUBLIC_SITE_URL=${{ vars.PUBLIC_SITE_URL }}`.

⇒ Sitemap, thẻ canonical và Open Graph **chỉ đổi sau một lượt dựng lại image và đề bạt**. Sửa `.env`
không đủ. Đây là lý do bước 4 của §3 tồn tại.

### ✅ Không có CORS

Toàn kho **không có** cấu hình CORS — kiến trúc same-origin, khối `admin.` tự proxy `/api/` sang
app. ⇒ Đổi `PUBLIC_DOMAIN` **không thể** làm hỏng giao diện quản trị. (Cái bẫy đã trả giá suốt
WS-8→WS-20 không áp ở đây.)

---

## 2. Cấp chứng chỉ mới — không cần dừng nginx

Khối `server_name _;` đã phục vụ `/.well-known/acme-challenge/` cho **mọi** tên miền, kể cả tên chưa
có khối `server` riêng. Nên `--webroot` chạy được với nginx đang lên.

### 2.1 Kiểm đường ACME **trước**, bằng một tệp thử

```bash
# đặt tệp thử
docker run --rm -v songnhue_certbot-webroot:/w alpine:3.20 sh -c \
  'mkdir -p /w/.well-known/acme-challenge && echo THU-NGHIEM > /w/.well-known/acme-challenge/kiem-thu'

# gọi TỪ INTERNET, qua chính tên miền cần cấp
curl -sS "http://<tên-miền-mới>/.well-known/acme-challenge/kiem-thu"     # phải in THU-NGHIEM

# dọn
docker run --rm -v songnhue_certbot-webroot:/w alpine:3.20 rm -f /w/.well-known/acme-challenge/kiem-thu
```

Bước này rẻ và loại bỏ mọi phỏng đoán về DNS, tường lửa, định tuyến.

### 2.2 Thử khô rồi cấp thật

⛔ **Không dùng `docker compose`** — xem §4.

```bash
CB="docker run --rm -v /etc/letsencrypt:/etc/letsencrypt \
      -v songnhue_certbot-webroot:/var/www/certbot certbot/certbot:v5.8.0"

# thử khô trước, để không đốt hạn mức của Let's Encrypt nếu có gì sai
$CB certonly --webroot -w /var/www/certbot \
    -d thuyloisongnhue.vn -d www.thuyloisongnhue.vn \
    --key-type ecdsa --non-interactive --agree-tos --dry-run

# thật
$CB certonly --webroot -w /var/www/certbot \
    -d thuyloisongnhue.vn -d www.thuyloisongnhue.vn \
    --key-type ecdsa --non-interactive --agree-tos
```

Đối chiếu sau khi cấp:
```bash
docker run --rm -v /etc/letsencrypt:/etc/letsencrypt:ro alpine/openssl \
  x509 -in /etc/letsencrypt/live/<miền>/cert.pem -noout -ext subjectAltName -enddate
```

> `key_type = ecdsa` và tài khoản ACME đã đăng ký sẵn (không có email). Chứng chỉ cũ dùng
> `authenticator = standalone` vì cấp lúc nginx chưa chạy; cron gia hạn truyền `--webroot` nên vẫn
> đúng.

---

## 3. Đổi tên miền — 5 bước, đúng thứ tự này

### Bước 1 — chứng chỉ (§2)

### Bước 2 — `.env` trên máy chủ

```bash
cd /opt/songnhue
cp -p .env /var/lib/songnhue/backup/env-truoc-doi-ten-mien-<ngày>.bak   # ⚠ ĐỂ NGOÀI /opt, xem §5
chmod 600 /var/lib/songnhue/backup/env-truoc-doi-ten-mien-<ngày>.bak
sed -i -e 's|^PUBLIC_DOMAIN=.*$|PUBLIC_DOMAIN=<miền mới>|' \
       -e 's|^APP_BASE_URL=.*$|APP_BASE_URL=https://<miền mới>|' \
       -e 's|^NEXT_PUBLIC_SITE_URL=.*$|NEXT_PUBLIC_SITE_URL=https://<miền mới>|' .env
diff <(cat /var/lib/songnhue/backup/env-truoc-doi-ten-mien-<ngày>.bak) .env | grep -c '^>'   # phải = 3
```

### Bước 3 — tạo lại nginx

⚠ `docker compose` cần `APP_IMAGE`/`ADMIN_IMAGE`/`PUBLIC_IMAGE`, mà chúng **cố ý không nằm trong
`.env`**. Lấy từ chính container đang chạy, đừng đoán:

```bash
export APP_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-app)
export ADMIN_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-admin-app)
export PUBLIC_IMAGE=$(docker inspect -f '{{.Config.Image}}' songnhue-public-web)

docker compose --env-file .env -f compose.prod.yml config --quiet          # nội suy sạch?
docker compose --env-file .env -f compose.prod.yml up -d --no-deps --force-recreate nginx

docker exec songnhue-nginx sh -c 'grep -h server_name /etc/nginx/conf.d/*.conf'
```

`--no-deps` để không kéo theo cả chuỗi `depends_on` — bài học §10.78(D), nơi một lệnh *"chỉ kiểm cấu
hình"* dựng luôn cả cluster postgres ngoài quy trình.

### Bước 4 — GitHub, rồi dựng lại image

```bash
gh variable set PUBLIC_SITE_URL --repo <kho> --body 'https://<miền mới>'
gh secret set PROD_BASE_URL --env production --repo <kho> --body 'https://<miền mới>'
```

Rồi **một commit chạm `deploy/` hoặc `frontend/`** (bộ lọc frontend của CI là
`^(frontend/|deploy/|\.github/workflows/)`) → CI dựng lại `public-web` → đề bạt `dev → staging →
production`.

### Bước 5 — nghiệm thu

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://<miền mới>/            # 200
curl -sS -o /dev/null -w '%{http_code}\n' https://www.<miền mới>/        # 200
curl -sS https://<miền mới>/robots.txt | grep -E '^Host|^Sitemap'       # phải là MIỀN MỚI
curl -sS https://<miền mới>/sitemap.xml | head -4                        # <loc> phải là MIỀN MỚI
```

⚠ **`robots.txt` và `sitemap.xml` là phép phân biệt hai trạng thái**: chúng vẫn nói tên miền cũ cho
tới khi image mới được đề bạt, **dù CD báo xanh**. Trước bước 4 hoàn tất, `curl` trang chủ vẫn còn
tên miền cũ ở canonical/OG — đo được 8 chỗ trong HTML.

---

## 4. ⛔ Cron gia hạn TLS — cái bẫy đã trả giá

Dòng cron cài lúc go-live dùng `docker compose`. Chạy đúng nó, chế độ thử khô:

```
$ docker compose --env-file .env -f compose.prod.yml --profile certbot run --rm certbot \
      renew --webroot -w /var/www/certbot --dry-run
error while interpolating services.app.image: required variable APP_IMAGE is missing a value
MÃ THOÁT = 1
```

**Compose nội suy TOÀN BỘ tệp trước khi trả lời bất cứ câu hỏi nào** — kể cả lệnh chỉ đụng service
`certbot`. Ba biến `*_IMAGE` cố ý không nằm trong `.env` (xem `deploy/lib/docker-svc.sh`). Vế sau của
cron, `docker compose exec nginx -s reload`, hỏng y hệt.

⇒ **Cron chưa bao giờ gia hạn được gì.** Chứng chỉ hạn 06/12/2026; cron không gửi thư đi đâu nên nó
chỉ lộ ra vào đúng ngày ấy.

Đây là lần thứ **ba** cùng một lỗi trong dự án (`seed.sh`, `pre-deploy-dump.sh`, §10.48) — và lần đầu
nó nằm trên đường giữ HTTPS còn sống. ⚠ Chú thích cảnh báo đúng lỗi này **đã nằm sẵn** ở đầu
`docker-svc.sh`, liệt kê hai nạn nhân trước. *Một chú thích không phải một cổng kiểm.*

### Bản đúng

[`deploy/gia-han-tls.sh`](../../deploy/gia-han-tls.sh) — `docker run` + `docker exec`, không compose.

```cron
17 3 * * 1 /opt/songnhue/gia-han-tls.sh >> /var/log/songnhue/gia-han-tls.log 2>&1
```

Hai lỗi phụ đã vá cùng lượt, cả hai thuộc họ *"hỏng câm"*:

- **`/var/log/songnhue` không ghi được.** Là `ubuntu:ubuntu 755`, mà cron chạy bằng user triển khai
  **uid 1001** còn container ghi bằng **uid 1000**. Nhóm chỉ có `r-x` ⇒ cron không ghi nổi log ⇒ lại
  hỏng câm. Nay `2775` (setgid), và `host-prepare.sh` đã sửa + thêm `kiem_ghi_duoc`.
- **Con số ngày còn lại SAI.** Bản đầu in `còn -20703 ngày` cho **mọi** chứng chỉ: `date -d "<chuỗi
  GMT>"` không chạy dưới busybox của image Alpine. Một con số sai đọc y hệt một con số đúng, và nó
  là thứ duy nhất người ta liếc qua trong log. Nay dùng `openssl x509 -checkend` — phép đo nhị phân
  của chính OpenSSL, phân biệt được hai trạng thái mà không cần số học ngày tháng.

### Nghiệm thu cron

Chạy **đúng dòng cron**, không chạy một biến thể:
```bash
/opt/songnhue/gia-han-tls.sh >> /var/log/songnhue/gia-han-tls.log 2>&1; echo "MÃ THOÁT = $?"
tail -5 /var/log/songnhue/gia-han-tls.log
```
Đạt là: thoát 0 · log **ghi được** · dòng `── đã nạp lại nginx` có mặt.

---

## 5. ⚠ `rsync --delete` của CD xoá tệp bạn đặt tay

CD chạy `rsync -az --delete deploy/ → /opt/songnhue/`, chỉ loại trừ `.env`, `env/`, `keys/`,
`compose.local.yml`, `compose.infra.yml`.

Thử khô sau lượt đổi tên miền:
```
*deleting gia-han-tls.sh                    ← script mà cron TLS gọi
*deleting .env.truoc-doi-ten-mien-...       ← bản lùi .env
*deleting backup/khoi-phuc-qua-container.sh
```

⇒ **Bản lùi `.env` phải để ở `/var/lib/songnhue/`, không để ở `/opt/songnhue/`.** Và mọi script đặt
tay phải vào kho trước lượt CD kế tiếp. Không có gì báo sự vắng mặt của một tệp. Xem nợ T11.95.

Cách tự kiểm trước:
```bash
git archive origin/production deploy | tar -x -C /tmp/prod-deploy
rsync -az --delete --dry-run --itemize-changes \
  --exclude '.env' --exclude 'env/' --exclude 'keys/' \
  --exclude 'compose.local.yml' --exclude 'compose.infra.yml' \
  -e "ssh -i ~/.ssh/songnhue_deploy" /tmp/prod-deploy/deploy/ <user>@<host>:/opt/songnhue/ \
  | grep deleting
```

---

## 6. Nhật ký

### 08/09/2026 — `songnhue.com` → `thuyloisongnhue.vn`

**Hiện trạng lúc bắt đầu** — tên miền chính thức của Công ty **hỏng hoàn toàn** với người dùng thật:

```
thuyloisongnhue.vn        → 27.71.16.154  → 308 sang https → TLS THẤT BẠI (không có chứng chỉ)
www.thuyloisongnhue.vn    → 27.71.16.154  → y hệt
admin.thuyloisongnhue.vn  → 27.71.27.75   ⚠ máy STAGING
files.thuyloisongnhue.vn  → 27.71.27.75   ⚠ máy STAGING
```

⚠ Hai tên con **không phải "chưa ăn DNS"** — chúng phân giải bình thường, chỉ là trỏ nhầm máy. Phân
biệt hai chuyện này quan trọng khi làm việc với nhà cung cấp DNS: cần *sửa giá trị*, không phải *chờ*.

**Đã làm** chứng chỉ mới (`thuyloisongnhue.vn` + `www`, hạn 06/12/2026) · `.env` 3 dòng · tạo lại
nginx · biến kho + secret GitHub · commit chạm `deploy/` để dựng lại `public-web`.

**Đo sau**: `https://thuyloisongnhue.vn` **200**, 246 KB, **122 liên kết bài viết** · `www` 200 ·
`admin.songnhue.com` 200 · `files.songnhue.com` 403 (đúng — MinIO đòi chữ ký).

**Hệ quả cố ý**: `songnhue.com` không còn khối `server` nào phục vụ ⇒ TLS thất bại. Muốn giữ liên
kết cũ sống thì phải thêm khối chuyển hướng 301 vào `default.conf.template` — **chưa làm**.

### 08/09/2026 (chặng 2) — `admin.` + `files.` sau khi VNPT sửa DNS

DNS đo từ **ba nguồn** (NS uỷ quyền, resolver công cộng, 8.8.8.8): cả bốn bản ghi → `27.71.16.154`.

⚠ **`FILES_DOMAIN` có một ràng buộc mà `PUBLIC_DOMAIN`/`ADMIN_DOMAIN` không có.** Nó xuất hiện ở
**hai** service:

```yaml
app:    MINIO_ENDPOINT: https://${FILES_DOMAIN}     # app KÝ presigned URL bằng tên này
nginx:  networks: {default: {aliases: [${FILES_DOMAIN}]}}   # để app không phải đi vòng hairpin NAT
```

⇒ Phải tạo lại **cả hai cùng một lệnh**. Lệch nhau là app ký URL bằng một tên mà nginx không phục
vụ, hoặc app không phân giải nổi endpoint của chính nó:

```bash
docker compose --env-file .env -f compose.prod.yml up -d --no-deps --force-recreate app nginx
```

**Kiểm CSP trước — và hoá ra rủi ro thấp hơn tưởng.** CSP của cổng là
`img-src 'self' data: blob: https://tile.openstreetmap.org`, **không** có tên miền kho tệp. Ảnh cổng
đi qua `/api/v1/public/files/<id>` — **cùng origin**, 0 tham chiếu `files.` trong HTML trang chủ. Nên
đổi `FILES_DOMAIN` chỉ đổi tên miền của **presigned URL lúc tải tệp về**, không đụng ảnh hiển thị.

**Kết quả đo:**

| | |
|---|---|
| Chứng chỉ | `admin.thuyloisongnhue.vn` và `files.thuyloisongnhue.vn`, hạn **07/12/2026** |
| Tạo lại `app` + `nginx` | healthy sau **40 giây** |
| `server_name` | cả bốn đã sang miền mới |
| Bí danh mạng nginx | `files.thuyloisongnhue.vn` |
| `MINIO_ENDPOINT` của app | `https://files.thuyloisongnhue.vn` |
| HTTP | apex/www/admin **200** · files **403** (đúng — MinIO đòi chữ ký) |
| TLS | `ssl_verify_result=0` cả bốn tên |
| ⭐ **End-to-end** | ảnh cổng trả **15.976 byte PNG thật** ⇒ app đọc được MinIO **qua endpoint mới** |
| sitemap / robots | đã mang tên miền mới ⇒ image dựng lại đã lên production |

Ba tên miền cũ (`songnhue.com`, `admin.`, `files.`) nay **không còn khối `server` nào phục vụ** —
`curl` trả `000` (TLS thất bại). Đúng chủ ý.

### ⏳ Bẫy hẹn giờ: ba chứng chỉ cũ vẫn nằm trong danh sách gia hạn

`certbot renew` gia hạn **mọi** lineage trong `/etc/letsencrypt/renewal/`, kể cả ba cái không ai
dùng nữa. Hôm nay chúng vẫn gia hạn được vì `songnhue.com` **vẫn trỏ về `27.71.16.154`** nên thử
thách ACME đi qua khối `server_name _;`.

⛔ **Ngày nào bạn gỡ bản ghi DNS của `songnhue.com`, phải xoá ba lineage ấy TRƯỚC** — nếu không
`certbot renew` thất bại → script thoát khác 0 → **cron báo đỏ vì thứ không ai còn dùng**, và một
cảnh báo sai là một cảnh báo người ta thôi đọc.

```bash
CB="docker run --rm -v /etc/letsencrypt:/etc/letsencrypt certbot/certbot:v5.8.0"
$CB delete --cert-name songnhue.com --non-interactive
$CB delete --cert-name admin.songnhue.com --non-interactive
$CB delete --cert-name files.songnhue.com --non-interactive
```

Giữ chúng lại lúc này là cố ý: chúng là điều kiện để thêm một khối chuyển hướng 301 từ tên miền cũ,
nếu sau này muốn.

⬜ **Còn treo**: `SMTP_FROM` vẫn `no-reply@songnhue.com`. ⬜ `songnhue.com` chưa có chuyển hướng 301.

> ⚠ `SMTP_FROM`: đổi **trước khi** xác nhận máy chủ SMTP cho phép gửi thay mặt miền mới là thư vào
> hộp rác hoặc bị từ chối thẳng.
