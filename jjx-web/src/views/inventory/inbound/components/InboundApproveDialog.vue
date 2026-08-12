<template>
  <el-dialog
    :title="`审核 - ${inboundNo || ''}`"
    :model-value="visible"
    width="1000px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <!-- 单据详情（公共组件） -->
    <InboundDetail v-if="visible && inboundId" :inbound-id="inboundId" />

    <!-- 审核操作 -->
    <el-divider content-position="left">审核意见</el-divider>
    <el-form label-width="80px">
      <el-form-item label="备注">
        <el-input
          v-model="remark"
          type="textarea"
          :rows="3"
          placeholder="请输入审核意见（选填）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="danger" :loading="submitting" @click="handleReject">驳 回</el-button>
      <el-button type="primary" :loading="submitting" @click="handleApprove">通 过</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { useUserStore } from '@/store/modules/user'
import InboundDetail from './InboundDetail.vue'

const props = defineProps<{
  visible: boolean
  inboundId?: number
  inboundNo?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const remark = ref('')
const submitting = ref(false)
const userStore = useUserStore()

watch(
  () => props.visible,
  (val) => {
    if (val) remark.value = ''
  },
)

const handleApprove = async () => {
  submitting.value = true
  try {
    const res = await inboundApi.approve({
      inboundId: String(props.inboundId),
      approverId: String(userStore.userId ?? 1),
      approverName: (userStore.nickName || userStore.userName || 'admin') as string,
      remark: remark.value || undefined,
    })
    if (res.data) {
      ElMessage.success('审批通过')
      emit('success')
      handleClose()
    } else {
      ElMessage.error('审批失败')
    }
  } catch (error) {
    console.error('审批失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleReject = async () => {
  submitting.value = true
  try {
    const res = await inboundApi.reject({
      inboundId: String(props.inboundId),
      approverId: String(userStore.userId ?? 1),
      approverName: (userStore.nickName || userStore.userName || 'admin') as string,
      remark: remark.value || '',
    })
    if (res.data) {
      ElMessage.success('已驳回')
      emit('success')
      handleClose()
    } else {
      ElMessage.error('驳回失败')
    }
  } catch (error) {
    console.error('驳回失败:', error)
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  remark.value = ''
  emit('update:visible', false)
}
</script>
