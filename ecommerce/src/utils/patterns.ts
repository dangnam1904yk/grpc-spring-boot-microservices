// Giống validation của auth-service (AuthzDtos)

/** Code của thao tác: không có ":" vì ":" ngăn cách menu và thao tác trong mã quyền. */
export const PERMISSION_CODE = /^[A-Z][A-Z0-9_]{0,49}$/;
export const PERMISSION_CODE_MESSAGE = 'Chỉ gồm chữ in hoa, số, "_", bắt đầu bằng chữ (vd. EXPORT)';

export const MENU_CODE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$/;
export const MENU_CODE_MESSAGE = 'Chỉ gồm chữ, số, ".", "_", "-", tối đa 50 ký tự';

export const ROLE_CODE = /^[A-Z][A-Z0-9_]{0,19}$/;
export const ROLE_CODE_MESSAGE = 'Chỉ gồm chữ in hoa, số, "_", bắt đầu bằng chữ, tối đa 20 ký tự';
