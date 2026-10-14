<template>
  <component :is="mode === 'dialog' ? ElDialog : 'div'" v-bind="dialogProps" @close="handleClose">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="110px"
      :class="mode === 'dialog' ? '' : 'sample-form-inline'"
    >
      <!-- 客户 -->
      <el-form-item label="客户" prop="customerId">
        <el-select
          v-model="form.customerId"
          placeholder="请选择客户"
          filterable
          remote
          :remote-method="searchCustomers"
          :loading="customerSearching"
          :disabled="isQuotationLocked"
          style="width: 100%"
          @change="onCustomerChange"
        >
          <el-option
            v-for="c in customerOptions"
            :key="c.customerId"
            :label="c.customerName"
            :value="c.customerId"
          />
        </el-select>
      </el-form-item>

      <!-- 来源类型 -->
      <el-form-item label="来源类型">
        <el-select
          v-model="form.sourceType"
          placeholder="无来源"
          clearable
          :disabled="!!editId"
          style="width: 100%"
          @change="onSourceTypeChange"
        >
          <el-option label="报价单" value="QUOTATION" />
          <el-option label="销售订单" value="SALES_ORDER" />
          <el-option label="样品单" value="SAMPLE_ORDER" disabled />
        </el-select>
      </el-form-item>

      <!-- 来源单号：报价单 -->
      <el-form-item v-if="form.sourceType === 'QUOTATION' && !editId" label="来源单号">
        <el-select
          v-model="form.sourceNo"
          placeholder="选择单产品报价单，带出客户和产品"
          filterable
          clearable
          style="width: 100%"
          @change="onQuotationChange"
        >
          <el-option
            v-for="q in quotationOptions"
            :key="q.quotationNo"
            :label="`${q.quotationNo} - ${q.customerName} (${q.finalAmount}元)`"
            :value="q.quotationNo"
          />
        </el-select>
      </el-form-item>
      <el-form-item v-else-if="form.sourceType" label="来源单号">
        <el-input v-model="form.sourceNo" :disabled="!!editId" placeholder="填写来源单号" />
      </el-form-item>

      <!-- 打样产品 -->
      <el-form-item label="打样产品" required>
        <div class="product-row">
          <el-select
            v-if="showProductSelect"
            v-model="form.product.productId"
            filterable
            remote
            :remote-method="searchProducts"
            :loading="productSearching"
            :disabled="!form.customerId"
            placeholder="请先选择客户，再选择一个打样产品"
            @change="onProductSelect"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.productId"
              :label="`${p.productCode} - ${p.productName}`"
              :value="p.productId"
            />
          </el-select>
          <el-input
            v-else-if="form.sourceType === 'QUOTATION' && form.sourceNo"
            :model-value="form.product.productName"
            readonly
            placeholder="从来源报价单带入"
          />
          <el-input v-else :model-value="newProductLabel" readonly placeholder="新产品（待建档）" />
          <el-button v-if="canCreateProduct" plain :icon="Plus" @click="openCodeGen">
            新建产品
          </el-button>
          <el-button v-else-if="productIsNew" plain @click="clearNewProduct">重选</el-button>
        </div>
        <div v-if="productIsNew" class="new-product-tag">
          <el-tag type="success" size="small">新产品 · 保存后自动建档</el-tag>
        </div>
        <div class="form-tip">一张样品单对应一个成品，可打样多件。</div>
        <el-alert
          v-if="quotationProductError"
          :title="quotationProductError"
          type="warning"
          :closable="false"
        />
      </el-form-item>

      <el-form-item label="产品编码">
        <el-input :model-value="form.product.productCode" readonly />
      </el-form-item>
      <el-form-item label="产品名称">
        <el-input :model-value="form.product.productName" readonly />
      </el-form-item>
      <el-form-item label="打样数量" prop="product.quantity">
        <el-input-number
          v-model="form.product.quantity"
          :min="1"
          :precision="0"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="单位">
        <el-input v-model="form.product.unit" placeholder="PCS" />
      </el-form-item>
      <el-form-item label="期望交样日期">
        <el-date-picker
          v-model="form.deliveryDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="默认继承报价单交期"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="联系人">
        <el-input v-model="form.contactPerson" placeholder="默认带出客户/报价单联系人" />
      </el-form-item>
      <el-form-item label="联系电话">
        <el-input v-model="form.contactPhone" placeholder="默认带出客户/报价单电话" />
      </el-form-item>
      <el-form-item label="技术要求">
        <el-input
          v-model="form.techRequirement"
          type="textarea"
          :rows="3"
          placeholder="工程打样要求（材质/工艺/颜色/按键数/连接器等），将传承给打样工作台"
        />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <ProductCodeGenDialog
      v-model="codeGenVisible"
      title="新建打样产品"
      :customer-short="customerShort"
      :used-serials="[]"
      show-product-name
      @confirm="onCodeGenConfirm"
    />

    <!-- 弹窗 footer -->
    <template v-if="mode === 'dialog'" #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        @click="submit"
        :loading="submitting"
        :disabled="quotationProductLoading || !!quotationProductError"
      >
        {{ editId ? '保存' : '创建' }}
      </el-button>
    </template>

    <!-- inline footer -->
    <div v-if="mode === 'inline'" class="inline-footer">
      <el-button @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        @click="submit"
        :loading="submitting"
        :disabled="quotationProductLoading || !!quotationProductError"
      >
        {{ editId ? '保存' : '创建' }}
      </el-button>
    </div>
  </component>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElDialog, ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { quotationApi } from '@/api/sales/quotation'
import { customerApi } from '@/api/sales/customer'
import { searchProduct } from '@/api/product'
import type {
  SampleDisplayMode,
  SampleProductItem,
  CustomerOption,
  QuotationOption,
  SampleOrderApiLike,
} from './types'
import request from '@/utils/request'
import ProductCodeGenDialog from '@/components/ProductCodeGenDialog/index.vue'
import type { ProductCodeResult } from '@/composables/useProductCode'

defineOptions({ name: 'SampleForm' })

interface Props {
  /** 展示模式：dialog 弹窗，inline 内嵌 */
  mode?: SampleDisplayMode
  /** 弹窗可见（mode=dialog 时使用） */
  visible?: boolean
  /** 编辑时的订单ID，为空代表新建 */
  editId?: number | null
  /** 编辑时的订单号（用于标题展示） */
  editOrderNo?: string
  /** 外部已加载的订单数据（编辑模式可传，省一次请求） */
  order?: Record<string, any> | null
  /** 外部已加载的产品（编辑模式可传） */
  products?: SampleProductItem[] | null
  /** 自定义 API（默认使用 sampleOrderApi） */
  api?: Partial<SampleOrderApiLike>
  /** 弹窗宽度 */
  width?: string
  /** 弹窗标题（自定义） */
  title?: string
  /** 是否打开时自动加载编辑数据（当传了 editId 且未传 order 时） */
  autoLoad?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'dialog',
  visible: false,
  editId: null,
  editOrderNo: '',
  order: null,
  products: null,
  api: undefined,
  width: '860px',
  title: '',
  autoLoad: true,
})

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success', payload: { mode: 'create' | 'edit'; data: any }): void
  (e: 'close'): void
}>()

// ====== API 注入 ======
const api = computed<SampleOrderApiLike>(() => ({
  getInfo: (id) => sampleOrderApi.getInfo(id),
  getProducts: (id) => sampleOrderApi.getProducts(id),
  create: (payload) => sampleOrderApi.create(payload),
  update: (id, payload) => sampleOrderApi.update(id, payload),
  ...(props.api || {}),
}))

// ====== 状态 ======
const formRef = ref<FormInstance>()
const submitting = ref(false)
const customerOptions = ref<CustomerOption[]>([])
const customerSearching = ref(false)
const productOptions = ref<any[]>([])
const productSearching = ref(false)
const codeGenVisible = ref(false)
const productIsNew = ref(false)
const quotationOptions = ref<QuotationOption[]>([])
const quotationProductLoading = ref(false)
const quotationProductError = ref('')

let quotationRequestId = 0

function emptyProduct(): SampleProductItem {
  return {
    productId: undefined,
    productCode: '',
    productName: '',
    quantity: 1,
    unit: 'PCS',
  }
}

const form = reactive({
  customerId: undefined as number | undefined,
  sourceType: '' as '' | 'QUOTATION' | 'SALES_ORDER' | 'SAMPLE_ORDER',
  sourceNo: '',
  product: emptyProduct(),
  deliveryDate: '',
  contactPerson: '',
  contactPhone: '',
  techRequirement: '',
  remark: '',
})

const rules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
}

const isQuotationLocked = computed(
  () => form.sourceType === 'QUOTATION' && !!form.sourceNo && !props.editId
)

// 「新建产品」：现场用产品编码器生成编码，productId 留空、随单据保存由后端自动建档
const customerShort = computed(
  () => customerOptions.value.find((c) => c.customerId === form.customerId)?.customerShortName || '',
)
const sourceFromQuotation = computed(() => form.sourceType === 'QUOTATION' && !!form.sourceNo)
const showProductSelect = computed(() => !sourceFromQuotation.value && !productIsNew.value)
const canCreateProduct = computed(
  () => !sourceFromQuotation.value && !props.editId && !!form.customerId && !productIsNew.value,
)
const newProductLabel = computed(() =>
  [form.product.productCode, form.product.productName].filter(Boolean).join(' - '),
)

const dialogProps = computed(() => {
  if (props.mode !== 'dialog') return {}
  return {
    modelValue: props.visible,
    title: props.title || (props.editId ? `编辑样品单（${props.editOrderNo}）` : '新增样品单'),
    width: props.width,
    appendToBody: true,
    'onUpdate:modelValue': (v: boolean) => emit('update:visible', v),
  }
})

// ====== 客户搜索 ======
async function searchCustomers(keyword: string) {
  customerSearching.value = true
  try {
    const res: any = await customerApi.searchCustomers(keyword || '')
    customerOptions.value = res.data || []
  } catch {
    customerOptions.value = []
  } finally {
    customerSearching.value = false
  }
}

function onCustomerChange(cid: number) {
  if (form.sourceType === 'SALES_ORDER') form.sourceNo = ''
  const c = customerOptions.value.find((x) => x.customerId === cid)
  if (c) {
    if (!form.contactPerson) form.contactPerson = c.contactPerson || ''
    if (!form.contactPhone) form.contactPhone = c.contactPhone || ''
  }
  resetProductSelection()
}

function onSourceTypeChange() {
  form.sourceNo = ''
  resetProductSelection()
}

function resetProductSelection() {
  quotationRequestId++
  quotationProductLoading.value = false
  quotationProductError.value = ''
  productOptions.value = []
  productIsNew.value = false
  form.product = emptyProduct()
}

function fillSampleProduct(item: any) {
  productIsNew.value = false
  form.product = {
    productId: item.productId ?? undefined,
    productCode: item.productCode || '',
    productName: item.productName || '',
    quantity: item.quantity ?? 1,
    unit: item.unit || 'PCS',
  }
  productOptions.value = item.productId ? [{ ...item }] : []
}

// ====== 报价单 ======
async function loadQuotationOptions() {
  try {
    const res: any = await request({
      url: '/sales/quotation/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 50, quotationStatus: 2 },
    })
    quotationOptions.value = res.data?.records || []
  } catch {
    quotationOptions.value = []
  }
}

async function onQuotationChange(quotationNo?: string) {
  resetProductSelection()
  if (!quotationNo) return
  const q = quotationOptions.value.find((x) => x.quotationNo === quotationNo)
  if (!q) return
  form.customerId = q.customerId
  form.contactPerson = q.contactPerson || ''
  form.contactPhone = q.contactPhone || ''
  form.deliveryDate = q.validUntil || ''
  if (!customerOptions.value.some((c) => c.customerId === q.customerId)) {
    customerOptions.value.push({ ...q })
  }
  const requestId = quotationRequestId
  quotationProductLoading.value = true
  try {
    const res: any = await quotationApi.getItems(q.quotationId)
    if (requestId !== quotationRequestId) return
    const items: any[] = res?.data || []
    if (items.length !== 1) {
      quotationProductError.value =
        items.length > 1
          ? '该报价包含多个产品，请到「报价单」使用「转为样品单」，按产品分别生成样品单。'
          : '该报价没有产品，请补充报价产品或清除来源报价后选择打样产品。'
      return
    }
    fillSampleProduct(items[0])
  } catch (e: any) {
    if (requestId !== quotationRequestId) return
    quotationProductError.value = e?.message || '加载报价产品失败，请重新选择来源报价单'
  } finally {
    if (requestId === quotationRequestId) quotationProductLoading.value = false
  }
}

// ====== 产品搜索 ======
async function searchProducts(keyword: string) {
  const customerId = form.customerId
  const requestId = quotationRequestId
  if (!customerId) return
  productSearching.value = true
  try {
    const res: any = await searchProduct(keyword || '', customerId)
    if (requestId !== quotationRequestId || customerId !== form.customerId) return
    productOptions.value = res.data || []
  } catch {
    if (requestId === quotationRequestId) productOptions.value = []
  } finally {
    productSearching.value = false
  }
}

function onProductSelect(productId: number) {
  productIsNew.value = false
  const product = productOptions.value.find((p) => p.productId === productId)
  if (product) {
    form.product.productCode = product.productCode
    form.product.productName = product.productName
    form.product.unit = product.unit || 'PCS'
  }
}

// ====== 新建产品（现场生成编码）======
function openCodeGen() {
  if (!form.customerId) {
    ElMessage.warning('请先选择客户，再新建产品')
    return
  }
  codeGenVisible.value = true
}

function onCodeGenConfirm(payload: { params: ProductCodeResult; productName: string }) {
  const p = payload.params
  form.product = {
    productId: undefined,
    productCode: p.productCode,
    productName: payload.productName || p.productCode,
    quantity: form.product.quantity || 1,
    unit: form.product.unit || 'PCS',
    serialNo: p.serialNo,
    panelType: p.panelType,
    panelFeature: p.panelFeature,
    circuitType: p.circuitType,
    circuitFeature: p.circuitFeature,
  }
  productIsNew.value = true
  productOptions.value = []
}

function clearNewProduct() {
  productIsNew.value = false
  resetProductSelection()
}

// ====== 重置 & 数据回填 ======
function resetForm() {
  form.customerId = undefined
  form.sourceType = ''
  form.sourceNo = ''
  form.deliveryDate = ''
  form.contactPerson = ''
  form.contactPhone = ''
  form.techRequirement = ''
  form.remark = ''
  resetProductSelection()
  formRef.value?.clearValidate()
}

/** 用 order + products 回填表单 */
function fillFromOrder(order: Record<string, any>, products: SampleProductItem[]) {
  form.customerId = order.customerId
  form.sourceType = (order.sourceType as any) || ''
  form.sourceNo = order.sourceNo || ''
  if (products.length > 1) {
    ElMessage.error('该历史样品单包含多个产品，请先按产品拆单，不能直接编辑覆盖')
    return
  }
  resetProductSelection()
  if (products.length === 1) fillSampleProduct(products[0])
  form.deliveryDate = order.deliveryDate || ''
  form.contactPerson = order.contactPerson || ''
  form.contactPhone = order.contactPhone || ''
  form.techRequirement = order.engineeringNote || order.techRequirement || ''
  form.remark = order.remark || ''
  if (order.customerId && !customerOptions.value.some((c) => c.customerId === order.customerId)) {
    customerOptions.value.push({
      customerId: order.customerId,
      customerName: order.customerName || '',
      contactPerson: order.contactPerson || '',
      contactPhone: order.contactPhone || '',
    })
  }
}

// ====== 生命周期 ======
async function initForCreate() {
  resetForm()
  await loadQuotationOptions()
  if (customerOptions.value.length === 0) await searchCustomers('')
}

async function initForEdit() {
  if (!props.editId) return
  try {
    if (props.order && props.products) {
      // 外部已传数据
      fillFromOrder(props.order, props.products)
      return
    }
    if (!props.autoLoad) return
    const [infoRes, prodRes]: any[] = await Promise.all([
      api.value.getInfo(props.editId),
      api.value.getProducts(props.editId),
    ])
    fillFromOrder(infoRes?.data || {}, prodRes?.data || [])
  } catch (e: any) {
    ElMessage.error(e?.message || '加载样品单数据失败，请刷新后重试')
  }
}

// 监听 visible（dialog 模式）
watch(
  () => props.visible,
  (v) => {
    if (props.mode !== 'dialog') return
    if (v) {
      props.editId ? initForEdit() : initForCreate()
    }
  },
  { immediate: true }
)

// 监听 editId 变化（inline 模式或编辑数据变化）
watch(
  () => props.editId,
  (v) => {
    if (props.mode === 'inline' || !props.visible) {
      if (v) initForEdit()
      else initForCreate()
    }
  }
)

// inline 模式立即初始化
if (props.mode === 'inline') {
  props.editId ? initForEdit() : initForCreate()
}

// ====== 提交 ======
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!form.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  if (form.sourceType && !form.sourceNo.trim()) {
    ElMessage.warning('请填写来源单号')
    return
  }
  if (quotationProductLoading.value || quotationProductError.value) {
    ElMessage.warning(quotationProductError.value || '请等待报价产品加载完成')
    return
  }
  const product = form.product
  if (!product.productCode || !product.productName) {
    ElMessage.warning('请选择一个打样产品')
    return
  }
  if (!Number.isInteger(product.quantity) || (product.quantity ?? 0) <= 0) {
    ElMessage.warning('打样数量必须为大于零的整数')
    return
  }
  submitting.value = true
  const payload: any = {
    customerId: form.customerId,
    items: [{ ...product, unit: product.unit || 'PCS' }],
    deliveryDate: form.deliveryDate || undefined,
    contactPerson: form.contactPerson || undefined,
    contactPhone: form.contactPhone || undefined,
    techRequirement: form.techRequirement || undefined,
    remark: form.remark || undefined,
  }
  try {
    if (props.editId) {
      await api.value.update(props.editId, payload)
      ElMessage.success(`样品单${props.editOrderNo}已保存`)
      emit('success', { mode: 'edit', data: { orderId: props.editId } })
    } else {
      const res = await api.value.create({
        ...payload,
        sourceType: form.sourceType === 'SAMPLE_ORDER' ? undefined : form.sourceType || undefined,
        sourceNo: form.sourceNo.trim() || undefined,
      })
      ElMessage.success(`样品单创建成功: ${res.data.orderNo}，可前往工程打样`)
      emit('success', { mode: 'create', data: res.data })
    }
    handleClose()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  emit('update:visible', false)
  emit('close')
}

defineExpose({
  /** 手动触发提交 */
  submit,
  /** 手动重置 */
  resetForm,
  /** 用外部数据回填 */
  fillFromOrder,
})
</script>

<style scoped lang="scss">
.form-tip {
  color: #909399;
  font-size: 12px;
}
.product-row {
  display: flex;
  gap: 8px;
  width: 100%;
  align-items: center;
}
.product-row > :deep(.el-select),
.product-row > :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.new-product-tag {
  margin-top: 6px;
}
.sample-form-inline {
  padding: 8px 0;
}
.inline-footer {
  margin-top: 16px;
  text-align: right;
}
</style>
