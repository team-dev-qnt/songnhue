# Quy chuẩn UI Styles — Sông Nhuệ

> Tài liệu này là **nguồn tham chiếu duy nhất** khi viết hoặc sửa CSS/theme/styling cho cả
> `admin-app` (Vite + AntD 5) lẫn `public-web` (Next.js + Tailwind 4). Mọi quyết định
> về màu sắc, font, spacing, animation và cấu trúc layout đều phải nhất quán với các quy tắc dưới đây.

---

## 1. Triết lý thiết kế

**Chuyên nghiệp — Hài hoà — Nhận diện thương hiệu vững chắc — Dễ đọc.**

Đây là hệ thống quản trị và cổng thông tin điện tử của Doanh nghiệp Nhà nước quản lý & khai thác công trình thủy lợi (Sông Nhuệ). Giao diện phải truyền tải sự **đáng tin cậy**, **uy nghiêm**, và **rõ ràng**, phục vụ cả nhân sự nội bộ lẫn nhân dân tra cứu thông tin.

- **Đồng bộ tuyệt đối**: Màu sắc giữa Header, Footer và các khối nội dung phải hài hòa trong cùng một hệ quy chiếu màu thương hiệu (`brandColors`). Không dùng màu lệch tông (ví dụ: Header xanh mà Footer xám/đen rời rạc).
- **Không rườm rà**: Mỗi hiệu ứng (animation/transition) phải phục vụ mục đích rõ ràng (phản hồi thao tác, hướng dẫn mắt nhìn, phân tách vùng chức năng). Không thêm hiệu ứng bay nhảy thừa thãi.
- **Tiếp cận được (Accessibility)**: Đạt chuẩn tối thiểu WCAG AA (contrast ≥ 4.5:1 cho body text, ≥ 3:1 cho large text), focus visible rõ ràng bằng đường viền brand color, font-size ≥ 14px cho nội dung đọc chính.

---

## 2. Bảng màu & Design Tokens

### 2.1. Nguồn duy nhất: `design-tokens`

⛔ **Cấm khai màu cứng (hardcoded hex/rgb) tại chỗ trong page/component.** Mọi màu phải được định nghĩa trong `frontend/design-tokens/src/index.ts` rồi tiêu thụ qua:
- **Admin-app**: `antdTheme.ts` ➔ AntD `ConfigProvider`
- **Public-web**: `tailwind.config.ts` ➔ class Tailwind (`bg-brand-primary`, `text-surface-textBase`...)

### 2.2. Chi tiết các nhóm màu

| Nhóm Token | Màu / Giá trị | Ý nghĩa & Khi nào dùng |
|---|---|---|
| **`brandColors.primary`** | `#165bb6` (Royal Navy Blue) | Màu thương hiệu chính — Nút bấm, link, icon, thanh điều hướng (sắc xanh sông nước rõ nét) |
| **`brandColors.primaryHover`** | `#206cd2` (Blue Hover) | Trạng thái hover của nút và link |
| **`brandColors.gold`** | `#dbc373` (Gold Accent) | Màu vàng kim điểm nhấn từ hoa văn logo — Dùng cho hover menu, active bar, hotline |
| **`brandColors.primaryLight`** | `#c8def7` (Water Blue Light) | Nền gradient Masthead, nền hover bảng, nền submenu, badge nhẹ |
| **`brandColors.primaryGradientFrom`**| `#0c366e` (Deep Navy) | Điểm bắt đầu của dải gradient xanh thương hiệu |
| **`brandColors.primaryGradientTo`**  | `#1b64c0` (Medium Blue) | Điểm kết thúc của dải gradient xanh thương hiệu |
| **`statusColors.danger`** | `#cf1322` (Red 7) | Trạng thái BÁO ĐỘNG (cấp III), sự cố khẩn cấp |
| **`statusColors.warning`** | `#d46b08` (Orange 6) | Trạng thái CẢNH BÁO (cấp II), cần chú ý |
| **`statusColors.caution`** | `#d48806` (Gold 6) | Trạng thái CHÚ Ý (cấp I), chuẩn bị |
| **`statusColors.normal`** | `#389e0d` (Green 6) | Trạng thái BÌNH THƯỜNG, an toàn |
| **`statusColors.neutral`**| `#595959` (Neutral 7)| Trạng thái KHÔNG XÁC ĐỊNH, chưa có dữ liệu |
| **`neutralColors.textBase`** | `#1f1f1f` | Văn bản chính — tiêu đề, nội dung đọc chính |
| **`neutralColors.textSecondary`** | `#595959` | Văn bản phụ — ngày tháng, mô tả, chú thích |
| **`neutralColors.border`** | `#d9d9d9` | Đường viền bảng, card, input |
| **`neutralColors.bgLayout`** | `#f0f2f5` | Nền layout tổng thể của ứng dụng |
| **`neutralColors.bgContainer`** | `#ffffff` | Nền thẻ card, modal, dropdown |

> [!IMPORTANT]
> Toàn bộ 5 mã màu trong `statusColors` mang **ý nghĩa nghiệp vụ phân loại công trình và mực nước thủy lợi**. Tuyệt đối không thay đổi giá trị của nhóm này.

### 2.3. Quy chuẩn Gradient

- **Full Brand Blue Gradient** — dùng cho các khối *bên trong* trang (card accent, nút CTA):
  `from-brand-primaryGradientFrom to-brand-primary`.

- **Navy khung cổng** — thanh nhận diện, thanh điều hướng và chân trang của `public-web`:
  `bg-gradient-to-r from-chrome-navy800 via-chrome-navy500 to-chrome-navy800` (đầu trang) ·
  `bg-gradient-to-b from-chrome-navy700 via-chrome-navy600 to-chrome-navy900` (chân trang).

  > [!WARNING]
  > ⚠⚠ **Mục này TRƯỚC 28/08/2026 ghi một dải màu chưa từng chạy.** Bản cũ viết
  > `from-[#0c366e] via-[#165bb6] to-[#0c366e]` cho navbar, trong khi `SiteHeader` thật sự vẽ
  > `#061b37 → #0b2d5b`. Hai nguồn nói hai chuyện suốt 13 ngày và **đọc bên nào cũng thấy hợp
  > lý** — không có lượt kiểm nào đối chiếu chúng (đúng hình dạng quy tắc 14).
  >
  > Đã sửa **tài liệu theo mã**, không sửa mã theo tài liệu: §2 của văn bản nghiệm thu 27/8 chốt
  > *"hệ màu GIỮ NGUYÊN"*, nên bản đúng là bản người dùng đang nhìn thấy. Bảy sắc navy chép tay
  > gộp còn năm bậc `portalChrome.navy900…navy500` trong `design-tokens`.
  >
  > `frontend/public-web/src/lib/noHardcodedColors.test.ts` nay canh: **0 mã hex** được phép nằm
  > trong mã nguồn `public-web`. ⚠ `admin-app` còn 25 chỗ, ghi nợ ở `master-tracking.md` T25.23.
- **Masthead Blue Gradient**: Dùng cho tầng nhận diện đầu trang:
  `bg-gradient-to-r from-[#bfd9f8] via-[#d5e7fb] to-[#b4d3f6]`.
- **Auth/Login Banner Gradient**: Nền đăng nhập chuyển nhẹ nhàng từ xanh thương hiệu mờ sang xám nhạt (`#0c366e/10` ➔ `#f0f2f5`).
- **Card Accent Bar**: Dải màu mảnh 3px trên đầu card quan trọng (`h-1 bg-gradient-to-r from-[#0c366e] to-[#1b64c0]`).

### 2.4. Hệ thống Shadow (Độ nổi)

| Cấp độ | Token / Tailwind | Dùng cho |
|---|---|---|
| **Shadow SM** | `shadow-sm` (`0 1px 2px 0 rgb(0 0 0 / 0.05)`) | Card mặc định, input focus, thanh header |
| **Shadow MD** | `shadow-md` (`0 4px 6px -1px rgb(0 0 0 / 0.1)`) | Card khi hover, sticky navbar, nút CTA |
| **Shadow LG** | `shadow-lg` (`0 10px 15px -3px rgb(0 0 0 / 0.1)`) | Dropdown menu, Modal, Drawer, Login Box |

---

## 3. Typography

### 3.1. Font chữ: Noto Sans

Toàn bộ dự án (`admin-app` và `public-web`) thống nhất sử dụng **Noto Sans** để đảm bảo hiển thị tiếng Việt hoàn hảo, dấu thanh rõ ràng, không bị lỗi font hệ thống:

```css
font-family: 'Noto Sans', system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
```

> [!IMPORTANT]
> ⛔ **TỰ HOST, không lấy từ CDN Google.** Bản đầu của quy chuẩn này ghi
> `@import url('https://fonts.googleapis.com/…')`. Đổi sang gói `@fontsource/noto-sans` — **cùng
> bộ chữ, cùng trọng số, hình hiện ra y hệt**, chỉ khác nơi tải về. Ba lý do, lý do đầu là lý do chặn:
>
> 1. `conventions.md` §4.5 đã chốt CSP `default-src 'self'`. Khi WS-11 dựng nginx thì
>    `fonts.googleapis.com` và `fonts.gstatic.com` **bị chặn** — trang vẫn hiện, chỉ rơi về font hệ
>    thống, **không lỗi nào**. Loại hỏng im lặng chỉ lộ ra sau khi đã lên production.
> 2. Cổng của doanh nghiệp nhà nước gửi địa chỉ IP của **mọi người dân tra cứu** sang máy chủ Google
>    ở mỗi lượt tải trang. Cùng lý do đã chọn `youtube-nocookie.com` cho khối nhúng video: người đọc
>    không có cách nào từ chối.
> 3. `@import url(...)` ra mạng ngoài **chặn lượt vẽ đầu tiên**; NFR-02 chỉ cho 3 giây. Mạng nội bộ
>    Công ty chặn ra ngoài thì font không tải được mà không ai biết vì sao.

- **Quy tắc import trong CSS**: đặt ở **đầu tệp** (trước `@import 'tailwindcss'`) theo đúng chuẩn CSS spec — `@import` phải đứng trước mọi quy tắc khác:

  ```css
  @import '@fontsource/noto-sans/400.css';
  @import '@fontsource/noto-sans/500.css';
  @import '@fontsource/noto-sans/600.css';
  @import '@fontsource/noto-sans/700.css';
  @import '@fontsource/noto-sans/400-italic.css';
  @import '@fontsource/noto-sans/500-italic.css';
  ```

  ⚠ Khai **đúng những trọng số dùng tới**. Gói này có 144 tệp `woff2`; nhập cả gói là bắt người đọc tải về thứ không bao giờ hiện ra.

- ⛔ **Không khai font ở `index.html`.** Bản đầu nạp cùng một bộ chữ ở **hai** nơi (thẻ `<link>` trong `index.html` *và* `@import` trong CSS) — hai lượt tải cho một kết quả.
- **Trọng số font (Weight)**:
  - `400` (Regular): Nội dung đoạn văn, mô tả.
  - `500` (Medium): Nhãn trường, link menu, text quan trọng.
  - `600` (SemiBold): Tiêu đề mục, tiêu đề card, nút bấm.
  - `700` (Bold): Tiêu đề trang, tên cơ quan, số liệu lớn.

### 3.2. Cỡ chữ (Font Size)

| Phần tử | Cỡ chữ | Line-height |
|---|---|---|
| Văn bản thường / Body text | 14px (`text-sm`) | 1.6 – 1.75 |
| Văn bản phụ / Chú thích / Nhãn | 12–13px (`text-xs`) | 1.4 – 1.5 |
| Tiêu đề card / Heading mục nhỏ | 16–18px (`text-base` – `text-lg`) | 1.35 – 1.4 |
| Tiêu đề chuyên mục cổng | 20–24px (`text-xl` – `text-2xl`) | 1.3 |
| Tiêu đề trang / Banner chính | 24–30px (`text-2xl` – `text-3xl`) | 1.25 |

---

## 4. Kiến trúc Layout & Component cổng công khai (`public-web`)

### 4.1. Đầu trang (Header Architecture)

Đầu trang được xây dựng theo **cấu trúc 2 tầng chuẩn Cổng thông tin Nhà nước** trên nền **Full Brand Blue Gradient**:

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ [Logo SN Trắng] CÔNG TY TNHH MTV ĐẦU TƯ PHÁT TRIỂN THỦY LỢI SÔNG NHUỆ  [☎ Hotline]     │
│                 Thủy Lợi Sông Nhuệ                                                     │
├────────────────────────────────────────────────────────────────────────────────────────┤
│ Trang chủ   Giới thiệu ▾   Tin tức   Thông báo   Văn bản điều hành   Liên hệ   [🔍 Tìm]│
└────────────────────────────────────────────────────────────────────────────────────────┘
```

1. **Tầng 1 — Brand Masthead (Dải nhận diện)**:
   - Nền: `bg-gradient-to-r from-brand-primaryGradientFrom via-brand-primary to-brand-primaryGradientFrom text-white`.
   - Logo: Huy hiệu nền trắng nổi bật, bo tròn 12px với chữ `SN` màu xanh thương hiệu (`text-brand-primary`).
   - Tên cơ quan: In hoa đậm, font chữ sắc nét `text-white drop-shadow-xs`.
   - Khối Hotline: Badge bo tròn nền mờ `bg-white/10 border-white/20`, số điện thoại màu vàng sáng `text-amber-300`.
2. **Tầng 2 — Sticky Navigation Bar (Thanh điều hướng bám đỉnh)**:
   - Nền: `sticky top-0 z-40 w-full bg-[#0038a8]/95 backdrop-blur-md text-white shadow-md`.
   - Cơ chế sticky: Đặt trực tiếp trong React Fragment (`<>...</>`) để là con trực tiếp của `<body>`, bám cố định xuyên suốt khi cuộn trang.
   - Menu items: Chữ trắng `hover:bg-white/20`, dropdown đa cấp nền trắng `shadow-xl`, icon mũi tên `▾` xoay khi hover.
   - **Fallback Menu**: Bắt buộc định nghĩa `DEFAULT_HEADER_MENU` để khi build SSG hoặc API backend khởi động trễ, thanh menu luôn luôn hiển thị đầy đủ các mục điều hướng cốt lõi.

### 4.2. Chân trang (Footer Architecture)

Chân trang được thiết kế đồng bộ **Full Brand Blue Gradient** (`#003eb3` ➔ `#0958d9` ➔ `#002f8a`), chia làm 3 tầng:

1. **Tầng 1 — Dải tiếp nhận thông tin & Hotline bão lũ**:
   - Nền: `bg-[#002875]/80 py-3 text-white border-b border-white/10`.
   - Dấu chấm xanh nhấp nháy (`animate-pulse`) + Số hotline phòng chống thiên tai `(024) 3382 4586` (Trực ban 24/7) + Nút gửi phản ánh.
2. **Tầng 2 — Khối nội dung chính 4 cột**:
   - **Cột 1 (Cơ quan & Pháp lý)**: Logo SN trắng, Tên cơ quan, Huy hiệu *"Doanh nghiệp 100% vốn Nhà nước"*, Trụ sở, Điện thoại, Email, Giờ làm việc.
   - **Cột 2 (Nghiệp vụ thủy lợi)**: Vận hành cống, Cảnh báo xả lũ, Quản lý hồ đập, Cơ cấu Xí nghiệp, Hệ thống văn bản điều hành ↗.
   - **Cột 3 (Liên kết nhanh)**: Tự động nạp menu `FOOTER` từ CMS kèm fallback mặc định.
   - **Cột 4 (Kênh kết nối & Bản đồ)**: Thẻ mạng xã hội mờ `bg-white/10 border-white/20` hover lật nền trắng, khung bản đồ embed.
3. **Tầng 3 — Dải bản quyền đáy trang**:
   - Nền: `bg-[#002266] py-3.5 text-white/70 border-t border-white/10`.
   - Bản quyền chính thức + Cam kết: *"Ghi rõ nguồn Cổng thông tin Thủy lợi Sông Nhuệ khi phát hành lại thông tin từ website này."* + Phiên bản & Sơ đồ cổng.

### 4.3. Card bài viết & Danh mục (`ArticleCard.tsx`)

- Nền trắng `bg-white`, viền mảnh `border-surface-border`, bo góc `rounded-lg`, bóng đổ nhẹ `shadow-sm`.
- Hiệu ứng tương tác:
  - Khi hover: Nhấc nhẹ `-translate-y-1`, tăng bóng `hover:shadow-md`, viền chuyển sang màu thương hiệu `hover:border-brand-primary`.
  - Ảnh cover: Bo góc trong `overflow-hidden`, khi hover card phóng nhẹ `group-hover:scale-105 transition-transform duration-500`.
  - Tiêu đề: Đổi màu mượt sang `group-hover:text-brand-primary`.

### 4.4. Cấu trúc Bố cục Trang chủ (`public-web/src/app/page.tsx`)

Toàn bộ bố cục trang chủ tuân thủ mô hình Cổng thông tin Đa tầng.

⛔⛔ **Khối nào chưa có nguồn dữ liệu thì dựng `EmptyBlock` nói rõ lý do — CẤM mọi mảng dữ liệu
mẫu viết cứng.** Bản kế hoạch cũ (`docs/web-refactor.md`, đã xoá) yêu cầu ngược lại: *"xây dựng
các khối với Mock data dự phòng (Fallback an toàn khi API backend chưa cấp đủ endpoint)"* — và
gọi cơ chế ấy là **an toàn**. Nó không an toàn: một mảng RỖNG cho ra một trang chủ ĐẦY, nên đường
dữ liệu hỏng hoàn toàn trông y hệt đường dữ liệu chạy đúng. 19 bài viết, 4 văn bản có số hiệu và
người ký, 5 trạm thuỷ văn có mực nước và 9 số điện thoại đã lên staging theo đúng chỉ dẫn đó
(§10.54). `noFabricatedContent.test.ts` nay canh toàn cây.

Hiện đang là ô rỗng chờ đấu nối: thuỷ văn (MOD-03) · văn bản điều hành (CN-01.7) · thư viện ảnh ·
đơn vị trực thuộc (`org_units`) — xem T11.29 / T11.30.

1. **Khung chứa chính (Main Container)**: `max-w-[1240px] mx-auto px-4 sm:px-6`.
2. **Khối Hero & Dòng thời sự (Grid 8 : 4)**:
   - Cột 8: Tin đinh 16:9 với lớp phủ Gradient `from-black/70` + Lưới 3 tin phụ tiêu điểm.
   - Cột 4: Tab "Tin mới" & "Thông báo" kèm danh sách cuộn thời sự nóng.
3. **Dải Giám sát Thủy văn & Cảnh báo nhanh**:
   - Nền xanh dịu `bg-brand-primaryLight/50`, viền `border-brand-primary/20`.
   - Trạng thái 5 màu nghiệp vụ chuẩn từ `statusColors` (`normal`, `warning`, `danger`, `unknown`).
4. **Khối Chỉ đạo Điều hành & Văn bản**:
   - Vạch Accent Bar 3px trên đỉnh thẻ (`bg-gradient-to-r from-brand-primaryGradientFrom to-brand-primaryGradientTo`).
5. **Lưới Chuyên mục Dịch vụ Công ích**:
   - 4 Cột card dịch vụ với icon chuyên dụng, hover lift `-translate-y-1`.
6. **Truyền thông Đa phương tiện & Mạng lưới Liên kết**:
   - Nhúng video an toàn qua `youtube-nocookie.com`, thư viện ảnh công trình tiêu biểu, logo các Xí nghiệp thủy lợi trực thuộc.

### 4.5. Kiến trúc Bố cục Trang con (`Subpages Architecture`)

Tất cả các trang con (`danh-muc/[slug]`, `bai-viet/[slug]`, `tim-kiem`) được chuẩn hóa đồng bộ theo cấu trúc 2 cột hiện đại:

1. **Khung chứa & Breadcrumb Điều hướng (`Breadcrumb.tsx`)**:
   - Chiều rộng cố định: `max-w-[1240px] mx-auto px-4 sm:px-6 py-6 sm:py-8`.
   - Vị trí: Đặt ở đầu mọi trang con, dẫn từ `Trang chủ ➔ [Chuyên mục] ➔ [Tên bài viết]`.
   - Thiết kế: Chữ xám thanh lịch, hover xanh thương hiệu `#165bb6`, icon Home SVG sắc nét.
2. **Bố cục 2 Cột chuẩn Báo chí & Cổng Chính phủ (Grid 8 : 4)**:
   - **Cột Trái (8/12 - Main Content Area)**:
     - **Trang Danh mục (`danh-muc/[slug]`)**: Header chuyên mục có thanh nhấn xanh `h-6 w-1.5 bg-brand-primary`, mô tả danh mục, lưới bài viết 2 cột + Phân trang số trang bo góc.
     - **Trang Chi tiết Bài viết (`bai-viet/[slug]`)**: Category tag, Tiêu đề H1 font black, dải Metadata (ngày đăng, lượt xem, nhãn lưu trữ), Khối Sapo viền xanh `bg-sky-50/70 border-l-4 border-brand-primary`, ảnh cover bo góc 12px, nội dung `sn-article` đã khử độc, chân bài có nút Quay lại.
     - **Trang Tìm kiếm (`tim-kiem`)**: Khung tìm kiếm tích hợp icon kính lúp, số lượng kết quả tìm thấy, danh sách bài viết.
   - **Cột Phải (4/12 - `PortalSidebar.tsx`)**:
     - Khối Trực ban PCTT 24/7 với đèn đỏ nhấp nháy + số Hotline bấm gọi nhanh.
     - Khối Tin mới nhận / Dòng thời sự với huy hiệu thứ tự (Top 1 đỏ, Top 2-3 xanh).
     - Khối Tra cứu nhanh Văn bản điều hành & Dịch vụ công ích.

---

## 5. Kiến trúc Layout & Trang quản trị (`admin-app`)

### 5.1. Màn hình đăng nhập (`AuthShell.tsx`)

- Nền trang: Gradient nhẹ nhàng từ xanh thương hiệu sang xám nhạt (`bg-gradient-to-br from-brand-primaryGradientFrom/10 via-surface-bgLayout to-surface-bgLayout`).
- Khung đăng nhập:
  - Bo góc 12px, bóng đổ nổi `shadow-lg`, nền trắng.
  - Vạch accent gradient 3px ở đỉnh thẻ (`bg-gradient-to-r from-brand-primaryGradientFrom to-brand-primaryGradientTo`).
  - Animation `fade-in` êm ái khi tải trang.

### 5.2. Khung quản trị chính (`AdminLayout.tsx`)

- **Sider (Menu bên)**:
  - Nền tối trang nhã (Dark Sider) hoặc Nền sáng có bóng đổ `box-shadow: 2px 0 8px rgba(0, 0, 0, 0.05)`.
  - Khối Logo đỉnh sider có đường viền đáy phân cách và gradient xanh nhẹ.
- **Header (Thanh công cụ trên)**:
  - Nền trắng `bg-white`, bóng đổ mờ phân cách nhẹ thay vì dùng đường kẻ cứng.
- **Content Area**:
  - Nền `neutralColors.bgLayout` (`#f0f2f5`).
  - Trang con khi nạp vào Outlet có animation fade-in nhẹ (`opacity 0 ➔ 1`, `translateY 4px ➔ 0`).

---

## 6. Animation & Transition

### 6.1. Nguyên tắc cốt lõi

1. **Subtle & Purposeful**: Mọi hiệu ứng chuyển động không quá 500ms, chỉ dùng để phản hồi xúc giác thao tác người dùng.
2. **Không dùng thư viện cồng kềnh**: Sử dụng 100% CSS Transitions và `@keyframes` có sẵn (không dùng framer-motion/GSAP gây phình bundle).
3. **Tuân thủ `prefers-reduced-motion`**: Khi người dùng bật chế độ giảm chuyển động trong hệ điều hành, toàn bộ duration chuyển về `0.01ms` để không gây chóng mặt.

### 6.2. Các hiệu ứng chuẩn

| Tên hiệu ứng | CSS / Keyframes | Sử dụng cho |
|---|---|---|
| **Fade In** | `@keyframes sn-fade-in { 0% { opacity: 0; transform: translateY(8px); } 100% { opacity: 1; transform: translateY(0); } }` | Trang tải nội dung, Modal mở |
| **Hover Lift** | `transition: transform 300ms ease, box-shadow 300ms ease; transform: translateY(-4px);` | Card tin tức, Card danh mục, Button |
| **Image Zoom** | `transition: transform 500ms cubic-bezier(0.4, 0, 0.2, 1); transform: scale(1.05);` | Ảnh bìa bài viết khi hover |
| **Backdrop Blur** | `backdrop-filter: blur(8px);` | Sticky Navbar, Frosted Badges |

---

## 7. Checklist kiểm tra UI Styles trước khi Release

- [ ] **Màu sắc**: Tất cả mã màu đều xuất phát từ `design-tokens`, không có hardcoded hex lạ.
- [ ] **Đồng bộ Header - Footer**: Header và Footer có tone màu xanh thương hiệu gradient đồng nhất.
- [ ] **Fallback**: Menu Header và Footer có giá trị fallback dự phòng, không bị trắng khi mất kết nối backend.
- [ ] **Typography**: Sử dụng Noto Sans **tự host** (`@fontsource/noto-sans`), import ở đầu tệp CSS. ⛔ Không `@import url(...)` ra CDN — CSP `default-src 'self'` sẽ chặn, và trang rơi về font hệ thống mà không báo lỗi.
- [ ] **Contrast**: Độ tương phản chữ đạt chuẩn WCAG AA trên cả nền xanh lẫn nền sáng.
- [ ] **Motion**: Đã kiểm tra `prefers-reduced-motion` không gây lỗi layout.
- [ ] **Mobile Responsive**: Header, Footer và Card co giãn mượt mà trên màn hình nhỏ (< 640px).

