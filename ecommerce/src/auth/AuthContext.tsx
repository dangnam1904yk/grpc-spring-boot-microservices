import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi, type RegisterInput } from '../api/auth';
import { getRefreshToken, refreshTokens, setSessionExpiredHandler, setTokens } from '../api/client';
import type { MyAuthorities, User } from '../api/types';

export const ROLE_ADMIN = 'ADMIN';

interface AuthState {
  /** true trong lúc khôi phục phiên khi mở trang */
  loading: boolean;
  user: User | null;
  authorities: MyAuthorities | null;
  /** Đăng nhập bằng email + mật khẩu (auth-service gọi Keycloak), xong tải thông tin user. */
  login: (email: string, password: string) => Promise<void>;
  /** Đăng ký rồi đăng nhập luôn. */
  register: (input: RegisterInput) => Promise<void>;
  /** Kết thúc phiên trên Keycloak và xoá token ở trình duyệt. */
  logout: () => void;
  /** Tải lại role / quyền / menu (sau khi đổi phân quyền của chính mình). */
  reload: () => Promise<void>;
  /** ADMIN luôn có mọi quyền, giống backend (AuthUser.hasPermission). */
  hasPermission: (permission: string) => boolean;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState<User | null>(null);
  const [authorities, setAuthorities] = useState<MyAuthorities | null>(null);

  const clear = useCallback(() => {
    setUser(null);
    setAuthorities(null);
  }, []);

  const reload = useCallback(async () => {
    const [me, mine] = await Promise.all([authApi.me(), authApi.myAuthorities()]);
    setUser(me);
    setAuthorities(mine);
  }, []);

  // Mở trang: còn refresh token thì lấy access token mới và tải lại thông tin user
  useEffect(() => {
    setSessionExpiredHandler(clear);
    (async () => {
      try {
        if (getRefreshToken() && (await refreshTokens())) {
          await reload();
        }
      } catch {
        clear();
      } finally {
        setLoading(false);
      }
    })();
  }, [clear, reload]);

  const login = useCallback(
    async (email: string, password: string) => {
      setTokens(await authApi.login(email, password));
      await reload();
    },
    [reload],
  );

  const register = useCallback(
    async (input: RegisterInput) => {
      setTokens(await authApi.register(input));
      await reload();
    },
    [reload],
  );

  const logout = useCallback(() => {
    const refreshToken = getRefreshToken();
    setTokens(null);
    clear();
    if (refreshToken) {
      // Không chờ: lỗi mạng thì phiên trên Keycloak tự hết hạn, token ở trình duyệt đã xoá
      authApi.logout(refreshToken).catch(() => {});
    }
  }, [clear]);

  const hasPermission = useCallback(
    (permission: string) =>
      !!authorities && (authorities.roles.includes(ROLE_ADMIN) || authorities.permissions.includes(permission)),
    [authorities],
  );

  const value = useMemo(
    () => ({ loading, user, authorities, login, register, logout, reload, hasPermission }),
    [loading, user, authorities, login, register, logout, reload, hasPermission],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth phải dùng bên trong <AuthProvider>');
  }
  return ctx;
}
