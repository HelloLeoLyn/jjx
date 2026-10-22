import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { createSvgIconsPlugin } from "vite-plugin-svg-icons";
import { resolve } from "node:path";

// 独立预览构建，不修改正在运行的主站Vite配置。
export default defineConfig({
  plugins: [
    vue(),
    createSvgIconsPlugin({
      iconDirs: [resolve("src/icons/svg"), resolve("src/icons/jjx")],
      symbolId: "icon-[name]",
    }),
  ],
  resolve: { alias: { "@": resolve("src") } },
  build: {
    outDir: resolve("../.tmp/uic-preview-dist"),
    emptyOutDir: true,
    rollupOptions: { input: resolve("uic-preview.html") },
  },
});
