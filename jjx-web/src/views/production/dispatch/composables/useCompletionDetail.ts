import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskCompletionDetails } from '@/api/production/task'
import type { TaskCompletionDetail, TaskTreeRow } from '@/types/production/task'
import { orderProcessLabel } from '../utils/taskFormatters'

/** 完成明细 Drawer（仅 APPROVED 有效完成事实） */
export function useCompletionDetail() {
  const detailOpen = ref(false)
  const detailLoading = ref(false)
  const detailList = ref<TaskCompletionDetail[]>([])
  const detailSource = ref<TaskTreeRow | null>(null)
  const detailTitle = computed(() =>
    detailSource.value ? `（${orderProcessLabel(detailSource.value)}）` : ''
  )
  const detailTotal = computed(() =>
    detailList.value.reduce((s, d) => s + Number(d.reportQuantity || 0), 0)
  )

  const openCompletionDetails = async (row: TaskTreeRow) => {
    detailSource.value = row
    detailList.value = []
    detailOpen.value = true
    detailLoading.value = true
    try {
      const res: any = await getTaskCompletionDetails(row.taskId)
      detailList.value = res?.data || []
    } catch (e: any) {
      ElMessage.error(e?.message || '完成明细加载失败')
      detailList.value = []
    } finally {
      detailLoading.value = false
    }
  }

  return {
    detailOpen,
    detailLoading,
    detailList,
    detailSource,
    detailTitle,
    detailTotal,
    openCompletionDetails,
  }
}
