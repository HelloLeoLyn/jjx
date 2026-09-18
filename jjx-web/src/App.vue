<template>
  <router-view />
</template>

<script setup lang="ts">
// 应用根组件
import { onMounted, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useScanner } from '@/composables/useScanner'
import { ensureDocTheme } from '@/composables/useDocTheme'

const router = useRouter()
const route = useRoute()

// 文档主题色：登录后拉 sys_config.theme_color 写到 :root（打印单据 / 移动端卡片统一主题）
// 2026-09-16 dev-20260916-003；失败静默，保留 styles/doc-theme.scss 的兜底色
// 2026-09-18 修复（B 方案）：登录页 / 未登录一律不请求该接口——此前无条件调用时，
// 未登录访问 /m/login 会拿到 code 401，被 utils/request.ts 的 redirectToLogin()
// 误判成 PC 端而 replace 到 /login。判定放在 ensureDocTheme()，并带 token 兜底，
// 避免 App.onMounted 时首屏路由尚未 resolve（route.path 仍是初始 '/'）而漏判。
onMounted(async () => {
  await router.isReady() // 等首屏路由 resolve，确保读到的是真实地址
  ensureDocTheme(route.path)
})
// 登录成功跳业务页 / 换账号回到登录页 时重新判定（已取到过则不重复请求）
watch(
  () => route.path,
  (path) => ensureDocTheme(path)
)

// 全局扫码枪监听（2026-08-12 DEV-979 扫码定位联动）
// 扫到工单号 → 已在生产订单列表页则原地更新查询参数定位；其他页面则跳转并带工单号
// /m/ 移动端页面自行处理扫码，全局监听需排除，避免双重跳转（DEV-981 扫码B）
useScanner({
  enabled: () => !route.path.startsWith('/m/'),
  onScan: (code) => {
    if (
      route.path.startsWith('/production/order') ||
      route.path.startsWith('/production/schedule')
    ) {
      router.push({
        path: route.path,
        query: { ...route.query, orderNo: code, t: Date.now() },
      })
    } else {
      router.push({ path: '/production/order', query: { orderNo: code } })
    }
  },
})
</script>

<style scoped>
#app {
  width: 100%;
  height: 100%;
}
</style>
