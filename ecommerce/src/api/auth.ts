import { api, getRefreshToken, setTokens } from './client';
import type {
  Action,
  Menu,
  MenuNode,
  MyAuthorities,
  Page,
  Resource,
  Role,
  TokenResponse,
  User,
} from './types';

export const authApi = {
  async login(email: string, password: string) {
    const tokens = await api.post<TokenResponse>('/api/auth/login', { email, password });
    setTokens(tokens);
  },

  register: (email: string, password: string) => api.post<User>('/api/auth/register', { email, password }),

  /** Thu hồi refresh token ở server; lỗi mạng cũng vẫn xoá phiên ở trình duyệt. */
  async logout() {
    const refreshToken = getRefreshToken();
    setTokens(null);
    if (refreshToken) {
      await api.post<void>('/api/auth/logout', { refreshToken }).catch(() => undefined);
    }
  },

  me: () => api.get<User>('/api/auth/me'),
  myAuthorities: () => api.get<MyAuthorities>('/api/auth/me/authorities'),

  listUsers: (page: number, size: number) => api.get<Page<User>>(`/api/auth/users?page=${page}&size=${size}`),
  updateUserRoles: (id: string, roles: string[]) => api.put<User>(`/api/auth/users/${id}/roles`, { roles }),
};

export type ActionInput = Pick<Action, 'name' | 'description'> & { code?: string; version?: number };
export type ResourceInput = Pick<Resource, 'name' | 'description' | 'actions'> & { code?: string; version?: number };
export type RoleInput = Pick<Role, 'name' | 'description' | 'permissions' | 'menuIds'> & {
  code?: string;
  version?: number;
};
export type MenuInput = Omit<Menu, 'id' | 'code' | 'version'> & { code?: string; version?: number };

/** CRUD cho 1 loại đối tượng phân quyền: POST tạo mới (có code), PUT cập nhật (có version). */
function crud<T, I>(base: string) {
  return {
    get: (id: string) => api.get<T>(`${base}/${id}`),
    create: (input: I) => api.post<T>(base, input),
    update: (id: string, input: I) => api.put<T>(`${base}/${id}`, input),
    remove: (id: string) => api.delete(`${base}/${id}`),
  };
}

export const actionApi = { list: () => api.get<Action[]>('/api/auth/actions'), ...crud<Action, ActionInput>('/api/auth/actions') };
export const resourceApi = {
  list: () => api.get<Resource[]>('/api/auth/resources'),
  ...crud<Resource, ResourceInput>('/api/auth/resources'),
};
export const roleApi = { list: () => api.get<Role[]>('/api/auth/roles'), ...crud<Role, RoleInput>('/api/auth/roles') };
/** list trả về cây menu đầy đủ (kể cả menu đang tắt). */
export const menuApi = { tree: () => api.get<MenuNode[]>('/api/auth/menus'), ...crud<Menu, MenuInput>('/api/auth/menus') };
