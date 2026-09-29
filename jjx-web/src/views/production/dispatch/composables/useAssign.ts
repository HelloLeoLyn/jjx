import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { assignTask, getTaskCandidates, getTaskChildren } from '@/api/production/task'
import type { TaskAssignPayload, TaskCandidate, TaskTreeRow } from '@/types/production/task'
import type { TreeRow } from '../types'
import { fmtQty, orderProcessLabel } from '../utils/taskFormatters'

/**
 * 分配 Dialog（统一多选 + 拆量：每个层级同构）
 * onSuccess：分配成功落库后由页面注入的刷新回调（refreshRowChain）
 */
export function useAssign(options: { onSuccess: (taskId: number) => Promise<void> }) {
  const assignOpen = ref(false)
  const assignLoading = ref(false)
  const assignTarget = ref<TreeRow | null>(null)
  const candidateList = ref<TaskCandidate[]>([])
  const candidateLoading = ref(false)
  // 统一多选 + 每行数量
  const selectedRows = ref<TaskCandidate[]>([])
  const qtyMap = reactive<Record<number, number>>({})

  const assignQuantityMax = computed(() => Number(assignTarget.value?.remainingQuantity || 0))
  const assignTotal = computed(() =>
    selectedRows.value.reduce((s, c) => s + Number(qtyMap[c.userId] || 0), 0)
  )
  const afterAssign = computed(
    () => Number(assignTarget.value?.remainingQuantity || 0) - assignTotal.value
  )
  // 平分后出现 0 的行（剩余 < 人数）不允许提交
  const hasZeroQty = computed(
    () =>
      selectedRows.value.length > 0 &&
      selectedRows.value.some((c) => Number(qtyMap[c.userId] || 0) <= 0)
  )
  const submitDisabled = computed(() => {
    if (!selectedRows.value.length) return true
    if (hasZeroQty.value) return true
    return assignTotal.value <= 0 || assignTotal.value > assignQuantityMax.value
  })
  const assignTitle = computed(() =>
    assignTarget.value ? `任务分配 · ${orderProcessLabel(assignTarget.value)}` : '任务分配'
  )

  // ============ 已分配责任（当前 Task 直接 Child；只读，与“本次分配”分离） ============
  const assignedList = ref<TaskTreeRow[]>([])
  const assignedListLoading = ref(false)

  const loadAssignedList = async (taskId: number) => {
    assignedListLoading.value = true
    try {
      const res: any = await getTaskChildren(taskId)
      assignedList.value = res?.data || []
    } catch (e: any) {
      ElMessage.error(e?.message || '已分配责任加载失败')
      assignedList.value = []
    } finally {
      assignedListLoading.value = false
    }
  }

  const candidateTreeRef = ref<any>(null)

  function normalizeCandidates(nodes: TaskCandidate[]): TaskCandidate[] {
    return (nodes || []).map((n) => ({
      ...n,
      disabled: n.selectable === false,
      children: normalizeCandidates(n.children || []),
    }))
  }

  const totalCandidateCount = computed(() => {
    const count = (nodes: TaskCandidate[]): number =>
      nodes.reduce((s, n) => s + 1 + count(n.children || []), 0)
    return count(candidateList.value)
  })

  // 手改标记：被手动编辑过的行，选择变化时保留其值，不参与重新平分
  const manualQty = ref<Set<number>>(new Set())
  const markManualQty = (row: TaskCandidate) => {
    manualQty.value.add(row.userId)
  }

  /**
   * 平分数量：整数件 → 向下取整，除不尽的余数按顺序给前面的人（100/3 → 34,33,33）；
   * 总量非整数 → 退化为两位小数平分（分给前面的人 1 分）
   */
  function splitEvenly(total: number, count: number): number[] {
    if (count <= 0) return []
    const isInt = Number.isInteger(total)
    const scaled = isInt ? Math.round(total) : Math.round(total * 100)
    const unit = isInt ? 1 : 100
    const base = Math.floor(scaled / count)
    const rem = scaled - base * count
    return Array.from({ length: count }, (_, i) => (base + (i < rem ? 1 : 0)) / unit)
  }

  // 统一多选树（父子独立勾选；根节点不可选，任意合法后代均可选）
  // 勾选变化 → 按「剩余 - 手改合计」在未手改的行里平分（顺序 = 候选树显示顺序）
  const onTreeCheck = () => {
    const checked: TaskCandidate[] = candidateTreeRef.value?.getCheckedNodes(false) || []
    selectedRows.value = checked
    const ids = new Set(checked.map((c) => c.userId))
    // 丢弃已取消勾选行的数量 + 手改标记
    Object.keys(qtyMap).forEach((k) => {
      if (!ids.has(Number(k))) delete qtyMap[Number(k)]
    })
    ;[...manualQty.value].forEach((id) => {
      if (!ids.has(id)) manualQty.value.delete(id)
    })
    // 手改行保留原值，其余行平分剩余
    const manualRows = checked.filter((c) => manualQty.value.has(c.userId))
    const freeRows = checked.filter((c) => !manualQty.value.has(c.userId))
    const manualSum = manualRows.reduce((s, c) => s + Number(qtyMap[c.userId] || 0), 0)
    const pool = Number(assignTarget.value?.remainingQuantity || 0) - manualSum
    const parts = splitEvenly(pool > 0 ? pool : 0, freeRows.length)
    freeRows.forEach((c, i) => {
      qtyMap[c.userId] = parts[i]
    })
  }

  const removeAssignItem = (row: TaskCandidate) => {
    candidateTreeRef.value?.setChecked(row.userId, false)
    selectedRows.value = selectedRows.value.filter((n) => n.userId !== row.userId)
    delete qtyMap[row.userId]
    manualQty.value.delete(row.userId)
  }

  const openAssignDialog = async (row: TreeRow) => {
    assignTarget.value = row
    candidateList.value = []
    selectedRows.value = []
    assignedList.value = []
    Object.keys(qtyMap).forEach((k) => delete qtyMap[Number(k)])
    manualQty.value.clear()
    assignOpen.value = true
    loadAssignedList(row.taskId)
    candidateLoading.value = true
    try {
      const res: any = await getTaskCandidates(row.taskId)
      candidateList.value = normalizeCandidates(res?.data || [])
    } catch (e: any) {
      ElMessage.error(e?.message || '候选人员加载失败')
      candidateList.value = []
    } finally {
      candidateLoading.value = false
    }
  }

  const handleAssignSubmit = async () => {
    const target = assignTarget.value
    if (!target) return
    // 统一多选：一次事务创建全部 Child（总数量校验，最终以后端 gate 为准；允许部分分配，剩余保留）
    if (!selectedRows.value.length) {
      ElMessage.warning('请至少选择一名执行人')
      return
    }
    const items = selectedRows.value.map((c) => ({
      assigneeId: c.userId,
      quantity: Number(qtyMap[c.userId]),
    }))
    if (items.some((it) => !it.quantity || it.quantity <= 0)) {
      ElMessage.warning('每项分配数量必须大于 0')
      return
    }
    const sum = items.reduce((s, it) => s + it.quantity, 0)
    if (sum > Number(target.remainingQuantity || 0)) {
      ElMessage.warning(`分配合计不能超过剩余 ${fmtQty(target.remainingQuantity)}`)
      return
    }
    const payload: TaskAssignPayload = { items }
    assignLoading.value = true
    try {
      await assignTask(target.taskId, payload)
      ElMessage.success('分配成功')
      // 关闭弹窗 + 刷新列表
      assignOpen.value = false
      await options.onSuccess(target.taskId)
      await loadAssignedList(target.taskId)
    } catch (e: any) {
      ElMessage.error(e?.message || '分配失败')
    } finally {
      assignLoading.value = false
    }
  }

  return {
    assignOpen,
    assignLoading,
    assignTarget,
    candidateList,
    candidateLoading,
    selectedRows,
    qtyMap,
    assignQuantityMax,
    assignTotal,
    afterAssign,
    hasZeroQty,
    submitDisabled,
    assignTitle,
    assignedList,
    assignedListLoading,
    loadAssignedList,
    candidateTreeRef,
    totalCandidateCount,
    onTreeCheck,
    markManualQty,
    removeAssignItem,
    openAssignDialog,
    handleAssignSubmit,
  }
}
