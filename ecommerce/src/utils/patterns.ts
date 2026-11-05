// Giống validation của auth-service (AuthzDtos)

/** Code của thao tác: không có ":" vì ":" ngăn cách menu và thao tác trong mã quyền. */
export const PERMISSION_CODE = /^[A-Z][A-Z0-9_]{0,49}$/;
export const PERMISSION_CODE_MESSAGE = 'Chỉ gồm chữ in hoa, số, "_", bắt đầu bằng chữ (vd. EXPORT)';

export const MENU_CODE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$/;
export const MENU_CODE_MESSAGE = 'Chỉ gồm chữ, số, ".", "_", "-", tối đa 50 ký tự';

export const ROLE_CODE = /^[A-Z][A-Z0-9_]{0,19}$/;
export const ROLE_CODE_MESSAGE = 'Chỉ gồm chữ in hoa, số, "_", bắt đầu bằng chữ, tối đa 20 ký tự';

/** Password policy của realm Keycloak: 8-72 ký tự, có ít nhất 1 chữ số (độ dài kiểm tra riêng). */
export const PASSWORD = /\d/;
export const PASSWORD_MESSAGE = 'Phải có ít nhất 1 chữ số';

/** Giống RegisterRequest của auth-service. */
export const PHONE = /^\+?[0-9]{8,15}$/;
export const PHONE_MESSAGE = 'Chỉ gồm 8-15 chữ số, có thể bắt đầu bằng "+" (vd. 0912345678)';
