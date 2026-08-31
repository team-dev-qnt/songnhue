import {
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BellOutlined,
  CloudServerOutlined,
  DashboardOutlined,
  HeartOutlined,
  InboxOutlined,
  FileTextOutlined,
  FundProjectionScreenOutlined,
  LaptopOutlined,
  LayoutOutlined,
  PictureOutlined,
  MailOutlined,
  ReadOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  TeamOutlined,
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
        key: 'giao-dien',
        label: 'Giao diện cổng',
        icon: <LayoutOutlined />,
        path: '/noi-dung/giao-dien',
        permissions: ['cms:layout:manage'],
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
export function visibleMenu(
  nodes: readonly MenuNode[],
  hasPermission: (code: string) => boolean,
): MenuNode[] {
  return nodes
    .map((node) => {
      const children = node.children ? visibleMenu(node.children, hasPermission) : undefined;
      const allowed = !node.permissions || node.permissions.some((code) => hasPermission(code));

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
