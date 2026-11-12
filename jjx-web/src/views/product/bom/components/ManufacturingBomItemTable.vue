<template>
  <div class="manufacturing-item-table">
    <div class="editor-header">
      <div class="header-left">
        <el-button v-if="!readonly" type="primary" :icon="Plus" size="small" @click="handleAdd">
          添加投入材料
        </el-button>
        <span class="header-hint">
          跳距组与模数/基数二选一；用量与总需求按原始参数只读试算
        </span>
      </div>
      <div class="header-right">
        <el-tag type="info" size="small">共 {{ rows.length }} 行投入</el-tag>
      </div>
    </div>

    <BomEntryTools :rows="rows" :readonly="readonly" :output-unit="outputUnit" :output-length-mm="outputLengthMm" @change="emit('update:modelValue', [...rows])" v-slot="{ resultFor, showConversions, showAdvanced, loading }">
    <el-table :data="rows" border size="small" row-key="itemId" :row-class-name="({ row }: { row: EngineeringBomItem }) => unitMismatch(row) ? 'bom-row-error' : ''" class="manufacturing-item-grid">
      <el-table-column label="序号" width="50" align="center">
        <template #default="scope">{{ scope.$index + 1 }}</template>
      </el-table-column>

      <el-table-column label="投入物料" min-width="190">
        <template #default="scope">
          <div class="material-cell">
          <BomMaterialSelector
            v-if="!readonly"
            :material-id="scope.row.materialId"
            :material-name="scope.row.materialName"
            confirm-selection
            @select="(material: InventoryMaterial) => handleSelect(scope.row, material)"
            @clear="handleClear(scope.row)"
            @create="(keyword: string) => handleCreate(scope.row, keyword)"
          />
          <span v-else>{{ scope.row.materialName }}</span>
          <small class="muted">{{ scope.row.materialCode }}</small>
          </div>
        </template>
      </el-table-column>


      <!-- 档案规格只读：从物料档案带入，不在这里反向改物料档案 -->
      <el-table-column label="档案规格" width="140" show-overflow-tooltip>
        <template #default="scope">
          <span class="archive-spec">{{ archiveSpecOf(scope.row) || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="单位" width="85" align="center">
        <template #default="scope">{{ scope.row.unit || '—' }}</template>
      </el-table-column>

      <el-table-column label="模数" width="85" align="center">
        <template #default="scope">
          <el-input-number
            v-if="!hasPitchValues(scope.row)"
            v-model="scope.row.moduleQty"
            :min="0"
            :precision="4"
            :step="1"
            :controls="false"
            size="small"
            :disabled="readonly"
            @change="markQuantityStale(scope.row)"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="基数" width="105" align="center">
        <template #default="scope">
          <el-input-number
            v-if="!hasPitchValues(scope.row)"
            v-model="scope.row.baseQty"
            :min="0"
            :precision="4"
            :step="1"
            :controls="false"
            size="small"
            :disabled="readonly"
            @change="markQuantityStale(scope.row)"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="跳距(mm)" width="100" align="center">
        <template #default="scope">
          <el-input-number v-if="!hasRatioValues(scope.row)" v-model="scope.row.pitchMm"
            :min="0" :precision="2" :controls="false" :disabled="readonly" size="small"
             @change="markQuantityStale(scope.row)" />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>

      <el-table-column label="每跳片数" width="100" align="center">
        <template #default="scope">
          <el-input-number v-if="!hasRatioValues(scope.row)" v-model="scope.row.pcsPerPitch"
            :min="1" :precision="0" :controls="false" :disabled="readonly" size="small"
            placeholder="1" @change="markQuantityStale(scope.row)" />
          <span v-else class="muted">—</span>
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

      <el-table-column label="每件用量" width="100" align="right">
        <template #default="scope"><el-tooltip :content="resultFor(scope.row)?.message || '每件净用量，不含损耗'"><span>{{ resultFor(scope.row)?.valid ? resultFor(scope.row)?.unitConsumption : loading ? '试算中' : '—' }}</span></el-tooltip></template>
      </el-table-column>
      <el-table-column label="总需求" width="110" align="right">
        <template #default="scope"><el-tooltip :content="resultFor(scope.row)?.message || '按产出数量计入损耗'"><strong>{{ resultFor(scope.row)?.valid ? resultFor(scope.row)?.totalConsumption : loading ? '试算中' : '—' }}</strong></el-tooltip></template>
      </el-table-column>
      <el-table-column v-if="showConversions" label="折算米" width="110" align="right">
        <template #default="scope">{{ resultFor(scope.row)?.metres ?? '—' }}</template>
      </el-table-column>
      <el-table-column v-if="showConversions" label="预计卷数（参考）" width="130" align="right">
        <template #default="scope"><el-tooltip :content="`按${resultFor(scope.row)?.referenceRollLengthM ?? '未填写'}m参考；不代表实际领卷`"><span>{{ resultFor(scope.row)?.referenceRollCount ?? '—' }}</span></el-tooltip></template>
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

      <el-table-column v-if="showAdvanced" label="备注" min-width="110">
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
    </BomEntryTools>

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
import BomEntryTools from '@/components/bom/BomEntryTools.vue'
import { hasPitchValues, hasRatioValues, unitMismatch, consumptionInputError } from '@/components/bom/consumptionMode'
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
  outputUnit?: string
  outputLengthMm?: number | null
  schemeLabels?: Record<number, ManufacturingSchemeLabel>
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  readonly: false,
  outputUnit: '',
  outputLengthMm: null,
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
  moduleQty: undefined,
  baseQty: undefined,
  pitchMm: null,
  pcsPerPitch: 1,
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
  if (!material || props.readonly) return
  const identityChanged = Number(row.materialId) !== Number(material.materialId || 0)
  const unitChanged = Number(row.materialId) !== Number(material.materialId || 0) || String(row.unit).toUpperCase() !== String(material.unit || row.unit || 'PCS').toUpperCase()
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
    row.pitchMm = null; row.pcsPerPitch = 1; row.baseQty = undefined; row.moduleQty = undefined; row.quantity = 0; row.quantityStale = true
    row.minIssueQty = 0
    if (identityChanged) { row.subBomId = null; row.sourceType = undefined; row.split = '' }
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
    const inputError = consumptionInputError(row)
    if (inputError) return `${prefix}${inputError}`
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
.manufacturing-item-grid :deep(tr.bom-row-error > td) { background:var(--el-color-danger-light-9);color:var(--el-color-danger); }
.manufacturing-item-grid :deep(.el-input-number) { width: 100%; }
.material-cell { display:flex;flex-direction:column;gap:4px; }
.muted {font-size:11px;color:var(--el-text-color-secondary)}
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
