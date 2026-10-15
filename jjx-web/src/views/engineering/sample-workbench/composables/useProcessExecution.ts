import type { Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleProcessStatusEnum } from '@/enums/sales'

interface ExecutionContext {
  readonlyMode: Readonly<Ref<boolean>>
  orderId: Readonly<Ref<number>>
  savingPlan: Readonly<Ref<boolean>>
  refreshCard: () => Promise<void>
  loadSummary: () => Promise<void>
}

/**
 * 执行请求入口；界面不直接调用状态接口。
 * 2026-10-15（dev-20261015-018）：当前工序未保存/有修改/保存中/失败时禁止开始与完成；
 * 组合卡片须全部成员已落库；其他工序或独立BOM草稿不阻断；失败或部分成功不谎报成功；
 * 执行后只回写本卡片的执行数据，不整表重载覆盖其他草稿。
 */
export function useProcessExecution(context: ExecutionContext) {
  const { readonlyMode, orderId, savingPlan, refreshCard, loadSummary } = context

  /** 当前卡片是否可执行：只有本卡自身状态参与判断，其他草稿不阻断。 */
  function blockReason(pc: any): string | null {
    if (pc.advancing) return '该工序正在提交，请勿重复操作'
    if (savingPlan.value) return '工序计划保存中，请稍后再操作'
    if (pc.editing) return '当前工序有未保存的修改，请先保存计划'
    if (pc.saveState === 'saving') return '当前工序保存中，请稍后再操作'
    if (pc.saveState === 'error') return '当前工序保存失败，请先重新保存'
    if (pc.saveState === 'dirty') return '当前工序有未保存的修改，请先保存计划'
    const items = pc.items || []
    if (!items.length || items.some((i: any) => !i.processId)) {
      return '该工序尚未保存，请先保存计划后再执行'
    }
    return null
  }

  /** 印刷行是否可执行（与卡片同口径）。 */
  function printBlockReason(row: any): string | null {
    if (row.advancing) return '该印刷工序正在提交，请勿重复操作'
    if (savingPlan.value) return '工序计划保存中，请稍后再操作'
    if (!row.processId) return '该印刷工序还未保存，请先保存计划'
    if (row.saveState === 'saving') return '该印刷工序保存中，请稍后再操作'
    if (row.saveState === 'error' || row.saveState === 'dirty') return '该印刷工序有未保存的修改，请先保存计划'
    return null
  }

  function applyExecution(pc: any, records: any[], next: number) {
    pc.status = next
    records.forEach((record) => {
      const item = (pc.items || []).find((i: any) => i.processId === record.processId)
      if (!item) return
      item.status = record.status ?? next
    })
    const first = records[0]
    if (first) {
      pc.startTime = first.startTime || null
      pc.endTime = first.endTime || null
      pc.durationMinutes = first.durationMinutes ?? null
      pc.operator = first.operator || ''
    }
  }

  // 印刷行状态推进：0待做 → 1进行中 → 2完成
  async function advancePrint(row: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    const printReason = printBlockReason(row)
    if (printReason) {
      ElMessage.warning(printReason)
      return
    }
    const next =
      row.status === SampleProcessStatusEnum.DOING.value
        ? SampleProcessStatusEnum.DONE.value
        : SampleProcessStatusEnum.DOING.value
    row.advancing = true
    try {
      const res: any = await sampleOrderApi.updateProcessItemStatus(orderId.value, row.processId, { status: next })
      const updated: any = res?.data
      row.status = updated?.status ?? next
      row.startTime = updated?.startTime ?? row.startTime
      row.endTime = updated?.endTime ?? null
      row.durationMinutes = updated?.durationMinutes ?? row.durationMinutes
      row.operator = updated?.operator ?? row.operator
      ElMessage.success(next === SampleProcessStatusEnum.DONE.value ? '印刷工序已完成' : '印刷工序已开始')
      await refreshCard()
      await loadSummary()
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    } finally {
      row.advancing = false
    }
  }

  async function advancePlan(pc: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    const reason = blockReason(pc)
    if (reason) {
      ElMessage.warning(reason)
      return
    }
    const next =
      pc.status === SampleProcessStatusEnum.DOING.value
        ? SampleProcessStatusEnum.DONE.value
        : SampleProcessStatusEnum.DOING.value
    const targetIds: number[] = (pc.items || []).map((i: any) => i.processId).filter(Boolean)
    pc.advancing = true
    const records: any[] = []
    try {
      for (const pid of targetIds) {
        const res: any = await sampleOrderApi.updateProcessItemStatus(orderId.value, pid, { status: next })
        records.push(res?.data || { processId: pid, status: next })
      }
      applyExecution(pc, records, next)
      ElMessage.success(next === SampleProcessStatusEnum.DONE.value ? '工序已完成' : '工序已开始')
      await refreshCard()
      await loadSummary()
    } catch (e: any) {
      // 部分成功不谎报整体成功；已成功的成员按返回结果回写，其余保持原状待刷新
      if (records.length > 0 && records.length < targetIds.length) {
        applyExecution(pc, records, next)
        ElMessage.error('部分工序状态更新失败，请刷新后重试')
      } else {
        ElMessage.error(e?.message || '操作失败')
      }
    } finally {
      pc.advancing = false
    }
  }

  return { advancePrint, advancePlan, blockReason, printBlockReason }
}
