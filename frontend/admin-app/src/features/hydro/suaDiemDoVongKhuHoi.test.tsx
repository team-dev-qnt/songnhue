import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import type { Station } from '@/shared/api-types';

/**
 * **Sửa điểm đo A → đóng → sửa điểm đo B ⇒ biểu mẫu mang dữ liệu của B** — T63.17.
 *
 * <h2>⭐ Bài này canh một cơ chế KHÁC với `LienKetCongTrinhModal`, và đó là điểm của nó</h2>
 *
 * {@code StationsPage} dùng <b>một</b> {@code Form.useForm()} cho <b>hai</b> hộp thoại (Thêm và
 * Sửa) — đúng cái hình dạng T51.12 cảnh báo. Nhưng nó ⛔ dựa vào {@code initialValues}: hàm
 * {@code moSua} gọi {@code form.resetFields()} rồi {@code form.setFieldsValue(<đủ 14 trường>)}
 * <b>ngay ở handler mở</b>. Đo trong {@code rc-field-form@2.7.1}: {@code resetFields()} ⛔ phải
 * một phép xoá mềm — nó đặt {@code store = merge(this.initialValues)}, tức <b>thay toàn phần</b>;
 * rồi {@code setFieldsValue} ghi bản ghi đang mở đè lên. ⇒ Cùng một bảo đảm với khuôn
 * {@code useLayoutEffect}, chỉ đặt ở chỗ khác.
 *
 * <p>⇒ Bài này <b>ĐO</b> điều đó thay vì suy từ việc đọc mã (luật 7), và nó là phép kiểm giữ chỗ
 * cho <b>cả một nhóm</b> màn hình cùng khuôn: {@code PositionsPage} · {@code AlertLevelsPage} ·
 * {@code AlertRulesPage} · {@code ApiSourcesPage} · {@code MeasurementTypesPage} ·
 * {@code OperationStatusCodesPage}. Ngày ai đó gỡ {@code setFieldsValue} khỏi handler mở — hoặc
 * thêm một trường vào biểu mẫu mà quên thêm vào {@code moSua} — bài này đỏ.
 */

function dungDiemDo(p: Partial<Station> & Pick<Station, 'id' | 'code' | 'name'>): Station {
  return {
    apiCode: 'F01501',
    apiSourceId: null,
    apiSourceCode: null,
    positionRole: 'THUONG_LUU',
    orgUnitId: null,
    orgUnitName: null,
    riverName: null,
    chainage: null,
    chainageM: null,
    latitude: null,
    longitude: null,
    interpolated: false,
    active: true,
    description: null,
    measurementTypes: [],
    constructions: [],
    thieuLienKetCongTrinh: false,
    chuaGanDonVi: false,
    ...p,
  } as Station;
}

const A = dungDiemDo({
  id: 'tram-a',
  code: 'DO-LMAC-TL',
  name: 'Cống Liên Mạc — thượng lưu',
  apiCode: 'F01501',
  riverName: 'Sông Nhuệ',
  chainage: 'K0+250',
});

/** ⛔ ô nào trùng A — trùng thì hai trạng thái ra cùng một màn hình (luật 9). */
const B = dungDiemDo({
  id: 'tram-b',
  code: 'DO-VDINH-HL',
  name: 'Cống Vân Đình — hạ lưu',
  apiCode: 'F01502',
  positionRole: 'HA_LUU',
  riverName: 'Sông Đáy',
  chainage: 'K12+800',
});

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => (duong === '/hyd/stations' ? [A, B] : [])),
    getPage: vi.fn(async () => ({
      items: [],
      meta: { page: 0, size: 20, total: 0, totalPages: 0 },
    })),
    put: vi.fn(async () => ({})),
    post: vi.fn(async () => ({})),
    delete: vi.fn(async () => ({})),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { StationsPage } = await import('./StationsPage');

/** ⚠ MỘT `QueryClient` cho cả bài — provider mới giữa chừng làm vế A → B xanh giả. */
let qc: QueryClient;

function dung() {
  const chuaKhai = () => {
    throw new Error('AuthContext giả: bài kiểm này ⛔ dựng phần đó');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'admin' },
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
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>
          <MemoryRouter>
            <StationsPage />
          </MemoryRouter>
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, s: Station) {
  const hang = (await screen.findByText(s.code)).closest('tr');
  if (!hang) throw new Error(`⛔ tìm thấy hàng của ${s.code}`);
  const nut = hang.querySelector('button[aria-label="Sửa"]');
  if (!nut) throw new Error('⛔ tìm thấy nút Sửa của hàng');
  await nguoiDung.click(nut);
  await screen.findByText(`Sửa điểm đo ${s.code}`);
}

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
});

afterEach(() => {
  cleanup();
  qc.clear();
});

describe('Sửa điểm đo — vòng khứ hồi giữa hai điểm đo', () => {
  it('⚠ chống tập rỗng: mở A ⇒ biểu mẫu bày đúng dữ liệu của A', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await moSua(nguoiDung, A);
    expect(screen.getByLabelText('Mã nội bộ')).toHaveValue(A.code);
    expect(screen.getByLabelText('Tên điểm đo')).toHaveValue(A.name);
  });

  it('⭐⭐ mở A → đóng → mở B ⇒ ô mang dữ liệu của B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, A);
    await screen.findByDisplayValue(A.code);
    await nguoiDung.click(screen.getByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, B);

    expect(
      screen.queryByDisplayValue(A.code),
      '⛔ Biểu mẫu đang bày mã nội bộ của điểm đo MỞ TRƯỚC. `moSua` phải gọi `resetFields()` rồi ' +
        '`setFieldsValue(<đủ trường>)` — nếu một trường rơi khỏi `moSua` thì nó mang giá trị của ' +
        'điểm đo trước, và `PUT` là thay toàn phần.',
    ).toBeNull();
    expect(screen.getByLabelText('Mã nội bộ')).toHaveValue(B.code);
    expect(screen.getByLabelText('Tên điểm đo')).toHaveValue(B.name);
    expect(screen.getByLabelText('Tuyến sông')).toHaveValue(B.riverName);
    expect(screen.getByLabelText('Lý trình')).toHaveValue(B.chainage);
  });
});
