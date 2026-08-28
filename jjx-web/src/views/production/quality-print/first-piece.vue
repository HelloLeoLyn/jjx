<template>
  <div class="linked-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button
      ><el-button
        type="primary"
        :loading="printing"
        :disabled="!execution || !firstCheck"
        @click="print"
        >打印</el-button
      >
    </div>
    <A4Canvas v-if="execution" :padding-mm="14"
      ><PrintCompanyHeader variant="center" />
      <div class="linked-print-title">{{ title }}</div>
      <div class="linked-print-meta">
        <div>记录编号：{{ recordNo }}</div>
        <div>首检单号：{{ display(firstCheck?.checkNo) }}</div>
        <div>工单：{{ display(execution.orderNo) }}</div>
        <div>工序：{{ display(execution.processName) }}</div>
        <div>产品：{{ display(execution.productionOrder?.productName) }}</div>
        <div>执行ID：{{ execution.executionId }}</div>
      </div>
      <table class="linked-print-table">
        <tbody>
          <tr>
            <th>检查类型</th>
            <td>首件检查</td>
            <th>检查结果</th>
            <td>{{ resultLabel }}</td>
            <th>检查时间</th>
            <td>{{ dateTime(firstCheck?.checkTime) }}</td>
          </tr>
          <tr>
            <th>检查人</th>
            <td>{{ display(firstCheck?.checker) }}</td>
            <th>计划数量</th>
            <td>{{ display(execution.inputQuantity) }}</td>
            <th>设备</th>
            <td>{{ display(execution.equipmentName) }}</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-section">检查项目与实测结果</div>
      <table class="linked-print-table">
        <thead>
          <tr>
            <th style="width: 45px">序号</th>
            <th>检查内容</th>
            <th style="width: 130px">判定</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(item, index) in checkItems" :key="index">
            <td>{{ index + 1 }}</td>
            <td>{{ item }}</td>
            <td>{{ resultLabel }}</td>
          </tr>
          <tr v-if="!checkItems.length">
            <td colspan="3">未记录检查项</td>
          </tr>
        </tbody>
      </table>
      <div class="linked-print-note">备注：{{ display(firstCheck?.remark) }}</div>
      <div class="linked-print-signs">
        <div>
          首检人：<span>{{ firstCheck?.checker }}</span>
        </div>
        <div>操作员：<span></span></div>
        <div>质量审核：<span></span></div></div
    ></A4Canvas>
    <div v-else v-loading="loading" class="linked-print-loading" />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { operationExecutionApi } from '@/api/production/operationExecution'
import type { OperationExecutionVO } from '@/types/production/operationExecution'
import { dateTime, display, logTemplatePrint } from './shared'
import './print-common.css'
interface CheckRecord {
  checkNo?: string
  checkType?: string
  result?: string
  checkResult?: string
  checkItems?: string | string[]
  checker?: string
  checkTime?: string
  remark?: string
}
const route = useRoute(),
  router = useRouter(),
  execution = ref<OperationExecutionVO | null>(null),
  loading = ref(false),
  printing = ref(false)
const records = computed<CheckRecord[]>(() => {
  try {
    const parsed = JSON.parse(execution.value?.qualityCheckResult || '[]')
    return Array.isArray(parsed) ? parsed : [parsed]
  } catch {
    return []
  }
})
const firstCheck = computed(() =>
  [...records.value].reverse().find((r) => r.checkType?.toUpperCase() === 'FIRST')
)
const isPrint = computed(() => execution.value?.majorCategory?.toUpperCase() === 'PRINT')
const recordNo = computed(() => (isPrint.value ? 'JJX-QR-082' : 'JJX-QR-083'))
const title = computed(() => (isPrint.value ? '印刷首件检查表' : '冲型首件检查表'))
const resultLabel = computed(() => {
  const r = (firstCheck.value?.result || firstCheck.value?.checkResult || '').toUpperCase()
  return r === 'PASS' ? '合格' : r === 'FAIL' ? '不合格' : '-'
})
const checkItems = computed(() => {
  const v = firstCheck.value?.checkItems
  if (Array.isArray(v)) return v
  return v
    ? String(v)
        .split(/[;,\n]/)
        .filter(Boolean)
    : []
})
onMounted(async () => {
  const id = Number(route.query.executionId)
  if (!id) return ElMessage.error('缺少有效的工序执行ID')
  loading.value = true
  try {
    const r = await operationExecutionApi.getInfo(id)
    execution.value = r.data
    if (!firstCheck.value) ElMessage.warning('该工序尚无 FIRST 首检记录')
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
async function print() {
  printing.value = true
  try {
    await logTemplatePrint(recordNo.value)
    window.print()
  } catch (e: any) {
    ElMessage.error(e?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>
