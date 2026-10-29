<!-- views/sales/quotation/components/QuotationFormDialog.vue
  2026-10-18：报价类型共用已有产品选择和行内新建产品入口；
  明细行 📎资料 → 产品文件库（按 product_id/编码挂载，保存建档后可用）
-->
<template>
  <el-dialog
    :title="title"
    v-model="visible"
    max-width="1300px"
    width="80%"
    append-to-body
    @close="handleClose"
  >
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
            <el-input
              v-model="formData.contactPhone"
              placeholder="默认带出客户电话（手机号或座机）"
            />
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
        <el-table-column label="产品编码" prop="productCode" width="170">
          <template #default="scope">
            <el-select
              v-model="scope.row.productCode"
              placeholder="选择已有产品"
              filterable
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
          </template>
        </el-table-column>
        <el-table-column label="产品名称" prop="productName" width="180">
          <template #default="scope">
            <el-input
              v-model="scope.row.productName"
              placeholder="产品名称"
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
        <el-table-column label="交期(天)" width="110" align="center">
          <template #default="scope">
            <span :title="'按 预计交期 − 报价日期 推算'">{{ deliveryDaysOf(scope.row) ?? '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="预计交期" prop="estimatedDeliveryDate" width="180">
          <template #default="scope">
            <el-date-picker
              v-model="scope.row.estimatedDeliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择预计交期"
              aria-label="预计交期"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column :min-width="180" align="center" fixed="right" label="操作">
          <template #header>
            <span>操作</span
            ><el-button type="primary" @click="addItem" size="small" text>添加明细</el-button>
          </template>
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openCodeGen(scope.row)"
              >新建</el-button
            >
            <el-button
              link
              size="small"
              type="warning"
              :disabled="!scope.row.productCode"
              @click="openRowAttachment(scope.row)"
              >上传附件</el-button
            >
            <el-button
              v-if="formData.quotationType === 1 || formData.quotationType === 2"
              link
              size="small"
              type="danger"
              @click="removeItem(scope.$index)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <el-row>
        <el-col :span="24">
          <AttachmentManager
            :session="session"
            class="attach-block"
            title="附件"
            source-type="quotation"
            :source-no="formData.quotationNo || ''"
            row-save
            @error="onAttachError"
          />
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
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleSubmit">保 存</el-button>
        <el-button @click="handleClose">关 闭</el-button>
      </div>
    </template>

    <!-- 行内新建产品编码生成弹窗 -->
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
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'

const props = defineProps<{
  modelValue: boolean
  title: string
  formData: any
  currencyOptions: Array<{ value: string; label: string }>
  /** 待绑定队列草稿标识（由父组件下发；新建未保存时报价单复用同一草稿）（dev-20261012-047） */
  draftId?: string
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
/** 当前生效客户 ID：区分「换客户」与「同一客户重选」（dev-20261020-005） */
const activeCustomerId = ref<number | null>(null)
/** 交期天数：由 预计交期 − 报价日期 推算（唯一真源是日期，dev-20261028-016） */
function deliveryDaysOf(row: any): number | null {
  const estimated = row?.estimatedDeliveryDate
  const base = props.formData?.quotationDate
  if (!estimated || !base) return null
  const days = Math.round(
    (new Date(`${estimated}T00:00:00`).getTime() - new Date(`${base}T00:00:00`).getTime()) / 86400000
  )
  return days > 0 ? days : null
}

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
watch(
  () => props.draftId,
  () => {
    rowAttachVisible.value = false
    rowAttachProductCode.value = ''
  }
)
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
  })
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
    // 打开/切换单据时同步客户身份快照：编辑回填是程序赋值，不触发 change，不覆盖单据自带联系人
    if (val) {
      activeCustomerId.value = props.formData.customerId ? Number(props.formData.customerId) : null
    }
  },
  { immediate: true }
)

watch(visible, (val) => {
  emit('update:modelValue', val)
})

/**
 * 客户变化（dev-20261020-005）：联系人/电话跟随新客户档案
 * 1）换客户 → 用新客户档案覆盖；客户没填则置空，不保留上一位客户的值；
 * 2）同一客户重选 → 不覆盖，保留用户手工修改；
 * 3）清空客户 → 联系人/电话一并清空。
 */
const onCustomerChange = (val: any, customer: any) => {
  const nextCustomerId = val ? Number(val) : null
  const customerChanged = activeCustomerId.value !== nextCustomerId
  if (customer) {
    props.formData.customerName = customer.customerName
    qShortName.value = (customer.customerShortName || '').substring(0, 3)
  } else {
    props.formData.customerName = ''
    qShortName.value = ''
  }
  if (customerChanged) {
    props.formData.contactPerson = nextCustomerId ? customer?.contactPerson || '' : ''
    props.formData.contactPhone = nextCustomerId ? customer?.contactPhone || '' : ''
  }
  activeCustomerId.value = nextCustomerId
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
// 行内新建产品：两种报价类型共用编码生成器
// ============================================================
const openCodeGen = (row: any) => {
  if (!qShortName.value) {
    ElMessage.warning('请先选择客户')
    return
  }
  codeGenRow.value = row
  // 新建产品始终从空参数开始；确认前不改原行，取消即可保留原产品。
  codeGenInitState.value = {}
  codeGenVisible.value = true
}

const onCodeGenConfirm = (params: ProductCodeResult) => {
  if (codeGenRow.value) {
    // 确认新建后替换产品身份，名称必须重新填写，避免沿用旧产品名称。
    const row = codeGenRow.value
    row.productCode = params.productCode
    // 编码变了 → 清掉旧 productId，提交时后端按新编码重新建档（2026-09-02）
    row.productId = undefined
    row.productName = ''
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
      // 编码参数随报价提交，供后端建档使用
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
// 已有产品选择（按客户过滤）
// ============================================================
const handleProductChange = (item: any) => {
  const selectedProduct = productOptions.value.find(
    (product) => product.productCode === item.productCode
  )
  if (selectedProduct) {
    item.productName = selectedProduct.productName
    item.productId = selectedProduct.productId
    item.serialNo = undefined
    item.panelType = undefined
    item.panelFeature = undefined
    item.circuitType = undefined
    item.circuitFeature = undefined
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
  return !!item.productId
}

const addItem = () => {
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
        // 交期天数改由 预计交期 − 报价日期 推算（dev-20261028-016），不再校验手填值
      }
      // 两种报价类型均禁止重复产品编码，与后端保持一致
      const seen = new Map<string, number>()
      for (const item of props.formData.items) {
        const code = (item.productCode || '').trim()
        if (!code) continue
        if (seen.has(code)) {
          ElMessage.error(`明细产品编码重复：${code}（第${seen.get(code)}行与当前行），请重新生成`)
          return
        }
        seen.set(code, props.formData.items.indexOf(item) + 1)
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
