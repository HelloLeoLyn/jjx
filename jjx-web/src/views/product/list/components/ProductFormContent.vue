<template>
  <el-form ref="formRef" :model="formData" :rules="rules" label-width="120px" class="product-form">
    <!-- ==================== 基本信息 ==================== -->
    <el-divider content-position="left">基本信息</el-divider>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="产品分类" prop="categoryId" required>
          <el-cascader
            v-model="formData.categoryId"
            :options="categoryTree"
            :props="cascaderProps"
            placeholder="请选择产品分类"
            clearable
            filterable
            style="width: 100%"
            @change="handleCategoryChange(formData.categoryId)"
          >
            <template #default="{ data }">
              <span>{{ data.categoryName }}</span>
              <span v-if="data.categoryCode" class="option-code"> ({{ data.categoryCode }}) </span>
            </template>
          </el-cascader>
          <div class="form-tip">支持分类编码/名称搜索，选择后自动生成产品编码</div>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="产品类型" prop="productType">
          <el-select
            v-model="formData.productType"
            placeholder="请选择产品类型"
            style="width: 100%"
          >
            <el-option
              v-for="item in ProductTypeEnum.items"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>


    <!-- 编码构成要素（客户选择 + 公共编码生成组件 2026-08-12） -->
    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="客户" prop="codeCustomerId" required>
          <CustomerSelector
            :model-value="formData.codeCustomerId ?? null"
            value-type="customerId"
            placeholder="请选择客户"
            @update:model-value="
              (val: any) => {
                formData.codeCustomerId = val ?? undefined
              }
            "
            @change="handleCustomerChange"
          />
        </el-form-item>
      </el-col>
    </el-row>
    <ProductCodeGenerator
      ref="codeGenRef"
      :customer-short="selectedCustomerShortName"
      :fetch-serial="fetchProductSerial"
      v-model:state="codeState"
      :emit-params="true"
      v-model:params="codeParams"
      hide-short-name
      @change="onCodeChange"
    />

    <!-- 产品编码（组件生成后自动填入，只读展示） -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-form-item label="产品编码" prop="productCode" required>
          <el-input
            v-model="formData.productCode"
            placeholder="选择客户并点击生成编码自动填入"
            readonly
          />
          <div class="form-tip" :class="{ 'is-error': codeError }">
            {{ codeError || '编码格式：客户简称(1-3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)' }}
          </div>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="产品名称" prop="productName" required>
          <el-input v-model="formData.productName" placeholder="请输入产品名称" />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="单位" prop="unit">
          <el-select
            v-model="formData.unit"
            placeholder="请选择单位"
            filterable
            allow-create
            style="width: 100%"
          >
            <el-option
              v-for="item in unitList"
              :key="item.code"
              :label="`${item.name} (${item.code})`"
              :value="item.code"
            />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>

    <!-- ==================== 价格与交期 ==================== -->
    <el-divider content-position="left">价格与交期</el-divider>

    <el-row :gutter="20">
      <el-col :span="8">
        <el-form-item label="基础售价">
          <el-input-number
            v-model="formData.basePrice"
            :min="0"
            :precision="2"
            placeholder="基础售价"
            style="width: 100%"
          >
            <template #prefix>¥</template>
          </el-input-number>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="标准成本">
          <el-input-number
            v-model="formData.costPrice"
            :min="0"
            :precision="2"
            placeholder="标准成本"
            style="width: 100%"
          >
            <template #prefix>¥</template>
          </el-input-number>
        </el-form-item>
      </el-col>
      <el-col :span="8">
        <el-form-item label="毛利率">
          <span class="margin-info">
            {{ calcMargin }}%
            <el-tag :type="marginType" size="small">{{ marginLevel }}</el-tag>
          </span>
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="最小起订量">
          <el-input-number
            v-model="formData.minOrderQty"
            :min="1"
            placeholder="最小起订量"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="标准交期(天)">
          <el-input-number
            v-model="formData.leadTime"
            :min="1"
            placeholder="标准交期"
            style="width: 100%"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="24">
        <el-form-item label="备注" prop="remark">
          <el-input v-model="formData.remark" placeholder="请输入备注" type="textarea" />
        </el-form-item>
      </el-col>
    </el-row>
  </el-form>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { productApi } from '@/api/product'
import { ProductTypeEnum } from '@/enums'
import type {
  ProductVo,
  ProductFormData,
  CategoryTree,
  RoutingSimpleVo,
  UnitVo,
} from '@/types/product'
import type { BomSimpleVo } from '@/types/product/bom'
import type { CustomerSearchVO } from '@/types/sales/customer'

const props = defineProps<{
  productId?: number
}>()

const emit = defineEmits<{
  success: []
  cancel: []
}>()

const isEdit = computed(() => !!props.productId)
const formRef = ref()
const submitting = ref(false)
const generatingCode = ref(false)
const codeError = ref('')

// 辅助数据
const categoryTree = ref<CategoryTree[]>([])
const bomList = ref<BomSimpleVo[]>([])
const routingList = ref<RoutingSimpleVo[]>([])
const unitList = ref<UnitVo[]>([])

// 客户数据
const selectedCustomerShortName = ref('')
const selectedCustomer = ref<CustomerSearchVO | null>(null)

// 选中的对象
const selectedBom = ref<BomSimpleVo | null>(null)
const selectedRouting = ref<RoutingSimpleVo | null>(null)

// 编码结构要素已收敛到公共组件 codeState（2026-08-12）

// 规格参数
const specData = reactive({
  length: 0,
  width: 0,
  height: 0,
  unit: 'mm',
  keyCount: 0,
  hasBacklight: false,
  ipGrade: '',
})

// 表单数据
const formData = reactive<Partial<ProductFormData>>({
  productCode: '',
  productName: '',
  categoryId: undefined,
  productType: undefined,
  productStatus: 1,
  unit: 'PCS',
  basePrice: 0,
  costPrice: 0,
  minOrderQty: 1,
  leadTime: 15,
  remark: '',
  currentBomId: undefined,
  currentRouteId: undefined,
  // 编码结构字段
  codeCustomerId: undefined,
  codeSerialNo: '',
})

// 客户简称显示
// 编码生成器（公共组件 2026-08-12）
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'
const codeGenRef = ref<InstanceType<typeof ProductCodeGenerator>>()
const codeState = ref<ProductCodeState>({ serialNo: '', panelType: '', panelFeature: '', circuitType: '', circuitFeature: '' })
const codeParams = ref<ProductCodeResult | null>(null)

// 编码生成回调：同步流水号/产品编码/错误提示
function onCodeChange(data: string | ProductCodeResult) {
  const result = typeof data === 'string' ? null : data
  formData.codeSerialNo = codeState.value.serialNo
  if (result) {
    formData.productCode = result.productCode
    codeError.value = ''
  } else {
    formData.productCode = ''
    codeError.value = '请完整选择面板结构/特征、线路类型/特征'
  }
}

// 级联选择器配置
const cascaderProps = {
  value: 'categoryId',
  label: 'categoryName',
  children: 'children',
  emitPath: false,
  checkStrictly: true,
}

// 表单验证规则
const rules = {
  categoryId: [{ required: true, message: '请选择产品分类', trigger: 'change' }],
  productCode: [
    { required: true, message: '请生成产品编码', trigger: 'blur' },
    // 2026-08-10 DEV-772 补漏：客户简称1-3位 → 编码9-10位（原硬编码10位导致2位简称保存失败）
    { pattern: /^[A-Za-z0-9]{9,10}$/, message: '编码格式不正确，请重新生成', trigger: 'blur' },
  ],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  codeCustomerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  codeSerialNo: [{ required: true, message: '请选择客户后自动生成流水号', trigger: 'change' }],
}

// 计算毛利率
const calcMargin = computed(() => {
  if (!formData.basePrice || formData.basePrice === 0) return 0
  const margin = ((formData.basePrice - (formData.costPrice || 0)) / formData.basePrice) * 100
  return margin.toFixed(1)
})

const marginType = computed(() => {
  const margin = Number(calcMargin.value)
  if (margin >= 30) return 'success'
  if (margin >= 15) return 'warning'
  return 'danger'
})

const marginLevel = computed(() => {
  const margin = Number(calcMargin.value)
  if (margin >= 30) return '高毛利'
  if (margin >= 15) return '中毛利'
  return '低毛利'
})

// 客户选择变化 - 生成流水号（公共组件自动取号，2026-08-12）
const handleCustomerChange = async (
  value: number | string | null,
  customer: CustomerSearchVO | null
) => {
  if (!value || !customer) {
    formData.codeSerialNo = ''
    formData.customerId = undefined
    formData.customerName = ''
    selectedCustomer.value = null
    selectedCustomerShortName.value = ''
    codeState.value = { serialNo: '', panelType: '', panelFeature: '', circuitType: '', circuitFeature: '' }
    return
  }

  // 保存选中的客户对象（2026-08-10：同时写入 customerId/customerName 落库关联）
  selectedCustomer.value = customer
  selectedCustomerShortName.value = customer.customerShortName || ''
  formData.customerId = customer.customerId
  formData.customerName = customer.customerName

  // 取流水号 + 拼码（组件 fetchSerial 按客户ID调用后端）
  codeGenRef.value?.generate()
}

// 产品表单专用取流水号：按客户ID（保持原有接口）
async function fetchProductSerial(short: string) {
  if (!formData.codeCustomerId) return '001'
  try {
    const res: any = await productApi.generateSerialNo(formData.codeCustomerId)
    return res?.data || '001'
  } catch {
    return '001'
  }
}

// 获取分类树
const loadCategoryTree = async () => {
  const res = await productApi.category.getProductCategoryTree()
  categoryTree.value = res.data || []
}

// 从产品编码反解编码构成要素（2026-08-10：编辑回显面板/线路/流水号）
// 编码 = 客户简称(1-3) + 流水号(3) + 面板结构(1) + 面板特征(1) + 线路类型(1) + 线路特征(1)，总长9-10位
function parseCodeElements(code?: string | null) {
  codeState.value = { serialNo: '', panelType: '', panelFeature: '', circuitType: '', circuitFeature: '' }
  formData.codeSerialNo = ''
  if (!code) return
  const c = code.trim()
  if (c.length < 7 || c.length > 10) return // 长度不符，无法反解
  // 面板结构合法值：M/S/P；面板特征：E/W/H/O；线路类型：O/M/P；线路特征：O/L/C/H
  const panelType = c.charAt(c.length - 4)
  const panelFeature = c.charAt(c.length - 3)
  const circuitType = c.charAt(c.length - 2)
  const circuitFeature = c.charAt(c.length - 1)
  codeState.value = {
    serialNo: '',
    panelType: 'MSP'.includes(panelType) ? panelType : '',
    panelFeature: 'EWHO'.includes(panelFeature) ? panelFeature : '',
    circuitType: 'OMP'.includes(circuitType) ? circuitType : '',
    circuitFeature: 'OLCH'.includes(circuitFeature) ? circuitFeature : '',
  }
  // 流水号：倒数第7~5位（3位数字）
  const serial = c.slice(-7, -4)
  if (/^\d{3}$/.test(serial)) {
    codeState.value.serialNo = serial
    formData.codeSerialNo = serial
  }
}

// 加载产品详情
const loadProductDetail = async () => {
  if (!props.productId) return
  const res = await productApi.info(props.productId)
  const data = res.data
  Object.assign(formData, data)

  // 2026-08-10：编辑回显客户（CustomerSelector 用 codeCustomerId 显示，自动加载客户名）
  if (data.customerId) {
    formData.codeCustomerId = data.customerId
  }

  // 2026-08-10：从产品编码反解编码构成要素（面板/线路/流水号），编码=简称(1-3)+流水(3)+面板结构(1)+面板特征(1)+线路类型(1)+线路特征(1)
  parseCodeElements(data.productCode)

  // 解析规格参数
  if (data.specJson) {
    try {
      const spec = JSON.parse(data.specJson)
      Object.assign(specData, spec)
    } catch (e) {}
  }

  // 设置选中项
  if (formData.currentBomId) {
    selectedBom.value = bomList.value.find((b) => b.bomId === formData.currentBomId) || null
  }
  if (formData.currentRouteId) {
    selectedRouting.value =
      routingList.value.find((r) => r.routingId === formData.currentRouteId) || null
  }
}

// 分类变化时自动生成编码
const handleCategoryChange = (categoryId?: number) => {
  // 分类变化不再自动生成编码，编码由结构要素决定
}


// 提交表单
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    // 验证失败，抛出异常让父组件知道
    throw new Error('表单验证失败')
  }

  if (codeError.value) {
    ElMessage.error('请修正产品编码后重试')
    throw new Error('产品编码错误')
  }

  // 组装规格参数JSON
  const specJson = JSON.stringify(specData)
  const submitData = { ...formData, specJson }

  submitting.value = true
  try {
    if (isEdit.value) {
      await productApi.edit(submitData as ProductFormData)
      ElMessage.success('更新成功')
    } else {
      await productApi.add(submitData as ProductFormData)
      ElMessage.success('创建成功')
    }
    emit('success')
  } finally {
    submitting.value = false
  }
}

// 重置表单
const resetForm = () => {
  Object.assign(formData, {
    productCode: '',
    productName: '',
    categoryId: undefined,
    productType: undefined,
    productStatus: 1,
    unit: 'PCS',
    basePrice: 0,
    costPrice: 0,
    minOrderQty: 1,
    leadTime: 15,
    remark: '',
    currentBomId: undefined,
    currentRouteId: undefined,
    codeCustomerId: undefined,
    codeSerialNo: '',
  })
  codeState.value = { serialNo: '', panelType: '', panelFeature: '', circuitType: '', circuitFeature: '' }
  codeParams.value = null
  selectedCustomer.value = null
  selectedCustomerShortName.value = ''
  Object.assign(specData, {
    length: 0,
    width: 0,
    height: 0,
    unit: 'mm',
    keyCount: 0,
    hasBacklight: false,
    ipGrade: '',
  })
  codeError.value = ''
  formRef.value?.resetFields()
}

// 初始化加载
const init = async () => {
  await loadCategoryTree()
  if (isEdit.value) {
    await loadProductDetail()
  }
}

// 暴露方法和属性给父组件
defineExpose({
  formRef,
  formData,
  submitting,
  handleSubmit,
  resetForm,
  init,
  isEdit,
})

// 组件挂载时初始化
onMounted(() => {
  init()
})
</script>

<style scoped lang="scss">
.product-form {
  max-height: 70vh;
  overflow-y: auto;
  padding-right: 10px;

  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;

    &.is-error {
      color: #f56c6c;
    }
  }

  .option-code {
    font-size: 12px;
    color: #909399;
    margin-left: 8px;
  }

  .select-option {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;

    .option-desc {
      font-size: 12px;
      color: #909399;
    }
  }

  .selected-info {
    margin-top: 8px;

    .bom-info,
    .routing-info {
      display: flex;
      align-items: center;
      gap: 16px;
      font-size: 13px;
    }
  }

  .margin-info {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    font-weight: bold;
  }
}

:deep(.el-divider) {
  margin: 20px 0;
}

:deep(.el-form-item) {
  margin-bottom: 18px;
}

/* 编码结构卡片样式 */
.code-structure-card {
  margin-bottom: 18px;
  border: 1px solid #e4e7ed;

  :deep(.el-card__header) {
    padding: 10px 16px;
    background-color: #f5f7fa;
    border-bottom: 1px solid #e4e7ed;
  }

  .card-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }

  :deep(.el-form-item) {
    margin-bottom: 0;
  }
}

/* 编码预览样式 */
.code-preview {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 20px;
  font-weight: bold;
  font-family: 'Courier New', Courier, monospace;
  letter-spacing: 2px;

  .code-part {
    padding: 4px 6px;
    border-radius: 4px;

    &.customer {
      color: #409eff;
      background-color: #ecf5ff;
    }

    &.serial {
      color: #67c23a;
      background-color: #f0f9eb;
    }

    &.panel {
      color: #e6a23c;
      background-color: #fdf6ec;
    }

    &.circuit {
      color: #f56c6c;
      background-color: #fef0f0;
    }
  }
}
</style>
