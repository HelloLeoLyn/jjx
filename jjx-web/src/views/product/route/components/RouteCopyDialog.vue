<template>
  <el-dialog
    v-model="visible"
    title="复制为新版本"
    width="400px"
    :close-on-click-modal="false"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="原版本">
        <el-input :model-value="currentVersion" disabled />
      </el-form-item>
      <el-form-item label="新版本" prop="newVersion">
        <el-input v-model="form.newVersion" placeholder="请输入新版本号，如 V2.0" maxlength="20" />
      </el-form-item>
    </el-form>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="loading" @click="handleSubmit">确定复制</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

const props = defineProps<{
  modelValue: boolean
  currentVersion: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [newVersion: string]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  newVersion: '',
})

const rules = reactive<FormRules<typeof form>>({
  newVersion: [
    { required: true, message: '请输入新版本号', trigger: 'blur' },
    { pattern: /^V\d+\.\d+$/, message: '版本号格式为 Vx.x，如 V2.0', trigger: 'blur' },
  ],
})

const handleSubmit = async () => {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
    loading.value = true
    emit('confirm', form.newVersion)
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.newVersion = ''
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
