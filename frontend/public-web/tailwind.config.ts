import { brandColors, neutralColors, shadow, sizing, statusColors } from 'design-tokens';
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
const config: Config = {
  content: ['./src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Dùng như `text-status-danger`, `bg-status-normal`
        status: statusColors,
        brand: brandColors,
        surface: neutralColors,
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
