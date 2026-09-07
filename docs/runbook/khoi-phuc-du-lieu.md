# Khôi phục CSDL từ bản sao lưu

> ⚠ **Thao tác này ghi đè toàn bộ dữ liệu. Không hoàn tác được.**
> Đọc hết mục 0 trước khi gõ lệnh đầu tiên.

## 0. Dừng lại một phút — ba câu hỏi

**Có chắc phải khôi phục không?** Khôi phục xoá sạch mọi thay đổi kể từ lúc bản dump được tạo — tối
đa 24 giờ làm việc của cả Công ty. Xoá nhầm một bảng thì khôi phục *chọn lọc* đúng bảng đó rẻ hơn
nhiều (mục 4).

**Khôi phục về đâu?** [`architecture-review.md`](../../.claude/architecture-review.md) §7.3 khuyến
nghị khôi phục ra **Staging trước** để đối chiếu, rồi mới quyết định làm gì với Production. Khi chưa
chắc bản dump nào đúng thì đây là bước bắt buộc, không phải bước tuỳ chọn.

**Mất bao nhiêu?** So mốc `finished_at` của bản dump với hiện tại:

```sql
SELECT file_name, finished_at, now() - finished_at AS se_mat_bao_nhieu, size_bytes, status
  FROM system_backups WHERE status = 'SUCCEEDED' ORDER BY finished_at DESC LIMIT 5;
```

---

## 1. Đường bình thường — qua giao diện (M5.11)

Điều kiện: tài khoản **Super Admin**, đã bật 2FA, và môi trường có đặt `DB_RESTORE_PASSWORD`.

1. Quản trị → **Sao lưu & khôi phục** → chọn bản → **Khôi phục**.
2. Nhập: chuỗi xác nhận `SONGNHUE` · lý do (≥ 10 ký tự) · **mã TOTP hiện tại**.
3. Hệ thống tự làm, theo thứ tự — bấm xong thì để yên, đừng khởi động lại app giữa chừng:

   | Bước | Việc | Hỏng thì |
   |---|---|---|
   | 1 | Bật chế độ bảo trì (chặn mọi ghi) | dừng, không đụng dữ liệu |
   | 2 | Đối chiếu checksum bản dump | dừng, `ADM-2012` |
   | 3 | **Chụp bản `PRE_RESTORE`** | **dừng** — không có đường lùi thì không khôi phục |
   | 4 | Ngắt các kết nối khác | chỉ cảnh báo, chạy tiếp |
   | 5 | `pg_restore --clean --single-transaction` | `ADM-2013`, xem mục 3 |
   | 6 | Tắt chế độ bảo trì | xem mục 2 |

4. Theo dõi tiến độ ở màn hình việc nền. Xong thì làm tiếp **mục 5 (kiểm tra sau khôi phục)**.

> Nút bị mờ hoặc trả `ADM-2010` = môi trường này **không bật** khôi phục qua UI. Không phải lỗi —
> đó là lựa chọn cấu hình (xem `BackupProperties`). Dùng mục 2.

---

## 2. Đường thủ công — khi ứng dụng không chạy được

Đây thường chính là lúc cần khôi phục nhất.

```bash
# Trên máy chủ, tại /opt/songnhue
ENV=prod deploy/backup/restore.sh                      # chọn bản mới nhất
ENV=prod deploy/backup/restore.sh /var/lib/songnhue/backup/songnhue-...dump
```

Script tự: đối chiếu checksum → bắt gõ đúng tên CSDL → **chụp bản `PRE_RESTORE`** → ngắt kết nối →
`pg_restore`.

### Tắt chế độ bảo trì bằng tay

Khi khôi phục hỏng ở bước cuối, cờ bảo trì có thể còn bật:

```sql
UPDATE settings SET setting_value = 'false' WHERE setting_key = 'system.maintenance-mode';
```

⚠ **Rồi phải khởi động lại ứng dụng.** Cache Caffeine của bảng `settings` giữ giá trị tới 60 giây,
nhưng quan trọng hơn: bản dump vừa ghi đè có thể mang theo *giá trị cũ* của cờ này. Sửa SQL mà không
khởi động lại thì trạng thái trong bộ nhớ và trong CSDL lệch nhau.

---

## 3. `pg_restore` báo lỗi

Lệnh chạy với `--single-transaction`, nên **hỏng là rollback sạch** — CSDL trở về đúng trạng thái
trước khi khôi phục. Đây là điều tốt: không có trạng thái nửa vời.

| Thông báo | Nguyên nhân | Xử lý |
|---|---|---|
| `must be owner of table …` | Chạy bằng vai trò không phải chủ sở hữu | Dùng `songnhue_owner` (`DB_MIGRATION_USER`) |
| `unsupported version … in file header` | Bản dump sinh bởi máy chủ **mới hơn** client | Nâng `postgresql-client` trong image — xem ghi chú ở `deploy/docker/backend.Dockerfile` |
| `database … is being accessed by other users` | Còn phiên khác giữ khoá | Chạy lại bước ngắt kết nối ở mục 2 |
| Treo không thông báo gì | Đang chờ khoá | `SELECT * FROM pg_locks WHERE NOT granted;` |

---

## 4. Khôi phục chọn lọc — thường là thứ bạn thật sự cần

Xoá nhầm dữ liệu một bảng thì đừng ghi đè cả CSDL. Định dạng `-Fc` cho phép lấy ra đúng phần cần:

```bash
# Xem trong bản dump có gì
pg_restore --list bansaoluu.dump | less

# Chỉ khôi phục MỘT bảng, vào một schema tạm để đối chiếu trước
psql -U songnhue_owner -d songnhue -c 'CREATE SCHEMA khoi_phuc_tam;'
pg_restore --data-only --table=ten_bang --no-owner \
           --schema=public bansaoluu.dump \
           | sed 's/public\./khoi_phuc_tam./g' \
           | psql -U songnhue_owner -d songnhue
```

Đối chiếu ở `khoi_phuc_tam` rồi mới chép sang `public` bằng `INSERT … SELECT`. Chậm hơn, nhưng không
mất 24 giờ dữ liệu của những bảng chẳng liên quan.

---

## 5. Kiểm tra sau khôi phục — bắt buộc, đủ 5 mục

```bash
# 1. Ứng dụng lên được
curl -fsS http://localhost:8080/actuator/health/readiness

# 2. Chuỗi hash nhật ký còn nguyên vẹn (rỗng = nguyên vẹn)
make db-verify-audit ENV=prod

# 3. Số bản ghi các bảng trọng yếu — so với con số ghi lại TRƯỚC khi khôi phục
psql -U songnhue_readonly -d songnhue -c \
  "SELECT 'users' t, count(*) FROM users
   UNION ALL SELECT 'org_units', count(*) FROM org_units
   UNION ALL SELECT 'settings', count(*) FROM settings
   UNION ALL SELECT 'audit_logs', count(*) FROM audit_logs;"

# 4. Migration khớp phiên bản mã nguồn đang chạy
make migrate-info ENV=prod

# 5. Chế độ bảo trì đã TẮT
psql -U songnhue_readonly -d songnhue -c \
  "SELECT setting_value FROM settings WHERE setting_key = 'system.maintenance-mode';"
```

⚠ **Mục 4 là mục hay bị bỏ nhất và hậu quả nặng nhất.** Bản dump cũ hơn lần deploy gần nhất sẽ khôi
phục về schema *cũ*, trong khi mã nguồn đang chạy là bản *mới*. Triệu chứng là lỗi lẻ tẻ ở vài màn
hình, không phải app chết — nên dễ bị bỏ qua hàng giờ. Gặp lệch: chạy `make migrate ENV=prod`.

## 6. Sau đó

- Đăng nhập lại: khôi phục ghi đè bảng `sessions`, mọi phiên đang mở đều không còn hợp lệ.
- Ghi lại **RTO thật** (từ lúc bắt đầu tới lúc mục 5 xanh hết) vào
  [dien-tap-khoi-phuc.md](dien-tap-khoi-phuc.md) — cam kết là ≤ 4 giờ, và chỉ con số đo được mới
  chứng minh điều đó.
- Thông báo cho người dùng khoảng thời gian dữ liệu đã mất, để họ nhập lại.
