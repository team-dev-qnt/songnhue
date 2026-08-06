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
- Runbook tối thiểu: khôi phục PITR, xoay key mã hóa, xử lý trạm OFFLINE kéo dài, retry job Failed.

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

### 7.2. Giữ phần mở rộng 🔷 ngoài SRS
Nhật ký vận hành, phiếu sự cố, báo cáo vận hành BC-01..08 **không có trong SRS v1.0**. Giữ trong function-spec (từ tài liệu gốc "Đặc tả hệ thống Website") nhưng đánh dấu 🔷 và **chờ khách xác nhận scope** (business-open-questions F1). Code phần này đặt trong module `operations` (nhóm C3), có thể bật/tắt theo scope chốt.

### 7.3. Restore qua UI (SRS M5.11) — ĐẢO quyết định E1 cũ + biện pháp bảo vệ
SRS M5.11/UC5.6 yêu cầu Admin chọn bản backup và **khôi phục qua UI**. Trước đây (business-open-questions E1) đề xuất restore chỉ qua runbook ops vì rủi ro cao. Nay **làm nút restore UI** nhưng bắt buộc kèm chốt chặn:

- **Chỉ Super Admin + 2FA (TOTP)** mới thấy/gọi được chức năng.
- **Xác nhận nhiều bước**: gõ đúng tên hệ thống + lý do restore; cảnh báo "ghi đè toàn bộ dữ liệu hiện tại".
- **Chạy async có tiến độ**, **đặt hệ thống vào maintenance mode** trong lúc restore (chặn ghi); ghi **security event** + audit.
- **Khuyến nghị restore ra Staging trước** để đối chiếu; production restore là thao tác có phê duyệt.
- **Runbook PITR giữ song song**: UI chỉ khôi phục từ bản backup logic (pg_dump) đã chọn; khôi phục về **thời điểm bất kỳ** (WAL/PITR, RPO ≤ 15') vẫn là quy trình ops có runbook — UI không thay thế được. UI hiển thị trạng thái backup gần nhất + link tới runbook PITR.
- Nguyên tắc §6.5 (backup là ưu tiên vận hành số 1, test restore định kỳ) **không đổi**.

### 7.4. Điểm khác cần theo dõi (đã ghi business-open-questions mục F)
Trạng thái bản ghi thủy văn 3 mức (Hợp lệ/Nghi ngờ/Loại bỏ — F2); lưu vực/khu tưới tiêu (F3); công cụ GIS đo/xuất bản đồ (F4); chức năng MOD-05 mới (F5); Shapefile (F7); NFR lệch nhẹ uptime 99% vs 99.5%, 200 vs 100–300 user, 2FA (F8). Các mục này không đảo quyết định kiến trúc đã chốt — chỉ bổ sung phạm vi.
