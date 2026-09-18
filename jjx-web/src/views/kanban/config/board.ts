import type { BoardTemplate } from '@/views/kanban/types/board'

function statusView() {
  return {
    id: 'status',
    name: '状态视图',
    groupBy: 'status',
    columns: [
      { id: 'pending', label: '待开始', color: '#909399', filterValue: 'pending' },
      { id: 'in_progress', label: '进行中', color: '#409eff', filterValue: 'in_progress' },
      { id: 'review', label: '待审核', color: '#e6a23c', filterValue: 'review' },
      { id: 'completed', label: '已完成', color: '#67c23a', filterValue: 'completed' },
      { id: 'blocked', label: '阻塞', color: '#f56c6c', filterValue: 'blocked' },
      { id: 'cancelled', label: '已废弃', color: '#c0c4cc', filterValue: 'cancelled' },
    ],
  }
}

/** 看板模板配置 */
export const boardTemplates: BoardTemplate[] = [
  {
    type: 'prod',
    name: '生产',
    icon: 'Sell',
    views: [statusView()],
  },
  {
    type: 'biz',
    name: '业务',
    icon: 'Notebook',
    views: [statusView()],
  },
  {
    type: 'dev',
    name: '开发任务',
    icon: 'Tools',
    views: [statusView()],
  },
]
