<template>
  <div class="production-operation">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">生产操作管理</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" @click="handleCreate">分配操作任务</el-button>
        <el-button icon="Refresh" @click="refreshData">刷新</el-button>
        <el-button icon="Download" @click="handleExport">导出</el-button>
      </div>
    </div>

    <!-- 搜索筛选 -->
    <div class="search-filter">
      <el-card shadow="never" class="filter-card">
        <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="100px">
          <el-row :gutter="20">
            <el-col :span="6">
              <el-form-item label="工单编号">
                <el-input
                  v-model="queryParams.workOrderNo"
                  placeholder="请输入工单编号"
                  clearable
                  @keyup.enter="handleQuery"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="操作员">
                <el-input
                  v-model="queryParams.operatorName"
                  placeholder="请输入操作员姓名"
                  clearable
                  @keyup.enter="handleQuery"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="操作状态">
                <el-select
                  v-model="queryParams.operationStatus"
                  placeholder="请选择操作状态"
                  clearable
                >
                  <el-option label="待分配" value="pending" />
                  <el-option label="进行中" value="in_progress" />
                  <el-option label="已完成" value="completed" />
                  <el-option label="已取消" value="cancelled" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="设备编号">
                <el-input
                  v-model="queryParams.equipmentCode"
                  placeholder="请输入设备编号"
                  clearable
                  @keyup.enter="handleQuery"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="工序名称">
                <el-input
                  v-model="queryParams.stepName"
                  placeholder="请输入工序名称"
                  clearable
                  @keyup.enter="handleQuery"
                />
              </el-form-item>
            </el-col>
            <el-col :span="6">
              <el-form-item label="操作日期">
                <el-date-picker
                  v-model="queryParams.operationDateRange"
                  type="daterange"
                  range-separator="至"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  value-format="YYYY-MM-DD"
                />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item>
                <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
                <el-button icon="Refresh" @click="resetQuery">重置</el-button>
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
      </el-card>
    </div>

    <!-- 操作统计卡片 -->
    <div class="operation-stats">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #409eff">
                <el-icon><Clock /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.pendingCount || 0 }}</div>
                <div class="stat-label">待分配</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #e6a23c">
                <el-icon><Loading /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.inProgressCount || 0 }}</div>
                <div class="stat-label">进行中</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #67c23a">
                <el-icon><Check /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.completedCount || 0 }}</div>
                <div class="stat-label">已完成</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #909399">
                <el-icon><Close /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">{{ stats.cancelledCount || 0 }}</div>
                <div class="stat-label">已取消</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 操作任务列表 -->
    <div class="operation-list">
      <el-card shadow="never">
        <div class="table-header">
          <div class="table-title">操作任务列表</div>
          <div class="table-actions">
            <el-button link icon="Setting" @click="showColumnSettings">列设置</el-button>
          </div>
        </div>

        <el-table
          v-loading="loading"
          :data="operationList"
          :row-key="(row) => row.operationId"
          style="width: 100%"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="操作编号" prop="operationCode" width="120" align="center" />
          <el-table-column label="工单编号" prop="workOrderNo" width="120" align="center" />
          <el-table-column label="产品名称" prop="productName" min-width="120" />
          <el-table-column label="工序名称" prop="stepName" width="100" align="center" />
          <el-table-column label="操作员" prop="operatorName" width="100" align="center" />
          <el-table-column label="设备编号" prop="equipmentCode" width="100" align="center" />
          <el-table-column label="计划数量" prop="plannedQuantity" width="90" align="center" />
          <el-table-column label="已完成" prop="completedQuantity" width="90" align="center" />
          <el-table-column label="操作状态" prop="operationStatus" width="100" align="center">
            <template #default="scope">
              <el-tag :type="getStatusTagType(scope.row.operationStatus)" size="small">
                {{ getStatusLabel(scope.row.operationStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="开始时间" prop="startTime" width="150" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.startTime) }}
            </template>
          </el-table-column>
          <el-table-column label="结束时间" prop="endTime" width="150" align="center">
            <template #default="scope">
              {{ formatTime(scope.row.endTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="scope">
              <el-button
                v-if="scope.row.operationStatus === 0"
                type="primary"
                link
                icon="Edit"
                @click="handleAssign(scope.row)"
                >分配</el-button
              >
              <el-button
                v-if="
                  scope.row.operationStatus === 0 ||
                  scope.row.operationStatus === 1
                "
                type="success"
                link
                icon="Play"
                @click="handleStart(scope.row)"
                >开始</el-button
              >
              <el-button
                v-if="scope.row.operationStatus === 1"
                type="warning"
                link
                icon="Check"
                @click="handleComplete(scope.row)"
                >完成</el-button
              >
              <el-button type="info" link icon="View" @click="handleView(scope.row)"
                >详情</el-button
              >
              <el-button type="danger" link icon="Delete" @click="handleDelete(scope.row)"
                >删除</el-button
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
    </div>

    <!-- 操作任务分配/编辑对话框 -->
    <el-dialog
      :title="dialogTitle"
      v-model="dialogOpen"
      width="600px"
      append-to-body
      @close="cancel"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="工单编号" prop="workOrderId">
          <el-select
            v-model="form.workOrderId"
            placeholder="请选择工单"
            filterable
            style="width: 100%"
            @change="handleWorkOrderChange"
          >
            <el-option
              v-for="item in workOrderOptions"
              :key="item.workOrderId"
              :label="`${item.workOrderNo} - ${item.productName}`"
              :value="item.workOrderId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工序" prop="stepId">
          <el-select v-model="form.stepId" placeholder="请选择工序" style="width: 100%">
            <el-option
              v-for="item in stepOptions"
              :key="item.stepId"
              :label="item.stepName"
              :value="item.stepId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作员" prop="operatorId">
          <el-select v-model="form.operatorId" placeholder="请选择操作员" style="width: 100%">
            <el-option
              v-for="item in operatorOptions"
              :key="item.userId"
              :label="item.userName"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="设备" prop="equipmentId">
          <el-select v-model="form.equipmentId" placeholder="请选择设备" style="width: 100%">
            <el-option
              v-for="item in equipmentOptions"
              :key="item.equipmentId"
              :label="`${item.equipmentCode} - ${item.equipmentName}`"
              :value="item.equipmentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划数量" prop="plannedQuantity">
          <el-input-number
            v-model="form.plannedQuantity"
            :min="1"
            :max="10000"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="工艺参数" prop="parameters">
          <el-input
            v-model="form.parameters"
            type="textarea"
            :rows="3"
            placeholder="请输入工艺参数（JSON格式）"
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 操作详情对话框 -->
    <el-dialog title="操作任务详情" v-model="detailOpen" width="700px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="操作编号">{{ detailForm.operationCode }}</el-descriptions-item>
        <el-descriptions-item label="工单编号">{{ detailForm.workOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detailForm.productName }}</el-descriptions-item>
        <el-descriptions-item label="工序名称">{{ detailForm.stepName }}</el-descriptions-item>
        <el-descriptions-item label="操作员">{{ detailForm.operatorName }}</el-descriptions-item>
        <el-descriptions-item label="设备编号">{{ detailForm.equipmentCode }}</el-descriptions-item>
        <el-descriptions-item label="计划数量">{{
          detailForm.plannedQuantity
        }}</el-descriptions-item>
        <el-descriptions-item label="已完成数量">{{
          detailForm.completedQuantity || 0
        }}</el-descriptions-item>
        <el-descriptions-item label="操作状态">
          <el-tag :type="getStatusTagType(detailForm.operationStatus)">
            {{ getStatusLabel(detailForm.operationStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{
          formatTime(detailForm.startTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{
          formatTime(detailForm.endTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="工艺参数" :span="2">
          <pre class="json-preview">{{ formatJson(detailForm.parameters) }}</pre>
        </el-descriptions-item>
        <el-descriptions-item label="质量结果">{{
          detailForm.qualityResult || '未检查'
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detailForm.remark || '无'
        }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="detailOpen = false">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Clock, Loading, Check, Close } from '@element-plus/icons-vue'

// 类型定义
interface OperationItem {
  operationId: string
  operationCode: string
  workOrderId: string
  workOrderNo: string
  productName: string
  stepId: string
  stepName: string
  operatorId: string
  operatorName: string
  equipmentId: string
  equipmentCode: string
  plannedQuantity: number
  completedQuantity: number
  operationStatus: number
  startTime: string
  endTime: string
  parameters: string
  qualityResult: string
  remark: string
}

interface QueryParams {
  workOrderNo: string
  operatorName: string
  operationStatus: string
  equipmentCode: string
  stepName: string
  operationDateRange: string[]
  pageNum: number
  pageSize: number
}

interface StatsData {
  pendingCount: number
  inProgressCount: number
  completedCount: number
  cancelledCount: number
}

// 响应式数据
const loading = ref(false)
const operationList = ref<OperationItem[]>([])
const total = ref(0)
const stats = ref<StatsData>({
  pendingCount: 0,
  inProgressCount: 0,
  completedCount: 0,
  cancelledCount: 0,
})

const queryParams = reactive<QueryParams>({
  workOrderNo: '',
  operatorName: '',
  operationStatus: '',
  equipmentCode: '',
  stepName: '',
  operationDateRange: [],
  pageNum: 1,
  pageSize: 10,
})

// 对话框相关
const dialogOpen = ref(false)
const detailOpen = ref(false)
const dialogTitle = ref('')
const formRef = ref<FormInstance>()
const form = reactive({
  operationId: '',
  workOrderId: '',
  stepId: '',
  operatorId: '',
  equipmentId: '',
  plannedQuantity: 1,
  parameters: '',
  remark: '',
})
const detailForm = ref<OperationItem>({} as OperationItem)

// 选项数据
const workOrderOptions = ref<any[]>([])
const stepOptions = ref<any[]>([])
const operatorOptions = ref<any[]>([])
const equipmentOptions = ref<any[]>([])

// 表单验证规则
const rules = reactive<FormRules>({
  workOrderId: [{ required: true, message: '请选择工单', trigger: 'change' }],
  stepId: [{ required: true, message: '请选择工序', trigger: 'change' }],
  operatorId: [{ required: true, message: '请选择操作员', trigger: 'change' }],
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  plannedQuantity: [
    { required: true, message: '请输入计划数量', trigger: 'blur' },
    { type: 'number', min: 1, message: '数量必须大于0', trigger: 'blur' },
  ],
})

// 多选数据
const selectedRows = ref<OperationItem[]>([])

// 生命周期
onMounted(() => {
  getList()
  getStats()
  loadOptions()
})

// 获取操作列表
const getList = async () => {
  loading.value = true
  try {
    // 模拟数据
    operationList.value = [
      {
        operationId: 'OP001',
        operationCode: 'OP20250410001',
        workOrderId: 'WO001',
        workOrderNo: 'WO20250410001',
        productName: '薄膜开关A型',
        stepId: 'STEP001',
        stepName: '印刷',
        operatorId: 'USER001',
        operatorName: '张三',
        equipmentId: 'EQ001',
        equipmentCode: 'PRINT-001',
        plannedQuantity: 1000,
        completedQuantity: 0,
        operationStatus: 0,
        startTime: '',
        endTime: '',
        parameters: '{"inkType": "UV油墨", "pressure": "2.5kg"}',
        qualityResult: '',
        remark: '首批生产',
      },
      {
        operationId: 'OP002',
        operationCode: 'OP20250410002',
        workOrderId: 'WO001',
        workOrderNo: 'WO20250410001',
        productName: '薄膜开关A型',
        stepId: 'STEP002',
        stepName: '层压',
        operatorId: 'USER002',
        operatorName: '李四',
        equipmentId: 'EQ002',
        equipmentCode: 'LAMINATE-001',
        plannedQuantity: 1000,
        completedQuantity: 500,
        operationStatus: 1,
        startTime: '2025-04-10 08:00:00',
        endTime: '',
        parameters: '{"temperature": "120℃", "time": "30s"}',
        qualityResult: '合格',
        remark: '注意温度控制',
      },
      {
        operationId: 'OP003',
        operationCode: 'OP20250410003',
        workOrderId: 'WO002',
        workOrderNo: 'WO20250410002',
        productName: '薄膜开关B型',
        stepId: 'STEP003',
        stepName: '冲切',
        operatorId: 'USER003',
        operatorName: '王五',
        equipmentId: 'EQ003',
        equipmentCode: 'CUT-001',
        plannedQuantity: 800,
        completedQuantity: 800,
        operationStatus: 2,
        startTime: '2025-04-09 09:00:00',
        endTime: '2025-04-09 17:00:00',
        parameters: '{"bladeType": "精密刀模", "pressure": "3.0kg"}',
        qualityResult: '合格',
        remark: '已完成',
      },
    ]
    total.value = operationList.value.length
  } catch (error) {
    console.error('获取操作列表失败:', error)
    ElMessage.error('获取操作列表失败')
  } finally {
    loading.value = false
  }
}

// 获取统计数据
const getStats = async () => {
  try {
    // 模拟统计数据
    stats.value = {
      pendingCount: 5,
      inProgressCount: 12,
      completedCount: 48,
      cancelledCount: 2,
    }
  } catch (error) {
    console.error('获取统计数据失败:', error)
  }
}

// 加载选项数据
const loadOptions = async () => {
  try {
    // 模拟工单选项
    workOrderOptions.value = [
      { workOrderId: 'WO001', workOrderNo: 'WO20250410001', productName: '薄膜开关A型' },
      { workOrderId: 'WO002', workOrderNo: 'WO20250410002', productName: '薄膜开关B型' },
      { workOrderId: 'WO003', workOrderNo: 'WO20250410003', productName: '薄膜开关C型' },
    ]

    // 模拟工序选项
    stepOptions.value = [
      { stepId: 'STEP001', stepName: '印刷' },
      { stepId: 'STEP002', stepName: '层压' },
      { stepId: 'STEP003', stepName: '冲切' },
      { stepId: 'STEP004', stepName: '组装' },
      { stepId: 'STEP005', stepName: '测试' },
    ]

    // 模拟操作员选项
    operatorOptions.value = [
      { userId: 'USER001', userName: '张三' },
      { userId: 'USER002', userName: '李四' },
      { userId: 'USER003', userName: '王五' },
      { userId: 'USER004', userName: '赵六' },
    ]

    // 模拟设备选项
    equipmentOptions.value = [
      { equipmentId: 'EQ001', equipmentCode: 'PRINT-001', equipmentName: '印刷机001' },
      { equipmentId: 'EQ002', equipmentCode: 'LAMINATE-001', equipmentName: '层压机001' },
      { equipmentId: 'EQ003', equipmentCode: 'CUT-001', equipmentName: '冲切机001' },
      { equipmentId: 'EQ004', equipmentCode: 'ASSEMBLE-001', equipmentName: '组装线001' },
    ]
  } catch (error) {
    console.error('加载选项数据失败:', error)
  }
}

// 搜索查询
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置查询
const resetQuery = () => {
  queryParams.workOrderNo = ''
  queryParams.operatorName = ''
  queryParams.operationStatus = ''
  queryParams.equipmentCode = ''
  queryParams.stepName = ''
  queryParams.operationDateRange = []
  handleQuery()
}

// 刷新数据
const refreshData = () => {
  getList()
  getStats()
}

// 处理工单选择变化
const handleWorkOrderChange = (workOrderId: string) => {
  // 可以根据选择的工单加载对应的工序
  console.log('选择的工单ID:', workOrderId)
}

// 获取状态标签类型
const getStatusTagType = (status: string): 'info' | 'warning' | 'success' | 'danger' => {
  const typeMap: Record<string, 'info' | 'warning' | 'success' | 'danger'> = {
    pending: 'info',
    in_progress: 'warning',
    completed: 'success',
    cancelled: 'danger',
  }
  return typeMap[status] || 'info'
}

// 获取状态标签文本
const getStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    pending: '待分配',
    in_progress: '进行中',
    completed: '已完成',
    cancelled: '已取消',
  }
  return labelMap[status] || '未知'
}

// 格式化时间
const formatTime = (time: string) => {
  if (!time) return '-'
  return time
}

// 格式化JSON
const formatJson = (jsonStr: string) => {
  if (!jsonStr) return '无'
  try {
    const obj = JSON.parse(jsonStr)
    return JSON.stringify(obj, null, 2)
  } catch {
    return jsonStr
  }
}

// 处理选择变化
const handleSelectionChange = (selection: OperationItem[]) => {
  selectedRows.value = selection
}

// 处理分页大小变化
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

// 处理当前页变化
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

// 显示列设置
const showColumnSettings = () => {
  ElMessage.info('列设置功能开发中')
}

// 导出数据
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 创建操作任务
const handleCreate = () => {
  resetForm()
  dialogTitle.value = '分配操作任务'
  dialogOpen.value = true
}

// 分配操作任务
const handleAssign = (row: OperationItem) => {
  Object.assign(form, {
    operationId: row.operationId,
    workOrderId: row.workOrderId,
    stepId: row.stepId,
    operatorId: row.operatorId,
    equipmentId: row.equipmentId,
    plannedQuantity: row.plannedQuantity,
    parameters: row.parameters,
    remark: row.remark,
  })
  dialogTitle.value = '编辑操作任务'
  dialogOpen.value = true
}

// 开始操作
const handleStart = async (row: OperationItem) => {
  try {
    await ElMessageBox.confirm('确认开始此操作任务?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    // 模拟API调用
    ElMessage.success('操作任务已开始')
    row.operationStatus = 1
    row.startTime = new Date().toISOString()
  } catch {
    // 用户取消
  }
}

// 完成操作
const handleComplete = async (row: OperationItem) => {
  try {
    await ElMessageBox.confirm('确认完成此操作任务?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    // 模拟API调用
    ElMessage.success('操作任务已完成')
    row.operationStatus = 2
    row.endTime = new Date().toISOString()
    row.completedQuantity = row.plannedQuantity
  } catch {
    // 用户取消
  }
}

// 查看详情
const handleView = (row: OperationItem) => {
  detailForm.value = { ...row }
  detailOpen.value = true
}

// 删除操作
const handleDelete = async (row: OperationItem) => {
  try {
    await ElMessageBox.confirm('确认删除此操作任务?', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    // 模拟API调用
    const index = operationList.value.findIndex((item) => item.operationId === row.operationId)
    if (index !== -1) {
      operationList.value.splice(index, 1)
      total.value = operationList.value.length
      ElMessage.success('删除成功')
    }
  } catch {
    // 用户取消
  }
}

// 重置表单
const resetForm = () => {
  form.operationId = ''
  form.workOrderId = ''
  form.stepId = ''
  form.operatorId = ''
  form.equipmentId = ''
  form.plannedQuantity = 1
  form.parameters = ''
  form.remark = ''

  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return

  const valid = await formRef.value.validate()
  if (!valid) return

  try {
    if (form.operationId) {
      // 更新操作
      const index = operationList.value.findIndex((item) => item.operationId === form.operationId)
      if (index !== -1) {
        const workOrder = workOrderOptions.value.find((w) => w.workOrderId === form.workOrderId)
        const step = stepOptions.value.find((s) => s.stepId === form.stepId)
        const operator = operatorOptions.value.find((o) => o.userId === form.operatorId)
        const equipment = equipmentOptions.value.find((e) => e.equipmentId === form.equipmentId)

        operationList.value[index] = {
          ...operationList.value[index],
          workOrderId: form.workOrderId,
          workOrderNo: workOrder?.workOrderNo || '',
          productName: workOrder?.productName || '',
          stepId: form.stepId,
          stepName: step?.stepName || '',
          operatorId: form.operatorId,
          operatorName: operator?.userName || '',
          equipmentId: form.equipmentId,
          equipmentCode: equipment?.equipmentCode || '',
          plannedQuantity: form.plannedQuantity,
          parameters: form.parameters,
          remark: form.remark,
        }
      }
      ElMessage.success('更新成功')
    } else {
      // 新增操作
      const workOrder = workOrderOptions.value.find((w) => w.workOrderId === form.workOrderId)
      const step = stepOptions.value.find((s) => s.stepId === form.stepId)
      const operator = operatorOptions.value.find((o) => o.userId === form.operatorId)
      const equipment = equipmentOptions.value.find((e) => e.equipmentId === form.equipmentId)

      const newOperation: OperationItem = {
        operationId: 'OP' + Date.now(),
        operationCode:
          'OP' +
          new Date()
            .toISOString()
            .replace(/[-:T.Z]/g, '')
            .slice(0, 14),
        workOrderId: form.workOrderId,
        workOrderNo: workOrder?.workOrderNo || '',
        productName: workOrder?.productName || '',
        stepId: form.stepId,
        stepName: step?.stepName || '',
        operatorId: form.operatorId,
        operatorName: operator?.userName || '',
        equipmentId: form.equipmentId,
        equipmentCode: equipment?.equipmentCode || '',
        plannedQuantity: form.plannedQuantity,
        completedQuantity: 0,
        operationStatus: 0,
        startTime: '',
        endTime: '',
        parameters: form.parameters,
        qualityResult: '',
        remark: form.remark,
      }

      operationList.value.unshift(newOperation)
      total.value = operationList.value.length
      ElMessage.success('创建成功')
    }

    dialogOpen.value = false
    resetForm()
  } catch (error) {
    console.error('保存失败:', error)
    ElMessage.error('保存失败')
  }
}

// 取消操作
const cancel = () => {
  dialogOpen.value = false
  resetForm()
}
</script>

<style scoped>
.production-operation {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.search-filter {
  margin-bottom: 20px;
}

.filter-card {
  border: none;
}

.operation-stats {
  margin-bottom: 20px;
}

.stat-card {
  border: none;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon .el-icon {
  font-size: 24px;
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  line-height: 1;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #909399;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.json-preview {
  margin: 0;
  padding: 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
  max-height: 200px;
  overflow: auto;
}
</style>
