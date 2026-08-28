<template>
  <div class="linked-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button
      ><el-button type="primary" :loading="printing" :disabled="!info" @click="print"
        >打印</el-button
      >
    </div>
    <A4Canvas v-if="info" :padding-mm="14"
      ><PrintCompanyHeader variant="center" />
      <div class="linked-print-title">成品检验报告</div>
      <div class="linked-print-meta">
        <div>记录编号：JJX-QR-039</div>
        <div>检验单号：{{ display(info.inspectionNo) }}</div>
        <div>打印日期：{{ today }}</div>
        <div>工单：{{ display(info.orderNo) }}</div>
        <div>产品：{{ display(info.productName) }}</div>
        <div>工序：{{ display(info.processName) }}</div>
      </div>
      <table class="linked-print-table">
        <tbody>
          <tr>
            <th>检验数</th>
            <td>{{ display(info.totalQty) }}</td>
            <th>合格数</th>
            <td>{{ display(info.passQty) }}</td>
            <th>不良数</th>
            <td>{{ display(info.failQty) }}</td>
          </tr>
          <tr>
            <th>判定结果</th>
            <td colspan="2">{{ display(info.resultName) }}</td>
            <th>检验员</th>
            <td>{{ display(info.inspector) }}</td>
            <th>检验时间</th>
            <td>{{ dateTime(info.inspectTime || info.createTime) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-section">检验项目</div>
      <table class="linked-print-table">
        <thead>
          <tr>
            <th style="width: 42px">序号</th>
            <th>检验项目</th>
            <th>标准</th>
            <th>实测值</th>
            <th>结果</th>
            <th>备注</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in info.items || []" :key="item.itemId || index">
            <td>{{ index + 1 }}</td>
            <td>{{ display(item.checkItem) }}</td>
            <td>{{ display(item.standard) }}</td>
            <td>{{ display(item.actualValue) }}</td>
            <td>{{ display(item.result) }}</td>
            <td>{{ display(item.remark) }}</td>
          </tr>
          <tr v-if="!info.items?.length">
            <td colspan="6">无明细检验项</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-note">不良/备注：{{ display(info.defectDesc || info.remark) }}</div>
      <div class="linked-print-signs">
        <div>
          检验员：<span>{{ info.inspector }}</span>
        </div>
        <div>审核：<span></span></div>
        <div>批准：<span></span></div></div
    ></A4Canvas>
    <div v-else v-loading="loading" class="linked-print-loading" />
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import { InspectionType } from '@/enums/quality'
import { dateTime, display, logTemplatePrint, printDate } from './shared'
import './print-common.css'
const route = useRoute(),
  router = useRouter(),
  info = ref<QualityVO | null>(null),
  loading = ref(false),
  printing = ref(false),
  today = printDate()
onMounted(async () => {
  const id = Number(route.query.inspectionId)
  if (!id) return ElMessage.error('缺少有效的质检单ID')
  loading.value = true
  try {
    const r = await qualityApi.getById(id)
    const data = r.data
    if (!data) throw new Error('质检单不存在')
    if (data.inspectionType !== InspectionType.FQC) throw new Error('该质检单不是 FQC 完工检验')
    info.value = data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
async function print() {
  printing.value = true
  try {
    await logTemplatePrint('JJX-QR-039')
    window.print()
  } catch (e: any) {
    ElMessage.error(e?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>
