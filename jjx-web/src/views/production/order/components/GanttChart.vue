<template>
  <div class="gantt-chart" ref="chartRef">
    <!-- 工具栏 -->
    <div class="gantt-toolbar">
      <div class="gantt-toolbar-left">
        <el-date-picker
          v-model="viewRange"
          type="daterange"
          range-separator="至"
          start-placeholder="视图起始"
          end-placeholder="视图结束"
          value-format="YYYY-MM-DD"
          style="width: 260px"
          @change="loadData"
        />
        <el-button @click="zoomIn" :disabled="zoomLevel >= 3">🔍+</el-button>
        <el-button @click="zoomOut" :disabled="zoomLevel <= 0">🔍-</el-button>
        <el-button @click="scrollToToday">📅 今天</el-button>
      </div>
      <div class="gantt-toolbar-right">
        <span style="color:#94a3b8;font-size:13px;margin-right:8px">图例:</span>
        <span class="legend-item"><span class="legend-dot" style="background:#3b82f6"></span>生产计划</span>
        <span class="legend-item"><span class="legend-dot" style="background:#10b981"></span>生产工单</span>
        <span class="legend-item"><span class="legend-dot" style="background:#ef4444"></span>已超期</span>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="gantt-loading">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <!-- 甘特图主体 -->
    <div v-else class="gantt-body" ref="bodyRef">
      <!-- 头部：时间刻度 -->
      <div class="gantt-header" ref="headerRef">
        <div class="gantt-header-left">
          <div class="gantt-label-cell gantt-label-header">生产单号 / 产品</div>
        </div>
        <div class="gantt-header-right" ref="timelineRef">
          <div class="gantt-timeline-row">
            <div
              v-for="day in timelineDays"
              :key="day.date"
              class="gantt-day-header"
              :class="{ 'gantt-weekend': day.isWeekend, 'gantt-today': day.isToday }"
              :style="{ width: dayWidth + 'px' }"
            >
              <div class="gantt-day-month">{{ day.month }}</div>
              <div class="gantt-day-date">{{ day.date }}</div>
              <div class="gantt-day-weekday">{{ day.weekday }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 滚动区域 -->
      <div class="gantt-scroll" @scroll="syncScroll" ref="scrollRef">
        <div class="gantt-rows" :style="{ minWidth: timelineWidth + 'px' }">
          <!-- 空状态 -->
          <div v-if="orders.length === 0" class="gantt-empty">
            暂无生产工单数据，请先创建生产计划或工单
          </div>

          <!-- 每行一个工单 -->
          <div
            v-for="(order, idx) in orders"
            :key="order.orderId"
            class="gantt-row"
            :class="{ 'gantt-row-alt': idx % 2 === 1 }"
          >
            <!-- 左侧标签 -->
            <div class="gantt-label">
              <div class="gantt-label-title" :title="order.orderNo" @click="$emit('view', order)">
                <span class="gantt-label-badge" :class="'badge-' + (order.orderType === 'PLAN' ? 'plan' : 'work')">
                  {{ order.orderType === 'PLAN' ? '计划' : '工单' }}
                </span>
                {{ order.orderNo }}
              </div>
              <div class="gantt-label-sub">
                {{ order.productName || order.productCode || '-' }}
                <template v-if="order.plannedQuantity">
                  | {{ order.plannedQuantity }}{{ order.productUnit || '件' }}
                </template>
              </div>
            </div>

            <!-- 右侧甘特条 -->
            <div class="gantt-bar-area" :style="{ position: 'relative', height: '100%' }">
              <!-- 网格背景 -->
              <div
                v-for="day in timelineDays"
                :key="'grid-' + day.date"
                class="gantt-grid-cell"
                :class="{ 'gantt-weekend': day.isWeekend }"
                :style="{ left: day.left + 'px', width: dayWidth + 'px' }"
              ></div>

              <!-- 今天线 -->
              <div
                v-if="todayOffset >= 0"
                class="gantt-today-line"
                :style="{ left: todayOffset + 'px' }"
              ></div>

              <!-- 甘特条 -->
              <div
                v-if="order.barLeft !== undefined"
                class="gantt-bar"
                :class="'gantt-bar-' + (order.orderType === 'PLAN' ? 'plan' : 'work')"
                :style="{
                  left: (order.barLeft != null ? order.barLeft : 0) + 'px',
                  width: Math.max(order.barWidth != null ? order.barWidth : 0, 4) + 'px',
                }"
                :title="order.orderNo + ': ' + order.planStartDate + ' ~ ' + order.planEndDate"
                @click="$emit('view', order)"
              >
                <span class="gantt-bar-text" v-if="(order.barWidth != null ? order.barWidth : 0) > 60">
                  {{ order.productName || order.orderNo }}
                </span>
              </div>

              <!-- 超期标记 -->
              <span
                v-if="order.isOverdue"
                class="gantt-overdue-badge"
                :style="{ left: Math.min((order.barLeft != null ? order.barLeft : 0) + (order.barWidth != null ? order.barWidth : 0) + 4, timelinePixels - 30) + 'px' }"
                title="已超期"
              >⚠️</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部统计 -->
    <div class="gantt-footer" v-if="!loading">
      <span>共 {{ orders.length }} 条</span>
      <span style="margin-left:16px">
        计划: {{ planCount }} | 工单: {{ workCount }}
      </span>
      <span style="margin-left:16px;color:#ef4444" v-if="overdueCount > 0">
        超期: {{ overdueCount }}
      </span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { getProductionOrderList } from '@/api/production/order'

interface GanttOrder {
  orderId: number
  orderNo: string
  orderType: string
  productName: string
  productCode: string
  productUnit: string
  plannedQuantity: number
  completedQuantity: number
  planStartDate: string
  planEndDate: string
  orderStatus: number
  orderStatusDesc: string
  priority: string
  barLeft?: number
  barWidth?: number
  isOverdue?: boolean
}

interface TimelineDay {
  date: string
  day: number
  month: string
  weekday: string
  isWeekend: boolean
  isToday: boolean
  left: number
}

defineEmits<{
  (e: 'view', order: GanttOrder): void
}>()

const props = withDefaults(defineProps<{
  startDate?: string
  endDate?: string
  orderType?: string
}>(), {})

// ===== 状态 =====
const loading = ref(false)
const orders = ref<GanttOrder[]>([])
const zoomLevel = ref(1)

// DOM refs
const chartRef = ref<HTMLElement>()
const bodyRef = ref<HTMLElement>()
const headerRef = ref<HTMLElement>()
const scrollRef = ref<HTMLElement>()
const timelineRef = ref<HTMLElement>()

// 视图日期范围（默认前后30天）
const today = new Date()
const viewRange = ref<string[]>([
  formatDate(new Date(today.getTime() - 30 * 86400000)),
  formatDate(new Date(today.getTime() + 30 * 86400000)),
])

// ===== 计算属性 =====
const dayWidth = computed(() => 28 + zoomLevel.value * 8) // 28~52px
const timelinePixels = computed(() => timelineDays.value.length * dayWidth.value)
const timelineWidth = computed(() => timelinePixels.value + 'px') // 留给左侧标签

const timelineDays = computed(() => {
  const days: TimelineDay[] = []
  if (!viewRange.value || viewRange.value.length < 2) return days

  const start = new Date(viewRange.value[0])
  const end = new Date(viewRange.value[1])
  const now = new Date()

  let left = 0
  for (let d = new Date(start); d <= end; d.setDate(d.getDate() + 1)) {
    const dateStr = formatDate(d)
    const isWeekend = d.getDay() === 0 || d.getDay() === 6
    const isToday = dateStr === formatDate(now)
    days.push({
      date: dateStr,
      day: d.getDate(),
      month: d.getDate() === 1 ? (d.getMonth() + 1) + '月' : '',
      weekday: ['日', '一', '二', '三', '四', '五', '六'][d.getDay()],
      isWeekend,
      isToday,
      left,
    })
    left += dayWidth.value
  }
  return days
})

const todayOffset = computed(() => {
  const now = formatDate(new Date())
  const day = timelineDays.value.find(d => d.date === now)
  return day ? day.left : -1
})

const planCount = computed(() => orders.value.filter(o => o.orderType === 'PLAN').length)
const workCount = computed(() => orders.value.filter(o => o.orderType === 'WORK_ORDER').length)
const overdueCount = computed(() => orders.value.filter(o => o.isOverdue).length)

// ===== 方法 =====
function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function parseDate(s: string): Date | null {
  if (!s) return null
  const parts = s.split('-')
  if (parts.length !== 3) return null
  return new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]))
}

/** 将日期转换为甘特图中的像素位置 */
function dateToPixel(dateStr: string): number | undefined {
  if (!dateStr || timelineDays.value.length === 0) return undefined
  const target = parseDate(dateStr)
  if (!target) return undefined
  const start = parseDate(viewRange.value[0])
  if (!start) return undefined
  const diff = (target.getTime() - start.getTime()) / 86400000
  if (diff < -1 || diff > timelineDays.value.length + 1) return undefined
  return Math.max(0, diff * dayWidth.value)
}

/** 加载数据 */
async function loadData() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      pageSize: 200,
    }
    if (viewRange.value && viewRange.value.length === 2) {
      params.planStartDateFrom = viewRange.value[0]
      params.planEndDateTo = viewRange.value[1]
    }
    if (props.orderType) {
      params.orderType = props.orderType
    }

    const res = await getProductionOrderList(params as any)
    let items = res?.data?.records || []
    if (!Array.isArray(items)) items = []

    const todayStr = formatDate(new Date())
    orders.value = items.map((o: any): GanttOrder => {
      const barLeft = dateToPixel(o.planStartDate)
      const barEnd = dateToPixel(o.planEndDate)
      const barWidth = barLeft !== undefined && barEnd !== undefined ? barEnd - barLeft + dayWidth.value : 0
      return {
        orderId: o.orderId,
        orderNo: o.orderNo,
        orderType: o.orderType || 'WORK_ORDER',
        productName: o.productName || '',
        productCode: o.productCode || '',
        productUnit: o.productUnit || '件',
        plannedQuantity: o.plannedQuantity || 0,
        completedQuantity: o.completedQuantity || 0,
        planStartDate: o.planStartDate || '',
        planEndDate: o.planEndDate || '',
        orderStatus: o.orderStatus,
        orderStatusDesc: o.orderStatusDesc || '',
        priority: o.priority || 'MEDIUM',
        barLeft: barLeft !== undefined ? barLeft : undefined,
        barWidth: barWidth,
        isOverdue: o.planEndDate ? o.planEndDate < todayStr && o.orderStatus < 8 : false,
      }
    })
  } catch (error) {
    console.error('获取甘特图数据失败:', error)
    orders.value = []
  } finally {
    loading.value = false
  }
}

/** 同步滚动（头和体联动） */
function syncScroll(e: Event) {
  const target = e.target as HTMLElement
  if (headerRef.value) {
    const right = headerRef.value.querySelector('.gantt-header-right') as HTMLElement
    if (right) right.style.transform = `translateX(-${target.scrollLeft}px)`
  }
}

/** 缩放 */
function zoomIn() {
  if (zoomLevel.value < 3) zoomLevel.value++
}
function zoomOut() {
  if (zoomLevel.value > 0) zoomLevel.value--
}

/** 跳到今天 */
function scrollToToday() {
  const now = new Date()
  viewRange.value = [
    formatDate(new Date(now.getTime() - 15 * 86400000)),
    formatDate(new Date(now.getTime() + 15 * 86400000)),
  ]
}

watch(() => [props.startDate, props.endDate, props.orderType], () => loadData())

onMounted(() => loadData())
</script>

<style scoped>
.gantt-chart {
  background: #1e293b;
  border: 1px solid #334155;
  border-radius: 12px;
  overflow: hidden;
}

.gantt-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #334155;
  flex-wrap: wrap;
  gap: 8px;
}
.gantt-toolbar-left { display: flex; align-items: center; gap: 8px; }
.gantt-toolbar-right { display: flex; align-items: center; gap: 12px; }
.legend-item { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #94a3b8; }
.legend-dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }

.gantt-loading {
  display: flex; align-items: center; justify-content: center;
  gap: 8px; padding: 60px; color: #94a3b8;
}

.gantt-body {
  position: relative;
}

.gantt-header {
  display: flex;
  border-bottom: 1px solid #334155;
  position: sticky;
  top: 0;
  z-index: 10;
  background: #1e293b;
}
.gantt-header-left {
  flex: 0 0 240px;
  border-right: 1px solid #334155;
}
.gantt-label-header {
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 600;
  color: #94a3b8;
}
.gantt-header-right {
  overflow: hidden;
  transition: none;
}
.gantt-timeline-row {
  display: flex;
}
.gantt-day-header {
  flex-shrink: 0;
  text-align: center;
  padding: 4px 0;
  border-right: 1px solid #1e293b;
  background: #1e293b;
}
.gantt-day-header.gantt-weekend { background: #162032; }
.gantt-day-header.gantt-today {
  background: #1e3a5f;
  border-bottom: 2px solid #3b82f6;
}
.gantt-day-month { font-size: 10px; color: #64748b; }
.gantt-day-date { font-size: 13px; font-weight: 700; color: #e2e8f0; }
.gantt-day-weekday { font-size: 10px; color: #64748b; }

.gantt-scroll {
  overflow-x: auto;
  overflow-y: auto;
  max-height: calc(100vh - 350px);
}
.gantt-scroll::-webkit-scrollbar { height: 8px; width: 6px; }
.gantt-scroll::-webkit-scrollbar-thumb { background: #475569; border-radius: 4px; }

.gantt-rows {
  min-width: 100%;
}

.gantt-empty {
  padding: 60px 24px;
  text-align: center;
  color: #64748b;
  font-size: 14px;
}

.gantt-row {
  display: flex;
  height: 48px;
  border-bottom: 1px solid #1e293b;
  position: relative;
}
.gantt-row-alt { background: rgba(255,255,255,.02); }

.gantt-label {
  flex: 0 0 240px;
  padding: 4px 12px;
  border-right: 1px solid #334155;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}
.gantt-label-title {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.gantt-label-title:hover { color: #3b82f6; }
.gantt-label-sub {
  font-size: 11px;
  color: #64748b;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.gantt-label-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  font-weight: 600;
  flex-shrink: 0;
}
.badge-plan { background: rgba(59,130,246,.2); color: #60a5fa; }
.badge-work { background: rgba(16,185,129,.2); color: #34d399; }

.gantt-bar-area {
  flex: 1;
  position: relative;
  overflow: hidden;
}

.gantt-grid-cell {
  position: absolute;
  top: 0;
  bottom: 0;
  border-right: 1px solid rgba(51,65,85,.3);
  pointer-events: none;
}
.gantt-grid-cell.gantt-weekend {
  background: rgba(255,255,255,.02);
}

.gantt-today-line {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #3b82f6;
  z-index: 5;
  pointer-events: none;
}
.gantt-today-line::after {
  content: '';
  position: absolute;
  top: 0;
  left: -4px;
  width: 10px;
  height: 10px;
  background: #3b82f6;
  border-radius: 50%;
}

.gantt-bar {
  position: absolute;
  top: 10px;
  height: 28px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  padding: 0 6px;
  z-index: 3;
  transition: opacity .15s;
  min-width: 4px;
}
.gantt-bar:hover { opacity: .85; }
.gantt-bar-plan {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border: 1px solid #60a5fa;
}
.gantt-bar-work {
  background: linear-gradient(135deg, #059669, #10b981);
  border: 1px solid #34d399;
}
.gantt-bar-text {
  font-size: 11px;
  color: #fff;
  font-weight: 500;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.gantt-overdue-badge {
  position: absolute;
  top: 12px;
  font-size: 14px;
  z-index: 4;
  cursor: help;
}

.gantt-footer {
  padding: 10px 16px;
  border-top: 1px solid #334155;
  font-size: 13px;
  color: #94a3b8;
}
</style>
