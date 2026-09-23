<template>
  <div class="transaction-ledger">
    <el-alert type="info" :closable="false" show-icon class="ledger-tip">
      <template #title>收发明细来自库存流水，只增不改；收入、发出和变动后结存可逐笔核对。</template>
    </el-alert>

    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true" label-width="76px">
        <el-form-item label="业务日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 250px"
          />
        </el-form-item>
        <el-form-item label="物料编码">
          <el-input
            v-model="queryParams.materialCode"
            clearable
            placeholder="编码"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="物料名称">
          <el-input
            v-model="queryParams.materialName"
            clearable
            placeholder="名称"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            clearable
            filterable
            placeholder="全部仓库"
            style="width: 150px"
          >
            <el-option
              v-for="warehouse in warehouses"
              :key="warehouse.warehouseId"
              :label="warehouse.warehouseName"
              :value="warehouse.warehouseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="批次">
          <el-input
            v-model="queryParams.batchNo"
            clearable
            placeholder="批次号"
            style="width: 150px"
          />
        </el-form-item>
        <el-form-item label="收发类型">
          <el-select
            v-model="queryParams.transactionType"
            clearable
            placeholder="全部"
            style="width: 130px"
          >
            <el-option
              v-for="item in TransactionTypeEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="业务来源">
          <el-select
            v-model="queryParams.sourceType"
            clearable
            placeholder="全部"
            style="width: 130px"
          >
            <el-option
              v-for="item in SourceTypeEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源单号">
          <el-input
            v-model="queryParams.sourceNo"
            clearable
            placeholder="单号"
            style="width: 170px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="page-summary">
        <span
          >当前页收入 <b class="qty-in">{{ fmt(pageSummary.received) }}</b></span
        >
        <span
          >当前页发出 <b class="qty-out">{{ fmt(pageSummary.issued) }}</b></span
        >
        <span>共 {{ total }} 笔流水</span>
      </div>

      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="业务时间" prop="transactionTime" width="165" fixed="left" />
        <el-table-column label="物料编码" prop="materialCode" width="145" show-overflow-tooltip />
        <el-table-column
          label="物料名称"
          prop="materialName"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column label="仓库" prop="warehouseName" width="110" />
        <el-table-column label="库位" width="100">
          <template #default="{ row }">{{ row.locationName || '-' }}</template>
        </el-table-column>
        <el-table-column label="批次" prop="batchNo" width="145" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="transactionTag(row.transactionType)">
              {{ row.transactionTypeName || transactionLabel(row.transactionType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收入" width="95" align="right">
          <template #default="{ row }"
            ><span class="qty-in">{{ row.quantity > 0 ? fmt(row.quantity) : '-' }}</span></template
          >
        </el-table-column>
        <el-table-column label="发出" width="95" align="right">
          <template #default="{ row }"
            ><span class="qty-out">{{
              row.quantity < 0 ? fmt(-row.quantity) : '-'
            }}</span></template
          >
        </el-table-column>
        <el-table-column label="变动前" width="95" align="right">
          <template #default="{ row }">{{ fmt(row.beforeQuantity) }}</template>
        </el-table-column>
        <el-table-column label="变动后" width="95" align="right">
          <template #default="{ row }"
            ><b>{{ fmt(row.afterQuantity) }}</b></template
          >
        </el-table-column>
        <el-table-column label="来源单号" prop="sourceNo" width="170" show-overflow-tooltip />
        <el-table-column label="业务来源" width="110">
          <template #default="{ row }">{{ row.sourceTypeName || row.sourceType || '-' }}</template>
        </el-table-column>
        <el-table-column label="经办人" prop="operatorName" width="100" />
        <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
      </el-table>

      <pagination
        v-show="total > 0"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="load"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'InventoryTransactionLedger' })

import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getTransactionPage } from '@/api/inventory/transaction'
import type { TransactionQueryParams, TransactionVO } from '@/api/inventory/transaction'
import { warehouseApi } from '@/api/inventory/warehouse'
import type { InventoryWarehouse } from '@/types/inventory/warehouse'
import { SourceTypeEnum, TransactionTypeEnum } from '@/enums/inventory/TransactionEnum'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const rows = ref<TransactionVO[]>([])
const total = ref(0)
const warehouses = ref<InventoryWarehouse[]>([])
const dateRange = ref<string[]>([])

const queryParams = reactive<TransactionQueryParams>({
  current: 1,
  pageSize: 20,
  inventoryItemId: undefined,
  materialCode: '',
  materialName: '',
  warehouseId: undefined,
  batchNo: '',
  transactionType: '',
  sourceType: '',
  sourceNo: '',
})

function fmt(value?: number | string | null): string {
  const n = Number(value ?? 0)
  return Number.isFinite(n) ? String(Math.round(n * 10000) / 10000) : '0'
}

function nextDay(date: string): string {
  const value = new Date(`${date}T00:00:00`)
  value.setDate(value.getDate() + 1)
  const year = value.getFullYear()
  const month = String(value.getMonth() + 1).padStart(2, '0')
  const day = String(value.getDate()).padStart(2, '0')
  return `${year}-${month}-${day} 00:00:00`
}

function transactionLabel(type?: string): string {
  return TransactionTypeEnum.getLabel(String(type || '').toLowerCase())
}

function transactionTag(type?: string) {
  return TransactionTypeEnum.getTagProps(String(type || '').toLowerCase()).type
}

const pageSummary = computed(() =>
  rows.value.reduce(
    (summary, row) => {
      const quantity = Number(row.quantity || 0)
      if (quantity > 0) summary.received += quantity
      if (quantity < 0) summary.issued += -quantity
      return summary
    },
    { received: 0, issued: 0 }
  )
)

function buildParams(): TransactionQueryParams {
  return {
    ...queryParams,
    transactionTimeStart: dateRange.value[0] ? `${dateRange.value[0]} 00:00:00` : undefined,
    transactionTimeEnd: dateRange.value[1] ? nextDay(dateRange.value[1]) : undefined,
  }
}

async function load() {
  loading.value = true
  try {
    const res = await getTransactionPage(buildParams())
    rows.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } catch (error: any) {
    rows.value = []
    total.value = 0
    ElMessage.error(error?.message || '加载收发明细失败')
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.current = 1
  void load()
}

function handleReset() {
  Object.assign(queryParams, {
    current: 1,
    pageSize: queryParams.pageSize,
    inventoryItemId: undefined,
    materialCode: '',
    materialName: '',
    warehouseId: undefined,
    batchNo: '',
    transactionType: '',
    sourceType: '',
    sourceNo: '',
  })
  dateRange.value = []
  void router.replace({ path: route.path })
  void load()
}

async function loadWarehouses() {
  try {
    const res = await warehouseApi.getAllEnabled()
    warehouses.value = res.data || []
  } catch {
    warehouses.value = []
  }
}

onMounted(() => {
  const inventoryItemId =
    typeof route.query.inventoryItemId === 'string' ? route.query.inventoryItemId : undefined
  queryParams.inventoryItemId = inventoryItemId
  queryParams.materialCode =
    typeof route.query.materialCode === 'string' ? route.query.materialCode : ''
  queryParams.materialName =
    typeof route.query.materialName === 'string' ? route.query.materialName : ''
  queryParams.batchNo = typeof route.query.batchNo === 'string' ? route.query.batchNo : ''
  void loadWarehouses()
  void load()
})
</script>

<style scoped>
.transaction-ledger {
  padding: 20px;
}
.ledger-tip,
.search-card,
.table-card {
  margin-bottom: 16px;
}
.page-summary {
  display: flex;
  gap: 28px;
  margin-bottom: 12px;
  color: #606266;
  font-size: 14px;
}
.qty-in {
  color: #67c23a;
}
.qty-out {
  color: #e6a23c;
}
</style>
