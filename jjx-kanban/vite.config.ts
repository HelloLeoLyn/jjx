import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    proxy: {
      '/production': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/notification': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/kanban/board': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/dev-tasks': {
        target: 'http://localhost:8899',
        changeOrigin: true,
        rewrite: (path: string) => path.replace(/^\/dev-tasks/, ''),
      },
    },
  },
})
