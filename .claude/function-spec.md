# FUNCTION SPECIFICATION — HỆ THỐNG QUẢN TRỊ & ĐIỀU HÀNH THỦY LỢI SÔNG NHUỆ

> Tài liệu đặc tả chức năng cô đọng cho team dev. Tổng hợp từ "Tổng quan HT PM Quản lý điều hành TLSN", "Đặc tả hệ thống Website Thủy Lợi Sông Nhuệ" và **SRS_QuanTriDieuHanh_TLSN ver 06.8.2026** (SRS v1.0, 23/07/2026).
> Phiên bản: **2.2** — Cập nhật: 2026-08-12 (áp dụng confirm **đợt 2 — mục G**: G1, G2, G3 (một phần), G4, G7, G9, G11, G12)
>
> ⚠ **Thay đổi v2.2** (theo confirm đợt 2 của Công ty): **G1 = PA A** — bỏ phiếu sự cố riêng, **gộp sự cố vào Lịch sử sửa chữa** (CN-02.2) · **G2** — Công ty **không cần** chỉ tiêu giờ chạy máy/điện năng/m³ bơm → đóng vĩnh viễn, không mở lại màn hình nhập · **G3** — chấp nhận không có API lịch sử (hệ thống tự fetch & lưu), **chu kỳ polling chốt: 2 phút/lần vào các phút lẻ**, có **rate-limit theo khung cập nhật**; trạm trục trặc → **GIS màu xám** · **G4** — tình hình vận hành cống **không có trong API**, nhập tay qua màn hình Admin, **danh mục mã có CRUD** + ánh xạ trạng thái + màu (CN-02.11 mới) · **G7** — audit log giữ **5 năm** rồi kết xuất lưu trữ · **G9** — Admin tự cấu hình ngưỡng, hệ thống chạy với ngưỡng mặc định tới khi có số liệu thật · **G11** — người nhận cảnh báo = nhóm "Ban điều hành" + auto người phụ trách công trình liên quan · **G12** — chốt con số NFR nghiệm thu. Chi tiết ở `business-open-questions.md` Phần I-B.
>
> ⚠ **Thay đổi lớn v2.1** (theo `docs_origin/Trả lời Business Open Questions 12.8.2026.docx.md`): **BỎ Nhật ký vận hành** (CN-02.8) — thay bằng **Lịch sử sửa chữa** (CN-02.2) do Admin/người được phân quyền nhập · **BỎ Kế hoạch tưới tiêu/vụ mùa** (kéo theo BC-04, BC-07) · **BỎ diện tích tưới tiêu** và **trạng thái tổ máy realtime** · Lưu vực = **trường tham chiếu văn bản** · Trạng thái bản ghi thủy văn còn **2 mức** (Hợp lệ/Nghi ngờ) · **Bỏ SMS ở v1** — thông báo qua website + email · **CN-01.7 đổi bản chất**: không đồng bộ danh sách văn bản mà **lưu mã số truy cập + auto-login** sang hệ thống nguồn · **Đã có endpoint API thủy văn thật** (CN-03.2) · Mọi tham số vận hành đưa vào **biến cấu hình**. Đối chiếu chi tiết ở `business-open-questions.md` Phần I-A.
>
> ⚠ **Thay đổi lớn v2.0**: cấu trúc 5 module đồng bộ theo SRS — **tách "Quản lý dữ liệu thủy văn" thành MOD-03 riêng**; **gộp tích hợp văn bản điều hành vào MOD-01 (Cổng TTĐT)**; HRM chuyển thành MOD-04; Quản trị hệ thống thành MOD-05 (bổ sung chức năng theo SRS). Các nghiệp vụ **nhật ký vận hành, phiếu sự cố, báo cáo vận hành BC-01..08** KHÔNG có trong SRS v1.0 → khi đó giữ lại như phần mở rộng đánh dấu 🔷 chờ khách xác nhận. *(→ **Đã đóng ở v2.2**: khách chốt loại bỏ nhật ký vận hành + phiếu sự cố riêng + BC-01/02/03/08. **Không còn hạng mục 🔷 nào**.)*

---

## 0. TỔNG QUAN

### 0.1. Phạm vi
Xây dựng hệ thống quản trị và điều hành công trình thủy lợi + Cổng thông tin điện tử cho Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ. Responsive Web (360px → 2560px), hỗ trợ trình chiếu màn hình lớn tại Phòng điều hành. Không bao gồm: hạ tầng phần cứng, mạng nội bộ (trừ thỏa thuận riêng), hệ thống kế toán/tài chính.

### 0.2. Kiến trúc & Tech stack

> ⚠ Đã cập nhật theo `architecture-review.md`. **v1 triển khai 1 node**, **bỏ Redis**, **worker in-process**, ShedLock giữ sẵn (bật khi ≥2 node).

| Thành phần | Công nghệ (đã chốt) |
|---|---|
| Kiến trúc | Modular Monolith, Layered; ArchUnit enforce ranh giới module |
| Public web | Next.js (SSR/ISR) + Tailwind — tách app riêng, phục vụ SEO |
| Admin app | React 18 + Vite + TypeScript + Ant Design 5; ECharts |
| Backend | Spring Boot 3 (Java 21); springdoc-openapi; Flyway migration |
| Auth | Access token 30' + Refresh token rotation (httpOnly cookie); BCrypt; denylist bảng DB |
| Database | PostgreSQL 16 + PostGIS; partition tháng cho time-series; full-text `unaccent` tiếng Việt |
| Cache | **Không Redis (v1)** — bảng `hydro_latest` (Postgres, poller UPSERT) cho reading mới nhất; Caffeine in-process cho site config |
| Job/Cron | DB-backed queue (SKIP LOCKED) + Spring Scheduler + ShedLock (giữ sẵn, bật khi ≥2 node) |
| File storage | MinIO (S3-compatible) — dùng ngay từ v1 để app stateless |
| GIS | Leaflet/MapLibre + OSM tiles (Google Maps optional); GeoJSON, KMZ (Shapefile — chốt ở thiết kế chi tiết, xem `business-open-questions.md`) |
| Chart | Apache ECharts |
| Monitoring | Prometheus + Grafana + Micrometer; log JSON + correlation-id, rotation 30 ngày |
| Backup | **`pg_dump` hàng đêm**, retention 30 ngày, lưu khác máy — **RPO ≤ 24h, RTO ≤ 4h** (bản tối giản, chốt 2026-08-13; xem `architecture-review.md` §6.5) |

### 0.3. Vai trò (Actors)

Hợp nhất từ nhóm vai trò tổng quát của SRS §2.2 và các actor chi tiết theo module.

| Vai trò | Phạm vi |
|---|---|
| Guest / Người dùng công cộng | Xem tin tức, thông tin công ty, văn bản công khai, gửi liên hệ, widget thủy văn |
| Cán bộ nội bộ (Viewer) | Xem báo cáo, dữ liệu thủy văn, GIS, hồ sơ công trình, danh bạ theo phòng ban được phân |
| Biên tập viên | Soạn thảo bài viết, media (MOD-01) — không tự xuất bản |
| Quản trị nội dung | Duyệt/xuất bản bài viết, danh mục, banner, liên hệ, phản hồi (MOD-01) |
| Cán bộ văn thư | Đánh dấu văn bản điều hành công khai phía hệ thống nguồn (MOD-01 tích hợp) |
| Cán bộ kỹ thuật | Hồ sơ công trình, số hóa GIS, điểm đo thủy văn, cấu hình ngưỡng, ghi nhận & khắc phục sự cố (CN-02.2), cập nhật tình hình vận hành cống (CN-02.11) |
| Cán bộ vận hành (Xí nghiệp) | Xem công trình + dữ liệu thủy văn thuộc XN mình; ghi nhận sự cố (CN-02.2) và cập nhật tình hình vận hành cống khi được phân quyền. *(Vai trò "Operator nhập nhật ký vận hành" đã bị loại khỏi scope 12/8/2026)* |
| Quản lý công trình / Quản lý XN | Duyệt hồ sơ/nhật ký, đóng cảnh báo/sự cố, báo cáo XN mình |
| Trực ban điều hành | Nhận cảnh báo ngưỡng thủy văn, theo dõi dashboard màn hình lớn |
| Ban giám đốc / Điều hành | Xem dashboard điều hành, báo cáo tổng hợp đa chiều |
| Quản trị nhân sự (Admin HR) | Toàn quyền dữ liệu HRM; trường nhạy cảm 🔒 |
| Admin (QTV hệ thống) | Toàn quyền: cấu hình, tài khoản, phân quyền, API nguồn, audit, backup/restore |

### 0.4. Danh sách module (đồng bộ SRS §2.3)

| Mã | Module (SRS) | Nội dung chính |
|---|---|---|
| MOD-01 | Cổng thông tin điện tử (E-Portal) | Bài viết, danh mục, media, banner, liên hệ, phản hồi, cấu hình giao diện, tìm kiếm, **tích hợp hệ thống văn bản điều hành** (M1.8) |
| MOD-02 | Quản lý & vận hành công trình thủy lợi (GIS) | Danh mục công trình, thông số kỹ thuật, tài liệu, bản đồ GIS nhiều lớp, dashboard điều hành, thống kê, nhật ký thay đổi hồ sơ, **lịch sử sửa chữa/bảo trì — bao gồm cả ghi nhận & khắc phục sự cố** (thay thế nhật ký vận hành đã bỏ; chốt G1 = PA A), **tình hình vận hành cống** (CN-02.11, nhập tay) |
| MOD-03 | Quản lý dữ liệu thủy văn | Danh mục điểm đo & loại chỉ số, kết nối API bên thứ 3 (polling), bóc tách/chuẩn hóa/validate, lưu time-series, giám sát realtime, biểu đồ, báo cáo thủy văn, cảnh báo ngưỡng, hiển thị lên GIS |
| MOD-04 | Quản lý nhân sự (HRM) | Sơ đồ tổ chức, hồ sơ CBNV, lý lịch, lịch sử công tác, tài liệu, danh bạ, thống kê, nghỉ phép |
| MOD-05 | Quản trị hệ thống, tài khoản & phân quyền | Tài khoản, RBAC chi tiết theo màn hình, cấu hình hệ thống, audit log, backup/**restore**, giám sát health-check, thông báo hệ thống, quản lý phiên, cảnh báo đăng nhập bất thường, xuất/nhập cấu hình |

> **Ánh xạ so với cấu trúc cũ (v1.0 nội bộ)**: MOD-02(cũ, gộp) → tách thành **MOD-02 vận hành công trình** + **MOD-03 thủy văn**; MOD-03(cũ HRM) → **MOD-04**; MOD-04(cũ tích hợp văn bản) → **gộp vào MOD-01 (CN-01.7)**; MOD-05 giữ số, mở rộng chức năng. Bảng traceability chi tiết ở §10.

---

## 1. MOD-01 — CỔNG THÔNG TIN ĐIỆN TỬ (E-PORTAL)

Người dùng: Biên tập viên, Quản trị nội dung, Cán bộ văn thư, Admin.
Tích hợp: Widget thủy văn lấy dữ liệu từ **MOD-03** qua REST API nội bộ; danh sách văn bản điều hành đồng bộ từ hệ thống nguồn (CN-01.7).

### CN-01.1. Quản lý Bài viết & Tin tức (Cao) — *SRS M1.1, UC1.1*

**Soạn thảo**: Rich Text Editor (CKEditor/TinyMCE); ảnh inline (upload hoặc từ Media, căn lề, caption); embed YouTube/Vimeo; bảng biểu; internal/external link (option mở tab mới); Preview trước khi lưu.

**Trường dữ liệu chính**:

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| Tiêu đề | Text(255) | ✔ | SRS: tối đa 250 ký tự |
| Slug | Text | ✔ | Auto từ tiêu đề (bỏ dấu, gạch ngang); cho sửa tay; cảnh báo trùng |
| Ảnh đại diện | Image | ✘ | Thumbnail listing + chia sẻ mạng xã hội |
| Tóm tắt | Text(500) | ✘ | |
| Nội dung | RichText | ✔ | |
| Danh mục | Multi-select | ✔ | 1 bài thuộc nhiều danh mục |
| Tác giả | FK User | ✔ | Auto = user đang login, cho đổi |
| Nguồn tin, Tags | Text | ✘ | |
| Tệp đính kèm | File | ✘ | Nhiều tệp (pdf, docx, xlsx...) |
| Ngày xuất bản | DateTime | ✘ | Hẹn giờ đăng |
| Meta Title/Description/Keywords | Text 70/160/- | ✘ | SEO, đếm ký tự + cảnh báo đỏ vượt ngưỡng; hỗ trợ Open Graph |
| Lượt xem | Int | ✘ | Auto đếm (SRS) |
| Trạng thái | Enum | ✔ | Nháp / Chờ duyệt / Yêu cầu chỉnh sửa / Xuất bản / Gỡ bài / Lưu trữ |

**Workflow trạng thái (state machine)**:

```
NHÁP → (Gửi duyệt) → CHỜ DUYỆT → (Duyệt) → XUẤT BẢN → GỠ BÀI ⇄ XUẤT BẢN
         ↑ (Từ chối/Yêu cầu chỉnh sửa + lý do)  ↙                ↘ LƯU TRỮ
Mọi trạng thái → ĐÃ XÓA (soft delete, terminal, không phục hồi)
```
- Nháp: chỉ tác giả + Admin xem. Chờ duyệt: khóa chỉnh sửa, notify Quản trị nội dung. Gỡ bài: URL trả 404, giữ dữ liệu. Lưu trữ: ẩn listing, vẫn truy cập qua URL trực tiếp. Tái xuất bản từ Gỡ bài: không cần duyệt lại.
- **Tách biệt vai trò (SRS quy tắc §3.1.3)**: Biên tập viên KHÔNG được tự xuất bản bài của chính mình — bắt buộc qua bước duyệt của Quản trị nội dung.
- Xuất bản → cập nhật search index + audit log + email notify. Bài chỉ hiển thị công khai khi "Xuất bản" và trong khoảng thời gian hiệu lực (nếu đặt lịch).

**Hẹn giờ đăng**: cron 5 phút/lần tự chuyển bài đến hạn sang Xuất bản; màn hình liệt kê bài đang hẹn giờ.

**Danh sách bài viết**: bảng (ID, Tiêu đề, Danh mục, Tác giả, Ngày tạo/cập nhật, Trạng thái); tìm theo tiêu đề/nội dung; lọc theo danh mục/trạng thái/tác giả/khoảng thời gian; sort; bulk action; phân trang 20/50/100.

**Audit log bài viết**: lịch sử đầy đủ; so sánh phiên bản (diff); rollback về bản cũ.

### CN-01.2. Quản lý Danh mục nội dung (Cao) — *SRS M1.2, UC1.2*
- Cây danh mục tối thiểu 3 cấp (Menu chính → Submenu → Chuyên mục con).
- Thuộc tính: Tên, Slug, Danh mục cha, Mô tả, Ảnh đại diện, Thứ tự, Trạng thái Hiện/Ẩn.
- UI: tree/accordion thu gọn – mở rộng; kéo thả sắp xếp.
- **Xóa danh mục còn bài viết**: cảnh báo + yêu cầu chuyển bài sang danh mục khác trước khi xóa (SRS).

### CN-01.3. Quản lý Thư viện Media (Trung bình) — *SRS M1.3, UC1.2*
- Thư mục phân cấp tối đa 3 cấp (Ảnh / Video / Tài liệu); tạo, đổi tên, xóa (chỉ khi rỗng), di chuyển; hiển thị dung lượng + số file.
- Multi-upload kéo-thả, progress bar từng file; auto nén ảnh sang WebP (giữ bản gốc fallback); auto thumbnail 150/400/800px.
- Giới hạn: Ảnh (JPG/PNG/GIF/WebP/SVG) 10MB; Video (MP4/WebM) 500MB — khuyến nghị YouTube embed; Tài liệu (PDF/DOC/DOCX/XLS/XLSX/PPT) 50MB; ZIP 100MB.
- Quản lý: Grid/List view; tìm theo tên, lọc theo loại/thư mục/ngày; chi tiết file + copy URL 1 click; xóa có xác nhận + cảnh báo nếu file đang được bài viết tham chiếu.

### CN-01.4. Quản lý Liên hệ (Trung bình) — *SRS M1.5, UC1.3*
- Form public: Họ tên, Email, SĐT, Chủ đề, Nội dung + Google reCAPTCHA v3; bật/tắt từng trường, đặt bắt buộc.
- Email notify khi có liên hệ mới (nhiều người nhận); auto email xác nhận cho người gửi.
- Danh sách liên hệ: trạng thái Mới / Đã đọc / Đang xử lý / Đã phản hồi / Đóng / Lưu trữ; phân loại + chuyển phòng ban liên quan; ghi chú nội bộ; export Excel; không cho xóa khi 'Đang xử lý'.
- **SLA**: quá thời hạn xử lý cấu hình → nhắc nhở người phụ trách (SRS UC1.3).

### CN-01.5. Quản lý Cấu hình giao diện (Trung bình) — *SRS M1.4, M1.6*

**Banner/Carousel**: ảnh (khuyến nghị 1920×600), tiêu đề overlay, mô tả, link, thứ tự (kéo thả), Bật/Tắt, lịch hiển thị (ngày bắt đầu/kết thúc). Slider config: thời gian dừng (mặc định 5s), Fade/Slide, autoplay, arrows/dots.

**Footer**: WYSIWYG; khối Thông tin công ty, Google Maps embed, Liên kết nhanh, Mạng xã hội (Facebook/Zalo/YouTube), copyright; có preview.

**Widget Thủy văn (public)**: chọn nhiều điểm đo + thông số (Mực nước, Lưu lượng, Lượng mưa, Trạng thái); auto-refresh 5/10/15/30 phút; màu cảnh báo vàng/đỏ khi vượt ngưỡng; vị trí Sidebar/Banner dưới/Floating. Lấy dữ liệu qua REST API nội bộ **MOD-03** (token auth); API lỗi → hiện "Không có dữ liệu" (không lộ lỗi kỹ thuật).

**Cấu hình chung website**: Tên site, slogan, logo (SVG/PNG), favicon 32×32, màu chủ đạo/phụ, GA Tracking ID, GTM Container ID, Maintenance Mode.

**Menu điều hướng**: Header + Footer menu độc lập; item = Tên, Loại link (Danh mục/Bài viết/URL/Văn bản), mở tab mới, trạng thái, thứ tự; kéo thả + nested submenu.

**Trang đặc biệt**: Trang chủ (chọn khối: Slider, Bài nổi bật, Dịch vụ, Widget thủy văn, Văn bản mới); trang 404 custom; trang Tìm kiếm.

### CN-01.6. Quản lý Phản hồi/Đánh giá người dùng (Thấp) — *SRS M1.7*
- Thu thập đánh giá/khảo sát mức độ hài lòng hoặc góp ý về nội dung/dịch vụ trên cổng; tổng hợp phục vụ báo cáo.
- ✅ **Chốt D1 (12/8/2026)**: phase 1 **TẮT bình luận công khai tự do**; chỉ làm khảo sát/góp ý **kiểm duyệt 100%** (mọi mục gửi lên = 'Chờ duyệt', chỉ hiện sau khi Quản trị nội dung duyệt). Nếu sau này bật bình luận: bắt buộc họ tên + email + reCAPTCHA + lọc spam.
- ✅ **Chốt D3**: **chỉ tiếng Việt** — không làm đa ngôn ngữ, không i18n nội dung.
- ✅ **Chốt D4**: **không migrate** bài viết/tài liệu từ website cũ — Công ty tự nhập lại thủ công. → Không có hạng mục migration trong kế hoạch go-live.

### CN-01.7. Liên kết Hệ thống Văn bản điều hành (Trung bình) — *SRS M1.8, UC1.4* — ⭐ **ĐỔI BẢN CHẤT theo E3 (12/8/2026)**

> 🟥 **CHỨA ĐIỂM CHƯA CHỐT — G5. Không code phần lưu mã số trước khi có trả lời.** Chưa rõ **mã số riêng từng người hay chung 1 mã** → khác nhau hoàn toàn về schema (`external_system_credentials` per-user *vs* 1 dòng `settings` toàn hệ thống), UI và phân quyền. Nếu Công ty xin được **token dùng-một-lần/SSO** thì bỏ hẳn việc lưu credential. Các chức năng khác của MOD-01 **không bị ảnh hưởng**, làm bình thường.

**Quyết định của Công ty**: hệ thống văn bản điều hành là **website độc lập** (`songnhue.bhh40.net` — bao gồm Công văn đến/đi, Lịch làm việc, Thông báo nội bộ, Quản lý công trình, Tài liệu QLKT, Công bố thông tin…). **KHÔNG đồng bộ dữ liệu, KHÔNG API, KHÔNG đọc CSDL**. Thay vào đó: hệ thống mới **lưu thông tin đăng nhập của người dùng vào hệ thống đó** và cung cấp **1 link bấm vào là tự động đăng nhập sang**.

**Hiện trạng kỹ thuật hệ thống nguồn (đã khảo sát 12/8/2026)**:
- Đăng nhập bằng **duy nhất 1 "mã số"** (form field `textlogin`, POST `default.aspx`, ASP.NET WebForms có `__VIEWSTATE`) — **không có cặp username/password**. Mã số do Ban quản trị hệ thống nguồn cấp cho từng tổ chức/cá nhân.
- Chạy **HTTP (không TLS)**.
- Một phần nội dung (biểu tổng hợp quan trắc) truy cập được **không cần mã số** qua link `?user=@tonghopdh`.

**Thiết kế chốt**:

| Hạng mục | Chốt |
|---|---|
| Lưu trữ | Bảng `external_system_credentials` (`user_id`, `system_code`, `credential` mã hóa **AES-256-GCM**, `updated_at`) — 1 bản ghi/người dùng/hệ thống ngoài |
| Ai nhập | **Chính người dùng tự nhập mã số của mình** trong trang "Tài khoản cá nhân" (khuyến nghị) — Admin không nhập hộ, không xem được |
| Hiển thị | Sau khi lưu chỉ hiện dạng mask `nan****826`; có nút "Cập nhật"/"Xóa liên kết". **Không endpoint nào trả credential ra ngoài** |
| Auto-login | Nút/menu "Hệ thống văn bản điều hành" → BE sinh **form HTML tự submit** (POST `textlogin` + `__VIEWSTATE` lấy tại thời điểm bấm) mở tab mới sang hệ thống nguồn. Credential **giải mã tại BE ngay thời điểm bấm**, không trả về FE dưới dạng đọc được, không ghi log |
| Cấu hình | URL hệ thống nguồn, tên field, có bật tích hợp hay không → **đọc từ env/`settings`**, không hardcode |
| Không làm | ❌ Bỏ bảng cache `external_documents`; ❌ bỏ job đồng bộ định kỳ; ❌ không hiển thị danh sách văn bản trên cổng công khai |

**⚠ Rủi ro phải ghi nhận với Công ty** (đã chấp nhận về mặt nghiệp vụ, cần biết để giảm thiểu):
1. Credential buộc phải **mã hóa 2 chiều** (giải mã được) — không hash được như mật khẩu người dùng nội bộ. Lộ key = lộ toàn bộ mã số. → Key AES nằm **ngoài DB** (env/Vault), tách khỏi bản backup DB, có quy trình xoay key.
2. Hệ thống nguồn chạy **HTTP** → mã số truyền plaintext trên đường truyền. → Đề nghị Công ty bật HTTPS; ghi rõ trong biên bản bàn giao là rủi ro tồn dư.
3. Mã số bị đổi/thu hồi phía nguồn → auto-login fail. → Bắt lỗi, hiện thông báo "Mã số không còn hiệu lực, vui lòng cập nhật", **không** hiển thị lỗi kỹ thuật.
4. **Phương án an toàn hơn nên chào Công ty**: đề nghị bên quản trị hệ thống nguồn cấp **link đăng nhập kèm token dùng-một-lần** hoặc bật SSO — nếu được thì bỏ hẳn việc lưu credential. Xem câu hỏi **G5**.

**Mọi thao tác** lưu/cập nhật/xóa credential + mỗi lần bấm auto-login → ghi **security event** (ai, khi nào, IP) trong audit log.

### CN-01.8. Tìm kiếm & Tra cứu nội dung (Trung bình) — *SRS M1.9*
- Tìm kiếm bài viết, văn bản, công trình theo từ khóa, danh mục, thời gian đăng.
- Full-text `unaccent` tiếng Việt (có/không dấu); highlight từ khóa; phân trang; lọc theo phạm vi (bài viết / văn bản / công trình).

### CN-01.9. Responsive Web Design — *SRS M1.10*
- Giao diện cổng hiển thị tối ưu desktop/tablet/mobile (xem NFR §7).

---

## 2. MOD-02 — QUẢN LÝ & VẬN HÀNH CÔNG TRÌNH THỦY LỢI (GIS)

Người dùng: Cán bộ kỹ thuật, Quản lý công trình, Ban giám đốc/Trực ban, Admin. *(Lịch sử sửa chữa CN-02.2: Admin + người được phân quyền.)*
Công nghệ đặc thù: GIS (GeoJSON/KMZ), thống kê, dashboard màn hình lớn. Dữ liệu thủy văn hiển thị lên bản đồ do **MOD-03** cung cấp (layer điểm đo).

### CN-02.1. Quản lý Danh mục Công trình (Cao) — *SRS M2.1, M2.2, M2.5, M2.6, UC2.1*

**Phân cấp & cấp quản lý**: Công ty → Xí nghiệp → Cụm công trình → Công trình đơn lẻ. Mỗi công trình gán **cấp quản lý** (Công ty/Xí nghiệp/Cụm) + **đơn vị phụ trách chính** (FK `org_units`). 1 công trình chỉ thuộc 1 cấp quản lý + 1 đơn vị phụ trách chính (SRS quy tắc §3.2.3).

**Loại công trình**: Trạm bơm / Cống / Kênh mương / **Đê điều** / Khác *(Đê điều bổ sung theo SRS)*.

**Hồ sơ Trạm bơm** — trường chính:
- Định danh: Mã CT (unique toàn hệ thống, VD `TB-SN-001`), Tên, Loại (Tưới/Tiêu/Hỗn hợp), Xí nghiệp (FK, bắt buộc), Cụm (FK), Địa chỉ, Tọa độ Lat/Lng Decimal(9,6), Năm XD, Năm sử dụng, Đơn vị thiết kế/thi công, Tổng vốn (triệu VND).
- Thông số kỹ thuật: Công suất tổng (kW), Số máy bơm + dự phòng, Lưu lượng thiết kế/máy (m³/s), Cột nước (m), **Lưu lượng tổng = SL máy × LL/máy (auto)**, Nguồn điện, Điện áp (kV), **Ngưỡng MN vận hành min/max**.
> ❌ **Bỏ theo B5 (12/8/2026)**: trường "Diện tích tưới tiêu (ha)" và mọi thống kê/công thức dựa trên diện tích tưới tiêu.

**Hồ sơ Cống điều tiết** — trường chính: Mã (`CG-SN-001`), Tên, Loại (Hộp/Tròn/Van phẳng/Clape), XN, Tọa độ, Số khoang, Khẩu độ/khoang (m), Cao trình ngưỡng/đỉnh, Lưu lượng thiết kế (m³/s), Thiết bị đóng mở (Thủ công/Điện/Thủy lực), **Ngưỡng MN thượng lưu cảnh báo/nguy hiểm**.

**Đê điều / Kênh mương** — hồ sơ tối thiểu (mã, tên, cấp, đơn vị, tọa độ/tuyến, thông số đặc thù dạng văn bản/số); chi tiết chốt ở thiết kế DB *(xem `business-open-questions.md` A3)*.

**Liên kết lưu vực / khu tưới tiêu (SRS M2.6)** — *chốt theo F3 (12/8/2026)*: **chỉ là trường tham chiếu dạng văn bản** trên hồ sơ công trình (VD "Lưu vực sông Nhuệ — khu tưới Hà Đông"), **không** dựng CRUD danh mục lưu vực, **không** bảng `irrigation_zones`, **không** ranh giới GIS riêng. Nếu sau này cần thống kê theo lưu vực → nâng cấp thành danh mục ở phiên bản sau.

**Tuyến sông & lý trình**: công trình/điểm đo nằm trên tuyến sông (Nhuệ, Đáy, Hồng, La Khê, Vân Đình, Duy Tiên...) và được định vị bằng **lý trình `K<km>+<m>`** (VD `K0+390`, `K18+100`) — đây là cách hệ thống nguồn của Công ty đang định danh vị trí. Bổ sung 2 trường `river_name`, `chainage` vào hồ sơ công trình + điểm đo để đối chiếu dữ liệu.

**Trạng thái vận hành công trình** (SRS §3.2.4): **Bình thường (xanh) / Cảnh báo (vàng) / Sự cố (đỏ) / Bảo trì (vàng)** + (nội bộ mở rộng: Ngừng mùa vụ xám / Đã thanh lý đen). Quyết định màu marker GIS; đồng bộ realtime với hồ sơ.
**Nguồn quyết định trạng thái** (thứ tự ưu tiên, tính ở BE — *chốt G1 + G4*): (1) có bản ghi **Khắc phục sự cố** đang mở (CN-02.2) → **Sự cố**; (2) có bản ghi **Sửa chữa/Bảo trì** đang thực hiện → **Bảo trì**; (3) có cảnh báo ngưỡng đang xảy ra tại điểm đo liên kết (CN-03.6) → **Cảnh báo**; (4) ánh xạ từ **mã tình hình vận hành** hiện hành (CN-02.11) nếu mã đó có cấu hình ánh xạ; (5) mặc định **Bình thường**. Không cho sửa trực tiếp cột trạng thái.

**Tình hình vận hành hiện hành** (MT / ĐK / ĐTTL / ĐTHL…): thông tin **độc lập** với trạng thái ở trên, nhập tay — xem **CN-02.11**.

**Quy tắc**: Mã công trình duy nhất toàn hệ thống; công trình chỉ lên bản đồ GIS khi đã số hóa tọa độ hợp lệ.

### CN-02.2. Lịch sử Sửa chữa / Bảo trì / Khắc phục sự cố (Cao) — *SRS M2.3, UC2.2* — ⭐ **THAY THẾ Nhật ký vận hành (B1/F1) + THAY THẾ Phiếu sự cố (chốt G1 = PA A)**

Đây là **chức năng ghi nhận hoạt động duy nhất** của MOD-02 sau khi loại bỏ Nhật ký vận hành và Phiếu sự cố riêng. Một bảng `maintenance_logs` phục vụ cả 2 nghiệp vụ, phân biệt bằng **Loại công việc**.

**Ai nhập**: Admin **hoặc người được phân quyền** (`ops:maintenance:create` — gán cho Kỹ thuật / Quản lý XN / Cán bộ vận hành theo nhu cầu). Không giới hạn theo ca/ngày như nhật ký, không có hạn "nhập bù 3 ngày".

**Trường dữ liệu**:

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| Mã bản ghi | Text | auto | `BT-<năm>-xxxx` |
| Công trình | FK | ✔ | Scope theo đơn vị của người nhập |
| Loại công việc | Enum | ✔ | Sửa chữa / Bảo trì định kỳ / Nâng cấp / Thay thế thiết bị / **Khắc phục sự cố** |
| **Mức độ** | Enum | ✔ khi loại = Khắc phục sự cố | Nghiêm trọng / Cao / Trung bình / Thấp — *(chốt G1 PA A)* |
| **Trạng thái xử lý** | Enum | ✔ | **Mới → Đang xử lý → Đã xử lý** (mặc định "Đã xử lý" với công việc nhập sau khi hoàn thành) |
| Ngày bắt đầu – Ngày hoàn thành | Date | ✔ / ✘ | Ngày hoàn thành ≥ ngày bắt đầu; bắt buộc khi trạng thái = Đã xử lý |
| Nội dung công việc | Text | ✔ | Với sự cố: mô tả hiện tượng + nguyên nhân + biện pháp |
| Hạng mục / thiết bị | Text | ✘ | VD "Tổ máy số 3", "Cánh van khoang 2" |
| Đơn vị thực hiện | Text / FK | ✔ | Nội bộ (org_unit) hoặc nhà thầu ngoài (text) |
| Chi phí | NUMERIC | ✘ | BigDecimal, đơn vị VND — **cấm float** |
| Nguồn vốn | Text | ✘ | |
| Kết quả / nghiệm thu | Text + Enum | ✘ | Đạt / Chưa đạt / Đang theo dõi |
| Người phụ trách | FK User | ✔ | Auto = người nhập, cho đổi |
| **Cảnh báo liên quan** | FK `alert_events` | ✘ | Khi bản ghi được tạo từ một cảnh báo ngưỡng thủy văn (CN-03.6) |
| Tài liệu, ảnh kèm | File | ✘ | Attachment service; biên bản nghiệm thu, ảnh trước/sau |

**Ghi nhận sự cố — chốt G1 (PA A, 12/8/2026)**: **không** làm phiếu sự cố riêng, **không** mã `SC-yyyy-xxxx`, **không** workflow 7 trạng thái, **không** phân công nhiều người / hạn xử lý / tab nhật ký xử lý. Thay vào đó:
- Sự cố = 1 bản ghi `maintenance_logs` với `loại = Khắc phục sự cố` + `mức độ` + `trạng thái xử lý`.
- **Liên kết trạng thái công trình**: tồn tại ≥1 bản ghi sự cố ở trạng thái Mới/Đang xử lý → công trình mang trạng thái **Sự cố (đỏ)**; đóng bản ghi cuối cùng → tự trả về trạng thái trước đó. Đây là **nguồn duy nhất** làm cờ đỏ có hồ sơ truy vết.
- **Từ cảnh báo sang xử lý**: cảnh báo ngưỡng (CN-03.6) **không tự sinh** bản ghi; trên màn hình cảnh báo có nút **"Tạo bản ghi khắc phục"** → mở form đã điền sẵn công trình + thời điểm + `alert_event_id`. Người dùng quyết định, tránh rác dữ liệu.
- Đổi `trạng thái xử lý` chỉ qua Workflow engine (Core), ghi audit + thông báo người phụ trách.

**Hiển thị**: timeline trên trang chi tiết công trình (mới nhất trước), lọc theo loại công việc / mức độ / trạng thái xử lý / khoảng thời gian / đơn vị thực hiện; tổng chi phí theo công trình/kỳ; danh sách riêng "Sự cố chưa xử lý" cho dashboard.
**Phục vụ**: lập kế hoạch bảo trì kỳ tiếp theo; nguồn dữ liệu cho BC-09 và BC-06 (xem CN-02.10).
**Quy tắc**: sửa/xóa bản ghi đã lưu → ghi audit old/new; soft delete.

### CN-02.3. Hình ảnh & Tài liệu Công trình (Trung bình) — *SRS M2.4, UC2.2*

**Tài liệu đính kèm** (tab riêng mỗi công trình): Quy trình vận hành, Phương án bảo vệ, Hồ sơ hoàn công (PDF/DWG), Biên bản kiểm tra, Hợp đồng bảo trì, Ảnh hiện trạng, Bản vẽ kỹ thuật, Tài liệu pháp lý.
- Multi-upload PDF/DOC/DOCX/DWG/JPG/PNG; 50MB/file, 500MB/công trình; gán nhãn loại + ngày lập + ngày hết hiệu lực; versioning tự động; phân quyền xem/tải theo XN.

### CN-02.4. Bản đồ GIS Công trình (Trung bình) — *SRS M2.7–M2.13, UC2.3*

- **Base map**: OSM mặc định (Vệ tinh/Địa hình/Hành chính); Google Maps optional; zoom/pan/fullscreen/reset; geocoding search; lưu vị trí + zoom mặc định theo user.
- **Số hóa tọa độ (M2.8)**: nhập tọa độ thủ công hoặc **chọn điểm trực tiếp trên bản đồ**; công trình chưa số hóa → không hiển thị + liệt kê ở danh sách "Công trình chưa có vị trí GIS".
- **Marker công trình**: màu theo trạng thái (xanh/vàng/đỏ/xám); icon theo loại (bơm/cống/kênh/đê); zoom ≥ 14 hiện tên. **Tooltip/Popup (M2.10)**: tên, mã, loại, XN, trạng thái, dữ liệu thủy văn mới nhất (từ MOD-03); nút 'Xem chi tiết' + 'Xem biểu đồ'.
- **Layer management (M2.9)** (Admin/Kỹ thuật): upload GeoJSON/KMZ ≤20MB, auto parse + validate + báo lỗi chi tiết; thuộc tính: tên, màu, opacity 0–100%, geometry type; kéo thả z-index. Layer điển hình: công trình, kênh mương (LineString), ranh giới quản lý/lưu vực (Polygon), **điểm đo thủy văn (từ MOD-03, M3.17)**, vùng cảnh báo lũ, quy hoạch (KMZ). Panel toggle từng layer, slider opacity; nhớ trạng thái theo session.
- **Cảnh báo trực quan (M2.11)**: biểu tượng/màu theo tình trạng công trình (bình thường/cảnh báo/sự cố/bảo trì).
- **Công cụ đo (M2.12)** — *mới theo SRS*: đo khoảng cách/diện tích trực tiếp trên bản đồ.
- **Xuất bản đồ/danh sách (M2.13)** — *mới theo SRS*: xuất ảnh bản đồ hoặc danh sách công trình theo khu vực ra tệp (ảnh/PDF/Excel).

### CN-02.5. Dashboard Vận hành / Điều hành (Trung bình) — *SRS M2.14, M2.15, UC2.5*

- **KPI cards** *(cập nhật 12/8/2026 — bỏ chỉ tiêu dựa trên nhật ký vận hành)*: Tổng công trình đang hoạt động / tổng số; Số công trình theo trạng thái (Bình thường / Cảnh báo / Sự cố / Bảo trì); Cảnh báo thủy văn đang xảy ra (MOD-03); Điểm đo mất tín hiệu (xám); Công việc sửa chữa/bảo trì đang thực hiện (CN-02.2); **Sự cố chưa xử lý** (CN-02.2, loại = Khắc phục sự cố, trạng thái Mới/Đang xử lý).
- **Bản đồ GIS tổng quan** + lớp thủy văn hiện hành (mực nước, lượng mưa theo điểm đo — MOD-03) + biểu tượng cảnh báo.
- **Chart & bảng** *(cập nhật 12/8/2026)*: đường mực nước 24h các điểm đo đang cảnh báo (MOD-03); bảng mực nước TL/HL hiện hành theo tuyến sông kèm **tình hình vận hành cống** (mô phỏng "biểu tổng hợp" Công ty đang dùng — xem CN-03.4 + CN-02.11); danh sách công việc bảo trì đang thực hiện; danh sách sự cố mới. *(Cột lượng mưa: xem ghi chú nguồn dữ liệu ở CN-03.2.)*
- **Tự động làm mới (M2.15)**: dữ liệu bản đồ + số liệu tự cập nhật theo chu kỳ cấu hình, không thao tác tay.
- **Chế độ màn hình lớn Phòng điều hành** (route `?mode=wall`): ưu tiên trực quan (biểu đồ/bản đồ/số liệu lớn dễ đọc từ xa) hơn bảng chi tiết; auto-rotate; dark theme. Khi cảnh báo ngưỡng (M3.14) hoặc CT chuyển sự cố → hiển thị nổi bật. Mất kết nối → "Dữ liệu chưa cập nhật" + thời điểm gần nhất.
- ✅ **Thiết bị hiển thị đã chốt (B8, 12/8/2026)**: **TV 85 inch, độ phân giải 4K (3840×2160)**; có thể kèm **máy chiếu 2K hoặc Full HD+**. → Thiết kế wall mode ở base 4K, **kiểm thử fallback 1920×1080/2560×1440** (không vỡ layout, không cắt số liệu); font size tối thiểu đọc được ở khoảng cách 4–6 m; không phụ thuộc thao tác chuột/bàn phím.

### CN-02.6. Thống kê & Tìm kiếm Công trình (Trung bình) — *SRS M2.16, M2.17, UC2.4*
- Thống kê số lượng công trình theo loại/khu vực/tình trạng/cấp quản lý; biểu đồ + bảng; xuất Excel/PDF.
- Tìm kiếm & lọc công trình theo tên, mã, khu vực, loại, tình trạng — trên danh sách và trên bản đồ.

### CN-02.7. Nhật ký Thay đổi Hồ sơ Công trình (Cao) — *SRS M2.18*
- Ghi lịch sử chỉnh sửa hồ sơ công trình (người sửa, thời gian, nội dung thay đổi — old/new) phục vụ truy vết; liên kết audit log MOD-05.

### CN-02.10. Tổng hợp & Xuất Báo cáo (Trung bình) — **THU GỌN theo 12/8/2026, chốt xong G1/G2**

Danh mục BC-01..08 cũ **không còn khả thi** vì mất nguồn dữ liệu (nhật ký vận hành + kế hoạch vụ mùa). Danh mục còn lại:

| Mã | Báo cáo | Nguồn dữ liệu | Trạng thái |
|---|---|---|---|
| ❌ BC-01/02/03 | Vận hành ngày/tuần/tháng | nhật ký vận hành | **Bỏ vĩnh viễn** (mất nguồn + chốt G2 không cần) |
| ❌ BC-04 | Kết quả vụ tưới/tiêu | kế hoạch vụ mùa | **Bỏ** (A1) |
| ✅ BC-05 | Thủy văn tháng | `hydro_readings` | **Chuyển sang MOD-03** (CN-03.5) |
| ✅ BC-06 | **Cảnh báo & sự cố** | `alert_events` + `maintenance_logs` (loại = Khắc phục sự cố) | **Giữ — chốt G1 (PA A)**; không còn bảng `incidents` |
| ❌ BC-07 | Kế hoạch vs thực hiện | kế hoạch vụ mùa | **Bỏ** (A1) |
| ❌ BC-08 | Tiêu thụ điện | nhật ký vận hành | **Bỏ vĩnh viễn** (chốt G2) |
| ⭐ BC-09 | **Tổng hợp sửa chữa/bảo trì** (theo công trình/XN/kỳ, kèm chi phí) | `maintenance_logs` (CN-02.2) | **Mới — thay BC-01..03** |
| ⭐ BC-10 | **Danh mục & hiện trạng công trình** (theo loại/khu vực/trạng thái) | `constructions` | **Mới** (đã có ở CN-02.6) |

- **Tạo thủ công + Async Job Queue**: POST → HTTP 202 + job_id → queue → Worker → notify + link tải hiệu lực 24h; lịch sử 90 ngày. Xuất PDF + Excel.
- **Bảng agg**: chỉ còn tổng hợp chi phí/số lượt bảo trì theo kỳ — nhẹ hơn nhiều so với thiết kế cũ.
- **Mẫu báo cáo**: Công ty yêu cầu **phía phát triển đề xuất format trước** để Công ty xây dựng lại theo mẫu chuẩn → xem `report-templates-proposal.md`.

### CN-02.11. Tình hình Vận hành Công trình (Cao) — ⭐ **MỚI, chốt G4 (12/8/2026)**

**Bối cảnh**: biểu tổng hợp hiện hành của Công ty có dòng "Ghi chú tình hình vận hành" với các mã `MT` (Mở treo), `ĐK` (Đóng kín), `ĐTTL+1.70m` (điều tiết thượng lưu), `ĐTHL+1.70m` (điều tiết hạ lưu).
✅ **Chốt G4**: thông tin này **KHÔNG có trong API** (`getmn.aspx` chỉ trả mực nước) → **100% nhập tay** qua màn hình quản trị.

**(a) Danh mục Mã tình hình vận hành** — CRUD đầy đủ (Admin), *không hard-code 4 mã hiện tại*:

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| Mã | Text | ✔ | Duy nhất — VD `MT`, `ĐK`, `ĐTTL`, `ĐTHL` |
| Tên đầy đủ | Text | ✔ | "Mở treo", "Đóng kín", "Điều tiết thượng lưu", "Điều tiết hạ lưu" |
| Có tham số kèm | Boolean + đơn vị | ✘ | `ĐTTL`/`ĐTHL` kèm cao trình (VD `+1.70m`) → khi bật thì form nhập bắt buộc điền giá trị NUMERIC + đơn vị |
| **Màu hiển thị** | Text (hex) | ✔ | Dùng cho badge trên biểu tổng hợp, popup GIS, wall mode |
| **Trạng thái công trình ánh xạ** | Enum | ✘ | Bình thường / Cảnh báo / Sự cố / Bảo trì — để trống = **không tác động** tới trạng thái công trình |
| Thứ tự hiển thị, Hiện/Ẩn | Int / Boolean | ✔ | |

- **Seed 4 mã hiện có** khi khởi tạo hệ thống; Admin thêm/sửa/ẩn về sau không cần sửa code.
- **Không xóa cứng** mã đã dùng trong lịch sử → chỉ Ẩn (soft delete + audit).
- Ánh xạ trạng thái áp dụng theo thứ tự ưu tiên ở CN-02.1 (ưu tiên **thấp hơn** sự cố / bảo trì / cảnh báo ngưỡng).

**(b) Cập nhật tình hình vận hành theo công trình**:
- Bảng `construction_operation_status` **append (lưu lịch sử, không ghi đè)**: Công trình (FK), Mã tình hình (FK), Giá trị tham số (NUMERIC, nullable), Thời điểm hiệu lực (`timestamptz`, mặc định = lúc nhập), Người cập nhật, Ghi chú.
- "Tình hình hiện hành" = bản ghi mới nhất theo `thời điểm hiệu lực` của công trình đó.
- **Ai nhập**: Admin + người được phân quyền `ops:opstatus:update` (Kỹ thuật / Quản lý XN / Cán bộ vận hành theo XN mình). **Tần suất**: cập nhật **khi có thay đổi** (không ép nhập theo ca/ngày); màn hình danh sách hiển thị cột "Cập nhật lần cuối" + cảnh báo mềm khi quá N ngày chưa cập nhật (N là tham số cấu hình).
- **Nhập nhanh hàng loạt**: 1 màn hình dạng bảng liệt kê toàn bộ cống/trạm bơm, chọn mã + nhập giá trị + lưu 1 lần — phục vụ trực ban cập nhật đầu ca.
- **Hiển thị**: cột "Tình hình vận hành" trên biểu tổng hợp theo tuyến sông (CN-03.4), popup GIS (CN-02.4), dashboard + wall mode (CN-02.5) — dạng badge màu theo cấu hình.
- Mọi thay đổi ghi audit old/new.

---

### ❌ PHẦN ĐÃ LOẠI KHỎI SCOPE MOD-02 — giữ lại để tránh code nhầm

> Các mục dưới đây đến từ tài liệu gốc "Đặc tả hệ thống Website", **không** có trong SRS v1.0 và **đã được Công ty chốt loại bỏ**. Giữ nguyên phần "hệ quả kỹ thuật" để không ai implement lại theo bản cũ.

### CN-02.8. ❌ Nhật ký Vận hành — **ĐÃ LOẠI KHỎI SCOPE (12/8/2026)**

Chốt theo B1 + F1: **không xây dựng** chức năng nhật ký vận hành (form theo ca, tổ máy, duyệt nhật ký). Thay thế bằng **CN-02.2 Lịch sử sửa chữa** do Admin/người được phân quyền nhập.

**Hệ quả kỹ thuật đã áp dụng**:
- Bỏ bảng `operation_logs`, `machine_run_records`; bỏ workflow duyệt nhật ký; bỏ quy tắc "nhập bù 3 ngày", "ca Ngày/Đêm", "120% lưu lượng thiết kế".
- Bỏ error code `OPS-2001`, `OPS-2003` khỏi catalog (`conventions.md` §2.3).
- Bỏ mọi chỉ tiêu tổng hợp dựa trên nhật ký: Σ giờ chạy máy, Σ lưu lượng bơm (m³), Σ kWh, số ca có sự cố.
- ✅ **Chốt G2 (12/8/2026)**: Công ty **KHÔNG cần** các chỉ tiêu giờ chạy máy / điện năng / m³ bơm trên hệ thống mới → **đóng vĩnh viễn**, **không** mở màn hình "Số liệu vận hành theo tháng" như phương án dự phòng đã đề xuất. BC-01/02/03/08 bỏ hẳn.

### CN-02.9. ❌ Phiếu Sự cố Công trình riêng — **ĐÃ LOẠI KHỎI SCOPE (chốt G1 = PA A, 12/8/2026)**

**Không** xây dựng phiếu sự cố như một thực thể riêng. Sự cố được ghi nhận bằng **CN-02.2** với `loại công việc = Khắc phục sự cố`.

**Hệ quả kỹ thuật**:
- **Không** có bảng `incidents`, **không** có mã `SC-yyyy-xxxx`, **không** có vòng đời 7 trạng thái (Chờ vật tư / Khắc phục tạm / Chờ nghiệm thu / Hủy), **không** có phân công multi-user, hạn xử lý, tab nhật ký xử lý riêng.
- Chỉ còn 3 trạng thái xử lý: **Mới → Đang xử lý → Đã xử lý**.
- Cảnh báo ngưỡng thủy văn **không auto sinh** bản ghi — chỉ có nút "Tạo bản ghi khắc phục" thủ công (xem CN-02.2).
- BC-06 lấy nguồn từ `alert_events` + `maintenance_logs`, không phải `incidents`.

---

## 3. MOD-03 — QUẢN LÝ DỮ LIỆU THỦY VĂN

> **Module mới tách riêng theo SRS §3.3.** Trước đây gộp trong MOD-02. Lõi kỹ thuật: kết nối API bên thứ 3, chuẩn hóa, time-series, biểu đồ, báo cáo, cảnh báo ngưỡng.
>
> 🟨 **Điểm chưa chốt còn ảnh hưởng module này**: **G3-a** lượng mưa (chưa có nguồn → giữ loại chỉ số + chừa chỗ cắm adapter, cột hiển thị `-`) · **G8** tuyến sông/lý trình/tọa độ 19 điểm + xác nhận 3 cặp mã trùng · **G9-a** bộ mức ngưỡng. **Không mục nào chặn việc code** pipeline/parser/polling/lưu trữ. Bảng truy vết đầy đủ: `business-open-questions.md` Phần III.

Người dùng: Hệ thống tự động (Job/Scheduler), Admin, Cán bộ kỹ thuật, Nội bộ/Quản lý, Ban giám đốc, Trực ban điều hành.

### CN-03.1. Quản lý Danh mục Điểm đo & Loại chỉ số (Cao) — *SRS M3.1, M3.2, UC3.1* — **đóng gap A2**

**Danh mục Loại chỉ số đo (M3.2)** (Admin): định nghĩa các chỉ số quan trắc (Mực nước, Lượng mưa, Lưu lượng, Nhiệt độ nước, Độ đục...) + **đơn vị đo** chuẩn.

**Danh mục Điểm đo / Trạm đo (M3.1)** — trường chính (SRS §3.3.4):

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| Mã điểm đo | Text | ✔ | Duy nhất nội bộ |
| Tên / Vị trí điểm đo | Text | ✔ | |
| Loại chỉ số | Multi-select | ✔ | Mực nước / Lượng mưa / Lưu lượng / Khác |
| **Mã ánh xạ API bên thứ 3** | Text | ✔ | 1 điểm đo ↔ **đúng 1** mã API (quy tắc §3.3.3) |
| Tọa độ điểm đo | Decimal(9,6) | ✘ | Phục vụ hiển thị GIS (M3.17) |
| Đơn vị quản lý phụ trách | FK org_unit | ✔ | |
| Nguồn dữ liệu | FK api_source | ✔ | |
| Ngưỡng cảnh báo (thấp/cao) | Numeric | ✘ | Theo điểm đo × loại chỉ số (CN-03.6) |
| Trạng thái | Enum | ✔ | Hoạt động / Offline / Ngừng |
| **Tuyến sông** | Text/FK | ✘ | Nhuệ / Đáy / Hồng / La Khê / Vân Đình / Duy Tiên… *(mới 12/8/2026)* |
| **Lý trình** | Text | ✘ | Dạng `K<km>+<m>` — VD `K0+390`, `K18+100` *(mới 12/8/2026)* |
| **Vị trí tương đối** | Enum | ✔ | `THUONG_LUU` / `HA_LUU` / `BE_HUT` / **`MN_SONG`** / `MUA` — khớp cách trình bày của hệ thống nguồn. ⭐ **`MN_SONG` (mực nước sông) bổ sung theo bảng ánh xạ G8b** — 4/19 điểm đo thực tế mang vai trò này |
| **Giá trị nội suy** | Boolean | ✘ | Nguồn đánh dấu một số điểm là "giá trị nội suy" (không đo trực tiếp) → phải giữ cờ này, không trộn lẫn với số đo thật |

- ✅ **Quan hệ Điểm đo ↔ Công trình — CONFIRMED (A2b, 12/8/2026)**: dùng bảng `station_constructions` **n–n có vai trò** (`role` = THUONG_LUU / HA_LUU / BE_HUT / MN_SONG / MUA).
  - Thực tế đối chiếu hệ thống nguồn của Công ty: một số cống/trạm bơm được theo dõi theo **cặp TL/HL** (VD Cống Liên Mạc: TL 4.47 m, HL 2.94 m) → công trình đó có **2 điểm đo mực nước** + tuỳ chọn 1 điểm mưa.
  - **Quan hệ 2 trường vai trò** (tránh lệch dữ liệu): `stations.position_role` là vai trò **hiển thị/chính thức**, bắt buộc, dùng cho biểu tổng hợp và nhãn trên GIS. `station_constructions.role` chỉ dùng khi điểm đo **có liên kết công trình**, và tồn tại vì 1 điểm đo có thể là HL của công trình này đồng thời là TL của công trình kế tiếp. **Ràng buộc**: nếu có bản ghi liên kết thì `role` của bản ghi *chính* (`is_primary = true`) phải trùng `stations.position_role`; validate ở service, test bắt buộc.
  - Điểm đo vai trò **`MN_SONG` có thể không liên kết công trình nào** (trạm thủy văn tham chiếu như TV Hà Nội, TV Ba Thá) — hợp lệ, không được coi là dữ liệu thiếu. Cảnh báo của những điểm này chỉ gửi nhóm "Ban điều hành" (xem CN-03.6/G11).
  - Ngưỡng cảnh báo gắn theo **điểm đo × loại chỉ số** (SRS); trạng thái công trình suy ra từ cảnh báo của các điểm đo liên kết theo vai trò.

#### ⭐ Bảng ánh xạ mã API ↔ điểm đo — **CHỐT G8b (12/8/2026)**

Công ty cung cấp đầy đủ **19/19 mã**; đã đối chiếu: không mã nào thừa, không mã nào thiếu so với response thật. Đây là **seed data bắt buộc** của MOD-03 — không được sinh điểm đo bằng suy đoán từ giá trị đo.

| Mã API | Điểm đo / Công trình | Vai trò | Mã nội bộ đề xuất | Giá trị mẫu 21:50 12/8 |
|---|---|---|---|---|
| F01771 | Cống Liên Mạc | Thượng lưu | `DO-LMAC-TL` | 4.47 m |
| F01672 | Cống Liên Mạc | Hạ lưu | `DO-LMAC-HL` | 2.94 m |
| F01965 | Liên Mạc 2 | Hạ lưu | `DO-LMAC2-HL` | 2.94 m |
| F01794 | Hà Đông | Thượng lưu | `DO-HDONG-TL` | 2.49 m |
| F01905 | Đồng Quan | Thượng lưu | `DO-DQUAN-TL` | 1.81 m |
| F01527 | Đồng Quan | Hạ lưu | `DO-DQUAN-HL` | 1.79 m |
| F02031 | Nhật Tựu | Thượng lưu | `DO-NTUU-TL` | 1.90 m |
| F02030 | Nhật Tựu | Hạ lưu | `DO-NTUU-HL` | 1.90 m |
| F01519 | Lương Cổ | Thượng lưu | `DO-LCO-TL` | 1.89 m |
| F01657 | Vân Đình | Thượng lưu | `DO-VDINH-TL` | 1.82 m |
| F01705 | Vân Đình | Hạ lưu | `DO-VDINH-HL` | 2.18 m |
| F02039 | Hòa Mỹ | Hạ lưu | `DO-HMY-HL` | 1.80 m |
| F01820 | Cống tiêu tự chảy Yên Nghĩa | Thượng lưu | `DO-CTTC-YNGHIA-TL` | 2.03 m |
| F01652 | Cống tiêu tự chảy Yên Nghĩa | Hạ lưu | `DO-CTTC-YNGHIA-HL` | 3.51 m |
| F01707 | TB Yên Nghĩa | Bể hút | `DO-TB-YNGHIA-BH` | 2.03 m |
| F01732 | TB Hồng Vân | MN sông | `DO-TB-HVAN-MN` | 3.75 m |
| F01559 | TV Hà Nội | MN sông | `DO-TV-HNOI-MN` | 4.36 m |
| F01812 | An Cảnh | MN sông | `DO-ANCANH-MN` | 3.42 m |
| F01532 | TV Ba Thá | MN sông | `DO-TV-BATHA-MN` | 2.56 m |

*Toàn bộ 19 điểm: loại chỉ số = **Mực nước**, đơn vị nguồn **cm** → chuẩn hóa **m scale 3**. Mã nội bộ là đề xuất, Công ty có thể đổi; **mã API là bất biến**, không được sửa tay sau khi seed.*

⚠ **4 lưu ý bắt buộc rút ra từ bảng này** — bỏ qua là sinh bug số liệu:

1. **CẤM đặt validate "TL phải cao hơn HL"**. Số liệu thật có **2/5 cặp bị đảo**: Vân Đình (TL 1.82 < HL 2.18, chênh −0.36 m) và Cống tiêu tự chảy Yên Nghĩa (TL 2.03 < HL 3.51, chênh −1.48 m — đúng bản chất cống *tiêu tự chảy* khi sông ngoài đang cao, cống phải đóng). Đây là trạng thái vận hành hợp lệ, không phải lỗi dữ liệu.
2. **Hai công trình khác nhau cùng tên "Yên Nghĩa"** (`TB Yên Nghĩa` và `Cống tiêu tự chảy Yên Nghĩa`), và cụm Liên Mạc có `Cống Liên Mạc` + `Liên Mạc 2`. → Seed và mọi join **phải dùng mã, cấm dùng tên**; UI hiển thị tên phải kèm mã hoặc lý trình để trực ban không nhầm.
3. **9/19 điểm đo không thành cặp TL–HL** (Lương Cổ, Hòa Mỹ, Hà Đông, Liên Mạc 2 chỉ có 1 vế; 4 điểm MN sông; 1 bể hút). → Biểu tổng hợp và báo cáo **phải chịu được ô trống**, không được giả định mọi công trình đều có đủ 2 vế.
4. **3 cặp mã đang trả giá trị trùng khít** tại mốc quan sát: `F02030`=`F02031` (1.90 m), `F01707`=`F01820` (2.03 m), `F01672`=`F01965` (2.94 m). Hai cặp sau là các công trình cùng cụm dùng chung vực nước nên hợp lý, nhưng **cần theo dõi vài ngày ở giai đoạn nghiệm thu dữ liệu**: nếu 2 mã **luôn** bằng nhau tuyệt đối ở mọi mốc thì nhiều khả năng là **một cảm biến được đăng ký 2 mã** (hoặc giá trị nội suy) → phải hỏi lại Công ty trước khi gắn 2 bộ ngưỡng độc lập.

### CN-03.2. Kết nối API & Đồng bộ Dữ liệu (Cao) — *SRS M3.3–M3.5, M3.15, M3.16, UC3.2, UC3.5*

**Luồng**: Trạm quan trắc (RTU/DataLogger) → Telemetry Server bên thứ 3 → **MOD-03 polling REST API 2 phút/lần vào các phút lẻ** (chốt G3) → bóc tách + chuẩn hóa đơn vị → validate → DB time-series → phân phối cho Dashboard, Widget CMS, GIS, Alert.

#### ⭐ Nguồn dữ liệu thật đã xác định (12/8/2026)

| Hạng mục | Giá trị |
|---|---|
| Hệ thống nguồn | `songnhue.bhh40.net` — hệ thống quan trắc + điều hành hiện có của Công ty (ASP.NET WebForms / IIS) |
| Endpoint mực nước | `http://songnhue.bhh40.net/api/getmn.aspx?key=<MA_SO>;` — ⚠ **bắt buộc có dấu `;` ở cuối**, thiếu là trả `not.working` |
| Xác thực | Query param `key` = **mã số** do Ban quản trị hệ thống nguồn cấp (1 chuỗi duy nhất, không có username). Lưu ở env `HYDRO_API_KEY` — **dấu `;` là một phần của giá trị env**, đừng để bị trim |
| Endpoint lượng mưa | ⬜ **KHÔNG TỒN TẠI** — đã thử `getmua`, `getlm`, `getrain`, `getluongmua`, `getmn2` đều HTTP 404. Chỉ có **duy nhất** `getmn.aspx` (mực nước). ✅ **Chốt G3: "tạm thời chưa có"** → v1 **không có nguồn lượng mưa**; mô hình dữ liệu vẫn giữ loại chỉ số "Lượng mưa" và adapter thiết kế sẵn chỗ cắm để bật khi Công ty cấp endpoint. Cột lượng mưa trên biểu tổng hợp/báo cáo hiển thị `-`. *(Cần Công ty xác nhận có nhập tay hay không — xem G3-a còn mở)* |
| Truy vấn lịch sử | ⛔ **Không có** — mọi tham số phụ (`date`, `from`/`to`, `type`, `loai`) đều **bị bỏ qua**, luôn trả snapshot hiện tại. ✅ **Chốt G3 (12/8/2026): chấp nhận — hệ thống mới tự fetch và tự ghi lịch sử**, không backfill được. Xem hệ quả kiến trúc bắt buộc ở `architecture-review.md` §8.2 |
| Biểu tổng hợp (tham chiếu nghiệp vụ) | `http://103.9.86.202/bhh40.net.songnhue/tonghop-dh/bieusov01.aspx?user=@tonghopdh&pro=homepage&menuh=indexselect01..10&tivi=yes` — 10 biểu, có sẵn chế độ `tivi=yes` cho màn hình lớn |
| **Hiện trạng** | ✅ **Đã đấu nối thử thành công 12/8/2026** — trả về 19 bản ghi mực nước |

#### Định dạng response thật (đặc tả parser)

```
F01527;12/08/2026;21:50;value=179;<br>F01519;12/08/2026;21:50;value=189;<br>…<br>
<!DOCTYPE html PUBLIC …>   ← trang ASP.NET rỗng, luôn bị nối vào cuối
```

| Đặc điểm | Chi tiết |
|---|---|
| Content-Type | `text/html` (**không phải JSON**) — payload là text thuần đứng **trước** một trang HTML rỗng |
| Phân tách bản ghi | Thẻ **`<br>`** (không phải xuống dòng) |
| Cấu trúc 1 bản ghi | `<MÃ_ĐIỂM_ĐO>;<dd/MM/yyyy>;<HH:mm>;value=<số nguyên>;` |
| Mã điểm đo | Dạng `F` + 5 chữ số (`F01527`) → chính là **"mã ánh xạ API bên thứ 3"** trong bảng `stations` |
| Đơn vị | **cm** (số nguyên) → chia 100 ra **m** |
| Thời điểm | Giờ Việt Nam, chung 1 mốc cho toàn bộ bản ghi (snapshot); mốc quan sát rơi vào bội số 10 phút |
| Không có | tên điểm đo · đơn vị đo · cờ chất lượng · trạng thái trạm · lượng mưa |
| Chuỗi lỗi | Body chứa **`not.working`** khi key sai/thiếu `;` |

**Quy tắc parse bắt buộc**:
1. **Ghi nguyên văn response vào `hydro_raw_logs` trước khi parse** (append-only) — đây là bản sao duy nhất, không lấy lại được từ nguồn.
2. Nếu body chứa `not.working` → ném `UpstreamException`, ghi `sync_logs` FAILED, **không** ghi reading nào; sau 3 lần liên tiếp → đánh dấu nguồn OFFLINE + cảnh báo Admin.
3. Cắt bỏ từ `<!DOCTYPE` trở đi → split theo `<br>` → bỏ dòng rỗng.
4. Mỗi dòng khớp regex `^([A-Z]\d+);(\d{2}/\d{2}/\d{4});(\d{2}:\d{2});value=(-?\d+(?:[.,]\d+)?);$`. Dòng không khớp → **bỏ qua + ghi log**, không làm hỏng cả mẻ.
5. Mã không có trong `stations.api_code` → **bỏ qua + cảnh báo Admin**, tuyệt đối **không tự tạo điểm đo mới**.
6. `dd/MM/yyyy HH:mm` diễn giải theo `Asia/Ho_Chi_Minh` → convert `Instant` UTC trước khi lưu.
7. Giá trị: `BigDecimal(cm).divide(100, 3, HALF_UP)` → m. **Cấm** dùng double.
8. Chống trùng: unique `(station_id, measured_at)` + `ON CONFLICT DO NOTHING` — poll 2' trên nguồn cập nhật 10' **sẽ trả trùng ở phần lớn các lần gọi**, đó là bình thường, không phải lỗi.
9. Nếu số bản ghi hợp lệ < 50% số điểm đo đang hoạt động → ghi cảnh báo "nguồn trả thiếu dữ liệu" (nguồn có thể lỗi một phần).
10. Toàn bộ 1 response xử lý trong **1 transaction** + ghi 1 dòng `sync_logs` (thời gian, số bản ghi nhận/ghi/bỏ qua).

#### ⭐ Chu kỳ polling & rate-limit — CHỐT G3 (12/8/2026)

**Nhịp cập nhật thật của nguồn** (Công ty xác nhận): nguồn làm việc theo **khung 10 phút**. Trong mỗi khung:

```
 mm:01:30 ──────────► mm:08:30        mm:08:30 ──────────► mm+10:01:30
 dữ liệu mới lên API                  máy chủ nhận dữ liệu từ máy đo (nghỉ)
```

**Lịch gọi chốt**: **2 phút/lần, vào các phút lẻ** (phút 1, 3, 5, 7, 9 của mỗi khung 10 phút → tương đương *mọi phút lẻ* trong giờ).

| Tham số | Giá trị mặc định | Ghi chú |
|---|---|---|
| Cron biểu thức | `45 1/2 * * * *` (Spring 6 trường) | Giây 45 để **vượt qua mốc `01:30`** — gọi vào giây 0 của phút 1 là gọi **trước** khi dữ liệu khung mới lên. Là **tham số cấu hình**, không hard-code |
| Chu kỳ | 2 phút | Cấu hình được (`hydro.polling.cron`) |
| Khung cập nhật nguồn | 10 phút | Dùng để tính rate-limit bên dưới |

**Rate-limit theo khung cập nhật** *(yêu cầu trực tiếp của Công ty: "hạn chế call API mà response không đổi")* — kiểm tra **trước khi mở kết nối HTTP**:
1. Tính khung hiện tại `frame = floor(now / 10 phút)` (theo giờ VN).
2. Nếu **toàn bộ điểm đo đang hoạt động** đã có bản ghi với `measured_at` thuộc `frame` → **bỏ qua lần gọi này**, không mở HTTP, ghi `sync_logs` trạng thái `SKIPPED_UP_TO_DATE` (mức DEBUG, không tính là lỗi).
3. Ngược lại → gọi API bình thường.
   - ⚠ **Không** dừng ngay sau lần đầu nhận được khung mới: nguồn cập nhật **rải từ 01:30 đến 08:30**, có thể một số trạm lên muộn. Điều kiện dừng phải là *đủ toàn bộ trạm*, không phải *có bản ghi đầu tiên*.
4. Thực tế kỳ vọng: **1–3 lần gọi thật/khung** thay vì 5 → giảm ~50% lưu lượng tới nguồn mà không mất dữ liệu.

**Trạm trục trặc / mất tín hiệu** — ✅ *chốt G3*:
- **Cách phát hiện** (nguồn không có cờ trạng thái trạm): điểm đo **không có bản ghi mới quá `N` khung** liên tiếp → chuyển `trạng thái = MẤT_TÍN_HIỆU`. `N` là tham số cấu hình, **mặc định 3 khung (≈30 phút)**.
- **Biểu thị**: marker trên **GIS màu xám**; bảng realtime badge "Mất tín hiệu" + thời điểm bản ghi cuối; biểu tổng hợp hiển thị `-`.
- **Không** tính cảnh báo ngưỡng cho điểm đo mất tín hiệu (tránh cảnh báo giả do giá trị cũ).
- Có bản ghi trở lại → tự động về `Hoạt động` + ghi log phục hồi.
- Phân biệt với **nguồn lỗi toàn phần** (`not.working` / timeout): nguồn lỗi → cảnh báo Admin về nguồn, không đánh dấu từng trạm.

**Đặc điểm dữ liệu quan sát được từ hệ thống nguồn** (căn cứ thiết kế adapter):
- Mực nước trình bày theo **cặp TL/HL** cho từng cống/trạm bơm, gắn **tuyến sông + lý trình** (`Liên Mạc (K0+390)`).
- **Đơn vị nguồn = cm** (VD `447` = 4.47 m) → adapter **bắt buộc chia 100** để chuẩn hóa về **m, scale 3** (quy ước B6). Giá trị trống thể hiện bằng `-`.
- Có khái niệm **"giá trị nội suy"** và **"bể hút"** → giữ cờ riêng, không coi là số đo trực tiếp.
- **Lượng mưa** thống kê theo **Đêm / Ngày / Tổng lượng mưa** (theo ca, không phải giá trị tức thời) → nếu sau này có nguồn, phải ghi rõ khoảng thời gian tích lũy, không lưu như reading điểm. **v1: chưa có nguồn** (chốt G3).
- Trường **tình hình vận hành cống** (`MT`, `ĐK`, `ĐTTL+1.70m`, `ĐTHL+1.70m`) — ✅ **chốt G4: KHÔNG có trong API**, nhập tay hoàn toàn qua **CN-02.11**. Adapter thủy văn **không** đụng tới dữ liệu này.
- Mốc thời gian dạng `21h20; ngày 12 tháng 8 năm 2026` → parse về `timestamptz` UTC.
- Quy mô thực tế (đo 12/8/2026): API trả **19 điểm đo mực nước**; biểu tổng hợp có thêm cột lượng mưa cho **~15 công trình** nhưng **lượng mưa không có trong API**.
- ✅ **Ánh xạ mã ↔ điểm đo: ĐÃ CÓ BẢNG CHÍNH THỨC (G8b, 12/8/2026)** — xem CN-03.1. Đối chiếu khớp 19/19 mã.
  > 📌 Bài học giữ lại: bản suy đoán trước đó từ biểu tổng hợp **sai 1/4 mã** (`F01705` đoán là Cống Phủ Lý, thực tế là **Vân Đình hạ lưu**). Tuyệt đối **không seed điểm đo bằng cách dò giá trị** — chỉ dùng bảng ánh xạ do Công ty cấp.

**Bảo mật bắt buộc**: mã số/`key` là credential → lưu **mã hóa AES-256-GCM** trong `api_sources`, đọc từ env, **không log, không hiển thị plaintext, không commit**. Endpoint là **HTTP (không TLS)** → chấp nhận rủi ro ở v1 nhưng phải ghi nhận: gọi từ server nội bộ, không gọi từ trình duyệt người dùng; đề nghị Công ty bật HTTPS phía nguồn.

**Cấu hình nguồn API (M3.3, MOD-05 M5.5)** (chỉ Admin): Tên nguồn, URL endpoint, Xác thực (API Key/Bearer/Basic/OAuth2 — **credential mã hóa AES-256-GCM, không hiển thị plaintext**), **Cron polling (mặc định `45 1/2 * * * *` = 2 phút/lần vào phút lẻ — chốt G3)**, **Khung cập nhật nguồn (mặc định 10 phút, dùng cho rate-limit)**, **Ngưỡng mất tín hiệu điểm đo (mặc định 3 khung)**, Timeout (mặc định 30s), Retry (mặc định 3, exponential backoff), Điểm đo gắn nguồn, Bật/Tắt. **Toàn bộ phải cấu hình được, không hard-code** (SRS quy tắc §3.3.3).

**Bóc tách & chuẩn hóa (M3.4)**: chuyển dữ liệu thô sang cấu trúc chuẩn (điểm đo, thời điểm đo, giá trị, đơn vị); adapter chuyển đổi đơn vị nguồn.

**Validate (M3.5)** — ⭐ **chốt lại theo F2 (12/8/2026)**: chỉ còn **2 trạng thái bản ghi: `HOP_LE` / `NGHI_NGO`** (bỏ mức "Loại bỏ").
- Kiểm tra: khoảng giá trị vật lý cho phép; chống trùng thời điểm đo trên cùng điểm đo; sai lệch bất thường so với bản ghi liền trước (delta/giờ vượt ngưỡng cấu hình).
- ⛔ **CẤM validate liên điểm đo kiểu "TL phải cao hơn HL"** — số liệu thật có cặp bị đảo hợp lệ (xem CN-03.1, lưu ý 1). Validate **chỉ xét từng điểm đo độc lập theo thời gian**, không so sánh chéo giữa 2 điểm đo.
- **Bản ghi Nghi ngờ vẫn được GHI vào bảng chính** (`hydro_readings`, cờ `quality = NGHI_NGO`) — khác thiết kế cũ.
- Đồng thời **phát thông báo cho Quản trị** → màn hình "Dữ liệu nghi ngờ" cho phép **Duyệt** (chuyển `HOP_LE`) hoặc **Xóa** (soft delete + audit ai xóa, lý do).
- Bản ghi `NGHI_NGO` **không dùng** cho cảnh báo ngưỡng và **loại khỏi** báo cáo/biểu đồ mặc định (có toggle "hiển thị cả dữ liệu nghi ngờ").
- Ngưỡng phân loại nghi ngờ là **tham số cấu hình** (MOD-05), không hard-code.

**Xử lý gián đoạn (M3.15, UC3.5)**: phân biệt 2 cấp — (a) **nguồn lỗi** (timeout / `not.working`): sau 3 lần retry thất bại liên tiếp → đánh dấu **nguồn OFFLINE** + alert Admin; (b) **trạm lỗi**: điểm đo không có bản ghi mới quá ngưỡng khung cấu hình → **MẤT_TÍN_HIỆU** (GIS xám). Gián đoạn kéo dài quá ngưỡng → nâng mức cảnh báo; kết nối phục hồi → ghi log phục hồi, tiếp tục đồng bộ.
> ⚠ **Ưu tiên vận hành**: vì nguồn **không có API lịch sử**, mọi phút poller chết là mất dữ liệu vĩnh viễn → giám sát poller xếp **ngang hàng với giám sát backup DB** (`architecture-review.md` §8.2).

**Nhật ký đồng bộ (M3.16)**: mọi lần đồng bộ (thành công/thất bại, thời gian, số bản ghi) ghi log; UI tra cứu phục vụ giám sát vận hành.

**Nhập tay**: hỗ trợ khi API gián đoạn, đánh dấu nguồn = 'Manual'.

### CN-03.3. Lưu trữ Dữ liệu Lịch sử (Cao) — *SRS M3.6, UC3.2*
- Raw data → `hydro_raw_logs` **append-only** (audit + tái xử lý). Dữ liệu chuẩn hóa → `hydro_readings` time-series, index `(station_id, timestamp)`, **partition theo tháng**.
- `hydro_latest` (1 dòng/điểm đo, poller UPSERT) — phục vụ Widget/GIS/Dashboard + graceful degradation.
- **Retention** *(chốt D5, 12/8/2026)*: mặc định **chi tiết 5 năm**, nhưng là **biến cấu hình** (`hydro.retention.detail-years`) để điều chỉnh khi triển khai; tổng hợp ngày vĩnh viễn; >2 năm chuyển Cold Storage (nén) vẫn truy vấn được.
- Mỗi bản ghi gắn 1 điểm đo + 1 timestamp duy nhất (SRS quy tắc §3.3.3).

### CN-03.4. Giám sát Realtime & Biểu đồ (Cao) — *SRS M3.7–M3.9, UC3.3*
- Bảng realtime toàn điểm đo, **auto-refresh 2'** (khớp chu kỳ polling chốt G3); màu ô Xanh/Vàng/Đỏ theo ngưỡng; **badge "Mất tín hiệu" (xám)** khi quá ngưỡng khung cấu hình (mặc định 3 khung ≈ 30'); badge "nghi ngờ" cho bản ghi `NGHI_NGO`; hiển thị thời điểm cập nhật gần nhất.
- ⭐ **Biểu tổng hợp theo tuyến sông** *(mới 12/8/2026)*: tái hiện đúng cách Công ty đang theo dõi — nhóm theo tuyến sông (Nhuệ / Đáy / Hồng / La Khê / Vân Đình / Duy Tiên), mỗi công trình 1 cột **cặp TL–HL**, kèm lý trình và **cột tình hình vận hành** lấy từ CN-02.11 (badge màu theo cấu hình mã). Đây là màn hình chính của Trực ban và là nội dung chính của wall mode. Bố cục tham chiếu: `bieusov01.aspx` của hệ thống nguồn. **Cột lượng mưa hiển thị `-`** ở v1 (chưa có nguồn — G3).
- Line chart (ECharts): **multi điểm đo so sánh (M3.9)**, chọn thông số, khoảng thời gian (24h/7d/30d/năm/custom); đường ngưỡng (nét đứt vàng/đỏ); tooltip; export PNG/SVG/CSV.
- Không có dữ liệu khoảng đã chọn → "Không có dữ liệu" (không vẽ biểu đồ trống).
- Lọc: đơn vị, điểm đo, thời gian, trạng thái.

### CN-03.5. Báo cáo Khai thác Dữ liệu Thủy văn (Cao) — *SRS M3.10–M3.12, M3.18, UC3.4*
- **Định kỳ (M3.10)**: báo cáo ngày/tuần/tháng theo mẫu cố định.
- **Theo yêu cầu (M3.11)**: người dùng tự đặt tham số (điểm đo, khoảng thời gian, loại chỉ số).
- **So sánh theo kỳ (M3.18)** *(điều chỉnh theo A1 — đã bỏ Kế hoạch vụ mùa)*: so sánh dữ liệu giữa các kỳ do người dùng tự chọn (cùng tháng nhiều năm, cùng khoảng ngày nhiều năm) — phân tích xu hướng. **Không** phụ thuộc bảng kế hoạch vụ mùa; không có chỉ tiêu "% hoàn thành kế hoạch".
- **BC-05 Báo cáo thủy văn tháng** (chuyển từ MOD-02 sang đây): mực nước max/min/trung bình theo điểm đo, tổng lượng mưa, số lần vượt ngưỡng.
- **Xuất (M3.12)**: Excel/PDF. Dùng Async Job Queue (202 + job_id) như CN-02.10. Validate tham số (ngày kết thúc ≥ bắt đầu).

### CN-03.6. Cảnh báo Ngưỡng Thủy văn (Cao) — *SRS M3.13, M3.14, UC3.5*

> 🟨 **CHỨA ĐIỂM CHƯA CHỐT — G9-a, G8.** Số mức ngưỡng chưa chốt (3 mức đề xuất *hay* báo động cấp I/II/III) → **bắt buộc thiết kế mức dạng danh mục có CRUD, cấm enum cứng**; đổi số mức khi đó chỉ là dữ liệu. Chờ thêm xác nhận **3 cặp mã trùng giá trị** (G8) — 1 hay 2 bộ ngưỡng độc lập.
- **Cấu hình theo từng điểm đo × từng loại chỉ số** (không dùng chung 1 ngưỡng toàn hệ thống — SRS quy tắc §3.3.3): mức thấp/cao; mở rộng nội bộ: 3 mức Bình thường/Warning/Critical, loại điều kiện `>`,`<`, ngoài khoảng, tốc độ thay đổi (delta/giờ); delay chống nhiễu (X phút liên tục). Ngưỡng mặc định khi tạo điểm đo mới lấy từ MOD-05 (M5.6).
- ✅ **Chốt G9 (12/8/2026)**: **Admin tự cấu hình toàn bộ ngưỡng** qua UI — Công ty **không cung cấp bảng ngưỡng thực tế trước khi triển khai**. Hệ quả:
  - Bàn giao phải có **màn hình cấu hình ngưỡng đầy đủ** (thêm/sửa/xóa theo điểm đo × chỉ số × mức, có lịch sử thay đổi + audit) — đây là hạng mục nghiệm thu, không phải seed data.
  - Hệ thống chạy được ngay với **ngưỡng mặc định** từ M5.6; điểm đo chưa cấu hình ngưỡng riêng → hiển thị nhãn **"chưa cấu hình ngưỡng"**, **không** phát cảnh báo (tránh cảnh báo sai hàng loạt).
  - Màn hình quản trị có danh sách "Điểm đo chưa cấu hình ngưỡng" để Admin hoàn thiện dần.
  - ⬜ *Còn mở*: bộ mức ngưỡng cụ thể Công ty muốn dùng — xem G9 phần còn lại ở `business-open-questions.md`.
- **Đánh giá**: ngay sau mỗi lần ghi reading; alert event unique key `(rule_id, thời điểm bắt đầu)` chống trùng; hysteresis lưu DB.
- **Kênh phát** (Notification service Core):

⭐ **Chốt theo B7 (12/8/2026)**: **v1 KHÔNG dùng SMS**. Thông báo phát **qua website (in-app)** tới **tài khoản Ban điều hành** + **người trực tiếp quản lý công trình liên quan**. SMS Gateway giữ ở dạng **adapter + cấu hình bật/tắt**, triển khai giai đoạn sau — không code cứng vào luồng alert.

✅ **Cách xác định người nhận — chốt G11 (12/8/2026)**: hợp của 2 tập, tính ở BE mỗi lần phát cảnh báo:
1. **Nhóm cố định "Ban điều hành"** — nhóm người nhận do Admin cấu hình trong MOD-05 (thêm/bớt tài khoản bất kỳ lúc nào, không cần sửa code). Ai muốn nhận thêm → Admin gán vào nhóm.
2. **Tự động**: người phụ trách của **đơn vị quản lý công trình** liên kết với điểm đo phát cảnh báo — suy ra theo chuỗi `station → station_constructions → constructions.org_unit → org_units.người đứng đầu/phó phụ trách`.
- Khử trùng lặp trước khi gửi; người đã nghỉ việc/khóa tài khoản bị loại tự động.
- Điểm đo **không liên kết công trình nào** → chỉ nhóm cố định nhận (và ghi log để Admin bổ sung liên kết).

| Kênh | Mức | Trạng thái v1 | Ghi chú |
|---|---|---|---|
| In-app (chuông + banner dashboard) | Warning + Critical | ✅ **Kênh chính** | Xác nhận đã đọc; người nhận = Ban điều hành + người quản lý công trình liên quan (suy từ `station_constructions` → đơn vị phụ trách) |
| Email | Warning + Critical | ✅ Có | Kèm link màn hình điểm đo/công trình; danh sách nhận theo điểm đo |
| Web Push trình duyệt | Warning + Critical | ⚪ Tùy chọn | Cần cấp quyền browser |
| SMS | Critical only | ⏸ **Hoãn sang phase sau** | Giữ interface `SmsSender` + cấu hình nhà cung cấp; mặc định tắt |

- **Lịch sử cảnh báo**: điểm đo, thông số, giá trị, mức, thời gian bắt đầu/kết thúc, người xác nhận, ghi chú xử lý; lọc; phân loại Đang xảy ra / Đã xử lý / False Alarm.

### CN-03.7. Hiển thị Thủy văn trên Bản đồ GIS (Trung bình) — *SRS M3.17, UC3.6*
- Lớp "Điểm đo thủy văn" trên bản đồ GIS (MOD-02): vị trí điểm đo + giá trị đo mới nhất (từ `hydro_latest`).
- **Màu marker điểm đo**: Xanh = trong ngưỡng · Vàng = cảnh báo · Đỏ = nguy hiểm · **Xám = mất tín hiệu / trạm trục trặc** *(chốt G3, 12/8/2026)* · viền nét đứt = bản ghi mới nhất đang `NGHI_NGO`.
- Popup điểm đo xám hiển thị **giá trị cuối cùng + thời điểm** kèm nhãn "Dữ liệu chưa cập nhật", không hiển thị như số liệu hiện hành.
- Click điểm đo → xem nhanh biểu đồ diễn biến gần nhất.
- Điểm đo cần đã số hóa tọa độ + có dữ liệu đồng bộ.

---

## 4. MOD-04 — QUẢN LÝ NHÂN SỰ (HRM)

> Trước đây là MOD-03. Nội dung giữ nguyên, đổi số hiệu CN-03.x → CN-04.x.

Người dùng: Quản trị nhân sự (Admin HR), Ban giám đốc, Quản lý phòng ban/XN, Nhân viên nội bộ.
**Tuân thủ**: NĐ 13/2023/NĐ-CP (bảo vệ dữ liệu cá nhân), BLLĐ 2019, Luật Lưu trữ 2011. Trường nhạy cảm (CCCD, lương, TK ngân hàng, MST, BHXH) mã hóa + phân quyền nghiêm ngặt.

### CN-04.1. Sơ đồ Tổ chức (Cao) — *SRS M4.1, UC4.1*
- Cây phân cấp ≥5 cấp: Công ty → Khối → Phòng/XN → Tổ/Đội/Cụm → Trạm.
- Đơn vị: Mã (unique, `PB-HC`, `XN-01`), Tên đầy đủ + viết tắt, Đơn vị cha (FK), Loại, Người đứng đầu (FK NV — 1 người chỉ đứng đầu 1 đơn vị) + Phó phụ trách, SĐT/Email, Chức năng nhiệm vụ, Trạng thái (Hoạt động/Giải thể/Tạm dừng), Thứ tự.
- UI: Tree view (dọc/ngang, thu gọn/mở rộng, zoom/pan, fit-to-screen; nút hiện tên + người đứng đầu + ảnh + SL nhân sự) và List view.
- Kéo thả di chuyển nhánh; **giải thể/xóa đơn vị chỉ khi không còn nhân viên/công trình liên kết** — cảnh báo chuyển dữ liệu trước, xác nhận 2 bước (SRS UC4.1).
- Export: PNG/SVG, PDF A3 ngang, Excel.
- **Dùng chung bảng `org_units`** cho cả Xí nghiệp (MOD-02) và phòng ban (MOD-04).

### CN-04.2. Hồ sơ Cán bộ Nhân viên (Cao) — *SRS M4.2, UC4.2*
- **Thông tin cá nhân**: Mã NV (unique, `NV-2019-001`, không đổi suốt quá trình công tác), Họ tên (+ bản không dấu auto), Ảnh (crop 300×300), Ngày sinh (18 ≤ tuổi ≤ 70), Giới tính, Dân tộc, Quê quán, **CCCD 9/12 số unique 🔒**, Ngày/Nơi cấp 🔒, Địa chỉ, SĐT, Email nội bộ/cá nhân, Hôn nhân, Liên hệ khẩn cấp.
- **Thông tin công tác**: Đơn vị (FK sơ đồ tổ chức), Chức vụ (FK danh mục), Chức danh, Ngày vào làm, Loại HĐLĐ, Ngày ký + hết hạn HĐ, **Lương cơ bản, hệ số, STK ngân hàng, MST, số BHXH 🔒**, Trạng thái (Đang làm/Thử việc/Thai sản/Không lương/Nghỉ việc/Nghỉ hưu), Ngày + lý do nghỉ việc.
- **Danh mục chức vụ chuẩn hóa** (Admin HR): Mã, Tên, Nhóm, Mô tả, Thứ tự.

### CN-04.3. Lý lịch & Chuyên môn (Cao) — *SRS M4.3, UC4.2*
- Học vấn cao nhất; Lịch sử đào tạo bổ sung; Bằng cấp/chứng chỉ (số hiệu, cơ quan cấp, ngày cấp, **ngày hết hiệu lực**, file); Ngoại ngữ, tin học, phần mềm chuyên dụng.

### CN-04.4. Lịch sử Công tác — Timeline (Trung bình) — *SRS M4.4, M4.5, M4.6, UC4.2, UC4.3*
- 10 loại sự kiện (Admin HR tạo): Tuyển dụng, Ký/gia hạn HĐ, Điều động, Bổ nhiệm/Miễn nhiệm, Nâng lương, Khen thưởng, Kỷ luật, Đào tạo, Nghỉ dài hạn, Nghỉ việc/hưu — mỗi loại ghi số QĐ, ngày hiệu lực, file gốc.
- UI: timeline dọc reverse-chronological, lọc theo loại/thời gian. **Không ghi đè mất dấu vết cũ** (SRS quy tắc §3.4.3).

### CN-04.5. Hợp đồng, Tài liệu & Cảnh báo hết hạn (Cao) — *SRS M4.7, M4.8, M4.9, UC4.4*
- 7 thư mục cố định: Giấy tờ tùy thân (10MB), Bằng cấp (10MB), HĐLĐ (20MB), Quyết định nhân sự (10MB), Ảnh (5MB), Hồ sơ y tế (20MB), Khác (10MB).
- Multi-upload + progress; versioning (không ghi đè); preview PDF/ảnh; tải đơn file hoặc cả hồ sơ ZIP; ngày hiệu lực/hết hạn.
- **Cảnh báo hết hạn (M4.9)**: HĐLĐ + chứng chỉ/giấy tờ sắp hết hạn theo ngưỡng cấu hình (VD 30/90 ngày): vàng < ngưỡng, đỏ đã hết hạn; HĐ hết hạn chưa gia hạn → đánh dấu "Hết hạn" + cảnh báo định kỳ. **% hoàn thiện hồ sơ** + danh sách tài liệu thiếu.

### CN-04.6. Danh bạ Nội bộ (Cao) — *SRS M4.11, UC4.6*
- Mọi nhân viên tra cứu; chỉ hiện thông tin liên hệ công vụ (không lộ dữ liệu nhạy cảm); chỉ NV 'Đang làm'.
- Grid/List; card: ảnh, tên, chức vụ, đơn vị, SĐT, email nội bộ. Full-text search (có/không dấu) debounce 300ms, highlight; lọc multi-select đơn vị/chức vụ/giới tính; chi tiết: gọi/email, vị trí sơ đồ, đồng nghiệp cùng đơn vị.

### CN-04.7. Tìm kiếm & Phân quyền Hồ sơ (Cao) — *SRS M4.12, M4.13, UC4.5*
- Tìm kiếm/lọc hồ sơ theo tên, mã NV, phòng ban, chức vụ, trình độ.
- **Phân quyền theo cấp quản lý (M4.13)**: Quản lý cấp XN/Cụm chỉ xem hồ sơ NV thuộc đơn vị mình; truy cập ngoài phạm vi → từ chối + ghi log. Trường 🔒 chỉ Admin HR + chính NV đó (nguyên tắc tối thiểu).

### CN-04.8. Thống kê & Báo cáo Nhân sự (Trung bình) — *SRS M4.14–M4.17, UC4.6*
- KPI: tổng NV đang làm, tuyển mới + nghỉ việc tháng, tỷ lệ nghỉ việc, **HĐ hết hạn 30 ngày tới**, chứng chỉ hết hiệu lực 90 ngày tới.
- Chart: NV theo phòng ban/đơn vị (M4.14), theo trình độ/độ tuổi/giới tính (M4.15, tròn), biến động tuyển mới/nghỉ/điều chuyển 12 tháng (M4.16, đường).
- Báo cáo BCNS-01→08 (trích ngang, theo đơn vị, biến động, HĐ sắp hết hạn, cơ cấu, chứng chỉ sắp hết hạn, lý lịch mẫu 2C-BNV, tổng hợp năm) — Excel/PDF (M4.17).
> 🟨 **CHỨA ĐIỂM CHƯA CHỐT — G6, G10.** **BCNS-07 (mẫu 2C-BNV/2008 Bộ Nội vụ) chưa có file mẫu gốc của Công ty** → làm 7 báo cáo còn lại trước, để BCNS-07 sau cùng. Đây là biểu mẫu quy định, **cấm tự chế layout**. Layout in ấn các báo cáo khác chờ duyệt (G10) — trường dữ liệu đã chốt nên làm khung trước được.

### CN-04.9. Quản lý Nghỉ phép (Trung bình) — *SRS M4.10*
- **Chính sách (Admin HR)** — ⭐ *chốt C1 (12/8/2026): toàn bộ thông số dưới đây là **biến cấu hình**, Admin sửa được trong UI, **cấm hard-code***: phép năm theo thâm niên (mặc định theo Điều 113 BLLĐ 2019: <5 năm=12; 5–10=13; >10=14); phép đặc biệt (thai sản 180, cưới 3, tang 3, khám SK 1); số ngày chuyển sang năm sau (mặc định 5); cách tính pro-rata (mặc định `12 × số tháng / 12`, làm tròn 0.5); mốc tính thâm niên (mặc định = ngày vào làm tại Công ty); ngày lễ (trừ tự động).
- **Quy trình**: NV đăng ký → tự tính số ngày (trừ cuối tuần + lễ) + hiển thị số dư → cảnh báo vượt phép → gửi đơn → Quản lý duyệt/từ chối (email) → tự trừ số dư.
- **Số dư**: Được hưởng = thâm niên + chuyển năm trước; Còn lại = Được hưởng − Đã nghỉ − Đang chờ duyệt (tính lại từ đơn, không cộng trừ tay).
- **Lịch đơn vị**: Calendar; cảnh báo trùng lịch khi > ngưỡng % quân số nghỉ cùng lúc.
- ✅ **Chốt C2**: mặc định duyệt **1 cấp** (trưởng đơn vị); có cấu hình "≥ N ngày cần thêm cấp duyệt 2" (mặc định tắt).
- ✅ **Chốt C3**: **cấp tài khoản cho toàn bộ CBNV**; nhân viên không dùng máy tính → quản lý đơn vị tạo đơn hộ (lưu trường "người tạo hộ", ghi audit).
- ✅ **Chốt B3**: có chức năng **ủy quyền duyệt có thời hạn** (từ–đến, người được ủy quyền cùng đơn vị hoặc cấp trên); audit ghi "duyệt theo ủy quyền của X".
- ✅ **Chốt C4**: chỉ **lưu trữ** mức lương/hệ số (mã hóa 🔒) — **không** có module tính lương, **không** chấm công.

---

## 5. MOD-05 — QUẢN TRỊ HỆ THỐNG, TÀI KHOẢN & PHÂN QUYỀN

Người dùng: Admin (Super Admin), Hệ thống tự động.

### CN-05.1. Quản lý Tài khoản (Cao) — *SRS M5.1, UC5.1*
- Tạo/sửa/khóa/mở khóa, đặt lại mật khẩu tài khoản nội bộ; sinh mật khẩu tạm hoặc email kích hoạt; liên kết tài khoản với hồ sơ nhân viên (MOD-04, `users.employee_id`).
- **Không xóa vĩnh viễn tài khoản đã có lịch sử thao tác** — chỉ khóa (đảm bảo toàn vẹn audit — SRS quy tắc §3.5.3).

### CN-05.2. Phân quyền RBAC chi tiết (Cao) — *SRS M5.2, M5.3, UC5.1*
- Định nghĩa Role → gán quyền chức năng/dữ liệu → gán Role cho tài khoản (1 tài khoản nhiều Role; quyền thực tế = hợp các Role).
- **Ma trận quyền chi tiết theo màn hình/chức năng (M5.3)**: view/thêm/sửa/xóa/xuất cho từng màn hình. Permission dạng `module:resource:action`; deny by default; scope theo đơn vị (ma trận RBAC §6).

### CN-05.3. Cấu hình Hệ thống (Cao) — *SRS M5.4–M5.6, M5.17, UC5.2*
- Thông tin Công ty (tên, logo, liên hệ hiển thị trên cổng — đồng bộ MOD-01).
- **Chu kỳ đồng bộ thủy văn (M5.5)**: chu kỳ, timeout, số lần retry cho polling API bên thứ 3 (MOD-03).
- **Ngưỡng cảnh báo mặc định (M5.6)**: áp dụng khi tạo điểm đo mới.
- Template báo cáo; tham số vận hành chung. Giá trị không hợp lệ (VD chu kỳ âm) → báo lỗi, giữ cấu hình cũ.

**⭐ Danh mục tham số cấu hình bắt buộc** (chốt 12/8/2026 — nguyên tắc: *cái gì khách nói "để config" thì phải nằm trong bảng `settings`, có UI sửa, có validate, cấm hard-code*):

| Nhóm | Tham số | Mặc định | Nguồn chốt |
|---|---|---|---|
| Bảo mật | **Khung giờ hành chính** (dùng cho cảnh báo đăng nhập bất thường M5.16) | **08:00–17:00** | F5 |
| Bảo mật | Độ phức tạp + số lần sai bị khóa | 10 ký tự / 5 lần / khóa 15' | M5.15 |
| Bảo mật | **Hạn mật khẩu** → quá hạn thì khoá tài khoản | **90 ngày** | **M5.15-a** |
| Bảo mật | **Giãn cách luồng tự đặt lại** (quên mật khẩu) | **90 ngày** | **M5.15-a** |
| Bảo mật | **Mốc nhắc đổi mật khẩu** trước ngày hết hạn | **14, 7, 1 ngày** | **M5.15-a** |
| Bảo mật | **Hạn dùng liên kết đặt lại** | **30 phút** | **M5.15-a** |
| Thủy văn | **Cron polling** | **`45 1/2 * * * *`** (2'/lần, phút lẻ) | **G3** |
| Thủy văn | **Khung cập nhật nguồn** (dùng cho rate-limit) | **10 phút** | **G3** |
| Thủy văn | **Ngưỡng mất tín hiệu điểm đo** (số khung không có dữ liệu → xám) | **3 khung (≈30')** | **G3** |
| Thủy văn | Timeout, số lần retry | 30s / 3 | M5.5 |
| Thủy văn | Ngưỡng cảnh báo mặc định khi tạo điểm đo mới | — | M5.6 |
| Thủy văn | Ngưỡng phân loại bản ghi "Nghi ngờ" (delta/giờ, khoảng vật lý) | theo loại chỉ số | F2 |
| Thủy văn | **Retention dữ liệu chi tiết** | **5 năm** | D5 |
| Vận hành | Số ngày chưa cập nhật tình hình vận hành → cảnh báo mềm (CN-02.11) | 7 ngày | **G4** |
| Nhân sự | Toàn bộ thông số phép năm (xem CN-04.9) | theo BLLĐ 2019 | C1 |
| Nhân sự | Ngưỡng cảnh báo HĐ/chứng chỉ sắp hết hạn | 30 / 90 ngày | M4.9 |
| Dung lượng | **Số điểm đo / công trình / người dùng tối đa** + giới hạn upload từng loại | không giới hạn cứng | E3 |
| Thông báo | Bật/tắt kênh SMS, email, web push | SMS **tắt** | B7 |
| Thông báo | **Nhóm người nhận cảnh báo "Ban điều hành"** (danh sách tài khoản, Admin sửa) + bật/tắt tự thêm người phụ trách công trình | nhóm rỗng + auto **bật** | **G11** |
| Audit | **Thời gian lưu nhật ký hoạt động** + bật/tắt kết xuất lưu trữ khi quá hạn | **5 năm** / bật | **G7** |
| Hệ thống | Chu kỳ auto-refresh dashboard & wall mode; thời gian auto-rotate | 5' / 30s | M2.15 |
| Tích hợp | URL + bật/tắt liên kết hệ thống văn bản điều hành | theo env | E3 |
- **Xuất/nhập cấu hình (M5.17)** — *mới theo SRS*: export bộ cấu hình ra tệp / import lại — hỗ trợ sao lưu cấu hình hoặc chuyển môi trường staging/production.

### CN-05.4. Nhật ký Hoạt động — Audit Log (Cao) — *SRS M5.7, M5.8, UC5.4*
- Ghi mọi thao tác quan trọng: đăng nhập/xuất, thêm/sửa/xóa dữ liệu, thay đổi phân quyền — user, timestamp, action, module, old/new value.
- UI tra cứu lọc theo user/module/thời gian/loại thao tác; xem chi tiết bản ghi.
- **Audit log KHÔNG được sửa/xóa bởi bất kỳ vai trò nào, kể cả Admin** (append-only + hash chain — SRS quy tắc §3.5.3; xem `conventions.md` §4.3).
- ✅ **Lưu trữ — chốt G7 (12/8/2026)**: giữ **5 năm** trong bảng nóng (đồng bộ retention thủy văn D5), là **biến cấu hình** (`audit.retention-years`). Quá hạn → **kết xuất ra file lưu trữ** (CSV/Parquet nén + **checksum SHA-256** kèm theo, đẩy lên MinIO bucket riêng có versioning) rồi mới xóa khỏi bảng nóng — **không xóa trắng**, vì audit là bằng chứng đối soát.
- Job kết xuất chạy theo lịch, ghi `sync_logs`/security event; thất bại → **không xóa** dòng nào + cảnh báo Admin. Hash chain phải nối tiếp qua ranh giới kết xuất (lưu hash cuối của lô đã kết xuất làm điểm neo).

### CN-05.5. Backup & Restore (Cao) — *SRS M5.9–M5.11, UC5.6*
- **Backup tự động theo lịch (M5.9)** — *bản tối giản, chốt 2026-08-13*: **`pg_dump -Fc` hàng đêm ~02:00** (ngoài giờ hành chính) → nén + checksum SHA-256 → retention 30 ngày; lưu **tách biệt máy chủ vận hành chính** (SRS quy tắc §3.5.3); ghi log kết quả; **thất bại hoặc quá 26 giờ không có bản mới → cảnh báo Admin ngay**.
  - **RPO ≤ 24 giờ · RTO ≤ 4 giờ** — chấp nhận mất tối đa 1 ngày dữ liệu khi DB hỏng. Lý do và bảng rủi ro chấp nhận: `architecture-review.md` §6.5.
  - ❌ **Không** WAL archiving / PITR / replica ở v1 — hệ nội bộ giờ hành chính, không phải hệ giao dịch liên tục.
  - **Key mã hóa (AES, JWT signing) lưu tách, không nằm trong bản backup.**
- **Backup theo yêu cầu (M5.10)**: Admin chủ động backup trước nâng cấp/thay đổi lớn.
- **Restore qua UI (M5.11)** — *theo SRS, đảo quyết định E1 cũ*: Admin chọn bản backup → thực hiện khôi phục → thông báo kết quả. **Bảo vệ bắt buộc** (xem `architecture-review.md` §7): xác nhận nhiều bước (gõ tên hệ thống), chỉ Super Admin + 2FA, ghi security event, chạy async có tiến độ, **maintenance mode chặn ghi trong lúc restore**, khuyến nghị restore ra môi trường staging trước. Khôi phục **từ bản dump đêm gần nhất** (không có khôi phục điểm-thời-gian — xem M5.9).
- UI hiển thị trạng thái backup gần nhất.

### CN-05.6. Giám sát Hệ thống & Thông báo (Trung bình) — *SRS M5.12, M5.13, UC5.3* — **mới theo SRS**
- **Health-check (M5.12)**: theo dõi tình trạng dịch vụ/API tích hợp (hệ thống văn bản điều hành MOD-01, API thủy văn MOD-03, SMTP, SMS, MinIO); cảnh báo khi sự cố. Tách khỏi alert nghiệp vụ.
- **Thông báo hệ thống (M5.13)**: Admin soạn/gửi thông báo chung (bảo trì, cập nhật phiên bản) tới toàn bộ hoặc nhóm người dùng.

### CN-05.7. Bảo mật Tài khoản & Phiên (Cao) — *SRS M5.14–M5.16, UC5.5* — **mới theo SRS**
- **Xác thực**: Access token 30' + Refresh rotation (httpOnly cookie), BCrypt; thu hồi qua denylist bảng DB; refresh reuse detection.
- **Quản lý phiên (M5.14)**: theo dõi phiên đang hoạt động (thời gian, thiết bị/IP), **đăng xuất từ xa** một phiên bất kỳ.
- **Chính sách mật khẩu (M5.15)**: độ phức tạp, thời hạn đổi định kỳ, khóa tài khoản sau N lần sai — **cấu hình được, không hard-code** (SRS quy tắc §3.5.3).

#### M5.15-a. Vòng đời mật khẩu & luồng quên mật khẩu — *chốt 18/8/2026*

> Trước 18/8 hệ thống **không có** đường tự đặt lại mật khẩu: quên là phải nhờ quản trị viên cấp mật khẩu tạm. Mục này đóng điểm đó.

**Bốn quy tắc.** Mọi con số đều nằm trong `settings`, có UI sửa — không hard-code.

| # | Quy tắc | Tham số | Mặc định |
|---|---|---|---|
| 1 | Mật khẩu **hết hạn** sau N ngày kể từ lần đổi gần nhất → tài khoản chuyển `DISABLED` | `security.password.max-age-days` | **90** |
| 2 | Luồng **tự đặt lại** (quên mật khẩu) chỉ dùng được nếu lần tự đặt lại gần nhất đã cách ≥ N ngày | `security.password.self-reset-cooldown-days` | **90** |
| 3 | **Nhắc trước hạn** qua email, ở các mốc trước ngày hết hạn | `security.password.reminder-days-before` | **14, 7, 1** |
| 4 | Liên kết đặt lại sống ngắn, dùng **một lần** | `security.password.reset-link-ttl-minutes` | **30** |

**Ba đường đi tới mật khẩu mới:**

| Đường | Điều kiện | Cách làm |
|---|---|---|
| **Đổi chủ động** | Biết mật khẩu hiện tại, tài khoản còn hoạt động | Nhập mật khẩu cũ + mới. **Không có giãn cách** — người nghi mật khẩu bị lộ phải đổi được ngay |
| **Tự đặt lại** (quên) | Tài khoản còn hoạt động **và** đã qua giãn cách quy tắc 2 | Nhận **liên kết một lần** qua email → tự đặt mật khẩu mới. Mật khẩu cũ **vẫn dùng được** cho tới khi đặt xong |
| **Quản trị viên cấp** | Chưa qua giãn cách, **hoặc** tài khoản đã bị khoá do hết hạn, **hoặc** người dùng không vào được email | Quản trị viên đặt **mật khẩu tạm** (gửi qua email) + mở khoá. Người dùng đăng nhập rồi **bắt buộc đổi ngay** — đúng cơ chế `must_change_password` đã có |

**⛔ Email của luồng tự phục vụ chứa liên kết, KHÔNG chứa mật khẩu.** Gửi thẳng mật khẩu mới theo một yêu cầu chưa xác thực nghĩa là **bất kỳ ai biết tên đăng nhập đều vô hiệu hoá được mật khẩu của người khác** — nạn nhân không đăng nhập được cho tới khi mở hộp thư. Mật khẩu tạm chỉ xuất hiện ở đường quản trị viên cấp, tức là đã có một con người chịu trách nhiệm và có dấu vết trong nhật ký.

**Trường dữ liệu thêm vào `users`:**

| Cột | Kiểu | Ý nghĩa |
|---|---|---|
| `password_changed_at` | `timestamptz` | Mốc tính hạn — quy tắc 1 |
| `password_expires_at` | `timestamptz` | Ngày hết hạn, dẫn xuất khi đổi mật khẩu. Lưu tường minh để truy vấn "sắp hết hạn" không phải tính trên toàn bảng |
| `last_self_reset_at` | `timestamptz` | Mốc chặn giãn cách — quy tắc 2. **Một con số đếm không diễn tả được "cách nhau 3 tháng"**, nên mốc thời gian mới là thứ chặn |
| `self_reset_count` | `INT` | Tổng số lượt tự đặt lại. Không dùng để chặn; dùng để nhìn ra người dùng hay quên và tài khoản đang bị nhắm |

Bảng riêng `password_reset_tokens`: chỉ lưu **băm SHA-256** của mã, `expires_at`, `used_at`, `requested_ip` — mã gốc chỉ tồn tại trong email.

**Việc chạy theo lịch** (đi qua hàng đợi job, không phải `@Scheduled` rời rạc): mỗi ngày quét tài khoản sắp hết hạn → gửi email nhắc theo mốc quy tắc 3; quét tài khoản đã quá hạn → chuyển `DISABLED` + ghi security event.

**Áp cho mọi tài khoản, kể cả Super Admin.** Miễn trừ Super Admin là làm rỗng chính sách. Hệ quả phải xử lý: Super Admin bị khoá thì không còn ai mở khoá được — đường thoát là **lệnh chạy trên máy chủ** (mở rộng `AdminBootstrapRunner`), không phải một ngoại lệ trong mã. Ai vào được máy chủ thì vốn đã có quyền cao hơn mật khẩu.
- **Cảnh báo đăng nhập bất thường (M5.16)**: nhiều lần sai mật khẩu liên tiếp, đăng nhập ngoài giờ hành chính → cảnh báo Admin **near real-time**, không xử lý theo lô.

---

## 6. MA TRẬN PHÂN QUYỀN RBAC (MOD-02/03 — tham chiếu chính)

> Cập nhật 12/8/2026: bỏ cột **Operator** và các dòng nhật ký vận hành; bổ sung lịch sử sửa chữa + duyệt dữ liệu nghi ngờ. **Cập nhật đợt 2**: bỏ dòng phiếu sự cố riêng (G1 = PA A → gộp vào CN-02.2); thêm dòng tình hình vận hành (G4) và nhóm người nhận cảnh báo (G11).

| Chức năng | Admin | Quản lý XN | Kỹ thuật | Cán bộ vận hành |
|---|:-:|:-:|:-:|:-:|
| Xem danh mục công trình | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Thêm/Sửa hồ sơ công trình | ✔ | ✘ | ✔ | ✘ |
| **Ghi lịch sử sửa chữa/bảo trì (CN-02.2)** | ✔ | ✔ | ✔ | ✘ *(cấp được qua quyền `ops:maintenance:create`)* |
| **Ghi nhận sự cố** (CN-02.2, loại = Khắc phục sự cố) | ✔ | ✔ | ✔ | ✔ (XN mình) |
| **Đóng bản ghi sự cố** (chuyển "Đã xử lý") | ✔ | ✔ (XN mình) | ✘ | ✘ |
| Xóa/sửa bản ghi sửa chữa đã lưu | ✔ | ✔ (XN mình) | ✘ | ✘ |
| **Cập nhật tình hình vận hành cống (CN-02.11)** | ✔ | ✔ | ✔ | ✔ (XN mình) |
| **Quản lý danh mục mã tình hình vận hành** | ✔ | ✘ | ✘ | ✘ |
| Upload tài liệu công trình | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Quản lý điểm đo (MOD-03) | ✔ | ✘ | ✔ | ✘ |
| Cấu hình API nguồn dữ liệu | ✔ | ✘ | ✘ | ✘ |
| Cấu hình ngưỡng cảnh báo (G9) | ✔ | ✔ | ✔ | ✘ |
| **Quản lý nhóm nhận cảnh báo "Ban điều hành" (G11)** | ✔ | ✘ | ✘ | ✘ |
| Đóng/Xử lý cảnh báo | ✔ | ✔ | ✔ | ✘ |
| **Duyệt/Xóa bản ghi thủy văn "Nghi ngờ" (F2)** | ✔ | ✘ | ✔ | ✘ |
| Upload/Quản lý layer GIS | ✔ | ✘ | ✔ | ✘ |
| Xem báo cáo | ✔ | ✔ (XN mình) | ✔ | ✔ (XN mình) |
| Tạo/Xuất báo cáo | ✔ | ✔ | ✔ | ✘ |

CMS (MOD-01): Biên tập viên (tạo + gửi duyệt) < Quản trị nội dung (Duyệt, Xuất bản, Gỡ) < Admin. Cán bộ văn thư: quản lý cờ công khai văn bản.
HR (MOD-04): Admin HR full; Quản lý đơn vị xem NV đơn vị mình; NV chỉ xem danh bạ + hồ sơ mình + đăng ký phép; trường 🔒 chỉ Admin HR + chính NV.
Quản trị (MOD-05): chỉ Admin/Super Admin; restore + xuất/nhập cấu hình yêu cầu 2FA.

---

## 7. YÊU CẦU PHI CHỨC NĂNG (NFR)

> ✅ **Chốt G12 (12/8/2026)**: các con số dưới đây là **con số nghiệm thu chính thức** — đưa vào biên bản. Đo trên môi trường Production sau go-live.

| # | Hạng mục | Yêu cầu | Tiêu chí đo |
|---|---|---|---|
| NFR-01 | Khả dụng | **Uptime ≥ 99%** (nội bộ mục tiêu 99.5% giờ hành chính), không tính bảo trì đã lên lịch | Alert khi downtime > 15' |
| NFR-02 | Hiệu năng web | **Trang chủ < 3s**/mạng bình thường; **≥ 200 người dùng đồng thời** không suy giảm đáng kể | P95 dashboard < 3s @ 50 users; load test 200 CCU |
| NFR-03 | Polling | Đúng cron cấu hình (2'/lần, phút lẻ — G3); đọc/lưu thủy văn realtime không làm chậm chức năng khác | Sai lệch < 10%; retry 3 lần + alert; **không bỏ sót khung 10' nào trong 7 ngày quan sát** |
| NFR-04 | Báo cáo | **Báo cáo tháng < 60s** (1 tháng/1 XN, async) | Job completed < 60s |
| NFR-05 | Bảo mật | HTTPS/TLS, RBAC tối thiểu quyền, hash mật khẩu, log thao tác nhạy cảm; **2FA bắt buộc cho Admin + Admin HR**; API credential AES-256-GCM | Không plaintext trong UI; đăng nhập Admin không có 2FA phải bị từ chối |
| NFR-06 | Phân quyền dữ liệu | Cán bộ vận hành/Quản lý XN chỉ thấy dữ liệu đơn vị mình; trường 🔒 mã hóa | Unit + integration test 100% pass |
| NFR-07 | Audit | Log mọi tạo/sửa/xóa + đăng nhập + đổi quyền; **giữ 5 năm** (G7) | user, timestamp, action, old/new value; kết xuất lưu trữ có checksum |
| NFR-08 | Lưu trữ & Backup | Hydro chi tiết 5 năm; **`pg_dump` hàng đêm, retention 30 ngày, lưu khác máy**; **RPO ≤ 24 giờ · RTO ≤ 4 giờ** | Có bản backup < 26h; diễn tập restore đo được RTO < 4h |
| NFR-09 | Tương thích | Chrome/Firefox/Edge/Safari; mobile; GIS GeoJSON/KMZ (Shapefile chốt sau) | Responsive 360px–2560px |
| NFR-10 | Pháp lý | NĐ 13/2023/NĐ-CP, BLLĐ 2019, Luật Lưu trữ 2011; quy định công bố thông tin DNNN | Áp dụng cho dữ liệu nhân sự + cổng |

---

## 8. HẠ TẦNG TRIỂN KHAI (PRODUCTION)

> Theo `architecture-review.md` §6: **v1 triển khai 1 node**, stateless để **thêm node 2 chỉ là đổi cấu hình**.

- 3 môi trường: Dev / Staging / Production. CI/CD rolling.
- **Nginx**: SSL termination + reverse proxy. V1 trỏ 1 App Server; thêm node = bổ sung upstream + bật ShedLock.
- **App Server ×1 (v1)**: Spring Boot API + Scheduler + Worker **in-process** (bounded pool). App **stateless**. Public web Next.js chạy riêng.
- **PostgreSQL 16 + PostGIS (1 node)**: source of truth cho data + queue (SKIP LOCKED) + lock (ShedLock) + `hydro_latest`. **Backup**: `pg_dump` hàng đêm, retention 30 ngày, **lưu khác đĩa/khác máy (VM-3)**, diễn tập restore trước go-live rồi theo quý. RPO ≤ 24h, RTO ≤ 4h.
- **Không Redis**: session/denylist ở DB; site config Caffeine in-process. **MinIO**: media, tài liệu, file báo cáo.
- **Monitoring**: Prometheus + Grafana + health-check (MOD-05 M5.12); log JSON rotation 30 ngày.
- **External** *(cập nhật 12/8/2026)*: **Telemetry API `songnhue.bhh40.net/api/getmn.aspx`** (HTTP, xác thực bằng `key` = mã số — mã hóa AES-256-GCM, đọc từ env); **hệ thống văn bản điều hành** = chính `songnhue.bhh40.net` (liên kết auto-login, không đồng bộ dữ liệu); SMTP; ~~SMS Gateway~~ (hoãn phase sau); Google Maps (optional).
- **Màn hình lớn Phòng điều hành**: TV 85" 4K (3840×2160) + máy chiếu 2K/Full HD+ — trình duyệt kiosk trỏ vào route `?mode=wall`, không cần máy trạm riêng.

---

## 9. LỘ TRÌNH TRIỂN KHAI (WBS — SRS §6)

1. **Khảo sát & thiết kế**: khảo sát quy trình vận hành tại trạm; thống kê mẫu báo cáo hiện có; chốt SRS; thiết kế UI/UX (public + admin + màn hình lớn); thiết kế DB (thủy văn, nhân sự, GIS) + phương án tích hợp API bên thứ 3 + hệ thống văn bản.
2. **Phát triển**: MOD-01 → MOD-02 (danh mục CT + GIS) ∥ MOD-03 (polling + thủy văn) → MOD-04 → MOD-05.
3. **Kiểm thử**: chức năng, hiệu năng (NFR-02), bảo mật, tương thích.
4. **Triển khai**: setup hạ tầng 3 môi trường, go-live, đào tạo Admin + User, bàn giao mã nguồn + Swagger/OpenAPI + tài liệu hướng dẫn.

**Tiêu chí nghiệm thu (SRS §7)**: đủ chức năng §3; pass test chức năng/bảo mật/hiệu năng; thủy văn đồng bộ chính xác đúng chu kỳ; GIS đúng vị trí + liên kết; Responsive; bàn giao tài liệu + đào tạo.

---

## 10. TRACEABILITY — ÁNH XẠ SRS ↔ FUNCTION-SPEC

| SRS | Function-spec | | SRS | Function-spec |
|---|---|---|---|---|
| M1.1 | CN-01.1 | | M3.1–M3.2 | CN-03.1 |
| M1.2 | CN-01.2 | | M3.3–M3.5, M3.15–M3.16 | CN-03.2 |
| M1.3 | CN-01.3 | | M3.6 | CN-03.3 |
| M1.4, M1.6 | CN-01.5 | | M3.7–M3.9 | CN-03.4 |
| M1.5 | CN-01.4 | | M3.10–M3.12, M3.18 | CN-03.5 |
| M1.7 | CN-01.6 | | M3.13–M3.14 | CN-03.6 |
| M1.8 | CN-01.7 | | M3.17 | CN-03.7 |
| M1.9 | CN-01.8 | | M4.1 | CN-04.1 |
| M1.10 | CN-01.9 | | M4.2 | CN-04.2 |
| M2.1–M2.2, M2.5–M2.6 | CN-02.1 | | M4.3 | CN-04.3 |
| M2.3 | CN-02.2 | | M4.4–M4.6 | CN-04.4 |
| M2.4 | CN-02.3 | | M4.7–M4.9 | CN-04.5 |
| M2.7–M2.13 | CN-02.4 | | M4.10 | CN-04.9 |
| M2.14–M2.15 | CN-02.5 | | M4.11 | CN-04.6 |
| M2.16–M2.17 | CN-02.6 | | M4.12–M4.13 | CN-04.7 |
| M2.18 | CN-02.7 | | M4.14–M4.17 | CN-04.8 |
| — (đã bỏ 12/8) | ❌ CN-02.8 / ❌ CN-02.9 | | M5.1 | CN-05.1 |
| — (ngoài SRS, đã chốt scope) | ✅ CN-02.10 báo cáo · ✅ CN-02.11 tình hình vận hành (G4) | | | |
| M5.2–M5.3 | CN-05.2 | | M5.4–M5.6, M5.17 | CN-05.3 |
| M5.7–M5.8 | CN-05.4 | | M5.9–M5.11 | CN-05.5 |
| M5.12–M5.13 | CN-05.6 | | M5.14–M5.16 | CN-05.7 |

❌ = đã loại khỏi scope 12/8/2026: nhật ký vận hành (CN-02.8), **phiếu sự cố riêng (CN-02.9 — chốt G1 PA A, gộp vào CN-02.2)**, kế hoạch vụ mùa, BC-01/02/03/04/07/08.
✅ = phần ngoài SRS v1.0 nhưng **đã chốt nằm trong scope**: CN-02.10 (BC-06/09/10) và CN-02.11 (tình hình vận hành cống — G4).
**Không còn hạng mục 🔷 nào trong MOD-02.**

---

## PHỤ LỤC — QUY ƯỚC CHUNG CHO DEV

- **Mã định danh**: Công trình `TB-SN-xxx`/`CG-SN-xxx`/`DE-SN-xxx`/`KM-SN-xxx`; Điểm đo (mã nội bộ + mã ánh xạ API `F#####`); Bảo trì **và sự cố** dùng chung `BT-<năm>-xxxx` *(bỏ mã `SC-` theo G1)*; NV `NV-<năm>-xxx`; Đơn vị `PB-xx`/`XN-xx`.
- **Đơn vị đo chuẩn nội bộ** (chốt B6): mực nước **m** scale 3 (nguồn trả **cm** → adapter chia 100); lượng mưa **mm** scale 1; lưu lượng **m³/s** scale 3. Mọi quy đổi làm ở adapter lúc ingest, không làm ở tầng hiển thị.
- **Vị trí**: ngoài tọa độ Lat/Lng, ghi kèm **tuyến sông + lý trình `K<km>+<m>`** theo cách Công ty đang định danh.
- **Soft delete** cho mọi entity nghiệp vụ; audit log old/new value; audit append-only.
- **Timestamp**: lưu `timestamptz` UTC; convert UTC+7 chỉ ở tầng hiển thị.
- **Kiểu số**: NUMERIC/BigDecimal cho mọi số đo (mực nước, lưu lượng, kWh) và tiền (chi phí bảo trì, lương); cấm float/double. Mọi giá trị tính toán tính ở BE — FE chỉ hiển thị.
- **Màu trạng thái thống nhất**: Xanh = bình thường; Vàng = cảnh báo/bảo trì; Đỏ = nguy hiểm/sự cố; **Xám = ngừng / mất tín hiệu / trạm trục trặc (chốt G3)**; Đen = thanh lý. Màu của **mã tình hình vận hành** (CN-02.11) do Admin cấu hình riêng, không trộn với bảng màu này.
- **Upload**: validate định dạng + dung lượng theo bảng từng module; scan malware; lưu MinIO.
- **API nội bộ**: REST, JWT Bearer; API public widget dùng token riêng; lỗi upstream → graceful degradation ("Không có dữ liệu").
