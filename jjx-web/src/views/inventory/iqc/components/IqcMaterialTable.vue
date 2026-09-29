<template>
  <el-table
    :data="rows"
    border
    class="material-table"
    :row-class-name="rowClass"
    @selection-change="(v: any[]) => emit('selection-change', v)"
  >
    <el-table-column type="selection" width="46" fixed="left" :selectable="canEdit" />
    <el-table-column prop="materialCode" label="材料编码" min-width="125" />
    <el-table-column prop="materialName" label="材料名称" min-width="150" />
    <el-table-column label="检验批号" min-width="150">
      <template #default="{ row }">
        <div>{{ row.qualityLotNo || '-' }}</div>
        <span v-if="row.parentQualityLotNo" class="muted">原批：{{ row.parentQualityLotNo }}</span>
      </template>
    </el-table-column>
    <el-table-column label="数量" width="105"
      ><template #default="{ row }"
        >{{ row.isReinspection ? '本批复检' : '整批收货' }}
        {{ row.isReinspection ? (row.reinspectionQuantity ?? 0) : row.quantity }}</template
      ></el-table-column
    >
    <el-table-column label="判定数量" width="105"
      ><template #default="{ row }"
        >{{ row.isReinspection ? '本批合格' : '整批合格' }}
        {{ row.qualifiedQuantity ?? 0 }}</template
      ></el-table-column
    >
    <el-table-column label="不良数量" width="105"
      ><template #default="{ row }"
        >{{ row.isReinspection ? '本批不良' : '整批不良' }}
        {{ row.rejectedQuantity ?? 0 }}</template
      ></el-table-column
    >
    <el-table-column label="数量口径" min-width="290"
      ><template #default="{ row }">
        <div v-if="row.isReinspection" class="quantity-context">
          <div>
            原批 {{ row.parentQualityLotNo || '-' }} ↔ 复检批
            {{ row.qualityLotNo || row.batchNo || '-' }}
          </div>
          <div class="quantity-context__emphasis">
            本批复检 {{ row.reinspectionQuantity ?? 0 }} 件（该批只针对
            {{ row.reinspectionQuantity ?? 0 }} 件）
          </div>
        </div>
        <div v-else class="quantity-context">
          <div>已处置 {{ row.trace?.disposedQuantity ?? disposedQuantity(row) }} 件</div>
          <div>
            剩余可处置
            {{ row.trace?.remainingDispositionQuantity ?? row.remainingDispositionQuantity ?? 0 }}
            件
          </div>
          <el-tag
            v-if="needsDisposition(row)"
            type="warning"
            size="small"
            effect="plain"
            class="pending-tag"
            >待处置</el-tag
          >
        </div>
      </template></el-table-column
    >
    <el-table-column label="检验结论" width="100"
      ><template #default="{ row }"
        ><el-tag
          v-if="row.inspectionResult"
          :type="InboundInspectionResultEnum.getTagProps(row.inspectionResult).type"
          >{{ InboundInspectionResultEnum.getLabel(row.inspectionResult) }}</el-tag
        ><span v-else>未检</span></template
      ></el-table-column
    >
    <el-table-column label="不合格原因" min-width="220" show-overflow-tooltip
      ><template #default="{ row }"
        ><span v-if="row.inspectionResult === InboundInspectionResultEnum.FAIL.value">{{
          deriveIqcReasonText(row) || '-'
        }}</span
        ><span v-else>-</span></template
      ></el-table-column
    >
    <el-table-column label="检测项目" width="125"
      ><template #default="{ row }"
        ><el-button link type="primary" @click="emit('edit', row)"
          >检测项目{{ progress(row) }}/{{ row.inspectionItems.length }}</el-button
        >
      </template></el-table-column
    >
    <el-table-column label="审核状态" width="105"
      ><template #default="{ row }"
        ><el-tag
          v-if="row.reviewStatus"
          :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus).type"
          >{{ QualityReviewStatusEnum.getLabel(row.reviewStatus) }}</el-tag
        ><el-tag v-else type="info">未检</el-tag></template
      ></el-table-column
    >
    <TableActionColumn
      :actions="materialActions"
      :min-width="170"
      :min-visible="3"
      display="text"
      @action="handleMaterialAction"
    >
    </TableActionColumn>
  </el-table>
</template>

<script setup lang="ts">
/**
 * 来料检验材料表（dev-20260924-024 刀1：从 iqc/index.vue 抽出的展示型子组件）
 * 只负责展示与事件上抛；数量/判定/原因均为只读派生结果（013 口径）。
 * 操作栏复用统一组件 TableActionColumn（dev-20260928-038 口径：IQC 操作栏统一）。
 * 根节点就是 el-table，父页面针对 .material-table 的 scoped 样式仍然生效。
 *
 * dev-20260929-007：一行材料 = 它的数量、结论、原因、检验批号 + 全部动作。
 *   原「待处理明细」独立表已下线，处置入口回到本表的「不良处置」（判据唯一出处 iqcRowRules.iqcNeedsDisposition）；
 *   结清构成看「处置历史」，跨批次待处置看 /inventory/iqc-quarantine。
 */
import { InspectionResultEnum as InboundInspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { QualityReviewStatus, QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'
import { deriveIqcReasonText, iqcNeedsDisposition } from '../iqcRowRules'

const { canEdit, canJudge, canDispose, isCompleted } = defineProps<{
  rows: any[]
  canEdit: (row: any) => boolean
  rowClass: (ctx: { row: any }) => string
  progress: (row: any) => number
  canJudge: boolean
  canDispose: boolean
  isCompleted: boolean
}>()

const emit = defineEmits<{
  (e: 'selection-change', rows: any[]): void
  (e: 'edit', row: any): void
  (e: 'review'): void
  (e: 'print', row: any): void
  (e: 'history', row: any): void
  (e: 'disposition-history', row: any): void
  (e: 'dispose', row: any): void
}>()

/** 该行是否待处置（判据唯一出处：iqcRowRules.iqcNeedsDisposition） */
function needsDisposition(row: any) {
  return iqcNeedsDisposition(row, isCompleted)
}

function disposedQuantity(row: any) {
  if (row.trace?.disposedQuantity != null) return Number(row.trace.disposedQuantity)
  return Math.max(
    0,
    Number(row.rejectedQuantity || 0) - Number(row.remainingDispositionQuantity || 0)
  )
}

/**
 * 待办链按原优先级互斥取唯一一项：能录入 → 录入，否则待审 → 审核。
 *
 * dev-20260929-007：处置（不良处置）与只读入口（处置历史/打印/质量历史）不进待办互斥 ——
 * 它们按自身条件出现：处置看「已审定不良 + 剩余可处置量 > 0」，只读入口看有无检验批。
 * 打印/历史属于只读附属入口，order 靠后，收进统一组件的「更多」。
 */
function pendingActionKey(row: any): 'edit' | 'review' | null {
  if (canEdit(row)) return 'edit'
  if (canJudge && row.reviewStatus === QualityReviewStatus.PENDING) return 'review'
  return null
}

const materialActions: TableAction<any>[] = [
  {
    key: 'edit',
    label: '检验录入',
    type: 'primary',
    order: 1,
    visible: ({ row }) => pendingActionKey(row) === 'edit',
  },
  {
    key: 'review',
    label: '审核',
    type: 'success',
    order: 2,
    visible: ({ row }) => pendingActionKey(row) === 'review',
  },
  {
    key: 'dispose',
    label: '不良处置',
    type: 'warning',
    order: 3,
    visible: ({ row }) => canDispose && needsDisposition(row),
  },
  {
    key: 'disposition-history',
    label: '处置历史',
    order: 9,
    visible: ({ row }) => !!row.lotId,
  },
  { key: 'print', label: '打印检验报告', order: 10, visible: ({ row }) => !!row.inspectionId },
  { key: 'history', label: '查看质量历史', order: 11, visible: ({ row }) => !!row.lotId },
]

function handleMaterialAction(key: string, row: any) {
  if (key === 'print') return emit('print', row)
  if (key === 'history') return emit('history', row)
  if (key === 'disposition-history') return emit('disposition-history', row)
  // 处置入口：只按「已审定不良 + 剩余可处置量 > 0」判定，不参与待办互斥
  if (key === 'dispose') {
    if (canDispose && needsDisposition(row)) emit('dispose', row)
    return
  }
  if (pendingActionKey(row) !== key) return
  if (key === 'edit') return emit('edit', row)
  emit('review')
}
</script>

<style scoped>
.quantity-context {
  line-height: 1.6;
  white-space: normal;
}

.quantity-context__emphasis {
  color: var(--el-color-warning-dark-2);
  font-weight: 600;
}
.muted {
  color: #909399;
  font-size: 12px;
}
.pending-tag {
  margin-top: 4px;
}
.operation-muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}
</style>
