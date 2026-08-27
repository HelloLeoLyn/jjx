<!-- components/RejectDialog.vue -->
<template>
  <el-dialog
    v-model="dialogVisible"
    title="审核驳回"
    width="500px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="订单编号" prop="orderNo">
        <el-input v-model="formData.orderNo" disabled />
      </el-form-item>

      <el-form-item label="客户名称" prop="customerName">
        <el-input v-model="formData.customerName" disabled />
      </el-form-item>

      <el-form-item label="驳回原因" prop="remark" required>
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="5"
          placeholder="请填写驳回原因"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item label="驳回人">
        <el-input v-model="formData.reviewerName" disabled />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="danger" :loading="loading" @click="handleConfirm"> 确认驳回 </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '@/api/sales/order'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { useUserStore } from '@/store/modules/user'

const props = defineProps<{
  modelValue: boolean
  orderId: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const userStore = useUserStore()

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const formRef = ref()
const loading = ref(false)

const formData = ref({
  orderId: 0,
  orderNo: '',
  customerName: '',
  remark: '',
  reviewerName: '',
})

const rules = {
  remark: [
    { required: true, message: '请填写驳回原因', trigger: 'blur' },
    { min: 1, max: 500, message: '驳回原因长度在1-500个字符之间', trigger: 'blur' },
  ],
}

// 获取订单详情
const fetchOrderDetail = async () => {
  if (!props.orderId) return

  try {
    const response = await orderApi.getOrder(props.orderId)
    const data = response.data

    formData.value.orderId = (data as any).orderId
    formData.value.orderNo = (data as any).orderNo
    formData.value.customerName = (data as any).customerName
  } catch (error) {
    console.error('获取订单详情失败', error)
    ElMessage.error('获取订单详情失败')
  }
}

// 确认驳回
const handleConfirm = async () => {
  await formRef.value?.validate()

  loading.value = true

  try {
    await orderStatusApi.rejectOrder(props.orderId, formData.value.remark)
    ElMessage.success('审核驳回成功')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('审核驳回失败', error)
    ElMessage.error('审核驳回失败')
  } finally {
    loading.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  dialogVisible.value = false
  formData.value.remark = ''
  formRef.value?.resetFields()
}

// 监听对话框打开
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.orderId) {
      await fetchOrderDetail()
      formData.value.reviewerName = userStore.userInfo?.userName || '当前用户'
    }
  }
)
</script>
