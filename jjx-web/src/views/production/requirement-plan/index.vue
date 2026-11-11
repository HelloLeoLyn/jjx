<template>
  <div class="app-container requirement-plan">
    <el-card shadow="never">
      <template #header>
        <div class="head">
          <span class="title">需求计划（多层净需求试算）</span>
          <el-tag type="info" effect="plain">只读</el-tag>
        </div>
      </template>

      <el-form :inline="true" @submit.prevent>
        <el-form-item label="产品" required>
          <el-select
            v-model="form.productId"
            filterable
            remote
            reserve-keyword
            :remote-method="searchProducts"
            :loading="searching"
            placeholder="输入编码/名称搜索"
            style="width: 260px"
          >
            <el-option
              v-for="item in products"
              :key="item.productId"
              :label="`${item.productCode || ''} ${item.productName || ''}`.trim()"
              :value="item.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="需求数量" required>
          <el-input-number v-model="form.quantity" :min="0.0001" :precision="4" style="width: 150px" />
        </el-form-item>
        <el-form-item label="需求日期">
          <el-date-picker
            v-model="form.demandDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认当天"
            style="width: 160px"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="form.warehouseId" clearable placeholder="全部仓库" style="width: 190px">
            <el-option
              v-for="item in warehouses"
              :key="item.warehouseId"
              :label="item.warehouseName"
              :value="item.warehouseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="runPreview">试算</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="summary" :title="notice" type="info" :closable="false" show-icon />

      <div v-if="summary" class="totals">
        <div class="total">
          <span>需求</span>
          <b>{{ formatQty(summary.requestedQuantity) }} {{ summary.productUnit || '' }}</b>
        </div>
        <div class="total"><span>缺口物料</span><b>{{ totals.gapLineCount }}</b></div>
        <div class="total"><span>缺口合计</span><b>{{ formatQty(totals.gapQty) }}</b></div>
        <div class="total"><span>生产供给</span><b>{{ formatQty(totals.productionSupply) }}</b></div>
        <div class="total"><span>采购供给</span><b>{{ formatQty(totals.purchaseSupply) }}</b></div>
        <div class="total">
          <span>计划采购（不抵扣）</span><b>{{ formatQty(totals.plannedPurchaseSupply) }}</b>
        </div>
      </div>

      <el-table
        v-if="summary"
        :data="summary.lines"
        border
        size="small"
        row-key="materialId"
        class="plan-table"
      >
        <el-table-column prop="materialCode" label="物料编码" width="130" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="stockUnit" label="单位" width="70" />
        <el-table-column label="理论用量" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.aggregateQtyDisplay ?? row.theoreticalQtyApprox) }}</template>
        </el-table-column>
        <el-table-column label="可用" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.availableForDemand) }}</template>
        </el-table-column>
        <el-table-column label="生产供给" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.productionSupply) }}</template>
        </el-table-column>
        <el-table-column label="采购供给" width="100" align="right">
          <template #default="{ row }">{{ formatQty(row.purchaseSupply) }}</template>
        </el-table-column>
        <el-table-column label="计划采购" width="100" align="right">
          <template #default="{ row }">
            <span class="muted">{{ formatQty(row.plannedPurchaseSupply) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="缺口" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ gap: hasGap(row) }">{{ formatQty(row.gapQtyDisplay ?? row.gapQtyApprox) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="summary?.warnings?.length" class="notes">
        <div class="notes-title">提示</div>
        <ul>
          <li v-for="(item, index) in summary.warnings" :key="`w-${index}`">{{ item }}</li>
        </ul>
      </div>
      <div v-if="summary?.limitations?.length" class="notes">
        <div class="notes-title">限制</div>
        <ul>
          <li v-for="(item, index) in summary.limitations" :key="`l-${index}`">{{ item }}</li>
        </ul>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { searchProduct } from '@/api/product'
import { warehouseApi } from '@/api/inventory/warehouse'
import { previewRequirement } from '@/api/production/requirementPlan'
import type { RequirementSummary } from '@/types/production/requirementPlan'
import { formatQty, hasGap, readOnlyNotice, summarizePlan } from './planModel'

interface ProductOption {
  productId: number
  productCode?: string
  productName?: string
}

const products = ref<ProductOption[]>([])
const warehouses = ref<Array<{ warehouseId: number; warehouseName: string }>>([])
const searching = ref(false)
const loading = ref(false)
const summary = ref<RequirementSummary | null>(null)

const form = reactive({
  productId: undefined as number | undefined,
  quantity: 1,
  demandDate: undefined as string | undefined,
  warehouseId: undefined as number | undefined,
})

const totals = computed(() => summarizePlan(summary.value?.lines || [], summary.value?.plannedPurchaseSupply))
const notice = computed(() => readOnlyNotice(summary.value))

async function searchProducts(keyword: string) {
  if (!keyword) return
  searching.value = true
  try {
    const response = await searchProduct(keyword)
    products.value = ((response.data || []) as ProductOption[]).slice(0, 20)
  } catch {
    products.value = []
  } finally {
    searching.value = false
  }
}

async function loadWarehouses() {
  try {
    const response = await warehouseApi.list({ current: 1, pageSize: 200 })
    warehouses.value = ((response.data?.records || []) as Array<{ warehouseId: number; warehouseName: string }>).map(
      (item) => ({ warehouseId: item.warehouseId, warehouseName: item.warehouseName }),
    )
  } catch {
    warehouses.value = []
  }
}

async function runPreview() {
  if (!form.productId) return void ElMessage.warning('请选择产品')
  if (!form.quantity || form.quantity <= 0) return void ElMessage.warning('需求数量必须大于 0')
  loading.value = true
  try {
    const response = await previewRequirement({
      productId: form.productId,
      quantity: form.quantity,
      demandDate: form.demandDate || null,
      warehouseId: form.warehouseId || null,
    })
    summary.value = response.data || null
  } catch (error) {
    summary.value = null
    ElMessage.error(error instanceof Error ? error.message : '试算失败，请稍后重试')
  } finally {
    loading.value = false
  }
}

onMounted(loadWarehouses)
</script>

<style scoped>
.head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.title {
  font-weight: 600;
}
.totals {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 12px 0;
}
.total {
  min-width: 140px;
  padding: 8px 12px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  background: #fafcff;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.total span {
  font-size: 12px;
  color: #909399;
}
.plan-table {
  margin-top: 8px;
}
.gap {
  color: var(--el-color-danger);
  font-weight: 600;
}
.muted {
  color: #909399;
}
.notes {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
.notes-title {
  font-weight: 600;
  margin-bottom: 4px;
}
.notes ul {
  margin: 0;
  padding-left: 18px;
}
</style>
