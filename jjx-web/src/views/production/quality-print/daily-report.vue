<template>
  <div class="linked-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button
      ><el-button type="primary" :loading="printing" :disabled="!execution" @click="print"
        >打印</el-button
      >
    </div>
    <A4Canvas v-if="execution" :padding-mm="14"
      ><PrintCompanyHeader variant="center" />
      <div class="linked-print-title">生产日报表</div>
      <div class="linked-print-meta">
        <div>记录编号：JJX-QR-043</div>
        <div>报表日期：{{ today }}</div>
        <div>工单：{{ display(execution.orderNo) }}</div>
        <div>工序：{{ display(execution.processName) }}</div>
        <div>产品：{{ display(execution.productionOrder?.productName) }}</div>
        <div>执行ID：{{ execution.executionId }}</div>
      </div>
      <div class="linked-print-section">当日报工</div>
      <table class="linked-print-table">
        <thead>
          <tr>
            <th>报工单</th>
            <th>报工人</th>
            <th>合格</th>
            <th>不良</th>
            <th>人工工时</th>
            <th>机器工时</th>
            <th>报工时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in todayRows" :key="row.reportId">
            <td>{{ display(row.reportNo) }}</td>
            <td>{{ display(row.reporterName) }}</td>
            <td>{{ display(row.qualifiedQuantity) }}</td>
            <td>{{ display(row.defectiveQuantity) }}</td>
            <td>{{ display(row.laborHours) }}</td>
            <td>{{ display(row.machineHours) }}</td>
            <td>{{ dateTime(row.reportTime) }}</td>
          </tr>
          <tr v-if="!todayRows.length">
            <td colspan="7">当日无有效报工</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-section">数量/工时汇总</div>
      <table class="linked-print-table">
        <thead>
          <tr>
            <th></th>
            <th>合格数</th>
            <th>不良数</th>
            <th>人工工时</th>
            <th>机器工时</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th>当日</th>
            <td>{{ sum(todayRows, 'qualifiedQuantity') }}</td>
            <td>{{ sum(todayRows, 'defectiveQuantity') }}</td>
            <td>{{ sum(todayRows, 'laborHours') }}</td>
            <td>{{ sum(todayRows, 'machineHours') }}</td>
          </tr>
          <tr>
            <th>累计</th>
            <td>{{ sum(validRows, 'qualifiedQuantity') }}</td>
            <td>{{ sum(validRows, 'defectiveQuantity') }}</td>
            <td>{{ sum(validRows, 'laborHours') }}</td>
            <td>{{ sum(validRows, 'machineHours') }}</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-signs">
        <div>制表：<span></span></div>
        <div>班组长：<span></span></div>
        <div>审核：<span></span></div></div
    ></A4Canvas>
    <div v-else v-loading="loading" class="linked-print-loading" />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { getWorkReportsByExecution, type WorkReportVO } from '@/api/production/workReport'
import type { OperationExecutionVO } from '@/types/production/operationExecution'
import { WorkReportStatus } from '@/enums/production'
import { dateTime, display, logTemplatePrint, printDate } from './shared'
import './print-common.css'
const route = useRoute(),
  router = useRouter(),
  execution = ref<OperationExecutionVO | null>(null),
  rows = ref<WorkReportVO[]>([]),
  loading = ref(false),
  printing = ref(false),
  today = printDate()
const validRows = computed(() =>
  rows.value.filter((r) => r.reportStatus === WorkReportStatus.APPROVED)
)
const todayRows = computed(() =>
  validRows.value.filter((r) => dayjs(r.reportTime).format('YYYY-MM-DD') === today)
)
const sum = (
  list: WorkReportVO[],
  key: 'qualifiedQuantity' | 'defectiveQuantity' | 'laborHours' | 'machineHours'
) => list.reduce((n, r) => n + Number(r[key] || 0), 0)
onMounted(async () => {
  const id = Number(route.query.executionId)
  if (!id) return ElMessage.error('缺少有效的工序执行ID')
  loading.value = true
  try {
    const [e, r] = await Promise.all([
      operationExecutionApi.getInfo(id),
      getWorkReportsByExecution(id),
    ])
    execution.value = e.data
    rows.value = (r as any)?.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
async function print() {
  printing.value = true
  try {
    await logTemplatePrint('JJX-QR-043')
    window.print()
  } catch (e: any) {
    ElMessage.error(e?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>
