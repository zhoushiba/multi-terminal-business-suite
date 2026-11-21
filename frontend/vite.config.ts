import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 开发/预览都用相对 /api 请求，再由 Vite 代理到 Spring Boot（8080），
// 这样前端代码里不出现后端地址，部署时换成 Nginx 反向代理即可。
const apiProxy = {
  '/api': {
    target: 'http://127.0.0.1:8080',
    changeOrigin: true
  },
  '/uploads': {
    target: 'http://127.0.0.1:8080',
    changeOrigin: true
  }
}

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': '/src'
    }
  },
  server: {
    port: 5173,
    host: '127.0.0.1',
    proxy: apiProxy
  },
  preview: {
    port: 4173,
    host: '127.0.0.1',
    proxy: apiProxy
  },
  build: {
    outDir: 'dist',
    chunkSizeWarningLimit: 1600,
    rollupOptions: {
      output: {
        manualChunks: {
          vue: ['vue', 'vue-router', 'pinia'],
          element: ['element-plus', '@element-plus/icons-vue'],
          echarts: ['echarts']
        }
      }
    }
  }
})
