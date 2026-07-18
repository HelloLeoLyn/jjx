<template>
  <el-dialog
    :title="title"
    v-model="dialogVisible"
    width="500px"
    append-to-body
    @close="handleCancel"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="角色名称" prop="roleName">
        <el-input v-model="formData.roleName" placeholder="请输入角色名称" />
      </el-form-item>
      <el-form-item label="权限字符" prop="roleKey">
        <el-input v-model="formData.roleKey" placeholder="请输入权限字符" />
      </el-form-item>
      <el-form-item label="角色顺序" prop="roleSort">
        <el-input-number v-model="formData.roleSort" controls-position="right" :min="0" />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="formData.status">
          <el-radio value="0">正常</el-radio>
          <el-radio value="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注">
        <el-input
          v-model="formData.remark"
          type="textarea"
          placeholder="请输入内容"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确 定</el-button>
        <el-button @click="handleCancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { roleApi } from '@/api/system/role'
import type { SysRole } from '@/types/system'

const props = defineProps<{
  visible: boolean
  title: string
  formData: SysRole
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const dialogVisible = ref(props.visible)
const submitLoading = ref(false)
const formRef = ref<FormInstance>()

watch(
  () => props.visible,
  (val) => {
    dialogVisible.value = val
  }
)

watch(
  () => dialogVisible.value,
  (val) => {
    emit('update:visible', val)
  }
)

const rules = reactive<FormRules>({
  roleName: [{ required: true, message: '角色名称不能为空', trigger: 'blur' }],
  roleKey: [{ required: true, message: '权限字符不能为空', trigger: 'blur' }],
  roleSort: [{ required: true, message: '角色顺序不能为空', trigger: 'blur' }],
})

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitLoading.value = true
      try {
        if (props.formData.roleId) {
          await roleApi.edit(props.formData)
          ElMessage.success('修改成功')
        } else {
          await roleApi.add(props.formData)
          ElMessage.success('新增成功')
        }
        dialogVisible.value = false
        emit('success')
      } catch (error) {
        console.error('保存角色失败:', error)
      } finally {
        submitLoading.value = false
      }
    }
  })
}

const handleCancel = () => {
  dialogVisible.value = false
}
</script>

<style scoped lang="scss">
.dialog-footer {
  text-align: right;
}
</style>
