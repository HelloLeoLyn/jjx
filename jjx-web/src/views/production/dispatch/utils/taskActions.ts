import type { TaskAllowedAction, TaskTreeRow } from '@/types/production/task'
/**
 * 任务动作可用性判断（业务统一出口）
 * 当前实现：直接读后端投影的 allowedActions；
 * 后续如果业务需要在这里重算/补充判断，只改这一个文件即可
 */
export function allowedActions(
  row: TaskTreeRow | null | undefined,
  action: TaskAllowedAction
): boolean {
  // 直接读后端投影的 allowedActions 并且要求row.assigneeId是登录用户id
  return !!row && Array.isArray(row.allowedActions) && row.allowedActions.includes(action)
}
