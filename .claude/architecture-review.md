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

---

### §10.37. ⚠⚠ Nghiệm thu image `make dev-docker` — một lỗi chặn, một lỗ lộ mã nguồn, và bộ lọc CI bỏ qua đúng thứ nó canh (24/8/2026)

Điểm xuất phát chỉ là *"chạy `make dev-docker` rồi nghiệm thu image"*. Nó ra năm chuyện, và phần lớn
thuộc họ **"cơ chế có mặt nhưng chưa ai đi qua"**.

#### 1. Bản dựng trên nhánh đang đỏ, mà không cổng kiểm nào biết

`make dev-docker` hỏng ngay ở bước biên dịch `admin-app`: **4 lỗi TypeScript**, cả bốn đến từ commit
`40685c8` **đã đẩy lên `origin/feat-CICD`**. CI có bước `npm run typecheck` bắt được cả bốn, nhưng CI
chỉ kích hoạt trên `dev` (push + PR) nên nhánh tính năng chưa mở PR thì không có cổng kiểm nào.

Một lỗi đáng chú ý hơn ba lỗi kia: `api-types.ts` dùng `AllowedAction` mà không import. Chữa bằng
cách import từ `components/` là **đảo chiều phụ thuộc** — cả codebase đi một chiều
`components → shared`. Đã khai `AllowedActionView` ngay trong `api-types.ts`, và chính lúc khai mới
lộ ra lỗi ở mục 2.

#### 2. ⛔ Trả bài về sửa là thao tác KHÔNG DÙNG ĐƯỢC — hai bản sao của một luật, đặt ở hai nơi

`ArticleController.transition` ép buộc lý do bằng một dòng khai cứng
`"REQUEST_CHANGES".equals(action) && blank(reason)`. Còn `ApprovalActions` mở ô nhập lý do khi
`action.requiresReason` bật — mà record `AllowedAction` của backend là `(action, label, toState)`,
**không có cờ đó và không nơi nào điền**. Kiểu phía giao diện lại khai thừa ba cờ
`primary`/`danger`/`requiresReason`, tất cả `optional`, nên TypeScript im lặng.

Chuỗi hậu quả đo được: người duyệt bấm *"Yêu cầu chỉnh sửa"* → không có ô nào để nhập → gửi lên
thiếu `reason` → backend trả `SYS-0003` → **không có đường nào đi tiếp**. Hai cờ còn lại cũng chưa
từng có giá trị: không nút nào từng là nút chính, không nút nào từng tô đỏ.

**Vì sao 555 bài kiểm không thấy.** `ArticleHttpTest.traBaiPhaiNeuLyDo` kiểm *cả hai vế* của ràng
buộc và xanh trọn vẹn — vì nó gửi JSON dựng tay, không bao giờ chạm vào `allowedActions`. Vế **ép
buộc** đúng; vế **quảng cáo** hỏng; không bài nào kiểm vế thứ hai.

**Cách chữa — bỏ bản sao, đưa luật về dữ liệu.** Thêm cột `workflow_transitions.requires_reason`
(`V202608241256`), record mang thêm `requiresReason`, và **`WorkflowEngine.execute` tự ép buộc** thay
vì controller. Engine là nơi *duy nhất* đổi trạng thái nên không đường vào nào bỏ sót (luật 12); bản
`execute` 3 tham số giữ lại và uỷ quyền với `reason = null`, tức là **hỏng đóng**. Thêm một bước đòi
lý do về sau là một dòng `UPDATE`, không phải một lượt deploy.

📌 `primary`/`danger` đã **gỡ hẳn**. Nút chính nay lấy theo *vị trí đầu danh sách* — backend đã sắp
theo `sort_order`, tức là thứ tự do người khai quy trình quyết định. Không nút nào tô đỏ, vì **không
có cột dữ liệu nào nói bước nào nguy hiểm**; ghi ra đây thay vì bịa một danh sách tên hành động ở
phía giao diện.

#### 3. Image quản trị phát nguyên mã nguồn ra ngoài

`vite.config.ts` đặt `sourcemap: true` không rào theo môi trường. Đo trên
`songnhue-admin-app:local`: **68 tệp `.map`**, và `GET /assets/ApprovalActions-*.js.map` trả **200**
kèm 4.799 byte TypeScript gốc — đủ cả chú thích nội bộ về mã quyền và hình dạng endpoint.
`location /assets/` có `try_files $uri =404` nên tệp tồn tại là nginx phục vụ.

Chữa ở **hai tầng khác nhau**: `sourcemap: false`, và khối `location ~ \.map$ { return 404; }`. Đo
lại sau khi dựng: **0 tệp `.map` trong image**, và lượt tải trả **404**. public-web không dính — Next
mặc định tắt `productionBrowserSourceMaps`.

#### 4. ⚠⚠ Bộ lọc CI bỏ qua đúng job canh những tệp vừa đổi

Job `backend` lọc `^(backend/|\.github/workflows/)`. Nhưng **7 lớp kiểm của bộ BE đọc tệp nằm ngoài
`backend/`**: `FrontendSameOriginTest` (frontend + deploy), `NginxSecurityHeadersTest`,
`EnvFileCommentTest`, `UnresolvedPlaceholderGuardTest`, `EditorVocabularyTest`,
`AllowedActionParityTest`, `SongnhuePostgres`.

Nghĩa là **PR chỉ đụng `frontend/` hoặc `deploy/` thì job canh chúng bị bỏ qua** — và bỏ qua không
hiện ra như lỗi, vì GitHub tính `skipped` của required check là **ĐẠT** (luật 23). Cụ thể cái suýt
mất: `FrontendSameOriginTest` canh đúng lỗi CORS đã làm giao diện quản trị chết suốt WS-8→WS-20, mà
lỗi ấy sống trong một tệp `frontend/`. Đã thêm `frontend/` + `deploy/` vào vế backend, `deploy/` vào
vế frontend; logic mới kiểm bằng `bash -c` với 7 kịch bản (luật 19).

#### 5. Hợp nhất nhánh hotfix — merge sạch mà vẫn vỡ một bài kiểm

`fix-public-web-ui` (9 commit) merge về `feat-CICD` **không đụng độ**, chỉ mang sang 9 tệp và **không
một tệp Java hay admin-app nào** — 30 tệp backend trùng nhau đã khớp nội dung nhờ cherry-pick
`4a413f7`. Nhưng kết luận "an toàn" dựa trên typecheck + phân tích tệp là **chưa đủ**: chạy bộ test
FE trên cây đã merge thì `siteContactConfig.test.ts` đỏ.

Nguyên nhân: bản vá giao diện đặt lại địa chỉ, điện thoại, fax, email, hotline và giờ làm việc vào
`SiteFooter.tsx`/`SiteHeader.tsx` làm giá trị dự phòng `??`. Đây là **đúng lỗi cũ quay lại theo hình
dạng khó thấy hơn** — màn hình vẫn hiện đúng, nên không dấu hiệu nào cho thấy số điện thoại người dân
gọi khi có sự cố lại đang nằm trong mã nguồn (vi phạm quy tắc 12).

⚠⚠ **Chỉ 1 trong 3 bộ canh bắt được.** Hai bộ kia xanh giả cho đúng dữ liệu đang có:

| Bộ canh | Vì sao lọt |
|---|---|
| số điện thoại | regex đòi khoảng trắng giữa các nhóm số, `(024) 33.546.247` có **dấu chấm** |
| địa chỉ | regex phân biệt hoa thường, địa chỉ mới viết **HOA toàn bộ** |
| email | bắt được — bộ duy nhất |

Đã trả dự phòng về `''`, vá hai regex, và thêm một bài canh ở tầng **cấu trúc** phủ cả sáu khoá cùng
lúc: mọi `config?.['company.*'] ?? …` phải rơi về chuỗi rỗng. Ba bài bắt theo *hình dạng từng loại dữ
liệu* thì luôn có loại thứ tư lọt qua.

📌 Bỏ giá trị cứng **không mất nội dung**, vì `V202608241255` đã seed đủ sáu khoá. Đã đo trên cổng
đang chạy: cả năm chuỗi (điện thoại, fax, email, địa chỉ, giờ trực ban) **vẫn hiện trên trang chủ**,
và không tệp nguồn nào còn chứa chúng.

#### Đã kiểm chứng bằng cách làm hỏng có chủ đích

Mọi cơ chế mới đều qua một lượt đột biến, **và mỗi lượt đều xác nhận bản hỏng đã được nạp** trước khi
đọc kết quả (luật 10): bật lại `sourcemap: true` → đỏ · gỡ khối `location ~ \.map$` → đỏ · thêm một
trường thừa vào `AllowedActionView` → `AllowedActionParityTest` đỏ và in ra cả hai bộ trường · ép
`toAllowedAction` trả `false` → bài HTTP đỏ và in nguyên văn payload trên dây · đặt lại số điện thoại
dạng chấm và địa chỉ viết hoa → cả ba bài canh liên hệ đỏ.

📌 **Rà mapping toàn bộ bề mặt API**: 68 kiểu TypeScript đối chiếu với 141 record/DTO Java — 42 khớp
theo tên, 22 khớp theo hình dạng, 4 xác minh tay (`ApiEnvelope` ↔ `ApiResponse<T>`, hai DTO của ops
khai bằng `class` chứ không `record`, `PageResult` là kiểu thuần FE). **Lệch thật duy nhất trên cả bề
mặt là `AllowedAction`.**

---

### §10.38. Job đóng gói image frontend chạy lần đầu và bắt ngay một lỗi đã nằm sẵn từ Phase 1 (24/8/2026)

Merge `feat-CICD` vào `dev` (PR #10) → CI đỏ ở **đúng một job**: `Đóng gói image frontend (public-web)`.
Bảy job còn lại xanh, kể cả `Frontend — lint` vốn đã chạy build cả hai app.

**Nguyên nhân.** Chuỗi ba mắt xích, mỗi mắt xích tự nó đều hợp lý:

| Mắt xích | Nội dung |
|---|---|
| `ci.yml` | `NEXT_PUBLIC_SITE_URL=${{ vars.PUBLIC_SITE_URL }}` — biến kho **chưa đặt** (chưa có tên miền, WS-11 chưa chạy) → build-arg là chuỗi rỗng |
| `public-web.Dockerfile` | `ARG NEXT_PUBLIC_SITE_URL` không giá trị mặc định + `ENV NEXT_PUBLIC_SITE_URL=$…` → biến **được đặt bằng chuỗi rỗng**, không phải "chưa đặt" |
| `site.ts` | `process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000'` — chuỗi rỗng **không nullish**, mặc định không chạm tới |

Kết quả: `new URL('')` trong `generateMetadata` của `layout.tsx` ném `ERR_INVALID_URL` giữa lúc
prerender `/_not-found` → `next build` thoát mã 1.

⚠⚠ **Chính lượt CI hỏng ấy có một bước in ra cảnh báo** *"Chưa đặt biến repo `PUBLIC_SITE_URL` —
sitemap/canonical sẽ trỏ về `localhost`"*. Tức là pipeline **tự khai** rằng nó tin có một giá trị mặc
định đang đỡ phía dưới. Mặc định ấy chưa bao giờ được dùng tới. Đây là luật 3 (*canh giá trị ĐÃ GIẢI,
đừng canh giá trị MẶC ĐỊNH*) tái phát ở một mặt phẳng khác: lần trước là `--env-file` thắng
`${VAR:-}`, lần này là Docker `ARG` rỗng thắng `??`.

📌 Cùng file `site.ts` đã ghi sẵn cảnh báo *"dùng `||` chứ không `??`"* cho `API_BASE_URL` ngay bên
dưới — bài học đã trả giá một lần, ghi lại đúng chỗ, mà **dòng kế bên vẫn dùng `??`**. Ghi chú không
phải cơ chế; chỉ phép kiểm mới là cơ chế.

**Vì sao 559 test BE + 180 test FE + `make ci-local` đều không thấy.** Không phải vì thiếu bài kiểm mà
vì **mọi lượt build local đều nạp `.env.local`** (có `NEXT_PUBLIC_SITE_URL=http://localhost:3000`),
còn CI checkout sạch nên không có tệp ấy. Biến rỗng là một trạng thái **chỉ tồn tại trong Docker
build**, và trước PR này **chưa từng có job nào build image public-web** — job đóng gói frontend do
chính PR này thêm vào. Nó bắt lỗi ngay lần chạy đầu tiên, đúng thứ nó sinh ra để bắt: trước đó hai
workflow triển khai chỉ `up -d app nginx`, tức là production sẽ chạy backend mới dưới giao diện cũ.

**Đã sửa.** `??` → `||` ở `SITE_URL` (đặt ở *đường dữ liệu đi qua*, không ở `Dockerfile` — luật 12: ba
lối vào khác nhau đều rơi về hằng số ấy). Thêm `src/lib/site.test.ts` — **11 bài**, kiểm *hành vi* chứ
không grep toán tử (luật 2): nạp lại module với biến ở **cả hai trạng thái rỗng và chưa đặt**, rồi
khẳng định đúng bất biến nơi gọi cần — `new URL(SITE_URL)` không ném, `API_INTERNAL_BASE_URL` tuyệt
đối, `API_BASE_URL` khác rỗng. Kèm một bài **liệt kê**: mọi `process.env.*` mà `site.ts` đọc phải nằm
trong danh sách được phủ, nên thêm biến thứ tư mà quên kiểm là đỏ ngay (luật 14).

**Kiểm chứng bằng cách làm hỏng có chủ đích** (luật 10 — xác nhận bản hỏng đã được nạp trước khi đọc
kết quả): trả `??` về `site.ts`, `grep` xác nhận dòng 18 đúng là bản hỏng, chạy lại → **2 bài đỏ với
đúng `TypeError: Invalid URL`**. Sau đó `next build` với `NEXT_PUBLIC_SITE_URL=` rỗng: trước vá thoát
mã 1, sau vá sinh đủ 10 route.

📌 Lỗi `ECONNREFUSED` in ra dày đặc trong cùng log **không phải nguyên nhân** — `getSiteConfig` bắt và
rơi về hằng số, build vẫn qua. Đọc log theo trình tự thay vì theo mức nghiêm trọng mới thấy dòng
`Invalid URL` mới là dòng giết build (luật 22).

**Chỗ đặt cổng kiểm cũng sai, và đó mới là phần đáng sửa.** Hai job đóng gói image có
`if: github.event_name == 'push'` — nghĩa là lượt dựng image đầu tiên của bất kỳ thay đổi nào diễn ra
**sau khi đã merge**. Chỗ duy nhất chúng có thể đỏ là `dev`. Đó không phải một cổng kiểm, đó là một
cái chuông báo cháy đặt ngoài toà nhà.

Và image là nơi **duy nhất** thấy được những gì chỉ tồn tại lúc build container: `ARG` để trống,
`.env.local` vắng mặt, tầng runtime chép hụt. Không thứ nào trong đó có mặt ở `npm run build` hay
`mvn verify`, kể cả khi chạy trên đúng commit ấy — nên "PR xanh" chưa bao giờ nói được gì về image.

Đã đổi cả hai job sang **dựng ở PR, chỉ đẩy GHCR khi push vào `dev`** (`push: ${{ github.event_name
== 'push' }}`, bước đăng nhập GHCR cũng gác theo). Đẩy từ nhánh chưa duyệt thì tag `dev` trỏ vào mã
chưa ai xem. Khối shell ghi tóm tắt kiểm bằng `bash -c` với **cả 4 tổ hợp** event × matrix (luật 19),
và YAML parse lại để xác nhận điều kiện đã đúng chứ không chỉ đọc bằng mắt.

⬜ **Hai nợ để lại, đều là việc bấm ở GitHub**:

| Nợ | Nội dung | Hệ quả nếu để nguyên |
|---|---|---|
| — | `PUBLIC_SITE_URL` chưa đặt (chưa có tên miền) → image hiện tại có `sitemap.xml`/canonical/Open Graph trỏ về `http://localhost:3000`. **Chốt cùng WS-11** | Trang chạy đúng; chỉ công cụ tìm kiếm và trình xem trước liên kết đọc ra địa chỉ sai |
| **#46** | Thêm 3 context đóng gói image vào `required_status_checks` của `dev` | Hai job ấy *hiện* lỗi ở PR nhưng **không chặn** merge — vẫn merge được một PR có image hỏng |

📌 Nợ #46 cùng hình dạng với #45 (Dependency graph) và #27 (bảo vệ nhánh): **một cổng kiểm tồn tại
trong mã nhưng chưa có hiệu lực ở nơi nó phải chặn.** Ba lần rồi.

---

### §10.39. Rà đường triển khai staging bằng mã thật — bốn lỗi chặn, cả bốn đều im lặng (24/8/2026)

Trước khi viết kế hoạch thực thi staging, rà lại toàn bộ đường triển khai
bằng **mã thật** thay vì bằng tài liệu. Bốn chỗ mà làm theo bản cũ sẽ hỏng.

#### 1. ⛔ Smoke test của CD đo một đường không đi tới đâu

Cả `deploy-staging.yml` và `deploy-prod.yml` hỏi `$BASE_URL/actuator/health/readiness`. nginx biên
chỉ định tuyến `/api/` và `/` sang hai image giao diện; **không khối `location` nào chuyển `/actuator`
sang `app`**, và không FE nào chuyển tiếp nó (`next.config.ts` có `headers()` cho `/:path*` chứ không
có `rewrites()`).

Đo thật trên image đã dựng:

| Qua image | Kết quả |
|---|---|
| `public-web` | **404** |
| `admin-app` | **200 — kèm trang HTML của SPA** |

Trường hợp thứ hai nguy hiểm hơn: `curl -f` đi qua, chỉ có phép so chuỗi `"status":"UP"` cứu. Hậu quả
là **lượt deploy đầu tiên đỏ sau đúng 5 phút chờ**, vì một endpoint chưa từng phục vụ được ai — và
người trực sẽ đi tìm lỗi ở stack vừa dựng.

Đổi đích sang `GET /api/v1/public/site-config` (`PublicHttpTest` đã canh nó truy cập được không cần
đăng nhập). Đích mới **đo được nhiều hơn hẳn**: nginx biên → public-web → Route Handler
`/api/v1/[...path]` → app → postgres. Readiness của actuator, kể cả khi định tuyến được, cũng chỉ
chứng minh tiến trình còn sống — không chứng minh một byte nào đi hết chặng người dùng thật đi
(luật 8). Kiểm bằng `bash` với **cả hai nhánh**: máy chủ trả envelope → thoát 0; đích chết → thoát 1
kèm ba bước chẩn đoán.

#### 2. ⛔ Sáu biến compose ĐÒI mà không tệp env mẫu nào có

`PUBLIC_DOMAIN` · `ADMIN_DOMAIN` · `FILES_DOMAIN` · `ROBOTS_TAG` · `MINIO_ROOT_USER` ·
`MINIO_ROOT_PASSWORD` · `REVALIDATE_SECRET` — bảy biến, `deploy-guideline.md` §3.2 liệt kê tay **sáu**
và bỏ sót `REVALIDATE_SECRET`. Không cái nào có trong `staging.env.example` hay `prod.env.example`, dù
chính tài liệu chỉ hai tệp ấy là *"danh sách biến đầy đủ"*.

Và chúng viết ở dạng `${TÊN}` **không có `:?`**, nên thiếu **không phải lỗi khởi động** — Compose thay
bằng chuỗi rỗng rồi chạy tiếp:

| Thiếu | Triệu chứng |
|---|---|
| `PUBLIC_DOMAIN`/`ADMIN_DOMAIN` | `server_name` rỗng → mọi tên miền rơi vào server block mặc định và bị `ssl_reject_handshake` từ chối. "Không vào được", không log ứng dụng nào |
| `FILES_DOMAIN` | `MINIO_ENDPOINT` thành `https://` → **mọi nút Tải về hỏng**, tải LÊN vẫn chạy |
| `MINIO_ROOT_*` | MinIO khởi động bằng tài khoản mặc định |
| `REVALIDATE_SECRET` | `/api/revalidate` trả 503; cổng **đứng yên ở nội dung cũ** sau mỗi lần duyệt bài |

⚠ `EnvFileCommentTest` và `UnresolvedPlaceholderGuard` không bắt được vì **cả hai soi giá trị của
những biến đã có mặt**. Không bài nào hỏi *"còn biến nào compose cần mà tệp mẫu chưa có"*. Đã thêm
`ComposeEnvCompletenessTest` (3 bài, có bài chống xanh-trên-tập-rỗng và bài kiểm chứng ngược), và
**kiểm chứng bằng cách làm hỏng**: bỏ `REVALIDATE_SECRET` khỏi mẫu → đỏ đúng tên biến đó.

#### 3. `GRAFANA_ADMIN_PASSWORD` viết `${…:?}` nhưng không được nhắc ở đâu

`docker compose -f compose.observability.yml up -d` — đúng lệnh `deploy-guideline.md` §10 bảo chạy —
**dừng ngay**, không container nào lên. Biến không có trong tệp mẫu nào, không có trong tài liệu nào.
Đã thêm khối giám sát vào `staging.env.example` (5 biến).

📌 Cùng mục đó còn một chỉ dẫn sai: §2.2 bảo `ufw allow ... port 3000` để xem Grafana, trong khi
`compose.observability.yml` publish ra `127.0.0.1:13001`. Mở cổng tường lửa không có tác dụng, và cổng
cũng sai. Vào bằng SSH tunnel.

#### 4. Ba service giám sát không có `mem_limit` nào — và ngân sách bộ nhớ VPS-2 không vừa

`hosting_recommendations.md` chốt VPS-2 = **4 GB**, con số có từ lúc gộp VM-3 (sao lưu + giám sát) vào
VPS-2 để tiết kiệm. **Ngân sách bộ nhớ không được tính lại sau khi gộp.** Cộng đúng những gì compose
khai: stack staging **3.968 MB** + giám sát **928 MB** + hệ điều hành ~500 MB ≈ **5,4 GB**.

Hết bộ nhớ thì OOM-killer chọn tiến trình có RSS lớn nhất — `app` hoặc `postgres`. `app` có
`-XX:+ExitOnOutOfMemoryError` nên JVM thoát, `restart: unless-stopped` dựng lại, và triệu chứng bên
ngoài là **"staging chập chờn"** chứ không phải một lỗi đọc được. Nguy hiểm hơn: VPS-2 **cũng giữ bản
sao lưu**; một lượt OOM lúc 03:00 làm hỏng lượt kéo về mà không ai biết.

Đã đặt trần cho cả ba service giám sát, và ghi con số + ba phương án chọn máy vào
`hosting_recommendations.md` §8. **Khuyến nghị: VPS-2 lên 8 GB.** Phương án giữ 4 GB bằng cách chuyển giám
sát sang VPS-1 bị bác — nó phá đúng lý do dựng ra nó.

📌 Có một cách tiết kiệm nghe hợp lý mà **không dùng được**: "chỉ bật staging khi cần test". Lúc test
chính là lúc cả hai stack cùng chạy — đúng lúc thiếu bộ nhớ. Tắt lúc rảnh tiết kiệm điện, không tiết
kiệm RAM phải mua.

---

### §10.40. 204 No Content bị giao diện biến thành lỗi — trên 24 endpoint, suốt từ WS-8 (25/8/2026)

Báo cáo từ vận hành: *"đổi mật khẩu API báo lỗi nhưng thực tế đã đổi thành công và đăng nhập được
bằng mật khẩu mới"*. Truy ra thì nó không phải lỗi của luồng đổi mật khẩu, và không chỉ một endpoint.

#### Cơ chế

| Mắt xích | Nội dung |
|---|---|
| Spring | `ResponseEnvelopeAdvice` là `ResponseBodyAdvice`, mà **advice không được gọi khi handler trả `void`** — không thân thì không converter nào chạy. **24 endpoint** vì thế trả 204 **trần, không envelope** |
| `conventions.md` §2.1 | Tiêu đề hứa *"thống nhất 100% endpoint"*, **không ghi ngoại lệ nào** |
| axios | Thân rỗng → `response.data = ''` |
| `apiClient.unwrap` | Viết theo đúng lời hứa "100%" nên **chỉ đọc `envelope.success`**, không đọc mã trạng thái. `''.success` là `undefined`, `!undefined` là `true` → ném `SYS-0001 "Thao tác không thành công"` |

**Máy chủ đã commit xong rồi mới ném.** Phủ toàn bộ nhóm 204: xoá, sắp xếp lại, đánh dấu đã đọc, gỡ
đăng, khoá tài khoản, đăng xuất, đổi mật khẩu.

#### Vì sao đổi mật khẩu là chỗ nó lộ ra nặng nhất

`api.post` ném **trước** khi tới `endSession()`, nên phiên phía giao diện không được dọn. Người dùng
thấy báo lỗi → bấm gửi lại → lần này backend trả **403 AUTH-0005**, vì lượt đầu đã thu hồi phiên và
xoá vé CSRF. Nhật ký máy chủ đọc ra `change-password → 204` rồi hai lượt `403`.

⚠⚠ **Lượt sửa ngày 22/8 đã chữa TRIỆU CHỨNG THỨ HAI của đúng chuỗi này** — guard `RequireAnonymous`
đẩy người dùng ngược về biểu mẫu — và ghi lại rất kỹ trong `ChangePasswordPage` lẫn `AuthProvider`.
Nhưng mắt xích đầu tiên nằm xa hơn về phía trước, ở `unwrap`, nên bản vá ấy **không bao giờ chạy
tới**. Hai vòng chữa cùng một triệu chứng mà không ai hỏi *"lượt gọi API có trả về được không"*.

📌 `ChangePasswordHttpTest` đã dựng lại được cả cảnh 403 và vẫn xanh trọn vẹn — vì bài kiểm BE không
đi qua `unwrap`. Luật 5 ở một hình dạng mới: không phải "gọi thẳng service", mà là **kiểm đúng một
đầu của một hợp đồng có hai đầu**.

#### Đã sửa

Vá ở `apiClient.unwrap` — chỗ **cả 24 đường vào cùng đi qua** (luật 12). Sửa ở màn hình đổi mật khẩu
thì 23 màn hình còn lại vẫn hỏng trong im lặng, và đó đúng là chuyện vừa xảy ra.

Ranh giới cố ý đặt ở **"không có thân"**, không phải "thân không đọc được": nới thành cái sau là
nuốt luôn `success:false` kèm 200 — có một bài canh riêng cho ranh giới ấy.

Ghi lại ngoại lệ vào `conventions.md` §2.1 (lời hứa "100%" chính là thứ khiến FE viết sai), và ghim
hai đầu hợp đồng: `ChangePasswordHttpTest` "Cam kết 0" khẳng định 204 có thân **rỗng** ↔
`apiClientNoContent.test.ts` khẳng định client coi thân rỗng là **thành công**.

**Kiểm chứng ngược** (luật 10 — xác nhận bản hỏng đã nạp trước khi đọc kết quả): gỡ bản vá → **5 bài
đỏ với đúng chuỗi `"Thao tác không thành công"`**, còn bài canh ranh giới **giữ xanh**.

---

### §10.41. `DB_APP_PASSWORD` — một biến không ai đọc che mất một biến thật sự thiếu (25/8/2026)

Lượt dựng staging đầu tiên: container `postgres` quay vòng khởi động lại, rồi `songnhue_app` báo
`password authentication failed`.

`compose.prod.yml` truyền `DB_APP_PASSWORD`, còn `10-bootstrap.sh` gọi `require_env DB_PASSWORD`.
Hai cái tên khác nhau, nên **biến được truyền thì không ai đọc, biến script cần thì không được
truyền** — luật 15 và một biến thiếu, cùng một dòng.

⚠ **Vì sao nó sống sót tới tận hôm dựng máy thật:** `compose.infra.yml` — đường chạy local — khai
**đúng** cả bốn tên. Mọi lượt thử ở máy vì thế đều xanh, và cái sai chỉ tồn tại trên đường mà chưa
ai đi. Cùng hình dạng với luật 3: hai đường vào cùng một script, chỉ một đường được đi thử.

Bản vá tại chỗ lúc deploy là **thêm** `DB_PASSWORD` bên cạnh `DB_APP_PASSWORD` — chạy được, nhưng để
lại đúng cái dòng chết đã gây ra chuyện. Nay xoá hẳn, giữ đúng một tên.

Thêm `PostgresInitEnvTest`: đối chiếu `require_env` của script với khối `environment` của service
`postgres` ở **cả hai** tệp compose, và bắt **cả hai chiều** — thiếu biến script cần, *và* thừa biến
`DB_*` không ai đọc.

**Kiểm chứng ngược**: trả `DB_APP_PASSWORD` về → hai bài đỏ, một báo *thiếu* `DB_PASSWORD`, một báo
*thừa* `DB_APP_PASSWORD`.

📌 Bản thân bài kiểm cũng vấp một lượt: `Pattern.compile("^\\s+…")` **thiếu `MULTILINE`**, nên `^`
chỉ khớp đầu cả chuỗi, hàm trả tập rỗng và bài kiểm đỏ với *cả bốn* biến — báo sai chỗ. Một bộ canh
đọc sai vẫn là một bộ canh đỏ; chỉ có đọc kỹ thông báo mới phân biệt được.

---

### §10.42. CD Staging đỏ vì PR bị squash — và thông báo lỗi trỏ vào ba chỗ đều đang tốt (25/8/2026)

Lượt CD Staging **đầu tiên** thất bại:

```
Đỉnh dev đang đề bạt: dbb67e674afcd63aab1ce755eb2e33d00de9093c
Error: Không tìm thấy image 'app' trong 50 commit gần nhất tính từ dbb67e6…
Kiểm tra theo thứ tự: (1) job đóng gói ở dev có chạy và đẩy được không …
```

Ba bước chẩn đoán ấy **đều là ngõ cụt**. Đo lại: lượt CI trên `dev` (`32770330801`, push `a0b6bfc`)
xanh cả 7 job, log ghi rõ `pushing manifest for …/public-web:a0b6bfc…`, `…/app:a0b6bfc…` — **cả ba
image đã lên GHCR**.

#### Nguyên nhân

PR #14 được **squash** vào `staging` thay vì tạo merge commit. `dbb67e6` chỉ có **một cha**:

| Bước | Điều đã xảy ra |
|---|---|
| `git rev-parse -q --verify HEAD^2` | thất bại — squash không có cha thứ hai |
| Nhánh `else` | rơi về `git rev-parse HEAD` = `dbb67e6`, **SHA chưa bao giờ tồn tại trên `dev`** |
| `git rev-list --first-parent -n 50` | lùi theo lịch sử **của `staging`** — `3c29f0c`, `35bc927`, `2f1fe5e`… |
| Kết quả | không commit nào có image, báo *"không tìm thấy image"* |

⚠ `staging` có **gốc lịch sử khác hẳn** `dev` — nó tách ra từ thời còn là kho tài liệu và chưa từng
nhận mã. Nên lượt lùi 50 bước không chỉ trượt, nó đi vào một nhánh không liên quan.

#### Cái sai thật nằm ở chỗ khác: bản dự phòng làm hỏng SAI CHỖ

`else dev_tip="$(git rev-parse HEAD)"` không sai vì nó dừng lượt deploy — dừng là đúng. Nó sai vì
**mô tả sai nguyên nhân**: người trực bị cử đi kiểm ba thứ đang hoạt động hoàn hảo, trong khi việc
cần làm là chọn lại phương thức merge.

Cùng họ với luật 22 (*đọc log theo trình tự, đừng đọc theo mã lỗi*) nhưng ở phía người viết công cụ:
**một thông báo lỗi tự tin mà sai hướng còn tốn thời gian hơn không có thông báo nào.**

#### Đã sửa — đối chiếu bằng CÂY TỆP

Khi không có cha thứ hai, tìm trong 200 commit gần nhất của `origin/dev` một commit có **cây tệp
trùng khít** với `HEAD`. Cây giống nhau là bằng chứng **mạnh hơn** quan hệ cha–con cho đúng câu đang
hỏi — *"mã trên staging có đúng là mã đã dựng ra image này không"* — vì nó so **nội dung thật**, chứ
không so mối liên kết. Không khớp cái nào thì thoát 1 và **nêu đích danh squash**.

Đo trên kho thật: cây của `origin/staging` trùng khít `dev@a0b6bfc` từng byte.

Kiểm bằng `bash` (luật 19), **ba kịch bản, mỗi kịch bản một nhánh mã khác nhau**:

| Kịch bản | Kết quả |
|---|---|
| squash + cây khớp (đúng hiện trạng) | cảnh báo + giải ra `a0b6bfc` |
| merge commit thật | lấy `HEAD^2`, **không** in cảnh báo |
| một cha + cây không khớp | **thoát 1**, nêu đích danh squash |

📌 Hai lượt kiểm đầu của tôi **không thật sự chạy**: `git checkout` bị chặn vì có thay đổi chưa
commit, nên cả ba kịch bản chạy trên cùng một `HEAD` và in ra ba kết quả giống hệt nhau. Chỉ đọc kỹ
mới thấy `HEAD^2 = KHÔNG_CÓ` ở kịch bản đáng lẽ phải có. Phải commit rồi dựng `git worktree` riêng
mới tách được ba trạng thái. **Luật 10 ở phía ngược lại**: không chỉ phải xác nhận bản *hỏng* đã
được nạp, mà cả bản *đúng* cũng vậy.

#### Bản vá đầu tiên CŨNG sai, và sai êm hơn (25/8, cùng ngày)

Bản vá 1 giữ `HEAD^2` làm đường chính, chỉ thêm đường cứu bằng cây tệp khi *thiếu* cha thứ hai. Dựng
xong nhánh giải xung đột cho PR #16 mới lộ ra lỗ: **thứ tự cha có thể đảo**.

`dev` bật `required_linear_history` nên không đặt được merge commit lên `dev` — tức là cách duy nhất
giải xung đột là cắt nhánh **từ `dev`** rồi merge `staging` vào. Khi đó `HEAD^1` là `dev`, còn
**`HEAD^2` là đỉnh `staging` cũ**. Cha thứ hai *có tồn tại* nên đường cứu không kích hoạt, và lượt
lùi lại đi vào lịch sử riêng của `staging` — hỏng đúng kiểu cũ, chỉ khác lối vào.

Bản vá 2 thử luật *"lấy cha nào nằm trên `dev`"*. **Cũng sai, và sai êm hơn nữa**: cha thứ nhất của
một commit squash là **tổ tiên chung cổ lỗ** `3c29f0c` — mà nó *đúng là* nằm trên `dev`. Luật ấy vì
thế chọn một commit từ thời kho này còn chưa có mã, và chọn **im lặng**, không cảnh báo gì. Bộ kịch
bản kiểm bắt được ngay: kịch bản 1 và 3 trả về `3c29f0c` thay vì `a0b6bfc`.

**Chốt lại thành một luật duy nhất:** *commit nào trên `dev` có **cây tệp** trùng khít mã đang nằm
trên `staging`.* `merge-base` chỉ là phỏng đoán nhanh, và phỏng đoán ấy **vẫn phải qua đúng phép
kiểm cây** như mọi ứng viên khác. Không còn nhánh nào tin vào quan hệ cha–con nữa.

Bốn kịch bản, mỗi kịch bản một hình dạng lịch sử riêng:

| Hình dạng | Cha | Kết quả |
|---|---|---|
| merge chuẩn `staging ← dev` | (staging, dev) | `merge-base` + cây khớp → đỉnh dev |
| **thứ tự cha đảo** `dev ← staging` | (dev, staging) | `merge-base` + cây khớp → đỉnh dev |
| squash | (tổ tiên chung) | quét cây → đúng commit dev |
| cây không khớp gì | — | **thoát 1**, không đoán tiếp |

📌 **Bộ kịch bản kiểm của tôi chạy giả ba lượt liên tiếp** trước khi cho ra số liệu dùng được: lượt
đầu `git checkout` bị chặn vì có thay đổi chưa commit; hai lượt sau `git merge` để lại xung đột nên
index kẹt và mọi checkout tiếp theo hỏng — cả bốn kịch bản chạy trên cùng một `HEAD` và in ra bốn
kết quả giống hệt nhau. Chỉ sau khi in `HEAD`, danh sách cha và **hash cây** *trước* mỗi lượt đọc kết
quả thì mới thấy. Luật 10 áp cho cả bản đúng lẫn bản hỏng: **chưa xác nhận trạng thái đã được dựng
thì con số đọc ra không nói gì cả.**

#### Chỗ không bịt được bằng cấu hình

GitHub **không** cho đặt phương thức merge theo từng nhánh đích, và `dev` bật
`required_linear_history` nên repo buộc phải cho phép squash. Không thể tắt squash ở cấp repo để
chặn. Đây là chỗ chỉ con người nhớ được — nên nó nằm ở `docs/branch-protection.md` §2.3-b và ở đầu
`deploy-staging.yml`, cộng với lưới an toàn đối chiếu cây tệp ở trên.

---

### §10.43. ⚠⚠ CD Staging vẫn đỏ sau khi đã sửa lịch sử — lượt tra image đi ẩn danh vì một dòng `env` cách đó 100 dòng (25/8/2026)

Sau §10.42, lượt giải đỉnh `dev` đã chạy đúng:

```
Đỉnh dev đang đề bạt: 9765581  (merge-base, cây tệp trùng khít)
Error: Không tìm thấy image 'app' trong 50 commit gần nhất tính từ 9765581
```

Lịch sử đúng, image vẫn "không tìm thấy". Và đây là lần thứ **hai liên tiếp** cùng một thông báo lỗi trỏ vào ba chỗ **đều đang tốt**.

#### Đo trước, đoán sau

| Câu hỏi | Đo bằng gì | Kết quả |
|---|---|---|
| Job đóng gói ở `dev` có chạy không | `gh run view` 5 lượt gần nhất | **cả 3 job xanh ở cả 5 commit** |
| Tag image có đúng dạng workflow tra không | đọc `ci.yml` | `ghcr.io/<repo>/app:<sha>` — **khớp chính xác** |
| Bước đăng nhập có xanh không | log lượt chạy thật | `Login Succeeded!` |
| Gói GHCR đọc được ẩn danh không | `GET ghcr.io/token` + `GET .../manifests/<sha>` | token **rỗng**, manifest **403** |

Bốn dòng ấy khoanh ngay vào một khả năng: lượt tra chạy **không mang thông tin đăng nhập**.

#### Nguyên nhân

Bước *Xác định image cần triển khai* mang:

```yaml
        env:
          DOCKER_CONFIG: ${{ runner.temp }}/.docker
```

`docker/login-action` ghi vào `$HOME/.docker`. **Hai thư mục khác nhau** — nên `Login Succeeded!` là thật, mà mọi lệnh `docker` ở bước sau đọc một thư mục rỗng và đi ẩn danh. Gói GHCR **mặc định riêng tư kể cả khi kho mã công khai**, thế là cả 50 ứng viên trượt sạch.

Dòng `env` ấy có từ **PR #1**. Nó sống được bốn tháng vì `deploy-staging.yml` chưa từng chạy thật lần nào — đúng luật 7: *một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai*. Đáng chú ý là `deploy-prod.yml` **không** có dòng này; sự bất đối xứng giữa hai tệp mới là dấu vết dẫn tới nó.

#### Vì sao lại mất hàng giờ mới thấy

`docker manifest inspect ... 2>/dev/null` làm **403 (không có quyền)** và **404 (chưa dựng)** trông y hệt nhau — luật 9: *một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì*. Thông báo lỗi kèm ba bước chẩn đoán, cả ba trỏ vào chỗ đang tốt, nên nó **chủ động dẫn người đọc đi sai đường**.

#### Bản vá — ba phần

1. **Gỡ `DOCKER_CONFIG`** khỏi bước tra (`deploy-prod.yml` vốn đã đúng).
2. **Một lượt tra thử để nguyên stderr** trên tag `app:dev` trước vòng lặp: hỏng ở đó là hỏng về **quyền**, và nó tự nói ra như vậy.
3. **`GhcrLookupAuthTest`** (3 bài) canh: không workflow triển khai nào đặt khoá `DOCKER_CONFIG:` · bước đăng nhập phải đứng **trước** lượt tra đầu tiên · lượt tra thử báo lỗi quyền còn nguyên.

Kiểm chứng bằng cách làm hỏng có chủ đích — mỗi bài bắt đúng vi phạm của nó, khôi phục thì xanh lại:

| Làm hỏng | Kết quả |
|---|---|
| đặt lại `DOCKER_CONFIG` | 1 đỏ |
| chuyển bước đăng nhập xuống sau lượt tra | 1 đỏ |
| gỡ lượt tra thử báo lỗi quyền | 1 đỏ |
| khôi phục | 3 xanh |

📌 Bản đầu của bài kiểm **đỏ ngay lần chạy đầu** — vì nó đo `indexOf("docker manifest inspect")` trên **nguyên văn**, mà đầu tệp có một chú thích nhắc đúng tên lệnh đó. Đúng luật 2: *canh cấu trúc, đừng canh văn bản*. Đã sửa thành bỏ mọi dòng chú thích trước khi đo.

---

### §10.44. `packages: read` cho một workflow có GHI — và bài kiểm đầu tiên của tôi không bắt được (25/8/2026)

Sau §10.43 lượt tra image chạy đúng: log cho thấy nó tìm ra `94fe2e9` và bắt đầu sao chép layer. Hỏng ở **bước kế tiếp**:

```
pushing sha256:5ec47a… to ghcr.io/…/app:staging
403 Forbidden
denied: installation not allowed to Write organization package
```

#### Nguyên nhân

Cả hai workflow triển khai khai:

```yaml
permissions:
  contents: read
  packages: read
```

Chúng **chủ yếu đọc** — tra image theo tag SHA — nên `read` trông vừa đủ. Nhưng bước cuối *"Gắn tag `staging`"* chạy `docker buildx imagetools create --tag …:staging`, tức **GHI một tag mới** lên gói GHCR. `ci.yml` khai `packages: write` ở hai job đóng gói; hai workflow triển khai thì không.

📌 Hình dạng đáng nhớ: **một workflow gần như chỉ đọc, có đúng một bước ghi ở cuối** — và khối `permissions:` nằm cách đó 180 dòng. Thông báo 403 của GHCR không nhắc gì tới `permissions:`, nên nó không tự dẫn về nguyên nhân.

#### Bài kiểm đầu tiên của tôi **không bắt được** — và lượt kiểm chứng ngược đã chỉ ra

Bản đầu hỏi `noiDung.contains("packages: write")`. Hạ quyền xuống `read` mà bài vẫn **xanh**: chính tệp ấy có dòng

```yaml
echo "quyền 'packages: write' của workflow, và đừng đặt DOCKER_CONFIG ở bước này"
```

nằm trong khối `run:` — **văn bản thường, không phải chú thích**, nên lượt bỏ chú thích không đụng tới và `contains` vẫn khớp. Trớ trêu: dòng làm bài kiểm mù chính là dòng chẩn đoán tôi thêm ở §10.43.

Đã đổi sang **đọc cấu trúc**: gom khối `permissions:` cấp cao nhất thành map rồi khẳng định `packages == "write"`.

| Làm hỏng có chủ đích | Bản `contains` | Bản đọc cấu trúc |
|---|---|---|
| hạ `deploy-staging` về `read` | **xanh — lọt** | 1 đỏ |
| hạ `deploy-prod` về `read` | 1 đỏ | 1 đỏ |
| xoá hẳn dòng `packages` | — | 1 đỏ |
| đổi bước ghi tag thành lệnh chỉ đọc | — | 1 đỏ |
| khôi phục | 4 xanh | 4 xanh |

⛔ Bài học lặp lại nguyên vẹn hai luật đã trả giá: **canh cấu trúc, đừng canh văn bản** (luật 2) và **bộ canh theo hình dạng phải được thử với dữ liệu THẬT đang dùng** (luật 24). Nếu bỏ qua lượt kiểm chứng ngược thì hôm nay đã có thêm một cơ chế xanh mà không canh gì.

---

### §10.45. Lượt deploy staging xanh trọn vẹn mà cổng không có dữ liệu — vì đường ống chưa từng có bước nạp dữ liệu (25/8/2026)

Lượt CD Staging đầu tiên thành công: **11/11 bước xanh**, kể cả rsync, `migrator`, `up -d`, smoke test. Cổng vẫn không có 5 bài.

#### Không có gì hỏng cả

| Bước | Thực sự đã làm gì |
|---|---|
| Đồng bộ cấu hình | rsync `deploy/` → `/opt/songnhue/` — **bộ seed đã nằm sẵn trên máy chủ** |
| `migrator` | chạy hết Flyway → lược đồ + danh mục + menu + 4 trang tĩnh |
| `up -d` | ba container lên bằng image mới |
| Smoke test | `/api/v1/public/site-config` trả `success:true` |

Không bước nào nạp nội dung, vì **đường ống chưa bao giờ có bước ấy** — và điều đó đúng thiết kế: bộ seed là 5 bài **sao chép nguyên văn từ báo ngoài**, cố ý làm script phải gõ tay thay vì migration (`deploy/seed/README.md`). Cái thiếu không phải một bản vá, mà là **một đường chạy tay chưa ai dựng**.

📌 Hình dạng cần nhớ: *"đã chạy xong" và "đã có tác dụng" là hai chuyện khác nhau.* Cả 11 bước đều đúng với việc chúng nhận làm; không bước nào nhận làm việc còn thiếu.

#### Smoke test không phân biệt được cổng có nội dung với cổng rỗng

`/api/v1/public/site-config` chứng minh chặng `nginx → public-web → app → postgres` thông suốt. Nó **không** nói gì về việc có bài nào không — đúng luật 9. Đây là giới hạn đã biết, ghi lại để lần sau không ai đọc "smoke test xanh" thành "cổng đã đủ nội dung".

#### Bản vá

`seed-staging.yml` — **`workflow_dispatch` duy nhất**, hai bước:

- `chay-thu` (mặc định) in ra sẽ ghi gì mà không ghi; `nap-that` mới nạp
- ô xác nhận phải gõ đúng `nap-noi-dung-staging` — cùng tinh thần Restore UI (§7.3)
- **không có tham số môi trường**: workflow chỉ biết bộ `STAGING_*`, nên không có đường nào kể cả gõ nhầm trỏ nó sang production. Một danh sách chọn *staging/production* chính là cú nhấp sai đang tránh
- không chép lại `deploy/seed/` — CD đã rsync rồi; hai đường cho cùng một tệp thì sẽ có ngày lệch

`SeedNeverAutomaticTest` (5 bài) giữ ràng buộc, vì tới giờ nó chỉ sống trong chú thích:

| Làm hỏng có chủ đích | Kết quả |
|---|---|
| gắn `seed.sh` vào bước triển khai của CD | 1 đỏ |
| thêm `push:` cho workflow seed | 1 đỏ |
| đổi sang secret `PROD_*` | 1 đỏ |
| gỡ ô xác nhận | 1 đỏ |
| khôi phục | 5 xanh |

Bài thứ 5 neo bốn khẳng định **phủ định** kia vào một thứ có thật — xoá `deploy/seed/` đi thì cả bốn xanh trọn vẹn mà không canh gì.

---

### §10.47. CI đỏ ở dòng đầu tiên vì tải hụt Maven — và thông báo trỏ vào một bước chưa từng chạy (25/8/2026)

```
Run ./mvnw -B -ntp spotless:check checkstyle:check
wget: Failed to fetch .../apache-maven-3.9.9-bin.zip
Error: Process completed with exit code 1
```

Đo lại URL ngay sau đó: **HTTP 200**, tải một byte đầu trả **206**. Nghĩa là URL vẫn sống — một lượt chập mạng trên runner, không phải lỗi mã.

#### Nhưng nó lộ ra ba chỗ mong manh có thật

| # | Chỗ mong manh | Hệ quả |
|---|---|---|
| 1 | `cache: maven` của `setup-java` **chỉ** đệm `~/.m2/repository` | bản phân phối Maven ở `~/.m2/wrapper/dists` **không** được đệm → mỗi lượt CI tải lại ~9 MB |
| 2 | `mvnw` tải bằng `wget` **không có thử lại** | một lượt chập là CI đỏ, phải bấm chạy lại tay |
| 3 | Lượt tải nằm chung bước với lệnh build | thông báo lỗi mang tên `spotless:check` — **một bước chưa từng được chạy** |

Chỗ thứ ba đáng nhớ nhất: nó là **luật 22** ở dạng thuần túy — *dòng đáng chú ý nhất nằm trước thứ được báo là lỗi*. Ai đọc lướt sẽ đi tìm lỗi định dạng mã nguồn.

#### Bản vá — hai thứ độc lập, cố ý không gộp

- **Đệm `~/.m2/wrapper`**, khoá theo hash của `maven-wrapper.properties` → từ lượt thứ hai không tải nữa
- **Tách lượt tải thành bước riêng, thử lại 3 lần** (10s/20s/30s) → lượt đầu chập thì tự khỏi, và hỏng thật thì hỏng dưới đúng tên của nó

Bộ đệm không thay được vòng thử lại: lượt đầu, và mọi lượt sau khi đổi phiên bản wrapper, vẫn phải tải thật.

Vòng thử lại kiểm bằng `mvnw` giả, cả ba đường:

| Kịch bản | Kết quả |
|---|---|
| hỏng cả 3 lượt | 3 cảnh báo → **thoát 1** |
| hỏng lượt 1, đạt lượt 2 | thoát 0, không thử lượt 3 |
| đạt ngay | thoát 0, không thử lại lần nào |

`MavenWrapperCiTest` (2 bài) áp cho **mọi** workflow gọi `./mvnw`, kiểm chứng ngược:

| Làm hỏng có chủ đích | Kết quả |
|---|---|
| gỡ bước đệm ở `ci.yml` | 1 đỏ |
| gỡ bước đệm ở `security-scan.yml` | 1 đỏ |
| chuyển bước đệm xuống **sau** lượt gọi đầu tiên | 1 đỏ |
| gỡ vòng thử lại | 1 đỏ |
| khôi phục | 2 xanh |

Kịch bản thứ ba là chỗ dễ sai nhất: một bước đệm đặt sai vị trí vẫn **có mặt**, vẫn xanh trong mắt phép kiểm ngây thơ, mà đệm khi đã tải xong thì không đệm gì cả.

---

### §10.48. ⚠⚠ Script vận hành gọi `docker compose` là hỏng — kể cả lệnh chỉ đọc; và một trong hai chỗ là bản chụp trước triển khai (25/8/2026)

Lượt `nap-that` hỏng ở bước đầu:

```
→ [1/3] Đẩy ảnh lên MinIO
error while interpolating services.app.image: required variable APP_IMAGE is missing a value
docker: no name set for network
exit 125
```

#### Vì sao một lệnh chỉ đọc cũng hỏng

`seed.sh` tìm mạng của MinIO bằng `dc ps -q minio`. Compose **nội suy toàn bộ tệp** trước khi trả lời bất cứ câu hỏi nào — kể cả `ps -q`. Mà `compose.prod.yml` khai ba tag image ở dạng bắt buộc:

```yaml
image: ${APP_IMAGE:?Thiếu APP_IMAGE - workflow deploy phải export biến này}
```

Ba biến ấy **cố ý không nằm trong `.env`** — workflow triển khai `export` chúng ngay trước khi gọi compose, vì ghim một phiên bản image vào đĩa máy chủ là đúng thứ luồng đề bạt tránh (xem `MIEN_TRU` trong `ComposeEnvCompletenessTest`). Nên **mọi script chạy ngoài lượt triển khai đều thiếu chúng**.

Tái hiện tại chỗ, đúng thông báo của máy chủ:

```
$ docker compose --env-file /tmp/min.env -f compose.prod.yml ps -q minio
error while interpolating services.app.image: required variable APP_IMAGE is missing a value
→ mã thoát 1, kết quả trả về: RỖNG
```

Chuỗi rỗng ấy đi thẳng vào `--network ""` → `docker: no name set for network`. `set -e` không cứu được: lượt hỏng nằm trong `$( )` làm **đối số**, nên nó chỉ biến thành chuỗi rỗng rồi đi tiếp.

#### ⚠⚠ Chỗ thứ hai nặng hơn nhiều

Bài kiểm mới chỉ ra `pre-deploy-dump.sh` cùng lỗi, và ở đó nó biểu hiện thành:

```
✗ Postgres không trả lời — DỪNG. Không deploy khi chưa chụp được CSDL.
```

CSDL **hoàn toàn khoẻ**. Câu lệnh `dc exec -T postgres pg_isready` hỏng vì thiếu ba biến image, và nhánh `if !` diễn giải mọi lượt hỏng thành "CSDL không trả lời".

Đây là **bản chụp trước triển khai** — điểm quay lui *duy nhất* khi migration làm hỏng dữ liệu, vì dự án cố ý không có PITR (§6.5). `deploy-prod.yml` gọi nó qua heredoc **có trích dẫn** (`<<'REMOTE'`), tức không truyền biến image nào. Nghĩa là **lượt deploy production đầu tiên sẽ dừng ngay tại đây**, với một thông báo cử người đi cứu một CSDL không hề ốm.

Production chưa dựng nên chưa ai gặp. Đúng luật 7: *cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai*.

#### Bản vá

`deploy/lib/docker-svc.sh` — hỏi bằng **nhãn `com.docker.compose.*`** mà chính compose gắn lên container lúc tạo. Hỏi thứ đang chạy, không hỏi tệp mô tả nó.

Kiểm bằng container thật gắn đúng nhãn:

| Phép thử | Kết quả |
|---|---|
| tìm container `postgres` / `minio` | ✓ ra đúng id |
| lấy tên mạng của `minio` | ✓ `thu-songnhue` |
| `docker exec` vào container tra được | ✓ chạy được lệnh |
| service không tồn tại | **dừng hẳn, thoát 1** — không trả chuỗi rỗng |
| đường cũ `docker compose ps -q minio` | **thoát 1, trả rỗng** |

Điểm mấu chốt của bản vá không chỉ là "tra được", mà là **hỏng thì dừng hẳn**: chuỗi rỗng đi tiếp chính là cách lỗi cũ ngụy trang.

`ScriptDockerLookupTest` (3 bài), kiểm chứng ngược:

| Làm hỏng có chủ đích | Kết quả |
|---|---|
| trả `seed.sh` về `dc()` | 1 đỏ |
| trả `pre-deploy-dump.sh` về `dc()` | 1 đỏ |
| không script gọi nào dùng `container_cua` | 1 đỏ *(xem dưới)* |
| ba biến image thành `${X:-latest}` | 1 đỏ |
| khôi phục | 3 xanh |

📌 Kịch bản thứ ba **lọt ở bản đầu**: bài neo đếm mọi `.sh` trong `deploy/`, mà chính tệp **định nghĩa** `container_cua` cũng là một `.sh` ở đó — nó tự đếm mình. Câu đang hỏi là *"có ai DÙNG không"*, không phải *"có ai viết ra nó không"*. Đã loại `deploy/lib/` khỏi phép đếm. Lại thêm một lần lượt kiểm chứng ngược là thứ duy nhất phát hiện ra.

---

### §10.49. ⚠⚠ `minio-init` chưa từng chạy — staging chạy suốt với MinIO không có bucket nào (25/8/2026)

Bản vá §10.48 chạy đúng: lượt seed qua được chỗ tra mạng, kéo được image `mc`, rồi hỏng ở dòng cuối cùng:

```
mc: <ERROR> Failed to copy … Bucket `songnhue-media` does not exist.
```

#### Nguyên nhân — một service tồn tại mà không nằm trên đường nào

`compose.prod.yml` **có** service `minio-init`: tạo ba bucket, bật versioning cho bucket audit, tạo tài khoản dịch vụ hạn chế cho ứng dụng. Nó khai `depends_on: minio`.

Nhưng **không service nào depends_on nó**, còn workflow triển khai chỉ gọi:

```
docker compose … up -d app admin-app public-web nginx
```

`postgres` và `minio` lên theo `app`. `minio-init` thì không bao giờ chạy. Hỏi thẳng compose bằng `--dry-run` với đúng lệnh ấy:

| | Container compose sẽ tạo |
|---|---|
| **trước bản vá** | admin-app · app · minio · nginx · postgres · public-web |
| **sau bản vá** | admin-app · app · minio · **minio-init** · nginx · postgres · public-web |

#### Phạm vi thật của lỗi rộng hơn bộ seed rất nhiều

Không bucket nào tồn tại, và ứng dụng không có tài khoản dịch vụ. Nghĩa là trên staging:

- **mọi lượt tải tệp lên** từ giao diện quản trị đều hỏng
- **mọi bản kết xuất báo cáo** hỏng
- **mọi lượt kết xuất audit** hỏng

Và **không bước nào báo sai**. Smoke test chỉ hỏi `/api/v1/public/site-config` — nó không đi qua MinIO. Lỗi lộ ra lần đầu qua một dòng của `mc`, khi nạp nội dung seed, tức hoàn toàn tình cờ.

#### Bản vá — đặt ràng buộc ở chỗ cần nó

Cách chữa hời hợt là thêm `minio-init` vào dòng `up -d`. Nó vá đúng một service, và service thứ mười một thêm vào sau này lại mồ côi y như thế.

Cách chữa đúng: **`app` `depends_on` `minio-init` với `service_completed_successfully`**. Thứ cần bucket là `app`, nên chính `app` phải là nơi ghi điều đó — luật 12. `service_started` không đủ: `minio-init` chạy xong rồi thoát, nên app có thể lên trước lúc bucket kịp tạo, và lỗi thành lúc có lúc không tuỳ máy nhanh chậm.

#### `NoOrphanServiceTest` — canh đồ thị, không canh danh sách

Bài kiểm hỏi câu tổng quát: *có service nào không nằm trên đường nào không?* Một service được coi là có đường chạy khi: (1) nằm trong `up -d` hoặc tới được từ đó qua `depends_on`; (2) được gọi bằng `run --rm <tên>` (cách `migrator` chạy); (3) có khai `profiles:` (cách `certbot` chạy).

Viết **trước** khi vá, và nó đỏ đúng một phát hiện: `["minio-init"]`.

| Làm hỏng có chủ đích | Kết quả |
|---|---|
| gỡ `minio-init` khỏi `depends_on` của app | **2 đỏ** |
| hạ xuống `service_started` | 1 đỏ |
| thêm một service mồ côi mới | 1 đỏ |
| thêm service mồ côi **có `profiles:`** | **0 đỏ** — đúng, không báo oan |
| khôi phục | 2 xanh |

Kịch bản thứ tư quan trọng ngang ba kịch bản kia: một bộ canh hay báo oan sẽ bị người ta tắt đi.

📌 Cần kiểm lại sau lượt deploy tới: **tải tệp lên từ giao diện quản trị staging** — đó là chức năng chưa từng chạy được, không phải chức năng vừa hỏng.

---

### §10.50. Dọn đường ống triển khai: bộ seed vào chuỗi migration, hai workflow gộp làm một, bỏ vòng tra image (25/8/2026)

Bảy sự cố liên tiếp (§10.42 → §10.49) đều nằm trên **một** đường: nạp nội dung cho staging. Không bản vá nào sai. Cái sai là **hình dạng**: đường ấy gồm 6 khâu (đọc `.env` → tra container → tra mạng → bucket → khoá → byte) và **chỉ chạy khi có người bấm nút**, nên mỗi lượt bấm chỉ lộ ra đúng khâu kế tiếp. Lượt vá thứ tám sẽ lộ ra khâu thứ tám.

> ⛔ Bài học chung: **một đường chỉ chạy khi được bấm là một đường chưa được kiểm.** Cách chữa không phải vá tiếp mà là đặt nó lên đường mà mỗi lượt triển khai đều đi qua.

#### 1. Bộ seed: script bấm tay → migration Flyway

Quyết định của chủ dự án (25/8): chấp nhận phương án migration; chuyện bản quyền của 5 bài chép lại **không còn là ràng buộc**. Lập luận cũ ("Flyway chạy ở mọi môi trường nên không được đưa seed vào") vì thế mất vế thứ nhất, nhưng **vế thứ hai còn nguyên và nặng hơn**: migration seed mở đầu bằng lệnh **xoá bài**, và chạy trên production nghĩa là xoá nội dung thật của Công ty. Nên cổng chặn không bỏ được, chỉ đổi chỗ.

**Chặn ở LOCATION, không chặn trong tệp SQL.** Tệp seed nằm ở `classpath:db/seed/portal`, ngoài `spring.flyway.locations` mặc định; mặc định là `classpath:db/seed/none` — một thư mục **có thật** và cố ý không có migration nào. Production không đặt `SEED_LOCATION` nên Flyway ở đó **không nhìn thấy** tệp: không phải "chạy rồi không làm gì", mà là không tồn tại.

Vì sao không gate bằng một câu `IF` trong chính tệp SQL: một cổng chặn nằm bên trong thứ nó chặn thì chỉ cách production đúng một lỗi gõ. Vì sao thư mục `none` phải có thật: Flyway trỏ vào location không tồn tại là hành vi **tuỳ phiên bản** (cảnh báo hay dừng), và một cổng chặn chỉ đúng "tuỳ phiên bản" thì không phải cổng chặn.

⚠ Đánh đổi đã biết: staging đã bật thì phải **giữ bật**. Gỡ location sau khi migration vào `flyway_schema_history` làm `validate` đỏ với *"applied migration not resolved"*. Muốn thôi seed thì dựng lại CSDL.

#### 2. Byte của ảnh: `minio-init`, và thứ tự đặt ở compose chứ không ở workflow

SQL không đẩy được byte. Byte đi qua `minio-init` — service đã nằm sẵn trên đường triển khai — bằng `mc cp --recursive` từ `deploy/seed/media/`.

**Bố cục thư mục CHÍNH LÀ khoá đối tượng**: tệp nằm ở `deploy/seed/media/<storage_key>`, nên lệnh copy không viết cứng tiền tố `seed/portal/` ở đâu cả. Một tiền tố viết ở hai nơi là một tiền tố sẽ lệch.

Thứ tự byte-trước-hàng đặt bằng `migrator: depends_on: minio-init (service_completed_successfully)`, **không** bằng thứ tự dòng lệnh trong workflow. Lý do là luật 12: migration ghi hàng `attachments` *khẳng định* byte đã tồn tại, nên ràng buộc thuộc về nơi lời khẳng định được viết ra. Hệ quả thực dụng: `docker compose run --rm migrator` gõ tay lúc chữa cháy vẫn ra đúng thứ tự.

#### 3. Xoá bài: canh theo QUAN HỆ, không theo danh sách slug

`DELETE FROM articles` trần **không chạy được**: `menu_items.article_id` tham chiếu `articles(id)` không khai `ON DELETE` — tức RESTRICT — nên nó dừng giữa chừng vì lỗi khoá ngoại, **sau khi đã xoá được một phần**. Vị từ chốt:

```sql
DELETE FROM articles a
 WHERE NOT EXISTS (SELECT 1 FROM menu_items m WHERE m.article_id = a.id);
```

Nó tự bảo vệ 4 trang tĩnh do `V202608191021` sở hữu, và vẫn đúng khi có trang tĩnh thứ năm — một danh sách slug viết cứng thì lần thêm ấy sẽ làm gãy menu, im lặng.

#### 4. Hai bộ canh, và cả hai đều chạy thật

| Bài kiểm | Canh gì | Kiểm chứng ngược |
|---|---|---|
| `SeedGateTest` (9 bài) | seed không nằm trong `db/migration/**` · mặc định là thư mục rỗng có thật · prod để trống cả hai biến · **`SEED_LOCATION` và `SEED_MEDIA_DIR` không được bật lệch** · `size_bytes`/`sha256` trong SQL khớp **byte thật trên đĩa** · không có byte mồ côi · khối xoá có vị từ menu | 3 kịch bản: sai băm → 1 đỏ · sai khoá → đỏ "không có tệp" · sai kích thước → 1 đỏ |
| `SeedPortalMigrationTest` (2 bài) | chạy **chính tệp SQL** trên **lược đồ thật** bằng vai trò `songnhue_owner`, rồi `ROLLBACK` | dựng sẵn một bài "rác" trước khi chạy — nếu không thì *"xoá đúng"* và *"không xoá gì"* cho ra cùng kết quả (luật 9) |

⚠ `SeedGateTest` đối chiếu SQL với **byte thật**, không với `images.json`: hai tệp cùng sinh ra từ một nguồn thì cả hai cùng sai vẫn xanh.

⚠ Bản đầu của bài canh khối xoá **đỏ oan**: nó quét văn bản và khớp trúng cụm `DELETE FROM articles` nằm trong khối chú thích giải thích *vì sao không được viết thế*. Phải bỏ chú thích trước khi tìm lệnh — luật 2, lần thứ n.

#### 5. Hai workflow triển khai gộp thành một thân chung

`deploy-staging.yml` (333 dòng) và `deploy-prod.yml` (277 dòng) trùng nhau ~85%. Ba lần sửa gần nhất đều phải sửa hai chỗ, và **§10.44 chỉ được sửa ở một chỗ trong bản đầu** — đúng kiểu trôi mà `compose.staging.yml` tránh bằng cách `include` `compose.prod.yml`.

Nay: `deploy.yml` (`workflow_call`) giữ toàn bộ thân; hai tệp kia chỉ còn phần khác nhau thật sự — cổng chặn đầu vào, tệp compose, bộ secret. Secret **truyền vào từ caller**, nên đường staging không chạm nổi `PROD_*` dù gõ nhầm input.

⚠ Ràng buộc mới của kiểu gọi chung: reusable workflow **không tự cấp quyền cho mình được** — token bị chặn trên bởi quyền của job gọi. Khai `packages: write` ở thân chung là chưa đủ. `GhcrLookupAuthTest` nay canh cả vế caller.

#### 6. Bỏ vòng quét 50 ứng viên — bằng cách gắn tag bù ở CI

Job `Gắn tag SHA cho image không đổi` chạy cuối CI: image nào lượt này không dựng lại thì gắn thêm tag `<sha-mới>` lên **đúng digest cũ** (`imagetools create`, không dựng lại một byte nào).

📌 **Không mâu thuẫn với `docs/cicd.md` §2.1.** §2.1 cấm *đóng gói lại* phần không đổi vì thế là tạo ra một image chưa ai thử. Ở đây digest không đổi — chỉ thêm một cái tên.

Đổi lại, bước tra image rút từ `50 commit × 3 image` xuống ba lượt tra thẳng, và **hai bản vá trước đó tự tiêu**: vòng quét nuốt stderr nên 403 và 404 cho ra cùng thông báo (§10.43), nên đã phải thêm một bước tra thử riêng chỉ để phân biệt hai thứ ấy. Bỏ vòng quét là bỏ cả hai.

#### 7. Ba thứ nữa đổi cùng lúc, mỗi thứ bịt một lỗ đã trả giá

* **Smoke test hỏi ba câu** (§10.45): thêm *cổng có ≥ 1 bài* và *`GET /files/<id>` trả `image/*`*. Câu thứ ba là phép kiểm **duy nhất** chứng minh MinIO có byte — điều mà `deploy/seed/README.md` từng ghi là "phải làm bằng trình duyệt". Nay nó chạy ở mỗi lượt deploy.
* **`pg_dump` trước triển khai chạy ở cả staging**. Trước đó chỉ production có, sai ở hai vế: migration được thử **lần đầu** ở staging; và một đường sao lưu chỉ đi thử đúng lúc production cần là đường **chưa từng được thử**.
* **`NoOrphanServiceTest` tự tìm workflow** thay vì đọc danh sách viết cứng. Khi hai tệp gộp lại, danh sách cũ trỏ vào hai tệp không còn lệnh compose nào và bài báo *toàn bộ 8 service đều mồ côi*. Lần này đỏ ầm ĩ nên thấy ngay; lần sau có thể là kiểu ngược lại — tập rỗng, xanh trọn vẹn (luật 7).

#### 8. Đã bỏ những gì, và bài học đi đâu

| Bỏ | Dòng | Vì sao |
|---|---|---|
| `seed-staging.yml` · `deploy/seed/seed.sh` · `SeedNeverAutomaticTest` | 424 | cơ chế chúng phục vụ không còn |
| `MavenWrapperCiTest` | 139 | canh **văn bản YAML** cho một lượt hụt mạng; các bước đệm/thử-lại trong workflow vẫn giữ |
| 2/4 bài của `GhcrLookupAuthTest` | ~110 | canh vòng quét và biến `DOCKER_CONFIG` — nay không workflow nào còn |
| trùng lặp hai workflow triển khai | ~150 | gộp thành thân chung |

📌 **Bài học không nằm trong chú thích của cơ chế bị xoá** — nó nằm ở §10.42 → §10.49, chỗ của nó. Đó là điều cho phép đường ống co lại mà không mất trí nhớ.

---

### §10.51. Triển khai theo digest, quay lui tự động, và một lượt diễn tập chạy được ở máy (25/8/2026)

Ba thứ đi cùng nhau vì chúng trả lời ba câu khác nhau của cùng một nỗi lo: *lượt triển khai này đưa cái gì lên, và nếu sai thì quay về đâu?*

#### 1. Digest thay cho tag

Bước xác định image giải `:<sha>` thành `@sha256:…` rồi triển khai bằng digest.

Tag là một **cái tên**, và tên thì gán lại được — kể cả bởi chính đường ống này (job `Gắn tag SHA cho image không đổi` gán tag mới lên digest cũ ở mỗi lượt CI). Giữa lúc workflow tra và lúc máy chủ `pull`, một lượt CI khác có thể đã trỏ tag đi nơi khác. Xác suất thấp, nhưng hậu quả là **một môi trường chạy thứ không ai truy ra được** — loại lỗi không điều tra được sau đó, vì bằng chứng đã bị ghi đè.

⚠ Đòi buildx ≥ 0.10 (`--format '{{.Manifest.Digest}}'`). Bước này khẳng định chuỗi nhận về khớp `sha256:…` rồi mới đi tiếp — một giá trị rỗng đi lọt sẽ thành `image@` và hỏng ở chỗ khó đoán.

#### 2. Quay lui tự động — và giới hạn của nó phải ghi ngay tại chỗ

Trước khi đổi, workflow hỏi **máy chủ** *cái gì đang chạy* (`docker inspect` trên container), không đọc tệp compose: câu đang hỏi là về thứ đang chạy, không phải về thứ được mô tả. Smoke test đỏ → dựng lại ba image cũ → hỏi lại câu 1 của smoke test.

⛔ **Đây là quay lui về MÃ NGUỒN, không phải về DỮ LIỆU.** `migrator` chạy trước và migration là một chiều: nếu nó đã đổi lược đồ thì mã cũ có thể không chạy nổi trên lược đồ mới. Câu ấy viết trong chính khối `run:` của bước quay lui, chứ không chỉ trong tài liệu — vì đó là chỗ người ta đọc lúc hoảng, và "đã có rollback tự động" là một lời trấn an rất dễ tin.

Hai chi tiết nhỏ nhưng cố ý:

* `failure()` chứ không `always()` — `always()` chạy cả khi người ta bấm huỷ, mà quay lui giữa chừng còn tệ hơn để nguyên.
* Lượt deploy đầu tiên chưa có container nào → `co_du=false`, bước tự bỏ qua kèm cảnh báo, thay vì dựng lại từ chuỗi rỗng.
* Lượt quay lui chỉ hỏi **câu 1** của smoke test. Câu 2 và 3 nói về *nội dung*, mà nội dung có thể đã bị migration của lượt hỏng đổi — đỏ ở đó không nói gì về việc bản cũ có sống lại hay không.

#### 3. `make rehearse` — chỗ đầu tiên đường triển khai được đi thử ngoài VPS

Đây là bản vá cho **nguyên nhân gốc** của cả chuỗi §10.42 → §10.49: đường triển khai chưa bao giờ có cách thử nào ngoài một lượt deploy thật.

`make ci-local` không đụng tới compose, `minio-init`, hay thứ tự khởi động. `compose.local.yml` là **đường build**, khác hẳn đường deploy. Nên "xanh ở máy" chưa bao giờ nói gì về chúng — và bảy sự cố liên tiếp là hệ quả trực tiếp.

`deploy/rehearse.sh` chạy **đúng `compose.staging.yml`** và **đúng lệnh CD gõ** (`run --rm migrator`, không phải `up -d`), rồi hỏi ba câu:

| | Câu hỏi | Bắt được lỗi nào |
|---|---|---|
| 1 | Bucket có được tạo không? | §10.49 — `minio-init` mồ côi |
| 2 | Migration seed ghi đủ hàng không? | §10.50 — cổng chặn location, thứ tự migrator |
| 3 | **Mỗi `storage_key` trong CSDL có byte thật trong MinIO không?** | chỗ hỏng CÂM — và là câu **không bài kiểm JUnit nào trả lời được** |

`compose.rehearse.yml` đổi đúng **ba** thứ, mỗi thứ là một điều lượt diễn tập không chứng minh được: đổi `container_name` (tránh đụng stack local) và thay hai bind mount vào đường tuyệt đối của máy chủ bằng volume có tên. ⛔ Hệ quả trực tiếp: **diễn tập không kiểm được quyền thư mục** — thứ đã làm container app crash trên VPS (quyền đúng: `deploy-guideline.md` §2.5). Danh sách "không nói gì về" in ra ở cuối mỗi lượt chạy, không giấu trong tài liệu.

#### Đã đo, cả hai chiều

| Lượt | Kết quả |
|---|---|
| bản hiện tại | 33 migration áp dụng (tới `202608251100 - seed portal content`) · ✓ bucket · ✓ 5 bài + 4 đính kèm · ✓ 4/4 khoá có byte |
| **làm hỏng có chủ đích**: `SEED_LOCATION=` (rỗng) | ✗ câu 2 → *0 bài (cần 5)* · thoát 1 · thông báo trỏ đúng biến |

Lượt thứ hai đồng thời là **bằng chứng cổng chặn production hoạt động**: không đặt `SEED_LOCATION` thì migration seed đơn giản là không tồn tại với Flyway.

⚠ Bản đầu của bài kiểm câu 3 in *"0 khoá không ra được byte"* rồi vẫn đỏ — một dòng không phân biệt được *"mọi khoá đều tốt"* với *"không có khoá nào để đối chiếu"* (luật 9). Đã tách thành hai nhánh.

⚠ `ScriptDockerLookupTest` bắt được bản đầu của chính `rehearse.sh`: luật cũ **cấm tuyệt đối** `docker compose` trong script. Luật ấy nay chính xác hơn — *gọi compose thì phải TỰ CẤP đủ ba biến image, và cấp TRƯỚC lượt gọi đầu tiên*. Đúng tinh thần §10.48 (compose nội suy toàn bộ tệp trước khi làm gì), và không phải một danh sách miễn trừ để người ta thêm tên vào.

### §10.52. ⚠⚠ Ảnh trên cổng chưa từng trả về được một byte nào — envelope bọc cả `byte[]` (25/8/2026)

**Triệu chứng.** Lượt triển khai staging đi qua hết: migrator chạy, 9 bài lên cổng, byte seed vào
MinIO (337 KiB, `mc` xác nhận). Câu 3 của smoke test đỏ:

```
curl: (22) The requested URL returned error: 500
Ảnh bìa 15509c57-… không ra được byte (content-type: 'application/json').
```

**Ba giả thuyết đầu đều sai, và sai theo cùng một kiểu.** Tôi lần lượt ngờ `SEED_MEDIA_DIR` không
có trong `.env`, rồi bucket lệch tên, rồi chứng chỉ TLS của `FILES_DOMAIN`. Cả ba đều là giả thuyết
về **hạ tầng**, vì lượt hỏng nằm trong một lượt triển khai. Cả ba bị bác bỏ bằng một lệnh. Thứ chấm
dứt việc đoán là dòng log thật:

```
java.lang.ClassCastException: ApiResponse cannot be cast to class [B
  at ByteArrayHttpMessageConverter.getContentLength(...:38)
  at HttpEntityMethodProcessor.handleReturnValue(...)
```

📌 `/var/log/songnhue` là bind mount ra host, nên log **sống sót qua cả lượt quay lui**. Đó là lý do
đọc được nguyên nhân sau khi container đã bị thay. Giữ nguyên tính chất này.

**Nguyên nhân gốc.** `ResponseEnvelopeAdvice.beforeBodyWrite` bọc **mọi** thân phản hồi dưới
`/api/v1` vào `ApiResponse`. Nhưng Spring chọn `HttpMessageConverter` theo **kiểu trả về của
handler**, trước khi advice chạy. `PublicPortalController.file()` khai `ResponseEntity<byte[]>` nên
được gán `ByteArrayHttpMessageConverter`; converter ấy ép kiểu thân về `[B` lúc tính
`Content-Length` → `ClassCastException` → 500, **sau khi** đã đọc xong toàn bộ byte từ MinIO
(603 ms trong log).

Advice đã từng phải chừa `StringHttpMessageConverter` ra một lần rồi. Đây là **lần thứ hai của đúng
một hình dạng**, và lần đầu được chữa bằng cách thêm một trường hợp riêng — nên lần thứ hai không
có gì chặn.

**Vì sao 565 bài kiểm không bắt được.** Endpoint này *có* bài kiểm:

```java
@DisplayName("⛔ Mã tệp không tồn tại trả 404, không lộ ra kho có gì")
http.getForEntity("/api/v1/public/files/00000000-0000-0000-0000-000000000000", …)
```

UUID không tồn tại → chỉ đi nhánh 404. **Nhánh trả byte — nhánh duy nhất hỏng — chưa ai đi qua.**
Luật 7 ở dạng thuần khiết: một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai. Và nó nằm ngay
cạnh một bài kiểm trông rất giống một bài kiểm đầy đủ.

**Bản vá — đổi danh sách cấm thành danh sách cho phép.**

```java
if (!AbstractJackson2HttpMessageConverter.class.isAssignableFrom(selectedConverterType)) {
    return body;
}
```

⛔ Cố ý **không** viết `if (body instanceof byte[]) return body;`. Đó là bắt theo từng loại dữ liệu,
và luôn có loại thứ tư lọt qua — `Resource`, `StreamingResponseBody`, SSE, protobuf (luật 24). Hỏi
"converter này có ghi được `ApiResponse` không" thì mọi loại về sau tự đi đúng đường mà không ai
phải nhớ.

**Phép kiểm — bốn bài, hai tầng, cả hai tầng đều đã kiểm chứng ngược (luật 10).**

| Bài | Ở đâu | Khẳng định |
|---|---|---|
| `anhKhongBiBocEnvelope` | `core` | `ResponseEntity<byte[]>` trả **đúng mảng byte**, không phải JSON |
| `resourceCungKhongBiBoc` | `core` | `Resource` cũng vậy — chứng minh phép chừa canh **converter**, không canh `byte[]` |
| `jsonVanBiBocNhuCu` | `core` | kiểm chứng ngược: `return body;` vô điều kiện cũng làm hai bài trên xanh, nhưng gỡ envelope khỏi toàn hệ |
| `anhCongKhaiTraNguyenByte` | `app` | **đường production**: tải ảnh lên MinIO thật → `GET /api/v1/public/files/{id}` qua HTTP thật → so byte với bản gốc |

Gỡ bản vá ra chạy lại: `core` đỏ 2 bài với đúng `ApiResponse cannot be cast to class [B`, `app` đỏ
1 bài với đúng `GET /api/v1/public/files/… → 500`. Cùng một dòng chữ với log staging.

⚠ Bài ở `app` xin `byte[].class` chứ không xin `String.class`: xin `String` thì `RestTemplate` tự
giải mã theo charset và một thân JSON sai vẫn "đọc được" — phép khẳng định sẽ không phân biệt được
hai trạng thái (luật 9).

**Điều đáng ghi nhận.** Câu 3 của smoke test — thêm ở §10.51 và bị nghi là thừa — là thứ duy nhất
bắt được lỗi này. Nó bắt vì nó hỏi **một câu mà không tầng nào khác hỏi**: "byte có ra khỏi hệ
thống được không". Đây là bằng chứng cho luật 5 theo chiều thuận, chứ không phải chiều nghịch.

**Nợ mở ra từ lượt này** (chưa làm, không chặn):

1. `minio-init` nuốt lỗi bằng `mc admin user add … || true` và `mc admin policy attach … || true` —
   tài khoản dịch vụ hỏng vẫn in `✓ Bucket và tài khoản dịch vụ sẵn sàng`.
2. `SEED_MEDIA_DIR` không mang thông tin gì: đường dẫn trong container đã bị bind mount ghim ở
   `/seed-media`. Nó chỉ nhắc lại "seed đang bật", tức là một công tắc thứ hai tồn tại chỉ để lệch
   với công tắc thứ nhất. Cho `minio-init` đọc thẳng `SEED_LOCATION` thì lệch thành **không biểu
   diễn được** — thay vì được canh bằng `SeedGateTest.haiVeKhongDuocLech`, bài kiểm soi **tệp mẫu
   trong repo** chứ không soi `/opt/songnhue/.env` đang chạy (luật 12).

### §10.53. ⚠⚠ Triển khai "theo digest" mà không ai đo lại — compose giữ container cũ, bản vá không bao giờ được nạp (25/8/2026)

**Triệu chứng.** Sau khi #35 vá `ClassCastException` và được merge, lượt triển khai staging **vẫn
đỏ ở đúng câu 3 với đúng ngoại lệ cũ**. Ba lần đọc log đầu tiên đều dẫn tới kết luận "bản vá không
hiệu quả" — sai.

**Bằng chứng chấm dứt tranh cãi.**

```
$ docker inspect songnhue-app --format '{{.Config.Image}} | {{.State.StartedAt}}'
ghcr.io/team-dev-qnt/songnhue/app:dev  |  2026-08-25T07:19:00Z
```

Container đang chạy được dựng **07:19 bằng tay**, không phải bởi lượt triển khai 07:25. Và trong log
runner:

```
07:25:57  Container ***-app Running     ← không phải "Recreated", không phải "Started"
07:25:59  Container ***-app Waiting
07:25:59  Container ***-app Healthy
```

**Nguyên nhân gốc.** Lượt triển khai làm đúng mọi việc *trừ việc cuối*:

| Bước | Chứng minh được gì |
|---|---|
| giải tag SHA → digest | digest tồn tại trên registry |
| `pull` | digest đã tới **đĩa máy chủ** |
| `up -d` in `Running` | compose **kết luận** không cần thay |
| tổng kết "Image (theo digest)" | workflow **tin** kết luận ấy |

Không dòng nào trả lời câu duy nhất quan trọng: **container đang chạy cái gì.** Bản vá nằm trong
image, image nằm trên đĩa, và không có gì nối hai thứ đó lại.

📌 Đây là luật 9 ở dạng đắt nhất, và là lần thứ hai trong ba ngày: bài canh "đi bằng HTTP/1.1" xanh
cả khi đã gỡ cấu hình, vì nó khẳng định thứ nghe có vẻ đúng thay vì **đo** thứ thật sự khác nhau
giữa hai trạng thái.

⚠ Và nó khớp với một hình dạng đã ghi ở §10.38/CLAUDE.md: **"xanh ở máy" và "xanh ở runner" không
phải bằng chứng** — nay thêm một mục nữa vào danh sách ấy: *"compose nói không cần thay"* cũng
không phải bằng chứng.

**Bản vá — hai vế trả lời hai câu khác nhau, cần cả hai.**

1. `up -d --force-recreate`. Triển khai là lúc **đặt** image đã khai vào container đang chạy — một
   mệnh lệnh, không phải gợi ý để compose cân nhắc.
2. Sau `up -d`, **đọc lại** ID ảnh của từng container đang chạy và so với ID ảnh của ref vừa triển
   khai; lệch là đỏ, kèm cả ba con số.

⛔ Vế 1 **không** thay được vế 2: nếu ref bị cấp sai — ví dụ `/opt/songnhue/.env` lỡ chứa
`APP_IMAGE` và `--env-file` thắng biến `export` — thì force cũng chỉ dựng lại đúng cái image sai.
`ComposeEnvCompletenessTest.MIEN_TRU` cấm ba biến ấy có mặt trong `.env` chính vì lý do này.

⚠ So bằng **ID ảnh**, không so chuỗi tag: `.Config.Image` giữ nguyên văn ref lúc tạo container, nên
hai ref khác nhau vẫn có thể là một ảnh, và `:dev` có thể đã trỏ sang ảnh khác từ lúc nào.

**Kiểm chứng — hai tầng, cả hai đã chạy ngược.**

*Hành vi*: trích nguyên vòng lặp ra khỏi workflow, chạy `bash -euo pipefail` với một `docker` giả ở
hai kịch bản. Container đúng image → 3 dòng ✓, exit 0. Container còn bản cũ → `::error::` kèm ref
đã khai / ID cần / ID đang chạy, exit 1. Vòng lặp **báo cáo trọn vẹn cả ba service** rồi mới thoát,
không dừng ở lỗi đầu (luật 11).

⚠ Lượt dựng bộ `docker` giả **đầu tiên xanh cả hai kịch bản** — nó đọc nhầm chỉ số đối số nên luôn
trả cùng một chuỗi cho cả hai vế so sánh. Chính bước chứng minh cũng suýt là một xanh giả, đúng như
§10.36 đã ghi. Sửa chỉ số rồi mới có hai kết quả khác nhau.

*Cấu trúc*: `DeployImageProofTest` — 5 bài, canh trên thân workflow **đã bỏ chú thích** (khối chú
thích trích dẫn nguyên văn các lệnh đang canh; tìm trên văn bản thô là xanh sau khi lệnh thật đã bị
xoá — đúng cách `SeedGateTest` từng khớp trúng một `DELETE FROM articles` trong lời giải thích).
Gỡ bộ canh khỏi workflow → 3/5 bài đỏ.

**Điều cần rút ra cho lần sau.** Khi một bản vá "không hiệu quả", câu hỏi đầu tiên không phải *"vá
sai chỗ à?"* mà là **"bản vá có thật sự đang chạy không?"** — §10.36 đã ghi đúng bài học này
(lượt kiểm chứng IDOR chạy trên jar cũ còn nguyên bản vá) và tôi vẫn đi lại đúng đường đó.

---

### §10.54. ⚠⚠ Trang chủ cổng chưa từng hiển thị dữ liệu thật sau một lượt triển khai — 14 bài viết bịa che một trang rỗng (25/8/2026)

**Triệu chứng người dùng báo.** *"Deploy đã thành công, nhưng tại sao nội dung seeding 5 bài viết vẫn chưa xuất hiện?"* — trong khi smoke test câu 2 của chính lượt deploy ấy in `✓ Cổng có 9 bài (cần ≥ 9)`.

Cả hai đều đúng. Đó là chỗ đáng sợ của vụ này.

#### Hai lỗi xếp chồng, và chúng che nhau

| | Lỗi | Hệ quả riêng lẻ |
|---|---|---|
| **A** | `next build` dựng sẵn trang chủ và ghi HTML vào image. Lượt build chạy ở CI, **không có backend** → `apiGet` nuốt lỗi, trả `null` → bản nướng KHÔNG CÓ BÀI NÀO | Trang chủ rỗng sau mỗi lượt deploy, cho tới khi ISR dựng lại |
| **B** | Bảy component của trang chủ có mảng `DEFAULT_*` viết cứng, trộn theo kiểu `articles.length >= 4 ? articles : [...articles, ...BIA]` | Mảng rỗng cho ra một trang **ĐẦY** |

**A** sinh ra trang rỗng. **B** làm trang rỗng trông đầy. Cộng lại: người dùng mở cổng ngay sau lượt deploy thấy một trang chủ hoàn chỉnh, đẹp, và **không có một dòng nội dung thật nào**. Vài phút sau ISR dựng lại, `curl` đo thấy 5/5 bài seed. Hai người nhìn cùng một URL ở hai thời điểm và thấy hai thứ khác nhau — không ai sai, và không có lỗi nào để đọc.

#### Quy mô của B

| Component | Bịa | Mức |
|---|---|---|
| `HomeHeroFeatured` · `HomeLatestNewsFeed` · `DirectiveDocumentsSection` · `PortalSidebar` | **19 bài viết** có tiêu đề, ngày đăng, lượt xem, slug | cao |
| `DirectiveDocumentsSection` | **4 văn bản có SỐ HIỆU và NGƯỜI KÝ** — `158/QĐ-SN`, `89/TB-SN`, "Chủ tịch Công ty", "Tổng Giám đốc" | **rất cao** — bịa văn bản pháp quy của doanh nghiệp nhà nước |
| `HydrologyQuickWidget` | **5 trạm + mực nước + một mức "Cảnh báo BĐ I"** gắn tên cống có thật (Hà Đông, Cầu Cung, Cổ Nhuế, Vân Đình, Đồng Quan), kèm chấm "live" nhấp nháy và dòng "Cập nhật trực tuyến" | **rất cao** — số liệu thủy văn bịa trên cổng của công ty thủy lợi |
| `AffiliatedUnitsLinks` | **8 xí nghiệp + địa chỉ + số điện thoại** | **rất cao** |
| `PortalSidebar` | 1 số **trực ban PCTT** ghi cứng — số người dân gọi khi có sự cố | **rất cao** |
| `SiteHeader` | menu dự phòng 10 mục, **3 mục trỏ tới chuyên mục không tồn tại** (`he-thong-cong-trinh`, `lich-van-hanh`, `pctt`) | cao — quảng cáo khu vực bấm vào là 404 |
| `HomeMediaGallery` | 4 ảnh **hotlink từ Unsplash** + 1 video YouTube gắn tiêu đề "Phóng sự … Sông Nhuệ" | cao — rò rỉ sang bên thứ ba, và `images.remotePatterns` đang để rỗng |
| `SiteFooter` | `site.footer.social.*` rơi về `https://facebook.com` · tên Công ty ghi cứng lần thứ hai | trung bình |
| `CategoryServicesGrid` | 4 chuyên mục; và khi có đủ 4 chuyên mục thật thì **vẫn mượn phần mô tả bịa** làm giá trị dự phòng | trung bình |

`CLAUDE.md` cấm đích danh chuyện này (*"⛔ Cấm seed dữ liệu công trình/thuỷ văn 'cho đẹp demo'"*), và luật 16 nói *"số 0 là một câu khẳng định"*. Bộ seed 5 bài còn có cổng chặn `SEED_LOCATION` nên production không nhìn thấy được; **19 bài này nằm trong bundle, production sẽ hiện y nguyên**.

#### Vì sao không bộ canh nào bắt được

Ba lớp phòng thủ cùng trượt, mỗi lớp một kiểu:

1. **`vitest.config.mts` cố ý loại component ra**, có ghi lý do: *"kiểm chúng cho tử tế cần cả backend… dựng một tầng mock nửa vời chỉ tạo ra thứ xanh mà không chứng minh gì"*. Lý lẽ đúng cho Server Component gọi API — nhưng `HomeHeroFeatured` là component thuần trình bày, nhận `articles` qua props. Một luật đúng bị áp quá rộng thành một vùng không ai soi.
2. **`siteContactConfig.test.ts` chỉ đọc HAI tệp** — `SiteFooter.tsx` và `SiteHeader.tsx` — trong khi 9 số điện thoại bịa nằm ở `components/home/` và `PortalSidebar.tsx`. Luật 12: bảo đảm đặt ở *nơi gọi* thay vì *chỗ dữ liệu đi qua*.
3. **Và ngay cả khi soi đúng tệp thì regex vẫn trượt.** Bản đang dùng đòi **đúng bốn nhóm số** vì được chỉnh cho `(024) 33.546.247`; nó không khớp `(024) 3382 4580` — dạng ba nhóm — tức toàn bộ 8 số của khối xí nghiệp. Đây là **lần thứ ba** cùng một regex phải nới ra vì không khớp dữ liệu thật đang chạy (luật 24, sau lần dấu chấm và lần chữ HOA).

Điểm 3 là điểm đắt nhất: nó chỉ lộ ra vì bản vá lần này bắt mỗi bộ canh phải **tự kiểm chứng với một mẫu vi phạm**. Nếu chỉ nới phạm vi tệp mà không có phép tự kiểm chứng ấy, bộ canh mới vẫn xanh và 8 số vẫn nằm nguyên.

#### Đường điều tra — và ba lượt tôi tự làm mình đi chệch

Tôi hỏi sai câu ba lần, và cả ba đều cùng một hình dạng: **tự đặt ra một phép đo không phân biệt được hai trạng thái** (luật 9).

1. Bảo người dùng đọc `.next/server/app/index.html` **trong container đang chạy**. Tệp đó là chỗ ISR **ghi đè lúc chạy** — nó không phân biệt được "nướng sẵn có nội dung" với "nướng rỗng rồi bị ghi đè". Kết quả trả về `✓ CÓ bài seed`, và suýt nữa đóng hồ sơ sai. Phép đo đúng là đọc **image** (`docker run --rm <img>`) — hoặc, như hoá ra, dựng lại ở máy và đọc `prerender-manifest`, thứ có sẵn trong repo từ đầu.
2. Đặt `connection()` ở `page.tsx`, rồi kết luận "xong" khi thấy `/` ra `ƒ` — nhưng lượt sửa chuyển nó sang `lib/api.ts` **chưa từng được nạp** (một `cd` hỏng làm `&&` ngắt mạch, python không chạy), nên `ƒ` ấy vẫn là công của bản vá cũ. Cả `✓ typecheck` lẫn bảng route đều xác nhận nhầm. Chính bài kiểm mới bắt được — đúng luật 10, lần thứ hai trong hai ngày.
3. Hai phép khẳng định của tôi đỏ oan vì **chú thích tôi vừa viết** có nhắc tên hằng số đang bị cấm — cùng cái bẫy `SeedGateTest` từng dính với `DELETE FROM articles` (luật 2). Bộ canh mới vì thế bỏ chú thích trước khi soi, và có một bài kiểm riêng cho chính bộ lọc bỏ chú thích ấy.

#### Bản vá

**Về A — đặt bảo đảm ở chokepoint, không ở từng route.**

`await connection()` đặt ở đầu `apiGetWithMeta` — chỗ **duy nhất** mọi lượt đọc API đi qua. "Đọc API mà vẫn bị dựng sẵn" trở thành điều không biểu diễn được (luật 12).

Bản đầu đặt ở `page.tsx`, và `sitemap.ts` lập tức chứng minh vì sao cách đó sai: nó đọc `getArticles` bằng một đường không ai để ý, nên sitemap trong image chỉ có **đúng một url**, host là `http://localhost:3000`. Chuyển về chokepoint thì `/`, `/_not-found` và `/sitemap.xml` cùng ra `ƒ` trong một lần, và ngoại lệ `force-dynamic` từng phải thêm cho sitemap **tự tiêu**.

⚠ Cố ý **không** dùng `dynamic = 'force-dynamic'`: nó hạ mặc định `fetch` xuống `no-store`, backend sẽ phải trả lời mọi lượt truy cập. Giữ ISR thì phải thêm `fetchCache = 'default-cache'` — hai công tắc phải nhớ cùng lúc (luật 14). `connection()` không đụng tới cache của `fetch`, nên `next.revalidate = 300` giữ nguyên: backend vẫn chỉ bị hỏi 1 lần / 5 phút, chỉ bước dựng React chạy theo request.

📌 Đây đúng là cái bẫy mà `next.config.ts` **đã ghi cho `rewrites()`** — *"Next gọi `rewrites()` lúc BUILD… bị nướng cứng vào image"*. Đã chữa cho `rewrites`, không ai nghĩ tiếp rằng **HTML prerender cũng bị ghim y hệt**; và không ai thấy, vì bộ dữ liệu bịa làm trang trông đầy.

**Về B — xoá sạch, thay bằng `EmptyBlock`.**

Ô nào chưa có nguồn thì nói thẳng là chưa có, kèm lý do (*"Mô-đun Quản lý dữ liệu thủy văn (MOD-03) chưa được đấu nối"*, *"Hệ thống văn bản điều hành là hệ thống riêng — cổng chỉ liên kết sang, không đồng bộ dữ liệu (CN-01.7)"*). Menu dự phòng của đầu trang rút về **một mục duy nhất** — trang chủ, tuyến đường do chính Next định nghĩa. Tên Công ty ở chân trang rơi về `SITE.name` thay vì một bản sao viết tay thứ hai.

#### Phép kiểm — 25 bài mới, mỗi bộ canh tự chứng minh nó bắt được vi phạm

`noFabricatedContent.test.ts` soi **toàn bộ `src/`** (không chỉ hai tệp), ba tầng chồng nhau:

1. **theo tên trường** — `slug` · `publishedAt` · `viewCount` · `waterLevel` · số hiệu văn bản · số điện thoại · email · địa chỉ. Mỗi hình dạng đi kèm **một mẫu vi phạm** và một bài khẳng định regex khớp được mẫu ấy. Chính phép tự kiểm chứng này phát hiện regex điện thoại cũ không nhận dạng ba nhóm.
2. **theo cấu trúc** — mọi `const X = [...]` có ≥3 object literal, bắt cả loại trường chưa ai nghĩ ra (luật 24). Danh sách cho phép có **đúng một** mục, kèm lý do.
3. **theo tên miền ngoài** — danh sách cho phép, chặn hotlink quay lại.

`noBuildTimePrerender.test.ts` canh chokepoint: `connection()` phải có, phải nhập từ `next/server`, phải nằm **trước** `fetch`, không route nào của cổng được dùng `force-dynamic`, không route nào khai `generateStaticParams`.

`siteContactConfig.test.ts` nay chỉ giữ hợp đồng "đọc từ cấu hình"; ba phép canh hình dạng đã **chuyển hẳn** sang bài soi toàn cây — xoá chứ không `it.skip`, vì một bài bị bỏ qua đọc như đã có coverage. Phép canh dự phòng cứng nới từ `company.*`/`??` sang mọi khoá `company.*`|`site.*` và cả `||`, và phân biệt **chuỗi viết cứng** với **giá trị tính ra** (`SITE.name`, `` `© ${năm} ${tên}` ``).

**Đo cả hai chiều** — gỡ đúng một dòng `await connection()` khỏi `lib/api.ts`:

| | `/` | `/_not-found` | `/sitemap.xml` |
|---|---|---|---|
| gỡ | `○` tĩnh | `○` tĩnh | `○` tĩnh |
| giữ | `ƒ` | `ƒ` | `ƒ` |

Bộ canh đỏ 2 bài kèm đúng dòng chẩn đoán. ⚠ Lượt gỡ đầu tiên **không** đo được gì: build chết ở `TS6133: 'connection' is declared but its value is never read` trước khi in bảng route — phải gỡ cả dòng `import` mới thấy `○`. Một lần nữa, bản hỏng phải được xác nhận là **đã nạp** thì phép kiểm chứng ngược mới nói lên điều gì (luật 10).

**Kết quả**: 222 test FE (142 admin + **80** cổng, trước là 55) · typecheck · eslint · `next build` đều xanh.

⭐ **Và đo một lần nữa ở đúng chỗ `make ci-local` không với tới được.** Hai job chỉ sống trên
runner (quét CVE · đóng gói image) chạy trên cây checkout sạch, không có `.env.local`; mọi lượt
build ở máy đều nạp tệp ấy. Nên bản vá này được kiểm thêm bằng một lượt `docker build` mang
**đúng đối số của `ci.yml`** (`NEXT_PUBLIC_API_BASE_URL=` và `NEXT_PUBLIC_SITE_URL=` cùng để
rỗng) — và đo *bên trong image sinh ra*:

```
┌ ƒ /                 ← trước bản vá: ○, kèm index.html chứa 19 liên kết /bai-viet/ bịa
├ ƒ /_not-found
├ ƒ /sitemap.xml
└ ○ /robots.txt       ← đúng, nó không đọc API
/app/public-web/.next/server/app/index.html → không tồn tại
```

Đây là hình dạng đã trả giá ở §10.38: một biến môi trường rỗng là trạng thái chỉ runner mới có.
Kiểm bản vá bằng `npm run build` ở máy là kiểm một môi trường khác với môi trường sẽ chạy.

#### Điều cần rút ra cho lần sau

**Một bộ dữ liệu dự phòng "cho giao diện luôn sống động" là một cơ chế biến lỗi thành im lặng.** Nó không làm dịu một sự cố — nó xoá dấu vết của sự cố. Trang chủ này hỏng từ ngày dựng, đi qua một lượt nghiệm thu WS-21 và một lượt deploy staging, và thứ duy nhất tố cáo nó là 14 dòng log prefetch tới những slug không tồn tại — thứ chỉ đọc được vì đang truy một lỗi khác.

⛔ **Và nguồn của nó là một tài liệu, không phải một lượt code cẩu thả.** `docs/web-refactor.md`
— bản kế hoạch tái cấu trúc giao diện cổng — kê đích danh những thứ về sau thành dữ liệu bịa:
*"Lưới thẻ các trạm đầu mối chính (Hà Đông, Cầu Cung, Cổ Nhuế, Vân Đình, Đồng Quan…)"*, *"Số hiệu
(VD: `158/QĐ-SN`)"*; và Bước 1 của lộ trình ghi thẳng: *"xây dựng các khối với **Mock data dự
phòng (Fallback an toàn** khi API backend chưa cấp đủ endpoint chuyên biệt)"*.

Người viết component làm **đúng** những gì được giao. Nên vá mã mà để tài liệu nguyên là để nguyên
cỗ máy sinh ra lỗi: lượt sau đọc nó sẽ dựng lại y hệt. Tài liệu đã bị xoá (kế hoạch đã thực hiện
xong), điều cấm chuyển vào `docs/ui-styles.md` §4.4 — nơi người sắp dựng một khối trang chủ thật
sự sẽ đọc.

Luật 16 đã nói *"ô số liệu chưa có nguồn phải trả rỗng kèm lý do"*. Vụ này bổ sung vế còn thiếu: **ràng buộc ấy phải ép ở component, không ép bằng lời dặn** — và phải có một bộ canh soi **toàn cây**, vì "ở đây thì chưa có nguồn" là câu người viết component nào cũng tự thấy mình là ngoại lệ.

---

### §10.55. `minio-init` đo thay vì khai báo, và bộ seed rút về một công tắc duy nhất (26/8/2026)

Hai nợ mở ra từ §10.52, đóng cùng lượt vì chúng nằm trên **cùng một đoạn 20 dòng** của `minio-init` và cùng một hình dạng: *một câu khẳng định không phân biệt được hai trạng thái*.

#### T11.25 — hai `|| true` nuốt lỗi thật

```sh
mc admin user add    local "$KEY" "$SECRET"          || true
mc admin policy attach local readwrite --user "$KEY" || true
echo "✓ Bucket và tài khoản dịch vụ sẵn sàng"
```

`|| true` ở đó **có lý do chính đáng**: `add` hỏng khi tài khoản đã tồn tại, `attach` hỏng khi policy đã gắn, mà chạy lại một lượt triển khai là chuyện thường. Nên cách chữa không phải bỏ nó đi — bỏ đi thì lượt deploy thứ hai đỏ.

Cách chữa là **đo trạng thái cuối** bằng chính cặp khoá ứng dụng sẽ dùng:

```sh
mc alias set svc http://minio:9000 "$KEY" "$SECRET"
echo ok | mc pipe "svc/$BUCKET/.songnhue-init-probe" >/dev/null
mc cat  "svc/$BUCKET/.songnhue-init-probe" >/dev/null
mc rm   "svc/$BUCKET/.songnhue-init-probe" >/dev/null
```

Mã thoát của `mc admin` chỉ nói *lệnh đã chạy xong*; nó không nói *quyền có hiệu lực*. Ghi → đọc → xoá là thứ duy nhất nói được điều thứ hai. `add` nay tách nhánh bằng `mc admin user info` nên không cần `|| true` và một lỗi thật ở đó dừng lượt triển khai ngay; `attach` vẫn giữ `|| true`, và điều đó **an toàn** vì phép đo bên dưới bắt được hậu quả.

**Đo bằng MinIO thật**, bốn kịch bản trên một container dựng tạm:

| Kịch bản | bản cũ | bản mới |
|---|---|---|
| lượt đầu, cặp khoá hợp lệ | — | thoát 0, `✓` |
| chạy lại, tài khoản đã có | — | thoát 0, `· tài khoản dịch vụ đã tồn tại` |
| **secret sai độ dài** | **thoát 0**, in `✓ … sẵn sàng` | **thoát 1**, không in `✓` |
| **policy gắn hụt** (tên policy sai, lỗi bị nuốt) | — | **thoát 1**, `Insufficient permissions to access this path .../.songnhue-init-probe` |

Dòng thứ ba là đúng sự cố T11.25 mô tả. Dòng thứ tư là bài kiểm cho **chính cơ chế mới** — một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai (luật 7).

⚠ Lượt đo đầu tiên của tôi báo *"bản mới cũng thoát 0"*. Sai ở **dụng cụ đo**: hàm bọc `docker run` qua một ống `| tail -4`, và zsh không có `PIPESTATUS` nên `$?` đọc được là mã thoát của `tail`. Bỏ ống đi thì hai bản tách ra ngay. Một phép đo hỏng trông y hệt một bản vá hỏng.

#### T11.26 — công tắc thứ hai chỉ tồn tại để lệch

Bộ seed có hai vế — hàng trong CSDL (`migrator`) và byte trong MinIO (`minio-init`) — và trước đây mỗi vế một biến môi trường. Biến thứ hai **không mang thông tin gì**: đường dẫn trong container đã bị bind mount ghim ở `/seed-media`. Nó chỉ có một khả năng duy nhất là **lệch** với biến thứ nhất, và lệch là hỏng câm:

- có hàng, không có byte → CSDL nói tệp tồn tại, `GET /api/v1/public/files/<id>` trả 404
- có byte, không có hàng → tệp nằm trong bucket không ai đọc tới

`SeedGateTest.haiVeKhongDuocLech` canh đúng chỗ đó — nhưng nó soi **hai tệp mẫu trong repo**, không soi `/opt/songnhue/.env` đang chạy trên máy chủ. Tức nó canh được bản mẫu chứ không canh được thứ quyết định hành vi (luật 12).

Nay `minio-init` đọc thẳng `SEED_LOCATION` và dùng `/seed-media` cố định. **Trạng thái lệch trở thành không biểu diễn được**, và bài canh cặp không còn việc gì để làm.

Thay nó bằng một bài khác hẳn: canh việc bản vá **không bị hoàn tác** — quét toàn bộ `deploy/` và khẳng định tên biến cũ không xuất hiện lại ở đâu. Nếu ai đó tách lại hai vế, họ sẽ không có bài canh cặp nào để đỡ, vì bài ấy đã bị gỡ cùng lúc.

📌 Tên biến cũ trong bài kiểm được ghép từ hai mảnh (`"SEED_MEDIA" + "_DIR"`) để chính mã nguồn của bộ canh không khớp phép canh của nó — cùng cái bẫy chú thích đã dính **ba lần** trong đợt này.

#### Kết quả

`SeedGateTest` 9 → **10 bài**; gói `deploy` 48/48 xanh. Kiểm chứng ngược cả hai bài mới: đưa biến cũ trở lại một tệp `deploy/` → đỏ đúng tệp đó; gỡ dòng `mc cat` khỏi phép probe → đỏ với *"phép đo phải GHI, ĐỌC và XOÁ"*.

⛔ **Không cần thao tác tay trên máy chủ.** `/opt/songnhue/.env` của staging đang có cả hai biến; biến bị bỏ đơn giản là không còn ai đọc, còn `SEED_LOCATION` thì `minio-init` nhận qua `${SEED_LOCATION:-}`. Dọn dòng thừa trong `.env` là việc tuỳ, không phải điều kiện.

---

### §10.56. ⚠⚠ Collation của CSDL — tham số chỉ có hiệu lực một lần, nên tệp cấu hình không còn là bằng chứng (26/8/2026)

#### Cái sai

`POSTGRES_INITDB_ARGS` (ICU `vi-VN`) được chốt 14/8 và ghi vào **hai** chỗ: `compose.infra.yml` (đường local) và `SongnhuePostgres` (đường test). Nó **chưa bao giờ** được ghi vào `compose.prod.yml` — tệp dựng cluster của staging **và** production.

Bản ghi cũ mô tả khoản nợ này là *"phải y hệt `compose.infra.yml`"*. Rà 26/8 bằng mã thật: không phải lệch, mà **vắng hẳn**. Và `deploy/postgres/init/` cũng không đặt locale ở đâu.

Nó sống sót 12 ngày vì mọi đường được đi thử đều đúng — cùng hình dạng với `DB_APP_PASSWORD` (§10.41), chỉ khác chỗ hậu quả không hồi phục được.

#### Vì sao khoản này đắt hơn vẻ ngoài

`initdb` chạy **một lần**, lúc tạo cluster. Sau đó image bỏ qua tham số vĩnh viễn. Hệ quả là **tệp cấu hình và cluster thật có thể nói hai điều khác nhau mãi mãi mà không lệnh nào báo sai** — một trạng thái mà phần lớn cấu hình khác không có.

Đo trực tiếp trên `postgis/postgis:16-3.4`, bốn kịch bản:

| kịch bản | kết quả |
|---|---|
| cluster dựng **có** tham số | `Anh < Dung < Đăng < Em` ✅ |
| cluster dựng **không** có tham số (glibc `en_US.utf8`) | `Anh < Đăng < Dung < Em` |
| locale `C` (so theo byte) | `Anh < Dung < Em < Đăng` |
| **vá tệp compose rồi `up -d --force-recreate`** | **vẫn `Anh < Đăng < Dung < Em`** |

⛔ Dòng cuối là dòng đáng giá nhất: **bản vá tệp compose một mình chỉ tạo cảm giác đã xong.** Nếu chỉ thêm dòng vào `compose.prod.yml` rồi tick, staging vẫn sai và không ai biết.

Ghi chú thêm: cách sai *phụ thuộc locale mặc định*, nên một phép kiểm khẳng định "Đăng rơi xuống sau Em" sẽ **xanh** trên cluster `en_US.utf8` — nó chỉ bắt được một trong hai kiểu sai. Phải so với thứ tự **đúng**.

#### Đã vá

1. **`compose.prod.yml`** — thêm dòng y hệt hai chỗ kia.
2. **`PostgresCollationParityTest`** (5 bài) — quét cả cây mã nguồn tìm chuỗi tham số trong `.yml/.yaml/.java/.sh`, đòi cả ba tệp bắt buộc có mặt, đòi mọi bản giống hệt nhau **và** đúng là ICU `vi-VN` (ba chỗ cùng sai vẫn là sai). Hằng số mốc ghép từ hai mảnh để chính tệp bài kiểm không tự khớp vào mình.
3. **`deploy/postgres/kiem-collation.sh`** — **ĐO** cluster đang chạy bằng một câu `ORDER BY`, ASCII thuần trên đường truyền (`chr(272)`/`chr(259)` thay vì ký tự tiếng Việt đi qua bốn tầng có thể đổi bảng mã).
4. **`deploy.yml`** — smoke test 3 câu → **4 câu**, collation đứng **đầu**: mọi câu sau chỉ có nghĩa trên một cluster đúng. Bước rsync đổi `chmod +x backup/*.sh` thành `find -name '*.sh'`, nếu không thì script mới lên máy chủ không có cờ chạy.
5. **`deploy-guideline.md` §2.7** — quy trình dựng lại cluster, **đã chạy thật**: dữ liệu giữ nguyên, thứ tự đổi từ `Anh<Đăng<Dung<Em` sang `Anh<Dung<Đăng<Em`. Checklist go-live thêm mục 20 (và sửa một dòng hỏng ở mục 18 — `%u:%g %a` từng bị shell nuốt thành `phải ra .`).

Kiểm chứng ngược **5 kịch bản**, mỗi lượt đều xác nhận bản hỏng đã nạp: lệch một ký tự (`vi-VN`→`vi_VN`) · xoá hẳn dòng · cả ba chỗ cùng đổi sang `libc` · workflow không gọi script · script mất cờ chạy. Cả năm đỏ đúng bài; khôi phục thì xanh lại.

#### Hai thứ lượt vá này tự va vào

**(a) `ScriptDockerLookupTest` bắt bản đầu của chính script mới.** Nó gọi `docker compose exec`, mà compose nội suy toàn bộ tệp trước khi trả lời — `compose.prod.yml` đòi `${APP_IMAGE:?}` vốn chỉ tồn tại trong lượt triển khai. Script sẽ hỏng ở production với một thông báo trỏ vào chỗ khác hẳn (§10.48). Bài canh viết sau hai lần trả giá đã chặn được lần thứ ba **trước khi** nó lên máy chủ. Đổi sang `container_cua` (tra bằng nhãn compose).

**(b) Lượt kiểm chứng ngược suýt nói dối — lần thứ ba trong dự án.** `sed -i.bak` rồi `mv .bak` khôi phục **cả mtime gốc**, nên với Maven tệp nguồn *cũ hơn* lớp `.class` vừa biên dịch từ bản hỏng → bỏ qua biên dịch lại → **cả bộ test chạy trên lớp hỏng**: 169 lỗi và dòng thật nằm ở `initdb: error: --icu-locale cannot be specified unless locale provider "icu" is chosen`, cách chỗ báo lỗi hàng nghìn dòng.

Trước đó cùng phiên còn một lượt nữa: một khối kiểm chứng ngược chạy sai thư mục (`cd` vào `backend` còn lưu lại), `sed` không tìm thấy tệp, mà bài kiểm **vẫn in 5/5 xanh** — vì bản hỏng chưa từng tồn tại.

⛔ Rút ra: luật 10 phải mở rộng — **bản KHÔI PHỤC cũng phải được xác nhận đã nạp**, không chỉ bản hỏng. Cách rẻ nhất: in một con số đo được ở mỗi bước (`grep -c`, `stat`), và `touch` tệp sau khi khôi phục.

---

### §10.57. Cổng secret bỏ qua trong im lặng, và bốn khoản "bấm ở GitHub" treo từ 15/8 (26/8/2026)

#### (a) Một lượt CD Production sẽ trông như đã chạy

Bước *"Kiểm tra đã có cấu hình máy chủ chưa"* hỏi **một** biến (`HOST`); thiếu thì `::warning::` + `ready=false`, và mọi bước sau mang `if: ready == 'true'` nên tự bỏ qua. Lượt chạy kết thúc **xanh trọn vẹn**.

Đo bằng `gh api` ngày 26/8: environment `production` **không có secret nào**. Nghĩa là bấm "CD Production" hôm ấy cho ra một lượt chạy xanh, một dòng tóm tắt "đã triển khai", và không một byte nào chạm máy chủ.

Cùng hình dạng với §10.53 (compose in `Running` mà giữ container cũ): **một câu khẳng định không phân biệt được hai trạng thái**.

**Vá.** Logic ra `.github/scripts/kiem-secret-may-chu.sh`, ba trạng thái:

| tình trạng | staging | production |
|---|---|---|
| đủ bốn | đi tiếp | đi tiếp |
| thiếu **một số** | ⛔ đỏ | ⛔ đỏ |
| thiếu **cả bốn** | cảnh báo + bỏ qua | ⛔ **đỏ** |

Vì sao lệch: CD Staging chạy **tự động** sau mỗi lượt merge — một môi trường chưa dựng mà nhuộm đỏ dòng CI của mọi người là đổi một lỗi thật lấy một lỗi phiền, rồi người ta tắt bớt cổng kiểm. CD Production chỉ chạy khi **có người bấm**, và với họ im lặng bỏ qua là câu trả lời sai nhất.

Và *"thiếu một số"* đỏ ở cả hai vì **cấu hình dở dang không phải "chưa dựng"** — nó là cấu hình hỏng, và nó sẽ hỏng muộn hơn ở `ssh` với `Permission denied`, một thông báo không nhắc gì tới secret.

**Vì sao tách ra tệp riêng.** Để `SecretGateTest` **chạy thật** script với từng tổ hợp biến (`ProcessBuilder`, môi trường **sạch** — kế thừa môi trường JVM là để một biến `USER` có sẵn trên máy dev quyết định kết quả bài kiểm). Một bài khẳng định *"`deploy.yml` có chứa `exit 1`"* xanh với cả script đúng lẫn script gọi `exit 1` ở nhánh không bao giờ tới (luật 9). 7 bài; kiểm chứng ngược 3 kịch bản: trả về hành vi cũ · chỉ hỏi `HOST` · workflow không gọi script.

#### (b) Bốn khoản "bấm ở GitHub" — áp và đo lại 26/8

Cùng một hình dạng: **cổng kiểm tồn tại trong tài liệu nhưng chưa có hiệu lực ở nơi nó phải chặn.** `branch-protection.md` §6.2 có sẵn lệnh từ 15/8; không ai chạy, và không ai biết là chưa chạy.

| | trước | sau |
|---|---|---|
| `dev` — required contexts | 2 | **7** |
| `staging` / `production` — `strict` | `true` | **`false`** |
| Secret scanning · push protection · non-provider | `disabled` | ✅ |
| Dependabot alerts + security updates | `disabled` | ✅ |

Hai chi tiết chỉ lộ ra khi làm thật:

- `Gắn tag SHA cho image không đổi` **không** được đưa vào `contexts` — nhưng lý do đầu tiên tôi ghi ra là **sai**, và phép đo bác nó ngay: `gh pr checks 41` cho thấy job ấy **có** báo cáo ở PR, ở trạng thái `skipping`. Một job bị `if` loại vẫn gửi kết luận `skipped` lên commit; §2.1 (*"Expected — Waiting for status"*) chỉ áp cho job **không tồn tại trong lượt chạy**. Lý do thật: `skipped` được tính là ĐẠT, nên một context **luôn** `skipped` ở PR **không bao giờ chặn được gì** — nó chỉ tạo ấn tượng sai rằng việc gắn tag đã được canh.
- `PUT automated-security-fixes` trả **422 `Vulnerability alerts must be enabled`**. *Alerts* (báo) và *security updates* (PR vá tự động) là hai công tắc, phải bật theo thứ tự.

Đo sau khi bật: `secret-scanning/alerts` → **0 cảnh báo**; `rulesets` → `[]` (không có luật kiểu mới chồng lên).

⬜ **Còn lại**: 4 secret `PROD_*` — chỉ đặt được sau khi có VPS-1. Nay thiếu chúng là lượt CD Production **đỏ**, không phải xanh giả.

---

### §10.58. ⚠⚠ Đường quay lui dữ liệu DUY NHẤT khôi phục ra một CSDL ứng dụng không đọc nổi (26/8/2026)

Tìm ra bằng một lượt **khôi phục thật** trên staging, không tìm ra bằng đọc mã. Lượt ấy vốn chỉ nhằm dựng lại cluster cho đúng collation (§10.56 → T11.3-b).

#### Cái sai

`pg_restore` xong, app chết ngay lúc khởi động:

```
ERROR: permission denied for table users
  at com.songnhue.core.application.auth.AdminBootstrapRunner.run(AdminBootstrapRunner.java:65)
```

CSDL có **đủ 61 bảng và đủ từng dòng dữ liệu**, nhưng `songnhue_app` không có quyền trên bảng nào.

**Vì sao.** GRANT cấp bảng của dự án do **migration Flyway** cấp (`V202608131006__core_db_role_grants.sql`), không do `10-bootstrap.sh` — script khởi tạo chỉ tạo role và cấp `CONNECT`/`USAGE`. Khi khôi phục vào một cluster mới, hai điều xảy ra cùng lúc:

1. `flyway_schema_history` được nạp lại **cùng dữ liệu** → Flyway báo *"Schema is up to date. No migration necessary"* → migration cấp quyền **không chạy**;
2. `--no-privileges` ở `pg_dump` đã **tước ACL** khỏi bản dump.

Không có nguồn nào còn giữ GRANT.

#### Vì sao nó ẩn được lâu đến thế

Khôi phục vào một CSDL **đã migrate** thì `ALTER DEFAULT PRIVILEGES FOR ROLE songnhue_owner` đã có sẵn, nên bảng do `--clean` dựng lại **tự động** có GRANT. Tức là:

| đường | có thử không | kết quả |
|---|---|---|
| khôi phục đè lên CSDL đang chạy | hay thử | ✅ chạy |
| khôi phục vào cluster **mới** | chưa ai đi | ⛔ hỏng |

Mà đường thứ hai chính là **hai công dụng duy nhất** `restore.sh` tự ghi ở đầu tệp: *"ứng dụng không khởi động nổi"* và *"khôi phục sang máy KHÁC (diễn tập trên VM-2 — T7.7)"*. Cùng hình dạng luật 3, và cùng họ với §10.41.

⛔ Hệ này **cố ý không có PITR** (§6.5), nên bản dump là đường quay lui dữ liệu **duy nhất**.

#### Hai thứ nữa cùng lộ ra trong lượt khôi phục

`pg_restore --exit-on-error` chạy bằng `songnhue_owner` **đỏ hai lần** trước khi tới được lỗi trên:

```
ERROR: must be owner of extension pg_trgm     ← COMMENT ON EXTENSION
ERROR: permission denied for table spatial_ref_sys  ← TABLE DATA của postgis
```

Cả hai thuộc về extension, do `CREATE EXTENSION` dựng lại rồi — `spatial_ref_sys` ở cluster mới đã có đủ 8500 dòng **trước khi** nạp gì. Bỏ chúng không mất dữ liệu.

#### Đã vá

- `backup.sh` + `pre-deploy-dump.sh`: bỏ `--no-privileges`, **giữ** `--no-owner`. Đo trên staging: mục ACL trong bản dump **0 → 98**, kích thước 319KB → 356KB.
- `restore.sh`: lọc mục lục bằng `--use-list`, bỏ `COMMENT - EXTENSION` và **mọi mục có chủ sở hữu `postgres`** (lọc theo chủ sở hữu, không theo danh sách tên — danh sách tên sẽ mục ngay khi có extension thứ tư); thêm chốt *"mục lục sau lọc phải > 100 mục"* để một bộ lọc hỏng không khôi phục ra CSDL rỗng mà vẫn thoát 0; thêm bước nghiệm thu **đọc thử bằng `songnhue_app`** — hỏi bằng chủ sở hữu xanh trong cả hai trạng thái (luật 9).
- `BackupRestoreFlagsTest` (7 bài) + kiểm chứng ngược 3 kịch bản.

#### Và một lỗi tự gây ra khi vá, `bash -n` không bắt được

Khối chú thích mới bị chèn **vào giữa** một lệnh nối dòng bằng `\`. Bash coi `\` là nối với dòng **kế tiếp**; dòng kế tiếp là `#` thì lệnh đứt tại đó, phần đối số còn lại thành **một lệnh mới**. Chạy thật mới lộ:

```
./backup/pre-deploy-dump.sh: line 91: --format=custom: command not found
```

⛔ `bash -n` trên bản hỏng **thoát 0** — cú pháp vẫn hợp lệ, chỉ là lệnh khác. Thêm bài `khongChuThichGiuaLenhNoiDong` quét mọi `.sh` trong `deploy/`; tự kiểm chứng đã đo: bản hỏng → `bash -n` exit 0, bài kiểm ĐỎ.

#### Trạng thái staging sau lượt này

61 bảng · 27 bảng có dữ liệu · vân tay số dòng **khớp từng bảng** với trước khi dựng lại, trừ đúng `system_backups` 7→6 — và chênh lệch ấy tự giải thích: `pre-deploy-dump.sh` chụp CSDL **rồi mới** ghi sổ bản chụp ấy, nên bản dump không chứa chính nó.

Sáu container **healthy**, kể cả `nginx` (trước đó `unhealthy` 22 giờ liền — T11.37). Bốn câu smoke test đều xanh trên site thật, và trang chủ trả **11 liên kết bài viết, tất cả là slug thật trong CSDL, 0 bài bịa** (§10.54 được nghiệm thu ở điều kiện thật).

---

### §10.59. ⚠ CD Staging đỏ vì cổng 22 bị quét — 30% lượt SSH bị sshd thả (27/8/2026)

#### Triệu chứng và cái KHÔNG phải nguyên nhân

Lượt CD Staging đầu tiên sau khi đề bạt `9ae5f19` đỏ ở bước *"Ghi lại bản đang chạy"*:

```
kex_exchange_identification: read: Connection reset by peer
Connection reset by <host> port 22
Process completed with exit code 255
```

Bước trước đó — `pg_dump trước khi triển khai` — **success**. Bước `Triển khai` **skipped**, nên máy chủ không bị đụng gì: 6/6 container vẫn healthy, site vẫn HTTP 200.

#### Nguyên nhân, đo được

| | |
|---|---|
| SSH cổng 22, giãn 4 giây, 10 lượt | **7 đạt / 3 hỏng — hỏng 30%** |
| HTTPS cổng 443, cùng máy cùng lúc | **5/5 = 200** |

Riêng cổng 22. Đếm kết nối trên máy chủ:

```
── kết nối :22 theo IP ──
     32  79.108.163.24
      1  <máy dev>
```

Sáu lượt đếm cách nhau 5 giây: `28 → 1 → 1 → 33 → 33 → 33` · **67 tiến trình sshd**.

`MaxStartups` mặc định `10:30:100` — vượt 10 kết nối chưa xác thực thì sshd **thả ngẫu nhiên 30%**. **30 đúng là chữ số giữa.** Bước đỏ là bước mở **ba** kết nối SSH liên tiếp (`docker inspect` cho ba container) — bước có xác suất trúng cao nhất trong cả job.

#### Ba thứ loại trừ được bằng phép đo, không bằng phỏng đoán

- `fail2ban` — **chưa cài** (không phải "cài mà tắt")
- sshd chưa từng restart (`NRestarts=0`), không đặt `MaxStartups` tường minh
- RAM còn 4,9/7,9 GB · `PasswordAuthentication no` · `PermitRootLogin no` · `who` = **0** phiên

⚠ Không ai vào được. Đây là vấn đề **sẵn sàng phục vụ**, không phải xâm nhập.

⚠ Và `ClientAliveInterval` không đặt → mặc định `0`, phiên chết **không bao giờ** được dọn: đo được 33 kết nối "đã xác thực" còn treo trong khi `who` trả về 0.

#### Đã vá — và ranh giới của bản vá

**Phía đường ống** (làm được ngay): `deploy.yml` ghép kênh SSH — `ControlMaster auto` · `ControlPath` · `ControlPersist 15m`, thêm `ServerAliveInterval`. Cả lượt deploy dùng **một** kết nối thay vì ~10. Lượt bắt tay đầu tiên — cái duy nhất còn phải đi qua cửa hẹp — thử lại 6 lần giãn cách tăng dần, hết lượt thì đỏ kèm ba bước chẩn đoán.

Và bước *"Ghi lại bản đang chạy"* gộp ba lượt `ssh` thành **một**: `docker inspect` nhận nhiều đối số. Kiểm chứng hành vi trên VPS-2 trước khi dựa vào nó — ba container thật → 3 dòng đúng thứ tự; xen một tên không tồn tại → **2 dòng, bỏ hẳn dòng thiếu** (không in dòng trống) và exit 1. Nên mã chỉ tin thứ tự khi đủ 3 dòng.

⛔ **Đó là giảm mặt tiếp xúc, KHÔNG phải bản vá gốc.** Gốc nằm ở máy chủ (`fail2ban` + `PerSourceMaxStartups` + `ClientAliveInterval`) và cần `sudo` — user triển khai không có sudo không mật khẩu. Quy trình: `deploy-guideline.md` **§2.2-b**.

`DeploySshMultiplexTest` (5 bài) canh phần làm được, và **một bài trong đó bắt workflow phải mang con trỏ tới §2.2-b, cùng một bài kiểm mục ấy có thật** — để không ai đọc bộ canh xanh rồi tưởng chuyện đã xong. Kiểm chứng ngược 3 kịch bản.

#### Bài học chung

Đây là lần đầu một lượt deploy đỏ vì **thứ nằm ngoài mã lẫn ngoài cấu hình của ta**. Cách phân biệt rẻ nhất hoá ra là **so hai cổng trên cùng một máy cùng một lúc**: 22 hỏng 30%, 443 đạt 5/5 — một phép so mất 30 giây và loại ngay được "máy chết", "mạng hỏng", "workflow sai".

---

### §10.60 — CD Staging báo **success** trọn vẹn trong khi **không container nào được thay** (27/8)

Lượt CD Staging đầu tiên đi hết đường ống sau khi vá ghép kênh SSH (§10.59). Mọi cổng đều xanh:
`pull` xong · `run --rm migrator` xong (33 migration) · **4/4 câu smoke test đạt** · gắn tag
`:staging` cho cả ba image · ghi tóm tắt "**✅ ĐẠT**".

Đo trên máy chủ ngay sau đó:

| service | digest vừa "triển khai" | image **đang chạy** | container tạo lúc |
|---|---|---|---|
| app | `6dcf9e4b…` | `9c9f18e9…` | **25/8** |
| admin-app | `af546d21…` | `f022dac1…` | **24/8** |
| public-web | `ed49011f…` | `19754e8b…` | **24/8** |

**Không một container nào được thay.** Máy chủ vẫn chạy mã của 24–25/8.

#### Nguyên nhân gốc

Khối "Triển khai" được nuôi vào bash-từ-xa bằng chính stdin:

```
ssh "$USER@$HOST" bash -euo pipefail <<REMOTE
```

Bash đọc script ấy **dần** từ stdin. Giữa khối có `docker compose run --rm migrator`, và
`docker compose run` **gắn stdin** — nó nuốt nốt phần script chưa đọc. Bash gặp EOF, thoát 0.

Phần bị nuốt gồm đúng hai thứ quan trọng nhất của bước:

- `$dc up -d --force-recreate app admin-app public-web nginx` — **việc chính**
- khối **đo lại image ID** của container đang chạy — **thứ duy nhất phát hiện được chuyện này**

Bằng chứng trong log: dòng cuối của bước là log tắt máy của migrator lúc `02:46:58.8`; bước Smoke
test mở lúc `02:46:59.19`. **0,4 giây** — không đủ chỗ cho `up -d` bốn container, và không có một
dòng `Recreated` nào.

Đo trên chính VPS-2, mỗi dòng là một khối heredoc có `echo` đứng sau lệnh:

| lệnh | dòng sau nó |
|---|---|
| `docker compose run --rm` | **MẤT** |
| `docker compose run --rm -T` | **MẤT** — `-T` chỉ tắt TTY, stdin vẫn gắn |
| `docker compose run --rm </dev/null` | chạy |
| `docker compose up -d` | chạy |
| `docker compose pull` | chạy |

#### Vì sao 4/4 smoke test không cứu được

Chúng hỏi **site**, mà site vẫn sống — bằng mã cũ. Không câu nào phân biệt được "đã thay container"
với "chưa thay":

- câu 1 hỏi collation của **cluster** — không đổi theo lượt deploy
- câu 2 hỏi chặng `nginx → public-web → app → postgres` — bản cũ cũng đi hết chặng ấy
- câu 3 đếm bài viết — dữ liệu, không phải mã
- câu 4 hỏi byte MinIO — cũng dữ liệu

Đó **đúng là** việc của bước đo lại image ID, thêm vào sau §10.53 vì lý do y hệt. Lần này nó không
sai — nó **không chạy**. Một cơ chế canh gác nằm trong vùng bị nuốt thì im lặng hệt như khi nó vắng mặt.

#### Vì sao ẩn được

`§10.53` (25/8) là *compose in `Running` rồi giữ container cũ* — khi ấy `up -d` **có** chạy và **có**
in ra. Bản vá là `--force-recreate` cộng bước đo lại. Cả hai đều đúng. Nhưng `run --rm migrator` được
thêm vào **giữa** chúng ở đợt sau (`migrator` thành service riêng, `depends_on: minio-init` — §10.55),
và từ lượt ấy trở đi mọi thứ đứng sau nó chưa từng chạy một lần nào.

Không lệnh nào báo sai, vì **không có lệnh nào cả** — phần script ấy đơn giản là biến mất trước khi
được đọc.

#### Đã vá

`.github/scripts/chay-tu-xa.sh` — khối lệnh **chuyển thành TỆP** trên máy chủ rồi chạy với
`</dev/null`. Bốn khối heredoc của `deploy.yml` đều đi qua nó.

Bảo đảm đặt **ở chỗ dữ liệu đi qua** (CLAUDE.md luật 12), không ở từng lời gọi: thêm `</dev/null`
riêng cho dòng `migrator` chỉ chữa đúng dòng ấy, còn lệnh nuốt stdin **tiếp theo** mà ai đó thêm vào
sẽ lại hỏng trong im lặng. `deploy/backup/pre-deploy-dump.sh` đã có sẵn **ba** lệnh `docker exec -i`;
nó chưa gây hại chỉ vì tình cờ được gọi ở **dòng cuối** của khối — tức là một quả mìn đã cài sẵn.

Kèm bước **đối chiếu số byte hai đầu**: gửi bao nhiêu, máy chủ nhận bấy nhiêu. Không có nó thì "sang
máy chủ một nửa" cũng chạy êm rồi thoát 0 — tức kiểu mới chỉ là một cách hỏng khác trong im lặng.

`DeployRemoteStdinTest` (6 bài), trong đó **một bài chạy kiểu CŨ và khẳng định nó MẤT dòng sau** —
không có bài ấy thì năm bài kia chỉ chứng minh "kiểu mới chạy được", chứ không chứng minh lỗi có
thật. Một bài khác cắt bớt byte lúc chuyển và đòi helper phải dừng đỏ.

#### Bài học

**Một lệnh có thể ăn mất phần công việc đứng sau nó, và dấu vết để lại là *không có dấu vết*.**
Mọi lỗi trước đây của dự án đều để lại *một dòng sai*. Lỗi này để lại một *khoảng trống* — và khoảng
trống thì không đọc log theo mã lỗi mà thấy được. Thứ tìm ra nó là một phép **so mốc thời gian**:
bước kết thúc 0,4 giây sau lệnh áp chót, trong khi việc còn lại cần vài chục giây.

⛔ Và một lần nữa: **cổng kiểm phải phân biệt được hai trạng thái nó khẳng định.** Smoke test hỏi
"site có sống không"; câu cần hỏi là "site đang chạy image nào".

### §10.61. Đợt chỉnh sửa cổng TTĐT theo văn bản nghiệm thu của Công ty (27/8/2026)

**Bối cảnh.** Công ty ban hành *"YÊU CẦU CHỈNH SỬA WEBSITE" v1.0* (`docs_origin/nghiem_thu_phase1.md`)
— 43 mã CR, một cây nội dung chuẩn 7 mục cấp 1, một bảng phân quyền, và 10 câu hỏi kỹ thuật. Đây là
lần đầu Công ty mô tả cổng bằng một tài liệu máy đọc được thay vì bằng ảnh chụp màn hình có chú
thích, nên nó **đóng mục G14** đã treo từ 19/8.

Mục dưới đây chỉ ghi những quyết định có *nguyên nhân gốc*, không chép lại danh sách việc — danh
sách nằm ở `master-tracking.md` WS-24.

#### 1. Cùng một hình dạng lỗi cũ: cây nội dung ở hai nơi, không nơi nào biết nơi kia

Menu của cổng nằm ở `menu_items` (CSDL), còn bảy tuyến đường mà menu trỏ tới nằm ở `ROUTES` của
Next. Một mục menu trỏ vào tuyến đường không tồn tại **không làm đỏ bất cứ thứ gì**: migration chạy
xanh, `next build` xanh, mọi bộ test xanh. Nó chỉ hiện ra khi một người dùng thật bấm vào và nhận
404 — đúng hình dạng §10.54, nơi cổng quảng cáo những khu vực bấm vào là không có.

Chữa bằng `PortalTaxonomyTest`, và điểm đáng ghi là nó canh **cả hai chiều**. Chiều "menu → có
trang" bắt lỗi 404. Chiều ngược lại — "trang → có ai dẫn tới" — bắt một loại lãng phí im lặng hơn:
một trang được viết, được kiểm, được triển khai mà không lối vào nào. Lượt kiểm chứng ngược làm hỏng
đúng một URL và **hai bài đỏ, mỗi chiều một bài**.

#### 2. ⚠⚠ Đổi menu làm ba trang tĩnh mất lối vào — và bộ seed sẽ xoá cứng chúng

Menu cũ trỏ vào bốn trang tĩnh bằng `link_type = 'ARTICLE'`. Cây nội dung mới chỉ giữ lại một
(`tong-quan`); ba trang kia bị thay bằng **trang thật ở đường dẫn khác** — `co-cau-to-chuc` thành
một trang đọc `org_units`, `lien-he` thành `/lien-he`, còn `chuc-nang-nhiem-vu` gộp vào Tổng quan
theo CR-23.

Hậu quả nhìn thấy được là ba bài mồ côi. Hậu quả **không** nhìn thấy được nằm ở bộ seed staging
`V202608251100`: vị từ dọn bài của nó là *"xoá mọi bài không có mục menu nào trỏ tới"*, **xoá CỨNG**.
Vị từ ấy được viết khi cả bốn trang đều có mục menu, và chính chú thích của nó liệt kê bốn slug như
một sự bảo đảm. Sau lượt đổi menu, nó sẽ nuốt ba bài — không lỗi, không log, và chỉ lộ ra ở lượt
dựng lại CSDL kế tiếp.

Điều đáng chú ý: vị từ **theo quan hệ** ấy hoá ra đúng, và đúng vì lý do người viết nó đã lường
trước — một danh sách slug viết cứng sẽ vẫn "bảo vệ" ba bài đã chết. Cái sai duy nhất là *chú thích*
của nó mô tả một trạng thái nay đã thay đổi.

Chữa: `V202608271031` **chủ động xoá mềm** ba bài, có điều kiện `created_by IS NULL AND updated_at
IS NULL` — biên tập viên đã viết nội dung thật vào đó thì bài ở lại, vì nội dung của khách không
phải thứ một migration được quyền quyết định thay họ. Việc chúng biến mất nay là một quyết định ghi
trong migration, không phải tác dụng phụ của một bộ seed mà không ai đọc ra.

`SeedPortalMigrationTest` nay canh **cả hai vế**: `tong-quan` phải sống sót, ba bài kia phải bị dọn.
Không có vế thứ hai thì một vị từ "bảo vệ mọi bài do migration tạo" cũng xanh y hệt — trong khi nó
để lại ba trang rỗng trên cổng.

#### 3. ⚠⚠ Cổng công khai chưa từng có CSP — hai tệp trỏ vào nhau

`next.config.ts` ghi *"CSP đầy đủ và HSTS đặt ở nginx (WS-11/T11.5) — nơi duy nhất biết đủ mọi
origin"*. `deploy/nginx/snippets/edge-headers.conf` ghi ngược lại: *"Cố ý KHÔNG đặt lại CSP … Hai
image FE đã đặt đủ chúng (`admin-app.Dockerfile` · public-web `next.config`)"*, kèm một nguyên tắc
nghe rất đúng — *mỗi header có ĐÚNG MỘT nơi chịu trách nhiệm*.

Cả hai lập luận đều mạch lạc. Chúng chỉ có một khiếm khuyết: **không nơi nào đặt CSP cho public-web**,
và đã như vậy từ WS-16. `NginxSecurityHeadersTest` có canh CSP — nhưng nó khai
`DOCKERFILE = "deploy/docker/admin-app.Dockerfile"`, nên cổng công khai nằm ngoài tầm với suốt thời
gian bài kiểm ấy báo xanh.

Đây là hình dạng đặc trưng của dự án ở dạng thuần khiết nhất: **một cơ chế canh gác tồn tại trong
tài liệu nhưng chưa có hiệu lực ở nơi nó phải chặn** — cùng họ với nợ #27 (lệnh bảo vệ nhánh nằm sẵn
trong `branch-protection.md` từ 15/8, không ai chạy, và không ai biết là chưa chạy). Khác biệt duy
nhất: ở đây *hai* tài liệu cùng khẳng định việc đã xong, nên đọc tệp nào cũng thấy yên tâm.

Nó lộ ra vì một lý do không liên quan: CR-22 đòi nhúng iframe Google Map, nên phải đi tìm `frame-src`
để mở — và không tìm thấy chỉ thị nào để mở.

`csp.test.ts` đọc **giá trị đã giải** qua `nextConfig.headers()`, không grep tệp: grep chuỗi
`Content-Security-Policy` sẽ xanh kể cả khi hằng số được khai mà không ai gắn vào `headers()` (luật 3).

⚠ Ghi lại một đánh đổi để nó là quyết định đọc được thay vì một chỗ ai đó nới ra rồi quên:
`script-src` của public-web **phải** có `'unsafe-inline'`, khác admin-app. Next App Router chèn
`<script>` nội tuyến cho hydration; cách chặt hơn là gắn `nonce`, nhưng nonce phải khác nhau mỗi
request — tức mọi trang thành động và **ISR tắt hẳn**, trong khi DOD1.17 (trang chủ < 3s) đang dựa
vào ISR.

#### 4. Hai bộ canh cũ đỏ vì canh **hình dạng** thay vì canh **bất biến**

Cả hai đều đỏ oan, và cả hai đều đáng ghi vì lý do đỏ khác nhau:

- `siteContactConfig.test.ts` khẳng định *"`SiteFooter.tsx` phải chứa `'company.email'`"*. CR-40 yêu
  cầu bỏ email khỏi chân trang, nên bài kiểm đọc một lượt sửa **đúng yêu cầu** thành *"cổng lại ghi
  cứng thông tin liên hệ"* — đúng ngược sự thật. Bất biến thật không bao giờ là *"tệp X chứa chuỗi
  Y"* mà là *"thông tin liên hệ phải đến từ `settings`, ở mọi nơi cổng công bố nó"*. Nay danh sách
  là **các nơi công bố**, mỗi khoá chỉ cần có một nơi đọc — và thêm **vế ngược**: chân trang KHÔNG
  được đọc lại hai khoá ấy, để lượt sửa giao diện kế tiếp không lặng lẽ đặt chúng về (đúng chuyện đã
  xảy ra ngày 24/8).

- `SiteLayoutTest.pathCuaMenuSeedDung` khẳng định *"ba mục con của Giới thiệu đều trỏ tới trang
  tĩnh"* — một mô tả hình dạng của cây menu tháng 8. Nay nó canh bất biến: cha đứng trước con, mọi
  mục có đích giải được **khớp `linkType`**, và mục `NONE` phải thật sự có con. Hình dạng thì ghim
  riêng ở `migrationSeedDuKhungCong` bằng danh sách bảy mục §3 — vì đó là *tiêu chí nghiệm thu*, và
  ghim nó là đúng: đổi tên hay đổi thứ tự một mục là một quyết định, nó **phải** làm đỏ.

Bài học chung: bài kiểm mô tả hình dạng hiện thời sẽ đỏ ở đúng lượt thay đổi hợp lệ, và cái giá
không phải là mấy phút sửa — mà là người sửa quen dần với việc "bài này đỏ thì cứ chỉnh cho khớp".

#### 5. Ba quyết định mô hình dữ liệu, và vì sao không chọn cách rẻ hơn

- **CR-25/26 → bảng `org_unit_leaders`, không phải hai cột `head_name`/`head_phone`.** Hai cột chỉ
  chứa được một người, mà CR-25 là một *danh sách* (Chủ tịch, Giám đốc, các Phó Giám đốc). Dựng hai
  cột cho CR-26 rồi dựng thêm cơ chế khác cho CR-25 là hai nơi trả lời cùng câu hỏi *"ai đứng đầu
  đơn vị này"*.
  ⛔ Và nó **không nối `employees`**: toàn bộ nội dung là thông tin Công ty chủ động công bố, nên
  endpoint công khai đọc nó không có đường nào chạm tới trường nhạy cảm (quy tắc 10, NĐ 13/2023).
  Một phép lọc là thứ có thể quên; một bảng không chứa dữ liệu nhạy cảm thì không rò được.

- **CR-30 Tiến độ sản xuất → cây danh mục CMS, không phải entity mới.** §5.5 mô tả đúng một luồng
  (Năm → Vụ → nội dung) nhưng **không nói tiến độ đo bằng chỉ tiêu gì, đơn vị gì, ai nhập, tần suất
  nào** — chưa đủ để thiết kế một bảng số liệu. Dựng `production_progress` lúc này là đoán hộ Công
  ty một mô hình nghiệp vụ, rồi phải di chuyển dữ liệu khi đoán sai. Cây danh mục đã có sẵn ba cấp
  đúng hình dạng cần.

- **CR-28 cột "Vị trí" → dựng từ `latitude`/`longitude`, KHÔNG thêm cột `map_url`.** Hai nguồn toạ
  độ cùng tồn tại là hai nguồn sẽ lệch — đúng hình dạng đã trả giá ở `ConstructionStatusService`
  (luật 13).

#### 6. Thứ cố ý **không** làm, và vì sao việc không làm cũng cần lý do ghi lại

CR-14/CR-38 đòi số liệu tuần/tháng chỉ xem được sau khi đăng nhập, và §2 nói rõ *"phân quyền phải xử
lý ở tầng route/API, không chỉ ẩn/hiện ở giao diện"*. Cổng công khai chưa có tầng xác thực nào.

Hai cách làm cho *trông như đã xong* đều bị loại có chủ đích:

1. **Nút "Đăng nhập" dẫn tới trang chưa tồn tại** — đúng hình dạng §10.54.
2. **Dựng sẵn bảng tuần/tháng rồi ẩn bằng CSS** — dữ liệu đã nằm trong HTML gửi tới trình duyệt, ai
   mở DevTools cũng đọc được; và tệ hơn là nó **trông như đã phân quyền**. Đó là ảo giác đắt tiền:
   nó làm người nghiệm thu tick vào một ô chưa có gì đứng sau — chính là *"việc làm xong nửa đường
   trông y hệt việc làm xong"* (luật 19).

Cùng lý lẽ cho khối Mực nước và khối Vận hành công trình: dựng đủ khung theo §7 (dòng "Cập nhật
lúc", nút làm mới, trạng thái dự phòng) nhưng **không** dựng sẵn một lưới 10 cống với dấu gạch —
một lưới mà không lượt chạy nào từng đổ dữ liệu thật vào là mã chưa được kiểm, đội lốt mã đã xong
(luật 7), và danh sách 10 cống còn đang chờ Công ty chốt (OI-03).

#### 7. ⚠⚠ Lỗi mà 906 bài kiểm của cả hai phía đều không thấy — chỉ lộ ra khi mở trang thật

Sau khi mọi cổng kiểm ở máy đã xanh (662 test BE · 244 test FE · 8/8 bước `ci-local`), lượt chạy
`make dev-docker` rồi bấm qua từng mục menu cho ra **17/17 đường dẫn trả 200**. Nhưng mở trang
"Tiến độ sản xuất" thì bộ chọn **Năm** hiện hai lựa chọn: *"Lịch vận hành cống & trạm bơm"* và
*"Thông báo xả nước đệm"*.

Không có lỗi nào báo ra. Hai danh mục ấy đều có thật, đều đang hiện, HTTP 200, trang dựng bình
thường — **chỉ nội dung là vô nghĩa**. Đây là loại sai mà mọi khẳng định kỹ thuật đều đúng.

Nguyên nhân là **hai lỗi độc lập gặp nhau**, và mỗi lỗi tự nó đủ để sinh ra triệu chứng:

1. **Backend lọc `isVisible()` theo từng dòng.** CR-01 ẩn mục "Thông báo", nhưng hai danh mục con
   của nó vẫn `visible = true` nên vẫn nằm trong danh sách trả về — **mồ côi mà vẫn hiện**.
2. **Giao diện suy quan hệ cha–con từ vị trí trong mảng phẳng.** Danh sách sắp theo `path` dạng
   **chuỗi**, nên `'/12/' < '/2/'`: hai đứa con của `thong-bao` (`/2/7/`, `/2/8/`) rơi đúng sau
   `tien-do-san-xuat` (`/12/`), và phép suy theo vị trí nhận chúng làm con của nó.

Điều đáng ghi nhất là **vì sao không bộ test nào bắt được**: mỗi bên đều đúng với dữ liệu của mình.
Bài kiểm backend dựng danh mục rồi khẳng định danh mục ẩn không trả về — đúng, nó không kiểm các
con. Bài kiểm frontend truyền một mảng dựng tay có đủ cha lẫn con liền nhau — đúng, đó là mảng
"sạch" mà backend chỉ trả về khi không có gì bị lọc. Lỗi nằm ở **chỗ tiếp giáp**, và chỗ tiếp giáp
chỉ tồn tại khi hai bên thật sự nói chuyện với nhau.

Vá cả hai vế, cố ý không chọn một:

- `PublicPortalService.categories()` loại **cả nhánh** dưới một danh mục ẩn (khớp tiền tố `path`).
  Áp cùng luật cho `articles(categorySlug)` — nếu không thì một nhánh đã rút khỏi điều hướng vẫn mở
  được bằng địa chỉ trực tiếp, tức hai câu trả lời cho cùng một câu hỏi.
- DTO công khai thêm `parentSlug`, và giao diện đối chiếu trường đó thay vì đếm vị trí.

Kiểm chứng ngược có số đo: gỡ bản vá backend (`grep -c noneMatch` = 0) → 1 bài đỏ; khôi phục (= 1)
→ 0 đỏ.

#### 8. Và lỗ thứ ba lộ ra khi viết bài kiểm cho lỗ thứ hai

Bài kiểm cần ẩn một danh mục để dựng hiện trường — và **không có đường nào để ẩn**. Cột
{@code categories.visible} có từ `V202608191016`, DTO của màn hình quản trị *trả nó ra*, nhưng
không endpoint nào ghi được nó. Quản trị viên nhìn thấy trạng thái Hiện/Ẩn của từng danh mục mà
không có cách nào đổi.

Đó là quy tắc 15 ở **chiều ghi** — bản thân nó đã là một lỗi. Nhưng nó thành chuyện gấp vì migration
của đợt này chọn *ẩn* mục "Thông báo" thay vì xoá, với lý do ghi thẳng trong tệp: *"ẩn là thao tác
quay lui được bằng một cú bấm, còn xoá thì không"*. Lý do ấy chỉ đúng khi cú bấm đó tồn tại — và nó
không tồn tại. Một quyết định đúng dựa trên một tiền đề sai.

`CategoryService.setVisible` + `PUT /cms/categories/{publicId}/visibility` đóng lỗ ở tầng API; nút
bấm trên màn hình quản trị còn nợ.

#### Bài học

⛔ **"Mọi cổng kiểm xanh" và "chạy đúng" là hai câu khác nhau, và khoảng cách giữa chúng nằm ở chỗ
tiếp giáp giữa hai hệ thống.** Cả hai bên đều có bài kiểm, cả hai bên đều đúng với dữ liệu *của
mình*, và cái sai chỉ tồn tại khi chúng nói chuyện với nhau. Không có cách nào tìm ra nó ngoài việc
mở đúng trang ấy trên hệ đang chạy — đúng điều CLAUDE.md đã ghi và lần này phải trả giá để nhớ:
*trước khi mở một giai đoạn mới, chạy đường mà người dùng thật đi.*

**Một tài liệu nghiệm thu là dữ liệu, không phải văn xuôi.** Bảy mục cấp 1 của §3 nay là một phép
khẳng định `containsExactly` chạy ở mỗi lượt CI; bảng phân quyền §6 sẽ là một bộ bài kiểm gọi thẳng
API khi chưa đăng nhập. Thứ không chuyển được thành phép khẳng định thì cũng không nghiệm thu được —
và với dự án này, "đã tick" chưa bao giờ là bằng chứng.

---

### §10.62. Đầu trang thân thiện + "mọi thứ hiển thị phải cấu hình từ admin" (28/8/2026)

Hai việc được giao. Việc thứ nhất là một yêu cầu giao diện; việc thứ hai là một **lượt kiểm kê**.
Cả hai đều tìm ra thứ không ai đang đi tìm, và cả hai thứ ấy đều đã nằm sẵn trong §10 của chính văn
bản nghiệm thu.

#### 1. Thanh điều hướng tràn khung trên **mọi** màn hình — và `flex-wrap` che nó đi

Cây nội dung §3 mà đợt trước vừa dựng có tám mục cấp 1 với nhãn tiếng Việt dài. Bản cũ vẽ chúng
bằng `text-[13px] font-bold uppercase tracking-wider` + `px-3.5`. Đo:

```
menu 1344px + nút Tìm kiếm 110px = 1454px
khung chứa 1240 − 48             = 1192px      → tràn 22%
```

Thanh không **vỡ** — nó `flex-wrap`, nên nó **xuống dòng**. Đầu trang cao gấp đôi ở mọi bề rộng, kể
cả desktop rộng nhất, và trên điện thoại tám mục viết hoa xếp thành một mảng chữ chiếm gần hết màn
hình đầu tiên. Không lỗi nào báo ra. Không bài kiểm nào đụng tới. `flex-wrap` là một cơ chế **chịu
lỗi**, và một cơ chế chịu lỗi làm đúng việc của nó thì lỗi không bao giờ nổi lên.

Nặng hơn phần bề rộng: hai mục cấp 1 kiểu `NONE` ("Giới thiệu", "Quản lý, vận hành") được vẽ thành
`<button>` **không gắn hành vi nào**. Trên máy có chuột thì `group-hover` mở menu con nên không ai
thấy vấn đề; trên máy tính bảng, chạm vào là chạm một nút không phản hồi và bốn mục con **không có
cách nào mở ra**. §10 của văn bản nghiệm thu ghi đúng một dòng cho chuyện này: *"Giao diện hiển thị
đúng trên máy tính, máy tính bảng và điện thoại"*.

#### 2. Sáu cột / khoá / tham số bày ra mà **một nửa cặp đọc–ghi không tồn tại**

Việc thứ hai không tìm ra một lỗi; nó tìm ra một **hình dạng**:

| Thứ bị hỏng | Nửa có | Nửa thiếu | Triệu chứng |
|---|---|---|---|
| `org_unit_leaders` | đọc + endpoint công khai | **không controller, không màn hình** | trang Lãnh đạo rỗng vĩnh viễn |
| `org_units.address/phone/email` | đọc + hiện ở bảng 6 cột | 3 setter **không lời gọi nào** | ba cột trống mãi |
| `CreateRequest.shortName` | DTO + validate + ô nhập | `create()` không nhận tham số | báo *lưu thành công*, giá trị biến mất |
| `PUT /org-units/{id}` | endpoint đầy đủ | **không màn hình nào gọi** | tên đơn vị không sửa được sau khi tạo |
| 2 cột tài liệu công trình | đọc + dựng 2 liên kết CR-28 | setter chỉ có **1 lời gọi, trong một bài kiểm** | hai liên kết không có gì để trỏ tới |
| `HomeMediaGallery` 3 props | component hoàn chỉnh | trang chủ gọi `<HomeMediaGallery />` **trần** | khối rỗng ở mọi môi trường |

Cộng thêm bốn khoá `settings` (`site.analytics.*`, `site.color.*`) bày trên màn hình cấu hình từ
19/8 mà **0 nơi đọc** — chiều ngược lại của cùng một hình dạng.

⚠ **Bốn trong sáu mục trên do chính đợt WS-24 tạo ra một ngày trước.** Đợt ấy đếm "đường đọc đã
dựng xong" rồi tick, và nửa còn lại của mỗi cặp không có ai đếm. Đây là luật 19 (*việc làm xong nửa
đường trông y hệt việc làm xong*) ở dạng có cấu trúc: không phải một người lười, mà là một **đơn vị
đếm sai** — ta đếm *tính năng đã dựng*, trong khi thứ người dùng nhận được là *vòng khép kín
nhập → lưu → hiện*.

#### 3. Hai lượt kiểm chứng ngược thất bại, và cả hai đều lộ ra lỗi thật

Đây là phần đáng giá nhất của lượt này.

**(a) Bộ canh khoá `settings`.** Để chứng minh nó bắt được vi phạm, tôi đặt `--` trước câu `DELETE`
của migration rồi chờ nó đỏ. Nó **không đỏ**. Nguyên nhân: mẫu bắt câu `DELETE` là biểu thức chính
quy, và regex **không biết SQL có chú thích** — một câu lệnh đã bị vô hiệu hoá vẫn được tính là đã
chạy, nên bốn khoá vẫn bị trừ khỏi danh sách phải-có-người-đọc. Bản hỏng *đã* được nạp; bộ canh mù
trước nó. Nếu lượt kiểm chứng ấy không chạy, lỗ này nằm lại vĩnh viễn và không có triệu chứng nào.

**(b) Bộ canh vị trí menu.** Bài kiểm chứng ngược bản đầu khẳng định mẫu bắt enum trả về
`("HEADER", "FOOTER")` từ một enum có **ba** hằng — vì mẫu đòi `[,;]` sau tên hằng, mà hằng cuối
của enum Java không có dấu phẩy. Bài kiểm chứng ngược ấy **chép lại hành vi sai thay vì bắt nó**, và
nó sẽ xanh mãi mãi. Chỗ lộ ra là một khẳng định khác trong bài chính: *"phải trích được ít nhất 3
giá trị"* — 2 < 3.

> ⛔ **Một bài kiểm chứng ngược cũng có thể sai theo đúng cách mà thứ nó kiểm chứng đang sai.**
> Người viết cả hai là cùng một người, mang cùng một giả định. Thứ cứu được ở đây không phải bài
> kiểm chứng ngược mà là một khẳng định **về số lượng** — `hasSizeGreaterThanOrEqualTo(3)` không
> chia sẻ giả định nào với mẫu regex. Bổ sung cho luật 10: *làm hỏng có chủ đích thì phải xác nhận
> bản hỏng đã được nạp* — và **xác nhận rằng bộ canh nhìn thấy nó**, hai chuyện khác nhau.

#### 4. Chạy thật lại tìm ra thứ thứ ba

17/17 đường dẫn trả 200 và mọi bài kiểm xanh, nhưng trang Xí nghiệp vẫn rỗng trong khi API trả đủ
dữ liệu. Không phải lỗi mã: bộ đệm ISR của Next nằm trên **lớp ghi của container**, và `docker
restart` giữ nguyên lớp ấy — phải `up -d --force-recreate`. Cùng họ §10.53.

Chính chỗ ấy lộ ra một giới hạn thật, đo được và chưa vá: `PortalCache` (nơi xếp job xoá cache cổng)
nằm ở module `content`, còn `org_units` ở `core` và `constructions` ở `operations`. Quy tắc 6 không
cho gọi qua ranh giới module, nên **sửa dữ liệu tổ chức không xoá được cache cổng** — cổng trễ tới
5 phút. Không mất dữ liệu, có tự lành, nhưng người nhập liệu không thấy đổi ngay sẽ tưởng lưu hỏng.
Vá tạm: một dòng cảnh báo trên màn hình. Vá thật cần một SPI cache cổng — ghi nợ T25.22.

#### 5. Tài liệu và mã nói hai chuyện về màu, suốt 13 ngày

`ui-styles.md` §2.3 ghi gradient navbar là `#0c366e → #165bb6`. `SiteHeader` vẽ `#061b37 → #0b2d5b`.
**Một trong hai màu chưa từng chạy**, và đọc tệp nào cũng thấy hợp lý. Cùng hình dạng với §10.61
(hai tệp trỏ vào nhau về CSP) và với quy tắc 14.

Đã sửa **tài liệu theo mã**, không ngược lại — §2 của văn bản nghiệm thu chốt *"hệ màu GIỮ
NGUYÊN"*, nên bản đúng là bản người dùng đang nhìn thấy. Bảy sắc navy chép tay gộp còn năm bậc
`portalChrome`, và `noHardcodedColors.test.ts` nay khẳng định **0 mã hex** trong `public-web`.

⚠ Bộ canh ấy cố ý **chưa phủ `admin-app`** (còn 25 chỗ, 12 tệp) — và javadoc của nó **ghi rõ giới
hạn ấy**. Im lặng về phạm vi là tái tạo đúng lỗi vừa sửa cùng lượt: `PortalSettingsReadTest` soi
đúng một tệp migration nên mọi khoá seed trước đó đi lọt, y như `NginxSecurityHeadersTest` chỉ soi
`admin-app` trong khi cổng công khai chạy không CSP (§10.61).

#### 6. Sổ tiến độ tự nó sai — vì một ký tự trong ghi chú

Bộ đọc `master-tracking.md` quét ghi chú tìm ký hiệu trạng thái để đọc được những dòng bảng không có
ô tick. Nó quét cả ghi chú của dòng **đã tick**, nên một task hoàn thành mà ghi chú nhắc phần còn
treo — `"⬜ Nợ: màn hình quản trị chưa có nút"` — bị hạ xuống `Pending` và lên Google Sheet dưới dạng
*chưa làm*. Đo lúc phát hiện: **T24.25** (tick từ 27/8) và **T25.13**.

Markdown đọc đúng, Sheet đọc sai, hai bên lệch nhau ở một ký tự. Cùng họ **T11.47**. Chữa ở phía nội
dung chứ không ở bộ đọc — nới lỏng nó sẽ làm hỏng những dòng bảng vốn dựa vào ký hiệu — và thêm phép
kiểm thứ **8** cho bộ đọc.

#### Bài học

⛔ **Đếm "đã dựng xong bao nhiêu tính năng" là đếm sai đơn vị.** Thứ người dùng nhận được là một
vòng khép kín *nhập → lưu → hiện*; một nửa vòng ấy chạy hoàn hảo vẫn cho ra số không. Bốn trong sáu
lỗ của lượt này ra đời **một ngày trước** từ một đợt làm việc cẩn thận, có bài kiểm, có nghiệm thu.

⛔ **Một cơ chế canh gác phải nói ra phạm vi của chính nó.** Ba lần trong hai ngày, cùng một hình
dạng: bộ canh đúng luật nhưng hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời bảo đảm.
Khi không phủ hết được thì **ghi giới hạn vào chính bộ canh** và mở một dòng nợ có số đo.


---

### §10.63 — Bảy context bắt buộc khoá chết mọi PR chỉ sửa tài liệu (27/8)

Hai PR mở **cùng lúc**, cùng nhánh đích `dev`, khác đúng **một** biến — một thí nghiệm đối chứng
tự nhiên mà không ai cố ý dựng ra:

| PR | đụng `frontend/`? | job matrix `Đóng gói image frontend` | kết quả |
|---|---|---|---|
| **#48** | có | **CHẠY** → báo hai tên đã bung | `CLEAN` |
| **#47** | không (`docs/` · `.claude/` · `.agents/`) | **BỎ QUA** → chỉ báo tên gốc | **`BLOCKED`** |

Context bắt buộc so với context thực sự được báo cáo trên #47:

| bắt buộc | báo cáo |
|---|---|
| `Đóng gói image frontend (admin-app, deploy/docker/admin-app.Dockerfile)` | — **không có** |
| `Đóng gói image frontend (public-web, deploy/docker/public-web.Dockerfile)` | — **không có** |
| | `Đóng gói image frontend` → `skipped` |

Một job matrix khi **chạy** báo tên đã bung; khi **bị bỏ qua** chỉ báo tên gốc chưa bung. Hai
context bắt buộc kia không bao giờ tới → PR treo mãi ở *"Waiting for status to be reported"*.

⛔ **Không sửa được bằng cách đổi danh sách context**: đòi tên gốc thì hỏng trường hợp *chạy*, đòi
tên đã bung thì hỏng trường hợp *bỏ qua*. Không có tên nào đúng cho cả hai.

#### Vì sao ẩn được

`branch-protection.md` §2.1 đã cảnh báo đúng cái bẫy này — *"check bắt buộc mà không bao giờ chạy"*
— và §6.2 còn ngồi phân tích riêng trường hợp `Gắn tag SHA`, rồi kết luận bảy context kia an toàn.
Phân tích ấy soi trường hợp job **bị `if` loại** (vẫn báo cáo), không soi trường hợp job **matrix
bị bỏ qua** (báo một cái tên khác). Từ 26/8 tới 27/8 không PR nào chỉ sửa tài liệu, nên cái bẫy nằm
im đúng một ngày rồi bật ra ở PR ghi lại chính ba sự cố trước đó.

📌 Cùng hình dạng luật 28: *một cơ chế canh gác hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc
như một lời bảo đảm.* Ở đây còn ngược đời hơn — cái **treo mãi** của nó đọc như *"CI đang chạy"*.

#### Đã vá

Job `Cổng kiểm CI` (`if: always()`, `needs` **mọi** job) thành context bắt buộc **duy nhất**. Nó đỏ
khi có job `failure`/`cancelled`, bỏ qua `skipped` — bộ lọc đường dẫn bỏ qua một vùng không thay
đổi là đúng việc của nó — và in bảng kết quả từng job ra tóm tắt.

⚠ Gom về một context nghĩa là **context ấy phải biết hết**. `CiGateCoverageTest` (4 bài) đối chiếu
`needs` với danh sách job có thật trong `ci.yml` **cả hai chiều**: job đứng ngoài `needs` → đỏ; và
`needs` trỏ tới job không tồn tại → cũng đỏ. Kiểm chứng ngược: bỏ `tracking` khỏi `needs` → bài đỏ
và **nêu đích danh** job thiếu.

⚠⚠ Thứ tự áp bắt buộc: job phải có mặt trên `dev` **TRƯỚC** khi đổi danh sách context. Đổi trước
thì mọi PR treo — kể cả PR mang chính job ấy — và chỉ còn đường bypass bằng quyền admin.

### §10.64 — Chín phép kiểm canh nguồn sự thật, không cổng nào chạy, và có sẵn nhánh thoát-0 (27/8)

Lộ ra khi tự kiểm dòng tracking vừa sửa bằng **chính bộ đọc thật** thay vì bằng một bộ kiểm tự viết
(bộ tự viết sai spec và báo 233 dòng "lỗi" không có thật — nó không biết dự án dùng `- [~]` và dòng
nối).

Ba tầng, mỗi tầng đủ để vô hiệu hoá tầng dưới:

1. `test_parse.py` **không nằm trong `ci.yml` lẫn `Makefile`** — chưa cổng nào chạy nó.
2. Nếu có chạy: nó `import server`, mà `server` kéo theo `fastmcp` + `google-api`, và nhánh
   `ImportError` gọi **`sys.exit(0)`** — in `BỎ QUA…` rồi **xanh**.
3. Bên trong bộ đọc: `_status_from_text` quét `✅` trên **toàn dòng kể cả cột Note**, nên một dấu
   `✅` đánh dấu ý phụ nâng cả task lên `Done`. Đo trên file thật: **đúng 2 dòng sai**, và cả hai là
   việc còn mở quan trọng nhất — **T11.45** `[ ]` (siết SSH, đang làm đỏ deploy) và **T11.7** `[~]`
   (secret production) đều hiện **Done** trên bảng Công ty đọc.

#### Hai nửa của cùng một lỗi, tìm ra bởi hai nhánh khác nhau

WS-25 (#48) tìm ra **chiều ngược lại** cùng lớp lỗi: `⬜` trong ghi chú **hạ** một task `[x]` xuống
`Pending`. Bản ghi ấy kết luận *"chữa ở phía nội dung chứ không ở phía bộ đọc: nới lỏng
`_status_from_text` sẽ làm hỏng những dòng bảng vốn dựa vào nó"*.

Nỗi lo đúng, nhưng bản vá **không nới lỏng** hàm ấy: dòng bảng vẫn gọi
`_status_from_text(status_col, status_col)` y nguyên. Chỉ dòng **có ô tick** thôi dùng nó — ở đó dấu
tích là thứ người viết cố ý đặt, chữ trong ghi chú thì không. Cả hai chiều hết cùng lúc.

#### Đã vá

Tách phần đọc thuần sang `tracking_parser.py` **chỉ dùng thư viện chuẩn** → `test_parse.py` import
thẳng, **không còn nhánh bỏ qua nào**; `server.py` tái xuất nên MCP không đổi hành vi (đo: 8/8 tên
còn nguyên, đọc ra 437 dòng). Thêm job `Bộ đọc tracking` vào `ci.yml` **không có bộ lọc đường dẫn**
và bước `[1/9]` của `make ci-local`.

#### Bài học

⚠⚠ **Bản ĐẦU của bài kiểm cho tầng 3 là xanh giả** — nó tự suy lại trạng thái từ dấu tích rồi so
với **chính dấu tích ấy**, nên vẫn xanh 8/8 sau khi gỡ bản vá. Chỉ bước kiểm chứng ngược bắt được.
Đây là lần thứ ba trong dự án một bài kiểm chứng ngược cứu một bộ canh vô nghĩa (§10.62 hai lần).

**Khi tự kiểm một tệp, hãy chạy BỘ ĐỌC THẬT của nó.** Một bộ kiểm tự viết mang đúng giả định của
người viết — và ở đây nó vừa bỏ sót lỗi có thật, vừa bịa ra 233 lỗi không có.

---

### §10.65 — Một đợt sửa CHÚ THÍCH làm ứng dụng không khởi động được (27/8)

CD Staging đỏ ở bước "Triển khai". Ứng dụng chết ngay lúc dựng `flywayInitializer`:

```
Migration checksum mismatch for migration version 202608251100
-> Applied to database : -1232886408
-> Resolved locally    :  2110920357
```

Thứ đã đổi trong `V202608251100__seed_portal_content.sql`: **14 dòng thêm, 5 dòng xoá, toàn bộ nằm
trong một khối chú thích `--`. Không một dòng SQL nào đổi.** Nội dung chú thích ấy hoàn toàn đúng và
hữu ích — nó giải thích vì sao vị từ xoá theo *quan hệ* tốt hơn danh sách slug viết cứng.

Flyway băm **toàn bộ nội dung tệp**. Chú thích nằm trong tệp, nên nó nằm trong checksum.

#### ⚠⚠ Vì sao 680 bài kiểm không thể bắt được

Mọi bài kiểm chạy migration từ CSDL **rỗng** — Testcontainers dựng mới mỗi lượt. Không có bản ghi
`flyway_schema_history` cũ nào, nên **không có checksum cũ để so**. Bộ test xanh trọn vẹn, `make
ci-local` 9/9, CI trên `dev` 10/10 — rồi lượt deploy kế tiếp chết.

Đây là lớp lỗi mà bộ test **về nguyên tắc** không thấy: nó chỉ tồn tại ở một CSDL đã sống. Cùng họ
với §10.56 (tham số chỉ có hiệu lực một lần) — *trạng thái đã tích luỹ ngoài kho không suy ra được
từ trong kho*.

#### Đường ống đã hành xử đúng — và đây là lần đầu chứng minh được

`migrator` chạy ở service **riêng**, `run --rm`, **trước** `up -d --force-recreate`. Nên:

- migration hỏng → bước "Triển khai" đỏ **trước khi** chạm container nào
- ba container vẫn là bản 03:17, `healthy`, site trả 200 suốt
- `Quay lui bản cũ` chạy và thành công (`→ [quay-lui] 511 byte, khớp hai đầu`)

⚠ Nhưng **không tick DOD0.21**: đường quay lui đã chạy trọn vẹn lần đầu, song đó là một lượt *không
có gì để quay lui* — container chưa hề bị thay. Nó chứng minh đường đi thông, chưa chứng minh nó
dựng lại được một bản đã bị thay.

📌 So với §10.60 (cũng 27/8): lần ấy bước "Triển khai" **xanh** trong khi không làm gì; lần này nó
**đỏ** đúng lúc phải đỏ. Cùng một bước, hai hành vi — khác nhau ở chỗ bản vá §10.60 làm nửa cuối
script thật sự chạy.

#### Đã vá

1. Khôi phục `V202608251100` về **đúng byte** bản staging đã áp (đối chiếu SHA-256 với bản ở
   `7b0b26a` — khớp). Nội dung chú thích có giá trị ấy chuyển về đây, không nhét lại vào tệp.
2. `backend/db-migration-checksums.txt` — vân tay SHA-256 của **41** tệp migration/seed trong nguồn.
3. `MigrationImmutabilityTest` (3 bài): tệp bị sửa → đỏ · tệp bị xoá → đỏ · tệp mới chưa ghi vân tay
   → đỏ kèm lệnh cần chạy. Kèm một bài canh chính manifest không lẫn `target/` (bản sao lúc build —
   `mvn clean` sẽ làm bộ canh đỏ mà không ai sửa gì) và `src/test/` (CSDL dùng-một-lần).
4. `make migration-manifest` → `backend/tools/sinh-vantay-migration.sh`, đã kiểm **chạy hai lần cho
   kết quả y hệt**.

#### Giới hạn, ghi ra thay vì để người đọc tự suy (luật 28)

- Bài dùng **SHA-256**, không phải thuật toán của Flyway. Với việc *phát hiện thay đổi* thì tương
  đương, nhưng SHA-256 **chặt hơn**: Flyway chuẩn hoá ký tự xuống dòng, SHA-256 thì không. Đỏ nhầm
  theo hướng an toàn.
- Bài **không** biết migration nào đã thật sự được áp ở đâu. Một migration thêm hôm nay, chưa chạy ở
  đâu cả, sửa vẫn vô hại — mà bài vẫn đỏ. Cố ý: "đã áp ở một CSDL nào đó trên đời" là thứ không kiểm
  được từ trong kho, và đoán sai theo hướng thoải mái thì đúng bằng không có bộ canh.

#### Bài học

**Chú thích trong tệp migration không phải là chú thích — nó là dữ liệu đã ký.** Muốn giải thích một
migration thì giải thích ở nơi khác. Và một lần nữa: *cây kiểm xanh không nói gì về trạng thái đã
tích luỹ ở môi trường thật.*

### §10.66 — Migration mới đánh số bằng GIỜ-PHÚT nên rơi xuống dưới bản đã áp (27/8)

CD Staging đỏ ở bước "Triển khai" — **lần thứ hai trong hai ngày, cùng một bước, khác nguyên nhân**.
`migrator` chết lúc dựng `flywayInitializer`:

```
Validate failed: Migrations have failed validation
Detected resolved migration not applied to database: 202608272320.
Detected resolved migration not applied to database: 202608272321.
```

Quy ước đánh số của dự án là `V<YYYYMMDD><số thứ tự 4 chữ số>` — chuỗi `1023, 1024, … 1038` chạy
xuyên suốt 43 tệp. Hai tệp thêm ở PR #53 dùng **giờ-phút**: `202608272320` = 23:20 ngày 27. Nhìn thì
giống, sắp thì không:

```
202608271035  ops_construction_public_documents     ← đã áp
202608272320  seed_portal_media_cong_ty             ← MỚI, rơi vào ĐÂY
202608272321  cms_home_video_value                  ← MỚI
202608281036  cms_menu_position_lien_ket            ← đã áp (PR #52)
202608281037  cms_drop_unread_site_settings         ← đã áp
202608281038  cms_home_video_settings               ← đã áp
```

Ngày 28 lớn hơn ngày 27, nên ba mã `…28xxxx` — vốn ra đời **trước** — lại đứng **sau**. Staging đã
áp tới `…281038`; hai mã mới nằm dưới nó ⇒ out-of-order ⇒ Flyway từ chối chạy. Đúng như thiết kế:
áp chèn vào giữa lịch sử là cách làm hai CSDL cùng lược đồ mà khác nội dung.

#### Hỏng thứ hai, im lặng hơn — và cùng một nguyên nhân

Cũng vì số hiệu sai thứ tự, seed `…2320` `UPDATE` khoá `site.home.photos-folder`, trong khi hàng ấy
do migration `…2321` mới `INSERT` ra. Trên một CSDL trắng, seed chạy **trước**, `UPDATE` chạm **đúng
0 hàng** — không lỗi, không cảnh báo, không dòng log nào. Khối ảnh trang chủ sẽ rỗng vĩnh viễn và
mọi bài kiểm vẫn xanh.

Nếu Flyway *không* chặn lượt deploy, thứ lên staging sẽ là một trang chủ thiếu ảnh mà không ai biết
tại sao. **Lượt đỏ này đã che cho một lỗi câm** — hình dạng luật 27 (nửa cặp đọc–ghi), lần thứ hai
trong ba ngày.

#### ⚠⚠ Vì sao 688 bài kiểm về nguyên tắc không thấy

Mọi bài kiểm chạy migration từ CSDL **rỗng**. Trên CSDL rỗng **không tồn tại khái niệm
out-of-order**: Flyway sắp mọi tệp theo version và áp tuần tự từ đầu, xanh trọn vẹn. Không có
`flyway_schema_history` cũ thì không có gì để "ngoài thứ tự" so với.

Cùng lớp mù với §10.65 (checksum) — và đó là **lần thứ hai liên tiếp** lớp mù ấy làm đỏ CD. Cả hai
đều là *trạng thái đã tích luỹ ở môi trường thật, không suy ra được từ trong kho*.

#### Hỏng thứ ba, do chính bản vá §10.60 đưa vào — dấu huyền trong heredoc không nháy

Giữa lượt deploy, log in ba dòng lạ **trước** khi khối được gửi đi:

```
requires at least 1 arg(s), only received 0
…/952e020a….sh: line 6: up: command not found
…/952e020a….sh: line 6: -T: command not found
…/952e020a….sh: line 6: chay-tu-xa.sh: command not found
```

Khối `trien-khai` mở bằng `<<REMOTE` **không nháy** — bắt buộc, vì nó cần `$APP_IMAGE`, `$COMPOSE`
của runner. Trong heredoc không nháy, dấu huyền là **thay thế lệnh**, kể cả nằm trong một dòng `#`:
với `bash` đó là chú thích, nhưng runner khai triển dòng ấy **trước khi** shell nào nhìn thấy nó.

Khối chú thích §10.60 viết hôm 27/8 có 5 cặp dấu huyền chưa thoát, nên runner chạy thật
`docker compose run`, `up -d --force-recreate`, `-T`, `chay-tu-xa.sh` và `</dev/null`. Vô hại lần
này (tất cả đều lỗi rồi thôi), nhưng đó là **may**: một dòng chú thích nhắc tới `rm -rf` hay
`docker compose down` sẽ chạy thật, trên runner, giữa lượt triển khai.

📌 Một chú thích **cảnh báo về lệnh nuốt stdin** lại tự nó chạy lệnh ấy. Cùng họ với §10.65: *thứ
trông như chú thích nhưng không phải chú thích.*

#### Đường ống lại hành xử đúng — lần thứ hai, và vẫn chưa đủ để tick DOD0.21

`migrator` chạy `run --rm` **trước** `up -d --force-recreate`, nên lượt hỏng dừng trước khi chạm
container nào; `Quay lui bản cũ` chạy và thành công (`→ [quay-lui] 511 byte, khớp hai đầu`), dịch vụ
trả lời sau 10 giây. Và vì `validate` chặn **trước** khi áp, **không migration nào được ghi vào
`flyway_schema_history`** — nên đổi tên hai tệp là an toàn ở staging/production.

⚠ Vẫn **không tick DOD0.21**, cùng lý do §10.65: không có gì bị thay thì không có gì để dựng lại.

#### Đã vá

1. Đổi tên theo đúng quy ước, và xếp cho **đúng chiều phụ thuộc**:
   `V202608281039__cms_home_video_value.sql` (tạo khoá) chạy **trước**
   `V202608281040__seed_portal_media_cong_ty.sql` (ghi giá trị). Ràng buộc ấy ghi vào chính đầu tệp
   1040, kèm lý do — không để người sau đọc số mà đoán.
2. `backend/tools/kiem-thu-tu-migration.sh` — so số hiệu migration **mới** với đỉnh của **nhánh
   nền**. Chạy ở `make ci-local` bước 2/10 và ở job CI mới `Thứ tự migration` (cần
   `fetch-depth: 0`). Kiểm chứng ngược **trên chính commit `3a39b79` đã làm đỏ CD**: đỏ, exit 1, gọi
   đích danh cả hai mã; cây đã sửa: xanh.
3. Thoát 5 cặp dấu huyền trong heredoc `trien-khai`, kèm 2 bài trong `DeployRemoteStdinTest`
   (8 bài): một bài soi tệp thật, một bài **chứng minh bộ dò bắt được vi phạm** — cố ý *không* dùng
   `boChuThich`, vì chính dòng `#` là chỗ đã nổ. Kiểm chứng ngược bằng cách nạp lại nội dung cũ:
   đỏ, đúng 5 dòng, đúng số hiệu dòng.
4. Sinh lại `db-migration-checksums.txt` → 43 vân tay.
5. Cổng gộp `Cổng kiểm CI` nâng ngưỡng `so_job` 8 → 9.

#### Giới hạn, ghi ra thay vì để người đọc tự suy (luật 28)

- Bộ canh lấy **`dev`** làm nền, không phải staging/production. Nếu có môi trường nào chạy trước
  `dev` thì phép so hụt — hiện không có.
- Nó **không** bắt việc xoá/đổi tên một migration đã phát hành; đó là vế `biXoa` của
  `MigrationImmutabilityTest`. Lượt `git mv` này đã bị đúng bài ấy chặn — đo lại bằng cách nạp
  manifest bản trước khi đổi tên (43 vân tay, 2 dòng mang số hiệu cũ): bài **đỏ**, nêu đích danh cả hai tệp
  kèm câu `applied migration not resolved locally`. Cơ chế hoạt động đúng.
- Nó chỉ biết **thứ tự**, không biết migration mới có đúng nghiệp vụ không.

#### Bài học

**Một quy ước đặt tên mà chỉ con người nhớ thì sớm muộn có người nhớ nhầm** — luật 14, ở dạng tên
tệp. Số hiệu migration trông như dấu thời gian nên rất dễ *viết như* dấu thời gian, và hai cách viết
chỉ khác nhau ở đúng chỗ không ai nhìn: thứ tự sắp xếp.

Và lần thứ hai trong hai ngày: **thứ quyết định lượt deploy sống hay chết nằm ở trạng thái CSDL đã
tích luỹ, không nằm trong kho** — nên bảo đảm cũng không thể chỉ là một bài kiểm chạy trên CSDL
rỗng.

### §10.67 — Bản vá sống trên đĩa, tiến trình đang phục vụ vẫn chạy mã cũ (28/8)

Lượt đồng bộ `master-tracking.md` lên Google Sheet trả về `Đã đồng bộ 452 công việc`. Cả 452 mã
đều có mặt trên bảng, không thiếu không thừa. Nhưng đọc ngược từng dòng thì **3 trạng thái sai**:

```
T11.7    cục bộ = In Progress   Sheet = Done       ← secret production, ĐANG LÀM
T11.50   cục bộ = Done          Sheet = Pending
T25.29   cục bộ = Done          Sheet = Pending
```

Đúng ba dòng ấy là ba dòng mà **phần chữ trong cột Note nói khác dấu tích** — tức đúng lỗi T11.47
đã vá hôm 27/8. Mô phỏng hành vi trước bản vá trên chính tệp tracking cho ra **đúng ba dòng, đúng
ba giá trị**. Không còn chỗ cho giả thuyết khác.

#### Vì sao một bản vá đã merge lại không có hiệu lực

Máy chủ MCP là **tiến trình sống lâu**. Nó `import` bộ đọc đúng một lần lúc khởi động rồi giữ
module trong bộ nhớ suốt phiên.

| | |
|---|---|
| bản vá `tracking_parser.py` vào kho | **27/08 19:36** (`992a586`) |
| tiến trình MCP #1 khởi động | **27/08 15:17:09** |
| tiến trình MCP #2 khởi động | **27/08 19:13:00** |
| `server.cpython-314.pyc` ghi lần cuối | 27/08 19:23:43 |

Cả hai tiến trình khởi động **trước** bản vá. `server.py` trên đĩa mang mốc 28/08 00:02 nhưng
bytecode vẫn là 19:23 — dấu hiệu đo được rằng **không tiến trình nào import lại kể từ đó**.

⛔ Bản ghi T11.47 nói *"đo trên file thật, đúng 2 dòng sai"* rồi coi như đóng. Phần ấy đúng — nhưng
nó đo **parser trên đĩa**, không đo **thứ đang chạy**. Cùng hình dạng luật 10: *xác nhận bản đã sửa
THẬT SỰ được nạp*. Ở §10.53 là container giữ image cũ; ở §10.56 là cluster giữ collation cũ; ở đây
là một tiến trình Python giữ module cũ. **Ba lần, ba tầng khác nhau, cùng một câu hỏi chưa ai hỏi:
thứ đang chạy có phải thứ vừa sửa không?**

#### Vì sao "ghi xong rồi đọc lại" không cứu được

Đây là chỗ dễ chọn nhầm bản vá. Một lượt đọc-ngược-sau-khi-ghi **vẫn xanh**: tiến trình cũ parse ra
giá trị cũ, ghi giá trị cũ, rồi đọc lại và thấy khớp — với **chính nó**. Hai trạng thái *"mã mới"*
và *"mã cũ"* không phân biệt được bằng phép ấy (luật 9: một khẳng định không phân biệt được hai
trạng thái thì không khẳng định gì).

Thứ duy nhất phân biệt được: **so mã trên ĐĨA với mã đã NẠP**.

#### Đã vá

1. Ba ô sai đã sửa bằng chính khoá dịch vụ của công cụ, có in trước–sau và đọc lại toàn bảng:
   **0 dòng còn lệch**.
2. `tracking_parser.van_tay_nguon(paths)` — SHA-256 của `tracking_parser.py` + `server.py`.
3. `server.py` chụp vân tay **lúc nạp**, và `sync_markdown_to_sheets` **từ chối ghi** khi vân tay
   trên đĩa đã khác, kèm câu chỉ thẳng việc phải làm (`/mcp` kết nối lại). Phép so đặt **trước**
   mọi lượt đọc/ghi — đặt sau thì bảng đã bị ghi đè trước khi ai kịp dừng.
4. Hai bài kiểm (bộ tracking nay 11): vân tay đổi khi nội dung đổi, trở lại khi khôi phục, phụ
   thuộc thứ tự tệp · `server.py` gọi cổng ấy **hai lượt** và phép so nằm **trước** lượt ghi.
   Kiểm chứng ngược: làm vân tay mù trước nội dung → đỏ · gỡ cổng khỏi `server.py` → đỏ · khôi
   phục → 0 đỏ.

#### Giới hạn, nói ra thay vì để người đọc tự suy (luật 28)

- Cổng chặn chỉ bắt được thay đổi xảy ra **sau** khi tiến trình nạp. Một tiến trình khởi động với
  mã đã hỏng sẵn thì nó không biết gì — đó vẫn là việc của bộ test.
- Nó **không** tự nạp lại mã; nó chỉ dừng và nói ra. Cố tự nạp lại module đang phục vụ là một cơ
  chế phức tạp hơn hẳn thứ nó chữa.
- ⚠ **Chính tiến trình đang chạy hôm nay vẫn là bản cũ** và không tự thấy được điều đó. Cổng chỉ
  có hiệu lực từ lượt kết nối lại kế tiếp.

#### Bài học

**Một tiến trình sống lâu là một bản sao của mã nguồn tại một thời điểm.** Kho có thể đã đi tiếp
mà nó thì không, và không lệnh nào báo sai. Mọi công cụ chạy nền — MCP server, worker, daemon —
cần một cách tự trả lời câu *"tôi có đang chạy đúng thứ trong kho không"*, vì không ai nhớ hỏi hộ.

---

### §10.68 — Lượt rà CI/CD 29/8: một cổng kiểm đỏ suốt một ngày không ai thấy, và một bước đỏ không nói được vì sao (29/8)

Hai phát hiện khác gốc, cùng lộ ra trong một lượt rà đường ống. Cả hai đều **không phải lỗi mã** —
nên cả hai đều không có bài kiểm nào ở máy bắt được, và đó là điểm chung đáng ghi.

#### A. `tomcat-embed-core` 10.1.57 — 9 mã CVE ≥ 7, đỏ từ 28/8 mà không ai biết

`Quét bảo mật phụ thuộc` xanh liên tục từ 20/8 tới **27/8 12:48**, rồi **đỏ từ 28/8 02:15 UTC** và
đỏ tiếp ở hai lượt sau. Giữa hai mốc ấy **không một dòng khai báo phụ thuộc nào đổi**.

Đây đúng là trường hợp `security-scan.yml` đã tự viết vào phần đầu của chính nó khi tách khỏi PR:

> *Kho phụ thuộc hôm nay không an toàn hơn vì có người mở PR, và nó kém an toàn đi kể cả khi không
> ai đụng vào mã — vì thế giới công bố thêm CVE.*

Lời ấy đúng, cơ chế dựng theo nó cũng đúng, và cơ chế đã **chạy đúng**: nó bắt được, đúng ngày.

⛔ **Chỗ hỏng nằm sau đó**: lượt quét chạy 02:15 UTC theo lịch, đỏ, rồi **không ai đọc trong hơn một
ngày**. Không có thông báo nào rời khỏi tab Actions. Đây không phải một bộ canh xanh giả — nó là
một **chuông reo trong phòng trống**, hình dạng thứ ba bên cạnh hai hình dạng đã biết
(*bộ canh không chạy* — §10.64; *bộ canh hẹp hơn nơi nó phải chặn* — §10.62 / luật 28).

Đọc **từ chính báo cáo lượt chạy 33244618650**, không đoán theo tên CVE:

| CVE | CVSS | `versionEndExcluding` |
|---|---|---|
| CVE-2026-65637 · CVE-2026-65905 | 9.8 | 10.1.58 |
| CVE-2026-65182 · CVE-2026-68525 | 9.1 | 10.1.58 |
| CVE-2026-65183 · CVE-2026-66422 · CVE-2026-68569 | 8.1 | 10.1.58 |
| CVE-2026-65927 · CVE-2026-68763 | 7.5 | 10.1.58 |

**Cả 9 mã vá ở đúng một bản** ⇒ không mã nào cần suppression (`conventions.md` §4.5 luật 1). Nâng
`<tomcat.version>` 10.1.57 → **10.1.59** (bản 10.1.x cao nhất trên `maven-metadata.xml` — tra bằng
metadata chứ không bằng API tìm kiếm, luật 21). Đo giá trị **đã giải** chứ không đọc lời khai trong
POM (luật 3): `dependency:tree` cho `tomcat-embed-core`, `-websocket` và `-el` đều ra **10.1.59**.

⭐ Nghiệm thu bằng **lượt quét thật** trên nhánh vá (run 33253652221), đọc báo cáo bằng chính bộ phân
tích đã dùng cho lượt đỏ: **110 artifact được quét** (không phải tập rỗng — luật 7), báo cáo mang đúng
`tomcat-embed-core-10.1.59.jar`, và **0 mã ≥ 7**.

⚠ Và sổ đang nói sai: `CLAUDE.md` khẳng định **"0 CVE ≥ 7"** như một thuộc tính của dự án. Nó không
phải thuộc tính — nó là **số đo có hạn dùng**, đúng tới ngày đo và tự hỏng theo thời gian mà không ai
chạm vào mã. Đã ghi lại kèm ngày đo.

#### B. Bước "Mở đường SSH" đỏ 6/6 mà không nói được vì sao

CD Staging của lượt đề bạt `6f86b47` đỏ ở bước *Mở đường SSH*: sáu lượt thử, mỗi lượt đúng 20 giây
(`ConnectTimeout`), rồi thoát. `deploy.yml` **không đổi một ký tự** kể từ lượt CD xanh gần nhất, và
lượt xanh ấy mở được kênh ở **lần thử 1 sau 5,6 giây** — nên nguyên nhân nằm ở máy chủ, không ở mã.

Đường ống lại hành xử đúng: nó dừng **trước** khi chạm container nào; `staging.songnhue.com` và
`admin-staging.songnhue.com` vẫn trả 200 suốt.

⛔ Nhưng bước ấy chạy `ssh … 2>/dev/null`. Nó **vứt đi câu trả lời** rồi in ba lệnh chẩn đoán để
người đi chạy tay trên máy chủ — trong khi client SSH đã viết sẵn lý do ra stderr. Ba nguyên nhân
thường gặp cần ba cách xử lý ngược nhau:

| client báo | nghĩa là | phải làm |
|---|---|---|
| `Connection refused` | có thứ **CHẶN** — fail2ban đã cấm IP runner, hoặc sshd không chạy | gỡ cấm / bật lại sshd |
| im lặng tới hết giờ, `Connection reset` | bị **THẢ** vì quá tải (§10.59) | thử lại là đúng |
| `Permission denied` | **SAI KHOÁ** | thử lại là vô nghĩa — đỏ ngay |

⭐ **Đo được, không phải lập luận**: cho bản cũ chạy qua ba `ssh` giả tương ứng ba nguyên nhân, đầu ra
**trùng từng byte** — cùng một vân tay `5ed3fce68b39` cho cả ba. Nó không phân biệt được gì (luật 9).
Bản mới cho ba vân tay khác nhau (`d3297a88…` / `d7da6704…` / `4b9a3b26…`), giữ nguyên văn lý do, và
với `Permission denied` thì **đỏ ngay ở lượt 1** thay vì tiêu thêm hai phút rồi báo nhầm là sự cố mạng.

Bộ canh: `DeploySshMultiplexTest` 5 → 8 bài, trong đó một bài **tự kiểm chứng** — nạp đúng nguyên văn
dòng `if ssh -o BatchMode=yes "$HOST" true 2>/dev/null; then` đã nằm trong kho tới 29/8 và đòi bộ dò
phải đỏ trước nó (luật 1). Kiểm chứng ngược trên tệp thật: khôi phục dòng cũ vào `deploy.yml` → bài
`khongDuocNuotLyDoSsh` **đỏ, gọi đích danh**; khôi phục lại → 8/8 xanh. Mẫu regex cố ý **không** khớp
`ssh-keyscan … 2>/dev/null` ở ngay trên — lượt kiểm chứng phân biệt được hai dòng ấy.

⚠ **Bản vá này chữa lượt SAU, không chữa lượt này.** Nguyên nhân gốc vẫn ở máy chủ và cần `sudo`.

⭐ Một số đo phụ, thu được ngoài ý muốn nhưng đáng giữ: dò SSH từ máy dev khiến **chính IP máy dev bị
fail2ban cấm** sau khoảng 20 lượt kết nối — cổng 22 chuyển từ mở sang `Connection refused`. Đó là
**bằng chứng chạy thật đầu tiên** rằng fail2ban của T11.45 đang cấm thật, chứ không chỉ `active`.
Nhưng nó cũng chỉ thẳng vào một rủi ro: `jail.local` trong `deploy-guideline.md` §2.2-b đặt
`mode = aggressive` + `maxretry = 3` và **không có `ignoreip`** — mà `mode = aggressive` tính cả
những lượt mở-rồi-đóng chưa xác thực. Một IP runner của GitHub trùng với IP từng bị quét sẽ bị thả
im lặng, và triệu chứng đúng bằng triệu chứng đã thấy: hết giờ, không lời giải thích.

#### Bài học

**Một cổng kiểm đỏ mà không ai đọc thì cũng gần bằng không có cổng kiểm** — nó khác hai hình dạng đã
biết ở chỗ cơ chế hoàn toàn đúng và vẫn không đổi được kết quả nào. Và **một số đo có hạn dùng thì
phải ghi kèm ngày đo**; viết `0 CVE ≥ 7` trần trụi vào sổ là biến một phép đo thành một lời hứa mà
không ai giữ được.

---

### §10.68-C — Lượt deploy tự cấm chính nó: `ssh-keyscan` làm mồi cho fail2ban (29/8)

Phần B ở trên vá được *cách báo lỗi* của bước `Mở đường SSH`. Đây là **nguyên nhân gốc** của lượt đỏ,
tìm ra sau khi QuanTran lấy được dữ liệu `sudo` trên VPS-2.

#### Bằng chứng khớp tới từng giây

```
19:27:03 Unable to negotiate with 52.230.251.196 port 39970: no matching host key type
         found. Their offer: sk-ssh-ed25519@openssh.com [preauth]
19:27:04 Connection closed by 52.230.251.196 port 39971 [preauth]
19:27:05 Connection closed by 52.230.251.196 port 39968 [preauth]
19:27:05 Connection closed by 52.230.251.196 port 39972 [preauth]
19:27:06 Unable to negotiate with 52.230.251.196 port 39969 … sk-ecdsa-sha2-nistp256
```

`52.230.251.196` thuộc dải Azure — nơi runner GitHub chạy — và nó nằm trong `Banned IP list`. Bước
*Mở đường SSH* khởi động **12:27:03 UTC = 19:27:03 giờ VN**. Năm kết nối song song, hai cái chào bằng
kiểu khoá `sk-*`: vân tay của **`ssh-keyscan`**, đúng dòng đầu tiên của bước ấy.

Tham số fail2ban **đo trên máy**, không đọc lại tài liệu: `bantime 3600` · `maxretry 3` ·
`findtime 600` · `mode = aggressive`. `aggressive` tính cả `Connection closed … [preauth]`.
Năm kết nối dò vượt ngưỡng ba **ngay lập tức**.

⛔ **Lượt deploy tự cấm chính nó bằng lệnh mở đầu của nó.** Sáu lượt `ssh` sau đó gõ vào bức tường mà
chính nó vừa dựng — và vì stderr bị `2>/dev/null` nuốt (phần B), sáu dòng cảnh báo không nói được gì.

#### Vì sao lượt trước vẫn xanh — và vì sao điều đó tệ hơn là an ủi

Lượt CD xanh gần nhất mở được kênh ở giây thứ **5,6**: `ssh-keyscan` chạy ~3s, rồi `ssh` chen vào
**trước khi** fail2ban kịp quét nhật ký và áp luật. Nó **thắng một cuộc đua**, không phải chạy đúng
thiết kế. Một đường ống mà kết quả phụ thuộc vào việc ai nhanh hơn ai vài giây thì "xanh" không còn là
tín hiệu — cùng họ với §10.62 (kết quả phụ thuộc *ai bấm F5 sau cùng*).

#### Vá — và nó vá luôn một lỗ hổng chưa ai gọi tên

Bỏ hẳn `ssh-keyscan`, ghim khoá công khai máy chủ vào secret `*_SSH_KNOWN_HOSTS`, khai
`StrictHostKeyChecking yes`.

⭐ Điều đáng nói: `ssh-keyscan` **nhận bất kỳ khoá nào máy chủ đưa ra rồi tin luôn**. Chạy lại ở *mỗi*
lượt deploy nghĩa là **không lượt nào thật sự xác minh** đang nói chuyện với đúng máy — ai chen vào
giữa sẽ nhận trọn khoá triển khai và toàn bộ nội dung deploy. Suốt từ WS-11 tới nay đường ống vẫn
`tin-lần-đầu` ở **mọi** lượt, tức là chưa từng có xác minh nào. Cái tưởng là bản vá độ ổn định hoá ra
đồng thời là bản vá bảo mật.

Cổng secret lên **năm** biến: thiếu `SSH_KNOWN_HOSTS` thì không nối được, nên phải chặn **sớm** ở cổng
thay vì hỏng muộn ở `ssh` với một câu không nhắc gì tới secret (luật 27 — nửa cặp đọc–ghi).

Bộ canh, tất cả có kiểm chứng ngược **có số đo trước mỗi lượt**: cấm `ssh-keyscan` quay lại (khôi phục
dòng cũ → đỏ đích danh; `grep -c` in `1` trước khi chạy) · buộc đọc `SSH_KNOWN_HOSTS` và khai
`StrictHostKeyChecking yes` · buộc cổng secret hỏi biến ấy (gỡ khỏi script → **hai** bài đỏ, trong đó
có bài `stagingRongThiBoQua` — vì đếm 4 và đếm 5 không còn khớp nhau). `DeploySshMultiplexTest`
8 → 11 bài · `SecretGateTest` 7 → 8 bài.

#### ⭐ Và bộ canh cũ đã bắt được chính đợt sửa này

Bản đầu của đợt vá đặt một dòng chú thích `# … \`ask\` …` **bên trong heredoc `<<CFG` không nháy** —
tức đúng lỗi §10.66 mà chính tôi vừa viết luật để chặn: trong heredoc không nháy, `#` không phải chú
thích mà là văn bản, và dấu huyền bị khai triển thành **thay thế lệnh**. `make ci-local` đỏ ngay,
`DeployRemoteStdinTest.heredocKhongNhayKhongThayTheLenh` gọi đích danh **1 dòng**.

Đây là lần đầu một bộ canh của dự án bắt được **người viết ra nó**, trong cùng một phiên. Nó cũng cho
thấy hình dạng lỗi này không phải chuyện cẩu thả một lần — nó là chỗ **rất dễ trượt chân**, vì viết
chú thích cạnh dòng cấu hình là phản xạ đúng ở mọi ngữ cảnh khác.

⚠ Kèm một cái bẫy nữa, cùng họ: thông báo hoàn tất của tác vụ nền báo `exit code 0` trong khi
**mã thoát thật của `make` là 2** — vì lệnh cuối trong khối là `echo`. Chỉ dòng
`MÃ THOÁT THẬT CỦA make = 2` do chính khối in ra mới lộ. Cùng bài học với `make ci-local | tail`.

#### Bài học

**Một cơ chế bảo vệ và một cơ chế tự động hoá đặt cạnh nhau mà không ai đối chiếu thì chúng sẽ ăn thịt
nhau.** fail2ban đúng, `ssh-keyscan` đúng, mỗi cái đọc riêng đều hợp lý — chỉ có điều một cái coi năm
kết nối vô danh là dấu hiệu tấn công, còn cái kia tạo ra đúng năm kết nối vô danh ở mỗi lượt deploy.
Không tài liệu nào sai; chỗ trống nằm **giữa** hai tài liệu.

⚠ Và lần này chỗ trống bị lộ ra bởi một tai nạn: tôi dò SSH quá tay, tự làm IP văn phòng bị cấm, và
chính việc phải vào bằng 5G mới lấy được `journalctl` — thứ lẽ ra phải lấy **ngay từ đầu**, thay vì
suy đoán ba lượt về nguyên nhân.

---

### §10.68-D — Secret nối được ba phần tư đường: cổng chặn đúng một lỗi lẽ ra không nên có (29/8)

Ngay sau khi §10.68-C lên `dev` và QuanTran đặt secret trên GitHub, lượt CD Staging kế tiếp đỏ:

```
SSH_KNOWN_HOSTS:
##[error]Cấu hình máy chủ staging DỞ DANG — thiếu: SSH_KNOWN_HOSTS
```

Secret **có** trên GitHub — đo qua API, environment `staging` có đủ 5 tên. Thứ thiếu là **đường dây**:
`SSH_KNOWN_HOSTS` được khai ở `deploy.yml` (`workflow_call.secrets`) và được kiểm ở
`kiem-secret-may-chu.sh`, nhưng **hai workflow gọi nó — `deploy-staging.yml` và `deploy-prod.yml` —
không truyền vào**. Trong `workflow_call`, secret không tự chảy xuống; caller phải ánh xạ từng cái.

Ba phần tư đường dây hoạt động hoàn hảo, và kết quả là chuỗi rỗng.

⭐ **Cổng kiểm hành xử ĐÚNG** — chặn ngay, gọi đích danh, kèm cách lấy giá trị. Đó chính là thứ
§10.57 dựng ra: thà đỏ sớm còn hơn hỏng muộn ở `ssh` với một câu không nhắc gì tới secret. Nhưng nó
đang chặn **một lỗi lẽ ra không nên tồn tại**.

⛔ **Và bài kiểm tôi vừa viết cho §10.68-C không bắt được.** `congSecretPhaiHoiKhoaGhim` khẳng định
chuỗi `SSH_KNOWN_HOSTS` **có mặt** trong `deploy.yml` và trong script — cả hai đều đúng, cả hai đều
xanh, và đường dây vẫn đứt. Một chuỗi ký tự có mặt ở hai tệp **không chứng minh được nó đã nối**.
Đúng luật 28: bộ canh hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời bảo đảm.

Đây là lần thứ hai trong cùng một phiên tôi tự dựng ra chỗ trống rồi tự vấp: lần trước là chú thích
trong heredoc (bị `DeployRemoteStdinTest` bắt), lần này là nửa đường dây (không bộ canh nào bắt, phải
đợi lượt CD thật).

#### Vá — đổi từ *"có chuỗi này không"* sang *"bốn tập có bằng nhau không"*

`DeploySecretWiringTest` đọc **bốn nơi** rồi so **tập hợp**, hai chiều:

| nơi | đọc gì |
|---|---|
| `deploy.yml` | khối `workflow_call.secrets:` |
| `deploy-staging.yml` | khối `secrets:` của lượt gọi |
| `deploy-prod.yml` | khối `secrets:` của lượt gọi |
| `kiem-secret-may-chu.sh` | danh sách trong `for ten in …` |

Thiếu ở caller → secret tới cổng dưới dạng **chuỗi rỗng**, không phân biệt được với *chưa đặt trên
GitHub* — nên người đọc log sẽ đi đặt lại một secret vốn đã có. Thừa ở cổng → đòi một secret không ai
truyền, chặn vĩnh viễn. Cả hai chiều đều phải bắt.

Kèm một bài chống **xanh trên tập rỗng** (luật 7): bốn tập rỗng cũng bằng nhau hoàn hảo, nên phải
khẳng định mỗi tập có ≥ 5 phần tử **và** chứa đúng những tên đã biết.

⭐ Kiểm chứng ngược, có số đo trước khi chạy: gỡ dòng khỏi `deploy-staging.yml` (`grep -c` in `0`) →
**2 bài đỏ**, gọi đích danh tệp và in ra cả hai tập để so; khôi phục (`grep -c` in `1`) → 3/3 xanh.

#### Bài học

**Khai một thứ và dùng một thứ chưa phải là nối nó.** Giữa hai đầu ấy còn những khâu trung gian không
ai nghĩ tới lúc sửa — ở đây là hai workflow gọi. Bộ canh đi theo **chuỗi ký tự** chỉ thấy hai đầu; bộ
canh đi theo **tập hợp, hai chiều, đủ mọi nơi tham gia** mới thấy khúc giữa.

### §10.69 — Trần 1MB không ai khai: mọi tệp lớn trả 500, và cả cơ chế hạn mức nghiệp vụ chưa từng quyết định điều gì (30/8)

#### Triệu chứng

Công ty tải ảnh sơ đồ hệ thống công trình lên staging — đúng ô vừa dựng xong hôm trước ở
§10.62/T26.66 — và nhận **500 Internal Server Error** trên
`POST /api/v1/cms/site-config/brand-images/site.home.map-image.attachment-id`.

#### Nguyên nhân gốc — đo trên log staging, không suy luận

```
ERROR ... GlobalExceptionHandler ... "Lỗi không lường trước"
  error.type: org.springframework.web.multipart.MaxUploadSizeExceededException
  at StandardMultipartHttpServletRequest.parseRequest
  at DispatcherServlet.checkMultipart          ← TRƯỚC KHI VÀO CONTROLLER
```

`application.yml` **chưa từng khai** `spring.servlet.multipart.*`, nên Spring Boot áp mặc định
**1MB/tệp**. Và mặc định ấy chặn ở `DispatcherServlet.checkMultipart` — trước cả `@RequirePermission`,
trước cả dòng mã nghiệp vụ đầu tiên.

#### ⭐ Thứ nặng hơn một lỗi 500

Hệ có một cơ chế hạn mức **đầy đủ và đúng**: bốn tham số `limits.upload.max-mb.{image,document,gis,video}`
= 10/50/100/500, nằm trong `settings`, sửa được trên giao diện, `AttachmentService` đọc thật, và
`AttachmentQuotaTest` chứng minh đổi giá trị thì hành vi đổi theo.

Cơ chế ấy **chưa từng quyết định điều gì**. Nó luôn nằm sau một trần thấp hơn mười lần mà không ai
nhìn thấy. Đây là quy tắc 3 nguyên văn — *canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH* — với một
vòng xoắn: **mặc định thắng cuộc lần này còn không phải của mình**, nó là của framework, không xuất
hiện trong bất kỳ tệp nào của dự án, nên không có gì để đọc và phát hiện ra.

Ghi nhận từ WS-12 (`AttachmentService` Javadoc) từng ghi *"bản đầu đọc một khoá CHƯA TỪNG ĐƯỢC SEED…
mọi lượt tải rơi về giá trị dự phòng"*. Sửa xong nửa ấy rồi, mà nửa còn lại vẫn treo 18 ngày.

#### ⚠⚠ Vì sao 723 bài kiểm về nguyên tắc không thể thấy

`AttachmentQuotaTest` gọi `attachments.upload(lenh)` — **thẳng vào service**, không qua bộ phân tích
multipart. Trần 1MB nằm ở tầng bài kiểm ấy không đi qua. Quy tắc 5 nguyên văn: *bài kiểm gọi thẳng
service không đi cùng đường với production*.

Và nó sống được nhờ một sự tình cờ **đo được**: `SELECT count(*) FILTER (WHERE size_bytes > 1048576)`
trên staging trả **0/39** — tệp lớn nhất từng được tải lên là 570 kB. Tấm sơ đồ hệ thống là tệp đầu
tiên vượt qua ngưỡng ấy. Quy tắc 25 ở dạng thụ động: một bộ canh chưa gặp dữ liệu thật chưa chứng minh
được gì, kể cả khi nó không tồn tại.

#### Bản vá — ba phần, và phần thứ ba chỉ lộ ra khi chạy thật

1. **Trần được khai tường minh**, `${UPLOAD_MAX_FILE_MB:120}MB`. ⛔ **Không** nâng lên 500 cho bằng
   `max-mb.video`: đường tải đi qua `byte[]` (`getBytes()` → `ImageSanitizer` → `storage.put`, ít nhất
   hai bản sao trong heap), heap staging đo được **1.076 GB** và JVM chạy `-XX:+ExitOnOutOfMemoryError`.
   Nâng trần lên 500MB không mở khoá gì — nó **đổi một lỗi 413 sạch sẽ lấy một lượt giết tiến trình**.
2. **`MaxUploadSizeExceededException` có nhánh riêng** → `SYS-0011`, **413**, kèm số MB đọc lại từ
   chính thuộc tính cấu hình (không phải hằng số chép tay — nếu không thì câu thông báo và hành vi thật
   trôi khỏi nhau ngay lượt đầu ai đó đặt `UPLOAD_MAX_FILE_MB`).
3. ⭐⭐ **`server.tomcat.max-swallow-size` = kích thước yêu cầu tối đa.** Phần này **không nằm trong kế
   hoạch** — nó lộ ra khi bài kiểm 413 chạy lần đầu và đỏ với
   `I/O error … chunked transfer encoding, state: READING_LENGTH`, chứ không đỏ vì sai mã lỗi. Tomcat
   từ chối tệp khi mới đọc được một phần thân yêu cầu; không đọc nốt phần còn lại thì nó **đóng kết
   nối**, và trình duyệt nhận một lỗi mạng trắng trơn. Mặc định 2MB — nhỏ hơn trần 120MB rất nhiều.
   Nghĩa là **đúng những lượt tải mà SYS-0011 sinh ra để giải thích lại là những lượt không bao giờ
   nhận được nó**: bộ bắt ngoại lệ vẫn chạy, log vẫn ghi, response vẫn dựng — chỉ là không ai đọc được.
4. `limits.upload.max-mb.video` **500 → 120** (`V202608301048`), kèm câu kẹp theo nhóm cho mọi khoá
   tương lai. Hạ số này không mất gì: **0 tệp video** từng được tải lên, và video trang chủ là nhúng
   YouTube (`site.home.video-id`), không phải tệp.

#### Kiểm chứng ngược — hai chiều, có số đo

| Làm hỏng | Đo trước khi chạy | Kết quả |
|---|---|---|
| Hạ trần về `1MB` (mô phỏng "quên khai") | `grep -c` = 1 | **3/3 bài đỏ**, mỗi bài gọi tên một thứ khác nhau |
| Gỡ `@ExceptionHandler(MaxUploadSizeExceededException)` | `grep -c` = **0** | đỏ với `SYS-0001 "Lỗi hệ thống, vui lòng thử lại"` — **tái hiện đúng triệu chứng staging** |

#### Bài học

**Một tham số cấu hình nói dối khó phát hiện hơn một tham số không ai đọc.** Quy tắc 15 canh cái thứ
hai (`limits.upload.max-mb.*` *có* mã đọc, *có* bài kiểm, *có* UI). Cái thứ nhất mang đủ mọi dấu hiệu
của một cơ chế đang chạy tốt, và chỉ sai ở chỗ **có một tầng thấp hơn nó nắm quyền quyết định thật**.
Muốn biết một hạn mức có thật hay không thì phải hỏi *"ai là người nói KHÔNG đầu tiên"*, chứ không
phải *"con số này có được đọc không"*.

**Hệ luận cho mọi tham số cấu hình về sau:** giá trị bày ra cho người dùng phải được ép ≤ trần kỹ thuật
bằng một phép kiểm, chứ không bằng việc người viết nhớ. Đó là `UploadSizeCeilingTest` mục 3 — nó đọc
**cả hai nguồn thật** (bảng `settings` và thuộc tính Spring đã giải) rồi so, nên không có chỗ nào để
một con số mới lọt vào mà không ai so lại.

---

### §10.70 — "Biên dịch được" đọc như "qua cổng kiểm"; và ba khuyết tật im lặng lộ ra ở lượt kiểm HTTP đầu tiên của `hydro` (1/9)

**Bối cảnh.** WS-27 + WS-28 dựng xong module `hydro` trên một máy **không có Maven/Docker và bị chặn
Maven Central**. Bản ghi 31/8 (T28.21) khai rất rõ và rất thật thà cái đã canh được: *cổng biên dịch
`javac 21` trên 395 tệp `src/main/java`, `JAVAC_EXIT=0`, classpath dựng từ `BOOT-INF/lib`; `tsc
--noEmit` sạch; `eslint` sạch; `menu.test.ts` 14/14 và `routerGuards.test.ts` 5/5 qua shim.* Cả bốn
đều **đúng**. Phiên 1/9 chạy trên máy có Java 21 + Docker 29.4.0 + Maven Central (200).

#### A. Bốn cổng đã chạy, năm cổng chưa ai chạm — và không có gì nói ra điều đó

Lượt `./mvnw verify` đầu tiên **đỏ ngay module thứ nhất**:

| # | Cổng | Cái gì đỏ | Vì sao 8 cổng hôm trước không thấy |
|---|---|---|---|
| 1 | Spotless | thứ tự import ở 2 tệp `core` (`PortalCachePort` chèn sai chỗ) | `javac` không có ý kiến gì về thứ tự import |
| 2 | Checkstyle | `ParameterNumber` ×2: `StationService.update` **14 tham số**, `ApiSourceService.update` 9 | như trên |
| 3 | Prettier | 4 tệp chưa định dạng (3 màn hình hydro + `router.tsx`) | `eslint` và `prettier` là hai cổng khác nhau, bước 4/10 và 5/10 |
| 4 | `npm test` | `error-map.test.ts` khẳng định **76 mã**, thực tế **79** | shim chỉ chạy 2 tệp test được chọn tay |
| 5 | ArchUnit | `SilentFailureRuleTest` đỏ vì `Station` | ArchUnit là **bài kiểm**, không phải trình biên dịch |

Không cổng nào trong số này *khó*. Vấn đề là **hình dạng của lời khai**: một danh sách bốn thứ đã
chạy, viết thật thà, đọc như một danh sách đầy đủ — vì nó không nói ra **cái đã KHÔNG chạy**. Cùng
họ luật 28 (*bộ canh phải nói ra phạm vi của chính nó*), nhưng ở tầng **bản ghi tiến độ** thay vì
tầng mã: cái xanh của bốn cổng đọc như một lời bảo đảm về mười cổng.

📌 **Hệ luận.** Khi không chạy được cổng kiểm thật, bản ghi phải liệt kê **cổng nào KHÔNG chạy được**,
không chỉ cổng nào đã chạy. `make ci-local` có đúng 10 bước đánh số sẵn — dùng chính danh sách đó làm
khuôn, và đánh dấu ⬜ cho từng bước chưa chạy.

#### B. Luật ArchUnit và thiết kế `Station` mâu thuẫn suốt WS-28 vì luật chưa từng chạy

`SCOPED_ENTITY_BAT_BUOC_MANG_FILTER` đòi điều kiện `@Filter` **bằng đúng** hằng dùng chung. `Station`
khai `(org_unit_id IS NULL OR <chuẩn>)` — và vế `IS NULL` là **bắt buộc**: `org_unit_id` nullable vì
19 điểm đo được seed trước khi OI-05 chốt 7 hay 8 Xí nghiệp, mà trong SQL `NULL IN (…)` cho ra `NULL`
chứ không phải `TRUE` ⇒ thiếu vế ấy thì **19/19 điểm đo vô hình với TẤT CẢ**, kể cả SUPER_ADMIN ở path
gốc. Cả hai bên đều đúng; T28.7 được tick ✅ và mâu thuẫn nằm im vì **không lượt nào chạy luật**.

⛔ **Cách vá SAI, và nó rất hấp dẫn**: nới luật thành `condition.contains(ORG_UNIT_FILTER_CONDITION)`.
Một dòng, hết đỏ, hằng dùng chung vẫn "được dùng". Nhưng nó cho qua cả `(1=1 OR <chuẩn>)` — chuỗi
chứa nguyên văn hằng, trong khi bộ lọc phạm vi **đã tắt hoàn toàn**: mọi Xí nghiệp thấy dữ liệu của
nhau, không lỗi, không log. Đúng loại hỏng âm thầm mà cả lớp luật ấy sinh ra để chặn.

**Cách vá đã dùng**: ngoại lệ phải thoả **CẢ HAI** vế — tên lớp có trong `PHAM_VI_NULL_DUOC_PHEP`
(hiện đúng `{Station}`) **và** điều kiện khớp mẫu chặt `^\(\w+ IS NULL OR <quote(chuẩn)>\)$`. Mẫu ép
vế nới phải là **một cột đơn `IS NULL`**, nên không viết được `1=1`; danh sách ép việc nới thành một
quyết định phải khai ra.

⭐ **Chi tiết đắt nhất của đợt này nằm ở bài tự-kiểm chứng.** Fixture `NullableScopeUnsanctioned`
(mang `1=1 OR`) cố ý **cũng nằm trong danh sách cho phép**. Nếu chỉ cho `NullableScopeCompliant` vào
danh sách thì bài `catchesUnsanctionedWidening` vẫn xanh — nhưng **xanh vì lý do sai**: nó đỏ do lớp
kia thiếu tên, chứ không phải do mẫu điều kiện bị từ chối. Cho cả hai vào thì thứ **duy nhất** phân
biệt chúng là hình dạng chuỗi — đúng thứ luật phải kiểm. Đây là §10.62 áp cho chính mình: *một bài
kiểm chứng ngược có thể sai theo đúng cách mà thứ nó kiểm chứng đang sai.*

#### C. Ba khuyết tật im lặng, và cả ba đều "lưu thành công"

WS-28 đóng với `StationScopeTest` + `ApiSourceServiceTest` — **cả hai gọi thẳng service**. Lượt kiểm
HTTP **đầu tiên** của ba controller `hydro` (`HydroCatalogueHttpTest`, 5 bài) bắt được ngay ba thứ:

1. **Đổi "Nguồn dữ liệu" của điểm đo bị vứt.** `StationsPage` render ô ấy **bắt buộc** ở cả hai chế
   độ và `PUT` trọn `values`; `StationService.update` không có tham số nào nhận nó. Màn hình báo *"Đã
   cập nhật điểm đo"*, `api_source_id` không đổi. ⚠ Im lặng **tuyệt đối** — vì `200 OK` là câu trả
   lời đúng cho mọi trường khác trong cùng lượt gửi.
2. **TECHNICIAN không tạo nổi một điểm đo nào.** Danh sách nguồn nạp sau
   `hasPermission('hyd:api-source:manage')`, mà TECHNICIAN — **vai trò DUY NHẤT ngoài SA/ADMIN có
   `hyd:station:manage`** — không có quyền ấy ⇒ ô bắt buộc rỗng vĩnh viễn. Đây là **T27.20 tái phát
   nguyên hình dạng, một ngày sau khi T27.20 được vá**: quyền cấp đúng, màn hình có thật, endpoint
   có thật, việc bị **chôn sau một quyền khác**.
3. **Cờ "Đang dùng" bị bỏ rơi** khi tạo loại chỉ số — nhận và validate rồi không truyền xuống service.

📌 **Đơn vị đếm.** Cả ba lọt qua `tsc`, `eslint`, `javac`, ArchUnit và 786 bài kiểm khác, vì mỗi mảnh
riêng lẻ đều đúng. Thứ sai là **vòng khép kín**: nhập → lưu → **đọc lại**. Đếm *màn hình đã dựng* thì
WS-28 xanh; đếm *vòng có khép không* thì ba vòng hở. (Luật 27, luật 5.)

#### D. Trả một nợ ở ba điểm ghi không đóng được lớp lỗi

T27.7 (31/8) nối `PortalCachePort` vào `OrgUnitService`, `OrgUnitLeaderService`, `ConstructionService`
— ba điểm ghi **đã biết**. Cùng đợt ấy, T27.16/T27.17 đưa **tình hình vận hành** lên cổng công khai
lần đầu. Nhưng `ConstructionOperationStatusService` có **0** lời gọi `portalCache`: trực ban bấm Lưu,
cổng vẫn hiện mã cũ tới 5 phút — **đúng nguyên văn triệu chứng §10.62 mà T27.7 vừa đi trả nợ**, tái
phát ở một đường ghi thứ tư, trong cùng một đợt làm việc.

📌 Nợ dạng "nối cơ chế X vào N nơi đang cần" không đóng được bằng cách nối đủ N nơi — vì N tăng. Cái
đóng được nó là một phép kiểm hỏi **"còn đường ghi nào chạm dữ liệu cổng mà không báo cache không?"**
Chưa có phép kiểm ấy; `PortalCacheInvalidationTest` hiện canh **bốn** đường ghi cụ thể và **tự khai
giới hạn đó** (luật 28).

#### E. Hai bẫy về công cụ, đo được trong chính phiên này

1. ⚠⚠ **`./mvnw -pl app test` KHÔNG có `-am` chạy trên jar module khác CŨ trong repo local.** Lượt
   kiểm chứng ngược đầu tiên của T27.18 — gỡ chốt `daCongBoTaiLieu` rồi chạy lại — báo **7/7 XANH**.
   Bản hỏng nằm trên đĩa (đo được: `grep -c` từ 2 xuống 1) nhưng **chưa từng được nạp**: Maven lấy
   `songnhue-operations` từ repo local. Thêm `-am` → **2 bài đỏ đúng chỗ**. Đây là luật 10 ở một hình
   dạng mới, và là hình dạng khó thấy nhất của nó: *bản hỏng có thật, lệnh chạy thật, kết quả xanh
   thật, và cả ba không nói về cùng một đống bytecode.*
2. **Reactor dừng ở module đầu che mất phần còn lại** (luật 11 tái diễn): Spotless đỏ ở `core` nên
   `hydro` và `app` không chạy — hai cổng đỏ tiếp theo chỉ lộ ra ở lượt thứ ba. Dùng `-fae` khi mục
   tiêu là **kiểm kê** chứ không phải **chặn**. ⚠ `-fae` không cứu được trường hợp module sau *phụ
   thuộc* module đỏ (`app` vẫn SKIPPED khi `hydro` đỏ ở pha `validate`).

#### F. Một bộ canh mới, và nó đỏ ngay lượt chạy đầu

T27.2 hứa *"bài kiểm mọi `V*.sql` khớp mẫu"*. Nhưng **canh hình dạng ở đây là vô dụng**:
`V202608272320` — chính tệp đã gây hai lượt CD đỏ (§10.66) — khớp hoàn hảo mẫu "8 chữ số + 4 chữ số".
Bất biến **thật**: sắp mọi migration theo số hiệu đầy đủ (đúng thứ tự Flyway áp) thì phần `nnnn` cũng
phải **tăng dần**; giờ-phút phá đúng điều đó và không phá gì khác (luật 9 — *đo cái thật sự khác giữa
hai cấu hình, đừng khẳng định cái nghe có vẻ đúng*).

`MigrationNamingTest` đỏ ngay lượt đầu, và **đúng**: `V202608241255` / `V202608241256` mang `1255` /
`1256` = **12:55 / 12:56**, lạc hẳn khỏi dãy `1001…1049` của cả kho. Đã merge (`c4a49ef`) và đã áp
staging ⇒ **không đổi tên được** (đổi số hiệu một migration đã ghi vào `flyway_schema_history` là
lượt khởi động kế tiếp báo thiếu bản đã áp). Baseline đúng hai tệp ấy, kèm một bài canh **danh sách
không dài thêm** — đường ranh, không phải chỗ để dọn.

⚠ Và nguyên nhân gốc của T27.2 lớn hơn cái tên nó: quy ước SAI vẫn nằm ở **`conventions.md` §1.2 —
nguồn sự thật** — cùng **4/5 README module** chép lại từ đó. Lượt 31/8 sửa đúng **1/6 nơi** (`hyd/`).
Người viết migration kế tiếp sẽ mở `conventions.md` và chép lại đúng lỗi đã làm đỏ hai lượt CD.

---

### §10.71 — CVE 9.8 không có bản vá trên dòng đang dùng, và một suppression bị bỏ quên sau lượt nâng cấp (1/9)

Lượt quét theo lịch 1/9 đỏ. Giả thuyết đầu tiên của QuanTran là **khoá NVD hết hạn** — hợp lý, vì
lượt quét cần khoá và nó vừa xanh hôm qua. Nhưng log nói khác: `NVD_API_KEY: ***` có mặt, lượt quét
**chạy trọn** và trả về báo cáo **110 artifact**. Khoá hỏng thì trượt ở bước cập nhật CSDL và không
ra được kết quả nào.

Đỏ vì một CVE thật: **CVE-2026-59313 (9.8)** ở `spring-core` và `spring-web` 6.2.19. Lại đúng hình
dạng §10.68-A — mã không đổi, **thế giới đổi**; lần này chỉ cách lần trước ba ngày.

#### Chỗ khác với lần trước: không có bản vá để nâng lên

`tomcat` lần trước có `versionEndExcluding = 10.1.58` và 10.1.59 nằm sẵn trên Central — nâng một dòng
là xong. Lần này advisory dùng **`versionEndIncluding`**, tức bản đang dùng *bị dính* và bản vá là
bản kế tiếp. Đo ngày 1/9:

| dòng | dính tới `<=` | Central cao nhất | |
|---|---|---|---|
| 5.3.x | 5.3.49 | 5.3.39 | chưa có bản vá công khai |
| 6.0.x | 6.0.30 | 6.0.23 | chưa có bản vá công khai |
| 6.1.x | 6.1.28 | 6.1.21 | chưa có bản vá công khai |
| **6.2.x** | **6.2.19** | **6.2.19** | **chưa có** — `6.2.20` trả **HTTP 404** |
| 7.0.x | 7.0.8 | 7.0.9 | ĐÃ CÓ |

⭐ Số cao hơn ở ba dòng đầu (5.3.49, 6.0.30, 6.1.28) **không tồn tại trên Central** — đó là các bản
hỗ trợ thương mại. Đường duy nhất tới bản đã sửa là **Spring Framework 7** ⇒ **Spring Boot 4**, cuộc
di trú mà `pom.xml` đã cố ý tách thành hạng mục riêng và ghi rõ *"không gộp vào một lượt vá bảo mật"*.

Nên đây là trường hợp `conventions.md` §4.5 luật 1 chừa ra: nâng cấp **không khả dụng**.

#### Lý do suppression phải là *"không áp dụng"*, và nó được chứng minh chứ không được khẳng định

Luật số 2 của `dependency-check-suppressions.xml`: *"Lý do phải nói ĐƯỢC hay KHÔNG áp dụng, không phải
'chưa có bản vá'."* CVE đòi **cả hai** điều kiện: *functional web framework* **và** *Server-Sent
Events*. Đếm trên toàn `backend/`, chạy dưới `bash` kèm **đối chứng phải-tìm-thấy**:

```
ĐỐI CHỨNG   @RestController 37 · @GetMapping 33          ← phép đo hoạt động
ĐIỀU KIỆN   RouterFunction(s) 0 · HandlerFunction 0 · SseEmitter 0
            ServerSentEvent 0 · TEXT_EVENT_STREAM 0 · text/event-stream 0
            reactor.core 0 · webflux 0
```

⚠ Lượt đếm **đầu tiên** cho 0 ở *tất cả* các mục — kể cả những mục chắc chắn phải có. Nguyên nhân:
zsh nuốt `--include=*.java` nên `grep` không quét gì. **Một phép đo trả 0 ở mọi ô là phép đo hỏng,
không phải kết quả tốt** (luật 20). Đối chứng là thứ phân biệt được hai trạng thái ấy.

`StreamingResponseBody` khớp đúng 1 tệp, và nó nằm trong **một dòng chú thích** của
`ResponseEnvelopeAdvice` — không phải mã chạy. Hết hạn đặt **15/10**, cố ý ngắn: 6.2.20 nhiều khả năng
ra trong vài tuần.

#### ⛔ Và lượt nâng tomcat ba ngày trước đã bỏ quên suppression của chính nó

Tệp còn một mục suppress `CVE-2026-66299` cho `tomcat-embed-core` **10.1.57**, kèm dòng tự dặn:
*"Xem lại khi 10.1.58 lên Central — lúc đó **xoá mục này và nâng phiên bản**, đừng gia hạn."*
§10.68-A đã nâng lên **10.1.59** và **không xoá mục ấy**.

Chứng minh nó đã thành rác chứ không đoán: báo cáo dependency-check ghi riêng phần
`suppressedVulnerabilities`, và `tomcat-embed-core-10.1.59.jar` cho **rỗng ở cả hai** — không mã nào
đang hiện, cũng không mã nào đang bị che. Suppression ấy không còn khớp gì.

Vô hại lần này, nhưng hình dạng thì không: **một suppression sống lâu hơn lý do của nó là một cái bẫy
đặt sẵn**. Ai đó hạ phiên bản tomcat vì lý do khác, và một CVE 7.5 sẽ bị che trong im lặng.

#### Bộ canh

Tệp suppression tự khai bốn luật ở đầu nó, và tới 1/9 **không gì thi hành cả** — trong khi đây là chỗ
**duy nhất** trong dự án mà một dòng chữ làm một CVE 9.8 biến mất khỏi mọi bảng điều khiển mà cổng
quét vẫn xanh. `SuppressionPolicyTest` 4 bài: mọi mục phải có `until` · phải có `<notes>` đủ dài để là
một lượt thẩm định thật · phải giới hạn phạm vi (`packageUrl`/`gav`/`filePath`/`cpe`) và chỉ đích danh
CVE · và một bài chống **xanh trên tập rỗng** (ba bài trên duyệt một danh sách; danh sách rỗng thì cả
ba xanh mà không kiểm gì — luật 7).

Kiểm chứng ngược **trên tệp thật**, có số đo trước khi chạy: gỡ `until` của một mục (`grep -c` 2 → 1)
→ đỏ đích danh; khôi phục (1 → 2) → 4/4 xanh.

#### ⭐ Lượt nghiệm thu bắt được bản vá đầu tiên còn hẹp

Chạy lại chính workflow quét trên nhánh vá — và nó **đỏ tiếp**, với cùng mã ấy ở **`spring-tx`** và
**`spring-webmvc`**. Mẫu đầu tiên viết `spring-(core|web)` vì đó là hai artifact xuất hiện trong log
lượt đỏ. Nhưng Dependency-Check khớp qua CPE `spring_framework`, tức nó gán lỗ hổng cho **mọi**
artifact của cùng release train. Đo trên báo cáo thật: **4** artifact `spring-*@6.2.19` mang mã này.

⛔ Đây đúng là luật 25 — **liệt kê tên là bắt theo từng loại dữ liệu, và luôn có loại thứ tư lọt qua**
— và tôi vấp nó ngay trong lượt sửa mà mình đang viết luật cho tệp ấy. Thứ cứu được không phải suy
luận mà là **chạy lại phép quét thật** thay vì tin rằng suppression đã đủ.

Lượt nghiệm thu **thứ hai** vẫn đỏ — `spring-context-support`. Hai lỗi độc lập: `[a-z]+` không khớp
dấu gạch nối; và **tập artifact mang mã này KHÔNG ổn định giữa các lượt quét trên cùng cây mã** —
lượt 1 báo `core · tx · web · webmvc`, lượt 2 báo `context-support · core · web`. Dependency-Check gán
CPE `spring_framework` cho một tập con **khác nhau mỗi lần**.

⛔⛔ Nghĩa là **mọi mẫu liệt kê tên artifact đều không thể đủ** — kể cả khi hôm nay nó phủ đúng tất cả
những gì báo cáo đang hiện. Đây là một bậc nặng hơn luật 25: không phải *"luôn có loại thứ tư"* mà là
*"tập cần phủ không đứng yên để mà đếm"*. Bất biến thật là **nhóm + phiên bản**:
`^pkg:maven/org\.springframework/[^/]+@6\.2\.19$`. **Ghim `@6.2.19` là cố ý**:
nó làm mục này *không thể sống lâu hơn phiên bản đã thẩm định* — nâng lên 6.2.20 thì mẫu hết khớp và
phép quét tự nói lại sự thật. Đó là bản vá trực tiếp cho đúng cái bẫy tomcat ở ngay trên.

Lượt quét **thứ ba** xanh, đọc trên báo cáo chứ không đọc màu job: **110 artifact · 0 mã ≥ 7**, phần
bị suppress đúng bộ đã khai và không thừa mục nào.

#### Bài học

**Nâng cấp xong phải đi xoá suppression mà nó vừa làm cho thừa** — lượt nâng chỉ hoàn tất khi lý do
cũ được gỡ đi, nếu không ta để lại một tấm chắn không ai nhớ là còn đó. Và **ràng buộc của một tệp
phải nằm trong một bài kiểm, không nằm trong phần chú thích của chính tệp ấy**: người thêm mục thứ ba
là người không đọc phần đầu.

---

### §10.72 — Một lượt đề bạt gộp bằng Squash làm gãy gốc chung, và xung đột giả ấy khoá luôn cổng kiểm bắt buộc (1/9)

**Triệu chứng.** CI trên `dev@4543d82` xanh trọn vẹn — 10 job `success`, 1 `skipped`. Mở PR đề bạt
`dev → staging` (#76): GitHub trả `mergeable = CONFLICTING`, `mergeStateStatus = DIRTY`, **13 tệp
xung đột**, trong đó có cả `page.tsx`, `PortalNav.tsx`, `PortalCache.java` — những tệp mà lượt đề bạt
lẽ ra chỉ việc chuyển nguyên qua.

**Và thứ đắt hơn hẳn:** danh sách check của PR #76 có đủ 11 mục của `dev`, nhưng **không có
`Promotion guard`** — đúng context bắt buộc DUY NHẤT của nhánh `staging`. Nó không đỏ. Nó **không tồn
tại**. Lượt chạy `promotion-guard.yml` gần nhất là 31/8, không có lượt nào cho #76.

**Nguyên nhân gốc.** PR đề bạt **#72** đã được gộp bằng **Squash and merge**. Đo được:

```
b4a0ac0  cha=1  feat(fe): tìm kiếm xuống thanh nav… (#70) (#72)   ← squash
6866f70  cha=2  Merge pull request #68 from team-dev-qnt/dev      ← đúng luồng
22876c8  cha=2  fix(db): migration đánh số bằng giờ-phút… (#56)
fe0d5ff  cha=2  Merge pull request #66 from team-dev-qnt/dev
```

Squash tạo một commit **mới**, một cha, mang đúng nội dung của `dev@2add2bf` nhưng **không nối vào
lịch sử `dev`**. Về nội dung không mất gì — đo được `git diff --name-only 2add2bf b4a0ac0` = **0 tệp**,
và `2add2bf` là tổ tiên của `origin/dev`. Về đồ thị thì gốc chung **đứng yên** ở `bbe0b50` (30/8, #67).

Hệ quả dây chuyền:

1. Lượt đề bạt kế tiếp phải áp lại nguyên delta của #70 lên một `staging` **vốn đã có nó**. Ở mọi tệp
   mà #73/#75 đụng tiếp sau đó, git thấy hai bên cùng sửa một vùng ⇒ **xung đột giả**.
2. GitHub dựng `refs/pull/N/merge` để chạy workflow `pull_request`. PR đụng độ thì ref ấy **không dựng
   được**, nên `Promotion guard` không bao giờ được lên lịch và context bắt buộc treo vĩnh viễn ở
   *"Expected — waiting for status to be reported"*.

📌 Đây là hình dạng §10.63 lặp lại ở một chỗ khác: **một cổng kiểm không chạy không đọc như một cổng
kiểm đỏ**. Nó đọc như *chưa xong*, và một PR chưa xong thì người ta đợi chứ không điều tra. Cùng họ
với luật 24 (`skipped` được tính là ĐẠT) — cái nguy hiểm không phải màu đỏ, mà là **sự vắng mặt**.

**⛔⛔ Chuông ĐÃ kêu — đúng lúc, đúng tên, và không ai nghe.** Đây là phần đắt nhất của vụ này.

`deploy-staging.yml` phát hiện được ngay tại thời điểm gây ra lỗi. Dòng 194 của log lượt
`33452639951`, lúc **31/8 23:54:54** — chưa đầy một phút sau khi #72 được gộp:

```
##[warning]Không nối được staging với dev qua merge-base — PR nhiều khả năng đã bị squash/rebase.
⚠ Lần sau chọn 'Create a merge commit' khi merge vào staging.
```

Gọi đúng nguyên nhân, nói đúng việc phải làm. Nó trôi qua vì **job màu xanh**.

Và lý do nó chỉ còn là một cảnh báo thì nằm ở chính một bản vá tốt: §10.42 cho `deploy-staging.yml`
giải image theo **cây tệp** thay vì theo `HEAD^2`, nên một lượt đề bạt bị squash **vẫn deploy thành
công**. Đó là bản vá đúng — lượt deploy không nên chết vì ai bấm nhầm nút. Nhưng nó đã đổi một lần
**DỪNG HẲN** lấy một dòng `::warning::` trên một lượt chạy xanh, và không ai để ý rằng lần dừng hẳn
ấy chính là **chuông báo duy nhất** của lỗi này.

📌 **Làm cho một sự cố sống sót được mà không dời chuông sang chỗ khác là gỡ mất chuông.** Cùng họ
§10.68-A (*cổng quét CVE đỏ hơn một ngày không ai đọc*) nhưng nặng hơn một bậc: lần ấy ít nhất còn có
màu đỏ. `docs/cicd.md` §9 khi ấy vẫn khẳng định *"làm sai ở vế thứ hai thì hỏng to tiếng"* — câu đó
đã hết đúng từ ngày §10.42 vào kho, và không ai sửa lại nó vì không ai có lý do quay lại đọc.

**Vì sao không bộ canh nào thấy.** `promotion-guard.yml` kiểm hai điều: nhánh nguồn đúng chặng trước,
và commit ấy đã xanh CI. Cả hai đều **đúng** ở đây. Không câu nào hỏi *"hai nhánh còn chung gốc
không"* — và nó không thể hỏi, vì bản thân nó đã không được chạy. Bộ canh chỉ chặn được ở lượt đề bạt
**kế tiếp**, không chặn được nút Squash: GitHub **không có** tuỳ chọn tắt squash cho riêng một nhánh.

**Bản vá.**

1. **Nối lại gốc chung, không đổi một byte** — `git merge -s ours origin/staging` trên một nhánh cắt từ
   `dev`. Giữ nguyên cây của `dev` (đo: hash cây trước = sau = `251fb445`, `git diff` 0 tệp), chỉ ghi
   lại quan hệ cha. An toàn *vì đã đo* rằng `staging` không có nội dung nào `dev` chưa có — không phải
   vì tin là thế.
2. **`.github/scripts/kiem-goc-chung.sh`** — bất biến đo được: nhánh đích không được có commit
   **không-phải-merge** nào mà nhánh nguồn không có. Đo 1/9 trước vá: **1**. Sau vá: **0**.
3. **Nối vào `promotion-guard.yml`** kèm `fetch-depth: 0` — clone nông làm `git rev-list A..B` trả rỗng,
   và rỗng trông y hệt *sạch* (luật 7). Script tự in số đếm được trước khi kết luận nên một lượt clone
   nông lộ ra ở dòng `hơn … 0 commit`.
4. **`PromotionAncestryTest` 6 bài** — dựng kho git thật trong `@TempDir`, tái hiện squash ⇒ đỏ, merge
   commit ⇒ xanh, `merge -s ours` ⇒ xanh và cây không đổi. Luật 9: nếu chỉ có bài "squash phải đỏ" thì
   một script `exit 1` vô điều kiện cũng qua.

**Kiểm chứng ngược, có số đo trước mỗi lượt** (luật 10 — bản hỏng phải được nạp *và* bộ canh phải nhìn
thấy nó):

| Đột biến | Số đo bản hỏng | Kết quả |
|---|---|---|
| `if [ "$so_rieng" -eq 0 ]` → `if true` | `grep -c 'if true; then'` = 1 | ĐỎ đích danh `squashLamGayGocChungThiPhaiDo` |
| gỡ `fetch-depth: 0` | `grep -c 'fetch-depth: 0'` = 0 | ĐỎ đích danh `phaiCheckoutDuLichSu` |
| khôi phục cả hai | `grep -c` về 1 và 1 | 6/6 xanh |

**Nợ để lại, ghi thẳng vì bộ canh hẹp hơn nơi nó phải chặn (luật 28).** Bộ canh này chặn ở lượt kế
tiếp. Thứ chặn được tận gốc là **cách gộp**: PR đề bạt phải dùng *Create a merge commit*, squash chỉ
dành cho PR tính năng vào `dev`. Điều đó hiện chỉ nằm trong tài liệu và trong thông báo lỗi của script
— không có cơ chế kỹ thuật nào cưỡng chế được nó ở tầng GitHub.

**⛔⛔ Và cách chữa hiển nhiên nhất KHÔNG chạy được — đo ra ở bước cuối.** Bản đầu của bộ canh in
hướng dẫn: *"`git merge -s ours origin/staging` rồi mở PR vào `dev`, gộp bằng merge commit"*. Tôi đã
dựng đúng commit ấy — cây trước = sau = `251fb445`, 0 tệp đổi — rồi mới đọc `branches/dev/protection`:

```
required_linear_history : true     ← dev KHÔNG nhận merge commit
required_status_checks  : ["Cổng kiểm CI"]
```

Squash và rebase đều **xoá đúng cái quan hệ cha** cần dựng, nên PR ấy về nguyên tắc không gộp được
theo cách có ích. Một hướng dẫn sai trong thông báo lỗi tệ hơn không có hướng dẫn: nó gửi người đọc
đi làm một việc bất khả rồi kết luận là bộ canh hỏng. Script nay in đúng hai lối còn lại, cả hai đều
cần quyền quản trị kho, kèm lệnh cụ thể và một phép so **hash cây** để dừng lại nếu lệch:

| | [A] tạm tắt `required_linear_history` | [B] admin gộp thẳng trên `staging` |
|---|---|---|
| Đổi cài đặt | có — hoàn nguyên được | không |
| Qua `Promotion guard` | **có** | không (kho đặt `enforce_admins: false` nên admin đi qua được) |
| Viết lại lịch sử | không | không |
| Đổi nội dung | không | không |

📌 Bài học lặp lại lần thứ ba trong dự án: **một quy trình chỉ đúng khi đã đối chiếu với cấu hình
đang chạy, không phải với cấu hình mình nhớ.** Cùng họ §10.57 (cổng secret bỏ qua trong im lặng) và
luật 3 (canh giá trị ĐÃ GIẢI, đừng canh giá trị mặc định).

📌 **Một lỗi đánh số phát hiện cùng lượt**: `architecture-review.md` có **hai** mục cùng mang số
`§10.69` (trần 1MB 30/8 và CVE spring 1/9), và `T11.61` trỏ vào số ấy — tức trỏ nhầm mục. Đã đổi mục
CVE thành **§10.71** và sửa con trỏ. Cùng họ với §10.66: một dãy số **trông như** tự tăng thì rất dễ
viết trùng, và chỗ trùng chỉ lộ ra khi có người lần theo con trỏ.

**Đuôi 1/9 — cổng vừa dựng xong thì đỏ giả ngay lượt chạy đầu tiên.** Gộp #77 lúc 11:06:0x làm head
SHA của PR #76 đổi, và `Promotion guard` chạy **lúc 11:06:19** — trước khi CI của `dev@4e564d9` kịp
bắt đầu. API trả `conclusion: null` cho hai check bắt buộc, và bước cũ rơi vào nhánh `*)`:

```
##[error]Backend — build, lint, test của commit 4e564d9 kết thúc với 'null'.
```

Câu ấy **nói sai chuyện đang xảy ra**: không có gì kết thúc cả. Cổng đỏ, PR #76 bị chặn, và người đọc
log bị dẫn đi tìm một lượt CI hỏng vốn không tồn tại. ⭐ Luật 9 đúng nguyên văn — `null` bảo *đợi
thêm*, `failure` bảo *dừng lại và đi sửa*, mà một nhánh `*)` trộn chung cả hai.

⚠ Và nó là một **cuộc đua**, nên nó không xảy ra mọi lần: mở PR đề bạt lâu sau lượt gộp thì cổng
xanh. Loại lỗi chỉ hiện ra khi hai việc xảy ra sát nhau là loại **dễ đóng hồ sơ nhầm nhất** —
*"chạy lại thấy xanh rồi"* là một kết luận đúng về triệu chứng và sai về nguyên nhân.

Vá: tách phần phân loại ra `.github/scripts/phan-loai-check-chang-truoc.sh` (0 = đạt · 2 = chưa xong ·
1 = hỏng, **hỏng thắng chưa-xong** — đợi thêm một thứ đã đỏ là mời người ta đợi vô ích); workflow chờ
tối đa 20×30s rồi **ĐỎ** chứ không đi tiếp. ⭐ Tách ra là để **kiểm chứng được**: nằm trong bước
`run:` thì muốn thử nhánh `null` phải *thắng một cuộc đua* mới tái hiện được; tách ra thì ba dòng đầu
vào tổng hợp là đo được cả ba nhánh. `PromotionCheckStateTest` 6 bài. Kiểm chứng ngược khôi phục đúng
lỗi cũ (`grep -c 'còn đang chạy'` = 0) ⇒ tái hiện **nguyên văn** `kết thúc với 'null'`, bài kiểm đỏ
đích danh; gỡ `sleep 30` (`grep -c` = 0) ⇒ đỏ đích danh bài canh vòng chờ.

⛔ **Một xanh giả bắt được ngay trong lượt này**: `-Dtest='A+B'` là cú pháp SAI của surefire (phải là
dấu phẩy). Lượt chạy đầu báo `MÃ THOÁT THẬT = 0` trong khi lớp kiểm mới **chưa từng chạy** — chỉ phép
đếm số tệp báo cáo (`ls … | wc -l`, cần = 2) mới nói ra. Luật 7 ở dạng công cụ: một bộ chọn không
khớp gì cũng cho ra một lượt chạy xanh trọn vẹn.
