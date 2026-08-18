import { brandColors, neutralColors, sizing, statusColors } from 'design-tokens';
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
    },
  },
};

export default config;
