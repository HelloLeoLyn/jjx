<template>
  <el-dialog :title="title" v-model="visible" width="500px" append-to-body @close="handleClose">
    <el-form ref="approveFormRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="订单号">
        <el-input v-model="form.orderNo" readonly />
      </el-form-item>

      <el-form-item label="供应商">
        <el-input v-model="form.supplierName" readonly />
      </el-form-item>

      <el-form-item label="订单金额">
        <el-input v-model="form.orderTotalAmount" readonly>
          <template #append>{{ form.currency }}</template>
        </el-input>
      </el-form-item>

      <el-form-item label="审批结果" prop="approvalStatus">
        <el-radio-group v-model="form.approvalStatus">
          <el-radio value="approved">通过</el-radio>
          <el-radio value="rejected">驳回</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="审批意见" prop="approvalComment">
        <el-input
          v-model="form.approvalComment"
          type="textarea"
          :rows="4"
          placeholder="请输入审批意见"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading"> 确定 </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { approveOrder } from '@/api/purchase/order'

const props = defineProps<{
  modelValue: boolean
  orderData: any
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = ref(false)
const loading = ref(false)
const approveFormRef = ref<FormInstance>()

const title = ref('订单审批')

const form = reactive({
  orderId: undefined as number | undefined,
  orderNo: '',
  supplierName: '',
  orderTotalAmount: 0,
  currency: 'CNY',
  approvalStatus: 'approved',
  approvalComment: '',
})

const rules = reactive<FormRules>({
  approvalStatus: [{ required: true, message: '请选择审批结果', trigger: 'change' }],
  approvalComment: [
    { required: true, message: '请输入审批意见', trigger: 'blur' },
    { min: 2, max: 500, message: '长度在 2 到 500 个字符', trigger: 'blur' },
  ],
})

// 监听props变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) {
      initForm()
    }
  }
)

// 监听visible变化
watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 初始化表单
const initForm = () => {
  if (props.orderData) {
    form.orderId = props.orderData.orderId
    form.orderNo = props.orderData.orderNo
    form.supplierName = props.orderData.supplierName
    form.orderTotalAmount = props.orderData.orderTotalAmount
    form.currency = props.orderData.currency || 'CNY'
    form.approvalStatus = 'approved'
    form.approvalComment = ''
  }

  // 重置表单验证
  nextTick(() => {
    approveFormRef.value?.clearValidate()
  })
}

// 关闭对话框
const handleClose = () => {
  visible.value = false
}

// 提交审批
const handleSubmit = async () => {
  if (!approveFormRef.value) return

  await approveFormRef.value.validate(async (valid: boolean) => {
    if (valid) {
      loading.value = true
      try {
        // 获取当前用户信息（这里需要根据实际项目调整）
        const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
        const approverId = userInfo.userId || 1
        const approverName = userInfo.nickName || userInfo.userName || '管理员'

        await approveOrder({
          orderId: form.orderId!,
          approverId,
          approverName,
          approvalComment: form.approvalComment,
          approvalStatus: form.approvalStatus === 'approved' ? 1 : 2,
        })

        ElMessage.success('审批成功')
        emit('success')
        handleClose()
      } catch (error) {
        console.error('审批失败:', error)
      } finally {
        loading.value = false
      }
    }
  })
}
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>
