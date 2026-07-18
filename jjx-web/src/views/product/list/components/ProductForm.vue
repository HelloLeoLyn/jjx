<template>
  <el-dialog
    v-model="visible"
    :title="isEdit ? '编辑产品' : '新增产品'"
    width="1200px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <ProductFormContent
      ref="formContentRef"
      :product-id="props.productId"
      @success="handleFormSuccess"
    />

    <template #footer>
      <el-button @click="handleClose">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ isEdit ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import ProductFormContent from './ProductFormContent.vue'

const props = defineProps<{
  modelValue: boolean
  productId?: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const isEdit = computed(() => !!props.productId)
const formContentRef = ref<InstanceType<typeof ProductFormContent>>()
const submitting = ref(false)

// 提交表单
const handleSubmit = async () => {
  if (!formContentRef.value) return
  submitting.value = true
  try {
    await formContentRef.value.handleSubmit()
    // 如果 handleSubmit 成功（没有抛异常），关闭弹窗
    handleClose()
  } catch {
    // 验证失败或提交失败，不关闭弹窗
  } finally {
    submitting.value = false
  }
}

// 表单提交成功
const handleFormSuccess = () => {
  emit('success')
  handleClose()
}

// 关闭
const handleClose = () => {
  visible.value = false
  formContentRef.value?.resetForm()
}

// 监听对话框打开
watch(visible, async (val) => {
  if (val) {
    await formContentRef.value?.init()
  }
})
</script>
