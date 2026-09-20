import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa một đơn vị → Lưu ⛔ đổi gì → payload phải mang lại ĐỦ 6 trường** — T47.17, và biểu mẫu này
 * mang thêm một vế thứ hai mà sáu bài trước ⛔ có: **⛔ được TRỘN hai đơn vị**.
 *
 * <h2>Vế 1 — giữ nguyên trường (T47.17)</h2>
 *
 * `address` · `phone` · `email` là **BA CỘT DUY NHẤT** nuôi bảng *Xí nghiệp trực thuộc* (CR-26)
 * trên cổng công khai. Ba cột ấy từng **ĐỌC ĐƯỢC MÀ ⛔ GHI ĐƯỢC** suốt một đợt (§10.62) — ⛔ biểu
 * mẫu nào có ô nhập — nên nhóm này có tiền sử. Đánh rơi một trường ⇒ mỗi lượt Lưu ghi đè giá trị
 * đang có bằng rỗng ⇒ bảng công khai **trống lần nữa**, và ⛔ thông báo nào.
 *
 * <h2>Vế 2 — ⛔ được trộn dữ liệu giữa hai đơn vị (T51.12 · T53.7)</h2>
 *
 * `EditOrgUnitModal` giữ {@code Form.useForm()} ở component **NGOÀI** `Modal` và được nơi gọi render
 * **vô điều kiện** ({@code <EditOrgUnitModal open={editing} unit={selected} …/>}, ⛔ phải
 * {@code editing ? … : null}). Đó đúng hình dạng đã gây ra T51.12 và tái phát ở màn hình thứ ba
 * (T53.7): `rc-field-form` chỉ áp {@code initialValues} khi {@code init}, nên *mở A → đóng → mở B*
 * có thể bày dữ liệu của **A** trong ô của **B**, rồi một lượt Lưu ghi hồ sơ A đè lên B kèm thông
 * báo *"Đã cập nhật đơn vị"*.
 *
 * <p>⚠ Ở đây {@code destroyOnHidden} + {@code preserve={false}} là **hai** biện pháp phòng, và
 * T53.7 đo được rằng **ba biện pháp phòng chồng nhau ⛔ cộng lại thành an toàn**. Nên vế này phải
 * được **ĐO**, ⛔ suy ra từ việc đọc mã: bài kiểm đi đúng đường người dùng đi.
 *
 * <p>⚠⚠ Và vế 2 ⛔ thay thế được vế 1: một biểu mẫu trộn dữ liệu vẫn *gửi đủ 6 khoá*, còn một biểu
 * mẫu đánh rơi trường vẫn *⛔ trộn ai với ai*. Hai kiểu hậu quả khác nhau ⇒ hai khẳng định.
 */

const GOC_KHO = join(dirname(new URL(import.meta.url).pathname), '../../../../..');
const DTOS = readFileSync(
  join(GOC_KHO, 'backend/core/src/main/java/com/songnhue/core/api/org/OrgUnitDtos.java'),
  'utf8',
);

function truongCuaRecord(ten: string): string[] {
  const m = new RegExp(`record\\s+${ten}\\s*\\(([\\s\\S]*?)\\)\\s*\\{`).exec(DTOS);
  if (!m) throw new Error(`Không tìm thấy record ${ten} trong OrgUnitDtos.java`);
  return m[1]
    .replace(/\/\*[\s\S]*?\*\//g, ' ')
    .replace(/\/\/.*$/gm, ' ')
    .replace(/@[\w.]+(\([^)]*\))?/g, ' ')
    .replace(/<[^<>]*>/g, '')
    .split(',')
    .map((phan) => phan.trim().split(/\s+/).pop() ?? '')
    .filter((t) => /^[a-z]\w*$/.test(t));
}

/**
 * Hai Xí nghiệp anh em, **mọi ô đều có giá trị và ⛔ ô nào trùng nhau**.
 *
 * ⚠ Ô rỗng làm bài mù trước vế *"trường bị đánh rơi"* (rỗng ↔ rơi ⛔ phân biệt được); giá trị trùng
 * làm bài mù trước vế *"trộn A vào B"*. Luật 9 ở cả hai chiều.
 */
const XN_A = {
  publicId: 'xn-a',
  code: 'XN01',
  name: 'Xí nghiệp Thuỷ lợi Hà Đông',
  shortName: 'XN Hà Đông',
  unitType: 'XI_NGHIEP' as const,
  path: '/cty/xn-a',
  depth: 1,
  sortOrder: 0,
  active: true,
  address: 'Số 1 Quang Trung, Hà Đông, Hà Nội',
  phone: '024.3355.1111',
  email: 'xn.hadong@thuyloisongnhue.vn',
  headUserPublicId: 'u-truong-a',
  deputyUserPublicId: 'u-pho-a',
  children: [],
};

const XN_B = {
  publicId: 'xn-b',
  code: 'XN02',
  name: 'Xí nghiệp Thuỷ lợi Thanh Trì',
  shortName: 'XN Thanh Trì',
  unitType: 'XI_NGHIEP' as const,
  path: '/cty/xn-b',
  depth: 1,
  sortOrder: 1,
  active: true,
  address: 'Km 8 Ngọc Hồi, Thanh Trì, Hà Nội',
  phone: '024.3688.2222',
  email: 'xn.thanhtri@thuyloisongnhue.vn',
  headUserPublicId: 'u-truong-b',
  deputyUserPublicId: 'u-pho-b',
  children: [],
};

/** Bốn tài khoản cho hai ô chọn trưởng/phó — H24. */
const TAI_KHOAN = [
  { publicId: 'u-truong-a', username: 'truonga', fullName: 'Trưởng A', status: 'ACTIVE' },
  { publicId: 'u-pho-a', username: 'phoa', fullName: 'Phó A', status: 'ACTIVE' },
  { publicId: 'u-truong-b', username: 'truongb', fullName: 'Trưởng B', status: 'ACTIVE' },
  { publicId: 'u-pho-b', username: 'phob', fullName: 'Phó B', status: 'ACTIVE' },
];

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => {
      if (duong === '/org-units/tree') return [XN_A, XN_B];
      // Ô chọn trưởng/phó nạp danh sách tài khoản — H24. Thiếu nhánh này thì `options` rỗng và
      // `Select` ⛔ hiện được nhãn, nhưng GIÁ TRỊ vẫn nằm trong form ⇒ vế "giữ nguyên trường"
      // của bài này vẫn đo đúng thứ nó cần đo.
      if (duong === '/admin/users') return TAI_KHOAN;
      return [];
    }),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return XN_A;
    }),
    post: vi.fn(async () => {
      throw new Error('Bài khứ hồi ⛔ được đi nhánh TẠO MỚI');
    }),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { OrgUnitsPage } = await import('./OrgUnitsPage');

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
          <OrgUnitsPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

/** Chọn một đơn vị trên cây rồi mở hộp thoại Sửa. */
async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, dv: typeof XN_A) {
  await nguoiDung.click(await screen.findByText(`${dv.name} (${dv.shortName})`));
  await nguoiDung.click(await screen.findByRole('button', { name: /Sửa thông tin/ }));
  await screen.findByText(`Sửa "${dv.name}"`);
}

afterEach(() => {
  cleanup();
  duongCuoi = '';
  thanCuoi = {};
});

describe('Đơn vị — vòng khứ hồi', () => {
  it('⚠ chống tập rỗng: đọc được 6 trường của OrgUnitDtos.UpdateRequest', () => {
    const truong = truongCuaRecord('UpdateRequest');
    expect(truong.length).toBeGreaterThanOrEqual(8);
    expect(truong).toEqual(
      expect.arrayContaining([
        'name',
        'shortName',
        'unitType',
        'address',
        'phone',
        'email',
        // H24 — hai ô quyết định AI NHẬN cảnh báo ngưỡng của G11. Đánh rơi chúng trong một lượt
        // sửa TÊN đơn vị là gỡ người nhận cảnh báo, im lặng.
        'headUserPublicId',
        'deputyUserPublicId',
      ]),
    );
  });

  it('⭐⭐ sửa đơn vị → Lưu ⛔ đổi gì ⇒ đủ 6 trường, ba cột nuôi cổng công khai còn nguyên', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, XN_A);
    await screen.findByDisplayValue(XN_A.address);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    const lech = truongCuaRecord('UpdateRequest')
      .map((t) => ({ t, kyVong: (XN_A as Record<string, unknown>)[t], thucTe: thanCuoi[t] }))
      .filter((x) => x.thucTe !== x.kyVong);

    expect(
      lech,
      '⛔ Trường bị đánh rơi sau một lượt Lưu ⛔ sửa gì. `address`/`phone`/`email` là BA CỘT DUY ' +
        'NHẤT nuôi bảng "Xí nghiệp trực thuộc" (CR-26) trên cổng công khai — ba cột ấy từng đọc ' +
        'được mà ⛔ ghi được suốt một đợt (§10.62), nên mất chúng là bảng công khai TRỐNG lần nữa.',
    ).toEqual([]);
  });

  it('⭐⭐ mở A → đóng → mở B ⇒ ô mang dữ liệu của B, và lượt Lưu ghi vào ĐÚNG B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, XN_A);
    await screen.findByDisplayValue(XN_A.address);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Hủy' }));

    await moSua(nguoiDung, XN_B);

    // Vế NHÌN THẤY: ô địa chỉ phải mang địa chỉ của B. Thiếu vế này thì một biểu mẫu trộn dữ liệu
    // vẫn đi lọt khi nơi gọi tình cờ gửi `publicId` đúng (T51.12 hỏng ở đúng chỗ ấy).
    expect(
      screen.queryByDisplayValue(XN_A.address),
      '⛔ Ô địa chỉ đang bày dữ liệu của đơn vị MỞ TRƯỚC. `Form.useForm()` nằm ở component ngoài ' +
        'Modal và nơi gọi render nó vô điều kiện ⇒ `initialValues` ⛔ được áp lại (T51.12 · T53.7).',
    ).toBeNull();
    await screen.findByDisplayValue(XN_B.address);

    await nguoiDung.click(await screen.findByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào đơn vị ĐANG mở').toBe(`/org-units/${XN_B.publicId}`);
    expect(
      thanCuoi,
      '⛔ Payload mang dữ liệu của đơn vị mở TRƯỚC ⇒ một lượt Lưu ghi hồ sơ A đè lên B, kèm thông ' +
        'báo "Đã cập nhật đơn vị".',
    ).toMatchObject({
      name: XN_B.name,
      shortName: XN_B.shortName,
      address: XN_B.address,
      phone: XN_B.phone,
      email: XN_B.email,
    });
  });
});
