import { App as AntdApp } from 'antd';
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthProvider } from '@/app/auth/AuthProvider';
import { RequireAnonymous, RequireAuth } from '@/app/auth/guards';
import type { MeResponse } from '@/shared/api-types';

import { ChangePasswordPage } from './ChangePasswordPage';

/**
 * **Đổi mật khẩu xong phải RỜI được khỏi biểu mẫu.**
 *
 * ## Lỗi đã xảy ra thật
 *
 * Anh Quân báo `POST /auth/change-password` trả **403**. Nhật ký máy chủ kể đúng trình tự:
 *
 * ```
 * POST /auth/change-password → 204   ← thành công, CSDL ghi password_changed_at
 * POST /auth/change-password → 403   ← "header có, cookie thiếu"
 * POST /auth/change-password → 403   ← "header thiếu, cookie thiếu"
 * ```
 *
 * Việc **đã xong ngay lượt đầu**. Thứ hỏng là đường ra: `ChangePasswordPage` gọi
 * `clearTokens()` — chỉ xoá token trong `apiClient` — nên `status` vẫn `authenticated` và
 * `user.mustChangePassword` vẫn `true`. `RequireAnonymous` đọc đúng hai giá trị đó và đẩy
 * người dùng **ngược về biểu mẫu vừa gửi**. Người dùng tưởng thất bại, bấm gửi lần nữa, và
 * lần này phiên đã bị thu hồi cùng cookie CSRF → 403 `AUTH-0005`.
 *
 * ## Vì sao bài kiểm này dựng cả router thật
 *
 * Lỗi **không nằm trong** `ChangePasswordPage`, cũng không nằm trong `guards.tsx`. Đọc
 * riêng từng tệp thì cả hai đều hợp lý; nó chỉ hiện ra ở **chỗ hai bên gặp nhau**. Nên bài
 * kiểm phải cho chúng gặp nhau thật: `AuthProvider` thật, `RequireAuth`/`RequireAnonymous`
 * thật, điều hướng thật. Kiểm một hàm quyết định viết lại cho gọn là kiểm một bản sao —
 * đúng loại bẫy đã trả giá nhiều lần trong dự án này.
 *
 * ⭐ Đây cũng là **bài kiểm component đầu tiên** của admin-app: `@testing-library/react` có
 * trong `package.json` từ WS-8 mà chưa lần nào được dùng.
 */

const HO_SO: MeResponse = {
  id: '00000000-0000-0000-0000-000000000001',
  username: 'superadmin',
  fullName: 'Quản trị hệ thống',
  orgUnitId: null,
  roles: ['SUPER_ADMIN'],
  permissions: [],
  mustChangePassword: true,
  twoFactorEnrolled: true,
};

const postGia = vi.fn();
const getGia = vi.fn();
const bootstrapGia = vi.fn();

vi.mock('@/shared/apiClient', () => ({
  api: {
    get: (url: string) => getGia(url) as unknown,
    post: (url: string, body?: unknown) => postGia(url, body) as unknown,
  },
  bootstrapSession: () => bootstrapGia() as unknown,
  clearTokens: vi.fn(),
  setAccessToken: vi.fn(),
  onSessionEvent: () => () => {},
  ApiClientError: class extends Error {},
}));

/** Dựng đúng hình dạng route của `router.tsx`: hai nhánh guard, hai màn hình. */
function dungManHinh() {
  return render(
    <AntdApp>
      <AuthProvider>
        <MemoryRouter initialEntries={['/doi-mat-khau']}>
          <Routes>
            <Route element={<RequireAnonymous />}>
              <Route path="/dang-nhap" element={<h1>Đăng nhập hệ thống</h1>} />
            </Route>
            <Route element={<RequireAuth />}>
              <Route path="/doi-mat-khau" element={<ChangePasswordPage />} />
              <Route path="/" element={<h1>Bảng điều khiển</h1>} />
            </Route>
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </AntdApp>,
  );
}

describe('Luồng đổi mật khẩu bắt buộc', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    bootstrapGia.mockResolvedValue(true);
    getGia.mockResolvedValue(HO_SO);
    postGia.mockResolvedValue(undefined);
  });

  // ⚠ `@testing-library/react` chỉ tự dọn DOM khi vitest bật `globals` — cấu hình của
  // admin-app thì không (TS strict, mọi thứ khai tường minh). Thiếu dòng này thì màn hình
  // của bài trước còn nguyên trong `document`, và bài sau đỏ với "Found multiple elements"
  // — một lời báo lỗi chẳng liên quan gì tới thứ đang kiểm.
  afterEach(cleanup);

  it('⭐⭐ đổi thành công thì về trang đăng nhập, KHÔNG bị guard đẩy ngược lại biểu mẫu', async () => {
    const nguoiDung = userEvent.setup();
    dungManHinh();

    await screen.findByRole('button', { name: 'Đổi mật khẩu' });

    await nguoiDung.type(screen.getByLabelText('Mật khẩu hiện tại'), 'MatKhauCu@2026');
    await nguoiDung.type(screen.getByLabelText('Mật khẩu mới'), 'MatKhauMoi@2026');
    await nguoiDung.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'MatKhauMoi@2026');
    await nguoiDung.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    await waitFor(() =>
      expect(postGia).toHaveBeenCalledWith('/auth/change-password', {
        currentPassword: 'MatKhauCu@2026',
        newPassword: 'MatKhauMoi@2026',
      }),
    );

    // Vế 1: đã tới được trang đăng nhập.
    expect(await screen.findByRole('heading', { name: 'Đăng nhập hệ thống' })).toBeInTheDocument();

    // Vế 2 — vế thật sự bắt lỗi: biểu mẫu KHÔNG được hiện lại. Chỉ khẳng định vế 1 thì một
    // bản dựng hiện cả hai màn hình cũng xanh, mà đúng triệu chứng người dùng gặp là biểu
    // mẫu quay lại.
    expect(screen.queryByRole('button', { name: 'Đổi mật khẩu' })).not.toBeInTheDocument();
  });

  it('⛔ chỉ gửi đúng MỘT lượt — không có đường bấm lại vào phiên đã bị thu hồi', async () => {
    const nguoiDung = userEvent.setup();
    dungManHinh();

    await screen.findByRole('button', { name: 'Đổi mật khẩu' });
    await nguoiDung.type(screen.getByLabelText('Mật khẩu hiện tại'), 'MatKhauCu@2026');
    await nguoiDung.type(screen.getByLabelText('Mật khẩu mới'), 'MatKhauMoi@2026');
    await nguoiDung.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'MatKhauMoi@2026');
    await nguoiDung.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    await screen.findByRole('heading', { name: 'Đăng nhập hệ thống' });

    const soLuot = postGia.mock.calls.filter(([url]) => url === '/auth/change-password').length;
    expect(soLuot).toBe(1);
  });

  it('đổi hỏng thì ở lại biểu mẫu và giữ nguyên phiên — không đá người dùng ra oan', async () => {
    const nguoiDung = userEvent.setup();
    postGia.mockRejectedValue(new Error('Mật khẩu hiện tại không đúng'));
    dungManHinh();

    await screen.findByRole('button', { name: 'Đổi mật khẩu' });
    await nguoiDung.type(screen.getByLabelText('Mật khẩu hiện tại'), 'SaiRoi@2026');
    await nguoiDung.type(screen.getByLabelText('Mật khẩu mới'), 'MatKhauMoi@2026');
    await nguoiDung.type(screen.getByLabelText('Nhập lại mật khẩu mới'), 'MatKhauMoi@2026');
    await nguoiDung.click(screen.getByRole('button', { name: 'Đổi mật khẩu' }));

    await waitFor(() => expect(postGia).toHaveBeenCalled());
    expect(screen.getByRole('button', { name: 'Đổi mật khẩu' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Đăng nhập hệ thống' })).not.toBeInTheDocument();
  });
});
