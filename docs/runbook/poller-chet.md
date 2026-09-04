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

> **Trạng thái từ 02/09/2026 (WS-31 + T31.13)**: poller đã chạy, và `sync_logs` nay **có màn hình**
> — trước T31.13 nó chỉ tra được bằng SQL. `HydroFreshnessRegistrar` đăng ký nguồn
> `hydro-water-level` với `DataFreshnessRegistry` **ở lượt đầu tiên đọc được một mốc thật** — cố ý
> đăng ký muộn, để cảnh báo này không kêu suốt quãng chưa có dữ liệu. Vế còn lại — *"chưa TỪNG ingest
> được lần nào"* — do job `HYDRO_SIGNAL_LOSS` đo, bằng `sync_logs` chứ không bằng độ tươi dữ liệu.

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
| Có mã lạ chưa khai điểm đo | Bình thường — nguồn trả 28 mã, ta khai 19 | Số đo nằm ở `hydro_unmapped_readings`, chờ Công ty khai báo (G8). ⛔ Hệ thống **không** tự tạo điểm đo |

> ⚠ Một khả năng nữa, chỉ lộ ở nhật ký bảo mật chứ không ở log ứng dụng: **giải mã hỏng**. Bản mã và
> khoá AES hiện tại không khớp (khoá vừa xoay mà chưa mã hoá lại, hoặc CSDL khôi phục từ bản sao lưu
> cũ hơn lần xoay khoá). Triệu chứng ở phía ứng dụng giống hệt "nguồn không phản hồi". Kiểm:
> `SELECT * FROM security_events WHERE event_type = 'EXTERNAL_CREDENTIAL_DECRYPT_FAILED' ORDER BY occurred_at DESC LIMIT 5;`

## 2. Nguồn ổn, poller không chạy

⭐ **Bắt đầu ở màn hình *Dữ liệu thuỷ văn › Nhật ký đồng bộ*** (`/thuy-van/nhat-ky-dong-bo`, T31.13).
Nó đọc đúng bảng `sync_logs` dưới đây, nhưng nói thẳng **việc phải làm** cho từng lý do hỏng và có
dải tóm tắt 24 giờ ở đầu trang. Quyền: `hyd:measurement:view` **hoặc** `hyd:api-source:manage` —
TECHNICIAN và cán bộ Xí nghiệp đều mở được.

- Bật công tắc **“Chỉ lượt có vấn đề”** để bỏ qua `SKIPPED_UP_TO_DATE` — 4/5 lượt rơi vào đó và đó
  là điều **đúng**.
- Ô **“Lượt gần nhất”** rỗng ⇒ **không có lượt polling nào trong 24 giờ** — triệu chứng nặng hơn mọi
  con số lỗi, đi thẳng xuống mục 2b.

⚠ **Khi ứng dụng không lên được thì mới dùng SQL** — và đó chính là lúc runbook này cần nhất. Câu
truy vấn dưới đây trả cùng dữ liệu màn hình hiển thị:

```sql
SELECT s.started_at, a.code, s.status, s.failure_kind, s.frame_start,
       s.received_count, s.written_count, s.skipped_count, s.unmapped_count
  FROM sync_logs s JOIN api_sources a ON a.id = s.api_source_id
 ORDER BY s.started_at DESC LIMIT 20;
```

| `status` | Nghĩa là | Xử lý |
|---|---|---|
| `SUCCESS` · `written_count = 0` | ✅ **Bình thường** — poll 2′ trên nguồn 10′ nên 4/5 lượt trả dữ liệu trùng | Không làm gì |
| `SKIPPED_UP_TO_DATE` | ✅ **Bình thường** — toàn bộ điểm đo đã có bản ghi của khung hiện tại | Không làm gì |
| `PARTIAL` | Nguồn trả **dưới 50%** số điểm đo đang hoạt động | Thường là nguồn đang đẩy dở dữ liệu của khung. Kéo dài nhiều khung mới là sự cố → mục 4 |
| `FAILED` | Đọc `failure_kind` — **năm giá trị, năm việc phải làm khác nhau** | Xem bảng ở mục 1 |
| **không có dòng nào** | Poller không chạy | Đọc tiếp dưới đây |

```sql
-- Bản ghi thô gần nhất (append-only, ghi TRƯỚC khi parse). ⚠ Cột là `fetched_at`.
SELECT max(fetched_at) FROM hydro_raw_logs;

-- Hàng đợi việc nền của MOD-03
SELECT job_type, status, count(*), max(created_at) FROM jobs
 WHERE job_type LIKE 'HYDRO%' GROUP BY job_type, status;
```

- `HYDRO_POLL` có dòng `FAILED` → lượt gọi **đã xảy ra** rồi hỏng (SYS-0006). Lý do cụ thể nằm ở
  `sync_logs.failure_kind`, không nằm ở `jobs.error`.
- ⚠ **`HYDRO_POLL` không có dòng `FAILED` KHÔNG có nghĩa là mọi thứ ổn**: thiếu mã số (`THIEU_MA_SO`)
  cố ý **không** làm job đỏ — 720 job FAILED mỗi ngày là một màn hình không ai đọc. Nó chỉ hiện ở
  `sync_logs` và trên màn hình *Nguồn dữ liệu*.
- ⚠ **Job polling chỉ thử MỘT lần** (`max_attempts = 1`), và đó là chủ ý: backoff của worker là
  1′/5′/15′ còn lượt polling kế tiếp chỉ cách 2 phút — **lượt kế tiếp chính là lượt thử lại**.
- Có bản ghi thô mới nhưng `hydro_readings` không có → **lỗi parser hoặc lỗi ánh xạ**, không phải lỗi
  mạng. Dữ liệu chưa mất (bản thô còn nguyên) — xem cột **“Mã lạ”** trên màn hình nhật ký, hoặc
  `sync_logs.unmapped_count`: khác 0 nghĩa là nguồn trả mã **chưa khai điểm đo**, và số đo nằm ở
  `hydro_unmapped_readings`. Danh sách đầy đủ ở màn hình *Dữ liệu thuỷ văn › Mã lạ từ nguồn*
  (`/thuy-van/ma-la`) — kèm số bản ghi đã tích, mốc gần nhất và nút *Khai thành điểm đo*.
  ⚠ Giá trị hiện ở màn hình ấy là số **nguyên văn nguồn (cm)**, ⛔ chưa quy đổi sang mét.
- Không có bản ghi thô nào → poller không chạy → [job-that-bai.md](job-that-bai.md).
- Kiểm `hydro.polling.cron` trong `settings`: mặc định `45 1/2 * * * *` (2 phút/lần, phút lẻ, giây 45).
  ⚠ **Đổi trên màn hình *Cấu hình hệ thống* có hiệu lực trong ≤10 giây, không cần khởi động lại** —
  poller có một nhịp tim 10 giây và tự chấm cron mỗi nhịp. ⛔ Đổi bằng `UPDATE settings` thẳng thì
  **không** có tác dụng cho tới khi restart: không sinh `SettingChangedEvent` nên đệm Caffeine vẫn
  phục vụ giá trị cũ.
- ⚠ Cron **riêng của từng nguồn** (`api_sources.cron`) thắng cron chung. Màn hình *Nguồn dữ liệu* hiện
  nhãn `tham số chung` / `riêng` ở cột "Đang chạy theo" — đọc nó trước khi sửa khoá `settings`.

### 2b. Tiến trình có đang chạy đúng bản build không

⚠ Ba lần dự án hỏi câu này và ba lần câu trả lời là **không** (§10.53 · §10.56 · §10.67). Poller là
daemon — nó không có ai bấm F5. Lúc khởi động nó in:

```
Poller thuỷ văn sẵn sàng — nhịp tim 10000 ms · vân tay mã: HydroPollScheduler=… · TelemetryIngestService=… · Bhh40Parser=…
```

Đối chiếu bằng cách băm chính tệp `.class` trong image:

```bash
docker compose exec app sh -c 'unzip -p /app/app.jar BOOT-INF/classes/com/songnhue/hydro/infra/Bhh40Parser.class | sha256sum | cut -c1-16'
```

Khác dòng log ⇒ **tiến trình đang chạy mã khác bản vừa triển khai**.

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
-- ⭐ Đọc `hydro_latest`, KHÔNG quét `hydro_readings` (quy tắc 8). `last_seen_at` trả lời "trạm còn
--   phát tín hiệu không" — cố ý BẤT KỂ chất lượng, vì một trạm chỉ trả số nghi ngờ VẪN đang phát.
SELECT s.code, s.name, s.active,
       max(l.last_seen_at) AS gan_nhat,
       now() - max(l.last_seen_at) AS im_lang_bao_lau
  FROM stations s LEFT JOIN hydro_latest l ON l.station_id = s.id
 WHERE s.deleted_at IS NULL
 GROUP BY s.id, s.code, s.name, s.active
 ORDER BY 4 NULLS FIRST;
```

- `gan_nhat` **NULL** ⇒ trạm **chưa từng** có bản ghi nào — ⛔ khác hẳn "mất tín hiệu". Một điểm đo
  vừa seed mà chưa tới lượt polling đầu tiên không phải một trạm hỏng.
- `im_lang_bao_lau` vượt `hydro.station.signal-loss-frames` × khung (mặc định 3 × 10′ = 30′) ⇒ mất tín
  hiệu. Job `HYDRO_SIGNAL_LOSS` (5 phút/lần) tự phát hiện và gửi **một thông báo gộp** cho người có
  quyền `hyd:station:manage` — ⚠ chỉ ở **lượt chuyển trạng thái**, không phát lại mỗi 5 phút.
- ⛔ Trạng thái hiển thị của điểm đo **không có cột nào trong CSDL** — nó suy ra lúc đọc. Đó là lý do
  hệ nói đúng khi poller chết: không ai ghi thì cũng không có trạng thái cũ để tin nhầm.

⚠ Truy vấn báo cáo/cảnh báo phải lọc `quality = 'HOP_LE'` (`CLAUDE.md` quy tắc 14). Bản ghi
`NGHI_NGO` vẫn nằm trong bảng chính — quên lọc là sai số liệu, và sai một cách âm thầm.
⭐ Ở tầng lược đồ, `hydro_latest` đã tách sẵn `valid_value` (giá trị **HỢP LỆ** gần nhất) khỏi
`last_seen_at`: widget cổng và lớp GIS đọc `valid_value` nên **không có cách nào** hiện nhầm một số
đang bị nghi ngờ.
✅ **Từ 02/09/2026 (WS-32) bộ phân loại đã chạy** — bộ lọc trên không còn là một luật chưa được thử.
Bộ canh `QualityFilterGuardTest` đọc **mã thật** và bắt mọi truy vấn đọc `hydro_readings` thiếu
`quality = 'HOP_LE'`; ngoại lệ phải khai **có tên** trong chính bộ canh ấy.

⚠ **Có thêm trạng thái thứ ba: `XOA`** (xoá mềm, người duyệt bấm kèm lý do bắt buộc). Nó ⛔ không
phải mức chất lượng thứ ba — bộ lọc `= 'HOP_LE'` loại nó ra **miễn phí**, nên ⛔ đừng thêm vế
`AND deleted_at IS NULL` ở đâu cả.

### 4-b. Hàng chờ duyệt rỗng — ba nghĩa khác nhau

Màn hình **Thuỷ văn › Dữ liệu nghi ngờ** (`/thuy-van/du-lieu-nghi-ngo`) tự nói ra nghĩa nào:

| Nhìn thấy | Nghĩa | Việc phải làm |
|---|---|---|
| Dải xanh *"bộ phân loại đang chạy…"* | Không có gì đáng ngờ | ✅ Không phải làm gì |
| Dải vàng *"chưa cấu hình quy tắc"* | ⛔ Bộ phân loại **đang tắt** — không bản ghi nào bị đánh dấu | Khai khoảng vật lý ở **Cấu hình hệ thống › HYDRO**, khoá `hydro.quality.suspect-rule` |
| Dải đỏ *"quy tắc đang HỎNG"* | JSON không đọc được ⇒ **mọi số đo mới ghi là Hợp lệ mà không qua kiểm tra nào** | Sửa JSON; log ứng dụng có dòng `ERROR` kèm nguyên nhân |

⛔ Một bảng rỗng ⛔ **không** đồng nghĩa dữ liệu sạch — quy tắc 16: *số 0 là một câu khẳng định*.

```sql
-- Đối chiếu nhanh khi ứng dụng đang chết
SELECT quality, count(*) FROM hydro_readings
 WHERE measured_at > now() - interval '24 hours' GROUP BY quality;
SELECT setting_value FROM settings WHERE setting_key = 'hydro.quality.suspect-rule';
```

## 5. Xác nhận đã xong

Grafana → *Độ tươi dữ liệu theo nguồn*: đường phải **về gần 0 và ở đó**. Về 0 rồi leo lên lại nghĩa
là poller chạy được một lượt rồi chết tiếp — tệ hơn chết hẳn, vì cảnh báo cứ bật tắt liên tục và sẽ
bị bỏ qua.
