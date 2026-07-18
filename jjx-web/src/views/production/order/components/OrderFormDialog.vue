<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="800px"
    :before-close="handleClose"
    destroy-on-close
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      :disabled="loading"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="订单类型" prop="orderType">
            <el-select
              v-model="formData.orderType"
              placeholder="请选择订单类型"
              :disabled="isEditMode"
            >
              <el-option label="生产计划" value="plan" />
              <el-option label="生产工单" value="work_order" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="产品" prop="productId">
            <el-select
              v-model="formData.productId"
              placeholder="请选择产品"
              filterable
              remote
              :remote-method="searchProducts"
              :loading="productLoading"
            >
              <el-option
                v-for="product in productOptions"
                :key="product.id"
                :label="`${product.code} - ${product.name}`"
                :value="product.id"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="计划数量" prop="plannedQuantity">
            <el-input-number
              v-model="formData.plannedQuantity"
              :min="1"
              :max="999999"
              placeholder="请输入计划数量"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="优先级" prop="priority">
            <el-select v-model="formData.priority" placeholder="请选择优先级">
              <el-option label="低" value="low" />
              <el-option label="中" value="medium" />
              <el-option label="高" value="high" />
              <el-option label="紧急" value="urgent" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="计划开始" prop="planStartDate">
            <el-date-picker
              v-model="formData.planStartDate"
              type="date"
              placeholder="选择开始日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12">
          <el-form-item label="计划结束" prop="planEndDate">
            <el-date-picker
              v-model="formData.planEndDate"
              type="date"
              placeholder="选择结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <el-col :span="12" v-if="formData.orderType === 'plan'">
          <el-form-item label="计划类型" prop="planType">
            <el-select v-model="formData.planType" placeholder="请选择计划类型">
              <el-option label="月计划" value="monthly" />
              <el-option label="周计划" value="weekly" />
              <el-option label="日计划" value="daily" />
              <el-option label="专项计划" value="special" />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12" v-if="formData.orderType === 'plan'">
          <el-form-item label="审批人" prop="approverId">
            <el-select
              v-model="formData.approverId"
              placeholder="请选择审批人"
              filterable
              remote
              :remote-method="searchApprovers"
              :loading="approverLoading"
            >
              <el-option
                v-for="approver in approverOptions"
                :key="approver.id"
                :label="approver.name"
                :value="approver.id"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12" v-if="formData.orderType === 'work_order'">
          <el-form-item label="操作员" prop="operatorId">
            <el-select
              v-model="formData.operatorId"
              placeholder="请选择操作员"
              filterable
              remote
              :remote-method="searchOperators"
              :loading="operatorLoading"
            >
              <el-option
                v-for="operator in operatorOptions"
                :key="operator.id"
                :label="operator.name"
                :value="operator.id"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="12" v-if="formData.orderType === 'work_order'">
          <el-form-item label="设备" prop="equipmentId">
            <el-select
              v-model="formData.equipmentId"
              placeholder="请选择设备"
              filterable
              remote
              :remote-method="searchEquipment"
              :loading="equipmentLoading"
            >
              <el-option
                v-for="equipment in equipmentOptions"
                :key="equipment.id"
                :label="`${equipment.code} - ${equipment.name}`"
                :value="equipment.id"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="3"
              placeholder="请输入备注信息"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose" :disabled="loading">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">
          {{ isEditMode ? '更新' : '创建' }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  OrderType,
  Priority,
  PlanType,
  type ProductionOrderVO,
  type ProductionOrderCreateDTO,
  type ProductionOrderUpdateDTO,
} from '@/types/production/order'

interface Props {
  visible: boolean
  order?: ProductionOrderVO | null
  loading?: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'submit', data: ProductionOrderCreateDTO | ProductionOrderUpdateDTO): void
  (e: 'close'): void
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  order: null,
  loading: false,
})

const emit = defineEmits<Emits>()
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})
// 表单引用
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<{
  orderType: OrderType
  productId: string
  plannedQuantity: number
  priority: Priority
  planStartDate: string
  planEndDate: string
  planType?: PlanType
  approverId?: string
  operatorId?: string
  equipmentId?: string
  remark?: string
}>({
  orderType: OrderType.PLAN,
  productId: '',
  plannedQuantity: 100,
  priority: Priority.MEDIUM,
  planStartDate: '',
  planEndDate: '',
  planType: PlanType.MONTHLY,
  approverId: '',
  operatorId: '',
  equipmentId: '',
  remark: '',
})

// 表单验证规则
const formRules: FormRules = {
  orderType: [{ required: true, message: '请选择订单类型', trigger: 'change' }],
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  plannedQuantity: [
    { required: true, message: '请输入计划数量', trigger: 'blur' },
    { type: 'number', min: 1, message: '数量必须大于0', trigger: 'blur' },
  ],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  planStartDate: [{ required: true, message: '请选择计划开始日期', trigger: 'change' }],
  planEndDate: [{ required: true, message: '请选择计划结束日期', trigger: 'change' }],
  planType: [{ required: true, message: '请选择计划类型', trigger: 'change' }],
}

// 搜索选项
const productOptions = ref<Array<{ id: string; code: string; name: string }>>([])
const approverOptions = ref<Array<{ id: string; name: string }>>([])
const operatorOptions = ref<Array<{ id: string; name: string }>>([])
const equipmentOptions = ref<Array<{ id: string; code: string; name: string }>>([])

// 加载状态
const productLoading = ref(false)
const approverLoading = ref(false)
const operatorLoading = ref(false)
const equipmentLoading = ref(false)

// 计算属性
const isEditMode = computed(() => !!props.order)
const dialogTitle = computed(() => {
  return isEditMode.value ? '编辑生产订单' : '新建生产订单'
})

// 监听props变化
watch(
  () => props.visible,
  (newValue) => {
    if (newValue) {
      resetForm()
      if (props.order) {
        loadOrderData(props.order)
      }
    }
  }
)

// 方法
const resetForm = () => {
  formData.orderType = OrderType.PLAN
  formData.productId = ''
  formData.plannedQuantity = 100
  formData.priority = Priority.MEDIUM
  formData.planStartDate = ''
  formData.planEndDate = ''
  formData.planType = PlanType.MONTHLY
  formData.approverId = ''
  formData.operatorId = ''
  formData.equipmentId = ''
  formData.remark = ''

  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

const loadOrderData = (order: ProductionOrderVO) => {
  formData.orderType = order.orderType
  formData.productId = order.productId
  formData.plannedQuantity = order.plannedQuantity
  formData.priority = order.priority
  formData.planStartDate = order.planStartDate
  formData.planEndDate = order.planEndDate
  formData.remark = order.remark || ''

  // 加载产品选项
  productOptions.value = [
    {
      id: order.productId,
      code: order.productCode,
      name: order.productName,
    },
  ]
}

const searchProducts = async (query: string) => {
  if (!query.trim()) {
    productOptions.value = []
    return
  }

  productLoading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))
    productOptions.value = [
      { id: '1', code: 'P001', name: '产品A' },
      { id: '2', code: 'P002', name: '产品B' },
      { id: '3', code: 'P003', name: '产品C' },
    ].filter((p) => p.code.includes(query) || p.name.includes(query))
  } catch (error) {
    console.error('搜索产品失败:', error)
  } finally {
    productLoading.value = false
  }
}

const searchApprovers = async (query: string) => {
  if (!query.trim()) {
    approverOptions.value = []
    return
  }

  approverLoading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))
    approverOptions.value = [
      { id: '1', name: '张三' },
      { id: '2', name: '李四' },
      { id: '3', name: '王五' },
    ].filter((a) => a.name.includes(query))
  } catch (error) {
    console.error('搜索审批人失败:', error)
  } finally {
    approverLoading.value = false
  }
}

const searchOperators = async (query: string) => {
  if (!query.trim()) {
    operatorOptions.value = []
    return
  }

  operatorLoading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))
    operatorOptions.value = [
      { id: '1', name: '操作员A' },
      { id: '2', name: '操作员B' },
      { id: '3', name: '操作员C' },
    ].filter((o) => o.name.includes(query))
  } catch (error) {
    console.error('搜索操作员失败:', error)
  } finally {
    operatorLoading.value = false
  }
}

const searchEquipment = async (query: string) => {
  if (!query.trim()) {
    equipmentOptions.value = []
    return
  }

  equipmentLoading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))
    equipmentOptions.value = [
      { id: '1', code: 'EQ001', name: '设备A' },
      { id: '2', code: 'EQ002', name: '设备B' },
      { id: '3', code: 'EQ003', name: '设备C' },
    ].filter((e) => e.code.includes(query) || e.name.includes(query))
  } catch (error) {
    console.error('搜索设备失败:', error)
  } finally {
    equipmentLoading.value = false
  }
}

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const handleSubmit = async () => {
  if (!formRef.value) return

  const valid = await formRef.value.validate()
  if (!valid) return

  // 构建提交数据
  const submitData: any = {
    ...formData,
  }

  if (isEditMode.value && props.order) {
    // 编辑模式
    submitData.orderId = props.order.orderId
    emit('submit', submitData as ProductionOrderUpdateDTO)
  } else {
    // 创建模式
    emit('submit', submitData as ProductionOrderCreateDTO)
  }
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
