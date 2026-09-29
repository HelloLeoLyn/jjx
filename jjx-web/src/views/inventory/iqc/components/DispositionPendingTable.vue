<template>
  <el-table v-loading="loading" :data="rows" border size="small">
    <el-table-column prop="materialCode" label="物料" min-width="140" />
    <el-table-column prop="materialName" label="物料名称" min-width="150" />
    <el-table-column label="数量口径" width="180">
      <template #default="{ row }">
        <div>原始隔离：{{ num(row.quantity) }}</div>
        <span class="muted">剩余可处置：{{ num(row.remainingQuantity) }}</span>
      </template>
    </el-table-column>
    <el-table-column label="来料批次 / 采购单号" min-width="190">
      <template #default="{ row }">
        <div>{{ row.inboundNo || '-' }}</div>
        <span class="muted">采购：{{ row.sourceNo || '-' }}</span>
      </template>
    </el-table-column>
    <el-table-column prop="supplierName" label="供应商" min-width="140" />
    <el-table-column label="检验批号" min-width="150">
      <template #default="{ row }">{{ row.lotNo || '-' }}</template>
    </el-table-column>
    <el-table-column label="不合格原因" min-width="220" show-overflow-tooltip>
      <template #default="{ row }">{{ row.defectReason || '-' }}</template>
    </el-table-column>
    <el-table-column label="操作" width="110" fixed="right">
      <template #default="{ row }">
        <el-button v-if="canDispose" link type="warning" @click="emit('dispose', row)">处置</el-button>
        <span v-else class="muted">无处置权限</span>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
/**
 * 待处理明细表（dev-20260929-007）—— 区块 = 组件 = 文案同名。
 *
 * 同一份实现在两处复用，避免第二份表格：
 *   ① 来料检验工作台（IqcWorkbenchPanel）—— 单个来料批次，数据来自 iqc-workbench；
 *   ② 跨批次待处置工作台（/inventory/iqc-quarantine）—— 全厂待处置，数据来自 /iqc-quarantine/page?pendingOnly=true。
 * 行数据必须带上下文（来料批次 / 采购单号 / 供应商 / 检验批号 / 不合格原因）—— 处置弹窗直接吃这一行。
 */
defineProps<{
  rows: any[]
  canDispose: boolean
  loading?: boolean
}>()
const emit = defineEmits<{ (e: 'dispose', row: any): void }>()

const num = (value?: number | string | null) =>
  value == null || value === ''
    ? '-'
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
</script>

<style scoped>
.muted {
  color: #909399;
  font-size: 12px;
}
</style>
