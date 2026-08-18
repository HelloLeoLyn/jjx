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
            <div v-if="scope.row.operation && scope.row.operation !== '-'">
              <div class="op-action">{{ scope.row.operation }}</div>
              <!-- 2026-08-18：字段级变更明细 -->
              <div v-if="scope.row.opChanges && scope.row.opChanges.length" class="op-changes">
                <div v-for="(c, i) in scope.row.opChanges" :key="i" class="op-change-item">
                  {{ c }}
                </div>
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

// sales_order 模块事件码 → 中文（saveOrderLog 写的 order.xxx，非 URL）
const ORDER_ACTION_MAP: Record<string, string> = {
  'order.submit_review': '提交审核',
  'order.start_review': '开始审核',
  'order.approve': '审核通过',
  'order.reject': '审核驳回',
  'order.resubmit': '重新提交审核',
  'order.cancel': '取消订单',
  'order.cancel_work_order': '取消工单',
  'order.send': '发送客户确认',
  'order.generate_plan': '生成生产计划',
  'order.confirm': '客户确认',
  'order.ship': '发货',
  'order.complete': '完成订单',
  'order.update': '修改订单',
  'order.create': '创建订单',
}

// 操作列可读化：sales_order 事件码转中文；其余直接显示 URL（2026-08-18 修字段名不匹配）
function formatOperation(action: string | null | undefined, module: string): string {
  if (!action) return '-'
  if (module === 'sales_order' && action.startsWith('order.')) {
    return ORDER_ACTION_MAP[action] || action.replace('order.', '')
  }
  return action
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
        operation: formatOperation(op.action, node.module),
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
  } catch {
    nodes.value = []
  } finally {
    loading.value = false
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
</style>
