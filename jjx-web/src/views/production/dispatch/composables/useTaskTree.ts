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

  /**
   * 行刷新：以服务端返回为准的通用合并
   * 2026-09-10：改为通用合并（原为白名单逐字段赋值）。
   * 原因：白名单漏了后端投影的 allowedActions/canAssign，派工/退回/收回后按钮（如「收回」）
   * 不即时更新，必须整页刷新重新 getList 才拿到新投影。
   * 现除 children（懒加载/mergeChildren 管理）与 __parent（本地树指针）外，fresh 的所有字段一律覆盖，
   * 以后后端投影新增字段也不会再变陈旧。
   */
  function updateFields(row: TreeRow, fresh: TaskTreeRow): void {
    const target = row as unknown as Record<string, unknown>
    const source = fresh as unknown as Record<string, unknown>
    for (const key of Object.keys(source)) {
      if (key === 'children' || key === '__parent') continue
      target[key] = source[key]
    }
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
