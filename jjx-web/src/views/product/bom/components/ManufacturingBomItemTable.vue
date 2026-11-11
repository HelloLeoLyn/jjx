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

      <el-table-column label="投入物料" min-width="200">
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
      <el-table-column label="档案规格" width="150" show-overflow-tooltip>
        <template #default="scope">
          <span class="archive-spec">{{ archiveSpecOf(scope.row) || '—' }}</span>
        </template>
      </el-table-column>

      <el-table-column label="单位" width="100">
        <template #default="scope">
          <el-select
            v-model="scope.row.unit"
            placeholder="请选择"
            size="small"
            filterable
            :disabled="readonly"
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

      <el-table-column label="模数" width="90" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.moduleQty"
            :min="0"
            :precision="4"
            :step="1"
            :controls="false"
            size="small"
            :disabled="readonly"
            @change="handleRatioChange(scope.row)"
          />
        </template>
      </el-table-column>

      <el-table-column label="基数" width="90" align="center">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.baseQty"
            :min="0"
            :precision="4"
            :step="1"
            :controls="false"
            size="small"
            :disabled="readonly"
            @change="handleRatioChange(scope.row)"
          />
        </template>
      </el-table-column>

      <!-- 裁切参数：跳距沿用主表既有字段 split（数字+mm），旧值原样保留 -->
      <el-table-column label="跳距(mm)" prop="split" width="100">
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

      <el-table-column label="损耗率(%)" width="100" align="center">
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

      <!-- 仅展示：权威耗用由后端按 module/base/loss 计算，前端不另立算法 -->
      <el-table-column label="参考用量" width="90" align="right">
        <template #default="scope">{{ displayQuantity(scope.row) }}</template>
      </el-table-column>

      <el-table-column label="下层方案" width="150">
        <template #default="scope">
          <template v-if="scope.row.subBomId">
            <el-tag type="success" size="small">方案 #{{ scope.row.subBomId }}</el-tag>
            <el-button link type="primary" size="small" @click="emit('view-sub', scope.row)"
              >查看下层</el-button
            >
          </template>
          <span v-else class="no-sub">—</span>
        </template>
      </el-table-column>

      <el-table-column label="备注" min-width="120">
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
 * 一种产出、多行投入；编码/名称/规格/单位/模数/基数/跳距/损耗/最低投料。
 * 不做分组、不加供给方式列、不内嵌工艺路线；规格档案只读，不反向改物料档案。
 */
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { EngineeringBomItem } from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import { BomItemReviewStatusEnum } from '@/enums/product'
import { calculateBomQuantity } from '@/utils/bomQuantity'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'

interface Props {
  modelValue: EngineeringBomItem[]
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  readonly: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: EngineeringBomItem[]): void
  /** 下层查看：只对已显式挂 subBomId 的行开放 */
  (e: 'view-sub', row: EngineeringBomItem): void
}>()

const rows = computed<EngineeringBomItem[]>(() => props.modelValue || [])

const unitOptions = [
  { value: 'PCS', label: '个(PCS)' },
  { value: 'KG', label: '千克(KG)' },
  { value: 'M', label: '米(M)' },
  { value: 'M²', label: '平方米(M²)' },
  { value: 'L', label: '升(L)' },
  { value: 'SET', label: '套(SET)' },
]

let tempItemIdSeq = -1
const nextTempItemId = () => tempItemIdSeq--

const archiveSpecOf = (row: EngineeringBomItem): string =>
  String(row.materialSpecification || row.specification || '')

/** 只做展示：用量 = 基数 ÷ 模数；落库与耗用以模块/基数及后端规则为准。 */
const displayQuantity = (row: EngineeringBomItem): string => {
  const qty = calculateBomQuantity(row.baseQty, row.moduleQty)
  if (qty === undefined) return '—'
  return String(Number(qty.toFixed(4)))
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

const handleRatioChange = (row: EngineeringBomItem) => {
  const quantity = calculateBomQuantity(row.baseQty, row.moduleQty)
  if (quantity !== undefined) row.quantity = quantity
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
  emit('update:modelValue', [...rows.value, copy])
}

const handleDelete = (index: number) => {
  const next = rows.value.filter((_, i) => i !== index)
  emit('update:modelValue', next)
}

const handleSelect = (row: EngineeringBomItem, material: InventoryMaterial) => {
  if (!material) return
  row.materialId = material.materialId || 0
  row.materialCode = material.materialCode || ''
  row.materialName = material.materialName || ''
  const spec = material.specification ?? ''
  row.materialSpecification = spec
  row.specification = spec
  row.unit = material.unit || row.unit || 'PCS'
  row.materialType = material.rootType || undefined
}

const handleClear = (row: EngineeringBomItem) => {
  row.materialId = 0
  row.materialCode = ''
  row.materialName = ''
  row.specification = ''
  row.materialSpecification = undefined
  row.unit = 'PCS'
  row.materialType = undefined
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

/** 校验；返回第一条错误消息，空串表示通过。后端仍是唯一权威。 */
const validate = (): string => {
  if (!rows.value.length) return '制造BOM至少需要一行投入材料'
  for (let i = 0; i < rows.value.length; i++) {
    const row = rows.value[i]
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
    const loss = Number(row.lossRate ?? 0)
    if (!Number.isFinite(loss) || loss < 0 || loss > 100) return `${prefix}损耗率须在 0~100 之间`
  }
  return ''
}

defineExpose({ validate, addRow: handleAdd })
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
