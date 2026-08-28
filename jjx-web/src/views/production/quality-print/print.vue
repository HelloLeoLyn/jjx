<template>
  <div class="quality-record-print-page">
    <div class="print-toolbar no-print">
      <div><el-button @click="router.back()">返回</el-button><span class="toolbar-tip">打印预览 - {{ info?.recordNo || '' }}</span></div>
      <el-button type="primary" icon="Printer" :loading="printing" :disabled="!info" @click="handlePrint">打印</el-button>
    </div>

    <A4Canvas v-if="info" :padding-mm="14">
      <PrintCompanyHeader variant="center" />
      <div class="doc-title">{{ info.recordName }}</div>
      <div class="doc-info">
        <div><span>记录编号</span>{{ info.recordNo }}</div>
        <div><span>版次</span>{{ info.version }}</div>
        <div><span>主管部门</span>{{ info.ownerDept || '-' }}</div>
        <div><span>保存期限</span>{{ info.retentionYears }} 年</div>
        <div><span>打印日期</span>{{ printDate }}</div>
        <div><span>打印人</span>{{ printerName || '-' }}</div>
      </div>
      <table class="record-table">
        <thead><tr><th class="seq">序号</th><th class="project">项目</th><th>内容</th><th class="result">结果</th><th class="remark">备注</th></tr></thead>
        <tbody><tr v-for="index in 12" :key="index"><td>{{ index }}</td><td></td><td></td><td></td><td></td></tr></tbody>
      </table>
      <div class="doc-signs"><div>记录人：<span></span></div><div>审核人：<span></span></div><div>日期：<span></span></div></div>
    </A4Canvas>
    <div v-else v-loading="loading" class="loading-panel"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { createQualityTemplatePrintLog, getQualityTemplate, type QualityTemplate } from '@/api/production/qualityTemplate'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { useUserStore } from '@/store/modules/user'
import { QualityTemplateCategory, QualityTemplateStatus } from '@/enums/production/QualityTemplateEnum'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const info = ref<QualityTemplate | null>(null)
const loading = ref(false)
const printing = ref(false)
const printDate = dayjs().format('YYYY-MM-DD')
const printerName = computed(() => userStore.nickName || userStore.userName)

async function loadData() {
  const templateId = Number(route.query.templateId)
  if (!Number.isInteger(templateId) || templateId <= 0) { ElMessage.error('缺少有效的模板ID'); return }
  loading.value = true
  try {
    const res: any = await getQualityTemplate(templateId)
    const template = res.data as QualityTemplate | undefined
    if (template?.status !== QualityTemplateStatus.ACTIVE) {
      ElMessage.error('仅生效模板可打印')
      return
    }
    if (template.category === QualityTemplateCategory.DATA) {
      ElMessage.warning('数据联动模板将在后续版本开放打印')
      return
    }
    info.value = template || null
    if (!info.value) ElMessage.error('加载质量记录模板失败')
  } finally { loading.value = false }
}
async function handlePrint() {
  if (!info.value?.id) return
  printing.value = true
  try {
    await createQualityTemplatePrintLog(info.value.id)
    window.print()
  } catch { ElMessage.error('打印留痕失败，本次未调起打印') }
  finally { printing.value = false }
}
onMounted(loadData)
</script>

<style scoped>
.quality-record-print-page { min-height: 100vh; background: #eef1f5; padding: 20px 0 36px; }
.print-toolbar { width: 210mm; box-sizing: border-box; margin: 0 auto 16px; display: flex; align-items: center; justify-content: space-between; }
.toolbar-tip { margin-left: 14px; color: #606266; }
.doc-title { margin: 12px 0 14px; text-align: center; font-size: 24px; font-weight: 700; letter-spacing: 4px; }
.doc-info { display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid #222; border-bottom: 0; font-size: 13px; }
.doc-info > div { min-height: 32px; display: flex; align-items: center; border-right: 1px solid #222; border-bottom: 1px solid #222; }
.doc-info > div:nth-child(3n) { border-right: 0; }
.doc-info span { width: 72px; align-self: stretch; display: inline-flex; align-items: center; justify-content: center; margin-right: 8px; border-right: 1px solid #222; font-weight: 600; background: #f5f5f5; }
.record-table { width: 100%; border-collapse: collapse; table-layout: fixed; font-size: 13px; }
.record-table th, .record-table td { border: 1px solid #222; text-align: center; }
.record-table th { height: 34px; background: #f5f5f5; }
.record-table td { height: 37px; }
.record-table .seq { width: 48px; }.record-table .project { width: 125px; }.record-table .result { width: 90px; }.record-table .remark { width: 110px; }
.doc-signs { display: grid; grid-template-columns: repeat(3, 1fr); gap: 28px; margin-top: 24px; font-size: 14px; }
.doc-signs div { display: flex; align-items: flex-end; }.doc-signs span { flex: 1; height: 24px; border-bottom: 1px solid #222; }
.loading-panel { height: 400px; }
@media print { .quality-record-print-page { background: #fff; padding: 0; } }
</style>
