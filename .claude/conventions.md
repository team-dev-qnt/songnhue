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
- ⭐ **Ngoại lệ duy nhất: `com.songnhue.core.common.*`** (Common Platform §2) — envelope, exception, mã lỗi, filter, utils, `BaseEntity`. Đây là hạ tầng dùng chung chứ không phải dịch vụ nghiệp vụ, mọi module import trực tiếp. Rule ArchUnit (T10.2) phải cho phép `<module>.spi.*` **và** `core.common.*`, chặn phần còn lại.
- Entity không bao giờ ra khỏi tầng application — controller chỉ nhận/trả DTO (Java `record`), map bằng MapStruct.
- `@Transactional` chỉ đặt ở application service, không ở controller/repository.
- Naming: `XxxController`, `XxxService` (interface) / `XxxServiceImpl`, `XxxRepository`, DTO: `XxxRequest` / `XxxResponse` / `XxxDto`.
- Cấm: `float/double` cho số đo/tiền (dùng `BigDecimal`), `new Date()` (dùng `Instant`/`OffsetDateTime`), catch nuốt exception, `System.out`.

### 1.2. Database (PostgreSQL)

- Tên bảng: `snake_case`, số nhiều (`constructions`, `maintenance_logs`); bảng nối: `article_categories`.
- Cột: `snake_case`; khóa chính `id BIGINT GENERATED ALWAYS AS IDENTITY`; **thêm `public_id UUID` cho mọi entity expose ra API** (chống đoán ID tuần tự — IDOR).
- Cột chuẩn mọi bảng nghiệp vụ (BaseEntity): `created_at timestamptz`, `created_by`, `updated_at`, `updated_by`, `deleted_at` (soft delete), `version int` (optimistic lock).
- FK: `<bảng_số_ít>_id` (`org_unit_id`); index: `ix_<bảng>_<cột>`; unique: `uq_<bảng>_<cột>`; check: `ck_<bảng>_<rule>`.
- Enum nghiệp vụ: lưu `VARCHAR` + CHECK constraint (không dùng Postgres enum type — khó migrate).
- Migration Flyway: `V<yyyyMMddHHmm>__<module>_<mô_tả>.sql` (VD `V202607201030__ops_create_constructions.sql`). Cấm sửa migration đã merge — chỉ thêm mới. Prefix `<module>`: `core`/`cms`/`ops`/`hyd`/`hr`.
- **Vị trí migration**: mỗi module tự quản trong `src/main/resources/db/migration/<prefix>/`; module `app` gộp lại qua `spring.flyway.locations=classpath:db/migration/core,…/cms,…/ops,…/hyd,…/hr`. Version là timestamp toàn cục nên thứ tự vẫn đúng khi trộn nhiều module.
- **Cấu hình Flyway bắt buộc**: `cleanDisabled=true` (chặn `flyway clean` xóa sạch production do lỡ tay) · `validateOnMigrate=true` · `outOfOrder=false`.
- **Production/Staging chạy migration ở service `migrator` riêng** trước khi app khởi động; app chạy với `flyway.enabled=false` → migration hỏng thì app không lên nửa vời (`architecture-review.md` §9.2).
- **Phân quyền DB theo role, không chỉ theo code**: `songnhue_owner` (chỉ migrator) · `songnhue_app` (**không có DELETE** trên `audit_logs`, `security_events`, `hydro_raw_logs`) · `songnhue_archiver` (DELETE audit, chỉ job kết xuất) · `songnhue_readonly`. Xem §4.3. Role tạo ở `deploy/postgres/init/10-bootstrap.sh` (cần superuser); GRANT ở migration.
- **Extension** (`postgis`, `unaccent`, `pg_trgm`) tạo ở init script chứ không ở migration — `postgis` không phải *trusted extension* nên `songnhue_owner` không tự tạo được. Migration đầu tiên chỉ **verify** để lỗi hiện ra ngay kèm hướng dẫn.
- ⚠ **Bảng append-only tạo sau phải tự REVOKE**: default privileges cấp sẵn `UPDATE, DELETE` cho `songnhue_app` trên mọi bảng mới. Migration tạo `hydro_raw_logs` (và mọi bảng bằng-chứng khác) **bắt buộc** revoke lại — nhắc sẵn ở `README.md` trong thư mục migration của từng module.
- **Không dùng repeatable migration (`R__`)** cho danh mục quyền / tham số cấu hình: `R__` chạy lại mỗi khi file đổi, tức là ghi đè âm thầm — trái với "cấm sửa migration đã merge". Thêm quyền/setting mới = thêm file `V` mới.
- Tránh đặt tên cột `key`, `value`, `order`, `group` — đều là **từ khóa JPQL/SQL**, phải escape ở mọi truy vấn. Bảng `settings` dùng `setting_key` / `setting_value`.

### 1.3. REST API

- Base path: `/api/v1/<module>/<resource>` — version ngay từ đầu. VD: `/api/v1/ops/constructions/{publicId}/documents`.
- Resource danh từ số nhiều, kebab-case: `/maintenance-logs`, `/leave-requests`.
- Action ngoài CRUD dùng sub-resource động từ: `POST /leave-requests/{id}/submit`, `/approve`, `/reject` (map vào Workflow engine).
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
  - ⚠ **`@Validated` + `@NotBlank` MỘT MÌNH không đủ để bắt biến môi trường bị thiếu.** Bộ nạp của `@ConfigurationProperties` dùng `PropertySourcesPlaceholdersResolver` với `ignoreUnresolvablePlaceholders = true` (khác `@Value`): thiếu env thì placeholder được gán **nguyên văn**, trường nhận đúng chuỗi `"${MINIO_ENDPOINT}"` — không rỗng, nên `@NotBlank` đi qua và app khởi động bình thường. Kiểm chứng bằng chạy thật 14/8: bỏ hẳn `MINIO_ENDPOINT` → `Started SongnhueApplication`, `/actuator/health` = `UP`.
  - Chốt chặn thật là **`UnresolvedPlaceholderGuard`** (`core.common.config`) — `BeanPostProcessor` quét mọi bean `@ConfigurationProperties` (String, Map, List), giá trị nào còn nguyên dạng `${TÊN_BIẾN}` thì ném lỗi gọi đúng tên biến + đường dẫn tham số. Chạy **sau khi nạp cấu hình, trước `@PostConstruct`** để không bị `@PostConstruct` của lớp cấu hình ném trước với thông báo sai nguyên nhân. Lớp cấu hình mới **không phải làm gì thêm** — cấm quay lại kiểu mỗi lớp tự kiểm, quên một lần là thủng lại mà không có gì báo.
- Cấm tạo connection trực tiếp trong code nghiệp vụ (`new RestTemplate(url)`, `DriverManager.getConnection`...) — mọi client (DB, HTTP, S3) khởi tạo 1 lần qua Spring bean cấu hình từ env, module nghiệp vụ chỉ inject.

### 1.7. Cấu trúc monorepo & cách chạy local

```
songnhue/
├── .github/workflows/       ci.yml · deploy-staging.yml · deploy-prod.yml
├── .claude/                 tài liệu spec + phase0-tracking.md
├── backend/
│   ├── pom.xml              parent — Java 21, Spring Boot BOM, dependencyManagement
│   ├── core/                ← Nhóm A: auth, rbac, orgunit, attachment, workflow,
│   │                          notification, jobs, audit, settings, backup/restore
│   ├── content/             MOD-01   operations/  MOD-02
│   ├── hydro/               MOD-03   hr/          MOD-04
│   └── app/                 bootstrap: main, application*.yml, Dockerfile
├── frontend/
│   ├── admin-app/           Vite + React 18 + AntD 5
│   └── public-web/          Next.js + Tailwind
├── deploy/
│   ├── compose.infra.yml    PG+PostGIS, MinIO, Mailpit  (nền, được include lại)
│   ├── compose.local.yml    include infra + app/admin/public theo PROFILE
│   ├── compose.staging.yml · compose.prod.yml · compose.backup.yml
│   ├── docker/              Dockerfile của backend + 2 app FE
│   ├── postgres/init/       CREATE ROLE + extension (chạy 1 lần, cần superuser)
│   └── nginx/ · backup/ · keys/ · env/*.env.example
├── docs/runbook/            restore từ dump, xoay key, poller chết, retry job
└── Makefile
```

**Bốn chế độ chạy local** — chọn theo service bạn ĐANG SỬA. Nguyên tắc: *service
đang sửa thì chạy native, còn lại đẩy vào Docker*. Chi tiết: `docs/run-guideline.md`.

| Lệnh | Chạy gì | Dùng khi |
|---|---|---|
| `make dev-infra` | Chỉ PostgreSQL + MinIO + Mailpit | Fullstack — BE và FE đều chạy native |
| `make dev-be` | + **backend** trong Docker | Dev FE — **không cần cài JDK** |
| `make dev-fe` | + **admin-app, public-web** trong Docker | Dev BE — **không cần cài Node** |
| `make dev-docker` | **Toàn bộ** stack | QA/demo, kiểm thử gần giống production |

Chọn service bằng **Compose profile** (`backend` / `admin` / `public` / `full`);
hạ tầng không có profile nên luôn chạy.

**Image luôn build từ mã nguồn local, biên dịch bên trong container** — máy không
cần JDK/Node để dựng service của người khác. Image **không** tự bám theo file:
sửa code xong phải thêm `BUILD=1`. Cache lớp dependency giữ thời gian build lại
ở mức ~7 giây. **Không bind-mount mã nguồn để hot-reload trong Docker** — người
cần hot-reload thì chạy native, còn bind-mount gây lệch `node_modules` giữa
macOS và Linux và làm `target/` thuộc quyền root.

**Cổng: Docker publish ra dải riêng, không đụng cổng native** (quy tắc thêm số
`1` vào đầu: 8080→18080, 5432→15432, 9000→19000…), và bind đúng `127.0.0.1` để
trùng cổng thì báo lỗi ngay thay vì âm thầm nối nhầm sang dịch vụ khác của máy.
| `make migrate` | Chạy service `migrator` trong Docker | Sau khi thêm migration mới |
| `make migrate-native` | Chạy migration từ máy (profile `migrate`) | Dev BE chạy native, đã có `make dev-infra` |
| `make migrate-info` | Liệt kê migration đã áp dụng | Đối chiếu phiên bản schema |
| `make db-verify-audit` | Verify chuỗi hash `audit_logs` | Sau restore, sau sự cố, trước nghiệm thu |
| `make test` | Unit + Testcontainers + ArchUnit | Trước khi push |
| `make backup` / `make restore` | Dump thủ công / khôi phục | Vận hành, diễn tập |

Quy tắc: **profile Spring chỉ khác nhau ở env, không khác code** (§1.6). Hệ quả:
chỉ có `application-local.yml`; **không có profile `docker`** (chạy native và chạy
trong Docker khác nhau đúng ở giá trị env), và chưa tạo `staging`/`prod` cho tới
khi có nội dung thật. File profile rỗng chỉ tạo chỗ cho hai lối chạy âm thầm lệch
nhau. Build backend luôn qua `mvnw` wrapper — không bắt máy dev cài Maven.

**Collation DB chốt `ICU vi-VN`** (`POSTGRES_INITDB_ARGS` trong compose): để mặc
định thì `ORDER BY` xếp "Đăng" sau "Em". Đổi sau khi đã có dữ liệu = dump +
restore, nên Staging/Production phải dùng đúng tham số này.

---

## 2. COMMON PLATFORM — API RESPONSE, EXCEPTION, ERROR CODE

Nằm trong `core/` (BE) và `shared/` (FE). **Mọi module bắt buộc dùng, cấm tự chế envelope/exception riêng.**

### 2.1. API Response envelope (thống nhất 100% endpoint)

```json
// Thành công
{ "success": true, "data": { ... }, "meta": { "page": 1, "size": 20, "totalElements": 134, "totalPages": 7 }, "traceId": "a1b2c3" }

// Lỗi
{ "success": false, "error": { "code": "OPS-2001", "message": "Ngày hoàn thành phải ≥ ngày bắt đầu", "details": [ { "field": "completedDate", "rule": "AFTER_OR_EQUAL_START_DATE", "rejectedValue": "2026-08-01" } ] }, "traceId": "a1b2c3" }
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
| SYS-0001 | 500 | Lỗi hệ thống, vui lòng thử lại. Mã tra cứu: {0} |
| SYS-0002 | 429 | Thao tác quá nhanh, vui lòng thử lại sau |
| **SYS-0003** | 400 | Dữ liệu gửi lên không hợp lệ — *mặc định của `ValidationException`* |
| **SYS-0004** | 404 | Không tìm thấy dữ liệu — *mặc định của `ResourceNotFoundException`* |
| **SYS-0005** | 409 | Dữ liệu vừa được người khác thay đổi — *optimistic lock, trùng unique* |
| **SYS-0006** | 502 | Hệ thống bên ngoài không phản hồi — *mặc định của `UpstreamException`* |
| **SYS-0007** | 503 | Hệ thống đang bảo trì — *maintenance mode lúc khôi phục dữ liệu (M5.11)* |
| **SYS-0008** | 422 | Thao tác không hợp lệ với trạng thái hiện tại — *mặc định của `BusinessRuleException`* |
| AUTH-0001 | 401 | Sai tên đăng nhập hoặc mật khẩu |
| AUTH-0002 | 401 | Phiên đăng nhập hết hạn |
| AUTH-0003 | 423 | Tài khoản tạm khóa do đăng nhập sai nhiều lần |
| **AUTH-0004** | 401 | Mã 2FA không đúng, hết hiệu lực, hoặc **đã dùng rồi** (chống replay) |
| **AUTH-0005** | 403 | Thiếu/sai `X-CSRF-Token` — double-submit không khớp |
| **AUTH-0006** | 422 | Mật khẩu mới không đạt chính sách (M5.15) hoặc trùng mật khẩu cũ |
| **AUTH-0007** | 403 | Đang bắt buộc đổi mật khẩu — chặn mọi thao tác khác cho tới khi đổi xong |
| **AUTH-0008** | 401 | Phiên bị thu hồi vì **phát hiện dùng lại refresh token** — buộc đăng nhập lại |
| AUTH-3001 | 403 | Không có quyền thực hiện thao tác này |
| AUTH-3002 | 403 | Dữ liệu không thuộc phạm vi đơn vị của bạn |
| CMS-2001 | 422 | Slug đã tồn tại |
| CMS-2002 | 422 | Chưa liên kết mã số hệ thống văn bản điều hành |
| CMS-5001 | 502 | Không đăng nhập được sang hệ thống văn bản điều hành — mã số có thể đã hết hiệu lực |
| OPS-2001 | 422 | Ngày hoàn thành phải ≥ ngày bắt đầu |
| OPS-2002 | 422 | Công trình đã bị xóa/thanh lý — không ghi nhận được công việc mới |
| OPS-2003 | 422 | Bản ghi loại "Khắc phục sự cố" bắt buộc có mức độ |
| OPS-2004 | 422 | Không chuyển được sang "Đã xử lý" khi chưa có ngày hoàn thành |
| OPS-2005 | 409 | Mã tình hình vận hành đã tồn tại |
| OPS-2006 | 422 | Mã tình hình vận hành này yêu cầu nhập giá trị kèm theo (VD `+1.70m`) |
| OPS-2007 | 422 | Mã tình hình vận hành đã được sử dụng — chỉ được ẩn, không được xóa |
| OPS-3001 | 403 | Không được sửa trực tiếp trạng thái công trình — trạng thái được tính tự động |
| HYD-1001 | 404 | Điểm đo chưa ánh xạ nguồn API bên thứ 3 |
| HYD-2001 | 422 | Giá trị đo ngoài khoảng vật lý cho phép |
| HYD-2002 | 422 | Bản ghi đang ở trạng thái Nghi ngờ — cần duyệt trước khi sử dụng |
| HYD-2003 | 422 | Điểm đo chưa cấu hình ngưỡng cảnh báo |
| HYD-2004 | 422 | Điểm đo đang mất tín hiệu — không dùng giá trị cũ để đánh giá ngưỡng |
| HR-2001 | 422 | Số ngày đăng ký vượt số phép còn lại |
| ADM-2001 | 422 | Kết xuất lưu trữ nhật ký thất bại — không xóa bản ghi nào |

> ⚠ **Đã gỡ (12/8/2026)**: `OPS-2001` cũ ("nhập bù tối đa 3 ngày") và `OPS-2003` cũ ("lưu lượng vượt 120% thiết kế") — thuộc nhật ký vận hành đã bỏ khỏi scope. Hai mã này **đã được tái sử dụng** cho rule mới ở bảng trên; khi đọc code/log cũ phải chú ý.
> ℹ **Không phải lỗi**: lượt polling bị bỏ qua do rate-limit (`sync_logs = SKIPPED_UP_TO_DATE`, chốt G3) **không** sinh error code, không alert — chỉ ghi log DEBUG.

- Message tiếng Việt tập trung 1 file **`error-messages.properties`** (BE) — FE có bản mirror `shared/error-map.ts` (fallback dùng message từ API). Thêm code mới = thêm vào catalog, có review; cấm hardcode message trong controller/service.
  - ⚠ **Tên file KHÔNG có hậu tố `_vi` — cố ý.** `MessageSourceAutoConfiguration` của Spring Boot chỉ tạo `MessageSource` khi tìm thấy đúng file basename; chỉ có `error-messages_vi.properties` thì **không MessageSource nào được tạo** và mọi lỗi trả ra khoá thô (`OPS-2001`) thay vì câu tiếng Việt. Lỗi này im lặng hoàn toàn, chỉ lộ khi người dùng gặp lỗi thật. `ErrorCatalogTest` chặn ở CI: mọi `ErrorCode` phải có message, và file không được có khoá thừa.
  - Tham số trong message dùng cú pháp `MessageFormat` (`{0}`, `{1}`), **không phải** `{traceId}`.
- Workflow engine trả lỗi transition không hợp lệ bằng code chung `<MOD>-2xxx` + details `{from, action, allowedActions}`.

### 2.4. Middleware / Filter chain (thứ tự cố định)

> Thứ tự khai báo bằng hằng số ở `FilterOrder` (core) — WS-5 chỉ cắm filter vào vị trí đã chừa sẵn, không sửa filter có trước. Ghi log là **filter** chứ không phải `HandlerInterceptor`: interceptor không thấy request bị chặn từ tầng filter và không đo được trọn thời gian xử lý.

```
Request → [1] CorrelationFilter (sinh/nhận traceId, MDC cho log)
        → [1b] RequestLoggingFilter (nằm TRONG correlation, NGOÀI rate limit — để request bị chặn 429 vẫn được ghi log)
        → [2] RateLimitFilter (bucket theo IP; login có bucket riêng)
        → [2b] CsrfFilter (double-submit, chỉ với method thay đổi dữ liệu — WS-5/T5.5)
        → [3] AuthFilter (verify access token; đối chiếu sessions + token_denylist)
        → [4] ScopeContextFilter (load user → role, permissions, org_unit path vào AuthContext)
        → [5] AuditContextFilter (gắn user/traceId cho audit interceptor)
        → PermissionInterceptor (tầng 2 — @RequirePermission, xem §4.2)
        → Controller → Service → Repository (scope filter tự áp — §4.2 tầng 3)
Response ← GlobalExceptionHandler / ResponseBodyAdvice (envelope) ← RequestLoggingFilter (method, path, status, duration — KHÔNG log body chứa dữ liệu nhạy cảm)
```

**Hai điểm chốt của WS-5, đừng đảo ngược khi sửa về sau:**

- **`AuthFilter` KHÔNG tự trả 401.** Ở tầng filter chưa biết endpoint sắp gọi có cần đăng nhập hay không (thông tin đó nằm ở annotation trên phương thức controller, Spring chưa phân giải handler). Cụ thể hơn: FE thường gửi kèm access token **đã hết hạn** khi gọi `/auth/refresh` — filter thấy token hỏng mà trả 401 ngay thì luồng làm mới token không bao giờ chạy được. Filter chỉ ghi nhận kết quả; `PermissionInterceptor` mới quyết định.
- **Phân quyền là `HandlerInterceptor`, không phải filter** — vì nó cần đọc annotation của đúng phương thức controller sắp chạy.

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
| `HashUtils` *(WS-5)* | SHA-256 hex 64 ký tự (refresh token, mã khôi phục, checksum tệp), sinh chuỗi ngẫu nhiên an toàn, **so sánh constant-time**. ⛔ KHÔNG dùng cho mật khẩu — mật khẩu cần thuật toán *chậm* (BCrypt cost ≥ 12) |
| `TotpGenerator` *(WS-5)* | Sinh/kiểm mã TOTP theo RFC 6238 (HMAC-SHA1, bước 30s, 6 chữ số, cho lệch ±1 bước). Tự cài thay vì kéo thư viện vì RFC có **bộ vector kiểm thử chính thức** — tính đúng đắn chứng minh bằng test, xem `TotpGeneratorTest` |

Base classes: `BaseEntity` (audit cột chuẩn + soft delete + version), `ScopedEntity extends BaseEntity` (+ `org_unit_id` — mọi entity thuộc phạm vi đơn vị bắt buộc kế thừa; điều kiện SQL của bộ lọc phạm vi viết **đúng một lần** ở hằng `ScopedEntity.ORG_UNIT_FILTER_CONDITION`).

⚠ **Cột `CHAR(n)` và `inet` của Postgres phải khai `@JdbcTypeCode`** (`SqlTypes.CHAR` / `SqlTypes.INET`). Thiếu thì `ddl-auto: validate` chặn ngay lúc khởi động với thông báo "wrong column type encountered" — đúng như thiết kế, nhưng dễ mất thời gian nếu không biết trước.

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
- Login: sai 5 lần/15' → khóa tạm 15' (AUTH-0003) + ghi security event; không tiết lộ user tồn tại hay không (message chung AUTH-0001) — sai tên, sai mật khẩu và tài khoản bị vô hiệu hoá đều trả **cùng một câu** và tốn **xấp xỉ cùng thời gian** (băm giả một lần khi không tìm thấy tài khoản, nếu không thì đo thời gian phản hồi là dựng được danh sách tài khoản có thật).
  - ⚠ **Hạn mức đăng nhập theo IP phải RỘNG HƠN ngưỡng khoá tài khoản** — chốt 30/15' theo IP so với 5 lần theo tài khoản (phát hiện khi chạy thử WS-5). Đặt bằng nhau thì rate limit ở filter luôn chặn trước, người dùng nhận `SYS-0002` thay vì `AUTH-0003` và tham số M5.15 Admin chỉnh trên UI **hoàn toàn không có tác dụng**. Thêm nữa, cả Công ty ra Internet qua một IP NAT: hạn mức quá chặt là vài người gõ nhầm mật khẩu buổi sáng làm cả cơ quan không đăng nhập được. `CaffeineRateLimitStoreTest` chặn ở CI nếu ai đó hạ xuống.
- Mật khẩu: BCrypt cost ≥ 12; policy ≥ 10 ký tự có chữ + số; bắt đổi lần đầu; **2FA (TOTP) bắt buộc cho role Super Admin / Admin / Admin HR**.
  - 2FA: secret **mã hoá** AES-256-GCM (không băm được — máy chủ phải đọc lại để tính mã); một mã dùng đúng **một lần** (`user_totp.last_used_step`, chống replay); 10 mã khôi phục băm SHA-256, dùng mã khôi phục sinh security event mức DANGER. Máy chủ chỉ trả chuỗi `otpauth://` — **QR do FE vẽ**, không sinh ảnh ở máy chủ (thêm một chỗ secret đi qua).
- Đổi mật khẩu / bị khóa → denylist toàn bộ token đang sống của user (bảng DB `token_denylist`), đồng thời thu hồi mọi phiên (kể cả phiên đang thao tác).
- Access token mang `fid` = **token family** của phiên. Thu hồi family (đăng xuất, đăng xuất từ xa, phát hiện reuse) là access token chết **ngay**, không phải chờ hết 30 phút.
- Access token **không** mang danh sách quyền: nhét quyền vào token thì Admin gỡ quyền xong người kia vẫn dùng được tới 30 phút. Quyền nạp từ DB mỗi request, cache 30 giây, có `AuthorityLoader.invalidate()` để hiệu lực tức thì.

### 4.2. Authorization — phân cấp, phân quyền 3 tầng

```
Tầng 1 — FE route/UI guard (usePermission)      → chỉ để UX, KHÔNG phải bảo mật
Tầng 2 — Controller @RequirePermission("ops:maintenance:create") → chặn action
Tầng 3 — Repository scope filter (org_unit)     → chặn dữ liệu (IDOR)
```

- Permission dạng `module:resource:action`, gán vào Role (ma trận RBAC trong function-spec §6 dịch thành seed data).
- **Deny by default — mỗi phương thức controller bắt buộc khai báo ĐÚNG MỘT trong ba annotation:**

  | Annotation | Dùng khi |
  |---|---|
  | `@RequirePermission("ops:maintenance:create")` | Cần quyền cụ thể. `mode = ANY` (mặc định) hoặc `ALL` |
  | `@AuthenticatedEndpoint(reason = "…")` | Chỉ cần đăng nhập — thao tác với **chính mình** (đổi mật khẩu, xem/đăng xuất phiên của mình). Gán mã quyền cho những việc này là sai mô hình |
  | `@PublicEndpoint(reason = "…")` | Không cần đăng nhập. **Bắt buộc ghi lý do** — danh sách endpoint công khai bị soát lại mỗi lần kiểm thử bảo mật |

  Có annotation riêng cho "chỉ cần đăng nhập" thay vì để trống là để phân biệt được với **quên khai báo**. Hai lớp chặn: `DenyByDefaultTest` làm **CI đỏ** (kèm kiểm định dạng mã quyền và module hợp lệ), và `PermissionInterceptor` **từ chối lúc chạy** endpoint không khai báo gì.
- Scope: user gắn `org_unit_id`; Hibernate filter tự thêm điều kiện đơn vị (+ cây con) cho mọi query trên `ScopedEntity` — vi phạm trả AUTH-3002. Integration test toàn bộ ma trận role × resource (NFR-06).
  - Lọc theo **materialized path**, không theo `org_unit_id = ?`: quản lý Xí nghiệp phải thấy cả Tổ đội trực thuộc. Hệ quả gọn: người ở nút gốc có path `/1/`, mà path của mọi đơn vị đều bắt đầu bằng `/1/` → họ tự nhiên thấy toàn bộ dữ liệu, **không cần cờ "bỏ qua phạm vi"** — mà cờ như vậy chính là thứ hay bị bật nhầm rồi không ai để ý.
  - Bật bằng `ScopeFilterAspect` quanh `@Transactional`, **một chỗ duy nhất** — quy tắc 5 của dự án: không dựa vào việc lập trình viên nhớ thêm `WHERE`.
- API nhận `public_id` (UUID) — không expose id tuần tự; mọi lookup luôn kèm scope, không bao giờ `findById` trần cho request user.

### 4.3. Chống giả mạo dữ liệu (integrity)

- **Không tin bất kỳ giá trị tính toán nào từ client**: BE nhận input thô và tự tính; field tính toán trong request bị ignore. **Trạng thái công trình là giá trị dẫn xuất** (từ sự cố đang mở / bảo trì / cảnh báo / mã tình hình vận hành) — client gửi lên bị ignore, sửa trực tiếp trả `OPS-3001`.
- Optimistic locking (`version`) trên mọi entity — 2 người sửa cùng lúc → 409, không silent overwrite.
- Trạng thái chỉ đổi qua Workflow engine: kiểm tra `(from, action, role)` hợp lệ trong DB transaction — không thể ép trạng thái bằng cách gọi API update thường. Áp dụng cả cho `maintenance_logs.handling_status` (Mới → Đang xử lý → Đã xử lý).
- **Audit log append-only + hash chain**: mỗi bản ghi audit chứa `hash = SHA-256(record + prev_hash)` — sửa/xóa lén audit sẽ phát hiện được khi verify chain; bảng audit không cấp quyền UPDATE/DELETE cho app user (GRANT chỉ INSERT/SELECT).
  - ⭐ **Hash tính bằng trigger trong DB**, không tính ở Java (chốt WS-2): trigger là `SECURITY DEFINER` nên client gửi `seq`/`hash`/`prev_hash` lên cũng bị ghi đè — app không có quyền `UPDATE` trên `audit_chain_head` để tự nối chuỗi. Nếu tính ở tầng Java thì một bug ở app đủ để phá chuỗi mà không ai biết.
  - Công thức băm nằm ở **đúng một chỗ**: `core_audit_canonical_payload()` + `core_audit_hash()`. API verify (T6.12) gọi `core_verify_audit_chain(from_seq, to_seq)` — **cấm cài lại công thức bên Java**, hai bản lệch nhau là chuỗi gãy giả.
  - Thêm một lớp nữa: trigger `BEFORE UPDATE` chặn sửa `audit_logs` với **mọi** role, kể cả `songnhue_owner`.
- **Kết xuất lưu trữ audit quá 5 năm (G7)**: chỉ được xóa khỏi bảng nóng **sau khi** file kết xuất đã ghi thành công lên MinIO **và** checksum SHA-256 verify khớp; lưu `hash` cuối của lô đã kết xuất làm **điểm neo** để chain tiếp tục liền mạch. Thất bại → không xóa dòng nào (`ADM-2001`) + alert Admin. Thao tác xóa này chạy bằng **DB role riêng có DELETE**, không dùng app user.
- `hydro_raw_logs`: app DB user chỉ có INSERT/SELECT (enforce ở tầng DB, không chỉ ở code). Là **bản sao duy nhất** của dữ liệu nguồn (không có API lịch sử) → ghi nguyên văn response **trước khi** parse.
- `construction_operation_status` **append-only theo nghiệp vụ**: cập nhật tình hình vận hành = thêm dòng mới có `effective_at`, không UPDATE dòng cũ — giữ được lịch sử đối soát.
- Link tải báo cáo / file: MinIO **presigned URL TTL ngắn** (15'–24h theo loại) + gắn userId trong path — không có URL công khai vĩnh viễn.

### 4.4. Input & injection

- SQL: chỉ JPA/parameterized query; cấm string concat vào query; sort/filter field đối chiếu **whitelist** trong `PageUtils`.
- XSS: nội dung Rich Text (CMS) sanitize server-side bằng allowlist (OWASP Java HTML Sanitizer) trước khi lưu; mọi output khác escape mặc định (React tự escape — cấm `dangerouslySetInnerHTML` ngoài component `RichContent` đã sanitize).
- Upload: check magic bytes + size + extension allowlist theo bảng spec; ảnh re-encode (strip EXIF/payload); SVG sanitize hoặc chỉ admin được up; malware scan (ClamAV) async trước khi file chuyển trạng thái "sẵn sàng"; serve từ MinIO với `Content-Disposition` + `X-Content-Type-Options: nosniff`, không bao giờ serve từ webroot app.
- GeoJSON/KMZ upload: parse bằng lib, giới hạn size/độ sâu — chống zip bomb, XXE (KMZ là zip+XML → disable external entities).

### 4.5. Hạ tầng & headers

- Nginx: HSTS, CSP (default-src 'self'; script chỉ từ self + GA/GTM đã khai báo), `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`; ẩn version server; giới hạn body size theo route upload.
- Rate limit 2 lớp: Nginx (thô, theo IP) + app filter (theo user/token, giá trị theo nhóm endpoint: **login 30/15'**, API thường 100/phút, export 10/giờ).
  - ⚠ **`login 30/15'` chứ không phải 5/15'** — con số này phải rộng hơn hẳn ngưỡng khoá tài khoản (5 lần, §4.1). Lý do đầy đủ ở §4.1; tóm tắt: đặt bằng nhau thì rate limit ở filter luôn chặn trước nên `AUTH-0003` không bao giờ kích hoạt được, và cả Công ty ra Internet qua một IP NAT. `CaffeineRateLimitStoreTest` chặn ở CI nếu ai đó hạ xuống bằng ngưỡng khoá.
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

### 4.7. Credential hệ thống bên ngoài (bổ sung 12/8/2026)

Hệ thống lưu 2 loại credential của bên thứ 3 — **bắt buộc theo cùng 1 chuẩn**:

| Loại | Bảng | Dùng cho |
|---|---|---|
| Khóa API thủy văn (`key` của `bhh40.net`) | `api_sources.credential` | Poller MOD-03 gọi `getmn.aspx` |
| Mã số đăng nhập hệ thống văn bản điều hành (theo từng người dùng) | `external_system_credentials.credential` | Auto-login CN-01.7 |

Quy tắc chung (áp dụng cả hai):
- Mã hóa **AES-256-GCM** qua `CryptoService`; **key nằm ngoài DB** (env/Vault), tách khỏi bản backup DB, có `key_id` để xoay key.
- **Không** có endpoint nào trả credential ra ngoài — kể cả cho Admin. UI chỉ hiện dạng mask (`MaskUtils`).
- **Không** ghi vào log, không đưa vào audit `old_value`/`new_value` (chỉ ghi "đã thay đổi credential"), không đưa vào response lỗi, không đưa vào bản export cấu hình (M5.17 phải loại trường này ra).
- Giải mã **chỉ tại thời điểm sử dụng**, trong bộ nhớ, không cache ra ngoài request.
- Mọi thao tác tạo/sửa/xóa/sử dụng → **security event** (ai, khi nào, IP).
- Người dùng tự quản mã số của mình; Admin không xem, không nhập hộ (trừ khi Công ty chốt dùng mã số chung — chờ G5).
- Hệ thống nguồn chạy **HTTP** → chỉ gọi từ backend, **cấm** để trình duyệt người dùng gọi trực tiếp; ghi nhận là rủi ro tồn dư trong hồ sơ bàn giao.

---

## 5. DEFINITION OF DONE (mỗi PR)

1. Dùng đúng envelope/exception/error-code của Core — không tự chế.
2. Endpoint mới có `@RequirePermission` + nằm trong whitelist sort/filter nếu có list.
3. Entity thuộc phạm vi đơn vị kế thừa `ScopedEntity`; có migration Flyway đúng naming.
4. Unit test rule nghiệp vụ ở domain layer; integration test quyền nếu thêm endpoint.
5. Message lỗi mới vào catalog (BE + FE error-map).
6. Không vi phạm ArchUnit; lint + CVE scan xanh.
