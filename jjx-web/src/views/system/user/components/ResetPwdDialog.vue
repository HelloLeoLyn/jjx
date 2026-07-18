<!-- views/system/user/components/ResetPwdDialog.vue -->
<template>
  <el-dialog
    title="重置密码"
    v-model="dialogVisible"
    width="400px"
    append-to-body
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="新密码" prop="newPassword">
        <el-input
          v-model="form.newPassword"
          placeholder="请输入新密码"
          type="password"
          maxlength="20"
          show-password
        />
      </el-form-item>
      <el-form-item label="确认密码" prop="confirmPassword">
        <el-input
          v-model="form.confirmPassword"
          placeholder="请确认新密码"
          type="password"
          maxlength="20"
          show-password
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitting" @click="submitForm">
          确 定
        </el-button>
        <el-button @click="handleClose">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/system/user'

interface Props {
  visible: boolean
  userId?: number
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const form = reactive({
  newPassword: '',
  confirmPassword: '',
})

const rules = reactive<FormRules>({
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度必须介于5到20之间', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
})

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (valid && props.userId) {
      submitting.value = true
      try {
        await userApi.resetPwd({
          userId: props.userId,
          password: form.newPassword,
        })
        ElMessage.success('重置密码成功')
        emit('success')
        handleClose()
      } catch (error) {
        console.error('重置密码失败:', error)
        ElMessage.error('重置密码失败')
      } finally {
        submitting.value = false
      }
    }
  })
}

// 关闭弹窗
const handleClose = () => {
  form.newPassword = ''
  form.confirmPassword = ''
  dialogVisible.value = false
}

// 重置表单
const resetForm = () => {
  form.newPassword = ''
  form.confirmPassword = ''
}

// 监听弹窗关闭
watch(
  () => props.visible,
  (val) => {
    if (!val) {
      resetForm()
    }
  },
)
</script>

<style scoped lang="scss">
.dialog-footer {
  text-align: right;
}
</style>
