> Cập nhật **2026-08-12** (bản 2 — sau confirm đợt 2).
> ✅ **ĐỢT 1 (mục A–F) ĐÃ ĐÓNG** — Công ty trả lời đầy đủ ngày 12/8/2026 (`docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md`), đã đồng bộ vào `function-spec.md`, `implement.md`, `architecture-review.md` §8.
> ✅ **ĐỢT 2 — ĐÃ ĐÓNG 8/12 mục**: **G1, G2, G3 (phần lớn), G4, G7, G9, G11, G12** → xem **Phần I-B**, đã đồng bộ vào `function-spec.md` v2.2.
> ⬜ **CÒN MỞ 5 mục**: **G3-a** (lượng mưa) · **G5** (mã số hệ thống văn bản) · **G6** (mẫu 2C-BNV) · **G8 + G8b** (danh sách điểm đo + **bảng ánh xạ mã API**) · **G10** (duyệt format báo cáo) → xem **Phần II**.
> 🔴 **Chỉ còn 1 mục chặn nghiệm thu: G8b.** Không mục nào chặn Phase 0 / Phase 1.
> Ký hiệu: 🔴 chặn thiết kế/code · 🟡 cần trước khi làm module liên quan · ⚪ chốt sau được.

---

## PHẦN I-A — ĐỢT 1: KẾT QUẢ ĐÃ CHỐT (đóng)

| Mã  | Nội dung hỏi                                   | Công ty trả lời                                                                                                        | Tác động đã áp dụng                                                                                                                              |
| --- | ---------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| A1  | Quản lý kế hoạch tưới tiêu / vụ mùa            | **Tạm bỏ nghiệp vụ này**                                                                                               | Bỏ CN-02.11 dự kiến; bỏ BC-04, BC-07; M3.18 chuyển thành "so sánh theo kỳ" không cần bảng kế hoạch                                               |
| A2b | Quan hệ điểm đo ↔ công trình                   | Đồng ý đề xuất                                                                                                         | `station_constructions` n–n có vai trò (TL/HL/Bể hút/Mưa) — CN-03.1                                                                              |
| A3  | Hồ sơ kênh mương / đê điều                     | Đồng ý đề xuất                                                                                                         | Hồ sơ tối thiểu + layer GIS LineString; bảng `canal_specs`/`dyke_specs` chỉ khi cần                                                              |
| B1  | Nhật ký vận hành theo ngày hay ca              | **Bỏ nhật ký vận hành**                                                                                                | ❌ Xóa CN-02.8; bỏ `operation_logs`, `machine_run_records`, workflow duyệt nhật ký, error code OPS-2001/2003                                     |
| B2  | Nhập bù quá 3 ngày                             | Đồng ý đề xuất                                                                                                         | Không còn ý nghĩa sau B1                                                                                                                         |
| B3  | Ủy quyền duyệt khi quản lý vắng                | Đồng ý đề xuất                                                                                                         | Ủy quyền có thời hạn + audit "duyệt theo ủy quyền của X" — áp dụng cho nghỉ phép (CN-04.9)                                                       |
| B4  | Trạng thái tổ máy realtime                     | **Tạm bỏ**                                                                                                             | Không hứa realtime tổ máy; GIS popup/KPI dùng trạng thái công trình cập nhật thủ công + nhãn thời điểm                                           |
| B5  | Diện tích tưới tiêu                            | **Bỏ chức năng liên quan**                                                                                             | Bỏ trường "Diện tích tưới tiêu (ha)"; bỏ mọi thống kê/công thức theo diện tích                                                                   |
| B6  | Chuẩn hóa đơn vị đo                            | Đồng ý đề xuất                                                                                                         | m (scale 3) / mm (1) / m³/s (3); adapter quy đổi lúc ingest — **nguồn trả cm → chia 100**                                                        |
| B7  | SMS Gateway                                    | **Trước mắt thông báo qua Website** tới Ban điều hành + người quản lý công trình trực tiếp; SMS cấu hình giai đoạn sau | Kênh chính = in-app + email; `SmsSender` giữ dạng interface, mặc định tắt                                                                        |
| B8  | Màn hình lớn Phòng điều hành                   | **TV 85" 4K**, có thể kèm máy chiếu 2K / Full HD+                                                                      | Wall mode base 3840×2160, test fallback 1920×1080 & 2560×1440                                                                                    |
| C1  | Phép năm: pro-rata, mốc thâm niên              | Đồng ý, **nhưng để ở biến cấu hình**, Admin điều chỉnh                                                                 | Toàn bộ thông số phép năm vào `settings` + UI                                                                                                    |
| C2  | Luồng duyệt phép mấy cấp                       | Đồng ý đề xuất                                                                                                         | 1 cấp mặc định; cấu hình thêm cấp 2 theo ngưỡng ngày (mặc định tắt)                                                                              |
| C3  | Phạm vi tài khoản CBNV                         | Đồng ý đề xuất                                                                                                         | Cấp tài khoản toàn bộ; có "người tạo hộ"                                                                                                         |
| C4  | Lương — có tính lương không                    | Đồng ý đề xuất                                                                                                         | Chỉ lưu trữ (mã hóa 🔒), không tính lương, không chấm công                                                                                       |
| D1  | Bình luận công khai                            | Đồng ý đề xuất                                                                                                         | Phase 1 tắt bình luận tự do; chỉ khảo sát/góp ý kiểm duyệt 100%                                                                                  |
| D2  | Số cấp vai trò biên tập                        | Đồng ý đề xuất                                                                                                         | 2 vai trò: Biên tập viên + Quản trị nội dung                                                                                                     |
| D3  | Đa ngôn ngữ                                    | **Chỉ tiếng Việt**                                                                                                     | Không i18n nội dung                                                                                                                              |
| D4  | Migrate website cũ                             | **Không migrate, làm thủ công**                                                                                        | Bỏ hạng mục migration khỏi kế hoạch go-live                                                                                                      |
| D5  | Retention dữ liệu                              | Mặc định **5 năm**, đưa vào **biến config**                                                                            | `hydro.retention.detail-years`                                                                                                                   |
| E1  | Backup/Restore qua UI                          | Đã chốt trước đó                                                                                                       | Có nút restore + bảo vệ nhiều lớp                                                                                                                |
| E3  | Tích hợp hệ thống văn bản                      | **Hệ thống độc lập** — lưu account/password của user, cho **1 link tự động đăng nhập**                                 | ⭐ CN-01.7 đổi bản chất: bỏ đồng bộ danh sách văn bản + bảng `external_documents`; thêm `external_system_credentials` (AES-256-GCM) + auto-login |
| E3  | Mẫu báo cáo                                    | **Đề xuất format để Công ty xây dựng**                                                                                 | → `report-templates-proposal.md` (đã soạn, chờ duyệt — G10)                                                                                      |
| E3  | Tài liệu API telemetry                         | **ĐÃ CÓ** (API mực nước, lượng mưa)                                                                                    | ✅ Đã đấu nối thành công: `http://songnhue.bhh40.net/api/getmn.aspx?key=<MÃ_SỐ>;` (**dấu `;` bắt buộc**). Chi tiết nhịp gọi + giới hạn nguồn: **Phần I-B (G3)** |
| E3  | Số lượng điểm đo/công trình/user               | Đưa vào **biến config**                                                                                                | Không giới hạn cứng trong code; giới hạn qua `settings`                                                                                          |
| F1  | Scope nhật ký/sự cố/báo cáo vận hành           | **Bỏ nhật ký vận hành**, thay bằng **Lịch sử các lần sửa chữa** do Admin/người được phân quyền nhập                    | CN-02.2 nâng thành chức năng ghi nhận chính; BC-01/02/03/08 bỏ, thêm BC-09/BC-10. Phần sự cố **đã chốt ở G1** (Phần I-B) — gộp vào CN-02.2       |
| F2  | Trạng thái bản ghi thủy văn                    | Chỉ **2 mức: Hợp lệ / Nghi ngờ**. Nghi ngờ **vẫn ghi data** + thông báo để quản trị **duyệt/xóa**                      | CN-03.2 sửa lại; thêm màn hình "Dữ liệu nghi ngờ"                                                                                                |
| F3  | Lưu vực / khu tưới tiêu                        | **Trường tham chiếu văn bản**                                                                                          | Bỏ CRUD danh mục + bảng `irrigation_zones` + polygon GIS riêng                                                                                   |
| F4  | Công cụ GIS đo & xuất bản đồ                   | Đồng ý                                                                                                                 | Giữ M2.12, M2.13                                                                                                                                 |
| F5  | Giờ hành chính (cảnh báo đăng nhập bất thường) | **Biến config, mặc định 8h–17h**                                                                                       | Vào danh mục cấu hình CN-05.3                                                                                                                    |
| F6  | Restore UI + 2FA                               | Đồng ý, chấp nhận ràng buộc                                                                                            | Giữ nguyên thiết kế `architecture-review.md` §7.3                                                                                                |
| F7  | Shapefile                                      | Đồng ý đề xuất                                                                                                         | v1 nhận GeoJSON/KMZ; import Shapefile → convert GeoJSON                                                                                          |
| F8  | NFR lệch giữa SRS và nội bộ                    | Đồng ý đề xuất                                                                                                         | Lấy mức chặt hơn làm mục tiêu nội bộ                                                                                                             |

---

## PHẦN I-B — ĐỢT 2: KẾT QUẢ ĐÃ CHỐT (đóng 12/8/2026)

| Mã | Nội dung hỏi | Công ty trả lời | Tác động đã áp dụng |
|---|---|---|---|
| **G1** | Phiếu sự cố còn trong scope? | **Theo đề xuất → PA A**: gộp sự cố vào Lịch sử sửa chữa | CN-02.2 thêm `Mức độ` + `Trạng thái xử lý` (Mới→Đang xử lý→Đã xử lý) + FK `alert_event_id`; **CN-02.9 chuyển thành ❌ tombstone**; bỏ bảng `incidents`, mã `SC-`, workflow 7 trạng thái; BC-06 đổi nguồn sang `alert_events + maintenance_logs`; trạng thái "Sự cố (đỏ)" của công trình suy ra từ bản ghi sự cố đang mở |
| **G2** | Cần chỉ tiêu giờ chạy máy / kWh / m³ bơm? | **Không cần** | Đóng vĩnh viễn — **không** làm màn hình "Số liệu vận hành theo tháng"; BC-01/02/03/08 bỏ hẳn (CN-02.8) |
| **G3**-b | Không có API lịch sử | **Chấp nhận — hệ thống tự fetch và ghi lịch sử** | Poller là điểm bắt dữ liệu duy nhất, không backfill; bắt buộc ghi `hydro_raw_logs` trước khi parse; **giám sát poller xếp ngang backup DB** (`architecture-review.md` §8.2) |
| **G3**-c | Chu kỳ gọi API | Nguồn cập nhật trong khung 10': **dữ liệu mới từ phút `x1:30` → `x8:30`**, còn lại nghỉ để nhận dữ liệu từ máy đo. → **Gọi 2 phút/lần vào các phút lẻ (1-3-5-7-9)** | Cron mặc định `45 1/2 * * * *` (giây 45 để vượt mốc `01:30`), là tham số cấu hình — CN-03.2 |
| **G3**-d | Rate limit | **Nên có rate-limit theo chu kỳ cập nhật** để khỏi gọi khi response không đổi | Trước mỗi lần gọi: nếu **toàn bộ** điểm đo hoạt động đã có bản ghi thuộc khung 10' hiện tại → **bỏ qua**, không mở HTTP (`sync_logs = SKIPPED_UP_TO_DATE`). Điều kiện dừng phải là *đủ toàn bộ trạm* vì nguồn trả rải rác trong 7 phút |
| **G3**-e | Trạm trục trặc thể hiện thế nào | **Trên GIS biểu thị màu xám** | Phát hiện = không có bản ghi mới quá `N` khung (mặc định 3 ≈ 30', cấu hình được) → `MẤT_TÍN_HIỆU`; marker GIS xám, badge bảng realtime, biểu tổng hợp hiển thị `-`, **không tính cảnh báo ngưỡng**; phân biệt với lỗi nguồn toàn phần |
| **G4** | Tình hình vận hành cống lấy từ đâu | **Không có trong API — config tay qua 1 màn hình admin**. Hiện 4 mã nhưng **cần CRUD** phòng thay đổi. **Có map sang trạng thái + màu tương ứng trên UI** | ⭐ **CN-02.11 mới**: (a) danh mục `operation_status_codes` CRUD — Mã, Tên, Có tham số kèm (`+1.70m`), **Màu hex**, **Trạng thái công trình ánh xạ** (để trống = không tác động), Thứ tự, Hiện/Ẩn; seed 4 mã, không xóa cứng mã đã dùng. (b) bảng `construction_operation_status` **append lưu lịch sử**, cập nhật khi có thay đổi + màn hình nhập nhanh hàng loạt. Hiển thị badge màu trên biểu tổng hợp / GIS / wall mode |
| **G7** | Retention audit log | **Confirm** đề xuất | Giữ **5 năm** (`audit.retention-years`, cấu hình được); quá hạn → **kết xuất file lưu trữ có checksum SHA-256** lên MinIO rồi mới xóa khỏi bảng nóng; hash chain nối tiếp qua ranh giới kết xuất; kết xuất lỗi → không xóa dòng nào — CN-05.4 |
| **G9** | Ngưỡng cảnh báo thực tế | **Admin sẽ tự config ngưỡng** | Bàn giao **màn hình cấu hình ngưỡng đầy đủ** (điểm đo × chỉ số × mức, có lịch sử + audit) là hạng mục nghiệm thu; điểm đo chưa cấu hình → nhãn "chưa cấu hình ngưỡng" + **không phát cảnh báo**; có danh sách "Điểm đo chưa cấu hình ngưỡng". *(Bộ mức ngưỡng cụ thể: xem G9 phần còn mở)* |
| **G11** | Người nhận cảnh báo | **Confirm** đề xuất | Hợp của: (a) nhóm cố định **"Ban điều hành"** do Admin cấu hình; (b) **tự động** người phụ trách đơn vị quản lý công trình liên kết điểm đo (`station_constructions → constructions.org_unit → org_units`). Khử trùng lặp; loại tài khoản khóa/nghỉ việc — CN-03.6 |
| **G12** | Con số nghiệm thu NFR | **Confirm** | Uptime **≥ 99%** · **200 CCU** · trang chủ **< 3s** · báo cáo tháng **< 60s** · **2FA bắt buộc Admin + Admin HR** → §7 NFR ghi rõ "con số nghiệm thu chính thức" + bổ sung tiêu chí đo (load test 200 CCU; 7 ngày không bỏ sót khung 10') |

---

## PHẦN II — CÒN MỞ: CẦN CÔNG TY CUNG CẤP

> 5 mục dưới đây **không chặn Phase 0 và Phase 1**. Chỉ **G8b** chặn nghiệm thu MOD-03.

### G3-a. 🟡 Lượng mưa — chốt cách xử lý ở v1

✅ Đã rõ: **API lượng mưa "tạm thời chưa có"** (đã thử `getmua`, `getlm`, `getrain`, `getluongmua`, `getmn2` — HTTP 404; chỉ tồn tại `getmn.aspx`).

Đang thiết kế theo hướng: giữ loại chỉ số "Lượng mưa" trong mô hình dữ liệu + chừa sẵn chỗ cắm adapter, **cột lượng mưa trên biểu tổng hợp/báo cáo hiển thị `-`**.

**Cần Công ty chốt 1 trong 3**:

| PA | Nội dung | Khối lượng |
|---|---|---|
| **A** _(đang áp dụng)_ | Chờ endpoint — v1 không hiển thị lượng mưa, khi nào nguồn cấp API thì bật lên, không phải sửa thiết kế | Không thêm gì |
| B | Làm **màn hình nhập tay lượng mưa** (theo ca Đêm/Ngày như biểu hiện tại) để không trống cột | + form nhập + phân quyền + báo cáo ~3–5 ngày công |
| C | **Bỏ hẳn** lượng mưa khỏi v1 (gỡ cả cột khỏi biểu tổng hợp và báo cáo) | Giảm nhẹ |

👉 Nếu Công ty vẫn cần theo dõi lượng mưa hằng ngày ngay từ v1 thì phải chọn **B**; PA A chỉ phù hợp nếu chấp nhận trống cột tới khi có endpoint.

### G5. 🟡 Liên kết hệ thống văn bản điều hành — chi tiết cách đăng nhập

Khảo sát cho thấy `songnhue.bhh40.net` đăng nhập bằng **1 "mã số" duy nhất** (không có cặp tài khoản/mật khẩu), do Ban quản trị cấp.

**Cần confirm**:

1. Mỗi CBNV có **mã số riêng**, hay cả Công ty dùng **chung 1 mã số**? _(khác nhau hoàn toàn về thiết kế: riêng → mỗi user tự nhập & tự lưu; chung → lưu 1 chỗ ở cấu hình hệ thống)_
2. Ai nhập mã số vào hệ thống mới: **người dùng tự nhập** (khuyến nghị — an toàn hơn) hay **Admin nhập hộ**?
3. ⭐ **Đề nghị cân nhắc phương án an toàn hơn**: xin bên quản trị `bhh40.net` cấp **link đăng nhập kèm token dùng một lần** hoặc mở SSO. Khi đó **không cần lưu mã số** của ai cả → loại bỏ hẳn rủi ro lộ credential. Công ty có thể đề nghị được không?
4. Hệ thống nguồn đang chạy **HTTP (không mã hóa)** — Công ty có kế hoạch bật HTTPS không? _(nếu không, mã số bị lộ khi truyền qua mạng — cần ghi nhận là rủi ro tồn dư trong biên bản)_

### G6. 🟡 File mẫu lý lịch 2C-BNV (báo cáo BCNS-07)

Báo cáo lý lịch cán bộ phải in **đúng mẫu 2C-BNV/2008 của Bộ Nội vụ** — không tự chế được.
**Cần**: file mẫu chính thức Công ty đang dùng (bản `.doc`/`.xls`), vì các đơn vị thường có biến thể riêng.
_(Gộp chung đợt gửi file mẫu báo cáo — G10.)_

### G8. 🟡 Xác nhận danh sách điểm đo & công trình ban đầu

Danh sách trích từ hệ thống nguồn ngày 12/8/2026 — **cần Công ty xác nhận là danh sách chuẩn để nhập liệu ban đầu**:

**Điểm đo mực nước (theo tuyến sông + lý trình)**

| Tuyến sông    | Điểm đo (lý trình)                                                                                                             |
| ------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| Sông Nhuệ     | Liên Mạc (K0+390) · ĐTHL Liên Mạc (K1+085) · Hà Đông (K18+100) · Đồng Quan (K43+750) · Nhật Tựu (K63+405) · Lương Cổ (K72+506) |
| Sông La Khê   | Cửa sông La Khê (K15+470 bờ hữu sông Nhuệ) · Trạm bơm Yên Nghĩa · Yên Nghĩa (K6+322)                                           |
| Sông Vân Đình | Hòa Mỹ (K1+460) · Vân Đình (K72+000 – sông Đáy)                                                                                |
| Sông Duy Tiên | Cửa sông Duy Tiên (K57+420 bờ tả sông Nhuệ) · Điệp Sơn (K21+000)                                                               |
| Sông Hồng     | … (K96+600) · Trạm bơm Thụy Phú II (K104+232) · Cống Tắc Giang (K129+696)                                                      |
| Sông Đáy      | Yên Nghĩa (K38+000) · Ba Thá (K46+500) · Vân Đình (K72+000) · Cống Phủ Lý (K109+754)                                           |

**Công trình có điểm đo mưa**: Cống Liên Mạc · TB Cầu Giát · Cống Hà Đông · TB Yên Nghĩa · TB Đại Áng · TB Xém · Cống Đồng Quan · Cống Hòa Mỹ · Cống Vân Đình · TB Ngoại Độ · Cống Nhật Tựu · Cống Lương Cổ · Cống Điệp Sơn · TB Thụy Phú II · TB Hồng Vân.

**Cần confirm**:

1. Danh sách trên đã đủ/đúng chưa? Có điểm đo/công trình nào ngoài danh sách này cần quản lý không?
2. **Tổng số công trình Công ty quản lý** (kể cả công trình không có điểm đo) là bao nhiêu? Có sẵn file Excel danh mục không?
3. **Tọa độ GPS** của các công trình/điểm đo — Công ty có sẵn không, hay cần số hóa bằng cách chọn điểm trên bản đồ?

### G8b. 🔴 **BẢNG ÁNH XẠ MÃ API ↔ TÊN ĐIỂM ĐO — chặn toàn bộ MOD-03**

API trả về **mã số, không trả tên điểm đo**. Không có bảng ánh xạ thì hệ thống không biết `F01771` là điểm nào, không gắn được ngưỡng cảnh báo, không lên được bản đồ, không ra được báo cáo.

19 mã nhận được ngày 12/8/2026 lúc 21:50 (giá trị đơn vị **cm**):

| Mã API | Giá trị | Mã API | Giá trị | Mã API | Giá trị | Mã API | Giá trị |
| ------ | ------- | ------ | ------- | ------ | ------- | ------ | ------- |
| F01519 | 189     | F01652 | 351     | F01732 | 375     | F01905 | 181     |
| F01527 | 179     | F01657 | 182     | F01771 | 447     | F01965 | 294     |
| F01532 | 256     | F01672 | 294     | F01794 | 249     | F02030 | 190     |
| F01559 | 436     | F01705 | 218     | F01812 | 342     | F02031 | 190     |
| F01707 | 203     | F01820 | 203     | F02039 | 180     |        |         |

**Cần Công ty cung cấp bảng**: `Mã API | Tên điểm đo | Tuyến sông | Lý trình | Công trình liên quan | Vai trò (TL/HL/Bể hút) | Loại chỉ số | Đơn vị`.

_(Đối chiếu sơ bộ với biểu tổng hợp lúc 21h20 chỉ khớp chắc chắn được vài mã — VD F01771=447 ↔ Liên Mạc TL (K0+390), F01652=351 ↔ Yên Nghĩa (K38+000) sông Đáy, F01532=256 ↔ Ba Thá (K46+500), F01705=218 ↔ Cống Phủ Lý (K109+754). **Phần còn lại không suy đoán** — ánh xạ sai thì toàn bộ cảnh báo và báo cáo sai theo.)_

### G9-a. ⚪ Bộ mức ngưỡng cảnh báo — cần chốt danh sách mức

✅ Đã chốt: **Admin tự cấu hình ngưỡng** trong UI (không cần Công ty cấp bảng số liệu trước khi triển khai).

⬜ Còn thiếu: câu trả lời bị cắt ở đoạn *"config ngưỡng cho các ngưỡng sau…"* → chưa rõ **hệ thống cần dựng sẵn những mức nào**. Đang thiết kế theo mặc định 3 mức để không chặn code:

| Mức | Ý nghĩa | Màu |
|---|---|---|
| Cảnh báo thấp | Mực nước xuống dưới mức tối thiểu vận hành | 🟡 |
| Cảnh báo cao | Vượt mức an toàn, cần theo dõi | 🟡 |
| Nguy hiểm | Vượt mức thiết kế, phải xử lý ngay | 🔴 |

👉 **Cần Công ty xác nhận**: giữ 3 mức trên, hay dùng bộ mức khác (VD **báo động cấp I / II / III** theo quy định ngành thủy lợi, hoặc thêm mức "Nguy hiểm thấp")? Nếu số mức thay đổi thì bảng cấu hình vẫn chạy được (thiết kế dạng danh mục mức), chỉ khác dữ liệu khởi tạo.

### G10. 🟡 Duyệt đề xuất format báo cáo

Đã soạn `report-templates-proposal.md` gồm: khung cấu trúc chuẩn 5 khối, quy ước trình bày, danh mục báo cáo còn lại sau khi cắt scope (BC-05, BC-09, BC-10, BC-11, BC-12, BC-13, BCNS-01..08, BCQT-01..03) và trường dữ liệu từng báo cáo.
**Cần Công ty**: (1) duyệt danh mục — bỏ/thêm báo cáo; (2) gửi **file mẫu thật** cho 4 báo cáo trọng yếu: **BC-11 biểu tổng hợp mực nước**, **BC-09 tổng hợp sửa chữa**, **BC-05 thủy văn tháng**, **BCNS-07 mẫu 2C-BNV**; (3) xác nhận khối chữ ký & thể thức; (4) cho biết báo cáo nào phải nộp cấp trên (phải theo mẫu quy định).

---

## TÓM TẮT VIỆC CẦN CÔNG TY LÀM

| # | Mục | Việc cần làm | Hạn cần có |
|---|---|---|---|
| 1 | 🔴 **G8b** | Điền **bảng ánh xạ 19 mã `F#####` ↔ tên điểm đo / tuyến sông / lý trình / vai trò TL-HL** | Trước nghiệm thu MOD-03 — **chặn** |
| 2 | 🟡 **G8** | Xác nhận danh sách điểm đo + **danh mục toàn bộ công trình (Excel)** + tọa độ GPS | Trước khi nhập liệu ban đầu |
| 3 | 🟡 **G10** | Duyệt `report-templates-proposal.md` + gửi **file mẫu thật** của BC-11, BC-09, BC-05, BCNS-07 | Trước Phase báo cáo |
| 4 | 🟡 **G6** | File mẫu **2C-BNV** Công ty đang dùng (gửi kèm G10) | Trước Phase HRM |
| 5 | 🟡 **G5** | Mã số hệ thống văn bản: **riêng từng người hay chung**? + đề nghị bên `bhh40.net` cấp **token/SSO** thay vì lưu mã số + kế hoạch bật **HTTPS** | Trước Phase MOD-01 |
| 6 | 🟡 **G3-a** | Chốt cách xử lý **lượng mưa** ở v1 (PA A/B/C) | Trước Phase MOD-03 |
| 7 | ⚪ **G9-a** | Xác nhận **bộ mức ngưỡng** cảnh báo (3 mức đề xuất hay cấp I/II/III) | Trước khi cấu hình ngưỡng thật |

Trả lời theo mã mục, ví dụ: `G3-a: chọn PA B · G5: mã số riêng từng người, user tự nhập · G9-a: dùng cấp I/II/III`.

Sau khi nhận confirm → cập nhật `function-spec.md`, `implement.md` và đóng mục tương ứng tại đây.
