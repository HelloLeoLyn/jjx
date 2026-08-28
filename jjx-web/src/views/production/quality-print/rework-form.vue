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
      <div class="linked-print-title">返工返修单</div>
      <div class="linked-print-meta">
        <div>记录编号：JJX-QR-073</div>
        <div>质检单：{{ display(info.inspectionNo) }}</div>
        <div>开单日期：{{ dateTime(info.inspectTime || info.createTime).slice(0, 10) }}</div>
        <div>工单：{{ display(info.orderNo) }}</div>
        <div>产品：{{ display(info.productName) }}</div>
        <div>工序：{{ display(info.processName) }}</div>
      </div>
      <table class="linked-print-table">
        <tbody>
          <tr>
            <th>检验数</th>
            <td>{{ display(info.totalQty) }}</td>
            <th>不良数</th>
            <td>{{ display(info.failQty) }}</td>
            <th>判定</th>
            <td>{{ display(info.resultName) }}</td>
          </tr>
          <tr>
            <th>检验员</th>
            <td>{{ display(info.inspector) }}</td>
            <th>检验时间</th>
            <td colspan="3">{{ dateTime(info.inspectTime || info.createTime) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-section">不良信息</div>
      <div class="linked-print-note">{{ display(info.defectDesc || info.remark) }}</div>
      <div class="linked-print-section">返工/返修处置</div>
      <table class="linked-print-table">
        <tbody>
          <tr>
            <th style="width: 110px">处置方式</th>
            <td>□ 返工　□ 返修　□ 报废　□ 其他</td>
          </tr>
          <tr>
            <th>责任工序/人</th>
            <td></td>
          </tr>
          <tr>
            <th>处置要求</th>
            <td style="height: 70px"></td>
          </tr>
          <tr>
            <th>复检结果</th>
            <td style="height: 50px"></td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-signs">
        <div>
          制单：<span>{{ info.inspector }}</span>
        </div>
        <div>处置人：<span></span></div>
        <div>复检/审核：<span></span></div></div
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
import { InspectionResult, InspectionType } from '@/enums/quality'
import { dateTime, display, logTemplatePrint } from './shared'
import './print-common.css'
const route = useRoute(),
  router = useRouter(),
  info = ref<QualityVO | null>(null),
  loading = ref(false),
  printing = ref(false)
onMounted(async () => {
  const id = Number(route.query.inspectionId)
  if (!id) return ElMessage.error('缺少有效的质检单ID')
  loading.value = true
  try {
    const r = await qualityApi.getById(id)
    const data = r.data
    if (!data) throw new Error('质检单不存在')
    if (data.inspectionType !== InspectionType.FQC || data.result !== InspectionResult.FAIL)
      throw new Error('仅 FQC 不合格单可打印返工返修单')
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
    await logTemplatePrint('JJX-QR-073')
    window.print()
  } catch (e: any) {
    ElMessage.error(e?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>
