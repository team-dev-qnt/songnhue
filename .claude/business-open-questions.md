> Cập nhật **2026-08-19** (bản 3 — mở 3 mục mới khi lập kế hoạch Phase 1: **G13, G14, G15**; **G15 đóng ngay trong ngày**).
> ✅ **ĐỢT 1 (mục A–F) ĐÃ ĐÓNG** — Công ty trả lời đầy đủ ngày 12/8/2026 (`docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md`), đã đồng bộ vào `function-spec.md`, `implement.md`, `architecture-review.md` §8.
> ✅ **ĐỢT 2 — ĐÃ ĐÓNG 9/12 mục**: **G1, G2, G3 (phần lớn), G4, G7, G8b, G9, G11, G12** → xem **Phần I-B**, đã đồng bộ vào `function-spec.md` v2.2.
> ⬜ **CÒN MỞ 8 mục**: **G3-a** (lượng mưa) · **G5** (mã số hệ thống văn bản) · **G6** (mẫu 2C-BNV) · **G8** (tuyến sông/lý trình/tọa độ + danh mục công trình) · **G9-a** (bộ mức ngưỡng) · **G10** (duyệt format báo cáo) · ⭐ **G13** (bộ nhận diện cổng) · ⭐ **G14** (sơ đồ danh mục/menu cổng) → xem **Phần II**.
> ✅ **G15 đóng 19/8/2026** — cụm công trình chỉ là cách nhóm, không phải đơn vị tổ chức.
> ✅ **KHÔNG CÒN MỤC NÀO CHẶN CODE.** G8b — mục chặn cuối cùng của MOD-03 — đã đóng ngày 12/8/2026. G13/G14 chặn **nghiệm thu** cổng TTĐT chứ không chặn code.
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
| **G8b** 🔴→✅ | Bảng ánh xạ mã API ↔ tên điểm đo | **Cung cấp đủ 19/19 mã** kèm vai trò TL/HL/Bể hút/**MN sông** | **Gỡ bỏ mục chặn cuối cùng của MOD-03.** Bảng seed đưa vào `function-spec.md` CN-03.1; bổ sung vai trò **`MN_SONG`** vào enum; chốt quy tắc `stations.position_role` vs `station_constructions.role`; **cấm validate "TL > HL"** (2/5 cặp đảo hợp lệ); seed/join **dùng mã, cấm dùng tên** (2 công trình cùng tên "Yên Nghĩa"); biểu tổng hợp phải chịu được ô trống (9/19 điểm không thành cặp) |
| **G12** | Con số nghiệm thu NFR | **Confirm** | Uptime **≥ 99%** · **200 CCU** · trang chủ **< 3s** · báo cáo tháng **< 60s** · **2FA bắt buộc Admin + Admin HR** → §7 NFR ghi rõ "con số nghiệm thu chính thức" + bổ sung tiêu chí đo (load test 200 CCU; 7 ngày không bỏ sót khung 10') |

---

## PHẦN II — CÒN MỞ: CẦN CÔNG TY CUNG CẤP

> Các mục dưới đây **không chặn Phase 0, Phase 1 và không chặn việc code MOD-03**. Ảnh hưởng chủ yếu tới **dữ liệu khởi tạo** và **nghiệm thu**.

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

✅ **Đã có (G8b)**: bảng ánh xạ 19 mã API ↔ tên điểm đo + vai trò → `function-spec.md` CN-03.1.
⬜ **Còn thiếu để nhập liệu ban đầu — 4 việc**:

**1. Bổ sung 3 cột còn thiếu cho đúng 19 điểm đo đã ánh xạ**: `Tuyến sông | Lý trình (K..+..) | Tọa độ GPS`. Không có tọa độ thì điểm đo **không lên được bản đồ GIS** (M2.8/M3.17).

**2. Đối chiếu khoảng trống giữa API và biểu tổng hợp.** API chỉ phủ **19 điểm**, ít hơn danh sách trên biểu tổng hợp. Cụ thể:

| | Nội dung |
|---|---|
| **Có trên biểu tổng hợp nhưng KHÔNG có trong API** | Cống Phủ Lý · Điệp Sơn · TB Thụy Phú II · Cống Tắc Giang · Cửa sông La Khê · Cửa sông Duy Tiên · ĐTHL Liên Mạc (K1+085) |
| **Có trong API nhưng KHÔNG có trong danh sách cũ** | TV Hà Nội · An Cảnh · Liên Mạc 2 · Cống tiêu tự chảy Yên Nghĩa |

👉 Các điểm nhóm trên **có được quan trắc tự động không**, hay số liệu nhập tay / đọc thủ công? Nếu không có telemetry thì hệ thống mới sẽ **không có dữ liệu** cho các điểm đó — cần biết trước để không hứa nhầm khi nghiệm thu.

**3. Xác nhận 3 cặp mã đang trả giá trị trùng khít** (quan sát 21:50 ngày 12/8): `F02030`≡`F02031` (Nhật Tựu TL/HL, 1.90 m) · `F01707`≡`F01820` (bể hút TB Yên Nghĩa ≡ TL cống tiêu tự chảy Yên Nghĩa, 2.03 m) · `F01672`≡`F01965` (HL Cống Liên Mạc ≡ HL Liên Mạc 2, 2.94 m).
👉 Đây là **2 cảm biến độc lập cùng vực nước**, hay **1 cảm biến được cấp 2 mã** (hoặc giá trị nội suy)? Khác nhau ở chỗ có nên gắn 2 bộ ngưỡng riêng hay không. *(Phía phát triển sẽ theo dõi vài ngày để tự đối chiếu, nhưng cần Công ty xác nhận chính thức.)*

**4. Danh mục công trình tổng thể**: tổng số công trình Công ty quản lý (kể cả công trình **không** có điểm đo) — có sẵn file Excel không? Kèm loại, cấp quản lý, đơn vị phụ trách, năm xây dựng, tọa độ.

> ℹ **Lưu ý phân biệt tên**: có **2 công trình khác nhau cùng mang tên "Yên Nghĩa"** (`TB Yên Nghĩa` và `Cống tiêu tự chảy Yên Nghĩa`) và cụm Liên Mạc có `Cống Liên Mạc` + `Liên Mạc 2`. Khi gửi danh mục đề nghị **kèm mã công trình**, tránh trùng tên gây nhầm khi nhập liệu.

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

> ⭐ **Ba mục dưới đây mở ngày 19/8/2026**, khi lập kế hoạch Phase 1. Chúng **không chặn việc viết mã** — thiết kế đã chừa chỗ — nhưng **chặn việc nghiệm thu**: một cổng thông tin không có logo, không có danh mục và không có bài nào thì không có gì để Công ty xem.

### G13. 🟡 Bộ nhận diện cổng TTĐT & tài khoản dịch vụ ngoài

CN-01.5 yêu cầu cấu hình logo, favicon, màu chủ đạo, footer, mạng xã hội, mã theo dõi — nhưng **không tài liệu nào nói ai cấp những thứ đó**. Hệ thống đọc tất cả từ `settings` nên không phải sửa mã khi nhận được, song không có thì cổng chạy bằng giá trị mặc định của lập trình viên.

**Cần Công ty gửi**:

| Nhóm | Cụ thể |
|---|---|
| Hình ảnh | **Logo** (nên có bản SVG hoặc PNG nền trong, ≥ 512px) · **Favicon** 32×32 · ảnh đại diện mặc định khi bài viết không có ảnh |
| Màu & chữ | Màu chủ đạo / màu phụ (mã hex nếu đã có bộ nhận diện; nếu chưa, phía phát triển đề xuất) |
| Thông tin chân trang | Tên đầy đủ, địa chỉ, điện thoại, email, mã số thuế, người chịu trách nhiệm nội dung · **giấy phép trang thông tin điện tử tổng hợp** (nếu có) |
| Liên kết | Facebook / Zalo / YouTube (nếu có) · vị trí trên bản đồ để nhúng |
| Dịch vụ ngoài | **Google Analytics Tracking ID** · **GTM Container ID** · **Google reCAPTCHA v3 site key + secret** *(reCAPTCHA dùng cho form Liên hệ ở Phase 2 — xin sớm để khỏi phải quay lại)* |

⚠ **reCAPTCHA secret là bí mật** — gửi riêng, không đưa vào tài liệu chung. Hệ thống lưu ở biến môi trường, không nằm trong bản xuất cấu hình.

### G14. 🟡 Sơ đồ danh mục nội dung, menu cổng và nội dung trang tĩnh

D4 đã chốt **không migrate website cũ**, Công ty tự nhập lại nội dung. Nhưng phần **khung** thì phía phát triển phải dựng và bàn giao sẵn, nếu không thì đến ngày nghiệm thu mới ngồi nghĩ cây danh mục.

**Cần Công ty chốt**:

1. **Cây danh mục nội dung** (tối đa 3 cấp) — ví dụ khung để Công ty sửa: *Giới thiệu* (Lịch sử, Cơ cấu tổ chức, Chức năng nhiệm vụ) · *Tin tức* (Tin hoạt động, Tin chuyên ngành, Thông báo) · *Công trình thuỷ lợi* · *Văn bản* · *Thông tin thuỷ văn* · *Liên hệ*.
2. **Menu header và menu footer** — có thể khác cây danh mục (menu thường gọn hơn).
3. **Nội dung các trang tĩnh**: Giới thiệu, Chức năng nhiệm vụ, Cơ cấu tổ chức, Liên hệ. Gửi bản Word cũng được.
4. **Ai là người đăng bài đầu tiên** và cần bao nhiêu tài khoản Biên tập viên / Quản trị nội dung — để cấp tài khoản khi bàn giao.

👉 Nếu Công ty chưa chốt kịp, phía phát triển sẽ **seed khung đề xuất ở trên** để cổng chạy được, Công ty sửa sau qua giao diện — không phải sửa mã.

### ~~G15~~. ✅ **ĐÃ ĐÓNG 19/8/2026** — "Cụm công trình" chỉ là cách nhóm

> **Trả lời**: cụm **không phải** đơn vị trong sơ đồ tổ chức — không có tổ trưởng, không có nhân sự thuộc cụm. Nó là cách nhóm các công trình gần nhau.
>
> **Áp dụng**: dựng bảng `construction_clusters` riêng + khoá ngoại **nullable** `constructions.cluster_id`. ⛔ **Không** thêm loại đơn vị mới vào `org_units` — cây tổ chức giữ nguyên cho Xí nghiệp và phòng ban (quy tắc 7). Đơn vị phụ trách công trình vẫn là `constructions.org_unit_id`, độc lập với cụm; phân quyền tầng 3 vẫn chạy trên `org_units` như cũ.
>
> **Hệ quả**: một công trình có thể thuộc **một** cụm hoặc không thuộc cụm nào. Cụm chỉ dùng để nhóm hiển thị và lọc — không mang ý nghĩa phân quyền. Chi tiết: `architecture-review.md` §10.5.

<details><summary>Nội dung câu hỏi gốc (giữ để truy vết)</summary>

Tài liệu đang mô tả hai điều khác nhau: CN-02.1 xếp **Cụm** vào *cấp quản lý* (Công ty / Xí nghiệp / Cụm), tức là một tầng trong bộ máy; còn kế hoạch triển khai lại nêu một bảng `construction_clusters` riêng, tức là một cách nhóm công trình.

**Cần Công ty cho biết**: một "Cụm công trình" (VD *Cụm Liên Mạc*, *Cụm Hà Đông*)…

| | Câu hỏi |
|---|---|
| a | Có **người phụ trách và nhân sự** thuộc cụm không, hay chỉ là tên gọi để nhóm các công trình gần nhau trên bản đồ? |
| b | Cụm có nằm trong **sơ đồ tổ chức** của Công ty không (tức là có xuất hiện ở phần Nhân sự MOD-04)? |
| c | Một công trình có bao giờ **thuộc hai cụm** không? |

**Vì sao hỏi**: nếu cụm là đơn vị tổ chức thì nó dùng chung bảng `org_units` với Xí nghiệp và phòng ban (quy tắc 7), và phân quyền theo đơn vị tự chạy. Nếu chỉ là cách nhóm thì phải có bảng riêng. Làm nhầm hướng thứ nhất sẽ **pha tạp sơ đồ tổ chức bằng những nút không phải đơn vị**, gỡ ra rất đau.

👉 Trong lúc chờ, phía phát triển đi **phương án tối giản**: mỗi công trình gắn **một đơn vị phụ trách** (`org_units`), chưa dựng bảng cụm. Thêm một khoá ngoại về sau là việc nhỏ; gỡ một cây tổ chức đã bị pha tạp thì không.

</details>

> ℹ **Ghi nhận về cách hỏi.** Phương án tối giản chọn hôm sáng hoá ra đúng hướng: câu trả lời là "chỉ là cách nhóm", nên việc phải làm thêm chỉ là một bảng và một khoá ngoại nullable — đúng như đã lượng trước. Nếu khi ấy đoán theo hướng "cụm là đơn vị tổ chức" và thêm loại nút vào `org_units` thì bây giờ phải gỡ chúng ra khỏi cây đang gánh cả phân quyền tầng 3 lẫn sơ đồ nhân sự.

---

## TÓM TẮT VIỆC CẦN CÔNG TY LÀM

| # | Mục | Việc cần làm | Hạn cần có |
|---|---|---|---|
| 1 | 🟡 **G8** | (a) **Tuyến sông + lý trình + tọa độ GPS** cho 19 điểm đo đã ánh xạ · (b) trả lời **khoảng trống API vs biểu tổng hợp** (7 điểm có trên biểu nhưng không có telemetry) · (c) xác nhận **3 cặp mã trùng giá trị** · (d) **danh mục toàn bộ công trình (Excel)** kèm mã | Trước khi nhập liệu ban đầu & nghiệm thu MOD-03 |
| 2 | 🟡 **G10** | Duyệt `report-templates-proposal.md` + gửi **file mẫu thật** của BC-11, BC-09, BC-05, BCNS-07 | Trước Phase báo cáo |
| 3 | 🟡 **G6** | File mẫu **2C-BNV** Công ty đang dùng (gửi kèm G10) | Trước Phase HRM |
| 4 | 🟡 **G5** | Mã số hệ thống văn bản: **riêng từng người hay chung**? + đề nghị bên `bhh40.net` cấp **token/SSO** thay vì lưu mã số + kế hoạch bật **HTTPS** | Trước Phase MOD-01 |
| 5 | 🟡 **G3-a** | Chốt cách xử lý **lượng mưa** ở v1 (PA A/B/C) | Trước Phase MOD-03 |
| 6 | ⚪ **G9-a** | Xác nhận **bộ mức ngưỡng** cảnh báo (3 mức đề xuất hay cấp I/II/III) | Trước khi cấu hình ngưỡng thật |
| 7 | 🟡 **G13** | **Bộ nhận diện cổng**: logo, favicon, màu, thông tin chân trang, liên kết mạng xã hội, GA/GTM, reCAPTCHA key | **Trước nghiệm thu cổng TTĐT (Phase 1)** |
| 8 | 🟡 **G14** | **Cây danh mục + menu cổng + nội dung 4 trang tĩnh** + số tài khoản biên tập cần cấp | **Trước nghiệm thu cổng TTĐT (Phase 1)** |
| ~~9~~ | ✅ **G15** | ~~"Cụm công trình" là đơn vị tổ chức hay cách nhóm?~~ | **ĐÃ ĐÓNG 19/8** — chỉ là cách nhóm → bảng riêng |

Trả lời theo mã mục, ví dụ: `G3-a: chọn PA B · G5: mã số riêng từng người, user tự nhập · G15: cụm có tổ trưởng, nằm trong sơ đồ tổ chức`.

Sau khi nhận confirm → cập nhật `function-spec.md`, `implement.md` và đóng mục tương ứng tại đây.

---

## PHẦN III — TRUY VẾT: CHỨC NĂNG NÀO CÒN CHỨA ĐIỂM CHƯA CHỐT

> Mục đích: dev nhìn 1 bảng là biết chức năng mình sắp code có "vùng chưa chốt" nào, mức độ ảnh hưởng ra sao, và **được phép làm tới đâu**.
> Ký hiệu mức ảnh hưởng: 🟥 **chặn code** (không viết được nếu chưa có trả lời) · 🟨 **code được nhưng chừa khe** (thiết kế phải hấp thụ được cả 2 nhánh trả lời) · 🟩 **chỉ chặn dữ liệu/nghiệm thu** (code xong hoàn toàn, chỉ thiếu số liệu thật để nhập).

| Chức năng | Mục mở | Mức | Vùng chưa chốt & cách xử lý tạm |
|---|---|:-:|---|
| **CN-01.7** Liên kết hệ thống văn bản | **G5** | 🟥 | **Mã số riêng từng người hay chung 1 mã?** Quyết định schema: `external_system_credentials(user_id, …)` **per-user** hay 1 dòng trong `settings` **toàn hệ thống** — 2 hướng khác nhau về cả bảng, UI lẫn phân quyền. Nếu Công ty xin được **token/SSO** thì bỏ hẳn việc lưu credential → đổi bản chất lần 2. **Không code phần lưu mã số trước khi có trả lời**; phần còn lại của MOD-01 làm bình thường |
| **CN-01.2** Danh mục nội dung | **G14** | 🟩 | Cây danh mục là **dữ liệu**, không phải mã. Chưa có sơ đồ của Công ty thì seed khung đề xuất ở G14, sửa qua giao diện — code xong hoàn toàn |
| **CN-01.5** Cấu hình giao diện | **G13** | 🟩 | Logo/màu/GA/GTM/mạng xã hội đọc từ `settings`, để trống vẫn chạy. Thiếu thì **cổng nghiệm thu bằng giá trị mặc định của lập trình viên** — không sai chức năng, sai diện mạo |
| **CN-02.1** Cấp quản lý & Cụm công trình | ~~G15~~ | ✅ | **Đã đóng 19/8**: cụm chỉ là cách nhóm → bảng `construction_clusters` + `constructions.cluster_id` nullable. ⛔ Không thêm loại nút vào `org_units` |
| **CN-03.1** Danh mục điểm đo | **G8** | 🟩 | Đã có tên + vai trò (G8b). Thiếu `river_name` / `chainage` / **tọa độ** của 19 điểm → cột đã có sẵn trong bảng, chỉ để `NULL` tới khi Công ty gửi |
| **CN-03.1** Danh mục loại chỉ số | **G3-a** | 🟨 | Giữ loại chỉ số "Lượng mưa" trong danh mục dù v1 chưa có nguồn — **không xóa khỏi enum/seed**, nếu chọn PA B (nhập tay) thì dùng lại ngay |
| **CN-03.2** Adapter & polling | **G3-a** | 🟨 | Thiếu endpoint mưa. `TelemetryAdapter` phải để **1 điểm cắm cho nguồn thứ 2**, không hard-code giả định "1 nguồn = 1 endpoint mực nước" |
| **CN-03.4** Biểu tổng hợp / realtime | **G3-a**, **G8** | 🟨 | Cột lượng mưa render `-`; nhóm theo **tuyến sông** cần `river_name` → tạm nhóm "Chưa phân tuyến" khi `NULL`, không crash |
| **CN-03.5** Báo cáo thủy văn | **G3-a**, **G10** | 🟨 | BC-05 có cột tổng lượng mưa (v1 `-`); layout chi tiết BC-05/11/12/13 chờ Công ty duyệt mẫu → làm **khung + trường dữ liệu** trước, chốt layout sau |
| **CN-03.6** Cảnh báo ngưỡng | **G9-a**, **G8** | 🟨 | **Số mức ngưỡng chưa chốt** → bắt buộc thiết kế mức dạng **danh mục có CRUD**, cấm enum cứng 3 mức. Ngoài ra chờ xác nhận **3 cặp mã trùng giá trị** (1 hay 2 bộ ngưỡng) |
| **CN-03.7** Thủy văn trên GIS | **G8** | 🟩 | Cần **tọa độ** mới hiển thị được. Code xong vẫn chạy — điểm chưa có tọa độ rơi vào danh sách "chưa số hóa vị trí" (đã có sẵn cơ chế ở CN-02.4) |
| **CN-02.1** Danh mục công trình | **G8** | 🟩 | Chờ danh mục công trình tổng thể (Excel) + tọa độ. Chỉ là nhập liệu |
| **CN-02.4** Bản đồ GIS công trình | **G8** | 🟩 | Như trên — thiếu tọa độ, không thiếu chức năng |
| **CN-02.5** Dashboard & wall mode | **G3-a** | 🟨 | Bỏ/ẩn widget lượng mưa ở v1; layout phải chịu được việc thiếu 1 khối |
| **CN-02.10** Báo cáo công trình | **G10** | 🟨 | BC-06/09/10: trường dữ liệu **đã chốt**, chỉ layout in ấn chờ duyệt |
| **CN-04.8** Báo cáo nhân sự | **G6**, **G10** | 🟨 | **BCNS-07 mẫu 2C-BNV chưa có file gốc** → làm 7 báo cáo còn lại trước, BCNS-07 để cuối. Đây là mẫu Bộ Nội vụ, **cấm tự chế** |
| **CN-05.3** Cấu hình hệ thống | **G9-a**, **G5**, **G3-a** | 🟨 | Bảng `settings` phải mở đủ để thêm tham số sau mà **không cần migration** (key-value có type) — đây chính là cách hấp thụ mọi câu trả lời còn lại |

**Chức năng KHÔNG chứa điểm mở nào — code thoải mái**: toàn bộ **Nhóm A / Core** (CN-05.1, 05.2, 05.4, 05.5, 05.6, 05.7) · CN-01.1, 01.3, 01.4, 01.6, 01.8, 01.9 · CN-02.2 (lịch sử sửa chữa + sự cố) · CN-02.3, 02.6, 02.7, **02.11** (tình hình vận hành) · CN-03.2 phần parser/polling/rate-limit · CN-03.3 (lưu trữ) · CN-04.1→04.7, 04.9.

> ⚠ **Đọc bảng này cho đúng.** 🟩 nghĩa là *viết mã được trọn vẹn*, **không** nghĩa là *bàn giao được*. Ba mục 🟩 của Phase 1 (CN-01.2, CN-01.5, CN-02.1) đều chặn **nghiệm thu** vì thiếu dữ liệu khởi tạo — mà nghiệm thu mới là thứ Công ty nhìn thấy. Đừng để tới tuần cuối mới đi xin.

> 📋 **18 điểm nghiệp vụ đã làm rõ cho Phase 1** — những chỗ spec không nói hoặc nói ra hai nghĩa (sửa bài đã xuất bản có phải duyệt lại không, bản ghi sửa chữa nhập sau khi xong thì bắt đầu ở trạng thái nào, tiền lưu VND hay triệu VND…) nằm ở `phase1-tracking.md` mục **"Nghiệp vụ — 18 điểm đã làm rõ trước khi code"**, kèm cột "ai quyết".
