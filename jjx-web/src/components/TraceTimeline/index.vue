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
          {{ formatBusinessType(scope.row.businessType) }}
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" width="80" />
      <el-table-column label="结果" width="70" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'danger'" size="small">
            {{ scope.row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'

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

watch(() => props.modelValue, (v) => { visible.value = v })
watch(() => props.traceId, () => { if (props.modelValue) loadTrace() })
watch(() => props.bizId, () => { if (props.modelValue) loadTrace() })

function handleClose() {
  emit('update:modelValue', false)
}

// 把所有模块的操作日志平铺成一个列表，按时间排序
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
  // 按时间正序
  list.sort((a, b) => (a.time || '').localeCompare(b.time || ''))
  return list
})

function formatBusinessType(code: number): string {
  const map: Record<number, string> = {
    1: '新增', 2: '修改', 3: '删除', 4: '导出', 5: '导入',
    6: '审批', 7: '登录', 8: '登出', 9: '其他', 10: '重置密码', 11: '转换',
  }
  return map[code] ?? String(code ?? '')
}

/**
 * 业务码 → 状态码枚举 映射表
 * 根据 @Log#bizType（业务码）找到对应业务的状态码枚举，
 * 再用 bizStatus 查状态名。与后端各 StatusEnum 对齐。
 */
const BIZ_STATUS_MAP: Record<string, Record<number, string>> = {
  // 销售询价单 InquiryStatus
  inquiry: { 0:'草稿', 1:'待处理', 2:'已发送', 3:'已转报价', 4:'已确认', 5:'已拒绝', 6:'已过期' },
  // 报价单 QuotationStatus
  quotation: { 0:'草稿', 1:'已发送', 2:'已确认', 3:'已拒绝', 4:'已过期', 5:'待审核', 6:'已审核', 8:'改单', 9:'已完成' },
  // 销售订单 OrderStatusEnum
  order: { 1:'草稿', 2:'待审核', 3:'审核中', 4:'已审核', 5:'已驳回', 6:'已确认', 7:'生产中', 8:'已发货', 9:'已完成', 10:'已取消' },
  sales_order: { 1:'草稿', 2:'待审核', 3:'审核中', 4:'已审核', 5:'已驳回', 6:'已确认', 7:'生产中', 8:'已发货', 9:'已完成', 10:'已取消' },
  // 样品订单 SampleOrderStatusEnum
  sample: { 1:'样品需求已创建', 2:'待审核', 3:'工程打样中', 4:'样品待送样', 5:'已送样待确认', 6:'样品确认', 7:'已转量产', 8:'已关闭', 9:'客户退回', 10:'已取消' },
  // 采购订单 PurchaseOrderStatusEnum
  purchase: { 0:'草稿', 1:'询价中', 2:'比价中', 3:'已提交', 4:'已批准', 5:'执行中', 6:'已完成', 7:'已关闭' },
  // 生产订单（production OrderStatusEnum）
  production: { 0:'草稿', 1:'待审核', 2:'已审核', 3:'已驳回', 4:'已计划', 5:'待开始', 6:'进行中', 7:'已暂停', 8:'已完成', 9:'已取消', 10:'已关闭', 11:'已超期' },
  // 产品 ProductEnums.Status
  product: { 1:'开发中', 2:'待审核', 3:'审核中', 4:'已通过', 5:'已驳回', 6:'已发布', 7:'停产', 8:'取消' },
  // 客户 CustomerStatusEnum
  custom: { 1:'潜在客户', 2:'正式客户', 3:'暂停合作', 4:'终止合作' },
  // 采购收货 ReceiptStatusEnum
  receipt: { 0:'待收货', 1:'部分收货', 2:'已收货' },
}

/**
 * 按业务码获取状态名：bizType(业务码) → 状态码枚举 → bizStatus → 状态名
 */
function formatBizStatus(bizStatus: number, bizType: string): string {
  if (bizStatus == null) return ''
  const statusMap = BIZ_STATUS_MAP[bizType || '']
  if (statusMap) {
    return statusMap[bizStatus] ?? String(bizStatus)
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
