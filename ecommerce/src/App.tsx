import { Button, Result } from 'antd';
import { BrowserRouter, Link, Route, Routes } from 'react-router-dom';
import './App.css';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth, RequirePermission } from './auth/guards';
import AppLayout from './layout/AppLayout';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import MenusPage from './pages/admin/MenusPage';
import PermissionsPage from './pages/admin/PermissionsPage';
import RolesPage from './pages/admin/RolesPage';
import UsersPage from './pages/admin/UsersPage';

/** Trang quản trị: path khớp với menu auth-service tạo sẵn (AuthorizationBootstrap). */
const ADMIN_PAGES = [
  { path: 'admin/users', permission: 'user:VIEW', element: <UsersPage /> },
  { path: 'admin/roles', permission: 'role:VIEW', element: <RolesPage /> },
  { path: 'admin/permissions', permission: 'permission:VIEW', element: <PermissionsPage /> },
  { path: 'admin/menus', permission: 'menu:VIEW', element: <MenusPage /> },
];

function NotFound() {
  return (
    <Result
      status="404"
      title="404"
      subTitle="Không tìm thấy trang"
      extra={
        <Link to="/">
          <Button type="primary">Về trang chủ</Button>
        </Link>
      }
    />
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route
            element={
              <RequireAuth>
                <AppLayout />
              </RequireAuth>
            }
          >
            <Route index element={<HomePage />} />
            {ADMIN_PAGES.map(({ path, permission, element }) => (
              <Route key={path} path={path} element={<RequirePermission permission={permission}>{element}</RequirePermission>} />
            ))}
            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
