// Giống validation của auth-service (AuthzDtos)

/** Code của action / resource: không có ":" vì ":" ngăn cách resource và action trong mã quyền. */
export const RESOURCE_CODE = /^[a-z][a-z0-9-]{0,49}$/;
export const RESOURCE_CODE_MESSAGE = 'Chỉ gồm a-z, 0-9, "-", bắt đầu bằng chữ';

export const ROLE_CODE = /^[A-Z][A-Z0-9_]{0,19}$/;
export const ROLE_CODE_MESSAGE = 'Chỉ gồm A-Z, 0-9, "_", bắt đầu bằng chữ, tối đa 20 ký tự';

export const MENU_CODE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/;
export const MENU_CODE_MESSAGE = 'Chỉ gồm chữ, số, ".", "_", "-"';
