<template>
  <el-drawer
    v-model="visible"
    title="🔗 链路追踪"
    size="820px"
    @open="loadTrace"
    @close="handleClose"
  >
    <!-- 链路上方信息 -->
    <div v-if="traceId" class="trace-header">
      <el-tag type="primary" effect="dark">traceId: {{ traceId }}</el-tag>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" style="text-align:center;padding:60px">
      <el-icon class="is-loading" :size="28"><Loading /></el-icon>
      <div style="margin-top:10px;color:var(--el-text-color-secondary)">加载中...</div>
    </div>

    <!-- 空结果 -->
    <el-empty v-else-if="flatOps.length === 0" description="暂无操作日志" />

    <!-- 平铺表格 -->
    <el-table v-else :data="flatOps" size="small" stripe border>
      <el-table-column prop="time" label="时间" width="160" />
      <el-table-column label="状态" width="110">
        <template #default="scope">
          {{ formatBizStatus(scope.row.bizStatus, scope.row.bizType) }}
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="120" />
      <el-table-column label="操作" min-width="180">
        <template #default="scope">
          <el-link
            v-if="scope.row.__att"
            type="primary"
            :href="downloadUrl(scope.row.attId)"
            :underline="false"
            target="_blank"
          >
            {{ scope.row.operation }} <el-icon><Download /></el-icon>
          </el-link>
          <span v-else>
            <span>{{ formatBusinessType(scope.row.businessType) }}</span>
            <!-- 2026-08-18：字段级变更明细 -->
            <div v-if="scope.row.opChanges && scope.row.opChanges.length" class="op-changes">
              <div v-for="(c, i) in scope.row.opChanges" :key="i" class="op-change-item">
                {{ c }}
              </div>
            </div>
            <div v-if="scope.row.attachments && scope.row.attachments.length" class="op-attachments">
              <el-link
                v-for="a in scope.row.attachments"
                :key="a.id"
                type="primary"
                :href="downloadUrl(a.id)"
                :underline="false"
                target="_blank"
                style="margin-right: 8px"
                >📎 {{ a.fileName }}</el-link
              >
            </div>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="80" />
      <el-table-column label="结果" width="80" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.__att" size="small" type="warning">📎 附件</el-tag>
          <el-tag v-else :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
            {{ scope.row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>

    <!-- 审核履历（review_flow，2026-08-28：审核意见/驳回原因/轮次/审核附件） -->
    <div v-if="reviewRounds.length" class="review-section">
      <div class="review-title">📋 审核履历</div>
      <el-collapse v-model="reviewActiveRounds">
        <el-collapse-item v-for="round in reviewRounds" :key="round.roundNo" :name="round.roundNo">
          <template #title>
            <span class="review-round">第 {{ round.roundNo }} 轮</span>
            <span class="review-round-time">{{ formatTime(round.items[round.items.length - 1].createTime) }}</span>
          </template>
          <div v-for="item in round.items" :key="item.flowId" class="review-item">
            <span class="review-icon">{{ reviewActionIcon(item.actionCode) }}</span>
            <span class="review-action">{{ item.actionName || reviewActionLabel(item.actionCode) }}</span>
            <span class="review-operator">{{ item.operatorName || '-' }}</span>
            <span v-if="reviewStatusText(item)" class="review-flow">
              {{ reviewStatusText(item) }}
            </span>
            <div class="review-comment" :class="{ 'is-reject': item.actionCode === 'REJECT' }">
              {{ item.comment || '（无意见）' }}
            </div>
            <div v-if="reviewAttachmentIds(item).length" class="review-attachments">
              <el-link
                v-for="a in reviewAttachmentIds(item)"
                :key="a"
                type="primary"
                :href="downloadUrl(a)"
                :underline="false"
                target="_blank"
                style="margin-right: 8px"
                >📎 附件{{ a }}</el-link
              >
            </div>
          </div>
        </el-collapse-item>
      </el-collapse>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Loading, Download } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { attachmentApi } from '@/api/system/attachment'

const props = defineProps<{
  traceId: string
  bizType?: string
  bizId?: string
  module?: string
  modelValue: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(false)
const loading = ref(false)
const nodes = ref<any[]>([])
/** 链路附件（DEV-735）：按 traceId 或 bizType+bizId 拉取，融入时间线 */
const attachments = ref<any[]>([])

watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.traceId, () => { if (props.modelValue) loadTrace() })
watch(() => props.bizId, () => { if (props.modelValue) loadTrace() })

function handleClose() {
  emit('update:modelValue', false)
}

// 把所有模块的操作日志平铺成一个列表，按时间排序（附件作为特殊行融入，DEV-735）
const flatOps = computed(() => {
  const list: any[] = []
  for (const node of nodes.value) {
    for (const op of (node.operations || [])) {
      // 解析操作日志 detail 中的附件（审核提交的图片等），挂到操作行
      let opAttachments: any[] = []
      if (op.detail) {
        try {
          const d = JSON.parse(op.detail)
          opAttachments = d.attachments || []
        } catch { /* ignore */ }
      }
      list.push({
        ...op,
        // 2026-08-18：变更明细（detail.changes）挂到行上展示
        opChanges: parseChanges(op.detail),
        module: node.module,
        attachments: opAttachments,
      })
    }
  }
  for (const att of attachments.value) {
    list.push({
      __att: true,
      attId: att.id,
      time: formatTime(att.createTime),
      bizType: att.bizType,
      module: att.bizType === 'product'
        ? `产品文件${att.category ? '·' + att.category : ''}`
        : '附件',
      operation: att.fileName || '-',
      operator: att.createBy,
      status: 1,
    })
  }
  // 按时间正序
  list.sort((a, b) => (a.time || '').localeCompare(b.time || ''))
  return list
})

// 解析 detail 中的变更清单（2026-08-18：{"changes":["交货日期:xxx→xxx", ...]}）
function parseChanges(detail: string | null | undefined): string[] {
  if (!detail) return []
  try {
    const d = JSON.parse(detail)
    return Array.isArray(d?.changes) ? d.changes : []
  } catch {
    return []
  }
}

function formatTime(t: string | null | undefined): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 19)
}

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

// 加载链路附件：优先按 traceId（含来源单据文档）；无 traceId 时按 bizType+bizId
async function loadAttachments() {
  attachments.value = []
  try {
    if (props.traceId) {
      const res = await attachmentApi.listByTrace(props.traceId)
      attachments.value = (res as any)?.data || []
    } else if (props.bizType && props.bizId) {
      const res = await attachmentApi.list(props.bizType, Number(props.bizId))
      attachments.value = (res as any)?.data || []
    }
  } catch {
    attachments.value = []
  }
}

function formatBusinessType(code: number): string {
  const map: Record<number, string> = {
    1: '新增', 2: '修改', 3: '删除', 4: '导出', 5: '导入',
    6: '审批', 7: '登录', 8: '登出', 9: '其他', 10: '重置密码', 11: '转换',
  }
  return map[code] ?? String(code ?? '')
}

/**
 * 业务码 → 状态枚举 映射
 * 与后端各 StatusEnum 一一对应，统一走 enums 目录（不硬编码）
 */
import { QuotationStatusEnum, SalesOrderStatusEnum, SampleOrderStatusEnum, InquiryStatusEnum } from '@/enums/sales'
import { ProductionOrderStatusEnum } from '@/enums/production'
import { PurchaseOrderStatusEnum } from '@/enums/purchase/order'

// 按 bizType 查对应状态枚举
const BIZ_STATUS_ENUMS: Record<string, { getLabel: (v: number) => string }> = {
  // 销售询价单 InquiryStatus
  inquiry: InquiryStatusEnum,
  // 报价单 QuotationStatus
  quotation: QuotationStatusEnum,
  // 销售订单 OrderStatusEnum
  order: SalesOrderStatusEnum,
  sales_order: SalesOrderStatusEnum,
  // 样品单 SampleOrderStatusEnum
  sample: SampleOrderStatusEnum,
  // 采购订单 PurchaseOrderStatusEnum
  purchase: PurchaseOrderStatusEnum,
  // 生产工单（production OrderStatusEnum）
  production: ProductionOrderStatusEnum,
}

/**
 * 按业务码获取状态名：bizType(业务码) → 状态枚举 → bizStatus → 状态名
 */
function formatBizStatus(bizStatus: number, bizType: string): string {
  if (bizStatus == null) return ''
  const statusEnum = BIZ_STATUS_ENUMS[bizType || '']
  if (statusEnum) {
    const label = statusEnum.getLabel(bizStatus)
    return label && label !== '未知' ? label : String(bizStatus)
  }
  return String(bizStatus)
}

async function loadTrace() {
  // 优先按 traceId 查完整链路；查不到或没有 traceId 时，按 bizType+bizId 反查
  if (!props.traceId && !props.bizId) return
  loading.value = true
  try {
    if (props.traceId) {
      const res = await request.get(`/api/trace/${props.traceId}`)
      nodes.value = (res as any).data || []
    }
    if (!nodes.value.length && props.bizId) {
      // 按业务ID反查：searchTrace 支持按 bizId 模糊匹配 trace_id
      const res = await request.get('/api/trace/search', { params: { keyword: props.bizId } })
      const traces: any[] = (res as any).data || []
      // 优先取 bizType 匹配的链路
      const match = traces.find((t) => {
        const firstNode = t.nodes?.[0]
        return !props.bizType || firstNode?.bizType === props.bizType
      }) || traces[0]
      nodes.value = match?.nodes || []
    }
    // 链路附件（DEV-735）
    await loadAttachments()
    // 审核履历（review_flow，2026-08-28）
    await loadReviewFlows()
  } catch {
    nodes.value = []
  } finally {
    loading.value = false
  }
}

// ==================== 审核履历（review_flow，2026-08-28） ====================

const reviewFlows = ref<any[]>([])
const reviewActiveRounds = ref<number[]>([])

/** 前端 bizType → review_flow.bizType（020 已接入的模块；未接入的返回 undefined 不查询） */
const REVIEW_FLOW_BIZ_MAP: Record<string, string> = {
  order: 'sales_order',
  sales_order: 'sales_order',
  purchase: 'purchase_order',
  purchase_order: 'purchase_order',
  bom: 'engineering_bom',
  film: 'engineering_film',
  // 报价：流水在 sales_quotation_flow（020 决策报价不接入 review_flow），走报价模块接口
  quotation: 'quotation',
}

/** 审核动作图标（未匹配时用首字符） */
function reviewActionIcon(actionCode: string): string {
  return ({ SUBMIT: '📤', SUBMIT_REVIEW: '📤', APPROVE: '✅', REJECT: '⛔', SEND: '📨', CONFIRM: '✔', CUSTOMER_CONFIRM: '✔', CANCEL: '🚫' } as Record<string, string>)[actionCode] || '•'
}

function reviewActionLabel(actionCode: string): string {
  return ({ SUBMIT: '提交审核', SUBMIT_REVIEW: '提交审核', APPROVE: '审核通过', REJECT: '审核驳回', SEND: '发送', CONFIRM: '确认', CUSTOMER_CONFIRM: '客户确认', CANCEL: '取消' } as Record<string, string>)[actionCode] || actionCode || ''
}

/** 状态码 → 状态名（复用 BIZ_STATUS_ENUMS；review_flow.bizType 需映射回枚举键） */
function reviewBizEnumKey(bizType: string): string | undefined {
  if (bizType === 'sales_order') return 'sales_order'
  if (bizType === 'purchase_order') return 'purchase'
  if (bizType === 'quotation') return 'quotation'
  return undefined
}

function reviewStatusText(item: any): string {
  const enumKey = reviewBizEnumKey(item.bizType)
  const statusEnum = enumKey ? BIZ_STATUS_ENUMS[enumKey] : undefined
  const nameOf = (code: string | null): string => {
    if (code == null || code === '') return ''
    if (statusEnum) {
      const label = statusEnum.getLabel(Number(code))
      if (label && label !== '未知') return label
    }
    return code
  }
  const from = nameOf(item.fromStatus)
  const to = nameOf(item.toStatus)
  if (!from && !to) return ''
  return `${from || '?'} → ${to || '?'}`
}

/** 附件 id 列表（逗号分隔 → number[]） */
function reviewAttachmentIds(item: any): number[] {
  if (!item.attachmentIds) return []
  return String(item.attachmentIds).split(',').map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n))
}

/** 按轮次分组：组内 flowId 升序，轮次降序（最新轮在前） */
const reviewRounds = computed(() => {
  const groups = new Map<number, any[]>()
  for (const f of [...reviewFlows.value].sort((a, b) => (a.flowId || 0) - (b.flowId || 0))) {
    const round = f.roundNo || 1
    if (!groups.has(round)) groups.set(round, [])
    groups.get(round)!.push(f)
  }
  return [...groups.entries()].sort((a, b) => b[0] - a[0]).map(([roundNo, items]) => ({ roundNo, items }))
})

async function loadReviewFlows() {
  reviewFlows.value = []
  if (!props.bizId) return
  const rfBizType = props.bizType ? REVIEW_FLOW_BIZ_MAP[props.bizType] : undefined
  if (!rfBizType) return
  try {
    let data: any[] = []
    if (rfBizType === 'quotation') {
      // 报价流水在 sales_quotation_flow（020 决策报价不接入 review_flow），复用报价模块接口
      const res = await request.get(`/sales/quotation/flow/${props.bizId}`)
      data = ((res as any)?.data || []).map((f: any) => ({
        flowId: f.flowId,
        bizType: 'quotation',
        roundNo: 1,
        actionCode: f.actionCode,
        actionName: f.actionName,
        fromStatus: f.fromStatus != null ? String(f.fromStatus) : null,
        toStatus: f.toStatus != null ? String(f.toStatus) : null,
        operatorName: f.operatorName,
        comment: f.remark,
        attachmentIds: f.attachmentIds,
        createTime: f.createTime,
      }))
    } else {
      const res = await request.get('/system/review-flow/list', { params: { bizType: rfBizType, bizId: props.bizId } })
      data = (res as any)?.data || []
    }
    reviewFlows.value = data
    const maxRound = reviewFlows.value.reduce((m, f) => Math.max(m, f.roundNo || 0), 0)
    reviewActiveRounds.value = maxRound ? [maxRound] : []
  } catch {
    reviewFlows.value = []
  }
}
</script>

<style scoped>
.trace-header { margin-bottom: 12px; }
<style scoped>
.op-action {
  font-size: 12px;
  color: var(--el-text-color-primary);
  margin-top: 2px;
}
.op-changes {
  margin-top: 2px;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.op-change-item {
  font-size: 12px;
  color: var(--el-color-warning-dark-2);
  line-height: 1.5;
}
.op-attachments {
  margin-top: 4px;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 0;
}
.op-attachments .el-link {
  font-size: 12px;
}

/* ==================== 审核履历（2026-08-28） ==================== */
.review-section {
  margin-top: 18px;
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}
.review-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
}
.review-round {
  font-weight: 600;
  margin-right: 12px;
  color: var(--el-color-primary);
}
.review-round-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.review-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  padding: 6px 4px;
  border-bottom: 1px dashed var(--el-border-color-lighter);
  font-size: 13px;
}
.review-item:last-child {
  border-bottom: none;
}
.review-icon {
  font-size: 14px;
}
.review-action {
  font-weight: 600;
}
.review-operator {
  color: var(--el-text-color-secondary);
}
.review-flow {
  font-size: 12px;
  color: var(--el-color-info);
  background: var(--el-fill-color-light);
  border-radius: 4px;
  padding: 0 6px;
}
.review-comment {
  width: 100%;
  padding-left: 22px;
  color: var(--el-text-color-primary);
  word-break: break-all;
}
.review-comment.is-reject {
  color: var(--el-color-danger);
  font-weight: 600;
}
.review-attachments {
  width: 100%;
  padding-left: 22px;
}
</style>
