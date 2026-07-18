<!-- components/ReviewDialog.vue -->
<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="订单信息" prop="orderNo">
        <el-input v-model="formData.orderNo" disabled placeholder="订单编号" />
      </el-form-item>

      <el-form-item label="客户名称" prop="customerName">
        <el-input v-model="formData.customerName" disabled placeholder="客户名称" />
      </el-form-item>

      <el-form-item label="订单金额" prop="totalAmount">
        <el-input v-model="formData.totalAmount" disabled placeholder="订单金额">
          <template #append>元</template>
        </el-input>
      </el-form-item>

      <el-form-item label="审核结果" prop="result" required>
        <el-radio-group v-model="formData.result">
          <el-radio value="approve">审核通过</el-radio>
          <el-radio value="reject">审核驳回</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="formData.result === 'reject'" label="驳回原因" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="4"
          placeholder="请填写驳回原因"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item v-else-if="formData.result === 'approve'" label="审核意见" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入审核意见（选填）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <!-- 审核人信息（只读） -->
      <el-divider content-position="left">审核信息</el-divider>

      <el-form-item label="审核人">
        <el-input v-model="formData.reviewerName" disabled placeholder="当前登录用户" />
      </el-form-item>

      <el-form-item label="审核时间">
        <el-input v-model="formData.reviewTime" disabled placeholder="当前时间" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm"> 确认 </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { orderApi } from '@/api/sales/order'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{
  modelValue: boolean
  orderId: number
  action: 'approve' | 'reject' | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

// 用户信息
const userStore = useUserStore()

// 对话框可见性
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// 对话框标题
const dialogTitle = computed(() => {
  if (props.action === 'approve') return '审核通过'
  if (props.action === 'reject') return '审核驳回'
  return '订单审核'
})

// 表单引用
const formRef = ref()
const loading = ref(false)

// 订单详情
const orderDetail = ref<any>(null)

// 表单数据
const formData = ref({
  orderId: 0,
  orderNo: '',
  customerName: '',
  totalAmount: '',
  result: 'approve',
  remark: '',
  reviewerName: '',
  reviewTime: '',
})

// 表单验证规则
const rules = {
  remark: [
    {
      required: true,
      message: '请填写驳回原因',
      trigger: 'blur',
      validator: (rule: any, value: string, callback: any) => {
        if (formData.value.result === 'reject' && !value) {
          callback(new Error('请填写驳回原因'))
        } else {
          callback()
        }
      },
    },
  ],
}

// 获取订单详情
const fetchOrderDetail = async () => {
  if (!props.orderId) return

  try {
    const response = await orderApi.getOrder(props.orderId)
    const data = response.data

    orderDetail.value = data
    formData.value.orderId = data.orderId
    formData.value.orderNo = data.orderNo
    formData.value.customerName = data.customerName
    formData.value.totalAmount = formatAmount(data.totalAmount)
  } catch (error) {
    console.error('获取订单详情失败', error)
    ElMessage.error('获取订单详情失败')
  }
}

// 格式化金额
const formatAmount = (amount: number): string => {
  if (amount === undefined || amount === null) return '0.00'
  return amount.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

// 格式化时间
const formatTime = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 确认审核
const handleConfirm = async () => {
  // 如果是驳回，需要验证表单
  if (formData.value.result === 'reject') {
    await formRef.value?.validate()
  }

  loading.value = true

  try {
    if (formData.value.result === 'approve') {
      // 审核通过
      await orderStatusApi.approveOrder(props.orderId, formData.value.remark)
      ElMessage.success('审核通过成功')
    } else {
      // 审核驳回
      await orderStatusApi.rejectOrder(props.orderId, formData.value.remark)
      ElMessage.success('审核驳回成功')
    }

    emit('success')
    handleClose()
  } catch (error) {
    console.error('审核操作失败', error)
    ElMessage.error('审核操作失败')
  } finally {
    loading.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  dialogVisible.value = false
  // 重置表单
  formData.value = {
    orderId: 0,
    orderNo: '',
    customerName: '',
    totalAmount: '',
    result: 'approve',
    remark: '',
    reviewerName: '',
    reviewTime: '',
  }
  formRef.value?.resetFields()
}

// 监听对话框打开
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.orderId) {
      await fetchOrderDetail()
      // 设置审核人信息
      formData.value.reviewerName = userStore.userInfo?.userName || '当前用户'
      formData.value.reviewTime = formatTime(new Date())
    }
  }
)

// 监听 action 变化，重置审核结果
watch(
  () => props.action,
  (val) => {
    if (val === 'approve') {
      formData.value.result = 'approve'
    } else if (val === 'reject') {
      formData.value.result = 'reject'
    }
  }
)
</script>

<style scoped lang="scss">
:deep(.el-dialog__body) {
  padding-top: 20px;
}

:deep(.el-divider) {
  margin: 16px 0;
}
</style>
