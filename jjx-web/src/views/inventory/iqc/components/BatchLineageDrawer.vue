<template>
  <el-drawer
    :model-value="visible"
    title="批次溯源"
    size="760px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="lineage-tip">
      当前批次：{{ batchNo || '-' }}
      <span class="lineage-count">共 {{ rows.length }} 个批次</span>
    </div>
    <el-table :data="rows" border size="small" max-height="460">
      <el-table-column prop="batchNo" label="批次" width="190" />
      <el-table-column prop="parentBatchNo" label="父批次" width="190" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">{{ IqcBatchTypeEnum.getLabel(row.batchType) }}</template>
      </el-table-column>
      <el-table-column prop="quantity" label="批次数量" width="95" />
      <el-table-column prop="acceptedQuantity" label="合格数量" width="95" />
      <el-table-column prop="rejectedQuantity" label="不良数量" width="95" />
      <el-table-column label="状态" width="105">
        <template #default="{ row }">
          <el-tag :type="IqcBatchStatusEnum.getTagProps(row.status).type">{{
            IqcBatchStatusEnum.getLabel(row.status)
          }}</el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!rows.length" description="暂无批次链路数据" />
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 批次溯源抽屉（dev-20260929-007）—— 区块=组件=文案同名。
 *
 * 数据源：IQC 工作台一次带回的批次列表（含 parentBatchNo 关系）。
 * 说明：原「来料不合格处置」页删除时把行内「谱系」一起带走，这里按同一实现补回工作台，
 * 保证返工子批/父批关系在界面可见（数据一直在 inventory_iqc_batch）。
 */
import { computed } from 'vue'
import { IqcBatchStatusEnum, IqcBatchTypeEnum } from '@/enums/inventory/IqcBatchEnum'

const props = defineProps<{
  visible: boolean
  /** 当前查看的批次号 */
  batchNo?: string
  /** 该来料批次的全部批次行（含子批） */
  batches: any[]
}>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void }>()

const rows = computed(() => {
  const all = props.batches || []
  if (!props.batchNo) return all
  const mine = all.filter(
    (row: any) => row.batchNo === props.batchNo || row.parentBatchNo === props.batchNo
  )
  return mine.length ? mine : all
})
</script>

<style scoped>
.lineage-tip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  color: #606266;
}
.lineage-count {
  color: #909399;
  font-size: 12px;
}
</style>
