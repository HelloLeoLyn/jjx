/** 卡片优先级 */
export type Priority = 'urgent' | 'high' | 'normal' | 'low'

/** 卡片状态 */
export type CardStatus = 'pending' | 'in_progress' | 'review' | 'completed' | 'blocked' | 'cancelled'

/** 看板模板类型 */
export type TemplateType = 'production' | 'office' | 'emergency' | 'dev'

/** 看板模板定义 */
export interface BoardTemplate {
  type: TemplateType
  name: string
  icon: string
  views: BoardView[]
}

/** 看板视图定义 */
export interface BoardView {
  id: string
  name: string
  /** 分组字段 */
  groupBy: string
  /** 列定义：静态列 或 从数据中动态提取 */
  columns: BoardColumnDef[]
}

/** 列定义 */
export interface BoardColumnDef {
  id: string
  label: string
  color?: string
  /** 筛选条件：匹配卡片上的哪个字段 */
  filterField?: string
  /** 筛选值 */
  filterValue?: string
  /** 最大卡片数（0 不限） */
  maxCards?: number
}

/** 看板列（运行时） */
export interface BoardColumn {
  def: BoardColumnDef
  cards: BoardCard[]
}

/** 看板卡片 */
export interface BoardCard {
  id: string
  title: string
  templateType: TemplateType

  // 通用字段
  priority: Priority
  status: CardStatus
  assignee: string
  deadline: string // YYYY-MM-DD
  remark: string
  createdAt: string
  updatedAt: string

  // 分组相关字段（不同视图用不同字段分组）
  currentProcess?: string    // 工序视图
  processOrder?: number      // 工序排序
  taskType?: string           // 办公室/紧急任务类型
  department?: string         // 部门
  urgencyType?: string        // 紧急类型

  // 生产工单特有字段
  productName?: string
  quantity?: number
  workOrderNo?: string
  customer?: string

  // 紧急任务特有
  sourceOrderNo?: string
  reason?: string

  // 额外数据（扩展用）
  extraData?: Record<string, unknown>
}

/** 看板视图配置（单个） */
export interface ViewConfig {
  view: BoardView
  columns: BoardColumn[]
}

/** 看板筛选条件 */
export interface BoardFilter {
  keyword?: string
  assignee?: string
  priority?: Priority
  status?: CardStatus
  deadlineFrom?: string
  deadlineTo?: string
}

/** 拖拽事件 */
export interface DragEvent {
  cardId: string
  fromColumnId: string
  toColumnId: string
  newIndex: number
}

/** API 响应 */
export interface ApiResponse<T> {
  code: number
  data: T
  message: string
}
