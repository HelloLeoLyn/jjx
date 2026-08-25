import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { returnTask } from '@/api/production/task'
import type { TreeRow } from '../types'
import { fmtQty } from '../utils/taskFormatters'

/**
 * 退回 Dialog（当前执行人把自身剩余退给父任务）
 * onSuccess：成功后由页面注入的刷新回调（refreshRowChain）
 */
export function useReturn(options: { onSuccess: (taskId: number) => Promise<void> }) {
  const returnOpen = ref(false)
  const returnLoading = ref(false)
  const returnTarget = ref<TreeRow | null>(null)
  const returnQuantity = ref<number | null>(null)
  const returnRemark = ref('')

  const returnMax = computed(() => Number(returnTarget.value?.remainingQuantity || 0))

  const openReturnDialog = (row: TreeRow) => {
    returnTarget.value = row
    returnQuantity.value = null
    returnRemark.value = ''
    returnOpen.value = true
  }

  const handleReturnSubmit = async () => {
    const target = returnTarget.value
    if (!target) return
    const qty = Number(returnQuantity.value || 0)
    if (qty <= 0) {
      ElMessage.warning('退回数量必须大于 0')
      return
    }
    if (qty > returnMax.value) {
      ElMessage.warning(`退回数量不能超过当前剩余 ${fmtQty(returnMax.value)}`)
      return
    }
    returnLoading.value = true
    try {
      await returnTask(target.taskId, {
        quantity: qty,
        remark: returnRemark.value.trim() || undefined,
      })
      ElMessage.success('退回成功')
      returnOpen.value = false
      await options.onSuccess(target.taskId)
    } catch (e: any) {
      ElMessage.error(e?.message || '退回失败')
    } finally {
      returnLoading.value = false
    }
  }

  return {
    returnOpen,
    returnLoading,
    returnTarget,
    returnQuantity,
    returnRemark,
    returnMax,
    openReturnDialog,
    handleReturnSubmit,
  }
}
