# Kế hoạch & Quy chuẩn Tái cấu trúc Giao diện Cổng Thông tin Sông Nhuệ (`public-web`)

> **Tài liệu Kế hoạch & Hướng dẫn Thực thi (Plan & Refactoring Guideline)**
> Tham chiếu: `docs/ui-styles.md`, `frontend/design-tokens/src/index.ts`, mô hình Cổng thông tin điện tử chuẩn Quốc gia (tham chiếu bố cục từ Cổng TTĐT Bộ Công an `bocongan.gov.vn`, Bộ NN&PTNT).

---

## 1. Mục tiêu & Định hướng Thiết kế

### 1.1. Bối cảnh & Tầm nhìn
Cổng thông tin điện tử của **Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ** là bộ mặt đại diện của Doanh nghiệp Nhà nước quản lý hệ thống đại thủy nông phục vụ Thủ đô Hà Nội. 
Giao diện cổng cần thể hiện sự **chính quy, uy tín, minh bạch**, phục vụ:
1. **Người dân & Doanh nghiệp**: Tra cứu tin tức, thông báo lịch điều tiết nước, văn bản, gửi phản ánh và theo dõi diễn biến bão lũ/mực nước thủy văn.
2. **Cơ quan quản lý & Đối tác**: Theo dõi hoạt động chỉ đạo điều hành, vận hành hệ thống cống/trạm bơm đầu mối, liên kết công tác.

### 1.2. Đánh giá Hiện trạng & Khoảng cách (Gap Analysis)
* **Hiện trạng**: Giao diện trang chủ hiện tại (`src/app/page.tsx`) chỉ có một ảnh banner đơn lẻ cùng danh sách lưới bài viết dàn trải và các ô chuyên mục đơn giản; chưa có phân cấp thông tin cao cấp (Visual Hierarchy), thiếu khu vực đặc thù thủy lợi (giám sát mực nước, chỉ đạo điều hành), và chưa tối ưu không gian hiển thị.
* **Mục tiêu Refactor**: Tái cấu trúc trang chủ thành một **Cổng thông tin đa tầng (Multi-tier Portal)** chuyên nghiệp:
  * Phân tầng bố cục rõ ràng theo mô hình chuẩn 8 : 4 (Tin đinh tiêu điểm + Danh sách dòng thời sự).
  * Khối thông tin nghiệp vụ thủy nông & cảnh báo thiên tai nổi bật.
  * Khối chỉ đạo điều hành & văn bản quy phạm tách bạch.
  * Khối truyền thông đa phương tiện (Video/Hình ảnh công trình) và Liên kết đơn vị trực thuộc.
  * Giữ vững 100% quy chuẩn màu sắc, font chữ (`Noto Sans` tự host), accessibility và design tokens từ `ui-styles.md`.

---

## 2. Bố cục Không gian & Cấu trúc Khối Trang chủ (Layout Hierarchy)

Trang chủ được tái cơ cấu theo **6 phân vùng cốt lõi (Core Sections)** từ trên xuống dưới, nằm trọn trong container trung tâm `max-w-[1240px]` căn giữa:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ HEADER: Brand Masthead (Full Gradient) + Sticky Navigation Bar                         │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 1. KHỐI TIÊU ĐIỂM & DÒNG THỜI SỰ (Hero Grid 8 : 4)                                     │
│ ┌──────────────────────────────────────────┬─────────────────────────────────────────┐ │
│ │ CỘT TRÁI (8 CỘT):                        │ CỘT PHẢI (4 CỘT):                       │ │
│ │ • Tin đinh nổi bật (Cover 16:9 + Title)  │ • Tab "Tin mới nhất" / "Thông báo"      │ │
│ │ • 3 tin tiêu điểm phụ dạng lưới nhỏ      │ • Danh sách cuộn thời sự nóng           │ │
│ └──────────────────────────────────────────┴─────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 2. KHỐI THEO DÕI THỦY VĂN & CẢNH BÁO THIÊN TAI (Hydrology & Emergency Bar)             │
│ ┌────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ • Mực nước các trạm đầu mối (Hà Đông, Cầu Cung, Cổ Nhuế, Vân Đình...)              │ │
│ │ • Trạng thái màu chuẩn: Bình thường (Xanh) - Cảnh báo (Vàng) - Báo động (Đỏ)       │ │
│ │ • Hotline Trực ban PCTT & TKCN 24/7 (024. 3382 4586)                               │ │
│ └────────────────────────────────────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 3. KHỐI CHỈ ĐẠO ĐIỀU HÀNH & VĂN BẢN (Directives & Policy Section - Grid 6 : 6 / 8 : 4) │
│ ┌──────────────────────────────────────────┬─────────────────────────────────────────┐ │
│ │ • Hoạt động Chỉ đạo điều hành Ban Lãnh   │ • Văn bản chỉ đạo, Quyết định vận hành  │ │
│ │   đạo Công ty (Ảnh sự kiện + Tóm tắt)    │ • Lịch trực & Kế hoạch phân phối nước   │ │
│ └──────────────────────────────────────────┴─────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 4. LƯỚI CHUYÊN MỤC DỊCH VỤ CÔNG ÍCH (Category & Services Grid - 4 Cột)                 │
│ ┌───────────────┬────────────────┬────────────────┬──────────────────────────────────┐ │
│ │ Quản lý công  │ Phòng chống    │ Cải cách hành  │ Hướng dẫn dịch vụ                │ │
│ │ trình thủy lợi│ thiên tai      │ chính & Kê khai│ thủy lợi nội đồng                │ │
│ └───────────────┴────────────────┴────────────────┴──────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 5. TRUYỀN THÔNG ĐA PHƯƠNG TIỆN & HÌNH ẢNH (Media Hub & Photo Gallery)                  │
│ ┌──────────────────────────────────────────┬─────────────────────────────────────────┐ │
│ │ • Video phóng sự vận hành công trình     │ • Thư viện ảnh cụm đầu mối, trạm bơm    │ │
│ └──────────────────────────────────────────┴─────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ 6. LIÊN KẾT ĐƠN VỊ & CƠ QUAN CHỦ QUẢN (Unit Directory & Partner Network)               │
│ ┌────────────────────────────────────────────────────────────────────────────────────┐ │
│ │ • Các Xí nghiệp Thủy lợi trực thuộc (Hà Đông, Thanh Oai, Ứng Hòa, Phú Xuyên...)    │ │
│ │ • Cổng TTĐT: Bộ NN&PTNT, UBND TP Hà Nội, Sở NN&PTNT Hà Nội, Tổng cục Thủy lợi      │ │
│ └────────────────────────────────────────────────────────────────────────────────────┘ │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ FOOTER: 3 Tầng Xanh Thương hiệu Đồng bộ (Hotline -> 4 Cột Thông tin -> Bản quyền)     │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Chi tiết Từng Phân vùng & Đặc tả Giao diện

### Phân vùng 1: Khối Tiêu điểm & Dòng thời sự (Hero Grid 8 : 4)
* **Cột trái (Chiếm 8/12 cột - ~66.6%)**:
  * **Lead Article (Tin đinh)**:
    * Ảnh bìa tỉ lệ vàng 16:9 với lớp phủ Gradient nhẹ ở góc dưới (`from-black/70 via-black/20 to-transparent`).
    * Badge danh mục bo tròn màu xanh thương hiệu (`bg-brand-primary text-white`).
    * Tiêu đề cỡ lớn `text-xl sm:text-2xl font-bold text-surface-textBase hover:text-brand-primary`.
    * Tóm tắt bài viết 2 dòng (`line-clamp-2 text-surface-textSecondary`), ngày đăng định dạng tiếng Việt.
  * **Sub-Featured Grid (3 tin tiêu điểm bên dưới)**:
    * Bố cục 3 cột con (`grid-cols-1 sm:grid-cols-3 gap-4`).
    * Thumbnail 4:3 bo góc `rounded-md`, tiêu đề 2 dòng đậm `text-sm font-semibold`, nhãn thời gian mờ.
* **Cột phải (Chiếm 4/12 cột - ~33.3%)**:
  * Header khối dạng Tab: **"Tin mới nhất"** và **"Thông báo điều hành"**.
  * Danh sách tin cuộn với đường viền mảnh ngăn cách (`divide-y divide-surface-border`).
  * Mỗi tin gồm: Icon chỉ mục hoặc thời gian rút gọn (ví dụ: `15:30 21/08`), tiêu đề cô đọng, hiệu ứng hover đổi màu thương hiệu mượt mà.
  * Nút CTA dưới chân: *"Xem toàn bộ tin tức ➔"*.

---

### Phân vùng 2: Dải Giám sát Thủy văn & Cảnh báo Nhanh (Hydrology Bar)
* **Vị trí**: Đặt ngay sau khối Tin nóng, tạo điểm nhấn nghiệp vụ đặc trưng của ngành thủy nông.
* **Thiết kế**:
  * Nền xanh nhạt trang nhã `bg-brand-primaryLight/50 border border-brand-primary/20 rounded-xl p-4`.
  * Huy hiệu tiêu đề: Icon giọt nước/trạm đo + Chữ in hoa `text-brand-primary font-bold`.
  * Lưới thẻ các trạm đầu mối chính (Hà Đông, Cầu Cung, Cổ Nhuế, Vân Đình, Đồng Quan...):
    * Tên trạm + Mực nước hiện tại (m) + Trạng thái an toàn.
    * Đèn tín hiệu trạng thái tuân thủ 100% `statusColors` trong `ui-styles.md`:
      * 🟢 `normal` (`#52c41a`): Mực nước dưới báo động I.
      * 🟡 `warning` (`#faad14`): Báo động I – Báo động II.
      * 🔴 `danger` (`#f5222d`): Báo động III / Xả lũ khẩn cấp.
      * ⚪ `unknown` (`#8c8c8c`): Đang hiệu chuẩn hoặc chờ tín hiệu.
  * Khối Trực ban PCTT bên phải: Số hotline `(024) 3382 4586` nổi bật với icon điện thoại và nhịp nhấp nháy nhẹ `animate-pulse`.

---

### Phân vùng 3: Khối Chỉ đạo Điều hành & Văn bản (Directives & Documents)
* **Cột 1 — Hoạt động Chỉ đạo của Lãnh đạo (6/12 hoặc 7/12 cột)**:
  * Khung viền thanh lịch có vạch Accent Bar 3px trên đỉnh (`bg-gradient-to-r from-brand-primaryGradientFrom to-brand-primaryGradientTo`).
  * Bài viết chỉ đạo kèm ảnh đồng chí Lãnh đạo Công ty / Hội nghị triển khai công tác.
* **Cột 2 — Hệ thống Văn bản & Lịch công tác (6/12 hoặc 5/12 cột)**:
  * Danh sách văn bản mới ban hành: Số hiệu (VD: `158/QĐ-SN`), Ngày ban hành, Trích yếu nội dung.
  * Nút tải file đính kèm nhanh (`PDF / DOCX`) hoặc link mở trực tiếp sang hệ thống văn bản điện tử (`songnhue.bhh40.net`).

---

### Phân vùng 4: Lưới Chuyên mục Dịch vụ Công ích (Services & Functions)
* **Bố cục**: 4 cột trên Desktop (`grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5`).
* **Phong cách Card (`CategoryCard`)**:
  * Nền trắng `bg-white`, bo góc 10px, bóng nhẹ `shadow-sm`, viền `border-surface-border`.
  * Icon SVG minh họa độc quyền (Trạm bơm, Đê điều, Pháp lý, Thủ tục).
  * Hiệu ứng hover: Nhấc nhẹ `hover:-translate-y-1`, bóng đổ `hover:shadow-md`, viền chuyển sang xanh thương hiệu `hover:border-brand-primary`.
  * Mô tả ngắn gọn giúp người dân định hướng nhanh nhu cầu tra cứu.

---

### Phân vùng 5: Truyền thông Đa phương tiện & Thư viện Ảnh (Media Hub)
* **Bố cục**: Chia làm 2 phần (Video phóng sự 60% - Thư viện ảnh 40%).
* **Thành phần**:
  * **Video Player**: Khung phát video nổi bật, sử dụng domain bảo mật `youtube-nocookie.com` tuân thủ nghiêm ngặt CSP của hệ thống.
  * **Photo Gallery**: Lưới 4 ảnh thu nhỏ các công trình thủy lợi tiêu biểu (Cụm đập, cống qua đê, dòng chảy sông Nhuệ) với hiệu ứng phóng nhẹ ảnh bìa khi rê chuột (`group-hover:scale-105 transition-transform duration-500`).

---

### Phân vùng 6: Mạng lưới Đơn vị Trực thuộc & Cơ quan Liên kết
* **Danh sách các Xí nghiệp Thủy lợi Trực thuộc**:
  * Cụm thẻ logo/tên các đơn vị: *Xí nghiệp Thủy lợi Hà Đông, Thanh Oai, Ứng Hòa, Phú Xuyên, Nam Từ Liêm, Hoài Đức, Thường Tín, Thanh Trì...*
  * Hỗ trợ bấm vào để xem thông tin liên hệ, địa bàn phụ trách và bản đồ trực thuộc.
* **Liên kết Cơ quan Chỉ đạo**:
  * Banner/Logo đối tác: *Bộ Nông nghiệp & PTNT*, *UBND TP Hà Nội*, *Sở Nông nghiệp & PTNT Hà Nội*, *Cục Thủy lợi*.

---

## 4. Cấu trúc Component Kỹ thuật (Next.js App Router Architecture)

Để đảm bảo khả năng mở rộng, bảo trì và tái sử dụng, toàn bộ giao diện trang chủ được module hóa trong thư mục `frontend/public-web/src/components/home/`:

```
frontend/public-web/src/
├── components/
│   ├── SiteHeader.tsx                     # Giữ nguyên kiến trúc chuẩn 2 tầng
│   ├── SiteFooter.tsx                     # Giữ nguyên kiến trúc chuẩn 3 tầng
│   ├── ArticleCard.tsx                    # Card bài viết chuẩn dùng chung
│   ├── home/                              # [MỚI] Các component chuyên biệt trang chủ
│   │   ├── HomeHeroFeatured.tsx           # Khối tin đinh 16:9 + 3 tin phụ
│   │   ├── HomeLatestNewsFeed.tsx         # Cột tin mới & thông báo nhanh
│   │   ├── HydrologyQuickWidget.tsx       # Widget quan trắc mực nước & hotline
│   │   ├── DirectiveDocumentsSection.tsx  # Khối chỉ đạo điều hành & văn bản
│   │   ├── CategoryServicesGrid.tsx       # Lưới 4 chuyên mục dịch vụ công ích
│   │   ├── HomeMediaGallery.tsx           # Khối video & thư viện ảnh
│   │   └── AffiliatedUnitsLinks.tsx       # Liên kết Xí nghiệp & Cơ quan cấp trên
└── app/
    └── page.tsx                           # Trang chủ tích hợp cấu hình động site.home.blocks
```

---

## 5. Quy chuẩn Thiết kế Cập nhật (UI/UX Guidelines Alignment)

| Tiêu chí | Quy chuẩn Thống nhất | Ghi chú Kỹ thuật |
|---|---|---|
| **Màu thương hiệu chính** | `#0958d9` (Primary Blue) | `text-brand-primary`, `bg-brand-primary` |
| **Gradient nhận diện** | `#003eb3` ➔ `#0958d9` ➔ `#003eb3` | Áp dụng cho Masthead, Footer, Accent Bar |
| **Màu trạng thái Thủy văn** | 🟢 `#52c41a` \| 🟡 `#faad14` \| 🔴 `#f5222d` \| ⚪ `#8c8c8c` | Lấy từ `statusColors` trong `design-tokens` |
| **Typography** | Font **Noto Sans** (Self-hosted via `@fontsource/noto-sans`) | ⛔ Cấm import CDN Google để tuân thủ CSP |
| **Kích thước Container** | Chiều rộng chuẩn: `max-w-[1240px]` căn giữa | Padding: `px-4 sm:px-6` |
| **Card Styling** | Bo góc `rounded-xl`, viền `border-surface-border`, bóng `shadow-sm` | Hover: `hover:-translate-y-1 hover:shadow-md` |
| **Độ tương phản (A11y)** | Đạt chuẩn **WCAG AA** (Contrast ≥ 4.5:1 với chữ thường) | Focus visible `outline-2 outline-brand-primary` |
| **Chuyển động (Motion)** | Tối đa 300ms–500ms, hỗ trợ `prefers-reduced-motion` | Không dùng thư viện animation nặng ngoài CSS |

---

## 6. Lộ trình Triển khai (Implementation Steps)

1. **Bước 1: Khởi tạo các Component Trang chủ** (`src/components/home/*.tsx`):
   - Xây dựng các khối giao diện chuẩn hóa với Mock data dự phòng (Fallback an toàn khi API backend chưa cấp đủ endpoint chuyên biệt).
2. **Bước 2: Cập nhật Trang chủ Chính** (`src/app/page.tsx`):
   - Tích hợp các module mới theo thứ tự khối `site.home.blocks` linh hoạt từ backend config.
3. **Bước 3: Tối ưu Styling & Accessibility**:
   - Tinh chỉnh khoảng cách giữa các section (`my-8 sm:my-12`), kiểm tra tương thích Responsive trên Mobile/Tablet/Desktop.
4. **Bước 4: Kiểm thử & Đánh giá**:
   - Chạy test suite tự động (`npm test`), kiểm tra revalidate cache và rà soát các tiêu chuẩn trong `docs/ui-styles.md`.
