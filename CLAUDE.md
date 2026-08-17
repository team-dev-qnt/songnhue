# CLAUDE.md — Bối cảnh dự án songnhue

## Dự án là gì

Hệ thống quản lý điều hành công trình thủy lợi + Cổng thông tin điện tử cho **Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ**.

**Ưu tiên xuyên suốt** (theo thứ tự): độ chính xác → nghiệp vụ chuẩn → tối ưu → vận hành/bảo trì → khả năng scale.

## Cấu trúc tài liệu (đọc theo thứ tự này)

| File | Vai trò |
|---|---|
| `function-spec.md` | **Nguồn sự thật về nghiệp vụ** — đặc tả 5 module, trường dữ liệu, workflow, validation, RBAC, NFR |
| `implement.md` | Kế hoạch implement — gom 4 nhóm (A Core / B Content / C Operations / D HR), thứ tự phase, cấu trúc code, checklist quyết định |
| `architecture-review.md` | Quyết định kiến trúc/tech ĐÃ CHỐT + lý do — khi mâu thuẫn với 2 file trên, file này thắng |
| `conventions.md` | Convention coding/design/security + đặc tả Common Platform (envelope, exception, error code, middleware, utils, RBAC 3 tầng, chống giả mạo) — chuẩn bắt buộc khi viết code |
| `business-open-questions.md` | Phần I-A: BOQ đợt 1 **đã đóng**. Phần I-B: **9 mục G đợt 2 đã đóng**. Phần II: **6 mục còn mở** cần khách cung cấp. Phần III: **truy vết chức năng nào còn chứa điểm chưa chốt** — đọc trước khi code 1 chức năng |
| `phase0-tracking.md` | **Bảng theo dõi tiến độ Phase 0** — 11 hạng mục WS-1→WS-11, **107 task** dạng checkbox, mỗi WS tự chứa điều kiện tiên quyết/đầu ra/cách kiểm chứng. Tick khi làm xong; cuối file là **21 mục Definition of Done** |
| `report-templates-proposal.md` | Đề xuất format mẫu báo cáo gửi Công ty duyệt (khung 5 khối + danh mục BC/BCNS/BCQT + trường dữ liệu). Layout chi tiết làm sau, khi vào Phase module tương ứng |
| `docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md` | **Câu trả lời chính thức của khách — đợt 1 (12/8/2026)**. Confirm **đợt 2 (mục G)** nhận qua trao đổi trực tiếp cùng ngày, ghi ở `business-open-questions.md` Phần I-B. Cả hai là nguồn của mọi thay đổi scope trong function-spec v2.2 |
| `docs_origin/SRS_QuanTriDieuHanh_TLSN ver 06.8.2026.docx.md` | **SRS v1.0 (23/07/2026)** — đặc tả yêu cầu chính thức của khách (dự thảo lấy ý kiến). function-spec.md v2.0 đã đồng bộ cấu trúc module + traceability theo file này |
| `docs_origin/Tổng quan HT PM...docx.md`, `docs_origin/Đặc tả hệ thống...docx.md` | Tài liệu gốc từ khách hàng — chỉ tham khảo, đã được tổng hợp vào function-spec.md |

## Module

> ⚠ **Cấu trúc module đã tái tổ chức theo SRS v1.0 (2026-08-06)** — xem `function-spec.md` v2.0 §0.4 + bảng traceability §10.

- **MOD-01** Cổng TTĐT/CMS (bài viết workflow duyệt, danh mục, media, banner, liên hệ, khảo sát/góp ý, tìm kiếm, widget thủy văn, **+ liên kết hệ thống văn bản điều hành CN-01.7** — *không đồng bộ dữ liệu, chỉ lưu mã số + auto-login*)
- **MOD-02** Vận hành công trình + GIS (danh mục công trình, **lịch sử sửa chữa/bảo trì/khắc phục sự cố = chức năng ghi nhận chính**, **tình hình vận hành cống CN-02.11 nhập tay + danh mục mã CRUD**, tài liệu, bản đồ GIS nhiều lớp, dashboard điều hành + wall 4K, thống kê, nhật ký thay đổi hồ sơ). ❌ **Đã loại khỏi scope**: nhật ký vận hành · **phiếu sự cố riêng** (gộp vào `maintenance_logs` — chốt G1)
- **MOD-03** Quản lý dữ liệu thủy văn (**tách riêng khỏi MOD-02 cũ**): danh mục điểm đo & loại chỉ số, polling API bên thứ 3 (`songnhue.bhh40.net`), chuẩn hóa/validate (**2 mức Hợp lệ/Nghi ngờ**), time-series, biểu đồ + biểu tổng hợp theo tuyến sông, báo cáo thủy văn, cảnh báo ngưỡng, hiển thị GIS — lõi kỹ thuật
- **MOD-04** HRM (sơ đồ tổ chức, hồ sơ CBNV, nghỉ phép — tuân thủ NĐ 13/2023, BLLĐ 2019) — *trước là MOD-03*
- **MOD-05** Quản trị (RBAC chi tiết, audit, backup/**restore UI**, health-check, thông báo hệ thống, quản lý phiên + đăng xuất từ xa, cảnh báo đăng nhập bất thường, xuất/nhập cấu hình)

## Tech stack (đã chốt — không tự ý đổi)

PostgreSQL 16 + PostGIS · Spring Boot 3 (Java 21) · Next.js (public, SSR/ISR) + React/Vite/AntD 5 (admin) · **Không Redis (v1)** — cache in-process (Caffeine) + bảng `hydro_latest`; denylist ở DB · **DB-backed job queue + ShedLock (giữ sẵn, bật khi ≥2 node)** · **Worker in-process (v1)** · MinIO · ECharts · Leaflet/MapLibre + OSM · Flyway · Auth: access token 30' + refresh rotation httpOnly cookie · **Modular Monolith 1 node (v1), stateless để thêm node = đổi cấu hình** · ArchUnit enforce boundary.

## Quy tắc bất di bất dịch khi code

1. Timestamp lưu `timestamptz` UTC; hiển thị UTC+7. Không lưu giờ địa phương.
2. NUMERIC/BigDecimal cho mọi số đo và tiền — cấm float/double.
3. Mọi giá trị tính toán (tổng hợp kỳ, chi phí bảo trì, số dư phép) tính ở BE; FE chỉ hiển thị.
4. Đổi trạng thái entity chỉ qua Workflow engine (Core) — không UPDATE status trực tiếp. **Trạng thái công trình là giá trị dẫn xuất** (sự cố đang mở → bảo trì → cảnh báo ngưỡng → ánh xạ mã tình hình vận hành → bình thường), không có cột cho người dùng sửa tay.
5. Data scoping theo Xí nghiệp/đơn vị ở tầng repository filter, không dựa vào dev nhớ thêm WHERE.
6. Module không import repository của module khác — chỉ gọi qua service interface.
7. `org_units` là 1 bảng dùng chung cho cả Xí nghiệp (MOD-02) và phòng ban (MOD-04 HRM).
8. Raw data thủy văn (`hydro_raw_logs`) append-only; báo cáo/dashboard đọc từ bảng agg, không scan raw.
9. Soft delete + audit log (old/new value) cho mọi entity nghiệp vụ.
10. Trường nhạy cảm HR (🔒 trong spec): bảng riêng `employee_sensitive`, AES-256-GCM, key ngoài DB.
11. Mọi connection/setup (DB, MinIO, SMTP, API ngoài...) đọc từ env — cấm hardcode; thiếu env bắt buộc → fail-fast lúc startup; client khởi tạo qua Spring bean, không tạo trực tiếp trong code nghiệp vụ.
12. **Tham số nghiệp vụ để trong bảng `settings` có UI sửa** (giờ hành chính 8–17h, retention 5 năm, thông số phép năm, chu kỳ polling, ngưỡng, giới hạn số lượng...) — không nằm trong `application.yml`, không hard-code.
13. **Credential bên thứ 3** (key API thủy văn, mã số hệ thống văn bản của từng user): AES-256-GCM, key ngoài DB, không log, không trả ra API, không nằm trong bản export cấu hình — xem `conventions.md` §4.7.
14. Dữ liệu thủy văn `quality = NGHI_NGO` **vẫn nằm trong bảng chính** → mọi truy vấn báo cáo/alert/tổng hợp **phải lọc `quality = HOP_LE`**. Đây là bẫy sai số liệu dễ mắc nhất.
15. Sự cố **không phải entity riêng** — là `maintenance_logs` với `loại = Khắc phục sự cố` (chốt G1). Không tạo bảng `incidents`, không mã `SC-`.
16. Danh mục do khách vận hành (mã tình hình vận hành, mức ngưỡng, nhóm người nhận cảnh báo) là **dữ liệu có CRUD**, không phải enum trong code — thêm mã mới không được đòi deploy.
17. Poller thủy văn: cron **2 phút/lần vào phút lẻ, giây 45**; **rate-limit trước khi mở HTTP** — bỏ lượt gọi khi *toàn bộ* trạm đã có bản ghi của khung 10' hiện tại (không phải "đã có bản ghi đầu tiên"). Nguồn trả rải rác trong cửa sổ `x1:30 → x8:30`.
18. Không có API lịch sử → **mất dữ liệu là vĩnh viễn**. Ghi nguyên văn response vào `hydro_raw_logs` trước khi parse; giám sát poller ưu tiên ngang backup DB.

## Trạng thái & mục chờ confirm

**📌 Phase "Tài liệu hệ thống" — HOÀN THÀNH ngày 2026-08-12.** BOQ đợt 1 (A–F) + **8/12 mục đợt 2 (G)** đã đóng và đồng bộ vào function-spec **v2.2** / implement / architecture-review §8 / conventions.

- ✅ Confirmed (2026-08-06): tái cấu trúc module theo SRS; Restore qua UI (M5.11) + bảo vệ nhiều lớp.
- ✅ Confirmed đợt 1 (2026-08-12): bỏ nhật ký vận hành → lịch sử sửa chữa · bỏ kế hoạch vụ mùa · bỏ diện tích tưới tiêu · bỏ trạng thái tổ máy realtime · lưu vực = trường text · thủy văn 2 mức chất lượng · bỏ SMS v1 (thông báo qua website + email) · TV 85" 4K · chỉ tiếng Việt · không migrate web cũ · quan hệ điểm đo↔công trình n–n có vai trò · mọi tham số để config.
- ✅ **Confirmed đợt 2 (2026-08-12)**: **G1** gộp sự cố vào lịch sử sửa chữa (PA A) · **G2** không cần giờ chạy máy/kWh/m³ → bỏ vĩnh viễn · **G3** chấp nhận không có API lịch sử; **cron 2'/phút lẻ + rate-limit theo khung 10'**; trạm trục trặc → **GIS xám** · **G4** tình hình vận hành cống nhập tay, **danh mục mã có CRUD + màu + ánh xạ trạng thái** (CN-02.11) · **G7** audit 5 năm rồi kết xuất lưu trữ · **G9** Admin tự cấu hình ngưỡng (màn hình cấu hình là hạng mục nghiệm thu) · **G11** người nhận = nhóm "Ban điều hành" ∪ người phụ trách công trình · **G12** chốt số NFR nghiệm thu (99% · 200 CCU · 3s · 60s · 2FA Admin).
- ✅ **API thủy văn đã đấu nối được (12/8/2026)**: `GET http://songnhue.bhh40.net/api/getmn.aspx?key=<mã số>;` — **dấu `;` cuối key bắt buộc**, thiếu thì trả `not.working`. Response text, phân tách `<br>`, bản ghi `F#####;dd/MM/yyyy;HH:mm;value=<cm>;` — 19 điểm mực nước, đơn vị **cm** (chia 100 ra m). Đặc tả parser: `function-spec.md` CN-03.2.
- ⛔ **Giới hạn nguồn**: **không có API lịch sử** (đã chấp nhận — poller là nơi bắt dữ liệu duy nhất, mất là mất vĩnh viễn, giám sát như backup) · **không có API lượng mưa** (v1 hiển thị `-`) · **không trả tên điểm đo, chỉ trả mã**.
- ✅ **G8b ĐÃ ĐÓNG (12/8/2026)** — Công ty cấp đủ **19/19 mã API ↔ tên điểm đo + vai trò**; bảng seed ở `function-spec.md` CN-03.1. **Không còn mục nào chặn.** 3 hệ quả: thêm vai trò **`MN_SONG`** (điểm loại này có thể không gắn công trình nào) · **cấm validate "TL > HL"** (2/5 cặp đảo hợp lệ) · seed/join **dùng mã, cấm dùng tên** (có 2 công trình cùng tên "Yên Nghĩa").
- ⬜ **Còn mở 6 mục, chỉ ảnh hưởng dữ liệu khởi tạo & nghiệm thu**: **G8** tuyến sông/lý trình/tọa độ + khoảng trống API-vs-biểu tổng hợp + 3 cặp mã trùng giá trị + danh mục công trình · **G3-a** lượng mưa · **G5** mã số hệ thống văn bản (+ xin SSO) · **G6** mẫu 2C-BNV · **G9-a** bộ mức ngưỡng · **G10** duyệt format báo cáo.
- ✅ **Đã verify sẵn sàng code (2026-08-13)**: Phase 0/1/2 **bắt đầu được ngay**; chỉ **CN-01.7 (lưu mã số) bị chặn bởi G5** → tách task riêng. Môi trường máy dev đủ (JDK 21 · Node 22 · Docker+Compose · psql 17); chưa cài Maven/Gradle nhưng dùng wrapper là được. Repo chưa có dòng code nào → greenfield. Chi tiết: `implement.md` §7.
- 📋 **Bảng truy vết "chức năng nào còn chứa điểm chưa chốt"**: `business-open-questions.md` **Phần III** — dev đọc trước khi bắt tay vào 1 chức năng.
- ✅ **Phase 0 đã có kế hoạch chi tiết (2026-08-13)** — `phase0-tracking.md`: 11 hạng mục, ~113 task, ~114 người-ngày. Quyết định nền tảng ghi ở `architecture-review.md` **§9**: Maven multi-module · monorepo · docker-compose **3 VM** · secrets env + GitHub Secrets · **migration chạy ở service `migrator` riêng** · **DB roles tách quyền**.
- ⚠ **Backup đã hạ xuống bản tối giản (13/8/2026)**: `pg_dump` hàng đêm, **RPO ≤ 24h · RTO ≤ 4h**, **không PITR/WAL/replica** — chấp nhận mất tối đa 1 ngày dữ liệu. Bảng 4 rủi ro chấp nhận ở `architecture-review.md` §6.5. Đây là quyết định nội bộ, **không hỏi khách**.
- ✅ **WS-1 xong (13/8)**: monorepo + Maven multi-module 6 module + Spotless/Checkstyle/ESLint/Prettier + hook commit-msg + Makefile.
- ✅ **WS-2 xong (14/8)**: 9 migration Flyway đa module · 25 bảng Core + 15 partition audit · 4 DB role tách quyền · 88 permission / 12 vai trò / 334 dòng phân quyền dịch từ ma trận §6 · 55 tham số `settings`. **Hash chain audit tính bằng trigger trong DB** (app không giả được chuỗi); `audit_logs` partition theo tháng. Tài khoản `superadmin` seed **không có mật khẩu** — kích hoạt bằng lệnh bootstrap ở T5.7.
- ✅ **WS-3 xong 6/7 (14/8)**: 4 chế độ chạy local chọn bằng Compose profile (`dev-infra` / `dev-be` / `dev-fe` / `dev-docker`) — image build từ mã nguồn local ngay trong container, build lại ~7 giây. Cổng Docker tách hẳn dải riêng + bind `127.0.0.1`. Collation DB chốt **ICU `vi-VN`**. Tài liệu: `docs/setup-guideline.md`, `docs/run-guideline.md`. Còn T3.4 (2 image FE) chờ WS-8/WS-9.
- ✅ **WS-4 xong (14/8)**: Common Platform ở `core.common.*` — envelope + `traceId` phủ 100% endpoint · **31 mã lỗi** · 8 subclass exception, lỗi lạ → `SYS-0001` không lộ chi tiết · rate limit 3 nhóm · 8 utils dùng chung · `BaseEntity`/`ScopedEntity` · fail-fast cấu hình · OpenAPI 6 nhóm. **`core.common.*` là ngoại lệ được import chéo** — ArchUnit (T10.2) phải theo đúng đó.
- ✅ **WS-5 xong (14/8)** — nơi dồn công của Phase 0: JWT **RS256** có `kid` · refresh rotation + **reuse detection** (thu hồi cả family, access token chết ngay nhờ claim `fid`) · CSRF double-submit · lockout không tiết lộ user tồn tại · **2FA TOTP** tự cài theo RFC 6238 (khớp 6/6 vector chuẩn) · **RBAC 3 tầng** với 3 annotation bắt buộc khai báo tường minh · quản lý phiên + đăng xuất từ xa · lệnh bootstrap `superadmin`. **36 mã lỗi**, 129 test xanh. **Không dùng filter chain Spring Security** — lý do ghi ở `architecture-review.md` **§9.5**.
  - ⚠ **Rate limit đăng nhập nâng 5→30 lượt/15' theo IP**: để bằng 5 thì khoá tài khoản (`AUTH-0003`) không bao giờ kích hoạt được và tham số M5.15 vô nghĩa; thêm nữa cả Công ty ra Internet qua một IP NAT nên hạn mức quá chặt là cả cơ quan không đăng nhập được. Có test chặn ở CI.
  - ⚠ **`@EntityScan` + `@EnableJpaRepositories` phải khai tường minh** ở `SongnhueApplication` — `scanBasePackages` không áp cho JPA.
- ✅ **Rà soát nợ tồn WS-1→WS-5 (14/8)** trước khi mở WS-6. Lập **"Sổ nợ liên WS"** ở `phase0-tracking.md` (18 dòng) — trước đó nợ chỉ ghi ở WS *giao*, **WS nhận không có task nào đứng tên** (8 mục). Kèm **luật 3 bước khi đóng WS**: tick task → tick dòng nợ → **quay lại sửa mô tả đã lỗi thời ở WS đã giao nợ** (bước 3 hay bị bỏ nhất).
  - ⚠ **Lỗi thật phát hiện khi rà soát: T4.8 đã tick nhưng fail-fast KHÔNG hoạt động.** `@Validated` + `@NotBlank` không bắt được biến môi trường bị thiếu — `@ConfigurationProperties` bỏ qua placeholder không giải được nên trường nhận nguyên văn `"${MINIO_ENDPOINT}"`, không rỗng, validate đi qua. Chạy thật: bỏ hẳn `MINIO_ENDPOINT` → app `Started`, health `UP`. Đã thêm **`UnresolvedPlaceholderGuard`** quét mọi bean `@ConfigurationProperties` → **đóng DoD mục 3**. **138 test xanh.**
  - ⚠ **`conventions.md` §4.5 từng tự mâu thuẫn với §4.1** (5/15' vs 30/15' cho rate limit login) — đã thống nhất về **30/15'**.
- ✅ **WS-6 xong (15/8)** — khối lớn nhất Phase 0: 6 pattern P1–P6 thành shared service (cây tổ chức · tệp đính kèm MinIO · workflow engine · thông báo in-app+email · hàng đợi job SKIP LOCKED · settings có UI) + nhật ký kiểm toán tự động + kết xuất audit 5 năm + lát cắt dọc CRUD tài khoản/vai trò. **43 mã lỗi**, **184 test xanh**. Trả **6 dòng nợ** (#9–#14).
  - ⚠ **ShedLock KHÔNG bọc quanh worker hàng đợi** — hai bài toán ngược nhau (`architecture-review.md` **§9.6**). Job theo lịch chỉ *đặt việc* vào hàng đợi, khoá chống trùng theo ngày làm DB thành điểm đồng bộ, nên cũng không cần ShedLock cho nhóm đó.
  - ⚠ **Ba bẫy auto-config của Spring Boot đã sập, cả ba đều im lặng** (`architecture-review.md` **§9.7**): khai bean `DataSource` hoặc `JdbcTemplate` làm Boot ngừng tạo bản chính (cả app chạy bằng vai trò `songnhue_archiver`); `@ConditionalOnBean` trên `@Component` không đáng tin.
  - ⚠ **Đăng ký Hibernate listener sau khi app đã lên KHÔNG có tác dụng** — Boot 6 chốt nhóm listener lúc dựng SessionFactory. Phải cắm bằng `Integrator`. Triệu chứng: `audit_logs` trống rỗng mà không lỗi nào.
  - ⚠ **`@Modifying` hàng loạt không đi qua bộ ghi nhật ký** — thao tác cần dấu vết phải đi qua entity hoặc gọi `AuditService.record(...)`.
- ✅ **WS-10 xong (15/8)** — làm trước WS-7 để rà soát chất lượng những gì đã dựng. 14 luật ArchUnit · ma trận RBAC đối chiếu 334 dòng phân quyền trong DB · chuỗi hash audit trên DB thật · cổng bao phủ JaCoCo tầng domain · `ci.yml` 3 job. **226 test xanh** (181 core + 45 app). Trả 4 dòng nợ (#5–#8).
  - ⚠⚠ **Phát hiện nặng nhất Phase 0: tầng 3 phân quyền chưa từng hoạt động.** `ScopeFilterAspect` đặt `@Order(LOWEST_PRECEDENCE - 1)` với ý định "nằm trong bộ chặn transaction", nhưng trong Spring AOP **số nhỏ hơn là vòng NGOÀI** — `enableFilter` rơi vào một `Session` tạm bị vứt đi, truy vấn thật chạy không có lọc. **Mọi Xí nghiệp đọc được dữ liệu của nhau, không một dòng lỗi nào.** Sửa bằng cách kéo bộ chặn transaction lên `CorePlatformConfig.TRANSACTION_ADVISOR_ORDER` (`architecture-review.md` §9.8.1).
  - ⚠ **Bốn cơ chế canh gác "xanh mà không chạy"**: bộ máy JUnit của ArchUnit tìm ra 0 bài kiểm (đặt luật chắc chắn sai vẫn xanh) · luật JaCoCo bị bỏ qua vì `<includes>` đặt trong `<rule>` so với *tên phần tử* · 2 luật chạy qua 0 lớp. **Chốt cách làm: mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi phạm** (`conventions.md` §1.5).
  - Thêm **`ScopeGuard`**: bản ghi ngoài phạm vi trả `AUTH-3002` + ghi `security_events` thay vì 404 im lặng — trước đó `AUTH-3002` là mã lỗi chết, có trong tiêu chí nghiệm thu mà không dòng mã nào ném ra.
  - Bộ luật T10.2 bắt được **3 vi phạm phân tầng có thật** ngay lần chạy đầu (controller gọi thẳng repository · record của `infra` lọt ra `api` · `WorkflowAware` sai tầng).
- ✅ **CI/CD 3 chặng + bảo vệ nhánh đã áp dụng (15/8)**: `dev` → `staging` → `production`, `master` ngoài luồng. **Kiểm một lần ở `dev`, đóng gói một lần** — image gắn tag theo commit SHA, hai chặng sau đề bạt chính image đó, không build lại. 4 workflow (`ci` · `promotion-guard` · `deploy-staging` · `deploy-prod`), tài liệu `docs/cicd.md` + `docs/branch-protection.md`. CI lọc theo **vùng đường dẫn** (BE/FE/tài liệu) nhưng **cố ý không lọc theo module backend** — 4 lỗi nặng nhất WS-10 đều sửa ở `core` mà bị bắt bởi bài kiểm ở `app`.
  - ⚠ **Kiểm chứng ngược bằng API tìm ra 3 lỗi trong chính tài liệu** (nợ #27, lệnh sửa ở `docs/branch-protection.md` §6.2): **`strict: true` ở staging/production tự khoá chặng đề bạt sau lần merge đầu** (dev không thể "Update branch" vì cả merge lẫn rebase đều vi phạm bảo vệ của chính nó) · **job lọc `Vùng nào thay đổi` không nằm trong `contexts`** nên nó hỏng thì 2 job nặng bị skip mà **skip được tính là đạt** · **đội 1 người mà đòi 1 lượt duyệt là cấm merge** (GitHub cấm tự duyệt PR) nên mọi lần merge phải bấm bypass, mà bypass bỏ qua luôn status check.
  - ⚠ **Hai quy ước merge ngược nhau**: feature → `dev` phải **Squash/Rebase** (linear history); `dev` → `staging` → `production` phải **merge commit** (deploy tìm image qua `HEAD^2`).
  - ⚠ **`dev` đang trống** — 18 commit/313 tệp nằm ở nhánh `common`, repo **chưa chạy lượt CI nào**. Gỡ bằng PR `common → dev` (nợ #28).
- ✅ **WS-7 xong 11/12 (16/8)** — sao lưu, khôi phục và giám sát. `pg_dump -Fc` hằng đêm 02:00 + theo yêu cầu (M5.10), sổ đăng ký `system_backups` **ghi cả lượt hỏng** · khôi phục qua UI (M5.11) **6 lớp chặn** · maintenance mode chặn ghi 503 · 4 health indicator + `GET /api/v1/system/health` (M5.12) · 3 gauge + counter sự kiện bảo mật · Prometheus/Grafana trên VM-3 + **14 luật cảnh báo** · 4 script vận hành · **7 runbook** ở `docs/runbook/`. **49 mã lỗi**, **255 test xanh**. Trả nợ #18, #21. Còn T7.7 (diễn tập khôi phục đo RTO thật) chờ VM-2.
  - ⚠ **`@Transactional` trên phương thức TỰ GỌI trong cùng lớp không có tác dụng** — self-invocation không đi qua proxy Spring. Bản đầu `BackupService` dính đúng bẫy này: dòng `RUNNING` không được commit trước khi `pg_dump` chạy, tức là mất đúng thứ cơ chế đó sinh ra để giữ (dấu vết khi tiến trình chết giữa chừng). Dùng `TransactionTemplate` như `JobService`/`SecurityEventService`.
  - ⚠ **`CHAR(n)` trong migration vs `String` trong entity** → `ddl-auto: validate` chặn **toàn bộ** context test tích hợp: 18 bài đỏ vì một cột. Luôn dùng `VARCHAR`.
  - ⚠ **Đọc luồng đầu ra tiến trình con tới EOF trước `waitFor(timeout)` làm hạn chờ vô hiệu** — tiến trình treo mà không ghi gì thì kẹt vĩnh viễn ở `readLine()`. Phải đọc ở luồng riêng.
  - **Kho sao lưu KÉO về VM-3, không đẩy đi từ VM-1** (`architecture-review.md` §9.9.1): VM-1 không giữ khoá ghi vào kho, nên chiếm được VM-1 vẫn không xoá được bản sao lưu. `prod.env.example` đã **bỏ** `BACKUP_TARGET_HOST` của bản WS-3.
  - **`pg_dump` chạy bằng `songnhue_readonly`; khôi phục là tính năng BẬT RIÊNG** — `DB_RESTORE_PASSWORD` để trống là lựa chọn hợp lệ, UI báo `ADM-2010` và khôi phục đi bằng runbook.
- ✅ **WS-8 xong (17/8)** — admin-app: Vite 8 + React 18 + TS strict + AntD 5 + TanStack Query + React Router 7. `apiClient` là HTTP client **duy nhất** (ESLint chặn `axios`/`fetch` chỗ khác) · **8 màn hình quản trị + 2 màn hình cá nhân** phủ MOD-05 · 7 component nghiệp vụ dùng chung · `error-map.ts` mirror **49 mã**. **24 test xanh**, image Docker build + chạy thật. Trả nợ #4, #32, #34 và **nửa** #15. Quyết định ghi ở `architecture-review.md` **§9.10**.
  - ⚠ **Access token chỉ nằm trong bộ nhớ**, refresh token trong cookie `httpOnly` — một lỗ XSS chỉ lấy được vé sống 30 phút. Cái giá phải nhận: F5 mất token, nên `bootstrapSession()` gọi `/auth/refresh` một lần lúc khởi động. Trạng thái xác thực có **ba** giá trị (`loading`/`anonymous`/`authenticated`), thiếu `loading` là route guard chuyển hướng ngay lúc chưa biết là ai.
  - ⚠ **Làm mới token phải đúng một lượt** — refresh **xoay vòng** token, gọi song song là tự kích hoạt cơ chế phát hiện dùng lại của backend, thu hồi cả family.
  - ⚠ **`tsc -b --noEmit false` đẻ 49 tệp `.js` ngay trong `src/`** — `--noEmit false` ghi đè tsconfig, không có `outDir` nên ghi cạnh mã nguồn. Lint, typecheck, build đều xanh; chỉ `prettier --check` bắt được một tệp lọt ra ngoài `src/`.
  - ⚠ **ESLint 9 flat config KHÔNG gộp cấu hình lồng nhau** — `eslint.config.mjs` đặt trong từng app bị bỏ qua **im lặng**, nhóm rule React không chạy mà lint vẫn xanh. Một file duy nhất ở `frontend/`, khoanh vùng theo `files`.
  - ⚠ **`npm ci` phải chạy ở `frontend/`**, không phải trong từng app: npm workspaces chỉ có **một** lockfile. `ci.yml` viết từ WS-1 sai đúng chỗ này ở cả hai job FE — đã sửa, nhưng chưa chạy thật vì repo còn 0 lượt CI.
- ➡️ **Bước tiếp theo**: gửi 6 mục còn mở + `report-templates-proposal.md` cho Công ty; làm **WS-9 (FE public-web — 5 pd)** rồi **WS-11 (Deploy — 10 pd)**. WS-9 gọn và mở khoá nốt T3.4 + DoD mục 2. WS-11 đang giữ nhiều nợ nhất: #26 (VM + compose staging/prod), #29 (VM-3 + Prometheus/Grafana), #30 (Alertmanager), #31 (nginx chặn actuator/swagger).

## Quy ước làm việc với user

- User: QuanTran (quantran@goapps.team). Trả lời tiếng Việt, ngắn gọn, đi thẳng vào vấn đề.
- Khi cập nhật quyết định kiến trúc: sửa `architecture-review.md` trước, rồi đồng bộ sang `function-spec.md` và `implement.md`.
