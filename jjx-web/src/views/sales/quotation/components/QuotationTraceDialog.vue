<template>
  <!-- 组合抽屉：业务流水 + 链路追踪（Tab 切换） -->
  <el-drawer
    :title="`查看流水 - ${quotationNo || ''}`"
    :model-value="modelValue"
    size="720px"
    append-to-body
    destroy-on-close
    @update:model-value="onVisibleChange"
  >
    <el-tabs v-model="activeTab">
      <el-tab-pane label="业务流水" name="flow">
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
                  <div class="flow-attachments" v-if="getAttachments(flow).length">
                    <el-link
                      v-for="att in getAttachments(flow)"
                      :key="att.id"
                      type="primary"
                      :href="downloadUrl(att.id)"
                      target="_blank"
                      style="margin-right: 8px"
                    >
                      {{ att.fileName }}
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
        <div class="tab-body">
          <div v-if="traceId" class="trace-header">
            <el-tag type="primary" effect="dark">traceId: {{ traceId }}</el-tag>
          </div>
          <div v-if="traceLoading" style="text-align:center;padding:40px">
            <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            <div style="margin-top:8px;color:#909399;font-size:13px">加载中...</div>
          </div>
          <el-empty v-else-if="traceFlatOps.length === 0" description="暂无操作日志" />
          <el-table v-else :data="traceFlatOps" size="small" stripe border>
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
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { quotationApi } from '@/api/sales/quotation'
import { attachmentApi } from '@/api/system/attachment'
import request from '@/utils/request'

const props = defineProps<{
  modelValue: boolean
  quotationId: number | null
  quotationNo?: string
  currentStatus?: number | null
  traceId?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const activeTab = ref<'flow' | 'trace'>('flow')

// 业务流水数据
const flowList = ref<any[]>([])
const flowLoading = ref(false)
const attachmentsMap = ref<Record<number, any[]>>({})

// 链路追踪数据（与 TraceTimeline 组件同款逻辑）
const traceNodes = ref<any[]>([])
const traceLoading = ref(false)

// 所有模块操作日志平铺，按时间排序
const traceFlatOps = computed(() => {
  const list: any[] = []
  for (const node of traceNodes.value) {
    for (const op of node.operations || []) {
      list.push({ ...op, module: node.module })
    }
  }
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

import { QuotationStatusEnum, SalesOrderStatusEnum, SampleOrderStatusEnum, InquiryStatusEnum } from '@/enums/sales'
import { ProductionOrderStatusEnum } from '@/enums/production'
import { PurchaseOrderStatusEnum } from '@/enums/purchase/order'

const BIZ_STATUS_ENUMS: Record<string, { getLabel: (v: number) => string }> = {
  inquiry: InquiryStatusEnum,
  quotation: QuotationStatusEnum,
  order: SalesOrderStatusEnum,
  sales_order: SalesOrderStatusEnum,
  sample: SampleOrderStatusEnum,
  purchase: PurchaseOrderStatusEnum,
  production: ProductionOrderStatusEnum,
}

function formatBizStatus(bizStatus: number, bizType: string): string {
  if (bizStatus == null) return ''
  const statusEnum = BIZ_STATUS_ENUMS[bizType || '']
  if (statusEnum) {
    const label = statusEnum.getLabel(bizStatus)
    return label && label !== '未知' ? label : String(bizStatus)
  }
  return String(bizStatus)
}

// 加载链路追踪（与 TraceTimeline 同款：优先 traceId，失败按 bizId 反查）
async function loadTrace() {
  if (!props.traceId && !props.quotationId) return
  traceLoading.value = true
  try {
    if (props.traceId) {
      const res: any = await request.get(`/api/trace/${props.traceId}`)
      traceNodes.value = res?.data || []
    }
    if (!traceNodes.value.length && props.quotationId) {
      const res: any = await request.get('/api/trace/search', {
        params: { keyword: props.quotationId },
      })
      const traces: any[] = res?.data || []
      const match =
        traces.find((t) => {
          const firstNode = t.nodes?.[0]
          return !firstNode?.bizType || firstNode?.bizType === 'quotation'
        }) || traces[0]
      traceNodes.value = match?.nodes || []
    }
  } catch (e) {
    console.error('加载链路失败:', e)
    traceNodes.value = []
  } finally {
    traceLoading.value = false
  }
}

const statusMap: Record<number, string> = {
  0: '草稿', 1: '已发送', 2: '已确认', 3: '已拒绝', 4: '已过期', 5: '待审核', 6: '已审核', 8: '改单', 9: '已完成',
}

function statusText(status: number | null | undefined): string {
  return statusMap[status ?? -1] ?? '-'
}

function formatTime(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

function timelineType(flow: any, idx: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (flow.actionCode === 'REJECT' || flow.actionCode === 'CUSTOMER_REJECT') return 'danger'
  if (flow.actionCode === 'APPROVE' || flow.actionCode === 'SUBMIT_REVIEW') return 'primary'
  if (idx === 0) return 'success'
  return 'info'
}

function getAttachments(flow: any): any[] {
  return attachmentsMap.value[flow.flowId] || []
}

function downloadUrl(id: number): string {
  return `/system/attachment/download/${id}`
}

function onVisibleChange(val: boolean) {
  emit('update:modelValue', val)
  if (val) {
    loadFlow()
    loadTrace()
  }
}

// 打开时默认切到业务流水
watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      activeTab.value = 'flow'
      loadFlow()
      loadTrace()
    }
  },
)

// 加载业务流水
async function loadFlow() {
  if (!props.quotationId) return
  flowLoading.value = true
  try {
    const res: any = await quotationApi.getFlowRecords(props.quotationId)
    flowList.value = res?.data || []
    await loadAttachments()
  } finally {
    flowLoading.value = false
  }
}

async function loadAttachments() {
  if (!props.quotationId) return
  attachmentsMap.value = {}
  try {
    const res: any = await attachmentApi.list('quotation_flow', props.quotationId)
    const atts: any[] = res?.data || []
    const map: Record<number, any[]> = {}
    for (const att of atts) {
      const flowId = Number(att.bizId)
      if (!map[flowId]) map[flowId] = []
      map[flowId].push(att)
    }
    attachmentsMap.value = map
  } catch (e) {
    console.error('加载附件失败:', e)
  }
}

</script>

<style scoped>
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

.trace-header {
  margin-bottom: 12px;
}

.trace-node {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  margin-bottom: 8px;
}

.trace-node-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.trace-node-time {
  font-size: 12px;
  color: #909399;
}

.trace-node-title {
  font-weight: 600;
  font-size: 13px;
  margin-top: 6px;
}

.trace-node-content {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
}
</style>
