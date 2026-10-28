import {
  AppstoreOutlined,
  DashboardOutlined,
  FileTextOutlined,
  FolderOutlined,
  HomeOutlined,
  KeyOutlined,
  MenuOutlined,
  ProfileOutlined,
  SafetyCertificateOutlined,
  SettingOutlined,
  ShopOutlined,
  ShoppingCartOutlined,
  TagsOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  UploadOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { ReactNode } from 'react';

/** Menu lưu tên icon (chuỗi) trong DB; chỉ các icon khai báo ở đây mới hiển thị được. */
export const ICON_OPTIONS: { value: string; label: string; icon: ReactNode }[] = [
  { value: 'MenuOutlined', label: 'Menu', icon: <MenuOutlined /> },
  { value: 'HomeOutlined', label: 'Trang chủ', icon: <HomeOutlined /> },
  { value: 'DashboardOutlined', label: 'Dashboard / Báo cáo', icon: <DashboardOutlined /> },
  { value: 'AppstoreOutlined', label: 'Appstore / Khối', icon: <AppstoreOutlined /> },
  { value: 'UserOutlined', label: 'Người dùng', icon: <UserOutlined /> },
  { value: 'TeamOutlined', label: 'Nhóm / Khách hàng', icon: <TeamOutlined /> },
  { value: 'SafetyCertificateOutlined', label: 'Vai trò & Phân quyền', icon: <SafetyCertificateOutlined /> },
  { value: 'KeyOutlined', label: 'Quyền / Key', icon: <KeyOutlined /> },
  { value: 'SettingOutlined', label: 'Cài đặt', icon: <SettingOutlined /> },
  { value: 'ShopOutlined', label: 'Cửa hàng / Doanh nghiệp', icon: <ShopOutlined /> },
  { value: 'ShoppingCartOutlined', label: 'Sản phẩm / Mua sắm', icon: <ShoppingCartOutlined /> },
  { value: 'ProfileOutlined', label: 'Đơn hàng / Hồ sơ', icon: <ProfileOutlined /> },
  { value: 'TagsOutlined', label: 'Nhãn / Danh mục', icon: <TagsOutlined /> },
  { value: 'UploadOutlined', label: 'Tải lên', icon: <UploadOutlined /> },
  { value: 'FolderOutlined', label: 'Thư mục', icon: <FolderOutlined /> },
  { value: 'FileTextOutlined', label: 'Tài liệu / Log', icon: <FileTextOutlined /> },
  { value: 'ToolOutlined', label: 'Công cụ', icon: <ToolOutlined /> },
  { value: 'ThunderboltOutlined', label: 'Thao tác', icon: <ThunderboltOutlined /> },
];

const ICONS = new Map(ICON_OPTIONS.map((o) => [o.value, o.icon]));

/** Icon không có trong danh sách thì dùng MenuOutlined. */
export function menuIcon(name: string | null | undefined): ReactNode {
  return (name && ICONS.get(name)) || <MenuOutlined />;
}
