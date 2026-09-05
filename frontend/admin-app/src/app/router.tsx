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
/**
 * Một tuyến quản trị, canh bằng quyền.
 *
 * <p>⚠ `permission` nhận **một mã hoặc một mảng**, và mảng có nghĩa **HOẶC**
 * ({@link RequirePermission} dùng `.some`). Đó là chỗ để khai *"ai được vào trang này"* cho đúng
 * khi một trang chứa nhiều việc thuộc nhiều quyền khác nhau — xem tuyến
 * `/van-hanh/cong-trinh/:publicId`.
 */
function adminRoute(path: string, permission: string | readonly string[], element: ReactElement) {
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
          // ---- Vận hành công trình (MOD-02) ----
          adminRoute(
            '/van-hanh/dieu-hanh',
            'ops:dashboard:view',
            lazyPage(
              () => import('@/features/dashboard/OperationsDashboardPage'),
              'OperationsDashboardPage',
            ),
          ),
          adminRoute(
            '/van-hanh/cong-trinh',
            'ops:construction:view',
            lazyPage(() => import('@/features/operations/ConstructionsPage'), 'ConstructionsPage'),
          ),
          adminRoute(
            '/thuy-van/diem-do',
            'hyd:station:view',
            lazyPage(() => import('@/features/hydro/StationsPage'), 'StationsPage'),
          ),
          adminRoute(
            '/thuy-van/loai-chi-so',
            'hyd:station:view',
            lazyPage(() => import('@/features/hydro/MeasurementTypesPage'), 'MeasurementTypesPage'),
          ),
          // ⚠ Quyền canh tuyến này là quyền mà ENDPOINT nó gọi đòi
          // (`hyd:api-source:manage`), không phải quyền xem điểm đo. Đây đúng hình
          // dạng lỗi 31/08: màn hình có thật, quyền cấp đúng, mà bị chôn sau một
          // quyền khác — xem `routerGuards.test.ts`.
          adminRoute(
            '/thuy-van/nguon-du-lieu',
            'hyd:api-source:manage',
            lazyPage(() => import('@/features/hydro/ApiSourcesPage'), 'ApiSourcesPage'),
          ),
          // ⭐ Hai tuyến chẩn đoán (T31.13) khai ĐÚNG cặp quyền mà endpoint của chúng nhận ở chế
          //   độ HOẶC. Gác riêng `hyd:api-source:manage` thì TECHNICIAN — vai trò duy nhất ngoài
          //   quản trị có `hyd:station:manage`, tức đúng người sẽ đi khai một mã lạ — không đọc
          //   nổi lý do vì sao số liệu không về. Đó là hình dạng T27.20 lần thứ ba.
          adminRoute(
            '/thuy-van/nhat-ky-dong-bo',
            ['hyd:measurement:view', 'hyd:api-source:manage'],
            lazyPage(() => import('@/features/hydro/SyncLogsPage'), 'SyncLogsPage'),
          ),
          adminRoute(
            '/thuy-van/ma-la',
            ['hyd:measurement:view', 'hyd:api-source:manage'],
            lazyPage(() => import('@/features/hydro/UnmappedCodesPage'), 'UnmappedCodesPage'),
          ),
          // ⭐ WS-32: trang chứa BA việc thuộc BA quyền (`:view` xem hàng chờ · `:review` duyệt ·
          //   `:create` nhập tay), nên tuyến khai quyền RỘNG NHẤT và các nút tự ẩn ở trong. Gác
          //   tuyến bằng `:review` là chôn cả trang sau quyền hẹp nhất — đúng lỗi 31/08 với tuyến
          //   hồ sơ công trình, nơi XN_MANAGER có 8 quyền vận hành mà vẫn nhận 403.
          adminRoute(
            '/thuy-van/du-lieu-nghi-ngo',
            ['hyd:measurement:view', 'hyd:measurement:review'],
            lazyPage(() => import('@/features/hydro/SuspectReadingsPage'), 'SuspectReadingsPage'),
          ),
          // ---- WS-33 máy cảnh báo ngưỡng ----
          // ⭐ Ba tuyến khai ĐÚNG quyền mà endpoint của chúng đòi. Danh mục mức và cấu hình ngưỡng
          //   gác bằng `hyd:threshold:view` (quyền RỘNG hơn `:manage`): người chỉ được xem vẫn
          //   phải thấy ngưỡng nào đang đặt, vì chính họ đọc con số cảnh báo sinh ra từ đó. Nút
          //   Thêm/Sửa/Xoá tự ẩn theo `:manage` ở trong trang.
          adminRoute(
            '/thuy-van/muc-canh-bao',
            'hyd:threshold:view',
            lazyPage(() => import('@/features/hydro/AlertLevelsPage'), 'AlertLevelsPage'),
          ),
          adminRoute(
            '/thuy-van/nguong-canh-bao',
            'hyd:threshold:view',
            lazyPage(() => import('@/features/hydro/AlertRulesPage'), 'AlertRulesPage'),
          ),
          // ⚠ Lịch sử gác bằng `hyd:alert:view`, ⛔ KHÔNG bằng `:handle`: XN_OPERATOR và
          //   DUTY_OFFICER có `:view` và là người ĐỌC cảnh báo nhiều nhất. Gác bằng quyền hẹp hơn
          //   là chôn cả trang sau nút của nó — đúng lỗi 31/08 với tuyến hồ sơ công trình.
          adminRoute(
            '/thuy-van/canh-bao',
            'hyd:alert:view',
            lazyPage(() => import('@/features/hydro/AlertHistoryPage'), 'AlertHistoryPage'),
          ),
          // ---- WS-34 báo cáo thuỷ văn ----
          // ⚠ Gác bằng `hyd:report:view`, ⛔ KHÔNG bằng `hyd:report:export`: xem và xuất là hai
          //   việc, và XN_OPERATOR · DUTY_OFFICER chỉ có vế đầu. Gác cả trang bằng quyền hẹp hơn
          //   là chôn trang sau nút của nó — hình dạng T27.20 đã tái phát ba lần (§10.70).
          adminRoute(
            '/thuy-van/bao-cao-dong-bo',
            'hyd:report:view',
            lazyPage(
              () => import('@/features/hydro/SyncQualityReportPage'),
              'SyncQualityReportPage',
            ),
          ),
          adminRoute(
            '/thuy-van/bao-cao-tong-hop',
            'hyd:report:view',
            lazyPage(() => import('@/features/hydro/PeriodReportPage'), 'PeriodReportPage'),
          ),
          adminRoute(
            '/thuy-van/bieu-tuyen-song',
            'hyd:report:view',
            lazyPage(() => import('@/features/hydro/RiverBoardPage'), 'RiverBoardPage'),
          ),
          // ⚠ `hyd:report:view` khớp đúng quyền mà `HydroChartController` đòi — ⛔ không phải
          //    `hyd:station:view`. Lệch tầng 1 ↔ tầng 2 ở đây cho ra đúng lỗi T27.28: mở được trang
          //    rồi nhận 403 lúc dữ liệu về, tức một màn hình trống ⛔ không giải thích được.
          adminRoute(
            '/thuy-van/bieu-do-muc-nuoc',
            'hyd:report:view',
            lazyPage(() => import('@/features/hydro/WaterLevelChartPage'), 'WaterLevelChartPage'),
          ),
          adminRoute(
            '/van-hanh/danh-muc-tinh-hinh',
            'ops:operation-status-code:manage',
            lazyPage(
              () => import('@/features/operations/OperationStatusCodesPage'),
              'OperationStatusCodesPage',
            ),
          ),
          adminRoute(
            '/van-hanh/cong-trinh/tao-moi',
            'ops:construction:create',
            lazyPage(
              () => import('@/features/operations/ConstructionFormPage'),
              'ConstructionFormPage',
            ),
          ),
          // ⚠⚠ Trang này chứa BA việc thuộc BA quyền khác nhau: sửa hồ sơ
          //     (`ops:construction:update`), tài liệu đính kèm (`ops:document:*`) và lịch sử sửa
          //     chữa (`ops:maintenance:*`). Canh cả trang bằng riêng `update` là chôn hai việc kia
          //     sau một quyền không liên quan — và đo được ngày 31/08: **XN_MANAGER có 8 quyền,
          //     XN_OPERATOR có 3 quyền, không ai trong hai vai trò mở nổi trang này** (403), nên
          //     toàn bộ 11 quyền ấy chưa từng dùng được. Cán bộ Xí nghiệp giữ tệp gốc Quy trình vận
          //     hành mà không có đường tải lên, trong khi ô chọn tệp vẫn bảo họ *"tải lên ở tab Tài
          //     liệu đính kèm trước"*.
          //
          //     Nguyên tắc đúng đã được chính dự án viết ra cho nút "Nhập nhanh"
          //     (`ConstructionsPage.tsx`): *canh đúng quyền mà endpoint đòi, không phải quyền của
          //     màn hình chứa nó*. Ở đây nghĩa là: vào được trang nếu có BẤT KỲ quyền nào trong ba;
          //     tab Hồ sơ tự chuyển sang chỉ-đọc khi thiếu `update` (xem `ConstructionFormPage`).
          adminRoute(
            '/van-hanh/cong-trinh/:publicId',
            ['ops:construction:update', 'ops:document:view', 'ops:maintenance:view'],
            lazyPage(
              () => import('@/features/operations/ConstructionFormPage'),
              'ConstructionFormPage',
            ),
          ),
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
            '/noi-dung/hop-thu-lien-he',
            'cms:contact:manage',
            lazyPage(() => import('@/features/cms/ContactsPage'), 'ContactsPage'),
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
            '/noi-dung/kho-tai-lieu',
            'cms:media:manage',
            lazyPage(() => import('@/features/cms/MediaPage'), 'KhoTaiLieuPage'),
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
