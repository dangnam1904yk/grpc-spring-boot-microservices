/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Gốc URL của API Gateway khi build production; để trống khi dev (đi qua proxy của Vite). */
  readonly VITE_API_BASE_URL?: string;
}
