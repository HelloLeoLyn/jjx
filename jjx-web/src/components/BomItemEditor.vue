<template>
  <div class="bom-item-editor">
    <!-- 操作按钮区域 -->
    <div class="editor-header">
      <div class="header-left">
        <el-button type="primary" :icon="Plus" @click="handleAddItem"> 添加物料 </el-button>
        <el-button type="info" :icon="Refresh" :loading="refreshLoading" @click="handleRefresh">
          刷新
        </el-button>
      </div>

      <div class="header-right">
        <el-tag type="info" size="small"> 共 {{ items.length }} 项物料 </el-tag>
      </div>
    </div>

    <!-- 明细表格 -->
    <el-table
      ref="tableRef"
      v-loading="tableLoading"
      :data="items"
      border
      style="width: 100%"
      row-key="itemId"
      :height="tableHeight"
      @selection-change="handleSelectionChange"
      class="bom-item-table"
    >
      <!-- 序号列 - 拖拽手柄 -->
      <el-table-column label="序号" width="50" align="center" fixed="left">
        <template #default="scope">
          <div class="drag-cell">
            <el-icon class="drag-handle"><Rank /></el-icon>
            <!-- <span class="row-index">{{ scope.row.sortOrder }}</span> -->
          </div>
        </template>
      </el-table-column>

      <!-- 物料编码 -->
      <el-table-column label="物料编码" prop="materialCode" width="170" fixed="left">
        <template #header>
          <div class="column-header">
            <span>物料编码</span>
            <el-button link type="primary" size="small" @click="handleBatchQueryMaterialCode"
              >查询</el-button
            >
          </div>
        </template>
        <template #default="scope">
          <MaterialCompleteSelector
            v-model="scope.row.materialCode"
            @material-select="(material) => handleMaterialSelect(material, scope.row)"
          />
        </template>
      </el-table-column>

      <!-- 物料名称 -->
      <el-table-column label="物料名称" prop="materialName" width="120">
        <template #default="scope">
          <el-input
            v-model="scope.row.materialName"
            placeholder="请输入物料名称"
            size="small"
            clearable
          />
        </template>
      </el-table-column>

      <!-- 规格型号 -->
      <el-table-column label="规格型号" prop="specification" width="100">
        <template #default="scope">
          <el-input v-model="scope.row.specification" placeholder="请输入规格型号" size="small" />
        </template>
      </el-table-column>

      <!-- 单位 -->
      <el-table-column label="单位" prop="unit" width="100">
        <template #default="scope">
          <el-select
            v-model="scope.row.unit"
            placeholder="请选择"
            size="small"
            filterable
            allow-create
            style="width: 100%"
          >
            <el-option
              v-for="item in unitOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </template>
      </el-table-column>

      <!-- 模数 -->
      <el-table-column label="模数" prop="moduleQty" align="center" width="80">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.moduleQty"
            :min="0"
            :precision="4"
            :step="1"
            size="small"
            controls-position="right"
            @change="handleModuleQtyChange(scope.row)"
          />
        </template>
      </el-table-column>

      <!-- 基数 -->
      <el-table-column label="基数" prop="baseQty" align="center" width="80">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.baseQty"
            :min="0"
            :step="1"
            size="small"
            controls-position="right"
            @change="handleBaseQtyChange(scope.row)"
          />
        </template>
      </el-table-column>

      <!-- 数量 -->
      <el-table-column label="数量" prop="quantity" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.quantity"
            :min="0"
            :precision="2"
            :step="getQuantityStep(scope.row.unit)"
            size="small"
            controls-position="right"
          />
        </template>
      </el-table-column>

      <!-- 损耗率 -->
      <el-table-column label="损耗率(%)" prop="lossRate" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.lossRate"
            :min="0"
            :max="100"
            :precision="2"
            :step="0.01"
            size="small"
            controls-position="right"
          >
            <template #append>%</template>
          </el-input-number>
        </template>
      </el-table-column>

      <!-- 最低投料量 -->
      <el-table-column label="最低投料量" prop="minIssueQty" align="center" width="110">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.minIssueQty"
            :min="0"
            :precision="4"
            :step="1"
            size="small"
            controls-position="right"
          />
        </template>
      </el-table-column>

      <!-- 宽度(mm) -->
      <el-table-column label="宽度(mm)" align="center" width="110">
        <template #header>
          <div class="column-header">
            <span>宽度(mm)</span>
            <el-button link type="primary" size="small" @click="resetAllWidth">重置</el-button>
          </div>
        </template>
        <template #default="scope">
          <el-input-number
            v-model="scope.row.widthMm"
            :min="0"
            :precision="2"
            :step="1"
            size="small"
            controls-position="right"
          />
        </template>
      </el-table-column>

      <!-- 长度(mm) -->
      <el-table-column label="长度(mm)" align="center" width="110">
        <template #header>
          <div class="column-header">
            <span>长度(mm)</span>
            <el-button link type="primary" size="small" @click="resetAllLength">重置</el-button>
          </div>
        </template>
        <template #default="scope">
          <el-input-number
            v-model="scope.row.lengthMm"
            :min="0"
            :precision="2"
            :step="1"
            size="small"
            controls-position="right"
          />
        </template>
      </el-table-column>

      <!-- 备注 -->
      <el-table-column label="备注" prop="remark" min-width="150">
        <template #default="scope">
          <el-input
            v-model="scope.row.remark"
            placeholder="请输入备注"
            size="small"
            clearable
            maxlength="500"
          />
        </template>
      </el-table-column>

      <!-- 操作列 -->
      <el-table-column label="操作" width="130" align="center" fixed="right">
        <template #default="scope">
          <el-button
            link
            type="primary"
            :icon="CopyDocument"
            @click="handleCopyItem(scope.row, scope.$index)"
          />
          <el-button
            v-if="scope.row.create"
            link
            type="warning"
            size="small"
            @click="handleCreateMaterial(scope.row, scope.$index)"
            >建档</el-button
          >
          <el-button link type="danger" :icon="Delete" @click="handleDeleteItem(scope.$index)" />
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Refresh, CopyDocument, Rank } from '@element-plus/icons-vue'
import { debounce } from 'lodash-es'
import type { EngineeringBomItem } from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import Sortable from 'sortablejs'
import MaterialCompleteSelector from '@/components/Selector/MaterialCompleteSelector.vue'
import { materialApi } from '@/api/inventory/material'

// ==================== Props & Emits ====================

interface Props {
  modelValue: EngineeringBomItem[]
  bomId?: number
  readonly?: boolean
  maxItems?: number
}

interface Emits {
  (e: 'update:modelValue', value: EngineeringBomItem[]): void
  (e: 'change', value: EngineeringBomItem[]): void
  (e: 'validate', valid: boolean): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  bomId: undefined,
  readonly: false,
  maxItems: 500,
})

const emit = defineEmits<Emits>()

// ==================== 响应式数据 ====================

const tableRef = ref()
const items = ref<EngineeringBomItem[]>([])
const selectedItems = ref<EngineeringBomItem[]>([])
const tableLoading = ref(false)
const refreshLoading = ref(false)
const tableHeight = ref(400)

// 单位选项
const unitOptions = [
  { value: 'PCS', label: '个(PCS)' },
  { value: 'KG', label: '千克(KG)' },
  { value: 'M', label: '米(M)' },
  { value: 'M²', label: '平方米(M²)' },
  { value: 'L', label: '升(L)' },
  { value: 'SET', label: '套(SET)' },
]

// ==================== 计算属性 ====================

const hasSelected = computed(() => selectedItems.value.length > 0)

// ==================== 初始化 & 监听 ====================

// 初始化数据（使用浅比较避免无限循环）
let isUpdating = false

// 防抖处理内部变化
const emitChange = debounce(() => {
  if (isUpdating) return
  isUpdating = true
  emit('update:modelValue', items.value)
  nextTick(() => {
    isUpdating = false
  })
}, 300)

watch(
  items,
  () => {
    emitChange()
  },
  { deep: true }
)

// 计算表格高度
const calculateTableHeight = () => {
  const windowHeight = window.innerHeight
  tableHeight.value = Math.max(300, windowHeight - 320)
}

onMounted(() => {
  calculateTableHeight()
  window.addEventListener('resize', calculateTableHeight)
  nextTick(() => {
    initSortable()
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', calculateTableHeight)
  emitChange.cancel()
  if (sortableInstance) {
    sortableInstance.destroy()
    sortableInstance = null
  }
})

// ==================== 物料选择处理 ====================

/**
 * 物料选择处理
 */
const handleMaterialSelect = (material: InventoryMaterial, row: EngineeringBomItem) => {
  if (!material) return

  row.materialId = material.materialId || 0
  row.materialCode = material.materialCode
  row.materialName = material.materialName
  row.specification = material.specification || ''
  row.unit = material.unit || 'PCS'
}

/**
 * 模数变化时自动计算数量
 * 数量 = 基数 ÷ 模数
 */
const handleModuleQtyChange = (row: EngineeringBomItem) => {
  const moduleQty = Number(row.moduleQty) || 1
  const baseQty = Number(row.baseQty) || 1
  row.quantity = Number((baseQty / moduleQty).toFixed(4))
}

/**
 * 基数变化时自动计算数量
 * 数量 = 基数 ÷ 模数
 */
const handleBaseQtyChange = (row: EngineeringBomItem) => {
  const moduleQty = Number(row.moduleQty) || 1
  const baseQty = Number(row.baseQty) || 1
  row.quantity = Number((baseQty / moduleQty).toFixed(4))
}

// 监听外部数据变化，初始化数据
watch(
  () => props.modelValue,
  (newVal) => {
    if (isUpdating) return
    if (JSON.stringify(newVal) !== JSON.stringify(items.value)) {
      items.value = newVal.map((item, index) => ({
        ...item,
        quantity: item.quantity ?? 0,
        lossRate: item.lossRate ?? 0,
        sortOrder: item.sortOrder ?? index + 1,
      }))
    }
  },
  { immediate: true, deep: true }
)

// ==================== 物料操作 ====================

/**
 * 添加物料
 */
const handleAddItem = () => {
  if (items.value.length >= props.maxItems) {
    ElMessage.warning(`最多只能添加 ${props.maxItems} 个物料`)
    return
  }

  const newItem: EngineeringBomItem = {
    itemId: undefined,
    bomId: props.bomId,
    materialId: 0,
    materialCode: '',
    materialName: '',
    specification: '',
    unit: 'PCS',
    quantity: 0,
    lossRate: 0,
    baseQty: 1,
    remark: '',
    sortOrder: items.value.length + 1,
    create: false,
  }

  items.value.push(newItem)

  // 滚动到新添加的行
  nextTick(() => {
    const lastRow = tableRef.value?.$el?.querySelector('.el-table__body tr:last-child')
    lastRow?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  })
}

/**
 * 复制物料
 */
const handleCopyItem = (item: EngineeringBomItem, index: number) => {
  const copyItem = JSON.parse(JSON.stringify(item))
  copyItem.itemId = undefined
  copyItem.sortOrder = items.value.length + 1
  items.value.splice(index + 1, 0, copyItem)
  ElMessage.success('复制成功')

  // 重新排序
  reorderItems()
}

/**
 * 删除物料
 */
const handleDeleteItem = async (index: number) => {
  try {
    await ElMessageBox.confirm('确定要删除该物料吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    items.value.splice(index, 1)
    reorderItems()
    ElMessage.success('删除成功')
  } catch {
    // 用户取消
  }
}

/**
 * 重新排序
 */
const reorderItems = () => {
  items.value.forEach((item, idx) => {
    item.sortOrder = idx + 1
  })
}

// ==================== 拖拽排序 ====================

let sortableInstance: Sortable | null = null

/**
 * 初始化拖拽排序
 */
const initSortable = () => {
  const el = tableRef.value?.$el?.querySelector('.el-table__body-wrapper tbody')
  if (!el) {
    // 如果表格还没渲染完成，延迟重试
    setTimeout(() => initSortable(), 200)
    return
  }

  // 销毁已有实例
  if (sortableInstance) {
    sortableInstance.destroy()
  }

  sortableInstance = Sortable.create(el, {
    handle: '.drag-handle',
    animation: 150,
    easing: 'cubic-bezier(0.25, 0.1, 0.25, 1)',
    ghostClass: 'sortable-ghost',
    dragClass: 'sortable-drag',
    onStart: () => {
      // 拖拽开始时添加样式
      tableRef.value?.$el?.classList.add('is-dragging')
    },
    onEnd: (evt: Sortable.SortableEvent) => {
      tableRef.value?.$el?.classList.remove('is-dragging')

      const { oldIndex, newIndex } = evt
      if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return

      // 移动数组元素 - 使用新数组触发响应式更新
      const newItems = [...items.value]
      const [movedItem] = newItems.splice(oldIndex, 1)
      newItems.splice(newIndex, 0, movedItem)

      // 重新分配排序值并替换整个数组以触发响应式更新
      newItems.forEach((item, idx) => {
        item.sortOrder = idx + 1
      })
      items.value = newItems
    },
  })
}

/**
 * 刷新
 */
const handleRefresh = async () => {
  refreshLoading.value = true
  try {
    ElMessage.success('刷新成功')
  } finally {
    refreshLoading.value = false
  }
}

// ==================== 批量查询物料编码 ====================

/**
 * 根据物料名称+规格型号批量查询物料编码
 * 遍历所有行，调用 materialApi.list 查询，找到唯一匹配则自动填充
 */
const handleBatchQueryMaterialCode = async () => {
  let matchCount = 0
  let failCount = 0

  for (const item of items.value) {
    const materialName = item.materialName?.trim()
    const specification = item.specification?.trim()
    if (!materialName) {
      failCount++
      continue
    }

    try {
      const params: any = { materialName }
      if (specification) {
        params.specification = specification
      }

      const res = await materialApi.list(params)
      const records = res.data || []

      if (records.length === 1) {
        const material = records[0]
        item.materialId = material.materialId || 0
        item.materialCode = material.materialCode
        item.materialName = material.materialName
        item.specification = material.specification || ''
        item.unit = material.unit || 'PCS'
        matchCount++
      } else {
        failCount++
        item.materialCode = records.length + ''
        item.create = true
      }
    } catch (error) {
      console.error('查询物料失败:', materialName, error)
      failCount++
    }
  }

  if (matchCount > 0) {
    ElMessage.success(
      `成功匹配 ${matchCount} 项物料${failCount > 0 ? `，${failCount} 项未匹配` : ''}`
    )
  } else {
    ElMessage.warning('未匹配到任何物料，请检查物料名称和规格')
  }
}

// ==================== 物料建档 ====================

/**
 * 快速建档：生成编码 → 新增物料 → 填充行
 */
const handleCreateMaterial = async (row: EngineeringBomItem, index: number) => {
  try {
    // 1. 生成物料编码
    const codeRes = await materialApi.generateCode()
    const materialCode = codeRes.data || ''

    // 2. 新增物料
    const saveData = {
      materialCode,
      materialName: row.materialName || '',
      materialType: 'R',
      specification: row.specification || '',
      unit: row.unit || 'PCS',
      safeStock: 0,
      maxStock: 0,
      reorderPoint: 0,
      batchControl: false,
      expiryAlertDays: 30,
    }
    const addRes = await materialApi.add(saveData)
    const newMaterialId = Number(addRes.data) || 0

    // 3. 填充行
    row.materialId = newMaterialId
    row.materialCode = materialCode
    row.create = false

    ElMessage.success('物料建档成功')
  } catch (error) {
    ElMessage.error('物料建档失败')
    console.error('建档失败:', error)
  }
}

// ==================== 表格事件 ====================

const handleSelectionChange = (selection: EngineeringBomItem[]) => {
  selectedItems.value = selection
}

// ==================== 批量重置 ====================

/**
 * 重置所有行的宽度为0
 */
const resetAllWidth = () => {
  items.value.forEach((item) => {
    item.widthMm = 0
  })
  ElMessage.success('已重置所有宽度为0')
}

/**
 * 重置所有行的长度为0
 */
const resetAllLength = () => {
  items.value.forEach((item) => {
    item.lengthMm = 0
  })
  ElMessage.success('已重置所有长度为0')
}

// ==================== 工具函数 ====================

/**
 * 根据单位获取数量步进值
 * 个(PCS)、套(SET) 为整数步进，其他为小数步进
 */
const getQuantityStep = (unit: string) => {
  return unit === 'PCS' || unit === 'SET' ? 1 : 0.1
}

// ==================== 暴露方法 ====================

defineExpose({
  getItems: () => items.value,
  clearItems: () => {
    items.value = []
    selectedItems.value = []
  },
  validateItems: (): boolean => {
    if (items.value.length === 0) {
      ElMessage.warning('请至少添加一个物料')
      return false
    }

    for (const item of items.value) {
      if (!item.materialCode?.trim()) {
        ElMessage.warning('物料编码不能为空')
        return false
      }
      if (!item.materialName?.trim()) {
        ElMessage.warning('物料名称不能为空')
        return false
      }
      if (item.quantity <= 0) {
        ElMessage.warning(`物料 "${item.materialName}" 数量必须大于0`)
        return false
      }
    }

    return true
  },
  reorderItems,
})
</script>

<style scoped lang="scss">
.bom-item-editor {
  width: 100%;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
  border-radius: 8px;

  .header-left {
    display: flex;
    gap: 12px;
  }

  .header-right {
    display: flex;
    gap: 16px;
    align-items: center;
  }
}

.bom-item-table {
  overflow-x: auto;

  // 拖拽相关样式
  :deep(.el-table__row) {
    &.sortable-ghost {
      opacity: 0.4;
      background-color: #e6f7ff !important;
    }

    &.sortable-drag {
      background-color: #f0f9ff !important;
      box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }
  }

  &.is-dragging :deep(.el-table__body-wrapper tbody tr) {
    cursor: grabbing;
  }

  :deep(.el-input-number) {
    width: 100%;

    .el-input-number__decrease,
    .el-input-number__increase {
      background: #f5f7fa;
    }
  }

  :deep(.el-input) {
    width: 100%;
  }

  // [MOD] 表格 cell padding 设为 0，消除表格与组件间的间距
  :deep(.el-table__cell) {
    padding: 0 !important;
    .cell {
      padding: 0 !important;
    }
  }

  :deep(.el-table__row:hover) {
    background-color: #f5f7fa;
  }

  // 表头列按钮样式
  .column-header {
    display: flex;
    align-items: center;
    gap: 2px;
    white-space: nowrap;

    :deep(.el-button) {
      padding: 0;
      min-height: auto;
      font-size: 12px;
    }
  }
}

// 拖拽单元格样式
.drag-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  cursor: grab;
  user-select: none;

  .drag-handle {
    font-size: 16px;
    color: #c0c4cc;
    transition: color 0.2s;

    &:hover {
      color: #409eff;
    }
  }

  .row-index {
    font-size: 13px;
    color: #606266;
    min-width: 16px;
    text-align: center;
  }
}

// 响应式适配
@media (max-width: 768px) {
  .bom-item-editor {
    padding: 12px;
  }

  .editor-header {
    flex-direction: column;
    gap: 12px;

    .header-left,
    .header-right {
      width: 100%;
      justify-content: center;
    }
  }
}
</style>
