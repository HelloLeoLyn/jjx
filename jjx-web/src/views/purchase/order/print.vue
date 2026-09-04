<template>
  <div class="purchase-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">打印预览 - {{ info?.orderNo || '' }}</span>
        <div class="layout-selector">
          <span>版式</span>
          <el-radio-group v-model="printLayout" size="small" @change="handleLayoutChange">
            <el-radio-button value="a4">系统版</el-radio-button>
            <el-radio-button value="qr024">纸版(QR-024)</el-radio-button>
          </el-radio-group>
        </div>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- A4 画布（干净页面） -->
    <A4Canvas :padding-mm="15" v-if="info">
      <template v-if="printLayout === 'a4'">
        <!-- 公司抬头 -->
        <PrintCompanyHeader variant="center" />

        <!-- 单据标题 -->
        <div class="doc-title">采 购 订 单</div>

        <!-- 信息区 -->
        <div class="doc-info">
          <div class="info-item"><span class="info-label">订单号</span>{{ info.orderNo }}</div>
          <div class="info-item">
            <span class="info-label">订单日期</span>{{ info.orderDate || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">供应商</span>{{ info.supplierName || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">交货日期</span>{{ info.expectedDeliveryDate || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">币种</span>{{ info.currency || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">合同号</span>{{ info.contractNo || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">交货方式</span>{{ info.deliveryMethod || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">交货地址</span>{{ info.deliveryAddress || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">审批状态</span
            >{{ info.approvalStatusName || info.approvalStatus || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">创建时间</span>{{ info.createTime || '-' }}
          </div>
        </div>

        <!-- 明细表格 -->
        <table class="doc-items">
          <thead>
            <tr>
              <th style="width: 5%">序号</th>
              <th style="width: 12%">物料编码</th>
              <th>物料名称</th>
              <th style="width: 10%">规格</th>
              <th style="width: 6%">单位</th>
              <th style="width: 10%">数量</th>
              <th style="width: 12%">单价</th>
              <th style="width: 13%">金额</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in itemsList" :key="idx">
              <td class="col-center">{{ idx + 1 }}</td>
              <td>{{ item.materialCode }}</td>
              <td>{{ item.materialName }}</td>
              <td>{{ item.materialSpec || '-' }}</td>
              <td class="col-center">{{ item.unit || '-' }}</td>
              <td class="col-right">{{ fmtNum(item.quantity) }}</td>
              <td class="col-right">{{ fmtMoney(item.unitPrice) }}</td>
              <td class="col-right">{{ fmtMoney(item.amount) }}</td>
            </tr>
            <tr v-if="!itemsList.length">
              <td colspan="8" class="col-center">无明细</td>
            </tr>
          </tbody>
        </table>

        <!-- 合计 -->
        <div class="doc-total-row">
          <span>物料种类：{{ itemsList.length }} 项</span>
          <span>订单总金额：{{ fmtMoney(info.orderTotalAmount) }}</span>
        </div>

        <!-- 备注 -->
        <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

        <!-- 签名区 -->
        <div class="doc-signs">
          <div class="sign-item">
            <div class="sign-line">供应商确认：</div>
            <div class="sign-underline"></div>
          </div>
          <div class="sign-item">
            <div class="sign-line">采购员：</div>
            <div class="sign-underline"></div>
          </div>
          <div class="sign-item">
            <div class="sign-line">日期：</div>
            <div class="sign-underline"></div>
          </div>
        </div>
      </template>

      <section v-else class="qr024-layout">
        <header class="qr024-company-header">
          <div class="qr024-company-name">{{ company.name || '深圳市精捷信科技有限公司' }}</div>
          <div v-if="company.address">地址：{{ company.address }}</div>
          <div>
            <span v-if="company.phone">电话：{{ company.phone }}</span>
          </div>
          <div v-if="company.email">E-mail: {{ company.email }}</div>
        </header>

        <div class="qr024-title">订&nbsp;&nbsp;购&nbsp;&nbsp;单</div>

        <!-- <div class="qr024-order-no">订单号码：{{ info.orderNo }}</div> -->
        <div class="qr024-info-grid">
          <div><span class="qr024-label"></span></div>
          <div><span class="qr024-label">订单号码：</span>{{ info.orderNo || '-' }}</div>
          <div><span class="qr024-label">厂商：</span>{{ info.supplierName || '-' }}</div>
          <div><span class="qr024-label">订货时间：</span>{{ info.orderDate || '-' }}</div>
          <div><span class="qr024-label">联系人：</span>{{ supplier?.contactPerson || '-' }}</div>
          <div>
            <span class="qr024-label">交货时间：</span>{{ info.expectedDeliveryDate || '-' }}
          </div>
          <div>
            <span class="qr024-label">TEL：</span>{{ supplier?.phone || '-' }}
            <span class="qr024-fax">FAX：</span>
          </div>
          <div><span class="qr024-label">交易方式：</span>{{ paymentTermsLabel }}</div>
        </div>

        <table class="qr024-items">
          <thead>
            <tr>
              <th style="width: 7%">项次</th>
              <th style="width: 20%">品名</th>
              <th style="width: 19%">规格</th>
              <th style="width: 8%">单位</th>
              <th style="width: 10%">数量</th>
              <th style="width: 11%">单价</th>
              <th style="width: 13%">金额</th>
              <th style="width: 12%">备注</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(item, idx) in itemsList" :key="idx">
              <td class="col-center">{{ idx + 1 }}</td>
              <td>{{ item.materialName || '' }}</td>
              <td>{{ item.materialSpec || '' }}</td>
              <td class="col-center">{{ item.unit || '' }}</td>
              <td class="col-right">{{ fmtNum(item.quantity) }}</td>
              <td class="col-right">{{ fmtMoney(item.unitPrice) }}</td>
              <td class="col-right">{{ fmtMoney(item.amount) }}</td>
              <td></td>
            </tr>
            <tr v-if="!itemsList.length">
              <td></td>
              <td colspan="6" class="col-center">以下空白</td>
              <td></td>
            </tr>
            <tr class="qr024-total-row">
              <td colspan="6"></td>
              <th>合计:</th>
              <td class="col-right">{{ fmtMoney(info.orderTotalAmount) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="qr024-terms">
          <div><span style="font: 16px sans-serif">交易条款：</span></div>
          <div v-for="term in tradeTerms" :key="term">{{ term }}</div>
        </div>

        <div class="qr024-code">JJX-QR-024</div>
        <div class="qr024-signatures">
          <div>供应商回签：<span class="qr024-sign-line2"></span></div>
          <div>经理审核：<span class="qr024-sign-line2"></span></div>
          <div>
            制表人：<span class="qr024-maker2">{{ makerName }}</span>
          </div>
        </div>
      </section>
    </A4Canvas>

    <div v-else v-loading="true" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getOrder } from '@/api/purchase/order'
import { getSupplier } from '@/api/purchase/supplier'
import { createQualityTemplatePrintLog } from '@/api/production/qualityTemplate'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import { useUserStore } from '@/store/modules/user'
import { dictApi } from '@/api/system/dict'
import type { PurchaseSupplier } from '@/types/purchase'
import type { SysDictItem } from '@/types/system/dict'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { company } = useCompanyConfig()

type PrintLayout = 'a4' | 'qr024'
const PRINT_LAYOUT_KEY = 'purchase-order-print-layout'

const storedLayout = localStorage.getItem(PRINT_LAYOUT_KEY)
const printLayout = ref<PrintLayout>(storedLayout === 'qr024' ? 'qr024' : 'a4')

const info = ref<any>(null)
const supplier = ref<PurchaseSupplier | null>(null)
const paymentTermsOptions = ref<SysDictItem[]>([])
const loading = ref(false)

const itemsList = computed<any[]>(() => info.value?.items || [])
const makerName = computed(
  () => info.value?.createBy || userStore.nickName || userStore.userName || '-'
)
const paymentTermsLabel = computed(() => {
  const paymentTerms = supplier.value?.paymentTerms
  if (!paymentTerms) return '-'
  const option = paymentTermsOptions.value.find((item) => item.itemKey === paymentTerms)
  return option?.label || option?.itemValue || paymentTerms
})

const tradeTerms = [
  '1. 供方如无法遵守本订单交期，需及时通知需方调整；若延误交期给需方造成的直接经济损失将由供方负责，若因供方交货延时而导致需方客户退货，需方将无条件退回供方。',
  '2. 供方的送货单上必须注明买方的订单号，品名，规格，数量，生产日期，货物需符合ROHS，REACH（255项）Non-Phtha1atc环保要求，若供应商所发货物不符合我厂要求而由此造成的一切的经济损失将由供应商负责承担.',
  '3. 供方负责把需方所订货物送达需方指定地点，运费由供方承担。',
  '4. 若供方所发货物不符合需方要求而由此造成的经济损失将由供方负责。需方在收到货物验收合格后按双方约定时间付款；',
  '5. 供需双方在签订和履行合同过程中，要对双方商业信息负有保密责任。',
  '6. 本合同双方盖章签字即生效，传真件具有同等效力，若出现纠纷，以需方所在地仲裁委员会仲裁或者需方所在地法院解决。',
  '7. 供方收到需方所下订单后确认签字盖章回传，一日内没有异议视为认同。未经双方同意而作修改，涂改，添加的内容视为无效。',
  '8. 货物检验后如发现品质不良，应在接到通知后3日内将货物取回，并尽快补回，逾期本公司概不负责。',
]

function handleLayoutChange(value: string | number | boolean | undefined) {
  localStorage.setItem(PRINT_LAYOUT_KEY, value === 'qr024' ? 'qr024' : 'a4')
}

const fmtNum = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN')
}

const fmtMoney = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n)
    ? String(v)
    : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadData() {
  const orderId = route.params.id as string
  if (!orderId) {
    ElMessage.error('缺少采购订单ID')
    return
  }
  loading.value = true
  try {
    const res: any = await getOrder(Number(orderId))
    if (res.code === 200 && res.data) {
      info.value = res.data
      if (res.data.supplierId) {
        try {
          const supplierRes: any = await getSupplier(Number(res.data.supplierId))
          supplier.value = supplierRes.data || null
        } catch {
          supplier.value = null
        }
      }
    } else {
      ElMessage.error(res.msg || '加载采购订单失败')
    }
  } catch {
    ElMessage.error('加载采购订单失败')
  } finally {
    loading.value = false
  }
}

async function loadPaymentTermsOptions() {
  try {
    const res = await dictApi.getItems('payment_terms')
    paymentTermsOptions.value = res.data || []
  } catch {
    paymentTermsOptions.value = []
  }
}

function handlePrint() {
  // 打印留痕（1318）：24 = JJX-QR-024 采购订单
  const orderId = route.params.id as string
  createQualityTemplatePrintLog(24, 'purchase_order', Number(orderId))
    .then(() => window.print())
    .catch(() => {
      ElMessage.error('打印留痕失败，请重试')
    })
}

onMounted(async () => {
  await Promise.all([loadData(), loadPaymentTermsOptions()])
})
</script>

<style scoped>
.purchase-print-page {
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

.doc-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  margin-bottom: 10px;
}

.doc-items th {
  background: #2b5aa7;
  color: #fff;
  padding: 6px 4px;
  font-weight: 600;
  border: 1px solid #2b5aa7;
}

.doc-items td {
  border: 1px solid #dcdfe6;
  padding: 5px 4px;
}

.doc-items tr:nth-child(even) td {
  background: #f7f9fc;
}

.col-center {
  text-align: center;
}

.col-right {
  text-align: right;
}

.doc-total-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f5f7fa;
  border: 1px solid #dcdfe6;
  font-size: 11px;
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

.qr024-layout {
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 11px;
}

.qr024-company-header {
  line-height: 1.55;
  text-align: center;
}

.qr024-company-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}

.qr024-title {
  margin: 8px 0 10px;
  text-align: center;
  font-family: SimHei, '黑体', sans-serif;
  font-size: 20px;
  font-weight: 700;
}

.qr024-order-no {
  margin: 0 1% 4px 62%;
  font-size: 12px;
}

.qr024-info-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  row-gap: 4px;
  margin-bottom: 5px;
  font-size: 12px;
  line-height: 1.35;
}

.qr024-label {
  display: inline-block;
  min-width: 66px;
}

.qr024-fax {
  margin-left: 24px;
}

.qr024-items {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 10px;
}

.qr024-items th,
.qr024-items td {
  height: 23px;
  padding: 3px 4px;
  border: 1px solid #000;
  overflow-wrap: anywhere;
}

.qr024-items th {
  text-align: center;
  font-size: 11px;
  font-weight: 700;
}

.qr024-total-row th,
.qr024-total-row td {
  height: 25px;
}

.qr024-terms {
  margin-top: 6px;
  font-size: 9px;
  line-height: 1.45;
}

.qr024-code {
  margin-top: 8px;
  padding-right: 4%;
  text-align: right;
  font-size: 11px;
}

.qr024-signatures {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 8px;
  font-size: 11px;
}

.qr024-signatures > div:nth-child(2) {
  text-align: center;
}

.qr024-signatures > div:last-child {
  text-align: right;
}

.qr024-sign-line {
  display: inline-block;
  width: 75px;
  border-bottom: 1px solid #000;
}

.qr024-maker {
  display: inline-block;
  min-width: 58px;
  padding: 0 3px 1px;
  border-bottom: 1px solid #000;
  text-align: center;
}

@media print {
  .no-print {
    display: none !important;
  }

  .purchase-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
