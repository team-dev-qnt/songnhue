# Di trú dữ liệu giữa hai môi trường — tài liệu kỹ thuật

> **Nhân bản CSDL + kho tệp từ môi trường này sang môi trường khác.**
> Viết sau lượt chạy thật `staging → production` ngày 07–08/09/2026. Mọi con số ở đây là số **đo
> được** trên máy thật, không phải ước lượng.
>
> Tài liệu này khác [`khoi-phuc-du-lieu.md`](khoi-phuc-du-lieu.md): ở đó là *khôi phục về chính
> mình sau sự cố*, ở đây là *mang dữ liệu sang một môi trường khác*. Hai việc dùng chung công cụ
> nhưng khác nhau ở một điểm quyết định — xem §2.

---

## 1. Dùng tài liệu này khi nào

| Tình huống | Áp dụng |
|---|---|
| Đưa nội dung staging lên production lúc go-live | ✅ toàn bộ |
| Làm mới staging từ dữ liệu production để thử nghiệm | ✅ nhưng đảo chiều — đọc kỹ §2.3 và §11 |
| Khôi phục production sau sự cố, từ bản dump của **chính nó** | ❌ dùng [`khoi-phuc-du-lieu.md`](khoi-phuc-du-lieu.md) |
| Diễn tập khôi phục | ❌ dùng [`dien-tap-khoi-phuc.md`](dien-tap-khoi-phuc.md) |

---

## 2. Mô hình rủi ro — một bản dump chứa những gì

Đây là phần quan trọng nhất của tài liệu. Ba lớp nội dung, và người ta chỉ nghĩ tới lớp đầu:

### 2.1 Dữ liệu

Hiển nhiên, và là thứ duy nhất ai cũng kiểm.

### 2.2 Lược đồ **và sổ migration**

`flyway_schema_history` đi cùng bản dump. Nếu môi trường nguồn áp những migration mà môi trường đích
**không giải được**, thì sau khôi phục Flyway ở đích báo *"Detected applied migration not resolved
locally"* và **ứng dụng không khởi động**.

Đo 08/09: staging 63 hàng, production 60, **cùng đỉnh** `202609071069`. Chênh đúng ba tệp
`seed_portal_*` nằm ở `classpath:db/seed/portal` — chỉ được giải khi `SEED_LOCATION` trỏ vào đó, mà
production để rỗng (`classpath:db/seed/none`, một thư mục cố ý không có migration nào).

⛔ **Cấm chữa bằng cách đặt `SEED_LOCATION` ở production.** Migration seed mở đầu bằng
`DELETE FROM articles`. Nó sẽ không chạy lần này (sổ đã có hàng), nhưng để lại một khẩu súng lên đạn
chĩa vào **đúng đường khôi phục thảm hoạ**: lượt dựng lại từ CSDL rỗng nào sau này cũng sẽ xoá sạch
bài của Công ty rồi thay bằng nội dung dàn dựng.

✅ Cách đúng: xoá **phần ghi sổ**, giữ nguyên **dữ liệu** mà chúng đã tạo ra.

### 2.3 ⛔⛔ Quyền (ACL) — lớp không ai nghĩ tới

`pg_dump` mang theo `GRANT`/`REVOKE` của từng bảng. Khôi phục **thay** ACL của đích bằng ACL của
nguồn.

Đo 08/09, `songnhue_app` trên staging so với production:

| Bảng | production (đúng) | staging (yếu hơn) |
|---|---|---|
| `audit_logs` + 15 phân mảnh | `ar` | `arwd` |
| `audit_chain_head` | *(không có quyền nào)* | `arwd` |
| `audit_archive_anchors` | `r` | `arwd` |
| `hydro_raw_logs` + 13 phân mảnh | `ar` | `arwd` |
| `security_events` | `ar` | `arwd` |
| `flyway_schema_history` | `r` | `arwd` |

Khôi phục nguyên trạng ⇒ **âm thầm hạ cấp production**: vai trò runtime sửa và xoá được nhật ký
kiểm toán (phá luật 18 — hash chain đang ký tên vào lịch sử), sửa được `hydro_raw_logs` (luật 8 —
bản sao **duy nhất** của nguồn không có API lịch sử), và ghi được cả sổ migration.

**Nguyên nhân staging mất phần siết**: lượt khôi phục staging 26/8 chạy bản `restore.sh` còn
`--no-privileges` — đúng thứ §10.58 ghi là *"`ALTER DEFAULT PRIVILEGES` cứu"*. Nó cứu app khỏi chết
và **cùng lúc xoá mọi câu `REVOKE`**. Staging đã chạy như thế 13 ngày, không có triệu chứng nào.

> ⇒ **Luật rút ra: nhân bản môi trường theo chiều *kém an toàn → an toàn hơn* là nhập khẩu cả phần
> yếu.** Và cách vá **không** phải chép ảnh chụp ACL của đích — ảnh chụp cũng có thể đã sai. Phải
> **tái khẳng định nguồn sự thật**: chạy lại nguyên văn phần `REVOKE` của migration.

---

## 3. Điều tra bắt buộc trước khi làm

Chạy đủ bảy phép này ở **cả hai** môi trường rồi mới quyết định. Mỗi phép trả một con số so được.

```bash
# Khuôn chung — mọi lệnh psql đều đi qua container, xem §4.1 vì sao
psql() { docker exec -i songnhue-postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atf -' ; }
```

**① Số hàng mọi bảng** (đếm CHÍNH XÁC, không dùng `reltuples`):

```sql
SELECT relname || '=' || (xpath('/row/c/text()',
         query_to_xml(format('select count(*) c from %I.%I', schemaname, relname), false, true, '')))[1]::text::bigint
FROM pg_stat_user_tables WHERE schemaname='public' ORDER BY relname;
```

**② Sổ migration** — số hàng *và* danh sách script, không chỉ đỉnh version:

```sql
SELECT version||' '||script FROM flyway_schema_history WHERE success ORDER BY version;
```
Chênh lệch phải giải thích được từng dòng. Cùng `max(version)` **không** có nghĩa là giống nhau.

**③ ACL từng bảng, theo NGỮ NGHĨA chứ không theo văn bản** — thứ tự phần tử trong `relacl` khác nhau
là chuyện bình thường và sẽ cho ra 28 dòng "lệch" giả:

```sql
SELECT c.relname||'|'||r.rolname||'|'||
  (CASE WHEN has_table_privilege(r.rolname,c.oid,'SELECT') THEN 'r' ELSE '-' END)||
  (CASE WHEN has_table_privilege(r.rolname,c.oid,'INSERT') THEN 'a' ELSE '-' END)||
  (CASE WHEN has_table_privilege(r.rolname,c.oid,'UPDATE') THEN 'w' ELSE '-' END)||
  (CASE WHEN has_table_privilege(r.rolname,c.oid,'DELETE') THEN 'd' ELSE '-' END)
FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
CROSS JOIN (SELECT rolname FROM pg_roles WHERE rolname LIKE 'songnhue%') r
WHERE n.nspname='public' AND c.relkind IN ('r','p') ORDER BY 1;
```

**④ Thuộc tính CSDL và những thứ `pg_dump` KHÔNG mang theo**:

```sql
SELECT datname, pg_encoding_to_char(encoding), datlocprovider, datcollate, datctype, daticulocale,
       array_to_string(datacl,' , ')                    -- ⚠ ACL cấp CSDL: dump KHÔNG có
FROM pg_database WHERE datname='songnhue';
SELECT nspname, array_to_string(nspacl,' , ') FROM pg_namespace WHERE nspname='public';
SELECT string_agg(extname||'@'||extversion,', ') FROM pg_extension;
SELECT string_agg(c.relname,', ') FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
 WHERE n.nspname='public' AND c.relkind='p';           -- bảng PHÂN MẢNH, xem §4.2
```

**⑤ Cột mã hoá** — mọi thứ ở đây bị khoá vào khoá AES của **môi trường nguồn**:

```sql
SELECT table_name||'.'||column_name FROM information_schema.columns
 WHERE table_schema='public'
   AND (column_name LIKE '%encrypted%' OR column_name LIKE '%credential%'
        OR column_name LIKE '%secret%' OR column_name='key_id');
```
Đo 08/09: đúng ba cột — `api_sources.credential`, `user_totp.secret_encrypted`, `user_totp.key_id`.
Phạm vi nhỏ, nhưng `user_totp` là thứ khoá bạn ra khỏi hệ thống (§4.4).

**⑥ Dữ liệu mang tên miền của môi trường nguồn**:

```sql
SELECT 'articles',        count(*) FROM articles         WHERE content       ILIKE '%staging%'
UNION ALL SELECT 'versions', count(*) FROM article_versions WHERE content     ILIKE '%staging%'
UNION ALL SELECT 'banners',  count(*) FROM banners        WHERE link_url      ILIKE '%staging%'
UNION ALL SELECT 'settings', count(*) FROM settings       WHERE setting_value ILIKE '%staging%';
```

**⑦ Kho tệp** — đếm đối tượng thật, không tin `mc ls`:

```bash
docker run --rm -v songnhue_minio-data:/d:ro alpine:3.20 sh -c \
  'echo "đối tượng: $(find /d/songnhue-media -name xl.meta -type f | wc -l)  byte: $(du -sb /d/songnhue-media | cut -f1)"'
```
Con số này phải khớp `SELECT count(*) FROM attachments`. Đo 08/09: staging **122 đối tượng /
93.868.815 B** ↔ **122 hàng** `attachments`. Khớp khít là dấu hiệu tốt; lệch là dấu hiệu có tệp mồ
côi hoặc hàng không có byte.

---

## 4. Bốn quyết định thiết kế, và vì sao

### 4.1 Chạy công cụ TRONG container, không trên host

Đo 08/09 trên **cả hai** VPS:

```
psql / pg_dump / pg_restore : KHÔNG CÓ trên host
DB_HOST=postgres            : chỉ phân giải BÊN TRONG mạng docker
container postgres          : "5432/tcp": null — không publish cổng nào
$DEPLOY_DIR/env/prod.env    : không tồn tại trên host (rsync loại trừ env/)
```

⇒ `deploy/backup/restore.sh` — đường khôi phục thủ công **duy nhất** của hệ, chính là thứ T7.13-a đã
vá sau §10.58 — **không chạy được ở đúng nơi cần nó**. Nó chỉ chạy trên máy dev, tức nơi không bao
giờ xảy ra thảm hoạ.

Dùng [`deploy/backup/khoi-phuc-qua-container.sh`](../../deploy/backup/khoi-phuc-qua-container.sh):
giữ nguyên cả 8 bảo đảm của `restore.sh` nhưng đi qua `docker exec`, đúng cách `pre-deploy-dump.sh`
đã làm và đã chạy thật trong mọi lượt CD. Phiên bản client khớp tuyệt đối với máy chủ vì đó **chính
là** máy chủ.

> Thư mục `/var/lib/songnhue/backup` được bind-mount vào container ở **cùng đường dẫn**, nên một tệp
> đặt ở đó nhìn thấy được từ cả hai phía. Script tự đo điều này thay vì tin vào compose.

### 4.2 Khôi phục **toàn bộ** rồi TRỪ đi — không chép theo hàng

Cùng một bộ seed, nhưng thứ tự chèn khác nhau nên khoá thay thế (serial) rơi khác chỗ:

| ID | staging | production |
|---|---|---|
| `categories 11` | Luật | Hướng dẫn |
| `categories 12` | Nghị định | Quyết định |
| `categories 15` | Hướng dẫn | Luật |
| `menu_items 24` | Ban lãnh đạo | Lãnh đạo Công ty |
| `menu_items 41–44` | viết HOA | viết thường |

`article_categories` trỏ `category_id`. Chép hàng sang là xếp **28 bài vào sai danh mục, im lặng,
không lỗi nào**. Đây là loại sai không có triệu chứng và chỉ lộ ra khi người dùng phàn nàn.

⇒ Khôi phục toàn bộ (mọi ID nhất quán với nhau vì cùng một nguồn), rồi gỡ ra những thứ không được
sang: phiên, hàng đợi, sổ sao lưu, danh tính.

### 4.3 Byte trước, hàng sau

Chép MinIO **trước** khi khôi phục CSDL.

- Đúng thứ tự: trong lúc chép có ai tải tệp mới lên nguồn thì đích được một object thừa không ai
  trỏ tới — **vô hại**.
- Ngược lại: có một khoảng thời gian CSDL đích khẳng định tệp tồn tại còn `GET` trả 404 — **hỏng
  câm**, và người dùng nhìn thấy trước bạn.

### 4.4 Giữ danh tính của môi trường ĐÍCH

⛔⛔ Bước này bị bỏ là **khoá cứng ra khỏi hệ thống**, không phải bất tiện.

Bản dump nguồn mang theo tài khoản `superadmin` của nguồn: ACTIVE, 2FA bắt buộc, và một bí mật TOTP
mã hoá AES-256-GCM bằng **khoá của nguồn**. Khoá AES nằm ngoài CSDL, mỗi môi trường một khoá riêng;
`key_id='v1'` chỉ là cái nhãn, không phải khoá. Đích không giải mã nổi ⇒ không sinh được mã 2FA hợp lệ.

Và [`AdminBootstrapRunner`](../../backend/core/src/main/java/com/songnhue/core/application/auth/AdminBootstrapRunner.java)
**chỉ tác động khi `status='PENDING_ACTIVATION'`** — cố ý như vậy, để ai sửa được `.env` cũng không
chiếm được tài khoản quản trị tối cao. Hệ quả: `BOOTSTRAP_ADMIN_PASSWORD` **không** mở lại được một
tài khoản đã ACTIVE.

✅ Cách làm: **chụp các cột danh tính của đích TRƯỚC khi di trú**, đè lại sau khôi phục.

```sql
-- Sinh trên môi trường ĐÍCH, ghi ra tệp mode 600, KHÔNG in ra màn hình
SELECT format('UPDATE users SET password_hash=%L, password_changed_at=%L, must_change_password=%L,
               status=%L, two_factor_required=%L, failed_login_count=0, locked_until=NULL,
               last_login_at=%L, last_login_ip=%L, updated_at=now() WHERE username=%L;',
  password_hash, password_changed_at, must_change_password, status, two_factor_required,
  last_login_at, last_login_ip, username) FROM users WHERE username='superadmin';

SELECT format('INSERT INTO user_totp (user_id,secret_encrypted,key_id,enrolled_at,confirmed_at,
               last_used_step,created_at,created_by,updated_at,updated_by,version)
               SELECT u.id,%L,%L,%L,%L,%L,%L,%L,%L,%L,%L FROM users u WHERE u.username=''superadmin'';',
  secret_encrypted,key_id,enrolled_at,confirmed_at,last_used_step,created_at,created_by,
  updated_at,updated_by,version)
FROM user_totp WHERE user_id=(SELECT id FROM users WHERE username='superadmin');

SELECT format('INSERT INTO user_recovery_codes (user_id,code_hash,used_at,created_at)
               SELECT u.id,%L,%L,%L FROM users u WHERE u.username=''superadmin'';',
  code_hash,used_at,created_at)
FROM user_recovery_codes WHERE user_id=(SELECT id FROM users WHERE username='superadmin') ORDER BY id;
```

⚠ Bám `username`, **không** bám `id=1`. Hai môi trường có thể đánh id khác nhau, và một câu
`WHERE id=1` sai thì nó đè lên **nhầm người**, im lặng.
⚠ Cột `id` của `user_totp` và `user_recovery_codes` là `GENERATED ALWAYS AS IDENTITY` — **đừng liệt
kê nó** trong danh sách cột của `INSERT`.

---

## 5. Quy trình — 9 bước

> Ước lượng: chuẩn bị 30–60 phút, gián đoạn thật **dưới 1 phút**. Đo 08/09: bản chụp PRE_RESTORE
> lúc `00:20:39`, ứng dụng đã chạy lại lúc `00:20:45` — cửa sổ ghi đè **6 giây**.

| # | Việc | Phép đo chứng minh bước đó xong |
|---|---|---|
| 1 | Chụp CSDL **đích** (điểm quay lui) | tên tệp + số byte + sha256 khớp, và kéo một bản ra **máy thứ hai** |
| 2 | Chụp **danh tính** của đích (§4.4) | đếm câu lệnh: 1 `UPDATE users` + 1 `INSERT user_totp` + N `INSERT user_recovery_codes`; **0** dòng bám `id=` |
| 3 | Chụp CSDL **nguồn** | `pg_dump -Fc --compress=6 --no-owner`, ⛔ **KHÔNG** `--no-privileges` (§10.58) |
| 4 | **Diễn tập trên CSDL nháp** — xem §6 | nháp phải khớp đích: `datlocprovider`, `daticulocale`, `datacl`, `nspacl`, số bảng, số phân mảnh |
| 5 | Chép kho tệp (§4.3) | số đối tượng trước → sau, và tổng byte |
| 6 | Dừng ứng dụng | `docker stop songnhue-app` |
| 7 | Khôi phục + khối vá, **một giao dịch** | mã thoát 0, số dòng `ERROR` = 0, mọi `RAISE NOTICE` in ra |
| 8 | Bật ứng dụng + khởi động lại `public-web` | `public-web` phải restart để bỏ đệm ISR của nội dung cũ |
| 9 | Nghiệm thu (§8) | 8 phép, mỗi phép phân biệt được hai trạng thái |

### Lệnh thật

```bash
# ── 1. chụp đích  (trên máy ĐÍCH)
cd /opt/songnhue && ./backup/pre-deploy-dump.sh < /dev/null
#    ⚠ `< /dev/null` bắt buộc — xem §7.6

# ── 3. chụp nguồn  (trên máy NGUỒN)
docker exec -i -e PGPASSWORD="$DB_READONLY_PASSWORD" songnhue-postgres pg_dump \
  --username=songnhue_readonly --dbname=songnhue \
  --format=custom --compress=6 --no-password --no-owner \
  --file=/var/lib/songnhue/backup/di-tru-<ngày>.dump < /dev/null

# ── 5. chép kho tệp  (chạy từ máy ĐÍCH, kéo về — chiều tin cậy đúng)
docker exec -i \
  -e MC_HOST_src="https://$KEY:$SECRET@files-<nguồn>.example" \
  -e MC_HOST_dst="http://$ROOT:$ROOTPW@localhost:9000" \
  songnhue-minio mc mirror --overwrite src/songnhue-media dst/songnhue-media < /dev/null

# ── 7. khôi phục
XAC_NHAN=songnhue ENV_FILE=/opt/songnhue/.env \
  ./backup/khoi-phuc-qua-container.sh /var/lib/songnhue/backup/di-tru-<ngày>.dump \
  --sau /var/lib/songnhue/backup/.khoi-va.sql
```

> Khối vá ghép từ [`deploy/backup/di-tru/sau-khoi-phuc-production.sql`](../../deploy/backup/di-tru/sau-khoi-phuc-production.sql)
> cộng tệp danh tính, bằng `sed` với mẫu **neo đầu dòng** — xem §7.7.

### Khối vá làm gì

| | Việc | Phép chốt |
|---|---|---|
| ① | xoá **ghi sổ** của migration seed, giữ dữ liệu | phải xoá đúng 3 hàng |
| ② | đè lại danh tính của đích, xoá TOTP/mã khôi phục/phiên của nguồn | superadmin ACTIVE + 1 TOTP đã xác nhận + 10 mã + giữ vai trò `SUPER_ADMIN` |
| ③ | khoá tài khoản của đội phát triển mang từ nguồn sang | đếm số hàng đã khoá |
| ④ | xoá job `PENDING`/`FAILED` và sổ sao lưu trỏ vào máy nguồn | đếm |
| ⑤ | sửa liên kết còn trỏ về tên miền nguồn | phải sửa đúng 1, và sau đó **0** chỗ còn sót |
| ⑥ | **tái khẳng định quyền append-only** (§2.3) | `has_table_privilege` phải `f` ở UPDATE/DELETE, `t` ở INSERT |
| ⑦ | tạo phân mảnh `audit_logs` cho các tháng tới | `core_ensure_audit_partitions(6)` |

Toàn khối nằm trong **một** `BEGIN … COMMIT`, mỗi mục có `RAISE EXCEPTION` khi số hàng đụng tới
không đúng kỳ vọng. Một câu `DELETE` khớp 0 hàng và một câu khớp 3 hàng trông giống hệt nhau trong
log; chỉ phép đếm phân biệt được (luật 9).

---

## 6. Diễn tập trên CSDL nháp — bước đắt giá nhất

**Đây là bước tìm ra cả ba khuyết tật CHẶN.** Không lượt rà tài liệu nào thấy chúng.

Dựng một CSDL trên **cùng cluster đích**, làm cho nó là bản sao đúng của đích, rồi chạy nguyên quy
trình lên đó. Rủi ro bằng không, và nó phơi bày đúng những lỗi chỉ xuất hiện khi *đích đã có dữ liệu*.

```sql
CREATE DATABASE songnhue_thu TEMPLATE template0 ENCODING 'UTF8';
-- extension: PHẢI tạo bằng superuser, songnhue_owner không tạo nổi postgis
\c songnhue_thu
CREATE EXTENSION postgis; CREATE EXTENSION pg_trgm; CREATE EXTENSION unaccent;
-- ACL cấp CSDL và cấp schema: pg_dump KHÔNG mang theo, phải chép tay
REVOKE ALL ON DATABASE songnhue_thu FROM PUBLIC;
GRANT CREATE,CONNECT ON DATABASE songnhue_thu TO songnhue_owner;
GRANT CONNECT ON DATABASE songnhue_thu TO songnhue_app, songnhue_archiver, songnhue_readonly;
GRANT ALL   ON SCHEMA public TO songnhue_owner;
GRANT USAGE ON SCHEMA public TO songnhue_app, songnhue_archiver, songnhue_readonly;
```

Rồi nạp **bản chụp của đích** vào (đích rỗng ⇒ không cần `--clean`), và kiểm nháp khớp đích:

```
datlocprovider=i  daticulocale=vi-VN   ← template0 của cluster đã mang sẵn, bẫy §10.56 không áp ở đây
datacl, nspacl                          ← phải giống TỪNG KÝ TỰ
số bảng, số phân mảnh audit_logs        ← 107 và 15 (đo 08/09)
```

Sau đó chạy đúng quy trình §5 lên nháp. Xong thì `DROP DATABASE songnhue_thu` — nó chiếm 33 MB và là
một bản sao đầy đủ dữ liệu thật.

⚠ **Đừng dựng nháp trên CSDL rỗng hoàn toàn.** Cảnh đó không tái lập được hai trong ba khuyết tật —
xem §7.1 và §7.2.

---

## 7. Sổ sự cố — 12 vấn đề đã gặp

Mỗi mục: **triệu chứng → nguyên nhân → vá → lần sau phát hiện bằng gì.**

### 7.1 ⛔ CHẶN — `pg_restore --clean` vấp bảng phân mảnh

**Triệu chứng**
```
ERROR: cannot drop index public.hydro_readings_p202708_station_id_measured_at_idx
       because index public.ix_hydro_readings_station_time requires it
Command was: DROP INDEX IF EXISTS public.hydro_readings_p202708_station_id_measured_at_idx;
```
Mã thoát 1. Nhờ `--single-transaction` nên dữ liệu cũ **còn nguyên** (đo: 107 bảng, `users=1`,
`articles=4` sau lượt hỏng).

**Nguyên nhân** `--clean` phát `DROP INDEX`/`DROP CONSTRAINT` cho **từng phân mảnh**, mà chỉ mục và
ràng buộc của phân mảnh không xoá lẻ được khi bảng cha còn — Postgres bắt xoá ở cha. Kho có **ba**
bảng phân mảnh: `audit_logs`, `hydro_raw_logs`, `hydro_readings`. Bản dump sinh ra 15 câu
`DROP CONSTRAINT` chỉ riêng cho phân mảnh `audit_logs`.

⚠ **Trên đích rỗng những câu ấy là no-op.** §10.58 và T11.3-b đều diễn tập trên cluster vừa dựng
lại ⇒ về nguyên tắc không thể thấy. *Đường hay thử thì chạy, đường dùng thật thì hỏng.*

**Vá** Sinh SQL ra **tệp**, ghép một khối bỏ-bảng-phân-mảnh lên **trước**, nạp cả hai trong **một**
giao dịch:

```sql
DO $$
DECLARE r record; n int := 0;
BEGIN
    FOR r IN SELECT c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid = c.relnamespace
              WHERE ns.nspname = 'public' AND c.relkind = 'p'
    LOOP EXECUTE format('DROP TABLE IF EXISTS public.%I CASCADE', r.relname); n := n + 1; END LOOP;
    RAISE NOTICE 'da bo % bang phan manh truoc khi nap', n;
END $$;
```

⛔ **Tách hai giao dịch là một lỗ mất dữ liệu.** Nếu khối `DO` chạy ở giao dịch riêng rồi
`pg_restore` hỏng, đích còn lại một CSDL **không có bảng phân mảnh nào**. Hôm nay lượt hỏng chỉ
"dừng, không mất gì" — đừng đổi điều đó lấy sự tiện.

**Lần sau phát hiện bằng gì** Diễn tập trên CSDL nháp **có dữ liệu** (§6), không phải cluster rỗng.

### 7.2 ⛔ CHẶN — bộ lọc mục lục để lọt mục `EXTENSION`, và §10.58 đã vá nhầm chỗ

**Triệu chứng** `must be owner of extension postgis`, mã thoát 1.

**Nguyên nhân** Bộ lọc cũ là `pg_restore --list | grep -v "COMMENT - EXTENSION" | awk '$NF != "postgres"'`.
Dòng mục lục của extension là:
```
2; 3079 16389 EXTENSION - postgis
```
`pg_dump` **không ghi chủ sở hữu** cho extension, nên `$NF` là *tên extension*, không phải `postgres`
⇒ mục đi lọt ⇒ `pg_restore` phát `DROP EXTENSION IF EXISTS postgis;` mà `songnhue_owner` không có
quyền chạy.

⚠ Đây **đúng nguyên văn** thông điệp mà §10.58 ghi lại và gán cho mục `COMMENT - EXTENSION`. Hai
trạng thái khác nhau in ra cùng một câu (luật 9) nên bản vá 26/8 nhắm trượt, và không ai biết vì nó
chỉ được thử trên đích rỗng — nơi extension chưa tồn tại và `DROP … IF EXISTS` là no-op.

**Vá — ba vế, không phải hai:**
```bash
grep -v "COMMENT - EXTENSION" "$TOC.day-du" \
  | grep -vE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION ' \
  | awk '$NF != "postgres"' > "$TOC"
```
Kèm **hai phép chốt**, vì bộ lọc mới đặt ra một tiền đề im lặng (luật 28):
```bash
SO_EXT="$(grep -cE '^[0-9]+; +[0-9]+ +[0-9]+ EXTENSION ' "$TOC" || true)"   # `|| true`: grep -c thoát 1 khi đếm 0
[ "$SO_EXT" -eq 0 ] || { echo "✗ mục lục còn $SO_EXT mục EXTENSION"; exit 1; }
# và: đích PHẢI có sẵn đủ extension, vì bản dump nay không tạo lại được chúng
SELECT count(*) FROM pg_extension WHERE extname IN ('postgis','unaccent','pg_trgm');   -- phải = 3
```

**Lần sau phát hiện bằng gì** Đếm mục `EXTENSION` còn lại trong **chính tệp mục lục** truyền cho
`pg_restore` — đừng đếm trên SQL sinh ra: một bài viết chứa chuỗi `DROP EXTENSION` sẽ làm phép đếm
sai, và nó sẽ **dừng lượt khôi phục thảm hoạ vì nội dung dữ liệu**.

### 7.3 ⛔ CHẶN — dữ liệu nguồn mang quyền yếu hơn

Xem §2.3. **Vá**: mục ⑥ của khối vá, chép nguyên văn phần *"2. Siết các bảng append-only"* của
`V202608131006__core_db_role_grants.sql` cộng phần tương ứng của `V202609041059__hyd_time_series.sql`.

**Bằng chứng hai trạng thái** (luật 1) — đo trên CSDL nháp:

| Phép hỏi | trước vá | sau vá |
|---|---|---|
| `songnhue_app` UPDATE `audit_logs` | `t` | `f` |
| DELETE `audit_logs` | `t` | `f` |
| DELETE `hydro_raw_logs` | `t` | `f` |
| SELECT `audit_chain_head` | `t` | `f` |
| INSERT `flyway_schema_history` | `t` | `f` |
| **INSERT `audit_logs`** (phải giữ được) | `t` | **`t`** |

Vế cuối quan trọng ngang năm vế trên: nó chứng minh đã siết **đúng chỗ**, không siết quá tay.

**Lần sau phát hiện bằng gì** Phép ③ của §3, so theo ngữ nghĩa. ⚠ So `relacl` bằng mắt cho ra 28
dòng lệch **giả** chỉ vì thứ tự phần tử trong mảng khác nhau.

### 7.4 ⛔ CHẶN — sổ migration mồ côi làm app không khởi động

Xem §2.2. **Vá**: `DELETE FROM flyway_schema_history WHERE script LIKE '%seed_portal%';` với chốt
`ROW_COUNT = 3`.

**Lần sau phát hiện bằng gì** Phép ② của §3 — so **danh sách script**, không so `max(version)`. Hai
môi trường có cùng đỉnh version mà lệch 3 hàng là chuyện có thật.

### 7.5 Khoá cứng ra khỏi hệ thống

Xem §4.4. Đây là khuyết tật *thiết kế của quy trình*, không phải lỗi công cụ: mọi thứ chạy đúng, chỉ
là sau đó không ai đăng nhập được nữa.

### 7.6 `docker exec -i` nuốt mất nửa cuối script

**Triệu chứng** Script chạy qua `ssh 'bash -s' <<'EOF'` dừng giữa chừng, **thoát 0**, không thông báo gì.

**Nguyên nhân** `docker exec -i` **đọc stdin**, mà stdin của script chính là khối lệnh đang được nạp.
Lệnh đầu tiên nuốt trọn phần còn lại. Đây là §10.60 — lượt CD Staging *success trọn vẹn mà không
container nào được thay*.

**Vá** `< /dev/null` ở **mọi** lời gọi `docker exec -i` không cố ý đẩy dữ liệu vào. Trong
`khoi-phuc-qua-container.sh` việc này được tách thành hai hàm — `trong()` chặn stdin, `trong_stdin()`
không — để người sau không phải nhớ.

> ⚠ Mắc **hai lần** trong cùng một phiên khi soạn chính tài liệu này.

### 7.7 `sed` chèn tệp danh tính **hai lần**

**Triệu chứng** `duplicate key value violates unique constraint "uq_user_totp_user_id"`.

**Nguyên nhân** Mẫu chèn không neo: `sed -e '/@@CHEN_DANH_TINH@@/r tệp'`. Chính **dòng chú thích mô
tả cách dùng** cũng chứa chuỗi mốc ⇒ `sed` chèn sau cả hai dòng.

**Vá** Neo hai đầu: `sed -e '/^-- @@CHEN_DANH_TINH@@$/r tệp'`, và đếm sau khi ghép:
```bash
[ "$(grep -c '^INSERT INTO user_totp' "$GHEP")" -eq 1 ] || exit 1
```
**Bắt được ở lượt diễn tập trên CSDL nháp, không phải trên production.**

### 7.8 `\i` / `\ir` không dùng được, và tệp `600` container không đọc nổi

Khối vá được nạp qua **stdin** của `docker exec`, nên `\ir` không có thư mục gốc để bám. Và tệp danh
tính để mode `600` của người triển khai, còn postgres trong container chạy bằng uid khác — nó không
đọc được.

⇒ **Ghép ở host** rồi đẩy qua stdin là đường duy nhất đúng cả hai phía.

### 7.9 `attachments` không có cột thư mục

`DELETE FROM media_folders WHERE name IN ('…')` **không** bị khoá ngoại nào chặn, nhưng liên kết đi
qua `owner_type`/`owner_id` ⇒ ảnh trở thành mồ côi, im lặng.

⇒ Xoá thư mục media **ở màn hình quản trị** để đi qua soft-delete + audit đúng luật 9. Cố ý không
làm bằng SQL.

### 7.10 Tệp trung gian chứa bí mật, quyền `644`

**Triệu chứng** Sau lượt di trú, `/var/lib/songnhue/backup` còn bốn tệp SQL 4 MB, quyền `644`/`664`,
mỗi tệp chứa 4 lần `password_hash` và `secret_encrypted` **dạng thuần**.

**Vá** `umask 077` ở đầu script + `trap 'rm -f "$GHEP"' EXIT`.

⬜ **Nợ còn lại (T11.96)**: các tệp `*.dump` vẫn là `644` — mỗi tệp là **toàn bộ CSDL**. Hành vi sẵn
có của `pre-deploy-dump.sh`, mâu thuẫn với chính chuẩn của dự án (`.env` bắt buộc `600`).

### 7.11 zsh không tách từ

```bash
for h in "prod 27.71.16.154"; do set -- $h; ...   # dưới zsh: $1 = cả chuỗi
```
Sinh ra tệp tên `dem-prod 27.71.16.154.txt` và mọi phép đo sau đó vô nghĩa. Luật 20: script phải
kiểm bằng `bash -c`; viết tường minh từng biến khi chạy tay trên macOS.

### 7.12 Phép khẳng định checksum tự nói dối

```bash
DO=$(shasum ... | awk '{print $1}')     # tệp không tồn tại ⇒ chuỗi rỗng
GHI=$(awk '{print $1}' "$F.sha256")     # cũng rỗng
[ "$DO" = "$GHI" ] && echo "✓ KHỚP"     # RỖNG = RỖNG ⇒ in ra "KHỚP"
```
In `✓ KHỚP` trong khi **cả hai tệp đều không tồn tại**. Luật 9 ở dạng thuần khiết nhất.

**Vá** Khẳng định trên một thuộc tính **đo được**, không chỉ trên phép so:
```bash
[ ${#DO} -eq 64 ] && [ "$DO" = "$GHI" ]
```

---

## 8. Nghiệm thu — 8 phép, mỗi phép phân biệt hai trạng thái

```bash
CT=songnhue-postgres
q() { docker exec -i "$CT" psql -U postgres -d songnhue -At -c "$1" < /dev/null; }
```

| # | Phép | Đạt là |
|---|---|---|
| 1 | `q "SELECT count(*) FROM pg_stat_user_tables"` | khớp số bảng của nguồn |
| 2 | Số hàng các bảng nghiệp vụ | khớp nguồn, trừ những bảng khối vá cố ý xoá |
| 3 | `q "SELECT count(*) FROM flyway_schema_history WHERE success"` | khớp số migration mà **image đích** giải được |
| 4 | Checksum migration đích vs nguồn | **0 dòng khác nhau** ⇒ Flyway `validate` sẽ qua |
| 5 | **Đọc bằng vai trò ỨNG DỤNG** — `songnhue_app`, không phải owner | ra số, không ra `permission denied` |
| 6 | `q "SELECT * FROM core_verify_audit_chain()"` | **rỗng** = chuỗi băm nguyên vẹn |
| 7 | Quyền append-only (bảng ở §7.3) | 5 phép `f`, `INSERT audit_logs` `t` |
| 8 | Thứ tự tiếng Việt | `Anh < Dung < Đăng < Em` |

⚠ **Phép 5 là phép §10.58 đã thiếu.** Chủ sở hữu *luôn* đọc được, nên hỏi bằng chủ sở hữu **không
phân biệt được hai trạng thái**. Phải hỏi bằng đúng vai trò mà ứng dụng dùng lúc chạy.

Sau đó, ngoài CSDL:

```bash
curl -sS -o /dev/null -w '%{http_code}\n' https://<miền>/            # 200
curl -sS https://<miền>/ | grep -c '/bai-viet/'                      # > 0, nội dung thật
docker run --rm -v songnhue_minio-data:/d:ro alpine:3.20 sh -c \
  'find /d/songnhue-media -name xl.meta -type f | wc -l'             # khớp count(*) FROM attachments
```

---

## 9. Quay lui

Trong lượt khôi phục: `--single-transaction` + `ON_ERROR_STOP=1` ⇒ hỏng là cuộn ngược sạch, dữ liệu
cũ còn nguyên. **Đã đo đúng tình huống ấy.**

⛔ Khi gặp lỗi, **đừng** "chữa nhanh" bằng cách bỏ `--exit-on-error` hay `--single-transaction`. Đó
là lúc duy nhất thật sự mất dữ liệu.

Sau lượt khôi phục đã COMMIT — quay về bản chụp bước 1:

```bash
XAC_NHAN=songnhue ENV_FILE=/opt/songnhue/.env \
  ./backup/khoi-phuc-qua-container.sh /var/lib/songnhue/backup/predeploy-<ngày>.dump
```

Kho tệp thì **không** quay lui được bằng lệnh — `mc mirror` không xoá thứ nó đã thêm. Các object
thừa vô hại (không hàng CSDL nào trỏ tới) nhưng cũng không có job dọn.

---

## 10. Ảnh hưởng tới CI/CD

CD chạy `rsync -az --delete deploy/ → /opt/songnhue/`, chỉ loại trừ `.env`, `env/`, `keys/`,
`compose.local.yml`, `compose.infra.yml`. **Mọi tệp khác trên máy chủ mà nhánh đang triển khai không
có đều bị xoá.**

Thử khô đúng lệnh ấy sau lượt di trú:
```
*deleting gia-han-tls.sh
*deleting .env.truoc-doi-ten-mien-20260908
*deleting backup/khoi-phuc-qua-container.sh
*deleting backup/chay-di-tru.sh
```

⇒ **Mọi tệp đặt tay vào `/opt/songnhue` phải vào kho trước lượt CD kế tiếp**, hoặc phải nằm ngoài
đường rsync (ví dụ `/var/lib/songnhue/`). Không có cơ chế nào báo sự vắng mặt của một tệp — cùng họ
luật 31. Xem nợ T11.95.

**CD không đụng tới**: `.env` · `/var/lib/songnhue/backup` · crontab · quyền `/var/log/songnhue` ·
chứng chỉ Let's Encrypt · volume docker · các câu `REVOKE` đã ghi vào CSDL (`V202608131006` đã áp nên
Flyway không chạy lại ⇒ không bị đảo ngược).

---

## 11. Nếu đảo chiều — production → staging

Chiều này **an toàn hơn về ACL** nhưng mở ra một rủi ro khác: dữ liệu cá nhân thật rời khỏi
production.

- NĐ 13/2023: hoặc **che dữ liệu cá nhân** trước khi nạp, hoặc giữ staging ở **cùng mức bảo vệ** như
  production. Không có lựa chọn thứ ba.
- `SEED_LOCATION` của staging đang bật ⇒ sau khôi phục, ba migration seed **chưa có trong sổ** sẽ
  chạy ở lượt khởi động kế tiếp, và câu đầu tiên của chúng là `DELETE FROM articles`. Phải chèn sẵn
  ba hàng vào `flyway_schema_history` hoặc tắt `SEED_LOCATION`.
- Danh tính: cùng vấn đề §4.4, đảo vai.

---

## Phụ lục — số đo lượt 08/09/2026

| | |
|---|---|
| Bản chụp đích (điểm quay lui) | `predeploy-songnhue-20260907-232828.dump` **656.077 B**, sha256 khớp, lưu ở **hai nơi** |
| Bản chụp nguồn | `di-tru-staging-20260907-2330.dump` **1.250.768 B**, checksum khớp qua **ba máy** |
| Mục lục | 1414 mục → lọc còn **1407** (bỏ `spatial_ref_sys`, 3 `COMMENT - EXTENSION`, 3 `EXTENSION`) |
| SQL sinh ra | **18.773** dòng |
| Kho tệp | **1 → 123** đối tượng · 93.966.947 B · 88,66 MiB trong **4 giây** |
| Cửa sổ ghi đè | PRE_RESTORE `00:20:39` → app chạy lại `00:20:45` = **6 giây** |
| Nội dung sang đích | 28 bài · 120 tệp đính kèm · 8 banner · 13 org_units · 20 danh mục · 32 mục menu · 9 khoá `settings` |
| Sau di trú | `audit_logs=2438` · `article_versions=150` · `article_attachments=41` · `settings=114` · `system_backups=1` · `sessions=0` |
| Nghiệm thu | 8/8 đạt · checksum migration **0 khác biệt** · chuỗi băm audit rỗng · `Anh < Dung < Đăng < Em` |

**Nguyên nhân gốc**: [`architecture-review.md`](../../.claude/architecture-review.md) §10.80.
**Công cụ**: [`khoi-phuc-qua-container.sh`](../../deploy/backup/khoi-phuc-qua-container.sh) ·
[`sau-khoi-phuc-production.sql`](../../deploy/backup/di-tru/sau-khoi-phuc-production.sql).
