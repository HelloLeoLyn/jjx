import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getTaskChildren, recallTask } from '@/api/production/task'
import type { Ref } from 'vue'
import type { TaskTreeRow } from '@/types/production/task'
import type { TreeRow } from '../types'
import { fmtQty } from '../utils/taskFormatters'

interface RecallOptions {
  target: Ref<TreeRow | null>
  visible: Ref<boolean>
  onSuccess: (taskId: number) => Promise<void>
}

/** 行是否可收回：自身剩余 > 0 且未完成/未取消（任意层级） */
function canRecall(row: TaskTreeRow): boolean {
  return (
    Number(row.remainingQuantity || 0) > 0 &&
    row.status !== 'COMPLETED' &&
    row.status !== 'CANCELLED'
  )
}

/** API 行 → 树行（弹窗内不做父链刷新，__parent 恒 null） */
function toRow(r: TaskTreeRow): TreeRow {
  return { ...r, children: [], __parent: null }
}

/**
 * 收回 Dialog 逻辑（树形可收回列表 + 多选 + 单行/批量收回）
 * - 数据：getTaskChildren 懒加载树（任意层级展示）
 * - 可收回 = 该行自身 remainingQuantity；可收回 > 0 的行可选
 * - 批量收回：循环调现有 recallTask（单任务接口，无批量事务）
 */
export function useRecall(options: RecallOptions) {
  const loading = ref(false)
  const rows = ref<TreeRow[]>([])
  const tableRef = ref<any>(null)
  const selectedRows = ref<TaskTreeRow[]>([])
  const qtyMap = reactive<Record<number, number>>({})
  const submitLoading = ref(false)
  const remark = ref('')

  const summary = computed(() =>
    selectedRows.value.reduce((s, r) => s + Number(qtyMap[r.taskId] || 0), 0)
  )

  const initQty = (row: TaskTreeRow) => {
    if (qtyMap[row.taskId] == null) {
      qtyMap[row.taskId] = Number(row.remainingQuantity || 0)
    }
  }

  const loadChildren = async (row: TreeRow, _node: any, resolve: (data: any[]) => void) => {
    try {
      const res: any = await getTaskChildren(row.taskId)
      const children = ((res?.data || []) as TaskTreeRow[]).map((c) => {
        initQty(c)
        return toRow(c)
      })
      row.children.splice(0, row.children.length, ...children)
      resolve(children)
    } catch (e: any) {
      ElMessage.error(e?.message || '子任务加载失败')
      resolve([])
    }
  }

  const loadFirstLevel = async () => {
    const target = options.target.value
    if (!target) return
    loading.value = true
    try {
      const res: any = await getTaskChildren(target.taskId)
      const list = (res?.data || []) as TaskTreeRow[]
      rows.value = list.map((c) => {
        initQty(c)
        return toRow(c)
      })
    } catch (e: any) {
      ElMessage.error(e?.message || '可收回任务加载失败')
      rows.value = []
    } finally {
      loading.value = false
    }
  }

  const reset = () => {
    rows.value = []
    selectedRows.value = []
    Object.keys(qtyMap).forEach((k) => delete qtyMap[Number(k)])
    remark.value = ''
  }

  const open = async () => {
    reset()
    await loadFirstLevel()
  }

  watch(
    () => options.visible.value,
    (v) => {
      if (v) open()
    }
  )

  const onSelectionChange = (sel: TaskTreeRow[]) => {
    selectedRows.value = sel
  }

  const selectable = (row: TaskTreeRow): boolean => canRecall(row)

  const validateQty = (row: TaskTreeRow): number | null => {
    const qty = Number(qtyMap[row.taskId] || 0)
    const max = Number(row.remainingQuantity || 0)
    if (qty <= 0) {
      ElMessage.warning(`「${row.assigneeName || row.taskId}」收回数量必须大于 0`)
      return null
    }
    if (qty > max) {
      ElMessage.warning(`「${row.assigneeName || row.taskId}」收回数量不能超过 ${fmtQty(max)}`)
      return null
    }
    return qty
  }

  const submitOne = async (row: TaskTreeRow): Promise<boolean> => {
    const target = options.target.value
    if (!target) return false
    const qty = validateQty(row)
    if (qty == null) return false
    try {
      await recallTask(target.taskId, {
        childTaskId: row.taskId,
        quantity: qty,
        remark: remark.value.trim() || undefined,
      })
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '收回失败')
      return false
    }
  }

  // 单行收回：成功后关闭弹窗并刷新
  const handleRecall = async (row: TaskTreeRow) => {
    submitLoading.value = true
    const ok = await submitOne(row)
    submitLoading.value = false
    if (ok) {
      ElMessage.success('收回成功')
      const target = options.target.value
      if (target) {
        options.visible.value = false
        await options.onSuccess(target.taskId)
      }
    }
  }

  // 批量收回：循环提交；全部成功关弹窗，部分失败保留失败项
  const handleBatchRecall = async () => {
    const target = options.target.value
    if (!target || !selectedRows.value.length) return
    submitLoading.value = true
    let okCount = 0
    const failedIds = new Set<number>()
    for (const row of selectedRows.value) {
      if (await submitOne(row)) {
        okCount++
      } else {
        failedIds.add(row.taskId)
      }
    }
    submitLoading.value = false
    if (failedIds.size) {
      selectedRows.value = selectedRows.value.filter((r) => failedIds.has(r.taskId))
      ElMessage.warning(`成功 ${okCount} 条，失败 ${failedIds.size} 条；失败项已保留勾选`)
      return
    }
    ElMessage.success(`收回成功 ${okCount} 条`)
    options.visible.value = false
    await options.onSuccess(target.taskId)
  }

  return {
    loading,
    rows,
    tableRef,
    selectedRows,
    qtyMap,
    summary,
    remark,
    loadChildren,
    onSelectionChange,
    selectable,
    handleRecall,
    handleBatchRecall,
    submitLoading,
  }
}
