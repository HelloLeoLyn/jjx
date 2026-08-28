<template>
  <el-drawer
    v-model="visible"
    title="业务事件流"
    size="880px"
    @open="loadTrace"
    @close="handleClose"
  >
    <div class="toolbar">
      <span class="toolbar-hint">按时间正序展示业务操作</span>
      <el-switch v-model="showTechnical" active-text="技术详情" />
    </div>

    <div v-if="loading" class="loading-box">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <div>加载中...</div>
    </div>

    <el-empty v-else-if="events.length === 0" description="暂无操作日志" />

    <template v-else>
      <el-table
        :data="events"
        size="small"
        border
        highlight-current-row
        row-key="eventId"
        class="event-table"
      >
        <el-table-column label="时间" width="165">
          <template #default="scope">{{ formatTime(scope.row.time) }}</template>
        </el-table-column>
        <el-table-column label="业务状态" width="110">
          <template #default="scope">
            {{ formatBizStatus(scope.row.bizStatus, scope.row.bizType) }}
          </template>
        </el-table-column>
        <el-table-column label="业务模块" width="100">
          <template #default="scope">
            {{ formatBizModule(scope.row.module, scope.row.bizType) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="220">
          <template #default="scope">
            <!-- 有详情（变更/意见/附件）的操作才可点击，primary 样式突出；普通操作不可点 -->
            <el-link
              v-if="hasDetail(scope.row)"
              type="primary"
              :underline="false"
              @click.stop="selectEvent(scope.row)"
              >{{ scope.row.actionTitle || '-' }}</el-link
            >
            <span v-else>{{ scope.row.actionTitle || '-' }}</span>
            <!-- 附件徽标：该事件含附件时直接可见（1199） -->
            <el-tag
              v-if="(scope.row.attachments?.length || 0) > 0"
              size="small"
              type="primary"
              class="att-badge"
            >📎 {{ scope.row.attachments.length }}</el-tag>
            <el-tag
              v-if="scope.row.changes?.length"
              size="small"
              type="warning"
              class="change-badge"
            >
              {{ scope.row.changes.length }}项变更
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operatorName" label="操作人" width="100">
          <template #default="scope">{{ scope.row.operatorName || '-' }}</template>
        </el-table-column>
        <el-table-column label="结果" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.result === 1 ? 'success' : 'danger'" size="small">
              {{ scope.row.result === 1 ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <el-pagination
        v-if="serverMode && total > pageSize"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        class="pagination"
        @current-change="loadEvents"
      />

      <section v-if="selectedEvent" class="event-detail">
        <header class="detail-header">
          <div>
            <strong>{{ selectedEvent.actionTitle || '-' }}</strong>
            <el-tag v-if="selectedEvent.roundNo" size="small" class="round-tag">
              第 {{ selectedEvent.roundNo }} 轮
            </el-tag>
          </div>
          <div class="detail-meta">
            {{ selectedEvent.operatorName || '-' }} · {{ formatTime(selectedEvent.time) }}
          </div>
        </header>

        <div v-if="selectedEvent.changes?.length" class="detail-block">
          <div class="detail-title">✎ 变更内容</div>
          <ul>
            <li v-for="(change, index) in selectedEvent.changes" :key="index">{{ change }}</li>
          </ul>
        </div>

        <div v-if="reviewComment" class="detail-block">
          <div class="detail-title">💬 {{ isRejected ? '驳回原因' : '审核意见' }}</div>
          <div class="review-comment" :class="{ rejected: isRejected }">
            {{ reviewComment }}
          </div>
        </div>

        <div v-if="detailAttachments.length" class="detail-block">
          <div class="detail-title">📎 附件</div>
          <el-link
            v-for="attachment in detailAttachments"
            :key="attachment.id"
            type="primary"
            :href="downloadUrl(attachment.id)"
            target="_blank"
            :underline="false"
            class="attachment-link"
          >
            {{ attachment.fileName || `附件${attachment.id}` }} <el-icon><Download /></el-icon>
          </el-link>
        </div>

        <div v-if="showTechnical" class="technical-detail">
          <div>traceId：{{ selectedEvent.traceId || traceId || '-' }}</div>
          <div>模块：{{ selectedEvent.module || module || '-' }}</div>
          <div>业务类型：{{ selectedEvent.bizType || bizType || '-' }}</div>
        </div>
      </section>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Download, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { attachmentApi } from '@/api/system/attachment'
import {
  InquiryStatusEnum,
  QuotationStatusEnum,
  SalesOrderStatusEnum,
  SampleOrderStatusEnum,
} from '@/enums/sales'
import { ExecutionStatusEnum, ProductionOrderStatusEnum } from '@/enums/production'
import { PurchaseOrderStatusEnum } from '@/enums/purchase/order'

interface TraceAttachment {
  id: number
  fileName?: string
}

interface ReviewHistory {
  roundNo?: number
  actionCode?: string
  actionName?: string
  fromStatus?: string | number | null
  toStatus?: string | number | null
  operatorName?: string
  comment?: string
  attachmentIds?: string
  createTime?: string
}

interface TraceEvent {
  eventId: string
  time?: string
  bizStatus?: number
  actionTitle?: string
  operatorName?: string
  result?: number
  changes?: string[]
  attachments?: TraceAttachment[]
  reviewHistory?: ReviewHistory[]
  roundNo?: number
  comment?: string
  traceId?: string
  module?: string
  bizType?: string
  businessType?: number
  actionCode?: string
}

const props = defineProps<{
  traceId: string
  bizType?: string
  bizId?: string
  module?: string
  modelValue: boolean
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const visible = ref(false)
const loading = ref(false)
const events = ref<TraceEvent[]>([])
const selectedEvent = ref<TraceEvent | null>(null)
const showTechnical = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)
const total = ref(0)
const serverMode = computed(() => Boolean(props.bizType && props.bizId))

watch(() => props.modelValue, (value) => { visible.value = value })
watch(() => props.traceId, () => { if (props.modelValue) resetAndLoad() })
watch(() => props.bizId, () => { if (props.modelValue) resetAndLoad() })

function statusEnumForBizType(type?: string): { getLabel: (value: number) => string } | undefined {
  switch (type) {
    case 'inquiry': return InquiryStatusEnum
    case 'quotation': return QuotationStatusEnum
    case 'order':
    case 'sales_order': return SalesOrderStatusEnum
    case 'sample': return SampleOrderStatusEnum
    case 'purchase':
    case 'purchase_order': return PurchaseOrderStatusEnum
    case 'production':
    case 'production_order': return ProductionOrderStatusEnum
    case 'execution':
    case 'production_execution': return ExecutionStatusEnum
    default: return undefined
  }
}

/** 该操作是否有详情（变更/意见/附件）——有才可点击查看 */
function hasDetail(event: TraceEvent): boolean {
  return !!(
    event.changes?.length ||
    event.comment?.trim() ||
    event.attachments?.length
  )
}

const reviewComment = computed(() => selectedEvent.value?.comment?.trim() || '')

const isRejected = computed(() =>
  selectedEvent.value?.actionCode === 'REJECT'
  || selectedEvent.value?.actionCode === 'CUSTOMER_REJECT'
)

const detailAttachments = computed(() => {
  const unique = new Map<number, TraceAttachment>()
  for (const attachment of selectedEvent.value?.attachments || []) {
    unique.set(attachment.id, attachment)
  }
  return [...unique.values()]
})

function handleClose() {
  emit('update:modelValue', false)
}

function resetAndLoad() {
  pageNum.value = 1
  selectedEvent.value = null
  loadTrace()
}

async function loadTrace() {
  if (!props.traceId && !props.bizId) return
  loading.value = true
  try {
    if (serverMode.value) await loadEvents()
    else await loadLegacyTrace()
  } catch {
    events.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function loadEvents() {
  if (!props.bizType || !props.bizId) return
  loading.value = true
  try {
    const response = await request.get('/api/trace/events', {
      params: {
        bizType: props.bizType,
        bizId: props.bizId,
        pageNum: pageNum.value,
        pageSize: pageSize.value,
      },
    })
    const data = (response as any)?.data || {}
    events.value = data.records || []
    total.value = Number(data.total || 0)
    selectedEvent.value = null
  } finally {
    loading.value = false
  }
}

async function loadLegacyTrace() {
  if (!props.traceId) return
  const response = await request.get(`/api/trace/${props.traceId}`)
  const nodes: any[] = (response as any)?.data || []
  const legacyEvents: TraceEvent[] = []
  for (const node of nodes) {
    for (const operation of node.operations || []) {
      legacyEvents.push({
        eventId: `legacy-${operation.id}`,
        time: operation.time,
        bizStatus: operation.bizStatus,
        actionTitle: formatBusinessType(operation.businessType),
        operatorName: operation.operator,
        result: operation.status,
        changes: parseChanges(operation.detail),
        attachments: parseDetailAttachments(operation.detail),
        traceId: props.traceId,
        module: node.module,
        bizType: operation.bizType,
        businessType: operation.businessType,
      })
    }
  }
  legacyEvents.sort((left, right) => String(left.time || '').localeCompare(String(right.time || '')))
  events.value = legacyEvents
  total.value = legacyEvents.length
}

function selectEvent(row: TraceEvent) {
  if (!hasDetail(row)) return
  selectedEvent.value = row
}

function parseChanges(detail?: string | null): string[] {
  if (!detail) return []
  try {
    const parsed = JSON.parse(detail)
    return Array.isArray(parsed?.changes) ? parsed.changes : []
  } catch {
    return []
  }
}

function parseDetailAttachments(detail?: string | null): TraceAttachment[] {
  if (!detail) return []
  try {
    const parsed = JSON.parse(detail)
    return Array.isArray(parsed?.attachments) ? parsed.attachments : []
  } catch {
    return []
  }
}

function parseAttachmentIds(value?: string): number[] {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) return parsed.map(Number).filter(Number.isFinite)
  } catch {
    // 兼容历史逗号分隔格式。
  }
  return value.split(',').map((item) => Number(item.trim())).filter(Number.isFinite)
}

function formatBizStatus(status?: number, type?: string): string {
  if (status == null) return '-'
  const statusEnum = statusEnumForBizType(type)
  if (!statusEnum) return '-'
  const label = statusEnum.getLabel(status)
  return label && label !== '未知' ? label : '-'
}

/** 业务模块列：优先 bizType 映射，module 兜底（1199：业务化名称，不再显示技术模块名） */
const BIZ_MODULE_NAMES: Record<string, string> = {
  order: '销售订单', sales_order: '销售订单',
  quotation: '报价单',
  sample: '样品单', sample_order: '样品单',
  inquiry: '询价单',
  purchase: '采购订单', purchase_order: '采购订单',
  production: '生产', production_order: '生产工单', production_execution: '工序执行',
  quality: '质检', bom: 'BOM', film: '工艺',
  review: '审核', attachment: '附件',
}
function formatBizModule(module?: string, bizType?: string): string {
  const byBizType = bizType ? BIZ_MODULE_NAMES[bizType] : undefined
  if (byBizType) return byBizType
  if (!module) return '-'
  return BIZ_MODULE_NAMES[module] || module
}

function formatReviewStatus(review: ReviewHistory): string {
  const statusEnum = statusEnumForBizType(selectedEvent.value?.bizType)
  const label = (value: string | number | null | undefined) => {
    if (value == null || value === '') return ''
    if (!statusEnum) return String(value)
    const result = statusEnum.getLabel(Number(value))
    return result && result !== '未知' ? result : '-'
  }
  const from = label(review.fromStatus)
  const to = label(review.toStatus)
  return from || to ? `${from || '-'} → ${to || '-'}` : ''
}

function reviewActionLabel(actionCode?: string): string {
  const labels: Record<string, string> = {
    SUBMIT: '提交审核',
    SUBMIT_REVIEW: '提交审核',
    APPROVE: '审核通过',
    REJECT: '审核驳回',
    SEND: '发送报价',
    CONFIRM: '客户确认报价',
    CUSTOMER_CONFIRM: '客户确认报价',
    CUSTOMER_REJECT: '客户拒绝报价',
    CANCEL: '取消',
  }
  return actionCode ? labels[actionCode] || actionCode : '-'
}

function formatBusinessType(code?: number): string {
  const labels: Record<number, string> = {
    1: '创建',
    2: '修改',
    3: '删除',
    4: '导出',
    5: '导入',
    6: '审批',
    7: '登录',
    8: '登出',
    9: '业务操作',
    10: '重置密码',
    11: '转换',
  }
  return code == null ? '-' : labels[code] || '业务操作'
}

function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
}

function formatShortTime(value?: string): string {
  return formatTime(value).slice(11, 16)
}

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-hint,
.detail-meta {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.loading-box {
  padding: 60px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.loading-box div {
  margin-top: 10px;
}
.event-table :deep(.el-table__row) {
  cursor: pointer;
}
.change-badge {
  margin-left: 6px;
}
.pagination {
  justify-content: flex-end;
  margin-top: 12px;
}
.event-detail {
  margin-top: 16px;
  padding: 16px 18px;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
}
.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.round-tag {
  margin-left: 8px;
}
.detail-block {
  margin-top: 14px;
}
.detail-title {
  margin-bottom: 7px;
  font-weight: 600;
}
.detail-block ul {
  margin: 0;
  padding-left: 22px;
}
.detail-block li {
  margin: 4px 0;
}
.review-comment {
  line-height: 1.7;
}
.rejected {
  color: var(--el-color-danger);
  font-weight: 700;
}
.approved {
  color: var(--el-color-success);
  font-weight: 700;
}
.review-row {
  display: grid;
  grid-template-columns: 55px 110px 90px 1fr 24px;
  gap: 8px;
  align-items: center;
  padding: 6px 0;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  font-size: 13px;
}
.attachment-link {
  margin-right: 14px;
}
.technical-detail {
  margin-top: 16px;
  padding-top: 10px;
  border-top: 1px dashed var(--el-border-color);
  color: var(--el-text-color-secondary);
  font-family: monospace;
  font-size: 12px;
  line-height: 1.7;
}
</style>
