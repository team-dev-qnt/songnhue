import { BellOutlined, LogoutOutlined, MenuOutlined, UserOutlined } from '@ant-design/icons';
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
import { useMemo, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

import { useAuth } from '@/app/auth/useAuth';
import { MENU, findMenuKey, visibleMenu, type MenuNode } from '@/app/menu';
import { api } from '@/shared/apiClient';
import { neutralColors, sizing } from 'design-tokens';

const { Header, Sider, Content } = Layout;

/** Khung chung của mọi màn hình quản trị: thanh bên theo quyền, thanh trên, vùng nội dung. */
export function AdminLayout() {
  const { user, logout, hasPermission, maintenance } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();

  const items = useMemo(() => visibleMenu(MENU, hasPermission), [hasPermission]);
  const selectedKey = findMenuKey(MENU, location.pathname);

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
      >
        <div
          style={{
            height: sizing.headerHeight,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: '#fff',
            fontWeight: 700,
            letterSpacing: 0.5,
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
            borderBottom: `1px solid ${neutralColors.border}`,
          }}
        >
          <Button
            type="text"
            icon={<MenuOutlined />}
            onClick={() => setCollapsed((value) => !value)}
            aria-label="Thu gọn menu"
          />

          <Space size="middle">
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

        <Content style={{ margin: 16 }}>
          {maintenance && (
            <Alert
              type="warning"
              showIcon
              banner
              style={{ marginBottom: 16 }}
              message="Hệ thống đang bảo trì"
              description="Đang khôi phục dữ liệu — mọi thao tác thay đổi dữ liệu tạm thời bị chặn. Xem lại sau khi có thông báo hoàn tất."
            />
          )}
          <Outlet />
        </Content>

        <Layout.Footer style={{ textAlign: 'center', paddingBlock: 12 }}>
          <Typography.Text type="secondary">
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
