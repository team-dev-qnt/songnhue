/**
 * Thông tin cố định của cổng thông tin và cấu hình đọc từ biến môi trường.
 *
 * ⚠ `NEXT_PUBLIC_SITE_URL` nhúng vào bundle lúc build. Nó là gốc của mọi URL tuyệt đối:
 * `sitemap.xml`, `robots.txt`, thẻ canonical, ảnh Open Graph. Đặt sai thì trang vẫn chạy
 * bình thường — chỉ có công cụ tìm kiếm và trình chia sẻ liên kết đọc ra địa chỉ sai, và
 * không ai phát hiện cho tới khi thấy kết quả tìm kiếm trỏ về `localhost`.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';

/** Địa chỉ API Core, đã gồm `/api/v1` (Phase 1 dùng để lấy bài viết, thủy văn). */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

export const SITE = {
  name: 'Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ',
  shortName: 'Thủy lợi Sông Nhuệ',
  description:
    'Cổng thông tin điện tử Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ — thông tin quản lý, vận hành công trình thủy lợi và số liệu thủy văn.',
  locale: 'vi_VN',
} as const;

/** Điều hướng chính. Phase 1 thay bằng danh mục lấy từ CMS; giữ tĩnh để có khung dựng trước. */
export const NAV_ITEMS: readonly { href: string; label: string }[] = [
  { href: '/', label: 'Trang chủ' },
  { href: '/gioi-thieu', label: 'Giới thiệu' },
  { href: '/tin-tuc', label: 'Tin tức' },
  { href: '/van-ban', label: 'Văn bản' },
  { href: '/thuy-van', label: 'Số liệu thủy văn' },
  { href: '/lien-he', label: 'Liên hệ' },
];
