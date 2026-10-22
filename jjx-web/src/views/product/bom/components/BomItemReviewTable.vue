<template>
  <el-card class="bom-review-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>BOM明细审核</span>
        <span class="review-hint">
          未建档材料可搜索关联已有物料或新建；逐行通过、待移除行确认后，整单才可批准
        </span>
      </div>
    </template>
    <div v-if="canOperate" class="batch-toolbar">
      <el-button
        v-hasPermi="['engineering:bom:approve']"
        type="success"
        :loading="batchBusy"
        :disabled="!selectedIds.length || busyItemId != null"
        @click="emit('batch-approve', selectedIds)"
        >批量通过 {{ selectedIds.length }} 行</el-button
      >
    </div>
    <el-alert v-if="batchFailures.length" type="warning" :closable="false" title="以下明细未通过">
      <div v-for="failure in batchFailures" :key="failure.itemId">
        itemId {{ failure.itemId }}：{{ failure.reason }}
      </div>
    </el-alert>
    <el-table
      ref="tableRef"
      :data="items"
      row-key="itemId"
      border
      style="width: 100%"
      height="360"
      :row-class-name="rowClass"
      @selection-change="selectedRows = $event"
    >
      <el-table-column v-if="canOperate" type="selection" width="45" :selectable="canSelect" reserve-selection />
      <el-table-column label="组合 / 项目" min-width="240">
        <template #default="{ $index }">
          <div>{{ groups[$index]?.label }}</div>
          <el-button v-if="canOperate && groups[$index]?.span && groups[$index]?.size > 1"
            link type="primary" :disabled="!groupSelectable(groups[$index].groupKey)"
            @click="selectGroup(groups[$index].groupKey)">选择本组待审</el-button>
        </template>
      </el-table-column>
      <el-table-column label="行状态" width="110" align="center">
        <template #default="{ row }">
          <div class="status-cell">
            <el-tag :type="BomItemReviewStatusEnum.getTagProps(rowStatus(row)).type" size="small">
              {{ BomItemReviewStatusEnum.getLabel(rowStatus(row)) }}
            </el-tag>
            <el-tooltip v-if="row.rejectReason" :content="row.rejectReason">
              <el-icon class="reject-icon"><WarningFilled /></el-icon>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="材料名称（匹配已有 / 新建）" min-width="250">
        <template #default="{ row }">
          <div class="material-cell">
            <BomMaterialSelector
              v-if="canOperate && !batchBusy && busyItemId == null && rowStatus(row) !== BomItemReviewStatusEnum.PENDING_REMOVE.value"
              :material-id="row.materialId"
              :material-name="row.materialName"
              @select="(material) => emit('link', row, material)"
              @create="(keyword) => emit('create', row, keyword)"
            />
            <span v-else>{{ row.materialName || '—' }}</span>
            <el-tag v-if="hasMaterial(row)" type="success" size="small">
              {{ row.materialCode || '已关联' }}
            </el-tag>
            <el-tag v-else type="warning" size="small">未关联</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="规格型号" prop="specification" width="130" show-overflow-tooltip />
      <!-- dev-20261022-021：原值对比列（取最近一次保存流水的改动前值，没有记录显示 —） -->
      <el-table-column label="原材料名称" width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'old-value-changed': hasOriginal(row, 'materialName') }">
            {{ originalOf(row, 'materialName') }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="原规格型号" width="130" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'old-value-changed': hasOriginal(row, 'specification') }">
            {{ originalOf(row, 'specification') }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="单位" prop="unit" width="70" align="center" />
      <el-table-column label="基数" prop="baseQty" width="80" align="right" />
      <el-table-column label="模数" prop="moduleQty" width="80" align="right" />
      <el-table-column label="损耗率(%)" prop="lossRate" width="100" align="right" />
      <el-table-column label="用量" width="85" align="right">
        <template #default="{ row }">{{ formatQty(unitQuantity(row)) }}</template>
      </el-table-column>
      <el-table-column label="应用料" width="95" align="right">
        <template #default="{ row }">{{ formatQty(appliedQuantity(row)) }}</template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" width="140" show-overflow-tooltip />
      <el-table-column v-if="canOperate" label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <template v-if="rowStatus(row) === BomItemReviewStatusEnum.PENDING_REMOVE.value">
            <el-tag v-if="row.removeConfirmed" type="success" size="small">已确认移除</el-tag>
            <el-button
              v-else
              link
              type="danger"
              :disabled="batchBusy || busyItemId != null"
              :loading="busyItemId === row.itemId"
              @click="emit('review', row, 'CONFIRM_REMOVE')"
              >确认移除</el-button
            >
          </template>
          <template v-else>
            <el-button
              link
              type="success"
              :disabled="batchBusy || !hasMaterial(row) || busyItemId != null"
              @click="emit('review', row, 'APPROVE')"
              >通过</el-button
            >
            <el-button
              link
              type="danger"
              :disabled="batchBusy || busyItemId != null"
              :loading="busyItemId === row.itemId"
              @click="emit('review', row, 'REJECT')"
              >驳回</el-button
            >
          </template>
        </template>
      </el-table-column>
    </el-table>
    <div v-if="items.length && blockingRows.length" class="blocking-hint">
      仍不满足批准条件：{{ blockingRows.length }} 行待处理（未审核通过、未关联物料或待移除未确认）
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, ref, watch, nextTick } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import type { EngineeringBomItem, BomItemBatchReviewResult, BomOriginalValues } from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import { BomItemReviewStatusEnum } from '@/enums/product'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'
import { bomGroupRows } from '@/utils/bomGroups'
import { calculateBomQuantity } from '@/utils/bomQuantity'
import { hasPermi } from '@/directives'

const props = withDefaults(
  defineProps<{
    items: EngineeringBomItem[]
    bomId?: number
    canReview?: boolean
    busyItemId?: number
    batchBusy?: boolean
    batchFailures?: BomItemBatchReviewResult['failed']
    /** 最近一次成功保存的改动前值，key为稳定itemId。 */
    originalValues?: BomOriginalValues
  }>(),
  {
    items: () => [],
    bomId: undefined,
    canReview: true,
    busyItemId: undefined,
    batchBusy: false,
    batchFailures: () => [],
    originalValues: () => ({}),
  }
)

/** 取该行的原值；没有流水记录时显示 — */
const originalOf = (row: EngineeringBomItem, field: 'materialName' | 'specification'): string => {
  const key = row.itemId == null ? '' : String(row.itemId)
  const entry = key ? props.originalValues?.[key] : undefined
  const value = entry?.[field]
  return value === undefined || value === null || value === '' ? '—' : value
}

/** 原值与现值不同才高亮（新增行/无记录不高亮） */
const hasOriginal = (row: EngineeringBomItem, field: 'materialName' | 'specification'): boolean => {
  const key = row.itemId == null ? '' : String(row.itemId)
  const entry = key ? props.originalValues?.[key] : undefined
  if (!entry || entry[field] === undefined) return false
  return (entry[field] || '') !== ((row as any)[field] || '')
}

const emit = defineEmits<{
  (e: 'batch-approve', itemIds: number[]): void
  (e: 'link', row: EngineeringBomItem, material: InventoryMaterial): void
  (e: 'create', row: EngineeringBomItem, keyword: string): void
  (e: 'review', row: EngineeringBomItem, action: 'APPROVE' | 'REJECT' | 'CONFIRM_REMOVE'): void
}>()

const rowStatus = (row: EngineeringBomItem): number =>
  row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value
const hasMaterial = (row: EngineeringBomItem) => row.materialId != null && Number(row.materialId) > 0

const selectedRows = ref<EngineeringBomItem[]>([])
const tableRef = ref()
const groups = computed(() => bomGroupRows(props.items, true))
const canOperate = computed(() => props.canReview && hasPermi(['engineering:bom:approve']))
const eligible = (row: EngineeringBomItem) =>
  canOperate.value && row.itemId != null && row.itemId > 0
  && (props.bomId == null || row.bomId == null || row.bomId === props.bomId)
  && rowStatus(row) === BomItemReviewStatusEnum.PENDING.value
const canSelect = (row: EngineeringBomItem) =>
  !props.batchBusy && props.busyItemId == null && eligible(row)
const selectedIds = computed(() => {
  const ids = new Set(selectedRows.value.map(row => row.itemId))
  return props.items.filter(row => ids.has(row.itemId) && eligible(row)).map(row => row.itemId!)
})
const groupSelectable = (key: string) => groups.value.some(group => group.groupKey === key && canSelect(group.item))
function selectGroup(key: string) {
  for (const group of groups.value) {
    if (group.groupKey === key && canSelect(group.item)) tableRef.value?.toggleRowSelection(group.item, true)
  }
}
watch(() => props.bomId, () => { selectedRows.value = []; tableRef.value?.clearSelection() })
watch([() => props.items, canOperate], async () => {
  const selected = new Set(selectedRows.value.map(row => row.itemId))
  selectedRows.value = props.items.filter(row => selected.has(row.itemId) && eligible(row))
  await nextTick()
  for (const row of props.items) {
    if (selected.has(row.itemId) && !eligible(row)) tableRef.value?.toggleRowSelection(row, false)
  }
})
const unitQuantity = (row: EngineeringBomItem) => calculateBomQuantity(row.baseQty, row.moduleQty) ?? row.quantity
const appliedQuantity = (row: EngineeringBomItem) => {
  const quantity = Number(unitQuantity(row))
  const loss = Number(row.lossRate || 0)
  return Number.isFinite(quantity) && Number.isFinite(loss) ? quantity * (1 + loss / 100) : undefined
}

const blockingRows = computed(() =>
  (props.items || []).filter((row) => {
    const status = rowStatus(row)
    if (status === BomItemReviewStatusEnum.PENDING_REMOVE.value) {
      return !row.removeConfirmed
    }
    return status !== BomItemReviewStatusEnum.APPROVED.value || !hasMaterial(row)
  })
)

const rowClass = ({ row }: { row: EngineeringBomItem }) =>
  rowStatus(row) === BomItemReviewStatusEnum.PENDING_REMOVE.value ? 'row-pending-remove' : ''

const formatQty = (value: number | string | undefined) => {
  if (value === undefined || value === null || value === '') return '-'
  const num = typeof value === 'string' ? parseFloat(value) : value
  return Number.isNaN(num) ? '-' : String(Number(num.toFixed(4)))
}
</script>

<style scoped>
.old-value-changed {
  color: var(--el-color-warning);
  font-weight: 600;
}
.batch-toolbar {
  margin-bottom: 10px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.review-hint {
  font-size: 12px;
  color: #909399;
}
.status-cell {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.reject-icon {
  color: var(--el-color-danger);
  cursor: help;
}
.material-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}
.material-cell > :first-child {
  flex: 1;
  min-width: 0;
}
.blocking-hint {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-danger);
}
:deep(.row-pending-remove > td) {
  background-color: #f4f4f5 !important;
}
</style>
