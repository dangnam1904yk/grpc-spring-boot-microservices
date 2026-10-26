import { HomeOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons';
import { Avatar, Dropdown, Layout, Menu, Space, Typography, type MenuProps } from 'antd';
import { useMemo, useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import type { MenuNode } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { menuIcon } from './icons';

type MenuItem = Required<MenuProps>['items'][number];

/** Menu lá có path thì key = path (để highlight theo URL), menu nhóm thì key = code. */
function toItems(nodes: MenuNode[]): MenuItem[] {
  return nodes.map((node) =>
    node.children.length > 0
      ? { key: node.code, icon: menuIcon(node.icon), label: node.name, children: toItems(node.children) }
      : { key: node.path ?? node.code, icon: menuIcon(node.icon), label: node.name },
  );
}

/** Code các menu nhóm chứa path đang mở (để mở sẵn submenu). */
function parentsOf(nodes: MenuNode[], path: string, parents: string[] = []): string[] {
  for (const node of nodes) {
    if (node.path === path) {
      return parents;
    }
    const found = parentsOf(node.children, path, [...parents, node.code]);
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
  const [collapsed, setCollapsed] = useState(false);

  const menus = useMemo(() => authorities?.menus ?? [], [authorities]);
  const items = useMemo<MenuItem[]>(
    () => [{ key: '/', icon: <HomeOutlined />, label: 'Trang chủ' }, ...toItems(menus)],
    [menus],
  );

  const userMenu: MenuProps = {
    items: [{ key: 'logout', icon: <LogoutOutlined />, label: 'Đăng xuất' }],
    onClick: async ({ key }) => {
      if (key === 'logout') {
        await logout();
        navigate('/login', { replace: true });
      }
    },
  };

  return (
    <Layout className="app-layout">
      <Layout.Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} width={232} breakpoint="lg">
        <div className="app-logo">{collapsed ? 'E' : 'Ecommerce'}</div>
        <Menu
          theme="dark"
          mode="inline"
          items={items}
          selectedKeys={[location.pathname]}
          defaultOpenKeys={parentsOf(menus, location.pathname)}
          onClick={({ key }) => key.startsWith('/') && navigate(key)}
        />
      </Layout.Sider>
      <Layout>
        <Layout.Header className="app-header">
          <Dropdown menu={userMenu} trigger={['click']}>
            <Space className="app-user">
              <Avatar size="small" icon={<UserOutlined />} />
              <Typography.Text>{user?.email}</Typography.Text>
            </Space>
          </Dropdown>
        </Layout.Header>
        <Layout.Content className="app-content">
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  );
}
