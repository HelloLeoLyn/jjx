<template>
  <div class="lot-report-page">
    <div class="toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <el-tag v-if="info" type="info">{{ info.lotNo }} · {{ info.reportTitle }}</el-tag>
      <el-button type="primary" :loading="printing" :disabled="!info" @click="print">打印</el-button>
    </div>
    <A4Canvas v-if="info" :padding-mm="10">
      <LotReportSheet :data="info" />
    </A4Canvas>
    <div v-else v-loading="loading" class="loading" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import LotReportSheet from './LotReportSheet.vue'
import { qualityLotApi } from '@/api/quality/lot'
import { logTemplatePrint } from '@/views/production/quality-print/shared'
import '@/views/production/quality-print/print-common.css'

const route = useRoute()
const router = useRouter()
const info = ref<Record<string, any> | null>(null)
const loading = ref(false)
const printing = ref(false)

onMounted(async () => {
  const lotId = Number(route.query.lotId)
  if (!lotId) {
    ElMessage.error('缺少检验批ID')
    return
  }
  loading.value = true
  try {
    const res: any = await qualityLotApi.report(lotId)
    info.value = res?.data || null
    if (!info.value) throw new Error('检验批不存在')
  } catch (e: any) {
    ElMessage.error(e?.message || '加载检验报告失败')
  } finally {
    loading.value = false
  }
})

async function print() {
  if (!info.value) return
  printing.value = true
  try {
    await logTemplatePrint(String(info.value.recordNo || ''), 'quality_lot', Number(info.value.lotId))
    window.print()
  } catch (e: any) {
    ElMessage.error(e?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>

<style scoped>
.lot-report-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}
.toolbar {
  max-width: 794px;
  margin: 0 auto 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
.loading {
  min-height: 200px;
}
@media print {
  .lot-report-page {
    padding: 0;
    background: #fff;
  }
}
</style>
