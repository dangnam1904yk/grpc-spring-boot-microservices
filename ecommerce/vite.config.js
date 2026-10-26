import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175, // Đổi port tại đây
    strictPort: true, // (tùy chọn) Nếu true thì sẽ báo lỗi nếu port đã bị chiếm
    proxy: {
      // Gọi API qua dev server -> API Gateway: cùng origin nên không vướng CORS
      '/api': {
        target: process.env.VITE_GATEWAY_URL || 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
