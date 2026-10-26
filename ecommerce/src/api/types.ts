// Kiểu dữ liệu khớp với DTO của auth-service (AuthDtos, AuthzDtos)

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  email: string;
  roles: string[];
  enabled: boolean;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface MenuNode {
  id: string;
  code: string;
  name: string;
  path: string | null;
  icon: string | null;
  sortOrder: number;
  active: boolean;
  children: MenuNode[];
}

export interface MyAuthorities {
  roles: string[];
  permissions: string[];
  menus: MenuNode[];
}

export interface Action {
  id: string;
  code: string;
  name: string;
  description: string | null;
  version: number;
}

export interface Resource {
  id: string;
  code: string;
  name: string;
  description: string | null;
  actions: string[];
  /** "resource:action" */
  permissions: string[];
  version: number;
}

export interface Role {
  id: string;
  code: string;
  name: string;
  description: string | null;
  builtIn: boolean;
  permissions: string[];
  menuIds: string[];
  version: number;
}

export interface Menu {
  id: string;
  code: string;
  name: string;
  path: string | null;
  icon: string | null;
  parentId: string | null;
  sortOrder: number;
  active: boolean;
  version: number;
}
