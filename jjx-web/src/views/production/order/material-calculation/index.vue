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
      title="本页读取真实需求、材料档案和库存。当前用于选料计算及预览，尚不保存方案到服务器、不生成领料单、不预占或扣库存。"
    />
    <template v-if="order && !loadError">
      <div class="summary-grid">
        <div>
          <span>待安排材料项目</span><strong>{{ demands.length }}<small>项</small></strong>
        </div>
        <div class="accent">
          <span>本次已配足</span><strong>{{ completeCount }}<small>项</small></strong>
        </div>
        <div :class="{ shortage: gapCount > 0 }">
          <span>仍有需求缺口</span><strong>{{ gapCount }}<small>项</small></strong>
        </div>
        <div>
          <span>选用实际材料</span
          ><strong>{{ Object.keys(totals).length }}<small>种</small></strong>
        </div>
        <div :class="{ shortage: errors.length > 0 }">
          <span>待处理校验</span><strong>{{ errors.length }}<small>项</small></strong>
        </div>
      </div>
      <el-empty
        v-if="!demands.length && !loading"
        description="当前工单没有剩余领料需求，或尚无可领BOM材料"
      />
      <section v-for="(d, index) in demands" :key="d.id" class="demand-card">
        <div class="demand-heading">
          <div>
            <el-tag effect="plain">{{ index + 1 }}</el-tag>
            <h3>{{ d.original.name }}</h3>
          </div>
          <span>{{ d.original.code }} · 原始需求只读</span>
        </div>
        <div class="original-spec">
          <b>原始工程规格：{{ d.original.spec || '未填写' }}</b
          ><span>整单需求 {{ fmt(d.total) }} {{ d.original.unit }}</span
          ><span>已开领料 {{ fmt(d.opened) }} {{ d.original.unit }}</span
          ><b>本次可安排 {{ fmt(d.remaining) }} {{ d.original.unit }}</b>
        </div>
        <div class="demand-ledger">
          <span
            >原规格当前可用 <b>{{ fmt(materials[d.original.id]?.available || 0) }}</b>
            {{ d.original.unit }}</span
          ><span>已开领料沿用现有接口口径，包含待发占用，不等同已实际发料。</span>
        </div>
        <div class="allocation-title">
          <b>本次实际用料</b><span>可同时选择多种材料，各自抵扣上方原需求</span
          ><el-button type="primary" @click="openPicker(d)">＋ 从库存选择材料</el-button>
        </div>
        <div class="table-scroll">
          <table class="allocation-table">
            <thead>
              <tr>
                <th>实际材料 / 当前档案规格</th>
                <th>可用库存</th>
                <th>抵扣原需求</th>
                <th>换算系数</th>
                <th>额外损耗 %</th>
                <th>取料步长</th>
                <th>本次需领</th>
                <th></th>
              </tr>
            </thead>
            <tbody v-for="a in d.allocations" :key="a.id">
              <tr>
                <td class="material-cell">
                  <b>{{ materials[a.materialId]?.name }}</b
                  ><small>{{ materials[a.materialId]?.code }}</small
                  ><small>{{ materials[a.materialId]?.spec || '档案未填规格' }}</small
                  ><el-tag v-if="a.materialId !== d.original.id" size="small" type="warning"
                    >替换用料 · 需核对适用性</el-tag
                  >
                </td>
                <td>
                  <b>{{ fmt(materials[a.materialId]?.available || 0) }}</b>
                  {{ materials[a.materialId]?.unit
                  }}<el-button link type="primary" @click="showBatches(a.materialId)"
                    >查看批次</el-button
                  >
                </td>
                <td>
                  <el-input-number
                    v-model="a.coverage"
                    :min="0"
                    :precision="4"
                    :controls="false"
                    aria-label="抵扣原需求数量"
                  /><small>{{ d.original.unit }}（原规格）</small>
                </td>
                <td>
                  <el-input-number
                    v-model="a.ratio"
                    :disabled="a.materialId === d.original.id"
                    :min="0"
                    :precision="4"
                    :controls="false"
                    aria-label="原需求换算系数"
                  /><small
                    >1 {{ materials[a.materialId]?.unit }} 抵扣多少 {{ d.original.unit }}</small
                  >
                </td>
                <td>
                  <el-input-number
                    v-model="a.loss"
                    :disabled="a.materialId === d.original.id"
                    :min="0"
                    :max="100"
                    :precision="2"
                    :controls="false"
                    aria-label="替换额外损耗率"
                  />
                </td>
                <td>
                  <el-input-number
                    v-model="a.step"
                    :min="0.0001"
                    :precision="4"
                    :controls="false"
                    aria-label="实际取料步长"
                  /><small>{{ materials[a.materialId]?.unit }}</small>
                </td>
                <td class="quantity-cell">
                  <b>{{ fmt(quantity(a)) }}</b
                  ><small>{{ materials[a.materialId]?.unit }}</small>
                </td>
                <td>
                  <el-button
                    link
                    type="danger"
                    @click="d.allocations = d.allocations.filter((x) => x.id !== a.id)"
                    >移除</el-button
                  >
                </td>
              </tr>
              <tr class="basis-row">
                <td colspan="8">
                  <div>
                    <span>{{ a.materialId === d.original.id ? '计算说明' : '换料依据 *' }}</span
                    ><el-input
                      v-model="a.reason"
                      placeholder="填写规格变化、排料/模数换算依据；系统不自动认定材料可替代"
                    /><small
                      >需领 = {{ fmt(a.coverage) }} ÷ {{ fmt(a.ratio) }} × (1 +
                      {{ fmt(a.loss) }}%)，按步长取整</small
                    >
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
          <el-empty
            v-if="!d.allocations.length"
            description="原规格无可用库存，请从库存选择一种或多种材料"
            :image-size="60"
          />
        </div>
        <div class="demand-footer">
          <span
            >本次抵扣 <b>{{ fmt(planned(d)) }}</b> / {{ fmt(d.remaining) }}
            {{ d.original.unit }}</span
          ><strong :class="{ 'warning-text': Math.abs(planned(d) - d.remaining) > 0.00001 }">{{
            planned(d) > d.remaining
              ? '分配超出剩余需求，请调整'
              : planned(d) < d.remaining
                ? '仍缺 ' + fmt(d.remaining - planned(d)) + ' ' + d.original.unit
                : '本项需求已配足'
          }}</strong>
        </div>
      </section>
      <section v-if="demands.length" class="stock-card">
        <div class="section-heading">
          <h3>实际材料合计校验</h3>
          <span>同一材料跨项目合并检查可用库存，不合计不同材料的抵扣量</span>
        </div>
        <div class="stock-grid">
          <div
            v-for="(qty, id) in totals"
            :key="id"
            :class="{ 'stock-short': qty > (materials[id]?.available || 0) }"
          >
            <b>{{ materials[id]?.name }}</b
            ><span>{{ materials[id]?.spec }}</span
            ><span
              >需领 {{ fmt(qty) }} / 可用 {{ fmt(materials[id]?.available || 0) }}
              {{ materials[id]?.unit }}</span
            ><strong>{{
              qty > (materials[id]?.available || 0) ? '库存不足，请调整' : '当前库存足够'
            }}</strong>
          </div>
        </div>
        <p class="formula-note">
          原需求包含BOM原损耗，原规格按1:1抵扣。替换系数由计算员根据模数/排料确认，不能只按尺寸比例自动判断；仅加本次替换额外损耗。再次预览会刷新需求与库存。
        </p>
        <el-alert v-if="errors.length" type="error" :closable="false"
          ><ul class="issue-list">
            <li v-for="error in errors" :key="error">{{ error }}</li>
          </ul></el-alert
        >
      </section>
      <div v-if="demands.length" class="action-bar">
        <div>
          <b>{{ gapCount ? gapCount + '项仍有缺口，可先预览部分用料' : '全部项目已安排' }}</b
          ><small>页面调整尚未保存；刷新或切换工单后重新计算</small>
        </div>
        <el-button
          type="primary"
          :disabled="loading || errors.length > 0"
          :loading="checking"
          @click="preview"
          >刷新校验并预览本次用料</el-button
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

    <el-dialog v-model="previewVisible" title="本次用料计算结果 · 尚未生成领料单" width="92%">
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
      <el-alert
        type="info"
        :closable="false"
        title="本次只预览计算结果；正式方案持久化和替换材料的领料抵扣接口尚未实施，因此此处不创建真实领料单。"
      />
      <template #footer
        ><el-button @click="previewVisible = false">返回继续调整</el-button></template
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
    d.allocations.push(a)
    pickerVisible.value = false
  } catch (error) {
    pickerError.value = message(error)
  } finally {
    pickerLoading.value = false
  }
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
.allocation-table {
  width: 100%;
  min-width: 880px;
  border-collapse: collapse;
  font-size: 12px;
}
.allocation-table th {
  background: #fafbfd;
  padding: 12px 10px;
  text-align: left;
  color: #7e8897;
  font-weight: 500;
  white-space: nowrap;
}
.allocation-table td {
  padding: 13px 10px 7px;
  vertical-align: top;
}
.allocation-table th:first-child,
.allocation-table td:first-child {
  padding-left: 20px;
}
.allocation-table .material-cell {
  min-width: 235px;
}
.material-cell .el-select {
  width: 100%;
}
.allocation-table small {
  display: block;
  color: #8b95a5;
  margin-top: 7px;
  font-size: 11px;
}
.allocation-table .el-input-number {
  width: 94px;
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
