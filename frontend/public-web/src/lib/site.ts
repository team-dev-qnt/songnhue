/**
 * Thông tin cố định của cổng thông tin và cấu hình đọc từ biến môi trường.
 *
 * ⚠ `NEXT_PUBLIC_SITE_URL` nhúng vào bundle lúc build. Nó là gốc của mọi URL tuyệt đối:
 * `sitemap.xml`, `robots.txt`, thẻ canonical, ảnh Open Graph. Đặt sai thì trang vẫn chạy
 * bình thường — chỉ có công cụ tìm kiếm và trình chia sẻ liên kết đọc ra địa chỉ sai, và
 * không ai phát hiện cho tới khi thấy kết quả tìm kiếm trỏ về `localhost`.
 *
 * ⚠⚠ Dùng `||` chứ **không** `??` — xem giải thích ở `API_BASE_URL` bên dưới. Bản đầu dùng
 * `??` và đã làm hỏng build: `Dockerfile` khai `ARG NEXT_PUBLIC_SITE_URL` không giá trị mặc
 * định, nên khi CI không truyền build-arg (biến kho `PUBLIC_SITE_URL` chưa đặt) thì `ENV`
 * gán vào một **chuỗi rỗng** — không phải "chưa đặt". `??` giữ nguyên chuỗi rỗng, và
 * `new URL('')` trong `layout.tsx` ném `ERR_INVALID_URL` giữa lúc prerender, giết cả lượt
 * `next build`. Chính lượt CI ấy còn in ra cảnh báo "chưa đặt PUBLIC_SITE_URL → sitemap sẽ
 * trỏ về localhost", tức là nó tin có một giá trị mặc định đang đỡ — mặc định chưa bao giờ
 * chạm tới.
 */
export const SITE_URL = process.env.NEXT_PUBLIC_SITE_URL || 'http://localhost:3000';

/**
 * Địa chỉ API mà **TRÌNH DUYỆT** gọi — nhúng vào bundle lúc build.
 *
 * Dùng cho những thứ người xem tải về: ảnh (`<img src>`), và lượt ping đếm view.
 *
 * ⚠⚠ Mặc định là đường dẫn **tương đối**, tức là cùng origin với trang; Next chuyển tiếp
 * sang backend bằng `rewrites()` trong `next.config.ts`. Bản đầu để mặc định là một origin
 * khác (`http://localhost:8080/api/v1`, và compose còn đặt `http://localhost:18080/api/v1`)
 * — trình duyệt khi đó gọi khác origin, mà backend **không cấu hình CORS**: preflight trả
 * thẳng `403 Invalid CORS request`.
 *
 * Hậu quả cụ thể: **bộ đếm lượt xem chưa từng chạy được từ trình duyệt thật**. Lượt kiểm ở
 * WS-16 gọi endpoint bằng `curl` nên đi qua — curl không làm preflight. Ảnh trong bài thì
 * vẫn hiện, vì thẻ `<img>` không chịu ràng buộc CORS; nên lỗi càng khó thấy.
 *
 * ⚠ Dùng `||` chứ **không** `??`: tệp compose truyền biến để trống sẽ nhúng vào bundle một
 * chuỗi rỗng, mà chuỗi rỗng không phải nullish nên `??` giữ nguyên nó.
 */
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || '/api/v1';

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
 *
 * ⚠⚠ Mặc định phải là một địa chỉ **tuyệt đối**, và cố ý **không** rơi về `API_BASE_URL` nữa:
 * biến kia nay là đường dẫn tương đối, mà `fetch('/api/v1/...')` ở phía máy chủ Next thì không
 * có gốc để nối — lượt gọi hỏng ngay, và trang dựng ra rỗng.
 */
export const API_INTERNAL_BASE_URL =
  process.env.API_INTERNAL_BASE_URL || 'http://localhost:8080/api/v1';

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
