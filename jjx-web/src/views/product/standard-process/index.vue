<template>
  <div class="standard-process-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" :inline="true" label-width="80px">
        <el-form-item label="工序编码" prop="processCode">
          <el-input
            v-model="queryParams.processCode"
            placeholder="请输入工序编码"
            clearable
            style="width: 180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="工序名称" prop="processName">
          <el-input
            v-model="queryParams.processName"
            placeholder="请输入工序名称"
            clearable
            style="width: 180px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="工序类型" prop="processType">
          <el-select
            v-model="queryParams.processType"
            placeholder="请选择工序类型"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in processTypeOptions"
              :key="item.itemValue"
              :label="item.label"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="工序类别" prop="processCategory">
          <el-select
            v-model="queryParams.processCategory"
            placeholder="请选择工序类别"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in processCategoryOptions"
              :key="item.itemValue"
              :label="item.label"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="审核状态">
          <el-select
            v-model="queryParams.processStatus"
            clearable
            placeholder="全部"
            style="width: 140px"
          >
            <el-option label="待审核" :value="StandardProcessStatus.PENDING" />
            <el-option label="正式" :value="StandardProcessStatus.FORMAL" />
            <el-option label="已驳回" :value="StandardProcessStatus.REJECTED" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态" prop="isEnabled">
          <el-select
            v-model="queryParams.isEnabled"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="启用" :value="CommonStatusEnum.NORMAL.value" />
            <el-option label="禁用" :value="CommonStatusEnum.DISABLED.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <div class="operation-bar">
        <el-button v-hasPermi="['engineering:standardProcess:add']" @click="openTempDialog"
          >新增临时工序</el-button
        >
        <el-button
          type="primary"
          icon="Plus"
          v-hasPermi="['engineering:standardProcess:add']"
          @click="handleAdd"
          >新增标准工序</el-button
        >
        <el-button
          type="success"
          plain
          icon="Upload"
          v-hasPermi="['engineering:standardProcess:add']"
          @click="openImportDialog"
          >导入</el-button
        >
      </div>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="icon" label="图标" width="90" align="center">
          <template #default="scope">
            <SvgIcon :name="scope.row.icon" :size="20" />
          </template>
        </el-table-column>
        <!-- <el-table-column label="组件预览" width="150" align="center">
          <template #default="scope">
            <ProcessOperation :items="[previewItem(scope.row)]" />
          </template>
        </el-table-column> -->
        <el-table-column prop="processCode" label="工序编码" width="140" />
        <el-table-column
          prop="processName"
          label="工序名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column prop="processType" label="工序类型" width="130" align="center">
          <template #default="scope">
            <el-tag size="small">
              {{ getProcessTypeLabel(scope.row.processType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="processCategory" label="工序类别" width="100" align="center">
          <template #default="scope">
            <el-tag size="small">
              {{ getProcessCategoryLabel(scope.row.processCategory) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="standardLaborHours" label="人工工时" width="90" align="right" />
        <el-table-column prop="standardMachineHours" label="机器工时" width="90" align="right" />

        <el-table-column prop="hasIndex" label="带下标" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.hasIndex === 1 ? 'warning' : 'info'" size="small">
              {{ scope.row.hasIndex === 1 ? '带下标' : '不带' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="hasWorkInstruction" label="作业说明" width="100" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.hasWorkInstruction === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.hasWorkInstruction === 1 ? '可填写' : '不带' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="displayOrder" label="排序" width="60" align="center" />
        <el-table-column prop="isEnabled" label="启用状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="rowEnabled(scope.row) ? 'success' : 'info'" size="small">
              {{ rowEnabled(scope.row) ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.processStatus === StandardProcessStatus.PENDING" type="warning"
              >待审核</el-tag
            >
            <el-tag v-else-if="row.processStatus === StandardProcessStatus.REJECTED" type="danger"
              >已驳回</el-tag
            >
            <el-tag v-else type="success">正式</el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="rejectReason"
          label="驳回原因"
          min-width="140"
          show-overflow-tooltip
        />
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <TableActionColumn
          :actions="processActions"
          :min-width="220"
          display="text"
          @action="handleProcessAction"
        />
      </el-table>

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
    <!-- 通用导入弹窗（2026-08-13，含结果/失败明细） -->
    <ExcelImportDialog
      :visible="importDialogVisible"
      @update:visible="importDialogVisible = $event"
      title="导入标准工序"
      :import-api="standardProcessApi.importProcesses"
      :template-api="standardProcessApi.importTemplate"
      template-name="标准工序导入模板.xlsx"
      @success="loadData"
    />
    <StandardProcessFormDialog
      v-model="processDialogVisible"
      :process-id="processDialogId"
      @success="loadData"
    />
    <el-dialog v-model="tempDialogVisible" title="新增临时工序" width="480px">
      <el-form label-width="80px">
        <el-form-item label="名称" required
          ><el-input v-model="tempForm.processName" maxlength="100"
        /></el-form-item>
        <el-form-item label="类别" required>
          <el-select v-model="tempForm.processCategory">
            <el-option
              v-for="item in processCategoryOptions"
              :key="item.itemValue"
              :label="item.label"
              :value="item.itemValue"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="说明" required
          ><el-input v-model="tempForm.description" type="textarea" maxlength="500"
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="tempDialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="tempSaving" @click="saveTempProcess"
          >提交待审</el-button
        ></template
      >
    </el-dialog>
    <StandardProcessPreviewDrawer v-model="previewVisible" :process="previewProcess" />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StandardProcess',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Edit, Delete, CircleCheck, CircleClose, View } from '@element-plus/icons-vue'

import { StandardProcessStatus } from '@/types/product/standardProcess'
import { useEnabledProcesses } from '@/composables/useEnabledProcesses'
import { standardProcessApi } from '@/api/product/standardProcess'
import ExcelImportDialog from '@/components/ExcelImportDialog/index.vue'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import StandardProcessFormDialog from './components/StandardProcessFormDialog.vue'
import StandardProcessPreviewDrawer from './components/StandardProcessPreviewDrawer.vue'
import ProcessOperation from '@/components/ProcessOperation/index.vue'
import type { ProcessOperationItem } from '@/components/ProcessOperation/types'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'
import { useDict } from '@/composables/useDict'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import type {
  StandardProcessQueryParams,
  StandardProcessItem,
} from '@/types/product/standardProcess'

// 工序类型/类别选项（字典维护）
const { options: processTypeOptions } = useDict('process_type')
const { options: processCategoryOptions } = useDict('process_category')

function getProcessTypeLabel(value: string): string {
  return processTypeOptions.value.find((i) => i.itemValue === value)?.label || value || '未知'
}

function getProcessCategoryLabel(value: string): string {
  return processCategoryOptions.value.find((i) => i.itemValue === value)?.label || value || '未知'
}

// ==================== 查询参数 ====================
const queryParams = reactive<StandardProcessQueryParams>({
  pageNum: 1,
  pageSize: 10,
  processCode: undefined,
  processName: undefined,
  processType: undefined,
  processCategory: undefined,
  isEnabled: undefined,
  orderByColumn: 'displayOrder',
  isAsc: 'asc',
})

// ==================== 表格数据 ====================
const tableData = ref<StandardProcessItem[]>([])
const total = ref(0)
const loading = ref(false)

const rowEnabled = (row: StandardProcessItem) => row.isEnabled === CommonStatusEnum.NORMAL.value

const previewVisible = ref(false)
const previewProcess = ref<StandardProcessItem | null>(null)
const processDialogVisible = ref(false)
const processDialogId = ref<number | undefined>(undefined)

const previewItem = (row: StandardProcessItem): ProcessOperationItem => ({
  key: row.processId,
  icon: row.icon,
  processName: row.processName,
  indexNumber: row.hasIndex === 1 ? 4 : null,
  workInstruction: row.hasWorkInstruction === 1 ? '作业说明' : undefined,
})

const tempDialogVisible = ref(false)
const tempSaving = ref(false)
const tempForm = reactive({ processName: '', processCategory: '', description: '' })
const openTempDialog = () => {
  Object.assign(tempForm, { processName: '', processCategory: '', description: '' })
  tempDialogVisible.value = true
}
const saveTempProcess = async () => {
  if (!tempForm.processName.trim() || !tempForm.processCategory || !tempForm.description.trim()) {
    ElMessage.warning('名称、类别、说明必填')
    return
  }
  tempSaving.value = true
  const payload = { ...tempForm }
  try {
    try {
      await standardProcessApi.createTemp(payload)
    } catch (error) {
      if (!(error instanceof Error) || !('code' in error) || error.code !== 40901) throw error
      await ElMessageBox.confirm(error.message, '近名工序确认', { type: 'warning' })
      await standardProcessApi.createTemp({ ...payload, force: true })
    }
    tempDialogVisible.value = false
    ElMessage.success('临时工序已提交待审')
    await Promise.all([loadData(), reloadProcesses()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('创建临时工序失败:', error)
  } finally {
    tempSaving.value = false
  }
}

const { reload: reloadProcesses } = useEnabledProcesses(false)
const processActions: TableAction<StandardProcessItem>[] = [
  {
    key: 'approve',
    label: '审核通过',
    type: 'success',
    permission: 'product:standardProcess:approve',
    visible: ({ row }) => row.processStatus === StandardProcessStatus.PENDING,
  },
  {
    key: 'reject',
    label: '驳回',
    type: 'danger',
    permission: 'product:standardProcess:approve',
    visible: ({ row }) => row.processStatus === StandardProcessStatus.PENDING,
  },
  {
    key: 'preview',
    label: '预览',
    icon: View,
    type: 'info',
  },
  {
    key: 'edit',
    label: '编辑',
    icon: Edit,
    type: 'primary',
    permission: 'engineering:standardProcess:edit',
  },
  {
    key: 'toggle',
    label: ({ row }) => (rowEnabled(row) ? '禁用' : '启用'),
    icon: ({ row }: { row: StandardProcessItem }) => (rowEnabled(row) ? CircleClose : CircleCheck),
    type: ({ row }) => (rowEnabled(row) ? 'warning' : 'success'),
    permission: 'engineering:standardProcess:edit',
  },
  {
    key: 'delete',
    visible: ({ row }) => row.processStatus === StandardProcessStatus.FORMAL,
    label: '删除',
    icon: Delete,
    type: 'danger',
    permission: 'engineering:standardProcess:edit',
  },
]

const handleProcessAction = (key: string, row: StandardProcessItem) => {
  if (key === 'approve' || key === 'reject') void handleReview(key, row)
  if (key === 'preview') handlePreview(row)
  if (key === 'edit') handleEdit(row)
  if (key === 'toggle') void handleToggleEnabled(row)
  if (key === 'delete') handleDelete(row)
}

const handleReview = async (action: 'approve' | 'reject', row: StandardProcessItem) => {
  try {
    if (action === 'approve') {
      await ElMessageBox.confirm(`确认将临时工序「${row.processName}」转正？`, '审核工序')
      await standardProcessApi.approve(row.processId)
    } else {
      const { value } = await ElMessageBox.prompt('请填写驳回原因', '驳回工序', {
        inputValidator: (value) =>
          (!!value?.trim() && value.length <= 500) || '原因必填且不超过500字',
      })
      await standardProcessApi.reject(row.processId, value.trim())
    }
    ElMessage.success('操作成功')
    await Promise.all([loadData(), reloadProcesses()])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') console.error('工序审核失败:', error)
  }
}

const handlePreview = (row: StandardProcessItem) => {
  previewProcess.value = row
  previewVisible.value = true
}

// ==================== 数据加载 ====================
const loadData = async () => {
  loading.value = true
  try {
    const response = await standardProcessApi.pageQuery(queryParams)
    const result = response.data
    if (result) {
      tableData.value = result.records || []
      total.value = result.total || 0
    }
  } catch (error) {
    console.error('加载标准工序列表失败:', error)
    ElMessage.error('加载标准工序列表失败')
  } finally {
    loading.value = false
  }
}

// ==================== 搜索 ====================
const handleQuery = () => {
  queryParams.pageNum = 1
  loadData()
}

const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    processCode: undefined,
    processName: undefined,
    processType: undefined,
    processCategory: undefined,
    processStatus: undefined,
    isEnabled: undefined,
    orderByColumn: 'displayOrder',
    isAsc: 'asc',
  })
  loadData()
}

// ==================== 分页 ====================
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  loadData()
}

const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  loadData()
}

// ==================== 新增 ====================
const handleAdd = () => {
  processDialogId.value = undefined
  processDialogVisible.value = true
}

// ==================== 编辑 ====================
const handleEdit = (row: StandardProcessItem) => {
  processDialogId.value = row.processId
  processDialogVisible.value = true
}

// ==================== 启用/禁用 ====================
const handleToggleEnabled = async (row: StandardProcessItem) => {
  const action = rowEnabled(row) ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}工序 "${row.processName}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    if (rowEnabled(row)) {
      await standardProcessApi.disable(row.processId)
    } else {
      await standardProcessApi.enable(row.processId)
    }
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (error) {
    if (error === 'cancel') return
    console.error(`${action}工序失败:`, error)
  }
}

// ==================== 删除 ====================
const handleDelete = (row: StandardProcessItem) => {
  ElMessageBox.confirm(`确定要删除工序 "${row.processName}" (${row.processCode}) 吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(async () => {
      try {
        await standardProcessApi.remove(row.processId)
        ElMessage.success('删除成功')
        loadData()
      } catch (error) {
        console.error('删除工序失败:', error)
      }
    })
    .catch(() => {
      // 取消删除
    })
}

onMounted(() => {
  loadData()
})

// 导入（2026-08-13 通用 ExcelImportDialog 组件）
const importDialogVisible = ref(false)
function openImportDialog() {
  importDialogVisible.value = true
}
</script>
