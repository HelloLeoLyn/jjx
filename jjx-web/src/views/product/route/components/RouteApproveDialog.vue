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
        <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
          <el-form-item label="审批结果">
            <el-radio-group v-model="form.action">
              <el-radio value="approve" :disabled="!canApprove">通过</el-radio>
              <el-radio value="reject" :disabled="!canReject">驳回</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="审批意见" prop="remark">
            <el-input
              v-model="form.remark"
              type="textarea"
              :rows="3"
              :placeholder="form.action === 'approve' ? '请输入审批意见（可选）' : '请输入驳回原因'"
            />
          </el-form-item>
        </el-form>
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
import { ref, reactive, computed, nextTick } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
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

const formRef = ref<FormInstance>()
const submitLoading = ref(false)
const detailViewRef = ref<InstanceType<typeof RouteDetailView>>()
const canApprove = computed(() => hasPermi('engineering:routing:approve'))
const canReject = computed(() => hasPermi('engineering:routing:reject'))

const form = reactive({
  action: 'approve' as 'approve' | 'reject',
  remark: '',
})

const rules = reactive<FormRules<typeof form>>({
  remark: [
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (form.action === 'reject' && !value) {
          callback(new Error('驳回时必须填写驳回原因'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
})

const handleOpened = () => {
  if (form.action === 'approve' && !canApprove.value && canReject.value) {
    form.action = 'reject'
  } else if (form.action === 'reject' && !canReject.value && canApprove.value) {
    form.action = 'approve'
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
    if (form.action === 'approve') {
      emit('approve', form.remark || undefined)
    } else {
      emit('reject', form.remark)
    }
  } catch (error) {
    console.error('表单验证失败:', error)
  } finally {
    submitLoading.value = false
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.action = 'approve'
  form.remark = ''
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
