import { Result, Spin } from 'antd';
import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Chưa đăng nhập -> chuyển tới /login, đăng nhập xong quay lại trang cũ. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { loading, user } = useAuth();
  const location = useLocation();
  if (loading) {
    return <Spin size="large" fullscreen />;
  }
  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}

/** Ẩn trang khi thiếu quyền. Backend vẫn kiểm tra lại, đây chỉ để giao diện không hiện nút / trang vô dụng. */
export function RequirePermission({ permission, children }: { permission: string; children: ReactNode }) {
  const { hasPermission } = useAuth();
  if (!hasPermission(permission)) {
    return <Result status="403" title="403" subTitle={`Bạn không có quyền ${permission}`} />;
  }
  return <>{children}</>;
}
