<template>
  <div class="production-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.orderNo || '' }}</span>
        <div class="layout-selector">
          <span>版式</span>
          <el-radio-group v-model="printLayout" size="small" @change="handleLayoutChange">
            <el-radio-button value="system">系统版</el-radio-button>
            <el-radio-button value="qr005">纸版(QR-005)</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- A4 画布（干净页面） -->
    <A4Canvas :padding-mm="15" v-if="info">
      <template v-if="printLayout === 'system'">
        <!-- 公司抬头 -->
        <PrintCompanyHeader variant="center" />

        <!-- 单据标题 + 工单二维码（扫码枪扫工单号定位，DEV-001） -->
        <div class="doc-title-row">
          <div class="doc-title">{{ isPlan ? '生 产 计 划' : '生 产 工 单' }}</div>
          <img
            v-if="qrDataUrl"
            :src="qrDataUrl"
            class="doc-qrcode"
            alt="工单二维码"
            title="扫码定位工单"
          />
        </div>

        <!-- 信息区 -->
        <div class="doc-info">
          <div class="info-item"><span class="info-label">单号</span>{{ info.orderNo }}</div>
          <div class="info-item">
            <span class="info-label">类型</span>{{ info.orderTypeDesc || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">销售订单</span>{{ info.salesOrderNo || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">产品编码</span>{{ info.productCode || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">产品名称</span>{{ info.productName || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">产品规格</span>{{ info.productSpec || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">计划开始</span>{{ info.planStartDate || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">计划结束</span>{{ info.planEndDate || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">单据状态</span>{{ info.orderStatusDesc || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">审批状态</span>{{ info.approvalStatusDesc || '-' }}
          </div>
        </div>

        <!-- 数量汇总 -->
        <div class="doc-summary">
          <div class="summary-item">
            <span class="summary-label">计划数量</span>
            <span class="summary-value">{{ fmtNum(info.plannedQuantity) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">已完成</span>
            <span class="summary-value">{{ fmtNum(info.completedQuantity) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">剩余</span>
            <span class="summary-value">{{ fmtNum(info.remainingQuantity) }}</span>
          </div>
          <div class="summary-item">
            <span class="summary-label">进度</span>
            <span class="summary-value">{{
              info.completionPercentage != null ? info.completionPercentage + '%' : '-'
            }}</span>
          </div>
        </div>

        <!-- 工艺路线 -->
        <div v-if="info.routingCode || info.routingName" class="doc-route">
          工艺路线：{{ info.routingCode || '' }} {{ info.routingName || '' }}
        </div>

        <!-- 备注 -->
        <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

        <!-- 签名区 -->
        <div class="doc-signs">
          <div class="sign-item">
            <div class="sign-line">车间负责人：</div>
            <div class="sign-underline"></div>
          </div>
          <div class="sign-item">
            <div class="sign-line">计划员：</div>
            <div class="sign-underline"></div>
          </div>
          <div class="sign-item">
            <div class="sign-line">日期：</div>
            <div class="sign-underline"></div>
          </div>
        </div>
      </template>

      <section v-else class="qr005-layout">
        <img
          v-if="qrDataUrl"
          :src="qrDataUrl"
          class="qr005-order-qrcode"
          alt="工单二维码"
          title="扫码定位工单"
        />

        <header class="qr005-company-header">
          <div class="qr005-company-name">{{ company.name || '深圳市精捷信科技有限公司' }}</div>
          <div v-if="company.address">地址：{{ company.address }}</div>
          <div v-if="company.phone">电话：{{ company.phone }}</div>
        </header>

        <div class="qr005-title">制&nbsp;&nbsp;造&nbsp;&nbsp;指&nbsp;&nbsp;令&nbsp;&nbsp;单</div>
        <div class="qr005-meta">
          <div>编号：JJX-QR-005</div>
          <div>日期：{{ qr005Date }}</div>
        </div>

        <table class="qr005-items">
          <thead>
            <tr>
              <th style="width: 6%">NO</th>
              <th style="width: 20%">品名</th>
              <th style="width: 12%">订单数量</th>
              <th style="width: 13%">交期</th>
              <th style="width: 15%">机种号</th>
              <th style="width: 14%">订单号</th>
              <th style="width: 10%">生产批号</th>
              <th style="width: 10%">库存</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td class="col-center">1</td>
              <td>{{ info.productName || '-' }}</td>
              <td class="col-right">{{ fmtNum(info.plannedQuantity) }}</td>
              <td class="col-center">{{ info.planEndDate || '-' }}</td>
              <td>{{ info.productCode || '-' }}</td>
              <td>{{ info.salesOrderNo || '-' }}</td>
              <td class="col-center">-</td>
              <td class="col-center">-</td>
            </tr>
            <tr class="qr005-remark-row">
              <th>备注</th>
              <td colspan="7">{{ info.remark || '' }}</td>
            </tr>
          </tbody>
        </table>
      </section>
    </A4Canvas>

    <div v-else v-loading="true" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductionOrderDetail } from '@/api/production/order'
import { createQualityTemplatePrintLog } from '@/api/production/qualityTemplate'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import QRCode from 'qrcode'

const route = useRoute()
const router = useRouter()
const { company } = useCompanyConfig()

type PrintLayout = 'system' | 'qr005'
const PRINT_LAYOUT_KEY = 'production-order-print-layout'

const storedLayout = localStorage.getItem(PRINT_LAYOUT_KEY)
const printLayout = ref<PrintLayout>(storedLayout === 'qr005' ? 'qr005' : 'system')

const info = ref<any>(null)
const loading = ref(false)
const qrDataUrl = ref('')

// 计划类型（orderType=PLAN 时显示"生产计划"标题）
const isPlan = computed(() => info.value?.orderType === 'PLAN')
const qr005Date = computed(
  () => info.value?.planStartDate || new Date().toLocaleDateString('sv-SE')
)

function handleLayoutChange(value: string | number | boolean | undefined) {
  localStorage.setItem(PRINT_LAYOUT_KEY, value === 'qr005' ? 'qr005' : 'system')
}

const fmtNum = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN')
}

async function loadData() {
  const orderId = route.params.id as string
  if (!orderId) {
    ElMessage.error('缺少工单ID')
    return
  }
  loading.value = true
  try {
    const res: any = await getProductionOrderDetail(orderId)
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载工单失败')
    }
  } catch {
    ElMessage.error('加载工单失败')
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  // 打印留痕（1296）：5 = JJX-QR-005 制造指令单
  const orderId = route.params.id as string
  createQualityTemplatePrintLog(5, 'production_order', Number(orderId))
    .then(() => window.print())
    .catch(() => {
      ElMessage.error('打印留痕失败，请重试')
    })
}

/** 生成工单二维码（内容=工单号，扫码枪识别后定位工单） */
async function genQr() {
  if (!info.value?.orderNo) return
  try {
    qrDataUrl.value = await QRCode.toDataURL(info.value.orderNo, { width: 96, margin: 1 })
  } catch {
    qrDataUrl.value = ''
  }
}

onMounted(async () => {
  await loadData()
  await genQr()
})
</script>

<style scoped>
.production-print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}

.print-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  font-size: 14px;
  color: #606266;
}

.layout-selector {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #606266;
}

/* 画布内容样式 */
/* 单据标题行（标题居中 + 二维码右上角，DEV-001） */
.doc-title-row {
  position: relative;
  padding-right: 96px; /* 给右侧二维码留位，避免遮挡标题 */
}

/* 工单二维码（扫码枪扫工单号定位） */
.doc-qrcode {
  position: absolute;
  top: 0;
  right: 0;
  width: 72px;
  height: 72px;
  border: 1px solid #dcdfe6;
  padding: 3px;
  background: #fff;
}

.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 8px;
  margin: 14px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #2b5aa7;
}

.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
  margin-bottom: 12px;
  font-size: 11px;
}

.info-item {
  display: flex;
}

.info-label {
  width: 70px;
  color: #888;
  flex-shrink: 0;
}

.doc-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.summary-item {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 10px;
  text-align: center;
  background: #f7f9fc;
}

.summary-label {
  display: block;
  font-size: 10px;
  color: #888;
}

.summary-value {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #2b5aa7;
  margin-top: 2px;
}

.doc-route {
  font-size: 11px;
  color: #555;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  background: #f7f9fc;
  margin-bottom: 12px;
}

.doc-remark {
  font-size: 10px;
  color: #555;
  margin-bottom: 20px;
}

.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding: 0 20px;
}

.sign-item {
  width: 30%;
  text-align: center;
  font-size: 11px;
}

.sign-line {
  padding-bottom: 4px;
}

.sign-underline {
  border-bottom: 1px solid #999;
}

.qr005-layout {
  position: relative;
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 11px;
}

.qr005-order-qrcode {
  position: absolute;
  top: 0;
  right: 0;
  width: 64px;
  height: 64px;
  padding: 2px;
  border: 1px solid #000;
  background: #fff;
  box-sizing: border-box;
}

.qr005-company-header {
  min-height: 64px;
  padding: 0 72px;
  line-height: 1.55;
  text-align: center;
}

.qr005-company-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}

.qr005-title {
  margin: 8px 0 4px;
  text-align: center;
  font-family: SimHei, '黑体', sans-serif;
  font-size: 20px;
  font-weight: 700;
}

.qr005-meta {
  margin: 0 0 5px auto;
  width: 145px;
  font-size: 11px;
  line-height: 1.5;
}

.qr005-items {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 10px;
}

.qr005-items th,
.qr005-items td {
  height: 28px;
  padding: 3px 4px;
  border: 1px solid #000;
  overflow-wrap: anywhere;
}

.qr005-items th {
  text-align: center;
  font-size: 11px;
  font-weight: 700;
}

.qr005-remark-row td {
  height: 72px;
  vertical-align: top;
}

@media print {
  .no-print {
    display: none !important;
  }

  .production-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
