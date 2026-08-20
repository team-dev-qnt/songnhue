/**
 * Thông tin cố định của cổng thông tin và cấu hình đọc từ biến môi trường.
 *
 * ⚠ `NEXT_PUBLIC_SITE_URL` nhúng vào bundle lúc build. Nó là gốc của mọi URL tuyệt đối:
 * `sitemap.xml`, `robots.txt`, thẻ canonical, ảnh Open Graph. Đặt sai thì trang vẫn chạy
 * bình thường — chỉ có công cụ tìm kiếm và trình chia sẻ liên kết đọc ra địa chỉ sai, và
 * không ai phát hiện cho tới khi thấy kết quả tìm kiếm trỏ về `localhost`.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL ?? 'http://localhost:3000';

/**
 * Địa chỉ API mà **TRÌNH DUYỆT** gọi — nhúng vào bundle lúc build.
 *
 * Dùng cho những thứ người xem tải về: ảnh (`<img src>`), và lượt ping đếm view.
 */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1';

/**
 * Địa chỉ API mà **MÁY CHỦ NEXT** gọi khi dựng trang — đọc lúc chạy, không nhúng vào bundle.
 *
 * ⚠⚠ Hai địa chỉ này khác nhau trong Docker, và nhầm chúng là một lỗi im lặng đắt tiền:
 * trình duyệt thấy backend ở `http://localhost:18080`, còn tiến trình Next nằm *trong* mạng
 * Docker và phải gọi `http://app:8080`. Dùng địa chỉ của trình duyệt cho lượt gọi phía máy
 * chủ thì Next gọi vào chính container của nó, mọi lượt gọi hỏng, và cổng dựng ra **một
 * trang trắng hoàn toàn hợp lệ** — không lỗi 500, không dấu vết, chỉ là không có nội dung.
 *
 * Không có tiền tố `NEXT_PUBLIC_` là cố ý: địa chỉ nội bộ không cần và không nên đi xuống
 * trình duyệt.
 */
export const API_INTERNAL_BASE_URL = process.env.API_INTERNAL_BASE_URL || API_BASE_URL;

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
