<template>
  <div class="production-quality">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">质量管理</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" @click="openCreate">新建检验</el-button>
        <el-button icon="Setting" @click="showSettings">检验标准</el-button>
        <el-button icon="Document" @click="handleReport">质量报告</el-button>
      </div>
    </div>

    <!-- 质量概览 -->
    <div class="quality-overview">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #67c23a">
                <el-icon><Check /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.passRate ?? '-' }}%</div>
                <div class="stat-label">综合良品率</div>
                <div class="stat-trend">合格 {{ stats.passCount ?? 0 }} 批</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #f56c6c">
                <el-icon><Close /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ failRate }}%</div>
                <div class="stat-label">不良品率</div>
                <div class="stat-trend">不合格 {{ stats.failCount ?? 0 }} 批</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #e6a23c">
                <el-icon><Warning /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalCount ?? 0 }}</div>
                <div class="stat-label">检验批次</div>
                <div class="stat-trend">待检 {{ stats.pendingCount ?? 0 }} 批</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #409eff">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.totalQty ?? 0 }}</div>
                <div class="stat-label">累计检验数量</div>
                <div class="stat-trend">通过 {{ stats.passQty ?? 0 }}</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 检验记录工作台 -->
    <div class="quality-data">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">检验记录</span>
            <div class="card-actions">
              <el-select v-model="query.typeFilter" placeholder="类型" clearable style="width: 120px" @change="loadPage(1)">
                <el-option label="FQC 完工检验" value="FQC" />
                <el-option label="IPQC 过程检验" value="IPQC" />
                <el-option label="IQC 来料检验" value="IQC" />
                <el-option label="OQC 出货检验" value="OQC" />
              </el-select>
              <el-select v-model="query.resultFilter" placeholder="结果" clearable style="width: 110px" @change="loadPage(1)">
                <el-option label="待检" value="pending" />
                <el-option label="合格" value="pass" />
                <el-option label="不合格" value="fail" />
              </el-select>
              <el-input v-model="query.orderNo" placeholder="工单编号" clearable style="width: 140px" @keyup.enter="loadPage(1)" @clear="loadPage(1)" />
              <el-button type="primary" icon="Search" @click="loadPage(1)">查询</el-button>
            </div>
          </div>
        </template>

        <el-table :data="inspectionData" style="width: 100%" v-loading="tableLoading">
          <el-table-column prop="inspectionNo" label="质检单号" width="170" show-overflow-tooltip />
          <el-table-column label="类型" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTag(row.inspectionType)" effect="dark">{{ typeLabel(row.inspectionType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="orderNo" label="工单" width="130" show-overflow-tooltip>
            <template #default="{ row }">{{ row.orderNo || '-' }}</template>
          </el-table-column>
          <el-table-column prop="processName" label="工序" min-width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ row.processName || '-' }}</template>
          </el-table-column>
          <el-table-column label="关联报工" width="90">
            <template #default="{ row }">
              <span v-if="row.workReportId">报工#{{ row.workReportId }}</span>
              <span v-else style="color: #c0c4cc">-</span>
            </template>
          </el-table-column>
          <el-table-column label="检验数量" width="90" align="right">
            <template #default="{ row }">{{ fmtQty(row.totalQty) }}</template>
          </el-table-column>
          <el-table-column label="合格数量" width="90" align="right">
            <template #default="{ row }">{{ fmtQty(row.passQty) }}</template>
          </el-table-column>
          <el-table-column label="不合格" width="90" align="right">
            <template #default="{ row }">{{ fmtQty(row.failQty) }}</template>
          </el-table-column>
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="resultTag(row.result)">{{ resultLabel(row.result) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="inspector" label="检验人" width="90">
            <template #default="{ row }">{{ row.inspector || '-' }}</template>
          </el-table-column>
          <el-table-column label="检验时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.inspectTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" min-width="310" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.result === 'pending'" v-hasPermi="['production:quality:judge']" link type="primary" size="small" @click="openJudge(row)">判定</el-button>
              <el-button v-if="row.result === 'pass' || row.result === 'fail'" v-hasPermi="['production:quality:judge']" link type="warning" size="small" @click="openReinspect(row)">复检</el-button>
              <el-button link type="info" size="small" @click="openDetail(row)">详情</el-button>
              <el-button v-if="row.inspectionType === InspectionType.FQC" link type="primary" size="small" @click="openLinkedPrint('fqc-report', row)">打印成品检验报告</el-button>
              <el-button v-if="row.inspectionType === InspectionType.FQC && row.result === InspectionResult.FAIL" link type="danger" size="small" @click="openLinkedPrint('rework-form', row)">打印返工返修单</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="query.pageNum"
            v-model:page-size="query.pageSize"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="loadPage(1)"
            @current-change="loadPage()"
          />
        </div>
      </el-card>
    </div>

    <!-- ============ 新建检验（IPQC 人工创建） ============ -->
    <el-dialog v-model="createVisible" title="新建检验（IPQC 过程检验）" width="520px" append-to-body>
      <el-form ref="createFormRef" :model="createForm" label-width="100px">
        <el-form-item label="检验类型" required>
          <el-select v-model="createForm.inspectionType" style="width: 100%">
            <el-option label="IPQC 过程检验" value="IPQC" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产工单" required>
          <el-select v-model="createForm.orderId" filterable placeholder="选择工单" style="width: 100%" @change="onOrderChange">
            <el-option v-for="o in orderOptions" :key="o.orderId" :label="`${o.orderNo}（${o.productName || ''}）`" :value="Number(o.orderId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="工序" required>
          <el-select v-model="createForm.executionId" filterable placeholder="选择工序" style="width: 100%" @change="onExecutionChange">
            <el-option v-for="e in executionOptions" :key="e.executionId" :label="`${e.processName || '工序' + e.processOrder}（序 ${e.processOrder}）`" :value="e.executionId" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联报工">
          <el-select v-model="createForm.workReportId" clearable filterable placeholder="可选：针对某次报工" style="width: 100%">
            <el-option v-for="r in reportOptions" :key="r.reportId" :label="`报工#${r.reportId}（${r.reporterName || ''}：合格${fmtQty(r.qualifiedQuantity)}/不良${fmtQty(r.defectiveQuantity)}）`" :value="r.reportId" />
          </el-select>
          <div style="font-size: 12px; color: #909399">选择报工后，后端会反查校验报工/工序/工单的一致性。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="可空" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">FQC 完工检验由最后工序完成后自动创建，无需人工创建。</div>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- ============ 判定（PENDING → PASS/FAIL） ============ -->
    <el-dialog v-model="judgeVisible" title="检验判定" width="520px" append-to-body>
      <template v-if="judgeRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="检验单号">{{ judgeRow.inspectionNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(judgeRow.inspectionType) }}</el-descriptions-item>
          <el-descriptions-item label="工单">{{ judgeRow.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ judgeRow.processName || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="100px" style="margin-top: 12px">
          <el-form-item label="检验数量" required>
            <el-input-number v-model="judgeForm.totalQty" :min="0" :precision="4" :step="1" style="width: 100%" placeholder="实际检验数量" />
          </el-form-item>
          <el-form-item label="合格数量" required>
            <el-input-number v-model="judgeForm.passQty" :min="0" :precision="4" :step="1" style="width: 100%" placeholder="质量认可合格数量" />
          </el-form-item>
          <el-form-item label="不合格数量" required>
            <el-input-number v-model="judgeForm.failQty" :min="0" :precision="4" :step="1" style="width: 100%" placeholder="质量判定不合格数量" />
          </el-form-item>
          <el-form-item label="缺陷说明">
            <el-input v-model="judgeForm.defectDesc" type="textarea" :rows="2" placeholder="不合格时填写" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="judgeForm.remark" type="textarea" :rows="2" />
          </el-form-item>
          <div style="color: #f56c6c; font-size: 12px">判定后结果不可修改；如需更正请使用复检（生成新的质检单）。</div>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="judgeVisible = false">取消</el-button>
        <el-button type="danger" :loading="judging" @click="submitJudge('FAIL')">判定不合格</el-button>
        <el-button type="success" :loading="judging" @click="submitJudge('PASS')">判定合格</el-button>
      </template>
    </el-dialog>

    <!-- ============ 详情 Drawer（基本信息 + 复检历史） ============ -->
    <el-drawer v-model="detailVisible" title="检验详情" size="560px" append-to-body>
      <template v-if="detailRow">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="检验单号">{{ detailRow.inspectionNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ typeLabel(detailRow.inspectionType) }}</el-descriptions-item>
          <el-descriptions-item label="工单">{{ detailRow.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ detailRow.processName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="关联报工">
            <span v-if="detailRow.workReportId">报工#{{ detailRow.workReportId }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ detailRow.productName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检验数量">{{ fmtQty(detailRow.totalQty) }}</el-descriptions-item>
          <el-descriptions-item label="合格数量">{{ fmtQty(detailRow.passQty) }}</el-descriptions-item>
          <el-descriptions-item label="不合格数量">{{ fmtQty(detailRow.failQty) }}</el-descriptions-item>
          <el-descriptions-item label="结果">
            <el-tag size="small" :type="resultTag(detailRow.result)">{{ resultLabel(detailRow.result) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="检验人">{{ detailRow.inspector || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检验时间">{{ fmtTime(detailRow.inspectTime) }}</el-descriptions-item>
          <el-descriptions-item label="缺陷说明" :span="2">{{ detailRow.defectDesc || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailRow.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-top: 12px">
          <el-button v-if="detailRow.inspectionType === InspectionType.FQC" type="primary" @click="openLinkedPrint('fqc-report', detailRow)">打印成品检验报告</el-button>
          <el-button v-if="detailRow.inspectionType === InspectionType.FQC && detailRow.result === InspectionResult.FAIL" type="danger" @click="openLinkedPrint('rework-form', detailRow)">打印返工返修单</el-button>
        </div>

        <div class="history-section">
          <div class="history-title">复检 / 质检历史（同工单 + 工序 + 类型）</div>
          <el-table :data="historyList" size="small" v-loading="historyLoading">
            <el-table-column prop="inspectionNo" label="质检单号" width="165" show-overflow-tooltip />
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag size="small" :type="resultTag(row.result)">{{ resultLabel(row.result) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="检验数量" width="80" align="right">
              <template #default="{ row }">{{ fmtQty(row.totalQty) }}</template>
            </el-table-column>
            <el-table-column label="检验人" prop="inspector" width="80" />
            <el-table-column label="检验时间" min-width="140">
              <template #default="{ row }">{{ fmtTime(row.inspectTime) }}</template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!historyList.length && !historyLoading" description="暂无历史记录" :image-size="50" />
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Check, Close, Warning, TrendCharts, Plus, Setting, Document } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qualityApi, type QualityVO, type QualityJudgePayload } from '@/api/production/quality'
import { getProductionOrderList } from '@/api/production/order'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { getWorkReportsByExecution } from '@/api/production/workReport'
import { InspectionResult, InspectionResultEnum, InspectionType, InspectionTypeEnum } from '@/enums/quality'

const trendTimeRange = ref('week')
const inspectionData = ref<QualityVO[]>([])
const tableLoading = ref(false)
const total = ref(0)
const stats = ref<Record<string, any>>({})

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  typeFilter: '',
  resultFilter: '',
  orderNo: '',
  executionId: undefined as number | undefined,
  orderId: undefined as number | undefined,
})

const failRate = computed(() => {
  const rate = Number(stats.value.passRate)
  return Number.isFinite(rate) ? (100 - rate).toFixed(1) : '0.0'
})

// ============ 展示辅助 ============
const typeTag = (t?: string) => t ? InspectionTypeEnum.getTagProps(t).type : 'info'
const typeLabel = (t?: string) => t ? InspectionTypeEnum.getLabel(t) : '-'
const resultTag = (r?: string) => r ? InspectionResultEnum.getTagProps(r).type : 'info'
const resultLabel = (r?: string) => r ? InspectionResultEnum.getLabel(r) : '-'
function fmtQty(v?: number | null): string {
  if (v === null || v === undefined) return '0'
  const n = Number(v)
  return Number.isNaN(n) ? '0' : String(n)
}
function fmtTime(t?: string): string {
  return t ? String(t).replace('T', ' ').slice(0, 16) : '-'
}

// ============ 列表加载 ============
async function loadPage(pageNum?: number) {
  if (pageNum) query.pageNum = pageNum
  tableLoading.value = true
  try {
    const params: any = {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      inspectionType: query.typeFilter || undefined,
      result: query.resultFilter || undefined,
      orderNo: query.orderNo || undefined,
      executionId: query.executionId || undefined,
      orderId: query.orderId || undefined,
    }
    const res: any = await qualityApi.page(params)
    const data = res?.data
    inspectionData.value = data?.records || data?.list || []
    total.value = data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载质检记录失败')
  } finally {
    tableLoading.value = false
  }
}

async function loadStats() {
  try {
    const res: any = await qualityApi.getStatistics()
    if (res?.code === 200 || res?.code === 0) stats.value = res.data || {}
  } catch { /* 统计失败不阻塞 */ }
}

// ============ 新建检验（IPQC） ============
const createVisible = ref(false)
const creating = ref(false)
const orderOptions = ref<any[]>([])
const executionOptions = ref<any[]>([])
const reportOptions = ref<any[]>([])
const createForm = reactive({
  inspectionType: 'IPQC',
  orderId: undefined as number | undefined,
  executionId: undefined as number | undefined,
  workReportId: undefined as number | undefined,
  remark: '',
})

async function openCreate() {
  Object.assign(createForm, { inspectionType: 'IPQC', orderId: undefined, executionId: undefined, workReportId: undefined, remark: '' })
  executionOptions.value = []
  reportOptions.value = []
  createVisible.value = true
  try {
    const res: any = await getProductionOrderList({ pageNum: 1, pageSize: 200 })
    orderOptions.value = res?.data || []
  } catch { orderOptions.value = [] }
}

async function onOrderChange(orderId: number) {
  createForm.executionId = undefined
  createForm.workReportId = undefined
  reportOptions.value = []
  try {
    const res: any = await operationExecutionApi.list({ orderId, pageNum: 1, pageSize: 200 })
    executionOptions.value = res?.data?.records || res?.data || []
  } catch { executionOptions.value = [] }
}

async function onExecutionChange(executionId: number) {
  createForm.workReportId = undefined
  try {
    const res: any = await getWorkReportsByExecution(executionId)
    reportOptions.value = res?.data || []
  } catch { reportOptions.value = [] }
}

async function submitCreate() {
  if (!createForm.orderId) { ElMessage.warning('请选择生产工单'); return }
  if (!createForm.executionId) { ElMessage.warning('请选择工序'); return }
  creating.value = true
  try {
    await qualityApi.createInspection({
      inspectionType: createForm.inspectionType,
      orderId: createForm.orderId,
      executionId: createForm.executionId,
      workReportId: createForm.workReportId,
      remark: createForm.remark || undefined,
    })
    ElMessage.success('已创建 IPQC 质检单')
    createVisible.value = false
    loadPage(1)
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '创建失败')
  } finally {
    creating.value = false
  }
}

// ============ 判定 ============
const judgeVisible = ref(false)
const judging = ref(false)
const judgeRow = ref<QualityVO | null>(null)
const judgeForm = reactive<QualityJudgePayload>({ result: 'PASS', totalQty: 0, passQty: 0, failQty: 0, defectDesc: '', remark: '' })

function openJudge(row: QualityVO) {
  judgeRow.value = row
  Object.assign(judgeForm, { result: 'PASS', totalQty: 0, passQty: 0, failQty: 0, defectDesc: '', remark: '' })
  judgeVisible.value = true
}

async function submitJudge(result: 'PASS' | 'FAIL') {
  const row = judgeRow.value
  if (!row) return
  const t = Number(judgeForm.totalQty || 0)
  const p = Number(judgeForm.passQty || 0)
  const f = Number(judgeForm.failQty || 0)
  if (t < 0 || p < 0 || f < 0) { ElMessage.warning('数量不能为负数'); return }
  if (p + f > t) { ElMessage.warning('合格+不合格数量不能超过检验数量'); return }
  if (result === 'PASS' && p <= 0) { ElMessage.warning('判定合格时合格数量必须大于 0'); return }
  judging.value = true
  try {
    await qualityApi.judge(row.inspectionId, { ...judgeForm, result })
    ElMessage.success(result === 'PASS' ? '判定合格' : '判定不合格')
    judgeVisible.value = false
    loadPage()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '判定失败')
  } finally {
    judging.value = false
  }
}

// ============ 复检 ============
async function openReinspect(row: QualityVO) {
  try {
    await ElMessageBox.confirm(
      `将为「${row.inspectionNo}」创建一张新的复检质检单（原记录 ${resultLabel(row.result)} 保留不动），是否继续？`,
      '复检确认',
      { type: 'warning', confirmButtonText: '创建复检' },
    )
  } catch { return }
  try {
    await qualityApi.reinspect(row.inspectionId)
    ElMessage.success('已创建复检单（PENDING）')
    loadPage()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '复检失败')
  }
}

// ============ 详情 Drawer + 复检历史 ============
const detailVisible = ref(false)
const detailRow = ref<QualityVO | null>(null)
const historyList = ref<QualityVO[]>([])
const historyLoading = ref(false)

async function openDetail(row: QualityVO) {
  detailRow.value = row
  detailVisible.value = true
  historyLoading.value = true
  historyList.value = []
  try {
    // 同工单 + 工序 + 类型的历史质检记录（复检链）
    const params: any = {
      pageNum: 1,
      pageSize: 50,
      inspectionType: row.inspectionType,
      orderId: row.orderId,
      executionId: row.executionId,
    }
    const res: any = await qualityApi.page(params)
    historyList.value = res?.data?.records || res?.data?.list || []
  } catch {
    historyList.value = []
  } finally {
    historyLoading.value = false
  }
}

// ============ 其他（保留现状） ============
const showSettings = () => {
  ElMessage.info('检验标准设置：P3 范围外，暂未开放')
}
const router = useRouter()
const openLinkedPrint = (page: 'fqc-report' | 'rework-form', row: QualityVO) => {
  window.open(router.resolve({ path: `/production/quality-print/${page}`, query: { inspectionId: row.inspectionId } }).href, '_blank')
}
const handleReport = () => {
  router.push('/production/quality/report')
}
const viewAllInspections = () => { loadPage(1) }

onMounted(() => {
  // P3-D：支持从 Execution 页面带 executionId/orderId 跳转过滤
  const route = useRoute()
  if (route.query.executionId) query.executionId = Number(route.query.executionId)
  if (route.query.orderId) query.orderId = Number(route.query.orderId)
  loadPage(1)
  loadStats()
})
</script>

<style scoped>
.production-quality { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; }
.page-title { font-size: 24px; font-weight: 600; color: #303133; margin: 0; }
.quality-overview { margin-bottom: 24px; }
.stat-card { border-radius: 8px; }
.stat-content { display: flex; align-items: center; }
.stat-icon { width: 48px; height: 48px; border-radius: 8px; display: flex; align-items: center; justify-content: center; margin-right: 16px; }
.stat-icon .el-icon { font-size: 24px; color: white; }
.stat-info { flex: 1; }
.stat-value { font-size: 24px; font-weight: 600; color: #303133; line-height: 1.2; }
.stat-label { font-size: 14px; color: #909399; margin-top: 4px; }
.stat-trend { font-size: 12px; margin-top: 2px; }
.section-card { border-radius: 8px; margin-bottom: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.card-title { font-size: 18px; font-weight: 600; color: #303133; }
.card-actions { display: flex; gap: 8px; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.history-section { margin-top: 18px; }
.history-title { font-size: 14px; font-weight: 600; color: #606266; margin-bottom: 8px; }
</style>
