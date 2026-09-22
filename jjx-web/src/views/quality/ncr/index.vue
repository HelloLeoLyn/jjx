<template>
  <div class="ncr-page">
    <el-alert type="info" :closable="false" show-icon class="scope-guide">
      <template #title>
        <div class="scope-guide__content">
          <span>本页主要处理成品检验批不良：返工、让步接收（特采）或报废。成品让步接收必须取得客户确认。</span>
          <el-button link type="primary" @click="goIqcQuarantine">查看来料不合格品处置</el-button>
        </div>
      </template>
    </el-alert>
    <el-card>
      <template #header>
        <div class="header">
          <span>不良台账</span>
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
        <el-table-column label="返工/复检关联" min-width="150">
          <template #default="{ row }">
            <div v-if="row.reworkExecutionId">工序 #{{ row.reworkExecutionId }}</div>
            <div v-if="row.reinspectionLotId">复检批 #{{ row.reinspectionLotId }}</div>
            <span v-if="!row.reworkExecutionId && !row.reinspectionLotId">-</span>
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
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { qualityNcrApi, type QualityNcr, type QualityNcrAction } from '@/api/quality/lot'

const router = useRouter()
const goIqcQuarantine = () => router.push('/inventory/iqc-quarantine')
const loading = ref(false)
const rows = ref<QualityNcr[]>([])
const total = ref(0)
const query = reactive({ pageNum: 1, pageSize: 10, lotType: '', status: '', materialCode: '' })
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
    const res: any = await qualityNcrApi.page({ ...query })
    const data = res?.data
    rows.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : Number(data?.total || 0)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载不良台账失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

const disposeVisible = ref(false)
const disposing = ref(false)
const disposeForm = reactive({ actionType: 'REWORK', quantity: 1, customerConfirmed: false, resultRemark: '' })
const openDispose = (row: QualityNcr) => {
  current.value = row
  disposeForm.actionType = 'REWORK'
  disposeForm.quantity = pending(row)
  disposeForm.customerConfirmed = false
  disposeForm.resultRemark = ''
  disposeVisible.value = true
}
const submitDispose = async () => {
  if (!current.value) return
  if (disposeForm.actionType === 'CONCESSION' && !disposeForm.customerConfirmed) {
    return ElMessage.warning('让步接收必须先勾选"客户已确认"')
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

onMounted(() => load(1))
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
