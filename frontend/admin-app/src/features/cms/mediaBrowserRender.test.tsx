import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
 * **Bài kiểm ĐẦU TIÊN render `MediaBrowser`** — T40.28.
 *
 * ## Vì sao cái xanh hiện tại chưa nói gì về màn hình
 *
 * `khoTaiLieu.test.ts` canh ba thứ, và ⛔ không thứ nào là giao diện: khoá cache phân biệt hai kho,
 * tên tham số gửi xuống backend, và — nhóm thứ ba — **so khớp chuỗi trên mã nguồn**
 * (`doc('src/features/cms/MediaBrowser.tsx')`). Javadoc của chính nó tự khai:
 * *"Tới 04/09 ⛔ không bài kiểm nào render MediaBrowser"*.
 *
 * <p>Khoảng trống ấy có hình dạng cụ thể: **bộ lọc mới vô hiệu trên màn hình**. `tsc` bắt được đổi
 * tên prop, khoá cache bắt được tham số gửi sai — nhưng một `.filter()` trả sai vẫn đi lọt cả hai,
 * và người vận hành mở Kho tài liệu thấy đủ mọi tệp ảnh lẫn video trong một màn hình đáng lẽ chỉ có
 * tài liệu.
 *
 * ## ⚠ Hai lớp lọc CỐ Ý song song
 *
 * Bộ lọc chạy ở **cả hai** phía: backend lọc theo `kho`+`type`, trình duyệt lọc lại bằng `nhomCua`.
 * Chú thích trong component nói rõ đó là chủ đích — hai lớp thật sự độc lập (một Java, một trình
 * duyệt) nên ⛔ không cùng hỏng vì một lý do. Bài này đo **lớp trình duyệt**, tức lớp mà ⛔ không bộ
 * canh nào đang chạm tới: fixture cố ý trả về cả ảnh lẫn tài liệu, y như một backend đã hỏng.
 */

const THU_MUC = [
  { publicId: 'tm-1', name: 'Văn bản 2026', parentPublicId: null, depth: 0, sortOrder: 0 },
];

const TEP = [
  {
    publicId: 'f-1',
    originalName: 'quyet-dinh-123.pdf',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    createdAt: '2026-09-08T00:00:00Z',
  },
  {
    publicId: 'f-2',
    originalName: 'bao-cao-thang-8.docx',
    contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    sizeBytes: 4096,
    createdAt: '2026-09-08T00:00:00Z',
  },
  // ⛔ Một tấm ẢNH lẫn vào danh sách tài liệu — tức là backend đã lọc HỎNG. Lớp trình duyệt phải
  //    đỡ được, và đây chính là thứ ⛔ không bộ canh nào đang đo.
  {
    publicId: 'f-3',
    originalName: 'anh-cong-trinh.jpg',
    contentType: 'image/jpeg',
    sizeBytes: 1024,
    createdAt: '2026-09-08T00:00:00Z',
  },
];

function dung(props: Partial<Parameters<typeof MediaBrowser>[0]> = {}) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MediaBrowser kho="TAI_LIEU" loai="document" {...props} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockImplementation((...args: unknown[]) => {
    const url = String(args[0]);
    if (url.includes('/folders/') && url.includes('/files')) return Promise.resolve(TEP);
    if (url.endsWith('/folders')) return Promise.resolve(THU_MUC);
    return Promise.resolve([]);
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MediaBrowser — render thật', () => {
  it('⚠ dựng được và gọi API — tiền đề, thiếu nó thì mọi bài dưới xanh trên màn hình trắng', async () => {
    dung();
    await waitFor(() => expect(screen.getByText('Văn bản 2026')).toBeInTheDocument());
    expect(get).toHaveBeenCalled();
  });

  it('⭐⭐ bộ lọc LOẠI chạy trên màn hình — ảnh lẫn vào danh sách tài liệu phải bị loại', async () => {
    dung();
    await waitFor(() => expect(screen.getByText('quyet-dinh-123.pdf')).toBeInTheDocument());

    expect(screen.getByText('bao-cao-thang-8.docx')).toBeInTheDocument();
    // Đây là khẳng định chịu lực: `loai="document"` phải loại tấm ảnh, kể cả khi backend gửi nó lên.
    expect(
      screen.queryByText('anh-cong-trinh.jpg'),
      'Ảnh lọt vào Kho tài liệu ⇒ lớp lọc phía trình duyệt đã ngừng chạy, và ⛔ không bộ canh nào ' +
        'khác thấy được: khoá cache vẫn đúng, tham số gửi xuống vẫn đúng.',
    ).not.toBeInTheDocument();
  });

  it('⭐ ⛔ KHÔNG lọc khi `loai` bỏ trống — vế phân biệt hai trạng thái (luật 9)', async () => {
    // Thiếu vế này thì một `.filter()` luôn trả rỗng cũng làm bài trên xanh.
    dung({ loai: undefined });
    await waitFor(() => expect(screen.getByText('anh-cong-trinh.jpg')).toBeInTheDocument());
    expect(screen.getByText('quyet-dinh-123.pdf')).toBeInTheDocument();
  });

  it('⭐ lọc theo TÊN thu hẹp danh sách ngay trên màn hình', async () => {
    dung();
    await waitFor(() => expect(screen.getByText('quyet-dinh-123.pdf')).toBeInTheDocument());

    await userEvent.type(screen.getByPlaceholderText('Lọc theo tên tệp'), 'bao-cao');

    await waitFor(() => expect(screen.queryByText('quyet-dinh-123.pdf')).not.toBeInTheDocument());
    expect(screen.getByText('bao-cao-thang-8.docx')).toBeInTheDocument();
  });

  it('thư mục rỗng nói ra là rỗng — ⛔ không để một bảng trắng tự giải thích', async () => {
    get.mockImplementation((...args: unknown[]) => {
      const url = String(args[0]);
      if (url.includes('/folders/') && url.includes('/files')) return Promise.resolve([]);
      if (url.endsWith('/folders')) return Promise.resolve(THU_MUC);
      return Promise.resolve([]);
    });
    dung();
    await waitFor(() =>
      expect(screen.getByText('Thư mục này chưa có tệp nào')).toBeInTheDocument(),
    );
  });

  it('⛔ chưa có thư mục nào ⇒ nói rõ, ⛔ không phải một cột trống', async () => {
    get.mockImplementation(() => Promise.resolve([]));
    dung();
    await waitFor(() => expect(screen.getByText('Chưa có thư mục')).toBeInTheDocument());
  });
});

/**
 * Thao tác trên **nút cây thư mục** — T37.15.
 *
 * <p>Hai bài dưới đo hai thứ ⛔ không suy ra được từ nhau, và bài thứ hai là bài chịu lực.
 */
describe('MediaBrowser — ô thao tác của thư mục (T37.15)', () => {
  const HAI_THU_MUC = [
    { publicId: 'tm-1', name: 'Văn bản 2026', parentPublicId: null, depth: 0, sortOrder: 0 },
    { publicId: 'tm-2', name: 'Ảnh công trình', parentPublicId: null, depth: 0, sortOrder: 1 },
  ];

  beforeEach(() => {
    get.mockImplementation((...args: unknown[]) => {
      const url = String(args[0]);
      if (url.includes('/folders/') && url.includes('/files')) return Promise.resolve(TEP);
      if (url.endsWith('/folders')) return Promise.resolve(HAI_THU_MUC);
      return Promise.resolve([]);
    });
  });

  it('⚠ tiền đề — ô thao tác hiện trên MỌI thư mục, ⛔ không chỉ thư mục đang mở', async () => {
    dung({ renderFolderExtra: (folder) => <button>Xoá {folder.name}</button> });
    await waitFor(() => expect(screen.getByText('Xoá Văn bản 2026')).toBeInTheDocument());
    expect(screen.getByText('Xoá Ảnh công trình')).toBeInTheDocument();
  });

  it('⛔ ⛔ KHÔNG truyền `renderFolderExtra` ⇒ ⛔ không nút nào — vế phân biệt (luật 9)', async () => {
    // Thiếu vế này thì một cài đặt luôn vẽ nút cũng làm bài trên xanh, và hộp CHỌN ẢNH của màn
    // soạn bài sẽ mọc ra nút Xoá thư mục — nơi người dùng đang đi chọn ảnh, ⛔ không đi quản trị.
    dung();
    await waitFor(() => expect(screen.getByText('Ảnh công trình')).toBeInTheDocument());
    expect(screen.queryByRole('button', { name: /Xoá/ })).not.toBeInTheDocument();
  });

  it('⭐⭐ bấm nút thao tác ⛔ KHÔNG đổi thư mục đang mở — `Tree` để `blockNode`', async () => {
    // Bất biến chịu lực. `blockNode` biến CẢ DÒNG thành vùng bấm chọn, nên thiếu `stopPropagation`
    // thì mỗi lượt bấm "Xoá" đồng thời chuyển sang thư mục đó: hộp xác nhận hiện lên trong khi
    // lưới tệp bên phải đã đổi, và người dùng đọc số tệp của thư mục SAI rồi bấm đồng ý.
    //
    // ⭐ Đo bằng thứ QUAN SÁT ĐƯỢC — lượt gọi API cho thư mục thứ hai — chứ ⛔ không khẳng định
    //    một class nội bộ của AntD (luật 2: canh cấu trúc/hành vi, ⛔ đừng canh văn bản).
    const bam = vi.fn();
    dung({ renderFolderExtra: (folder) => <button onClick={bam}>Xoá {folder.name}</button> });
    await waitFor(() => expect(screen.getByText('Xoá Ảnh công trình')).toBeInTheDocument());

    const truoc = get.mock.calls.filter((call) => String(call[0]).includes('tm-2')).length;
    expect(
      truoc,
      'tm-1 phải là thư mục mở sẵn — nếu ⛔ không thì bài này ⛔ không đo được gì',
    ).toBe(0);

    await userEvent.click(screen.getByText('Xoá Ảnh công trình'));

    expect(
      bam,
      'nút phải nhận được cú bấm — ⛔ không thì "0 lượt gọi tm-2" là xanh giả',
    ).toHaveBeenCalledTimes(1);
    const sau = get.mock.calls.filter((call) => String(call[0]).includes('tm-2')).length;
    expect(
      sau,
      'Bấm nút thao tác đã kéo theo một lượt chọn thư mục — `stopPropagation` ở `MediaBrowser` ' +
        'đã mất tác dụng.',
    ).toBe(0);
  });

  it('⭐⭐ thư mục đang mở bị xoá ⇒ lưới tệp ⛔ THÔI hỏi id đã chết', async () => {
    // Nửa thứ hai của T37.15, và là nợ do chính lượt này tạo ra: từ lúc có nút Xoá, `folderId`
    // giữ trong state **sống lâu hơn** thư mục nó trỏ tới. Không lọc thì sau lượt xoá, lưới tệp
    // vẫn hỏi một thư mục đã biến mất — cây bên trái vẽ đúng, khung bên phải báo lỗi, và triệu
    // chứng đọc như "xoá xong thì hỏng".
    //
    // ⭐ Đo đúng bất biến: **⛔ không lượt gọi tệp MỚI nào mang id đã chết** sau khi danh sách
    //    thư mục đổi. Đo cái cây có hiện tên nữa không là đo một hệ quả hiển nhiên của dữ liệu.
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MediaBrowser kho="TAI_LIEU" loai="document" />
      </QueryClientProvider>,
    );
    await waitFor(() => expect(screen.getByText('Ảnh công trình')).toBeInTheDocument());

    await userEvent.click(screen.getByText('Ảnh công trình'));
    await waitFor(() =>
      expect(get.mock.calls.some((call) => String(call[0]).includes('tm-2'))).toBe(true),
    );

    // Thư mục tm-2 vừa bị xoá ⇒ lượt nạp lại danh sách ⛔ không còn nó.
    get.mockImplementation((...args: unknown[]) => {
      const url = String(args[0]);
      if (url.includes('/folders/') && url.includes('/files')) return Promise.resolve(TEP);
      if (url.endsWith('/folders')) return Promise.resolve([HAI_THU_MUC[0]]);
      return Promise.resolve([]);
    });
    get.mockClear();
    await queryClient.invalidateQueries({ queryKey: ['cms', 'folders'] });

    await waitFor(() => expect(screen.queryByText('Ảnh công trình')).not.toBeInTheDocument());
    expect(
      get.mock.calls.filter((call) => String(call[0]).includes('tm-2')),
      'Lưới tệp vẫn hỏi thư mục đã bị xoá — `conTonTai` ở `MediaBrowser` đã mất tác dụng.',
    ).toEqual([]);
    // Vế phân biệt: nó phải chuyển sang thư mục còn sống, ⛔ không phải ngồi im trên tập rỗng.
    expect(get.mock.calls.some((call) => String(call[0]).includes('tm-1'))).toBe(true);
  });
});
