<template>
  <el-dialog
    :model-value="modelValue"
    title="新建临时工序"
    width="480px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="70px" :disabled="submitting">
      <el-form-item label="名称" prop="processName">
        <el-input v-model="form.processName" maxlength="100" show-word-limit />
      </el-form-item>
      <el-form-item label="类别" prop="processCategory">
        <el-select v-model="form.processCategory" placeholder="请选择类别" style="width: 100%">
          <el-option
            v-for="option in categoryOptions"
            :key="option.itemValue"
            :label="option.label || option.itemValue"
            :value="option.itemValue"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="说明" prop="description">
        <el-input
          v-model="form.description"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button :disabled="submitting" @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'
import type { StandardProcessItem, TempStandardProcessData } from '@/types/product/standardProcess'

const props = defineProps<{
  modelValue: boolean
  initialName: string
  processes: StandardProcessItem[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'created', item: StandardProcessItem): void
}>()
const { options: categoryOptions } = useDict('process_category')
const formRef = ref<FormInstance>()
const submitting = ref(false)
const form = reactive<TempStandardProcessData>({
  processName: '',
  processCategory: '',
  description: '',
})
const rules: FormRules = {
  processName: [{ required: true, whitespace: true, message: '请输入工序名称', trigger: 'blur' }],
  processCategory: [{ required: true, message: '请选择类别', trigger: 'change' }],
  description: [{ required: true, whitespace: true, message: '请输入说明', trigger: 'blur' }],
}
watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) return
    Object.assign(form, { processName: props.initialName, processCategory: '', description: '' })
    formRef.value?.clearValidate()
  }
)

async function submit() {
  if (submitting.value || !(await formRef.value?.validate().catch(() => false))) return
  const data: TempStandardProcessData = {
    processName: form.processName.trim(),
    processCategory: form.processCategory,
    description: form.description.trim(),
  }
  const duplicate = props.processes.find(
    (item) =>
      item.processCategory === data.processCategory &&
      item.processName.trim().toLowerCase() === data.processName.toLowerCase()
  )
  if (duplicate) {
    ElMessage.warning(`同类别已存在工序：${duplicate.processName}（${duplicate.processCode}）`)
    return
  }
  submitting.value = true
  try {
    let result
    try {
      // 后端在全部工序（含禁用/驳回）中查重，避免只查启用缓存漏检。
      result = await standardProcessApi.createTemp(data)
    } catch (error) {
      if ((error as { code?: number }).code !== 40901) throw error
      try {
        await ElMessageBox.confirm((error as Error).message, '存在近名工序，是否继续创建？', {
          type: 'warning',
          confirmButtonText: '仍然创建',
          cancelButtonText: '取消',
        })
      } catch (action) {
        if (action === 'cancel' || action === 'close') return
        throw action
      }
      // 与 TempStandardProcessDTO 对齐；同名校验不可由 force 绕过。
      result = await standardProcessApi.createTemp({ ...data, force: true })
    }
    if (!result.data) {
      ElMessage.error('创建响应缺少工序信息，请查询工序列表确认后再操作')
      return
    }
    emit('created', result.data)
    emit('update:modelValue', false)
    ElMessage.success('临时工序已创建，待审核')
  } catch (error) {
    // 请求层负责展示接口错误，保留弹窗输入便于修正或重试。
    console.error('新建临时工序失败:', error)
  } finally {
    submitting.value = false
  }
}
</script>
