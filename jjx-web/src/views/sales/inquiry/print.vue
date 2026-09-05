<template>
  <div class="inquiry-print-page">
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.inquiryNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" :disabled="!info" @click="handlePrint">
        打印
      </el-button>
    </div>

    <A4Canvas v-if="info" :padding-mm="15">
      <div class="inquiry-company-header">
        <PrintCompanyHeader variant="center" />
        <img
          v-if="qrDataUrl"
          :src="qrDataUrl"
          class="inquiry-qrcode"
          alt="询价单二维码"
          title="扫码识别询价单号"
        />
      </div>
      <div class="doc-title">询价单</div>

      <div class="doc-info">
        <div><span class="info-label">询价单号</span>{{ info.inquiryNo || '-' }}</div>
        <div><span class="info-label">询价日期</span>{{ info.inquiryDate || '-' }}</div>
        <div><span class="info-label">客户名称</span>{{ info.customerName || '-' }}</div>
        <div><span class="info-label">联系人</span>{{ contactText }}</div>
      </div>

      <table class="requirement-table">
        <tbody>
          <tr>
            <th>产品编码</th>
            <td>{{ info.productCode || '-' }}</td>
            <th>产品名称</th>
            <td>{{ info.productName || '-' }}</td>
          </tr>
          <tr>
            <th>数量</th>
            <td>{{ info.expectedQuantity ?? '-' }}</td>
            <th>图纸标识</th>
            <td>{{ drawingText }}</td>
          </tr>
          <tr>
            <th>产品描述</th>
            <td colspan="3" class="multiline-content">{{ info.productDescription || '-' }}</td>
          </tr>
          <tr>
            <th>特殊要求</th>
            <td colspan="3" class="multiline-content">{{ info.specialRequirements || '-' }}</td>
          </tr>
          <tr>
            <th>备注</th>
            <td colspan="3" class="multiline-content">{{ info.remark || '-' }}</td>
          </tr>
        </tbody>
      </table>

      <div class="doc-signatures">
        <span>申请人：{{ info.salesPersonName || '________________' }}</span>
        <span>审核人：________________</span>
        <span>日期：________________</span>
      </div>
    </A4Canvas>

    <div v-else v-loading="loading" class="loading-area"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { inquiryApi, type InquiryBase } from '@/api/sales/inquiry'
import { createQualityTemplatePrintLog } from '@/api/production/qualityTemplate'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const inquiryId = Number(route.query.inquiryId)
const info = ref<InquiryBase>()
const loading = ref(false)
const qrDataUrl = ref('')

const contactText = computed(() => {
  const person = info.value?.contactPerson || ''
  const phone = info.value?.contactPhone || ''
  return [person, phone].filter(Boolean).join(' ') || '-'
})

const drawingText = computed(() => (info.value?.hasDrawing ? '有图纸' : '无图纸'))

/** 生成询价单二维码；使用高分辨率源图保证纸张打印清晰度 */
async function genQr() {
  if (!info.value?.inquiryNo) return
  try {
    qrDataUrl.value = await QRCode.toDataURL(info.value.inquiryNo, { width: 256, margin: 1 })
  } catch {
    qrDataUrl.value = ''
  }
}

async function handlePrint() {
  if (!info.value) return
  try {
    await createQualityTemplatePrintLog(65, 'sales_inquiry', inquiryId)
    window.print()
  } catch {
    ElMessage.error('打印留痕失败，请重试')
  }
}

async function loadData() {
  if (!Number.isFinite(inquiryId) || inquiryId <= 0) {
    ElMessage.error('询价单ID缺失')
    return
  }

  loading.value = true
  try {
    const response = await inquiryApi.getInfo(inquiryId)
    info.value = response.data || undefined
    await genQr()
  } catch {
    ElMessage.error('询价单打印数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.inquiry-print-page {
  min-height: 100vh;
  padding: 20px;
  background: #eef0f3;
}

.print-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 794px;
  margin: 0 auto 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  color: #606266;
  font-size: 14px;
}

.inquiry-company-header {
  position: relative;
}

.inquiry-qrcode {
  position: absolute;
  top: 0;
  right: 0;
  width: 72px;
  height: 72px;
  padding: 3px;
  border: 1px solid #dcdfe6;
  background: #fff;
  box-sizing: border-box;
}

.doc-title {
  margin: 14px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #2b5aa7;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 8px;
  text-align: center;
}

.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 24px;
  margin-bottom: 14px;
  font-size: 11px;
}

.info-label {
  display: inline-block;
  width: 70px;
  color: #888;
}

.requirement-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 11px;
}

.requirement-table th,
.requirement-table td {
  padding: 8px;
  border: 1px solid #b8bec8;
  overflow-wrap: anywhere;
}

.requirement-table th {
  width: 13%;
  background: #eef3fa;
  color: #303133;
  font-weight: 600;
  text-align: center;
}

.multiline-content {
  min-height: 48px;
  white-space: pre-wrap;
}

.doc-signatures {
  display: flex;
  justify-content: space-between;
  margin-top: 55px;
  font-size: 11px;
}

.loading-area {
  height: 400px;
}

@media print {
  .inquiry-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
