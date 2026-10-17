<!-- views/sales/quotation/components/QuotationFormDialog.vue
  2026-09-02 优化：样品类型允许多条明细；编码生成器下沉明细行操作栏（针对行内产品）；
  明细行 📎资料 → 产品文件库（按 product_id/编码挂载，保存建档后可用）
-->
<template>
  <el-dialog :title="title" v-model="visible" width="1300px" append-to-body @close="handleClose">
    <el-dropdown v-if="!formData.quotationId && draftChoices?.length" @command="(id: string) => emit('restore-draft', id)">
      <el-button plain>恢复未保存草稿</el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="draft in draftChoices" :key="draft.id" :command="draft.id">{{ draft.label }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <el-dropdown
      v-if="!formData.quotationId && draftChoices?.length"
      style="margin-left: 8px"
      @command="(id: string) => emit('discard-draft', id)"
    >
      <el-button plain type="danger">放弃草稿</el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item v-for="draft in draftChoices" :key="draft.id" :command="draft.id">{{ draft.label }}</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-row>
        <el-col :span="12">
          <el-form-item label="报价类型" prop="quotationType">
            <el-radio-group v-model="formData.quotationType">
              <el-radio :value="1" border>标准品</el-radio>
              <el-radio :value="2" border>样品</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="客户" prop="customerId">
            <CustomerSelector
              v-model="formData.customerId"
              value-type="customerId"
              placeholder="请选择客户"
              @change="onCustomerChange"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="联系人" prop="contactPerson">
            <el-input v-model="formData.contactPerson" placeholder="默认带出客户联系人" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="联系电话" prop="contactPhone">
            <el-input v-model="formData.contactPhone" placeholder="默认带出客户电话（手机号或座机）" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="报价日期" prop="quotationDate">
            <el-date-picker
              v-model="formData.quotationDate"
              type="date"
              placeholder="请选择报价日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="有效期至" prop="validUntil">
            <el-date-picker
              v-model="formData.validUntil"
              type="date"
              placeholder="请选择有效期至"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="12">
          <el-form-item label="币种" prop="currency">
            <el-select
              v-model="formData.currency"
              placeholder="请选择币种"
              style="width: 100%"
              @change="handleCurrencyChange"
            >
              <el-option
                v-for="dict in currencyOptions"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="汇率" prop="exchangeRate">
            <el-input-number
              v-model="formData.exchangeRate"
              :min="0"
              :precision="4"
              :step="0.0001"
              placeholder="请输入汇率"
              style="width: 100%"
            />
            <span v-if="exchangeRateHint" class="rate-hint">{{ exchangeRateHint }}</span>
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 报价明细表格 -->
      <el-divider content-position="left">报价明细</el-divider>
      <el-table :data="formData.items" border style="width: 100%; margin-bottom: 10px">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="产品编码" prop="productCode" width="150">
          <template #default="scope">
            <el-select
              v-if="formData.quotationType === 1"
              v-model="scope.row.productCode"
              placeholder="选择产品或输入编码"
              filterable
              allow-create
              default-first-option
              :loading="productLoading"
              :disabled="!formData.customerId"
              style="width: 100%"
              @change="handleProductChange(scope.row)"
              @focus="handleProductFocus()"
            >
              <el-option
                v-for="item in productOptions"
                :key="item.productCode"
                :label="item.productCode"
                :value="item.productCode"
              />
            </el-select>
            <!-- 样品类型：编码由行内生成器生成，只读展示 -->
            <span v-else-if="scope.row.productCode" class="sample-code">{{
              scope.row.productCode
            }}</span>
            <span v-else class="sample-code-empty">（点击📝生成编码）</span>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" prop="productName" width="180">
          <template #default="scope">
            <el-input
              v-model="scope.row.productName"
              placeholder="产品名称（样品可手动输入）"
              :readonly="isStandardProduct(scope.row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="数量" prop="quantity" width="120">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.quantity"
              :min="1"
              :precision="0"
              controls-position="right"
              @change="calculateItemAmount(scope.row)"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价" prop="unitPrice" width="150">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.unitPrice"
              :min="0"
              :precision="2"
              controls-position="right"
              @change="calculateItemAmount(scope.row)"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" prop="amount" width="110">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.amount) }}</span>
          </template>
        </el-table-column>
        <TableActionColumn :min-width="180" align="center" :fixed="false">
          <template #before="scope">
            <el-button
              v-if="formData.quotationType === 2"
              link
              type="primary"
              @click="openCodeGen(scope.row)"
              >生成编码</el-button
            >
            <el-button
              link
              type="warning"
              :disabled="!scope.row.productCode"
              @click="openRowAttachment(scope.row)"
              >上传附件</el-button
            >
            <el-button
              v-if="formData.quotationType === 1 || formData.quotationType === 2"
              link
              type="danger"
              @click="removeItem(scope.$index)"
              >删除</el-button
            >
          </template>
        </TableActionColumn>
      </el-table>

      <el-row>
        <el-col :span="24" style="text-align: right">
          <el-button type="primary" icon="Plus" @click="addItem">添加明细</el-button>
          <span v-if="formData.quotationType === 2" class="sample-limit-tip"
            >样品明细通过编码生成器添加（可多条）</span
          >
        </el-col>
      </el-row>

      <!-- 金额汇总 -->
      <el-divider content-position="left">金额汇总</el-divider>
      <el-row class="amount-summary">
        <el-col :span="8">
          <el-form-item label="小计金额(元)">
            <el-input v-model="formData.subtotalAmount" readonly style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="税率(%)">
            <el-input-number
              v-model="formData.taxRate"
              :min="0"
              :max="100"
              :precision="2"
              controls-position="right"
              @change="calculateTotalAmount"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="税额(元)">
            <el-input v-model="formData.taxAmount" readonly style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row class="amount-summary">
        <el-col :span="8">
          <el-form-item label="运费(元)">
            <el-input-number
              v-model="formData.shippingFee"
              :min="0"
              :precision="2"
              controls-position="right"
              @change="calculateTotalAmount"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="折扣金额(元)">
            <el-input-number
              v-model="formData.discountAmount"
              :min="0"
              :precision="2"
              controls-position="right"
              @change="calculateTotalAmount"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="总金额(元)">
            <el-input v-model="formData.totalAmount" readonly style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row class="amount-summary">
        <el-col :span="8">
          <el-form-item label="最终金额(元)">
            <el-input v-model="formData.finalAmount" readonly style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row
        v-if="formData.currency && formData.currency !== 'CNY' && formData.exchangeRate > 0"
        class="amount-summary"
      >
        <el-col :span="24">
          <el-form-item label="外币折算">
            <span class="rate-hint" style="font-size: 13px">
              最终金额 {{ formatCurrency(formData.finalAmount) }} CNY ≈
              <b>{{ formatCurrency(Number(foreignCurrencyDisplay)) }} {{ formData.currency }}</b>
              （1 {{ formData.currency }} = {{ formData.exchangeRate }} CNY）
            </span>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              placeholder="请输入备注"
              :rows="3"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="24">
          <!-- 整单级附件（dev-20261016-071：旧 AttachmentCenter → Upload + Manager，共用同一份会话队列）：
               上传只进「待保存」，随单据「保存」落定（001 §7-1 方案 A）；
               附件区「保存」（row-save/batch-save）= 只提交附件信息类改动，不落定。
               明细行「上传附件」只当上传入口（带该行产品归属），看/管以本列表为准。 -->
          <AttachmentUpload
            :session="session"
            class="attach-block"
            drop-zone
            button-text="上传整单附件"
            @uploaded="(item) => session.registerUploaded(item.pending, item.ownership)"
            @error="onAttachError"
          />
          <AttachmentManager
            :session="session"
            class="attach-block"
            title="整单附件"
            source-type="quotation"
            :source-no="formData.quotationNo || ''"
            row-save
            batch-save
            @error="onAttachError"
          />
        </el-col>
      </el-row>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleSubmit">保 存</el-button>
        <el-button @click="handleClose">关 闭</el-button>
      </div>
    </template>

    <!-- 行内编码生成弹窗（样品类型） -->
    <QuotationCodeGenDialog
      v-model="codeGenVisible"
      :customer-short="qShortName"
      :init-state="codeGenInitState"
      :used-serials="usedSerialsForCodeGen"
      @confirm="onCodeGenConfirm"
    />

    <!-- 明细行上传附件（S5 dev-20261013-043：附件挂本单 + 记该行产品编码，不建产品档案） -->
    <el-dialog
      v-model="rowAttachVisible"
      :title="`上传附件【${rowAttachProductCode || ''}】`"
      width="1200px"
      append-to-body
    >
      <!-- 明细行上传入口（与整单区共用同一份会话队列）：只负责「带该行产品归属」的上传；
           查看/管理统一在整单附件区（可按归属筛选本行产品）；行级视图不显示同链路（001 §5-3） -->
      <AttachmentUpload
        v-if="rowAttachVisible"
        :session="session"
        :ownership="rowAttachOwnership"
        drop-zone
        :button-text="`上传到 ${rowAttachProductCode || '本行'}`"
        @uploaded="(item) => session.registerUploaded(item.pending, item.ownership)"
        @error="onAttachError"
      />
      <div class="attach-row-hint">
        上传后进入「待保存」，随报价单「保存」落定；查看/管理请在「整单附件」区（可按归属筛选本行产品）。
      </div>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { formatCurrency } from '@/utils/format'
import { quotationApi } from '@/api/sales/quotation'
import { listProduct } from '@/api/product'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import QuotationCodeGenDialog from './QuotationCodeGenDialog.vue'
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import { productOwnership, type AttachmentSession } from '@/composables/useAttachmentSession'
import type { AttachmentDraftChoice } from '@/composables/useAttachmentRefs'
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'
import { parseProductCode } from '@/composables/useProductCode'

const props = defineProps<{
  modelValue: boolean
  title: string
  formData: any
  currencyOptions: Array<{ value: string; label: string }>
  /** 待绑定队列草稿标识（由父组件下发；新建未保存时报价单复用同一草稿）（dev-20261012-047） */
  draftId?: string
  draftChoices?: AttachmentDraftChoice[]
  /**
   * 附件共享会话（dev-20261016-071）：由容器（index.vue）创建并显式下传，本弹窗只消费。
   * 整单附件区与明细行弹窗共用它 ⇒ 一份待保存队列；落定随单据「保存」。
   */
  session: AttachmentSession
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submit'): void
  (e: 'cancel'): void
  (e: 'restore-draft', id: string): void
  (e: 'discard-draft', id: string): void
}>()

const visible = ref(false)
const formRef = ref<FormInstance>()
const productLoading = ref(false)
const productOptions = ref<Array<{ productId: number; productCode: string; productName: string }>>(
  []
)

// 行内编码生成
const codeGenVisible = ref(false)
const codeGenRow = ref<any>(null)
const codeGenInitState = ref<Partial<ProductCodeState>>({})
const qShortName = ref('')

/** 本单已占用的流水号（排除当前编辑行自身；新增行 codeGenRow 为 null 时取全部） */
const usedSerialsForCodeGen = computed(() => {
  const current = codeGenRow.value
  return (props.formData.items || [])
    .filter((item: any) => item !== current)
    .map((item: any) => item.serialNo)
    .filter((s: any) => s && String(s).trim())
    .map((s: any) => String(s).trim().padStart(3, '0'))
})

// 明细行「上传附件」（S5 dev-20261013-043）
const rowAttachVisible = ref(false)
const rowAttachProductCode = ref('')
watch(() => props.draftId, () => { rowAttachVisible.value = false; rowAttachProductCode.value = '' })
/** 该行产品 id（行内上传归属；未建档/未回填时为 null，归属退回到稳定行键） */
const rowAttachProductId = computed<number | null>(() => {
  const code = rowAttachProductCode.value
  if (!code) return null
  const row = (props.formData.items || []).find((it: any) => it?.productCode === code)
  return row?.productId ?? null
})
/** 行级上传归属：正式 productId 优先，否则用不随渲染变化的行键（产品编码）作稳定身份（043/048） */
const rowAttachOwnership = computed(() =>
  productOwnership({
    rowKey: rowAttachProductCode.value,
    productId: rowAttachProductId.value,
    productCode: rowAttachProductCode.value,
  }),
)
const uploadedAttachmentIds = ref<number[]>([])

/** 附件上传/校验失败提示：上传失败组件内已提示，这里只补「选择文件校验」这类没有内建提示的错误 */
function onAttachError(payload: { action: string; message: string }) {
  if (payload.action === 'upload') return
  ElMessage.error(payload.message || '附件处理失败')
}

const exchangeRateHint = computed(() => {
  if (!props.formData.currency || props.formData.currency === 'CNY') return ''
  return `1 ${props.formData.currency} = ${props.formData.exchangeRate} CNY`
})

const foreignCurrencyDisplay = computed(() => {
  if (
    !props.formData.exchangeRate ||
    !props.formData.finalAmount ||
    props.formData.currency === 'CNY'
  ) {
    return '0.00'
  }
  return (props.formData.finalAmount / props.formData.exchangeRate).toFixed(2)
})

const rules = reactive<FormRules>({
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  quotationDate: [{ required: true, message: '请选择报价日期', trigger: 'change' }],
  validUntil: [{ required: true, message: '请选择有效期至', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
})

watch(
  () => props.modelValue,
  (val) => {
    uploadedAttachmentIds.value = []
    visible.value = val
  },
  { immediate: true }
)

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const onCustomerChange = (val: any, customer: any) => {
  if (customer) {
    props.formData.customerName = customer.customerName
    qShortName.value = (customer.customerShortName || '').substring(0, 3)
    // 联系人/电话照销售订单、样品单口径带出客户档案值（仅在为空时带，用户可改）
    if (!props.formData.contactPerson) props.formData.contactPerson = customer.contactPerson || ''
    if (!props.formData.contactPhone) props.formData.contactPhone = customer.contactPhone || ''
  } else {
    props.formData.customerName = ''
    qShortName.value = ''
  }
  productOptions.value = []
  props.formData.items.forEach((item: any) => {
    item.productId = undefined
    item.productCode = ''
    item.productName = ''
  })
}

// 2026-09-10 按 Leo 要求：汇率不做任何兜底——取不到就清空并明确提示，由用户手工填写
const handleCurrencyChange = async (val: string) => {
  if (val === 'CNY') {
    props.formData.exchangeRate = 1
    return
  }
  props.formData.exchangeRate = undefined as unknown as number
  try {
    const res = await quotationApi.getExchangeRate(val)
    if (res?.code === 200 && res.data) {
      props.formData.exchangeRate = res.data
    } else {
      ElMessage.warning(`未取到 ${val} 的实时汇率，请手工填写`)
    }
  } catch (e) {
    ElMessage.warning(`实时汇率获取失败，请手工填写 1 ${val} 对 CNY 的汇率`)
  }
}

// ============================================================
// 行内编码生成（样品类型，针对明细行产品）
// ============================================================
const openCodeGen = (row: any) => {
  codeGenRow.value = row
  // 编辑回显：行上已有参数直接用；无参数尝试从编码反解
  codeGenInitState.value = {
    serialNo: row.serialNo,
    panelType: row.panelType,
    panelFeature: row.panelFeature,
    circuitType: row.circuitType,
    circuitFeature: row.circuitFeature,
  }
  if (!hasAnyCodeParam(codeGenInitState.value) && row.productCode) {
    codeGenInitState.value = parseCodeFromProductCode(row.productCode)
  }
  codeGenVisible.value = true
}

const onCodeGenConfirm = (params: ProductCodeResult) => {
  if (codeGenRow.value) {
    // 编辑已有行：只改编码+参数，保留手改的产品名称（2026-09-02）
    const row = codeGenRow.value
    row.productCode = params.productCode
    // 编码变了 → 清掉旧 productId，提交时后端按新编码重新建档（2026-09-02）
    row.productId = undefined
    row.serialNo = params.serialNo
    row.panelType = params.panelType
    row.panelFeature = params.panelFeature
    row.circuitType = params.circuitType
    row.circuitFeature = params.circuitFeature
  } else {
    // 新增行：回填编码；产品名称不默认编码（2026-09-02：必填，由销售在表格填写）
    props.formData.items.push({
      productId: undefined,
      productCode: params.productCode,
      productName: '',
      quantity: 1,
      unitPrice: 0,
      amount: 0,
      unit: 'PCS',
      // 编码参数（样品类型静默携带，提交时后端建档用）
      serialNo: params.serialNo,
      panelType: params.panelType,
      panelFeature: params.panelFeature,
      circuitType: params.circuitType,
      circuitFeature: params.circuitFeature,
    })
  }
  calculateTotalAmount()
  ElMessage.success(`编码已生成：${params.productCode}`)
}

const openRowAttachment = (row: any) => {
  if (!row.productCode) {
    ElMessage.warning('请先生成产品编码')
    return
  }
  rowAttachProductCode.value = row.productCode
  rowAttachVisible.value = true
}

// ============================================================
// 标准品产品选择
// ============================================================
const handleProductChange = (item: any) => {
  const selectedProduct = productOptions.value.find(
    (product) => product.productCode === item.productCode
  )
  if (selectedProduct) {
    item.productName = selectedProduct.productName
    item.productId = selectedProduct.productId
  } else if (item.productCode) {
    item.productId = undefined
    if (!item.productName || item.productName.startsWith('产品_')) {
      item.productName = ''
    }
  }
}

const handleProductFocus = async () => {
  if (productOptions.value.length === 0) {
    productLoading.value = true
    try {
      const res = await listProduct({
        pageNum: 1,
        pageSize: 50,
        customerId: props.formData.customerId,
      } as any)
      const data = (res?.data as any)?.records || res?.data || []
      productOptions.value = data.map((p: any) => ({
        productId: p.productId,
        productCode: p.productCode,
        productName: p.productName,
      }))
    } catch {
      productOptions.value = []
    } finally {
      productLoading.value = false
    }
  }
}

const isStandardProduct = (item: any) => {
  return productOptions.value.some((product) => product.productCode === item.productCode)
}

const addItem = () => {
  // 样品类型：直接弹出编码生成窗，确定后落行（2026-09-02）
  if (props.formData.quotationType === 2) {
    codeGenRow.value = null // null = 新增行模式
    codeGenInitState.value = {}
    if (!qShortName.value) {
      ElMessage.warning('请先选择客户')
      return
    }
    codeGenVisible.value = true
    return
  }
  props.formData.items.push({
    productId: undefined,
    productCode: '',
    productName: '',
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    unit: 'PCS',
  })
}

const removeItem = (index: number) => {
  props.formData.items.splice(index, 1)
  calculateTotalAmount()
}

const calculateItemAmount = (item: any) => {
  item.amount = (item.quantity || 0) * (item.unitPrice || 0)
  calculateTotalAmount()
}

const calculateTotalAmount = () => {
  const form = props.formData
  form.subtotalAmount = form.items.reduce((sum: number, item: any) => sum + (item.amount || 0), 0)
  form.taxAmount = (form.subtotalAmount * (form.taxRate || 0)) / 100
  // 运费单列、不计税；总金额为含税含运费，最终金额=总金额-折扣（对齐销售订单口径）
  form.totalAmount = form.subtotalAmount + form.taxAmount + (form.shippingFee || 0)
  form.finalAmount = form.totalAmount - (form.discountAmount || 0)
}

const handleSubmit = () => {
  formRef.value?.validate((valid) => {
    if (valid) {
      if (props.formData.items.length === 0) {
        ElMessage.warning('请至少添加一条报价明细')
        return
      }
      for (const item of props.formData.items) {
        if (!item.productCode || !item.productName) {
          ElMessage.warning('请填写完整的产品信息（产品名称必填）')
          return
        }
        if (item.quantity <= 0) {
          ElMessage.warning('数量必须大于0')
          return
        }
        // 2026-09-02：单价必须大于0（样品明细不得免费/零价）
        if (item.unitPrice <= 0) {
          ElMessage.warning('单价必须大于0')
          return
        }
      }
      // 2026-09-02：保存防线——样品类型明细编码不得重复（同码会导致两行指向同一草稿产品）
      if (props.formData.quotationType === 2) {
        const seen = new Map<string, number>()
        for (const item of props.formData.items) {
          const code = (item.productCode || '').trim()
          if (!code) continue
          if (seen.has(code)) {
            ElMessage.error(
              `明细产品编码重复：${code}（第${seen.get(code)}行与当前行），请重新生成`
            )
            return
          }
          seen.set(code, props.formData.items.indexOf(item) + 1)
        }
      }
      if (props.formData.quotationId !== undefined && props.formData.quotationId > 0) {
        props.formData.attachmentIds = [...uploadedAttachmentIds.value]
      }
      emit('submit')
    }
  })
}

const handleClose = () => {
  uploadedAttachmentIds.value = []
  visible.value = false
  emit('cancel')
}

// ============================================================
// 工具：编码参数反解/判断
// ============================================================
function hasAnyCodeParam(state: Partial<ProductCodeState>): boolean {
  return !!(
    state.serialNo ||
    state.panelType ||
    state.panelFeature ||
    state.circuitType ||
    state.circuitFeature
  )
}

/** 从产品编码反解构成要素（口径统一在 useProductCode.parseProductCode，2026-09-30 dev-20260930-011） */
function parseCodeFromProductCode(code?: string | null): Partial<ProductCodeState> {
  const parsed = parseProductCode(code)
  if (!parsed) {
    return { serialNo: '', panelType: '', panelFeature: '', circuitType: '', circuitFeature: '' }
  }
  const { serialNo, panelType, panelFeature, circuitType, circuitFeature } = parsed
  return { serialNo, panelType, panelFeature, circuitType, circuitFeature }
}

defineExpose({
  formRef,
  calculateTotalAmount,
})
</script>

<style scoped>
/* 金额汇总：统一对齐口径（2026-09-22 用户反馈「有些居中有些居左」）
   el-input-number 默认 text-align:center，而同区块里 readonly 的 el-input 是左对齐 →
   两种对齐混排；这里把金额汇总区的数字框统一成左对齐，与表单其它输入框一致。 */
.amount-summary :deep(.el-input-number .el-input__inner) {
  text-align: left;
}
.rate-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.dialog-footer {
  text-align: right;
}
.attach-block {
  margin-bottom: 8px;
}
.attach-row-hint {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}
.sample-code {
  color: #409eff;
  font-weight: 500;
}
.sample-code-empty {
  color: #c0c4cc;
  font-size: 12px;
}
</style>
