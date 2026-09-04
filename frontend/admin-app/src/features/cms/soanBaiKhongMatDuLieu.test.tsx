import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Phục hồi phiên bản phải làm mới màn hình** — WS-41 (T41.9, T41.12).
 *
 * <h2>Sự cố đo được 04/09/2026</h2>
 *
 * `ArticleEditorPage` dựng `<ArticleForm key={article.data.publicId}>` — một **hằng số suốt vòng
 * đời màn hình**. Còn `useState(data?.content)` và `initialValues` của AntD chỉ đổ dữ liệu vào
 * **lượt mount đầu** (`rc-field-form` chỉ `updateStore` khi `init === true`).
 *
 * Nên sau khi bấm *Phục hồi*, đo được:
 *
 * <pre>
 *   dữ liệu query đã đổi          = true
 *   DOM còn nội dung CŨ           = true
 *   DOM có nội dung ĐÃ PHỤC HỒI   = false
 * </pre>
 *
 * Backend `restoreInto` ghi đè **9 trường**, nên màn hình đứng yên hoàn toàn — và cú **Lưu** kế
 * tiếp gửi lại `content`/`documents`/`coverId` CŨ **đè lên bản vừa phục hồi**. Mất dữ liệu, im
 * lặng, không lỗi nào.
 *
 * <h2>⚠ Khẳng định phải là "nội dung MỚI xuất hiện", không phải "nội dung cũ biến mất"</h2>
 *
 * `queryByText(cũ)).not.toBeInTheDocument()` xanh trọn vẹn khi biểu mẫu **unmount** hoặc khi
 * component **sập** — hai trạng thái tệ hơn hẳn lỗi đang sửa. Chỉ một khẳng định khẳng định
 * (`findByText(mới)`) mới phân biệt được ba trạng thái ấy (quy tắc 9).
 */

const BAI_CU = '<p>NOI DUNG CU</p>';
const BAI_PHUC_HOI = '<p>NOI DUNG DA PHUC HOI</p>';

function chiTiet(content: string, title = 'Tiêu đề gốc') {
  return {
    publicId: 'bai-1',
    title,
    slug: 'tieu-de',
    summary: null,
    content,
    coverAttachmentPublicId: null,
    source: null,
    status: 'NHAP',
    publishedAt: null,
    reviewNote: null,
    metaTitle: null,
    metaDescription: null,
    metaKeywords: null,
    docNumber: null,
    docIssuedDate: null,
    viewCount: 0,
    publiclyVisible: false,
    // ⚠ Phải CÓ danh mục: biểu mẫu bắt buộc ít nhất một, và thiếu nó thì `validateFields()`
    //   reject — bài kiểm sẽ đỏ vì một lý do chẳng liên quan tới thứ nó đang đo.
    categoryPublicIds: ['dm-1'],
    documents: [],
    allowedActions: [{ action: 'SUBMIT', label: 'Gửi duyệt' }],
  };
}

/**
 * ⛔ Mock ở tầng `./api`, KHÔNG ở `@/shared/apiClient`.
 *
 * Mock `apiClient` thay luôn lớp `ApiClientError`, nên `caught instanceof ApiClientError` trong
 * `ArticleEditorPage` sẽ là `false` và nhánh xử lý lỗi theo trường **không bao giờ chạy** — bài
 * kiểm khi đó nói về một màn hình khác với màn hình thật.
 */
const nhanTuMayChu = { hienTai: BAI_CU };
const capNhat = vi.fn();

vi.mock('./api', () => ({
  cmsKeys: {
    article: (id: string) => ['cms', 'article', id] as const,
    categories: () => ['cms', 'categories'] as const,
    folders: () => ['cms', 'folders'] as const,
    files: (f: string | null) => ['cms', 'files', f] as const,
    versions: (id: string) => ['cms', 'article', id, 'versions'] as const,
    versionContent: (id: string, v: string) => ['cms', 'article', id, 'version', v] as const,
  },
  cmsApi: {
    getArticle: vi.fn(async () => chiTiet(nhanTuMayChu.hienTai)),
    categories: vi.fn(async () => [{ publicId: 'dm-1', name: 'Tin tức', depth: 0 }]),
    folders: vi.fn(async () => []),
    versions: vi.fn(async () => [
      {
        publicId: 'v1',
        versionNo: 1,
        createdAt: '2026-09-01T00:00:00Z',
        title: 'Bản 1',
        note: null,
        servingPublic: false,
      },
    ]),
    versionContent: vi.fn(async () => ({ content: BAI_CU })),
    restoreVersion: vi.fn(async () => chiTiet(BAI_PHUC_HOI, 'Tiêu đề đã phục hồi')),
    updateArticle: capNhat.mockImplementation(async () => chiTiet(BAI_PHUC_HOI)),
  },
}));

const { ArticleEditorPage } = await import('./ArticleEditorPage');

/**
 * ⚠ `AuthProvider` là **bắt buộc**: `useAuth()` **ném** khi thiếu context (cố ý — quên bọc là lỗi
 * lập trình, không phải một trạng thái). Thiếu nó thì màn hình không dựng và mọi bài kiểm đỏ với
 * *"Unable to find an element"* — một thông báo chỉ sai hướng hoàn toàn.
 *
 * Chỉ khai đúng phần `ArticleEditorPage` chạm tới (`hasPermission`); phần còn lại ném nếu bị gọi,
 * để một lượt gọi ngoài dự kiến **lộ ra** thay vì im lặng trả `undefined`.
 */
function boc(children: React.ReactNode, qc: QueryClient) {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này không dựng phần đó — khai thêm nếu cần');
  };
  const auth = {
    status: 'authenticated' as const,
    user: null,
    hasPermission: () => true,
    hasRole: () => true,
    maintenance: false,
    login: chuaKhai,
    verifyTwoFactor: chuaKhai,
    confirmEnrollment: chuaKhai,
    logout: chuaKhai,
    endSession: chuaKhai,
    reloadProfile: chuaKhai,
  } as unknown as AuthContextValue;

  return (
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>{children}</AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>
  );
}

/**
 * ⚠⚠ `createMemoryRouter` + `RouterProvider`, ⛔ KHÔNG `<MemoryRouter>`.
 *
 * `useChanRoiTrang` gọi `useBlocker`, và `useBlocker` **ném** khi không có `DataRouterContext`:
 * *"useBlocker must be used within a data router"*. `<MemoryRouter>` không cấp context ấy.
 *
 * Đây là tệp kiểm **đầu tiên** trong kho dùng data router — bốn tệp `.test.tsx` còn lại đều dùng
 * `<MemoryRouter>`, và chúng không sao vì không màn hình nào của chúng chặn điều hướng. Ghi lý do
 * ở đây để lượt sau không "sửa cho giống các tệp khác".
 */
function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const router = createMemoryRouter(
    [
      { path: '/noi-dung/bai-viet/:publicId', element: <ArticleEditorPage /> },
      { path: '/noi-dung/bai-viet', element: <div>Danh sách bài viết</div> },
    ],
    { initialEntries: ['/noi-dung/bai-viet/bai-1'] },
  );
  render(boc(<RouterProvider router={router} />, qc));
  return { qc, router };
}

/**
 * Bấm trọn đường Phục hồi: mở ngăn kéo → nút trong danh sách → nút xác nhận của `Popconfirm`.
 *
 * ⚠ Hai nút cùng tên "Phục hồi" cùng lúc (nút trong hàng và nút OK của Popconfirm), nên
 * `getByRole('button', { name: 'Phục hồi' })` ném *"Found multiple elements"*. Lấy theo **thứ tự
 * xuất hiện** thay vì đặt tên khác cho nút — đổi nhãn nút chỉ để bài kiểm dễ viết là để bài kiểm
 * lái sản phẩm.
 */
async function phucHoiBanDau(nguoiDung: ReturnType<typeof userEvent.setup>) {
  await nguoiDung.click(screen.getByRole('button', { name: /Lịch sử phiên bản/ }));
  await nguoiDung.click(await screen.findByRole('button', { name: 'Phục hồi' }));
  await waitFor(() =>
    expect(screen.getAllByRole('button', { name: 'Phục hồi' }).length).toBeGreaterThan(1),
  );
  const nut = screen.getAllByRole('button', { name: 'Phục hồi' });
  await nguoiDung.click(nut[nut.length - 1]);
}

beforeEach(() => {
  nhanTuMayChu.hienTai = BAI_CU;
  capNhat.mockClear();
});

afterEach(cleanup);

describe('Phục hồi phiên bản', () => {
  it('⚠ vế chống xanh-trên-tập-rỗng: màn hình dựng được và đang hiện bài CŨ', async () => {
    // Luật 7 — nếu màn hình không dựng thì bài dưới "không tìm thấy nút" và tác giả sẽ sửa
    // selector cho tới khi xanh, thay vì thấy màn hình hỏng.
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');
    expect(document.body.innerHTML).toContain('NOI DUNG CU');
  });

  it('⭐⭐ bấm Phục hồi ⇒ nội dung MỚI hiện ngay trên màn hình', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');

    await phucHoiBanDau(nguoiDung);

    // ⭐ Khẳng định KHẲNG ĐỊNH: nội dung mới phải xuất hiện. Trước bản vá, đây là chỗ đỏ.
    await waitFor(() => expect(document.body.innerHTML).toContain('NOI DUNG DA PHUC HOI'));
    // Và biểu mẫu AntD cũng phải theo — `initialValues` chỉ đổ lúc mount, nên ô tiêu đề là
    // phép đo thứ hai cho cùng một bản vá, ở một cơ chế khác.
    expect(await screen.findByDisplayValue('Tiêu đề đã phục hồi')).toBeInTheDocument();
  });

  it('⭐⭐ vừa mở bài, CHƯA gõ gì ⇒ rời trang KHÔNG bị hỏi', async () => {
    // ⚠⚠ Bài quan trọng ngang bài chặn thật. `HtmlSanitizer` gọi `Jsoup.clean` với prettyPrint bật,
    // nên HTML trong CSDL có thụt lề còn `editor.getHTML()` thì không — một bản vá "ngây thơ" so
    // hai chuỗi ấy sẽ báo bẩn **100%** ngay khi vừa mở bài. Và một cảnh báo luôn hiện là cảnh báo
    // người dùng học cách bấm qua mà không đọc, tức là tệ hơn không có.
    const nguoiDung = userEvent.setup();
    const { router } = dung();
    await screen.findByDisplayValue('Tiêu đề gốc');

    await nguoiDung.click(screen.getByRole('button', { name: 'Quay lại danh sách bài viết' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/noi-dung/bai-viet'));
    expect(screen.queryByText('Rời trang khi chưa lưu?')).toBeNull();
  });

  it('⭐⭐ gõ một chữ rồi bấm ← ⇒ ĐƯỢC HỎI, và "Ở lại" thì vẫn ở màn soạn', async () => {
    const nguoiDung = userEvent.setup();
    const { router } = dung();
    const oTieuDe = await screen.findByDisplayValue('Tiêu đề gốc');

    await nguoiDung.type(oTieuDe, ' sửa');
    await nguoiDung.click(screen.getByRole('button', { name: 'Quay lại danh sách bài viết' }));

    expect(await screen.findByText('Rời trang khi chưa lưu?')).toBeInTheDocument();
    await nguoiDung.click(screen.getByRole('button', { name: 'Ở lại' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/noi-dung/bai-viet/bai-1'));
  });

  it('⭐ và bấm "Rời trang" thì đi thật — hộp thoại không được thành ngõ cụt', async () => {
    const nguoiDung = userEvent.setup();
    const { router } = dung();
    const oTieuDe = await screen.findByDisplayValue('Tiêu đề gốc');

    await nguoiDung.type(oTieuDe, ' sửa');
    await nguoiDung.click(screen.getByRole('button', { name: 'Quay lại danh sách bài viết' }));
    await nguoiDung.click(await screen.findByRole('button', { name: 'Rời trang' }));

    await waitFor(() => expect(router.state.location.pathname).toBe('/noi-dung/bai-viet'));
  });

  it('⭐⭐ còn sửa chưa lưu ⇒ nút chuyển trạng thái bị khoá VÀ có câu lý do bằng chữ', async () => {
    // ⛔ Hai lỗ mà một luật khoá dùng chung đóng lại. Trước lượt này `ApprovalActions` chỉ đọc
    // `transition.isPending`, nên bấm "Gửi duyệt" khi đang sửa dở ⇒ `CHO_DUYET` ⇒ biểu mẫu
    // `disabled` + nút Lưu khoá ⇒ **phần vừa gõ còn trên màn hình mà không còn đường nào lưu**.
    //
    // ⚠ Khẳng định cả CHỮ, không chỉ trạng thái `disabled`: máy tính bảng không có hover nên một
    // nút xám kèm tooltip là một nút xám câm.
    const nguoiDung = userEvent.setup();
    dung();
    const oTieuDe = await screen.findByDisplayValue('Tiêu đề gốc');

    expect(screen.queryByText(/Còn thay đổi chưa lưu/)).toBeNull();

    await nguoiDung.type(oTieuDe, ' sửa');

    expect(await screen.findByText(/Còn thay đổi chưa lưu/)).toBeInTheDocument();
  });

  it('⭐⭐ và cú LƯU kế tiếp gửi nội dung ĐÃ PHỤC HỒI, không phải nội dung cũ', async () => {
    // ⚠ Đây mới là bài có tiền. Bài trên chứng minh màn hình đúng; bài này chứng minh **dữ liệu
    // gửi đi** đúng. Trước bản vá, người dùng thấy "Đã phục hồi" rồi bấm Lưu và **ghi đè ngược**
    // bản vừa phục hồi bằng nội dung cũ đang nằm trong state.
    const nguoiDung = userEvent.setup();
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');

    await phucHoiBanDau(nguoiDung);
    await screen.findByDisplayValue('Tiêu đề đã phục hồi');

    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));

    await waitFor(() => expect(capNhat).toHaveBeenCalled());
    const payload = capNhat.mock.calls.at(-1)?.[1] as { content: string };
    expect(payload.content, 'payload phải mang nội dung đã phục hồi').toContain(
      'NOI DUNG DA PHUC HOI',
    );
    expect(payload.content, 'và KHÔNG được mang nội dung cũ').not.toContain('NOI DUNG CU');
  });
});
