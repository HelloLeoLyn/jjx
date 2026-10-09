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
      :tree-props="{ children: 'children' }"
      default-expand-all
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
      <el-table-column label="物料编码" prop="materialCode" width="120" fixed="left" />

      <!-- 物料名称 -->
      <el-table-column label="物料名称" prop="materialName" min-width="240">
        <template #default="scope">
          <BomMaterialSelector
            :material-id="scope.row.materialId"
            :material-name="scope.row.materialName"
            @select="(material) => handleMaterialSelect(material, scope.row)"
            @clear="handleMaterialClear(scope.row)"
            @create="(keyword) => handleCreateMaterial(scope.row, keyword)"
          />
        </template>
      </el-table-column>
      <el-table-column label="项目" prop="processName" width="160">
        <template #default="scope">
          <el-select
            v-model="scope.row.processId"
            placeholder="请选择标准工序"
            size="small"
            filterable
            clearable
            style="width: 100%"
            @change="(val: number | undefined) => handleProjectChange(scope.row, val)"
          >
            <el-option
              v-for="item in processOptions"
              :key="item.processId"
              :label="item.processName"
              :value="item.processId"
            />
          </el-select>
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
      <!-- <el-table-column label="数量" prop="quantity" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.quantity"
            :min="0"
            :precision="2"
            :step="getQuantityStep(scope.row.unit)"
            size="small"
            controls-position="right"
            @change="recalcAppliedIssue(scope.row)"
          />
        </template>
      </el-table-column> -->

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
            @change="handleLossRateChange(scope.row)"
          >
            <template #append>%</template>
          </el-input-number>
        </template>
      </el-table-column>

      <!-- 应用料（含损耗，只读） -->
      <el-table-column label="应用料" prop="appliedQty" align="center" width="90">
        <template #default="scope">{{ formatQty(scope.row.appliedQty) }}</template>
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
            @change="recalcAppliedIssue(scope.row)"
          />
        </template>
      </el-table-column>

      宽度(mm)
      <!-- <el-table-column label="宽度(mm)" align="center" width="110">
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
      </el-table-column> -->

      <!-- 长度(mm) -->
      <!-- <el-table-column label="长度(mm)" align="center" width="110">
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
      </el-table-column> -->

      <!-- 项目（标准工序） -->

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
      <el-table-column label="操作" width="220" align="center" fixed="right">
        <template #default="scope">
          <el-button
            v-if="!scope.row.materialId"
            link
            type="primary"
            size="small"
            @click="handleCreateMaterial(scope.row, scope.row.materialName || '')"
            >建档</el-button
          >
          <el-button link type="primary" size="small" @click="handleAddChildItem(scope.row)"
            >子物料</el-button
          >
          <!-- <el-button link type="primary" :icon="CopyDocument" @click="handleCopyItem(scope.row)" /> -->
          <el-button link type="danger" :icon="Delete" @click="handleDeleteItem(scope.row)" />
        </template>
      </el-table-column>
    </el-table>
    <MaterialFormDialog
      v-model="materialFormVisible"
      :preset-data="materialPreset"
      @success="handleMaterialCreated"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete, Refresh, CopyDocument, Rank } from '@element-plus/icons-vue'
import { debounce } from 'lodash-es'
import type { EngineeringBomItem } from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import type { StandardProcessItem } from '@/types/product/standardProcess'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'

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

// 项目（标准工序）选项：BOM 明细「项目」列取值 = 标准工序
const processOptions = ref<StandardProcessItem[]>([])
async function loadProcessOptions() {
  try {
    const res = await standardProcessApi.pageQuery({
      pageNum: 1,
      pageSize: 200,
      isEnabled: 1,
      orderByColumn: 'displayOrder',
      isAsc: 'asc',
    })
    processOptions.value = res.data?.records || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

const handleProjectChange = (row: EngineeringBomItem, processId: number | undefined) => {
  const p = processOptions.value.find((x) => x.processId === processId)
  row.processName = p ? p.processName : ''
}

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

// ==================== 树形结构工具（2026-08-10） ====================

// 新增/复制行分配的临时负数 id：唯一、稳定，供前端树结构与 el-table row-key 使用；
// 提交后端时由 flattenTree() 剥离（新增行 itemId 仍为空，不改变接口约定）。
let tempItemIdSeq = -1
const nextTempItemId = () => tempItemIdSeq--

/**
 * 平铺数组 → 树（按 parentMaterialId 构建，NULL=根）
 * 新行用临时负数 id 作为父引用（前端树形 row-key 需要稳定 id）
 */
function buildTree(list: EngineeringBomItem[]): EngineeringBomItem[] {
  const arr = (list || []).map((it) => ({
    ...it,
    children: it.children ? [...it.children] : undefined,
  }))
  // 确保每行有稳定的 itemId（新行用临时负数）
  arr.forEach((it) => {
    if (it.itemId == null) it.itemId = nextTempItemId()
  })
  const map = new Map<number, EngineeringBomItem>()
  arr.forEach((it) => map.set(Number(it.itemId), it))
  const roots: EngineeringBomItem[] = []
  arr.forEach((it) => {
    const pid = it.parentMaterialId
    if (pid != null && map.has(Number(pid))) {
      const parent = map.get(Number(pid))!
      if (!parent.children) parent.children = []
      parent.children.push(it)
    } else {
      roots.push(it)
    }
  })
  return roots
}

/**
 * 树 → 平铺（深度优先，保持层级顺序）
 */
function flattenTree(tree: EngineeringBomItem[]): EngineeringBomItem[] {
  const out: EngineeringBomItem[] = []
  const walk = (nodes: EngineeringBomItem[]) => {
    nodes.forEach((n) => {
      const copy = { ...n }
      delete copy.children
      // 临时负数 id 仅用于前端树/row-key；提交时剥离，保证新增行 itemId 为空、父引用不悬空
      if (copy.itemId != null && Number(copy.itemId) < 0) copy.itemId = undefined
      if (copy.parentMaterialId != null && Number(copy.parentMaterialId) < 0) copy.parentMaterialId = null
      out.push(copy)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(tree || [])
  return out
}

/** 遍历树（含所有层级） */
function walkTree(tree: EngineeringBomItem[], fn: (row: EngineeringBomItem) => void) {
  const walk = (nodes: EngineeringBomItem[]) => {
    nodes.forEach((n) => {
      fn(n)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(tree || [])
}

/** 在树中查找节点 */
function findInTree(tree: EngineeringBomItem[], itemId: number): EngineeringBomItem | null {
  let found: EngineeringBomItem | null = null
  walkTree(tree, (n) => {
    if (Number(n.itemId) === itemId) found = n
  })
  return found
}

// ==================== 初始化 & 监听 ====================

// 初始化数据（使用浅比较避免无限循环）
let isUpdating = false

// 防抖处理内部变化（树 → 平铺提交）
const emitChange = debounce(() => {
  if (isUpdating) return
  isUpdating = true
  emit('update:modelValue', flattenTree(items.value))
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
  loadProcessOptions()
  window.addEventListener('resize', calculateTableHeight)
  nextTick(() => {
    // 树形模式：拖拽排序已禁用
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', calculateTableHeight)
  emitChange.cancel()
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
  row.materialType = material.materialType
  row.create = false
  recalcAppliedIssue(row)
}

/**
 * 模数变化时自动计算数量
 * 数量 = 基数 ÷ 模数
 */
const handleModuleQtyChange = (row: EngineeringBomItem) => {
  // 缺值/非法不参与计算（不再静默当 1），由提交审核前校验拦截
  const moduleQty = Number(row.moduleQty)
  const baseQty = Number(row.baseQty)
  if (!Number.isFinite(moduleQty) || moduleQty <= 0 || !Number.isFinite(baseQty) || baseQty <= 0) return
  row.quantity = Number((baseQty / moduleQty).toFixed(4))
  recalcAppliedIssue(row)
}

/**
 * 基数变化时自动计算数量
 * 数量 = 基数 ÷ 模数
 */
const handleBaseQtyChange = (row: EngineeringBomItem) => {
  // 缺值/非法不参与计算（不再静默当 1），由提交审核前校验拦截
  const moduleQty = Number(row.moduleQty)
  const baseQty = Number(row.baseQty)
  if (!Number.isFinite(moduleQty) || moduleQty <= 0 || !Number.isFinite(baseQty) || baseQty <= 0) return
  row.quantity = Number((baseQty / moduleQty).toFixed(4))
  recalcAppliedIssue(row)
}

/** 损耗率变化：重算应用料/实际投料 */
const handleLossRateChange = (row: EngineeringBomItem) => {
  recalcAppliedIssue(row)
}

/**
 * 计算应用料/实际投料（前端预览，与后端一致）
 * 应用料 = 用量 × (1 + 损耗率/100)
 * 实际投料 = 单位应用料（含损耗、不取整）；整批取整与最低投料量下限由领料/缺料/预留侧按工单数量计算
 */
const recalcAppliedIssue = (row: EngineeringBomItem) => {
  const qty = Number(row.quantity) || 0
  const loss = Number(row.lossRate) || 0
  const applied = qty * (1 + loss / 100)
  row.appliedQty = Number(applied.toFixed(4))
  row.actualIssueQty = row.appliedQty
}

/** 数量格式化（只读列展示） */
const formatQty = (v: any): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : String(n)
}

// 监听外部数据变化，初始化数据（平铺 → 树形）
watch(
  () => props.modelValue,
  (newVal) => {
    if (isUpdating) return
    if (JSON.stringify(newVal) !== JSON.stringify(flattenTree(items.value))) {
      items.value = buildTree(newVal)
      // 应用料/实际投料：按公式重新计算预览（递归）
      walkTree(items.value, (row) => {
        if (row.quantity != null) {
          recalcAppliedIssue(row)
        }
      })
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
    itemId: nextTempItemId(),
    bomId: props.bomId,
    parentMaterialId: null, // 根节点
    materialId: 0,
    materialCode: '',
    materialName: '',
    specification: '',
    unit: 'PCS',
    quantity: 0,
    lossRate: 0,
    appliedQty: 0,
    actualIssueQty: 0,
    moduleQty: 1,
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
 * 添加子物料（挂到当前节点下，树形结构）
 */
const handleAddChildItem = (parent: EngineeringBomItem) => {
  const newItem: EngineeringBomItem = {
    itemId: nextTempItemId(),
    bomId: props.bomId,
    parentMaterialId: Number(parent.itemId),
    materialId: 0,
    materialCode: '',
    materialName: '',
    specification: '',
    unit: 'PCS',
    quantity: 0,
    lossRate: 0,
    appliedQty: 0,
    actualIssueQty: 0,
    moduleQty: 1,
    baseQty: 1,
    remark: '',
    sortOrder: (parent.children?.length || 0) + 1,
    create: false,
  }
  if (!parent.children) parent.children = []
  parent.children.push(newItem)
}

/**
 * 复制物料（复制整棵子树，挂到同父节点下）
 */
const handleCopyItem = (item: EngineeringBomItem) => {
  const copyItem = JSON.parse(JSON.stringify(item))
  copyItem.itemId = undefined
  copyItem.parentMaterialId = item.parentMaterialId ?? null
  // 子树也重新生成临时 id
  const reId = (n: any, parentNewId: number | null) => {
    const newId = nextTempItemId()
    n.itemId = newId
    n.parentMaterialId = parentNewId
    ;(n.children || []).forEach((c: any) => reId(c, newId))
  }
  reId(copyItem, copyItem.parentMaterialId)
  // 找到父节点插入
  if (copyItem.parentMaterialId != null) {
    const parent = findInTree(items.value, copyItem.parentMaterialId)
    if (parent) {
      if (!parent.children) parent.children = []
      parent.children.push(copyItem)
      ElMessage.success('复制成功')
      return
    }
  }
  items.value.push(copyItem)
  ElMessage.success('复制成功')
}

/**
 * 删除物料（树形：有子节点时阻止删除）
 */
const handleDeleteItem = async (row: EngineeringBomItem) => {
  // 有子节点 → 阻止
  if (row.children && row.children.length > 0) {
    ElMessage.warning('请先删除子节点，再删除该物料')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定要删除物料 "${row.materialName || row.materialCode || ''}" 吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    // 从树中移除（按对象引用优先、id 兜底；失败不谎报成功）
    const removed = removeFromTree(items.value, Number(row.itemId), row)
    if (removed) {
      ElMessage.success('删除成功')
    } else {
      ElMessage.warning('该行已不在列表中，删除未生效')
    }
  } catch {
    // 用户取消
  }
}

/** 从树中移除节点（含嵌套） */
function removeFromTree(
  tree: EngineeringBomItem[],
  itemId: number,
  row?: EngineeringBomItem
): boolean {
  for (let i = 0; i < tree.length; i++) {
    // 对象引用优先（新增行 itemId 缺失/临时时也能命中），有限数字 id 兜底
    const sameRow = row != null && tree[i] === row
    const sameId = Number.isFinite(itemId) && Number(tree[i].itemId) === itemId
    if (sameRow || sameId) {
      tree.splice(i, 1)
      return true
    }
    if (tree[i].children?.length && removeFromTree(tree[i].children!, itemId, row)) {
      return true
    }
  }
  return false
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
// 2026-08-10：树形表格不支持平铺拖拽排序，已禁用（原 Sortable 实现移除）

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

// 物料建档：保存独立物料档案后回填当前行
const materialFormVisible = ref(false)
const materialPreset = ref({ materialName: '', specification: '', unit: 'PCS' })
let creatingRow: EngineeringBomItem | null = null

const handleMaterialClear = (row: EngineeringBomItem) => {
  row.materialId = 0
  row.materialCode = ''
  row.materialName = ''
  row.specification = ''
  row.unit = 'PCS'
  row.materialType = undefined
  row.create = false
}

const handleCreateMaterial = (row: EngineeringBomItem, keyword: string) => {
  creatingRow = row
  materialPreset.value = {
    materialName: keyword,
    specification: row.specification || '',
    unit: row.unit || 'PCS',
  }
  materialFormVisible.value = true
}

const handleMaterialCreated = (material: InventoryMaterial) => {
  if (!material.materialId) {
    ElMessage.warning('物料已建档，请按名称搜索后选择')
    return
  }
  // 父表回填可能重建行对象，按稳定的明细ID找到当前行。
  const row =
    creatingRow?.itemId == null ? creatingRow : findInTree(items.value, Number(creatingRow.itemId))
  if (row) handleMaterialSelect(material, row)
  creatingRow = null
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
  walkTree(items.value, (item) => {
    item.widthMm = 0
  })
  ElMessage.success('已重置所有宽度为0')
}

/**
 * 重置所有行的长度为0
 */
const resetAllLength = () => {
  walkTree(items.value, (item) => {
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
  getItems: () => flattenTree(items.value),
  clearItems: () => {
    items.value = []
    selectedItems.value = []
  },
  validateItems: (): boolean => {
    if (items.value.length === 0) {
      ElMessage.warning('请至少添加一个物料')
      return false
    }

    let ok = true
    walkTree(items.value, (item) => {
      if (!ok) return
      if (!item.materialCode?.trim()) {
        ElMessage.warning('物料编码不能为空')
        ok = false
        return
      }
      if (!item.materialName?.trim()) {
        ElMessage.warning('物料名称不能为空')
        ok = false
        return
      }
      if (item.quantity <= 0) {
        ElMessage.warning(`物料 "${item.materialName}" 数量必须大于0`)
        ok = false
      }
    })

    return ok
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
