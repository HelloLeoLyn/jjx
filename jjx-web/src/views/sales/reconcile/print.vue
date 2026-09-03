<template>
  <div class="rec-print-page">
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <span class="toolbar-tip">对账单打印预览</span>
      </div>
      <el-button type="primary" icon="Printer" :disabled="!data.rows" @click="handlePrint">打印</el-button>
    </div>

    <A4Canvas v-if="data.rows" :padding-mm="14">
      <PrintCompanyHeader variant="center" />
      <div class="doc-title">业 务 对 账 单</div>
      <div class="doc-subtitle">对账期间：{{ data.startDate }} 至 {{ data.endDate }}</div>

      <!-- 客户信息 + 付款资料 -->
      <div class="doc-info">
        <div class="info-row">
          <span class="label">客户名称：</span>{{ customer?.customerName || '' }}
        </div>
        <div class="info-row">
          <span class="label">对账说明：</span>请核对下列送货明细，如有异议请于 7 日内书面反馈；确认无误请签字回传。
        </div>
      </div>

      <!-- 明细表（模板列：送货日期/送货单号/物料料号/品名规格/数量/销售号/单价/金额/订单号码） -->
      <table class="doc-table">
        <thead>
          <tr>
            <th style="width: 30px">序号</th>
            <th style="width: 86px">送货日期</th>
            <th style="width: 96px">送货单号</th>
            <th>品名料号</th>
            <th style="width: 64px">单位</th>
            <th style="width: 60px">数量</th>
            <th style="width: 80px">单价</th>
            <th style="width: 90px">金额</th>
            <th style="width: 96px">订单号码</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(r, i) in rows" :key="i">
            <td class="center">{{ i + 1 }}</td>
            <td class="center">{{ r.deliveryDate }}</td>
            <td>{{ r.deliveryNo }}</td>
            <td>
              {{ r.customerMaterialNo || r.productCode }}
              <div class="cell-sub">{{ r.productName }}<span v-if="r.specification"> {{ r.specification }}</span></div>
            </td>
            <td class="center">{{ r.unit }}</td>
            <td class="right">{{ fmtNum(r.quantity) }}</td>
            <td class="right">{{ money(r.unitPrice) }}</td>
            <td class="right">{{ money(r.amount) }}</td>
            <td>{{ r.orderNo }}</td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <td colspan="7" class="right strong">合 计</td>
            <td class="right strong">{{ money(deliveryTotal) }}</td>
            <td></td>
          </tr>
        </tfoot>
      </table>

      <!-- 付款资料 -->
      <div class="pay-block">
        <div class="pay-title">付款资料</div>
        <div class="pay-row">
          <span>期间回款笔数：{{ data.paymentCount }}</span>
          <span style="margin-left: 32px">期间回款合计：<b>{{ money(data.paymentTotal) }}</b></span>
          <span style="margin-left: 32px">未收差额：<b>{{ money(unpaidDiff) }}</b></span>
        </div>
      </div>

      <!-- 客户回签栏 -->
      <div class="sign-area">
        <div class="sign-row">
          <span>客户确认（盖章）：____________________</span>
          <span>日期：____________________</span>
        </div>
        <div class="sign-row">
          <span>我方经办：____________________</span>
          <span>日期：____________________</span>
        </div>
      </div>
    </A4Canvas>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getReconciliation } from '@/api/sales/reconcile'
import { customerApi } from '@/api/sales/customer'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'

const route = useRoute()
const router = useRouter()

const data = ref<any>(null)
const customer = ref<any>(null)

const rows = computed(() => {
  const flat: any[] = []
  for (const d of data.value?.rows || []) {
    for (const it of d.items || []) {
      flat.push({ deliveryDate: d.deliveryDate, deliveryNo: d.deliveryNo, orderNo: d.orderNo, ...it })
    }
  }
  return flat
})
const deliveryTotal = computed(() => rows.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))
const unpaidDiff = computed(() => Number(deliveryTotal.value) - Number(data.value?.paymentTotal || 0))

const money = (v?: number | string) =>
  v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const fmtNum = (v?: number | string) => (v == null ? '-' : Number(v).toLocaleString('zh-CN'))

function handlePrint() {
  window.print()
}

onMounted(async () => {
  const customerId = Number(route.query.customerId)
  if (!customerId) {
    ElMessage.error('缺少客户参数')
    return
  }
  try {
    const [res, cus] = await Promise.all([
      getReconciliation({
        customerId,
        startDate: (route.query.startDate as string) || undefined,
        endDate: (route.query.endDate as string) || undefined,
      }),
      customerApi.getCustomer(customerId),
    ])
    data.value = res?.data
    customer.value = cus?.data
  } catch (e: any) {
    ElMessage.error(e?.message || '对账单加载失败')
  }
})
</script>

<style scoped>
.rec-print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}
.print-toolbar {
  max-width: 794px;
  margin: 0 auto 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #fff;
  padding: 8px 12px;
  border-radius: 8px;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.doc-title {
  text-align: center;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 8px;
  margin: 4px 0 2px;
}
.doc-subtitle {
  text-align: center;
  color: #666;
  font-size: 12px;
  margin-bottom: 10px;
}
.doc-info {
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.8;
}
.info-row .label {
  font-weight: 600;
}
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.doc-table th,
.doc-table td {
  border: 1px solid #999;
  padding: 5px 6px;
  vertical-align: top;
}
.doc-table th {
  background: #f2f3f5;
  text-align: center;
}
.cell-sub {
  color: #666;
  font-size: 11px;
}
.center {
  text-align: center;
}
.right {
  text-align: right;
}
.strong {
  font-weight: 700;
}
.pay-block {
  margin-top: 12px;
  border: 1px solid #999;
  padding: 8px 10px;
  font-size: 13px;
}
.pay-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.sign-area {
  margin-top: 42px;
  font-size: 13px;
}
.sign-row {
  display: flex;
  justify-content: space-between;
  margin-bottom: 26px;
}
@media print {
  .rec-print-page {
    padding: 0;
    background: #fff;
  }
  .no-print {
    display: none !important;
  }
}
</style>
