<template>
  <div class="delivery-workbench">
    <header class="page-heading"><div><h2>发货管理</h2><p>选择销售订单明细，安排本次发货</p></div></header>
    <el-card shadow="never" class="arrange-panel">
      <template #header><div class="section-heading"><div><strong>① 待安排发货明细</strong><span>生产中且有可安排量的销售订单</span></div><el-button link @click="expanded = !expanded">{{ expanded ? '收起' : '展开' }}</el-button></div></template>
      <div v-show="expanded">
        <el-alert v-if="orderId" :title="`当前查看销售单：${orderNo || orderId}`" type="info" :closable="false" style="margin-bottom: 12px"><el-button link type="primary" @click="emit('clearOrder')">查看全部订单</el-button></el-alert>
        <el-form inline @submit.prevent="search">
          <el-form-item label="客户 / 订单 / 产品"><el-input v-model="query.keyword" clearable placeholder="客户名、销售单号、客户料号或品名" style="width: 330px" @keyup.enter="search" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="loading" @click="search">查询</el-button><el-button @click="reset">重置</el-button><el-button :loading="loading" @click="reload">刷新</el-button></el-form-item>
        </el-form>
        <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" show-icon style="margin-bottom: 12px" />
        <div class="scope-note">同客户、同收货地址、同币种可合并；本次交货方式在建单时统一选择。不同交期可合并。</div>
        <el-table v-loading="loading" :data="rows" border max-height="360" row-key="id">
          <el-table-column width="55" align="center">
            <template #header><el-checkbox :model-value="allVisibleSelected" :indeterminate="someVisibleSelected && !allVisibleSelected" :disabled="!eligibleRows.length || loading" aria-label="选择当前可合并明细" @change="toggleAll" /></template>
            <template #default="{ row }"><el-checkbox :model-value="selected.some(item => item.id === row.id)" :disabled="!!blockedReason(row) || loading" :aria-label="`选择 ${row.orderNo} ${row.productCode}`" @change="toggleRow(row)" /></template>
          </el-table-column>
          <el-table-column label="客户 / 收货地址" min-width="210"><template #default="{ row }">{{ row.customerName }}<div class="secondary">{{ row.deliveryAddress || '建单时填写地址' }}</div></template></el-table-column>
          <el-table-column prop="orderNo" label="销售单号" min-width="150" />
          <el-table-column label="客户料号 / 品名" min-width="215"><template #default="{ row }">{{ row.customerMaterialNo || row.productName }}<div class="secondary">{{ row.productName }}</div></template></el-table-column>
          <el-table-column prop="currency" label="币种" width="75" />
          <el-table-column prop="dueDate" label="交期" width="110" />
          <el-table-column prop="quantity" label="订单量" width="85" align="right" />
          <el-table-column prop="shipped" label="已发量" width="85" align="right" />
          <el-table-column prop="occupied" label="待发占用" width="95" align="right" />
          <el-table-column prop="availableQuantity" label="本次可建单" width="105" align="right" />
          <el-table-column prop="orderRemainingQuantity" label="订单待安排" width="105" align="right" />
          <el-table-column prop="shortageQuantity" label="尚缺成品" width="95" align="right" />
          <el-table-column prop="stockAvailable" label="成品可用量" width="110" align="right" />
          <el-table-column label="安排条件" min-width="145"><template #default="{ row }"><el-tag :type="blockedReason(row) ? 'info' : 'success'" size="small">{{ blockedReason(row) || (row.shortageQuantity > 0 ? '可部分发货' : '可选择') }}</el-tag></template></el-table-column>
          <template #empty><el-empty :description="loadError ? '数据加载失败，请重试' : '暂无符合条件的待安排明细'" :image-size="65" /></template>
        </el-table>
        <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="reload" />
        <div class="selection-bar"><div>已选 <strong>{{ selected.length }}</strong> 条明细 · <strong>{{ selectedOrderCount }}</strong> 张订单 <span v-if="differentDueDates" class="due-warning">交期不同，请核对统一送货日期</span></div><div><el-button :disabled="!selected.length" @click="selected = []">清空选择</el-button><el-button v-hasPermi="['sales:order:edit']" type="primary" :disabled="!selected.length || loading" @click="dialog?.open(selected)">合并发货</el-button></div></div>
        <p class="stock-note">订单待安排量 = 订单量 − 已发量 − 待发占用；本次可建单量同时受可用成品限制。同产品合计校验，实际出库须OQC放行与仓库确认。</p>
      </div>
    </el-card>
    <el-card shadow="never" class="records-panel"><template #header><div class="section-heading"><strong>② 发货单记录</strong><span>建单 → OQC → 安排出库 → 仓库确认 → 签收</span></div></template><slot /></el-card>
    <MergeDeliveryDialog ref="dialog" @created="onCreated" />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { deliveryApi, type DeliveryArrangeLine } from '@/api/sales/delivery'
import MergeDeliveryDialog from './MergeDeliveryDialog.vue'
const props = defineProps<{ orderId?: number; orderNo?: string }>()
const emit = defineEmits<{ created: [deliveryId: number]; clearOrder: [] }>()
const dialog = ref<InstanceType<typeof MergeDeliveryDialog>>()
const rows = ref<DeliveryArrangeLine[]>([])
const selected = ref<DeliveryArrangeLine[]>([])
const loading = ref(false)
const loadError = ref('')
const total = ref(0)
const query = reactive({ keyword: '', orderId: props.orderId, pageNum: 1, pageSize: 30 })
const expanded = ref(true)
let loadSequence = 0
const group = computed(() => selected.value[0])
function blockedReason(row: DeliveryArrangeLine) {
  if (row.blockedReason) return row.blockedReason
  if (row.availableQuantity <= 0) return '暂无可用成品'
  const first = group.value
  if (!first) return ''
  if (row.customerId !== first.customerId) return '客户不同'
  if (row.deliveryAddress?.trim() !== first.deliveryAddress?.trim()) return '收货地址不同'
  if (row.currency !== first.currency) return '币种不同'
  return ''
}
const selectedOrderCount = computed(() => new Set(selected.value.map(line => line.orderId)).size)
const differentDueDates = computed(() => new Set(selected.value.map(line => line.dueDate)).size > 1)
const eligibleRows = computed(() => rows.value.filter(row => !blockedReason(row)))
const allVisibleSelected = computed(() => eligibleRows.value.length > 0 && eligibleRows.value.every(row => selected.value.some(item => item.id === row.id)))
const someVisibleSelected = computed(() => eligibleRows.value.some(row => selected.value.some(item => item.id === row.id)))
function toggleRow(row: DeliveryArrangeLine) {
  if (blockedReason(row)) return
  selected.value = selected.value.some(item => item.id === row.id) ? selected.value.filter(item => item.id !== row.id) : [...selected.value, row]
}
function toggleAll() {
  if (allVisibleSelected.value) {
    const ids = new Set(eligibleRows.value.map(row => row.id)); selected.value = selected.value.filter(row => !ids.has(row.id))
  } else {
    // 首行确立合并范围，后续逐行复核；不同客户不能被全选带入。
    for (const row of rows.value) if (!blockedReason(row) && !selected.value.some(item => item.id === row.id)) selected.value.push(row)
  }
}
async function reload() {
  const sequence = ++loadSequence; loading.value = true; loadError.value = ''
  try {
    const res = await deliveryApi.availableLines({ ...query })
    if (sequence !== loadSequence) return
    rows.value = res.data?.records || []; total.value = res.data?.total || 0
    // 当前页已选行同步最新数量，跨页选择仍由服务端最终复核。
    const latest = new Map(rows.value.map(row => [row.id, row]))
    selected.value = selected.value.map(row => latest.get(row.id) || row).filter(row => !row.blockedReason && row.availableQuantity > 0)
  } catch (error) {
    if (sequence !== loadSequence) return
    rows.value = []; total.value = 0; selected.value = []
    loadError.value = error instanceof Error ? error.message : '待安排明细加载失败，请重试'
  } finally { if (sequence === loadSequence) loading.value = false }
}
function search() { query.pageNum = 1; void reload() }
function reset() { selected.value = []; query.keyword = ''; search() }
async function onCreated(id: number) { selected.value = []; emit('created', id); await reload() }
watch(() => props.orderId, value => { query.orderId = value; query.pageNum = 1; selected.value = []; void reload() })
onMounted(reload)
defineExpose({ reload })
</script>
<style scoped>
.page-heading, .section-heading, .selection-bar { display:flex; align-items:center; justify-content:space-between; gap:16px; }
.page-heading { margin-bottom:16px; }.page-heading h2 { margin:0 0 6px; font-size:20px; color:#334155; }
.page-heading p,.section-heading span,.secondary,.scope-note,.stock-note { color:#64748b; font-size:12px; }
.page-heading p { margin:0; }.section-heading > div { display:flex; align-items:center; gap:16px; }
.arrange-panel,.records-panel { margin-top:16px; }.scope-note { margin-bottom:12px; }.secondary { margin-top:4px; }
.selection-bar { padding:14px 16px; background:#f1f5f9; border-radius:6px; margin-top:12px; font-size:13px; }
.selection-bar strong { color:#2563eb; }.stock-note { margin:10px 0 0; line-height:1.6; }.due-warning { color:#b45309; margin-left:12px; }
@media(max-width:850px) { .section-heading > div,.selection-bar { align-items:flex-start; flex-direction:column; }.due-warning { display:block; margin:6px 0 0; } }
</style>
