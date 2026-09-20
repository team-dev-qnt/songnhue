import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Danh sách công trình phải bày TUYẾN SÔNG và CỤM CÔNG TRÌNH** — T75.1.
 *
 * <h2>Khoảng trống được vá</h2>
 *
 * `ConstructionRow` mang sẵn `riverName`, `chainage` và `clusterName` từ WS-17 — backend tính,
 * serialize, gửi về máy khách đủ mỗi dòng — nhưng mảng `columns` ⛔ bày chúng ra. Nửa cặp đọc–ghi ở
 * dạng tốn kém nhất (quy tắc 27): dữ liệu đã trả giá đủ cả đường đi mà ⛔ ai đọc được nó, và
 * ⛔ triệu chứng nào ngoài việc người dùng phải mở lần lượt từng hồ sơ để tra một ô.
 *
 * Cùng lượt: bộ lọc gửi thêm `clusterId` — tham số backend khai từ WS-17 mà **⛔ nơi nào gọi**, nên
 * cột cụm vừa hiện ra sẽ lọc được ngay thay vì thành một cột chỉ để nhìn.
 *
 * <h2>Vì sao bài này phân biệt được hai trạng thái (luật 9)</h2>
 *
 * Hai công trình khác nhau ở **mọi ô**, và ⛔ ô nào trùng giá trị của ô khác:
 *
 * <ul>
 *   <li>Một dòng có đủ tuyến sông + lý trình + cụm ⇒ bắt lượt render ra rỗng.
 *   <li>Một dòng có tuyến sông nhưng <b>⛔ có lý trình</b> và <b>⛔ thuộc cụm nào</b> ⇒ bắt lượt
 *       render ném khi gặp `null`, và bắt cả lượt in chữ {@code "null"} ra màn hình. G8 còn mở nên
 *       ô trống là trạng thái <b>ĐÚNG</b>, ⛔ phải một ca hiếm (quy tắc 16).
 * </ul>
 *
 * ⚠ Bài khẳng định theo <b>từng Ô của đúng hàng ấy</b> ({@code within(hàng)}), ⛔ theo cả bảng:
 * {@code screen.getByText('Sông Đáy')} xanh kể cả khi chuỗi ấy rơi nhầm sang dòng công trình khác —
 * đúng kiểu khẳng định ⛔ phân biệt được hai trạng thái mà dự án đã trả giá ở T46.8.
 */

const CONG_LIEN_MAC = {
  publicId: 'ct-lien-mac',
  code: 'CT-LMAC',
  name: 'Cống Liên Mạc',
  constructionType: 'CONG',
  operationalStatus: 'BINH_THUONG',
  managementLevel: 'CONG_TY',
  lifecycleState: 'DANG_KHAI_THAC',
  orgUnitName: 'Xí nghiệp Thuỷ lợi Hà Đông',
  riverName: 'Sông Hồng',
  chainage: 'K53+450',
  clusterName: 'Cụm đầu mối Liên Mạc',
  updatedAt: '2026-09-18T03:00:00Z',
};

/** Tuyến sông có, lý trình ⛔ có, cụm ⛔ có — ba trạng thái rỗng khác nhau trong MỘT hàng. */
const TRAM_TAY_TUU = {
  publicId: 'ct-tay-tuu',
  code: 'CT-TTU2',
  name: 'Trạm bơm Tây Tựu 2',
  constructionType: 'TRAM_BOM',
  operationalStatus: 'BINH_THUONG',
  managementLevel: 'XI_NGHIEP',
  lifecycleState: 'DANG_KHAI_THAC',
  orgUnitName: 'Xí nghiệp Thuỷ lợi Từ Liêm',
  riverName: 'Sông Pheo',
  chainage: null,
  clusterName: null,
  updatedAt: '2026-09-17T03:00:00Z',
};

const CUM_LIEN_MAC = {
  publicId: 'cum-lien-mac',
  code: 'CUM-LMAC',
  name: 'Cụm đầu mối Liên Mạc',
  orgUnitId: 'xn-ha-dong',
  orgUnitName: 'XN Hà Đông',
  description: 'Cống Liên Mạc và 4 trạm bơm ven sông Hồng',
  sortOrder: 5,
  active: true,
};

/** Tham số của lượt gọi `GET /ops/constructions` gần nhất — vế đo của phép kiểm bộ lọc. */
let thamSoCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/ops/constructions/rivers') return ['Sông Hồng', 'Sông Pheo'];
      if (duong === '/ops/construction-clusters') return [CUM_LIEN_MAC];
      if (duong === '/ops/constructions/statistics') {
        return { total: 2, byType: [], byStatus: [], byLevel: [] };
      }
      return [];
    }),
    getPage: vi.fn(async (duong: string, thamSo?: Record<string, unknown>) => {
      if (duong !== '/ops/constructions') return { items: [], meta: { totalElements: 0 } };
      thamSoCuoi = thamSo ?? {};
      return {
        items: [CONG_LIEN_MAC, TRAM_TAY_TUU],
        meta: { totalElements: 2, totalPages: 1, page: 1, size: 20 },
      };
    }),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { ConstructionsPage } = await import('./ConstructionsPage');

function dung() {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
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
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <MemoryRouter>
            <ConstructionsPage />
          </MemoryRouter>
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** Hàng `<tr>` chứa tên công trình — mọi khẳng định về ô đều neo vào nó. */
async function hang(tenCongTrinh: string): Promise<HTMLElement> {
  const o = await screen.findByText(tenCongTrinh);
  const tr = o.closest('tr');
  if (!tr) throw new Error(`⛔ tìm thấy hàng của "${tenCongTrinh}"`);
  return tr as HTMLElement;
}

afterEach(() => {
  cleanup();
  thamSoCuoi = {};
});

describe('Danh sách công trình — tuyến sông & cụm công trình', () => {
  // ⚠ Hỏi theo VAI TRÒ `columnheader`, ⛔ theo chuỗi: cả hai chữ ấy còn xuất hiện ở ô lọc phía
  //   trên, nên `getByText` ném "Found multiple elements" — và nếu chỉ có một nơi mang chữ ấy thì
  //   nó xanh cả khi đó là cái nhãn bộ lọc chứ ⛔ phải tiêu đề cột (luật 9).
  it('có đủ hai tiêu đề cột mới', async () => {
    dung();
    const bang = await screen.findByRole('table');
    expect(within(bang).getByRole('columnheader', { name: 'Tuyến sông' })).toBeTruthy();
    expect(within(bang).getByRole('columnheader', { name: 'Cụm công trình' })).toBeTruthy();
  });

  it('⭐ dòng đủ dữ liệu: tuyến sông + lý trình + cụm nằm ĐÚNG hàng của nó', async () => {
    dung();
    const tr = await hang(CONG_LIEN_MAC.name);

    expect(within(tr).getByText(CONG_LIEN_MAC.riverName)).toBeTruthy();
    expect(within(tr).getByText(CONG_LIEN_MAC.chainage)).toBeTruthy();
    expect(within(tr).getByText(CONG_LIEN_MAC.clusterName)).toBeTruthy();
  });

  it('⭐ dòng thiếu dữ liệu: ⛔ ném, ⛔ in chữ "null" — ô trống là trạng thái ĐÚNG (G8)', async () => {
    dung();
    const tr = await hang(TRAM_TAY_TUU.name);

    expect(within(tr).getByText(TRAM_TAY_TUU.riverName)).toBeTruthy();
    expect(
      within(tr).queryByText(/null/i),
      'Render `null` ra chữ là cách quen thuộc để một ô rỗng trông như có dữ liệu',
    ).toBeNull();
    // Cụm rỗng và lý trình rỗng ⇒ hàng này phải có ô gạch ngang, ⛔ phải một ô biến mất.
    expect(within(tr).getAllByText('-').length).toBeGreaterThanOrEqual(1);
  });

  it('⚠ chống tập rỗng: lượt gọi đầu KHÔNG mang `clusterId` — vế đối chứng của bài dưới', async () => {
    dung();
    await waitFor(() => expect(thamSoCuoi.page).toBe(1));
    expect(thamSoCuoi.clusterId).toBeUndefined();
  });

  it('⭐⭐ chọn cụm rồi bấm Lọc ⇒ gửi `clusterId` lên backend (tham số mồ côi từ WS-17)', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await screen.findByRole('table');

    await nguoiDung.click(screen.getByLabelText('Cụm công trình'));
    await nguoiDung.click(await screen.findByTitle(`${CUM_LIEN_MAC.code} — ${CUM_LIEN_MAC.name}`));
    await nguoiDung.click(screen.getByRole('button', { name: 'Lọc' }));

    await waitFor(() =>
      expect(
        thamSoCuoi.clusterId,
        'Tên trường bộ lọc phải TRÙNG KHÍT tham số backend — `ConstructionsPage` rải thẳng nó vào query string',
      ).toBe(CUM_LIEN_MAC.publicId),
    );
  });
});
