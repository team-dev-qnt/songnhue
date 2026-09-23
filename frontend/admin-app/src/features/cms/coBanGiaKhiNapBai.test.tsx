import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Mở một bài đã lưu ⛔ được tự bịa ra "còn thay đổi chưa lưu"** — T86.1.
 *
 * <h2>Triệu chứng người dùng báo (22/09/2026)</h2>
 *
 * Tạo bài kèm video → Lưu → Gửi duyệt. Bài **đã** sang `CHO_DUYET` (màn hình hiện *Duyệt và xuất
 * bản* / *Yêu cầu chỉnh sửa*), nhưng cả hai nút bị khoá kèm câu
 * *"Còn thay đổi chưa lưu — bấm Lưu trước…"* — trong khi ⛔ ai gõ gì, và ở `CHO_DUYET` thì
 * `khoaSua` bật nên **⛔ còn đường nào lưu nữa**. Ngõ cụt: bài kẹt vĩnh viễn ở Chờ duyệt.
 *
 * <h2>⛔⛔ VIDEO ⛔ phải nguyên nhân — nó chỉ là thứ làm lỗi lộ ra</h2>
 *
 * Nguyên nhân là `<Form onValuesChange={() => setCoSuaChuaLuu(true)}>`: lượt `onUpdate` mà TipTap
 * bắn **lúc nạp** được `RichTextBridge` chuyển tiếp vào `Form`, nên cờ bẩn bật trước khi người
 * dùng chạm vào gì. Phạm vi đo được, và nó **rộng hơn hẳn** báo cáo ban đầu:
 *
 * <pre>
 *   nội dung 1 khối (chuẩn hoá ⛔ đổi chuỗi)      ⇒ sạch
 *   nội dung ≥ 2 khối (prettyPrint thêm '\n')     ⇒ BẨN
 *   nội dung có video (⇒ luôn ≥ 2 khối)           ⇒ BẨN
 * </pre>
 *
 * Mà `HtmlSanitizer` gọi `Jsoup.clean` với prettyPrint bật ⇒ **mọi bài thật nhiều hơn một khối**
 * đều rơi vào đây, từ WS-41 (`5a8156d7`, 04/09). Trước nay ⛔ ai báo vì ở `NHAP` thì nút Lưu còn
 * bấm được: người dùng bấm Lưu, cờ tắt, đi tiếp. Chỉ ở `CHO_DUYET` — nơi Lưu **cũng** bị khoá —
 * nó mới thành ngõ cụt.
 *
 * <h2>Vì sao ⛔ phải lỗi của đường lọc</h2>
 *
 * Đã đo `HtmlSanitizer` trên chính chuỗi TipTap phát: nó **⛔ làm rơi thuộc tính nào**, chỉ thêm
 * xuống dòng của prettyPrint.
 *
 * <h2>⚠ Hai ca ĐỐI CHỨNG là bắt buộc</h2>
 *
 * Một bài chỉ khẳng định *"⛔ bẩn"* sẽ xanh cả khi cờ bẩn **⛔ bao giờ** bật nữa — tức khi ta vô ý
 * tháo mất cảnh báo thật của T41.11. Đo được: tháo hẳn vị từ ⇒ **4** bài đỏ, trong đó **3** là bộ
 * canh WS-41 có sẵn (luật 1 · luật 9).
 */

const VIDEO_ID = '11111111-2222-3333-4444-555555555555';

/**
 * Chuỗi **máy chủ trả về** — đúng dạng đã ĐO từ `HtmlSanitizer` (prettyPrint xuống dòng giữa các
 * khối, thuộc tính bool giữ nguyên giá trị TipTap gửi lên).
 */
const BAI_CO_VIDEO =
  '<p>Mở bài</p>\n' +
  `<video src="/api/v1/public/videos/${VIDEO_ID}" controls="true" preload="metadata" playsinline="true"></video>\n` +
  '<p>Kết</p>';

const BAI_KHONG_VIDEO = '<p>Mở bài</p>\n<p>Kết</p>';

const nhanTuMayChu = { noiDung: BAI_CO_VIDEO, trangThai: 'NHAP' as string };

function hanhDong(trangThai: string) {
  return trangThai === 'CHO_DUYET'
    ? [
        { action: 'APPROVE', label: 'Duyệt và xuất bản' },
        { action: 'REQUEST_CHANGES', label: 'Yêu cầu chỉnh sửa' },
      ]
    : [{ action: 'SUBMIT', label: 'Gửi duyệt' }];
}

function chiTiet(noiDung: string, trangThai: string) {
  return {
    publicId: 'bai-1',
    title: 'Tiêu đề gốc',
    slug: 'tieu-de',
    summary: null,
    content: noiDung,
    coverAttachmentPublicId: null,
    source: null,
    status: trangThai,
    publishedAt: null,
    reviewNote: null,
    metaTitle: null,
    metaDescription: null,
    metaKeywords: null,
    docNumber: null,
    docIssuedDate: null,
    viewCount: 0,
    publiclyVisible: false,
    authorPublicId: null,
    authorName: null,
    categoryPublicIds: ['dm-1'],
    documents: [],
    allowedActions: hanhDong(trangThai),
  };
}

vi.mock('./api', () => ({
  cmsKeys: {
    article: (id: string) => ['cms', 'article', id] as const,
    categories: () => ['cms', 'categories'] as const,
    folders: () => ['cms', 'folders'] as const,
    files: (f: string | null) => ['cms', 'files', f] as const,
    versions: (id: string) => ['cms', 'article', id, 'versions'] as const,
    versionContent: (id: string, v: string) => ['cms', 'article', id, 'version', v] as const,
    articleAuthors: () => ['cms', 'article-authors'] as const,
  },
  cmsApi: {
    getArticle: vi.fn(async () => chiTiet(nhanTuMayChu.noiDung, nhanTuMayChu.trangThai)),
    articleAuthors: vi.fn(async () => []),
    categories: vi.fn(async () => [{ publicId: 'dm-1', name: 'Tin tức', depth: 0 }]),
    folders: vi.fn(async () => []),
    versions: vi.fn(async () => []),
    // Gửi duyệt: máy chủ trả bản chi tiết ĐẦY ĐỦ ở trạng thái mới — đúng như `transition` thật.
    transition: vi.fn(async () => {
      nhanTuMayChu.trangThai = 'CHO_DUYET';
      return chiTiet(nhanTuMayChu.noiDung, 'CHO_DUYET');
    }),
    updateArticle: vi.fn(async () => chiTiet(nhanTuMayChu.noiDung, nhanTuMayChu.trangThai)),
  },
}));

const { ArticleEditorPage } = await import('./ArticleEditorPage');

function boc(children: React.ReactNode, qc: QueryClient) {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó — khai thêm nếu cần');
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

const CANH_BAO = /Còn thay đổi chưa lưu/;

beforeEach(() => {
  nhanTuMayChu.noiDung = BAI_CO_VIDEO;
  nhanTuMayChu.trangThai = 'NHAP';
});
afterEach(cleanup);

describe('Cờ "còn thay đổi chưa lưu" ⛔ được bật khi chỉ NẠP bài', () => {
  it('⚠ chống xanh-trên-tập-rỗng: màn hình dựng được và thẻ <video> có mặt', async () => {
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');
    await waitFor(() => expect(document.querySelector(`video[src*="${VIDEO_ID}"]`)).not.toBeNull());
  });

  it('⛔⛔ mở bài CÓ VIDEO ⇒ ⛔ được tự bịa ra "còn thay đổi chưa lưu"', async () => {
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');
    await waitFor(() => expect(document.querySelector(`video[src*="${VIDEO_ID}"]`)).not.toBeNull());
    expect(screen.queryByText(CANH_BAO)).toBeNull();
  });

  it('⭐ ca ĐỐI CHỨNG — bài KHÔNG video cũng ⛔ bẩn (để biết ca trên đỏ vì VIDEO)', async () => {
    nhanTuMayChu.noiDung = BAI_KHONG_VIDEO;
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');
    expect(screen.queryByText(CANH_BAO)).toBeNull();
  });

  it('⭐ ca PHÂN ĐỊNH PHẠM VI — bài MỘT khối (chuẩn hoá ⛔ đổi chuỗi) ⛔ bẩn', async () => {
    nhanTuMayChu.noiDung = '<p>Chỉ một khối</p>';
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');
    expect(screen.queryByText(CANH_BAO)).toBeNull();
  });

  it('⛔⛔ gửi duyệt xong ⇒ nút Duyệt ⛔ được bị khoá bởi một thay đổi ⛔ ai tạo ra', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await screen.findByDisplayValue('Tiêu đề gốc');
    await waitFor(() => expect(document.querySelector(`video[src*="${VIDEO_ID}"]`)).not.toBeNull());

    await nguoiDung.click(screen.getByRole('button', { name: 'Gửi duyệt' }));

    // Bài ĐÃ sang Chờ duyệt — đúng ảnh chụp người dùng gửi.
    const nutDuyet = await screen.findByRole('button', { name: 'Duyệt và xuất bản' });

    expect(screen.queryByText(CANH_BAO)).toBeNull();
    expect(nutDuyet).toBeEnabled();
  });

  it('⭐ ca ĐỐI CHỨNG của luật 1 — gõ THẬT thì cảnh báo VẪN phải hiện', async () => {
    // ⛔ Thiếu ca này thì một bản vá kiểu "tháo hẳn cờ bẩn" cũng làm bốn ca trên xanh,
    //    và ta mất luôn chốt chặn T41.11 (gửi duyệt khi đang sửa dở ⇒ mất phần vừa gõ).
    const nguoiDung = userEvent.setup();
    dung();
    const oTieuDe = await screen.findByDisplayValue('Tiêu đề gốc');
    await nguoiDung.type(oTieuDe, ' sửa');
    expect(await screen.findByText(CANH_BAO)).toBeInTheDocument();
  });
});
