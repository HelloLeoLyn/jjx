<!-- views/sales/quotation/components/QuotationSummaryCard.vue -->
<template>
  <div class="quotation-summary">
    <div class="summary-title">
      报价摘要
      <span class="summary-tip">用于本次操作核对，只读</span>
    </div>

    <el-skeleton v-if="loading" :rows="3" animated />

    <template v-else-if="detail">
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="客户名称">
          {{ detail.customerName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="报价日期">
          {{ detail.quotationDate || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="有效期至">
          {{ detail.validUntil || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="币种">
          {{ detail.currency || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="总金额">
          <span class="amount">
            {{ formatCurrency(detail.totalAmount) }} {{ detail.currency || '' }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="销售员">
          {{ detail.salesPersonName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">
          {{ detail.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <el-table :data="visibleItems" border size="small" class="items-table">
        <el-table-column label="序号" type="index" width="55" align="center" />
        <el-table-column label="产品编码" prop="productCode" width="140" />
        <el-table-column label="产品名称" prop="productName" min-width="160" />
        <el-table-column label="数量" prop="quantity" width="90" align="center" />
        <el-table-column label="单价" width="110" align="right">
          <template #default="{ row }">{{ formatCurrency(row.unitPrice) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">
            <span class="amount">{{ formatCurrency(row.amount) }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div v-if="items.length > COLLAPSED_COUNT" class="items-toggle">
        <el-button link type="primary" @click="expanded = !expanded">
          {{ expanded ? '收起明细' : `展开全部 ${items.length} 行` }}
        </el-button>
      </div>
    </template>

    <el-empty v-else description="未加载到报价信息" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { quotationApi } from '@/api/sales/quotation'
import { formatCurrency } from '@/utils/format'

const props = defineProps<{
  quotationId: number | null
}>()

const COLLAPSED_COUNT = 5

const loading = ref(false)
const detail = ref<any>(null)
const expanded = ref(false)

const items = computed<any[]>(() => detail.value?.items || [])
const visibleItems = computed(() =>
  expanded.value ? items.value : items.value.slice(0, COLLAPSED_COUNT)
)

watch(
  () => props.quotationId,
  async (id) => {
    expanded.value = false
    if (!id) {
      detail.value = null
      return
    }
    loading.value = true
    try {
      const res: any = await quotationApi.getInfo(id)
      detail.value = res?.data || null
    } catch (error) {
      console.error('加载报价摘要失败', error)
      detail.value = null
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.quotation-summary {
  margin-bottom: 16px;
}

.summary-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.summary-tip {
  font-size: 12px;
  font-weight: 400;
  color: #909399;
}

.amount {
  font-weight: 600;
  color: #409eff;
}

.items-table {
  margin-top: 12px;
  width: 100%;
}

.items-toggle {
  display: flex;
  justify-content: center;
  margin-top: 6px;
}
</style>
