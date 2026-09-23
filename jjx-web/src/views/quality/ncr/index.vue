<template>
  <div class="ncr-page">
    <el-alert type="info" :closable="false" show-icon class="scope-guide">
      <template #title>
        <div class="scope-guide__content">
          <span>本页主要处理成品检验批不良：返工、让步接收（特采）或报废。成品让步接收必须取得客户确认。</span>
          <el-button link type="primary" @click="goIqcQuarantine">查看来料不合格处置</el-button>
        </div>
      </template>
    </el-alert>
    <el-card>
      <template #header>
        <div class="header">
          <span>产品不良台账</span>
          <div>
            <el-select v-model="query.lotType" clearable placeholder="来源类型" style="width: 140px" @change="load(1)">
              <el-option label="来料检验" value="IQC" />
              <el-option label="成品检验" value="FQC" />
            </el-select>
            <el-select v-model="query.status" clearable placeholder="状态" style="width: 130px" @change="load(1)">
              <el-option label="待处置" value="PENDING" />
              <el-option label="处置中" value="DISPOSING" />
              <el-option label="已结" value="CLOSED" />
            </el-select>
            <el-input v-model="query.materialCode" clearable placeholder="物料编码" style="width: 160px" @keyup.enter="load(1)" />
            <el-button type="primary" @click="load(1)">查询</el-button>
            <el-button @click="load()">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border size="small">
        <template #empty><el-empty description="暂无不良记录" /></template>
        <el-table-column prop="ncrNo" label="不良单号" min-width="150" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ row.lotType === 'IQC' ? '来料' : '成品' }}</template>
        </el-table-column>
        <el-table-column label="工单/来源" min-width="150">
          <template #default="{ row }">
            {{ row.orderId ? '工单 #' + row.orderId : '批 #' + row.lotId }}
          </template>
        </el-table-column>
        <el-table-column label="物料/产品" min-width="150">
          <template #default="{ row }">
            {{ row.materialCode || row.productCode || '-' }}
            <span class="sub">{{ row.materialName || row.productName || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" min-width="120" />
        <el-table-column label="不良数量" width="95" align="right">
          <template #default="{ row }">{{ num(row.defectQuantity) }}</template>
        </el-table-column>
        <el-table-column label="CR/MA/MI" width="110" align="center">
          <template #default="{ row }">{{ num(row.crQuantity) }}/{{ num(row.maQuantity) }}/{{ num(row.miQuantity) }}</template>
        </el-table-column>
        <el-table-column label="已处置" width="90" align="right">
          <template #default="{ row }">{{ num(row.disposedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="待处置" width="90" align="right">
          <template #default="{ row }">
            <el-tag v-if="pending(row) > 0" type="danger" size="small">{{ num(pending(row)) }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="95">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'CLOSED' ? 'success' : row.status === 'DISPOSING' ? 'warning' : 'danger'">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="defectReason" label="不良原因" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" :disabled="pending(row) <= 0" @click="openDispose(row)">处置</el-button>
            <el-button link size="small" @click="openActions(row)">处置记录</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="load()"
          @size-change="load(1)"
        />
      </div>
    </el-card>

    <!-- 处置 -->
    <el-dialog v-model="disposeVisible" title="不良处置" width="520px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="不良单号">{{ current?.ncrNo }}</el-form-item>
        <el-form-item label="待处置数量">{{ num(current ? pending(current) : 0) }}</el-form-item>
        <el-form-item label="处置方式" required>
          <el-select v-model="disposeForm.actionType" style="width: 100%">
            <el-option label="返工" value="REWORK" />
            <el-option label="让步接收（特采）" value="CONCESSION" />
            <el-option label="报废" value="SCRAP" />
          </el-select>
        </el-form-item>
        <el-form-item label="处置数量" required>
          <el-input-number v-model="disposeForm.quantity" :min="1" :max="current ? pending(current) : 0" />
        </el-form-item>
        <template v-if="disposeForm.actionType === 'REWORK'">
          <el-form-item label="返工工序" required>
            <el-select v-model="disposeForm.standardProcessId" filterable style="width: 100%" placeholder="选择标准工序">
              <el-option
                v-for="process in standardProcesses"
                :key="process.processId"
                :label="`${process.processCode || ''} ${process.processName}`.trim()"
                :value="process.processId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="返工要求">
            <el-input v-model="disposeForm.reworkRequirement" type="textarea" :rows="3" placeholder="填写本次返工的特殊要求" />
          </el-form-item>
        </template>
        <el-form-item v-if="disposeForm.actionType === 'CONCESSION'" label="客户已确认">
          <el-switch v-model="disposeForm.customerConfirmed" />
          <span class="tip">让步接收必须先取得客户确认</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="disposeForm.resultRemark" type="textarea" :rows="2" placeholder="可空" />
        </el-form-item>
        <div class="tip block">
          返工：暂不影响库存，生成返工工序并在复检合格后入库；让步接收（特采）：影响库存，须客户确认后转良品库存；报废：不影响库存，只记台账（不良品未入良品库）
        </div>
      </el-form>
      <template #footer>
        <el-button @click="disposeVisible = false">取消</el-button>
        <el-button type="primary" :loading="disposing" @click="submitDispose">提交处置</el-button>
      </template>
    </el-dialog>

    <!-- 处置记录 -->
    <el-dialog v-model="actionsVisible" title="处置记录" width="680px" append-to-body>
      <el-table :data="actions" border size="small">
        <el-table-column label="方式" width="130">
          <template #default="{ row }">{{ actionLabel(row.actionType) }}</template>
        </el-table-column>
        <el-table-column label="数量" width="90" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="客户确认" width="100" align="center">
          <template #default="{ row }">{{ row.customerConfirmed ? '是' : '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ actionStatusLabel(row.status) }}</template>
        </el-table-column>
        <!-- dev-20260923-031：返工要有进度（原来只给裸 ID「工序 #12」，看不出修到哪一步） -->
        <el-table-column label="返工 / 复检进度" min-width="260">
          <template #default="{ row }">
            <div v-if="reworkOf(row)" class="rework-cell">
              <el-tag type="danger" size="small" effect="plain">返工</el-tag>
              <span class="rework-tip">
                {{ reworkOf(row)?.processName || '返工工序' }} ·
                {{ reworkOf(row)?.statusText || '' }}
              </span>
              <div v-if="reworkOf(row)?.reinspectionLotNo" class="rework-tip">
                复检批 {{ reworkOf(row)?.reinspectionLotNo }}
              </div>
            </div>
            <div v-else-if="row.reinspectionLotId" class="rework-tip">
              复检批 #{{ row.reinspectionLotId }}
            </div>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="resultRemark" label="说明" min-width="220" />
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              v-if="row.actionType === 'REWORK' && row.status !== 'DONE'"
              link
              type="primary"
              size="small"
              @click="completeAction(row)"
              >推进返工闭环</el-button
            >
            <el-button
              v-if="row.actionType === 'REWORK' && row.status !== 'DONE' && current?.orderId"
              link
              type="warning"
              size="small"
              @click="openSupplement(row)"
              >补料</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="supplementVisible" title="返工补料" width="760px" append-to-body>
      <el-alert title="补料不占 BOM 剩余定额；提交即记录当前用户为审批人，并生成待仓库发料单。" type="warning" :closable="false" />
      <el-table :data="supplementItems" border size="small" style="margin-top: 12px">
        <el-table-column prop="materialCode" label="物料编码" width="140" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column prop="available" label="可用库存" width="100" align="right" />
        <el-table-column label="补料数量" width="150">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :max="Number(row.available || 0)" :precision="4" />
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="supplementVisible = false">取消</el-button>
        <el-button type="primary" :loading="supplementing" @click="submitSupplement">确认并生成补料单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { qualityNcrApi, type QualityNcr, type QualityNcrAction } from '@/api/quality/lot'
import { standardProcessApi } from '@/api/product/standardProcess'
import type { StandardProcessItem } from '@/types/product/standardProcess'
import { outboundApi } from '@/api/inventory/outbound'
import type { PickPreviewRow } from '@/types/inventory/outbound'

const router = useRouter()
const route = useRoute()
const goIqcQuarantine = () => router.push('/inventory/iqc-quarantine')
const loading = ref(false)
const rows = ref<QualityNcr[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, lotType: '', status: '', materialCode: '' })
// dev-20260922-012（G3）：支持从检验批工作台判定后带 materialCode 跳进来，直接筛到该物料
if (route.query.materialCode) {
  query.materialCode = String(route.query.materialCode)
}
const current = ref<QualityNcr | null>(null)

const num = (value?: number | null) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const pending = (row: QualityNcr) => Number(row.defectQuantity || 0) - Number(row.disposedQuantity || 0)
const statusLabel = (status: string) =>
  ({ PENDING: '待处置', DISPOSING: '处置中', CLOSED: '已结' })[status] || status
const actionLabel = (type: string) =>
  ({ REWORK: '返工', CONCESSION: '让步接收（特采）', SCRAP: '报废' })[type] || type
const actionStatusLabel = (status: string) =>
  ({ PENDING: '待执行', PROCESSING: '执行中', DONE: '已完成' })[status] || status

const load = async (page?: number) => {
  if (page) query.pageNum = page
  loading.value = true
  try {
import { reworkTraceApi } from '@/api/production/rework'
import type { ReworkTraceVO } from '@/types/production/operationExecution'
    const res: any = await qualityNcrApi.page({ ...query })
    const data = res?.data
    rows.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : Number(data?.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载产品不良台账失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

const disposeVisible = ref(false)
const disposing = ref(false)
const standardProcesses = ref<StandardProcessItem[]>([])
const disposeForm = reactive({
  actionType: 'REWORK',
  quantity: 1,
  customerConfirmed: false,
  standardProcessId: undefined as number | undefined,
  reworkRequirement: '',
  resultRemark: '',
})
const openDispose = (row: QualityNcr) => {
  current.value = row
  disposeForm.actionType = 'REWORK'
  disposeForm.quantity = pending(row)
  disposeForm.customerConfirmed = false
  disposeForm.standardProcessId = undefined
  disposeForm.reworkRequirement = ''
  disposeForm.resultRemark = ''
  disposeVisible.value = true
}
const submitDispose = async () => {
  if (!current.value) return
  if (disposeForm.actionType === 'CONCESSION' && !disposeForm.customerConfirmed) {
    return ElMessage.warning('让步接收必须先勾选"客户已确认"')
  }
  if (disposeForm.actionType === 'REWORK' && !disposeForm.standardProcessId) {
    return ElMessage.warning('请选择返工工序')
  }
  disposing.value = true
  try {
    await qualityNcrApi.dispose(current.value.ncrId, { ...disposeForm })
    ElMessage.success('处置已登记')
    disposeVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '处置失败')
  } finally {
    disposing.value = false
  }
}

const actionsVisible = ref(false)
const actions = ref<QualityNcrAction[]>([])
const supplementVisible = ref(false)
const supplementing = ref(false)
const supplementAction = ref<QualityNcrAction | null>(null)
const supplementItems = ref<Array<PickPreviewRow & { quantity: number }>>([])
const openActions = async (row: QualityNcr) => {
  current.value = row
  try {
    const res: any = await qualityNcrApi.actions(row.ncrId)
    actions.value = res?.data || []
  } catch {
    actions.value = []
  }
  actionsVisible.value = true
}

const openSupplement = async (action: QualityNcrAction) => {
  if (!current.value?.orderId) return
  supplementAction.value = action
  try {
    const res: any = await outboundApi.pickPreview(current.value.orderId)
    supplementItems.value = (res?.data || []).map((item: PickPreviewRow) => ({ ...item, quantity: 0 }))
    supplementVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载补料物料失败')
  }
}

const submitSupplement = async () => {
  if (!current.value?.orderId || !supplementAction.value) return
  const items = supplementItems.value
    .filter((item) => Number(item.quantity) > 0)
/** 返工链进度（按 actionId 索引）—— dev-20260923-031 */
const reworkTraceMap = ref<Record<number, ReworkTraceVO>>({})
const reworkOf = (row: QualityNcrAction) =>
  row?.actionId ? reworkTraceMap.value[row.actionId] || null : null
    .map((item) => ({
      materialId: item.materialId,
      materialCode: item.materialCode,
      materialName: item.materialName,
      quantity: item.quantity,
    }))
  if (!items.length) return ElMessage.warning('请填写至少一项补料数量')
  supplementing.value = true
  try {
    // dev-20260923-031：一并取返工链进度（工序名 / 状态 / 回收数），把裸 ID 换成看得懂的一行
    reworkTraceMap.value = {}
    try {
      const trace: any = await reworkTraceApi.trace({ ncrId: row.ncrId })
      const map: Record<number, ReworkTraceVO> = {}
      ;(trace?.data || []).forEach((item: ReworkTraceVO) => {
        if (item.actionId) map[item.actionId] = item
      })
      reworkTraceMap.value = map
    } catch {
      reworkTraceMap.value = {}
    }
    await outboundApi.createReworkSupplement(current.value.orderId, current.value.ncrId, items)
    ElMessage.success('返工补料单已生成，等待仓库发料')
    supplementVisible.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '生成返工补料单失败')
  } finally {
    supplementing.value = false
  }
}
const completeAction = async (row: QualityNcrAction) => {
  try {
    const res: any = await qualityNcrApi.completeAction(row.actionId)
    ElMessage.success(res?.data?.status === 'DONE' ? '返工复检已合格，处置完成' : '返工报工已完成，已生成 FQC 复检批')
    if (current.value) openActions(current.value)
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(async () => {
  load(1)
  try {
    const res: any = await standardProcessApi.getEnabledProcesses()
    standardProcesses.value = res?.data || []
  } catch {
    standardProcesses.value = []
  }
})
</script>

<style scoped>
.scope-guide {
  margin-bottom: 16px;
}
.scope-guide__content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.sub {
  margin-left: 6px;
  color: #909399;
  font-size: 12px;
}
.tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.tip.block {
  padding-left: 110px;
  margin-left: 0;
  line-height: 1.5;
}
</style>
/* dev-20260923-031：返工进度单元格 */
.rework-cell {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.rework-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
