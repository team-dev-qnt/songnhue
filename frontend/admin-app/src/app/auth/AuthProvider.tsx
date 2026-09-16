import { useQueryClient } from '@tanstack/react-query';
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
   *
   * <h3>⛔⛔ Thứ tư: đệm truy vấn — ASVS 8.2.3 (T63.3)</h3>
   *
   * `queryClient.clear()` ⛔ phải dọn dẹp cho gọn, nó là một cam kết **bảo mật**. Ba dòng
   * trên chỉ đổi *trạng thái đăng nhập*; **dữ liệu** thì vẫn nằm trong bộ nhớ của TanStack
   * Query, và `gcTime` mặc định là **5 phút**. Trong cửa sổ ấy, người kế tiếp đăng nhập
   * trên cùng trình duyệt — máy dùng chung ở văn phòng là ca thường, ⛔ phải ca hiếm — sẽ
   * thấy đệm của người trước **hiện ra ngay** rồi mới được thay bằng dữ liệu của mình, vì
   * `staleTime: 30_000` cho phép react-query phục vụ bản đệm trước khi nạp lại.
   *
   * Thứ nằm trong đệm ấy ⛔ phải dữ liệu vô hại: hồ sơ CBNV, danh bạ, nhật ký kiểm toán,
   * bản ghi lương/CCCD đã giải mã của những màn hình 🔒. Quy tắc 10 và NĐ 13/2023 nói về
   * đúng nhóm dữ liệu này.
   *
   * ⚠ Đặt ở `endSession` — ⛔ phải ở `logout` — là cố ý: phiên còn kết thúc qua **đường
   * khác** (backend thu hồi phiên ⇒ `onSessionEvent`, đổi mật khẩu, bị đăng xuất từ xa).
   * Đặt ở nơi *dữ liệu đi qua* thay vì ở *một nơi gọi* là luật 12.
   */
  const queryClient = useQueryClient();
  const endSession = useCallback(() => {
    clearTokens();
    setUser(null);
    setStatus('anonymous');
    queryClient.clear();
  }, [queryClient]);

  const logout = useCallback(async () => {
    try {
      await api.post<void>('/auth/logout');
    } catch {
      // Dọn phía FE dù backend có trả lỗi gì: người dùng đã bấm đăng xuất thì màn hình
      // phải rời khỏi trạng thái đăng nhập, không thể "đăng xuất hỏng nên vẫn ở trong".
      //
      // ⚠ `catch` chứ ⛔ phải `finally` (sửa 16/09, T63.3): với `finally` thì lượt dọn CÓ
      // chạy, nhưng lỗi vẫn **ném tiếp ra ngoài** — và nơi gọi duy nhất trong kho là
      // `AdminLayout.tsx:134` viết `void logout()`, tức ⛔ ai bắt. Kết quả là một
      // *Unhandled Promise Rejection* thật trong trình duyệt mỗi lần đăng xuất lúc phiên đã
      // bị thu hồi từ xa hoặc mạng đứt. Ở đây quyết định sản phẩm đã rõ và được viết ngay
      // dòng trên: đăng xuất **luôn thành công** dưới góc nhìn người dùng ⇒ hàm ⛔ được có
      // đường thất bại. Lộ ra nhờ bài kiểm mô phỏng backend từ chối.
    } finally {
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
