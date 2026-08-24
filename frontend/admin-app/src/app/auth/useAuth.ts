import { useContext } from 'react';

import { AuthContext, type AuthContextValue } from './AuthContext';

/** Ném lỗi thay vì trả `null`: quên bọc `AuthProvider` là lỗi lập trình, không phải trạng thái. */
export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth phải nằm trong <AuthProvider>');
  }
  return context;
}

/**
 * Kiểm quyền để **ẩn/hiện giao diện** — conventions.md §4.2 tầng 1.
 *
 * ⛔ Đây **không phải** cơ chế bảo mật. Người dùng sửa được biến trong trình duyệt, gọi
 * thẳng API bằng curl, hoặc đơn giản là gõ URL. Chốt chặn thật nằm ở tầng 2
 * (`@RequirePermission` trên controller) và tầng 3 (scope filter theo đơn vị). Ẩn nút
 * chỉ để người dùng khỏi bấm vào thứ chắc chắn sẽ báo lỗi.
 */
export function usePermission(code: string): boolean {
  return useAuth().hasPermission(code);
}

/** Đúng khi có **ít nhất một** quyền trong danh sách — dùng cho mục menu gộp nhiều màn hình. */
export function useAnyPermission(codes: readonly string[]): boolean {
  const { hasPermission } = useAuth();
  return codes.some((code) => hasPermission(code));
}
