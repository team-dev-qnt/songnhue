import dayjs from 'dayjs';

/**
 * Định dạng ngày hiển thị của màn hình nghỉ phép.
 *
 * ⛔⛔ **Tệp riêng, ⛔ không nằm cạnh component** — `react-refresh/only-export-components` cấm một
 * tệp vừa export component vừa export hàm, và cổng `Frontend — lint` chạy với `--max-warnings 0`.
 * Đây là lỗi mà **cả `tsc` lẫn `vitest` đều ⛔ không thấy** (T55.8): tám bài kiểm xanh, typecheck
 * sạch, cổng CI đỏ. Cùng lý do `boDau.ts` phải tách khỏi `DanhBaPage.tsx`.
 *
 * ⚠ Chỉ nhận chuỗi `YYYY-MM-DD` của `LocalDate` — ⛔ không đổi múi giờ, vì một ngày nghỉ ⛔ không
 * có giờ. Đưa một `timestamptz` vào đây là mời lệch một ngày ở biên (quy tắc 1).
 */
export const ngayVn = (iso: string): string => dayjs(iso).format('DD/MM/YYYY');
