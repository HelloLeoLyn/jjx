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

    <!-- 导入对话框（2026-08-08，照物料导入模式） -->
    <el-dialog title="导入标准工序" v-model="importDialogVisible" width="500px" append-to-body>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        accept=".xlsx,.xls"
        drag
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            <p>仅支持 .xlsx / .xls 格式的Excel文件</p>
            <p>表头需包含：工序编码、工序名称、工序类型、工序类别等</p>
            <el-button link type="primary" @click="handleDownloadTemplate">
              下载导入模板
            </el-button>
          </div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="handleImportCancel">取消</el-button>
        <el-button type="primary" :loading="importLoading" @click="handleImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>

    <!-- 导入结果弹窗（失败明细可下载） -->
    <el-dialog title="导入结果" v-model="importResultVisible" width="640px" append-to-body>
      <template v-if="importResult">
        <el-alert
          :title="`导入完成：成功 ${importResult.successCount} 条，跳过重复 ${importResult.skipCount} 条，失败 ${importResult.failCount} 条`"
          :type="importResult.failCount > 0 ? 'warning' : 'success'"
          show-icon
          :closable="false"
          style="margin-bottom: 12px"
        />
        <el-table v-if="importResult.failDetails?.length > 0" :data="importResult.failDetails" border max-height="360" size="small" style="width: 100%">
          <el-table-column label="行号" prop="rowIndex" width="80" align="center" />
          <el-table-column label="工序" prop="materialName" min-width="160" show-overflow-tooltip />
          <el-table-column label="失败原因" prop="reason" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button type="primary" plain @click="handleDownloadFail">下载失败明细</el-button>
        <el-button type="primary" @click="importResultVisible = false; handleImportCancel()">完成</el-button>
      </template>
    </el-dialog>

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

// ==================== 导入（2026-08-08，照物料导入模式） ====================
const importDialogVisible = ref(false)
const importResultVisible = ref(false)
const importResult = ref<any>(null)
const importLoading = ref(false)
const uploadRef = ref<any>()
const importFile = ref<File | null>(null)

function openImportDialog() {
  importDialogVisible.value = true
}

const handleFileChange = (file: any) => {
  importFile.value = file.raw
}

const handleExceed = (files: File[]) => {
  uploadRef.value?.clearFiles()
  uploadRef.value?.handleStart(files[0])
}

const handleImportCancel = () => {
  importDialogVisible.value = false
  importFile.value = null
  uploadRef.value?.clearFiles()
}

const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }
  importLoading.value = true
  try {
    const res: any = await standardProcessApi.importProcesses(importFile.value)
    const data = res.data as any
    importResult.value = data
    importResultVisible.value = true
    if (!data?.failDetails?.length) {
      ElMessage.success(`导入完成：成功 ${data?.successCount ?? 0} 条，跳过 ${data?.skipCount ?? 0} 条`)
    }
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    importLoading.value = false
    importDialogVisible.value = false
    importFile.value = null
    uploadRef.value?.clearFiles()
  }
}

// 下载模板
const handleDownloadTemplate = async () => {
  try {
    const res: any = await standardProcessApi.importTemplate()
    // request 拦截器对 blob 直接返回 Blob 本身（照物料页写法）
    const blob = new Blob([res as any], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '标准工序导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch {
    ElMessage.error('模板下载失败')
  }
}

// 下载失败明细
const handleDownloadFail = () => {
  const details = importResult.value?.failDetails || []
  if (details.length === 0) return
  const wb = XLSX.utils.book_new()
  const rows = details.map((d: any) => ({
    行号: d.rowIndex,
    工序: d.materialName,
    失败原因: d.reason,
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  XLSX.utils.book_append_sheet(wb, ws, '失败明细')
  XLSX.writeFile(wb, `标准工序导入失败明细_${new Date().toISOString().slice(0, 10)}.xlsx`)
}
</script>

<style scoped>
.standard-process-container {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.operation-bar {
  display: flex;
  justify-content: flex-start;
  align-items: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
