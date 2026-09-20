import {
  brandColors,
  externalBrandColors,
  neutralColors,
  portalChrome,
  shadow,
  sizing,
  statusColors,
} from '@songnhue/design-tokens';
import type { Config } from 'tailwindcss';

/**
 * Cấu hình Tailwind dựng **từ** `design-tokens` — không có mã màu nào tự khai ở đây.
 *
 * Tailwind 4 khai theme bằng CSS (`@theme`), nhưng vẫn nhận cấu hình JS/TS qua chỉ thị
 * `@config` trong `globals.css`. Dùng đường đó là có chủ ý: khai lại năm màu trạng thái
 * bằng CSS custom property nghĩa là **hai bản sao** — mà năm màu đó mang nghĩa nghiệp vụ
 * (đỏ = sự cố đang mở, xám = trạm mất tín hiệu), nên hai bản sao lệch nhau là hai trang
 * cùng một hệ thống nói hai điều khác nhau về cùng một mức nghiêm trọng.
 */

/**
 * Bọc một token thành màu **đổi được lúc chạy**, giữ chính token làm giá trị dự phòng.
 *
 * <h3>Vì sao phải có tầng này — và vì sao nó ⛔ phải một nguồn màu thứ hai</h3>
 *
 * Tailwind **nướng mã hex vào CSS lúc build**: `bg-brand-primary` biên dịch thành
 * `background-color: #1758bf`. Một khoá `settings` vì thế ⛔ đổi được gì — đúng khuyết tật đã khiến
 * `site.color.*` bị gỡ ngày 28/08 (`V202608281037`): quản trị viên đặt giá trị, hệ báo *lưu thành
 * công*, cổng ⛔ đổi một pixel nào. Lần này đường đọc là **biến CSS**, tiêm ở `layout.tsx` từ
 * `getSiteConfig()` — lượt gọi vốn đã có sẵn ở đó, nên ⛔ thêm vòng khứ hồi nào.
 *
 * Giá trị dự phòng lấy **từ chính `design-tokens`**, ⛔ gõ lại: khoá để trống ⇒ ⛔ có biến ⇒ trình
 * duyệt rơi về đúng token. Một giá trị đi hai đường, ⛔ phải hai lời khai (quy tắc 14).
 *
 * ⚠ **Opacity modifier vẫn chạy** — `border-brand-primary/30` dùng ở 7 chỗ trong `public-web`.
 * Tailwind 4 biên dịch chúng bằng `color-mix(in oklab, <màu> 30%, transparent)`, mà `color-mix`
 * nhận `var()` như một màu bình thường. Ở Tailwind 3 (cú pháp `<alpha-value>`) vế này **vỡ trong
 * im lặng** — CSS hỏng ⛔ báo lỗi, chỉ mất viền. Đã đo trên CSS SINH RA chứ ⛔ suy từ tài liệu; phép
 * đo nằm ở `mauThuongHieu.test.ts`.
 */
const doiDuocLucChay = (bien: string, duPhong: string) => `var(--sn-${bien}, ${duPhong})`;

const config: Config = {
  // ⚠ Tailwind 4 TỰ dò nguồn từ thư mục dự án; mảng này chỉ THÊM vào, KHÔNG thu hẹp được. Đo
  //   ngày 29/08: cả mẫu phủ định ở đây lẫn `@source not` trong `globals.css` đều không loại
  //   được tệp kiểm khỏi phạm vi quét, nên cách duy nhất còn tác dụng là **đừng viết tên lớp
  //   thành một token liền mạch** ở nơi không phải giao diện — kể cả trong chú thích. Xem
  //   `vuaThanhNgang.test.ts`, chỗ mẫu vi phạm được ghép lúc chạy.
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Dùng như `text-status-danger`, `bg-status-normal`
        status: statusColors,
        // ⚠ Chỉ HAI vai trò của bộ nhận diện đổi được lúc chạy (`site.brand.*`). Các sắc dẫn xuất
        //   (hover, light, gradient) cố ý GIỮ token: suy chúng từ một màu người dùng vừa gõ là làm
        //   phép tính màu lúc chạy, và một lượt gõ nhầm sẽ kéo theo cả khung cổng.
        brand: {
          ...brandColors,
          primary: doiDuocLucChay('brand-primary', brandColors.primary),
          link: doiDuocLucChay('brand-primary', brandColors.link),
          accent: doiDuocLucChay('brand-accent', brandColors.accent),
        },
        surface: neutralColors,
        // Navy của khung cổng (đầu trang / chân trang) — `bg-chrome-navy800`.
        chrome: portalChrome,
        // Chỉ cho biểu tượng của chính nền tảng đó — `text-social-facebook`.
        social: externalBrandColors,
      },
      fontFamily: {
        sans: [sizing.fontFamily],
      },
      borderRadius: {
        DEFAULT: `${sizing.borderRadius}px`,
      },
      boxShadow: {
        sm: shadow.sm,
        md: shadow.md,
        lg: shadow.lg,
        // `shadow-card` — thẻ trắng nổi trên nền trắng của cổng công khai.
        card: shadow.card,
      },
      keyframes: {
        'sn-fade-in': {
          from: { opacity: '0', transform: 'translateY(8px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'sn-slide-up': {
          from: { opacity: '0', transform: 'translateY(16px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
      },
      animation: {
        'fade-in': 'sn-fade-in 0.5s cubic-bezier(0.4, 0, 0.2, 1) both',
        'slide-up': 'sn-slide-up 0.5s cubic-bezier(0.4, 0, 0.2, 1) both',
      },
      transitionTimingFunction: {
        smooth: 'cubic-bezier(0.4, 0, 0.2, 1)',
      },
    },
  },
};

export default config;
