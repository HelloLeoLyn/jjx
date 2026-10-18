<template>
  <el-dialog
    v-model="visible"
    title="批量发起打样"
    width="1040px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :show-close="!saving"
  >
    <div v-loading="loading">
      <el-alert
        v-if="blockedReason"
        :title="blockedReason"
        type="warning"
        :closable="false"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-if="submitError"
        :title="submitError"
        type="error"
        :closable="false"
        style="margin-bottom: 12px"
      />
      <template v-if="created.length">
        <el-alert
          :title="`已创建 ${created.length} 张样品单，各产品分别跟踪打样进度。`"
          type="success"
          :closable="false"
        />
        <div v-for="sample in created" :key="sample.orderId" class="sample-result">
          <el-link type="primary" @click="showSample(sample.orderId)">{{ sample.orderNo }}</el-link>
          <span>{{ sample.sampleProductName || sample.sampleProductCode }}</span>
          <span>{{ sample.sampleQty }} 件</span>
        </div>
      </template>
      <template v-else-if="options">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="来源订单">{{ options.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ options.customerName }}</el-descriptions-item>
          <el-descriptions-item label="联系人"
            >{{ options.contactPerson || '—' }} {{ options.contactPhone }}</el-descriptions-item
          >
        </el-descriptions>
        <el-form label-width="110px" class="sample-common" :disabled="saving">
          <el-form-item label="期望交样日期">
            <el-date-picker
              v-model="deliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可统一调整交样日期"
            />
          </el-form-item>
          <el-form-item label="共同打样要求">
            <el-input
              v-model="techRequirement"
              maxlength="500"
              placeholder="所有勾选产品共用，可留空"
            />
          </el-form-item>
        </el-form>
        <el-table :data="rows" border max-height="430">
          <el-table-column label="勾选" width="65" align="center">
            <template #default="{ row }"
              ><el-checkbox
                v-model="row.selected"
                :disabled="saving || !!row.activeSamples.length"
                :aria-label="`选择${row.productCode}`"
            /></template>
          </el-table-column>
          <el-table-column prop="productCode" label="产品编码" min-width="150" />
          <el-table-column prop="productName" label="产品名称" min-width="140" />
          <el-table-column prop="orderQuantity" label="订单数量" width="95" align="right" />
          <el-table-column label="打样数量" width="140">
            <template #default="{ row }">
              <el-input
                v-model="row.quantity"
                type="number"
                min="1"
                step="1"
                :disabled="saving || !row.selected"
                :aria-label="`${row.productCode}打样数量`"
              />
              <span v-if="row.error" class="quantity-error">{{ row.error }}</span>
            </template>
          </el-table-column>
          <el-table-column label="产品备注" min-width="150">
            <template #default="{ row }"
              ><el-input
                v-model="row.remark"
                maxlength="500"
                :disabled="saving || !row.selected"
                placeholder="可单独补充"
            /></template>
          </el-table-column>
          <el-table-column label="进行中样品单" min-width="190">
            <template #default="{ row }">
              <div v-for="sample in row.activeSamples" :key="sample.orderId">
                <el-link type="primary" @click="showSample(sample.orderId)">{{
                  sample.orderNo
                }}</el-link>
                <span> {{ SampleOrderStatusEnum.getLabel(sample.sampleStatus) }}</span>
              </div>
              <span v-if="!row.activeSamples.length">—</span>
            </template>
          </el-table-column>
        </el-table>
      </template>
      <el-empty v-else-if="!blockedReason" description="未加载到订单打样资料" />
    </div>
    <template #footer>
      <el-button
        v-if="!created.length && !blockedReason"
        :disabled="saving || loading"
        @click="load(true)"
        >刷新资料</el-button
      >
      <el-button :disabled="saving" @click="visible = false">{{
        created.length ? '关闭' : '取消'
      }}</el-button>
      <el-button
        v-if="!created.length"
        type="primary"
        :loading="saving"
        :disabled="loading || !!blockedReason || !selected.length"
        @click="submit"
      >
        发起打样（{{ selected.length }} 个产品，共 {{ total }} 件）
      </el-button>
    </template>
    <SampleDetail v-model:visible="detailVisible" :order-id="detailId" />
  </el-dialog>
</template>

<script lang="ts">
import { SalesOrderStatusEnum } from '@/enums/sales/OrderEnum'

/** 允许发起打样的订单状态：未结束订单（含生产中补打样），与后端 requireSampleableOrderStatus 保持一致。 */
const SAMPLEABLE_ORDER_STATUSES: number[] = [
  SalesOrderStatusEnum.DRAFT.value,
  SalesOrderStatusEnum.PENDING_REVIEW.value,
  SalesOrderStatusEnum.REVIEWING.value,
  SalesOrderStatusEnum.REVIEWED.value,
  SalesOrderStatusEnum.REJECTED.value,
  SalesOrderStatusEnum.CONFIRMED.value,
  SalesOrderStatusEnum.PRODUCING.value,
]

/**
 * 打样入口状态闸门（dev-20261018-039）：非终态返回空串；终态返回原因；未知/空状态明确拒绝。
 */
export function sampleEntryBlockedReason(orderStatus?: number | null): string {
  if (orderStatus == null) return '订单状态未知，请刷新订单后再试'
  if (SAMPLEABLE_ORDER_STATUSES.includes(orderStatus)) return ''
  const label = SalesOrderStatusEnum.getLabel(orderStatus)
  if (label === '未知') return `订单状态未知（${orderStatus}），请刷新订单后再试`
  return `订单当前状态为「${label}」，不可发起打样`
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import type { OrderSampleOption, OrderSampleOptions, SampleOrder } from '@/types/sales/sampleOrder'
import { SampleOrderStatusEnum } from '@/enums/sales/SampleEnum'
import SampleDetail from '../../sample-order/components/SampleDetail.vue'

const props = defineProps<{
  modelValue: boolean
  sourceOrderId?: number
  orderStatus?: number | null
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; created: [] }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (!saving.value) emit('update:modelValue', value)
  },
})
type Row = OrderSampleOption & { selected: boolean; quantity: string | number; error: string }
const options = ref<OrderSampleOptions>()
const rows = ref<Row[]>([])
const loading = ref(false)
const saving = ref(false)
const deliveryDate = ref('')
const techRequirement = ref('')
const submitError = ref('')
const created = ref<SampleOrder[]>([])
const detailVisible = ref(false)
const detailId = ref<number>()
const selected = computed(() => rows.value.filter((r) => r.selected))
/** 订单状态闸门：终态/未知状态直接拦在入口，和后端校验同一口径（dev-20261018-039）。 */
const blockedReason = computed(() => sampleEntryBlockedReason(props.orderStatus))
const total = computed(() =>
  selected.value.reduce((sum, r) => sum + (validQuantity(r.quantity) ? Number(r.quantity) : 0), 0)
)
let loadVersion = 0

function validQuantity(value: string | number) {
  const quantity = Number(value)
  return Number.isInteger(quantity) && quantity > 0 && quantity <= 2147483647
}

async function load(preserve: boolean) {
  const sourceId = props.sourceOrderId
  const version = ++loadVersion
  const previous = preserve ? new Map(rows.value.map((r) => [r.itemId, r])) : new Map<number, Row>()
  if (!sourceId) return
  submitError.value = ''
  if (blockedReason.value) {
    options.value = undefined
    rows.value = []
    return
  }
  loading.value = true
  try {
    const res = await sampleOrderApi.getOrderSampleOptions(sourceId)
    if (version !== loadVersion || sourceId !== props.sourceOrderId) return
    options.value = res.data
    rows.value = res.data.items.map((item) => ({
      ...item,
      activeSamples: item.activeSamples || [],
      quantity: previous.get(item.itemId)?.quantity ?? 1,
      remark: previous.get(item.itemId)?.remark ?? item.remark ?? '',
      error: '',
      selected:
        !item.activeSamples?.length &&
        (previous.get(item.itemId)?.selected ?? res.data.items.length === 1),
    }))
    if (!preserve) deliveryDate.value = res.data.deliveryDate || ''
  } catch (error: any) {
    if (version === loadVersion)
      submitError.value = error?.message || '加载订单打样资料失败，请刷新重试'
  } finally {
    if (version === loadVersion) loading.value = false
  }
}

async function submit() {
  if (saving.value || blockedReason.value || !options.value || !props.sourceOrderId) return
  rows.value.forEach(
    (r) =>
      (r.error = r.selected && !validQuantity(r.quantity) ? '请输入正整数，最大2147483647' : '')
  )
  if (!selected.value.length || rows.value.some((r) => r.error)) return
  const sourceId = props.sourceOrderId
  saving.value = true
  submitError.value = ''
  try {
    const res = await sampleOrderApi.createBatchFromOrder(sourceId, {
      sourceVersion: options.value.sourceVersion,
      deliveryDate: deliveryDate.value || undefined,
      techRequirement: techRequirement.value || undefined,
      items: selected.value.map((r) => ({
        itemId: r.itemId,
        quantity: Number(r.quantity),
        remark: r.remark,
      })),
    })
    if (sourceId !== props.sourceOrderId) return
    created.value = res.data
    emit('created')
    ElMessage.success(`已创建${res.data.length}张样品单`)
  } catch (error: any) {
    submitError.value = error?.message || '整批创建未成功，请检查后重试'
  } finally {
    saving.value = false
  }
}

function showSample(id: number) {
  detailId.value = id
  detailVisible.value = true
}

watch(
  () => [props.modelValue, props.sourceOrderId] as const,
  ([open]) => {
    ++loadVersion
    detailVisible.value = false
    if (!open) return
    options.value = undefined
    rows.value = []
    created.value = []
    deliveryDate.value = ''
    techRequirement.value = ''
    submitError.value = ''
    load(false)
  },
  { immediate: true }
)
</script>

<style scoped>
.sample-common {
  margin-top: 16px;
}
.quantity-error {
  color: var(--el-color-danger);
  font-size: 12px;
}
.sample-result {
  display: flex;
  gap: 24px;
  padding: 12px 0;
}
</style>
