<template>
  <div />
</template>

<script setup lang="ts">
/**
 * 旧「来料不合格处置」页（dev-20260929-007 收敛）
 *
 * 处置动作已并入来料检验工作台 /inventory/iqc 的「待处理明细」区（跨批次视角由列表流程状态筛选代替），
 * 本路由只保留兼容跳转：历史菜单/通知/其它页面（质量不良台账、检验批工作台）的链接不失效。
 * 原页面的 4 类行合并工作台实现已删除（避免第二份实现与死代码）。
 */
import { onBeforeMount } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
onBeforeMount(() => {
  router.replace({ path: '/inventory/iqc', query: route.query })
})
</script>
