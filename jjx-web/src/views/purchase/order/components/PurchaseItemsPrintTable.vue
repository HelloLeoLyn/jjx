<template>
  <table class="purchase-print-items">
    <caption>币种：{{ currency || 'CNY' }}</caption>
    <thead><tr><th>序号</th><th>品名</th><th>规格</th><th>采购数量</th><th>采购单位</th><th>计价数量</th><th>报价单位</th><th>单价（未税）</th><th>金额（未税）</th></tr></thead>
    <tbody>
      <template v-for="(item,index) in items" :key="index">
        <tr>
          <td>{{ index + 1 }}</td><td>{{ item.supplierMaterialName || item.materialName }}<small>{{ item.materialCode }}</small></td>
          <td>{{ entrySpecification(item) }}<small v-if="item.rollLengthM && (item.purchaseUnit === 'ROLL' || item.priceUnit === 'ROLL')">卷宽{{ entryNumber(item.rollWidthMm) }}mm，每卷{{ entryNumber(item.rollLengthM) }}m</small></td>
          <td class="numeric">{{ entryNumber(item.purchaseQuantity ?? (item.purchaseUnit === 'ROLL' ? item.rollCount : item.quantity)) }}</td>
          <td>{{ unitLabel(item.purchaseUnit || item.unit) }}</td>
          <td class="numeric">{{ entryNumber(item.priceQuantity,12) }}</td><td>{{ unitLabel(item.priceUnit || item.unit) }}</td>
          <td class="numeric">{{ entryPrice(item.unitPrice) }}</td><td class="numeric">{{ item.amount == null ? '—' : Number(item.amount).toFixed(2) }}</td>
        </tr>
        <tr v-if="item.purchaseRemark"><td></td><td colspan="8" class="purchase-requirement">采购 / 加工交货要求：{{ item.purchaseRemark }}</td></tr>
        <tr v-if="item.pricingMessage"><td></td><td colspan="8">换算待确认：{{ item.pricingMessage }}</td></tr>
      </template>
      <tr v-if="!items.length"><td colspan="9">无明细</td></tr>
    </tbody>
  </table>
</template>
<script setup lang="ts">
import { entryNumber,entryPrice,entrySpecification } from '@/api/purchase/orderEntry'
import { getUnitName } from '@/enums/unit'
const unitLabel = (code?: string) => code === '㎡' ? '㎡' : getUnitName(code || '')
interface Item {
  materialCode?: string; materialName?: string; supplierMaterialName?: string; materialSpec?: string;
  purchaseQuantity?: number | null; priceQuantity?: number | null; quantity?: number;
  purchaseUnit?: string; priceUnit?: string; unit?: string; rollCount?: number | null;
  rollLengthM?: number | null; rollWidthMm?: number | null;
  unitPrice?: number; amount?: number; purchaseRemark?: string; pricingMessage?: string;
}
defineProps<{ items: Item[]; currency?: string }>()
</script>
<style scoped>
.purchase-print-items{width:100%;border-collapse:collapse;table-layout:fixed;font-size:12px}.purchase-print-items th,.purchase-print-items td{border:1px solid #333;padding:5px 4px;overflow-wrap:anywhere;text-align:center;vertical-align:top}.purchase-print-items th:first-child{width:5%}.purchase-print-items th:nth-child(2){width:18%}.purchase-print-items th:nth-child(3){width:17%}.purchase-print-items small{display:block;font-size:10px;color:#444;margin-top:3px}.purchase-print-items .numeric{text-align:right}.purchase-print-items .purchase-requirement{text-align:left;white-space:pre-wrap}tr{break-inside:avoid}
</style>
