<template>
  <div class="work-ticket-print-page">
    <div class="print-toolbar no-print">
      <div>
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">工票打印 - {{ info?.reportNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" :loading="printing" :disabled="!info" @click="handlePrint">
        打印
      </el-button>
    </div>

    <A4Canvas v-if="info" :padding-mm="14">
      <PrintCompanyHeader variant="center" />
      <div class="doc-title">生产报工单（工票）</div>

      <!-- 单据头 -->
      <div class="doc-info">
        <div><span>报工单号</span>{{ info.reportNo }}</div>
        <div><span>工单号</span>{{ info.orderNo }}</div>
        <div><span>报工状态</span>{{ info.reportStatusLabel }}</div>
        <div><span>报工时间</span>{{ fmtTime(info.reportTime) }}</div>
        <div><span>报工人</span>{{ info.reporterName }}</div>
        <div v-if="info.proxyName"><span>代报人</span>{{ info.proxyName }}</div>
        <div><span>设备</span>{{ info.equipmentName || '-' }}</div>
        <div><span>打印日期</span>{{ printDate }}</div>
      </div>

      <!-- 数量明细 -->
      <table class="record-table">
        <thead>
          <tr>
            <th class="seq">序号</th>
            <th>项目</th>
            <th class="qty">数量</th>
            <th class="qty">工时</th>
            <th class="remark">备注</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>1</td>
            <td>合格数量</td>
            <td>{{ fmtQty(info.qualifiedQuantity) }}</td>
            <td>人工 {{ fmtQty(info.laborHours) }} h</td>
            <td rowspan="3">{{ info.remark || '-' }}</td>
          </tr>
          <tr>
            <td>2</td>
            <td>不良数量</td>
            <td>{{ fmtQty(info.defectiveQuantity) }}</td>
            <td>机器 {{ fmtQty(info.machineHours) }} h</td>
          </tr>
          <tr>
            <td>3</td>
            <td>不良原因</td>
            <td colspan="2">{{ info.defectReason || '-' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 工作时间 -->
      <div class="doc-time">
        <div>开始时间：{{ fmtTime(info.workStartTime) }}</div>
        <div>结束时间：{{ fmtTime(info.workEndTime) }}</div>
      </div>

      <!-- 审批链 -->
      <div class="doc-signs">
        <div>报工人：{{ info.reporterName }}</div>
        <div v-if="info.reviewerName">审批人：{{ info.reviewerName }}<span v-if="info.reviewTime">（{{ fmtTime(info.reviewTime) }}）</span></div>
        <div v-if="info.reviewRemark">审批意见：{{ info.reviewRemark }}</div>
        <div>日期：<span></span></div>
      </div>
    </A4Canvas>
    <div v-else v-loading="loading" class="loading-panel"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getWorkReport, type WorkReportVO } from '@/api/production/workReport'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'

const route = useRoute()
const router = useRouter()
const info = ref<WorkReportVO | null>(null)
const loading = ref(false)
const printing = ref(false)
const printDate = dayjs().format('YYYY-MM-DD')

function fmtTime(v?: string | null): string {
  return v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-'
}
function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : String(n)
}

async function loadData() {
  const reportId = Number(route.params.id)
  if (!Number.isInteger(reportId) || reportId <= 0) {
    ElMessage.error('缺少有效的报工单ID')
    return
  }
  loading.value = true
  try {
    const res: any = await getWorkReport(reportId)
    info.value = (res?.data as WorkReportVO) || null
    if (!info.value) ElMessage.error('加载报工单失败')
  } finally {
    loading.value = false
  }
}

async function handlePrint() {
  printing.value = true
  try {
    // 打印前无独立留痕接口，直接调起打印（后续可接入 print-log）
    window.print()
  } catch {
    ElMessage.error('调起打印失败')
  } finally {
    printing.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.work-ticket-print-page {
  padding: 16px;
}
.print-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.toolbar-tip {
  margin-left: 12px;
  color: #909399;
  font-size: 13px;
}
.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  margin: 8px 0 12px;
}
.doc-info {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 6px 16px;
  font-size: 13px;
  margin-bottom: 12px;
}
.doc-info > div span {
  display: inline-block;
  min-width: 64px;
  color: #909399;
  margin-right: 4px;
}
.record-table {
  width: 100%;
  border-collapse: collapse;
  margin-bottom: 12px;
  font-size: 13px;
}
.record-table th,
.record-table td {
  border: 1px solid #333;
  padding: 6px 8px;
  text-align: left;
}
.record-table th {
  background: #f5f5f5;
}
.record-table .seq {
  width: 48px;
}
.record-table .qty {
  width: 110px;
}
.record-table .remark {
  width: 220px;
}
.doc-time {
  font-size: 13px;
  margin-bottom: 12px;
  display: flex;
  gap: 32px;
}
.doc-signs {
  display: flex;
  gap: 48px;
  font-size: 13px;
  margin-top: 24px;
}
.loading-panel {
  min-height: 200px;
}
</style>
