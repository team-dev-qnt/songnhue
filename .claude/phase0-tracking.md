# PHASE 0 — CORE PLATFORM · BẢNG THEO DÕI TIẾN ĐỘ

> **Cập nhật lần cuối**: 2026-08-13 · **Tiến độ: 0/107 task (0%)** · **DoD: 0/21** · Trạng thái: ⬜ Chưa bắt đầu
> Nguồn ràng buộc: `conventions.md` (coding/security) · `architecture-review.md` §6, §9 (kiến trúc đã chốt) · `function-spec.md` (nghiệp vụ MOD-05)
> **Cách dùng**: làm xong task nào tick `[x]` task đó; xong 1 WS thì chạy mục "Kiểm chứng" của WS rồi cập nhật bảng tổng + dòng "Cập nhật lần cuối" ở trên.

---

## Bảng tổng

| WS | Hạng mục | Task | Xong | Trạng thái | Phụ thuộc | Ước tính |
|---|---|:-:|:-:|---|---|:-:|
| **WS-1** | Repo & quy ước nền | 6 | 0 | ⬜ Chưa bắt đầu | — | 2 pd |
| **WS-2** | DB & Migration | 10 | 0 | ⬜ Chưa bắt đầu | WS-1 | 8 pd |
| **WS-3** | Docker & môi trường chạy local | 7 | 0 | ⬜ Chưa bắt đầu | WS-1 | 5 pd |
| **WS-4** | BE — Common Platform | 10 | 0 | ⬜ Chưa bắt đầu | WS-2 | 10 pd |
| **WS-5** | BE — Auth & RBAC 3 tầng | 14 | 0 | ⬜ Chưa bắt đầu | WS-4 | 15 pd |
| **WS-6** | BE — Core services | 15 | 0 | ⬜ Chưa bắt đầu | WS-4, WS-5 | 25 pd |
| **WS-7** | BE — Backup/Restore & Observability | 12 | 0 | ⬜ Chưa bắt đầu | WS-6 | 9 pd |
| **WS-8** | FE — admin-app | 11 | 0 | ⬜ Chưa bắt đầu | WS-4→6 (API) | 15 pd |
| **WS-9** | FE — public-web | 5 | 0 | ⬜ Chưa bắt đầu | WS-1 | 5 pd |
| **WS-10** | Test & CI | 7 | 0 | ⬜ Chưa bắt đầu | WS-4 | 10 pd |
| **WS-11** | Deploy Staging & Production | 10 | 0 | ⬜ Chưa bắt đầu | WS-3, 7, 10 | 10 pd |
| | **TỔNG** | **107** | **0** | | | **114 pd** |

*(107 task triển khai + 21 mục Definition of Done ở cuối file.)*

**Trạng thái**: ⬜ Chưa bắt đầu · 🟡 Đang làm · ✅ Xong · ⏸ Tạm dừng · ❌ Bỏ

### Sơ đồ phụ thuộc — làm tuần tự từng module

```
WS-1 ─► WS-2 ─► WS-3          [nền — bắt buộc trước mọi thứ]
   └─► WS-4 ─► WS-5 ─► WS-6   [BE lõi — tuần tự, không đảo được]
                  └─► WS-7
   └─► WS-8                    [FE — cần API của WS-4/5/6]
   └─► WS-9 · WS-10            [độc lập, chen vào lúc nào cũng được]
                  └─► WS-11    [cần WS-3 + WS-7 + WS-10]
```

> ⚠ **Hai việc nên làm ngay tuần 1 dù thường bị để cuối**: **T10.2 ArchUnit** (cài sau khi đã có code là gỡ rất đau) và **T7.11 khung cảnh báo dữ liệu quá hạn** (nguồn thủy văn không có API lịch sử — mất dữ liệu là vĩnh viễn).

### Ba ràng buộc thiết kế giữ xuyên suốt

Đây là cơ chế hấp thụ 6 mục nghiệp vụ còn mở (G3-a, G5, G6, G8, G9-a, G10) mà không phải sửa schema về sau:

1. **`settings` key-value có type** — mọi tham số chưa chốt đổ vào đây, không cần migration khi Công ty trả lời.
2. **Danh mục hóa thay vì enum cứng** — mức ngưỡng, mã tình hình vận hành, loại chỉ số đo đều là bảng có CRUD.
3. **App stateless tuyệt đối** — không giữ state trong memory; file luôn ở MinIO ngay từ v1.

---

## WS-1 — Repo & quy ước nền · 2 pd

**Tiên quyết**: không có. **Đầu ra**: repo build được, lint chạy được, `make` có đủ lệnh.

- [ ] **T1.1** Tạo cấu trúc monorepo + `.gitignore`, `.editorconfig`, `.gitattributes` — *layout: `conventions.md` §1.7*
- [ ] **T1.2** Maven parent `backend/pom.xml`: Java 21, Spring Boot 3.x BOM, 6 module con (`core/content/operations/hydro/hr/app`), `spring-boot-maven-plugin` ở `app/` — *§1.1*
- [ ] **T1.3** Spotless + Checkstyle (BE), ESLint + Prettier (FE) — chạy được ở local và CI — *§1.5*
- [ ] **T1.4** `.env.example` cho từng môi trường, liệt kê **đủ key**, không giá trị thật — *§1.6, cấm commit `.env`*
- [ ] **T1.5** `Makefile`: `dev-infra`, `dev-native`, `dev-docker`, `migrate`, `test`, `backup`, `restore` — *§1.7*
- [ ] **T1.6** Commit convention (Conventional Commits) + PR template gắn **Definition of Done** — *§1.5, §5*

**Kiểm chứng**: `./mvnw clean verify` xanh trên repo rỗng · `make` liệt kê đủ lệnh · lint chạy không lỗi cấu hình.

---

## WS-2 — DB & Migration · 8 pd

**Tiên quyết**: WS-1. **Đầu ra**: DB rỗng chạy migration ra đủ schema Core + seed + phân quyền role.

- [ ] **T2.1** Image `postgis/postgis:16-3.4`; bật extension `postgis`, `unaccent`, `pg_trgm` — *architecture §3*
- [ ] **T2.2** Flyway đa module: mỗi module `resources/db/migration/<prefix>/`, `app` gộp qua `spring.flyway.locations`. Bật `validateOnMigrate=true`, `outOfOrder=false`, **`cleanDisabled=true`** — *§1.2*
- [ ] **T2.3** Migration `core` — bảng nền: `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `org_units`, `sessions`, `token_denylist`, `user_totp`
- [ ] **T2.4** Migration `core` — nền tảng: `attachments`, `settings`, `jobs`, `notifications`, `notification_recipients`, `workflow_definitions`, `workflow_transitions`, `holidays`, `code_sequences`, `shedlock`, `security_events`
- [ ] **T2.5** Migration `core` — `audit_logs` **partition RANGE theo tháng** + cột `hash`/`prev_hash`; bảng `audit_archive_anchors` giữ điểm neo hash chain — *§4.3 + G7*
- [ ] **T2.6** Job tạo partition tháng kế tiếp (chạy trước hạn, **idempotent**) — tránh insert lỗi đầu tháng
- [ ] **T2.7** **DB roles tách quyền**: `songnhue_owner` (migrator) · `songnhue_app` (**không DELETE** trên `audit_logs`/`hydro_raw_logs`) · `songnhue_archiver` · `songnhue_readonly`. GRANT trong migration, CREATE ROLE ở init script — *§1.2, §4.3 "enforce ở tầng DB"*
- [ ] **T2.8** Cột chuẩn: `id BIGINT IDENTITY`, **`public_id UUID`**, `created_at/by`, `updated_at/by`, `deleted_at`, `version`; enum lưu `VARCHAR` + CHECK — *§1.2*
- [ ] **T2.9** Seed: org_units gốc, roles + permissions dịch từ ma trận RBAC `function-spec.md` §6, tài khoản Super Admin (bắt đổi mật khẩu + bắt buộc 2FA)
- [ ] **T2.10** Seed `settings` — các tham số bắt buộc theo `function-spec.md` CN-05.3 (giờ hành chính 08:00–17:00, retention 5 năm, cron polling `45 1/2 * * * *`, khung 10', ngưỡng mất tín hiệu 3 khung…) — *rule 12 CLAUDE.md*

**Kiểm chứng**: DB volume mới → `make migrate` → `flyway_schema_history` đủ version, không lỗi · `songnhue_app` thử `UPDATE audit_logs` → **bị DB từ chối**.

---

## WS-3 — Docker & môi trường chạy local · 5 pd

**Tiên quyết**: WS-1. **Đầu ra**: chạy được **cả 2 lối** — native và full Docker.

- [ ] **T3.1** `compose.infra.yml` — chỉ `postgres`(+PostGIS), `minio`(+`mc` tạo bucket), `mailhog`; **expose port ra host** để app chạy native từ IDE
- [ ] **T3.2** `compose.local.yml` — full stack: infra + `app` + `admin-app`(vite dev) + `public-web`(next dev), hot-reload qua bind mount
- [ ] **T3.3** `Dockerfile` backend: multi-stage (maven build → JRE 21 slim), **non-root user**, healthcheck
- [ ] **T3.4** `Dockerfile` admin-app (build → nginx static) và public-web (Next standalone output)
- [ ] **T3.5** Script init Postgres: extension + CREATE ROLE + database — *khớp T2.7*
- [ ] **T3.6** Profile Spring `local`/`docker`/`staging`/`prod` — **chỉ khác env, không khác code** — *§1.6*
- [ ] **T3.7** `make dev-infra` / `make dev-native` / `make dev-docker` + README hướng dẫn 2 lối chạy — *§1.7*

**Kiểm chứng**: `make dev-infra` + `./mvnw -pl app spring-boot:run` → health UP · `make dev-docker` → truy cập được admin-app + public-web + API cùng lúc.

---

## WS-4 — BE Common Platform · 10 pd

**Tiên quyết**: WS-2. **Đầu ra**: nền chung mà mọi module sau bắt buộc dùng, cấm tự chế bản riêng.

- [ ] **T4.1** `ApiResponse<T>`, `ApiError`, `ErrorDetail` + `ResponseEnvelopeAdvice` (ResponseBodyAdvice) — controller chỉ return DTO — *§2.1*
- [ ] **T4.2** `AppException` + 8 subclass đúng §2.2; `GlobalExceptionHandler` map toàn bộ; exception lạ → `SYS-0001`, **cấm lộ stacktrace/SQL** — *§2.2*
- [ ] **T4.3** `ErrorCode` enum sinh từ catalog §2.3 (26 mã) + `error-messages_vi.properties`; test đảm bảo mọi mã có message — *§2.3*
- [ ] **T4.4** Filter chain **đúng thứ tự**: `CorrelationFilter` → `RateLimitFilter` → `AuthFilter` → `ScopeContextFilter` → `AuditContextFilter` — *§2.4*
- [ ] **T4.5** `RateLimitFilter` qua interface `RateLimitStore` (impl Caffeine in-process) — login 5/15', API 100/phút, export 10/giờ — *§4.5; ≥2 node phải đổi impl sang DB*
- [ ] **T4.6** 8 utils: `DateTimeUtils`, `NumericUtils`, `SlugUtils/VietnameseUtils`, `CodeGenerator`(DB sequence), `MaskUtils`, `PageUtils`(whitelist sort), `FileValidator`(magic bytes), `CryptoService`(AES-256-GCM + `key_id`) — *§2.5, cấm module viết lại*
- [ ] **T4.7** `BaseEntity` / `ScopedEntity` + JPA auditing + soft delete + `@Version` — *§2.5*
- [ ] **T4.8** `@ConfigurationProperties` + `@Validated` cho mọi nhóm config → **fail-fast lúc startup** khi thiếu env — *§1.6*
- [ ] **T4.9** Log JSON + `traceId` trong MDC; `LoggingInterceptor` (method/path/status/duration, **không log body nhạy cảm**) — *§2.4, §4.5*
- [ ] **T4.10** springdoc-openapi: `/api/v1/**`, group theo module — *§1.3*

**Kiểm chứng**: mọi response (kể cả lỗi) đúng envelope §2.1 và luôn có `traceId` · xóa 1 env bắt buộc → app **không khởi động**, log chỉ rõ key thiếu.

---

## WS-5 — BE Auth & RBAC 3 tầng · 15 pd

**Tiên quyết**: WS-4. **Đầu ra**: xác thực + phân quyền đủ mạnh, có 2 chốt chặn ở CI. **Đây là nơi dồn công của Phase 0** (`architecture-review.md` §9.4).

- [ ] **T5.1** JWT **RS256**, keypair đọc từ file/env, `kid` trong header để xoay key; access token 30' — *§4.1*
- [ ] **T5.2** Refresh token rotation lưu **httpOnly + Secure + SameSite=Strict cookie**; token family — *§4.1*
- [ ] **T5.3** **Refresh reuse detection** → thu hồi cả family + force re-login + security event — *§4.1*
- [ ] **T5.4** `token_denylist` bảng DB; đổi mật khẩu / khóa tài khoản → denylist toàn bộ token đang sống — *§4.1*
- [ ] **T5.5** CSRF double-submit (`X-CSRF-Token`) cho mọi request thay đổi dữ liệu — *§4.1*
- [ ] **T5.6** Login lockout 5 lần/15' → `AUTH-0003`; message chung `AUTH-0001` **không tiết lộ user có tồn tại** — *§4.1*
- [ ] **T5.7** BCrypt cost ≥ 12; policy ≥10 ký tự chữ+số; bắt đổi mật khẩu lần đầu — *§4.1*
- [ ] **T5.8** **2FA TOTP bắt buộc Admin + Admin HR** (enroll, QR, verify, recovery code) — *§4.1 + G12*
- [ ] **T5.9** Tầng 2 — `@RequirePermission("module:resource:action")` + interceptor — *§4.2*
- [ ] **T5.10** **Deny by default**: test CI quét toàn bộ controller method, thiếu annotation → **CI fail** — *§4.2*
- [ ] **T5.11** Tầng 3 — Hibernate `@Filter` scope `org_unit` (+ cây con) tự áp cho `ScopedEntity`; vi phạm → `AUTH-3002` — *§4.2*
- [ ] **T5.12** Mọi lookup qua `public_id` UUID, **cấm `findById` trần** cho request người dùng — *§4.2 chống IDOR*
- [ ] **T5.13** Quản lý phiên (M5.14): danh sách phiên + **đăng xuất từ xa** — *CN-05.7*
- [ ] **T5.14** Cảnh báo đăng nhập bất thường (M5.16): sai nhiều lần, ngoài **giờ hành chính đọc từ `settings`** → near-real-time — *CN-05.7, F5*

**Kiểm chứng**: đúng quyền → 200 · thiếu permission → 403 `AUTH-3001` · ngoài đơn vị → 403 `AUTH-3002` · dùng lại refresh token cũ → thu hồi family + security event · thêm endpoint không có `@RequirePermission` → **CI đỏ**.

---

## WS-6 — BE Core services · 25 pd

**Tiên quyết**: WS-4, WS-5. **Đầu ra**: 6 pattern P1–P6 thành shared service; module nghiệp vụ Phase 1+ chỉ khai báo cấu hình.

- [ ] **T6.1** `org_units` — cây ≥5 cấp, materialized path + `sort_order`, API move/reorder; **1 bảng dùng chung XN + phòng ban** — *rule 7 CLAUDE.md*
- [ ] **T6.2** Tree helper (P2) tái sử dụng cho danh mục/media/công trình/menu
- [ ] **T6.3** Attachment service (P3): bảng polymorphic, upload MinIO, versioning, `valid_until`, presigned URL TTL ngắn — *§4.3*
- [ ] **T6.4** `FileValidator`: magic bytes + size theo config + tên file random; ảnh re-encode strip EXIF; **ClamAV scan async** trước khi chuyển "sẵn sàng" — *§4.4*
- [ ] **T6.5** **Workflow engine (P1)**: `workflow_definitions` + `transitions`, check `(from, action, role)` trong transaction, hook notify + audit. **Nơi duy nhất đổi trạng thái** — *§4.3, rule 4*
- [ ] **T6.6** Notification service (P4): `notify(event, targets, channels)`; **v1 bật in-app + email**, `SmsSender` interface mặc định tắt — *B7*
- [ ] **T6.7** **Recipient resolver theo G11**: nhóm "Ban điều hành" từ `settings` ∪ người đứng đầu/phó `org_units` của công trình liên quan; khử trùng lặp; loại tài khoản khóa — *G11*
- [ ] **T6.8** Job & Scheduler (P5): `jobs` + **SKIP LOCKED**, worker in-process bounded pool, trạng thái + retry 3, **chống overlapping run** — *§6.3*
- [ ] **T6.9** ShedLock cài sẵn, `shedlock.enabled` đọc env, mặc định tắt (1 node) — *§6.2*
- [ ] **T6.10** Async job API chuẩn: `POST → 202 + jobId`, endpoint tra tiến độ, link tải TTL 24h — *§1.3*
- [ ] **T6.11** Settings service: key-value **có type** + validate + Caffeine cache + UI API; **export/import cấu hình loại trừ credential** — *CN-05.3, §4.7*
- [ ] **T6.12** Audit interceptor: ghi old/new JSON; **append-only + hash chain SHA-256(record + prev_hash)**; API verify chain — *§4.3*
- [ ] **T6.13** Job kết xuất audit >5 năm: CSV/Parquet nén → MinIO bucket riêng → **verify checksum** → mới xóa; ghi anchor hash; lỗi → không xóa dòng nào + `ADM-2001` — *G7, §4.3*
- [ ] **T6.14** Thông báo hệ thống (M5.13): Admin gửi thông báo chung tới toàn bộ/nhóm — *CN-05.6*
- [ ] **T6.15** **Vertical slice**: CRUD `users` + `roles` đi hết 3 tầng quyền + audit + notification — *nghiệm thu Phase 0*

**Kiểm chứng**: tạo/sửa/xóa → `audit_logs` có bản ghi, verify chain pass · upload sai magic bytes → từ chối · `POST` job → 202 + `jobId` → worker chạy → notification in-app + email (MailHog).

---

## WS-7 — BE Backup/Restore & Observability · 9 pd

**Tiên quyết**: WS-6. **Đầu ra**: backup chạy tự động, restore được, có giám sát.
📌 **Backup là bản tối giản** — `architecture-review.md` §6.5: `pg_dump` hàng đêm, **RPO ≤ 24h · RTO ≤ 4h**, **không PITR/WAL/replica**, chấp nhận 4 rủi ro đã ghi.

### 7a. Backup

- [ ] **T7.1** **`pg_dump -Fc` hàng đêm ~02:00** → nén → **checksum SHA-256** → prune > 30 ngày. *Đây là toàn bộ cơ chế backup*
- [ ] **T7.2** Copy bản dump sang **VM-3 (khác máy với DB)**; **key AES + JWT signing key lưu tách, KHÔNG nằm trong bản backup** — *§6.5*
- [ ] **T7.3** **Một alert duy nhất**: `backup_last_success_timestamp` — bắn khi bản gần nhất **quá 26 giờ**
- [ ] **T7.4** Backup theo yêu cầu (M5.10) + hiển thị trạng thái backup gần nhất trên UI — *CN-05.5*

### 7b. Restore & vận hành

- [ ] **T7.5** **Restore qua UI (M5.11)**: chỉ Super Admin + 2FA, xác nhận nhiều bước (gõ tên hệ thống + lý do), async có tiến độ, security event + audit. Khôi phục từ bản dump đêm — *§7.3*
- [ ] **T7.6** **Maintenance mode**: flag `settings` + filter chặn ghi (503) trong lúc restore, trừ Super Admin — *§7.3*
- [ ] **T7.7** **Diễn tập restore 1 lần trước go-live** trên VM-2 + ghi con số RTO thật vào runbook; sau đó theo quý (thủ công, có checklist)
- [ ] **T7.8** Health-check (M5.12): actuator + indicator cho DB, MinIO, SMTP, **telemetry (stub Phase 2)** — *CN-05.6*
- [ ] **T7.9** Micrometer → Prometheus + Grafana **đặt trên VM-3** (sống sót khi VM production chết); log JSON rotation 30 ngày — *§2.4*
- [ ] **T7.10** Security event stream riêng (login fail, refresh reuse, 403 scope, đổi quyền, truy cập credential) → Grafana + alert — *§4.5, §4.7*
- [ ] **T7.11** ⚠ **Khung cảnh báo "dữ liệu quá hạn"**: gauge `data_freshness_seconds{source}` + alert rule mẫu — Phase 2 đăng ký nguồn hydro — *làm sớm, mất dữ liệu thủy văn là vĩnh viễn*
- [ ] **T7.12** Runbook `docs/runbook/`: restore từ dump, xoay key AES/JWT, poller chết, retry job Failed — *§2.4*

**Kiểm chứng**: `make backup` → dump + checksum khớp, **nằm trên VM-3** · dừng job dump quá 26h → alert bắn · giải nén backup, `grep` **không** thấy AES/JWT key · restore lên VM-2 đo được RTO **< 4 giờ**.

---

## WS-8 — FE admin-app · 15 pd

**Tiên quyết**: API của WS-4/5/6 (bám dần, không cần chờ xong hết). **Đầu ra**: SPA quản trị chạy được với đủ màn hình MOD-05.

- [ ] **T8.1** Vite + React 18 + TS **strict, cấm `any`** + AntD 5 + TanStack Query + React Router; cấu trúc `shared/ components/ features/ app/` — *§1.4*
- [ ] **T8.2** `shared/tokens.ts` — **design tokens 1 nguồn**: màu trạng thái xanh/vàng/đỏ/xám/đen → AntD theme + ECharts theme (+ Tailwind config public-web) — *§3, architecture §4*
- [ ] **T8.3** `shared/apiClient` — axios instance **duy nhất**: gắn CSRF header, **auto refresh 1 lần rồi logout**, unwrap envelope, error → notification — *§2.5*
- [ ] **T8.4** `shared/error-map.ts` mirror catalog BE (fallback dùng message từ API) — *§2.3*
- [ ] **T8.5** `useAuth`, `usePermission(code)`, route guard — **chỉ để UX, không phải bảo mật** — *§4.2 tầng 1*
- [ ] **T8.6** Màn hình auth: login, **2FA TOTP** (enroll + verify), đổi mật khẩu bắt buộc lần đầu, quên mật khẩu
- [ ] **T8.7** Layout + menu render theo permission; trang 403/404/500 hiển thị `traceId`
- [ ] **T8.8** 7 component nghiệp vụ: `StatusBadge`, `ThresholdValue`, `ApprovalActions` (render từ `allowedActions` API), `OrgUnitTreeSelect`, `AttachmentPanel`, `DateRangeFilter`, `ExportButton` — *§3, architecture §4*
- [ ] **T8.9** `formatDateTime` **UTC+7 `dd/MM/yyyy HH:mm`**, `formatNumber` kiểu VN — *§3*
- [ ] **T8.10** Màn hình quản trị: tài khoản, vai trò/phân quyền, sơ đồ đơn vị, cấu hình hệ thống, audit log + verify chain, phiên đăng nhập, backup/restore — *MOD-05*
- [ ] **T8.11** Bảng dữ liệu chuẩn: phân trang server-side, empty state, loading skeleton — *§3*

**Kiểm chứng**: login → 2FA → vào được dashboard · menu ẩn đúng theo permission · restore UI **không hiện** với non-Super-Admin.

---

## WS-9 — FE public-web · 5 pd

**Tiên quyết**: WS-1. **Đầu ra**: khung Next.js sẵn cho Phase 1 cắm CMS vào.

- [ ] **T9.1** Next.js + Tailwind + TS strict; import **tokens dùng chung** với admin-app
- [ ] **T9.2** Layout công khai + trang chủ tạm + trang 404/500
- [ ] **T9.3** SEO base: metadata/Open Graph, `sitemap.xml`, `robots.txt`
- [ ] **T9.4** Scaffold ISR + `revalidate` hook (Phase 1 CMS cắm vào)
- [ ] **T9.5** Health route + Dockerfile standalone

**Kiểm chứng**: `make dev-docker` → public-web render được, Lighthouse SEO không lỗi cấu hình cơ bản.

---

## WS-10 — Test & CI · 10 pd

**Tiên quyết**: WS-4 (nhưng **T10.2 nên làm ngay sau WS-1**). **Đầu ra**: CI chặn được vi phạm kiến trúc và quyền.

- [ ] **T10.1** Testcontainers **PostgreSQL + PostGIS** làm nền cho integration test — *architecture §5*
- [ ] **T10.2** ⚠ **ArchUnit** — chặn: module chỉ import `spi/` của module khác · entity không ra khỏi application · `@Transactional` chỉ ở application · **cấm `float/double`** cho số đo/tiền · cấm `new Date()` · cấm `System.out` — *§1.1, rule 6 CLAUDE.md — **cài từ commit đầu***
- [ ] **T10.3** Harness **ma trận RBAC role × resource** (NFR-06 yêu cầu pass 100%) — *§4.2*
- [ ] **T10.4** Test deny-by-default (T5.10) + test hash chain audit + test `CryptoService` xoay key
- [ ] **T10.5** Coverage gate domain layer — không merge nếu giảm — *§1.5*
- [ ] **T10.6** `ci.yml`: build → Spotless/Checkstyle → unit → Testcontainers → ArchUnit → ESLint → **OWASP Dependency-Check + `npm audit`, fail ở CVE high/critical** — *§4.5*
- [ ] **T10.7** Branch protection: 1 reviewer + CI xanh mới merge — *§1.5*

**Kiểm chứng**: cố tình import repository module khác → **test đỏ** · push PR → toàn bộ pipeline xanh.

---

## WS-11 — Deploy Staging & Production · 10 pd

**Tiên quyết**: WS-3, WS-7, WS-10. **Đầu ra**: 2 môi trường deploy tự động, có rollback.
📌 Phân bổ VM (`architecture-review.md` §9.2): **VM-1** Production · **VM-2** Staging (+ đích diễn tập restore) · **VM-3** Backup & Monitoring.

- [ ] **T11.1** Build & push image lên **GHCR**, tag theo commit SHA + semver
- [ ] **T11.2** Dựng 3 VM theo phân bổ trên; `compose.backup.yml` cho VM-3 (kho dump + Prometheus/Grafana)
- [ ] **T11.3** `compose.staging.yml` / `compose.prod.yml`: `nginx` + `app` + `postgres` + `minio` + `backup-agent`
- [ ] **T11.4** **Migration là service riêng `migrator`** chạy trước (`depends_on: service_completed_successfully`), app khởi động với `flyway.enabled=false` — *§9.2, migration hỏng thì app không lên nửa vời*
- [ ] **T11.5** **Tự động `pg_dump` trước mỗi lần deploy** production, giữ riêng khỏi bản đêm — *điểm rollback dữ liệu duy nhất vì không có PITR*
- [ ] **T11.6** Nginx: TLS 1.3, **HSTS, CSP, `X-Frame-Options: DENY`, `Referrer-Policy`**, ẩn version, rate limit theo IP, giới hạn body size route upload — *§4.5*
- [ ] **T11.7** Secrets: **GitHub Secrets** cho CI; `/opt/songnhue/.env` (chmod 600) trên VM; key AES/JWT ở `/opt/songnhue/keys/` **ngoài backup DB** — *§9.3*
- [ ] **T11.8** `deploy-staging.yml` tự động khi merge `master`; `deploy-prod.yml` chạy tay/theo tag, có approval
- [ ] **T11.9** Quy trình rollback: quay lại image tag trước; migration đã đổi schema → restore từ **bản dump pre-deploy**. Mỗi migration đổi schema phải kèm ghi chú rollback trong PR
- [ ] **T11.10** Smoke test sau deploy: health, login, 1 endpoint có quyền, kiểm tra backup gần nhất. Chốt `app.nodes`/`worker.enabled`/`shedlock.enabled` **đọc từ env** — *§6.4*

**Kiểm chứng**: merge `master` → tự deploy staging → smoke test pass · quay về image tag trước → hệ thống chạy lại bình thường.

---

## DEFINITION OF DONE — PHASE 0

Chạy tuần tự, tất cả phải xanh mới coi là Phase 0 hoàn thành:

- [ ] **1. Chạy native** — `make dev-infra` → `./mvnw -pl app spring-boot:run` → `GET /actuator/health` = UP
- [ ] **2. Chạy full Docker** — `make dev-docker` → admin-app + public-web + API cùng lúc
- [ ] **3. Fail-fast thiếu env** — xóa 1 biến bắt buộc → app **không khởi động**, log chỉ rõ key thiếu
- [ ] **4. Migration sạch từ DB rỗng** — `flyway_schema_history` đủ version, không lỗi
- [ ] **5. Auth + 2FA** — login Super Admin → bắt đổi mật khẩu → enroll TOTP → nhận access + refresh cookie
- [ ] **6. Refresh reuse detection** — dùng lại refresh token cũ → thu hồi family + security event
- [ ] **7. RBAC 3 tầng** — đúng quyền → 200 · thiếu permission → 403 `AUTH-3001` · ngoài đơn vị → 403 `AUTH-3002`
- [ ] **8. Deny by default** — endpoint không có `@RequirePermission` → **CI fail**
- [ ] **9. Envelope + traceId** — mọi response (kể cả lỗi) đúng §2.1, luôn có `traceId`
- [ ] **10. Audit hash chain** — verify chain pass; `songnhue_app` thử `UPDATE audit_logs` → **bị DB từ chối**
- [ ] **11. Attachment** — upload → MinIO; sai magic bytes → từ chối; presigned URL hết hạn đúng TTL
- [ ] **12. Async job** — `POST` → 202 + `jobId` → worker chạy → notification in-app + email (MailHog)
- [ ] **13. Backup** — dump + checksum khớp, **nằm trên VM-3, không cùng máy DB**; prune giữ đúng 30 ngày
- [ ] **14. Đo RTO thật** — restore lên VM-2 → so số bản ghi → **< 4 giờ**; ghi con số thật vào runbook
- [ ] **15. Alert backup hỏng** — dừng job dump quá 26h → alert bắn
- [ ] **16. Key không nằm trong backup** — giải nén bản backup, `grep` không thấy AES/JWT key
- [ ] **17. Restore UI** — non-Super-Admin không thấy chức năng; Super Admin phải qua 2FA + gõ tên hệ thống; trong lúc restore mọi request ghi trả 503
- [ ] **18. ArchUnit** — cố tình import repository module khác → **test đỏ**
- [ ] **19. CI đầy đủ** — build, lint, unit, Testcontainers, ArchUnit, CVE scan đều xanh
- [ ] **20. Deploy Staging** — merge `master` → tự deploy → smoke test pass; `migrator` chạy trước app
- [ ] **21. Rollback** — quay về image tag trước → hệ thống chạy lại bình thường

---

## Nhật ký thay đổi kế hoạch

| Ngày | Nội dung |
|---|---|
| 2026-08-13 | Lập kế hoạch Phase 0. Chốt Maven multi-module · monorepo · deploy compose 3 VM · secrets env+GitHub Secrets · migration service riêng · DB roles tách quyền. **Backup hạ xuống bản tối giản** (RPO 24h, RTO 4h, không PITR/replica) — đồng bộ ngược vào `function-spec.md`, `architecture-review.md` §6.5/§9, `conventions.md` §1.2/§1.7. |
