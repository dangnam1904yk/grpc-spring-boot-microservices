import type { TokenResponse } from './types';

/**
 * Gọi REST qua API Gateway.
 * - Access token chỉ giữ trong bộ nhớ (mất khi tải lại trang); refresh token lưu localStorage để giữ phiên.
 * - Gặp 401: tự refresh 1 lần (các request đồng thời dùng chung 1 lần refresh) rồi gửi lại.
 * - Lỗi trả về dạng ProblemDetail (RFC 7807) -> ném ApiError.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
const REFRESH_TOKEN_KEY = 'refreshToken';

let accessToken: string | null = null;
let refreshing: Promise<boolean> | null = null;
let onSessionExpired: () => void = () => {};

export class ApiError extends Error {
  status: number;
  /** ErrorCode của backend: NOT_FOUND, VERSION_CONFLICT, IN_USE... */
  code?: string;
  /** Lỗi validation theo field */
  errors?: Record<string, string>;

  constructor(status: number, message: string, code?: string, errors?: Record<string, string>) {
    super(message);
    this.status = status;
    this.code = code;
    this.errors = errors;
  }
}

export function setTokens(tokens: TokenResponse | null) {
  accessToken = tokens?.accessToken ?? null;
  try {
    if (tokens) {
      localStorage.setItem(REFRESH_TOKEN_KEY, tokens.refreshToken);
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY);
    }
  } catch {
    // localStorage bị chặn: phiên chỉ sống tới khi tải lại trang
  }
}

export function getRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  } catch {
    return null;
  }
}

/** Gọi khi refresh thất bại (phiên hết hạn / bị thu hồi). */
export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler;
}

async function doRefresh(): Promise<boolean> {
  // Đọc trong lock: tab khác có thể vừa xoay vòng token và lưu token mới
  const refreshToken = getRefreshToken();
  if (!refreshToken) {
    return false;
  }
  const res = await fetch(`${BASE_URL}/api/auth/refresh`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  });
  if (!res.ok) {
    setTokens(null);
    return false;
  }
  setTokens((await res.json()) as TokenResponse);
  return true;
}

/**
 * Đổi refresh token lấy cặp token mới. Trả false nếu không còn phiên hợp lệ.
 * Backend coi refresh token bị dùng lại là bị đánh cắp và thu hồi mọi phiên, nên các tab phải refresh
 * lần lượt (Web Locks), không gửi cùng 1 token.
 */
export function refreshTokens(): Promise<boolean> {
  if (!refreshing) {
    const locked = typeof navigator !== 'undefined' && navigator.locks
      ? navigator.locks.request('auth-refresh', doRefresh)
      : doRefresh();
    refreshing = locked.finally(() => {
      refreshing = null;
    });
  }
  return refreshing;
}

async function toApiError(res: Response): Promise<ApiError> {
  try {
    const body = await res.json();
    return new ApiError(res.status, body.detail || body.title || res.statusText, body.code, body.errors);
  } catch {
    return new ApiError(res.status, res.statusText || 'Lỗi kết nối');
  }
}

export async function request<T>(method: string, path: string, body?: unknown, retry = true): Promise<T> {
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (accessToken) {
    headers.Authorization = `Bearer ${accessToken}`;
  }
  let res: Response;
  try {
    res = await fetch(`${BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new ApiError(0, 'Không kết nối được tới server');
  }

  if (res.status === 401 && retry && getRefreshToken()) {
    if (await refreshTokens()) {
      return request<T>(method, path, body, false);
    }
    onSessionExpired();
  }
  if (!res.ok) {
    throw await toApiError(res);
  }
  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: (path: string) => request<void>('DELETE', path),
};
