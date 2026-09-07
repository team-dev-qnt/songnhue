# Việc nền không chạy

> Cảnh báo `WorkerCoVeDaChet` (nghiêm trọng) hoặc `HangDoiViecTonDong` (cảnh báo).
>
> **`WorkerCoVeDaChet` nặng hơn hẳn**, dù con số nhỏ hơn: 5 việc *đứng im* nguy hiểm hơn 60 việc
> *đang giảm dần*. Ngưỡng theo số lượng bỏ sót đúng trường hợp đầu.

## Vì sao đây không phải chuyện nhỏ

Worker chết thì giao diện vẫn chạy bình thường, không lỗi nào báo ra — nhưng những thứ sau lặng lẽ
ngừng: **sao lưu hằng đêm**, thông báo in-app và email, quét virus tệp tải lên (tệp kẹt ở trạng thái
chưa tải xuống được), kết xuất báo cáo, kết xuất nhật ký quá hạn, tạo partition `audit_logs` tháng
tới.

Mục đầu là mục đắt nhất: worker chết vào thứ Sáu nghĩa là cả cuối tuần không có bản sao lưu nào.

## 1. Nhìn hàng đợi

```sql
SELECT status, job_type, count(*), min(created_at) AS cu_nhat
  FROM jobs GROUP BY status, job_type ORDER BY status, count(*) DESC;

-- Việc hỏng gần đây, kèm nguyên văn lỗi
SELECT public_id, job_type, attempts, max_attempts, available_at,
       left(coalesce(last_error, ''), 300) AS loi
  FROM jobs WHERE status = 'FAILED' ORDER BY finished_at DESC LIMIT 20;
```

| Thấy gì | Nghĩa là | Mục |
|---|---|---|
| Nhiều `PENDING`, không cái nào thành `RUNNING` | Worker không nhặt việc | 2 |
| `RUNNING` treo rất lâu | Handler treo | 3 |
| Nhiều `FAILED` cùng `job_type` | Lỗi thật của loại việc đó | 4 |

## 2. Worker không nhặt việc

```bash
grep -i "JobWorker" /var/log/songnhue/app.log | tail -30
```

- **`WORKER_ENABLED=false`** → có người tắt. Bật lại rồi khởi động lại app.
- **Không có dòng log nào của worker** → bộ hẹn giờ không chạy. Khởi động lại app.
- **`connection is not available`** → cạn connection pool. Kiểm `DB_POOL_MAX`; tìm truy vấn chậm:

  ```sql
  SELECT pid, now() - query_start AS chay_bao_lau, left(query, 120)
    FROM pg_stat_activity WHERE state = 'active' ORDER BY query_start LIMIT 10;
  ```

## 3. Job `RUNNING` treo

Job đang chạy dở lúc node chết sẽ được trả về hàng đợi, nhưng job *thật sự treo* thì không. Trả nó
về `PENDING` để lượt sau nhặt lại:

```sql
UPDATE jobs
   SET status = 'PENDING', started_at = NULL, available_at = now()
 WHERE status = 'RUNNING' AND started_at < now() - interval '2 hours';
```

⚠ **Đừng làm việc này với `DB_RESTORE`.** Khôi phục có `max_attempts = 1` một cách cố ý: chạy lại
một thao tác khôi phục hỏng dở là ghi đè tiếp lên chỗ đang dở, và bản `PRE_RESTORE` của lượt thứ hai
sẽ chụp lại chính trạng thái hỏng đó — **đè mất đường lùi**. Xem
[khoi-phuc-du-lieu.md](khoi-phuc-du-lieu.md).

## 4. Chạy lại việc đã hỏng

Sau khi đã **sửa nguyên nhân** (không sửa thì chạy lại cũng hỏng y như cũ):

```sql
-- Một việc cụ thể
UPDATE jobs SET status = 'PENDING', attempts = 0, available_at = now(), last_error = NULL
 WHERE public_id = '<uuid>';

-- Cả một loại việc hỏng trong 24h qua
UPDATE jobs SET status = 'PENDING', attempts = 0, available_at = now(), last_error = NULL
 WHERE status = 'FAILED' AND job_type = 'NOTIFICATION_DISPATCH'
   AND finished_at > now() - interval '24 hours';
```

Riêng sao lưu thì chạy tay nhanh hơn: `ENV=prod deploy/backup/backup.sh`.

## 5. Lỗi hay gặp theo loại việc

| `job_type` | Lỗi thường gặp | Xử lý |
|---|---|---|
| `DB_BACKUP` | Hết đĩa, thiếu `pg_dump` | [sao-luu-hong.md](sao-luu-hong.md) |
| `NOTIFICATION_DISPATCH` | SMTP từ chối / hết hạn kết nối | Kiểm `SMTP_*`; job tự thử lại 3 lần |
| `VIRUS_SCAN` | Chưa dựng ClamAV (nợ #20) | Đang là `SKIPPED` — đúng thiết kế tạm thời |
| `AUDIT_ARCHIVE` | Thiếu `DB_ARCHIVER_PASSWORD` | Đặt biến; **không dòng nhật ký nào bị xoá khi lỗi** (G7) |
| `AUDIT_PARTITION` | Quyền tạo partition | Hàm chạy `SECURITY DEFINER`; kiểm chủ sở hữu hàm |

## 6. Xác nhận đã xong

Trên Grafana, biểu đồ *Tồn đọng hàng đợi việc nền* phải **giảm dần**, không chỉ là "dưới ngưỡng".
Đứng yên ở mức thấp vẫn là worker chết.
