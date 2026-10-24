<template>
  <div class="material-list">
    <!-- 搜索栏 -->
    <SearchForm
      v-model="queryParams"
      :fields="searchFields"
      @search="handleQuery"
      @reset="handleReset"
    >
      <template #supplierId>
        <SupplierSelector
          v-model="supplierIdModel"
          :remote="true"
          style="width: 190px"
          @update:model-value="handleQuery"
        />
      </template>
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
          type="primary"
          size="small"
          v-hasPermi="['inventory:material:edit']"
          :disabled="!ids.length"
          @click="openBatchClassify"
        >
          批量定分类
        </el-button>
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
        :row-class-name="classRowName"
        @selection-change="handleSelectionChange"
        border
      >
        <el-table-column type="selection" width="55" align="center" />
        <!-- <el-table-column label="物料编码" prop="materialCode" width="150" /> -->
        <!-- <el-table-column
          label="原规格"
          prop="originalSpecification"
          width="200"
          show-overflow-tooltip
        /> -->
        <el-table-column label="品类" prop="categoryId" width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ categoryNameOf(row) }}</template>
        </el-table-column>
        <el-table-column label="材质" width="110"
          ><template #default="{ row }">{{
            dictionaryText('material_meta', row.materialMeta)
          }}</template></el-table-column
        >
        <el-table-column label="形态" width="90"
          ><template #default="{ row }">{{
            dictionaryText('form', row.materialForm)
          }}</template></el-table-column
        >
        <el-table-column label="型号" prop="model" width="120" show-overflow-tooltip />
        <el-table-column label="颜色" width="90"
          ><template #default="{ row }">{{
            dictionaryText('color', row.color)
          }}</template></el-table-column
        >
        <el-table-column label="品牌" width="120"
          ><template #default="{ row }">{{
            dictionaryText('brand', row.brand)
          }}</template></el-table-column
        >
        <el-table-column label="尺寸" prop="size" width="140" show-overflow-tooltip />
        <el-table-column label="生成规格" prop="specification" width="230" show-overflow-tooltip />
        <el-table-column label="客供专用" width="90"
          ><template #default="{ row }">{{
            row.isCustomerSupplied ? '是' : '否'
          }}</template></el-table-column
        >
        <el-table-column label="样品专用" width="90"
          ><template #default="{ row }">{{ row.isSample ? '是' : '否' }}</template></el-table-column
        >
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
              :active-value="CommonStatusEnum.NORMAL.value"
              :inactive-value="CommonStatusEnum.DISABLED.value"
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
        <el-table-column label="当前名称" prop="materialName" width="220" show-overflow-tooltip />
        <el-table-column label="归类状态" width="150" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="MaterialClassStatusEnum.getTagProps(resolveClassStatus(row)).type as any"
            >
              {{ MaterialClassStatusEnum.getLabel(resolveClassStatus(row)) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="原名称" width="200" show-overflow-tooltip>
          <template #header>
            <span
              title="迁移期辅助列：用于核对被重写的名称；566 条归类确认、original_* 清理后一起下线"
              >原名称</span
            >
          </template>
          <template #default="{ row }">{{ originalNameOf(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
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
              >重算名称与规格</el-button
            >
            <el-button
              v-if="resolveClassStatus(row) === 'rule_unconfirmed'"
              link
              type="warning"
              v-hasPermi="['inventory:material:edit']"
              @click="confirmClass(row)"
              >确认归类</el-button
            >
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
    <MaterialRegenerateDialog
      v-model="regenerateVisible"
      :material-id="regenerateMaterialId"
      @success="getList"
    />
    <MaterialClassifyDialog
      v-model="classifyVisible"
      :material-ids="classifyIds"
      :names="classifyNames"
      @success="getList"
    />
    <MaterialClassifyRuleDrawer v-model="rulesVisible" />

    <!-- 物料表单对话框 -->
    <MaterialFormDialog
      v-model="dialogVisible"
      :material-id="editingMaterialId"
      @success="handleFormSuccess"
    />

    <MaterialMasterImportDialog v-model="importDialogVisible" @success="getList" />
    <MaterialMasterSettingsDialog v-model="settingsVisible" @success="loadCategoryOptions" />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InventoryMaterial',
})

import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import SearchForm from '@/components/common-ui/SearchForm.vue'
import Toolbar from '@/components/common-ui/Toolbar.vue'
import TagQuerySelect from '@/components/TagQuerySelect.vue'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import MaterialDetailDialog from '@/components/inventory/MaterialDetailDialog.vue'
import MaterialRegenerateDialog from '@/components/inventory/MaterialRegenerateDialog.vue'
import MaterialClassifyDialog from '@/components/inventory/MaterialClassifyDialog.vue'
import MaterialClassifyRuleDrawer from '@/components/inventory/MaterialClassifyRuleDrawer.vue'
import { materialClassifyApi } from '@/api/inventory/classify'
import { MaterialClassStatusEnum, resolveClassStatus } from '@/enums/inventory/MaterialClassEnum'
import MaterialMasterImportDialog from '@/components/inventory/MaterialMasterImportDialog.vue'
import MaterialMasterSettingsDialog from '@/components/inventory/MaterialMasterSettingsDialog.vue'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { downloadMaterialBlob } from '@/components/inventory/materialMasterModel'
import { parseOriginalName } from '@/components/inventory/materialRegenerate'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import type { MaterialMasterCategory, MaterialDictionaries } from '@/types/inventory/materialMaster'
import { materialApi } from '@/api/inventory/material'
import { formatCurrency } from '@/utils/format'
import type { InventoryMaterial, InventoryMaterialQueryParams } from '@/types/inventory/material'
import type { SearchOptions, ToolbarOptions } from '@/components/common-ui/type'
import { searchOptions, toolbarOptions } from './config'
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
  pageSize: 20,
  keyword: '',
  materialCode: '',
  materialName: '',
  rootType: '',
  materialMeta: '',
  materialForm: '',
  tagIds: [],
  tagMatchMode: 'AND',
  classStatus: '',
  supplierId: undefined,
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
const supplierIdModel = computed({
  get: () => queryParams.supplierId,
  set: (value: number | string | undefined) => {
    queryParams.supplierId = value == null || value === '' ? undefined : Number(value)
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
const classifyVisible = ref(false)
const classifyIds = ref<number[]>([])
const classifyNames = ref<string[]>([])
const rulesVisible = ref(false)
const settingsVisible = ref(false)

// 按末级品类生成模板并做整表预检。
const importDialogVisible = ref(false)

const categoryOptions = ref<MaterialMasterCategory[]>([])
const dictionaries = ref<MaterialDictionaries>({})
const dictionaryText = (dict: string, code?: string) =>
  dictionaries.value[dict]?.find((o) => o.code === code)?.label || code || '-'
const loadCategoryOptions = async () => {
  const [categoryResponse, dictResponse] = await Promise.all([
    materialMasterApi.categories(),
    materialMasterApi.dictionaries(),
  ])
  const options = categoryResponse.data || []
  categoryOptions.value = options
  dictionaries.value = dictResponse.data || {}
  const field = searchFields.value.find((item) => item.prop === 'categoryId')
  if (field) {
    field.options = options
      .filter((item) => item.selectable)
      .map((item) => ({ value: item.categoryId, label: item.pathName }))
  }
  for (const [prop, dict] of Object.entries({
    materialMeta: 'material_meta',
    materialForm: 'form',
    color: 'color',
    brand: 'brand',
  })) {
    const filter = searchFields.value.find((item) => item.prop === prop)
    if (filter)
      filter.options = (dictionaries.value[dict] || []).map((o) => ({
        value: o.code,
        label: o.label,
      }))
  }
}

// 列表列的品类名：后端 MaterialVO.categoryName 未赋值，这里按已加载品类映射
const categoryNameOf = (row: InventoryMaterial) => {
  if (row.categoryId == null) return '-'
  return (
    categoryOptions.value.find((item) => item.categoryId === row.categoryId)?.pathName ||
    row.categoryName ||
    '-'
  )
}

// 获取列表
const getList = async () => {
  loading.value = true
  try {
    const res = await materialApi.page(queryParams)
    materialList.value = res.data?.records || []
    total.value = res.data?.total || 0
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
  queryParams.keyword = ''
  queryParams.materialCode = ''
  queryParams.materialName = ''
  queryParams.rootType = ''
  queryParams.materialMeta = ''
  queryParams.materialForm = ''
  queryParams.status = ''
  queryParams.classStatus = ''
  queryParams.supplierId = undefined
  queryParams.categoryId = undefined
  queryParams.materialGrade = ''
  queryParams.model = ''
  queryParams.color = ''
  queryParams.brand = ''
  queryParams.isCustomerSupplied = undefined
  queryParams.isSample = undefined
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

// 迁移期辅助列：优先主表 original_name，其次 remark 的【原名称】（dev-20261024-004 用户裁定恢复）
const originalNameOf = (row: InventoryMaterial) =>
  row.originalName || parseOriginalName(row.remark) || '-'

// 归类三态行高亮：未归类标黄提示，但不禁用编辑（dev-20261024-004 阶段三）
const classRowName = ({ row }: { row: InventoryMaterial }) =>
  resolveClassStatus(row) === 'unclassified' ? 'row-unclassified' : ''

// 批量定分类（单次上限 200 条，超限前端先拦）
const openBatchClassify = () => {
  if (!ids.value.length) {
    ElMessage.warning('请先勾选物料')
    return
  }
  if (ids.value.length > 200) {
    ElMessage.warning(`单次最多 200 条，当前已选 ${ids.value.length} 条`)
    return
  }
  classifyIds.value = ids.value.map(Number)
  classifyNames.value = materialList.value
    .filter((row) => ids.value.includes(String(row.materialId)))
    .map((row) => row.materialName)
  classifyVisible.value = true
}

// 单条确认归类（仅规则命中未确认的行）
const confirmClass = async (row: InventoryMaterial) => {
  try {
    await materialClassifyApi.confirm(row.materialId)
    ElMessage.success('已确认归类')
    getList()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '确认归类失败')
  }
}

// 重算名称与规格（只改名称/规格，不写分类与属性）
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
  if (key === 'master-settings') settingsVisible.value = true
  if (key === 'migration-export')
    materialMasterApi
      .exportMigrationCandidates()
      .then((blob) => downloadMaterialBlob(blob, '待归类物料.xlsx'))
  if (key === 'classify-rules') rulesVisible.value = true
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
  materialMasterApi
    .exportExcel(queryParams)
    .then((blob) => downloadMaterialBlob(blob, '物料主数据.xlsx'))
    .finally(() => loading.close())
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

:deep(.el-table .row-unclassified) {
  background: #fdf6ec;
}
</style>
