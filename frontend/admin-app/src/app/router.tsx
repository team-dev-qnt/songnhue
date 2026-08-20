import { Skeleton } from 'antd';
import { Suspense, lazy, type ComponentType, type ReactElement } from 'react';
import { createBrowserRouter } from 'react-router-dom';

import { AdminLayout } from '@/app/AdminLayout';
import { RequireAnonymous, RequireAuth, RequirePermission } from '@/app/auth/guards';
import { RouteErrorBoundary } from '@/app/errors/ErrorBoundary';
import { NotFoundPage } from '@/app/errors/NotFoundPage';
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage';
import { EnrollTwoFactorPage } from '@/features/auth/EnrollTwoFactorPage';
import { LoginPage } from '@/features/auth/LoginPage';
import { TwoFactorPage } from '@/features/auth/TwoFactorPage';

/**
 * Bảng route.
 *
 * <h3>Đường dẫn tiếng Việt không dấu — cố ý</h3>
 *
 * Hệ thống chỉ phục vụ tiếng Việt (chốt BOQ đợt 1), người dùng là cán bộ trong Công ty.
 * `/quan-tri/sao-luu` đọc được ngay khi ai đó đọc URL qua điện thoại cho người khác, còn
 * `/admin/backups` thì phải dịch. Không dấu để URL không bị mã hoá phần trăm khi chép.
 *
 * <h3>`/doi-mat-khau` nằm NGOÀI khung quản trị</h3>
 *
 * Người đang bị bắt đổi mật khẩu chưa dùng được chức năng nào — hiện menu đầy đủ quanh
 * họ chỉ để bấm vào đâu cũng bị đá về đây thì vô nghĩa. Nó vẫn nằm trong `RequireAuth`
 * vì cần phiên để gọi endpoint đổi mật khẩu.
 *
 * <h3>Màn hình quản trị tải theo nhu cầu, màn hình đăng nhập thì không</h3>
 *
 * Nhóm quản trị kéo theo bảng, cây, biểu đồ tiến độ — phần nặng nhất của bó mã. Người mở
 * trang lần đầu chỉ cần đăng nhập, nên bắt họ tải trước cả màn hình khôi phục CSDL là
 * kéo dài đúng lúc gây ấn tượng nhất (NFR-03: tải trang ≤ 3 giây). Ngược lại, bốn màn
 * hình xác thực nạp thẳng: chúng LÀ đường vào, tách ra chỉ thêm một vòng chờ mạng.
 */

// Bọc `lazy` để mỗi route không phải tự viết `.then(m => ({default: m.X}))` — các
// component ở đây là export có tên, không phải export mặc định.
function lazyPage(loader: () => Promise<Record<string, ComponentType>>, name: string) {
  const Component = lazy(async () => {
    const module = await loader();
    const found = module[name];
    if (!found) {
      throw new Error(`Không tìm thấy component "${name}" trong module route`);
    }
    return { default: found };
  });

  return (
    <Suspense fallback={<Skeleton active paragraph={{ rows: 8 }} />}>
      <Component />
    </Suspense>
  );
}

/** Route quản trị: tải theo nhu cầu + chặn theo quyền, gộp thành một chỗ khai báo. */
function adminRoute(path: string, permission: string, element: ReactElement) {
  return { path, element: <RequirePermission code={permission}>{element}</RequirePermission> };
}
export const router = createBrowserRouter([
  {
    element: <RequireAnonymous />,
    errorElement: <RouteErrorBoundary />,
    children: [
      { path: '/dang-nhap', element: <LoginPage /> },
      { path: '/xac-thuc-2-buoc', element: <TwoFactorPage /> },
      { path: '/dang-ky-2fa', element: <EnrollTwoFactorPage /> },
    ],
  },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorBoundary />,
    children: [
      { path: '/doi-mat-khau', element: <ChangePasswordPage /> },
      {
        element: <AdminLayout />,
        children: [
          {
            path: '/',
            element: lazyPage(() => import('@/features/dashboard/DashboardPage'), 'DashboardPage'),
          },
          {
            path: '/hop-thu',
            element: lazyPage(() => import('@/features/account/InboxPage'), 'InboxPage'),
          },
          {
            path: '/phien-dang-nhap',
            element: lazyPage(() => import('@/features/account/SessionsPage'), 'SessionsPage'),
          },
          // ---- Nội dung (MOD-01) ----
          adminRoute(
            '/noi-dung/bai-viet',
            'cms:article:view',
            lazyPage(() => import('@/features/cms/ArticleListPage'), 'ArticleListPage'),
          ),
          adminRoute(
            '/noi-dung/bai-viet/:publicId',
            'cms:article:view',
            lazyPage(() => import('@/features/cms/ArticleEditorPage'), 'ArticleEditorPage'),
          ),
          adminRoute(
            '/noi-dung/danh-muc',
            'cms:category:manage',
            lazyPage(() => import('@/features/cms/CategoriesPage'), 'CategoriesPage'),
          ),
          adminRoute(
            '/noi-dung/thu-vien',
            'cms:media:manage',
            lazyPage(() => import('@/features/cms/MediaPage'), 'MediaPage'),
          ),
          adminRoute(
            '/noi-dung/giao-dien',
            'cms:layout:manage',
            lazyPage(() => import('@/features/cms/SiteLayoutPage'), 'SiteLayoutPage'),
          ),
          adminRoute(
            '/quan-tri/tai-khoan',
            'adm:user:view',
            lazyPage(() => import('@/features/admin/UsersPage'), 'UsersPage'),
          ),
          adminRoute(
            '/quan-tri/vai-tro',
            'adm:role:view',
            lazyPage(() => import('@/features/admin/RolesPage'), 'RolesPage'),
          ),
          adminRoute(
            '/quan-tri/don-vi',
            'adm:org-unit:view',
            lazyPage(() => import('@/features/admin/OrgUnitsPage'), 'OrgUnitsPage'),
          ),
          adminRoute(
            '/quan-tri/cau-hinh',
            'adm:setting:view',
            lazyPage(() => import('@/features/admin/SettingsPage'), 'SettingsPage'),
          ),
          adminRoute(
            '/quan-tri/nhat-ky',
            'adm:audit:view',
            lazyPage(() => import('@/features/admin/AuditLogPage'), 'AuditLogPage'),
          ),
          adminRoute(
            '/quan-tri/sao-luu',
            'adm:backup:view',
            lazyPage(() => import('@/features/admin/BackupPage'), 'BackupPage'),
          ),
          adminRoute(
            '/quan-tri/tinh-trang',
            'adm:health:view',
            lazyPage(() => import('@/features/admin/HealthPage'), 'HealthPage'),
          ),
          adminRoute(
            '/quan-tri/thong-bao',
            'adm:notification:broadcast',
            lazyPage(() => import('@/features/admin/BroadcastPage'), 'BroadcastPage'),
          ),
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
