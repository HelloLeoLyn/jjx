<template>
  <div class="reconcile-page">
    <el-card shadow="never">
      <!-- 查询区 -->
      <el-form inline>
        <el-form-item label="客户">
          <el-select
            v-model="query.customerId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchCustomers"
            :loading="customerLoading"
            placeholder="输入客户名称/编码搜索"
            style="width: 240px"
            clearable
          >
            <el-option
              v-for="c in customerOptions"
              :key="c.customerId"
              :label="`${c.customerCode} ${c.customerName}`"
              :value="c.customerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="期间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" :loading="loading" @click="search">查询对账</el-button>
          <el-button icon="Printer" :disabled="!rows.length" @click="goPrint">打印对账单</el-button>
        </el-form-item>
      </el-form>

      <!-- 汇总信息 -->
      <template v-if="loaded">
        <el-alert
          v-if="!rows.length"
          title="该客户在所选期间内无已发货送货单"
          type="info"
          :closable="false"
          style="margin-bottom: 12px"
        />
        <el-row v-else :gutter="16" class="summary-row">
          <el-col :span="6">
            <div class="sum-card">
              <div class="sum-label">送货单数</div>
              <div class="sum-value">{{ data.deliveryCount }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="sum-card">
              <div class="sum-label">送货金额合计</div>
              <div class="sum-value primary">{{ money(deliveryTotal) }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="sum-card">
              <div class="sum-label">期间回款（{{ data.paymentCount }} 笔）</div>
              <div class="sum-value success">{{ money(data.paymentTotal) }}</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="sum-card">
              <div class="sum-label">未收差额</div>
              <div class="sum-value warning">{{ money(unpaidDiff) }}</div>
            </div>
          </el-col>
        </el-row>
      </template>

      <!-- 明细表（送货行平铺） -->
      <el-table v-if="rows.length" :data="rows" border stripe size="small" style="margin-top: 12px" max-height="560">
        <el-table-column type="index" label="#" width="46" align="center" />
        <el-table-column prop="deliveryDate" label="送货日期" width="100" />
        <el-table-column prop="deliveryNo" label="送货单号" width="130" />
        <el-table-column label="料号" min-width="120">
          <template #default="{ row }">{{ row.customerMaterialNo || row.productCode || '-' }}</template>
        </el-table-column>
        <el-table-column label="品名规格" min-width="180">
          <template #default="{ row }">
            {{ row.productName }}
            <div v-if="row.specification" style="color: #909399; font-size: 12px">{{ row.specification }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="60" align="center" />
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column label="单价" width="100" align="right">
          <template #default="{ row }">{{ money(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单号" width="130" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getReconciliation } from '@/api/sales/reconcile'
import { customerApi } from '@/api/sales/customer'

defineOptions({ name: 'SalesReconcile' })

const loading = ref(false)
const customerLoading = ref(false)
const customerOptions = ref<any[]>([])
const dateRange = ref<string[]>([])
const query = reactive<{ customerId?: number; customerName?: string }>({})
const data = ref<any>({ rows: [], deliveryCount: 0, paymentTotal: 0, paymentCount: 0 })
const loaded = ref(false)

const rows = computed(() => {
  // 送货单 → 订单明细行平铺（一单一发，明细即订单行）
  const flat: any[] = []
  for (const d of data.value.rows || []) {
    for (const it of d.items || []) {
      flat.push({
        deliveryDate: d.deliveryDate,
        deliveryNo: d.deliveryNo,
        orderNo: d.orderNo,
        ...it,
      })
    }
  }
  return flat
})

const deliveryTotal = computed(() => rows.value.reduce((s, r) => s + (Number(r.amount) || 0), 0))
const unpaidDiff = computed(() => Number(deliveryTotal.value) - Number(data.value.paymentTotal || 0))

const money = (v?: number | string) =>
  v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

function searchCustomers(keyword: string) {
  customerLoading.value = true
  customerApi
    .searchCustomers(keyword)
    .then((res: any) => {
      customerOptions.value = res?.data || []
    })
    .finally(() => {
      customerLoading.value = false
    })
}

async function search() {
  if (!query.customerId) {
    ElMessage.warning('请先选择客户')
    return
  }
  loading.value = true
  try {
    const res: any = await getReconciliation({
      customerId: query.customerId,
      startDate: dateRange.value?.[0],
      endDate: dateRange.value?.[1],
    })
    data.value = res?.data || { rows: [] }
    loaded.value = true
    if (!data.value.rows?.length) {
      ElMessage.info('该客户在所选期间内无对账数据')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
  } finally {
    loading.value = false
  }
}

function goPrint() {
  if (!query.customerId) return
  const params = new URLSearchParams({ customerId: String(query.customerId) })
  if (dateRange.value?.[0]) params.set('startDate', dateRange.value[0])
  if (dateRange.value?.[1]) params.set('endDate', dateRange.value[1])
  window.open(`/sales/reconcile/print?${params.toString()}`, '_blank')
}
</script>

<style scoped>
.summary-row {
  margin-bottom: 4px;
}
.sum-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 10px 14px;
  background: #fafbfc;
}
.sum-label {
  font-size: 12px;
  color: #909399;
}
.sum-value {
  font-size: 20px;
  font-weight: 700;
  margin-top: 4px;
}
.sum-value.primary {
  color: #409eff;
}
.sum-value.success {
  color: #67c23a;
}
.sum-value.warning {
  color: #e6a23c;
}
</style>
