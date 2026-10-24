import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5175, // Đổi port tại đây
    strictPort: true // (tùy chọn) Nếu true thì sẽ báo lỗi nếu port đã bị chiếm
  }
})
