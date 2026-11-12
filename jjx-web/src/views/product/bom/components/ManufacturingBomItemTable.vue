<template>
  <div class="manufacturing-item-table">
    <div class="editor-header">
      <div class="header-left">
        <el-button v-if="!readonly" type="primary" :icon="Plus" size="small" @click="handleAdd">
          添加投入材料
        </el-button>
        <span class="header-hint">
          规则：产出「模数」数量的本层物料，投入「基数」数量的该行材料；模数、基数均须大于 0，最多 4 位小数
        </span>
      </div>
      <div class="header-right">
        <el-tag type="info" size="small">共 {{ rows.length }} 行投入</el-tag>
      </div>
    </div>

    <el-table :data="rows" border size="small" row-key="itemId" class="manufacturing-item-grid">
      <el-table-column label="序号" width="50" align="center">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column label="投入物料" min-width="190">
        <template #default="scope">
          <BomMaterialSelector
            v-if="!readonly"
            :material-id="scope.row.materialId"
            :material-name="scope.row.materialName"
            confirm-selection
            @select="(material: InventoryMaterial) => handleSelect(scope.row, material)"
            @clear="handleClear(scope.row)"
            @create="(keyword: string) => handleCreate(scope.row, keyword)"
          />
          <span v-else>{{ scope.row.materialCode }} {{ scope.row.materialName }}</span>
        </template>
      </el-table-column>

      <el-table-column label="物料编码" prop="materialCode" width="120" />

      <!-- 档案规格只读：从物料档案带入，不在这里反向改物料档案 -->
      <el-table-column label="档案规格" width="140" show-overflow-tooltip>
        <template #default="scope">
          <span class="archive-spec">{{ archiveSpecOf(scope.row) || '—' }}</span>
        </template>
      </el-table-column>

      <!-- 档案单位与 BOM 投入单位分开显示：档案单位只读，投入单位可改 -->
      <el-table-column label="档案单位" width="90" align="center">
        <template #default="scope">
          <span class="archive-spec">{{ archiveUnitOf(scope.row) || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="投入单位" width="110">
        <template #default="scope">
          <el-select
            :model-value="scope.row.unit"
            placeholder="请选择"
            size="small"
            filterable
            :disabled="readonly"
            style="width: 100%"
            @update:model-value="(value: string) => handleUnitChange(scope.row, value)"
          >
            <el-option
              v-for="item in unitOptionsFor(scope.row)"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </template>
      </el-table-column>

      <el-table-column label="模数" width="85" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.moduleQty"
            :min="0"
            :precision="4"
            :step="1"
            :controls="false"
            size="small"
            :disabled="readonly"
            @change="markQuantityStale(scope.row)"
          />
        </template>
      </el-table-column>

      <el-table-column label="基数" width="85" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.baseQty"
            :min="0"
            :precision="4"
            :step="1"
            :controls="false"
            size="small"
            :disabled="readonly"
            @change="markQuantityStale(scope.row)"
          />
        </template>
      </el-table-column>

      <!-- 裁切参数：跳距沿用主表既有字段 split（数字+mm），旧值原样保留 -->
      <el-table-column label="跳距(mm)" prop="split" width="95">
        <template #default="scope">
          <el-input-number
            :model-value="splitNumber(scope.row.split)"
            :min="0"
            :precision="0"
            :controls="false"
            size="small"
            :disabled="readonly"
            @update:model-value="(value?: number | null) => handleSplitChange(scope.row, value)"
          />
        </template>
      </el-table-column>

      <el-table-column label="损耗率(%)" width="90" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.lossRate"
            :min="0"
            :max="100"
            :precision="2"
            :step="0.01"
            size="small"
            :controls="false"
            :disabled="readonly"
            @change="markQuantityStale(scope.row)"
          />
        </template>
      </el-table-column>

      <el-table-column label="最低投料" width="90" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.minIssueQty"
            :min="0"
            :precision="4"
            size="small"
            :controls="false"
            :disabled="readonly"
          />
        </template>
      </el-table-column>

      <!-- 跨单位换算依据：只有 BOM 投入单位与档案单位不同才需要；前端不自动算面积/卷长 -->
      <el-table-column label="单位换算" width="210">
        <template #default="scope">
          <div v-if="needsUnitFactor(scope.row)" class="unit-factor-cell">
            <el-input-number
              v-model="scope.row.unitFactor"
              :min="0"
              :precision="8"
              :step="1"
              :controls="false"
              size="small"
              :disabled="readonly"
              placeholder="换算系数"
              class="factor-input"
            />
            <el-input
              v-model="scope.row.unitFactorBasis"
              size="small"
              maxlength="200"
              :disabled="readonly"
              placeholder="换算依据（尺寸/批次实测/供应商资料）"
            />
            <el-input
              v-model="scope.row.unitFactorSource"
              size="small"
              maxlength="100"
              :disabled="readonly"
              placeholder="依据来源（选填）"
            />
          </div>
          <span v-else class="same-unit">同单位，无需换算系数</span>
        </template>
      </el-table-column>

      <!-- 只展示后端保存/回读的参考用量；比例改动未保存时不给旧值冒充当前值 -->
      <el-table-column label="参考用量(已保存)" width="110" align="right">
        <template #default="scope">{{ referenceQuantity(scope.row) }}</template>
      </el-table-column>

      <!-- 下层方案：已选方案显示编码+版本并查看；已建档物料即使未选方案也能进入该物料的候选并新建 -->
      <el-table-column label="下层方案" width="160">
        <template #default="scope">
          <template v-if="scope.row.subBomId">
            <el-tag type="success" size="small">{{ schemeLabel(scope.row.subBomId) }}</el-tag>
            <el-button link type="primary" size="small" @click="emit('open-sub', scope.row)"
              >查看</el-button
            >
          </template>
          <el-button
            v-else-if="canOpenSub(scope.row)"
            link
            type="primary"
            size="small"
            @click="emit('open-sub', scope.row)"
            >制造BOM</el-button
          >
          <span v-else class="no-sub">—</span>
        </template>
      </el-table-column>

      <el-table-column label="备注" min-width="110">
        <template #default="scope">
          <el-input
            v-model="scope.row.remark"
            size="small"
            maxlength="200"
            :disabled="readonly"
            placeholder="选填"
          />
        </template>
      </el-table-column>

      <el-table-column v-if="!readonly" label="操作" width="110" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="handleCopy(scope.row)">复制</el-button>
          <el-button link type="danger" size="small" @click="handleDelete(scope.$index)"
            >删除</el-button
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
/**
 * 制造BOM 投入材料表（dev-20261111-005）。
 * 一种产出、多行投入；编码/名称/档案规格/档案单位/投入单位/模数/基数/跳距/损耗/最低投料/跨单位换算依据。
 * 不做分组、不加供给方式列、不内嵌工艺路线；规格与档案单位只读，不反向改物料档案。
 * 用量一律以保存后后端回读为准：这里不计算、不回写 row.quantity，只标“待保存”。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { EngineeringBomItem } from '@/types/product/bom'
import type { ManufacturingSchemeLabel } from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import { BomItemReviewStatusEnum } from '@/enums/product'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'

interface Props {
  modelValue: EngineeringBomItem[]
  readonly?: boolean
  schemeLabels?: Record<number, ManufacturingSchemeLabel>
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  readonly: false,
  schemeLabels: () => ({}),
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: EngineeringBomItem[]): void
  /** 打开下层：已选方案=查看；已建档未选方案=进入该物料候选并新建 */
  (e: 'open-sub', row: EngineeringBomItem): void
}>()

const rows = computed<EngineeringBomItem[]>(() => props.modelValue || [])

const baseUnitOptions = [
  { value: 'PCS', label: '个(PCS)' },
  { value: 'KG', label: '千克(KG)' },
  { value: 'M', label: '米(M)' },
  { value: 'M²', label: '平方米(M²)' },
  { value: 'L', label: '升(L)' },
  { value: 'SET', label: '套(SET)' },
]

/** 档案单位不在预置列表时也允许回显，避免“旧值看起来丢了”。 */
const unitOptionsFor = (row: EngineeringBomItem) => {
  const current = String(row.unit || '').trim()
  if (!current || baseUnitOptions.some((item) => item.value === current)) return baseUnitOptions
  return [...baseUnitOptions, { value: current, label: current }]
}

let tempItemIdSeq = -1
const nextTempItemId = () => tempItemIdSeq--

const archiveSpecOf = (row: EngineeringBomItem): string =>
  String(row.materialSpecification || row.specification || '')

const archiveUnitOf = (row: EngineeringBomItem): string => String(row.archiveUnit || '')

/** 跨单位：BOM 投入单位与档案单位不同（档案单位未知时交给后端判断）。 */
const needsUnitFactor = (row: EngineeringBomItem): boolean => {
  const archive = archiveUnitOf(row).trim()
  const unit = String(row.unit || '').trim()
  if (!archive || !unit) return false
  return archive.toLowerCase() !== unit.toLowerCase()
}

const schemeLabel = (bomId?: number | null): string => {
  const id = Number(bomId)
  if (!id) return '已选下层方案'
  const label = props.schemeLabels?.[id]
  const version = String(label?.bomVersion || '').trim()
  const text = [String(label?.bomCode || '').trim(), version].filter(Boolean).join(' ')
  return text || '已选下层方案'
}

/** 已选方案可查看；未选方案但已建档物料也可进入候选新建（只读除外）。 */
const canOpenSub = (row: EngineeringBomItem): boolean =>
  !props.readonly && Number(row.materialId) > 0

/** 参考用量只取后端保存/回读值；比例改动后不展示旧值。 */
const referenceQuantity = (row: EngineeringBomItem): string => {
  if (!Number(row.quantity) && row.quantity !== 0) return '—'
  if (row.quantityStale) return '待保存后更新'
  const value = Number(row.quantity)
  return Number.isFinite(value) ? String(value) : '—'
}

const splitNumber = (value?: string | null): number | undefined => {
  if (value == null || value === '') return undefined
  const n = Number.parseFloat(String(value))
  return Number.isFinite(n) ? Math.trunc(n) : undefined
}

const handleSplitChange = (row: EngineeringBomItem, value?: number | null) => {
  if (value == null || !Number.isFinite(Number(value)) || Number(value) <= 0) {
    row.split = ''
    return
  }
  row.split = `${Math.trunc(Number(value))}mm`
}

/** 比例/损耗改动只标“参考用量已过期待保存”，不在前端重算，也不写 row.quantity。 */
const markQuantityStale = (row: EngineeringBomItem) => {
  row.quantityStale = true
}

/** 改投入单位后旧系数不能继续沿用：清空系数与依据，要求按新单位重新填写。 */
const handleUnitChange = (row: EngineeringBomItem, value: string) => {
  const next = String(value || '').trim()
  if (next === String(row.unit || '').trim()) return
  row.unit = next
  row.unitFactor = null
  row.unitFactorBasis = ''
  row.unitFactorSource = ''
}

const createRow = (): EngineeringBomItem => ({
  itemId: nextTempItemId(),
  parentMaterialId: null,
  materialId: 0,
  materialCode: '',
  materialName: '',
  specification: '',
  unit: 'PCS',
  quantity: 0,
  moduleQty: 1,
  baseQty: 1,
  lossRate: 0,
  minIssueQty: 0,
  appliedQty: 0,
  actualIssueQty: 0,
  sortOrder: rows.value.length + 1,
  reviewStatus: BomItemReviewStatusEnum.DRAFT.value,
  removeRequested: false,
  unitFactor: null,
  unitFactorBasis: '',
  unitFactorSource: '',
})

const handleAdd = () => {
  emit('update:modelValue', [...rows.value, createRow()])
}

const handleCopy = (row: EngineeringBomItem) => {
  const copy: EngineeringBomItem = JSON.parse(JSON.stringify(row))
  copy.itemId = nextTempItemId()
  copy.sortOrder = rows.value.length + 1
  copy.reviewStatus = BomItemReviewStatusEnum.DRAFT.value
  copy.rejectReason = undefined
  copy.approvedFingerprint = undefined
  copy.approvedBy = undefined
  copy.approvedTime = undefined
  copy.removeConfirmed = false
  copy.removeRequested = false
  copy.quantityStale = true
  emit('update:modelValue', [...rows.value, copy])
}

const handleDelete = (index: number) => {
  const next = rows.value.filter((_, i) => i !== index)
  emit('update:modelValue', next)
}

const handleSelect = (row: EngineeringBomItem, material: InventoryMaterial) => {
  if (!material) return
  const unitChanged = Number(row.materialId) !== Number(material.materialId || 0)
  row.materialId = material.materialId || 0
  row.materialCode = material.materialCode || ''
  row.materialName = material.materialName || ''
  const spec = material.specification ?? ''
  row.materialSpecification = spec
  row.specification = spec
  row.archiveUnit = material.unit || ''
  row.unit = material.unit || row.unit || 'PCS'
  row.materialType = material.rootType || undefined
  if (unitChanged) {
    row.unitFactor = null
    row.unitFactorBasis = ''
    row.unitFactorSource = ''
  }
}

const handleClear = (row: EngineeringBomItem) => {
  row.materialId = 0
  row.materialCode = ''
  row.materialName = ''
  row.specification = ''
  row.materialSpecification = undefined
  row.archiveUnit = undefined
  row.unit = 'PCS'
  row.materialType = undefined
  row.unitFactor = null
  row.unitFactorBasis = ''
  row.unitFactorSource = ''
}

/** 快捷建档：建成后回填当前行（与既有 BOM 编辑器同一入口） */
const materialFormVisible = ref(false)
const materialPreset = ref({ materialName: '', specification: '', unit: 'PCS' })
let creatingRow: EngineeringBomItem | null = null

const handleCreate = (row: EngineeringBomItem, keyword: string) => {
  creatingRow = row
  materialPreset.value = {
    materialName: keyword,
    specification: row.specification || '',
    unit: row.unit || 'PCS',
  }
  materialFormVisible.value = true
}

const handleMaterialCreated = (material: InventoryMaterial) => {
  if (!material?.materialId) {
    ElMessage.warning('物料已建档，请按名称搜索后选择')
    return
  }
  if (creatingRow) handleSelect(creatingRow, material)
  creatingRow = null
}

const isPositive = (value: unknown): boolean => {
  const n = Number(value)
  return Number.isFinite(n) && n > 0
}

const decimalPlaces = (value: unknown): number => {
  const text = String(Number(value))
  const dot = text.indexOf('.')
  return dot < 0 ? 0 : text.length - dot - 1
}

/**
 * 校验；返回第一条错误消息，空串表示通过。后端仍是唯一权威。
 * input 传入当前草稿行，避免同一 tick 内父组件刚改 items、props 尚未刷新时校验到旧数组。
 */
const validate = (input?: EngineeringBomItem[]): string => {
  const list = input ?? rows.value
  if (!list.length) return '制造BOM至少需要一行投入材料'
  for (let i = 0; i < list.length; i++) {
    const row = list[i]
    const prefix = `第${i + 1}行`
    if (!row.materialId || Number(row.materialId) <= 0) return `${prefix}请选择投入物料`
    if (!String(row.materialName || '').trim()) return `${prefix}物料名称不能为空`
    if (!String(row.unit || '').trim()) return `${prefix}请选择投入单位`
    if (!isPositive(row.moduleQty)) return `${prefix}模数必须大于 0`
    if (!isPositive(row.baseQty)) return `${prefix}基数必须大于 0`
    if (!/^\d+(\.\d{1,4})?$/.test(String(Number(row.moduleQty)))) {
      return `${prefix}模数最多 4 位小数`
    }
    if (!/^\d+(\.\d{1,4})?$/.test(String(Number(row.baseQty)))) {
      return `${prefix}基数最多 4 位小数`
    }
    // 与后端decimal(5,2)一致，仅校验输入形状，不在前端计算耗用。
    const loss = Number(row.lossRate ?? 0)
    if (!Number.isFinite(loss) || loss < 0 || loss > 100 || decimalPlaces(loss) > 2) {
      return `${prefix}损耗率须在0~100之间，最多2位小数`
    }
    // 跨单位必须填写换算系数与依据；档案单位未知时由后端校验
    if (needsUnitFactor(row)) {
      const factor = Number(row.unitFactor)
      if (!Number.isFinite(factor) || factor <= 0) {
        return `${prefix}投入单位与档案单位不同，请填写换算系数`
      }
      if (decimalPlaces(factor) > 8) return `${prefix}换算系数最多 8 位小数`
      if (!String(row.unitFactorBasis || '').trim()) {
        return `${prefix}跨单位换算缺少依据，请填写换算依据`
      }
      if (String(row.unitFactorBasis || '').length > 200) return `${prefix}换算依据不能超过200字`
      if (String(row.unitFactorSource || '').length > 100) return `${prefix}换算来源不能超过100字`
    } else if (row.unitFactor != null && Number(row.unitFactor) <= 0) {
      return `${prefix}换算系数必须大于 0`
    }
  }
  return ''
}

defineExpose({
  validate,
  addRow: handleAdd,
  needsUnitFactor,
  referenceQuantity,
  schemeLabel,
  canOpenSub,
  markQuantityStale,
  handleUnitChange,
})
</script>

<style scoped>
.manufacturing-item-table {
  width: 100%;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  padding: 8px 12px;
  background: linear-gradient(135deg, #f5f7fa 0%, #f0f2f5 100%);
  border-radius: 6px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.header-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.archive-spec {
  color: var(--el-text-color-regular, #606266);
  font-size: 12px;
}

.same-unit {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.unit-factor-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.factor-input {
  width: 100%;
}

.no-sub {
  color: var(--el-text-color-secondary, #909399);
}

.manufacturing-item-grid :deep(.el-table__cell) {
  padding: 2px 4px;
}

.manufacturing-item-grid :deep(.el-input-number) {
  width: 100%;
}
</style>
