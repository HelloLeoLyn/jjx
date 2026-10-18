<template>
  <el-form ref="orderFormRef" :model="form" :rules="rules" label-width="120px">
    <!-- 订单基本信息 -->
    <el-divider content-position="left">订单基本信息</el-divider>
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
        <el-form-item label="运输方式" prop="shippingMethod">
          <el-select v-model="form.shippingMethod" placeholder="请选择运输方式" style="width: 100%">
            <el-option
              v-for="dict in shippingMethodOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="联系人" prop="contactPerson">
          <el-input v-model="form.contactPerson" placeholder="请输入联系人"> </el-input>
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
        <el-form-item label="订单日期" prop="orderDate">
          <el-date-picker
            v-model="form.orderDate"
            type="date"
            placeholder="请选择订单日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="交货日期" prop="deliveryDate">
          <el-date-picker
            v-model="form.deliveryDate"
            type="date"
            placeholder="请选择交货日期"
            value-format="YYYY-MM-DD"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="销售负责人" prop="salesPersonId">
          <el-select
            v-model="form.salesPersonId"
            placeholder="请选择销售负责人"
            filterable
            style="width: 100%"
            @change="salesPersonChanged"
          >
            <el-option
              v-for="item in salesPersonOptions"
              :key="item.userId"
              :label="item.nickName"
              :value="item.userId"
            />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="币种" prop="currency">
          <el-select
            v-model="form.currency"
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
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="汇率" prop="exchangeRate">
          <el-input-number
            v-model="form.exchangeRate"
            :min="0"
            :precision="4"
            :step="0.0001"
            placeholder="请输入汇率"
            style="width: 100%"
          />
          <span v-if="exchangeRateHint" class="rate-hint">{{ exchangeRateHint }}</span>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="付款条件" prop="paymentTerms">
          <el-select v-model="form.paymentTerms" placeholder="请选择付款条件" style="width: 100%">
            <el-option
              v-for="dict in paymentTermsOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <el-divider content-position="left">收货信息</el-divider>
    <!-- <el-row :gutter="10" style="margin-bottom: 8px">
      <el-col :span="16">
        <el-select
          v-model="addressPick"
          placeholder="从客户地址簿 / 公司地址选择（也可直接手填下方地址）"
          clearable
          filterable
          style="width: 100%"
          @change="onAddressPick"
        >
          <el-option v-for="o in addressOptions" :key="o.key" :label="o.label" :value="o.key" />
        </el-select>
      </el-col>
      <el-col :span="8">
        <el-button :disabled="!form.customerId" @click="addressDialogVisible = true"
          >新增/维护地址</el-button
        >
      </el-col>
    </el-row> -->
    <el-row>
      <el-col :span="24">
        <InternationalAddressEditor v-model="form.shippingAddress" prop-path="address" />
      </el-col>
    </el-row>

    <!-- 价格来源提示（2026-09-21 dev-20260921-040）：转量产单价自动带出/需补价时给出明确提示 -->
    <el-alert
      v-if="priceAutoFillHint"
      :title="priceAutoFillHint"
      :type="priceAutoFillWarning ? 'warning' : 'success'"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />

    <!-- 订单产品明细 -->
    <el-divider content-position="left"
      ><el-link @click="goToProductIndex()"
        >订单产品明细（点击可以跳转的产品列表页）</el-link
      ></el-divider
    >
    <el-table :data="form.items" style="width: 100%; margin-bottom: 20px" border>
      <el-table-column label="序号" type="index" width="60" align="center" />
      <el-table-column label="产品编码" prop="productCode" min-width="200">
        <template #default="scope">
          <!-- 标准单：ProductSelector 远程搜索选产品 -->
          <ProductSelector
            :model-value="scope.row.productCode"
            value-type="productCode"
            :customer-id="form.customerId"
            status-scope="active"
            :disabled="!form.customerId"
            placeholder="请先选择客户，再搜索该客户的产品"
            :min-keyword-length="1"
            class="borderless-input"
            @change="(val: any, product: any) => handleProductChange(scope.row, val, product)"
          />
        </template>
      </el-table-column>
      <el-table-column label="产品名称" prop="productName" width="160">
        <template #default="scope">
          <el-input
            v-model="scope.row.productName"
            placeholder="请输入新产品名称"
            :readonly="!!scope.row.productId"
            maxlength="20"
            class="borderless-input"
          />
        </template>
      </el-table-column>
      <el-table-column label="客户物料号" prop="customerMaterialNo" width="120">
        <template #default="scope">
          <el-input
            v-model="scope.row.customerMaterialNo"
            placeholder="客户物料号"
            class="borderless-input"
          />
        </template>
      </el-table-column>
      <el-table-column label="单位" prop="unit" width="80">
        <template #default="scope">
          <el-input v-model="scope.row.unit" placeholder="单位" class="borderless-input" />
        </template>
      </el-table-column>
      <el-table-column label="数量" prop="quantity" width="100">
        <template #default="scope">
          <el-input
            v-model="scope.row.quantity"
            :min="1"
            :precision="0"
            @change="calculateItemAmount(scope.row)"
            style="width: 100%"
            class="borderless-input"
            type="number"
          />
        </template>
      </el-table-column>
      <el-table-column label="单价" prop="unitPrice" width="160">
        <template #default="scope">
          <el-input-number
            v-model="scope.row.unitPrice"
            :min="0"
            :precision="2"
            @change="calculateItemAmount(scope.row)"
            style="width: 100%"
            class="borderless-input"
          />
        </template>
      </el-table-column>
      <el-table-column label="金额" prop="amount" width="120">
        <template #default="scope">
          <span>{{ formatCurrency(scope.row.amount) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="交期(天)" prop="deliveryDays" width="100">
        <template #default="scope">
          <el-input
            v-model="scope.row.deliveryDays"
            :min="1"
            :precision="0"
            style="width: 100%"
            type="number"
          />
        </template>
      </el-table-column>
      <el-table-column label="行备注" prop="lineRemark" min-width="120">
        <template #default="scope">
          <el-input v-model="scope.row.lineRemark" placeholder="行备注" />
        </template>
      </el-table-column>
      <TableActionColumn width="260" align="center" fixed="right">
        <template #header>
          <div class="action-header">
            <span>操作</span>
            <el-button type="primary" link @click="addItem()"> 添加明细 </el-button>
          </div>
        </template>
        <template #before="scope">
          <el-button link type="primary" :disabled="!scope.row.productCode" @click="openProductAttachments(scope.row)">
            附件（{{ productAttachmentCount(scope.row) }}）
          </el-button>
          <el-button link type="danger" @click="removeItem(scope.$index)">删除</el-button>
          <el-button
            link
            type="primary"
            :disabled="!form.customerId"
            @click="openNewProduct(scope.row)"
            >新建产品</el-button
          >
        </template>
      </TableActionColumn>
    </el-table>

    <!-- 附件上传（新建和编辑都显示） -->
    <el-divider content-position="left">订单附件</el-divider>
    <div class="attachment-section">
      <el-dropdown v-if="!form.orderId && draftChoices.length" @command="restoreAttachmentDraft">
        <el-button plain>恢复未保存草稿</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="draft in draftChoices" :key="draft.id" :command="draft.id">{{
              draft.label
            }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown
        v-if="!form.orderId && draftChoices.length"
        style="margin-left: 8px"
        @command="discardAttachmentDraft"
      >
        <el-button plain type="danger">放弃草稿</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="draft in draftChoices" :key="draft.id" :command="draft.id">{{
              draft.label
            }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <!-- 订单附件（dev-20261016-073：旧 AttachmentCenter → Upload + Manager，共用同一份会话队列；
           dev-20261018-019：上传归属可选整单级/产品级，归属在上传发起时固定）：
           上传只进「待保存」，随订单「保存」落定（001 §7-1 方案 A）；
           附件区「保存」（row-save / batch-save）= 只提交附件信息类改动，不落定。 -->
      <div class="attach-ownership">
        <span class="attach-ownership-label">上传归属</span>
        <el-select
          v-model="uploadOwnershipKey"
          size="small"
          style="width: 300px"
          placeholder="选择附件归属"
        >
          <el-option
            v-for="option in uploadOwnershipOptions"
            :key="option.key"
            :label="option.label"
            :value="option.key"
          />
        </el-select>
        <span class="attach-ownership-tip">产品级附件保存后挂到该产品；整单级不归属产品</span>
      </div>
      <AttachmentUpload
        :session="attachSession"
        :ownership="uploadOwnership"
        class="attach-block"
        :accept="['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.jpg', '.jpeg', '.png']"
        drop-zone
        button-text="上传订单附件"
        @uploaded="(item) => attachSession.registerUploaded(item.pending, item.ownership)"
        @error="onAttachError"
      />
      <AttachmentManager
        :session="attachSession"
        class="attach-block"
        title="订单附件"
        source-type="sales_order"
        :source-no="(form as any)?.orderNo || ''"
        @error="onAttachError"
      />
    </div>

    <el-dialog v-model="productAttachmentsVisible" :title="`产品附件 · ${productAttachmentIdentity?.productCode || ''}`" width="1000px" append-to-body destroy-on-close>
      <p>已保存 {{ productAttachmentSession.refRows.value.length }} 个，待保存 {{ productAttachmentSession.pending.value.length }} 个；上传后随订单保存。</p>
      <AttachmentUpload :session="attachSession" :ownership="productAttachmentOwnership" button-text="上传产品附件"
        @uploaded="(item) => attachSession.registerUploaded(item.pending, item.ownership)" @error="onAttachError" />
      <AttachmentManager v-if="productAttachmentsVisible" :session="productAttachmentSession" :show-ownership-filter="false" source-type="sales_order" title="本产品附件" @error="onAttachError" />
      <template #footer><el-button @click="productAttachmentsVisible = false">关闭</el-button></template>
    </el-dialog>

    <!-- 金额汇总 -->
    <el-divider content-position="left">金额汇总</el-divider>
    <el-alert
      v-if="!form.amountBreakdownConfirmed"
      title="本单为历史订单，金额组成尚未确认，汇总暂按原金额冻结。修改价格、数量、运费或折扣时会提示确认，确认后按新组成重新计算。"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 12px"
    />
    <el-checkbox
      v-if="!form.amountBreakdownConfirmed"
      v-model="form.amountBreakdownConfirmed"
      @change="calculateTotalAmount"
      >确认运费和折扣，重新计算金额</el-checkbox
    >
    <el-row class="amount-summary" :gutter="20">
      <el-col :span="8">
        <el-form-item label="未税小计">
          <el-input v-model="form.subtotalAmount" readonly style="width: 100%">
            <template #append>元</template>
          </el-input>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="税率(%)">
          <el-input-number
            v-model="form.taxRate"
            :min="0"
            :max="100"
            :precision="2"
            @change="onPricingChange"
            style="width: 100%"
          >
            <template #append>%</template>
          </el-input-number>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="税额">
          <el-input v-model="form.taxAmount" readonly style="width: 100%">
            <template #append>元</template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row class="amount-summary" :gutter="20">
      <el-col :span="8">
        <el-form-item label="运费（不计税）">
          <el-input-number
            v-model="form.shippingFee"
            :min="0"
            :precision="2"
            @change="onPricingChange"
            style="width: 100%"
          >
            <template #append>元</template>
          </el-input-number>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="折扣金额">
          <el-input-number
            v-model="form.discountAmount"
            :min="0"
            :precision="2"
            @change="onPricingChange"
            style="width: 100%"
          >
            <template #append>元</template>
          </el-input-number>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="应付总额">
          <el-input v-model="form.finalAmount" readonly style="width: 100%">
            <template #append>元</template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row class="amount-summary" :gutter="20" v-if="form.currency && form.currency !== 'CNY'">
      <el-col :span="8">
        <el-form-item :label="`外币总金额（${form.currency}）`">
          <el-input v-model="foreignCurrencyDisplay" readonly style="width: 100%">
            <template #append>{{ form.currency }}</template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- 其他信息 -->
    <el-divider content-position="left">其他信息</el-divider>
    <el-row :gutter="20">
      <el-col :span="24">
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            placeholder="请输入备注"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-col>
    </el-row>
    <OrderProductCodeGenDialog
      v-model="productCodeDialogVisible"
      :customer-short="productCustomerShort"
      :used-serials="form.items.map((item) => item.serialNo || '').filter(Boolean)"
      @confirm="confirmNewProduct"
    />
    <!-- 新增客户弹窗 -->
    <CustomerFormDialog
      v-model:visible="customerDialogVisible"
      title="新增客户"
      :form-data="customerFormData"
      @success="handleCustomerSuccess"
      @cancel="customerDialogVisible = false"
    />
    <!-- 客户收货地址簿（新增/维护 + 选为本单地址） dev-20261008-029 -->
    <CustomerAddressDialog
      v-model="addressDialogVisible"
      :customer-id="form.customerId"
      :customer-name="form.customerName"
      selectable
      @select="onAddressPickedFromDialog"
      @changed="loadCustomerAddresses"
    />
  </el-form>
</template>

<script setup lang="ts">
import OrderProductCodeGenDialog from './OrderProductCodeGenDialog.vue'
import type { OrderItem } from '@/types/sales/order'
import type { ProductCodeResult } from '@/composables/useProductCode'
import WorkSpecVersionLink from '@/components/product/WorkSpecVersionLink.vue'
import { onActivated, onMounted, ref, reactive, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import {
  useAttachmentSession,
  type AttachmentChanges,
  type AttachmentOwnership,
  type AttachmentSaveResult,
} from '@/composables/useAttachmentSession'
import { useAttachmentDraftInstance } from '@/composables/useAttachmentRefs'
import { orderApi } from '@/api/sales/order'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { customerApi } from '@/api/sales/customer'
import { serializeAddress } from '@/types/sales/address'
import { quotationApi } from '@/api/sales/quotation'
import { useOrderForm } from '../composables/useOrderForm'
import InternationalAddressEditor from '@/components/InternationalAddressEditor.vue'
import CustomerFormDialog from '../../customer/components/CustomerFormDialog.vue'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import type { CustomerFormData, SalesCustomerAddress } from '@/types/sales/customer'
import { useCompanyConfig } from '@/composables/useCompanyConfig'
import CustomerAddressDialog from '../../customer/components/CustomerAddressDialog.vue'

interface Props {
  isEdit?: boolean
  orderId?: number
  initialData?: Record<string, any>
  /** 从样品单转量产：进入后预填样品数据，提交走转量产接口（2026-09-07） */
  sampleOrderId?: number
}

const props = withDefaults(defineProps<Props>(), {
  isEdit: false,
  orderId: undefined,
  initialData: () => ({}),
  sampleOrderId: undefined,
})

const emit = defineEmits<{
  success: []
  cancel: []
}>()

const router = useRouter()
const goToProductIndex = () => {
  router.push('/product/list')
}

// 新增客户弹窗
const customerDialogVisible = ref(false)
const customerFormData = reactive<CustomerFormData>({
  customerId: undefined,
  customerCode: '',
  customerName: '',
  customerShortName: '',
  customerType: undefined,
  customerLevel: undefined,
  customerStatus: undefined,
  industryCategory: '',
  customerSource: undefined,
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  fax: '',
  address: '',
  creditLimit: 0,
  usedCreditLimit: 0,
  customerScore: 3,
  paymentMethod: undefined,
  vip: false,
  remark: '',
})

const goToCustomerAdd = () => {
  // 重置表单数据
  Object.assign(customerFormData, {
    customerId: undefined,
    customerCode: '系统自动生成',
    customerName: '',
    customerShortName: '',
    customerType: undefined,
    customerLevel: undefined,
    customerStatus: undefined,
    industryCategory: '',
    customerSource: undefined,
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    fax: '',
    address: '',
    creditLimit: 0,
    usedCreditLimit: 0,
    customerScore: 3,
    paymentMethod: undefined,
    vip: false,
    remark: '',
  })
  customerDialogVisible.value = true
}

const handleCustomerSuccess = (data: CustomerFormData) => {
  customerDialogVisible.value = false
  // 自动填充订单表单
  form.customerId = data.customerId
  form.contactPerson = data.contactPerson || ''
  form.contactPhone = data.contactPhone || ''
  // 刷新客户下拉列表
  if (data.customerName) {
    searchCustomer(data.customerName)
  }
}

// 使用订单表单可组合函数
const {
  orderFormRef,
  customerLoading,
  submitting,
  customerOptions,
  currencyOptions,
  paymentTermsOptions,
  shippingMethodOptions,
  salesPersonOptions,
  form,
  rules,
  searchCustomer,
  customerChanged,
  loadSalesPersons,
  salesPersonChanged,
  handleProductChange,
  applyNewProduct,
  validateForm,
  calculateItemAmount,
  calculateTotalAmount,
  onPricingChange,
  addItem,
  removeItem,
  resetForm,
  generateOrderNo,
  loadOrderData,
  submitForm: submitOrderForm,
  formatCurrency,
} = useOrderForm({ isEdit: props.isEdit, initialData: props.initialData })

// 编码生成只在确认时改行；每次打开不传旧编码参数。
const productCodeDialogVisible = ref(false)
const productCustomerShort = ref('')
let productEntryRow: OrderItem | null = null
let productEntryCustomerId: number | undefined
let productEntryRequest = 0
async function openNewProduct(row: OrderItem) {
  const customerId = form.customerId
  if (!customerId) return
  const request = ++productEntryRequest
  productEntryRow = null
  productCodeDialogVisible.value = false
  try {
    const res = await customerApi.getCustomer(customerId)
    if (
      request !== productEntryRequest ||
      customerId !== form.customerId ||
      !form.items.includes(row)
    )
      return
    const short = res.data?.customerShortName?.trim()
    if (!short) {
      ElMessage.warning('请先在客户档案填写客户简称')
      return
    }
    productCustomerShort.value = short
    productEntryCustomerId = customerId
    productEntryRow = row
    productCodeDialogVisible.value = true
  } catch {
    ElMessage.error('读取客户简称失败，请重试')
  }
}
function confirmNewProduct(params: ProductCodeResult) {
  if (
    productEntryRow &&
    productEntryCustomerId === form.customerId &&
    form.items.includes(productEntryRow)
  ) {
    applyNewProduct(productEntryRow, params)
  }
  productEntryRow = null
  productCodeDialogVisible.value = false
}
watch(
  () => form.customerId,
  () => {
    productEntryRequest++
    productEntryRow = null
    productCodeDialogVisible.value = false
  }
)

// ===== 汇率自动填充 =====
const exchangeRateLoading = ref(false)

// 外币总金额显示（订单选外币时，将人民币总金额折算成外币）
const foreignCurrencyDisplay = computed(() => {
  if (!form.exchangeRate || !form.finalAmount || form.currency === 'CNY') return 0
  // 汇率 = 1外币 = N人民币，所以外币金额 = 人民币总金额 / 汇率
  const foreignAmount = form.finalAmount / form.exchangeRate
  return foreignAmount.toFixed(2)
})

// 汇率提示文字
const exchangeRateHint = computed(() => {
  if (!form.currency || form.currency === 'CNY') return ''
  return `1 ${form.currency} = ${form.exchangeRate} CNY`
})

// 币种变化时自动获取实时汇率
// 2026-09-10 按 Leo 要求：汇率不做任何兜底——取不到就清空并明确提示，由用户手工填写，
// 禁止拿旧值/1 冒充实时汇率参与报价金额计算
const handleCurrencyChange = async (val: string) => {
  if (val === 'CNY') {
    form.exchangeRate = 1
    return
  }
  form.exchangeRate = undefined as unknown as number
  exchangeRateLoading.value = true
  try {
    const res = await orderApi.getExchangeRate(val)
    if (res?.code === 200 && res.data) {
      form.exchangeRate = res.data
    } else {
      ElMessage.warning(`未取到 ${val} 的实时汇率，请手工填写`)
    }
  } catch (e) {
    ElMessage.warning(`实时汇率获取失败，请手工填写 1 ${val} 对 CNY 的汇率`)
  } finally {
    exchangeRateLoading.value = false
  }
}

// ===== 附件（dev-20261016-073：新 Upload/Manager + 单据实例共享会话） =====
/** 未保存单据按实例隔离；刷新恢复当前实例，显式恢复可找回已关闭实例。 */
const draftSession = useAttachmentDraftInstance('sales_order', {
  snapshot: () => form,
  restore: (data) => {
    resetForm()
    Object.assign(form, data)
  },
})
const { draftId, choices: draftChoices, complete: clearDraftId } = draftSession
function ensureDraftId() {
  if (!draftSession.resumeId || !draftSession.restore(draftSession.resumeId))
    draftSession.startNew()
}
function restoreAttachmentDraft(id: string) {
  // 恢复是整单覆盖（客户/产品/明细+附件）：先确认（dev-20261016-042）
  void draftSession.confirmRestore(id)
}
function discardAttachmentDraft(id: string) {
  // 放弃草稿：删本地记录/队列 + 库中草稿行（dev-20261016-042）
  void draftSession.confirmDiscard(id)
}

// ====== 附件共享会话（dev-20261016-073：照 071 报价页闭环复制）======
// 由本页（单据容器）创建并同时喂给 Upload 与 Manager ⇒ 一份待保存队列；
// 落定（建引用）只由单据「保存」负责：待保存队列随新增/编辑的 attachments 提交（后端 dev-20261017-001）；
// saveHandler 只做「附件信息类」提交（二级类型/备注/排序），不落定（001 §7-1 方案 A）。
const attachSession = useAttachmentSession({
  sourceType: 'sales_order',
  sourceId: computed(() => form.orderId || null),
  draftId: computed(() => draftId.value || ''),
  traceId: computed(() => (form as any)?.traceId || ''),
  viewPerm: 'sales:order:view',
  editPerm: 'sales:order:edit',
  saveHandler: saveAttachInfo,
})

/** 附件信息类提交（保存处理器的唯一出口）：只认 updates；uploads 随单据保存落定，这里明确拒绝而不是静默吞掉 */
async function saveAttachInfo(changes: AttachmentChanges): Promise<AttachmentSaveResult> {
  if (changes.uploads.length) {
    throw new Error('待保存附件随订单「保存」落定，此处只提交附件信息类改动')
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

// ===== 上传归属（整单级 / 产品级，dev-20261018-019）=====
// 产品级归属键用正式 productId 优先，否则用不随渲染变化的明细行键；产品编码只作展示/透传给后端解析。
// 归属对象在本文件内构造（公共会话模块只提供契约类型），避免调用方额外依赖公共模块的导出。
function orderOwnershipOf(): AttachmentOwnership {
  return { key: 'order', kind: 'order', productId: null, productCode: null, label: '订单级' }
}
function productOwnershipOf(input: {
  rowKey: string
  productId?: number | null
  productCode?: string | null
  label?: string
}): AttachmentOwnership {
  return {
    key: input.productId != null ? `product:${input.productId}` : `row:${input.rowKey}`,
    kind: 'product',
    productId: input.productId ?? null,
    productCode: input.productCode ?? null,
    label: input.label,
  }
}

const orderRowKeys = new WeakMap<object, string>()
let orderRowKeySeq = 0
function stableRowKey(row: OrderItem): string {
  const cached = orderRowKeys.get(row)
  if (cached) return cached
  const key = row.id != null ? `item-${row.id}` : `new-${++orderRowKeySeq}`
  orderRowKeys.set(row, key)
  return key
}

// 派生视图共享原会话的所有写方法和队列，仅限定当前订单产品的显示范围。
const productAttachmentsVisible = ref(false)
const productAttachmentIdentity = ref<AttachmentOwnership>()
const productAttachmentOwnership = computed(() => productAttachmentIdentity.value || orderOwnershipOf())
function ownershipOfRow(row: OrderItem) {
  return productOwnershipOf({ rowKey: stableRowKey(row), productId: row.productId, productCode: row.productCode, label: row.productName })
}
function pendingForProduct(row: { productId?: number | null; productCode?: string | null; key: string }) {
  return attachSession.pending.value.filter(entry => entry.ownershipKey === row.key && entry.productCode?.trim() === row.productCode?.trim())
}
function refsForProduct(row: { productId?: number | null }) {
  return attachSession.refRows.value.filter(entry => row.productId != null && entry.productId === row.productId && Number(entry.sourceId) === form.orderId && entry.sourceType === 'sales_order')
}
function productAttachmentCount(row: OrderItem) {
  const ownership = ownershipOfRow(row)
  return pendingForProduct(ownership).length + refsForProduct(ownership).length
}
const productAttachmentSession = {
  ...attachSession,
  pending: computed(() => productAttachmentIdentity.value ? pendingForProduct(productAttachmentIdentity.value) : []),
  refRows: computed(() => productAttachmentIdentity.value ? refsForProduct(productAttachmentIdentity.value) : []),
  refs: computed(() => productAttachmentIdentity.value ? refsForProduct(productAttachmentIdentity.value) : []),
  legacyOnlyRows: computed(() => []), traceOnlyRows: computed(() => []), hasReadonlyDocs: computed(() => false),
}
function openProductAttachments(row: OrderItem) {
  productAttachmentIdentity.value = ownershipOfRow(row)
  productAttachmentsVisible.value = true
}
watch(() => [form.customerId, form.items.map(row => `${stableRowKey(row)}:${row.productId}:${row.productCode}`).join('|')], () => {
  productAttachmentsVisible.value = false
})

const uploadOwnershipKey = ref('order')
const uploadOwnershipOptions = computed(() => {
  const options = [
    {
      key: 'order',
      label: '整单级（不归属产品）',
      productId: null as number | null,
      productCode: null as string | null,
    },
  ]
  form.items.forEach((row) => {
    const productCode = row.productCode?.trim() || ''
    if (row.productId == null && !productCode) return
    const key = row.productId != null ? `product:${row.productId}` : `row:${stableRowKey(row)}`
    if (options.some((option) => option.key === key)) return
    const suffix = row.productName ? ` · ${row.productName}` : ''
    options.push({
      key,
      label: `产品 ${productCode || `#${row.productId}`}${suffix}`,
      productId: row.productId ?? null,
      productCode: productCode || null,
    })
  })
  return options
})
/** 上传归属在上传发起时固定：切换产品/客户不改变已上传附件的归属 */
const uploadOwnership = computed(() => {
  const selected = uploadOwnershipOptions.value.find(
    (option) => option.key === uploadOwnershipKey.value
  )
  if (!selected || selected.key === 'order') return orderOwnershipOf()
  return productOwnershipOf({
    rowKey: selected.key.startsWith('row:') ? selected.key.slice(4) : selected.key,
    productId: selected.productId,
    productCode: selected.productCode,
    label: selected.label,
  })
})
watch(uploadOwnershipOptions, (options) => {
  if (!options.some((option) => option.key === uploadOwnershipKey.value))
    uploadOwnershipKey.value = 'order'
})

/**
 * 待保存附件的归属是否仍在本单明细内（dev-20261018-019）：
 * 产品被切换/移除后，旧产品附件既不跟随新产品，也不允许后端「编码查不到→静默降级整单」丢失归属。
 */
const attachOrphanCount = computed(
  () =>
    attachSession.pending.value.filter((entry) => {
      const key = entry.ownershipKey || ''
      if (!key || key === 'order') return false
      const productId = /^product:(\d+)$/.exec(key)
      if (productId) return !form.items.some((row) => row.productId === Number(productId[1]))
      const code = entry.productCode?.trim()
      return !code || !form.items.some((row) => row.productCode?.trim() === code)
    }).length
)

/** 保存前校验用：待保存队列里尚未选二级资料类型的条数（沿用 032 口径，谓词同 useAttachmentRefs） */
const attachMissingTypeCount = computed(
  () => attachSession.pending.value.filter((e) => e.state !== 'binding' && !e.documentType).length
)

/** 待保存队列 → 订单保存请求的 attachments（attachmentId/documentType/productId/productCode） */
function attachPayload() {
  return attachSession.getChanges({ kind: 'all' }).uploads.map((u) => ({
    attachmentId: u.attachmentId,
    documentType: u.documentType ?? null,
    productId: productIdOfOwnership(u.ownershipKey),
    productCode: u.productCode ?? null,
  }))
}

/** 上传归属键 → 正式产品 id：只有 `product:<id>` 能直接给出；`row:<键>` 交给后端按明细编码解析 */
function productIdOfOwnership(key?: string | null): number | null {
  const matched = /^product:(\d+)$/.exec(key || '')
  return matched ? Number(matched[1]) : null
}

/** 附件上传/校验失败提示：上传失败组件内已提示，这里只补「选择文件校验」这类没有内建提示的错误 */
function onAttachError(payload: { action: string; message: string }) {
  if (payload.action === 'upload') return
  ElMessage.error(payload.message || '附件处理失败')
}

/**
 * 单据保存**成功后**的附件收尾（承 077 §4 的 bindPending 语义：成功清队列、失败保留）：
 * 重取引用 → 按 attachmentId 取**真实 refId** → acceptSaved；没落定的仍留在「待保存」待重试。
 * 不做任何前端绑定请求（落定已在单据保存事务内完成）。
 */
async function finalizeAttachments(submittedIds: number[]) {
  if (!submittedIds.length) return
  try {
    await attachSession.refresh()
    const sourceId = Number(form.orderId)
    const refIdByAttachment = new Map<number, number>()
    for (const row of attachSession.refs.value) {
      const attachmentId = Number((row as any).attachmentId)
      if (!attachmentId || row.deleted || Number(row.sourceId) !== sourceId) continue
      refIdByAttachment.set(attachmentId, row.id)
    }
    const acceptedUploads = submittedIds
      .filter((id) => refIdByAttachment.has(id))
      .map((id) => ({ attachmentId: id, refId: refIdByAttachment.get(id) as number }))
    if (acceptedUploads.length) attachSession.acceptSaved({ acceptedUploads })
    const remaining = submittedIds.length - acceptedUploads.length
    if (remaining)
      ElMessage.warning(`有 ${remaining} 个附件未落定，仍保留在「待保存」，请再保存一次`)
  } catch (e) {
    // 附件收尾失败不得冒泡成「整单保存失败」（订单此时已保存成功）
    console.error('附件收尾异常（订单已保存）:', e)
    ElMessage.warning('订单已保存，但附件队列未刷新，可在附件区重试')
  }
}

const createdOrderId = ref<number>()
const createdTraceId = ref<string>()

/** 客户档案 payment_method(1预付/2货到付款/3月结30天/4月结60天) → 订单同值域枚举 */
const PAYMENT_METHOD_TO_TERMS: Record<number, string> = {
  1: 'prepaid',
  2: 'cod',
  3: 'net30',
  4: 'net60',
}

function fmtDate(d: any): string {
  if (!d) return ''
  return String(d).slice(0, 10)
}

function localToday(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * 样品转量产预填（2026-09-07）：样品单头 + 明细带入标准单表单，数量/单价可改；
 * 付款条件/收货地址默认取客户档案（样品单无则客户兜底）。
 */
/**
 * 转量产预填时的价格提示（2026-09-21 dev-20260921-040）
 */
const priceAutoFillHint = ref('')
const priceAutoFillWarning = ref(false)

/**
 * 按「来源报价单」自动带出单价（仅补 0 价行，不覆盖用户已填值）。
 * 背景：SP260921001 → SO260921001 转量产时样品单价为 0，直接预填生成 0 元订单（金额/税额/应收全 0）。
 */
async function autoFillPriceFromQuotation(sample: any) {
  priceAutoFillHint.value = ''
  priceAutoFillWarning.value = false
  const hasZeroPrice = (form.items as any[]).some((it) => !Number(it.unitPrice))
  if (!hasZeroPrice) return
  if (sample?.sourceType !== 'QUOTATION' || !sample?.sourceNo) {
    priceAutoFillHint.value =
      '本样品单未关联报价单，请填写单价后再提交（金额为 0 的订单不允许发货）'
    priceAutoFillWarning.value = true
    return
  }
  try {
    const summary: any = await sampleOrderApi.getSourceQuotationSummary(sample.orderId)
    const quotationId = summary?.data?.quotationId
    if (!quotationId) throw new Error('来源报价单不存在')
    const qRes: any = await quotationApi.getItems(quotationId)
    const qItems: any[] = qRes?.data || []
    let filled = 0
    ;(form.items as any[]).forEach((it) => {
      if (Number(it.unitPrice)) return
      const hit = qItems.find(
        (q) =>
          (q.productId != null && it.productId != null && q.productId === it.productId) ||
          (!!q.productCode && !!it.productCode && q.productCode === it.productCode)
      )
      if (hit && Number(hit.unitPrice) > 0) {
        it.unitPrice = Number(hit.unitPrice)
        it.amount = Number((it.quantity ?? 0) * it.unitPrice)
        filled++
      }
    })
    calculateTotalAmount()
    if (filled > 0) {
      priceAutoFillHint.value = `单价已按来源报价单【${sample.sourceNo}】自动带出 ${filled} 行，可直接修改`
    } else {
      priceAutoFillHint.value = `来源报价单【${sample.sourceNo}】未带价，请填写单价后再提交（金额为 0 的订单不允许发货）`
      priceAutoFillWarning.value = true
    }
  } catch {
    priceAutoFillHint.value =
      '未能读取来源报价单价，请确认单价后再提交（金额为 0 的订单不允许发货）'
    priceAutoFillWarning.value = true
  }
}

async function prefillFromSample(sampleId: number) {
  try {
    const sampleRes: any = await sampleOrderApi.getInfo(sampleId)
    const s = sampleRes?.data
    if (!s) {
      ElMessage.error('加载样品单失败，请重试')
      return
    }
    const prodRes: any = await sampleOrderApi.getProducts(sampleId)
    const prods: any[] = prodRes?.data || []
    form.items = prods.map((p: any) => ({
      productId: p.productId ?? null,
      productCode: p.productCode || '',
      productName: p.productName || '',
      specification: p.specification || '',
      unit: p.unit || 'PCS',
      quantity: Number(p.quantity ?? 0),
      unitPrice: Number(p.unitPrice ?? 0),
      amount: Number((p.quantity ?? 0) * (p.unitPrice ?? 0)),
      deliveryDays: 0,
      customRequirements: '',
      customerMaterialNo: p.customerMaterialNo || '',
      lineRemark: p.lineRemark || '',
    }))
    Object.assign(form, {
      customerId: s.customerId,
      customerName: s.customerName || '',
      contactPerson: s.contactPerson || '',
      contactPhone: s.contactPhone || '',
      orderDate: fmtDate(s.orderDate) || localToday(),
      deliveryDate: fmtDate(s.deliveryDate),
      currency: s.currency || 'CNY',
      exchangeRate: Number(s.exchangeRate ?? 1),
      salesPersonId: s.salesManagerId ?? undefined,
      salesPersonName: s.salesManagerName || '',
      remark: '',
    })
    if (s.deliveryAddress) {
      form.shippingAddress = s.deliveryAddress
    }
    // 价格来源（2026-09-21 dev-20260921-040）：样品单本身常无单价（打样不计价），直接预填会生成 0 元订单。
    // 这里自动按「来源报价单」带出同产品单价（可改），不能再让财务/业务手工逐行回填；
    // 报价单也无价时给出明确提示（金额为 0 的订单不允许发货）。
    await autoFillPriceFromQuotation(s)
    // 客户兜底：付款条件（客户 payment_method 映射）+ 收货地址（样品单未带时）
    try {
      const cusRes: any = await customerApi.getCustomer(s.customerId)
      const c = cusRes?.data
      if (c) {
        if (c.paymentMethod != null && PAYMENT_METHOD_TO_TERMS[c.paymentMethod]) {
          form.paymentTerms = PAYMENT_METHOD_TO_TERMS[c.paymentMethod]
        }
        if (!form.shippingAddress) {
          const addr: Record<string, string> = {
            country: c.country || '',
            province: c.province || '',
            city: c.city || '',
            street: c.address || '',
            zipCode: c.postalCode || '',
          }
          if (Object.values(addr).some((v) => v)) {
            form.shippingAddress = serializeAddress(addr as any)
          }
        }
        if (!form.contactPerson) {
          form.contactPerson = c.contactPerson || ''
          form.contactPhone = c.contactPhone || ''
        }
      }
    } catch {
      // 客户信息拉取失败不阻断预填
    }
    calculateTotalAmount()
    ElMessage.success('已带入样品单数据，请确认数量/单价后提交')
  } catch (e) {
    console.error('样品转量产预填失败:', e)
    ElMessage.error('样品数据预填失败，请重试或手动填写')
  }
}

// ===== 收货地址选择：客户地址簿 + 公司地址（dev-20261008-029）=====
const { company } = useCompanyConfig()
const customerAddresses = ref<SalesCustomerAddress[]>([])
const addressPick = ref('')
const addressDialogVisible = ref(false)
const COMPANY_KEY = 'company'

const toInternational = (a: SalesCustomerAddress) => ({
  country: a.country || '',
  province: a.province || '',
  city: a.city || '',
  street: a.address || '',
  zipCode: a.postalCode || '',
})

const addressOptions = computed(() => {
  const opts = customerAddresses.value.map((a) => ({
    key: `addr-${a.addressId}`,
    label:
      `${a.label || '地址'}｜` +
      [a.country, a.province, a.city, a.address].filter(Boolean).join(' ') +
      (a.isDefault === 1 ? '（默认）' : ''),
  }))
  if (company.address) {
    opts.push({ key: COMPANY_KEY, label: `公司地址｜${company.address}` })
  }
  return opts
})

const applyAddress = (a: SalesCustomerAddress) => {
  form.shippingAddress = serializeAddress(toInternational(a))
  addressPick.value = `addr-${a.addressId}`
}

const applyCompany = () => {
  form.shippingAddress = serializeAddress({
    country: '',
    province: '',
    city: '',
    street: company.address,
    zipCode: '',
  })
  addressPick.value = COMPANY_KEY
}

const onAddressPick = (key: string) => {
  if (key === COMPANY_KEY) {
    applyCompany()
    return
  }
  const id = Number(String(key).replace('addr-', ''))
  const hit = customerAddresses.value.find((a) => a.addressId === id)
  if (hit) applyAddress(hit)
}

const onAddressPickedFromDialog = (a: SalesCustomerAddress) => {
  addressDialogVisible.value = false
  if (a?.addressId) {
    applyAddress(a)
  }
}

const loadCustomerAddresses = async () => {
  if (!form.customerId) {
    customerAddresses.value = []
    return
  }
  try {
    const res = await customerApi.getCustomerAddresses(form.customerId)
    customerAddresses.value = res?.data || []
  } catch (e) {
    console.error('加载客户收货地址失败', e)
    customerAddresses.value = []
  }
}

// 表单尚无地址时，优先带出客户默认地址
const preferDefaultAddress = () => {
  if (form.shippingAddress) return
  const def = customerAddresses.value.find((a) => a.isDefault === 1) || customerAddresses.value[0]
  if (def) applyAddress(def)
}

watch(
  () => form.customerId,
  async () => {
    await loadCustomerAddresses()
    preferDefaultAddress()
  }
)

// 自提且无地址时，默认带出公司地址
watch(
  () => form.shippingMethod,
  (m) => {
    if (m === 'self_pickup' && !form.shippingAddress && company.address) {
      applyCompany()
    }
  }
)

// 缓存页面离开时已关闭实例；再次进入新增页必须创建新实例。
onActivated(() => {
  if (!props.isEdit && !draftId.value && !form.orderId) {
    resetForm()
    draftSession.startNew()
  }
})

// 初始化
onMounted(() => {
  resetForm()
  if (!props.isEdit) ensureDraftId() // 新页面实例或刷新恢复，互不混用
  loadSalesPersons()
  // 2026-09-07：不再预取单号（取消/失败会烧号导致跳号），改由后端提交落库时生成
  if (props.sampleOrderId && !draftSession.resumeId) {
    prefillFromSample(props.sampleOrderId)
  }
  loadCustomerAddresses().then(() => preferDefaultAddress())
})

// 编辑模式：监听 orderId（父组件的 orderId 可能在 onMounted 之后才赋值）
watch(
  () => props.orderId,
  (newId) => {
    if (props.isEdit && newId) {
      loadOrderData(newId)
    }
  },
  { immediate: true }
)

// 提交表单（重写：保存订单后再上传附件）
const submitForm = async (): Promise<boolean> => {
  if (props.isEdit) {
    // 编辑模式：待保存附件随「单据保存」一并提交，由后端在保存事务内建引用（dev-20261017-001）
    if (attachOrphanCount.value > 0) {
      ElMessage.warning(
        `有 ${attachOrphanCount.value} 个待保存附件归属的产品已不在本单明细，请移除后重试`
      )
      return false
    }
    if (attachMissingTypeCount.value > 0) {
      ElMessage.warning(`有 ${attachMissingTypeCount.value} 个附件未选择资料类型，请先选择后再保存`)
      return false
    }
    const attachments = attachPayload()
    const submittedIds = attachments.map((a) => a.attachmentId)
    const success = await submitOrderForm(attachments)
    if (success) {
      await nextTick()
      await finalizeAttachments(submittedIds)
      emit('success')
    }
    return success
  }

  // 新增模式：手动提交，获取新订单ID后上传附件
  if (!orderFormRef.value) return false
  if (createdOrderId.value) {
    emit('success')
    return true
  }

  if (!(await validateForm())) return false

  // 资料类型必选（dev-20261015-032）：保存前拦住缺二级类型的待保存附件，避免「保存成功但附件没落定」
  if (attachOrphanCount.value > 0) {
    ElMessage.warning(
      `有 ${attachOrphanCount.value} 个待保存附件归属的产品已不在本单明细，请移除后重试`
    )
    return false
  }
  if (attachMissingTypeCount.value > 0) {
    ElMessage.warning(`有 ${attachMissingTypeCount.value} 个附件未选择资料类型，请先选择后再保存`)
    return false
  }

  // 构建提交数据（随单附件：待保存队列 → 请求体 attachments，落定由后端在保存事务内完成）
  const attachments = attachPayload()
  const submittedIds = attachments.map((a) => a.attachmentId)
  const submitData = {
    ...form,
    deliveryAddress: form.shippingAddress || '',
    shippingFee: form.amountBreakdownConfirmed ? form.shippingFee : null,
    salesManagerId: form.salesPersonId!,
    salesManagerName: form.salesPersonName,
    attachments,
  }

  try {
    // 1. 保存订单（转量产模式走样品转量产接口：建标准单 + 回写样品单状态，事务内完成）
    const isSampleConvert = !!props.sampleOrderId
    // 两个接口响应均已解包为 { code, msg, data }，类型统一按 any 处理
    const orderResponse: any = isSampleConvert
      ? await sampleOrderApi.convertSample(props.sampleOrderId as number, submitData as any)
      : await orderApi.addOrder(submitData as any)
    if (orderResponse.code !== 200) {
      ElMessage.error(isSampleConvert ? '转量产失败' : '新增订单失败')
      return false
    }
    const data = orderResponse.data!
    const newOrderId = data.orderId
    const newTraceId = data.traceId
    createdOrderId.value = newOrderId
    createdTraceId.value = newTraceId
    // 新建单的链路号只有保存后才存在：回填后同链路只读段/附件来源按本单 trace 归属（dev-20261018-019）
    form.traceId = newTraceId

    // 2. 附件收尾（dev-20261016-073）：落定已在保存事务内完成，这里按真实 refId 清「待保存」队列
    form.orderId = newOrderId // 先回写 sourceId，finalizeAttachments 才能按真实 refId 回填
    await nextTick()
    await finalizeAttachments(submittedIds)
    clearDraftId() // 保存成功：清空草稿标识
    ElMessage.success(isSampleConvert ? '转量产成功，标准订单已生成' : '新增成功')
    emit('success')
    return true
  } catch (error) {
    console.error('新增订单失败:', error)
    ElMessage.error('新增订单失败')
    return false
  }
}

// 暴露给父组件的方法和属性
function resetOrderForm() {
  draftSession.close()
  createdOrderId.value = undefined
  createdTraceId.value = undefined
  resetForm()
  if (!props.isEdit) draftSession.startNew()
}
defineExpose({
  orderFormRef,
  form,
  submitting,
  resetForm: resetOrderForm,
  generateOrderNo,
  loadOrderData,
  submitForm,
})
</script>

<style scoped>
/* 金额汇总中的数字输入与只读金额统一左对齐。 */
.amount-summary :deep(.el-input-number .el-input__inner) {
  text-align: left;
}

/* 无边框输入框样式 */
.borderless-input :deep(.el-input__wrapper) {
  box-shadow: none;
  background-color: transparent;
  padding: 0;
}

.borderless-input :deep(.el-input__wrapper:hover) {
  box-shadow: none;
}

.borderless-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: none;
}

/* 无边框数字输入框 */
.borderless-input-number :deep(.el-input__wrapper) {
  box-shadow: none;
  background-color: transparent;
}

.borderless-input-number :deep(.el-input-number__decrease),
.borderless-input-number :deep(.el-input-number__increase) {
  background: transparent;
  border: none;
}

/* 无边框文本域 */
.borderless-textarea :deep(.el-textarea__inner) {
  box-shadow: none;
  background-color: transparent;
  border: none;
  padding: 4px 0;
  resize: none;
}

.borderless-textarea :deep(.el-textarea__inner:hover) {
  border: none;
  box-shadow: none;
}

.borderless-textarea :deep(.el-textarea__inner:focus) {
  border: none;
  box-shadow: none;
}

/* 附件区（Upload / Manager）块间距 */
.attach-block {
  margin-bottom: 8px;
}

/* 上传归属选择（整单 / 产品级） */
.attach-ownership {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.attach-ownership-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.attach-ownership-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 汇率提示文字 */
.rate-hint {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
