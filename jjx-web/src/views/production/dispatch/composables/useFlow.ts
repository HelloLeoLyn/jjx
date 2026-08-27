import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskEvents } from '@/api/production/task'
import type { TaskEvent } from '@/types/production/task'
import type { TreeRow } from '../types'
import { orderProcessLabel } from '../utils/taskFormatters'

/** 任务流水 Drawer（ProductionTaskEvent 业务流水） */
export function useFlow() {
  const flowOpen = ref(false)
  const flowLoading = ref(false)
  const flowSource = ref<TreeRow | null>(null)
  const flowEvents = ref<TaskEvent[]>([])
  const flowTitle = computed(() =>
    flowSource.value ? `（${orderProcessLabel(flowSource.value)}）` : ''
  )

  const openFlowDrawer = async (row: TreeRow) => {
    flowSource.value = row
    flowEvents.value = []
    flowOpen.value = true
    flowLoading.value = true
    try {
      const res: any = await getTaskEvents(row.taskId)
      flowEvents.value = res?.data || []
    } catch (e: any) {
      ElMessage.error(e?.message || '流水加载失败')
      flowEvents.value = []
    } finally {
      flowLoading.value = false
    }
  }

  return { flowOpen, flowLoading, flowSource, flowEvents, flowTitle, openFlowDrawer }
}
