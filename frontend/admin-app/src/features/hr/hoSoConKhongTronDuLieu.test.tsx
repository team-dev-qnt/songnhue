import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { App as AntdApp } from 'antd';
import type React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type * as ApiClientModule from '@/shared/apiClient';

import type { LyLichView } from './hrVocabulary';

/**
 * ⛔⛔⛔ **Ngăn kéo hồ sơ con ⛔ KHÔNG được mang dữ liệu của người TRƯỚC sang người SAU** — WS-53.
 *
 * <h2>Vì sao phải viết lại bài này cho một màn hình mới</h2>
 *
 * T51.12 đã đo được hậu quả trên hai hộp thoại khác của <b>cùng module</b>: mở hồ sơ A → đóng → mở
 * B thì ô *Họ và tên* hiện `Nguyễn Văn A`, hộp thoại 🔒 hiện CCCD của A **dưới tên B**. Bài kiểm
 * sinh ra từ đó (`hoSoKhongTronDuLieu.test.tsx`) phủ **đúng hai màn hình ấy** — luật 28: cái xanh
 * của nó ⛔ không nói gì về màn hình thứ ba.
 *
 * <p>Và ở đây thứ có thể lẫn giữa hai con người là **bằng cấp**, **quyết định kỷ luật** và **hồ sơ
 * y tế** — dữ liệu cá nhân theo NĐ 13/2023, một số thuộc nhóm *nhạy cảm*.
 *
 * <h2>⚠ Khẳng định là "dữ liệu của B XUẤT HIỆN", ⛔ không phải "của A biến mất"</h2>
 *
 * Luật 9: `queryByText(cuaA)` trả `null` cũng khi ngăn kéo ⛔ không dựng, khi component sập, hoặc
 * khi nhãn đổi tên — ba trạng thái tệ hơn hẳn lỗi đang sửa. Nên mỗi bài khẳng định **giá trị đúng
 * của B**, rồi mới khẳng định thêm rằng của A ⛔ không còn.
 */

const LY_LICH_A: LyLichView[] = [
  {
    publicId: 'll-a',
    kind: 'BANG_CAP',
    name: 'Kỹ sư Thuỷ lợi (của A)',
    grade: 'Giỏi',
    major: 'Kỹ thuật tài nguyên nước',
    institution: 'Đại học Thuỷ lợi',
    certificateNo: 'A-0001',
    issuedOn: '2013-06-20',
    expiresOn: null,
    note: null,
    updatedAt: '2026-09-10T00:00:00Z',
  },
];

/** ⛔ B có ĐÚNG MỘT mục và nó khác hẳn của A — nếu ngăn kéo giữ lại dữ liệu cũ thì lộ ra ngay. */
const LY_LICH_B: LyLichView[] = [
  {
    publicId: 'll-b',
    kind: 'CHUNG_CHI',
    name: 'Chứng chỉ an toàn (của B)',
    grade: null,
    major: null,
    institution: 'Sở Xây dựng',
    certificateNo: 'B-0002',
    issuedOn: '2024-01-05',
    expiresOn: '2027-01-05',
    note: null,
    updatedAt: '2026-09-10T00:00:00Z',
  },
];

const TINH_TRANG_CHUA_CAU_HINH = {
  daCauHinh: false,
  batBuoc: [],
  conThieu: [],
  soTepTheoThuMuc: {},
  dungLuongDaDungByte: 0,
};

vi.mock('@/shared/apiClient', async () => {
  const that = await vi.importActual<typeof ApiClientModule>('@/shared/apiClient');
  return {
    ...that,
    api: {
      get: vi.fn(async (duongDan: string) => {
        if (duongDan === '/hr/employees/hs-a/ly-lich') return LY_LICH_A;
        if (duongDan === '/hr/employees/hs-b/ly-lich') return LY_LICH_B;
        if (duongDan.endsWith('/timeline')) return [];
        if (duongDan.endsWith('/tai-lieu')) return [];
        if (duongDan.endsWith('/tai-lieu/tinh-trang')) return TINH_TRANG_CHUA_CAU_HINH;
        throw new Error('Bài kiểm ⛔ không dựng đường dẫn này: ' + duongDan);
      }),
      post: vi.fn(),
      put: vi.fn(),
      delete: vi.fn(),
      upload: vi.fn(),
      getPage: vi.fn(),
    },
  };
});

const { HoSoConDrawer } = await import('./HoSoConDrawer');
const { LyLichFormModal } = await import('./LyLichFormModal');

function boc(children: React.ReactNode, qc: QueryClient) {
  return (
    <QueryClientProvider client={qc}>
      <AntdApp>{children}</AntdApp>
    </QueryClientProvider>
  );
}

let qc: QueryClient;

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

afterEach(() => {
  cleanup();
  qc.clear();
});

describe('màn hình hồ sơ con ⛔ không trộn dữ liệu giữa hai CBNV', () => {
  it('⛔⛔⛔ Sửa mục của A → đóng → sửa mục của B: ô mang giá trị của B (T51.12 áp cho màn hình thứ ba)', async () => {
    const mucA = LY_LICH_A[0];
    const mucB = LY_LICH_B[0];

    const { rerender } = render(
      boc(
        <LyLichFormModal open hoSoId="hs-a" muc={mucA} onClose={() => {}} onSaved={() => {}} />,
        qc,
      ),
    );
    await waitFor(() => expect(screen.getByLabelText('Tên')).toHaveValue(mucA.name));

    rerender(
      boc(
        <LyLichFormModal
          open={false}
          hoSoId="hs-a"
          muc={mucA}
          onClose={() => {}}
          onSaved={() => {}}
        />,
        qc,
      ),
    );
    rerender(
      boc(
        <LyLichFormModal open hoSoId="hs-b" muc={mucB} onClose={() => {}} onSaved={() => {}} />,
        qc,
      ),
    );

    // ⭐ Vế CHÍNH — giá trị ĐÚNG của B phải hiện ra.
    await waitFor(() => expect(screen.getByLabelText('Tên')).toHaveValue(mucB.name));
    expect(screen.getByLabelText('Số hiệu')).toHaveValue(mucB.certificateNo);
    // ⛔ Ô mà A CÓ và B KHÔNG — chỗ dữ liệu cũ dễ đọng lại nhất.
    expect(screen.getByLabelText('Chuyên ngành')).toHaveValue('');
  });

  it('⛔ Chưa cấu hình thư mục bắt buộc ⇒ nói "chưa cấu hình", ⛔ KHÔNG hiện 0%', async () => {
    render(boc(<HoSoConDrawer open publicId="hs-b" tenCanBo="B" coSua onClose={() => {}} />, qc));

    const tab = await screen.findByRole('tab', { name: /Hồ sơ tài liệu/ });
    tab.click();

    await waitFor(() =>
      expect(screen.getByText('Chưa cấu hình danh sách tài liệu bắt buộc')).toBeInTheDocument(),
    );
    // ⛔ Quy tắc 16: số 0 là một câu khẳng định. Hiện "0%" ở đây là công bố một con số dựa trên
    //    một luật nhân sự ⛔ KHÔNG AI duyệt.
    expect(screen.queryByText('0%')).not.toBeInTheDocument();
  });
});
