<template>
  <div class="bom-item-editor">
    <!-- 操作按钮区域 -->
    <div class="editor-header">
      <div class="header-left">
        <el-button type="primary" :icon="Plus" @click="handleAddItem"> 添加物料 </el-button>
        <el-button type="info" :icon="Refresh" :loading="refreshLoading" @click="handleRefresh">
          刷新
        </el-button>
        <el-button
          v-if="!readonly"
          type="warning"
          :icon="Link"
          :loading="syncLoading"
          @click="handleSyncMaterials"
        >
          全量同步
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
      :row-class-name="rowClassName"
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
      <el-table-column label="物料编码" prop="materialCode" width="120" fixed="left">
        <template #default="scope">
          <span>{{ scope.row.materialCode }}</span>
          <el-tooltip
            v-if="syncReasonText(scope.row)"
            :content="syncReasonText(scope.row)"
            placement="top"
          >
            <el-icon class="sync-warn-icon"><WarningFilled /></el-icon>
          </el-tooltip>
        </template>
      </el-table-column>

      <!-- 物料名称 -->
      <el-table-column label="物料名称" prop="materialName" min-width="240">
        <template #default="scope">
          <div class="material-name-cell">
            <BomMaterialSelector
              :material-id="scope.row.materialId"
              :material-name="scope.row.materialName"
              @select="(material) => handleMaterialSelect(material, scope.row)"
              @clear="handleMaterialClear(scope.row)"
              @create="(keyword) => handleCreateMaterial(scope.row, keyword)"
            />
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!scope.row.materialName && !scope.row.materialCode"
              title="复制 物料名 · 规格 · 编码"
              @click="copyMaterial(scope.row)"
              >复制</el-button
            >
          </div>
        </template>
      </el-table-column>
      <el-table-column label="项目" prop="processName" width="160">
        <template #default="scope">
          <el-select
            :model-value="scope.row.processId ?? scope.row.processName"
            placeholder="选择或输入项目名称"
            size="small"
            filterable
            allow-create
            default-first-option
            clearable
            style="width: 100%"
            @change="(val: number | string | undefined) => handleProjectChange(scope.row, val)"
          >
            <el-option
              v-if="scope.row.processId == null && scope.row.processName"
              :label="scope.row.processName"
              :value="scope.row.processName"
            />
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
            :min="1"
            :precision="0"
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
            :min="1"
            :precision="0"
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
import {
  Plus,
  Delete,
  Refresh,
  CopyDocument,
  Rank,
  Link,
  WarningFilled,
} from '@element-plus/icons-vue'
import { debounce } from 'lodash-es'
import type { EngineeringBomItem, BomMaterialMatchItem } from '@/types/product/bom'
import { calculateBomQuantity } from '@/utils/bomQuantity'
import type { InventoryMaterial } from '@/types/inventory/material'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import { productBomApi } from '@/api/product/bom'
import type { StandardProcessItem } from '@/types/product/standardProcess'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import Sortable from 'sortablejs'

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

// 项目可关联启用的标准工序，也可只保存用户填写的名称。
const processOptions = ref<StandardProcessItem[]>([])
async function loadProcessOptions() {
  try {
    const res = await standardProcessApi.getEnabledProcesses()
    processOptions.value = res.data || []
    walkTree(items.value, matchProjectByName)
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

// 仅完整名称唯一匹配时关联ID；不存在或重名都保留原文，供用户手动选择。
function matchProjectByName(row: EngineeringBomItem) {
  if (row.processId != null || !row.processName) return
  const matches = processOptions.value.filter((p) => p.processName === row.processName)
  if (matches.length === 1) row.processId = matches[0].processId
}

const handleProjectChange = (row: EngineeringBomItem, value: number | string | undefined) => {
  if (typeof value === 'number') {
    const p = processOptions.value.find((x) => x.processId === value)
    if (!p) return
    row.processId = p.processId
    row.processName = p.processName
  } else {
    row.processId = undefined
    row.processName = value ?? ''
    matchProjectByName(row)
  }
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

// ======== 明细行工具（2026-10-10 dev-20261010-017：恢复平铺，取消子物料层级） ========
// 说明：子物料（parent_material_id 树形）实测未使用、无下游依赖，已回退为平铺列表并恢复拖拽排序；
// 后端 parent_material_id 字段与历史数据保留不动（此处统一按 null 写回）。

// 新增/复制行分配的临时负数 id：唯一、稳定，供 el-table row-key 与拖拽使用；
// 提交后端时由 toSubmitItems() 剥离（新增行 itemId 仍为空，不改变接口约定）。
let tempItemIdSeq = -1
const nextTempItemId = () => tempItemIdSeq--

/** 外部平铺数据 → 内部行（补稳定临时 id；强制无层级） */
function toFlatItems(list: EngineeringBomItem[]): EngineeringBomItem[] {
  return (list || []).map((it) => {
    const copy: EngineeringBomItem = { ...it, parentMaterialId: null }
    delete copy.children
    if (copy.itemId == null) copy.itemId = nextTempItemId()
    return copy
  })
}

/** 内部行 → 提交/对外数据（保序、写回 sortOrder、剥离临时 id、强制无层级） */
function toSubmitItems(list: EngineeringBomItem[]): EngineeringBomItem[] {
  return (list || []).map((it, idx) => {
    const copy: EngineeringBomItem = { ...it }
    delete copy.children
    copy.parentMaterialId = null
    copy.sortOrder = idx + 1
    if (copy.itemId != null && Number(copy.itemId) < 0) copy.itemId = undefined
    return copy
  })
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
  emit('update:modelValue', toSubmitItems(items.value))
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
    initSortable()
  })
})

onUnmounted(() => {
  window.removeEventListener('resize', calculateTableHeight)
  emitChange.cancel()
  destroySortable()
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
  recalcAppliedIssue(row)
}

/**
 * 基数变化时自动计算数量
 * 数量 = 基数 ÷ 模数
 */
const handleBaseQtyChange = (row: EngineeringBomItem) => {
  recalcAppliedIssue(row)
}

/** 损耗率变化：重算应用料/实际投料 */
const handleLossRateChange = (row: EngineeringBomItem) => {
  recalcAppliedIssue(row)
}

/**
 * 计算应用料/实际投料（前端预览，与后端一致）
 * 有效基数、模数下：用量 = 基数 ÷ 模数；应用料 = 用量 × (1 + 损耗率/100)
 * 实际投料 = 单位应用料（含损耗、不取整）；整批取整与最低投料量下限由领料/缺料/预留侧按工单数量计算
 */
const recalcAppliedIssue = (row: EngineeringBomItem) => {
  // 初始化/导入/参数变更走同一口径，不能沿用Excel实发数量或旧quantity。
  const quantity = calculateBomQuantity(row.baseQty, row.moduleQty)
  if (quantity !== undefined) row.quantity = quantity
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
    if (JSON.stringify(newVal) !== JSON.stringify(toSubmitItems(items.value))) {
      items.value = toFlatItems(newVal)
      walkTree(items.value, matchProjectByName)
      // 初始化也先按当前基数/模数重算用量，再计算应用料。
      walkTree(items.value, recalcAppliedIssue)
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
 * 复制物料（平铺：复制为一行追加到末尾）
 */
const handleCopyItem = (item: EngineeringBomItem) => {
  const copyItem: EngineeringBomItem = JSON.parse(JSON.stringify(item))
  delete copyItem.children
  copyItem.itemId = nextTempItemId()
  copyItem.parentMaterialId = null
  copyItem.sortOrder = items.value.length + 1
  items.value.push(copyItem)
  ElMessage.success('复制成功')
}

/**
 * 删除物料（平铺列表）
 */
const handleDeleteItem = async (row: EngineeringBomItem) => {
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
    // 平铺列表：按行引用定位，失败兜底按 id（失败不谎报成功）
    let idx = items.value.indexOf(row)
    if (idx < 0) idx = items.value.findIndex((it) => Number(it.itemId) === Number(row.itemId))
    if (idx >= 0) {
      items.value.splice(idx, 1)
      ElMessage.success('删除成功')
    } else {
      ElMessage.warning('该行已不在列表中，删除未生效')
    }
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

// ==================== 拖拽排序（2026-10-10 dev-20261010-017：平铺表恢复 Sortable） ====================

let sortableInstance: Sortable | null = null

/** 初始化拖拽排序（拖手柄 .drag-handle；拖动后重排 items 并回写 sortOrder） */
const initSortable = () => {
  const el = tableRef.value?.$el?.querySelector(
    '.el-table__body-wrapper tbody'
  ) as HTMLElement | null
  if (!el) {
    // 表格未渲染完成时延迟重试
    setTimeout(() => initSortable(), 200)
    return
  }
  destroySortable()
  sortableInstance = Sortable.create(el, {
    handle: '.drag-handle',
    animation: 150,
    easing: 'cubic-bezier(0.25, 0.1, 0.25, 1)',
    ghostClass: 'sortable-ghost',
    dragClass: 'sortable-drag',
    onStart: () => {
      tableRef.value?.$el?.classList.add('is-dragging')
    },
    onEnd: (evt: Sortable.SortableEvent) => {
      tableRef.value?.$el?.classList.remove('is-dragging')
      const { oldIndex, newIndex } = evt
      if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return
      const newItems = [...items.value]
      const [moved] = newItems.splice(oldIndex, 1)
      newItems.splice(newIndex, 0, moved)
      newItems.forEach((it, idx) => {
        it.sortOrder = idx + 1
      })
      items.value = newItems
    },
  })
}

const destroySortable = () => {
  if (sortableInstance) {
    sortableInstance.destroy()
    sortableInstance = null
  }
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

// ==================== 全量同步（库存物料自动关联，dev-20261010-002） ====================
// 仅对「未关联」行按【名称+规格】回查物料库：唯一命中自动回填 materialId/materialCode/单位；
// 未匹配 / 多义行不进 item 对象，只记在 syncFlags，避免污染提交 payload。
const syncLoading = ref(false)
const syncFlags = ref<Record<string, 'AMBIGUOUS' | 'NOT_FOUND'>>({})

/** 当前行未关联物料（无 materialId 或无 materialCode） */
const isRowUnlinked = (row: EngineeringBomItem) =>
  !row.materialId || Number(row.materialId) <= 0 || !row.materialCode?.trim()

const handleSyncMaterials = async () => {
  if (!items.value.length) {
    ElMessage.warning('没有可同步的物料')
    return
  }
  // 只收集未关联的行（已关联的不动）
  const targets: EngineeringBomItem[] = []
  walkTree(items.value, (row) => {
    if (isRowUnlinked(row)) targets.push(row)
  })
  if (!targets.length) {
    syncFlags.value = {}
    ElMessage.info('没有未关联的物料，无需同步')
    return
  }

  syncLoading.value = true
  try {
    const payload: BomMaterialMatchItem[] = targets.map((row, i) => ({
      index: i,
      name: row.materialName || '',
      spec: row.specification || '',
    }))
    const res = await productBomApi.matchMaterials(payload)
    const results = res.data || []

    let matched = 0
    let notFound = 0
    let ambiguous = 0
    const flags: Record<string, 'AMBIGUOUS' | 'NOT_FOUND'> = {}
    results.forEach((r) => {
      const row = targets[r.index]
      if (!row) return
      if (r.status === 'MATCHED' && r.materialId) {
        // 唯一命中：回填 materialId / materialCode / 单位（名称保留 Excel 原文）
        row.materialId = r.materialId
        row.materialCode = r.materialCode || ''
        if (r.unit) row.unit = r.unit
        matched++
      } else if (r.status === 'AMBIGUOUS') {
        flags[String(row.itemId)] = 'AMBIGUOUS'
        ambiguous++
      } else {
        flags[String(row.itemId)] = 'NOT_FOUND'
        notFound++
      }
    })
    syncFlags.value = flags
    ElMessage.success(`同步完成：命中 ${matched}，未匹配 ${notFound}，多义待确认 ${ambiguous}`)
  } catch (error) {
    console.error('物料全量同步失败:', error)
    ElMessage.error('物料全量同步失败')
  } finally {
    syncLoading.value = false
  }
}

/** 行标黄：未匹配（浅黄） / 多义待确认（深黄） */
const rowClassName = ({ row }: { row: EngineeringBomItem }) => {
  const flag = syncFlags.value[String(row.itemId)]
  if (flag === 'AMBIGUOUS') return 'bom-row-ambiguous'
  if (flag === 'NOT_FOUND') return 'bom-row-unmatched'
  return ''
}

/** 行标黄原因（悬浮提示） */
const syncReasonText = (row: EngineeringBomItem): string => {
  const flag = syncFlags.value[String(row.itemId)]
  if (flag === 'AMBIGUOUS') return '库中存在多条同名同规格物料，请手动选择'
  if (flag === 'NOT_FOUND') return '库中未匹配到同名同规格物料'
  return ''
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

/**
 * 复制 物料名 · 规格 · 编码 到剪贴板
 * （物料名称是 el-select，文本选不中，给个显式复制）
 */
function copyMaterial(row: EngineeringBomItem) {
  const text = [row.materialName, row.specification, row.materialCode]
    .filter((v) => v != null && String(v).trim() !== '')
    .join(' · ')
  if (!text) {
    ElMessage.warning('无内容可复制')
    return
  }
  const done = () => ElMessage.success(`已复制：${text}`)
  const clip = navigator.clipboard
  if (clip && typeof clip.writeText === 'function') {
    clip.writeText(text).then(done).catch(() => {
      if (fallbackCopy(text)) done()
      else ElMessage.warning('复制失败，请手动选择')
    })
  } else if (fallbackCopy(text)) {
    done()
  } else {
    ElMessage.warning('复制失败，请手动选择')
  }
}

/** 非安全上下文（http 内网）下 navigator.clipboard 不可用时的兜底 */
function fallbackCopy(text: string): boolean {
  try {
    const ta = document.createElement('textarea')
    ta.value = text
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    const ok = document.execCommand('copy')
    document.body.removeChild(ta)
    return ok
  } catch {
    return false
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
  getItems: () => toSubmitItems(items.value),
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

  // 全量同步：未匹配标浅黄 / 多义待确认标深黄（dev-20261010-002）
  :deep(.el-table__body tr.bom-row-unmatched > td) {
    background-color: #fdf6ec !important;
  }

  :deep(.el-table__body tr.bom-row-ambiguous > td) {
    background-color: #ffe7ba !important;
  }

  .sync-warn-icon {
    margin-left: 4px;
    color: #e6a23c;
    vertical-align: middle;
    cursor: help;
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

.material-name-cell {
  display: flex;
  align-items: center;
  gap: 4px;
}
.material-name-cell > :first-child {
  flex: 1;
  min-width: 0;
}
</style>
