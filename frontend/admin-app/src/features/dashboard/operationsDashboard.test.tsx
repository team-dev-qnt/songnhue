import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { DashboardView, StationLayerView } from '@/shared/api-types';
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
    // ⭐ T35.3 (04/09/2026) — hai ô thuỷ văn NAY CÓ SỐ THẬT. Trước đó chúng là dữ liệu mẫu
    //   "chưa có nguồn"; giữ nguyên sau khi backend đã nối là để bài kiểm mô tả một trạng thái
    //   backend ⛔ không còn sinh ra được — xanh, và sai (§10.69).
    {
      key: 'hydro.stations-offline',
      label: 'Điểm đo mất tín hiệu',
      value: 2,
      total: 19,
      tone: 'WARNING',
      unavailableReason: null,
      availableIn: null,
    },
    {
      key: 'hydro.active-alerts',
      label: 'Cảnh báo thuỷ văn đang xảy ra',
      value: 1,
      total: null,
      tone: 'DANGER',
      unavailableReason: null,
      availableIn: null,
    },
    // ⚠⚠ Ô TỔNG HỢP, ⛔ không phải một ô có thật của backend.
    //
    // Sau T35.3 ⛔ KHÔNG ô KPI nào của backend còn trả `value: null` — cả mười ô đều có nguồn. Nhưng
    // cơ chế "ô trống phải nói được vì sao trống" vẫn phải sống: nó là ràng buộc ở tầng kiểu của
    // record `Kpi` (backend ném lỗi nếu thiếu lý do), và ô KPI thứ mười một sẽ cần đúng nó.
    //
    // ⛔ Đừng thay ô này bằng một khoá `hydro.*` cho "thật hơn" — làm thế là quay lại đúng lời nói
    // dối vừa gỡ. Đây là dữ liệu mẫu cho MỘT nhánh hiển thị, và nó tự khai điều đó.
    {
      key: 'demo.chua-co-nguon',
      label: 'Ô mẫu cho nhánh chưa có nguồn',
      value: null,
      total: null,
      tone: 'UNKNOWN',
      unavailableReason: 'Ô tổng hợp của bài kiểm — không ô backend nào đang ở trạng thái này',
      availableIn: 'Hạng mục sau',
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

/**
 * ⭐ Lớp điểm đo thuỷ văn (T35.1) — hai điểm đo có toạ độ, một điểm đo còn chờ toạ độ (G8).
 *
 * ⚠ Phải khai RIÊNG khỏi `/ops/dashboard/map-points`: hai endpoint cùng chứa chuỗi `map-points`,
 * nên bản đầu của bộ giả trả `[]` cho cả hai — và trang **sập** với `undefined.length`. Đó là một
 * lỗi thật ở mã trang, ⛔ không phải lỗi của bộ giả; nó đã được vá bằng `?.` ở cả hai mức.
 */
const LOP_DIEM_DO: StationLayerView = {
  diemDo: [
    {
      publicId: 'a1',
      code: 'DO-TEST-TL',
      name: 'Điểm đo kiểm thử — Thượng lưu',
      positionRole: 'THUONG_LUU',
      latitude: '20.980000',
      longitude: '105.780000',
      riverName: null,
      chainage: null,
      trangThai: 'HOAT_DONG',
      nghiNgo: false,
      giaTri: '2.400',
      donVi: 'm',
      tenChiSo: 'Mực nước',
      mocDo: '2026-09-04T03:00:00Z',
      khoaMauCanhBao: null,
      tenMucCanhBao: null,
    },
    {
      publicId: 'a2',
      code: 'DO-TEST-HL',
      name: 'Điểm đo kiểm thử — Hạ lưu',
      positionRole: 'HA_LUU',
      latitude: '20.990000',
      longitude: '105.790000',
      riverName: null,
      chainage: null,
      trangThai: 'MAT_TIN_HIEU',
      nghiNgo: true,
      giaTri: '1.800',
      donVi: 'm',
      tenChiSo: 'Mực nước',
      mocDo: '2026-09-01T03:00:00Z',
      khoaMauCanhBao: 'alert-level-3',
      tenMucCanhBao: 'Báo động III',
    },
  ],
  chuaSoHoaViTri: [
    {
      publicId: 'b1',
      code: 'DO-CHUA-TOA-DO',
      name: 'Điểm đo chưa có toạ độ',
      positionRole: 'MN_SONG',
      riverName: null,
      chainage: null,
    },
  ],
};

function dung(duongDan = '/van-hanh/dieu-hanh') {
  getGia.mockImplementation((url: string) => {
    if (url.startsWith('/hyd/stations/map-points')) return Promise.resolve(LOP_DIEM_DO);
    if (url.includes('map-points')) return Promise.resolve([]);
    return Promise.resolve(DASHBOARD);
  });
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

    // Ô mẫu chưa có nguồn phải nói ra điều đó...
    expect(screen.getAllByText('Chưa có dữ liệu')).toHaveLength(1);
    expect(screen.getAllByText('Hạng mục sau')).toHaveLength(1);

    // ...và ô "Sự cố" (đã đo, bằng 0) vẫn phải hiện đúng số 0. Đây là vế thứ hai, và
    // thiếu nó thì một bản sửa biến mọi số 0 thành dấu gạch cũng sẽ xanh — tức là giấu
    // mất con số duy nhất người trực cần thấy khi mọi thứ đang bình thường.
    expect(screen.getByText('0')).toBeInTheDocument();
  });

  /**
   * ⭐ T35.3 — vế NGƯỢC, và nó là vế đáng giá hơn.
   *
   * ⛔ Bài trên chỉ khẳng định "ô rỗng thì nói là rỗng". Nó vẫn xanh trọn vẹn nếu ai đó làm hai ô
   * thuỷ văn quay về `null` — mà đó đúng là hồi quy cần bắt: hai ô ấy vừa chuyển từ "chưa có nguồn"
   * sang "có số thật", và đường đi qua `hydro.spi` là thứ mới nhất, mỏng nhất trong cả chuỗi.
   */
  it('⭐ hai ô thuỷ văn hiện SỐ, ⛔ không còn "Chưa có dữ liệu"', async () => {
    dung();

    await waitFor(() => expect(screen.getByText('Điểm đo mất tín hiệu')).toBeInTheDocument());

    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('/ 19')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
    // Không còn ô thuỷ văn nào hẹn "sẽ có ở Phase 2".
    expect(screen.queryByText('Phase 2 (MOD-03)')).not.toBeInTheDocument();
  });

  it('ô có số hiện đủ tử số và mẫu số', async () => {
    dung();

    await waitFor(() => expect(screen.getByText('32')).toBeInTheDocument());
    expect(screen.getByText('/ 40')).toBeInTheDocument();
  });
});

describe('lớp điểm đo thuỷ văn trên bản đồ (T35.1 · T35.2)', () => {
  it('⭐ T35.2 — nói ra ĐÍCH XÁC còn bao nhiêu điểm đo chưa có toạ độ', async () => {
    dung();

    await waitFor(() =>
      expect(screen.getByText('Bản đồ công trình và điểm đo')).toBeInTheDocument(),
    );
    expect(
      screen.getByText(/1 điểm đo chưa có toạ độ nên chưa lên bản đồ \(mục G8\)/),
    ).toBeInTheDocument();
  });

  /**
   * ⭐⭐ Hồi quy cho một lỗi THẬT, tìm ra lúc chạy bài kiểm đầu tiên của T35.1.
   *
   * Bản đầu đọc `lopDiemDo.data.chuaSoHoaViTri.length` với `?.` chỉ ở mức `data`. Một phản hồi
   * đúng kiểu nhưng sai HÌNH DẠNG (API cũ, thân rỗng, hay như ở đây: bộ giả trả `[]`) cho
   * `chuaSoHoaViTri === undefined`, và `undefined.length` ném ngay trong lúc render ⇒ **sập cả
   * màn hình điều hành** vì một lớp phụ.
   *
   * ⛔ TypeScript không thấy được điều đó — nó tin kiểu ta khai ở `api-types.ts`. Chỉ một lượt
   * chạy thật mới bắt được, và đó đúng là lý do bài này tồn tại.
   */
  it('⭐ lớp điểm đo trả hình dạng lạ ⇒ ⛔ KHÔNG làm sập dashboard', async () => {
    getGia.mockImplementation((url: string) => {
      if (url.startsWith('/hyd/stations/map-points')) return Promise.resolve([]);
      if (url.includes('map-points')) return Promise.resolve([]);
      return Promise.resolve(DASHBOARD);
    });
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/van-hanh/dieu-hanh']}>
          <OperationsDashboardPage />
        </MemoryRouter>
      </QueryClientProvider>,
    );

    // Phần còn lại của dashboard vẫn phải dựng được.
    await waitFor(() => expect(screen.getByTestId('luoi-kpi')).toBeInTheDocument());
    expect(screen.getByText('Công trình đang hoạt động')).toBeInTheDocument();
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
      // ⚠ 4 → 5 ở T35.3: dữ liệu mẫu thêm một ô cho nhánh "chưa có nguồn" sau khi hai ô thuỷ văn
      //   chuyển sang số thật. Con số này đếm DỮ LIỆU MẪU, ⛔ không phải số ô của backend.
      expect(screen.getByTestId('luoi-kpi').children).toHaveLength(5);
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
    expect(screen.getByTestId('luoi-kpi').children).toHaveLength(5);
    expect(screen.getByTestId('luoi-thong-ke').children).toHaveLength(3);
    expect(screen.getAllByText('Chưa có dữ liệu')).toHaveLength(1);
  });
});
