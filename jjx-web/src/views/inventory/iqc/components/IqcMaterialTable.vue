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
    /><el-table-column prop="quantity" label="收货数量" width="90" />
    <el-table-column label="合格" width="90"
      ><template #default="{ row }">{{ row.qualifiedQuantity }}</template></el-table-column
    >
    <el-table-column label="不良" width="90"
      ><template #default="{ row }">{{ row.rejectedQuantity }}</template></el-table-column
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
        ><el-button
          v-if="
            canDispose &&
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
  (e: 'go-disposition', row: any): void
}>()
</script>
