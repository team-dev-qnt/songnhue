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
- ➡️ **Bước tiếp theo**: gửi 6 mục còn mở + `report-templates-proposal.md` cho Công ty; bắt đầu **Phase 0** theo `phase0-tracking.md` — trong đó **ArchUnit, 2FA Admin và khung giám sát dữ liệu quá hạn phải nằm trong Phase 0**, load test 200 CCU đưa vào kế hoạch từ Phase 2.

## Quy ước làm việc với user

- User: QuanTran (quantran@goapps.team). Trả lời tiếng Việt, ngắn gọn, đi thẳng vào vấn đề.
- Khi cập nhật quyết định kiến trúc: sửa `architecture-review.md` trước, rồi đồng bộ sang `function-spec.md` và `implement.md`.
