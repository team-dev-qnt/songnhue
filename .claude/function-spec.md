# FUNCTION SPECIFICATION — HỆ THỐNG QUẢN TRỊ & ĐIỀU HÀNH THỦY LỢI SÔNG NHUỆ

> Tài liệu đặc tả chức năng cô đọng cho team dev. Tổng hợp từ "Tổng quan HT PM Quản lý điều hành TLSN", "Đặc tả hệ thống Website Thủy Lợi Sông Nhuệ" và **SRS_QuanTriDieuHanh_TLSN ver 06.8.2026** (SRS v1.0, 23/07/2026).
> Phiên bản: 2.0 — Cập nhật: 2026-08-06 (tái cấu trúc module theo SRS)
>
> ⚠ **Thay đổi lớn v2.0**: cấu trúc 5 module đồng bộ theo SRS — **tách "Quản lý dữ liệu thủy văn" thành MOD-03 riêng**; **gộp tích hợp văn bản điều hành vào MOD-01 (Cổng TTĐT)**; HRM chuyển thành MOD-04; Quản trị hệ thống thành MOD-05 (bổ sung chức năng theo SRS). Các nghiệp vụ **nhật ký vận hành, phiếu sự cố, báo cáo vận hành BC-01..08** KHÔNG có trong SRS v1.0 → giữ lại như **phần mở rộng (ngoài SRS)**, đánh dấu 🔷 và cần khách xác nhận nằm trong scope hợp đồng.

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
| Backup | pg_dump hàng ngày + WAL archiving (PITR, RPO ≤ 15'), retention 30 ngày |

### 0.3. Vai trò (Actors)

Hợp nhất từ nhóm vai trò tổng quát của SRS §2.2 và các actor chi tiết theo module.

| Vai trò | Phạm vi |
|---|---|
| Guest / Người dùng công cộng | Xem tin tức, thông tin công ty, văn bản công khai, gửi liên hệ, widget thủy văn |
| Cán bộ nội bộ (Viewer) | Xem báo cáo, dữ liệu thủy văn, GIS, hồ sơ công trình, danh bạ theo phòng ban được phân |
| Biên tập viên | Soạn thảo bài viết, media (MOD-01) — không tự xuất bản |
| Quản trị nội dung | Duyệt/xuất bản bài viết, danh mục, banner, liên hệ, phản hồi (MOD-01) |
| Cán bộ văn thư | Đánh dấu văn bản điều hành công khai phía hệ thống nguồn (MOD-01 tích hợp) |
| Cán bộ kỹ thuật | Hồ sơ công trình, số hóa GIS, điểm đo thủy văn, cấu hình ngưỡng, xử lý sự cố 🔷 |
| Operator (vận hành viên) 🔷 | Nhập nhật ký vận hành, tạo phiếu sự cố — chỉ trong Xí nghiệp mình (phần mở rộng ngoài SRS) |
| Quản lý công trình / Quản lý XN | Duyệt hồ sơ/nhật ký, đóng cảnh báo/sự cố, báo cáo XN mình |
| Trực ban điều hành | Nhận cảnh báo ngưỡng thủy văn, theo dõi dashboard màn hình lớn |
| Ban giám đốc / Điều hành | Xem dashboard điều hành, báo cáo tổng hợp đa chiều |
| Quản trị nhân sự (Admin HR) | Toàn quyền dữ liệu HRM; trường nhạy cảm 🔒 |
| Admin (QTV hệ thống) | Toàn quyền: cấu hình, tài khoản, phân quyền, API nguồn, audit, backup/restore |

### 0.4. Danh sách module (đồng bộ SRS §2.3)

| Mã | Module (SRS) | Nội dung chính |
|---|---|---|
| MOD-01 | Cổng thông tin điện tử (E-Portal) | Bài viết, danh mục, media, banner, liên hệ, phản hồi, cấu hình giao diện, tìm kiếm, **tích hợp hệ thống văn bản điều hành** (M1.8) |
| MOD-02 | Quản lý & vận hành công trình thủy lợi (GIS) | Danh mục công trình, thông số kỹ thuật, lịch sử bảo trì, tài liệu, bản đồ GIS nhiều lớp, dashboard điều hành, thống kê, nhật ký thay đổi hồ sơ. **Mở rộng 🔷: nhật ký vận hành, phiếu sự cố, báo cáo vận hành** |
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
- Nếu bật bình luận: mọi bình luận mới = 'Chờ duyệt', chỉ hiện sau duyệt; Duyệt / Từ chối / Xóa / Spam; lọc spam (Akismet hoặc tương đương); email notify.
- *(Xem `business-open-questions.md` D1 — đề xuất phase 1 tắt bình luận công khai.)*

### CN-01.7. Tích hợp Hệ thống Văn bản điều hành (Cao) — *SRS M1.8, UC1.4* — **gộp từ MOD-04 cũ**
- Công ty **đã có** hệ thống quản lý văn bản điều hành. Không xây mới — chỉ **hiển thị/đồng bộ** danh sách văn bản đã ban hành, được phép công khai lên cổng thông tin.
- **Luồng**: Cán bộ văn thư đánh dấu văn bản "cho phép công khai" ở hệ thống nguồn → hệ thống này (theo lịch đồng bộ định kỳ hoặc theo sự kiện) lấy danh sách văn bản mới → hiển thị lên mục Văn bản trên cổng (số hiệu, ngày ban hành, loại văn bản, tệp đính kèm) → Quản trị nội dung có thể ẩn/hiện.
- **Quy tắc**: hệ thống KHÔNG tự thay đổi trạng thái công khai của văn bản gốc; chỉ hiển thị văn bản đã đánh dấu công khai từ nguồn.
- **Lỗi kết nối**: ghi log, giữ nguyên dữ liệu đồng bộ lần gần nhất (graceful degradation).
- **Phương án tích hợp** (SSO / API / CSDL / định dạng trao đổi) — ⬜ **chưa chốt**, cần khảo sát thông tin kỹ thuật từ Công ty (xem `business-open-questions.md` E3 và SRS §8).

### CN-01.8. Tìm kiếm & Tra cứu nội dung (Trung bình) — *SRS M1.9*
- Tìm kiếm bài viết, văn bản, công trình theo từ khóa, danh mục, thời gian đăng.
- Full-text `unaccent` tiếng Việt (có/không dấu); highlight từ khóa; phân trang; lọc theo phạm vi (bài viết / văn bản / công trình).

### CN-01.9. Responsive Web Design — *SRS M1.10*
- Giao diện cổng hiển thị tối ưu desktop/tablet/mobile (xem NFR §7).

---

## 2. MOD-02 — QUẢN LÝ & VẬN HÀNH CÔNG TRÌNH THỦY LỢI (GIS)

Người dùng: Cán bộ kỹ thuật, Quản lý công trình, Ban giám đốc/Trực ban, Admin. *(Nhật ký vận hành 🔷: Operator, Quản lý XN.)*
Công nghệ đặc thù: GIS (GeoJSON/KMZ), thống kê, dashboard màn hình lớn. Dữ liệu thủy văn hiển thị lên bản đồ do **MOD-03** cung cấp (layer điểm đo).

### CN-02.1. Quản lý Danh mục Công trình (Cao) — *SRS M2.1, M2.2, M2.5, M2.6, UC2.1*

**Phân cấp & cấp quản lý**: Công ty → Xí nghiệp → Cụm công trình → Công trình đơn lẻ. Mỗi công trình gán **cấp quản lý** (Công ty/Xí nghiệp/Cụm) + **đơn vị phụ trách chính** (FK `org_units`). 1 công trình chỉ thuộc 1 cấp quản lý + 1 đơn vị phụ trách chính (SRS quy tắc §3.2.3).

**Loại công trình**: Trạm bơm / Cống / Kênh mương / **Đê điều** / Khác *(Đê điều bổ sung theo SRS)*.

**Hồ sơ Trạm bơm** — trường chính:
- Định danh: Mã CT (unique toàn hệ thống, VD `TB-SN-001`), Tên, Loại (Tưới/Tiêu/Hỗn hợp), Xí nghiệp (FK, bắt buộc), Cụm (FK), Địa chỉ, Tọa độ Lat/Lng Decimal(9,6), Năm XD, Năm sử dụng, Đơn vị thiết kế/thi công, Tổng vốn (triệu VND).
- Thông số kỹ thuật: Công suất tổng (kW), Số máy bơm + dự phòng, Lưu lượng thiết kế/máy (m³/s), Cột nước (m), **Lưu lượng tổng = SL máy × LL/máy (auto)**, Diện tích tưới tiêu (ha), Nguồn điện, Điện áp (kV), **Ngưỡng MN vận hành min/max**.

**Hồ sơ Cống điều tiết** — trường chính: Mã (`CG-SN-001`), Tên, Loại (Hộp/Tròn/Van phẳng/Clape), XN, Tọa độ, Số khoang, Khẩu độ/khoang (m), Cao trình ngưỡng/đỉnh, Lưu lượng thiết kế (m³/s), Thiết bị đóng mở (Thủ công/Điện/Thủy lực), **Ngưỡng MN thượng lưu cảnh báo/nguy hiểm**.

**Đê điều / Kênh mương** — hồ sơ tối thiểu (mã, tên, cấp, đơn vị, tọa độ/tuyến, thông số đặc thù dạng văn bản/số); chi tiết chốt ở thiết kế DB *(xem `business-open-questions.md` A3)*.

**Liên kết lưu vực / khu tưới tiêu (SRS M2.6)**: công trình gắn với **lưu vực / khu tưới tiêu / hệ thống kênh mương** mà nó phục vụ (FK tham chiếu). Là cơ sở cho thống kê diện tích và kế hoạch vụ mùa *(xem A1)*.

**Trạng thái vận hành công trình** (SRS §3.2.4): **Bình thường (xanh) / Cảnh báo (vàng) / Sự cố (đỏ) / Bảo trì (vàng)** + (nội bộ mở rộng: Ngừng mùa vụ xám / Đã thanh lý đen). Quyết định màu marker GIS; đồng bộ realtime với hồ sơ.

**Quy tắc**: Mã công trình duy nhất toàn hệ thống; công trình chỉ lên bản đồ GIS khi đã số hóa tọa độ hợp lệ.

### CN-02.2. Lịch sử Sửa chữa / Bảo trì (Cao) — *SRS M2.3, UC2.2* — **mới theo SRS**
- Ghi nhận mỗi lần sửa chữa/bảo trì/nâng cấp: ngày thực hiện, nội dung, đơn vị thực hiện, **chi phí (nếu có)**, tài liệu/ảnh kèm.
- Hiển thị dạng timeline trên trang chi tiết công trình; phục vụ lập kế hoạch bảo trì tiếp theo.
- Số tiền dùng NUMERIC (BigDecimal) — cấm float.

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

- **KPI cards**: Tổng CT hoạt động/tổng, Đang vận hành, Cảnh báo đang xảy ra, Sự cố chưa xử lý 🔷, Σ m³ bơm hôm nay 🔷, nhật ký hôm nay 🔷.
- **Bản đồ GIS tổng quan** + lớp thủy văn hiện hành (mực nước, lượng mưa theo điểm đo — MOD-03) + biểu tượng cảnh báo.
- **Chart & bảng**: cột lưu lượng 7 ngày theo XN 🔷; đường mực nước 24h điểm đo đang cảnh báo (MOD-03); danh sách sự cố mới 🔷; nhật ký chưa duyệt 🔷.
- **Tự động làm mới (M2.15)**: dữ liệu bản đồ + số liệu tự cập nhật theo chu kỳ cấu hình, không thao tác tay.
- **Chế độ màn hình lớn Phòng điều hành** (route `?mode=wall`): ưu tiên trực quan (biểu đồ/bản đồ/số liệu lớn dễ đọc từ xa) hơn bảng chi tiết; auto-rotate; dark theme. Khi cảnh báo ngưỡng (M3.14) hoặc CT chuyển sự cố → hiển thị nổi bật. Mất kết nối → "Dữ liệu chưa cập nhật" + thời điểm gần nhất.
- Nội dung/độ phân giải màn hình lớn — ⬜ cần khảo sát Phòng điều hành *(B8, SRS §8)*.

### CN-02.6. Thống kê & Tìm kiếm Công trình (Trung bình) — *SRS M2.16, M2.17, UC2.4*
- Thống kê số lượng công trình theo loại/khu vực/tình trạng/cấp quản lý; biểu đồ + bảng; xuất Excel/PDF.
- Tìm kiếm & lọc công trình theo tên, mã, khu vực, loại, tình trạng — trên danh sách và trên bản đồ.

### CN-02.7. Nhật ký Thay đổi Hồ sơ Công trình (Cao) — *SRS M2.18*
- Ghi lịch sử chỉnh sửa hồ sơ công trình (người sửa, thời gian, nội dung thay đổi — old/new) phục vụ truy vết; liên kết audit log MOD-05.

---

### 🔷 PHẦN MỞ RỘNG MOD-02 (NGOÀI SRS v1.0 — cần khách xác nhận scope)

> Các mục CN-02.8 → CN-02.10 đến từ tài liệu gốc "Đặc tả hệ thống Website" và không xuất hiện trong SRS v1.0. Giữ lại vì là nghiệp vụ lõi vận hành thực tế; đánh dấu 🔷 và chờ Công ty xác nhận nằm trong phạm vi hợp đồng (xem `business-open-questions.md`).

### CN-02.8. 🔷 Nhật ký Vận hành (Cao)

**Quy trình**: Operator chọn công trình (chỉ CT thuộc XN mình) → chọn ngày (mặc định hôm nay, **nhập bù tối đa 3 ngày trước**) → check trùng (công trình × ngày × ca) → nhập form → Lưu nháp hoặc Gửi → Quản lý XN duyệt.

**Form**:
- Chung: Công trình, Ngày, Ca (Ngày 6h-18h / Đêm 18h-6h / Cả ngày), Người vận hành, Thời tiết.
- Mỗi tổ máy: Số máy, Trạng thái (Chạy/Dừng/Bảo trì/Sự cố), Giờ BD–KT (bắt buộc khi Chạy), **Số giờ chạy = auto (KT–BD)**, Lưu lượng thực tế (m³/s), **Lưu lượng bơm = LL × giờ × 3600 (auto, m³)**, Điện năng (kWh), Ghi chú.
- Thủy văn: MN thượng lưu đầu/cuối ca (bắt buộc), MN hạ lưu đầu/cuối ca, Lượng mưa trong ca.
- Sự cố trong ca (checkbox): loại, mô tả, mức độ (Nhẹ/Trung bình/Nặng), biện pháp tạm, thời điểm, ảnh (≤5×5MB). **Mức 'Nặng' → auto tạo phiếu sự cố (CN-02.9) + notify Kỹ thuật.**

**Validation**: Số giờ chạy 0 < giờ ≤ độ dài ca; Lưu lượng thực tế > 0 và ≤ 120% thiết kế (vượt → xác nhận); Mực nước trong dải [min,max]; Ngày nhập bù hôm nay−3 ≤ ngày ≤ hôm nay; Giờ KT > BD.

**Trạng thái duyệt**: Nháp → Chờ duyệt (khóa sửa) → Đã duyệt (chính thức, sửa cần Admin) / Từ chối (lý do) / Yêu cầu sửa.

### CN-02.9. 🔷 Quản lý Sự cố Công trình (Trung bình)
- **Nguồn tạo phiếu**: auto từ nhật ký (sự cố Nặng); thủ công; từ màn hình cảnh báo (MOD-03).
- **Phiếu sự cố**: Mã auto (`SC-2026-0001`), Công trình, Tiêu đề (≤200), Loại (Điện/Cơ khí/Thủy công/Thiên tai/An ninh/Khác), Mức độ (Critical/High/Medium/Low), Mô tả, Thời điểm + Người phát hiện, Ảnh/video, Phân công (multi-user), Hạn xử lý.
- **Vòng đời**: Mới → Đang xử lý → (Chờ vật tư | Khắc phục tạm) → Chờ nghiệm thu → Đã đóng (Quản lý/Admin) | Hủy (Admin).
- **Nhật ký xử lý**: tab riêng, cập nhật tiến độ + media; mọi đổi trạng thái auto ghi log + email liên quan.

### CN-02.10. 🔷 Tổng hợp & Xuất Báo cáo Vận hành (Cao)
- **Danh mục**: BC-01 Vận hành ngày; BC-02 Tuần; BC-03 Tháng; BC-04 Kết quả vụ tưới/tiêu; BC-05 Thủy văn tháng; BC-06 Cảnh báo & sự cố; BC-07 Kế hoạch vs thực hiện; BC-08 Tiêu thụ điện. PDF + Excel.
- **Tổng hợp tự động (cron)**: ngày 00:05, tuần T2 00:10, tháng ngày 1 00:15. Chỉ tiêu: Σ giờ chạy máy, Σ lưu lượng bơm (m³), Σ kWh, diện tích tưới/tiêu đạt, % hoàn thành kế hoạch, số ca có sự cố.
- **Tạo thủ công + Async Job Queue**: POST → HTTP 202 + job_id → queue → Worker → notify + link tải hiệu lực 24h; lịch sử 90 ngày. Template chuẩn công ty (.xlsx/.docx→PDF).
- BC-04/BC-07 phụ thuộc **Kế hoạch vụ mùa** — chức năng còn thiếu, xem A1 (`business-open-questions.md`).

---

## 3. MOD-03 — QUẢN LÝ DỮ LIỆU THỦY VĂN

> **Module mới tách riêng theo SRS §3.3.** Trước đây gộp trong MOD-02. Lõi kỹ thuật: kết nối API bên thứ 3, chuẩn hóa, time-series, biểu đồ, báo cáo, cảnh báo ngưỡng.

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

- **Quan hệ Điểm đo ↔ Công trình**: hiện SRS chưa định nghĩa rõ (thượng lưu/hạ lưu). Đề xuất thiết kế **n–n có vai trò** (`station_constructions`: role = thượng lưu / hạ lưu / mưa). ⬜ Cần khách confirm mô hình thực địa — xem `business-open-questions.md` A2b.

### CN-03.2. Kết nối API & Đồng bộ Dữ liệu (Cao) — *SRS M3.3–M3.5, M3.15, M3.16, UC3.2, UC3.5*

**Luồng**: Trạm quan trắc (RTU/DataLogger) → Telemetry Server bên thứ 3 → **MOD-03 polling REST API theo chu kỳ 5–15'** → bóc tách + chuẩn hóa đơn vị → validate → DB time-series → phân phối cho Dashboard, Widget CMS, GIS, Alert.

**Cấu hình nguồn API (M3.3, MOD-05 M5.5)** (chỉ Admin): Tên nguồn, URL endpoint, Xác thực (API Key/Bearer/Basic/OAuth2 — **credential mã hóa AES-256-GCM, không hiển thị plaintext**), Chu kỳ polling (min 5', mặc định 15'), Timeout (mặc định 30s), Retry (mặc định 3, exponential backoff 5→10→20'), Điểm đo gắn nguồn, Bật/Tắt. **Chu kỳ + số lần retry phải cấu hình được, không hard-code** (SRS quy tắc §3.3.3).

**Bóc tách & chuẩn hóa (M3.4)**: chuyển dữ liệu thô sang cấu trúc chuẩn (điểm đo, thời điểm đo, giá trị, đơn vị); adapter chuyển đổi đơn vị nguồn.

**Validate (M3.5)**: khoảng giá trị vật lý cho phép + chống trùng thời điểm đo trên cùng điểm đo. Dữ liệu không hợp lệ → **không ghi vào lịch sử chính thức, vẫn lưu log** để đối soát. **Trạng thái bản ghi**: Hợp lệ / Nghi ngờ / Loại bỏ *(mới theo SRS §3.3.4)*.

**Xử lý gián đoạn (M3.15, UC3.5)**: sau 3 lần retry thất bại liên tiếp → đánh dấu điểm đo/nguồn OFFLINE + email alert Admin; gián đoạn kéo dài quá ngưỡng → cảnh báo cấp cao hơn; kết nối phục hồi → ghi log phục hồi, tiếp tục đồng bộ.

**Nhật ký đồng bộ (M3.16)**: mọi lần đồng bộ (thành công/thất bại, thời gian, số bản ghi) ghi log; UI tra cứu phục vụ giám sát vận hành.

**Nhập tay**: hỗ trợ khi API gián đoạn, đánh dấu nguồn = 'Manual'.

### CN-03.3. Lưu trữ Dữ liệu Lịch sử (Cao) — *SRS M3.6, UC3.2*
- Raw data → `hydro_raw_logs` **append-only** (audit + tái xử lý). Dữ liệu chuẩn hóa → `hydro_readings` time-series, index `(station_id, timestamp)`, **partition theo tháng**.
- `hydro_latest` (1 dòng/điểm đo, poller UPSERT) — phục vụ Widget/GIS/Dashboard + graceful degradation.
- Retention: chi tiết 5 năm; tổng hợp ngày vĩnh viễn; >2 năm chuyển Cold Storage (nén) vẫn truy vấn được.
- Mỗi bản ghi gắn 1 điểm đo + 1 timestamp duy nhất (SRS quy tắc §3.3.3).

### CN-03.4. Giám sát Realtime & Biểu đồ (Cao) — *SRS M3.7–M3.9, UC3.3*
- Bảng realtime toàn điểm đo, auto-refresh 5'; màu ô Xanh/Vàng/Đỏ theo ngưỡng; badge OFFLINE nếu bản ghi cuối > 1 giờ; hiển thị thời điểm cập nhật gần nhất.
- Line chart (ECharts): **multi điểm đo so sánh (M3.9)**, chọn thông số, khoảng thời gian (24h/7d/30d/năm/custom); đường ngưỡng (nét đứt vàng/đỏ); tooltip; export PNG/SVG/CSV.
- Không có dữ liệu khoảng đã chọn → "Không có dữ liệu" (không vẽ biểu đồ trống).
- Lọc: đơn vị, điểm đo, thời gian, trạng thái.

### CN-03.5. Báo cáo Khai thác Dữ liệu Thủy văn (Cao) — *SRS M3.10–M3.12, M3.18, UC3.4*
- **Định kỳ (M3.10)**: báo cáo ngày/tuần/tháng theo mẫu cố định.
- **Theo yêu cầu (M3.11)**: người dùng tự đặt tham số (điểm đo, khoảng thời gian, loại chỉ số).
- **Thống kê mùa vụ (M3.18)**: so sánh dữ liệu giữa các kỳ/mùa vụ (theo năm, theo tháng tương ứng nhiều năm) — phân tích xu hướng.
- **Xuất (M3.12)**: Excel/PDF. Dùng Async Job Queue (202 + job_id) như CN-02.10. Validate tham số (ngày kết thúc ≥ bắt đầu).

### CN-03.6. Cảnh báo Ngưỡng Thủy văn (Cao) — *SRS M3.13, M3.14, UC3.5*
- **Cấu hình theo từng điểm đo × từng loại chỉ số** (không dùng chung 1 ngưỡng toàn hệ thống — SRS quy tắc §3.3.3): mức thấp/cao; mở rộng nội bộ: 3 mức Bình thường/Warning/Critical, loại điều kiện `>`,`<`, ngoài khoảng, tốc độ thay đổi (delta/giờ); delay chống nhiễu (X phút liên tục). Ngưỡng mặc định khi tạo điểm đo mới lấy từ MOD-05 (M5.6).
- **Đánh giá**: ngay sau mỗi lần ghi reading; alert event unique key `(rule_id, thời điểm bắt đầu)` chống trùng; hysteresis lưu DB.
- **Kênh phát** (Notification service Core):

| Kênh | Mức | Ghi chú |
|---|---|---|
| Dashboard banner | Warning + Critical | Xác nhận đã đọc |
| Email | Warning + Critical | Kèm link màn hình điểm đo/công trình; danh sách nhận theo điểm đo |
| SMS | Critical only | ESMS/Twilio *(⚪ chốt nhà cung cấp — B7)* |
| Web Push in-app | Warning + Critical | Cần cấp quyền browser |

- **Lịch sử cảnh báo**: điểm đo, thông số, giá trị, mức, thời gian bắt đầu/kết thúc, người xác nhận, ghi chú xử lý; lọc; phân loại Đang xảy ra / Đã xử lý / False Alarm.

### CN-03.7. Hiển thị Thủy văn trên Bản đồ GIS (Trung bình) — *SRS M3.17, UC3.6*
- Lớp "Điểm đo thủy văn" trên bản đồ GIS (MOD-02): vị trí điểm đo + giá trị đo mới nhất (từ `hydro_latest`).
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

### CN-04.9. Quản lý Nghỉ phép (Trung bình) — *SRS M4.10*
- **Chính sách (Admin HR)**: phép năm theo thâm niên (Điều 113 BLLĐ 2019: <5 năm=12; 5–10=13; >10=14); phép đặc biệt (thai sản 180, cưới 3, tang 3, khám SK 1); chuyển tối đa N ngày (mặc định 5); ngày lễ (trừ tự động).
- **Quy trình**: NV đăng ký → tự tính số ngày (trừ cuối tuần + lễ) + hiển thị số dư → cảnh báo vượt phép → gửi đơn → Quản lý duyệt/từ chối (email) → tự trừ số dư.
- **Số dư**: Được hưởng = thâm niên + chuyển năm trước; Còn lại = Được hưởng − Đã nghỉ − Đang chờ duyệt (tính lại từ đơn, không cộng trừ tay).
- **Lịch đơn vị**: Calendar; cảnh báo trùng lịch khi > ngưỡng % quân số nghỉ cùng lúc.
- *(Chi tiết pro-rata, luồng duyệt nhiều cấp, phạm vi tài khoản — xem `business-open-questions.md` C1–C3.)*

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
- **Xuất/nhập cấu hình (M5.17)** — *mới theo SRS*: export bộ cấu hình ra tệp / import lại — hỗ trợ sao lưu cấu hình hoặc chuyển môi trường staging/production.

### CN-05.4. Nhật ký Hoạt động — Audit Log (Cao) — *SRS M5.7, M5.8, UC5.4*
- Ghi mọi thao tác quan trọng: đăng nhập/xuất, thêm/sửa/xóa dữ liệu, thay đổi phân quyền — user, timestamp, action, module, old/new value.
- UI tra cứu lọc theo user/module/thời gian/loại thao tác; xem chi tiết bản ghi.
- **Audit log KHÔNG được sửa/xóa bởi bất kỳ vai trò nào, kể cả Admin** (append-only + hash chain — SRS quy tắc §3.5.3; xem `conventions.md` §4.3).

### CN-05.5. Backup & Restore (Cao) — *SRS M5.9–M5.11, UC5.6*
- **Backup tự động theo lịch (M5.9)**: pg_dump hàng ngày + WAL archiving (PITR, RPO ≤ 15'), retention 30 ngày; lưu **tách biệt máy chủ vận hành chính** (SRS quy tắc §3.5.3); ghi log kết quả; thất bại → cảnh báo Admin ngay.
- **Backup theo yêu cầu (M5.10)**: Admin chủ động backup trước nâng cấp/thay đổi lớn.
- **Restore qua UI (M5.11)** — *theo SRS, đảo quyết định E1 cũ*: Admin chọn bản backup → thực hiện khôi phục → thông báo kết quả. **Bảo vệ bắt buộc** (xem `architecture-review.md` §7): xác nhận nhiều bước (gõ tên hệ thống), chỉ Super Admin + 2FA, ghi security event, chạy async có tiến độ, khuyến nghị restore ra môi trường staging trước; runbook PITR vẫn giữ song song cho khôi phục điểm-thời-gian.
- UI hiển thị trạng thái backup gần nhất.

### CN-05.6. Giám sát Hệ thống & Thông báo (Trung bình) — *SRS M5.12, M5.13, UC5.3* — **mới theo SRS**
- **Health-check (M5.12)**: theo dõi tình trạng dịch vụ/API tích hợp (hệ thống văn bản điều hành MOD-01, API thủy văn MOD-03, SMTP, SMS, MinIO); cảnh báo khi sự cố. Tách khỏi alert nghiệp vụ.
- **Thông báo hệ thống (M5.13)**: Admin soạn/gửi thông báo chung (bảo trì, cập nhật phiên bản) tới toàn bộ hoặc nhóm người dùng.

### CN-05.7. Bảo mật Tài khoản & Phiên (Cao) — *SRS M5.14–M5.16, UC5.5* — **mới theo SRS**
- **Xác thực**: Access token 30' + Refresh rotation (httpOnly cookie), BCrypt; thu hồi qua denylist bảng DB; refresh reuse detection.
- **Quản lý phiên (M5.14)**: theo dõi phiên đang hoạt động (thời gian, thiết bị/IP), **đăng xuất từ xa** một phiên bất kỳ.
- **Chính sách mật khẩu (M5.15)**: độ phức tạp, thời hạn đổi định kỳ, khóa tài khoản sau N lần sai — **cấu hình được, không hard-code** (SRS quy tắc §3.5.3).
- **Cảnh báo đăng nhập bất thường (M5.16)**: nhiều lần sai mật khẩu liên tiếp, đăng nhập ngoài giờ hành chính → cảnh báo Admin **near real-time**, không xử lý theo lô.

---

## 6. MA TRẬN PHÂN QUYỀN RBAC (MOD-02/03 — tham chiếu chính)

| Chức năng | Admin | Quản lý XN | Kỹ thuật | Operator |
|---|:-:|:-:|:-:|:-:|
| Xem danh mục công trình | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Thêm/Sửa hồ sơ công trình | ✔ | ✘ | ✔ | ✘ |
| Ghi lịch sử bảo trì | ✔ | ✔ | ✔ | ✘ |
| Upload tài liệu công trình | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Quản lý điểm đo (MOD-03) | ✔ | ✘ | ✔ | ✘ |
| Cấu hình API nguồn dữ liệu | ✔ | ✘ | ✘ | ✘ |
| Cấu hình ngưỡng cảnh báo | ✔ | ✔ | ✔ | ✘ |
| Đóng/Xử lý cảnh báo | ✔ | ✔ | ✔ | ✘ |
| Upload/Quản lý layer GIS | ✔ | ✘ | ✔ | ✘ |
| Nhập nhật ký vận hành 🔷 | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Duyệt nhật ký 🔷 | ✔ | ✔ | ✘ | ✘ |
| Tạo phiếu sự cố 🔷 | ✔ | ✔ | ✔ | ✔ |
| Đóng phiếu sự cố 🔷 | ✔ | ✔ | ✘ | ✘ |
| Xem báo cáo | ✔ | ✔ (XN mình) | ✔ | ✔ (XN mình) |
| Tạo/Xuất báo cáo | ✔ | ✔ | ✔ | ✘ |

CMS (MOD-01): Biên tập viên (tạo + gửi duyệt) < Quản trị nội dung (Duyệt, Xuất bản, Gỡ) < Admin. Cán bộ văn thư: quản lý cờ công khai văn bản.
HR (MOD-04): Admin HR full; Quản lý đơn vị xem NV đơn vị mình; NV chỉ xem danh bạ + hồ sơ mình + đăng ký phép; trường 🔒 chỉ Admin HR + chính NV.
Quản trị (MOD-05): chỉ Admin/Super Admin; restore + xuất/nhập cấu hình yêu cầu 2FA.

---

## 7. YÊU CẦU PHI CHỨC NĂNG (NFR)

| # | Hạng mục | Yêu cầu | Tiêu chí đo |
|---|---|---|---|
| NFR-01 | Khả dụng | Uptime ≥ 99% (SRS §4.4; nội bộ mục tiêu 99.5% giờ hành chính), không tính bảo trì đã lên lịch | Alert khi downtime > 15' |
| NFR-02 | Hiệu năng web | Trang chủ < 3s/mạng bình thường; ≥ 200 users đồng thời không suy giảm đáng kể (SRS §4.1) | P95 dashboard < 3s @ 50 users |
| NFR-03 | Polling | Đúng chu kỳ cấu hình; đọc/lưu thủy văn realtime không làm chậm chức năng khác | Sai lệch < 10%; retry 3 lần + alert |
| NFR-04 | Báo cáo | BC 1 tháng/1 XN < 60s (async) | Job completed < 60s |
| NFR-05 | Bảo mật | HTTPS/TLS, RBAC tối thiểu quyền, hash mật khẩu, log thao tác nhạy cảm; 2FA (mở rộng); API credential AES-256 | Không plaintext trong UI |
| NFR-06 | Phân quyền dữ liệu | Operator/Quản lý chỉ dữ liệu đơn vị mình; trường 🔒 mã hóa | Unit + integration test 100% pass |
| NFR-07 | Audit | Log mọi tạo/sửa/xóa + đăng nhập + đổi quyền | user, timestamp, action, old/new value |
| NFR-08 | Lưu trữ | Hydro chi tiết 5 năm; backup hàng ngày retention 30 ngày, lưu tách biệt | Không mất dữ liệu |
| NFR-09 | Tương thích | Chrome/Firefox/Edge/Safari; mobile; GIS GeoJSON/KMZ (Shapefile chốt sau) | Responsive 360px–2560px |
| NFR-10 | Pháp lý | NĐ 13/2023/NĐ-CP, BLLĐ 2019, Luật Lưu trữ 2011; quy định công bố thông tin DNNN | Áp dụng cho dữ liệu nhân sự + cổng |

---

## 8. HẠ TẦNG TRIỂN KHAI (PRODUCTION)

> Theo `architecture-review.md` §6: **v1 triển khai 1 node**, stateless để **thêm node 2 chỉ là đổi cấu hình**.

- 3 môi trường: Dev / Staging / Production. CI/CD rolling.
- **Nginx**: SSL termination + reverse proxy. V1 trỏ 1 App Server; thêm node = bổ sung upstream + bật ShedLock.
- **App Server ×1 (v1)**: Spring Boot API + Scheduler + Worker **in-process** (bounded pool). App **stateless**. Public web Next.js chạy riêng.
- **PostgreSQL 16 + PostGIS (1 node)**: source of truth cho data + queue (SKIP LOCKED) + lock (ShedLock) + `hydro_latest`. **Backup**: pg_dump hàng ngày + WAL archiving (PITR, RPO ≤ 15'), lưu khác đĩa/khác máy, test restore định kỳ.
- **Không Redis**: session/denylist ở DB; site config Caffeine in-process. **MinIO**: media, tài liệu, file báo cáo.
- **Monitoring**: Prometheus + Grafana + health-check (MOD-05 M5.12); log JSON rotation 30 ngày.
- External: Telemetry API (HTTPS, key AES-256), hệ thống văn bản điều hành, SMTP, SMS Gateway, Google Maps (optional).

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
| — (ngoài SRS) | 🔷 CN-02.8/09/10 | | M5.1 | CN-05.1 |
| M5.2–M5.3 | CN-05.2 | | M5.4–M5.6, M5.17 | CN-05.3 |
| M5.7–M5.8 | CN-05.4 | | M5.9–M5.11 | CN-05.5 |
| M5.12–M5.13 | CN-05.6 | | M5.14–M5.16 | CN-05.7 |

🔷 = phần mở rộng ngoài SRS v1.0 (nhật ký vận hành, sự cố, báo cáo vận hành) — cần khách xác nhận scope.

---

## PHỤ LỤC — QUY ƯỚC CHUNG CHO DEV

- **Mã định danh**: Công trình `TB-SN-xxx`/`CG-SN-xxx`; Điểm đo (mã nội bộ + mã ánh xạ API); Sự cố `SC-<năm>-xxxx`; NV `NV-<năm>-xxx`; Đơn vị `PB-xx`/`XN-xx`.
- **Soft delete** cho mọi entity nghiệp vụ; audit log old/new value; audit append-only.
- **Timestamp**: lưu `timestamptz` UTC; convert UTC+7 chỉ ở tầng hiển thị.
- **Kiểu số**: NUMERIC/BigDecimal cho mọi số đo (mực nước, lưu lượng, kWh) và tiền (chi phí bảo trì, lương); cấm float/double. Mọi giá trị tính toán tính ở BE — FE chỉ hiển thị.
- **Màu trạng thái thống nhất**: Xanh = bình thường; Vàng = cảnh báo/bảo trì; Đỏ = nguy hiểm/sự cố; Xám = ngừng; Đen = thanh lý.
- **Upload**: validate định dạng + dung lượng theo bảng từng module; scan malware; lưu MinIO.
- **API nội bộ**: REST, JWT Bearer; API public widget dùng token riêng; lỗi upstream → graceful degradation ("Không có dữ liệu").
