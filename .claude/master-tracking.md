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

- [x] T11.1: Build & push image lên GHCR, tag theo commit SHA
- [ ] T11.2: Dựng 3 VM theo phân bổ trên; `compose.backup.yml` cho VM-3 (kho dump + Prometheus/Grafana)
- [ ] T11.3: `compose.staging.yml` / `compose.prod.yml`: `nginx` + `app` + `postgres` + `minio` + `backup-agent`
- [ ] Nhận nợ WS-3: `POSTGRES_INITDB_ARGS` phải y hệt `compose.infra.yml` (`--locale-provider=icu --icu-locale=vi-VN`). Quên thì DB production xếp `ORDER BY` tiếng Việt sai, và đổi sau khi đã có dữ liệu là phải dump + restore toàn bộ
- [ ] T11.4: Migration là service riêng `migrator` chạy trước (`depends_on: service_completed_successfully`), app khởi động với `flyway.enabled=false`
- [ ] T11.5: Tự động `pg_dump` trước mỗi lần deploy production, giữ riêng khỏi bản đêm
- [ ] T11.6: Nginx: TLS 1.3, HSTS, CSP, `X-Frame-Options: DENY`, `Referrer-Policy`, ẩn version, rate limit theo IP, giới hạn body size route upload
- [ ] Nhận nợ WS-4: chặn `/swagger-ui/` và `/v3/api-docs/` ở nginx production
- [x] T11.0: Rà đường triển khai staging bằng mã thật trước khi bắt tay | Date: 24/08/2026 | Note: 4 lỗi CHẶN, cả 4 im lặng — (1) smoke test của cả hai workflow CD hỏi `/actuator/**` qua tên miền công khai, đường KHÔNG định tuyến: đo thật public-web 404, admin-app 200 kèm HTML của SPA → lượt deploy đầu tiên đỏ sau 5 phút; đổi sang `/api/v1/public/site-config`, kiểm bằng bash cả hai nhánh. (2) 6 biến compose ĐÒI mà không tệp env mẫu nào có, viết `${}` không `:?` nên thiếu = chuỗi rỗng im lặng → thêm `ComposeEnvCompletenessTest` 3 bài + kiểm chứng ngược. (3) `GRAFANA_ADMIN_PASSWORD` viết `:?` mà không được nhắc ở đâu → lệnh ở §10 dừng ngay. (4) 3 service giám sát không có `mem_limit`, và ngân sách RAM VPS-2 là 5,4 GB trên máy 4 GB — gộp VM-3 vào VPS-2 mà không tính lại. Nguyên nhân gốc §10.39
- [x] T11.2-a: Chốt lại cấu hình VPS-2 | Date: 24/08/2026 | Note: **chốt 2 vCPU / 8 GB / 80 GB** (phương án A). Bản đầu ghi 4 GB — con số có từ lúc gộp VM-3 vào VPS-2 mà không tính lại ngân sách RAM: stack staging 3.968 + giám sát 928 + OS ~500 ≈ 5,4 GB. Loại phương án B (6 GB, không còn biên cho `pg_restore` lúc diễn tập khôi phục — DOD0.14 bắt buộc) và C (chuyển giám sát sang VPS-1, phá đúng lý do dựng ra nó). Vì chọn A nên KHÔNG sửa `compose.staging.yml` — staging càng giống production càng tốt. Đã đồng bộ `hosting_recommendations.md` §1 và `deploy-guideline.md` §1.2
- [ ] T11.2-b: Mua tên miền `.vn` — **một** tên miền cho cả hai môi trường (6 địa chỉ = 1 gốc + 5 tên miền phụ miễn phí). Chủ thể PHẢI là Công ty; đổi chủ thể về sau là thủ tục hành chính cần hồ sơ hai bên. ⚠ Kiểm bằng `whois` sau khi đăng ký: có nhà đăng ký đăng ký dưới tên CHÍNH HỌ rồi 'quản lý hộ' — khi đó Công ty không sở hữu tên miền. Cách làm đầy đủ: `docs/deploy-staging.md` §2
- [x] T11.11: Vá 2 lỗi lộ ra sau lượt deploy staging đầu tiên | Date: 25/08/2026 | Note: (1) **204 No Content bị `apiClient.unwrap` biến thành lỗi trên 24 endpoint** — Spring không gọi `ResponseBodyAdvice` cho handler `void` nên 204 không có envelope, axios đặt `data=''`, `''.success` là undefined → ném `SYS-0001` SAU KHI máy chủ đã commit. Ở đổi mật khẩu thì `api.post` ném trước `endSession()` → phiên không dọn → bấm lại → 403 AUTH-0005. ⚠ Lượt sửa 22/8 đã chữa triệu chứng thứ hai của đúng chuỗi này mà không tới được mắt xích đầu. Vá ở `unwrap` (24 đường vào chung), ghim hợp đồng hai đầu, kiểm chứng ngược 5 đỏ/1 giữ xanh. §10.40 (2) **`DB_APP_PASSWORD` không ai đọc, che mất `DB_PASSWORD` thật sự thiếu** → postgres staging quay vòng khởi động lại. Sống sót vì `compose.infra.yml` (local) khai đúng. Thêm `PostgresInitEnvTest` bắt cả hai chiều thiếu/thừa. §10.41
- [x] T11.12: Bộ seed nội dung staging `deploy/seed/` | Date: 25/08/2026 | Note: 4 ảnh vào MinIO + 5 bài (đều sao chép từ báo ngoài — **chỉ staging**, có cảnh báo bản quyền). Là SCRIPT không phải migration: Flyway chạy ở mọi môi trường không hỏi ai. Không seed `categories` (migration `V202608191021` đã sở hữu). Sửa ghi cứng `/images/` → `/api/v1/public/files/{id}`, gán ảnh bìa cho cả 5 — trước đó `cover_attachment_public_id` rỗng ở cả 18 bài nên thumbnail không có gì để hiện. Kiểm trên CSDL nháp giữ nguyên danh mục: 5/5 có bản đăng, 5/5 có bìa, 0 đường dẫn cứng, chạy lại không nhân đôi. 2 lỗi chỉ lộ khi chạy thật: `ON CONFLICT` trên chỉ mục MỘT PHẦN, `author_user_id` NOT NULL
- [x] T11.13: Vá CD Staging đỏ ở lượt chạy đầu | Date: 25/08/2026 | Note: PR #14 bị **squash** vào staging → `dbb67e6` một cha → `HEAD^2` không có → workflow rơi về `HEAD` (SHA chưa từng có trên dev) → lùi vào lịch sử riêng của staging → báo 'không tìm thấy image'. ⚠ Ba bước chẩn đoán trong thông báo đều là ngõ cụt: 3 image ĐÃ đẩy đủ cho a0b6bfc, job xanh, đăng nhập xanh. Cái sai thật là bản dự phòng mô tả SAI NGUYÊN NHÂN. Nay đối chiếu bằng CÂY TỆP với 200 commit dev; không khớp thì thoát 1 nêu đích danh squash. Kiểm bash 3 kịch bản trong worktree riêng. §10.42
- [x] T11.14: Vá CD Staging đỏ lượt thứ hai — lượt tra image GHCR đi ẩn danh | Date: 25/08/2026 | Note: Lịch sử đã đúng (`Đỉnh dev đang đề bạt: 9765581 (merge-base, cây tệp trùng khít)`) nhưng vẫn 'không tìm thấy image'. Bước tra bị gắn `env: DOCKER_CONFIG: ${{ runner.temp }}/.docker` trong khi `docker/login-action` ghi vào `$HOME/.docker` → `Login Succeeded!` là THẬT mà lượt tra vẫn ẩn danh; gói GHCR mặc định RIÊNG TƯ (đo: token ẩn danh rỗng, manifest 403) → cả 50 ứng viên trượt. ⚠ Dòng `env` ấy có từ PR #1, sống 4 tháng vì workflow chưa chạy thật lần nào; `deploy-prod.yml` KHÔNG có nó — chính sự bất đối xứng dẫn tới nguyên nhân. ⚠ Đây là lần thứ HAI liên tiếp cùng một thông báo lỗi trỏ vào ba chỗ đều đang tốt, vì `2>/dev/null` làm 403 và 404 giống hệt nhau. Vá 3 phần: gỡ `DOCKER_CONFIG` · thêm lượt tra thử để nguyên stderr báo đích danh lỗi QUYỀN · `GhcrLookupAuthTest` 3 bài, kiểm chứng ngược 3 đỏ/khôi phục xanh. §10.43
- [x] T11.15: Vá CD Staging đỏ lượt thứ ba — `packages: read` cho một workflow có GHI | Date: 25/08/2026 | Note: Lượt tra image đã chạy đúng (tìm ra 94fe2e9, sao chép layer) rồi hỏng ở bước cuối `docker buildx imagetools create --tag …:staging` với `403 denied: installation not allowed to Write organization package`. Cả hai workflow triển khai khai `packages: read` — hợp lý vì chúng gần như chỉ đọc, trừ đúng MỘT bước ghi ở cuối, mà khối `permissions:` thì cách đó 180 dòng và thông báo 403 không nhắc tới nó. `ci.yml` vốn khai `packages: write` ở hai job đóng gói. ⚠⚠ Bài kiểm ĐẦU TIÊN của lượt vá này KHÔNG bắt được: nó hỏi `contains("packages: write")`, mà cùng tệp có dòng `echo "quyền 'packages: write'…"` trong khối `run:` (văn bản thường, không phải chú thích) — chính dòng chẩn đoán thêm ở §10.43 làm bài kiểm mù. Đổi sang đọc CẤU TRÚC khối `permissions:`; kiểm chứng ngược 4 kịch bản đều đỏ, khôi phục 4 xanh. §10.44
- [x] T11.16: Dựng đường nạp nội dung staging — CD xanh mà cổng không có dữ liệu | Date: 25/08/2026 | Note: Lượt CD Staging đầu tiên xanh 11/11 bước (rsync, migrator, up -d, smoke test) mà cổng vẫn không có 5 bài — vì đường ống CHƯA TỪNG có bước nạp dữ liệu, và đó đúng thiết kế: bộ seed là 5 bài sao chép nguyên văn từ báo ngoài, cố ý làm script gõ tay thay vì migration. rsync của CD đã đưa `deploy/seed/` sang `/opt/songnhue/seed/` mỗi lượt, chỉ thiếu người bấm chạy. ⚠ Smoke test `/api/v1/public/site-config` không phân biệt được cổng có nội dung với cổng rỗng (luật 9) — giới hạn đã biết, ghi lại. Thêm `seed-staging.yml`: workflow_dispatch DUY NHẤT, `chay-thu` mặc định, ô xác nhận gõ tay, KHÔNG có tham số môi trường nên chỉ biết bộ STAGING_*. `SeedNeverAutomaticTest` 5 bài, kiểm chứng ngược 4 đỏ/khôi phục 5 xanh. Tiện thể sửa chú thích lệch ở compose.staging.yml (còn ghi VPS-2 4 GB, đã chốt 8 GB từ T11.2-a). §10.45
- [x] T11.18: CI backend đỏ vì tải hụt bản phân phối Maven | Date: 25/08/2026 | Note: `wget: Failed to fetch apache-maven-3.9.9-bin.zip`. Đo lại URL: HTTP 200, tải byte đầu 206 — chập mạng runner, không phải lỗi mã. Nhưng lộ 3 chỗ mong manh: (1) `cache: maven` của setup-java CHỈ đệm `~/.m2/repository`, bản phân phối nằm ở `~/.m2/wrapper/dists` nên mỗi lượt CI tải lại ~9 MB; (2) `mvnw` tải bằng `wget` KHÔNG thử lại; (3) lượt tải nằm chung bước với lệnh build nên thông báo lỗi mang tên `spotless:check` — một bước CHƯA TỪNG chạy (luật 22). Vá 2 thứ độc lập ở cả ci.yml và security-scan.yml: đệm `~/.m2/wrapper` khoá theo hash maven-wrapper.properties, và tách lượt tải thành bước riêng thử lại 3 lần. Vòng thử lại kiểm bằng mvnw giả cả 3 đường (hỏng hết → thoát 1; hỏng rồi đạt → thoát 0; đạt ngay → không thử lại). `MavenWrapperCiTest` 2 bài, kiểm chứng ngược 4 đỏ/khôi phục xanh — gồm kịch bản bước đệm đặt SAU lượt gọi đầu tiên. §10.47
- [x] T11.19: Bỏ `docker compose` khỏi script vận hành — seed hỏng, và pre-deploy-dump cũng vậy | Date: 25/08/2026 | Note: `nap-that` hỏng exit 125 `docker: no name set for network`. Gốc: compose NỘI SUY TOÀN BỘ tệp trước khi trả lời cả lệnh chỉ đọc `ps -q`; compose.prod.yml đòi APP/ADMIN/PUBLIC_IMAGE dạng `${X:?}` — ba biến CỐ Ý không có trong .env (workflow deploy export lúc chạy), nên mọi script chạy ngoài lượt triển khai đều hỏng. `set -e` không cứu vì lượt hỏng nằm trong `$( )` làm đối số → thành chuỗi rỗng rồi đi tiếp. Tái hiện tại chỗ đúng thông báo máy chủ. ⚠⚠ Bài kiểm mới chỉ ra `pre-deploy-dump.sh` cùng lỗi, nhưng biểu hiện thành '✗ Postgres không trả lời — DỪNG' trong khi CSDL khoẻ — mà đó là bản chụp trước triển khai, điểm quay lui DUY NHẤT khi migration hỏng vì không có PITR; deploy-prod.yml gọi nó qua heredoc CÓ trích dẫn nên không truyền biến image nào → lượt deploy production đầu tiên sẽ dừng ngay tại đó. Vá bằng `deploy/lib/docker-svc.sh` tra theo nhãn com.docker.compose.*; kiểm bằng container thật gắn nhãn, gồm cả nhánh service không tồn tại phải DỪNG HẲN chứ không trả rỗng. `ScriptDockerLookupTest` 3 bài; kiểm chứng ngược 4 kịch bản — 1 kịch bản LỌT ở bản đầu vì tệp định nghĩa `container_cua` tự đếm mình. §10.48
- [x] T11.20: `minio-init` chưa từng chạy — staging không có bucket nào | Date: 25/08/2026 | Note: Seed hỏng ở `mc: Bucket songnhue-media does not exist`. compose.prod.yml CÓ service `minio-init` (tạo 3 bucket + bật versioning bucket audit + tạo tài khoản dịch vụ hạn chế) nhưng KHÔNG service nào depends_on nó, còn workflow chỉ `up -d app admin-app public-web nginx` → nó không bao giờ chạy. Hỏi compose bằng --dry-run với đúng lệnh ấy: trước bản vá không có songnhue-minio-init, sau bản vá có. ⚠⚠ Phạm vi rộng hơn seed nhiều: không bucket + không tài khoản dịch vụ = MỌI lượt tải tệp lên, kết xuất báo cáo và kết xuất audit trên staging đều hỏng, và không bước nào báo sai (smoke test không đi qua MinIO). Vá bằng `app depends_on minio-init: service_completed_successfully` — đặt ràng buộc ở chỗ CẦN nó, không thêm tên vào dòng `up -d` (service thứ 11 sẽ lại mồ côi). `service_started` không đủ vì minio-init chạy xong rồi thoát. `NoOrphanServiceTest` canh ĐỒ THỊ chứ không canh danh sách, viết TRƯỚC khi vá và đỏ đúng ['minio-init']; kiểm chứng ngược 4 kịch bản gồm 1 kịch bản chống báo oan (service có profiles). 📌 Sau lượt deploy tới phải kiểm TẢI TỆP LÊN từ admin staging — chức năng chưa từng chạy được. §10.49
- [x] T11.21: Dọn đường ống triển khai — seed vào chuỗi migration, gộp 2 workflow, bỏ vòng tra image | Date: 25/08/2026 | Note: **Thay đổi hình dạng, không vá tiếp.** 7 sự cố §10.42→§10.49 đều nằm trên đường nạp nội dung staging — một đường 6 khâu CHỈ chạy khi có người bấm, nên mỗi lượt bấm chỉ lộ ra khâu kế tiếp. (1) Bộ seed thành migration Flyway ở location RIÊNG `db/seed/portal`, mặc định `db/seed/none` (thư mục có thật, rỗng) — production không đặt `SEED_LOCATION` thì Flyway KHÔNG NHÌN THẤY tệp, chứ không phải 'chạy rồi không làm gì'. Chặn ở location vì migration này có lệnh XOÁ BÀI. (2) Byte ảnh qua `minio-init` từ `deploy/seed/media/<storage_key>` — bố cục thư mục CHÍNH LÀ khoá, không tiền tố viết cứng; thứ tự byte-trước-hàng đặt bằng `migrator depends_on minio-init` chứ không bằng thứ tự dòng lệnh workflow (luật 12). (3) Vị từ xoá canh theo QUAN HỆ menu (`NOT EXISTS menu_items`) — `DELETE FROM articles` trần dừng giữa chừng vì `menu_items.article_id` là RESTRICT, sau khi đã xoá một phần. (4) `SeedGateTest` 9 bài (đối chiếu sha256/size với BYTE THẬT, không với images.json; canh cặp `SEED_LOCATION`↔`SEED_MEDIA_DIR` không bật lệch) + `SeedPortalMigrationTest` 2 bài chạy CHÍNH tệp SQL trên lược đồ thật rồi ROLLBACK, có dựng sẵn bài 'rác' để phân biệt 'xoá đúng' với 'không xoá gì' (luật 9). ⚠ Bản đầu bài canh khối xoá ĐỎ OAN vì khớp trúng cụm `DELETE FROM articles` trong chú thích giải thích vì sao không được viết thế (luật 2). (5) `deploy.yml` workflow_call làm thân chung; 2 caller chỉ còn cổng chặn + compose + secret. Reusable KHÔNG tự cấp quyền được → `GhcrLookupAuthTest` canh thêm vế caller. (6) Job CI `Gắn tag SHA cho image không đổi` gắn tag mới lên ĐÚNG digest cũ → bỏ vòng quét 50×3 và bước tra thử phân biệt 403/404 (§10.43 tự tiêu). Không mâu thuẫn cicd.md §2.1: digest không đổi, chỉ thêm một tên. (7) Smoke test 3 câu — thêm 'cổng có ≥1 bài' và '`GET /files/<id>` trả image/*', phép kiểm DUY NHẤT chứng minh MinIO có byte (§10.45). (8) `pg_dump` trước deploy nay chạy ở CẢ staging. (9) `NoOrphanServiceTest` tự tìm workflow — danh sách viết cứng đã trỏ vào 2 tệp rỗng sau khi gộp. Bỏ 823 dòng: seed-staging.yml + seed.sh + SeedNeverAutomaticTest + MavenWrapperCiTest + 2/4 bài GhcrLookupAuth + trùng lặp 2 workflow. 303 bài app xanh. §10.50
- [x] T11.23: Ảnh cổng chưa từng trả về được một byte — envelope bọc cả `byte[]` | Date: 25/08/2026 | Note: Smoke test câu 3 đỏ `500 (content-type: application/json)`. Byte CÓ trong MinIO, hàng CSDL CÓ, bucket không lệch — thủ phạm là `ResponseEnvelopeAdvice`: Spring chọn converter theo KIỂU TRẢ VỀ trước khi advice chạy, `ResponseEntity<byte[]>` → `ByteArrayHttpMessageConverter` → ép kiểu thân về `[B` lúc tính Content-Length → `ClassCastException`, SAU KHI đã đọc xong byte từ MinIO (603 ms trong log). ⚠ Ba giả thuyết đầu của tôi (`SEED_MEDIA_DIR`, tên bucket, TLS của FILES_DOMAIN) đều SAI — đều là giả thuyết hạ tầng vì lỗi xuất hiện giữa lượt triển khai; thứ chấm dứt việc đoán là log ứng dụng, sống sót qua cả lượt quay lui nhờ `/var/log/songnhue` là bind mount. Endpoint CÓ bài kiểm nhưng dùng UUID không tồn tại nên chỉ đi nhánh 404 (luật 7). Vá bằng DANH SÁCH CHO PHÉP: chỉ bọc khi converter là converter JSON — không viết `instanceof byte[]` vì luôn có loại thứ tư lọt qua (luật 24). 4 bài / 2 tầng, gồm 1 bài đi ĐƯỜNG PRODUCTION (MinIO thật → HTTP thật → so byte); kiểm chứng ngược cho ra đúng dòng chữ của staging. PR #35. §10.52
- [x] T11.24: Bản vá không bao giờ được nạp — compose giữ container cũ mà báo xong | Date: 25/08/2026 | Note: Sau khi #35 merge, staging VẪN đỏ đúng ngoại lệ cũ. `docker inspect` chỉ ra container đang chạy dựng lúc 07:19 bằng tay, không phải bởi lượt triển khai 07:25; log runner in `Container app Running` chứ không `Recreated`. Lượt triển khai giải đúng digest, `pull` đúng digest — cả chuỗi chỉ chứng minh digest TỚI ĐĨA, không dòng nào đo container đang chạy cái gì (luật 9). Vá 2 vế: `up -d --force-recreate` (triển khai là mệnh lệnh, không phải gợi ý để compose cân nhắc) + đọc lại ID ảnh từng container so với ID ảnh của ref vừa triển khai, báo cáo trọn vẹn cả 3 service rồi mới thoát (luật 11). Vế 1 KHÔNG thay được vế 2: ref bị cấp sai thì force chỉ dựng lại đúng image sai. So bằng ID ảnh không so chuỗi tag. Kiểm 2 tầng: vòng lặp chạy thật với `docker` giả 2 kịch bản (⚠ bộ giả lượt đầu xanh cả hai vì đọc nhầm chỉ số đối số — chính bước chứng minh suýt là xanh giả) + `DeployImageProofTest` 5 bài canh trên thân workflow ĐÃ BỎ chú thích, gỡ bộ canh → 3/5 đỏ. ⛔ `.env` staging đã kiểm: KHÔNG chứa APP/ADMIN/PUBLIC_IMAGE, nên nguyên nhân là chính phép quyết định của compose — cơ chế chưa giải thích được, và đó chính là lý do phải có vế đo. Xác nhận trên máy thật: `--force-recreate` → `Started`, digest đúng, `GET /public/files/<id>` → **200 image/jpeg**. PR #37. §10.53
- [x] T11.27: Trang chủ cổng chưa từng hiện dữ liệu thật sau một lượt triển khai | Date: 25/08/2026 | Note: **Hai lỗi xếp chồng và chúng CHE NHAU.** (A) `next build` dựng sẵn `/` rồi ghi HTML vào image; lượt build ở CI KHÔNG có backend nên `apiGet` nuốt lỗi trả `null` → bản nướng không có bài nào, và mỗi container mới phục vụ đúng bản ấy cho tới khi ISR dựng lại (mà `--force-recreate` của §10.53 bảo đảm là MỖI lượt deploy). (B) Bảy component có mảng `DEFAULT_*` viết cứng trộn kiểu `length >= 4 ? thật : [...thật, ...BIA]`, nên mảng RỖNG cho ra trang ĐẦY. Cộng lại: smoke test in `✓ 9 bài` còn người dùng thấy trang không có nội dung seed — cả hai đều đúng. Quy mô B: **19 bài viết · 4 văn bản CÓ SỐ HIỆU VÀ NGƯỜI KÝ (`158/QĐ-SN`, 'Chủ tịch Công ty') · 5 trạm thuỷ văn có mực nước + một mức 'Cảnh báo BĐ I' trên tên cống có thật · 9 số điện thoại (gồm 1 số TRỰC BAN PCTT) · 3 mục menu trỏ tới chuyên mục không tồn tại · 4 ảnh hotlink Unsplash · 1 video YouTube gắn tiêu đề 'Phóng sự … Sông Nhuệ'**. Ba lớp canh cùng trượt: vitest cố ý loại `components/` (lý lẽ đúng cho Server Component, áp quá rộng thành vùng không ai soi) · `siteContactConfig.test.ts` chỉ đọc 2 tệp (luật 12) · **và regex điện thoại đòi ĐÚNG 4 nhóm số nên không khớp `(024) 3382 4580` dạng 3 nhóm — luật 24 lần thứ ba trên cùng một regex**, chỉ lộ ra vì bản mới bắt mỗi bộ canh tự kiểm chứng với một mẫu vi phạm. Vá A: `await connection()` đặt ở **chokepoint** `apiGetWithMeta` — chỗ DUY NHẤT mọi lượt đọc API đi qua, nên 'đọc API mà vẫn bị dựng sẵn' thành không biểu diễn được (luật 12); bản đầu đặt ở `page.tsx` và `sitemap.ts` lập tức chứng minh cách đó sai (sitemap trong image chỉ có ĐÚNG 1 url, host `localhost:3000`), chuyển về chokepoint thì `/` + `/_not-found` + `/sitemap.xml` cùng ra `ƒ` và ngoại lệ `force-dynamic` tự tiêu. ⚠ KHÔNG dùng `force-dynamic`: nó hạ fetch xuống `no-store` → backend trả lời mọi request; giữ ISR thì phải thêm `fetchCache` — hai công tắc phải nhớ (luật 14). Vá B: xoá sạch, thay bằng `EmptyBlock` nói rõ lý do. 📌 Đúng cái bẫy `next.config.ts` ĐÃ ghi cho `rewrites()` — đã chữa cho rewrites, không ai nghĩ tiếp rằng HTML prerender cũng bị ghim y hệt. Đo 2 chiều: gỡ 1 dòng → `/`,`/_not-found`,`/sitemap.xml` về `○`, bộ canh đỏ 2 bài (⚠ lượt gỡ đầu KHÔNG đo được gì: build chết ở `TS6133` trước khi in bảng route, phải gỡ cả `import` — luật 10). 25 bài mới, 3 tầng chồng nhau + backstop cấu trúc. FE 222 bài (142 admin + 80 cổng, trước 55). §10.54
- [ ] T11.7: Secrets: GitHub Secrets cho CI; `/opt/songnhue/.env` (chmod 600) trên VM; key AES/JWT ở `/opt/songnhue/keys/` ngoài backup DB
- [ ] Nhận nợ WS-22 (§10.38): đặt **biến kho** `PUBLIC_SITE_URL` = tên miền thật rồi build lại image `public-web`. Chưa đặt thì `sitemap.xml`/canonical/Open Graph của image đóng ra trỏ về `http://localhost:3000` — đã đo trên image dựng đúng đối số CI. `NEXT_PUBLIC_*` nhúng lúc build nên đổi biến **bắt buộc phải build lại**, không sửa được ở compose
- [~] T11.8: `deploy-staging.yml` (tự động khi push vào `staging`) và `deploy-prod.yml` (chỉ `workflow_dispatch`, có `environment: production` chờ duyệt)
- [x] Nhận nợ WS-10/T10.7: mô tả cũ ("merge `master`") viết theo mô hình 2 nhánh, đã lỗi thời
- [x] T11.22: Triển khai theo digest · quay lui tự động · `make rehearse` | Date: 25/08/2026 | Note: (1) **Digest thay tag** — tag là một cái tên và tên gán lại được, kể cả bởi chính job gắn tag bù; lượt tra và lượt `pull` cách nhau vài phút là đủ để môi trường chạy thứ không ai truy ra được. Khẳng định chuỗi khớp `sha256:` trước khi đi tiếp, vì giá trị rỗng sẽ thành `image@` và hỏng ở chỗ khó đoán. (2) **Quay lui**: hỏi máy chủ *cái gì đang chạy* bằng `docker inspect` (không đọc tệp compose) TRƯỚC khi đổi; smoke test đỏ → dựng lại 3 digest cũ → hỏi lại CÂU 1 (câu 2/3 nói về nội dung, mà nội dung có thể đã bị migration của lượt hỏng đổi). `failure()` chứ không `always()` — `always()` chạy cả khi bấm huỷ. ⛔ Giới hạn ghi trong CHÍNH khối `run:`: đây là quay lui về MÃ NGUỒN, migration một chiều nên nếu đã đổi lược đồ thì bước này không cứu gì — đường về dữ liệu là bản chụp `predeploy-*`. (3) **`make rehearse`** vá nguyên nhân gốc của cả chuỗi §10.42→§10.49: đường triển khai chưa từng có cách thử nào ngoài một lượt deploy thật (`ci-local` không đụng compose/minio-init/thứ tự khởi động; `compose.local.yml` là đường build). Chạy ĐÚNG compose.staging.yml + ĐÚNG lệnh `run --rm migrator`, hỏi 3 câu: bucket có chưa · seed đủ hàng chưa · **mỗi storage_key có byte thật không** (câu không bài JUnit nào trả lời được). `compose.rehearse.yml` đổi đúng 3 thứ và mỗi thứ là một điều diễn tập KHÔNG chứng minh được — in ra ở cuối mỗi lượt, không giấu trong tài liệu. ĐO CẢ HAI CHIỀU: bản hiện tại 33 migration + 3 ✓; làm hỏng có chủ đích `SEED_LOCATION=` → ✗ câu 2 '0 bài (cần 5)', thoát 1 — đồng thời là bằng chứng cổng chặn production hoạt động. ⚠ Bản đầu câu 3 in '0 khoá không ra được byte' rồi vẫn đỏ, không phân biệt được 'mọi khoá đều tốt' với 'không có khoá nào' (luật 9) — đã tách. ⚠ `ScriptDockerLookupTest` bắt được bản đầu của chính rehearse.sh; luật cũ cấm tuyệt đối `docker compose` trong script, nay chính xác hơn: gọi compose thì phải TỰ CẤP đủ 3 biến image và cấp TRƯỚC lượt gọi. §10.51
- [~] T11.9: Quy trình rollback: quay lại image tag trước; migration đã đổi schema → restore từ bản dump pre-deploy. Mỗi migration đổi schema phải kèm ghi chú rollback trong PR | Note 25/08/2026: **vế mã nguồn đã dựng xong và tự động** (T11.22) — ghi lại digest đang chạy trước khi đổi, smoke test đỏ thì dựng lại. Vế **dữ liệu** vẫn là việc tay theo `docs/runbook/khoi-phuc-du-lieu.md`. ⛔ Chưa tick đủ vì **chưa lượt deploy thật nào đi qua đường quay lui** — DOD0.21 giữ nguyên trạng thái treo cho tới khi có (luật: 'một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai')
- [ ] T11.25: `minio-init` nuốt lỗi bằng hai `|| true` — `mc admin user add` và `mc admin policy attach` hỏng vẫn in `✓ Bucket và tài khoản dịch vụ sẵn sàng`. Tài khoản dịch vụ sai cặp khoá là MỌI lượt tải tệp/kết xuất báo cáo hỏng, im lặng. Mở ra từ §10.52
- [ ] T11.26: Bỏ `SEED_MEDIA_DIR` — nó không mang thông tin gì (bind mount đã ghim `/seed-media`), chỉ nhắc lại "seed đang bật", tức một công tắc thứ hai tồn tại chỉ để lệch với công tắc thứ nhất. Cho `minio-init` đọc thẳng `SEED_LOCATION` thì lệch thành **không biểu diễn được**, thay vì được canh bằng `SeedGateTest.haiVeKhongDuocLech` — bài kiểm soi **tệp mẫu trong repo**, không soi `/opt/songnhue/.env` đang chạy (luật 12). Mở ra từ §10.52
- [ ] T11.28: `http://songnhue.bhh40.net` ghi cứng ở 2 component (`DirectiveDocumentsSection`, `PortalSidebar`) — địa chỉ hệ thống văn bản điều hành là **cấu hình của khách**, phải nằm trong `settings` có UI sửa (CLAUDE.md luật 12), không phải hằng số trong bundle. Đổi địa chỉ hiện nay = sửa mã + dựng lại image. Đang được `noFabricatedContent.test.ts` cho qua bằng danh sách tên miền cho phép, kèm ghi chú trỏ về nợ này. Mở ra từ §10.54
- [ ] T11.29: Trang chủ nay có 4 ô `EmptyBlock` thường trực (thuỷ văn MOD-03 · văn bản CN-01.7 · thư viện ảnh · đơn vị trực thuộc `org_units`) — trung thực nhưng **cần Công ty quyết**: ẩn hẳn khối, hay giữ ô rỗng chờ đấu nối. Không chặn code, chặn nghiệm thu giao diện. Liên quan G8/G13/G14. Mở ra từ §10.54
- [ ] T11.10: Smoke test sau deploy: health, login, 1 endpoint có quyền, kiểm tra backup gần nhất. Chốt `app.nodes`/`worker.enabled`/`shedlock.enabled` đọc từ env

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
- [ ] Nợ #46 (bấm ở GitHub): thêm 3 context đóng gói image vào `required_status_checks` của `dev` — script + tên context đầy đủ ở `docs/branch-protection.md` §4.1. Chưa bật thì hai job ấy chỉ *hiện* lỗi ở PR chứ không *chặn* merge
- [x] T22.21: Vá CI đỏ sau khi merge vào `dev` (PR #10) | Date: 24/08/2026 | Note: job **đóng gói image `public-web` — do chính PR này thêm vào — đỏ ngay lần chạy đầu**. `vars.PUBLIC_SITE_URL` chưa đặt → build-arg rỗng → `ARG` không mặc định → `ENV` gán **chuỗi rỗng** → `??` trong `site.ts` không đỡ → `new URL('')` giết `next build`. ⚠ Chính lượt CI ấy in cảnh báo "chưa đặt PUBLIC_SITE_URL → sitemap trỏ localhost", tức tin có mặc định đang đỡ (luật 3). Local không thấy vì mọi lượt build đều nạp `.env.local`. Chữa: `??` → `||` + `site.test.ts` (11 bài, kiểm **hành vi** ở cả hai trạng thái rỗng/chưa-đặt, kèm bài liệt kê biến). Kiểm chứng: trả `??` về → 2 bài đỏ đúng `Invalid URL`; `docker build` đúng đối số CI → thoát 0, image chạy, health 200. Nguyên nhân gốc §10.38

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
- [ ] DOD0.21: Rollback

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
- [ ] DOD1.17: Trang chủ cổng < 3s (NFR-02) đo trên môi trường gần thật | Note: KHÔNG kiểm được ở máy phát triển — cần VPS staging đã dựng. Nằm trong checklist nghiệm thu của docs/deploy-guideline.md
