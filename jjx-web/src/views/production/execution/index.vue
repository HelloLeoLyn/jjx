<template>
  <div class="app-container">
    <el-card class="search-card" shadow="never">
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="我的任务" name="my">
          <template #label>
            <span><el-icon><UserFilled /></el-icon> 我的任务</span>
          </template>
        </el-tab-pane>
        <el-tab-pane label="全部任务" name="all">
          <template #label>
            <span><el-icon><List /></el-icon> 全部任务</span>
          </template>
        </el-tab-pane>
      </el-tabs>
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="100px">
        <el-form-item label="工单编号" prop="orderNo">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入工单编号"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="工序名称" prop="processName">
          <el-input
            v-model="queryParams.processName"
            placeholder="请输入工序名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="执行状态" prop="executionStatus">
          <el-select
            v-model="queryParams.executionStatus"
            placeholder="请选择执行状态"
            clearable
            style="width: 200px"
          >
            <el-option label="待执行" :value="0" />
            <el-option label="执行中" :value="2" />
            <el-option label="已完成" :value="4" />
            <el-option label="已暂停" :value="3" />
            <el-option label="已取消" :value="6" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-header">
        <div class="table-title">生产执行列表</div>
        <div class="table-actions">
          <el-button type="warning" icon="Refresh" @click="getList">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="executionList" row-key="executionId">
        <el-table-column label="工单编号" prop="orderNo" width="190" align="center" />
        <el-table-column label="工序" width="140">
          <template #default="scope">
            <div class="process-cell">
              <!-- 有下标：图标+红底数字（仿工艺路线详情） -->
              <IconStepBadge
                v-if="scope.row.hasIndex === 1"
                :icon="scope.row.icon || ''"
                :size="18"
                :index="scope.row.indexNumber ?? null"
              />
              <!-- 无下标：图标+名称（印刷工序带标识，2026-08-12） -->
              <template v-else>
                <SvgIcon
                  v-if="scope.row.icon"
                  :name="scope.row.icon"
                  :size="18"
                  style="margin-right: 6px; vertical-align: middle"
                />
                <el-tag v-if="scope.row.majorCategory === 'PRINT'" size="small" type="warning" effect="plain" style="margin-right: 4px">印刷</el-tag>
                <span class="process-name">{{ scope.row.processName }}</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="工艺参数" min-width="160">
          <template #default="scope">
            <span v-if="printParamsText(scope.row)" style="color: #e6a23c; font-size: 12px">🖨️ {{ printParamsText(scope.row) }}</span>
            <span v-else style="color: #c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="工序顺序" prop="processOrder" width="80" align="center" />
        <el-table-column label="投入数量" prop="inputQuantity" width="100" align="center" />
        <el-table-column label="产出数量" prop="outputQuantity" width="100" align="center" />
        <el-table-column label="合格数量" prop="qualifiedQuantity" width="100" align="center" />
        <el-table-column label="执行状态" prop="executionStatus" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.executionStatus)">
              {{ scope.row.executionStatusDesc || scope.row.executionStatus }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作员" prop="operatorName" width="100" />
        <el-table-column label="开始时间" prop="actualStartTime" width="160" align="center">
          <template #default="scope">
            {{ parseTime(scope.row.actualStartTime) }}
          </template>
        </el-table-column>
        <el-table-column label="完成时间" prop="actualEndTime" width="160" align="center">
          <template #default="scope">
            {{ parseTime(scope.row.actualEndTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="380" align="center" fixed="right">
          <template #default="scope">
            <el-button
              type="success"
              link
              icon="PlayCircle"
              @click="handleStart(scope.row)"
              v-if="scope.row.executionStatus === 0"
              >开始</el-button
            >
            <el-button
              type="warning"
              link
              icon="Pause"
              @click="handlePause(scope.row)"
              v-if="scope.row.executionStatus === 2"
              >暂停</el-button
            >
            <el-button
              type="primary"
              link
              icon="Check"
              @click="handleComplete(scope.row)"
              v-if="scope.row.executionStatus === 2"
              >完成</el-button
            >
            <el-button type="primary" link icon="View" @click="handleView(scope.row)"
              >详情</el-button
            >
            <el-button type="info" link icon="Document" @click="handleRecord(scope.row)"
              >记录</el-button
            >
            <el-button
              type="warning"
              link
              icon="WarningFilled"
              @click="handleQualityCheck(scope.row)"
              v-if="scope.row.executionStatus === 2 || scope.row.executionStatus === 4"
              >质检</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <el-dialog title="生产执行详情" v-model="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="工单编号">{{ detailForm.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="工序名称">{{ detailForm.processName }}</el-descriptions-item>
        <el-descriptions-item label="工序顺序">{{ detailForm.processOrder }}</el-descriptions-item>
        <el-descriptions-item label="投入数量">{{ detailForm.inputQuantity }}</el-descriptions-item>
        <el-descriptions-item label="产出数量">{{
          detailForm.outputQuantity || 0
        }}</el-descriptions-item>
        <el-descriptions-item label="合格数量">{{
          detailForm.qualifiedQuantity || 0
        }}</el-descriptions-item>
        <el-descriptions-item label="不良数量">{{
          detailForm.defectiveQuantity || 0
        }}</el-descriptions-item>
        <el-descriptions-item label="执行状态">
          <el-tag :type="getStatusTagType(detailForm.executionStatus)">
            {{ detailForm.executionStatusDesc || detailForm.executionStatus }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="操作员">{{ detailForm.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="设备编号">{{ detailForm.equipmentCode }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{
          parseTime(detailForm.actualStartTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{
          parseTime(detailForm.actualEndTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="实际工时(h)">{{
          detailForm.actualLaborHours
        }}</el-descriptions-item>
        <el-descriptions-item label="机器工时(h)">{{
          detailForm.actualMachineHours
        }}</el-descriptions-item>
        <el-descriptions-item label="合格率">{{ detailForm.qualifiedRate }}%</el-descriptions-item>
        <el-descriptions-item label="不良率">{{ detailForm.defectiveRate }}%</el-descriptions-item>
        <el-descriptions-item label="工艺参数" :span="2">
          <pre class="json-preview">{{ formatJson(detailForm.actualProcessParams) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="质量检查结果" :span="2">
          <pre class="json-preview">{{ formatJson(detailForm.qualityCheckResult) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="不良原因" :span="2">
          {{ detailForm.defectiveReason || '无' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 首检/巡检对话框 -->
    <el-dialog title="工序质量检验" v-model="qcVisible" width="500px" append-to-body>
      <el-form :model="qcForm" label-width="100px">
        <el-form-item label="检验类型">
          <el-select v-model="qcForm.checkType" placeholder="请选择">
            <el-option label="首检（首批确认）" value="first_piece" />
            <el-option label="巡检（过程抽检）" value="spot_check" />
          </el-select>
        </el-form-item>
        <el-form-item label="抽检数量">
          <el-input-number v-model="qcForm.checkQty" :min="1" :max="9999" style="width:100%" />
        </el-form-item>
        <el-form-item label="合格数量">
          <el-input-number v-model="qcForm.passQty" :min="0" :max="qcForm.checkQty" style="width:100%" />
        </el-form-item>
        <el-form-item label="检验结果">
          <el-radio-group v-model="qcForm.result">
            <el-radio value="pass">✅ 合格</el-radio>
            <el-radio value="fail">❌ 不合格</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="缺陷描述" v-if="qcForm.result === 'fail'">
          <el-input v-model="qcForm.defectDesc" type="textarea" :rows="2" placeholder="请描述不合格项" maxlength="500" />
        </el-form-item>
        <el-form-item label="检验人">
          <el-input v-model="qcForm.inspector" placeholder="输入检验人姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qcVisible = false">取消</el-button>
        <el-button type="primary" @click="submitQc">提交检验</el-button>
      </template>
    </el-dialog>

    <!-- 生产记录对话框 -->
    <el-dialog title="生产记录" v-model="recordOpen" width="800px" append-to-body>
      <el-form ref="recordFormRef" :model="recordForm" label-width="120px">
        <el-form-item label="投入数量" prop="inputQuantity">
          <el-input-number
            v-model="recordForm.inputQuantity"
            :min="0"
            style="width: 100%"
            placeholder="请输入投入数量"
          />
        </el-form-item>
        <el-form-item label="产出数量" prop="outputQuantity">
          <el-input-number
            v-model="recordForm.outputQuantity"
            :min="0"
            :max="recordForm.inputQuantity || 999999"
            style="width: 100%"
            placeholder="请输入产出数量"
          />
        </el-form-item>
        <el-form-item label="合格数量" prop="qualifiedQuantity">
          <el-input-number
            v-model="recordForm.qualifiedQuantity"
            :min="0"
            :max="recordForm.outputQuantity || 999999"
            style="width: 100%"
            placeholder="请输入合格数量"
          />
        </el-form-item>
        <el-form-item label="不良数量" prop="defectiveQuantity">
          <el-input-number
            v-model="recordForm.defectiveQuantity"
            :min="0"
            :max="recordForm.outputQuantity"
            style="width: 100%"
            placeholder="请输入不良数量"
          />
        </el-form-item>
        <el-form-item label="实际工时(h)" prop="actualLaborHours">
          <el-input-number
            v-model="recordForm.actualLaborHours"
            :min="0"
            :step="0.1"
            :precision="2"
            style="width: 100%"
            placeholder="请输入实际工时"
          />
        </el-form-item>
        <el-form-item label="机器工时(h)" prop="actualMachineHours">
          <el-input-number
            v-model="recordForm.actualMachineHours"
            :min="0"
            :step="0.1"
            :precision="2"
            style="width: 100%"
            placeholder="请输入机器工时"
          />
        </el-form-item>
        <el-form-item label="不良原因" prop="defectiveReason">
          <el-input
            v-model="recordForm.defectiveReason"
            type="textarea"
            :rows="3"
            placeholder="请输入不良原因"
          />
        </el-form-item>
        <el-form-item label="工艺参数" prop="actualProcessParams">
          <el-input
            v-model="recordForm.actualProcessParams"
            type="textarea"
            :rows="3"
            placeholder="请输入工艺参数（JSON格式）"
          />
          <div class="form-tips">
            支持JSON格式，如：{"temperature": "150°C", "pressure": "12kg/cm²"}
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitRecord">确 定</el-button>
          <el-button @click="recordOpen = false">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductionExecutionList',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, List } from '@element-plus/icons-vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { ExecutionStatusEnum } from '@/enums/production'
import type {
  OperationExecutionVO,
  OperationExecutionQuery,
} from '@/types/production/operationExecution'
import { parseTime } from '@/utils/format'

// ==================== 状态 ====================
const loading = ref(false)
const executionList = ref<OperationExecutionVO[]>([])
const total = ref(0)
const activeTab = ref('my')
const detailOpen = ref(false)
const recordOpen = ref(false)

// 质检
const qcVisible = ref(false)
const qcForm = reactive({
  checkType: 'first_piece',
  checkQty: 5,
  passQty: 5,
  result: 'pass',
  defectDesc: '',
  inspector: '',
})
let qcCurrentRow: any = null

const handleQualityCheck = (row: any) => {
  qcCurrentRow = row
  qcForm.checkType = 'first_piece'
  qcForm.checkQty = 5
  qcForm.passQty = 5
  qcForm.result = 'pass'
  qcForm.defectDesc = ''
  qcForm.inspector = ''
  qcVisible.value = true
}

const submitQc = async () => {
  if (qcForm.passQty > qcForm.checkQty) {
    ElMessage.warning('合格数量不能大于抽检数量')
    return
  }
  if (!qcCurrentRow?.executionId) return
  const typeText = qcForm.checkType === 'first_piece' ? '首检' : '巡检'
  try {
    const checkResult = qcForm.result === 'pass' ? 'PASS' : 'FAIL'
    const checkItems = `抽检${qcForm.checkQty}件/合格${qcForm.passQty}件${qcForm.defectDesc ? '/' + qcForm.defectDesc : ''}`
    await operationExecutionApi.qualityCheck(
      qcCurrentRow.executionId,
      qcForm.checkType === 'first_piece' ? 'FIRST' : 'PATROL',
      checkResult,
      checkItems,
      qcForm.defectDesc || undefined,
    )
    ElMessage.success(`${typeText}完成：${qcForm.passQty}/${qcForm.checkQty} ${qcForm.result === 'pass' ? '合格' : '不合格'}`)
    qcVisible.value = false
    if (qcForm.result === 'fail') {
      ElMessage.warning('不合格，工序已自动暂停，请排查问题！')
    }
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || `${typeText}提交失败`)
  }
}
const recordFormRef = ref()
const queryForm = ref()

const queryParams = reactive<OperationExecutionQuery>({
  orderNo: '',
  processName: '',
  executionStatus: '',
  operatorName: '',
  pageNum: 1,
  pageSize: 10,
})

const detailForm = reactive<Record<string, any>>({})

const recordForm = reactive({
  outputQuantity: 0,
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  actualLaborHours: 0,
  actualMachineHours: 0,
  defectiveReason: '',
  actualProcessParams: '',
  inputQuantity: 0,
  currentExecutionId: 0,
})

// ==================== API ====================
const getList = async () => {
  loading.value = true
  if (activeTab.value === 'my') {
    queryParams.operatorName = '当前用户'
  } else {
    queryParams.operatorName = ''
  }
  try {
    const res = await operationExecutionApi.list(queryParams)
    // 2026-08-11 修复：后端 /list 返回裸数组（非分页结构），兼容两种
    const data = res.data as any
    executionList.value = Array.isArray(data) ? data : data?.records || []
    total.value = Array.isArray(data) ? data.length : data?.total || 0
  } catch (error) {
    console.error('获取工序执行列表失败:', error)
  } finally {
    loading.value = false
  }
}

// ==================== 事件 ====================
const handleTabChange = () => {
  queryParams.pageNum = 1
  if (activeTab.value === 'my') {
    queryParams.operatorName = '当前用户'
  } else {
    queryParams.operatorName = ''
  }
  getList()
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  queryParams.orderNo = ''
  queryParams.processName = ''
  queryParams.executionStatus = ''
  queryParams.operatorName = ''
  handleQuery()
}

const handleCurrentChange = (page: number) => {
  queryParams.pageNum = page
  getList()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  getList()
}

// 开始执行
const handleStart = async (row: OperationExecutionVO) => {
  try {
    await ElMessageBox.confirm('确认开始执行该工序？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await operationExecutionApi.start(row.executionId!)
    ElMessage.success('工序已开始执行')
    getList()
  } catch {
    // 取消操作不做处理
  }
}

// 暂停执行
const handlePause = async (row: OperationExecutionVO) => {
  try {
    await ElMessageBox.confirm('确认暂停该工序？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await operationExecutionApi.pause(row.executionId!)
    ElMessage.success('工序已暂停')
    getList()
  } catch {
    // 取消操作不做处理
  }
}

// 完成执行
const handleComplete = (row: OperationExecutionVO) => {
  recordForm.outputQuantity = row.outputQuantity || 0
  recordForm.qualifiedQuantity = row.qualifiedQuantity || 0
  recordForm.defectiveQuantity = row.defectiveQuantity || 0
  recordForm.actualLaborHours = row.actualLaborHours || 0
  recordForm.actualMachineHours = row.actualMachineHours || 0
  recordForm.defectiveReason = row.defectiveReason || ''
  recordForm.actualProcessParams = row.actualProcessParams || ''
  recordForm.inputQuantity = row.inputQuantity || 0
  // 2026-08-11 修复：投入数量为 0 时给出默认值，避免产出/合格被 max=0 锁死
  if (recordForm.inputQuantity <= 0) {
    recordForm.inputQuantity = 50
  }
  recordForm.currentExecutionId = row.executionId!
  recordOpen.value = true
}

// 查看详情
const handleView = async (row: OperationExecutionVO) => {
  try {
    const res = await operationExecutionApi.getInfo(row.executionId!)
    Object.assign(detailForm, res.data)
    detailOpen.value = true
  } catch (error) {
    console.error('获取工序执行详情失败:', error)
  }
}

// 记录生产数据
const handleRecord = (row: OperationExecutionVO) => {
  recordForm.outputQuantity = row.outputQuantity || 0
  recordForm.qualifiedQuantity = row.qualifiedQuantity || 0
  recordForm.defectiveQuantity = row.defectiveQuantity || 0
  recordForm.actualLaborHours = row.actualLaborHours || 0
  recordForm.actualMachineHours = row.actualMachineHours || 0
  recordForm.defectiveReason = row.defectiveReason || ''
  recordForm.actualProcessParams = row.actualProcessParams || ''
  recordForm.inputQuantity = row.inputQuantity || 0
  recordForm.currentExecutionId = row.executionId!
  recordOpen.value = true
}

// 提交记录
const submitRecord = async () => {
  if (recordForm.outputQuantity > recordForm.inputQuantity) {
    ElMessage.error('产出数量不能超过投入数量')
    return
  }

  if (recordForm.qualifiedQuantity + recordForm.defectiveQuantity > recordForm.outputQuantity) {
    ElMessage.error('合格数量+不良数量不能超过产出数量')
    return
  }

  try {
    await operationExecutionApi.edit({
      executionId: recordForm.currentExecutionId,
      actualCompletedQuantity: recordForm.outputQuantity,
      actualQualifiedQuantity: recordForm.qualifiedQuantity,
      actualDefectiveQuantity: recordForm.defectiveQuantity,
      actualLaborHours: recordForm.actualLaborHours,
      actualMachineHours: recordForm.actualMachineHours,
      remark: recordForm.defectiveReason,
    })
    ElMessage.success('生产记录已保存')
    recordOpen.value = false
    getList()
  } catch (error) {
    console.error('保存生产记录失败:', error)
  }
}

// ==================== 工具函数 ====================
function formatJson(json: string | null) {
  if (!json) return '无'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

function getStatusTagType(
  status: number | undefined
): 'primary' | 'success' | 'warning' | 'info' | 'danger' | undefined {
  // 统一枚举：对应后端 ExecutionStatusEnum
  const props = ExecutionStatusEnum.getTagProps(status ?? -1)
  const type = props.type as string
  return (['primary', 'success', 'warning', 'info', 'danger'] as const).includes(type as any)
    ? (type as any)
    : undefined
}

/** 印刷参数友好文本（2026-08-12）：计划参数 customProcessParams / 实际参数 actualProcessParams */
function printParamsText(row: any): string {
  const json = row?.customProcessParams || row?.actualProcessParams
  if (!json) return ''
  try {
    const o = typeof json === 'string' ? JSON.parse(json) : json
    const parts: string[] = []
    if (o.colorNo) parts.push(`色号:${o.colorNo}`)
    if (o.inkNo) parts.push(`油墨:${o.inkNo}`)
    if (o.screenNo) parts.push(`网框:${o.screenNo}`)
    return parts.join(' ')
  } catch {
    return ''
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

/* 2026-08-11 工序图标展示（仿工艺路线详情） */
.process-cell {
  display: flex;
  align-items: center;
}
.process-name {
  font-size: 13px;
  color: #303133;
}

.search-card {
  margin-bottom: 20px;
}
.table-card {
  margin-top: 20px;
}
.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.table-title {
  font-size: 16px;
  font-weight: bold;
}
.pagination-container {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
.json-preview {
  background: #f5f5f5;
  padding: 10px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}
.form-tips {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
}
</style>
