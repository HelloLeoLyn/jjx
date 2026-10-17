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

      <!-- 附件（dev-20261016-074：新公共组件 AttachmentUpload + AttachmentManager，共用同一份会话队列；
           上传只进「待保存」，随「保存样品单」由后端同事务落定（001 §7-1 方案 A）） -->
      <el-divider content-position="left">附件</el-divider>
      <el-dropdown v-if="!currentId && draftChoices.length" @command="restoreAttachmentDraft">
        <el-button plain>恢复未保存草稿</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="draft in draftChoices" :key="draft.id" :command="draft.id">{{ draft.label }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown v-if="!currentId && draftChoices.length" style="margin-left: 8px" @command="discardAttachmentDraft">
        <el-button plain type="danger">放弃草稿</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="draft in draftChoices" :key="draft.id" :command="draft.id">{{ draft.label }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <AttachmentUpload
        :session="session"
        drop-zone
        button-text="上传整单附件"
        style="margin: 6px 0"
        @uploaded="(item) => session.registerUploaded(item.pending, item.ownership)"
        @error="onAttachError"
      />
      <AttachmentManager
        :session="session"
        title="样品单附件"
        source-type="sample"
        :source-no="props.editOrderNo || ''"
        row-save
        batch-save
        style="margin-bottom: 10px"
        @error="onAttachError"
      />
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
import { onActivated, onDeactivated, onMounted, ref, reactive, computed, watch } from 'vue'
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
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import {
  useAttachmentSession,
  type AttachmentChanges,
  type AttachmentSaveResult,
} from '@/composables/useAttachmentSession'
import { useAttachmentDraftInstance } from '@/composables/useAttachmentRefs'
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

// ====== 附件（公共附件中心，单据级）======
/** 当前单据ID：新建时为空（走待绑定队列），创建成功后回写，绑定待绑定附件时依赖它 */
const currentId = ref<number | null>(null)
/** 新建草稿标识：同一张未保存样品单生命周期内保持稳定，保存成功后清空（隔离待绑定队列） */
const draftSession = useAttachmentDraftInstance('sample', {
  snapshot: () => ({ form, productIsNew: productIsNew.value }),
  restore: (data) => { resetForm(); currentId.value = null; Object.assign(form, data.form || {}); productIsNew.value = !!data.productIsNew },
})
const { draftId, choices: draftChoices, complete: clearDraftId } = draftSession
let resumeDraftId: string | undefined = draftSession.resumeId
function ensureDraftId() {
  const id = resumeDraftId
  resumeDraftId = undefined
  if (!id || !draftSession.restore(id)) draftSession.startNew()
}
function restoreAttachmentDraft(id: string) {
  // 恢复是整单覆盖（客户/产品/明细+附件）：先确认（dev-20261016-042）
  void draftSession.confirmRestore(id)
}
function discardAttachmentDraft(id: string) {
  // 放弃草稿：删本地记录/队列 + 库中草稿行（dev-20261016-042）
  void draftSession.confirmDiscard(id)
}

/**
 * 附件共享会话（dev-20261016-074；与 071 报价页同款）：
 * - 队列键与旧附件中心组件一致（sourceType=sample + sourceId/draftId，键算法见 useAttachmentRefs.ts）；
 * - 落定（建引用）**只由「保存样品单」负责**：待保存队列随 create/update 的 attachments 提交，
 *   由后端在保存事务内建引用/草稿转正（dev-20261017-002）；本会话不做任何落定请求；
 * - saveHandler 只做「附件信息类」提交（二级类型/备注/排序），不落定（001 §7-1 方案 A）。
 */
const session = useAttachmentSession({
  sourceType: 'sample',
  sourceId: computed(() => currentId.value),
  draftId: computed(() => draftId.value || ''),
  viewPerm: 'sales:sample:view',
  editPerm: 'sales:sample:edit',
  saveHandler: saveAttachInfo,
})

/** 附件信息类提交（保存处理器唯一出口）：只认 updates；uploads 随「保存样品单」落定，这里明确拒绝而不是静默吞掉 */
async function saveAttachInfo(changes: AttachmentChanges): Promise<AttachmentSaveResult> {
  if (changes.uploads.length) {
    throw new Error('待保存附件随样品单「保存」落定，此处只提交附件信息类改动')
  }
  if (!changes.updates.length) return {}
  const items = changes.updates.map((u) => ({
    refId: u.refId,
    documentType: u.documentType,
    remark: u.remark,
    sortOrder: u.sortOrder,
  }))
  await attachmentRefApi.batchUpdate(items)
  return { acceptedUpdates: items.map((i) => i.refId) }
}

/** 保存前校验用：待保存队列里尚未选二级资料类型的条数（沿用 dev-20261015-032 口径） */
const attachMissingTypeCount = computed(
  () => session.pending.value.filter((e) => e.state !== 'binding' && !e.documentType).length,
)

/** 待保存队列 → 样品单保存请求的 attachments（attachmentId/documentType/productId/productCode） */
function attachPayload() {
  return session.getChanges({ kind: 'all' }).uploads.map((u) => ({
    attachmentId: u.attachmentId,
    documentType: u.documentType ?? null,
    productId: productIdOfOwnership(u.ownershipKey),
    productCode: u.productCode ?? null,
  }))
}

/** 上传归属键 → 正式产品 id：只有 `product:<id>` 能直接给出（整单级不带产品时由后端按来源唯一产品回填） */
function productIdOfOwnership(key?: string | null): number | null {
  const matched = /^product:(\d+)$/.exec(key || '')
  return matched ? Number(matched[1]) : null
}

/**
 * 单据保存**成功后**的附件收尾（承 077 §4 的 bindPending 语义：成功清队列、失败保留）：
 * 重取引用 → 按 attachmentId 取**真实 refId** → acceptSaved；没落定的仍留在「待保存」待重试。
 * 不做任何前端绑定请求（落定已在单据保存事务内完成）。
 */
async function finalizeAttachments(submittedIds: number[]) {
  if (!submittedIds.length) return
  await session.refresh()
  const sourceId = Number(currentId.value)
  const refIdByAttachment = new Map<number, number>()
  for (const row of session.refs.value) {
    const attachmentId = Number((row as any).attachmentId)
    if (!attachmentId || row.deleted || Number(row.sourceId) !== sourceId) continue
    refIdByAttachment.set(attachmentId, row.id)
  }
  const acceptedUploads = submittedIds
    .filter((id) => refIdByAttachment.has(id))
    .map((id) => ({ attachmentId: id, refId: refIdByAttachment.get(id) as number }))
  if (acceptedUploads.length) session.acceptSaved({ acceptedUploads })
  const remaining = submittedIds.length - acceptedUploads.length
  if (remaining) ElMessage.warning(`有 ${remaining} 个附件未落定，仍保留在「待保存」，请再保存一次`)
}

/** 附件上传/校验失败提示：上传失败组件内已提示，这里只补「选择文件校验」这类没有内建提示的错误 */
function onAttachError(payload: { action: string; message: string }) {
  if (payload.action === 'upload') return
  ElMessage.error(payload.message || '附件处理失败')
}

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
  draftSession.close()
  resetForm()
  currentId.value = null
  ensureDraftId()
  await loadQuotationOptions()
  if (customerOptions.value.length === 0) await searchCustomers('')
}

async function initForEdit() {
  draftSession.close()
  if (!props.editId) return
  currentId.value = props.editId
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
    } else draftSession.close()
  },
  { immediate: true }
)

// 监听 editId 变化（inline 模式或编辑数据变化）
watch(
  () => props.editId,
  (v) => {
    if (props.mode === 'inline') {
      if (v) initForEdit()
      else initForCreate()
    }
  }
)

// inline 模式立即初始化
if (props.mode === 'inline') {
  props.editId ? initForEdit() : initForCreate()
}

onActivated(() => {
  if (props.mode === 'inline' && !props.editId && !draftId.value && !currentId.value) void initForCreate()
})
onDeactivated(() => {
  if (props.mode === 'dialog') emit('update:visible', false)
})

onMounted(() => {
  if (resumeDraftId && props.mode === 'dialog' && !props.editId && !props.visible) {
    emit('update:visible', true)
  }
})

// ====== 提交 ======
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 资料类型必选（dev-20261015-032）：保存前拦住缺二级类型的待绑定附件，避免「保存成功但附件未绑定」
  if (attachMissingTypeCount.value > 0) {
    ElMessage.warning(`有 ${attachMissingTypeCount.value} 个附件未选择资料类型，请先选择后再保存`)
    return
  }
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
  // 待保存附件随单据保存一并提交，由后端在同一事务内建引用/草稿转正（dev-20261017-002，001 §7-1 方案 A）
  const attachments = attachPayload()
  const submittedIds = attachments.map((a) => a.attachmentId)
  try {
    if (props.editId) {
      await api.value.update(props.editId, { ...payload, attachments } as any)
      currentId.value = props.editId
      await finalizeAttachments(submittedIds)
      ElMessage.success(`样品单${props.editOrderNo}已保存`)
      emit('success', { mode: 'edit', data: { orderId: props.editId } })
    } else {
      const res = await api.value.create({
        ...payload,
        sourceType: form.sourceType === 'SAMPLE_ORDER' ? undefined : form.sourceType || undefined,
        sourceNo: form.sourceNo.trim() || undefined,
        attachments,
      } as any)
      // 先回写单据ID（落定回填依赖 sourceId），收尾完再清草稿标识
      currentId.value = res.data?.orderId ?? null
      await finalizeAttachments(submittedIds)
      clearDraftId()
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
