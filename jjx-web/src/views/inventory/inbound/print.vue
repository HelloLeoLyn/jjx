<template>
  <div class="linked-print-page inbound-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <span class="toolbar-tip">打印预览 - {{ info?.inboundNo || '' }}</span>
      <el-button type="primary" :loading="printing" :disabled="!info" @click="print"
        >打印</el-button
      >
    </div>

    <A4Canvas v-if="info" :padding-mm="14">
      <section class="system-sheet">
        <PrintCompanyHeader variant="center" />
        <div class="linked-print-title">入库明细</div>
        <div class="linked-print-meta">
          <div>记录编号：JJX-QR-037</div>
          <div>验收单号：{{ display(info.inboundNo) }}</div>
          <div>报告编号：{{ display(info.inboundNo) }}</div>
          <div>收货日期：{{ display(reportDate) }}</div>
          <div>供应厂商：{{ display(info.supplierName) }}</div>
          <div>来料批量：{{ display(incomingQuantity) }}</div>
          <div>抽检数量：{{ display(sampledQuantity) }}</div>
          <div>来源单号：{{ display(info.sourceNo) }}</div>
          <div>检验结果：{{ inspectionLabel }}</div>
        </div>
        <table class="linked-print-table">
          <thead>
            <tr>
              <th style="width: 42px">序号</th>
              <th>物料编码</th>
              <th>物料名称</th>
              <th>规格</th>
              <th>批次</th>
              <th>收货数</th>
              <th>合格数</th>
              <th>拒收数</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in info.items || []"
              :key="item.inboundItemId || item.itemId || index"
            >
              <td>{{ index + 1 }}</td>
              <td>{{ display(item.materialCode) }}</td>
              <td>{{ display(item.materialName) }}</td>
              <td>{{ display(item.specification) }}</td>
              <td>{{ display(item.batchNo) }}</td>
              <td>{{ display(item.quantity) }} {{ item.unit || '' }}</td>
              <td>{{ display(item.qualifiedQuantity) }}</td>
              <td>{{ display(item.rejectedQuantity) }}</td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <th colspan="5">合计</th>
              <td>{{ display(incomingQuantity) }}</td>
              <td>{{ display(qualifiedTotal) }}</td>
              <td>{{ display(rejectedTotal) }}</td>
            </tr>
          </tfoot>
        </table>
        <div class="linked-print-note">
          检验说明：当前系统无独立 IQC 单，本报告依据采购入库/收货单的检验字段与物料明细生成。<br />
          检验备注：{{ display(info.inspectionRemark || info.remark) }}
        </div>
        <div class="linked-print-signs">
          <div>
            检验员：<span>{{ info.inspectorName }}</span>
          </div>
          <div>采购：<span></span></div>
          <div>审核：<span></span></div>
        </div>
      </section>
    </A4Canvas>
    <div v-else v-loading="loading" class="linked-print-loading" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { inboundApi } from '@/api/inventory/inbound'
import type { InboundVO } from '@/types/inventory/inbound'
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { display, logTemplatePrint } from '@/views/production/quality-print/shared'
import '@/views/production/quality-print/print-common.css'

const route = useRoute()
const router = useRouter()
const info = ref<InboundVO | null>(null)
const loading = ref(false)
const printing = ref(false)
const reportDate = computed(
  () => info.value?.inboundDate || info.value?.createTime?.slice(0, 10) || ''
)
const inspectionLabel = computed(() =>
  info.value?.inspectionResult ? InspectionResultEnum.getLabel(info.value.inspectionResult) : '-'
)
const incomingQuantity = computed(() => sumItems('quantity') ?? '')
const sampledQuantity = computed(() => {
  const qualified = sumItems('qualifiedQuantity'),
    rejected = sumItems('rejectedQuantity')
  return qualified === undefined && rejected === undefined ? '' : (qualified || 0) + (rejected || 0)
})
const qualifiedTotal = computed(() => sumItems('qualifiedQuantity') ?? '')
const rejectedTotal = computed(() => sumItems('rejectedQuantity') ?? '')

function sumItems(field: 'quantity' | 'qualifiedQuantity' | 'rejectedQuantity') {
  const values = (info.value?.items || [])
    .map((item) => item[field])
    .filter((value): value is number => value !== null && value !== undefined)
  return values.length ? values.reduce((sum, value) => sum + Number(value), 0) : undefined
}
onMounted(async () => {
  const inboundId = String(route.params.id || '')
  if (!inboundId) return ElMessage.error('缺少有效的入库单ID')
  loading.value = true
  try {
    info.value = (await inboundApi.getById(inboundId)).data
  } catch (error: any) {
    ElMessage.error(error?.message || '加载入库单失败')
  } finally {
    loading.value = false
  }
})
async function print() {
  printing.value = true
  try {
    await logTemplatePrint('JJX-QR-037')
    window.print()
  } catch (error: any) {
    ElMessage.error(error?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>

<style scoped>
.linked-print-toolbar {
  gap: 14px;
}
.toolbar-tip {
  margin-right: auto;
  font-size: 14px;
  color: #606266;
}
.system-sheet {
  min-height: 270mm;
  box-sizing: border-box;
}
@media print {
  .inbound-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
