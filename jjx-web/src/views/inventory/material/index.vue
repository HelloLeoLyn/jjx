<template>
  <div class="material-list">
    <!-- 搜索栏 -->
    <SearchForm
      v-model="queryParams"
      :fields="searchFields"
      @search="handleQuery"
      @reset="handleReset"
    />

    <!-- 操作栏 -->
    <Toolbar
      :buttons="toolbarButtons"
      :selected-count="ids.length"
      :show-batch-bar="true"
      @click="handleToolbarClick"
      @refresh="getList"
    >
      <template #batch-actions>
        <el-button type="danger" size="small" @click="() => handleDelete(null)">
          批量删除
        </el-button>
      </template>
    </Toolbar>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="materialList"
        @selection-change="handleSelectionChange"
        border
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="物料编码" prop="materialCode" width="150" />
        <el-table-column label="物料名称" prop="materialName" width="200" show-overflow-tooltip />
        <el-table-column label="机种" prop="materialNameEn" width="200" />
        <el-table-column label="物料类型" prop="materialType" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="MaterialTypeEnum.getTagProps(row.materialType).type as any" size="small">
              {{ MaterialTypeEnum.getLabel(row.materialType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格型号" prop="specification" width="150" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="安全库存" prop="safeStock" width="100" align="right" />
        <el-table-column label="标准单价" prop="standardPrice" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.standardPrice) }} </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              :active-value="1"
              :inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="供应商" prop="supplierName" width="200" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 导入对话框 -->
    <el-dialog title="导入物料" v-model="importDialogVisible" width="500px" append-to-body>
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        accept=".xlsx,.xls"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            <p>仅支持 .xlsx / .xls 格式的Excel文件</p>
            <p>表头需包含：材料、规格、供应商、备注</p>
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

    <!-- DEV-702：导入结果弹窗（失败明细可下载） -->
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
          <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
          <el-table-column label="失败原因" prop="reason" min-width="220" show-overflow-tooltip />
        </el-table>
      </template>
      <template #footer>
        <el-button type="primary" plain @click="handleDownloadFail">下载失败明细</el-button>
        <el-button type="primary" @click="importResultVisible = false; handleImportCancel()">完成</el-button>
      </template>
    </el-dialog>

    <!-- 物料表单对话框 -->
    <MaterialFormDialog
      v-model="dialogVisible"
      :material-id="editingMaterialId"
      @success="handleFormSuccess"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InventoryMaterial',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import SearchForm from '@/components/common-ui/SearchForm.vue'
import Toolbar from '@/components/common-ui/Toolbar.vue'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import { materialApi } from '@/api/inventory/material'
import { formatCurrency } from '@/utils/format'
import type { InventoryMaterial, InventoryMaterialQueryParams } from '@/types/inventory/material'
import type { SearchOptions, ToolbarOptions } from '@/components/common-ui/type'
import { searchOptions, toolbarOptions } from './config'
import { MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
import type { UploadInstance } from 'element-plus'

const router = useRouter()

// ==================== 配置 ====================

// 搜索表单配置
const searchFields: SearchOptions[] = searchOptions

// 工具栏配置
const toolbarButtons: ToolbarOptions[] = toolbarOptions

// 表格列配置

// 查询参数
const queryParams = reactive<InventoryMaterialQueryParams>({
  pageNum: 1,
  pageSize: 10,
  materialCode: '',
  materialName: '',
  materialType: '',
  specification: '',
  status: '',
})

// 响应式数据
const loading = ref(false)
const materialList = ref<InventoryMaterial[]>([])
const total = ref(0)
const ids = ref<string[]>([])
const single = ref(true)
const multiple = ref(true)
const dialogVisible = ref(false)
const editingMaterialId = ref<number | null>(null)

// 导入相关
const importDialogVisible = ref(false)
// DEV-702：导入结果（失败明细可下载）
const importResult = ref<any>(null)
const importResultVisible = ref(false)
const importLoading = ref(false)
const uploadRef = ref<UploadInstance>()
const importFile = ref<File | null>(null)

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await materialApi.page(queryParams)
    materialList.value = res.data?.records || []
    total.value = res.data?.total || 0
    console.log(total.value)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.materialCode = ''
  queryParams.materialName = ''
  queryParams.materialType = ''
  queryParams.status = ''
  getList()
}

// 多选框选中
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.materialId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 新增
const handleAdd = () => {
  editingMaterialId.value = null
  dialogVisible.value = true
}

// 编辑
const handleEdit = (row: any) => {
  editingMaterialId.value = row.materialId
  dialogVisible.value = true
}

// 表单提交成功回调
const handleFormSuccess = () => {
  getList()
}

// 查看详情
const handleView = (row: any) => {
  router.push(`/inventory/material/detail/${row.materialId}`)
}

const handleDelete = (row: any) => {
  const materialIds = row ? [row.materialId] : ids.value
  ElMessageBox.confirm('确认删除选中的物料吗？', '提示', { type: 'warning' })
    .then(() => materialApi.delete(materialIds))
    .then(() => {
      ElMessage.success('删除成功')
      getList()
    })
    .catch(() => {})
}

// 状态变更
const handleStatusChange = async (row: any) => {
  await materialApi.updateStatus(row.materialId, row.status)
  ElMessage.success('状态更新成功')
}

// 工具栏按钮点击事件
const handleToolbarClick = (key: string) => {
  if (key === 'add') handleAdd()
  if (key === 'import') handleShowImport()
  if (key === 'export') handleExport()
}

// 显示导入对话框
const handleShowImport = () => {
  importFile.value = null
  importDialogVisible.value = true
}

// 文件选择变更
const handleFileChange = (uploadFile: any) => {
  importFile.value = uploadFile.raw
}

// 文件数量超出限制
const handleExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

// 下载导入模板
const handleDownloadTemplate = async () => {
  try {
    const res = await materialApi.downloadImportTemplate()
    const blob = new Blob([res as any], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '物料导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (error) {
    ElMessage.error('下载模板失败')
  }
}

// 取消导入
const handleImportCancel = () => {
  importDialogVisible.value = false
  importFile.value = null
  // 清除上传组件中的文件列表
  uploadRef.value?.clearFiles()
}

// 执行导入（DEV-702：结果显示+失败明细可下载）
const handleImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }

  importLoading.value = true
  try {
    const res: any = await materialApi.importExcel(importFile.value)
    const data = res.data as any
    const successCount = data?.successCount ?? 0
    const skipCount = data?.skipCount ?? 0
    const failDetails = data?.failDetails || []

    if (failDetails.length > 0) {
      // 有失败：展示结果+提供下载失败明细
      importResult.value = data
      importResultVisible.value = true
      ElMessage.warning(
        `导入完成：成功 ${successCount} 条，跳过重复 ${skipCount} 条，失败 ${failDetails.length} 条`
      )
    } else {
      ElMessage.success(`导入完成：成功 ${successCount} 条${skipCount > 0 ? `，跳过重复 ${skipCount} 条` : ''}`)
      importDialogVisible.value = false
      importFile.value = null
      uploadRef.value?.clearFiles()
      getList()
    }
  } catch (error: any) {
    ElMessage.error(error?.msg || '导入失败')
  } finally {
    importLoading.value = false
  }
}

// 下载失败明细（DEV-702）
const handleDownloadFail = () => {
  const details = importResult.value?.failDetails || []
  if (details.length === 0) return
  const wb = XLSX.utils.book_new()
  const rows = details.map((d: any) => ({
    行号: d.rowIndex,
    物料名称: d.materialName,
    失败原因: d.reason,
  }))
  const ws = XLSX.utils.json_to_sheet(rows)
  XLSX.utils.book_append_sheet(wb, ws, '失败明细')
  XLSX.writeFile(wb, `物料导入失败明细_${new Date().toISOString().slice(0, 10)}.xlsx`)
}

// 导出
const handleExport = () => {
  const loading = ElLoading.service({ text: '导出中...', lock: true })
  materialApi
    .export(queryParams)
    .finally(() => loading.close())
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.material-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}

.low-stock {
  color: #f56c6c;
  font-weight: bold;
}
</style>
