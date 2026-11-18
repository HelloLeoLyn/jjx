<template>
  <div ref="sheet" class="payment-print">
    <h2>采购付款单</h2>
    <table>
      <tbody>
        <tr>
          <th>付款单号</th>
          <td>{{ payment.paymentNo }}</td>
          <th>采购订单号</th>
          <td>{{ payment.orderNo || '-' }}</td>
        </tr>
        <tr>
          <th>供应商</th>
          <td colspan="3">{{ payment.supplierName || '-' }}</td>
        </tr>
        <tr>
          <th>申请金额</th>
          <td>{{ money(payment.paymentAmount) }}</td>
          <th>付款方式</th>
          <td>{{ PaymentMethodEnum.getLabel(payment.paymentMethod) }}</td>
        </tr>
        <tr>
          <th>计划付款日</th>
          <td>{{ payment.paymentDate || '-' }}</td>
          <th>实际付款日</th>
          <td>{{ payment.actualPaymentDate || '-' }}</td>
        </tr>
        <tr>
          <th>银行账号</th>
          <td colspan="3">{{ payment.bankAccount || '-' }}</td>
        </tr>
        <tr>
          <th>转账流水号</th>
          <td colspan="3">{{ payment.voucherNo || '-' }}</td>
        </tr>
        <tr>
          <th>阶段</th>
          <td>{{ PaymentStageEnum.getLabel(paymentStageOf(payment)) }}</td>
          <th>审批人</th>
          <td>{{ payment.approverName || '-' }}</td>
        </tr>
        <tr>
          <th>审批时间</th>
          <td colspan="3">{{ payment.approvalTime || '-' }}</td>
        </tr>
        <tr>
          <th>审批意见</th>
          <td colspan="3">{{ payment.approvalComment || '-' }}</td>
        </tr>
        <tr>
          <th>备注</th>
          <td colspan="3">{{ payment.remark || '-' }}</td>
        </tr>
      </tbody>
    </table>
    <p>以银行转账回单为付款凭证；未确认付款的申请单不表示已支付。</p>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { PaymentMethodEnum, PaymentStageEnum, paymentStageOf } from '@/enums/purchase/payment'
defineProps<{ payment: any }>()
const sheet = ref<HTMLElement>()
let frame: HTMLIFrameElement | undefined
const money = (value: unknown) =>
  value == null
    ? '-'
    : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
function print() {
  if (!sheet.value) return
  frame?.remove()
  frame = document.createElement('iframe')
  frame.style.cssText = 'position:fixed;width:0;height:0;border:0'
  frame.title = '采购付款单打印'
  frame.onload = () => {
    frame?.contentWindow?.focus()
    frame?.contentWindow?.print()
  }
  frame.srcdoc = `<!doctype html><html lang="zh-CN"><head><meta charset="UTF-8"><title>采购付款单</title><style>@page{size:A4;margin:16mm}body{font:14px sans-serif;color:#000}h2{text-align:center}table{border-collapse:collapse;width:100%;table-layout:fixed}td,th{border:1px solid #333;padding:10px;white-space:pre-wrap;overflow-wrap:anywhere}th{width:18%}p{font-size:12px}</style></head><body>${sheet.value.innerHTML}</body></html>`
  document.body.appendChild(frame)
}
onBeforeUnmount(() => frame?.remove())
defineExpose({ print })
</script>

<style scoped>
.payment-print h2 {
  text-align: center;
}
.payment-print table {
  border-collapse: collapse;
  width: 100%;
  table-layout: fixed;
}
.payment-print td,
.payment-print th {
  border: 1px solid var(--el-border-color);
  padding: 10px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.payment-print th {
  width: 18%;
}
.payment-print p {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
