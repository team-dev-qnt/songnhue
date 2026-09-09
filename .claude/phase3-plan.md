# PHASE 3 — KẾ HOẠCH CODING CHI TIẾT

> **Đây là kế hoạch, KHÔNG phải sổ tracking.** Task và nợ vẫn ở `master-tracking.md`
> (`conventions.md` §6 — mục ấy CẤM tạo file tracking mới, tiền lệ `phase1-execution-tracking.md`
> lệch 29 mã số task). File này chỉ giữ đặc tả mỗi hạng mục: tiên quyết · đầu ra · cách kiểm chứng ·
> bẫy đã trả giá. Mâu thuẫn thì `architecture-review.md` > file này > `implement.md`.
>
> Lập ngày **09/09/2026** trên đỉnh `dev` `72794b6`. Nguồn: `docs_origin/spec-description.md`
> (SPEC MOD-03 v1.0, 01/09) · `function-spec.md` v2.2 · `implement.md` §2 nhóm C3+D · lượt rà
> 10 chiều + phản biện đối kháng 09/09 (28/80 phát hiện được phản biện: **11 CONFIRMED, 17
> PARTLY_WRONG**) · **phép đo nguồn `songnhue.bhh40.net` chạy thật 09/09**.
>
> ⛔ **Mọi con số trong file này là số đo có hạn dùng.** Đọc lại thì đo lại, đừng trích.

Ký hiệu: ⛔ cấm/chặn · ⚠ dễ sai · ⭐ bằng chứng đo được · 📌 bài học rộng hơn · ⬜ còn treo.

---

## 0. Đọc file này thế nào

| Cần gì | Đọc mục |
|---|---|
| Bốn quyết định QuanTran đã chốt 09/09 | §1 ⛔ đọc trước tiên |
| ⭐ Phép đo nguồn 09/09 — cái gì vừa đổi | §2 |
| Phase 3 gồm và **không** gồm gì | §3 |
| Nền đo được: cái gì đã có, cái gì thiếu nửa cặp | §4 |
| Phải chốt gì **trước khi gõ dòng mã đầu tiên** | §5 |
| Thứ tự + phụ thuộc | §6 |
| Đặc tả từng hạng mục WS-43 → WS-50 | §7 |
| Definition of Done Phase 3 | §8 |
| Checklist luật — dán vào mỗi PR | §9 |
| Nợ phải trả + mục chờ Công ty | §10 |

---

## 1. ⛔ Bốn quyết định đã chốt 09/09/2026 (QuanTran)

| # | Câu hỏi | Chốt | Hệ quả |
|---|---|---|---|
| **Q1** | Spec §3 đề xuất bảng PIVOT `wl_reading`; kho đang time-series DỌC | **Giữ DỌC, pivot ở tầng ĐỌC** | ⛔ KHÔNG tạo `wl_reading`/`rain_reading`. Thêm cột trên `stations`, viết câu truy vấn pivot + DTO cây. `hydro_latest`/`hydro_agg_daily`/`alert_rules` giữ nguyên khoá |
| **Q2** | `conventions.md` §6 cấm tạo file tracking mới | **`phase3-plan.md` là KẾ HOẠCH** | Checkbox tiến độ vẫn ở `master-tracking.md`. ⛔ Không sinh nguồn sự thật thứ hai |
| **Q3** | Có đo thử `default.aspx?pro=xemchitiet` không | **Có — đo, báo cáo trước khi dùng** | ✅ Đã đo 09/09, kết quả §2 |
| **Q4** | CR-08 đăng nhập cổng có vào Phase 3 không | ⛔ **HUỶ. Dữ liệu thuỷ văn CÔNG KHAI toàn bộ** | Cột "Đã đăng nhập" của spec §9 **bỏ hẳn**. Ẩn/hiện bằng công tắc admin đã có (`khoiVanHanh` + `hydro.portal.station-codes`) |
| **Q5** | `meta` đặt ở đâu | **Trong `data` của riêng hydro** | §5.1 |
| **Q6** | Trang chủ vs trang chi tiết dùng chung một danh sách điểm đo? | **Hai núm riêng** | `is_main_axis` (cột mới) quyết định **trang chủ**; `hydro.portal.station-codes` (khoá cũ) quyết định **trang chi tiết**. OI-C về ⇒ tick 10 ô, ⛔ không cần deploy |
| **Q7** | Thứ tự hai việc ưu tiên | **WS-43 → bảng cây → biểu đồ** | Bảng dùng DTO đầy đủ nhất ⇒ nó kiểm chứng hình dạng DTO **trước khi** biểu đồ bám vào |
| **Q8** | Lượng mưa làm gì tiếp | **Quan sát trước khi cam kết** | ⛔ Chưa dựng adapter HTML. Đặt lượt đo định kỳ tab `MN+Mưa` cho tới khi phân biệt được `-` = "không mưa" với `-` = "không có số" (luật 9) |

### ⚠ Ba hệ quả của Q4 phải xử lý, không được bỏ quên

1. **Spec §9 hết hiệu lực** — bảng phân quyền 8 hàng, `KhoaDangNhap`, blur, nút "Đăng nhập để xem
   chi tiết" đều **không dựng**. Sửa lại `muc-nuoc-luong-mua/page.tsx` (javadoc đang khai
   *"phần sau đăng nhập chưa dựng"*) và gỡ `KhoaDangNhap` khỏi 2 trang đang dùng, ⛔ không để một
   ô chữ nói về một quyết định đã bị huỷ.
2. **Xuất CSV/PNG thành bề mặt công khai** ⇒ phải qua `RateLimitFilter` (đã có, `core` WS-4).
   Một endpoint xuất không giới hạn là một cửa quét dữ liệu. ⛔ Đây là việc BẮT BUỘC của WS-43,
   không phải việc để dành.
3. **Biểu đồ §7 phải dựng ở `public-web`**, nơi có **0 thư viện biểu đồ**
   (`grep -rn echarts frontend/public-web/src` = 0). Đây là chi phí thật, không phải chép component
   từ `admin-app` sang.

---

## 2. ⭐ PHÉP ĐO NGUỒN 09/09/2026 — thứ vừa thay đổi bức tranh

Chạy thật bằng `curl`, đọc-thuần, không dùng mã số riêng. ⚠ Số đo có hạn dùng.

### 2.1. Spec §2.1 mô tả một TÍCH HỢP KHÁC hẳn thứ đang chạy

| | Đang chạy (Phase 2) | Spec §2.1 mô tả |
|---|---|---|
| Đường dẫn | `api/getmn.aspx?key=<mã số>;` | `default.aspx?user=<TOKEN>&pro=…` |
| Hình dạng | text, **28 mã một lượt** | trang **HTML**, một lượt một trạm |
| Trong kho | `Bhh40Adapter` | `grep xemchitiet\|default.aspx\|id1=\|HYDRO_SOURCE_TOKEN` = **0** |

📌 ⇒ **OI-A ("id1/id2 nghĩa là gì") là câu hỏi về một endpoint hệ thống KHÔNG dùng.** Đừng đi hỏi
Công ty câu ấy trước khi chốt có dùng đường HTML hay không.

### 2.2. ⭐⭐ Nguồn CÓ lượng mưa — G3-a phải mở lại

Trang chủ nguồn (`default.aspx`, HTTP 200, 21.270 byte) có menu **`QUAN TRẮC [NSL]`**:

> Mực nước · **Lượng mưa** · **Tổng hợp mưa** · Biểu tổng hợp · Tivi · Bản đồ Google

kèm đúng **20 công trình mực nước** (Phụ lục A) và **15 trạm mưa** (Phụ lục B) của spec.

Bản đồ đường dẫn trích được từ chính HTML:

| `pro=` | Nghĩa | Gỡ được gì |
|---|---|---|
| `ketquado.songnhue-xemmucnuoc` | Xem mực nước | — |
| **`ketquado.songnhue-xemluongmua`** | **Xem lượng mưa** | **G3-a** |
| **`tonghopluongmua`** | **Tổng hợp mưa** | **spec §3.4 `rain_daily`** |
| `tonghopdieuhanh` | Biểu tổng hợp điều hành | spec §5.2 / §6.1.2 |
| `ketquado.songnhue-bando` | Bản đồ | **G8 toạ độ** (⬜ chưa đo) |
| `xemchitiet.<MÃ>&mode=view&id1=0&id2=0` | Chi tiết một điểm đo | spec §2.1 |
| `nhapsuadulieu` | Nhập/sửa dữ liệu | — |

⛔ **Kết luận cũ của G3-a chỉ đúng trong phạm vi nó đo.** BOQ ghi *"đã thử `getmua`/`getlm`/
`getrain`/`getluongmua`/`getmn2` — HTTP 404; chỉ tồn tại `getmn.aspx`"*. Đúng — **cho họ `/api/`**.
Dữ liệu mưa nằm sau `default.aspx`, không sau `/api/`. Đây đúng luật 28: *một cơ chế canh gác phải
nói ra phạm vi của chính nó* — kết luận "không có nguồn mưa" hẹp hơn nơi nó được dùng để chặn.

### 2.3. ⭐⭐ "Biểu tổng hợp" CÔNG KHAI — và nó LÀ bảng spec §5.2 đang mô tả

`http://103.9.86.202/bhh40.net.songnhue/tonghop-dh/bieusov01.aspx?user=@tonghopdh&pro=homepage&tivi=yes`
— mã `@tonghopdh` nằm **nguyên văn trong HTML công khai**, HTTP 200, **không cần mã số riêng**.

Nội dung đo được (18h50 ngày 09/09/2026):

```
I. Mực nước tại các cống trên sông Nhuệ
   Liên Mạc (K0+390) | Liên Mạc (K1+085) | Hà Đông (K18+100) | Đồng Quan (K43+750) | …
   Vận hành...       | Vận hành...       | Vận hành...       | Vận hành...
   TL   HL           | TL   HL           | TL   HL           | TL   HL
   202  303          | -    304          | 255  -            | 117  115
   Mực nước trên sông La Khê · sông Vân Đình · sông Duy Tiên   [Giá trị nội suy]
II. Mực nước tại các cống trên sông Hồng      III. … sông Đáy
Ghi chú: MT: Mở treo · ĐK: Đóng kín · ĐTTL+1.70m · ĐTHL+1.70m
```

Bảy điều khớp **chính xác** với thứ ta đã dựng hoặc đang cần:

| Đo được ở nguồn | Đối ứng trong kho |
|---|---|
| Nhóm theo **tuyến sông** (Nhuệ · La Khê · Vân Đình · Duy Tiên · Hồng · Đáy) | `stations.river_name` (13/19 có) — spec §5.2 gộp ô |
| **Lý trình trong tên** `(K0+390)`, `(K43+750)`, `(K72+506)` | `stations.chainage` + `chainage_m` STORED — khớp từng giá trị |
| Cặp **TL / HL** hai cột dưới một công trình | `position_role` — chính là cây 3 tầng của WS-44 |
| Dòng **"Vận hành..."** | `construction_operation_status` (CN-02.11) |
| **MT · ĐK · ĐTTL · ĐTHL** | 4 mã đã seed ở `operation_status_codes` |
| Giá trị **cm nguyên**, ô thiếu là `-` | spec §2.3 (chia 100) + quy tắc 16 (`-` ≠ `0.00`) |
| **"Giá trị nội suy"** | `stations.is_interpolated` |

⭐ ⇒ **Đây là bản tham chiếu THẬT cho "gen tree structure"**, thay cho việc suy từ văn bản spec.
⚠ Nhưng luật *"chép PIXEL của cổng tham chiếu vào một khung hẹp hơn"* (WS-39, 01/09) áp thẳng vào
đây: bảng của họ rộng **1336px** (`<table width=1336px>`). ⛔ Đo bề rộng khung ta TRƯỚC khi chia cột.

### 2.4. ⭐ Tab `MN+Mưa` có khối mưa đúng hình dạng spec §3.4

`&menuh=indexselect02` trả thêm:

```
II. Lượng mưa tại các điểm đo trên hệ thống thủy lợi sông Nhuệ
    Thời điểm lượng mưa từ...h...ngày...đến...h...ngày...
    Liên Mạc          | Hà Đông           | Yên Nghĩa         | Đồng Quan
    Đêm Ngày Tổng     | Đêm Ngày Tổng     | Đêm Ngày Tổng     | Đêm Ngày Tổng
    -   -    -        | -   -    -        | -   -    -        | -   -    -
```

**Đêm / Ngày / Tổng lượng mưa** = đúng `rain_night_mm` / `rain_day_mm` / `rain_total_mm` của spec §3.4.

⬜ **CHƯA TRẢ LỜI ĐƯỢC**: cả 15 trạm đang hiện `-`. Chưa phân biệt được *"hôm nay không mưa"* với
*"chưa bao giờ có số"* — đúng hình dạng quy tắc 16 ở phía nguồn. ⛔ Phải quan sát qua một trận mưa,
hoặc hỏi Công ty, **trước khi** cam kết dựng đường nạp mưa.

### 2.5. ⭐ Biểu 03 → Biểu 10 của nguồn: RỖNG, và nguồn tự nói vì sao

`&menuh=indexselect03` trả đúng một câu:

> *"cmd=indexselect03 (đợi Sông Nhuệ thiết kế biểu mẫu dashboard xong thì lập trình hiển thị)"*

📌 **Nhà cung cấp nguồn đang chờ Công ty đúng thứ ta đang chờ ở G10.** Nên gộp câu hỏi G10 lại:
một bộ biểu mẫu duyệt xong phục vụ cả hai bên. ⇒ đưa vào thư `docs/de-nghi-cung-cap-g6-g8-g10.md`.

### 2.6. ⚠ Nguồn trả HTTP 200 cho mã số SAI — ở CẢ hai đường

`default.aspx?user=x&…` → **HTTP 200**, 21.337 byte, gần như trùng trang chưa đăng nhập.
Cùng hình dạng với `getmn.aspx` trả `not.working` kèm **200** (CLAUDE.md T42.17).

⛔ ⇒ Bất kỳ adapter HTML nào cũng phải phân biệt *"khoá hết hạn"* với *"hôm nay không có số"*
**bằng nội dung**, ⛔ không bằng mã trạng thái. Quy tắc 18 nói **mất dữ liệu là vĩnh viễn**, và
luật 9 nói *một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì*.

### 2.7. ⭐ Manh mối cho "9 mã lạ mỗi lượt poll"

Biểu tổng hợp liệt các công trình **không có trong danh mục 19 điểm** của ta:
`Liên Mạc (K1+085)` · `Cửa sông La Khê (K15+470)` · `Cửa sông Duy Tiên (K57+420)` ·
`Yên Nghĩa (K6+322)` · `Yên Nghĩa (K38+000)` · `Sơn Tây (K31+600)` · `Đan Hoài (K46+100)` ·
`Liên Mạc (K53+450)` · `Long Biên (K65+210)` · `Ba Thá (K46+500)` · `Cống Phủ Lý (K109+754)`.

⬜ **Giả thuyết chưa kiểm**: đây là phần lớn 9 mã `F#####` mà `hydro_unmapped_readings` đang giữ.
Kiểm bằng cách đối chiếu `soMaLa` của BC-13 với danh sách này. Nếu đúng ⇒ **G8 mở rộng danh mục
điểm đo 19 → ~28** và lớp GIS + biểu tổng hợp đầy lên đáng kể.

---

## 3. Phạm vi Phase 3

### 3.1. Trong phạm vi

| Nhóm | Nội dung | Nguồn chốt |
|---|---|---|
| ⭐ **P3-A — MOD-03 tầng trình bày** *(ưu tiên QuanTran giao)* | Cây/lưới §5.2 + §6.1.2 · biểu đồ §7.1 · `meta` envelope · trạng thái rỗng §8 | `spec-description.md` |
| **P3-B — Lượng mưa** | §3.3 · §3.4 · §6.2 · §7.2 — **chỉ mở khi §2.4 trả lời xong** | spec + G3-a |
| **C3 — GIS + Dashboard + wall 4K + báo cáo MOD-02** | `implement.md` §3 | có bản tham chiếu ở §2.3 |
| **D — HRM (MOD-04)** | CN-04.1 → CN-04.9 | `function-spec.md` §612–651 |
| **Trả nợ chặn** | §10.1 | lượt rà 09/09 |

### 3.2. Ngoài phạm vi — và vì sao

| Bỏ | Vì sao |
|---|---|
| **CR-08 đăng nhập cổng + toàn bộ spec §9** | QuanTran huỷ 09/09 (§1 Q4) |
| Bảng `wl_reading` / `rain_reading` / `rain_daily` | Q1 — giữ dọc, pivot ở tầng đọc |
| Thêm `MISSING` vào `ReadingQuality` | §5.3 — MISSING là **sự vắng mặt**, không phải một hàng |
| Đổi lịch poller sang 10'/15' của spec §4.1 | Không mâu thuẫn: spec nói **tần suất ĐO tại nguồn**, luật 17 nói **nhịp GỌI**. Cả hai đã là 2 tham số riêng trong mã |
| `BCNS-07` (mẫu 2C-BNV) | G6 — cấm tự chế layout biểu mẫu quy định |

---

## 4. Nền — đo ngày 09/09/2026

### 4.1. Đã có, ⛔ KHÔNG dựng lại

`stations` 19 dòng + `river_name` 13 + `chainage` 10 · `hydro_readings` partition + UNIQUE 3 khoá ·
`hydro_latest` · `hydro_agg_daily` · `alert_rules`/`alert_levels` (⚠ **0 hàng**, cố ý, chờ G9-a) ·
`Bhh40Adapter` + parser 10 quy tắc · poller + rate-limit + `PollerChangCuoiHttpTest` (⭐ chặng cuối
**đã có** bằng chứng — cảnh báo ⛔⛔ trong CLAUDE.md **đã lỗi thời**) · quy đổi cm→m có mã **và** có
phép kiểm HTTP · `TreeBuilder` + `MaterializedPath.sortForDisplay` (4/4 repo, nợ §11.12 **đã vá
thật**) · `PermissionInterceptor` deny-by-default + `DenyByDefaultTest` · `RateLimitFilter` ·
`CryptoService` · Workflow engine · Attachment (`owner_type` đã ghi sẵn `'EMPLOYEE'`) ·
cơ chế nhập tệp dùng chung (`core/common/importer` + `ImportModal`) · 11 khoá `settings` nhóm HYDRO.

### 4.2. ⛔ Thiếu — mỗi dòng là một nửa cặp đọc–ghi (luật 27)

| # | Thiếu | Phép đo | Chặn |
|---|---|---|---|
| 1 | `stations.structure_code` / `structure_name` | grep = 0 | khoá gộp cặp TL/HL |
| 2 | `stations.display_order` | grep = 0 | thứ tự thượng→hạ nguồn §5.2 |
| 3 | `stations.is_main_axis` | grep = 0 | 10 cống trục chính (OI-C) |
| 4 | Câu truy vấn **đa-điểm-đo theo cửa sổ thời gian** | 10/10 lời gọi `hydro_readings` đều `WHERE station_id = ?` | §6.1.2 toàn phần |
| 5 | `delta_m` / "Chênh lệch" | grep toàn kho = 0 (chữ "delta" trong kho là **tốc độ đổi**, khái niệm khác) | §6.1.2 · §7.1 |
| 6 | `meta` trong envelope | `ApiResponse.meta` là `PageMeta` 4 trường cứng; conventions §2.1 khai meta **chỉ** cho phân trang | §8.1 · §8.2 |
| 7 | `source_status` OK/DEGRADED/DOWN | grep = 0 | §8 — ⛔ không chọn được 1 trong 3 trạng thái |
| 8 | `rowSpan`/`colSpan` trên bảng dữ liệu | 0 toàn frontend; bảng cổng là **lưới CSS bằng `div`**, ⛔ không phải `<table>` | §5.2 · §6.1.2 · §6.2 |
| 9 | Thư viện biểu đồ ở `public-web` | grep `echarts` = 0 | §7 |
| 10 | Sinh cột động theo mốc thời gian | 0 | §6.1.1 (7 chế độ xem) |

### 4.3. ⛔⛔ Bốn khuyết tật đo được, chưa ai chặn

| # | Khuyết tật | Bằng chứng |
|---|---|---|
| **N1** | Dòng **"Cập nhật lúc"** trên cổng đọc **đồng hồ máy chủ** (`Instant.now()`), ⛔ không phải `MAX(synced_at)` — spec §5.1 cấm đích danh. Poller chết 3 ngày, cổng vẫn in giờ mới mỗi lượt F5. Tệ hơn: ô trạng thái rỗng §8.1 in *"Thời điểm cập nhật gần nhất: &lt;giờ hiện tại&gt;"* — **một câu khẳng định SAI xuất hiện đúng lúc hệ thống đang hỏng** | CONFIRMED |
| **N2** | **Q5 nửa vế**: regex nhận `-?\d+` ⇒ giá trị **ÂM** đi thẳng vào bảng chính. Và chú thích migration khẳng định sentinel `-999` bị vỏ bọc bắt — đo lại: `-999 cm = -9,99 m` **nằm trong** `[-10; 30]` ⇒ ra `HOP_LE`. Bài kiểm duy nhất canh mục ấy chỉ thử `-9999` | CONFIRMED |
| **N3** | **Q2 chưa bật**: `deltaToiDaMoiGio` cố ý không seed ⇒ **chính ca thật spec dẫn ra** (Lương Cổ 194→450 cm) hôm nay ra `HOP_LE` | CONFIRMED |
| **N4** | `hydro.polling.max-retry` / `api_sources.max_retry` là **núm không điều khiển gì** — có seed, có cột, có ô nhập UI, có giá trị "đã giải" hiển thị, **0 nơi dùng**. Luật 15 | CHƯA phản biện |

### 4.4. ⚠ Ba khoảng lệch giữa SỔ và MÃ (luật: sổ cũng là dữ liệu chưa kiểm)

- **73 dòng task còn mở** (59 `[ ]` + 14 `[~]`), ⛔ không phải 63/18 như phép đếm thô.
- **8 dòng sổ bị mã bác bỏ**: T24.33 · T26.24 · T26.76 · T38.12 đã xong mà vẫn `[ ]`; T11.88 tick
  `[x]` trong khi chính Note của nó khai *"STAGING VẪN CHƯA CÓ"*; DOD0.20 mở trong khi staging chạy thật.
- **2 nợ chỉ sống trong `CLAUDE.md`**, 0 lần trong sổ (bật lịch sao lưu · phép nghiệm thu số 9
  chứng minh `MINIO_ENDPOINT`) — vi phạm "nguồn DUY NHẤT".

---

## 5. ⛔ Bảy điểm phải chốt TRƯỚC dòng mã đầu tiên

### 5.1. ✅ CHỐT 09/09 — `meta` nằm TRONG `data` của riêng hydro
Spec §10 đòi mọi response kèm `last_sync_at` · `source_status` · `unit`. `ApiResponse.meta` hôm nay
là `PageMeta` bốn trường **cứng**, và `conventions.md` §2.1 khai *"meta chỉ xuất hiện với response
phân trang"*.

⇒ **DTO hydro tự mang khối `meta`**, envelope giữ nguyên:

```json
{ "data": { "meta": { "lastSyncAt": …, "sourceStatus": "DEGRADED", "unit": {…} },
            "tuyenSong": [ … ] },
  "meta": null }
```

**Vì sao ⛔ không mở rộng envelope**: `ResponseEnvelopeAdvice` vừa dính một khuyết tật **im lặng** ở
lượt Boot 4 (§11.20 — nó bỏ bọc envelope cho MỌI endpoint mà vẫn biên dịch sạch). Chạm vào nó để
phục vụ một module là đổi rủi ro của 5 module lấy sự đúng chữ của một tài liệu.
⚠ Lệch chữ spec §10 ⇒ **ghi ra** ở javadoc DTO, ⛔ đừng để người sau đọc spec rồi tưởng làm thiếu.

### 5.2. Khoá gộp cặp TL/HL: cột mới trên `stations`, ⛔ không phải `constructions`
Lượt phản biện **sửa lại** phát hiện ban đầu: spec §3.1 đặt `structure_code`/`structure_name` ngay
**trên bảng điểm đo**, ⛔ không đòi danh mục `constructions` (đang **0 dòng**). ⇒ **P3-A KHÔNG bị G8
chặn.** Dựng được ngay và ra **14 công trình / 19 dòng số thật**.
⚠ Nhưng phải nói rõ trong migration vì sao có HAI khoá công trình (`stations.structure_code` cho
trình bày thuỷ văn · `constructions.code` cho MOD-02), ⛔ nếu không người sau sẽ hợp nhất chúng.

### 5.3. `MISSING` là SỰ VẮNG MẶT, ⛔ không phải một hàng — và đây là thứ làm biểu đồ ngắt đường
`hydro_readings.reading_value` là `NOT NULL` ⇒ một hàng MISSING **về nguyên tắc không tồn tại được**.
Thêm `MISSING` vào enum còn kéo theo `ck_hydro_latest_quality` (CHECK **duy nhất còn 2 giá trị**),
`HydroLatestRecomputer` lọc `<> 'XOA'` (MISSING sẽ chui vào rồi ném ở CHECK), 3 `FILTER` ở
`HydroReportRepository` và tổng 3 số hạng ở `ChatLuongNgayRow` **nuốt nó im lặng**.
⇒ ⛔ **KHÔNG thêm.** MISSING suy ra ở tầng đọc bằng cách **trải lưới thời gian rồi trừ đi mốc có số**.
⚠ Đây cũng là chỗ CONFIRMED một khuyết tật biểu đồ: hôm nay trục X dựng **từ chính mảng điểm trả
về**, nên `connectNulls:false` **⛔ không có `null` nào để chạy qua** — mốc thiếu bị **NUỐT**, đường
nối liền qua chỗ mất dữ liệu. Sửa: BE trả **lưới đủ mốc**, ô thiếu là `null`.

### 5.4. `quality` ra API biểu đồ — đây là ngoại lệ luật 14, phải khai bằng tên
Quy tắc 14 buộc mọi truy vấn báo cáo lọc `HOP_LE`. Spec §7.1 lại đòi vẽ điểm `SUSPECT` **rỗng, nét
đứt, không nối**. Kho **đã có tiền lệ chạy thật**: `NGOAI_LE` 10 mục, trong đó BC-12
(`HydroReportRepository#SQL_CHI_TIET`) đã bỏ lọc để đổi lấy cột Chất lượng.
⇒ Đi theo tiền lệ ấy: thêm một mục `NGOAI_LE` có tên, ⛔ không lặng lẽ bỏ lọc.

### 5.5. Q4 (HL > TL quá 0,5 m) — ⛔ ĐỪNG viết trước khi Công ty trả lời
Lượt phản biện **sửa lại**: T32.2 (*cấm lệnh "TL > HL"*) **KHÔNG** chặn Q4 — cấm lệnh chỉ cấm **hạ
xuống NGHI_NGO**, còn Q4 giữ `GOOD`; hai bên thống nhất. Thứ chặn thật là spec **OI-H** để ngỏ:
Yên Nghĩa (2,75/4,96) và Vân Đình (2,51/3,64) là **vận hành đúng** hay **lệch cao độ chuẩn**?
⛔ Chưa trả lời thì đừng viết — bật sai biến mọi trận vận hành thành cảnh báo.

### 5.6. Số ÂM: loại bỏ (spec Q5) hay giữ + gắn cờ (quy tắc 18)?
Spec Q5 nói **loại bỏ bản ghi + ghi log**. Quy tắc 18 nói **mất dữ liệu là vĩnh viễn**, ghi nguyên
văn trước khi parse. Hai câu không trái nhau (raw vẫn giữ), nhưng phải chốt bản ghi **đã parse** đi
đường nào. ⛔ Đừng tự chọn.

### 5.7. `hr.*` — 15 khoá `settings` có UI sửa, **0 nơi đọc** (T42.15)
Seed từ 13/8, hiện thành tab "Nhân sự" ở admin. Vi phạm luật 15 đúng chữ. ⇒ **Phải quyết TRƯỚC dòng
mã HRM đầu tiên**: `editable=FALSE` cho tới khi có nơi đọc, **hay** dựng bộ đọc ngay trong WS mở HRM.

---

## 6. Thứ tự và phụ thuộc

```
WS-43  Nền trình bày MOD-03  ────────────────────────┐  (chặn mọi thứ P3-A)
       cột stations · truy vấn pivot · meta+source_status
       · rate-limit đường xuất công khai · N1
          │
          ├──▶ WS-44  ⭐ CÂY / LƯỚI  §5.2 + §6.1.2      ← ưu tiên 1a
          │
          └──▶ WS-45  ⭐ BIỂU ĐỒ     §7.1               ← ưu tiên 1b
                      (cần thư viện biểu đồ ở public-web)
                         │
WS-46  Lượng mưa §3.3/§3.4/§6.2/§7.2  ⬜ chỉ mở sau khi §2.4 trả lời xong
WS-47  Trả nợ chặn: N2 · N3 · N4 · T42.15 · 8 dòng sổ sai
WS-48  C3 — GIS + Dashboard + wall 4K   (⭐ có bản tham chiếu §2.3)
WS-49  D — HRM: D0 lược đồ → D1 hồ sơ → D6 nghỉ phép
WS-50  Nghiệm thu Phase 3
```

⚠ **WS-44 và WS-45 dùng CHUNG câu truy vấn pivot của WS-43** — làm song song thì phải chốt DTO
trước, ⛔ không để hai người dựng hai hình dạng.

---

## 7. Đặc tả từng hạng mục

### WS-43 — Nền trình bày MOD-03

**Tiên quyết**: §5.1, §5.2, §5.3 đã chốt và ghi vào `architecture-review.md`.

**Đầu ra**
1. Migration: `stations` + `structure_code` · `structure_name` · `display_order` · `is_main_axis`.
   ⚠ Đánh số theo dãy `nnnn` tăng dần (`MigrationNamingTest`), ⛔ không dùng giờ-phút (§10.66).
   ⚠ Cập nhật `db-migration-checksums.txt` (§10.65).
2. Câu truy vấn **đa-điểm-đo × cửa sổ thời gian** — cái đầu tiên của kho.
   ⛔ Đọc bảng agg khi khoảng > 1 ngày (quy tắc 8), ⛔ không scan raw.
3. DTO cây 3 tầng `tuyến sông > công trình > chỉ tiêu[]`, mỗi chỉ tiêu mang **lưới đủ mốc**
   (ô thiếu = `null`, §5.3). `delta` tính ở **BE** (quy tắc 3), chỉ khi **cả hai** khác NULL — ⛔
   không suy diễn, ⛔ không gán 0 (spec §3.2).
4. `meta`: `last_sync_at` (**`MAX(synced_at)`**, ⛔ không `Instant.now()` — sửa N1) ·
   `source_status` OK|DEGRADED|DOWN · `unit`.
5. ⛔ **`RateLimitFilter` cho mọi endpoint xuất công khai** (hệ quả Q4).

**Cách kiểm chứng**
- Bài kiểm **qua HTTP** (luật 5), có `Origin` (luật 6).
- ⛔ Vế **chống tập rỗng**: khẳng định lưới có `> 0` mốc **và** ít nhất một công trình có **đủ cặp
  TL+HL** — ⛔ nếu không, bộ canh xanh trong đúng tình huống nó sinh ra để bắt (luật 7 · §11.19).
- Bài chứng minh `delta` **KHÔNG** được tính khi thiếu một vế.
- Bài chứng minh `source_status` phân biệt được **cả ba** trạng thái (luật 9).
- ⛔ Chạy `make ci-order` — `PUT` thiếu trường đã xoá trắng dữ liệu G8 **hai lần** ở đúng module này (§11.19).

**Bẫy**: N1 · luật 27 (mỗi cột mới phải trả đủ 6 câu §7.3 của `phase2-plan.md`) · §11.15 (endpoint
phải có màn hình gọi).

---

### WS-44 — ⭐ CÂY / LƯỚI §5.2 + §6.1.2  *(ưu tiên 1a)*

**"Gen tree structure" là BA hình dạng khác nhau, ⛔ không phải một:**

| | Hình dạng | Ghi chú |
|---|---|---|
| (a) | Nhóm 2 tầng **tuyến sông > công trình**, gộp ô cột Tuyến sông | §5.2 |
| (b) | **2–3 dòng/công trình**: Thượng lưu → Hạ lưu → *Chênh lệch*; gộp ô 3 cột đầu | §6.1.2 |
| (c) | **Tiêu đề 2 tầng** + cột thời gian **động** (12/24/…), sticky 3 cột đầu | §6.1.1 |

⛔ **`TreeBuilder` của `core` KHÔNG dùng được ở đây.** Nó ghép theo `parent_id` (org_units,
categories, media_folders, menu_items — 4 bảng). Đây là **gộp nhóm trên một phép nối**, không phải
cây cha–con. ⇒ Viết mới, ⛔ đừng bẻ cong `TreeBuilder`; và ⛔ đừng thêm `parent_id` vào `stations`.

**Đầu ra**
1. Component bảng **`<table>` thật** ở `public-web`. ⛔ Bảng cổng hôm nay là **lưới CSS bằng `div`**
   — `rowSpan` về nguyên tắc ⛔ không dựng được ở đó. Đây là **viết lại**, ⛔ không phải sửa.
2. Dòng **Chênh lệch**: in nghiêng, nền xám nhạt, chỉ hiện khi có **đủ** TL+HL. ⛔ Không tô màu
   ngưỡng cho dòng này (spec §6.1.2).
3. Ô `NGHI_NGO`: vàng + `⚠` + tooltip lý do. Ô thiếu: **trống** + tooltip. ⛔ Cấm `0.00` (quy tắc 16).
4. Tô màu ngưỡng §5.3 qua **`alert_levels.color_token`** (đã có, CHECK cấm mã hex). ⛔ Cấm ghi cứng
   mã màu (nợ T25.23). ⚠ `alert_levels` **0 hàng** ⇒ phải chạy đúng trên tập rỗng (luật 7).
5. Sticky 3 cột đầu + `overflow-x: auto` — ⛔ thân trang **không** được cuộn ngang.

**Bẫy**: ⛔⛔ **WS-39** — *chép PIXEL của cổng tham chiếu vào một khung hẹp hơn*: bảng nguồn rộng
**1336px** (§2.3). Đo bề rộng khung ta **trước** khi chia cột. · §10.79 (khối rỗng làm vỡ bố cục) ·
`xemTruocKhopCong` phải chạy.

---

### WS-45 — ⭐ BIỂU ĐỒ §7.1  *(ưu tiên 1b)*

**Điểm xuất phát đo được**: `GET /api/v1/hyd/bieu-do/muc-nuoc-24h` — **1 điểm đo × 1 chỉ số × 24h
chốt**, chỉ vẽ ở `admin-app`. DTO mang `(moc, giaTri)`, ⛔ không cờ chất lượng, ⛔ không ngưỡng,
⛔ không delta.

**Đầu ra**
1. Endpoint chuỗi **theo CÔNG TRÌNH** (cặp TL/HL + delta), nhận `from`/`to`/`interval`.
   ⚠ `HydroChartService` có javadoc khai *cửa sổ 24h là RÀNG BUỘC, ⛔ không phải thiếu sót* — nới nó
   phải **đọc và cập nhật javadoc ấy**, ⛔ không lặng lẽ nới (§10.42: hạ mức một cảnh báo thì phải
   nói ra ai còn đọc nó).
2. Thư viện biểu đồ cho `public-web` (**0 hôm nay**). ⚠ Đăng ký `MarkLineComponent` — thiếu nó
   ECharts **im lặng**, ⛔ không ném lỗi (`toolbox`/`dataZoom` thì có in console.error).
3. Hai đường TL/HL + **dải tô nền giữa hai đường** (stacked `areaStyle`, ⛔ **không** `markArea` —
   bề dày biến thiên) + **trục Y phụ** = chênh lệch + **3 đường ngang đứt nét** BĐ1/2/3.
4. Điểm `NGHI_NGO` vẽ rỗng nét đứt, ⛔ không nối. Mốc thiếu **ngắt** đường (§5.3).
5. Nút xuất PNG + CSV. ⛔ Qua `RateLimitFilter` (hệ quả Q4).
6. Không có dữ liệu → **câu chữ**, ⛔ không vẽ khung trục rỗng. Khuôn đã có: `BieuDoMucNuoc` ép
   *"hoặc CÓ điểm, hoặc CÓ lý do trống"* ở **hàm dựng** — giữ đúng khuôn ấy.

**⚠ A11y** (spec §7.1 tự nêu): đỏ–xanh là cặp khó phân biệt với người rối loạn sắc giác ⇒ TL nét
liền, HL nét đứt. `docs/ui-styles.md` có mục a11y — đọc trước.

---

### WS-46 → WS-50

Đặc tả mở sau khi WS-43 đóng — ⛔ ghi trước là ghi một kế hoạch chưa có nền đo được.
⬜ WS-46 **chỉ mở khi §2.4 trả lời xong** (mưa `-` là "không mưa" hay "không có số").

---

## 8. Definition of Done — Phase 3

| Mã | Nội dung |
|---|---|
| DOD3.1 | Bảng §6.1.2 ra **≥ 1 công trình có đủ cặp TL+HL và dòng Chênh lệch**, đo qua HTTP trên CSDL thật |
| DOD3.2 | `delta` **không** xuất hiện khi thiếu một vế — có bài kiểm |
| DOD3.3 | Mốc thiếu **ngắt** đường biểu đồ; có bài chứng minh mốc thiếu ⇒ `null` trong lưới |
| DOD3.4 | `source_status` phân biệt được **cả ba** trạng thái, mỗi trạng thái một bài |
| DOD3.5 | "Cập nhật lúc" = `MAX(synced_at)`; bài kiểm: đóng băng dữ liệu ⇒ dòng chữ **không** đổi |
| DOD3.6 | Mọi endpoint xuất công khai qua `RateLimitFilter` — có bài đo 429 |
| DOD3.7 | 0 mã màu ghi cứng ở `public-web` (giữ mức hiện tại); ngưỡng đi qua `color_token` |
| DOD3.8 | Bảng chạy đúng trên **tập rỗng** `alert_levels` **và** trên tập có ngưỡng |
| DOD3.9 | `make ci-local` **và** `make ci-order` đều thoát 0 |
| DOD3.10 | Mỗi bộ canh mới có bài chứng minh nó **bắt được vi phạm** (luật 1) |
| DOD3.11 | Mọi endpoint mới có **màn hình gọi** (§11.15 — 7/62 endpoint CMS từng mồ côi) |
| DOD3.12 | 8 dòng sổ sai (§4.4) đã sửa; 2 nợ chỉ sống ở CLAUDE.md đã vào sổ |
| DOD3.13 | HRM: `hr.*` hoặc có nơi đọc, hoặc `editable=FALSE` (T42.15) |

---

## 9. Checklist luật — dán vào mỗi PR Phase 3

Dùng lại **`phase2-plan.md` §7 nguyên văn** (⛔ không chép lại ở đây — hai bản sẽ lệch). Bổ sung
bốn câu riêng của Phase 3:

- [ ] Cột/khoá mới có **đủ cặp đọc–ghi**, và có **màn hình** gọi được? (luật 27 · §11.15)
- [ ] Bộ canh mới có chạy được trên **tập rỗng** không, và nó có vế **chống tập rỗng** chưa? (luật 7 · §11.19)
- [ ] Đã chạy **`make ci-order`**? (thứ tự lớp — macOS ngược Linux)
- [ ] Con số nào ghi vào tài liệu đều **kèm ngày đo**?

---

## 10. Nợ và mục chờ Công ty

### 10.1. Nợ CHẶN Phase 3 (đo 09/09)

| Mã | Nội dung | Chặn |
|---|---|---|
| **T42.15** | 15 khoá `hr.*` có UI sửa, 0 nơi đọc | dòng mã HRM đầu tiên |
| **T42.14** | Kho **không có bộ kết xuất PDF/XLSX nào** (`common/export/` 1 tệp; POI/jasper/openpdf = 0 trong cả 7 pom) | CN-04.1 · BCNS-07 · vế in BC-11 |
| **G6-a** | Danh sách CBNV | HRM — thứ THẬT SỰ chặn, chưa mục nào hỏi |
| **N1–N4** | §4.3 | nghiệm thu §5.1/§8 · Q2/Q5 |

⛔ T42.14: **đừng chọn thư viện trước khi có file mẫu thật** — mẫu quyết định khổ giấy, gộp ô, phông tiếng Việt.

### 10.2. Nợ KHÔNG chặn, trả song song

DOD0.21 quay lui thật · T11.88 TLS staging (hạn **22/11/2026**, chưa có cron) · T7.13 diễn tập khôi
phục · T37.1 (7 ngày lịch — ⭐ **bấm giờ được ngay**, đồng hồ càng bấm sớm càng tốt) · T37.2 load
test 200 CCU · T38.10 Playwright chưa vào CI · T25.23 (25 mã màu `admin-app`) · DOD1.17 (<3s).

### 10.3. Mục chờ Công ty — ⭐ ba mục vừa ĐỔI TRẠNG THÁI ngày 09/09

| Mã | Trạng thái mới | Vì sao |
|---|---|---|
| **G3-a** | ⭐ **MỞ LẠI — kết luận cũ hẹp hơn phạm vi nó chặn** | Nguồn **CÓ** "Lượng mưa" + "Tổng hợp mưa" sau `default.aspx` (§2.2). Kết luận cũ đúng cho `/api/`, ⛔ không đúng cho cả nguồn. ⬜ Còn phải phân biệt `-` = "không mưa" hay "không có số" (§2.4) |
| **OI-A** | ⭐ **Sai đề** | `id1/id2` thuộc endpoint hệ thống ⛔ không dùng (§2.1). Hỏi lại: *có dùng đường HTML không* |
| **G10** | ⭐ **Gộp được với nguồn** | Biểu 03→10 của nguồn cũng đang **chờ Công ty duyệt biểu mẫu** (§2.5). Một bộ mẫu phục vụ cả hai bên |
| **G8** | ⬜ Toạ độ vẫn **0/19**. ⭐ Nhưng `pro=ketquado.songnhue-bando` **chưa đo** — có thể có toạ độ | §2.2 |
| **OI-C** | ⬜ 10 cống trục chính | ⭐ Cột `is_main_axis` dựng ở WS-43 ⇒ ngày Công ty chốt là **gõ vào là xong** |
| **OI-D / G9-a** | ⬜ Ngưỡng cho 25 điểm còn lại | `alert_levels` **0 hàng** — chặn **nghiệm thu** §5.3, ⛔ không chặn viết |
| **OI-H** | ⬜ HL > TL ở Yên Nghĩa / Vân Đình | Chặn Q4 (§5.5) |

### 10.4. ⬜ Ba phép đo nên chạy tiếp (rẻ, giá trị cao)

1. `pro=ketquado.songnhue-bando` → **toạ độ 19 điểm?** ⇒ có thể gỡ vế nặng nhất của G8.
2. Đối chiếu **9 mã lạ** của BC-13 với 11 công trình lạ ở §2.7 ⇒ mở rộng danh mục 19 → ~28.
3. Quan sát tab `MN+Mưa` qua **một trận mưa** ⇒ trả lời dứt điểm G3-a.
