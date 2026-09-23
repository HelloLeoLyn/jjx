<template>
  <el-dialog
    title="批次库存（收发存）"
    v-model="dialogVisible"
    width="90%"
    append-to-body
    :close-on-click-modal="false"
    top="5vh"
    @close="handleClose"
  >
    <!-- 物料汇总信息 -->
    <el-card class="summary-card" shadow="never">
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="物料编码">{{ materialCode }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ specification }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ unit }}</el-descriptions-item>
        <el-descriptions-item label="累计入库">{{ fmt(summaryReceived) }}</el-descriptions-item>
        <el-descriptions-item label="累计出库">{{ fmt(summaryIssued) }}</el-descriptions-item>
        <el-descriptions-item label="结存数量">
          <b>{{ fmt(totalQuantity) }}</b>
        </el-descriptions-item>
        <el-descriptions-item label="可用数量">{{ fmt(availableQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="总预留数量">{{ fmt(totalReserved) }}</el-descriptions-item>
        <el-descriptions-item label="最早有效期">{{ earliestExpiry || '-' }}</el-descriptions-item>
      </el-descriptions>
      <!-- 口径说明：解决“只看到结存、看不出入过多少/怎么变的” -->
      <div class="ledger-tip">
        口径：<b>结存 = 累计入库 − 累计出库 ± 盘点调整</b>；入库原始量以「入库单明细」为准，
        每笔变动记录在「库存流水」里（只增不改）。点任一行「变动流水」可看该批次的每次进出与变动前后余额。
      </div>
    </el-card>

    <!-- 搜索栏 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" :inline="true" size="small">
        <el-form-item label="批次号">
          <el-input
            v-model="queryParams.batchNo"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in warehouseOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位">
          <el-select
            v-model="queryParams.locationId"
            placeholder="请选择库位"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in locationOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option label="生效" :value="1" />
            <el-option label="未生效" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="hideZero">仅看有结存</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
          <el-button :icon="Refresh" @click="refreshAll">刷新</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 批次库存表格（收发存三栏） -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="displayList" border style="width: 100%" max-height="420">
        <el-table-column label="批次号" prop="batchNo" width="150" show-overflow-tooltip />
        <el-table-column label="仓库" prop="warehouseName" width="100" />
        <el-table-column label="库位" width="100">
          <template #default="{ row }">{{ row.locationName || '-' }}</template>
        </el-table-column>
        <!-- 收发存三栏：入库/已出库为流水派生，结存=quantity -->
        <el-table-column label="入库数量" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.receivedQuantity === undefined" class="no-perm">-</span>
            <span v-else class="qty-in">{{ fmt(row.receivedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已出库" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.issuedQuantity === undefined" class="no-perm">-</span>
            <span v-else class="qty-out">{{ fmt(row.issuedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结存数量" prop="quantity" width="100" align="right" />
        <el-table-column label="预留数量" prop="reservedQuantity" width="90" align="right" />
        <el-table-column label="可用数量" prop="availableQuantity" width="90" align="right" />
        <el-table-column label="单位成本" prop="unitCost" width="90" align="right">
          <template #default="{ row }">
            {{ row.unitCost ? formatCurrency(row.unitCost) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="生产日期" prop="productionDate" width="100" align="center">
          <template #default="{ row }">
            {{ row.productionDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="有效期至" prop="expiryDate" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.expiryDate" :class="{ expiring: isExpiring(row.expiryDate) }">
              {{ row.expiryDate }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <!-- 0 结存不再显示“生效”，改为“已用尽”，避免误读 -->
            <el-tag v-if="isExhausted(row)" type="info" size="small">已用尽</el-tag>
            <el-tag v-else :type="StockItemStatusEnum.getTagProps(row.status).type" size="small">
              {{ row.statusName || StockItemStatusEnum.getLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后入库" prop="lastInboundTime" width="150" align="center" />
        <el-table-column label="最后出库" prop="lastOutboundTime" width="150" align="center" />
        <el-table-column label="操作" width="110" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="canViewFlow"
              link
              type="primary"
              size="small"
              @click="openFlow(row)"
            >变动流水</el-button>
            <span v-else class="no-perm">-</span>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 批次变动流水抽屉 -->
    <BatchTransactionDrawer
      v-model:visible="flowVisible"
      :inventory-item-id="flowInventoryItemId"
      :batch-no="flowBatchNo"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { stockItemApi } from '@/api/inventory/stockItem'
import { stockApi } from '@/api/inventory/stock'
import { formatCurrency } from '@/utils/format'
import { useUserStore } from '@/store/modules/user'
import { StockItemStatusEnum } from '@/enums/inventory/StockItemEnum'
import type {
  StockItemQueryParams,
  StockItemVO,
  StockVO,
  BatchFlowSummaryVO,
} from '@/types/inventory/stock'
import BatchTransactionDrawer from './BatchTransactionDrawer.vue'

/**
 * 批次库存（收发存口径，dev-20260923-017 一期）
 *
 * 展示口径（业内「收发存台账」）：
 *  - 入库数量 / 已出库 = 由库存流水（inventory_transaction）按批次聚合得到（唯一真源）；
 *  - 结存数量 = 批次表 inventory_stock_item.quantity（= 入库 − 出库 ± 调整），两者必须一致；
 *  - 「变动流水」抽屉展示每笔变动与变动前后余额（需 inventory:transaction:view）；
 *  - 0 结存显示「已用尽」，并提供「仅看有结存」过滤。
 * 设计依据：jjx-docs/design/stock-ledger-receiving-issuing-balance-dev-20260923-016.md
 */
const props = defineProps<{
  visible: boolean
  stockId?: string
  inventoryItemId?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const userStore = useUserStore()
const canViewFlow = computed(() => userStore.hasPermission('inventory:transaction:view'))

const dialogVisible = ref(props.visible)
watch(
  () => props.visible,
  (val) => {
    dialogVisible.value = val
    if (val && props.inventoryItemId) {
      queryParams.inventoryItemId = props.inventoryItemId
      getMaterialSummary()
      refreshAll()
    }
  }
)
watch(dialogVisible, (val) => {
  emit('update:visible', val)
})

// 物料汇总信息
const materialCode = ref('')
const materialName = ref('')
const specification = ref('')
const unit = ref('')
const totalQuantity = ref(0)
const totalReserved = ref(0)
const availableQuantity = ref(0)
const earliestExpiry = ref('')

// 查询参数
const queryParams = reactive<StockItemQueryParams>({
  current: 1,
  pageSize: 10,
  inventoryItemId: props.inventoryItemId || '',
  batchNo: '',
  warehouseId: undefined,
  locationId: undefined,
  status: undefined,
})

// 响应式数据
const loading = ref(false)
const itemList = ref<StockItemVO[]>([])
const total = ref(0)
const hideZero = ref(false)
/** 批次 → 收发存汇总（流水派生） */
const flowSummary = ref<Record<string, BatchFlowSummaryVO>>({})

// 变动流水抽屉
const flowVisible = ref(false)
const flowInventoryItemId = ref<string>()
const flowBatchNo = ref<string>()

// 仓库选项（示例，实际应从API获取）
const warehouseOptions = ref<{ value: string; label: string }[]>([])
const locationOptions = ref<{ value: string; label: string }[]>([])

function fmt(v?: number | string | null): string {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? String(Math.round(n * 100) / 100) : '0'
}

function isExhausted(row: StockItemVO): boolean {
  return Number(row.quantity || 0) <= 0
}

/** 累计入库/出库（该库存物品下所有批次，流水派生；与结存三栏同源） */
const summaryReceived = computed(() =>
  Object.values(flowSummary.value).reduce((s, v) => s + Number(v.receivedQuantity || 0), 0)
)
const summaryIssued = computed(() =>
  Object.values(flowSummary.value).reduce((s, v) => s + Number(v.issuedQuantity || 0), 0)
)

/** 表格展示数据：把流水派生的收/发并到批次行上 + 可选隐藏 0 结存 */
const displayList = computed(() => {
  const rows = itemList.value.map((row) => {
    const s = flowSummary.value[row.batchNo]
    return {
      ...row,
      receivedQuantity: s ? Number(s.receivedQuantity || 0) : undefined,
      issuedQuantity: s ? Number(s.issuedQuantity || 0) : undefined,
    }
  })
  return hideZero.value ? rows.filter((r) => Number(r.quantity || 0) !== 0) : rows
})

// 获取物料汇总信息
const getMaterialSummary = async () => {
  if (!props.stockId) return
  try {
    const res = await stockApi.getById(props.stockId)
    const data = res.data as StockVO
    if (data) {
      materialCode.value = data.materialCode || ''
      materialName.value = data.materialName || ''
      specification.value = data.specification || ''
      unit.value = data.unit || ''
      totalQuantity.value = data.totalQuantity || 0
      totalReserved.value = data.totalReserved || 0
      availableQuantity.value = data.availableQuantity || 0
      earliestExpiry.value = data.earliestExpiry || ''
    }
  } catch (error) {
    console.error('获取物料汇总信息失败:', error)
  }
}

// 批次收发存汇总（入库/出库/结存，来源=库存流水）
const getBatchSummary = async () => {
  if (!props.inventoryItemId) return
  try {
    const res = await stockItemApi.batchSummary(props.inventoryItemId)
    const map: Record<string, BatchFlowSummaryVO> = {}
    ;(res.data || []).forEach((it) => {
      map[it.batchNo] = it
    })
    flowSummary.value = map
  } catch (error) {
    console.error('获取批次收发存汇总失败:', error)
    flowSummary.value = {}
  }
}

// 获取批次明细列表
const getList = async () => {
  loading.value = true
  try {
    const res = await stockItemApi.list(queryParams)
    itemList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取批次明细失败:', error)
    ElMessage.error('获取批次明细失败')
  } finally {
    loading.value = false
  }
}

/** 列表 + 收发存汇总一起刷新 */
const refreshAll = async () => {
  await Promise.all([getList(), getBatchSummary()])
}

// 搜索
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.batchNo = ''
  queryParams.warehouseId = undefined
  queryParams.locationId = undefined
  queryParams.status = undefined
  hideZero.value = false
  getList()
}

// 打开变动流水
const openFlow = (row: StockItemVO) => {
  flowInventoryItemId.value = String(row.inventoryItemId || props.inventoryItemId || '')
  flowBatchNo.value = row.batchNo
  flowVisible.value = true
}

// 关闭
const handleClose = () => {
  dialogVisible.value = false
  flowVisible.value = false
}

// 判断是否临期（30天内）
const isExpiring = (dateStr: string): boolean => {
  if (!dateStr) return false
  const expiryDate = new Date(dateStr)
  const now = new Date()
  const diffDays = Math.ceil((expiryDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return diffDays <= 30 && diffDays >= 0
}
</script>

<style scoped>
.summary-card,
.search-card,
.table-card {
  margin-bottom: 12px;
}

.expiring {
  color: #e6a23c;
  font-weight: bold;
}

.ledger-tip {
  margin-top: 8px;
  padding: 6px 10px;
  background: #fdf6ec;
  border-radius: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #8a6d3b;
}

.qty-in {
  color: #67c23a;
}

.qty-out {
  color: #e6a23c;
}

.no-perm {
  color: #c0c4cc;
}
</style>
