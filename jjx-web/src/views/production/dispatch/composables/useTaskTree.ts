import { reactive } from 'vue'
import type { TaskTreeRow } from '@/types/production/task'
import type { TreeRow } from '../types'

/**
 * 树行模型（纯逻辑，不持有 UI/API 状态）
 * rowsGetter 由列表层注入（第一层行），findRow 依赖它做全树查找
 */
export function useTaskTree(getRows: () => TreeRow[]) {
  function initRow(r: TaskTreeRow, parent: TreeRow | null = null): TreeRow {
    const row = reactive({
      ...r,
      children: [] as TreeRow[],
      __parent: parent,
    }) as TreeRow
    ;(r.children || []).forEach((c) => row.children.push(initRow(c, row)))
    return row
  }

  function updateFields(row: TreeRow, fresh: TaskTreeRow): void {
    row.taskId = fresh.taskId
    row.taskNo = fresh.taskNo
    row.parentTaskId = fresh.parentTaskId
    row.executionId = fresh.executionId
    row.orderNo = fresh.orderNo
    row.processName = fresh.processName
    row.processCode = fresh.processCode
    row.processOrder = fresh.processOrder
    row.assigneeId = fresh.assigneeId
    row.assigneeName = fresh.assigneeName
    row.parentAssigneeName = fresh.parentAssigneeName
    row.taskQuantity = fresh.taskQuantity
    row.completedQuantity = fresh.completedQuantity
    row.pendingQuantity = fresh.pendingQuantity
    row.assignedQuantity = fresh.assignedQuantity
    row.remainingQuantity = fresh.remainingQuantity
    row.status = fresh.status
    row.statusLabel = fresh.statusLabel
    row.hasChildren = fresh.hasChildren
  }

  function findRow(taskId: number, rows: TreeRow[] = getRows()): TreeRow | null {
    for (const r of rows) {
      if (r.taskId === taskId) return r
      if (r.children.length) {
        const hit = findRow(taskId, r.children)
        if (hit) return hit
      }
    }
    return null
  }

  // 合并刷新 children：保留已展开的旧行对象（保持展开状态），新增/更新字段
  function mergeChildren(row: TreeRow, freshChildren: TreeRow[]): void {
    const oldMap = new Map<number, TreeRow>()
    row.children.forEach((c) => oldMap.set(c.taskId, c))
    const merged: TreeRow[] = freshChildren.map((f) => {
      const old = oldMap.get(f.taskId)
      if (old) {
        updateFields(old, f)
        old.__parent = row
        return old
      }
      return f
    })
    row.children.splice(0, row.children.length, ...merged)
  }

  return { initRow, updateFields, findRow, mergeChildren }
}
