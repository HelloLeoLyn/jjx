<template>
  <div class="m-order">
    <header class="m-header">
      <el-button link @click="router.back()">← 返回</el-button>
      <span class="m-header-title">工单 {{ orderNo }}</span>
      <el-button link type="primary" @click="router.push('/m/scan')">扫码</el-button>
    </header>

    <div v-loading="loading" class="m-order-body">
      <!-- 工单信息 -->
      <div v-if="order" class="m-order-card">
        <div class="m-order-card-title">{{ order.productName }}</div>
        <div class="m-order-card-meta">
          <span>规格：{{ order.productSpec || '-' }}</span>
          <span>计划：{{ fmtQty(order.plannedQuantity) }} {{ order.productUnit || '' }}</span>
          <span>状态：{{ orderStatusLabel }}</span>
        </div>
      </div>

      <!-- 我的工序执行 -->
      <template v-if="executions.length">
        <div class="m-section-title">我的工序任务</div>
        <div v-for="ex in executions" :key="ex.executionId" class="m-exec-card">
          <div class="m-exec-head">
            <span class="m-exec-process">{{ ex.processName }}</span>
            <el-tag size="small" :type="execStatusTag(ex.executionStatus)">
              {{ execStatusLabel(ex.executionStatus) }}
            </el-tag>
          </div>
          <div class="m-exec-qty">
            责任 {{ fmtQty(ex.myResponsibilityQuantity) }} · 已报 {{ fmtQty(ex.myCompletedQuantity) }}
            <span v-if="Number(ex.myPendingReviewQuantity || 0) > 0">
              · 待审 {{ fmtQty(ex.myPendingReviewQuantity) }}
            </span>
            <span v-if="Number(ex.myProcessableQuantity || 0) > 0">
              · 可报 {{ fmtQty(ex.myProcessableQuantity) }}
            </span>
          </div>
          <div class="m-exec-actions">
            <el-button
              v-if="canStart(ex)"
              size="small"
              type="primary"
              plain
              :loading="startingId === ex.executionId"
              @click="handleStart(ex)"
            >
              开始
            </el-button>
            <el-button
              v-if="canReport(ex)"
              size="small"
              type="primary"
              @click="goReport(ex)"
            >
              报工
            </el-button>
          </div>
        </div>
      </template>
      <el-empty v-else-if="!loading" description="该工单暂无我的任务" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductionOrderByCode } from '@/api/production/order'
import { getMyProductionExecutions } from '@/api/production/task'
import { operationExecutionApi } from '@/api/production/operationExecution'
import type { MyProductionExecution } from '@/types/production/task'
import type { ProductionOrderVO } from '@/types/production/order'
import { ExecutionStatusEnum, ProductionOrderStatusEnum } from '@/enums/production'

const route = useRoute()
const router = useRouter()

const orderNo = computed(() => String(route.query.orderNo || ''))
const loading = ref(false)
const order = ref<ProductionOrderVO | null>(null)
const executions = ref<MyProductionExecution[]>([])
const startingId = ref<number | null>(null)

const orderStatusLabel = computed(() => {
  if (!order.value) return '-'
  const st = Number(order.value.orderStatus)
  return ProductionOrderStatusEnum.getLabel(st)
})

function fmtQty(v?: number | string | null): string {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

function execStatusLabel(v?: number): string {
  return ExecutionStatusEnum.getLabel(Number(v ?? -1))
}

function execStatusTag(v?: number): any {
  return ExecutionStatusEnum.getTagProps(Number(v ?? -1))?.type || 'info'
}

/** 待执行且工单进行中 → 可开始 */
function canStart(ex: MyProductionExecution): boolean {
  return (
    Number(ex.executionStatus) === ExecutionStatusEnum.PENDING.value &&
    Number(ex.orderStatus) === ProductionOrderStatusEnum.IN_PROGRESS.value
  )
}

/** 执行中且有可报额度 → 可报工 */
function canReport(ex: MyProductionExecution): boolean {
  return (
    Number(ex.executionStatus) === ExecutionStatusEnum.EXECUTING.value &&
    Number(ex.myProcessableQuantity || 0) > 0
  )
}

async function handleStart(ex: MyProductionExecution) {
  if (!ex.executionId) return
  // 扫码C：可选扫设备码（不一致后端软校验放行并记录），跳过=不校验
  let deviceCode: string | undefined
  try {
    const { value } = await ElMessageBox.prompt(
      ex.equipmentName ? `指定设备：${ex.equipmentName}（${ex.equipmentCode || '-'}）` : '该工序未指定设备',
      '开始工序（可选扫设备码）',
      {
        confirmButtonText: '开始',
        cancelButtonText: '跳过',
        inputPlaceholder: '扫码枪扫设备码，或直接点开始',
        inputValidator: (v: string) => (v && v.trim() ? true : true), // 可空，跳过校验
        closeOnClickModal: false,
      },
    )
    deviceCode = value?.trim() || undefined
  } catch {
    return // 用户取消
  }
  startingId.value = ex.executionId
  try {
    await operationExecutionApi.start(ex.executionId, deviceCode)
    ElMessage.success('工序已开始')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '开始失败')
  } finally {
    startingId.value = null
  }
}

function goReport(ex: MyProductionExecution) {
  router.push({
    path: '/m/report',
    query: {
      executionId: ex.executionId,
      orderNo: ex.orderNo || orderNo.value,
      processName: ex.processName || '',
    },
  })
}

async function loadData() {
  if (!orderNo.value) return
  loading.value = true
  try {
    // 1. 工单信息（orderNo → orderId）
    const orderRes: any = await getProductionOrderByCode(orderNo.value)
    order.value = orderRes?.data || null

    // 2. 我的工序执行（按工单号聚合）
    const res: any = await getMyProductionExecutions({
      orderNo: orderNo.value,
      pageNum: 1,
      pageSize: 100,
    })
    executions.value = res?.data?.records || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

loadData()
</script>

<style scoped>
.m-order {
  min-height: 100vh;
  background: #f5f7fa;
}
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 10;
}
.m-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-order-body {
  padding: 12px;
}
.m-order-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
}
.m-order-card-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
}
.m-order-card-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 13px;
  color: #606266;
}
.m-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin: 4px 0 10px;
}
.m-exec-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-exec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.m-exec-process {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-exec-qty {
  font-size: 13px;
  color: #606266;
  margin-bottom: 10px;
}
.m-exec-actions {
  display: flex;
  gap: 10px;
}
</style>
