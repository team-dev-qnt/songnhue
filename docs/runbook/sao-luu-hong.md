# Sao lưu hỏng

> Cảnh báo `SaoLuuQuaHan` hoặc `SaoLuuChuaRaKhoiMayChu`.
>
> **Mức độ: nghiêm trọng, kể cả khi hệ thống vẫn chạy bình thường.** Không có PITR, không có replica
> — bản dump đêm là đường phục hồi *duy nhất*. Hệ thống đang chạy không có lưới an toàn, và mỗi giờ
> trôi qua là thêm một giờ dữ liệu không cứu được nếu ổ đĩa hỏng.

## 1. Xem chuyện gì đã xảy ra — 2 phút

```sql
-- Lượt gần nhất, gồm cả lượt hỏng. Cột error_message có nguyên văn lỗi pg_dump.
SELECT started_at, status, trigger_type, size_bytes,
       left(coalesce(error_message, ''), 300) AS loi
  FROM system_backups ORDER BY started_at DESC LIMIT 10;
```

Ba dạng, xử lý khác nhau:

| Thấy gì | Nghĩa là | Đi tiếp mục |
|---|---|---|
| Dòng `FAILED` có `error_message` | pg_dump chạy nhưng hỏng | 2 |
| **Không có dòng nào** của đêm qua | Job không được đặt, hoặc worker chết | 3 |
| Dòng `RUNNING` treo từ lâu | Tiến trình chết giữa chừng | 4 |
| Toàn `SUCCEEDED` mà vẫn báo động | Cảnh báo là `SaoLuuChuaRaKhoiMayChu` | 5 |

## 2. `FAILED` — đọc `error_message`

| Nguyên văn | Nguyên nhân | Xử lý |
|---|---|---|
| `No space left on device` | Hết đĩa | `df -h`; xoá bản dump cũ; kiểm `backup.retention-days` |
| `No such file or directory` (pg_dump) | Image thiếu `postgresql-client` | Xem `deploy/docker/backend.Dockerfile`; deploy lại |
| `password authentication failed` | Sai/thiếu `DB_READONLY_PASSWORD` | Kiểm env; vai trò `songnhue_readonly` còn tồn tại không |
| `permission denied for table …` | Bảng mới chưa cấp `SELECT` cho readonly | Migration của module đó thiếu `GRANT` — xem `V…1006` |
| `server version mismatch` | Client cũ hơn máy chủ | Nâng `postgresql-client` |
| `Quá hạn 2h` | Dump chạy quá lâu | Tăng `BACKUP_TIMEOUT`; kiểm tải I/O của máy chủ |

Sửa xong thì chạy tay ngay, **đừng chờ tới đêm mai**:

```bash
ENV=prod deploy/backup/backup.sh
```

## 3. Không có dòng nào — job không chạy

```sql
-- Job có được đặt vào hàng đợi không?
SELECT job_type, status, attempts, created_at, last_error
  FROM jobs WHERE job_type = 'DB_BACKUP' ORDER BY created_at DESC LIMIT 5;

-- Lịch có đang bật không?
SELECT setting_value FROM settings WHERE setting_key = 'backup.schedule-enabled';
```

- **`backup.schedule-enabled = false`** → có người tắt. Bật lại trên UI, và tìm xem ai tắt
  (`audit_logs` có dòng đổi tham số này).
- **Không có job nào** → bộ hẹn giờ không chạy. Xem log lúc 02:00; kiểm `WORKER_ENABLED`.
- **Job `PENDING` nằm im** → worker chết → [job-that-bai.md](job-that-bai.md).

## 4. `RUNNING` treo

Tiến trình chết giữa chừng (mất điện, OOM-kill). Dòng đó là *dấu vết cố ý để lại* — xem javadoc
`SystemBackup`. Đóng nó lại rồi chạy tay:

```sql
UPDATE system_backups
   SET status = 'FAILED', finished_at = now(),
       error_message = 'Tiến trình chết giữa chừng — đóng bằng tay'
 WHERE status = 'RUNNING' AND started_at < now() - interval '3 hours';
```

Kiểm `dmesg | grep -i oom` — nếu là OOM-kill thì tăng bộ nhớ container, vì lần sau vẫn thế.

## 5. Bản dump chưa ra khỏi máy chủ CSDL

Bản dump nằm cùng máy với CSDL nó phải cứu **không cứu được gì** khi ổ đĩa hỏng. Trên **VM-3**:

```bash
# Chạy tay xem hỏng ở đâu
/opt/songnhue/backup/pull-from-prod.sh

# Cron có chạy không
grep pull-from-prod /var/log/syslog | tail
tail -50 /var/log/songnhue-pull.log
```

| Triệu chứng | Xử lý |
|---|---|
| `Permission denied (publickey)` | Khoá SSH của VM-3 hết hạn / bị xoá khỏi `authorized_keys` trên VM-1 |
| `Checksum KHÔNG khớp` | Tệp hỏng trên đường truyền — script đã xoá để kéo lại; chạy lại |
| rsync treo | Kiểm mạng VM-3 → VM-1, tường lửa cổng 22 |
| Metric không xuất hiện | Sai `METRICS_FILE`; kiểm `--collector.textfile.directory` của node-exporter |

## 6. Xác nhận đã xong

```bash
ENV=prod deploy/backup/backup.sh      # phải in ✓
make backup-verify ENV=prod           # khoá KHÔNG nằm trong bản dump (DoD 13d)
```

Trên Grafana, bảng **Sông Nhuệ — Vận hành**: ô *Tuổi bản sao lưu gần nhất* phải về xanh trong vòng
một chu kỳ lấy số (30 giây). Cảnh báo tự tắt sau đó.

> ⚠ Nếu sự cố kéo dài quá một ngày, hãy **ghi lại khoảng thời gian không có bản sao lưu**. Đó là
> khoảng dữ liệu sẽ mất nếu ổ đĩa hỏng trong khoảng ấy — thông tin cần cho báo cáo sự cố, và cần cho
> quyết định có phải chạy sao lưu bù ngay giữa giờ hành chính hay không.
