<template>
  <el-card class="bom-detail-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>BOM明细</span>
        <span class="total-count">共 {{ items.length }} 项</span>
      </div>
    </template>
    <el-table :data="items" border style="width: 100%" :height="height" :stripe="stripe">
      <el-table-column label="组合 / 独立材料" min-width="215" align="center">
        <template #default="{ $index }">{{ groups[$index]?.label }}</template>
      </el-table-column>
      <el-table-column label="行状态" width="105" align="center">
        <template #default="{ row }">
          <el-tag
            :type="
              BomItemReviewStatusEnum.getTagProps(
                row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value
              ).type
            "
            size="small"
          >
            {{
              BomItemReviewStatusEnum.getLabel(
                row.reviewStatus ?? BomItemReviewStatusEnum.DRAFT.value
              )
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="物料编码" prop="materialCode" width="120" />
      <el-table-column label="物料名称" prop="materialName" width="180" />
      <el-table-column label="项目" prop="processName" width="160" show-overflow-tooltip>
        <template #default="{ row }">
          <ProcessName :process-id="row.processId" :name="row.processName" />
        </template>
      </el-table-column>
      <el-table-column label="规格型号" prop="specification" width="120" />
      <el-table-column label="单位" prop="unit" width="80" />
      <el-table-column label="数量" prop="quantity" width="80" align="right">
        <template #default="scope">
          {{ formatNumber(scope.row.quantity) }}
        </template>
      </el-table-column>
      <el-table-column label="损耗率(%)" prop="lossRate" width="100" align="right">
        <template #default="scope">
          {{ formatNumber(scope.row.lossRate) }}
        </template>
      </el-table-column>
      <slot name="extra-columns" />
      <!-- <el-table-column label="来源类型" prop="sourceType" width="100">
        <template #default="scope">
          <el-tag :type="SourceTypeEnum.getTagProps(scope.row.sourceType)?.type" size="small">
            {{ SourceTypeEnum.getLabel(scope.row.sourceType) }}
          </el-tag>
        </template>
      </el-table-column> -->

      <el-table-column label="驳回原因" prop="rejectReason" width="160" show-overflow-tooltip />
      <el-table-column label="备注" prop="remark" />
    </el-table>
  </el-card>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { bomGroupRows } from '@/utils/bomGroups'
import type { EngineeringBomItem } from '@/types/product/bom'
import ProcessName from '@/components/ProcessName.vue'
import { SourceTypeEnum, BomItemReviewStatusEnum } from '@/enums/product'
const props = withDefaults(
  defineProps<{ items: EngineeringBomItem[]; height?: number | string; stripe?: boolean }>(),
  { height: 300, stripe: false }
)
const groups = computed(() => bomGroupRows(props.items, true))
const formatNumber = (value: number | string) => {
  if (value === undefined || value === null) return '0'
  const num = typeof value === 'string' ? parseFloat(value) : value
  return Number.isNaN(num) ? '0' : num.toFixed(2)
}
</script>
<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.total-count {
  font-size: 14px;
  color: #909399;
}
</style>
