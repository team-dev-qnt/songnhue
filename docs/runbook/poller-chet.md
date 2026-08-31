# Nguồn dữ liệu ngoài im lặng (poller thủy văn)

> Cảnh báo `NguonDuLieuImLang`.
>
> ⚠ **Đây là cảnh báo có tính khẩn cấp cao nhất trong hệ thống**, ngang bằng sao lưu hỏng — dù nghe
> có vẻ chỉ là "thiếu vài số đo".

## Vì sao khẩn cấp

Nguồn `songnhue.bhh40.net` **không có API lịch sử** (chốt G3, `CLAUDE.md` quy tắc 18). Poller là nơi
duy nhất bắt được dữ liệu. Khung 10 phút nào bị lỡ là **mất vĩnh viễn** — không có cách nào lấy lại,
không phải chờ, không phải backfill.

Poller chết lúc 3 giờ sáng thứ Bảy mà tới thứ Hai mới phát hiện = mất 2 ngày dữ liệu mực nước của
19 điểm đo. Vì vậy luật cảnh báo này có `for` ngắn nhất trong cả bộ (5 phút).

> **Trạng thái Phase 0**: chưa có nguồn nào đăng ký với `DataFreshnessRegistry`, nên cảnh báo này
> chưa khớp gì cả và chỉ số `telemetry` trong health báo UP kèm ghi chú "chưa có nguồn nào". Runbook
> viết sẵn để Phase 2 chỉ việc cắm nguồn vào — xem javadoc `DataFreshnessRegistry`.

## 1. Nguồn còn sống không — 1 phút

> ⚠⚠ **Từ WS-28 (31/08/2026), mã số KHÔNG còn nằm ở biến môi trường.** Nhà của nó là cột
> `api_sources.credential`, mã hoá AES-256-GCM, đổi trên màn hình *Quản trị › Nguồn dữ liệu*.
> `HYDRO_API_KEY` chỉ còn là giá trị **mồi** cho lần triển khai đầu và **rỗng ở staging / rehearse /
> prod**. Chạy lệnh dưới đây với biến rỗng sẽ nhận `not.working` — trông y hệt "sai mã số" và đẩy
> người trực đi tìm nhầm hướng, nên dòng `[ -z ... ]` ở đầu là bắt buộc, không phải cho đẹp.

**Trước tiên, kiểm nguồn đã cấu hình mã số chưa** — đây là nguyên nhân số một của "poller không chạy
mà không lỗi gì":

```sql
SELECT code, name,
       credential IS NOT NULL AS da_co_ma_so,
       status, consecutive_failures, last_success_at, last_failure_reason
  FROM api_sources WHERE deleted_at IS NULL;
```

`da_co_ma_so = false` ⇒ **dừng ở đây**: vào *Quản trị › Nguồn dữ liệu* đặt mã số, không cần đọc tiếp.

```bash
# ⚠ Dấu ";" ở CUỐI mã số là BẮT BUỘC. Thiếu thì API trả `not.working`,
#   trông y hệt lỗi sai key.
# ⚠ Mã số lấy từ người giữ mã (hoặc bản ghi bàn giao), KHÔNG đọc ngược được từ CSDL —
#   cột `credential` là bản mã và không endpoint nào trả nó ra.
[ -z "${HYDRO_API_KEY}" ] && { echo "⛔ HYDRO_API_KEY rỗng — lệnh dưới sẽ trả not.working, KHÔNG phải lỗi mã số. Dán mã số vào biến rồi chạy lại."; exit 1; }
curl -sS --max-time 30 "http://songnhue.bhh40.net/api/getmn.aspx?key=${HYDRO_API_KEY}"
```

| Kết quả | Nghĩa là | Xử lý |
|---|---|---|
| Nhiều dòng `F#####;dd/MM/yyyy;HH:mm;value=<cm>;` | Nguồn ổn → lỗi ở phía mình, mục 2 | |
| `not.working` | Sai mã số, **mất dấu `;` cuối**, hoặc biến rỗng | Đối chiếu mã số trong CSDL (`da_co_ma_so`), coi chừng CI/shell trim mất dấu `;` |
| Rỗng / hết hạn / 5xx | Nguồn đang chết | Mục 3 |
| Thiếu vài mã điểm đo | Trạm đó trục trặc | Mục 4 |

> ⚠ Một khả năng nữa, chỉ lộ ở nhật ký bảo mật chứ không ở log ứng dụng: **giải mã hỏng**. Bản mã và
> khoá AES hiện tại không khớp (khoá vừa xoay mà chưa mã hoá lại, hoặc CSDL khôi phục từ bản sao lưu
> cũ hơn lần xoay khoá). Triệu chứng ở phía ứng dụng giống hệt "nguồn không phản hồi". Kiểm:
> `SELECT * FROM security_events WHERE event_type = 'EXTERNAL_CREDENTIAL_DECRYPT_FAILED' ORDER BY occurred_at DESC LIMIT 5;`

## 2. Nguồn ổn, poller không chạy

```sql
-- Bản ghi thô gần nhất (append-only, ghi TRƯỚC khi parse)
SELECT max(created_at) FROM hydro_raw_logs;

-- Job poller
SELECT status, count(*), max(created_at) FROM jobs
 WHERE job_type LIKE 'HYDRO%' GROUP BY status;
```

- Có bản ghi thô mới nhưng bảng đã chuẩn hoá không có → **lỗi parser**, không phải lỗi mạng. Dữ liệu
  chưa mất (bản thô còn nguyên) — sửa parser rồi chạy lại từ `hydro_raw_logs`. Đây chính là lý do
  bản thô được ghi trước khi parse.
- Không có bản ghi thô nào → poller không chạy → [job-that-bai.md](job-that-bai.md).
- Kiểm `hydro.polling.cron` trong `settings`: phải là `45 1/2 * * * *` (2 phút/lần, phút lẻ, giây 45).

## 3. Nguồn đang chết

Không làm gì được ngoài chuẩn bị để không mất thêm:

1. Xác nhận poller vẫn chạy và vẫn thử — để giây phút nguồn sống lại là bắt được ngay.
2. Kiểm log không bị đầy vì retry dồn dập.
3. Báo Công ty để liên hệ đơn vị vận hành nguồn.
4. **Ghi lại khoảng thời gian mất dữ liệu.** Trên GIS, trạm mất tín hiệu hiển thị **màu xám** (chốt
   G3) — không phải màu bình thường, và cũng không phải cảnh báo ngưỡng.

## 4. Chỉ vài trạm im lặng

Nguồn trả rải rác trong cửa sổ `x1:30 → x8:30`, nên thiếu trong một lượt gọi là **bình thường**.
Thiếu liên tiếp qua `hydro.station.signal-loss-frames` khung (mặc định 3) mới là mất tín hiệu thật.

```sql
SELECT station_code, max(observed_at) AS gan_nhat, now() - max(observed_at) AS im_lang_bao_lau
  FROM hydro_measurements GROUP BY station_code ORDER BY 3 DESC;
```

⚠ Truy vấn báo cáo/cảnh báo phải lọc `quality = 'HOP_LE'` (`CLAUDE.md` quy tắc 14). Bản ghi
`NGHI_NGO` vẫn nằm trong bảng chính — quên lọc là sai số liệu, và sai một cách âm thầm.

## 5. Xác nhận đã xong

Grafana → *Độ tươi dữ liệu theo nguồn*: đường phải **về gần 0 và ở đó**. Về 0 rồi leo lên lại nghĩa
là poller chạy được một lượt rồi chết tiếp — tệ hơn chết hẳn, vì cảnh báo cứ bật tắt liên tục và sẽ
bị bỏ qua.
