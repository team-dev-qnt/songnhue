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
| 7 | Backup | Dump hàng ngày, retention 30 ngày | **Giữ dump hàng đêm; RPO ≤ 24h, RTO ≤ 4h** ⚠ *(đảo lại 13/8/2026)* | Bản chốt 2026-07-20 từng thêm WAL/PITR (RPO 15'). Rà lại quy mô thật (200 CCU nội bộ, giờ hành chính, vài nghìn bản ghi/ngày) → **PITR là over-engineer**, đã gỡ. Chi tiết + rủi ro chấp nhận: §6.5 |
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
- Runbook tối thiểu: **restore từ bản dump đêm**, xoay key mã hóa, **xử lý poller chết / trạm mất tín hiệu kéo dài** (ưu tiên cao — không backfill được), retry job Failed.

### 2.5. Scale tương lai

- Modular Monolith với ràng buộc "chỉ gọi qua service interface" (đã có trong implement.md) → module nào nóng (khả năng cao là `operations/hydro`) tách ra service riêng sau này mà không đập cấu trúc.
- App stateless (session/denylist ở DB, file ở MinIO) → thêm node App Server chỉ là dựng thêm instance + sửa config Nginx + bật ShedLock.
- Worker tách process ngay từ đầu → scale số worker độc lập với web.
- DB: bắt đầu 1 node PostgreSQL + backup dump hàng đêm; thêm read-replica khi báo cáo/analytics nặng (không cần ngay — bảng agg đã giảm tải); partition đã sẵn cho data 5 năm.
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
| Backup | **`pg_dump` hàng đêm**, retention 30 ngày, lưu khác máy — **RPO ≤ 24h, RTO ≤ 4h** (bản tối giản, §6.5) |
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
| Số node app | 2 (Active-Passive) | **1 node** | Hệ nội bộ, giờ hành chính; NFR-01 cho phép downtime ~15'/lần. Backup dump hàng đêm đủ để khôi phục trong RTO 4h. Không nuôi HA khi chưa cần |
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

### 6.5. BACKUP DATABASE — bản tối giản ⚠ **SỬA LẠI 2026-08-13**

> Bản 2026-07-21 quy định pg_dump + **WAL archiving (PITR), RPO ≤ 15'**. Rà lại đúng quy mô thật — **200 CCU nội bộ, chỉ chạy giờ hành chính, ghi vài nghìn bản ghi/ngày, không phải hệ giao dịch liên tục** — thì PITR là over-engineer. **Gỡ PITR, làm vừa đủ, chấp nhận rủi ro có kiểm soát.** Đúng nguyên tắc §6.1: *mỗi thành phần hạ tầng phải tự chứng minh nó đáng nuôi*.

**Cơ chế duy nhất**:

| Hạng mục | Chốt |
|---|---|
| Cơ chế | **`pg_dump -Fc` hàng đêm ~02:00** → nén → checksum SHA-256 → copy sang **VM-3 (khác máy với DB)** |
| **RPO** | **≤ 24 giờ** |
| **RTO** | **≤ 4 giờ** — *đóng mục "RTO ghi rõ khi chốt SRS"*; thực tế 30–60' với DB <20GB |
| Retention | 30 ngày |
| Giám sát | **1 alert duy nhất**: bản backup gần nhất quá 26 giờ |
| Kiểm chứng | Diễn tập restore **1 lần trước go-live** (ghi con số RTO thật vào runbook), sau đó theo quý |
| Key mã hóa | AES + JWT signing key **lưu tách, KHÔNG nằm trong bản backup** |

❌ **Không làm ở v1**: WAL archiving · PITR · `pg_basebackup` · streaming replica · diễn tập restore tự động.

**Rủi ro chấp nhận** (ghi rõ để về sau có căn cứ, không phải quyết định cảm tính):

| # | Rủi ro | Hệ quả xấu nhất | Vì sao chấp nhận |
|---|---|---|---|
| 1 | Mất tối đa **1 ngày dữ liệu nhập tay** | Phải nhập lại số liệu sửa chữa/bài viết/hồ sơ NS trong ngày | Giờ hành chính, khối lượng nhập nhỏ |
| 2 | Mất tối đa **1 ngày dữ liệu thủy văn — vĩnh viễn** | ~2.700 bản ghi mực nước không lấy lại được (nguồn không có API lịch sử) | Công ty đã vận hành không có hệ thống này; biểu giấy vẫn còn. **Giảm nhẹ ở Phase 2** bằng `DurableSpool` + đẩy bản thô lên MinIO |
| 3 | **Không lùi được về thời điểm bất kỳ** | Migration hỏng/xóa nhầm giữa ngày → chỉ về được bản đêm trước | Bù bằng **`pg_dump` tự động ngay trước mỗi lần deploy** — đúng lúc rủi ro cao nhất |
| 4 | **Không HA** — DB chết là dừng dịch vụ | Ngừng 30–60' | NFR-01 (99% ≈ 7.2h/tháng) thừa biên |

**Vì sao không replica**: replica chống chết máy nhưng **nhân bản trung thành cả migration hỏng, adapter parse sai, xóa nhầm** — vốn là kịch bản dễ xảy ra hơn ở đây. Thêm nữa, nguy cơ mất dữ liệu thật **không nằm ở DB mà ở poller** (nguồn không có API lịch sử) → thứ cứu được là spool đĩa local ở tầng ứng dụng, không phải replica.

**Đường nâng cấp** (chỉ là thêm cấu hình, không sửa code): bật `archive_mode=on` + `archive_timeout` + `pg_basebackup` hàng tuần. Cân nhắc khi dữ liệu thủy văn đã tích lũy nhiều năm và mất 1 ngày trở nên đắt · Công ty nâng cam kết uptime · hoặc lên ≥2 node.
> 📌 Lưu ý cho lần nâng cấp: **không replay được WAL lên bản `pg_dump`** — PITR bắt buộc phải có `pg_basebackup` (bản vật lý). Bật WAL archiving mà chỉ có dump logic là vô nghĩa.

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
- **Nguồn khôi phục**: UI khôi phục từ **bản `pg_dump` đêm** đã chọn. Không có khôi phục điểm-thời-gian ở v1 (§6.5) → UI là đường phục hồi **duy nhất** bên cạnh runbook thủ công, phải làm chắc. UI hiển thị trạng thái backup gần nhất.
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

Toàn bộ quyết định §1–§7 giữ nguyên: PostgreSQL 16 + PostGIS, Modular Monolith 1 node, không Redis, DB-backed queue + ShedLock, worker in-process, MinIO, timestamptz UTC, BigDecimal, restore UI có 2FA. *(Riêng backup đã sửa lại thành bản tối giản ngày 13/8/2026 — xem §6.5.)* Các thay đổi 12/8/2026 **chỉ thu hẹp phạm vi và bổ sung nghĩa vụ bảo mật**, không đảo hướng kiến trúc.

---

## 9. QUYẾT ĐỊNH NỀN TẢNG PHASE 0 (2026-08-13)

> Các quyết định phát sinh khi lập kế hoạch dựng codebase. Đây là **quyết định nội bộ của phía phát triển** — không đưa vào `business-open-questions.md`. Kế hoạch chi tiết + trạng thái: `phase0-tracking.md`.

### 9.1. Build & tổ chức mã nguồn

| Hạng mục | Chốt | Lý do |
|---|---|---|
| Repo | **Monorepo** — `backend/` + `frontend/{admin-app, public-web}` + `deploy/` + `docs/` | 1 team, contract FE↔BE đổi cùng nhịp; tránh đồng bộ version chéo repo |
| Build backend | **Maven multi-module**: parent + `core/content/operations/hydro/hr` + `app` (bootstrap) | Phổ biến nhất trong hệ Spring, dễ bàn giao cho team vận hành của Công ty — khớp ưu tiên *vận hành/bảo trì > scale*. Dùng `mvnw` wrapper, không bắt cài Maven |
| Ranh giới module | Mỗi module đúng 5 package `api/application/domain/infra/spi`; **ArchUnit chặn import chéo ngoài `spi/`** | `conventions.md` §1.1 — cài **từ commit đầu**, gỡ sau khi có code rất đau |

### 9.2. Triển khai & môi trường

| Hạng mục | Chốt | Lý do |
|---|---|---|
| Mô hình deploy | **docker-compose trên VM** (không Kubernetes) | v1 chỉ 1 node app (§6.2); thêm tầng k8s là nuôi hạ tầng chưa cần — trái §6.1 |
| Phân bổ **3 VM** | **VM-1** Production (nginx + app + postgres + minio) · **VM-2** Staging (+ đích diễn tập restore) · **VM-3** Backup & Monitoring (kho dump + Prometheus/Grafana) | Backup phải **khác máy** với DB (§6.5); monitoring tách khỏi prod để **còn sống khi prod chết** |
| Chạy local | 2 chế độ: `compose.infra.yml` (chỉ PG+MinIO+MailHog, app chạy **native** từ IDE) và `compose.local.yml` (**full-stack trong Docker**) | Dev BE cần hot-reload native; FE/QA không phải cài JDK |
| **Migration** | Chạy ở **service `migrator` riêng** trước app (`depends_on: service_completed_successfully`); app khởi động với `flyway.enabled=false` | Migration hỏng thì app **không lên nửa vời**; rollback rõ ràng |
| Flyway | `cleanDisabled=true` · `validateOnMigrate=true` · `outOfOrder=false`; `locations` gộp theo module | `cleanDisabled` chặn xóa sạch production do lỡ tay |
| Rollback | Quay lại image tag trước; nếu migration đã đổi schema → restore từ **bản dump pre-deploy** | Không có PITR (§6.5) nên dump pre-deploy là điểm rollback dữ liệu duy nhất |
| CI/CD | **GitHub Actions** (remote đã là GitHub) + image lên **GHCR** | |

### 9.3. Bảo mật vận hành

| Hạng mục | Chốt |
|---|---|
| Secrets | **GitHub Secrets** cho CI · `/opt/songnhue/.env` (chmod 600) trên VM cho runtime · key AES/JWT ở `/opt/songnhue/keys/` **ngoài bản backup DB**. Không dùng Vault ở v1 |
| **DB roles tách quyền** | `songnhue_owner` (chỉ migrator) · `songnhue_app` (**không có DELETE** trên `audit_logs`/`security_events`/`hydro_raw_logs`) · `songnhue_archiver` (DELETE audit, chỉ job kết xuất) · `songnhue_readonly`. GRANT trong migration, CREATE ROLE ở init script |
| **Hash chain audit tính ở DB** (chốt 14/8 khi làm WS-2) | Trigger `SECURITY DEFINER` cấp `seq`/`prev_hash`/`hash`; app chỉ có `INSERT` và **không** có `UPDATE` trên `audit_chain_head` → client không tự nối chuỗi được, gửi hash giả lên cũng bị ghi đè. Đổi lại: insert audit bị tuần tự hóa qua 1 dòng khóa — chấp nhận được với tải vài nghìn bản ghi/ngày. Verify bằng `core_verify_audit_chain()`, **cấm cài lại công thức băm ở Java** |
| **`audit_logs` partition theo tháng** | Tạo sẵn **12 tháng runway** lúc migrate + job hằng tháng giữ ≥6 tháng. Có **partition `DEFAULT`** làm lưới an toàn: job chết thì bản ghi vẫn vào được, chỉ chậm — thà ghi chậm còn hơn `INSERT` lỗi làm hỏng giao dịch nghiệp vụ. Gỡ kẹt: `docs/runbook/audit-partition.md` |
| **Tài khoản Super Admin** | Seed ở trạng thái `PENDING_ACTIVATION`, `password_hash = '!'` (không mật khẩu nào khớp). **Không có mật khẩu mặc định trong repo.** Kích hoạt bằng lệnh bootstrap đọc `BOOTSTRAP_ADMIN_PASSWORD` từ env, chạy 1 lần lúc dựng môi trường (WS-5/T5.7) |
| Rate limit | In-process (Caffeine) qua interface `RateLimitStore` — **1 node nên chấp nhận được**; lên ≥2 node phải đổi impl sang DB |

### 9.4. Nơi dồn công

Vì cắt bớt ở hạ tầng (không replica/PITR/k8s/Vault), trọng tâm đầu tư dồn vào **bảo mật — authentication — authorization**: JWT RS256 có `kid` xoay được · refresh rotation + **reuse detection** thu hồi cả token family · CSRF double-submit · lockout không tiết lộ user tồn tại · **2FA TOTP bắt buộc Admin/Admin HR** · **RBAC 3 tầng** với `@RequirePermission` + Hibernate scope filter theo `org_unit` · lookup qua `public_id` UUID chống IDOR. Kiểm chứng bằng **2 chốt chặn ở CI**: endpoint thiếu `@RequirePermission` → build đỏ, và ma trận role × resource phải pass 100% (NFR-06).

### 9.5. Không dùng filter chain của Spring Security (chốt 14/8, khi làm WS-5)

Dự án **chỉ lấy `spring-security-crypto`** (BCrypt) chứ không kéo `spring-boot-starter-security`. Ba lý do cụ thể, không phải sở thích:

| Vấn đề nếu dùng cả framework | Hệ quả |
|---|---|
| `FilterChainProxy` mặc định nằm ở order **-100** | Chen vào **trước** `CorrelationFilter` (order 10) → đúng những lỗi sớm nhất lại không có `traceId`, tức là mất khả năng tra cứu ở nhóm lỗi cần tra nhất |
| 401/403 do `AuthenticationEntryPoint` sinh ra | **Không đi qua** `GlobalExceptionHandler` → phá envelope + `traceId` (DoD #9) đúng ở nhóm lỗi hay gặp nhất |
| Cơ chế quyền là `@PreAuthorize` | conventions.md §4.2 đã chốt `@RequirePermission` + interceptor + quét deny-by-default ở CI — hai mô hình song song thì có hai nơi để quên |

**Phần khó và dễ sai vẫn dùng thư viện**, không tự cài: BCrypt (`spring-security-crypto`) và JOSE/JWT (`nimbus-jose-jwt` — chính thư viện Spring Security dùng bên trong). Tự viết đúng hai thứ đã cài sẵn: `TotpGenerator` (RFC 6238 có bộ vector kiểm thử chính thức nên chứng minh được bằng test) và `HashUtils` (SHA-256 + so sánh constant-time).

Đánh đổi phải chấp nhận: những gì Spring Security cho sẵn thì mình tự lo — hiện đã có CSRF, rate limit, lockout, denylist; **security headers do nginx đặt** (WS-11/T11.5), không đặt ở tầng ứng dụng.

### 9.6. Hàng đợi job và ShedLock là hai thứ khác nhau (chốt 15/8, khi làm WS-6)

Hai cơ chế cùng liên quan tới "chạy nền" nhưng giải hai bài toán **ngược nhau**, nên cài đặt cũng ngược nhau:

| | Hàng đợi `jobs` | Job theo lịch |
|---|---|---|
| Câu hỏi | Ai *cũng* nên lấy việc | Ai *duy nhất* được chạy |
| Cơ chế | `SELECT … FOR UPDATE SKIP LOCKED` | ShedLock (khoá qua bảng `shedlock`) |
| Thêm node | Nhanh lên tuyến tính | Không nhanh hơn, chỉ an toàn hơn |
| Hỏng nếu dùng nhầm | Bọc ShedLock quanh worker → mất sạch khả năng mở rộng, quay về một node | Không khoá → bản sao lưu chạy hai lần, thông báo gửi hai lần |

**Hệ quả thực tế**: `JobWorker` **không** mang `@SchedulerLock`, và lên ≥2 node thì lớp đó không cần sửa dòng nào.

**Việc theo lịch thì chỉ *đặt việc*, không tự làm.** `MaintenanceScheduler` dùng `@Scheduled` để đẩy job vào hàng đợi với khoá chống trùng theo ngày (VD `TOKEN_CLEANUP:2026-08-15`), rồi handler mới làm việc thật. Nhờ vậy:

- Dùng lại toàn bộ bộ máy đã có: trạng thái, số lần thử, backoff, màn hình theo dõi, thu hồi job treo. Việc chạy thẳng trong `@Scheduled` hỏng thì **im lặng** — không trạng thái, không thử lại, không ai nhìn thấy.
- **Không cần ShedLock cho nhóm này**: hai node cùng hẹn giờ thì node thứ hai va chỉ mục duy nhất `uq_jobs_dedup_active` và nhận lại chính job node thứ nhất vừa tạo. **DB đã là điểm đồng bộ** — thêm một cơ chế khoá nữa là hai nguồn sự thật cho cùng một việc.

ShedLock vẫn giữ (cài sẵn, mặc định tắt) cho những việc theo lịch *không* đi qua hàng đợi được — chủ yếu là các tác vụ hạ tầng ở WS-7.

### 9.7. Ba bẫy auto-configuration của Spring Boot đã sập khi làm WS-6

Ghi lại vì cả ba đều **im lặng**: không lỗi nào biểu hiện ở đúng chỗ sai, và cả ba đều chỉ lộ ra khi chạy thật.

| Bẫy | Hệ quả | Cách tránh |
|---|---|---|
| Khai bean `DataSource` | `DataSourceAutoConfiguration` mang `@ConditionalOnMissingBean` → Boot **ngừng tạo DataSource chính**; cả app chạy bằng vai trò phụ | Không khai bean thuộc kiểu Boot tự cấu hình; bọc vào **kiểu riêng** |
| Khai bean `JdbcTemplate` | Y hệt, ở tầng `JdbcTemplateAutoConfiguration` | Như trên — `ArchiverJdbc` là ví dụ |
| `@ConditionalOnBean` trên `@Component` | Spring **chỉ bảo đảm** điều kiện này cho lớp auto-configuration; với bean quét theo `@Component` thì phụ thuộc thứ tự nạp và có thể bỏ qua âm thầm | Đặt điều kiện trên **tham số cấu hình** (`@ConditionalOnProperty`) trong một lớp `@Configuration` |

Bài học chung: **thêm một bean hạ tầng cùng kiểu với thứ Boot tự cấu hình là thay thế nó, không phải bổ sung.** Khi cần một kết nối/khách hàng thứ hai, luôn gói vào kiểu riêng của dự án.


### 9.8. Thứ tự aspect quanh transaction — và bốn cơ chế "xanh mà không chạy" (chốt 15/8, khi làm WS-10)

**Bối cảnh.** WS-10 dựng bộ luật kiến trúc và test tích hợp trên DB thật. Nó tìm ra một lỗi nặng hơn
mọi thứ WS-6 phát hiện, và ba biến thể của cùng một kiểu hỏng.

#### 9.8.1. `ScopeFilterAspect` phải nằm BÊN TRONG bộ chặn transaction

Trong Spring AOP, **số `@Order` nhỏ hơn nghĩa là chạy ở vòng ngoài**. Aspect bật bộ lọc phạm vi đơn
vị đặt `Ordered.LOWEST_PRECEDENCE - 1` với ý định "vào trong bộ chặn transaction", nhưng
`Integer.MAX_VALUE - 1 < Integer.MAX_VALUE`, nên nó lại ra **ngoài**. Hệ quả: `enableFilter()` chạy
khi chưa có transaction, Spring cấp cho nó một `Session` tạm rồi vứt đi, còn truy vấn thật chạy
trong `Session` khác **không có bộ lọc**.

Triệu chứng: **không có triệu chứng nào**. Mọi Xí nghiệp đọc được dữ liệu của nhau.

Không sửa được bằng cách hạ aspect xuống thấp hơn — `LOWEST_PRECEDENCE` đã là số lớn nhất. Nên chốt:
**bộ chặn transaction được kéo lên `Ordered.LOWEST_PRECEDENCE - 100`** qua
`@EnableTransactionManagement(order = …)` ở `CorePlatformConfig`. Khoảng cách 100 để còn chỗ chen
aspect vào giữa nếu về sau cần.

> Mọi aspect cần một `Session`/`Connection` **đang mở** đều phải nằm trong khoảng đó, và phải đọc
> `CorePlatformConfig.TRANSACTION_ADVISOR_ORDER` cùng lúc với `@Order` của chính nó.

#### 9.8.2. Bốn cơ chế canh gác báo thành công trong khi không làm gì

| Cơ chế | Vì sao im lặng | Bằng chứng |
|---|---|---|
| Bộ luật ArchUnit theo lối `@AnalyzeClasses` + `@ArchTest` | Bộ máy `archunit` nạp đủ trên classpath nhưng Surefire báo `Tests run: 0` cho cả 4 lớp luật, build xanh | Đặt một luật chắc chắn sai → vẫn xanh |
| Cổng bao phủ JaCoCo | `<includes>` bên trong `<rule>` so với **tên phần tử**; với `element=BUNDLE` tên là tên module nên mẫu theo gói không khớp gì → luật bị bỏ qua | Nâng ngưỡng lên 0.999 → vẫn xanh |
| Luật `ScopedEntity` phải mang `@Filter` | Đúng, nhưng Phase 0 chưa có lớp nào để soi → xanh vĩnh viễn kể cả khi biểu thức sai | Chạy luật lên mã cố ý sai |
| Bộ lọc phạm vi đơn vị | Xem 9.8.1 | Entity thật đầu tiên |

**Chốt cách làm**: mỗi cơ chế canh gác phải đi kèm **một bài kiểm chứng minh nó bắt được vi phạm**.
Không có bằng chứng đó thì "xanh" chỉ nói lên rằng nó không đỏ, không nói lên rằng nó đang canh.
Cụ thể trong repo: `ImportedScopeTest` (tập lớp đem soi không rỗng) · `SilentFailureRuleSelfCheckTest`
(luật bắt được lỗi thật) · `RbacMatrixTest#matrixIsNotDegenerate` (ma trận không rỗng).

#### 9.8.3. Chọn ArchUnit lõi, không dùng bộ máy JUnit riêng của nó

Gọi thẳng `rule.check(classes)` trong `@Test` thường. Đổi lại mất tính năng cache tập lớp của
`@ArchTest` — bù bằng một hằng `JavaClasses` dùng chung. Được lại: số bài kiểm hiện đúng trong log
CI, tên luật hiện ra khi gãy, và không có bộ máy trung gian nào để hỏng âm thầm.

#### 9.8.4. Phiên bản Docker Engine API cho Testcontainers

Docker Engine 29 **đã bỏ mọi API cũ hơn 1.44**; docker-java đi kèm Testcontainers 1.21 mặc định
thương lượng bản cũ hơn thế → Testcontainers báo *"Could not find a valid Docker environment"* trong
khi `docker ps` vẫn chạy bình thường. Ghim `api.version` qua thuộc tính `docker.api.version` (mặc
định **1.44** = Docker Engine 25.0, 01/2024) trong cấu hình Surefire của module `app`.

⚠ Không có con số nào đúng cho mọi engine: engine < 25.0 chưa có 1.44, engine ≥ 29 không nhận 1.43.
Runner cũ hơn phải truyền `-Ddocker.api.version=1.43`.

---

### 9.9. Sao lưu, khôi phục và giám sát (chốt 16/8, khi làm WS-7)

Bốn quyết định đáng ghi, cả bốn đều là chọn giữa hai phương án đúng-về-mặt-kỹ-thuật.

#### 9.9.1. Kho sao lưu **KÉO** về VM-3, không đẩy đi từ VM-1

`deploy/backup/pull-from-prod.sh` chạy **trên VM-3** và kéo bản dump về; VM-1 không giữ khoá SSH nào.

Mô hình đẩy đòi VM-1 — chính máy phơi ra Internet — phải có quyền **ghi** vào kho sao lưu. Ai chiếm
được VM-1 cũng chiếm luôn khả năng **xoá mọi bản sao lưu**, và mã hoá tống tiền làm đúng việc đó
trước tiên. Kéo về thì chiếm được VM-1 vẫn không chạm được vào kho.

Hệ quả cần biết: VM-3 phải có tài khoản chỉ-đọc trên VM-1, và `env/prod.env.example` **đã bỏ**
`BACKUP_TARGET_HOST` của bản WS-3.

#### 9.9.2. Sao lưu chạy bằng `songnhue_readonly`, khôi phục là tính năng **bật riêng**

`pg_dump` chỉ cần đọc, và nó chạy mỗi đêm — cấp quyền ghi cho việc đó là mở rộng vô cớ phạm vi
thiệt hại. Cùng nguyên tắc đã áp cho `songnhue_archiver` (§9.3).

Khôi phục thì cần quyền chủ sở hữu. Thay vì mặc nhiên đưa mật khẩu owner vào tiến trình ứng dụng,
`DB_RESTORE_PASSWORD` để trống là **hợp lệ**: nút khôi phục trên UI báo `ADM-2010` và từ chối ngay,
còn khôi phục đi bằng runbook. Nơi nào chấp nhận đánh đổi thì điền vào.

⚠ Điểm dễ sai: điều kiện phải là "mật khẩu **khác rỗng**", không phải "có khai mật khẩu" — đúng cái
bẫy đã sập một lần ở `ArchiverDataSourceConfig` (§9.7).

#### 9.9.3. Chỉ số đo **sự vắng mặt**, không đếm lỗi

Ba gauge của `PlatformMetrics` — tuổi bản sao lưu, tồn đọng hàng đợi, độ tươi dữ liệu — đều trả lời
"việc lẽ ra phải xảy ra có còn xảy ra không". Những kiểu hỏng đắt nhất của hệ này **không ném
exception nào**: worker chết, poller ngừng, job sao lưu không được đặt. Đếm số lỗi không bao giờ bắt
được chúng.

Hai hệ quả cài đặt:

- **`-1` nghĩa là "chưa từng có", không phải `0`.** `0` đọc là "vừa mới xong" — ngược hẳn.
- **Làm mới theo lịch, không đọc DB trong hàm gauge.** Micrometer gọi hàm đó mỗi lượt Prometheus lấy
  số; CSDL chết thì lượt lấy số treo theo và kéo sập luôn `/actuator/prometheus` — mất đúng công cụ
  cần dùng để biết chuyện gì đang xảy ra.

**Sai lệch có chủ đích so với kế hoạch**: §6.5 ghi "một alert duy nhất cho backup", thực tế có
**hai**. "Đã dump" và "đã đưa ra khỏi máy chủ CSDL" là hai sự thật khác nhau, hỏng độc lập — mà bản
dump nằm cùng máy với CSDL nó phải cứu thì không cứu được gì.

#### 9.9.4. Một ngoại lệ cho luật cấm `float/double`

Gói `core.common.observability` được miễn. Mô hình dữ liệu của Prometheus **là float64** và API
`Gauge` của Micrometer chỉ nhận `double` — không có cách viết nào tránh được. An toàn vì thứ đi qua
đó không phải số nghiệp vụ: tuổi bản sao lưu tính bằng giây, số việc tồn, số giây im lặng.

Ngoại lệ được canh bằng `CodingRuleTest#ngoaiLeChiGomGoiQuanSat`: chạy luật **không có ngoại lệ** lên
toàn bộ mã nguồn rồi đòi mọi vi phạm phải nằm trong đúng gói đó. Bắt được cả hai hướng — nới ngoại lệ
ra gói khác, và ngoại lệ đã thừa mà không ai gỡ.

---

### 9.10. Giao diện quản trị (chốt 17/8, khi làm WS-8)

#### 9.10.1. Access token nằm trong bộ nhớ, và hệ quả bắt buộc phải nhận

Access token **không** vào `localStorage` cũng không vào `sessionStorage` — chỉ là một biến trong
module `shared/apiClient`. Refresh token thì hoàn toàn ngoài tầm với của JavaScript (cookie
`httpOnly`). Nghĩa là một lỗ XSS chỉ lấy được cái vé sống 30 phút, không lấy được cái vé sống nhiều
ngày.

Cái giá phải trả **không được lảng tránh**: F5 là mất token. Nếu để nguyên như vậy, người dùng sẽ
phải đăng nhập lại sau mỗi lần tải lại trang, và áp lực "sửa cho tiện" sẽ đẩy token xuống
localStorage — tức là vứt bỏ đúng lớp phòng thủ vừa dựng. Nên `bootstrapSession()` gọi
`POST /auth/refresh` **một lần lúc khởi động**: có cookie hợp lệ thì đi tiếp, không thì về trang đăng
nhập. Trạng thái xác thực vì thế có **ba** giá trị (`loading` / `anonymous` / `authenticated`), không
phải hai — thiếu `loading` thì route guard chạy đúng vào khoảnh khắc chưa biết là ai và chuyển hướng
thật, mất luôn đường dẫn người dùng đang mở.

Kèm một chi tiết dễ bỏ sót: vé CSRF cũng mất khi F5, nhưng cookie `XSRF-TOKEN` (cố ý **không**
httpOnly) thì còn. `currentCsrfToken()` rơi về đọc cookie khi bộ nhớ trống — không có bước đó thì
`/auth/refresh` bị `CsrfFilter` chặn ngay và việc khôi phục phiên không bao giờ chạy được.

#### 9.10.2. Làm mới token đúng một lượt, và không bao giờ cho vòng lặp

Mười request cùng nhận 401 phải dùng chung **một** lời hứa refresh (`refreshInFlight`). Không phải để
tiết kiệm: refresh **xoay vòng** token, nên hai lượt gọi song song là lượt thứ hai dùng lại token đã
bị thay — đúng định nghĩa của cơ chế phát hiện dùng lại ở backend, và hậu quả là thu hồi cả family
rồi đá người dùng ra ngoài. Tự tay kích hoạt cảnh báo bảo mật của chính mình.

Ba chốt chặn đi kèm: cờ `__retried` cho phép gửi lại **đúng một lần**; `AUTH_ENTRY_PATHS`
(`/auth/login`, `/auth/2fa/`, `/auth/refresh`) không bao giờ kéo theo vòng làm mới vì chính chúng là
luồng cấp token; và `AUTH-0008` (đã phát hiện dùng lại) **không** thử lại — backend đã thu hồi xong,
gọi thêm chỉ tạo một sự kiện bảo mật giả.

#### 9.10.3. `error-map.ts` mang *hành động*, không chỉ mang câu chữ

Bản sao 49 mã lỗi ở FE **không** dùng để hiển thị — `messageFor()` luôn ưu tiên câu do API trả về, vì
đó mới là câu đã điền tham số. Bản sao tồn tại vì hai việc khác: (1) **quyết định hành vi** — cùng là
HTTP 403 nhưng `AUTH-3001` phải đưa sang trang "không có quyền" còn `AUTH-0005` (CSRF lệch) phải làm
mới vé rồi thử lại, nhìn status code không phân biệt được; (2) **khi chưa tới được máy chủ**, lúc đó
không có envelope nào để lấy câu chữ.

Việc đồng bộ hai bên **có bài kiểm canh**: `error-map.test.ts` đọc thẳng
`backend/core/src/main/resources/error-messages.properties` và làm đỏ khi lệch. Trước đó, nghĩa vụ
đồng bộ chỉ được nhắc bằng một dòng chú thích trong `ErrorCode.java` — và nó đã trôi qua ba đợt
(31 → 36 → 43 → 49 mã) mà không ai làm. Bài kiểm cũng tự chứng minh nó bắt được lệch, theo luật ở
`conventions.md` §1.5.

#### 9.10.4. Quyền ở FE **chỉ** để ẩn/hiện, và chỗ nguy hiểm nhất được tách ra kiểm

Route guard và menu đọc `permissions` từ `GET /auth/me`. Đây là **tầng 1** của §4.2: người dùng gỡ nó
bằng công cụ dev trong ba giây, nên nó không bảo vệ gì cả — chốt chặn thật là `@RequirePermission`
(tầng 2) và scope filter theo đơn vị (tầng 3), cả hai đều có bài kiểm ở CI.

Riêng nút **khôi phục dữ liệu** được tách thành hàm thuần `isRestoreVisible(isSuperAdmin, status)` ở
tệp riêng, có bài kiểm cả bốn nhánh. Lý do: điều kiện của nó có **hai vế độc lập** — vai trò Super
Admin (kiểm tường minh chứ không qua quyền `adm:backup:restore`, vì quyền thì gán được cho vai trò
khác bằng vài cú nhấp) và môi trường có bật khôi phục (`DB_RESTORE_PASSWORD` không rỗng). Một điều
kiện hai vế nằm lẫn trong JSX là thứ dễ bị rút gọn nhầm lúc dọn dẹp, mà hậu quả thì là hiện nút ghi
đè toàn bộ CSDL cho người không được phép.

#### 9.10.5. Màn hình vai trò chỉ xem — cố ý

Ma trận 12 vai trò × 88 quyền (334 dòng) nạp bằng migration ở WS-2, dịch thẳng từ `function-spec.md`
§6, và có bài kiểm ở CI đối chiếu từng dòng. Mở cho sửa trên giao diện là để một cú nhấp phá vỡ thứ
mà cả một bộ kiểm thử đang canh, lại mất luôn dấu vết "vì sao ma trận thành ra thế này". Việc thật sự
hay làm là **gán vai trò cho người**, và việc đó nằm ở màn hình Tài khoản. Sửa được ma trận là hạng
mục của Phase 1, khi nghiệp vụ đã ổn định.

#### 9.10.6. Chưa có "quên mật khẩu"

Backend Phase 0 không có endpoint đặt lại mật khẩu, và làm nửa vời (gửi liên kết đặt lại qua email)
là mở thêm một đường vào hệ thống mà chưa ai rà — trong khi kênh email hiện chỉ là thông báo một
chiều. Đường chính thức lúc này: quản trị viên cấp lại mật khẩu tạm, người dùng bị bắt đổi ở lần đăng
nhập kế tiếp. Ghi thành nợ #35 để không trôi thành "quên làm".

---

### 9.11. Cổng thông tin công khai và tệp chung của hai app FE (chốt 17/8, khi làm WS-9)

#### 9.11.1. `design-tokens` là workspace thứ ba, không nằm trong admin-app

Hai ứng dụng FE **ngang hàng**. Để bảng màu trong `admin-app` thì cổng thông tin công khai phải phụ
thuộc vào ứng dụng quản trị nội bộ chỉ để lấy màu — quan hệ ngược chiều, và kéo cả mã nguồn
admin-app vào bối cảnh build của public-web. Một gói nhỏ mà cả hai cùng phụ thuộc thì quan hệ đúng
chiều, và mỗi image chỉ tải phần nó cần (`npm ci --workspace <app>`).

Gói này **không có bước biên dịch**: `exports` trỏ thẳng vào `.ts`, Vite transpile sẵn, Next khai
`transpilePackages`. Thêm một bước build chỉ để phát ra vài hằng số là thêm một chỗ quên chạy lại.

Tailwind 4 khai theme bằng CSS (`@theme`), nhưng vẫn nhận cấu hình TS qua chỉ thị `@config` — dùng
đường đó là có chủ ý: khai lại năm màu trạng thái bằng CSS custom property nghĩa là **hai bản sao**,
mà năm màu đó mang nghĩa nghiệp vụ chứ không phải thẩm mỹ.

#### 9.11.2. Cổng thông tin dựng tĩnh, không render động mỗi lượt

Trang công khai chịu lượt xem không kiểm soát được (một bài viết được chia sẻ rộng là đủ), trong khi
backend cùng lúc phục vụ hệ điều hành nội bộ **trên cùng một máy chủ VM-1**. HTML tĩnh + ISR giữ cho
lượt đọc của công chúng không chạm tới CSDL.

Kèm đường dựng lại **tức thì**: `POST /api/revalidate` cho những thứ không chờ được chu kỳ 5 phút —
thông báo xả lũ, cảnh báo mực nước, đính chính bài đã đăng sai. `REVALIDATE_SECRET` **không** có
tiền tố `NEXT_PUBLIC_`: biến mang tiền tố đó bị nhúng vào bundle gửi xuống trình duyệt, và khi đó
endpoint này thành nút bất kỳ ai cũng bấm được để ép máy chủ dựng lại trang liên tục.

#### 9.11.3. Health của public-web cố ý **không** hỏi sang backend

Gọi sang API Core để kiểm tra thì backend hỏng sẽ làm container này bị đánh dấu unhealthy và khởi
động lại — trong khi phần lớn cổng thông tin là HTML tĩnh, vẫn phục vụ người đọc bình thường. Một
thành phần hỏng không nên làm hỏng lây thành phần còn chạy được. Tình trạng backend đã có chỗ riêng:
`GET /api/v1/system/health` (M5.12).

#### 9.11.4. Trang 500 công khai **không** hiện thông điệp lỗi

Khác trang 500 của admin-app (hiện `traceId` cho cán bộ đọc lại cho quản trị viên): đây là trang ai
cũng vào được. Next đã thay thông điệp lỗi thật bằng chuỗi rỗng kèm `digest` trước khi gửi xuống
trình duyệt, và ta giữ nguyên cách đó — chỉ hiện `digest`, tra trong log máy chủ ra đúng lỗi.

#### 9.11.5. ⚠ Migrator **phải thoát được** — và nó đã không thoát suốt từ WS-6

Cả luồng deploy dựa vào một điều: `migrator` chạy Flyway rồi kết thúc mã 0, và
`depends_on: service_completed_successfully` mới cho `app` khởi động (§9.2, T11.4). Đó là cơ chế duy
nhất ngăn app lên trên schema hỏng.

Thực tế đo được ngày 17/8: migration chạy xong, log in "✓ Migration hoàn tất", rồi tiến trình **không
bao giờ thoát**. Container đứng `Up` vô hạn, `app` kẹt ở `Created`, **không một dòng lỗi nào**. Ba
nguyên nhân chồng lên nhau, và tắt hai trong ba vẫn treo y như cũ:

1. **`@EnableScheduling`** dựng `ThreadPoolTaskScheduler` với luồng **không phải daemon** — luồng đó
   giữ JVM sống mãi. `spring.main.web-application-type: none` không cứu được: nó chỉ tắt cổng HTTP.
   Khởi tạo lười cũng không, vì Spring Boot cố ý loại bean mang `@Scheduled` ra khỏi cơ chế đó. →
   tách thành `SchedulingConfig` mang `@Profile("!migrate")`.
2. **Worker hàng đợi** mở luồng riêng chạy vòng lặp vô hạn. → `app.worker-enabled: false` trong
   profile `migrate`.
3. **Không có khởi tạo lười**, context dựng cả chuỗi `AuthController → AuthService → TokenService →
   JwtKeyStore`, và **đòi khoá ký JWT** dù việc duy nhất của tiến trình là chạy DDL. Ở production,
   chiều theo đòi hỏi đó nghĩa là đưa khoá ký cho một tiến trình không có lý do gì để cầm. →
   `spring.main.lazy-initialization: true`.

Canh bằng `MigrateProfileTest`: kiểm cả ba công tắc, và **cấm `@EnableScheduling` xuất hiện ở lớp nào
khác** — đó mới là đường quay lại của lỗi này.

#### 9.11.6. ⚠ Image Docker mặc định **luôn build lại**

`make dev-*` trước đây chỉ build lại khi gõ `BUILD=1`. Hệ quả đo được: image `songnhue-app:local` nằm
nguyên từ WS-3 — bản dựng **trước khi có controller nào**. Container lên, healthcheck
`/actuator/health` xanh, mà **mọi endpoint `/api/v1/**` trả 404**. Nhìn từ ngoài là "hệ thống chạy
tốt"; không ai phát hiện suốt WS-4 → WS-8.

Đo thật: build lại khi mã nguồn không đổi tốn **~10 giây**. Đổi mặc định thành luôn build, `NOBUILD=1`
để bỏ qua.

Cùng loại với những gì đã gặp ở §9.8: cơ chế canh gác báo xanh trong khi thứ nó phải canh chưa hề
chạy. Ở đây thủ phạm là **healthcheck trỏ vào endpoint không đại diện cho thứ đang kiểm** —
`/actuator/health` sống được kể cả khi không còn controller nào.

---

### 9.12. Rà soát nợ và kiểm chứng lại các WS đã đóng (17/8)

Rà soát này **không** thêm chức năng nào. Việc của nó là chạy lại bằng tay những thứ đã đánh dấu
xong, và nó tìm ra **bốn lỗi thật**, trong đó ba nằm ở đúng cơ chế mà cả hệ thống dựa vào.

#### 9.12.1. ⚠⚠ Sao lưu **không sinh ra tệp nào** — hai nguyên nhân chồng lên nhau

`make backup` trên hệ đang chạy dừng ở:

```
pg_dump: error: query failed: ERROR: permission denied for sequence system_backups_id_seq
```

`V202608131006` §3 khai quyền mặc định cho bảng tạo sau, nhưng chỉ có dòng `TABLES` cho
`songnhue_readonly`, **thiếu dòng `SEQUENCES`**. `GRANT … ON ALL SEQUENCES` ở cùng migration chỉ áp
cho sequence *đang tồn tại lúc nó chạy*. Bảng đầu tiên tạo sau nó là `system_backups` — **chính bảng
sổ đăng ký sao lưu** — nên cơ chế sao lưu tự chặn mình bằng cái bảng nó vừa tạo ra.

Sửa ở tầng quyền mặc định (`V202608171011`), không phải bằng một `GRANT` lẻ: mọi bảng của Phase 1+
(công trình, thuỷ văn, hồ sơ nhân sự) đều sẽ làm hỏng lại đúng như vậy, mỗi lần đều im lặng cho tới
lần sao lưu kế tiếp.

**Vì sao 255 bài kiểm của WS-7 không bắt được**: `BackupServiceTest` **mock `PostgresToolRunner`**.
Nó chứng minh phần điều phối — checksum đọc lại từ đĩa, ghi `FAILED` khi hỏng, mật khẩu đi qua
`PGPASSWORD` — nhưng **chưa một lần gọi `pg_dump`**. Nay có `BackupRoleTest` chạy `pg_dump` thật bằng
đúng vai trò và đúng tham số của production, **bên trong container** để phiên bản luôn khớp máy chủ.

Đây là lỗi đắt nhất trong nhóm: hệ này **không có PITR, không có replica** (§6.5), nên bản dump đêm
là lưới an toàn *duy nhất*. Suốt WS-7 → WS-9 nó không tồn tại.

#### 9.12.2. ⚠ Phép kiểm bảo mật báo ĐẠT mà chưa từng quét

`verify-no-keys.sh` (T7.2 · DoD 13d) tìm khoá PEM bằng mẫu `-----BEGIN … PRIVATE KEY-----`. Mẫu bắt
đầu bằng `-` nên `grep -qiE "$pattern"` đọc nó thành **tham số dòng lệnh**:

```
grep: unrecognized option `-----BEGIN [A-Z ]*PRIVATE KEY-----'
```

grep chết, lời gọi nằm trong `if` nên lỗi bị nuốt, và script in `✓ Bản sao lưu không chứa khoá`. Một
phép kiểm bảo mật báo đạt **trong khi nó chưa soi một dòng nào**.

Sửa bằng `-e`, và thêm **phép tự kiểm chạy mỗi lượt**: cho khoá giả đi qua đúng hàm đó và bắt nó
phải kêu, cho dữ liệu sạch đi qua và bắt nó phải im. Đã kiểm chứng ngược bằng cách cắm một khoá PEM
giả vào CSDL: sao lưu bị chặn với `⛔`. Trước bản vá, cùng phép thử đó cho ra `✓`.

Kèm một chỗ nữa cùng loại: bản dump **không đọc được** cũng cho ra `✓` (không tìm thấy gì vì không
đọc được gì) → nay chặn bằng `pg_restore --list` trước khi soi.

#### 9.12.3. ⚠ `make migrate` chạy **image cũ** — migration mới không hề chạy

WS-9 đã đổi `make dev-*` thành luôn build lại, nhưng **bỏ sót `make migrate`**. Triệu chứng đo được:
thêm một migration, gõ `make migrate`, log in `✓ Migration hoàn tất — tổng số migration đã áp dụng:
10` — thành công theo mọi dấu hiệu nhìn thấy được, mà bản mới thì không chạy. Nguy hiểm hơn `dev-*`
một bậc vì migration nằm trong jar: image cũ nghĩa là chạy Flyway của bản mã cũ, đúng trên CSDL thật.

#### 9.12.4. ⚠ CSDL test có schema mà production không có

`SongnhuePostgres` chép `deploy/postgres/init` vào `/docker-entrypoint-initdb.d` để "dùng chung
script khởi tạo với production, không chép lại". Nhưng `withCopyFileToContainer` **chép vào thư
mục**, không đè cả thư mục — nên `10_postgis.sh` có sẵn trong image vẫn chạy và tạo thêm
`postgis_topology` + `postgis_tiger_geocoder` (kéo theo schema `topology`, `tiger`). Ở production,
bind-mount của compose **che cả thư mục**, script đó không bao giờ chạy.

Sai lệch này nguy hiểm theo cả hai chiều: bài kiểm đỏ vì thứ không tồn tại thật (đúng cách nó lộ ra —
`pg_dump` báo `permission denied for schema tiger`), và mã dùng hàm của `topology` sẽ xanh ở đây rồi
hỏng khi chạy thật. Vô hiệu hoá script của image trong test; canh bằng bài kiểm khẳng định danh sách
extension **đúng bằng** danh sách của production, không phải "có chứa".

#### 9.12.5. `/actuator/health` **cố ý** DOWN khi chưa có bản sao lưu — nên smoke test phải hỏi `readiness`

`BackupHealthIndicator` báo DOWN khi chưa từng sao lưu thành công, và đó là quyết định đúng đã ghi ở
§9.9. Nhưng hệ quả chưa được tính hết: **smoke test của cả hai workflow deploy** đọc
`/actuator/health` và tìm `"status":"UP"`. Môi trường mới dựng thì chưa có bản sao lưu nào — tức là
**đúng lần deploy đầu tiên**, smoke test đỏ suốt 5 phút vì một chuyện hoàn toàn bình thường, rồi
deploy bị coi là hỏng. Ở production còn tệ hơn: `show-details: never` nên phản hồi chỉ có
`{"status":"DOWN"}`, không nói vì sao.

Chốt: **câu hỏi "ứng dụng phục vụ được chưa" là `readiness`**; bản tổng trả lời một câu khác — "hệ
thống có đang thiếu lưới an toàn nào không". Smoke test, healthcheck container và tài liệu dựng máy
đều hỏi `readiness`; bản tổng dành cho giám sát và cho màn hình M5.12.

#### 9.12.6. Ba con số trong tài liệu không khớp thực tế

Đối chiếu bằng truy vấn trên CSDL thật và bằng API GitHub: `settings` **58** tham số chứ không phải
55 · `docs/runbook/` có **8** runbook chứ không phải 7 · nhánh `common` đi trước `dev` **22 commit /
431 tệp** chứ không phải 18/313, và `dev` **không trống** — nó có 12 tệp tài liệu, nhưng không có mã
nguồn và không có `.github/`, nên kết luận "repo chưa chạy lượt CI nào" vẫn đúng.

---

### 9.13. Vòng đời mật khẩu và luồng quên mật khẩu (chốt 18/8, đóng nợ #35)

Đặc tả nghiệp vụ đầy đủ ở `function-spec.md` **M5.15-a**. Mục này ghi *vì sao* chọn như vậy.

#### 9.13.1. Hai đồng hồ 3 tháng, và chúng đo hai thứ khác nhau

Dễ đọc nhầm thành mâu thuẫn: mật khẩu **phải đổi trong** 90 ngày, mà luồng tự đặt lại **phải cách
nhau** 90 ngày. Chúng không đè lên nhau vì áp cho hai hành động khác nhau:

- **Đổi chủ động** (biết mật khẩu cũ) — *không* có giãn cách. Đây là điều kiện bắt buộc về an toàn:
  người nghi mật khẩu bị lộ phải đổi được ngay, không đợi hết quý.
- **Tự đặt lại** (quên, xác thực bằng email) — có giãn cách 90 ngày. Quên tiếp trong kỳ thì đi đường
  quản trị viên.

Cách đọc này bị ép bởi chính quy tắc "sau khi quản trị viên cấp mật khẩu tạm, người dùng đổi được
ngay": nếu giãn cách áp cho mọi lần đổi thì đường cấp mật khẩu tạm tự nó bế tắc.

Hai đồng hồ **tự đồng bộ** sau mỗi lần tự đặt lại: đặt lại ngày X thì mật khẩu hết hạn ngày X+90 và
giãn cách cũng hết ngày X+90. Không có khoảng nào người dùng vừa bị buộc đổi vừa bị chặn đổi.

#### 9.13.2. ⛔ Email tự phục vụ gửi **liên kết**, không gửi mật khẩu

Gửi thẳng mật khẩu mới theo một yêu cầu **chưa xác thực** biến chức năng quên mật khẩu thành công cụ
tấn công: bất kỳ ai biết tên đăng nhập đều làm mật khẩu của người khác ngừng hoạt động, và nạn nhân
mù cho tới khi mở hộp thư. Với đơn vị này, tên đăng nhập gần như đoán được từ tên cán bộ.

Liên kết một lần (TTL 30') không có tính chất đó: **mật khẩu cũ vẫn dùng được** cho tới khi người
dùng thật sự đặt mật khẩu mới. Yêu cầu giả chỉ tạo ra một email thừa.

Mật khẩu tạm **vẫn giữ**, nhưng chỉ ở đường quản trị viên cấp — nơi đã có một con người chịu trách
nhiệm và có dấu vết trong nhật ký kiểm toán. Lưu **băm** của mã đặt lại chứ không lưu mã: bảng này
mà lộ thì kẻ đọc được không đặt lại được mật khẩu của ai.

#### 9.13.3. Mốc thời gian chặn, con số đếm để nhìn

`self_reset_count` **không** dùng để chặn — một con số đếm không diễn tả được "cách nhau 90 ngày".
Thứ chặn là `last_self_reset_at`. Con số đếm giữ lại vì nó trả lời câu hỏi khác: ai hay quên (cần
hướng dẫn lại), và tài khoản nào đang bị người ngoài nhắm (nhiều lượt yêu cầu mà không lượt nào hoàn
tất).

#### 9.13.4. ⚠ Khoá theo hạn áp cho **cả Super Admin** — nên phải có đường thoát ngoài mã

Miễn trừ Super Admin là làm rỗng chính sách ở đúng tài khoản nguy hiểm nhất. Nhưng giữ nguyên quy tắc
thì gặp bài toán tự nhốt: Super Admin hết hạn → `DISABLED` → người mở khoá lại chính là Super Admin.

Chốt: giữ quy tắc cho mọi tài khoản, đường thoát là **lệnh chạy trên máy chủ** (mở rộng
`AdminBootstrapRunner` của WS-5), không phải một nhánh ngoại lệ trong mã nghiệp vụ. Lý do: ai đã vào
được máy chủ thì vốn có quyền cao hơn bất kỳ mật khẩu nào — đặt đường thoát ở đó không hạ thấp gì,
trong khi một ngoại lệ trong mã thì tồn tại mãi và ai đọc cũng thấy.

#### 9.13.5. Việc theo lịch đi qua hàng đợi job

Quét tài khoản sắp hết hạn (gửi nhắc) và quá hạn (chuyển `DISABLED`) chạy hằng ngày, nhưng **đặt việc
vào hàng đợi** chứ không xử lý ngay trong bộ lập lịch — đúng mô hình đã chốt ở §9.6. Khoá chống trùng
theo ngày, nên thêm node thứ hai không sinh ra hai lượt gửi nhắc.

#### 9.13.6. Điều Công ty cần được báo trước

Quy tắc "quá 90 ngày không đổi → khoá tài khoản" là chính sách **người dùng cảm nhận trực tiếp**, và
nó dồn việc lên quản trị viên: cán bộ đi công tác dài, nghỉ phép dài, hoặc dùng hệ thống thưa (nhiều
người ở MOD-01/MOD-04 chỉ vào vài lần mỗi quý) sẽ bị khoá đúng lúc cần dùng. Ba mốc nhắc trước hạn là
để giảm chuyện đó, nhưng không xoá được nó.

Đây **không** phải mục cần Công ty quyết định — đã chốt nội bộ — mà là mục cần **thông báo** khi
nghiệm thu, kèm ước lượng khối lượng cấp lại mật khẩu tạm cho quản trị viên.

---

### 9.14. Ranh giới module vs sáu dịch vụ dùng chung — SPI, không phải nới luật (chốt 19/8, khi rà soát tài liệu trước Phase 1)

**Vấn đề phát hiện khi viết `docs/coding-guide.md`**, không phải khi chạy test: `ModuleBoundaryTest`
chỉ cho một module import `com.songnhue.<module>.spi.*` và `com.songnhue.core.common.*`. Nhưng sáu
dịch vụ mà mọi module nghiệp vụ đều phải dùng — `WorkflowEngine`, `NotificationService`,
`AttachmentService`, `JobService`, `SettingService`, `OrgUnitService` — nằm ở `core.application.*`.

`core/spi/` hiện chỉ có `package-info.java`. Nghĩa là **dòng mã Phase 1 đầu tiên gọi `WorkflowEngine`
sẽ làm CI đỏ**.

Phase 0 không lộ ra chuyện này vì bốn module nghiệp vụ còn là khung rỗng và chưa import gì từ `core`
— luật đúng, mã đúng, nhưng chưa có ai đi qua chỗ giao nhau.

#### Chốt: mở SPI, giữ nguyên luật

| Phương án | Đánh giá |
|---|---|
| **Thêm interface vào `core/spi/`**, service ở `core.application` cài interface đó | ✅ **Chọn.** Đúng ý định thiết kế đã ghi trong `spi/package-info.java`; giữ được khả năng tách module thành service riêng về sau; chi phí là vài interface mỏng |
| Nới `ModuleBoundaryTest` cho phép `core.application.*` | ⛔ Xoá ranh giới đã dựng cả Phase 0. `core.common.*` là ngoại lệ **hạ tầng** (envelope, mã lỗi, utils) — mở tiếp cho *dịch vụ nghiệp vụ* thì luật không còn nghĩa gì |
| Chuyển sáu dịch vụ sang `core.common` | ⛔ Sai phân tầng: chúng có logic nghiệp vụ và ranh giới giao dịch, không phải hạ tầng dùng chung |

#### Nguyên tắc rút ra

**Một ranh giới chưa ai đi qua thì chưa biết nó đúng hay sai.** ArchUnit xanh suốt Phase 0 không
chứng minh luật khả thi — nó chỉ chứng minh chưa có ai thử. Đây là biến thể của bài học §9.8.2
("xanh mà không chạy"), lần này ở tầng thiết kế chứ không phải tầng công cụ: phép kiểm chạy đúng,
nhưng chạy qua một tập rỗng.

Hệ quả cho các ranh giới còn lại: mỗi khi Phase 1 mở một đường đi mới giữa hai module, phải hỏi
"luật hiện tại có cho phép đường này không, và nếu không thì đúng ra nên mở ở đâu" — trước khi
viết mã, không phải sau khi CI đỏ.
