import type { MenuLink } from '@/lib/api';
import { API_BASE_URL } from '@/lib/site';

/**
 * Sơ đồ đường dẫn của cổng — **một nơi duy nhất** biết URL trông như thế nào.
 *
 * <h3>Vì sao backend không trả sẵn `href`</h3>
 *
 * Cấu trúc URL là quyết định của giao diện, không phải của dữ liệu. Backend trả `linkType`
 * cộng `slug`; ghép thành đường dẫn là việc ở đây. Chốt `href` ở backend thì mỗi lần đổi
 * cấu trúc URL phải đi sửa Java, và hai bên lệch nhau lúc chỉ sửa một bên.
 *
 * <h3>Vì sao có tiền tố `/bai-viet/` và `/danh-muc/`</h3>
 *
 * Đặt bài viết thẳng ở gốc (`/ten-bai`) trông gọn hơn, nhưng khi đó slug bài viết tranh
 * chỗ với mọi đường dẫn khác của cổng: một bài đặt tên "tim-kiem" là mất trang tìm kiếm.
 * Va chạm kiểu đó không có thông báo nào và người sửa nội dung không thể lường trước.
 */

export const ROUTES = {
  home: '/',
  search: '/tim-kiem',
  article: (slug: string) => `/bai-viet/${slug}`,
  category: (slug: string) => `/danh-muc/${slug}`,
} as const;

/**
 * Địa chỉ công khai của một tệp — T16.6.
 *
 * ⚠ **Không phải presigned URL.** Presigned sống 10 phút, còn trang tĩnh sống hàng giờ:
 * trang dựng lúc 9h vẫn nằm trong bộ đệm lúc 11h và mọi ảnh trong đó đã chết. Đường dẫn
 * này ổn định vĩnh viễn vì `publicId` không đổi.
 */
export function fileUrl(publicId: string | null | undefined): string | null {
  return publicId ? `${API_BASE_URL}/public/files/${publicId}` : null;
}

/**
 * Đường dẫn của một mục menu.
 *
 * @returns `null` cho mục chỉ mở menu con (`NONE`) — nơi gọi render nó thành thẻ không bấm
 *   được, chứ không phải một liên kết trỏ về `#`.
 */
export function menuHref(item: MenuLink): string | null {
  switch (item.linkType) {
    case 'CATEGORY':
      return item.categorySlug ? ROUTES.category(item.categorySlug) : null;
    case 'ARTICLE':
      return item.articleSlug ? ROUTES.article(item.articleSlug) : null;
    case 'URL':
    case 'EXTERNAL_DOC':
      return item.url ?? null;
    case 'NONE':
      return null;
    default:
      return null;
  }
}

/** Mục dẫn ra khỏi cổng — cần `rel="noopener"` và biểu tượng báo cho người dùng. */
export function isExternal(item: MenuLink): boolean {
  const href = menuHref(item);
  return Boolean(href && /^https?:\/\//i.test(href));
}

/**
 * Dựng cây menu hai cấp từ danh sách phẳng.
 *
 * Backend đã sắp theo `path` nên cha luôn đứng trước con — một lượt duyệt là đủ, không cần
 * sắp lại.
 */
export function buildMenuTree(items: MenuLink[]): { item: MenuLink; children: MenuLink[] }[] {
  const roots: { item: MenuLink; children: MenuLink[] }[] = [];

  for (const item of items) {
    if (item.depth === 0) {
      roots.push({ item, children: [] });
      continue;
    }
    const parent = roots.find((r) => r.item.label === item.parentLabel);
    if (parent) {
      parent.children.push(item);
    } else {
      // Mục con mà không tìm thấy cha (cha đang tắt chẳng hạn) — nâng lên cấp gốc thay vì
      // bỏ đi. Mất một mục menu là thứ không ai phát hiện; thừa một mục thì thấy ngay.
      roots.push({ item, children: [] });
    }
  }
  return roots;
}

/** `dd/MM/yyyy` theo giờ Việt Nam — quy tắc 1 của dự án: lưu UTC, hiển thị UTC+7. */
export function formatDate(isoInstant: string | null | undefined): string {
  if (!isoInstant) {
    return '';
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    timeZone: 'Asia/Ho_Chi_Minh',
  }).format(new Date(isoInstant));
}
