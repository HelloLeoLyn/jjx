<template>
  <el-form
    ref="formRef"
    :model="modelValue"
    :rules="rules"
    label-width="100px"
    :disabled="disabled"
  >
    <el-form-item label="审核结果" prop="result">
      <el-radio-group :model-value="modelValue.result" @update:model-value="changeResult">
        <el-radio :value="ApprovalResultEnum.APPROVE" :disabled="!canApprove" border>通过</el-radio>
        <el-radio :value="ApprovalResultEnum.REJECT" :disabled="!canReject" border>驳回</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item
      v-if="modelValue.result === ApprovalResultEnum.APPROVE && quickOpinions.length"
      label="审核意见模板"
    >
      <el-button
        v-for="opinion in quickOpinions"
        :key="opinion"
        type="primary"
        plain
        link
        @click="updateRemark(opinion)"
        >{{ opinion }}</el-button
      >
    </el-form-item>
    <el-form-item label="审核意见" prop="remark">
      <el-input
        :model-value="modelValue.remark"
        type="textarea"
        :rows="3"
        :placeholder="requiredRemark ? '请输入审核意见' : '请输入审核意见（可选）'"
        :maxlength="maxLength"
        :show-word-limit="maxLength !== undefined"
        @update:model-value="updateRemark"
      />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ApprovalResultEnum, type ApprovalResult } from '@/enums/common/ApprovalEnum'
import type { ApprovalOpinion } from './types'

const props = withDefaults(
  defineProps<{
    modelValue: ApprovalOpinion
    canApprove?: boolean
    canReject?: boolean
    requireApproveRemark?: boolean
    requireRejectRemark?: boolean
    minLength?: number
    maxLength?: number
    quickOpinions?: string[]
    disabled?: boolean
  }>(),
  {
    canApprove: true,
    canReject: true,
    requireApproveRemark: true,
    requireRejectRemark: true,
    minLength: 0,
    quickOpinions: () => [],
    disabled: false,
  }
)
const emit = defineEmits<{ (e: 'update:modelValue', value: ApprovalOpinion): void }>()
const formRef = ref<FormInstance>()
const requiredRemark = computed(() =>
  props.modelValue.result === ApprovalResultEnum.APPROVE
    ? props.requireApproveRemark
    : props.requireRejectRemark
)
const rules: FormRules<ApprovalOpinion> = {
  result: [
    {
      validator: (_rule, value, callback) => {
        const allowed =
          value === ApprovalResultEnum.APPROVE
            ? props.canApprove
            : value === ApprovalResultEnum.REJECT && props.canReject
        callback(allowed ? undefined : new Error('请选择有权限的审核结果'))
      },
      trigger: 'change',
    },
  ],
  remark: [
    {
      validator: (_rule, value: string, callback) => {
        if (requiredRemark.value && !value?.trim()) {
          callback(new Error('请输入审核意见'))
        } else if (value && value.length < props.minLength) {
          callback(new Error(`审核意见至少${props.minLength}个字符`))
        } else if (props.maxLength !== undefined && value?.length > props.maxLength) {
          callback(new Error(`审核意见最多${props.maxLength}个字符`))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}
const updateRemark = (remark: string) => emit('update:modelValue', { ...props.modelValue, remark })
const changeResult = async (value: string | number | boolean | undefined) => {
  if (value !== ApprovalResultEnum.APPROVE && value !== ApprovalResultEnum.REJECT) return
  emit('update:modelValue', { ...props.modelValue, result: value as ApprovalResult })
  await nextTick()
  formRef.value?.clearValidate()
}
const validate = async () => {
  await nextTick()
  if (!formRef.value) throw new Error('审核表单尚未就绪')
  return formRef.value.validate()
}
const clearValidate = () => formRef.value?.clearValidate()
defineExpose({ validate, clearValidate })
</script>
