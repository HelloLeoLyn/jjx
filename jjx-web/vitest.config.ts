import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";
import AutoImport from "unplugin-auto-import/vite";
import Components from "unplugin-vue-components/vite";
import { ElementPlusResolver } from "unplugin-vue-components/resolvers";
import { resolve } from "path";

/**
 * 前端测试栈配置（dev-20261016-060）：Vitest + @vue/test-utils + jsdom。
 *
 * 1）与既有手搓 `node --test` + vm 沙箱的用例**并存**，靠**文件后缀分工**避免互相误收：
 *    - vitest 新用例 → `*.spec.ts`（本 runner 只收集它）
 *    - 遗留 node:test 用例 → `*.test.cjs` / `*.test.ts`（例如 quotationAttachments.test.cjs、
 *      engineering/product-spec 下的 4 个 .test.ts），这些**不在本 runner 收集范围内**，迁移时逐个改名。
 *    未按后缀隔离时 vitest 会误收 node:test 文件并报「No test suite found」。
 * 2）插件与 vite.config.ts 同源（vue + element-plus 自动导入），便于直接挂载真实业务组件；
 *    但 `dts: false` —— 跑测试不改写 src/auto-imports.d.ts、src/components.d.ts。
 * 3）内存护栏不在本文件：跑测试统一走
 *    `systemd-run --user --scope -p MemoryMax=1G -p MemorySwapMax=0 -- npx vitest run`
 *    （见 jjx-docs/task-docs/dev-20261016-060-plan.md 与 059 事故档）。
 */
export default defineConfig({
  plugins: [
    vue(),
    AutoImport({
      resolvers: [ElementPlusResolver()],
      imports: ["vue", "vue-router", "pinia"],
      dts: false,
    }),
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false,
    }),
  ],
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
    },
  },
  test: {
    environment: "jsdom",
    css: false,
    include: ["src/**/__tests__/**/*.spec.ts"],
    exclude: ["**/node_modules/**"],
  },
});
