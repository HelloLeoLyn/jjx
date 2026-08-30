<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="500px"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="订单号">
        <el-input :model-value="orderNo" disabled />
      </el-form-item>
      <el-form-item label="审批结果" prop="approvalStatus">
        <el-radio-group v-model="form.approvalStatus">
          <el-radio :value="3" border>通过</el-radio>
          <el-radio :value="4" border>拒绝</el-radio>
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
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { approveOrder } from '@/api/purchase/order'
import { ApprovalStatusEnum } from '@/enums/purchase/order'
const props = defineProps<{
  visible: boolean
  orderId?: number
  orderNo: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  approvalComment: '',
  approvalStatus: 3,
})

const title = computed(() => `审批采购订单 - ${props.orderNo}`)

const rules = reactive<FormRules>({
  approvalStatus: [{ required: true, message: '请选择审批结果', trigger: 'change' }],
})

const handleSubmit = async () => {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await approveOrder({
      orderId: Number(props.orderId),
      approverId: 1, // 需要从当前用户获取
      approverName: '当前用户', // 需要从当前用户获取
      approvalComment: form.approvalComment,
      approvalStatus: form.approvalStatus,
    })
    ElMessage.error('审批成功')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('审批失败:', error)
    ElMessage.error('审批失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.approvalComment = ''
  emit('update:visible', false)
}
</script>
