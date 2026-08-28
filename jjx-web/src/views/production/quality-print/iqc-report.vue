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
      <div class="linked-print-title">进料检验报告</div>
      <div class="linked-print-meta">
        <div>记录编号：JJX-QR-037</div>
        <div>收货单：{{ display(info.inboundNo) }}</div>
        <div>收货日期：{{ display(info.inboundDate || info.createTime?.slice(0, 10)) }}</div>
        <div>供应商：{{ display(info.supplierName) }}</div>
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
      </table>
      <div class="linked-print-note">
        检验说明：当前系统无独立 IQC 单，本报告依据采购入库/收货单的检验字段与物料明细生成。<br />检验备注：{{
          display(info.inspectionRemark || info.remark)
        }}
      </div>
      <div class="linked-print-signs">
        <div>
          检验员：<span>{{ info.inspectorName }}</span>
        </div>
        <div>采购：<span></span></div>
        <div>审核：<span></span></div></div
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
import { inboundApi } from '@/api/inventory/inbound'
import type { InboundVO } from '@/types/inventory/inbound'
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { display, logTemplatePrint } from './shared'
import './print-common.css'
const route = useRoute(),
  router = useRouter(),
  info = ref<InboundVO | null>(null),
  loading = ref(false),
  printing = ref(false)
const inspectionLabel = computed(() =>
  info.value?.inspectionResult ? InspectionResultEnum.getLabel(info.value.inspectionResult) : '-'
)
onMounted(async () => {
  const id = String(route.query.inboundId || '')
  if (!id) return ElMessage.error('缺少有效的采购收货单ID')
  loading.value = true
  try {
    const r = await inboundApi.getById(id)
    info.value = r.data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
async function print() {
  printing.value = true
  try {
    await logTemplatePrint('JJX-QR-037')
    window.print()
  } catch (e: any) {
    ElMessage.error(e?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>
