<template>
  <el-drawer
    v-model="visible"
    title="🔗 链路追踪"
    size="700px"
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
          <span v-else>{{ formatBusinessType(scope.row.businessType) }}</span>
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

// 把所有模块的操作日志平铺成一个列表，按时间排序（附件作为特殊行融入，DEV-735）
const flatOps = computed(() => {
  const list: any[] = []
  for (const node of nodes.value) {
    for (const op of (node.operations || [])) {
      list.push({
        ...op,
        module: node.module,
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
</style>
