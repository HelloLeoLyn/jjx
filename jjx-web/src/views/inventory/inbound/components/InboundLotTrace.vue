<template>
  <section v-if="lots?.length">
    <el-divider content-position="left">来源检验批及处置</el-divider>
    <p class="quantity-note">来源批次数量是整批送检数；本单数量是当前入库单数量。相关单据可能分次入库，请结合各单已入库量和状态查看。</p>
    <div v-for="lot in lots" :key="lot.lotId" class="lot-section">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="检验批">{{ lot.lotNo }}</el-descriptions-item>
        <el-descriptions-item label="来源批次数量">{{ formatNumber(lot.lotQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="检验合格">{{ formatNumber(lot.qualifiedQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="检验不合格">{{ formatNumber(lot.rejectedQuantity) }}</el-descriptions-item>
      </el-descriptions>
      <p>本批相关入库单（含让步入库、红冲单）</p>
      <el-table :data="lot.inboundDocuments || []" border size="small">
        <el-table-column label="入库单号" min-width="185">
          <template #default="{ row }">
            {{ row.inboundNo }} <el-tag v-if="String(row.inboundId) === String(inboundId)" size="small">本单</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="类型" min-width="140">
          <template #default="{ row }">{{ inboundTypeLabel(row.inboundType, { reverse: isReverseInbound(row.inboundNo, row.quantity) }) }}</template>
        </el-table-column>
        <el-table-column label="单据数量" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="已入库量" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.postedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ inboundStatusLabel(row.status, { inboundType: row.inboundType }) }}</template>
        </el-table-column>
      </el-table>
      <p>本批报废单</p>
      <el-table :data="lot.scrapDocuments || []" border size="small" empty-text="暂无报废单">
        <el-table-column label="报废单号" prop="scrapNo" min-width="180" />
        <el-table-column label="报废数量" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ ScrapOrderStatusEnum.getLabel(row.status) }}</template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { InboundLotSummaryVO } from '@/types/inventory/inbound'
import { formatNumber } from '@/utils/format'
import { inboundTypeLabel, inboundStatusLabel, isReverseInbound } from '@/enums/inventory/InboundEnum'
import { ScrapOrderStatusEnum } from '@/enums/quality'

defineProps<{ lots?: InboundLotSummaryVO[]; inboundId: string | number }>()
</script>

<style scoped>
.quantity-note { color: #606266; font-size: 13px; }
.lot-section { margin-bottom: 18px; }
.lot-section > p { font-size: 13px; margin: 12px 0 6px; }
</style>
