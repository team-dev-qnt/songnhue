import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Mở điểm đo đủ mọi ô → bấm Lưu ⛔ sửa gì → payload phải mang lại ĐỦ 14 trường** — T47.17.
 *
 * <h2>Vì sao biểu mẫu này xếp NGUY HIỂM NHẤT trong nhóm còn lại</h2>
 *
 * `latitude` · `longitude` · `riverName` · `chainage` là **dữ liệu G8 nhập tay** — nguồn thuỷ văn
 * ⛔ trả toạ độ, Công ty gửi bằng bản chụp, và quy tắc 18 nói mất là mất **vĩnh viễn**. Chúng đổ
 * thẳng ra lớp điểm đo của **cổng công khai**. Và đây đúng là chỗ §11.19 đã xảy ra một lần: `PUT`
 * điểm đo **xoá trắng** tuyến sông/lý trình khi thân JSON thiếu trường, vô hình suốt từ WS-29 vì
 * mọi ô vốn đã NULL.
 *
 * <p>Bản vá lần ấy nằm ở **backend** và có bài kiểm BE (`HydroCatalogueHttpTest:502`). Bài này canh
 * nửa còn lại: **biểu mẫu**. Một bài *"PUT nguyên văn thân GET"* xanh ở CẢ HAI trạng thái vì tầng
 * HTTP round-trip hoàn hảo — thứ đánh rơi trường là ô nhập.
 *
 * <h2>Rủi ro thật ở màn hình này</h2>
 *
 * `moSua` nạp đủ 14 trường và `onOk` gửi nguyên `values`. Nên khe hở ⛔ nằm ở phép ánh xạ mà ở chỗ
 * <b>một ô được NẠP nhưng ⛔ được RENDER</b>: `form.validateFields()` chỉ trả về trường có
 * {@code Form.Item} đang gắn. Một ô bị bọc vào nhánh điều kiện, hay bị gỡ khỏi `truongChung`, sẽ
 * biến mất khỏi payload mà ⛔ một dòng lỗi nào.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hydro/src/main/java/com/songnhue/hydro/api/HydroCatalogDtos.java'),
  'utf8',
);

/** Tên thành phần của một `record` trong `HydroCatalogDtos.java` — ĐỌC từ nguồn (luật 14). */
function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong HydroCatalogDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/** Điểm đo mang GIÁ TRỊ Ở MỌI Ô — một ô `null` là một trường bài này ⛔ thấy nếu bị đánh rơi. */
const DIEM_DO = {
  id: 'tram-1',
  code: 'TL-001',
  name: 'Trạm Lương Cổ',
  apiCode: 'F01519',
  apiSourceId: 'nguon-1',
  apiSourceCode: 'BHH40',
  positionRole: 'HA_LUU',
  orgUnitId: 'dv-1',
  orgUnitName: 'Xí nghiệp A',
  riverName: 'Sông Nhuệ',
  chainage: 'K12+500',
  chainageM: 12500,
  latitude: 21.048201,
  longitude: 105.7825,
  interpolated: true,
  active: true,
  description: 'Mô tả điểm đo để bài kiểm thấy nếu nó biến mất',
  measurementTypes: [{ id: 'ct-1', code: 'MN', name: 'Mực nước' }],
  constructions: [],
  thieuLienKetCongTrinh: false,
  chuaGanDonVi: false,
};

const capNhat = vi.fn(async () => DIEM_DO);

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hyd/stations') return [DIEM_DO];
      if (duong === '/hyd/api-sources') return [{ id: 'nguon-1', code: 'BHH40', name: 'BHH40' }];
      if (duong === '/hyd/measurement-types') {
        return [{ id: 'ct-1', code: 'MN', name: 'Mực nước', unit: 'm' }];
      }
      if (duong === '/org-units/tree') {
        return [{ publicId: 'dv-1', name: 'Xí nghiệp A', depth: 0, children: [] }];
      }
      return [];
    }),
    put: vi.fn(async (_duong: string, than: unknown) => {
      capNhat();
      thanCuoi = than as Record<string, unknown>;
      return DIEM_DO;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
  },
}));

let thanCuoi: Record<string, unknown> = {};

const { StationsPage } = await import('./StationsPage');

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
            <StationsPage />
          </MemoryRouter>
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** Hình dạng dây khác hình dạng đọc ở đúng MỘT chỗ — khai ra thay vì chép phép ánh xạ. */
function kyVong(truong: string): unknown {
  if (truong === 'measurementTypeIds') return DIEM_DO.measurementTypes.map((t) => t.id);
  return (DIEM_DO as Record<string, unknown>)[truong];
}

afterEach(() => {
  cleanup();
  capNhat.mockClear();
  thanCuoi = {};
});

describe('Điểm đo — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được ≥ 12 trường từ StationRequest, có đủ nhóm G8', () => {
    const truong = truongCuaRecord('StationRequest');
    expect(truong.length).toBeGreaterThanOrEqual(12);
    expect(truong).toEqual(
      expect.arrayContaining([
        'riverName',
        'chainage',
        'latitude',
        'longitude',
        'measurementTypeIds',
      ]),
    );
  });

  it('⭐⭐ mở điểm đo → Lưu ⛔ sửa gì ⇒ payload mang đủ 14 trường, đúng giá trị đã nạp', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await screen.findByText(DIEM_DO.name);
    await nguoiDung.click((await screen.findAllByRole('button', { name: /Sửa/ }))[0]);
    await screen.findByDisplayValue(DIEM_DO.name);

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(capNhat).toHaveBeenCalled());

    const lech = truongCuaRecord('StationRequest')
      .map((t) => ({ t, kyVong: kyVong(t), thucTe: thanCuoi[t] }))
      .filter((x) => JSON.stringify(x.thucTe) !== JSON.stringify(x.kyVong));

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `latitude`/`longitude`/`riverName`/' +
        '`chainage` là dữ liệu G8 NHẬP TAY — nguồn thuỷ văn ⛔ trả toạ độ, nên mất là mất vĩnh viễn ' +
        '(quy tắc 18). Đây đúng chỗ §11.19 đã xảy ra một lần ở tầng backend.',
    ).toEqual([]);
  });
});
