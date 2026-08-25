import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  completeTask,
  getTaskChildren,
  getTaskDetail,
  getTaskTreePage,
  type TaskTreeQuery,
} from '@/api/production/task'
import type { TaskTreeRow } from '@/types/production/task'
import type { PageResult } from '@/types'
import type { TreeRow } from '../types'
import { useTaskTree } from './useTaskTree'
import { orderProcessLabel } from '../utils/taskFormatters'

/**
 * 第一层分页 + 筛选 + 统计 + 树懒加载 + 行刷新 + 完成操作
 */
export function useDispatchList() {
  const { initRow, updateFields, findRow, mergeChildren } = useTaskTree(() => firstLevelRows.value)

  const loading = ref(false)
  const firstLevelRows = ref<TreeRow[]>([])
  const total = ref(0)
  const queryParams = reactive<TaskTreeQuery>({ pageNum: 1, pageSize: 10 })
  const filterForm = reactive({ keyword: '', status: '' })

  const getList = async () => {
    loading.value = true
    try {
      const res: any = await getTaskTreePage({
        ...queryParams,
        keyword: filterForm.keyword.trim() || undefined,
        status: filterForm.status || undefined,
      })
      const page: PageResult<TaskTreeRow> | null = res?.data
      firstLevelRows.value = (page?.records || []).map((r) => initRow(r))
      total.value = page?.total || 0
    } catch (e: any) {
      ElMessage.error(e?.message || '任务加载失败')
      firstLevelRows.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  // 统计（当前页 First Task；P6：未分配按状态 PENDING 统计，不再依赖 assignee_id）
  const statUnassigned = computed(
    () => firstLevelRows.value.filter((r) => r.status === 'PENDING').length
  )
  const statActive = computed(() => firstLevelRows.value.filter((r) => r.status === 'ACTIVE').length)
  const statPendingQty = computed(
    () => firstLevelRows.value.filter((r) => Number(r.pendingQuantity || 0) > 0).length
  )

  const handleQuery = () => {
    queryParams.pageNum = 1
    getList()
  }
  const handleReset = () => {
    Object.assign(filterForm, { keyword: '', status: '' })
    queryParams.pageNum = 1
    getList()
  }

  // 子树原生懒加载（el-table lazy 模式：hasChildren=true 显示箭头，展开时调 load）
  const tableRef = ref<any>(null)

  const loadTreeChildren = async (row: TreeRow, _node: any, resolve: (data: any[]) => void) => {
    try {
      const res: any = await getTaskChildren(row.taskId)
      const children = ((res?.data || []) as TaskTreeRow[]).map((c) => initRow(c, row))
      // 保留自己的 children 副本（findRow/refreshRowChain 依赖；el-table 用内部 lazy map 渲染）
      row.children.splice(0, row.children.length, ...children)
      resolve(children)
    } catch (e: any) {
      ElMessage.error(e?.message || '子任务加载失败')
      resolve([])
    }
  }

  async function refreshRow(row: TreeRow): Promise<void> {
    const res: any = await getTaskDetail(row.taskId)
    const fresh: TaskTreeRow | null = res?.data
    if (!fresh) return
    updateFields(row, fresh)
    // 该行已展开过（children 副本非空）→ 同步刷新子层（el-table lazy map 同步）
    if (row.children.length) {
      const res2: any = await getTaskChildren(row.taskId)
      const freshChildren = ((res2?.data || []) as TaskTreeRow[]).map((c) => initRow(c, row))
      mergeChildren(row, freshChildren)
      tableRef.value?.updateKeyChildren(row.taskId, row.children)
    }
  }

  // 刷新目标行 + 父链（分配会改变父行的 assigned/remaining 投影）
  async function refreshRowChain(taskId: number): Promise<void> {
    const row = findRow(taskId)
    if (!row) return
    await refreshRow(row)
    let parent = row.__parent
    while (parent) {
      await refreshRow(parent)
      parent = parent.__parent
    }
  }

  const handleComplete = async (row: TreeRow) => {
    try {
      await ElMessageBox.confirm(
        `确认完成「${orderProcessLabel(row)}」？完成后将禁止分配/退回/收回/报工。`,
        '完成确认',
        { type: 'warning' }
      )
    } catch {
      return
    }
    try {
      await completeTask(row.taskId)
      ElMessage.success('任务已完成')
      await refreshRowChain(row.taskId)
    } catch (e: any) {
      ElMessage.error(e?.message || '完成失败')
    }
  }

  return {
    loading,
    firstLevelRows,
    total,
    queryParams,
    filterForm,
    getList,
    statUnassigned,
    statActive,
    statPendingQty,
    handleQuery,
    handleReset,
    tableRef,
    loadTreeChildren,
    refreshRowChain,
    handleComplete,
  }
}
