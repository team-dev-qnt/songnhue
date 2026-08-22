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
- ⚠⚠ **`core/spi/` hiện RỖNG (tính tới 19/8/2026)** — chỉ có `package-info.java`. Nhưng sáu dịch vụ dùng chung (`WorkflowEngine`, `NotificationService`, `AttachmentService`, `JobService`, `SettingService`, `OrgUnitService`) nằm ở `core.application.*`, tức **dòng mã Phase 1 đầu tiên gọi tới chúng sẽ làm ArchUnit đỏ**. Việc mở màn Phase 1 là **thêm interface vào `core/spi/`** rồi để service ở `application` cài nó. ⛔ **Không nới luật ArchUnit cho `core.application.*`** — sửa file test kiến trúc để mã của mình chạy được là dấu hiệu đi sai đường, và ranh giới module là thứ giữ cho Modular Monolith khỏi thành khối dính.
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

**Bổ sung sau WS-8 (17/8)** — những điều chỉ lộ ra khi dựng thật:

- **Một tệp cấu hình ESLint duy nhất** ở `frontend/eslint.config.mjs`, áp cho cả hai app. ESLint 9 flat config **không gộp cấu hình lồng nhau** như `.eslintrc` ngày trước: nó chỉ đọc một file tính từ thư mục chạy lệnh. Đặt `eslint.config.mjs` riêng trong từng app thì file đó bị **bỏ qua im lặng** — lint vẫn xanh mà nhóm rule React chưa từng chạy. Lệnh lint và `npm ci` đều chạy ở `frontend/` (workspaces chỉ có **một** lockfile).
- **Mỗi module chỉ xuất component, hoặc chỉ xuất dữ liệu/hook — không lẫn.** Bảng từ vựng trạng thái, hook phân trang, hàm thuần đều nằm ở tệp riêng (`statusVocabulary.ts`, `usePagination.ts`, `restoreAccess.ts`). Luật `react-refresh/only-export-components` canh chỗ này; nó chạy ở mức lỗi vì `--max-warnings=0`.
- **Đường dẫn route tiếng Việt không dấu** (`/quan-tri/sao-luu`): hệ thống chỉ phục vụ tiếng Việt, và URL đọc được qua điện thoại thì không phải dịch. Không dấu để chép đi chép lại không bị mã hoá phần trăm.
- **Màn hình quản trị tải theo nhu cầu** (`React.lazy`), màn hình xác thực nạp thẳng. Nhóm quản trị kéo theo bảng/cây/biểu đồ — phần nặng nhất của bó mã; người mở trang lần đầu chỉ cần đăng nhập (NFR-03: tải trang ≤ 3 giây).
- **Điều kiện hiện/ẩn nhiều vế phải tách thành hàm thuần có bài kiểm**, không nằm lẫn trong JSX — xem `architecture-review.md` §9.10.4.
- **Bản sao danh mục mã lỗi phải có bài kiểm đọc thẳng file của backend** (`error-map.test.ts`). Nghĩa vụ đồng bộ ghi bằng chú thích đã trôi qua ba đợt mà không ai làm.

**Bổ sung sau WS-9 (17/8)**:

- **Thứ dùng chung giữa hai app FE nằm ở workspace riêng** (`frontend/design-tokens`), không nằm trong app này rồi app kia import sang — hai app ngang hàng, để lẫn là tạo quan hệ phụ thuộc ngược và kéo mã nguồn app kia vào bối cảnh build. Gói dùng chung xuất thẳng `.ts`, không có bước biên dịch riêng.
- **Mỗi Dockerfile FE chỉ chép manifest của workspace mình + gói dùng chung**, rồi `npm ci --workspace <app> --include-workspace-root`. Chép chéo manifest của app kia làm hai image ràng buộc lẫn nhau vô cớ.
- **Bí mật phía máy chủ của Next tuyệt đối không mang tiền tố `NEXT_PUBLIC_`** — biến mang tiền tố đó bị nhúng vào bundle gửi xuống trình duyệt.
- **`robots.ts` phải tự chặn lập chỉ mục khi không phải production.** Staging dùng chung mã nguồn với production; cho lập chỉ mục là Google có hai bản của cùng nội dung.

### 1.5. Git & CI

- Branch: `feat/<module>-<mô-tả>`, `fix/…`, `chore/…`; commit theo Conventional Commits (`feat(ops): thêm alert engine`).
- ⚠⚠ **Squash merge xong thì nhánh nguồn ĐÃ CHẾT — cắt nhánh mới từ `dev`, đừng dùng lại.** Squash tạo commit mới mang nội dung nhưng **không mang lịch sử**, nên git không biết `dev` đã chứa công việc đó; tổ tiên chung đứng nguyên và PR sau sẽ dựng lại toàn bộ khác biệt. Ngày 18/8 sập **hai lần**: lần đầu PR hiện **437 tệp** trong khi nhánh chỉ khác **8**; lần sau commit chồng lên nền chưa reset → **xung đột thật** ở 3 tệp dù nội dung hai bên giống hệt. Muốn dùng lại tên nhánh thì `git reset --hard origin/dev` rồi `cherry-pick` phần thật sự mới. Có cơ chế canh: `.githooks/pre-push` (`make hooks` để bật, `make branch-check` hỏi tay) — đếm số tệp **hiện trong diff ba chấm nhưng nội dung đã giống base**, lớn hơn 0 là lỗi thời. Chi tiết `docs/cicd.md` §9.1.
- PR bắt buộc: 1 reviewer, CI xanh (unit + integration Testcontainers + ArchUnit + lint), không merge khi coverage domain layer giảm.
  - Thi hành: `.github/workflows/ci.yml` (**5 job**: lọc vùng · backend · frontend · đóng gói image · soi phụ thuộc PR) + `security-scan.yml` (quét CVE **theo lịch**, không gắn vào PR — `docs/cicd.md` §3.3) + `docs/branch-protection.md`. ⚠ Branch protection là **cấu hình phía GitHub, không nằm trong repo** — tắt đi không để lại dấu vết nào trong mã nguồn, nên trạng thái của nó phải được ghi ra thay vì giả định.
  - Cổng bao phủ: JaCoCo `check` ở phase `verify`, **chỉ soi gói `domain`**. Ngưỡng hiện tại (`jacoco.domain.line.coverage`) là **mức đo được**, không phải mục tiêu — nâng dần khi Phase 1 đưa logic nghiệp vụ thật vào `domain`, và không bao giờ hạ.
- Cấm commit: secrets, file config môi trường thật, `.env` (dùng `.env.example`).
- ⚠ **Mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi phạm.** WS-10 tìm ra **4 cơ chế báo xanh trong khi không chạy qua thứ gì** (`architecture-review.md` §9.8.2): bộ máy ArchUnit tìm ra 0 bài kiểm, luật JaCoCo bị bỏ qua vì lọc sai chỗ, và 2 luật chạy qua 0 lớp. "Xanh" chỉ nói lên rằng nó không đỏ, không nói lên rằng nó đang canh.
- ⚠⚠ **Mock đặt đúng chỗ mã chạm ra ngoài = chưa kiểm gì cả.** Chạm ra ngoài nghĩa là tiến trình con, CSDL, hệ tệp, mạng — nơi *môi trường* mới là thứ hay hỏng, chứ không phải logic. Rà soát 17/8 (`architecture-review.md` §9.12.1): `BackupServiceTest` mock `PostgresToolRunner` nên xanh trọn vẹn trong khi `pg_dump` **chưa từng chạy được một lần nào** vì thiếu một quyền trên CSDL — mất trắng cơ chế sao lưu suốt 3 work stream. Bài kiểm mock chứng minh phần điều phối, và phải đi kèm **một** bài chạy thật qua đúng ranh giới đó.
- ⚠ **Script trong workflow phải kiểm bằng `bash -c`, không phải shell mặc định của máy.** Runner GitHub chạy **bash**; máy dev ở đây chạy **zsh**, mà zsh **không tách từ khi khai triển biến** còn bash thì có. Ngày 19/8 một bản sửa `promotion-guard` dùng `for muc in $runs` với tên job chứa dấu cách (`Backend — build, lint, test`): thử ở local thấy đúng, dưới bash thì tên job vỡ thành 5 mảnh và cổng **luôn đỏ**. Cùng họ với luật ngay trên — shell là cơ chế canh gác, và nó hỏng theo cách phụ thuộc môi trường.
- ⚠ **Script shell cũng là cơ chế canh gác, và hỏng còn im lặng hơn.** `verify-no-keys.sh` in `✓` suốt từ WS-7 vì mẫu tìm khoá PEM bắt đầu bằng `-` nên `grep` đọc thành tuỳ chọn rồi chết, mà lời gọi nằm trong `if` nên lỗi bị nuốt (§9.12.2). Luật: mẫu luôn truyền qua `-e`/`--`, và script canh gác phải **tự kiểm mỗi lượt** — cho một mẫu vi phạm giả đi qua đúng hàm đó và bắt nó phải kêu.

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
├── frontend/                npm workspaces — MỘT lockfile, MỘT eslint.config.mjs
│   ├── design-tokens/       màu + kích thước dùng chung, xuất thẳng .ts (không build)
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
| ADM-2008 | 422 | Chưa cấu hình được sao lưu — thư mục lưu hoặc tài khoản đọc CSDL (WS-7) |
| ADM-2009 | 409 | Đang có một lượt sao lưu chạy |
| ADM-2010 | 422 | Khôi phục qua giao diện chưa được bật trên môi trường này |
| ADM-2011 | 422 | Chuỗi xác nhận khôi phục không đúng (M5.11) |
| ADM-2012 | 422 | Bản sao lưu không dùng được: thiếu tệp hoặc checksum không khớp |
| ADM-2013 | 500 | Khôi phục thất bại — xem `docs/runbook/khoi-phuc-du-lieu.md` |

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
| `MaterializedPath` *(WS-6)* | Phép toán path cây `/1/4/9/` — dựng, so cha con, chuyển cây con, chống tạo vòng. ⚠ Path **bắt buộc** có `/` ở cả hai đầu: thiếu nó thì `LIKE '/1/4%'` khớp nhầm `/1/40/` và bộ lọc phạm vi tầng 3 rò dữ liệu giữa các Xí nghiệp. Chuyển cây con **cấm dùng `replace()`** — path có thể chứa lặp tiền tố, chỉ được cắt phần đầu |
| `TreeBuilder` *(WS-6)* | Danh sách phẳng → cây lồng nhau, một lượt duyệt (không N+1). Nút có cha nằm ngoài danh sách được coi là gốc — cần cho người chỉ xem được cây con đơn vị mình |
| `ImageSanitizer` *(WS-6)* | Mã hoá lại ảnh JPEG/PNG để bỏ **EXIF** (ảnh điện thoại mang toạ độ GPS — ảnh hiện trường đăng lên Cổng TTĐT là công khai kèm toạ độ) và **dữ liệu lạ gắn kèm** (polyglot: magic bytes không bắt được vì phần đầu đúng là ảnh thật). ⛔ Không dùng cho SVG — SVG là XML, cần đường sanitize riêng |
| `TotpGenerator` *(WS-5)* | Sinh/kiểm mã TOTP theo RFC 6238 (HMAC-SHA1, bước 30s, 6 chữ số, cho lệch ±1 bước). Tự cài thay vì kéo thư viện vì RFC có **bộ vector kiểm thử chính thức** — tính đúng đắn chứng minh bằng test, xem `TotpGeneratorTest` |

Base classes: `BaseEntity` (audit cột chuẩn + soft delete + version), `ScopedEntity extends BaseEntity` (+ `org_unit_id` — mọi entity thuộc phạm vi đơn vị bắt buộc kế thừa; điều kiện SQL của bộ lọc phạm vi viết **đúng một lần** ở hằng `ScopedEntity.ORG_UNIT_FILTER_CONDITION`).

⚠ **Cột `CHAR(n)` và `inet` của Postgres phải khai `@JdbcTypeCode`** (`SqlTypes.CHAR` / `SqlTypes.INET`). Thiếu thì `ddl-auto: validate` chặn ngay lúc khởi động với thông báo "wrong column type encountered" — đúng như thiết kế, nhưng dễ mất thời gian nếu không biết trước.

FE mirror (`shared/`): `apiClient` (axios instance duy nhất: gắn CSRF header, auto refresh token 1 lần rồi logout, unwrap envelope, error → notification theo `error-map`), `useAuth`, `usePermission(code)`, `formatDateTime` (UTC+7), `formatNumber` (hiển thị số đo/tiền thống nhất).

---

### 2.6. Sáu pattern dùng chung (WS-6) — module nghiệp vụ chỉ khai báo, không tự cài lại

| Pattern | Cách cắm vào | Bẫy đã biết |
|---|---|---|
| **P1 Workflow** | Entity cài `WorkflowAware`; quy trình khai bằng **dữ liệu** trong `workflow_definitions` + `workflow_transitions`. Gọi `WorkflowEngine.execute(entity, action, title)` | ⛔ **Cấm gọi `applyState` trực tiếp** — bỏ qua kiểm quyền, bỏ qua bắn thông báo, bỏ qua nhật ký (quy tắc 4). ArchUnit sẽ chặn ở T10.2. FE lấy nút từ `allowedActions()`, **không tự suy** |
| **P2 Cây** | `MaterializedPath` + `TreeBuilder` | Path phải có `/` hai đầu; move dùng `substring` chứ không `replace`; chống tạo vòng trước khi ghi |
| **P3 Tệp đính kèm** | `AttachmentService.upload(...)` với danh sách MIME cho phép của loại tài liệu đó | Tệp **chưa quét xong thì chưa tải xuống được**; đuôi tệp lấy theo MIME phát hiện được, không theo tên gốc |
| **P4 Thông báo** | `NotificationService.notify(NotificationRequest)` | Ghi trước, gửi sau — **cấm gọi SMTP đồng bộ trong request**. Người nhận đích danh và người nhận suy ra từ nhóm lọc khác nhau — xem javadoc `RecipientResolver` |
| **P5 Job nền** | Khai một bean cài `JobHandler`; worker tự tìm thấy qua Spring | Handler **phải chạy lại được** (job có thể thử tới `maxAttempts`, và job đang chạy lúc node chết sẽ được trả về hàng đợi). Việc theo lịch thì *đặt việc* vào hàng đợi, đừng tự làm trong `@Scheduled` |
| **P6 Tham số** | `SettingService.getInt/getBoolean/getTime(...)` với giá trị dự phòng | Tham số nghiệp vụ **cấm** để trong `application.yml` (quy tắc 12). Giá trị dự phòng trong mã phải bằng đúng giá trị seed |

**Nhật ký kiểm toán tự động**: gắn `@Audited(module=…, entityType=…)` lên entity là đủ — `AuditEventListener` bắt mọi tạo/sửa/xoá ở tầng Hibernate. Trường nhạy cảm khai trong `excludeFields` (vẫn thấy *có* thay đổi, không thấy giá trị).
  - ⚠ **Giới hạn**: câu lệnh `@Modifying` hàng loạt (JPQL/native) **không** đi qua bộ lắng nghe — Hibernate không nạp entity nên không có sự kiện nào để bắt. Thao tác cần dấu vết phải đi qua entity, hoặc tự gọi `AuditService.record(...)`.
  - ⚠ **Cắm listener bằng `Integrator` lúc dựng SessionFactory**, không đăng ký sau khi app đã lên: Hibernate 6 chốt các nhóm listener vào `FastSessionServices` ngay khi dựng, nên `appendListeners` gọi trong `@PostConstruct` chạy trót lọt mà **không bao giờ được gọi** — nhật ký trống rỗng, không lỗi nào báo ra.

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
- **Vòng đời mật khẩu (chốt 18/8, dựng ở Phase 1)** — đặc tả `function-spec.md` M5.15-a, lý do `architecture-review.md` §9.13: hạn **90 ngày** rồi khoá tài khoản · luồng tự đặt lại có **giãn cách 90 ngày**, trong kỳ thì quản trị viên cấp mật khẩu tạm · **đổi chủ động thì KHÔNG giãn cách** (người nghi lộ mật khẩu phải đổi được ngay).
  - ⛔ **Email của luồng tự phục vụ gửi LIÊN KẾT một lần, không gửi mật khẩu.** Gửi mật khẩu mới theo yêu cầu chưa xác thực nghĩa là ai biết tên đăng nhập cũng vô hiệu hoá được mật khẩu của người khác. Mật khẩu tạm chỉ có ở đường quản trị viên cấp. Bảng `password_reset_tokens` lưu **băm** của mã, không lưu mã.
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
  - ⚠ **Aspect phải nằm BÊN TRONG bộ chặn transaction**, nếu không `enableFilter` rơi vào một `Session` tạm bị vứt đi và **mọi Xí nghiệp đọc được dữ liệu của nhau, không một dòng lỗi**. Số `@Order` nhỏ hơn = vòng ngoài, nên bộ chặn transaction được kéo lên `CorePlatformConfig.TRANSACTION_ADVISOR_ORDER`; hai chỗ đó phải đọc cùng nhau (`architecture-review.md` §9.8.1). Đây là lỗi có thật, sống sót từ WS-5 tới khi WS-10 có entity thật để thử.
  - ⚠ **Lớp con `ScopedEntity` bắt buộc khai `@Filter`** kèm đúng hằng `ORG_UNIT_FILTER_CONDITION` — `@FilterDef` chỉ *định nghĩa* bộ lọc, Hibernate chỉ *áp* nó cho entity có khai. Luật ArchUnit canh điều này.
  - **Tra theo `public_id` phải đi qua `ScopeGuard.require(...)`**: bản ghi tồn tại mà ngoài phạm vi thì trả `AUTH-3002` + ghi `security_events`, không trả 404. Trả 404 thì đúng là dữ liệu không lọt ra, nhưng người dò `public_id` để tìm hồ sơ đơn vị khác trông y hệt người gõ nhầm đường dẫn — và `AUTH-3002` thành mã lỗi chết.
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
  - ⚠ Kết nối của role đó **không được khai thành bean `DataSource` hay `JdbcTemplate`**: hai kiểu này đều được Spring Boot tự cấu hình kèm `@ConditionalOnMissingBean`, nên khai thêm một bean cùng kiểu làm Boot **ngừng tạo bản chính** — cả ứng dụng lặng lẽ chuyển sang chạy bằng role archiver. Bọc vào một kiểu riêng (`ArchiverJdbc`). Đã sập cả hai lần khi làm WS-6; triệu chứng là `permission denied for table jobs` rồi `permission denied for table audit_logs`, không dòng nào nhắc tới DataSource.
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
- Dependency: scan CVE **theo lịch** (`security-scan.yml` hằng đêm, không gắn vào PR — lý do ở `docs/cicd.md` §3.3) — fail với CVE CVSS ≥ 7. Ở PR chỉ chạy `dependency-review-action` soi phần PR thêm vào.

**Xử lý kết quả quét CVE — 3 luật, rút từ lượt quét thật 18/8 (50 CVE ≥ 7):**

1. **Nâng cấp trước, suppress sau.** 49/50 mã biến mất chỉ bằng một dòng `spring-boot 3.5.3 → 3.5.16`. Suppression chỉ dành cho phần *không xử lý được bằng phiên bản*.
2. ⛔ **Tra phiên bản mới nhất bằng `https://repo1.maven.org/maven2/<path>/maven-metadata.xml`, KHÔNG bằng `search.maven.org/solrsearch`.** API tìm kiếm trả kết quả cũ — hôm 18/8 nó báo 3.5.3 (20/6/2025) là bản mới nhất trong khi dòng 3.5.x đã tới 3.5.16, suýt dẫn tới việc lập suppression cho 49 CVE **có bản vá sẵn**. Sai lầm loại này im lặng và trông rất giống làm việc cẩn thận.
3. **Mọi mục suppression PHẢI có `until`, và lý do phải là "không áp dụng", không phải "chưa có bản vá".** File `backend/dependency-check-suppressions.xml` giữ luật này trong chính header của nó. Suppression không hạn là cách êm ái nhất để một lỗ hổng thật biến mất khỏi tầm mắt; hết hạn thì phép quét tự đỏ lại và buộc nhìn lại. CVE chạm thật tới hệ mà chưa có bản vá thì thuộc **sổ nợ + phương án giảm nhẹ**, không thuộc file này.

4. ⚠⚠ **Điểm in ra trong thông báo lỗi KHÔNG phải điểm dùng để chặn.** Dependency-Check in điểm **CVSS v4** (thang mới nhất có), nhưng chặn theo **điểm cao nhất trong mọi thang**. Nên hoàn toàn bình thường khi thấy:
>
> ```
> One or more dependencies … CVSS score greater than or equal to '7.0':
>   log4j-api-2.24.3.jar … CVE-2026-34479(6.9)     ← in ra v4 = 6.9
> ```
>
> Mã đó có **v3 = 7.5**, và đó mới là cái vượt ngưỡng. Trông như cổng chặn hỏng, thực ra nó đúng.
>
> Hệ quả cho mọi script đọc `dependency-check-report.json`: **phải lấy `max` của `cvssv2`/`cvssv3`/`cvssv4`**, không lấy cái cuối cùng gặp được. Tôi đã mắc đúng lỗi này ngày 18/8 — vòng lặp `for k in ("cvssv3","cvssv4")` để v4 ghi đè v3, làm **đếm thiếu**: báo cáo "66 → 9 → 3" bị đọc nhầm thành "56 → 6 → 0", tức là tưởng đã sạch trong khi còn 3 mã. Trường `severity` cấp trên cùng cũng lấy theo v4 (`MEDIUM`) nên **không dùng được** để lọc.

5. ⚠⚠ **Phép kiểm dừng ở lỗi đầu tiên thì mỗi lượt lại giấu đi phần còn lại.** Goal `check` của Dependency-Check chạy riêng từng module, mà Maven dừng reactor ở module đầu tiên hỏng — năm module sau in `SKIPPED`, tức **chưa từng được quét**. Bốn lượt quét liên tiếp ngày 18/8 chỉ soi đúng module `core`; `app` mãi tới lượt thứ năm mới lộ ra CVE-2026-54291 (8.2, driver PostgreSQL âm thầm hạ cấp channel binding). Đáng sợ hơn cả sự phiền toái: **nếu module đầu tình cờ sạch thì ta tưởng cả dự án sạch**. Dùng `dependency-check:aggregate` — soi gốc + mọi module con trong một lượt, một danh sách, một báo cáo. Nguyên tắc áp cho mọi phép kiểm chạy lâu: **ưu tiên báo cáo trọn vẹn hơn là dừng sớm** (`-fae`, `aggregate`, gom lỗi rồi mới ném).
6. ⚠ **Cảnh báo lặp lại hàng trăm lần mỗi lượt là một hỏng hóc, không phải nền nhiễu.** Sonatype OSS Index đổ **130 cảnh báo mỗi lượt quét** suốt 4 lượt liền — tức nó lỗi ở gần như mọi artifact và **chưa từng đóng góp dữ liệu nào**. Không ai đọc, vì nó chỉ là `[WARNING]`. Tới lượt thứ tư Sonatype chặn truy cập ẩn danh (401) thì DC nâng lên `AnalysisException` và **giết cả build đúng lúc cổng CVE vừa sạch**. Nguồn dữ liệu hỏng thì tắt đích danh và ghi lại, đừng để nó nằm đó kêu.
   ⛔ Và đừng chữa bằng `failOnError=false`: nó nuốt **mọi** lỗi phân tích, kể cả của analyzer đang chạy thật — biến một hỏng hóc nhìn thấy được thành một hỏng hóc im lặng.

> ⚠ Nâng phiên bản để vá bảo mật thì **giữ trong cùng dòng minor** (3.5.x → 3.5.x). Nhảy major là hạng mục riêng, không gộp vào một lượt vá — và không phải nâng nào cũng đi được: `minio 8.6.0` kéo okhttp 5.x phát hành kiểu Kotlin Multiplatform, Maven không giải được biến thể nên vỡ biên dịch (chi tiết ghi tại chỗ trong `pom.xml`).

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

## 10. Task Tracking (SSoT)

- Mọi thao tác cập nhật tiến độ, đánh dấu hoàn thành `[x]`, hoặc thêm take note đều CHỈ ĐƯỢC PHÉP thực hiện tại file `.claude/master-tracking.md`.
- Tuyệt đối tuân thủ quy tắc tổng hợp: xóa bỏ mọi icon, markdown thừa; mô tả vắn tắt đúng trọng tâm hành động.
- Cú pháp quy ước: `- [x] T1.1: Tên task | Date: DD/MM/YYYY | Note: ghi chú ngắn gọn` (phần Date và Note là tuỳ chọn).
- Các file phase cũ (phase0, phase1...) chỉ là tài liệu lưu trữ, cấm sửa đổi.
