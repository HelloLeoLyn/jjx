<template>
  <el-form ref="orderFormRef" :model="form" :rules="rules" label-width="120px">
    <!-- 订单基本信息 -->
    <el-divider content-position="left">订单基本信息</el-divider>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="订单号" prop="orderNo">
          <el-input
            v-model="form.orderNo"
            placeholder="系统自动生成"
            maxlength="50"
            :readonly="true"
          />
        </el-form-item>
      </el-col>
      <el-col :span="10">
        <el-form-item label="客户" prop="customerId">
          <CustomerSelector
            v-model="form.customerId"
            value-type="customerId"
            placeholder="请选择客户"
            @change="customerChanged"
          />
        </el-form-item>
      </el-col>
      <el-col :span="2"
        ><el-button @click="goToCustomerAdd()" type="primary">新增客户</el-button></el-col
      >
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
          <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%" @change="handleCurrencyChange">
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

    <el-row :gutter="20">
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
    <el-divider content-position="left">收货信息</el-divider>
    <el-row>
      <el-col :span="24">
        <InternationalAddressEditor v-model="form.shippingAddress" prop-path="address" />
      </el-col>
    </el-row>

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
            placeholder="产品名称"
            readonly
            class="borderless-input"
          />
        </template>
      </el-table-column>
      <el-table-column label="规格型号" prop="specification" width="120">
        <template #default="scope">
          <el-input
            v-model="scope.row.specification"
            placeholder="规格型号"
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
      <el-table-column label="操作" width="80" align="center">
        <template #default="scope">
          <el-button link type="danger" icon="Delete" @click="removeItem(scope.$index)"></el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-row>
      <el-col :span="24" style="text-align: right">
        <el-button type="primary" icon="Plus" @click="addItem()">添加明细</el-button>
      </el-col>
    </el-row>

    <!-- 附件上传（新建和编辑都显示） -->
    <el-divider content-position="left">订单附件</el-divider>
    <div class="attachment-section">
      <AttachmentPanel
        v-if="form.orderId"
        biz-type="sales_order"
        :biz-id="form.orderId"
        style="margin-bottom: 10px"
      />
      <AttachmentUploader
        ref="uploaderRef"
        biz-type="sales_order"
        :biz-id="form.orderId"
        :trace-id="(form as any)?.traceId"
        :accept="['.pdf','.doc','.docx','.xls','.xlsx','.jpg','.jpeg','.png']"
        button-text="上传附件"
        tip="支持 .pdf .doc .xls .jpg .png，单个文件不超过10MB；新建订单时附件将在保存后自动上传"
      />
    </div>

    <!-- 金额汇总 -->
    <el-divider content-position="left">金额汇总</el-divider>
    <el-row :gutter="20">
      <el-col :span="8">
        <el-form-item label="小计金额">
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
            @change="calculateTotalAmount"
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
    <el-row :gutter="20">
      <el-col :span="8">
        <el-form-item label="运费">
          <el-input-number
            v-model="form.shippingFee"
            :min="0"
            :precision="2"
            @change="calculateTotalAmount"
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
            @change="calculateTotalAmount"
            style="width: 100%"
          >
            <template #append>元</template>
          </el-input-number>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="总金额">
          <el-input v-model="form.totalAmount" readonly style="width: 100%">
            <template #append>元</template>
          </el-input>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="20" v-if="form.currency && form.currency !== 'CNY'">
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
    <!-- 新增客户弹窗 -->
    <CustomerFormDialog
      v-model:visible="customerDialogVisible"
      title="新增客户"
      :form-data="customerFormData"
      @success="handleCustomerSuccess"
      @cancel="customerDialogVisible = false"
    />
  </el-form>
</template>

<script setup lang="ts">
import { onMounted, ref, reactive, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import AttachmentUploader from '@/components/AttachmentUploader/index.vue'
import { orderApi } from '@/api/sales/order'
import { useOrderForm } from '../composables/useOrderForm'
import InternationalAddressEditor from '@/components/InternationalAddressEditor.vue'
import CustomerFormDialog from '../../customer/components/CustomerFormDialog.vue'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import type { CustomerFormData } from '@/types/sales/customer'

interface Props {
  isEdit?: boolean
  orderId?: number
  initialData?: Record<string, any>
}

const props = withDefaults(defineProps<Props>(), {
  isEdit: false,
  orderId: undefined,
  initialData: () => ({}),
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
  calculateItemAmount,
  calculateTotalAmount,
  addItem,
  removeItem,
  resetForm,
  generateOrderNo,
  loadOrderData,
  submitForm: submitOrderForm,
  formatCurrency,
} = useOrderForm({ isEdit: props.isEdit, initialData: props.initialData })

// ===== 汇率自动填充 =====
const exchangeRateLoading = ref(false)

// 外币总金额显示（订单选外币时，将人民币总金额折算成外币）
const foreignCurrencyDisplay = computed(() => {
  if (!form.exchangeRate || !form.totalAmount || form.currency === 'CNY') return 0
  // 汇率 = 1外币 = N人民币，所以外币金额 = 人民币总金额 / 汇率
  const foreignAmount = form.totalAmount / form.exchangeRate
  return foreignAmount.toFixed(2)
})

// 汇率提示文字
const exchangeRateHint = computed(() => {
  if (!form.currency || form.currency === 'CNY') return ''
  return `1 ${form.currency} = ${form.exchangeRate} CNY`
})

// 币种变化时自动获取汇率
const handleCurrencyChange = async (val: string) => {
  if (val === 'CNY') {
    form.exchangeRate = 1
    return
  }
  exchangeRateLoading.value = true
  try {
    const res = await orderApi.getExchangeRate(val)
    if (res?.code === 200 && res.data) {
      form.exchangeRate = res.data
    }
  } catch (e) {
    console.error('获取汇率失败:', e)
  } finally {
    exchangeRateLoading.value = false
  }
}

// ===== 附件上传（DEV-733 统一组件） =====
const uploaderRef = ref<InstanceType<typeof AttachmentUploader>>()

// 初始化
onMounted(() => {
  resetForm()
  loadSalesPersons()
  if (!props.isEdit) {
    generateOrderNo()
  }
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
    // 编辑模式：走原有逻辑（附件已直接上传）
    const success = await submitOrderForm()
    if (success) {
      emit('success')
    }
    return success
  }

  // 新增模式：手动提交，获取新订单ID后上传附件
  if (!orderFormRef.value) return false

  // 先验证明细数量（独立于 element-plus 表单校验）
  if (form.items.length === 0) {
    ElMessage.warning('请至少添加一条订单明细')
    return false
  }

  // 验证表单字段（使用 Promise 方式，校验失败会飘红）
  try {
    await orderFormRef.value.validate()
  } catch {
    // Element Plus 已自动将错误字段飘红
    ElMessage.warning('请完善表单信息')
    return false
  }

  // 构建提交数据
  const submitData = {
    ...form,
    salesManagerId: form.salesPersonId!,
    salesManagerName: form.salesPersonName,
  }

  try {
    // 1. 保存订单，获取新订单ID
    const orderResponse = await orderApi.addOrder(submitData as any)
    if (orderResponse.code !== 200) {
      ElMessage.error('新增订单失败')
      return false
    }
    const newOrderId = orderResponse.data!

    // 2. 上传待处理的附件
    await uploaderRef.value?.flushPending(newOrderId)

    ElMessage.success('新增成功')
    emit('success')
    return true
  } catch (error) {
    console.error('新增订单失败:', error)
    ElMessage.error('新增订单失败')
    return false
  }
}

// 暴露给父组件的方法和属性
defineExpose({
  orderFormRef,
  form,
  submitting,
  resetForm,
  generateOrderNo,
  loadOrderData,
  submitForm,
})
</script>

<style scoped>
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

/* 汇率提示文字 */
.rate-hint {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  line-height: 1.4;
}
</style>
