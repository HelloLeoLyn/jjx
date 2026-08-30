<template>
  <el-drawer
    v-model="visible"
    title="业务事件流"
    size="880px"
    @open="resetAndLoad"
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
            {{ formatBizStatus(scope.row.bizStatus) }}
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
              underline="never"
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
              >📎 {{ scope.row.attachments.length }}</el-tag
            >
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
        v-if="total > 10"
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
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
          <el-image
            v-for="attachment in imageAttachments"
            :key="attachment.id"
            :src="downloadUrl(attachment.id)"
            :preview-src-list="imagePreviewUrls"
            fit="cover"
            class="attachment-image"
          />
          <el-link
            v-for="attachment in fileAttachments"
            :key="attachment.id"
            type="primary"
            :href="downloadUrl(attachment.id)"
            target="_blank"
            underline="never"
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
import { BusinessTypeEnum } from '@/enums/system/LogEnum'

interface TraceAttachment {
  id: number
  fileName?: string
}

interface TraceDetail {
  changes: string[]
  attachments: TraceAttachment[]
}

/** /api/trace/reviews 返回的审核流水记录 */
interface ReviewHistory {
  flowId?: string
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
  bizStatus?: string
  actionTitle?: string
  operatorName?: string
  result?: number
  /** 以下为前端解析/按需加载后回填的展示字段 */
  changes?: string[]
  attachments?: TraceAttachment[]
  roundNo?: number
  comment?: string
  actionCode?: string
  isReview?: boolean
  traceId?: string
  module?: string
  bizType?: string
  bizId?: string
  businessType?: number
  operUrl?: string
  operParam?: string
  detail?: string
}

const props = defineProps<{
  traceId?: string
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
const pageSize = ref(20)
const total = ref(0)

/** 按需加载缓存：key = bizType:bizId */
const reviewCache = new Map<string, ReviewHistory[]>()

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value
  }
)
watch(
  () => props.traceId,
  () => {
    if (props.modelValue) resetAndLoad()
  }
)
watch(
  () => [props.bizType, props.bizId] as const,
  () => {
    if (props.modelValue) resetAndLoad()
  }
)


/** 该操作是否有详情（变更/审核意见/附件）——有才可点击查看 */
function hasDetail(event: TraceEvent): boolean {
  return !!(event.changes?.length || event.isReview || event.attachments?.length)
}

const reviewComment = computed(() => selectedEvent.value?.comment?.trim() || '')

const isRejected = computed(
  () =>
    selectedEvent.value?.actionCode === 'REJECT' ||
    selectedEvent.value?.actionCode === 'CUSTOMER_REJECT'
)

const detailAttachments = computed(() => {
  const unique = new Map<number, TraceAttachment>()
  for (const attachment of selectedEvent.value?.attachments || []) {
    unique.set(attachment.id, attachment)
  }
  return [...unique.values()]
})

const imageAttachments = computed(() => detailAttachments.value.filter(isImageAttachment))
const fileAttachments = computed(() =>
  detailAttachments.value.filter((attachment) => !isImageAttachment(attachment))
)
const imagePreviewUrls = computed(() =>
  imageAttachments.value.map((attachment) => downloadUrl(attachment.id))
)

function isImageAttachment(attachment: TraceAttachment): boolean {
  return /\.(png|jpe?g|gif|webp|bmp|svg)$/i.test(attachment.fileName || '')
}

function handleClose() {
  emit('update:modelValue', false)
}

function resetAndLoad() {
  pageNum.value = 1
  selectedEvent.value = null
  loadEvents()
}

async function loadEvents() {
  if (!props.traceId && !(props.bizType && props.bizId)) return
  loading.value = true
  try {
    const params: Record<string, any> = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    }
    if (props.traceId) {
      params.traceId = props.traceId
    } else {
      params.bizType = props.bizType
      params.bizId = props.bizId
    }
    const response = await request.get('/api/trace/events', { params })
    const data = (response as any)?.data || {}
    events.value = (data.records || []).map(enrichEvent)
    total.value = Number(data.total || 0)
    selectedEvent.value = null
  } catch {
    events.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 后端只透传原文，前端解析 detail；标题按 businessType 枚举映射，不做 URL 语义判断 */
function enrichEvent(row: any): TraceEvent {
  const detail = parseDetail(row.detail)
  // 审批类操作（businessType=6）才有审核意见可拉取，按枚举识别，不做 URL 猜测
  const isReview = row.businessType === 6
  return {
    ...row,
    actionTitle: buildActionTitle(row),
    changes: detail.changes,
    attachments: detail.attachments,
    actionCode: isReview ? 'REVIEW' : undefined,
    isReview,
  }
}

/** 流水标题：module（去"管理"后缀）+ 业务类型枚举，如「产品BOM - 修改」 */
function buildActionTitle(row: any): string {
  const module = (row.module || '').replace(/管理$/, '')
  const bizLabel = BusinessTypeEnum.getLabel(row.businessType)
  if (module && bizLabel) return `${module} - ${bizLabel}`
  return bizLabel || module || '操作'
}

function handlePageChange() {
  selectedEvent.value = null
  loadEvents()
}

function handleSizeChange() {
  pageNum.value = 1
  selectedEvent.value = null
  loadEvents()
}

async function selectEvent(row: TraceEvent) {
  if (!hasDetail(row)) return
  selectedEvent.value = row
  await loadRowContent(row)
}

/** 点击行后按需加载审核意见（/api/trace/reviews） */
async function loadRowContent(row: TraceEvent) {
  const key = `${row.bizType || ''}:${row.bizId || ''}`
  if (!row.bizType || !row.bizId) return
  if (row.isReview) {
    try {
      let reviews = reviewCache.get(key)
      if (!reviews) {
        const res: any = await request.get('/api/trace/reviews', {
          params: { bizType: row.bizType, bizId: row.bizId },
        })
        reviews = res?.data || []
        reviewCache.set(key, reviews || [])
      }
      const matched = matchReview(reviews ?? [], row)
      if (matched) {
        row.comment = matched.comment
        row.roundNo = matched.roundNo
        row.actionCode = matched.actionCode || row.actionCode
      }
    } catch {
      /* 审核流水加载失败不阻断展示 */
    }
  }
}

/** 按时间最近匹配该行对应的审核记录（审核记录 actionCode 与日志行 actionCode 不做语义猜测，直接按时间就近） */
function matchReview(reviews: ReviewHistory[], row: TraceEvent): ReviewHistory | null {
  if (!reviews?.length) return null
  const list = reviews
  const target = row.time
    ? new Date(String(row.time).replace('T', ' ').replace(' ', 'T')).getTime()
    : NaN
  if (!Number.isFinite(target)) return list[list.length - 1] || null
  let best: ReviewHistory | null = null
  let bestDiff = Number.MAX_SAFE_INTEGER
  for (const review of list) {
    if (!review.createTime) continue
    const t = new Date(String(review.createTime).replace('T', ' ').replace(' ', 'T')).getTime()
    if (Number.isNaN(t)) continue
    const diff = Math.abs(t - target)
    if (diff < bestDiff) {
      bestDiff = diff
      best = review
    }
  }
  return best || list[list.length - 1] || null
}

function parseDetail(detail?: string | null): TraceDetail {
  if (!detail) return { changes: [], attachments: [] }
  try {
    const parsed = JSON.parse(detail)
    return {
      changes: Array.isArray(parsed?.changes) ? parsed.changes : [],
      attachments: Array.isArray(parsed?.attachments) ? parsed.attachments : [],
    }
  } catch {
    return { changes: [], attachments: [] }
  }
}

/**
 * bizStatus 直接显示：后端在写入流水时已把当时的状态快照成文案
 * （sys_oper_log.biz_status 为 varchar，@Log 取 T(状态枚举).常量.getLabel()），前端不再做映射。
 * 历史行里残留的数字是迁移前的旧数据。
 */
function formatBizStatus(status?: string): string {
  return status && status.trim() ? status : '-'
}

/** 业务模块列：优先 bizType 映射，module 兜底（1199：业务化名称，不再显示技术模块名） */
const BIZ_MODULE_NAMES: Record<string, string> = {
  order: '销售订单',
  sales_order: '销售订单',
  quotation: '报价单',
  sample: '样品单',
  sample_order: '样品单',
  inquiry: '询价单',
  purchase: '采购订单',
  purchase_order: '采购订单',
  production: '生产',
  production_order: '生产工单',
  production_execution: '工序执行',
  quality: '质检',
  bom: 'BOM',
  film: '工艺',
  review: '审核',
  attachment: '附件',
}
function formatBizModule(module?: string, bizType?: string): string {
  const byBizType = bizType ? BIZ_MODULE_NAMES[bizType] : undefined
  if (byBizType) return byBizType
  if (!module) return '-'
  return BIZ_MODULE_NAMES[module] || module
}

function formatTime(value?: string): string {
  return value ? String(value).replace('T', ' ').slice(0, 19) : '-'
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
