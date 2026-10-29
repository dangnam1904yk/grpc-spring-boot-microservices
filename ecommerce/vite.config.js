import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175, // Đổi port tại đây
    strictPort: true, // (tùy chọn) Nếu true thì sẽ báo lỗi nếu port đã bị chiếm
    proxy: {
      // Gọi API qua dev server -> API Gateway: với trình duyệt là cùng origin.
      // Bỏ header Origin: trình duyệt vẫn gửi nó với POST/PUT/DELETE, và gateway sẽ chặn (403) vì
      // origin của dev server không nằm trong CORS_ALLOWED_ORIGINS.
      '/api': {
        target: process.env.VITE_GATEWAY_URL || 'http://localhost:8080',
        changeOrigin: true,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'))
        },
      },
    },
  },
})
