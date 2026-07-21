import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { resolve } from 'path'
// 添加导入
import { createSvgIconsPlugin } from 'vite-plugin-svg-icons'

// https://vitejs.dev/config/
export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd())
  return {
    plugins: [
      vue(),
      // 添加 svg 图标插件
      createSvgIconsPlugin({
        iconDirs: [
          resolve(process.cwd(), 'src/icons/svg'),
          resolve(process.cwd(), 'src/icons/jjx'),
        ],
        symbolId: 'icon-[name]',
      }),
      AutoImport({
        resolvers: [ElementPlusResolver()],
        imports: ['vue', 'vue-router', 'pinia'],
        dts: 'src/auto-imports.d.ts',
      }),
      Components({
        resolvers: [ElementPlusResolver()],
        dts: 'src/components.d.ts',
      }),
    ],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
      },
    },
    server: {
      port: Number(env.VITE_PORT) || 3000,
      host: '0.0.0.0',
      open: false,
      hmr: { overlay: true },
      // WSL2 文件系统需要轮询才能触发热更新
      watch: {
        usePolling: true,
        interval: 500,
      },
      proxy: {
        [env.VITE_BASE_API || '/api']: {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8080',
          changeOrigin: true,
          rewrite: (path) =>
            path.replace(new RegExp(`^${env.VITE_BASE_API || '/api'}`), ''),
        },
        '/sessions': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/notification': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
        '/production': {
          target: 'http://localhost:8080',
          changeOrigin: true,
        },
      },
    },
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: `@use "@/styles/variables.scss" as *;`,
        },
      },
    },
    build: {
      rollupOptions: {
        output: {
          chunkFileNames: 'assets/js/[name]-[hash].js',
          entryFileNames: 'assets/js/[name]-[hash].js',
          assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
        },
      },
    },
  }
})
