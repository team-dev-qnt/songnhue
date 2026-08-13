# ARCHITECTURE & TECH REVIEW — QUYẾT ĐỊNH CHỐT

> Review kiến trúc/tech trong `function-spec.md` + `implement.md` theo 5 ưu tiên: **độ chính xác — nghiệp vụ chuẩn — tối ưu — vận hành/bảo trì — scale tương lai**.
> Trạng thái: ✅ ĐÃ CHỐT (2026-07-20). Các file spec đã được cập nhật theo bản này.

---

## 1. TÓM TẮT THAY ĐỔI SO VỚI ĐẶC TẢ GỐC

| # | Hạng mục | Gốc | Chốt | Lý do |
|---|---|---|---|---|
| 1 | Database | MySQL 8 Primary-Replica | **PostgreSQL 16 + PostGIS** | GIS native (spatial query, validate GeoJSON trong DB), partitioning khai báo cho time-series, full-text `unaccent` tiếng Việt, JSONB cho event payload. MySQL spatial + ngram yếu hơn rõ rệt cho đúng bài toán này |
| 2 | Job queue | "Redis Queue / RabbitMQ" (chưa chốt) | **DB-backed queue (SKIP LOCKED) + ShedLock** | Job báo cáo/polling phải **transactional cùng dữ liệu nghiệp vụ** (ưu tiên chính xác). Redis queue có thể mất job khi crash; RabbitMQ thêm 1 thành phần hạ tầng phải vận hành. Redis chỉ còn vai trò cache |
| 3 | Cron trên 2 app server | Không đề cập | **ShedLock (distributed lock)** | 2 node cùng chạy cron → polling/tổng hợp/hẹn giờ đăng chạy 2 lần → sai dữ liệu. Bắt buộc phải có |
| 4 | Auth | JWT TTL 8h | **Access token 30' + Refresh token rotation, httpOnly cookie** | JWT 8h không thu hồi được khi lộ token/khóa tài khoản; token trong localStorage dính XSS. Refresh rotation + denylist (bảng DB, xem §6) giải quyết cả hai |
| 5 | Timestamp | "UTC+7, lưu Unix timestamp" | **Lưu `timestamptz` UTC, hiển thị UTC+7** | Lưu theo múi giờ địa phương là nguồn bug kinh điển (DST không có ở VN nhưng so sánh/tổng hợp kỳ vẫn dễ sai). Convert chỉ ở tầng hiển thị |
| 6 | Kiểu số liệu | Không quy định | **NUMERIC/BigDecimal cho mọi số đo + tiền; cấm float/double** | Lưu lượng, mực nước, lương, tổng hợp kỳ — sai số float tích lũy qua Σ là không chấp nhận được với ưu tiên chính xác |
| 7 | Backup | Dump hàng ngày, retention 30 ngày | **+ WAL archiving (PITR), RPO ≤ 15'** | Dump ngày = mất tối đa 24h dữ liệu quan trắc + nhật ký. PITR khôi phục về bất kỳ thời điểm nào |
| 8 | Chart | "ECharts hoặc Highcharts" | **ECharts** | Highcharts tính phí license thương mại; ECharts đủ tính năng (zoom, threshold line, export) |
| 9 | Base map | "Google Maps / OSM" | **OSM (default) + Google Maps optional** | Google Maps JS API tính phí theo usage; OSM tile miễn phí đủ cho bài toán marker + layer nội bộ. Để config switch được |
| 10 | Admin UI | Không quy định | **Ant Design 5 + design tokens** | Hệ nặng Table/Form/Tree/Dashboard — AntD mạnh nhất mảng này, giảm lượng component tự viết |
| 11 | Public web | "React SSR" (chung chung) | **Next.js (SSR/ISR) + Tailwind, tách app riêng** | Trang tin tức cần SEO thật; ISR cache trang bài viết → nhanh + giảm tải BE |
| 12 | Mã hóa dữ liệu nhạy cảm | "AES-256" (chung chung) | **AES-256-GCM tầng app, key ngoài DB (env/Vault), tách bảng `employee_sensitive`** | GCM có authentication (chống sửa trộm); key không nằm cùng DB backup; tách bảng để phân quyền + audit riêng |

Giữ nguyên (đã đúng): **Modular Monolith** (đúng cỡ dự án, đúng hướng scale), Spring Boot, MinIO, Prometheus + Grafana.

> **Cập nhật 2026-07-21:** chốt lại quy mô triển khai theo hướng tối giản — **v1 chạy 1 node**, **bỏ Redis**, **worker in-process**. Chi tiết + lý do ở **§6**. Bảng §3 đã phản ánh thay đổi này.

---

## 2. REVIEW THEO 5 TIÊU CHÍ

### 2.1. Độ chính xác (Correctness)

- **Mọi giá trị tính toán tính ở BE, FE chỉ hiển thị**: số giờ chạy máy, lưu lượng bơm (Q×h×3600), tổng hợp kỳ, số dư phép. FE không bao giờ gửi giá trị đã tính lên server.
- **Bảng tổng hợp (agg_daily/weekly/monthly) phải idempotent**: cron chạy lại không nhân đôi số liệu — dùng UPSERT theo khóa kỳ, có cột `computed_at`. Khi nhật ký được sửa (Admin duyệt) → đánh dấu kỳ liên quan dirty, tính lại.
- **Alert engine**: đánh giá rule trong cùng transaction ghi reading; alert event có unique key `(rule_id, thời điểm bắt đầu)` chống bắn trùng; hysteresis (delay X phút) lưu trạng thái trong DB, không trong memory (không mất khi restart, và sẵn sàng khi thêm node).
- **Raw data bất biến**: bảng `hydro_raw_logs` append-only, không UPDATE/DELETE — là nguồn sự thật để tái xử lý.
- **Số dư phép tính lại từ đơn (derive), không cộng trừ tích lũy** — tránh lệch khi hủy/sửa đơn.

### 2.2. Nghiệp vụ chuẩn (Business integrity)

- Workflow engine (Nhóm A) là **nơi duy nhất** đổi trạng thái — cấm UPDATE trạng thái trực tiếp ở service nghiệp vụ; mọi transition qua engine → tự động audit + notify + check quyền.
- Validation nghiệp vụ (bảng validation CN-02.5, ngưỡng 120% thiết kế...) đặt ở **domain layer**, không chỉ ở FE/controller — và có unit test từng rule.
- RBAC scope theo XN: implement bằng **filter tầng repository** (mọi query tự động thêm điều kiện đơn vị theo user) — không dựa vào từng dev nhớ thêm WHERE. NFR-06 yêu cầu test 100%: viết integration test ma trận role × chức năng.
- Soft delete + audit old/new value cho mọi entity nghiệp vụ (đã có trong spec — giữ, thực thi bằng interceptor chung).

### 2.3. Tối ưu (Performance)

- Time-series: partition `hydro_readings` theo tháng (PostgreSQL native); dashboard/báo cáo đọc từ bảng agg, **không bao giờ** scan bảng reading raw. Nếu sau này >50 trạm × tần suất cao → gắn TimescaleDB extension, không phải đổi kiến trúc.
- "Cache" không dùng Redis (v1): reading mới nhất mỗi trạm ở bảng `hydro_latest` (poller UPSERT), cấu hình site ở Caffeine in-process. Widget public đọc `hydro_latest` — API bên thứ 3 chết thì vẫn phục vụ bản ghi cuối (graceful degradation, lại transactional).
- Public web: Next.js ISR (revalidate khi xuất bản bài) → trang tin gần như static, đạt NFR < 3s/4G dễ dàng.
- GIS: marker + trạng thái trả về dạng GeoJSON đã cache; layer tĩnh (kênh mương, ranh giới) serve như static file từ MinIO + CDN header, không query DB mỗi lần pan/zoom.

### 2.4. Vận hành & bảo trì (Operability)

- **Flyway** cho migration DB — mỗi module tự quản version migration của mình (đã ghi trong implement.md).
- **springdoc-openapi**: API docs tự sinh, là contract giữa FE/BE và tài liệu bàn giao (khớp WBS mục 4.3).
- Log JSON có **correlation-id** xuyên suốt request → job → notification (debug luồng async).
- Health check endpoint riêng cho: app, worker, kết nối telemetry (Nginx + Prometheus dùng chung).
- Alert vận hành tách khỏi alert nghiệp vụ: Prometheus alert (hạ tầng) ≠ Alert thủy văn (nghiệp vụ) — 2 kênh, 2 đối tượng nhận.
- Runbook tối thiểu: khôi phục PITR, xoay key mã hóa, **xử lý poller chết / trạm mất tín hiệu kéo dài** (ưu tiên cao — không backfill được), retry job Failed.

### 2.5. Scale tương lai

- Modular Monolith với ràng buộc "chỉ gọi qua service interface" (đã có trong implement.md) → module nào nóng (khả năng cao là `operations/hydro`) tách ra service riêng sau này mà không đập cấu trúc.
- App stateless (session/denylist ở DB, file ở MinIO) → thêm node App Server chỉ là dựng thêm instance + sửa config Nginx + bật ShedLock.
- Worker tách process ngay từ đầu → scale số worker độc lập với web.
- DB: bắt đầu 1 node PostgreSQL + PITR; thêm read-replica khi báo cáo/analytics nặng (không cần ngay — bảng agg đã giảm tải); partition đã sẵn cho data 5 năm.
- Điểm cần giữ kỷ luật nhất để scale được: **không cho module import repository của nhau** — đề xuất chặn bằng ArchUnit test trong CI.

---

## 3. TECH STACK CHỐT (bảng thay thế §0.2 function-spec.md)

| Thành phần | Công nghệ chốt |
|---|---|
| Kiến trúc | Modular Monolith (Spring Modulith-style), Layered; ArchUnit enforce boundary |
| Public web | Next.js (SSR/ISR) + Tailwind CSS |
| Admin app | React 18 + Vite + Ant Design 5 + TypeScript; ECharts |
| Backend | Spring Boot 3 (Java 21), springdoc-openapi, Flyway |
| Auth | Access token 30' + Refresh rotation (httpOnly cookie), BCrypt, denylist bảng DB |
| Database | PostgreSQL 16 + PostGIS; partition theo tháng cho time-series; `unaccent` full-text |
| Cache | **Không Redis (v1)** — bảng `hydro_latest` (Postgres, poller UPSERT) + Caffeine in-process (site config, TTL ngắn). Xem §6 |
| Job/Cron | DB-backed queue (SELECT … FOR UPDATE SKIP LOCKED) + Spring Scheduler + ShedLock (giữ sẵn, bật khi ≥2 node) |
| File storage | MinIO (S3-compatible) — dùng ngay để app stateless, sẵn sàng thêm node |
| Worker | **In-process (v1)** — cùng process app, bounded thread pool; giữ Spring profile `worker` để tách process về sau. Xem §6 |
| GIS | Leaflet/MapLibre + OSM tiles (Google Maps optional qua config); GeoJSON/KMZ |
| Monitoring | Prometheus + Grafana + Micrometer; log JSON + correlation-id, rotation 30 ngày |
| Backup | pg_dump hàng ngày + WAL archiving (PITR, RPO ≤ 15'), retention 30 ngày |
| CI/CD | Pipeline test (unit + integration Testcontainers + ArchUnit) → build → rolling/blue-green |
| Bảo mật | HTTPS/TLS 1.3, CSP, rate limiting, malware scan upload, AES-256-GCM (key ngoài DB) |

---

## 4. DESIGN SYSTEM (chốt cho FE)

- **Nền tảng**: Ant Design 5 (admin) — theme token hóa theo bộ nhận diện công ty (primary/secondary color từ CN-01.5 site config).
- **Design tokens chung 2 app**: màu trạng thái thống nhất toàn hệ thống (đã có trong Phụ lục function-spec: xanh/vàng/đỏ/xám/đen) — định nghĩa 1 lần trong tokens, AntD theme + Tailwind config + ECharts theme cùng đọc.
- **Bộ component nghiệp vụ dùng chung** (xây trên AntD, đặt trong `admin-app/src/components/business/`): `StatusBadge` (màu trạng thái), `ThresholdValue` (số liệu đổi màu theo ngưỡng), `ApprovalActions` (nút theo workflow engine), `OrgUnitTreeSelect`, `AttachmentPanel` (upload/version/hạn), `DateRangeFilter`, `ExportButton` (gọi async job + theo dõi trạng thái).
- **Màn hình lớn Phòng điều hành**: chế độ hiển thị riêng của Dashboard (route `?mode=wall`): font/marker to, auto-rotate giữa các tab, dark theme, không thao tác — không xây app riêng.
- Responsive breakpoints: 360 / 768 / 1200 / 1920 / 2560px (theo NFR-09).

---

## 5. VIỆC CẦN LÀM THEO REVIEW (đã phản ánh vào spec)

1. ✅ `function-spec.md` §0.2 — thay bảng tech stack; sửa quy ước timestamp (Phụ lục).
2. ✅ `implement.md` — checklist quyết định: đóng các mục 3, 4, 5 (queue, chart, map); bổ sung ShedLock + ArchUnit vào Nhóm A.
3. ⬜ Khi bắt đầu Phase 0: setup Testcontainers (PostgreSQL + PostGIS) cho integration test; viết bộ test ma trận RBAC trước khi code nghiệp vụ.
4. ⬜ Còn mở (ngoài phạm vi tech): contract API Telemetry thật (mục 6.1 implement.md) và khả năng tích hợp hệ thống văn bản (6.2) — cần khảo sát với bên thứ 3, quyết trong giai đoạn SRS.

---

## 6. QUYẾT ĐỊNH V1 — TRIỂN KHAI TỐI GIẢN 1 NODE (2026-07-21)

### 6.1. Bối cảnh & nguyên tắc
Workload thực tế: **100–300 user nội bộ** (đọc là chính, đo P95 ở 50 user), **không có stream real-time phức tạp**, không có concurrency kiểu TMĐT. Bản chất hệ thống = **lấy dữ liệu API bên thứ 3 theo batch thời gian + vài cronjob tổng hợp theo batch**. Điểm nóng **không phải** tải request mà là **tính đúng đắn của batch** (polling, tổng hợp kỳ, hẹn giờ đăng).

Theo đúng thứ tự ưu tiên (chính xác → nghiệp vụ → tối ưu → **vận hành/bảo trì > scale**): mỗi thành phần hạ tầng phải tự chứng minh nó đáng nuôi. Kết luận: **cắt bớt hạ tầng chạy nền, dồn công vào tối ưu DB + logic xử lý + convention + lock tại tầng xử lý data.**

### 6.2. Chốt
| Hạng mục | Trước | V1 chốt | Lý do |
|---|---|---|---|
| Số node app | 2 (Active-Passive) | **1 node** | Hệ nội bộ, giờ hành chính; NFR-01 cho phép downtime ~15'/lần. Backup + PITR đủ để khôi phục. Không nuôi HA khi chưa cần |
| Redis | Cache + session + denylist | **Bỏ** | `hydro_latest` (Postgres, poller UPSERT) phục vụ "reading mới nhất" + graceful degradation, lại transactional; site config → Caffeine in-process; denylist refresh token → bảng DB. Xóa nguyên 1 service phải vận hành/backup/bảo mật |
| Worker | Process riêng, 2 worker | **In-process** (bounded pool) | Job nhẹ (poll vài chục trạm, báo cáo tháng < 60s, tần suất thấp). Chạy chung process cho gọn; giữ Spring profile `worker` để tách sau khi đo thấy nặng |
| ShedLock | Bắt buộc (2 node) | **Giữ trong code, mặc định tắt** | 1 node thì cron không đè nhau; nhưng chỉ là 1 bảng DB, bật lại khi lên ≥2 node là xong |

### 6.3. Trọng tâm bù lại (thay cho hạ tầng)
Vì bỏ hạ tầng đỡ tải, phải làm chắc ở tầng data — **6 chốt chặn không được cắt** (chi phí ops ~0, bảo vệ ưu tiên số 1):
1. Chống job chạy trùng/đè (ShedLock khi ≥2 node) + chống **overlapping run** (kỳ trước chưa xong kỳ sau đã chạy).
2. **Idempotent UPSERT** cho bảng agg (khóa theo kỳ + `computed_at`).
3. `hydro_raw_logs` **append-only** — nguồn sự thật để tái xử lý.
4. **Alert dedup**: unique key `(rule_id, thời điểm bắt đầu)`; hysteresis lưu DB không lưu memory.
5. Mọi giá trị tính ở BE; `timestamptz` UTC; NUMERIC/BigDecimal.
6. **Constraint DB** (unique/FK/check) là chốt chặn cuối, không chỉ validate ở code.

Chiến lược lock cho batch (đủ, không cần hơn): **ShedLock** cho scheduled job · **SKIP LOCKED** cho queue lấy việc song song · **advisory lock theo trạm/kỳ** đảm bảo 1 trạm chỉ 1 luồng xử lý tại 1 thời điểm.

### 6.4. Điều kiện để "thêm node 2 = chỉ đổi cấu hình" (thiết kế v1 phải giữ)
- **App stateless tuyệt đối**: không giữ state trong memory (session/cache/hysteresis/tiến độ job đều ở DB hoặc Caffeine TTL-ngắn chấp nhận lệch). File **luôn ở MinIO** ngay từ v1, không ghi filesystem local.
- **ShedLock + DB-backed queue có sẵn** từ đầu (chạy nhưng 1 node) → thêm node không phát sinh chạy trùng.
- Cấu hình hoá: `app.nodes`, `worker.enabled`, `shedlock.enabled` đọc từ env → thêm node = dựng thêm 1 instance + bật Nginx upstream + bật ShedLock, **không sửa code**.
- Giữ ranh giới module (ArchUnit) để sau tách `operations/hydro` hoặc `worker` ra process/service riêng khi đo thấy nóng.

### 6.5. Ưu tiên vận hành số 1: BACKUP DATABASE
Vì 1 node, DB là điểm chịu rủi ro lớn nhất — backup phải chắc:
- **pg_dump hàng ngày** (logical, retention 30 ngày) **+ WAL archiving liên tục (PITR)**, RPO ≤ 15'.
- **Kiểm thử phục hồi định kỳ** (restore thử ra môi trường staging) — backup không test coi như không có.
- WAL/dump lưu **khác đĩa/khác máy** với DB chính; key mã hóa `employee_sensitive` lưu tách, không nằm trong cùng bản backup.
- Runbook PITR: khôi phục về thời điểm bất kỳ; RTO mục tiêu ghi rõ khi chốt SRS.

---

## 7. ĐỒNG BỘ THEO SRS v1.0 — TÁI CẤU TRÚC MODULE & RESTORE UI (2026-08-06)

> Đối chiếu `function-spec.md` với **SRS_QuanTriDieuHanh_TLSN ver 06.8.2026 (SRS v1.0)**. 3 quyết định dưới đây **thắng** các bản mô tả module cũ trong function-spec/implement. function-spec đã lên **v2.0**.

### 7.1. Tái cấu trúc 5 module theo SRS
| Hạng mục | Cũ (nội bộ v1) | Chốt theo SRS | Lý do |
|---|---|---|---|
| Thủy văn | Gộp trong MOD-02 (vận hành) | **Tách thành MOD-03 riêng** | SRS §3.3 tách "Quản lý dữ liệu thủy văn" thành module độc lập (pipeline API + điểm đo + cảnh báo + báo cáo). Khớp bản chất kỹ thuật: data pipeline chạy nền, vòng đời/scale khác nghiệp vụ công trình. Trùng với ranh giới code đã dự kiến (`operations/hydro` — nay nâng thành module `hydro`) |
| Tích hợp văn bản | MOD-04 riêng | **Gộp vào MOD-01 (CN-01.7)** | SRS xếp M1.8 "tích hợp văn bản điều hành" trong Module 1 (E-Portal). Bản chất = hiển thị/đồng bộ lên cổng, không có DB nghiệp vụ riêng → thuộc nhóm Content |
| HRM | MOD-03 | **MOD-04** | Đổi số theo thứ tự SRS |
| Quản trị | MOD-05 | **MOD-05** (giữ số, +chức năng) | SRS Module 5 bổ sung: health-check, thông báo hệ thống, quản lý phiên + đăng xuất từ xa, cảnh báo đăng nhập bất thường, xuất/nhập cấu hình, ma trận quyền theo màn hình |

**Hệ quả kỹ thuật**: module code backend nay là `core / content / operations / hydro / hr` (thêm `hydro`). Ranh giới ArchUnit: `content` (widget) và `operations` (GIS marker, dashboard) gọi `hydro` **chỉ qua service interface `spi/`**, không import repository. `hydro_latest` là điểm tích hợp chính giữa `hydro` → widget/GIS/dashboard.

### 7.2. ~~Giữ phần mở rộng 🔷 ngoài SRS~~ — **HẾT HIỆU LỰC**

> ⚠ **Mục này đã bị thay thế hoàn toàn (12/8/2026) — xem §8.** Toàn bộ phần mở rộng ngoài SRS đã được Công ty chốt: nhật ký vận hành ❌ · phiếu sự cố riêng ❌ (gộp vào `maintenance_logs` — G1 PA A) · BC-01/02/03/04/07/08 ❌ · BC-06/09/10 ✅ · tình hình vận hành cống ✅ (CN-02.11 — G4). **Không còn hạng mục 🔷 nào, không còn cờ bật/tắt scope trong module `operations`.**

### 7.3. Restore qua UI (SRS M5.11) — ĐẢO quyết định E1 cũ + biện pháp bảo vệ
SRS M5.11/UC5.6 yêu cầu Admin chọn bản backup và **khôi phục qua UI**. Trước đây (business-open-questions E1) đề xuất restore chỉ qua runbook ops vì rủi ro cao. Nay **làm nút restore UI** nhưng bắt buộc kèm chốt chặn:

- **Chỉ Super Admin + 2FA (TOTP)** mới thấy/gọi được chức năng.
- **Xác nhận nhiều bước**: gõ đúng tên hệ thống + lý do restore; cảnh báo "ghi đè toàn bộ dữ liệu hiện tại".
- **Chạy async có tiến độ**, **đặt hệ thống vào maintenance mode** trong lúc restore (chặn ghi); ghi **security event** + audit.
- **Khuyến nghị restore ra Staging trước** để đối chiếu; production restore là thao tác có phê duyệt.
- **Runbook PITR giữ song song**: UI chỉ khôi phục từ bản backup logic (pg_dump) đã chọn; khôi phục về **thời điểm bất kỳ** (WAL/PITR, RPO ≤ 15') vẫn là quy trình ops có runbook — UI không thay thế được. UI hiển thị trạng thái backup gần nhất + link tới runbook PITR.
- Nguyên tắc §6.5 (backup là ưu tiên vận hành số 1, test restore định kỳ) **không đổi**.

### 7.4. Điểm khác cần theo dõi (đã ghi business-open-questions mục F — nay đã đóng, xem §8)
Trạng thái bản ghi thủy văn 3 mức (Hợp lệ/Nghi ngờ/Loại bỏ — F2); lưu vực/khu tưới tiêu (F3); công cụ GIS đo/xuất bản đồ (F4); chức năng MOD-05 mới (F5); Shapefile (F7); NFR lệch nhẹ uptime 99% vs 99.5%, 200 vs 100–300 user, 2FA (F8). Các mục này không đảo quyết định kiến trúc đã chốt — chỉ bổ sung phạm vi. **→ Toàn bộ đã được khách trả lời ngày 12/8/2026, xem §8.**

---

## 8. ÁP DỤNG CÂU TRẢ LỜI BUSINESS OPEN QUESTIONS (2026-08-12)

> Nguồn: `docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md` (đợt 1, mục A–F) + **confirm đợt 2 mục G ngày 12/8/2026** + khảo sát thực tế hệ thống nguồn `songnhue.bhh40.net`. Mục này **thắng** các mô tả cũ. `function-spec.md` đã lên **v2.2**.

### 8.1. Thay đổi phạm vi ảnh hưởng kiến trúc

| # | Quyết định | Hệ quả kiến trúc |
|---|---|---|
| 1 | **Bỏ Nhật ký vận hành** (B1/F1), thay bằng **Lịch sử sửa chữa** | Bỏ `operation_logs`, `machine_run_records`; **workflow engine giảm 1 use-case lớn**; bảng agg thu nhỏ còn tổng hợp chi phí/số lượt bảo trì (không còn Σ giờ chạy, Σ m³, Σ kWh) → **áp lực tính đúng batch giảm rõ rệt**, quyết định "1 node, worker in-process" (§6) càng đúng |
| 2 | **Bỏ kế hoạch vụ mùa** (A1) + **bỏ diện tích tưới tiêu** (B5) | Bỏ nhóm bảng kế hoạch; bỏ BC-04/BC-07; M3.18 chỉ còn so sánh theo kỳ (query trực tiếp trên agg) |
| 3 | **Lưu vực = trường text** (F3) | Bỏ `irrigation_zones`, bỏ nhu cầu polygon lưu vực trong PostGIS ở v1 |
| 4 | **Trạng thái bản ghi 2 mức, Nghi ngờ VẪN GHI** (F2) | ⚠ Đảo giả định cũ: `hydro_readings` nay chứa cả bản ghi chưa tin cậy → **mọi truy vấn báo cáo/alert/agg phải lọc `quality = HOP_LE`**. Bổ sung index theo `quality`; thêm luồng duyệt/xóa có audit. Đây là điểm dễ sinh bug số liệu nhất — bắt buộc test |
| 5 | **Bỏ SMS ở v1** (B7) | Notification chỉ còn In-app + Email → bỏ phụ thuộc nhà cung cấp SMS khỏi đường go-live; `SmsSender` giữ interface, cấu hình tắt |
| 6 | **CN-01.7 đổi từ đồng bộ dữ liệu → lưu credential + auto-login** (E3) | **Bỏ hẳn 1 job đồng bộ định kỳ + bảng `external_documents`** (giảm rủi ro vận hành). Đổi lại phát sinh **nghĩa vụ bảo mật mới**: lưu credential bên thứ 3 mã hóa 2 chiều → xem §8.3 |
| 7 | **Màn hình lớn = TV 85" 4K** (B8) | Wall mode thiết kế base 3840×2160; không cần app riêng, vẫn là route `?mode=wall` như §4 |
| 8 | **Mọi tham số vận hành để config** (C1, D5, F5, E3) | Bảng `settings` trở thành thành phần bắt buộc từ Phase 0, có UI + validate + export/import (M5.17) — không được để giá trị nghiệp vụ nằm trong `application.yml` |
| 9 | **Gộp sự cố vào Lịch sử sửa chữa** (G1 = PA A) | **Bỏ hẳn bảng `incidents` + workflow 7 trạng thái**. Workflow engine chỉ còn 3 trạng thái trên `maintenance_logs` → tiếp tục giảm tải Core. Trạng thái "Sự cố (đỏ)" của công trình là **giá trị dẫn xuất** (derived) từ bản ghi đang mở, **không phải cột nhập tay** → cần view/service tính trạng thái, cấm UPDATE trực tiếp |
| 10 | **Tình hình vận hành cống nhập tay + danh mục mã CRUD** (G4) | Thêm 2 bảng MOD-02: `operation_status_codes` (danh mục, có `color`, `mapped_construction_status`) + `construction_operation_status` (**append, có lịch sử**, không ghi đè). Màu và ánh xạ trạng thái là **dữ liệu**, không phải enum trong code — tránh phải deploy khi Công ty thêm mã |
| 11 | **Audit giữ 5 năm rồi kết xuất lưu trữ** (G7) | Job kết xuất định kỳ: xuất CSV/Parquet nén + **checksum SHA-256** lên MinIO bucket riêng (versioning, khác bucket media), xong mới xóa khỏi bảng nóng. **Hash chain phải nối tiếp qua ranh giới kết xuất** (lưu hash cuối lô làm điểm neo) — nếu không, chuỗi toàn vẹn đứt và audit mất giá trị chứng minh. Kết xuất lỗi → không xóa dòng nào |
| 12 | **Con số NFR đã chốt nghiệm thu** (G12) | 200 CCU + trang chủ < 3s + báo cáo tháng < 60s là **cam kết hợp đồng**, không còn là mục tiêu nội bộ → phải có **load test trong kế hoạch kiểm thử**, không chỉ test chức năng. 2FA bắt buộc Admin/Admin HR đưa vào Phase 0 (không để cuối) |

### 8.2. Nguồn dữ liệu thủy văn thật — đánh giá kỹ thuật

Endpoint được cấp: `http://songnhue.bhh40.net/api/getmn.aspx?key=<mã số>` (ASP.NET WebForms/IIS 8.5).

**Kết quả kiểm thử 12/8/2026**: ✅ **đấu nối thành công** — endpoint yêu cầu **dấu `;` ở cuối key** (`?key=<mã số>;`), thiếu thì trả chuỗi `not.working`. Trả về **19 bản ghi mực nước** dạng text phân tách bằng `<br>`, mỗi bản ghi `<mã>;dd/MM/yyyy;HH:mm;value=<cm>;`, kèm một trang HTML rỗng ở cuối. Đặc tả parser đầy đủ ở `function-spec.md` CN-03.2.

**⛔ Hai giới hạn của nguồn có ảnh hưởng kiến trúc**:

1. **Không có API lịch sử** — mọi tham số (`date`, `from`/`to`) bị bỏ qua, chỉ trả snapshot hiện tại. → Hệ thống mới là **nơi lưu lịch sử duy nhất**, và **poller là điểm bắt dữ liệu một-lần-duy-nhất, không backfill được**. Hệ quả bắt buộc:
   - `hydro_raw_logs` append-only **ghi nguyên văn response trước khi parse** — đây là bản sao duy nhất tồn tại.
   - **Giám sát poller là hạng mục ưu tiên cao**, ngang backup DB: alert khi không có bản ghi mới quá N phút (Prometheus + email Admin), không đợi người dùng phát hiện.
   - Downtime của app = **mất dữ liệu vĩnh viễn**, không chỉ là gián đoạn dịch vụ → xem lại NFR-01: cửa sổ bảo trì phải ngắn, và nên tách poller thành tiến trình có thể chạy độc lập khi app bảo trì (giữ Spring profile `worker` như §6.2 đã dự phòng — nay có lý do nghiệp vụ rõ ràng để dùng).
   - ✅ **Công ty đã chấp nhận rủi ro này (confirm G3, 12/8/2026)**: *"không có API quét lịch sử, hệ thống tự fetch và ghi lịch sử"* → 3 ràng buộc trên trở thành **yêu cầu bắt buộc của thiết kế**, không còn là đề xuất.
2. **Không có API lượng mưa** (chỉ tồn tại `getmn.aspx`) trong khi biểu nghiệp vụ có cột lượng mưa. Công ty trả lời *"tạm thời chưa có"* → v1 **không có nguồn lượng mưa**; giữ loại chỉ số + chỗ cắm adapter, cột hiển thị `-`. Cách xử lý cuối cùng chờ **G3-a** (chờ endpoint / nhập tay / bỏ hẳn).

**⭐ Nhịp polling — chốt G3 (ảnh hưởng thiết kế scheduler)**: nguồn làm việc theo **khung 10 phút**, dữ liệu mới chỉ lên API trong cửa sổ **`x1:30 → x8:30`**, phần còn lại máy chủ nhận dữ liệu từ máy đo. Công ty chốt **gọi 2 phút/lần vào các phút lẻ** + yêu cầu **rate-limit để không gọi khi response không đổi**. Hệ quả kiến trúc:
- Cron mặc định `45 1/2 * * * *` — **giây 45**, không phải giây 0: gọi đúng đầu phút lẻ đầu tiên là gọi *trước* mốc `01:30`. Là tham số cấu hình.
- **Rate-limit ở tầng ứng dụng, trước khi mở HTTP**: nếu toàn bộ điểm đo hoạt động đã có bản ghi thuộc khung 10' hiện tại → bỏ qua lượt gọi (`sync_logs = SKIPPED_UP_TO_DATE`). Điều kiện dừng phải là **đủ toàn bộ trạm**, không phải "đã có bản ghi đầu tiên" — vì nguồn trả rải rác trong 7 phút. Kỳ vọng 1–3 lần gọi thật/khung thay vì 5.
- Nhịp 2' làm **tăng mật độ ghi `hydro_raw_logs`** (≈720 response/ngày kể cả trùng) → raw log phải có **partition theo tháng + retention riêng ngắn hơn readings** (raw chỉ phục vụ tái xử lý/đối soát). Cần chốt ở thiết kế DB Phase 1.
- Trạng thái **trạm mất tín hiệu** phải suy ra ở phía hệ thống mới (nguồn không có cờ trạng thái): không có bản ghi mới quá N khung → `MẤT_TÍN_HIỆU` → GIS xám, loại khỏi đánh giá ngưỡng.

**✅ Đã gỡ mục chặn MOD-03 (G8b, 12/8/2026)**: Công ty cấp bảng ánh xạ đủ **19/19 mã** ↔ tên điểm đo + vai trò → seed data ở `function-spec.md` CN-03.1. Ba hệ quả thiết kế:
- Enum vai trò phải thêm **`MN_SONG`** (mực nước sông) — 4/19 điểm; điểm loại này **có thể không gắn công trình nào** (trạm thủy văn tham chiếu), không được coi là dữ liệu thiếu.
- **Cấm mọi validate liên điểm đo kiểu "TL > HL"**: số liệu thật có 2/5 cặp bị đảo hợp lệ (cống tiêu tự chảy khi sông ngoài cao). Validate chỉ xét từng điểm đo theo trục thời gian.
- Nguồn phủ **19 điểm, ít hơn** danh sách trên biểu tổng hợp → phạm vi dữ liệu tự động của hệ thống mới **hẹp hơn biểu giấy hiện hành**; phải làm rõ khi nghiệm thu để không bị hiểu là thiếu chức năng (**G8**).

**Đặc điểm dữ liệu (quan sát từ biểu tổng hợp công khai `bieusov01.aspx`)** — căn cứ thiết kế adapter:
- Mực nước theo **cặp TL/HL** cho từng cống/trạm bơm, định vị bằng **tuyến sông + lý trình `K..+..`** → bổ sung `river_name`, `chainage`, `position_role` vào `stations`/`constructions`.
- **Đơn vị nguồn = cm** → adapter chia 100 về **m scale 3**. Sai chỗ này là sai toàn bộ ngưỡng cảnh báo → **unit test bắt buộc cho adapter**.
- Có cờ **"giá trị nội suy"** → không được trộn với số đo trực tiếp.
- **Lượng mưa tích lũy theo ca Đêm/Ngày** (không phải reading tức thời) → nếu có nguồn thì lưu kèm khoảng thời gian tích lũy; **không** đối xử như time-series điểm.
- **Tình hình vận hành cống** (MT/ĐK/ĐTTL/ĐTHL) — ✅ **chốt G4: KHÔNG có trong API**, là dữ liệu **nhập tay** thuộc MOD-02 (CN-02.11), không đi qua adapter thủy văn. Danh mục mã là **bảng CRUD có cột màu + ánh xạ trạng thái**, không phải enum trong code.

**Quyết định kiến trúc**: giữ nguyên hướng đã chốt — `TelemetryAdapter` là interface, `Bhh40Adapter` + `MockAdapter` là 2 implementation, chọn qua config. Nay nguồn đã thông nên `Bhh40Adapter` phát triển được với dữ liệu thật ngay từ Phase 1; `MockAdapter` vẫn giữ để test tự động không phụ thuộc mạng/nguồn.

### 8.3. Lưu credential hệ thống ngoài — rủi ro & biện pháp (mới)

Công ty chốt: lưu thông tin đăng nhập hệ thống văn bản điều hành của người dùng để auto-login. Khảo sát cho thấy hệ thống nguồn dùng **1 "mã số" duy nhất** (không có cặp user/pass), truyền qua **HTTP**.

| Rủi ro | Biện pháp bắt buộc |
|---|---|
| Credential phải mã hóa **2 chiều** (giải mã được) — lộ key = lộ tất cả | AES-256-GCM, key ngoài DB (env/Vault), tách khỏi backup DB, có key rotation; xem `conventions.md` §4.7 |
| Admin/dev có thể tò mò xem credential người khác | Không endpoint nào trả credential; UI mask; không log; loại khỏi export cấu hình (M5.17); mọi truy cập ghi security event |
| Hệ thống nguồn chạy HTTP → nghe lén được | Chỉ gọi từ backend; đề nghị Công ty bật HTTPS; ghi nhận rủi ro tồn dư trong hồ sơ bàn giao |
| Mã số hết hiệu lực | Bắt lỗi, báo "mã số không còn hiệu lực", không lộ chi tiết kỹ thuật |

**Khuyến nghị đã gửi Công ty (G5)**: xin bên quản trị nguồn cấp **link đăng nhập kèm token dùng-một-lần** hoặc SSO → khi đó **không cần lưu credential**, xóa hẳn nhóm rủi ro này. Nếu Công ty chấp nhận, thiết kế đơn giản hơn và an toàn hơn hẳn.

### 8.4. Không đổi

Toàn bộ quyết định §1–§7 giữ nguyên: PostgreSQL 16 + PostGIS, Modular Monolith 1 node, không Redis, DB-backed queue + ShedLock, worker in-process, MinIO, timestamptz UTC, BigDecimal, backup pg_dump + PITR, restore UI có 2FA. Các thay đổi 12/8/2026 **chỉ thu hẹp phạm vi và bổ sung nghĩa vụ bảo mật**, không đảo hướng kiến trúc.
