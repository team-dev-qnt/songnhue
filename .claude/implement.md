# IMPLEMENTATION PLAN — GOM NHÓM MODULE & TỔ CHỨC CODING

> Kèm theo `function-spec.md` (v2.0). Mục tiêu: gom nhóm theo điểm chung về logic / DB / business để code 1 lần dùng nhiều nơi, tránh mỗi module tự chế một kiểu.
>
> ⚠ **Cập nhật 2026-08-06**: đồng bộ cấu trúc module theo SRS — thủy văn tách thành **MOD-03** (module code `hydro`), tích hợp văn bản gộp vào **MOD-01**, HRM = **MOD-04**. Nhóm code vẫn giữ nguyên tư duy pattern-based; chỉ tách thêm module `hydro` khỏi `operations`.

---

## 1. NGUYÊN TẮC GOM NHÓM

Nhìn xuyên qua 5 module, có **6 pattern lặp lại** — đây là căn cứ gom nhóm, không gom theo "module nghiệp vụ" một cách máy móc:

| # | Pattern chung | Xuất hiện ở |
|---|---|---|
| P1 | **Workflow duyệt / state machine** | Bài viết (CN-01.1), Nhật ký vận hành 🔷 (CN-02.8), Phiếu sự cố 🔷 (CN-02.9), Nghỉ phép (CN-04.9), Phản hồi (CN-01.6) |
| P2 | **Cấu trúc cây phân cấp** | Danh mục nội dung (3 cấp), Thư mục media (3 cấp), Phân cấp công trình (4 cấp), Sơ đồ tổ chức (5 cấp), Menu nested |
| P3 | **File đính kèm + versioning + hạn hiệu lực** | Media CMS, Tài liệu công trình, Hồ sơ nhân viên, Template báo cáo, Layer GIS |
| P4 | **Notification đa kênh** (in-app, email, SMS, web push) | Cảnh báo thủy văn (MOD-03), duyệt bài, duyệt nhật ký, sự cố, liên hệ mới, nghỉ phép, HĐ/chứng chỉ sắp hết hạn, thông báo hệ thống |
| P5 | **Async job + cron scheduler** | Xuất báo cáo (BC + BCNS + thủy văn), tổng hợp kỳ, polling API, hẹn giờ đăng bài, quét hạn, đồng bộ văn bản |
| P6 | **Data scoping theo Xí nghiệp/Đơn vị** | Toàn bộ MOD-02/MOD-03 (Operator/Quản lý XN/Kỹ thuật), báo cáo, MOD-04 (Quản lý đơn vị) |

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
| Workflow engine (P1) | Bảng `workflow_transitions` + service generic: states, transitions, role được phép, hook (notify, audit) theo entity type | Bài viết, nhật ký 🔷, sự cố 🔷, nghỉ phép |
| Notification service (P4) | 1 API `notify(event, targets, channels)`; adapter Email(SMTP)/SMS(ESMS)/WebPush/In-app; template + danh sách nhận; thông báo hệ thống (M5.13) | Tất cả |
| Job & Scheduler (P5) | **DB-backed queue** (SKIP LOCKED, transactional) + worker **in-process** (bounded pool, v1); Pending/Processing/Completed/Failed/Cancelled, retry 3; cron Spring Scheduler + **ShedLock** (giữ sẵn, bật khi ≥2 node) + chống overlapping run | Báo cáo, polling, hẹn giờ, quét hạn, đồng bộ văn bản |
| Audit log | Interceptor global: user, timestamp, action, old/new JSON; **append-only + hash chain** (M5.7, không sửa/xóa) | Tất cả (NFR-07) |
| System config | Bảng `settings` key-value có type; UI quản trị; **xuất/nhập cấu hình (M5.17)**; health-check dịch vụ/API tích hợp (M5.12) | Tất cả |
| Backup/Restore | Backup tự động + theo yêu cầu; **restore qua UI có bảo vệ nhiều lớp (M5.11)** + runbook PITR song song (xem `architecture-review.md` §7) | Vận hành |

**DB dùng chung của Nhóm A**: `users`, `roles`, `permissions`, `org_units`, `attachments`, `audit_logs`, `notifications`, `jobs`, `settings`, `workflow_transitions`, `sessions`, `token_denylist`.

### NHÓM B — CONTENT (MOD-01, gồm CMS + tích hợp văn bản)

Điểm chung: nội dung hiển thị public, SEO, workflow xuất bản, ít liên quan dữ liệu vận hành.

- Article + Category + Tag (dùng Tree helper + Workflow engine + Attachment).
- Banner, Footer, Menu, Site config, trang đặc biệt, tìm kiếm (CN-01.8) — gom nhóm `site_configuration`.
- Contact + Phản hồi/khảo sát (CN-01.6): pattern "tiếp nhận từ public → hàng đợi xử lý/duyệt nội bộ" → chung service `inbound_submission`.
- **Tích hợp văn bản điều hành (CN-01.7)**: adapter đồng bộ định kỳ từ hệ thống nguồn (dùng Job/Scheduler Core) → lưu danh sách văn bản công khai → hiển thị lên cổng. Không xây DB nghiệp vụ văn bản (nguồn giữ), chỉ bảng cache `external_documents` + cấu hình kết nối.
- **DB**: `articles`, `article_versions`, `categories`, `article_categories`, `comments`, `contacts`, `banners`, `menus`, `external_documents`. Media dùng `attachments` của Core.
- Frontend: nhóm duy nhất cần **SSR/Static** (SEO); tách app public khỏi admin SPA ngay từ đầu.

### NHÓM C — OPERATIONS + HYDRO (MOD-02 + MOD-03) — nhóm nặng nhất, **2 module code tách riêng**

**C1. Master data công trình** (module `operations`, làm trước — mọi thứ tham chiếu):
- `constructions` (chung cho trạm bơm + cống + đê + kênh: cột chung + bảng extension `pump_station_specs`, `sluice_specs`, tùy chọn `dyke_specs`/`canal_specs`), `construction_clusters`, cấp quản lý + đơn vị phụ trách, **liên kết lưu vực/khu tưới (`irrigation_zones`)**, trạng thái, tài liệu (Attachment), tọa độ GIS, **lịch sử bảo trì `maintenance_logs`** (chi phí BigDecimal), nhật ký thay đổi hồ sơ.
- Là dependency của Widget CMS, Dashboard, và layer GIS.

**C2. Data pipeline thủy văn — module `hydro` (MOD-03, tách riêng theo SRS)**:
- `measurement_types` (loại chỉ số + đơn vị), `stations` (điểm đo + **mã ánh xạ API bên thứ 3**, 1–1), `station_constructions` (n–n vai trò TL/HL/mưa — chờ confirm A2b).
- `api_sources` (credential AES-256-GCM), Polling worker (Job/Scheduler Core, retry/backoff, cấu hình được), `hydro_raw_logs` (raw append-only), `hydro_readings` (time-series, partition tháng, retention 5 năm + cold storage, **trạng thái bản ghi Hợp lệ/Nghi ngờ/Loại bỏ**), `hydro_latest` (1 dòng/điểm đo, UPSERT — thay cache Redis cho widget/GIS/dashboard, graceful degradation), `sync_logs` (nhật ký đồng bộ M3.16).
- **Alert engine**: `alert_rules` (theo điểm đo × chỉ số, 3 mức, delay) + `alert_events`; đánh giá ngay sau ghi reading; phát qua Notification service.
- Báo cáo thủy văn (định kỳ/theo yêu cầu/mùa vụ — dùng Job queue).
- Chạy **song song** với C1 (chỉ cần thống nhất `station_id` + quan hệ station↔construction trước).

**C3. Nghiệp vụ vận hành — module `operations` (phần lớn 🔷 ngoài SRS, phụ thuộc C1, một phần C2)**:
- 🔷 Nhật ký vận hành: `operation_logs` + `machine_run_records` (Workflow engine; validation CN-02.8; auto-tính ở BE).
- 🔷 Sự cố: `incidents` + `incident_updates` (Workflow engine, 7 trạng thái); hook từ nhật ký (Nặng) và từ alert (MOD-03).
- 🔷 Báo cáo vận hành: `report_templates` + Job queue; engine tổng hợp đọc `operation_logs` + `hydro_readings`; bảng agg `agg_daily/weekly/monthly` (cron ghi sẵn — báo cáo/dashboard đọc từ agg, không query raw).
- GIS: `gis_layers` (file qua Attachment) + API GeoJSON; công cụ đo + xuất bản đồ (M2.12/M2.13); marker đọc `constructions` + trạng thái + `hydro_latest`.
- Dashboard: view tổng hợp — đọc agg + alert + incidents + `hydro_latest`, không logic mới.

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
Phase 3  ─ C3 (🔷 Nhật ký → Sự cố → Báo cáo) + GIS + Dashboard │
         ─ D (HRM) ← song song với C3, chỉ cần A               │
Phase 4  ─ hardening, test NFR, security, deploy ──────────────┘
```

Điểm khóa dependency:
- **Widget thủy văn (MOD-01) là điểm giao B×C** — chốt contract API nội bộ MOD-03 (endpoint, token, schema JSON) cuối Phase 1, mock trước khi C2 xong.
- **`org_units` thiết kế 1 lần cho cả XN (MOD-02) và phòng ban (MOD-04)** — sai ở đây phải migrate đau.
- **Quan hệ `station_constructions`** (điểm đo↔công trình) chốt trước khi C2 gắn cảnh báo vào công trình (A2b).
- **Workflow engine chốt trong Phase 0** — tránh 4 bản copy state machine.
- Báo cáo (C3) đọc từ bảng agg — cron tổng hợp phải xong trước UI báo cáo.
- **Tích hợp văn bản (CN-01.7) + phương án tích hợp** — chờ khảo sát kỹ thuật hệ thống nguồn (business-open-questions E3); mock adapter trước.

---

## 4. TỔ CHỨC CODE (Modular Monolith)

```
backend/
├── core/            # Nhóm A: auth, rbac, orgunit, attachment, workflow,
│                    #  notification, jobs, audit, settings, backup/restore
├── content/         # Nhóm B / MOD-01: article, category, comment, contact,
│                    #  siteconfig, search, external-documents (tích hợp văn bản)
├── operations/      # MOD-02: construction, maintenance, gis, dashboard,
│                    #  🔷 logbook, incident, report
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
| BE 2 | Attachment/notification/audit/backup-restore | B: CMS + văn bản + C1 | 🔷 nhật ký + sự cố / D leave |
| FE 1 | Setup 2 app + design system | public-web + CMS admin | GIS + Dashboard |
| FE 2 | — (join Phase 1) | admin: công trình + hydro chart | D: HRM UI + danh bạ |
| QA/DevOps | CI/CD, 3 môi trường | test theo module | test NFR, security, go-live |

---

## 6. CHECKLIST QUYẾT ĐỊNH CẦN CHỐT TRƯỚC KHI CODE

> Cập nhật 2026-08-06: đồng bộ SRS.

1. ✅ **Cơ chế lấy dữ liệu thủy văn: CONFIRMED** — polling API ngoài (module `hydro`). Danh mục điểm đo + mã ánh xạ API đã rõ trong SRS §3.3 (đóng A2). ⬜ Còn chờ: tài liệu API thật (endpoint, schema, auth, rate limit) — dev theo interface + mock adapter.
2. ✅ **Cấu trúc module: CONFIRMED (2026-08-06)** — theo SRS: MOD-01 CMS+văn bản, MOD-02 vận hành+GIS, MOD-03 thủy văn (module `hydro` riêng), MOD-04 HRM, MOD-05 quản trị.
3. ✅ Queue: **DB-backed queue + ShedLock**.
4. ✅ Chart: **ECharts**.
5. ✅ Base map: **OSM mặc định** (Leaflet/MapLibre), Google Maps optional. ⬜ Shapefile (SRS §4.6) — chốt ở thiết kế chi tiết (F7).
6. ✅ Database: **PostgreSQL 16 + PostGIS**.
7. ✅ Admin UI: **Ant Design 5**; Public web: **Next.js + Tailwind**.
8. ✅ **Quy mô triển khai: CONFIRMED** — v1 1 node, bỏ Redis, worker in-process, ShedLock giữ sẵn. Trọng tâm: backup DB + PITR + test restore. Xem `architecture-review.md` §6.
9. ✅ **Restore UI: CONFIRMED (2026-08-06)** — làm nút restore (M5.11) + bảo vệ nhiều lớp (`architecture-review.md` §7.3).
10. 🔴 **Scope phần mở rộng 🔷** (nhật ký vận hành/sự cố/BC-01..08) — *chờ khách xác nhận* (business-open-questions F1). Ảnh hưởng khối lượng Phase 3.
11. ⬜ Phương án tích hợp hệ thống văn bản (SSO/API/CSDL) — quyết phạm vi CN-01.7. *Chờ confirm.*
12. ⬜ Mẫu báo cáo chuẩn công ty (file thật) — cần trước khi làm template engine. *Chờ confirm.*
13. ⬜ Quan hệ điểm đo↔công trình (A2b); kế hoạch vụ mùa (A1). *Chờ confirm.*
