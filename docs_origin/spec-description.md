# SPEC – MOD-03: QUẢN LÝ DỮ LIỆU THỦY VĂN (MỰC NƯỚC – LƯỢNG MƯA)

| | |
|---|---|
| **Mã module** | MOD-03 |
| **Phiên bản** | 1.0 |
| **Ngày** | 01/09/2026 |
| **Tham chiếu** | SRS ver 06.8.2026 – M3.1 → M3.18 · Yêu cầu chỉnh sửa website v1.0 – CR-13, CR-14, CR-33 → CR-37, CR-38 |
| **Phạm vi** | Thu thập dữ liệu từ hệ thống quan trắc, lưu vào CSDL, hiển thị trên trang chủ và trang chi tiết `/quan-ly-van-hanh/muc-nuoc-luong-mua` |

---

## 1. TỔNG QUAN LUỒNG DỮ LIỆU

```
Hệ thống quan trắc BHH4.0 (songnhue.bhh40.net)
        │  HTTP GET, mỗi mã điểm đo 1 lần
        ▼
[1] Job đồng bộ (server-side, có lịch)
        │  bóc tách → chuẩn hóa đơn vị → kiểm tra hợp lệ → gắn cờ chất lượng
        ▼
[2] CSDL nội bộ (bảng danh mục + bảng số liệu + bảng tổng hợp)
        │
        ├──▶ [3] API nội bộ (REST, JSON)
        │            │
        │            ├──▶ [4a] Trang chủ – khối "Mực nước, lượng mưa"  → PUBLIC
        │            └──▶ [4b] Trang chi tiết + biểu đồ                → PUBLIC (rút gọn) / ĐĂNG NHẬP (đầy đủ)
        │
        └──▶ [5] Nhật ký đồng bộ + cảnh báo vượt ngưỡng
```

**Nguyên tắc bắt buộc:** frontend KHÔNG bao giờ gọi thẳng sang `songnhue.bhh40.net`. Mọi dữ liệu hiển thị đều đọc từ CSDL nội bộ qua API nội bộ. Lý do: nguồn có token trong URL (mục 2.2), và cần lớp đệm khi nguồn gián đoạn.

---

## 2. NGUỒN DỮ LIỆU

### 2.1. Định dạng URL

```
http://songnhue.bhh40.net/default.aspx
    ?user=<TOKEN>
    &pro=xemchitiet.<MA_DIEM_DO>
    &mode=view
    &id1=0
    &id2=0
```

Ví dụ: `...&pro=xemchitiet.F01771&mode=view&id1=0&id2=0`

| Tham số | Ý nghĩa | Ghi chú |
|---|---|---|
| `user` | Token định danh tài khoản truy cập | **Bí mật** – chỉ lưu ở biến môi trường server |
| `pro` | `xemchitiet.` + mã điểm đo | Mã điểm đo dạng `F` + 5 chữ số (VD: `F01519`, `F01771`) |
| `mode` | `view` | Cố định |
| `id1`, `id2` | Tham số phân trang/khoảng thời gian | **Cần dev khảo sát** – xem mục 11, OI-A |

### 2.2. Yêu cầu bảo mật

- Token `user` lưu trong biến môi trường (`HYDRO_SOURCE_TOKEN`), **không** commit vào mã nguồn, **không** để lộ ra HTML/JS phía client.
- Job đồng bộ chạy server-side. Không proxy URL nguồn ra ngoài dưới bất kỳ hình thức nào.
- Ghi log mọi lần gọi nguồn (thời gian, mã điểm đo, HTTP status, số bản ghi) nhưng **che token** trong log.

### 2.3. Đơn vị đo – BẮT BUỘC CHUYỂN ĐỔI

Hệ thống nguồn trả **giá trị nguyên, đơn vị centimet (cm)**. Bằng chứng đối chiếu:

| Điểm đo | Giá trị nguồn | Ngưỡng BĐ1 khai báo | Kết luận |
|---|---|---|---|
| Cống Đồng Quan – TL | 232 – 236 | +4.00 m | 232 cm = 2,32 m |
| TV Hà Nội | 140 – 145 | +9.50 m | 143 cm = 1,43 m |
| TV Ba Thá | 439 – 441 | +5.50 m | 441 cm = 4,41 m |

→ Khi lưu vào CSDL: **chia 100, lưu kiểu `DECIMAL(6,2)` đơn vị mét**. Nếu bỏ qua bước này, toàn bộ so sánh ngưỡng báo động sẽ sai 100 lần.

Lượng mưa: đơn vị **milimet (mm)**, giữ nguyên, kiểu `DECIMAL(6,1)`.

### 2.4. Tần suất đo tại nguồn

| Loại | Mốc đo | Ghi chú |
|---|---|---|
| Mực nước | **10 phút/lần** | Trang nguồn có chế độ xem: phút · giờ (7h, 19h, 7/19h, 1/7/13/19h, 1/3/5…/23h) · ngày |
| Lượng mưa | **1 giờ/lần** | Kèm tổng hợp ngày và các mốc 3 / 5 / 10 / 30 ngày |

---

## 3. MÔ HÌNH DỮ LIỆU

### 3.1. `hydro_station` – Danh mục điểm đo

Bảng master. Nạp một lần, cập nhật khi có thay đổi.

| Trường | Kiểu | Bắt buộc | Nguồn | Ghi chú |
|---|---|---|---|---|
| `id` | BIGINT PK | x | tự sinh | |
| `station_code` | VARCHAR(10) | x | Hệ quan trắc | Mã điểm đo, VD `F01519`. **UNIQUE** |
| `structure_code` | VARCHAR(20) | x | Công ty | Mã công trình nội bộ |
| `structure_name` | NVARCHAR(150) | x | Hệ quan trắc | Tên cống / trạm bơm |
| `station_type` | ENUM | x | | `WATER_LEVEL` \| `RAINFALL` |
| `indicator` | ENUM | x | Hệ quan trắc | `TL` (thượng lưu) \| `HL` (hạ lưu) \| `BE_HUT` \| `SONG_HONG` \| `RAIN` |
| `river_route` | NVARCHAR(100) | | **Công ty cung cấp** | Tuyến sông – *không có trong nguồn* |
| `chainage` | VARCHAR(30) | | **Công ty cung cấp** | Lý trình – *không có trong nguồn* |
| `longitude` | DECIMAL(10,7) | | **Cần xác nhận** | Kinh độ – xem OI-B |
| `latitude` | DECIMAL(10,7) | | **Cần xác nhận** | Vĩ độ – xem OI-B |
| `enterprise_id` | BIGINT FK | | Công ty | Xí nghiệp quản lý |
| `is_public` | BOOLEAN | x | Công ty | Có hiển thị cho khách vãng lai không |
| `is_main_axis` | BOOLEAN | x | Công ty | Thuộc nhóm cống trên trục chính (hiển thị trang chủ) |
| `display_order` | INT | x | Công ty | Thứ tự hiển thị theo tuyến sông |
| `alarm_1` / `alarm_2` / `alarm_3` | DECIMAL(6,2) | | Công ty | Ngưỡng BĐ1/BĐ2/BĐ3 (m) – hiện mới có 4/29 điểm |
| `value_min` / `value_max` | DECIMAL(6,2) | | Công ty | Dải giá trị vật lý hợp lệ, phục vụ kiểm tra mục 4.3 |
| `is_active` | BOOLEAN | x | | Tắt điểm đo hỏng mà không xóa dữ liệu lịch sử |

### 3.2. `wl_reading` – Số liệu mực nước

Mỗi bản ghi = **một công trình tại một mốc thời gian**, gộp sẵn cặp thượng lưu / hạ lưu theo đúng yêu cầu nghiệp vụ.

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `id` | BIGINT PK | x | |
| `structure_code` | VARCHAR(20) | x | Mã cống |
| `structure_name` | NVARCHAR(150) | x | Tên cống / trạm bơm |
| `longitude` | DECIMAL(10,7) | | Kinh độ |
| `latitude` | DECIMAL(10,7) | | Vĩ độ |
| `measure_date` | DATE | x | Ngày đo |
| `measure_time` | TIME | x | Giờ đo (mốc 10 phút) |
| `upstream_m` | DECIMAL(6,2) | | Mực nước thượng lưu (m) |
| `downstream_m` | DECIMAL(6,2) | | Mực nước hạ lưu (m) |
| `delta_m` | DECIMAL(6,2) | | **Chênh lệch = `upstream_m` − `downstream_m`**, tính khi ghi |
| `quality` | ENUM | x | `GOOD` \| `SUSPECT` \| `MISSING` – xem mục 4.3 |
| `src_code_tl` | VARCHAR(10) | | Mã điểm đo nguồn của thượng lưu |
| `src_code_hl` | VARCHAR(10) | | Mã điểm đo nguồn của hạ lưu |
| `synced_at` | DATETIME | x | Thời điểm job ghi bản ghi |

**Ràng buộc:** `UNIQUE (structure_code, measure_date, measure_time)` – chống trùng mốc đo.
**Index:** `(structure_code, measure_date, measure_time DESC)` phục vụ truy vấn biểu đồ.

**Lưu ý xử lý:**
- Công trình chỉ có một chỉ tiêu (VD: TB Yên Nghĩa – MN Bể hút; TB Hồng Vân – MN sông Hồng) → ghi vào `upstream_m`, để `downstream_m` và `delta_m` NULL.
- `delta_m` chỉ tính khi **cả hai** giá trị khác NULL. Không suy diễn, không gán 0.

### 3.3. `rain_reading` – Số liệu lượng mưa theo giờ

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `id` | BIGINT PK | x | |
| `structure_code` | VARCHAR(20) | x | |
| `structure_name` | NVARCHAR(150) | x | |
| `longitude` / `latitude` | DECIMAL(10,7) | | |
| `measure_date` | DATE | x | Ngày đo |
| `measure_hour` | TINYINT | x | Giờ đo, 0–23 |
| `rainfall_mm` | DECIMAL(6,1) | | Lượng mưa trong giờ đó (mm) |
| `quality` | ENUM | x | `GOOD` \| `SUSPECT` \| `MISSING` |
| `src_code` | VARCHAR(10) | | Mã điểm đo nguồn |
| `synced_at` | DATETIME | x | |

**Ràng buộc:** `UNIQUE (structure_code, measure_date, measure_hour)`

### 3.4. `rain_daily` – Tổng hợp mưa theo ngày

Bảng tính sẵn, chạy lại sau mỗi lần đồng bộ. Phục vụ bảng ở mục 6 và các mốc 3/5/10/30 ngày mà không phải cộng lại từ đầu.

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `structure_code` | VARCHAR(20) | |
| `measure_date` | DATE | Ngày quan trắc |
| `rain_day_mm` | DECIMAL(7,1) | **Mưa ban ngày**: tổng từ **07:00 đến 19:00 cùng ngày** |
| `rain_night_mm` | DECIMAL(7,1) | **Mưa ban đêm**: tổng từ **19:00 ngày N đến 07:00 ngày N+1** |
| `rain_total_mm` | DECIMAL(7,1) | **Tổng ngày** = `rain_day_mm` + `rain_night_mm` |
| `hours_missing` | TINYINT | Số giờ thiếu dữ liệu trong ngày |
| `quality` | ENUM | `GOOD` nếu `hours_missing` = 0, ngược lại `PARTIAL` |

**Ràng buộc:** `UNIQUE (structure_code, measure_date)`

> **Lưu ý về mốc ban đêm:** khoảng 19h–7h **vắt qua hai ngày lịch**. Bản ghi mưa đêm của ngày N chỉ chốt được sau 07:00 ngày N+1. Trước thời điểm đó phải hiển thị là số liệu tạm và đánh dấu `PARTIAL`, không được hiển thị như đã chốt.

### 3.5. `sync_log` – Nhật ký đồng bộ

`id` · `run_at` · `station_code` · `http_status` · `records_ok` · `records_rejected` · `duration_ms` · `error_message`

Phục vụ M3.16 và giúp đối soát khi số liệu bất thường.

---

## 4. JOB ĐỒNG BỘ

### 4.1. Lịch chạy

| Loại | Chu kỳ mặc định | Ghi chú |
|---|---|---|
| Mực nước | 10 phút | Bám theo tần suất đo tại nguồn |
| Lượng mưa | 15 phút | Nguồn cập nhật theo giờ, chạy dày hơn để bắt kịp |
| Tính lại `rain_daily` | Sau mỗi lần đồng bộ mưa | Chỉ tính lại ngày hiện tại và ngày liền trước |

**Bắt buộc:** chu kỳ, timeout, số lần thử lại là **tham số cấu hình trong CSDL hoặc file config**, không hard-code (CR-37, M3.3).

### 4.2. Các bước xử lý

1. Đọc danh sách điểm đo `is_active = true` từ `hydro_station`.
2. Với từng mã, gọi URL nguồn theo định dạng mục 2.1.
3. Bóc tách bảng số liệu trong HTML trả về.
4. Chuyển đơn vị: mực nước **chia 100** → mét (mục 2.3).
5. Chạy bộ kiểm tra hợp lệ (mục 4.3), gắn cờ `quality`.
6. Ghép cặp thượng lưu / hạ lưu theo `structure_code` + mốc thời gian, tính `delta_m`.
7. `UPSERT` vào `wl_reading` / `rain_reading` theo khóa unique — chạy lại nhiều lần không sinh bản ghi trùng.
8. Ghi `sync_log`.
9. So sánh với `alarm_1/2/3`, sinh cảnh báo nếu vượt ngưỡng (M3.13, M3.14).

### 4.3. Quy tắc kiểm tra chất lượng

| # | Điều kiện | Kết quả | Căn cứ thực tế |
|---|---|---|---|
| Q1 | Giá trị nằm ngoài `[value_min, value_max]` | `SUSPECT`, **không** hiển thị công khai, vẫn lưu để đối soát | |
| Q2 | Biến thiên giữa hai mốc liên tiếp (10 phút) **> 50 cm** | `SUSPECT` | Cống Lương Cổ – TL nhảy 194 → 450 (2,56 m/10 phút) ngày 01/09/2026 |
| Q3 | Không có số liệu quá **30 phút** (mực nước) hoặc **2 giờ** (mưa) | `MISSING` | Cống Lương Cổ – HL rỗng hoàn toàn |
| Q4 | `downstream_m` > `upstream_m` quá **0,5 m** | `GOOD` nhưng **ghi cảnh báo đối soát** cho quản trị viên | Cống tiêu tự chảy Yên Nghĩa (2,75 / 4,96); Cống Vân Đình (2,51 / 3,64) – nghi khác cao độ chuẩn |
| Q5 | Giá trị âm hoặc không phải số | Loại bỏ bản ghi, ghi log | |

Bản ghi `SUSPECT` và `MISSING` **không được hiển thị như số liệu bình thường** trên giao diện công khai — hiển thị theo mục 8.

---

## 5. TRANG CHỦ – KHỐI "MỰC NƯỚC, LƯỢNG MƯA"

### 5.1. Bố cục khối

```
┌────────────────────────────────────────────────────────────────────┐
│ MỰC NƯỚC, LƯỢNG MƯA                                                │
│ Biểu tổng hợp theo tuyến sông — mỗi công trình một cặp             │
│ thượng lưu và hạ lưu                                               │
│ Trực ban PCTT 24/7: (024) 33.546.247          [Xem chi tiết ➔]     │
├────────────────────────────────────────────────────────────────────┤
│  <BẢNG SỐ LIỆU – mục 5.2>                                          │
├────────────────────────────────────────────────────────────────────┤
│ Cập nhật lúc: 10:50 01/09/2026                        [ Làm mới ]  │
└────────────────────────────────────────────────────────────────────┘
```

| Thành phần | Nội dung / hành vi |
|---|---|
| Tiêu đề | `MỰC NƯỚC, LƯỢNG MƯA` |
| Phụ đề | `Biểu tổng hợp theo tuyến sông — mỗi công trình một cặp thượng lưu và hạ lưu` |
| Hotline | `Trực ban PCTT 24/7:` + số `(024) 33.546.247`, thẻ `<a href="tel:02433546247">` |
| Nút "Xem chi tiết ➔" | Điều hướng `/quan-ly-van-hanh/muc-nuoc-luong-mua` |
| Dòng cập nhật | `Cập nhật lúc: HH:mm dd/MM/yyyy` – lấy từ `MAX(synced_at)`, **không** phải giờ máy client |
| Nút "Làm mới" | Gọi lại API nội bộ. Chống spam: khóa nút 30 giây sau mỗi lần bấm |
| Tự động làm mới | Theo chu kỳ cấu hình, mặc định 10 phút |

### 5.2. Cấu trúc bảng

| Cột | Nguồn dữ liệu | Định dạng |
|---|---|---|
| Tuyến sông | `hydro_station.river_route` | Gộp ô theo nhóm tuyến |
| Công trình / điểm đo | `wl_reading.structure_name` | |
| Lý trình | `hydro_station.chainage` | |
| Mực nước thượng lưu (m) | `wl_reading.upstream_m` | 2 chữ số thập phân, VD `2.32` |
| Mực nước hạ lưu (m) | `wl_reading.downstream_m` | 2 chữ số thập phân |
| Lượng mưa (mm) | `rain_reading.rainfall_mm` giờ gần nhất | 1 chữ số thập phân; điểm không có trạm mưa hiển thị `–` |
| Thời điểm đo | `measure_date` + `measure_time` | `HH:mm dd/MM/yyyy` |
| Chất lượng | `wl_reading.quality` | Nhãn màu: `Tốt` (xanh) · `Nghi ngờ` (vàng) · `Không có dữ liệu` (xám) |

**Phạm vi dữ liệu công khai:** chỉ các công trình có `is_main_axis = true`, số liệu tại **mốc đo gần nhất** so với giờ truy cập. Danh sách cụ thể xem OI-C.

**Sắp xếp:** theo `river_route`, rồi `display_order` (thứ tự từ thượng nguồn xuống hạ nguồn).

### 5.3. Cảnh báo trực quan

Ô mực nước được tô nền theo ngưỡng của chính điểm đo đó:

| Điều kiện | Hiển thị |
|---|---|
| `value` < `alarm_1` | Bình thường – nền trắng |
| `alarm_1` ≤ `value` < `alarm_2` | **BĐ1** – nền vàng |
| `alarm_2` ≤ `value` < `alarm_3` | **BĐ2** – nền cam |
| `value` ≥ `alarm_3` | **BĐ3** – nền đỏ, chữ trắng |
| Điểm đo chưa khai báo ngưỡng | Không tô màu, **không** dùng ngưỡng của điểm khác |

Hiện mới có 4 điểm đo được khai báo ngưỡng (Cống Đồng Quan – TL, TV Hà Nội, An Cảnh, TV Ba Thá). 25 điểm còn lại chờ Công ty bổ sung — xem OI-D.

---

## 6. TRANG CHI TIẾT `/quan-ly-van-hanh/muc-nuoc-luong-mua`

Trang có **2 tab: Mực nước** và **Lượng mưa**.

### 6.1. Tab Mực nước

#### 6.1.1. Thanh chọn mốc thời gian

Đặt trên bảng, giữ đúng các chế độ xem của hệ quan trắc nguồn:

`Xem theo phút` · `Xem theo giờ` »» `[7h]` `[19h]` `[7/19h]` `[1/7/13/19h]` `[1/3/5/../23h]` · `Xem theo ngày`

| Chế độ | Mốc hiển thị | Số cột |
|---|---|---|
| **Xem theo phút** (mặc định) | Mốc **10 phút/lần** của ngày hiện tại | 12 cột gần nhất (≈ 2 giờ), cuộn ngang để xem lùi về đầu ngày |
| Xem theo giờ | Giá trị tại đầu mỗi giờ | 24 cột |
| `[7h]` / `[19h]` | Chỉ mốc 7h hoặc 19h | 1 cột/ngày |
| `[7/19h]` | 2 mốc/ngày | 2 cột/ngày |
| `[1/7/13/19h]` | 4 mốc/ngày | 4 cột/ngày |
| `[1/3/5/../23h]` | Mốc lẻ, 2 giờ/lần | 12 cột/ngày |
| Xem theo ngày | Giá trị đại diện ngày (mốc 7h) | 1 cột/ngày |

Kèm ô chọn **Ngày** (mặc định = ngày hiện tại) và ô chọn **Công trình** (chọn nhiều, mặc định tất cả).

> Một ngày có **144 mốc đo 10 phút** — không được đổ hết 144 cột ra bảng. Mặc định hiển thị cửa sổ 12 mốc gần nhất và cho cuộn ngang; số cột mặc định là tham số cấu hình.

#### 6.1.2. Bảng tổng hợp mực nước

Tiêu đề 2 tầng, cột thời gian xếp **mới nhất bên trái**:

| Số TT | Mã công trình | Tên công trình | Chỉ tiêu | 01/09/2026 |||||| Chi tiết |
|---|---|---|---|---|---|---|---|---|---|---|
| | | | | **10h** | **50'** | **40'** | **30'** | **20'** | **10'** | |
| 1 | SN-LM-01 | Cống Liên Mạc | Thượng lưu | 1.61 | 1.61 | 1.61 | 1.61 | 1.61 | 1.61 | »» |
| | | | Hạ lưu | 1.57 | 1.57 | 1.57 | 1.57 | 1.57 | 1.57 | |
| | | | *Chênh lệch* | *0.04* | *0.04* | *0.04* | *0.04* | *0.04* | *0.04* | |
| 2 | SN-LM-02 | Cống Liên Mạc 2 | Hạ lưu | 1.61 | 1.61 | 1.61 | 1.61 | 1.61 | 1.62 | »» |
| 3 | SN-YN-TB | TB Yên Nghĩa | MN Bể hút | 2.73 | 2.74 | 2.74 | 2.74 | 2.74 | 2.74 | »» |
| … | | | | | | | | | | |

**Đặc tả cột:**

| Cột | Nguồn | Định dạng |
|---|---|---|
| Số TT | Số thứ tự theo `display_order` | Gộp ô theo công trình |
| Mã công trình | `hydro_station.structure_code` | Gộp ô theo công trình |
| Tên công trình | `wl_reading.structure_name` | Gộp ô theo công trình |
| Chỉ tiêu | `hydro_station.indicator` | `Thượng lưu` \| `Hạ lưu` \| `Chênh lệch` \| `MN Bể hút` \| `MN sông Hồng` \| `MN` |
| Các cột thời gian | `wl_reading.upstream_m` / `downstream_m` / `delta_m` | **Đơn vị mét, 2 chữ số thập phân** |
| Chi tiết `»»` | | Mở màn hình chi tiết công trình – mục 6.1.3 |

**Quy tắc dựng dòng:**

- Mỗi công trình chiếm **2 đến 3 dòng**: `Thượng lưu` → `Hạ lưu` → `Chênh lệch`.
- Dòng **Chênh lệch** là dòng **tự tính**, in nghiêng, nền xám nhạt để phân biệt với số liệu đo. Chỉ hiện khi công trình có **đủ cả** thượng lưu và hạ lưu.
- Công trình chỉ có một chỉ tiêu (TB Yên Nghĩa, TB Ngoại Độ II, TB Hồng Vân, TB Thụy Phú II, Cống Tắc Giang, TV Hà Nội, An Cảnh, TV Ba Thá, Cống Liên Mạc 2, Cống 6 cửa Hòa Bình, Cống xả tiêu TB Vĩnh Mộ 2) chỉ chiếm **1 dòng**, không có dòng Chênh lệch.
- 3 cột đầu (Số TT, Mã công trình, Tên công trình) **cố định khi cuộn ngang** (sticky column).
- Ô có `quality = SUSPECT` in màu vàng kèm dấu `⚠` và tooltip lý do; ô `MISSING` để trống, tooltip "Không có dữ liệu". Không hiển thị `0.00` cho ô thiếu dữ liệu.
- Tô màu ngưỡng báo động theo mục 5.3, áp cho dòng Thượng lưu / Hạ lưu; **không** áp cho dòng Chênh lệch.

#### 6.1.3. Màn hình chi tiết một công trình (nút `»»`)

Mở trang riêng hoặc modal toàn màn hình, gồm 3 phần:

**(a) Thông tin công trình** — Mã · Tên · Tuyến sông · Lý trình · Xí nghiệp quản lý · Tọa độ (kèm link Google Maps) · Ngưỡng BĐ1/BĐ2/BĐ3 nếu có.

**(b) Biểu đồ diễn biến mực nước** — đặc tả tại mục 7.1. Đây là nội dung chính của màn hình.

**(c) Bảng số liệu chi tiết** — mỗi dòng một mốc 10 phút:

| Thời điểm | Thượng lưu (m) | Hạ lưu (m) | Chênh lệch (m) | Chất lượng |
|---|---|---|---|---|
| 10:00 01/09/2026 | 1.61 | 1.57 | 0.04 | Tốt |
| 09:50 01/09/2026 | 1.61 | 1.57 | 0.04 | Tốt |

Có phân trang hoặc cuộn vô hạn; nút xuất CSV / Excel.

**Bộ lọc trên màn hình chi tiết:** Ngày · Tuần · Tháng · Khoảng tùy chọn (từ ngày – đến ngày).

> **Phân quyền:** biểu đồ (b), khoảng thời gian Tuần / Tháng / tùy chọn ở (c), và chức năng xuất tệp **yêu cầu đăng nhập**. Khách vãng lai mở `»»` chỉ xem được (a) và bảng (c) của **ngày hiện tại**; phần biểu đồ hiển thị khung mờ kèm nút `Đăng nhập để xem biểu đồ diễn biến`.

### 6.2. Tab Lượng mưa

**Thanh mốc thời gian** (đặt trên bảng, dạng tab ngang):

`Mưa hiện tại` · `Mưa 3 ngày` · `Mưa 5 ngày` · `Mưa 10 ngày` · `Mưa 30 ngày`

**Bảng tổng hợp mưa** – cấu trúc tiêu đề 2 tầng:

| Số TT | Công trình (hoặc điểm đo) | Chỉ tiêu | Mưa ngày dd/MM/yyyy ||| Mưa theo giờ |||| Chi tiết |
|---|---|---|---|---|---|---|---|---|---|---|
| | | | **Tổng (7h+19h)** | **19h (7h–19h)** | **7h (19h–7h)** | **0h** | **1h** | … | **23h** | |
| 1 | Cống Liên Mạc | Lượng mưa | | | | | | | | »» |
| 2 | Trạm bơm Cầu Giát | Lượng mưa | | | | | | | | »» |
| … | … | | | | | | | | | |
| 15 | Trạm bơm Hồng Vân | Lượng mưa | | | | | | | | »» |

Ánh xạ 3 cột tổng hợp ngày:

| Nhãn cột | Ý nghĩa | Trường CSDL |
|---|---|---|
| `Tổng (7h+19h)` | Tổng lượng mưa cả ngày | `rain_daily.rain_total_mm` |
| `19h (7h-19h)` | Lượng mưa **ban ngày**, tích lũy 07:00 → 19:00 | `rain_daily.rain_day_mm` |
| `7h (19h-7h)` | Lượng mưa **ban đêm**, tích lũy 19:00 hôm trước → 07:00 | `rain_daily.rain_night_mm` |

**Cột "Mưa theo giờ": đủ 24 cột, từ 0h đến 23h.** Bảng ngang rộng → đặt trong vùng cuộn ngang riêng (`overflow-x: auto`), giữ cố định 3 cột đầu (Số TT, Công trình, Chỉ tiêu) khi cuộn.

**Quy ước hiển thị ô rỗng:**

| Trạng thái | Hiển thị |
|---|---|
| Không mưa (giá trị 0) | `–` |
| Chưa tới mốc đo | ô trống |
| Mất dữ liệu | `·` kèm tooltip "Không có dữ liệu" |

Không được hiển thị `0.0` cho ô mất dữ liệu — hai trạng thái này khác nhau về nghiệp vụ.

**Cột "Chi tiết" (`»»`):** mở trang chi tiết một trạm — biểu đồ và bảng số liệu đầy đủ theo mục 7.2. **Yêu cầu đăng nhập.**

---

## 7. BIỂU ĐỒ

### 7.1. Biểu đồ diễn biến mực nước

| Thuộc tính | Quy định |
|---|---|
| Loại | Biểu đồ đường (line chart) |
| Trục hoành (X) | Thời gian – mốc đo **10 phút/lần** liên tục trong ngày, nhãn `HH:mm`; khi xem nhiều ngày thì nhãn `HH:mm dd/MM` |
| Trục tung (Y) | Mực nước (m), 2 chữ số thập phân |
| Số đường | **2 đường cho mỗi công trình** |
| Đường thượng lưu | **Màu đỏ** |
| Đường hạ lưu | **Màu xanh** |
| Chú giải | Bắt buộc, đặt trên hoặc dưới vùng vẽ |
| Tooltip | Hiện `Thời điểm · Thượng lưu · Hạ lưu · Chênh lệch` khi rê chuột |
| Đường ngưỡng | Vẽ 3 đường ngang đứt nét BĐ1 / BĐ2 / BĐ3 nếu điểm đo có khai báo ngưỡng |
| Điểm `SUSPECT` | Vẽ rỗng, nét đứt, **không nối liền** vào đường chính |
| Điểm `MISSING` | Ngắt đường, không nội suy |

**Thể hiện quan hệ chênh lệch thượng lưu – hạ lưu:**

| Thành phần | Quy định |
|---|---|
| Vùng chênh lệch | Tô nền mờ (opacity ~15%) **giữa hai đường** TL và HL, suốt chiều dài biểu đồ. Bề dày dải chính là chênh lệch tại từng mốc |
| Màu vùng | Đỏ nhạt khi `TL > HL` (chênh dương, dòng chảy xuôi) · Xanh nhạt khi `HL > TL` (chênh âm, nước dềnh ngược) |
| Trục Y phụ (bên phải) | Giá trị **chênh lệch (m)**, vẽ dạng đường mảnh màu xám hoặc cột nhạt ở nền |
| Tooltip | Khi rê chuột hiển thị đồng thời: `Thời điểm` · `Thượng lưu` · `Hạ lưu` · `Chênh lệch` · `Chất lượng` |
| Bật/tắt | Người dùng bật/tắt riêng từng thành phần qua chú giải (click vào legend) |

> Chị ghi "biểu đồ line với 2 trục" nên em hiểu là: **trục Y chính** (bên trái) là mực nước cho 2 đường TL/HL, **trục Y phụ** (bên phải) là chênh lệch. Hai trục có thang đo khác nhau (mực nước ~0–5 m, chênh lệch ~0–1 m) nên phải tách trục, nếu dùng chung một trục thì đường chênh lệch sẽ bị dí sát đáy và không đọc được. Nếu ý chị chỉ là biểu đồ hai chiều thông thường (trục hoành thời gian, trục tung mực nước) thì bỏ trục Y phụ, chỉ giữ dải tô nền — chị chốt giúp em.

> Vì màu đỏ – xanh là cặp khó phân biệt với người rối loạn sắc giác, đề nghị bổ sung phân biệt thứ hai: đường thượng lưu nét liền, đường hạ lưu nét đứt. Vẫn giữ nguyên quy ước màu chị đã chốt.

### 7.2. Biểu đồ diễn biến lượng mưa

| Thuộc tính | Quy định |
|---|---|
| Loại | **Biểu đồ cột** cho lượng mưa từng mốc đo |
| Trục hoành (X) | Ngày / mốc thời gian đo (theo giờ) |
| Trục tung (Y) | Lượng mưa (mm) |
| Màu cột | Xanh dương |
| Đường tích lũy (tùy chọn) | Đường tích lũy cộng dồn trên trục Y phụ, phục vụ theo dõi tổng mưa trận |

> Lượng mưa là **giá trị tích lũy trong khoảng**, không phải giá trị tức thời như mực nước. Vẽ đường nối giữa hai mốc sẽ ngụ ý "mưa biến thiên tuyến tính giữa hai giờ" — điều không đúng. Vì vậy đề nghị dùng cột thay cho đường. Nếu Công ty vẫn muốn dạng đường, dev dùng kiểu bậc thang (step line), không dùng đường cong.

### 7.3. Yêu cầu chung cho mọi biểu đồ

- Responsive: co giãn theo khung, trên điện thoại cho phép cuộn ngang thay vì nén nhãn trục X.
- Có nút xuất ảnh PNG và xuất dữ liệu CSV (chức năng xuất: yêu cầu đăng nhập).
- Không có dữ liệu trong khoảng đã chọn → hiển thị `Không có dữ liệu trong khoảng thời gian đã chọn`, **không** vẽ biểu đồ trống.

---

## 8. TRẠNG THÁI KHI CHƯA CÓ DỮ LIỆU

### 8.1. Chưa đấu nối module

Khi `MOD-03` chưa được kết nối, khối trang chủ **vẫn dựng đủ khung** (tiêu đề, phụ đề, hotline, nút Xem chi tiết, dòng cập nhật, nút Làm mới) và phần thân hiển thị:

```
Dữ liệu tạm thời chưa khả dụng

Mô-đun Quản lý dữ liệu thủy văn (MOD-03) chưa được đấu nối,
nên chưa có mực nước và lượng mưa để hiển thị.

Thời điểm cập nhật gần nhất: 10:50 01/09/2026
```

### 8.2. Đã đấu nối nhưng mất kết nối tạm thời

Hiển thị **số liệu gần nhất còn hợp lệ** trong CSDL, kèm dải cảnh báo phía trên bảng:

```
⚠ Dữ liệu tạm thời chưa khả dụng — đang hiển thị số liệu lúc HH:mm dd/MM/yyyy
```

### 8.3. Nguyên tắc chung

- **Tuyệt đối không hiển thị số liệu gán cứng (hard-code) như số liệu thật.**
- Không để khối trống hoặc sập bố cục khi thiếu dữ liệu.
- Không nội suy, không lấp giá trị 0 vào chỗ thiếu.
- Dòng "Thời điểm cập nhật gần nhất" luôn hiển thị, kể cả khi chưa có dữ liệu nào.

---

## 9. PHÂN QUYỀN

| Chức năng | Khách vãng lai | Đã đăng nhập (cán bộ Xí nghiệp) |
|---|---|---|
| Khối "Mực nước, lượng mưa" trên trang chủ | ✅ Xem, chỉ các công trình `is_main_axis`, mốc đo gần nhất | ✅ Đầy đủ |
| Trang chi tiết – tab Mực nước, ngày hiện tại | ✅ Xem | ✅ |
| Trang chi tiết – tab Mực nước, theo **tuần / tháng** | ❌ | ✅ |
| Bảng mưa – **preview**: tổng hợp ngày (3 cột) + các giờ đã qua của ngày hiện tại | ✅ Xem | ✅ |
| Bảng mưa – mốc **3 / 5 / 10 / 30 ngày** | ❌ | ✅ |
| Nút `»»` Chi tiết từng trạm mưa | ❌ | ✅ |
| Biểu đồ diễn biến (mực nước, lượng mưa) | ❌ | ✅ |
| Xuất CSV / PNG | ❌ | ✅ |

**Cách xử lý cho khách vãng lai:** phần bị khóa hiển thị dạng mờ (blur) hoặc khung trống kèm nút `Đăng nhập để xem chi tiết`, không ẩn hoàn toàn để người dùng biết chức năng tồn tại.

**Bắt buộc:** phân quyền xử lý ở **tầng route và API**, không chỉ ẩn ở giao diện. Khách vãng lai gọi trực tiếp endpoint dữ liệu chi tiết phải nhận `401 Unauthorized`, không nhận được dữ liệu (CR-38, M5.2, M5.3).

---

## 10. API NỘI BỘ

Tất cả trả JSON. Endpoint có dấu 🔒 yêu cầu xác thực.

| Method | Endpoint | Tham số | Mô tả |
|---|---|---|---|
| GET | `/api/hydro/stations` | `type`, `public_only` | Danh mục điểm đo |
| GET | `/api/hydro/summary` | — | Dữ liệu khối trang chủ (mục 5.2) |
| GET | `/api/hydro/water-level/latest` | `structure_code[]` | Mốc đo gần nhất |
| GET | `/api/hydro/water-level/grid` | `date`, `mode` (`minute`\|`hour`\|`7h`\|`19h`\|`7-19h`\|`1-7-13-19h`\|`odd`\|`day`), `slots`, `offset` | Bảng tổng hợp mực nước (mục 6.1.2). Trả kèm dòng `delta` đã tính sẵn |
| GET 🔒 | `/api/hydro/water-level/series` | `structure_code`, `from`, `to`, `interval` | Chuỗi số liệu vẽ biểu đồ mực nước |
| GET | `/api/hydro/rainfall/preview` | `date` | Bảng mưa rút gọn cho khách vãng lai |
| GET 🔒 | `/api/hydro/rainfall/daily` | `date`, `range` (1/3/5/10/30) | Bảng mưa đầy đủ 24 giờ |
| GET 🔒 | `/api/hydro/rainfall/series` | `structure_code`, `from`, `to` | Chuỗi số liệu vẽ biểu đồ mưa |
| GET 🔒 | `/api/hydro/export` | `type`, `format`, `from`, `to` | Xuất CSV / Excel |
| GET 🔒 | `/api/hydro/sync-log` | `from`, `to`, `status` | Nhật ký đồng bộ (quản trị viên) |

**Mọi response bắt buộc kèm metadata:**

```json
{
  "data": [ ... ],
  "meta": {
    "last_sync_at": "2026-09-01T10:50:00+07:00",
    "source_status": "OK",
    "unit": { "water_level": "m", "rainfall": "mm" }
  }
}
```

`source_status`: `OK` | `DEGRADED` (có dữ liệu cũ) | `DOWN` (chưa đấu nối / mất kết nối hoàn toàn) — frontend dựa vào trường này để chọn trạng thái hiển thị ở mục 8.

---

## 11. CÁC ĐIỂM CẦN XÁC NHẬN

| Mã | Nội dung | Bên trả lời |
|---|---|---|
| **OI-A** | Ý nghĩa tham số `id1`, `id2` trong URL nguồn. Có cho phép truy vấn theo khoảng thời gian (lấy dữ liệu lịch sử) hay chỉ trả dữ liệu hiện tại? Nếu chỉ trả hiện tại thì phải chạy job liên tục ngay từ đầu để tích lũy lịch sử. | Dev + đơn vị quản trị BHH4.0 |
| **OI-B** | Nguồn quan trắc có trả **kinh độ / vĩ độ** không? Nếu không, Công ty cung cấp tọa độ 20 điểm mực nước + 15 trạm mưa (dùng chung với bản đồ GIS ở MOD-02). | Công ty |
| **OI-C** | Danh sách chính xác **các cống trên trục chính** hiển thị công khai ở trang chủ. Nguồn hiện có 20 công trình mực nước, tài liệu Bố cục quy định chỉ hiện 10. | Công ty |
| **OI-D** | Ngưỡng báo động BĐ1/BĐ2/BĐ3 cho **25 điểm đo còn lại** (hiện mới có 4 điểm khai báo). Chưa có ngưỡng thì chưa tô màu cảnh báo được. | Công ty |
| **OI-E** | **Tuyến sông** và **lý trình** của từng công trình — hai cột này có trên giao diện yêu cầu nhưng không có trong nguồn quan trắc. | Công ty |
| **OI-F** | Bảng ánh xạ **mã điểm đo ↔ công trình ↔ chỉ tiêu** cho toàn bộ 29 điểm mực nước và 15 trạm mưa. Hiện mới có 2 mẫu: `F01519`, `F01771`. | Dev + Công ty |
| **OI-G** | **Cống Lương Cổ**: điểm hạ lưu không có dữ liệu, điểm thượng lưu có bước nhảy bất thường 194 → 450 ngày 01/09/2026. Cảm biến hỏng hay đang hiệu chỉnh? | Công ty |
| **OI-H** | **Cống tiêu tự chảy Yên Nghĩa** (2,75 / 4,96) và **Cống Vân Đình** (2,51 / 3,64) có hạ lưu cao hơn thượng lưu trên 1 m. Do khác cao độ chuẩn giữa hai cảm biến, hay do đấu nhầm điểm đo? | Công ty |
| **OI-I** | Nhóm tài khoản được xem dữ liệu chi tiết: chỉ cán bộ Xí nghiệp, hay có phân theo phạm vi từng Xí nghiệp (mỗi cán bộ chỉ xem công trình đơn vị mình)? | Công ty |
| **OI-J** | Thời gian lưu trữ dữ liệu lịch sử (1 năm / 3 năm / vô hạn) và chính sách dồn nén dữ liệu cũ. Mốc 10 phút × 29 điểm ≈ **1,5 triệu bản ghi/năm**. | Công ty + Dev |

---

## PHỤ LỤC A – DANH SÁCH ĐIỂM ĐO MỰC NƯỚC (20 công trình / 29 điểm)

| TT | Công trình | Chỉ tiêu | Số điểm | Ngưỡng BĐ |
|---|---|---|---|---|
| 1 | Cống Liên Mạc | Thượng lưu · Hạ lưu | 2 | — |
| 2 | Cống Liên Mạc 2 | Hạ lưu | 1 | — |
| 3 | TB Yên Nghĩa | MN Bể hút | 1 | — |
| 4 | Cống tiêu tự chảy Yên Nghĩa | Thượng lưu · Hạ lưu | 2 | — |
| 5 | Cống Hà Đông | Thượng lưu · Hạ lưu | 2 | — |
| 6 | Cống 6 cửa Hòa Bình | Hạ lưu | 1 | — |
| 7 | Cống xả tiêu TB Vĩnh Mộ 2 | Hạ lưu | 1 | — |
| 8 | Cống Đồng Quan | Thượng lưu · Hạ lưu | 2 | TL: +4.00 / +4.40 / +4.70 |
| 9 | Cống Hòa Mỹ | Thượng lưu · Hạ lưu | 2 | — |
| 10 | Cống Vân Đình | Thượng lưu · Hạ lưu | 2 | — |
| 11 | TB Ngoại Độ II | MN Bể hút | 1 | — |
| 12 | Cống Nhật Tựu | Thượng lưu · Hạ lưu | 2 | — |
| 13 | Cống Lương Cổ | Thượng lưu (`F01519`) · Hạ lưu | 2 | — |
| 14 | Cống Điệp Sơn | Thượng lưu · Hạ lưu | 2 | — |
| 15 | TB Hồng Vân | MN sông Hồng | 1 | — |
| 16 | TB Thụy Phú II | MN sông Hồng | 1 | — |
| 17 | TV Hà Nội | MN | 1 | +9.50 / +10.50 / +11.50 |
| 18 | An Cảnh | MN | 1 | +7.20 / +8.20 / +9.10 |
| 19 | TV Ba Thá | MN | 1 | +5.50 / +6.50 / +7.50 |
| 20 | Cống Tắc Giang | MN sông Hồng | 1 | — |

## PHỤ LỤC B – DANH SÁCH TRẠM ĐO MƯA (15 trạm)

| TT | Công trình / điểm đo |
|---|---|
| 1 | Cống Liên Mạc |
| 2 | Trạm bơm Cầu Giát |
| 3 | Cống Hà Đông |
| 4 | Trạm Bơm Yên Nghĩa |
| 5 | Trạm bơm Đại Áng |
| 6 | Trạm bơm Xém |
| 7 | Cống Đồng Quan |
| 8 | Cống Hòa Mỹ |
| 9 | Cống Vân Đình |
| 10 | Trạm bơm Ngoại Độ |
| 11 | Cống Nhật Tựu |
| 12 | Cống Lương Cổ |
| 13 | Cống Điệp Sơn |
| 14 | Trạm bơm Thụy Phú II |
| 15 | Trạm bơm Hồng Vân |

**Lưu ý:** 12 vị trí xuất hiện ở cả hai phụ lục (Liên Mạc, Hà Đông, Yên Nghĩa, Đồng Quan, Hòa Mỹ, Vân Đình, Ngoại Độ, Nhật Tựu, Lương Cổ, Điệp Sơn, Hồng Vân, Thụy Phú II) nhưng là **điểm đo riêng biệt, mã riêng biệt**. Không gộp chung bản ghi mực nước và lượng mưa vào một hàng trong CSDL.
