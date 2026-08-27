<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="500px"
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
      <el-form-item label="目标状态" prop="orderStatus">
        <el-select v-model="formData.orderStatus" placeholder="请选择目标状态" style="width: 100%">
          <el-option
            v-for="status in availableStatuses"
            :key="status.value"
            :label="status.label"
            :value="status.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入状态变更备注"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item
        v-if="formData.orderStatus === 2"
        label="审批备注"
        prop="approvalRemark"
      >
        <el-input
          v-model="formData.approvalRemark"
          type="textarea"
          :rows="2"
          placeholder="请输入审批备注"
          maxlength="200"
          show-word-limit
        />
      </el-form-item>

      <el-form-item
        v-if="formData.orderStatus === 8"
        label="完成数量"
        prop="completedQuantity"
      >
        <el-input-number
          v-model="formData.completedQuantity"
          :min="0"
          :max="maxCompletedQuantity"
          placeholder="请输入完成数量"
          style="width: 100%"
        />
      </el-form-item>

      <el-form-item
        v-if="formData.orderStatus === 8"
        label="质量结果"
        prop="qualityResult"
      >
        <el-select
          v-model="formData.qualityResult"
          placeholder="请选择质量结果"
          style="width: 100%"
        >
          <el-option label="合格" value="qualified" />
          <el-option label="不合格" value="unqualified" />
          <el-option label="待检" value="pending" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose" :disabled="loading">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading"> 确认更新 </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  OrderStatus,
  ApprovalStatus,
  type ProductionOrderVO,
  type OrderStatusUpdateDTO,
} from '@/types/production/order'
import { STATUS_FLOW_RULES, STATUS_LABELS } from '@/types/production/order'
import { ExecutionStatusEnum } from '@/enums/production'

interface Props {
  visible: boolean
  order: ProductionOrderVO | null
  loading?: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'submit', data: OrderStatusUpdateDTO): void
  (e: 'close'): void
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  order: null,
  loading: false,
})
// 弹窗可见性
const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})
const emit = defineEmits<Emits>()

// 表单引用
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive<{
  orderStatus: OrderStatus
  remark?: string
  approvalRemark?: string
  completedQuantity?: number
  qualityResult?: string
}>({
  orderStatus: OrderStatus.DRAFT,
  remark: '',
  approvalRemark: '',
  completedQuantity: 0,
  qualityResult: '',
})

// 表单验证规则
const formRules: FormRules = {
  orderStatus: [{ required: true, message: '请选择目标状态', trigger: 'change' }],
  completedQuantity: [{ type: 'number', min: 0, message: '完成数量不能小于0', trigger: 'blur' }],
}

// 计算属性
const dialogTitle = computed(() => {
  if (!props.order) return '状态更新'
  return `更新订单状态 - ${props.order.orderNo}`
})

const availableStatuses = computed(() => {
  if (!props.order) return []

  const currentStatus = props.order.orderStatus
  const nextStatuses = STATUS_FLOW_RULES[currentStatus] || []

  return nextStatuses.map((status) => ({
    value: status,
    label: STATUS_LABELS[status],
  }))
})

const maxCompletedQuantity = computed(() => {
  if (!props.order) return 0
  return props.order.plannedQuantity - props.order.completedQuantity
})

// 监听props变化
watch(
  () => props.visible,
  (newValue) => {
    if (newValue && props.order) {
      resetForm()
    }
  }
)

// 方法
const resetForm = () => {
  if (!props.order) return

  formData.orderStatus = props.order.orderStatus
  formData.remark = ''
  formData.approvalRemark = ''
  formData.completedQuantity = 0
  formData.qualityResult = ''

  // 设置默认值
  const nextStatuses = STATUS_FLOW_RULES[props.order.orderStatus] || []
  if (nextStatuses.length > 0) {
    formData.orderStatus = nextStatuses[0]
  }

  if (formRef.value) {
    formRef.value.clearValidate()
  }
}

const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const handleSubmit = async () => {
  if (!formRef.value || !props.order) return

  const valid = await formRef.value.validate()
  if (!valid) return

  // 构建提交数据
  const submitData: OrderStatusUpdateDTO = {
    orderId: props.order.orderId,
    orderStatus: formData.orderStatus,
    remark: formData.remark,
  }

  // 根据状态添加额外字段
  if (formData.orderStatus === OrderStatus.APPROVED) {
    submitData.approvalStatus = ApprovalStatus.APPROVED
    submitData.approvalRemark = formData.approvalRemark
  }

  if (formData.orderStatus === OrderStatus.COMPLETED) {
    submitData.executionStatus = ExecutionStatusEnum.COMPLETED.value
    submitData.completedQuantity = formData.completedQuantity
    submitData.qualityResult = formData.qualityResult
  }

  emit('submit', submitData)
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
