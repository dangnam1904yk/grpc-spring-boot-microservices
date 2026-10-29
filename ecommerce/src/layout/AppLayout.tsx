import {
  HomeOutlined,
  InfoCircleOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Avatar, Button, Descriptions, Dropdown, Grid, Layout, Menu, Modal, Space, Tag, Typography, type MenuProps } from 'antd';
import { useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { MenuNode } from '../api/types';
import { ROLE_ADMIN, useAuth } from '../auth/AuthContext';
import { menuIcon } from './icons';

const { Text, Title } = Typography;

type MenuItem = Required<MenuProps>['items'][number];

/** Menu lá có url thì key = url (để highlight theo URL), menu nhóm thì key = code. */
function toItems(nodes: MenuNode[]): MenuItem[] {
  return nodes.map((node) =>
    node.children.length > 0
      ? { key: node.code, icon: menuIcon(node.icon), label: node.name, children: toItems(node.children) }
      : { key: node.url ?? node.code, icon: menuIcon(node.icon), label: node.name },
  );
}

/** Code các menu nhóm chứa url đang mở (để mở sẵn submenu). */
function parentsOf(nodes: MenuNode[], url: string, parents: string[] = []): string[] {
  for (const node of nodes) {
    if (node.url === url) {
      return parents;
    }
    const found = parentsOf(node.children, url, [...parents, node.code]);
    if (found.length > 0) {
      return found;
    }
  }
  return [];
}

export default function AppLayout() {
  const { user, authorities, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const screens = Grid.useBreakpoint();
  const isMobile = !screens.md;
  const [collapsed, setCollapsed] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);

  const menus = useMemo(() => authorities?.menus ?? [], [authorities]);
  const items = useMemo<MenuItem[]>(
    () => [{ key: '/', icon: <HomeOutlined />, label: 'Trang chủ' }, ...toItems(menus)],
    [menus],
  );

  const displayName = user?.fullName || user?.email || '';
  const roles = authorities?.roles ?? [];
  const roleLabel = roles.includes(ROLE_ADMIN) ? 'Quản trị viên' : roles.filter((r) => r !== 'USER').join(', ') || 'Người dùng';

  const signOut = async () => {
    await logout();
    navigate('/login', { replace: true });
  };

  const userMenu: MenuProps = {
    items: [
      {
        key: 'info',
        type: 'group',
        label: (
          <div className="profile-menu-head">
            <Text strong>{displayName}</Text>
            <Text type="secondary" className="small">
              {user?.email}
            </Text>
            <Tag color="blue" className="profile-role">
              {roleLabel}
            </Tag>
          </div>
        ),
      },
      { type: 'divider' },
      { key: 'profile', icon: <InfoCircleOutlined />, label: 'Thông tin tài khoản' },
      { key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất', danger: true },
    ],
    onClick: ({ key }) => {
      if (key === 'profile') {
        setProfileOpen(true);
      } else if (key === 'logout') {
        signOut();
      }
    },
  };

  const sideCollapsed = isMobile || collapsed;

  return (
    <Layout className="app-layout">
      <Layout.Sider
        theme="light"
        trigger={null}
        collapsible
        collapsed={sideCollapsed}
        collapsedWidth={isMobile ? 0 : 80}
        width={240}
        className="app-sider"
      >
        <div className="app-logo">
          <Title level={4} className="app-logo-text">
            {sideCollapsed ? 'E' : 'Ecommerce'}
          </Title>
        </div>
        <Menu
          mode="inline"
          items={items}
          selectedKeys={[location.pathname]}
          defaultOpenKeys={parentsOf(menus, location.pathname)}
          onClick={({ key }) => key.startsWith('/') && navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <Button
            type="text"
            className="sider-toggle"
            icon={sideCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            onClick={() => setCollapsed(!collapsed)}
            disabled={isMobile}
          />
          <Dropdown menu={userMenu} trigger={['click']} placement="bottomRight">
            <Space className="app-user">
              <Avatar className="user-avatar">{displayName.charAt(0).toUpperCase()}</Avatar>
              {!isMobile && (
                <div className="app-user-text">
                  <Text strong className="small">
                    {displayName}
                  </Text>
                  <Text type="secondary" className="tiny">
                    {roleLabel}
                  </Text>
                </div>
              )}
            </Space>
          </Dropdown>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
      </Layout>

      <Modal
        title="Thông tin tài khoản"
        open={profileOpen}
        onCancel={() => setProfileOpen(false)}
        footer={
          <Button type="primary" onClick={() => setProfileOpen(false)}>
            Đóng
          </Button>
        }
        centered
        width={440}
      >
        <div className="profile-head">
          <Avatar size={72} className="user-avatar profile-avatar">
            {displayName.charAt(0).toUpperCase()}
          </Avatar>
          <Title level={4}>{displayName}</Title>
          <Text type="secondary">{roleLabel}</Text>
        </div>
        <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="Họ và tên">{user?.fullName || '—'}</Descriptions.Item>
          <Descriptions.Item label="Email">{user?.email}</Descriptions.Item>
          <Descriptions.Item label="Số điện thoại">{user?.phoneNumber || 'Chưa cập nhật'}</Descriptions.Item>
          <Descriptions.Item label="Vai trò">
            <Space wrap size={4}>
              {roles.map((r) => (
                <Tag key={r} color={r === ROLE_ADMIN ? 'red' : 'blue'}>
                  {r}
                </Tag>
              ))}
            </Space>
          </Descriptions.Item>
          <Descriptions.Item label="Trạng thái">
            <Text type="success">Đang hoạt động</Text>
          </Descriptions.Item>
        </Descriptions>
      </Modal>
    </Layout>
  );
}
