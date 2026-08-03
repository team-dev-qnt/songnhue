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
| `business-open-questions.md` | Điểm nghiệp vụ chưa clear chờ user confirm (gap: kế hoạch vụ mùa, trạm quan trắc...) — confirm xong phải cập nhật ngược vào function-spec.md |
| `Tổng quan HT PM...docx.md`, `Đặc tả hệ thống...docx.md` | Tài liệu gốc từ khách hàng — chỉ tham khảo, đã được tổng hợp vào function-spec.md |

## Module

- **MOD-01** CMS/Cổng TTĐT (bài viết workflow duyệt, danh mục, media, banner, liên hệ, widget thủy văn)
- **MOD-02** Vận hành công trình (danh mục công trình, polling thủy văn, cảnh báo ngưỡng, GIS, nhật ký vận hành, báo cáo async, sự cố, dashboard) — lõi nghiệp vụ
- **MOD-03** HRM (sơ đồ tổ chức, hồ sơ CBNV, nghỉ phép — tuân thủ NĐ 13/2023, BLLĐ 2019)
- **MOD-04** Tích hợp hệ thống văn bản điều hành CÓ SẴN (không xây mới)
- **MOD-05** Quản trị (RBAC, audit, backup)

## Tech stack (đã chốt — không tự ý đổi)

PostgreSQL 16 + PostGIS · Spring Boot 3 (Java 21) · Next.js (public, SSR/ISR) + React/Vite/AntD 5 (admin) · **Không Redis (v1)** — cache in-process (Caffeine) + bảng `hydro_latest`; denylist ở DB · **DB-backed job queue + ShedLock (giữ sẵn, bật khi ≥2 node)** · **Worker in-process (v1)** · MinIO · ECharts · Leaflet/MapLibre + OSM · Flyway · Auth: access token 30' + refresh rotation httpOnly cookie · **Modular Monolith 1 node (v1), stateless để thêm node = đổi cấu hình** · ArchUnit enforce boundary.

## Quy tắc bất di bất dịch khi code

1. Timestamp lưu `timestamptz` UTC; hiển thị UTC+7. Không lưu giờ địa phương.
2. NUMERIC/BigDecimal cho mọi số đo và tiền — cấm float/double.
3. Mọi giá trị tính toán (giờ chạy máy, lưu lượng bơm, tổng hợp kỳ, số dư phép) tính ở BE; FE chỉ hiển thị.
4. Đổi trạng thái entity chỉ qua Workflow engine (Core) — không UPDATE status trực tiếp.
5. Data scoping theo Xí nghiệp/đơn vị ở tầng repository filter, không dựa vào dev nhớ thêm WHERE.
6. Module không import repository của module khác — chỉ gọi qua service interface.
7. `org_units` là 1 bảng dùng chung cho cả Xí nghiệp (MOD-02) và phòng ban (MOD-03).
8. Raw data thủy văn (`hydro_raw_logs`) append-only; báo cáo/dashboard đọc từ bảng agg, không scan raw.
9. Soft delete + audit log (old/new value) cho mọi entity nghiệp vụ.
10. Trường nhạy cảm HR (🔒 trong spec): bảng riêng `employee_sensitive`, AES-256-GCM, key ngoài DB.
11. Mọi connection/setup (DB, MinIO, SMTP, API ngoài...) đọc từ env — cấm hardcode; thiếu env bắt buộc → fail-fast lúc startup; client khởi tạo qua Spring bean, không tạo trực tiếp trong code nghiệp vụ.

## Trạng thái & mục chờ confirm

- ✅ Confirmed: dữ liệu thủy văn = polling API ngoài được cấp (C2 code theo interface + mock adapter trong lúc chờ tài liệu API thật).
- ⬜ Chờ: phương án tích hợp hệ thống văn bản (phạm vi MOD-04); file mẫu báo cáo chuẩn công ty; tài liệu API telemetry thật.

## Quy ước làm việc với user

- User: QuanTran (quantran@goapps.team). Trả lời tiếng Việt, ngắn gọn, đi thẳng vào vấn đề.
- Khi cập nhật quyết định kiến trúc: sửa `architecture-review.md` trước, rồi đồng bộ sang `function-spec.md` và `implement.md`.
