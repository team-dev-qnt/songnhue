import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';

/**
 * **Sửa tài khoản A → đóng → sửa tài khoản B ⇒ biểu mẫu phải mang dữ liệu của B** — T63.17,
 * hình dạng T51.12.
 *
 * <h2>Vì sao màn hình này nằm trong nhóm nguy hiểm</h2>
 *
 * {@code UsersPage} render {@code <EditUserModal user={editing} …/>} <b>vô điều kiện</b> ở dòng
 * cuối, và {@code EditUserModal} giữ {@code Form.useForm()} ở component NGOÀI {@code Modal} với
 * {@code initialValues} <b>phụ thuộc bản ghi</b> ({@code user?.fullName} …). Đo trong
 * {@code rc-field-form@2.7.1}: {@code setInitialValues} chạy {@code merge(initialValues, store)}
 * ⇒ <b>kho giá trị THẮNG {@code initialValues}</b>.
 *
 * <p>⚠ Ở đây có {@code preserve={false}}, thứ <i>về nguyên tắc</i> dọn kho khi Form unmount
 * ({@code prevWithoutPreserves}). Nhưng T53.7 đã đo: {@code destroyOnHidden} chỉ tháo cây con
 * <b>sau khi hoạt ảnh đóng chạy xong</b> — mở lại trước lúc ấy là kho còn nguyên của tài khoản
 * TRƯỚC. ⇒ Đây là một <b>cuộc đua</b>, ⛔ một bảo đảm; và luật 7 nói một cơ chế chưa ai đi qua thì
 * chưa biết nó đúng hay sai ⇒ vế này phải được <b>ĐO</b>.
 *
 * <p>⛔⛔ Hậu quả: {@code PUT /admin/users/{publicId}} là <b>thay toàn phần</b> ba trường
 * danh tính. Một lượt Lưu trộn ⇒ họ tên, email và số điện thoại của người A ghi đè lên tài khoản
 * của người B, kèm thông báo <i>"Đã cập nhật"</i>. Email ấy là nơi hệ thống gửi cảnh báo và thư
 * đặt lại mật khẩu.
 */

const A = {
  publicId: 'u-a',
  username: 'thang.nv',
  fullName: 'Nguyễn Văn Thắng',
  email: 'thang.nv@thuyloisongnhue.vn',
  phone: '024.3355.1188',
  status: 'ACTIVE',
  roles: [],
  orgUnitId: null,
  orgUnitName: null,
  employeePublicId: null,
  employeeCode: null,
  employeeFullName: null,
  twoFactorEnabled: false,
  lastLoginAt: null,
  createdAt: '2026-01-01T00:00:00Z',
};

/** ⛔ ô nào trùng A — nếu trùng thì hai trạng thái ra cùng một màn hình (luật 9). */
const B = {
  ...A,
  publicId: 'u-b',
  username: 'hoa.tt',
  fullName: 'Trần Thị Hoà',
  email: 'hoa.tt@thuyloisongnhue.vn',
  phone: '024.3688.2299',
};

let duongCuoi = '';
let thanCuoi: Record<string, unknown> = {};

vi.mock('@/shared/apiClient', () => ({
  ApiClientError: class extends Error {},
  api: {
    get: vi.fn(async (duong: string) => (duong === '/admin/users' ? [A, B] : [])),
    getPage: vi.fn(async () => ({
      items: [],
      meta: { page: 0, size: 20, total: 0, totalPages: 0 },
    })),
    put: vi.fn(async (duong: string, than: unknown) => {
      duongCuoi = duong;
      thanCuoi = than as Record<string, unknown>;
      return A;
    }),
    post: vi.fn(async () => ({})),
    delete: vi.fn(async () => ({})),
  },
}));

vi.mock('@/shared/loiTheoTruong', () => ({ datLoiTheoTruong: () => false }));

const { UsersPage } = await import('./UsersPage');

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
          <UsersPage />
        </AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

async function moSua(nguoiDung: ReturnType<typeof userEvent.setup>, u: typeof A) {
  const hang = (await screen.findByText(u.username)).closest('tr');
  if (!hang) throw new Error(`⛔ tìm thấy hàng của ${u.username}`);
  const nut = [...hang.querySelectorAll('button')].find((b) => b.textContent?.trim() === 'Sửa');
  if (!nut) throw new Error('⛔ tìm thấy nút Sửa — bài kiểm ⛔ còn đi đúng đường người dùng đi');
  await nguoiDung.click(nut);
  await screen.findByText(`Sửa tài khoản ${u.username}`);
}

beforeEach(() => {
  qc = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  duongCuoi = '';
  thanCuoi = {};
});

afterEach(() => {
  cleanup();
  qc.clear();
});

describe('Sửa tài khoản — vòng khứ hồi giữa hai người dùng', () => {
  it('⚠ chống tập rỗng: mở A ⇒ ba ô danh tính bày đúng dữ liệu của A', async () => {
    const nguoiDung = userEvent.setup();
    dung();
    await moSua(nguoiDung, A);
    expect(screen.getByLabelText('Họ và tên')).toHaveValue(A.fullName);
    expect(screen.getByLabelText('Email')).toHaveValue(A.email);
    expect(screen.getByLabelText('Điện thoại')).toHaveValue(A.phone);
  });

  it('⭐⭐ mở A → đóng → mở B ⇒ ba ô là của B, ⛔ giữ lại của A', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, A);
    await screen.findByDisplayValue(A.fullName);
    await nguoiDung.click(screen.getByRole('button', { name: 'Hủy' }));

    await moSua(nguoiDung, B);

    expect(
      screen.queryByDisplayValue(A.fullName),
      '⛔ Biểu mẫu đang bày danh tính của tài khoản MỞ TRƯỚC. `Form.useForm()` nằm ngoài Modal và ' +
        'UsersPage render EditUserModal vô điều kiện ⇒ hình dạng T51.12. `preserve={false}` chỉ ' +
        'dọn SAU khi hoạt ảnh đóng chạy xong — một cuộc đua, ⛔ một bảo đảm.',
    ).toBeNull();
    expect(screen.getByLabelText('Họ và tên')).toHaveValue(B.fullName);
    expect(screen.getByLabelText('Email')).toHaveValue(B.email);
    expect(screen.getByLabelText('Điện thoại')).toHaveValue(B.phone);
  });

  it('⭐⭐ và lượt Lưu ghi ĐÚNG B — ⛔ đẩy danh tính của A sang tài khoản B', async () => {
    const nguoiDung = userEvent.setup();
    dung();

    await moSua(nguoiDung, A);
    await screen.findByDisplayValue(A.fullName);
    await nguoiDung.click(screen.getByRole('button', { name: 'Hủy' }));
    await moSua(nguoiDung, B);

    await nguoiDung.click(screen.getByRole('button', { name: 'Lưu' }));
    await waitFor(() => expect(Object.keys(thanCuoi).length).toBeGreaterThan(0));

    expect(duongCuoi, 'lượt Lưu phải ghi vào tài khoản ĐANG mở').toBe(`/admin/users/${B.publicId}`);
    expect(
      thanCuoi,
      '⛔ Payload mang danh tính của tài khoản mở TRƯỚC. `PUT` là thay toàn phần ⇒ email nhận cảnh ' +
        'báo và thư đặt lại mật khẩu của B bị thay bằng của A, kèm thông báo "Đã cập nhật".',
    ).toMatchObject({ fullName: B.fullName, email: B.email, phone: B.phone });
  });
});
