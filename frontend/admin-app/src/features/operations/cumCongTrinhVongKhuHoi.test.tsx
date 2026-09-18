import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một cụm công trình → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 5 trường** — T47.17.
 *
 * <h2>Hậu quả nghiệp vụ của từng trường bị đánh rơi</h2>
 *
 * <ul>
 *   <li><b>`sortOrder`</b> — {@code ConstructionClusterController} quy
 *       {@code request.sortOrder() == null ? 0 : …}, một mặc định **LẶNG**. Rơi ⇒ mỗi lượt sửa kéo
 *       một cụm về **0**, và khi mọi cụm đã về 0 thì thứ tự danh mục rơi vào thứ tự CSDL trả về —
 *       đúng hình dạng §11.12 (`sort_order` là một núm ⛔ điều khiển gì). ⛔ Có dòng log nào.
 *   <li><b>`description`</b> — {@code cum.setDescription(...)} chạy **VÔ ĐIỀU KIỆN** ⇒ rơi = **xoá
 *       trắng**, ⛔ một thông báo (§11.19).
 *   <li><b>`orgUnitId`</b> — {@code @NotNull} ⇒ **400 ở mọi lượt sửa**. Đơn vị quản lý là thứ cắt
 *       phạm vi dữ liệu của cụm; ⛔ có đường đoán lại từ đâu.
 *   <li><b>`code`</b> — {@code @NotBlank}, và nó ⛔ phải một cái nhãn: **mã cụm khai ở đây chính là
 *       giá trị hợp lệ của cột `ma_cum`** trong tệp nhập danh mục công trình (T42.23). Đổi hoặc
 *       mất nó là mọi tệp Công ty gửi có điền `ma_cum` đều ra lỗi dòng.
 *   <li><b>`name`</b> — {@code @NotBlank} ⇒ 400.
 * </ul>
 *
 * <h2>⚠ Vì sao biểu mẫu này có tiền sử</h2>
 *
 * <p>Ba endpoint ghi của cụm có **0 nơi gọi** kể từ khi ra đời cho tới khi màn hình này được dựng
 * (T42.23) — tức vòng *nhập → lưu → hiện* mới khép lại **một lần duy nhất**, và ⛔ lượt rà nào đi
 * qua nó lần thứ hai. Đây là lượt ấy.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(
    GOC_KHO,
    'backend/operations/src/main/java/com/songnhue/operations/api/ConstructionDtos.java',
  ),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong ConstructionDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

const XN_HA_DONG = {
  publicId: 'xn-ha-dong',
  code: 'XN01',
  name: 'Xí nghiệp Thuỷ lợi Hà Đông',
  shortName: 'XN Hà Đông',
  unitType: 'XI_NGHIEP' as const,
  path: '/cty/xn-ha-dong',
  depth: 1,
  sortOrder: 0,
  active: true,
  children: [],
};

const XN_THANH_TRI = {
  publicId: 'xn-thanh-tri',
  code: 'XN02',
  name: 'Xí nghiệp Thuỷ lợi Thanh Trì',
  shortName: 'XN Thanh Trì',
  unitType: 'XI_NGHIEP' as const,
  path: '/cty/xn-thanh-tri',
  depth: 1,
  sortOrder: 1,
  active: true,
  children: [],
};

/**
 * Hai cụm, **mọi ô có giá trị và ⛔ ô nào trùng nhau** (luật 9 ở cả hai chiều).
 *
 * ⚠ `sortOrder` của cả hai đều **khác 0** có chủ đích: 0 là đúng giá trị mà mặc định lặng của
 * controller sinh ra, nên một cụm khai `sortOrder: 0` làm bài mù trước lượt rơi.
 */
const CUM_LIEN_MAC = {
  publicId: 'cum-lien-mac',
  code: 'CUM-LMAC',
  name: 'Cụm đầu mối Liên Mạc',
  orgUnitId: XN_HA_DONG.publicId,
  orgUnitName: XN_HA_DONG.shortName,
  description: 'Gồm cống Liên Mạc và 4 trạm bơm ven sông Hồng',
  sortOrder: 5,
  active: true,
};

const CUM_TIEU_NOI_THI = {
  publicId: 'cum-tieu-noi-thi',
  code: 'CUM-TNT',
  name: 'Cụm trạm bơm tiêu nội thị',
  orgUnitId: XN_THANH_TRI.publicId,
  orgUnitName: XN_THANH_TRI.shortName,
  description: 'Bốn trạm bơm tiêu bàn giao từ Xí nghiệp Thanh Trì năm 2021',
  sortOrder: 9,
  active: true,
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/ops/construction-clusters') return [CUM_LIEN_MAC, CUM_TIEU_NOI_THI];
      if (duong === '/org-units/selectable') return [XN_HA_DONG, XN_THANH_TRI];
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return CUM_LIEN_MAC;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { ConstructionClustersPage } = await import('./ConstructionClustersPage');

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
          <ConstructionClustersPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, cum: typeof CUM_LIEN_MAC) {
  await nguoiDung.click(await screen.findByRole('button', { name: `Sửa cụm ${cum.code}` }));
  await screen.findByDisplayValue(cum.description);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Cụm công trình — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 5 trường của ClusterRequest', () => {
    const truong = truongCuaRecord('ClusterRequest');
    expect(truong.length).toBeGreaterThanOrEqual(5);
    expect(truong).toEqual(
      expect.arrayContaining(['code', 'name', 'orgUnitId', 'description', 'sortOrder']),
    );
  });

  it('⭐⭐ sửa cụm → Lưu ⛔ đổi gì ⇒ đủ 5 trường, `sortOrder` ⛔ tụt về 0', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, CUM_LIEN_MAC);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('ClusterRequest')
      .map((t) => ({
        t,
        kyVong: (CUM_LIEN_MAC as Record<string, unknown>)[t],
        thucTe: thanCuoi[t],
      }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `description` bị ghi đè VÔ ĐIỀU KIỆN nên ' +
        'rơi = xoá trắng; `orgUnitId` là @NotNull nên rơi = 400 ở MỌI lượt sửa; `code` là giá trị ' +
        'hợp lệ của cột `ma_cum` trong tệp nhập danh mục công trình (T42.23).',
    ).toEqual([]);

    expect(
      thanCuoi.sortOrder,
      '⛔⛔ Controller quy `sortOrder == null ⇒ 0`. Đánh rơi trường này là mỗi lượt sửa kéo một cụm ' +
        'về 0, và khi mọi cụm đã về 0 thì thứ tự danh mục rơi vào thứ tự CSDL trả về (§11.12).',
    ).toBe(CUM_LIEN_MAC.sortOrder);
  });

  it('⭐⭐ mở cụm A → đóng → mở cụm B ⇒ ô mang dữ liệu của B, Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, CUM_LIEN_MAC);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Huỷ' }));

    await moSua(nguoiDung, CUM_TIEU_NOI_THI);
    expect(
      screen.queryByDisplayValue(CUM_LIEN_MAC.description),
      '⛔ Ô Mô tả đang bày dữ liệu của cụm MỞ TRƯỚC (T63.12 · T51.12 · T53.7).',
    ).toBeNull();
    expect(
      screen.queryByDisplayValue(CUM_LIEN_MAC.code),
      '⛔ Ô Mã cụm đang bày mã của cụm MỞ TRƯỚC ⇒ lượt Lưu đổi `ma_cum` của B thành mã của A, và ' +
        'mọi tệp nhập công trình đang điền mã cũ sẽ ra lỗi dòng.',
    ).toBeNull();

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào cụm ĐANG mở').toBe(
      `/ops/construction-clusters/${CUM_TIEU_NOI_THI.publicId}`,
    );
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của cụm mở TRƯỚC ⇒ một lượt Lưu ghi hồ sơ A đè lên B kèm thông báo ' +
        '"Đã lưu thay đổi". `orgUnitId` sai là cả cụm đổi Xí nghiệp quản lý.',
    ).toMatchObject({
      code: CUM_TIEU_NOI_THI.code,
      name: CUM_TIEU_NOI_THI.name,
      orgUnitId: CUM_TIEU_NOI_THI.orgUnitId,
      description: CUM_TIEU_NOI_THI.description,
      sortOrder: CUM_TIEU_NOI_THI.sortOrder,
    });
  });
});
