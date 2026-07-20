<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="false"
    :show-close="true"
    @update:model-value="$emit('update:visible', $event)"
    @close="handleCancel"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="rules"
      :label-width="labelWidth"
      v-bind="$attrs"
    >
      <el-row :gutter="20">
        <el-col
          v-for="field in fields"
          :key="field.prop"
          :span="field.span || 24"
        >
          <el-form-item
            :label="field.label"
            :prop="field.prop"
            :required="field.required"
          >
            <el-input
              v-if="field.type === 'input'"
              v-model="formData[field.prop]"
              :placeholder="field.placeholder || '请输入'"
              :disabled="field.disabled"
              :readonly="field.readonly"
              :maxlength="field.maxlength"
              clearable
            />
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="formData[field.prop]"
              type="textarea"
              :placeholder="field.placeholder || '请输入'"
              :disabled="field.disabled"
              :readonly="field.readonly"
              :rows="field.rows || 3"
            />
            <el-select
              v-else-if="field.type === 'select'"
              v-model="formData[field.prop]"
              :placeholder="field.placeholder || '请选择'"
              :disabled="field.disabled"
              :multiple="field.multiple"
              :clearable="field.clearable ?? true"
              style="width:100%"
            >
              <el-option v-for="opt in field.options || []" :key="opt.value" :label="opt.label" :value="opt.value" />
            </el-select>
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="formData[field.prop]"
              :disabled="field.disabled"
            />
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="formData[field.prop]"
              type="date"
              style="width:100%"
            />
            <el-date-picker
              v-else-if="field.type === 'datetime'"
              v-model="formData[field.prop]"
              type="datetime"
              style="width:100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer v-if="showFooter !== false">
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { FormInstance } from 'element-plus'
import type { FormOptions } from './type'

const props = defineProps<{
  visible: boolean
  title?: string
  formData: Record<string, any>
  fields: FormOptions[]
  rules?: Record<string, any>
  submitLoading?: boolean
  labelWidth?: string
  width?: string
  showFooter?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'submit', data: Record<string, any>): void
  (e: 'cancel'): void
}>()

const formRef = ref<FormInstance>()

function handleCancel() {
  emit('cancel')
  emit('update:visible', false)
}

async function handleSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('submit', props.formData)
  } catch { /* validation failed */ }
}
</script>
