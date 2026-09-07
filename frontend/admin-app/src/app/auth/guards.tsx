import { Spin } from 'antd';
import { type ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { ForbiddenPage } from '@/app/errors/ForbiddenPage';

import { useAuth } from './useAuth';

/**
 * Chặn route cho người chưa đăng nhập.
 *
 * ⛔ Nhắc lại cho rõ: **guard này không bảo vệ dữ liệu**. Người dùng gỡ nó bằng công cụ
 * dev trong ba giây. Mọi endpoint đều tự kiểm quyền ở backend (deny by default, có bài
 * kiểm ở CI làm đỏ nếu ai thêm endpoint mà quên khai). Guard chỉ để không dẫn người dùng
 * vào một màn hình chắc chắn rỗng.
 */
export function RequireAuth() {
  const { status, user } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <FullScreenSpinner />;
  }
  if (status === 'anonymous') {
    // Nhớ đường dẫn đang mở để đăng nhập xong quay lại đúng chỗ, thay vì luôn về trang chủ.
    return (
      <Navigate to="/dang-nhap" replace state={{ from: location.pathname + location.search }} />
    );
  }
  // Đang bắt buộc đổi mật khẩu thì mọi màn hình khác đều đóng — backend cũng trả AUTH-0007
  // cho mọi endpoint, nên có đi tiếp cũng chỉ gặp lỗi.
  if (user?.mustChangePassword && location.pathname !== '/doi-mat-khau') {
    return <Navigate to="/doi-mat-khau" replace />;
  }
  return <Outlet />;
}

/** Ngược lại: đã đăng nhập thì không cần thấy trang đăng nhập nữa. */
export function RequireAnonymous() {
  const { status, user } = useAuth();

  if (status === 'loading') {
    return <FullScreenSpinner />;
  }
  if (status === 'authenticated') {
    return <Navigate to={user?.mustChangePassword ? '/doi-mat-khau' : '/'} replace />;
  }
  return <Outlet />;
}

/**
 * Chặn theo quyền. Thiếu quyền thì hiện trang 403 **tại chỗ** chứ không chuyển hướng —
 * chuyển hướng về trang chủ làm người dùng tưởng mình bấm nhầm, rồi bấm lại đúng chỗ cũ.
 */
export function RequirePermission({
  code,
  children,
}: {
  code: string | readonly string[];
  children?: ReactNode;
}) {
  const { hasPermission } = useAuth();
  const codes = typeof code === 'string' ? [code] : code;

  if (!codes.some((item) => hasPermission(item))) {
    return <ForbiddenPage requiredPermission={codes.join(' hoặc ')} />;
  }
  return <>{children ?? <Outlet />}</>;
}

function FullScreenSpinner() {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: '100vh',
      }}
    >
      <Spin size="large" tip="Đang khôi phục phiên làm việc…" />
    </div>
  );
}
