<template>
  <div class="m-order">
    <!-- 工单信息（扫码/输入定位后显示） -->
    <div v-if="order" class="m-order-card">
      <div class="m-order-card-title">{{ order.productName }}</div>
      <div class="m-order-card-meta">
        <span>单号 {{ order.orderNo }} · 规格 {{ order.productSpec || '-' }}</span>
        <span>计划 {{ fmtQty(order.plannedQuantity) }} {{ order.productUnit || '' }} · {{ orderStatusLabel }}</span>
      </div>
    </div>

    <!-- 状态筛选 + 刷新 -->
    <div class="m-filter">
      <div class="m-chips">
        <span
          v-for="f in filters"
          :key="f.value"
          class="m-chip"
          :class="{ active: statusFilter === f.value }"
          @click="statusFilter = f.value"
          >{{ f.label }}<i v-if="f.count != null" class="m-chip-count">{{ f.count }}</i></span
        >
      </div>
      <button class="m-refresh" :disabled="loading" @click="loadData">⟳</button>
    </div>

    <!-- 任务列表 -->
    <div v-if="filteredExecs.length" class="m-list">
      <div v-for="ex in filteredExecs" :key="ex.executionId" class="m-exec-card">
        <div class="m-exec-head">
          <span class="m-exec-process">{{ ex.processName || '未命名工序' }}</span>
          <span class="m-tag" :class="'s-' + (Number(ex.executionStatus) || 0)">{{
            execStatusLabel(ex.executionStatus)
          }}</span>
        </div>
        <div v-if="!orderNo && ex.orderNo" class="m-exec-order">🏷 {{ ex.orderNo }}</div>
        <div class="m-exec-qty">
          责任 <b>{{ fmtQty(ex.myResponsibilityQuantity) }}</b> · 已报
          <b>{{ fmtQty(ex.myCompletedQuantity) }}</b>
          <span v-if="Number(ex.myPendingReviewQuantity || 0) > 0" class="pending"
            >· 待审 {{ fmtQty(ex.myPendingReviewQuantity) }}</span
          >
          <span v-if="Number(ex.myProcessableQuantity || 0) > 0" class="can-report"
            >· 可报 {{ fmtQty(ex.myProcessableQuantity) }}</span
          >
        </div>
        <div class="m-progress">
          <div
            class="m-progress-bar"
            :style="{ width: progressPct(ex) }"
            :class="progressDone(ex) ? 'full' : ''"
          ></div>
        </div>
        <div class="m-exec-actions">
          <button v-if="canStart(ex)" class="m-act m-act-primary" :disabled="startingId === ex.executionId" @click="handleStart(ex)">
            ▶ 开始
          </button>
          <button v-if="canPause(ex)" class="m-act m-act-warn" :disabled="pausingId === ex.executionId" @click="handlePause(ex)">
            ⏸ 暂停
          </button>
          <button v-if="canResume(ex)" class="m-act m-act-primary" :disabled="startingId === ex.executionId" @click="handleStart(ex)">
            ▶ 继续
          </button>
          <button v-if="!isProductionWorker && canComplete(ex)" class="m-act m-act-ok" :disabled="completingId === ex.executionId" @click="handleComplete(ex)">
            ✓ 完工
          </button>
          <button v-if="canReport(ex)" class="m-act m-act-primary" @click="goReport(ex)">
            报工
          </button>
        </div>
      </div>
    </div>
    <div v-else-if="!loading" class="m-empty">
      {{ statusFilter === 'all' ? '当前没有我的任务' : '该状态下没有任务' }}
    </div>
  </div>
</template>



<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getProductionOrderByCode } from '@/api/production/order'
import { getMyProductionExecutions } from '@/api/production/task'
import { operationExecutionApi } from '@/api/production/operationExecution'
import type { MyProductionExecution } from '@/types/production/task'
import type { ProductionOrderVO } from '@/types/production/order'
import { ExecutionStatusEnum, ProductionOrderStatusEnum } from '@/enums/production'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const isProductionWorker = computed(() => userStore.getRoles.includes('production:worker'))

const orderNo = computed(() => String(route.query.orderNo || ''))
const loading = ref(false)
const order = ref<ProductionOrderVO | null>(null)
const executions = ref<MyProductionExecution[]>([])
const startingId = ref<number | null>(null)
const pausingId = ref<number | null>(null)
const completingId = ref<number | null>(null)

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

/** 执行中 → 可暂停 */
function canPause(ex: MyProductionExecution): boolean {
  return Number(ex.executionStatus) === ExecutionStatusEnum.EXECUTING.value
}

/** 已暂停 → 可继续 */
function canResume(ex: MyProductionExecution): boolean {
  return Number(ex.executionStatus) === ExecutionStatusEnum.PAUSED.value
}

/** 执行中 → 可完工（后端 assertExecutionCompletable 兜底校验） */
function canComplete(ex: MyProductionExecution): boolean {
  return Number(ex.executionStatus) === ExecutionStatusEnum.EXECUTING.value
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
  const isResume = canResume(ex)
  // 扫码C：可选扫设备码（不一致后端软校验放行并记录），跳过=不校验
  let deviceCode: string | undefined
  try {
    const { value } = await ElMessageBox.prompt(
      ex.equipmentName ? `指定设备：${ex.equipmentName}（${ex.equipmentCode || '-'}）` : '该工序未指定设备',
      `${isResume ? '继续' : '开始'}工序（可选扫设备码）`,
      {
        confirmButtonText: isResume ? '继续' : '开始',
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
    ElMessage.success(isResume ? '工序已继续' : '工序已开始')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || (isResume ? '继续失败' : '开始失败'))
  } finally {
    startingId.value = null
  }
}

async function handlePause(ex: MyProductionExecution) {
  if (!ex.executionId) return
  pausingId.value = ex.executionId
  try {
    await operationExecutionApi.pause(ex.executionId)
    ElMessage.success('工序已暂停')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '暂停失败')
  } finally {
    pausingId.value = null
  }
}

async function handleComplete(ex: MyProductionExecution) {
  if (!ex.executionId) return
  try {
    await ElMessageBox.confirm(
      `确认完成工序「${ex.processName}」？\n后端将校验：子树完成量、无待审批报工、无剩余责任，不满足会拦截。`,
      '完工确认',
      { type: 'warning', confirmButtonText: '确认完工', cancelButtonText: '再想想' },
    )
  } catch {
    return
  }
  completingId.value = ex.executionId
  try {
    await operationExecutionApi.complete(ex.executionId)
    ElMessage.success('工序已完成；若为最后工序将自动生成完工检验')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '完工失败')
  } finally {
    completingId.value = null
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

const statusFilter = ref<'all' | 'todo' | 'doing' | 'done'>('all')

function execBucket(v: number): 'todo' | 'doing' | 'done' {
  if (v === ExecutionStatusEnum.PENDING.value) return 'todo'
  if (v === ExecutionStatusEnum.COMPLETED.value || v === ExecutionStatusEnum.SKIPPED.value) return 'done'
  return 'doing'
}

const filterCounts = computed(() => {
  const c = { all: executions.value.length, todo: 0, doing: 0, done: 0 }
  executions.value.forEach((ex) => {
    c[execBucket(Number(ex.executionStatus))]++
  })
  return c
})

type FilterValue = 'all' | 'todo' | 'doing' | 'done'
const filters = computed<{ value: FilterValue; label: string; count: number }[]>(() => [
  { value: 'all', label: '全部', count: filterCounts.value.all },
  { value: 'todo', label: '待做', count: filterCounts.value.todo },
  { value: 'doing', label: '进行中', count: filterCounts.value.doing },
  { value: 'done', label: '已完成', count: filterCounts.value.done },
])

const filteredExecs = computed(() => {
  if (statusFilter.value === 'all') return executions.value
  return executions.value.filter((ex) => execBucket(Number(ex.executionStatus)) === statusFilter.value)
})

function progressPct(ex: MyProductionExecution): string {
  const total = Number(ex.myResponsibilityQuantity || 0)
  const done = Number(ex.myCompletedQuantity || 0)
  if (!total) return '0%'
  const p = Math.min(100, Math.round((done / total) * 100))
  return p + '%'
}
function progressDone(ex: MyProductionExecution): boolean {
  const total = Number(ex.myResponsibilityQuantity || 0)
  return total > 0 && Number(ex.myCompletedQuantity || 0) >= total
}

async function loadData() {
  loading.value = true
  try {
    // 2026-09-04：支持无工单号直进——显示“我的全部任务”（扫码/输入定位只是筛选特定工单）
    if (orderNo.value) {
      // 1. 工单信息（orderNo → orderId）
      const orderRes: any = await getProductionOrderByCode(orderNo.value)
      order.value = orderRes?.data || null
    }

    // 2. 我的工序执行（无工单号=全量我的任务；带工单号=按工单聚合）
    const params: any = { pageNum: 1, pageSize: 100 }
    if (orderNo.value) params.orderNo = orderNo.value
    const res: any = await getMyProductionExecutions(params)
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
  padding: 12px 12px 70px;
}
.m-order-card {
  background: linear-gradient(135deg, #2b5aa7, #4a7fd4);
  border-radius: 14px;
  padding: 14px 16px;
  margin-bottom: 12px;
  color: #fff;
}
.m-order-card-title {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 6px;
}
.m-order-card-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  opacity: 0.92;
}
.m-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.m-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}
.m-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 16px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(43, 90, 167, 0.06);
}
.m-chip.active {
  background: #2b5aa7;
  color: #fff;
  font-weight: 600;
}
.m-chip-count {
  font-style: normal;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  padding: 0 5px;
  line-height: 15px;
}
.m-chip.active .m-chip-count {
  background: rgba(255, 255, 255, 0.25);
}
.m-refresh {
  flex-shrink: 0;
  width: 34px;
  height: 34px;
  margin-left: 8px;
  border: none;
  border-radius: 50%;
  background: #fff;
  color: #2b5aa7;
  font-size: 16px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(43, 90, 167, 0.1);
}
.m-refresh:disabled {
  opacity: 0.5;
}
.m-exec-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 2px 10px rgba(43, 90, 167, 0.05);
}
.m-exec-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.m-exec-process {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-exec-order {
  font-size: 11px;
  color: #909399;
  margin-bottom: 4px;
  font-family: ui-monospace, monospace;
}
.m-tag {
  flex-shrink: 0;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}
.m-tag.s-0 {
  color: #e6a23c;
  background: #fdf6ec;
}
.m-tag.s-2 {
  color: #2b5aa7;
  background: #ecf3ff;
}
.m-tag.s-4 {
  color: #67c23a;
  background: #f0f9eb;
}
.m-tag.s-3 {
  color: #909399;
  background: #f4f4f5;
}
.m-exec-qty {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}
.m-exec-qty b {
  color: #303133;
  font-size: 15px;
}
.m-exec-qty .pending {
  color: #e6a23c;
}
.m-exec-qty .can-report {
  color: #2b5aa7;
}
.m-progress {
  height: 6px;
  border-radius: 3px;
  background: #ebeef5;
  margin-bottom: 12px;
  overflow: hidden;
}
.m-progress-bar {
  height: 100%;
  border-radius: 3px;
  background: linear-gradient(90deg, #4a7fd4, #2b5aa7);
  transition: width 0.3s;
}
.m-progress-bar.full {
  background: #67c23a;
}
.m-exec-actions {
  display: flex;
  gap: 8px;
}
.m-act {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.m-act-primary {
  background: linear-gradient(135deg, #2b5aa7, #4a7fd4);
  color: #fff;
}
.m-act-ok {
  background: #67c23a;
  color: #fff;
}
.m-act-warn {
  background: #fff;
  color: #e6a23c;
  border: 1px solid #e6a23c;
}
.m-act:disabled {
  opacity: 0.6;
}
.m-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 70px 0;
}
</style>
