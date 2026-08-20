<template>
  <div class="execution-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">工序执行</h1>
    </div>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-tabs v-model="activeTab" @tab-change="handleTabChange">
          <el-tab-pane label="全部任务" name="all" />
          <el-tab-pane label="我的当前任务" name="mine" />
        </el-tabs>
        <el-input v-model="queryParams.orderNo" placeholder="工单编号" clearable style="width: 150px" @keyup.enter="handleQuery" @clear="handleQuery" />
        <el-input v-model="queryParams.processName" placeholder="工序" clearable style="width: 120px" @keyup.enter="handleQuery" @clear="handleQuery" />
        <el-select v-model="queryParams.executionStatus" placeholder="状态" clearable style="width: 120px" @change="handleQuery">
          <el-option v-for="s in STATUS_ITEMS" :key="s.value" :label="s.label" :value="String(s.value)" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 主表 -->
    <el-card class="list-card" shadow="never">
      <el-table v-loading="loading" :data="executionList" style="width: 100%">
        <el-table-column prop="orderNo" label="工单编号" width="180" show-overflow-tooltip />
        <el-table-column label="工序" min-width="130">
          <template #default="{ row }">
            <span>{{ row.processName || '-' }}</span>
            <div v-if="row.processOrder" style="font-size: 12px; color: #909399">序 {{ row.processOrder }}</div>
          </template>
        </el-table-column>
        <!-- P2-D：当前责任人（P1 ACTIVE DispatchNode projection，不用 operatorName） -->
        <el-table-column label="当前责任人" min-width="120">
          <template #default="{ row }">
            <span v-if="row.currentAssigneeName" class="cur-assignee">{{ row.currentAssigneeName }}</span>
            <el-tag v-else size="small" type="info" effect="plain">待派工</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="设备" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.equipmentName">{{ row.equipmentName }}</span>
            <span v-else style="color: #c0c4cc">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="计划数量" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.inputQuantity) }}</template>
        </el-table-column>
        <!-- P2-D：累计投影（WorkReport projection，后端提供） -->
        <el-table-column label="累计合格" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="累计不良" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="累计产出" width="90" align="right">
          <template #default="{ row }">{{ fmtQty(row.outputQuantity) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.executionStatus)">{{ statusLabel(row.executionStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="300" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.executionStatus === 0" type="success" link icon="PlayCircle" v-hasPermi="['production:operation-execution:edit']" @click="handleStart(row)">开始</el-button>
            <el-button v-if="row.executionStatus === 2" type="warning" link icon="Pause" v-hasPermi="['production:operation-execution:edit']" @click="handlePause(row)">暂停</el-button>
            <!-- P2-D 报工：仅 EXECUTING 且 canReport（ACTIVE assignee + add 权限） -->
            <el-button v-if="row.executionStatus === 2 && row.canReport" type="primary" link icon="EditPen" @click="openReport(row)">报工</el-button>
            <el-button v-if="row.executionStatus === 2" type="primary" link icon="View" @click="handleView(row)">详情</el-button>
            <el-button v-if="[2, 3].includes(row.executionStatus)" type="success" link icon="Check" v-hasPermi="['production:operation-execution:edit']" @click="handleComplete(row)">完成</el-button>
            <el-button v-if="[2, 4].includes(row.executionStatus)" type="warning" link icon="WarningFilled" v-hasPermi="['production:quality:view']" @click="handleQualityCheck(row)">首检/巡检</el-button>
            <el-button v-if="row.executionId" type="info" link icon="List" v-hasPermi="['production:quality:view']" @click="goQualityRecords(row)">质检记录</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="getList"
          @current-change="getList"
        />
      </div>
    </el-card>

    <!-- ============ P2-D 报工 Drawer ============ -->
    <el-drawer v-model="reportVisible" title="生产报工" size="520px" append-to-body>
      <template v-if="reportRow">
        <!-- 只读生产上下文 -->
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单">{{ reportRow.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ reportRow.processName }}（序 {{ reportRow.processOrder }}）</el-descriptions-item>
          <el-descriptions-item label="当前责任人">
            <span class="cur-assignee">{{ reportRow.currentAssigneeName }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="责任组织">{{ reportRow.currentOrgName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="默认设备">{{ reportRow.equipmentName || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(reportRow.executionStatus) }}</el-descriptions-item>
        </el-descriptions>

        <!-- 生产进度（只读投影） -->
        <div class="progress-section">
          <div class="progress-title">生产进度（由报工记录自动汇总）</div>
          <el-descriptions :column="4" size="small">
            <el-descriptions-item label="计划">{{ fmtQty(reportRow.inputQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="累计合格">{{ fmtQty(reportRow.qualifiedQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="累计不良">{{ fmtQty(reportRow.defectiveQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="累计产出">{{ fmtQty(reportRow.outputQuantity) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 本次报工表单 -->
        <el-form ref="reportFormRef" :model="reportForm" label-width="100px" style="margin-top: 12px">
          <el-form-item label="合格数量" required>
            <el-input-number v-model="reportForm.qualifiedQuantity" :min="0" :precision="4" style="width: 100%" placeholder="本次合格数量" />
          </el-form-item>
          <el-form-item label="不良数量" required>
            <el-input-number v-model="reportForm.defectiveQuantity" :min="0" :precision="4" style="width: 100%" placeholder="本次不良数量" />
          </el-form-item>
          <el-form-item v-if="reportForm.defectiveQuantity > 0" label="不良原因" required>
            <el-input v-model="reportForm.defectReason" type="textarea" :rows="2" placeholder="存在不良时必填" />
          </el-form-item>
          <el-form-item label="人工工时(h)">
            <el-input-number v-model="reportForm.laborHours" :min="0" :step="0.1" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="机器工时(h)">
            <el-input-number v-model="reportForm.machineHours" :min="0" :step="0.1" :precision="2" style="width: 100%" />
          </el-form-item>
          <el-form-item label="开始时间">
            <el-date-picker v-model="reportForm.workStartTime" type="datetime" placeholder="可空" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker v-model="reportForm.workEndTime" type="datetime" placeholder="可空" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
          </el-form-item>
          <el-form-item label="本次设备">
            <el-select v-model="reportForm.equipmentId" placeholder="本次实际使用设备（空=默认设备）" clearable filterable style="width: 100%">
              <el-option v-for="eq in equipmentOptions" :key="eq.equipmentId" :label="`${eq.equipmentName}（${eq.equipmentNo}）`" :value="eq.equipmentId" />
            </el-select>
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="reportForm.remark" type="textarea" :rows="2" />
          </el-form-item>
          <div style="color: #909399; font-size: 12px">每次报工为一条不可覆盖的生产事实；报错后可在报工历史中撤销并重新报工。超计划报工允许，确认后提交。</div>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="reportVisible = false">取消</el-button>
        <el-button type="primary" :loading="reporting" @click="handleSubmitReport">确认报工</el-button>
      </template>
    </el-drawer>

    <!-- ============ 详情 Drawer（Tabs） ============ -->
    <el-drawer v-model="detailOpen" title="工序执行详情" size="560px" append-to-body>
      <el-tabs v-model="detailTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="base">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工单">{{ detailForm.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="工序">{{ detailForm.processName }}（序 {{ detailForm.processOrder }}）</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detailForm.executionStatus) }}</el-descriptions-item>
            <el-descriptions-item label="当前责任人">
              <span v-if="detailForm.currentAssigneeName" class="cur-assignee">{{ detailForm.currentAssigneeName }}</span>
              <span v-else style="color: #c0c4cc">待派工</span>
            </el-descriptions-item>
            <el-descriptions-item label="设备">{{ detailForm.equipmentName || '不限' }}</el-descriptions-item>
            <el-descriptions-item label="操作员（旧）">{{ detailForm.operatorName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="计划数量">{{ fmtQty(detailForm.inputQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="累计合格">{{ fmtQty(detailForm.qualifiedQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="累计不良">{{ fmtQty(detailForm.defectiveQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="累计产出">{{ fmtQty(detailForm.outputQuantity) }}</el-descriptions-item>
            <el-descriptions-item label="人工工时">{{ fmtQty(detailForm.actualLaborHours) }}h</el-descriptions-item>
            <el-descriptions-item label="机器工时">{{ fmtQty(detailForm.actualMachineHours) }}h</el-descriptions-item>
            <el-descriptions-item label="开始时间" :span="2">{{ fmtTime(detailForm.actualStartTime) }}</el-descriptions-item>
            <el-descriptions-item label="完成时间" :span="2">{{ fmtTime(detailForm.actualEndTime) }}</el-descriptions-item>
          </el-descriptions>
          <div style="color: #909399; font-size: 12px; margin-top: 8px">数量/工时由报工记录自动汇总，不可直接编辑。</div>
        </el-tab-pane>

        <!-- 报工记录 -->
        <el-tab-pane label="报工记录" name="reports">
          <el-table v-loading="reportsLoading" :data="reportList" size="small">
            <el-table-column label="报工时间" width="120">
              <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
            </el-table-column>
            <el-table-column label="报工人" prop="reporterName" width="90" />
            <el-table-column label="合格" width="70" align="right">
              <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
            </el-table-column>
            <el-table-column label="不良" width="70" align="right">
              <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.reportStatus === 'CANCELLED'" size="small" type="danger" effect="plain">已撤销</el-tag>
                <el-tag v-else size="small" type="success" effect="plain">已提交</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="90">
              <template #default="{ row }">
                <el-button link size="small" type="primary" @click="openReportDetail(row)">详情</el-button>
                <el-button v-if="canCancelReport(row)" link size="small" type="danger" @click="openCancelReport(row)">撤销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!reportList.length" description="暂无报工记录" :image-size="50" />
        </el-tab-pane>

        <!-- 操作记录（现有 Timeline 能力，P2-D 不接线） -->
        <el-tab-pane label="操作记录" name="ops">
          <el-empty description="操作记录（P4 Trace 统一接线）" :image-size="50" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <!-- 报工详情弹窗 -->
    <el-dialog v-model="reportDetailVisible" title="报工详情" width="460px" append-to-body>
      <el-descriptions v-if="reportDetail" :column="1" border size="small">
        <el-descriptions-item label="报工ID">{{ reportDetail.reportId }}</el-descriptions-item>
        <el-descriptions-item label="报工人">{{ reportDetail.reporterName }}</el-descriptions-item>
        <el-descriptions-item label="合格数量">{{ fmtQty(reportDetail.qualifiedQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="不良数量">{{ fmtQty(reportDetail.defectiveQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="不良原因">{{ reportDetail.defectReason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="人工工时">{{ fmtQty(reportDetail.laborHours) }}h</el-descriptions-item>
        <el-descriptions-item label="机器工时">{{ fmtQty(reportDetail.machineHours) }}h</el-descriptions-item>
        <el-descriptions-item label="设备">{{ reportDetail.equipmentName || '不限' }}</el-descriptions-item>
        <el-descriptions-item label="生产区间">{{ fmtTime(reportDetail.workStartTime) }} ~ {{ fmtTime(reportDetail.workEndTime) }}</el-descriptions-item>
        <el-descriptions-item label="报工时间">{{ fmtTime(reportDetail.reportTime) }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ reportDetail.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ reportDetail.reportStatusLabel }}</el-descriptions-item>
        <template v-if="reportDetail.reportStatus === 'CANCELLED'">
          <el-descriptions-item label="撤销人">{{ reportDetail.cancelledByName }}</el-descriptions-item>
          <el-descriptions-item label="撤销时间">{{ fmtTime(reportDetail.cancelledAt) }}</el-descriptions-item>
          <el-descriptions-item label="撤销原因">{{ reportDetail.cancelReason }}</el-descriptions-item>
        </template>
      </el-descriptions>
      <div style="color: #909399; font-size: 12px; margin-top: 8px">报工为不可覆盖的生产事实，不允许编辑。</div>
    </el-dialog>

    <!-- 撤销确认弹窗 -->
    <el-dialog v-model="cancelVisible" title="撤销报工" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="报工">
          <span>{{ cancelTarget?.reporterName }}：合格 {{ fmtQty(cancelTarget?.qualifiedQuantity) }} / 不良 {{ fmtQty(cancelTarget?.defectiveQuantity) }}</span>
        </el-form-item>
        <el-form-item label="撤销原因" required>
          <el-input v-model="cancelReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
        <div style="color: #f56c6c; font-size: 12px">撤销后该报工不计入累计，但历史保留（显示已撤销）。</div>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" :loading="cancelling" @click="handleCancelReport">确认撤销</el-button>
      </template>
    </el-dialog>

    <!-- 质检（保留现状） -->
    <el-dialog v-model="qcVisible" title="质量检查" width="480px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="检查类型">
          <el-radio-group v-model="qcForm.checkType">
            <el-radio value="first_piece">首检</el-radio>
            <el-radio value="patrol">巡检</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="抽检数量">
          <el-input-number v-model="qcForm.checkQty" :min="1" />
        </el-form-item>
        <el-form-item label="合格数量">
          <el-input-number v-model="qcForm.passQty" :min="0" :max="qcForm.checkQty" />
        </el-form-item>
        <el-form-item label="结果">
          <el-radio-group v-model="qcForm.result">
            <el-radio value="pass">✅ 合格</el-radio>
            <el-radio value="fail">❌ 不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="不良描述">
          <el-input v-model="qcForm.defectDesc" type="textarea" :rows="2" placeholder="不合格时描述" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQc">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { getEquipmentList } from '@/api/production/equipment'
import {
  submitWorkReport,
  cancelWorkReport,
  getWorkReportsByExecution,
  type WorkReportVO,
  type WorkReportSubmitPayload,
} from '@/api/production/workReport'
import type { OperationExecutionVO, OperationExecutionQuery } from '@/types/production/operationExecution'

defineOptions({ name: 'ProductionExecutionList' })

const STATUS_LABELS: Record<number, string> = {
  0: '待执行', 1: '准备中', 2: '执行中', 3: '已暂停', 4: '已完成',
  5: '已跳过', 6: '已取消', 7: '已超期', 8: '异常中', 9: '待确认',
}
const STATUS_ITEMS = Object.entries(STATUS_LABELS).map(([v, label]) => ({ value: Number(v), label }))

function statusLabel(s?: number): string {
  return STATUS_LABELS[s ?? 0] || String(s ?? 0)
}
function statusTag(s?: number): any {
  return { 0: 'info', 1: 'warning', 2: 'success', 3: 'warning', 4: 'success', 6: 'danger' }[s ?? 0] || 'info'
}
function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '0'
  return String(Number(v))
}
function fmtTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

const loading = ref(false)
const executionList = ref<OperationExecutionVO[]>([])
const total = ref(0)
const activeTab = ref('mine')
const queryParams = reactive<OperationExecutionQuery & { scope?: string }>({
  orderNo: '', processName: '', executionStatus: '', operatorName: '',
  pageNum: 1, pageSize: 10, scope: '',
})

const getList = async () => {
  loading.value = true
  try {
    queryParams.scope = activeTab.value === 'mine' ? 'mine' : ''
    const res: any = await operationExecutionApi.list(queryParams)
    const data = res?.data
    executionList.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch {
    executionList.value = []
  } finally {
    loading.value = false
  }
}
const handleTabChange = () => { queryParams.pageNum = 1; getList() }
const handleQuery = () => { queryParams.pageNum = 1; getList() }
const handleReset = () => {
  Object.assign(queryParams, { orderNo: '', processName: '', executionStatus: '', pageNum: 1 })
  getList()
}

// ============ 开始/暂停/恢复/完成 ============
const handleStart = async (row: OperationExecutionVO) => {
  try { await operationExecutionApi.start(row.executionId!); ElMessage.success('已开始'); getList() }
  catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}
const handlePause = async (row: OperationExecutionVO) => {
  try { await operationExecutionApi.pause(row.executionId!); ElMessage.success('已暂停'); getList() }
  catch (e: any) { ElMessage.error(e?.message || '操作失败') }
}

// P2-D 完成提示：0 报工 / 低于计划 / 超计划 warning（后端 gate 为准）
const handleComplete = async (row: OperationExecutionVO) => {
  const qualified = Number(row.qualifiedQuantity || 0)
  const defective = Number(row.defectiveQuantity || 0)
  const planned = Number(row.inputQuantity || 0)
  let msg = `确定完成「${row.processName}」吗？`
  if (qualified === 0 && defective === 0) {
    ElMessage.warning('当前工序尚无有效报工记录，不能完成')
    return
  }
  if (planned > 0 && qualified < planned) {
    msg = `计划数量：${planned}，累计合格：${qualified}，累计不良：${defective}\n当前合格数量低于计划数量，是否仍确认完成？`
  } else if (planned > 0 && qualified > planned) {
    msg = `实际累计产出超过计划数量（计划 ${planned}，累计 ${qualified}），是否确认完成？`
  }
  try {
    await ElMessageBox.confirm(msg, '完成工序', { type: 'info', confirmButtonText: '确认完成' })
  } catch { return }
  try {
    await operationExecutionApi.complete(row.executionId!)
    ElMessage.success('工序已完成；若为最后工序将自动生成完工检验，等待质检')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ============ 报工 Drawer ============
const reportVisible = ref(false)
const reporting = ref(false)
const reportRow = ref<OperationExecutionVO | null>(null)
const reportFormRef = ref()
const equipmentOptions = ref<any[]>([])
const reportForm = reactive<WorkReportSubmitPayload>({
  executionId: 0, qualifiedQuantity: 0, defectiveQuantity: 0,
  laborHours: 0, machineHours: 0, workStartTime: undefined, workEndTime: undefined,
  equipmentId: undefined, defectReason: '', remark: '',
})

const openReport = async (row: OperationExecutionVO) => {
  reportRow.value = row
  Object.assign(reportForm, {
    executionId: row.executionId, qualifiedQuantity: 0, defectiveQuantity: 0,
    laborHours: 0, machineHours: 0, workStartTime: undefined, workEndTime: undefined,
    equipmentId: row.equipmentId || undefined, defectReason: '', remark: '',
  })
  reportVisible.value = true
  if (!equipmentOptions.value.length) {
    try { const res: any = await getEquipmentList({}); equipmentOptions.value = res?.data || [] } catch { equipmentOptions.value = [] }
  }
}

const handleSubmitReport = async () => {
  if (!reportForm.executionId) return
  const q = Number(reportForm.qualifiedQuantity || 0)
  const d = Number(reportForm.defectiveQuantity || 0)
  if (q < 0 || d < 0) { ElMessage.warning('数量不能为负数'); return }
  if (q + d <= 0) { ElMessage.warning('本次报工合格与不良数量之和必须大于 0'); return }
  if (d > 0 && !reportForm.defectReason?.trim()) { ElMessage.warning('存在不良数量时，不良原因必填'); return }
  if (reportForm.workStartTime && !reportForm.workEndTime || !reportForm.workStartTime && reportForm.workEndTime) {
    ElMessage.warning('生产开始/结束时间需同时填写'); return
  }
  if (reportForm.workStartTime && reportForm.workEndTime && reportForm.workEndTime < reportForm.workStartTime) {
    ElMessage.warning('结束时间不能早于开始时间'); return
  }
  // 超计划确认（后端允许）
  const planned = Number(reportRow.value?.inputQuantity || 0)
  const curQ = Number(reportRow.value?.qualifiedQuantity || 0)
  const curD = Number(reportRow.value?.defectiveQuantity || 0)
  if (planned > 0 && curQ + curD + q + d > planned) {
    try {
      await ElMessageBox.confirm(`本次报工后累计产出将超过计划数量 ${planned}，是否继续？`, '超计划提示', { type: 'warning' })
    } catch { return }
  }
  reporting.value = true
  try {
    await submitWorkReport({ ...reportForm })
    ElMessage.success('报工成功')
    reportVisible.value = false
    getList()
    if (detailOpen.value && detailTab.value === 'reports') loadReports()
  } catch (e: any) {
    ElMessage.error(e?.message || '报工失败')
  } finally {
    reporting.value = false
  }
}

// ============ 详情 Drawer ============
const detailOpen = ref(false)
const detailTab = ref('base')
const detailForm = reactive<Record<string, any>>({})

const handleView = async (row: OperationExecutionVO) => {
  Object.assign(detailForm, row)
  detailTab.value = 'base'
  detailOpen.value = true
  loadReports()
}

// ============ 报工历史 ============
const reportsLoading = ref(false)
const reportList = ref<WorkReportVO[]>([])

const loadReports = async () => {
  if (!detailForm.executionId) return
  reportsLoading.value = true
  try {
    const res: any = await getWorkReportsByExecution(detailForm.executionId)
    reportList.value = res?.data || []
  } catch { reportList.value = [] } finally { reportsLoading.value = false }
}

const canCancelReport = (row: WorkReportVO) => {
  return row.reportStatus === 'SUBMITTED'
}

// 报工详情
const reportDetailVisible = ref(false)
const reportDetail = ref<WorkReportVO | null>(null)
const openReportDetail = (row: WorkReportVO) => {
  reportDetail.value = row
  reportDetailVisible.value = true
}

// 撤销
const cancelVisible = ref(false)
const cancelling = ref(false)
const cancelTarget = ref<WorkReportVO | null>(null)
const cancelReason = ref('')

const openCancelReport = (row: WorkReportVO) => {
  cancelTarget.value = row
  cancelReason.value = ''
  cancelVisible.value = true
}

const handleCancelReport = async () => {
  if (!cancelReason.value.trim()) { ElMessage.warning('撤销原因必填'); return }
  cancelling.value = true
  try {
    await cancelWorkReport(cancelTarget.value!.reportId, { cancelReason: cancelReason.value.trim() })
    ElMessage.success('已撤销')
    cancelVisible.value = false
    loadReports()
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '撤销失败')
  } finally { cancelling.value = false }
}

// P3-D：跳转质检管理页并按当前工序过滤（FQC/IPQC 记录）
const goQualityRecords = (row: OperationExecutionVO) => {
  const query: Record<string, string> = {}
  if (row.executionId) query.executionId = String(row.executionId)
  if (row.orderId) query.orderId = String(row.orderId)
  const router = useRouter()
  router.push({ path: '/production/quality', query })
}

// ============ 质检（保留现状） ============
const qcVisible = ref(false)
const qcForm = reactive({ checkType: 'first_piece', checkQty: 5, passQty: 5, result: 'pass', defectDesc: '', inspector: '' })
let qcCurrentRow: any = null

const handleQualityCheck = (row: any) => {
  qcCurrentRow = row
  Object.assign(qcForm, { checkType: 'first_piece', checkQty: 5, passQty: 5, result: 'pass', defectDesc: '' })
  qcVisible.value = true
}

const submitQc = async () => {
  if (qcForm.passQty > qcForm.checkQty) { ElMessage.warning('合格数量不能大于抽检数量'); return }
  if (!qcCurrentRow?.executionId) return
  try {
    const checkResult = qcForm.result === 'pass' ? 'PASS' : 'FAIL'
    const checkItems = `抽检${qcForm.checkQty}件/合格${qcForm.passQty}件${qcForm.defectDesc ? '/' + qcForm.defectDesc : ''}`
    await operationExecutionApi.qualityCheck(
      qcCurrentRow.executionId,
      qcForm.checkType === 'first_piece' ? 'FIRST' : 'PATROL',
      checkResult, checkItems, qcForm.defectDesc || undefined,
    )
    ElMessage.success('质检完成')
    qcVisible.value = false
    if (qcForm.result === 'fail') ElMessage.warning('不合格，工序已自动暂停，请排查问题！')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '质检提交失败')
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.execution-page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.filter-card { margin-bottom: 16px; }
.filter-bar { display: flex; gap: 10px; align-items: center; padding-bottom: 8px; flex-wrap: wrap; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.cur-assignee { font-weight: 500; color: #303133; }
.progress-section { margin-top: 14px; }
.progress-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 8px; }
</style>
