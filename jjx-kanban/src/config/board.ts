import type { BoardTemplate } from '@/types/board'

/** 看板模板配置 */
export const boardTemplates: BoardTemplate[] = [
  {
    type: 'production',
    name: '生产工单',
    icon: 'Sell',
    views: [
      {
        id: 'process',
        name: '工序视图',
        groupBy: 'currentProcess',
        columns: [
          { id: 'printing', label: '印刷', color: '#409eff', filterValue: '印刷' },
          { id: 'cutting', label: '冲切', color: '#67c23a', filterValue: '冲切' },
          { id: 'laminating', label: '贴合', color: '#e6a23c', filterValue: '贴合' },
          { id: 'smt', label: 'SMT贴片', color: '#f56c6c', filterValue: 'SMT贴片' },
          { id: 'assembly', label: '装配', color: '#909399', filterValue: '装配' },
          { id: 'testing', label: '测试', color: '#b37feb', filterValue: '测试' },
          { id: 'packing', label: '包装', color: '#36cfc9', filterValue: '包装' },
        ],
      },
      {
        id: 'priority',
        name: '紧急度视图',
        groupBy: 'priority',
        columns: [
          { id: 'urgent', label: '紧急', color: '#f56c6c', filterValue: 'urgent' },
          { id: 'high', label: '高', color: '#e6a23c', filterValue: 'high' },
          { id: 'normal', label: '普通', color: '#409eff', filterValue: 'normal' },
          { id: 'low', label: '低', color: '#909399', filterValue: 'low' },
        ],
      },
      {
        id: 'deadline',
        name: '交期视图',
        groupBy: 'deadline',
        columns: [
          { id: 'overdue', label: '已逾期', color: '#f56c6c' },
          { id: 'today', label: '今日', color: '#e6a23c' },
          { id: 'this_week', label: '本周', color: '#409eff' },
          { id: 'next_week', label: '下周', color: '#67c23a' },
          { id: 'later', label: '更晚', color: '#909399' },
        ],
      },
    ],
  },
  {
    type: 'office',
    name: '办公室任务',
    icon: 'Notebook',
    views: [
      {
        id: 'status',
        name: '状态视图',
        groupBy: 'status',
        columns: [
          { id: 'pending', label: '待处理', color: '#909399', filterValue: 'pending' },
          { id: 'in_progress', label: '进行中', color: '#409eff', filterValue: 'in_progress' },
          { id: 'review', label: '待审核', color: '#e6a23c', filterValue: 'review' },
          { id: 'completed', label: '已完成', color: '#67c23a', filterValue: 'completed' },
          { id: 'blocked', label: '阻塞', color: '#f56c6c', filterValue: 'blocked' },
        ],
      },
      {
        id: 'department',
        name: '部门视图',
        groupBy: 'department',
        columns: [
          { id: 'sales', label: '销售部', filterValue: '销售部' },
          { id: 'purchase', label: '采购部', filterValue: '采购部' },
          { id: 'design', label: '设计部', filterValue: '设计部' },
          { id: 'quality', label: '品质部', filterValue: '品质部' },
          { id: 'production_mgt', label: '生产管理', filterValue: '生产管理' },
        ],
      },
    ],
  },
  {
    type: 'emergency',
    name: '紧急任务',
    icon: 'WarningFilled',
    views: [
      {
        id: 'type',
        name: '类型视图',
        groupBy: 'urgencyType',
        columns: [
          { id: 'rework', label: '返工单', color: '#f56c6c', filterValue: '返工' },
          { id: 'rush', label: '急单', color: '#e6a23c', filterValue: '急单' },
          { id: 'insert', label: '插单', color: '#409eff', filterValue: '插单' },
        ],
      },
      {
        id: 'assignee',
        name: '跟进人视图',
        groupBy: 'assignee',
        columns: [
          { id: 'zhang', label: '张三', filterValue: '张三' },
          { id: 'li', label: '李四', filterValue: '李四' },
          { id: 'wang', label: '王五', filterValue: '王五' },
          { id: 'zhao', label: '赵六', filterValue: '赵六' },
        ],
      },
    ],
  },

  {
    type: 'dev',
    name: '开发任务',
    icon: 'Tools',
    views: [
      {
        id: 'priority',
        name: '优先级视图',
        groupBy: 'priority',
        columns: [
          { id: 'p0', label: 'P0 紧急', color: '#f56c6c', filterValue: 'urgent' },
          { id: 'p1', label: 'P1 高', color: '#e6a23c', filterValue: 'high' },
          { id: 'p2', label: 'P2 中', color: '#409eff', filterValue: 'normal' },
          { id: 'p3', label: 'P3 低', color: '#909399', filterValue: 'low' },
        ],
      },
      {
        id: 'status',
        name: '状态视图',
        groupBy: 'status',
        columns: [
          { id: 'todo', label: '待处理', color: '#f59e0b', filterValue: 'pending' },
          { id: 'done', label: '已完成', color: '#10b981', filterValue: 'completed' },
        ],
      },
    ],
  },
]
