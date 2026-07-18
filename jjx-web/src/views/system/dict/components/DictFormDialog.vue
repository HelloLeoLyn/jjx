<template>
  <el-dialog
    :title="title"
    v-model="visible"
    width="500px"
    append-to-body
    :before-close="handleCancel"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="字典编码" prop="dictCode">
        <el-input
          v-model="formData.dictCode"
          placeholder="请输入字典编码"
          maxlength="50"
          :disabled="!!formData.dictId"
        />
      </el-form-item>
      <el-form-item label="字典名称" prop="dictName">
        <el-input v-model="formData.dictName" placeholder="请输入字典名称" maxlength="100" />
      </el-form-item>
      <el-form-item label="排序" prop="sortOrder">
        <el-input-number v-model="formData.sortOrder" :min="0" :max="9999" style="width: 100%" />
      </el-form-item>
      <el-form-item label="状态" prop="isActive">
        <el-radio-group v-model="formData.isActive">
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="0">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          placeholder="请输入备注"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确 定</el-button>
        <el-button @click="handleCancel">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { SysDictDTO } from '@/types/system/dict'

// 组件属性
interface Props {
  visible: boolean
  title?: string
  formData: SysDictDTO
  submitLoading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  title: '字典类型',
  submitLoading: false,
})

// 组件事件
const emit = defineEmits<{
  'update:visible': [value: boolean]
  submit: []
  cancel: []
}>()

// 响应式数据
const visible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})

const formRef = ref<FormInstance>()

// 表单验证规则
const rules: FormRules = {
  dictCode: [
    { required: true, message: '字典编码不能为空', trigger: 'blur' },
    { max: 50, message: '字典编码长度不能超过50个字符', trigger: 'blur' },
  ],
  dictName: [
    { required: true, message: '字典名称不能为空', trigger: 'blur' },
    { max: 100, message: '字典名称长度不能超过100个字符', trigger: 'blur' },
  ],
}

// 提交表单
const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      emit('submit')
    }
  })
}

// 取消操作
const handleCancel = () => {
  visible.value = false
  emit('cancel')
}
</script>
