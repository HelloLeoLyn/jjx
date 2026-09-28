<template>
  <el-table
    :data="rows"
    border
    class="material-table"
    :row-class-name="rowClass"
    @selection-change="(v: any[]) => emit('selection-change', v)"
  >
    <el-table-column type="selection" width="46" fixed="left" :selectable="canEdit" />
    <el-table-column prop="materialCode" label="材料编码" min-width="125" /><el-table-column
      prop="materialName"
      label="材料名称"
      min-width="150"
    /><el-table-column label="数量" width="105"
      ><template #default="{ row }">{{ row.isReinspection ? '本批复检' : '整批收货' }} {{
        row.isReinspection ? row.reinspectionQuantity ?? 0 : row.quantity
      }}</template></el-table-column
    >
    <el-table-column label="判定数量" width="105"
      ><template #default="{ row }">{{ row.isReinspection ? '本批合格' : '整批合格' }} {{
        row.qualifiedQuantity ?? 0
      }}</template></el-table-column
    >
    <el-table-column label="不良数量" width="105"
      ><template #default="{ row }">{{ row.isReinspection ? '本批不良' : '整批不良' }} {{
        row.rejectedQuantity ?? 0
      }}</template></el-table-column
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
          <div>剩余可处置 {{ row.trace?.remainingDispositionQuantity ?? row.remainingDispositionQuantity ?? 0 }} 件</div>
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
    <el-table-column label="操作" width="205" fixed="right"
      ><template #default="{ row }"
        ><el-button v-if="canEdit(row)" link type="primary" @click="emit('edit', row)"
          >检验录入</el-button
        ><el-button
          v-if="canJudge && row.reviewStatus === QualityReviewStatus.PENDING"
          link
          type="success"
          @click="emit('review')"
          >审核/驳回</el-button
        ><el-button v-if="row.inspectionId" link type="primary" @click="emit('print', row)"
          >打印</el-button
        ><el-button v-if="row.lotId" link type="info" @click="emit('history', row)"
          >质量历史</el-button
        ><el-button
          v-if="
                canDispose &&
                Number((row.trace?.remainingDispositionQuantity ?? row.remainingDispositionQuantity) || 0) > 0 &&
                row.inspectionResult === InboundInspectionResultEnum.FAIL.value &&
            (row.reviewStatus === QualityReviewStatus.APPROVED || isCompleted)
          "
          link
          type="warning"
          @click="emit('go-disposition', row)"
          >去处置</el-button
        ></template
      ></el-table-column
    >
  </el-table>
</template>

<script setup lang="ts">
/**
 * 来料检验材料表（dev-20260924-024 刀1：从 iqc/index.vue 抽出的展示型子组件）
 * 只负责展示与事件上抛；数量/判定/原因均为只读派生结果（013 口径）。
 * 根节点就是 el-table，父页面针对 .material-table 的 scoped 样式仍然生效。
 */
import { InspectionResultEnum as InboundInspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { QualityReviewStatus, QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'
import { deriveIqcReasonText } from '../iqcRowRules'

defineProps<{
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
  (e: 'go-disposition', row: any): void
}>()

function disposedQuantity(row: any) {
  if (row.trace?.disposedQuantity != null) return Number(row.trace.disposedQuantity)
  return Math.max(
    0,
    Number(row.rejectedQuantity || 0) - Number(row.remainingDispositionQuantity || 0)
  )
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
</style>
