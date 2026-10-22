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
    <div class="batch-toolbar">
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
      :data="items"
      border
      style="width: 100%"
      height="360"
      :row-class-name="rowClass"
      @selection-change="selectedRows = $event"
    >
      <el-table-column type="selection" width="45" :selectable="canSelect" />
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
              :material-id="row.materialId"
              :material-name="row.materialName"
              @select="(material) => emit('link', row, material)"
              @create="(keyword) => emit('create', row, keyword)"
            />
            <el-tag v-if="row.materialId" type="success" size="small">
              {{ row.materialCode || '已关联' }}
            </el-tag>
            <el-tag v-else type="warning" size="small">未关联</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="规格型号" prop="specification" width="130" show-overflow-tooltip />
      <el-table-column label="单位" prop="unit" width="70" align="center" />
      <el-table-column label="用量" width="85" align="right">
        <template #default="{ row }">{{ formatQty(row.quantity) }}</template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" width="140" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="{ row }">
          <template v-if="rowStatus(row) === BomItemReviewStatusEnum.PENDING_REMOVE.value">
            <el-tag v-if="row.removeConfirmed" type="success" size="small">已确认移除</el-tag>
            <el-button
              v-else
              link
              type="danger"
              :disabled="batchBusy"
              :loading="busyItemId === row.itemId"
              @click="emit('review', row, 'CONFIRM_REMOVE')"
              >确认移除</el-button
            >
          </template>
          <template v-else>
            <el-button
              link
              type="success"
              :disabled="batchBusy || !row.materialId || busyItemId === row.itemId"
              @click="emit('review', row, 'APPROVE')"
              >通过</el-button
            >
            <el-button
              link
              type="danger"
              :disabled="batchBusy"
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
import { computed, ref } from 'vue'
import { WarningFilled } from '@element-plus/icons-vue'
import type { EngineeringBomItem, BomItemBatchReviewResult } from '@/types/product/bom'
import type { InventoryMaterial } from '@/types/inventory/material'
import { BomItemReviewStatusEnum } from '@/enums/product'
import BomMaterialSelector from '@/components/Selector/BomMaterialSelector.vue'

const props = withDefaults(
  defineProps<{
    items: EngineeringBomItem[]
    busyItemId?: number
    batchBusy?: boolean
    batchFailures?: BomItemBatchReviewResult['failed']
  }>(),
  { items: () => [], busyItemId: undefined, batchBusy: false, batchFailures: () => [] }
)

const emit = defineEmits<{
  (e: 'batch-approve', itemIds: number[]): void
  (e: 'link', row: EngineeringBomItem, material: InventoryMaterial): void
  (e: 'create', row: EngineeringBomItem, keyword: string): void
  (e: 'review', row: EngineeringBomItem, action: 'APPROVE' | 'REJECT' | 'CONFIRM_REMOVE'): void
}>()

const rowStatus = (row: EngineeringBomItem): number =>
  row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value

const selectedRows = ref<EngineeringBomItem[]>([])
const canSelect = (row: EngineeringBomItem) =>
  !props.batchBusy && row.itemId != null && rowStatus(row) === BomItemReviewStatusEnum.PENDING.value
const selectedIds = computed(() =>
  selectedRows.value
    .filter((row) => row.itemId != null && rowStatus(row) === BomItemReviewStatusEnum.PENDING.value)
    .map((row) => row.itemId!)
)

const blockingRows = computed(() =>
  (props.items || []).filter((row) => {
    const status = rowStatus(row)
    if (status === BomItemReviewStatusEnum.PENDING_REMOVE.value) {
      return !row.removeConfirmed
    }
    return status !== BomItemReviewStatusEnum.APPROVED.value || !row.materialId
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
