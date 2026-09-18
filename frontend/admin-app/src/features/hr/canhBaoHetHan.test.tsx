import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, within } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type * as ApiClientModule from '@/shared/apiClient';

import { type CanhBaoHetHanView, nhanConLai } from './hrVocabulary';

/**
 * **Màn hình cảnh báo hết hạn M4.9** — T61.20. `GET /hr/canh-bao-het-han` có 0 nơi gọi trước đợt này.
 * Vế backend (ngưỡng đọc từ settings, đã quá hạn vẫn nằm trong danh sách, `soNgayCon` âm):
 * `HoSoConHttpTest.nguongCanhBaoDocTuSettings` · `daHetHanVanNamTrongDanhSach`.
 */

let duLieu: CanhBaoHetHanView;
const goi = vi.fn();

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) => {
        goi(url);
        return duLieu;
      }),
    },
  };
});

const { CanhBaoHetHanPage } = await import('./CanhBaoHetHanPage');

function dung() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AntdApp>
        <CanhBaoHetHanPage />
      </AntdApp>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  goi.mockClear();
});

describe('Cảnh báo hết hạn — M4.9', () => {
  it('⭐⭐ gọi đúng đường; ngưỡng ngày trên tiêu đề lấy TỪ API, ⛔ ghi cứng', async () => {
    // 45 và 75 cố ý ⛔ trùng mặc định 30/90 — trùng thì một bản ghi cứng cũng xanh (T48.7).
    duLieu = { nguongNgayHopDong: 45, nguongNgayChungChi: 75, hopDong: [], chungChi: [] };
    dung();
    expect(await screen.findByText(/hết hạn trong 45 ngày tới hoặc đã quá hạn/)).toBeTruthy();
    expect(screen.getByText(/hết hiệu lực trong 75 ngày tới hoặc đã quá hạn/)).toBeTruthy();
    expect(screen.getByText('Không có hợp đồng nào hết hạn trong 45 ngày tới')).toBeTruthy();
    expect(goi).toHaveBeenCalledWith('/hr/canh-bao-het-han');
  });

  it('⛔⛔ mục đã quá hạn hiện SỐ NGÀY QUÁ HẠN, ⛔ kẹp về 0', async () => {
    duLieu = {
      nguongNgayHopDong: 30,
      nguongNgayChungChi: 90,
      hopDong: [
        {
          hoSoPublicId: 'hs-1',
          maCanBo: 'CB-001',
          hoTen: 'Nguyễn Văn A',
          moTa: 'Hợp đồng lao động',
          hetHan: '2026-08-05',
          soNgayCon: -40,
        },
      ],
      chungChi: [
        {
          hoSoPublicId: 'hs-2',
          maCanBo: 'CB-002',
          hoTen: 'Trần Thị B',
          moTa: 'Chứng chỉ an toàn lao động',
          hetHan: '2026-09-24',
          soNgayCon: 10,
        },
      ],
    };
    dung();
    const hangA = (await screen.findByText('Nguyễn Văn A')).closest('tr') as HTMLElement;
    expect(within(hangA).getByText('Quá hạn 40 ngày')).toBeTruthy();
    expect(within(hangA).getByText('05/08/2026')).toBeTruthy();
    const hangB = screen.getByText('Trần Thị B').closest('tr') as HTMLElement;
    expect(within(hangB).getByText('Còn 10 ngày')).toBeTruthy();
    expect(screen.getByText(/lọc theo phạm vi đơn vị/)).toBeTruthy();
  });

  it('⭐ nhãn còn lại: âm/0 đỏ, dương vàng', () => {
    expect(nhanConLai(-1)).toEqual({ chu: 'Quá hạn 1 ngày', mau: 'error' });
    expect(nhanConLai(0)).toEqual({ chu: 'Hết hạn hôm nay', mau: 'error' });
    expect(nhanConLai(7)).toEqual({ chu: 'Còn 7 ngày', mau: 'warning' });
  });
});
