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
| `conventions.md` | Convention coding/design/security + đặc tả Common Platform (envelope, exception, error code, middleware, utils, RBAC 3 tầng, chống giả mạo) — **luật** bắt buộc khi viết code |
| `docs/coding-guide.md` | **Cách làm** — công thức viết một chức năng nghiệp vụ theo thứ tự (migration → entity → workflow → service → controller → seed quyền → mã lỗi → test), kê đủ những gì Core cho sẵn để không ai dựng lại, kèm các bẫy đã trả giá. Đọc cùng `conventions.md`: file kia là luật, file này là đường đi |
| `docs/deploy-staging.md` | **Kế hoạch thực thi staging** — chốt cấu hình VPS-2 (kèm phép cộng bộ nhớ cho thấy 4 GB không đủ), cắt chi phí ở đâu và ba chỗ cấm cắt, 9 bước theo thứ tự có phép kiểm. Dùng chung `deploy-guideline.md` §2–§4 cho phần dựng máy, không chép lại |
| `docs/ui-styles.md` | **Quy chuẩn UI styles** — triết lý thiết kế, bảng màu (qua `design-tokens`), typography (Noto Sans), spacing, animation (nguyên tắc + danh sách được phép/cấm), component styling, responsive, accessibility. Đọc trước khi sửa CSS/theme/styling ở `admin-app` hoặc `public-web` |
| `business-open-questions.md` | Phần I-A + I-B: BOQ đợt 1 và đợt 2 **đã đóng** (tóm tắt tại chỗ, nguyên văn ở `docs_origin/`). Phần II: **8 mục còn mở** cần khách cung cấp. Phần III: **truy vết chức năng nào còn chứa điểm chưa chốt** — đọc trước khi code 1 chức năng |
| `phase0-tracking.md` | **Bảng theo dõi tiến độ Phase 0** — 11 hạng mục WS-1→WS-11, **107 task** dạng checkbox, mỗi WS tự chứa điều kiện tiên quyết/đầu ra/cách kiểm chứng. Tick khi làm xong; cuối file là **21 mục Definition of Done** và **Sổ nợ liên WS — nguồn duy nhất của nợ Phase 0** |
| `phase1-tracking.md` | **Bảng theo dõi tiến độ Phase 1** (CMS + master data công trình) — 12 hạng mục WS-12→WS-23, **112 task**, 17 mục Definition of Done, sổ nợ riêng. ⭐ Chứa mục **"Nghiệp vụ — 18 điểm đã làm rõ trước khi code"**: những chỗ spec không nói hoặc nói ra hai nghĩa, kèm cột "ai quyết" (nội bộ / phải hỏi Công ty) |
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

## Trạng thái

**Phase "Tài liệu hệ thống"** ✅ xong 12/8/2026 — BOQ đợt 1 (A–F) + đợt 2 (G) đã đóng và đồng bộ vào `function-spec.md` **v2.2**.
**Phase 0 — Core Platform** ✅ xong 10/11 hạng mục. Còn **WS-11 (Deploy Staging/Production, 10 pd)** treo để khép sổ.
**Phase 1 — CMS & master data công trình** ✅ **xong 24/8/2026** — WS-12→WS-23 đóng đủ, **16/17 mục DoD** có phép kiểm đứng sau. Nhánh `fix-public-web-ui` đã hợp nhất về `feat-CICD`.

⬜ **Mục DoD duy nhất còn treo**: **DOD1.17** trang chủ cổng < 3s (NFR-02) — chỉ đo được trên môi trường gần thật, nằm trong checklist nghiệm thu của `docs/deploy-guideline.md`. Không chặn code, chặn nghiệm thu cùng WS-11.

⚠⚠ **Ba lần liên tiếp một bản ghi "đã xong" bị lượt rà sau bác bỏ** — đây là hình dạng rủi ro đặc trưng của dự án này, không phải sự cố lẻ:

| Ngày | Lượt rà tìm ra |
|---|---|
| 22/8 | bản ghi "đã xong" bị bác bỏ toàn phần |
| 23/8 | **4/11 mục WS-21** chưa làm hoặc hỏng hẳn · **4/17 cam kết DoD** không có phép kiểm nào — §10.36 |
| 24/8 | **1 lỗi CHẶN nghiệp vụ** trong phạm vi đã tick (trả bài về sửa tắc hoàn toàn) · image quản trị phát mã nguồn · bộ lọc CI bỏ qua đúng job nó cần · hotfix làm tái phát lỗi ghi cứng — §10.37 |
| 24/8 (merge `dev`) | CI đỏ ở job đóng gói image `public-web` **dù 8 cổng kiểm ở máy đều xanh** — biến build rỗng, `??` không đỡ, `new URL('')` giết `next build` — §10.38 |
| 25/8 (sau deploy staging) | **204 No Content bị giao diện biến thành lỗi trên 24 endpoint** — việc đã commit xong mà người dùng thấy báo lỗi. Lượt sửa 22/8 đã chữa *triệu chứng thứ hai* của đúng chuỗi này — §10.40 · `DB_APP_PASSWORD` không ai đọc che mất biến thật sự thiếu — §10.41 |

⛔ Hệ quả rút ra: **"đã tick" không phải bằng chứng.** Trước khi mở một giai đoạn mới, đối chiếu với mã thật và chạy đường mà người dùng thật đi.

⛔ Và **"xanh ở máy" cũng không phải bằng chứng**: hai job chỉ sống trên runner (quét CVE · đóng gói image) chạy trên **cây checkout sạch, không có `.env.local`**. Mọi lượt build ở máy đều nạp tệp ấy — nên một biến môi trường rỗng là trạng thái mà `make ci-local` **về nguyên tắc không dựng lại được**. Muốn kiểm trước thì phải `docker build` đúng đối số của `ci.yml`.

⛔ **Cấm seed dữ liệu công trình/thuỷ văn "cho đẹp demo"** — ô nào chưa có nguồn thì nói thẳng là chưa có.

**Codebase đo ngày 25/8**: **565 test BE** (239 core + 20 content + 24 operations + 282 app) + **197 test FE** (142 admin + 55 public) · **74 mã lỗi** (BE = FE) · 88 quyền / 12 vai trò / 334 dòng phân quyền · **32 bài ArchUnit** (7 lớp, gồm 11 bài tự-kiểm chứng minh luật bắt được vi phạm) · 7 phép kiểm bộ đọc tracking · mọi cổng bao phủ **chạy thật** (content 18.2%) · 0 CVE ≥ 7.

### Tra ở đâu

| Cần gì | Đọc ở đâu |
|---|---|
| Nợ đang treo, task còn lại | **`.claude/master-tracking.md`** — nguồn DUY NHẤT (conventions.md §6). `phase0-tracking.md` / `phase1-tracking.md` chỉ là lưu trữ, cấm sửa |
| **Lý do** một quyết định, **nguyên nhân gốc** một lỗi đã sửa | `architecture-review.md` **§9** (Phase 0, 14 mục) · **§10** (Phase 1, 41 mục — §10.35 đợt vá sau WS-22, §10.36 nghiệm thu lại WS-21 + DoD, §10.37 nghiệm thu image, §10.38 CI đỏ sau merge `dev`, §10.40 lỗi 204, §10.41 biến CSDL không ai đọc) |
| Cách viết một chức năng + bảng bẫy tra nhanh | `docs/coding-guide.md` |
| Luật bắt buộc khi viết code | `conventions.md` |
| Nghiệp vụ | `function-spec.md`; điểm chưa chốt → `business-open-questions.md` Phần III |

### Nghiệp vụ còn chờ Công ty — 8 mục

Không mục nào **chặn code**, chỉ chặn **dữ liệu khởi tạo và nghiệm thu**; riêng **G5** chặn đích danh CN-01.7 (lưu mã số hệ thống văn bản) nên task đó tách riêng.

**G3-a** lượng mưa · **G5** mã số hệ thống văn bản (+ xin SSO) · **G6** mẫu 2C-BNV · **G8** tuyến sông/lý trình/toạ độ + danh mục công trình · **G9-a** bộ mức ngưỡng · **G10** duyệt format báo cáo · **G13** bộ nhận diện cổng (logo/màu/GA/GTM/reCAPTCHA) · **G14** cây danh mục + menu + nội dung 4 trang tĩnh.

Gửi kèm `report-templates-proposal.md`. Chi tiết từng mục: `business-open-questions.md` Phần II.

### Ba việc bấm ở GitHub còn treo

**Nợ #45** bật Dependency graph (không bật thì job *Soi phụ thuộc PR thêm vào* tự bỏ qua — phép kiểm chưa chạy lần nào) · **nợ #27** chỉnh 2 mục bảo vệ nhánh (`docs/branch-protection.md` §6.2) · **nợ #46** thêm 3 context đóng gói image vào `required_status_checks` của `dev` — hai job ấy nay chạy ở PR nhưng chưa bắt buộc thì chỉ *hiện* lỗi chứ không *chặn* merge (§10.38).

📌 Cả ba đều là cùng một hình dạng: **một cổng kiểm tồn tại trong mã nhưng chưa có hiệu lực ở nơi nó phải chặn.**

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
10. **Làm hỏng có chủ đích để kiểm chứng thì phải xác nhận bản hỏng ĐÃ được nạp** — lượt kiểm chứng bản vá IDOR ngày 23/8 báo 6/6 xanh sau khi đã gỡ lớp bảo vệ; hoá ra `install` bị Checkstyle chặn, output đã bị `>/dev/null` nuốt, và bài kiểm chạy trên jar cũ *còn nguyên bản vá*. Chính bước chứng minh cũng là một xanh giả.
11. **Phép kiểm chạy lâu phải ưu tiên báo cáo trọn vẹn hơn dừng sớm** — reactor dừng ở module đầu làm 4 vòng quét CVE chỉ soi được **một** module; nếu module đầu tình cờ sạch thì ta tưởng cả dự án sạch.

**Về chỗ đặt một bảo đảm**

12. **Khi một bảo đảm phải đúng ở nhiều đường vào, đặt nó ở chỗ *dữ liệu đi qua*, đừng đặt ở *nơi gọi*** — không đặt được thì phải có phép kiểm đếm đủ các đường vào. (XSS lưu trữ lọt qua 2/3 đường ghi `settings`; `SvgSanitizer` có 9 bài kiểm mà không nằm trên đường chạy nào.)
13. **Một cột dẫn xuất trộn hai nguồn khác chiều lọc thì kết quả phụ thuộc *ai bấm F5 sau cùng*** — `ConstructionStatusService.tinh()` đếm sự cố bằng câu native (không lọc) nhưng tra mã tình hình vận hành bằng câu derived (có lọc); người ngoài đơn vị mở màn hình là trạng thái bị hạ xuống "Bình thường" **cho tất cả mọi người**, vì đó là cột được ghi xuống CSDL.
14. **Chỗ nào con người phải nhớ hai nơi thì chỗ đó cần một phép kiểm nhớ hộ** — enum SPI ↔ enum domain · từ vựng trình soạn thảo ↔ danh sách cho phép của bộ lọc ↔ CSS cổng công khai · mã lỗi BE ↔ FE · URL tile ↔ CSP.
15. **Công tắc / cột / tham số chưa ai đọc là một lỗi, không phải việc để dành** — `limits.upload.max-mb.*`, `company.*`, `attachments.valid_from` đều bày ra ở giao diện hoặc lược đồ mà không dòng mã nào đọc. ⛔ Hệ quả: **không seed tham số `settings` cho tính năng chưa dựng**.
16. **Số 0 là một câu khẳng định** — ô số liệu chưa có nguồn phải trả rỗng kèm lý do, và ràng buộc đó ép ở **hàm dựng** chứ không ở lời dặn.
17. **Đổi trạng thái chỉ qua Workflow engine, và cấm lách bằng transition giả** — hash chain đang ký tên vào lịch sử, bịa một bước chuyển là bịa một chữ ký.

18. **Việc làm xong nửa đường trông y hệt việc làm xong** — nghiệm thu WS-21 tìm ra một placeholder văn bản, một component gọi sai kiểu, một `navigate` mà đầu nhận không đọc; cả ba đều đã được tích ✅. Nghiệm thu phải đối chiếu với **mã thật**, không đối chiếu với bản ghi tiến độ.
**Về công cụ và quy trình**

19. **Script của workflow phải kiểm bằng `bash -c`** — zsh không tách từ mặc định, thử ở máy local không lộ ra mà runner chạy bash.
20. **Nâng cấp trước, suppress sau; tra phiên bản bằng `maven-metadata.xml`, không bằng API tìm kiếm** — API `solrsearch` trả kết quả cũ, suýt lập suppression cho 49 CVE **đã có bản vá**.
21. **Squash xong thì nhánh nguồn đã chết — cắt nhánh mới từ `dev`** (`.githooks/pre-push` canh; `make hooks` để bật, và nó là cấu hình **cục bộ từng bản clone**).
22. **Đọc log theo trình tự, đừng đọc theo mã lỗi** — dòng đáng chú ý nhất thường nằm *trước* thứ được báo là lỗi.
23. **`skipped` của một required check được GitHub tính là ĐẠT** — bộ lọc đường dẫn trục trặc thì phải mặc định **chạy thừa**, không bỏ sót. ⚠ Và bộ lọc phải bao **những tệp mà bài kiểm ĐỌC**, không chỉ những tệp nó nằm cùng thư mục: 7 lớp kiểm của bộ BE đọc `frontend/` và `deploy/`, nên bộ lọc cũ bỏ qua job canh chúng **đúng lúc chúng thay đổi**.

24. **Một bộ canh theo hình dạng phải được thử với dữ liệu THẬT đang dùng** *(cùng họ với nhóm phép kiểm ở trên)* — ba bộ canh "không ghi cứng liên hệ Công ty", chỉ **một** bắt được khi lỗi tái phát: regex điện thoại đòi khoảng trắng giữa các nhóm số trong khi số thật dùng dấu chấm; regex địa chỉ phân biệt hoa thường trong khi địa chỉ mới viết HOA. Bắt theo hình dạng là đúng hướng, nhưng hình dạng phải đối chiếu với dữ liệu đang chạy — nếu không thì nó chỉ canh được cái đã chết. Bổ trợ bằng một bài ở tầng **cấu trúc** (mọi khoá `company.*` phải rơi về rỗng): bắt theo từng loại dữ liệu thì luôn có loại thứ tư lọt qua.

25. **Merge không đụng độ ≠ merge không vỡ** — `git merge-tree` sạch, typecheck sạch, phân tích tệp cho thấy không đụng file nào của backend; vậy mà bộ test FE trên cây đã merge vẫn đỏ, vì nhánh kia khôi phục một lỗi mà nhánh này có bài canh. **Xung đột văn bản và xung đột ngữ nghĩa là hai chuyện khác nhau** — phải chạy bộ kiểm trên chính cây đã hợp nhất, không suy ra từ việc mỗi nhánh riêng lẻ đều xanh.

## Quy ước làm việc với user

- User: QuanTran (quantran@goapps.team). Trả lời tiếng Việt, ngắn gọn, đi thẳng vào vấn đề.
- Khi cập nhật quyết định kiến trúc: sửa `architecture-review.md` trước, rồi đồng bộ sang `function-spec.md` và `implement.md`.
