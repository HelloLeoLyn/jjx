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
        <el-button type="danger" size="small" v-hasPermi="['inventory:material:delete']" @click="() => handleDelete(null)">
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
            <el-button link type="primary" v-hasPermi="['inventory:material:edit']" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="['inventory:material:delete']" @click="handleDelete(row)">删除</el-button>
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

    <!-- 物料表单对话框 -->
    <MaterialFormDialog
      v-model="dialogVisible"
      :material-id="editingMaterialId"
      @success="handleFormSuccess"
    />

    <!-- 通用导入弹窗（2026-08-13，含结果/失败明细） -->
    <ExcelImportDialog
      :visible="importDialogVisible"
      @update:visible="importDialogVisible = $event"
      title="导入物料"
      :import-api="materialApi.importExcel"
      :template-api="materialApi.downloadImportTemplate"
      template-name="物料导入模板.xlsx"
      @success="getList"
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
import ExcelImportDialog from '@/components/ExcelImportDialog/index.vue'
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

// 导入（2026-08-13 通用 ExcelImportDialog 组件）
const importDialogVisible = ref(false)

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
  if (key === 'import') importDialogVisible.value = true
  if (key === 'export') handleExport()
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
