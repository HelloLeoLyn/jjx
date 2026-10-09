<template>
  <div class="calculation-page" v-loading="loading">
    <header class="page-heading">
      <div>
        <el-button link @click="router.push('/production/order')">← 返回生产订单</el-button>
        <h1>工单用料计算</h1>
        <p>先看原始需求，再从实际库存选择本次用料。</p>
      </div>
      <el-button :disabled="!order || loading" @click="reload">重新读取需求与库存</el-button>
    </header>
    <section class="order-card">
      <el-form inline
        ><el-form-item label="生产工单"
          ><el-select
            :model-value="selectedId"
            filterable
            style="width: 360px"
            placeholder="请选择已下达工单"
            @change="chooseOrder"
            ><el-option
              v-for="o in orders"
              :key="o.orderId"
              :value="String(o.orderId)"
              :label="o.orderNo + ' · ' + o.productName" /></el-select></el-form-item
      ></el-form>
      <template v-if="order">
        <div class="order-topline">
          <strong>{{ order.orderNo }}</strong
          ><el-tag effect="plain">真实工单数据</el-tag><span>来源计划 {{ parentNo || '—' }}</span>
        </div>
        <div class="order-meta">
          <div>
            <small>产品</small><b>{{ order.productName }} / {{ order.productCode }}</b>
          </div>
          <div>
            <small>工单数量</small><b>{{ fmt(order.plannedQuantity) }} {{ order.productUnit }}</b>
          </div>
          <div><small>需求依据</small><b>工单绑定BOM的领料预览</b></div>
          <div>
            <small>数据读取时间</small><b>{{ loadedAt || '—' }}</b>
          </div>
        </div>
        <el-alert v-if="order.remark" type="info" :closable="false" :title="order.remark" />
      </template>
    </section>
    <el-alert
      v-if="loadError"
      type="error"
      show-icon
      :closable="false"
      :title="loadError"
      class="demo-notice"
    />
    <el-alert
      type="info"
      :closable="false"
      class="demo-notice"
      title="需求按工单绑定 BOM 计算；替代料抵扣关系随领料明细保存，生成领料单时预占库存，确认发料后再扣减。"
    />
    <template v-if="order && !loadError">
      <div class="summary-line">{{ demands.length }} 项 BOM 材料　·　{{ completeCount }} 项配齐　·　{{ gapCount }} 项仍有缺口</div>
      <el-empty
        v-if="!demands.length && !loading"
        description="当前工单没有剩余领料需求，或尚无可领BOM材料"
      />
      <section class="allocation-sheet">
        <div class="table-scroll">
          <el-table :data="allocationRows" row-key="key" border class="allocation-table">
            <el-table-column label="BOM 原材料 / 规格" min-width="200">
            <template #default="{ row }">
              <b>{{ row.kind === 'bom' ? row.demand.original.name : '↳ 平替 ' + row.demand.original.name }}</b>
              <small>{{ row.demand.original.code }} · {{ row.demand.original.spec || '未填规格' }}</small>
            </template>
            </el-table-column>
            <el-table-column label="基数" width="72" align="center">
              <template #default="{ row }">
                <template v-if="row.kind === 'bom'">{{ fmt(row.demand.baseQty) }}</template>
                <el-input-number
                  v-else-if="row.allocation"
                  v-model="row.allocation.baseQty"
                  :min="0"
                  :precision="4"
                  :controls="false"
                  size="small"
                  aria-label="平替材料基数"
                  @change="updateSubstitution(row.allocation, row.demand)"
                />
              </template>
            </el-table-column>
            <el-table-column label="模数" width="72" align="center">
              <template #default="{ row }">
                <template v-if="row.kind === 'bom'">{{ fmt(row.demand.moduleQty) }}</template>
                <el-input-number
                  v-else-if="row.allocation"
                  v-model="row.allocation.moduleQty"
                  :min="0"
                  :precision="4"
                  :controls="false"
                  size="small"
                  aria-label="平替材料模数"
                  @change="updateSubstitution(row.allocation, row.demand)"
                />
              </template>
            </el-table-column>
            <el-table-column label="损耗率" width="100" align="center">
            <template #default="{ row }">
              <template v-if="row.kind === 'bom'">{{ row.demand.lossRate }}%</template>
              <el-input-number
                v-else-if="row.allocation"
                v-model="row.allocation.loss"
                :min="0"
                :max="100"
                :precision="2"
                :controls="false"
                size="small"
                @change="updateCoverage(row.allocation, row.demand)"
              />
            </template>
            </el-table-column>
            <el-table-column label="本次需求" min-width="130">
            <template #default="{ row }">
              <template v-if="row.kind === 'bom'">
                <b>{{ fmt(row.demand.remaining) }} {{ row.demand.original.unit }}</b>
                <small>已抵扣 {{ fmt(planned(row.demand)) }} · 尚缺 {{ fmt(Math.max(0, row.demand.remaining - planned(row.demand))) }}</small>
              </template>
              <span v-else>同上</span>
            </template>
            </el-table-column>
            <el-table-column label="本次安排材料" min-width="220">
            <template #default="{ row }">
              <template v-if="row.allocation">
                <b>{{ materials[row.allocation.materialId]?.name }}</b>
                <small>{{ materials[row.allocation.materialId]?.code }} · {{ materials[row.allocation.materialId]?.spec || '未填规格' }}</small>
                <template v-if="row.kind === 'substitute'">
                  <el-tag size="small" type="warning">替代 {{ row.demand.original.code }}</el-tag>
                  <small>抵扣系数 {{ fmt(row.allocation.ratio) }} {{ row.demand.original.unit }}/{{ materials[row.allocation.materialId]?.unit }}</small>
                  <el-input
                    v-model="row.allocation.reason"
                    size="small"
                    class="reason-input"
                    maxlength="500"
                    placeholder="替代依据（必填）"
                  />
                </template>
              </template>
              <span v-else>—</span>
            </template>
            </el-table-column>
            <el-table-column label="可用库存" width="120" align="right">
            <template #default="{ row }">
              <template v-if="row.allocation">{{ fmt(materials[row.allocation.materialId]?.available || 0) }} {{ materials[row.allocation.materialId]?.unit }}</template>
              <span v-else>—</span>
            </template>
            </el-table-column>
            <el-table-column label="本次领料量" width="170" align="right">
            <template #default="{ row }">
              <template v-if="row.allocation">
                <el-input-number
                  v-model="row.allocation.issueQuantity"
                  :min="0"
                  :precision="4"
                  :controls="false"
                  size="small"
                  aria-label="本次实际材料领料数量"
                  @change="updateCoverage(row.allocation, row.demand)"
                />
                {{ materials[row.allocation.materialId]?.unit }}
              </template>
              <span v-else>—</span>
            </template>
            </el-table-column>
            <el-table-column label="抵扣原需求" width="130" align="right">
            <template #default="{ row }">
              <b v-if="row.allocation" class="coverage-value">{{ fmt(row.allocation.coverage) }} {{ row.demand.original.unit }}</b>
              <span v-else>—</span>
            </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.kind === 'bom'" link type="primary" @click="openPicker(row.demand)">平替材料</el-button>
              <el-button
                v-if="row.allocation"
                link
                type="danger"
                @click="row.demand.allocations = row.demand.allocations.filter((a: Allocation) => a.id !== row.allocation?.id)"
              >移除</el-button>
            </template>
            </el-table-column>
          </el-table>
        </div>
        <p class="formula-note">BOM 需求按工单数量 × 基数 ÷ 模数并计入 BOM 损耗。平替材料的基数、模数可输入，抵扣系数按原 BOM 单位需求 ÷ 平替材料单位需求自动计算；抵扣原需求 = 领料量 × 抵扣系数 ÷ (1 + 损耗率)。平替损耗默认复制 BOM，可调整。</p>
      </section>
      <section v-if="demands.length" class="stock-card">
        <div class="section-heading"><h3>所选材料库存合计</h3></div>
        <el-table :data="stockSummaryRows" size="small" border>
          <el-table-column prop="name" label="实际材料" min-width="220" />
          <el-table-column prop="spec" label="规格" min-width="160" />
          <el-table-column label="本次需领" width="140"><template #default="{ row }">{{ fmt(row.required) }} {{ row.unit }}</template></el-table-column>
          <el-table-column label="可用库存" width="140"><template #default="{ row }">{{ fmt(row.available) }} {{ row.unit }}</template></el-table-column>
          <el-table-column label="校验" width="110"><template #default="{ row }"><el-tag :type="row.required > row.available ? 'danger' : 'success'">{{ row.required > row.available ? '库存不足' : '充足' }}</el-tag></template></el-table-column>
        </el-table>
        <el-alert v-if="errors.length" type="error" :closable="false"
          ><ul class="issue-list">
            <li v-for="error in errors" :key="error">{{ error }}</li>
          </ul></el-alert
        >
      </section>
      <div v-if="demands.length" class="action-bar">
        <div>
          <b>{{ gapCount ? gapCount + '项仍有缺口，可先预览部分用料' : '全部项目已安排' }}</b
          ><small>确认后生成领料单，并保存材料对应 BOM 项、抵扣量和替代依据</small>
        </div>
        <el-button
          type="primary"
          :disabled="loading || errors.length > 0"
          :loading="checking"
          @click="preview"
          >校验并预览领料单</el-button
        >
      </div>
    </template>

    <el-dialog v-model="pickerVisible" title="从实际库存选择材料" width="90%">
      <p v-if="pickerDemand">
        原需求：{{ pickerDemand.original.name }} / {{ pickerDemand.original.spec }}；还需分配
        {{ fmt(Math.max(0, pickerDemand.remaining - planned(pickerDemand))) }}
        {{ pickerDemand.original.unit }}
      </p>
      <el-form inline @submit.prevent="searchStock"
        ><el-form-item label="材料名称"
          ><el-input
            v-model="searchName"
            clearable
            placeholder="输入名称查询，可清空查看所有库存"
            @keyup.enter="searchStock" /></el-form-item
        ><el-button type="primary" @click="searchStock">查询</el-button
        ><el-button @click="showAllStock">查看全部库存</el-button></el-form
      >
      <el-alert v-if="pickerError" type="error" :closable="false" :title="pickerError" />
      <el-table v-loading="pickerLoading" :data="stockRows" border>
        <el-table-column prop="received" label="累计收" width="95" />
        <el-table-column prop="issued" label="累计发" width="95" />
        <el-table-column prop="materialCode" label="材料编码" width="155" /><el-table-column
          prop="materialName"
          label="材料名称"
          min-width="180"
        /><el-table-column
          prop="specification"
          label="实际档案规格"
          min-width="150"
        /><el-table-column prop="unit" label="单位" width="65" /><el-table-column
          prop="totalQuantity"
          label="结存"
          width="95"
        /><el-table-column prop="totalReserved" label="预留" width="90" /><el-table-column
          prop="availableQuantity"
          label="可用"
          width="100"
        />
        <el-table-column label="操作" width="110"
          ><template #default="{ row }"
            ><el-button
              link
              type="primary"
              :disabled="Number(row.availableQuantity) <= 0 || pickerLoading"
              @click="selectMaterial(row)"
              >选择材料</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-pagination
        v-model:current-page="stockPage"
        :total="stockTotal"
        :page-size="20"
        layout="total, prev, pager, next"
        @current-change="loadStocks"
        style="margin-top: 16px"
      />
    </el-dialog>

    <el-dialog v-model="batchVisible" :title="'实际批次 · ' + batchTitle" width="85%">
      <el-alert v-if="batchError" type="error" :closable="false" :title="batchError" />
      <el-table v-loading="batchLoading" :data="batchRows" border
        ><el-table-column prop="batchNo" label="批次" min-width="220" /><el-table-column
          prop="received"
          label="累计入库（流水）"
          width="150" /><el-table-column
          prop="issued"
          label="累计出库（流水）"
          width="150" /><el-table-column
          prop="balance"
          label="批次结存（流水）"
          width="150" /><el-table-column
          prop="availableQuantity"
          label="本库位可用"
          width="115" /><el-table-column prop="lastInboundTime" label="最近入库" min-width="170"
      /></el-table>
      <p>收／发／结存来自库存流水聚合；这里查看库存来源，不锁定或分配发料批次。</p>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="确认本次领料安排" width="92%">
      <div class="preview-header">
        <h2>{{ order?.orderNo }} · {{ order?.productName }}</h2>
        <p>原始规格与本次实际选择并列；库存已重新读取。</p>
      </div>
      <el-table :data="previewRows" border
        ><el-table-column prop="originalName" label="原始材料" min-width="180" /><el-table-column
          prop="originalSpec"
          label="原始工程规格"
          min-width="145" /><el-table-column
          prop="actualName"
          label="实际材料"
          min-width="180" /><el-table-column
          prop="actualSpec"
          label="当前选择规格"
          min-width="145" /><el-table-column
          prop="coverage"
          label="抵扣原需求"
          width="130" /><el-table-column
          prop="issue"
          label="本次需领"
          width="120" /><el-table-column prop="reason" label="换算依据" min-width="190"
      /></el-table>
      <p v-if="gapCount">仍有 {{ gapCount }} 项未配足，缺口保留在原需求下。</p>
      <template #footer
        ><el-button @click="previewVisible = false">返回继续调整</el-button>
        <el-button type="primary" :loading="submittingPick" @click="submitAllocations">确认生成领料单</el-button></template
      >
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'
import { OrderType } from '@/types/production/order'
import type { StockVO, StockItemVO, BatchFlowSummaryVO } from '@/types/inventory/stock'
import {
  allocation,
  buildDemands,
  materialTotals,
  planned,
  quantity,
  validationIssues,
  type Allocation,
  type Demand,
  type Material,
  type Order,
  type PickRow,
} from './model'

defineOptions({ name: 'WorkOrderMaterialCalculation' })
const router = useRouter(),
  route = useRoute()
const orders = ref<Order[]>([]),
  order = ref<Order | null>(null),
  selectedId = ref('')
const demands = ref<Demand[]>([]),
  materials = reactive<Record<string, Material>>({})
interface AllocationTableRow {
  key: string
  kind: 'bom' | 'substitute'
  demand: Demand
  allocation?: Allocation
}
const allocationRows = computed<AllocationTableRow[]>(() =>
  demands.value.flatMap((d) => {
    const originalAllocation = d.allocations.find((a) => a.materialId === d.original.id)
    const substituteRows = d.allocations
      .filter((a) => a.materialId !== d.original.id)
      .map((allocation) => ({
        key: allocation.id,
        kind: 'substitute' as const,
        demand: d,
        allocation,
      }))
    return [
      {
        key: `${d.id}-bom`,
        kind: 'bom' as const,
        demand: d,
        allocation: originalAllocation,
      },
      ...substituteRows,
    ]
  })
)
const parentNo = ref(''),
  loadedAt = ref(''),
  loading = ref(false),
  loadError = ref(''),
  checking = ref(false)
let generation = 0
async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await request.get(url, { params })
  return response.data as T
}
const message = (error: unknown) =>
  error instanceof Error ? error.message : '数据读取失败，请重试'
const fmt = (value: number) =>
  Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const totals = computed(() => materialTotals(demands.value))
const stockSummaryRows = computed(() => Object.entries(totals.value).map(([id, required]) => ({
  id,
  name: materials[id]?.name || '材料信息加载中',
  spec: materials[id]?.spec || '—',
  unit: materials[id]?.unit || '',
  required,
  available: materials[id]?.available || 0,
})))
const errors = computed(() => validationIssues(demands.value, materials))
const gapCount = computed(() => demands.value.filter((d) => planned(d) < d.remaining - 1e-8).length)
const completeCount = computed(
  () => demands.value.filter((d) => Math.abs(planned(d) - d.remaining) < 1e-8).length
)
async function readMaterial(id: string): Promise<Material> {
  const [m, stock] = await Promise.all([
    get<{ materialCode: string; materialName: string; specification: string; unit: string }>(
      '/inventory/material/' + id
    ),
    get<StockVO | null>('/inventory/stock/material/' + id),
  ])
  return {
    id,
    code: m.materialCode,
    name: m.materialName,
    spec: m.specification || '',
    unit: m.unit,
    available: Number(stock?.availableQuantity || 0),
    inventoryItemId: stock?.inventoryItemId ? String(stock.inventoryItemId) : undefined,
  }
}
async function load(id: string) {
  const run = ++generation
  loading.value = true
  loadError.value = ''
  selectedId.value = id
  order.value = null
  demands.value = []
  parentNo.value = ''
  loadedAt.value = ''
  pickerVisible.value = false
  previewVisible.value = false
  batchVisible.value = false
  for (const key of Object.keys(materials)) delete materials[key]
  try {
    const detail = await get<Order>('/production/order/' + id)
    if (detail.orderType.toUpperCase() !== OrderType.WORK_ORDER.toUpperCase())
      throw new Error('请选择已经下达的生产工单；生产计划需先转工单')
    const [rows, parent] = await Promise.all([
      get<PickRow[]>('/inventory/outbound/pick-preview/' + id),
      detail.parentOrderId
        ? get<Order>('/production/order/' + detail.parentOrderId)
        : Promise.resolve(null),
    ])
    const nextDemands = buildDemands(rows)
    const loaded = await Promise.all(
      [...new Set(nextDemands.map((d) => d.original.id))].map(readMaterial)
    )
    if (run !== generation) return
    for (const m of loaded) materials[m.id] = m
    demands.value = nextDemands
    order.value = detail
    parentNo.value = parent?.orderNo || ''
    loadedAt.value = new Date().toLocaleTimeString()
  } catch (error) {
    if (run === generation) loadError.value = message(error)
  } finally {
    if (run === generation) loading.value = false
  }
}
async function chooseOrder(id: string) {
  await router.replace({ path: route.path, query: { orderId: id } })
}
async function reload() {
  try {
    await ElMessageBox.confirm('重新读取会清除本页尚未保存的材料分配，是否继续？', '重新计算', {
      type: 'warning',
    })
  } catch {
    return
  }
  if (selectedId.value) await load(selectedId.value)
}
watch(
  () => route.query.orderId,
  (id) => {
    if (typeof id === 'string' && id !== selectedId.value) void load(id)
  }
)
onMounted(async () => {
  try {
    const list = await get<Order[] | { records: Order[] }>('/production/order/list', {
      orderType: OrderType.WORK_ORDER.toUpperCase(),
    })
    orders.value = Array.isArray(list) ? list : list.records
    const id = typeof route.query.orderId === 'string' ? route.query.orderId : ''
    if (id) await load(id)
    else if (orders.value.length === 1) await chooseOrder(String(orders.value[0].orderId))
  } catch (error) {
    loadError.value = message(error)
  }
})

const pickerVisible = ref(false),
  pickerLoading = ref(false),
  pickerError = ref('')
const pickerDemand = ref<Demand | null>(null),
  searchName = ref(''),
  stockPage = ref(1),
  stockTotal = ref(0)
const stockRows = ref<(StockVO & { received: string; issued: string })[]>([])
let stockRequest = 0
async function openPicker(d: Demand) {
  pickerDemand.value = d
  searchName.value = d.original.name
  stockPage.value = 1
  pickerVisible.value = true
  await loadStocks()
}
async function searchStock() {
  stockPage.value = 1
  await loadStocks()
}
async function showAllStock() {
  searchName.value = ''
  await searchStock()
}
async function loadStocks() {
  const run = ++stockRequest
  pickerLoading.value = true
  pickerError.value = ''
  try {
    const page = await get<{ records: StockVO[]; total: number }>('/inventory/stock/list', {
      current: stockPage.value,
      pageSize: 20,
      itemType: 'MATERIAL',
      materialName: searchName.value || undefined,
    })
    if (run !== stockRequest) return
    const withFlows = await Promise.all(
      page.records.map(async (stock) => {
        const flows = await get<BatchFlowSummaryVO[]>('/inventory/stock-item/batch-summary', {
          inventoryItemId: stock.inventoryItemId,
        })
        return {
          ...stock,
          received: fmt(flows.reduce((n, f) => n + Number(f.receivedQuantity), 0)),
          issued: fmt(flows.reduce((n, f) => n + Number(f.issuedQuantity), 0)),
        }
      })
    )
    if (run !== stockRequest) return
    stockRows.value = withFlows
    stockTotal.value = Number(page.total)
  } catch (error) {
    if (run === stockRequest) {
      stockRows.value = []
      pickerError.value = message(error)
    }
  } finally {
    if (run === stockRequest) pickerLoading.value = false
  }
}
async function selectMaterial(stock: StockVO) {
  const d = pickerDemand.value,
    run = generation
  if (!d || !stock.materialId) return
  const id = String(stock.materialId)
  if (d.allocations.some((a) => a.materialId === id)) {
    ElMessage.info('本项目已选择该材料，请直接调整已有行')
    return
  }
  pickerLoading.value = true
  try {
    const m = await readMaterial(id)
    if (run !== generation) return
    if (m.available <= 0) {
      ElMessage.warning('该材料已无可用库存，请重新选择')
      return
    }
    materials[id] = m
    const a = allocation(id, Math.max(0, d.remaining - planned(d)))
    // 替换材料的比例不根据尺寸或历史测试数据猜测，计算员必须明确填写。
    if (id !== d.original.id) a.ratio = 0
    if (id !== d.original.id) {
      a.loss = d.lossRate
      a.issueQuantity = 0
      a.coverage = 0
      updateSubstitution(a, d)
    }
    d.allocations.push(a)
    pickerVisible.value = false
  } catch (error) {
    pickerError.value = message(error)
  } finally {
    pickerLoading.value = false
  }
}
function updateCoverage(a: Allocation, d: Demand) {
  const issue = Number(a.issueQuantity) || 0
  if (a.materialId === d.original.id) {
    a.coverage = issue
    return
  }
  a.coverage = a.ratio > 0
    ? Number((issue * a.ratio / (1 + a.loss / 100)).toFixed(4))
    : 0
}
function updateSubstitution(a: Allocation, d: Demand) {
  const originalRate = d.baseQty > 0 && d.moduleQty > 0 ? d.baseQty / d.moduleQty : 0
  const substituteRate = a.baseQty > 0 && a.moduleQty > 0 ? a.baseQty / a.moduleQty : 0
  a.ratio = originalRate > 0 && substituteRate > 0 ? Number((originalRate / substituteRate).toFixed(6)) : 0
  updateCoverage(a, d)
}

const batchVisible = ref(false),
  batchLoading = ref(false),
  batchTitle = ref(''),
  batchError = ref('')
const batchRows = ref<(StockItemVO & { received: string; issued: string; balance: string })[]>([])
let batchRequest = 0
async function showBatches(id: string) {
  const run = ++batchRequest
  batchVisible.value = true
  batchLoading.value = true
  batchError.value = ''
  batchRows.value = []
  batchTitle.value = materials[id].name
  try {
    const m = await readMaterial(id)
    const [batches, flows] = await Promise.all([
      get<StockItemVO[]>('/inventory/stock-item/material/' + id),
      m.inventoryItemId
        ? get<BatchFlowSummaryVO[]>('/inventory/stock-item/batch-summary', {
            inventoryItemId: m.inventoryItemId,
          })
        : Promise.resolve([]),
    ])
    if (run !== batchRequest) return
    batchRows.value = batches.map((b) => {
      const f = flows.find((x) => x.batchNo === b.batchNo)
      return {
        ...b,
        received: f ? fmt(f.receivedQuantity) : '—',
        issued: f ? fmt(f.issuedQuantity) : '—',
        balance: f ? fmt(f.balanceQuantity) : '—',
      }
    })
  } catch (error) {
    if (run === batchRequest) batchError.value = message(error)
  } finally {
    if (run === batchRequest) batchLoading.value = false
  }
}
const previewVisible = ref(false)
const submittingPick = ref(false)
const previewRows = ref<
  {
    originalName: string
    originalSpec: string
    actualName: string
    actualSpec: string
    coverage: string
    issue: string
    reason: string
  }[]
>([])
async function preview() {
  if (!order.value || checking.value) return
  checking.value = true
  const run = generation
  try {
    const [rows, loaded] = await Promise.all([
      get<PickRow[]>('/inventory/outbound/pick-preview/' + order.value.orderId),
      Promise.all(Object.keys(totals.value).map(readMaterial)),
    ])
    if (run !== generation) return
    for (const m of loaded) materials[m.id] = m
    const fresh = buildDemands(rows)
    const oldSignature = demands.value.map((d) => [
      d.original.id,
      d.original.spec,
      d.total,
      d.opened,
      d.remaining,
    ])
    const newSignature = fresh.map((d) => [
      d.original.id,
      d.original.spec,
      d.total,
      d.opened,
      d.remaining,
    ])
    if (JSON.stringify(oldSignature) !== JSON.stringify(newSignature)) {
      ElMessage.warning('工单需求已变化，请点击重新读取后再计算')
      return
    }
    loadedAt.value = new Date().toLocaleTimeString()
    if (errors.value.length) {
      ElMessage.warning('库存或分配校验未通过，请处理页面提示')
      return
    }
    previewRows.value = demands.value.flatMap((d) =>
      d.allocations.map((a) => ({
        originalName: d.original.name,
        originalSpec: d.original.spec || '未填写',
        actualName: materials[a.materialId].name,
        actualSpec: materials[a.materialId].spec || '未填写',
        coverage: fmt(a.coverage) + ' ' + d.original.unit,
        issue: fmt(quantity(a)) + ' ' + materials[a.materialId].unit,
        reason: a.reason || '原规格按BOM需求领料',
      }))
    )
    previewVisible.value = true
  } catch (error) {
    ElMessage.error(message(error))
  } finally {
    checking.value = false
  }
}
async function submitAllocations() {
  if (!order.value || submittingPick.value) return
  if (errors.value.length) {
    ElMessage.warning('请先处理表格中的校验问题')
    return
  }
  submittingPick.value = true
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    const items = demands.value.flatMap((d) => d.allocations.map((a) => ({
      bomItemId: Number(d.bomItemId),
      materialId: Number(a.materialId),
      quantity: quantity(a),
      coverageQuantity: Number(a.coverage),
      allocationRatio: Number(a.ratio),
      allocationLossRate: Number(a.loss),
      allocationReason: a.reason.trim(),
    })))
    const response = await materialPickApi.createCalculatedProductionPick(Number(order.value.orderId), items)
    ElMessage.success(`领料单已生成（出库单 ${response?.data}）`)
    previewVisible.value = false
    await load(String(order.value.orderId))
  } catch (error: any) {
    ElMessage.error(error?.message || '生成领料单失败')
  } finally {
    submittingPick.value = false
  }
}
</script>

<style scoped lang="scss">
.calculation-page {
  padding: 24px;
  background: #f4f6f9;
  color: #243247;
  min-height: 100%;
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}
h1 {
  font-size: 25px;
  margin: 10px 0 8px;
  display: flex;
  align-items: center;
  gap: 14px;
}
h2 {
  font-size: 18px;
  margin: 0 0 8px;
}
h3 {
  font-size: 15px;
  margin: 0;
}
p {
  line-height: 1.7;
}
.page-heading p {
  color: #718096;
  margin: 0;
}
.demo-notice {
  margin-bottom: 20px;
}
.summary-line {
  margin: 8px 0 12px;
  color: #6f7d8e;
  font-size: 13px;
}
.workspace {
  display: grid;
  grid-template-columns: 230px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}
.scenario-panel {
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  overflow: hidden;
  position: sticky;
  top: 16px;
}
.panel-caption {
  padding: 19px 16px;
  font-weight: 700;
  border-bottom: 1px solid #edf0f5;
  display: flex;
  justify-content: space-between;
}
.panel-caption span {
  font-size: 12px;
  color: #8491a3;
  font-weight: 400;
}
.scenario-button {
  width: 100%;
  display: flex;
  gap: 10px;
  align-items: start;
  border: 0;
  border-left: 3px solid transparent;
  background: #fff;
  text-align: left;
  padding: 15px 12px;
  cursor: pointer;
  color: #4a5667;
  font: inherit;
}
.scenario-button:hover {
  background: #f5f8fc;
}
.scenario-button.selected {
  background: #edf5ff;
  border-left-color: #2878da;
  color: #175db3;
}
.scenario-button strong {
  display: block;
  font-size: 13px;
  margin-bottom: 5px;
}
.scenario-button small {
  font-size: 11px;
  color: #7d899b;
}
.scenario-number {
  font-size: 11px;
  padding-top: 2px;
  color: #94a1b3;
}
.scenario-tip {
  margin: 8px 15px 18px;
  font-size: 12px;
  line-height: 1.7;
  color: #8a94a3;
}
.order-card,
.demand-card,
.stock-card,
.trace-card {
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  margin-bottom: 18px;
  overflow: hidden;
}
.order-card {
  padding: 20px 22px;
}
.order-topline {
  display: flex;
  align-items: center;
  gap: 12px;
}
.order-topline > strong {
  font-size: 17px;
}
.order-topline > span:last-child {
  margin-left: auto;
  color: #6c7a8b;
  font-size: 13px;
}
.order-meta {
  display: grid;
  grid-template-columns: 2fr 1fr 1.3fr 1fr;
  gap: 14px;
  padding: 20px 0;
}
.order-meta small {
  display: block;
  color: #8390a2;
  margin-bottom: 8px;
}
.order-meta b {
  font-size: 13px;
  font-weight: 500;
}
.flow {
  display: flex;
  gap: 22px;
  padding-top: 16px;
  border-top: 1px solid #eef1f5;
  font-size: 12px;
  color: #929cac;
}
.flow b {
  color: #2878da;
}
.scenario-intro {
  padding: 2px 0 12px;
}
.scenario-intro p {
  color: #768195;
  font-size: 13px;
  margin: 0;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 18px;
}
.summary-grid > div {
  padding: 16px;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
}
.summary-grid span {
  color: #7e899a;
  font-size: 12px;
}
.summary-grid strong {
  display: block;
  font-size: 26px;
  margin-top: 10px;
  font-variant-numeric: tabular-nums;
}
.summary-grid small {
  font-size: 11px;
  font-weight: 400;
  margin-left: 7px;
  color: #8b97a7;
}
.summary-grid .accent {
  background: #eff6ff;
  border-color: #cde1fb;
  color: #2268ba;
}
.summary-grid .shortage {
  background: #fff8ec;
  border-color: #f5dfb6;
  color: #b87316;
}
.demand-heading {
  padding: 18px 20px 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}
.demand-heading > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.demand-heading > span {
  color: #8a95a4;
  font-size: 12px;
}
.original-spec {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  margin: 0 20px;
  padding: 14px 16px;
  background: #f6f8fb;
  border-radius: 6px;
  font-size: 12px;
  color: #7a8697;
}
.original-spec b {
  color: #48556a;
}
.demand-ledger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 22px;
  padding: 14px 20px;
  color: #7c8796;
  font-size: 12px;
}
.demand-ledger b {
  color: #33445d;
  margin-left: 5px;
}
.allocation-title {
  padding: 16px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  border-top: 1px solid #edf0f4;
  font-size: 13px;
}
.allocation-title > span {
  color: #8a95a3;
  font-size: 12px;
}
.allocation-title .el-button {
  margin-left: auto;
}
.table-scroll {
  overflow-x: auto;
}
.allocation-sheet {
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 10px;
  margin-bottom: 18px;
  overflow: hidden;
}
.allocation-table {
  width: 100%;
  min-width: 1420px;
  font-size: 12px;
}
function updateSubstitution(a: Allocation, d: Demand) {
  const originalRate = d.baseQty > 0 && d.moduleQty > 0 ? d.baseQty / d.moduleQty : 0
  const substituteRate = a.baseQty > 0 && a.moduleQty > 0 ? a.baseQty / a.moduleQty : 0
  a.ratio = originalRate > 0 && substituteRate > 0 ? Number((originalRate / substituteRate).toFixed(6)) : 0
  updateCoverage(a, d)
}
.allocation-table :deep(.el-table__header th) {
  background: #fafbfd;
  text-align: left;
  color: #7e8897;
  font-weight: 500;
  white-space: nowrap;
}
.allocation-table :deep(.el-table__body td) {
  vertical-align: middle;
}
.allocation-table b {
  display: block;
  color: #26384d;
}
.allocation-table :deep(.el-table__body .cell) {
  line-height: 1.45;
}
.allocation-table :deep(.el-table__body .cell > b) {
  white-space: nowrap;
}
.allocation-table small {
  display: block;
  color: #8b95a5;
  margin-top: 7px;
  font-size: 11px;
}
.allocation-table .el-input-number {
  width: 62px;
}
.reason-input {
  width: 180px;
  margin-top: 5px;
}
.quantity-cell {
  white-space: nowrap;
}
.quantity-cell b {
  font-size: 20px;
  color: #246bbf;
}
.basis-row td {
  padding-bottom: 16px;
  border-bottom: 1px solid #edf0f4;
}
.basis-row td > div {
  display: flex;
  align-items: center;
  gap: 12px;
}
.basis-row span {
  white-space: nowrap;
  color: #8590a0;
}
.basis-row .el-input {
  max-width: 440px;
}
.basis-row small {
  margin: 0;
}
.demand-footer {
  display: flex;
  justify-content: space-between;
  padding: 15px 20px;
  font-size: 12px;
  color: #7c8796;
}
.demand-footer strong {
  color: #27816a;
}
.demand-footer b {
  color: #2878da;
}
.demand-footer .warning-text {
  color: #be7a1e;
}
.stock-card,
.trace-card {
  padding: 20px;
}
.section-heading {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 16px;
}
.section-heading span {
  font-size: 12px;
  color: #8893a3;
}
.stock-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 12px;
}
.stock-grid > div {
  border: 1px solid #e7edf3;
  background: #fafcfe;
  padding: 14px;
  border-radius: 6px;
  font-size: 12px;
}
.stock-grid span {
  display: block;
  color: #8894a2;
  margin: 8px 0;
}
.stock-grid strong {
  color: #26816a;
  font-weight: 500;
}
.stock-grid .stock-short {
  background: #fff6f3;
  border-color: #f5cdbf;
}
.stock-short strong {
  color: #ce6247;
}
.formula-note {
  font-size: 12px;
  color: #8994a4;
  margin: 14px 0 0;
}
.issue-list {
  padding-left: 18px;
  margin: 5px 0;
}
.stock-card .el-alert {
  margin-top: 12px;
}
.trace-event {
  display: flex;
  gap: 12px;
  margin-top: 18px;
  font-size: 12px;
}
.trace-dot {
  height: 8px;
  width: 8px;
  border-radius: 50%;
  background: #98b8da;
  margin-top: 5px;
  flex-shrink: 0;
}
.trace-event p {
  color: #8a95a3;
  margin: 6px 0 0;
}
.action-bar {
  position: sticky;
  bottom: 0;
  z-index: 5;
  background: #fff;
  padding: 16px 20px;
  border: 1px solid #e4e9f0;
  border-radius: 8px;
  box-shadow: 0 -5px 20px #283b5010;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.action-bar b {
  font-size: 13px;
}
.action-bar small {
  display: block;
  margin-top: 6px;
  color: #8a95a3;
  font-size: 11px;
}
.history-card {
  padding: 22px 0 4px;
}
.history-card h3 {
  margin-top: 14px;
}
.history-card p {
  color: #778394;
  font-size: 13px;
}
.preview-header {
  text-align: center;
  margin-bottom: 20px;
}
.preview-header p {
  color: #8691a1;
  font-size: 13px;
}
@media (max-width: 1200px) {
  .workspace {
    grid-template-columns: 195px minmax(0, 1fr);
    gap: 14px;
  }
  .calculation-page {
    padding: 16px;
  }
  .summary-grid > div {
    padding: 12px;
  }
  .order-meta {
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 800px) {
  .page-heading {
    align-items: start;
    flex-direction: column;
  }
  h1 {
    font-size: 21px;
    flex-wrap: wrap;
  }
  .workspace {
    display: block;
  }
  .scenario-panel {
    position: static;
    display: flex;
    overflow-x: auto;
    margin-bottom: 16px;
  }
  .panel-caption,
  .scenario-tip {
    display: none;
  }
  .scenario-button {
    min-width: 210px;
  }
  .summary-grid {
    grid-template-columns: repeat(3, 1fr);
  }
  .flow {
    gap: 10px;
    flex-wrap: wrap;
  }
  .action-bar {
    position: static;
    align-items: start;
    flex-direction: column;
  }
  .allocation-title > span,
  .demand-heading > span {
    display: none;
  }
  .section-heading {
    align-items: start;
    flex-direction: column;
  }
}
</style>
