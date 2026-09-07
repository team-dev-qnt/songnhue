import { App } from 'antd';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';

import { type LoginResponse, type MeResponse } from '@/shared/api-types';
import {
  api,
  bootstrapSession,
  clearTokens,
  onSessionEvent,
  setAccessToken,
} from '@/shared/apiClient';

import { AuthContext, type AuthContextValue, type AuthStatus } from './AuthContext';

/**
 * Nguồn sự thật về "ai đang đăng nhập" của cả admin-app.
 *
 * Danh sách quyền lấy từ `GET /auth/me` **mỗi lần khởi động**, không lưu lại giữa các
 * phiên: quản trị viên vừa gỡ một vai trò thì lần tải trang kế tiếp phải thấy menu mới,
 * chứ không phải thấy bản cũ đọc từ localStorage.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>('loading');
  const [user, setUser] = useState<MeResponse | null>(null);
  const [maintenance, setMaintenance] = useState(false);
  const { notification } = App.useApp();

  const loadProfile = useCallback(async () => {
    const profile = await api.get<MeResponse>('/auth/me');
    setUser(profile);
    setStatus('authenticated');
  }, []);

  /** Áp token của một lượt đăng nhập thành công rồi nạp hồ sơ. */
  const applyTokens = useCallback(
    async (response: LoginResponse): Promise<LoginResponse> => {
      if (response.stage !== 'AUTHENTICATED' || !response.accessToken) {
        return response;
      }
      // Vé CSRF không chép vào bộ nhớ: nó đã nằm ở cookie `XSRF-TOKEN` do chính phản hồi
      // này đặt, và cookie là vế mà backend đối chiếu (xem `currentCsrfToken`).
      setAccessToken(response.accessToken);
      await loadProfile();
      return response;
    },
    [loadProfile],
  );

  // --- Khôi phục phiên sau khi tải lại trang -------------------------------
  useEffect(() => {
    let huy = false;

    void (async () => {
      const restored = await bootstrapSession();
      if (huy) {
        return;
      }
      if (!restored) {
        setStatus('anonymous');
        return;
      }
      try {
        await loadProfile();
      } catch {
        // Cookie refresh còn hiệu lực mà /auth/me hỏng nghĩa là phiên không dùng được —
        // coi như chưa đăng nhập, đừng để giao diện kẹt vĩnh viễn ở trạng thái `loading`.
        clearTokens();
        setStatus('anonymous');
      }
    })();

    return () => {
      huy = true;
    };
  }, [loadProfile]);

  // --- Nghe sự kiện phiên do apiClient phát ---------------------------------
  // `notification` nằm trong danh sách phụ thuộc thay vì được giữ qua một ref: đọc/ghi
  // ref trong lúc render là thứ React cấm (và eslint-plugin-react-hooks bắt được).
  // `App.useApp()` trả về đối tượng ổn định, nên thực tế effect này chỉ chạy một lần.
  useEffect(
    () =>
      onSessionEvent((event) => {
        switch (event.type) {
          case 'sessionLost':
            setUser(null);
            setStatus('anonymous');
            notification.warning({
              message: 'Phiên đăng nhập kết thúc',
              description: event.reason,
            });
            break;
          case 'maintenance':
            setMaintenance(true);
            break;
          case 'mustChangePassword':
            setUser((current) => (current ? { ...current, mustChangePassword: true } : current));
            break;
        }
      }),
    [notification],
  );

  const login = useCallback(
    async (username: string, password: string) =>
      applyTokens(await api.post<LoginResponse>('/auth/login', { username, password })),
    [applyTokens],
  );

  const verifyTwoFactor = useCallback(
    async (challengeToken: string, code: string, recoveryCode: boolean) =>
      applyTokens(
        await api.post<LoginResponse>('/auth/2fa/verify', { challengeToken, code, recoveryCode }),
      ),
    [applyTokens],
  );

  const confirmEnrollment = useCallback(
    async (challengeToken: string, code: string) =>
      applyTokens(await api.post<LoginResponse>('/auth/2fa/confirm', { challengeToken, code })),
    [applyTokens],
  );

  /**
   * Đưa giao diện về trạng thái chưa đăng nhập.
   *
   * ⚠⚠ Phải dọn **cả ba** thứ, và đó là lý do hàm này tồn tại thay vì để mỗi nơi tự nhớ.
   * `clearTokens()` chỉ xoá token trong `apiClient`; `user` và `status` nằm ở React và
   * **guard đọc chúng**. Bỏ sót hai cái sau thì `RequireAnonymous` vẫn thấy
   * `status === 'authenticated'` và đẩy người dùng ngược về màn hình cũ.
   *
   * Đã xảy ra thật: đổi mật khẩu thành công (backend trả 204, mật khẩu đã đổi trong CSDL)
   * nhưng `ChangePasswordPage` chỉ gọi `clearTokens()`, nên `user.mustChangePassword` còn
   * `true` → guard bật lại đúng biểu mẫu vừa gửi → người dùng tưởng thất bại, bấm gửi lần
   * nữa, và lần này backend trả **403 AUTH-0005** vì phiên đã bị thu hồi cùng vé CSRF.
   * Triệu chứng người dùng thấy là "đổi mật khẩu bị 403", trong khi việc đã xong từ đầu.
   */
  const endSession = useCallback(() => {
    clearTokens();
    setUser(null);
    setStatus('anonymous');
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post<void>('/auth/logout');
    } finally {
      // Dọn phía FE dù backend có trả lỗi gì: người dùng đã bấm đăng xuất thì màn hình
      // phải rời khỏi trạng thái đăng nhập, không thể "đăng xuất hỏng nên vẫn ở trong".
      endSession();
    }
  }, [endSession]);

  const value = useMemo<AuthContextValue>(() => {
    const permissions = new Set(user?.permissions ?? []);
    const roles = new Set(user?.roles ?? []);
    return {
      status,
      user,
      login,
      verifyTwoFactor,
      confirmEnrollment,
      logout,
      endSession,
      reloadProfile: loadProfile,
      hasPermission: (code) => permissions.has(code),
      hasRole: (code) => roles.has(code),
      maintenance,
    };
  }, [
    status,
    user,
    login,
    verifyTwoFactor,
    confirmEnrollment,
    logout,
    endSession,
    loadProfile,
    maintenance,
  ]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
