// Kiểu dữ liệu khớp với DTO của auth-service (AuthDtos, AuthzDtos)

export type Status = 'ACTIVE' | 'INACTIVE';

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface User {
  id: string;
  email: string;
  fullName: string | null;
  phoneNumber: string | null;
  roles: string[];
  enabled: boolean;
  status: Status;
  createDate: string | null;
  version: number;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Một nút trong cây menu. system: menu auth-service tạo sẵn, không xoá được. */
export interface MenuNode {
  id: string;
  code: string;
  name: string;
  url: string | null;
  icon: string | null;
  parentId: string | null;
  orderIndex: number;
  level: number;
  status: Status;
  system: boolean;
  version: number;
  children: MenuNode[];
}

export interface MyAuthorities {
  roles: string[];
  /** "menuCode:PERMISSION", vd. "category:ADD" */
  permissions: string[];
  menus: MenuNode[];
}

/** Thao tác (VIEW, ADD, EDIT...): cột của ma trận phân quyền. */
export interface Permission {
  id: string;
  code: string;
  name: string;
  description: string | null;
  type: 'SYSTEM' | 'CUSTOM';
  status: Status;
  version: number;
}

export interface Role {
  id: string;
  code: string;
  name: string;
  description: string | null;
  system: boolean;
  status: Status;
  accountCount: number;
  permissionCount: number;
  menuCount: number;
  version: number;
}

export interface RoleDetail {
  id: string;
  code: string;
  name: string;
  description: string | null;
  system: boolean;
  status: Status;
  permissions: string[];
  menuIds: string[];
  userIds: string[];
  version: number;
}
