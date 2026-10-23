<template>
  <div class="material-list">
    <!-- 搜索栏 -->
    <SearchForm
      v-model="queryParams"
      :fields="searchFields"
      @search="handleQuery"
      @reset="handleReset"
    >
      <template #tagIds>
        <TagQuerySelect
          v-model="tagIdsModel"
          v-model:match-mode="tagMatchModeModel"
          biz-type="inventory_material"
          block
        />
      </template>
    </SearchForm>

    <!-- 操作栏 -->
    <Toolbar
      :buttons="toolbarButtons"
      :selected-count="ids.length"
      :show-batch-bar="true"
      @click="handleToolbarClick"
      @refresh="getList"
    >
      <template #batch-actions>
        <el-button
          type="danger"
          size="small"
          v-hasPermi="['inventory:material:delete']"
          @click="() => handleDelete(null)"
        >
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
        <el-table-column label="原名称" width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ originalNameOf(row) }}</template>
        </el-table-column>

        <el-table-column label="物料名称" prop="materialName" width="220" show-overflow-tooltip />
        <el-table-column label="品类" prop="categoryId" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryNameOf(row) }}</template>
        </el-table-column>
        <el-table-column label="材料" prop="materialGrade" width="110" />
        <el-table-column label="型号" prop="model" width="120" show-overflow-tooltip />
        <el-table-column label="颜色" prop="color" width="90" />
        <el-table-column label="品牌" prop="brand" width="120" show-overflow-tooltip />
        <el-table-column label="尺寸" prop="size" width="140" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="安全库存" prop="safeStock" width="100" align="right" />
        <el-table-column label="标准单价" prop="standardPrice" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.standardPrice) }} </template>
        </el-table-column>
        <el-table-column label="供应商" prop="supplierName" width="200" />
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
        <el-table-column label="物料标签" min-width="180">
          <template #default="{ row }">
            <el-tag
              v-for="tag in row.tags || []"
              :key="tag.tagId"
              size="small"
              class="material-tag"
            >
              {{ tag.tagName }}
            </el-tag>
            <span v-if="!row.tags?.length">-</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" width="200" show-overflow-tooltip />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button
              link
              type="primary"
              v-hasPermi="['inventory:material:edit']"
              @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button
              link
              type="primary"
              v-hasPermi="['inventory:material:edit']"
              @click="handleRegenerate(row)"
            >
              重新生成
            </el-button>
            <el-button
              link
              type="danger"
              v-hasPermi="['inventory:material:delete']"
              @click="handleDelete(row)"
              >删除</el-button
            >
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

    <!-- 物料详情弹窗（不再跳路由页） -->
    <MaterialDetailDialog v-model="detailVisible" :material-id="detailMaterialId" />

    <!-- 重新生成（单项，按规则重算属性，逐项可改） -->
    <MaterialRegenerateDialog
      v-model="regenerateVisible"
      :material-id="regenerateMaterialId"
      @success="getList"
    />

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
      :tip="importTip"
      @success="getList"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InventoryMaterial',
})

import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import SearchForm from '@/components/common-ui/SearchForm.vue'
import Toolbar from '@/components/common-ui/Toolbar.vue'
import TagQuerySelect from '@/components/TagQuerySelect.vue'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import MaterialDetailDialog from '@/components/inventory/MaterialDetailDialog.vue'
import MaterialRegenerateDialog from '@/components/inventory/MaterialRegenerateDialog.vue'
import { materialApi } from '@/api/inventory/material'
import ExcelImportDialog from '@/components/ExcelImportDialog/index.vue'
import { formatCurrency } from '@/utils/format'
import type { InventoryMaterial, InventoryMaterialQueryParams } from '@/types/inventory/material'
import type { SearchOptions, ToolbarOptions } from '@/components/common-ui/type'
import { searchOptions, toolbarOptions } from './config'
import {
  loadMaterialCategoryOptions,
  type MaterialCategoryOption,
} from '@/components/inventory/materialMasterFields'
import { parseOriginalName } from '@/components/inventory/materialRegenerate'
import type { UploadInstance } from 'element-plus'
import { openLabelPrint } from '@/utils/labelPrint'

const router = useRouter()

// ==================== 配置 ====================

// 搜索表单配置（品类选项异步注入，见 loadCategoryOptions）
const searchFields = ref<SearchOptions[]>(searchOptions.map((item) => ({ ...item })))

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
  tagIds: [],
  tagMatchMode: 'AND',
  categoryId: undefined,
  materialGrade: '',
  model: '',
  color: '',
  specification: '',
  status: '',
})

// 标签筛选绑定（queryParams 字段可选，这里做非空归一，dev-20260912-007）
const tagIdsModel = computed({
  get: () => queryParams.tagIds ?? [],
  set: (value: number[]) => {
    queryParams.tagIds = value
  },
})
const tagMatchModeModel = computed({
  get: () => queryParams.tagMatchMode ?? 'AND',
  set: (value: 'AND' | 'OR') => {
    queryParams.tagMatchMode = value
  },
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
const detailVisible = ref(false)
const detailMaterialId = ref<number | null>(null)
const regenerateVisible = ref(false)
const regenerateMaterialId = ref<number | null>(null)

// 导入（2026-08-13 通用 ExcelImportDialog 组件）
const importDialogVisible = ref(false)
const importTip =
  '1）请先查看模板中的「填写说明」页；数据页第2行为示例，必须删除后从第2行填写真实数据。' +
  '2）表头(*)为必填提示：物料名称须提供，其余按品类必填矩阵填写；建议明确品类。' +
  '3）材料23词、颜色13色及逐列含义见说明页；名称按“材料+型号+颜色（品牌）”填写。' +
  '4）尺寸可选，格式为“宽×长/厚”，缺省mm，可写cm/m/inch。' +
  '5）旧模板（仅物料名称+规格）仍兼容；系统尝试识别品类和结构化字段，缺少品类必填项时须补齐后重试。' +
  '6）本入口导入物料档案，数量列不写入库存；请勿把说明文字放入数据区。'

// 品类下拉（接口不可用时用本地种子列表兜底）
const categoryOptions = ref<MaterialCategoryOption[]>([])
const loadCategoryOptions = async () => {
  const options = await loadMaterialCategoryOptions()
  categoryOptions.value = options
  const field = searchFields.value.find((item) => item.prop === 'categoryId')
  if (field) {
    field.options = options.map((item) => ({ value: item.categoryId, label: item.categoryName }))
  }
}

// 列表列的品类名：后端 MaterialVO.categoryName 未赋值，这里按已加载品类映射
const categoryNameOf = (row: InventoryMaterial) => {
  if (row.categoryId == null) return '-'
  return (
    categoryOptions.value.find((item) => item.categoryId === row.categoryId)?.categoryName || '-'
  )
}

// 列表列的「原名称」：remark 里由回填脚本写入的【原名称】，便于核对重写后的名称（dev-20261023-020）
const originalNameOf = (row: InventoryMaterial) => parseOriginalName(row.remark) || '-'

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
  queryParams.categoryId = undefined
  queryParams.materialGrade = ''
  queryParams.model = ''
  queryParams.color = ''
  queryParams.tagIds = []
  queryParams.tagMatchMode = 'AND'
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

// 重新生成（单项）
const handleRegenerate = (row: any) => {
  regenerateMaterialId.value = row.materialId
  regenerateVisible.value = true
}

// 查看详情（弹窗，不跳路由页）
const handleView = (row: any) => {
  detailMaterialId.value = row.materialId
  detailVisible.value = true
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
  if (key === 'print-label') {
    if (!ids.value.length) {
      ElMessage.warning('请先选择要打印的物料')
      return
    }
    openLabelPrint(router, { type: 'material', materialIds: ids.value.map(Number) })
  }
}

// 导出
const handleExport = () => {
  const loading = ElLoading.service({ text: '导出中...', lock: true })
  materialApi.export(queryParams).finally(() => loading.close())
}

onMounted(async () => {
  getList()
  loadCategoryOptions()
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

.material-tag {
  margin-right: 4px;
}
</style>
