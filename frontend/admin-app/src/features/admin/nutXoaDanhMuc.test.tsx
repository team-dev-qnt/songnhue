import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App as AntdApp } from 'antd';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { AuthContext, type AuthContextValue } from '@/app/auth/AuthContext';
import { type ApiSource, type UserView } from '@/shared/api-types';
import type * as ApiClientModule from '@/shared/apiClient';

/**
 * **Nút xoá tài khoản và nguồn dữ liệu** — T61.21 (QuanTran chốt 14/09/2026: có nút cho hai danh mục
 * này, điểm đo CỐ Ý ⛔ có). Canh ĐƯỜNG + ĐỘNG TỪ, và việc ẩn nút ở dòng của chính mình.
 * Vế backend: `XoaTaiKhoanHttpTest` (ADM-2020, mất quyền ngay) · `HydroCatalogueHttpTest` (HYD-1002).
 */

const goi = vi.fn();

const TAI_KHOAN: UserView[] = [
  {
    publicId: 'toi',
    username: 'quantri',
    fullName: 'Tôi',
    email: null,
    phone: null,
    status: 'ACTIVE',
    mustChangePassword: false,
    twoFactorRequired: false,
    lastLoginAt: null,
    hoSoNhanSu: null,
  },
  {
    publicId: 'khac',
    username: 'taonham',
    fullName: 'Tạo nhầm',
    email: null,
    phone: null,
    status: 'ACTIVE',
    mustChangePassword: false,
    twoFactorRequired: false,
    lastLoginAt: null,
    hoSoNhanSu: null,
  },
];

const NGUON = [
  {
    id: 'ng-1',
    code: 'BHH40',
    name: 'Nguồn thử',
    adapterType: 'BHH40',
    baseUrl: 'http://x',
    credentialDaCauHinh: true,
    status: 'ACTIVE',
  } as unknown as ApiSource,
];

vi.mock('@/shared/apiClient', async (importOriginal) => {
  const thuc = await importOriginal<typeof ApiClientModule>();
  return {
    ...thuc,
    api: {
      ...thuc.api,
      get: vi.fn(async (url: string) =>
        url === '/admin/users' ? TAI_KHOAN : url === '/hyd/api-sources' ? NGUON : [],
      ),
      delete: vi.fn(async (url: string) => {
        goi('DELETE', url);
      }),
      // ⚠ T61.31 — mock PHẢI chuyển tiếp cả THÂN yêu cầu: bản cũ chỉ ghi lại đường dẫn, nên một
      //   lượt gửi thiếu mật khẩu tạm vẫn xanh (luật 9 — hai trạng thái ⛔ phân biệt được).
      post: vi.fn(async (url: string, than?: unknown) => {
        goi('POST', url, than);
      }),
    },
  };
});

const { UsersPage } = await import('./UsersPage');
const { ApiSourcesPage } = await import('@/features/hydro/ApiSourcesPage');

function dung(trang: React.ReactNode, quyen: string[]) {
  const khongDung = () => {
    throw new Error('AuthContext giả');
  };
  const auth = {
    status: 'authenticated' as const,
    user: { id: 'toi', username: 'quantri' },
    hasPermission: (q: string) => quyen.includes(q),
    hasRole: () => false,
    maintenance: false,
    login: khongDung,
    verifyTwoFactor: khongDung,
    confirmEnrollment: khongDung,
    logout: khongDung,
    endSession: khongDung,
    reloadProfile: khongDung,
  } as unknown as AuthContextValue;
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <AuthContext.Provider value={auth}>
        <AntdApp>{trang}</AntdApp>
      </AuthContext.Provider>
    </QueryClientProvider>,
  );
}

afterEach(() => {
  cleanup();
  goi.mockClear();
});

describe('Nút xoá danh mục — T61.21', () => {
  it('⭐⭐ xoá tài khoản khác gọi DELETE đúng đường; dòng của CHÍNH MÌNH ⛔ có nút', async () => {
    const nguoiDung = userEvent.setup();
    dung(<UsersPage />, ['adm:user:view', 'adm:user:update']);
    await screen.findByText('taonham');
    expect(screen.queryByRole('button', { name: 'Xoá tài khoản quantri' })).toBeNull();

    await nguoiDung.click(screen.getByRole('button', { name: 'Xoá tài khoản taonham' }));
    await nguoiDung.click(await screen.findByRole('button', { name: 'Xoá' }));
    await waitFor(() => expect(goi).toHaveBeenCalledWith('DELETE', '/admin/users/khac'));
  });

  it('⭐⭐ T61.30 — đặt lại 2FA tài khoản khác gọi POST đúng đường; dòng của CHÍNH MÌNH ⛔ có nút', async () => {
    const nguoiDung = userEvent.setup();
    dung(<UsersPage />, ['adm:user:view', 'adm:user:update']);
    await screen.findByText('taonham');
    expect(screen.queryByRole('button', { name: 'Đặt lại 2FA của quantri' })).toBeNull();

    await nguoiDung.click(screen.getByRole('button', { name: 'Đặt lại 2FA của taonham' }));
    await nguoiDung.click(await screen.findByRole('button', { name: 'Đặt lại' }));
    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('POST', '/admin/users/khac/dat-lai-2fa', undefined),
    );
  });

  it('⭐⭐ T61.31 — đặt lại mật khẩu: nhập mật khẩu tạm rồi mã 2FA, POST đúng đường; dòng của CHÍNH MÌNH ⛔ có nút', async () => {
    const nguoiDung = userEvent.setup();
    dung(<UsersPage />, ['adm:user:view', 'adm:user:reset-password']);
    await screen.findByText('taonham');
    expect(screen.queryByRole('button', { name: 'Đặt lại mật khẩu của quantri' })).toBeNull();

    await nguoiDung.click(screen.getByRole('button', { name: 'Đặt lại mật khẩu của taonham' }));
    await nguoiDung.type(await screen.findByLabelText('Mật khẩu tạm'), 'MatKhauTam2026xyz');
    // Hộp thoại mã 2FA chỉ hiện SAU bước mật khẩu — hai bước tường minh, ⛔ suy từ ô đã gõ hay chưa.
    await nguoiDung.click(screen.getByRole('button', { name: 'Tiếp tục' }));
    await nguoiDung.type(await screen.findByLabelText('Mã xác thực hai bước'), '123456');
    await nguoiDung.click(screen.getByRole('button', { name: 'Xác nhận' }));

    await waitFor(() =>
      expect(goi).toHaveBeenCalledWith('POST', '/admin/users/khac/dat-lai-mat-khau', {
        matKhauTam: 'MatKhauTam2026xyz',
        maXacThuc: '123456',
      }),
    );
  });

  it('⛔ thiếu `adm:user:reset-password` ⇒ ⛔ có nút đặt lại mật khẩu', async () => {
    dung(<UsersPage />, ['adm:user:view', 'adm:user:update']);
    await screen.findByText('taonham');
    expect(screen.queryByRole('button', { name: /^Đặt lại mật khẩu/ })).toBeNull();
  });

  it('⛔ thiếu `adm:user:update` ⇒ ⛔ có nút xoá tài khoản nào', async () => {
    dung(<UsersPage />, ['adm:user:view']);
    await screen.findByText('taonham');
    expect(screen.queryByRole('button', { name: /^Xoá tài khoản/ })).toBeNull();
    expect(screen.queryByRole('button', { name: /^Đặt lại 2FA/ })).toBeNull();
  });

  it('⭐⭐ xoá nguồn dữ liệu gọi DELETE đúng đường sau khi xác nhận', async () => {
    const nguoiDung = userEvent.setup();
    dung(<ApiSourcesPage />, ['hyd:api-source:manage']);
    await nguoiDung.click(await screen.findByRole('button', { name: 'Xoá nguồn BHH40' }));
    await nguoiDung.click(await screen.findByRole('button', { name: 'Xoá' }));
    await waitFor(() => expect(goi).toHaveBeenCalledWith('DELETE', '/hyd/api-sources/ng-1'));
  });
});
