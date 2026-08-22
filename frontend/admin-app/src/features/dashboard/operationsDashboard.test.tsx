import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { DashboardView } from '@/shared/api-types';
import { datBeRongCua } from '@/testsupport/setup';

import { OperationsDashboardPage } from './OperationsDashboardPage';

/**
 * Dashboard điều hành — bố cục và cách nói "chưa có dữ liệu" (T23.11).
 *
 * <h3>Hai thứ được thay bằng bản giả, và vì sao</h3>
 *
 * <ul>
 *   <li><b>Biểu đồ</b> — ECharts vẽ lên `<canvas>`, jsdom không có bộ vẽ canvas. Nội dung
 *       biểu đồ đã được kiểm ở {@code chartOptions.test.ts}, nơi kiểm được thật sự.
 *   <li><b>Bản đồ</b> — Leaflet đo bố cục thật để đặt tile; jsdom trả mọi kích thước bằng
 *       0 nên nó dựng ra một khung rỗng và ném lỗi ở chỗ chẳng liên quan gì tới thứ đang
 *       kiểm.
 * </ul>
 *
 * <p>Phần **không** giả: lưới, KPI card, chế độ wall, cách hiển thị ô chưa có nguồn — tức
 * là đúng những thứ bài kiểm này nói nó kiểm.
 */

vi.mock('@/components/charts/BaseChart', () => ({
  BaseChart: ({ empty }: { empty: boolean }) => (
    <div data-testid="bieu-do">{empty ? 'rỗng' : 'có dữ liệu'}</div>
  ),
}));

vi.mock('@/components/dashboard/ConstructionMap', () => ({
  ConstructionMap: () => <div data-testid="ban-do" />,
}));

const getGia = vi.fn();

vi.mock('@/shared/apiClient', () => ({
  api: { get: (url: string) => getGia(url) as unknown },
}));

const DASHBOARD: DashboardView = {
  generatedAt: '2026-08-21T03:00:00Z',
  autoRefreshSeconds: 300,
  wallRotateSeconds: 30,
  kpis: [
    {
      key: 'construction.active',
      label: 'Công trình đang hoạt động',
      value: 32,
      total: 40,
      tone: 'NORMAL',
      unavailableReason: null,
      availableIn: null,
    },
    {
      key: 'construction.incident',
      label: 'Sự cố',
      value: 0,
      total: null,
      tone: 'DANGER',
      unavailableReason: null,
      availableIn: null,
    },
    // ⚠ Ô "chưa có nguồn" thứ hai lấy từ MOD-03, KHÔNG lấy từ WS-18 nữa: WS-18 đã trả nợ hai ô
    //   sửa chữa / sự cố, và một dữ liệu mẫu hẹn "sẽ có ở WS-18" là một lời hẹn đã tới hạn — nó làm
    //   bài kiểm mô tả sai hiện trạng, dù vẫn xanh.
    {
      key: 'hydro.stations-offline',
      label: 'Điểm đo mất tín hiệu',
      value: null,
      total: null,
      tone: 'UNKNOWN',
      unavailableReason: 'Chưa đấu nối dữ liệu thuỷ văn',
      availableIn: 'Phase 2 (MOD-03)',
    },
    {
      key: 'hydro.active-alerts',
      label: 'Cảnh báo thuỷ văn đang xảy ra',
      value: null,
      total: null,
      tone: 'UNKNOWN',
      unavailableReason: 'Chưa đấu nối dữ liệu thuỷ văn',
      availableIn: 'Phase 2 (MOD-03)',
    },
  ],
  statistics: {
    total: 40,
    withoutLocation: 8,
    byType: [{ key: 'TRAM_BOM', label: 'TRAM_BOM', count: 40 }],
    byStatus: [{ key: 'BINH_THUONG', label: 'BINH_THUONG', count: 40 }],
    byOrgUnit: [{ key: '1', label: 'Công ty', count: 40 }],
    byManagementLevel: [{ key: 'XI_NGHIEP', label: 'XI_NGHIEP', count: 40 }],
  },
  map: {
    tileUrl: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '© OpenStreetMap contributors',
    centerLat: 20.98,
    centerLng: 105.78,
    defaultZoom: 11,
    maxZoom: 18,
  },
};

function dung(duongDan = '/van-hanh/dieu-hanh') {
  getGia.mockImplementation((url: string) =>
    url.includes('map-points') ? Promise.resolve([]) : Promise.resolve(DASHBOARD),
  );
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[duongDan]}>
        <OperationsDashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('ô KPI chưa có nguồn', () => {
  it('⛔ hiện "Chưa có dữ liệu" kèm mốc sẽ có — KHÔNG hiện số 0', async () => {
    dung();

    await waitFor(() => expect(screen.getByText('Điểm đo mất tín hiệu')).toBeInTheDocument());

    // Hai ô "chưa có nguồn" đều phải nói ra điều đó...
    expect(screen.getAllByText('Chưa có dữ liệu')).toHaveLength(2);
    expect(screen.getAllByText('Phase 2 (MOD-03)')).toHaveLength(2);

    // ...và ô "Sự cố" (đã đo, bằng 0) vẫn phải hiện đúng số 0. Đây là vế thứ hai, và
    // thiếu nó thì một bản sửa biến mọi số 0 thành dấu gạch cũng sẽ xanh — tức là giấu
    // mất con số duy nhất người trực cần thấy khi mọi thứ đang bình thường.
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  it('ô có số hiện đủ tử số và mẫu số', async () => {
    dung();

    await waitFor(() => expect(screen.getByText('32')).toBeInTheDocument());
    expect(screen.getByText('/ 40')).toBeInTheDocument();
  });
});

describe('bố cục ở các bề rộng thiết bị', () => {
  // 3840 = TV 85" 4K đã chốt (B8) · 1920 = máy chiếu Full-HD · 1366 = laptop quản trị.
  // Thêm 900 (cửa sổ chia đôi màn hình) vì ở ba bề rộng kia **trần cột luôn là thứ quyết
  // định** — cả ba đều ra 5/3, nên nếu chỉ kiểm chúng thì một lỗi làm bề rộng không đo
  // được (luôn 0 → 1 cột) vẫn có thể lọt. 900px là bề rộng duy nhất trong bộ này mà con
  // số phụ thuộc thật vào phép đo.
  const TRUONG_HOP: [number, number, number][] = [
    [3840, 5, 3],
    [1920, 5, 3],
    [1366, 5, 3],
    [900, 4, 2],
  ];

  for (const [beRong, cotKpi, cotKhoi] of TRUONG_HOP) {
    it(`⭐ ${beRong}px: ${cotKpi} cột KPI / ${cotKhoi} cột khối, không khối nào bị cắt`, async () => {
      datBeRongCua(beRong);
      dung();

      await waitFor(() => expect(screen.getByTestId('luoi-kpi')).toBeInTheDocument());

      // Vế "không mất khối": cùng một cây component cho mọi bề rộng, nên số khối phải
      // không đổi. Một bản "rút gọn cho màn hình nhỏ" sẽ đỏ ngay ở đây.
      expect(screen.getByTestId('luoi-kpi').children).toHaveLength(4);
      expect(screen.getByTestId('luoi-thong-ke').children).toHaveLength(3);

      // Vế "không tràn ngang" đã kiểm bằng số ở `gridLayout.test.ts`; ở đây khẳng định
      // trang thật sự đo được khung và dùng hàm đó, chứ không tự viết một chuỗi cột khác.
      expect(screen.getByTestId('luoi-kpi').style.gridTemplateColumns).toBe(
        `repeat(${cotKpi}, minmax(0, 1fr))`,
      );
      expect(screen.getByTestId('luoi-thong-ke').style.gridTemplateColumns).toBe(
        `repeat(${cotKhoi}, minmax(0, 1fr))`,
      );
    });
  }
});

describe('chế độ màn hình lớn', () => {
  it('?mode=wall dựng khung wall; không có tham số thì không', async () => {
    datBeRongCua(3840);
    const { unmount } = dung('/van-hanh/dieu-hanh?mode=wall');
    await waitFor(() => expect(screen.getByTestId('khung-wall')).toBeInTheDocument());
    unmount();
    cleanup();

    dung('/van-hanh/dieu-hanh');
    await waitFor(() => expect(screen.getByTestId('luoi-kpi')).toBeInTheDocument());
    expect(screen.queryByTestId('khung-wall')).not.toBeInTheDocument();
  });

  it('⭐ wall dùng ĐÚNG các khối như chế độ thường — không phải bố cục thứ hai', async () => {
    datBeRongCua(3840);
    dung('/van-hanh/dieu-hanh?mode=wall');

    await waitFor(() => expect(screen.getByTestId('khung-wall')).toBeInTheDocument());
    expect(screen.getByTestId('luoi-kpi').children).toHaveLength(4);
    expect(screen.getByTestId('luoi-thong-ke').children).toHaveLength(3);
    expect(screen.getAllByText('Chưa có dữ liệu')).toHaveLength(2);
  });
});
