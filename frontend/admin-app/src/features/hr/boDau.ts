/**
 * Bỏ dấu + hạ chữ thường — **bản JS của `sn_khong_dau(text)`** phía CSDL.
 *
 * ## ⛔⛔ Cùng một luật chuẩn hoá sống ở HAI nơi ⛔ không dùng chung mã được
 *
 * `sn_khong_dau` (PL/pgSQL, `V202608191014`) quyết định **ai được tìm thấy**; hàm này quyết định
 * **phần nào được tô sáng** (`ToSang`). Lệch nhau thì backend trả **đúng người** mà màn hình **⛔
 * không tô gì** — và người dùng đọc cái đó thành *"hệ thống tìm sai"*. Đây là luật 14 ở dạng ⛔
 * không gỡ được bằng mã: bù bằng `toSang.test.tsx`.
 *
 * ⛔ `đ`/`Đ` phải xử lý **riêng**: chúng ⛔ không phải `d` + dấu tổ hợp mà là một ký tự Unicode độc
 * lập, nên `NFD` ⛔ không tách chúng ra. Thiếu hai dòng ấy thì gõ `dieu` ⛔ không tô được `Điều`,
 * trong khi `unaccent` của Postgres **có** xử lý — tức hai vế lệch nhau đúng ở chỗ tiếng Việt dùng
 * hằng ngày.
 *
 * ⚠ Hàm này ở một tệp `.ts` RIÊNG, ⛔ không nằm cạnh component: quy tắc `react-refresh/only-export-components`
 * (bật ở mức **lỗi** trong kho này) cấm một module vừa export component vừa export hàm thường.
 */
export function boDau(s: string): string {
  return s
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .toLowerCase();
}
