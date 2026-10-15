import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleProcessStatusEnum } from '@/enums/sales'

interface ExecutionContext {
  readonlyMode: Readonly<Ref<boolean>>
  orderId: Readonly<Ref<number>>
  loadPlan: () => Promise<void>
  refreshCard: () => Promise<void>
  loadSummary: () => Promise<void>
}

/** 执行请求入口；界面不直接调用状态接口。 */
export function useProcessExecution(context: ExecutionContext) {
  const { readonlyMode, orderId, loadPlan, refreshCard, loadSummary } = context
  // 印刷行状态推进：0待做 → 1进行中 → 2完成
  async function advancePrint(row: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    if (!row.processId) {
      ElMessage.warning('该印刷工序还未保存，请先保存计划')
      return
    }
    const next = row.status === SampleProcessStatusEnum.DOING.value ? SampleProcessStatusEnum.DONE.value : SampleProcessStatusEnum.DOING.value
    try {
      await sampleOrderApi.updateProcessItemStatus(orderId.value, row.processId, { status: next })
      row.status = next
      ElMessage.success(next === SampleProcessStatusEnum.DONE.value ? '印刷工序已完成' : '印刷工序已开始')
      await refreshCard()
      await loadSummary()
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    }
  }

  async function advancePlan(pc: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    const next = pc.status === SampleProcessStatusEnum.DOING.value ? SampleProcessStatusEnum.DONE.value : SampleProcessStatusEnum.DOING.value
    pc.advancing = true
    try {
      const targetIds = pc.items.map((i: any) => i.processId).filter(Boolean)
      for (const pid of targetIds) {
        await sampleOrderApi.updateProcessItemStatus(orderId.value, pid, { status: next })
      }
      ElMessage.success(next === SampleProcessStatusEnum.DONE.value ? '工序已完成' : '工序已开始')
      await loadPlan()
      await refreshCard()
      await loadSummary()
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    } finally {
      pc.advancing = false
    }
  }

  return { advancePrint, advancePlan }
}
