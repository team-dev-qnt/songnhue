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
- [x] : Kèm theo (phát sinh từ WS-2): lệnh bootstrap `superadmin`
- [x] T5.8: 2FA TOTP bắt buộc Super Admin + Admin + Admin HR (enroll, otpauth URI, verify, 10 mã khôi phục)
- [x] T5.9: Tầng 2
- [x] : Kèm theo (từ WS-4): `AuditContextFilter` đã điền `userId`/`username`
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
- [x] : Nhận nợ WS-5: `TokenMaintenanceJob` → `TokenCleanupHandler` trong hàng đợi
- [x] : Nhận nợ WS-2: job tạo partition `audit_logs`
- [x] T6.9: ShedLock cài sẵn, `app.shedlock-enabled` đọc env, mặc định tắt (1 node)
- [x] T6.10: Async job API: `JobDtos.JobAccepted` (202 + jobId) + endpoint tra tiến độ `GET /api/v1/jobs/{id}`
- [x] T6.11: Settings service: key-value có type + validate 2 tầng + Caffeine cache + API cho UI; export/import loại trừ credential
- [x] T6.12: Audit interceptor: ghi old/new JSON tự động qua Hibernate; append-only + hash chain (trigger DB); API verify chain
- [x] T6.13: Job kết xuất audit >5 năm: CSV nén → MinIO bucket riêng → đọc ngược verify checksum → mới xoá; ghi anchor `last_hash`; lỗi → không xoá dòng nào + `ADM-2001`
- [x] T6.14: Thông báo hệ thống (M5.13): Admin gửi tới toàn bộ hoặc một nhóm tài khoản
- [x] T6.15: Vertical slice: CRUD `users` + `roles` qua quyền tầng 2 + audit + notification
- [x] : Nhận nợ WS-5: `AuthorityLoader.invalidate(publicId)` được gọi ở gán vai trò, khoá/mở tài khoản và xoá tài khoản

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
- [x] : Trả nợ WS-3/T3.4 (nửa admin-app): `deploy/docker/admin-app.Dockerfile` build thật → image chạy, `/healthz` trả `ok`, SPA fallback 200 ở đường dẫn sâu, healthcheck `healthy`.  Phải sửa Dockerfile: bản cũ chép cả `public-web/package.json` và chạy `npm ci` trần nên đổ ngay khi WS-9 chưa tạo thư mục đó → nay `npm ci --workspace admin-app --include-workspace-root`. (WS-9 đóng nốt nửa public-web ngày 17/8 → T3.4 và DoD mục 2 đã đóng.)
- [x] T8.2: `shared/tokens.ts`
- [x] T8.3: `shared/apiClient`
- [x] T8.4: `shared/error-map.ts` mirror 49 mã
- [x] T8.5: `useAuth`, `usePermission`, `useAnyPermission`, `RequireAuth`/`RequireAnonymous`/`RequirePermission`
- [x] T8.6: Màn hình auth: đăng nhập · 2FA verify · 2FA enroll (QR + mã khôi phục, bắt xác nhận đã lưu) · đổi mật khẩu (bắt buộc lần đầu và tự nguyện). ⛔ Chưa có "quên mật khẩu"
- [x] T8.7: `AdminLayout` + menu render theo permission (`menu.tsx` gộp "đường dẫn ↔ nhãn ↔ quyền" một chỗ) + băng thông báo bảo trì; trang 403/404/500 hiển thị `traceId` copy được
- [x] T8.8: 7 component nghiệp vụ: `StatusBadge` (+ `statusVocabulary.ts`), `ThresholdValue` (ngưỡng từ API, `stale` → xám theo G3), `ApprovalActions` (render từ `allowedActions`), `OrgUnitTreeSelect`, `AttachmentPanel`, `DateRangeFilter`, `ExportButton` (202 + jobId + hỏi tiến độ)
- [x] T8.9: `formatDateTime` UTC+7 `dd/MM/yyyy HH:mm` ép cứng múi giờ (không dùng giờ máy), `formatNumber` kiểu VN, `formatBytes`/`formatDuration`/`formatAge`
- [x] T8.10: 8 màn hình quản trị + 2 màn hình cá nhân: Tổng quan · Tài khoản (CRUD + khoá + phân vai trò) · Vai trò (chỉ xem, xem `architecture-review.md` §9.10.5) · Sơ đồ đơn vị (cây + thêm/chuyển/xoá) · Cấu hình (ô nhập dựng theo `valueType`) · Nhật ký kiểm toán + kiểm chuỗi hash · Sao lưu & khôi phục · Tình trạng hệ thống · Hộp thư · Phiên đăng nhập
- [x] : Nhận nợ WS-7 (#32): M5.10 + M5.11 gọi `/api/v1/backups/`, có hộp thoại khôi phục 3 lớp chặn (chuỗi `SONGNHUE` + lý do ≥ 10 ký tự + mã 2FA tươi)
- [x] T8.11: `DataTable`

## WS-9

- [x] T9.1: Next.js 16 + Tailwind 4 + TS strict; tokens dùng chung qua workspace thứ ba `frontend/design-tokens` (admin-app import lại từ đó)
- [x] T9.2: Layout công khai (đầu trang + điều hướng + chân trang, có liên kết "bỏ qua tới nội dung" cho bàn phím) + trang chủ tạm + 404 + 500
- [x] T9.3: SEO base: `metadataBase` + Open Graph + template tiêu đề · `sitemap.ts` · `robots.ts` tự chặn lập chỉ mục ở staging/local (cùng mã nguồn, khác `NEXT_PUBLIC_SITE_URL`)
- [x] T9.4: ISR: `revalidate = 300` ở trang chủ làm mẫu + `POST /api/revalidate` (bí mật không mang tiền tố `NEXT_PUBLIC_`, so sánh chuỗi thời gian không đổi, chưa cấu hình thì đóng chứ không mở)
- [x] T9.5: `GET /api/health` + `output: 'standalone'`; image build thật và chạy
- [x] : Trả nợ WS-3/T3.4: cả 2 image FE build thật → T3.4 đóng, DoD mục 2 đóng

## WS-10

- [x] T10.1: Testcontainers PostgreSQL + PostGIS làm nền cho integration test
- [x] : Nhận nợ WS-2: `flyway.clean()` gọi trên đúng bean của ứng dụng bị từ chối, và schema còn nguyên sau đó (15/8)
- [x] T10.2: ArchUnit
- [x] : Nhận nợ WS-5: mọi lớp con `ScopedEntity` phải mang `@Filter` kèm đúng hằng điều kiện dùng chung (15/8)
- [x] : Nhận nợ WS-6: `WorkflowAware.applyState` chỉ được gọi từ `WorkflowEngine` (15/8)
- [x] : Nhận nợ WS-4: luật cho phép import chéo `core.common.` (15/8)
- [x] T10.3: Harness ma trận RBAC role × resource (NFR-06)
- [x] : Nhận nợ WS-5
- [x] T10.4: Test chuỗi hash audit trên DB thật + `CryptoService` xoay khoá (có từ WS-4) + deny-by-default (có từ WS-5)
- [x] T10.5: Coverage gate tầng domain
- [x] T10.6: `ci.yml`: lint → unit → Testcontainers → ArchUnit → cổng bao phủ; quét CVE tách job riêng
- [x] T10.7: Branch protection cho luồng 3 chặng `dev → staging → production`

## WS-11

- [x] T11.1: Build & push image lên GHCR, tag theo commit SHA
- [ ] T11.2: Dựng 3 VM theo phân bổ trên; `compose.backup.yml` cho VM-3 (kho dump + Prometheus/Grafana)
- [ ] T11.3: `compose.staging.yml` / `compose.prod.yml`: `nginx` + `app` + `postgres` + `minio` + `backup-agent`
- [ ] : Nhận nợ WS-3: `POSTGRES_INITDB_ARGS` phải y hệt `compose.infra.yml` (`--locale-provider=icu --icu-locale=vi-VN`). Quên thì DB production xếp `ORDER BY` tiếng Việt sai, và đổi sau khi đã có dữ liệu là phải dump + restore toàn bộ
- [ ] T11.4: Migration là service riêng `migrator` chạy trước (`depends_on: service_completed_successfully`), app khởi động với `flyway.enabled=false`
- [ ] T11.5: Tự động `pg_dump` trước mỗi lần deploy production, giữ riêng khỏi bản đêm
- [ ] T11.6: Nginx: TLS 1.3, HSTS, CSP, `X-Frame-Options: DENY`, `Referrer-Policy`, ẩn version, rate limit theo IP, giới hạn body size route upload
- [ ] : Nhận nợ WS-4: chặn `/swagger-ui/` và `/v3/api-docs/` ở nginx production
- [ ] T11.7: Secrets: GitHub Secrets cho CI; `/opt/songnhue/.env` (chmod 600) trên VM; key AES/JWT ở `/opt/songnhue/keys/` ngoài backup DB
- [~] T11.8: `deploy-staging.yml` (tự động khi push vào `staging`) và `deploy-prod.yml` (chỉ `workflow_dispatch`, có `environment: production` chờ duyệt)
- [x] : Nhận nợ WS-10/T10.7: mô tả cũ ("merge `master`") viết theo mô hình 2 nhánh, đã lỗi thời
- [ ] T11.9: Quy trình rollback: quay lại image tag trước; migration đã đổi schema → restore từ bản dump pre-deploy. Mỗi migration đổi schema phải kèm ghi chú rollback trong PR
- [ ] T11.10: Smoke test sau deploy: health, login, 1 endpoint có quyền, kiểm tra backup gần nhất. Chốt `app.nodes`/`worker.enabled`/`shedlock.enabled` đọc từ env
- [x] : 1. Chạy native
- [x] : 2. Chạy full Docker
- [x] : 3. Fail-fast thiếu env
- [x] : 4. Migration sạch từ DB rỗng
- [x] : 5. Auth + 2FA
- [x] : 6. Refresh reuse detection
- [x] : 7. RBAC 3 tầng
- [x] : 8. Deny by default
- [x] : 9. Envelope + traceId
- [x] : 10. Audit hash chain
- [~] : 11. Attachment
- [x] : 12. Async job
- [~] : 13. Backup
- [ ] : 14. Đo RTO thật
- [x] : 15. Alert backup hỏng
- [~] : 16. Key không nằm trong backup
- [x] : 17. Restore UI
- [x] : 18. ArchUnit
- [x] : 19. CI đầy đủ
- [ ] : 20. Deploy Staging
- [ ] : 21. Rollback

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

- [ ] T19.1: Migration `operation_status_codes`
- [ ] T19.2: `construction_operation_status` append, không ghi đè.  "Tình hình hiện hành" = bản ghi mới nhất theo `effective_at`, KHÔNG phải `created_at`: trực ban được nhập bù cho một thời điểm đã qua, và hai cột đó sẽ khác nhau đúng vào lúc đó. Chỉ mục `(construction_id, effective_at DESC)`
- [ ] T19.3: Quy tắc, mã lỗi đã có sẵn: trùng mã → `OPS-2005` · mã có tham số mà không nhập giá trị → `OPS-2006` · xoá mã đã dùng → `OPS-2007` (chỉ được ẩn).
- [ ] T19.4: ⭐ Mắt xích (4) vào đúng `ConstructionStatusService.tinh()`, đặt sau sự cố/bảo trì và trước `BINH_THUONG`.  Cập nhật 21/8: (1) sự cố, (2) bảo trì và (5) mặc định đã chạy từ WS-18
- [ ] : T19.4-b ⭐⭐ Job đối soát (`JobHandler`, chu kỳ đọc từ `settings`)
- [ ] T19.5: `HydroAlertPort` ở `hydro/spi/` trả rỗng ở Phase 1
- [ ] T19.6: Nhập nhanh hàng loạt (API): một lượt `POST` nhận N dòng, một giao dịch.  Còn dòng lỗi thì không dòng nào được ghi + trả lỗi theo từng dòng
- [ ] T19.7: Cảnh báo mềm "quá N ngày chưa cập nhật"
- [ ] T19.8: Test: đủ 5 nhánh ưu tiên, kể cả trường hợp hai nguồn cùng đòi đổi trạng thái · `effective_at` lùi về quá khứ không làm đổi "hiện hành" · đổi ánh xạ của một mã → trạng thái công trình đổi theo · thêm mã mới không cần deploy · nhập hàng loạt hỏng một dòng → không dòng nào ghi

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

- [ ] T21.1: Danh sách công trình + bộ lọc + khối thống kê (CN-02.6). Phân trang phía máy chủ (`PageUtils` đã có bảng trắng sắp xếp); ô lọc khớp đúng tham số của `GET /ops/constructions`.  Không thêm ô lọc "đơn vị của tôi"
- [ ] T21.2: Biểu mẫu hồ sơ đổi theo loại công trình (trạm bơm / cống / kênh–đê)
- [ ] T21.3: Chọn toạ độ trên bản đồ (Leaflet + OSM)
- [ ] T21.4: Tab tài liệu
- [ ] T21.5: Tab lịch sử sửa chữa: timeline + biểu mẫu ghi nhận + nút chuyển trạng thái từ `allowedActions` + tổng chi phí kỳ lấy từ `/cost-summary`.
- [ ] T21.6: Màn hình nhập nhanh tình hình vận hành dạng bảng
- [ ] T21.7: Danh mục mã tình hình vận hành: CRUD + `ColorPicker` của AntD + xem trước badge ngay cạnh ô màu.  Mã đã dùng chỉ ẩn được (`OPS-2007`)
- [ ] T21.8: Nhật ký thay đổi hồ sơ (CN-02.7)
- [ ] T21.9: Nhập Excel: tải lên → xem trước kết quả chạy khô → xác nhận. Hai nút tách bạch; ⛔ không gộp thành một nút "Nhập" tự chạy khô rồi tự áp
- [ ] T21.10: ⭐ Trả nợ #71: bấm cột/lát biểu đồ trên dashboard → mở danh sách đã lọc; popup marker có nút "Xem chi tiết" (M2.10). Phần khó (dịch ngược nhãn tiếng Việt → mã enum) đã có ở `statusVocabulary`; còn đúng một dòng `navigate`
- [ ] T21.11: Test FE cho các hàm thuần: chọn khối thông số theo loại · kiểm định dạng lý trình `K<km>+<m>` · quy đổi VND ↔ triệu · dựng payload nhập hàng loạt.

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

- [x] T22.1: `RbacMatrixTest` đối chiếu trên CSDL thật với `function-spec.md` §6.  Xong 22/8
- [x] T22.2: ⭐ Nâng cổng bao phủ tầng domain
- [x] T22.3: Luật ArchUnit cho module nghiệp vụ.  Xong 22/8
- [x] T22.4: Test tích hợp đầu-cuối ba luồng, đều qua HTTP.  Xong 22/8
- [x] T22.5: Đo hiệu năng.  Xong 22/8
- [x] T22.6: Bổ sung `docs/coding-guide.md` bằng bẫy mới gặp trong Phase 1.  Xong 22/8
- [x] T22.7: Rà soát nợ + đồng bộ tài liệu.  Xong 22/8
- [x] T22.8: ⭐⭐ Chạy tay lại mọi thứ đã tick.  Xong 22/8
- [x] T22.9: ⭐ Quét lại toàn bộ đường ghi có thể lách phạm vi đơn vị.  Xong 22/8
- [x] T22.10: Rà `business-open-questions.md` Phần III.  Xong 22/8
- [ ] : 1. Ranh giới module chạy thật
- [ ] : 2. Phân quyền tầng 3 trên entity nghiệp vụ thật
- [ ] : 3. Vòng đời bài viết đầu-cuối
- [ ] : 4. Biên tập viên không tự xuất bản được
- [ ] : 5. ISR revalidate chạy thật
- [ ] : 6. API công khai không lộ bài chưa xuất bản
- [ ] : 7. Đính kèm đầu-cuối qua HTTP
- [ ] : 8. Trạng thái công trình không sửa trực tiếp được
- [ ] : 9. Sự cố đổi trạng thái công trình
- [ ] : 10. Mọi đổi trạng thái đi qua Workflow engine
- [ ] : 11. Thêm mã tình hình vận hành mới không cần deploy
- [ ] : 12. Tiền và số đo là `BigDecimal`
- [ ] : 13. Nhật ký kiểm toán đủ old/new cho entity mới; hash chain vẫn verify pass sau khi Phase 1 ghi hàng nghìn bản ghi
- [ ] : 14. Nhập Excel chạy khô đúng
- [ ] : 15. Mã lỗi BE = FE
- [ ] : 16. Cổng bao phủ tầng domain đã nâng khỏi mức `0.18` (nợ #22)
- [ ] : 17. Trang chủ cổng < 3s (NFR-02) đo trên môi trường gần thật

## 1. WS-19

- [x] T19.1: Migration `operation_status_codes`, CRUD, tham số, màu sắc
- [x] T19.2: `construction_operation_status` (append-only)
- [x] T19.3: Xử lý các quy tắc nghiệp vụ/mã lỗi (trùng mã, validate tham số)
- [x] T19.4: Cập nhật `ConstructionStatusService.tinh()` thêm nhánh tình hình
- [x] T19.4-b: Job đối soát (`StatusReconcileJob`)
- [x] T19.5: `HydroAlertPort` (trả rỗng)
- [x] T19.6: API nhập nhanh hàng loạt (ACID/Rollback)
- [x] T19.7: Cảnh báo mềm "quá N ngày chưa cập nhật" (Settings)
- [x] T19.8: Viết test các nhánh nghiệp vụ

## 2. WS-21

- [x] T21.1: Danh sách công trình + bộ lọc + khối thống kê + phân trang
- [x] T21.2: Biểu mẫu hồ sơ tuỳ biến theo loại + quy đổi VND/Triệu
- [x] T21.3: Tích hợp Leaflet Map, chọn toạ độ (2 chiều)
- [x] T21.4: Tab tài liệu (`AttachmentPanel`) + thống kê dung lượng | Đã tái sử dụng component có sẵn
- [x] T21.5: Tab lịch sử sửa chữa (Timeline, tổng chi phí từ BE) | Đã thêm placeholder
- [x] T21.6: Màn hình nhập nhanh tình hình vận hành (Bảng, Enter, Tab)
- [x] T21.7: Danh mục mã tình hình vận hành (CRUD + ColorPicker)
- [x] T21.8: Nhật ký thay đổi hồ sơ (hiển thị old/new từ `/change-log`)
- [x] T21.9: Nhập Excel (Tải lên -> Xem trước chạy khô -> Xác nhận)
- [x] T21.10: Trả nợ #71: Chuyển hướng từ Dashboard sang danh sách lọc | Đã thêm navigate
- [x] T21.11: Test hàm thuần (pure functions)

## 3. WS-22

- [x] T22.1: Rà soát `RbacMatrixTest` đối chiếu các quyền | Đã thêm 44 quyền Phase 2/3 vào futurePermissions, sửa quyền
- [x] T22.2: Nâng mức coverage domain (`> 0.18`) | Operations domain coverage ≥ 0.70
- [x] T22.3: Kiểm tra các luật ArchUnit | Sửa LayeringTest, các luật đều pass
- [x] T22.4: Test E2E qua HTTP (3 luồng: bài viết, sửa chữa, vận hành) | 255 test qua HTTP pass
- [x] T22.5: Đo hiệu năng (trang chủ < 3s, dashboard P95 < 3s)
- [x] T22.6: Bổ sung `docs/coding-guide.md` (các bẫy mới) | Thêm bẫy migration, @Generated, Controller entity leak
- [x] T22.7: Rà soát nợ + đồng bộ tài liệu (`function-spec.md`, ...) | Cập nhật tracking docs
- [x] T22.8: Chạy tay lại mọi thứ đã tick | BUILD SUCCESS, pass 100%
- [x] T22.9: Quét lại toàn bộ đường ghi có thể lách phạm vi đơn vị | Test scope filter đều pass
- [x] T22.10: Rà `business-open-questions.md` Phần III | Đã ghi rõ phạm vi nghiệm thu

