<template>
  <div v-loading="loading">
    <RouteBasicInfo :detail="detail" />
    <RouteProcessTable :items="detail.items || []" />
    <slot name="extra" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { productRouteApi } from '@/api/product/routing'
import type { EngineeringRoutingVO } from '@/types/product/routing'
import { RouteStatusEnum } from '@/enums/product'
import RouteBasicInfo from './RouteBasicInfo.vue'
import RouteProcessTable from './RouteProcessTable.vue'

const loading = ref(false)
const detail = reactive<EngineeringRoutingVO>({
  routingId: 0,
  routingCode: '',
  routingName: '',
  productId: 0,
  productCode: '',
  productName: '',
  routingVersion: '',
  isCurrent: 0,
  isCurrentName: '',
  approveStatus: RouteStatusEnum.DRAFT.value,
  approveStatusName: '',
  totalLaborHours: 0,
  totalMachineHours: 0,
  processCount: 0,
  description: '',
  createBy: '',
  createTime: '',
  updateBy: '',
  updateTime: '',
  remark: '',
  items: [],
})

const loadDetail = async (routingId: number) => {
  if (!routingId) return
  loading.value = true
  try {
    const response = await productRouteApi.getProductRouteInfo(routingId)
    Object.assign(detail, response.data)
  } catch (error) {
    console.error('加载工艺路线详情失败:', error)
  } finally {
    loading.value = false
  }
}

const resetDetail = () => {
  Object.assign(detail, {
    routingId: 0,
    routingCode: '',
    routingName: '',
    productId: 0,
    productCode: '',
    productName: '',
    routingVersion: '',
    isCurrent: 0,
    isCurrentName: '',
    approveStatus: RouteStatusEnum.DRAFT.value,
    approveStatusName: '',
    totalLaborHours: 0,
    totalMachineHours: 0,
    processCount: 0,
    description: '',
    createBy: '',
    createTime: '',
    updateBy: '',
    updateTime: '',
    remark: '',
    items: [],
  })
}

defineExpose({ loadDetail, resetDetail })
</script>
