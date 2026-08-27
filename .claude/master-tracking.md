# Master Tracking Board

> **Quy tắc (SSoT)**: Mọi thao tác cập nhật tiến độ CHỈ ĐƯỢC thực hiện tại file này.
> 1. Trạng thái chỉ dùng: `[x]` (Xong), `[~]` (Đang làm), `[ ]` (Chưa làm).
> 2. Giữ nội dung vắn tắt, trọng tâm.

## WS-1

- [x] T1.1: Tạo cấu trúc monorepo + `.gitignore`, `.editorconfig`, `.gitattributes` | Date: 23/08/2026
- [x] T1.2: Maven parent `backend/pom.xml`: Java 21, Spring Boot 3.5.3, 6 module con (`core/content/operations/hydro/hr/app`), `spring-boot-maven-plugin` ở `app/`
- [x] T1.3: Spotless + Checkstyle (BE), ESLint + Prettier (FE)
- [x] T1.4: `.env.example` cho `local`/`staging`/`prod`, liệt kê đủ key, không giá trị thật
- [x] T1.5: `Makefile` 21 lệnh: `dev-infra`, `dev-native`, `dev-docker`, `migrate`, `test`, `backup`, `restore`…
- [x] T1.6: Commit convention (hook `commit-msg`) + PR template gắn Definition of Done

## WS-2

- [x] T2.1: Image `postgis/postgis:16-3.4`; bật extension `postgis`, `unaccent`, `pg_trgm`
- [x] T2.2: Flyway đa module: mỗi module `resources/db/migration/<prefix>/`, `app` gộp qua `spring.flyway.locations`. Bật `validateOnMigrate=true`, `outOfOrder=false`, `cleanDisabled=true`
- [x] T2.3: Migration `core`
- [x] T2.4: Migration `core`
- [x] T2.5: Migration `core`
- [x] T2.6: Job tạo partition tháng kế tiếp (chạy trước hạn, idempotent)
- [x] T2.7: DB roles tách quyền: `songnhue_owner` (migrator) · `songnhue_app` (không DELETE trên `audit_logs`/`hydro_raw_logs`) · `songnhue_archiver` · `songnhue_readonly`. GRANT trong migration, CREATE ROLE ở init script
- [x] T2.8: Cột chuẩn: `id BIGINT IDENTITY`, `public_id UUID`, `created_at/by`, `updated_at/by`, `deleted_at`, `version`; enum lưu `VARCHAR` + CHECK
- [x] T2.9: Seed: org_units gốc, roles + permissions dịch từ ma trận RBAC `function-spec.md` §6, tài khoản Super Admin (bắt đổi mật khẩu + bắt buộc 2FA)
- [x] T2.10: Seed `settings`

## WS-3

- [x] T3.1: `compose.infra.yml`
- [x] T3.2: `compose.local.yml`
- [x] T3.3: `Dockerfile` backend: multi-stage (maven build → JRE 21 alpine), non-root, healthcheck theo `/actuator/health/readiness`
- [x] T3.4: `Dockerfile` admin-app (build → nginx static) và public-web (Next standalone)
- [x] T3.5: Script init Postgres: extension + CREATE ROLE
- [x] T3.6: Profile Spring
- [x] T3.7: `make dev-infra` / `dev-be` / `dev-fe` / `dev-docker` / `dev-native` + `make doctor` + 2 tài liệu hướng dẫn

## WS-4

- [x] T4.1: `ApiResponse<T>`, `ApiError`, `ErrorDetail` + `ResponseEnvelopeAdvice` (ResponseBodyAdvice)
- [x] T4.2: `AppException` + 8 subclass đúng §2.2; `GlobalExceptionHandler` map toàn bộ; exception lạ → `SYS-0001`, cấm lộ stacktrace/SQL
- [x] T4.3: `ErrorCode` enum sinh từ catalog §2.3 (31 mã) + `error-messages.properties`; test đảm bảo mọi mã có message
- [x] T4.4: Filter chain đúng thứ tự qua hằng `FilterOrder`
- [x] T4.5: `RateLimitFilter` qua interface `RateLimitStore` (impl Caffeine in-process)
- [x] T4.6: 8 utils: `DateTimeUtils`, `NumericUtils`, `VietnameseUtils`, `CodeGenerator`(DB sequence), `MaskUtils`, `PageUtils`(whitelist sort), `FileValidator`(magic bytes), `CryptoService`(AES-256-GCM + `key_id`)
- [x] T4.7: `BaseEntity` / `ScopedEntity` + JPA auditing + soft delete + `@Version`
- [x] T4.8: `@ConfigurationProperties` + `@Validated` cho mọi nhóm config → fail-fast lúc startup khi thiếu env
- [x] T4.9: Log JSON (cơ chế sẵn có của Boot 3.4+) + `traceId` trong MDC; `RequestLoggingFilter` (method/path/status/duration, không log body nhạy cảm)
- [x] T4.10: Springdoc-openapi: `/api/v1/`, 6 nhóm theo module

## WS-5

- [x] T5.1: JWT RS256, keypair đọc từ file/env, `kid` trong header để xoay key; access token 30'
- [x] T5.2: Refresh token rotation lưu httpOnly + Secure + SameSite=Strict cookie; token family
- [x] T5.3: Refresh reuse detection → thu hồi cả family + force re-login + security event
- [x] T5.4: `token_denylist` bảng DB; đổi mật khẩu / khóa tài khoản → denylist toàn bộ token đang sống
- [x] T5.5: CSRF double-submit (`X-CSRF-Token`) cho mọi request thay đổi dữ liệu
- [x] T5.6: Login lockout 5 lần/15' → `AUTH-0003`; message chung `AUTH-0001` không tiết lộ user có tồn tại
- [x] T5.7: BCrypt cost ≥ 12; policy ≥10 ký tự chữ+số; bắt đổi mật khẩu lần đầu
- [x] Kèm theo (phát sinh từ WS-2): lệnh bootstrap `superadmin`
- [x] T5.8: 2FA TOTP bắt buộc Super Admin + Admin + Admin HR (enroll, otpauth URI, verify, 10 mã khôi phục)
- [x] T5.9: Tầng 2
- [x] Kèm theo (từ WS-4): `AuditContextFilter` đã điền `userId`/`username`
- [x] T5.10: Deny by default: `DenyByDefaultTest` quét toàn bộ controller, thiếu annotation → CI đỏ
- [x] T5.11: Tầng 3
- [x] T5.12: Lookup qua `public_id` UUID (`findByPublicIdAndDeletedAtIsNull`)
- [x] T5.13: Quản lý phiên (M5.14): danh sách phiên gộp theo family + đăng xuất từ xa
- [x] T5.14: Cảnh báo đăng nhập bất thường (M5.16): `AbnormalLoginDetector`

## WS-6

- [x] T6.1: `org_units`
- [x] T6.2: Tree helper (P2) tái sử dụng cho danh mục/media/công trình/menu
- [x] T6.3: Attachment service (P3): bảng polymorphic, upload MinIO, versioning, `valid_until`, presigned URL TTL 10 phút
- [x] T6.4: `FileValidator`: magic bytes + size theo config + tên file random; ảnh re-encode strip EXIF (`ImageSanitizer`); ClamAV scan async qua giao thức INSTREAM trước khi chuyển "sẵn sàng"
- [x] T6.5: Workflow engine (P1): `workflow_definitions` + `transitions`, check `(from, action, quyền)` trong transaction, hook notify + audit. Nơi duy nhất đổi trạng thái
- [x] T6.6: Notification service (P4): `notify(request)`; v1 bật in-app + email, SMS/web-push tắt theo `settings`
- [x] T6.7: Recipient resolver theo G11: nhóm "Ban điều hành" từ `settings` ∪ người đứng đầu/phó `org_units`; khử trùng lặp; loại tài khoản khoá
- [x] T6.8: Job & Scheduler (P5): `jobs` + SKIP LOCKED, worker in-process bounded pool, backoff 1'→5'→15', chống overlapping run, thu hồi job treo
- [x] Nhận nợ WS-5: `TokenMaintenanceJob` → `TokenCleanupHandler` trong hàng đợi
- [x] Nhận nợ WS-2: job tạo partition `audit_logs`
- [x] T6.9: ShedLock cài sẵn, `app.shedlock-enabled` đọc env, mặc định tắt (1 node)
- [x] T6.10: Async job API: `JobDtos.JobAccepted` (202 + jobId) + endpoint tra tiến độ `GET /api/v1/jobs/{id}`
- [x] T6.11: Settings service: key-value có type + validate 2 tầng + Caffeine cache + API cho UI; export/import loại trừ credential
- [x] T6.12: Audit interceptor: ghi old/new JSON tự động qua Hibernate; append-only + hash chain (trigger DB); API verify chain
- [x] T6.13: Job kết xuất audit >5 năm: CSV nén → MinIO bucket riêng → đọc ngược verify checksum → mới xoá; ghi anchor `last_hash`; lỗi → không xoá dòng nào + `ADM-2001`
- [x] T6.14: Thông báo hệ thống (M5.13): Admin gửi tới toàn bộ hoặc một nhóm tài khoản
- [x] T6.15: Vertical slice: CRUD `users` + `roles` qua quyền tầng 2 + audit + notification
- [x] Nhận nợ WS-5: `AuthorityLoader.invalidate(publicId)` được gọi ở gán vai trò, khoá/mở tài khoản và xoá tài khoản

## WS-7

- [x] T7.1: `pg_dump -Fc` hàng đêm ~02:00 → nén → checksum SHA-256 → prune > 30 ngày. Đây là toàn bộ cơ chế backup
- [x] T7.2: Copy bản dump sang VM-3 (khác máy với DB); key AES + JWT signing key lưu tách, KHÔNG nằm trong bản backup
- [x] T7.3: Một alert duy nhất: `backup_last_success_timestamp`
- [x] T7.4: Backup theo yêu cầu (M5.10) + hiển thị trạng thái backup gần nhất trên UI
- [x] T7.5: Restore qua UI (M5.11): chỉ Super Admin + 2FA, xác nhận nhiều bước (gõ tên hệ thống + lý do), async có tiến độ, security event + audit. Khôi phục từ bản dump đêm
- [x] T7.6: Maintenance mode: flag `settings` + filter chặn ghi (503) trong lúc restore, trừ Super Admin
- [~] T7.7: Diễn tập restore 1 lần trước go-live trên VM-2 + ghi con số RTO thật vào runbook; sau đó theo quý (thủ công, có checklist)
- [x] T7.8: Health-check (M5.12): actuator + indicator cho DB, MinIO, SMTP, telemetry (stub Phase 2)
- [x] T7.9: Micrometer → Prometheus + Grafana đặt trên VM-3 (sống sót khi VM production chết); log JSON rotation 30 ngày
- [x] T7.10: Security event stream riêng (login fail, refresh reuse, 403 scope, đổi quyền, truy cập credential) → Grafana + alert
- [x] T7.11: Khung cảnh báo "dữ liệu quá hạn": gauge `data_freshness_seconds{source}` + alert rule mẫu
- [x] T7.12: Runbook `docs/runbook/`: restore từ dump, xoay key AES/JWT, poller chết, retry job Failed
- [ ] T7.13: **Diễn tập khôi phục một lần trước go-live** — *(xác nhận 26/8: `docs/runbook/dien-tap-khoi-phuc.md` còn nguyên các ô `______` cho giờ bắt đầu và RTO thật, tức chưa diễn tập lần nào)* — chạy `docs/runbook/dien-tap-khoi-phuc.md` trên dữ liệu thật rồi ghi con số **RTO đo được** vào runbook. Chưa diễn tập thì RTO trong tài liệu là một con số ước lượng, không phải một cam kết | Note: chuyển từ `cicd.md` §8 (26/8) — nợ này trước chỉ sống trong văn xuôi, không có trong sổ
- [x] T7.13-a: ⚠⚠ **Bản dump khôi phục ra CSDL mà `songnhue_app` không đọc nổi** | Date: 26/08/2026 | Note: **tìm ra bằng lượt khôi phục THẬT 26/8, không tìm ra bằng đọc mã** (§10.58). GRANT cấp bảng do migration Flyway cấp; khôi phục vào cluster MỚI thì `flyway_schema_history` được nạp lại → Flyway nói "up to date" → migration không chạy, mà `--no-privileges` đã tước ACL khỏi dump. App chết ở `permission denied for table users`. ⛔ Hệ không có PITR nên đây là đường quay lui dữ liệu DUY NHẤT. Ẩn lâu vì khôi phục ĐÈ lên CSDL đã migrate thì `ALTER DEFAULT PRIVILEGES` cứu — tức đường hay thử thì chạy, đường dùng lúc thảm hoạ thì hỏng. Vá: bỏ `--no-privileges` ở 2 script dump (đo: mục ACL trong dump **0 → 98**, 319KB→356KB) · `restore.sh` lọc mục lục theo CHỦ SỞ HỮU + chốt ">100 mục" + bước nghiệm thu đọc bằng `songnhue_app`. `BackupRestoreFlagsTest` 7 bài, kiểm chứng ngược 3 kịch bản

## WS-8

- [x] T8.1: Vite 8 + React 18 + TS strict, cấm `any` + AntD 5 + TanStack Query 5 + React Router 7; cấu trúc `shared/ components/ features/ app/`
- [x] Trả nợ WS-3/T3.4 (nửa admin-app): `deploy/docker/admin-app.Dockerfile` build thật → image chạy, `/healthz` trả `ok`, SPA fallback 200 ở đường dẫn sâu, healthcheck `healthy`.  Phải sửa Dockerfile: bản cũ chép cả `public-web/package.json` và chạy `npm ci` trần nên đổ ngay khi WS-9 chưa tạo thư mục đó → nay `npm ci --workspace admin-app --include-workspace-root`. (WS-9 đóng nốt nửa public-web ngày 17/8 → T3.4 và DoD mục 2 đã đóng.)
- [x] T8.2: `shared/tokens.ts`
- [x] T8.3: `shared/apiClient`
- [x] T8.4: `shared/error-map.ts` mirror 49 mã
- [x] T8.5: `useAuth`, `usePermission`, `useAnyPermission`, `RequireAuth`/`RequireAnonymous`/`RequirePermission`
- [x] T8.6: Màn hình auth: đăng nhập · 2FA verify · 2FA enroll (QR + mã khôi phục, bắt xác nhận đã lưu) · đổi mật khẩu (bắt buộc lần đầu và tự nguyện). ⛔ Chưa có "quên mật khẩu"
- [x] T8.7: `AdminLayout` + menu render theo permission (`menu.tsx` gộp "đường dẫn ↔ nhãn ↔ quyền" một chỗ) + băng thông báo bảo trì; trang 403/404/500 hiển thị `traceId` copy được
- [x] T8.8: 7 component nghiệp vụ: `StatusBadge` (+ `statusVocabulary.ts`), `ThresholdValue` (ngưỡng từ API, `stale` → xám theo G3), `ApprovalActions` (render từ `allowedActions`), `OrgUnitTreeSelect`, `AttachmentPanel`, `DateRangeFilter`, `ExportButton` (202 + jobId + hỏi tiến độ)
- [x] T8.9: `formatDateTime` UTC+7 `dd/MM/yyyy HH:mm` ép cứng múi giờ (không dùng giờ máy), `formatNumber` kiểu VN, `formatBytes`/`formatDuration`/`formatAge`
- [x] T8.10: 8 màn hình quản trị + 2 màn hình cá nhân: Tổng quan · Tài khoản (CRUD + khoá + phân vai trò) · Vai trò (chỉ xem, xem `architecture-review.md` §9.10.5) · Sơ đồ đơn vị (cây + thêm/chuyển/xoá) · Cấu hình (ô nhập dựng theo `valueType`) · Nhật ký kiểm toán + kiểm chuỗi hash · Sao lưu & khôi phục · Tình trạng hệ thống · Hộp thư · Phiên đăng nhập
- [x] Nhận nợ WS-7 (#32): M5.10 + M5.11 gọi `/api/v1/backups/`, có hộp thoại khôi phục 3 lớp chặn (chuỗi `SONGNHUE` + lý do ≥ 10 ký tự + mã 2FA tươi)
- [x] T8.11: `DataTable`

## WS-9

- [x] T9.1: Next.js 16 + Tailwind 4 + TS strict; tokens dùng chung qua workspace thứ ba `frontend/design-tokens` (admin-app import lại từ đó)
- [x] T9.2: Layout công khai (đầu trang + điều hướng + chân trang, có liên kết "bỏ qua tới nội dung" cho bàn phím) + trang chủ tạm + 404 + 500
- [x] T9.3: SEO base: `metadataBase` + Open Graph + template tiêu đề · `sitemap.ts` · `robots.ts` tự chặn lập chỉ mục ở staging/local (cùng mã nguồn, khác `NEXT_PUBLIC_SITE_URL`)
- [x] T9.4: ISR: `revalidate = 300` ở trang chủ làm mẫu + `POST /api/revalidate` (bí mật không mang tiền tố `NEXT_PUBLIC_`, so sánh chuỗi thời gian không đổi, chưa cấu hình thì đóng chứ không mở)
- [x] T9.5: `GET /api/health` + `output: 'standalone'`; image build thật và chạy
- [x] Trả nợ WS-3/T3.4: cả 2 image FE build thật → T3.4 đóng, DoD mục 2 đóng

## WS-10

- [x] T10.1: Testcontainers PostgreSQL + PostGIS làm nền cho integration test
- [x] Nhận nợ WS-2: `flyway.clean()` gọi trên đúng bean của ứng dụng bị từ chối, và schema còn nguyên sau đó (15/8)
- [x] T10.2: ArchUnit
- [x] Nhận nợ WS-5: mọi lớp con `ScopedEntity` phải mang `@Filter` kèm đúng hằng điều kiện dùng chung (15/8)
- [x] Nhận nợ WS-6: `WorkflowAware.applyState` chỉ được gọi từ `WorkflowEngine` (15/8)
- [x] Nhận nợ WS-4: luật cho phép import chéo `core.common.` (15/8)
- [x] T10.3: Harness ma trận RBAC role × resource (NFR-06)
- [x] Nhận nợ WS-5
- [x] T10.4: Test chuỗi hash audit trên DB thật + `CryptoService` xoay khoá (có từ WS-4) + deny-by-default (có từ WS-5)
- [x] T10.5: Coverage gate tầng domain
- [x] T10.6: `ci.yml`: lint → unit → Testcontainers → ArchUnit → cổng bao phủ; quét CVE tách job riêng
- [x] T10.7: Branch protection cho luồng 3 chặng `dev → staging → production`

## WS-11

> ⚠ `Note:` chỉ ghi **kết luận một dòng**. Nguyên nhân gốc đầy đủ ở `architecture-review.md` §mục
> được trỏ — một sự việc chép ở hai nơi là hai nơi phải sửa khi hiểu biết thay đổi.

### Chưa làm

- [ ] T11.45: ⚠ **Siết SSH trên máy chủ — cần `sudo`, tôi không tự chạy được** | Note: **bản vá GỐC của §10.59**; ghép kênh ở T11.44 chỉ giảm mặt tiếp xúc. Đo 27/8 trên VPS-2: `fail2ban` **CHƯA CÀI** (không phải cài mà tắt) · `MaxStartups` không đặt tường minh → mặc định `10:30:100` · `ClientAliveInterval` không đặt → mặc định `0` nên phiên chết KHÔNG BAO GIỜ được dọn (đo: 33 kết nối 'đã xác thực' treo trong khi `who` = 0). Lệnh đầy đủ + bước kiểm chứng sau khi làm: `deploy-guideline.md` §2.2-b. ⚠ Chưa làm thì mọi lượt deploy vẫn có thể đỏ vì lý do không liên quan tới mã. ✅ Không phải sự cố xâm nhập: `PasswordAuthentication no`, `PermitRootLogin no`, `who` = 0 phiên ⭐ **Đo lại 27/8 10:05 — sshd TỰ KHAI, không còn phải suy ra**: `exited MaxStartups throttling after 00:00:25, 13 connections dropped`, kèm ~19 dòng `Disconnecting authenticating user root 79.108.163.24 … [preauth]` trong cùng một giây. Đã loại giả thuyết 'chính vòng lặp đo gây ra': `ufw: command not found`, `iptables -S`/`nft` **không dòng nào** khớp cổng 22. ⛔ `ss -K` chỉ dọn hiện trường: sau lượt cắt 26/8 sshd rơi 67→7 tiến trình, rồi **cùng IP ấy quay lại** hôm sau. Vẫn còn hiệu lực trong lượt CD 27/8: `✓ Mở được kênh dùng chung ở lần thử 2` — lần 1 vẫn bị thả, vòng thử lại đang gánh ⭐ **Đo 27/8 sau 4 lượt CD**: ba lượt đầu đều `Mở được kênh dùng chung ở lần thử 2` (lần 1 bị thả); lượt 15:39 là lần ĐẦU `ở lần thử 1`. **Một lượt không đủ để kết luận** — vòng thử lại vẫn là thứ đang gánh, và `fail2ban` vẫn KHÔNG có trong `dpkg`, không tệp drop-in nào, `NRestarts=0`. Chỉ đóng khi đo lại 10/10 SSH kèm `fail2ban-client status sshd` chạy được
- [ ] T11.2: Dựng **2 VPS** — VPS-1 production, VPS-2 staging kiêm kho sao lưu + giám sát. ⚠ Bản ghi cũ ghi "3 VM + `compose.backup.yml` cho VM-3", đã lỗi thời: VM-3 gộp vào VPS-2 và `compose.observability.yml` thay `compose.backup.yml` (`hosting_recommendations.md` §1). VPS-2 đã dựng và đang chạy staging; **còn VPS-1** | Note: sửa mô tả sau lượt rà 26/8
- [ ] T11.2-b: Mua tên miền `.vn` — MỘT tên miền cho cả hai môi trường. Chủ thể PHẢI là Công ty
- [ ] T11.6-a: Nhận nợ WS-4 — chặn `/swagger-ui/` và `/v3/api-docs/` ở nginx production
- [~] T11.7: Secrets và biến | Note: rà 26/8 — ✅ repo secret `NVD_API_KEY` · ✅ environment `staging` đủ 4 · ⛔ **environment `production` vẫn KHÔNG có secret nào** (`PROD_HOST`/`PROD_USER`/`PROD_SSH_KEY`/`PROD_BASE_URL`) — đặt được sau khi có VPS-1. ✅ **Lỗ im lặng đã bịt (T11.7-b)**: trước đây thiếu secret thì CD Production cảnh báo rồi bỏ qua → lượt chạy xanh mà không byte nào chạm máy chủ
- [ ] T11.7-a: Nhận nợ WS-22 — đặt **biến kho** `PUBLIC_SITE_URL` rồi dựng lại image `public-web` | Note: **xác nhận 26/8** — `gh api repos/…/actions/variables` trả về RỖNG, tức image đang chạy có `NEXT_PUBLIC_SITE_URL=''` và `sitemap.xml`/canonical của staging đang trỏ `http://localhost:3000` (§10.38). **ĐO TRỰC TIẾP trên site thật 26/8**: `sitemap.xml` → `<loc>http://localhost:3000</loc>`, trang chủ → `<link rel="canonical" href="http://localhost:3000"/>`. Chưa hại gì vì staging trả `X-Robots-Tag: noindex, nofollow` và `robots.txt` `Disallow: /` — nhưng **đúng image này là image sẽ lên production**: `NEXT_PUBLIC_*` nướng lúc build, và luồng đề bạt cố ý dùng chung một image cho cả hai môi trường. ⛔ **CHẶN Ở QUYẾT ĐỊNH NGHIỆP VỤ, không chặn ở kỹ thuật**: giá trị phải là địa chỉ PRODUCTION, mà tên miền chưa chốt (T11.2-b — `.vn`, chủ thể phải là Công ty). Đoán một giá trị là nướng canonical sai vào mọi image sau đó. Cần QuanTran chốt tên miền rồi mới đặt biến
- [~] T11.9: Quy trình quay lui — về mã nguồn đã tự động; **về DỮ LIỆU vẫn phải restore tay** từ dump pre-deploy
- [ ] T11.28: `http://songnhue.bhh40.net` ghi cứng ở **3** tệp (`DirectiveDocumentsSection` · `PortalSidebar` · `SiteFooter` — rà 26/8, bản ghi cũ đếm thiếu 1) — địa chỉ hệ thống văn bản là **cấu hình của khách**, phải vào `settings` có UI sửa (luật 12). Đổi địa chỉ hiện nay = sửa mã + dựng lại image | Note: §10.54
- [ ] T11.30: **Phase sau** — endpoint công khai cho thư viện ảnh công trình và cho `org_units`. Chưa có đường nào để cổng lấy hai bộ dữ liệu này | Note: QuanTran chốt 26/8; §10.54
- [ ] T11.33: `app.storage` chỉ có **một** `endpoint`, dùng chung cho lượt gọi nội bộ lẫn lượt ký presigned URL. Đang chữa tạm bằng bí danh mạng cho `nginx` để app không đi vòng ra Internet. Chữa gốc: tách `app.storage.public-endpoint`, dùng hai `MinioClient` | Note: chuyển từ `deploy-guideline.md` §9.3 (26/8)
- [ ] T11.34: `system_backups.trigger_type` chưa có giá trị `PRE_DEPLOY` — bản chụp trước deploy đang ghi `MANUAL`, phân biệt bằng tiền tố tên tệp. Chữa gốc: migration bốn dòng, gộp vào lần sửa lược đồ kế tiếp | Note: chuyển từ `deploy-guideline.md` §9.3 (26/8)
- [ ] T11.35: **Gói quyền thư mục host thành `deploy/host-prepare.sh`** — hiện là việc gõ tay, tức một thứ phải nhớ khi dựng VPS-1. ⛔ `chown` trong Dockerfile KHÔNG có tác dụng với bind mount (host che hoàn toàn thứ image dựng sẵn), nên ba đường dẫn phải `chown` trên máy chủ: `/opt/songnhue/keys` → `1000:1000` dir 700 tệp 600 · `/var/log/songnhue` → `1000:1000` 755 · `/var/lib/songnhue/backup` → **`999:1000` + `2775`**. ⚠⚠ Ô cuối dễ sai nhất: thư mục sao lưu dùng chung BA danh tính (postgres uid 999 chạy `pg_dump` bên trong container · app uid 1000 · user SSH trên host). `chown -R 1000:1000` làm bước chụp trước triển khai hỏng, mà bước ấy nay chạy ở MỌI lượt deploy staging → mọi lượt deploy đỏ ngay bước đầu | Note: chuyển từ `deploy-staging-issue.md` (26/8); nhóm hiện mượn gid 1000 của user `ubuntu`, nên `host-prepare.sh` phải tạo nhóm riêng và đọc uid/gid từ image (`docker run --rm --entrypoint id <image>`) thay vì ghi cứng
- [ ] T11.36: `docker login ghcr.io` trên VPS đang là thao tác tay bằng PAT — hoặc tự động hoá, hoặc chuyển sang để workflow đẩy image qua SSH. Chưa làm thì lượt dựng VPS-1 sẽ dừng ở `unauthorized` ngay lệnh `compose up` đầu tiên | Note: chuyển từ `deploy-staging-issue.md` (26/8)
- [ ] T11.41: `verify-no-keys.sh` **không chạy được trên máy chủ** — không có `pg_restore` ở host, nên `pre-deploy-dump.sh` in *"BỎ QUA việc kiểm khoá trong bản dump"* ở MỌI lượt triển khai | Note: lộ ra khi chạy thật 26/8. Đây là bỏ qua một kiểm chứng bảo mật, không phải "không sao" — bản dump đi ra kho ngoài nhà cung cấp (§9.2). Chữa: gọi `pg_restore` bên trong container postgres (nó có sẵn) thay vì đòi ở host
- [ ] T11.38: **Hai đường seed cùng tồn tại** — `make seed-portal` + `tools/seeder/seed-portal-data.ts` (gọi REST API) vẫn còn, trong khi T11.21 đã chuyển bộ seed vào chuỗi migration Flyway. Hai cơ chế cho một việc là hai nơi phải nhớ, và đường cũ không có cổng chặn `SEED_LOCATION` nào. Quyết: bỏ hẳn đường cũ, hay giữ cho việc dựng máy dev | Note: lộ ra khi rà tài liệu 26/8

### Đã làm — hạ tầng nền (rà lại bằng mã thật 26/8)

- [x] T11.52: ⚠⚠ **Migration mới đánh số bằng GIỜ-PHÚT nên rơi xuống DƯỚI bản đã áp** | Date: 27/08/2026 | Note: CD Staging đỏ ở bước Triển khai — **lần thứ hai trong hai ngày, cùng một bước, khác nguyên nhân** (§10.65 là checksum, đây là thứ tự). `Validate failed … Detected resolved migration not applied to database: 202608272320` và `…2321`. Quy ước dự án là `V<YYYYMMDD><số thứ tự 4 chữ số>` (1023→1038 chạy suốt 43 tệp); hai tệp PR #53 dùng **giờ-phút** (`2320` = 23:20) nên xếp **trước** `…281036/37/38` mà staging **đã áp** ở PR #52 ⇒ out-of-order ⇒ Flyway từ chối chạy. ⛔ **Lượt đỏ ấy che cho một lỗi CÂM cùng gốc**: seed `…2320` `UPDATE` khoá `site.home.photos-folder` mà migration `…2321` mới `INSERT` ra — trên CSDL trắng seed chạy trước, `UPDATE` chạm **đúng 0 hàng**, không lỗi không cảnh báo, khối ảnh trang chủ rỗng vĩnh viễn (luật 27, lần thứ hai trong ba ngày). ⚠⚠ **688 bài kiểm về NGUYÊN TẮC không thấy**: test chạy migration từ CSDL RỖNG, mà trên CSDL rỗng **không tồn tại khái niệm out-of-order** — Flyway sắp theo version rồi áp tuần tự, xanh trọn vẹn. Cùng lớp mù với §10.65, hai lượt CD liên tiếp. ⚠ **Hỏng thứ ba, do chính bản vá §10.60 đưa vào**: khối chú thích cảnh báo về `docker compose run` viết dấu huyền **chưa thoát** trong heredoc `<<REMOTE` **không nháy** → runner khai triển nó thành **thay thế lệnh** và chạy thật `docker compose run`, `up -d --force-recreate`, `-T`, `chay-tu-xa.sh` (4 dòng lạ giữa log deploy). Vô hại lần này là **may** — một dòng chú thích nhắc `rm -rf` sẽ chạy thật. ✅ Đường ống lại hành xử ĐÚNG: `migrator` `run --rm` chạy TRƯỚC `up -d`, nên dừng trước khi chạm container nào; `Quay lui bản cũ` thành công (`[quay-lui] 511 byte, khớp hai đầu`), dịch vụ trả lời sau 10s. Và vì `validate` chặn **trước khi áp**, **không migration nào vào `flyway_schema_history`** → đổi tên tệp là an toàn ở staging/production. Vá: (1) đổi tên theo đúng quy ước **và đúng chiều phụ thuộc** — `V202608281039__cms_home_video_value` (tạo khoá) trước `V202608281040__seed_portal_media_cong_ty` (ghi giá trị), ràng buộc ghi vào đầu tệp 1040; (2) `backend/tools/kiem-thu-tu-migration.sh` so số hiệu migration MỚI với đỉnh **nhánh nền** — `make ci-local` bước 2/10 + job CI `Thứ tự migration` (`fetch-depth: 0`); (3) thoát 5 cặp dấu huyền + 2 bài trong `DeployRemoteStdinTest` (8 bài), một bài soi tệp thật, một bài **chứng minh bộ dò bắt được vi phạm**, cố ý KHÔNG dùng `boChuThich` vì chính dòng `#` là chỗ đã nổ; (4) `SeedPortalMigrationTest` thêm bài đóng **vòng đọc–ghi**: giá trị seed ghi xuống phải JOIN ra **25 ảnh thật** (ba khẳng định rời đều xanh được trong khi không dính gì nhau); (5) manifest 43 vân tay; cổng gộp `so_job` 8 → 9. ⭐ **Kiểm chứng ngược, cả ba, có số đo**: bộ canh thứ tự chạy trên **chính commit `3a39b79` đã làm đỏ CD** → exit 1, gọi đích danh cả hai mã · cây đã sửa → xanh. Bộ dò dấu huyền nạp lại nội dung cũ của `deploy.yml` (25 dòng đã thoát → 30) → đỏ đúng **5 dòng, đúng số hiệu dòng** → khôi phục 30. Bài vòng đọc–ghi: bỏ vế `UPDATE` khỏi seed (1 câu → 0, **xác nhận trên classpath cũng 0** rồi mới chạy) → đỏ với **expected 25 / but was 0** → khôi phục. ⚠ Bản đầu của bài ấy đỏ bằng `PSQLException` (`invalid input syntax for type uuid: ""`) thay vì thông báo giải thích — đã đổi sang `NULLIF`; **một bộ canh đỏ mà không nói được lý do thì người gặp phải dò lại từ đầu**. ⬜ **Chưa nghiệm thu trên môi trường thật** — bộ seed ảnh và bước nạp byte `minio-init` chưa lượt CD nào chạy qua. §10.66
- [x] T11.51: ⚠⚠ **Một đợt sửa CHÚ THÍCH làm ứng dụng không khởi động được** | Date: 27/08/2026 | Note: CD Staging đỏ ở bước Triển khai — `Migration checksum mismatch for migration version 202608251100` (`applied -1232886408` ≠ `resolved 2110920357`). Thứ đã đổi trong `V202608251100__seed_portal_content.sql`: **14 dòng thêm / 5 dòng xoá, TOÀN BỘ trong khối chú thích `--`, không một dòng SQL nào đổi** — Flyway băm cả tệp. ⚠⚠ **680 bài kiểm về NGUYÊN TẮC không bắt được**: test chạy migration từ CSDL RỖNG nên không có checksum cũ để so; `make ci-local` 9/9 và CI trên `dev` 10/10 đều xanh rồi lượt deploy chết. Cùng họ §10.56 — trạng thái tích luỹ ngoài kho không suy ra được từ trong kho. ✅ Đường ống hành xử ĐÚNG: `migrator` chạy `run --rm` TRƯỚC `up -d --force-recreate` nên dừng trước khi chạm container nào — 3 container vẫn là bản 03:17, `healthy`, site 200 suốt; `Quay lui bản cũ` chạy và thành công. Vá: khôi phục tệp về **đúng byte** bản staging đã áp (đối chiếu SHA-256 với `7b0b26a` — khớp) · `backend/db-migration-checksums.txt` 41 vân tay · `MigrationImmutabilityTest` 3 bài (sửa/xoá/thêm-chưa-ghi đều đỏ) · `make migration-manifest` (đã kiểm chạy hai lần cho kết quả y hệt). ⛔ Chú thích trong migration KHÔNG phải chú thích — nó là dữ liệu đã ký. §10.65
- [x] T11.50: Áp `Cổng kiểm CI` làm context bắt buộc của `dev` | Date: 27/08/2026 | Note: áp **SAU** khi job có mặt trên `dev` (ngược thứ tự là khoá chết mọi PR, kể cả PR mang chính job ấy). Đo trước: 7 context · sau: **`strict=true` · 1 context = `Cổng kiểm CI`**. ⚠ Lượt áp ĐẦU **hỏng trong im lặng**: gõ `-X PUT -f strict=true >/dev/null 2>&1` → exit 0, tưởng xong, nhưng API trả **422** (`For 'properties/strict', "true" is not a boolean`) và danh sách context **không đổi một ký tự**. Chỉ bước đo lại lộ ra — cùng hình dạng §10.60 và §10.57: **đầu ra bị vứt đi thì thất bại trông y hệt thành công**. Lệnh trong `branch-protection.md` §7.3 đã sửa (`-X PATCH` + `-F strict=true`); §4.1 vốn đã đúng từ trước. ⬜ Chưa nghiệm thu được trường hợp **matrix bị bỏ qua** — lượt CI trên #47 có matrix CHẠY; cái đã đo được là cổng vẫn chạy khi `Gắn tag SHA` **skipped** (`✓ 8 job, không job nào hỏng`). PR chỉ-sửa-tài-liệu kế tiếp mới là phép kiểm thật
- [x] T11.48: ⚠⚠ **Bảy context bắt buộc khoá chết mọi PR chỉ sửa tài liệu** | Date: 27/08/2026 | Note: **thí nghiệm đối chứng tự nhiên** — hai PR mở cùng lúc, cùng nhánh đích `dev`, khác đúng một biến: #48 (đụng `frontend/`) matrix CHẠY → báo hai tên đã bung → `CLEAN`; #47 (chỉ `docs/`+`.claude/`+`.agents/`) matrix BỎ QUA → chỉ báo tên GỐC → **`BLOCKED`**. Job matrix khi chạy báo tên đã bung `(admin-app, …)`, khi bỏ qua chỉ báo `Đóng gói image frontend` — hai context bắt buộc kia không bao giờ tới, PR treo mãi ở *Waiting for status to be reported*. ⛔ **Không sửa được bằng cách đổi danh sách context**: đòi tên gốc thì hỏng lúc chạy, đòi tên đã bung thì hỏng lúc bỏ qua. Ẩn được vì §2.1 của `branch-protection.md` đã cảnh báo đúng cái bẫy này, nhưng §6.2 chỉ soi trường hợp job bị `if` loại, không soi job matrix bị bỏ qua — và từ 26/8 tới 27/8 không PR nào chỉ sửa tài liệu. Vá: job **`Cổng kiểm CI`** (`if: always()`, `needs` mọi job) là context bắt buộc DUY NHẤT; đỏ khi có `failure`/`cancelled`, bỏ qua `skipped`, in bảng kết quả từng job. `CiGateCoverageTest` 4 bài đối chiếu `needs` với danh sách job có thật **cả hai chiều** (luật 28 — gom về một context nghĩa là context ấy phải biết hết). Kiểm chứng ngược: bỏ `tracking` khỏi `needs` → bài ĐỎ và nêu đích danh job thiếu. `branch-protection.md` §7
- [x] T11.49: 9 phép kiểm bộ đọc tracking chưa cổng nào chạy, và có sẵn nhánh thoát-0 | Date: 27/08/2026 | Note: chúng canh `master-tracking.md` — **nguồn sự thật DUY NHẤT về task và nợ** — mà không nằm trong `ci.yml` lẫn `Makefile`. Tệ hơn: `test_parse.py` `import server`, mà `server` kéo theo fastmcp + google-api, và nhánh `ImportError` gọi **`sys.exit(0)`** → chạy ở máy chưa dựng venv là **xanh mà không kiểm gì** (đo được: in `BỎ QUA…` rồi exit 0). Vá: tách phần đọc thuần sang `tracking_parser.py` **chỉ dùng thư viện chuẩn** → `test_parse.py` import thẳng, **bỏ hẳn nhánh bỏ qua**; `server.py` tái xuất nên MCP không đổi hành vi (kiểm: 8/8 tên còn nguyên, đọc ra 437 dòng). Thêm job `Bộ đọc tracking` vào `ci.yml` **không có bộ lọc đường dẫn** (chạy ~10s, không cài gì — đặt bộ lọc chỉ thêm một chỗ bỏ sót) và bước `[1/9]` của `make ci-local`. Đo: chạy trần không stub → 9/9 xanh
- [x] T11.47: Bộ đọc tracking báo **sai trạng thái** lên Google Sheet — chữ trong ghi chú ghi đè dấu tích | Date: 27/08/2026 | Note: lộ ra khi tự kiểm dòng vừa sửa bằng **chính bộ đọc thật** thay vì bằng một bộ kiểm tự chế. `_status_from_text` quét `✅` trên TOÀN dòng kể cả cột Note, nên một dấu `✅` đánh dấu ý phụ ("✅ Không phải sự cố xâm nhập") biến cả task thành Done. Đo trên file thật: **đúng 2 dòng sai, và cả hai là việc còn mở quan trọng nhất** — **T11.45** `[ ]` (siết SSH, đang làm đỏ deploy) và **T11.7** `[~]` (secret production) đều hiện **Done** trên bảng Công ty đọc. Vá: dấu tích thắng, chỉ đoán theo chữ khi dấu không nhận ra được. `test_dau_tich_thang_chu_trong_ghi_chu`. ⚠⚠ **Bản ĐẦU của bài kiểm là xanh giả** — nó tự suy lại trạng thái từ dấu tích rồi so với chính dấu tích, nên vẫn xanh sau khi gỡ bản vá; chỉ bước kiểm chứng ngược bắt được. Bản sau so với ĐẦU RA của parser, ghép 1:1 theo thứ tự và khẳng định độ dài khớp trước khi ghép. Kiểm chứng ngược có số đo: có vá → 0 đỏ · gỡ vá → 1 đỏ exit 1 · khôi phục → 0 đỏ. ⬜ **Nợ**: bộ 8 phép kiểm này KHÔNG nằm trong `ci.yml` lẫn `Makefile`, và nhánh thiếu phụ thuộc của nó `sys.exit(0)` — tức nếu đưa vào CI nguyên trạng thì nó xanh mà không kiểm gì
- [x] T11.46: ⚠⚠ **CD Staging báo success mà KHÔNG container nào được thay** | Date: 27/08/2026 | Note: **lượt CD đi hết đường ống đầu tiên, xanh trọn vẹn, và không triển khai gì** (§10.60). Đo ngay sau lượt chạy: app `6dcf9e4b…` vừa 'triển khai' nhưng container chạy `9c9f18e9…` **tạo 25/8**; admin-app và public-web **tạo 24/8**. Gốc: khối 'Triển khai' được nuôi vào bash-từ-xa qua **stdin** (`ssh host bash <<REMOTE`), mà `docker compose run --rm migrator` **gắn stdin** nên nuốt nốt phần script chưa đọc → `up -d --force-recreate` VÀ khối đo lại image ID không hề chạy; bash gặp EOF, thoát 0. Đo trên VPS-2: `run --rm` mất dòng sau · `run --rm -T` **cũng mất** (‑T chỉ tắt TTY) · `</dev/null` thì chạy · `up -d`/`pull` không đụng stdin. ⛔ 4/4 smoke test xanh vì chúng hỏi *site còn sống không*, không hỏi *site đang chạy image nào*. Vá: `.github/scripts/chay-tu-xa.sh` chuyển khối thành TỆP rồi chạy `</dev/null` + **đối chiếu số byte hai đầu**; cả 4 khối heredoc của `deploy.yml` đi qua nó. `DeployRemoteStdinTest` 6 bài, trong đó 1 bài chạy KIỂU CŨ và khẳng định nó mất dòng sau. Dấu vết duy nhất tìm ra nó: bước kết thúc **0,4 giây** sau log tắt của migrator ✅ **NGHIỆM THU 27/8 bằng lượt CD thật** (run 33035827794, staging `82c2676`): helper in `[pg-dump] 49 byte` · `[trien-khai] 4653 byte` · `[collation] 50 byte` **khớp hai đầu**; 4 dòng `Recreated`; **3 dòng `✓ … đang chạy đúng image vừa triển khai` — chưa từng in ra lần nào trước đó**. Đối chiếu ĐỘC LẬP trên máy chủ (tôi tự đo, không đọc lại lời workflow): app `5b0d7d35…` · admin-app `5c2a69d8…` · public-web `4458b40d…` — khớp từng cái với digest workflow giải ra; container tạo 03:17:04–05 đúng trong cửa sổ deploy. Bước 'Triển khai' **73 giây** so với 40 giây lượt hỏng — chênh lệch ấy chính là phần việc từng bị nuốt
- [x] T11.44: CD Staging đỏ vì cổng 22 bị quét — ghép kênh SSH cho cả lượt deploy | Date: 27/08/2026 | Note: **không phải lỗi mã** (§10.59). Đo: SSH cổng 22 hỏng **30%** (7/10 đạt, giãn 4 giây) trong khi HTTPS cùng máy cùng lúc 5/5 — một IP lạ giữ **32 kết nối** đồng thời, 67 tiến trình sshd, và `MaxStartups` mặc định `10:30:100` thả ngẫu nhiên **30%**; con số 30 đúng là chữ số giữa. Bước đỏ là bước mở BA kết nối liên tiếp. Vá phía đường ống: `ControlMaster`/`ControlPath`/`ControlPersist` → cả lượt dùng MỘT kết nối thay vì ~10, lượt bắt tay đầu thử lại 6 lần giãn cách tăng dần; gộp 3 lượt `ssh` của bước ấy thành 1 bằng `docker inspect` nhiều đối số (đã kiểm hành vi trên VPS-2: thiếu container thì BỎ HẲN dòng, exit 1). `DeploySshMultiplexTest` 5 bài, kiểm chứng ngược 3 kịch bản
- [x] T11.43: Chú thích chèn giữa lệnh nối dòng `\` cắt đứt lệnh — `bash -n` KHÔNG bắt được | Date: 26/08/2026 | Note: **lỗi tôi tự tạo khi vá T7.13-a, chỉ lộ ra khi chạy thật** ở `--format=custom: command not found`. Bash nối `\` với dòng KẾ TIẾP; dòng ấy là `#` thì lệnh đứt, phần đối số còn lại thành LỆNH MỚI, và cú pháp vẫn hợp lệ. Đo: bản hỏng → `bash -n` **exit 0**. Bài `khongChuThichGiuaLenhNoiDong` quét mọi `.sh` trong `deploy/`
- [x] T11.3-b: Cluster staging đã dựng lại với collation ICU vi-VN | Date: 26/08/2026 | Note: **làm thật trên VPS-2 26/8.** Đo trước: `c | collate=en_US.utf8`, `Anh<Đăng<Dung<Em`. Sau: `i | collate=C.UTF-8 | icu=vi-VN`, `Anh<Dung<Đăng<Em`. Trình tự: 2 bản chụp (bộ công cụ dự án + `pg_dumpall` dự phòng, checksum đối chiếu) → chép volume sang `songnhue_postgres-data-T11-3b-backup` để bước xoá **quay lui được** → `docker volume rm` → `up -d postgres` → **ĐO collation TRƯỚC khi nạp lại** → `pg_restore --exit-on-error` exit 0 → đối chiếu vân tay. Vân tay khớp **từng bảng**, trừ đúng `system_backups` 7→6 — chênh lệch tự giải thích vì `pre-deploy-dump.sh` chụp CSDL RỒI MỚI ghi sổ bản chụp ấy. 6/6 container healthy, 4/4 câu smoke test xanh trên site thật, trang chủ 11 liên kết đều là slug thật, 0 bài bịa. ⭐ Lượt này tìm ra **T7.13-a** (§10.58)
- [x] T11.37: Healthcheck `nginx` báo `unhealthy` GIẢ | Note: **vá 26/8.** Đích cũ `/.well-known/acme-challenge/` trả **404** khi không có lượt xin chứng chỉ nào chạy — tức gần như luôn luôn; đo trên staging: `songnhue-nginx Up 22 hours (unhealthy)` trong khi cả hệ phục vụ bình thường. Một cảnh báo luôn bật là cảnh báo không ai đọc. Đích mới `/healthz/upstream` **CHUYỂN TIẾP** tới `public-web:3000/api/health` (đường ấy cố ý không gọi backend nên lượt kiểm 30s/lần không tạo tải CSDL), `allow 127.0.0.1` + `deny all`, đi qua BIẾN `$probe_upstream` nên bắt được cả lỗi phân giải DNS trong mạng compose. `NginxHealthcheckTest` (4 bài) đọc đích TỪ compose rồi tìm nó trong template — hai tệp không lệch được (luật 14). Kiểm chứng ngược 3 kịch bản. `nginx -t` trên container nháp ở staging: exit 0
- [x] T11.42: `find` trong bước rsync của `deploy.yml` thoát 1 vì `keys/` là 700 | Note: **lỗi tôi tự tạo cùng ngày, bắt được khi chạy thật trên VPS-2 trước khi nó lên `dev`.** `find /opt/songnhue -maxdepth 2 -name '*.sh'` in `Permission denied` rồi **thoát 1**; dưới `set -euo pipefail` là cả bước rsync đỏ với thông báo không liên quan gì tới việc đang làm. Đo: nguyên bản → exit 1 · thêm `\( -name keys -prune \)` → exit 0
- [x] T11.39: Nợ #27 — bảo vệ nhánh | Note: **áp 26/8 bằng `gh api`, đo lại ngay sau đó.** `staging` + `production` `strict` `true`→`false` (hai chặng đề bạt không build lại; bắt cập nhật nhánh chỉ tạo vòng merge vô ích). `dev` thêm `Vùng nào thay đổi`. Chi tiết + lệnh: `docs/branch-protection.md` §6.2
- [x] T11.40: Bảo mật kho — secret scanning · push protection · non-provider patterns · Dependabot alerts + security updates | Note: **bật 26/8, đo lại: cả 5 `enabled`.** `secret-scanning/alerts` trả **0 cảnh báo**. ⚠ `automated-security-fixes` trả 422 nếu chưa `PUT vulnerability-alerts` trước — hai thứ khác nhau: *alerts* là báo, *security updates* là PR vá tự động. Repo public nên miễn phí
- [x] T11.3-a: `POSTGRES_INITDB_ARGS` đã có ở `compose.prod.yml` + bài canh ba chỗ không được lệch | Note: **vá 26/8.** Rà ra là VẮNG HẲN, không phải "lệch". Ba việc: (1) thêm dòng y hệt `compose.infra.yml` vào `compose.prod.yml`; (2) `PostgresCollationParityTest` — 5 bài, quét cả cây mã nguồn, bắt buộc có mặt ở 3 tệp, tự-kiểm-chứng bộ bóc tách; (3) `deploy/postgres/kiem-collation.sh` **ĐO cluster đang chạy**, gọi ở smoke test câu [1/4]. Kiểm chứng ngược 5 kịch bản (lệch 1 ký tự · xoá dòng · cả 3 cùng sai · workflow không gọi · mất cờ chạy) đều đỏ đúng bài, khôi phục thì xanh lại
- [x] T11.3: `compose.staging.yml` / `compose.prod.yml` | Date: 23/08/2026 | Note: rà 26/8 — cả hai tệp tồn tại; `compose.staging.yml` `include` nguyên văn bản prod và chỉ ghi đè `mem_limit`, nên hai môi trường không trôi khỏi nhau
- [x] T11.4: `migrator` là service riêng chạy trước; app khởi động với Flyway tắt | Date: 23/08/2026 | Note: rà 26/8 — `compose.prod.yml` có service `migrator` + `FLYWAY_ENABLED: "false"` ở service `app`; `SeedGateTest.composeRangBuocThuTu` canh thứ tự `depends_on: service_completed_successfully`
- [x] T11.5: `pg_dump` tự động trước mỗi lượt deploy | Date: 23/08/2026 | Note: rà 26/8 — `deploy/backup/pre-deploy-dump.sh` tồn tại và được `deploy.yml` gọi; T11.21-i mở rộng sang CẢ staging. ⚠ Tên tệp `predeploy-*` là thứ duy nhất phân biệt với bản đêm — xem T11.34
- [x] T11.6: Nginx biên — TLS 1.3, HSTS, rate limit, giới hạn body size, ẩn version | Date: 23/08/2026 | Note: rà 26/8 — có đủ `TLSv1.3` · `limit_req` · `client_max_body_size` · `server_tokens` ở `default.conf.template`, `Strict-Transport-Security` ở `snippets/edge-headers.conf`. ⭐ CSP/X-Frame-Options/Referrer-Policy CỐ Ý đặt ở hai image FE chứ không ở nginx (mỗi image tự phục vụ tên miền của nó); `NginxSecurityHeadersTest` canh đúng những chỗ đó
- [x] T11.8: Hai workflow triển khai | Date: 25/08/2026 | Note: rà 26/8 — `deploy-staging.yml` kích hoạt bằng `push: branches: [staging]`; `deploy-prod.yml` chỉ `workflow_dispatch` với hai input (SHA đầy đủ 40 ký tự + lý do) và `environment: production`. Cả hai gọi thân chung `deploy.yml` (T11.21-e). Environment `production` + `staging` đã tồn tại trên GitHub
- [x] T11.10: Chốt tham số node/worker/shedlock cho môi trường thật | Date: 25/08/2026 | Note: rà 26/8 — `SHEDLOCK_ENABLED=false` + `WORKER_ENABLED=true` có mặt ở cả ba tệp env mẫu kèm lý do ("v1 chạy 1 node; bật khi ≥2 node"). Smoke test sau deploy đã có 3 câu (T11.21-h)
- [x] T11.32: Dependency graph đã bật (nợ #45) | Date: 26/08/2026 | Note: **rà bằng hai tín hiệu độc lập, không tin cấu hình**: (1) job `Soi phụ thuộc PR thêm vào` chạy `success` — KHÔNG `skipped` — ở run #32882796669; (2) endpoint `dependency-graph/sbom` trả về SBOM. Bản ghi cũ trong `cicd.md` §8 và `CLAUDE.md` đã lỗi thời

### Đã làm — dựng đường ống

- [x] T11.7-b: Cổng secret của lượt triển khai — production thiếu secret thì **DỪNG ĐỎ**, không bỏ qua trong im lặng | Note: **vá 26/8.** Bản cũ chỉ hỏi `HOST` và luôn `::warning::` + `ready=false`, nên mọi bước sau tự bỏ qua và lượt chạy XANH TRỌN VẸN. Tách logic ra `.github/scripts/kiem-secret-may-chu.sh` để `SecretGateTest` (7 bài) **chạy thật** từng tổ hợp — đọc chữ trong YAML không phân biệt được hai trạng thái (luật 9). Ba trạng thái: đủ 4 → đi tiếp · thiếu MỘT SỐ → đỏ ở cả hai môi trường (cấu hình dở dang ≠ chưa dựng) · thiếu CẢ BỐN → production đỏ, staging cảnh báo. Kiểm chứng ngược 3 kịch bản. ⭐ Kèm `CiPathFilterTest` (4 bài): bộ lọc CI cũ chỉ bao `.github/workflows/` nên một PR chỉ đụng `.github/scripts/` sẽ **bỏ qua job backend đúng lúc bài canh script ấy cần chạy** — và `skipped` được GitHub tính là ĐẠT. Bài mới quét mã nguồn test tìm mọi đường dẫn ngoài `backend/` rồi thử CHÍNH biểu thức trong `ci.yml` với từng đường, nên nó không mục theo thời gian
- [x] T11.1: Build & push image lên GHCR, tag theo commit SHA
- [x] T11.0: Rà đường triển khai staging bằng mã thật trước khi bắt tay | Date: 24/08/2026 | Note: 4 lỗi CHẶN, cả 4 im lặng; §10.39
- [x] T11.2-a: Chốt cấu hình VPS-2 — 2 vCPU / 8 GB / 80 GB | Date: 24/08/2026 | Note: bản đầu ghi 4 GB, thiếu khi cộng đủ bộ nhớ; §10.39
- [x] T11.12: Bộ seed nội dung staging `deploy/seed/` — 4 ảnh + 5 bài | Date: 25/08/2026 | Note: chỉ staging, có cổng chặn; §10.44
- [x] T11.16: Dựng đường nạp nội dung staging — CD xanh mà cổng không có dữ liệu | Date: 25/08/2026 | Note: §10.45

### Đã làm — CD Staging đỏ ba lượt liên tiếp

- [x] T11.13: Lượt 1 — PR bị squash nên `HEAD^2` không tồn tại | Date: 25/08/2026 | Note: thông báo lỗi trỏ vào ba chỗ đều đang tốt; §10.42
- [x] T11.14: Lượt 2 — lượt tra image GHCR đi ẩn danh | Date: 25/08/2026 | Note: §10.43
- [x] T11.15: Lượt 3 — thiếu `packages: read` cho một workflow có GHI | Date: 25/08/2026 | Note: §10.43
- [x] T11.18: CI backend đỏ vì tải hụt bản phân phối Maven | Date: 25/08/2026 | Note: URL đã đổi ở upstream; §10.46
- [x] T11.19: Bỏ `docker compose` khỏi script vận hành | Date: 25/08/2026 | Note: compose nội suy TOÀN BỘ tệp trước khi trả lời, kể cả lệnh chỉ đọc; §10.48
- [x] T11.20: `minio-init` chưa từng chạy — staging không có bucket nào | Date: 25/08/2026 | Note: §10.49
- [x] T11.11: Vá 2 lỗi lộ ra sau lượt deploy staging đầu tiên | Date: 25/08/2026 | Note: 204 bị biến thành lỗi trên 24 endpoint · `DB_APP_PASSWORD` không ai đọc; §10.40 · §10.41

### Đã làm — T11.21 dọn đường ống (đổi hình dạng, không vá tiếp) · §10.50

- [x] T11.21: Gói 9 mục dưới đây; bỏ 823 dòng | Date: 25/08/2026 | Note: 7 sự cố §10.42→§10.49 đều nằm trên một đường 6 khâu CHỈ chạy khi có người bấm
- [x] T11.21-a: Bộ seed thành migration Flyway ở location riêng `db/seed/portal`, mặc định `db/seed/none` rỗng | Date: 25/08/2026 | Note: chặn ở LOCATION vì migration này có lệnh xoá bài
- [x] T11.21-b: Byte ảnh qua `minio-init`; bố cục thư mục CHÍNH LÀ storage_key, không tiền tố viết cứng | Date: 25/08/2026
- [x] T11.21-c: Thứ tự byte-trước-hàng đặt bằng `migrator depends_on minio-init`, không bằng thứ tự dòng lệnh | Date: 25/08/2026 | Note: luật 12
- [x] T11.21-d: Vị từ xoá canh theo QUAN HỆ menu — `DELETE FROM articles` trần dừng giữa chừng vì khoá ngoại RESTRICT | Date: 25/08/2026
- [x] T11.21-e: `SeedGateTest` 9 bài + `SeedPortalMigrationTest` 2 bài chạy chính tệp SQL rồi ROLLBACK | Date: 25/08/2026 | Note: bản đầu ĐỎ OAN vì khớp trúng cụm trong chú thích (luật 2)
- [x] T11.21-f: Gộp 2 workflow deploy thành `deploy.yml` dạng `workflow_call` | Date: 25/08/2026 | Note: reusable không tự cấp quyền → phải canh thêm vế caller
- [x] T11.21-g: Job CI gắn tag SHA lên ĐÚNG digest cũ → bỏ vòng quét 50×3 | Date: 25/08/2026 | Note: §10.43 tự tiêu
- [x] T11.21-h: Smoke test 3 câu — thêm "cổng có ≥N bài" và "`GET /files/<id>` trả `image/*`" | Date: 25/08/2026 | Note: câu 3 là phép kiểm DUY NHẤT chứng minh MinIO có byte
- [x] T11.21-i: `pg_dump` trước deploy chạy ở CẢ staging; `NoOrphanServiceTest` tự tìm workflow | Date: 25/08/2026 | Note: danh sách viết cứng đã trỏ vào 2 tệp rỗng sau khi gộp

### Đã làm — T11.22 triển khai theo digest · §10.51

- [x] T11.22: Gói 4 mục dưới đây | Date: 25/08/2026
- [x] T11.22-a: Triển khai theo **digest**, không theo tag | Date: 25/08/2026 | Note: tag là một cái tên và tên gán lại được, kể cả bởi chính job gắn tag bù
- [x] T11.22-b: Quay lui tự động khi smoke test đỏ — hỏi máy chủ *cái gì đang chạy* trước khi đổi | Date: 25/08/2026 | Note: `failure()` chứ không `always()`; đây là quay lui MÃ NGUỒN, không phải dữ liệu
- [x] T11.22-c: `make rehearse` — diễn tập đúng compose + đúng lệnh `run --rm migrator` ở máy | Date: 25/08/2026 | Note: vá nguyên nhân gốc §10.42→§10.49: đường triển khai chưa từng có cách thử nào ngoài một lượt deploy thật
- [x] T11.22-d: `ScriptDockerLookupTest` nới luật — gọi compose thì phải TỰ CẤP đủ 3 biến image và cấp TRƯỚC lượt gọi | Date: 25/08/2026 | Note: chính bản đầu của `rehearse.sh` bị bắt

### Đã làm — T11.23 ảnh cổng chưa từng ra được một byte · §10.52 · PR #35

- [x] T11.23: Gói 3 mục dưới đây | Date: 25/08/2026 | Note: ⚠ ba giả thuyết hạ tầng đầu của tôi đều SAI; thứ chấm dứt việc đoán là log ứng dụng
- [x] T11.23-a: `ResponseEnvelopeAdvice` chỉ bọc khi converter là converter JSON — DANH SÁCH CHO PHÉP | Date: 25/08/2026 | Note: không viết `instanceof byte[]` vì luôn có loại thứ tư lọt qua (luật 24)
- [x] T11.23-b: 3 bài kiểm tầng core — ảnh, `Resource`, và JSON vẫn bọc như cũ | Date: 25/08/2026
- [x] T11.23-c: 1 bài đi ĐƯỜNG PRODUCTION — MinIO thật → HTTP thật → so từng byte | Date: 25/08/2026 | Note: endpoint CÓ bài kiểm cũ nhưng dùng UUID không tồn tại nên chỉ đi nhánh 404 (luật 7)

### Đã làm — T11.24 bản vá không bao giờ được nạp · §10.53 · PR #37

- [x] T11.24: Gói 3 mục dưới đây | Date: 25/08/2026 | Note: cả chuỗi chỉ chứng minh digest TỚI ĐĨA; không dòng nào đo container đang chạy cái gì (luật 9)
- [x] T11.24-a: `up -d --force-recreate` — triển khai là mệnh lệnh, không phải gợi ý để compose cân nhắc | Date: 25/08/2026
- [x] T11.24-b: Đọc lại ID ảnh từng container so với ID của ref vừa triển khai, báo cáo trọn vẹn cả 3 service rồi mới thoát | Date: 25/08/2026 | Note: luật 11; vế 1 KHÔNG thay được vế 2
- [x] T11.24-c: `DeployImageProofTest` 5 bài canh trên thân workflow ĐÃ BỎ chú thích | Date: 25/08/2026 | Note: ⚠ bộ `docker` giả lượt đầu xanh cả hai kịch bản vì đọc nhầm chỉ số đối số — chính bước chứng minh suýt là xanh giả

### Đã làm — T11.25 + T11.26 minio-init và công tắc seed · §10.55

- [x] T11.25: `minio-init` ĐO tài khoản dịch vụ thay vì khai báo nó | Date: 26/08/2026 | Note: gói 3 mục dưới
- [x] T11.25-a: `mc admin user add` tách nhánh bằng `user info` — lỗi thật dừng deploy ngay | Date: 26/08/2026 | Note: `|| true` CÓ lý do chính đáng nên không bỏ nó, mà đo trạng thái cuối
- [x] T11.25-b: Probe ghi→đọc→xoá bằng CHÍNH cặp khoá ứng dụng | Date: 26/08/2026 | Note: mã thoát `mc admin` chỉ nói lệnh chạy xong, không nói quyền có hiệu lực (luật 9)
- [x] T11.25-c: Đo bằng MinIO thật 4 kịch bản; secret sai → cũ thoát 0 in `✓`, mới thoát 1 | Date: 26/08/2026 | Note: ⚠ lượt đo đầu sai vì zsh không có `PIPESTATUS`
- [x] T11.26: Bộ seed rút về MỘT công tắc — lệch thành không biểu diễn được | Date: 26/08/2026 | Note: gói 3 mục dưới
- [x] T11.26-a: `minio-init` đọc thẳng `SEED_LOCATION`, `/seed-media` cố định | Date: 26/08/2026 | Note: công tắc thứ hai chỉ tồn tại để LỆCH với công tắc thứ nhất
- [x] T11.26-b: Gỡ biến khỏi 2 env mẫu + `deploy/seed/README.md` + 2 dòng chẩn đoán `deploy.yml` | Date: 26/08/2026 | Note: không cần thao tác tay trên máy chủ
- [x] T11.26-c: `SeedGateTest.motCongTacDuyNhat` — quét TOÀN BỘ `deploy/`, tên biến cũ không được quay lại | Date: 26/08/2026 | Note: bài canh cặp cũ soi tệp mẫu trong repo, không soi `.env` đang chạy (luật 12)

### Đã làm — T11.27 trang chủ nướng rỗng + dữ liệu bịa che chỗ rỗng · §10.54 · PR #39

- [x] T11.27: Hai lỗi xếp chồng và chúng CHE NHAU | Date: 26/08/2026 | Note: A sinh ra trang rỗng, B làm trang rỗng trông đầy; smoke test xanh và người dùng thấy trang trống — cả hai đều đúng
- [x] T11.27-a: `await connection()` ở CHOKEPOINT `apiGetWithMeta` — chỗ duy nhất mọi lượt đọc API đi qua | Date: 26/08/2026 | Note: đặt ở `page.tsx` thì `sitemap.ts` lọt (luật 12)
- [x] T11.27-b: KHÔNG dùng `dynamic = 'force-dynamic'` — nó hạ fetch xuống `no-store` | Date: 26/08/2026 | Note: giữ ISR thì phải thêm `fetchCache`, hai công tắc phải nhớ (luật 14)
- [x] T11.27-c: Xoá 19 bài viết bịa ở 4 component | Date: 26/08/2026 | Note: `length >= 4 ? thật : [...thật, ...BIA]` khiến mảng RỖNG cho ra trang ĐẦY
- [x] T11.27-d: Xoá 4 văn bản bịa CÓ SỐ HIỆU VÀ NGƯỜI KÝ (`158/QĐ-SN`, "Chủ tịch Công ty") | Date: 26/08/2026 | Note: cổng của doanh nghiệp nhà nước
- [x] T11.27-e: Xoá 5 trạm thuỷ văn bịa + chấm "live" nhấp nháy + dòng "Cập nhật trực tuyến" | Date: 26/08/2026 | Note: có một mức "Cảnh báo BĐ I" gắn tên cống có thật
- [x] T11.27-f: Xoá 9 số điện thoại bịa — 8 xí nghiệp + 1 số TRỰC BAN PCTT | Date: 26/08/2026 | Note: số người dân gọi khi có sự cố công trình
- [x] T11.27-g: Menu dự phòng đầu trang rút từ 10 mục về 1 | Date: 26/08/2026 | Note: 3 mục cũ trỏ tới chuyên mục không tồn tại, và chỉ hiện đúng lúc backend hỏng
- [x] T11.27-h: Xoá 4 ảnh hotlink Unsplash + 1 video YouTube gán nhãn "Phóng sự … Sông Nhuệ" | Date: 26/08/2026 | Note: `images.remotePatterns` đang để rỗng
- [x] T11.27-i: `site.footer.social.*` rơi về chuỗi rỗng; tên Công ty về `SITE.name` | Date: 26/08/2026 | Note: tên Công ty đang ghi cứng ở hai nơi, viết khác nhau
- [x] T11.27-j: `EmptyBlock` dùng chung — ràng buộc ép ở component, không ép bằng lời dặn | Date: 26/08/2026 | Note: luật 16
- [x] T11.27-k: `noFabricatedContent.test.ts` — 3 tầng, soi TOÀN BỘ `src/`, mỗi hình dạng kèm mẫu vi phạm tự kiểm chứng | Date: 26/08/2026 | Note: backstop cấu trúc bắt cả loại trường chưa ai nghĩ ra
- [x] T11.27-l: Nới regex điện thoại — bản cũ đòi ĐÚNG 4 nhóm số nên không khớp `(024) 3382 4580` | Date: 26/08/2026 | Note: luật 24, lần thứ ba trên cùng một regex
- [x] T11.27-m: Chuyển 3 phép canh hình dạng từ 2 tệp sang bài soi toàn cây; XOÁ hẳn, không `it.skip` | Date: 26/08/2026 | Note: một bài bị bỏ qua đọc như đã có coverage
- [x] T11.27-n: `noBuildTimePrerender.test.ts` — canh chokepoint, thứ tự trước `fetch`, cấm `generateStaticParams` | Date: 26/08/2026
- [x] T11.27-o: Đo hai chiều — gỡ 1 dòng thì `/`, `/_not-found`, `/sitemap.xml` về `○` | Date: 26/08/2026 | Note: ⚠ lượt gỡ đầu KHÔNG đo được gì vì build chết ở `TS6133` trước khi in bảng route (luật 10)
- [x] T11.27-p: Kiểm ở tầng IMAGE bằng đúng đối số rỗng của `ci.yml` | Date: 26/08/2026 | Note: `make ci-local` về nguyên tắc không dựng lại được trạng thái này (§10.38)
- [x] T11.27-q: XOÁ `docs/web-refactor.md` — nguồn THƯỢNG NGUỒN của toàn bộ dữ liệu bịa | Date: 26/08/2026 | Note: nó kê đích danh 5 trạm, số hiệu `158/QĐ-SN`, và gọi bộ mock là "Fallback an toàn"; điều cấm chuyển vào `ui-styles.md` §4.4
- [x] T11.29: 4 ô `EmptyBlock` thường trực — QuanTran chốt **giữ nguyên** | Date: 26/08/2026 | Note: ẩn khối đi thì không ai biết chỗ ấy thiếu gì

## WS-12

- [x] T12.1: Sáu interface ở `core/spi/`: `WorkflowPort`, `NotificationPort`, `AttachmentPort`, `JobPort`, `SettingPort`, `OrgUnitPort`
- [x] T12.2: Bộ record truyền dữ liệu ở `core.spi`: `AllowedAction`, `AttachmentRef`, `AttachmentUploadCommand`, `JobRef`, `JobRequest`, `NotifyRequest`, `OrgUnitRef` + 2 enum `NotifySeverity`/`NotifyChannel`  19/8
- [x] T12.3: Chuyển `WorkflowAware` từ `core.domain.workflow` sang `core.common.persistence`
- [x] T12.4: Service ở `core.application` cài interface tương ứng; bean công khai cho module khác là interface  19/8
- [x] T12.5: Workflow nhiều trạng thái khởi đầu
- [x] T12.6: `AttachmentPort`: hạn mức theo chủ sở hữu (CN-02.3
- [ ] T12.7: HOÃN CÓ CHỦ ĐÍCH
- [x] T12.8: ⭐ Bài kiểm chứng minh ranh giới bắt được vi phạm

## WS-13

- [x] T13.1: Migration `db/migration/cms/`: `categories` (cây 3 cấp, materialized path), `articles`, `article_categories`, `article_versions`, `tags`, `article_tags`
- [x] T13.2: Entity + `@Audited(module="cms")`; `Article implements WorkflowAware`; kế thừa `BaseEntity`, KHÔNG `ScopedEntity`
- [x] T13.3: Seed workflow `ARTICLE` bằng migration: `NHAP · CHO_DUYET · YEU_CAU_CHINH_SUA · XUAT_BAN · GO_BAI · LUU_TRU` + transition kèm `required_permission` và `notify_event`. Quy tắc tách vai trò: `SUBMIT` cần `cms:article:submit`, `APPROVE` cần `cms:article:approve`
- [x] T13.4: Slug: `SlugUtils` bỏ dấu tiếng Việt, cho sửa tay, duy nhất → trùng trả `CMS-2001`
- [x] T13.5: `article_versions`: mỗi lần lưu nội dung ghi một bản; API so sánh (diff) + phục hồi bản cũ
- [x] T13.6: Sửa bài đã xuất bản theo cơ chế copy-on-write
- [x] T13.7: Hẹn giờ đăng: `published_at` tương lai; job 5' quét bài tới hạn → gọi revalidate (đấu nối thật ở WS-16)
- [x] T13.8: Tìm kiếm quản trị (CN-01.8 phần bài viết): `unaccent` + `pg_trgm`, lọc theo danh mục/trạng thái/tác giả/khoảng thời gian, phân trang 20/50/100, sắp xếp qua `PageUtils` (danh sách cột cho phép)
- [x] T13.9: Xoá danh mục còn bài viết → chặn, yêu cầu chuyển bài trước (mã lỗi mới)
- [x] T13.10: Đếm lượt xem theo lô
- [x] T13.11: Mã lỗi mới → `ErrorCode` (BE) và `frontend/admin-app/src/shared/error-map.ts`
- [x] T13.12: Test: Biên tập viên gọi `APPROVE` → 403 · workflow đủ nhánh · slug trùng · phiên bản + phục hồi · hẹn giờ
- [x] T13.13: ⭐ Seed khung danh mục đề xuất (Tin tức + 2 danh mục con · Thông báo · Giới thiệu)

## WS-14

- [x] T14.1: Migration `media_folders`
- [x] T14.2: Tệp media = `attachments` với `owner_type='MEDIA_FOLDER'`
- [x] T14.3: Tải nhiều tệp; giới hạn theo loại đọc từ `settings`: ảnh 10MB · video 500MB · tài liệu 50MB · nén 100MB
- [x] T14.4: Danh sách Grid/List, lọc theo loại/thư mục/ngày, sao chép URL 1 lần bấm.  Ảnh hiển thị là ảnh gốc (T12.7 hoãn) → lưới ảnh bắt buộc `loading="lazy"` + khung CSS cố định, nếu không thì mở một thư mục 200 ảnh là tải về vài trăm MB
- [x] T14.5: Xoá tệp đang được bài viết tham chiếu → cảnh báo có danh sách bài đang dùng; xoá thư mục chỉ khi rỗng
- [x] T14.6: SVG

## WS-15

- [x] T15.1: Migration `banners`, `menu_items` (cây lồng nhau, hai vị trí header/footer độc lập)  19/8
- [x] T15.2: Cấu hình chung website → nhóm `SITE` trong `settings`, không bảng mới: tên site, slogan, logo, favicon, màu chủ đạo/phụ, GA Tracking ID, GTM Container ID  19/8
- [x] T15.3: Footer: khối thông tin công ty, bản đồ nhúng, mạng xã hội, copyright  19/8
- [x] T15.4: Trang đặc biệt: `site.home.blocks` (JSON
- [x] T15.5: ⛔ Widget thuỷ văn: KHÔNG seed tham số nào  19/8
- [x] T15.6: Cache Caffeine + dọn bằng sự kiện `SettingChangedEvent`  19/8
- [x] T15.7: ⭐ Seed menu header/footer đề xuất + 4 trang tĩnh (Giới thiệu chung · Chức năng nhiệm vụ · Cơ cấu tổ chức · Liên hệ)

## WS-16

- [x] T16.1: Nhóm API công khai `@PublicEndpoint`: danh sách bài, chi tiết theo slug, theo danh mục, menu, banner, cấu hình site. ⛔ Chỉ trả bài `XUAT_BAN` và `published_at <= now()`
- [x] T16.2: Giới hạn tần suất riêng cho nhóm công khai + cache; không đụng bucket của API quản trị
- [x] T16.3: Trang Next: danh sách, chi tiết, theo danh mục, tìm kiếm; dùng ISR
- [x] T16.4: SEO: metadata + Open Graph theo từng bài; `sitemap.ts` đọc từ DB thay vì danh sách tĩnh; giữ nguyên cơ chế tự chặn lập chỉ mục ở staging/local
- [x] T16.5: ⭐ `POST /api/revalidate` đấu nối thật vào bước xuất bản và bước hẹn giờ tới hạn; có bí mật chia sẻ, có ghi log lượt gọi. Đi qua hàng đợi job (`CMS_PORTAL_REVALIDATE`) chứ không gọi thẳng
- [x] T16.6: Ảnh trong bài: quyết định đường phục vụ tệp công khai từ MinIO (bucket công khai riêng hay proxy qua BE)
- [x] T16.7: Trang 404/500; bài `GO_BAI` trả 404 nhưng giữ nguyên dữ liệu; bài `LUU_TRU` không lên danh sách nhưng vẫn vào được bằng URL trực tiếp (kèm `noindex`)
- [x] T16.8: Kiểm chứng đầu-cuối: soạn → gửi duyệt → duyệt → xuất bản → cổng hiện bài, đo thời gian thật từ lúc bấm tới lúc trang đổi

## WS-17

- [x] T17.1: Migration `db/migration/ops/`: `constructions` + `pump_station_specs` + `sluice_specs` + hồ sơ tối thiểu cho đê/kênh. Mọi số đo `NUMERIC`, tiền `NUMERIC(18,2)` VND
- [x] T17.2: ⭐ `Construction extends ScopedEntity`
- [x] T17.3: Mã công trình duy nhất toàn hệ thống; gợi ý tự sinh nhưng cho sửa
- [x] T17.4: Toạ độ `Decimal(9,6)` + cột PostGIS; `river_name`, `chainage` (`K<km>+<m>`); danh sách "Công trình chưa có vị trí GIS"
- [x] T17.5: Lưu vực / khu tưới tiêu = trường văn bản (chốt F3)
- [x] T17.6: Trạng thái vận hành là cột dẫn xuất (tính ở WS-19); API nhận `status` từ client → `OPS-3001`
- [x] T17.7: Tài liệu công trình (CN-02.3) qua `AttachmentPort`: hạn mức 500MB/công trình, nhãn loại tài liệu, ngày lập + ngày hết hiệu lực, phiên bản
- [x] T17.8: Nhật ký thay đổi hồ sơ (CN-02.7) = API đọc `audit_logs` lọc theo `entity_type='CONSTRUCTION'`
- [x] T17.9: Nhập từ Excel/CSV: chạy khô trước (xem trước + báo lỗi từng dòng + đếm sẽ thêm/sửa bao nhiêu), có lỗi chặn thì không nhập dòng nào. Đây cũng là đường seed dữ liệu thật khi G8 về
- [x] T17.10: Thống kê & tìm kiếm (CN-02.6): đếm theo loại / đơn vị / trạng thái / cấp quản lý; lọc trên danh sách. Biểu đồ để Phase 3
- [x] T17.11: `construction_clusters` (mã, tên, đơn vị quản lý, thứ tự) + `constructions.cluster_id` nullable + CRUD danh mục cụm
- [x] T17.12: Test: tầng 3 đủ 3 nhánh (đơn vị mình · cấp trên thấy cấp dưới · đơn vị khác → `AUTH-3002` + `security_events`) + mã lỗi mới

## WS-18

- [x] T18.1: Migration `maintenance_logs`: `NUMERIC(18,2)` VND; `performer_org_unit_id` hoặc `performer_name` + CHECK đúng một
- [x] T18.2: Phạm vi đơn vị: sao chép `org_unit_id` từ công trình lúc tạo.  Ghi rõ hệ quả: công trình đổi đơn vị phụ trách thì bản ghi cũ giữ nguyên đơn vị lúc phát sinh  21/8
- [x] T18.3: Seed workflow: `MOI → DANG_XU_LY → DA_XU_LY`, hai trạng thái khởi đầu (T12.5)
- [x] T18.4: Quy tắc nghiệp vụ, mã lỗi đã có sẵn trong catalog: `OPS-2003` · `OPS-2001` · `OPS-2004` · `OPS-2002`. Thêm `OPS-2017` (đơn vị thực hiện đúng một trong hai) → 72 mã, BE = FE  21/8
- [x] T18.5: Mã bản ghi `BT-<năm>-xxxx` qua `code_sequences` của Core  21/8
- [x] T18.6: Tệp đính kèm: biên bản nghiệm thu, ảnh trước/sau
- [x] T18.7: Timeline theo công trình + bộ lọc; tổng chi phí theo kỳ tính ở BE (quy tắc 3)
- [x] T18.8: Danh sách "Sự cố chưa xử lý"
- [x] T18.9: Quyền sửa/xoá sau khi lưu theo `function-spec.md` §6 + cửa sổ tác giả tự sửa đọc từ `settings`, mặc định tắt  21/8
- [x] T18.10: `alert_event_public_id UUID` không FK
- [x] T18.11: Test: 22 bài qua HTTP + 8 bài phạm vi/cấu trúc  21/8

## WS-19

- [x] T19.1: Migration `operation_status_codes` + CRUD + tham số + màu | Date: 23/08/2026 | Note: đường dẫn sửa/xoá đổi sang publicId; danh sách quản trị nay lọc deleted_at
- [x] T19.2: `construction_operation_status` append-only, "hiện hành" theo `effective_at`, chỉ mục `(construction_id, effective_at DESC)` | Date: 23/08/2026
- [x] T19.3: Mã lỗi OPS-2005/2006/2007 + thêm OPS-2018 (ghi vào mã đã ẩn) | Date: 23/08/2026
- [x] T19.4: Mắt xích 4 vào `ConstructionStatusService.tinh()` | Date: 23/08/2026 | Note: đổi sang câu native — bản cũ dùng derived query nên bị lọc phạm vi, người ngoài đơn vị mở màn hình là trạng thái rơi về BINH_THUONG
- [x] T19.4-b: Job đối soát `StatusReconcileJob` | Date: 23/08/2026 | Note: bỏ findAll() gồm hồ sơ đã xoá mềm; đấu dây tham số ops.operation-status.stale-days
- [x] T19.5: `HydroAlertPort` trả rỗng ở Phase 1 | Date: 23/08/2026
- [x] T19.6: Nhập nhanh hàng loạt, một giao dịch, lỗi báo theo từng dòng | Date: 23/08/2026 | Note: đổi sang hai pha kiểm-hết-rồi-ghi; trả OPS-2019 kèm details items[i]. Lỗi phạm vi đơn vị KHÔNG bị gom — vẫn là 403 và vẫn ghi sự kiện an ninh
- [x] T19.7: Cảnh báo mềm quá N ngày chưa cập nhật | Date: 23/08/2026 | Note: gửi qua NotificationPort tới người có ops:operation-status:update
- [x] T19.8: Test các nhánh nghiệp vụ | Date: 23/08/2026 | Note: 8 bài qua HTTP + 24 bài đơn vị. Đã bổ sung bài `effective_at` lùi quá khứ không đổi "hiện hành" và bài lô nhiều dòng lỗi báo đủ một lượt
- [x] T19.9: Endpoint đọc lịch sử tình hình vận hành | Date: 23/08/2026 | Note: lấp quyền chết ops:operation-status:view — cấp cho 6 vai trò từ WS-5 mà không endpoint nào đòi

## WS-20

- [x] T20.1: Trình soạn thảo
- [x] T20.2: Danh sách bài + bộ lọc + thao tác hàng loạt + phân trang phía máy chủ.  Xoá hàng loạt chạy tuần tự: hỏng giữa chừng thì biết chính xác đã xong tới đâu, và không bắn hai chục giao dịch song song vào hệ 200 người dùng
- [x] T20.3: Biểu mẫu bài viết: SEO đếm ký tự có cảnh báo vượt ngưỡng, ảnh đại diện, hẹn giờ đăng
- [x] T20.4: ⭐ Nút duyệt render từ `allowedActions` của API
- [x] T20.5: So sánh phiên bản (diff) + phục hồi bản cũ. So theo khối văn bản chứ không theo từ trên chuỗi HTML
- [x] T20.6: Cây danh mục kéo thả, chặn kéo vào chính nhánh con trước khi gửi lên
- [x] T20.7: Thư viện media: tải nhiều tệp có thanh tiến trình từng tệp, hộp chọn ảnh cho bài viết.  Xoá tệp thì hỏi backend xem bài nào đang dùng trước
- [x] T20.8: Cấu hình giao diện: banner (đổi thứ tự bằng nút, không kéo thả
- [x] T20.9: Không phát sinh mã lỗi mới ở WS-20
- [x] T20.10: Test FE cho các hàm thuần: 43 bài mới (SEO 11 · diff 13 · cây 10 · chuẩn hoá URL video 9)
- [x] T20.11: ⭐⭐ Chèn ảnh đúng vị trí
- [x] T20.12: Bộ từ vựng chuyển sang `design-tokens/src/editor-schema.ts`
- [x] T20.13: Gỡ `@tiptap/extension-image` và `@tiptap/extension-text-align`

## WS-21

> ⚠ **Đã nghiệm thu lại ngày 23/8.** Bản ghi cũ đánh dấu toàn bộ WS-21 là xong; đối chiếu với mã
> thật thì **4/11 mục chưa làm hoặc hỏng hẳn**, trong đó hai mục là placeholder văn bản và một mục
> khoá đúng vai trò sở hữu nó. Chi tiết nguyên nhân: `architecture-review.md` §10.36.

- [x] T21.1: Danh sách công trình + bộ lọc + khối thống kê + phân trang phía máy chủ | Date: 23/08/2026 | Note: 6 ô lọc khớp đúng tham số của GET /ops/constructions
- [x] T21.2: Biểu mẫu hồ sơ đổi theo loại công trình | Date: 23/08/2026 | Note: 4 bước; khối thông số lọc theo loại bằng hàm thuần có bài kiểm (bẫy ô ẩn của AntD giữ giá trị cũ)
- [x] T21.3: Chọn toạ độ trên bản đồ (Leaflet + OSM) | Date: 23/08/2026
- [x] T21.4: Tab tài liệu | Date: 23/08/2026 | Note: DỰNG LẠI. Bản cũ gọi /attachments?ownerId=<uuid> vào tham số kiểu Long → 400 ở mọi lượt mở tab, và gate bằng quyền ops:construction:update thay vì ops:document:*
- [x] T21.5: Tab lịch sử sửa chữa: timeline + biểu mẫu ghi nhận + nút từ allowedActions + tổng chi phí từ /cost-summary | Date: 23/08/2026 | Note: VIẾT MỚI. Bản cũ là một dòng chữ "sẽ được tích hợp trong phiên bản sau" nhưng vẫn đánh dấu xong
- [x] T21.6: Màn hình nhập nhanh tình hình vận hành dạng bảng | Date: 23/08/2026 | Note: viết lại hợp đồng gọi API; nút cũng đổi sang gate bằng ops:operation-status:update cho khớp endpoint
- [x] T21.7: Danh mục mã tình hình vận hành: CRUD + ColorPicker | Date: 23/08/2026 | Note: đổi sang publicId, bỏ trường id khỏi phản hồi
- [x] T21.8: Nhật ký thay đổi hồ sơ (CN-02.7) | Date: 23/08/2026
- [x] T21.9: Nhập Excel: tải lên → xem trước chạy khô → xác nhận, hai nút tách bạch | Date: 23/08/2026
- [x] T21.10: Trả nợ #71: bấm biểu đồ dashboard → mở danh sách ĐÃ LỌC | Date: 23/08/2026 | Note: SỬA. Dashboard vẫn điều hướng sang ?status=… nhưng trang danh sách không đọc query string nên mở ra danh sách không lọc
- [x] T21.11: Test FE cho các hàm thuần | Date: 23/08/2026 | Note: VIẾT MỚI 19 bài — trước đó thư mục operations không có tệp test nào. Gồm cả bài chứng minh vì sao không nhân 1e6 trên số thực
- [x] T21.12: Ô chọn đơn vị dùng /org-units/selectable | Date: 23/08/2026 | Note: TECHNICIAN là vai trò DUY NHẤT tạo được công trình nhưng ô chọn đơn vị gọi /org-units/tree đòi adm:org-unit:view — biểu mẫu tạo hồ sơ chưa từng chạy được với đúng vai trò của nó

## WS-23

- [x] T23.1: Theme ECharts sinh từ `design-tokens`
- [x] T23.2: Bộ component biểu đồ dùng chung (`LineChart`, `BarChart`, `PieChart`, `GaugeChart`)
- [x] T23.3: Nạp ECharts theo kiểu chọn lọc (chỉ import loại biểu đồ dùng tới)
- [x] T23.4: `KpiCard` + `ChartCard` + khung lưới dashboard tự xếp lại theo bề rộng
- [x] T23.5: Móc tự làm mới theo chu kỳ đọc từ `settings` (M2.15, mặc định 5')
- [x] T23.6: API tổng hợp `GET /api/v1/ops/dashboard`
- [x] T23.7: KPI card: tổng công trình đang hoạt động/tổng · số công trình theo trạng thái. ⛔ Ô nào chưa có nguồn (cảnh báo thuỷ văn → Phase 2; sự cố chưa xử lý → WS-18) hiện "Chưa có dữ liệu" kèm lý do, không hiện số 0
- [x] T23.8: Biểu đồ thống kê công trình (CN-02.6): theo loại · theo đơn vị · theo cấp quản lý.  Phần "bấm vào cột mở danh sách đã lọc" CHƯA làm → nợ #71, vì màn hình danh sách công trình thuộc WS-21 và chưa tồn tại: một liên kết trỏ tới route không có thật trông như chức năng có mà hỏng, tệ hơn hẳn chức năng chưa có
- [x] T23.9: Bản đồ GIS tổng quan: marker theo toạ độ thật, màu theo trạng thái, popup theo M2.10. Công trình chưa có toạ độ đưa vào một danh sách riêng thay vì bỏ im
- [x] T23.10: ⭐ Wall mode `?mode=wall`
- [x] T23.11: Test: hàm gom số liệu ở BE (đủ nhánh "chưa có nguồn") · bố cục wall ở ba bề rộng 3840/1920/1366, khẳng định cả hai vế: không tràn ngang và không mất khối

## WS-22

> ⚠ **Nghiệm thu lần ba ngày 24/8** (T22.15→T22.20). Lượt này xuất phát từ một việc nhỏ — chạy
> `make dev-docker` rồi soi image — và tìm ra **một lỗi chặn nghiệp vụ nằm trong phạm vi Phase 1**
> đã được tick từ 23/8, cộng một lỗ lộ mã nguồn và một lỗ ở bộ lọc CI. Nguyên nhân gốc:
> `architecture-review.md` §10.37.

- [x] T22.1: `RbacMatrixTest` đối chiếu trên CSDL thật với `function-spec.md` §6 | Date: 23/08/2026 | Note: lượt 22/8 thêm 44 quyền vào danh sách miễn kiểm, trong đó 7 quyền ĐANG dùng thật qua workflow_transitions. Nay phép quét đọc cả hai kênh khai báo và có bài canh danh sách miễn kiểm không phình
- [x] T22.2: ⭐ Nâng cổng bao phủ tầng domain
- [x] T22.3: Luật ArchUnit cho module nghiệp vụ.  Xong 22/8
- [x] T22.4: Test tích hợp đầu-cuối ba luồng, đều qua HTTP.  Xong 22/8
- [x] T22.5: Đo hiệu năng.  Xong 22/8
- [x] T22.6: Bổ sung `docs/coding-guide.md` bằng bẫy mới gặp trong Phase 1.  Xong 22/8
- [x] T22.7: Rà soát nợ + đồng bộ tài liệu.  Xong 22/8
- [x] T22.8: ⭐⭐ Chạy tay lại mọi thứ đã tick.  Xong 22/8
- [x] T22.9: Quét lại toàn bộ đường ghi có thể lách phạm vi đơn vị | Date: 23/08/2026 | Note: lượt 22/8 ghi "đều pass" trong khi đường ghi tình hình vận hành nhận khoá tự tăng và tra bằng findById — không qua bộ lọc phạm vi. Nay có luật ArchUnit đếm đủ mọi @PathVariable và mọi trường khoá trong DTO nhận
- [x] T22.10: Rà `business-open-questions.md` Phần III.  Xong 22/8
- [x] T22.11: Luật cấu trúc chặn khoá nội bộ lọt ra API (`ApiSurfaceRuleTest`) | Date: 23/08/2026 | Note: 5 bài, có bài canh ngoại lệ không phình và bài chống xanh-trên-tập-rỗng
- [x] T22.13: Nghiệm thu lại WS-21 và 17 mục DoD Phase 1 | Date: 23/08/2026 | Note: 4/11 mục WS-21 chưa làm; 3/17 mục DoD không có phép kiểm nào (DOD1.6/1.7/1.11) và DOD1.5 chưa từng có bài kiểm phía BE
- [x] T22.14: Cổng bao phủ tầng domain của module content CHẠY THẬT | Date: 23/08/2026 | Note: trước đó module không có bài kiểm nào nên JaCoCo bỏ qua luật trong im lặng; nay 18.2% và cổng thật sự chặn
- [x] T22.12: Dọn tracking về một nguồn | Date: 23/08/2026 | Note: xoá `phase1-execution-tracking.md`; gộp 29 mã số trùng (19 cặp mâu thuẫn trạng thái); tách DoD ra mục riêng có mã số; sửa bộ đọc MCP + thêm phép kiểm chạy trên file thật
- [x] T22.15: Nghiệm thu image `make dev-docker` | Date: 24/08/2026 | Note: bản dựng ĐỎ ở nhánh đã push — 4 lỗi TypeScript từ commit 40685c8. CI có bước typecheck bắt được cả 4 nhưng chỉ kích hoạt trên `dev`, nên nhánh tính năng chưa mở PR đi qua không cổng kiểm nào. Sau khi vá: image dựng lại, 6/6 container healthy, `/api/v1/**` trả 401/200 đúng, FE gọi cùng origin 200
- [x] T22.16: ⛔ Lỗi CHẶN — trả bài về sửa không dùng được | Date: 24/08/2026 | Note: `ArticleController` ép buộc lý do bằng dòng khai cứng, còn FE mở ô nhập theo cờ `requiresReason` mà record `AllowedAction` KHÔNG có và không ai điền → bấm "Yêu cầu chỉnh sửa" là tắc hoàn toàn. `ArticleHttpTest.traBaiPhaiNeuLyDo` kiểm cả hai vế ràng buộc và vẫn xanh vì gửi JSON dựng tay. Chữa: cột `workflow_transitions.requires_reason` (V202608241256), engine tự ép buộc thay controller, `primary`/`danger` gỡ hẳn vì không ai ghi
- [x] T22.17: Image admin-app phát nguyên mã nguồn ra ngoài | Date: 24/08/2026 | Note: `sourcemap: true` không rào môi trường → 68 tệp `.map` trong image, GET trả 200 kèm 4.799 byte TypeScript gốc. Chữa ở hai tầng: `sourcemap: false` + `location ~ \.map$ { return 404; }`. Đo lại sau khi dựng: 0 tệp, lượt tải 404
- [x] T22.18: Bộ lọc CI bỏ qua đúng job canh những tệp vừa đổi | Date: 24/08/2026 | Note: job `backend` lọc `^(backend/|.github/workflows/)` trong khi **7 lớp kiểm BE đọc tệp ngoài `backend/`** (FrontendSameOriginTest, NginxSecurityHeadersTest, EnvFileCommentTest, UnresolvedPlaceholderGuardTest, EditorVocabularyTest, AllowedActionParityTest, SongnhuePostgres). PR chỉ đụng `frontend/` hoặc `deploy/` là bỏ qua sạch — mà `skipped` được tính là ĐẠT. Đã thêm `frontend/` + `deploy/`; kiểm logic bằng `bash -c` với 7 kịch bản
- [x] T22.19: Hợp nhất nhánh `fix-public-web-ui` | Date: 24/08/2026 | Note: merge KHÔNG đụng độ nhưng bộ test FE trên cây đã merge ĐỎ — bản vá giao diện đặt lại liên hệ Công ty vào mã nguồn làm dự phòng `??`. ⚠ Chỉ 1/3 bộ canh bắt được: regex điện thoại đòi khoảng trắng (dữ liệu thật dùng dấu chấm), regex địa chỉ phân biệt hoa thường (địa chỉ mới viết HOA). Đã vá cả hai + thêm bài canh cấu trúc phủ 6 khoá `company.*`
- [x] T22.20: Rà mapping toàn bộ bề mặt API | Date: 24/08/2026 | Note: 68 kiểu TypeScript ↔ 141 record/DTO Java — 42 khớp theo tên, 22 khớp theo hình dạng, 4 xác minh tay. **Lệch thật duy nhất là `AllowedAction`** (T22.16). Có `AllowedActionParityTest` canh tiếp, đặt ở bộ BE vì nguồn sự thật là record Java
- [x] T22.22: Hai job đóng gói image chạy ở cả PR, chỉ ĐẨY GHCR khi push `dev` | Date: 24/08/2026 | Note: trước đó `if: github.event_name == 'push'` khiến lượt dựng image đầu tiên diễn ra **sau khi đã merge** — chỗ duy nhất chúng có thể đỏ là `dev`. Image là nơi DUY NHẤT thấy được thứ chỉ tồn tại lúc build container (`ARG` rỗng, `.env.local` vắng, tầng runtime chép hụt). Kiểm logic tóm tắt bằng `bash -c` với 4 tổ hợp event × matrix (luật 19); YAML parse lại để xác nhận `push:` và điều kiện đăng nhập GHCR đúng
- [x] T22.23: Nợ #46 — context đóng gói image ở `dev` | Note: **áp 26/8**, `dev` 2 → **7** context. Thêm `Đóng gói image` · hai job `Đóng gói image frontend (...)` · và `Soi phụ thuộc PR thêm vào` (luôn báo cáo ở PR nhờ `if: github.event_name == 'pull_request'`). ⚠ `Gắn tag SHA cho image không đổi` CỐ Ý không đưa vào — đo trên PR #41 thì nó CÓ báo cáo (trạng thái `skipping`), nên lý do KHÔNG phải §2.1; lý do thật là một context **luôn** `skipped` ở PR thì **luôn** được tính ĐẠT, tức không chặn được gì
- [x] T22.21: Vá CI đỏ sau khi merge vào `dev` (PR #10) | Date: 24/08/2026 | Note: job **đóng gói image `public-web` — do chính PR này thêm vào — đỏ ngay lần chạy đầu**. `vars.PUBLIC_SITE_URL` chưa đặt → build-arg rỗng → `ARG` không mặc định → `ENV` gán **chuỗi rỗng** → `??` trong `site.ts` không đỡ → `new URL('')` giết `next build`. ⚠ Chính lượt CI ấy in cảnh báo "chưa đặt PUBLIC_SITE_URL → sitemap trỏ localhost", tức tin có mặc định đang đỡ (luật 3). Local không thấy vì mọi lượt build đều nạp `.env.local`. Chữa: `??` → `||` + `site.test.ts` (11 bài, kiểm **hành vi** ở cả hai trạng thái rỗng/chưa-đặt, kèm bài liệt kê biến). Kiểm chứng: trả `??` về → 2 bài đỏ đúng `Invalid URL`; `docker build` đúng đối số CI → thoát 0, image chạy, health 200. Nguyên nhân gốc §10.38

## WS-24 — Đợt chỉnh sửa cổng TTĐT theo nghiệm thu Công ty (27/08/2026)

> Nguồn: `docs_origin/nghiem_thu_phase1.md` — "YÊU CẦU CHỈNH SỬA WEBSITE" v1.0, trạng thái *Ban
> hành để thực hiện*. Bảng §4 ghi "44 mục" nhưng **chỉ có 43 dòng**: mã **CR-13** vắng khỏi bảng
> mà vẫn được §5.2 trích dẫn. Hiểu CR-13 = khối "Mực nước, lượng mưa" trên trang chủ và làm theo
> §5.2.
>
> ⭐ Tài liệu này **đóng mục G14** của `business-open-questions.md` — cây danh mục, menu cổng và
> nội dung trang tĩnh, treo từ 19/8.
>
> ⚠ Bản Công ty rà là bản **~25/8**, trước đợt vá §10.54. Ba mã đã tự hết hiệu lực: **CR-20**
> (ảnh stock xe khách/nhà máy — đã gỡ hết 25/8), **CR-17** phần "4 văn bản mẫu" (số hiệu và
> người ký bịa — đã gỡ), **CR-33** phần "gán cứng số liệu" (5 trạm quan trắc bịa — đã gỡ).

### Đợt 1 — Menu, cây nội dung, trang chủ, chân trang

- [x] T24.1: Cây nội dung chuẩn §3 vào CSDL — `V202608271031__cms_site_taxonomy_v2.sql` | Date: 27/08/2026 | Note: CR-01…CR-07 · CR-09 · CR-30 · CR-31 · CR-32. Menu HEADER **8 mục cấp 1 + 12 mục cấp 2**, FOOTER 7 mục cùng hệ phân loại. Danh mục cũ đổi tên TẠI CHỖ (`tin-chuyen-nganh`→`tin-thuy-loi`, `tin-hoat-dong`→`tin-cong-ty`) để bài viết giữ nguyên liên kết; `thong-bao` ẩn chứ không xoá. `menu_items` xoá rồi dựng lại vì menu cũ/mới không có ánh xạ 1-1
- [x] T24.2: ⚠ **Ba trang tĩnh mất lối vào — xử lý tường minh, không để thành tác dụng phụ** | Date: 27/08/2026 | Note: menu mới chỉ còn trỏ tới `tong-quan`; `chuc-nang-nhiem-vu` (gộp vào Tổng quan, CR-23) · `co-cau-to-chuc` (thay bằng trang đọc `org_units`, CR-24) · `lien-he` (thay bằng trang `/lien-he`, CR-22) thành **mồ côi**. Nguy hiểm hơn: bộ seed staging `V202608251100` **xoá CỨNG mọi bài không có menu trỏ tới**, nên ba bài ấy sẽ biến mất mà không dòng nào nói. Xoá mềm có điều kiện `created_by IS NULL AND updated_at IS NULL` — biên tập viên đã viết nội dung thật thì bài ở lại. `SeedPortalMigrationTest.xoaDungPhamVi` nay canh **cả hai vế**: `tong-quan` phải sống, ba bài kia phải bị dọn
- [x] T24.3: CR-07 — địa chỉ hệ thống văn bản điều hành vào `settings`, **đóng nợ T11.28** | Date: 27/08/2026 | Note: đổi đích `songnhue.bhh40.net` → `quanlyvanban.hanoi.gov.vn` theo tài liệu, và gỡ địa chỉ ghi cứng khỏi **3** tệp giao diện. ⚠ Cùng địa chỉ nằm ở hai bảng (`settings` + `menu_items.url`) → `PortalTaxonomyTest.diaChiHeThongVanBanKhongLech` canh (luật 14). ⛔ `songnhue.bhh40.net` vẫn là nguồn API thuỷ văn MOD-03, KHÔNG bị đụng
- [x] T24.4: CR-10 — slider ảnh hoạt động thay bài viết đinh | Date: 27/08/2026 | Note: `HomeBannerSlider` client component: tự chạy, nút ‹ ›, indicator, tạm dừng khi rê chuột/focus. **Năm tham số đều từ `settings`**, không số nào viết cứng (§2). Thêm khoá `site.slider.max-items`. Chưa có ảnh nào ⇒ `EmptyBlock` nói thẳng, không ảnh mặc định
- [x] T24.5: CR-11 · CR-12 · CR-16 · CR-17 · CR-18 — bố cục trang chủ theo đúng thứ tự §3 | Date: 27/08/2026 | Note: `HomeHotNews` giữ 3 tin Hot (CR-11 ghi "Giữ nguyên") · "Dòng thời sự"→**Tin tức – Sự kiện** kèm nhãn chuyên mục · bỏ hẳn cột "Chỉ đạo điều hành" · "Văn bản & Quyết định"→**Công bố thông tin** · 5 card **đọc từ chính cây menu** thay vì từ `categories` (§2: một hệ phân loại dùng chung). ⛔ Gỡ khối "Thông báo điều hành" lọc bài bằng `title.includes('thông báo')` — phân loại nội dung bằng chuỗi con của tiêu đề
- [x] T24.6: CR-12 cần nhãn chuyên mục → thêm `categories` vào `PublicArticleRow` | Date: 27/08/2026 | Note: JPQL không dựng được collection trong `SELECT new`, nên thêm hàm dựng phụ + **một** lượt hỏi gom cho cả trang (`findCategoryLabels`). Đọc `article.getCategories()` trong vòng lặp là 13 truy vấn cho 12 bài. Lọc `c.visible` ngay trong câu lệnh: chuyên mục đang ẩn không được lộ ra dưới dạng nhãn
- [x] T24.7: CR-35 · CR-36 · CR-37 — khung khối dữ liệu thời gian thực dùng chung | Date: 27/08/2026 | Note: `RealtimeFrame` gom đủ ba ràng buộc §7 vào MỘT nơi: dòng "Cập nhật lúc HH:mm dd/MM/yyyy", nút làm mới tay, trạng thái "Dữ liệu tạm thời chưa khả dụng" giữ nguyên bố cục. Chu kỳ tự làm mới từ `settings` (`site.home.realtime.refresh-seconds`, mặc định 300 — OI-09 chờ Công ty chốt 5/10/15'). ⚠ Mốc thời gian lấy từ **máy chủ** (`GET /public/now`, `revalidate: 0`), không phải `new Date()` máy khách
- [x] T24.8: CR-39 · CR-40 · CR-41 · CR-42 — chân trang | Date: 27/08/2026 | Note: bỏ "Doanh nghiệp 100% vốn Nhà nước" · bỏ email và giờ làm việc khỏi chân trang (**giữ khoá trong `settings`**, vẫn hiện ở trang Liên hệ — OI-04 chờ chốt) · địa chỉ theo địa giới mới `V202608271033` và **thôi ép `uppercase`**. CR-09: gỡ cột "Nghiệp vụ thủy lợi" — 5 liên kết viết cứng tạo hệ phân loại thứ hai, 3 trong số đó trỏ vào mục vừa bị đổi hoặc bỏ
- [x] T24.9: CR-21 — đổi "Sở Nông nghiệp & PTNT Hà Nội" → **"Sở Nông nghiệp và Môi trường Hà Nội"** | Date: 27/08/2026 | Note: ⚠ Hai dòng còn lại (Bộ NN&PTNT · Cục Thuỷ lợi) **giữ nguyên chờ Công ty xác nhận** — CR-21 nói "rà soát lại tên và đường link chính thức", và đổi tên một cơ quan nhà nước theo suy đoán của người viết mã thì sai cũng không ai phát hiện
- [x] T24.10: ⚠⚠ **Cổng công khai chưa từng có CSP nào** — vá cùng lượt | Date: 27/08/2026 | Note: `next.config.ts` ghi *"CSP đặt ở nginx"*, `deploy/nginx/snippets/edge-headers.conf` ghi ngược lại *"hai image FE đã đặt đủ"*. Đọc tệp nào cũng thấy yên tâm, **không tệp nào đặt**, từ WS-16. `NginxSecurityHeadersTest` không bắt được vì chỉ soi `admin-app.Dockerfile`. Phát hiện khi chuẩn bị nhúng iframe Google Map cho CR-22. `csp.test.ts` 11 bài đọc **giá trị đã giải** qua `nextConfig.headers()`, không grep tệp

### Đợt 2 — Endpoint công khai cho tổ chức và công trình (đóng nợ T11.30)

- [x] T24.11: `GET /api/v1/public/org-units/{chart,leaders,subsidiaries}` | Date: 27/08/2026 | Note: CR-19 · CR-24 · CR-25 · CR-26. **DTO liệt kê từng trường bằng tay**, KHÔNG tái dùng DTO của màn hình quản trị: không trả `path` (chuỗi id chạy số), không trả `headUserId`. Đặt ở module `core` vì `org_units` là bảng của core (quy tắc 6)
- [x] T24.12: Bảng `org_unit_leaders` — `V202608271034` | Date: 27/08/2026 | Note: ⚠ **Không phải hai cột `head_name`/`head_phone`**: CR-25 là một DANH SÁCH (Chủ tịch, Giám đốc, các Phó GĐ), hai cột chỉ chứa được một người. Một bảng phục vụ cả CR-25 (dòng của nút Công ty) lẫn CR-26 (dòng đầu của từng Xí nghiệp). ⛔ **KHÔNG nối `employees`** — endpoint công khai không có đường nào chạm trường nhạy cảm (quy tắc 10, NĐ 13/2023). Phân biệt với `org_units.head_user_id`: cột ấy trỏ TÀI KHOẢN cho luồng duyệt, Giám đốc XN có thể không có tài khoản. ⛔ Không seed dòng nào
- [x] T24.13: `GET /api/v1/public/constructions` — bảng 7 cột §5.1 | Date: 27/08/2026 | Note: CR-27 · CR-28 · CR-44. Gom theo Xí nghiệp; lọc công trình đã thanh lý và đã xoá mềm **ngay trong truy vấn** (đường công khai không có tầng phân quyền nào phía sau). Cột "Thông tin chủ yếu" = `pump_count × flow_per_pump_m3s`, thiếu **bất kỳ** vế nào thì trả `null` — không ghép một nửa, không điền 0 (quy tắc 16). Cột "Vị trí" dựng từ `latitude`/`longitude`, **không thêm cột `map_url`** (luật 13: hai nguồn toạ độ là hai nguồn sẽ lệch)
- [x] T24.14: Hai cột tài liệu công bố của công trình — `V202608271035` | Date: 27/08/2026 | Note: `operating_procedure_attachment_public_id` + `protection_plan_attachment_public_id`. Cơ chế đính kèm cũ là một DANH SÁCH không phân loại — không trường nào nói tệp thứ ba là Quy trình vận hành hay ảnh hiện trường, mà cột CR-28 phải trỏ đích danh MỘT tệp
- [x] T24.15: Tám trang mới của cổng | Date: 27/08/2026 | Note: `/gioi-thieu/{co-cau-to-chuc,lanh-dao,xi-nghiep}` · `/quan-ly-van-hanh/{danh-muc-cong-trinh,tien-do-san-xuat,muc-nuoc-luong-mua,van-hanh-cong-trinh}` · `/lien-he`. ⚠ Bảy trong tám là đích của mục menu `link_type='URL'` — dựng đủ trong CÙNG lượt với migration menu, vì một mục menu trỏ vào 404 đúng là hình dạng §10.54. `next build` đo được: cả 8 route là `ƒ` (dựng lúc chạy), `prerender-manifest` chỉ có `/_global-error` + `/robots.txt`, **0 trang có nội dung bị nướng vào image**
- [x] T24.16: CR-30 Tiến độ sản xuất **dựng bằng CMS**, không entity mới | Date: 27/08/2026 | Note: QuanTran chốt 27/8. §5.5 mô tả đúng một luồng (Năm → Vụ → nội dung) nhưng **không nói tiến độ đo bằng chỉ tiêu gì, đơn vị gì, ai nhập** — chưa đủ để thiết kế bảng số liệu. Cây danh mục đã có 3 cấp đúng hình dạng cần; mỗi kỳ là một bài viết, dùng lại nguyên bộ duyệt/đính kèm/audit. Bộ chọn đi qua tham số URL nên không cần JavaScript
- [x] T24.17: CR-22 trang Liên hệ + bản đồ Google Map | Date: 27/08/2026 | Note: đọc thẳng `settings` thay vì để địa chỉ trong thân một bài viết (nguồn thứ hai). Bản đồ **dùng chung khoá** `site.footer.map-embed` với chân trang — một trụ sở thì một bản đồ (luật 14). Cần `frame-src https://www.google.com` → xem T24.10

### Bộ canh mới — mỗi cơ chế đều có bài chứng minh nó bắt được vi phạm (luật 1)

- [x] T24.18: `PortalTaxonomyTest` 5 bài — menu trong CSDL ↔ `ROUTES` của cổng | Date: 27/08/2026 | Note: canh **cả hai chiều**. Chiều "menu → có trang" bắt lỗi 404; chiều "trang → có ai dẫn tới" bắt một trang được dựng, được kiểm, được triển khai mà không lối vào nào. ⭐ **Kiểm chứng ngược có số đo**: bản gốc `grep -c '/gioi-thieu/lanh-dao'` = 1 → 0 đỏ · đổi URL menu sang tuyến không tồn tại (đo: 0 và 1) → **2 đỏ, mỗi chiều một bài** · khôi phục (đo: 1 và 0) → 0 đỏ
- [x] T24.19: `PortalSettingsReadTest` 4 bài — mọi khoá `settings` seed phải có nơi đọc | Date: 27/08/2026 | Note: quy tắc 15 ở dạng thi hành được. `SiteLayoutTest.khongCoCongTacWidgetThuyVan` chỉ khẳng định được *vắng mặt*; vế khó — *khoá có mặt thì phải có người đọc* — buộc phải nhìn sang mã cổng. ⭐ Kiểm chứng ngược: thêm khoá `site.khong.ai.doc` (đo: grep = 1) → 1 đỏ nêu đích danh khoá · khôi phục (đo: grep = 0, 7 khoá seed) → 0 đỏ
- [x] T24.20: Gỡ hai công tắc không ai đọc: `site.home.blocks` · `site.slider.effect` | Date: 27/08/2026 | Note: `site.home.blocks` liệt kê `SLIDER·FEATURED·NEWS·NOTICE·THUY_VAN` — từ vựng có TRƯỚC cây nội dung §3, nay `FEATURED` bị CR-10 thay bằng slider và `NOTICE` bị CR-01 bỏ khỏi cây. `site.slider.effect` chưa từng có nơi đọc, kể cả trước đợt này. Bỏ luôn nơi đọc cũ ở `tools/seeder/seed-portal-data.ts` (nợ T11.38 — hai đường seed cùng tồn tại, đường cũ sẽ dựng lại đúng thứ migration vừa xoá)
- [x] T24.21: Sửa hai bộ canh cũ đỏ vì **canh hình dạng thay vì canh bất biến** | Date: 27/08/2026 | Note: `siteContactConfig.test.ts` đóng đinh "SiteFooter phải chứa `company.email`" → đọc CR-40 (bỏ email khỏi chân trang) thành "cổng lại ghi cứng liên hệ", đúng ngược sự thật. Nay canh *mọi nơi công bố* + thêm **vế ngược**: chân trang KHÔNG được đọc lại hai khoá ấy. `SiteLayoutTest.pathCuaMenuSeedDung` khẳng định "ba mục con của Giới thiệu đều trỏ tới trang tĩnh" → nay canh bất biến (cha đứng trước con · mọi mục có đích giải được đúng `linkType` · `NONE` phải có con), còn hình dạng thì ghim riêng bằng danh sách 7 mục §3

- [x] T24.22: Bài kiểm cho hai service công khai — **lộ ra một lỗi sắp xếp tiếng Việt của chính tôi** | Date: 27/08/2026 | Note: hai service đứng sau `/api/v1/public/**` ban đầu **không có bài kiểm nào**, và cổng bao phủ `operations` tụt 0.70 → **0.69** làm `make ci-local` đỏ ở bước [8]. ⛔ Không nới ngưỡng — viết thật: `PublicConstructionCatalogServiceTest` 9 bài · `PublicOrgDirectoryServiceTest` 7 bài. ⭐ Bài `locVongDoi` bắt được `String.CASE_INSENSITIVE_ORDER` xếp **"Ngừng mùa vụ" TRƯỚC "Đang hoạt động"** — nó so đơn vị mã UTF-16 nên `Đ` (U+0110) rơi sau `N` (U+004E). Đúng bộ bốn tên T11.3-b dùng để đo collation cluster: `Anh < Dung < Em < Đăng` thay vì `Anh < Dung < Đăng < Em`. Vá bằng `Collator` locale `vi-VN`, kèm **bài kiểm chứng ngược khẳng định bộ so sánh cũ THẬT SỰ xếp sai**. ⚠ Danh sách trên cổng không đi qua `ORDER BY` của Postgres — nó sắp trong Java — nên collation ICU của CSDL (T11.3-b) không đỡ được chỗ này
- [x] T24.23: Bề mặt DTO công khai canh ở tầng **cấu trúc**, không canh giá trị | Date: 27/08/2026 | Note: `dtoKhongLoKhoaNoiBo` đọc `getRecordComponents()` và khẳng định `OrgChartNode`/`SubsidiaryRow` **không có** `path` · `publicId` · `headUserId` · `deputyUserId` · `id`. Một giá trị `null` hôm nay không ngăn ai điền nó vào ngày mai; một trường không tồn tại thì không điền được (cùng lý lẽ `ApiSurfaceRuleTest`)

- [x] T24.24: ⚠⚠ **Chạy thật trên stack đầy đủ — bắt được lỗi mà 906 bài kiểm hai phía đều không thấy** | Date: 27/08/2026 | Note: `make dev-docker`, 5/5 migration `success`, rồi bấm qua **17 đường dẫn menu** → 17×200, 0 lỗi 404. Nhưng trang **Tiến độ sản xuất** liệt kê *"Lịch vận hành cống & trạm bơm"* và *"Thông báo xả nước đệm"* làm các **Năm**. ⛔ Không lỗi nào báo ra — hai danh mục đều có thật, đều đang hiện, trang vẫn dựng bình thường; chỉ nội dung là vô nghĩa. **HAI nguyên nhân độc lập, mỗi cái tự nó đủ để sinh lỗi**: (1) backend lọc `isVisible()` từng dòng nên ẩn `thong-bao` (CR-01) để lại HAI con của nó mồ côi-mà-vẫn-hiện; (2) giao diện suy quan hệ cha–con từ **vị trí trong mảng phẳng**, mà danh sách sắp theo `path` DẠNG CHUỖI nên `'/12/' < '/2/'` — hai đứa con rơi đúng sau `tien-do-san-xuat`. Vá cả hai: `PublicPortalService` loại cả nhánh dưới danh mục ẩn (áp cho cả `articles(slug)` để một nhánh đã rút khỏi điều hướng không mở được bằng địa chỉ trực tiếp) · API thêm `parentSlug`, giao diện đối chiếu trường đó. 3 bài kiểm mới; **kiểm chứng ngược có số đo**: gỡ vá (`grep -c noneMatch` = 0) → 1 đỏ · khôi phục (= 1) → 0 đỏ
- [x] T24.25: `categories.visible` là cột **không endpoint nào ghi** — đóng lỗ | Date: 27/08/2026 | Note: lộ ra khi viết bài kiểm cho T24.24. Cột có từ `V202608191016`, DTO quản trị **trả nó ra** nhưng không đường ghi nào tồn tại — quản trị viên thấy trạng thái Hiện/Ẩn mà không đổi được (quy tắc 15, chiều ghi). Thành chuyện gấp vì migration CR-01 chọn **ẩn** `thong-bao` với lý do *"ẩn là thao tác quay lui được bằng một cú bấm"* — lý do ấy chỉ đúng khi cú bấm đó tồn tại. Thêm `CategoryService.setVisible` + `PUT /api/v1/cms/categories/{publicId}/visibility`. **Nợ nút bấm trên màn hình quản trị đã trả ở T25.9** (28/8)
- [x] T24.26: Sắp tên tiếng Việt trong Java — `Collator` `vi-VN` | Date: 27/08/2026 | Note: gộp từ lượt vá cùng ngày; xem T24.22

### Chưa làm — 9 mã CR còn lại

- [ ] T24.27: **CR-08 · CR-14 · CR-38** — đăng nhập trên cổng công khai và phân quyền ở tầng route/API | Note: **Đợt 3**, QuanTran chốt cơ chế 27/8: dựng auth ngay trên cổng, `public-web` có form riêng gọi `/api/v1/auth/login` qua proxy cùng origin (đã có sẵn, cookie đi qua được), thêm vai trò "Người xem cổng" không bắt 2FA (`totp.isRequiredFor` đã theo vai trò). ⛔ Endpoint chi tiết **không** mang `@PublicEndpoint`, và phải có bài kiểm gọi thẳng API khi chưa đăng nhập → 401/403. ⚠ Đợt này cố ý **KHÔNG** dựng nút Đăng nhập dẫn tới hư không, và **KHÔNG** dựng sẵn bảng tuần/tháng rồi ẩn bằng CSS — §2 cấm đích danh, và ẩn ở giao diện tạo *ảo giác đã phân quyền*. Chỗ ấy hiện là `KhoaDangNhap` nói thẳng
- [ ] T24.28: **CR-13 · CR-33** — đấu nối số liệu mực nước, lượng mưa | Note: **trả lời OI-01: CHƯA có API.** `hydro/` mới chỉ có khai báo gói + 1 service rỗng — MOD-03 chưa dựng. Ba giới hạn của nguồn đã đo (12/8): **không có API lượng mưa** (chỉ `getmn.aspx`) · **không có API lịch sử** · phủ 19 điểm đo, ít hơn biểu giấy. ⇒ cột "lượng mưa" của §5.2 **không tự động hoá được** bằng nguồn hiện tại, và bảng tuần/tháng của CR-14 chỉ có dữ liệu **kể từ ngày poller chạy lần đầu**. Hai điều này Công ty phải biết trước khi nghiệm thu. Khung khối đã dựng đủ theo §7 (T24.7)
- [ ] T24.29: **CR-15 · CR-34** — đấu nối số liệu vận hành trạm bơm | Note: **trả lời OI-02: CHƯA có API.** `construction_operation_status` đã có từ WS-19 nhưng phục vụ CN-02.11 (nhập tay, mỗi bản ghi một mã trạng thái + MỘT giá trị tham số), còn §5.3 đòi bốn trường cùng lúc theo ngày truy cập. Thiếu ba thứ: API nguồn · dữ liệu công trình (G8) · endpoint công khai cho nhật ký vận hành — bảng ấy thuộc phạm vi đơn vị nên mở ra công khai là một quyết định về phạm vi công bố, phải kèm bài kiểm cố tình hỏi dữ liệu Xí nghiệp khác. Khung khối đã dựng đủ (T24.7)
- [ ] T24.30: **CR-29** — bản đồ hệ thống PDF + KMZ | Note: vế PDF (1/50.000 và 1/75.000) thiếu **tệp**, Công ty chưa gửi. Vế KMZ thiếu **tầng đăng nhập** (T24.22) và **OI-07** còn hỏi cho tải về hay nhúng viewer. Trang Danh mục công trình đã có mục "Bản đồ hệ thống" nói rõ trạng thái, không để một nút chết
- [ ] T24.31: **CR-19 · CR-25 · CR-26 · CR-28 · CR-44** — nhập liệu, không phải mã | Note: bốn endpoint và bốn trang đã chạy; bảng rỗng vì `org_units` cố ý **không seed** (dữ liệu chịu tải — phân quyền tầng 3 neo vào id của nó) và danh mục công trình thuộc **G8**. ⚠ **OI-05** chặn: Bố cục ghi **7** Xí nghiệp, bộ dữ liệu Danh mục công trình có **8** (thêm XNTL Nhật Tựu). CR-44 (địa điểm cấp xã mới) là ràng buộc nhập liệu — cổng hiện nguyên văn `constructions.address`, cố ý không "chuẩn hoá" hộ theo một bảng ánh xạ không ai duyệt
- [ ] T24.32: **CR-20** — ảnh và video hoạt động thật | Note: phần "bỏ ảnh stock không liên quan" **đã xong từ 25/8** (§10.54) — khối nay là `EmptyBlock`. Còn lại thuần nội dung: Công ty gửi ảnh/video. Cũng cần **endpoint công khai cho thư viện ảnh công trình** (nợ T11.30, vế còn lại)
- [ ] T24.33: **Form liên hệ** (§3, ngoài 43 mã CR) | Note: §3 mô tả trang Liên hệ gồm cả form; CR-22 chỉ đòi bản đồ. Form **không dựng được ở tầng giao diện**: cần bảng lưu, endpoint nhận, chống spam, và màn hình cho người xử lý — quyền `cms:contact:manage` đã có trong RBAC từ `V202608131007` nhưng **chưa có gì đứng sau**. Một form gửi đi mà không ai nhận tệ hơn không có form
- [ ] T24.34: Trả lời Công ty 10 mục **OI-01 → OI-10** của §9 | Note: OI-01/02/07 thuộc phần kỹ thuật, tài liệu đề nghị trả lời **ngay trong tuần**. Câu trả lời đo được đã có ở T24.28/T24.29/T24.30; còn lại chờ Công ty. **OI-08** (giữ/đổi/bỏ "Tra cứu văn bản" và "Gửi phản ánh kiến nghị" ở thanh trên cùng) — đợt này **giữ nguyên**, bỏ trước là tự quyết thay khách
- [ ] T24.35: Đề bạt lên staging rồi đối chiếu bằng **đường người dùng thật đi** | Note: ⛔ "xanh ở máy" không phải bằng chứng. Phải bấm qua đủ 8 mục cấp 1 + 12 mục cấp 2 trên site thật, xác nhận **0 mục trả 404**, và đo lại `prerender-manifest` trên image đã dựng ở CI (không phải bản build ở máy — `.env.local` chỉ tồn tại ở máy)

## WS-25 — Đầu trang thân thiện + "mọi thứ hiển thị phải cấu hình được từ admin" (28/08/2026)

> Hai việc QuanTran giao 28/8, và cả hai đều **không phải yêu cầu mới** — chúng là hai dòng đã có
> sẵn trong §10 văn bản nghiệm thu: *"Giao diện hiển thị đúng trên máy tính, máy tính bảng và điện
> thoại (Responsive)"* và §2 *"Mọi tham số vận hành phải cấu hình được, không gán cứng trong mã
> nguồn"*. Lượt rà này biến chúng thành thứ đo được.
>
> ⚠⚠ **Việc 2 tìm ra SÁU cột / khoá / tham số bày ra mà không ai ghi hoặc không ai đọc**, trong đó
> **bốn** do chính đợt WS-24 vừa tạo ra một ngày trước. Đây là hình dạng lỗi đặc trưng của dự án ở
> chiều ngược: WS-24 đếm "đường đọc đã dựng xong" và tick, mà nửa còn lại của mỗi cặp đọc–ghi
> không có ai đếm.

### Nhóm A — Đầu trang và điều hướng (việc 1)

- [x] T25.1: ⚠⚠ **Thanh điều hướng TRÀN khung trên MỌI màn hình** — đo trước khi sửa | Date: 28/08/2026 | Note: cây §3 có 8 mục cấp 1, nhãn dài ("Hoạt động Đảng, đoàn thể" 24 ký tự), vẽ bằng `text-[13px] font-bold uppercase tracking-wider` + `px-3.5`. **Đo được: menu 1344px + nút Tìm kiếm 110px = 1454px trong khung 1240−48 = 1192px → tràn 22%**. `flex-wrap` nên nó không vỡ bố cục mà **xuống dòng** — thanh cao gấp đôi ở mọi bề rộng, kể cả desktop rộng nhất, và trên điện thoại thành một mảng chữ hoa chiếm gần hết màn hình đầu. Không lỗi nào báo ra, không bài kiểm nào đụng tới
- [x] T25.2: Dựng lại `PortalNav` — ngăn kéo di động, menu con mở bằng **chạm**, trạng thái trang hiện tại | Date: 28/08/2026 | Note: ba đổi thay, mỗi cái đổi lấy một thứ cụ thể: (1) bỏ `uppercase`+`tracking-wider` ở cấp 1 → **1344 → ~1082px**, và chữ hoa tiếng Việt chồng dấu vốn khó đọc hơn chữ thường; (2) dưới `lg` chuyển sang ngăn kéo — nhãn dài không còn cạnh tranh bề ngang; (3) ⛔ mục `NONE` ("Giới thiệu", "Quản lý, vận hành") bản cũ là `<button>` **không gắn hành vi nào** — trên máy tính bảng chạm vào là chạm một nút không phản hồi, và bốn mục con không có cách nào mở. Máy tính bảng nằm đúng trong câu §10. ⛔ Hệ màu / kiểu khối / chiều cao **giữ nguyên** theo §2
- [x] T25.3: Đóng ngăn kéo bằng **trình xử lý sự kiện**, không bằng `useEffect` | Date: 28/08/2026 | Note: ESLint `react-hooks/set-state-in-effect` chặn (cùng luật đã bắt `RealtimeFrame` đợt trước) — nhưng lý do đúng là lý do thứ hai: bám theo `pathname` thì bấm một liên kết trỏ về **chính trang đang xem** sẽ không đóng ngăn kéo, vì đường dẫn không đổi nên effect không chạy. Người dùng bấm, không thấy gì, bấm tiếp
- [x] T25.4: **12 mã màu ghi cứng** ở `SiteHeader`/`SiteFooter` → `design-tokens` | Date: 28/08/2026 | Note: `ui-styles.md` §2.1 cấm khai màu tại chỗ **từ WS-15**; đo 28/8 vẫn còn 12 chỗ, gồm **7 sắc navy khác nhau** cho cái lẽ ra là một dải. Tệ hơn con số: §2.3 của chính tài liệu ấy ghi gradient navbar là `#0c366e → #165bb6`, còn thứ chạy thật là `#061b37` — **một màu chưa từng chạy**, và đọc bên nào cũng thấy hợp lý (quy tắc 14). Gộp 7 → 5 bậc `portalChrome.navy900…navy500`, giá trị lấy từ **màn hình** chứ không từ tài liệu (§2 chốt "hệ màu GIỮ NGUYÊN"); lệch tối đa 8/255 ở một kênh. Ba màu nhận diện mạng xã hội tách riêng `externalBrandColors`. `ui-styles.md` §2.3 sửa theo thực tế

### Nhóm B — Mọi thứ hiển thị phải cấu hình được từ admin (việc 2)

> ⛔ Sáu mục dưới đây có chung một hình dạng: **một nửa của cặp đọc–ghi tồn tại, nửa kia không**.
> Triệu chứng luôn giống nhau — màn hình quản trị báo *lưu thành công*, cổng không đổi gì, và
> không có lỗi nào để đi tìm.

- [x] T25.5: ⚠⚠ `org_unit_leaders` là **bảng chỉ có đường đọc** — dựng CRUD + màn hình | Date: 28/08/2026 | Note: bảng dựng **hôm trước** (`V202608271034`, T24.12) kèm repository và `PublicOrgDirectoryService` đọc ra cổng. Đo 28/8: **0 controller, 0 màn hình** ghi vào nó. Trang `/gioi-thieu/lanh-dao` (CR-25) và cột "Giám đốc XN" (CR-26) đọc một bảng **không ai điền được** — hai trang đã dựng, đã có bài kiểm, đã lên staging, và sẽ rỗng vĩnh viễn. Thêm `OrgUnitLeaderService` + `/api/v1/org-units/{id}/leaders` (5 thao tác) + `OrgUnitLeadersPanel`. ⛔ Dùng lại `adm:org-unit:manage`, không đẻ quyền thứ 89: danh bạ là một phần hồ sơ đơn vị, không vai trò nào cần vế này mà không cần vế kia
- [x] T25.6: `org_units.address/phone/email` — **3 cột đọc-được-mà-không-ghi-được** | Date: 28/08/2026 | Note: ba cột có từ `V202608131004`, `/public/org-units/subsidiaries` **hiển thị chúng** ở bảng 6 cột CR-26 và ở thẻ trang chủ CR-19 — mà `setAddress`/`setPhone`/`setEmail` **không có lời gọi nào** trong toàn kho ngoài chính lớp entity. Biểu mẫu quản trị chỉ có mã, tên, tên tắt, loại, đơn vị cha. Thêm vào `CreateRequest`/`UpdateRequest`/`OrgUnitNode` + ô nhập dùng chung cho cả hai biểu mẫu
- [x] T25.7: `CreateRequest.shortName` **bị bỏ rơi lặng lẽ** | Date: 28/08/2026 | Note: DTO khai nó, `@Size(max = 100)` validate nó, biểu mẫu có ô "Tên viết tắt" — nhưng `create()` **không nhận tham số ấy**. Người dùng gõ, bấm Lưu, màn hình báo *tạo thành công*, giá trị biến mất. Chỉ `update()` ghi được, mà `update()` lại không màn hình nào gọi (T25.8) → tên tắt **chưa bao giờ lưu được**. Triệu chứng ở cổng: thẻ đơn vị hiện `shortName || name` nên nó "chỉ là không bao giờ hiện tên tắt", không giống một lỗi ghi dữ liệu
- [x] T25.8: `PUT /org-units/{publicId}` — **endpoint không màn hình nào gọi** | Date: 28/08/2026 | Note: có từ WS-6; màn hình Sơ đồ tổ chức chỉ có Tạo, Chuyển cha, Xoá. Hệ quả: **tên, tên viết tắt và loại đơn vị chưa bao giờ sửa được sau khi tạo** — gõ sai một chữ trong tên Xí nghiệp thì cách chữa duy nhất là xoá rồi tạo lại, mà xoá vướng ràng buộc "còn người dùng thuộc đơn vị". Dựng `EditOrgUnitModal`, `initialValues` nạp **đủ sáu trường** (nạp thiếu một trường thì mỗi lượt Lưu ghi đè giá trị đang có bằng rỗng). Mã đơn vị cố ý **không** sửa được — nó là khoá nghiệp vụ đã in trên văn bản và dùng tra cứu ở tệp nhập công trình
- [x] T25.9: `categories.visible` — nút Ẩn/Hiện trên màn hình quản trị (**trả nợ T24.25**) | Date: 28/08/2026 | Note: endpoint dựng 27/8, nút bấm còn nợ. Migration CR-01 chọn **ẩn** danh mục "Thông báo" với lý do *"ẩn là thao tác quay lui được bằng một cú bấm"* — lý do ấy chỉ đúng khi cú bấm đó tồn tại. Thêm `Switch` + thẻ "Đang ẩn" hiện cho **mọi người xem màn hình**, không chỉ người có quyền sửa: một danh mục vắng mặt trên cổng mà màn hình quản trị không nói gì thì người ta đi tìm lỗi ở chỗ khác
- [x] T25.10: Hai cột tài liệu công bố của công trình — setter chỉ có **một** lời gọi, và nó nằm trong một bài kiểm | Date: 28/08/2026 | Note: `V202608271035` (T24.14, hôm trước) thêm 2 cột, `PublicConstructionCatalogService` đọc chúng dựng 2 liên kết CR-28 — nhưng `SaveRequest` không mang chúng và biểu mẫu không có ô nào. §10 đòi *"các link Quyết định và Google Map hoạt động"*; hai liên kết ấy **không bao giờ có gì để trỏ tới**. Nối `SaveRequest → ConstructionForm → apDung() → ConstructionDetail` + ô **chọn từ tệp đã tải lên** (không phải ô gõ URL: giá trị là khoá ngoại `attachments.public_id`, chọn từ danh sách thì không trỏ được vào tệp không tồn tại, và gỡ tệp là cột tự rỗng). ⛔ Tệp nhập Excel cố ý **không** nhận 2 cột này
- [x] T25.11: `EXTERNAL_PORTALS` — 4 liên kết cơ quan cấp trên viết cứng → vị trí menu `LIEN_KET` | Date: 28/08/2026 | Note: CR-21 yêu cầu Công ty *"rà soát lại tên và đường link chính thức"* — rà xong thì **không có cách nào sửa**: đổi một cái tên là sửa mã nguồn, dựng lại image, đề bạt qua ba chặng. Cùng nợ đã trả cho địa chỉ hệ thống văn bản (T11.28) và cho liên hệ Công ty (§10.54). ⭐ Là **vị trí menu thứ ba** chứ không phải khoá `settings`: đây là danh sách có thứ tự, mỗi phần tử có nhãn + đích + cờ mở tab — đúng hình dạng `menu_items`, và javadoc `MenuPosition` đã chốt điều kiện *"phải có chỗ trên giao diện cổng để hiển thị nó"*, chỗ ấy tồn tại từ WS-16. Migration **chuyển chỗ** dữ liệu đang chạy, không tạo mới — không chuyển thì dải liên kết biến mất khỏi trang chủ
- [x] T25.12: **4 khoá `settings` chưa từng có nơi đọc** → gỡ | Date: 28/08/2026 | Note: `site.analytics.ga-tracking-id` · `site.analytics.gtm-container-id` · `site.color.primary` · `site.color.secondary` — đo grep toàn kho: **0 nơi đọc**, bày trên màn hình Cấu hình hệ thống từ 19/8. ⛔ Gỡ chứ không viết mã đọc: GA/GTM đòi tải mã từ `googletagmanager.com` mà CSP chốt `script-src 'self'` → đọc khoá mà không nới CSP thì trình duyệt chặn **không báo lỗi nào**, lại thêm một công tắc bấm-mà-không-chạy; nới CSP là quyết định về quyền riêng tư người dân tra cứu (cùng lý lẽ đã tự host Noto Sans) → thuộc **G13**. `site.color.*` cạnh tranh với `design-tokens` cho cùng một giá trị (quy tắc 14), và giá trị seed còn **sai**: `#1677ff` là xanh mặc định AntD, màu thương hiệu thật là `#165bb6`
- [x] T25.13: `HomeMediaGallery` — ba props mà **không nơi gọi nào truyền** | Date: 28/08/2026 | Note: component nhận `videoId`/`videoTitle`/`photos` và dựng khung nhúng YouTube tử tế; trang chủ gọi `<HomeMediaGallery />` **trần**. Nên khối luôn hiện hai ô rỗng, ở dev, ở staging, ở mọi nơi — quy tắc 15 ở dạng React. Khác với mực nước (chưa có nguồn, OI-01): ở đây **mã hiển thị đã chạy được**, thứ thiếu là một ô để Công ty nhập. Thêm `site.home.video-id` / `site.home.video-title` (`V202608281038`). Vế **ảnh** tách riêng thành T25.24 (cần endpoint công khai cho thư viện ảnh công trình, nợ T11.30) — một quyết định về phạm vi công bố, không phải một ô cấu hình
- [x] T25.14: Chân trang — ba chỗ nhãn và đích nói hai chuyện khác nhau | Date: 28/08/2026 | Note: (1) *"Ghi rõ nguồn «Cổng thông tin Thủy lợi Sông Nhuệ»"* ghi cứng tên, ngay dưới một dòng `{siteName}` đọc từ `settings` — đổi tên trên màn hình cấu hình chỉ đổi được một nửa (quy tắc 14); (2) **"Sơ đồ cổng"** `href="/"` — nhãn hứa một trang sơ đồ, đích là trang chủ, không có trang sơ đồ nào tồn tại; (3) "Phiên bản 1.0" viết cứng, không nơi nào đọc, khẳng định một điều không gì bảo đảm còn đúng

### Bộ canh mới — mỗi cơ chế có bài chứng minh nó bắt được vi phạm (luật 1)

- [x] T25.15: `noHardcodedColors.test.ts` — 5 bài, soi cả `.ts` lẫn `.tsx` | Date: 28/08/2026 | Note: canh **nguồn** của một mã màu, không canh màu hiện ra: hai bên đang hiện đúng cùng màu, thứ hỏng là *nó khai ở đâu*. Chú thích được phép nhắc tới mã màu (dùng chung `boChuThich` với bộ canh dữ liệu bịa) — cấm cả trong chú thích thì người sau xoá ghi chú chứ không xoá vi phạm. ⚠ Tách `boChuThich` ra module riêng: bản đầu `import` thẳng từ tệp test khác nên Vitest nạp nó như một suite và **23 bài của nó chạy lại lần hai** (đo được: báo 28 bài cho 5 bài đã viết). ⭐ Kiểm chứng ngược có số đo: 0 hex → 5 xanh · đặt lại 1 hex (đo `grep -c` = 1) → **1 đỏ nêu đích danh cả hai mã** · khôi phục (= 0) → 5 xanh. ⚠ Phạm vi **chỉ `public-web`**, ghi rõ trong javadoc — `admin-app` còn 25 hex ở 12 tệp (T25.19)
- [x] T25.16: `PortalSettingsReadTest` **mở phạm vi từ 1 tệp → cả thư mục migration** — và việc mở phạm vi lộ ra 4 khoá chết | Date: 28/08/2026 | Note: bản đầu (T24.19) chỉ soi migration của đợt 27/8, nên **mọi khoá seed trước đó đều đi lọt**. Đúng hình dạng `NginxSecurityHeadersTest` chỉ soi `admin-app` trong khi cổng công khai không có CSP (§10.61): *một bộ canh có phạm vi hẹp hơn nơi nó phải chặn, và cái xanh của nó đọc như một lời bảo đảm*. ⚠⚠ **Lượt kiểm chứng ngược đầu tiên THẤT BẠI và điều đó lộ ra một lỗ thứ hai**: đặt `--` trước câu `DELETE` rồi chờ bộ canh đỏ — nó không đỏ, vì `CAU_XOA` là regex và regex không biết SQL có chú thích, nên một câu `DELETE` đã bị vô hiệu hoá vẫn được tính là đã chạy. Thêm `boChuThichSql()`. ⭐ Kiểm chứng ngược **đúng cách** (xoá hẳn câu lệnh, không chú thích nó): 1 câu DELETE → 5 xanh · 0 câu → **2 đỏ nêu đủ 4 khoá** · khôi phục → 5 xanh
- [x] T25.17: `PortalTaxonomyTest.viTriMenuKhongLech` — **ba nơi nhớ cùng một danh sách** | Date: 28/08/2026 | Note: quy tắc 14. Thêm `LIEN_KET` đòi sửa enum Java + kiểu TypeScript + ràng buộc `ck_menu_items_position`; quên chỗ nào thì triệu chứng khác nhau và **không chỗ nào chỉ về nguyên nhân** (quên enum → Jackson ném lỗi ở một endpoint không liên quan · quên kiểu FE → ô chọn thiếu một lựa chọn, không lỗi nào · quên CHECK → lưu thất bại với lỗi ràng buộc thô sau khi người dùng nhập xong). ⚠ Đọc ràng buộc từ migration **mới nhất chạm tới nó**, không từ `V202608191019` (đã `DROP`/`ADD` lại). ⚠⚠ Mẫu bắt enum bản đầu đòi `[,;]` nên **bỏ sót hằng cuối** (hằng cuối của enum Java không có dấu phẩy) — và bài kiểm chứng ngược bản đầu khẳng định `("HEADER","FOOTER")`, tức **chép lại hành vi sai thay vì bắt nó**. Chỗ lộ ra là khẳng định số lượng tối thiểu ở bài chính (2 < 3). ⭐ Kiểm chứng ngược: gỡ `LIEN_KET` khỏi kiểu FE (đo `grep -c` = 0) → 1 đỏ *"kiểu FE lệch enum backend"* · khôi phục (= 1) → 7 xanh
- [x] T25.18: `OrgUnitLeaderHttpTest` — 8 bài **đi qua HTTP** | Date: 28/08/2026 | Note: luật 5. Thứ hỏng ở đây **không phải logic nghiệp vụ** mà là *một trường không có mặt trong DTO* — gọi service với đủ tham số thì bài kiểm luôn xanh kể cả khi DTO bỏ quên ba trong số đó; chỉ lượt gọi qua Jackson mới chứng minh trường ấy đi hết đường từ trình duyệt xuống CSDL rồi quay lại. Khẳng định **ở CSDL**, không chỉ ở thân trả về (một DTO có thể vọng lại đúng giá trị vừa nhận mà chưa ghi xuống đâu cả — `shortName` hỏng đúng kiểu ấy). ⚠ Phải dùng vai trò kiểm thử tạm: đo được **`adm:org-unit:manage` chỉ ở SUPER_ADMIN và ADMIN, cả hai đều bắt buộc 2FA** → không vai trò nào quản lý được cơ cấu tổ chức mà không qua 2FA; đó là thiết kế đúng (`org_units` là biên giới phân quyền tầng 3), nên bài kiểm khẳng định **cổng quyền** chứ không khẳng định ma trận vai trò. ⭐ Kiểm chứng ngược: gỡ `unit.setShortName(...)` (đo `grep -c` 2 → 1) → **1 đỏ đúng bài, đúng thông điệp** · khôi phục (= 2) → 8 xanh

### Chạy thật trên stack đầy đủ — và nó lại tìm ra thứ bài kiểm không thấy

- [x] T25.20: `make dev-docker` + bấm qua đường người dùng thật đi | Date: 28/08/2026 | Note: 3/3 migration mới `success` · **17/17 đường dẫn menu → 200** · 4 khoá chết = **0 dòng** trong `settings` · 2 khoá video có mặt, giá trị rỗng · menu `LIEN_KET` trả đủ 4 dòng, `CHECK` đã nới · đầu trang có nút ngăn kéo, `aria-expanded`, class `from-chrome-navy800`, **0 mã hex**, **0 chỗ `uppercase` ở mục cấp 1** · 4 liên kết cơ quan cấp trên giữ nguyên `target="_blank" rel="noopener noreferrer"`. ⭐ Chèn dữ liệu tổ chức tạm rồi đo đường đọc: `/public/org-units/leaders` lọc đúng dòng `active=false`, trường `phone` **vắng mặt** khi NULL (không phải `""`); trang Lãnh đạo, trang Xí nghiệp và thẻ trang chủ hiện đủ 6 cột. Dọn sạch dữ liệu tạm sau khi đo
- [x] T25.21: ⚠ **ISR giữ trang cũ — và `docker restart` không xoá được nó** | Date: 28/08/2026 | Note: lộ ra ở T25.20: API trả đúng dữ liệu mà trang vẫn rỗng. Không phải lỗi mã — `revalidate = 300` cộng bộ đệm dữ liệu của Next nằm trên **lớp ghi của container**, mà `docker restart` giữ nguyên lớp ấy (cùng họ §10.53: `up -d` in `Running` rồi giữ container cũ). Phải `up -d --force-recreate` mới sạch. Cũng chính chỗ này lộ ra T25.22

### Nợ mới ghi nhận — đo được, chưa làm, không giấu

- [x] T25.27: Ảnh + logo + video phóng sự do Công ty gửi | Date: 27/08/2026 | Note: **nội dung THẬT Công ty gửi**, không phải bộ dữ liệu cho đẹp demo. **Ảnh**: 30 tệp, phân nhóm theo đúng quy ước Công ty tự đặt trong tên tệp — `Ảnh to.` (5, gốc 2560–4032px) → `banners` slider · `AN1/2/3.` (25) → thư mục media. ⚠ Nén trước khi vào kho: gốc **49 MB**, ảnh lớn nhất **8,8 MB** (một mình nó đã ngược NFR-02, và 49 MB vào lịch sử git thì không gỡ ra được) → hero 1600px q55 (~271 KB), thư viện 900px q55 (~124 KB) = **4,35 MB**, đúng cỡ quy ước bộ seed cũ (83–111 KB). `public_id` là UUIDv5 sinh từ tên tệp nên `ON CONFLICT DO NOTHING` idempotent thật. Tiêu đề lấy **nguyên văn** từ tên tệp — không câu nào do phía phát triển nghĩ ra. **Logo**: thay tại chỗ `logo-song-nhue.png` (giữ tên nên 3 nơi tham chiếu không phải sửa) — **636 KB → 19 KB**. Đo trước khi thay: 16,5% điểm ảnh đặc, **100% gần trắng**, cùng loại logo cũ (thiết kế cho dải navy). **Video**: `Mb70qe84eqU`. ⚠ Tiêu đề lấy từ **oEmbed của YouTube**, không tự đặt: *Hà Nội đầu tư hơn 75.000 tỷ đồng hồi sinh sông Nhuệ*. Kênh **MÔI TRƯỜNG TV** — **không phải kênh Công ty**, nên ghi kèm nguồn (tiền lệ: 5 bài seed sao chép từ báo ngoài đều ghi `source`). `UPDATE … WHERE setting_value = ''` — không giẫm lên lựa chọn Công ty tự nhập ⚠ **Hai migration của lượt này đánh số SAI, đã đổi tên ở T11.52**: `202608272320/2321` → `V202608281039` (cms, tạo khoá) + `V202608281040` (seed, ghi giá trị). Số cũ dùng giờ-phút nên vừa rơi dưới bản staging đã áp (CD đỏ) vừa xếp seed TRƯỚC tệp tạo ra khoá nó ghi (hỏng câm)
- [x] T25.28: ⚠ **`SeedGateTest` hẹp hơn thứ nó phải chặn** — đọc MỘT tệp SQL ghi cứng | Date: 27/08/2026 | Note: lộ ra ngay khi có tệp seed **thứ hai**: hai bài canh byte đọc `SEED_SQL` ghi cứng rồi khẳng định `hasSize(4)`, nên 30 tệp byte mới bị báo **mồ côi** trong khi hàng của chúng nằm ở tệp seed kia. Đúng hình dạng luật 28, lần thứ tư trong dự án. Vá: `moiSeedSql()` nối **mọi** `.sql` dưới `db/seed/`; ngưỡng đổi từ `hasSize(4)` sang **sàn** `>= 4`; thêm chiều ngược lại (hàng trỏ tới byte KHÔNG có trên đĩa → đỏ). Kiểm chứng ngược có số đếm: 34 tệp → exit 0 · thêm 1 tệp mồ côi → **exit 1, nêu đích danh** · 34 tệp → exit 0
- [x] T25.25: **Cổng ép ALL CAPS ở 31 chỗ trong khi thanh điều hướng đã bỏ** | Date: 27/08/2026 | Note: WS-25 bỏ `uppercase` khỏi `PortalNav` với lý do ghi rõ — *chữ hoa tiếng Việt chồng dấu làm dấu thanh dính vào nhau* — nhưng chỉ sửa MỘT component. Đo 27/8: **31 chỗ trong 21 tệp** còn ép hoa, nên nav nói một kiểu và toàn bộ thân trang nói kiểu ngược lại. ⭐ Chỗ đắt nhất: `SiteHeader` + `SiteFooter` vẽ `{siteName}` bằng `uppercase`, trong khi `site.name` trong `settings` là `Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ` — giao diện đang hiện `CÔNG TY TNHH MTV ĐẦU TƯ…`. **Không phải lựa chọn thẩm mỹ mới**: CR-42 đã chốt đúng luật ấy cho địa chỉ (*hiện nguyên văn giá trị trong `settings`; ép hoa là giao diện tự quyết định thay người nhập*), chỉ chưa áp cho tên Công ty. Vá: gỡ `uppercase` + `tracking-wider` ở 31 className (giãn chữ chỉ hợp chữ hoa); giữ nguyên đậm/cỡ/màu. `noForcedUppercase.test.ts` 3 bài; kiểm chứng ngược: trả lại 1 `uppercase` → `1 failed`, exit 1 → khôi phục → 110 xanh. ⚠ Giới hạn ghi trong chính bộ canh: chỉ soi `public-web`, **`admin-app` chưa rà** (cùng hình dạng T25.23)
- [x] T25.26: **Tiêu đề ghi cứng dùng Title Case kiểu Anh, sai quy ước tiếng Việt** | Date: 27/08/2026 | Note: lộ ra khi đối chiếu tiêu đề ghi cứng với nhãn menu **Công ty duyệt** (G14): menu dùng câu thường (`Quản lý, vận hành` · `Mực nước, lượng mưa`), còn component viết `Chuyên mục & Lĩnh vực Hoạt động` · `Truyền thông & Hình ảnh Hoạt động` · `Đơn vị Trực thuộc & Mạng lưới Liên kết` · `Hệ thống Văn bản Điều hành` · `Gửi Phản ánh & Kiến nghị` · `Tra cứu & Dịch vụ` · `Chỉ đường tới Trụ sở` · `Liên kết Cổng TTĐT`. Chữ hoa giữa câu không phải quy ước tiếng Việt, và trước lượt này `uppercase` che mất nên không ai thấy. Sửa **8** chuỗi về câu thường. ⛔ **KHÔNG đụng nhãn menu** — cây nội dung nhận qua §3 văn bản nghiệm thu; `Tin tức – Sự kiện` giữ nguyên vì nó khớp đúng nhãn ấy
- [ ] T25.22: **Sửa dữ liệu tổ chức / công trình KHÔNG xoá cache cổng** — cổng trễ tới 5 phút | Note: `PortalCache` (nơi xếp job `CMS_PORTAL_REVALIDATE`) nằm ở module `content`, còn `org_units` ở `core` và `constructions` ở `operations` — **không gọi qua ranh giới module được** (quy tắc 6). Hệ quả đo được: nhập một Xí nghiệp xong, cổng đổi theo sau **tối đa 5 phút**. Không phải mất dữ liệu, có tự lành, nhưng người nhập liệu không thấy đổi ngay sẽ tưởng lưu hỏng và nhập lại. Vá tạm đã làm: một dòng cảnh báo ngay trên bảng danh bạ. Vá thật cần một **SPI cache cổng** để `core`/`operations` gọi được — quyết định liên module, phải đi riêng
- [ ] T25.23: **25 mã màu ghi cứng ở `admin-app`**, 12 tệp | Note: cùng vi phạm `ui-styles.md` §2.1, `noHardcodedColors.test.ts` **cố ý chưa phủ** và javadoc của nó ghi rõ giới hạn ấy — im lặng về phạm vi là tái tạo đúng lỗi vừa sửa ở T25.16. Tệp: `AuthShell` · `OperationStatusCodesPage` · `LocationPickerMap` · `StepLocation` · `StepFinance` · `DashboardPage` · `StepTechnical` · `MediaBrowser` · `SiteConfigTab` · `VersionHistoryDrawer` · `RichTextEditor` · `ConstructionMap`. Là lượt sửa 25 chỗ ở bốn màn hình chưa đụng tới đợt này — phải đi riêng để lượt rà đọc được nó
- [x] T25.24: **Thư viện ảnh công trình chưa mở ra cổng** — vế còn lại của CR-20 và của nợ T11.30 | Note: `HomeMediaGallery.photos` nay có nơi truyền nhưng chưa có nguồn. Ảnh hiện trạng đang nằm trong hồ sơ từng công trình (`ops:document:*`), mở ra công khai là quyết định về **phạm vi công bố** — phải kèm bài kiểm cố tình hỏi ảnh của Xí nghiệp khác ✅ **ĐÓNG 27/8** — Công ty gửi 30 ảnh, tức quyết định về *phạm vi công bố* (thứ nợ này chờ) đã có. Dựng `GET /api/v1/public/photos` đọc thư mục mà **`site.home.photos-folder`** chỉ tới (khuôn giống `site.home.documents-category`); `PublicPortalService.photos()` dùng `AttachmentPort.refsOf` nên không đụng repository module khác (luật 6). Khoá rỗng / UUID hỏng / thư mục đã xoá → **mảng rỗng**, khối nói thẳng là chưa có — không bộ ảnh dự phòng nào (luật 16). ⚠ Bỏ trường `location` khỏi `PhotoItem`: ảnh Công ty gửi **không kèm nơi chụp**, bịa một địa điểm cho mỗi ảnh là đúng thứ luật 16 cấm


## DoD Phase 0

- [x] DOD0.1: Chạy native
- [x] DOD0.2: Chạy full Docker
- [x] DOD0.3: Fail-fast thiếu env
- [x] DOD0.4: Migration sạch từ DB rỗng
- [x] DOD0.5: Auth + 2FA
- [x] DOD0.6: Refresh reuse detection
- [x] DOD0.7: RBAC 3 tầng
- [x] DOD0.8: Deny by default
- [x] DOD0.9: Envelope + traceId
- [x] DOD0.10: Audit hash chain
- [~] DOD0.11: Attachment
- [x] DOD0.12: Async job
- [~] DOD0.13: Backup
- [ ] DOD0.14: Đo RTO thật
- [x] DOD0.15: Alert backup hỏng
- [~] DOD0.16: Key không nằm trong backup
- [x] DOD0.17: Restore UI
- [x] DOD0.18: ArchUnit
- [x] DOD0.19: CI đầy đủ
- [ ] DOD0.20: Deploy Staging
- [ ] DOD0.21: Rollback | Note: ⭐ **27/8 — đường quay lui CHẠY THẬT lần đầu** ở lượt CD hỏng vì checksum migration (run 33086135148): `::warning::Lượt triển khai hỏng — đang dựng lại bản trước đó` → `→ [quay-lui] 511 byte, khớp hai đầu` → bước `success`. ⛔ **VẪN KHÔNG TICK**: đó là một lượt **không có gì để quay lui**. `migrator` chạy `run --rm` TRƯỚC `up -d --force-recreate`, nên lượt deploy dừng trước khi chạm container nào — ba container vẫn nguyên bản 03:17, `Created` không đổi. Nó chứng minh **đường đi thông**, chưa chứng minh nó **dựng lại được một bản đã bị thay**. Muốn đóng: cần một lượt hỏng SAU bước `up -d` (ví dụ smoke test đỏ) rồi đo `Created` của container quay về mốc cũ ⭐ **Lần thứ hai 27/8** (run 33095696654, hỏng vì thứ tự migration — T11.52): y hệt, `[quay-lui] 511 byte, khớp hai đầu`, dịch vụ trả lời sau 10 giây. **Hai lượt, cùng một hình dạng, vẫn không đóng được** — cả hai đều dừng ở `migrator` nên vẫn không có gì bị thay. 📌 Đây không phải trùng hợp: `migrator` chạy trước `up -d` là **thiết kế**, nên mọi lỗi migration sẽ mãi dừng ở đó. Bằng chứng cho DOD0.21 chỉ đến từ một lỗi ở tầng ứng dụng (smoke test đỏ), không đến từ lỗi CSDL — nếu chờ nó xảy ra tự nhiên thì có thể chờ mãi

## DoD Phase 1

> Nghiệm thu 23/8/2026 — mỗi mục kèm **tên phép kiểm** đứng sau nó. Mục nào không có phép kiểm thì
> để trống, không tick.

- [x] DOD1.1: Ranh giới module chạy thật | Date: 23/08/2026 | Note: ModuleBoundaryTest + ModuleBoundarySelfCheckTest (4 bài chứng minh luật bắt được vi phạm)
- [x] DOD1.2: Phân quyền tầng 3 trên entity nghiệp vụ thật | Date: 23/08/2026 | Note: ScopeFilterEndToEndTest 7 · ConstructionScopeTest 8 · MaintenanceScopeTest 8 · OperationStatusHttpTest 8 (có bài IDOR qua HTTP)
- [x] DOD1.3: Vòng đời bài viết đầu-cuối | Date: 24/08/2026 | Note: ArticleLifecycleTest 14 bài. ⚠ Mục này tick từ 23/8 nhưng **một nhánh của vòng đời không dùng được**: trả bài về sửa tắc hoàn toàn (T22.16). Cả 14 bài đều xanh vì gửi JSON dựng tay, không đi qua `allowedActions` — vế backend ép buộc đúng, vế backend QUẢNG CÁO cho giao diện thì hỏng. Nay thêm `ArticleHttpTest.allowedActionsNoiRaBuocDoiLyDo` khẳng định cờ đi hết đường tuần tự hoá ra tới dây, và chốt cả vế ngược (APPROVE phải là false)
- [x] DOD1.4: Biên tập viên không tự xuất bản được | Date: 23/08/2026 | Note: ArticleLifecycleTest.bienTapVienKhongTuXuatBanDuoc — ràng buộc nằm ở workflow_transitions, không ở câu if
- [x] DOD1.5: ISR revalidate chạy thật | Date: 23/08/2026 | Note: PortalRevalidateClientTest 7 bài trên máy chủ HTTP thật. Bản đầu của bài canh HTTP/1.1 là XANH GIẢ — đã đo lại và đổi sang khẳng định không gửi header Upgrade/HTTP2-Settings. ⚠ Chứng minh phía phát ra gửi đúng, KHÔNG chứng minh Next dựng lại trang
- [x] DOD1.6: API công khai không lộ bài chưa xuất bản | Date: 23/08/2026 | Note: Phase1AcceptanceTest.draftArticlesNeverReachThePublicApi — trước đó KHÔNG có phép kiểm nào
- [x] DOD1.7: Đính kèm đầu-cuối qua HTTP | Date: 23/08/2026 | Note: Phase1AcceptanceTest — kiểm cả hai vế của cổng quét virus (PENDING → 409, CLEAN+READY → 200). Trước đó chỉ có bài gọi thẳng service
- [x] DOD1.8: Trạng thái công trình không sửa trực tiếp được | Date: 23/08/2026 | Note: ConstructionHttpTest — client gửi operationalStatus → OPS-3001
- [x] DOD1.9: Sự cố đổi trạng thái công trình | Date: 23/08/2026 | Note: MaintenanceLogHttpTest.incidentDrivesConstructionStatusBothWays — cả chiều bật lẫn chiều tắt
- [x] DOD1.10: Mọi đổi trạng thái đi qua Workflow engine | Date: 23/08/2026 | Note: SilentFailureRuleTest + SilentFailureRuleSelfCheckTest (applyState chỉ gọi được từ WorkflowEngine)
- [x] DOD1.11: Thêm mã tình hình vận hành mới không cần deploy | Date: 23/08/2026 | Note: Phase1AcceptanceTest — mã thêm lúc chạy dùng được ngay và lái được trạng thái dẫn xuất. Trước đó KHÔNG có phép kiểm nào
- [x] DOD1.12: Tiền và số đo là BigDecimal | Date: 23/08/2026 | Note: CodingRuleTest ở mức bytecode + bài canh danh sách ngoại lệ không phình
- [x] DOD1.13: Nhật ký kiểm toán đủ old/new; hash chain verify pass | Date: 23/08/2026 | Note: AuditChainTest 4 bài
- [x] DOD1.14: Nhập Excel chạy khô đúng | Date: 23/08/2026 | Note: ConstructionImportTest 10 bài — còn dòng lỗi thì không dòng nào được ghi
- [x] DOD1.15: Mã lỗi BE = FE | Date: 23/08/2026 | Note: error-map.test.ts đọc thẳng error-messages.properties; 74 mã, có bài đếm buộc người thêm mã phải biết mình vừa thêm
- [x] DOD1.16: Cổng bao phủ tầng domain đã nâng khỏi 0.18 | Date: 23/08/2026 | Note: core/operations đạt từ trước; content nay 18.2% và cổng CHẠY THẬT lần đầu (trước đó bị bỏ qua vì module không có bài kiểm nào)
- [ ] DOD1.17: Trang chủ cổng < 3s (NFR-02) đo trên môi trường gần thật | Note: KHÔNG kiểm được ở máy phát triển — cần VPS staging đã dựng. Nằm trong checklist nghiệm thu của docs/deploy-guideline.md ⭐ **Đo được 27/8 trên staging có nội dung thật** (`https://staging.songnhue.com/`, 3–5 lượt mỗi đợt): TTFB 0,15–0,57s · **tổng 0,22–0,87s** · 95,8 KB. ⛔ **CHƯA ĐỦ để tick**: đó là thời gian tải *tài liệu HTML*, không phải toàn trang — chưa tính JS/CSS/ảnh/font, và NFR-02 nói về trang chủ như người dùng thấy. Cần đo bằng công cụ đo trang thật (LCP) từ máy ở Việt Nam, và đo cả lượt ISR NGUỘI chứ không chỉ lượt đã ấm
