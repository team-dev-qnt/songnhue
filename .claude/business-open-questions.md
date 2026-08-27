> Cập nhật **2026-08-21** (bản 4 — **nén Phần I-A**; xem lý do ngay đầu phần đó. Bản 3 ngày 19/8 mở 3 mục mới khi lập kế hoạch Phase 1: **G13, G14, G15**; **G15 đóng ngay trong ngày**).
> ⛔ **Phần I-B KHÔNG nén và không được nén**: câu trả lời đợt 2 nhận **qua trao đổi trực tiếp**, không có văn bản gốc — đây là **bản ghi duy nhất** của những gì Công ty đã chốt.
> ✅ **ĐỢT 1 (mục A–F) ĐÃ ĐÓNG** — Công ty trả lời đầy đủ ngày 12/8/2026 (`docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md`), đã đồng bộ vào `function-spec.md`, `implement.md`, `architecture-review.md` §8.
> ✅ **ĐỢT 2 — ĐÃ ĐÓNG 9/12 mục**: **G1, G2, G3 (phần lớn), G4, G7, G8b, G9, G11, G12** → xem **Phần I-B**, đã đồng bộ vào `function-spec.md` v2.2.
> ⬜ **CÒN MỞ 7 mục**: **G3-a** (lượng mưa) · **G5** (mã số hệ thống văn bản) · **G6** (mẫu 2C-BNV) · **G8** (tuyến sông/lý trình/tọa độ + danh mục công trình) · **G9-a** (bộ mức ngưỡng) · **G10** (duyệt format báo cáo) · ⭐ **G13** (bộ nhận diện cổng) → xem **Phần II**.
> ✅ **G14 đóng 27/8/2026** — Công ty ban hành *"YÊU CẦU CHỈNH SỬA WEBSITE" v1.0* (`docs_origin/nghiem_thu_phase1.md`) với cây nội dung chuẩn 7 mục cấp 1 ở §3. Đã dựng vào CSDL ở `V202608271031`; xem `master-tracking.md` WS-24.
> ⬜ **MỞ MỚI 27/8 — 10 mục `OI-01`→`OI-10`** ở §9 của chính tài liệu ấy. Chúng KHÔNG chặn code (khung đã dựng đủ theo §7) nhưng chặn **đấu nối dữ liệu và nghiệm thu**: `OI-01`/`OI-02` API mực nước & vận hành trạm bơm · `OI-03` danh sách 10 cống trục chính · `OI-05` 7 hay 8 Xí nghiệp · `OI-07` KMZ cho tải hay nhúng viewer. Phía phát triển đã trả lời `OI-01`/`OI-02`/`OI-07` — xem WS-24 T24.23→T24.25.
> ✅ **G15 đóng 19/8/2026** — cụm công trình chỉ là cách nhóm, không phải đơn vị tổ chức.
> ✅ **KHÔNG CÒN MỤC NÀO CHẶN CODE.** G8b — mục chặn cuối cùng của MOD-03 — đã đóng ngày 12/8/2026. G13 chặn **nghiệm thu** cổng TTĐT chứ không chặn code; G14 đã đóng.
> Ký hiệu: 🔴 chặn thiết kế/code · 🟡 cần trước khi làm module liên quan · ⚪ chốt sau được.

---

## PHẦN I-A — ĐỢT 1 (A–F): ĐÃ ĐÓNG 12/8/2026

> 📌 **Bản này đã nén ngày 21/8/2026.** Bảng đầy đủ 33 dòng trước đây có hai cột, cả hai đều có nguồn khác giữ nguyên vẹn nên không cần lưu hai lần:
> **Công ty trả lời** → nguyên văn ở `docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md` · **Tác động đã áp dụng** → đã ngấm vào `function-spec.md` v2.2, mỗi chức năng bị bỏ đều có bia mộ tại chỗ (CN-02.8, CN-02.9, `operation_logs`, `irrigation_zones`…).
> Giữ lại ở đây phần **không suy ra được từ hai nguồn kia**: quyết định nào **cắt bỏ phạm vi** — vì đó là câu hỏi sẽ được hỏi lại lúc nghiệm thu ("vì sao hệ thống không có chức năng X?").

### Đã CẮT khỏi phạm vi — nêu rõ để trả lời được lúc nghiệm thu

| Mã | Công ty quyết | Hệ quả trong hệ thống |
|---|---|---|
| **A1** | Tạm bỏ quản lý kế hoạch tưới tiêu / vụ mùa | Bỏ BC-04, BC-07; M3.18 thành "so sánh theo kỳ", không có bảng kế hoạch |
| **B1 · F1** | **Bỏ nhật ký vận hành**, thay bằng **Lịch sử các lần sửa chữa** do Admin/người được phân quyền nhập | ❌ CN-02.8; bỏ `operation_logs`, `machine_run_records`, workflow duyệt nhật ký, mã lỗi OPS-2001/2003. CN-02.2 nâng thành chức năng ghi nhận chính; bỏ BC-01/02/03/08, thêm BC-09/BC-10 |
| **B4** | Tạm bỏ trạng thái tổ máy realtime | Không hứa realtime; GIS popup/KPI dùng trạng thái công trình + nhãn thời điểm |
| **B5** | Bỏ chức năng liên quan diện tích tưới tiêu | Bỏ trường "Diện tích tưới tiêu (ha)" và mọi thống kê/công thức theo diện tích |
| **B7** | Trước mắt **thông báo qua Website** tới Ban điều hành + người quản lý công trình trực tiếp; SMS để giai đoạn sau | Kênh chính = in-app + email; `SmsSender` giữ dạng interface, mặc định tắt |
| **D1** | — | Phase 1 tắt bình luận tự do; chỉ khảo sát/góp ý kiểm duyệt 100% |
| **D3** | **Chỉ tiếng Việt** | Không i18n nội dung |
| **D4** | **Không migrate website cũ**, làm thủ công | Bỏ hạng mục migration khỏi kế hoạch go-live |
| **F3** | Lưu vực / khu tưới tiêu = **trường tham chiếu văn bản** | Bỏ CRUD danh mục, bảng `irrigation_zones`, polygon GIS riêng |
| **C4** | Chỉ lưu trữ thông tin lương (mã hoá 🔒) | **Không** tính lương, **không** chấm công |

### Đã ĐỔI bản chất

| Mã | Công ty quyết | Hệ quả |
|---|---|---|
| **E3** | Hệ thống văn bản điều hành là **hệ thống độc lập** — lưu account/password của user, cho **1 link tự động đăng nhập** | ⭐ CN-01.7 đổi bản chất: bỏ đồng bộ danh sách văn bản + bảng `external_documents`; thêm `external_system_credentials` (AES-256-GCM) + auto-login. *(Chi tiết cách đăng nhập còn mở — **G5**)* |
| **F2** | Bản ghi thuỷ văn chỉ **2 mức: Hợp lệ / Nghi ngờ**; Nghi ngờ **vẫn ghi data** + báo để quản trị duyệt/xoá | CN-03.2 sửa lại; thêm màn hình "Dữ liệu nghi ngờ". ⚠ Kéo theo quy tắc 14 ở `CLAUDE.md`: mọi truy vấn báo cáo/alert **phải lọc `HOP_LE`** |
| **B8** | Màn hình lớn = **TV 85" 4K**, có thể kèm máy chiếu 2K / Full HD+ | Wall mode base 3840×2160, kiểm fallback 1920×1080 và 2560×1440 |
| **E3** | Mẫu báo cáo: **đề xuất format để Công ty xây dựng** | → `report-templates-proposal.md`, chờ duyệt (**G10**) |
| **E3** | Tài liệu API telemetry: **đã có** | ✅ Đã đấu nối. Nhịp gọi + giới hạn nguồn: **Phần I-B (G3)** |

### Đưa vào biến cấu hình thay vì chốt cứng

**C1** thông số phép năm · **D5** retention mặc định 5 năm (`hydro.retention.detail-years`) · **E3** số lượng điểm đo/công trình/user · **F5** giờ hành chính mặc định 8h–17h.
→ Tất cả nằm ở bảng `settings` có UI sửa (quy tắc 12 ở `CLAUDE.md`).

### Đồng ý theo đề xuất, không đổi gì so với thiết kế đã trình

**A2b** quan hệ điểm đo ↔ công trình n–n có vai trò (CN-03.1) · **A3** hồ sơ kênh mương/đê điều + layer GIS LineString · **B2** nhập bù (không còn ý nghĩa sau B1) · **B3** uỷ quyền duyệt có thời hạn + audit (CN-04.9) · **B6** chuẩn hoá đơn vị đo — m(3)/mm(1)/m³/s(3), **nguồn trả cm → chia 100** · **C2** duyệt phép 1 cấp, cấp 2 theo ngưỡng ngày mặc định tắt · **C3** cấp tài khoản toàn bộ CBNV, có "người tạo hộ" · **D2** 2 vai trò biên tập · **E1 · F6** Restore qua UI + 2FA + xác nhận nhiều bước (`architecture-review.md` §7.3) · **F4** giữ M2.12, M2.13 · **F7** v1 nhận GeoJSON/KMZ, Shapefile → convert · **F8** NFR lấy mức chặt hơn làm mục tiêu nội bộ.

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

### ~~G14~~. ✅ **ĐÃ ĐÓNG 27/8/2026** — Công ty ban hành cây nội dung chuẩn

> **Trả lời**: `docs_origin/nghiem_thu_phase1.md` — *"YÊU CẦU CHỈNH SỬA WEBSITE" v1.0*, trạng thái *Ban hành để thực hiện*. §3 cho **cây nội dung chuẩn 7 mục cấp 1**, và §2 ra một ràng buộc bằng lời: *"Menu chính, footer, các card chuyên mục và cây nội dung phải dùng CHUNG một hệ phân loại"*.
>
> **Áp dụng**: `V202608271031__cms_site_taxonomy_v2.sql` thay khung đề xuất cũ. Menu HEADER 8 mục cấp 1 + 12 mục cấp 2, FOOTER 7 mục cùng hệ phân loại. Khối "Chuyên mục & lĩnh vực" của trang chủ nay **đọc thẳng cây menu** thay vì đọc `categories` — hai nguồn không trôi ra khỏi nhau được nữa.
>
> **Hệ quả cần biết**: trong 4 trang tĩnh khung cũ, **ba trang mất mục menu** vì bị trang thật ở đường dẫn khác thay thế (Cơ cấu tổ chức → đọc `org_units`; Liên hệ → `/lien-he`; Chức năng nhiệm vụ → gộp vào Tổng quan). Chúng bị xoá mềm có điều kiện — xem `architecture-review.md` §10.61 mục 2.
>
> ⬜ **Ý 4 của câu hỏi gốc vẫn CHƯA có trả lời**: *cần bao nhiêu tài khoản Biên tập viên / Quản trị nội dung, ai đăng bài đầu tiên*. Tài liệu nghiệm thu không nhắc tới, và `OI-06` (§9) hỏi một câu gần đó — *cơ chế cấp tài khoản đăng nhập: ai cấp, bao nhiêu nhóm quyền*. Gộp vào `OI-06` để hỏi một lần.

<details><summary>Nội dung câu hỏi gốc (giữ để truy vết)</summary>

#### G14. 🟡 Sơ đồ danh mục nội dung, menu cổng và nội dung trang tĩnh

D4 đã chốt **không migrate website cũ**, Công ty tự nhập lại nội dung. Nhưng phần **khung** thì phía phát triển phải dựng và bàn giao sẵn, nếu không thì đến ngày nghiệm thu mới ngồi nghĩ cây danh mục.

**Cần Công ty chốt**:

1. **Cây danh mục nội dung** (tối đa 3 cấp) — ví dụ khung để Công ty sửa: *Giới thiệu* (Lịch sử, Cơ cấu tổ chức, Chức năng nhiệm vụ) · *Tin tức* (Tin hoạt động, Tin chuyên ngành, Thông báo) · *Công trình thuỷ lợi* · *Văn bản* · *Thông tin thuỷ văn* · *Liên hệ*.
2. **Menu header và menu footer** — có thể khác cây danh mục (menu thường gọn hơn).
3. **Nội dung các trang tĩnh**: Giới thiệu, Chức năng nhiệm vụ, Cơ cấu tổ chức, Liên hệ. Gửi bản Word cũng được.
4. **Ai là người đăng bài đầu tiên** và cần bao nhiêu tài khoản Biên tập viên / Quản trị nội dung — để cấp tài khoản khi bàn giao.

👉 Nếu Công ty chưa chốt kịp, phía phát triển sẽ **seed khung đề xuất ở trên** để cổng chạy được, Công ty sửa sau qua giao diện — không phải sửa mã.

</details>

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
| 8 | ✅ ~~**G14**~~ | ~~Cây danh mục + menu cổng + nội dung 4 trang tĩnh~~ **đóng 27/8** bằng §3 của văn bản nghiệm thu. ⬜ Còn lại: **số tài khoản biên tập cần cấp** → gộp vào `OI-06` | Đã dựng vào `V202608271031` |
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
| **CN-01.2** Danh mục nội dung | ✅ ~~G14~~ | 🟩 | Cây danh mục là **dữ liệu**, không phải mã. Sơ đồ chính thức nhận 27/8 (§3 văn bản nghiệm thu), dựng ở `V202608271031`; sửa tiếp qua giao diện |
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
| **CN-02.11** Tình hình vận hành — *giá trị seed* | **G4** (đã đóng) | 🟩 | ⚠ **Chức năng đã chốt trọn vẹn ở G4, nhưng 4 giá trị seed thì chưa ai duyệt.** Migration `V202608221029` seed `MT / ĐK / ĐTTL / ĐTHL` kèm cột `mapped_status` do phía phát triển tự đặt — G4 chỉ nói "seed 4 mã", không nói mã nào ánh xạ sang trạng thái nào. Hai chỗ cần Công ty xác nhận trước nghiệm thu: (a) **`ĐK` (Đóng kín) → `NGUNG_MUA_VU`** — đóng cống là ngừng mùa vụ, hay chỉ là một thao tác vận hành bình thường? (b) **`ĐTHL`** đang mang tên *"Đóng xả thuỷ điện"* trong khi chữ viết tắt gợi *"Đóng tiêu hạ lưu"*. Sửa được qua giao diện danh mục, **không cần deploy** |
| **CN-02.10** Báo cáo công trình | **G10** | 🟨 | BC-06/09/10: trường dữ liệu **đã chốt**, chỉ layout in ấn chờ duyệt |
| **CN-04.8** Báo cáo nhân sự | **G6**, **G10** | 🟨 | **BCNS-07 mẫu 2C-BNV chưa có file gốc** → làm 7 báo cáo còn lại trước, BCNS-07 để cuối. Đây là mẫu Bộ Nội vụ, **cấm tự chế** |
| **CN-05.3** Cấu hình hệ thống | **G9-a**, **G5**, **G3-a** | 🟨 | Bảng `settings` phải mở đủ để thêm tham số sau mà **không cần migration** (key-value có type) — đây chính là cách hấp thụ mọi câu trả lời còn lại |

**Chức năng KHÔNG chứa điểm mở nào — code thoải mái**: toàn bộ **Nhóm A / Core** (CN-05.1, 05.2, 05.4, 05.5, 05.6, 05.7) · CN-01.1, 01.3, 01.4, 01.6, 01.8, 01.9 · CN-02.2 (lịch sử sửa chữa + sự cố) · CN-02.3, 02.6, 02.7, **02.11** (tình hình vận hành — *chức năng* thoải mái; riêng **giá trị seed** của 4 mã cần Công ty duyệt trước nghiệm thu, xem bảng trên) · CN-03.2 phần parser/polling/rate-limit · CN-03.3 (lưu trữ) · CN-04.1→04.7, 04.9.

> ⚠ **Đọc bảng này cho đúng.** 🟩 nghĩa là *viết mã được trọn vẹn*, **không** nghĩa là *bàn giao được*. Ba mục 🟩 của Phase 1 (CN-01.2, CN-01.5, CN-02.1) đều chặn **nghiệm thu** vì thiếu dữ liệu khởi tạo — mà nghiệm thu mới là thứ Công ty nhìn thấy. Đừng để tới tuần cuối mới đi xin.

> 📋 **18 điểm nghiệp vụ đã làm rõ cho Phase 1** — những chỗ spec không nói hoặc nói ra hai nghĩa (sửa bài đã xuất bản có phải duyệt lại không, bản ghi sửa chữa nhập sau khi xong thì bắt đầu ở trạng thái nào, tiền lưu VND hay triệu VND…) nằm ở `phase1-tracking.md` mục **"Nghiệp vụ — 18 điểm đã làm rõ trước khi code"**, kèm cột "ai quyết".
