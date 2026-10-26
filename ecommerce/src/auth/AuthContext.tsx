import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import { getRefreshToken, refreshTokens, setSessionExpiredHandler } from '../api/client';
import type { MyAuthorities, User } from '../api/types';

export const ROLE_ADMIN = 'ADMIN';

interface AuthState {
  /** true trong lúc khôi phục phiên khi mở trang */
  loading: boolean;
  user: User | null;
  authorities: MyAuthorities | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
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
      await authApi.login(email, password);
      await reload();
    },
    [reload],
  );

  const logout = useCallback(async () => {
    await authApi.logout();
    clear();
  }, [clear]);

  const hasPermission = useCallback(
    (permission: string) =>
      !!authorities && (authorities.roles.includes(ROLE_ADMIN) || authorities.permissions.includes(permission)),
    [authorities],
  );

  const value = useMemo(
    () => ({ loading, user, authorities, login, logout, reload, hasPermission }),
    [loading, user, authorities, login, logout, reload, hasPermission],
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
