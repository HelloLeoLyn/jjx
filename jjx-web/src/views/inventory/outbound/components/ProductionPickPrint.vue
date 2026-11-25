<template>
  <section class="production-pick-print">
    <PrintCompanyHeader variant="center" />
    <h2>{{ mode === 'picking' ? '拣 货 单' : '领 料 单' }}</h2>
    <div class="print-meta">
      <span>单据号：{{ detail.outboundNo }}</span
      ><span>仓库：{{ detail.warehouseName }}</span
      ><span>来源工单：{{ detail.sourceNo }}</span
      ><span>状态：{{ detail.statusName }}</span
      ><span v-if="mode !== 'picking'">表单编号：JJX-QR-031</span>
    </div>
    <table v-if="mode === 'picking'">
      <thead>
        <tr>
          <th>库位</th>
          <th>物料编码</th>
          <th>物料名称</th>
          <th>批次</th>
          <th>卷号</th>
          <th>单位</th>
          <th>拣货数量</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in picking" :key="index">
          <td>{{ row.locationName || row.locationId }}</td>
          <td>{{ row.materialCode }}</td>
          <td>{{ row.materialName }}</td>
          <td>{{ row.batchNo }}</td>
          <td>{{ row.rollNo || '-' }}</td>
          <td>{{ row.unit }}</td>
          <td>{{ row.quantity }}</td>
        </tr>
      </tbody>
    </table>
    <table v-else>
      <thead>
        <tr>
          <th>序号</th>
          <th>物料编码</th>
          <th>物料名称</th>
          <th>规格</th>
          <th>单位</th>
          <th>需求</th>
          <th>实发</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(row, index) in materials" :key="row.materialId">
          <td>{{ index + 1 }}</td>
          <td>{{ row.materialCode }}</td>
          <td>{{ row.materialName }}</td>
          <td>{{ row.specification }}</td>
          <td>{{ row.unit }}</td>
          <td>{{ row.quantity }}</td>
          <td>{{ row.issued }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="shortage">尚有欠料 {{ shortage }}，请核对批次分配。</p>
    <p v-if="allocation.mixedSupplier">跨供应商混批；发料以已批准的分配为准。</p>
    <p v-if="allocation.unknownSupplier">存在供应商未知的批次，禁止发料。</p>
    <footer>
      <span>领料人：</span><span>仓管员：</span><span>核准：</span><span>日期：</span>
    </footer>
  </section>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import type { PickAllocationView, OutboundVO } from '@/types/inventory/outbound'
import { materialRows, pickingRows } from './pickAllocation'
const props = defineProps<{ allocation: PickAllocationView; detail: OutboundVO; mode: string }>()
const materials = computed(() => materialRows(props.allocation).filter((row) => !row.isGroup))
const picking = computed(() => pickingRows(props.allocation))
const shortage = computed(() =>
  Number(
    props.allocation.demands
      .flatMap((d) => d.allocations)
      .filter((row) => row.stockItemId == null)
      .reduce((sum, row) => sum + Number(row.quantity), 0)
      .toFixed(4)
  )
)
</script>
<style scoped>
h2 {
  text-align: center;
  letter-spacing: 6px;
}
.print-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 25px;
  margin: 12px 0;
  font-size: 12px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
th,
td {
  border: 1px solid #333;
  padding: 7px 4px;
  overflow-wrap: anywhere;
}
thead {
  display: table-header-group;
}
tr {
  break-inside: avoid;
}
footer {
  display: flex;
  justify-content: space-between;
  margin-top: 28px;
  font-size: 12px;
}
p {
  font-size: 12px;
}
</style>
