# Runbook — partition của `audit_logs`

`audit_logs` phân mảnh RANGE theo tháng. Việc tạo partition do
`core_ensure_audit_partitions(n)` lo, chạy tự động hằng tháng (WS-6/T6.8) và đã
tạo sẵn 12 tháng runway ngay lúc migrate.

## Kiểm tra nhanh

```sql
-- Còn bao nhiêu tháng runway?
SELECT max(substring(c.relname from 'p(\d{6})')) AS partition_xa_nhat
  FROM pg_inherits i JOIN pg_class c ON c.oid = i.inhrelid
 WHERE i.inhparent = 'public.audit_logs'::regclass;

-- Partition mặc định PHẢI rỗng
SELECT count(*) FROM audit_logs_default;
```

## Sự cố: `audit_logs_default` có bản ghi

**Nghĩa là gì.** Job bảo trì partition đã chết đủ lâu để hết runway. Nghiệp vụ
**không** hỏng — bản ghi vẫn ghi được, vẫn đọc được, chuỗi hash vẫn liền mạch;
chỉ là truy vấn theo khoảng thời gian chậm hơn vì không cắt tỉa được partition.

**Vì sao hàm tạo partition lại bỏ qua tháng đó.** PostgreSQL từ chối tạo
partition mới khi partition mặc định đang giữ dòng thuộc khoảng đó.
`core_create_audit_partition()` phát hiện và trả `WARNING` rồi bỏ qua, thay vì
làm đổ vỡ cả migration hoặc cả job.

**Cách gỡ** (làm ngoài giờ hành chính — bước 2 khóa bảng):

```sql
BEGIN;

-- 1. Tách partition mặc định ra
ALTER TABLE audit_logs DETACH PARTITION audit_logs_default;

-- 2. Tạo partition cho từng tháng đang kẹt (lặp lại cho mỗi tháng)
CREATE TABLE audit_logs_p202701 PARTITION OF audit_logs
    FOR VALUES FROM ('2027-01-01') TO ('2027-02-01');

-- 3. Chuyển dòng về đúng chỗ
INSERT INTO audit_logs
SELECT * FROM audit_logs_default
 WHERE occurred_at >= '2027-01-01' AND occurred_at < '2027-02-01';

DELETE FROM audit_logs_default
 WHERE occurred_at >= '2027-01-01' AND occurred_at < '2027-02-01';

-- 4. Gắn lại partition mặc định
ALTER TABLE audit_logs ATTACH PARTITION audit_logs_default DEFAULT;

COMMIT;
```

> ⚠ Bước 3 đi qua bảng cha nên **trigger `trg_audit_logs_chain` sẽ cấp lại
> `seq`/`hash` mới và làm gãy chuỗi**. Phải tắt trigger trong lúc chuyển:
> `ALTER TABLE audit_logs DISABLE TRIGGER trg_audit_logs_chain;` … và bật lại
> sau `COMMIT`. Chạy `make db-verify-audit` để xác nhận chuỗi còn nguyên vẹn.

## Sau khi gỡ

1. Chạy `SELECT core_ensure_audit_partitions(12);` để dựng lại runway.
2. Tìm nguyên nhân job chết (bảng `jobs`, `status = 'FAILED'`, `job_type` bảo
   trì partition) — đây mới là lỗi gốc.
3. `make db-verify-audit` phải trả về **0 dòng**.

## Quyền trên partition

Quyền **không** kế thừa từ bảng cha khi truy vấn thẳng vào partition. Mọi
partition tạo bằng `core_create_audit_partition()` đã được siết sẵn:

| Role | Quyền trên partition |
|---|---|
| `songnhue_app` | `SELECT`, `INSERT` — **không** UPDATE/DELETE/TRUNCATE |
| `songnhue_archiver` | `SELECT`, `DELETE` (job kết xuất quá hạn — G7) |
| `songnhue_readonly` | `SELECT` |

Nếu tạo partition **bằng tay** (như bước gỡ ở trên), phải tự chạy lại các lệnh
`GRANT`/`REVOKE` tương ứng — xem thân hàm `core_create_audit_partition`.
