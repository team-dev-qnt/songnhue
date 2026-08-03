# FUNCTION SPECIFICATION — HỆ THỐNG QUẢN LÝ ĐIỀU HÀNH & CỔNG TTĐT THỦY LỢI SÔNG NHUỆ

> Tài liệu đặc tả chức năng cô đọng cho team dev. Tổng hợp từ "Tổng quan HT PM Quản lý điều hành TLSN" và "Đặc tả hệ thống Website Thủy Lợi Sông Nhuệ".
> Phiên bản: 1.0 — Cập nhật: 2026-07-20

---

## 0. TỔNG QUAN

### 0.1. Phạm vi
Xây dựng hệ thống quản lý điều hành công trình thủy lợi + Cổng thông tin điện tử cho Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ. Responsive Web (360px → 2560px), hỗ trợ trình chiếu màn hình lớn tại Phòng điều hành.

### 0.2. Kiến trúc & Tech stack

> ⚠ Đã cập nhật theo `architecture-review.md` — thay đổi so với đặc tả gốc: PostgreSQL thay MySQL, DB-backed queue thay Redis/RabbitMQ queue, auth refresh-token thay JWT 8h. **Cập nhật 2026-07-21 (review §6):** v1 triển khai **1 node**, **bỏ Redis**, **worker in-process**, ShedLock giữ sẵn (bật khi ≥2 node).

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
| Worker | **In-process (v1)**, bounded thread pool; giữ Spring profile `worker` để tách process khi cần |
| GIS | Leaflet/MapLibre + OSM tiles (Google Maps optional); GeoJSON, KMZ |
| Chart | Apache ECharts |
| Monitoring | Prometheus + Grafana + Micrometer; log JSON + correlation-id, rotation 30 ngày |
| Backup | pg_dump hàng ngày + WAL archiving (PITR, RPO ≤ 15'), retention 30 ngày |

### 0.3. Vai trò (Actors)

| Vai trò | Phạm vi |
|---|---|
| Guest (công cộng) | Xem tin tức, thông tin công ty, gửi liên hệ, widget thủy văn |
| Viewer (nội bộ) | Xem báo cáo, dữ liệu thủy văn, GIS, hồ sơ công trình, danh bạ |
| Operator (vận hành viên) | Nhập nhật ký vận hành, tạo phiếu sự cố — chỉ trong Xí nghiệp mình |
| Kỹ thuật | Hồ sơ công trình, cấu hình ngưỡng, xử lý sự cố, layer GIS |
| Quản lý XN | Duyệt nhật ký, đóng cảnh báo/sự cố, báo cáo XN mình |
| Admin | Toàn quyền: CMS, cấu hình API nguồn, tài khoản, phân quyền, HR |

### 0.4. Danh sách module

| Mã | Module | Nội dung chính |
|---|---|---|
| MOD-01 | Cổng thông tin điện tử (CMS) | Bài viết, danh mục, media, banner, liên hệ, phản hồi, cấu hình giao diện |
| MOD-02 | Quản lý vận hành công trình | Danh mục công trình, giám sát thủy văn (API polling), cảnh báo, GIS, nhật ký vận hành, báo cáo, sự cố, dashboard |
| MOD-03 | Quản lý nhân sự (HRM) | Sơ đồ tổ chức, hồ sơ CBNV, lý lịch, lịch sử công tác, tài liệu, danh bạ, thống kê, nghỉ phép |
| MOD-04 | Tích hợp Văn bản điều hành | Tích hợp hệ thống quản lý văn bản điều hành CÓ SẴN vào cổng thông tin (không xây mới) |
| MOD-05 | Quản trị hệ thống | Tài khoản, phân quyền RBAC, cấu hình hệ thống, audit log, backup/restore |

---

## 1. MOD-01 — CỔNG THÔNG TIN ĐIỆN TỬ (CMS)

Người dùng: Biên tập viên, Trưởng ban biên tập, Quản trị viên nội dung, Admin.
Tích hợp: Widget thủy văn lấy dữ liệu từ MOD-02 qua REST API nội bộ.

### CN-01.1. Quản lý Bài viết & Tin tức (Ưu tiên: Cao)

**Soạn thảo**: Rich Text Editor (CKEditor/TinyMCE); ảnh inline (upload hoặc từ Media, căn lề, caption); embed YouTube/Vimeo; bảng biểu; internal/external link (option mở tab mới); Preview trước khi lưu.

**Trường dữ liệu chính**:

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| Tiêu đề | Text(255) | ✔ | |
| Slug | Text | ✔ | Auto từ tiêu đề (bỏ dấu, gạch ngang); cho sửa tay; cảnh báo trùng |
| Ảnh đại diện | Image | ✘ | Thumbnail listing |
| Tóm tắt | Text(500) | ✘ | |
| Nội dung | RichText | ✔ | |
| Danh mục | Multi-select | ✔ | 1 bài thuộc nhiều danh mục |
| Tác giả | FK User | ✔ | Auto = user đang login, cho đổi |
| Nguồn tin, Tags | Text | ✘ | |
| Ngày xuất bản | DateTime | ✘ | Hẹn giờ đăng |
| Meta Title/Description/Keywords | Text 70/160/- | ✘ | SEO, đếm ký tự + cảnh báo đỏ vượt ngưỡng; hỗ trợ Open Graph |
| Trạng thái | Enum | ✔ | Nháp / Chờ duyệt / Xuất bản / Gỡ bài / Lưu trữ |

**Workflow trạng thái (state machine)**:

```
NHÁP → (Gửi duyệt) → CHỜ DUYỆT → (Duyệt) → XUẤT BẢN → GỠ BÀI ⇄ XUẤT BẢN
         ↑ (Từ chối + lý do)  ↙                        ↘ LƯU TRỮ
Mọi trạng thái → ĐÃ XÓA (soft delete, terminal, không phục hồi)
```
- Nháp: chỉ tác giả + Admin xem. Chờ duyệt: khóa chỉnh sửa, notify Trưởng ban. Gỡ bài: URL trả 404, giữ dữ liệu. Lưu trữ: ẩn listing, vẫn truy cập qua URL trực tiếp. Tái xuất bản từ Gỡ bài: không cần duyệt lại.
- Phân quyền: Biên tập viên chỉ Tạo + Gửi duyệt. Trưởng ban/QTV nội dung/Admin: full (Duyệt, Xuất bản, Gỡ).
- Xuất bản → cập nhật search index + audit log + email notify.

**Hẹn giờ đăng**: cron job 5 phút/lần tự chuyển bài đến hạn sang Xuất bản; màn hình liệt kê bài đang hẹn giờ.

**Danh sách bài viết**: bảng (ID, Tiêu đề, Danh mục, Tác giả, Ngày tạo/cập nhật, Trạng thái); tìm theo tiêu đề/nội dung; lọc theo danh mục/trạng thái/tác giả/khoảng thời gian; sort; bulk action (xóa, gỡ, lưu trữ); phân trang 20/50/100.

**Audit log bài viết**: lịch sử đầy đủ (thời gian, người, hành động); so sánh phiên bản (diff); rollback về bản cũ.

### CN-01.2. Quản lý Danh mục nội dung (Cao)
- Cây danh mục tối thiểu 3 cấp (Menu chính → Submenu → Chuyên mục con).
- Thuộc tính: Tên, Slug, Danh mục cha, Mô tả, Ảnh đại diện, Thứ tự, Trạng thái Hiện/Ẩn.
- UI quản trị: tree/accordion thu gọn – mở rộng.

### CN-01.3. Quản lý Thư viện Media (Trung bình)
- Thư mục phân cấp tối đa 3 cấp (Ảnh / Video / Tài liệu); tạo, đổi tên, xóa (chỉ khi rỗng), di chuyển; hiển thị dung lượng + số file.
- Multi-upload kéo-thả, progress bar từng file; auto nén ảnh sang WebP (giữ bản gốc fallback); auto thumbnail 150/400/800px.
- Giới hạn: Ảnh (JPG/PNG/GIF/WebP/SVG) 10MB; Video (MP4/WebM) 500MB — khuyến nghị YouTube embed; Tài liệu (PDF/DOC/DOCX/XLS/XLSX/PPT) 50MB; ZIP 100MB (auto giải nén nếu chứa ảnh).
- Quản lý: Grid/List view; tìm theo tên, lọc theo loại/thư mục/ngày; chi tiết file + copy URL 1 click; xóa có xác nhận + cảnh báo nếu file đang được bài viết tham chiếu.

### CN-01.4. Quản lý Liên hệ (Trung bình)
- Form public: Họ tên, Email, SĐT, Chủ đề, Nội dung + Google reCAPTCHA v3; bật/tắt từng trường, đặt bắt buộc.
- Email notify khi có liên hệ mới (nhiều người nhận); auto email xác nhận cho người gửi.
- Danh sách liên hệ: trạng thái Mới / Đã đọc / Đang xử lý / Đã xử lý / Lưu trữ; ghi chú nội bộ; export Excel; không cho xóa khi 'Đang xử lý'.

### CN-01.5. Quản lý Cấu hình giao diện (Trung bình)

**Banner/Carousel**: ảnh (khuyến nghị 1920×600), tiêu đề overlay, mô tả, link, thứ tự (kéo thả), Bật/Tắt, lịch hiển thị (ngày bắt đầu/kết thúc). Slider config: thời gian dừng (mặc định 5s), hiệu ứng Fade/Slide, autoplay, arrows/dots.

**Footer**: WYSIWYG; khối Thông tin công ty, Google Maps embed, Liên kết nhanh, Mạng xã hội (Facebook/Zalo/YouTube), copyright; có preview.

**Widget Thủy văn (public)**: chọn nhiều trạm + thông số (Mực nước, Lưu lượng, Nhiệt độ nước, Trạng thái cống); auto-refresh 5/10/15/30 phút; màu cảnh báo vàng/đỏ khi vượt ngưỡng; vị trí Sidebar/Banner dưới/Floating. Lấy dữ liệu qua REST API nội bộ MOD-02 (token auth); API lỗi → hiện "Không có dữ liệu" (không lộ lỗi kỹ thuật).

**Cấu hình chung website**: Tên site, slogan, logo (SVG/PNG), favicon 32×32, màu chủ đạo/phụ, GA Tracking ID, GTM Container ID, Maintenance Mode (bật/tắt + thông điệp).

**Menu điều hướng**: Header + Footer menu độc lập; item = Tên, Loại link (Danh mục/Bài viết/URL), mở tab mới, trạng thái, thứ tự; kéo thả sắp xếp + nested submenu.

**Trang đặc biệt**: Trang chủ (chọn khối: Slider, Bài nổi bật, Dịch vụ, Widget thủy văn); trang 404 custom; trang Tìm kiếm (phạm vi: bài viết/danh mục/tài liệu).

### CN-01.6. Quản lý Phản hồi/Bình luận (Thấp)
- Bật/tắt bình luận toàn site hoặc theo danh mục; mọi bình luận mới = 'Chờ duyệt', chỉ hiện sau duyệt.
- Thao tác: Duyệt / Từ chối / Xóa / Đánh dấu Spam; lọc spam tự động (Akismet hoặc tương đương); email notify khi có phản hồi cần duyệt.

---

## 2. MOD-02 — QUẢN LÝ VẬN HÀNH CÔNG TRÌNH THỦY LỢI

Lõi nghiệp vụ kỹ thuật. Người dùng: Operator, Kỹ thuật, Quản lý XN, Admin.
Công nghệ đặc thù: REST API polling, GIS (GeoJSON/KMZ), Async Job Queue.

### CN-02.1. Quản lý Danh mục Công trình (Cao)

**Phân cấp 4 bậc**: Công ty → Xí nghiệp → Cụm công trình → Công trình đơn lẻ (trạm bơm / cống / kênh mương).

**Hồ sơ Trạm bơm** — trường chính:
- Định danh: Mã CT (unique, VD `TB-SN-001`), Tên, Loại (Tưới/Tiêu/Hỗn hợp), Xí nghiệp (FK, bắt buộc), Cụm (FK), Địa chỉ, Tọa độ Lat/Lng Decimal(9,6) (bắt buộc — dùng GIS), Năm XD, Năm sử dụng, Đơn vị thiết kế/thi công, Tổng vốn (triệu VND).
- Thông số kỹ thuật: Công suất tổng (kW), Số máy bơm + dự phòng, Lưu lượng thiết kế/máy (m³/s), Cột nước (m), **Lưu lượng tổng = SL máy × LL/máy (auto)**, Diện tích tưới tiêu (ha), Nguồn điện, Điện áp (kV), **Ngưỡng MN vận hành min/max** (dừng máy nếu MN < min; cảnh báo nếu > max).

**Hồ sơ Cống điều tiết** — trường chính: Mã (`CG-SN-001`), Tên, Loại (Hộp/Tròn/Van phẳng/Clape), XN, Tọa độ, Số khoang, Khẩu độ/khoang (m), Cao trình ngưỡng/đỉnh (hệ cao độ quốc gia), Lưu lượng thiết kế (m³/s), Thiết bị đóng mở (Thủ công/Điện/Thủy lực), **Ngưỡng MN thượng lưu cảnh báo (vàng) / nguy hiểm (đỏ)**.

**Tài liệu đính kèm** (tab riêng mỗi công trình): Quy trình vận hành, Phương án bảo vệ, Hồ sơ hoàn công (PDF/DWG), Biên bản kiểm tra, Hợp đồng bảo trì, Ảnh thực địa.
- Multi-upload PDF/DOC/DOCX/DWG/JPG/PNG; 50MB/file, 500MB/công trình; gán nhãn loại + ngày lập + ngày hết hiệu lực; versioning tự động; phân quyền xem/tải theo XN.

**Trạng thái công trình**: Hoạt động (xanh) / Đang bảo trì (vàng) / Sự cố–Ngừng (đỏ) / Ngừng mùa vụ (xám) / Đã thanh lý (đen) — quyết định màu marker GIS.

### CN-02.2. Giám sát Dữ liệu Thủy văn (Cao)

**Luồng dữ liệu**: Trạm quan trắc (RTU/DataLogger) → Telemetry Server bên thứ 3 → **MOD-02 polling REST API theo chu kỳ 15–30'** → validate + chuẩn hóa đơn vị → DB time-series → phân phối cho Dashboard, Widget CMS, Alert.

**Cấu hình nguồn API** (chỉ Admin): Tên nguồn, URL endpoint, Xác thực (API Key/Bearer/Basic/OAuth2 — **credential mã hóa AES-256, không hiển thị plaintext**), Chu kỳ polling (min 5', mặc định 15'), Timeout (mặc định 30s), Retry (mặc định 3 lần, exponential backoff 5→10→20'), Trạm gắn nguồn (multi-select), Bật/Tắt.
- Sau 3 lần retry thất bại liên tiếp: đánh dấu trạm OFFLINE + email alert Admin.

**Thông số thu thập**: H_TL, H_HL (cm/m), Lượng mưa lũy kế P (mm), Mưa tức thời P_r (mm/h), Lưu lượng Q (m³/s), Nhiệt độ nước, Độ đục (tùy chọn), Trạng thái thiết bị (Online/Offline/Warning/Error), Timestamp (UTC+7, lưu Unix timestamp).

**Lưu trữ**:
- Raw data lưu bảng log riêng (audit + tái xử lý). Dữ liệu chuẩn hóa lưu time-series, index `(station_id, timestamp)`.
- Retention: chi tiết 5 năm; tổng hợp ngày vĩnh viễn; >2 năm chuyển Cold Storage (nén) nhưng vẫn truy vấn được.
- Hỗ trợ nhập tay khi API gián đoạn, đánh dấu nguồn = 'Manual'.

**Giao diện**:
- Bảng realtime toàn trạm, auto-refresh 5'; màu ô Xanh/Vàng/Đỏ theo ngưỡng; badge OFFLINE nếu bản ghi cuối > 1 giờ.
- Line chart (ECharts/Highcharts): multi-trạm so sánh, chọn thông số, khoảng thời gian (24h/7d/30d/custom); vẽ đường ngưỡng cảnh báo (nét đứt vàng/đỏ); tooltip chi tiết; export PNG/SVG/CSV.
- Lọc: XN, Cụm, Trạm, Thời gian, Trạng thái trạm.

### CN-02.3. Cảnh báo Ngưỡng Thủy văn (Cao)

**Cấu hình theo từng công trình, từng thông số** (H_TL, H_HL, Q, mưa):
- 3 mức: Bình thường (xanh) / Warning (vàng, gửi thông báo) / Critical (đỏ, cảnh báo khẩn).
- Loại điều kiện: `>`, `<`, ngoài khoảng [min,max], tốc độ thay đổi (delta/giờ).
- Delay chống nhiễu: chỉ kích hoạt khi điều kiện duy trì liên tục X phút.

**Kênh phát**:

| Kênh | Mức | Ghi chú |
|---|---|---|
| Dashboard banner | Warning + Critical | Yêu cầu xác nhận đã đọc |
| Email | Warning + Critical | Kèm link màn hình công trình; danh sách nhận theo công trình |
| SMS | Critical only | ESMS/Twilio, tin ngắn |
| Web Push in-app | Warning + Critical | Cần user cấp quyền browser |

**Lịch sử cảnh báo**: công trình, thông số, giá trị, mức, thời gian bắt đầu/kết thúc, người xác nhận, ghi chú xử lý; lọc theo mức/công trình/thời gian; phân loại Đang xảy ra / Đã xử lý / False Alarm; Kỹ thuật ghi 'Biện pháp xử lý' và đóng cảnh báo.

### CN-02.4. Bản đồ GIS (Trung bình)

- **Base map**: Google Maps (Satellite/Roadmap/Terrain/Hybrid), OpenStreetMap; zoom/pan/fullscreen/reset; geocoding search; lưu vị trí + zoom mặc định theo user.
- **Marker công trình**: màu theo trạng thái (xanh/vàng/đỏ/xám); icon theo loại (bơm/cống/kênh); zoom ≥ 14 hiện tên. Popup khi click: tên, mã, loại, XN, trạng thái, dữ liệu thủy văn mới nhất, trạng thái tổ máy; nút 'Xem chi tiết' + 'Xem biểu đồ'.
- **Layer management** (Admin/Kỹ thuật): upload GeoJSON/KMZ ≤20MB, auto parse + validate + báo lỗi chi tiết; thuộc tính layer: tên, màu, opacity 0–100%, geometry type; kéo thả z-index. Layer điển hình: kênh mương (LineString), ranh giới lưu vực/XN, vùng cảnh báo lũ (Polygon), quy hoạch (KMZ).
- **Điều khiển hiển thị**: panel toggle từng layer, slider opacity, 'chỉ hiện layer này'; nhớ trạng thái theo session.

### CN-02.5. Nhật ký Vận hành (Cao)

**Quy trình**: Operator chọn công trình (chỉ CT thuộc XN mình) → chọn ngày (mặc định hôm nay, **nhập bù tối đa 3 ngày trước**) → hệ thống check trùng ngày (sửa nhật ký cũ cần Admin duyệt) → nhập form → Lưu nháp hoặc Gửi → Quản lý XN duyệt.

**Form**:
- Thông tin chung: Công trình, Ngày, Ca (Ngày 6h-18h / Đêm 18h-6h / Cả ngày), Người vận hành, Thời tiết.
- Mỗi tổ máy: Số máy, Trạng thái (Chạy/Dừng/Bảo trì/Sự cố), Giờ BD–KT (bắt buộc khi Chạy), **Số giờ chạy = auto (KT–BD)**, Lưu lượng thực tế (m³/s), **Lưu lượng bơm = LL × giờ × 3600 (auto, m³)**, Điện năng (kWh), Ghi chú.
- Thủy văn: MN thượng lưu đầu/cuối ca (bắt buộc), MN hạ lưu đầu/cuối ca, Lượng mưa trong ca.
- Sự cố trong ca (checkbox): loại (Điện/Cơ khí/Thủy công/Thiên tai/Khác), mô tả, mức độ (Nhẹ/Trung bình/Nặng), biện pháp tạm, thời điểm, ảnh (≤5 ảnh × 5MB). **Mức 'Nặng' → auto tạo phiếu sự cố (CN-02.7) + notify Kỹ thuật.**

**Validation**:

| Trường | Quy tắc |
|---|---|
| Số giờ chạy | 0 < giờ ≤ 24 (hoặc độ dài ca) |
| Lưu lượng thực tế | > 0 và ≤ 120% lưu lượng thiết kế (vượt → yêu cầu xác nhận) |
| Mực nước | Trong dải [MN_min, MN_max] của công trình |
| Ngày nhập bù | Hôm nay − 3 ≤ ngày ≤ hôm nay |
| Giờ kết thúc | > giờ bắt đầu |

**Trạng thái duyệt**: Nháp → Chờ duyệt (khóa sửa) → Đã duyệt (dữ liệu chính thức, sửa cần Admin) / Từ chối (kèm lý do, Operator sửa gửi lại) / Yêu cầu sửa (bổ sung, không tạo mới).

### CN-02.6. Tổng hợp & Xuất Báo cáo (Cao)

**Danh mục báo cáo**: BC-01 Vận hành ngày; BC-02 Tuần; BC-03 Tháng; BC-04 Kết quả vụ tưới/tiêu; BC-05 Thủy văn tháng; BC-06 Cảnh báo & sự cố (PDF); BC-07 Kế hoạch vs thực hiện; BC-08 Tiêu thụ điện. Định dạng PDF + Excel.

**Tổng hợp tự động (cron)**: ngày 00:05, tuần T2 00:10, tháng ngày 1 00:15. Chỉ tiêu: Σ giờ chạy máy, Σ lưu lượng bơm (m³), Σ kWh, diện tích tưới/tiêu đạt, % hoàn thành kế hoạch, số ca có sự cố.

**Tạo thủ công + Async Job Queue**:
- User chọn loại BC, XN/công trình, kỳ, định dạng → POST → API trả **HTTP 202 + job_id** → job vào queue → Worker xử lý → xong: notify in-app + email kèm **link tải hiệu lực 24h**; lịch sử báo cáo lưu 90 ngày.
- Trạng thái job: Pending (cho hủy) → Processing (progress %) → Completed / Failed (sau 3 retry) / Cancelled.
- Template chuẩn công ty (header logo, footer số trang + người xuất, vị trí chữ ký); Admin upload template mới (.xlsx / .docx→PDF).

### CN-02.7. Quản lý Sự cố Công trình (Trung bình)

**Nguồn tạo phiếu**: auto từ nhật ký (sự cố Nặng); thủ công; từ màn hình cảnh báo.

**Phiếu sự cố**: Mã auto (`SC-2026-0001`), Công trình, Tiêu đề (≤200), Loại (Điện/Cơ khí/Thủy công/Thiên tai/An ninh/Khác), Mức độ (Critical/High/Medium/Low), Mô tả, Thời điểm + Người phát hiện, Ảnh/video (≤10 ảnh, ≤2 video × 100MB), Phân công (multi-user), Hạn xử lý.

**Vòng đời**: Mới → Đang xử lý → (Chờ vật tư | Khắc phục tạm) → Chờ nghiệm thu → Đã đóng (Quản lý/Admin) | Hủy (Admin).

**Nhật ký xử lý**: tab riêng, cập nhật tiến độ + media; mọi đổi trạng thái auto ghi log (timestamp, người) + email các bên liên quan.

### CN-02.8. Dashboard Vận hành (Trung bình)

- **KPI cards**: Tổng CT hoạt động/tổng (xanh nếu ≥90%), Đang vận hành, Cảnh báo đang xảy ra, Sự cố chưa xử lý, Nhật ký hôm nay nộp/cần nộp, Σ m³ bơm hôm nay.
- **Bản đồ GIS thu nhỏ** + link mở đầy đủ.
- **Chart & bảng**: cột lưu lượng 7 ngày theo XN; đường mực nước 24h trạm đang cảnh báo; 5 sự cố mới chưa xử lý; nhật ký chưa duyệt.
- Tối ưu chế độ trình chiếu màn hình lớn Phòng điều hành.

---

## 3. MOD-03 — QUẢN LÝ NHÂN SỰ (HRM)

Người dùng: Admin HR, Lãnh đạo, Quản lý phòng ban/XN, Nhân viên (Viewer).
**Tuân thủ**: Nghị định 13/2023/NĐ-CP (bảo vệ dữ liệu cá nhân), Bộ luật Lao động 2019, Luật Lưu trữ 2011. Trường nhạy cảm (CCCD, lương, tài khoản NH, MST, BHXH) phải mã hóa + phân quyền nghiêm ngặt.

### CN-03.1. Sơ đồ Tổ chức (Cao)
- Cây phân cấp ≥5 cấp: Công ty → Khối → Phòng/XN → Tổ/Đội/Cụm → Trạm.
- Đơn vị: Mã (unique, `PB-HC`, `XN-01`), Tên đầy đủ + viết tắt, Đơn vị cha (FK), Loại, Người đứng đầu (FK NV — 1 người chỉ đứng đầu 1 đơn vị) + Phó phụ trách, SĐT/Email đơn vị, Chức năng nhiệm vụ, Trạng thái (Hoạt động/Giải thể/Tạm dừng), Thứ tự.
- UI: Tree view (dọc/ngang, thu gọn/mở rộng, zoom/pan, fit-to-screen; nút hiện tên đơn vị + người đứng đầu + ảnh + SL nhân sự) và List view (lọc, tìm kiếm).
- Thao tác: thêm (cây tự cập nhật); di chuyển nhánh bằng kéo thả; **giải thể chỉ khi không còn nhân viên**, xác nhận 2 bước.
- Export: PNG/SVG, PDF A3 ngang, Excel.

### CN-03.2. Hồ sơ Cán bộ Nhân viên (Cao)
- **Thông tin cá nhân**: Mã NV (unique, `NV-2019-001`), Họ tên (+ bản không dấu auto cho full-text search), Ảnh (crop vuông 300×300), Ngày sinh (validate 18 ≤ tuổi ≤ 70), Giới tính, Dân tộc, Quê quán, **CCCD 9/12 số unique 🔒**, Ngày/Nơi cấp 🔒, Địa chỉ thường trú/tạm trú, SĐT, Email nội bộ/cá nhân, Hôn nhân, Liên hệ khẩn cấp.
- **Thông tin công tác**: Đơn vị (FK sơ đồ tổ chức), Chức vụ (FK danh mục), Chức danh, Ngày vào làm, Loại HĐLĐ (Thử việc/Xác định/Không thời hạn/CTV), Ngày ký + hết hạn HĐ, **Lương cơ bản, hệ số, STK ngân hàng, MST, số BHXH 🔒**, Trạng thái (Đang làm/Thử việc/Thai sản/Không lương/Nghỉ việc/Nghỉ hưu), Ngày + lý do nghỉ việc (bắt buộc khi trạng thái = Nghỉ việc).
- **Danh mục chức vụ chuẩn hóa** (Admin HR): Mã, Tên, Nhóm, Mô tả, Thứ tự xếp hạng — tránh trùng tên gọi, đảm bảo thống kê nhất quán.

### CN-03.3. Lý lịch & Chuyên môn (Cao)
- Học vấn cao nhất: Trình độ (THCS→Tiến sĩ), Chuyên ngành, Hình thức (Chính quy/Tại chức/…), Trường, Năm TN, Xếp loại, Số hiệu bằng.
- Lịch sử đào tạo bổ sung (nhiều bản ghi): khóa học, đơn vị, thời gian, hình thức, chứng chỉ, file.
- Bằng cấp/chứng chỉ (nhiều loại: hành nghề, kỹ năng nghề, ngoại ngữ, tin học, ATLĐ…): số hiệu, cơ quan cấp, ngày cấp, **ngày hết hiệu lực**, file đính kèm.
- Ngoại ngữ (chính + phụ, trình độ A1→C2/điểm), tin học, phần mềm chuyên dụng.

### CN-03.4. Lịch sử Công tác — Timeline (Trung bình)
- 10 loại sự kiện (Admin HR tạo): Tuyển dụng, Ký/gia hạn HĐ, Điều động, Bổ nhiệm/Miễn nhiệm, Nâng lương, Khen thưởng, Kỷ luật, Đào tạo, Nghỉ dài hạn, Nghỉ việc/hưu — mỗi loại ghi số QĐ, ngày hiệu lực, file gốc.
- UI: timeline dọc reverse-chronological, card có icon + file đính kèm; lọc theo loại/thời gian.

### CN-03.5. Tài liệu Hồ sơ Nhân viên (Cao)
- 7 thư mục cố định: Giấy tờ tùy thân (10MB), Bằng cấp (10MB), HĐLĐ (20MB), Quyết định nhân sự (10MB), Ảnh (5MB), Hồ sơ y tế (20MB), Khác (10MB).
- Multi-upload + progress; versioning (không ghi đè); preview PDF/ảnh trong trình duyệt; tải đơn file hoặc cả hồ sơ ZIP; ghi chú ngày cấp/hết hạn.
- **Cảnh báo hết hạn**: vàng < 90 ngày, đỏ đã hết hạn. **% hoàn thiện hồ sơ** + danh sách tài liệu thiếu.

### CN-03.6. Danh bạ Nội bộ (Cao)
- Mọi nhân viên có tài khoản tra cứu; chỉ hiện thông tin công khai nội bộ (không lộ dữ liệu nhạy cảm); chỉ NV 'Đang làm'.
- Grid/List toggle; card: ảnh, tên, chức vụ, đơn vị, SĐT, email. Sort: đơn vị → chức vụ → tên.
- Full-text search (có/không dấu, SĐT, email, đơn vị, chức vụ); realtime debounce 300ms, highlight từ khóa.
- Lọc multi-select đơn vị/chức vụ/giới tính. Trang chi tiết: nút gọi/gửi email, vị trí trong sơ đồ, 3–5 đồng nghiệp cùng đơn vị.

### CN-03.7. Thống kê & Báo cáo Nhân sự (Trung bình)
- KPI: tổng NV đang làm (biên chế/hợp đồng), tuyển mới + nghỉ việc tháng, tỷ lệ nghỉ việc, **HĐ hết hạn 30 ngày tới (cảnh báo vàng)**, chứng chỉ hết hiệu lực 90 ngày tới.
- Chart: NV theo đơn vị (cột ngang), giới tính/trình độ/loại HĐ (tròn), biến động 12 tháng (đường), độ tuổi + thâm niên (cột) — lọc theo đơn vị/kỳ.
- 8 báo cáo BCNS-01→08 (trích ngang, theo đơn vị, biến động, HĐ sắp hết hạn, cơ cấu, chứng chỉ sắp hết hạn, lý lịch mẫu 2C-BNV, tổng hợp năm) — Excel/PDF.

### CN-03.8. Quản lý Nghỉ phép (Trung bình)
- **Chính sách (Admin HR cấu hình)**: phép năm theo thâm niên (Điều 113 BLLĐ 2019: <5 năm = 12 ngày; 5–10 = 13; >10 = 14); phép đặc biệt (thai sản 180, cưới 3, tang 3, khám SK 1); chuyển tối đa N ngày sang năm sau (mặc định 5); danh sách ngày lễ (trừ tự động).
- **Quy trình**: NV đăng ký (loại, từ–đến) → hệ thống tự tính số ngày (trừ cuối tuần + lễ) + hiển thị số dư → cảnh báo nếu vượt phép (xác nhận nghỉ không lương) → gửi đơn → Quản lý duyệt/từ chối (email) → NV nhận kết quả → duyệt xong tự trừ số dư.
- **Số dư**: Được hưởng = thâm niên + chuyển năm trước; Còn lại = Được hưởng − Đã nghỉ − Đang chờ duyệt.
- **Lịch đơn vị**: Calendar view; cảnh báo trùng lịch khi > ngưỡng % quân số nghỉ cùng lúc (cấu hình, VD 50%).

---

## 4. MOD-04 — TÍCH HỢP HỆ THỐNG VĂN BẢN ĐIỀU HÀNH

- Công ty **đã có** hệ thống quản lý văn bản điều hành. Không xây mới — chỉ tích hợp vào cổng thông tin (SSO/link/embed + API nếu có).
- Yêu cầu: khảo sát API/khả năng tích hợp của hệ thống hiện có trong giai đoạn phân tích; chốt phương án trong SRS.

---

## 5. MOD-05 — QUẢN TRỊ HỆ THỐNG

- **Tài khoản & phân quyền**: RBAC (ma trận mục 6); tạo/khóa tài khoản, gán vai trò; liên kết tài khoản với hồ sơ nhân viên (MOD-03).
- **Xác thực**: Access token 30' + Refresh token rotation (httpOnly cookie), BCrypt; thu hồi qua denylist (bảng DB); hết hạn refresh → redirect login.
- **Cấu hình hệ thống**: thông số chung, tham số polling, template báo cáo, ngưỡng cảnh báo mặc định.
- **Audit log**: mọi thao tác tạo/sửa/xóa — user, timestamp, action, old/new value; giao diện tra cứu lọc theo user/module/thời gian.
- **Backup/Restore**: backup DB tự động hàng ngày, retention 30 ngày; quy trình khôi phục có tài liệu.

---

## 6. MA TRẬN PHÂN QUYỀN RBAC (MOD-02 — tham chiếu chính)

| Chức năng | Admin | Quản lý XN | Kỹ thuật | Operator |
|---|:-:|:-:|:-:|:-:|
| Xem danh mục công trình | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Thêm/Sửa hồ sơ công trình | ✔ | ✘ | ✔ | ✘ |
| Upload tài liệu công trình | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Cấu hình API nguồn dữ liệu | ✔ | ✘ | ✘ | ✘ |
| Cấu hình ngưỡng cảnh báo | ✔ | ✔ | ✔ | ✘ |
| Đóng/Xử lý cảnh báo | ✔ | ✔ | ✔ | ✘ |
| Upload/Quản lý layer GIS | ✔ | ✘ | ✔ | ✘ |
| Nhập nhật ký vận hành | ✔ | ✔ | ✔ | ✔ (XN mình) |
| Duyệt nhật ký | ✔ | ✔ | ✘ | ✘ |
| Xem nhật ký XN khác | ✔ | ✘ | ✔ | ✘ |
| Tạo phiếu sự cố | ✔ | ✔ | ✔ | ✔ |
| Phân công xử lý sự cố | ✔ | ✔ | ✔ | ✘ |
| Đóng phiếu sự cố | ✔ | ✔ | ✘ | ✘ |
| Xem báo cáo | ✔ | ✔ (XN mình) | ✔ | ✔ (XN mình) |
| Tạo/Xuất báo cáo | ✔ | ✔ | ✔ | ✘ |
| Cấu hình mẫu báo cáo | ✔ | ✘ | ✘ | ✘ |

CMS: Biên tập viên (tạo + gửi duyệt) < Trưởng ban biên tập = QTV nội dung < Admin (mục 1, CN-01.1).
HR: Admin HR full; Quản lý đơn vị xem NV đơn vị mình; NV chỉ xem danh bạ + đăng ký phép; trường 🔒 chỉ Admin HR.

---

## 7. YÊU CẦU PHI CHỨC NĂNG (NFR)

| # | Hạng mục | Yêu cầu | Tiêu chí đo |
|---|---|---|---|
| NFR-01 | Khả dụng | Uptime ≥ 99.5% giờ hành chính | Alert khi downtime > 15' |
| NFR-02 | Hiệu năng web | Tải trang < 3s/4G; API < 500ms; 100–300 users đồng thời | P95 dashboard < 3s @ 50 users |
| NFR-03 | Polling | Đúng chu kỳ cấu hình | Sai lệch < 10%; retry 3 lần + alert |
| NFR-04 | Báo cáo | BC 1 tháng/1 XN < 60s (async) | Job completed < 60s |
| NFR-05 | Bảo mật | HTTPS/TLS 1.3, CSP, rate limiting, scan malware file upload; API credential AES-256 | Không plaintext trong UI |
| NFR-06 | Phân quyền dữ liệu | Operator chỉ dữ liệu XN mình; trường 🔒 mã hóa | Unit + integration test 100% pass |
| NFR-07 | Audit | Log mọi tạo/sửa/xóa | user, timestamp, action, old/new value |
| NFR-08 | Lưu trữ | Hydro chi tiết 5 năm; backup hàng ngày retention 30 ngày | Không mất dữ liệu |
| NFR-09 | Tương thích | Chrome/Firefox/Edge/Safari; mobile; mạng 3G | Responsive 360px–2560px |
| NFR-10 | Pháp lý | NĐ 13/2023/NĐ-CP, BLLĐ 2019, Luật Lưu trữ 2011 | Áp dụng cho dữ liệu nhân sự |

---

## 8. HẠ TẦNG TRIỂN KHAI (PRODUCTION)

> Cập nhật theo `architecture-review.md` §6 (2026-07-21): **v1 triển khai 1 node**, thiết kế stateless để **thêm node 2 chỉ là đổi cấu hình**.

- 3 môi trường: Dev / Staging / Production. CI/CD rolling.
- **Nginx**: SSL termination + reverse proxy. V1 trỏ 1 App Server; thêm node = bổ sung upstream + bật ShedLock, không sửa code.
- **App Server ×1 (v1)**: Spring Boot API + Scheduler + Worker **in-process** (bounded pool). App **stateless** (không giữ state trong memory) → sẵn sàng nhân bản. Public web Next.js chạy riêng.
- **PostgreSQL 16 + PostGIS (1 node)**: là source of truth cho data + queue (SKIP LOCKED) + lock (ShedLock) + `hydro_latest`. **Backup**: pg_dump hàng ngày (retention 30 ngày) **+ WAL archiving (PITR, RPO ≤ 15')**, lưu khác đĩa/khác máy, kiểm thử restore định kỳ.
- **Không Redis**: session/denylist ở DB; site config cache Caffeine in-process. **MinIO**: media, tài liệu, file báo cáo (dùng ngay để app stateless).
- **Monitoring**: Prometheus + Grafana (uptime, latency, CPU/RAM); log JSON tập trung rotation 30 ngày.
- External: Telemetry API (HTTPS, key AES-256), SMTP, SMS Gateway (ESMS/Twilio), Google Maps API (optional).

---

## 9. LỘ TRÌNH TRIỂN KHAI (theo WBS)

1. **Khảo sát & thiết kế**: khảo sát quy trình vận hành tại trạm; thống kê mẫu báo cáo hiện có; chốt SRS; thiết kế UI/UX (public + admin dashboard + màn hình lớn); thiết kế DB (thủy văn, nhân sự, GIS) + phương án tích hợp API bên thứ 3.
2. **Phát triển**: MOD-01 → MOD-02 (danh mục CT + polling trước, GIS + báo cáo sau) → MOD-03 → MOD-04/05 song song.
3. **Kiểm thử**: chức năng, hiệu năng (theo NFR-02), bảo mật, tương thích.
4. **Triển khai**: setup hạ tầng 3 môi trường, go-live, đào tạo Admin + User, bàn giao mã nguồn + tài liệu Swagger/OpenAPI.

---

## PHỤ LỤC — QUY ƯỚC CHUNG CHO DEV

- **Mã định danh**: Công trình `TB-SN-xxx`/`CG-SN-xxx`; Sự cố `SC-<năm>-xxxx`; NV `NV-<năm>-xxx`; Đơn vị `PB-xx`/`XN-xx`.
- **Soft delete** cho mọi entity nghiệp vụ; audit log old/new value.
- **Timestamp**: lưu `timestamptz` UTC trong DB; convert UTC+7 chỉ ở tầng hiển thị. Không lưu theo giờ địa phương.
- **Kiểu số**: NUMERIC/BigDecimal cho mọi số đo (mực nước, lưu lượng, kWh) và tiền tệ; cấm float/double. Mọi giá trị tính toán (giờ chạy, lưu lượng bơm, tổng hợp kỳ, số dư phép) tính ở BE — FE chỉ hiển thị.
- **Màu trạng thái thống nhất**: Xanh = bình thường; Vàng = cảnh báo/bảo trì; Đỏ = nguy hiểm/sự cố; Xám = ngừng; Đen = thanh lý.
- **Upload**: mọi upload phải validate định dạng + dung lượng theo bảng từng module; scan malware; lưu MinIO.
- **API nội bộ**: REST, JWT Bearer; API public widget dùng token riêng; lỗi upstream → graceful degradation (hiện "Không có dữ liệu").


