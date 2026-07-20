import type { BoardCard, BoardColumnDef } from '@/types/board'

/** 生成生产工单 Mock 数据 */
const processes = ['印刷', '冲切', '贴合', 'SMT贴片', '装配', '测试', '包装']

const products = [
  { name: '薄膜开关-MK12', customer: '华为', qty: 5000 },
  { name: '薄膜开关-MK08', customer: '小米', qty: 3000 },
  { name: '背光板-BL06', customer: 'OPPO', qty: 2000 },
  { name: '薄膜开关-LT03', customer: '联想', qty: 8000 },
  { name: '触摸面板-TP01', customer: '大疆', qty: 1500 },
  { name: '背光板-BL10', customer: 'vivo', qty: 4500 },
  { name: '薄膜开关-MK15', customer: '比亚迪', qty: 10000 },
  { name: '面板组件-PK02', customer: '海康威视', qty: 2500 },
  { name: '薄膜开关-DY01', customer: '德力西', qty: 6000 },
  { name: '膜内按钮-BN04', customer: '格力', qty: 3500 },
  { name: '触摸面板-TP03', customer: '美的', qty: 1200 },
  { name: '薄膜开关-MK20', customer: '中兴', qty: 7000 },
]

const assignees = ['张三', '李四', '王五', '赵六', '陈七']

function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

function randomPick<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)]
}

function randomDate(daysAgo: number, daysLater: number): string {
  const now = new Date()
  const offset = randomInt(-daysAgo, daysLater)
  const d = new Date(now.getTime() + offset * 86400000)
  return d.toISOString().slice(0, 10)
}

export function generateProductionCards(): BoardCard[] {
  const cards: BoardCard[] = []

  for (let i = 1; i <= 20; i++) {
    const product = randomPick(products)
    const processOrder = randomInt(1, 7)
    const deadline = randomDate(3, 14)
    const now = new Date().toISOString().slice(0, 10)

    cards.push({
      id: `WO-${String(i).padStart(3, '0')}`,
      title: `${product.name}`,
      templateType: 'production',
      workOrderNo: `WO-202607-${String(i).padStart(3, '0')}`,
      productName: product.name,
      quantity: product.qty,
      customer: product.customer,
      currentProcess: processes[processOrder - 1],
      processOrder,
      priority: deadline < now ? 'urgent' : randomPick(['urgent', 'high', 'normal', 'low'] as const),
      status: processOrder >= 7 ? 'completed' : deadline < now ? 'blocked' : 'in_progress',
      assignee: randomPick(assignees),
      deadline,
      remark: '',
      createdAt: randomDate(30, 0),
      updatedAt: randomDate(7, 0),
      extraData: {
        quantity: product.qty,
        unit: 'pcs',
        materialStatus: randomPick(['齐料', '待料', '部分到']),
      },
    })
  }

  return cards
}

export function generateOfficeTasks(): BoardCard[] {
  const titles = [
    '采购询价-薄膜材料',
    '客户样板设计修改',
    '供应商对账',
    '工艺文件编写',
    '图纸会签',
    '新员工入职培训计划',
    '月度生产报表',
    '设备保养记录整理',
    '质量体系内审准备',
    'ERP 权限梳理',
    '订单评审-华为新项目',
    '外协加工费用核算',
    '来料检验标准更新',
    '生产排程优化方案',
    '客诉处理-小米面板偏位',
    '工装夹具设计',
    'BOM 清单审核',
    '仓库盘点计划',
  ]

  const departments = ['销售部', '采购部', '设计部', '品质部', '生产管理']
  const statuses = ['pending', 'in_progress', 'review', 'completed', 'blocked'] as const

  return titles.map((title, i) => ({
    id: `TASK-${String(i + 1).padStart(3, '0')}`,
    title,
    templateType: 'office' as const,
    priority: randomPick(['urgent', 'high', 'normal', 'low'] as const),
    status: statuses[i % 5] as BoardCard['status'],
    assignee: randomPick(assignees),
    department: randomPick(departments),
    deadline: randomDate(1, 14),
    remark: '',
    createdAt: randomDate(30, 0),
    updatedAt: randomDate(7, 0),
    taskType: randomPick(['采购', '销售', '设计', '跟单', '行政']),
  }))
}

export function generateEmergencyCards(): BoardCard[] {
  const items = [
    { title: 'MK12 返工-印刷偏位', type: '返工', source: 'WO-202607-005', reason: '印刷偏位超公差' },
    { title: 'BL06 返工-贴合气泡', type: '返工', source: 'WO-202607-012', reason: '贴合层气泡超标' },
    { title: '华为急单-3000pcs', type: '急单', source: 'SO-202607-023', reason: '客户产线急停' },
    { title: '小米样品-3天交期', type: '急单', source: 'SO-202607-025', reason: '客户样品需求' },
    { title: '大疆插单-TP01加急', type: '插单', source: 'SO-202607-028', reason: '紧急订单插队' },
    { title: 'LT03 返工-断路', type: '返工', source: 'WO-202607-008', reason: '线路断路' },
    { title: 'vivo 急单变更', type: '急单', source: 'SO-202607-030', reason: '客户要求提前交货' },
    { title: '比亚迪插单-10000pcs', type: '插单', source: 'SO-202607-032', reason: '大客户紧急订单' },
  ]

  return items.map((item, i) => ({
    id: `EM-${String(i + 1).padStart(3, '0')}`,
    title: item.title,
    templateType: 'emergency' as const,
    urgencyType: item.type,
    priority: 'urgent' as const,
    status: randomPick(['pending', 'in_progress', 'blocked'] as const),
    assignee: randomPick(assignees),
    deadline: randomDate(0, 5),
    remark: item.reason,
    createdAt: randomDate(7, 0),
    updatedAt: randomDate(2, 0),
    sourceOrderNo: item.source,
    reason: item.reason,
  }))
}

/** 产出卡片的分组数据 */
export function groupCardsByColumn(
  cards: BoardCard[],
  columns: BoardColumnDef[],
  groupBy: string,
): { column: BoardColumnDef; cards: BoardCard[] }[] {
  if (groupBy === 'deadline') {
    return groupByDeadline(cards, columns)
  }
  if (groupBy === 'currentProcess') {
    return groupByField(cards, columns, groupBy, 'filterValue')
  }
  return groupByField(cards, columns, groupBy, 'filterValue')
}

function groupByDeadline(cards: BoardCard[], columns: BoardColumnDef[]) {
  const now = new Date()
  const today = now.toISOString().slice(0, 10)

  const groups: { column: BoardColumnDef; cards: BoardCard[] }[] = columns.map(col => ({ column: col, cards: [] }))

  for (const card of cards) {
    let colIdx = columns.length - 1
    if (card.deadline < today) colIdx = 0
    else if (card.deadline === today) colIdx = 1
    else if (isThisWeek(card.deadline)) colIdx = 2
    else if (isNextWeek(card.deadline)) colIdx = 3
    else colIdx = 4

    groups[colIdx].cards.push(card)
  }

  return groups
}

function isThisWeek(dateStr: string): boolean {
  const now = new Date()
  const d = new Date(dateStr)
  const dayOfWeek = now.getDay() || 7
  const weekEnd = new Date(now.getTime() + (7 - dayOfWeek) * 86400000)
  return d <= weekEnd && d >= now
}

function isNextWeek(dateStr: string): boolean {
  const now = new Date()
  const dayOfWeek = now.getDay() || 7
  const nextWeekStart = new Date(now.getTime() + (8 - dayOfWeek) * 86400000)
  const nextWeekEnd = new Date(nextWeekStart.getTime() + 6 * 86400000)
  const d = new Date(dateStr)
  return d >= nextWeekStart && d <= nextWeekEnd
}

function groupByField(
  cards: BoardCard[],
  columns: BoardColumnDef[],
  fieldName: string,
  filterParam: 'filterValue',
) {
  return columns.map(col => ({
    column: col,
    cards: cards.filter(c => {
      const val = (c as Record<string, unknown>)[fieldName] ?? ''
      return String(val) === String(col[filterParam])
    }),
  }))
}
