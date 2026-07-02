import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3005,       // 固定开发服务器端口，避免与其他项目冲突
    strictPort: true, // 端口被占用时直接报错，不自动跳到其他端口
    cors: true
  }
})
