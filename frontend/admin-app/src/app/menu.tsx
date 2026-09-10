import {
  AlertOutlined,
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  HeartOutlined,
  ExperimentOutlined,
  HistoryOutlined,
  IdcardOutlined,
  InboxOutlined,
  FileTextOutlined,
  FundProjectionScreenOutlined,
  LaptopOutlined,
  LayoutOutlined,
  LineChartOutlined,
  PictureOutlined,
  LikeOutlined,
  MailOutlined,
  QuestionCircleOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  SolutionOutlined,
  TeamOutlined,
  WarningOutlined,
} from '@ant-design/icons';
import { type ReactNode } from 'react';

/**
 * Khai báo menu — **một nơi duy nhất** ghép "đường dẫn ↔ nhãn ↔ quyền cần có".
 *
 * Để mỗi màn hình tự ẩn mình khỏi menu thì sớm muộn cũng có màn hình bị lộ ra cho người
 * không có quyền (menu bấm được, vào tới nơi mới 403). Gộp về đây thì thanh điều hướng
 * và route guard đọc **cùng một mã quyền**, không thể lệch nhau.
 *
 * ⛔ Đây là tầng 1 (§4.2): ẩn menu **không** bảo vệ dữ liệu, backend mới là chốt chặn.
 */
export interface MenuNode {
  key: string;
  label: string;
  icon?: ReactNode;
  path?: string;
  /** Cần **ít nhất một** quyền trong danh sách. Bỏ trống = chỉ cần đăng nhập. */
  permissions?: readonly string[];
  /**
   * Mục chỉ hiện khi tài khoản **có liên kết hồ sơ CBNV** — T51.8.
   *
   * ⛔ Đây ⛔ **không** phải một mã quyền, và cố ý ⛔ không giả làm một mã quyền. Vế *"chính nhân
   * viên đó"* của CN-04.7 là quan hệ giữa **một tài khoản** và **một hàng**, ⛔ không phải một
   * thuộc tính của vai trò — nhét một mã giả vào `permissions` sẽ làm nó lệch khỏi danh mục quyền
   * thật của backend, thứ mà chính tệp này hứa là "cùng một mã quyền, ⛔ không thể lệch nhau".
   */
  requiresEmployeeLink?: boolean;
  children?: MenuNode[];
}

export const MENU: readonly MenuNode[] = [
  {
    key: 'tong-quan',
    label: 'Tổng quan',
    icon: <DashboardOutlined />,
    path: '/',
  },
  {
    key: 'van-hanh',
    label: 'Vận hành công trình',
    icon: <FundProjectionScreenOutlined />,
    children: [
      {
        key: 'dieu-hanh',
        label: 'Dashboard điều hành',
        icon: <DashboardOutlined />,
        path: '/van-hanh/dieu-hanh',
        permissions: ['ops:dashboard:view'],
      },
      {
        key: 'cong-trinh',
        label: 'Hồ sơ công trình',
        icon: <AppstoreOutlined />,
        path: '/van-hanh/cong-trinh',
        permissions: ['ops:construction:view'],
      },
      {
        key: 'cum-cong-trinh',
        label: 'Cụm công trình',
        icon: <AppstoreOutlined />,
        path: '/van-hanh/cum-cong-trinh',
        permissions: ['ops:construction:view'],
      },
      {
        key: 'danh-muc-tinh-hinh',
        label: 'Danh mục tình trạng vận hành',
        icon: <AppstoreOutlined />,
        path: '/van-hanh/danh-muc-tinh-hinh',
        permissions: ['ops:operation-status-code:manage'],
      },
    ],
  },
  {
    key: 'thuy-van',
    label: 'Dữ liệu thuỷ văn',
    icon: <CloudServerOutlined />,
    children: [
      {
        key: 'diem-do',
        label: 'Danh mục điểm đo',
        icon: <ApartmentOutlined />,
        path: '/thuy-van/diem-do',
        permissions: ['hyd:station:view'],
      },
      {
        key: 'loai-chi-so',
        label: 'Loại chỉ số quan trắc',
        icon: <AppstoreOutlined />,
        path: '/thuy-van/loai-chi-so',
        permissions: ['hyd:station:view'],
      },
      {
        key: 'nguon-du-lieu',
        label: 'Nguồn dữ liệu',
        icon: <CloudServerOutlined />,
        path: '/thuy-van/nguon-du-lieu',
        permissions: ['hyd:api-source:manage'],
      },
      {
        key: 'nhat-ky-dong-bo',
        label: 'Nhật ký đồng bộ',
        icon: <HistoryOutlined />,
        path: '/thuy-van/nhat-ky-dong-bo',
        permissions: ['hyd:measurement:view', 'hyd:api-source:manage'],
      },
      {
        key: 'ma-la',
        label: 'Mã lạ từ nguồn',
        icon: <QuestionCircleOutlined />,
        path: '/thuy-van/ma-la',
        permissions: ['hyd:measurement:view', 'hyd:api-source:manage'],
      },
      {
        // ⚠ Gác bằng `hyd:measurement:view` — quyền RỘNG NHẤT trong ba quyền của trang. Người
        //   không duyệt được vẫn phải THẤY số liệu nào đang bị treo, vì chính họ là người đọc
        //   biểu đồ có lỗ hổng ấy. Nút Duyệt / Nhập tay tự ẩn theo quyền hẹp hơn ở trong trang.
        key: 'du-lieu-nghi-ngo',
        label: 'Dữ liệu nghi ngờ',
        icon: <ExperimentOutlined />,
        path: '/thuy-van/du-lieu-nghi-ngo',
        permissions: ['hyd:measurement:view', 'hyd:measurement:review'],
      },
      {
        key: 'muc-canh-bao',
        label: 'Mức cảnh báo',
        icon: <AppstoreOutlined />,
        path: '/thuy-van/muc-canh-bao',
        permissions: ['hyd:threshold:view'],
      },
      {
        key: 'nguong-canh-bao',
        label: 'Ngưỡng cảnh báo',
        icon: <WarningOutlined />,
        path: '/thuy-van/nguong-canh-bao',
        permissions: ['hyd:threshold:view'],
      },
      {
        // ⚠ `hyd:alert:view` — quyền RỘNG NHẤT của trang. Người không xử lý được vẫn phải THẤY
        //   cảnh báo nào đang mở; nút Đã xử lý / Báo động giả tự ẩn theo `hyd:alert:handle`.
        key: 'canh-bao',
        label: 'Cảnh báo ngưỡng',
        icon: <AlertOutlined />,
        path: '/thuy-van/canh-bao',
        permissions: ['hyd:alert:view'],
      },
      {
        key: 'bieu-tuyen-song',
        label: 'Biểu tổng hợp tuyến sông',
        icon: <FundProjectionScreenOutlined />,
        path: '/thuy-van/bieu-tuyen-song',
        permissions: ['hyd:report:view'],
      },
      {
        key: 'bieu-do-muc-nuoc',
        label: 'Biểu đồ mực nước 24h',
        icon: <LineChartOutlined />,
        path: '/thuy-van/bieu-do-muc-nuoc',
        permissions: ['hyd:report:view'],
      },
      {
        key: 'bao-cao-tong-hop',
        label: 'Báo cáo tổng hợp kỳ',
        icon: <FileTextOutlined />,
        path: '/thuy-van/bao-cao-tong-hop',
        permissions: ['hyd:report:view'],
      },
      {
        key: 'bao-cao-dong-bo',
        label: 'Báo cáo đồng bộ & chất lượng',
        icon: <FileTextOutlined />,
        path: '/thuy-van/bao-cao-dong-bo',
        permissions: ['hyd:report:view'],
      },
    ],
  },
  {
    key: 'noi-dung',
    label: 'Nội dung cổng',
    icon: <ReadOutlined />,
    children: [
      {
        key: 'bai-viet',
        label: 'Bài viết',
        icon: <FileTextOutlined />,
        path: '/noi-dung/bai-viet',
        permissions: ['cms:article:view'],
      },
      {
        key: 'hop-thu-lien-he',
        label: 'Hộp thư liên hệ',
        icon: <MailOutlined />,
        path: '/noi-dung/hop-thu-lien-he',
        permissions: ['cms:contact:manage'],
      },
      {
        key: 'gop-y',
        label: 'Góp ý & đánh giá',
        icon: <LikeOutlined />,
        path: '/noi-dung/gop-y',
        permissions: ['cms:feedback:manage'],
      },
      {
        key: 'danh-muc-noi-dung',
        label: 'Danh mục',
        icon: <AppstoreOutlined />,
        path: '/noi-dung/danh-muc',
        permissions: ['cms:category:manage'],
      },
      {
        key: 'thu-vien',
        label: 'Thư viện media',
        icon: <PictureOutlined />,
        path: '/noi-dung/thu-vien',
        permissions: ['cms:media:manage'],
      },
      {
        // ⛔ Dùng lại `cms:media:manage`, KHÔNG thêm mã quyền mới (WS-40): cùng bộ máy, cùng nhóm
        //    người dùng, và quyền này đã cấp cho cả vai trò biên tập lẫn quản trị nội dung. Thêm
        //    một mã quyền là thêm một dòng phân quyền phải seed, phải cấp, phải nhớ.
        key: 'kho-tai-lieu',
        label: 'Kho tài liệu',
        icon: <FileTextOutlined />,
        path: '/noi-dung/kho-tai-lieu',
        permissions: ['cms:media:manage'],
      },
      {
        key: 'giao-dien',
        label: 'Giao diện cổng',
        icon: <LayoutOutlined />,
        path: '/noi-dung/giao-dien',
        permissions: ['cms:layout:manage'],
      },
    ],
  },
  {
    // ⛔ Cả hai mục gác bằng `hr:employee:view`. Danh mục chức vụ ⛔ KHÔNG được gác bằng một quyền
    //    hẹp hơn: nó là nguồn dữ liệu cho ô "Chức vụ" của biểu mẫu hồ sơ, nên người dựng hồ sơ mà
    //    ⛔ không mở được nó thì cũng ⛔ không kiểm tra được mã mình đang chọn (WS-28).
    key: 'nhan-su',
    label: 'Nhân sự',
    icon: <TeamOutlined />,
    children: [
      {
        key: 'ho-so-cbnv',
        label: 'Hồ sơ cán bộ',
        icon: <IdcardOutlined />,
        path: '/nhan-su/ho-so',
        permissions: ['hr:employee:view'],
      },
      {
        key: 'chuc-vu',
        label: 'Danh mục chức vụ',
        icon: <SolutionOutlined />,
        path: '/nhan-su/chuc-vu',
        permissions: ['hr:employee:view'],
      },
      {
        // ⛔ ⛔ Không `permissions` — mục này gác bằng LIÊN KẾT, ⛔ không bằng quyền. Gác thêm
        //    `hr:employee:view` sẽ chặn đúng đối tượng nó phục vụ: một cán bộ vai trò VIEWER ⛔
        //    không có quyền ấy, mà đặc tả CN-04.7 nói *"chính nhân viên đó"*.
        key: 'ho-so-cua-toi',
        label: 'Hồ sơ của tôi',
        icon: <IdcardOutlined />,
        path: '/nhan-su/ho-so-cua-toi',
        requiresEmployeeLink: true,
      },
    ],
  },
  {
    key: 'quan-tri',
    label: 'Quản trị hệ thống',
    icon: <SafetyCertificateOutlined />,
    children: [
      {
        key: 'tai-khoan',
        label: 'Tài khoản',
        icon: <TeamOutlined />,
        path: '/quan-tri/tai-khoan',
        permissions: ['adm:user:view'],
      },
      {
        key: 'vai-tro',
        label: 'Vai trò & phân quyền',
        icon: <SafetyCertificateOutlined />,
        path: '/quan-tri/vai-tro',
        permissions: ['adm:role:view'],
      },
      {
        key: 'don-vi',
        label: 'Sơ đồ đơn vị',
        icon: <ApartmentOutlined />,
        path: '/quan-tri/don-vi',
        permissions: ['adm:org-unit:view'],
      },
      {
        key: 'cau-hinh',
        label: 'Cấu hình hệ thống',
        icon: <SettingOutlined />,
        path: '/quan-tri/cau-hinh',
        permissions: ['adm:setting:view'],
      },
      {
        key: 'nhat-ky',
        label: 'Nhật ký kiểm toán',
        icon: <AuditOutlined />,
        path: '/quan-tri/nhat-ky',
        permissions: ['adm:audit:view'],
      },
      {
        key: 'sao-luu',
        label: 'Sao lưu & khôi phục',
        icon: <CloudServerOutlined />,
        path: '/quan-tri/sao-luu',
        permissions: ['adm:backup:view'],
      },
      {
        key: 'tinh-trang',
        label: 'Tình trạng hệ thống',
        icon: <HeartOutlined />,
        path: '/quan-tri/tinh-trang',
        permissions: ['adm:health:view'],
      },
      {
        key: 'thong-bao-he-thong',
        label: 'Thông báo hệ thống',
        icon: <BellOutlined />,
        path: '/quan-tri/thong-bao',
        permissions: ['adm:notification:broadcast'],
      },
    ],
  },
  {
    key: 'ca-nhan',
    label: 'Cá nhân',
    icon: <InboxOutlined />,
    children: [
      { key: 'hop-thu', label: 'Hộp thư', icon: <InboxOutlined />, path: '/hop-thu' },
      {
        key: 'phien-dang-nhap',
        label: 'Phiên đăng nhập',
        icon: <LaptopOutlined />,
        path: '/phien-dang-nhap',
      },
    ],
  },
];

/**
 * Lọc menu theo quyền.
 *
 * Nhóm cha rỗng sau khi lọc thì **bỏ luôn cả nhóm** — để lại một mục "Quản trị hệ thống"
 * bấm vào không có gì bên trong thì người dùng tưởng giao diện hỏng.
 */
/**
 * Điều kiện hiển thị **ngoài phân quyền** — T51.8.
 *
 * ⚠ Bỏ trống là `false`, tức **ẩn**. Fail-closed có chủ đích: một nơi gọi quên truyền hồ sơ sẽ
 * làm mục biến mất (khó chịu, tự lộ ra) chứ ⛔ không làm nó hiện ra cho người ⛔ không có liên kết
 * (im lặng, và bấm vào thì 404).
 */
export interface HoSoNguoiDung {
  coHoSoNhanSu?: boolean;
}

export function visibleMenu(
  nodes: readonly MenuNode[],
  hasPermission: (code: string) => boolean,
  hoSo: HoSoNguoiDung = {},
): MenuNode[] {
  return nodes
    .map((node) => {
      const children = node.children ? visibleMenu(node.children, hasPermission, hoSo) : undefined;
      const allowed =
        (!node.permissions || node.permissions.some((code) => hasPermission(code))) &&
        (!node.requiresEmployeeLink || hoSo.coHoSoNhanSu === true);

      if (node.children) {
        return children && children.length > 0 ? { ...node, children } : null;
      }
      return allowed ? node : null;
    })
    .filter((node): node is MenuNode => node !== null);
}

/** Tra ngược từ đường dẫn ra khoá menu đang mở — dùng để tô sáng đúng mục sau khi F5. */
export function findMenuKey(nodes: readonly MenuNode[], pathname: string): string | undefined {
  let best: { key: string; length: number } | undefined;

  const walk = (items: readonly MenuNode[]) => {
    for (const item of items) {
      if (item.path && (pathname === item.path || pathname.startsWith(`${item.path}/`))) {
        // Chọn đường dẫn khớp DÀI NHẤT: '/' khớp với mọi thứ, nên so sánh độ dài mới
        // không bị "Tổng quan" sáng ở mọi màn hình.
        if (!best || item.path.length > best.length) {
          best = { key: item.key, length: item.path.length };
        }
      }
      if (item.children) {
        walk(item.children);
      }
    }
  };

  walk(nodes);
  return best?.key;
}
