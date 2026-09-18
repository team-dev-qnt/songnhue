/**
 * Liên kết lấy từ DỮ LIỆU (menu, `settings`) chỉ thành `href` khi an toàn — T61.34 (ASVS 5.3.3, tìm ra ở T61.28).
 *
 * <h2>Vì sao</h2>
 *
 * `menu_items.url` và các khoá `site.footer.social.*` · `site.external.doc-system-url` là chữ tự do do người
 * quản trị nhập; backend chỉ kiểm rỗng. Cổng đưa thẳng vào `href`, và **React 18.3.1 chỉ CẢNH BÁO chứ ⛔ chặn**
 * `javascript:` trong `href` ⇒ một mục menu `javascript:fetch(…document.cookie)` chạy khi người dân bấm.
 * Tiền lệ đúng đã có từ T26.63 (`nguonBaiViet.ts`) mà chưa nơi nào khác dùng.
 *
 * <h2>Danh sách CHO PHÉP</h2>
 *
 * `http:` · `https:` · `mailto:` · `tel:` · đường dẫn trong cổng bắt đầu bằng MỘT dấu `/` (⛔ `//host` — URL
 * tuyệt đối không giao thức) · neo `#…`. Mọi thứ khác ⇒ `null`, nơi gọi bỏ hẳn liên kết.
 */

const GIAO_THUC_CHO_PHEP = new Set(['http:', 'https:', 'mailto:', 'tel:']);

/** Trình duyệt bỏ ký tự điều khiển + khoảng trắng TRONG giao thức (`java\tscript:`) — đánh giá trên bản đã bỏ. */
// eslint-disable-next-line no-control-regex
const KY_TU_BI_BO = /[\u0000-\u0020]/g;

export function lienKetAnToan(url: string | null | undefined): string | null {
  const chu = (url ?? '').trim();
  if (chu === '') return null;
  const gon = chu.replace(KY_TU_BI_BO, '');
  if (gon.startsWith('#')) return chu;
  if (gon.startsWith('/')) {
    return gon.startsWith('//') || gon.startsWith('/\\') ? null : chu;
  }
  let giaoThuc: string;
  try {
    giaoThuc = new URL(gon).protocol.toLowerCase();
  } catch {
    return null; // tương đối kiểu `abc/xyz`, `\\host` … — ⛔ đoán
  }
  return GIAO_THUC_CHO_PHEP.has(giaoThuc) ? chu : null;
}
