<template>
  <el-descriptions :column="2" border>
    <el-descriptions-item label="登记单号">{{ invoice.documentNo }}</el-descriptions-item>
    <el-descriptions-item label="真实发票号码">{{
      invoice.invoiceNo || '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="采购订单">
      <el-link v-if="invoice.orderNo" type="primary" @click="emit('order', invoice.orderId)">{{
        invoice.orderNo
      }}</el-link>
      <span v-else>订单信息未取得</span>
    </el-descriptions-item>
    <el-descriptions-item label="供应商">{{ invoice.supplierName || '-' }}</el-descriptions-item>
    <el-descriptions-item label="订单含税金额">{{
      money(invoice.orderTotalAmount)
    }}</el-descriptions-item>
    <el-descriptions-item label="收货状态">{{
      ReceiptStatusEnum.getLabel(invoice.receiptStatus)
    }}</el-descriptions-item>
    <el-descriptions-item label="票面开票日期">{{
      invoice.invoiceNo ? invoice.documentDate : '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="币种">{{
      invoice.invoiceNo ? invoice.currency : '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="销售方名称">{{
      invoice.sellerName || '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="销售方税号">{{
      invoice.sellerTaxNo || '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="购买方名称">{{
      invoice.buyerName || '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="购买方税号">{{
      invoice.buyerTaxNo || '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="不含税金额">{{
      money(invoice.untaxedAmount)
    }}</el-descriptions-item>
    <el-descriptions-item label="税额">{{ money(invoice.taxAmount) }}</el-descriptions-item>
    <el-descriptions-item label="本张价税合计">{{
      invoice.invoiceNo ? money(invoice.documentAmount) : '待补录'
    }}</el-descriptions-item>
    <el-descriptions-item label="状态">{{
      InvoiceStatusEnum.getLabel(invoice.documentStatus)
    }}</el-descriptions-item>
    <el-descriptions-item label="登记备注" :span="2">{{
      invoice.remark || '-'
    }}</el-descriptions-item>
    <el-descriptions-item label="登记时间">{{ invoice.createTime || '-' }}</el-descriptions-item>
    <el-descriptions-item label="核验日期">{{
      invoice.verificationDate || '-'
    }}</el-descriptions-item>
    <el-descriptions-item label="实际核验人">{{
      invoice.verifierName || invoice.verifiedBy || '未记录'
    }}</el-descriptions-item>
    <el-descriptions-item label="实际核验时间">{{
      invoice.verifiedTime || '未记录'
    }}</el-descriptions-item>
    <el-descriptions-item label="核验意见" :span="2">{{
      invoice.verificationRemark || '-'
    }}</el-descriptions-item>
  </el-descriptions>
</template>

<script setup lang="ts">
import { InvoiceStatusEnum } from '@/enums/purchase/invoice'
import { ReceiptStatusEnum } from '@/enums/purchase/receipt'
defineProps<{ invoice: Record<string, any> }>()
const emit = defineEmits<{ order: [id: number] }>()
const money = (value: number | null | undefined) =>
  value == null
    ? '待补录'
    : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
</script>
