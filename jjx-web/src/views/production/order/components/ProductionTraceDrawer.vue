<template>
  <el-drawer
    :model-value="visible"
    title="生产履历"
    size="760px"
    destroy-on-close
    @close="handleClose"
  >
    <div class="prod-trace" v-loading="loading">
      <!-- 顶部订单信息 -->
      <div v-if="trace?.orderHeader" class="trace-header">
        <div class="header-grid">
          <div class="header-item">
            <span class="label">工单号</span>
            <span class="value">{{ trace.orderHeader.orderNo || '-' }}</span>
          </div>
          <div class="header-item">
            <span class="label">产品</span>
            <span class="value">{{ trace.orderHeader.productName || '-' }}</span>
          </div>
          <div class="header-item">
            <span class="label">计划数量</span>
            <span class="value">{{ fmtNum(trace.orderHeader.plannedQuantity) }}</span>
          </div>
          <div class="header-item">
            <span class="label">订单状态</span>
            <el-tag size="small" :type="statusTagType(trace.orderHeader.orderStatus)">
              {{ trace.orderHeader.orderStatusDesc || '-' }}
            </el-tag>
          </div>
          <div class="header-item">
            <span class="label">开始时间</span>
            <span class="value">{{ fmtTime(trace.orderHeader.actualStartTime) }}</span>
          </div>
          <div class="header-item">
            <span class="label">完成时间</span>
            <span class="value">{{ fmtTime(trace.orderHeader.actualEndTime) }}</span>
          </div>
        </div>
      </div>

      <!-- 筛选区 -->
      <div v-if="events.length > 0" class="trace-filters">
        <div class="filter-row">
          <span class="filter-label">分类</span>
          <el-radio-group v-model="category" size="small" @change="handleFilterChange">
            <el-radio-button value="">全部</el-radio-button>
            <el-radio-button value="EXECUTION">生产执行</el-radio-button>
            <el-radio-button value="WORK_REPORT">报工</el-radio-button>
            <el-radio-button value="QUALITY">质量</el-radio-button>
          </el-radio-group>
        </div>
        <div v-if="executionOptions.length > 1" class="filter-row">
          <span class="filter-label">工序</span>
          <el-select v-model="executionId" size="small" clearable placeholder="全部工序" style="width: 200px" @change="handleFilterChange">
            <el-option v-for="opt in executionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
      </div>

      <!-- 错误提示 -->
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" show-icon :closable="false" class="trace-error" />

      <!-- 空 Timeline -->
      <el-empty v-if="!loading && !errorMsg && events.length === 0" description="暂无生产履历" />

      <!-- Timeline -->
      <el-timeline v-if="!loading && !errorMsg && filteredEvents.length > 0" class="trace-timeline">
        <el-timeline-item
          v-for="(ev, idx) in filteredEvents"
          :key="ev.sourceType + '-' + ev.sourceId + '-' + ev.eventType + '-' + idx"
          :type="eventTagType(ev.eventType)"
          :hollow="false"
          :timestamp="fmtTime(ev.eventTime)"
          placement="top"
        >
          <div class="event-card">
            <div class="event-head">
              <span class="event-title">{{ eventLabel(ev.eventType) }}</span>
              <el-tag size="small" :type="eventTagType(ev.eventType)">{{ eventCategoryLabel(ev) }}</el-tag>
            </div>
            <div class="event-body">
              <div class="event-title-line">{{ ev.title }}</div>
              <div v-if="ev.description" class="event-desc">{{ ev.description }}</div>
              <div class="event-meta">
                <span v-if="ev.actorName" class="meta-item">操作人：{{ ev.actorName }}</span>
                <span v-if="ev.status" class="meta-item">
                  状态：
                  <span :class="statusClass(ev.status)">{{ statusLabel(ev) }}</span>
                </span>
                <span class="meta-item source">来源：{{ ev.sourceType }}</span>
              </div>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { productionTraceApi, TraceEventType } from '@/api/production/trace'
import type { OrderTraceVO, TraceEventVO } from '@/api/production/trace'

const props = defineProps<{
  visible: boolean
  orderId: number | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()

const loading = ref(false)
const errorMsg = ref('')
const trace = ref<OrderTraceVO | null>(null)
const category = ref('')
const executionId = ref<number | undefined>(undefined)

/** 12 个 eventType → 中文（与后端 TraceEventType 一一对应，前端不新增类型） */
const EVENT_LABEL: Record<string, string> = {
  [TraceEventType.ORDER_CREATED]: '订单创建',
  [TraceEventType.ORDER_STARTED]: '订单开始',
  [TraceEventType.ORDER_COMPLETED]: '订单完成',
  [TraceEventType.EXECUTION_STARTED]: '工序开始',
  [TraceEventType.EXECUTION_COMPLETED]: '工序完成',
  [TraceEventType.WORK_REPORT_SUBMITTED]: '生产报工',
  [TraceEventType.WORK_REPORT_CANCELLED]: '撤销报工',
  [TraceEventType.QUALITY_CREATED]: '创建质检',
  [TraceEventType.QUALITY_PASSED]: '质检通过',
  [TraceEventType.QUALITY_FAILED]: '质检不通过',
}

/** 事件类别中文 */
const CATEGORY_LABEL: Record<string, string> = {
  ORDER: '订单',
  EXECUTION: '生产执行',
  WORK_REPORT: '报工',
  QUALITY: '质量',
}

/** 状态视觉：轻量 4 类颜色，不做 16 套 */
function eventTagType(eventType: string): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  switch (eventType) {
    case TraceEventType.ORDER_COMPLETED:
    case TraceEventType.EXECUTION_COMPLETED:
    case TraceEventType.QUALITY_PASSED:
      return 'success'
    case TraceEventType.QUALITY_FAILED:
    case TraceEventType.WORK_REPORT_CANCELLED:
      return 'danger'
    case TraceEventType.ORDER_CREATED:
    case TraceEventType.QUALITY_CREATED:
      return 'info'
    default:
      return 'primary'
  }
}

function eventLabel(eventType: string): string {
  return EVENT_LABEL[eventType] || eventType
}

function eventCategoryLabel(ev: TraceEventVO): string {
  return CATEGORY_LABEL[ev.sourceType] || ev.sourceType
}

function statusClass(status: string): string {
  const up = (status || '').toUpperCase()
  if (['PASS', 'COMPLETED', 'SUBMITTED', 'SUCCESS'].includes(up)) return 'st-success'
  if (['FAIL', 'FAILED', 'CANCELLED', 'REJECT', 'REJECTED'].includes(up)) return 'st-danger'
  return 'st-normal'
}

function statusLabel(ev: TraceEventVO): string {
  const s = ev.status || ''
  const up = s.toUpperCase()
  if (up === 'PASS') return '通过'
  if (up === 'FAIL' || up === 'FAILED') return '不通过'
  if (up === 'COMPLETED') return '已完成'
  if (up === 'CANCELLED') return '已撤销'
  if (up === 'SUBMITTED') return '已提交'
  if (up === 'REJECT' || up === 'REJECTED') return '已退回'
  if (up === 'PENDING') return '待检'
  if (up === 'DRAFT') return '草稿'
  if (up === 'IN_PROGRESS') return '进行中'
  if (up === 'EXECUTING') return '执行中'
  return s || '-'
}

function statusTagType(status?: number | null): 'info' | 'primary' | 'success' | 'warning' | 'danger' {
  const map: Record<number, 'info' | 'primary' | 'success' | 'warning' | 'danger'> = {
    8: 'success', // 已完成
    9: 'danger', // 已取消
    6: 'primary', // 进行中
  }
  return map[status ?? -1] || 'info'
}

function fmtNum(v?: number | null): string {
  if (v == null) return '-'
  return Number(v).toLocaleString()
}

function fmtTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

/** 全部事件（未筛选） */
const events = computed(() => trace.value?.events || [])

/** 工序筛选选项：从事件中提取 executionId（不去请求工艺路线） */
const executionOptions = computed(() => {
  const map = new Map<number, string>()
  for (const ev of events.value) {
    if (ev.executionId == null) continue
    if (!map.has(ev.executionId)) {
      // 工序名：优先用事件 title 中出现的名称；否则"工序 N"
      map.set(ev.executionId, `工序 ${ev.executionId}`)
    }
  }
  return [...map.entries()].map(([value, label]) => ({ value, label }))
})

/** 前端筛选（分类走 API category 参数；工序本地筛 executionId，避免多次请求） */
const filteredEvents = computed(() => {
  if (!executionId.value) return events.value
  return events.value.filter((ev) => ev.executionId === executionId.value)
})

function handleFilterChange() {
  // 分类变化时重新请求（使用 P4-B category 参数）
  loadTrace()
}

async function loadTrace() {
  if (!props.orderId) return
  loading.value = true
  errorMsg.value = ''
  try {
    const params: { category?: string; executionId?: number } = {}
    if (category.value) params.category = category.value
    const res = await productionTraceApi.getOrderTrace(props.orderId, params)
    if (res.code === 200) {
      trace.value = res.data
      // 工序筛选项基于全量事件；分类过滤后 executionId 可能消失，重置
      if (executionId.value && !trace.value?.events?.some((e) => e.executionId === executionId.value)) {
        executionId.value = undefined
      }
    } else {
      errorMsg.value = res.msg || '加载生产履历失败'
    }
  } catch (e: any) {
    errorMsg.value = e?.msg || e?.message || '加载生产履历失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
}

watch(
  () => props.visible,
  (v) => {
    if (v) {
      category.value = ''
      executionId.value = undefined
      trace.value = null
      loadTrace()
    }
  }
)
</script>

<style scoped>
.prod-trace {
  min-height: 200px;
}

.trace-header {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 12px 14px;
  margin-bottom: 12px;
}

.header-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px 16px;
}

.header-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.header-item .label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.header-item .value {
  font-size: 13px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}

.trace-filters {
  margin-bottom: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.filter-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.trace-error {
  margin-bottom: 12px;
}

.trace-timeline {
  padding-left: 4px;
}

.event-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  padding: 8px 12px;
  background: #fff;
}

.event-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.event-title {
  font-weight: 600;
  font-size: 13px;
}

.event-title-line {
  font-size: 13px;
  color: var(--el-text-color-primary);
  margin-bottom: 2px;
}

.event-desc {
  font-size: 12px;
  color: var(--el-text-color-regular);
  margin-bottom: 4px;
}

.event-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.meta-item.source {
  margin-left: auto;
}

.st-success {
  color: var(--el-color-success);
  font-weight: 600;
}

.st-danger {
  color: var(--el-color-danger);
  font-weight: 600;
}

.st-normal {
  color: var(--el-text-color-regular);
}
</style>
