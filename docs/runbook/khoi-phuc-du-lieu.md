# Khôi phục CSDL từ bản sao lưu

> ⚠ **Thao tác này ghi đè toàn bộ dữ liệu. Không hoàn tác được.**
> Đọc hết mục 0 trước khi gõ lệnh đầu tiên.
>
> **Cập nhật 08/09/2026 sau lượt khôi phục THẬT đầu tiên trên production.** Ba khẳng định của bản
> trước tài liệu này **đã sai** và được sửa ở mục 1, 2, 3. Nếu bạn nhớ bản cũ, đọc lại.

## 0. Dừng lại một phút — ba câu hỏi

**Có chắc phải khôi phục không?** Khôi phục xoá sạch mọi thay đổi kể từ lúc bản dump được tạo — tối
đa 24 giờ làm việc của cả Công ty. Xoá nhầm một bảng thì khôi phục *chọn lọc* đúng bảng đó rẻ hơn
nhiều (mục 5).

**Khôi phục về đâu?** [`architecture-review.md`](../../.claude/architecture-review.md) §7.3 khuyến
nghị khôi phục ra **Staging trước** để đối chiếu, rồi mới quyết định làm gì với Production. Khi chưa
chắc bản dump nào đúng thì đây là bước bắt buộc, không phải bước tuỳ chọn.

**Mất bao nhiêu?** So mốc `finished_at` của bản dump với hiện tại:

```sql
SELECT file_name, finished_at, now() - finished_at AS se_mat_bao_nhieu, size_bytes, status
  FROM system_backups WHERE status = 'SUCCEEDED' ORDER BY finished_at DESC LIMIT 5;
```

> ⚠ Bảng này **có thể trống** ngay sau một lượt di trú môi trường — khối vá cố ý xoá các hàng trỏ
> vào tệp nằm trên máy khác. Danh sách tệp thật nằm ở `ls -lt /var/lib/songnhue/backup/`.

---

## 1. Đường qua giao diện (M5.11) — kiểm xem có bật không TRƯỚC

Điều kiện: tài khoản **Super Admin**, đã bật 2FA, và môi trường có đặt `DB_RESTORE_PASSWORD`.

```bash
awk -F= '/^DB_RESTORE_PASSWORD=/{v=$2; gsub(/[[:space:]]+$/,"",v); print "độ dài = " length(v)}' /opt/songnhue/.env
```

⛔ **Đo trên production 08/09: độ dài = 0.** Tức nút khôi phục **đang tắt** (`ADM-2010`), và đường
duy nhất là mục 2. Đừng mở giao diện lên tìm nút; nó sẽ không có ở đó.

⛔⛔ **Và ngay cả khi bật, đường này mang một khuyết tật chưa vá.** `RestoreService.restoreCommand()`
dùng `pg_restore --clean --if-exists --single-transaction` và **luôn** nạp vào một CSDL đang có dữ
liệu — tức đúng cảnh làm lộ lỗi ở mục 3.1. Nút này **chưa ai chạy thật bao giờ**. Xem nợ T11.92.

Nếu vẫn dùng: Quản trị → **Sao lưu & khôi phục** → chọn bản → **Khôi phục** → nhập chuỗi xác nhận
`SONGNHUE` · lý do (≥ 10 ký tự) · **mã TOTP hiện tại**. Hệ thống bật chế độ bảo trì → đối chiếu
checksum → chụp `PRE_RESTORE` → ngắt kết nối → `pg_restore` → tắt bảo trì. Bấm xong thì để yên.

---

## 2. Đường thủ công — đường thật sự dùng được

### ⛔ `deploy/backup/restore.sh` KHÔNG chạy được trên máy chủ

Đo 08/09 trên **cả hai** VPS:

```
psql / pg_dump / pg_restore : KHÔNG CÓ trên host
DB_HOST=postgres            : chỉ phân giải BÊN TRONG mạng docker
container postgres          : "5432/tcp": null — không publish cổng nào
$DEPLOY_DIR/env/prod.env    : không tồn tại trên host (rsync loại trừ env/)
```

Đường khôi phục thủ công **duy nhất** của hệ — chính là thứ T7.13-a đã vá sau §10.58 — chỉ chạy được
trên máy dev, tức nơi không bao giờ xảy ra thảm hoạ. *Một cơ chế tồn tại trong mã nhưng chưa có hiệu
lực ở nơi nó phải chặn.*

### ✅ Dùng bản chạy qua container

```bash
cd /opt/songnhue
ls -lt /var/lib/songnhue/backup/*.dump | head        # chọn bản

XAC_NHAN=songnhue ENV_FILE=/opt/songnhue/.env \
  ./backup/khoi-phuc-qua-container.sh /var/lib/songnhue/backup/<tên>.dump
```

[`khoi-phuc-qua-container.sh`](../../deploy/backup/khoi-phuc-qua-container.sh) giữ nguyên cả 8 bảo
đảm của `restore.sh` nhưng đi qua `docker exec` — đúng cách `pre-deploy-dump.sh` đã làm và đã chạy
thật trong mọi lượt CD:

① đo bản dump từ **cả hai phía** (host và container) · ② đối chiếu checksum trước khi đụng dữ liệu ·
③ bắt xác nhận bằng **tên CSDL** qua `XAC_NHAN=` (không phải `read`, vì `read` trên ssh không tty
nhận EOF và script tự huỷ — hỏng đúng lúc người ta chạy nó từ xa) · ④ tự chụp `PRE_RESTORE` ·
⑤ ngắt kết nối khác · ⑥ lọc mục lục theo chủ sở hữu **và loại mục `EXTENSION`** · ⑦ bỏ bảng phân
mảnh trong **cùng giao dịch** với lượt nạp · ⑧ nghiệm thu bằng **vai trò của ứng dụng**.

Chạy khối SQL vá kèm theo (nếu có): thêm `--sau <tệp.sql>`.

### Tắt chế độ bảo trì bằng tay

```bash
docker exec -i songnhue-postgres psql -U postgres -d songnhue -c \
  "UPDATE settings SET setting_value='false' WHERE setting_key='system.maintenance-mode';" < /dev/null
```

⚠ **Rồi phải khởi động lại ứng dụng.** Cache Caffeine của bảng `settings` giữ giá trị tới 60 giây,
nhưng quan trọng hơn: bản dump vừa ghi đè có thể mang theo *giá trị cũ* của cờ này.

---

## 3. `pg_restore` báo lỗi

Lệnh chạy với `--single-transaction` + `ON_ERROR_STOP=1`, nên **hỏng là cuộn ngược sạch** — CSDL trở
về đúng trạng thái trước khi khôi phục. Đã đo đúng tình huống ấy: sau một lượt thoát 1, đích vẫn còn
107 bảng và đủ số hàng.

⛔ **Khi gặp lỗi, đừng "chữa nhanh" bằng cách bỏ `--exit-on-error` hay `--single-transaction`.** Đó là
lúc duy nhất thật sự mất dữ liệu.

### 3.1 `cannot drop index … because index … requires it` — bảng phân mảnh

```
ERROR: cannot drop index public.hydro_readings_p202708_station_id_measured_at_idx
       because index public.ix_hydro_readings_station_time requires it
```

`--clean` phát `DROP INDEX`/`DROP CONSTRAINT` cho **từng phân mảnh**, mà chỉ mục của phân mảnh không
xoá lẻ được khi bảng cha còn. Kho có **ba** bảng phân mảnh: `audit_logs`, `hydro_raw_logs`,
`hydro_readings`.

⚠ **Chỉ xuất hiện khi đích ĐÃ CÓ dữ liệu.** Trên cluster vừa dựng lại thì những câu ấy là no-op —
đó là lý do mọi lượt diễn tập trước (§10.58, T11.3-b) không thể thấy.

**Xử lý**: dùng `khoi-phuc-qua-container.sh`, nó đã bỏ bảng phân mảnh trong cùng giao dịch. Chi tiết
kỹ thuật: [di-tru-du-lieu-giua-moi-truong.md §7.1](di-tru-du-lieu-giua-moi-truong.md).

### 3.2 `must be owner of extension postgis`

Bộ lọc mục lục cũ để lọt mục `EXTENSION`: `pg_dump` không ghi chủ sở hữu cho extension nên
`awk '$NF != "postgres"'` giữ nó lại.

⚠ Đây **đúng nguyên văn** thông điệp mà §10.58 gán cho mục `COMMENT - EXTENSION` và vá nhầm chỗ.
Dùng `songnhue_owner` **không** cứu được — `postgis` thuộc về `postgres`.

**Xử lý**: `khoi-phuc-qua-container.sh` đã loại mục ấy. Nó cũng chốt rằng đích **có sẵn đủ 3
extension**, vì bản dump đã lọc thì không tạo lại được chúng.

### 3.3 Bảng khác

| Thông báo | Nguyên nhân | Xử lý |
|---|---|---|
| `permission denied for schema public` | CSDL đích thiếu `GRANT` cấp schema | `GRANT ALL ON SCHEMA public TO songnhue_owner; GRANT USAGE … TO songnhue_app, songnhue_archiver, songnhue_readonly;` |
| `must be owner of table …` | chạy bằng vai trò không phải chủ sở hữu | dùng `songnhue_owner` (`DB_MIGRATION_USER`) |
| `unsupported version … in file header` | dump sinh bởi máy chủ **mới hơn** client | chạy qua container ⇒ không thể gặp; nếu gặp là đang dùng công cụ ngoài |
| `database … is being accessed by other users` | còn phiên khác giữ khoá | script tự ngắt; nếu vẫn còn thì dừng `songnhue-app` trước |
| Treo không thông báo gì | đang chờ khoá | `SELECT * FROM pg_locks WHERE NOT granted;` |
| App khởi động rồi chết ở `Detected applied migration not resolved locally` | bản dump từ môi trường có `SEED_LOCATION` khác | xem [di-tru-du-lieu-giua-moi-truong.md §2.2](di-tru-du-lieu-giua-moi-truong.md) — ⛔ **không** chữa bằng cách bật `SEED_LOCATION` |

---

## 4. ⛔ Nếu bản dump đến từ MÔI TRƯỜNG KHÁC

Bản dump mang theo **dữ liệu + lược đồ + ACL**. Khôi phục **thay** quyền của đích bằng quyền của
nguồn. Đo 08/09: dữ liệu staging cho `songnhue_app` quyền `arwd` trên ~35 bảng mà production cố ý chỉ
cho `ar`/`r` — gồm `audit_logs`, `audit_chain_head`, `hydro_raw_logs`, `flyway_schema_history`.

⇒ Sau khôi phục **bắt buộc** chạy phần tái khẳng định quyền append-only (mục ⑥ của
[`sau-khoi-phuc-production.sql`](../../deploy/backup/di-tru/sau-khoi-phuc-production.sql)), và kiểm
bằng mục 6 phép 7 dưới đây.

Toàn bộ quy trình: [di-tru-du-lieu-giua-moi-truong.md](di-tru-du-lieu-giua-moi-truong.md).

---

## 5. Khôi phục chọn lọc — thường là thứ bạn thật sự cần

Xoá nhầm dữ liệu một bảng thì đừng ghi đè cả CSDL. Định dạng `-Fc` cho phép lấy ra đúng phần cần.
Mọi lệnh chạy **trong container**; bản dump phải nằm ở `/var/lib/songnhue/backup` (bind-mount cùng
đường dẫn):

```bash
D=/var/lib/songnhue/backup/<tên>.dump
docker exec -i songnhue-postgres pg_restore --list "$D" < /dev/null | less

docker exec -i songnhue-postgres psql -U postgres -d songnhue \
  -c 'CREATE SCHEMA IF NOT EXISTS khoi_phuc_tam;' < /dev/null

docker exec -i songnhue-postgres sh -c "
  pg_restore --data-only --table=<ten_bang> --no-owner --schema=public '$D' \
  | sed 's/public\\./khoi_phuc_tam./g' \
  | psql -U postgres -d songnhue" < /dev/null
```

Đối chiếu ở `khoi_phuc_tam` rồi mới chép sang `public` bằng `INSERT … SELECT`. Chậm hơn, nhưng không
mất 24 giờ dữ liệu của những bảng chẳng liên quan. Xong thì `DROP SCHEMA khoi_phuc_tam CASCADE;`.

---

## 6. Kiểm tra sau khôi phục — bắt buộc, đủ 7 mục

```bash
q() { docker exec -i songnhue-postgres psql -U postgres -d songnhue -At -c "$1" < /dev/null; }
```

**1. Ứng dụng lên được**
```bash
curl -fsS http://localhost:8080/actuator/health/readiness   # từ trong mạng docker
docker inspect -f '{{.State.Health.Status}}' songnhue-app   # healthy
```

**2. ⭐ Đọc được bằng VAI TRÒ CỦA ỨNG DỤNG** — không phải bằng chủ sở hữu
```bash
MK=$(awk -F= '/^DB_PASSWORD=/{v=$2;gsub(/[[:space:]]+$/,"",v);print v}' /opt/songnhue/.env)
docker exec -i -e PGPASSWORD="$MK" songnhue-postgres \
  psql -U songnhue_app -d songnhue -At -c 'SELECT count(*) FROM users' < /dev/null
```
⚠ **Đây là phép §10.58 đã thiếu.** Chủ sở hữu *luôn* đọc được, nên hỏi bằng chủ sở hữu **không phân
biệt được hai trạng thái**. Khôi phục vào cluster mới mà thiếu `GRANT` thì mọi bảng đều có dữ liệu và
app vẫn chết ngay lúc khởi động.

**3. Chuỗi hash nhật ký còn nguyên vẹn** (rỗng = nguyên vẹn)
```bash
q 'SELECT * FROM core_verify_audit_chain()'
```

**4. Số bản ghi các bảng trọng yếu** — so với con số ghi lại **TRƯỚC** khi khôi phục
```bash
q "SELECT 'users='||(SELECT count(*) FROM users)||' org_units='||(SELECT count(*) FROM org_units)
   ||' settings='||(SELECT count(*) FROM settings)||' audit_logs='||(SELECT count(*) FROM audit_logs)"
```

**5. Migration khớp mã nguồn đang chạy**
```bash
q "SELECT count(*)||' hàng, đỉnh '||max(version) FROM flyway_schema_history WHERE success"
```
⚠ **Mục hay bị bỏ nhất và hậu quả nặng nhất.** Bản dump cũ hơn lần deploy gần nhất sẽ khôi phục về
schema *cũ* trong khi mã nguồn là bản *mới*. Triệu chứng là lỗi lẻ tẻ ở vài màn hình, không phải app
chết — nên dễ bị bỏ qua hàng giờ.

**6. Chế độ bảo trì đã TẮT**
```bash
q "SELECT setting_value FROM settings WHERE setting_key='system.maintenance-mode'"   # false
```

**7. ⭐ Quyền append-only còn nguyên** — bắt buộc nếu bản dump đến từ môi trường khác (mục 4)
```bash
q "SELECT 'audit UPDATE='||has_table_privilege('songnhue_app','audit_logs','UPDATE')::text
   ||' audit DELETE='||has_table_privilege('songnhue_app','audit_logs','DELETE')::text
   ||' hydro DELETE='||has_table_privilege('songnhue_app','hydro_raw_logs','DELETE')::text
   ||' chain SELECT='||has_table_privilege('songnhue_app','audit_chain_head','SELECT')::text
   ||' | audit INSERT='||has_table_privilege('songnhue_app','audit_logs','INSERT')::text"
```
Đạt là: bốn phép đầu **`false`**, phép cuối **`true`**. Vế cuối quan trọng ngang bốn vế đầu — nó
chứng minh đã siết **đúng chỗ**, không siết quá tay.

---

## 7. Sau đó

- **Đăng nhập lại**: khôi phục ghi đè bảng `sessions`, mọi phiên đang mở đều không còn hợp lệ.
- **Khởi động lại `public-web`** nếu cổng công khai có nội dung: nó giữ đệm ISR của trang cũ.
- Ghi **RTO thật** (từ lúc bắt đầu tới lúc mục 6 xanh hết) vào
  [dien-tap-khoi-phuc.md](dien-tap-khoi-phuc.md) — cam kết là ≤ 4 giờ, và chỉ con số đo được mới
  chứng minh điều đó.
- Thông báo cho người dùng khoảng thời gian dữ liệu đã mất, để họ nhập lại.
