<template>
  <div class="print-page">
    <PrintToolbar :title="`送货单打印-${info?.deliveryNo || ''}`">
      <template #actions>
        <el-radio-group :model-value="layout" size="small" @change="handleLayoutChange">
          <el-radio-button value="qr026">纸版(QR-026)</el-radio-button>
          <el-radio-button value="triplicate">三联纸(241×140)</el-radio-button>
        </el-radio-group>
        <el-checkbox v-model="showAmount">显示金额</el-checkbox>
        <el-checkbox v-model="showWeight">显示单重（g）</el-checkbox>
        <el-button icon="Download" :loading="exporting" :disabled="!info" @click="exportExcel">导出Excel</el-button>
        <el-button type="primary" icon="Printer" :disabled="!info || !items.length" @click="print">打印</el-button>
      </template>
    </PrintToolbar>

    <Qr026DeliverySheet
      v-if="info"
      :info="info"
      :items="items"
      :order-no="orderNo"
      :company="company"
      :show-amount="showAmount"
      :show-weight="showWeight"
      :paper-format="layout === 'triplicate' ? 'triplicate' : 'a4'"
    />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import Qr026DeliverySheet from './Qr026DeliverySheet.vue'
import PrintToolbar from '@/components/print/PrintToolbar.vue'
import { useDeliveryPrintOptions } from './components/useDeliveryPrintOptions'
import { deliveryApi, type SalesDeliveryVO, type SalesDeliveryItem } from '@/api/sales/delivery'
import { orderApi } from '@/api/sales/order'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import { usePrintLayout } from '@/composables/usePrint'
import { download } from '@/utils/format'

type PrintLayout = 'qr026' | 'triplicate'

const route = useRoute()
const info = ref<SalesDeliveryVO>()
const items = ref<SalesDeliveryItem[]>([])
const orderNo = ref('')
const exporting = ref(false)
const deliveryId = Number(route.query.deliveryId)
const { company } = useCompanyConfig()
const { showAmount, showWeight } = useDeliveryPrintOptions()
const { layout, setLayout } = usePrintLayout<PrintLayout>('delivery-print-layout', [
  { value: 'qr026', label: '纸版(QR-026)' },
  { value: 'triplicate', label: '三联纸(241×140)' },
])

function handleLayoutChange(value: string | number | boolean | undefined) {
  if (value === 'qr026' || value === 'triplicate') setLayout(value)
}
async function exportExcel() {
  if (!info.value || exporting.value) return
  exporting.value = true
  try {
    const blob = await deliveryApi.exportExcel(deliveryId, { showAmount: showAmount.value, showWeight: showWeight.value })
    // 全局拦截器直接透传 Blob；业务错误可能仍是 HTTP 200 的 JSON，不能下载成损坏的 Excel。
    if (blob.type.includes('json')) {
      const error = JSON.parse(await blob.text())
      throw new Error(error.msg || '送货单导出失败')
    }
    if (!blob.size) throw new Error('导出文件为空，请重试')
    const deliveryNo = info.value.deliveryNo.replace(/[\\/:*?"<>|]/g, '_')
    download(blob, `送货单_${deliveryNo}.xlsx`)
    ElMessage.success('Excel已导出，可自行调整格式和打印设置')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '送货单导出失败')
  } finally {
    exporting.value = false
  }
}
async function print() {
  await nextTick()
  await document.fonts.ready
  // 口径 D3：打印必留痕（谁/何时/第几次/哪张单）；留痕失败不阻断打印本身
  try {
    await deliveryApi.printLog(deliveryId)
  } catch {
    ElMessage.warning('打印留痕失败（不影响打印）')
  }
  window.print()
}

onMounted(async () => {
  if (!deliveryId) {
    ElMessage.error('发货单ID缺失')
    return
  }
  const detail = await deliveryApi.getById(deliveryId)
  info.value = detail.data || undefined
  // 分批发货（2026-09-21 dev-20260921-039）：送货单只打印「本次发货明细」，不再从订单带全量明细
  const deliveryItems: SalesDeliveryItem[] = detail.data?.items || []
  if (deliveryItems.length) {
    items.value = deliveryItems
  }
  if (!deliveryItems.length) ElMessage.warning('缺少本次发货明细，请核对后打印')
  if (info.value?.orderId) {
    try {
      const order = await orderApi.getOrder(info.value.orderId)
      orderNo.value = order.data?.orderNo || ''
    } catch {
      if (!deliveryItems.length) {
        items.value = []
      }
      ElMessage.warning('订单明细加载失败，可继续打印')
    }
  }
})
</script>

<style scoped>
.print-page { min-height:100vh; background:#eef0f3; padding:20px; }
.print-page :deep(.print-toolbar) { max-width:1100px; flex-wrap:wrap; }
.print-page :deep(.print-toolbar__actions) { flex-wrap:wrap; }
@media print { .print-page { padding:0; background:#fff; } }
</style>
