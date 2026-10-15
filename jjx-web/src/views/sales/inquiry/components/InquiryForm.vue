<template>
  <component :is="mode === 'dialog' ? ElDialog : 'div'" v-bind="dialogProps" @close="handleClose">
    <el-form
      ref="formRef"
      :model="form"
      :rules="rules"
      label-width="120px"
      :class="mode === 'inline' ? 'inquiry-form-inline' : ''"
    >
      <el-divider content-position="left">客户信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="客户" prop="customerId">
            <CustomerSelector
              v-model="form.customerId"
              value-type="customerId"
              placeholder="请选择客户"
              @change="customerChanged"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户名称" prop="customerName">
            <el-input v-model="form.customerName" placeholder="客户名称" disabled />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="联系人" prop="contactPerson">
            <el-input v-model="form.contactPerson" placeholder="请输入联系人" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="询价日期" prop="inquiryDate">
            <el-date-picker
              v-model="form.inquiryDate"
              type="date"
              placeholder="请选择询价日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预估数量" prop="expectedQuantity">
            <el-input-number
              v-model="form.expectedQuantity"
              :min="0"
              placeholder="预估数量"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预估单价" prop="unitPrice">
            <el-input-number
              v-model="form.unitPrice"
              :min="0"
              :precision="2"
              placeholder="转报价继承，可留空"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">产品信息</el-divider>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="类型" prop="inquiryType">
            <el-radio-group v-model="form.inquiryType" @change="onTypeChange">
              <el-radio :value="1" border>标准品</el-radio>
              <el-radio :value="2" border>样品（需打样）</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 标准品：选择产品 -->
          <el-form-item v-if="form.inquiryType === 1" label="选择产品" prop="productId">
            <ProductPicker
              v-model="form.productId"
              value-type="productId"
              :customer-id="form.customerId"
              :disabled="!form.customerId"
              placeholder="请先选择客户，再搜索该客户的产品"
              @change="onProductSelect"
            />
          </el-form-item>

          <!-- 样品：编码构成要素 -->
          <template v-if="form.inquiryType === 2">
            <ProductCodeGenerator
              ref="codeGenRef"
              :customer-short="shortNameDisplay"
              v-model:state="codeState"
              :emit-params="true"
              v-model:params="codeParams"
              @change="onCodeChange"
            />
          </template>

          <el-form-item v-else label="产品编码">
            <el-input v-model="form.productCode" placeholder="选择产品后自动带出" readonly />
          </el-form-item>

          <el-form-item label="产品名称" prop="productName">
            <el-input v-model="form.productName" placeholder="请填写产品名称" maxlength="200" />
          </el-form-item>
          <el-form-item label="产品描述" prop="productDescription">
            <el-input
              v-model="form.productDescription"
              type="textarea"
              :rows="3"
              placeholder="产品规格/描述"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="2"
              placeholder="备注"
              maxlength="1000"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row>
        <el-col :span="24">
          <AttachmentCenter
            ref="attachmentCenterRef"
            :source-type="AttachmentSourceTypeEnum.getItem('inquiry')?.value || 'inquiry'"
            :source-id="form.inquiryId || null"
            :source-no="(form as any).inquiryNo || ''"
            :draft-id="draftId"
            :product-id="form.productId || null"
            :product-code="(form as any).productCode || null"
            :actions="['download', 'detail', 'remove', 'save']"
            :auto-bind="false"
            drop-zone
            view-perm="sales:inquiry:view"
            edit-perm="sales:inquiry:edit"
            @save="onAttachSave"
            style="margin-bottom: 10px"
          />
        </el-col>
      </el-row>
    </el-form>

    <!-- 弹窗 footer -->
    <template v-if="mode === 'dialog'" #footer>
      <div class="dialog-footer">
        <el-button @click="cancel">{{ form.inquiryId ? '取 消' : '关 闭' }}</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">
          {{ form.inquiryId ? '确 定' : '保 存' }}
        </el-button>
      </div>
    </template>

    <!-- inline footer -->
    <div v-if="mode === 'inline'" class="dialog-footer inline-footer">
      <el-button @click="cancel">{{ form.inquiryId ? '取 消' : '关 闭' }}</el-button>
      <el-button type="primary" @click="submitForm" :loading="submitting">
        {{ form.inquiryId ? '确 定' : '保 存' }}
      </el-button>
    </div>
  </component>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick } from 'vue'
import { ElDialog, ElMessage } from 'element-plus'
import type { FormInstance } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { inquiryApi } from '@/api/sales/inquiry'
import { customerApi } from '@/api/sales/customer'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import ProductPicker from '@/components/Selector/ProductPicker.vue'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'
import { parseProductCode } from '@/composables/useProductCode'
import { AttachmentSourceTypeEnum } from '@/enums/system/AttachmentEnum'
import type { InquiryDisplayMode, InquiryFormData, InquiryApiLike } from './types'
import { createEmptyInquiryForm } from './types'

defineOptions({ name: 'InquiryForm' })

interface Props {
  mode?: InquiryDisplayMode
  visible?: boolean
  /** 编辑时的询价单ID，为空代表新建 */
  editId?: number | null
  /** 外部已加载的数据（编辑模式可传，省一次 getInfo） */
  data?: InquiryFormData | null
  /** 自定义 API */
  api?: Partial<InquiryApiLike>
  width?: string
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  mode: 'dialog',
  visible: false,
  editId: null,
  data: null,
  api: undefined,
  width: '1200px',
  title: '',
})

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'success', payload: { mode: 'create' | 'edit'; inquiryId: number }): void
  (e: 'close'): void
}>()

const api = computed<InquiryApiLike>(() => ({
  getInfo: (id) => inquiryApi.getInfo(id),
  add: (payload) => inquiryApi.add(payload),
  edit: (payload) => inquiryApi.edit(payload),
  ...(props.api || {}),
}))

// ====== 表单状态 ======
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<InquiryFormData>(createEmptyInquiryForm())

const dialogTitle = computed(
  () => props.title || (props.editId || form.inquiryId ? '修改询价单' : '新增询价单')
)

const dialogProps = computed(() => {
  if (props.mode !== 'dialog') return {}
  return {
    modelValue: props.visible,
    title: dialogTitle.value,
    width: props.width,
    appendToBody: true,
    'onUpdate:modelValue': (v: boolean) => emit('update:visible', v),
  }
})

// ====== 校验规则 ======
const rules: Record<string, any> = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  inquiryDate: [{ required: true, message: '请选择询价日期', trigger: 'change' }],
  productId: [
    {
      validator: (_rule: any, value: any, callback: any) => {
        if (form.inquiryType === 1 && !value) {
          callback(new Error('标准品必须选择产品'))
        } else {
          callback()
        }
      },
      trigger: 'change',
    },
  ],
  productName: [{ required: true, message: '请填写产品名称', trigger: 'blur' }],
}

// ====== 编码生成器 ======
const codeGenRef = ref<InstanceType<typeof ProductCodeGenerator>>()
const codeState = ref<ProductCodeState>({
  serialNo: '',
  panelType: '',
  panelFeature: '',
  circuitType: '',
  circuitFeature: '',
})
const codeParams = ref<ProductCodeResult | null>(null)

const shortNameDisplay = computed(() => {
  const s = form.customerShortName || ''
  return s.substring(0, 3)
})

function onCodeChange(data: string | ProductCodeResult) {
  const code = typeof data === 'string' ? data : data.productCode
  form.productCode = code
}

function onTypeChange() {
  codeState.value = {
    serialNo: '',
    panelType: '',
    panelFeature: '',
    circuitType: '',
    circuitFeature: '',
  }
  form.productCode = ''
  if (form.inquiryType === 1) {
    form.productName = ''
  } else {
    form.productName = ''
    if (form.customerShortName) codeGenRef.value?.generate()
  }
}

function onProductSelect(val: number, product?: any) {
  if (!product) {
    product = {}
  }
  if (product) {
    form.productCode = product.productCode || ''
    form.productName = product.productName || ''
    const parsed = parseProductCode(product.productCode)
    if (parsed) {
      form.customerShortName = parsed.customerShort
      codeState.value = {
        serialNo: parsed.serialNo,
        panelType: parsed.panelType,
        panelFeature: parsed.panelFeature,
        circuitType: parsed.circuitType,
        circuitFeature: parsed.circuitFeature,
      }
    }
  }
}

// ====== 客户变更 ======
function customerChanged(val: number, customer?: any) {
  // 兼容 CustomerSelector 只 emit id 的场景：从组件内部 option 缓存取
  if (!customer) {
    customer = { customerId: val }
  }
  if (customer && customer.customerName) {
    form.customerName = customer.customerName
    if (!form.contactPerson) form.contactPerson = customer.contactPerson || ''
    if (!form.contactPhone) form.contactPhone = customer.contactPhone || ''
    form.customerShortName = customer.customerShortName || ''
    if (form.inquiryType === 2) {
      codeGenRef.value?.generate()
    }
  }
  if (form.inquiryType === 1 && form.productId) {
    form.productId = undefined
    form.productCode = ''
    form.productName = ''
    formRef.value?.validateField('productId').catch(() => {})
  }
}

// ====== 附件草稿 ======
const attachmentCenterRef = ref<InstanceType<typeof AttachmentCenter>>()
const draftId = ref('')
const draftUserStore = useUserStore()
let draftOwnerKey = ''
const draftSessionKey = () => `attach-draft:v1:${draftUserStore.userId ?? 'anonymous'}:inquiry`

function ensureDraftId() {
  const key = draftSessionKey()
  if (draftOwnerKey !== key) {
    draftId.value = ''
    draftOwnerKey = key
  }
  if (!draftId.value) {
    try {
      draftId.value = sessionStorage.getItem(key) || ''
    } catch (error) {
      console.warn('读取询价草稿标识失败，刷新后可能无法恢复待绑定附件', error)
    }
    if (!draftId.value) draftId.value = newDraftId()
  }
  try {
    sessionStorage.setItem(key, draftId.value)
  } catch (error) {
    console.warn('保存询价草稿标识失败，刷新后可能无法恢复待绑定附件', error)
  }
}

function clearDraftId() {
  if (!draftId.value) return
  try {
    sessionStorage.removeItem(draftOwnerKey)
  } catch (error) {
    console.warn('清理已保存询价草稿标识失败', error)
  }
  draftId.value = ''
}

function newDraftId() {
  return `inq-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

// 2026-10-15（dev-20261015-034）：原「保存后再绑附件」的 bindPendingAttachments 已删除——
// 附件改为随单据请求一起提交、后端同事务建引用；失败即整单回滚，本地待绑定队列保留可重试。

// ====== 附件区「保存」（dev-20261016-005，询价试点）======
// 已保存单据：把待绑定附件落定（建引用）；草稿单：附件信息暂存本地，保存单据时随单落定。
async function onAttachSave() {
  await nextTick()
  const result = await attachmentCenterRef.value?.bindPending()
  if (!result) return
  if (result.status === 'document-not-saved') {
    ElMessage.info('单据尚未保存：附件信息已暂存在本地，保存单据时会一并落定')
    return
  }
  if (result.status === 'all-ok') {
    ElMessage.success('附件已保存')
    return
  }
  if (result.status === 'partial') {
    ElMessage.warning(`部分附件保存失败（成功 ${result.bound.length} 条、失败 ${result.failed.length} 条），可在附件区重试`)
    return
  }
  ElMessage.error(`附件全部保存失败（${result.failed.length} 条），可在附件区重试`)
}

// ====== 初始化 ======
function resetForm() {
  formRef.value?.resetFields()
  Object.assign(form, createEmptyInquiryForm())
  codeState.value = {
    serialNo: '',
    panelType: '',
    panelFeature: '',
    circuitType: '',
    circuitFeature: '',
  }
  codeParams.value = null
}

async function initCreate() {
  resetForm()
  ensureDraftId()
}

async function initEdit() {
  draftId.value = ''
  if (props.data) {
    Object.assign(form, props.data)
    hydrateFromCode(form.productCode)
    return
  }
  if (!props.editId) return
  try {
    const res = await api.value.getInfo(props.editId)
    const data = res.data
    Object.assign(form, {
      inquiryId: data.inquiryId,
      customerId: data.customerId,
      customerName: data.customerName,
      contactPerson: data.contactPerson,
      contactPhone: data.contactPhone,
      inquiryDate: data.inquiryDate,
      expectedQuantity: data.expectedQuantity,
      unitPrice: data.unitPrice,
      productId: data.productId,
      productCode: data.productCode || '',
      productName: data.productName || '',
      productDescription: data.productDescription,
      hasDrawing: data.hasDrawing ?? 0,
      inquiryStatus: data.inquiryStatus,
      inquiryType: data.inquiryType ?? 1,
      remark: data.remark,
      salesPersonId: data.salesPersonId,
      salesPersonName: data.salesPersonName,
    })
    hydrateFromCode(form.productCode)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载询价单数据失败')
  }
}

function hydrateFromCode(code: string) {
  if (!code) return
  const parsed = parseProductCode(code)
  if (parsed && form.inquiryType === 2) {
    form.customerShortName = parsed.customerShort
    codeState.value = {
      serialNo: parsed.serialNo,
      panelType: parsed.panelType,
      panelFeature: parsed.panelFeature,
      circuitType: parsed.circuitType,
      circuitFeature: parsed.circuitFeature,
    }
  }
}

// 打开时初始化（dialog 模式）
watch(
  () => props.visible,
  (v) => {
    if (props.mode !== 'dialog') return
    if (v) props.editId ? initEdit() : initCreate()
  },
  { immediate: true }
)

// editId 变化（inline 或编辑数据变化）
watch(
  () => props.editId,
  (v) => {
    if (props.mode === 'inline' || !props.visible) {
      if (v) initEdit()
      else initCreate()
    }
  }
)

if (props.mode === 'inline') {
  props.editId ? initEdit() : initCreate()
}

// ====== 提交 ======
async function submitForm() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  // 资料类型必选（dev-20261015-032）：保存前拦住缺二级类型的待绑定附件，避免「保存成功但附件未绑定」
  const missingTypeCount = attachmentCenterRef.value?.getMissingTypeCount() ?? 0
  if (missingTypeCount > 0) {
    ElMessage.warning(`有 ${missingTypeCount} 个附件未选择资料类型，请先选择后再保存`)
    return
  }
  // 保存即完整（dev-20261015-034）：把待绑定附件（含资料类型）随单据一起提交，后端在同一事务内建引用
  const attachments = (attachmentCenterRef.value?.pendingEntries ?? []).map((e: any) => ({
    attachmentId: e.attachmentId,
    documentType: e.documentType ?? null,
  }))
  submitting.value = true
  const payload = { ...(form as any), attachments }
  try {
    if (form.inquiryId) {
      await api.value.edit(payload)
      attachmentCenterRef.value?.clearPending()
      ElMessage.success('修改成功')
      emit('success', { mode: 'edit', inquiryId: form.inquiryId })
      if (props.mode === 'dialog') dialogVisibleClose()
    } else {
      const res = await api.value.add(payload)
      const newId = (res as any)?.data?.inquiryId || 0
      form.inquiryId = newId
      attachmentCenterRef.value?.clearPending()
      ElMessage.success('已保存，附件已一并落定')
      emit('success', { mode: 'create', inquiryId: newId })
      // 新增保存后留在弹窗里，方便接着上传附件
    }
    clearDraftId()
  } catch (e) {
    console.error('提交失败:', e)
  } finally {
    submitting.value = false
  }
}

function cancel() {
  emit('update:visible', false)
  emit('close')
}

function dialogVisibleClose() {
  emit('update:visible', false)
}

function handleClose() {
  emit('close')
}

defineExpose({
  /** 手动触发提交 */
  submitForm,
  /** 手动重置 */
  resetForm,
  /** 用外部数据回填 */
  fillFromData: (data: InquiryFormData) => {
    Object.assign(form, data)
    hydrateFromCode(form.productCode)
  },
})
</script>

<style scoped lang="scss">
.dialog-footer {
  text-align: right;
}
.inquiry-form-inline {
  padding: 8px 0;
}
.inline-footer {
  margin-top: 16px;
}
</style>
