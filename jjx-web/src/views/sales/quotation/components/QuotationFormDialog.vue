<!-- views/sales/quotation/components/QuotationFormDialog.vue -->
<template>
  <el-dialog :title="title" v-model="visible" width="1300px" append-to-body @close="handleClose">
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

      <!-- 编码生成器 -->
      <template v-if="formData.quotationType === 2">
        <ProductCodeGenerator
          ref="qCodeGenRef"
          :customer-short="qShortName"
          v-model:state="qCodeState"
          :emit-params="true"
          v-model:params="qCodeParams"
          @change="onQCodeChange"
        />
      </template>

      <!-- 报价明细表格 -->
      <el-divider content-position="left">报价明细</el-divider>
      <el-table :data="formData.items" border style="width: 100%; margin-bottom: 10px">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="产品编码" prop="productCode" width="140">
          <template #default="scope">
            <el-select
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
        <el-table-column label="金额" prop="amount" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="80" align="center">
          <template #default="scope">
            <el-button
              v-if="formData.quotationType === 1"
              link
              type="danger"
              icon="Delete"
              @click="removeItem(scope.$index)"
            />
          </template>
        </el-table-column>
      </el-table>

      <el-row>
        <el-col :span="24" style="text-align: right">
          <el-button
            v-if="formData.quotationType === 1 || formData.quotationType === 2"
            type="primary"
            icon="Plus"
            :disabled="formData.quotationType === 2 && formData.items.length >= 1"
            @click="addItem"
            >添加明细</el-button
          >
          <span v-if="formData.quotationType === 2" class="sample-limit-tip">样品单仅支持一条明细</span>
        </el-col>
      </el-row>

      <!-- 金额汇总 -->
      <el-divider content-position="left">金额汇总</el-divider>
      <el-row>
        <el-col :span="8">
          <el-form-item label="小计金额">
            <el-input v-model="formData.subtotalAmount" readonly style="width: 100%">
              <template #append>元</template>
            </el-input>
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
            >
              <template #append>%</template>
            </el-input-number>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="税额">
            <el-input v-model="formData.taxAmount" readonly style="width: 100%">
              <template #append>元</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row>
        <el-col :span="8">
          <el-form-item label="折扣金额">
            <el-input-number
              v-model="formData.discountAmount"
              :min="0"
              :precision="2"
              controls-position="right"
              @change="calculateTotalAmount"
              style="width: 100%"
            >
              <template #append>元</template>
            </el-input-number>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="总金额">
            <el-input v-model="formData.totalAmount" readonly style="width: 100%">
              <template #append>元</template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="最终金额">
            <el-input v-model="formData.finalAmount" readonly style="width: 100%">
              <template #append>元</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>
      <el-row v-if="formData.currency && formData.currency !== 'CNY' && formData.exchangeRate > 0">
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
        <el-button type="primary" @click="handleSubmit">确 定</el-button>
        <el-button @click="handleClose">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { formatCurrency } from '@/utils/format'
import { quotationApi } from '@/api/sales/quotation'
import { customerApi } from '@/api/sales/customer'
import { listProduct } from '@/api/product'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'

const props = defineProps<{
  modelValue: boolean
  title: string
  formData: any
  currencyOptions: Array<{ value: string; label: string }>
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

const qCodeGenRef = ref()
const qShortName = ref('')
const qCodeState = ref<ProductCodeState>({
  serialNo: '',
  panelType: '',
  panelFeature: '',
  circuitType: '',
  circuitFeature: '',
})
const qCodeParams = ref<ProductCodeResult | null>(null)

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
    visible.value = val
  }
)

watch(visible, (val) => {
  emit('update:modelValue', val)
})

const onCustomerChange = (val: any, customer: any) => {
  if (customer) {
    props.formData.customerName = customer.customerName
    qShortName.value = (customer.customerShortName || '').substring(0, 3)
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

const handleCurrencyChange = async (val: string) => {
  if (val === 'CNY') {
    props.formData.exchangeRate = 1
    return
  }
  try {
    const res = await quotationApi.getExchangeRate(val)
    if (res?.code === 200 && res.data) {
      props.formData.exchangeRate = res.data
    }
  } catch (e) {
    console.error('获取汇率失败:', e)
  }
}

const onQCodeChange = (data: string | ProductCodeResult) => {
  const code = typeof data === 'string' ? data : data.productCode
  const row = props.formData.items[0]
  if (!row || !code) return
  row.productCode = code
  row.productName = code
  ElMessage.success('编码与名称已填入明细')
}

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
  if (props.formData.quotationType === 2 && props.formData.items.length >= 1) {
    ElMessage.warning('样品单仅支持一条明细')
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
  form.totalAmount = form.subtotalAmount + form.taxAmount
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
          ElMessage.warning('请填写完整的产品信息')
          return
        }
        if (item.quantity <= 0) {
          ElMessage.warning('数量必须大于0')
          return
        }
        if (item.unitPrice < 0) {
          ElMessage.warning('单价不能为负数')
          return
        }
      }
      if (props.formData.quotationType === 2 && qCodeParams.value && props.formData.items.length) {
        const p = qCodeParams.value
        Object.assign(props.formData.items[0], {
          serialNo: p.serialNo,
          panelType: p.panelType,
          panelFeature: p.panelFeature,
          circuitType: p.circuitType,
          circuitFeature: p.circuitFeature,
        })
      }
      emit('submit')
    }
  })
}

const handleClose = () => {
  visible.value = false
  emit('cancel')
}

defineExpose({
  formRef,
  calculateTotalAmount,
})
</script>

<style scoped>
.rate-hint {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
.dialog-footer {
  text-align: right;
}
.sample-limit-tip {
  color: #909399;
  font-size: 12px;
  margin-left: 8px;
}
</style>
