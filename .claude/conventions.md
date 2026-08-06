# CONVENTIONS & COMMON PLATFORM — CODING · DESIGN · SECURITY

> Áp dụng cho toàn codebase. Bổ sung cho `architecture-review.md` (tech đã chốt) và `implement.md` (Nhóm A Core).
> Nguyên tắc chung: **mọi thứ lặp ≥ 2 lần phải nằm trong Core/shared — module nghiệp vụ không tự chế bản riêng.**

---

## 1. CODEBASE & NAMING CONVENTIONS

### 1.1. Backend (Java / Spring Boot)

**Cấu trúc package trong mỗi module** (core, content, operations, hydro, hr):

```
com.songnhue.<module>/
├── api/            # Controller + Request/Response DTO (record) — KHÔNG chứa logic
├── application/    # Service (use-case), orchestration, transaction boundary
├── domain/         # Entity, Value Object, domain service, business rule, validation
├── infra/          # Repository impl, client API ngoài, adapter (mock/real)
└── spi/            # Service interface public cho module khác gọi (duy nhất được import chéo)
```

Quy tắc:
- Module khác **chỉ được import `spi/`** — ArchUnit test chặn trong CI (đã chốt).
- Entity không bao giờ ra khỏi tầng application — controller chỉ nhận/trả DTO (Java `record`), map bằng MapStruct.
- `@Transactional` chỉ đặt ở application service, không ở controller/repository.
- Naming: `XxxController`, `XxxService` (interface) / `XxxServiceImpl`, `XxxRepository`, DTO: `XxxRequest` / `XxxResponse` / `XxxDto`.
- Cấm: `float/double` cho số đo/tiền (dùng `BigDecimal`), `new Date()` (dùng `Instant`/`OffsetDateTime`), catch nuốt exception, `System.out`.

### 1.2. Database (PostgreSQL)

- Tên bảng: `snake_case`, số nhiều (`constructions`, `operation_logs`); bảng nối: `article_categories`.
- Cột: `snake_case`; khóa chính `id BIGINT GENERATED ALWAYS AS IDENTITY`; **thêm `public_id UUID` cho mọi entity expose ra API** (chống đoán ID tuần tự — IDOR).
- Cột chuẩn mọi bảng nghiệp vụ (BaseEntity): `created_at timestamptz`, `created_by`, `updated_at`, `updated_by`, `deleted_at` (soft delete), `version int` (optimistic lock).
- FK: `<bảng_số_ít>_id` (`org_unit_id`); index: `ix_<bảng>_<cột>`; unique: `uq_<bảng>_<cột>`; check: `ck_<bảng>_<rule>`.
- Enum nghiệp vụ: lưu `VARCHAR` + CHECK constraint (không dùng Postgres enum type — khó migrate).
- Migration Flyway: `V<yyyyMMddHHmm>__<module>_<mô_tả>.sql` (VD `V202607201030__ops_create_constructions.sql`). Cấm sửa migration đã merge — chỉ thêm mới. Prefix `<module>`: `core`/`cms`/`ops`/`hyd`/`hr`.

### 1.3. REST API

- Base path: `/api/v1/<module>/<resource>` — version ngay từ đầu. VD: `/api/v1/ops/constructions/{publicId}/documents`.
- Resource danh từ số nhiều, kebab-case: `/operation-logs`, `/leave-requests`.
- Action ngoài CRUD dùng sub-resource động từ: `POST /operation-logs/{id}/submit`, `/approve`, `/reject` (map vào Workflow engine).
- Query chuẩn: `?page=1&size=20&sort=createdAt,desc&filter[status]=APPROVED` — sort field **whitelist** (mục 4.4).
- HTTP status: 200/201/204 thành công; 202 cho async job (trả `jobId`); lỗi theo bảng mục 2.3.

### 1.4. Frontend (TypeScript / React)

```
admin-app/src/
├── shared/         # api client, hooks, utils, constants, error-map — mirror của Core BE
├── components/     # UI thuần + components/business (StatusBadge, ApprovalActions...)
├── features/<module>/<feature>/   # page + components + hooks + api riêng của feature
└── app/            # router, layout, providers, permission guard
```

- TypeScript strict, cấm `any` (dùng `unknown` + narrow); server state qua TanStack Query (không tự quản bằng useState/Redux); form: AntD Form + zod schema (schema validation dùng lại được giữa các form).
- Naming: component `PascalCase`, hook `useXxx`, file component `.tsx` trùng tên component.
- FE **không tính toán nghiệp vụ, không tự quyết quyền** — permission chỉ để ẩn/hiện UI (mục 5.2), giá trị tính toán luôn lấy từ API.

### 1.5. Git & CI

- Branch: `feat/<module>-<mô-tả>`, `fix/…`, `chore/…`; commit theo Conventional Commits (`feat(ops): thêm alert engine`).
- PR bắt buộc: 1 reviewer, CI xanh (unit + integration Testcontainers + ArchUnit + lint), không merge khi coverage domain layer giảm.
- Cấm commit: secrets, file config môi trường thật, `.env` (dùng `.env.example`).

### 1.6. Cấu hình & kết nối — bắt buộc qua env

- **Mọi connection và setup (DB, MinIO, SMTP, SMS, telemetry API, Google Maps key, base URL...) phải đọc từ biến môi trường / file env — cấm hardcode trong code hoặc `application.yml` commit lên repo.**
- BE: `application.yml` chỉ chứa placeholder `${DB_URL}`, `${REDIS_HOST}`...; giá trị thật nằm ở env theo môi trường (Dev/Staging/Prod). FE: qua `import.meta.env.VITE_*` / `process.env.NEXT_PUBLIC_*`, build-time inject.
- Mỗi repo có `.env.example` liệt kê đầy đủ key (không giá trị thật) — thêm config mới phải cập nhật file này, thiếu env bắt buộc → app **fail-fast lúc startup** (validate bằng `@ConfigurationProperties` + `@Validated`), không chạy với default ngầm.
- Cấm tạo connection trực tiếp trong code nghiệp vụ (`new RestTemplate(url)`, `DriverManager.getConnection`...) — mọi client (DB, HTTP, S3) khởi tạo 1 lần qua Spring bean cấu hình từ env, module nghiệp vụ chỉ inject.

---

## 2. COMMON PLATFORM — API RESPONSE, EXCEPTION, ERROR CODE

Nằm trong `core/` (BE) và `shared/` (FE). **Mọi module bắt buộc dùng, cấm tự chế envelope/exception riêng.**

### 2.1. API Response envelope (thống nhất 100% endpoint)

```json
// Thành công
{ "success": true, "data": { ... }, "meta": { "page": 1, "size": 20, "totalElements": 134, "totalPages": 7 }, "traceId": "a1b2c3" }

// Lỗi
{ "success": false, "error": { "code": "OPS-2003", "message": "Lưu lượng vượt 120% thiết kế", "details": [ { "field": "actualFlow", "rule": "MAX_120_PERCENT_DESIGN", "rejectedValue": "9.99" } ] }, "traceId": "a1b2c3" }
```

- `traceId` = correlation-id, luôn có — user báo lỗi chỉ cần đọc traceId là dev tra được log.
- `meta` chỉ xuất hiện với response phân trang. Envelope build tự động qua `ResponseBodyAdvice` — controller chỉ return DTO.

### 2.2. Exception hierarchy (Core)

```
AppException (abstract — mang ErrorCode, args cho message template)
├── ValidationException      → 400  (input sai format/thiếu)
├── BusinessRuleException    → 422  (vi phạm rule nghiệp vụ: vượt ngưỡng, sai trạng thái workflow...)
├── ResourceNotFoundException→ 404
├── PermissionDeniedException→ 403  (fail RBAC/scope)
├── AuthenticationException  → 401
├── ConflictException        → 409  (optimistic lock, trùng unique)
├── RateLimitException       → 429
└── UpstreamException        → 502  (telemetry API, SMTP, SMS lỗi)
```

- **Một** `GlobalExceptionHandler` (`@RestControllerAdvice`) trong Core map toàn bộ về envelope. Exception ngoài danh sách → 500 + code `SYS-0001`, **không bao giờ** trả stacktrace/SQL/message kỹ thuật ra ngoài; chi tiết chỉ ghi log kèm traceId.
- Module nghiệp vụ chỉ được throw các exception trên (hoặc subclass), không throw `RuntimeException` trần.

### 2.3. Error code catalog

Format: `<PREFIX>-<4 số>` — prefix theo module: `SYS` (hệ thống), `AUTH`, `CMS`, `OPS` (vận hành công trình), `HYD` (thủy văn — MOD-03), `HR`, `ADM`. Dải số: 0xxx hệ thống/chung, 1xxx not-found/conflict, 2xxx business rule, 3xxx permission/scope.

| Code | HTTP | Message (vi) |
|---|---|---|
| SYS-0001 | 500 | Lỗi hệ thống, vui lòng thử lại. Mã: {traceId} |
| SYS-0002 | 429 | Thao tác quá nhanh, vui lòng thử lại sau |
| AUTH-0001 | 401 | Sai tên đăng nhập hoặc mật khẩu |
| AUTH-0002 | 401 | Phiên đăng nhập hết hạn |
| AUTH-0003 | 423 | Tài khoản tạm khóa do đăng nhập sai nhiều lần |
| AUTH-3001 | 403 | Không có quyền thực hiện thao tác này |
| AUTH-3002 | 403 | Dữ liệu không thuộc phạm vi đơn vị của bạn |
| OPS-2001 | 422 | Chỉ được nhập bù tối đa 3 ngày trước 🔷 |
| OPS-2003 | 422 | Lưu lượng vượt 120% thiết kế — cần xác nhận 🔷 |
| HYD-1001 | 404 | Điểm đo chưa ánh xạ nguồn API bên thứ 3 |
| HYD-2001 | 422 | Giá trị đo ngoài khoảng vật lý cho phép |
| CMS-2001 | 422 | Slug đã tồn tại |
| HR-2001 | 422 | Số ngày đăng ký vượt số phép còn lại |

- Message tiếng Việt tập trung 1 file `error-messages_vi.properties` (BE) — FE có bản mirror `shared/error-map.ts` (fallback dùng message từ API). Thêm code mới = thêm vào catalog, có review; cấm hardcode message trong controller/service.
- Workflow engine trả lỗi transition không hợp lệ bằng code chung `<MOD>-2xxx` + details `{from, action, allowedActions}`.

### 2.4. Middleware / Filter chain (thứ tự cố định)

```
Request → [1] CorrelationFilter (sinh/nhận traceId, MDC cho log)
        → [2] RateLimitFilter (bucket theo IP + user; login có bucket riêng)
        → [3] AuthFilter (verify access token, check denylist ở bảng DB)
        → [4] ScopeContextFilter (load user → role, permissions, org_unit vào SecurityContext)
        → [5] AuditContextFilter (gắn user/traceId cho audit interceptor)
        → Controller → Service → Repository (scope filter tự áp — mục 5.3)
Response ← GlobalExceptionHandler / ResponseBodyAdvice (envelope) ← LoggingInterceptor (method, path, status, duration — KHÔNG log body chứa dữ liệu nhạy cảm)
```

### 2.5. Utils dùng chung (Core `common/util` — cấm viết lại trong module)

| Util | Nội dung |
|---|---|
| `DateTimeUtils` | UTC ⇄ UTC+7; tính độ dài ca (ngày/đêm qua nửa đêm); ngày làm việc (trừ cuối tuần + bảng `holidays`) |
| `NumericUtils` | BigDecimal: scale/rounding chuẩn (đo lường scale 3 HALF_UP, tiền scale 0, % scale 2); so sánh an toàn; Σ null-safe |
| `SlugUtils` / `VietnameseUtils` | Bỏ dấu tiếng Việt (slug, họ tên không dấu cho search) — 1 implementation duy nhất |
| `CodeGenerator` | Sinh mã nghiệp vụ `SC-2026-0001`, `NV-2026-001`… bằng DB sequence theo (loại, năm) — an toàn concurrent, không MAX()+1 |
| `MaskUtils` | Mask dữ liệu nhạy cảm cho log/UI: CCCD `0123****789`, SĐT, STK |
| `PageUtils` | Chuẩn hóa page/size (size ≤ 100), parse sort + đối chiếu whitelist |
| `FileValidator` | Check magic bytes (không tin extension), size theo config từng loại, tên file random hóa |
| `CryptoService` | AES-256-GCM encrypt/decrypt cột nhạy cảm; key từ env/Vault; hỗ trợ key rotation (key_id trong ciphertext) |

Base classes: `BaseEntity` (audit cột chuẩn + soft delete + version), `ScopedEntity extends BaseEntity` (+ `org_unit_id` — mọi entity thuộc phạm vi đơn vị bắt buộc kế thừa).

FE mirror (`shared/`): `apiClient` (axios instance duy nhất: gắn CSRF header, auto refresh token 1 lần rồi logout, unwrap envelope, error → notification theo `error-map`), `useAuth`, `usePermission(code)`, `formatDateTime` (UTC+7), `formatNumber` (hiển thị số đo/tiền thống nhất).

---

## 3. DESIGN CONVENTIONS (FE)

- Design tokens (`shared/tokens.ts`): màu trạng thái hệ thống (xanh `#52c41a` / vàng `#faad14` / đỏ `#f5222d` / xám `#8c8c8c` / đen) — AntD theme, Tailwind config (public web), ECharts theme cùng import từ 1 nguồn.
- Mọi trạng thái hiển thị qua `StatusBadge` (nhận enum → màu + label vi) — cấm hardcode màu/label tại page.
- Số liệu đo lường hiển thị qua `ThresholdValue` (tự đổi màu theo ngưỡng trả về từ API — ngưỡng do BE cung cấp, FE không giữ bản sao rule).
- Nút thao tác workflow luôn render từ `allowedActions` API trả về (`ApprovalActions`) — không tự suy quyền ở FE.
- Form: label tiếng Việt, message validation lấy từ zod schema dùng chung; trường bắt buộc theo spec, đánh dấu `*`.
- Bảng dữ liệu: luôn có phân trang server-side, empty state, loading skeleton; export qua `ExportButton` (async job).
- Datetime hiển thị `dd/MM/yyyy HH:mm` (UTC+7); số: dấu phẩy ngăn nghìn kiểu VN.

---

## 4. SECURITY — CHỐNG TẤN CÔNG & GIẢ MẠO

### 4.1. Authentication (chống chiếm phiên)

- Access token 30' (JWT ký RS256 — key riêng cho ký, xoay được) + Refresh token rotation lưu **httpOnly + Secure + SameSite=Strict cookie**.
- **Refresh reuse detection**: refresh token cũ bị dùng lại → thu hồi cả token family, force re-login, ghi security event (dấu hiệu token bị đánh cắp).
- CSRF: vì auth bằng cookie → double-submit token (`X-CSRF-Token` header) cho mọi request thay đổi dữ liệu.
- Login: rate limit riêng; sai 5 lần/15' → khóa tạm 15' (AUTH-0003) + ghi security event; không tiết lộ user tồn tại hay không (message chung AUTH-0001).
- Mật khẩu: BCrypt cost ≥ 12; policy ≥ 10 ký tự có chữ + số; bắt đổi lần đầu; **2FA (TOTP) bắt buộc cho role Admin/Admin HR**.
- Đổi mật khẩu / bị khóa → denylist toàn bộ token đang sống của user (bảng DB `token_denylist`).

### 4.2. Authorization — phân cấp, phân quyền 3 tầng

```
Tầng 1 — FE route/UI guard (usePermission)      → chỉ để UX, KHÔNG phải bảo mật
Tầng 2 — Controller @RequirePermission("ops:log:approve") → chặn action
Tầng 3 — Repository scope filter (org_unit)     → chặn dữ liệu (IDOR)
```

- Permission dạng `module:resource:action`, gán vào Role (ma trận RBAC trong function-spec §6 dịch thành seed data); **deny by default** — endpoint không khai báo permission → CI fail.
- Scope: user gắn `org_unit_id`; Hibernate filter tự thêm điều kiện đơn vị (+ cây con) cho mọi query trên `ScopedEntity` — vi phạm trả AUTH-3002. Integration test toàn bộ ma trận role × resource (NFR-06).
- API nhận `public_id` (UUID) — không expose id tuần tự; mọi lookup luôn kèm scope, không bao giờ `findById` trần cho request user.

### 4.3. Chống giả mạo dữ liệu (integrity)

- **Không tin bất kỳ giá trị tính toán nào từ client**: BE nhận input thô (giờ bắt đầu/kết thúc, lưu lượng đo) và tự tính; field tính toán trong request bị ignore.
- Optimistic locking (`version`) trên mọi entity — 2 người sửa cùng lúc → 409, không silent overwrite.
- Trạng thái chỉ đổi qua Workflow engine: kiểm tra `(from, action, role)` hợp lệ trong DB transaction — không thể ép trạng thái bằng cách gọi API update thường.
- **Audit log append-only + hash chain**: mỗi bản ghi audit chứa `hash = SHA-256(record + prev_hash)` — sửa/xóa lén audit sẽ phát hiện được khi verify chain; bảng audit không cấp quyền UPDATE/DELETE cho app user (GRANT chỉ INSERT/SELECT).
- `hydro_raw_logs`: app DB user chỉ có INSERT/SELECT (enforce ở tầng DB, không chỉ ở code).
- Link tải báo cáo / file: MinIO **presigned URL TTL ngắn** (15'–24h theo loại) + gắn userId trong path — không có URL công khai vĩnh viễn.

### 4.4. Input & injection

- SQL: chỉ JPA/parameterized query; cấm string concat vào query; sort/filter field đối chiếu **whitelist** trong `PageUtils`.
- XSS: nội dung Rich Text (CMS) sanitize server-side bằng allowlist (OWASP Java HTML Sanitizer) trước khi lưu; mọi output khác escape mặc định (React tự escape — cấm `dangerouslySetInnerHTML` ngoài component `RichContent` đã sanitize).
- Upload: check magic bytes + size + extension allowlist theo bảng spec; ảnh re-encode (strip EXIF/payload); SVG sanitize hoặc chỉ admin được up; malware scan (ClamAV) async trước khi file chuyển trạng thái "sẵn sàng"; serve từ MinIO với `Content-Disposition` + `X-Content-Type-Options: nosniff`, không bao giờ serve từ webroot app.
- GeoJSON/KMZ upload: parse bằng lib, giới hạn size/độ sâu — chống zip bomb, XXE (KMZ là zip+XML → disable external entities).

### 4.5. Hạ tầng & headers

- Nginx: HSTS, CSP (default-src 'self'; script chỉ từ self + GA/GTM đã khai báo), `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`; ẩn version server; giới hạn body size theo route upload.
- Rate limit 2 lớp: Nginx (thô, theo IP) + app filter (theo user/token, giá trị theo nhóm endpoint: login 5/15', API thường 100/phút, export 10/giờ).
- Secrets: env/Vault; khác nhau mỗi môi trường; xoay key AES + JWT signing key có quy trình (key_id versioning); cấm secrets trong log/config commit.
- Log: mask dữ liệu nhạy cảm (MaskUtils); security event riêng (login fail, refresh reuse, 403 scope, đổi quyền) → dashboard Grafana + alert.
- Dependency: scan CVE trong CI (OWASP Dependency-Check / `npm audit`) — CI fail với CVE high/critical.

### 4.6. Checklist OWASP Top 10 (điều kiện pass security test)

| Rủi ro | Biện pháp tại mục |
|---|---|
| A01 Broken Access Control | 4.2 (3 tầng + deny default + UUID + scope test 100%) |
| A02 Cryptographic Failures | 4.1, 4.5 (TLS 1.3, AES-GCM, BCrypt, key ngoài DB) |
| A03 Injection | 4.4 (parameterized, whitelist sort, sanitize) |
| A04 Insecure Design | Workflow engine, BE-only calculation (4.3) |
| A05 Misconfiguration | 4.5 headers, ẩn version, env tách biệt |
| A06 Vulnerable Components | 4.5 CVE scan trong CI |
| A07 AuthN Failures | 4.1 (lockout, rotation, reuse detection, 2FA admin) |
| A08 Integrity Failures | 4.3 (hash chain audit, optimistic lock, signed URL) |
| A09 Logging Failures | 4.5 security events + traceId + mask |
| A10 SSRF | URL fetch duy nhất là telemetry adapter — endpoint từ config Admin, validate scheme/host, không nhận URL từ user |

---

## 5. DEFINITION OF DONE (mỗi PR)

1. Dùng đúng envelope/exception/error-code của Core — không tự chế.
2. Endpoint mới có `@RequirePermission` + nằm trong whitelist sort/filter nếu có list.
3. Entity thuộc phạm vi đơn vị kế thừa `ScopedEntity`; có migration Flyway đúng naming.
4. Unit test rule nghiệp vụ ở domain layer; integration test quyền nếu thêm endpoint.
5. Message lỗi mới vào catalog (BE + FE error-map).
6. Không vi phạm ArchUnit; lint + CVE scan xanh.
