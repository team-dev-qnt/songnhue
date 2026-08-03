# IMPLEMENTATION PLAN — GOM NHÓM MODULE & TỔ CHỨC CODING

> Kèm theo `function-spec.md`. Mục tiêu: gom nhóm theo điểm chung về logic / DB / business để code 1 lần dùng nhiều nơi, tránh mỗi module tự chế một kiểu.

---

## 1. NGUYÊN TẮC GOM NHÓM

Nhìn xuyên qua 5 module, có **6 pattern lặp lại** — đây là căn cứ gom nhóm, không gom theo "module nghiệp vụ" một cách máy móc:

| # | Pattern chung | Xuất hiện ở |
|---|---|---|
| P1 | **Workflow duyệt / state machine** (Nháp → Chờ duyệt → Duyệt/Từ chối) | Bài viết (CN-01.1), Nhật ký vận hành (CN-02.5), Phiếu sự cố (CN-02.7), Nghỉ phép (CN-03.8), Bình luận (CN-01.6) |
| P2 | **Cấu trúc cây phân cấp** | Danh mục nội dung (3 cấp), Thư mục media (3 cấp), Phân cấp công trình (4 cấp), Sơ đồ tổ chức (5 cấp), Menu nested |
| P3 | **File đính kèm + versioning + hạn hiệu lực** | Media CMS, Tài liệu công trình, Hồ sơ nhân viên, Template báo cáo, Layer GIS |
| P4 | **Notification đa kênh** (in-app, email, SMS, web push) | Cảnh báo thủy văn, duyệt bài, duyệt nhật ký, sự cố, liên hệ mới, nghỉ phép, HĐ/chứng chỉ sắp hết hạn |
| P5 | **Async job + cron scheduler** | Xuất báo cáo (BC + BCNS), tổng hợp kỳ, polling API, hẹn giờ đăng bài, quét hạn hiệu lực |
| P6 | **Data scoping theo Xí nghiệp/Đơn vị** | Toàn bộ MOD-02 (Operator/Quản lý XN), báo cáo, MOD-03 (Quản lý đơn vị) |

→ **Xây các pattern này thành shared service/library trong Core trước**, các module nghiệp vụ chỉ khai báo cấu hình (trạng thái, ai duyệt, kênh notify...).

---

## 2. GOM NHÓM IMPLEMENT — 4 NHÓM

### NHÓM A — CORE PLATFORM (làm đầu tiên, mọi nhóm phụ thuộc)

Gồm: MOD-05 + toàn bộ shared services rút ra từ P1–P6.

| Thành phần | Nội dung | Phục vụ |
|---|---|---|
| Auth & RBAC | Access token 30' + Refresh rotation (httpOnly cookie) + BCrypt, denylist bảng DB; Role + Permission; annotation/middleware check quyền + **scope filter theo đơn vị (P6)** | Tất cả |
| User & Org Unit | Bảng `users`, `org_units` (cây đơn vị dùng CHUNG cho MOD-02 Xí nghiệp và MOD-03 phòng ban — 1 bảng, đừng tách 2) | MOD-02, 03 |
| Tree helper (P2) | Base entity cây: `parent_id` + `path` (materialized path) + `sort_order`; API move/reorder | Danh mục, media folder, công trình, org chart, menu |
| Attachment service (P3) | 1 bảng `attachments` polymorphic (`owner_type`, `owner_id`, `folder`, `version`, `valid_until`); upload MinIO, validate định dạng/dung lượng theo config, scan malware, versioning, preview | CMS media, tài liệu CT, hồ sơ NV |
| Workflow engine (P1) | Bảng `workflow_transitions` + service generic: định nghĩa states, transitions, role được phép, hook (notify, audit) theo từng entity type | Bài viết, nhật ký, sự cố, nghỉ phép |
| Notification service (P4) | 1 API `notify(event, targets, channels)`; adapter Email(SMTP)/SMS(ESMS)/WebPush/In-app; template + danh sách nhận theo cấu hình | Tất cả |
| Job & Scheduler (P5) | **DB-backed queue** (bảng `jobs`, SELECT … FOR UPDATE SKIP LOCKED — transactional cùng dữ liệu nghiệp vụ) + worker **in-process** (bounded pool, v1; giữ profile `worker` để tách sau); trạng thái Pending/Processing/Completed/Failed/Cancelled, retry 3; cron qua Spring Scheduler + **ShedLock** (giữ sẵn, bật khi ≥2 node) + chống overlapping run | Báo cáo, polling, hẹn giờ, quét hạn |
| Audit log | Interceptor global: user, timestamp, action, old/new JSON | Tất cả (NFR-07) |
| System config | Bảng `settings` key-value có type; UI quản trị | Tất cả |

**DB dùng chung của Nhóm A**: `users`, `roles`, `permissions`, `org_units`, `attachments`, `audit_logs`, `notifications`, `jobs`, `settings`, `workflow_transitions`.

### NHÓM B — CONTENT (MOD-01 + MOD-04)

Điểm chung: nội dung hiển thị public, SEO, workflow xuất bản, ít liên quan dữ liệu vận hành.

- Article + Category + Tag (dùng Tree helper + Workflow engine + Attachment).
- Banner, Footer, Menu, Site config, trang đặc biệt — đều là "settings có cấu trúc", gom vào 1 nhóm `site_configuration`.
- Contact + Comment: cùng pattern "tiếp nhận từ public → hàng đợi xử lý/duyệt nội bộ" → chung 1 service `inbound_submission` (khác nhau ở form + spam filter).
- MOD-04 gom vào đây vì bản chất là tích hợp hiển thị trên cổng (link/SSO/embed) — không có DB riêng.
- **DB**: `articles`, `article_versions`, `categories`, `article_categories`, `comments`, `contacts`, `banners`, `menus`. Media dùng `attachments` của Core.
- Frontend: đây là nhóm duy nhất cần **SSR/Static** (SEO); tách app public khỏi admin SPA ngay từ đầu.

### NHÓM C — OPERATIONS (MOD-02) — nhóm nặng nhất, tách 3 lớp nội bộ

**C1. Master data công trình** (làm trước — mọi thứ trong C tham chiếu nó):
- `constructions` (chung cho trạm bơm + cống: cột chung + bảng extension `pump_station_specs`, `sluice_specs`), `construction_clusters`, trạng thái, tài liệu (Attachment), tọa độ cho GIS.
- Đây cũng là dependency của Widget CMS và Dashboard.

**C2. Data pipeline thủy văn** (độc lập về logic, chạy nền):
- `api_sources` (credential AES-256), Polling worker (dùng Job/Scheduler của Core, retry/backoff), `hydro_raw_logs` (raw append-only), `hydro_readings` (time-series, index `station_id + timestamp`, partition theo tháng để retention 5 năm + cold storage), `hydro_latest` (1 dòng/trạm, poller UPSERT — thay vai trò cache Redis cho widget/GIS/dashboard, graceful degradation khi API ngoài chết).
- **Alert engine**: `alert_rules` (theo công trình + thông số, 3 mức, delay chống nhiễu) + `alert_events`; đánh giá rule ngay sau mỗi lần ghi reading; phát qua Notification service.
- Team làm C2 có thể chạy song song với C1 (chỉ cần `station_id` thống nhất trước).

**C3. Nghiệp vụ vận hành** (phụ thuộc C1, một phần C2):
- Nhật ký vận hành: `operation_logs` + `machine_run_records` (dùng Workflow engine cho duyệt; validation theo spec CN-02.5; auto-tính giờ chạy/lưu lượng ở BE, FE chỉ hiển thị).
- Sự cố: `incidents` + `incident_updates` (Workflow engine, 7 trạng thái); hook từ nhật ký (mức Nặng) và từ alert.
- Báo cáo: `report_templates` + dùng Job queue; engine tổng hợp đọc từ `operation_logs` + `hydro_readings`; các bảng tổng hợp kỳ `agg_daily/weekly/monthly` (cron ghi sẵn — báo cáo và dashboard đọc từ bảng agg, không query raw).
- GIS: `gis_layers` (file qua Attachment) + API GeoJSON cho FE; marker đọc từ `constructions` + trạng thái + reading mới nhất (bảng `hydro_latest`, không Redis).
- Dashboard: chỉ là view tổng hợp — đọc từ agg tables + alert + incidents, không logic mới.

### NHÓM D — HR (MOD-03) — độc lập nhất, có thể làm song song sau khi Core xong

- Org chart: dùng lại `org_units` của Core (chỉ thêm UI tree + export).
- `employees` (tách bảng `employee_sensitive` cho trường 🔒 — mã hóa cột, quyền riêng chỉ Admin HR; đừng để chung bảng với thông tin danh bạ), `positions` (danh mục chức vụ), `employee_events` (timeline — 1 bảng event type + JSON payload thay vì 10 bảng), `qualifications` (bằng cấp/chứng chỉ, có `valid_until` → quét hạn bằng cron chung P5).
- Tài liệu hồ sơ: Attachment service + cấu hình 7 folder cố định + rule % hoàn thiện.
- Danh bạ: view/API đọc từ `employees` (chỉ cột công khai) + full-text index (cột tên không dấu).
- Nghỉ phép: `leave_policies`, `leave_requests` (Workflow engine), `leave_balances` (tính lại bằng service, không cộng trừ tay rải rác); `holidays`.
- Liên kết `users.employee_id` → tài khoản gắn hồ sơ NV.

---

## 3. THỨ TỰ IMPLEMENT & DEPENDENCY

```
Phase 0  ─ NHÓM A: Core Platform ──────────────────────────────┐
                                                               │ tất cả phụ thuộc A
Phase 1  ─ B (CMS: article/category/media/config)              │
         ─ C1 (Master data công trình)          ← song song ───┤
Phase 2  ─ C2 (Polling + Alert)                                │
         ─ B hoàn thiện (contact/comment/banner/public site)   │
         ─ Widget thủy văn (cần C1 + C2 + B)                   │
Phase 3  ─ C3 (Nhật ký → Sự cố → Báo cáo → GIS → Dashboard)    │
         ─ D (HRM) ← song song với C3, chỉ cần A               │
Phase 4  ─ MOD-04 tích hợp văn bản; hardening, test NFR, deploy┘
```

Điểm khóa dependency:
- **Widget thủy văn (MOD-01) là điểm giao B×C** — chốt contract API nội bộ (endpoint, token, schema JSON) ngay cuối Phase 1, mock trước khi C2 xong.
- **`org_units` phải thiết kế 1 lần cho cả XN (MOD-02) và phòng ban (MOD-03)** — sai ở đây sẽ phải migrate đau về sau.
- **Workflow engine chốt trong Phase 0** — nếu để mỗi module tự viết state machine sẽ có 4 bản copy khác nhau.
- Báo cáo (C3) đọc từ bảng agg — nghĩa là cron tổng hợp phải xong trước UI báo cáo.

---

## 4. TỔ CHỨC CODE (Modular Monolith)

```
backend/
├── core/            # Nhóm A: auth, rbac, orgunit, attachment, workflow,
│                    #  notification, jobs, audit, settings
├── content/         # Nhóm B: article, category, comment, contact, siteconfig
├── operations/      # Nhóm C: construction, hydro, alert, logbook, incident,
│                    #  report, gis, dashboard
├── hr/              # Nhóm D: employee, qualification, leave, directory
└── shared/          # DTO chung, exception, utils
frontend/
├── public-web/      # SSR/Static — trang public + widget (SEO)
└── admin-app/       # SPA — toàn bộ quản trị B/C/D, chia route theo module
```

Quy tắc ràng buộc giữa module (giữ đúng Modular Monolith):
- Module nghiệp vụ **chỉ gọi core qua service interface**, không import repository của module khác — **enforce bằng ArchUnit test trong CI**.
- `content` không được đụng bảng của `operations` — widget lấy qua API/service interface của `operations`.
- Mỗi module tự quản migration DB của mình; bảng chung thuộc `core`.
- Worker (polling, report) cùng codebase; **v1 chạy in-process** (bounded pool). Giữ profile/entrypoint `worker` riêng để tách thành process độc lập khi đo thấy nặng — không đổi code, chỉ đổi cấu hình. App phải **stateless** (state ở DB/MinIO) để thêm node 2 chỉ là bổ sung instance + bật ShedLock.

---

## 5. GỢI Ý PHÂN CÔNG TEAM (tham khảo)

| Vai trò | Phase 0 | Phase 1–2 | Phase 3–4 |
|---|---|---|---|
| BE 1 (senior) | Core: auth/RBAC/workflow/jobs | C2 polling + alert | C3 báo cáo + agg |
| BE 2 | Attachment/notification/audit | B: CMS API + C1 | C3 nhật ký + sự cố / D leave |
| FE 1 | Setup 2 app + design system | public-web + CMS admin | GIS + Dashboard |
| FE 2 | — (join Phase 1) | admin: công trình + hydro chart | D: HRM UI + danh bạ |
| QA/DevOps | CI/CD, 3 môi trường | test theo module | test NFR, security, go-live |

---

## 6. CHECKLIST QUYẾT ĐỊNH CẦN CHỐT TRƯỚC KHI CODE

> Cập nhật 2026-07-20: các mục 3–7 đã chốt trong `architecture-review.md`.

1. ✅ **Cơ chế lấy dữ liệu thủy văn: CONFIRMED (2026-07-20)** — hệ thống call sang API ngoài được cấp và fetch data về theo chu kỳ (polling), đúng thiết kế C2. ⬜ Còn chờ: tài liệu API thật (endpoint, schema, auth, rate limit) để code adapter — trong lúc chờ, C2 dev theo interface + mock adapter.
2. ⬜ Khả năng tích hợp hệ thống văn bản điều hành hiện có (SSO? API? chỉ link?) — quyết phạm vi MOD-04. *Chờ user confirm.*
3. ✅ Queue: **DB-backed queue + ShedLock** (không Redis Queue/RabbitMQ) — job transactional cùng dữ liệu nghiệp vụ.
4. ✅ Chart: **ECharts** (Highcharts tính phí thương mại).
5. ✅ Base map: **OSM mặc định** (Leaflet/MapLibre), Google Maps optional qua config.
6. ✅ Database: **PostgreSQL 16 + PostGIS** (thay MySQL — xem lý do trong architecture-review.md §1).
7. ✅ Admin UI: **Ant Design 5**; Public web: **Next.js + Tailwind**.
8. ✅ **Quy mô triển khai: CONFIRMED (2026-07-21)** — v1 chạy **1 node**, **bỏ Redis** (thay bằng `hydro_latest` Postgres + Caffeine + denylist DB), **worker in-process**, ShedLock giữ sẵn (bật khi ≥2 node). App stateless + file ở MinIO → thêm node 2 chỉ là đổi cấu hình. Trọng tâm vận hành: **backup DB + PITR + test restore**. Xem `architecture-review.md` §6.
9. ⬜ Mẫu báo cáo chuẩn công ty (file thật) — cần trước khi làm template engine C3. *Chờ user confirm.*
