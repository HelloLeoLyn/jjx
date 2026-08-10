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

    <!-- 编码构成要素 -->
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
      <el-col :span="12">
        <el-form-item label="流水号" prop="codeSerialNo" required>
          <el-input v-model="formData.codeSerialNo" placeholder="自动生成" maxlength="3" />
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="面板结构" required>
          <el-select
            v-model="codePanelType"
            placeholder="面板类型"
            style="width: 100%"
            @change="composeProductCode"
          >
            <el-option label="有面板有线路" value="M" />
            <el-option label="仅有线路" value="S" />
            <el-option label="仅有面板" value="P" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="面板特征" required>
          <el-select
            v-model="codePanelFeature"
            placeholder="面板特征"
            style="width: 100%"
            @change="composeProductCode"
          >
            <el-option label="面板有凹凸" value="E" />
            <el-option label="面板有窗口" value="W" />
            <el-option label="有窗口也有凹凸" value="H" />
            <el-option label="无" value="O" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <el-row :gutter="20">
      <el-col :span="12">
        <el-form-item label="线路类型" required>
          <el-select
            v-model="codeCircuitType"
            placeholder="线路类型"
            style="width: 100%"
            @change="composeProductCode"
          >
            <el-option label="无(印银平key)" value="O" />
            <el-option label="有金属弹片" value="M" />
            <el-option label="线路有凹凸" value="P" />
          </el-select>
        </el-form-item>
      </el-col>
      <el-col :span="12">
        <el-form-item label="线路特征" required>
          <el-select
            v-model="codeCircuitFeature"
            placeholder="线路特征"
            style="width: 100%"
            @change="composeProductCode"
          >
            <el-option label="无" value="O" />
            <el-option label="有发光二极体" value="L" />
            <el-option label="有连接器" value="C" />
            <el-option label="有连接器及发光二极体" value="H" />
          </el-select>
        </el-form-item>
      </el-col>
    </el-row>
    <!-- 产品编码构成 -->
    <el-row :gutter="20">
      <el-col :span="24">
        <el-form-item label="产品编码" prop="productCode" required>
          <el-input
            v-model="formData.productCode"
            placeholder="请选择编码构成要素自动生成"
            readonly
          >
            <template #append>
              <el-button @click="validateCodeUnique" :loading="generatingCode">
                <el-icon><Refresh /></el-icon> 校验唯一性
              </el-button>
            </template>
          </el-input>
          <div class="form-tip" :class="{ 'is-error': codeError }">
            {{
              codeError || '编码格式：客户简称(3位) + 流水号(3位) + 面板结构(2位) + 线路结构(2位)'
            }}
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

// 编码结构要素（独立于formData，用于组合编码）
const codePanelType = ref('')
const codePanelFeature = ref('')
const codeCircuitType = ref('')
const codeCircuitFeature = ref('')

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
const customerShortNameDisplay = computed(() => {
  if (!selectedCustomer.value) return ''
  return (
    selectedCustomer.value.customerShortName ||
    selectedCustomer.value.customerName.substring(0, 3) ||
    ''
  )
})

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
    { pattern: /^[A-Za-z0-9]{10}$/, message: '编码格式不正确，请重新生成', trigger: 'blur' },
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

// 客户选择变化 - 生成流水号
const handleCustomerChange = async (
  value: number | string | null,
  customer: CustomerSearchVO | null
) => {
  if (!value || !customer) {
    formData.codeSerialNo = ''
    selectedCustomer.value = null
    composeProductCode()
    return
  }

  // 保存选中的客户对象
  selectedCustomer.value = customer
  selectedCustomerShortName.value = customer.customerShortName || ''

  // 生成流水号（调用后端API）
  try {
    const res = await productApi.generateSerialNo(formData.codeCustomerId!)
    if (res.code === 200 && res.data) {
      formData.codeSerialNo = String(res.data).padStart(3, '0')
    } else {
      // 如果API失败，使用临时流水号
      formData.codeSerialNo = '001'
    }
  } catch (error) {
    console.error('生成流水号失败:', error)
    formData.codeSerialNo = '001'
  }

  composeProductCode()
}

// 组合产品编码
const composeProductCode = () => {
  // 2026-08-10 DEV-772：客户简称不足3位不再卡死——按实际长度使用（1-3位），不强制补到3位
  const customerPart = customerShortNameDisplay.value.substring(0, 3)
  const serialPart = formData.codeSerialNo || ''
  const panelPart = `${codePanelType.value}${codePanelFeature.value}`
  const circuitPart = `${codeCircuitType.value}${codeCircuitFeature.value}`

  if (
    customerPart.length >= 1 &&
    serialPart.length === 3 &&
    panelPart.length === 2 &&
    circuitPart.length === 2
  ) {
    formData.productCode = `${customerPart}${serialPart}${panelPart}${circuitPart}`
    codeError.value = ''
  } else {
    formData.productCode = ''
  }
}

// 获取分类树
const loadCategoryTree = async () => {
  const res = await productApi.category.getProductCategoryTree()
  categoryTree.value = res.data || []
}

// 加载产品详情
const loadProductDetail = async () => {
  if (!props.productId) return
  const res = await productApi.info(props.productId)
  const data = res.data
  Object.assign(formData, data)

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

// 验证编码唯一性
const validateCodeUnique = async () => {
  if (!formData.productCode) return
  const res = await productApi.isUniqueProductCode(formData.productCode)
  if (res.data) {
    codeError.value = '产品编码已存在'
  } else {
    codeError.value = ''
  }
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
  codePanelType.value = ''
  codePanelFeature.value = ''
  codeCircuitType.value = ''
  codeCircuitFeature.value = ''
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
