<template>
  <el-descriptions :column="column" border size="small">
    <el-descriptions-item label="产品编码">{{ detail?.product?.productCode || '-' }}</el-descriptions-item>
    <el-descriptions-item label="产品名称">{{ detail?.product?.productName || '-' }}</el-descriptions-item>
    <el-descriptions-item label="客户">{{ detail?.product?.customerName || '-' }}</el-descriptions-item>
    <el-descriptions-item label="当前BOM">{{ bomLabel }}</el-descriptions-item>
    <el-descriptions-item label="当前路线">{{ routeLabel }}</el-descriptions-item>
    <el-descriptions-item label="规格">{{ specLabel }}</el-descriptions-item>
  </el-descriptions>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ProductFullVO } from '@/types/product'

/**
 * 产品基本信息（公共组件；dev-20261010-032）
 * 产品作业规范抽屉与产品详情复用。
 * 字段用后端实际返回的 key：BOM/路线版本取 detail.bom.bomVersion / detail.routing.routingVersion
 * —— ProductVo 不含 currentBomVersion/currentRoutingVersion/specification，
 * 旧代码读错 key 导致「当前BOM/当前路线/规格」恒为 '-'（本次修正）。
 */
const props = withDefaults(defineProps<{ detail?: ProductFullVO | null; column?: number }>(), {
  column: 3,
})

function joinCode(code?: string, version?: string) {
  if (code && version) return `${code}_${version}`
  return code || version || '-'
}

const bomLabel = computed(() => joinCode(props.detail?.bom?.bomCode, props.detail?.bom?.bomVersion))
const routeLabel = computed(() =>
  joinCode(props.detail?.routing?.routingCode, props.detail?.routing?.routingVersion)
)
const specLabel = computed(() => (props.detail?.product?.specJson ? '已配置' : '-'))
</script>
