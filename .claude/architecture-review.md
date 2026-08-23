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

## 5. ~~VIỆC CẦN LÀM THEO REVIEW~~ — **ĐÃ LÀM XONG, XOÁ 21/8/2026**

> Mục này là checklist 4 việc phải làm sau đợt review đầu (2026-07). Cả 4 đã đóng, nên giữ lại chỉ khiến người đọc tưởng còn việc:
> **(1)** bảng tech stack + quy ước timestamp — đã vào `function-spec.md` §0.2 và Phụ lục · **(2)** checklist quyết định queue/chart/map + ShedLock + ArchUnit — đã vào `implement.md` · **(3)** Testcontainers PostgreSQL+PostGIS và bộ kiểm ma trận RBAC — dựng ở **WS-10** (`RbacMatrixTest` đối chiếu 334 dòng phân quyền trên CSDL thật) · **(4)** contract API telemetry — đóng bằng **G3 + G8b** ngày 12/8; phần tích hợp hệ thống văn bản là mục **G5**, đang theo dõi ở `business-open-questions.md` Phần II chứ không phải ở đây.
>
> ⛔ Không xoá lặng lẽ mà để lại bia mộ: một mục biến mất không dấu vết thì lần review sau sẽ có người hỏi "checklist đó đi đâu rồi".

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

---

## §10. QUYẾT ĐỊNH KIẾN TRÚC PHASE 1 (2026-08-19)

> Phase 1 = Nhóm B (CMS) + C1 (master data công trình). Kế hoạch chi tiết: `phase1-tracking.md`
> (11 hạng mục WS-12→WS-22, 99 task). Mục này chỉ ghi **quyết định và lý do**, không lặp lại task.

### §10.1. Gộp phần hiển thị công khai vào Phase 1

`implement.md` §3 xếp "B hoàn thiện … public" vào Phase 2. **Đổi**: phần hiển thị bài viết trên cổng
công khai (danh sách · chi tiết · danh mục · SEO · sitemap) làm ngay ở Phase 1.

Lý do không phải là "tiện tay làm luôn":

1. **WS-9 đã dựng `POST /api/revalidate` "cho luồng duyệt bài Phase 1"** và tới nay chưa ai đi qua.
   Một cơ chế viết sẵn cho một luồng chưa tồn tại là đúng định nghĩa "xanh mà không chạy" — thứ đã
   sập bốn lần ở Phase 0 (§9.8.2, §9.11.5, §9.12.1, §9.12.2). Để nó nằm im thêm một Phase nữa là
   tự nguyện nhận lại cùng một rủi ro.
2. **Luồng duyệt bài chỉ chứng minh được khi đi hết.** Duyệt xong mà không ai thấy bài ở đâu thì
   không biết ISR có làm mới không, ảnh có phục vụ được không, bài Nháp có rò ra ngoài không.
3. Đây cũng là **thứ đầu tiên Công ty nhìn thấy được**. Ba tuần Phase 0 không có gì để trình bày;
   Phase 1 nên kết thúc bằng một cổng chạy thật.

Cái giá: **+8 pd** (WS-16). Đổi lại, Phase 2 bớt đúng phần đó.

⚠ **Hệ quả kỹ thuật phải xử lý ở WS-16**: ảnh trong bài viết **không dùng presigned URL**. Trang
công khai được ISR cache lại, mà presigned URL có hạn — trang cache sống lâu hơn URL thì ảnh hỏng
hàng loạt sau vài giờ, và triệu chứng xuất hiện *sau khi* mọi người đã nghiệm thu xong.

### §10.2. Sửa bài đã xuất bản: copy-on-write, không hạ bài xuống

Spec có `article_versions` + diff + rollback nhưng **không nói** sửa bài đang xuất bản thì bài có
phải rời khỏi cổng để chờ duyệt lại không. Hai hướng:

| Phương án | Đánh giá |
|---|---|
| **Copy-on-write** — bản đang xuất bản giữ nguyên trên cổng, bản sửa vào `CHỜ DUYỆT` như một phiên bản mới | ✅ **Chọn.** Sửa một lỗi chính tả không làm bài biến mất khỏi cổng trong lúc chờ duyệt |
| Sửa là hạ trạng thái về `CHỜ DUYỆT` | ⛔ Mỗi lần sửa là một lần bài rời cổng. Với tin đang được đọc thì đây là lỗi vận hành, không phải lựa chọn thiết kế |

Người có `cms:article:publish` sửa thì đăng thẳng — không bắt họ tự duyệt bản của chính mình qua hai
bước hình thức.

### §10.3. Trạng thái công trình: vật chất hoá, không tính lúc đọc

CN-02.1 chốt trạng thái là **giá trị dẫn xuất** theo 5 mức ưu tiên, tính ở BE. Spec không nói tính
lúc nào.

**Chốt: lưu sẵn một cột + tính lại theo sự kiện + job đối soát định kỳ.** Tính lúc đọc thì mỗi lần
mở bản đồ hay dashboard là vài trăm truy vấn con lồng nhau — mà bản đồ và wall mode 4K (Phase 3)
chính là nơi trạng thái được đọc nhiều nhất, đọc liên tục cả ngày.

Job đối soát là phần bắt buộc, không phải phần thêm: cột vật chất hoá luôn có nguy cơ lệch khi một
sự kiện không kích hoạt được đường tính lại. Đối soát định kỳ biến sai lệch âm thầm thành sai lệch
**đo được**.

⛔ Cột này **không có API sửa**. Client gửi `status` lên → `OPS-3001`.

### §10.4. Không đặt khoá ngoại xuyên module ở tầng CSDL

`maintenance_logs` cần trỏ tới `alert_events` của module `hydro` (CN-02.2 — "Cảnh báo liên quan").
**Chốt: lưu `alert_event_public_id UUID`, không `REFERENCES`.**

Ranh giới module (§9.14) chặn được lời gọi ở tầng Java, nhưng một khoá ngoại xuyên module trói hai
module lại ở tầng CSDL — chỗ mà ArchUnit không nhìn tới. Khi tách `hydro` thành service riêng (mốc
§6.4), khoá ngoại đó là thứ phải gỡ trước, và nó nằm trong một migration đã merge từ lâu.

Cùng nguyên tắc: **mỗi module chỉ đặt khoá ngoại tới bảng của chính nó và của `core`.**

### §10.5. `construction_clusters` là bảng riêng — G15 đã đóng (19/8/2026)

Tài liệu từng tự mâu thuẫn: CN-02.1 xếp "Cụm" vào *cấp quản lý* (một tầng trong bộ máy), còn
`implement.md` nêu bảng `construction_clusters` (một cách nhóm công trình).

**Trả lời của Công ty: cụm chỉ là cách nhóm các công trình gần nhau** — không có tổ trưởng, không có
nhân sự thuộc cụm.

**Chốt**: bảng `construction_clusters` riêng + khoá ngoại **nullable** `constructions.cluster_id`.
⛔ **Không** thêm loại nút nào vào `org_units`. Đơn vị phụ trách vẫn là `constructions.org_unit_id`,
độc lập với cụm; phân quyền tầng 3 tiếp tục chạy trên `org_units` và **cụm không mang ý nghĩa phân
quyền** — nhóm hiển thị và lọc, thế thôi.

#### Ghi lại cách quyết định, vì nó sẽ lặp lại

Trước khi có câu trả lời, kế hoạch chọn **phương án tối giản**: chỉ gắn `org_unit_id`, chưa dựng bảng
cụm. Lý do không phải là "đoán đúng hơn" mà là **cái sai rẻ hơn**:

| Nếu chọn trước… | …và sai thì phải làm gì |
|---|---|
| Không dựng gì | Thêm một bảng + một khoá ngoại nullable — migration nhỏ |
| Thêm loại nút vào `org_units` | **Gỡ những nút không phải đơn vị ra khỏi cây đang gánh cả phân quyền tầng 3 lẫn sơ đồ nhân sự MOD-04** |

Câu trả lời hoá ra là nhánh rẻ, nhưng đó là may — điều đáng giữ là **quy tắc chọn**: khi chưa biết,
chọn hướng mà cái sai rẻ hơn, không phải hướng có vẻ đúng hơn.

### §10.6. Thư viện media không có bảng tệp riêng

CN-01.3 mô tả thư viện media như một chức năng độc lập. **Chốt: tệp media là `attachments` của Core**
với `owner_type='MEDIA_FOLDER'`, `owner_id` trỏ bảng `media_folders` của module `content`.

Dựng bảng tệp thứ hai nghĩa là có hai đường tải lên, hai chỗ kiểm magic bytes, hai chỗ quét virus,
hai chỗ tính hạn mức — và chúng sẽ lệch nhau. P3 (đính kèm) sinh ra đúng để tránh chuyện đó.

### §10.7. Workflow phải nhận nhiều trạng thái khởi đầu

`workflow_definitions` hiện có đúng một cột `initial_state`. Nhưng CN-02.2 nói bản ghi sửa chữa
**nhập sau khi đã hoàn thành** thì bắt đầu thẳng ở "Đã xử lý", còn bản ghi sự cố đang xảy ra thì bắt
đầu ở "Mới".

**Chốt: mở rộng engine để một entity type có nhiều trạng thái khởi đầu hợp lệ.**

⛔ **Cấm lách bằng cách tạo ở `MOI` rồi chạy transition cho tới `DA_XU_LY`.** Cách đó làm nhật ký ghi
lại một chuỗi sự việc **chưa từng xảy ra** — và nhật ký kiểm toán của hệ này có hash chain, tức là
chúng ta đang ký tên vào một lịch sử bịa. Không đáng để tiết kiệm một cột.

### §10.8. Tiền lưu VND, một đơn vị duy nhất

CN-02.2 ghi chi phí đơn vị **VND**; CN-02.1 ghi tổng vốn đơn vị **triệu VND**. Hai đơn vị trong cùng
một CSDL là lỗi cộng dồn chờ sẵn — và nó sẽ lộ ra ở báo cáo tổng hợp chi phí (BC-09, Phase 3), tức
là xa chỗ gây ra nó.

**Chốt: mọi cột tiền lưu VND, `NUMERIC(18,2)`.** Biểu mẫu nào hiển thị theo triệu thì quy đổi ở tầng
hiển thị và ghi rõ đơn vị trên nhãn.

---

### §10.9. Ảnh phái sinh: hoãn sang Phase 2, Phase 1 dùng ảnh gốc (19/8/2026)

CN-01.3 yêu cầu *"auto nén ảnh sang WebP (giữ bản gốc fallback); auto thumbnail 150/400/800px"*.
**Chốt: Phase 1 không dựng, dùng thẳng ảnh gốc.** Đây là mục nghiệm thu bị hoãn — nợ #62, có điều
kiện kích hoạt ghi ở `phase1-tracking.md`.

**Vì sao không đẩy sang frontend — phương án nghe hợp lý nhất và là phương án đã cân nhắc đầu tiên.**
Ảnh bài viết và ảnh hiện trạng công trình là nội dung người dùng tải lên **sau khi deploy**, nên
không thể nằm trong `public/` (thư mục đó bị nướng vào image lúc build). Ảnh thật nằm ở MinIO, phát
ra bằng presigned URL **sống 10 phút**. Bản nghiêm túc của ý này là `next/image`, và nó vướng ba chỗ,
cả ba đều thuộc về chính hệ này chứ không phải ý kiến chung chung:

1. **`next/image` đòi `sharp`** (Next 14 đã bỏ squoosh) — thư viện native libvips. Tức là không tránh
   được việc thêm bộ mã hoá ảnh, chỉ đổi chỗ nó đứng: từ JVM sang Node, và rơi lên đúng node đang
   phục vụ cổng công khai.
2. **Presigned URL phá bộ đệm của chính bộ tối ưu.** Next lưu bản đã tối ưu theo khoá là URL nguồn;
   URL của ta mang chữ ký + hạn dùng nên mỗi lượt gọi là một khoá khác → không bao giờ trúng đệm.
   Nặng hơn: trang ISR sống vài giờ trong khi URL chết sau 10 phút → **ảnh vỡ trên trang đã dựng**.
3. **Một nửa số ảnh không đi qua Next.** `admin-app` là Vite dựng ra tệp tĩnh cho nginx, không có bộ
   tối ưu ảnh nào. Ảnh hiện trạng công trình nằm đúng ở đó, lại thuộc phạm vi đơn vị — đẩy chúng qua
   `/_next/image` là biến bộ tối ưu thành proxy **đi vòng qua phân quyền tầng 3**.

**Vì sao vẫn hoãn dù backend làm được.** Phần tốn kém duy nhất là **WebP** — JDK không có bộ mã hoá
WebP, thêm nó là thêm phụ thuộc mới kèm bề mặt CVE. Phần thumbnail thì rẻ (`ImageSanitizer` đã
`ImageIO.read` → `ImageIO.write` trên mọi ảnh tải lên từ WS-6 để bóc EXIF, đường giải mã/mã hoá đang
chạy sẵn). Nhưng khối lượng ảnh thật của Phase 1 chưa biết, mà **tối ưu trước khi biết tải trọng là
đoán mò** — đúng nguyên tắc §6.1: mỗi thành phần phải tự chứng minh nó đáng nuôi.

**⛔ Cố ý KHÔNG seed tham số `settings` cho việc này.** Một công tắc "bật ảnh phái sinh" mà chưa có mã
nào đọc là **lặp lại đúng lỗi vừa sửa hôm nay** ở WS-12/T12.6: ba tham số `limits.upload.max-mb.*`
nằm trong giao diện suốt từ WS-6 mà không dòng mã nào đọc, quản trị viên chỉnh xong không có gì đổi.
Tham số chỉ xuất hiện **cùng lúc** với mã đọc nó.

**Rủi ro nhận về, nói thẳng.** Cán bộ mở một công trình có 30 ảnh chụp điện thoại tải về ~120MB, mà
người dùng đó thường ngồi ngoài trạm bơm bằng 4G. Giảm nhẹ trong Phase 1 bằng thứ không tốn gì:
`loading="lazy"` + khung CSS cố định ở mọi lưới ảnh (T14.4, WS-21) — chỉ tải phần đang nhìn. Đây là
giảm nhẹ, **không phải lời giải**; lời giải là nợ #62.

---

### §10.10. Người nhận thông báo của quy trình duyệt khai bằng dữ liệu (19/8/2026)

**Phát hiện khi bài viết trở thành entity đầu tiên đi qua workflow engine.** `WorkflowEngine`
(WS-6) truyền `entity.orgUnitId()` cho `RecipientResolver`, mà resolver đó **luôn cộng thêm nhóm
"Ban điều hành"** — đúng luật chốt G11, vì nó sinh ra cho **cảnh báo vận hành công trình**.

Áp lên bài viết thì sai cả hai đầu: `articles` không thuộc phạm vi đơn vị (điểm nghiệp vụ 9) nên
`orgUnitId()` trả `null`; và người nhận còn lại hoá ra là ban lãnh đạo Công ty, trong khi CN-01.1
ghi rõ phải báo cho **Quản trị nội dung**. Nghĩa là cơ chế `notify_event` của WS-6 **chưa từng dùng
được cho một quy trình duyệt nào** — nó xanh suốt Phase 0 vì chưa có quy trình duyệt nào tồn tại.

**Chốt: thêm hai cột vào `workflow_transitions`**, giữ nguyên triết lý "quy trình khai bằng dữ liệu":

| Cột | Nghĩa |
|---|---|
| `required_permission` | *(đã có)* ai **được bấm** |
| `notify_permission` | ai **cần biết** — mọi tài khoản đang hoạt động có quyền này |
| `notify_owner` | báo cho **chủ bản ghi** (`WorkflowAware.ownerUserId()`) |

Hai khái niệm đầu rời hẳn nhau ở bước gửi duyệt: người bấm là biên tập viên, người cần biết là quản
trị nội dung.

**⚠ Khi có `notify_permission`, nhóm Ban điều hành bị THAY THẾ chứ không cộng dồn.** Đây là điểm dễ
làm sai nhất. Hai bài toán ngược nhau:

- **Cảnh báo vận hành** — hệ thống *đoán* ai nên biết; không ai "sở hữu" một mực nước vượt ngưỡng.
- **Quy trình duyệt** — nơi gọi *biết chính xác*: người có quyền duyệt, và người đã gửi lên.

Cộng thêm Ban điều hành vào nhánh thứ hai nghĩa là mỗi lần biên tập viên bấm "Gửi duyệt" thì cả ban
lãnh đạo nhận một email. Vài tuần sau là không ai đọc thông báo nữa — **và lúc đó cảnh báo sự cố
thật chết theo**. Đó mới là thiệt hại, không phải chuyện hộp thư đầy.

⛔ **`ownerUserId()` không được lấy từ `createdBy`.** Biểu mẫu bài viết cho đổi tác giả (CN-01.1), và
khi đó thư "bài của bạn bị trả về" phải tới tác giả hiện tại, không tới người bấm Tạo mới từ tháng
trước.

Ràng buộc CHECK ở CSDL chặn khai `notify_permission`/`notify_owner` mà quên `notify_event` — engine
kiểm `notify_event` trước tiên, nên thiếu nó là thông báo **không bao giờ được sinh ra**, im lặng.

---

### §10.11. Hàm bỏ dấu tiếng Việt đặt ở Core, và phải khai kiểu cho Hibernate (19/8/2026)

Tìm kiếm không dấu ("de dieu" ra "đê điều") cần một hàm SQL trong biểu thức chỉ mục. Hai cái bẫy đi
liền nhau, cả hai đều chỉ lộ ra lúc chạy thật:

1. **`unaccent(text)` một tham số KHÔNG phải IMMUTABLE** — nó tra từ điển lúc chạy, nên Postgres từ
   chối đưa vào chỉ mục. Phải bọc bản hai tham số `unaccent(regdictionary, text)`.
2. **HQL không đoán được kiểu trả về của hàm tự định nghĩa** — Hibernate 6 coi kết quả là `Object`
   và câu nào so sánh chuỗi sẽ chết với *"Operand of 'like' is of type 'java.lang.Object'"*. Phải
   khai qua `FunctionContributor` (`CoreFunctionContributor`), nạp bằng `ServiceLoader`.

Điểm đáng mừng: lỗi (2) nổ **lúc dựng bean** chứ không lúc chạy truy vấn, vì Spring Data biên dịch
mọi `@Query` khi tạo repository. Không bản build nào lên được với câu truy vấn hỏng. Cái giá là
thông báo hiện ra dưới dạng "không tạo được bean", rất dễ đọc nhầm thành lỗi cấu hình Spring.

**Chốt: hàm tên `sn_khong_dau`, tạo ở migration của `core`, số hiệu nhỏ hơn mọi migration dùng nó.**
Đặt ở `core` vì tìm kiếm bỏ dấu là nhu cầu của mọi module, và vì `CoreFunctionContributor` không
được phụ thuộc vào migration của module con.

⚠ **Flyway sắp thứ tự theo số hiệu trên TOÀN BỘ các thư mục**, nên `cms` bắt đầu từ 1016 là có chủ ý
— không phải khoảng trống bỏ quên.

---

### §10.12. Cấu hình cổng nằm ở `settings`, và Core mở một port ghi **theo nhóm** (19/8/2026)

Toàn bộ cấu hình giao diện cổng — tên site, logo, màu, chân trang, khối trang chủ, tuỳ chọn trình
chiếu — nằm ở bảng `settings` nhóm `SITE`. **Không có bảng `site_config`.** Ranh giới:

> Có nhiều dòng, người dùng thêm/bớt/sắp xếp → **bảng**. Đúng một giá trị cho cả hệ thống → **`settings`**.

Một bảng một dòng là tự nhận thêm một màn hình cấu hình thứ hai, một cơ chế xuất/nhập thứ hai và một
bộ nhớ đệm thứ hai — trong khi `settings` đã có đủ cả ba từ WS-6.

**Nhưng dữ liệu chung không có nghĩa là quyền chung.** API cấu hình hệ thống của MOD-05 gác bằng
`adm:setting:update`; Quản trị nội dung không có mã đó và **không nên có** — bắt họ cầm quyền sửa
chính sách bảo mật chỉ để đổi dòng bản quyền ở chân trang là mở quá tay. Vì vậy Core mở port thứ hai:

```java
SettingAdminPort.listGroup(String groupCode)
SettingAdminPort.updateInGroup(String groupCode, String key, String value)
```

⛔ **Mọi hàm đều mang `groupCode`, và đó là phần quan trọng nhất.** Một port ghi tự do dạng
`update(key, value)` thì chốt chặn duy nhất là annotation `@RequirePermission` trên controller — *một
dòng người ta có thể quên*. Buộc khai nhóm và từ chối khoá ngoài nhóm biến giới hạn thành thứ máy
kiểm tra được: `content` khai `"site"` nên nó **không có đường nào** chạm tới nhóm `SECURITY`.

### §10.13. Bộ nhớ đệm cấu hình dọn bằng **sự kiện**, không bằng sự tự giác (19/8/2026)

Cổng công khai đọc cả *cụm* cấu hình ở mọi lượt dựng trang, nên module nghiệp vụ phải có bộ nhớ đệm
riêng ở tầng cụm — bộ nhớ đệm của `SettingService` chỉ ở tầng từng khoá.

Hai bộ nhớ đệm thì phải có đường nối, và đường nối đó **không được đi qua sự tự giác**: cùng một dòng
`settings` sửa được từ **hai** màn hình. Nếu `SiteConfigService` chỉ tự dọn trong hàm `update` của
chính nó thì Quản trị viên hệ thống đổi tên cổng ở màn hình kia, giao diện báo thành công, và cổng
vẫn hiện tên cũ tới hết TTL — không lỗi, không dấu vết.

**Chốt: `SettingService.update` — nơi duy nhất ghi bảng — phát `SettingChangedEvent`; người quan tâm
nghe bằng `@TransactionalEventListener(AFTER_COMMIT)`.**

⚠ `AFTER_COMMIT` là **bắt buộc**, không phải cẩn thận thừa: dọn trước khi commit thì lượt đọc kế tiếp
nạp lại đúng giá trị **cũ** (giao dịch chưa nhìn thấy được), rồi giao dịch rollback — bộ nhớ đệm vừa
được làm mới bằng dữ liệu sai và không còn ai dọn nó lần nữa.

### §10.14. SVG: lớp khử trùng dựng ở WS-14 **không có đường nào chạm tới** (19/8/2026)

`FileValidator.detect()` trả `null` cho mọi tệp SVG — SVG là XML thuần, **không có magic bytes** — nên
`detectAndValidate` từ chối chúng ở *mọi* đường tải lên, kể cả đường mà chốt của dự án cho phép. Hệ
quả: `SvgSanitizer` có 9 bài kiểm riêng, xanh trọn vẹn, và **chưa bao giờ nằm trên một đường chạy
thật**. Lại đúng dạng lỗi đã trả giá nhiều lần: cơ chế có mặt, xanh, chưa ai đi qua.

Ba việc sửa:

1. `detect()` **đoán** SVG bằng cách đọc phần mở đầu: bỏ BOM, cắt khoảng trắng, bắt buộc mở đầu bằng
   `<` rồi mới tìm `<svg`. Đây là đoán chứ không phải xác thực — chấp nhận được vì thứ quyết định cuối
   cùng vẫn là **danh sách cho phép của nơi gọi**: chỉ màn hình cấu hình nhận diện khai
   `image/svg+xml`, mọi đường khác vẫn từ chối y như trước.
2. `AttachmentService.upload` cho SVG đi nhánh `SvgSanitizer` thay vì `ImageSanitizer`. ⭐ Nhánh này
   đặt **ở tầng đính kèm, không ở nơi gọi**: chọn được định dạng nào là việc của `allowedMimeTypes`,
   còn khử trùng thì không nơi gọi nào được phép quên.
3. `extensionOf("image/svg+xml") → "svg"`.

**Bài kiểm chứng minh:** tải một logo SVG có `<script>` và `onload` qua đúng đường production, rồi đọc
lại **từ MinIO** — nội dung đã lưu không còn phần chạy được, và vẫn còn hình vẽ.

### §10.15. Vì sao seed khung danh mục/menu, trong khi V…1008 cố ý **không** seed cơ cấu tổ chức (19/8/2026)

Nhìn qua thì mâu thuẫn. Thực ra hai loại dữ liệu chịu hậu quả khác hẳn nhau khi đoán sai:

| | `org_units` (V…1008 — **không** seed) | Danh mục · menu · trang tĩnh (V…1021 — **có** seed) |
|---|---|---|
| Vai trò | Dữ liệu **chịu tải** — phân quyền tầng 3, hồ sơ công trình, hồ sơ nhân sự đều neo vào id | Dữ liệu **trình bày** |
| Sửa sai tốn gì | Phải di chuyển dữ liệu đã bám vào | Đổi tên, kéo thả, xoá — vài phút |
| Để trống tốn gì | Không gì cả, chờ G8 | **Cổng rỗng thì không có gì để Công ty xem lúc nghiệm thu** — G14 quay lại chặn đúng lúc muộn nhất |

Bốn trang tĩnh đặt thẳng ở trạng thái **Xuất bản**, không đi qua quy trình duyệt. Cố ý, và chỉ đúng
cho dữ liệu khởi tạo: **không có bước chuyển giả nào được ghi vào lịch sử**, `created_by` để `NULL`
(= hệ thống) nên nhật ký không hề nói rằng có người nào đó đã duyệt. Để ở Nháp thì menu trỏ vào bốn
địa chỉ trả 404 — đúng thứ mà việc seed này sinh ra để tránh.

⛔ **Không seed tham số bật/tắt widget thuỷ văn**, dù T15.5 ghi "giữ chỗ cấu hình": widget cần MOD-03
(Phase 2) nên chưa dòng mã nào đọc được khoá đó, và một công tắc chưa ai đọc chính là lỗi vừa sửa ở
WS-12 (§10.9). Chỗ giữ là một khối bị khoá trên giao diện, không phải một dòng trong CSDL.

Tương tự, **không** thêm `site.maintenance-mode`: khoá `system.maintenance-mode` đã có từ WS-7 và
đang được `MaintenanceFilter` đọc thật. Hai công tắc cho một bóng đèn thì người vận hành gạt cái đang
nhìn thấy, hệ thống nghe cái kia.

### §10.16. Cổng công khai đọc bằng địa chỉ **nội bộ**, trình duyệt đọc bằng địa chỉ ngoài (20/8/2026)

`public-web` gọi backend ở hai vai trò khác nhau và chúng **không thể** dùng chung một địa chỉ:

| Ai gọi | Biến | Giá trị ở Docker |
|---|---|---|
| Server Component / route handler (trong container) | `API_INTERNAL_BASE_URL` | `http://app:8080/api/v1` |
| Trình duyệt của khách (ảnh, đếm lượt xem) | `NEXT_PUBLIC_API_BASE_URL` | `http://localhost:18080/api/v1` |

Bản đầu chỉ có biến thứ hai. Triệu chứng: cổng **dựng ra trang trắng** — mọi lượt gọi phía máy chủ
trỏ vào `localhost` của chính container `public-web`, nơi không có backend nào. Không có lỗi HTTP
nào nổi lên vì `apiGet` cố ý nuốt lỗi để một sự cố backend không làm trắng cả trang; log có, nhưng
phải đi tìm.

`NEXT_PUBLIC_*` bị **nướng vào bundle lúc build**, nên nó không thể là địa chỉ nội bộ: người dùng
cuối không phân giải được tên `app`.

### §10.17. Hâm nóng ISR phải gửi **đường dẫn**, không gửi nhãn (20/8/2026)

Ảnh Docker của cổng dựng ở CI, lúc đó không có backend nào chạy — Next vẫn dựng sẵn trang chủ và
`sitemap.xml` thành HTML tĩnh, với nội dung rỗng.

Phần không hiển nhiên, và **đo thật mới biết**: lượt `fetch` hỏng thì **không có mục cache nào mang
nhãn được tạo ra**, nên `revalidateTag` về sau không có gì để lần ngược tới tuyến đường đó. Đo ở
WS-16: gửi nhãn hai lần, cả hai trả `{"revalidated":true}`, trang **vẫn rỗng**; gửi
`{"path":"/"}` → trang có nội dung ngay. Chỉ `revalidatePath` mới trỏ thẳng vào bộ nhớ đệm của
tuyến đường.

Vì vậy `PortalCache.warmUp()` đặt hai việc **theo đường dẫn** (`/` và `/sitemap.xml`) vào hàng đợi
khi backend sẵn sàng. Đi qua hàng đợi chứ không gọi thẳng: lúc backend lên thì cổng thường *chưa*
(compose để `public-web` chờ `app` khoẻ), nên lượt thử lại của hàng đợi lo phần đó.

⚠ Sitemap nằm trong danh sách là có lý do riêng: một sitemap chỉ có mỗi trang chủ thì công cụ tìm
kiếm không thấy bài nào của cổng — hỏng âm thầm, và hỏng ở đúng thứ cổng TTĐT sinh ra để làm. Đo
thật: trước khi thêm, `sitemap.xml` có **1** `<url>`; sau khi thêm, **10**.

### §10.18. `HttpClient` của JDK phải ép **HTTP/1.1** khi gọi Next (20/8/2026)

Việc hâm nóng hỏng ba lượt liên tiếp với `IllegalStateException: HTTP/1.1 header parser received no
bytes`, trong khi `curl` cùng URL trả 200. Nguyên nhân: `HttpClient` mặc định **HTTP/2**, và trên
HTTP thuần nó gửi kèm `Connection: Upgrade, HTTP2-Settings` để thử nâng cấp (h2c). Máy chủ Node của
Next không hiểu và **đóng kết nối**, nên phía Java không đọc được byte nào.

Chốt `.version(HttpClient.Version.HTTP_1_1)`. Đây là chỗ mà "thử bằng `curl` thấy chạy" gây hiểu
nhầm hẳn về phía sai: hai công cụ nói hai giao thức khác nhau.

### §10.19. CSRF **không áp** cho `/api/v1/public/**` (20/8/2026)

CSRF là tấn công **mượn phiên** của nạn nhân: kẻ tấn công dụ trình duyệt gửi request kèm cookie mà
chủ nhân không biết. Endpoint công khai không đọc phiên nào, nên **không có gì để mượn**.

Ngược lại, chặn ở đó thì trình duyệt của khách vãng lai không có cookie CSRF để gửi kèm, và
`POST /api/v1/public/articles/{slug}/views` trả **403** — bộ đếm lượt xem *không bao giờ* chạy được.
Đo thật trước khi sửa: 403; sau khi sửa: 204, và `view_count` lên đúng số lượt gọi.

`PublicHttpTest` giữ **cả hai vế**: đường công khai không bị 403, và đường quản trị **vẫn** bị chặn
khi thiếu token. Chỉ kiểm vế đầu thì một lượt tắt CSRF toàn cục cũng xanh.

### §10.20. Luật ArchUnit thứ ba: cấm **tự gọi hàm `@Transactional`** (20/8/2026)

Spring cài giao dịch bằng proxy, nên chú thích chỉ có tác dụng khi lời gọi **đi từ ngoài vào**. Một
hàm gọi sang hàm `@Transactional` của cùng lớp bằng `this` thì giao dịch **không mở** — mã biên dịch
trót lọt, bài kiểm gọi thẳng hàm bên trong vẫn xanh, chỉ production hỏng.

Lỗi này đã sập **hai lần**: `BackupService` (WS-7, dòng `RUNNING` không được commit trước khi
`pg_dump` chạy) và `ViewCountService` (WS-16, ném `TransactionRequiredException` mỗi phút trong log
của bộ hẹn giờ — **chưa từng ghi được một lượt xem nào**). Hai lần là đủ để nó thành luật.

Luật chạy lần đầu tìm ra **8 vi phạm trong mã production**, và chúng chia làm ba mức:

| Mức | Chỗ | Hệ quả thật |
|---|---|---|
| ⚠⚠ Lỗi thật | `NotificationService.notify(NotifyRequest)` | Xem §10.21 — nặng nhất, và tìm ra một cách gián tiếp |
| ⚠ Lỗi tiềm ẩn | `CodeGenerator.next(seqType, padding)` | Nạp chồng tiện dụng gọi bản `REQUIRES_NEW` bằng `this` → bộ đếm mã **rơi vào chính giao dịch nghiệp vụ mà nó phải đứng ngoài**. Lượt ghi hỏng thì bộ đếm lùi theo và bản ghi kế tiếp mang **lại đúng mã đó** — đúng thứ lớp này sinh ra để chống |
| Chú thích sai sự thật | `BackupService.pruneExpired`, `JobService.findActiveByDedupKey`, `SettingService.getString` | Hành vi không đổi (chỉ đọc, hoặc chỉ xoá tệp), nhưng chú thích ghi một bảo đảm không tồn tại |

Cách chữa thống nhất với phần còn lại của dự án: mở giao dịch bằng `TransactionTemplate`
(`JobService`, `SecurityEventService`, `BackupService` đã làm vậy từ WS-6/WS-7), để việc "có giao
dịch hay không" không phụ thuộc vào ai gọi từ đâu.

⚠ Luật cố ý **không** báo khi người gọi cũng `@Transactional` và hàm đích dùng propagation mặc định
— lúc đó giao dịch đã mở từ lượt gọi ngoài vào, đây là cách viết hợp lệ. Báo cả trường hợp đó là
luật bị nới ra trong vòng một tuần. `SilentFailureRuleSelfCheckTest` có bài chứng minh **cả hai
chiều**: bắt được vi phạm, và không báo nhầm cặp hợp lệ.

### §10.21. Chú thích Java bám vào **khai báo kế tiếp**, không bám vào đoạn chú giải (20/8/2026)

Lỗi nặng nhất của WS-16, và nó không nằm trong WS-16 — luật §10.20 lôi nó ra một cách gián tiếp.

Ở `NotificationService`, khối SPI thêm tại WS-12 được chèn vào **giữa** một
`@Transactional(readOnly = true)` và hàm nó thuộc về:

```java
// ---- Hộp thư của người dùng ----
@Transactional(readOnly = true)
// ---- Hợp đồng cho module nghiệp vụ (core.spi) ----   ← khối chèn vào ở WS-12
@Override
public void notify(NotifyRequest request) { … }          ← nhận nhầm chú thích
…
public Page<InboxEntry> inbox(…) { … }                   ← mất chú thích của chính nó
```

Trình biên dịch gắn chú thích cho khai báo kế tiếp; đoạn chú giải ở giữa không cản gì cả. Hệ quả:
**cửa vào mà mọi module nghiệp vụ dùng để gửi thông báo chạy trong giao dịch chỉ đọc**, còn `inbox()`
thì mất chú thích của chính nó.

**Đo thật, không suy đoán** — cắm lại đúng lỗi cũ rồi chạy bài kiểm mới: PostgreSQL từ chối thẳng với
`ERROR: cannot execute INSERT in a read-only transaction`. Nói cho chính xác: đây **không** phải hỏng
im lặng lúc chạy — module nghiệp vụ đầu tiên gọi `NotificationPort.notify(...)` sẽ nhận lỗi 500 ngay
lần đầu. Cái im lặng là ở **thời điểm phát hiện**: lỗi nằm im từ WS-12 tới WS-16 qua bốn WS và hơn
370 bài kiểm, vì `WorkflowEngine` ở trong `core` nên nó gọi thẳng `notify(NotificationRequest)` và
**không ai đi qua cửa SPI**. Người đi đầu tiên sẽ là WS-17.

Lại đúng dạng *một ranh giới chưa ai đi qua thì chưa biết nó đúng hay sai* (§9.14) — và lần này ranh
giới đó là thứ Phase 1 vừa dựng ra ở WS-12 để module nghiệp vụ dùng.

**Luật rút ra**: khi chèn một khối mới vào giữa lớp, kiểm lại chú thích ở mép trên của chỗ chèn.
Không có cách nào để mắt người thấy điều này — `@Transactional` cách hàm nhận nó **tám dòng chú
giải**. Thứ bắt được là một phép kiểm máy chạy.

### §10.22. Trình soạn thảo: TipTap (MIT), vì CKEditor và TinyMCE đều đã chuyển sang GPL (20/8/2026)

`phase1-tracking.md` viết "CKEditor 5 hoặc TinyMCE, bản tự host". Tới thời điểm dựng, **cả
hai đều là GPL**: CKEditor 5 từ v44, TinyMCE từ v7. Nhúng một thư viện GPL vào `admin-app`
làm ứng dụng trở thành tác phẩm phái sinh và phải phát hành theo GPL khi bàn giao cho Công
ty. Đó là **quyết định pháp lý của chủ đầu tư**, không phải của người viết mã — nên không
chọn thay được, và cũng không nên âm thầm chọn rồi để lộ ra lúc bàn giao.

TipTap là **MIT**, và nó giải thêm một vấn đề mà bộ soạn thảo trọn gói không giải: ở đây ta
khai **chính xác** những nút nào tồn tại. Bộ trọn gói có hàng chục nút mà phần lớn tạo ra thẻ
`HtmlSanitizer` sẽ gỡ — xem §10.23.

Cái giá: phải tự viết ba extension nhỏ (`AlignClass`, `FigureImage`, `VideoEmbed`), tổng ~200
dòng. Đổi lại là bộ từ vựng HTML khép kín và kiểm được bằng máy.

### §10.23. Bộ từ vựng của trình soạn thảo phải khớp danh sách cho phép của bộ lọc (20/8/2026)

`HtmlSanitizer` chạy lúc **ghi**. Thẻ ngoài danh sách bị gỡ, im lặng, và bài **vẫn lưu thành
công**. Nên một nút trên thanh công cụ tạo ra thẻ ngoài danh sách cho ra đúng kịch bản này:

> Biên tập viên chèn bảng, bấm Lưu, hệ thống báo *"Đã lưu"*, mở lại thì bảng biến mất. Không
> lỗi, không cảnh báo, và người dùng sẽ nghĩ mình thao tác sai.

Đây là biến thể của lỗi đã trả giá nhiều lần trong dự án — một cơ chế chạy đúng ở **một nửa
đường**. Lần này nạn nhân là người dùng cuối chứ không phải lập trình viên, và họ không có
cách nào tự chẩn đoán.

Hai danh sách nằm ở hai ngôn ngữ và hai thư mục, trình biên dịch không bắt lệch được. Nên
`EditorVocabularyTest` (Java) đọc `editorSchema.ts` (TypeScript), chạy mẫu HTML qua
`HtmlSanitizer` **thật**, và đòi mọi thẻ sống sót. Cùng cách `error-map.test.ts` canh danh
mục mã lỗi, chỉ ngược chiều — bắt buộc phải ngược, vì logic khử trùng là Java.

**Bốn phát hiện ở lượt chạy đầu:**

| # | Phát hiện | Vì sao im lặng |
|---|---|---|
| 1 | **`<s>` bị gỡ** | `Safelist.relaxed()` của jsoup chỉ có `strike` (thẻ HTML5 đã loại bỏ); mọi trình soạn thảo hiện đại phát ra `<s>`. Nút bấm được, lưu xong định dạng biến mất |
| 2 | **Nhúng video bị gỡ sạch** | CN-01.1 yêu cầu embed YouTube/Vimeo, mà `clean()` gỡ mọi `<iframe>`. Chức năng nằm trong đặc tả và **chưa bao giờ chạy được** |
| 3 | **Căn lề phải đi bằng `class`** | `HtmlSanitizer` cấm `style` — đúng, vì `style` mở đường cho `position:fixed` phủ kín trang hoặc chữ trắng trên nền trắng giấu nội dung trong một bài **đã được duyệt**. Bản gốc `@tiptap/extension-text-align` phát ra `style` |
| 4 | **Chính bài kiểm gộp hai nguyên nhân** | "Thẻ không có trong kết quả" = bộ lọc gỡ nó (lỗi thật) **hoặc** mẫu chưa từng có nó (lỗi bài kiểm). Lượt đỏ đầu tiên chỉ đường sai; suýt thêm 4 thẻ vào safelist trong khi chúng chưa bao giờ bị gỡ |

⚠ Danh sách tên miền video **tách hẳn** khỏi danh sách tên miền bản đồ, và có bài kiểm chứng
minh hai danh sách không lẫn vào nhau: khối bản đồ ở chân trang không phải chỗ nhúng video, và
nội dung bài không phải chỗ nhúng bản đồ. Gộp làm một danh sách là nới cả hai cùng lúc.

Đường nhúng YouTube chuẩn hoá sang `youtube-nocookie.com` **ngay lúc dán**: bản thường đặt
cookie theo dõi ngay khi trang tải, kể cả khi người đọc không bấm phát — với cổng thông tin
của cơ quan nhà nước thì đó là chuyện không nên có, và người đọc không có cách nào từ chối.

### §10.24. Đếm ký tự SEO bằng `Intl.Segmenter`, không bằng `Array.from` (20/8/2026)

Ba cách đếm cho ba kết quả khác nhau với chữ "Đề" dán từ Word (dạng NFD):

| Cách | Kết quả | |
|---|:-:|---|
| `String.length` | 4 | đơn vị mã UTF-16 |
| `Array.from(...).length` | 4 | điểm mã — **mỗi dấu tổ hợp là một điểm mã riêng** |
| `Intl.Segmenter` | 2 | cụm hiển thị ✔ |

Bản đầu dùng `Array.from` kèm một dòng tài liệu khẳng định như vậy là đủ. Hậu quả: ô đếm báo
vượt ngưỡng trong khi mắt thấy chưa vượt, và người soạn sẽ cắt bớt một tiêu đề hoàn toàn hợp
lệ. Bài kiểm bắt được vì nó khẳng định **cả ba con số**, không chỉ con số cuối.

### §10.25. Nội dung bài lưu **HTML**, không lưu cây JSON — và ba cạnh của hợp đồng (20/8/2026)

Câu hỏi đặt ra khi rà soát phần chèn ảnh: có nên bỏ HTML, lưu thẳng cây JSON của TipTap vào một
cột `jsonb`, để không phải phân tích lại chuỗi HTML mỗi lần mở bài? **Chốt: giữ HTML.**

**Nỗi lo gốc đã được đo, và nó không thành hiện thực.** jsoup không trả lại đúng chuỗi ta đưa
vào — nó dựng lại tài liệu rồi in ra có thụt lề. Đo trên jsoup 1.23.1 với bảy mẫu:

| Mẫu | Kết quả |
|---|---|
| `<pre><code>` có thụt lề | **giữ nguyên** — jsoup biết khoảng trắng trong `pre` là nội dung |
| thẻ inline sát chữ (`Cống<strong>Yên Nghĩa</strong>đóng`) | **giữ nguyên** |
| câu dài có `<strong>`/`<em>` ở giữa | **giữ nguyên** |
| `<table>`, `<ul>`, `<figure>`, nhiều `<p>` liền nhau | thêm `\n` + thụt lề **giữa các thẻ khối** |

Ba mẫu đầu mới là chỗ dễ hỏng (thêm khoảng trắng giữa chữ và thẻ inline là **đổi nội dung câu**),
và jsoup không đụng vào. Phần thụt lề giữa thẻ khối là vô hại — `editorRoundTrip.test.ts` khẳng
định điều đó bằng cách phân tích cả bản gọn lẫn bản có thụt lề rồi so **cây nút**, chứ không so
chuỗi.

**Cái giá nếu đổi sang `jsonb`** — bốn khoản, khoản đầu là khoản chặn:

1. **Mất `HtmlSanitizer`.** Lớp khử trùng đang chạy ở backend bằng jsoup (danh sách CHO PHÉP, thư
   viện có tuổi đời, đã bị soi nhiều năm). Với cây JSON thì backend phải tự duyệt cây bằng Java —
   tức là **viết lại bộ khử trùng**, đúng thứ mà `core/pom.xml` đã ghi lý do không tự viết. Bộ mới
   sẽ chưa từng có ai thử tấn công.
2. **Cổng công khai hết dựng được HTML** nếu không mang cả schema TipTap sang phía máy chủ. Làm
   được, nhưng khi đó admin-app và public-web phải **luôn cùng phiên bản schema** — lệch một bản là
   bài cũ hiển thị sai.
3. **Vỡ ba thứ đang chạy**: so sánh phiên bản (`diff.ts` dựng trên DOM), tìm kiếm toàn văn
   (`sn_khong_dau` chạy trên chữ), và chế độ soạn HTML (người soạn nội dung cơ quan dán từ Word —
   dùng thật, không phải tính năng cho vui).
4. Lưu **cả hai** thì có hai nguồn sự thật cho cùng một nội dung — loại lỗi dự án này đã trả giá
   nhiều lần.

**Phần đúng trong đề xuất đã lấy hết**: nút ảnh là node TipTap tự viết, chèn bằng `insertContent`
để giữ đúng vị trí con trỏ; kéo-thả viết bằng `handleDrop` thường, lấy vị trí bằng `posAtCoords`
rồi `insertContentAt` — **thả đâu ảnh nằm đó**, không nhảy về chỗ con trỏ cũ; ô giữ chỗ lạc quan
hiện ngay bằng `blob:` rồi thay `src` thật khi tải xong.

**⛔ Không dùng presigned URL cho đường TẢI LÊN.** Cho trình duyệt `PUT` thẳng vào MinIO thì bỏ
qua toàn bộ chuỗi kiểm: `FileValidator` (magic bytes — đuôi tệp nói dối được), `ImageSanitizer`
(bóc EXIF, mà EXIF ảnh chụp bằng điện thoại mang **toạ độ GPS** — đăng lên cổng công khai là công
bố vị trí công trình thuỷ lợi), `SvgSanitizer`, ClamAV, hạn mức đọc từ `settings`. Ta cũng không
có bộ bắt sự kiện MinIO nào để quét bù về sau. Đường **đọc** thì đã chốt ở §10.19 là không presign.

**Hợp đồng có BA cạnh, không phải hai.** Bộ từ vựng chuyển từ `admin-app` sang
`design-tokens/editor-schema.ts` vì nó là thoả thuận giữa *soạn thảo* (admin-app sinh ra thẻ) ·
*khử trùng* (backend giữ lại) · *hiển thị* (public-web dựng CSS). Mỗi cạnh có một phép canh:

| Cạnh | Phép canh | Nơi |
|---|---|---|
| soạn thảo → khử trùng | `EditorVocabularyTest` (Java đọc mã nguồn TS) | `core` |
| khử trùng → soạn thảo | `editorRoundTrip.test.ts` | admin-app |
| khử trùng → hiển thị | `articleContentCss.test.ts` | public-web |

Cạnh thứ ba **chưa từng tồn tại**, và đó là chỗ hỏng nặng nhất tìm được — xem §10.26.

### §10.26. Bốn lỗi im lặng ở đường chèn ảnh, và lỗi nặng nhất không nằm ở trình soạn thảo (20/8/2026)

Rà soát bắt đầu từ một câu hỏi hẹp — *chèn ảnh vào đúng vị trí có chạy không* — và tìm ra bốn lỗi.
Cả bốn đều không sinh ra dòng lỗi nào, và **cả bốn đều được phát hiện bằng cách chạy máy, không
phải bằng cách đọc mã**.

**1. ⚠⚠ Căn lề ảnh chưa bao giờ hoạt động.** `AlignClass` khai áp dụng cho `'image'` và `'figure'`.
Không tên nào tồn tại: nút ảnh do `FigureImage` đăng ký mang tên `figureImage`. Hỏng hai tầng, cả
hai đều im:

- TipTap **bỏ qua lặng lẽ** `addGlobalAttributes` trỏ vào type không có thật → nút ảnh không hề có
  thuộc tính `align` (đo bằng `getSchema`: `figureImage attrs: ['src','alt','caption']`);
- lệnh trả `NHOM_AP_DUNG.some(t => commands.updateAttributes(t, …))`, mà `.some` **dừng ở phần tử
  đầu tiên trả `true`** và `'paragraph'` luôn trả `true` → lệnh báo thành công, nút sáng lên, ảnh
  đứng yên.

**2. ⚠⚠ Cổng công khai không có CSS nào cho nội dung bài.** Thân bài mang class `prose`, mà gói
`@tailwindcss/typography` **chưa từng được cài** — `prose` là class rỗng. Cộng với preflight của
Tailwind xoá hình dạng mặc định của trình duyệt: danh sách mất dấu đầu dòng và thụt lề, `<h3>`/`<h4>`
đúng bằng cỡ chữ đoạn văn, bảng không viền, `<figcaption>` không phân biệt được với một câu trong
bài, `sn-align-*` không định nghĩa ở đâu.

Điều đáng sợ nhất của lỗi này: **màn hình xem trước trong admin-app vẫn đúng**, vì nó dùng CSS của
trình soạn thảo. Biên tập viên định dạng kỹ, xem trước thấy đẹp, xuất bản, và không bao giờ mở lại
trang công khai để đối chiếu. Tài liệu của `AlignClass` thậm chí đã viết sẵn điều kiện *"với điều
kiện cổng công khai có định nghĩa ba class đó trong CSS"* — **điều kiện được ghi ra và không ai
thực hiện**.

**3. Chú thích ảnh không có đường nào tạo ra được.** `FigureImage` khai thuộc tính `caption`,
`HtmlSanitizer` cho `figcaption` qua, `EDITOR_SAMPLE_HTML` có nó, `EditorVocabularyTest` xanh — mà
`RichTextEditor` truyền cứng `caption: null` và không có ô nhập nào. CN-01.1 yêu cầu *"ảnh inline
(căn lề, caption)"*: hai vế, cả hai đều hỏng.

**4. Kéo một tệp ảnh vào bài làm mất bài đang soạn.** Không có `handleDrop` thì trình duyệt xử lý
theo mặc định của nó: **điều hướng cả tab sang tệp vừa thả**. Đây là lỗi nặng hơn "thiếu tính năng"
— nó phá công việc đang dở.

**Điều đáng ghi nhất: bài canh cho lỗi (2) ban đầu XANH trong khi không kiểm được gì.** Bản đầu hỏi
`CSS.includes('.sn-align-center')`. Kiểm chứng ngược bằng cách xoá hẳn quy tắc `text-align: center`
→ **vẫn xanh**, vì chuỗi đó còn xuất hiện ở một quy tắc khác trong cùng tệp
(`figure.sn-align-center`). Bài canh chống lỗi im lặng lại chính là một lỗi im lặng. Nay nó tách
tệp thành từng quy tắc và hỏi **thuộc tính có thật sự được khai không**; kiểm chứng ngược ở mức
thuộc tính bắt đủ ba lượt phá hoại.

⛔ **Luật rút ra, bổ sung cho `conventions.md` §1.5**: phép canh dựa trên *sự có mặt của một chuỗi*
gần như luôn yếu hơn ta tưởng, vì cùng một chuỗi thường xuất hiện ở nhiều chỗ với ý nghĩa khác
nhau. Canh **cấu trúc** (quy tắc nào, thuộc tính nào) chứ đừng canh **văn bản**.

### §10.27. ⚠⚠ Biến môi trường "để trống" **không hề trống** — lỗi bảo mật im lặng từ WS-3 (20/8/2026)

Phát hiện khi dựng tài khoản quản trị để kiểm thử trên trình duyệt. Docker Compose đọc `env_file`
theo luật riêng, **không phải luật của shell**: với dòng

```
BOOTSTRAP_ADMIN_PASSWORD=           # [T] chỉ dùng 1 lần
```

nó **không** cắt phần chú thích, mà cắt khoảng trắng đầu rồi lấy toàn bộ phần còn lại làm giá trị.
Kiểm chứng bằng ví dụ tối giản (alpine + compose):

```
RONG=           # chú thích     →  RONG=[# chú thích]      ⛔
CO_GIATRI=abc   # chú thích     →  CO_GIATRI=[abc]         ✔
```

**Chỉ trường hợp giá trị rỗng mới hỏng** — mà đó đúng là những biến dùng quy ước *"để trống = tắt
tính năng"*, nên hậu quả rơi vào chỗ đắt nhất:

| Biến | "Rỗng" nghĩa là | Thực tế |
|---|---|---|
| `BOOTSTRAP_ADMIN_PASSWORD` | không kích hoạt tài khoản quản trị | ⚠⚠ `AdminBootstrapRunner` chạy ở **mọi** lượt khởi động, đặt mật khẩu `superadmin` thành **chính đoạn chú thích** — chuỗi nằm trong tệp `.example` **đã commit lên repo** |
| `DB_RESTORE_PASSWORD` | tắt khôi phục qua UI (`ADM-2010`) | thao tác phá huỷ nhất trong hệ tưởng như đã được cấu hình |
| `SMTP_USERNAME` / `SMTP_PASSWORD` | máy chủ thư không cần xác thực | client thử `AUTH` với chuỗi rác → hỏng toàn bộ đường gửi thư |
| `GOOGLE_MAPS_API_KEY` | chỉ dùng OSM | đi đường Google Maps với khoá rác |
| `LOG_FILE` | không ghi log ra tệp | mở tệp tên `# [T]` |

**37 dòng** dính, trải cả ba tệp `local/staging/prod.env.example`. Đây là lý do `superadmin` ở máy
dev đang ACTIVE với một mật khẩu **không ai cố ý đặt**.

**Vì sao `UnresolvedPlaceholderGuard` (WS-4/T4.8) không bắt được.** Bộ canh đó tìm giá trị còn
nguyên dạng `"${TÊN_BIẾN}"` — dấu hiệu placeholder không giải được. Ở đây giá trị **giải ra bình
thường**, chỉ là giải ra sai; và nó **không rỗng** nên `@NotBlank` cũng đi qua. Cùng một họ lỗi,
khác một bậc: lần trước là *"thiếu biến mà tưởng có"*, lần này là *"có biến mà tưởng thiếu"*.

⚠ **Lỗi chỉ tồn tại ở đường Docker.** Chạy native thì `make` nạp tệp bằng shell, mà shell cắt chú
thích đúng — nên **hai lối chạy cho ra hai hành vi khác nhau từ cùng một tệp cấu hình**. Đó là lý
do nó sống sót qua mọi lượt chạy tay từ WS-3 tới nay.

**Sửa**: chú thích lên dòng riêng, để lại `TÊN_BIẾN=` trống thật. Canh bằng `EnvFileCommentTest`
(có kiểm chứng ngược: cắm lại đúng dòng cũ → đỏ, chỉ đích danh tên biến và số dòng).

### §10.28. Font Noto Sans **tự host**, không lấy từ CDN Google (20/8/2026)

`docs/ui-styles.md` bản đầu quy định `@import url('https://fonts.googleapis.com/…')` ở dòng đầu
`globals.css`, và `admin-app/index.html` còn nạp thêm một lượt nữa bằng thẻ `<link>` — **cùng một
bộ chữ tải hai lần**. Đổi sang gói `@fontsource/noto-sans`: **cùng bộ chữ, cùng trọng số, hình
hiện ra y hệt**, chỉ khác nơi tải về. Ba lý do, lý do đầu là lý do chặn:

1. ⛔ **`conventions.md` §4.5 đã chốt CSP `default-src 'self'`.** Khi WS-11 dựng nginx thì
   `fonts.googleapis.com` (biểu định kiểu) và `fonts.gstatic.com` (tệp chữ) đều bị chặn — trang vẫn
   hiện, chỉ rơi về font hệ thống, **không lỗi nào**. Loại hỏng im lặng chỉ lộ ra sau khi lên
   production, và `phase1-tracking.md` T20.1 đã ghi sẵn luật *"bản tự host, không CDN"* cho trình
   soạn thảo vì đúng lý do này.
2. **Quyền riêng tư.** Cổng của doanh nghiệp nhà nước gửi địa chỉ IP của **mọi người dân tra cứu**
   sang máy chủ Google ở mỗi lượt tải trang. Cùng lập luận đã dùng để chọn `youtube-nocookie.com`
   cho khối nhúng video (§10.23): người đọc không có cách nào từ chối.
3. **Tốc độ và mạng nội bộ.** `@import url(...)` ra mạng ngoài chặn lượt vẽ đầu tiên (NFR-02 cho
   3 giây); mạng Công ty chặn ra ngoài thì font không tải được mà không ai biết vì sao.

Đo sau khi đổi: **48 tệp `woff2`** nằm trong bản dựng của mỗi app, **0 tham chiếu** tới
`fonts.googleapis.com`/`fonts.gstatic.com` trong `admin-app/dist` lẫn `public-web/.next`.
⚠ Chỉ khai **6 trọng số dùng tới** (400/500/600/700 + nghiêng 400/500) — gói có 144 tệp `woff2`,
nhập cả gói là bắt người đọc tải thứ không bao giờ hiện ra.

### §10.29. ⚠⚠ Trình duyệt chưa từng gọi được API — CORS chặn toàn bộ giao diện (20/8/2026)

Phát hiện khi anh Quân mở `http://localhost:15173/dang-nhap` và nhận *"Không kết nối được máy
chủ"* kèm lỗi CORS ở tab Network. Truy ra: **cả giao diện quản trị chưa bao giờ dùng được trên
trình duyệt**, suốt từ WS-8 tới WS-20.

Backend **không cấu hình CORS** — và đó là lựa chọn đúng, vì ở production nginx đứng trước cả hệ
(T11.5) nên admin-app và API vốn cùng origin. Nhưng `compose.local.yml` lại build bundle với
`VITE_API_BASE_URL=http://localhost:18080/api/v1`, trong khi giao diện phục vụ ở cổng 15173. Khác
cổng là **khác origin**, nên trình duyệt gửi lượt kiểm trước và nhận `403 Invalid CORS request`.

**⚠⚠ Vì sao mọi lượt kiểm trước đây đều xanh.** Chúng gọi bằng `curl` thẳng vào cổng backend:

| Lượt kiểm | Đã chứng minh | KHÔNG chứng minh |
|---|---|---|
| WS-20: *"4 route CMS trả 200, API trả 401"* | backend chạy | trình duyệt gọi được |
| WS-16: *"bộ đếm lượt xem lên đúng 7/7"* | endpoint chạy | trình duyệt gọi được |

`curl` không phải trình duyệt: không có origin, không có chính sách cùng nguồn, **không làm
preflight** — nên nó đi lọt qua đúng bức tường chặn người dùng thật. Đây là biến thể mới của bài
học cũ *"kiểm bằng một đường khác đường production đi thì chưa kiểm gì cả"*, cùng họ với
`BackupServiceTest` mock `PostgresToolRunner` (§9.12) và `ViewCountService` gọi thẳng `day()` thay
vì qua proxy (§10.20).

**Chốt: trình duyệt luôn gọi CÙNG origin**, việc chuyển tiếp do tầng phục vụ lo — đúng hình dạng
production, nên không sinh ra một đường đi mà production không có.

| Nơi | Cách chuyển tiếp |
|---|---|
| `make dev-docker` — admin-app | nginx của chính image: `location /api/` → `${API_UPSTREAM}` |
| `make dev-docker` — public-web | Route Handler `src/app/api/v1/[...path]/route.ts` |
| `make dev-native` | `server.proxy` của Vite |
| staging/production | nginx chung (T11.5) |

Mã FE mặc định về đường dẫn tương đối `/api/v1`. ⚠ Phải dùng `||` chứ **không** `??`: compose
truyền biến để trống thì Vite/Next nhúng vào bundle một **chuỗi rỗng**, mà chuỗi rỗng không phải
nullish nên `??` giữ nguyên nó → `baseURL = ''` → lượt gọi mất hẳn tiền tố `/api/v1`.

**Ba lỗi phụ, cả ba chỉ lộ ra khi chạy thật:**

1. ⚠⚠ **`rewrites()` của Next bị nướng vào lúc BUILD.** Với `output: 'standalone'`, Next gọi
   `rewrites()` lúc build rồi ghi kết quả đã giải sẵn vào `.next/required-server-files.json`. Biến
   `API_INTERNAL_BASE_URL` chưa tồn tại lúc build nên rơi về `localhost:8080` và **cứng luôn** —
   container có đúng biến môi trường (kiểm bằng `printenv`) mà log vẫn `ECONNREFUSED 127.0.0.1:8080`.
   Chú thích tôi vừa viết khẳng định ngược lại, và bản chạy thật bác bỏ nó. Chuyển sang **Route
   Handler** — chạy mỗi request, đọc env lúc chạy, một image dùng cho mọi môi trường (đúng nguyên
   tắc *"đóng gói một lần, đề bạt cùng image"*).
2. ⚠⚠ **`proxy_pass http://app:8080` làm nginx phân giải DNS lúc nạp cấu hình.** Backend chưa lên
   là `[emerg] host not found in upstream "app"` và container quay vòng khởi động lại — **một sự cố
   của backend kéo theo cả trang trắng**, thay vì chỉ hỏng lượt gọi API. Sửa bằng `resolver` + biến
   trong `proxy_pass` để hoãn phân giải tới lúc có request. Đo lại: `RestartCount 0`.
3. ⚠ **`resolver ${NGINX_LOCAL_RESOLVERS}` không được thay.** Script `15-local-resolvers.envsh` của
   image mở đầu bằng `[ "${NGINX_ENTRYPOINT_LOCAL_RESOLVERS:-}" ] || return 0` — là tính năng **phải
   chủ động bật**. Thiếu biến đó thì nginx đọc nguyên văn chuỗi `${NGINX_LOCAL_RESOLVERS}` và chết
   với `host not found in resolver`.

**Kiểm chứng trên bản chạy thật**, gọi kèm `Origin` như trình duyệt: đăng nhập qua cổng 15173 →
`success: true`, bundle còn **0** tham chiếu tới `:18080`; cổng công khai `GET /public/categories`
→ **200**, `POST .../views` → **204** (trước là 403).

⛔ **Luật bổ sung cho `conventions.md` §1.5**: một endpoint mà **trình duyệt** phải gọi thì lượt
kiểm chứng phải mang `Origin` — `curl` trần đi qua được đúng bức tường chặn người dùng thật.

---

### §10.29-a. ⚠⚠ Bản sửa §10.29 sửa vào chỗ không ai đọc — lỗi sống nguyên sau khi "đã sửa" (20/8/2026)

Anh Quân dựng lại image rồi báo **vẫn CORS**. Bản sửa hôm trước đổi `compose.local.yml` thành
`VITE_API_BASE_URL: ${VITE_API_BASE_URL:-}` — mặc định rỗng, đúng ý định. Nhưng `:-` **chỉ có tác
dụng khi biến vắng mặt**, mà Makefile chạy compose với `--env-file env/local.env`, và `--env-file`
nuôi luôn phép thế biến. Tệp env vẫn giữ nguyên dòng:

```
VITE_API_BASE_URL=http://localhost:18080/api/v1      # [B] admin-app
```

Giá trị đó **thắng** mặc định trong compose. Đo lại bằng chính docker:

```
$ docker compose --env-file env/local.env -f compose.local.yml --profile full config | grep VITE
        VITE_API_BASE_URL: http://localhost:18080/api/v1
```

Nghĩa là mọi lượt dựng lại đều nướng đúng địa chỉ khác origin vào bundle, y như trước.

**⚠⚠ Và bài canh viết ra để chặn đúng lỗi này thì xanh trọn vẹn.** `FrontendSameOriginTest` khẳng
định *"biến build của FE không được trỏ sang origin khác"* — nhưng nó soi **`compose.local.yml`**,
tức là soi **giá trị mặc định**, trong khi nơi quyết định là tệp env. Nó chứng minh một điều đúng
về một tệp không ai đọc tới.

> ⛔ **Luật: canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH.** Mặc định chỉ là thứ dùng đến khi
> không ai ghi đè — mà ở đây luôn có người ghi đè. Cùng họ với §9.8.2 "xanh mà không chạy", nhưng
> tinh vi hơn: phép kiểm chạy thật, khẳng định đúng, chỉ là **về sai đối tượng**.

**Lỗi ở ba nơi, không phải một** — chữa một chỗ là lần sau lại sập ở chỗ khác:

| Nơi | Sai gì | Sửa |
|---|---|---|
| `deploy/env/*.env*` (4 tệp) | gán địa chỉ tuyệt đối — **nơi quyết định** | để rỗng, chú thích ở dòng riêng (bẫy §10.27) |
| `deploy/compose.local.yml` | mặc định đúng nhưng bị ghi đè | giữ nguyên, ghi rõ nó không phải nơi quyết định |
| `Makefile` mục `dev-fe` | tiêm `VITE_API_BASE_URL=http://localhost:8080/api/v1` lúc build | đổi **đích chuyển tiếp** (`API_UPSTREAM`, `API_INTERNAL_BASE_URL`) — đọc lúc chạy |

`staging/prod.env.example` cũng gán tuyệt đối. Ở đó nó *tình cờ* trùng origin thật nên chưa hỏng,
nhưng biến một hằng số hạ tầng thành thứ phải build lại mỗi lần đổi tên miền, và sai một ký tự là
cả giao diện chết. Nay để rỗng ở cả hai.

**`make dev-fe` (FE trong Docker, backend native) là chế độ dễ tái phát nhất** — nó thật sự cần
một địa chỉ khác `dev-docker`. Nhưng thứ khác là **đích chuyển tiếp**, không phải origin của
trình duyệt: `API_UPSTREAM=http://host.docker.internal:8080` cho nginx và
`API_INTERNAL_BASE_URL=…/api/v1` cho Next, cả hai đọc lúc chạy nên không phải dựng lại image.
Thêm `extra_hosts: host.docker.internal:host-gateway` để Linux giống Docker Desktop.

**Bài canh mới** (`tepEnvKhongGanDiaChiTuyetDoi`) quét **mọi** tệp trong `deploy/env/`, gồm cả
`local.env` không commit — ở CI nó không tồn tại nên chỉ soi các bản mẫu, ở máy lập trình viên nó
soi đúng tệp đang nuôi `docker compose`. Kiểm chứng ngược 3 phép phá hoại, **cả 3 đỏ**: trả lại
dòng cũ · để trống kèm chú thích cùng dòng (bẫy §10.27) · `dev-fe` tiêm lại biến build.

⚠ **Và bản đầu của chính bài canh đó đỏ oan**: nó khớp trúng một **chú thích trong Makefile mô tả
lại lỗi cũ**, chứa nguyên văn `VITE_API_BASE_URL=http://localhost:8080/api/v1`. Một phép canh
trượt trên tài liệu giải thích chính nó là phép canh **soi văn bản thay vì soi cấu trúc** — đúng
thứ đã bị cấm sau vụ `articleContentCss.test.ts`. Nay bỏ dòng chú thích trước khi khớp.

**Kiểm chứng trên bản chạy thật sau khi sửa**: bundle admin-app và public-web đều **0** tham chiếu
`:18080` · đăng nhập qua cổng 15173 kèm `Origin` → **200 `TWO_FACTOR_ENROLL_REQUIRED`** ·
`POST /public/articles/1/views` → **204** · `GET /public/categories` → **200** · 3 container
`healthy`, `RestartCount 0` · và đường **khác** origin cũ (`OPTIONS` thẳng vào `:18080`) vẫn trả
**403** — tức là bức tường CORS vẫn đứng nguyên, ta chỉ thôi tự đâm vào nó.

---

### §10.30. ⚠⚠ "Đổi mật khẩu bị 403" — việc đã xong từ lượt đầu, hỏng là đường ra (20/8/2026)

Anh Quân thử luồng 2FA và báo `POST /auth/change-password` trả **403**. Nhật ký máy chủ kể đủ:

```
POST /auth/login            → 200
POST /auth/2fa/enroll       → 200
POST /auth/2fa/confirm      → 200
GET  /auth/me               → 200
POST /auth/change-password  → 204   ← thành công
POST /auth/change-password  → 403   ← "header có, cookie thiếu"
POST /auth/refresh          → 403
POST /auth/change-password  → 403   ← "header thiếu, cookie thiếu"
```

CSDL xác nhận lượt đầu đã ăn: `must_change_password = false`, `password_changed_at` đúng giây của
dòng 204, TOTP đã xác nhận. **Backend không hỏng một chỗ nào** — đổi mật khẩu thu hồi mọi phiên và
xoá cookie CSRF là hành vi đã chốt ở §4.1.

> ⭐ **Đọc log theo trình tự, đừng đọc theo mã lỗi.** Đi tìm nguyên nhân của "403 ở
> change-password" là đi tìm một lỗi phân quyền không tồn tại. Dòng đáng chú ý nhất trong cả
> đoạn là dòng **204**, và nó nằm *trước* thứ được báo là lỗi.

**Lỗi thật ở giao diện, và nó nằm giữa hai tệp chứ không nằm trong tệp nào.** `ChangePasswordPage`
gọi `clearTokens()` — hàm này chỉ xoá token trong `apiClient`. Còn `status` và `user` nằm ở
`AuthProvider`, và **guard đọc đúng hai giá trị đó**. Nên lượt `navigate('/dang-nhap')` ngay sau đó
bị `RequireAnonymous` thấy `status === 'authenticated'` + `mustChangePassword === true` và đẩy
**ngược về đúng biểu mẫu vừa gửi**. Người dùng thấy form hiện lại y nguyên, kết luận là thất bại,
bấm gửi lần nữa — và lần này phiên đã chết nên nhận 403.

Đọc riêng `ChangePasswordPage` thì hợp lý; đọc riêng `guards.tsx` cũng hợp lý. Sai nằm ở **chỗ hai
bên gặp nhau**, nên bài kiểm cũng phải cho chúng gặp nhau thật.

**Ba bản sửa:**

1. **`endSession()` ở `AuthProvider`**, dùng chung cho `logout` và đổi mật khẩu — dọn cả ba thứ ở
   *một* chỗ. Trước đó `logout` làm đúng còn đổi mật khẩu làm thiếu, tức là hai nơi phải cùng nhớ
   một thủ tục ba bước. Đây là biến thể của luật *"chỗ nào con người phải nhớ hai nơi thì chỗ đó
   cần một phép kiểm nhớ hộ"* (§10.9-b) — lần này giải bằng cách **bỏ hẳn cái phải nhớ**.
2. **Bỏ `navigate(0)`.** Nó tải lại trang để "dọn sạch bộ nhớ", tức là *che* đúng lỗi trên: lúc
   chạy được thì nó dọn hộ, lúc không thì không ai biết vì sao. Điều hướng tất định đọc ra được là
   đúng hay sai.
3. **Vé CSRF chỉ còn MỘT nguồn: cookie.**

**⚠⚠ Về bản sửa thứ ba, bản đầu của tôi sai và bài kiểm bắt được.** `currentCsrfToken()` viết
`csrfToken ?? readCookie(...)` — bộ nhớ thắng cookie. Tôi sửa thành `readCookie(...) ?? csrfToken`,
tưởng là xong. Bài kiểm đỏ ngay: **đảo thứ tự chỉ chữa lúc hai bên cùng có mà lệch nhau**; lúc máy
chủ **xoá** cookie thì vẫn rơi về bộ nhớ và vẫn gửi một vé đã chết. Lập luận quyết định:

> Máy chủ đối chiếu header với **cookie**. Cookie vắng mặt thì **không giá trị nào** gửi lên đi qua
> được. Bản sao trong bộ nhớ không cứu được lượt gọi nào — nó chỉ đổi thông báo từ *"thiếu vé"*
> thành *"vé không khớp"*, và đẩy người đọc log đi tìm một lỗi đối chiếu không tồn tại.

Nên bỏ hẳn biến `csrfToken` và hàm `setCsrfToken`. Bản sao đó cũng không giải quyết vấn đề nào có
thật: cookie `XSRF-TOKEN` cố ý **không** httpOnly, `Path=/`, cùng origin — luôn đọc được bằng JS;
phản hồi đăng nhập vừa đặt cookie vừa trả vé trong thân, và trình duyệt xử lý `Set-Cookie` xong mới
giải lời hứa. **Giữ hai nguồn cho một sự thật chỉ tạo cơ hội để chúng lệch nhau.**

**Ba bài kiểm mới, cả ba kiểm chứng ngược đều đỏ đúng chỗ:**

| Bài | Ở đâu | Chứng minh gì |
|---|---|---|
| `changePasswordFlow.test.tsx` | admin-app | dựng `AuthProvider` + guard + router **thật**; đổi xong phải tới trang đăng nhập **và** biểu mẫu không hiện lại |
| `csrfToken.test.ts` | admin-app | cookie là nguồn duy nhất; cookie bị xoá thì không gửi vé nào |
| `ChangePasswordHttpTest` | app | qua HTTP thật: 204 · xoá cả hai cookie · access token đang cầm **chết ngay** · mật khẩu mới dùng được |

⭐ `changePasswordFlow.test.tsx` là **bài kiểm component đầu tiên** của admin-app —
`@testing-library/react` nằm trong `package.json` từ WS-8 mà chưa lần nào được dùng. Thêm một cơ
chế "dựng ra mà chưa ai đi qua" nay đã có người đi.

⚠ `ChangePasswordHttpTest` đi qua HTTP chứ không gọi `PasswordChangeService`: **hai trong ba cam
kết không tồn tại ở tầng service** (cookie nằm ở controller, token chết nằm ở chuỗi filter). Gọi
thẳng service là kiểm đúng phần không hỏng — đây là phần trả trước của nợ **#65**.

⚠ Hai chi tiết nhỏ, cả hai đều là "công cụ im lặng": `@testing-library/react` **chỉ tự dọn DOM khi
vitest bật `globals`**, mà admin-app thì không — thiếu `afterEach(cleanup)` là bài sau đỏ với
*"Found multiple elements"*, một lời báo lỗi chẳng liên quan gì tới thứ đang kiểm. Và luật ESLint
cấm import `axios` ngoài `apiClient` **không phân biệt import kiểu** — giữ nguyên luật, khai kiểu
tại chỗ, vì nới ra cho "chỉ là kiểu thôi" là mở đúng cái khe lần sau có người dựng instance riêng
chui qua.

---

### §10.31. ⚠⚠ Rà soát toàn tuyến sau khi trình duyệt chạy ổn định — 4 lỗi thật, 2 nghiêm trọng (21/8/2026)

Anh Quân xác nhận giao diện đã chạy trên trình duyệt và yêu cầu **rà lại toàn bộ luồng đã dựng**,
bảo đảm không còn lỗi cùng họ. Đây là bản ghi kết quả.

**Cách rà, và vì sao chọn cách đó.** Ba bài học gần nhất (§10.29, §10.29-a, §10.30) đều cùng một
hình: *cơ chế xanh mà chưa ai đi qua đúng đường production đi*. Nên lần này không đọc mã để đoán, mà
**gọi thật 99 endpoint qua cổng 15173 — tức qua nginx của admin-app, kèm header `Origin`** — đúng
cách trình duyệt gọi, bốn lượt:

| Lượt | Tư cách | Kỳ vọng |
|---|---|---|
| S1 | không token | mọi endpoint không công khai chặn lại |
| S2 | token **không có quyền nào** | mọi `@RequirePermission` trả 403 `AUTH-3001` |
| S3 | token đủ quyền + vé CSRF | đường đi phải thông |
| S4 | token đủ quyền, **thiếu header CSRF** | mọi lượt ghi bị chặn |

Kèm một phép đối chiếu tĩnh: **99 endpoint backend ↔ mọi lượt gọi API trong mã FE**.

#### Kết quả phần lành

- **99/99 endpoint đều có khai báo bảo mật tường minh** — 75 `@RequirePermission`, 14
  `@PublicEndpoint`, 10 `@AuthenticatedEndpoint`. Deny-by-default (T5.10) đứng vững.
- **FE ↔ BE khớp tuyệt đối**: không lượt gọi nào của FE trỏ vào endpoint không tồn tại, và ngược lại.
- **CSRF phủ đúng**: S4 chặn toàn bộ lượt ghi, ngoại lệ duy nhất là `POST /public/articles/{slug}/views`
  — đúng §10.19.
- **Cookie đúng cờ**: `refresh_token` `HttpOnly` + `SameSite=Strict` + `Path=/api/v1/auth`;
  `XSRF-TOKEN` cố ý không `HttpOnly` (double-submit cần JS đọc được).
- Cổng công khai trả 200 ở trang chủ, danh mục, tìm kiếm, sitemap, health.

> ⚠ Một chi tiết của **bộ quét**, không phải của hệ: lượt S2 gọi trúng `POST /auth/logout` giữa
> chừng nên các endpoint sau đó nhận 401 thay vì 403. Kiểm lại riêng thì tầng 2 trả đúng
> `AUTH-3001`. Ghi ra vì nó cũng là một bài học: **bộ quét tuần tự mà chứa một lời gọi làm đổi trạng
> thái phiên thì mọi kết quả sau đó không còn đọc được** — và rất dễ báo động giả.

#### Lỗi 1 ⛔⛔ — XSS lưu trữ trên cổng công khai, qua **hai** đường ghi cấu hình

`site.footer.company-info` và `site.footer.map-embed` là hai giá trị duy nhất mà cổng dựng bằng
`dangerouslySetInnerHTML`. Việc khử trùng đặt ở `SiteConfigService.update()` — tức ở **một** trong
**ba** đường ghi vào bảng `settings`. Hai đường còn lại (`PUT /api/v1/settings/{key}` của màn hình
cấu hình hệ thống, và `POST /api/v1/settings/import`) ghi thẳng chuỗi thô.

Đo thật trên hệ đang chạy: gửi `<img src=x onerror="alert(document.cookie)"><script>…</script>` qua
cả hai đường → **cả hai trả 200**, CSDL lưu **nguyên văn**, `GET /api/v1/public/site-config` trả lại
**nguyên văn**. Đoạn mã đó chạy trong trình duyệt của **mọi người dân vào tra cứu**.

**Chốt: luật khử trùng chuyển từ *nơi gọi* sang *dữ liệu*.** Cột `value_type` nhận thêm `HTML` và
`HTML_EMBED`; `SettingService` khử trùng theo kiểu tại một điểm ghi chung mà cả ba đường đều đi qua.
Đường ghi viết sau này cũng bị ràng buộc mà không ai phải nhớ — đúng cách `AttachmentService` tự đưa
SVG qua `SvgSanitizer` thay vì tin nơi gọi (§10.14).

⚠ **Lỗi thứ hai lộ ra ngay cạnh**: `importConfiguration` cũng **không phát `SettingChangedEvent`**,
nên nhập cấu hình xong thì cổng còn hiện giá trị cũ tới hết TTL 10 phút — đúng thứ §10.13 dựng ra để
chống, vẫn sống trên đường thứ ba. Chú thích tại chỗ ghi *"đây là nơi DUY NHẤT ghi bảng settings"*:
đúng ở mức **lớp**, sai ở mức **phương thức**, và cái sai đó không có bài kiểm nào chạm tới.

#### Lỗi 2 ⛔⛔ — Toàn bộ màn hình quản trị nội dung trả 500, từ WS-13 tới nay

`GET /api/v1/cms/articles` và `GET /api/v1/cms/articles/{id}` trả **500 `SYS-0001` cho mọi lượt
gọi**. Tức là **danh sách bài viết và màn hình sửa bài** — trọng tâm của Phase 1 — chưa từng dùng
được. Nguyên nhân: `ArticleController` ánh xạ entity sang DTO **trong controller**, sau khi phương
thức `@Transactional` đã kết thúc và `Session` đã đóng (`open-in-view: false`), còn
`ArticleSummary.of`/`ArticleDetail.of` đọc `getCategories()` — quan hệ lười →
`LazyInitializationException`.

Không có lượt gọi nào thoát: `categoryPublicIds` mang `@NotEmpty`, nên **mọi** bài viết đều có danh
mục.

⚠⚠ **Vì sao 391 bài kiểm xanh không thấy gì**: `ArticleLifecycleTest` gọi thẳng service, nên phép
khẳng định chạy *bên trong* giao dịch — nơi nạp lười vẫn hoạt động bình thường. **Nợ #65 ("bài kiểm
CMS chưa đi qua HTTP") không phải mục cho đẹp hồ sơ — nó đang che một sự cố toàn phần.** Đây là lần
thứ hai trong dự án một dòng nợ hoá ra là nắp đậy, không phải việc để dành.

Sửa: `ArticleService` nạp sẵn quan hệ trước khi entity rời khỏi giao dịch, `@BatchSize(50)` giữ cho
lượt nạp của cả trang gom về một truy vấn. Bài canh là `ArticleHttpTest` — **đi qua HTTP, vì đó là
đường duy nhất tái hiện được**.

#### Lỗi 3 ⚠ — Image admin-app không đặt **một** header bảo mật nào

`curl -I http://localhost:15173/` trả về đúng `Server`, `Date`, `Content-Type`, `ETag`,
`Cache-Control`. Không `X-Frame-Options`, không CSP, không `X-Content-Type-Options` — trong khi
public-web đặt sẵn ba cái. Hai tầng phục vụ của cùng một hệ thống có hai mức bảo vệ khác hẳn nhau mà
không ai chọn điều đó.

⚠⚠ **Cái bẫy nằm ở chính cách sửa**: trong nginx, `add_header` **không cộng dồn qua các cấp** — khối
`location` có `add_header` riêng sẽ vứt bỏ *toàn bộ* header kế thừa từ khối `server`. Cấu hình này
có `add_header Cache-Control` ở cả `/assets/` lẫn `/`, đúng hai khối phục vụ mọi thứ người dùng tải
về. Khai một lần ở cấp `server` rồi kiểm bằng `curl /` sẽ thấy header **biến mất**, và rất dễ kết
luận nhầm là "cấu hình rồi mà không chạy". Nên header để riêng một tệp và `include` lại ở từng khối;
`NginxSecurityHeadersTest` **soi cấu trúc khối `location`**, không tìm chuỗi trong tệp.

**CSP — hai lựa chọn có chủ đích, cả hai đã đo:**

- `style-src` **phải** có `'unsafe-inline'`. AntD 5 dùng cssinjs, chèn `<style data-css-hash=…>` lúc
  chạy (đã kiểm trong bundle đã dựng). Với `style-src 'self'` thì giao diện quản trị hiện ra **không
  còn định dạng nào**. Đường thoát duy nhất là `StyleProvider` + nonce theo từng request, mà bundle
  Vite là tĩnh do nginx phục vụ nên không có chỗ sinh nonce. Đây là **cái giá của việc chọn AntD**,
  ghi ra để WS-11 không siết rồi mới phát hiện lúc đã lên staging.
- `script-src 'self'` thì **an toàn** — `index.html` của bản dựng có đúng một thẻ script và nó mang
  `src`, không có script nội tuyến. Đây mới là lớp chặn XSS thật sự, và bài kiểm khẳng định
  `'unsafe-inline'` không được lọt sang đó.

HSTS cố ý **không** đặt ở tầng này: nó thuộc về nơi kết thúc TLS (nginx chung, T11.5). Đặt ở đây thì
local chạy HTTP nên trình duyệt bỏ qua, và nó tạo cảm giác đã có lớp bảo vệ mà thật ra chưa.

⛔ **Chưa làm, và nói rõ là chưa**: public-web **không** có CSP. Next chèn script nội tuyến để
hydrate (`self.__next_f`), nên CSP ở đó cần nonce qua middleware — một việc riêng, ghi thành nợ chứ
không vá vội bằng `'unsafe-inline'` (vá thế thì có CSP mà không có tác dụng).

#### Lỗi 4 ⚠ — Công tắc chết và giá trị ghi cứng, hai đầu của cùng một chuyện

Đối chiếu **85 tham số `settings`** với mã nguồn (Java lẫn TypeScript, tính cả khoá ghép từ tiền tố):

- **3 khoá nhóm CMS** bày trên giao diện mà không nơi nào đọc. Hai khoá đầu **không phải sơ suất** —
  `ViewCountService` và `ScheduledPublishScanner` cố ý ghi cứng chu kỳ, và tài liệu của chúng nói rõ
  vì sao (`@Scheduled` chốt chu kỳ lúc dựng bean). Quyết định ở tầng mã là đúng; thứ thiếu là **bước
  gỡ dòng dữ liệu tương ứng**. Khoá thứ ba nặng hơn: `cms.article.view-count-window-minutes` mô tả
  một tính năng **không tồn tại** — không có phép khử trùng lặp lượt xem nào. → gỡ cả ba.
- **5 khoá `company.*`** không ai đọc, ba trong năm để trống — trong khi `SiteFooter.tsx` **ghi
  cứng** địa chỉ trụ sở, điện thoại, fax, email và số đường dây nóng (số hotline xuất hiện *hai
  lần*). Đây là cổng của một doanh nghiệp nhà nước, và đó là số người dân gọi khi có sự cố công
  trình: đổi nó lẽ ra là một ô nhập, không phải sửa mã rồi dựng lại image. → nối chân trang vào
  `company.*`, thêm `company.fax` / `company.hotline` / `company.working-hours`, điền giá trị đúng
  bằng chuỗi đang ghi cứng nên **cổng hiển thị không đổi gì**.

⚠ `company.hotline` tách riêng khỏi `company.phone`: một bên là tổng đài giờ hành chính, một bên là
trực ban 24/7 phòng chống thiên tai. Gộp làm một thì tới mùa lũ sẽ có người sửa số này và vô tình
đổi luôn số kia.

**Ranh giới quyền giữ nguyên**: `SiteConfigService` **đọc** nhóm `COMPANY` nhưng chỉ **ghi** được
nhóm `SITE`. Tên pháp nhân và số đường dây nóng không phải lựa chọn trình bày, và người có quyền sửa
giao diện cổng không đương nhiên có quyền sửa chúng.

#### Điều rút ra

Cả bốn lỗi đều **không** thuộc loại "viết sai logic". Chúng là cùng một hình dạng, lặp lại lần thứ
tư trong dự án: **một bảo đảm được phát biểu ở một chỗ, trong khi đường chạy thật đi qua chỗ khác.**
Khử trùng nằm ở một trong ba đường ghi; ánh xạ DTO nằm ngoài giao dịch mà bài kiểm lại nằm trong;
header khai ở cấp `server` mà request đi qua `location`; giá trị thật nằm trong mã còn công tắc nằm
trong CSDL.

⛔ **Luật bổ sung, cùng họ với "canh giá trị đã giải" (§10.29-a): khi một bảo đảm phải đúng ở nhiều
đường vào, hãy đặt nó ở chỗ *dữ liệu đi qua*, đừng đặt ở *nơi gọi* — và nếu không đặt được thì phải
có một phép kiểm đếm đủ các đường vào.**

---

### §10.32. WS-17 — bảng nghiệp vụ đầu tiên thuộc phạm vi đơn vị (21/8/2026)

Danh mục công trình (CN-02.1) là nơi **ba cơ chế của Phase 0 lần đầu chạy trên dữ liệu thật**: phân
quyền tầng 3, cửa SPI thông báo, và bộ ghi nhật ký kiểm toán trên một entity nghiệp vụ có thông số
kỹ thuật tách bảng.

#### Bằng chứng tầng 3 đã sống — trả nợ #57

Dòng log lúc khởi động đổi từ *"Chưa có entity nào thuộc phạm vi đơn vị — bỏ qua lọc tầng 3 (Phase 0)"*
thành *"Bộ lọc phạm vi đơn vị đã sẵn sàng"*. Đo trên `make dev-docker` sau khi migration 1026 chạy.

`ConstructionScopeTest` kiểm đủ ba nhánh mà mục "Kiểm chứng" của WS-17 đòi, cộng hai nhánh nữa mà
`ScopeFilterEndToEndTest` không có vì `ScopedRecord` không có đường ghi: **sửa, đổi vòng đời và xoá
một hồ sơ ngoài phạm vi cũng bị chặn**. Bộ lọc và `ScopeGuard` là hai cơ chế khác nhau — bộ lọc làm
bản ghi "không có trong kết quả", còn việc hàm ghi có tra qua `ScopeGuard` hay không là lựa chọn của
người viết service. Quên một chỗ là sửa được hồ sơ của Xí nghiệp khác.

⭐ **Kiểm chứng ngược đã chạy**: gỡ `@Filter` khỏi `Construction` → **6/8 bài đỏ** cộng luật ArchUnit
`everyScopedEntityCarriesTheFilter` đỏ, chỉ đích danh lớp thiếu annotation. Khôi phục → xanh lại.

#### Thông số kỹ thuật là **bảng phụ**, không phải entity riêng

Trạm bơm có 9 thông số, cống có 8, kênh/đê có 7 — ba tập không giao nhau. Nhồi chung vào
`constructions` thì mỗi hồ sơ mang hơn hai chục cột rỗng và không gì ngăn được một cái cống có "số
máy bơm". Nhưng tách thành **entity** riêng thì mỗi lần sửa thông số là một dòng nhật ký mang loại
đối tượng khác, và "nhật ký thay đổi hồ sơ công trình" (CN-02.7) **bỏ sót đúng phần kỹ thuật** —
thứ đáng theo dõi nhất.

`@SecondaryTable` + `@SecondaryRow(optional = true)` giữ được cả hai: dữ liệu tách bảng, nhưng với
Hibernate và với bộ ghi nhật ký thì đây vẫn là *một* hồ sơ. Đổi loại công trình thì service **xoá
sạch thông số của loại cũ** — bỏ bước đó thì `pump_station_specs` còn một dòng mồ côi, biểu mẫu
không hiện nữa nhưng báo cáo "tổng công suất trạm bơm" vẫn cộng vào.

#### Giá trị dẫn xuất đặt ở CSDL khi có thể

Ba cột sinh, cả ba **không có đường ghi nào** nên không thể lệch khỏi dữ liệu sinh ra chúng:

| Cột | Công thức | Vì sao không tính ở Java |
|---|---|---|
| `geom` | `ST_SetSRID(ST_MakePoint(lng, lat), 4326)` | Lưu song song với lat/lng là hẹn trước một lần lệch; marker sai vị trí không có triệu chứng nào |
| `chainage_m` | tách từ `K<km>+<m>` | Dùng để sắp xếp dọc tuyến sông; viết tay hai giá trị cho một sự thật thì sớm muộn chúng khác nhau |
| `total_flow_m3s` | `pump_count × flow_per_pump` | CN-02.1 ghi "auto". Quy tắc 3 nói tính ở BE — ở đây còn chặt hơn, FE không có đường nào tính ra số khác |

Đo trên CSDL thật: `POINT(105.78 20.98)` SRID 4326 · `chainage_m = 18100` cho `K18+100` ·
`totalFlowM3s = 4.500` cho 3 máy × 1,5 m³/s (qua HTTP).

Riêng **trạng thái vận hành** không đặt được ở CSDL vì chuỗi suy ra của nó cần dữ liệu của WS-18 và
Phase 2. Thay vào đó: `ConstructionStatusService` là **nơi duy nhất** ghi, entity **không có setter
công khai** cho cột đó (có bài kiểm soi cấu trúc lớp), và endpoint trả `OPS-3001` khi client gửi
kèm. WS-18/WS-19 thêm luật vào *đúng hàm ấy*, không mở đường ghi mới.

⚠ **Vòng đời tách khỏi trạng thái vận hành.** `lifecycle_state` do con người quyết (đang hoạt động /
ngừng mùa vụ / đã thanh lý), `operational_status` do máy suy ra. Gộp làm một thì hoặc một sự cố vừa
đóng sẽ "hồi sinh" một công trình đã thanh lý, hoặc người dùng phải sửa tay một cột lẽ ra do máy
tính. Đổi vòng đời đi bằng endpoint riêng **có lý do bắt buộc** — không lẫn vào một lượt sửa địa chỉ.

#### ⚠⚠ `now()` của PostgreSQL là thời điểm **bắt đầu giao dịch** — nhật ký thay đổi trả về rỗng

Bài kiểm qua HTTP bắt lỗi này ở lượt chạy đầu. `ConstructionChangeLogService` lấy `createdAt` của
công trình làm cận dưới cho truy vấn `audit_logs` (đúng ý đồ: bảng phân mảnh theo tháng, không có
cận dưới thì quét cả 60 partition). Nhưng `audit_logs.occurred_at` mặc định là `now()`, mà `now()`
trả **transaction timestamp**, còn `createdAt` do Spring gán lúc flush — sau đó vài mili giây. Kết
quả: dòng nhật ký của chính lượt tạo nằm ngay *dưới* mốc và bị loại. Triệu chứng phía người dùng là
tab "Nhật ký thay đổi" **trống trơn, không lỗi nào**, và người ta sẽ đi tìm nguyên nhân ở bộ ghi
nhật ký chứ không ở câu truy vấn.

Chữa bằng cách lùi mốc về **đầu tháng** chứa `createdAt`: vì partition chia theo tháng nên **không
quét thêm partition nào**, mà xoá hẳn cả lớp lỗi do lệch đồng hồ giữa tiến trình ứng dụng và CSDL.

#### ⚠ Hai cột `valid_from` / `valid_until` đã "chết" từ WS-2

CN-02.3 đòi tài liệu công trình có *ngày lập* và *ngày hết hiệu lực*. Hai cột ấy có trong lược đồ từ
WS-2, entity có setter — và **không dòng mã nào gọi**: `AttachmentUploadCommand` không mang chúng,
`AttachmentRef` chỉ lộ `validUntil`. Cùng họ với `limits.upload.max-mb.*` (WS-12) và `company.*`
(rà soát 21/8): cột có sẵn nên ai đọc lược đồ cũng tưởng nó đang chạy.

Mở `AttachmentPort.setValidity(...)` thay vì nhét thêm tham số vào lệnh tải lên — ngày lập là **siêu
dữ liệu nghiệp vụ**, sửa được sau khi tệp đã vào kho, còn nội dung tệp thì không. (Một hồ sơ hoàn
công lập năm 2018 vẫn được số hoá hôm nay, nên "ngày lập" không suy ra được từ ngày tải lên.)

#### ⛔ Nhập tệp: không thêm Apache POI

Tệp nhập danh mục là một **bảng phẳng toàn chữ và số** — không công thức, không ô ngày, không định
dạng. Đọc được chừng đó thì XLSX chỉ là ZIP chứa XML, và JDK có sẵn cả `java.util.zip` lẫn StAX.
POI kéo theo `xmlbeans`, `commons-compress`, `commons-io` và là một trong những nguồn CVE Java
thường xuyên nhất — mà dự án đã tự đặt luật *"mỗi thành phần phải tự chứng minh nó đáng nuôi"* và đã
**trả lại** một phiên bản MinIO cùng một module Testcontainers vì đúng lý do đó. Khi nào cần **xuất**
Excel có định dạng (CN-02.10, Phase 3) thì POI mới đáng.

Giới hạn đã ghi thẳng trong mã: chỉ sheet đầu tiên · đọc giá trị đã lưu của ô công thức, không tính
lại · **không** đổi số sê-ri ngày của Excel (bảng nhập công trình không có cột ngày nào; cột ngày
đầu tiên xuất hiện thì phải xử lý ở đó, đừng để nơi gọi tự đoán).

⚠⚠ **Dấu chấm là chỗ nguy hiểm nhất của cả lượt nhập.** Tiếng Việt dùng "." ngăn hàng nghìn, còn toạ
độ GPS viết `21.023456` với "." là dấu thập phân. Quy tắc "bỏ hết dấu chấm" biến vĩ độ 21,023456
thành **21023456** — một điểm giữa đại dương. CHECK của CSDL bắt được khi vượt [-90, 90], nhưng
**không bắt được sai số nhỏ hơn**, và một công trình lệch vài trăm mét trên bản đồ điều hành thì
không ai phát hiện bằng mắt. Nên phân biệt bằng *hình dạng chuỗi*, không đoán theo ngôn ngữ.

⭐ **Chạy khô và chạy thật đi CÙNG một đường** (`lapKeHoach`). Viết hai bộ luật thì bản xem trước sẽ
dần khác bản chạy thật, người dùng nhận "0 lỗi" rồi vẫn hỏng ở lượt nhập — mất niềm tin không gỡ lại
được. Và **một dòng lỗi thì không dòng nào được ghi**: nhập một nửa rồi dừng là trạng thái tệ nhất,
người dùng không biết đã vào tới đâu, sửa tệp rồi nhập lại thì phần đầu vào hai lần.

⚠ Thêm một chốt nhỏ nhưng đáng: tệp nhị phân (có byte `0x00`) bị từ chối bằng `OPS-2015` ngay. Không
có bước đó thì một tệp `.doc` tải nhầm vẫn được "đọc" thành một dòng ký tự rác và người dùng nhận
thông báo *"tệp thiếu cột bắt buộc"* — câu đó dẫn họ đi sửa tiêu đề của một tệp không hề là bảng tính.

#### Cụm công trình chỉ là **cách nhóm** (G15)

⛔ `cluster_id` không được xuất hiện trong bất kỳ truy vấn phân quyền nào. Phạm vi đi bằng
`org_unit_id`, và chỉ bằng nó. Nếu một ngày cụm cần mang ý nghĩa phân quyền thì đường đúng là thêm
một cấp vào `org_units` — vì có hai nguồn phạm vi thì sớm muộn chúng lệch nhau, và **bên lỏng hơn sẽ
thắng mà không ai biết**.

Cụm dùng lại quyền `ops:construction:*` thay vì thêm quyền mới: ma trận §6 đã được Công ty duyệt và
có 334 dòng đang được `RbacMatrixTest` đối chiếu trên CSDL thật. Thêm một quyền ngoài ma trận sẽ tạo
ra một ô mà **không vai trò nào được gán** — tức một chức năng không ai dùng được.

---

### §10.33. WS-23 — nền biểu đồ và dashboard điều hành (21/8/2026)

Hạng mục **thêm vào Phase 1** (chốt 20/8): `implement.md` vốn xếp dashboard vào Phase 3, nhưng WS-17 vừa làm cho số liệu công trình thành **số thật**, nên đây là lúc dựng được một màn hình demo mà không phải bịa một con số nào.

#### 1. ⛔ Ô chưa có nguồn nói thẳng là chưa có — ép ở tầng kiểu, không ở lời dặn

CN-02.5 liệt kê sáu nhóm KPI, **bốn nhóm chưa có dữ liệu**: cảnh báo thuỷ văn và điểm đo mất tín hiệu thuộc MOD-03 (Phase 2), công việc bảo trì và sự cố chưa xử lý thuộc WS-18.

Cách làm sai mà dễ rơi vào nhất là trả `0`. **Số 0 là một câu khẳng định** — "đã đo và bằng không". Trên màn hình treo tường phòng trực, ô *"Sự cố chưa xử lý: 0"* nói rằng không có sự cố nào, và người trực ca sẽ tin nó, trong khi hệ thống chưa hề biết gì.

Nên `DashboardService.Kpi` trả `value = null` kèm **lý do** và **mốc sẽ có** (`"WS-18 (CN-02.2)"`). Ràng buộc nằm ở **hàm dựng của record**:

```java
public Kpi {
    if (value == null && (unavailableReason == null || unavailableReason.isBlank())) {
        throw new IllegalArgumentException("KPI '%s' không có số thì bắt buộc phải nói lý do…");
    }
}
```

Đặt ở đây chứ không ở bài kiểm, vì bài kiểm chỉ phủ những ô **đã tồn tại lúc viết nó** — ô KPI thứ mười một mà WS-18 thêm vào cũng phải đi qua đúng ràng buộc đó mà không cần ai nhớ.

⚠⚠ **`@JsonInclude(ALWAYS)` trên record này là bắt buộc, và nó đè cấu hình `NON_NULL` chung.** Bỏ hẳn khoá `value` khỏi JSON thì phía nhận đọc ra `undefined` — không phân biệt được với "API đổi tên trường" hay "bản cũ chưa có trường này". Cả thiết kế dựa trên việc **nói rõ** rằng không có số; để nó im lặng biến mất là mâu thuẫn với chính điều đang cố diễn đạt. Đây là ngoại lệ duy nhất trong dự án.

#### 2. Một bảng màu, không phải hai — và cách canh điều đó

T23.1 đòi theme ECharts sinh từ `design-tokens`. Lý do không phải "cho gọn": năm màu trạng thái **mang nghĩa nghiệp vụ** (đỏ = sự cố đang mở, xám = mất tín hiệu). Hai bảng màu thì badge trên bảng và lát bánh trên biểu đồ lệch nhau, và **không ai coi đó là lỗi để đi sửa** — nhìn riêng từng màn hình thì cả hai đều "trông ổn".

Phép canh thật nằm ở `chartOptions.test.ts`: lát *"Sự cố"* phải đúng bằng `statusColors.danger` mà `StatusBadge` dùng. Kiểm chứng ngược bằng cách viết một mã màu tại chỗ → đỏ.

⛔ **Mã lạ không rơi về màu `normal`.** `mauCua` trả `undefined` để ECharts dùng dãy màu của theme. Rơi về `normal` nghĩa là một trạng thái hệ thống chưa biết sẽ hiện màu xanh — một khẳng định "ổn" về thứ chưa ai xác nhận.

#### 3. ⚠⚠ Bố cục co giãn tính bằng JS, vì CSS `auto-fit` không kiểm được

`repeat(auto-fit, minmax(300px, 1fr))` làm đúng việc và không bao giờ tràn — nhưng nó chỉ được tính bởi **bộ dựng bố cục của trình duyệt**, mà jsdom không có bộ dựng bố cục. Yêu cầu T23.11 (*"ba bề rộng 3840/1920/1366, khẳng định cả hai vế: không tràn ngang và không mất khối"*) sẽ không có cách nào kiểm ở CI: một bài kiểm `render()` rồi đọc `style` chỉ chứng minh chuỗi CSS được viết ra.

Đưa quyết định về hàm thuần `soCot(beRong, rongToiThieu, tranCot)` thì bài kiểm chạy **đúng đoạn mã production chạy**, và quét được cả dải 320→4096 px chứ không chỉ ba điểm — lỗi bố cục hay nằm ở khe hẹp ngay dưới một điểm ngắt.

**Có trần số cột, kể cả ở 4K**, và không phải để tiết kiệm chỗ: màn hình 85" được đọc từ **4–6 m**, mười hai ô một hàng thì mỗi ô hẹp tới mức chữ không còn đọc được ở khoảng cách đó — thêm cột lại làm **mất** thông tin.

#### 4. ⚠⚠ Lỗi thật do bài kiểm bắt: lưới luôn một cột vì ref gắn sau khi effect đã chạy

Bản đầu của `useElementWidth` dùng `useRef` + `useEffect([])`. Bài kiểm bố cục ở bốn bề rộng bắt được: lưới luôn ra `repeat(1, …)`.

Nguyên nhân: trang hiện khung xương trong lúc chờ dữ liệu, nên ở lượt render đầu — **đúng lượt mà effect chạy** — thẻ mang ref chưa có trong cây DOM, `ref.current` là `null`, effect thoát sớm. Khi dữ liệu về và thẻ được gắn vào thì không có gì gọi lại effect: danh sách phụ thuộc rỗng nghĩa là "chạy đúng một lần", và lần đó đã trôi qua.

Hậu quả thật, không chỉ trong bài kiểm: **dashboard hiện một cột trên mọi màn hình** cho tới khi người dùng đổi kích thước cửa sổ — mà trên TV treo tường thì không bao giờ có ai đổi kích thước cả. Chữa bằng **ref dạng hàm**: React gọi nó đúng vào lúc nút gắn vào và gỡ ra, thay vì một thời điểm mà ta *đoán* là nút đã có ở đó.

⭐ Đáng chú ý là **bản đầu của bài kiểm cũng không bắt được**: nó khớp `repeat([1-5], …)` bằng biểu thức chính quy nên `repeat(1, …)` vẫn xanh. Siết lại thành con số chính xác cho từng bề rộng mới lộ ra. Cùng họ với bài học `articleContentCss.test.ts` — **canh cấu trúc, và canh cho chặt**.

Bộ bề rộng cũng phải chọn có chủ ý: ở 3840/1920/1366 thì **trần cột luôn là thứ quyết định** (cả ba đều ra 5/3), nên thêm **900 px** — bề rộng duy nhất trong bộ mà con số phụ thuộc thật vào phép đo.

#### 5. Bản đồ: URL tile ở `settings`, nhưng CSP không tự đi theo

`architecture-review.md` §3 mục 9 chốt "OSM mặc định, Google Maps optional, để config switch được" → 6 khoá `ops.map.*` nhóm `OPERATION` (migration `V…1027`), đọc lúc chạy.

⚠⚠ **Nhưng chỉ thị `img-src` nằm ở nginx của ảnh admin-app.** Host tile không nằm trong danh sách đó thì trình duyệt chặn **từng ô ảnh**, và triệu chứng là **bản đồ xám trơn có marker nổi lên trên** — không một lỗi nào ở tầng ứng dụng, chỉ vài dòng trong console mà không ai mở. `NginxSecurityHeadersTest.hostTileBanDoNamTrongCsp` đọc URL seed từ chính migration và đối chiếu với CSP trong Dockerfile; kiểm chứng ngược bằng cách gỡ host khỏi CSP → đỏ đúng chỗ.

⛔ **Không nới `img-src` thành `https:` trần** — nới thế thì mọi tên miền trở thành nguồn ảnh hợp lệ, và một thẻ `<img>` chèn được vào nội dung sẽ gửi thông tin ra ngoài bằng chính đường dẫn ảnh. Bài kiểm khẳng định cả vế này.

⛔ **Marker vẽ bằng `divIcon` (CSS), không dùng ảnh biểu tượng mặc định của Leaflet**: ảnh PNG mặc định nạp theo đường dẫn tương đối tính từ tệp CSS nên vỡ trong bản dựng có băm tên tệp (marker biến mất mà bản đồ vẫn chạy); màu marker phải theo trạng thái mà PNG thì không đổi màu được; và vẽ bằng CSS thì **không cần tới `img-src` chút nào**.

#### 6. Wall mode: một cây component, chỉ đổi theme và cỡ chữ

Thiết bị đã chốt (B8) là TV 85" 4K, kèm khả năng có máy chiếu 2K/Full-HD. Cách làm sai là thiết kế riêng cho 3840×2160 rồi thêm một bản "cho laptop": hai bộ bố cục thì mọi thay đổi phải nhớ làm hai lần, và **bản bị quên luôn là bản không ai mở hằng ngày** — tức là bản treo trên tường phòng trực.

Nên `?mode=wall` chỉ đổi theme (`echartsWallTheme` **sinh từ** `echartsTheme`, không chép lại) và cỡ chữ (`clamp()` + `vw`). Số cột vẫn do `boCucTheoBeRong` quyết. Bài kiểm khẳng định wall dựng **đúng chừng ấy khối** như chế độ thường.

**Năm màu trạng thái giữ nguyên ở chế độ tối** — chỉ nền và chữ đảo. Đổi sắc độ theo nền là tạo ra hai bảng nghĩa cho cùng một hệ thống, và người trực đọc màn hình tường rồi mở máy tính tra tiếp sẽ thấy hai màu khác nhau cho cùng một công trình.

**Auto-rotate là cuộn, không phải đổi trang.** Thay hẳn nội dung theo chu kỳ nghĩa là **có những phút không nhìn thấy được số sự cố** — trong khi đó chính là con số người ta treo màn hình lên để nhìn. Khối KPI luôn nằm trên, chỉ phần dưới cuộn.

#### 7. ⚠⚠ Hạn mức đăng nhập là ngân sách **dùng chung** giữa mọi lớp kiểm thử HTTP

`DashboardHttpTest` xanh khi chạy riêng và **8/10 bài đỏ khi chạy cả bộ**, với `SYS-0002 · 429`. Nguyên nhân không nằm ở dashboard: hạn mức đăng nhập là **30 lượt / 15 phút theo IP**, mọi bài kiểm HTTP đi từ `127.0.0.1`, và bộ đếm là Caffeine trong tiến trình nên nó dùng chung cho **toàn bộ lượt chạy**.

⛔ Cách chữa **sai** là nới hạn mức ở hồ sơ kiểm thử — làm thế thì một cơ chế bảo mật thật không còn được chạy qua ở CI. Cách đúng là dùng ít vé hơn: đăng nhập ở `@BeforeAll` (`@TestInstance(PER_CLASS)`), 20 lượt còn 2.

📌 **Luật cho lớp kiểm thử HTTP thêm sau**: đăng nhập một lần cho cả lớp. Không thì nó làm đỏ một lớp *khác*, và người đọc log sẽ đi tìm lỗi ở đúng chỗ không có lỗi nào.

#### 8. Hai thứ cố ý **chưa** làm, ghi ra thay vì giấu

- **Bấm vào cột để mở danh sách đã lọc (T23.8)** — màn hình danh sách công trình thuộc **WS-21**, chưa dựng. Nối sẵn một liên kết trỏ tới route không tồn tại thì người bấm nhận trang 404: **một bên là chức năng chưa có, bên kia trông như chức năng có mà hỏng**. Cùng lý do, popup marker chưa có nút "Xem chi tiết" (M2.10 có yêu cầu). → nợ #71, nhận ở WS-21.
- **`optionDuong` (biểu đồ đường) chưa có nơi gọi** — chuỗi thời gian đầu tiên của hệ thống là mực nước 24 giờ (Phase 2). Giữ lại vì nó là hàm thuần có bài kiểm riêng, còn phần rủi ro thật (dựng thực thể ECharts, đổi kích thước, huỷ) nằm ở `BaseChart` và đã có ba loại biểu đồ khác đi qua. ⛔ Phase 2 đến mà vẫn không ai gọi thì **xoá**, không phải giữ.

#### 9. Số đo thật

Bó mã của route dashboard: **727 kB (239 kB nén gzip)** — tách chunk riêng nhờ nạp theo nhu cầu, nên trang đăng nhập không gánh. Nạp ECharts **chọn lọc** (`echarts/core` + 4 loại biểu đồ + 5 component + bộ vẽ Canvas); nạp trọn gói thì phần này lớn hơn nhiều lần. Bộ vẽ **Canvas chứ không SVG**: wall mode 4K vẽ lại mỗi chu kỳ và chạy liên tục nhiều giờ, Canvas giữ số nút DOM không đổi.

---

### §10.34. WS-18 — lịch sử sửa chữa và sự cố; chuỗi suy ra trạng thái có đầu vào đầu tiên (21/8/2026)

#### 1. ⭐ **Hai** quy trình workflow trên **một** bảng — vì ma trận phân quyền đòi thế

Ma trận §6 tách hai dòng khác nhau ở đúng cột "Kỹ thuật":

| Chức năng | Admin | QL XN | Kỹ thuật | Vận hành |
|---|:-:|:-:|:-:|:-:|
| Ghi lịch sử sửa chữa/bảo trì | ✔ | ✔ | ✔ | ✘ |
| **Đóng bản ghi sự cố** ("Đã xử lý") | ✔ | ✔ | **✘** | ✘ |

Tức là *"chuyển sang Đã xử lý"* đòi quyền **khác nhau tuỳ bản ghi là sự cố hay không**. Mà `workflow_transitions.required_permission` gắn theo `(from_state, action)` chứ không theo loại công việc — một quy trình duy nhất thì luật đó chỉ diễn đạt được bằng một câu `if` trong service, đúng thứ mà cả cơ chế workflow sinh ra để tránh.

Chốt: seed **`MAINTENANCE_LOG`** và **`MAINTENANCE_INCIDENT`**, `MaintenanceLog.workflowEntityType()` trả tên quy trình theo `work_type`. Cùng một bảng, cùng một entity, cùng ba trạng thái — khác nhau ở ai được bấm nút nào, và khác biệt đó nằm ở **dữ liệu**.

⚠ Đây **không** phải lách quy tắc 15 ("sự cố không phải entity riêng"). Vẫn một bảng `maintenance_logs`, không mã `SC-`, không vòng đời bảy trạng thái. Thứ tách đôi là *quy trình duyệt*, không phải *bản ghi*.

📌 Hệ quả phụ đáng giá: `ops:maintenance:close-incident` seed từ WS-2 nay **có người đọc**. Không dùng tới thì nó là một quyền chưa ai đọc — đúng loại lỗi đã trả giá ba lần (`limits.upload.max-mb.*`, `company.*`, `attachments.valid_from`).

#### 2. ⚠⚠ Trạng thái công trình là sự thật về **công trình**, không phải về **người đang nhìn**

T18.2 chốt bản ghi giữ `org_unit_id` lúc *phát sinh*, không đi theo công trình khi công trình được bàn giao — đúng cho hồ sơ lịch sử, vì chi phí sửa chữa năm ngoái thuộc về Xí nghiệp đã bỏ tiền ra.

Nhưng nó tạo ra một khoảng thời gian mà **bản ghi và công trình thuộc hai đơn vị khác nhau**. Nếu phép đếm *"còn sự cố nào đang mở không"* đi qua bộ lọc phạm vi thì trong khoảng đó nó trả 0, **cờ đỏ tắt, và không một dòng lỗi nào**. Tệ hơn: cột `operational_status` được *lưu sẵn*, nên giá trị sai đó bị ghi xuống và ở lại — công trình rồi sẽ mang trạng thái của lượt tính gần nhất, tức là của người mở màn hình gần nhất.

Chốt: `MaintenanceLogRepository.demBanGhiDangMo` là câu **native** (bộ lọc Hibernate không áp cho native query), và `ConstructionStatusService.recomputeFor(Long)` tra bằng `findById`. Cả hai đều là ngoại lệ có chủ đích của `conventions.md` §4.2, đều chỉ phục vụ việc tính một giá trị dẫn xuất, và đều không trả gì ra API. Bài kiểm `MaintenanceScopeTest.statusSurvivesAConstructionHandover` dựng đúng kịch bản bàn giao.

Cùng lý do, `ConstructionRepository.briefsByIds` cũng là native: sau bàn giao, người của đơn vị cũ vẫn đọc được **bản ghi** cũ mà không còn đọc được **hồ sơ công trình**, nên câu có lọc phạm vi sẽ trả về đúng những dòng đó với tên công trình để trống — một lỗi trông như lỗi dữ liệu.

#### 3. ⚠⚠ Kiểm quy tắc **sau** `workflow.execute(...)` không bao giờ chạy tới

Bản đầu kiểm "đóng bản ghi mà chưa có ngày hoàn thành" *sau* khi engine chuyển trạng thái, với lập luận: engine sở hữu máy trạng thái, hỏi nó "bước này dẫn tới đâu" là chép lại một nửa máy trạng thái ra chỗ khác; ném ngoại lệ sau thì giao dịch quay lui hết.

Lập luận đó sai ở một chỗ: **lượt kiểm sau không bao giờ chạy tới**. `WorkflowEngine.execute` ghi một dòng thông báo, lượt ghi đó **flush** cả entity đang bẩn, và ràng buộc `ck_maintenance_logs_completed_when_done` bắn trước — người dùng nhận một lỗi ràng buộc thô thay vì `OPS-2004`.

Chốt: kiểm **trước**, và tra đích đến bằng chính `WorkflowPort.allowedActions()` — dữ liệu của engine, không phải bản sao. Hành động không có trong danh sách thì để `execute` trả về đúng mã lỗi của nó.

#### 4. Ba lỗi im lặng khác, bài kiểm bắt được ngay lượt chạy đầu

- **`updatedAt == null` không bao giờ đúng.** Bộ ghi nhật ký của Spring Data đặt `@LastModifiedDate` ngay ở lượt **chèn**, nên điều kiện "chưa ai động vào" của cửa sổ tự sửa (T18.9) luôn sai → công tắc bật lên mà **không mở cho ai**. Dùng `version == 0`.
- **`SUM(cost)` trả `null` biến mất khỏi JSON** vì cấu hình `NON_NULL` chung. "Chưa ai điền chi phí" và "đã làm mà không tốn tiền" là hai câu khác nhau; trên một bảng quyết toán, chọn nhầm câu là đưa ra một con số không có thật. Phải đè `@JsonInclude(ALWAYS)` — cùng lý do với ô KPI ở §10.33.
- **Thân JSON của bài kiểm dựng bằng `replace`** chồng lên bản mặc định để lại **hai khoá cùng tên**; Jackson lấy khoá sau, và bài kiểm nhận `OPS-2004` thay vì thứ nó định kiểm.

#### 5. ⛔ Ngân sách hạn mức tần suất của bộ kiểm thử đã vỡ — mở rộng §10.33 mục 7

Thêm hai lớp HTTP là vượt **cả hai** hạn mức đếm theo IP: đăng nhập 30 lượt/15' **và API thường 100 lượt/phút**. Triệu chứng là `ConstructionHttpTest` + `DashboardHttpTest` đỏ hàng loạt với `SYS-0002` — hai lớp không liên quan gì tới WS-18.

Chữa hai tầng:

1. `ArticleHttpTest` và `ConstructionHttpTest` chuyển đăng nhập sang `@BeforeAll` (20 + 18 → 2 + 2 vé) — đúng luật §10.33 mục 7 đã ghi mà hai lớp cũ chưa áp.
2. `PhienHttp` gắn `X-Forwarded-For` **riêng cho mỗi thực thể**: mỗi lớp kiểm thử là một máy khách. Filter vẫn chạy, vẫn đếm, vẫn chặn; `CaffeineRateLimitStoreTest` vẫn là nơi chứng minh nó chặn được thật.

⛔ Cách chữa **sai** vẫn là nới hạn mức ở hồ sơ kiểm thử. Ở production nginx **ghi đè** `X-Forwarded-For`, nên không có đường nào để client thật tự cấp cho mình một IP.

#### 6. Số đo thật

**493 test BE** (239 core + 254 app, +42) + 151 FE · **72 mã lỗi** (thêm `OPS-2017`, BE = FE) · 2 quy trình workflow mới · 1 tham số `settings` có người đọc và có bài kiểm cho **cả hai phía** 0 / khác 0.

Hai ô KPI `incident.open` và `maintenance.in-progress` của §10.33 nay có nguồn thật — `DashboardHttpTest` tách thành hai bài: hai ô thuỷ văn vẫn phải rỗng-kèm-lý-do, hai ô này phải là **số**.

### §10.35. ⚠⚠ Đợt vá sau nghiệm thu WS-22 — một lỗ IDOR, một cột dẫn xuất trộn hai chiều lọc, và ba cơ chế canh gác tự khai mình đã chạy (23/8/2026)

WS-22 đóng ngày 22/8 với T22.8 *"Chạy tay lại mọi thứ đã tick"* và T22.9 *"Quét lại toàn bộ đường
ghi có thể lách phạm vi đơn vị — test scope filter đều pass"*. Lượt rà soát độc lập ngày 23/8 tìm
ra **bốn lỗi thật**, trong đó lỗi nặng nhất nằm đúng trong phạm vi mà T22.9 tuyên bố đã quét.

#### Lỗi 1 — IDOR ở đường ghi tình hình vận hành

`ConstructionOperationStatusService.createSingle` nhận `Long constructionId` thẳng từ payload rồi
tra bằng `constructionRepository.findById`. Ba thứ hỏng cùng lúc:

- Khoá tự tăng **đoán được** — gõ 1, 2, 3 là quét hết bảng công trình.
- ⭐ **`findById` không đi qua `@Filter` của Hibernate.** Bộ lọc áp cho truy vấn và collection,
  *không* áp cho lượt tra thẳng theo khoá chính. Đây là chi tiết quyết định: mọi đường ghi khác của
  MOD-02 đi qua `ConstructionService.get(publicId)` → `ScopeGuard.require`, còn đường này đi thẳng
  xuống repository nên tầng 3 vắng mặt hoàn toàn.
- Bản ghi sinh ra **chép `org_unit_id` của nạn nhân**, nên nó nằm gọn trong phạm vi của họ và lật
  luôn trạng thái dẫn xuất công trình của họ — người bị ảnh hưởng không có cách nào lần ra nguồn.

Bản vá không thêm một phép kiểm mới mà đi lại đúng con đường chung. Gọi `scopeGuard.require` tại chỗ
cũng chặn được, nhưng khi đó lớp này phải tự nhớ ba điều kiện (`public_id`, `deleted_at IS NULL`,
bọc `ScopeGuard`) — đúng kiểu "người viết phải nhớ" mà quy tắc 5 cấm.

#### Lỗi 2 — cột dẫn xuất trộn hai chiều lọc

`ConstructionStatusService.tinh()` xếp 5 mắt xích. Mắt xích 1–2 đi bằng câu **native** (không lọc
phạm vi, có chủ ý, đã ghi ở §10.34). Mắt xích 4 lại đi bằng câu **derived** nên *có* lọc. Hậu quả
không phải chặn nhầm mà là **ghi sai**: người ngoài đơn vị mở màn hình → mắt xích 4 tra ra rỗng →
trạng thái bị hạ xuống `BINH_THUONG` **cho tất cả mọi người**, vì đây là cột được ghi xuống CSDL.

> **Luật rút ra:** một cột dẫn xuất trộn hai nguồn khác chiều lọc thì kết quả phụ thuộc *ai bấm F5
> sau cùng*. Đã đưa thành javadoc của `ConstructionOperationStatusRepository`, nơi cả hai loại câu
> cùng tồn tại và khác biệt được nêu thành hai gạch đầu dòng.

#### Lỗi 3 — màn hình nhập nhanh chưa từng ghi được một dòng nào

Giao diện gọi `/ops/operation-status/batch` (số ít), backend phục vụ `/ops/operation-statuses`
(số nhiều) → **404 ở mọi lượt bấm Lưu**. Và nếu đường dẫn có đúng thì vẫn hỏng tiếp ở bốn tầng:
tên trường lệch hết (`statusCode`/`remarks`/`reportedAt` ↔ `operationCode`/`note`/`effectiveAt`);
khoá công trình gửi UUID trong khi backend đọc số; ô chọn lấy từ `CONSTRUCTION_STATUS` — tức
**trạng thái dẫn xuất**, thứ quy tắc 4 cấm người dùng đặt tay; và mỗi lượt Lưu gửi *toàn bộ* danh
sách công trình kèm trạng thái hiện tại.

Chức năng này được đánh dấu ✅ trong bản ghi tiến độ. Không bài kiểm nào thấy vì phía BE được kiểm
bằng lời gọi service trực tiếp — đúng bài học 391-bài-xanh-mà-mọi-màn-hình-500 của WS-20.

#### Lỗi 4 — ba cơ chế canh gác tự khai mình đã chạy

| Cơ chế | Tự khai | Sự thật |
|---|---|---|
| `RbacMatrixTest` "mọi quyền đều có endpoint dùng" | xanh | Chỉ quét `@RequirePermission`, bỏ qua `workflow_transitions.required_permission` — kênh khai báo thứ hai. Lượt 22/8 đẩy **44 quyền** vào danh sách miễn kiểm, trong đó **7 quyền đang chạy thật** |
| `ops:operation-status:view` | cấp cho 6 vai trò từ WS-5 | Không endpoint nào đòi nó — dữ liệu chỉ có đường ghi vào, không có đường đọc ra |
| `ops.operation-status.stale-days` | seed + bày ra giao diện cấu hình | Không dòng mã nào đọc. Người quản trị chỉnh 7 → 3 và không có gì xảy ra |

#### Ba luật đặt thêm, mỗi luật kèm bài kiểm chứng minh nó bắt được vi phạm

1. **`ApiSurfaceRuleTest`** — không `@PathVariable` nào mang kiểu số, không DTO nhận nào có trường
   khoá kiểu số. Mỗi luật có bài canh danh sách ngoại lệ *không phình* và một bài chống xanh-trên-
   tập-rỗng (đếm được > 20 controller mới tính).
2. **`RbacMatrixTest.usedPermissionCodes()`** — hợp của **hai** kênh khai báo, kèm bài đỏ khi một
   quyền đang dùng còn nằm trong danh sách miễn kiểm.
3. **`OperationStatusHttpTest`** — 6 bài đi qua HTTP thật, gồm cả bài khẳng định đường dẫn số ít
   **không tồn tại** (canh việc hai phía khớp nhau, không canh một lỗi).

> ⚠⚠ **Lượt kiểm chứng đầu tiên của chính bản vá cũng là một xanh giả.** Để chứng minh bài kiểm bắt
> được vi phạm, tôi cố ý gỡ lớp bảo vệ rồi chạy lại — **6/6 vẫn xanh**. Nguyên nhân: lệnh `install`
> bị Checkstyle chặn (tên hàm cố ý xấu), và output đã bị `>/dev/null` nuốt, nên bài kiểm chạy trên
> **jar cũ còn nguyên bản vá**. Đổi tên hàm cho hợp lệ rồi chạy lại thì đỏ đúng 2 bài. Bài học: khi
> làm hỏng có chủ đích để kiểm chứng, **phải xác nhận bản hỏng đã thực sự được nạp**.

#### Dọn nguồn theo dõi tiến độ

`conventions.md` §10 (nay đánh số lại thành **§6** — mục liền trước là §5, không có §6–§9) tự đặt
luật "chỉ sửa `master-tracking.md`" rồi **file tracking thứ tư ra đời ngay sau đó**. Đo trên file
thật: **310/310 dòng mất mã số** (bộ đọc đòi khoảng trắng, cú pháp quy ước dùng dấu hai chấm) ·
khoá nhóm tách làm `WS-19` và `1. WS-19` · **29 mã số trùng, 19 cặp mâu thuẫn trạng thái**. Công cụ
đồng bộ còn `clear()` trước `update()` — một lượt parse rỗng **xoá sạch bảng rồi báo `Success!``.

Nay: một nguồn duy nhất, DoD tách thành mục riêng có mã số, và `test_parse.py` chạy trên **chính**
file thật (không trên chuỗi mẫu tự soạn — người soạn mẫu chép lại đúng giả định sai của mình).

### §10.36. ⚠⚠ Nghiệm thu lại WS-21 và 17 mục DoD — 4/11 màn hình chưa làm, 4/17 cam kết không có phép kiểm nào (23/8/2026)

Sau §10.35, WS-21 và WS-22 được giữ ở `[~]` vì bản ghi "đã xong" ngày 22/8 đã tự chứng minh là không
dùng được. Lượt nghiệm thu lại đối chiếu **từng task với mã thật** thay vì với bản ghi.

#### WS-21 — bốn mục hỏng, mỗi mục một kiểu

| Mục | Bản ghi cũ | Sự thật |
|---|---|---|
| **T21.5** Tab lịch sử sửa chữa | ✅ *"Đã thêm placeholder"* | Một dòng chữ: *"Lịch sử sửa chữa sẽ được tích hợp trong phiên bản sau."* Placeholder **là** nội dung của tab |
| **T21.4** Tab tài liệu | ✅ *"Đã tái sử dụng component có sẵn"* | Gọi `/attachments?ownerId=<uuid>` vào `@RequestParam Long ownerId` → **400 ở mọi lượt mở tab**. Mã nguồn còn nguyên chú thích tự hỏi *"backend expects UUID for construction?"* cạnh một lượt ép kiểu `as unknown as number` |
| **T21.10** Nợ #71 | ✅ *"Đã thêm navigate"* | Dashboard *có* điều hướng sang `?status=SU_CO`, nhưng trang danh sách **không đọc query string** → mở ra danh sách không lọc. Đúng nửa việc, và nửa thiếu không có triệu chứng |
| **T21.11** Test hàm thuần | ✅ *"Test hàm thuần (pure functions)"* | Thư mục `features/operations` **không có một tệp test nào** |

> ⭐ Ba trong bốn mục trên đều thuộc một khuôn: **việc có được làm, nhưng không được nối vào đường
> chạy thật.** Một placeholder được viết ra, một component có sẵn được gọi sai kiểu, một `navigate`
> được thêm mà đầu nhận không đọc. Không cái nào là "quên làm"; cả ba là "làm xong nửa đường rồi
> tích".

#### Một lỗ phân quyền chưa ai chạm tới

`TECHNICIAN` là vai trò **duy nhất** có `ops:construction:create`. Biểu mẫu tạo hồ sơ bắt buộc chọn
đơn vị quản lý, ô chọn gọi `/org-units/tree`, đường đó đứng sau `adm:org-unit:view` — quyền mà
TECHNICIAN không có. **Biểu mẫu tạo công trình chưa từng dùng được bởi đúng vai trò sở hữu nó.**

Điều đáng chú ý: javadoc của `OrgUnitController` từ WS-6 đã ghi đúng chủ ý — *"xem thì gần như ai
cũng cần (chọn đơn vị trong biểu mẫu), còn sửa cấu trúc là việc của quản trị"*. Tài liệu đúng, mã
sai, và không có gì bắt hai bên đối chiếu. Vá bằng `/org-units/selectable` dưới
`@AuthenticatedEndpoint` — cùng hình dạng với `/ops/operation-status-codes/active` ở §10.35.

#### DoD — bốn cam kết không có phép kiểm nào đứng sau

`DOD1.6` (cổng công khai không lộ bài chưa xuất bản) · `DOD1.7` (đính kèm đầu-cuối qua HTTP) ·
`DOD1.11` (thêm mã mới không cần deploy) chưa từng được kiểm. `DOD1.5` (ISR revalidate) có bài kiểm
cấu hình phía Next nhưng **phía phát ra chưa ai gọi thử** — trong khi chính javadoc của
`PortalRevalidateClient` ghi *"không bài kiểm đơn vị nào bắt được chỗ này"* về bẫy HTTP/2.

> ⚠⚠ **Và bài kiểm đầu tiên tôi viết cho DOD1.5 cũng là một xanh giả.** Nó khẳng định
> `exchange.getProtocol()` bằng `"HTTP/1.1"` — và vẫn xanh sau khi đã gỡ `.version(HTTP_1_1)`, vì
> `com.sun.net.httpserver` chỉ nói HTTP/1.1 nên client tự hạ cấp; giao thức quan sát được **giống
> hệt nhau ở cả hai cấu hình**. Đo lại bằng cách chạy cả hai cấu hình lên cùng một máy chủ mới ra
> điểm khác thật: `HTTP_2 → upgrade=true, http2-settings=true` · `HTTP_1_1 → cả hai false`. Đổi sang
> khẳng định trên hai header đó thì bản cố ý hỏng đỏ đúng một bài.
>
> Đây là **lần thứ hai trong hai ngày** một lượt kiểm chứng của chính tôi hoá ra không kiểm gì (lần
> đầu ở §10.35: `install` bị Checkstyle chặn, bài kiểm chạy trên jar cũ). Cùng một bài học: *một
> khẳng định không phân biệt được hai trạng thái thì không khẳng định gì* — và cách duy nhất biết
> được là **chạy thử bản hỏng**.

#### Cổng bao phủ của module `content` chưa từng chạy

Thêm bài kiểm đầu tiên vào `content` làm build **đỏ**: `lines covered ratio is 0.00, but expected
minimum is 0.18`. Trước đó module không có bài kiểm nào nên JaCoCo báo *"Skipping … due to missing
execution data file"* và luật bị bỏ qua trong im lặng — đúng khuôn luật 7, và cùng họ với bẫy
`<includes>` đã ghi ở luật 1.

⛔ **Không nới ngưỡng và không dời bài kiểm sang module khác** — cả hai đều là khôi phục lại trạng
thái im lặng. Thay vào đó viết hai lớp kiểm domain có giá trị thật (`Article.isPubliclyVisible` với
đủ từng điều kiện; `MenuItem.pointTo` với bất biến dọn hai cột kia), đủ đưa lên **18.2%**.

📌 Trong lúc viết, bài kiểm còn ghi lại một hành vi ngoài dự đoán: `LUU_TRU` **không** nằm trong danh
sách loại trừ của `isPubliclyVisible`, nên bài đã lưu trữ mà còn bản duyệt vẫn hiện ngoài cổng. Đã
ghi đúng hành vi thật vào bài kiểm kèm ghi chú — nếu đó không phải ý muốn thì sửa hàm, không sửa bài
kiểm.

#### Ba lỗi nhỏ hơn, cùng một họ "thông điệp nói sai chỗ"

- `SYS-0009` in ra *"trạng thái quét: CLEAN"* trong khi điều kiện chặn là cột `status`, không phải
  `scan_status`. Câu đó tự mâu thuẫn và dẫn người đọc đi tra nhầm chỗ. Đã đổi sang báo `status`.
- Nút "Nhập nhanh" gate bằng `ops:construction:update` trong khi endpoint đòi
  `ops:operation-status:update`.
- Nhập lô dừng ở dòng lỗi **đầu tiên**; với màn hình nhập vài chục cống thì đó là sửa một dòng, gửi
  lại, lại hỏng ở dòng khác. Đổi sang hai pha *kiểm hết rồi ghi*, trả `OPS-2019` kèm `items[i]`. ⚠
  Lỗi phạm vi đơn vị **không** bị gom vào danh sách đó — gom một tín hiệu an ninh vào một lời nhắc
  nhập liệu là làm mất cả mã 403 lẫn sự kiện `security_events`.
