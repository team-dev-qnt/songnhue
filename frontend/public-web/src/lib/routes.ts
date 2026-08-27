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

  /**
   * Bảy tuyến đường mà **menu trong CSDL trỏ vào bằng `linkType: 'URL'`** — đợt chỉnh sửa
   * 27/08/2026, CR-02 và CR-05.
   *
   * ⚠⚠ Hai nơi phải khớp: các chuỗi dưới đây và cột `menu_items.url` do
   * `V202608271031__cms_site_taxonomy_v2.sql` ghi. Lệch một ký tự là một mục menu trỏ vào
   * 404 — đúng hình dạng §10.54, nơi cổng quảng cáo những khu vực mà bấm vào là không có.
   * `portalRoutes.test.ts` đọc thẳng tệp migration rồi đối chiếu với bảng này (luật 14).
   */
  gioiThieu: {
    coCauToChuc: '/gioi-thieu/co-cau-to-chuc',
    lanhDao: '/gioi-thieu/lanh-dao',
    xiNghiep: '/gioi-thieu/xi-nghiep',
  },
  quanLyVanHanh: {
    danhMucCongTrinh: '/quan-ly-van-hanh/danh-muc-cong-trinh',
    tienDoSanXuat: '/quan-ly-van-hanh/tien-do-san-xuat',
    mucNuocLuongMua: '/quan-ly-van-hanh/muc-nuoc-luong-mua',
    vanHanhCongTrinh: '/quan-ly-van-hanh/van-hanh-cong-trinh',
  },
  lienHe: '/lien-he',
} as const;

/**
 * Liên kết Google Map của một công trình — cột "Vị trí" của bảng CR-28.
 *
 * ⚠ Dựng từ toạ độ chứ **không** đọc một cột `map_url` nào: hai nguồn toạ độ cùng tồn tại là
 * hai nguồn sẽ lệch, và một cột dẫn xuất trộn hai nguồn đúng là hình dạng lỗi đã trả giá ở
 * `ConstructionStatusService` (luật 13).
 *
 * @returns `null` khi chưa có toạ độ — nơi gọi hiện dấu gạch, không dựng liên kết trỏ vào
 *   giữa Đại Tây Dương (quy tắc 16).
 */
export function mapUrl(
  latitude: string | number | null | undefined,
  longitude: string | number | null | undefined,
): string | null {
  if (
    latitude === null ||
    latitude === undefined ||
    longitude === null ||
    longitude === undefined
  ) {
    return null;
  }
  return `https://www.google.com/maps/search/?api=1&query=${latitude},${longitude}`;
}

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

/**
 * `HH:mm dd/MM/yyyy` theo giờ Việt Nam — dòng "Cập nhật lúc" mà CR-35 bắt buộc có ở mọi khối
 * dữ liệu thời gian thực.
 *
 * ⚠ Định dạng viết đúng thứ tự tài liệu yêu cầu (giờ trước, ngày sau) chứ không phải thứ tự
 * mặc định của `Intl`. Nên phải ghép tay từ `formatToParts`; `format()` của locale `vi-VN`
 * trả `dd/MM/yyyy HH:mm`.
 *
 * @returns chuỗi rỗng khi không có mốc thời gian — nơi gọi phải hiện "chưa rõ", không được
 *   thay bằng giờ hiện tại của trình duyệt (quy tắc 16: một mốc bịa trông y hệt mốc thật).
 */
export function formatDateTime(isoInstant: string | null | undefined): string {
  if (!isoInstant) {
    return '';
  }
  const phan = new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour12: false,
    timeZone: 'Asia/Ho_Chi_Minh',
  }).formatToParts(new Date(isoInstant));

  const lay = (loai: Intl.DateTimeFormatPartTypes) =>
    phan.find((p) => p.type === loai)?.value ?? '';
  return `${lay('hour')}:${lay('minute')} ${lay('day')}/${lay('month')}/${lay('year')}`;
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
