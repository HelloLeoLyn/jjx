<template>
  <div class="execution-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">工序执行</h1>
      <div class="page-actions">
        <el-button icon="Document" v-hasPermi="['production:work-report:view']" @click="openMyReports"
          >我的报工</el-button
        >
        <el-button type="primary" icon="Stamp" v-hasPermi="['production:work-report:approve']" @click="openPendingApproval"
          >待我审批</el-button
        >
      </div>
    </div>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input v-model="queryParams.orderNo" placeholder="工单编号" clearable style="width: 150px" @keyup.enter="handleQuery" @clear="handleQuery" />
        <el-input v-model="queryParams.processName" placeholder="工序" clearable style="width: 120px" @keyup.enter="handleQuery" @clear="handleQuery" />
        <el-select v-model="queryParams.executionStatus" placeholder="状态" clearable style="width: 120px" @change="handleQuery">
          <el-option v-for="s in STATUS_ITEMS" :key="s.value" :label="s.label" :value="String(s.value)" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 主表：一行一道 Execution -->
    <el-card class="list-card" shadow="never">
      <el-table v-loading="loading" :data="executionList" style="width: 100%">
        <el-table-column prop="orderNo" label="工单编号" width="180" show-overflow-tooltip />
        <el-table-column label="工序" min-width="130">
          <template #default="{ row }">
            <span>{{ row.processName || '-' }}</span>
            <div v-if="row.processOrder" style="font-size: 12px; color: #909399">序 {{ row.processOrder }}</div>
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
        <!-- 累计投影（WorkReport projection，后端提供） -->
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
            <el-button v-if="row.executionStatus === 2" type="primary" link icon="EditPen" v-hasPermi="['production:work-report:add']" @click="openReportDialog(row)">报工</el-button>
            <el-button v-if="row.executionStatus === 2" type="warning" link icon="Pause" v-hasPermi="['production:operation-execution:edit']" @click="handlePause(row)">暂停</el-button>
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

    <!-- ============ 详情 Drawer（Tabs） ============ -->
    <el-drawer v-model="detailOpen" title="工序执行详情" size="560px" append-to-body>
      <el-tabs v-model="detailTab">
        <!-- 基本信息 -->
        <el-tab-pane label="基本信息" name="base">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="工单">{{ detailForm.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="工序">{{ detailForm.processName }}（序 {{ detailForm.processOrder }}）</el-descriptions-item>
            <el-descriptions-item label="状态">{{ statusLabel(detailForm.executionStatus) }}</el-descriptions-item>
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
                <el-tag v-else-if="row.reportStatus === 'REJECTED'" size="small" type="warning" effect="plain">已驳回</el-tag>
                <el-tag v-else-if="row.reportStatus === 'APPROVED'" size="small" type="success" effect="plain">已通过</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">待审批</el-tag>
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
        <template v-if="reportDetail.reportStatus === 'APPROVED' || reportDetail.reportStatus === 'REJECTED'">
          <el-descriptions-item label="审批人">{{ reportDetail.reviewerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="审批时间">{{ fmtTime(reportDetail.reviewTime) }}</el-descriptions-item>
          <el-descriptions-item label="审批备注">{{ reportDetail.reviewRemark || '-' }}</el-descriptions-item>
        </template>
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

    <!-- 报工提交 Dialog（P6：仅当前 Task 执行人可报；数量 <= 任务剩余） -->
    <el-dialog v-model="reportOpen" title="报工" width="560px" append-to-body>
      <template v-if="reportExec">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="工单">{{ reportExec.orderNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ reportExec.processName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="累计合格">{{ fmtQty(reportExec.qualifiedQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="累计不良">{{ fmtQty(reportExec.defectiveQuantity) }}</el-descriptions-item>
        </el-descriptions>
        <el-form label-width="96px" style="margin-top: 12px">
          <el-form-item label="我的任务" required>
            <el-select v-model="reportTaskId" placeholder="选择本次报工对应的任务" style="width: 100%" :loading="reportTaskLoading">
              <el-option
                v-for="t in reportTasks"
                :key="t.taskId"
                :value="t.taskId"
                :label="`${t.assigneeName || '我'} · 剩余 ${fmtQty(t.remainingQuantity)}${t.parentAssigneeName ? `（来源：${t.parentAssigneeName}）` : ''}`"
              />
            </el-select>
            <div class="text-muted tip">报工必须绑定本人持有且处于责任执行中的任务；数量上限 = 任务剩余。</div>
          </el-form-item>
          <el-form-item label="合格数量" required>
            <el-input-number v-model="reportForm.qualifiedQuantity" :min="0" :precision="2" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item label="不良数量">
            <el-input-number v-model="reportForm.defectiveQuantity" :min="0" :precision="2" :step="1" style="width: 100%" />
          </el-form-item>
          <el-form-item v-if="Number(reportForm.defectiveQuantity) > 0" label="不良原因" required>
            <el-input v-model="reportForm.defectReason" type="textarea" :rows="2" placeholder="不良数量大于 0 时必填" />
          </el-form-item>
          <el-form-item label="人工工时">
            <el-input-number v-model="reportForm.laborHours" :min="0" :precision="2" :step="0.5" style="width: 100%" />
          </el-form-item>
          <el-form-item label="机器工时">
            <el-input-number v-model="reportForm.machineHours" :min="0" :precision="2" :step="0.5" style="width: 100%" />
          </el-form-item>
          <el-form-item label="生产区间">
            <el-date-picker
              v-model="reportTimeRange"
              type="datetimerange"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="reportForm.remark" type="textarea" :rows="2" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="reportOpen = false">取消</el-button>
        <el-button type="primary" :loading="reportLoading" :disabled="!canSubmitReport" @click="handleReportSubmit">提交报工</el-button>
      </template>
    </el-dialog>

    <!-- 我的报工 Drawer -->
    <el-drawer v-model="myReportsOpen" title="我的报工" size="720px" append-to-body>
      <el-table v-loading="myReportsLoading" :data="myReports" size="small">
        <el-table-column label="工单/工序" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orderNo || '-' }} · {{ row.executionId }}</template>
        </el-table-column>
        <el-table-column label="合格" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="不良" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="报工时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">{{ row.reportStatusLabel || row.reportStatus }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button v-if="row.reportStatus === 'PENDING'" link size="small" type="danger" @click="openCancelReport(row)">撤销</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="myReportsPage"
          v-model:page-size="myReportsPageSize"
          :total="myReportsTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadMyReports"
          @current-change="loadMyReports"
        />
      </div>
    </el-drawer>

    <!-- 待我审批 Drawer -->
    <el-drawer v-model="pendingOpen" title="待我审批" size="760px" append-to-body>
      <el-table v-loading="pendingLoading" :data="pendingList" size="small">
        <el-table-column label="工单/工序" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.orderNo || '-' }} · {{ row.processName || row.executionId }}</template>
        </el-table-column>
        <el-table-column label="报工人" prop="reporterName" width="90" />
        <el-table-column label="合格" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.qualifiedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="不良" width="70" align="right">
          <template #default="{ row }">{{ fmtQty(row.defectiveQuantity) }}</template>
        </el-table-column>
        <el-table-column label="报工时间" width="120">
          <template #default="{ row }">{{ fmtTime(row.reportTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link size="small" type="success" @click="openApprove(row)">通过</el-button>
            <el-button link size="small" type="danger" @click="openReject(row)">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pendingPage"
          v-model:page-size="pendingPageSize"
          :total="pendingTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadPendingApproval"
          @current-change="loadPendingApproval"
        />
      </div>
    </el-drawer>

    <!-- 审批通过（备注可选） -->
    <el-dialog v-model="approveVisible" title="审批通过" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="报工">
          <span>{{ approveTarget?.reporterName }}：合格 {{ fmtQty(approveTarget?.qualifiedQuantity) }} / 不良 {{ fmtQty(approveTarget?.defectiveQuantity) }}</span>
        </el-form-item>
        <el-form-item label="审批备注">
          <el-input v-model="approveRemark" type="textarea" :rows="2" placeholder="可空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" :loading="approveLoading" @click="handleApprove">确认通过</el-button>
      </template>
    </el-dialog>

    <!-- 审批驳回（原因必填） -->
    <el-dialog v-model="rejectVisible" title="审批驳回" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="报工">
          <span>{{ rejectTarget?.reporterName }}：合格 {{ fmtQty(rejectTarget?.qualifiedQuantity) }} / 不良 {{ fmtQty(rejectTarget?.defectiveQuantity) }}</span>
        </el-form-item>
        <el-form-item label="驳回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="rejectLoading" :disabled="!rejectReason.trim()" @click="handleReject">确认驳回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { operationExecutionApi } from '@/api/production/operationExecution'
import {
  cancelWorkReport,
  submitWorkReport,
  approveWorkReport,
  rejectWorkReport,
  getMyWorkReports,
  getPendingApprovalWorkReports,
  getWorkReportsByExecution,
  type WorkReportVO,
} from '@/api/production/workReport'
import { getMyTasks } from '@/api/production/task'
import type { TaskTreeRow } from '@/types/production/task'
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
const queryParams = reactive<OperationExecutionQuery>({
  orderNo: '', processName: '', executionStatus: '', operatorName: '',
  pageNum: 1, pageSize: 10,
})

const getList = async () => {
  loading.value = true
  try {
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

// 完成提示：0 报工 / 低于计划 / 超计划 warning（后端 gate 为准）
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
  // P3：仅 PENDING 可撤销；APPROVED 为有效完成事实禁止普通撤销（更正走 P4 冲销）
  return row.reportStatus === 'PENDING'
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
    if (myReportsOpen.value) loadMyReports()
    if (pendingOpen.value) loadPendingApproval()
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

// ============ P6 报工提交 ============
const reportOpen = ref(false)
const reportLoading = ref(false)
const reportExec = ref<OperationExecutionVO | null>(null)
const reportTasks = ref<TaskTreeRow[]>([])
const reportTaskLoading = ref(false)
const reportTaskId = ref<number | null>(null)
const reportTimeRange = ref<[string, string] | null>(null)
const reportForm = reactive({
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  defectReason: '',
  laborHours: 0,
  machineHours: 0,
  remark: '',
})

const canSubmitReport = computed(() => {
  if (!reportTaskId.value) return false
  const q = Number(reportForm.qualifiedQuantity || 0)
  const d = Number(reportForm.defectiveQuantity || 0)
  if (q + d <= 0) return false
  if (d > 0 && !reportForm.defectReason.trim()) return false
  const task = reportTasks.value.find((t) => t.taskId === reportTaskId.value)
  if (task && q + d > Number(task.remainingQuantity || 0)) return false
  const start = reportTimeRange.value?.[0]
  const end = reportTimeRange.value?.[1]
  if (!!start !== !!end) return false
  return true
})

const openReportDialog = async (row: OperationExecutionVO) => {
  reportExec.value = row
  reportTasks.value = []
  reportTaskId.value = null
  Object.assign(reportForm, {
    qualifiedQuantity: 0,
    defectiveQuantity: 0,
    defectReason: '',
    laborHours: 0,
    machineHours: 0,
    remark: '',
  })
  reportTimeRange.value = null
  reportOpen.value = true
  if (!row.executionId) return
  reportTaskLoading.value = true
  try {
    const res: any = await getMyTasks(row.executionId)
    reportTasks.value = res?.data || []
    if (!reportTasks.value.length) {
      ElMessage.warning('当前工序没有你持有的可报工任务（须为任务执行人且处于责任执行中）')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '我的任务加载失败')
  } finally {
    reportTaskLoading.value = false
  }
}

const handleReportSubmit = async () => {
  const exec = reportExec.value
  if (!exec?.executionId || !reportTaskId.value) return
  const qualified = Number(reportForm.qualifiedQuantity || 0)
  const defective = Number(reportForm.defectiveQuantity || 0)
  if (qualified + defective <= 0) {
    ElMessage.warning('合格与不良数量之和必须大于 0')
    return
  }
  if (defective > 0 && !reportForm.defectReason.trim()) {
    ElMessage.warning('不良数量大于 0 时，不良原因必填')
    return
  }
  reportLoading.value = true
  try {
    await submitWorkReport({
      executionId: exec.executionId,
      taskId: reportTaskId.value,
      qualifiedQuantity: qualified,
      defectiveQuantity: defective,
      laborHours: Number(reportForm.laborHours || 0) || undefined,
      machineHours: Number(reportForm.machineHours || 0) || undefined,
      workStartTime: reportTimeRange.value?.[0] || undefined,
      workEndTime: reportTimeRange.value?.[1] || undefined,
      defectReason: defective > 0 ? reportForm.defectReason.trim() : undefined,
      remark: reportForm.remark.trim() || undefined,
    })
    ElMessage.success('报工已提交，等待审批')
    reportOpen.value = false
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '报工提交失败')
  } finally {
    reportLoading.value = false
  }
}

// ============ P6 我的报工 ============
const myReportsOpen = ref(false)
const myReportsLoading = ref(false)
const myReports = ref<WorkReportVO[]>([])
const myReportsPage = ref(1)
const myReportsPageSize = ref(10)
const myReportsTotal = ref(0)

const loadMyReports = async () => {
  myReportsLoading.value = true
  try {
    const res: any = await getMyWorkReports({ pageNum: myReportsPage.value, pageSize: myReportsPageSize.value })
    const data = res?.data
    myReports.value = Array.isArray(data) ? data : data?.records || []
    myReportsTotal.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '我的报工加载失败')
    myReports.value = []
  } finally {
    myReportsLoading.value = false
  }
}

const openMyReports = () => {
  myReportsOpen.value = true
  loadMyReports()
}

// ============ P6 待我审批 ============
const pendingOpen = ref(false)
const pendingLoading = ref(false)
const pendingList = ref<WorkReportVO[]>([])
const pendingPage = ref(1)
const pendingPageSize = ref(10)
const pendingTotal = ref(0)

const loadPendingApproval = async () => {
  pendingLoading.value = true
  try {
    const res: any = await getPendingApprovalWorkReports({ pageNum: pendingPage.value, pageSize: pendingPageSize.value })
    const data = res?.data
    pendingList.value = Array.isArray(data) ? data : data?.records || []
    pendingTotal.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '待审批列表加载失败')
    pendingList.value = []
  } finally {
    pendingLoading.value = false
  }
}

const openPendingApproval = () => {
  pendingOpen.value = true
  loadPendingApproval()
}

// 审批通过
const approveVisible = ref(false)
const approveLoading = ref(false)
const approveTarget = ref<WorkReportVO | null>(null)
const approveRemark = ref('')

const openApprove = (row: WorkReportVO) => {
  approveTarget.value = row
  approveRemark.value = ''
  approveVisible.value = true
}

const handleApprove = async () => {
  const target = approveTarget.value
  if (!target) return
  approveLoading.value = true
  try {
    await approveWorkReport(target.reportId, { reviewRemark: approveRemark.value.trim() || undefined })
    ElMessage.success('审批通过')
    approveVisible.value = false
    loadPendingApproval()
  } catch (e: any) {
    ElMessage.error(e?.message || '审批失败')
  } finally {
    approveLoading.value = false
  }
}

// 审批驳回
const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectTarget = ref<WorkReportVO | null>(null)
const rejectReason = ref('')

const openReject = (row: WorkReportVO) => {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

const handleReject = async () => {
  const target = rejectTarget.value
  if (!target) return
  if (!rejectReason.value.trim()) {
    ElMessage.warning('驳回原因必填')
    return
  }
  rejectLoading.value = true
  try {
    await rejectWorkReport(target.reportId, { reviewRemark: rejectReason.value.trim() })
    ElMessage.success('已驳回')
    rejectVisible.value = false
    loadPendingApproval()
  } catch (e: any) {
    ElMessage.error(e?.message || '驳回失败')
  } finally {
    rejectLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.execution-page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-actions { display: flex; gap: 8px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.filter-card { margin-bottom: 16px; }
.filter-bar { display: flex; gap: 10px; align-items: center; padding-bottom: 8px; flex-wrap: wrap; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
