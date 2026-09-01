# CLAUDE.md — Bối cảnh dự án songnhue

## Dự án là gì

Hệ thống quản lý điều hành công trình thủy lợi + Cổng thông tin điện tử cho **Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ**.

**Ưu tiên xuyên suốt** (theo thứ tự): độ chính xác → nghiệp vụ chuẩn → tối ưu → vận hành/bảo trì → khả năng scale.

## Cấu trúc tài liệu

Khi mâu thuẫn: `architecture-review.md` > `function-spec.md` / `implement.md`.

| File | Vai trò |
|---|---|
| `.claude/function-spec.md` | **Nguồn sự thật nghiệp vụ** — 5 module, trường dữ liệu, workflow, validation, RBAC, NFR |
| `.claude/architecture-review.md` | Quyết định kiến trúc ĐÃ CHỐT + **nguyên nhân gốc từng sự cố** (§9 Phase 0 · §10 Phase 1). Kho lưu, không phải guideline |
| `.claude/conventions.md` | **Luật** khi viết code + đặc tả Common Platform (envelope, exception, mã lỗi, RBAC 3 tầng, chống giả mạo) |
| `.claude/master-tracking.md` | **Nguồn DUY NHẤT** của task và nợ (§6). Đồng bộ lên Google Sheet qua MCP `google_sheets_sync` |
| `.claude/implement.md` | Kế hoạch implement — 4 nhóm A/B/C/D, thứ tự phase, cấu trúc code |
| `.claude/business-open-questions.md` | BOQ đợt 1+2 đã đóng · **7 mục còn mở** (G14 đóng 27/8 bằng văn bản nghiệm thu) · truy vết chức năng nào còn điểm chưa chốt |
| `.claude/phase0-tracking.md` · `phase1-tracking.md` | **Lưu trữ, cấm sửa.** Phase 1 có mục "18 điểm nghiệp vụ đã làm rõ trước khi code" |
| `.claude/report-templates-proposal.md` | Đề xuất format báo cáo gửi Công ty duyệt |
| `docs/coding-guide.md` | **Đường đi** — công thức viết một chức năng (migration → entity → workflow → service → controller → quyền → mã lỗi → test) + bẫy đã trả giá |
| `docs/ui-styles.md` | Quy chuẩn UI — màu qua `design-tokens`, Noto Sans, spacing, animation, a11y. Đọc trước khi sửa styling |
| `hosting_recommendations.md` | **Mua gì và vì sao** — 2 VPS không PaaS (5 bảo đảm phải tháo), pháp lý DLCN, ngân sách bộ nhớ §8, tên miền §9, cắt chi phí §10 |
| `docs/cicd.md` | Luồng 3 chặng `dev → staging → production`, cổng đề bạt, secret cần đặt |
| `docs/deploy-guideline.md` | Dựng máy, khoá, `.env`, DNS/TLS, lượt deploy tay đầu tiên, checklist nghiệm thu |
| `docs/branch-protection.md` | Ba hồ sơ bảo vệ nhánh + lệnh áp dụng |
| `docs/nghiem-thu-cong-ttdt-v1.md` | **Sổ nghiệm thu v1 chốt 28/8** — đối chiếu 43 mã CR ↔ trạng thái, checklist §10, trả lời OI-01→OI-12, danh sách ô sẽ rỗng và vì sao. **Bản đối chiếu, không phải nguồn sự thật** — task và nợ vẫn ở `master-tracking.md` |
| `docs/setup-guideline.md` · `run-guideline.md` | Dựng máy dev; bốn chế độ chạy |
| `docs_origin/` | Tài liệu gốc của khách — chỉ tham khảo, đã tổng hợp vào `function-spec.md` |

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

## Trạng thái

**Phase "Tài liệu hệ thống"** ✅ xong 12/8/2026 — BOQ đợt 1 (A–F) + đợt 2 (G) đã đóng và đồng bộ vào `function-spec.md` **v2.2**.
**Phase 0 — Core Platform** ✅ 10/11 hạng mục. **WS-11 (Deploy)**: staging đã chạy thật, đường ống CD đóng (§10.50→§10.55); còn production + quay lui thật.
**Phase 1 — CMS & master data công trình** ✅ **xong 24/8/2026** — WS-12→WS-23 đóng đủ, **16/17 mục DoD** có phép kiểm đứng sau.
**WS-24 — Đợt chỉnh sửa cổng theo nghiệm thu Công ty** (`docs_origin/nghiem_thu_phase1.md`, 27/8): **34/43 mã CR đóng**, 9 mã còn lại chờ đăng nhập trên cổng · nguồn dữ liệu · nhập liệu. Đã **chạy thật trên stack đầy đủ**: 17/17 đường dẫn menu trả 200. Chi tiết `master-tracking.md` WS-24 · nguyên nhân gốc §10.61.

✅ **Staging nghiệm thu 28/8 sau lượt CD `22876c8`** — đo ĐỘC LẬP qua SSH, không đọc lại lời workflow: ba container chạy đúng ID ảnh đã triển khai, `healthy`, tạo trong cửa sổ deploy · Flyway `1039 → 1040` đúng thứ tự, 0 migration thất bại · `/photos` trả **25 ảnh, 25/25 ra byte JPEG thật** (tb 124 KB) · `/banners` **5/5** · video + logo đúng. ⚠ Smoke test của CD **không** kiểm 25 ảnh mới — nó hỏi ảnh seed từ 25/8.

✅ **T11.45 đóng 28/8** — QuanTran chạy lệnh `sudo`; đo lại: `fail2ban` **active**, drop-in `60-startups.conf` có mặt **và đã được nạp** (`StateChangeTimestamp` 00:36:12 trong khi `NRestarts=0` — chữ ký của `reload`), **SSH 10/10** (trước: 7/10), cổng 22 còn 2 kết nối / 5 tiến trình sshd (lúc sự cố: 67). ⬜ Còn `sudo fail2ban-client status sshd` để xem số IP đã cấm.

**WS-25 — Đầu trang thân thiện + kiểm kê "cấu hình được từ admin"** ✅ **28/8** (§10.62). Thanh điều hướng **đo được là tràn 1454/1192px trên mọi màn hình** (`flex-wrap` che đi) và mục cấp 1 kiểu `NONE` là nút không hành vi → không mở được menu con trên máy tính bảng — cả hai nằm trong §10 checklist *"Responsive"*. Kiểm kê tìm ra **6 cột/khoá/tham số thiếu một nửa cặp đọc–ghi** (4 trong số đó do WS-24 tạo ra **một ngày trước**) + 4 khoá `settings` không ai đọc. **21/24 task đóng**; 3 nợ có số đo: T25.22 (cache cổng không xoá được từ `core`/`operations` — trễ 5') · T25.23 (25 hex ở admin-app) · ~~T25.24~~ đã đóng 27/8.

⬜ **DoD còn treo**: **DOD1.17** trang chủ < 3s (NFR-02) — nay đo được trên staging có nội dung thật · **DOD0.21** quay lui — chưa lượt deploy nào đi qua đường quay lui thành công.

✅ **Staging đã dựng lại cluster 26/8** (T11.3-b) — `i | collate=C.UTF-8 | icu=vi-VN`, vân tay số dòng khớp từng bảng, 6/6 container healthy, 4/4 smoke test xanh trên site thật, trang chủ 11 liên kết đều là slug thật. Lượt khôi phục ấy tìm ra **T7.13-a** — đường quay lui dữ liệu duy nhất của hệ vốn khôi phục ra một CSDL ứng dụng không đọc nổi (§10.58).

⚠⚠ **Hai mươi lăm lượt liên tiếp một bản ghi "đã xong" bị lượt rà sau bác bỏ.** Đây là hình dạng rủi ro
đặc trưng của dự án, không phải sự cố lẻ — nguyên nhân gốc từng vụ ở `architecture-review.md`:

| Ngày | Lượt rà tìm ra | § |
|---|---|---|
| 22/8 | bản ghi "đã xong" bị bác bỏ toàn phần | — |
| 23/8 | 4/11 mục WS-21 chưa làm hoặc hỏng; 4/17 cam kết DoD không có phép kiểm nào | §10.36 |
| 24/8 | 1 lỗi CHẶN nghiệp vụ trong phạm vi đã tick; image quản trị phát mã nguồn; bộ lọc CI bỏ qua đúng job nó cần | §10.37 |
| 24/8 | CI đỏ ở job đóng gói image **dù 8 cổng kiểm ở máy đều xanh** — biến build rỗng, `??` không đỡ | §10.38 |
| 25/8 | 204 No Content bị giao diện biến thành lỗi trên 24 endpoint; `DB_APP_PASSWORD` không ai đọc che mất biến thật sự thiếu | §10.40 · §10.41 |
| 25/8 | ảnh cổng chưa từng ra được một byte — bài kiểm dùng UUID không tồn tại nên chỉ đi nhánh 404 | §10.52 |
| 25/8 | bản vá không bao giờ được nạp — compose in `Running` và giữ container cũ | §10.53 |
| 25/8 | trang chủ nướng rỗng vào image, và **19 bài viết + 4 văn bản có số hiệu + 5 trạm thuỷ văn + 9 số điện thoại bịa** làm trang rỗng trông đầy | §10.54 |
| 26/8 | tham số collation **vắng hẳn** ở `compose.prod.yml` 12 ngày — và vá tệp cũng không chữa được cluster đã dựng | §10.56 |
| 26/8 | cổng secret bỏ qua trong im lặng → CD Production xanh mà không byte nào chạm máy chủ | §10.57 |
| 26/8 | **đường quay lui dữ liệu duy nhất** khôi phục ra CSDL mà ứng dụng không đọc nổi | §10.58 |
| 27/8 | lượt deploy đỏ vì **cổng 22 bị quét** — sshd thả 30% kết nối; không phải lỗi mã | §10.59 |
| 27/8 | CD Staging **success trọn vẹn mà không container nào được thay** — một lệnh nuốt mất nửa cuối script | §10.60 |
| 27/8 | **cổng công khai chưa từng có CSP nào** — `next.config` bảo *nginx đặt*, nginx bảo *image FE đặt*; bộ canh chỉ soi `admin-app` | §10.61 |
| 27/8 | trang **Tiến độ sản xuất** liệt kê hai danh mục của mục đã ẩn làm các **Năm** — 906 bài kiểm hai phía đều xanh, chỉ lộ ra khi mở trang trên stack đang chạy | §10.61 |
| 28/8 | **6 cột/khoá/tham số thiếu nửa cặp đọc–ghi** — 4 do đợt hôm trước tạo ra; thanh điều hướng tràn khung 22% ở *mọi* bề rộng mà `flex-wrap` che đi | §10.62 |
| 28/8 | **hai lượt kiểm chứng ngược của chính tôi đều sai** — một cái mù trước SQL đã chú thích, một cái *chép lại* hành vi sai thay vì bắt nó | §10.62 |
| 27/8 | **bảy context bắt buộc khoá chết mọi PR chỉ sửa tài liệu** — job matrix bỏ qua báo một cái tên khác | §10.63 |
| 27/8 | **9 phép kiểm canh nguồn sự thật, không cổng nào chạy** — và có sẵn nhánh `sys.exit(0)` chờ | §10.64 |
| 27/8 | **một đợt sửa CHÚ THÍCH làm ứng dụng không khởi động được** — Flyway băm cả tệp; 680 bài kiểm về nguyên tắc không thấy | §10.65 |
| 27/8 | **migration mới đánh số bằng giờ-phút** rơi xuống dưới bản staging đã áp — và cùng lỗi ấy làm seed ghi vào một khoá chưa tồn tại, **0 hàng, không một dòng log** | §10.66 |
| 28/8 | **bản vá sống trên đĩa mà tiến trình MCP vẫn chạy mã cũ** — bảng Công ty đọc mang 3 trạng thái sai suốt từ lúc bản vá vào kho; đọc-ngược-sau-khi-ghi *không* bắt được | §10.67 |
| 29/8 | **cổng quét CVE đỏ hơn một ngày không ai đọc** — 9 mã ≥ 7 ở `tomcat-embed-core`, mã không đổi, *thế giới* đổi; và bước SSH của CD đỏ 6/6 mà `2>/dev/null` **vứt mất lý do** | §10.68 |
| 29/8 | **lượt deploy tự cấm chính nó** — `ssh-keyscan` mở 5 kết nối vô danh, fail2ban `maxretry=3` cấm ngay IP runner; lượt xanh hôm trước chỉ **thắng cuộc đua** | §10.68-C |
| 29/8 | **secret nối được ba phần tư đường** — khai ở thân + kiểm ở cổng, nhưng hai workflow GỌI không truyền; bộ canh soi *chuỗi có mặt* nên xanh trong khi đường dây đứt | §10.68-D |
| 30/8 | **trần tải tệp 1MB chưa ai khai** — mặc định của *framework* thắng, nên bốn hạn mức trong `settings` (có UI, có mã đọc, có bài kiểm) **chưa từng quyết định điều gì**; và phản hồi 413 vừa thêm vốn không giao được tới trình duyệt | §10.69 |
| 1/9 | **5 cổng kiểm CI sẽ đỏ mà 8 cổng "đã canh" hôm trước không cái nào chạm tới** — *biên dịch được* đọc như *qua cổng kiểm*; và luật ArchUnit vs thiết kế `Station` mâu thuẫn suốt vì **luật chưa từng chạy** | §10.70 |
| 1/9 | **ba khuyết tật im lặng lộ ra ngay lượt kiểm HTTP ĐẦU TIÊN của `hydro`** — đổi Nguồn dữ liệu bị vứt · TECHNICIAN không tạo nổi điểm đo · cờ "Đang dùng" bị bỏ rơi; cả ba đều *lưu thành công* | §10.70 |
| 1/9 | **T27.7 trả nợ cache cổng ở ba điểm ghi, điểm ghi thứ tư ra đời cùng đợt mang lại đúng lỗi cũ** — tình hình vận hành lên cổng từ T27.16 mà không xoá đệm | §10.70 |

⛔ Hệ quả rút ra: **"đã tick" không phải bằng chứng.** Trước khi mở một giai đoạn mới, đối chiếu với mã thật và chạy đường mà người dùng thật đi.

⛔ Và **"xanh ở máy" cũng không phải bằng chứng**: hai job chỉ sống trên runner (quét CVE · đóng gói image) chạy trên **cây checkout sạch, không có `.env.local`**. Mọi lượt build ở máy đều nạp tệp ấy — nên một biến môi trường rỗng là trạng thái mà `make ci-local` **về nguyên tắc không dựng lại được**. Muốn kiểm trước thì phải `docker build` đúng đối số của `ci.yml`.

⛔ **Cấm seed dữ liệu công trình/thuỷ văn "cho đẹp demo"** — ô nào chưa có nguồn thì nói thẳng là chưa có.

**Codebase đo ngày 1/9 (sau §10.69)**: **809 test BE** (249 core + 37 content + 41 operations + 14 hydro + 468 app) + **428 test FE** (187 admin + 241 public) · **79 mã lỗi** (BE = FE = properties — +HYD-1002/2005/2006 của WS-28; bài canh đếm còn kẹt ở 76 nên CI sẽ đỏ, đã vá) · 88 quyền / 12 vai trò / 334 dòng phân quyền · **36 bài ArchUnit** (7 lớp, gồm **14 bài tự-kiểm** — +3 bài chứng minh ngoại lệ "cột phạm vi NULLable" của `Station` không bị lạm dụng) · **11 phép kiểm bộ đọc tracking** · **bộ canh thứ tự migration** (script so nhánh nền) **+ `MigrationNamingTest` 5 bài** canh dãy `nnnn` tăng dần — bắt được ngay 2 tệp lịch sử `V202608241255/1256` đánh số bằng giờ-phút · 45 migration, đỉnh `V202608311049` · mọi cổng bao phủ **chạy thật** · CVE ≥ 7: **0** (đo 28/8, `tomcat.version` 10.1.59 — số đo có hạn dùng, **chưa đo lại 1/9**) · **0 mã màu ghi cứng** trong `public-web` (admin-app còn 25 — nợ T25.23). ⛔ Mọi con số trên là **số đo có hạn dùng**: ghi vào sổ thì phải ghi kèm ngày đo.

⛔⛔ **Và "biên dịch được" KHÔNG phải "qua cổng kiểm"** (§10.70): bản ghi 31/8 khai *javac 21 trên 395 tệp, JAVAC_EXIT=0, `tsc` sạch, `eslint` sạch* — đúng cả bốn, và **không cái nào chạm tới** Spotless, Checkstyle, Prettier, ArchUnit hay một dòng SQL. Lượt chạy thật đầu tiên tìm ra 5 cổng sẽ đỏ. ⚠ Thêm một bẫy đo được 1/9: **`./mvnw -pl app test` KHÔNG có `-am` chạy trên jar module khác CŨ trong repo local** — một lượt kiểm chứng ngược "phá rồi thử" báo XANH vì bản hỏng chưa từng được nạp (luật 10).

### Tra ở đâu

| Cần gì | Đọc ở đâu |
|---|---|
| Nợ đang treo, task còn lại | **`.claude/master-tracking.md`** — nguồn DUY NHẤT (conventions.md §6). `phase0-tracking.md` / `phase1-tracking.md` chỉ là lưu trữ, cấm sửa |
| **Lý do** một quyết định, **nguyên nhân gốc** một lỗi đã sửa | `architecture-review.md` **§9** (Phase 0, 14 mục) · **§10** (Phase 1, 49 mục — §10.35 đợt vá sau WS-22, §10.36 nghiệm thu lại WS-21 + DoD, §10.37 nghiệm thu image, §10.38 CI đỏ sau merge `dev`, §10.40 lỗi 204, §10.41 biến CSDL không ai đọc, §10.52 envelope bọc `byte[]`, §10.53 container không được thay, §10.54 trang chủ nướng rỗng + dữ liệu bịa che chỗ rỗng, §10.55 `minio-init` đo thay vì khai báo + bộ seed một công tắc, §10.56 collation vắng ở `compose.prod.yml` — tham số chỉ chạy một lần, §10.57 cổng secret bỏ qua trong im lặng + 4 khoản bấm ở GitHub, §10.58 bản dump khôi phục ra CSDL không đọc nổi, §10.59 cổng 22 bị quét làm đỏ deploy, §10.60 một lệnh nuốt stdin làm nửa cuối khối triển khai không chạy mà CD vẫn xanh, §10.61 đợt chỉnh sửa cổng theo nghiệm thu Công ty — cổng công khai chưa từng có CSP, đổi menu làm ba trang tĩnh mất lối vào, hai bộ canh cũ canh hình dạng thay vì canh bất biến, §10.62 sáu cột/khoá thiếu nửa cặp đọc–ghi + hai lượt kiểm chứng ngược tự sai, §10.63 bảy context bắt buộc khoá chết mọi PR chỉ sửa tài liệu, §10.64 chín phép kiểm canh nguồn sự thật mà không cổng nào chạy, §10.65 một đợt sửa chú thích làm ứng dụng không khởi động được, §10.66 migration đánh số bằng giờ-phút rơi xuống dưới bản đã áp, §10.67 bản vá sống trên đĩa mà tiến trình MCP vẫn chạy mã cũ, §10.68 cổng quét CVE đỏ mà không ai đọc + bước SSH vứt mất lý do đỏ, §10.68-C lượt deploy tự cấm chính nó bằng `ssh-keyscan`, §10.68-D secret nối ba phần tư đường, **§10.69 trần multipart 1MB không ai khai — một tham số cấu hình *nói dối* khó thấy hơn một tham số không ai đọc**) |
| Cách viết một chức năng + bảng bẫy tra nhanh | `docs/coding-guide.md` |
| Luật bắt buộc khi viết code | `conventions.md` |
| Nghiệp vụ | `function-spec.md`; điểm chưa chốt → `business-open-questions.md` Phần III |

### Nghiệp vụ còn chờ Công ty — 7 mục BOQ + 10 mục OI

Không mục nào **chặn code**, chỉ chặn **dữ liệu khởi tạo và nghiệm thu**; riêng **G5** chặn đích danh CN-01.7 (lưu mã số hệ thống văn bản) nên task đó tách riêng.

**G3-a** lượng mưa · **G5** mã số hệ thống văn bản (+ xin SSO) · **G6** mẫu 2C-BNV · **G8** tuyến sông/lý trình/toạ độ + danh mục công trình · **G9-a** bộ mức ngưỡng · **G10** duyệt format báo cáo · **G13** bộ nhận diện cổng (logo/màu/GA/GTM/reCAPTCHA).

✅ **G14 đóng 27/8** — cây danh mục + menu nhận qua §3 văn bản nghiệm thu, dựng ở `V202608271031`.

⬜ **Mở mới 27/8 — `OI-01`→`OI-10`** (§9 của `docs_origin/nghiem_thu_phase1.md`). Tài liệu đề nghị phía phát triển trả lời **ngay trong tuần** ba mục kỹ thuật `OI-01`/`OI-02`/`OI-07`; câu trả lời đo được đã có ở `master-tracking.md` T24.23→T24.25. Chặn nghiệm thu nặng nhất: **`OI-03`** (danh sách 10 cống trục chính) · **`OI-05`** (7 hay 8 Xí nghiệp — Bố cục ghi 7, danh mục công trình có 8).

Gửi kèm `report-templates-proposal.md`. Chi tiết từng mục: `business-open-questions.md` Phần II.

### Việc bấm ở GitHub — áp và đo lại 26/8

| | Trạng thái đo được |
|---|---|
| **Nợ #45** Dependency graph | ✅ đã bật từ trước, sổ ghi sai — job *Soi phụ thuộc* chạy `success` (không `skipped`), SBOM trả về (T11.32) |
| **Nợ #27** bảo vệ nhánh | ✅ `staging` + `production` `strict` → `false`; `dev` thêm *Vùng nào thay đổi* (T11.39) |
| **Nợ #46** context đóng gói image | ✅ `dev` **2 → 7** context. ⚠ `Gắn tag SHA cho image không đổi` cố ý ngoài danh sách — nó **có** báo cáo ở PR (`skipping`), mà `skipped` được tính ĐẠT, nên nó không chặn được gì (T22.23) |
| Bảo mật kho | ✅ secret scanning · push protection · non-provider patterns · Dependabot alerts + security updates — cả 5 `enabled`, `secret-scanning/alerts` trả **0** (T11.40) |
| Cổng secret của lượt triển khai | ✅ thiếu secret ở production nay **DỪNG ĐỎ**. Trước đó cảnh báo rồi bỏ qua → lượt CD Production xanh trọn vẹn mà không byte nào chạm máy chủ (T11.7-b, §10.57) |
| Environment `production` | ⬜ vẫn **không có secret nào** — chỉ đặt được sau khi có VPS-1 (T11.7) |
| Biến kho `PUBLIC_SITE_URL` | ⬜ `actions/variables` vẫn RỖNG → sitemap/canonical của staging trỏ `localhost` (T11.7-a) |

📌 Cùng một hình dạng: **một cổng kiểm tồn tại trong mã nhưng chưa có hiệu lực ở nơi nó phải chặn.**
Lệnh áp nợ #27 nằm sẵn trong `branch-protection.md` §6.2 **từ 15/8** — không ai chạy, và không ai
biết là chưa chạy. Bản ghi cũng mục theo thời gian: nợ #45 đã xong từ lúc nào không ai cập nhật.

## Luật đã trả giá — áp cho mọi phiên làm việc

Rút ra sau khi **cùng một hình dạng lỗi lặp lại nhiều lần**. Nguyên nhân gốc từng vụ ở `architecture-review.md` §9–§10; ở đây chỉ giữ phần dùng được cho việc kế tiếp.

**Về phép kiểm — nhóm đắt giá nhất, gần như mọi lỗi nặng của dự án đều đi qua đây**

1. **Mỗi cơ chế canh gác phải có bài kiểm chứng minh nó bắt được vi phạm** (`conventions.md` §1.5). Đã có 5 cơ chế *xanh mà không chạy*: bộ máy JUnit của ArchUnit tìm ra 0 bài kiểm · luật JaCoCo bị bỏ qua vì `<includes>` sai chỗ · `verify-no-keys.sh` chưa từng quét khoá PEM · `FrontendSameOriginTest` soi sai đối tượng · bài canh CSS khớp trúng chuỗi ở quy tắc khác.
2. **Canh cấu trúc, đừng canh văn bản** — `includes('.sn-align-center')` vẫn xanh sau khi thuộc tính đã bị xoá hẳn.
3. **Canh giá trị ĐÃ GIẢI, đừng canh giá trị MẶC ĐỊNH** — mặc định chỉ dùng đến khi không ai ghi đè, mà thường thì luôn có người ghi đè (`--env-file` thắng `${VAR:-}`). ⚠ Và **"rỗng" khác "chưa đặt"**: Docker `ARG` không truyền vẫn gán chuỗi rỗng, nên `??` giữ nguyên nó còn `||` mới đỡ. Đây là chỗ mọi mặc định của FE nằm — dùng `||` cho mọi hằng số đọc từ env (§10.38).
4. **Mock đặt đúng chỗ mã chạm ra ngoài là chưa kiểm gì cả** — `BackupServiceTest` mock `PostgresToolRunner`, và sao lưu (lưới an toàn *duy nhất* của hệ) chưa từng sinh ra một tệp nào suốt 4 ngày.
5. **Bài kiểm gọi thẳng service không đi cùng đường với production** — 391 bài xanh trong khi mọi màn hình quản trị nội dung trả 500. Cam kết nằm ở controller/filter thì phải kiểm **qua HTTP**.
6. **Endpoint mà trình duyệt phải gọi thì lượt kiểm phải mang `Origin`** — `curl` không có origin, không preflight, nên đi lọt qua đúng bức tường chặn người dùng thật (CORS chặn toàn bộ giao diện quản trị suốt WS-8→WS-20).
7. **Một cơ chế chưa ai đi qua thì chưa biết nó đúng hay sai** — phép kiểm chạy qua *tập rỗng* vẫn xanh trọn vẹn (ArchUnit suốt Phase 0, tầng 3 phân quyền, ISR revalidate).
8. **Healthcheck trỏ vào endpoint không đại diện chỉ chứng minh tiến trình còn sống** — sập 3 lần, nặng nhất là image backend chạy suốt 4 WS mà mọi `/api/v1/**` trả 404.
9. **Một khẳng định không phân biệt được hai trạng thái thì không khẳng định gì** — bài canh "đi bằng HTTP/1.1" khẳng định `exchange.getProtocol()`, và xanh cả khi đã gỡ `.version(HTTP_1_1)`: máy chủ JDK chỉ nói HTTP/1.1 nên client tự hạ cấp. Phải **đo** cái gì thật sự khác giữa hai cấu hình (ở đây là header `Upgrade`/`HTTP2-Settings`), đừng khẳng định cái nghe có vẻ đúng.
10. **Làm hỏng có chủ đích để kiểm chứng thì phải xác nhận bản hỏng ĐÃ được nạp — và bản KHÔI PHỤC cũng vậy** — lượt kiểm chứng bản vá IDOR ngày 23/8 báo 6/6 xanh sau khi đã gỡ lớp bảo vệ; hoá ra `install` bị Checkstyle chặn, output đã bị `>/dev/null` nuốt, và bài kiểm chạy trên jar cũ *còn nguyên bản vá*. Chính bước chứng minh cũng là một xanh giả. ⚠ Thêm 26/8, hai lượt nữa trong một phiên: `sed -i.bak` rồi `mv .bak` trả lại **mtime gốc**, nên Maven thấy nguồn cũ hơn `.class` và bỏ qua biên dịch — cả bộ test chạy trên lớp hỏng (169 lỗi, dòng thật nằm cách chỗ báo lỗi hàng nghìn dòng); và một khối kiểm chứng chạy sai thư mục nên `sed` không sửa gì mà bài kiểm **vẫn in 5/5 xanh**. Cách rẻ nhất: in một con số ĐO ĐƯỢC ở mỗi bước (`grep -c`, `stat`), và `touch` tệp sau khi khôi phục (§10.56).
11. **Phép kiểm chạy lâu phải ưu tiên báo cáo trọn vẹn hơn dừng sớm** — reactor dừng ở module đầu làm 4 vòng quét CVE chỉ soi được **một** module; nếu module đầu tình cờ sạch thì ta tưởng cả dự án sạch.

**Về chỗ đặt một bảo đảm**

12. **Khi một bảo đảm phải đúng ở nhiều đường vào, đặt nó ở chỗ *dữ liệu đi qua*, đừng đặt ở *nơi gọi*** — không đặt được thì phải có phép kiểm đếm đủ các đường vào. (XSS lưu trữ lọt qua 2/3 đường ghi `settings`; `SvgSanitizer` có 9 bài kiểm mà không nằm trên đường chạy nào.)
13. **Một cột dẫn xuất trộn hai nguồn khác chiều lọc thì kết quả phụ thuộc *ai bấm F5 sau cùng*** — `ConstructionStatusService.tinh()` đếm sự cố bằng câu native (không lọc) nhưng tra mã tình hình vận hành bằng câu derived (có lọc); người ngoài đơn vị mở màn hình là trạng thái bị hạ xuống "Bình thường" **cho tất cả mọi người**, vì đó là cột được ghi xuống CSDL.
14. **Chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ** — enum SPI ↔ enum domain · từ vựng trình soạn thảo ↔ danh sách cho phép của bộ lọc ↔ CSS cổng công khai · mã lỗi BE ↔ FE · URL tile ↔ CSP.
15. **Công tắc / cột / tham số chưa ai đọc là một lỗi, không phải việc để dành** — `limits.upload.max-mb.*`, `company.*`, `attachments.valid_from` đều bày ra ở giao diện hoặc lược đồ mà không dòng mã nào đọc. ⛔ Hệ quả: **không seed tham số `settings` cho tính năng chưa dựng**.
16. **Số 0 là một câu khẳng định** — ô số liệu chưa có nguồn phải trả rỗng kèm lý do, và ràng buộc đó ép ở **hàm dựng** chứ không ở lời dặn.
    ⛔ **Và cấm mọi bộ dữ liệu dự phòng "cho giao diện luôn sống động"** — nó không làm dịu một sự cố, nó xoá dấu vết của sự cố: `articles.length >= 4 ? articles : [...articles, ...BIA]` khiến một mảng RỖNG cho ra một trang chủ ĐẦY. 19 bài viết, 4 văn bản có số hiệu và người ký, 5 trạm thuỷ văn có mực nước, 9 số điện thoại — tất cả đã lên staging. Bộ canh phải soi **toàn cây**, vì "ở đây thì chưa có nguồn" là câu người viết component nào cũng tự thấy mình là ngoại lệ (§10.54).
17. **Tham số chỉ có hiệu lực MỘT LẦN thì tệp cấu hình không còn là bằng chứng — phải đo thứ ĐÃ được tạo ra.** `POSTGRES_INITDB_ARGS` chỉ chạy lúc `initdb` dựng cluster; sau đó tệp compose và CSDL thật có thể nói hai điều khác nhau **vĩnh viễn** mà không lệnh nào báo sai. Đo 26/8: vá tệp rồi `up -d --force-recreate` vẫn ra collation cũ — bản vá tệp một mình chỉ tạo **cảm giác** đã xong. Cùng họ: quyền thư mục lúc tạo volume, `docker login` trên máy chủ (§10.56).

18. **Đổi trạng thái chỉ qua Workflow engine, và cấm lách bằng transition giả** — hash chain đang ký tên vào lịch sử, bịa một bước chuyển là bịa một chữ ký.

19. **Việc làm xong nửa đường trông y hệt việc làm xong** — nghiệm thu WS-21 tìm ra một placeholder văn bản, một component gọi sai kiểu, một `navigate` mà đầu nhận không đọc; cả ba đều đã được tích ✅. Nghiệm thu phải đối chiếu với **mã thật**, không đối chiếu với bản ghi tiến độ.
**Về công cụ và quy trình**

20. **Script của workflow phải kiểm bằng `bash -c`** — zsh không tách từ mặc định, thử ở máy local không lộ ra mà runner chạy bash.
21. **Nâng cấp trước, suppress sau; tra phiên bản bằng `maven-metadata.xml`, không bằng API tìm kiếm** — API `solrsearch` trả kết quả cũ, suýt lập suppression cho 49 CVE **đã có bản vá**.
22. **Squash xong thì nhánh nguồn đã chết — cắt nhánh mới từ `dev`** (`.githooks/pre-push` canh; `make hooks` để bật, và nó là cấu hình **cục bộ từng bản clone**).
23. **Đọc log theo trình tự, đừng đọc theo mã lỗi** — dòng đáng chú ý nhất thường nằm *trước* thứ được báo là lỗi.
24. **`skipped` của một required check được GitHub tính là ĐẠT** — bộ lọc đường dẫn trục trặc thì phải mặc định **chạy thừa**, không bỏ sót. ⚠ Và bộ lọc phải bao **những tệp mà bài kiểm ĐỌC**, không chỉ những tệp nó nằm cùng thư mục: 7 lớp kiểm của bộ BE đọc `frontend/` và `deploy/`, nên bộ lọc cũ bỏ qua job canh chúng **đúng lúc chúng thay đổi**.

25. **Một bộ canh theo hình dạng phải được thử với dữ liệu THẬT đang dùng** *(cùng họ với nhóm phép kiểm ở trên)* — ba bộ canh "không ghi cứng liên hệ Công ty", chỉ **một** bắt được khi lỗi tái phát: regex điện thoại đòi khoảng trắng giữa các nhóm số trong khi số thật dùng dấu chấm; regex địa chỉ phân biệt hoa thường trong khi địa chỉ mới viết HOA. Bắt theo hình dạng là đúng hướng, nhưng hình dạng phải đối chiếu với dữ liệu đang chạy — nếu không thì nó chỉ canh được cái đã chết. Bổ trợ bằng một bài ở tầng **cấu trúc** (mọi khoá `company.*` phải rơi về rỗng): bắt theo từng loại dữ liệu thì luôn có loại thứ tư lọt qua.

26. **Merge không đụng độ ≠ merge không vỡ** — `git merge-tree` sạch, typecheck sạch, phân tích tệp cho thấy không đụng file nào của backend; vậy mà bộ test FE trên cây đã merge vẫn đỏ, vì nhánh kia khôi phục một lỗi mà nhánh này có bài canh. **Xung đột văn bản và xung đột ngữ nghĩa là hai chuyện khác nhau** — phải chạy bộ kiểm trên chính cây đã hợp nhất, không suy ra từ việc mỗi nhánh riêng lẻ đều xanh.

27. **Đếm "đã dựng xong bao nhiêu tính năng" là đếm sai đơn vị** — thứ người dùng nhận được là một vòng khép kín *nhập → lưu → hiện*, và một nửa vòng chạy hoàn hảo vẫn cho ra số không. Lượt 28/8 tìm ra **sáu** cột/khoá/tham số thiếu đúng một nửa cặp đọc–ghi, **bốn trong số đó ra đời một ngày trước** từ một đợt cẩn thận, có bài kiểm, có nghiệm thu: bảng `org_unit_leaders` chỉ có đường đọc · ba cột liên hệ có người hiển thị mà không ai ghi · `shortName` qua validate rồi bị vứt · `PUT` không màn hình nào gọi · hai cột tài liệu có setter mà lời gọi duy nhất nằm trong một bài kiểm · component có ba props mà nơi gọi truyền rỗng. Triệu chứng luôn giống nhau và luôn im lặng: **màn hình báo *lưu thành công*, cổng không đổi gì.**

28. **Một cơ chế canh gác phải nói ra phạm vi của chính nó** — ba lần trong hai ngày cùng một hình dạng: `NginxSecurityHeadersTest` soi mỗi `admin-app` trong khi cổng công khai chạy không CSP · `PortalSettingsReadTest` soi mỗi một tệp migration nên mọi khoá seed trước đó đi lọt · bộ canh màu chưa phủ `admin-app`. Bộ canh đúng luật, hẹp hơn nơi nó phải chặn, và **cái xanh của nó đọc như một lời bảo đảm**. Không phủ hết được thì ghi giới hạn vào chính bộ canh và mở một dòng nợ có số đo.

29. **Một bài kiểm chứng ngược có thể sai theo đúng cách mà thứ nó kiểm chứng đang sai** — người viết cả hai là cùng một người, mang cùng một giả định. Ngày 28/8 cả hai lượt kiểm chứng ngược đều hỏng: một cái đặt `--` trước câu `DELETE` rồi chờ bộ canh đỏ (không đỏ — regex không biết SQL có chú thích), một cái khẳng định mẫu bắt enum trả về 2 giá trị từ một enum có 3 (**chép lại lỗi thay vì bắt nó**). Thứ cứu được không phải bài kiểm chứng ngược mà là một khẳng định **về số lượng** — `hasSizeGreaterThanOrEqualTo(3)` không chia sẻ giả định nào với mẫu regex. Bổ sung luật 10: xác nhận bản hỏng **đã được nạp** *và* **bộ canh nhìn thấy nó** là hai chuyện khác nhau.

30. **Bộ test chạy migration từ CSDL RỖNG mù trước cả một lớp lỗi — và lớp ấy chỉ hiện ra lúc deploy** — hai lượt CD liên tiếp ngày 27/8 chết vì đúng nó. Trên CSDL rỗng **không có checksum cũ để so** (§10.65) và **không tồn tại khái niệm out-of-order** (§10.66): Flyway sắp mọi tệp theo version rồi áp tuần tự, xanh trọn vẹn. 688 bài kiểm không sai — chúng **về nguyên tắc** không thể thấy. Mọi bảo đảm về migration vì thế phải neo vào thứ **ngoài** bộ test: vân tay ghi trong kho (`db-migration-checksums.txt`) và phép so với **nhánh nền** (`kiem-thu-tu-migration.sh`). ⚠ Hệ quả cho việc đặt tên: số hiệu `V<YYYYMMDD><số thứ tự>` **trông như** dấu thời gian nên rất dễ viết *thành* dấu thời gian, và hai cách viết chỉ khác nhau ở đúng chỗ không ai nhìn — thứ tự sắp xếp.


## Quy ước làm việc với user

- User: QuanTran (quantran@goapps.team). Trả lời tiếng Việt, ngắn gọn, đi thẳng vào vấn đề.
- Khi cập nhật quyết định kiến trúc: sửa `architecture-review.md` trước, rồi đồng bộ sang `function-spec.md` và `implement.md`.
