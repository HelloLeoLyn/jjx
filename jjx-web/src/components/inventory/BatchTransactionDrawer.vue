<template>
  <el-drawer
    :model-value="props.visible"
    :title="`变动流水 - ${props.batchNo || ''}`"
    size="760px"
    append-to-body
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <div class="flow-head">
      <span class="flow-tip">
        流水只增不改：每笔变动都记「变动前 → 变动后」；结存 = 入库 − 出库 ± 盘点调整
      </span>
    </div>

    <div class="flow-summary">
      <span>入库合计 <b class="in">{{ fmt(summary.received) }}</b></span>
      <span>出库合计 <b class="out">{{ fmt(summary.issued) }}</b></span>
      <span>结存 <b>{{ fmt(summary.balance) }}</b></span>
    </div>

    <el-table v-loading="loading" :data="rows" border size="small" max-height="560">
      <el-table-column label="时间" prop="transactionTime" width="160" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTagType(row.transactionType)">
            {{ row.transactionTypeName || typeLabel(row.transactionType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="数量" width="100" align="right">
        <template #default="{ row }">
          <span :class="row.quantity >= 0 ? 'in' : 'out'">
            {{ row.quantity >= 0 ? '+' : '' }}{{ fmt(row.quantity) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="变动前" width="90" align="right">
        <template #default="{ row }">{{ fmt(row.beforeQuantity) }}</template>
      </el-table-column>
      <el-table-column label="变动后" width="90" align="right">
        <template #default="{ row }">{{ fmt(row.afterQuantity) }}</template>
      </el-table-column>
      <el-table-column label="来源单据" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span>{{ row.sourceNo || '-' }}</span>
          <span v-if="row.sourceTypeName || row.sourceType" class="flow-sub">
            （{{ row.sourceTypeName || row.sourceType }}）
          </span>
        </template>
      </el-table-column>
      <el-table-column label="仓库" prop="warehouseName" width="100" />
      <el-table-column label="操作人" prop="operatorName" width="100" />
      <el-table-column label="备注" prop="remark" min-width="140" show-overflow-tooltip />
    </el-table>

    <el-empty v-if="!loading && !rows.length" description="该批次暂无流水（可能由历史数据导入产生）" :image-size="60" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTransactionsByBatch } from '@/api/inventory/transaction'
import type { TransactionVO } from '@/api/inventory/transaction'
import { TransactionTypeEnum } from '@/enums/inventory/TransactionEnum'

/**
 * 批次「变动流水」抽屉（dev-20260923-017 一期）
 * 数据源 = inventory_transaction（只增不改的流水，唯一审计依据），按批次 + 时间正序。
 */
const props = defineProps<{
  visible: boolean
  inventoryItemId?: string
  batchNo?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const loading = ref(false)
const rows = ref<TransactionVO[]>([])

function fmt(v?: number | string | null): string {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? String(Math.round(n * 100) / 100) : '0'
}

type TagType = 'success' | 'primary' | 'info' | 'warning' | 'danger'

function typeLabel(t?: string): string {
  // 后端流水类型为大写枚举（INBOUND/OUTBOUND/TRANSFER_IN/TRANSFER_OUT/ADJUST），前端枚举为小写
  return TransactionTypeEnum.getLabel(String(t || '').toLowerCase())
}

function typeTagType(t?: string): TagType {
  return (TransactionTypeEnum.getTagProps(String(t || '').toLowerCase()).type || 'info') as TagType
}

const summary = computed(() => {
  let received = 0
  let issued = 0
  rows.value.forEach((r) => {
    const q = Number(r.quantity || 0)
    if (q > 0) received += q
    else issued += -q
  })
  return { received, issued, balance: received - issued }
})

async function load() {
  if (!props.inventoryItemId || !props.batchNo) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const res: any = await getTransactionsByBatch(props.inventoryItemId, props.batchNo)
    rows.value = res?.data || []
  } catch (e: any) {
    rows.value = []
    ElMessage.error(e?.message || '加载批次流水失败')
  } finally {
    loading.value = false
  }
}

watch(
  () => props.visible,
  (val) => {
    if (val) void load()
  }
)

const handleClose = () => {
  emit('update:visible', false)
}
</script>

<style scoped>
.flow-head {
  margin-bottom: 8px;
}
.flow-tip {
  font-size: 12px;
  color: #909399;
}
.flow-summary {
  display: flex;
  gap: 24px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #606266;
}
.flow-summary b {
  color: #303133;
}
.in {
  color: #67c23a;
}
.out {
  color: #e6a23c;
}
.flow-sub {
  color: #909399;
  font-size: 12px;
}
</style>
