<template>
  <el-dialog
    :title="title"
    v-model="visible"
    width="1400px"
    append-to-body
    :before-close="handleCancel"
  >
    <OrderForm ref="orderFormRef" @success="handleFormSuccess" @cancel="handleCancel" />

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确 定</el-button>
        <el-button @click="handleCancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import OrderForm from './OrderForm.vue'

// 组件属性
interface Props {
  modelValue: boolean
  title?: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  title: '新增订单',
})

// 组件事件
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  cancel: []
}>()

// 响应式数据
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// 表单组件引用
const orderFormRef = ref<InstanceType<typeof OrderForm> | null>(null)
const submitting = ref(false)

// 监听对话框显示状态
watch(visible, (newVal) => {
  if (newVal && orderFormRef.value) {
    orderFormRef.value.resetForm()
    orderFormRef.value.generateOrderNo()
  }
})

// 提交表单
const handleSubmit = async () => {
  if (!orderFormRef.value) return

  submitting.value = true
  try {
    const success = await orderFormRef.value.submitForm()
    if (success) {
      visible.value = false
      emit('success')
    }
  } catch (error) {
    console.error('新增订单失败:', error)
    ElMessage.error('新增订单失败')
  } finally {
    submitting.value = false
  }
}

// 表单内部成功回调
const handleFormSuccess = () => {
  visible.value = false
  emit('success')
}

// 取消操作
const handleCancel = () => {
  visible.value = false
  emit('cancel')
}
</script>
