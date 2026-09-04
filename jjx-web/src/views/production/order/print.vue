<template>
  <div class="production-print-page">
    <div class="print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <div class="toolbar-options">
        <div class="option-group">
          <span>版式</span>
          <el-radio-group v-model="printLayout" size="small" @change="saveLayout">
            <el-radio-button value="system">系统版</el-radio-button>
            <el-radio-button value="qr005">纸版 QR-005</el-radio-button>
          </el-radio-group>
        </div>
        <div class="option-group column-selector">
          <span>打印列</span>
          <el-checkbox-group v-model="selectedColumns" @change="saveColumns">
            <el-checkbox v-for="column in columns" :key="column.key" :value="column.key">
              {{ column.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>
      </div>
      <el-button type="primary" icon="Printer" :disabled="!orders.length" @click="handlePrint">
        打印
      </el-button>
    </div>

    <A4Canvas v-if="orders.length" :padding-mm="15">
      <section class="print-sheet" :class="`layout-${printLayout}`">
        <PrintCompanyHeader v-if="printLayout === 'system'" variant="center" />
        <header v-else class="paper-company-header">
          <div class="paper-company-name">{{ company.name || '深圳市精捷信科技有限公司' }}</div>
          <div v-if="company.address">地址：{{ company.address }}</div>
          <div v-if="company.phone">电话：{{ company.phone }}</div>
        </header>

        <div class="title-row">
          <div class="document-title">
            制&nbsp;&nbsp;造&nbsp;&nbsp;指&nbsp;&nbsp;令&nbsp;&nbsp;单
          </div>
          <div v-if="printLayout === 'qr005'" class="paper-meta">
            <div>编号：JJX-QR-005</div>
            <div>日期：{{ printDate }}</div>
          </div>
        </div>

        <table class="order-table">
          <thead>
            <tr>
              <th v-for="column in visibleColumns" :key="column.key">{{ column.label }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(order, index) in orders" :key="order.orderId">
              <td
                v-for="column in visibleColumns"
                :key="column.key"
                :class="column.align ? `col-${column.align}` : undefined"
              >
                {{ column.render(order, index) }}
              </td>
            </tr>
          </tbody>
        </table>

        <div class="remark-area">
          <span class="remark-label">备注</span>
          <span class="remark-content">{{ combinedRemarks }}</span>
        </div>
      </section>
    </A4Canvas>

    <div v-else v-loading="loading" class="loading-area"></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getProductionOrderDetail } from '@/api/production/order'
import { createQualityTemplatePrintLog } from '@/api/production/qualityTemplate'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import type { ProductionOrderVO } from '@/types/production/order'

type PrintLayout = 'system' | 'qr005'
type ColumnKey =
  | 'no'
  | 'productName'
  | 'plannedQuantity'
  | 'planEndDate'
  | 'productCode'
  | 'salesOrderNo'
  | 'productionBatchNo'
  | 'stock'

interface PrintColumn {
  key: ColumnKey
  label: string
  align?: 'center' | 'right'
  render: (order: ProductionOrderVO, index: number) => string
}

const route = useRoute()
const router = useRouter()
const { company } = useCompanyConfig()
const PRINT_LAYOUT_KEY = 'production-order-print-layout'
const PRINT_COLUMNS_KEY = 'production-order-print-cols'

const fmtNum = (value?: number | string | null): string => {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isNaN(number) ? String(value) : number.toLocaleString('zh-CN')
}

const columns: PrintColumn[] = [
  { key: 'no', label: 'NO', align: 'center', render: (_order, index) => String(index + 1) },
  { key: 'productName', label: '品名', render: (order) => order.productName || '-' },
  {
    key: 'plannedQuantity',
    label: '订单数量',
    align: 'right',
    render: (order) => fmtNum(order.plannedQuantity),
  },
  {
    key: 'planEndDate',
    label: '交期',
    align: 'center',
    render: (order) => order.planEndDate || '-',
  },
  { key: 'productCode', label: '机种号', render: (order) => order.productCode || '-' },
  { key: 'salesOrderNo', label: '订单号', render: (order) => order.salesOrderNo || '-' },
  { key: 'productionBatchNo', label: '生产批号', align: 'center', render: () => '-' },
  { key: 'stock', label: '库存', align: 'center', render: () => '-' },
]
const allColumnKeys = columns.map((column) => column.key)

const storedLayout = localStorage.getItem(PRINT_LAYOUT_KEY)
const printLayout = ref<PrintLayout>(storedLayout === 'qr005' ? 'qr005' : 'system')
const selectedColumns = ref<ColumnKey[]>(readStoredColumns())
const orders = ref<ProductionOrderVO[]>([])
const loading = ref(false)

const visibleColumns = computed(() =>
  columns.filter((column) => selectedColumns.value.includes(column.key))
)
const combinedRemarks = computed(() =>
  orders.value
    .map((order) => order.remark?.trim())
    .filter(Boolean)
    .join('；')
)
const printDate = new Date().toLocaleDateString('sv-SE')

function readStoredColumns(): ColumnKey[] {
  try {
    const raw = localStorage.getItem(PRINT_COLUMNS_KEY)
    if (raw === null) return [...allColumnKeys]
    const stored = JSON.parse(raw)
    if (!Array.isArray(stored)) return [...allColumnKeys]
    const valid = stored.filter((key): key is ColumnKey => allColumnKeys.includes(key as ColumnKey))
    return valid
  } catch {
    return [...allColumnKeys]
  }
}

function saveLayout(value: string | number | boolean | undefined) {
  localStorage.setItem(PRINT_LAYOUT_KEY, value === 'qr005' ? 'qr005' : 'system')
}

function saveColumns(value: Array<string | number | boolean>) {
  localStorage.setItem(PRINT_COLUMNS_KEY, JSON.stringify(value))
}

function getOrderIds(): string[] {
  const queryIds = typeof route.query.ids === 'string' ? route.query.ids.split(',') : []
  const rawIds = queryIds.length ? queryIds : [String(route.params.id || '')]
  return [...new Set(rawIds.map((id) => id.trim()).filter(Boolean))]
}

async function loadData() {
  const orderIds = getOrderIds()
  if (!orderIds.length) {
    ElMessage.error('缺少工单ID')
    return
  }
  loading.value = true
  try {
    const responses: any[] = await Promise.all(orderIds.map((id) => getProductionOrderDetail(id)))
    const failed = responses.find((response) => response.code !== 200 || !response.data)
    if (failed) throw new Error(failed.msg || '加载工单失败')
    orders.value = responses.map((response) => response.data)
  } catch (error: any) {
    ElMessage.error(error?.message || '加载工单失败')
  } finally {
    loading.value = false
  }
}

async function handlePrint() {
  try {
    await Promise.all(
      orders.value.map((order) =>
        createQualityTemplatePrintLog(5, 'production_order', Number(order.orderId))
      )
    )
    window.print()
  } catch {
    ElMessage.error('打印留痕失败，请重试')
  }
}

onMounted(loadData)
</script>

<style scoped>
.production-print-page {
  min-height: 100vh;
  padding: 20px;
  background: #eef0f3;
}
.print-toolbar {
  max-width: 1120px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.toolbar-options {
  flex: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 10px 20px;
}
.option-group {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 13px;
}
.column-selector :deep(.el-checkbox) {
  margin-right: 12px;
}
.print-sheet {
  color: #303133;
  font-size: 11px;
}
.title-row {
  position: relative;
  min-height: 46px;
}
.document-title {
  padding: 12px 150px 9px;
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 5px;
}
.paper-meta {
  position: absolute;
  right: 0;
  bottom: 5px;
  width: 145px;
  line-height: 1.5;
}
.order-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.order-table th,
.order-table td {
  height: 30px;
  padding: 4px 6px;
  border: 1px solid #c8cdd4;
  overflow-wrap: anywhere;
}
.order-table th {
  background: #f2f6fc;
  text-align: center;
  color: #303133;
}
.col-center {
  text-align: center;
}
.col-right {
  text-align: right;
}
.remark-area {
  display: grid;
  grid-template-columns: 54px 1fr;
  min-height: 72px;
  border: 1px solid #c8cdd4;
  border-top: 0;
}
.remark-label {
  display: flex;
  align-items: center;
  justify-content: center;
  border-right: 1px solid #c8cdd4;
  font-weight: 700;
}
.remark-content {
  padding: 6px;
  white-space: pre-wrap;
}
.layout-qr005 {
  color: #000;
  font-family: SimSun, '宋体', serif;
}
.paper-company-header {
  min-height: 58px;
  text-align: center;
  line-height: 1.5;
}
.paper-company-name {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: 2px;
}
.layout-qr005 .document-title {
  font-family: SimHei, '黑体', sans-serif;
}
.layout-qr005 .order-table th,
.layout-qr005 .order-table td,
.layout-qr005 .remark-area {
  border-color: #000;
}
.layout-qr005 .order-table th {
  background: #fff;
  color: #000;
  font-weight: 700;
}
.layout-qr005 .remark-label {
  border-color: #000;
}
.loading-area {
  height: 400px;
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
