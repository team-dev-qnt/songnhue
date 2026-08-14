# PHASE 0 — CORE PLATFORM · BẢNG THEO DÕI TIẾN ĐỘ

> **Cập nhật lần cuối**: 2026-08-14 · **Tiến độ: 46/107 task (43%)** · **DoD: 7/21** (mục 7 xong tầng 2, tầng 3 chờ WS-10) · Trạng thái: 🟡 Đang làm (xong WS-1, WS-2, WS-4, WS-5; WS-3 còn T3.4)
> Nguồn ràng buộc: `conventions.md` (coding/security) · `architecture-review.md` §6, §9 (kiến trúc đã chốt) · `function-spec.md` (nghiệp vụ MOD-05)
> **Cách dùng**: làm xong task nào tick `[x]` task đó; xong 1 WS thì chạy mục "Kiểm chứng" của WS rồi cập nhật bảng tổng + dòng "Cập nhật lần cuối" ở trên.

---

## Bảng tổng

| WS | Hạng mục | Task | Xong | Trạng thái | Phụ thuộc | Ước tính |
|---|---|:-:|:-:|---|---|:-:|
| **WS-1** | Repo & quy ước nền | 6 | **6** | ✅ **Xong** (13/8) | — | 2 pd |
| **WS-2** | DB & Migration | 10 | **10** | ✅ **Xong** (14/8) | WS-1 | 8 pd |
| **WS-3** | Docker & môi trường chạy local | 7 | **6** | 🟡 **6/7** (14/8) — T3.4 chờ WS-8/9 | WS-1 | 5 pd |
| **WS-4** | BE — Common Platform | 10 | **10** | ✅ **Xong** (14/8) | WS-2 | 10 pd |
| **WS-5** | BE — Auth & RBAC 3 tầng | 14 | **14** | ✅ **Xong** (14/8) | WS-4 | 15 pd |
| **WS-6** | BE — Core services | 15 | 0 | ⬜ Chưa bắt đầu | WS-4, WS-5 | 25 pd |
| **WS-7** | BE — Backup/Restore & Observability | 12 | 0 | ⬜ Chưa bắt đầu | WS-6 | 9 pd |
| **WS-8** | FE — admin-app | 11 | 0 | ⬜ Chưa bắt đầu | WS-4→6 (API) | 15 pd |
| **WS-9** | FE — public-web | 5 | 0 | ⬜ Chưa bắt đầu | WS-1 | 5 pd |
| **WS-10** | Test & CI | 7 | 0 | ⬜ Chưa bắt đầu | WS-4 | 10 pd |
| **WS-11** | Deploy Staging & Production | 10 | 0 | ⬜ Chưa bắt đầu | WS-3, 7, 10 | 10 pd |
| | **TỔNG** | **107** | **46** | | | **114 pd** |

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

## WS-1 — Repo & quy ước nền · 2 pd — ✅ **XONG 13/8/2026**

**Tiên quyết**: không có. **Đầu ra**: repo build được, lint chạy được, `make` có đủ lệnh.

- [x] **T1.1** Tạo cấu trúc monorepo + `.gitignore`, `.editorconfig`, `.gitattributes` — *layout: `conventions.md` §1.7*
- [x] **T1.2** Maven parent `backend/pom.xml`: Java 21, **Spring Boot 3.5.3**, 6 module con (`core/content/operations/hydro/hr/app`), `spring-boot-maven-plugin` ở `app/` — *§1.1*
- [x] **T1.3** Spotless + Checkstyle (BE), ESLint + Prettier (FE) — chạy được ở local và CI — *§1.5*
- [x] **T1.4** `.env.example` cho `local`/`staging`/`prod`, liệt kê **đủ key**, không giá trị thật — *§1.6, cấm commit `.env`*
- [x] **T1.5** `Makefile` 21 lệnh: `dev-infra`, `dev-native`, `dev-docker`, `migrate`, `test`, `backup`, `restore`… — *§1.7*
- [x] **T1.6** Commit convention (hook `commit-msg`) + PR template gắn **Definition of Done** — *§1.5, §5*

**Kiểm chứng — đã chạy**:
- ✅ `./mvnw clean verify` → **BUILD SUCCESS**, 7/7 module, 0 Checkstyle violation
- ✅ `make` → liệt kê **21 lệnh**; lệnh phụ thuộc WS-3 báo lỗi có hướng dẫn thay vì chết câm
- ✅ `make lint` → Spotless + Checkstyle + ESLint + Prettier đều xanh
- ✅ **Checkstyle bắt lỗi thật**: file thử vi phạm `System.out` / `new Date()` / `catch(Throwable)` / empty catch → 4 violation, build đỏ
- ✅ **Hook commit-msg**: message sai → chặn (exit 1); đúng Conventional Commits → qua
- ✅ **`.gitignore` chặn secret**: tạo `deploy/env/local.env` thật → không xuất hiện trong `git status`

**Quyết định phát sinh khi làm** (khác/bổ sung so với kế hoạch):
| Việc | Xử lý |
|---|---|
| Máy chưa có Maven | Sinh `mvnw` wrapper (Maven 3.9.9, loại `only-script`) bằng Docker — không bắt dev cài Maven |
| `${maven.multiModuleProjectDirectory}` trỏ vào `backend/`, không phải gốc repo | Chuyển checkstyle config về `backend/config/checkstyle/` thay vì hack `../` |
| Spotless `sortPom` mặc định indent 2, lệch `.editorconfig` (xml = 4) | Ép `nrOfIndentSpace=4` — nếu không, IDE và Spotless sẽ liên tục sửa ngược nhau |
| Formatter Java | **Palantir Java Format** (4 space, 120 cột) — khớp `.editorconfig`, khác google-java-format (2 space) |
| ESLint chặn kiến trúc FE | Thêm `no-restricted-imports` (axios, moment) + `no-restricted-globals` (fetch) → ép mọi request đi qua `shared/apiClient`; miễn trừ cho chính `shared/apiClient` |

---

## WS-2 — DB & Migration · 8 pd — ✅ **XONG 14/8/2026**

**Tiên quyết**: WS-1. **Đầu ra**: DB rỗng chạy migration ra đủ schema Core + seed + phân quyền role.

- [x] **T2.1** Image `postgis/postgis:16-3.4`; bật extension `postgis`, `unaccent`, `pg_trgm` — *architecture §3*
- [x] **T2.2** Flyway đa module: mỗi module `resources/db/migration/<prefix>/`, `app` gộp qua `spring.flyway.locations`. Bật `validateOnMigrate=true`, `outOfOrder=false`, **`cleanDisabled=true`** — *§1.2*
- [x] **T2.3** Migration `core` — bảng nền: `users`, `roles`, `permissions`, `role_permissions`, `user_roles`, `org_units`, `sessions`, `token_denylist`, `user_totp`
- [x] **T2.4** Migration `core` — nền tảng: `attachments`, `settings`, `jobs`, `notifications`, `notification_recipients`, `workflow_definitions`, `workflow_transitions`, `holidays`, `code_sequences`, `shedlock`, `security_events`
- [x] **T2.5** Migration `core` — `audit_logs` **partition RANGE theo tháng** + cột `hash`/`prev_hash`; bảng `audit_archive_anchors` giữ điểm neo hash chain — *§4.3 + G7*
- [x] **T2.6** Job tạo partition tháng kế tiếp (chạy trước hạn, **idempotent**) — tránh insert lỗi đầu tháng
- [x] **T2.7** **DB roles tách quyền**: `songnhue_owner` (migrator) · `songnhue_app` (**không DELETE** trên `audit_logs`/`hydro_raw_logs`) · `songnhue_archiver` · `songnhue_readonly`. GRANT trong migration, CREATE ROLE ở init script — *§1.2, §4.3 "enforce ở tầng DB"*
- [x] **T2.8** Cột chuẩn: `id BIGINT IDENTITY`, **`public_id UUID`**, `created_at/by`, `updated_at/by`, `deleted_at`, `version`; enum lưu `VARCHAR` + CHECK — *§1.2*
- [x] **T2.9** Seed: org_units gốc, roles + permissions dịch từ ma trận RBAC `function-spec.md` §6, tài khoản Super Admin (bắt đổi mật khẩu + bắt buộc 2FA)
- [x] **T2.10** Seed `settings` — các tham số bắt buộc theo `function-spec.md` CN-05.3 (giờ hành chính 08:00–17:00, retention 5 năm, cron polling `45 1/2 * * * *`, khung 10', ngưỡng mất tín hiệu 3 khung…) — *rule 12 CLAUDE.md*

**Kết quả**: 9 migration · **40 bảng** (25 bảng nghiệp vụ + 15 partition audit) · **88 permission** · **12 vai trò** · **334 dòng phân quyền** · **55 tham số cấu hình**.

**Kiểm chứng — đã chạy trên volume Postgres rỗng hoàn toàn**:
- ✅ Init script tạo **4 role + 3 extension**; `make migrate-native` → 9/9 migration, không lỗi
- ✅ Chạy migrator **lần 2 → exit 0**, không áp dụng lại (idempotent, `validateOnMigrate` qua)
- ✅ `make migrate-info` liệt kê đủ 9 version, `success = t`
- ✅ **App thường khởi động với `FLYWAY_ENABLED=false`** → health UP, **0 dòng log Flyway** (đúng mô hình migrator riêng)
- ✅ **`songnhue_app` bị DB từ chối**: `UPDATE`/`DELETE` `audit_logs` (cả qua bảng cha lẫn **thẳng vào partition**), `TRUNCATE` partition, mọi thao tác trên `audit_chain_head`, `DELETE security_events`, ghi `flyway_schema_history`
- ✅ **Client không giả được chuỗi hash**: INSERT kèm `seq=999999` + `hash=f×64` → trigger ghi đè thành `seq=4` và hash thật
- ✅ **Phát hiện sửa lén**: tắt trigger bằng superuser rồi `UPDATE` → verify báo *"Nội dung bản ghi không khớp hash"*
- ✅ **Phát hiện xóa lén**: archiver xóa 1 dòng giữa chuỗi → verify báo *"prev_hash không khớp bản ghi liền trước"*
- ✅ **Trigger chặn UPDATE với cả `songnhue_owner`**, không riêng app user
- ✅ Định tuyến partition đúng (bản ghi lùi 20 ngày → `audit_logs_p202607`); **`audit_logs_default` rỗng**; gọi lại hàm tạo partition → tạo thêm **0** (idempotent)
- ✅ `songnhue_readonly` đọc được, **ghi bị từ chối**
- ⬜ **Chưa kiểm chứng bằng chạy thật**: `clean-disabled=true` — chỉ là cấu hình, không có đường code nào trong app gọi `flyway clean`. Đưa vào test tự động ở WS-10.

**Quyết định phát sinh khi làm** (khác/bổ sung so với kế hoạch):
| Việc | Xử lý |
|---|---|
| Hash chain tính ở đâu | **Trong DB bằng trigger `SECURITY DEFINER`**, không ở Java — app không có `UPDATE` trên `audit_chain_head` nên không tự nối chuỗi được. Đổi lại insert audit bị tuần tự hóa qua 1 dòng khóa (chấp nhận được ở tải này) |
| Partition hết runway thì sao | Thêm partition **`DEFAULT`** làm lưới an toàn + tạo sẵn **12 tháng**. Thà ghi chậm còn hơn `INSERT` lỗi làm hỏng giao dịch nghiệp vụ. Runbook gỡ kẹt: `docs/runbook/audit-partition.md` |
| Mật khẩu Super Admin | Seed `PENDING_ACTIVATION` + `password_hash = '!'` — **không có mật khẩu mặc định trong repo**. Kích hoạt bằng `BOOTSTRAP_ADMIN_PASSWORD` → **thêm việc cho T5.7** |
| `security_events` | Siết append-only **giống `audit_logs`** (kế hoạch chỉ nêu `audit_logs`/`hydro_raw_logs`) — cũng là bằng chứng điều tra sự cố |
| Quyền trên partition | Không kế thừa từ bảng cha khi truy vấn thẳng vào partition → hàm tạo partition phải tự `GRANT`/`REVOKE`, nếu không app xóa được bằng `DELETE FROM audit_logs_p202608` |
| Repeatable migration `R__` | **Không dùng** cho danh mục quyền/settings — `R__` ghi đè âm thầm, trái quy tắc "cấm sửa migration đã merge" |
| Tên cột `key`/`value` | Đổi thành `setting_key`/`setting_value` — `KEY()`/`VALUE()` là từ khóa JPQL |
| Checkstyle `ConstantName` | Cho phép thêm tên `log` — Lombok `@Slf4j` sinh field tên `log`, bắt viết hoa thì logger tay và logger sinh tự động lệch tên nhau |
| `make migrate-info` (từ WS-1) | Trỏ `flyway-maven-plugin` **chưa hề được cấu hình** → viết lại bằng `psql` đọc `flyway_schema_history`. Thêm `make migrate-native`, `make db-verify-audit` |
| Danh sách Xí nghiệp/phòng ban | **Cố ý không seed** — cơ cấu tổ chức thật nằm ở mục **G8** còn chờ Công ty. Chỉ seed đơn vị gốc `CTY` |
| Ngày lễ | Chỉ seed lễ **dương lịch cố định** (2026–2027). Tết, Giỗ Tổ, ngày nghỉ bù Quốc khánh đổi theo năm → Admin nhập qua UI |
| Quyền của `ADMIN` | **Không** có `hr:employee:view-sensitive` — §6 ghi trường 🔒 chỉ Admin HR + chính NV. Chỗ dễ sai nhất nếu hiểu "Admin = toàn quyền" |

---

## WS-3 — Docker & môi trường chạy local · 5 pd — 🟡 **6/7 (14/8/2026)**

**Tiên quyết**: WS-1. **Đầu ra**: chạy được **cả 4 chế độ** — chọn từng service chạy native hay Docker.
📘 Tài liệu người dùng: `docs/setup-guideline.md` (dựng máy) · `docs/run-guideline.md` (chạy hằng ngày).

- [x] **T3.1** `compose.infra.yml` — `postgres`(+PostGIS), `minio`(+`mc` tạo bucket), `mailpit`; **expose port ra host** để app chạy native từ IDE
- [x] **T3.2** `compose.local.yml` — `include` infra + `migrator`/`app`/`admin-app`/`public-web`, chọn service bằng **Compose profile**
- [x] **T3.3** `Dockerfile` backend: multi-stage (maven build → JRE 21 alpine), **non-root**, healthcheck theo `/actuator/health/readiness`
- [ ] **T3.4** `Dockerfile` admin-app (build → nginx static) và public-web (Next standalone) — ⚠ **file đã viết, CHƯA build được**: `frontend/admin-app` và `frontend/public-web` do WS-8/WS-9 tạo. `make dev-fe`/`dev-docker` chặn sớm kèm thông báo rõ
- [x] **T3.5** Script init Postgres: extension + CREATE ROLE — *đã làm ở WS-2 (`deploy/postgres/init/`), WS-3 đấu vào compose*
- [x] **T3.6** Profile Spring — **chỉ khác env, không khác code** — *§1.6*
- [x] **T3.7** `make dev-infra` / `dev-be` / `dev-fe` / `dev-docker` / `dev-native` + `make doctor` + 2 tài liệu hướng dẫn

**Bốn chế độ chạy**:
| Lệnh | Docker chạy | Native | Cho ai |
|---|---|---|---|
| `make dev-infra` | PG · MinIO · Mailpit | BE + FE | Fullstack |
| `make dev-be` | + backend | FE | **Người làm FE** — không cần JDK |
| `make dev-fe` | + 2 app FE | BE | **Người làm BE** — không cần Node |
| `make dev-docker` | tất cả | — | QA / demo |

**Kiểm chứng — đã chạy**:
- ✅ `make dev-infra` → 4 container healthy; 3 bucket MinIO tạo xong, bucket audit **bật versioning**
- ✅ `make dev-be` → `migrator` chạy trước và **exit 0**, app khởi động sau, health UP ở cổng 18080
- ✅ App trong Docker **không tự chạy Flyway** (0 dòng log) — đúng mô hình `migrator` riêng của production
- ✅ **Build từ mã nguồn local**: lần đầu ~9'38"; **sửa code → build lại 6,9 giây**; `make dev-be BUILD=1` trọn gói **10,7 giây**
- ✅ Chứng minh trực tiếp: thêm `application-local.yml` → chưa rebuild thì health không đổi; `BUILD=1` xong health hiện đủ components
- ✅ **Chạy song song backend native (8080) + backend Docker (18080)**, cùng nối PostgreSQL container ở 15432, không xung đột
- ✅ Image chạy bằng **user thường** (`uid=100 songnhue`), dung lượng 339MB
- ✅ Collation `ICU vi-VN`: `Anh < Dung < Đăng < Em` (mặc định sẽ xếp "Đăng" sau "Em")
- ✅ `make doctor` liệt kê công cụ + 8 cổng, phát hiện đúng cổng bị chiếm
- ✅ `make dev-fe` / `dev-docker` khi thiếu app FE → dừng sớm, chỉ rõ **WS-8 / T8.1**
- ⬜ **Chưa kiểm chứng**: 2 image FE (T3.4) — không có `frontend/admin-app`, `frontend/public-web` để build

**Quyết định phát sinh khi làm**:
| Việc | Xử lý |
|---|---|
| Chọn service chạy Docker | Dùng **Compose profile** thay vì nhiều file compose — hạ tầng không profile nên luôn chạy, 3 service ứng dụng bật/tắt độc lập |
| **Hot-reload bind-mount trong Docker** | **Bỏ** (kế hoạch T3.2 có nêu). Người cần hot-reload thì chạy native; bind-mount làm lệch `node_modules` macOS↔Linux, `target/` thành root-owned, và chậm trên macOS. Docker giữ đúng chế độ giống production |
| ⚠ **Cổng Docker đụng cổng native** | Máy dev có PostgreSQL native ở 5432; Docker bind `*:5432` **không báo lỗi** nhưng `localhost` trên macOS đi vào `::1` → app **lặng lẽ nối nhầm DB của máy**. Sửa: cổng Docker sang **dải riêng** (thêm số `1` vào đầu: 15432/19000/18080…) **và** bind đúng `127.0.0.1` để trùng cổng là báo lỗi ngay |
| MailHog → **Mailpit** | MailHog không còn bảo trì và **không có image arm64** (Apple Silicon phải giả lập). Mailpit cùng cổng 1025/8025, thay thẳng |
| **Collation DB** | Chốt **ICU `vi-VN`** ngay từ đầu — đổi sau khi có dữ liệu là dump+restore. Staging/Production phải dùng đúng `POSTGRES_INITDB_ARGS` này |
| Profile Spring `docker` | **Không tạo**. Native và Docker khác nhau đúng ở env, một file profile rỗng chỉ tạo chỗ cho hai lối chạy âm thầm lệch nhau. `staging`/`prod` cũng chưa tạo cho tới khi có nội dung thật |
| Nơi để Dockerfile | Gom hết ở `deploy/docker/` (§1.7 cũ vẽ Dockerfile nằm trong `app/`) — một chỗ cho mọi định nghĩa container |
| Khóa JWT/AES trong container | Mount `deploy/keys` vào `/app/keys` **lúc chạy**, không bake vào image |
| `make doctor` | Thêm mới — sự cố trùng cổng biểu hiện rất khó đoán, cần một lệnh chỉ thẳng ra nguyên nhân |

---

## WS-4 — BE Common Platform · 10 pd — ✅ **XONG 14/8/2026**

**Tiên quyết**: WS-2. **Đầu ra**: nền chung mà mọi module sau bắt buộc dùng, cấm tự chế bản riêng.
📦 Toàn bộ nằm ở `com.songnhue.core.common.*` — **ngoại lệ duy nhất** của quy tắc "chỉ import `spi/` của module khác".

- [x] **T4.1** `ApiResponse<T>`, `ApiError`, `ErrorDetail` + `ResponseEnvelopeAdvice` (ResponseBodyAdvice) — controller chỉ return DTO — *§2.1*
- [x] **T4.2** `AppException` + 8 subclass đúng §2.2; `GlobalExceptionHandler` map toàn bộ; exception lạ → `SYS-0001`, **cấm lộ stacktrace/SQL** — *§2.2*
- [x] **T4.3** `ErrorCode` enum sinh từ catalog §2.3 (**31 mã**) + `error-messages.properties`; test đảm bảo mọi mã có message — *§2.3*
- [x] **T4.4** Filter chain **đúng thứ tự** qua hằng `FilterOrder`: `Correlation` → `RequestLogging` → `RateLimit` → *(chừa AuthFilter, ScopeContextFilter cho WS-5)* → `AuditContext` — *§2.4*
- [x] **T4.5** `RateLimitFilter` qua interface `RateLimitStore` (impl Caffeine in-process) — login 5/15', API 100/phút, export 10/giờ — *§4.5; ≥2 node phải đổi impl sang DB*
- [x] **T4.6** 8 utils: `DateTimeUtils`, `NumericUtils`, `VietnameseUtils`, `CodeGenerator`(DB sequence), `MaskUtils`, `PageUtils`(whitelist sort), `FileValidator`(magic bytes), `CryptoService`(AES-256-GCM + `key_id`) — *§2.5, cấm module viết lại*
- [x] **T4.7** `BaseEntity` / `ScopedEntity` + JPA auditing + soft delete + `@Version` — *§2.5*
- [x] **T4.8** `@ConfigurationProperties` + `@Validated` cho mọi nhóm config → **fail-fast lúc startup** khi thiếu env — *§1.6*
- [x] **T4.9** Log JSON (cơ chế sẵn có của Boot 3.4+) + `traceId` trong MDC; `RequestLoggingFilter` (method/path/status/duration, **không log body nhạy cảm**) — *§2.4, §4.5*
- [x] **T4.10** springdoc-openapi: `/api/v1/**`, **6 nhóm theo module** — *§1.3*

**Kiểm chứng — 53 test xanh + chạy thật**:
- ✅ `ErrorCatalogTest`: 31/31 mã có message · không có khoá thừa · không mã nào lộ chi tiết kỹ thuật · định dạng mã và HTTP status hợp lệ
- ✅ **Envelope trên request thật**: 404 trả `{"success":false,"error":{"code":"SYS-0004",…},"traceId":"…"}` — trước WS-4 là format mặc định của Spring
- ✅ **traceId** có trong header `X-Trace-Id`, trong body, và trong **mọi dòng log**; nhận lại traceId phía gọi gửi sang; traceId rác (`x'; DROP TABLE…`) bị loại
- ✅ **Không rò rỉ**: `IllegalStateException` mang tên bảng/cột → response chỉ có `SYS-0001` + traceId, không có tên class, tên bảng, `com.songnhue`
- ✅ **Rate limit đăng nhập**: 5 lượt qua, lượt 6 → **429 + `SYS-0002`** + `Retry-After: 899`; bucket API không bị ảnh hưởng
- ✅ **Fail-fast**: khoá AES sai định dạng → app **không khởi động** (exit 1), thông báo chỉ rõ `openssl rand -base64 32`
- ✅ **Xoay khoá AES**: bản ghi mã bằng `v1` vẫn giải mã sau khi chuyển sang `v2`; sửa 1 ký tự bản mã → giải mã hỏng ngay (AEAD)
- ✅ **OpenAPI** 6 nhóm `00-core … 05-adm`; `/actuator/**` và `/v3/api-docs` **không** bị bọc envelope
- ✅ **Checkstyle vẫn bắt lỗi thật** sau khi đổi cấu hình: file thử vi phạm `System.out` / `new Date()` / `catch(Throwable)` / `printStackTrace()` → 4 violation, build đỏ; chữ trong Javadoc và bình luận **không** bị tính

**Hai lỗi do test phát hiện** (đều sẽ lộ ra ở production nếu không có test):
| Lỗi | Nguyên nhân |
|---|---|
| Response lỗi bị bọc envelope **hai lần** → `success:true` với lỗi nằm trong `data` | `supports()` lọc theo kiểu trả về, mà handler lỗi khai báo `ResponseEntity<ApiResponse<…>>` nên `getParameterType()` ra `ResponseEntity`. Sửa: nhận diện theo **body thật** |
| **Không message nào tra được**, mọi lỗi trả khoá thô `OPS-2001` | Spring Boot chỉ tạo `MessageSource` khi có file đúng basename. Chỉ có `error-messages_vi.properties` → điều kiện không thoả. Sửa: đổi tên thành `error-messages.properties` |

**Quyết định phát sinh khi làm**:
| Việc | Xử lý |
|---|---|
| Thiếu mã lỗi chung cho 8 subclass exception | Thêm **6 mã `SYS-0003…0008`** (400/404/409/502/503/422) — catalog gốc chỉ có mã theo nghiệp vụ, không có mã mặc định cho tầng framework. Tổng còn **31 mã** |
| Ranh giới module với `core.common` | `core.common.*` là **ngoại lệ được phép import chéo**. Đã ghi vào `conventions.md` §1.1 — **T10.2 phải viết rule ArchUnit theo đúng điều này** |
| Hạn mức rate limit để ở đâu | **Hằng số trong mã**, KHÔNG ở bảng `settings` như đa số tham số khác — đây là chốt chặn bảo mật; để Admin sửa được thì tài khoản Admin bị chiếm sẽ tự nới hạn mức trước khi dò mật khẩu |
| Ghi log truy cập | Cài là **filter** chứ không phải `HandlerInterceptor` như §2.4 viết — interceptor không thấy request bị chặn ở tầng filter (429) và không đo trọn thời gian |
| Log JSON | Dùng `logging.structured.format` **có sẵn từ Boot 3.4**, không thêm `logstash-logback-encoder` — bớt một phụ thuộc phải theo dõi CVE |
| Profile `docker` | Đã bỏ ở WS-3; `application-local.yml` là file profile duy nhất |
| Checkstyle bắt nhầm chữ trong bình luận | Đổi 3 rule sang **`RegexpSinglelineJava` + `ignoreComments`** — bản cũ quét cả Javadoc nên chính câu "cấm new Date()" trong tài liệu bị báo vi phạm |
| Sinh mã nghiệp vụ | `CodeGenerator` chạy `REQUIRES_NEW` → có thể **nhảy số** khi giao dịch ngoài rollback. Chấp nhận: nhảy số vô hại, mã trùng thì không |
| Ngoại lệ định dạng String | Controller trả `String` được tự serialize thủ công để envelope **không có ngoại lệ nào** (DoD #9) |
| `FileValidator` | Viết tay bảng magic bytes thay vì kéo Apache Tika (>10MB + cây phụ thuộc) — danh sách định dạng của dự án rất ngắn |

**Còn nợ, đã ghi chỗ cắm sẵn**: `AuditContextFilter` mới lấy được IP (`userId`/`username` chờ **WS-5**) · bộ lọc Hibernate theo `org_unit` mới khai báo `@FilterDef`, bật theo phiên ở **WS-5/T5.11** · `shared/error-map.ts` mirror 31 mã ở **WS-8/T8.4**.

---

## WS-5 — BE Auth & RBAC 3 tầng · 15 pd — ✅ **XONG 14/8/2026**

**Tiên quyết**: WS-4. **Đầu ra**: xác thực + phân quyền đủ mạnh, có 2 chốt chặn ở CI. **Đây là nơi dồn công của Phase 0** (`architecture-review.md` §9.4).

- [x] **T5.1** JWT **RS256**, keypair đọc từ file/env, `kid` trong header để xoay key; access token 30' — *§4.1*
- [x] **T5.2** Refresh token rotation lưu **httpOnly + Secure + SameSite=Strict cookie**; token family — *§4.1*
- [x] **T5.3** **Refresh reuse detection** → thu hồi cả family + force re-login + security event — *§4.1*
- [x] **T5.4** `token_denylist` bảng DB; đổi mật khẩu / khóa tài khoản → denylist toàn bộ token đang sống — *§4.1*
- [x] **T5.5** CSRF double-submit (`X-CSRF-Token`) cho mọi request thay đổi dữ liệu — *§4.1*
- [x] **T5.6** Login lockout 5 lần/15' → `AUTH-0003`; message chung `AUTH-0001` **không tiết lộ user có tồn tại** — *§4.1*
- [x] **T5.7** BCrypt cost ≥ 12; policy ≥10 ký tự chữ+số; bắt đổi mật khẩu lần đầu — *§4.1*
  - [x] **Kèm theo (phát sinh từ WS-2)**: lệnh bootstrap `superadmin` — `AdminBootstrapRunner`, đọc `BOOTSTRAP_ADMIN_PASSWORD`, **chỉ tác động khi tài khoản còn `PENDING_ACTIVATION`** (chạy lại với biến còn nguyên cũng không đặt lại mật khẩu)
- [x] **T5.8** **2FA TOTP bắt buộc Super Admin + Admin + Admin HR** (enroll, otpauth URI, verify, 10 mã khôi phục) — *§4.1 + G12*
- [x] **T5.9** Tầng 2 — `@RequirePermission("module:resource:action")` + `PermissionInterceptor` — *§4.2*
  - [x] **Kèm theo (từ WS-4)**: `AuditContextFilter` đã điền `userId`/`username` — kiểm chứng bằng `users.updated_by = 1` sau khi đổi mật khẩu
- [x] **T5.10** **Deny by default**: `DenyByDefaultTest` quét toàn bộ controller, thiếu annotation → **CI đỏ** — *§4.2*
- [x] **T5.11** Tầng 3 — `ScopeFilterAspect` bật Hibernate `@Filter` theo materialized path `org_unit` — *§4.2*
- [x] **T5.12** Lookup qua `public_id` UUID (`findByPublicIdAndDeletedAtIsNull`) — *§4.2 chống IDOR*
- [x] **T5.13** Quản lý phiên (M5.14): danh sách phiên gộp theo family + **đăng xuất từ xa** — *CN-05.7*
- [x] **T5.14** Cảnh báo đăng nhập bất thường (M5.16): `AbnormalLoginDetector` — ngoài **giờ hành chính đọc từ `settings`** → `security_events` — *CN-05.7, F5*

**Kiểm chứng — đã chạy thật trên CSDL Docker (14/8)**

| Hạng mục | Kết quả |
|---|---|
| Không tiết lộ tài khoản | Sai mật khẩu · tên không tồn tại · tài khoản DISABLED → **cùng `AUTH-0001`**, cùng câu chữ |
| Khoá tài khoản | Sai lần 5 → `AUTH-0003`; **mật khẩu ĐÚNG trong lúc khoá cũng bị chặn** |
| Ngưỡng khoá đọc từ `settings` | Đổi `max-failed-attempts` 5→3 trên DB → sau 60s (TTL cache) khoá đúng ở lần 3, **không deploy lại** |
| 2FA | Mã TOTP do client tự tính (RFC 6238) khớp máy chủ; Super Admin dừng ở `TWO_FACTOR_ENROLL_REQUIRED`, **không có access token** |
| Cookie | `refresh_token`: `HttpOnly; SameSite=Strict; Path=/api/v1/auth` · `XSRF-TOKEN`: đọc được, `SameSite=Strict` |
| CSRF | Có cookie + **thiếu header** (đúng hình dạng request giả mạo) → `AUTH-0005` |
| **Reuse detection** | Dùng lại refresh cũ → `AUTH-0008`; **refresh token mới của người dùng thật cũng chết** (`AUTH-0002`); **access token hết hiệu lực ngay**, không chờ hết 30' |
| Bắt đổi mật khẩu | `/auth/sessions` → `AUTH-0007`; `/auth/me` vẫn gọi được (không thì người dùng bị kẹt) |
| Đổi mật khẩu | Yếu → `AUTH-0006` + `details` theo từng luật, **`rejectedValue` rỗng** (mật khẩu không lọt ra response); thành công → thu hồi **cả phiên chưa hề đụng tới** |
| Quản lý phiên | 2 thiết bị, đánh dấu đúng phiên hiện tại; đăng xuất từ xa → thiết bị kia `AUTH-0002`, phiên của mình vẫn chạy |
| `security_events` | 8 loại ghi đủ, đúng mức: `REFRESH_REUSE_DETECTED`=CRITICAL, `LOGIN_LOCKED`=DANGER |
| Deny by default | Thêm 3 endpoint vi phạm → **CI đỏ**, chỉ đích danh cả 3 (thiếu annotation · mã quyền sai định dạng · module không tồn tại) |
| Kiểm token | 15 test: chặn **alg confusion** (ký HS256 bằng khoá công khai), **alg=none**, sửa nội dung, ký bằng khoá khác, dùng vé 2FA thay access token |
| Khoá JWT | Sai cặp khoá / thiếu file / đưa nhầm khoá công khai vào ô khoá riêng → **app không khởi động**, thông báo chỉ rõ cách sinh lại |

**5 lỗi chạy thật mới lộ ra** (unit test không bắt được):

| # | Lỗi | Nếu không phát hiện |
|---|---|---|
| 1 | `@EntityScan`/`@EnableJpaRepositories` thiếu — `scanBasePackages` **không** áp cho JPA | App chết lúc khởi động với thông báo "required a bean … could not be found", không hề gợi ý nguyên nhân thật |
| 2 | `@FilterDef` trên `@MappedSuperclass` **chưa có entity con** → Hibernate không đăng ký | `UnknownFilterException` ở **mọi** request có `@Transactional` — toàn bộ API lỗi 500 |
| 3 | Cột `CHAR(64)`/`inet` thiếu `@JdbcTypeCode` | `ddl-auto: validate` chặn khởi động (đúng vai trò, nhưng phải biết mới sửa nhanh) |
| 4 | **Rate limit login 5/15' theo IP chặn trước khoá tài khoản** | `AUTH-0003` không bao giờ kích hoạt được, tham số M5.15 vô nghĩa; và 200 người sau một IP NAT dùng chung hạn mức 5 lượt → cả cơ quan không đăng nhập được. Đã nâng lên 30/15', có test chặn ở CI |
| 5 | `make dev-native` hỏng: `-am` kéo POM cha vào, `spring-boot:run` chạy trên module không có main class | Không ai chạy được backend native bằng lệnh trong tài liệu |

**Quyết định đáng lưu ý**

| Việc | Vì sao |
|---|---|
| **Không dùng filter chain Spring Security**, chỉ lấy `spring-security-crypto` | `architecture-review.md` **§9.5** — FilterChainProxy chen trước `CorrelationFilter`, và 401/403 của nó không đi qua `GlobalExceptionHandler` (phá envelope + traceId) |
| `TransactionTemplate(REQUIRES_NEW)` thay cho `@Transactional(REQUIRES_NEW)` | Lời gọi trong cùng đối tượng **không đi qua proxy** → annotation bị bỏ qua lặng lẽ. Bộ đếm đăng nhập sai và nhật ký bảo mật nằm trên đường rollback, hỏng ở đây là **khoá tài khoản không bao giờ chạy** mà không có triệu chứng |
| Tách `PasswordChangeService` + `AbnormalLoginDetector` khỏi `AuthService` | Checkstyle báo 10 tham số constructor — sửa bằng cách tách đúng mối quan tâm, không nới luật |
| `TotpGenerator` tự cài | RFC 6238 có **bộ vector kiểm thử chính thức** → đúng đắn chứng minh bằng test, đổi lại bớt một phụ thuộc phải theo dõi CVE |
| QR do FE vẽ, máy chủ chỉ trả `otpauth://` | Sinh ảnh ở máy chủ là thêm một chỗ secret đi qua (bộ nhớ đệm, proxy, log truy cập) |

**Còn nợ, đã ghi chỗ cắm**: `shared/error-map.ts` mirror **36 mã** ở WS-8/T8.4 · luật ArchUnit bắt mọi lớp con `ScopedEntity` phải mang `@Filter` ở **WS-10/T10.2** (thiếu nó thì bộ lọc có tồn tại nhưng không áp — dữ liệu mọi đơn vị lộ hết mà không có lỗi nào) · ma trận role × resource ở **WS-10/T10.3** (tầng 3 hiện mới có test đơn vị, chưa có test đầu-cuối vì Phase 0 chưa có entity nào thuộc phạm vi đơn vị) · job dọn token chuyển sang hàng đợi DB + ShedLock ở **WS-6/T6.8**.

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
- [ ] **T10.2** ⚠ **ArchUnit** — chặn: module chỉ import `spi/` của module khác **hoặc `core.common.*`** (ngoại lệ Common Platform, xem `conventions.md` §1.1) · entity không ra khỏi application · `@Transactional` chỉ ở application · **cấm `float/double`** cho số đo/tiền · cấm `new Date()` · cấm `System.out` — *§1.1, rule 6 CLAUDE.md — **cài từ commit đầu***
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

- [x] **1. Chạy native** — `make dev-infra` → `./mvnw -pl app spring-boot:run` → `GET /actuator/health` = UP ✅ *14/8*
- [ ] **2. Chạy full Docker** — `make dev-docker` → admin-app + public-web + API cùng lúc *(backend đã xong; 2 app FE chờ WS-8/WS-9)*
- [ ] **3. Fail-fast thiếu env** — xóa 1 biến bắt buộc → app **không khởi động**, log chỉ rõ key thiếu
- [x] **4. Migration sạch từ DB rỗng** — `flyway_schema_history` đủ version, không lỗi ✅ *14/8: 9/9 migration trên volume rỗng, chạy lại lần 2 exit 0*
- [x] **5. Auth + 2FA** — login Super Admin → enroll TOTP (mã client tự tính khớp máy chủ) → nhận access + refresh cookie; `must_change_password` chặn mọi endpoint khác bằng `AUTH-0007` *(WS-5, 14/8)*
- [x] **6. Refresh reuse detection** — dùng lại refresh cũ → `AUTH-0008`, thu hồi cả family, `security_events` mức CRITICAL; refresh MỚI của người dùng thật **cũng chết**, access token hết hiệu lực **ngay** *(WS-5, 14/8)*
- [~] **7. RBAC 3 tầng** — tầng 2 xong và có test (đúng quyền → 200 · thiếu → `AUTH-3001` · quên khai báo → cấm). ⬜ **Tầng 3 (`AUTH-3002`) chưa kiểm chứng đầu-cuối được**: Phase 0 chưa có entity nào thuộc phạm vi đơn vị → dời sang **WS-10/T10.3** (ma trận role × resource) *(WS-5, 14/8)*
- [x] **8. Deny by default** — thêm 3 endpoint vi phạm → **CI đỏ**, chỉ đích danh cả 3 (thiếu annotation · mã quyền sai định dạng · module không tồn tại) *(WS-5, 14/8)*
- [x] **9. Envelope + traceId** — mọi response (kể cả lỗi) đúng §2.1, luôn có `traceId` ✅ *14/8: kiểm bằng request thật + 8 test lát cắt web*
- [x] **10. Audit hash chain** — verify chain pass; `songnhue_app` thử `UPDATE audit_logs` → **bị DB từ chối** ✅ *14/8: chặn cả qua bảng cha lẫn thẳng vào partition; thử sửa/xóa lén đều bị verify phát hiện*
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
| 2026-08-14 | **WS-4 xong**. 31 mã lỗi (thêm 6 mã `SYS` chung cho tầng framework) · envelope + traceId phủ 100% endpoint · rate limit 3 nhóm · 8 utils · `BaseEntity`/`ScopedEntity` · fail-fast cấu hình · OpenAPI 6 nhóm. Chốt **`core.common.*` là ngoại lệ import chéo** → T10.2 phải viết rule ArchUnit theo đó. Sửa Checkstyle bắt nhầm chữ trong bình luận. 53 test xanh; 2 lỗi thật do test phát hiện (bọc envelope 2 lần, MessageSource không được tạo do tên file có hậu tố `_vi`). |
| 2026-08-14 | **WS-3 xong 6/7**. Chốt: chọn service bằng **Compose profile** (4 chế độ chạy) · **build từ mã nguồn local trong container**, KHÔNG bind-mount hot-reload · **cổng Docker sang dải riêng + bind 127.0.0.1** (máy dev có PostgreSQL native gây nối nhầm DB) · **Mailpit** thay MailHog (arm64) · **collation ICU vi-VN** · bỏ profile Spring `docker`. Thêm `make doctor`, `docs/setup-guideline.md`, `docs/run-guideline.md`. T3.4 (2 image FE) chờ WS-8/WS-9. |
| 2026-08-14 | **WS-2 xong**. Chốt: hash chain audit tính bằng **trigger trong DB** (không ở Java) · `audit_logs` có partition **`DEFAULT`** + 12 tháng runway · Super Admin seed **không mật khẩu**, kích hoạt bằng lệnh bootstrap (thêm việc cho T5.7) · `security_events` cũng append-only · **không dùng `R__`** cho danh mục quyền/settings. Sửa `make migrate-info` (WS-1 trỏ plugin chưa cấu hình), thêm `make migrate-native` + `make db-verify-audit`. Đồng bộ `architecture-review.md` §9.3, `conventions.md` §1.2/§1.7/§4.3. |
| 2026-08-13 | **WS-1 xong**. Chốt Spring Boot **3.5.3**, formatter **Palantir Java Format** (4 space/120 cột), checkstyle config đặt ở `backend/config/checkstyle/`. Wrapper Maven 3.9.9 sinh qua Docker. |
| 2026-08-13 | Lập kế hoạch Phase 0. Chốt Maven multi-module · monorepo · deploy compose 3 VM · secrets env+GitHub Secrets · migration service riêng · DB roles tách quyền. **Backup hạ xuống bản tối giản** (RPO 24h, RTO 4h, không PITR/replica) — đồng bộ ngược vào `function-spec.md`, `architecture-review.md` §6.5/§9, `conventions.md` §1.2/§1.7. |
