import {
  BellOutlined,
  LogoutOutlined,
  MenuOutlined,
  QuestionCircleOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Avatar,
  Badge,
  Button,
  Dropdown,
  Layout,
  Menu,
  Space,
  Typography,
  type MenuProps,
} from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { Tooltip } from 'antd';

import { useAuth } from '@/app/auth/useAuth';
import { MENU, findMenuKey, visibleMenu, type MenuNode } from '@/app/menu';
import { type TomTatCauHinhView } from '@/shared/api-types';
import { api } from '@/shared/apiClient';
import { neutralColors, sizing } from '@songnhue/design-tokens';

const { Header, Sider, Content } = Layout;

/** Khung chung của mọi màn hình quản trị: thanh bên theo quyền, thanh trên, vùng nội dung. */
export function AdminLayout() {
  const { user, logout, hasPermission, maintenance } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  // T51.8 — `coHoSoNhanSu` ⛔ không phải một mã quyền, nên nó đi bằng tham số riêng. `user` có thể
  // là `null` trong khoảnh khắc khôi phục phiên ⇒ `?? false` ⇒ mục ẩn, fail-closed.
  const items = useMemo(
    () => visibleMenu(MENU, hasPermission, { coHoSoNhanSu: user?.coHoSoNhanSu ?? false }),
    [hasPermission, user?.coHoSoNhanSu],
  );
  const selectedKey = findMenuKey(MENU, location.pathname);

  // T61.41 — "vẫn hiện cảnh báo nếu chưa cấu hình": chỉ tài khoản có quyền xem tình trạng cấu hình (SUPER_ADMIN)
  //   mới hỏi, và hỏi thưa (5 phút) — mỗi lượt máy chủ còn PING máy quét virus.
  const coQuyenCauHinh = hasPermission('adm:system-config:view');
  const tomTatCauHinh = useQuery({
    queryKey: ['system', 'cau-hinh', 'tom-tat'],
    queryFn: () => api.get<TomTatCauHinhView>('/system/cau-hinh/tom-tat'),
    enabled: coQuyenCauHinh,
    refetchInterval: 300_000,
  });
  const soChanCauHinh = tomTatCauHinh.data?.soChan ?? 0;
  const soCanhBaoCauHinh = tomTatCauHinh.data?.soCanhBao ?? 0;

  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: () => api.get<{ unread: number }>('/notifications/unread-count'),
    // Hộp thư là thứ người trực nhìn cả ngày; 60 giây là đủ tươi mà không tạo tải vô ích.
    refetchInterval: 60_000,
  });

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        trigger={null}
        width={sizing.siderWidth}
        collapsedWidth={sizing.siderCollapsedWidth}
        style={{
          boxShadow: '2px 0 8px 0 rgba(0, 0, 0, 0.1)',
          zIndex: 10,
        }}
      >
        <div
          style={{
            height: sizing.headerHeight,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            // ⚠ Chữ trên dải gradient thương hiệu (xanh đậm) — phải TRẮNG THẬT, ⛔ theo chủ đề:
            //   một `colorTextLightSolid` đổi theo theme sẽ làm chữ chìm vào nền ở chế độ tối.
            color: neutralColors.bgContainer,
            fontWeight: 700,
            letterSpacing: 0.5,
            background: 'linear-gradient(180deg, rgba(255,255,255,0.08) 0%, transparent 100%)',
            borderBottom: '1px solid rgba(255,255,255,0.06)',
          }}
        >
          {collapsed ? 'SN' : 'SÔNG NHUỆ'}
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={selectedKey ? [selectedKey] : []}
          defaultOpenKeys={['quan-tri']}
          items={items.map(toAntdItem)}
        />
      </Sider>

      <Layout>
        <Header
          style={{
            background: neutralColors.bgContainer,
            paddingInline: 16,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px 0 rgba(0, 0, 0, 0.06)',
            zIndex: 9,
          }}
        >
          <Button
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setCollapsed((value) => !value)}
            aria-label="Thu gọn menu"
          />

          <Space size="middle">
            <NutTroGiup duongDan={location.pathname} />

            <Link to="/hop-thu" aria-label="Hộp thư">
              <Badge count={unread.data?.unread ?? 0} size="small">
                <BellOutlined style={{ fontSize: 18 }} />
              </Badge>
            </Link>

            <Dropdown
              menu={{
                items: [
                  { key: 'doi-mat-khau', label: 'Đổi mật khẩu' },
                  { key: 'phien-dang-nhap', label: 'Phiên đăng nhập' },
                  { type: 'divider' },
                  { key: 'dang-xuat', label: 'Đăng xuất', icon: <LogoutOutlined />, danger: true },
                ],
                onClick: ({ key }) => {
                  if (key === 'dang-xuat') {
                    void logout();
                  } else {
                    navigate(`/${key}`);
                  }
                },
              }}
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar size="small" icon={<UserOutlined />} />
                <span>{user?.fullName ?? user?.username}</span>
              </Space>
            </Dropdown>
          </Space>
        </Header>

        <Content style={{ margin: 20, marginTop: 16 }}>
          {maintenance && (
            <Alert
              type="warning"
              showIcon
              banner
              style={{ marginBottom: 16, borderRadius: 8 }}
              title="Hệ thống đang bảo trì"
              description="Đang khôi phục dữ liệu — mọi thao tác thay đổi dữ liệu tạm thời bị chặn. Xem lại sau khi có thông báo hoàn tất."
            />
          )}
          {coQuyenCauHinh &&
            (soChanCauHinh > 0 || soCanhBaoCauHinh > 0) &&
            location.pathname !== '/quan-tri/tinh-trang-cau-hinh' && (
              <Alert
                type={soChanCauHinh > 0 ? 'error' : 'warning'}
                showIcon
                banner
                style={{ marginBottom: 16, borderRadius: 8 }}
                title={
                  soChanCauHinh > 0
                    ? `Cấu hình hệ thống: ${soChanCauHinh} mục CHẶN${soCanhBaoCauHinh > 0 ? `, ${soCanhBaoCauHinh} mục cần chú ý` : ''}`
                    : `Cấu hình hệ thống: ${soCanhBaoCauHinh} mục cần chú ý`
                }
                action={<Link to="/quan-tri/tinh-trang-cau-hinh">Xem chi tiết</Link>}
              />
            )}
          <div className="sn-page-enter">
            <Outlet />
          </div>
        </Content>

        <Layout.Footer
          style={{
            textAlign: 'center',
            paddingBlock: 12,
            background: 'transparent',
          }}
        >
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Công ty TNHH MTV Đầu tư Phát triển Thủy lợi Sông Nhuệ
          </Typography.Text>
        </Layout.Footer>
      </Layout>
    </Layout>
  );
}

/** Một phần tử `items` của Menu AntD — khai tường minh vì `toAntdItem` gọi đệ quy. */
type AntdMenuItem = Required<MenuProps>['items'][number];

/** Đổi khai báo menu của mình sang hình dạng AntD, giữ nguyên cấu trúc cây. */
function toAntdItem(node: MenuNode): AntdMenuItem {
  if (node.children) {
    return {
      key: node.key,
      icon: node.icon,
      label: node.label,
      children: node.children.map(toAntdItem),
    };
  }
  return {
    key: node.key,
    icon: node.icon,
    label: node.path ? <Link to={node.path}>{node.label}</Link> : node.label,
  };
}

/**
 * Nút `?` — dẫn thẳng tới mục hướng dẫn của **màn hình đang mở**.
 *
 * <h3>⭐⭐ Một chỗ, phủ cả 51 màn hình</h3>
 *
 * Đặt ở khung chung thay vì gắn vào từng trang: 51 lượt sửa là 51 dịp quên, và màn hình bị quên
 * luôn là màn hình ⛔ ai mở hằng ngày — đúng hình dạng luật 27 đã trả giá nhiều lần. Ở đây bảng
 * tra **ĐO từ tài liệu**, nên màn hình mới có hướng dẫn là nút tự hoạt động.
 *
 * <h3>⛔⛔ Ẩn khi ⛔ tra ra mục, ⛔ dẫn bừa</h3>
 *
 * `phuManHinh.test.ts` làm CI đỏ khi một màn hình trong `MENU` ⛔ có mục hướng dẫn, nên về nguyên
 * tắc nút luôn hiện. Nhưng với một đường dẫn ngoài `MENU` (trang 404, trang chi tiết lạ) thì tra
 * ⛔ ra — và một nút dẫn tới **giữa tài liệu một cách ngẫu nhiên** tệ hơn hẳn ⛔ có nút: người
 * dùng tin là mình vừa được đưa tới đúng chỗ rồi đọc nhầm hướng dẫn của màn hình khác (T23.8).
 *
 * <h3>⚠ `import()` động, ⛔ phải import tĩnh</h3>
 *
 * Bảng tra kéo theo tệp markdown **60 KB**. Khung này nạp ở **mọi** lượt tải trang, nên nhập tĩnh
 * là bắt cả Công ty tải tài liệu hướng dẫn mỗi lần đăng nhập để phục vụ một cái nút (NFR-03).
 */
function NutTroGiup({ duongDan }: { duongDan: string }) {
  const [neo, setNeo] = useState<string | undefined>(undefined);

  useEffect(() => {
    let conSong = true;
    void import('@/features/help/neoHuongDan').then((m) => {
      // ⚠ Tránh đặt state sau khi component đã tháo: lượt chuyển trang nhanh hơn lượt tải bó mã.
      if (conSong) {
        setNeo(m.neoChoDuongDan(duongDan));
      }
    });
    return () => {
      conSong = false;
    };
  }, [duongDan]);

  if (!neo) {
    return null;
  }
  return (
    <Tooltip title="Hướng dẫn sử dụng màn hình này">
      <Link to={`/huong-dan#${neo}`} aria-label="Hướng dẫn sử dụng màn hình này">
        <QuestionCircleOutlined style={{ fontSize: 18 }} />
      </Link>
    </Tooltip>
  );
}
