<template>
  <router-view />
</template>

<script setup lang="ts">
// 应用根组件
import { useRouter, useRoute } from 'vue-router'
import { useScanner } from '@/composables/useScanner'

const router = useRouter()
const route = useRoute()

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
