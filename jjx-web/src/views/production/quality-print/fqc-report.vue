<template>
  <div class="linked-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <el-button type="primary" :loading="printing" :disabled="!info" @click="print"
        >打印</el-button
      >
    </div>
    <A4Canvas v-if="info" :padding-mm="9">
      <FqcReportQr039Print :data="info" />
    </A4Canvas>
    <div v-else v-loading="loading" class="linked-print-loading" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import { qualityApi, type FqcReportPrintVO } from '@/api/production/quality'
import FqcReportQr039Print from './FqcReportQr039Print.vue'
import { logTemplatePrint } from './shared'
import './print-common.css'

const route = useRoute()
const router = useRouter()
const info = ref<FqcReportPrintVO | null>(null)
const loading = ref(false)
const printing = ref(false)

onMounted(async () => {
  const id = Number(route.query.inspectionId)
  if (!id) return ElMessage.error('缺少有效的质检单ID')
  loading.value = true
  try {
    const response = await qualityApi.getFqcReportPrint(id)
    if (!response.data) throw new Error('质检单不存在')
    info.value = response.data
  } catch (error: any) {
    ElMessage.error(error?.message || '加载失败')
  } finally {
    loading.value = false
  }
})

async function print() {
  printing.value = true
  try {
    await logTemplatePrint('JJX-QR-039', 'production_quality', info.value?.inspectionId)
    window.print()
  } catch (error: any) {
    ElMessage.error(error?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>
