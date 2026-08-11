<template>
  <el-drawer
    v-model="visible"
    :title="title"
    size="820px"
    @open="handleOpen"
    @close="handleClose"
  >
    <!-- 统一 Tabs：有业务流水时显示两个 Tab，无则只显示链路追踪（隐藏 Tab 头） -->
    <el-tabs v-model="activeTab" :class="{ 'trace-single-tab': !flowApi }">
      <el-tab-pane v-if="flowApi" label="业务流水" name="flow">
        <div class="tab-hint">单据状态流转记录：谁操作、状态变化、意见与附件</div>
        <div v-loading="flowLoading" class="tab-body">
          <template v-if="flowList.length">
            <el-timeline>
              <el-timeline-item
                v-for="(flow, idx) in flowList"
                :key="flow.flowId"
                :timestamp="formatTime(flow.createTime)"
                :type="timelineType(flow, idx)"
                :hollow="idx !== 0"
              >
                <div class="flow-item">
                  <div class="flow-title">
                    <span class="action-name">{{ flow.actionName }}</span>
                    <span class="status-change" v-if="flow.fromStatus !== null && flow.fromStatus !== flow.toStatus">
                      {{ statusText(flow.fromStatus) }} → {{ statusText(flow.toStatus) }}
                    </span>
                  </div>
                  <div class="flow-meta">操作人：{{ flow.operatorName || '-' }}</div>
                  <div class="flow-remark" v-if="flow.remark">
                    <span class="remark-label">意见/说明：</span>{{ flow.remark }}
                  </div>
                  <div class="op-attachments" v-if="getAttachments(flow).length">
                    <el-link
                      v-for="att in getAttachments(flow)"
                      :key="att.id"
                      type="primary"
                      :href="downloadUrl(att.id)"
                      target="_blank"
                      style="margin-right: 8px"
                    >
                      📎 {{ att.fileName }}
                    </el-link>
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
          </template>
          <el-empty v-else description="暂无流转记录" :image-size="60" />
        </div>
      </el-tab-pane>
      <el-tab-pane label="链路追踪" name="trace">
        <div class="tab-hint">系统操作链路（traceId 关联的各模块操作日志）</div>

        <!-- 链路上方信息 -->
        <div v-if="traceId" class="trace-header">
          <el-tag type="primary" effect="dark">traceId: {{ traceId }}</el-tag>
        </div>

        <!-- 加载中 -->
        <div v-if="loading" style="text-align:center;padding:40px">
          <el-icon class="is-loading" :size="24"><Loading /></el-icon>
          <div style="margin-top:8px;color:var(--el-text-color-secondary);font-size:13px">加载中...</div>
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
      </el-tab-pane>
    </el-tabs>
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
  /** 业务流水加载函数（传入后显示“业务流水”Tab，2026-08-11 统一组件） */
  flowApi?: (bizId: number | string) => Promise<any>
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const visible = ref(false)
const loading = ref(false)
const nodes = ref<any[]>([])
/** 链路附件（DEV-735）：按 traceId 或 bizType+bizId 拉取，融入时间线 */
const attachments = ref<any[]>([])

// ===== 业务流水（可选，2026-08-11 从 QuotationTraceDialog 合并） =====
const activeTab = ref<'flow' | 'trace'>('flow')
const flowList = ref<any[]>([])
const flowLoading = ref(false)
const attachmentsMap = ref<Record<number, any[]>>({})

const title = computed(() => (props.flowApi ? `查看流水 - ${props.bizId || ''}` : '🔗 链路追踪'))

watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.traceId, () => { if (props.modelValue) loadTrace() })
watch(() => props.bizId, () => { if (props.modelValue) loadTrace() })

function handleOpen() {
  loadTrace()
  if (props.flowApi) {
    activeTab.value = 'flow'
    loadFlow()
  }
}

function handleClose() {
  emit('update:modelValue', false)
}

// ===== 业务流水 =====
async function loadFlow() {
  if (!props.flowApi || !props.bizId) return
  flowLoading.value = true
  try {
    const res: any = await props.flowApi(String(props.bizId))
    flowList.value = res?.data || []
    await loadFlowAttachments()
  } catch {
    flowList.value = []
  } finally {
    flowLoading.value = false
  }
}

async function loadFlowAttachments() {
  if (!props.bizId) return
  attachmentsMap.value = {}
  try {
    const res: any = await attachmentApi.list('quotation_flow', Number(props.bizId))
    const atts: any[] = res?.data || []
    const map: Record<number, any[]> = {}
    for (const att of atts) {
      const flowId = Number(att.bizId)
      if (!map[flowId]) map[flowId] = []
      map[flowId].push(att)
    }
    attachmentsMap.value = map
  } catch {
    attachmentsMap.value = {}
  }
}

function getAttachments(flow: any): any[] {
  return attachmentsMap.value[flow.flowId] || []
}

function timelineType(flow: any, idx: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (flow.actionCode === 'REJECT' || flow.actionCode === 'CUSTOMER_REJECT') return 'danger'
  if (flow.actionCode === 'APPROVE' || flow.actionCode === 'SUBMIT_REVIEW') return 'primary'
  if (idx === 0) return 'success'
  return 'info'
}

const statusMap: Record<number, string> = {
  0: '草稿', 1: '已发送', 2: '已确认', 3: '已拒绝', 4: '已过期', 5: '待审核', 6: '已审核', 8: '改单', 9: '已完成',
}

function statusText(status: number | null | undefined): string {
  return statusMap[status ?? -1] ?? '-'
}

// ===== 链路追踪 =====
/** 所有模块操作日志平铺列表（附件作为特殊行融入，DEV-735） */
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

/* 2026-08-11 业务流水 Tab 样式（从 QuotationTraceDialog 合并） */
.tab-hint {
  color: #909399;
  font-size: 12px;
  margin-bottom: 12px;
}
.tab-body {
  min-height: 200px;
  max-height: 480px;
  overflow-y: auto;
}
.flow-item {
  padding: 4px 0;
}
.flow-title {
  display: flex;
  align-items: center;
  gap: 8px;
}
.action-name {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}
.status-change {
  font-size: 12px;
  color: #909399;
}
.flow-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.flow-remark {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
  background: #f5f7fa;
  padding: 6px 10px;
  border-radius: 4px;
}
/* 无业务流水时隐藏 Tab 头，保持旧观感 */
.trace-single-tab :deep(.el-tabs__header) {
  display: none;
}
<style scoped>
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
