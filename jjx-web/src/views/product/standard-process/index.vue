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
        <el-form-item label="启用状态" prop="isEnabled">
          <el-select
            v-model="queryParams.isEnabled"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="启用" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <el-button type="primary" icon="Plus" @click="handleAdd">新增标准工序</el-button>
        <el-button type="success" plain icon="Upload" @click="openImportDialog">导入</el-button>
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

        <el-table-column prop="displayOrder" label="排序" width="60" align="center" />
        <el-table-column prop="isEnabled" label="启用状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isEnabled === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.isEnabled === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="handleEdit(scope.row)">
              编辑
            </el-button>
            <el-button
              link
              :type="scope.row.isEnabled === 1 ? 'warning' : 'success'"
              size="small"
              @click="handleToggleEnabled(scope.row)"
            >
              {{ scope.row.isEnabled === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row)">
              删除
            </el-button>
          </template>
        </el-table-column>
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
  </div>

</template>

<script setup lang="ts">
defineOptions({
  name: 'StandardProcess',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { UploadFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { standardProcessApi } from '@/api/product/standardProcess'
import ExcelImportDialog from '@/components/ExcelImportDialog/index.vue'
import { useDict } from '@/composables/useDict'
import type {
  StandardProcessQueryParams,
  StandardProcessItem,
} from '@/types/product/standardProcess'
import type { PageResult } from '@/types'

const router = useRouter()

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
  router.push('/product/standard-process/add')
}

// ==================== 编辑 ====================
const handleEdit = (row: StandardProcessItem) => {
  router.push(`/product/standard-process/edit/${row.processId}`)
}

// ==================== 启用/禁用 ====================
const handleToggleEnabled = async (row: StandardProcessItem) => {
  const action = row.isEnabled === 1 ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定要${action}工序 "${row.processName}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    if (row.isEnabled === 1) {
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

