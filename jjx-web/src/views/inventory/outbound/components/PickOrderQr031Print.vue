<template>
  <section class="qr031-sheet">
    <header class="company-name">深圳市精捷信科技有限公司</header>
    <h1>领 料 单</h1>
    <div class="meta-grid">
      <div>
        机种：<strong>{{ data.machineModel || '' }}</strong>
      </div>
      <div>订购数量：{{ formatNumber(data.orderQuantity) }}</div>
      <div class="record-no">{{ data.recordNo || 'JJX-QR-031' }}</div>
      <div>品名：{{ data.productName || '' }}</div>
      <div>交货日期：{{ data.deliveryDate || '' }}</div>
      <div>制表日期：{{ data.preparedDate || '' }}</div>
    </div>

    <table>
      <thead>
        <tr>
          <th class="seq">项次</th>
          <th class="material">原料品名</th>
          <th class="project">项目</th>
          <th class="spec">规格</th>
          <th class="unit">单位</th>
          <th class="module">模数</th>
          <th class="qty">实发数量</th>
          <th class="stock">库存数量</th>
          <th class="remark">备注</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="(item, index) in displayRows" :key="index">
          <td>{{ item?.sequence || '' }}</td>
          <td>{{ item?.materialName || '' }}</td>
          <td>{{ item?.projectName || '' }}</td>
          <td>{{ item?.specification || '' }}</td>
          <td>{{ item?.unit || '' }}</td>
          <td>{{ formatNumber(item?.moduleQty, '') }}</td>
          <td>{{ formatNumber(item?.issuedQuantity, '') }}</td>
          <td>{{ formatNumber(item?.stockQuantity, '') }}</td>
          <td>{{ item?.remark || '' }}</td>
        </tr>
      </tbody>
    </table>

    <footer>
      <span>主管：</span>
      <span>核准：</span>
      <span>制表：{{ data.preparedBy || '' }}</span>
    </footer>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PickOrderPrintItemVO, PickOrderPrintVO } from '@/types/inventory/outbound'

const props = defineProps<{ data: PickOrderPrintVO }>()
// 按实际领料明细生成行（不补空行）：实际几项材料就渲染几行
const displayRows = computed<PickOrderPrintItemVO[]>(() => props.data.items || [])

function formatNumber(value?: number, empty = '-') {
  if (value === undefined || value === null) return empty
  return Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
}
</script>

<style scoped>
.qr031-sheet {
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 11px;
}
.company-name {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}
h1 {
  margin: 5px 0 8px;
  text-align: center;
  font:
    700 20px SimHei,
    '黑体',
    sans-serif;
  letter-spacing: 12px;
}
.meta-grid {
  display: grid;
  grid-template-columns: 1.25fr 1fr 0.7fr;
  line-height: 24px;
}
.record-no {
  text-align: right;
  font-weight: 700;
}
table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
thead {
  display: table-header-group;
}
tr {
  break-inside: avoid;
}
th,
td {
  height: 25px;
  padding: 2px 3px;
  border: 1px solid #000;
  text-align: center;
  overflow-wrap: anywhere;
}
th {
  font-weight: 700;
}
.seq {
  width: 5%;
}
.material {
  width: 25%;
}
.project {
  width: 10%;
}
.spec {
  width: 13%;
}
.unit {
  width: 7%;
}
.module {
  width: 7%;
}
.qty {
  width: 10%;
}
.stock {
  width: 10%;
}
.remark {
  width: 13%;
}
footer {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin-top: 10px;
  font-size: 12px;
}
footer span:nth-child(2) {
  text-align: center;
}
footer span:last-child {
  text-align: right;
}
@media print {
  .qr031-sheet {
    break-inside: avoid;
  }
}
</style>
