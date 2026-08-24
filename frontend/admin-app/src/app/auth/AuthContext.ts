import { createContext } from 'react';

import { type LoginResponse, type MeResponse } from '@/shared/api-types';

/**
 * `loading` = đang thử khôi phục phiên từ cookie refresh; chưa biết là ai.
 *
 * Ba trạng thái chứ không phải hai (`user | null`): thiếu `loading` thì mọi lần F5 đều
 * nháy qua màn hình đăng nhập trước khi phiên được khôi phục — và nếu route guard chạy
 * trong khoảnh khắc đó, nó chuyển hướng thật, mất luôn đường dẫn người dùng đang mở.
 */
export type AuthStatus = 'loading' | 'anonymous' | 'authenticated';

export interface AuthContextValue {
  status: AuthStatus;
  user: MeResponse | null;

  /** Bước 1. Trả nguyên `LoginResponse` để màn hình tự quyết đi tiếp nhánh 2FA nào. */
  login: (username: string, password: string) => Promise<LoginResponse>;
  verifyTwoFactor: (
    challengeToken: string,
    code: string,
    recoveryCode: boolean,
  ) => Promise<LoginResponse>;
  confirmEnrollment: (challengeToken: string, code: string) => Promise<LoginResponse>;
  logout: () => Promise<void>;

  /**
   * Đưa giao diện về trạng thái chưa đăng nhập **mà không gọi backend** — dùng khi phiên đã
   * bị chính backend thu hồi (đổi mật khẩu thành công).
   *
   * ⚠ Không dùng `clearTokens()` của `apiClient` cho việc này: nó chỉ xoá token, còn `user`
   * và `status` thì guard đọc. Bỏ sót là màn hình bị đẩy ngược về chỗ vừa rời đi.
   */
  endSession: () => void;
  reloadProfile: () => Promise<void>;

  /** Đúng/sai theo danh sách quyền backend trả về. **Chỉ để ẩn/hiện UI** (§4.2 tầng 1). */
  hasPermission: (code: string) => boolean;
  hasRole: (code: string) => boolean;

  /** Bật khi backend trả SYS-0007 — mọi thao tác ghi đang bị chặn (đang khôi phục dữ liệu). */
  maintenance: boolean;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
