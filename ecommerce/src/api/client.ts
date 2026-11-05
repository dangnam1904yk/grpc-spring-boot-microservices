/**
 * Gọi REST qua API Gateway bằng access token do Keycloak ký (auth-service lấy hộ qua /api/auth/login).
 * - Access token chỉ giữ trong bộ nhớ (mất khi tải lại trang); refresh token lưu localStorage để giữ phiên.
 * - Gặp 401: tự refresh 1 lần (các request đồng thời dùng chung 1 lần refresh) rồi gửi lại.
 * - Lỗi trả về dạng ProblemDetail (RFC 7807) -> ném ApiError.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '';
const REFRESH_TOKEN_KEY = 'refreshToken';
// Khoá cũ của luồng OIDC (đăng nhập trên trang Keycloak), xoá khi gặp
const LEGACY_ID_TOKEN_KEY = 'idToken';

/** Response của /api/auth/login, /register, /refresh. expiresIn: số giây access token còn hiệu lực. */
export interface TokenResponse {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  refreshExpiresIn: number;
}

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
    localStorage.removeItem(LEGACY_ID_TOKEN_KEY);
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
  try {
    setTokens(await publicPost<TokenResponse>('/api/auth/refresh', { refreshToken }));
    return true;
  } catch (e) {
    // Mất kết nối / 503: giữ refresh token để thử lại sau, chỉ xoá khi phiên thật sự hết hạn
    if (e instanceof ApiError && (e.status === 401 || e.status === 400)) {
      setTokens(null);
    }
    return false;
  }
}

/**
 * Đổi refresh token lấy cặp token mới. Trả false nếu không còn phiên hợp lệ.
 * Keycloak (realm bật revoke refresh token) chỉ chấp nhận mỗi refresh token 1 lần, nên các tab phải refresh
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

interface RequestOptions {
  /** Không gửi access token (API đăng nhập / đăng ký / refresh / đăng xuất): token hết hạn sẽ bị chặn 401. */
  anonymous?: boolean;
  /** Gặp 401 thì refresh rồi gửi lại 1 lần. */
  retry?: boolean;
}

export async function request<T>(method: string, path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
  const { anonymous = false, retry = !anonymous } = options;
  const headers: Record<string, string> = {};
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (accessToken && !anonymous) {
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
      return request<T>(method, path, body, { retry: false });
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

/** POST không kèm access token, không tự refresh. */
export const publicPost = <T>(path: string, body?: unknown) => request<T>('POST', path, body, { anonymous: true });

export const api = {
  get: <T>(path: string) => request<T>('GET', path),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, body),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, body),
  delete: (path: string) => request<void>('DELETE', path),
};
