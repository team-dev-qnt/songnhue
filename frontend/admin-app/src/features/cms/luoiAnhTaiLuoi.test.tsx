import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const get = vi.fn((..._args: unknown[]) => Promise.resolve([] as unknown));

vi.mock('@/shared/apiClient', () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    upload: vi.fn(() => Promise.resolve({})),
    delete: vi.fn(() => Promise.resolve()),
  },
  ApiClientError: class extends Error {},
}));

const { MediaBrowser } = await import('./MediaBrowser');

/**
 * **Lưới kho ảnh phải hoãn tải ảnh ngoài khung nhìn** — T12.7, lời hứa của T14.4.
 *
 * ## Vì sao ô này chứ ⛔ phải mọi thẻ ảnh
 *
 * Ảnh phái sinh (WebP + thumbnail 150/400/800 của CN-01.3) **vẫn đang hoãn**, nên thứ lưới này
 * hiển thị là **ảnh GỐC**. T14.4 đã viết ra hệ quả từ lúc dựng: *"lưới ảnh bắt buộc
 * `loading="lazy"` + khung CSS cố định, nếu không thì mở một thư mục 200 ảnh là tải về vài trăm
 * MB"*. Đo 24/09 trước bản vá: `grep -rn 'loading="lazy"' admin-app/src` = **0**.
 *
 * ⛔ Và ⛔ phải chỗ nào có ảnh cũng nên hoãn — đó là lý do lớp này ⛔ phải một luật quét toàn kho.
 * Sáu chỗ render ảnh của `admin-app` chia làm hai loại, và mỗi loại cần một quyết định ngược nhau:
 *
 * <ul>
 *   <li><b>Theo từng mục của một danh sách</b> — lưới kho ảnh, danh sách banner, logo trong cây
 *       menu ⇒ hoãn. Số ảnh tăng theo dữ liệu.
 *   <li><b>Một ảnh đơn</b> — ảnh đại diện trong biểu mẫu bài viết, ô xem trước trong hộp thoại ⇒
 *       <b>⛔ hoãn</b>. Ảnh ấy chính là thứ người dùng vừa mở ra xem; hoãn nó là làm chậm đúng
 *       nội dung duy nhất của màn hình. Một luật quét toàn kho sẽ ép sai ở đúng hai chỗ này.
 * </ul>
 *
 * ## ⛔⛔ Bài này ĐO việc antd chuyển tiếp prop, ⛔ tin vào nó
 *
 * `loading` ⛔ phải prop của `antd`. Nó đi lọt được là nhờ `rc-image` rải prop lạ xuống thẻ
 * `<img>` — một hành vi ⛔ ai hứa, và là thứ một lượt nâng antd có thể đổi trong im lặng. Đúng
 * hình dạng `T11.69`: Jackson 3 đảo một mặc định trong một bản nâng biên dịch sạch trơn.
 *
 * <p>Nên bài này khẳng định trên **thẻ `<img>` đã render**, ⛔ trên mã nguồn: nếu antd thôi rải
 * prop, thuộc tính biến mất khỏi DOM và bài đỏ — dù mã nguồn ⛔ đổi một ký tự.
 */

const THU_MUC = [
  { publicId: 'tm-1', name: 'Ảnh công trình', parentPublicId: null, depth: 0, sortOrder: 0 },
];

/** Ba tấm là đủ: bất biến ở đây là "MỌI ảnh của lưới", ⛔ phải một con số. */
const ANH = [1, 2, 3].map((i) => ({
  publicId: `anh-${i}`,
  originalName: `cong-trinh-${i}.jpg`,
  contentType: 'image/jpeg',
  sizeBytes: 1024 * i,
  createdAt: '2026-09-24T00:00:00Z',
}));

function dung() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MediaBrowser kho="MEDIA" loai="image" />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockImplementation((...args: unknown[]) => {
    const url = String(args[0]);
    if (url.includes('/folders/') && url.includes('/files')) return Promise.resolve(ANH);
    if (url.endsWith('/folders')) return Promise.resolve(THU_MUC);
    return Promise.resolve([]);
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('lưới kho ảnh hoãn tải ảnh ngoài khung nhìn', () => {
  it('⭐⭐ MỌI thẻ ảnh của lưới mang `loading="lazy"` trong DOM thật', async () => {
    dung();

    // ⛔⛔ Hỏi DOM bằng TÊN THẺ, ⛔ bằng `getByRole('img')`. Đo 24/09: mọi icon của antd là một
    //    `<span role="img">`, nên `getAllByRole('img')` trên màn hình này trả về 4 icon và
    //    **⛔ một thẻ `<img>` nào** — một bộ canh hỏi sai đối tượng sẽ khẳng định về mấy cái
    //    icon, thứ ⛔ bao giờ mang `loading` (luật 9: nó ⛔ phân biệt được hai trạng thái).
    await screen.findByText(ANH[0].originalName);
    const anh = [...document.querySelectorAll('img')];

    // Tiền đề (luật 7): lưới chưa nạp xong thì tập rỗng, và `filter` trên tập rỗng LUÔN cho
    // mảng rỗng — bài sẽ xanh mà ⛔ nhìn thấy một tấm ảnh nào.
    expect(anh.length, 'lưới phải render đủ ảnh thì phép đếm bên dưới mới có nghĩa').toBe(
      ANH.length,
    );

    const thieu = anh
      .filter((e) => e.getAttribute('loading') !== 'lazy')
      .map((e) => e.getAttribute('alt'));
    expect(
      thieu,
      `Thẻ ảnh ⛔ có \`loading="lazy"\`: ${thieu.join(', ')}\n\n` +
        'Hai nguyên nhân có thể, và chúng đòi hai việc khác hẳn nhau:\n' +
        '  1. `MediaBrowser` quên prop  ⇒ thêm lại.\n' +
        '  2. antd/`rc-image` THÔI rải prop lạ xuống `<img>` sau một lượt nâng ⇒ prop vẫn nằm\n' +
        '     nguyên trong mã mà ⛔ tới được DOM. Lúc ấy phải đổi sang thẻ `<img>` trần cho lưới,\n' +
        '     ⛔ phải sửa bài kiểm này.\n\n' +
        'Cái giá nếu bỏ qua: ảnh trong lưới là ảnh GỐC (ảnh phái sinh vẫn hoãn — T12.7), nên mở\n' +
        'một thư mục 200 ảnh là tải về vài trăm MB (T14.4).',
    ).toEqual([]);
  });
});
