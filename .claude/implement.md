# IMPLEMENTATION PLAN — GOM NHÓM MODULE & TỔ CHỨC CODING

> Kèm theo `function-spec.md` (**v2.2**). Mục tiêu: gom nhóm theo điểm chung về logic / DB / business để code 1 lần dùng nhiều nơi, tránh mỗi module tự chế một kiểu.
>
> ⚠ **Cập nhật 2026-08-06**: đồng bộ cấu trúc module theo SRS — thủy văn tách thành **MOD-03** (module code `hydro`), tích hợp văn bản gộp vào **MOD-01**, HRM = **MOD-04**. Nhóm code vẫn giữ nguyên tư duy pattern-based; chỉ tách thêm module `hydro` khỏi `operations`.
>
> ⚠ **Cập nhật 2026-08-12 (áp dụng BOQ đợt 1)**: **bỏ Nhật ký vận hành** → Nhóm C3 nhẹ đi đáng kể (bỏ `operation_logs`, `machine_run_records`, workflow duyệt nhật ký, bảng agg theo ca); **bỏ kế hoạch vụ mùa**; **bỏ `irrigation_zones`**; **CN-01.7 đổi từ "đồng bộ văn bản" sang "lưu credential + auto-login"** (bỏ `external_documents`, bỏ job đồng bộ); trạng thái bản ghi thủy văn còn **2 mức**; **SMS hoãn phase sau**. Chi tiết: `function-spec.md` v2.1 + `business-open-questions.md` Phần I-A.
>
> ⚠ **Cập nhật 2026-08-12 (áp dụng confirm đợt 2 — mục G)**: **bỏ `incidents`** → sự cố gộp vào `maintenance_logs` (G1 = PA A) · **không** làm màn hình nhập số liệu vận hành tháng (G2) · **chốt cron polling 2'/phút lẻ + rate-limit theo khung 10'** và **trạm mất tín hiệu → GIS xám** (G3) · **thêm 2 bảng tình hình vận hành cống nhập tay có CRUD danh mục mã** (G4) · **audit retention 5 năm + job kết xuất lưu trữ có checksum** (G7) · **màn hình cấu hình ngưỡng là hạng mục nghiệm thu** (G9) · **nhóm người nhận cảnh báo cấu hình được** (G11) · **NFR thành cam kết nghiệm thu → cần load test** (G12). Chi tiết: `function-spec.md` v2.2 + `business-open-questions.md` Phần I-B.

---

## 1. NGUYÊN TẮC GOM NHÓM

Nhìn xuyên qua 5 module, có **6 pattern lặp lại** — đây là căn cứ gom nhóm, không gom theo "module nghiệp vụ" một cách máy móc:

| # | Pattern chung | Xuất hiện ở |
|---|---|---|
| P1 | **Workflow duyệt / state machine** | Bài viết (CN-01.1), Nghỉ phép (CN-04.9), Phản hồi/khảo sát (CN-01.6), Duyệt dữ liệu thủy văn nghi ngờ (CN-03.2), **trạng thái xử lý sự cố trên `maintenance_logs`** (CN-02.2 — 3 trạng thái, chốt G1). *(Nhật ký vận hành + phiếu sự cố riêng đã bỏ 12/8/2026)* |
| P2 | **Cấu trúc cây phân cấp** | Danh mục nội dung (3 cấp), Thư mục media (3 cấp), Phân cấp công trình (4 cấp), Sơ đồ tổ chức (5 cấp), Menu nested |
| P3 | **File đính kèm + versioning + hạn hiệu lực** | Media CMS, Tài liệu công trình, Hồ sơ nhân viên, Template báo cáo, Layer GIS |
| P4 | **Notification đa kênh** (**v1: in-app + email**; SMS/web push để adapter, mặc định tắt — B7) | Cảnh báo thủy văn (MOD-03), dữ liệu nghi ngờ, duyệt bài, sự cố (CN-02.2), liên hệ mới, nghỉ phép, HĐ/chứng chỉ sắp hết hạn, thông báo hệ thống. **Người nhận cảnh báo = nhóm cấu hình "Ban điều hành" ∪ người phụ trách đơn vị quản lý công trình liên quan** (G11) |
| P5 | **Async job + cron scheduler** | Xuất báo cáo (BC + BCNS + thủy văn), tổng hợp kỳ, polling API, hẹn giờ đăng bài, quét hạn, đồng bộ văn bản |
| P6 | **Data scoping theo Xí nghiệp/Đơn vị** | Toàn bộ MOD-02/MOD-03 (Cán bộ vận hành/Quản lý XN/Kỹ thuật), báo cáo, MOD-04 (Quản lý đơn vị) |

→ **Xây các pattern này thành shared service/library trong Core trước**, các module nghiệp vụ chỉ khai báo cấu hình (trạng thái, ai duyệt, kênh notify...).

---

## 2. GOM NHÓM IMPLEMENT — 5 MODULE, 4 NHÓM CODE

### NHÓM A — CORE PLATFORM (làm đầu tiên, mọi nhóm phụ thuộc)

Gồm: MOD-05 + toàn bộ shared services rút ra từ P1–P6.

| Thành phần | Nội dung | Phục vụ |
|---|---|---|
| Auth & RBAC | Access token 30' + Refresh rotation (httpOnly cookie) + BCrypt, denylist bảng DB; Role + Permission (ma trận chi tiết theo màn hình — M5.3); annotation/middleware check quyền + **scope filter theo đơn vị (P6)**; quản lý phiên + đăng xuất từ xa (M5.14); chính sách mật khẩu cấu hình (M5.15); cảnh báo đăng nhập bất thường (M5.16) | Tất cả |
| User & Org Unit | Bảng `users`, `org_units` (cây đơn vị dùng CHUNG cho MOD-02 Xí nghiệp và MOD-04 phòng ban — 1 bảng, đừng tách 2) | MOD-02, 03, 04 |
| Tree helper (P2) | Base entity cây: `parent_id` + `path` (materialized path) + `sort_order`; API move/reorder | Danh mục, media folder, công trình, org chart, menu |
| Attachment service (P3) | 1 bảng `attachments` polymorphic (`owner_type`, `owner_id`, `folder`, `version`, `valid_until`); upload MinIO, validate, scan malware, versioning, preview | CMS media, tài liệu CT, hồ sơ NV |
| Workflow engine (P1) | Bảng `workflow_transitions` + service generic: states, transitions, role được phép, hook (notify, audit) theo entity type | Bài viết, nghỉ phép, duyệt dữ liệu nghi ngờ, trạng thái xử lý sự cố |
| Notification service (P4) | 1 API `notify(event, targets, channels)`; **v1 bật In-app + Email(SMTP)**; adapter SMS/WebPush viết sẵn interface, **mặc định tắt** (B7); template + **resolver người nhận theo G11** (nhóm "Ban điều hành" lưu ở `settings` ∪ người đứng đầu/phó phụ trách `org_units` của công trình liên quan, khử trùng lặp, loại tài khoản khóa); thông báo hệ thống (M5.13) | Tất cả |
| Job & Scheduler (P5) | **DB-backed queue** (SKIP LOCKED, transactional) + worker **in-process** (bounded pool, v1); Pending/Processing/Completed/Failed/Cancelled, retry 3; cron Spring Scheduler + **ShedLock** (giữ sẵn, bật khi ≥2 node) + chống overlapping run | Báo cáo, polling, hẹn giờ, quét hạn, đồng bộ văn bản |
| Audit log | Interceptor global: user, timestamp, action, old/new JSON; **append-only + hash chain** (M5.7, không sửa/xóa); **retention 5 năm + job kết xuất lưu trữ CSV/Parquet + checksum SHA-256 lên MinIO trước khi xóa khỏi bảng nóng, hash chain nối tiếp qua ranh giới kết xuất** (G7) | Tất cả (NFR-07) |
| System config | Bảng `settings` key-value có type; UI quản trị; **xuất/nhập cấu hình (M5.17)**; health-check dịch vụ/API tích hợp (M5.12) | Tất cả |
| Backup/Restore | **`pg_dump` hàng đêm** (bản tối giản — RPO ≤ 24h, RTO ≤ 4h, `architecture-review.md` §6.5) + backup theo yêu cầu; **restore qua UI có bảo vệ nhiều lớp (M5.11)** + maintenance mode | Vận hành |

**DB dùng chung của Nhóm A**: `users`, `roles`, `permissions`, `org_units`, `attachments`, `audit_logs`, `notifications`, `jobs`, `settings`, `workflow_transitions`, `sessions`, `token_denylist`.

### NHÓM B — CONTENT (MOD-01, gồm CMS + tích hợp văn bản)

Điểm chung: nội dung hiển thị public, SEO, workflow xuất bản, ít liên quan dữ liệu vận hành.

- Article + Category + Tag (dùng Tree helper + Workflow engine + Attachment).
- Banner, Footer, Menu, Site config, trang đặc biệt, tìm kiếm (CN-01.8) — gom nhóm `site_configuration`.
- Contact + Phản hồi/khảo sát (CN-01.6): pattern "tiếp nhận từ public → hàng đợi xử lý/duyệt nội bộ" → chung service `inbound_submission`.
- **Liên kết hệ thống văn bản điều hành (CN-01.7)** — ⭐ *đổi hoàn toàn theo E3 (12/8/2026)*: **không** đồng bộ dữ liệu, **không** job định kỳ. Chỉ: bảng `external_system_credentials` (mã hóa AES-256-GCM qua `CryptoService` của Core) + endpoint sinh **form auto-submit** đăng nhập sang hệ thống nguồn + màn hình người dùng tự nhập/cập nhật/xóa mã số. Mọi thao tác ghi security event. Cấu hình URL/field từ env.
- **DB**: `articles`, `article_versions`, `categories`, `article_categories`, `feedbacks`, `contacts`, `banners`, `menus`, `external_system_credentials`. Media dùng `attachments` của Core. ~~`external_documents`~~ (bỏ), ~~`comments`~~ (D1 — phase 1 không bật bình luận).
- Frontend: nhóm duy nhất cần **SSR/Static** (SEO); tách app public khỏi admin SPA ngay từ đầu.

### NHÓM C — OPERATIONS + HYDRO (MOD-02 + MOD-03) — nhóm nặng nhất, **2 module code tách riêng**

**C1. Master data công trình** (module `operations`, làm trước — mọi thứ tham chiếu):
- `constructions` (chung cho trạm bơm + cống + đê + kênh: cột chung + bảng extension `pump_station_specs`, `sluice_specs`, tùy chọn `dyke_specs`/`canal_specs`), `construction_clusters`, cấp quản lý + đơn vị phụ trách, trạng thái, tài liệu (Attachment), tọa độ GIS + **`river_name` / `chainage` (lý trình K..+..)**, nhật ký thay đổi hồ sơ.
- ⭐ **`maintenance_logs` (CN-02.2) là trọng tâm mới của C1** — thay thế **cả** nhật ký vận hành **và** phiếu sự cố: loại công việc (5 giá trị, gồm **Khắc phục sự cố**), **`severity`** (bắt buộc khi là sự cố), **`handling_status`** (MOI/DANG_XU_LY/DA_XU_LY, đổi qua Workflow engine), nội dung, hạng mục/thiết bị, ngày BĐ–HT, đơn vị thực hiện, **chi phí BigDecimal**, nguồn vốn, kết quả nghiệm thu, **`alert_event_id` nullable**, attachment. Quyền `ops:maintenance:*` + audit.
- ⭐ **`operation_status_codes` + `construction_operation_status` (CN-02.11 — G4)**: danh mục mã tình hình vận hành **CRUD đầy đủ** (`code`, `name`, `has_param`+đơn vị, **`color`**, **`mapped_construction_status`** nullable, `sort_order`, `is_active`) + bảng ghi nhận **append có lịch sử** (`construction_id`, `code_id`, `param_value` NUMERIC, `effective_at` timestamptz, `updated_by`, `note`). Seed 4 mã MT/ĐK/ĐTTL/ĐTHL. **Cấm hard-code enum** — Công ty sẽ thêm mã về sau. Màn hình nhập nhanh dạng bảng cho trực ban.
- ⭐ **Trạng thái công trình là giá trị dẫn xuất**, không phải cột nhập tay: service tính theo thứ tự sự cố đang mở → bảo trì đang thực hiện → cảnh báo ngưỡng → ánh xạ từ mã tình hình vận hành → Bình thường (CN-02.1).
- ❌ Bỏ `irrigation_zones` (F3 — lưu vực chỉ là trường text trên `constructions`); ❌ bỏ trường diện tích tưới tiêu (B5); ❌ **bỏ `incidents` + `incident_updates`** (G1).
- Là dependency của Widget CMS, Dashboard, và layer GIS.

**C2. Data pipeline thủy văn — module `hydro` (MOD-03, tách riêng theo SRS)**:
- `measurement_types` (loại chỉ số + đơn vị), `stations` (điểm đo + **mã ánh xạ API bên thứ 3**, 1–1, + `river_name`/`chainage`/`position_role`/`is_interpolated`), `station_constructions` (n–n vai trò **TL/HL/Bể hút/MN sông/Mưa** — ✅ confirmed A2b + G8b).
- ⭐ **Seed 19 điểm đo từ bảng ánh xạ G8b** (`function-spec.md` CN-03.1) ngay ở migration đầu của `hydro` — **cấm sinh điểm đo tự động từ response**. Enum `position_role` phải có **`MN_SONG`**; điểm `MN_SONG` được phép **không có dòng `station_constructions` nào** (trạm thủy văn tham chiếu) — viết test cho nhánh này, đừng để `NOT NULL`/inner join làm rớt.
- ⛔ **Cấm rule "TL > HL"** ở mọi tầng (validate ingest, cảnh báo, báo cáo): dữ liệu thật có cặp đảo hợp lệ. Validate chỉ theo **từng điểm đo × trục thời gian**.
- 📋 **Task nghiệm thu dữ liệu (Phase 2)**: theo dõi 3 cặp mã đang trùng giá trị (`F02030`/`F02031`, `F01707`/`F01820`, `F01672`/`F01965`) trong ≥3 ngày. Luôn bằng nhau tuyệt đối ⇒ nghi **1 cảm biến 2 mã** → hỏi lại Công ty trước khi gắn 2 bộ ngưỡng độc lập (G8 mục 3).
- `api_sources` (credential AES-256-GCM), Polling worker (Job/Scheduler Core, retry/backoff, cấu hình được), `hydro_raw_logs` (raw append-only), `hydro_readings` (time-series, partition tháng, retention 5 năm **config được**, **`quality` chỉ 2 mức HOP_LE/NGHI_NGO** — F2), `hydro_latest` (1 dòng/điểm đo, UPSERT — thay cache Redis cho widget/GIS/dashboard, graceful degradation), `sync_logs` (nhật ký đồng bộ M3.16).
- ⭐ **Adapter nguồn thật `Bhh40Adapter`** (đã đấu nối thử OK 12/8/2026): `GET getmn.aspx?key=<mã số>;` (**dấu `;` cuối key là bắt buộc**) → body text, bản ghi phân tách bằng `<br>`, mỗi bản ghi `<mã F#####>;dd/MM/yyyy;HH:mm;value=<cm>;`, đuôi response có 1 trang HTML rỗng phải cắt bỏ. Chuỗi `not.working` = lỗi xác thực. Đơn vị **cm → chia 100 ra m (BigDecimal, scale 3)**; giờ VN → UTC. Đặc tả 10 quy tắc parse ở `function-spec.md` CN-03.2 — **viết unit test cho từng quy tắc, gồm cả response lỗi và dòng rác**.
- Giữ interface `TelemetryAdapter` + `MockAdapter` (chọn qua config) để test/CI không phụ thuộc mạng.
- ⭐ **Lịch polling — chốt G3**: cron mặc định **`45 1/2 * * * *`** (2 phút/lần vào các phút lẻ, **giây 45** để vượt mốc `x1:30` khi nguồn bắt đầu đẩy dữ liệu của khung mới). Cron nằm trong `settings`, không hard-code.
- ⭐ **Rate-limit trước khi mở HTTP (G3)**: tính `frame = floor(now / 10')`; nếu **toàn bộ** điểm đo đang hoạt động đã có bản ghi thuộc `frame` → **skip**, ghi `sync_logs = SKIPPED_UP_TO_DATE` (DEBUG, không tính lỗi). ⚠ Điều kiện dừng là *đủ toàn bộ trạm*, **không** phải "đã nhận bản ghi đầu tiên" — nguồn trả rải rác trong cửa sổ `x1:30 → x8:30`. Viết test cho đúng nhánh này.
- ⭐ **Phát hiện trạm mất tín hiệu (G3)**: job phụ đánh dấu `stations.status = MAT_TIN_HIEU` khi không có bản ghi mới quá N khung (mặc định 3 ≈ 30', config) → GIS marker **xám**, badge bảng realtime, loại khỏi đánh giá ngưỡng; có dữ liệu lại → tự phục hồi + ghi log. Phân biệt rõ với **lỗi nguồn toàn phần** (`not.working`/timeout → alert về nguồn, không đánh dấu từng trạm).
- ⛔ **Nguồn KHÔNG có API lịch sử** (Công ty đã chấp nhận rủi ro — G3) → `hydro_raw_logs` phải ghi **nguyên văn response trước khi parse**; poller là điểm bắt dữ liệu duy nhất, mất là mất vĩnh viễn. Bổ sung **alert khi không có bản ghi mới quá N phút** vào hạng mục monitoring Phase 0. Nhịp 2' ⇒ ~720 response/ngày → `hydro_raw_logs` **partition tháng + retention riêng ngắn hơn `hydro_readings`**.
- ⬜ **Lượng mưa**: v1 **không có nguồn** (API chỉ có `getmn.aspx`). Giữ `measurement_types` có "Lượng mưa" + chừa chỗ cắm adapter; cột báo cáo hiển thị `-`. Chờ **G3-a** trước khi làm màn hình nhập tay.
- Màn hình **"Dữ liệu nghi ngờ"**: danh sách bản ghi `NGHI_NGO` + Duyệt/Xóa (audit) + thông báo cho Quản trị khi phát sinh.
- **Alert engine**: `alert_rules` (theo điểm đo × chỉ số, mức, delay) + `alert_events`; đánh giá ngay sau ghi reading; phát qua Notification service. ⭐ **G9**: **màn hình cấu hình ngưỡng đầy đủ là hạng mục nghiệm thu** (Công ty tự nhập số liệu thật) — điểm đo chưa cấu hình → nhãn "chưa cấu hình ngưỡng" + **không phát cảnh báo**; có danh sách "Điểm đo chưa cấu hình ngưỡng". Số mức ngưỡng thiết kế dạng **danh mục**, không enum cứng (chờ G9-a).
- Báo cáo thủy văn (định kỳ/theo yêu cầu/mùa vụ — dùng Job queue).
- Chạy **song song** với C1 (chỉ cần thống nhất `station_id` + quan hệ station↔construction trước).

**C3. GIS + Dashboard + Báo cáo — module `operations` (đã THU GỌN mạnh theo BOQ 12/8/2026, phụ thuộc C1 + C2)**:
- ❌ **Bỏ nhật ký vận hành** (`operation_logs`, `machine_run_records`, workflow duyệt, agg theo ca) — B1/F1.
- ❌ **Bỏ `incidents` + `incident_updates`** — chốt **G1 = PA A**: sự cố là bản ghi `maintenance_logs` (thuộc C1), không phải module riêng. C3 chỉ còn *đọc* để hiển thị/báo cáo. Cảnh báo ngưỡng **không auto sinh** bản ghi — chỉ nút "Tạo bản ghi khắc phục" trên màn hình cảnh báo, prefill `alert_event_id`.
- Báo cáo: `report_templates` + Job queue; danh mục thu gọn còn **BC-09/BC-10/BC-06** (từ `maintenance_logs` + `constructions` + `alert_events`) và **BC-05/BC-11/BC-12/BC-13** (thuộc `hydro`); bảng agg chỉ còn tổng hợp chi phí/số lượt bảo trì theo kỳ. Format mẫu: `report-templates-proposal.md`.
- ⭐ **BC-11 "Biểu tổng hợp mực nước theo tuyến sông"** dùng chung layout với **wall mode 4K** — làm 1 lần, dùng 2 nơi. Ưu tiên cao vì đây là màn hình Trực ban đang dùng hằng ngày. Gồm **cột tình hình vận hành** đọc từ `construction_operation_status` (badge màu theo danh mục — G4); cột lượng mưa hiển thị `-` ở v1.
- GIS: `gis_layers` (file qua Attachment) + API GeoJSON; công cụ đo + xuất bản đồ (M2.12/M2.13); marker đọc `constructions` + trạng thái dẫn xuất + `hydro_latest`; **marker điểm đo xám khi `MAT_TIN_HIEU`** (G3).
- Dashboard: view tổng hợp — đọc agg + alert + `maintenance_logs` (sự cố đang mở) + `hydro_latest`, không logic mới.

### NHÓM D — HR (MOD-04) — độc lập nhất, làm song song sau khi Core xong

- Org chart: dùng lại `org_units` của Core (thêm UI tree + export).
- `employees` (+ bảng `employee_sensitive` cho trường 🔒 — mã hóa cột, chỉ Admin HR), `positions`, `employee_events` (timeline — 1 bảng event type + JSON payload), `qualifications` (bằng cấp/chứng chỉ, `valid_until` → quét hạn cron P5).
- Tài liệu hồ sơ: Attachment service + 7 folder cố định + % hoàn thiện.
- Danh bạ: view/API đọc `employees` (cột công khai) + full-text (tên không dấu).
- Nghỉ phép: `leave_policies`, `leave_requests` (Workflow engine), `leave_balances` (tính lại bằng service), `holidays`.
- Liên kết `users.employee_id`.

---

## 3. THỨ TỰ IMPLEMENT & DEPENDENCY

```
Phase 0  ─ NHÓM A: Core Platform ──────────────────────────────┐
                                                               │ tất cả phụ thuộc A
Phase 1  ─ B (CMS: article/category/media/config)              │
         ─ C1 (Master data công trình `operations`) ← song song┤
Phase 2  ─ C2 (`hydro`: điểm đo + polling + alert)             │
         ─ B hoàn thiện (contact/phản hồi/banner/văn bản/public)│
         ─ Widget thủy văn (cần C1 + C2 + B)                   │
Phase 3  ─ C3 (GIS + Dashboard + wall 4K + Báo cáo)             │
         ─ D (HRM) ← song song với C3, chỉ cần A               │
Phase 4  ─ hardening, test NFR, security, deploy ──────────────┘
```

Điểm khóa dependency:
- **Widget thủy văn (MOD-01) là điểm giao B×C** — chốt contract API nội bộ MOD-03 (endpoint, token, schema JSON) cuối Phase 1, mock trước khi C2 xong.
- **`org_units` thiết kế 1 lần cho cả XN (MOD-02) và phòng ban (MOD-04)** — sai ở đây phải migrate đau.
- ✅ **Quan hệ `station_constructions`** (điểm đo↔công trình) đã chốt A2b — vai trò TL/HL/Bể hút/Mưa.
- **Workflow engine chốt trong Phase 0** — tránh nhiều bản copy state machine (nay dùng cho bài viết, nghỉ phép, duyệt dữ liệu nghi ngờ, trạng thái xử lý sự cố).
- Báo cáo (C3) đọc từ bảng agg — cron tổng hợp phải xong trước UI báo cáo.
- ✅ **Đã gỡ mục chặn MOD-03**: bảng ánh xạ 19 mã API ↔ điểm đo đã có (G8b) → seed data ở `function-spec.md` CN-03.1. Còn thiếu **tuyến sông / lý trình / tọa độ** (G8) — chỉ chặn phần **hiển thị GIS**, không chặn pipeline.
- ⚠ **Rủi ro #1: mất dữ liệu do poller chết.** Công ty đã chấp nhận "không có API lịch sử, hệ thống tự ghi" (G3) ⇒ trách nhiệm giữ dữ liệu chuyển hoàn toàn sang hệ thống mới, **không có đường backfill**. Monitoring poller phải xong **trong Phase 0**, không để tới Phase 4.
- ⚠ **Rủi ro #2: NFR nay là cam kết nghiệm thu** (G12) → **load test 200 CCU phải nằm trong kế hoạch từ Phase 2**, không dồn vào Phase 4; 2FA Admin/Admin HR làm ngay Phase 0.
- ⬜ **Lượng mưa (G3-a)**: nếu Công ty chọn "nhập tay" thì phát sinh thêm form + phân quyền + báo cáo ở Phase 2 (~3–5 ngày công). Chưa chốt → không code trước.
- **CN-01.7 auto-login** cần `CryptoService` của Core (Phase 0) — không có dependency ngoài, làm được ngay ở Phase 1.

---

## 4. TỔ CHỨC CODE (Modular Monolith)

```
backend/
├── core/            # Nhóm A: auth, rbac, orgunit, attachment, workflow,
│                    #  notification, jobs, audit, settings, backup/restore
├── content/         # Nhóm B / MOD-01: article, category, contact, feedback,
│                    #  siteconfig, search, external-login (auto-login văn bản)
├── operations/      # MOD-02: construction, maintenance (gồm cả sự cố),
│                    #  opstatus (tình hình vận hành), gis, dashboard, report
├── hydro/           # MOD-03: station, measurement-type, api-source, poller,
│                    #  reading, alert, hydro-report   ← TÁCH RIÊNG theo SRS
├── hr/              # MOD-04: employee, qualification, leave, directory
└── shared/          # DTO chung, exception, utils
frontend/
├── public-web/      # SSR/Static — trang public + widget (SEO)
└── admin-app/       # SPA — toàn bộ quản trị B/C/D, chia route theo module
```

Quy tắc ràng buộc giữa module (giữ đúng Modular Monolith):
- Module nghiệp vụ **chỉ gọi core + module khác qua service interface `spi/`**, không import repository — **enforce bằng ArchUnit test trong CI**.
- `content` (widget) và `operations` (GIS marker, dashboard) lấy dữ liệu thủy văn **qua `spi/` của `hydro`** (hoặc `hydro_latest` như contract đọc), không đụng bảng `hydro_*` trực tiếp.
- `content` không được đụng bảng của `operations`/`hydro`.
- Mỗi module tự quản migration DB của mình; bảng chung thuộc `core`.
- Worker (polling, report) cùng codebase; **v1 chạy in-process** (bounded pool). Giữ profile/entrypoint `worker` để tách process khi đo thấy nặng — điểm nóng dự kiến là `hydro` (polling) → tách trước tiên. App **stateless** (state ở DB/MinIO) để thêm node 2 chỉ là bổ sung instance + bật ShedLock.

---

## 5. GỢI Ý PHÂN CÔNG TEAM (tham khảo)

| Vai trò | Phase 0 | Phase 1–2 | Phase 3–4 |
|---|---|---|---|
| BE 1 (senior) | Core: auth/RBAC/workflow/jobs | `hydro`: polling + alert | C3 báo cáo + agg |
| BE 2 | Attachment/notification/audit/backup-restore | B: CMS + auto-login văn bản + C1 (`maintenance_logs`, `operation_status_codes`) | Báo cáo BC-06/09/10 / D leave |
| FE 1 | Setup 2 app + design system | public-web + CMS admin | GIS + Dashboard |
| FE 2 | — (join Phase 1) | admin: công trình + hydro chart | D: HRM UI + danh bạ |
| QA/DevOps | CI/CD, 3 môi trường | test theo module | test NFR, security, go-live |

---

## 6. CHECKLIST QUYẾT ĐỊNH CẦN CHỐT TRƯỚC KHI CODE

> Cập nhật **2026-08-12**: đóng toàn bộ BOQ đợt 1 **và 8/12 mục đợt 2 (G1, G2, G3, G4, G7, G9, G11, G12)**. Còn mở: G3-a, G5, G6, G8/G8b, G9-a, G10 — xem `business-open-questions.md` Phần II.

1. ✅ **Cơ chế lấy dữ liệu thủy văn: CONFIRMED** — polling API ngoài (module `hydro`). **Endpoint thật đã đấu nối OK**: `http://songnhue.bhh40.net/api/getmn.aspx?key=<mã số>;` (**dấu `;` bắt buộc**); đơn vị nguồn **cm**, dữ liệu theo cặp TL/HL + lý trình. **Cron chốt 2'/phút lẻ + rate-limit theo khung 10'** (G3). ⬜ Chưa có endpoint lượng mưa → G3-a.
2. ✅ **Cấu trúc module: CONFIRMED (2026-08-06)** — theo SRS: MOD-01 CMS+văn bản, MOD-02 vận hành+GIS, MOD-03 thủy văn (module `hydro` riêng), MOD-04 HRM, MOD-05 quản trị.
3. ✅ Queue: **DB-backed queue + ShedLock**.
4. ✅ Chart: **ECharts**.
5. ✅ Base map: **OSM mặc định** (Leaflet/MapLibre), Google Maps optional. ⬜ Shapefile (SRS §4.6) — chốt ở thiết kế chi tiết (F7).
6. ✅ Database: **PostgreSQL 16 + PostGIS**.
7. ✅ Admin UI: **Ant Design 5**; Public web: **Next.js + Tailwind**.
8. ✅ **Quy mô triển khai: CONFIRMED** — v1 1 node, bỏ Redis, worker in-process, ShedLock giữ sẵn. **Backup bản tối giản: `pg_dump` hàng đêm, RPO ≤ 24h, RTO ≤ 4h, không PITR/replica** (chốt 13/8/2026). Xem `architecture-review.md` §6.5.
9. ✅ **Restore UI: CONFIRMED (2026-08-06)** — làm nút restore (M5.11) + bảo vệ nhiều lớp (`architecture-review.md` §7.3).
10. ✅ **Scope phần mở rộng: ĐÃ ĐÓNG HOÀN TOÀN (12/8/2026)** — **bỏ nhật ký vận hành + phiếu sự cố riêng + BC-01/02/03/04/07/08**, thay bằng **Lịch sử sửa chữa/khắc phục sự cố (CN-02.2)** + BC-06/BC-09/BC-10. **Không còn hạng mục 🔷 nào.**
11. ✅ **Tích hợp hệ thống văn bản: CONFIRMED** — không SSO/API/CSDL; lưu credential người dùng + auto-login (CN-01.7). ⬜ Còn chi tiết: mã số riêng hay chung, ai nhập — G5.
12. ⬜ **Mẫu báo cáo**: đã soạn đề xuất format (`report-templates-proposal.md`), chờ Công ty duyệt + gửi 4 file mẫu trọng yếu — G10.
13. ✅ **Quan hệ điểm đo↔công trình: CONFIRMED** (A2b). ✅ **Kế hoạch vụ mùa: BỎ** (A1).
14. ✅ **Bảng ánh xạ mã API ↔ điểm đo: CONFIRMED (G8b)** — đủ 19/19 mã, đã thành seed data ở `function-spec.md` CN-03.1. ⬜ Còn thiếu **tuyến sông / lý trình / tọa độ GPS** + danh mục công trình tổng thể (G8) — chặn hiển thị GIS, không chặn pipeline.
15. ✅ **Tình hình vận hành cống: CONFIRMED (G4)** — **không** có trong API, nhập tay qua CN-02.11; danh mục mã **CRUD** + màu + ánh xạ trạng thái, seed 4 mã.
16. ✅ **Ngưỡng cảnh báo: CONFIRMED (G9)** — Admin tự cấu hình; hệ thống phải bàn giao **màn hình cấu hình ngưỡng** + chạy được với ngưỡng mặc định; điểm đo chưa cấu hình thì không phát cảnh báo. ⬜ Bộ mức ngưỡng cụ thể — G9-a.
17. ✅ **Số liệu vận hành (giờ chạy/kWh/m³): BỎ VĨNH VIỄN (G2)** — không mở lại màn hình nhập nào.
18. ✅ **Audit retention: CONFIRMED 5 năm (G7)** — kết xuất lưu trữ có checksum trước khi xóa, hash chain nối tiếp.
19. ✅ **Người nhận cảnh báo: CONFIRMED (G11)** — nhóm "Ban điều hành" cấu hình được ∪ người phụ trách công trình liên quan.
20. ✅ **Con số NFR nghiệm thu: CONFIRMED (G12)** — 99% uptime · 200 CCU · 3s · 60s · 2FA Admin/Admin HR → **có load test trong kế hoạch kiểm thử**.

---

## 7. ĐÁNH GIÁ MỨC ĐỘ SẴN SÀNG CODE (2026-08-13)

### 7.1. ~~Môi trường máy dev~~ — ĐÃ XOÁ 19/8/2026

> Mục này từng liệt kê phiên bản JDK/Node/Docker trên máy dev và kết luận *"repo hiện chỉ có tài
> liệu, 0 file mã nguồn → greenfield"*. **Cả hai điều đó nay đều sai**: Phase 0 đã dựng xong, repo
> có 443 tệp, Maven chạy bằng wrapper `./mvnw`.
>
> Xoá thay vì sửa, vì một bảng phiên bản chụp tại một thời điểm thì luôn lỗi thời — điều kiện môi
> trường thật sự nằm ở `make doctor` (kiểm công cụ + cổng trống) và `docs/setup-guideline.md`.
> Một tài liệu tự già đi mà không ai hay là thứ nguy hiểm hơn không có tài liệu.

### 7.2. Kết luận theo từng Phase

| Phase | Nội dung | Sẵn sàng? | Ghi chú |
|---|---|:-:|---|
| **Phase 0** — Nhóm A Core | auth/RBAC/orgunit/attachment/workflow/notification/jobs/audit/settings/backup-restore | ✅ **XONG 19/8/2026** | 12/21 mục Definition of Done đạt, 5 dở dang, **4 mục chưa xong đều phụ thuộc VM** (đo RTO thật · deploy staging · rollback). Không mục nào chặn việc viết nghiệp vụ |
| **Phase 1** — B (CMS) + C1 (master data công trình) | article/category/media/siteconfig **+ hiển thị công khai** · `constructions`, `maintenance_logs`, `operation_status` | 🟡 **Đang làm** — kế hoạch xong 19/8, xem §7.6 | Nền đã có: 6 pattern P1–P6 là shared service, ArchUnit canh ranh giới. ⚠ **WS-12 phải xong trước** (nợ #56 — `core/spi/` rỗng). ⚠ Chỉ **CN-01.7** bị chặn cứng bởi **G5** — đã tách khỏi Phase 1 |
| **Phase 2** — C2 (`hydro`) | điểm đo, adapter, polling, rate-limit, lưu trữ, alert engine | ✅ **Bắt đầu được ngay** | Ánh xạ 19 mã đã có (G8b). Thiếu toạ độ/tuyến sông (G8) chỉ chặn phần hiển thị GIS, không chặn pipeline |
| **Phase 3** — C3 (GIS/dashboard/báo cáo) + D (HRM) | | 🟨 **Code được, chốt layout sau** | Trường dữ liệu báo cáo đã chốt; **layout in ấn** chờ Công ty duyệt (G10). BCNS-07 chờ mẫu 2C-BNV (G6) |
| **Phase 4** — hardening/NFR/go-live | | ✅ | Con số nghiệm thu đã chốt (G12). Gồm nốt phần deploy còn treo của Phase 0 |

### 7.3. Ba ràng buộc phải cài từ Phase 0 để hấp thụ các câu trả lời còn lại

Đây là lý do có thể bắt đầu code dù còn 6 mục mở — thiết kế phải **chịu được cả hai nhánh trả lời**:

1. **`settings` key-value có type + UI + validate** — mọi tham số chưa chốt (mức ngưỡng, bật/tắt lượng mưa, nhóm người nhận, URL tích hợp) đổ vào đây, **không cần migration** khi Công ty trả lời.
2. **Danh mục hóa thay vì enum cứng** — mức ngưỡng (G9-a), mã tình hình vận hành (G4), loại chỉ số đo (G3-a) đều là **bảng có CRUD**. Enum trong code = phải deploy lại mỗi lần khách đổi ý.
3. **`TelemetryAdapter` đa nguồn** — không hard-code "1 nguồn = 1 endpoint mực nước", để cắm thêm nguồn lượng mưa (G3-a) mà không sửa pipeline.

### 7.4. Kế hoạch chi tiết Phase 0 → `phase0-tracking.md`

📋 **11 hạng mục WS-1→WS-11, 107 task có ID, 21 mục Definition of Done, sổ nợ liên WS: [`phase0-tracking.md`](phase0-tracking.md).** Mỗi WS tự chứa điều kiện tiên quyết / đầu ra / cách kiểm chứng để làm độc lập tuần tự. Quyết định nền tảng (Maven multi-module, monorepo, deploy 3 VM, secrets, migration service riêng, DB roles) ở `architecture-review.md` §9.

> 📌 **Sơ đồ phụ thuộc giữa các WS đã gỡ khỏi đây (21/8/2026)** — nó là bản sao nguyên xi của mục *"Sơ đồ phụ thuộc — làm tuần tự từng module"* ở đầu `phase0-tracking.md`, mà bản kia còn kèm cảnh báo "hai việc nên làm ngay tuần 1". **Giữ hai bản của cùng một thứ là cách chắc chắn để chúng lệch nhau** — cùng lý do đã xoá §7.5.

### 7.5. ~~Thứ tự khởi động Phase 0~~ — ĐÃ XOÁ 19/8/2026

> Mục này liệt kê 6 bước khởi động tuần 1 của Phase 0. **Phase 0 đã xong**, nên nó chỉ còn giá trị
> lịch sử — và lịch sử ấy đã nằm đầy đủ ở `phase0-tracking.md` (11 hạng mục, nhật ký theo ngày,
> sổ nợ liên WS). Giữ hai bản của cùng một thứ là cách chắc chắn để chúng lệch nhau.
>
> Thứ tự khởi động **Phase 1** sẽ nằm ở kế hoạch riêng của Phase 1, không viết chồng vào đây.

### 7.6. Kế hoạch chi tiết Phase 1

📋 **Toàn bộ Phase 1 đã được break thành 11 hạng mục (WS-12→WS-22) với 99 task có ID + 17 mục
Definition of Done: [`phase1-tracking.md`](phase1-tracking.md).** Quyết định kiến trúc kèm lý do:
`architecture-review.md` **§10**.

Ba thay đổi phạm vi so với §3 của chính file này, chốt ngày 19/8/2026:

| Thay đổi | Lý do |
|---|---|
| **Hiển thị công khai bài viết chuyển từ Phase 2 lên Phase 1** (+8 pd) | `POST /api/revalidate` đã dựng ở WS-9 "cho luồng duyệt bài Phase 1" và chưa ai đi qua — `architecture-review.md` §10.1 |
| **Liên hệ (CN-01.4) + Phản hồi (CN-01.6) giữ nguyên ở Phase 2** | Cùng là pattern "tiếp nhận từ public → hàng đợi nội bộ", phụ thuộc reCAPTCHA key của Công ty (G13); gom một đợt |
| **Thêm đường nhập danh mục công trình từ Excel/CSV có chạy khô** (+3 pd) | G8 đang xin file Excel của Công ty; có đường nhập thì lúc file về là dùng được ngay, và đó cũng là đường seed dữ liệu thật |

Ràng buộc thứ tự thật sự:

```
WS-12 ─────────────────────────────────────►  [nền — CHẶN mọi thứ]
   ├─► WS-13 ─► WS-15 ─► WS-16                [CMS + cổng công khai]
   │      └────────────► WS-20
   ├─► WS-14 ──────────► WS-20
   └─► WS-17 ─► WS-18 ─► WS-19                [công trình — tuần tự]
          └───────────────► WS-21
                              └─► WS-22
```

⚠ **WS-12 (mở `core/spi/`) là việc chặn, không phải việc dọn dẹp.** `core/spi/` rỗng trong khi cả
sáu dịch vụ dùng chung nằm ở `core.application.*` → dòng mã Phase 1 đầu tiên gọi `WorkflowEngine`
làm CI đỏ. Và nó lớn hơn "thêm sáu interface": chữ ký hiện tại trả về **entity domain**, mà module
nghiệp vụ import `core.domain.*` là vi phạm y hệt.

📌 **18 điểm nghiệp vụ chưa rõ trong spec đã được làm rõ** trước khi code (sửa bài đã xuất bản có
phải duyệt lại không · bản ghi sửa chữa nhập sau khi xong bắt đầu ở trạng thái nào · tiền lưu VND
hay triệu VND · SVG có được tải lên không…) — bảng đầy đủ kèm cột "ai quyết" ở `phase1-tracking.md`.
Ba mục phải hỏi Công ty đã mở thành **G13 · G14 · G15**.
