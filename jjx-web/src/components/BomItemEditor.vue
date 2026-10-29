<template>
  <div class="bom-item-editor">
    <!-- 操作按钮区域 -->
    <div class="editor-header">
      <div class="header-left">
        <el-tooltip
          content="快速模式：材料名称必填，规格型号选填，允许未建档先提交审核；审核员再匹配或新建物料"
          placement="bottom"
        >
          <el-switch
            :model-value="quickMode"
            @change="setQuickMode"
            active-text="快速模式"
            inactive-text="常规模式"
            inline-prompt
            style="--el-switch-on-color: #e6a23c"
          />
        </el-tooltip>
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
      highlight-current-row
      :current-row-key="currentRowKey"
      :height="tableHeight"
      @selection-change="handleSelectionChange"
      @row-click="handleRowClick"
      :span-method="comboSpan"
      :row-class-name="rowClassName"
      class="bom-item-table"
    >
      <!-- 序号列 - 拖拽手柄 -->
      <el-table-column label="序号" width="50" align="center" fixed="left">
        <template #default="scope">
          <div class="drag-cell">
            <el-icon class="drag-handle"><Rank /></el-icon>
            <el-icon v-if="scope.row.materialId" size="small" color="#15bf15">
              <Check />
            </el-icon>
            <el-icon v-else size="small" color="#e56706">
              <WarningFilled />
            </el-icon>
            <span v-if="comboNoOf(scope.$index)" class="row-index">{{
              comboNoOf(scope.$index)
            }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="项目" prop="processName" width="80">
        <template #default="scope">
          <StandardProcessSelector
            :mode="PROJECT_PICKER_MODE"
            :model-value="scope.row.processId ?? scope.row.processName"
            :process-name="scope.row.processName"
            size="small"
            @change="(val: number | string | undefined) => handleProjectChange(scope.row, val)"
          />
        </template>
      </el-table-column>
      <!-- 物料名称：常规模式选物料；快速模式直接录材料原文（dev-20261020-029 A） -->
      <el-table-column label="物料名称" prop="materialName" min-width="140">
        <template #default="scope">
          <div class="material-name-cell">
            <el-input
              v-if="quickMode"
              v-model="scope.row.materialName"
              placeholder="输入材料名称（必填）"
              size="small"
              maxlength="200"
            />
            <BomMaterialSelector
              v-else
              :material-id="scope.row.materialId"
              :material-name="scope.row.materialName"
              confirm-selection
              @draft-change="(draft) => handleMaterialDraft(scope.row, draft)"
              @select="(material) => handleMaterialSelect(material, scope.row)"
              @clear="handleMaterialClear(scope.row)"
              @create="(keyword) => handleCreateMaterial(scope.row, keyword)"
            />
            <!-- <el-tag
              v-if="quickMode && !scope.row.materialId"
              size="small"
              type="warning"
              effect="plain"
              >未建档</el-tag
            > -->
          </div>
        </template>
      </el-table-column>
      <!-- 规格型号（dev-20261024-023：默认取物料系统生成值，可手改，可恢复） -->
      <el-table-column label="规格" prop="specification" width="120">
        <template #default="scope">
          <el-input
            v-model="scope.row.specification"
            placeholder="选填"
            size="small"
            maxlength="500"
            @input="markManualSpec(scope.row)"
          />
          <div v-if="isManualSpec(scope.row)" class="spec-manual">
            <el-tag size="small" type="warning">已改写</el-tag>
            <el-button link type="primary" size="small" @click="restoreAutoSpec(scope.row)">
              恢复系统生成
            </el-button>
          </div>
          <div v-if="specErrors[String(scope.row.itemId)]" class="spec-error">
            规格生成失败，请点击刷新重试
          </div>
          <div v-if="isManualSpec(scope.row) && specSourceChanged(scope.row)" class="spec-error">
            物料属性已变，当前为人工改写，请复核
          </div>
        </template>
      </el-table-column>
      <!-- 模数（恢复原义：每份材料可产出产品数量；用量 = 基数 ÷ 模数） -->
      <el-table-column label="模数" prop="moduleQty" align="center" width="60">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.moduleQty"
            :min="1"
            :precision="0"
            :controls="false"
            size="small"
            @change="handleModuleQtyChange(scope.row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="跳距" prop="split" width="90">
        <template #default="scope">
          <el-input-number
            :model-value="splitNumber(scope.row.split)"
            :min="0"
            size="small"
            :controls="false"
            :precision="0"
            @update:model-value="(value) => handleSplitChange(scope.row, value)"
          >
            <template #suffix>
              <span>mm</span>
            </template>
          </el-input-number>
        </template>
      </el-table-column>
      <!-- 基数 -->
      <el-table-column label="基数" prop="baseQty" align="center" width="60">
        <template #header>
          <span>基数</span>
          <el-tooltip content="产品单位用量/每个项目标准用量" placement="top">
            <el-icon class="info-icon"><InfoFilled /></el-icon>
          </el-tooltip>
        </template>
        <template #default="scope">
          <el-input-number
            v-model="scope.row.baseQty"
            :controls="false"
            :min="1"
            :precision="0"
            size="small"
            @change="handleBaseQtyChange(scope.row)"
          />
        </template>
      </el-table-column>
      <!-- 单位 -->
      <el-table-column label="单位" prop="unit" width="80">
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
      <!-- 最低投料量 -->
      <el-table-column label="最低投料量" prop="minIssueQty" align="center" width="60">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.minIssueQty"
            :min="0"
            :precision="0"
            size="small"
            :controls="false"
            @change="recalcAppliedIssue(scope.row)"
          />
        </template>
      </el-table-column>
      <!-- 应用料（含损耗，只读） -->
      <el-table-column label="应用料" prop="appliedQty" align="center" width="70">
        <template #default="scope">{{ formatQty(scope.row.appliedQty) }}</template>
      </el-table-column>

      <!-- 行审核状态（dev-20261020-029） -->
      <el-table-column label="行状态" width="80" align="center">
        <template #default="scope">
          <div class="row-status-cell">
            <el-tooltip :content="scope.row.rejectReason || '无'" placement="top">
              <el-tag
                :type="BomItemReviewStatusEnum.getTagProps(rowReviewStatus(scope.row)).type"
                size="small"
              >
                {{ BomItemReviewStatusEnum.getLabel(rowReviewStatus(scope.row)) }}
              </el-tag>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>

      <!-- 操作列 -->
      <el-table-column label="操作" min-width="120" align="left" fixed="right">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="handleCopyItem(scope.row)"
            >复制</el-button
          >

          <el-button
            v-if="isPendingRemove(scope.row)"
            link
            type="warning"
            size="small"
            @click="handleUndoRemove(scope.row)"
            >撤销移除</el-button
          >
          <el-button
            v-if="!isPendingRemove(scope.row)"
            link
            size="small"
            type="danger"
            @click="handleDeleteItem(scope.row)"
            >删除</el-button
          >
          <el-button
            v-if="!scope.row.materialId && !isPendingRemove(scope.row)"
            link
            type="primary"
            size="small"
            @click="handleCreateMaterial(scope.row, scope.row.materialName || '')"
            >建档</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <MaterialFormDialog
      v-model="materialFormVisible"
      :preset-data="materialPreset"
      from-bom
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
import { BomItemReviewStatusEnum } from '@/enums/product'
import { calculateBomQuantity } from '@/utils/bomQuantity'
import { bomGroupRows } from '@/utils/bomGroups'
import { autoSpecOf, isManualSpec, specFollowValue, specSourceChanged } from './bomSpecMode'
import { generateBomSpecification, specificationSource } from './bomSpecification'
import type { InventoryMaterial } from '@/types/inventory/material'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import StandardProcessSelector from '@/components/Selector/StandardProcessSelector.vue'
import { useEnabledProcesses } from '@/composables/useEnabledProcesses'
import { productBomApi } from '@/api/product/bom'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import { materialMasterApi } from '@/api/inventory/materialMaster'
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
const errorRowKey = ref<number | string | null>(null)
const currentRowKey = ref<number | string | undefined>(undefined)
const items = ref<EngineeringBomItem[]>([])
const selectedItems = ref<EngineeringBomItem[]>([])
const tableLoading = ref(false)
const refreshLoading = ref(false)
const tableHeight = ref(400)
// 快速模式（dev-20261020-029）：直接录材料名称+规格，materialId 可空
const quickMode = ref(false)
const materialDrafts = ref<Record<string, string>>({})
const specErrors = ref<Record<string, boolean>>({})
const specRequests = new Map<string, Promise<void>>()
const specVersions = new Map<string, number>()
function handleMaterialDraft(row: EngineeringBomItem, draft: { text: string; pending: boolean }) {
  const key = String(row.itemId)
  if (draft.pending) materialDrafts.value[key] = draft.text
  else delete materialDrafts.value[key]
}
function setQuickMode(value: string | number | boolean) {
  const pendingRow = items.value.find(
    (row) => materialDrafts.value[String(row.itemId)] !== undefined && !isPendingRemove(row)
  )
  if (pendingRow) {
    void markRowError(pendingRow, '请先选择候选物料或取消搜索修改，再切换模式')
    return
  }
  quickMode.value = Boolean(value)
}

/** 行审核状态（历史数据缺省按草稿展示） */
const rowReviewStatus = (row: EngineeringBomItem): number =>
  row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value

const isPendingRemove = (row: EngineeringBomItem): boolean =>
  rowReviewStatus(row) === BomItemReviewStatusEnum.PENDING_REMOVE.value

const isApprovedRow = (row: EngineeringBomItem): boolean =>
  rowReviewStatus(row) === BomItemReviewStatusEnum.APPROVED.value

// 项目选择器交互模式：'dropdown' 内联下拉 / 'dialog' 分组网格弹窗
// （2026-10-12 两模式并存，实际效果对比后再定用哪个）
const PROJECT_PICKER_MODE: 'dropdown' | 'dialog' = 'dropdown'

// 项目可关联启用的标准工序（全局单例缓存，多实例只请求一次），也可只保存用户填写的名称。
const { processes: processOptions } = useEnabledProcesses()
// 工序加载完成后，为仅填了名称的明细行回填 processId
watch(processOptions, () => walkTree(items.value, matchProjectByName))

// 仅完整名称唯一匹配时关联ID；不存在或重名都保留原文，供用户手动选择。
function matchProjectByName(row: EngineeringBomItem) {
  if (row.processId != null || !row.processName) return
  const matches = processOptions.value.filter((p) => p.processName === row.processName)
  if (matches.length === 1) {
    row.processId = matches[0].processId
    row.isCommon = false // 解析到工序 → 非通用（dev-1792056991945）
  }
}

const handleProjectChange = (row: EngineeringBomItem, value: number | string | undefined) => {
  if (typeof value === 'number') {
    const p = processOptions.value.find((x) => x.processId === value)
    if (!p) return
    row.processId = p.processId
    row.processName = p.processName
    row.isCommon = false // 选了工序即非通用（dev-20261012-039）
  } else {
    row.processId = undefined
    row.processName = value ?? ''
    matchProjectByName(row)
    row.isCommon = row.processId == null && !(row.processName || '').trim()
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

// ======== 明细行工具：界面平铺，连续同项目行的组合关系写入 parent_material_id ========

// 新增/复制行分配的临时负数 id：唯一、稳定，供 el-table row-key 与拖拽使用；
// 提交时保留临时编号供组合引用，后端统一映射为数据库生成的真实行号。
let tempItemIdSeq = -1
const nextTempItemId = () => tempItemIdSeq--

/** 外部平铺数据 → 内部行（补稳定临时 id） */
function toFlatItems(list: EngineeringBomItem[]): EngineeringBomItem[] {
  const flat = (list || []).map((it) => {
    const copy: EngineeringBomItem = { ...it }
    delete copy.children
    if (Number(it.materialId) > 0 && it.materialSpecificationSource) copy.materialSpecification = autoSpecOf(it)
    if (copy.reviewStatus == null) copy.reviewStatus = BomItemReviewStatusEnum.DRAFT.value
    // 待移除行回填时必须继续带 removeRequested，否则保存会被当成“撤销移除”
    copy.removeRequested =
      copy.reviewStatus === BomItemReviewStatusEnum.PENDING_REMOVE.value ||
      copy.removeRequested === true
    if (copy.itemId == null) copy.itemId = nextTempItemId()
    return copy
  })
  return flat
}

/**
 * 内部行 → 提交/对外数据（保序、写回 sortOrder）。
 * dev-20261022-017：保留负数临时 itemId 与 parentMaterialId —— 后端用临时 id 映射父子关系，
 * 新增子行必须能指到同批新增的父行。
 */
function toSubmitItems(list: EngineeringBomItem[]): EngineeringBomItem[] {
  return (list || []).map((it, idx) => {
    const copy: EngineeringBomItem = { ...it }
    if (Number(it.materialId) > 0 && it.materialSpecificationSource) copy.materialSpecification = autoSpecOf(it)
    delete copy.children
    // 组合：同一「项目」连续多行视为一组，组内除首行外都挂到首行（落库 parent_material_id）
    const leader = comboLeaderOf(it)
    copy.parentMaterialId = leader && leader !== it ? (leader.itemId ?? null) : null
    copy.sortOrder = idx + 1
    copy.removeRequested = !!it.removeRequested
    return copy
  })
}

/** 遍历树（含所有层级） */
/** 组合判定：连续且「项目」相同的行视为同一组合（项目为空视为未分组） */
const draftGroups = computed(() => bomGroupRows(items.value))

/** 每行的组合首行（组内其它行都挂到它），未分组返回 null */
const comboLeaderOf = (row: EngineeringBomItem): EngineeringBomItem | null => {
  const idx = items.value.indexOf(row)
  if (idx < 0) return null
  const entry = draftGroups.value[idx]
  const leader = draftGroups.value.find((group) => group.groupKey === entry.groupKey)?.item
  return leader && leader !== row ? leader : null
}

/** 序号列：组合序号（连续同项目为一组，1、2、3…）+ 组内合并 */
const comboRuns = computed(() => {
  return draftGroups.value
    .filter((group) => group.span > 0)
    .map((group) => ({ start: group.index, size: group.span, no: group.displayNumber }))
})

const comboNoOf = (index: number): number => {
  const run = comboRuns.value.find((r) => index >= r.start && index < r.start + r.size)
  return run ? run.no : 0
}

/** 序号列合并：同一组合合并成一个单元格 */
const comboSpan = ({ rowIndex, columnIndex }: { rowIndex: number; columnIndex: number }) => {
  if (columnIndex !== 0) return [1, 1]
  const run = comboRuns.value.find((r) => rowIndex >= r.start && rowIndex < r.start + r.size)
  if (!run || run.size <= 1) return [1, 1]
  if (rowIndex !== run.start) return [0, 0]
  return [run.size, 1]
}

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
  delete materialDrafts.value[String(row.itemId)]

  row.materialId = material.materialId || 0
  row.materialCode = material.materialCode
  row.materialName = material.materialName
  // dev-20261024-023：选料带入系统生成规格；物料无规格时保留原手填值，不用空串覆盖
  const autoSpec = (material.size || '').trim()
  delete row.materialSpecificationSource
  row.materialSpecification = autoSpec
  row.specificationBaseline = autoSpec
  row.specificationManual = false
  if (autoSpec) row.specification = autoSpec
  row.unit = material.unit || 'PCS'
  row.materialType = material.rootType || undefined
  row.create = false
  recalcAppliedIssue(row)
  void loadAutoSpec(row, true)
}

function markManualSpec(row: EngineeringBomItem) {
  row.specificationManual = row.specification !== autoSpecOf(row)
}

/** 复用物料详情中的服务端属性生成结果，不把库存规格文本当作生成器。 */
function loadAutoSpec(row: EngineeringBomItem, selected = false): Promise<void> {
  const key = String(row.itemId)
  const materialId = row.materialId
  const version = (specVersions.get(key) || 0) + 1
  specVersions.set(key, version)
  const request = (async () => {
    try {
      const response = await materialMasterApi.detail(materialId)
      if (!response.data) throw new Error('物料详情为空')
      const current = items.value.find((item) => String(item.itemId) === key)
      if (!current || current.materialId !== materialId || specVersions.get(key) !== version) return
      const source = specificationSource(response.data)
      const auto = generateBomSpecification(source)
      const previous = autoSpecOf(current)
      const manual = previous ? isManualSpec(current) : !!current.specificationManual
      current.specificationManual = manual
      current.materialSpecificationSource = source
      current.materialSpecification = auto
      // 选料后的异步返回不能覆盖用户刚输入的规格；刷新保留已有人工值。
      if (selected && !manual && auto) current.specification = auto
      else {
        if (!manual && previous) current.specificationBaseline = previous
        followAutoSpec(current)
      }
      if (!isManualSpec(current)) current.specificationBaseline = auto
      delete specErrors.value[key]
    } catch (error) {
      const current = items.value.find((item) => String(item.itemId) === key)
      if (current?.materialId === materialId && specVersions.get(key) === version) {
        specErrors.value[key] = true
        console.error('BOM规格生成失败:', error)
      }
    } finally {
      if (specVersions.get(key) === version) specRequests.delete(key)
    }
  })()
  specRequests.set(key, request)
  return request
}

async function prepareSpecifications(): Promise<boolean> {
  await Promise.all(specRequests.values())
  await Promise.all(
    items.value
      .filter((row) => Number(row.materialId) > 0 && !isPendingRemove(row))
      .map((row) => loadAutoSpec(row))
  )
  const failed = items.value.find(
    (row) => !isPendingRemove(row) && specErrors.value[String(row.itemId)]
  )
  if (failed) {
    await markRowError(failed, `物料「${failed.materialName}」规格生成失败，请刷新重试`)
    return false
  }
  return true
}

/** 空值及已知旧自动值跟随系统规格；人工改写不覆盖（dev-20261024-023）。 */
function followAutoSpec(row: EngineeringBomItem) {
  const next = specFollowValue(row)
  if (next !== null) row.specification = next
}

/** 恢复为物料当前系统生成规格（dev-20261024-023） */
function restoreAutoSpec(row: EngineeringBomItem) {
  const auto = autoSpecOf(row)
  if (auto) {
    row.specification = auto
    row.specificationBaseline = auto
    row.specificationManual = false
  }
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
      materialDrafts.value = {}
      walkTree(items.value, matchProjectByName)
      // 初始化也先按当前基数/模数重算用量，再计算应用料。
      walkTree(items.value, recalcAppliedIssue)
      // dev-20261024-023：规格为空的行自动跟随物料系统规格；人工改写不覆盖
      walkTree(items.value, followAutoSpec)
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
    isCommon: true, // dev-1792056991945：新增行默认「整单通用」，选工序会自动取消
    remark: '',
    sortOrder: items.value.length + 1,
    create: false,
    reviewStatus: BomItemReviewStatusEnum.DRAFT.value,
    removeRequested: false,
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
  // 复制行重新走审核（dev-20261020-029）
  copyItem.reviewStatus = BomItemReviewStatusEnum.DRAFT.value
  copyItem.rejectReason = undefined
  copyItem.approvedFingerprint = undefined
  copyItem.approvedBy = undefined
  copyItem.approvedTime = undefined
  copyItem.removeConfirmed = false
  copyItem.removeRequested = false
  items.value.push(copyItem)
  ElMessage.success('复制成功')
}

/**
 * 删除物料（平铺列表）
 */
const handleDeleteItem = async (row: EngineeringBomItem) => {
  if (isPendingRemove(row)) {
    ElMessage.info('该行已标记待移除，需审核员确认')
    return
  }
  const approved = isApprovedRow(row)
  try {
    await ElMessageBox.confirm(
      approved
        ? `物料 "${row.materialName || row.materialCode || ''}" 已审核通过，删除将标记为「待移除」，审核员确认后才物理删除。确定继续吗？`
        : `确定要删除物料 "${row.materialName || row.materialCode || ''}" 吗？`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
    if (approved) {
      // 已通过行先标记待移除，保留行ID供审核员确认（dev-20261020-029 B）
      row.removeRequested = true
      row.reviewStatus = BomItemReviewStatusEnum.PENDING_REMOVE.value
      row.removeConfirmed = false
      ElMessage.success('已标记待移除，保存后由审核员确认')
      return
    }
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

/** 撤销「待移除」：保存后后端按指纹恢复为审核通过/待审核 */
const handleUndoRemove = (row: EngineeringBomItem) => {
  row.removeRequested = false
  row.reviewStatus = row.approvedFingerprint
    ? BomItemReviewStatusEnum.APPROVED.value
    : BomItemReviewStatusEnum.PENDING.value
  ElMessage.success('已撤销移除，保存后生效')
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
    if (await prepareSpecifications()) ElMessage.success('规格已按最新物料属性刷新')
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
  if (errorRowKey.value != null && String(row.itemId) === String(errorRowKey.value)) {
    return 'bom-row-error'
  }
  if (isPendingRemove(row)) return 'bom-row-pending-remove'
  const flag = syncFlags.value[String(row.itemId)]
  if (flag === 'AMBIGUOUS') return 'bom-row-ambiguous'
  if (flag === 'NOT_FOUND') return 'bom-row-unmatched'
  return ''
}

/** 校验失败：红行 + 滚动到可见；toast 只作补充。 */
async function markRowError(item: EngineeringBomItem, message: string) {
  errorRowKey.value = item.itemId ?? null
  ElMessage.warning(message)
  await nextTick()
  const row = tableRef.value?.$el?.querySelector('tr.bom-row-error') as HTMLElement | null
  row?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  row?.querySelector<HTMLInputElement>('.material-name-cell input')?.focus()
}

function handleRowClick(row: EngineeringBomItem) {
  currentRowKey.value = row.itemId ?? undefined
  if (errorRowKey.value != null && String(errorRowKey.value) === String(row.itemId)) {
    errorRowKey.value = null
  }
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

/** 跳距：界面只输整数、单位固定 mm；存储保留“数字+mm”字符串供打印/快照使用。 */
function splitNumber(value?: string | null): number | undefined {
  if (value == null || value === '') return undefined
  const n = Number.parseFloat(String(value))
  return Number.isFinite(n) ? Math.trunc(n) : undefined
}
function handleSplitChange(row: EngineeringBomItem, value?: number | null) {
  if (value == null || !Number.isFinite(Number(value)) || Number(value) <= 0) {
    row.split = ''
    return
  }
  row.split = `${Math.trunc(Number(value))}mm`
}
let creatingRow: EngineeringBomItem | null = null

const handleMaterialClear = (row: EngineeringBomItem) => {
  delete materialDrafts.value[String(row.itemId)]
  row.materialId = 0
  row.materialCode = ''
  row.materialName = ''
  row.specification = ''
  row.materialSpecification = undefined
  delete row.materialSpecificationSource
  row.specificationBaseline = undefined
  row.specificationManual = false
  specVersions.set(String(row.itemId), (specVersions.get(String(row.itemId)) || 0) + 1)
  specRequests.delete(String(row.itemId))
  delete specErrors.value[String(row.itemId)]
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
    clip
      .writeText(text)
      .then(done)
      .catch(() => {
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
  prepareSpecifications,
  getItems: () => toSubmitItems(items.value),
  clearItems: () => {
    materialDrafts.value = {}
    items.value = []
    selectedItems.value = []
  },
  validateItems: (): boolean => {
    if (items.value.length === 0) {
      ElMessage.warning('请至少添加一个物料')
      return false
    }

    const failures: Array<{ item: EngineeringBomItem; message: string }> = []
    walkTree(items.value, (item) => {
      if (failures.length) return
      if (isPendingRemove(item)) return
      if (specRequests.has(String(item.itemId)) || specErrors.value[String(item.itemId)]) {
        failures.push({
          item,
          message: `物料「${item.materialName}」规格尚未生成，请稍后或刷新重试`,
        })
        return
      }
      if (!quickMode.value && materialDrafts.value[String(item.itemId)] !== undefined) {
        failures.push({
          item,
          message: `第${items.value.indexOf(item) + 1}行物料搜索尚未确认，请选择候选物料或取消修改`,
        })
        return
      }
      if (!item.materialName?.trim()) {
        failures.push({ item, message: '材料名称不能为空' })
        return
      }
      // 规格型号不必填（dev-20261022-020）；其余校验保留并定位到行。
      if (!quickMode.value && (!item.materialId || Number(item.materialId) <= 0)) {
        failures.push({
          item,
          message: `请为材料 "${item.materialName}" 选择物料，或切换快速模式`,
        })
        return
      }
      if (item.quantity <= 0) {
        failures.push({ item, message: `物料 "${item.materialName}" 数量必须大于0` })
      }
    })

    if (failures.length) {
      const [failure] = failures
      void markRowError(failure.item, failure.message)
      return false
    }
    errorRowKey.value = null
    return true
  },
  reorderItems,
})
</script>

<style scoped lang="scss">
/* 编辑行/错误行（dev-20261022-020）：点中=系统浅蓝，聚焦=蓝色左边框，错误=红色优先 */
.bom-item-table :deep(.el-table__body tr:focus-within > td) {
  background: #ecf5ff !important;
}
.bom-item-table :deep(.el-table__body tr:focus-within) {
  box-shadow: inset 3px 0 0 #409eff;
}
.bom-item-table :deep(.el-table__body tr.bom-row-error > td) {
  background: #fef0f0 !important;
}
.bom-item-table :deep(.el-table__body tr.bom-row-error) {
  box-shadow: inset 3px 0 0 #f56c6c;
}

.bom-item-editor {
  width: 100%;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
}

.split-unit {
  margin-left: 4px;
  font-size: 12px;
  color: #909399;
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

  // 待移除行：整行置灰并禁用编辑（保留可见，等待审核员确认）
  :deep(.el-table__body tr.bom-row-pending-remove > td) {
    background-color: #f4f4f5 !important;
    color: #909399;
  }

  :deep(.el-table__body tr.bom-row-pending-remove > td:not(:last-child)) {
    opacity: 0.6;
    pointer-events: none;
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

.group-parent-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
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

.row-status-cell {
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.reject-icon {
  color: var(--el-color-danger);
  cursor: help;
}

/* dev-20261024-023：规格型号「已改写」标记 + 恢复入口 */
.spec-manual {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 2px;
}
.spec-error {
  color: var(--el-color-danger);
  font-size: 12px;
}
</style>
