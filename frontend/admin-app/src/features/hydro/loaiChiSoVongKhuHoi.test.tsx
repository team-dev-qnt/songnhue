import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một loại chỉ số → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 7 trường** — T47.17.
 *
 * <h2>Hai mặc định LẶNG của biểu mẫu này</h2>
 *
 * `MeasurementTypeService` quy `valueScale == null ⇒ 3` và `sortOrder == null ⇒ 0`. Nên đánh rơi
 * một trong hai ⛔ sinh lỗi nào, ⛔ dòng log nào:
 *
 * <ul>
 *   <li><b>`valueScale`</b> — số chữ số thập phân **hiển thị và làm tròn**. Lượng mưa khai `1` bị
 *       đẩy về `3` là mọi bảng, biểu đồ và bản kết xuất đổi cách đọc số cùng lúc; ngược lại mực
 *       nước khai `3` (tới **mm**) tụt xuống ít chữ số hơn là **mất độ phân giải của số đo**.
 *   <li><b>`sortOrder`</b> — thứ tự danh mục, tức thứ tự cột trên biểu tổng hợp theo tuyến sông và
 *       thứ tự ô trên dashboard trực ban. Về `0` là cả danh mục xáo lại, và ⛔ ai bấm gì cả.
 * </ul>
 *
 * <p>⚠ `active` cũng là một mặc định lặng ở tầng dưới, nên dữ liệu thử dùng **`active: false`** có
 * chủ đích: với `true` thì đánh rơi trường ấy cho ra ĐÚNG giá trị cũ và bài kiểm ⛔ phân biệt được
 * hai trạng thái (luật 9 — cùng lý do đã ghi ở `nguongCanhBaoVongKhuHoi.test.tsx`).
 *
 * <p>⚠ Ô **Mã** khoá lại khi sửa (`disabled`) — mã là khoá nghiệp vụ. Nó vẫn phải có trong payload:
 * `MeasurementTypeRequest.code` khai `@NotBlank`, nên đánh rơi nó là **400 ở mọi lượt sửa**.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/hydro/src/main/java/com/songnhue/hydro/api/HydroCatalogDtos.java'),
  'utf8',
);

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

/** Hai loại chỉ số, mọi ô khác nhau — gồm `valueScale` khác nhau, đúng chỗ mặc định lặng nằm. */
const LUONG_MUA = {
  id: 'lcs-a',
  code: 'LUONG_MUA',
  name: 'Lượng mưa',
  unit: 'mm',
  valueScale: 1,
  sortOrder: 20,
  active: false,
  description: 'Tạm ngừng dùng cho tới khi Công ty chốt nguồn số liệu mưa (G3-a)',
};

const MUC_NUOC = {
  id: 'lcs-b',
  code: 'MUC_NUOC',
  name: 'Mực nước',
  unit: 'm',
  valueScale: 3,
  sortOrder: 10,
  active: true,
  description: 'Chỉ số chính của toàn hệ, lưu tới mm',
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/hyd/measurement-types') return [LUONG_MUA, MUC_NUOC];
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return LUONG_MUA;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { MeasurementTypesPage } = await import('./MeasurementTypesPage');

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
          <MeasurementTypesPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, loai: typeof LUONG_MUA) {
  await nguoiDung.click(
    await screen.findByRole('button', { name: `Sửa loại chỉ số ${loai.code}` }),
  );
  await screen.findByText('Sửa loại chỉ số');
  await screen.findByDisplayValue(loai.name);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Loại chỉ số — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 7 trường của MeasurementTypeRequest', () => {
    const truong = truongCuaRecord('MeasurementTypeRequest');
    expect(truong.length).toBeGreaterThanOrEqual(7);
    expect(truong).toEqual(
      expect.arrayContaining(['code', 'name', 'unit', 'valueScale', 'sortOrder', 'active']),
    );
  });

  it('⭐⭐ sửa loại chỉ số → Lưu ⛔ đổi gì ⇒ đủ 7 trường, `valueScale` và `sortOrder` còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, LUONG_MUA);
    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('MeasurementTypeRequest')
      .map((t) => ({ t, kyVong: (LUONG_MUA as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `valueScale == null ⇒ 3` và ' +
        '`sortOrder == null ⇒ 0` là hai mặc định LẶNG: lượng mưa khai 1 chữ số bị đẩy về 3, mực ' +
        'nước mất độ phân giải mm, và cả danh mục xáo lại thứ tự — ⛔ một dòng lỗi nào.',
    ).toEqual([]);

    expect(thanCuoi.active, 'loại chỉ số đang TẮT ⛔ được tự bật lại sau một lượt Lưu').toBe(false);
  });

  it('⭐⭐ mở loại A → đóng → mở loại B ⇒ ô mang dữ liệu của B, Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, LUONG_MUA);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Cancel' }));

    await moSua(nguoiDung, MUC_NUOC);
    expect(
      screen.queryByDisplayValue(LUONG_MUA.name),
      '⛔ Ô Tên đang bày dữ liệu của loại chỉ số MỞ TRƯỚC (T63.12 · T51.12).',
    ).toBeNull();

    await nguoiDung.click(await screen.findByRole('button', { name: 'OK' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi).toBe(`/hyd/measurement-types/${MUC_NUOC.id}`);
    expect(thanCuoi).toMatchObject({
      code: MUC_NUOC.code,
      name: MUC_NUOC.name,
      unit: MUC_NUOC.unit,
      valueScale: MUC_NUOC.valueScale,
      sortOrder: MUC_NUOC.sortOrder,
      active: MUC_NUOC.active,
    });
  });
});
