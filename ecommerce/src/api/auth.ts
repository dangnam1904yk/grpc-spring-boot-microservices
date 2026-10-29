import { api, getRefreshToken, setTokens } from './client';
import type {
  MenuNode,
  MyAuthorities,
  Page,
  Permission,
  Role,
  RoleDetail,
  Status,
  TokenResponse,
  User,
} from './types';

export interface CreateUserInput {
  email: string;
  password: string;
  fullName?: string | null;
  phoneNumber?: string | null;
  roles?: string[];
  status?: Status;
}

/** roles bỏ trống = giữ nguyên. */
export interface UpdateUserInput {
  fullName?: string | null;
  phoneNumber?: string | null;
  status?: Status;
  roles?: string[];
  version: number;
}

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
};

export const userApi = {
  list: (params: { keyword?: string; page?: number; size?: number } = {}) => {
    const query = new URLSearchParams();
    if (params.keyword) query.set('keyword', params.keyword);
    query.set('page', String(params.page ?? 0));
    query.set('size', String(params.size ?? 100));
    return api.get<Page<User>>(`/api/auth/users?${query}`);
  },
  create: (input: CreateUserInput) => api.post<User>('/api/auth/users', input),
  update: (id: string, input: UpdateUserInput) => api.put<User>(`/api/auth/users/${id}`, input),
};

export interface PermissionInput {
  code?: string;
  name: string;
  description?: string | null;
  status?: Status;
  version?: number;
}

/** Thao tác (VIEW, ADD, EDIT...). */
export const permissionApi = {
  list: () => api.get<Permission[]>('/api/auth/permissions'),
  create: (input: PermissionInput) => api.post<Permission>('/api/auth/permissions', input),
  update: (id: string, input: PermissionInput) => api.put<Permission>(`/api/auth/permissions/${id}`, input),
  remove: (id: string) => api.delete(`/api/auth/permissions/${id}`),
};

export interface MenuInput {
  code?: string;
  name: string;
  url?: string | null;
  icon?: string | null;
  parentId?: string | null;
  orderIndex?: number | null;
  status?: Status;
  version?: number;
}

export interface MenuOrder {
  id: string;
  orderIndex: number;
  parentId: string | null;
}

export const menuApi = {
  /** Cây đầy đủ, kể cả menu đang tắt. */
  tree: () => api.get<MenuNode[]>('/api/auth/menus'),
  get: (id: string) => api.get<MenuNode>(`/api/auth/menus/${id}`),
  create: (input: MenuInput) => api.post<MenuNode>('/api/auth/menus', input),
  update: (id: string, input: MenuInput) => api.put<MenuNode>(`/api/auth/menus/${id}`, input),
  reorder: (items: MenuOrder[]) => api.put<void>('/api/auth/menus/reorder', items),
  /** Xoá cả menu con / cháu. */
  remove: (id: string) => api.delete(`/api/auth/menus/${id}`),
};

export interface RoleInput {
  code?: string;
  name: string;
  description?: string | null;
  status?: Status;
  /** Bỏ trống = giữ nguyên (khi sửa). */
  menuIds?: string[];
  permissions?: string[];
  userIds?: string[];
  version?: number;
}

export const roleApi = {
  list: () => api.get<Role[]>('/api/auth/roles'),
  get: (id: string) => api.get<RoleDetail>(`/api/auth/roles/${id}`),
  create: (input: RoleInput) => api.post<RoleDetail>('/api/auth/roles', input),
  update: (id: string, input: RoleInput) => api.put<RoleDetail>(`/api/auth/roles/${id}`, input),
  /** Lưu một hàng của ma trận phân quyền. */
  updatePermissions: (id: string, permissions: string[], version: number) =>
    api.put<RoleDetail>(`/api/auth/roles/${id}/permissions`, { permissions, version }),
  updateMenus: (id: string, menuIds: string[], version: number) =>
    api.put<RoleDetail>(`/api/auth/roles/${id}/menus`, { menuIds, version }),
  remove: (id: string) => api.delete(`/api/auth/roles/${id}`),
};
