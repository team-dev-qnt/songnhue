import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { type ArticleDocumentView } from './types';

/**
 * **Tệp đính kèm bài viết: xem trước và tải về** — T84.7.
 *
 * <h3>⚠ Đây là bài kiểm ĐẦU TIÊN dựng `ArticleDocumentsPanel`</h3>
 *
 * Đo 22/09/2026: 0 tệp kiểm nào render khối này, dù nó đã chạy từ WS-40 và là nơi người biên tập
 * gắn văn bản vào bài. Ba nút sẵn có (lên/xuống/gỡ) vì thế cũng chưa từng bị ai bấm trong bộ kiểm.
 *
 * <h3>Bài này canh ĐƯỜNG DẪN, ⛔ chỉ canh chữ hiện ra</h3>
 *
 * Khuôn `tepSuaChuaVongKhuHoi.test.tsx` (T61.19): một nút đúng chữ mà gọi sai đường là một nút
 * hỏng ⛔ ai thấy. Ở đây vế ấy đặc biệt quan trọng — `fileUrl` và `fileInlineUrl` trả hai chuỗi
 * **trông giống hệt nhau** nhưng ký hai `Content-Disposition` khác nhau, và nhầm đường thì
 * `<iframe>` ra khung trắng ⛔ một dòng lỗi nào.
 */

const layUrlTai = vi.fn(async (_id: string) => ({ url: 'https://minio.test/tai?sig=A' }));
const layUrlXem = vi.fn(async (_id: string) => ({ url: 'https://minio.test/xem?sig=B' }));

vi.mock('./api', () => ({
  cmsApi: {
    fileUrl: (id: string) => layUrlTai(id),
    fileInlineUrl: (id: string) => layUrlXem(id),
  },
}));

const { ArticleDocumentsPanel } = await import('./ArticleDocumentsPanel');

const PDF: ArticleDocumentView = {
  publicId: 'tep-pdf',
  label: 'Quyết định 123',
  originalName: 'qd-123.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  downloadable: true,
};

const DOCX: ArticleDocumentView = {
  publicId: 'tep-docx',
  label: null,
  originalName: 'bao-cao.docx',
  contentType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  sizeBytes: 4096,
  downloadable: true,
};

const DANG_QUET: ArticleDocumentView = {
  publicId: 'tep-dang-quet',
  label: 'Phụ lục',
  originalName: 'phu-luc.pdf',
  contentType: 'application/pdf',
  sizeBytes: 1024,
  downloadable: false,
};

function dung(documents: ArticleDocumentView[]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <ArticleDocumentsPanel documents={documents} onChange={vi.fn()} onPick={vi.fn()} />
      </AntdApp>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  layUrlTai.mockClear();
  layUrlXem.mockClear();
});

describe('Tệp đính kèm bài viết — xem trước và tải (T84.7)', () => {
  it('⭐ PDF đã quét xong có CẢ hai nút, và gọi tên được', () => {
    dung([PDF]);
    expect(screen.getByRole('button', { name: 'Xem trước "Quyết định 123"' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Tải "Quyết định 123" về máy' })).toBeTruthy();
  });

  it('⭐⭐ .docx chỉ có nút Tải — xem trước một .docx cho ra khung trắng', () => {
    dung([DOCX]);
    // Nhãn rơi về tên gốc khi `label === null` — đúng như cổng công khai làm.
    expect(screen.getByRole('button', { name: 'Tải "bao-cao.docx" về máy' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: /^Xem trước/ })).toBeNull();
  });

  it('⭐⭐ tệp chưa quét xong ⇒ ẨN HẲN cả hai nút, ⛔ `disabled`', () => {
    dung([DANG_QUET]);
    // Tiền lệ ba panel: một nút xám mà bấm vào vẫn ra 409 được đọc là *hệ thống hỏng*.
    expect(screen.queryByRole('button', { name: /^Xem trước/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /về máy$/ })).toBeNull();
    // Và trạng thái phải HIỆN RA — ẩn nút mà ⛔ nói vì sao là một khối trống ⛔ lý do.
    expect(screen.getByText('đang quét')).toBeTruthy();
    // Ba nút sắp xếp/gỡ thì vẫn còn: chúng ⛔ đụng tới byte của tệp.
    expect(screen.getByRole('button', { name: 'Gỡ "Phụ lục" khỏi bài viết' })).toBeTruthy();
  });

  it('⭐⭐ bấm Xem trước ⇒ gọi ĐÚNG `fileInlineUrl` và URL ấy vào `src` của iframe', async () => {
    const nguoiDung = userEvent.setup();
    dung([PDF]);

    await nguoiDung.click(screen.getByRole('button', { name: 'Xem trước "Quyết định 123"' }));

    await waitFor(() => expect(layUrlXem).toHaveBeenCalledTimes(1));
    expect(layUrlXem).toHaveBeenCalledWith('tep-pdf');
    // ⛔⛔ Vế quyết định: đường TẢI ⛔ được gọi. Hai hàm trả hai chuỗi trông giống nhau, nên một
    //   khẳng định chỉ về `<iframe>` có `src` sẽ xanh ở CẢ HAI trạng thái (luật 9).
    expect(layUrlTai).not.toHaveBeenCalled();

    const khung = await screen.findByTitle('Quyết định 123');
    expect(khung.getAttribute('src')).toBe('https://minio.test/xem?sig=B');
  });

  it('⭐ bấm Tải ⇒ gọi `fileUrl`, ⛔ mở hộp thoại xem trước', async () => {
    const nguoiDung = userEvent.setup();
    const moTab = vi.spyOn(window, 'open').mockReturnValue(null);
    dung([PDF]);

    await nguoiDung.click(screen.getByRole('button', { name: 'Tải "Quyết định 123" về máy' }));

    await waitFor(() => expect(layUrlTai).toHaveBeenCalledWith('tep-pdf'));
    expect(layUrlXem).not.toHaveBeenCalled();
    expect(moTab).toHaveBeenCalledWith('https://minio.test/tai?sig=A', '_blank', 'noopener');
    moTab.mockRestore();
  });

  it('⭐⭐ đóng hộp thoại ⇒ iframe bị THÁO — URL 10 phút ⛔ nằm lại trong DOM', async () => {
    const nguoiDung = userEvent.setup();
    dung([PDF]);

    await nguoiDung.click(screen.getByRole('button', { name: 'Xem trước "Quyết định 123"' }));
    await screen.findByTitle('Quyết định 123');

    await nguoiDung.click(screen.getByRole('button', { name: 'Close' }));
    await waitFor(() => expect(screen.queryByTitle('Quyết định 123')).toBeNull());
  });

  it('⛔ mọi nút chỉ-có-icon của khối đều có tên đọc được (trần T63.9 = 0)', () => {
    dung([PDF, DOCX]);
    for (const nut of screen.getAllByRole('button')) {
      const coChu = (nut.textContent ?? '').trim().length > 0;
      const coTen = (nut.getAttribute('aria-label') ?? '').trim().length > 0;
      expect(coChu || coTen, `nút ⛔ có tên: ${nut.outerHTML.slice(0, 120)}`).toBe(true);
    }
  });
});
