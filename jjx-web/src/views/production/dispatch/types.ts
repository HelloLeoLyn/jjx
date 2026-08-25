import type { TaskTreeRow } from '@/types/production/task'

// 行类型：TaskTreeRow + 前端树状态（__parent 供 refreshRowChain 回溯父链）
export type TreeRow = Omit<TaskTreeRow, 'children'> & {
  children: TreeRow[]
  __parent: TreeRow | null
}
