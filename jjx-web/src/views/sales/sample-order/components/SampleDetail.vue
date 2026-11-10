<template>
  <component :is="mode === 'dialog' ? ElDialog : 'div'" v-bind="dialogProps" @open="onOpen">
    <div v-loading="loading" class="sample-detail">
      <template v-if="data">
        <template v-if="!costOnly">
        <!-- 主体信息 -->
        <el-descriptions :column="2" border>
          <el-descriptions-item label="样品单号" :span="2">{{ data.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{ data.customerName }}</el-descriptions-item>
          <el-descriptions-item label="来源类型">{{
            sourceTypeLabel(data.sourceType)
          }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ data.sourceNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{
            data.contactPerson || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="样品状态">
            <el-tag :type="statusTagType(data.sampleStatus)" size="small">
              {{ statusLabel(data.sampleStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="迭代轮次"
            >Round {{ data.sampleRound || 1 }}</el-descriptions-item
          >
          <el-descriptions-item label="打样数量">{{ data.sampleQty || '-' }}</el-descriptions-item>
          <el-descriptions-item label="送样日期">{{
            data.sampleSendDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="快递单号">{{
            data.sampleTrackingNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="客户确认日期">{{
            data.sampleConfirmDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="客户确认人">{{
            data.sampleClientName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="转量产订单ID">{{
            data.convertedOrderId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="转量产时间">{{
            data.convertOrderTime || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            data.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 产品明细 -->
        <el-divider content-position="left">产品明细</el-divider>
        <el-table
          v-if="products.length"
          :data="products"
          size="small"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column prop="productCode" label="产品编码" width="120" />
          <el-table-column prop="productName" label="产品名称" min-width="140" />
          <el-table-column prop="specification" label="规格/要求" min-width="140" />
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        </el-table>
        <div v-else class="empty-tip">暂无产品明细</div>

        <AttachmentDocList
          source-type="sample"
          :source-id="data.orderId ?? undefined"
          :trace-id="data.traceId || undefined"
        />
        </template>

      <div v-if="canReadPickCost" v-loading="pickCostLoading">
        <div class="pick-cost-fields">
          <div>打样轮次：
            <el-select v-model="pickCostRound" clearable placeholder="全部轮次" style="width: 160px" @change="loadPickCost" :disabled="pickCostRecalculating">
              <el-option v-for="round in data?.sampleRound || 1" :key="round" :value="round" :label="`Round ${round}`" />
            </el-select>
          </div>
          <div>领料成本合计：
            <strong>{{ pickCostSummary ? formatPickCost(pickCostSummary.totalAmount) : '-' }}</strong>
          </div>
          <div>当前打样成本：{{ formatPickCost(data?.sampleCost) }}</div>
        </div>
        <el-button v-hasPermi="['sales:sample:engineering', 'engineering:sample:workbench']"
          type="primary" :loading="pickCostRecalculating" :disabled="!canRecalculatePickCost" @click="recalculatePickCost">
          按领料重算打样成本
        </el-button>
        <p>仅汇总已确认发料的领料单；工时单独记录，不并入领料成本。</p>
        <el-alert v-if="pickCostSummary?.missingCostCount" type="warning" :closable="false"
          :title="`${pickCostSummary.missingCostCount} 条明细缺少成本；已知金额小计 ${formatPickCost(pickCostSummary.knownAmount)}，不能重算打样成本。`" />
        <el-alert v-if="pickCostSummary && !pickCostSummary.items?.length" type="info" :closable="false"
          title="当前轮次还没有已发料的领料单，尚未产生领料成本：请先生成领料单并完成发料，或切换「全部轮次」查看。" />
        <el-alert v-if="pickCostRound && pickCostSummary?.unknownRoundOrderCount" type="warning" :closable="false"
          :title="`${pickCostSummary.unknownRoundOrderCount} 张历史领料单缺少轮次，未计入本轮，请选择全部轮次后重算。`" />
        <el-table :data="pickCostSummary?.items || []" border style="margin-top: 12px">
          <el-table-column prop="outboundNo" label="领料单号" min-width="150" />
          <el-table-column prop="materialCode" label="物料编码" min-width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column prop="batchNo" label="批次" min-width="130" />
          <el-table-column prop="quantity" label="数量" width="90" align="right" />
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">{{ formatPickCost(row.unitPrice, 4) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100" align="right">
            <template #default="{ row }">{{ formatPickCost(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </div>
      </template>
    </div>

    <template v-if="mode === 'dialog'" #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </component>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElDialog, ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import type { SamplePickCostSummary } from '@/types/sales/sampleOrder'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleOrderStatusEnum } from '@/enums/sales'
import type { TagType } from '@/types'
import type { SampleDisplayMode, SampleOrderApiLike, SampleOrder, SampleProductItem } from './types'
import { sourceTypeLabel } from './types'

defineOptions({ name: 'SampleDetail' })

interface Props {
  costOnly?: boolean
  mode?: SampleDisplayMode
  visible?: boolean
  /** 订单ID（优先） */
  orderId?: number | null
  /** 外部已加载的订单数据（传入则跳过 getInfo） */
  order?: SampleOrder | null
  /** 外部已加载的产品明细（传入则跳过 getProducts） */
  products?: SampleProductItem[] | null
  /** 自定义 API */
  api?: Partial<SampleOrderApiLike>
  width?: string
  title?: string
  /** 打开时是否重新拉取最新数据 */
  refreshOnOpen?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'dialog',
  costOnly: false,
  visible: false,
  orderId: null,
  order: null,
  products: null,
  api: undefined,
  width: '820px',
  title: '样品单详情',
  refreshOnOpen: true,
})

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'close'): void
  (e: 'cost-recalculated'): void
  (e: 'loaded', payload: { order: SampleOrder; products: SampleProductItem[] }): void
}>()

const api = computed<SampleOrderApiLike>(() => ({
  getInfo: (id) => sampleOrderApi.getInfo(id),
  getProducts: (id) => sampleOrderApi.getProducts(id),
  create: (payload) => sampleOrderApi.create(payload),
  update: (id, payload) => sampleOrderApi.update(id, payload),
  ...(props.api || {}),
}))

const loading = ref(false)
const data = ref<SampleOrder | null>(props.order ?? null)
const products = ref<SampleProductItem[]>(props.products ?? [])

const dialogProps = computed(() => {
  if (props.mode !== 'dialog') return {}
  return {
    modelValue: props.visible,
    title: props.title,
    width: props.width,
    appendToBody: true,
    'onUpdate:modelValue': (v: boolean) => emit('update:visible', v),
  }
})

// ====== 加载数据 ======
async function loadData() {
  // 优先使用外部数据
  if (props.order && !props.refreshOnOpen) {
    data.value = props.order
    products.value = props.products ?? []
    return
  }
  const id = props.orderId ?? props.order?.orderId
  if (!id) return
  loading.value = true
  try {
    const [infoRes, prodRes]: any[] = await Promise.all([
      api.value.getInfo(id),
      props.costOnly ? Promise.resolve({ data: [] }) : api.value.getProducts(id),
    ])
    data.value = infoRes?.data || null
    products.value = prodRes?.data || []
    emit('loaded', { order: data.value as SampleOrder, products: products.value })
  } catch (e: any) {
    ElMessage.error(e?.message || '加载样品单详情失败')
  } finally {
    loading.value = false
  }
}

const userStore = useUserStore()
const pickCostLoading = ref(false)
const pickCostRecalculating = ref(false)
const pickCostRound = ref<number>()
const pickCostSummary = ref<SamplePickCostSummary | null>(null)
let pickCostRequestId = 0
const canReadPickCost = computed(() => userStore.permissions.some(permission =>
  ['*', '*:*:*', 'sales:sample:view', 'engineering:sample:workbench'].includes(permission)))
const canRecalculatePickCost = computed(() => !pickCostLoading.value && !!pickCostSummary.value?.items.length
  && pickCostSummary.value.totalAmount != null
  && !(pickCostRound.value && pickCostSummary.value.unknownRoundOrderCount))

function formatPickCost(value?: number | null, precision = 2) {
  return value == null ? '未计价' : Number(value).toFixed(precision)
}

async function loadPickCost() {
  const id = data.value?.orderId
  const requestId = ++pickCostRequestId
  pickCostSummary.value = null
  pickCostLoading.value = false
  if (!id || !canReadPickCost.value || (props.mode === 'dialog' && !props.visible)) return
  pickCostLoading.value = true
  try {
    const res = await sampleOrderApi.getPickCost(id, pickCostRound.value)
    if (requestId === pickCostRequestId) pickCostSummary.value = res.data
  } catch (error: any) {
    if (requestId === pickCostRequestId) ElMessage.error(error?.message || '读取领料成本失败')
  } finally {
    if (requestId === pickCostRequestId) pickCostLoading.value = false
  }
}

async function recalculatePickCost() {
  if (!canRecalculatePickCost.value || pickCostRecalculating.value || !data.value) return
  const order = data.value
  const round = pickCostRound.value
  // 确认期间也禁用按钮，避免重复弹窗、切换单据后误写。
  pickCostRecalculating.value = true
  try {
    await ElMessageBox.confirm(
      `将${order.orderNo}的打样成本 ${formatPickCost(order.sampleCost)} 替换为${round ? `Round ${round}` : '全部轮次'}领料成本合计 ${formatPickCost(pickCostSummary.value?.totalAmount)}，工时保持独立。是否继续？`,
      '按领料重算打样成本', { type: 'warning', confirmButtonText: '确认重算', cancelButtonText: '取消' }
    )
  } catch {
    pickCostRecalculating.value = false
    return
  }
  if (data.value?.orderId !== order.orderId || (props.mode === 'dialog' && !props.visible)) {
    pickCostRecalculating.value = false
    return
  }
  try {
    const res = await sampleOrderApi.recalculatePickCost(order.orderId, round)
    if (data.value?.orderId === order.orderId) {
      data.value = { ...data.value, sampleCost: res.data.totalAmount ?? undefined }
      if (pickCostRound.value === round) pickCostSummary.value = res.data
    }
    ElMessage.success('打样成本已按领料重算')
    emit('cost-recalculated')
  } catch (error: any) {
    ElMessage.error(error?.message || '重算打样成本失败')
  } finally {
    pickCostRecalculating.value = false
  }
}

watch([() => data.value?.orderId, () => props.visible, canReadPickCost], () => {
  pickCostRound.value = undefined
  void loadPickCost()
}, { immediate: true })

// 打开弹窗时加载
function onOpen() {
  if (props.refreshOnOpen) loadData()
}

// 监听 visible
watch(
  () => props.visible,
  (v) => {
    if (props.mode !== 'dialog') return
    if (v && !props.refreshOnOpen) {
      // 不刷新时用外部数据
      data.value = props.order ?? data.value
      products.value = props.products ?? products.value
    }
  }
)

// 监听 orderId 变化（inline 模式）
watch(
  () => props.orderId,
  (v) => {
    if (props.mode === 'inline' && v) loadData()
  }
)

// 监听外部 order 变化
watch(
  () => props.order,
  (v) => {
    if (v && !props.refreshOnOpen) {
      data.value = v
      products.value = props.products ?? []
    }
  }
)

// inline 模式立即加载
if (props.mode === 'inline') {
  if (props.order && !props.refreshOnOpen) {
    data.value = props.order
    products.value = props.products ?? []
  } else if (props.orderId) {
    loadData()
  }
}

// ====== 状态映射 ======
function statusLabel(status?: number): string {
  if (status == null) return '-'
  const label = SampleOrderStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : `未知(${status})`
}
function statusTagType(status?: number): TagType {
  if (status == null) return 'info'
  return (SampleOrderStatusEnum.getTagProps(status).type as TagType) || 'info'
}

function handleClose() {
  emit('update:visible', false)
  emit('close')
}

defineExpose({
  loadData,
  /** 手动设置数据（外部编辑后刷新用） */
  setData: (order: SampleOrder, prods: SampleProductItem[]) => {
    data.value = order
    products.value = prods
  },
})
</script>

<style scoped lang="scss">
.pick-cost-fields {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}
.sample-detail {
  min-height: 80px;
}
.empty-tip {
  color: #999;
  font-size: 13px;
  padding: 8px 0;
}
</style>
