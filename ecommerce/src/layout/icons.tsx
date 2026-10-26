import {
  AppstoreOutlined,
  DashboardOutlined,
  FileTextOutlined,
  HomeOutlined,
  MenuOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';

/** Menu lưu tên icon (chuỗi) trong DB; chỉ các icon khai báo ở đây mới hiển thị được. */
export const MENU_ICONS: Record<string, ReactNode> = {
  AppstoreOutlined: <AppstoreOutlined />,
  DashboardOutlined: <DashboardOutlined />,
  FileTextOutlined: <FileTextOutlined />,
  HomeOutlined: <HomeOutlined />,
  MenuOutlined: <MenuOutlined />,
  SettingOutlined: <SettingOutlined />,
  ShopOutlined: <ShopOutlined />,
  ShoppingCartOutlined: <ShoppingCartOutlined />,
  TagsOutlined: <TagsOutlined />,
  TeamOutlined: <TeamOutlined />,
  ThunderboltOutlined: <ThunderboltOutlined />,
  UserOutlined: <UserOutlined />,
};

export function menuIcon(name: string | null | undefined): ReactNode {
  return name ? MENU_ICONS[name] : undefined;
}
