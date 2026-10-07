<template>
  <el-dialog
    v-model="visible"
    title="审批工艺路线"
    width="70%"
    :close-on-click-modal="false"
    @close="handleClose"
    @opened="handleOpened"
  >
    <RouteDetailView ref="detailViewRef">
      <template #extra>
        <el-divider content-position="left">审批意见</el-divider>
        <ApprovalOpinionForm
          ref="formRef"
          v-model="form"
          :can-approve="canApprove"
          :can-reject="canReject"
          :require-approve-remark="false"
          :disabled="submitLoading"
        />
      </template>
    </RouteDetailView>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="submitLoading"
          :disabled="!canApprove && !canReject"
          @click="handleSubmit"
        >
          确定
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import ApprovalOpinionForm from '@/components/Approval/ApprovalOpinionForm.vue'
import type { ApprovalOpinion } from '@/components/Approval/types'
import { ApprovalResultEnum } from '@/enums/common/ApprovalEnum'
import { hasPermi } from '@/directives'
import RouteDetailView from './RouteDetailView.vue'

const props = defineProps<{
  modelValue: boolean
  routingId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'approve', remark?: string): void
  (e: 'reject', remark: string): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const formRef = ref<InstanceType<typeof ApprovalOpinionForm>>()
const submitLoading = ref(false)
const detailViewRef = ref<InstanceType<typeof RouteDetailView>>()
const canApprove = computed(() => hasPermi('engineering:routing:approve'))
const canReject = computed(() => hasPermi('engineering:routing:reject'))

const form = ref<ApprovalOpinion>({ result: ApprovalResultEnum.APPROVE, remark: '' })

const handleOpened = () => {
  if (form.value.result === ApprovalResultEnum.APPROVE && !canApprove.value && canReject.value) {
    form.value.result = ApprovalResultEnum.REJECT
  } else if (
    form.value.result === ApprovalResultEnum.REJECT &&
    !canReject.value &&
    canApprove.value
  ) {
    form.value.result = ApprovalResultEnum.APPROVE
  }

  if (props.routingId) {
    nextTick(() => {
      detailViewRef.value?.loadDetail(props.routingId!)
    })
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    submitLoading.value = true
    if (form.value.result === ApprovalResultEnum.APPROVE) {
      emit('approve', form.value.remark || undefined)
    } else {
      emit('reject', form.value.remark)
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    submitLoading.value = false
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.clearValidate()
  }
  form.value.result = ApprovalResultEnum.APPROVE
  form.value.remark = ''
  detailViewRef.value?.resetDetail()
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
