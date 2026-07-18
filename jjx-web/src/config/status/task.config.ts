// 任务状态枚举
export enum TaskStatus {
  TODO = 'todo',
  IN_PROGRESS = 'in_progress',
  REVIEW = 'review',
  DONE = 'done',
  ARCHIVED = 'archived',
  BLOCKED = 'blocked',
}

// 任务状态配置 - 直接导出，简单明了
export const taskStatusConfig = {
  [TaskStatus.TODO]: {
    label: '待处理',
    type: 'info',
  },
  [TaskStatus.IN_PROGRESS]: {
    label: '进行中',
    type: 'warning',
  },
  [TaskStatus.REVIEW]: {
    label: '待审核',
    type: 'warning',
  },
  [TaskStatus.DONE]: {
    label: '已完成',
    type: 'success',
  },
  [TaskStatus.ARCHIVED]: {
    label: '已归档',
    type: 'info',
  },
  [TaskStatus.BLOCKED]: {
    label: '已阻塞',
    type: 'danger',
  },
}
