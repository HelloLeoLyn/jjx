<template>
  <component :is="mode === 'dialog' ? ElDialog : 'div'" v-bind="dialogProps" @open="onOpen">
    <div v-loading="loading" class="sample-detail">
      <template v-if="data">
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
    </div>

    <template v-if="mode === 'dialog'" #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </component>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElDialog, ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleOrderStatusEnum } from '@/enums/sales'
import type { TagType } from '@/types'
import type { SampleDisplayMode, SampleOrderApiLike, SampleOrder, SampleProductItem } from './types'
import { sourceTypeLabel } from './types'

defineOptions({ name: 'SampleDetail' })

interface Props {
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
      api.value.getProducts(id),
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
.sample-detail {
  min-height: 80px;
}
.empty-tip {
  color: #999;
  font-size: 13px;
  padding: 8px 0;
}
</style>
